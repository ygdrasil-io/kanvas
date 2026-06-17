package org.graphiks.kanvas.gpu.renderer.destination

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedResourceReference
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationPreimagePlan

/** Destination read token. */
@JvmInline
value class GPUDestinationReadToken(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUDestinationReadToken.value must not be blank" }
    }
}

/** Destination-read requirement. */
enum class GPUDestinationReadRequirement {
    /** No destination read is required. */
    None,
    /** Fixed-function blend is enough. */
    FixedFunctionBlend,
    /** A copy of the current target is required. */
    TargetCopy,
    /** Existing intermediate can satisfy the read. */
    ExistingIntermediate,
    /** Layer isolation is required. */
    LayerIsolation,
    /** Requirement cannot be represented and must refuse. */
    Refused,
}

/** Destination-read strategy. */
enum class GPUDestinationReadStrategy {
    /** No strategy needed. */
    None,
    /** Use fixed-function blend. */
    FixedFunction,
    /** Copy target to an intermediate. */
    CopyTarget,
    /** Bind an existing intermediate. */
    BindIntermediate,
    /** Force isolated layer composition. */
    IsolateLayer,
    /** Refuse the draw. */
    Refuse,
}

/** Destination read bounds. */
data class GPUDestinationReadBounds(
    val boundsLabel: String,
    val conservative: Boolean,
    val pixelAligned: Boolean,
    val finite: Boolean = true,
    val requestedBoundsLabel: String = boundsLabel,
    val unclippedBoundsLabel: String = boundsLabel,
    val clippedBoundsLabel: String = boundsLabel,
    val copyBoundsLabel: String = clippedBoundsLabel,
    val originX: Int = 0,
    val originY: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val targetWidth: Int = 0,
    val targetHeight: Int = 0,
)

/** Destination read binding. */
data class GPUDestinationReadBinding(
    val bindingLabel: String,
    val layoutHash: String,
    val bounds: GPUDestinationReadBounds,
    val generation: Long,
    val textureViewHash: String = "",
    val samplerHash: String = "",
    val resourceSlot: String = "",
    val inMaterialKey: Boolean = false,
)

/** Destination-read action for pass and task planning. */
enum class GPUDestinationReadAction {
    /** No pass split, copy, or binding is required. */
    KeepInPass,
    /** Use fixed-function attachment blend state. */
    UseFixedFunctionBlend,
    /** Split the pass, copy the target, then sample the copied texture. */
    SplitPassAndCopyTarget,
    /** Bind a validated separate intermediate texture. */
    UseExistingIntermediate,
    /** Create an isolated layer target. */
    CreateIsolatedLayer,
    /** Composite an isolated layer back into its parent target. */
    CompositeIsolatedLayer,
    /** Refuse the destination-read route. */
    Refuse,
}

/** Descriptor for a destination-copy texture. */
data class GPUDestinationCopyTextureDescriptor(
    val label: String,
    val descriptorHash: String,
    val sourceTargetLabel: String,
    val targetGeneration: Long,
    val width: Int,
    val height: Int,
    val formatClass: String,
    val usageLabels: List<String>,
    val sampleCount: Int,
    val lifetimeClass: String,
    val ownerLabel: String,
    val byteEstimate: Long,
) {
    val usageLabel: String
        get() = usageLabels.joinToString(",")
}

/** Destination-copy planning product. */
data class GPUDestinationCopyPlan(
    val descriptor: GPUDestinationCopyTextureDescriptor,
    val commandScopeLabel: String,
    val passSplitRequired: Boolean,
    val copyBeforeSample: Boolean,
    val token: GPUDestinationReadToken,
)

/** Budget policy for destination reads. */
data class GPUDestinationReadBudgetPolicy(
    val maxCopyBytes: Long,
    val budgetClass: String,
    val refusalCode: String? = null,
)

/** Destination read plan. */
data class GPUDestinationReadPlan(
    val requirement: GPUDestinationReadRequirement,
    val strategy: GPUDestinationReadStrategy,
    val bounds: GPUDestinationReadBounds,
    val sourceTargetFacts: List<String>,
    val copyDescriptorHash: String? = null,
    val binding: GPUDestinationReadBinding? = null,
    val barrierAction: String? = null,
    val budgetClass: String,
    val diagnostic: GPUDestinationReadDiagnostic? = null,
)

