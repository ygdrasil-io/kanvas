package org.graphiks.kanvas.gpu.renderer.state

import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadAction
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadRequirement
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategy
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategyGatePlan
import java.security.MessageDigest

/** Stable blend-mode identity used by blend planning. */
enum class GPUBlendMode {
    /** Source replaces destination subject to alpha plan. */
    Src,
    /** Source-over Porter-Duff composition. */
    SrcOver,
    /** Destination-over Porter-Duff composition. */
    DstOver,
    /** Multiply blend. */
    Multiply,
    /** Screen blend. */
    Screen,
    /** Unsupported or deferred custom mode. */
    Custom,
}

/** Store behavior for target output. */
enum class GPUStorePlan {
    /** Preserve attachment contents after the pass. */
    Store,
    /** Discard attachment contents after the pass. */
    Discard,
    /** Resolve multisample content and store resolved output. */
    ResolveAndStore,
}

/** Descriptor for a render target texture. */
data class GPUTargetTextureDescriptor(
    val width: Int,
    val height: Int,
    val colorFormat: String,
    val usageLabels: Set<String>,
    val isSurfaceBacked: Boolean,
) {
    init {
        require(width > 0) { "GPUTargetTextureDescriptor.width must be positive" }
        require(height > 0) { "GPUTargetTextureDescriptor.height must be positive" }
        require(colorFormat.isNotBlank()) { "GPUTargetTextureDescriptor.colorFormat must not be blank" }
    }
}

/** Alpha-domain and premul handling plan. */
data class GPUAlphaPlan(
    val inputAlpha: String,
    val outputAlpha: String,
    val premultiply: Boolean,
    val clamp: Boolean,
)

/** Blend plan chosen before pipeline-key construction. */
data class GPUBlendPlan(
    val mode: GPUBlendMode,
    val requiresDestinationRead: Boolean,
    val pipelineBlendStateKey: String,
    val unsupportedReasonCode: String? = null,
)

/** Blend allowlist request before pipeline-key or backend materialization. */
data class GPUBlendAllowlistRequest(
    val commandId: String,
    val mode: GPUBlendMode,
    val targetFormatClass: String,
    val materialKeyHash: String,
    val renderStepIdentity: String,
    val alphaPlan: GPUAlphaPlan = GPUAlphaPlan(
        inputAlpha = "premultiplied",
        outputAlpha = "premultiplied",
        premultiply = false,
        clamp = true,
    ),
    val destinationReadPlan: GPUDestinationReadStrategyGatePlan? = null,
    val destinationReadCopyBoundsLabel: String? = null,
    val destinationReadGeneration: Long? = null,
    val activeAttachmentSampled: Boolean = false,
) {
    init {
        require(commandId.isNotBlank()) { "GPUBlendAllowlistRequest.commandId must not be blank" }
        require(targetFormatClass.isNotBlank()) { "GPUBlendAllowlistRequest.targetFormatClass must not be blank" }
        require(materialKeyHash.isNotBlank()) { "GPUBlendAllowlistRequest.materialKeyHash must not be blank" }
        require(renderStepIdentity.isNotBlank()) { "GPUBlendAllowlistRequest.renderStepIdentity must not be blank" }
        val acceptedDestinationReadPlan = destinationReadPlan?.diagnostics?.none { it.terminal } == true
        require(!acceptedDestinationReadPlan || !destinationReadCopyBoundsLabel.isNullOrBlank()) {
            "GPUBlendAllowlistRequest.destinationReadCopyBoundsLabel is required when destinationReadPlan is set"
        }
        require(!acceptedDestinationReadPlan || destinationReadGeneration != null) {
            "GPUBlendAllowlistRequest.destinationReadGeneration is required when destinationReadPlan is set"
        }
        require(destinationReadGeneration == null || destinationReadGeneration >= 0) {
            "GPUBlendAllowlistRequest.destinationReadGeneration must be non-negative"
        }
    }
}

/** Blend plan kind used by allowlist evidence dumps. */
enum class GPUBlendPlanKind {
    /** WebGPU attachment blend state can represent the mode without sampling destination. */
    FixedFunctionBlend,
    /** Shader blend would need a validated destination-read strategy. */
    ShaderBlendWithDstRead,
    /** Unsupported blend mode outside the current allowlist. */
    UnsupportedBlend,
}

