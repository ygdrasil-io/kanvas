package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceDescriptor
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream
import org.graphiks.kanvas.gpu.renderer.passes.dumpLines
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineKeyPreimage
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineKeys
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandBinding
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandReference
import org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.resources.ValidatingPayloadResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.dumpLines
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslConsumedReflectionReport

/** Runtime-effect identifier. */
@JvmInline
value class GPURuntimeEffectID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPURuntimeEffectID.value must not be blank" }
    }
}

/** Runtime-effect descriptor version. */
@JvmInline
value class GPURuntimeEffectDescriptorVersion(val value: Int) {
    init {
        require(value >= 0) { "GPURuntimeEffectDescriptorVersion.value must be non-negative" }
    }
}

/** Accepted runtime-effect placement. */
enum class GPURuntimeEffectRoutePlacement {
    /** Runtime effect produces material source color. */
    MaterialSource,
    /** Runtime effect is folded as a material color filter. */
    MaterialColorFilter,
    /** Runtime effect is used as a shader-side blender. */
    MaterialBlender,
    /** Runtime effect is used as a render filter node. */
    FilterRenderNode,
    /** Runtime effect is used as a compute filter node. */
    FilterComputeNode,
    /** Runtime effect combines primitive color with material output. */
    PrimitiveBlender,
    /** Runtime effect is used as a future clip shader. */
    ClipShader,
    /** Runtime effect is only available as CPU reference evidence. */
    CPUReferenceOnly,
}

/** Immutable runtime-effect registry snapshot pinned by recording/evidence. */
data class GPURuntimeEffectRegistrySnapshot(
    val registryVersion: String,
    val generation: Long,
    val descriptors: List<GPURuntimeEffectDescriptor>,
    val provenance: String,
) {
    /** Returns all descriptors matching an ID; more than one is a registry collision. */
    fun lookupAll(id: GPURuntimeEffectID): List<GPURuntimeEffectDescriptor> =
        descriptors.filter { descriptor -> descriptor.id == id }

    /** Looks up a descriptor in this immutable snapshot. */
    fun lookup(id: GPURuntimeEffectID): GPURuntimeEffectDescriptor? =
        lookupAll(id).singleOrNull()

    /** Deterministic descriptor summary for dump evidence. */
    val descriptorSummary: String
        get() = if (descriptors.isEmpty()) {
            "none"
        } else {
            descriptors.sortedWith(compareBy({ it.id.value }, { it.version.value }))
                .joinToString(",") { descriptor -> "${descriptor.id.value}@${descriptor.version.value}" }
        }
}

/** Registry for product-supported runtime effects. */
interface GPURuntimeEffectRegistry {
    /** Looks up a registered descriptor by ID. */
    fun lookup(id: GPURuntimeEffectID): GPURuntimeEffectDescriptor? = TODO("Wire GPURuntimeEffectRegistry to registered Kotlin/WGSL descriptors")
}

/** Runtime-effect uniform schema. */
data class GPURuntimeEffectUniformSchema(
    val schemaHash: String,
    val fields: List<String>,
    val packingPolicy: String,
)

/** Runtime-effect uniform block plan. */
data class GPURuntimeEffectUniformBlockPlan(
    val schema: GPURuntimeEffectUniformSchema,
    val blockSizeBytes: Long,
    val dynamicOffsets: Boolean,
)

/** Runtime-effect child slot plan. */
data class GPURuntimeEffectChildSlotPlan(
    val slotName: String,
    val acceptedSourceKinds: Set<String>,
    val required: Boolean,
)

/** Runtime-effect resource plan. */
data class GPURuntimeEffectResourcePlan(
    val resourceLabels: List<String>,
    val bindingPlanHash: String,
)

/** Runtime-effect WGSL plan. */
data class GPURuntimeEffectWGSLPlan(
    val moduleHash: String,
    val entryPoint: String,
    val reflectionHash: String,
)

/** Consumed wgsl4k evidence attached to a runtime-effect descriptor route. */
data class GPURuntimeEffectWGSLEvidence(
    val report: WgslConsumedReflectionReport,
)

/** CPU oracle contract for reference validation only. */
interface GPURuntimeEffectCPUOracle {
    /** Evaluates reference output for evidence, not product fallback. */
    fun evaluate(): GPURuntimeEffectOracleResult = TODO("Wire GPURuntimeEffectCPUOracle to explicit validation-only evidence")
}

/** CPU oracle result used only for validation evidence. */
data class GPURuntimeEffectOracleResult(
    val effectId: GPURuntimeEffectID,
    val evidenceHash: String,
    val diagnostics: List<GPURuntimeEffectDiagnostic> = emptyList(),
)

/** Runtime-effect route contract. */
data class GPURuntimeEffectRouteContract(
    val nativeSupported: Boolean,
    val cpuOracleOnly: Boolean,
    val refusalCode: String? = null,
    val acceptedPlacements: Set<GPURuntimeEffectRoutePlacement> = emptySet(),
)

/** Runtime-effect lookup result pinned to a registry snapshot. */
data class GPURuntimeEffectLookupPlan(
    val inputKind: String,
    val registryVersion: String,
    val registryGeneration: Long,
    val requestedEffectId: GPURuntimeEffectID,
    val descriptorId: GPURuntimeEffectID?,
    val descriptorVersion: GPURuntimeEffectDescriptorVersion?,
    val requestedPlacement: GPURuntimeEffectRoutePlacement,
    val routePlacement: GPURuntimeEffectRoutePlacement?,
    val diagnostic: GPURuntimeEffectDiagnostic?,
)

/** Request for the registered runtime-effect descriptor route gate. */
data class GPURuntimeEffectDescriptorRouteRequest(
    val label: String = "accepted",
    val effectId: GPURuntimeEffectID,
    val requestedPlacement: GPURuntimeEffectRoutePlacement,
    val registrySnapshot: GPURuntimeEffectRegistrySnapshot,
    val wgslEvidence: GPURuntimeEffectWGSLEvidence?,
    val cpuOracle: GPURuntimeEffectOracleResult?,
    val dynamicSkSLSourceProvided: Boolean = false,
)

/** Registered runtime-effect route plan. */
sealed interface GPURuntimeEffectRoutePlan {
    /** Accepted descriptor-backed material route. */
    data class Accepted(
        val lookupPlan: GPURuntimeEffectLookupPlan,
        val descriptor: GPURuntimeEffectDescriptor,
        val wgslEvidence: GPURuntimeEffectWGSLEvidence,
        val cpuOracle: GPURuntimeEffectOracleResult,
        val materialSource: GPUMaterialSourceDescriptor.RuntimeEffect,
    ) : GPURuntimeEffectRoutePlan

    /** Refused descriptor route. */
    data class Refused(val lookupPlan: GPURuntimeEffectLookupPlan, val diagnostic: GPURuntimeEffectDiagnostic) :
        GPURuntimeEffectRoutePlan
}