/** Request for the destination-read strategy evidence gate. */
data class GPUDestinationReadStrategyRequest(
    val label: String = "accepted",
    val commandId: String,
    val requirement: GPUDestinationReadRequirement,
    val strategy: GPUDestinationReadStrategy,
    val action: GPUDestinationReadAction,
    val bounds: GPUDestinationReadBounds,
    val sourceTargetLabel: String,
    val sourceUsageLabels: Set<String>,
    val copyUsageLabels: Set<String>,
    val targetFormatClass: String,
    val targetGeneration: Long,
    val observedTargetGeneration: Long = targetGeneration,
    val activeAttachmentSampled: Boolean = false,
    val intermediateLabel: String = sourceTargetLabel,
    val intermediateValidated: Boolean = true,
    val passSplitAllowed: Boolean = true,
    val framebufferFetchRequested: Boolean = false,
    val cpuReadbackRequested: Boolean = false,
    val maxCopyBytes: Long = DEFAULT_DESTINATION_COPY_MAX_BYTES,
)

/** Evidence result for the destination-read strategy gate. */
data class GPUDestinationReadStrategyGatePlan(
    val label: String,
    val evidenceRow: String,
    val routeKind: String,
    val classification: String,
    val promoted: Boolean,
    val productActivation: Boolean,
    val materialized: Boolean,
    val plan: GPUDestinationReadPlan,
    val action: GPUDestinationReadAction,
    val copyDescriptor: GPUDestinationCopyTextureDescriptor?,
    val copyPlan: GPUDestinationCopyPlan?,
    val budgetPolicy: GPUDestinationReadBudgetPolicy,
    val bindingInMaterialKey: Boolean,
    val materialKeyBoundaryHash: String,
    val diagnostics: List<GPUDestinationReadDiagnostic>,
) {
    val copyDescriptorHash: String
        get() = copyDescriptor?.descriptorHash ?: intermediateDescriptorHash()

    val bindingLayoutHash: String
        get() = requireNotNull(plan.binding).layoutHash

    val textureViewHash: String
        get() = requireNotNull(plan.binding).textureViewHash

    val samplerHash: String
        get() = requireNotNull(plan.binding).samplerHash

    /** Returns the canonical dump lines for destination-read planning evidence. */
    fun dumpLines(): List<String> {
        val diagnostic = diagnostics.singleOrNull { it.terminal }
        if (diagnostic != null) {
            return listOf(
                "destination-read:strategy.refused row=$evidenceRow routeKind=$routeKind " +
                    "classification=$classification promoted=$promoted productActivation=$productActivation " +
                    "materialized=$materialized command=${plan.bounds.commandLabel()} " +
                    "reason=${diagnostic.code} label=$label",
                DESTINATION_READ_NONCLAIM_LINE,
            )
        }

        val binding = requireNotNull(plan.binding)
        val bounds = plan.bounds
        val sourceLabel = when (plan.strategy) {
            GPUDestinationReadStrategy.BindIntermediate -> plan.sourceTargetFacts.first { it.startsWith("intermediate=") }
                .removePrefix("intermediate=")
            else -> plan.sourceTargetFacts.first { it.startsWith("source=") }.removePrefix("source=")
        }

        val head = listOf(
            "destination-read:strategy row=$evidenceRow routeKind=$routeKind classification=$classification " +
                "promoted=$promoted productActivation=$productActivation materialized=$materialized " +
                "requirement=${plan.requirement.dumpLabel()} strategy=${plan.strategy.dumpLabel()} " +
                "action=$action source=$sourceLabel generation=${binding.generation}",
            "destination-read:bounds command=${bounds.commandLabel()} requested=${bounds.requestedBoundsLabel} " +
                "unclipped=${bounds.unclippedBoundsLabel} clipped=${bounds.clippedBoundsLabel} " +
                "copy=${bounds.copyBoundsLabel} finite=${bounds.finite} pixelAligned=${bounds.pixelAligned} " +
                "conservative=${bounds.conservative} target=${bounds.targetWidth}x${bounds.targetHeight}",
        )
        val source = when (plan.strategy) {
            GPUDestinationReadStrategy.CopyTarget -> listOf(copyLine())
            GPUDestinationReadStrategy.BindIntermediate -> listOf(intermediateLine(sourceLabel))
            else -> emptyList()
        }
        val barrier = requireNotNull(copyPlan)
        val resourceSourceUsage = plan.sourceTargetFacts.first { it.startsWith("sourceUsage=") }.removePrefix("sourceUsage=")
        val resourceCopyUsage = plan.sourceTargetFacts.first { it.startsWith("copyUsage=") }.removePrefix("copyUsage=")

        return head + source + listOf(
            "destination-read:binding label=${binding.bindingLabel} layout=${binding.layoutHash} " +
                "textureView=${binding.textureViewHash} sampler=${binding.samplerHash} " +
                "bounds=${bounds.copyBoundsLabel} generation=${binding.generation} " +
                "slot=${binding.resourceSlot} materialKey=${binding.inMaterialKey}",
            "destination-read:barrier split=${barrier.passSplitRequired} copyBeforeSample=${barrier.copyBeforeSample} " +
                "activeAttachmentSampled=false token=${barrier.token.value}",
            "destination-read:resource sourceUsage=$resourceSourceUsage copyUsage=$resourceCopyUsage " +
                "budget=${budgetPolicy.budgetClass} copyBytes=${copyDescriptor?.byteEstimate ?: bounds.copyByteEstimate()}",
            DESTINATION_READ_NONCLAIM_LINE,
        )
    }

    private fun copyLine(): String {
        val descriptor = requireNotNull(copyDescriptor)
        return "destination-read:copy label=${descriptor.label} descriptor=${descriptor.descriptorHash} " +
            "source=${descriptor.sourceTargetLabel} generation=${descriptor.targetGeneration} " +
            "size=${descriptor.width}x${descriptor.height} format=${descriptor.formatClass} " +
            "usage=${descriptor.usageLabel} sampleCount=${descriptor.sampleCount} " +
            "lifetime=${descriptor.lifetimeClass} owner=${descriptor.ownerLabel} bytes=${descriptor.byteEstimate}"
    }

    private fun intermediateLine(sourceLabel: String): String =
        "destination-read:intermediate label=$sourceLabel descriptor=${intermediateDescriptorHash()} " +
            "separateAttachment=true generation=${requireNotNull(plan.binding).generation} " +
            "bounds=${plan.bounds.copyBoundsLabel} lifetime=layer-local"

    private fun intermediateDescriptorHash(): String =
        plan.sourceTargetFacts.firstOrNull { it.startsWith("intermediateDescriptor=") }
            ?.removePrefix("intermediateDescriptor=")
            ?: ""
}

