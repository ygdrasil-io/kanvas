package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity

/** Explicit owner of the native MSAA attachment named by one continuation key. */
enum class GPUSampleAttachmentAuthority {
    SceneTargetRetained,
    PreparedFramePayload,
}

/** Exact handle-free identity required to continue one stored MSAA attachment set. */
data class GPUSampleContinuationKey(
    val target: GPUTargetIdentity,
    val targetGeneration: Long,
    val deviceGeneration: GPUDeviceGenerationID,
    val colorFormat: GPUColorFormat,
    val colorInterpretation: GPUColorInterpretation,
    val samplePlan: GPUSamplePlan.MultisampleFrame,
    val attachmentAuthority: GPUSampleAttachmentAuthority,
    val colorAttachment: GPUTargetIdentity,
    val depthStencilAttachment: GPUTargetIdentity?,
) {
    init {
        require(targetGeneration >= 0L) {
            "GPUSampleContinuationKey.targetGeneration must be non-negative"
        }
    }
}

/** Load behavior at the start of one render-pass segment. */
enum class GPUSampleLoadTransition {
    FreshClear,
    RetainedLoad,
}

/** Independent attachment store action at the end of one render-pass segment. */
enum class GPUSampleStoreAction {
    Store,
    Discard,
}

/** Independent canonical-target resolve action at the end of one render-pass segment. */
enum class GPUSampleResolveAction {
    ResolveCanonical,
    Skip,
}

/** Immutable request for one MSAA pass-segment transition. */
data class GPUSampleContinuationRequest(
    val key: GPUSampleContinuationKey,
    val loadTransition: GPUSampleLoadTransition,
    val storeAction: GPUSampleStoreAction,
    val resolveAction: GPUSampleResolveAction,
)

/** Complete ordered sequence over which retained attachment authority is evaluated. */
data class GPUSampleContinuationSequenceRequest(
    val transitions: List<GPUSampleContinuationRequest>,
) {
    init {
        require(transitions.isNotEmpty()) {
            "GPUSampleContinuationSequenceRequest.transitions must not be empty"
        }
    }
}

/** One accepted step in an ordered MSAA continuation sequence. */
data class GPUSampleContinuationTransitionPlan(
    val key: GPUSampleContinuationKey,
    val loadTransition: GPUSampleLoadTransition,
    val storeAction: GPUSampleStoreAction,
    val resolveAction: GPUSampleResolveAction,
    val storedForNextTransition: Boolean,
)

/** Pure accepted sequence with no caller-replayable continuation proof. */
data class GPUSampleContinuationPlan(
    val transitions: List<GPUSampleContinuationTransitionPlan>,
    val finalStoredKey: GPUSampleContinuationKey?,
)

/** Result of validating and planning one ordered MSAA attachment sequence. */
sealed interface GPUSampleContinuationResult {
    data class Accepted(val plan: GPUSampleContinuationPlan) : GPUSampleContinuationResult
    data class Refused(
        val transitionIndex: Int,
        val diagnostic: GPUDiagnostic,
    ) : GPUSampleContinuationResult
}

/** Validates one complete ordered sequence without observing or producing GPU handles. */
class GPUSampleContinuationPlanner {
    fun plan(request: GPUSampleContinuationSequenceRequest): GPUSampleContinuationResult {
        var currentStoredKey: GPUSampleContinuationKey? = null
        val transitions = ArrayList<GPUSampleContinuationTransitionPlan>(request.transitions.size)
        request.transitions.forEachIndexed { index, transition ->
            val diagnostic = transition.validationDiagnostic(currentStoredKey)
            if (diagnostic != null) {
                return GPUSampleContinuationResult.Refused(
                    transitionIndex = index,
                    diagnostic = diagnostic,
                )
            }

            currentStoredKey = when (transition.storeAction) {
                GPUSampleStoreAction.Store -> transition.key
                GPUSampleStoreAction.Discard -> null
            }
            transitions += GPUSampleContinuationTransitionPlan(
                key = transition.key,
                loadTransition = transition.loadTransition,
                storeAction = transition.storeAction,
                resolveAction = transition.resolveAction,
                storedForNextTransition = currentStoredKey != null,
            )
        }
        return GPUSampleContinuationResult.Accepted(
            GPUSampleContinuationPlan(
                transitions = transitions,
                finalStoredKey = currentStoredKey,
            ),
        )
    }
}

private fun GPUSampleContinuationRequest.validationDiagnostic(
    currentStoredKey: GPUSampleContinuationKey?,
): GPUDiagnostic? {
    if (resolveAction != GPUSampleResolveAction.ResolveCanonical) {
        return refusal(
            code = "unsupported.msaa.continuation_resolve_missing",
            message = "Every producing MSAA pass must resolve the canonical target.",
        )
    }
    if (loadTransition == GPUSampleLoadTransition.FreshClear) {
        return if (currentStoredKey == null) null else refusal(
            code = "unsupported.msaa.continuation_fresh_clear_with_stored_state",
            message = "Fresh-clear MSAA transitions cannot consume retained attachment state.",
        )
    }

    val storedKey = currentStoredKey ?: return refusal(
        code = "unsupported.msaa.continuation_attachment_not_stored",
        message = "Retained-load MSAA transitions require previously stored attachments.",
    )
    return when {
        storedKey.target.value != key.target.value -> refusal(
            "unsupported.msaa.continuation_target_identity",
            "Retained-load target identity does not match the stored MSAA target.",
        )
        storedKey.targetGeneration != key.targetGeneration -> refusal(
            "unsupported.msaa.continuation_target_generation",
            "Retained-load target generation does not match the stored MSAA target generation.",
        )
        storedKey.deviceGeneration != key.deviceGeneration -> refusal(
            "unsupported.msaa.continuation_device_generation",
            "Retained-load device generation does not match the stored MSAA device generation.",
        )
        storedKey.attachmentAuthority != key.attachmentAuthority -> refusal(
            "unsupported.msaa.continuation_attachment_authority",
            "Retained-load requires the same MSAA attachment ownership authority.",
        )
        storedKey.colorAttachment != key.colorAttachment ||
            storedKey.depthStencilAttachment != key.depthStencilAttachment -> refusal(
            "unsupported.msaa.continuation_attachment_mismatch",
            "Retained-load requires the same stored MSAA color and depth-stencil attachments.",
        )
        storedKey.samplePlan != key.samplePlan -> refusal(
            "unsupported.msaa.continuation_sample_plan",
            "Retained-load sample plan does not match the stored MSAA sample plan.",
        )
        storedKey.colorFormat != key.colorFormat ||
            storedKey.colorInterpretation != key.colorInterpretation -> refusal(
            "unsupported.msaa.continuation_color_contract",
            "Retained-load color format or interpretation does not match stored MSAA state.",
        )
        else -> null
    }
}

private fun refusal(code: String, message: String): GPUDiagnostic = GPUDiagnostic(
    code = GPUDiagnosticCode(code),
    domain = GPUDiagnosticDomain.Passes,
    severity = GPUDiagnosticSeverity.Error,
    message = message,
)