/** Evidence result for the registered runtime-effect route gate. */
data class GPURuntimeEffectDescriptorGatePlan(
    val label: String,
    val evidenceRow: String,
    val routeKind: String,
    val classification: String,
    val promoted: Boolean,
    val productActivation: Boolean,
    val materialized: Boolean,
    val routePlan: GPURuntimeEffectRoutePlan,
    val registrySnapshot: GPURuntimeEffectRegistrySnapshot,
    val payloadPlanHash: String,
    val materialKeyBoundaryHash: String,
    val materialKeyIncludesUniformValues: Boolean,
    val diagnostics: List<GPURuntimeEffectDiagnostic>,
) {
    /** Returns canonical PM/review dump lines for this gate. */
    fun dumpLines(): List<String> {
        val terminalDiagnostic = diagnostics.singleOrNull { it.terminal }
        if (terminalDiagnostic != null) {
            val lookup = (routePlan as GPURuntimeEffectRoutePlan.Refused).lookupPlan
            return listOf(
                "runtime-effect:registered.refused row=$evidenceRow routeKind=$routeKind " +
                    "classification=$classification promoted=$promoted productActivation=$productActivation " +
                    "materialized=$materialized descriptor=${lookup.requestedEffectId.value} " +
                    "reason=${terminalDiagnostic.code} label=$label",
                RUNTIME_EFFECT_NONCLAIM_LINE,
            )
        }

        val accepted = routePlan as GPURuntimeEffectRoutePlan.Accepted
        val descriptor = accepted.descriptor
        val wgslReport = accepted.wgslEvidence.report
        return listOf(
            "runtime-effect:registered row=$evidenceRow routeKind=$routeKind classification=$classification " +
                "promoted=$promoted productActivation=$productActivation materialized=$materialized " +
                "descriptor=${descriptor.id.value} version=${descriptor.version.value} " +
                "requestKind=${accepted.lookupPlan.requestedPlacement} route=${accepted.lookupPlan.routePlacement}",
            "runtime-effect:registry version=${registrySnapshot.registryVersion} generation=${registrySnapshot.generation} " +
                "descriptors=${registrySnapshot.descriptorSummary} provenance=${registrySnapshot.provenance}",
            "runtime-effect:uniform schema=${descriptor.uniformSchema.schemaHash} " +
                "fields=${descriptor.uniformSchema.fields.joinToString(",")} " +
                "packing=${descriptor.uniformSchema.packingPolicy} " +
                "blockBytes=${descriptor.uniformBlockPlan.blockSizeBytes} " +
                "dynamicOffsets=${descriptor.uniformBlockPlan.dynamicOffsets}",
            "runtime-effect:wgsl module=${wgslReport.moduleId} source=${wgslReport.sourceId} " +
                "hash=${descriptor.wgslPlan.moduleHash} entry=${descriptor.wgslPlan.entryPoint} " +
                "wgsl4k=${wgslReport.wgsl4kSha} comparison=${wgslReport.comparison.status} " +
                "reflection=${descriptor.wgslPlan.reflectionHash}",
            "runtime-effect:oracle id=${accepted.cpuOracle.effectId.value} evidence=${accepted.cpuOracle.evidenceHash} " +
                "diagnostics=${accepted.cpuOracle.diagnostics.dumpRuntimeEffectDiagnostics()} fallback=false",
            "runtime-effect:material snippet=${descriptor.id.value}@${descriptor.version.value} " +
                "payload=$payloadPlanHash materialKey=$materialKeyBoundaryHash " +
                "uniformValuesInKey=$materialKeyIncludesUniformValues route=registered-descriptor",
            "runtime-effect:diagnostic code=${diagnostics.single().code} terminal=${diagnostics.single().terminal}",
            RUNTIME_EFFECT_NONCLAIM_LINE,
        )
    }
}

/** Request for executing one registered descriptor-backed runtime-effect material lane. */
data class GPURuntimeEffectExecutionRequest(
    val label: String,
    val gatePlan: GPURuntimeEffectDescriptorGatePlan,
    val expectedDescriptorId: GPURuntimeEffectID,
    val expectedDescriptorVersion: GPURuntimeEffectDescriptorVersion,
    val expectedRegistryGeneration: Long,
    val expectedRoutePlacement: GPURuntimeEffectRoutePlacement,
    val expectedWgslModuleHash: String,
    val expectedReflectionHash: String,
    val expectedUniformSchemaHash: String,
    val payloadRequest: GPUPayloadMaterializationRequest,
    val pipelineCacheKey: String,
    val targetStateHash: String,
    val loadStoreLabel: String,
    val passId: String,
    val packetStreamId: String,
    val streamId: String,
    val vertexSourceLabel: String,
    val cpuOracleEvidenceHash: String,
    val gpuReadbackStatus: String,
    val gpuReadbackReason: String,
) {
    init {
        require(label.isNotBlank()) { "GPURuntimeEffectExecutionRequest.label must not be blank" }
        require(expectedRegistryGeneration >= 0L) {
            "GPURuntimeEffectExecutionRequest.expectedRegistryGeneration must be non-negative"
        }
        require(expectedWgslModuleHash.isNotBlank()) {
            "GPURuntimeEffectExecutionRequest.expectedWgslModuleHash must not be blank"
        }
        require(expectedReflectionHash.isNotBlank()) {
            "GPURuntimeEffectExecutionRequest.expectedReflectionHash must not be blank"
        }
        require(expectedUniformSchemaHash.isNotBlank()) {
            "GPURuntimeEffectExecutionRequest.expectedUniformSchemaHash must not be blank"
        }
        require(pipelineCacheKey.isNotBlank()) {
            "GPURuntimeEffectExecutionRequest.pipelineCacheKey must not be blank"
        }
        require(targetStateHash.isNotBlank()) {
            "GPURuntimeEffectExecutionRequest.targetStateHash must not be blank"
        }
        require(loadStoreLabel.isNotBlank()) { "GPURuntimeEffectExecutionRequest.loadStoreLabel must not be blank" }
        require(passId.isNotBlank()) { "GPURuntimeEffectExecutionRequest.passId must not be blank" }
        require(packetStreamId.isNotBlank()) {
            "GPURuntimeEffectExecutionRequest.packetStreamId must not be blank"
        }
        require(streamId.isNotBlank()) { "GPURuntimeEffectExecutionRequest.streamId must not be blank" }
        require(vertexSourceLabel.isNotBlank()) {
            "GPURuntimeEffectExecutionRequest.vertexSourceLabel must not be blank"
        }
        require(cpuOracleEvidenceHash.isNotBlank()) {
            "GPURuntimeEffectExecutionRequest.cpuOracleEvidenceHash must not be blank"
        }
        require(gpuReadbackStatus.isNotBlank()) {
            "GPURuntimeEffectExecutionRequest.gpuReadbackStatus must not be blank"
        }
        require(gpuReadbackReason.isNotBlank()) {
            "GPURuntimeEffectExecutionRequest.gpuReadbackReason must not be blank"
        }
    }
}