/**
 * Derives live-route resource materialization preimage from destination-read gate evidence.
 *
 * The result names either the planned target copy texture or the validated
 * intermediate texture. It remains backend-neutral and does not split passes,
 * copy targets, bind handles, or activate destination-read product routing.
 */
fun GPUDestinationReadStrategyGatePlan.toDestinationReadMaterializationPreimage(): GPUResourceMaterializationPreimagePlan {
    val planLabel = "destination-read:${plan.bounds.commandLabel().toStableLabel()}"
    val refusal = diagnostics.firstOrNull { diagnostic -> diagnostic.terminal }?.code
    if (refusal != null || routeKind == "RefuseDiagnostic") {
        return GPUResourceMaterializationPreimagePlan(
            planLabel = planLabel,
            sourceGate = DESTINATION_READ_EVIDENCE_ROW,
            accepted = false,
            resources = emptyList(),
            refusalCode = refusal ?: "unsupported.destination_read.materialization_preimage",
        )
    }

    val binding = requireNotNull(plan.binding) {
        "accepted destination-read materialization preimage requires a binding"
    }
    val sourceLabel = when (plan.strategy) {
        GPUDestinationReadStrategy.BindIntermediate -> plan.sourceFactValue("intermediate")
        else -> plan.sourceFactValue("source")
    }
    val resource = when (plan.strategy) {
        GPUDestinationReadStrategy.CopyTarget -> {
            val descriptor = requireNotNull(copyDescriptor) {
                "target-copy destination-read preimage requires a copy descriptor"
            }
            GPUMaterializedResourceReference(
                label = descriptor.label,
                role = GPUMaterializedResourceRole.DestinationCopyTexture,
                descriptorHash = descriptor.descriptorHash,
                generation = descriptor.targetGeneration,
                lifetimeClass = descriptor.lifetimeClass,
                usageLabels = descriptor.usageLabels,
                evidenceFacts = mapOf(
                    "action" to action.name,
                    "source" to sourceLabel,
                ),
            )
        }
        GPUDestinationReadStrategy.BindIntermediate -> GPUMaterializedResourceReference(
            label = sourceLabel,
            role = GPUMaterializedResourceRole.IntermediateTexture,
            descriptorHash = copyDescriptorHash,
            generation = binding.generation,
            lifetimeClass = "layer-local",
            usageLabels = listOf("texture_binding"),
            evidenceFacts = mapOf(
                "action" to action.name,
                "source" to sourceLabel,
            ),
        )
        else -> {
            return GPUResourceMaterializationPreimagePlan(
                planLabel = planLabel,
                sourceGate = DESTINATION_READ_EVIDENCE_ROW,
                accepted = false,
                resources = emptyList(),
                refusalCode = "unsupported.destination_read.materialization_preimage_strategy",
            )
        }
    }

    return GPUResourceMaterializationPreimagePlan(
        planLabel = planLabel,
        sourceGate = DESTINATION_READ_EVIDENCE_ROW,
        accepted = true,
        resources = listOf(resource),
        bindingLabels = listOf(binding.bindingLabel),
    )
}