/** Fixed-function attachment blend state chosen for an allowlisted mode. */
data class GPUFixedFunctionBlendState(
    val mode: GPUBlendMode,
    val colorSrcFactor: String,
    val colorDstFactor: String,
    val colorOperation: String,
    val alphaSrcFactor: String,
    val alphaDstFactor: String,
    val alphaOperation: String,
    val writeMask: String,
) {
    /** Returns the canonical fixed-function blend state dump. */
    fun dumpLine(): String =
        "blend:fixed-function mode=$mode " +
            "color=src=$colorSrcFactor,dst=$colorDstFactor,op=$colorOperation " +
            "alpha=src=$alphaSrcFactor,dst=$alphaDstFactor,op=$alphaOperation " +
            "writeMask=$writeMask destinationRead=FixedFunctionAttachmentBlend"
}

/** Diagnostic emitted by the blend allowlist planner. */
data class GPUBlendDiagnostic(
    val code: String,
    val mode: GPUBlendMode,
    val message: String,
    val terminal: Boolean,
)

/** Evidence result for the blend allowlist gate. */
data class GPUBlendAllowlistGatePlan(
    val commandId: String,
    val evidenceRow: String,
    val routeKind: String,
    val classification: String,
    val promoted: Boolean,
    val productActivation: Boolean,
    val materialized: Boolean,
    val targetFormatClass: String,
    val materialKeyHash: String,
    val renderStepIdentity: String,
    val alphaPlan: GPUAlphaPlan,
    val planKind: GPUBlendPlanKind,
    val plan: GPUBlendPlan,
    val fixedFunctionState: GPUFixedFunctionBlendState?,
    val destinationReadRequirement: GPUDestinationReadRequirement,
    val destinationReadStrategy: GPUDestinationReadStrategy,
    val destinationReadAction: GPUDestinationReadAction,
    val citedDestinationReadPlanRef: String?,
    val citedDestinationReadPlanStrategy: GPUDestinationReadStrategy?,
    val activeAttachmentSampled: Boolean,
    val pipelineKeyHash: String,
    val diagnostics: List<GPUBlendDiagnostic>,
) {
    val blendStateHash: String
        get() = plan.pipelineBlendStateKey

    val pipelineBlendStateKey: String
        get() = plan.pipelineBlendStateKey

    /** Returns the canonical dump lines for blend allowlist evidence. */
    fun dumpLines(): List<String> {
        val terminal = diagnostics.singleOrNull { it.terminal }
        if (terminal != null) {
            return listOf(
                "blend:allowlist.refused row=$evidenceRow routeKind=$routeKind " +
                    "classification=$classification promoted=$promoted productActivation=$productActivation " +
                    "materialized=$materialized command=$commandId mode=${plan.mode} plan=$planKind " +
                    "reason=${terminal.code} target=$targetFormatClass",
                destinationReadLine(),
                BLEND_ALLOWLIST_NONCLAIM_LINE,
            )
        }

        val diagnostic = diagnostics.single()
        val stateLine = fixedFunctionState?.dumpLine()
            ?: "blend:shader mode=${plan.mode} destinationRead=TextureCopy route=shaderBlend"
        return listOf(
            "blend:allowlist row=$evidenceRow routeKind=$routeKind classification=$classification " +
                "promoted=$promoted productActivation=$productActivation materialized=$materialized " +
                "command=$commandId mode=${plan.mode} plan=$planKind target=$targetFormatClass " +
                "state=$blendStateHash pipeline=$pipelineBlendStateKey",
            alphaPlan.dumpLine(),
            stateLine,
            "blend:pipeline-key material=$materialKeyHash renderStep=$renderStepIdentity " +
                "blendState=$blendStateHash pipelineKey=$pipelineKeyHash",
            "blend:diagnostic code=${diagnostic.code} terminal=${diagnostic.terminal} mode=${diagnostic.mode}",
            if (planKind == GPUBlendPlanKind.ShaderBlendWithDstRead) {
                "blend:nonclaim nativeAdvancedBlend=false shaderBlend=true framebufferFetch=false inputAttachment=false destinationReadTexture=true productActivation=true"
            } else {
                BLEND_ALLOWLIST_NONCLAIM_LINE
            },
        )
    }

    private fun destinationReadLine(): String =
        "blend:destination-read mode=${plan.mode} requirement=${destinationReadRequirement.dumpLabel()} " +
            "strategy=${destinationReadStrategy.dumpLabel()} action=$destinationReadAction " +
            "plan=${citedDestinationReadPlanRef ?: "missing"} " +
            "planStrategy=${citedDestinationReadPlanStrategy?.dumpLabel() ?: "none"} " +
            "activeAttachmentSampled=$activeAttachmentSampled"
}

