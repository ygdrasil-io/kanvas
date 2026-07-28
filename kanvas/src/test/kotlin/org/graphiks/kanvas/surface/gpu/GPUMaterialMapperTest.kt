package org.graphiks.kanvas.surface.gpu

import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedMaterialUnsupportedEvidence
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedMaterialUnsupportedReason
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectUniformValue
import org.graphiks.kanvas.image.AlphaType
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
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.ColorSpace
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Size
import org.graphiks.kanvas.types.b
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.r
import org.junit.jupiter.api.Test

class GPUMaterialMapperTest {
    @Test
    fun `legacy unsupported blend falls back to source while prepared mapping preserves blend`() {
        val dst = Shader.ConicalGradient(
            start = Point(0f, 0f),
            startRadius = 0f,
            end = Point(10f, 10f),
            endRadius = 10f,
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(1f, 0f, 0f, 1f)),
                GradientStop(1f, Color.fromRGBA(0f, 0f, 1f, 1f)),
            ),
        )
        val src = Shader.SolidColor(Color.fromRGBA(0.25f, 0.5f, 0.75f, 1f))
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
            color = Color.fromRGBA(0.2f, 0.4f, 0.6f, 0.5f),
        ).toPreparedMaterialMapping()
        val solid = assertIs<GPUMaterialDescriptor.SolidColor>(mapping.descriptor)

        assertEquals(0.5f, solid.a, 0.002f)
        assertEquals(1f, mapping.paintAlpha)
    }

    @Test
    fun `prepared gradient mapping retains source alpha and separates caller modulation`() {
        val gradient = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(10f, 0f),
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(1f, 0f, 0f, 0.25f)),
                GradientStop(1f, Color.fromRGBA(0f, 0f, 1f, 0.75f)),
            ),
        )
        val mapping = Paint(
            color = Color.fromRGBA(1f, 1f, 1f, 0.5f),
            shader = gradient,
        ).toPreparedMaterialMapping()
        val descriptor = assertIs<GPUMaterialDescriptor.LinearGradient>(mapping.descriptor)

        assertEquals(0.25f, descriptor.startA, 0.002f)
        assertEquals(0.75f, descriptor.endA, 0.002f)
        assertEquals(0.5f, mapping.paintAlpha, 0.002f)
    }

    @Test
    fun `prepared alpha image keeps tint RGB and moves caller alpha to paint modulation`() {
        val mapping = Paint(
            color = Color.fromRGBA(0.25f, 0.5f, 0.75f, 0.5f),
            shader = imageShader("mask", byteArrayOf(0x80.toByte()), ColorType.ALPHA_8),
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
    fun `prepared runtime mapping snapshots exact uniform payload and child facts`() {
        val matrixValues = FloatArray(16) { index -> index.toFloat() }
        val childMap = linkedMapOf<String, Shader>(
            "input" to Shader.SolidColor(Color.RED),
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
            color = Color.fromRGBA(0.2f, 0.4f, 0.6f, 0.8f),
            colorFilter = ColorFilter.Matrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 0.5f, 0f,
                ),
            ),
        )

        assertEquals(paint.toMaterial(), paint.toPreparedMaterialMapping().descriptor)
    }

    @Test
    fun `prepared mapping refuses every unimplemented semantic boundary with typed reasons`() {
        val gradient = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(1f, 1f),
            stops = listOf(
                GradientStop(0f, Color.RED),
                GradientStop(1f, Color.BLUE),
            ),
        )
        val solid = Shader.SolidColor(Color.RED)
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
                    sourceId = "gray",
                    pixels = byteArrayOf(1),
                    colorType = ColorType.GRAY_8,
                ),
            ) to GPUPreparedMaterialUnsupportedReason.IMAGE_COLOR_TYPE,
            Paint(
                shader = imageShader(
                    sourceId = "f16",
                    pixels = ByteArray(8),
                    colorType = ColorType.RGBA_F16,
                ),
            ) to GPUPreparedMaterialUnsupportedReason.IMAGE_COLOR_TYPE,
            Paint(
                shader = imageShader(
                    sourceId = "565",
                    pixels = ByteArray(2),
                    colorType = ColorType.RGB_565,
                ),
            ) to GPUPreparedMaterialUnsupportedReason.IMAGE_COLOR_TYPE,
            Paint(
                shader = imageShader(
                    sourceId = "4444",
                    pixels = ByteArray(2),
                    colorType = ColorType.ARGB_4444,
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
                shader = Shader.WithLocalMatrix(solid, Matrix33.identity()),
            ) to GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX,
            Paint(
                shader = Shader.WithWorkingColorSpace(solid, ColorSpaceInterpolation.OKLAB),
            ) to GPUPreparedMaterialUnsupportedReason.WORKING_COLOR_SPACE,
            Paint(
                shader = Shader.CoordClamp(solid, Rect.fromXYWH(0f, 0f, 1f, 1f)),
            ) to GPUPreparedMaterialUnsupportedReason.COORDINATE_CLAMP,
            Paint(
                shader = Shader.PerlinNoise(1f, 2f, 3, 4, Size(8f, 8f)),
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
        val base = Shader.SolidColor(Color.fromRGBA(0.25f, 0.5f, 0.75f, 1f))
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
                outer = ColorFilter.Matrix(childMatrix),
                inner = ColorFilter.Blend(Color.BLUE, BlendMode.SRC_OVER),
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
                    outer = ColorFilter.Matrix(
                        FloatArray(20) { index -> index.toFloat() / 10f },
                    ),
                    inner = ColorFilter.Blend(Color.BLUE, BlendMode.SRC_OVER),
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
                outer = ColorFilter.Matrix(FloatArray(20) { it.toFloat() }),
                inner = ColorFilter.Luma,
            ),
        )
        val changedDescendant = runtimeColorFilterEvidence(
            effectId = "runtime.filter.exact",
            amount = 0.25f,
            childName = "input",
            child = ColorFilter.Compose(
                outer = ColorFilter.Matrix(FloatArray(20) { it.toFloat() }),
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
                    Shader.SolidColor(Color.RED),
                    Matrix33.identity(),
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
                        dst = Shader.SolidColor(Color.BLACK),
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
            outer = ColorFilter.Matrix(FloatArray(20) { index -> index.toFloat() }),
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
    fun `prepared image conversion proves channels alpha and supported metadata`() {
        val a8 = assertIs<GPUMaterialDescriptor.ImageDraw>(
            Paint(
                shader = imageShader(
                    sourceId = "a8",
                    pixels = byteArrayOf(0x80.toByte()),
                    colorType = ColorType.ALPHA_8,
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
                sourceId = "a8-short",
                pixels = byteArrayOf(1),
            ),
            Image(
                width = 1,
                height = 1,
                colorType = ColorType.ALPHA_8,
                sourceId = "a8-long",
                pixels = byteArrayOf(1, 2),
            ),
            Image(
                width = Int.MAX_VALUE,
                height = Int.MAX_VALUE,
                colorType = ColorType.ALPHA_8,
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
                    Shader.SolidColor(Color.RED),
                    Matrix33.identity(),
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
        assertEquals(Color.RED.r, legacyWrapped.r)
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

    private fun testRuntimeEffect(id: String): RuntimeEffect =
        RuntimeEffect(
            id = id,
            module = ShaderModule.fromSource("registered-only"),
            uniformLayout = UniformLayout(emptyList()),
            children = emptyList(),
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

    private companion object {
        val SHA256_IDENTITY = Regex("""sha256:[0-9a-f]{64}""")
    }
}