/** Result of contract-only registered runtime-effect execution materialization. */
data class GPURuntimeEffectExecutionResult(
    val evidenceRow: String,
    val descriptorId: GPURuntimeEffectID,
    val descriptorVersion: GPURuntimeEffectDescriptorVersion?,
    val registryGeneration: Long,
    val routePlacement: GPURuntimeEffectRoutePlacement?,
    val pipelineLabel: String?,
    val renderPipelineKey: String?,
    val pipelineCacheKey: String?,
    val payloadFingerprint: String?,
    val bindingLayoutHash: String?,
    val wgslModuleHash: String?,
    val reflectionHash: String?,
    val uniformSchemaHash: String?,
    val cpuOracleEvidenceHash: String?,
    val gpuReadbackStatus: String,
    val gpuReadbackReason: String,
    val resourceDecision: GPUResourceMaterializationDecision,
    val commandStream: GPUPassCommandStream?,
    val uniformValuesInKey: Boolean,
    val adapterBacked: Boolean = false,
    val productActivation: Boolean = true,
) {
    /** Returns canonical PM/review dump lines for this execution lane. */
    fun dumpLines(): List<String> {
        val executionLine = if (resourceDecision is GPUResourceMaterializationDecision.Materialized) {
            "runtime-effect:execution row=$evidenceRow " +
                "descriptor=${descriptorId.value} version=${descriptorVersion?.value ?: NONE_RUNTIME_EFFECT_VALUE} " +
                "registryGeneration=$registryGeneration route=${routePlacement ?: NONE_RUNTIME_EFFECT_VALUE} " +
                "wgsl=${wgslModuleHash ?: NONE_RUNTIME_EFFECT_VALUE} " +
                "reflection=${reflectionHash ?: NONE_RUNTIME_EFFECT_VALUE} " +
                "schema=${uniformSchemaHash ?: NONE_RUNTIME_EFFECT_VALUE} " +
                "payload=${payloadFingerprint ?: NONE_RUNTIME_EFFECT_VALUE} " +
                "bindingLayout=${bindingLayoutHash ?: NONE_RUNTIME_EFFECT_VALUE} " +
                "pipeline=${pipelineLabel ?: NONE_RUNTIME_EFFECT_VALUE} " +
                "renderKey=${renderPipelineKey ?: NONE_RUNTIME_EFFECT_VALUE} " +
                "pipelineCache=${pipelineCacheKey ?: NONE_RUNTIME_EFFECT_VALUE} " +
                "uniformValuesInKey=$uniformValuesInKey " +
                "adapterBacked=$adapterBacked productActivation=$productActivation"
        } else {
            val diagnosticCode = when (resourceDecision) {
                is GPUResourceMaterializationDecision.Refused -> resourceDecision.diagnostic.code
                is GPUResourceMaterializationDecision.Deferred -> resourceDecision.reasonCode
                is GPUResourceMaterializationDecision.Materialized -> RUNTIME_EFFECT_ACCEPTED_CODE
            }
            "runtime-effect:execution.refused row=$evidenceRow " +
                "descriptor=${descriptorId.value} version=${descriptorVersion?.value ?: NONE_RUNTIME_EFFECT_VALUE} " +
                "registryGeneration=$registryGeneration route=${routePlacement ?: NONE_RUNTIME_EFFECT_VALUE} " +
                "reason=$diagnosticCode adapterBacked=$adapterBacked productActivation=$productActivation"
        }
        val readbackLine =
            "runtime-effect:readback row=$evidenceRow descriptor=${descriptorId.value} " +
                "cpuOracle=${cpuOracleEvidenceHash ?: NONE_RUNTIME_EFFECT_VALUE} " +
                "gpuReadback=$gpuReadbackStatus reason=$gpuReadbackReason promoted=false"

        return listOf(executionLine, readbackLine) +
            resourceDecision.dumpLines() +
            commandStream?.dumpLines().orEmpty() +
            listOf(RUNTIME_EFFECT_EXECUTION_NONCLAIM_LINE)
    }
}

/** Deterministic pipeline key derivation for registered runtime-effect execution. */
object GPURuntimeEffectExecutionKeys {
    /** Builds the render pipeline preimage from accepted descriptor-gate evidence. */
    fun renderPipelinePreimage(gatePlan: GPURuntimeEffectDescriptorGatePlan): GPUPipelineKeyPreimage.Render {
        val accepted = gatePlan.routePlan as GPURuntimeEffectRoutePlan.Accepted
        val descriptor = accepted.descriptor
        return GPUPipelineKeyPreimage.Render(
            renderStepIdentity = "runtime-effect:${descriptor.id.value}",
            renderStepVersion = descriptor.version.value.toString(),
            primitiveTopology = "triangle-list",
            materialKeyHash = gatePlan.materialKeyBoundaryHash,
            materialProgramId = descriptor.id.value,
            materialDictionaryVersion = gatePlan.registrySnapshot.registryVersion,
            materialLayoutHash = descriptor.uniformSchema.schemaHash,
            snippetIdentityHash = runtimeEffectRouteContractHash(descriptor),
            moduleHash = descriptor.wgslPlan.moduleHash,
            vertexLayoutHash = "runtime-effect-fullscreen-triangle-v1",
            targetFormatClass = "rgba8unorm",
            blendStateHash = "runtime-effect-source-over-v1",
            sampleStateHash = "sample-count-1",
            bindGroupLayoutHash = descriptor.resources.bindingPlanHash,
            capabilityClass = "registered-runtime-effect",
            capabilityFacts = listOf(
                "descriptor=${descriptor.id.value}@${descriptor.version.value}",
                "registry=${gatePlan.registrySnapshot.registryVersion}@${gatePlan.registrySnapshot.generation}",
                "reflection=${descriptor.wgslPlan.reflectionHash}",
                "route=${accepted.lookupPlan.routePlacement}",
                "schema=${descriptor.uniformSchema.schemaHash}",
                "uniformValuesInKey=false",
            ),
            rendererSalt = "kanvas-runtime-effect-execution-v1",
        )
    }

    /** Derives the render key used by `setRenderPipeline`. */
    fun renderPipelineKey(gatePlan: GPURuntimeEffectDescriptorGatePlan) =
        GPUPipelineKeys.renderPipelineKey(renderPipelinePreimage(gatePlan))

    /** Derives the cache key used for render pipeline cache evidence. */
    fun pipelineCacheKey(gatePlan: GPURuntimeEffectDescriptorGatePlan) =
        GPUPipelineKeys.pipelineCacheKey(renderPipelinePreimage(gatePlan))
}

/** Materializes one registered runtime-effect material lane after the descriptor gate accepts it. */
class ValidatingRuntimeEffectExecutionMaterializer(
    private val payloadProvider: GPUResourceProvider = ValidatingPayloadResourceProvider(),
) {
    /** Validates descriptor, registry, WGSL, uniform, payload, and pipeline facts before command encoding. */
    fun materialize(
        request: GPURuntimeEffectExecutionRequest,
        context: GPUTargetPreparationContext,
    ): GPURuntimeEffectExecutionResult {
        val accepted = request.gatePlan.routePlan as? GPURuntimeEffectRoutePlan.Accepted
            ?: return request.refusedFromGate(context)

        val diagnostics = request.executionDiagnostics(accepted, context)
        if (diagnostics.isNotEmpty()) {
            return request.refusedExecution(accepted, diagnostics, context)
        }

        val payloadDecision = payloadProvider.materializePayloadBindings(request.payloadRequest, context)
        val payloadMaterialized = payloadDecision as? GPUResourceMaterializationDecision.Materialized
            ?: return request.resultFromDecision(accepted, payloadDecision, commandStream = null)

        val pipelineBinding = request.pipelineOperandBinding(accepted, context)
        val materializedDecision = payloadMaterialized.copy(
            resourcePlanLabels = (payloadMaterialized.dumpResourcePlanLabelsSnapshot + pipelineBinding.operand.label)
                .distinct(),
            operandBridge = payloadMaterialized.dumpOperandBridgeSnapshot + pipelineBinding,
        )
        val commandStream = request.commandStream(materializedDecision)

        return request.resultFromDecision(
            accepted = accepted,
            decision = materializedDecision,
            commandStream = commandStream,
        )
    }
}

