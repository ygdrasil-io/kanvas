package org.graphiks.kanvas.surface.gpu

import org.graphiks.math.color.ColorMatrixF32

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptorAssemblySession
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedMaterialUnsupportedEvidence
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedMaterialUnsupportedReason
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectUniformValue
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorSpaceInterpolation
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.ShaderModule
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.pipeline.UniformLayout
import org.graphiks.math.color.ColorARGB
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeF32
import org.junit.jupiter.api.Test

class GPUMaterialMapperTest {
    @Test
    fun `legacy unsupported blend falls back to source while prepared mapping preserves blend`() {
        val dst = Shader.ConicalGradient(
            start = Point2F32(0f, 0f),
            startRadius = 0f,
            end = Point2F32(10f, 10f),
            endRadius = 10f,
            stops = listOf(
                GradientStop(0f, ColorARGB.fromRGBA(1f, 0f, 0f, 1f)),
                GradientStop(1f, ColorARGB.fromRGBA(0f, 0f, 1f, 1f)),
            ),
        )
        val src = Shader.SolidColor(ColorARGB.fromRGBA(0.25f, 0.5f, 0.75f, 1f))
        val paint = Paint(
            shader = Shader.Blend(
                mode = BlendMode.SRC_OVER,
                dst = dst,
                src = src,
            ),
        )

        val legacy = assertIs<GPUMaterialDescriptor.SolidColor>(paint.toMaterial())
        val prepared = assertIs<GPUMaterialDescriptor.BlendShader>(
            paint.toPreparedMaterialMapping().descriptor,
        )

        assertEquals(src.color.r, legacy.r)
        assertEquals(src.color.g, legacy.g)
        assertEquals(src.color.b, legacy.b)
        assertIs<GPUMaterialDescriptor.ConicalGradient>(prepared.dst)
        assertIs<GPUMaterialDescriptor.SolidColor>(prepared.src)
        assertEquals("", prepared.wgslCombined)
        assertEquals(0, prepared.uniformBytes.size)
    }

    @Test
    fun `prepared solid mapping represents paint color alpha exactly once`() {
        val mapping = Paint(
            color = ColorARGB.fromRGBA(0.2f, 0.4f, 0.6f, 0.5f),
        ).toPreparedMaterialMapping()
        val solid = assertIs<GPUMaterialDescriptor.SolidColor>(mapping.descriptor)

        assertEquals(0.5f, solid.a, 0.002f)
        assertEquals(1f, mapping.paintAlpha)
    }

    @Test
    fun `prepared gradient mapping retains source alpha and separates caller modulation`() {
        val gradient = Shader.LinearGradient(
            start = Point2F32(0f, 0f),
            end = Point2F32(10f, 0f),
            stops = listOf(
                GradientStop(0f, ColorARGB.fromRGBA(1f, 0f, 0f, 0.25f)),
                GradientStop(1f, ColorARGB.fromRGBA(0f, 0f, 1f, 0.75f)),
            ),
        )
        val mapping = Paint(
            color = ColorARGB.fromRGBA(1f, 1f, 1f, 0.5f),
            shader = gradient,
        ).toPreparedMaterialMapping()
        val descriptor = assertIs<GPUMaterialDescriptor.LinearGradient>(mapping.descriptor)

        assertEquals(0.25f, descriptor.startA, 0.002f)
        assertEquals(0.75f, descriptor.endA, 0.002f)
        assertEquals(0.5f, mapping.paintAlpha, 0.002f)
    }

    @Test
    fun `linear descriptor snapshots gradient facts without mutable escape`() {
        val positions = floatArrayOf(0f, 1f)
        val colors = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f)
        val descriptor = GPUMaterialDescriptor.LinearGradient(
            startX = 0f, startY = 0f, endX = 8f, endY = 0f,
            startR = 1f, startG = 0f, startB = 0f, startA = 1f,
            endR = 0f, endG = 0f, endB = 1f, endA = 1f,
            allStopPositions = positions,
            allStopColors = colors,
        )

        positions[0] = 0.25f
        colors[0] = 0.25f

