package org.graphiks.kanvas.gpu.renderer.layers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GPUPreparedCompositePreflightTest {

    @Test
    fun `valid plan returns Ready`() {
        val plan = minimalPlan()
        val capabilities = GPUPreflightCapabilities(
            maxTextureSize = 8192,
            maxColorAttachments = 8,
        )
        val result = GPUPreparedCompositePreflight.preflight(plan, capabilities)
        require(result is GPUPreparedCompositeLowering.Ready)
        assertEquals(plan.identity, result.plan.identity)
    }

    @Test
    fun `layer count exceeds maxColorAttachments returns Refused with PREFLIGHT code`() {
        val layers = (0..5).map { minimalLayerPlan("layer_$it") }
        val plan = GPUPreparedCompositePlan(
            captureIdentity = "cap_v1",
            rootScopeId = GPUPreparedCompositeScopeId("root"),
            layers = layers,
            normalizedFilters = emptyMap(),
            identity = "plan_v1",
        )
        val capabilities = GPUPreflightCapabilities(
            maxTextureSize = 8192,
            maxColorAttachments = 4,
        )
        val result = GPUPreparedCompositePreflight.preflight(plan, capabilities)
        require(result is GPUPreparedCompositeLowering.Refused)
        assertEquals(GPUPreparedCompositeRefusalCodes.PREFLIGHT, result.code)
        assertEquals("6", result.facts["layerCount"])
        assertEquals("4", result.facts["maxColorAttachments"])
        assertEquals("Layer count exceeds device color attachment limit", result.facts["reason"])
    }

    @Test
    fun `oversized target exceeds maxTextureSize returns Refused with PREFLIGHT code`() {
        val layer = GPULayerPlan(
            saveRecord = GPULayerSaveRecord(
                scopeId = GPULayerScopeID("largeLayer"),
                boundsLabel = "large",
                backdropRequired = false,
            ),
            bounds = GPULayerBoundsPlan(
                requestedBoundsLabel = "large",
                deviceBoundsLabel = "0,0,10000,10000",
                conservative = false,
                finite = true,
                width = 10000,
                height = 10000,
            ),
            execution = GPULayerExecutionPlan.IsolatedTarget(
                target = GPULayerTargetPlan(
                    targetLabel = "large-target",
                    formatClass = "rgba8unorm",
                    sampleCount = 1,
                    lifetimeClass = "layer-local",
                    byteEstimate = 400000000L,
                ),
                tasks = GPULayerTaskPlan(
                    taskLabels = listOf("render"),
                    dependencies = emptyList(),
                ),
            ),
        )
        val plan = GPUPreparedCompositePlan(
            captureIdentity = "cap_v1",
            rootScopeId = GPUPreparedCompositeScopeId("root"),
            layers = listOf(layer),
            normalizedFilters = emptyMap(),
            identity = "plan_v1",
        )
        val capabilities = GPUPreflightCapabilities(
            maxTextureSize = 8192,
            maxColorAttachments = 8,
        )
        val result = GPUPreparedCompositePreflight.preflight(plan, capabilities)
        require(result is GPUPreparedCompositeLowering.Refused)
        assertEquals(GPUPreparedCompositeRefusalCodes.PREFLIGHT, result.code)
        assertEquals("0", result.facts["layerIndex"])
        assertEquals("10000", result.facts["targetDimension"])
        assertEquals("8192", result.facts["maxTextureSize"])
        assertEquals("Layer target exceeds max texture size", result.facts["reason"])
    }

    @Test
    fun `layer exactly at limit passes`() {
        val layer = GPULayerPlan(
            saveRecord = GPULayerSaveRecord(
                scopeId = GPULayerScopeID("exactLayer"),
                boundsLabel = "exact",
                backdropRequired = false,
            ),
            bounds = GPULayerBoundsPlan(
                requestedBoundsLabel = "exact",
                deviceBoundsLabel = "0,0,8192,8192",
                conservative = false,
                finite = true,
                width = 8192,
                height = 8192,
            ),
            execution = GPULayerExecutionPlan.IsolatedTarget(
                target = GPULayerTargetPlan(
                    targetLabel = "exact-target",
                    formatClass = "rgba8unorm",
                    sampleCount = 1,
                    lifetimeClass = "layer-local",
                    byteEstimate = 268435456L,
                ),
                tasks = GPULayerTaskPlan(
                    taskLabels = listOf("render"),
                    dependencies = emptyList(),
                ),
            ),
        )
        val plan = GPUPreparedCompositePlan(
            captureIdentity = "cap_v1",
            rootScopeId = GPUPreparedCompositeScopeId("root"),
            layers = listOf(layer),
            normalizedFilters = emptyMap(),
            identity = "plan_v1",
        )
        val capabilities = GPUPreflightCapabilities(
            maxTextureSize = 8192,
            maxColorAttachments = 8,
        )
        val result = GPUPreparedCompositePreflight.preflight(plan, capabilities)
        require(result is GPUPreparedCompositeLowering.Ready)
    }

    private fun minimalPlan(): GPUPreparedCompositePlan {
        return GPUPreparedCompositePlan(
            captureIdentity = "cap_v1",
            rootScopeId = GPUPreparedCompositeScopeId("root"),
            layers = emptyList(),
            normalizedFilters = emptyMap(),
            identity = "plan_v1",
        )
    }

    private fun minimalLayerPlan(label: String): GPULayerPlan {
        return GPULayerPlan(
            saveRecord = GPULayerSaveRecord(
                scopeId = GPULayerScopeID(label),
                boundsLabel = "bounds:$label",
                backdropRequired = false,
            ),
            bounds = GPULayerBoundsPlan(
                requestedBoundsLabel = "bounds:$label",
                deviceBoundsLabel = "0,0,256,256",
                conservative = false,
                finite = true,
                width = 256,
                height = 256,
            ),
            execution = GPULayerExecutionPlan.IsolatedTarget(
                target = GPULayerTargetPlan(
                    targetLabel = "target:$label",
                    formatClass = "rgba8unorm",
                    sampleCount = 1,
                    lifetimeClass = "layer-local",
                ),
                tasks = GPULayerTaskPlan(
                    taskLabels = listOf("render"),
                    dependencies = emptyList(),
                ),
            ),
        )
    }
}
