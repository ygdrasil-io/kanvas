package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.gpu.renderer.materials.CanonicalIdentityEncoder
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadUploadPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadField
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTBindingPlanHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTDescriptorVersion
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTEffectId
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTModuleHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTReflectionHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTUniformBlockSizeBytes
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTUniformSchemaHash

/** Registered descriptor for the simple_rt runtime effect (solid color). */
object SimpleRTDescriptor {
    val effectId: GPURuntimeEffectID = GPURuntimeEffectID(SimpleRTEffectId)
    val descriptorVersion: GPURuntimeEffectDescriptorVersion =
        GPURuntimeEffectDescriptorVersion(SimpleRTDescriptorVersion)

    val uniformSchema: GPURuntimeEffectUniformSchema = GPURuntimeEffectUniformSchema(
        schemaHash = SimpleRTUniformSchemaHash,
        fields = listOf("gColor:vec4<f32>@0:16"),
        packingPolicy = "std140",
    )

    val uniformBlockPlan: GPURuntimeEffectUniformBlockPlan = GPURuntimeEffectUniformBlockPlan(
        schema = uniformSchema,
        blockSizeBytes = SimpleRTUniformBlockSizeBytes.toLong(),
        dynamicOffsets = false,
    )

    val resources: GPURuntimeEffectResourcePlan = GPURuntimeEffectResourcePlan(
        resourceLabels = listOf("group1.binding0.uniformBuffer"),
        bindingPlanHash = SimpleRTBindingPlanHash,
    )

    val wgslPlan: GPURuntimeEffectWGSLPlan = GPURuntimeEffectWGSLPlan(
        moduleHash = SimpleRTModuleHash,
        entryPoint = SimpleRTEntryPoint,
        reflectionHash = SimpleRTReflectionHash,
    )

    val routeContract: GPURuntimeEffectRouteContract = GPURuntimeEffectRouteContract(
        nativeSupported = true,
        cpuOracleOnly = false,
        acceptedPlacements = setOf(GPURuntimeEffectRoutePlacement.MaterialSource),
    )

    val liveEditPlan: GPURuntimeEffectLiveEditPlan = GPURuntimeEffectLiveEditPlan(
        enabled = false,
        descriptorVersion = descriptorVersion,
        validationPolicy = "static",
    )

    val childSlots: List<GPURuntimeEffectChildSlotPlan> = emptyList()

    /** Builds a GPURuntimeEffectDescriptor from the plan properties. */
    fun createDescriptor(): GPURuntimeEffectDescriptor = GPURuntimeEffectDescriptor(
        id = effectId,
        version = descriptorVersion,
        uniformSchema = uniformSchema,
        uniformBlockPlan = uniformBlockPlan,
        childSlots = childSlots,
        resources = resources,
        wgslPlan = wgslPlan,
        routeContract = routeContract,
        liveEditPlan = liveEditPlan,
    )
    /** Builds a minimal [GPURuntimeEffectExecutionRequest] for testing dispatch paths. */
    fun createExecutionRequest(): GPURuntimeEffectExecutionRequest {
        val gatePlan = GPURuntimeEffectDescriptorGatePlan(
            label = "simple_rt-test",
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
                        message = "placeholder gate plan for dispatch test",
                        terminal = false,
                    ),
                ),
                diagnostic = GPURuntimeEffectDiagnostic(
                    code = "placeholder",
                    effectId = effectId,
                    message = "placeholder gate plan for dispatch test",
                    terminal = false,
                ),
            ),
            registrySnapshot = GPURuntimeEffectRegistrySnapshot(
                registryVersion = "runtime-registry-v1",
                generation = 1L,
                descriptors = emptyList(),
                provenance = "dispatch-test-fixture",
            ),
            payloadPlanHash = "payload:simple_rt:test",
            materialKeyBoundaryHash = "material-key:simple_rt:test",
            materialKeyIncludesUniformValues = false,
            diagnostics = emptyList(),
        )
        return GPURuntimeEffectExecutionRequest(
            label = "simple_rt-test",
            gatePlan = gatePlan,
            expectedDescriptorId = effectId,
            expectedDescriptorVersion = descriptorVersion,
            expectedRegistryGeneration = 1L,
            expectedRoutePlacement = GPURuntimeEffectRoutePlacement.MaterialSource,
            expectedWgslModuleHash = wgslPlan.moduleHash,
            expectedReflectionHash = wgslPlan.reflectionHash,
            expectedUniformSchemaHash = uniformSchema.schemaHash,
            payloadRequest = GPUPayloadMaterializationRequest(
                targetId = "root-target",
                packetId = "runtime-effect:simple_rt:draw",
                taskIds = listOf("task-test"),
                resourcePlanLabels = listOf("runtime-effect-execution:simple_rt"),
                uniformBlock = GPUUniformPayloadBlock(
                    fingerprint = GPUPayloadFingerprint("uniform-simple_rt-test"),
                    packingPlanHash = "simple_rt-layout-v1",
                    byteSize = 16L,
                    zeroedPadding = true,
                    scope = "pass-test",
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
                    slotId = GPUPayloadSlotID("pass-test:uniform:0"),
                    fingerprint = GPUPayloadFingerprint("uniform-simple_rt-test"),
                    byteOffset = 0L,
                ),
                resourceBlock = GPUResourceBindingBlock(
                    fingerprint = GPUPayloadFingerprint("resource-simple_rt-test"),
                    bindingPlanHash = resources.bindingPlanHash,
                    bindingCount = 1,
                    resourceDescriptorLabels = listOf("uniform:simple_rt-payload"),
                    dynamicOffsets = emptyList(),
                ),
                resourceSlot = GPUResourceBindingSlot(
                    slotId = GPUPayloadSlotID("pass-test:resource:0"),
                    fingerprint = GPUPayloadFingerprint("resource-simple_rt-test"),
                    bindingIndex = 0,
                ),
                uploadPlan = GPUPayloadUploadPlan(
                    planHash = "upload-test-v1",
                    byteRanges = listOf(0L..15L),
                    stagingScope = "pass-test-staging",
                    budgetClass = "unit-test",
                    beforeUseToken = "before-draw",
                ),
                reflectedBindingLayoutHash = resources.bindingPlanHash,
                deviceGeneration = 11L,
                payloadGeneration = 7L,
                alignmentBytes = 16L,
                uploadBudgetBytes = 64L,
                uploadCapabilityAvailable = true,
                maxDynamicOffsets = 0,
                requiredUniformUsageLabels = setOf("copy_dst", "uniform"),
                availableUniformUsageLabels = setOf("copy_dst", "uniform"),
            ),
            pipelineCacheKey = "render-pipeline:render:dispatch-test",
            targetStateHash = "target-state:rgba8unorm",
            loadStoreLabel = "load-store:clear",
            passId = "pass-dispatch",
            packetStreamId = "runtime-effect:dispatch-test:packets",
            streamId = "runtime-effect:dispatch-test:stream",
            vertexSourceLabel = "fullscreen-triangle",
            cpuOracleEvidenceHash = "sha256:0000000000000000000000000000000000000000000000000000000000000000",
            gpuReadbackStatus = "skipped",
            gpuReadbackReason = "headless-contract-only",
        )
    }
}

