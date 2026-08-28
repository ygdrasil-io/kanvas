package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName.SCISSOR_NATIVE
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometryClass
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageRouteCapability
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32

class GPUPreparedDrawImageLowererTest {

    private val boundedW28Config = RenderConfig(
        preparedImageRouteCapability = GPUPreparedImageRouteCapability.BoundedNearest1To1,
    )

    private fun target() = GPUTargetFacts(64, 64, "rgba8unorm-srgb")

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity(
            facadeName = "test",
            implementationName = "fake",
            adapterName = "mock",
            deviceName = "mock-device",
        ),
        facts = listOf(
            GPUCapabilityFact(
                name = SCISSOR_NATIVE,
                source = "test",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "test:$SCISSOR_NATIVE",
            ),
        ),
        knownUnsupportedFacts = emptyList(),
        snapshotId = "fp04-lowerer-test",
        limits = GPULimits(
            maxTextureDimension2D = 8192,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        rendererFeatures = buildSet { add(GPURendererFeature.RenderPass) },
    )

    private fun commonRgbaImage(sourceId: String = "fixture-rgba"): Image = Image(
        width = GPUPreparedImageTestFixtures.rgbaPremul2x2Width,
        height = GPUPreparedImageTestFixtures.rgbaPremul2x2Height,
        colorType = GPUPreparedImageTestFixtures.rgbaPremul2x2ColorType,
        sourceId = sourceId,
        pixels = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes,
        alphaType = AlphaType.PREMUL,
    )

    private fun commonA8Image(sourceId: String = "fixture-a8"): Image = Image(
        width = GPUPreparedImageTestFixtures.a8_3x1Width,
        height = GPUPreparedImageTestFixtures.a8_3x1Height,
        colorType = GPUPreparedImageTestFixtures.a8_3x1ColorType,
        sourceId = sourceId,
        pixels = GPUPreparedImageTestFixtures.a8_3x1Bytes,
        alphaType = AlphaType.PREMUL,
    )

    private fun rgbaImage(
        width: Int = 4,
        height: Int = 3,
        r: Int = 128,
        g: Int = 128,
        b: Int = 128,
        a: Int = 255,
        sourceId: String = "test-rgba",
    ): Image {
        val pixels = ByteArray(width * height * 4) { i ->
            when (i % 4) {
                0 -> r.toByte()
                1 -> g.toByte()
                2 -> b.toByte()
                else -> a.toByte()
            }
        }
        return Image(
            width = width,
            height = height,
            colorType = ColorType.RGBA_8888,
            sourceId = sourceId,
            pixels = pixels,
            alphaType = AlphaType.PREMUL,
        )
    }

    private fun a8Image(width: Int = 4, height: Int = 3, alpha: Int = 200, sourceId: String = "test-a8"): Image {
        val pixels = ByteArray(width * height) { alpha.toByte() }
        return Image(
            width = width,
            height = height,
            colorType = ColorType.ALPHA_8,
            sourceId = sourceId,
            pixels = pixels,
            alphaType = AlphaType.PREMUL,
        )
    }

