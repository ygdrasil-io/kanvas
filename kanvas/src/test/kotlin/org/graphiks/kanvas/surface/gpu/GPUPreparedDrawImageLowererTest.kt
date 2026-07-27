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
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometryClass
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

class GPUPreparedDrawImageLowererTest {

    private fun target() = GPUTargetFacts(64, 64, "rgba8unorm-srgb")

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity(
            facadeName = "test",
            implementationName = "fake",
            adapterName = "mock",
            deviceName = "mock-device",
        ),
        facts = emptyList(),
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
        dst: Rect = Rect.fromLTRB(10f, 10f, 50f, 40f),
        src: Rect = Rect.fromLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat()),
        paint: Paint? = null,
        transform: Matrix33 = Matrix33.identity(),
    ): DisplayOp.DrawImage = DisplayOp.DrawImage(
        image = image,
        src = src,
        dst = dst,
        paint = paint,
        transform = transform,
        clip = ClipStack.WideOpen,
    )

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
                RenderConfig.DEFAULT,
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
                drawImage(image, Rect.fromLTRB(20f, 20f, 60f, 50f)),
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
    fun `source rect and UV clamp exact`() {
        val image = rgbaImage(width = 8, height = 6)
        val src = Rect.fromLTRB(1f, 2f, 5f, 4f)
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, src = src),
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
            ),
        )
        val geometry = result.command.preparedImage!!.geometry
        val uvs = geometry.vertices.map { it.u to it.v }
        val imageW = image.width.toFloat()
        val imageH = image.height.toFloat()
        val expectedUvs = listOf(
            1f / imageW to 2f / imageH,
            5f / imageW to 2f / imageH,
            5f / imageW to 4f / imageH,
            1f / imageW to 4f / imageH,
        )
        assertEquals(expectedUvs, uvs)
    }

    @Test
    fun `identity transform produces rect geometry with exact positions`() {
        val image = rgbaImage(width = 2, height = 2)
        val dst = Rect.fromLTRB(10f, 20f, 30f, 40f)
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
            GPUPreparedDrawImageLowerer.lower(
                drawImage(image, dst = dst, transform = Matrix33.identity()),
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
            listOf(10f to 20f, 30f to 20f, 30f to 40f, 10f to 40f),
            positions,
        )
    }

    @Test
    fun `translation transform produces rect geometry with shifted positions`() {
        val image = rgbaImage(width = 2, height = 2)
        val dst = Rect.fromLTRB(0f, 0f, 20f, 20f)
        val tx = Matrix33.translate(5f, 10f)
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
            listOf(5f to 10f, 25f to 10f, 25f to 30f, 5f to 30f),
            positions,
        )
    }

    @Test
    fun `scale transform produces rect geometry with scaled positions`() {
        val image = rgbaImage(width = 2, height = 2)
        val dst = Rect.fromLTRB(1f, 2f, 4f, 6f)
        val scale = Matrix33.scale(2f, 3f)
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
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
        val positions = result.command.preparedImage!!.geometry.vertices.map { it.x to it.y }
        assertEquals(
            listOf(2f to 6f, 8f to 6f, 8f to 18f, 2f to 18f),
            positions,
        )
    }

    @Test
    fun `rotation transform preserves four transformed corners`() {
        val image = rgbaImage(width = 2, height = 2)
        val dst = Rect.fromLTRB(0f, 0f, 10f, 10f)
        val rotate = Matrix33.rotate(90f)
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
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
        val geometry = result.command.preparedImage!!.geometry
        assertEquals(4, geometry.vertices.size)
    }

    @Test
    fun `reflection transform preserves four corners`() {
        val image = rgbaImage(width = 2, height = 2)
        val dst = Rect.fromLTRB(1f, 2f, 5f, 8f)
        val reflectX = Matrix33.makeAll(-1f, 0f, 0f, 0f, 1f, 0f)
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
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
        val geometry = result.command.preparedImage!!.geometry
        assertEquals(4, geometry.vertices.size)
    }

    @Test
    fun `skew transform produces quad geometry with four corners preserved`() {
        val image = rgbaImage(width = 2, height = 2)
        val dst = Rect.fromLTRB(10f, 10f, 30f, 30f)
        val skew = Matrix33.makeAll(1f, 0.5f, 0f, 0f, 1f, 0f)
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
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
        val geometry = result.command.preparedImage!!.geometry
        assertEquals(GPUPreparedImageGeometryClass.Quad, geometry.geometryClass)
        assertEquals(4, geometry.vertices.size)
    }

    @Test
    fun `perspective transform refused`() {
        val image = rgbaImage()
        val perspective = Matrix33.makeAll(1f, 0f, 0f, 0f, 1f, 0f, 0.001f, 0f, 1f)
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
        val singular = Matrix33.makeAll(0f, 0f, 0f, 0f, 0f, 0f)
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
        assertEquals("unsupported.transform.affine_singular", result.code)
    }

    @Test
    fun `NEAREST sampling conserved`() {
        val image = rgbaImage()
        val paint = Paint.fill(Color.WHITE).copy(
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
    fun `LINEAR sampling conserved`() {
        val image = rgbaImage()
        val paint = Paint.fill(Color.WHITE).copy(
            shader = Shader.Image(image, sampling = SamplingOptions.LINEAR),
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
        assertEquals(GPUPreparedImageSampling.Linear, result.command.preparedImage!!.sampling)
    }

    @Test
    fun `cubic sampling refused`() {
        val image = rgbaImage()
        val paint = Paint.fill(Color.WHITE).copy(
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
        val image = a8Image()
        val red = Color.fromArgb(255, 255, 0, 0)
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
        val semiTransparent = Color.fromArgb(128, 255, 255, 255)
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
    fun `clip preserved for non-wide-open clip`() {
        val image = rgbaImage()
        val rectPath = org.graphiks.kanvas.geometry.Path().apply {
            moveTo(0f, 0f)
            lineTo(100f, 0f)
            lineTo(100f, 100f)
            close()
        }
        val clipped = ClipStack.Complex(
            listOf(
                org.graphiks.kanvas.canvas.ClipStackOp.RectOp(
                    Rect.fromLTRB(0f, 0f, 100f, 100f),
                    org.graphiks.kanvas.pipeline.ClipOp.INTERSECT,
                    false,
                ),
            ),
        )
        val op = DisplayOp.DrawImage(
            image = image,
            src = Rect.fromLTRB(0f, 0f, 4f, 3f),
            dst = Rect.fromLTRB(10f, 10f, 50f, 40f),
            paint = null,
            transform = Matrix33.identity(),
            clip = clipped,
        )
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
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
        assertNotNull(result.command.clipCoverage)
    }

    @Test
    fun `blend mode preserved`() {
        val image = rgbaImage()
        val multiplyPaint = Paint.fill(Color.WHITE).copy(blendMode = BlendMode.MULTIPLY)
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
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
        val normalized = assertIs<NormalizedDrawCommand.DrawImageRect>(result.command.normalized)
        assertEquals(BlendMode.MULTIPLY.toGpuBlendFacts(), normalized.blend)
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
        val perspective = Matrix33.makeAll(1f, 0f, 0f, 0f, 1f, 0f, 0.001f, 0f, 1f)
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
    fun `cannot substitute with bounding box for skew`() {
        val image = rgbaImage(width = 2, height = 2)
        val dst = Rect.fromLTRB(10f, 10f, 30f, 20f)
        val skew = Matrix33.makeAll(1f, 0.5f, 0f, 0.3f, 1f, 0f)
        val result = assertIs<GPUPreparedDrawImageLowering.Ready>(
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
        val geometry = result.command.preparedImage!!.geometry
        assertEquals(GPUPreparedImageGeometryClass.Quad, geometry.geometryClass)

        val positions = geometry.vertices.map { it.x to it.y }
        val srcBbox = Rect.fromLTRB(10f, 10f, 30f, 20f)
        val expectedCorner1 = skew * Point(srcBbox.left, srcBbox.top)
        val expectedCorner2 = skew * Point(srcBbox.right, srcBbox.top)
        val expectedCorner3 = skew * Point(srcBbox.right, srcBbox.bottom)
        val expectedCorner4 = skew * Point(srcBbox.left, srcBbox.bottom)
        assertEquals(expectedCorner1.x, positions[0].first, 0.001f)
        assertEquals(expectedCorner1.y, positions[0].second, 0.001f)
        assertEquals(expectedCorner2.x, positions[1].first, 0.001f)
        assertEquals(expectedCorner2.y, positions[1].second, 0.001f)
        assertEquals(expectedCorner3.x, positions[2].first, 0.001f)
        assertEquals(expectedCorner3.y, positions[2].second, 0.001f)
        assertEquals(expectedCorner4.x, positions[3].first, 0.001f)
        assertEquals(expectedCorner4.y, positions[3].second, 0.001f)
    }

    @Test
    fun `default sampling without paint produces LINEAR`() {
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
        assertEquals(GPUPreparedImageSampling.Linear, result.command.preparedImage!!.sampling)
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
