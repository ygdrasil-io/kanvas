package org.graphiks.kanvas.gpu.renderer.layers

import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GPUPreparedCompositeLowererTest {

    @Test
    fun `empty capture produces Ready with zero layers`() {
        val scopes = mapOf(
            rootScopeId to rootScope(),
        )

        val result = GPUPreparedCompositeLowerer.lower(scopes, rootScopeId, identity = "test-empty")

        val ready = assertIs<GPUPreparedCompositeLowering.Ready>(result)
        assertEquals(0, ready.plan.layers.size)
        assertEquals("test-empty", ready.plan.captureIdentity)
        assertEquals(rootScopeId, ready.plan.rootScopeId)
    }

    @Test
    fun `single saveLayer with draws produces Ready with one layer plan`() {
        val layerId = GPUPreparedCompositeScopeId("layer_1")
        val state = scopeState(
            bounds = rectSnapshot(0f, 0f, 64f, 48f),
        )
        val layer = GPUPreparedCompositeScope(
            id = layerId,
            parentId = rootScopeId,
            saveOperationIndex = 0,
            restoreOperationIndex = 1,
            entries = listOf(GPUPreparedCompositeEntry.Draw(0)),
            sourceKind = GPUPreparedCompositeScopeKind.SaveLayer,
            provenance = "layer[0]",
            state = state,
        )
        val scopes = mapOf(
            rootScopeId to rootScope(entries = listOf(GPUPreparedCompositeEntry.Scope(layerId))),
            layerId to layer,
        )

        val result = GPUPreparedCompositeLowerer.lower(scopes, rootScopeId, identity = "test-single")

        val ready = assertIs<GPUPreparedCompositeLowering.Ready>(result)
        assertEquals(1, ready.plan.layers.size)
        assertEquals("test-single", ready.plan.captureIdentity)
    }

    @Test
    fun `nested saveLayers produce Ready with two layer plans`() {
        val innerId = GPUPreparedCompositeScopeId("layer_2")
        val outerId = GPUPreparedCompositeScopeId("layer_1")
        val inner = GPUPreparedCompositeScope(
            id = innerId,
            parentId = outerId,
            saveOperationIndex = 1,
            restoreOperationIndex = 2,
            entries = listOf(GPUPreparedCompositeEntry.Draw(0)),
            sourceKind = GPUPreparedCompositeScopeKind.SaveLayer,
            provenance = "layer[1]",
            state = scopeState(bounds = rectSnapshot(0f, 0f, 32f, 24f)),
        )
        val outer = GPUPreparedCompositeScope(
            id = outerId,
            parentId = rootScopeId,
            saveOperationIndex = 0,
            restoreOperationIndex = 3,
            entries = listOf(GPUPreparedCompositeEntry.Scope(innerId)),
            sourceKind = GPUPreparedCompositeScopeKind.SaveLayer,
            provenance = "layer[0]",
            state = scopeState(bounds = rectSnapshot(0f, 0f, 64f, 48f)),
        )
        val scopes = mapOf(
            rootScopeId to rootScope(entries = listOf(GPUPreparedCompositeEntry.Scope(outerId))),
            outerId to outer,
            innerId to inner,
        )

        val result = GPUPreparedCompositeLowerer.lower(scopes, rootScopeId, identity = "test-nested")

        val ready = assertIs<GPUPreparedCompositeLowering.Ready>(result)
        assertEquals(2, ready.plan.layers.size)
    }

    @Test
    fun `Root and PaintedPicture scopes are skipped`() {
        val paintedId = GPUPreparedCompositeScopeId("painted_1")
        val painted = GPUPreparedCompositeScope(
            id = paintedId,
            parentId = rootScopeId,
            saveOperationIndex = 0,
            restoreOperationIndex = 0,
            entries = listOf(GPUPreparedCompositeEntry.Draw(0)),
            sourceKind = GPUPreparedCompositeScopeKind.PaintedPicture,
            provenance = "picture[0]",
            state = scopeState(bounds = rectSnapshot(0f, 0f, 32f, 24f)),
        )
        val scopes = mapOf(
            rootScopeId to rootScope(entries = listOf(GPUPreparedCompositeEntry.Scope(paintedId))),
            paintedId to painted,
        )

        val result = GPUPreparedCompositeLowerer.lower(scopes, rootScopeId, identity = "test-skip")

        val ready = assertIs<GPUPreparedCompositeLowering.Ready>(result)
        assertEquals(0, ready.plan.layers.size)
    }

    @Test
    fun `SaveLayer with null state is skipped`() {
        val layerId = GPUPreparedCompositeScopeId("layer_1")
        val layer = GPUPreparedCompositeScope(
            id = layerId,
            parentId = rootScopeId,
            saveOperationIndex = 0,
            restoreOperationIndex = 1,
            entries = listOf(GPUPreparedCompositeEntry.Draw(0)),
            sourceKind = GPUPreparedCompositeScopeKind.SaveLayer,
            provenance = "layer[0]",
            state = null,
        )
        val scopes = mapOf(
            rootScopeId to rootScope(entries = listOf(GPUPreparedCompositeEntry.Scope(layerId))),
            layerId to layer,
        )

        val result = GPUPreparedCompositeLowerer.lower(scopes, rootScopeId, identity = "test-null-state")

        val ready = assertIs<GPUPreparedCompositeLowering.Ready>(result)
        assertEquals(0, ready.plan.layers.size)
    }

    @Test
    fun `SaveLayer refused by planner is propagated as Refused`() {
        val layerId = GPUPreparedCompositeScopeId("layer_1")
        val state = scopeState(
            bounds = rectSnapshot(0f, 0f, 0f, 48f),
        )
        val layer = GPUPreparedCompositeScope(
            id = layerId,
            parentId = rootScopeId,
            saveOperationIndex = 5,
            restoreOperationIndex = 6,
            entries = listOf(GPUPreparedCompositeEntry.Draw(0)),
            sourceKind = GPUPreparedCompositeScopeKind.SaveLayer,
            provenance = "layer[0]",
            state = state,
        )
        val scopes = mapOf(
            rootScopeId to rootScope(entries = listOf(GPUPreparedCompositeEntry.Scope(layerId))),
            layerId to layer,
        )

        val result = GPUPreparedCompositeLowerer.lower(scopes, rootScopeId, identity = "test-refused")

        val refused = assertIs<GPUPreparedCompositeLowering.Refused>(result)
        assertEquals("unsupported.layer.bounds_invalid", refused.code)
        assertEquals(5, refused.operationIndex)
        assertTrue(refused.facts.containsKey("scopeId"))
    }

    @Test
    fun `FilterPictureSource scope is skipped`() {
        val filterId = GPUPreparedCompositeScopeId("filter_1")
        val filter = GPUPreparedCompositeScope(
            id = filterId,
            parentId = rootScopeId,
            saveOperationIndex = 0,
            restoreOperationIndex = 0,
            entries = listOf(GPUPreparedCompositeEntry.Draw(0)),
            sourceKind = GPUPreparedCompositeScopeKind.FilterPictureSource,
            provenance = "filter[0]",
            state = scopeState(bounds = rectSnapshot(0f, 0f, 32f, 24f)),
        )
        val scopes = mapOf(
            rootScopeId to rootScope(entries = listOf(GPUPreparedCompositeEntry.Scope(filterId))),
            filterId to filter,
        )

        val result = GPUPreparedCompositeLowerer.lower(scopes, rootScopeId, identity = "test-filter-skip")

        val ready = assertIs<GPUPreparedCompositeLowering.Ready>(result)
        assertEquals(0, ready.plan.layers.size)
    }

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

        private fun scopeState(
            bounds: GPUPreparedRectSnapshot? = null,
            paint: GPUPreparedPaintSnapshot? = null,
            transform: GPUPreparedMatrixSnapshot = identityMatrix(),
            clip: GPUPreparedClipSnapshot = GPUPreparedClipSnapshot.WideOpen,
        ): GPUPreparedCompositeScopeState = GPUPreparedCompositeScopeState(
            bounds = bounds,
            paint = paint,
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