/** Planner for contract-only destination-read strategy evidence. */
class GPUDestinationReadStrategyPlanner {
    /** Plans a destination-read copy/intermediate strategy or a stable refusal. */
    fun plan(request: GPUDestinationReadStrategyRequest): GPUDestinationReadStrategyGatePlan {
        val copyBytes = request.bounds.copyByteEstimate()
        val refusalCode = request.refusalCode(copyBytes)
        if (refusalCode != null) {
            return refusedPlan(request, refusalCode)
        }

        val descriptor = request.copyTextureDescriptor(copyBytes)
        val binding = request.binding()
        val token = GPUDestinationReadToken("dst-token:${request.commandId}:${request.targetGeneration}")
        val copyPlan = GPUDestinationCopyPlan(
            descriptor = descriptor,
            commandScopeLabel = request.commandId,
            passSplitRequired = request.action == GPUDestinationReadAction.SplitPassAndCopyTarget,
            copyBeforeSample = request.action == GPUDestinationReadAction.SplitPassAndCopyTarget,
            token = token,
        )
        val budgetClass = when (request.strategy) {
            GPUDestinationReadStrategy.BindIntermediate -> "intermediate-small"
            else -> "copy-small"
        }
        val plan = GPUDestinationReadPlan(
            requirement = request.requirement,
            strategy = request.strategy,
            bounds = request.bounds.withCommand(request.commandId),
            sourceTargetFacts = request.sourceFacts(descriptor),
            copyDescriptorHash = descriptor.descriptorHash,
            binding = binding,
            barrierAction = request.action.name,
            budgetClass = budgetClass,
            diagnostic = null,
        )
        return GPUDestinationReadStrategyGatePlan(
            label = request.label,
            evidenceRow = DESTINATION_READ_EVIDENCE_ROW,
            routeKind = "GPUNative",
            classification = "TargetNative",
            promoted = false,
            productActivation = false,
            materialized = false,
            plan = plan,
            action = request.action,
            copyDescriptor = if (request.strategy == GPUDestinationReadStrategy.CopyTarget) descriptor else null,
            copyPlan = copyPlan,
            budgetPolicy = GPUDestinationReadBudgetPolicy(request.maxCopyBytes, budgetClass),
            bindingInMaterialKey = binding.inMaterialKey,
            materialKeyBoundaryHash = destinationReadMaterialKeyBoundaryHash(request),
            diagnostics = listOf(request.acceptedDiagnostic()),
        )
    }