/** Planner for contract-only registered runtime-effect route evidence. */
class GPURuntimeEffectDescriptorRoutePlanner {
    /** Plans a descriptor-backed runtime-effect material route or a stable refusal. */
    fun plan(request: GPURuntimeEffectDescriptorRouteRequest): GPURuntimeEffectDescriptorGatePlan {
        val matchingDescriptors = request.registrySnapshot.lookupAll(request.effectId)
        val hasDescriptorCollision = matchingDescriptors.size > 1
        val descriptor = matchingDescriptors.singleOrNull()
        val refusalCode = request.refusalCode(descriptor, hasDescriptorCollision)
        if (refusalCode != null) {
            return refusedPlan(request, matchingDescriptors.firstOrNull(), refusalCode)
        }

        val acceptedDescriptor = requireNotNull(descriptor)
        val wgslEvidence = requireNotNull(request.wgslEvidence)
        val cpuOracle = requireNotNull(request.cpuOracle)
        val diagnostic = GPURuntimeEffectDiagnostic(
            code = RUNTIME_EFFECT_ACCEPTED_CODE,
            effectId = acceptedDescriptor.id,
            message = "registered runtime-effect descriptor accepted: ${acceptedDescriptor.id.value}",
            terminal = false,
        )
        val lookupPlan = GPURuntimeEffectLookupPlan(
            inputKind = "descriptor-id",
            registryVersion = request.registrySnapshot.registryVersion,
            registryGeneration = request.registrySnapshot.generation,
            requestedEffectId = request.effectId,
            descriptorId = acceptedDescriptor.id,
            descriptorVersion = acceptedDescriptor.version,
            requestedPlacement = request.requestedPlacement,
            routePlacement = request.requestedPlacement,
            diagnostic = diagnostic,
        )
        val materialSource = GPUMaterialSourceDescriptor.RuntimeEffect(
            effectId = acceptedDescriptor.id.value,
            descriptorVersion = acceptedDescriptor.version.value,
            routeContractHash = runtimeEffectRouteContractHash(acceptedDescriptor),
        )

        return GPURuntimeEffectDescriptorGatePlan(
            label = request.label,
            evidenceRow = RUNTIME_EFFECT_EVIDENCE_ROW,
            routeKind = "GPUNative",
            classification = "DependencyGated",
            promoted = false,
            productActivation = true,
            materialized = false,
            routePlan = GPURuntimeEffectRoutePlan.Accepted(
                lookupPlan = lookupPlan,
                descriptor = acceptedDescriptor,
                wgslEvidence = wgslEvidence,
                cpuOracle = cpuOracle,
                materialSource = materialSource,
            ),
            registrySnapshot = request.registrySnapshot,
            payloadPlanHash = runtimeEffectPayloadPlanHash(acceptedDescriptor),
            materialKeyBoundaryHash = runtimeEffectMaterialKeyBoundaryHash(acceptedDescriptor, request.registrySnapshot),
            materialKeyIncludesUniformValues = false,
            diagnostics = listOf(diagnostic),
        )
    }

    private fun refusedPlan(
        request: GPURuntimeEffectDescriptorRouteRequest,
        descriptor: GPURuntimeEffectDescriptor?,
        refusalCode: String,
    ): GPURuntimeEffectDescriptorGatePlan {
        val diagnostic = GPURuntimeEffectDiagnostic(
            code = refusalCode,
            effectId = request.effectId,
            message = "registered runtime-effect descriptor refused: $refusalCode",
            terminal = true,
        )
        val lookupPlan = GPURuntimeEffectLookupPlan(
            inputKind = if (request.dynamicSkSLSourceProvided) "dynamic-sksl-source" else "descriptor-id",
            registryVersion = request.registrySnapshot.registryVersion,
            registryGeneration = request.registrySnapshot.generation,
            requestedEffectId = request.effectId,
            descriptorId = descriptor?.id,
            descriptorVersion = descriptor?.version,
            requestedPlacement = request.requestedPlacement,
            routePlacement = null,
            diagnostic = diagnostic,
        )

        return GPURuntimeEffectDescriptorGatePlan(
            label = request.label,
            evidenceRow = RUNTIME_EFFECT_EVIDENCE_ROW,
            routeKind = "RefuseDiagnostic",
            classification = "DependencyGated",
            promoted = false,
            productActivation = true,
            materialized = false,
            routePlan = GPURuntimeEffectRoutePlan.Refused(lookupPlan, diagnostic),
            registrySnapshot = request.registrySnapshot,
            payloadPlanHash = "",
            materialKeyBoundaryHash = "",
            materialKeyIncludesUniformValues = false,
            diagnostics = listOf(diagnostic),
        )
    }
}

/** Runtime-effect live edit plan. */
data class GPURuntimeEffectLiveEditPlan(
    val enabled: Boolean,
    val descriptorVersion: GPURuntimeEffectDescriptorVersion,
    val validationPolicy: String,
)

/** Runtime-effect usage set captured by a recording. */
data class GPURuntimeEffectUsageSet(
    val effectIds: Set<GPURuntimeEffectID>,
    val descriptorVersions: Map<GPURuntimeEffectID, GPURuntimeEffectDescriptorVersion>,
)

/** Runtime-effect descriptor. */
data class GPURuntimeEffectDescriptor(
    val id: GPURuntimeEffectID,
    val version: GPURuntimeEffectDescriptorVersion,
    val uniformSchema: GPURuntimeEffectUniformSchema,
    val uniformBlockPlan: GPURuntimeEffectUniformBlockPlan,
    val childSlots: List<GPURuntimeEffectChildSlotPlan>,
    val resources: GPURuntimeEffectResourcePlan,
    val wgslPlan: GPURuntimeEffectWGSLPlan,
    val routeContract: GPURuntimeEffectRouteContract,
    val liveEditPlan: GPURuntimeEffectLiveEditPlan,
    val liveParameterSchema: GPURuntimeEffectLiveParameterSchema? = null,
    val diagnostics: List<GPURuntimeEffectDiagnostic> = emptyList(),
)

/** Runtime-effect diagnostic. */
data class GPURuntimeEffectDiagnostic(
    val code: String,
    val effectId: GPURuntimeEffectID? = null,
    val message: String,
    val terminal: Boolean,
)

/** Input for the refusal-only runtime-effect child/source matrix. */
data class GPURuntimeEffectRefusalMatrixInput(
    val registrySnapshot: GPURuntimeEffectRegistrySnapshot,
    val descriptorId: GPURuntimeEffectID,
    val shapes: List<GPURuntimeEffectRefusalShape>,
)

/** Dynamic source categories that are never compiled by the GPU renderer. */
enum class GPURuntimeEffectDynamicSourceKind {
    /** Skia SkSL source text. */
    SkSL,
    /** Raw WGSL source text outside a registered descriptor. */
    WGSL,
}

/** Runtime-effect shape that must remain refusal-only in KGPU-M7-002. */
sealed interface GPURuntimeEffectRefusalShape {
    val label: String

