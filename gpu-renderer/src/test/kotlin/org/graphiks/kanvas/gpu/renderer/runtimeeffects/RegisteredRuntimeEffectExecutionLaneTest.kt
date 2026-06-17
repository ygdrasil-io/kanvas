package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadUploadPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadField
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.resources.dumpLines
import org.graphiks.kanvas.gpu.renderer.wgsl.REVIEWED_WGSL4K_REFLECTION_SHA
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLUniformFieldLayout
import org.graphiks.kanvas.gpu.renderer.wgsl.Wgsl4kBindingReflection
import org.graphiks.kanvas.gpu.renderer.wgsl.Wgsl4kEntryPointReflection
import org.graphiks.kanvas.gpu.renderer.wgsl.Wgsl4kLayoutMemberReflection
import org.graphiks.kanvas.gpu.renderer.wgsl.Wgsl4kLayoutReflection
import org.graphiks.kanvas.gpu.renderer.wgsl.Wgsl4kReflectionReport
import org.graphiks.kanvas.gpu.renderer.wgsl.Wgsl4kValidationSummary
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslExpectedBinding
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslExpectedEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslExpectedLayout
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslReflectionExpectation
import org.graphiks.kanvas.gpu.renderer.wgsl.consumeWgsl4kReflectionReport

/** Verifies KGPU-M11-008 registered runtime-effect execution materialization. */
class RegisteredRuntimeEffectExecutionLaneTest {
    @Test
    fun `registered runtime effect materializes pipeline payload bind group and command stream`() {
        val result = ValidatingRuntimeEffectExecutionMaterializer().materialize(
            request = executionRequest(),
            context = targetPreparationContext(),
        )

        val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(result.resourceDecision)
        val commandStream = checkNotNull(result.commandStream)
        val renderKey = assertIs<GPUPassCommand.SetRenderPipeline>(
            commandStream.commands.single { command -> command.commandLabel == "setRenderPipeline" },
        ).pipelineKey.value
        val expectedCacheKey = GPURuntimeEffectExecutionKeys.pipelineCacheKey(resultGatePlan()).value
        assertEquals(GPURuntimeEffectExecutionKeys.renderPipelineKey(resultGatePlan()).value, renderKey)
        assertFalse(renderKey == expectedCacheKey)
        assertEquals(
            listOf(
                "setBindGroup" to GPUMaterializedCommandOperandKind.UniformBuffer,
                "setBindGroup" to GPUMaterializedCommandOperandKind.BindGroup,
                "setRenderPipeline" to GPUMaterializedCommandOperandKind.RenderPipeline,
            ),
            materialized.dumpOperandBridgeSnapshot.map { binding -> binding.commandLabel to binding.operand.kind },
        )
        assertEquals(
            listOf("beginRenderPass", "setRenderPipeline", "setBindGroup", "draw", "endRenderPass"),
            commandStream.commandLabels,
        )
        assertEquals(
            listOf(
                "payload-upload:pass-runtime:uniform:0",
                "bind-group:pass-runtime:resource:0",
                "runtime-effect-pipeline:runtime.simple.color@1",
            ),
            commandStream.materializedOperandLabels,
        )

        val lines = result.dumpLines()
        assertContains(
            lines,
                "runtime-effect:execution row=gpu-renderer.runtime-effect.registered.execution " +
                    "descriptor=runtime.simple.color version=1 registryGeneration=17 route=MaterialSource " +
                    "wgsl=sha256:runtime-simple reflection=sha256:reflection-simple-color " +
                    "schema=sha256:schema-simple-color payload=uniform-fingerprint-runtime " +
                    "bindingLayout=sha256:binding-simple-color pipeline=runtime-effect-pipeline:runtime.simple.color@1 " +
                    "renderKey=$renderKey pipelineCache=$expectedCacheKey " +
                    "uniformValuesInKey=false adapterBacked=false productActivation=false",
        )
        assertContains(
            lines,
                "resource.materialization:operand operand=runtime-effect-pipeline:runtime.simple.color@1 kind=render-pipeline " +
                "deviceGeneration=11 owner=runtime-effect-pipeline-cache usage=render " +
                "invalidation=registry-generation descriptor=$expectedCacheKey " +
                "facts=descriptor=runtime.simple.color;entry=fs_main;registryGeneration=17;route=MaterialSource;" +
                "schema=sha256:schema-simple-color;uniformValuesInKey=false;wgsl=sha256:runtime-simple",
        )
        assertContains(
            lines,
            "passes.command-stream id=runtime-effect:runtime.simple.color:stream packetStream=runtime-effect:runtime.simple.color:packets " +
                "pass=pass-runtime commands=beginRenderPass,setRenderPipeline,setBindGroup,draw,endRenderPass " +
                "packets=runtime-effect:runtime.simple.color:draw,runtime-effect:runtime.simple.color:draw,runtime-effect:runtime.simple.color:draw diagnostics=none",
        )
        assertContains(
            lines,
            "runtime-effect:readback row=gpu-renderer.runtime-effect.registered.execution descriptor=runtime.simple.color " +
                "cpuOracle=sha256:4c284b52db68f20a6f9f30998de4d063d99940dc3aa65b6e5126f8c49f49d90d " +
                "gpuReadback=skipped reason=headless-contract-only promoted=false",
        )
        assertFalse(lines.joinToString("\n").contains("WGPU"))
        assertFalse(lines.joinToString("\n").contains("@0x"))
    }

