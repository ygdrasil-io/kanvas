package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUImageFilterPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTexture
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRectDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSimplePassBatchKind
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilMode
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilFillRule
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilCoverConfig
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendTriangleData
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendUniformPayloadDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendVertexColorData
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendVertexPositionUVData
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.types.b
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.r
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GPUAlphaImageMaterialTest {
    private val halfAlpha = 0x80 / 255f
    private val alphaImage = Image.fromPixels(
        width = 2,
        height = 1,
        pixels = byteArrayOf(0x00, 0x80.toByte()),
        colorType = ColorType.ALPHA_8,
        sourceId = "alpha-mask",
    )
    private val rgbaImage = Image.fromPixels(
        width = 1,
        height = 1,
        pixels = byteArrayOf(0x20, 0x40, 0x60, 0xFF.toByte()),
        colorType = ColorType.RGBA_8888,
        sourceId = "rgba-tile",
    )

    @Test
    fun `alpha image shader uploads white mask pixels and carries paint tint`() {
        val paint = Paint(
            color = Color.fromRGBA(0f, 1f, 0f, 0.5f),
            shader = Shader.Image(alphaImage),
        )

        val material = paint.toMaterial() as GPUMaterialDescriptor.ImageDraw

        assertEquals(true, material.alphaOnly)
        assertEquals(0f, material.tintR, 0.001f)
        assertEquals(1f, material.tintG, 0.001f)
        assertEquals(0f, material.tintB, 0.001f)
        assertEquals(halfAlpha, material.tintA, 0.001f)
        assertArrayEquals(
            byteArrayOf(
                0x00, 0x00, 0x00, 0x00,
                0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(),
            ),
            material.rgbaPixels,
        )
    }

    @Test
    fun `draw image command carries alpha image tint from paint`() {
        val paint = Paint(color = Color.fromRGBA(0f, 1f, 0f, 0.5f))
        val op = DisplayOp.DrawImage(
            image = alphaImage,
            src = Rect(0f, 0f, 2f, 1f),
            dst = Rect(0f, 0f, 2f, 1f),
            paint = paint,
            transform = Matrix33.identity(),
            clip = ClipStack.WideOpen,
        )

        val command = op.toImageRectCommand(
            GPUDrawCommandID(7),
            GPUTargetFacts(width = 16, height = 16, colorFormat = "bgra8unorm"),
        )
        val material = command.material as GPUMaterialDescriptor.ImageDraw

        assertEquals(true, material.alphaOnly)
        assertEquals(0f, material.tintR, 0.001f)
        assertEquals(1f, material.tintG, 0.001f)
        assertEquals(0f, material.tintB, 0.001f)
        assertEquals(halfAlpha, material.tintA, 0.001f)
    }

    @Test
    fun `draw image command uses black tint for alpha image without paint`() {
        val op = DisplayOp.DrawImage(
            image = alphaImage,
            src = Rect(0f, 0f, 2f, 1f),
            dst = Rect(0f, 0f, 2f, 1f),
            paint = null,
            transform = Matrix33.identity(),
            clip = ClipStack.WideOpen,
        )

        val command = op.toImageRectCommand(
            GPUDrawCommandID(9),
            GPUTargetFacts(width = 16, height = 16, colorFormat = "bgra8unorm"),
        )
        val material = command.material as GPUMaterialDescriptor.ImageDraw

        assertEquals(true, material.alphaOnly)
        assertEquals(0f, material.tintR, 0.001f)
        assertEquals(0f, material.tintG, 0.001f)
        assertEquals(0f, material.tintB, 0.001f)
        assertEquals(1f, material.tintA, 0.001f)
    }

    @Test
    fun `draw image command carries paint alpha for rgba image without rgb tint`() {
        val paint = Paint(color = Color.fromRGBA(0.2f, 0.4f, 0.6f, 0.4f))
        val op = DisplayOp.DrawImage(
            image = rgbaImage,
            src = Rect(0f, 0f, 1f, 1f),
            dst = Rect(0f, 0f, 4f, 4f),
            paint = paint,
            transform = Matrix33.identity(),
            clip = ClipStack.WideOpen,
        )

        val command = op.toImageRectCommand(
            GPUDrawCommandID(12),
            GPUTargetFacts(width = 16, height = 16, colorFormat = "bgra8unorm"),
        )
        val material = command.material as GPUMaterialDescriptor.ImageDraw

        assertEquals(false, material.alphaOnly)
        assertEquals(1f, material.tintR, 0.001f)
        assertEquals(1f, material.tintG, 0.001f)
        assertEquals(1f, material.tintB, 0.001f)
        assertEquals(paint.color.a, material.tintA, 0.001f)
    }

    @Test
    fun `image material with pixels is accepted for fill dispatch`() {
        val material = Paint(
            color = Color.WHITE,
            shader = Shader.Image(alphaImage),
        ).toMaterial()
        val op = DisplayOp.DrawRect(
            rect = Rect(0f, 0f, 2f, 1f),
            paint = Paint(shader = Shader.Image(alphaImage)),
            transform = Matrix33.identity(),
            clip = ClipStack.WideOpen,
        )
        val command = op.toNormalizedCommand(
            GPUDrawCommandID(8),
            GPUTargetFacts(width = 16, height = 16, colorFormat = "bgra8unorm"),
        )

        assertEquals(GPUMaterialKind.ImageDraw, material.kind)
        assertEquals(null, command.fillGuardRefusalReasonOrNull())
    }

    @Test
    fun `draw image command dispatches alpha texture and tint uniforms`() {
        val paint = Paint(
            color = Color.fromRGBA(0.25f, 0.5f, 0.75f, 0.8f),
            blendMode = BlendMode.SRC_OVER,
        )
        val op = DisplayOp.DrawImage(
            image = alphaImage,
            src = Rect(1f, 0f, 2f, 1f),
            dst = Rect(4f, 5f, 8f, 7f),
            paint = paint,
            transform = Matrix33.identity(),
            clip = ClipStack.DeviceRect(Rect(5f, 5f, 7f, 7f)),
        )
        val command = op.toImageRectCommand(
            GPUDrawCommandID(10),
            GPUTargetFacts(width = 16, height = 16, colorFormat = "bgra8unorm"),
        )
        val recorder = CapturingRenderRecorder()
        val diagnostics = Diagnostics()

        recorder.dispatchImageRect(
            cmd = command,
            textureCache = mapOf("alpha-mask" to expandedAlphaPixels),
            dispatched = mutableListOf(),
            diagnostics = diagnostics,
            surfaceWidth = 16,
            surfaceHeight = 16,
            config = RenderConfig.DEFAULT,
        )

        assertFalse(diagnostics.hasFatal)
        val pass = recorder.singleTexturePass()
        assertArrayEquals(expandedAlphaPixels, pass.textureRgba)
        assertEquals(2, pass.textureWidth)
        assertEquals(1, pass.textureHeight)
        assertEquals("rgba8unorm", pass.textureFormat)
        assertEquals(GPUBlendMode.SRC_OVER, pass.blendMode)
        assertNull(pass.stencilMode)

        val draw = pass.draws.single()
        assertEquals(5, draw.scissorX)
        assertEquals(5, draw.scissorY)
        assertEquals(2, draw.scissorWidth)
        assertEquals(2, draw.scissorHeight)
        assertFloatUniforms(
            draw.uniformBytes,
            4f, 5f, 8f, 7f,
            0.5f, 1f,
            0.5f, 0f,
            paint.color.r, paint.color.g, paint.color.b, paint.color.a,
        )
    }

    @Test
    fun `draw image dispatch refuses an explicit image filter refusal before texture dispatch`() {
        val recorder = CapturingRenderRecorder()
        val diagnostics = Diagnostics()
        val dispatched = mutableListOf<String>()

        recorder.dispatchImageRect(
            cmd = alphaImageCommand().copy(
                imageFilterPlan = GPUImageFilterPlan.Refused("expected-refusal"),
            ),
            textureCache = mapOf("alpha-mask" to expandedAlphaPixels),
            dispatched = dispatched,
            diagnostics = diagnostics,
            surfaceWidth = 16,
            surfaceHeight = 16,
            config = RenderConfig.DEFAULT,
        )

        assertEquals(1, diagnostics.fatalCount)
        assertEquals("expected-refusal", diagnostics.entries.single().reason)
        assertEquals(0, recorder.texturePassCount)
        assertEquals(emptyList<String>(), dispatched)
    }

    @Test
    fun `draw image dispatch refuses blur plans before texture dispatch`() {
        val recorder = CapturingRenderRecorder()
        val diagnostics = Diagnostics()
        val dispatched = mutableListOf<String>()

        recorder.dispatchImageRect(
            cmd = alphaImageCommand().copy(
                imageFilterPlan = GPUImageFilterPlan.Blur(
                    sigmaX = 1f,
                    sigmaY = 1f,
                    haloX = 3,
                    haloY = 3,
                    outputBounds = GPURect(0f, 0f, 2f, 1f),
                ),
            ),
            textureCache = mapOf("alpha-mask" to expandedAlphaPixels),
            dispatched = dispatched,
            diagnostics = diagnostics,
            surfaceWidth = 16,
            surfaceHeight = 16,
            config = RenderConfig.DEFAULT,
        )

        assertEquals(1, diagnostics.fatalCount)
        assertEquals("unsupported.image-filter.blur.route-bypass", diagnostics.entries.single().reason)
        assertEquals(0, recorder.texturePassCount)
        assertEquals(emptyList<String>(), dispatched)
    }

    @Test
    fun `alpha image shader fill path dispatches texture with stencil test`() {
        val paint = Paint(
            color = Color.fromRGBA(0.2f, 0.4f, 0.6f, 0.7f),
            shader = Shader.Image(alphaImage),
        )
        val path = Path().apply {
            addRect(Rect(2f, 3f, 6f, 5f))
            fillType = FillType.INVERSE_EVEN_ODD
        }
        val op = DisplayOp.DrawPath(
            path = path,
            paint = paint,
            transform = Matrix33.identity(),
            clip = ClipStack.WideOpen,
        )
        val vertices = listOf(2f, 3f, 6f, 3f, 6f, 5f, 2f, 5f)
        val command = op.toNormalizedCommand(
            GPUDrawCommandID(11),
            GPUTargetFacts(width = 16, height = 16, colorFormat = "bgra8unorm"),
            tessellatedVertices = vertices,
            contourStarts = listOf(0),
            edgeCount = 4,
        )
        val recorder = CapturingRenderRecorder()
        val diagnostics = Diagnostics()

        recorder.dispatchFillPath(
            cmd = command,
            dispatched = mutableListOf(),
            diagnostics = diagnostics,
            surfaceWidth = 16,
            surfaceHeight = 16,
            config = RenderConfig.DEFAULT,
        )

        assertFalse(diagnostics.hasFatal)
        val pass = recorder.singleTexturePass()
        assertArrayEquals(expandedAlphaPixels, pass.textureRgba)
        assertEquals(2, pass.textureWidth)
        assertEquals(1, pass.textureHeight)
        assertEquals("rgba8unorm", pass.textureFormat)
        assertEquals(GPUBackendStencilMode.Test, pass.stencilMode)
        assertEquals(
            GPUBackendStencilCoverConfig(GPUBackendStencilFillRule.EvenOdd, inverse = true),
            pass.stencilConfig,
        )

        val draw = pass.draws.single()
        assertEquals(2, draw.scissorX)
        assertEquals(3, draw.scissorY)
        assertEquals(4, draw.scissorWidth)
        assertEquals(2, draw.scissorHeight)
        assertFloatUniforms(
            draw.uniformBytes,
            2f, 3f, 6f, 5f,
            2f, 2f,
            0f, 0f,
            paint.color.r, paint.color.g, paint.color.b, paint.color.a,
        )
    }

    @Test
    fun `path fill preserves contours and inverse even odd stencil configuration`() {
        val path = Path().apply {
            addRect(Rect(2f, 2f, 14f, 14f))
            addRect(Rect(5f, 5f, 11f, 11f))
            fillType = FillType.INVERSE_EVEN_ODD
        }
        val command = DisplayOp.DrawPath(
            path = path,
            paint = Paint.fill(Color.RED),
            transform = Matrix33.identity(),
            clip = ClipStack.WideOpen,
        ).toNormalizedCommand(
            GPUDrawCommandID(15),
            GPUTargetFacts(width = 16, height = 16, colorFormat = "bgra8unorm"),
            tessellatedVertices = listOf(
                2f, 2f, 14f, 2f, 14f, 14f, 2f, 14f,
                5f, 5f, 11f, 5f, 11f, 11f, 5f, 11f,
            ),
            contourStarts = listOf(0, 4),
            edgeCount = 8,
        )
        val recorder = CapturingRenderRecorder()
        val diagnostics = Diagnostics()

        recorder.dispatchFillPath(
            cmd = command,
            dispatched = mutableListOf(),
            diagnostics = diagnostics,
            surfaceWidth = 16,
            surfaceHeight = 16,
            config = RenderConfig.DEFAULT,
        )

        assertFalse(diagnostics.hasFatal)
        assertEquals("EvenOdd", command.pathDescriptor.fillRule)
        assertTrue(command.pathDescriptor.inverseFill)
        val write = recorder.stencilPasses.single { it.stencilMode == GPUBackendStencilMode.Write }
        val triangleData = requireNotNull(write.triangleData)
        assertEquals(8, triangleData.triangleCount)
        assertEquals(48, triangleData.vertices.size)
        assertEquals((0 until 24).toList(), triangleData.indices.toList())
        assertEquals(GPUBackendStencilFillRule.EvenOdd, write.stencilConfig.fillRule)
        assertTrue(write.stencilConfig.inverse)
        val cover = recorder.stencilPasses.single { it.stencilMode == GPUBackendStencilMode.Test }
        assertEquals(write.stencilConfig, cover.stencilConfig)
    }

    @Test
    fun `path fill rejects non finite vertices before stencil fan creation`() {
        val command = DisplayOp.DrawPath(
            path = Path().addRect(Rect(2f, 2f, 6f, 5f)),
            paint = Paint.fill(Color.RED),
            transform = Matrix33.identity(),
            clip = ClipStack.WideOpen,
        ).toNormalizedCommand(
            GPUDrawCommandID(16),
            GPUTargetFacts(width = 16, height = 16, colorFormat = "bgra8unorm"),
            tessellatedVertices = listOf(2f, 2f, Float.NaN, 2f, 6f, 5f, 2f, 5f),
            contourStarts = listOf(0),
            edgeCount = 4,
        )
        val recorder = CapturingRenderRecorder()
        val diagnostics = Diagnostics()

        recorder.dispatchFillPath(
            cmd = command,
            dispatched = mutableListOf(),
            diagnostics = diagnostics,
            surfaceWidth = 16,
            surfaceHeight = 16,
            config = RenderConfig.DEFAULT,
        )

        assertEquals(1, diagnostics.fatalCount)
        assertEquals("non_finite_vertices", diagnostics.entries.single().reason)
        assertEquals(emptyList<StencilPass>(), recorder.stencilPasses)
    }

    @Test
    fun `image shader larger than recorder texture limit is refused before texture upload`() {
        val image = Image.fromPixels(
            width = 1,
            height = 8_193,
            pixels = ByteArray(8_193),
            colorType = ColorType.ALPHA_8,
            sourceId = "too-tall-alpha-mask",
        )
        val op = DisplayOp.DrawPath(
            path = Path().addRect(Rect(2f, 3f, 6f, 5f)),
            paint = Paint(shader = Shader.Image(image)),
            transform = Matrix33.identity(),
            clip = ClipStack.WideOpen,
        )
        val command = op.toNormalizedCommand(
            GPUDrawCommandID(13),
            GPUTargetFacts(width = 16, height = 16, colorFormat = "bgra8unorm"),
            tessellatedVertices = listOf(2f, 3f, 6f, 3f, 6f, 5f, 2f, 5f),
            contourStarts = listOf(0),
            edgeCount = 4,
        )
        val recorder = CapturingRenderRecorder(maxTextureDimension2D = 8_192)
        val diagnostics = Diagnostics()
        val dispatched = mutableListOf<String>()

        recorder.dispatchFillPath(
            cmd = command,
            dispatched = dispatched,
            diagnostics = diagnostics,
            surfaceWidth = 16,
            surfaceHeight = 16,
            config = RenderConfig.DEFAULT,
        )

        assertEquals(1, diagnostics.fatalCount)
        assertEquals(
            "texture_dimensions_exceed_max_texture_dimension:1x8193>8192",
            diagnostics.entries.single().reason,
        )
        assertEquals(0, recorder.texturePassCount)
        assertEquals(emptyList<String>(), dispatched)
    }

    private val expandedAlphaPixels = byteArrayOf(
        0x00, 0x00, 0x00, 0x00,
        0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(),
    )

    private fun alphaImageCommand() = DisplayOp.DrawImage(
        image = alphaImage,
        src = Rect(0f, 0f, 2f, 1f),
        dst = Rect(0f, 0f, 2f, 1f),
        paint = Paint(),
        transform = Matrix33.identity(),
        clip = ClipStack.WideOpen,
    ).toImageRectCommand(
        GPUDrawCommandID(14),
        GPUTargetFacts(width = 16, height = 16, colorFormat = "bgra8unorm"),
    )

    private fun assertFloatUniforms(bytes: ByteArray, vararg expected: Float) {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(expected.size * 4, bytes.size)
        expected.forEachIndexed { index, value ->
            assertEquals(value, buffer.getFloat(index * 4), 0.001f, "uniform[$index]")
        }
    }

    private class CapturingRenderRecorder(
        override val maxTextureDimension2D: Int = Int.MAX_VALUE,
    ) : GPUBackendRenderRecorder {
        private val texturePasses = mutableListOf<TexturePass>()
        val stencilPasses = mutableListOf<StencilPass>()

        val texturePassCount: Int get() = texturePasses.size

        fun singleTexturePass(): TexturePass {
            assertEquals(1, texturePasses.size)
            return texturePasses.single()
        }

        override fun drawFullscreenTextureUniformPass(
            wgsl: String,
            colorFormat: String,
            textureRgba: ByteArray,
            textureWidth: Int,
            textureHeight: Int,
            textureFormat: String,
            draws: List<GPUBackendRawUniformDraw>,
            blendMode: GPUBlendMode?,
            stencilMode: GPUBackendStencilMode?,
            stencilConfig: GPUBackendStencilCoverConfig,
        ) {
            texturePasses.add(
                TexturePass(
                    textureRgba = textureRgba,
                    textureWidth = textureWidth,
                    textureHeight = textureHeight,
                    textureFormat = textureFormat,
                    draws = draws,
                    blendMode = blendMode,
                    stencilMode = stencilMode,
                    stencilConfig = stencilConfig,
                ),
            )
        }

        override fun drawFullscreenPass(
            wgsl: String,
            colorFormat: String,
            draws: List<GPUBackendRectDraw>,
            blendMode: GPUBlendMode?,
            passBatchKind: GPUBackendSimplePassBatchKind?,
        ) = unsupported()

        override fun drawFullscreenUniformPayloadPass(
            wgsl: String,
            colorFormat: String,
            draws: List<GPUBackendUniformPayloadDraw>,
            blendMode: GPUBlendMode?,
            sourceLabel: String,
            passBatchKind: GPUBackendSimplePassBatchKind?,
        ) = unsupported()

        override fun drawFullscreenRawUniformPass(
            wgsl: String,
            colorFormat: String,
            draws: List<GPUBackendRawUniformDraw>,
            blendMode: GPUBlendMode?,
            passBatchKind: GPUBackendSimplePassBatchKind?,
        ) = unsupported()

        override fun drawFullscreenStencilPass(
            wgsl: String,
            colorFormat: String,
            stencilMode: GPUBackendStencilMode,
            triangleData: GPUBackendTriangleData?,
            draws: List<GPUBackendRawUniformDraw>,
            blendMode: GPUBlendMode?,
            stencilConfig: org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilCoverConfig,
        ) {
            stencilPasses += StencilPass(stencilMode, triangleData, stencilConfig)
        }

        override fun createVertexColorBuffer(data: GPUBackendVertexColorData): String = unsupported()

        override fun drawVertexColorIndexed(
            vertexBufferLabel: String,
            indexCount: Int,
            uniformDraw: GPUBackendRawUniformDraw,
            blendMode: GPUBlendMode?,
        ) = unsupported()

        override fun createVertexPositionUVBuffer(data: GPUBackendVertexPositionUVData): String = unsupported()

        override fun drawVertexPositionUVIndexed(
            vertexBufferLabel: String,
            indexCount: Int,
            uniformDraw: GPUBackendRawUniformDraw,
            textureRgba: ByteArray,
            textureWidth: Int,
            textureHeight: Int,
            textureFormat: String,
            blendMode: GPUBlendMode?,
        ) = unsupported()

        override fun drawVertexPositionDualUVIndexed(
            vertexBufferLabel: String,
            indexCount: Int,
            uniformDraw: GPUBackendRawUniformDraw,
            texture1Rgba: ByteArray,
            texture1Width: Int,
            texture1Height: Int,
            texture2Rgba: ByteArray,
            texture2Width: Int,
            texture2Height: Int,
            textureFormat: String,
            blendMode: GPUBlendMode?,
        ) = unsupported()

        override fun createOffscreenTexture(texture: GPUBackendOffscreenTexture): String = unsupported()

        override fun encodeOffscreenTexture(
            textureLabel: String,
            clearColor: GPUClearColor?,
            block: GPUBackendRenderRecorder.() -> Unit,
        ) = unsupported()

        override fun drawCompositePass(
            wgsl: String,
            colorFormat: String,
            textureLabel: String,
            draws: List<GPUBackendRawUniformDraw>,
            blendMode: GPUBlendMode?,
        ) = unsupported()

        override fun drawTwoTexturePass(
            wgsl: String,
            colorFormat: String,
            firstTextureLabel: String,
            secondTextureLabel: String,
            draws: List<GPUBackendRawUniformDraw>,
            blendMode: GPUBlendMode?,
        ) = unsupported()

        override fun drawThreeTexturePass(
            wgsl: String,
            colorFormat: String,
            firstTextureLabel: String,
            secondTextureLabel: String,
            thirdTextureLabel: String,
            draws: List<GPUBackendRawUniformDraw>,
            blendMode: GPUBlendMode?,
        ) = unsupported()

        override fun drawBlendPass(
            wgsl: String,
            colorFormat: String,
            srcTextureLabel: String,
            dstTextureLabel: String,
            draws: List<GPUBackendRawUniformDraw>,
        ) = unsupported()

        override fun drawTextAtlasPass(
            atlasRgba: ByteArray,
            atlasWidth: Int,
            atlasHeight: Int,
            atlasFormat: String,
            vertexData: FloatArray,
            indexData: IntArray,
            draws: List<GPUBackendRawUniformDraw>,
            blendMode: GPUBlendMode?,
        ) = unsupported()

        override fun drawColorGlyphPass(
            atlasRgba: ByteArray,
            atlasWidth: Int,
            atlasHeight: Int,
            atlasFormat: String,
            vertexData: FloatArray,
            indexData: IntArray,
            draws: List<GPUBackendRawUniformDraw>,
            blendMode: GPUBlendMode?,
        ) = unsupported()

        private fun unsupported(): Nothing = error("Unexpected recorder call")
    }

    private data class TexturePass(
        val textureRgba: ByteArray,
        val textureWidth: Int,
        val textureHeight: Int,
        val textureFormat: String,
        val draws: List<GPUBackendRawUniformDraw>,
        val blendMode: GPUBlendMode?,
        val stencilMode: GPUBackendStencilMode?,
        val stencilConfig: GPUBackendStencilCoverConfig,
    )

    private data class StencilPass(
        val stencilMode: GPUBackendStencilMode,
        val triangleData: GPUBackendTriangleData?,
        val stencilConfig: org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilCoverConfig,
    )
}
