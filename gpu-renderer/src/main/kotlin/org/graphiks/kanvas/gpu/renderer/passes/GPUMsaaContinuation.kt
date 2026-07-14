package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity

/** Exact handle-free identity required to continue one stored MSAA attachment set. */
data class GPUSampleContinuationKey(
    val target: GPUTargetIdentity,
    val targetGeneration: Long,
    val deviceGeneration: GPUDeviceGenerationID,
    val colorFormat: GPUColorFormat,
    val colorInterpretation: GPUColorInterpretation,
    val samplePlan: GPUSamplePlan.MultisampleFrame,
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

/** Store/resolve behavior at the end of one render-pass segment. */
enum class GPUSampleEndTransition {
    StoreForContinuation,
    Resolve,
    Discard,
}

/** Proof emitted only when both attachments were stored for a later pass break. */
class GPUSampleStoredState internal constructor(
    val key: GPUSampleContinuationKey,
)

/** Immutable request for one MSAA pass-segment transition. */
data class GPUSampleContinuationRequest(
    val key: GPUSampleContinuationKey,
    val loadTransition: GPUSampleLoadTransition,
    val endTransition: GPUSampleEndTransition,
    val storedState: GPUSampleStoredState? = null,
)

/** Pure accepted MSAA continuation transition. */
data class GPUSampleContinuationPlan(
    val key: GPUSampleContinuationKey,
    val loadTransition: GPUSampleLoadTransition,
    val endTransition: GPUSampleEndTransition,
    val storedState: GPUSampleStoredState?,
)

/** Result of validating and planning one MSAA attachment transition. */
sealed interface GPUSampleContinuationResult {
    data class Accepted(val plan: GPUSampleContinuationPlan) : GPUSampleContinuationResult
    data class Refused(val diagnostic: GPUDiagnostic) : GPUSampleContinuationResult
}

/** Validates exact attachment continuity without observing or producing GPU handles. */
class GPUSampleContinuationPlanner {
    fun plan(request: GPUSampleContinuationRequest): GPUSampleContinuationResult {
        val diagnostic = request.validationDiagnostic()
        if (diagnostic != null) return GPUSampleContinuationResult.Refused(diagnostic)

        val nextStoredState = when (request.endTransition) {
            GPUSampleEndTransition.StoreForContinuation -> GPUSampleStoredState(request.key)
            GPUSampleEndTransition.Resolve,
            GPUSampleEndTransition.Discard,
            -> null
        }
        return GPUSampleContinuationResult.Accepted(
            GPUSampleContinuationPlan(
                key = request.key,
                loadTransition = request.loadTransition,
                endTransition = request.endTransition,
                storedState = nextStoredState,
            ),
        )
    }
}

private fun GPUSampleContinuationRequest.validationDiagnostic(): GPUDiagnostic? {
    if (loadTransition == GPUSampleLoadTransition.FreshClear) {
        return if (storedState == null) null else refusal(
            code = "unsupported.msaa.continuation_fresh_clear_with_stored_state",
            message = "Fresh-clear MSAA transitions cannot consume retained attachment state.",
        )
    }

    val storedKey = storedState?.key ?: return refusal(
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