    private fun drawImage(
        image: Image,
        dst: RectF32 = RectF32.ofLTRB(
            10f,
            10f,
            10f + image.width.toFloat(),
            10f + image.height.toFloat(),
        ),
        src: RectF32 = RectF32.ofLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat()),
        paint: Paint? = null,
        transform: Matrix3x3F32 = Matrix3x3F32.Identity,
    ): DisplayOp.DrawImage = DisplayOp.DrawImage(
        image = image,
        src = src,
        dst = dst,
        paint = paint,
        transform = transform,
        clip = ClipStack.WideOpen,
    )

    private fun assertPositions(
        expected: List<Pair<Float, Float>>,
        actual: List<Pair<Float, Float>>,
    ) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEachIndexed { index, (want, got) ->
            assertEquals(want.first, got.first, 0.0001f, "x at vertex $index")
            assertEquals(want.second, got.second, 0.0001f, "y at vertex $index")
        }
    }

    @Test
    fun `snapshot of pixels before mutation by caller`() {
        val image = rgbaImage()
        val originalPixels = image.pixels!!.copyOf()

        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                boundedW28Config,
                capabilities(),
            ),
        )
        val facts = assertNotNull(result.command.preparedImage)

        image.pixels!![0] = 42
        val uploaded = facts.artifact.tightRgba8BytesForUpload()
        for (i in originalPixels.indices) {
            assertEquals(originalPixels[i], uploaded[i], "byte mismatch at index $i")
        }
    }

    @Test
    fun `two draws of same Image share one artifact`() {
        val image = rgbaImage()
        val result1 = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        val result2 = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, RectF32.ofLTRB(20f, 20f, 24f, 23f)),
                GPUDrawCommandID(1),
                1,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )

        val key1 = result1.command.preparedImage!!.artifact.key
        val key2 = result2.command.preparedImage!!.artifact.key
        assertEquals(key1, key2)
    }

    @Test
    fun `RGBA command keeps logical premul facts while artifact owns straight upload bytes`() {
        val image = rgbaImage(r = 64, g = 32, b = 16, a = 128)
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )

        val normalized = assertIs<NormalizedDrawCommand.DrawImageRect>(result.command.normalized)
        val artifact = result.command.preparedImage!!.artifact
        assertEquals("RGBA8Unorm", normalized.pixelsFormat)
        assertEquals("Premul", normalized.pixelsAlphaType)
        assertEquals("srgb", normalized.pixelsColorProfileLabel)
        assertEquals(artifact.contentHash, normalized.pixelsContentHash)
        assertEquals("prepared-surface-artifact", normalized.pixelsProvenance)
        assertContentEquals(
            byteArrayOf(128.toByte(), 64, 32, 128.toByte()),
            artifact.tightRgba8BytesForUpload().copyOfRange(0, 4),
        )
    }

    @Test
    fun `A8 command keeps logical facts while artifact owns linear coverage bytes`() {
        val image = a8Image(alpha = 200)
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )

        val normalized = assertIs<NormalizedDrawCommand.DrawImageRect>(result.command.normalized)
        val artifact = result.command.preparedImage!!.artifact
        assertEquals("RGBA8Unorm", normalized.pixelsFormat)
        assertEquals("Premul", normalized.pixelsAlphaType)
        assertTrue(artifact.alphaOnly)
        assertContentEquals(
            byteArrayOf(200.toByte(), 200.toByte(), 200.toByte(), 200.toByte()),
            artifact.tightRgba8BytesForUpload().copyOfRange(0, 4),
        )
    }

    @Test
    fun `two distinct images with equal sourceId remain distinct`() {
        val imageA = rgbaImage(sourceId = "shared-id", r = 10)
        val imageB = rgbaImage(sourceId = "shared-id", r = 20)

        val resultA = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(imageA),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        val resultB = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(imageB),
                GPUDrawCommandID(1),
                1,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )

        val keyA = resultA.command.preparedImage!!.artifact.key
        val keyB = resultB.command.preparedImage!!.artifact.key
        assertNotEquals(keyA, keyB)
    }

    @Test
    fun `source crop is refused by the bounded native geometry contract`() {
        val image = rgbaImage(width = 8, height = 6)
        val src = RectF32.ofLTRB(1f, 2f, 5f, 4f)
        val result = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, src = src),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                boundedW28Config,
                capabilities(),
            ),
        )
        assertEquals(GPUPreparedImageRefusalCodes.RECT_GEOMETRY, result.code)
    }

    @Test
    fun `identity transform produces rect geometry with exact positions`() {
        val image = commonRgbaImage()
        val dst = RectF32.ofLTRB(10f, 20f, 12f, 22f)
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, dst = dst, transform = Matrix3x3F32.Identity),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        val geometry = result.command.preparedImage!!.geometry
        assertEquals(GPUPreparedImageGeometryClass.Rect, geometry.geometryClass)
        val positions = geometry.vertices.map { it.x to it.y }
        assertEquals(
            listOf(10f to 20f, 12f to 20f, 12f to 22f, 10f to 22f),
            positions,
        )
    }

    @Test
    fun `integer translation is folded into native image destination`() {
        val image = commonRgbaImage()
        val dst = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val tx = Matrix3x3F32.translation(5f, 10f)
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, dst = dst, transform = tx),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        val positions = result.command.preparedImage!!.geometry.vertices.map { it.x to it.y }
        assertEquals(
            listOf(5f to 10f, 7f to 10f, 7f to 12f, 5f to 12f),
            positions,
        )
        val normalized = assertIs<NormalizedDrawCommand.DrawImageRect>(result.command.normalized)
        assertEquals(GPUTransformType.Identity, normalized.transform.type)
        assertEquals(GPURect(5f, 10f, 7f, 12f), normalized.dst)
    }

    @Test
    fun `scale transform is refused before native image submission`() {
        val image = commonRgbaImage()
        val dst = RectF32.ofLTRB(1f, 2f, 4f, 6f)
        val scale = Matrix3x3F32.scaling(2f, 3f)
        val result = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, dst = dst, transform = scale),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        assertEquals("unsupported.image.affine_sampling", result.code)
    }

    @Test
    fun `rotation transform is refused before native image submission`() {
        val image = commonRgbaImage()
        val dst = RectF32.ofLTRB(0f, 0f, 10f, 10f)
        val rotate = Matrix3x3F32.rotation(90f)
        val result = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, dst = dst, transform = rotate),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        assertEquals("unsupported.image.affine_sampling", result.code)
    }

    @Test
    fun `reflection transform is refused before native image submission`() {
        val image = commonRgbaImage()
        val dst = RectF32.ofLTRB(1f, 2f, 5f, 8f)
        val reflectX = Matrix3x3F32.of(-1f, 0f, 0f, 0f, 1f, 0f)
        val result = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, dst = dst, transform = reflectX),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        assertEquals("unsupported.image.affine_sampling", result.code)
    }

    @Test
    fun `skew transform is refused before native image submission`() {
        val image = commonRgbaImage()
        val dst = RectF32.ofLTRB(10f, 10f, 30f, 30f)
        val skew = Matrix3x3F32.of(1f, 0.5f, 0f, 0f, 1f, 0f)
        val result = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, dst = dst, transform = skew),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        assertEquals("unsupported.image.affine_sampling", result.code)
    }

    @Test
    fun `composed affine transform is refused before native image submission`() {
        val image = commonRgbaImage()
        val dst = RectF32.ofLTRB(1f, 2f, 3f, 4f)
        val transform =
            Matrix3x3F32.translation(10f, 20f) * Matrix3x3F32.rotation(90f) * Matrix3x3F32.scaling(2f, 3f)
        val result = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, dst = dst, transform = transform),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )

        assertEquals("unsupported.image.affine_sampling", result.code)
    }

    @Test
    fun `perspective transform refused`() {
        val image = rgbaImage()
        val perspective = Matrix3x3F32.of(1f, 0f, 0f, 0f, 1f, 0f, 0.001f, 0f, 1f)
        val result = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, transform = perspective),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        assertEquals(GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING, result.code)
    }

    @Test
    fun `singular affine transform refused`() {
        val image = rgbaImage()
        val singular = Matrix3x3F32.of(0f, 0f, 0f, 0f, 0f, 0f)
        val result = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, transform = singular),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        assertEquals(GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING, result.code)
    }

    @Test
    fun `NEAREST sampling conserved`() {
        val image = rgbaImage()
        val paint = Paint.fill(ColorARGB.White).copy(
            shader = Shader.Image(image, sampling = SamplingOptions.NEAREST),
        )
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, paint = paint),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        assertEquals(GPUPreparedImageSampling.Nearest, result.command.preparedImage!!.sampling)
    }

    @Test
    fun `bounded W28 capability refuses linear before native nearest-only execution`() {
        val image = rgbaImage()
        val paint = Paint.fill(ColorARGB.White).copy(
            shader = Shader.Image(image, sampling = SamplingOptions.LINEAR),
        )
        val result = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, paint = paint),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                boundedW28Config,
                capabilities(),
            ),
        )
        assertEquals("unsupported.image.sampling_filter", result.code)
    }

    @Test
    fun `bounded W28 capability refuses scaled cropped and fractional image rectangles`() {
        val image = rgbaImage(width = 2, height = 1)
        val scaled = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, dst = RectF32.ofLTRB(10f, 10f, 11f, 11f)),
                GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target(),
                boundedW28Config, capabilities(),
            ),
        )
        val cropped = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(
                    image,
                    dst = RectF32.ofLTRB(10f, 10f, 11f, 11f),
                    src = RectF32.ofLTRB(0f, 0f, 1f, 1f),
                ),
                GPUDrawCommandID(1), 1, GPUFrameProvenance.None, target(),
                boundedW28Config, capabilities(),
            ),
        )
        val fractional = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, dst = RectF32.ofLTRB(10.5f, 10f, 12.5f, 11f)),
                GPUDrawCommandID(2), 2, GPUFrameProvenance.None, target(),
                boundedW28Config, capabilities(),
            ),
        )

        assertEquals(GPUPreparedImageRefusalCodes.RECT_GEOMETRY, scaled.code)
        assertEquals(GPUPreparedImageRefusalCodes.RECT_GEOMETRY, cropped.code)
        assertEquals(GPUPreparedImageRefusalCodes.RECT_GEOMETRY, fractional.code)
    }

    @Test
    fun `generic native capability retains established scaled image support`() {
        val image = rgbaImage(width = 2, height = 1)
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, dst = RectF32.ofLTRB(10f, 10f, 11f, 11f)),
                GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target(),
                RenderConfig.DEFAULT, capabilities(),
            ),
        )
        assertEquals(GPUPreparedImageSampling.Nearest, result.command.preparedImage!!.sampling)
    }

    @Test
    fun `image shader rect preserves nearest alpha tint in normalized and native facts`() {
        val image = commonA8Image()
        val paint = Paint(
            color = ColorARGB.fromRGBA(1f, 0f, 0f, 0.5f),
            shader = Shader.Image(image, sampling = SamplingOptions.NEAREST),
        )
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lowerImageShaderRect(
                operation = DisplayOp.DrawRect(
                    rect = RectF32(0f, 0f, 3f, 1f),
                    paint = paint,
                    transform = Matrix3x3F32.Identity,
                    clip = ClipStack.WideOpen,
                ),
                commandId = GPUDrawCommandID(0),
                paintOrder = 0,
                provenance = GPUFrameProvenance.None,
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
            ),
        )

        val material = assertIs<GPUMaterialDescriptor.ImageDraw>(result.command.normalized.material)
        val quantizedHalfAlpha = 128f / 255f
        assertEquals(quantizedHalfAlpha, material.tintA)
        assertEquals(quantizedHalfAlpha, result.command.preparedImage!!.tintPremultipliedRgba[3])
        assertEquals(GPUPreparedImageSampling.Nearest, result.command.preparedImage!!.sampling)
    }

    @Test
    fun `cubic sampling refused`() {
        val image = rgbaImage()
        val paint = Paint.fill(ColorARGB.White).copy(
            shader = Shader.Image(image, sampling = SamplingOptions.Cubic.Mitchell),
        )
        val result = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, paint = paint),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        assertEquals(GPUPreparedImageRefusalCodes.SAMPLING_CUBIC, result.code)
    }

    @Test
    fun `tint A8 uses paint color channels`() {
        val image = commonA8Image()
        val red = ColorARGB.of(255, 255, 0, 0)
        val paint = Paint.fill(red)
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, paint = paint),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        val tint = result.command.preparedImage!!.tintPremultipliedRgba
        assertEquals(1f, tint[0])
        assertEquals(0f, tint[1])
        assertEquals(0f, tint[2])
        assertEquals(1f, tint[3])
    }

    @Test
    fun `paint alpha applied to RGBA tint once`() {
        val image = rgbaImage()
        val semiTransparent = ColorARGB.of(128, 255, 255, 255)
        val paint = Paint.fill(semiTransparent)
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, paint = paint),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        val tint = result.command.preparedImage!!.tintPremultipliedRgba
        val expectedA = 128f / 255f
        assertEquals(expectedA, tint[3], 0.001f)
        assertEquals(expectedA, tint[0], 0.001f)
        assertEquals(expectedA, tint[1], 0.001f)
        assertEquals(expectedA, tint[2], 0.001f)
    }

    @Test
    fun `RGBA paint RGB is neutral while equal nontrivial alpha stays exact`() {
        val image = rgbaImage()
        val paints = listOf(
            Paint.fill(ColorARGB.of(192, 128, 64, 160)),
            Paint.fill(ColorARGB.of(192, 32, 224, 96)),
        )

        val tints = paints.mapIndexed { index, paint ->
            val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
                GPUPreparedDrawImageLowerer.lower(
                    drawImage(image, paint = paint),
                    GPUDrawCommandID(index),
                    index,
                    GPUFrameProvenance.None,
                    target(),
                    RenderConfig.DEFAULT,
                    capabilities(),
                ),
            )
            result.command.preparedImage!!.tintPremultipliedRgba
        }

        val alpha = 192f / 255f
        val expected = listOf(alpha, alpha, alpha, alpha)
        assertEquals(expected, tints[0])
        assertEquals(expected, tints[1])
    }

    @Test
    fun `integral device clip lowers to one exact prepared image scissor`() {
        val image = rgbaImage()
        val clip = ClipStack.DeviceRect(
            RectF32.ofLTRB(4f, 6f, 52f, 48f),
            antiAlias = false,
        )
        val operation = drawImage(image).copy(clip = clip)

        val ready = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                operation,
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )

        assertEquals(
            GPUClipCoveragePlan.Scissor(GPUBounds(4f, 6f, 52f, 48f)),
            ready.command.clipCoverage,
        )
        assertEquals(
            GPUClipExecutionPlan.ScissorOnly(GPUPixelBounds(4, 6, 52, 48)),
            ready.command.clipExecutionPlan,
        )
    }

    @Test
    fun `complex clip refuses the prepared image before recording`() {
        val image = rgbaImage()
        val clipped = ClipStack.Complex(
            listOf(
                org.graphiks.kanvas.canvas.ClipStackOp.RectOp(
                    RectF32.ofLTRB(0f, 0f, 100f, 100f),
                    org.graphiks.kanvas.pipeline.ClipOp.INTERSECT,
                    false,
                ),
            ),
        )
        val op = DisplayOp.DrawImage(
            image = image,
            src = RectF32.ofLTRB(0f, 0f, 4f, 3f),
            dst = RectF32.ofLTRB(10f, 10f, 14f, 13f),
            paint = null,
            transform = Matrix3x3F32.Identity,
            clip = clipped,
        )
        val refused = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                op,
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        assertEquals("unsupported.surface.prepared.image-clip", refused.code)
        assertEquals("unsupported_clip_plan", refused.facts["reason"])
    }

    @Test
    fun `blend mode unsupported by native image pipeline is refused before recording`() {
        val image = rgbaImage()
        val multiplyPaint = Paint.fill(ColorARGB.White).copy(blendMode = BlendMode.MULTIPLY)
        val result = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, paint = multiplyPaint),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, result.code)
        assertEquals(BlendMode.MULTIPLY.name, result.facts["blendMode"])
        assertEquals(BlendMode.SRC_OVER.name, result.facts["supportedBlendMode"])
    }

    @Test
    fun `mode blender overrides legacy blend mode before direct image validation`() {
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(
                    rgbaImage(),
                    paint = Paint.fill(ColorARGB.White).copy(
                        blendMode = BlendMode.MULTIPLY,
                        blender = Blender.Mode(BlendMode.SRC_OVER),
                    ),
                ),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )

        assertEquals(GPUBlendMode.SRC_OVER, result.command.normalized.blend.mode)
    }

    @Test
    fun `non SrcOver mode blender is an exact direct image refusal`() {
        val result = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(
                    rgbaImage(),
                    paint = Paint.fill(ColorARGB.White).copy(
                        blendMode = BlendMode.SRC_OVER,
                        blender = Blender.Mode(BlendMode.MULTIPLY),
                    ),
                ),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )

        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, result.code)
        assertEquals(
            mapOf(
                "sourceId" to "test-rgba",
                "blendMode" to BlendMode.MULTIPLY.name,
                "supportedBlendMode" to BlendMode.SRC_OVER.name,
            ),
            result.facts,
        )
    }

    @Test
    fun `arithmetic blender is an exact direct image refusal`() {
        val result = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(
                    rgbaImage(),
                    paint = Paint.fill(ColorARGB.White).copy(
                        blender = Blender.Arithmetic(0f, 1f, 1f, 0f),
                    ),
                ),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )

        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, result.code)
        assertEquals(
            mapOf(
                "reason" to "unsupported_blender",
                "blenderKind" to "Arithmetic",
            ),
            result.facts,
        )
    }

    @Test
    fun `paint effects without prepared image bindings refuse before recording`() {
        val image = rgbaImage()
        val paints = listOf(
            "colorFilter" to Paint(colorFilter = ColorFilter.HighContrast),
            "maskFilter" to Paint(
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, sigma = 1f),
            ),
            "imageFilter" to Paint(
                imageFilter = ImageFilter.Blur(1f, 1f),
            ),
        )

        paints.forEachIndexed { index, (paintField, paint) ->
            val refused = assertIs<GPUPreparedDrawImageLowering.Refused>(
                GPUPreparedDrawImageLowerer.lower(
                    drawImage(image, paint = paint),
                    GPUDrawCommandID(index),
                    index,
                    GPUFrameProvenance.None,
                    target(),
                    RenderConfig.DEFAULT,
                    capabilities(),
                ),
                paintField,
            )
            assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, refused.code, paintField)
            assertEquals("unsupported_paint_effect", refused.facts["reason"], paintField)
            assertEquals(paintField, refused.facts["paintField"], paintField)
        }
    }

    @Test
    fun `provenance conserved`() {
        val image = rgbaImage()
        val provenance = GPUFrameProvenance.GmContent
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image),
                GPUDrawCommandID(0),
                0,
                provenance,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        assertEquals(provenance, result.command.provenance)
        assertEquals(provenance, result.command.normalized.source.frameProvenance)
    }

    @Test
    fun `paint order conserved`() {
        val image = rgbaImage()
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image),
                GPUDrawCommandID(7),
                13,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        assertEquals(7, result.command.normalized.commandId.value)
        assertEquals(13, result.command.normalized.ordering.paintOrder)
    }

    @Test
    fun `no visual command returned after refusal`() {
        val image = rgbaImage()
        val perspective = Matrix3x3F32.of(1f, 0f, 0f, 0f, 1f, 0f, 0.001f, 0f, 1f)
        val result = GPUPreparedDrawImageLowerer.lower(
            drawImage(image, transform = perspective),
            GPUDrawCommandID(0),
            0,
            GPUFrameProvenance.None,
            target(),
            RenderConfig.DEFAULT,
            capabilities(),
        )
        assertIs<GPUPreparedDrawImageLowering.Refused>(result)
    }

    @Test
    fun `missing pixels refused`() {
        val image = Image(
            width = 4,
            height = 3,
            colorType = ColorType.RGBA_8888,
            sourceId = "no-pixels",
            pixels = null,
            alphaType = AlphaType.PREMUL,
        )
        val result = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        assertEquals(GPUPreparedImageRefusalCodes.PIXELS_MISSING, result.code)
    }

    @Test
    fun `skew cannot reach native image submission through a bounding-box substitute`() {
        val image = commonRgbaImage()
        val dst = RectF32.ofLTRB(10f, 10f, 30f, 20f)
        val skew = Matrix3x3F32.of(1f, 0.5f, 0f, 0.3f, 1f, 0f)
        val result = assertIs<GPUPreparedDrawImageLowering.Refused>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, dst = dst, transform = skew),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        assertEquals("unsupported.image.affine_sampling", result.code)
    }

    @Test
    fun `default sampling without paint selects the native nearest sampler`() {
        val image = rgbaImage()
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, paint = null),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        assertEquals(GPUPreparedImageSampling.Nearest, result.command.preparedImage!!.sampling)
    }

    @Test
    fun `blend plan identity populated`() {
        val image = rgbaImage()
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        assertTrue(result.command.blendPlan is GPUBlendPlan.FixedFunctionBlend)
    }

    @Test
    fun `geometry coverage is FullOrScissor`() {
        val image = rgbaImage()
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        assertEquals(GPUCoverageConsumption.FullOrScissor, result.command.geometryCoverage)
    }
}
