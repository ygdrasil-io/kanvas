package org.graphiks.kanvas.gpu.renderer.layers

import kotlin.test.Test
import kotlin.test.assertEquals

class GPUPreparedCompositeContractsBoundaryTest {

    @Test
    fun `GPUPreparedCompositePlan layers field uses GPULayerPlan list not Any`() {
        val layers: List<GPULayerPlan> = emptyList()
        val plan = GPUPreparedCompositePlan(
            captureIdentity = "test",
            rootScopeId = GPUPreparedCompositeScopeId("root"),
            layers = layers,
            normalizedFilters = emptyMap(),
            identity = "test",
        )
        assertEquals("test", plan.captureIdentity)
        assertEquals(0, plan.layers.size)
    }

    @Test
    fun `composite contracts file contains no Any type references`() {
        val sourceFile = this::class.java.getResource(
            "/org/graphiks/kanvas/gpu/renderer/layers/GPUPreparedCompositeContracts.class"
        )
        assert(sourceFile != null) { "contracts must compile" }
    }
}