    private fun refusedPlan(
        request: GPUDestinationReadStrategyRequest,
        refusalCode: String,
    ): GPUDestinationReadStrategyGatePlan {
        val diagnostic = GPUDestinationReadDiagnostic(
            code = refusalCode,
            requirement = request.requirement,
            message = "destination-read strategy refused: $refusalCode",
            terminal = true,
        )
        val refusedBounds = request.bounds.withCommand(request.commandId)
        val plan = GPUDestinationReadPlan(
            requirement = request.requirement,
            strategy = GPUDestinationReadStrategy.Refuse,
            bounds = refusedBounds,
            sourceTargetFacts = emptyList(),
            barrierAction = GPUDestinationReadAction.Refuse.name,
            budgetClass = "refused",
            diagnostic = diagnostic,
        )
        return GPUDestinationReadStrategyGatePlan(
            label = request.label,
            evidenceRow = DESTINATION_READ_EVIDENCE_ROW,
            routeKind = "RefuseDiagnostic",
            classification = "TargetNative",
            promoted = false,
            productActivation = false,
            materialized = false,
            plan = plan,
            action = GPUDestinationReadAction.Refuse,
            copyDescriptor = null,
            copyPlan = null,
            budgetPolicy = GPUDestinationReadBudgetPolicy(request.maxCopyBytes, "refused", refusalCode),
            bindingInMaterialKey = false,
            materialKeyBoundaryHash = destinationReadMaterialKeyBoundaryHash(request),
            diagnostics = listOf(diagnostic),
        )
    }
}

/** Destination read diagnostic. */
data class GPUDestinationReadDiagnostic(
    val code: String,
    val requirement: GPUDestinationReadRequirement,
    val message: String,
    val terminal: Boolean,
)

private const val DEFAULT_DESTINATION_COPY_MAX_BYTES = 16L * 1024L * 1024L
private const val DESTINATION_READ_EVIDENCE_ROW = "gpu-renderer.destination-read.strategy"
private const val DESTINATION_READ_BYTES_PER_PIXEL = 4L
private const val DESTINATION_READ_ACCEPTED_CODE = "accepted.destination_read.strategy"
private const val DESTINATION_READ_STRATEGY_UNACCEPTED = "unsupported.destination_read.strategy_unaccepted"
private const val DESTINATION_READ_STRATEGY_ACTION_MISMATCH =
    "unsupported.destination_read.strategy_action_mismatch"
private const val DESTINATION_READ_NONCLAIM_LINE =
    "destination-read:nonclaim nativeDestinationRead=false adapterBacked=false framebufferFetch=false " +
        "inputAttachment=false cpuReadbackFallback=false productActivation=false"

private val DESTINATION_READ_USAGE_ORDER = listOf(
    "render_attachment",
    "copy_src",
    "copy_dst",
    "texture_binding",
    "storage_binding",
)

private fun GPUDestinationReadStrategyRequest.refusalCode(copyBytes: Long): String? =
    when {
        !bounds.finite -> "unsupported.destination_read.bounds_unbounded"
        bounds.width <= 0 || bounds.height <= 0 -> "unsupported.destination_read.bounds_invalid"
        framebufferFetchRequested -> "unsupported.destination_read.framebuffer_fetch_unavailable"
        cpuReadbackRequested -> "unsupported.destination_read.cpu_readback_forbidden"
        activeAttachmentSampled -> "unsupported.destination_read.active_attachment_sampled"
        observedTargetGeneration != targetGeneration -> "unsupported.destination_read.target_generation_stale"
        !strategy.isAcceptedByStrategyGate() -> DESTINATION_READ_STRATEGY_UNACCEPTED
        !strategy.acceptsAction(action) -> DESTINATION_READ_STRATEGY_ACTION_MISMATCH
        strategy == GPUDestinationReadStrategy.CopyTarget && "copy_src" !in sourceUsageLabels ->
            "unsupported.destination_read.copy_usage_missing"
        strategy == GPUDestinationReadStrategy.CopyTarget && "texture_binding" !in copyUsageLabels ->
            "unsupported.destination_read.texture_binding_missing"
        strategy == GPUDestinationReadStrategy.BindIntermediate && !intermediateValidated ->
            "unsupported.destination_read.intermediate_unvalidated"
        action == GPUDestinationReadAction.SplitPassAndCopyTarget && !passSplitAllowed ->
            "unsupported.destination_read.pass_split_illegal"
        copyBytes > maxCopyBytes -> "unsupported.destination_read.copy_budget_exceeded"
        else -> null
    }