/**
 * CPU oracle for the simple_rt runtime effect.
 * Produces validation-only evidence; not a product fallback path.
 */
object SimpleRTCPUOracle : GPURuntimeEffectCPUOracle {
    override fun evaluate(): GPURuntimeEffectOracleResult =
        GPURuntimeEffectOracleResult(
            effectId = SimpleRTDescriptor.effectId,
            evidenceHash = runtimeEffectOracleEvidenceHash(SimpleRTDescriptor.effectId, SimpleRTDescriptor.descriptorVersion),
        )

    override fun evaluateMaterial(
        input: GPURuntimeEffectMaterialEvaluationInput,
    ): GPURuntimeEffectMaterialEvaluationResult {
        val uniformBytes = input.uniformBytes
        if (uniformBytes.size != SimpleRTUniformBlockSizeBytes) {
            return GPURuntimeEffectMaterialEvaluationResult.Unsupported(
                GPURuntimeEffectMaterialEvaluationRefusal.PAYLOAD_SIZE,
            )
        }
        val values = ByteBuffer.wrap(uniformBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .let { buffer -> List(4) { buffer.float } }
        if (
            values.any { value -> !value.isFinite() } ||
            !input.localPositionX.isFinite() ||
            !input.localPositionY.isFinite()
        ) {
            return GPURuntimeEffectMaterialEvaluationResult.Unsupported(
                GPURuntimeEffectMaterialEvaluationRefusal.NON_FINITE_INPUT,
            )
        }
        return GPURuntimeEffectMaterialEvaluationResult.Color(
            r = values[0],
            g = values[1],
            b = values[2],
            a = values[3],
            evidenceHash = materialEvaluationEvidenceHash(input, values),
        )
    }
}

private fun materialEvaluationEvidenceHash(
    input: GPURuntimeEffectMaterialEvaluationInput,
    output: List<Float>,
): String {
    val bytes = ByteBuffer.allocate(8 + input.uniformBytes.size + output.size * Float.SIZE_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putFloat(input.localPositionX)
        .putFloat(input.localPositionY)
        .put(input.uniformBytes)
        .apply { output.forEach(::putFloat) }
        .array()
    return CanonicalIdentityEncoder("runtime-effect-material-evaluation-v2")
        .bytes("inputAndOutput", bytes)
        .digestIdentity()
}

private fun runtimeEffectOracleEvidenceHash(
    id: GPURuntimeEffectID,
    version: GPURuntimeEffectDescriptorVersion,
): String {
    return CanonicalIdentityEncoder("runtime-effect-cpu-oracle-v2")
        .text("effectId", id.value)
        .int("descriptorVersion", version.value)
        .digestIdentity()
}
