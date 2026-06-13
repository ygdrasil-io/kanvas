package org.graphiks.kanvas.gpu.renderer.execution

/** Device-generation marker used to invalidate stale resources. */
@JvmInline
value class GPUDeviceGeneration(val value: Long) {
    init {
        require(value >= 0L) { "GPUDeviceGeneration.value must be non-negative" }
    }
}

/** Readback request descriptor. */
data class GPUReadbackRequest(
    val requestId: String,
    val sourceLabel: String,
    val boundsLabel: String,
    val format: String,
)

/** Command scope used while encoding GPU work. */
sealed interface GPUCommandScope {
    /** Render pass command-encoding scope. */
    data class Render(val label: String, val useTokenLabels: List<String>) : GPUCommandScope

    /** Compute pass command-encoding scope. */
    data class Compute(val label: String, val useTokenLabels: List<String>) : GPUCommandScope

    /** Copy or upload command-encoding scope. */
    data class CopyUpload(val label: String, val useTokenLabels: List<String>) : GPUCommandScope

    /** Readback command-encoding scope. */
    data class Readback(val request: GPUReadbackRequest) : GPUCommandScope
}

/** Submission record for encoded GPU commands. */
sealed interface GPUCommandSubmission {
    /** Commands were submitted to the facade. */
    data class Submitted(
        val submissionId: String,
        val scopeLabel: String,
        val deviceGeneration: GPUDeviceGeneration,
        val diagnostics: List<GPUExecutionDiagnostic> = emptyList(),
    ) : GPUCommandSubmission

    /** Commands were refused before submission. */
    data class Refused(val diagnostic: GPUExecutionDiagnostic) : GPUCommandSubmission

    /** Commands failed during submission. */
    data class Failed(val diagnostic: GPUExecutionDiagnostic) : GPUCommandSubmission
}

/** Readback result descriptor. */
sealed interface GPUReadbackResult {
    /** Readback completed and produced a typed payload descriptor. */
    data class Completed(
        val request: GPUReadbackRequest,
        val payloadHash: String,
        val byteCount: Long,
        val diagnostics: List<GPUExecutionDiagnostic> = emptyList(),
    ) : GPUReadbackResult

    /** Readback was skipped by an explicit policy. */
    data class Skipped(val request: GPUReadbackRequest, val reasonCode: String) : GPUReadbackResult

    /** Readback was refused before backend work. */
    data class Refused(val request: GPUReadbackRequest, val diagnostic: GPUExecutionDiagnostic) : GPUReadbackResult
}

/** Execution context that owns access to the selected GPU facade. */
interface GPUExecutionContext {
    /** Current device generation used for stale resource detection. */
    val deviceGeneration: GPUDeviceGeneration

    /** Submits one encoded command scope to the GPU facade. */
    fun submit(scope: GPUCommandScope): GPUCommandSubmission = TODO("Wire GPUExecutionContext.submit to the GPU facade command queue")

    /** Reads back a typed request through the GPU facade. */
    fun readback(request: GPUReadbackRequest): GPUReadbackResult = TODO("Wire GPUExecutionContext.readback to the GPU facade readback path")
}

/** Surface or offscreen target selected for execution. */
data class GPUSurfaceTarget(
    val targetId: String,
    val descriptor: GPUSurfaceTargetDescriptor,
    val deviceGeneration: GPUDeviceGeneration,
)

/** Descriptor for a surface target before acquiring frame resources. */
data class GPUSurfaceTargetDescriptor(
    val width: Int,
    val height: Int,
    val colorFormat: String,
    val surfaceBacked: Boolean,
)

/** Frame submission record including target and device-generation facts. */
data class GPUFrameSubmission(
    val frameId: String,
    val target: GPUSurfaceTarget,
    val commandSubmissions: List<GPUCommandSubmission>,
    val diagnostics: List<GPUExecutionDiagnostic> = emptyList(),
)

/** Diagnostic emitted by execution or submission. */
data class GPUExecutionDiagnostic(
    val code: String,
    val stage: String,
    val message: String,
    val terminal: Boolean,
)