    /** Arbitrary dynamic source input. */
    data class DynamicSource(
        override val label: String,
        val sourceKind: GPURuntimeEffectDynamicSourceKind,
    ) : GPURuntimeEffectRefusalShape {
        init {
            require(label.isNotBlank()) { "runtime-effect refusal label must not be blank" }
        }
    }

    /** Unknown compatibility key or source hash that must not imply descriptor support. */
    data class CompatibilityKey(
        override val label: String,
        val keyHash: String,
    ) : GPURuntimeEffectRefusalShape {
        init {
            require(label.isNotBlank()) { "runtime-effect refusal label must not be blank" }
            require(keyHash.isNotBlank()) { "runtime-effect compatibility key hash must not be blank" }
        }
    }

    /** Child slot or child source shape not promoted by the descriptor route. */
    data class ChildSlot(
        override val label: String,
        val slot: GPURuntimeEffectChildSlotPlan,
        val childSourceKind: String,
        val sampleUsage: String,
    ) : GPURuntimeEffectRefusalShape {
        init {
            require(label.isNotBlank()) { "runtime-effect refusal label must not be blank" }
            require(childSourceKind.isNotBlank()) { "runtime-effect child source kind must not be blank" }
            require(sampleUsage.isNotBlank()) { "runtime-effect child sample usage must not be blank" }
        }
    }

    /** Descriptor placement not accepted by the registered route contract. */
    data class UnsupportedPlacement(
        override val label: String,
        val requestedPlacement: GPURuntimeEffectRoutePlacement,
    ) : GPURuntimeEffectRefusalShape {
        init {
            require(label.isNotBlank()) { "runtime-effect refusal label must not be blank" }
        }
    }
}

/** One PM-visible refusal row for runtime-effect source, child, or placement shapes. */
data class GPURuntimeEffectRefusalMatrixRow(
    val label: String,
    val category: String,
    val descriptorAnchor: String,
    val routeKind: String,
    val classification: String,
    val productActivation: Boolean,
    val diagnostic: GPURuntimeEffectDiagnostic,
    val facts: Map<String, String>,
)

/** Refusal-only runtime-effect child/source matrix report. */
data class GPURuntimeEffectRefusalMatrixReport(
    val evidenceRow: String,
    val classification: String,
    val rows: List<GPURuntimeEffectRefusalMatrixRow>,
) {
    /** KGPU-M7-002 never promotes runtime-effect source, child, or placement support. */
    val promotable: Boolean = false

    /** Returns canonical PM/review dump lines for this refusal-only matrix. */
    fun dumpLines(): List<String> {
        val rowLines = rows.map { row ->
            "runtime-effect-refusal row=$evidenceRow category=${row.category} shape=${row.label} " +
                "descriptor=${row.descriptorAnchor} routeKind=${row.routeKind} " +
                "classification=${row.classification} reason=${row.diagnostic.code} " +
                "productActivation=${row.productActivation} facts=${row.facts.dumpRuntimeEffectRefusalFacts()}"
        }
        val descriptorAnchor = rows.firstOrNull()?.descriptorAnchor ?: "none"
        return rowLines + listOf(
            "runtime-effect-refusal:summary row=$evidenceRow descriptor=$descriptorAnchor " +
                "refused=${rows.size} promotable=$promotable",
            RUNTIME_EFFECT_REFUSAL_NONCLAIM_LINE,
        )
    }
}

/** Builds refusal-only child/source evidence without promoting runtime-effect breadth. */
object GPURuntimeEffectRefusalMatrix {
    /** Evaluates runtime-effect shapes into stable refusal rows. */
    fun evaluate(input: GPURuntimeEffectRefusalMatrixInput): GPURuntimeEffectRefusalMatrixReport {
        val matchingDescriptors = input.registrySnapshot.lookupAll(input.descriptorId)
        val descriptor = matchingDescriptors.singleOrNull()
        val anchor = descriptorAnchor(input.descriptorId, matchingDescriptors)
        val rows = input.shapes.map { shape ->
            val code = when {
                matchingDescriptors.isEmpty() -> "unsupported.runtime_effect.unregistered_descriptor"
                matchingDescriptors.size > 1 -> "unsupported.runtime_effect.descriptor_collision"
                else -> shape.refusalCode(requireNotNull(descriptor))
            }
            GPURuntimeEffectRefusalMatrixRow(
                label = shape.label,
                category = shape.category,
                descriptorAnchor = anchor,
                routeKind = "RefuseDiagnostic",
                classification = RUNTIME_EFFECT_REFUSAL_CLASSIFICATION,
                productActivation = true,
                diagnostic = GPURuntimeEffectDiagnostic(
                    code = code,
                    effectId = input.descriptorId,
                    message = "runtime-effect refusal matrix row ${shape.label} refused: $code",
                    terminal = true,
                ),
                facts = shape.refusalFacts(descriptor, matchingDescriptors.size),
            )
        }

        return GPURuntimeEffectRefusalMatrixReport(
            evidenceRow = RUNTIME_EFFECT_REFUSAL_EVIDENCE_ROW,
            classification = RUNTIME_EFFECT_REFUSAL_CLASSIFICATION,
            rows = rows,
        )
    }
}

private fun GPURuntimeEffectExecutionRequest.refusedFromGate(
    context: GPUTargetPreparationContext,
): GPURuntimeEffectExecutionResult {
    val refused = gatePlan.routePlan as GPURuntimeEffectRoutePlan.Refused
    val diagnostic = refused.diagnostic.toResourceDiagnostic(resourceLabel = executionResourceLabel())
    val decision = GPUResourceMaterializationDecision.Refused(
        diagnostic = diagnostic,
        targetId = context.targetId,
        taskIds = payloadRequest.dumpTaskIdsSnapshot,
        resourcePlanLabels = listOf(executionResourceLabel()),
        diagnostics = listOf(diagnostic),
    )
    return GPURuntimeEffectExecutionResult(
        evidenceRow = RUNTIME_EFFECT_EXECUTION_EVIDENCE_ROW,
        descriptorId = refused.lookupPlan.requestedEffectId,
        descriptorVersion = refused.lookupPlan.descriptorVersion,
        registryGeneration = refused.lookupPlan.registryGeneration,
        routePlacement = refused.lookupPlan.routePlacement,
        pipelineLabel = null,
        renderPipelineKey = null,
        pipelineCacheKey = pipelineCacheKey,
        payloadFingerprint = null,
        bindingLayoutHash = null,
        wgslModuleHash = null,
        reflectionHash = null,
        uniformSchemaHash = null,
        cpuOracleEvidenceHash = null,
        gpuReadbackStatus = "refused",
        gpuReadbackReason = diagnostic.code,
        resourceDecision = decision,
        commandStream = null,
        uniformValuesInKey = gatePlan.materialKeyIncludesUniformValues,
    )
}

private fun GPURuntimeEffectExecutionRequest.refusedExecution(
    accepted: GPURuntimeEffectRoutePlan.Accepted,
    diagnostics: List<GPUResourceDiagnostic>,
    context: GPUTargetPreparationContext,
): GPURuntimeEffectExecutionResult {
    val decision = GPUResourceMaterializationDecision.Refused(
        diagnostic = diagnostics.first(),
        targetId = context.targetId,
        taskIds = payloadRequest.dumpTaskIdsSnapshot,
        resourcePlanLabels = listOf(executionResourceLabel()),
        diagnostics = diagnostics,
    )
    return resultFromDecision(accepted, decision, commandStream = null)
}

