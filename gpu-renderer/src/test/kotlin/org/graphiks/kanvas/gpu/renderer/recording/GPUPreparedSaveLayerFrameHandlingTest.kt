package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedClipSnapshot
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeEntry
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScope
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeId
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeKind
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeState
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeRefusalCodes
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedMatrixSnapshot
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedPaintSnapshot
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedPaintStyle
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedRectSnapshot
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedStrokeCap
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedStrokeJoin
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreflightCapabilities
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GPUPreparedSaveLayerFrameHandlingTest {

    @Test
    fun `empty capture produces Ready without materialization`() {
        val scopes = mapOf(
            rootScopeId to rootScope(),
        )

        val handling = builder().handleSaveLayer(
            scopes = scopes,
            rootScopeId = rootScopeId,
            identity = "test-empty",
            capabilities = defaultCapabilities(),
            context = defaultContext(),
        )

        val ready = assertIs<GPUPreparedSaveLayerFrameHandling.Ready>(handling)
        assertEquals(0, ready.results.size)
        assertTrue(ready.commands.isEmpty())
        assertEquals(0, ready.plan.layers.size)
    }

    @Test
    fun `single saveLayer materializes full command sequence`() {
        val layerId = GPUPreparedCompositeScopeId("layer_1")
        val layer = saveLayerScope(layerId, rectSnapshot(0f, 0f, 64f, 48f))
        val scopes = mapOf(
            rootScopeId to rootScope(entries = listOf(GPUPreparedCompositeEntry.Scope(layerId))),
            layerId to layer,
        )

        val handling = builder().handleSaveLayer(
            scopes = scopes,
            rootScopeId = rootScopeId,
            identity = "test-single",
            capabilities = defaultCapabilities(),
            context = defaultContext(),
        )

        val ready = assertIs<GPUPreparedSaveLayerFrameHandling.Ready>(handling)
        assertEquals(1, ready.results.size)
        val result = ready.results.single()
        assertIs<org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision.Materialized>(
            result.resourceDecision,
        )
        assertEquals(1, ready.plan.gatePlans.size)
        val commandKinds = ready.commands.map { it::class.simpleName }.toSet()
        assertTrue(commandKinds.contains("PrepareLayerTarget"), "missing PrepareLayerTarget in $commandKinds")
        assertTrue(commandKinds.contains("RenderLayerChildren"), "missing RenderLayerChildren in $commandKinds")
        assertTrue(commandKinds.contains("CompositeLayer"), "missing CompositeLayer in $commandKinds")
        assertEquals(1, ready.commands.filterIsInstance<GPUPassCommand.PrepareLayerTarget>().size)
        assertEquals(1, ready.commands.filterIsInstance<GPUPassCommand.CompositeLayer>().size)
    }

    @Test
    fun `oversized layer target is refused by preflight`() {
        val layerId = GPUPreparedCompositeScopeId("layer_1")
        val layer = saveLayerScope(layerId, rectSnapshot(0f, 0f, 128f, 96f))
        val scopes = mapOf(
            rootScopeId to rootScope(entries = listOf(GPUPreparedCompositeEntry.Scope(layerId))),
            layerId to layer,
        )

        val handling = builder().handleSaveLayer(
            scopes = scopes,
            rootScopeId = rootScopeId,
            identity = "test-oversized",
            capabilities = GPUPreflightCapabilities(maxTextureSize = 64, maxColorAttachments = 8),
            context = defaultContext(),
        )

        val refused = assertIs<GPUPreparedSaveLayerFrameHandling.Refused>(handling)
        assertEquals(GPUPreparedCompositeRefusalCodes.PREFLIGHT, refused.code)
    }

    @Test
    fun `layer exceeding materialization budget is refused stably`() {
        val layerId = GPUPreparedCompositeScopeId("layer_1")
        val layer = saveLayerScope(layerId, rectSnapshot(0f, 0f, 64f, 48f))
        val scopes = mapOf(
            rootScopeId to rootScope(entries = listOf(GPUPreparedCompositeEntry.Scope(layerId))),
            layerId to layer,
        )

        val handling = builder().handleSaveLayer(
            scopes = scopes,
            rootScopeId = rootScopeId,
            identity = "test-budget",
            capabilities = defaultCapabilities(),
            context = defaultContext(),
            targetBudgetBytes = 0L,
        )

        val refused = assertIs<GPUPreparedSaveLayerFrameHandling.Refused>(handling)
        assertEquals("unsupported.layer.target_too_large", refused.code)
        assertTrue(refused.facts.containsKey("scopeId"))
    }

    @Test
    fun `nested saveLayers materialize innermost first`() {
        val outerId = GPUPreparedCompositeScopeId("layer_outer")
        val innerId = GPUPreparedCompositeScopeId("layer_inner")
        val outer = saveLayerScope(
            id = outerId,
            bounds = rectSnapshot(0f, 0f, 128f, 96f),
            parentId = rootScopeId,
            saveOperationIndex = 0,
        )
        val inner = saveLayerScope(
            id = innerId,
            bounds = rectSnapshot(0f, 0f, 64f, 48f),
            parentId = outerId,
            saveOperationIndex = 1,
        )
        val scopes = mapOf(
            rootScopeId to rootScope(entries = listOf(GPUPreparedCompositeEntry.Scope(outerId))),
            outerId to outer,
            innerId to inner,
        )

        val handling = builder().handleSaveLayer(
            scopes = scopes,
            rootScopeId = rootScopeId,
            identity = "test-nested",
            capabilities = defaultCapabilities(),
            context = defaultContext(),
        )

        val ready = assertIs<GPUPreparedSaveLayerFrameHandling.Ready>(handling)
        assertEquals(2, ready.results.size)
        val compositeSources = ready.commands
            .filterIsInstance<GPUPassCommand.CompositeLayer>()
            .map(GPUPassCommand.CompositeLayer::sourceLabel)
        assertEquals(
            listOf("layer-target:layer_inner", "layer-target:layer_outer"),
            compositeSources,
            "inner layer must composite before its parent",
        )
    }

    @Test
    fun `device generation is threaded into gate and materialization evidence`() {
        val layerId = GPUPreparedCompositeScopeId("layer_1")
        val layer = saveLayerScope(layerId, rectSnapshot(0f, 0f, 64f, 48f))
        val scopes = mapOf(
            rootScopeId to rootScope(entries = listOf(GPUPreparedCompositeEntry.Scope(layerId))),
            layerId to layer,
        )

        val handling = builder().handleSaveLayer(
            scopes = scopes,
            rootScopeId = rootScopeId,
            identity = "test-generation",
            capabilities = defaultCapabilities(),
            context = GPUTargetPreparationContext(
                targetId = "target:test",
                frameId = "frame:test",
                deviceGeneration = 7L,
                budgetClass = "default",
            ),
        )

        val ready = assertIs<GPUPreparedSaveLayerFrameHandling.Ready>(handling)
        val gatePlan = ready.plan.gatePlans.getValue("layer_1")
        val target = (gatePlan.layerPlan.execution as
            org.graphiks.kanvas.gpu.renderer.layers.GPULayerExecutionPlan.IsolatedTarget).target
        assertEquals("target-generation:7", target.generationLabel)
        assertEquals(1, ready.results.size)
    }

    @Test
    fun `translucent alpha saveLayer produces composite layer command with layer alpha`() {
        val layerId = GPUPreparedCompositeScopeId("layer_1")
        val layer = saveLayerScope(
            id = layerId,
            bounds = rectSnapshot(0f, 0f, 64f, 48f),
            paint = translucentPaintSnapshot(),
        )
        val scopes = mapOf(
            rootScopeId to rootScope(entries = listOf(GPUPreparedCompositeEntry.Scope(layerId))),
            layerId to layer,
        )

        val handling = builder().handleSaveLayer(
            scopes = scopes,
            rootScopeId = rootScopeId,
            identity = "test-alpha",
            capabilities = defaultCapabilities(),
            context = defaultContext(),
        )

        val ready = assertIs<GPUPreparedSaveLayerFrameHandling.Ready>(handling)
        val composite = ready.commands
            .filterIsInstance<GPUPassCommand.CompositeLayer>()
            .single()
        assertEquals(128f / 255f, composite.alpha)
    }

    @Test
    fun `device rect clip saveLayer produces composite layer command carrying the clip label`() {
        val layerId = GPUPreparedCompositeScopeId("layer_1")
        val clip = GPUPreparedClipSnapshot.DeviceRect(
            rect = rectSnapshot(4f, 5f, 60f, 40f),
            antiAlias = false,
        )
        val layer = saveLayerScope(
            id = layerId,
            bounds = rectSnapshot(0f, 0f, 64f, 48f),
            clip = clip,
        )
        val scopes = mapOf(
            rootScopeId to rootScope(entries = listOf(GPUPreparedCompositeEntry.Scope(layerId))),
            layerId to layer,
        )

        val handling = builder().handleSaveLayer(
            scopes = scopes,
            rootScopeId = rootScopeId,
            identity = "test-clip",
            capabilities = defaultCapabilities(),
            context = defaultContext(),
        )

        val ready = assertIs<GPUPreparedSaveLayerFrameHandling.Ready>(handling)
        val composite = ready.commands
            .filterIsInstance<GPUPassCommand.CompositeLayer>()
            .single()
        assertEquals(
            "device-rect:l=${clip.rect.leftBits},t=${clip.rect.topBits},r=${clip.rect.rightBits}," +
                "b=${clip.rect.bottomBits},aa=${clip.antiAlias}",
            composite.clipLabel,
        )
    }

    @Test
    fun `non-srcOver blend saveLayer is refused by the restore blend gate`() {
        val layerId = GPUPreparedCompositeScopeId("layer_1")
        val layer = saveLayerScope(
            id = layerId,
            bounds = rectSnapshot(0f, 0f, 64f, 48f),
            paint = modulatePaintSnapshot(),
        )
        val scopes = mapOf(
            rootScopeId to rootScope(entries = listOf(GPUPreparedCompositeEntry.Scope(layerId))),
            layerId to layer,
        )

        val handling = builder().handleSaveLayer(
            scopes = scopes,
            rootScopeId = rootScopeId,
            identity = "test-modulate",
            capabilities = defaultCapabilities(),
            context = defaultContext(),
        )

        val refused = assertIs<GPUPreparedSaveLayerFrameHandling.Refused>(handling)
        assertEquals("unsupported.layer.restore_blend", refused.code)
        assertTrue(refused.facts.containsKey("scopeId"))
    }

    private fun builder() = GPUPreparedSurfaceFrameTaskListBuilder()

    private fun defaultCapabilities() =
        GPUPreflightCapabilities(maxTextureSize = 4096, maxColorAttachments = 8)

    private fun defaultContext() = GPUTargetPreparationContext(
        targetId = "target:test",
        frameId = "frame:test",
        deviceGeneration = 0L,
        budgetClass = "default",
    )

    companion object {
        private val rootScopeId = GPUPreparedCompositeScopeId("root")

        private fun rootScope(
            entries: List<GPUPreparedCompositeEntry> = emptyList(),
        ): GPUPreparedCompositeScope = GPUPreparedCompositeScope(
            id = rootScopeId,
            parentId = null,
            saveOperationIndex = null,
            restoreOperationIndex = null,
            entries = entries,
            sourceKind = GPUPreparedCompositeScopeKind.Root,
            provenance = "root",
            state = scopeState(),
        )

        private fun saveLayerScope(
            id: GPUPreparedCompositeScopeId,
            bounds: GPUPreparedRectSnapshot,
            parentId: GPUPreparedCompositeScopeId = rootScopeId,
            saveOperationIndex: Int? = 0,
            paint: GPUPreparedPaintSnapshot? = null,
            clip: GPUPreparedClipSnapshot = GPUPreparedClipSnapshot.WideOpen,
        ): GPUPreparedCompositeScope = GPUPreparedCompositeScope(
            id = id,
            parentId = parentId,
            saveOperationIndex = saveOperationIndex,
            restoreOperationIndex = if (saveOperationIndex == null) null else saveOperationIndex + 1,
            entries = listOf(GPUPreparedCompositeEntry.Draw(0)),
            sourceKind = GPUPreparedCompositeScopeKind.SaveLayer,
            provenance = "layer[0]",
            state = scopeState(bounds = bounds, paint = paint, clip = clip),
        )

        private fun scopeState(
            bounds: GPUPreparedRectSnapshot? = null,
            transform: GPUPreparedMatrixSnapshot = identityMatrix(),
            clip: GPUPreparedClipSnapshot = GPUPreparedClipSnapshot.WideOpen,
            paint: GPUPreparedPaintSnapshot? = null,
        ): GPUPreparedCompositeScopeState = GPUPreparedCompositeScopeState(
            bounds = bounds,
            paint = paint,
            transform = transform,
            clip = clip,
        )

        private fun translucentPaintSnapshot(): GPUPreparedPaintSnapshot = paintSnapshot(
            colorArgb = 128u shl 24,
        )

        private fun modulatePaintSnapshot(): GPUPreparedPaintSnapshot = paintSnapshot(
            blendMode = GPUBlendMode.MODULATE,
        )

        private fun paintSnapshot(
            colorArgb: UInt = 0xFFFFFFFFu,
            blendMode: GPUBlendMode = GPUBlendMode.SRC_OVER,
        ): GPUPreparedPaintSnapshot = GPUPreparedPaintSnapshot(
            colorArgb = colorArgb,
            blendMode = blendMode,
            style = GPUPreparedPaintStyle.Fill,
            strokeWidthBits = 0f.toRawBits(),
            strokeCap = GPUPreparedStrokeCap.Butt,
            strokeJoin = GPUPreparedStrokeJoin.Miter,
            strokeMiterBits = 4f.toRawBits(),
            antiAlias = false,
        )

        private fun rectSnapshot(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
        ): GPUPreparedRectSnapshot = GPUPreparedRectSnapshot(
            leftBits = left.toRawBits(),
            topBits = top.toRawBits(),
            rightBits = right.toRawBits(),
            bottomBits = bottom.toRawBits(),
        )

        private fun identityMatrix(): GPUPreparedMatrixSnapshot = GPUPreparedMatrixSnapshot(
            scaleXBits = 1.0f.toRawBits(),
            skewXBits = 0.0f.toRawBits(),
            transXBits = 0.0f.toRawBits(),
            skewYBits = 0.0f.toRawBits(),
            scaleYBits = 1.0f.toRawBits(),
            transYBits = 0.0f.toRawBits(),
            persp0Bits = 0.0f.toRawBits(),
            persp1Bits = 0.0f.toRawBits(),
            persp2Bits = 1.0f.toRawBits(),
        )
    }
}