    @Test
    fun `registered runtime effect execution refuses stale or mismatched evidence`() {
        val refusedGate = GPURuntimeEffectDescriptorRoutePlanner().plan(
            routeRequest(effectId = GPURuntimeEffectID("runtime.unknown")),
        )
        val dynamicSourceGate = GPURuntimeEffectDescriptorRoutePlanner().plan(
            routeRequest(dynamicSkSLSourceProvided = true),
        )
        val cases = listOf(
            refusalCase(
                label = "gate-refused",
                request = executionRequest(gatePlan = refusedGate),
                expectedCode = "unsupported.runtime_effect.unregistered_descriptor",
            ),
            refusalCase(
                label = "dynamic-sksl",
                request = executionRequest(gatePlan = dynamicSourceGate),
                expectedCode = "unsupported.runtime_effect.dynamic_sksl_forbidden",
            ),
            refusalCase(
                label = "stale-registry",
                request = executionRequest(expectedRegistryGeneration = 18L),
                expectedCode = "unsupported.runtime_effect.registry_generation_stale",
            ),
            refusalCase(
                label = "descriptor-version",
                request = executionRequest(expectedDescriptorVersion = GPURuntimeEffectDescriptorVersion(2)),
                expectedCode = "unsupported.runtime_effect.descriptor_version_mismatch",
            ),
            refusalCase(
                label = "route-placement",
                request = executionRequest(expectedRoutePlacement = GPURuntimeEffectRoutePlacement.FilterRenderNode),
                expectedCode = "unsupported.runtime_effect.route_placement_mismatch",
            ),
            refusalCase(
                label = "wgsl-module",
                request = executionRequest(expectedWgslModuleHash = "sha256:runtime-other"),
                expectedCode = "unsupported.runtime_effect.wgsl_module_mismatch",
            ),
            refusalCase(
                label = "uniform-schema",
                request = executionRequest(expectedUniformSchemaHash = "sha256:schema-other"),
                expectedCode = "unsupported.runtime_effect.uniform_schema_mismatch",
            ),
            refusalCase(
                label = "uniform-payload-schema",
                request = executionRequest(
                    payloadRequest = runtimePayloadRequest(
                        uniformFields = listOf(
                            GPUUniformPayloadField(
                                fieldPath = "color",
                                byteOffset = 0L,
                                byteSize = 12L,
                                valueClass = "vec4<f32>",
                            ),
                        ),
                    ),
                ),
                expectedCode = "unsupported.runtime_effect.uniform_payload_schema_mismatch",
            ),
            refusalCase(
                label = "pipeline-cache-key",
                request = executionRequest(pipelineCacheKey = "render-pipeline:render:stale"),
                expectedCode = "unsupported.runtime_effect.pipeline_cache_key_mismatch",
            ),
            refusalCase(
                label = "payload-refused",
                request = executionRequest(
                    payloadRequest = runtimePayloadRequest(availableUniformUsageLabels = setOf("copy_dst")),
                ),
                expectedCode = "unsupported.resource.command_operand_usage_missing",
            ),
        )

        cases.forEach { case ->
            val result = ValidatingRuntimeEffectExecutionMaterializer().materialize(
                request = case.request,
                context = targetPreparationContext(),
            )

            val refused = assertIs<GPUResourceMaterializationDecision.Refused>(result.resourceDecision, case.label)
            assertContains(refused.diagnostics.map { diagnostic -> diagnostic.code }, case.expectedCode, case.label)
            assertEquals(emptyList(), result.dumpLines().filter { line -> line.contains("adapterBacked=true") }, case.label)
        }
    }
}