private fun GPURuntimeEffectExecutionRequest.resultFromDecision(
    accepted: GPURuntimeEffectRoutePlan.Accepted,
    decision: GPUResourceMaterializationDecision,
    commandStream: GPUPassCommandStream?,
): GPURuntimeEffectExecutionResult {
    val descriptor = accepted.descriptor
    return GPURuntimeEffectExecutionResult(
        evidenceRow = RUNTIME_EFFECT_EXECUTION_EVIDENCE_ROW,
        descriptorId = descriptor.id,
        descriptorVersion = descriptor.version,
        registryGeneration = gatePlan.registrySnapshot.generation,
        routePlacement = accepted.lookupPlan.routePlacement,
        pipelineLabel = runtimeEffectPipelineOperandLabel(descriptor),
        renderPipelineKey = GPURuntimeEffectExecutionKeys.renderPipelineKey(gatePlan).value,
        pipelineCacheKey = pipelineCacheKey,
        payloadFingerprint = payloadRequest.uniformBlock.fingerprint.value,
        bindingLayoutHash = payloadRequest.reflectedBindingLayoutHash,
        wgslModuleHash = descriptor.wgslPlan.moduleHash,
        reflectionHash = descriptor.wgslPlan.reflectionHash,
        uniformSchemaHash = descriptor.uniformSchema.schemaHash,
        cpuOracleEvidenceHash = accepted.cpuOracle.evidenceHash,
        gpuReadbackStatus = if (decision is GPUResourceMaterializationDecision.Materialized) {
            gpuReadbackStatus
        } else {
            "refused"
        },
        gpuReadbackReason = if (decision is GPUResourceMaterializationDecision.Materialized) {
            gpuReadbackReason
        } else {
            decision.runtimeEffectRefusalReason()
        },
        resourceDecision = decision,
        commandStream = commandStream,
        uniformValuesInKey = gatePlan.materialKeyIncludesUniformValues,
    )
}

private fun GPURuntimeEffectExecutionRequest.executionDiagnostics(
    accepted: GPURuntimeEffectRoutePlan.Accepted,
    context: GPUTargetPreparationContext,
): List<GPUResourceDiagnostic> {
    val descriptor = accepted.descriptor
    return buildList {
        if (payloadRequest.targetId != context.targetId) {
            add(executionDiagnostic("unsupported.runtime_effect.target_mismatch", descriptor))
        }
        if (gatePlan.registrySnapshot.generation != expectedRegistryGeneration ||
            accepted.lookupPlan.registryGeneration != expectedRegistryGeneration
        ) {
            add(executionDiagnostic("unsupported.runtime_effect.registry_generation_stale", descriptor))
        }
        if (descriptor.id != expectedDescriptorId) {
            add(executionDiagnostic("unsupported.runtime_effect.descriptor_id_mismatch", descriptor))
        }
        if (descriptor.version != expectedDescriptorVersion) {
            add(executionDiagnostic("unsupported.runtime_effect.descriptor_version_mismatch", descriptor))
        }
        if (accepted.lookupPlan.routePlacement != expectedRoutePlacement) {
            add(executionDiagnostic("unsupported.runtime_effect.route_placement_mismatch", descriptor))
        }
        if (descriptor.wgslPlan.moduleHash != expectedWgslModuleHash) {
            add(executionDiagnostic("unsupported.runtime_effect.wgsl_module_mismatch", descriptor))
        }
        if (descriptor.wgslPlan.reflectionHash != expectedReflectionHash) {
            add(executionDiagnostic("unsupported.runtime_effect.wgsl_reflection_mismatch", descriptor))
        }
        if (descriptor.uniformSchema.schemaHash != expectedUniformSchemaHash) {
            add(executionDiagnostic("unsupported.runtime_effect.uniform_schema_mismatch", descriptor))
        }
        if (descriptor.uniformBlockPlan.blockSizeBytes != payloadRequest.uniformBlock.byteSize) {
            add(executionDiagnostic("unsupported.runtime_effect.uniform_block_size_mismatch", descriptor))
        }
        val payloadFields = payloadRequest.uniformBlock.fields.map { field ->
            "${field.fieldPath}:${field.valueClass}@${field.byteOffset}:${field.byteSize}"
        }
        if (payloadFields != descriptor.uniformSchema.fields) {
            add(executionDiagnostic("unsupported.runtime_effect.uniform_payload_schema_mismatch", descriptor))
        }
        if (descriptor.resources.bindingPlanHash != payloadRequest.reflectedBindingLayoutHash ||
            descriptor.resources.bindingPlanHash != payloadRequest.resourceBlock.bindingPlanHash
        ) {
            add(executionDiagnostic("unsupported.runtime_effect.payload_binding_mismatch", descriptor))
        }
        if (pipelineCacheKey != GPURuntimeEffectExecutionKeys.pipelineCacheKey(gatePlan).value) {
            add(executionDiagnostic("unsupported.runtime_effect.pipeline_cache_key_mismatch", descriptor))
        }
        if (descriptor.childSlots.isNotEmpty()) {
            add(executionDiagnostic("unsupported.runtime_effect.child_count", descriptor))
        }
        if (descriptor.liveEditPlan.enabled) {
            add(executionDiagnostic("unsupported.runtime_effect.live_edit_unaccepted", descriptor))
        }
        if (gatePlan.materialKeyIncludesUniformValues) {
            add(executionDiagnostic("unsupported.runtime_effect.uniform_values_in_pipeline_key", descriptor))
        }
        if (accepted.cpuOracle.evidenceHash != cpuOracleEvidenceHash) {
            add(executionDiagnostic("unsupported.runtime_effect.cpu_oracle_mismatch", descriptor))
        }
        if (accepted.lookupPlan.requestedPlacement != GPURuntimeEffectRoutePlacement.MaterialSource) {
            add(executionDiagnostic("unsupported.runtime_effect.route_placement_mismatch", descriptor))
        }
    }
}

private fun GPURuntimeEffectExecutionRequest.pipelineOperandBinding(
    accepted: GPURuntimeEffectRoutePlan.Accepted,
    context: GPUTargetPreparationContext,
): GPUMaterializedCommandOperandBinding {
    val descriptor = accepted.descriptor
    return GPUMaterializedCommandOperandBinding(
        packetId = payloadRequest.packetId,
        commandLabel = "setRenderPipeline",
        operand = GPUMaterializedCommandOperandReference(
            label = runtimeEffectPipelineOperandLabel(descriptor),
            kind = GPUMaterializedCommandOperandKind.RenderPipeline,
            descriptorHash = pipelineCacheKey,
            deviceGeneration = context.deviceGeneration,
            ownerScope = "runtime-effect-pipeline-cache",
            usageLabels = listOf("render"),
            invalidationPolicy = "registry-generation",
            evidenceFacts = mapOf(
                "descriptor" to descriptor.id.value,
                "entry" to descriptor.wgslPlan.entryPoint,
                "registryGeneration" to gatePlan.registrySnapshot.generation.toString(),
                "route" to accepted.lookupPlan.routePlacement.toString(),
                "schema" to descriptor.uniformSchema.schemaHash,
                "uniformValuesInKey" to gatePlan.materialKeyIncludesUniformValues.toString(),
                "wgsl" to descriptor.wgslPlan.moduleHash,
            ),
        ),
    )
}

