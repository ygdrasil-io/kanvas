package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadUploadPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadField
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext

class GPURuntimeEffectExecutorTest {

    private val emptyRegistry = object : GPURuntimeEffectRegistry {
        override fun lookup(id: GPURuntimeEffectID): GPURuntimeEffectDescriptor? = null
    }

    @Test
    fun `executor refuses unregistered effect`() {
        val executor = GPURuntimeEffectExecutor(emptyRegistry)
        val request = executorRequest(effectId = GPURuntimeEffectID("runtime.unknown"))
        val result = executor.execute(request)
        assertEquals("unsupported.runtime_effect.unregistered_descriptor", result.gpuReadbackReason)
        assertIs<GPUResourceMaterializationDecision.Refused>(result.resourceDecision)
    }

    @Test
    fun `executor produces evidence row for refused lane`() {
        val executor = GPURuntimeEffectExecutor(emptyRegistry)
        val request = executorRequest(effectId = GPURuntimeEffectID("runtime.unknown"))
        val result = executor.execute(request)
        assertEquals("gpu-renderer.runtime-effect.registered.execution", result.evidenceRow)
    }
}

private fun executorRequest(
    effectId: GPURuntimeEffectID = GPURuntimeEffectID("runtime.simple_rt"),
    descriptorVersion: GPURuntimeEffectDescriptorVersion = GPURuntimeEffectDescriptorVersion(1),
    pipelineCacheKey: String = "render-pipeline:render:executor-test",
): GPURuntimeEffectExecutionRequest {
    val gatePlan = gatePlan(effectId, descriptorVersion)
    return GPURuntimeEffectExecutionRequest(
        label = "executor-test",
        gatePlan = gatePlan,
        expectedDescriptorId = effectId,
        expectedDescriptorVersion = descriptorVersion,
        expectedRegistryGeneration = 1L,
        expectedRoutePlacement = GPURuntimeEffectRoutePlacement.MaterialSource,
        expectedWgslModuleHash = "module:simple_rt:v1",
        expectedReflectionHash = "reflection:simple_rt:v1",
        expectedUniformSchemaHash = "schema:simple_rt:v1",
        payloadRequest = payloadRequest(),
        pipelineCacheKey = pipelineCacheKey,
        targetStateHash = "target-state:rgba8unorm",
        loadStoreLabel = "load-store:clear",
        passId = "pass-executor",
        packetStreamId = "runtime-effect:executor-test:packets",
        streamId = "runtime-effect:executor-test:stream",
        vertexSourceLabel = "fullscreen-triangle",
        cpuOracleEvidenceHash = "sha256:0000000000000000000000000000000000000000000000000000000000000000",
        gpuReadbackStatus = "skipped",
        gpuReadbackReason = "headless-contract-only",
    )
}

private fun gatePlan(
    effectId: GPURuntimeEffectID,
    descriptorVersion: GPURuntimeEffectDescriptorVersion,
): GPURuntimeEffectDescriptorGatePlan = GPURuntimeEffectDescriptorGatePlan(
    label = "executor-test",
    evidenceRow = "gpu-renderer.runtime-effect.registered",
    routeKind = "GPUNative",
    classification = "DependencyGated",
    promoted = false,
    productActivation = true,
    materialized = false,
    routePlan = GPURuntimeEffectRoutePlan.Refused(
        lookupPlan = GPURuntimeEffectLookupPlan(
            inputKind = "descriptor-id",
            registryVersion = "runtime-registry-v1",
            registryGeneration = 1L,
            requestedEffectId = effectId,
            descriptorId = effectId,
            descriptorVersion = descriptorVersion,
            requestedPlacement = GPURuntimeEffectRoutePlacement.MaterialSource,
            routePlacement = null,
            diagnostic = GPURuntimeEffectDiagnostic(
                code = "placeholder",
                effectId = effectId,
                message = "placeholder gate plan for executor test",
                terminal = false,
            ),
        ),
        diagnostic = GPURuntimeEffectDiagnostic(
            code = "placeholder",
            effectId = effectId,
            message = "placeholder gate plan for executor test",
            terminal = false,
        ),
    ),
    registrySnapshot = GPURuntimeEffectRegistrySnapshot(
        registryVersion = "runtime-registry-v1",
        generation = 1L,
        descriptors = emptyList(),
        provenance = "executor-test-fixture",
    ),
    payloadPlanHash = "payload:executor-test",
    materialKeyBoundaryHash = "material-key:executor-test",
    materialKeyIncludesUniformValues = false,
    diagnostics = emptyList(),
)

private fun payloadRequest(): GPUPayloadMaterializationRequest = GPUPayloadMaterializationRequest(
    targetId = "root-target",
    packetId = "runtime-effect:executor-test:draw",
    taskIds = listOf("task-executor-test"),
    resourcePlanLabels = listOf("runtime-effect-execution:executor-test"),
    uniformBlock = GPUUniformPayloadBlock(
        fingerprint = GPUPayloadFingerprint("uniform-fingerprint-executor"),
        packingPlanHash = "executor-layout-v1",
        byteSize = 16L,
        zeroedPadding = true,
        scope = "pass-executor",
        bytes = listOf(0, 0, 128, 63, 0, 0, 0, 63, 0, 0, 0, 0, 0, 0, 128, 63),
        fields = listOf(
            GPUUniformPayloadField(
                fieldPath = "gColor",
                byteOffset = 0L,
                byteSize = 16L,
                valueClass = "vec4<f32>",
            ),
        ),
    ),
    uniformSlot = GPUUniformPayloadSlot(
        slotId = GPUPayloadSlotID("pass-executor:uniform:0"),
        fingerprint = GPUPayloadFingerprint("uniform-fingerprint-executor"),
        byteOffset = 0L,
    ),
    resourceBlock = GPUResourceBindingBlock(
        fingerprint = GPUPayloadFingerprint("resource-fingerprint-executor"),
        bindingPlanHash = "binding:simple_rt:v1",
        bindingCount = 1,
        resourceDescriptorLabels = listOf("uniform:executor-payload"),
        dynamicOffsets = emptyList(),
    ),
    resourceSlot = GPUResourceBindingSlot(
        slotId = GPUPayloadSlotID("pass-executor:resource:0"),
        fingerprint = GPUPayloadFingerprint("resource-fingerprint-executor"),
        bindingIndex = 0,
    ),
    uploadPlan = GPUPayloadUploadPlan(
        planHash = "upload-executor-v1",
        byteRanges = listOf(0L..15L),
        stagingScope = "pass-executor-staging",
        budgetClass = "unit-test",
        beforeUseToken = "before-executor-draw",
    ),
    reflectedBindingLayoutHash = "binding:simple_rt:v1",
    deviceGeneration = 11L,
    payloadGeneration = 7L,
    alignmentBytes = 16L,
    uploadBudgetBytes = 64L,
    uploadCapabilityAvailable = true,
    maxDynamicOffsets = 0,
    requiredUniformUsageLabels = setOf("copy_dst", "uniform"),
    availableUniformUsageLabels = setOf("copy_dst", "uniform"),
)