/** Planner for contract-only blend allowlist and destination-read refusal evidence. */
class GPUBlendAllowlistPlanner {
    /** Plans a fixed-function allowlisted blend or a stable refusal. */
    fun plan(request: GPUBlendAllowlistRequest): GPUBlendAllowlistGatePlan {
        val targetRefusalCode = request.targetFormatRefusalCode()
        if (targetRefusalCode != null) {
            return refusedPlan(
                request = request,
                planKind = request.mode.planKind(),
                destinationReadRequirement = request.mode.destinationReadRequirement(),
                destinationReadStrategy = GPUDestinationReadStrategy.Refuse,
                destinationReadAction = GPUDestinationReadAction.Refuse,
                reasonCode = targetRefusalCode,
            )
        }

        if (request.activeAttachmentSampled) {
            return refusedPlan(
                request = request,
                planKind = request.mode.planKind(),
                destinationReadRequirement = request.mode.destinationReadRequirement(),
                destinationReadStrategy = GPUDestinationReadStrategy.Refuse,
                destinationReadAction = GPUDestinationReadAction.Refuse,
                reasonCode = "unsupported.destination_read.active_attachment_sampled",
            )
        }

        val alphaRefusalCode = request.alphaRefusalCode()
        if (alphaRefusalCode != null) {
            return refusedPlan(
                request = request,
                planKind = request.mode.planKind(),
                destinationReadRequirement = request.mode.destinationReadRequirement(),
                destinationReadStrategy = GPUDestinationReadStrategy.Refuse,
                destinationReadAction = GPUDestinationReadAction.Refuse,
                reasonCode = alphaRefusalCode,
            )
        }

        val fixedFunctionState = request.mode.fixedFunctionState()
        if (fixedFunctionState != null) {
            return acceptedFixedFunctionPlan(request, fixedFunctionState)
        }

        val planKind = request.mode.planKind()
        val reasonCode = request.refusalCode()
        if (planKind == GPUBlendPlanKind.ShaderBlendWithDstRead && reasonCode == "unsupported.blend.shader_route_unvalidated") {
            return acceptedShaderBlendPlan(request)
        }

        return refusedPlan(
            request = request,
            planKind = planKind,
            destinationReadRequirement = request.mode.destinationReadRequirement(),
            destinationReadStrategy = GPUDestinationReadStrategy.Refuse,
            destinationReadAction = GPUDestinationReadAction.Refuse,
            reasonCode = reasonCode,
        )
    }

    private fun acceptedFixedFunctionPlan(
        request: GPUBlendAllowlistRequest,
        fixedFunctionState: GPUFixedFunctionBlendState,
    ): GPUBlendAllowlistGatePlan {
        val blendStateHash = fixedFunctionState.blendStateHash(request.targetFormatClass, request.alphaPlan)
        val plan = GPUBlendPlan(
            mode = request.mode,
            requiresDestinationRead = false,
            pipelineBlendStateKey = blendStateHash,
        )
        val pipelineKeyHash = pipelineKeyHash(request, blendStateHash)
        val diagnostic = GPUBlendDiagnostic(
            code = BLEND_ALLOWLIST_ACCEPTED_CODE,
            mode = request.mode,
            message = "blend allowlist accepted fixed-function mode ${request.mode}",
            terminal = false,
        )
        return GPUBlendAllowlistGatePlan(
            commandId = request.commandId,
            evidenceRow = BLEND_ALLOWLIST_EVIDENCE_ROW,
            routeKind = "GPUNative",
            classification = "TargetNative",
            promoted = false,
            productActivation = true,
            materialized = false,
            targetFormatClass = request.targetFormatClass,
            materialKeyHash = request.materialKeyHash,
            renderStepIdentity = request.renderStepIdentity,
            alphaPlan = request.alphaPlan,
            planKind = GPUBlendPlanKind.FixedFunctionBlend,
            plan = plan,
            fixedFunctionState = fixedFunctionState,
            destinationReadRequirement = GPUDestinationReadRequirement.FixedFunctionBlend,
            destinationReadStrategy = GPUDestinationReadStrategy.FixedFunction,
            destinationReadAction = GPUDestinationReadAction.UseFixedFunctionBlend,
            citedDestinationReadPlanRef = null,
            citedDestinationReadPlanStrategy = null,
            activeAttachmentSampled = request.activeAttachmentSampled,
            pipelineKeyHash = pipelineKeyHash,
            diagnostics = listOf(diagnostic),
        )
    }

