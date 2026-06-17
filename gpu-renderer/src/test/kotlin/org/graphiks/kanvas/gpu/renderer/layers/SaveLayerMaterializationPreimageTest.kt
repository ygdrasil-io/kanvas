package org.graphiks.kanvas.gpu.renderer.layers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.dumpLines

class SaveLayerMaterializationPreimageTest {
    @Test
    fun `isolated target gate derives layer target materialization preimage`() {
        val gate = GPUSaveLayerIsolatedTargetPlanner().plan(saveLayerPreimageRequest())

        val preimage = gate.toIsolatedTargetMaterializationPreimage()

        assertEquals(true, preimage.accepted)
        assertFalse(preimage.nonClaims.adapterBacked)
        assertFalse(preimage.nonClaims.liveHandles)
        assertFalse(preimage.nonClaims.productRoute)
        assertEquals(listOf(GPUMaterializedResourceRole.LayerTargetTexture), preimage.resources.map { it.role })
        assertEquals(
            listOf(
                "resource-preimage:accepted plan=savelayer:layer-card source=gpu-renderer.savelayer.isolated-target resources=layer-target:card bindings=none adapterBacked=false liveHandles=false productRoute=false",
                "resource-preimage:resource label=layer-target:card role=layer-target-texture generation=17 lifetime=layer-local descriptor=${gate.targetDescriptorHash} usage=render_attachment,texture_binding facts=load=clear;owner=GPURecorderScope;store=store",
                "resource-preimage:nonclaim adapterBacked=false liveHandles=false productRoute=false providerCalled=false submitCalled=false",
            ),
            preimage.dumpLines(),
        )
    }

    @Test
    fun `refused isolated target gate derives refused materialization preimage`() {
        val gate = GPUSaveLayerIsolatedTargetPlanner().plan(
            saveLayerPreimageRequest(
                saveRecord = saveLayerPreimageRecord(initWithPrevious = true),
            ),
        )

        val preimage = gate.toIsolatedTargetMaterializationPreimage()

        assertFalse(preimage.accepted)
        assertEquals("unsupported.layer.init_previous_unaccepted", preimage.refusalCode)
        assertEquals(
            listOf(
                "resource-preimage:refused plan=savelayer:layer-card source=gpu-renderer.savelayer.isolated-target reason=unsupported.layer.init_previous_unaccepted resources=none bindings=none adapterBacked=false liveHandles=false productRoute=false",
                "resource-preimage:nonclaim adapterBacked=false liveHandles=false productRoute=false providerCalled=false submitCalled=false",
            ),
            preimage.dumpLines(),
        )
    }
}

private fun saveLayerPreimageRequest(
    saveRecord: GPULayerSaveRecord = saveLayerPreimageRecord(),
): GPUSaveLayerIsolatedTargetRequest = GPUSaveLayerIsolatedTargetRequest(
    saveRecord = saveRecord,
    bounds = GPULayerBoundsPlan(
        requestedBoundsLabel = "card-local",
        deviceBoundsLabel = "0,0,64,48",
        conservative = true,
        originX = 0,
        originY = 0,
        width = 64,
        height = 48,
    ),
    parentTargetLabel = "root-target",
    deviceGeneration = 17,
)

private fun saveLayerPreimageRecord(
    initWithPrevious: Boolean = false,
): GPULayerSaveRecord = GPULayerSaveRecord(
    scopeId = GPULayerScopeID("layer:card"),
    parentScopeId = GPULayerScopeID("root"),
    boundsLabel = "card-local",
    childCommandIds = listOf("draw-rect", "draw-image"),
    initWithPrevious = initWithPrevious,
    backdropRequired = false,
)