private data class ExecutionRefusalCase(
    val label: String,
    val request: GPURuntimeEffectExecutionRequest,
    val expectedCode: String,
)

private fun refusalCase(
    label: String,
    request: GPURuntimeEffectExecutionRequest,
    expectedCode: String,
): ExecutionRefusalCase = ExecutionRefusalCase(label, request, expectedCode)

private fun executionRequest(
    gatePlan: GPURuntimeEffectDescriptorGatePlan = GPURuntimeEffectDescriptorRoutePlanner().plan(routeRequest()),
    expectedDescriptorId: GPURuntimeEffectID = GPURuntimeEffectID("runtime.simple.color"),
    expectedDescriptorVersion: GPURuntimeEffectDescriptorVersion = GPURuntimeEffectDescriptorVersion(1),
    expectedRegistryGeneration: Long = 17L,
    expectedRoutePlacement: GPURuntimeEffectRoutePlacement = GPURuntimeEffectRoutePlacement.MaterialSource,
    expectedWgslModuleHash: String = "sha256:runtime-simple",
    expectedReflectionHash: String = "sha256:reflection-simple-color",
    expectedUniformSchemaHash: String = "sha256:schema-simple-color",
    pipelineCacheKey: String = runtimeEffectPipelineCacheKeyOrPlaceholder(gatePlan),
    payloadRequest: GPUPayloadMaterializationRequest = runtimePayloadRequest(),
): GPURuntimeEffectExecutionRequest =
    GPURuntimeEffectExecutionRequest(
        label = "runtime-simple-color",
        gatePlan = gatePlan,
        expectedDescriptorId = expectedDescriptorId,
        expectedDescriptorVersion = expectedDescriptorVersion,
        expectedRegistryGeneration = expectedRegistryGeneration,
        expectedRoutePlacement = expectedRoutePlacement,
        expectedWgslModuleHash = expectedWgslModuleHash,
        expectedReflectionHash = expectedReflectionHash,
        expectedUniformSchemaHash = expectedUniformSchemaHash,
        payloadRequest = payloadRequest,
        pipelineCacheKey = pipelineCacheKey,
        targetStateHash = "target-state:rgba8unorm",
        loadStoreLabel = "load-store:clear",
        passId = "pass-runtime",
        packetStreamId = "runtime-effect:runtime.simple.color:packets",
        streamId = "runtime-effect:runtime.simple.color:stream",
        vertexSourceLabel = "fullscreen-triangle",
        cpuOracleEvidenceHash = RUNTIME_ORACLE_EVIDENCE_HASH,
        gpuReadbackStatus = "skipped",
        gpuReadbackReason = "headless-contract-only",
    )