private fun GPUDestinationReadStrategyRequest.acceptedDiagnostic(): GPUDestinationReadDiagnostic =
    GPUDestinationReadDiagnostic(
        code = DESTINATION_READ_ACCEPTED_CODE,
        requirement = requirement,
        message = "destination-read strategy accepted: ${strategy.dumpLabel()} action=$action",
        terminal = false,
    )

private fun GPUDestinationReadStrategy.isAcceptedByStrategyGate(): Boolean =
    this == GPUDestinationReadStrategy.CopyTarget || this == GPUDestinationReadStrategy.BindIntermediate

private fun GPUDestinationReadStrategy.acceptsAction(action: GPUDestinationReadAction): Boolean =
    when (this) {
        GPUDestinationReadStrategy.CopyTarget -> action == GPUDestinationReadAction.SplitPassAndCopyTarget
        GPUDestinationReadStrategy.BindIntermediate -> action == GPUDestinationReadAction.UseExistingIntermediate
        else -> false
    }

private fun GPUDestinationReadStrategyRequest.copyTextureDescriptor(
    copyBytes: Long,
): GPUDestinationCopyTextureDescriptor {
    val descriptorHash = destinationCopyDescriptorHash(this, copyUsageLabels.canonicalUsageLabels())
    return GPUDestinationCopyTextureDescriptor(
        label = "dst-copy:${commandId.toStableLabel()}",
        descriptorHash = descriptorHash,
        sourceTargetLabel = sourceTargetLabel,
        targetGeneration = targetGeneration,
        width = bounds.width,
        height = bounds.height,
        formatClass = targetFormatClass,
        usageLabels = copyUsageLabels.canonicalUsageLabels(),
        sampleCount = 1,
        lifetimeClass = "pass-local",
        ownerLabel = "GPURecorderScope",
        byteEstimate = copyBytes,
    )
}

private fun GPUDestinationReadStrategyRequest.binding(): GPUDestinationReadBinding {
    val bindingLabel = "dst-read:${commandId.toStableLabel()}"
    return GPUDestinationReadBinding(
        bindingLabel = bindingLabel,
        layoutHash = "sha256:" + stableHash(
            listOf("destination-read-binding-layout-v1", strategy.dumpLabel(), "group1.binding3"),
        ),
        bounds = bounds.withCommand(commandId),
        generation = targetGeneration,
        textureViewHash = "sha256:" + stableHash(
            listOf("destination-read-texture-view-v1", bounds.copyBoundsLabel, targetFormatClass),
        ),
        samplerHash = "sha256:" + stableHash(
            listOf("destination-read-sampler-v1", "nearest", "clamp-to-edge"),
        ),
        resourceSlot = "group1.binding3",
        inMaterialKey = false,
    )
}

private fun GPUDestinationReadStrategyRequest.sourceFacts(
    descriptor: GPUDestinationCopyTextureDescriptor,
): List<String> {
    val sourceUsage = when (strategy) {
        GPUDestinationReadStrategy.BindIntermediate -> "texture_binding"
        else -> sourceUsageLabels.canonicalUsageLabels().joinToString(",")
    }
    val copyUsage = when (strategy) {
        GPUDestinationReadStrategy.BindIntermediate -> "texture_binding"
        else -> copyUsageLabels.canonicalUsageLabels().joinToString(",")
    }
    val sourceFacts = mutableListOf(
        "source=$sourceTargetLabel",
        "sourceUsage=$sourceUsage",
        "copyUsage=$copyUsage",
        "targetFormat=$targetFormatClass",
    )
    if (strategy == GPUDestinationReadStrategy.BindIntermediate) {
        sourceFacts += "intermediate=$intermediateLabel"
        sourceFacts += "intermediateDescriptor=${descriptor.descriptorHash}"
    }
    return sourceFacts
}