    private fun acceptedShaderBlendPlan(request: GPUBlendAllowlistRequest): GPUBlendAllowlistGatePlan {
        val destinationReadPlan = requireNotNull(request.destinationReadPlan) {
            "accepted shader blend requires destinationReadPlan"
        }
        val blendStateHash = "shader-blend:${request.mode}:${request.targetFormatClass}:${destinationReadPlan.copyDescriptorHash}"
        val plan = GPUBlendPlan(
            mode = request.mode,
            requiresDestinationRead = true,
            pipelineBlendStateKey = blendStateHash,
        )
        val diagnostic = GPUBlendDiagnostic(
            code = "accepted.blend.shader_destination_read",
            mode = request.mode,
            message = "blend allowlist accepted shader destination-read mode ${request.mode}",
            terminal = false,
        )
        return GPUBlendAllowlistGatePlan(
            commandId = request.commandId,
            evidenceRow = BLEND_ALLOWLIST_EVIDENCE_ROW,
            routeKind = "GPUNative",
            classification = "TargetNative",
            promoted = false,
            productActivation = true,
            materialized = false,
            targetFormatClass = request.targetFormatClass,
            materialKeyHash = request.materialKeyHash,
            renderStepIdentity = request.renderStepIdentity,
            alphaPlan = request.alphaPlan,
            planKind = GPUBlendPlanKind.ShaderBlendWithDstRead,
            plan = plan,
            fixedFunctionState = null,
            destinationReadRequirement = request.mode.destinationReadRequirement(),
            destinationReadStrategy = destinationReadPlan.plan.strategy,
            destinationReadAction = destinationReadPlan.action,
            citedDestinationReadPlanRef = destinationReadPlan.planRef(),
            citedDestinationReadPlanStrategy = destinationReadPlan.plan.strategy,
            activeAttachmentSampled = request.activeAttachmentSampled,
            pipelineKeyHash = pipelineKeyHash(request, blendStateHash),
            diagnostics = listOf(diagnostic),
        )
    }

    private fun refusedPlan(
        request: GPUBlendAllowlistRequest,
        planKind: GPUBlendPlanKind,
        destinationReadRequirement: GPUDestinationReadRequirement,
        destinationReadStrategy: GPUDestinationReadStrategy,
        destinationReadAction: GPUDestinationReadAction,
        reasonCode: String,
    ): GPUBlendAllowlistGatePlan {
        val plan = GPUBlendPlan(
            mode = request.mode,
            requiresDestinationRead = planKind == GPUBlendPlanKind.ShaderBlendWithDstRead,
            pipelineBlendStateKey = "none",
            unsupportedReasonCode = reasonCode,
        )
        val diagnostic = GPUBlendDiagnostic(
            code = reasonCode,
            mode = request.mode,
            message = "blend allowlist refused mode ${request.mode}: $reasonCode",
            terminal = true,
        )
        return GPUBlendAllowlistGatePlan(
            commandId = request.commandId,
            evidenceRow = BLEND_ALLOWLIST_EVIDENCE_ROW,
            routeKind = "RefuseDiagnostic",
            classification = "TargetNative",
            promoted = false,
            productActivation = true,
            materialized = false,
            targetFormatClass = request.targetFormatClass,
            materialKeyHash = request.materialKeyHash,
            renderStepIdentity = request.renderStepIdentity,
            alphaPlan = request.alphaPlan,
            planKind = planKind,
            plan = plan,
            fixedFunctionState = null,
            destinationReadRequirement = destinationReadRequirement,
            destinationReadStrategy = destinationReadStrategy,
            destinationReadAction = destinationReadAction,
            citedDestinationReadPlanRef = request.destinationReadPlan?.planRef(),
            citedDestinationReadPlanStrategy = request.destinationReadPlan?.plan?.strategy,
            activeAttachmentSampled = request.activeAttachmentSampled,
            pipelineKeyHash = "none",
            diagnostics = listOf(diagnostic),
        )
    }
}