private fun runtimePayloadRequest(
    availableUniformUsageLabels: Set<String> = setOf("copy_dst", "uniform"),
    uniformFields: List<GPUUniformPayloadField> = listOf(
        GPUUniformPayloadField(
            fieldPath = "color",
            byteOffset = 0L,
            byteSize = 16L,
            valueClass = "vec4<f32>",
        ),
    ),
): GPUPayloadMaterializationRequest =
    GPUPayloadMaterializationRequest(
        targetId = "root-target",
        packetId = "runtime-effect:runtime.simple.color:draw",
        taskIds = listOf("task-runtime-effect-execution"),
        resourcePlanLabels = listOf("runtime-effect-execution:runtime.simple.color"),
        uniformBlock = GPUUniformPayloadBlock(
            fingerprint = GPUPayloadFingerprint("uniform-fingerprint-runtime"),
            packingPlanHash = "runtime-simple-color-layout-v1",
            byteSize = 16L,
            zeroedPadding = true,
            scope = "pass-runtime",
            bytes = listOf(0, 0, 128, 63, 0, 0, 0, 63, 0, 0, 0, 0, 0, 0, 128, 63),
            fields = uniformFields,
        ),
        uniformSlot = GPUUniformPayloadSlot(
            slotId = GPUPayloadSlotID("pass-runtime:uniform:0"),
            fingerprint = GPUPayloadFingerprint("uniform-fingerprint-runtime"),
            byteOffset = 0L,
        ),
        resourceBlock = GPUResourceBindingBlock(
            fingerprint = GPUPayloadFingerprint("resource-fingerprint-runtime"),
            bindingPlanHash = "sha256:binding-simple-color",
            bindingCount = 1,
            resourceDescriptorLabels = listOf("uniform:runtime-simple-color-payload"),
            dynamicOffsets = emptyList(),
        ),
        resourceSlot = GPUResourceBindingSlot(
            slotId = GPUPayloadSlotID("pass-runtime:resource:0"),
            fingerprint = GPUPayloadFingerprint("resource-fingerprint-runtime"),
            bindingIndex = 0,
        ),
        uploadPlan = GPUPayloadUploadPlan(
            planHash = "upload-runtime-simple-color-v1",
            byteRanges = listOf(0L..15L),
            stagingScope = "pass-runtime-staging",
            budgetClass = "unit-test",
            beforeUseToken = "before-runtime-effect-draw",
        ),
        reflectedBindingLayoutHash = "sha256:binding-simple-color",
        deviceGeneration = 11L,
        payloadGeneration = 7L,
        alignmentBytes = 16L,
        uploadBudgetBytes = 64L,
        uploadCapabilityAvailable = true,
        maxDynamicOffsets = 0,
        requiredUniformUsageLabels = setOf("copy_dst", "uniform"),
        availableUniformUsageLabels = availableUniformUsageLabels,
    )

private fun targetPreparationContext(): GPUTargetPreparationContext =
    GPUTargetPreparationContext(
        targetId = "root-target",
        frameId = "frame-1",
        deviceGeneration = 11L,
        budgetClass = "unit-test",
    )

private fun routeRequest(
    label: String = "accepted",
    effectId: GPURuntimeEffectID = GPURuntimeEffectID("runtime.simple.color"),
    requestedPlacement: GPURuntimeEffectRoutePlacement = GPURuntimeEffectRoutePlacement.MaterialSource,
    registrySnapshot: GPURuntimeEffectRegistrySnapshot = registrySnapshot(),
    dynamicSkSLSourceProvided: Boolean = false,
    wgslEvidence: GPURuntimeEffectWGSLEvidence = acceptedRuntimeWgslEvidence(),
    cpuOracle: GPURuntimeEffectOracleResult? = runtimeOracle(),
): GPURuntimeEffectDescriptorRouteRequest =
    GPURuntimeEffectDescriptorRouteRequest(
        label = label,
        effectId = effectId,
        requestedPlacement = requestedPlacement,
        registrySnapshot = registrySnapshot,
        wgslEvidence = wgslEvidence,
        cpuOracle = cpuOracle,
        dynamicSkSLSourceProvided = dynamicSkSLSourceProvided,
    )

private fun registrySnapshot(
    descriptors: List<GPURuntimeEffectDescriptor> = listOf(runtimeDescriptor()),
): GPURuntimeEffectRegistrySnapshot =
    GPURuntimeEffectRegistrySnapshot(
        registryVersion = "runtime-registry-v1",
        generation = 17,
        descriptors = descriptors,
        provenance = "test-fixture",
    )