private fun GPURuntimeEffectExecutionRequest.commandStream(
    materializedDecision: GPUResourceMaterializationDecision.Materialized,
): GPUPassCommandStream {
    val packetId = GPUDrawPacketID(payloadRequest.packetId)
    return GPUPassCommandStream(
        streamId = streamId,
        packetStreamId = packetStreamId,
        passId = passId,
        commands = listOf(
            GPUPassCommand.BeginRenderPass(
                targetStateHash = targetStateHash,
                loadStoreLabel = loadStoreLabel,
            ),
            GPUPassCommand.SetRenderPipeline(
                pipelineKey = GPURuntimeEffectExecutionKeys.renderPipelineKey(gatePlan),
                packetId = packetId,
            ),
            GPUPassCommand.SetBindGroup(
                bindingLayoutHash = payloadRequest.reflectedBindingLayoutHash,
                uniformSlot = payloadRequest.uniformSlot,
                resourceSlot = payloadRequest.resourceSlot,
                packetId = packetId,
            ),
            GPUPassCommand.Draw(
                vertexSourceLabel = vertexSourceLabel,
                packetId = packetId,
            ),
            GPUPassCommand.EndRenderPass(passId = passId),
        ),
        operandBridge = materializedDecision.dumpOperandBridgeSnapshot.map(GPUPassCommandOperandBridge::fromMaterializedBinding),
    )
}

private fun GPURuntimeEffectExecutionRequest.executionDiagnostic(
    code: String,
    descriptor: GPURuntimeEffectDescriptor,
): GPUResourceDiagnostic =
    GPUResourceDiagnostic(
        code = code,
        resourceLabel = executionResourceLabel(),
        message = "registered runtime-effect execution refused for ${descriptor.id.value}: $code",
        terminal = true,
        facts = mapOf(
            "descriptor" to descriptor.id.value,
            "version" to descriptor.version.value.toString(),
            "registryGeneration" to gatePlan.registrySnapshot.generation.toString(),
            "expectedRegistryGeneration" to expectedRegistryGeneration.toString(),
        ),
    )

private fun GPURuntimeEffectDiagnostic.toResourceDiagnostic(resourceLabel: String): GPUResourceDiagnostic =
    GPUResourceDiagnostic(
        code = code,
        resourceLabel = resourceLabel,
        message = message,
        terminal = terminal,
        facts = effectId?.let { id -> mapOf("effectId" to id.value) }.orEmpty(),
    )

private fun GPUResourceMaterializationDecision.runtimeEffectRefusalReason(): String =
    when (this) {
        is GPUResourceMaterializationDecision.Refused -> diagnostic.code
        is GPUResourceMaterializationDecision.Deferred -> reasonCode
        is GPUResourceMaterializationDecision.Materialized -> RUNTIME_EFFECT_ACCEPTED_CODE
    }

private fun GPURuntimeEffectExecutionRequest.executionResourceLabel(): String =
    "runtime-effect-execution:${expectedDescriptorId.value}"

private fun runtimeEffectPipelineOperandLabel(descriptor: GPURuntimeEffectDescriptor): String =
    "runtime-effect-pipeline:${descriptor.id.value}@${descriptor.version.value}"

private const val RUNTIME_EFFECT_EVIDENCE_ROW = "gpu-renderer.runtime-effect.registered"
private const val RUNTIME_EFFECT_EXECUTION_EVIDENCE_ROW = "gpu-renderer.runtime-effect.registered.execution"
private const val NONE_RUNTIME_EFFECT_VALUE = "none"
private const val RUNTIME_EFFECT_ACCEPTED_CODE = "accepted.runtime_effect.registered_descriptor"
private const val RUNTIME_EFFECT_NONCLAIM_LINE =
    "runtime-effect:nonclaim nativeRuntimeEffect=false adapterBacked=false dynamicSkSL=false " +
        "arbitraryWGSL=false children=false blender=false filter=false productActivation=true"
private const val RUNTIME_EFFECT_EXECUTION_NONCLAIM_LINE =
    "runtime-effect:nonclaim executionLane=registered-descriptor adapterBacked=false " +
        "dynamicSkSL=false arbitraryWGSL=false children=false blender=false filter=false productActivation=true"
private const val RUNTIME_EFFECT_REFUSAL_EVIDENCE_ROW = "gpu-renderer.runtime-effect-refusals"
private const val RUNTIME_EFFECT_REFUSAL_CLASSIFICATION = "RefuseRequired"
private const val RUNTIME_EFFECT_REFUSAL_NONCLAIM_LINE =
    "runtime-effect:nonclaim arbitrarySkSL=false arbitraryWGSL=false children=false " +
        "unsupportedPlacementSupport=false productActivation=true"

private fun GPURuntimeEffectDescriptorRouteRequest.refusalCode(
    descriptor: GPURuntimeEffectDescriptor?,
    hasDescriptorCollision: Boolean,
): String? =
    when {
        dynamicSkSLSourceProvided -> "unsupported.runtime_effect.dynamic_sksl_forbidden"
        hasDescriptorCollision -> "unsupported.runtime_effect.descriptor_collision"
        descriptor == null -> "unsupported.runtime_effect.unregistered_descriptor"
        requestedPlacement !in descriptor.routeContract.acceptedPlacements -> "unsupported.runtime_effect.kind_mismatch"
        !descriptor.routeContract.nativeSupported || descriptor.routeContract.cpuOracleOnly ->
            descriptor.routeContract.refusalCode ?: "unsupported.runtime_effect.route_unaccepted"
        descriptor.routeContract.refusalCode != null -> descriptor.routeContract.refusalCode
        wgslEvidence == null -> "unsupported.runtime_effect.wgsl_missing"
        !wgslEvidence.report.matchesDescriptor(descriptor) ->
            "unsupported.runtime_effect.wgsl_reflection"
        cpuOracle == null || !cpuOracle.matchesDescriptor(descriptor) ->
            "unsupported.runtime_effect.cpu_oracle_missing"
        descriptor.childSlots.isNotEmpty() -> "unsupported.runtime_effect.child_count"
        else -> null
    }

private fun runtimeEffectRouteContractHash(descriptor: GPURuntimeEffectDescriptor): String =
    "sha256:" + runtimeEffectStableHash(
        listOf(
            "runtime-effect-route-contract-v1",
            descriptor.id.value,
            descriptor.version.value.toString(),
            descriptor.routeContract.acceptedPlacementKey(),
            descriptor.routeContract.nativeSupported.toString(),
            descriptor.routeContract.cpuOracleOnly.toString(),
            descriptor.wgslPlan.moduleHash,
            descriptor.uniformSchema.schemaHash,
            descriptor.resources.bindingPlanHash,
        ),
    )

private fun runtimeEffectPayloadPlanHash(descriptor: GPURuntimeEffectDescriptor): String =
    "sha256:" + runtimeEffectStableHash(
        listOf(
            "runtime-effect-payload-plan-v1",
            descriptor.id.value,
            descriptor.version.value.toString(),
            descriptor.uniformSchema.schemaHash,
            descriptor.uniformSchema.fields.joinToString(","),
            descriptor.uniformBlockPlan.blockSizeBytes.toString(),
            descriptor.resources.bindingPlanHash,
        ),
    )

