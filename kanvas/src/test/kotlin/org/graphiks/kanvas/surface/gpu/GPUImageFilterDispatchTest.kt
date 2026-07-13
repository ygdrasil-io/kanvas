package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUImageFilterPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTexture
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendCoverageMask
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendCoverageMaskRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRectDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSimplePassBatchKind
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilMode
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendTriangleData
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendUniformPayloadDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendVertexColorData
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendVertexPositionUVData
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.execution.GPUSurfaceTarget
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GPUImageFilterDispatchTest {
    @Test
    fun `complex clip blur stays renderable as an unclipped source`() {
        val command = DisplayOp.DrawImage(
            image = Image.fromPixels(4, 4, fixtureRgba, ColorType.RGBA_8888, "fixture"),
            src = Rect(0f, 0f, 4f, 4f),
            dst = Rect(20f, 30f, 24f, 34f),
            paint = Paint(imageFilter = ImageFilter.Blur(2f, 3f)),
            transform = Matrix33.identity(),
            clip = ClipStack.Complex(
                listOf(
                    ClipStackOp.RectOp(Rect(8f, 8f, 40f, 48f), ClipOp.INTERSECT),
                    ClipStackOp.RectOp(Rect(16f, 16f, 24f, 24f), ClipOp.DIFFERENCE),
                ),
            ),
        ).toImageRectCommand(
            GPUDrawCommandID(1),
            GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
        )

        assertTrue(command.imageFilterPlan is GPUImageFilterPlan.Blur, command.imageFilterPlan.toString())
    }

    @Test
    fun `clip source image blur restores the full target halo before the mask composite`() {
        val command = DisplayOp.DrawImage(
            image = Image.fromPixels(4, 4, fixtureRgba, ColorType.RGBA_8888, "fixture"),
            src = Rect(0f, 0f, 4f, 4f),
            dst = Rect(20f, 30f, 24f, 34f),
            paint = Paint(imageFilter = ImageFilter.Blur(2f, 3f)),
            transform = Matrix33.identity(),
            clip = ClipStack.DeviceRect(Rect(21f, 31f, 23f, 33f), antiAlias = true),
        ).toImageRectCommand(
            GPUDrawCommandID(1),
            GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
        )

        val source = command.copyForClipSource(targetWidth = 64, targetHeight = 64)
        val blur = source.imageFilterPlan as GPUImageFilterPlan.Blur

        assertEquals(14f, blur.outputBounds.left)
        assertEquals(21f, blur.outputBounds.top)
        assertEquals(30f, blur.outputBounds.right)
        assertEquals(43f, blur.outputBounds.bottom)
        assertEquals(0f, source.clip.bounds.left)
        assertEquals(64f, source.clip.bounds.right)
        assertTrue(!source.blend.needsDestinationTexture())
    }

    @Test
    fun `accepted blur records source horizontal vertical and scene composite passes`() {
        val target = CapturingOffscreenTarget()

        val result = target.renderImageCommand(
            sceneTextureLabel = "scene",
            command = blurCommand(sigmaX = 2f, sigmaY = 3f),
            textureCache = mapOf("fixture" to fixtureRgba),
            sceneClearColor = GPUClearColor(0.0, 0.0, 0.0, 0.0),
        )

        assertTrue(result.rendered)
        assertEquals(listOf("source", "blur-h", "blur-v", "scene"), target.passKinds)
        assertEquals(3, target.createdTextures.size)
        assertEquals(16, target.createdTextures.single { it.label.contains("source") }.width)
        assertEquals(22, target.createdTextures.single { it.label.contains("source") }.height)
        val sourceDraw = requireNotNull(target.sourceDraw)
        assertEquals(0, sourceDraw.scissorX)
        assertEquals(0, sourceDraw.scissorY)
        assertEquals(16, sourceDraw.scissorWidth)
        assertEquals(22, sourceDraw.scissorHeight)
        assertFloatUniforms(sourceDraw.uniformBytes, 6f, 9f, 10f, 13f)
    }

    @Test
    fun `accepted blur is dispatched after the scene composite`() {
        val target = CapturingOffscreenTarget()
        val dispatched = mutableListOf<String>()
        target.onSceneComposite = { assertTrue(dispatched.isEmpty()) }
        val command = blurCommand(sigmaX = 2f, sigmaY = 3f)

        val result = target.renderImageCommand(
            sceneTextureLabel = "scene",
            command = command,
            textureCache = mapOf("fixture" to fixtureRgba),
            sceneClearColor = GPUClearColor(0.0, 0.0, 0.0, 0.0),
            dispatched = dispatched,
        )

        assertTrue(result.rendered)
        assertEquals(listOf(command.commandId.toString()), dispatched)
    }

    private fun blurCommand(sigmaX: Float, sigmaY: Float) = DisplayOp.DrawImage(
        image = Image.fromPixels(
            width = 4,
            height = 4,
            pixels = fixtureRgba,
            colorType = ColorType.RGBA_8888,
            sourceId = "fixture",
        ),
        src = Rect(0f, 0f, 4f, 4f),
        dst = Rect(20f, 30f, 24f, 34f),
        paint = Paint(),
        transform = Matrix33.identity(),
        clip = ClipStack.WideOpen,
    ).toImageRectCommand(
        GPUDrawCommandID(1),
        GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
    ).copy(
        imageFilterPlan = GPUImageFilterPlan.Blur(
            sigmaX = sigmaX,
            sigmaY = sigmaY,
            haloX = 6,
            haloY = 9,
            outputBounds = GPURect(14f, 21f, 30f, 43f),
        ),
    )

    private class CapturingOffscreenTarget : GPUBackendOffscreenTarget {
        val passKinds = mutableListOf<String>()
        val createdTextures = mutableListOf<GPUBackendOffscreenTexture>()
        val targetCopyTextureLabels = mutableListOf<String>()
        var sourceDraw: GPUBackendRawUniformDraw? = null
        var onSceneComposite: (() -> Unit)? = null

        override val target: GPUSurfaceTarget
            get() = error("target is not used by this pass-planning test")

        override fun encode(clearColor: GPUClearColor, block: GPUBackendRenderRecorder.() -> Unit) = error("Unexpected target pass")

        override fun readRgba(): ByteArray = error("Unexpected readback")

        override fun createOffscreenTexture(texture: GPUBackendOffscreenTexture): String {
            createdTextures += texture
            return texture.label
        }

        override fun snapshotTargetToOffscreenTexture(textureLabel: String) = error("Unexpected snapshot")

        override fun copyTargetToOffscreenTexture(destinationTextureLabel: String) {
            targetCopyTextureLabels += destinationTextureLabel
        }

        override fun encodeOffscreenTexture(
            textureLabel: String,
            clearColor: GPUClearColor?,
            block: GPUBackendRenderRecorder.() -> Unit,
        ) {
            block(CapturingRecorder(textureLabel))
        }

        override fun createCoverageMask(request: GPUBackendCoverageMaskRequest): GPUBackendCoverageMask =
            error("Unexpected coverage mask allocation")

        override fun encodeCoverageMask(
            mask: GPUBackendCoverageMask,
            clearColor: GPUClearColor?,
            block: GPUBackendRenderRecorder.() -> Unit,
        ) = error("Unexpected coverage mask pass")

        override fun releaseCoverageMask(mask: GPUBackendCoverageMask) =
            error("Unexpected coverage mask release")

        override fun copyOffscreenTexture(sourceTextureLabel: String, destinationTextureLabel: String) =
            error("Unexpected GPU-to-GPU copy")

        override fun close() = Unit

        private inner class CapturingRecorder(
            private val destinationLabel: String,
        ) : GPUBackendRenderRecorder {
            override fun drawFullscreenTextureUniformPass(
                wgsl: String,
                colorFormat: String,
                textureRgba: ByteArray,
                textureWidth: Int,
                textureHeight: Int,
                textureFormat: String,
                draws: List<GPUBackendRawUniformDraw>,
                blendMode: GPUFixedFunctionBlendState?,
                stencilMode: GPUBackendStencilMode?,
                stencilConfig: org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilCoverConfig,
            ) {
                passKinds += "source"
                sourceDraw = draws.single()
            }

            override fun drawCompositePass(
                wgsl: String,
                colorFormat: String,
                textureLabel: String,
                draws: List<GPUBackendRawUniformDraw>,
                blendMode: GPUFixedFunctionBlendState?,
            ) {
                passKinds += when {
                    destinationLabel.contains("horizontal") -> "blur-h"
                    destinationLabel.contains("vertical") -> "blur-v"
                    else -> {
                        onSceneComposite?.invoke()
                        "scene"
                    }
                }
            }

            override fun drawTwoTexturePass(wgsl: String, colorFormat: String, firstTextureLabel: String, secondTextureLabel: String, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUFixedFunctionBlendState?) = unexpected()
            override fun drawThreeTexturePass(wgsl: String, colorFormat: String, firstTextureLabel: String, secondTextureLabel: String, thirdTextureLabel: String, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUFixedFunctionBlendState?) = unexpected()

            override fun drawFullscreenPass(wgsl: String, colorFormat: String, draws: List<GPUBackendRectDraw>, blendMode: GPUFixedFunctionBlendState?, passBatchKind: GPUBackendSimplePassBatchKind?) = unexpected()
            override fun drawFullscreenUniformPayloadPass(wgsl: String, colorFormat: String, draws: List<GPUBackendUniformPayloadDraw>, blendMode: GPUFixedFunctionBlendState?, sourceLabel: String, passBatchKind: GPUBackendSimplePassBatchKind?) = unexpected()
            override fun drawFullscreenRawUniformPass(wgsl: String, colorFormat: String, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUFixedFunctionBlendState?, passBatchKind: GPUBackendSimplePassBatchKind?) = unexpected()
            override fun drawFullscreenStencilPass(wgsl: String, colorFormat: String, stencilMode: GPUBackendStencilMode, triangleData: GPUBackendTriangleData?, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUFixedFunctionBlendState?, stencilConfig: org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilCoverConfig) = unexpected()
            override fun createVertexColorBuffer(data: GPUBackendVertexColorData): String = unexpected()
            override fun drawVertexColorIndexed(vertexBufferLabel: String, indexCount: Int, uniformDraw: GPUBackendRawUniformDraw, blendMode: GPUFixedFunctionBlendState?) = unexpected()
            override fun createVertexPositionUVBuffer(data: GPUBackendVertexPositionUVData): String = unexpected()
            override fun drawVertexPositionUVIndexed(vertexBufferLabel: String, indexCount: Int, uniformDraw: GPUBackendRawUniformDraw, textureRgba: ByteArray, textureWidth: Int, textureHeight: Int, textureFormat: String, blendMode: GPUFixedFunctionBlendState?) = unexpected()
            override fun drawVertexPositionDualUVIndexed(vertexBufferLabel: String, indexCount: Int, uniformDraw: GPUBackendRawUniformDraw, texture1Rgba: ByteArray, texture1Width: Int, texture1Height: Int, texture2Rgba: ByteArray, texture2Width: Int, texture2Height: Int, textureFormat: String, blendMode: GPUFixedFunctionBlendState?) = unexpected()
            override fun createOffscreenTexture(texture: GPUBackendOffscreenTexture): String = unexpected()
            override fun encodeOffscreenTexture(textureLabel: String, clearColor: GPUClearColor?, block: GPUBackendRenderRecorder.() -> Unit) = unexpected()
            override fun drawBlendPass(wgsl: String, colorFormat: String, srcTextureLabel: String, dstTextureLabel: String, draws: List<GPUBackendRawUniformDraw>) = unexpected()
            override fun drawTextAtlasPass(atlasRgba: ByteArray, atlasWidth: Int, atlasHeight: Int, atlasFormat: String, vertexData: FloatArray, indexData: IntArray, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUFixedFunctionBlendState?) = unexpected()
            override fun drawColorGlyphPass(atlasRgba: ByteArray, atlasWidth: Int, atlasHeight: Int, atlasFormat: String, vertexData: FloatArray, indexData: IntArray, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUFixedFunctionBlendState?) = unexpected()

            private fun unexpected(): Nothing = error("Unexpected recorder call")
        }
    }

    private companion object {
        val fixtureRgba = ByteArray(4 * 4 * 4) { 0x7f.toByte() }
    }

    private fun assertFloatUniforms(bytes: ByteArray, vararg expected: Float) {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        expected.forEachIndexed { index, value ->
            assertEquals(value, buffer.getFloat(index * 4), 0.001f, "uniform[$index]")
        }
    }
}