private fun runtimeDescriptor(
    version: GPURuntimeEffectDescriptorVersion = GPURuntimeEffectDescriptorVersion(1),
): GPURuntimeEffectDescriptor =
    GPURuntimeEffectDescriptor(
        id = GPURuntimeEffectID("runtime.simple.color"),
        version = version,
        uniformSchema = GPURuntimeEffectUniformSchema(
            schemaHash = "sha256:schema-simple-color",
            fields = listOf("color:vec4<f32>@0:16"),
            packingPolicy = "std140",
        ),
        uniformBlockPlan = GPURuntimeEffectUniformBlockPlan(
            schema = GPURuntimeEffectUniformSchema(
                schemaHash = "sha256:schema-simple-color",
                fields = listOf("color:vec4<f32>@0:16"),
                packingPolicy = "std140",
            ),
            blockSizeBytes = 16,
            dynamicOffsets = false,
        ),
        childSlots = emptyList(),
        resources = GPURuntimeEffectResourcePlan(
            resourceLabels = listOf("group1.binding0.uniformBuffer"),
            bindingPlanHash = "sha256:binding-simple-color",
        ),
        wgslPlan = GPURuntimeEffectWGSLPlan(
            moduleHash = "sha256:runtime-simple",
            entryPoint = "fs_main",
            reflectionHash = "sha256:reflection-simple-color",
        ),
        routeContract = GPURuntimeEffectRouteContract(
            nativeSupported = true,
            cpuOracleOnly = false,
            acceptedPlacements = setOf(GPURuntimeEffectRoutePlacement.MaterialSource),
        ),
        liveEditPlan = GPURuntimeEffectLiveEditPlan(
            enabled = false,
            descriptorVersion = version,
            validationPolicy = "static",
        ),
    )

private const val RUNTIME_ORACLE_EVIDENCE_HASH =
    "sha256:4c284b52db68f20a6f9f30998de4d063d99940dc3aa65b6e5126f8c49f49d90d"

private fun resultGatePlan(): GPURuntimeEffectDescriptorGatePlan =
    GPURuntimeEffectDescriptorRoutePlanner().plan(routeRequest())

private fun runtimeEffectPipelineCacheKeyOrPlaceholder(gatePlan: GPURuntimeEffectDescriptorGatePlan): String =
    if (gatePlan.routePlan is GPURuntimeEffectRoutePlan.Accepted) {
        GPURuntimeEffectExecutionKeys.pipelineCacheKey(gatePlan).value
    } else {
        "render-pipeline:render:refused"
    }

private fun runtimeOracle(evidenceHash: String = RUNTIME_ORACLE_EVIDENCE_HASH): GPURuntimeEffectOracleResult =
    GPURuntimeEffectOracleResult(
        effectId = GPURuntimeEffectID("runtime.simple.color"),
        evidenceHash = evidenceHash,
    )

private fun acceptedRuntimeWgslEvidence(): GPURuntimeEffectWGSLEvidence =
    GPURuntimeEffectWGSLEvidence(
        report = consumeWgsl4kReflectionReport(
            report = Wgsl4kReflectionReport(
                sourceId = "runtime/runtime_simple_rt.wgsl",
                moduleHash = "sha256:runtime-simple",
                wgsl4kSha = REVIEWED_WGSL4K_REFLECTION_SHA,
                validation = Wgsl4kValidationSummary(success = true),
                entryPoints = listOf(Wgsl4kEntryPointReflection("fs_main", "fragment")),
                bindings = listOf(
                    Wgsl4kBindingReflection(1, 0, "runtimeUniforms", "uniformBuffer", minBindingSize = 16),
                ),
                layouts = listOf(
                    Wgsl4kLayoutReflection(
                        structName = "RuntimeSimpleUniforms",
                        addressSpace = "uniform",
                        size = 16,
                        alignment = 16,
                        members = listOf(
                            Wgsl4kLayoutMemberReflection(
                                "color",
                                "vec4<f32>",
                                offset = 0,
                                size = 16,
                                alignment = 16,
                                stride = null,
                            ),
                        ),
                    ),
                ),
            ),
            expectation = WgslReflectionExpectation(
                reportKind = "runtime-effect",
                moduleId = "runtime.simple.color",
                allowedSourceIds = setOf("runtime/runtime_simple_rt.wgsl"),
                expectedEntryPoints = listOf(WgslExpectedEntryPoint("fs_main", "fragment")),
                expectedBindings = listOf(WgslExpectedBinding(1, 0, "runtimeUniforms", "uniformBuffer", minBindingSize = 16)),
                expectedLayouts = listOf(
                    WgslExpectedLayout(
                        structName = "RuntimeSimpleUniforms",
                        addressSpace = "uniform",
                        size = 16,
                        alignment = 16,
                        members = listOf(
                            WGSLUniformFieldLayout("color", "vec4<f32>", offset = 0L, sizeBytes = 16L, alignment = 16),
                        ),
                    ),
                ),
                descriptorId = "runtime.simple.color",
                descriptorVersion = 1,
                routePromotion = "not-promoted",
                productActivation = false,
            ),
        ),
    )
