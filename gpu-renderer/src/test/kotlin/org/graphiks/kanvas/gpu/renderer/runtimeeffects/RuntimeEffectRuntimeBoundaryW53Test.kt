package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RuntimeEffectRuntimeBoundaryW53Test {
    private val descriptor = SimpleRTDescriptor.createDescriptor()
    private val snapshot = GPURuntimeEffectRegistrySnapshot("runtime-registry-v1", 1, listOf(descriptor), "test")

    @Test
    fun `dynamic source compilation and VM requests are refused by route gate`() {
        listOf(
            GPURuntimeEffectDescriptorRouteRequest(effectId = descriptor.id, requestedPlacement = GPURuntimeEffectRoutePlacement.MaterialSource, registrySnapshot = snapshot, wgslEvidence = null, cpuOracle = null, dynamicWGSLSourceProvided = true) to "unsupported.runtime_effect.dynamic_wgsl_forbidden",
            GPURuntimeEffectDescriptorRouteRequest(effectId = descriptor.id, requestedPlacement = GPURuntimeEffectRoutePlacement.MaterialSource, registrySnapshot = snapshot, wgslEvidence = null, cpuOracle = null, dynamicCompilationRequested = true) to "unsupported.runtime_effect.dynamic_compilation_forbidden",
            GPURuntimeEffectDescriptorRouteRequest(effectId = descriptor.id, requestedPlacement = GPURuntimeEffectRoutePlacement.MaterialSource, registrySnapshot = snapshot, wgslEvidence = null, cpuOracle = null, vmExecutionRequested = true) to "unsupported.runtime_effect.vm_execution_forbidden",
        ).forEach { (request, code) ->
            val result = GPURuntimeEffectDescriptorRoutePlanner().plan(request)
            assertEquals(code, result.diagnostics.single().code)
            assertIs<GPURuntimeEffectRoutePlan.Refused>(result.routePlan)
        }
    }

    @Test
    fun `unsupported color filter blender and image filter placements refuse explicitly`() {
        listOf(
            GPURuntimeEffectRoutePlacement.MaterialColorFilter,
            GPURuntimeEffectRoutePlacement.MaterialBlender,
            GPURuntimeEffectRoutePlacement.FilterRenderNode,
            GPURuntimeEffectRoutePlacement.FilterComputeNode,
        ).forEach { placement ->
            val result = GPURuntimeEffectDescriptorRoutePlanner().plan(
                GPURuntimeEffectDescriptorRouteRequest(
                    effectId = descriptor.id, requestedPlacement = placement, registrySnapshot = snapshot,
                    wgslEvidence = null, cpuOracle = null,
                ),
            )
            assertEquals("unsupported.runtime_effect.kind_mismatch", result.diagnostics.single().code)
        }
    }

    @Test
    fun `unregistered descriptor cannot enter any runtime boundary`() {
        val result = GPURuntimeEffectDescriptorRoutePlanner().plan(
            GPURuntimeEffectDescriptorRouteRequest(
                effectId = GPURuntimeEffectID("runtime.not_registered"),
                requestedPlacement = GPURuntimeEffectRoutePlacement.MaterialSource,
                registrySnapshot = snapshot,
                wgslEvidence = null,
                cpuOracle = null,
            ),
        )
        assertEquals("unsupported.runtime_effect.unregistered_descriptor", result.diagnostics.single().code)
        assertIs<GPURuntimeEffectRoutePlan.Refused>(result.routePlan)
    }
}