        assertEquals(listOf(0f, 1f), descriptor.allStopPositions?.toList())
        assertEquals(listOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f), descriptor.allStopColors?.toList())
        assertEquals("srgb", descriptor.interpolation)
        assertEquals(listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), descriptor.localMatrix)

        val (_, _, _, _, _, _, _, _, _, _, _, _, _, componentPositions, componentColors,
            componentSnippet, componentEntryPoint) = descriptor
        assertEquals(listOf(0f, 1f), componentPositions?.toList())
        assertEquals(listOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f), componentColors?.toList())
        assertEquals(null, componentSnippet)
        assertEquals(null, componentEntryPoint)

        val nonSrgb = descriptor.withGradientFacts(
            GPUMaterialDescriptor.GradientFacts(interpolation = "linear"),
        )
        assertEquals("linear", nonSrgb.interpolation)
        assertNotEquals(descriptor, nonSrgb)
        assertNotEquals(descriptor.hashCode(), nonSrgb.hashCode())

        val escaped = requireNotNull(descriptor.allStopPositions)
        escaped[0] = 0.75f
        val escapedColors = requireNotNull(descriptor.allStopColors)
        escapedColors[0] = 0.75f
        assertEquals(listOf(0f, 1f), descriptor.allStopPositions?.toList())
        assertEquals(listOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f), descriptor.allStopColors?.toList())

        val copied = nonSrgb.copy()
        assertEquals("linear", copied.interpolation)
        assertEquals(nonSrgb.localMatrix, copied.localMatrix)
        assertEquals(listOf(0f, 1f), copied.allStopPositions?.toList())
        assertEquals(listOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f), copied.allStopColors?.toList())

        fun nanDescriptor(bits: Int) = GPUMaterialDescriptor.LinearGradient(
            startX = Float.fromBits(bits), startY = 0f, endX = 1f, endY = 0f,
            startR = 1f, startG = 0f, startB = 0f, startA = 1f,
            endR = 0f, endG = 0f, endB = 1f, endA = 1f,
        )
        val firstNan = nanDescriptor(0x7fc00001)
        val secondNan = nanDescriptor(0x7fc00002)
        assertNotEquals(firstNan, secondNan)
        assertNotEquals(firstNan.hashCode(), secondNan.hashCode())
        assertNotEquals(firstNan.toString(), secondNan.toString())
    }

    @Test
    fun `linear wrappers retain source facts while prepared mapping refuses unsupported facts`() {
        val gradient = Shader.LinearGradient(
            start = Point2F32(1f, 2f),
            end = Point2F32(9f, 2f),
            stops = threeGradientStops(),
        )
        val localMatrix = Matrix3x3F32.translation(3f, 4f)

        val legacyMatrix = assertIs<GPUMaterialDescriptor.LinearGradient>(
            Paint(shader = Shader.WithLocalMatrix(gradient, localMatrix)).toMaterial(),
        )
        assertEquals(
            listOf(1f, 0f, 3f, 0f, 1f, 4f, 0f, 0f, 1f),
            legacyMatrix.localMatrix,
        )
        assertEquals("srgb", legacyMatrix.interpolation)
        val preparedMatrix = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(shader = Shader.WithLocalMatrix(gradient, localMatrix))
                .toPreparedMaterialMapping().descriptor,
        )
        assertEquals(GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX, preparedMatrix.reason)
        assertEquals(GPUMaterialKind.LinearGradient, preparedMatrix.originalKind)
        assertEquals(
            listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            assertIs<GPUMaterialDescriptor.LinearGradient>(preparedMatrix.source).localMatrix,
        )

        val legacyWorkingSpace = assertIs<GPUMaterialDescriptor.LinearGradient>(
            Paint(
                shader = Shader.WithWorkingColorSpace(
                    gradient,
                    ColorSpaceInterpolation.LINEAR,
                ),
            ).toMaterial(),
        )
        assertEquals("linear", legacyWorkingSpace.interpolation)
        assertEquals(
            listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            legacyWorkingSpace.localMatrix,
        )
        val preparedWorkingSpace = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(
                shader = Shader.WithWorkingColorSpace(
                    gradient,
                    ColorSpaceInterpolation.LINEAR,
                ),
            ).toPreparedMaterialMapping().descriptor,
        )
        assertEquals(GPUPreparedMaterialUnsupportedReason.WORKING_COLOR_SPACE, preparedWorkingSpace.reason)
        assertEquals(GPUMaterialKind.LinearGradient, preparedWorkingSpace.originalKind)
        assertEquals(legacyWorkingSpace, preparedWorkingSpace.source)
    }

    @Test
    fun `prepared radial mapping retains normalized stops interpolation tile mode and identity facts`() {
        val descriptor = assertIs<GPUMaterialDescriptor.RadialGradient>(
            Paint(
                shader = Shader.RadialGradient(
                    center = Point2F32(10f, 20f),
                    radius = 30f,
                    stops = threeGradientStops(),
                    tileMode = TileMode.CLAMP,
                ),
            ).toPreparedMaterialMapping().descriptor,
        )

        assertEquals("clamp", descriptor.tileMode)
        assertEquals("srgb", descriptor.interpolation)
        assertEquals(listOf(0f, 0.35f, 1f), descriptor.allStopPositions?.toList())
        assertContentEquals(
            threeGradientColors(),
            descriptor.allStopColors!!,
        )
        assertEquals(
            listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            descriptor.localMatrix,
        )
    }

    @Test
    fun `prepared sweep mapping retains normalized stops interpolation tile mode and identity facts`() {
        val descriptor = assertIs<GPUMaterialDescriptor.SweepGradient>(
            Paint(
                shader = Shader.SweepGradient(
                    center = Point2F32(10f, 20f),
                    startAngle = 15f,
                    endAngle = 300f,
                    stops = threeGradientStops(),
                    tileMode = TileMode.CLAMP,
                ),
            ).toPreparedMaterialMapping().descriptor,
        )

        assertEquals("clamp", descriptor.tileMode)
        assertEquals("srgb", descriptor.interpolation)
        assertEquals(listOf(0f, 0.35f, 1f), descriptor.allStopPositions?.toList())
        assertContentEquals(
            threeGradientColors(),
            descriptor.allStopColors!!,
        )
        assertEquals(
            listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            descriptor.localMatrix,
        )
    }

    @Test
    fun `prepared empty gradient stops return a typed refusal without throwing`() {
        val cases = listOf(
            Shader.LinearGradient(
                start = Point2F32(0f, 0f),
                end = Point2F32(10f, 0f),
                stops = emptyList(),
            ) to GPUMaterialKind.LinearGradient,
            Shader.RadialGradient(
                center = Point2F32(0f, 0f),
                radius = 10f,
                stops = emptyList(),
            ) to GPUMaterialKind.RadialGradient,
            Shader.SweepGradient(
                center = Point2F32(0f, 0f),
                stops = emptyList(),
            ) to GPUMaterialKind.SweepGradient,
            Shader.ConicalGradient(
                start = Point2F32(0f, 0f),
                startRadius = 0f,
                end = Point2F32(10f, 10f),
                endRadius = 10f,
                stops = emptyList(),
            ) to GPUMaterialKind.TwoPointConical,
        )

        cases.forEach { (shader, kind) ->
            val descriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
                Paint(shader = shader).toPreparedMaterialMapping().descriptor,
            )
            assertEquals("unsupported.material.mapping.gradient_stop_count", descriptor.reason.diagnosticCode)
            assertEquals(kind, descriptor.originalKind)
        }
    }

    @Test
    fun `prepared gradient local matrix wrappers refuse until the route consumes matrix facts`() {
        val matrix = Matrix3x3F32.of(
            1.5f, 0.25f, 3f,
            -0.5f, 0.75f, 4f,
            0.01f, 0.02f, 1f,
        )
        listOf(
            Shader.RadialGradient(
                center = Point2F32(10f, 20f),
                radius = 30f,
                stops = threeGradientStops(),
            ) to GPUMaterialKind.RadialGradient,
            Shader.SweepGradient(
                center = Point2F32(10f, 20f),
                stops = threeGradientStops(),
            ) to GPUMaterialKind.SweepGradient,
        ).forEach { (shader, kind) ->
            val descriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
                Paint(shader = Shader.WithLocalMatrix(shader, matrix))
                    .toPreparedMaterialMapping()
                    .descriptor,
            )
            assertEquals(GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX, descriptor.reason)
            assertEquals(kind, descriptor.originalKind)
        }
    }

    @Test
    fun `prepared nested non commutative gradient matrices remain a stable local matrix refusal`() {
        val inner = Matrix3x3F32.of(
            2f, 1f, 3f,
            0f, 1f, 4f,
            0f, 0f, 1f,
        )
        val outer = Matrix3x3F32.of(
            1f, 0f, 5f,
            1f, 1f, 0f,
            0f, 0f, 1f,
        )
        val descriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(
                shader = Shader.WithLocalMatrix(
                    Shader.WithLocalMatrix(
                        Shader.RadialGradient(
                            center = Point2F32(10f, 20f),
                            radius = 30f,
                            stops = threeGradientStops(),
                        ),
                        inner,
                    ),
                    outer,
                ),
            ).toPreparedMaterialMapping().descriptor,
        )

        assertEquals(GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX, descriptor.reason)
        assertEquals(GPUMaterialKind.RadialGradient, descriptor.originalKind)
    }

    @Test
    fun `gradient matrix facts snapshot caller input immutably`() {
        val matrix = mutableListOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f,
        )
        val descriptor = GPUMaterialDescriptor.RadialGradient(
            centerX = 0f,
            centerY = 0f,
            radius = 1f,
            startR = 1f,
            startG = 0f,
            startB = 0f,
            startA = 1f,
            endR = 0f,
            endG = 0f,
            endB = 1f,
            endA = 1f,
        ).withGradientFacts(
            GPUMaterialDescriptor.GradientFacts(localMatrix = matrix),
        )
        matrix[0] = 9f

        assertEquals(1f, descriptor.localMatrix.first())
    }

    @Test
    fun `legacy nested gradient matrices preserve non commutative multiplication order`() {
        val inner = Matrix3x3F32.of(
            2f, 1f, 3f,
            0f, 1f, 4f,
            0f, 0f, 1f,
        )
        val outer = Matrix3x3F32.of(
            1f, 0f, 5f,
            1f, 1f, 0f,
            0f, 0f, 1f,
        )
        val descriptor = assertIs<GPUMaterialDescriptor.RadialGradient>(
            Paint(
                shader = Shader.WithLocalMatrix(
                    Shader.WithLocalMatrix(
                        Shader.RadialGradient(
                            center = Point2F32(10f, 20f),
                            radius = 30f,
                            stops = threeGradientStops(),
                        ),
                        inner,
                    ),
                    outer,
                ),
            ).toMaterial(),
        )

        assertEquals(
            listOf(3f, 1f, 13f, 1f, 1f, 4f, 0f, 0f, 1f),
            descriptor.localMatrix,
        )
    }

    @Test
    fun `legacy gradient color filters preserve descriptor facts`() {
        val descriptor = assertIs<GPUMaterialDescriptor.RadialGradient>(
            Paint(
                shader = Shader.WithColorFilter(
                    shader = Shader.WithWorkingColorSpace(
                        Shader.RadialGradient(
                            center = Point2F32(10f, 20f),
                            radius = 30f,
                            stops = threeGradientStops(),
                        ),
                        ColorSpaceInterpolation.OKLAB,
                    ),
                    filter = ColorFilter.Matrix(ColorMatrixF32.of(
                        floatArrayOf(
                            0f, 0f, 1f, 0f, 0f,
                            0f, 1f, 0f, 0f, 0f,
                            1f, 0f, 0f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    )),
                ),
            ).toMaterial(),
        )

        assertEquals("oklab", descriptor.interpolation)
    }

    @Test
    fun `prepared invalid gradient matrix input returns a typed refusal without throwing`() {
        val descriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(
                shader = Shader.WithLocalMatrix(
                    Shader.RadialGradient(
                        center = Point2F32(10f, 20f),
                        radius = 30f,
                        stops = threeGradientStops(),
                    ),
                    Matrix3x3F32.of(
                        1f, 0f, 0f,
                        0f, Float.NaN, 0f,
                        0f, 0f, 1f,
                    ),
                ),
            ).toPreparedMaterialMapping().descriptor,
        )

        assertEquals(GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX, descriptor.reason)
        assertEquals(GPUMaterialKind.RadialGradient, descriptor.originalKind)
    }

    @Test
    fun `legacy invalid radial and sweep matrices return typed refusals without throwing`() {
        val invalidMatrix = Matrix3x3F32.of(
            1f, 0f, 0f,
            0f, Float.NaN, 0f,
            0f, 0f, 1f,
        )
        listOf(
            Shader.RadialGradient(
                center = Point2F32(10f, 20f),
                radius = 30f,
                stops = threeGradientStops(),
            ) to GPUMaterialKind.RadialGradient,
            Shader.SweepGradient(
                center = Point2F32(10f, 20f),
                stops = threeGradientStops(),
            ) to GPUMaterialKind.SweepGradient,
        ).forEach { (shader, kind) ->
            val descriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
                Paint(shader = Shader.WithLocalMatrix(shader, invalidMatrix)).toMaterial(),
            )
            assertEquals(GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX, descriptor.reason)
            assertEquals(kind, descriptor.originalKind)
        }
    }

    @Test
    fun `prepared gradient wrappers preserve outer refusal precedence for unsupported sources`() {
        val nonSrgb = Shader.RadialGradient(
            center = Point2F32(10f, 20f),
            radius = 30f,
            stops = threeGradientStops(),
            interpolation = ColorSpaceInterpolation.LINEAR,
        )

        val localMatrixDescriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(shader = Shader.WithLocalMatrix(nonSrgb, Matrix3x3F32.Identity))
                .toPreparedMaterialMapping()
                .descriptor,
        )
        assertEquals(GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX, localMatrixDescriptor.reason)

        val workingColorDescriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(
                shader = Shader.WithWorkingColorSpace(nonSrgb, ColorSpaceInterpolation.OKLAB),
            ).toPreparedMaterialMapping().descriptor,
        )
        assertEquals(GPUPreparedMaterialUnsupportedReason.WORKING_COLOR_SPACE, workingColorDescriptor.reason)
    }

    @Test
    fun `prepared working color space preserves non-srgb interpolation facts before refusing`() {
        val descriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(
                shader = Shader.WithWorkingColorSpace(
                    Shader.RadialGradient(
                        center = Point2F32(10f, 20f),
                        radius = 30f,
                        stops = threeGradientStops(),
                    ),
                    ColorSpaceInterpolation.OKLAB,
                ),
            ).toPreparedMaterialMapping().descriptor,
        )

        assertEquals(
            GPUPreparedMaterialUnsupportedReason.WORKING_COLOR_SPACE,
            descriptor.reason,
        )
        assertEquals(
            "oklab",
            assertIs<GPUMaterialDescriptor.RadialGradient>(descriptor.source).interpolation,
        )
    }

    @Test
    fun `prepared color filter preserves local matrix refusal diagnostics`() {
        val descriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(
                shader = Shader.WithColorFilter(
                    shader = Shader.WithLocalMatrix(
                        Shader.SweepGradient(
                            center = Point2F32(10f, 20f),
                            stops = threeGradientStops(),
                        ),
                        Matrix3x3F32.translation(3f, 4f),
                    ),
                    filter = ColorFilter.Matrix(ColorMatrixF32.of(
                        floatArrayOf(
                            1f, 0f, 0f, 0f, 0f,
                            0f, 1f, 0f, 0f, 0f,
                            0f, 0f, 1f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    )),
                ),
            ).toPreparedMaterialMapping().descriptor,
        )

        assertEquals(GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX, descriptor.reason)
    }

    @Test
    fun `prepared alpha image keeps tint RGB and moves caller alpha to paint modulation`() {
        val mapping = Paint(
            color = ColorARGB.fromRGBA(0.25f, 0.5f, 0.75f, 0.5f),
            shader = imageShader(
                sourceId = "mask",
                pixels = byteArrayOf(0x80.toByte()),
                colorType = ColorType.ALPHA_8,
                alphaType = AlphaType.PREMUL,
            ),
        ).toPreparedMaterialMapping()
        val descriptor = assertIs<GPUMaterialDescriptor.ImageDraw>(mapping.descriptor)

        assertEquals(true, descriptor.alphaOnly)
        assertEquals(0.25f, descriptor.tintR, 0.002f)
        assertEquals(0.5f, descriptor.tintG, 0.002f)
        assertEquals(0.75f, descriptor.tintB, 0.002f)
        assertEquals(1f, descriptor.tintA)
        assertEquals(0.5f, mapping.paintAlpha, 0.002f)
    }

    @Test
    fun `prepared A8 image refuses non-premultiplied alpha types`() {
        listOf(AlphaType.UNPREMUL, AlphaType.UNKNOWN, AlphaType.OPAQUE).forEach { alphaType ->
            val unsupported = assertIs<GPUMaterialDescriptor.Unsupported>(
                Paint(
                    shader = imageShader(
                        sourceId = "a8-$alphaType",
                        pixels = byteArrayOf(0x80.toByte()),
                        colorType = ColorType.ALPHA_8,
                        alphaType = alphaType,
                    ),
                ).toPreparedMaterialMapping().descriptor,
                alphaType.name,
            )

            assertEquals(GPUPreparedMaterialUnsupportedReason.IMAGE_ALPHA_TYPE, unsupported.reason)
        }
    }

    @Test
    fun `prepared solid product path converts sRGB before one premultiplication`() {
        val mapping = Paint(
            color = ColorARGB.of(alpha = 128, red = 128, green = 128, blue = 128),
        ).toPreparedMaterialMapping()
        val program = compilePrepared(mapping)
        val uniform = program.uniformFloats()

        assertEquals(0.10835351f, uniform[0], 0.000001f)
        assertEquals(0.10835351f, uniform[1], 0.000001f)
        assertEquals(0.10835351f, uniform[2], 0.000001f)
        assertEquals(128f / 255f, uniform[3], 0.000001f)
        assertEquals(1f, program.paintAlpha)
        assertTrue("return source;" in program.composableFragment.evaluationFunctionWgsl)
    }

    @Test
    fun `prepared blend solid children convert sRGB before one premultiplication`() {
        val mapping = Paint(
            shader = Shader.Blend(
                mode = BlendMode.SRC_OVER,
                dst = Shader.SolidColor(ColorARGB.of(alpha = 128, red = 128, green = 128, blue = 128)),
                src = Shader.SolidColor(ColorARGB.of(alpha = 64, red = 128, green = 128, blue = 128)),
            ),
        ).toPreparedMaterialMapping()
        val uniform = compilePrepared(mapping).uniformFloats()

        assertEquals(0.10835351f, uniform[0], 0.000001f)
        assertEquals(128f / 255f, uniform[3], 0.000001f)
        assertEquals(0.05417676f, uniform[12], 0.000001f)
        assertEquals(64f / 255f, uniform[15], 0.000001f)
    }

    @Test
    fun `prepared A8 product tint converts sRGB and keeps caller alpha separate`() {
        val mapping = Paint(
            color = ColorARGB.of(alpha = 128, red = 128, green = 128, blue = 128),
            shader = imageShader(
                sourceId = "midtone-mask",
                pixels = byteArrayOf(0xff.toByte()),
                colorType = ColorType.ALPHA_8,
                alphaType = AlphaType.PREMUL,
            ),
        ).toPreparedMaterialMapping()
        val program = compilePrepared(mapping)
        val uniform = program.uniformFloats()

        assertEquals(0.2158605f, uniform[0], 0.000001f)
        assertEquals(0.2158605f, uniform[1], 0.000001f)
        assertEquals(0.2158605f, uniform[2], 0.000001f)
        assertEquals(1f, uniform[3])
        assertEquals(128f / 255f, program.paintAlpha, 0.000001f)
        assertEquals(0.10835351f, uniform[0] * program.paintAlpha, 0.000001f)
        assertTrue("return source;" in program.composableFragment.evaluationFunctionWgsl)
    }

    @Test
    fun `prepared runtime mapping snapshots exact uniform payload and child facts`() {
        val matrixValues = FloatArray(16) { index -> index.toFloat() }
        val childMap = linkedMapOf<String, Shader>(
            "input" to Shader.SolidColor(ColorARGB.Red),
        )
        val shader = Shader.RuntimeEffect(
            effect = RuntimeEffect(
                id = "runtime.simple_rt",
                module = ShaderModule.fromSource("registered-only"),
                uniformLayout = UniformLayout(emptyList()),
                children = emptyList(),
            ),
            uniforms = UniformBlock {
                float4("gColor", 0.25f, 0.5f, 0.75f, 1f)
                mat4x4("transform", matrixValues)
            },
            children = childMap,
        )

        val mapping = Paint(shader = shader).toPreparedMaterialMapping()
        matrixValues.fill(99f)
        childMap.clear()

        val descriptor = assertIs<GPUMaterialDescriptor.RuntimeEffect>(mapping.descriptor)
        assertEquals(
            GPURuntimeEffectUniformValue.Float4(0.25f, 0.5f, 0.75f, 1f),
            descriptor.uniforms.getValue("gColor"),
        )
        val matrix = assertIs<GPURuntimeEffectUniformValue.Matrix4x4>(
            descriptor.uniforms.getValue("transform"),
        )
        assertEquals((0 until 16).map(Int::toFloat), matrix.values)
        assertIs<GPUMaterialDescriptor.SolidColor>(descriptor.children.getValue("input"))
    }

    @Test
    fun `prepared mapping preserves legacy supported color filter behavior`() {
        val paint = Paint(
            color = ColorARGB.fromRGBA(0.2f, 0.4f, 0.6f, 0.8f),
            colorFilter = ColorFilter.Matrix(ColorMatrixF32.of(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 0.5f, 0f,
                ),
            )),
        )

        assertEquals(paint.toMaterial(), paint.toPreparedMaterialMapping().descriptor)
    }

    @Test
    fun `prepared mapping refuses every unimplemented semantic boundary with typed reasons`() {
        val gradient = Shader.LinearGradient(
            start = Point2F32(0f, 0f),
            end = Point2F32(1f, 1f),
            stops = listOf(
                GradientStop(0f, ColorARGB.Red),
                GradientStop(1f, ColorARGB.Blue),
            ),
        )
        val solid = Shader.SolidColor(ColorARGB.Red)
        val cases = listOf(
            Paint(
                shader = imageShader(
                    sourceId = "cubic",
                    pixels = byteArrayOf(1, 2, 3, 4),
                    sampling = SamplingOptions.Cubic.Mitchell,
                ),
            ) to GPUPreparedMaterialUnsupportedReason.IMAGE_CUBIC_SAMPLING,
            Paint(
                shader = imageShader(
                    sourceId = "repeat",
                    pixels = byteArrayOf(1, 2, 3, 4),
                    tileModeX = TileMode.REPEAT,
                ),
            ) to GPUPreparedMaterialUnsupportedReason.IMAGE_TILE_MODE,
            Paint(
                shader = imageShader(
                    sourceId = "unknown-format",
                    pixels = byteArrayOf(1, 2, 3, 4),
                    colorType = ColorType.RGB_888X,
                    alphaType = AlphaType.OPAQUE,
                ),
            ) to GPUPreparedMaterialUnsupportedReason.IMAGE_COLOR_TYPE,
            Paint(
                shader = imageShader(
                    sourceId = "premul",
                    pixels = byteArrayOf(1, 2, 3, 4),
                    alphaType = AlphaType.PREMUL,
                ),
            ) to GPUPreparedMaterialUnsupportedReason.IMAGE_ALPHA_TYPE,
            Paint(
                shader = imageShader(
                    sourceId = "unknown-alpha",
                    pixels = byteArrayOf(1, 2, 3, 4),
                    alphaType = AlphaType.UNKNOWN,
                ),
            ) to GPUPreparedMaterialUnsupportedReason.IMAGE_ALPHA_TYPE,
            Paint(
                shader = imageShader(
                    sourceId = "p3",
                    pixels = byteArrayOf(1, 2, 3, 4),
                    colorSpace = ColorSpace.DISPLAY_P3,
                ),
            ) to GPUPreparedMaterialUnsupportedReason.IMAGE_COLOR_SPACE,
            Paint(
                shader = gradient.copy(interpolation = ColorSpaceInterpolation.LINEAR),
            ) to GPUPreparedMaterialUnsupportedReason.GRADIENT_INTERPOLATION,
            Paint(
                shader = Shader.WithLocalMatrix(solid, Matrix3x3F32.Identity),
            ) to GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX,
            Paint(
                shader = Shader.WithWorkingColorSpace(solid, ColorSpaceInterpolation.OKLAB),
            ) to GPUPreparedMaterialUnsupportedReason.WORKING_COLOR_SPACE,
            Paint(
                shader = Shader.CoordClamp(solid, RectF32.ofOriginSize(0f, 0f, 1f, 1f)),
            ) to GPUPreparedMaterialUnsupportedReason.COORDINATE_CLAMP,
            Paint(
                shader = Shader.PerlinNoise(1f, 2f, 3, 4, SizeF32.of(8f, 8f)),
            ) to GPUPreparedMaterialUnsupportedReason.NOISE_SHADER,
            Paint(
                shader = Shader.FractalNoise(1f, 2f, 3, 4, null),
            ) to GPUPreparedMaterialUnsupportedReason.NOISE_SHADER,
            Paint(
                shader = Shader.WithColorFilter(solid, ColorFilter.HighContrast),
            ) to GPUPreparedMaterialUnsupportedReason.COLOR_FILTER,
        )

        cases.forEach { (paint, reason) ->
            val unsupported = assertIs<GPUMaterialDescriptor.Unsupported>(
                paint.toPreparedMaterialMapping().descriptor,
                reason.name,
            )
            assertEquals(reason, unsupported.reason)
        }
    }

    @Test
    fun `prepared runtime color filter retains its base and refuses filter placement`() {
        val base = Shader.SolidColor(ColorARGB.fromRGBA(0.25f, 0.5f, 0.75f, 1f))
        val filter = ColorFilter.RuntimeEffect(
            effect = testRuntimeEffect("runtime.filter"),
            uniforms = UniformBlock { float4("gColor", 1f, 0f, 0f, 1f) },
            children = mapOf("child" to ColorFilter.Luma),
        )

        val unsupported = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(shader = base, colorFilter = filter).toPreparedMaterialMapping().descriptor,
        )

        assertEquals(
            GPUPreparedMaterialUnsupportedReason.RUNTIME_COLOR_FILTER_PLACEMENT,
            unsupported.reason,
        )
        assertIs<GPUMaterialDescriptor.SolidColor>(unsupported.source)
    }

    @Test
    fun `prepared runtime color filter retains exact immutable canonical refusal evidence`() {
        val matrixUniform = FloatArray(16) { index -> index.toFloat() }
        val childMatrix = FloatArray(20) { index -> index.toFloat() / 10f }
        val childMap = linkedMapOf<String, ColorFilter>(
            "input" to ColorFilter.Compose(
                outer = ColorFilter.Matrix(ColorMatrixF32.of(childMatrix)),
                inner = ColorFilter.Blend(ColorARGB.Blue, BlendMode.SRC_OVER),
            ),
        )
        val filter = ColorFilter.RuntimeEffect(
            effect = testRuntimeEffect("runtime.filter.exact"),
            uniforms = UniformBlock {
                float1("amount", 0.25f)
                mat4x4("transform", matrixUniform)
            },
            children = childMap,
        )
        val equivalent = ColorFilter.RuntimeEffect(
            effect = testRuntimeEffect("runtime.filter.exact"),
            uniforms = UniformBlock {
                float1("amount", 0.25f)
                mat4x4("transform", FloatArray(16) { index -> index.toFloat() })
            },
            children = mapOf(
                "input" to ColorFilter.Compose(
                    outer = ColorFilter.Matrix(ColorMatrixF32.of(
                        FloatArray(20) { index -> index.toFloat() / 10f },
                    )),
                    inner = ColorFilter.Blend(ColorARGB.Blue, BlendMode.SRC_OVER),
                ),
            ),
        )

        val unsupported = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(colorFilter = filter).toPreparedMaterialMapping().descriptor,
        )
        val evidence = assertIs<GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter>(
            unsupported.evidence,
        )
        val equivalentEvidence =
            assertIs<GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter>(
                assertIs<GPUMaterialDescriptor.Unsupported>(
                    Paint(colorFilter = equivalent).toPreparedMaterialMapping().descriptor,
                ).evidence,
            )

        assertEquals("runtime.filter.exact", evidence.effectId)
        assertEquals(
            GPURuntimeEffectUniformValue.Float1(0.25f),
            evidence.uniforms.getValue("amount"),
        )
        assertEquals(setOf("input"), evidence.childIdentities.keys)
        assertTrue(evidence.childIdentities.getValue("input").matches(SHA256_IDENTITY))
        assertEquals(evidence, equivalentEvidence)
        assertEquals(evidence.hashCode(), equivalentEvidence.hashCode())

        val comparisonBaseline = runtimeColorFilterEvidence(
            effectId = "runtime.filter.exact",
            amount = 0.25f,
            childName = "input",
            child = ColorFilter.Luma,
        )
        val differingEvidence = listOf(
            runtimeColorFilterEvidence(
                effectId = "runtime.filter.other",
                amount = 0.25f,
                childName = "input",
                child = ColorFilter.Luma,
            ),
            runtimeColorFilterEvidence(
                effectId = "runtime.filter.exact",
                amount = 0.5f,
                childName = "input",
                child = ColorFilter.Luma,
            ),
            runtimeColorFilterEvidence(
                effectId = "runtime.filter.exact",
                amount = 0.25f,
                childName = "mask",
                child = ColorFilter.Luma,
            ),
            runtimeColorFilterEvidence(
                effectId = "runtime.filter.exact",
                amount = 0.25f,
                childName = "input",
                child = ColorFilter.Overdraw,
            ),
        )
        differingEvidence.forEach { other ->
            assertNotEquals(comparisonBaseline, other)
        }
        val nestedBaseline = runtimeColorFilterEvidence(
            effectId = "runtime.filter.exact",
            amount = 0.25f,
            childName = "input",
            child = ColorFilter.Compose(
                outer = ColorFilter.Matrix(ColorMatrixF32.of(FloatArray(20) { it.toFloat() })),
                inner = ColorFilter.Luma,
            ),
        )
        val changedDescendant = runtimeColorFilterEvidence(
            effectId = "runtime.filter.exact",
            amount = 0.25f,
            childName = "input",
            child = ColorFilter.Compose(
                outer = ColorFilter.Matrix(ColorMatrixF32.of(FloatArray(20) { it.toFloat() })),
                inner = ColorFilter.Overdraw,
            ),
        )
        assertNotEquals(nestedBaseline, changedDescendant)

        val retainedUniform = evidence.uniforms.getValue("transform")
        val retainedChildIdentity = evidence.childIdentities.getValue("input")
        matrixUniform.fill(99f)
        childMatrix.fill(99f)
        childMap.clear()

        assertEquals(
            (0 until 16).map(Int::toFloat),
            assertIs<GPURuntimeEffectUniformValue.Matrix4x4>(retainedUniform).values,
        )
        assertEquals(retainedChildIdentity, evidence.childIdentities.getValue("input"))
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (evidence.uniforms as MutableMap<String, GPURuntimeEffectUniformValue>).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (evidence.childIdentities as MutableMap<String, String>).clear()
        }
    }

    @Test
    fun `prepared runtime color filter wraps an already unsupported base with exact evidence`() {
        val filter = ColorFilter.RuntimeEffect(
            effect = testRuntimeEffect("runtime.filter.unsupported-base"),
            uniforms = UniformBlock { int1("mode", 7) },
            children = mapOf("input" to ColorFilter.SRGBToLinear),
        )

        val descriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(
                shader = Shader.WithLocalMatrix(
                    Shader.SolidColor(ColorARGB.Red),
                    Matrix3x3F32.Identity,
                ),
                colorFilter = filter,
            ).toPreparedMaterialMapping().descriptor,
        )

        assertEquals(
            GPUPreparedMaterialUnsupportedReason.RUNTIME_COLOR_FILTER_PLACEMENT,
            descriptor.reason,
        )
        val evidence = assertIs<GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter>(
            descriptor.evidence,
        )
        assertEquals("runtime.filter.unsupported-base", evidence.effectId)
        assertEquals(GPURuntimeEffectUniformValue.Int1(7), evidence.uniforms.getValue("mode"))
        assertEquals(setOf("input"), evidence.childIdentities.keys)
        assertEquals(
            GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX,
            assertIs<GPUMaterialDescriptor.Unsupported>(descriptor.source).reason,
        )
    }

    @Test
    fun `prepared unsupported gradient and image sources have immutable content value semantics`() {
        fun runtimeFilter(): ColorFilter.RuntimeEffect =
            ColorFilter.RuntimeEffect(
                effect = testRuntimeEffect("runtime.filter.value-source"),
                uniforms = UniformBlock.EMPTY,
                children = emptyMap(),
            )

        fun gradientRefusal(): GPUMaterialDescriptor.Unsupported =
            assertIs(
                Paint(
                    shader = Shader.LinearGradient(
                        start = Point2F32(0f, 0f),
                        end = Point2F32(10f, 0f),
                        stops = listOf(
                            GradientStop(0f, ColorARGB.fromRGBA(1f, 0f, 0f, 0.25f)),
                            GradientStop(1f, ColorARGB.fromRGBA(0f, 0f, 1f, 0.75f)),
                        ),
                    ),
                    colorFilter = runtimeFilter(),
                ).toPreparedMaterialMapping().descriptor,
            )

        fun imageRefusal(pixels: ByteArray): GPUMaterialDescriptor.Unsupported =
            assertIs(
                Paint(
                    shader = imageShader(
                        sourceId = "immutable-refusal",
                        pixels = pixels,
                    ),
                    colorFilter = runtimeFilter(),
                ).toPreparedMaterialMapping().descriptor,
            )

        val firstGradient = gradientRefusal()
        val equalGradient = gradientRefusal()
        val gradientHash = firstGradient.hashCode()
        val gradientString = firstGradient.toString()
        assertEquals(equalGradient, firstGradient)
        assertEquals(equalGradient.hashCode(), firstGradient.hashCode())
        assertEquals(equalGradient.toString(), firstGradient.toString())

        val escapedGradient =
            assertIs<GPUMaterialDescriptor.LinearGradient>(firstGradient.source)
        escapedGradient.allStopPositions!!.fill(99f)
        escapedGradient.allStopColors!!.fill(99f)
        assertEquals(equalGradient, firstGradient)
        assertEquals(gradientHash, firstGradient.hashCode())
        assertEquals(gradientString, firstGradient.toString())

        val callerPixels = byteArrayOf(1, 2, 3, 4)
        val firstImage = imageRefusal(callerPixels)
        val equalImage = imageRefusal(byteArrayOf(1, 2, 3, 4))
        val imageHash = firstImage.hashCode()
        val imageString = firstImage.toString()
        callerPixels.fill(99)
        assertEquals(equalImage, firstImage)
        assertEquals(equalImage.hashCode(), firstImage.hashCode())
        assertEquals(equalImage.toString(), firstImage.toString())

        assertIs<GPUMaterialDescriptor.ImageDraw>(firstImage.source)
            .rgbaPixels
            .fill(88)
        assertEquals(equalImage, firstImage)
        assertEquals(imageHash, firstImage.hashCode())
        assertEquals(imageString, firstImage.toString())
    }

    @Test
    fun `prepared shader mapping refuses identity cycles and permits a shared acyclic dag`() {
        val selfChildren = linkedMapOf<String, Shader>()
        val self = Shader.RuntimeEffect(
            effect = testRuntimeEffect("runtime.shader.self"),
            uniforms = UniformBlock.EMPTY,
            children = selfChildren,
        )
        selfChildren["self"] = self

        val leftChildren = linkedMapOf<String, Shader>()
        val rightChildren = linkedMapOf<String, Shader>()
        val left = Shader.RuntimeEffect(
            effect = testRuntimeEffect("runtime.shader.left"),
            uniforms = UniformBlock.EMPTY,
            children = leftChildren,
        )
        val right = Shader.RuntimeEffect(
            effect = testRuntimeEffect("runtime.shader.right"),
            uniforms = UniformBlock.EMPTY,
            children = rightChildren,
        )
        leftChildren["right"] = right
        rightChildren["left"] = left

        listOf(self, left).forEach { cyclic ->
            val descriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
                Paint(
                    shader = Shader.Blend(
                        mode = BlendMode.SRC_OVER,
                        dst = Shader.SolidColor(ColorARGB.Black),
                        src = cyclic,
                    ),
                ).toPreparedMaterialMapping().descriptor,
            )
            assertEquals(
                GPUPreparedMaterialUnsupportedReason.SHADER_GRAPH_CYCLE,
                descriptor.reason,
            )
        }

        val shared = Shader.RuntimeEffect(
            effect = testRuntimeEffect("runtime.shader.shared"),
            uniforms = UniformBlock { float1("amount", 0.5f) },
        )
        val dag = assertIs<GPUMaterialDescriptor.RuntimeEffect>(
            Paint(
                shader = Shader.RuntimeEffect(
                    effect = testRuntimeEffect("runtime.shader.parent"),
                    uniforms = UniformBlock.EMPTY,
                    children = linkedMapOf("left" to shared, "right" to shared),
                ),
            ).toPreparedMaterialMapping().descriptor,
        )

        assertEquals(setOf("left", "right"), dag.children.keys)
        assertEquals(dag.children.getValue("left"), dag.children.getValue("right"))
    }

    @Test
    fun `prepared runtime color filter evidence refuses identity cycles and permits a shared dag`() {
        val selfChildren = linkedMapOf<String, ColorFilter>()
        val self = ColorFilter.RuntimeEffect(
            effect = testRuntimeEffect("runtime.filter.self"),
            uniforms = UniformBlock.EMPTY,
            children = selfChildren,
        )
        selfChildren["self"] = self

        val leftChildren = linkedMapOf<String, ColorFilter>()
        val rightChildren = linkedMapOf<String, ColorFilter>()
        val left = ColorFilter.RuntimeEffect(
            effect = testRuntimeEffect("runtime.filter.left"),
            uniforms = UniformBlock.EMPTY,
            children = leftChildren,
        )
        val right = ColorFilter.RuntimeEffect(
            effect = testRuntimeEffect("runtime.filter.right"),
            uniforms = UniformBlock.EMPTY,
            children = rightChildren,
        )
        leftChildren["right"] = right
        rightChildren["left"] = left

        listOf(self, left).forEach { cyclic ->
            val descriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
                Paint(colorFilter = cyclic).toPreparedMaterialMapping().descriptor,
            )
            assertEquals(
                GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_CYCLE,
                descriptor.reason,
            )
        }

        val nestedChildren = linkedMapOf<String, ColorFilter>()
        val nestedRuntime = ColorFilter.RuntimeEffect(
            effect = testRuntimeEffect("runtime.filter.nested-cycle"),
            uniforms = UniformBlock.EMPTY,
            children = nestedChildren,
        )
        val nestedRoot = ColorFilter.Compose(
            outer = nestedRuntime,
            inner = ColorFilter.Luma,
        )
        nestedChildren["root"] = nestedRoot
        assertEquals(
            GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_CYCLE,
            assertIs<GPUMaterialDescriptor.Unsupported>(
                Paint(colorFilter = nestedRoot).toPreparedMaterialMapping().descriptor,
            ).reason,
        )

        val shared = ColorFilter.Compose(
            outer = ColorFilter.Matrix(ColorMatrixF32.of(FloatArray(20) { index -> index.toFloat() })),
            inner = ColorFilter.Luma,
        )
        val dag = ColorFilter.RuntimeEffect(
            effect = testRuntimeEffect("runtime.filter.dag"),
            uniforms = UniformBlock.EMPTY,
            children = linkedMapOf("left" to shared, "right" to shared),
        )
        val evidence = assertIs<GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter>(
            assertIs<GPUMaterialDescriptor.Unsupported>(
                Paint(colorFilter = dag).toPreparedMaterialMapping().descriptor,
            ).evidence,
        )

        assertEquals(setOf("left", "right"), evidence.childIdentities.keys)
        assertEquals(
            evidence.childIdentities.getValue("left"),
            evidence.childIdentities.getValue("right"),
        )
    }

    @Test
    fun `prepared shader depth budget returns a typed refusal only beyond the boundary`() {
        assertIs<GPUMaterialDescriptor.RuntimeEffect>(
            Paint(
                shader = shaderRuntimeChain(PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH),
            ).toPreparedMaterialMapping().descriptor,
        )

        val tooDeep = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(
                shader = shaderRuntimeChain(
                    PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH + 1,
                ),
            ).toPreparedMaterialMapping().descriptor,
        )
        assertEquals(
            GPUPreparedMaterialUnsupportedReason.SHADER_GRAPH_DEPTH,
            tooDeep.reason,
        )
    }

    @Test
    fun `prepared color filter depth budget returns a typed refusal only beyond the boundary`() {
        val boundary = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(
                colorFilter = colorFilterRuntimeChain(
                    PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH,
                ),
            ).toPreparedMaterialMapping().descriptor,
        )
        assertEquals(
            GPUPreparedMaterialUnsupportedReason.RUNTIME_COLOR_FILTER_PLACEMENT,
            boundary.reason,
        )

        val tooDeep = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(
                colorFilter = colorFilterRuntimeChain(
                    PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH + 1,
                ),
            ).toPreparedMaterialMapping().descriptor,
        )
        assertEquals(
            GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_DEPTH,
            tooDeep.reason,
        )
    }

    @Test
    fun `prepared shader cycle behind the depth boundary outranks depth`() {
        val descriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(
                shader = shaderRuntimeCycleBehindDepth(
                    activeDepth = PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH + 1,
                    ancestorIndex = PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH / 2,
                ),
            ).toPreparedMaterialMapping().descriptor,
        )

        assertEquals(
            GPUPreparedMaterialUnsupportedReason.SHADER_GRAPH_CYCLE,
            descriptor.reason,
        )
    }

    @Test
    fun `prepared color filter cycle behind the depth boundary outranks depth`() {
        val descriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(
                colorFilter = colorFilterRuntimeCycleBehindDepth(
                    activeDepth = PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH + 1,
                    ancestorIndex = PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH / 2,
                ),
            ).toPreparedMaterialMapping().descriptor,
        )

        assertEquals(
            GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_CYCLE,
            descriptor.reason,
        )
    }

    @Test
    fun `prepared shader mapping bounds a depth sixty shared diamond`() {
        val (shared, duplicated) = assertCompletesWithin(
            description = "shader diamond mapping",
        ) {
            val sharedRoot = shaderRuntimeDiamondRoot(
                childDepth = PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH - 5,
                shareRootChild = true,
            )
            val duplicatedRoot = shaderRuntimeDiamondRoot(
                childDepth = PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH - 5,
                shareRootChild = false,
            )
            Paint(shader = sharedRoot).toPreparedMaterialMapping().descriptor to
                Paint(shader = duplicatedRoot).toPreparedMaterialMapping().descriptor
        }

        assertEquals(duplicated, shared)
        assertEquals(duplicated.hashCode(), shared.hashCode())
        assertEquals(duplicated.toString(), shared.toString())
    }

    @Test
    fun `prepared mapper preserves a layered shared dag through one assembly session`() {
        val session = GPUMaterialDescriptorAssemblySession()
        val shared = Paint(
            shader = layeredRuntimeShaderDag(),
        ).toPreparedMaterialMapping(session).descriptor
        val independent = Paint(
            shader = layeredRuntimeShaderDag(),
        ).toPreparedMaterialMapping().descriptor

        assertIs<GPUMaterialDescriptor.RuntimeEffect>(shared)
        assertEquals(independent, shared)
        assertEquals(independent.hashCode(), shared.hashCode())
        assertEquals(independent.toString(), shared.toString())
    }

    @Test
    fun `prepared runtime color filter evidence bounds a depth sixty shared diamond`() {
        val (shared, duplicated) = assertCompletesWithin(
            description = "runtime color filter evidence diamond",
        ) {
            val sharedRoot = colorFilterRuntimeDiamondRoot(
                childDepth = PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH - 5,
                shareRootChild = true,
            )
            val duplicatedRoot = colorFilterRuntimeDiamondRoot(
                childDepth = PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH - 5,
                shareRootChild = false,
            )
            runtimeColorFilterEvidence(sharedRoot) to
                runtimeColorFilterEvidence(duplicatedRoot)
        }

        assertEquals(duplicated, shared)
        assertEquals(duplicated.hashCode(), shared.hashCode())
        assertEquals(duplicated.toString(), shared.toString())
        assertEquals(
            shared.childIdentities.getValue("left"),
            shared.childIdentities.getValue("right"),
        )
    }

    @Test
    fun `prepared shader cycle refusal outranks an earlier depth refusal`() {
        val cycleChildren = linkedMapOf<String, Shader>()
        val cycle = Shader.RuntimeEffect(
            effect = testRuntimeEffect("runtime.shader.mixed-cycle"),
            uniforms = UniformBlock.EMPTY,
            children = cycleChildren,
        )
        cycleChildren["self"] = cycle

        val descriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(
                shader = Shader.RuntimeEffect(
                    effect = testRuntimeEffect("runtime.shader.mixed-root"),
                    uniforms = UniformBlock.EMPTY,
                    children = linkedMapOf(
                        "a-deep" to shaderRuntimeChain(
                            PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH,
                        ),
                        "z-cycle" to cycle,
                    ),
                ),
            ).toPreparedMaterialMapping().descriptor,
        )

        assertEquals(
            GPUPreparedMaterialUnsupportedReason.SHADER_GRAPH_CYCLE,
            descriptor.reason,
        )
    }

    @Test
    fun `prepared color filter cycle refusal outranks an earlier depth refusal`() {
        val cycleChildren = linkedMapOf<String, ColorFilter>()
        val cycle = ColorFilter.RuntimeEffect(
            effect = testRuntimeEffect("runtime.filter.mixed-cycle"),
            uniforms = UniformBlock.EMPTY,
            children = cycleChildren,
        )
        cycleChildren["self"] = cycle

        val descriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(
                colorFilter = ColorFilter.RuntimeEffect(
                    effect = testRuntimeEffect("runtime.filter.mixed-root"),
                    uniforms = UniformBlock.EMPTY,
                    children = linkedMapOf(
                        "a-deep" to colorFilterRuntimeChain(
                            PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH,
                        ),
                        "z-cycle" to cycle,
                    ),
                ),
            ).toPreparedMaterialMapping().descriptor,
        )

        assertEquals(
            GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_CYCLE,
            descriptor.reason,
        )
    }

    @Test
    fun `global material cycle in paint color filter outranks shader depth`() {
        val cycleChildren = linkedMapOf<String, ColorFilter>()
        val cycle = ColorFilter.RuntimeEffect(
            effect = testRuntimeEffect("runtime.filter.paint-cross-type-cycle"),
            uniforms = UniformBlock.EMPTY,
            children = cycleChildren,
        )
        cycleChildren["self"] = cycle

        val descriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(
                shader = shaderRuntimeChain(
                    PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH + 1,
                ),
                colorFilter = cycle,
            ).toPreparedMaterialMapping().descriptor,
        )

        assertEquals(
            GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_CYCLE,
            descriptor.reason,
        )
    }

    @Test
    fun `global material cycle in shader wrapper color filter outranks shader depth`() {
        val cycleChildren = linkedMapOf<String, ColorFilter>()
        val cycle = ColorFilter.RuntimeEffect(
            effect = testRuntimeEffect("runtime.filter.wrapper-cross-type-cycle"),
            uniforms = UniformBlock.EMPTY,
            children = cycleChildren,
        )
        cycleChildren["self"] = cycle

        val descriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(
                shader = Shader.WithColorFilter(
                    shader = shaderRuntimeChain(
                        PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH,
                    ),
                    filter = cycle,
                ),
            ).toPreparedMaterialMapping().descriptor,
        )

        assertEquals(
            GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_CYCLE,
            descriptor.reason,
        )
    }

    @Test
    fun `global material shader cycle outranks paint color filter depth`() {
        val cycleChildren = linkedMapOf<String, Shader>()
        val cycle = Shader.RuntimeEffect(
            effect = testRuntimeEffect("runtime.shader.paint-cross-type-cycle"),
            uniforms = UniformBlock.EMPTY,
            children = cycleChildren,
        )
        cycleChildren["self"] = cycle

        val descriptor = assertIs<GPUMaterialDescriptor.Unsupported>(
            Paint(
                shader = cycle,
                colorFilter = colorFilterRuntimeChain(
                    PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH + 1,
                ),
            ).toPreparedMaterialMapping().descriptor,
        )

        assertEquals(
            GPUPreparedMaterialUnsupportedReason.SHADER_GRAPH_CYCLE,
            descriptor.reason,
        )
    }

    @Test
    fun `prepared image conversion proves channels alpha and supported metadata`() {
        val a8 = assertIs<GPUMaterialDescriptor.ImageDraw>(
            Paint(
                shader = imageShader(
                    sourceId = "a8",
                    pixels = byteArrayOf(0x80.toByte()),
                    colorType = ColorType.ALPHA_8,
                    alphaType = AlphaType.PREMUL,
                ),
            ).toPreparedMaterialMapping().descriptor,
        )
        val bgra = assertIs<GPUMaterialDescriptor.ImageDraw>(
            Paint(
                shader = imageShader(
                    sourceId = "bgra",
                    pixels = byteArrayOf(1, 2, 3, 4),
                    colorType = ColorType.BGRA_8888,
                    sampling = SamplingOptions.LINEAR,
                ),
            ).toPreparedMaterialMapping().descriptor,
        )
        val opaque = assertIs<GPUMaterialDescriptor.ImageDraw>(
            Paint(
                shader = imageShader(
                    sourceId = "opaque",
                    pixels = byteArrayOf(1, 2, 3, 4),
                    alphaType = AlphaType.OPAQUE,
                ),
            ).toPreparedMaterialMapping().descriptor,
        )

        assertContentEquals(byteArrayOf(0, 0, 0, 0x80.toByte()), a8.rgbaPixels)
        assertContentEquals(byteArrayOf(3, 2, 1, 4), bgra.rgbaPixels)
        assertEquals("linear", bgra.samplingFilterMode)
        assertContentEquals(byteArrayOf(1, 2, 3, 0xff.toByte()), opaque.rgbaPixels)
    }

    @Test
    fun `prepared image conversion expands bitmap configs to straight RGBA8`() {
        val rgb565 = assertIs<GPUMaterialDescriptor.ImageDraw>(
            Paint(
                shader = imageShader(
                    sourceId = "rgb565",
                    pixels = byteArrayOf(0x00, 0xf8.toByte()),
                    colorType = ColorType.RGB_565,
                    alphaType = AlphaType.OPAQUE,
                ),
            ).toPreparedMaterialMapping().descriptor,
        )
        val argb4444 = assertIs<GPUMaterialDescriptor.ImageDraw>(
            Paint(
                shader = imageShader(
                    sourceId = "argb4444",
                    pixels = byteArrayOf(0x21, 0x84.toByte()),
                    colorType = ColorType.ARGB_4444,
                    alphaType = AlphaType.PREMUL,
                ),
            ).toPreparedMaterialMapping().descriptor,
        )
        val rgbaF16 = assertIs<GPUMaterialDescriptor.ImageDraw>(
            Paint(
                shader = Shader.Image(
                    requireNotNull(
                        Bitmap(1, 1, ColorType.RGBA_F16).also {
                            it.setPixel(0, 0, ColorARGB.fromRGBA(0.5f, 0.25f, 0.75f, 0.5f))
                        }.toImageOrNull(),
                    ),
                ),
            ).toPreparedMaterialMapping().descriptor,
        )
        val gray8 = assertIs<GPUMaterialDescriptor.ImageDraw>(
            Paint(
                shader = imageShader(
                    sourceId = "gray8",
                    pixels = byteArrayOf(0x80.toByte()),
                    colorType = ColorType.GRAY_8,
                    alphaType = AlphaType.OPAQUE,
                ),
            ).toPreparedMaterialMapping().descriptor,
        )

        assertContentEquals(byteArrayOf(0xff.toByte(), 0, 0, 0xff.toByte()), rgb565.rgbaPixels)
        assertContentEquals(byteArrayOf(128.toByte(), 64, 32, 136.toByte()), argb4444.rgbaPixels)
        assertContentEquals(byteArrayOf(128.toByte(), 64, 191.toByte(), 128.toByte()), rgbaF16.rgbaPixels)
        assertContentEquals(byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0xff.toByte()), gray8.rgbaPixels)
    }

    @Test
    fun `prepared image mapping snapshots caller pixels before descriptor escape`() {
        val pixels = byteArrayOf(1, 2, 3, 4)
        val image = Image(
            width = 1,
            height = 1,
            colorType = ColorType.RGBA_8888,
            sourceId = "snapshot",
            pixels = pixels,
        )
        val descriptor = assertIs<GPUMaterialDescriptor.ImageDraw>(
            Paint(shader = Shader.Image(image)).toPreparedMaterialMapping().descriptor,
        )

        pixels.fill(99)
        image.pixels!!.fill(88)

        assertContentEquals(byteArrayOf(1, 2, 3, 4), descriptor.rgbaPixels)
    }

    @Test
    fun `prepared image conversion refuses malformed and overflowing payloads without throwing`() {
        val invalidImages = listOf(
            Image(
                width = 0,
                height = 1,
                colorType = ColorType.RGBA_8888,
                sourceId = "zero-width",
                pixels = byteArrayOf(),
            ),
            Image(
                width = -1,
                height = 1,
                colorType = ColorType.RGBA_8888,
                sourceId = "negative-width",
                pixels = byteArrayOf(),
            ),
            Image(
                width = 1,
                height = 1,
                colorType = ColorType.RGBA_8888,
                sourceId = "rgba-short",
                pixels = byteArrayOf(1, 2, 3),
            ),
            Image(
                width = 1,
                height = 1,
                colorType = ColorType.RGBA_8888,
                sourceId = "rgba-long",
                pixels = byteArrayOf(1, 2, 3, 4, 5),
            ),
            Image(
                width = 1,
                height = 1,
                colorType = ColorType.BGRA_8888,
                sourceId = "bgra-short",
                pixels = byteArrayOf(1, 2),
            ),
            Image(
                width = 1,
                height = 1,
                colorType = ColorType.BGRA_8888,
                sourceId = "bgra-long",
                pixels = byteArrayOf(1, 2, 3, 4, 5),
            ),
            Image(
                width = 2,
                height = 1,
                colorType = ColorType.ALPHA_8,
                alphaType = AlphaType.PREMUL,
                sourceId = "a8-short",
                pixels = byteArrayOf(1),
            ),
            Image(
                width = 1,
                height = 1,
                colorType = ColorType.ALPHA_8,
                alphaType = AlphaType.PREMUL,
                sourceId = "a8-long",
                pixels = byteArrayOf(1, 2),
            ),
            Image(
                width = Int.MAX_VALUE,
                height = Int.MAX_VALUE,
                colorType = ColorType.ALPHA_8,
                alphaType = AlphaType.PREMUL,
                sourceId = "overflow",
                pixels = byteArrayOf(1),
            ),
            Image(
                width = 1,
                height = 1,
                colorType = ColorType.RGBA_8888,
                sourceId = "missing",
                pixels = null,
            ),
        )

        invalidImages.forEach { image ->
            val mapping = runCatching {
                Paint(shader = Shader.Image(image)).toPreparedMaterialMapping()
            }
            assertTrue(mapping.isSuccess, image.sourceId)
            val unsupported = assertIs<GPUMaterialDescriptor.Unsupported>(
                mapping.getOrThrow().descriptor,
                image.sourceId,
            )
            assertEquals("IMAGE_PIXEL_PAYLOAD", unsupported.reason.name)
        }
    }

    @Test
    fun `legacy mapping keeps its existing approximations and A8 expansion`() {
        val cubicRepeat = imageShader(
            sourceId = "legacy-cubic-repeat",
            pixels = byteArrayOf(1, 2, 3, 4),
            tileModeX = TileMode.REPEAT,
            sampling = SamplingOptions.Cubic.Mitchell,
        )
        val legacyImage = assertIs<GPUMaterialDescriptor.ImageDraw>(
            Paint(shader = cubicRepeat).toMaterial(),
        )
        val legacyA8 = assertIs<GPUMaterialDescriptor.ImageDraw>(
            Paint(
                shader = imageShader(
                    sourceId = "legacy-a8",
                    pixels = byteArrayOf(0x80.toByte()),
                    colorType = ColorType.ALPHA_8,
                ),
            ).toMaterial(),
        )
        val legacyWrapped = assertIs<GPUMaterialDescriptor.SolidColor>(
            Paint(
                shader = Shader.WithLocalMatrix(
                    Shader.SolidColor(ColorARGB.Red),
                    Matrix3x3F32.Identity,
                ),
            ).toMaterial(),
        )
        val legacyNoise = assertIs<GPUMaterialDescriptor.SolidColor>(
            Paint(shader = Shader.PerlinNoise(1f, 2f, 3, 4, null)).toMaterial(),
        )

        assertEquals("linear", legacyImage.samplingFilterMode)
        assertContentEquals(
            byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte()),
            legacyA8.rgbaPixels,
        )
        assertEquals(ColorARGB.Red.r, legacyWrapped.r)
        assertEquals(0f, legacyNoise.a)
    }

    private fun imageShader(
        sourceId: String,
        pixels: ByteArray,
        colorType: ColorType = ColorType.RGBA_8888,
        alphaType: AlphaType = AlphaType.UNPREMUL,
        colorSpace: ColorSpace = ColorSpace.SRGB,
        tileModeX: TileMode = TileMode.CLAMP,
        tileModeY: TileMode = TileMode.CLAMP,
        sampling: SamplingOptions = SamplingOptions.NEAREST,
    ): Shader.Image =
        Shader.Image(
            Image(
                width = 1,
                height = 1,
                pixels = pixels,
                colorType = colorType,
                sourceId = sourceId,
                colorSpace = colorSpace,
                alphaType = alphaType,
            ),
            tileModeX = tileModeX,
            tileModeY = tileModeY,
            sampling = sampling,
        )

    private fun threeGradientStops(): List<GradientStop> = listOf(
        GradientStop(0f, ColorARGB.fromRGBA(1f, 0.1f, 0.2f, 0.9f)),
        GradientStop(0.35f, ColorARGB.fromRGBA(0.3f, 0.4f, 0.5f, 0.6f)),
        GradientStop(1f, ColorARGB.fromRGBA(0.7f, 0.8f, 0.9f, 1f)),
    )

    private fun threeGradientColors(): FloatArray =
        threeGradientStops().flatMap { stop ->
            listOf(stop.color.r, stop.color.g, stop.color.b, stop.color.a)
        }.toFloatArray()

    private fun testRuntimeEffect(id: String): RuntimeEffect =
        RuntimeEffect(
            id = id,
            module = ShaderModule.fromSource("registered-only"),
            uniformLayout = UniformLayout(emptyList()),
            children = emptyList(),
        )

    private fun shaderRuntimeChain(activeDepth: Int): Shader {
        require(activeDepth >= 1)
        var shader: Shader = Shader.SolidColor(ColorARGB.Red)
        repeat(activeDepth - 1) { index ->
            val child = shader
            shader = Shader.RuntimeEffect(
                effect = testRuntimeEffect("runtime.shader.depth-$index"),
                uniforms = UniformBlock.EMPTY,
                children = mapOf("child" to child),
            )
        }
        return shader
    }

    private fun colorFilterRuntimeChain(activeDepth: Int): ColorFilter {
        require(activeDepth >= 1)
        var filter: ColorFilter = ColorFilter.Luma
        repeat(activeDepth - 1) { index ->
            val child = filter
            filter = ColorFilter.RuntimeEffect(
                effect = testRuntimeEffect("runtime.filter.depth-$index"),
                uniforms = UniformBlock.EMPTY,
                children = mapOf("child" to child),
            )
        }
        return filter
    }

    private fun shaderRuntimeCycleBehindDepth(
        activeDepth: Int,
        ancestorIndex: Int,
    ): Shader {
        require(activeDepth >= 2)
        require(ancestorIndex in 0 until activeDepth - 1)
        val children = List(activeDepth) { linkedMapOf<String, Shader>() }
        val nodes = List(activeDepth) { index ->
            Shader.RuntimeEffect(
                effect = testRuntimeEffect("runtime.shader.deep-cycle-$index"),
                uniforms = UniformBlock.EMPTY,
                children = children[index],
            )
        }
        repeat(activeDepth - 1) { index ->
            children[index]["next"] = nodes[index + 1]
        }
        children.last()["back"] = nodes[ancestorIndex]
        return nodes.first()
    }

    private fun colorFilterRuntimeCycleBehindDepth(
        activeDepth: Int,
        ancestorIndex: Int,
    ): ColorFilter {
        require(activeDepth >= 2)
        require(ancestorIndex in 0 until activeDepth - 1)
        val children =
            List(activeDepth) { linkedMapOf<String, ColorFilter>() }
        val nodes = List(activeDepth) { index ->
            ColorFilter.RuntimeEffect(
                effect = testRuntimeEffect("runtime.filter.deep-cycle-$index"),
                uniforms = UniformBlock.EMPTY,
                children = children[index],
            )
        }
        repeat(activeDepth - 1) { index ->
            children[index]["next"] = nodes[index + 1]
        }
        children.last()["back"] = nodes[ancestorIndex]
        return nodes.first()
    }

    private fun shaderRuntimeDiamondRoot(
        childDepth: Int,
        shareRootChild: Boolean,
    ): Shader.RuntimeEffect {
        val left = shaderRuntimeDiamond(childDepth)
        val right = if (shareRootChild) left else shaderRuntimeDiamond(childDepth)
        return Shader.RuntimeEffect(
            effect = testRuntimeEffect("runtime.shader.diamond-root"),
            uniforms = UniformBlock.EMPTY,
            children = linkedMapOf("left" to left, "right" to right),
        )
    }

    private fun shaderRuntimeDiamond(depth: Int): Shader {
        var shader: Shader = Shader.SolidColor(ColorARGB.Red)
        repeat(depth) { index ->
            val sharedChild = shader
            shader = Shader.RuntimeEffect(
                effect = testRuntimeEffect("runtime.shader.diamond-$index"),
                uniforms = UniformBlock.EMPTY,
                children = linkedMapOf(
                    "left" to sharedChild,
                    "right" to sharedChild,
                ),
            )
        }
        return shader
    }

    private fun layeredRuntimeShaderDag(): Shader.RuntimeEffect {
        var previous = listOf<Shader>(Shader.SolidColor(ColorARGB.Red))
        val width = 3
        repeat(4) { layer ->
            previous = List(width) { node ->
                Shader.RuntimeEffect(
                    effect = testRuntimeEffect("runtime.shader.layer.$layer.$node"),
                    uniforms = UniformBlock.EMPTY,
                    children = previous.mapIndexed { index, child ->
                        "child-$index" to child
                    }.toMap(LinkedHashMap()),
                )
            }
        }
        return Shader.RuntimeEffect(
            effect = testRuntimeEffect("runtime.shader.layer.root"),
            uniforms = UniformBlock.EMPTY,
            children = previous.mapIndexed { index, child -> "root-$index" to child }
                .toMap(LinkedHashMap()),
        )
    }

    private fun colorFilterRuntimeDiamondRoot(
        childDepth: Int,
        shareRootChild: Boolean,
    ): ColorFilter.RuntimeEffect {
        val left = colorFilterRuntimeDiamond(childDepth)
        val right = if (shareRootChild) {
            left
        } else {
            colorFilterRuntimeDiamond(childDepth)
        }
        return ColorFilter.RuntimeEffect(
            effect = testRuntimeEffect("runtime.filter.diamond-root"),
            uniforms = UniformBlock.EMPTY,
            children = linkedMapOf("left" to left, "right" to right),
        )
    }

    private fun colorFilterRuntimeDiamond(depth: Int): ColorFilter {
        var filter: ColorFilter = ColorFilter.Luma
        repeat(depth) { index ->
            val sharedChild = filter
            filter = ColorFilter.RuntimeEffect(
                effect = testRuntimeEffect("runtime.filter.diamond-$index"),
                uniforms = UniformBlock.EMPTY,
                children = linkedMapOf(
                    "left" to sharedChild,
                    "right" to sharedChild,
                ),
            )
        }
        return filter
    }

    private fun runtimeColorFilterEvidence(
        filter: ColorFilter.RuntimeEffect,
    ): GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter =
        assertIs(
            assertIs<GPUMaterialDescriptor.Unsupported>(
                Paint(colorFilter = filter).toPreparedMaterialMapping().descriptor,
            ).evidence,
        )

    private fun runtimeColorFilterEvidence(
        effectId: String,
        amount: Float,
        childName: String,
        child: ColorFilter,
    ): GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter {
        val filter = ColorFilter.RuntimeEffect(
            effect = testRuntimeEffect(effectId),
            uniforms = UniformBlock { float1("amount", amount) },
            children = mapOf(childName to child),
        )
        return assertIs(
            assertIs<GPUMaterialDescriptor.Unsupported>(
                Paint(colorFilter = filter).toPreparedMaterialMapping().descriptor,
            ).evidence,
        )
    }

    private fun <T : Any> assertCompletesWithin(
        description: String,
        timeoutSeconds: Long = 5,
        block: () -> T,
    ): T {
        val completed = CountDownLatch(1)
        val value = AtomicReference<T>()
        val failure = AtomicReference<Throwable?>()
        Thread(
            {
                try {
                    value.set(block())
                } catch (caught: Throwable) {
                    failure.set(caught)
                } finally {
                    completed.countDown()
                }
            },
            "prepared-material-$description",
        ).apply {
            isDaemon = true
            start()
        }

        assertTrue(
            completed.await(timeoutSeconds, TimeUnit.SECONDS),
            "$description did not complete within $timeoutSeconds seconds",
        )
        failure.get()?.let { throw it }
        return checkNotNull(value.get()) {
            "$description completed without a value"
        }
    }

    private fun compilePrepared(mapping: GPUPreparedMaterialMapping): GPUPreparedMaterialProgram =
        assertIs<GPUPreparedMaterialProgramResult.Ready>(
            GPUPreparedMaterialProgramCompiler.compile(
                descriptor = mapping.descriptor,
                paintAlpha = mapping.paintAlpha,
                context = preparedTextMaterialContext(target(), capabilities()),
            ),
        ).program

    private fun GPUPreparedMaterialProgram.uniformFloats(): List<Float> =
        ByteBuffer.wrap(uniformBytes.map(Int::toByte).toByteArray())
            .order(ByteOrder.LITTLE_ENDIAN)
            .let { buffer ->
                List(uniformBytes.size / Float.SIZE_BYTES) { buffer.float }
            }

    private companion object {
        val SHA256_IDENTITY = Regex("""sha256:[0-9a-f]{64}""")
    }
}
