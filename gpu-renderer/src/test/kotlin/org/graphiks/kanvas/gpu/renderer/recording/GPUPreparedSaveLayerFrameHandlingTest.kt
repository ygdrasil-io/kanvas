package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedClipSnapshot
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeEntry
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScope
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeId
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeKind
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeState
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeRefusalCodes
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedMatrixSnapshot
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedRectSnapshot
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreflightCapabilities
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
        ): GPUPreparedCompositeScope = GPUPreparedCompositeScope(
            id = id,
            parentId = rootScopeId,
            saveOperationIndex = 0,
            restoreOperationIndex = 1,
            entries = listOf(GPUPreparedCompositeEntry.Draw(0)),
            sourceKind = GPUPreparedCompositeScopeKind.SaveLayer,
            provenance = "layer[0]",
            state = scopeState(bounds = bounds),
        )

        private fun scopeState(
            bounds: GPUPreparedRectSnapshot? = null,
            transform: GPUPreparedMatrixSnapshot = identityMatrix(),
            clip: GPUPreparedClipSnapshot = GPUPreparedClipSnapshot.WideOpen,
        ): GPUPreparedCompositeScopeState = GPUPreparedCompositeScopeState(
            bounds = bounds,
            paint = null,
            transform = transform,
            clip = clip,
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
