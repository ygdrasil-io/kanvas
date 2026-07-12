package org.graphiks.kanvas.surface.gpu

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElementKind
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendCoverageMask
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendCoverageMaskRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRectDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSimplePassBatchKind
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilCoverConfig
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilMode
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendTriangleData
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendUniformPayloadDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTexture
import org.graphiks.kanvas.gpu.renderer.execution.GPUSurfaceTarget
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue

@OptIn(ExperimentalUnsignedTypes::class)
class GPUClipCoverageDispatchTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `difference rrect path clip initializes mask then composes ordered coverage`() {
        val target = CapturingClipTarget()
        val cache = GPUClipCoverageFrameCache(totalBudgetBytes = 4096)
        val plan = complexPlan()
        cache.registerUses(plan.contentKey, count = 1)

        val lease = target.acquireClipMask(plan, cache, Diagnostics(), RenderConfig.DEFAULT)

        assertEquals(
            listOf("clear-white", "rect-dst-in", "rrect-dst-out", "path-stencil-dst-in"),
            target.passKinds,
        )
        val triangleData = requireNotNull(target.stencilWriteTriangleData)
        assertEquals(4, triangleData.triangleCount)
        assertEquals(24, triangleData.vertices.size)
        assertEquals((0 until 12).toList(), triangleData.indices.toList())
        assertFalse(lease.cacheHit)
        lease.close()
        lease.close()
        assertEquals(1, target.releasedMasks.size)
    }

    @Test
    fun `second consumer of identical stack reuses the frame local mask`() {
        val cache = GPUClipCoverageFrameCache(totalBudgetBytes = 4096)
        cache.registerUses("same", count = 2)
        val first = cache.acquire(maskPlan("same")) { "mask:same" }
        val second = cache.acquire(maskPlan("same")) { error("must reuse") }

        assertFalse(first.cacheHit)
        assertTrue(second.cacheHit)
        first.close()
        assertTrue(cache.contains("same"))
        second.close()
        assertFalse(cache.contains("same"))
    }

    @Test
    fun `frame cache refuses unknown uses and preaccounted budget before allocation`() {
        val unregistered = GPUClipCoverageFrameCache(totalBudgetBytes = 4096)
        assertFailsWith<IllegalStateException> {
            unregistered.acquire(maskPlan("unknown")) { error("must not create") }
        }

        val overBudget = GPUClipCoverageFrameCache(totalBudgetBytes = 7)
        overBudget.registerUses("same", count = 1)
        var created = false
        assertFailsWith<IllegalStateException> {
            overBudget.acquire(maskPlan("same")) {
                created = true
                "must not create"
            }
        }
        assertFalse(created)
    }

    @Test
    fun `clip composite uniform pack matches the reflected vec4 layout`() {
        val draw = clipMaskCompositeUniformDraw(width = 7, height = 9)
        val values = ByteBuffer.wrap(draw.uniformBytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(16, draw.uniformBytes.size)
        repeat(4) { assertEquals(0f, values.getFloat()) }
        assertEquals(7, draw.scissorWidth)
        assertEquals(9, draw.scissorHeight)
    }

    @Test
    fun `textured vertices source encoder overrides the logical blend with src over`() {
        val target = CapturingClipTarget()

        val encoded = target.recorder().dispatchTexturedVertices(
            positions = floatArrayOf(0f, 0f, 4f, 0f, 0f, 4f),
            uvs = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f),
            uvs2 = null,
            indices = intArrayOf(0, 1, 2),
            paint = Paint.fill(Color.RED).copy(blendMode = BlendMode.SRC),
            textureBytes = byteArrayOf(0, 0, 0xff.toByte(), 0xff.toByte()),
            textureWidth = 1,
            textureHeight = 1,
            textureSourceId = "clip-source-vertex",
            diagnostics = Diagnostics(),
            surfaceWidth = 8,
            surfaceHeight = 8,
            config = RenderConfig.DEFAULT,
            diagnosticName = "drawVertices",
            blendModeOverride = GPUBlendMode.SRC_OVER,
        )

        assertTrue(encoded)
        assertEquals(listOf<GPUBlendMode?>(GPUBlendMode.SRC_OVER), target.vertexBlendModes)
    }

    @Test
    fun `use prepass counts one complex mask per logical points draw`() {
        val clip = ClipStack.Complex(
            listOf(
                ClipStackOp.RectOp(Rect.fromLTRB(0f, 0f, 8f, 8f), ClipOp.INTERSECT, antiAlias = true),
            ),
        )
        val operations = listOf<DisplayOp>(
            DisplayOp.DrawRect(Rect.fromLTRB(1f, 1f, 7f, 7f), Paint.fill(Color.RED), Matrix33.identity(), clip),
            DisplayOp.DrawPoints(
                PointMode.POINTS,
                listOf(Point(2f, 2f), Point(6f, 6f)),
                Paint.fill(Color.RED),
                Matrix33.identity(),
                clip,
            ),
        )
        val cache = GPUClipCoverageFrameCache(totalBudgetBytes = 4096)

        val result = GPUClipUsePrepass.register(
            operations = operations,
            target = GPUTargetFacts(8, 8, "rgba8unorm"),
            config = RenderConfig.DEFAULT,
            maxTextureDimension2D = 4096,
            cache = cache,
        )

        assertEquals(2, result.registeredUsesByKey.values.single())
        assertTrue(result.refusalCodes.isEmpty())
    }

    @Test
    fun `complex clip is materialized and composited by the real GPU route`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")
        runtime!!.close()
        val result = Surface(width = 16, height = 16).run {
            canvas {
                clipRect(Rect.fromLTRB(2f, 2f, 14f, 14f), antiAlias = false)
                clipRRect(
                    RRect(
                        rect = Rect.fromLTRB(5f, 5f, 11f, 11f),
                        topLeft = CornerRadii(2f, 2f),
                        topRight = CornerRadii(2f, 2f),
                        bottomRight = CornerRadii(2f, 2f),
                        bottomLeft = CornerRadii(2f, 2f),
                    ),
                    ClipOp.DIFFERENCE,
                    antiAlias = false,
                )
                clipPath(
                    Path().apply { addRect(Rect.fromLTRB(6f, 6f, 10f, 10f)) },
                    ClipOp.DIFFERENCE,
                    antiAlias = false,
                )
                drawRect(Rect.fromLTRB(0f, 0f, 16f, 16f), Paint.fill(Color.RED))
            }
            render()
        }

        assertEquals(0, result.stats.opsRefused)
        assertEquals(0, alphaAt(result.pixels, 1, 8, 16))
        assertTrue(alphaAt(result.pixels, 3, 3, 16) > 0)
        assertTrue(alphaAt(result.pixels, 5, 5, 16) > 0)
        assertEquals(0, alphaAt(result.pixels, 8, 8, 16))
    }

    private fun alphaAt(pixels: UByteArray, x: Int, y: Int, width: Int): Int =
        pixels[(y * width + x) * 4 + 3].toInt() and 0xff

    private fun complexPlan(): GPUClipCoveragePlan.Mask = GPUClipCoveragePlan.Mask(
        contentKey = "complex",
        width = 8,
        height = 8,
        sampleCount = 4,
        resolvedBytes = 256,
        requiredBytes = 1280,
        elements = listOf(
            element(
                operation = GPUClipCoverageOperation.Intersect,
                kind = GPUClipCoverageElementKind.Rect,
                values = listOf(0f, 0f, 8f, 8f),
            ),
            element(
                operation = GPUClipCoverageOperation.Difference,
                kind = GPUClipCoverageElementKind.RRect,
                values = listOf(1f, 1f, 7f, 7f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f),
                antiAlias = true,
            ),
            element(
                operation = GPUClipCoverageOperation.Intersect,
                kind = GPUClipCoverageElementKind.Path,
                values = listOf(1f, 0f, 0f, 0f, 8f, 0f, 8f, 8f, 0f, 8f),
                vertexCount = 4,
                antiAlias = true,
            ),
        ),
    )

    private fun maskPlan(contentKey: String): GPUClipCoveragePlan.Mask = GPUClipCoveragePlan.Mask(
        contentKey = contentKey,
        width = 1,
        height = 1,
        sampleCount = 1,
        resolvedBytes = 4,
        requiredBytes = 8,
        elements = emptyList(),
    )

    private fun element(
        operation: GPUClipCoverageOperation = GPUClipCoverageOperation.Intersect,
        kind: GPUClipCoverageElementKind,
        values: List<Float>,
        vertexCount: Int = 0,
        antiAlias: Boolean = false,
    ): GPUClipCoverageElement = GPUClipCoverageElement(
        operation = operation,
        kind = kind,
        values = values,
        vertexCount = vertexCount,
        antiAlias = antiAlias,
        fillRule = GPUClipFillRule.Winding,
        inverseFill = false,
    )

    private class CapturingClipTarget : GPUBackendOffscreenTarget {
        val passKinds = mutableListOf<String>()
        val releasedMasks = mutableListOf<GPUBackendCoverageMask>()
        val vertexBlendModes = mutableListOf<GPUBlendMode?>()
        var stencilWriteTriangleData: GPUBackendTriangleData? = null

        fun recorder(): GPUBackendRenderRecorder = CapturingRecorder()

        override val target: GPUSurfaceTarget
            get() = error("target is not used by this pass-planning test")

        override fun encode(clearColor: GPUClearColor, block: GPUBackendRenderRecorder.() -> Unit) =
            error("Unexpected target pass")

        override fun readRgba(): ByteArray = error("Unexpected readback")

        override fun createOffscreenTexture(texture: GPUBackendOffscreenTexture): String = unexpected()

        override fun snapshotTargetToOffscreenTexture(textureLabel: String) = unexpected()

        override fun encodeOffscreenTexture(
            textureLabel: String,
            clearColor: GPUClearColor?,
            block: GPUBackendRenderRecorder.() -> Unit,
        ) = unexpected()

        override fun createCoverageMask(request: GPUBackendCoverageMaskRequest): GPUBackendCoverageMask =
            GPUBackendCoverageMask(
                renderLabel = request.label,
                sampleLabel = "${request.label}:sample",
                width = request.width,
                height = request.height,
                sampleCount = request.sampleCount,
            )

        override fun encodeCoverageMask(
            mask: GPUBackendCoverageMask,
            clearColor: GPUClearColor?,
            block: GPUBackendRenderRecorder.() -> Unit,
        ) {
            assertEquals(GPUClearColor(1.0, 1.0, 1.0, 1.0), clearColor)
            passKinds += "clear-white"
            block(CapturingRecorder())
        }

        override fun releaseCoverageMask(mask: GPUBackendCoverageMask) {
            releasedMasks += mask
        }

        override fun copyOffscreenTexture(sourceTextureLabel: String, destinationTextureLabel: String) = unexpected()

        override fun close() = Unit

        private inner class CapturingRecorder : GPUBackendRenderRecorder {
            override fun drawFullscreenRawUniformPass(
                wgsl: String,
                colorFormat: String,
                draws: List<GPUBackendRawUniformDraw>,
                blendMode: GPUBlendMode?,
                passBatchKind: GPUBackendSimplePassBatchKind?,
            ) {
                passKinds += when (wgsl) {
                    RECT_AA_WGSL -> "rect-${clipBlendLabel(blendMode!!)}"
                    RRECT_WGSL -> "rrect-${clipBlendLabel(blendMode!!)}"
                    else -> error("Unexpected raw WGSL")
                }
            }

            override fun drawFullscreenStencilPass(
                wgsl: String,
                colorFormat: String,
                stencilMode: GPUBackendStencilMode,
                triangleData: GPUBackendTriangleData?,
                draws: List<GPUBackendRawUniformDraw>,
                blendMode: GPUBlendMode?,
                stencilConfig: GPUBackendStencilCoverConfig,
            ) {
                when (stencilMode) {
                    GPUBackendStencilMode.Write -> {
                        assertEquals(CLIP_STENCIL_WRITE_WGSL, wgsl)
                        stencilWriteTriangleData = triangleData
                    }
                    GPUBackendStencilMode.Test -> {
                        assertEquals(CLIP_MASK_COVER_WGSL, wgsl)
                        if (passKinds.none { it.startsWith("path-stencil-") }) {
                            passKinds += "path-stencil-${clipBlendLabel(blendMode!!)}"
                        }
                    }
                }
            }

            override fun drawFullscreenPass(wgsl: String, colorFormat: String, draws: List<GPUBackendRectDraw>, blendMode: GPUBlendMode?, passBatchKind: GPUBackendSimplePassBatchKind?) = unexpected()
            override fun drawFullscreenUniformPayloadPass(wgsl: String, colorFormat: String, draws: List<GPUBackendUniformPayloadDraw>, blendMode: GPUBlendMode?, sourceLabel: String, passBatchKind: GPUBackendSimplePassBatchKind?) = unexpected()
            override fun drawFullscreenTextureUniformPass(wgsl: String, colorFormat: String, textureRgba: ByteArray, textureWidth: Int, textureHeight: Int, textureFormat: String, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUBlendMode?, stencilMode: GPUBackendStencilMode?, stencilConfig: GPUBackendStencilCoverConfig) = unexpected()
            override fun createVertexColorBuffer(data: org.graphiks.kanvas.gpu.renderer.execution.GPUBackendVertexColorData): String = unexpected()
            override fun drawVertexColorIndexed(vertexBufferLabel: String, indexCount: Int, uniformDraw: GPUBackendRawUniformDraw, blendMode: GPUBlendMode?) = unexpected()
            override fun createVertexPositionUVBuffer(data: org.graphiks.kanvas.gpu.renderer.execution.GPUBackendVertexPositionUVData): String = "vertex-buffer"
            override fun drawVertexPositionUVIndexed(vertexBufferLabel: String, indexCount: Int, uniformDraw: GPUBackendRawUniformDraw, textureRgba: ByteArray, textureWidth: Int, textureHeight: Int, textureFormat: String, blendMode: GPUBlendMode?) {
                vertexBlendModes += blendMode
            }
            override fun drawVertexPositionDualUVIndexed(vertexBufferLabel: String, indexCount: Int, uniformDraw: GPUBackendRawUniformDraw, texture1Rgba: ByteArray, texture1Width: Int, texture1Height: Int, texture2Rgba: ByteArray, texture2Width: Int, texture2Height: Int, textureFormat: String, blendMode: GPUBlendMode?) = unexpected()
            override fun createOffscreenTexture(texture: GPUBackendOffscreenTexture): String = unexpected()
            override fun encodeOffscreenTexture(textureLabel: String, clearColor: GPUClearColor?, block: GPUBackendRenderRecorder.() -> Unit) = unexpected()
            override fun drawCompositePass(wgsl: String, colorFormat: String, textureLabel: String, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUBlendMode?) = unexpected()
            override fun drawTwoTexturePass(wgsl: String, colorFormat: String, firstTextureLabel: String, secondTextureLabel: String, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUBlendMode?) = unexpected()
            override fun drawThreeTexturePass(wgsl: String, colorFormat: String, firstTextureLabel: String, secondTextureLabel: String, thirdTextureLabel: String, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUBlendMode?) = unexpected()
            override fun drawBlendPass(wgsl: String, colorFormat: String, srcTextureLabel: String, dstTextureLabel: String, draws: List<GPUBackendRawUniformDraw>) = unexpected()
            override fun drawTextAtlasPass(atlasRgba: ByteArray, atlasWidth: Int, atlasHeight: Int, atlasFormat: String, vertexData: FloatArray, indexData: IntArray, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUBlendMode?) = unexpected()
            override fun drawColorGlyphPass(atlasRgba: ByteArray, atlasWidth: Int, atlasHeight: Int, atlasFormat: String, vertexData: FloatArray, indexData: IntArray, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUBlendMode?) = unexpected()

            private fun unexpected(): Nothing = error("Unexpected recorder call")

            private fun clipBlendLabel(blendMode: GPUBlendMode): String = when (blendMode) {
                GPUBlendMode.DST_IN -> "dst-in"
                GPUBlendMode.DST_OUT -> "dst-out"
                else -> error("Unexpected clip blend mode: $blendMode")
            }
        }

        private fun unexpected(): Nothing = error("Unexpected target call")
    }
}