private fun runtimeEffectMaterialKeyBoundaryHash(
    descriptor: GPURuntimeEffectDescriptor,
    registrySnapshot: GPURuntimeEffectRegistrySnapshot,
): String =
    "sha256:" + runtimeEffectStableHash(
        listOf(
            "runtime-effect-material-key-boundary-v1",
            registrySnapshot.registryVersion,
            registrySnapshot.generation.toString(),
            descriptor.id.value,
            descriptor.version.value.toString(),
            descriptor.routeContract.acceptedPlacementKey(),
            descriptor.uniformSchema.schemaHash,
            descriptor.wgslPlan.moduleHash,
            descriptor.resources.bindingPlanHash,
            "uniform-values-excluded",
            "cpu-oracle-output-excluded",
            "dynamic-source-excluded",
        ),
    )

private fun List<GPURuntimeEffectDiagnostic>.dumpRuntimeEffectDiagnostics(): String =
    if (isEmpty()) {
        "none"
    } else {
        joinToString(",") { diagnostic -> diagnostic.code }
    }

private fun GPURuntimeEffectRouteContract.acceptedPlacementKey(): String =
    acceptedPlacements.map { placement -> placement.name }.sorted().joinToString(",")

private fun WgslConsumedReflectionReport.matchesDescriptor(descriptor: GPURuntimeEffectDescriptor): Boolean =
    comparison.status == "accepted" &&
        diagnostics.none { diagnostic -> diagnostic.terminal } &&
        reportKind == "runtime-effect" &&
        moduleId == descriptor.id.value &&
        moduleHash == descriptor.wgslPlan.moduleHash &&
        descriptorId == descriptor.id.value &&
        descriptorVersion == descriptor.version.value &&
        routePromotion == "not-promoted" &&
        productActivation &&
        entryPoints.any { entryPoint -> entryPoint.name == descriptor.wgslPlan.entryPoint && entryPoint.stage == "fragment" } &&
        uniformSchemaMatchesDescriptor(descriptor)

private fun WgslConsumedReflectionReport.uniformSchemaMatchesDescriptor(
    descriptor: GPURuntimeEffectDescriptor,
): Boolean {
    val uniformLayouts = layouts.filter { layout -> layout.addressSpace == "uniform" }
    val reflectedFields = uniformLayouts.flatMap { layout ->
        layout.members.map { member -> "${member.name}:${member.type}@${member.offset}:${member.size}" }
    }
    return descriptor.uniformBlockPlan.schema == descriptor.uniformSchema &&
        descriptor.uniformSchema.fields == reflectedFields &&
        uniformLayouts.any { layout -> layout.size.toLong() == descriptor.uniformBlockPlan.blockSizeBytes }
}

private fun GPURuntimeEffectOracleResult.matchesDescriptor(descriptor: GPURuntimeEffectDescriptor): Boolean =
    effectId == descriptor.id &&
        evidenceHash.isCanonicalRuntimeEffectEvidenceHash() &&
        diagnostics.none { diagnostic -> diagnostic.terminal }

private fun String.isCanonicalRuntimeEffectEvidenceHash(): Boolean =
    matches(Regex("""sha256:[0-9a-f]{64}"""))

private val GPURuntimeEffectRefusalShape.category: String
    get() = when (this) {
        is GPURuntimeEffectRefusalShape.DynamicSource -> "source"
        is GPURuntimeEffectRefusalShape.CompatibilityKey -> "source"
        is GPURuntimeEffectRefusalShape.ChildSlot -> "child"
        is GPURuntimeEffectRefusalShape.UnsupportedPlacement -> "placement"
    }

private fun GPURuntimeEffectRefusalShape.refusalCode(descriptor: GPURuntimeEffectDescriptor): String =
    when (this) {
        is GPURuntimeEffectRefusalShape.DynamicSource -> when (sourceKind) {
            GPURuntimeEffectDynamicSourceKind.SkSL -> "unsupported.runtime_effect.dynamic_sksl_forbidden"
            GPURuntimeEffectDynamicSourceKind.WGSL -> "unsupported.runtime_effect.dynamic_wgsl_forbidden"
        }
        is GPURuntimeEffectRefusalShape.CompatibilityKey ->
            "unsupported.runtime_effect.compatibility_key_unknown"
        is GPURuntimeEffectRefusalShape.ChildSlot ->
            when {
                childSourceKind == "missing" && slot.required -> "unsupported.runtime_effect.child_missing"
                childSourceKind !in slot.acceptedSourceKinds -> "unsupported.runtime_effect.child_kind"
                sampleUsage != "same-pixel" -> "unsupported.runtime_effect.child_sample_radius"
                else -> "unsupported.runtime_effect.child_count"
            }
        is GPURuntimeEffectRefusalShape.UnsupportedPlacement ->
            if (requestedPlacement in descriptor.routeContract.acceptedPlacements) {
                "unsupported.runtime_effect.route_unaccepted"
            } else {
                "unsupported.runtime_effect.kind_mismatch"
            }
    }

private fun GPURuntimeEffectRefusalShape.refusalFacts(
    descriptor: GPURuntimeEffectDescriptor?,
    descriptorMatchCount: Int,
): Map<String, String> {
    val facts = linkedMapOf("descriptorMatches" to descriptorMatchCount.toString())
    when (this) {
        is GPURuntimeEffectRefusalShape.DynamicSource ->
            facts["sourceKind"] = sourceKind.name
        is GPURuntimeEffectRefusalShape.CompatibilityKey ->
            facts["keyHash"] = keyHash
        is GPURuntimeEffectRefusalShape.ChildSlot -> {
            facts["slotName"] = slot.slotName
            facts["acceptedSourceKinds"] = slot.acceptedSourceKinds.stableFactSet()
            facts["required"] = slot.required.toString()
            facts["childSourceKind"] = childSourceKind
            facts["sampleUsage"] = sampleUsage
        }
        is GPURuntimeEffectRefusalShape.UnsupportedPlacement -> {
            facts["requestedPlacement"] = requestedPlacement.name
            facts["acceptedPlacements"] = descriptor?.routeContract?.acceptedPlacements.stablePlacementFactSet()
        }
    }
    return facts
}

private fun Map<String, String>.dumpRuntimeEffectRefusalFacts(): String =
    entries.sortedBy { entry -> entry.key }
        .joinToString(",") { entry -> "${entry.key}=${entry.value}" }

private fun Set<String>.stableFactSet(): String =
    if (isEmpty()) {
        "none"
    } else {
        sorted().joinToString("|")
    }

private fun Set<GPURuntimeEffectRoutePlacement>?.stablePlacementFactSet(): String =
    if (isNullOrEmpty()) {
        "none"
    } else {
        map { placement -> placement.name }.sorted().joinToString("|")
    }

private fun descriptorAnchor(
    id: GPURuntimeEffectID,
    descriptors: List<GPURuntimeEffectDescriptor>,
): String =
    when (descriptors.size) {
        0 -> "${id.value}@unregistered"
        1 -> "${descriptors.single().id.value}@${descriptors.single().version.value}"
        else -> "${id.value}@collision"
    }

private fun runtimeEffectStableHash(parts: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = parts.joinToString(separator = "\u001F").toByteArray()
    return digest.digest(bytes)
        .take(8)
        .joinToString("") { byte -> "%02x".format(byte) }
}