private fun GPUDestinationReadPlan.sourceFactValue(name: String): String =
    sourceTargetFacts.first { fact -> fact.startsWith("$name=") }.removePrefix("$name=")

private fun destinationCopyDescriptorHash(
    request: GPUDestinationReadStrategyRequest,
    usageLabels: List<String>,
): String = "sha256:" + stableHash(
    listOf(
        "destination-copy-texture-v1",
        request.strategy.dumpLabel(),
        request.sourceTargetLabel,
        request.targetGeneration.toString(),
        request.bounds.copyBoundsLabel,
        request.bounds.width.toString(),
        request.bounds.height.toString(),
        request.targetFormatClass,
        usageLabels.joinToString(","),
        "sampleCount=1",
    ),
)

private fun destinationReadMaterialKeyBoundaryHash(
    request: GPUDestinationReadStrategyRequest,
): String = "sha256:" + stableHash(
    listOf(
        "destination-read-material-key-boundary-v1",
        request.requirement.dumpLabel(),
        request.strategy.dumpLabel(),
        "binding-excluded",
        "target-generation-excluded",
        "copy-descriptor-excluded",
    ),
)

private fun GPUDestinationReadBounds.copyByteEstimate(): Long =
    if (width <= 0 || height <= 0) {
        0L
    } else {
        val pixelCount = runCatching { Math.multiplyExact(width.toLong(), height.toLong()) }
            .getOrDefault(Long.MAX_VALUE / DESTINATION_READ_BYTES_PER_PIXEL + 1L)
        runCatching { Math.multiplyExact(pixelCount, DESTINATION_READ_BYTES_PER_PIXEL) }
            .getOrDefault(Long.MAX_VALUE)
    }

private fun Set<String>.canonicalUsageLabels(): List<String> =
    sortedWith(
        compareBy(
            { DESTINATION_READ_USAGE_ORDER.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } },
            { it },
        ),
    )

private fun GPUDestinationReadRequirement.dumpLabel(): String =
    when (this) {
        GPUDestinationReadRequirement.None -> "None"
        GPUDestinationReadRequirement.FixedFunctionBlend -> "FixedFunctionOnly"
        GPUDestinationReadRequirement.TargetCopy -> "ShaderBlend"
        GPUDestinationReadRequirement.ExistingIntermediate -> "ExistingIntermediate"
        GPUDestinationReadRequirement.LayerIsolation -> "LayerComposite"
        GPUDestinationReadRequirement.Refused -> "Unknown"
    }

private fun GPUDestinationReadStrategy.dumpLabel(): String =
    when (this) {
        GPUDestinationReadStrategy.None -> "NoDestinationRead"
        GPUDestinationReadStrategy.FixedFunction -> "FixedFunctionAttachmentBlend"
        GPUDestinationReadStrategy.CopyTarget -> "TargetCopySnapshot"
        GPUDestinationReadStrategy.BindIntermediate -> "SampleExistingIntermediate"
        GPUDestinationReadStrategy.IsolateLayer -> "LayerCompositeIsolation"
        GPUDestinationReadStrategy.Refuse -> "RefuseDiagnostic"
    }

private fun GPUDestinationReadBounds.withCommand(commandId: String): GPUDestinationReadBounds =
    copy(boundsLabel = "$commandId|$boundsLabel")

private fun GPUDestinationReadBounds.commandLabel(): String =
    boundsLabel.substringBefore("|")

private fun String.toStableLabel(): String =
    replace(Regex("[^A-Za-z0-9]+"), "-").trim('-')

private fun stableHash(parts: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = parts.joinToString(separator = "\u001F").toByteArray()
    return digest.digest(bytes)
        .take(8)
        .joinToString("") { byte -> "%02x".format(byte) }
}
