package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUOrderingFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTexture
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
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlan
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedBlurStyle
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.surface.Diagnostics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GPUMaskBlurDispatchTest {
    @Test
    fun `mask blur records five ordered passes then dispatches once`() {
        val target = CapturingMaskBlurTarget()
        val dispatched = mutableListOf<String>()

        val result = target.renderMaskBlurCommand(
            "scene", solidRectCommand(), readyPlan(NormalizedBlurStyle.NORMAL),
            GPUClearColor(0.0, 0.0, 0.0, 0.0), dispatched, Diagnostics(), "rgba8unorm",
        )

        assertTrue(result.rendered)
        assertEquals(listOf("mask", "blur-h", "blur-v", "style", "scene"), target.passKinds)
        assertEquals(listOf("1"), dispatched)
        assertEquals(4, target.createdTextures.size)
    }

    private fun solidRectCommand(): NormalizedDrawCommand.FillRect {
        val target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
        val bounds = GPUBounds(10f, 10f, 30f, 30f)
        return NormalizedDrawCommand.FillRect(
            commandId = GPUDrawCommandID(1),
            rect = GPURect(10f, 10f, 30f, 30f),
            transform = GPUTransformFacts.identity(),
            clip = GPUClipFacts.wideOpen(GPUBounds(0f, 0f, 64f, 64f)),
            layer = GPULayerFacts.root(target),
            material = GPUMaterialDescriptor.SolidColor(1f, 0.25f, 0.5f, 1f),
            blend = GPUBlendFacts.srcOver(),
            bounds = bounds,
            ordering = GPUOrderingFacts(0, dependsOnDestination = false, requiresBarrier = false),
            source = GPUCommandSource(adapter = "unit-test", operation = "fillRect"),
            antiAlias = false,
        )
    }

    private fun readyPlan(style: NormalizedBlurStyle) = MaskBlurPlan.Ready(
        style = style,
        requestedSigma = 2f,
        normalizedSigma = 2f,
        effectiveSigma = 2f,
        scale = 1f,
        deviceBounds = GPUBounds(4f, 4f, 36f, 36f),
        localWidth = 32,
        localHeight = 32,
        bytesPerTexture = 4096,
        requiredBytes = 16_384,
        diagnostics = emptyList(),
    )

    private class CapturingMaskBlurTarget : GPUBackendOffscreenTarget {
        val passKinds = mutableListOf<String>()
        val createdTextures = mutableListOf<GPUBackendOffscreenTexture>()

        override val target: GPUSurfaceTarget
            get() = error("target is not used by this pass-planning test")

        override fun encode(clearColor: GPUClearColor, block: GPUBackendRenderRecorder.() -> Unit) =
            error("Unexpected target pass")

        override fun readRgba(): ByteArray = error("Unexpected readback")

        override fun createOffscreenTexture(texture: GPUBackendOffscreenTexture): String {
            createdTextures += texture
            return texture.label
        }

        override fun snapshotTargetToOffscreenTexture(textureLabel: String) = error("Unexpected snapshot")

        override fun encodeOffscreenTexture(
            textureLabel: String,
            clearColor: GPUClearColor?,
            block: GPUBackendRenderRecorder.() -> Unit,
        ) {
            block(CapturingRecorder(textureLabel))
        }

        override fun close() = Unit

        private inner class CapturingRecorder(
            private val destinationLabel: String,
        ) : GPUBackendRenderRecorder {
            override val maxTextureDimension2D: Int
                get() = 4096

            override fun drawFullscreenPass(
                wgsl: String,
                colorFormat: String,
                draws: List<GPUBackendRectDraw>,
                blendMode: GPUBlendMode?,
                passBatchKind: GPUBackendSimplePassBatchKind?,
            ) {
                passKinds += "mask"
            }

            override fun drawCompositePass(
                wgsl: String,
                colorFormat: String,
                textureLabel: String,
                draws: List<GPUBackendRawUniformDraw>,
                blendMode: GPUBlendMode?,
            ) {
                passKinds += when {
                    destinationLabel.endsWith(":horizontal") -> "blur-h"
                    destinationLabel.endsWith(":vertical") -> "blur-v"
                    else -> "scene"
                }
            }

            override fun drawBlendPass(
                wgsl: String,
                colorFormat: String,
                srcTextureLabel: String,
                dstTextureLabel: String,
                draws: List<GPUBackendRawUniformDraw>,
            ) {
                passKinds += "style"
            }

            override fun drawFullscreenUniformPayloadPass(wgsl: String, colorFormat: String, draws: List<GPUBackendUniformPayloadDraw>, blendMode: GPUBlendMode?, sourceLabel: String, passBatchKind: GPUBackendSimplePassBatchKind?) = unexpected()
            override fun drawFullscreenRawUniformPass(wgsl: String, colorFormat: String, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUBlendMode?, passBatchKind: GPUBackendSimplePassBatchKind?) = unexpected()
            override fun drawFullscreenStencilPass(wgsl: String, colorFormat: String, stencilMode: GPUBackendStencilMode, triangleData: GPUBackendTriangleData?, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUBlendMode?) = unexpected()
            override fun drawFullscreenTextureUniformPass(wgsl: String, colorFormat: String, textureRgba: ByteArray, textureWidth: Int, textureHeight: Int, textureFormat: String, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUBlendMode?, stencilMode: GPUBackendStencilMode?) = unexpected()
            override fun createVertexColorBuffer(data: GPUBackendVertexColorData): String = unexpected()
            override fun drawVertexColorIndexed(vertexBufferLabel: String, indexCount: Int, uniformDraw: GPUBackendRawUniformDraw, blendMode: GPUBlendMode?) = unexpected()
            override fun createVertexPositionUVBuffer(data: GPUBackendVertexPositionUVData): String = unexpected()
            override fun drawVertexPositionUVIndexed(vertexBufferLabel: String, indexCount: Int, uniformDraw: GPUBackendRawUniformDraw, textureRgba: ByteArray, textureWidth: Int, textureHeight: Int, textureFormat: String, blendMode: GPUBlendMode?) = unexpected()
            override fun drawVertexPositionDualUVIndexed(vertexBufferLabel: String, indexCount: Int, uniformDraw: GPUBackendRawUniformDraw, texture1Rgba: ByteArray, texture1Width: Int, texture1Height: Int, texture2Rgba: ByteArray, texture2Width: Int, texture2Height: Int, textureFormat: String, blendMode: GPUBlendMode?) = unexpected()
            override fun createOffscreenTexture(texture: GPUBackendOffscreenTexture): String = unexpected()
            override fun encodeOffscreenTexture(textureLabel: String, clearColor: GPUClearColor?, block: GPUBackendRenderRecorder.() -> Unit) = unexpected()
            override fun drawTextAtlasPass(atlasRgba: ByteArray, atlasWidth: Int, atlasHeight: Int, atlasFormat: String, vertexData: FloatArray, indexData: IntArray, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUBlendMode?) = unexpected()
            override fun drawColorGlyphPass(atlasRgba: ByteArray, atlasWidth: Int, atlasHeight: Int, atlasFormat: String, vertexData: FloatArray, indexData: IntArray, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUBlendMode?) = unexpected()

            private fun unexpected(): Nothing = error("Unexpected recorder call")
        }
    }
}