/** Sample count or coverage sample-state contract. */
data class GPUSampleState(
    val sampleCount: Int,
    val coverageSampleCount: Int,
    val alphaToCoverage: Boolean,
) {
    init {
        require(sampleCount > 0) { "GPUSampleState.sampleCount must be positive" }
        require(coverageSampleCount > 0) { "GPUSampleState.coverageSampleCount must be positive" }
    }
}

/** Load/store behavior for a render or layer target. */
data class GPULoadStorePlan(
    val loadOp: String,
    val storePlan: GPUStorePlan,
    val clearColorLabel: String? = null,
)

/** Target attachment and store assumptions for a render route. */
data class GPUTargetState(
    val target: GPUTargetTextureDescriptor,
    val loadStore: GPULoadStorePlan,
    val blend: GPUBlendPlan,
    val alpha: GPUAlphaPlan,
    val sampleState: GPUSampleState,
)

private const val BLEND_ALLOWLIST_EVIDENCE_ROW = "gpu-renderer.blend-allowlist"
private const val BLEND_ALLOWLIST_ACCEPTED_CODE = "accepted.blend.fixed_function"
private const val BLEND_ALLOWLIST_NONCLAIM_LINE =
    "blend:nonclaim nativeAdvancedBlend=false shaderBlend=false framebufferFetch=false inputAttachment=false " +
        "destinationReadTexture=false productActivation=true"

private val BLEND_ALLOWLIST_TARGET_FORMATS = setOf("rgba8unorm", "bgra8unorm")

private fun GPUBlendAllowlistRequest.targetFormatRefusalCode(): String? =
    when (targetFormatClass) {
        in BLEND_ALLOWLIST_TARGET_FORMATS -> null
        else -> "unsupported.target.format_blend_incompatible"
    }

private fun GPUBlendAllowlistRequest.alphaRefusalCode(): String? =
    when {
        mode.planKind() != GPUBlendPlanKind.FixedFunctionBlend -> null
        alphaPlan.isAcceptedFixedFunctionAlphaPlan() -> null
        else -> "unsupported.blend.alpha_plan_unaccepted"
    }

private fun GPUBlendAllowlistRequest.refusalCode(): String =
    when {
        activeAttachmentSampled -> "unsupported.destination_read.active_attachment_sampled"
        mode.requiresShaderDestinationRead() && destinationReadPlan == null ->
            "unsupported.blend.dst_read_requires_intermediate"
        mode.requiresShaderDestinationRead() && destinationReadPlan?.diagnostics?.any { it.terminal } == true ->
            "unsupported.blend.dst_read_requires_intermediate"
        mode.requiresShaderDestinationRead() && destinationReadPlan?.matchesBlendRequest(this) == false ->
            "unsupported.blend.destination_read_plan_mismatch"
        mode.requiresShaderDestinationRead() -> "unsupported.blend.shader_route_unvalidated"
        else -> "unsupported.blend.mode_unimplemented"
    }

private fun GPUAlphaPlan.isAcceptedFixedFunctionAlphaPlan(): Boolean =
    inputAlpha == "premultiplied" &&
        outputAlpha == "premultiplied" &&
        !premultiply &&
        clamp

private fun GPUAlphaPlan.dumpLine(): String =
    "blend:alpha input=$inputAlpha output=$outputAlpha premultiply=$premultiply clamp=$clamp"

private fun GPUBlendMode.planKind(): GPUBlendPlanKind =
    when (this) {
        GPUBlendMode.Src,
        GPUBlendMode.SrcOver,
        GPUBlendMode.DstOver,
        -> GPUBlendPlanKind.FixedFunctionBlend
        GPUBlendMode.Multiply,
        GPUBlendMode.Screen,
        -> GPUBlendPlanKind.ShaderBlendWithDstRead
        GPUBlendMode.Custom -> GPUBlendPlanKind.UnsupportedBlend
    }

private fun GPUBlendMode.destinationReadRequirement(): GPUDestinationReadRequirement =
    when {
        planKind() == GPUBlendPlanKind.FixedFunctionBlend -> GPUDestinationReadRequirement.FixedFunctionBlend
        requiresShaderDestinationRead() -> GPUDestinationReadRequirement.TargetCopy
        else -> GPUDestinationReadRequirement.Refused
    }

private fun GPUBlendMode.requiresShaderDestinationRead(): Boolean =
    this == GPUBlendMode.Multiply || this == GPUBlendMode.Screen

