package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceDescriptor
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
            productActivation = false,
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
            productActivation = false,
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
    val diagnostics: List<GPURuntimeEffectDiagnostic> = emptyList(),
)

/** Runtime-effect diagnostic. */
data class GPURuntimeEffectDiagnostic(
    val code: String,
    val effectId: GPURuntimeEffectID? = null,
    val message: String,
    val terminal: Boolean,
)

private const val RUNTIME_EFFECT_EVIDENCE_ROW = "gpu-renderer.runtime-effect.registered"
private const val RUNTIME_EFFECT_ACCEPTED_CODE = "accepted.runtime_effect.registered_descriptor"
private const val RUNTIME_EFFECT_NONCLAIM_LINE =
    "runtime-effect:nonclaim nativeRuntimeEffect=false adapterBacked=false dynamicSkSL=false " +
        "arbitraryWGSL=false children=false blender=false filter=false productActivation=false"

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
        !productActivation &&
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

private fun runtimeEffectStableHash(parts: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = parts.joinToString(separator = "\u001F").toByteArray()
    return digest.digest(bytes)
        .take(8)
        .joinToString("") { byte -> "%02x".format(byte) }
}