private fun GPUBlendMode.fixedFunctionState(): GPUFixedFunctionBlendState? =
    when (this) {
        GPUBlendMode.Src -> GPUFixedFunctionBlendState(
            mode = this,
            colorSrcFactor = "one",
            colorDstFactor = "zero",
            colorOperation = "add",
            alphaSrcFactor = "one",
            alphaDstFactor = "zero",
            alphaOperation = "add",
            writeMask = "rgba",
        )
        GPUBlendMode.SrcOver -> GPUFixedFunctionBlendState(
            mode = this,
            colorSrcFactor = "one",
            colorDstFactor = "one-minus-src-alpha",
            colorOperation = "add",
            alphaSrcFactor = "one",
            alphaDstFactor = "one-minus-src-alpha",
            alphaOperation = "add",
            writeMask = "rgba",
        )
        GPUBlendMode.DstOver -> GPUFixedFunctionBlendState(
            mode = this,
            colorSrcFactor = "one-minus-dst-alpha",
            colorDstFactor = "one",
            colorOperation = "add",
            alphaSrcFactor = "one-minus-dst-alpha",
            alphaDstFactor = "one",
            alphaOperation = "add",
            writeMask = "rgba",
        )
        GPUBlendMode.Multiply,
        GPUBlendMode.Screen,
        GPUBlendMode.Custom,
        -> null
    }

private fun GPUFixedFunctionBlendState.blendStateHash(
    targetFormatClass: String,
    alphaPlan: GPUAlphaPlan,
): String =
    "sha256:" + blendStableHash(
        listOf(
            "blend-fixed-function-v1",
            mode.name,
            targetFormatClass,
            alphaPlan.inputAlpha,
            alphaPlan.outputAlpha,
            alphaPlan.premultiply.toString(),
            alphaPlan.clamp.toString(),
            colorSrcFactor,
            colorDstFactor,
            colorOperation,
            alphaSrcFactor,
            alphaDstFactor,
            alphaOperation,
            writeMask,
        ),
    )

private fun pipelineKeyHash(
    request: GPUBlendAllowlistRequest,
    blendStateHash: String,
): String = "sha256:" + blendStableHash(
    listOf(
        "blend-pipeline-key-v1",
        request.materialKeyHash,
        request.renderStepIdentity,
        request.targetFormatClass,
        blendStateHash,
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

private fun GPUDestinationReadStrategyGatePlan?.planRef(): String =
    if (this == null) {
        "missing"
    } else {
        "$evidenceRow:$label"
    }

private fun GPUDestinationReadStrategyGatePlan?.planStrategyLabel(): String =
    this?.plan?.strategy?.dumpLabel() ?: "none"

private fun GPUDestinationReadStrategyGatePlan.matchesBlendRequest(
    request: GPUBlendAllowlistRequest,
): Boolean {
    val binding = plan.binding ?: return false
    val expectedCopyBounds = requireNotNull(request.destinationReadCopyBoundsLabel)
    val expectedGeneration = requireNotNull(request.destinationReadGeneration)
    val planTargetFormat = plan.sourceTargetFacts.firstOrNull { it.startsWith("targetFormat=") }
        ?.removePrefix("targetFormat=")
    val copyDescriptorFormatMatches = copyDescriptor?.formatClass?.let { it == request.targetFormatClass } ?: true
    return routeKind == "GPUNative" &&
        productActivation &&
        !materialized &&
        diagnostics.none { it.terminal } &&
        plan.requirement in setOf(
            GPUDestinationReadRequirement.TargetCopy,
            GPUDestinationReadRequirement.ExistingIntermediate,
        ) &&
        plan.strategy in setOf(GPUDestinationReadStrategy.CopyTarget, GPUDestinationReadStrategy.BindIntermediate) &&
        plan.bounds.boundsLabel.substringBefore("|") == request.commandId &&
        plan.bounds.copyBoundsLabel == expectedCopyBounds &&
        binding.generation == expectedGeneration &&
        planTargetFormat == request.targetFormatClass &&
        copyDescriptorFormatMatches
}

private fun blendStableHash(parts: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = parts.joinToString(separator = "\u001F").toByteArray(Charsets.UTF_8)
    return digest.digest(bytes)
        .take(8)
        .joinToString("") { byte -> "%02x".format(byte) }
}
