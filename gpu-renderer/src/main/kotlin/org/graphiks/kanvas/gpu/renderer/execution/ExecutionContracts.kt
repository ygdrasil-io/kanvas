package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPass
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketStream
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawPayloadRef
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineCreationPlan
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineKeyPreimage
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest

/** Source bridge for the unchanged Task 9 native runtime; no second runtime type is created. */
typealias GPUDeviceGeneration = GPUDeviceGenerationID

/** Command class requested by a command-encoding scope. */
enum class GPUCommandClass {
    Render,
    Compute,
    CopyUpload,
    Readback,
}

/** Capability facts needed before a command scope can be submitted. */
data class GPUExecutionCapabilities(
    val render: Boolean = false,
    val compute: Boolean = false,
    val copyUpload: Boolean = false,
    val readback: Boolean = false,
) {
    companion object {
        /** Returns a capability set that refuses every backend command class. */
        fun refusing(): GPUExecutionCapabilities = GPUExecutionCapabilities()
    }

    /** Returns true when this context supports the requested command class. */
    fun supports(commandClass: GPUCommandClass): Boolean =
        when (commandClass) {
            GPUCommandClass.Render -> render
            GPUCommandClass.Compute -> compute
            GPUCommandClass.CopyUpload -> copyUpload
            GPUCommandClass.Readback -> readback
        }
}

/** Command scope used while encoding GPU work. */
sealed interface GPUCommandScope {
    /** Stable label used in submission diagnostics and dumps. */
    val scopeLabel: String

    /** Command class required by this scope. */
    val commandClass: GPUCommandClass

    /**
     * Render pass command-encoding scope.
     *
     * [useTokenLabels] is copied at construction so caller-owned mutable lists
     * cannot rewrite validation or dump evidence after the scope enters
     * execution preflight.
     */
    class Render(val label: String, useTokenLabels: List<String>) : GPUCommandScope {
        val useTokenLabels: List<String> = useTokenLabels.toList()
        override val scopeLabel: String get() = label
        override val commandClass: GPUCommandClass get() = GPUCommandClass.Render

        override fun equals(other: Any?): Boolean =
            this === other ||
                other is Render &&
                label == other.label &&
                useTokenLabels == other.useTokenLabels

        override fun hashCode(): Int =
            31 * label.hashCode() + useTokenLabels.hashCode()

        override fun toString(): String =
            "Render(label=$label, useTokenLabels=$useTokenLabels)"
    }

    /**
     * Compute pass command-encoding scope.
     *
     * [useTokenLabels] is copied at construction and then treated as immutable
     * evidence for resource-use ordering.
     */
    class Compute(val label: String, useTokenLabels: List<String>) : GPUCommandScope {
        val useTokenLabels: List<String> = useTokenLabels.toList()
        override val scopeLabel: String get() = label
        override val commandClass: GPUCommandClass get() = GPUCommandClass.Compute

        override fun equals(other: Any?): Boolean =
            this === other ||
                other is Compute &&
                label == other.label &&
                useTokenLabels == other.useTokenLabels

        override fun hashCode(): Int =
            31 * label.hashCode() + useTokenLabels.hashCode()

        override fun toString(): String =
            "Compute(label=$label, useTokenLabels=$useTokenLabels)"
    }

    /**
     * Copy or upload command-encoding scope.
     *
     * [useTokenLabels] is copied at construction so upload/copy evidence remains
     * stable even if the caller mutates its staging collection.
     */
    class CopyUpload(val label: String, useTokenLabels: List<String>) : GPUCommandScope {
        val useTokenLabels: List<String> = useTokenLabels.toList()
        override val scopeLabel: String get() = label
        override val commandClass: GPUCommandClass get() = GPUCommandClass.CopyUpload

        override fun equals(other: Any?): Boolean =
            this === other ||
                other is CopyUpload &&
                label == other.label &&
                useTokenLabels == other.useTokenLabels

        override fun hashCode(): Int =
            31 * label.hashCode() + useTokenLabels.hashCode()

        override fun toString(): String =
            "CopyUpload(label=$label, useTokenLabels=$useTokenLabels)"
    }

    /** Readback command-encoding scope. */
    data class Readback(val request: GPUFrameReadbackRequest) : GPUCommandScope {
        override val scopeLabel: String get() = request.requestId.value
        override val commandClass: GPUCommandClass get() = GPUCommandClass.Readback
    }
}

/**
 * Encoder planning record that bridges pass command streams to a concrete GPU facade.
 *
 * The plan records which packet and pass command streams will be encoded for a scope, which device
 * and target generations they were validated against, and the facade operation classes expected in
 * order. It still owns no backend encoder, command buffer, queue submission, bind group, texture,
 * or Dawn/WGPU object.
 */
class GPUCommandEncoderPlan private constructor(
    val planId: String,
    val contextIdentity: String,
    val deviceGeneration: GPUDeviceGenerationID,
    val targetGeneration: Long,
    scopes: List<GPUCommandEncoderScopePlan>,
) {
    /** The sole Task 8 encoding authority, in semantic step order. */
    val scopes: List<GPUCommandEncoderScopePlan> = immutableList(scopes)

    /** Compatibility projections for established single-render-scope tests; all derive from [scopes]. */
    val commandClass: GPUCommandClass?
        get() = scopes.singleOrNull()?.let { single -> when (single.operationKind) {
            GPUEncoderOperationKind.Render, GPUEncoderOperationKind.SurfaceBlit -> GPUCommandClass.Render
            GPUEncoderOperationKind.Compute -> GPUCommandClass.Compute
            GPUEncoderOperationKind.Readback -> GPUCommandClass.Readback
            else -> GPUCommandClass.CopyUpload
        } }
    val scope: GPUCommandScope?
        get() = scopes.singleOrNull()?.let { single ->
            when (single.operationKind) {
                GPUEncoderOperationKind.Render, GPUEncoderOperationKind.SurfaceBlit ->
                    GPUCommandScope.Render(single.scopeLabel, single.sourceTaskIds.map { it.value })
                GPUEncoderOperationKind.Compute ->
                    GPUCommandScope.Compute(single.scopeLabel, single.sourceTaskIds.map { it.value })
                GPUEncoderOperationKind.Readback -> null
                else -> GPUCommandScope.CopyUpload(
                    single.scopeLabel,
                    single.sourceTaskIds.map { it.value },
                )
            }
        }
    val packetStreamId: String? get() = scopes.singleOrNull()?.passCommandStream?.packetStreamId
    val passCommandStreamId: String? get() = scopes.singleOrNull()?.passCommandStream?.streamId
    val packetCount: Int get() = scopes.singleOrNull()?.sourcePacketIds?.size ?: scopes.sumOf { it.sourcePacketIds.size }
    val passCommandCount: Int get() = scopes.sumOf { it.facadeOperationClasses.size }
    val facadeOperationClasses: List<String> get() = scopes.flatMap { it.facadeOperationClasses }
    val resourceGenerationLabels: List<String> get() = scopes.flatMap { it.resourceGenerationLabels }
    val diagnostics: List<String>
        get() = scopes.flatMap { it.passCommandStream?.diagnostics?.map { diagnostic -> diagnostic.code }.orEmpty() }

    init {
        requireExecutionDumpSafe("GPUCommandEncoderPlan.planId", planId)
        requireExecutionDumpSafe("GPUCommandEncoderPlan.contextIdentity", contextIdentity)
        require(targetGeneration >= 0L) { "GPUCommandEncoderPlan.targetGeneration must be non-negative" }
        require(scopes.map { it.sourceStepIndex }.zipWithNext().all { (a, b) -> a < b }) {
            "GPUCommandEncoderPlan.scopes must be strictly ordered by semantic step"
        }
    }

    companion object {
        /** Builds the Task 8 ordered plan without creating an encoder or backend object. */
        fun ordered(
            planId: String,
            contextIdentity: String,
            deviceGeneration: GPUDeviceGenerationID,
            targetGeneration: Long,
            scopes: List<GPUCommandEncoderScopePlan>,
        ): GPUCommandEncoderPlan {
            return GPUCommandEncoderPlan(
                planId = planId,
                contextIdentity = contextIdentity,
                deviceGeneration = deviceGeneration,
                targetGeneration = targetGeneration,
                scopes = scopes,
            )
        }

        /** Builds a render encoder plan from matching packet and pass command streams. */
        fun fromPassCommandStream(
            planId: String,
            contextIdentity: String,
            deviceGeneration: GPUDeviceGenerationID,
            targetGeneration: Long,
            scope: GPUCommandScope,
            packetStream: GPUDrawPacketStream,
            passCommandStream: GPUPassCommandStream,
            resourceGenerationLabels: List<String> = emptyList(),
        ): GPUCommandEncoderPlan {
            require(scope.commandClass == GPUCommandClass.Render) {
                "GPUPassCommandStream currently lowers only render command scopes"
            }
            require(packetStream.passId == passCommandStream.passId) {
                "Packet stream pass ${packetStream.passId} must match command stream pass ${passCommandStream.passId}"
            }
            require(packetStream.streamId == passCommandStream.packetStreamId) {
                "Packet stream ${packetStream.streamId} must match command stream ${passCommandStream.packetStreamId}"
            }

            return GPUCommandEncoderPlan(
                planId = planId,
                contextIdentity = contextIdentity,
                deviceGeneration = deviceGeneration,
                targetGeneration = targetGeneration,
                scopes = listOf(
                    GPUCommandEncoderScopePlan(
                        sourceStepIndex = 0,
                        operationKind = GPUEncoderOperationKind.Render,
                        scopeLabel = scope.scopeLabel,
                        sourceTaskIds = listOf(org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID("legacy.$planId")),
                        sourcePacketIds = packetStream.packetIds,
                        facadeOperationClasses = passCommandStream.commandLabels,
                        targetGeneration = targetGeneration,
                        resourceGenerationLabels = resourceGenerationLabels,
                        passCommandStream = passCommandStream,
                    ),
                ),
            )
        }
    }
}

/**
 * Emits deterministic encoder-plan evidence before backend command recording.
 *
 * The line names scope, device/target generations, stream IDs, operation classes, resource
 * generation labels, and diagnostics only. It does not imply submission or backend completion.
 */
fun GPUCommandEncoderPlan.dumpLines(): List<String> =
    if (scopes.size == 1) {
        val single = scopes.single()
        val singleClass = commandClass
        listOf(
            "execution.encoder-plan id=$planId " +
                "context=$contextIdentity class=$singleClass scope=${single.scopeLabel} " +
                "deviceGeneration=${deviceGeneration.value} targetGeneration=$targetGeneration " +
                "packetStream=${packetStreamId ?: "none"} passCommandStream=${passCommandStreamId ?: "step.${single.sourceStepIndex}"} " +
                "packets=$packetCount commands=$passCommandCount " +
                "operations=${facadeOperationClasses.dumpSequence()} " +
                "resources=${resourceGenerationLabels.dumpSequence()} diagnostics=${diagnostics.dumpSequence()}",
        )
    } else {
        listOf(
            "execution.encoder-plan id=$planId context=$contextIdentity " +
                "deviceGeneration=${deviceGeneration.value} targetGeneration=$targetGeneration scopes=${scopes.size}",
        ) + scopes.map { scope ->
            "execution.encoder-scope index=${scope.sourceStepIndex} kind=${scope.operationKind} " +
                "tasks=${scope.sourceTaskIds.map { it.value }.dumpSequence()} " +
                "packets=${scope.sourcePacketIds.map { it.value }.dumpSequence()} " +
                "commands=${scope.facadeOperationClasses.size} " +
                "operations=${scope.facadeOperationClasses.dumpSequence()} " +
                "resources=${scope.resourceGenerationLabels.dumpSequence()}"
        }
    }

/**
 * Submission record for encoded GPU commands.
 *
 * Execution owns these records after resource materialization and command
 * encoding. A record is either real submitted evidence, a pre-submit refusal,
 * or a backend submission failure. Refusal-first contexts must keep returning
 * `Refused`; `Submitted` dumps are object evidence only unless a real backend
 * path constructs the object after validation and submission.
 */
sealed interface GPUCommandSubmission {
    /**
     * Commands were submitted to the real GPU facade after target, capability,
     * resource, and scope validation.
     *
     * Tests may construct this value to verify dump shape, but production code
     * must not emit it from refusal-first doubles or before backend submission
     * has actually occurred. Failed validation must use `Refused`; backend
     * submission errors must use `Failed`.
     */
    data class Submitted(
        val submissionId: String,
        val scopeLabel: String,
        val deviceGeneration: GPUDeviceGenerationID,
        val targetGeneration: Long = 0L,
        val scopeLabels: List<String> = listOf(scopeLabel),
        val taskIds: List<String> = emptyList(),
        val passIds: List<String> = emptyList(),
        val resourceUsageSummary: List<String> = emptyList(),
        val submittedRouteCounts: Map<String, Int> = emptyMap(),
        val readbackRequests: List<GPUFrameReadbackRequest> = emptyList(),
        val diagnostics: List<GPUExecutionDiagnostic> = emptyList(),
    ) : GPUCommandSubmission {
        internal val dumpScopeLabelsSnapshot: List<String> = scopeLabels.toList()
        internal val dumpTaskIdsSnapshot: List<String> = taskIds.toList()
        internal val dumpPassIdsSnapshot: List<String> = passIds.toList()
        internal val dumpResourceUsageSummarySnapshot: List<String> = resourceUsageSummary.toList()
        internal val dumpSubmittedRouteCountsSnapshot: Map<String, Int> = submittedRouteCounts.toMap()
        internal val dumpReadbackRequestsSnapshot: List<GPUFrameReadbackRequest> = readbackRequests.toList()
        internal val dumpDiagnosticsSnapshot: List<GPUExecutionDiagnostic> = diagnostics.toList()
    }

    /** Commands were refused before submission. */
    data class Refused(val diagnostic: GPUExecutionDiagnostic) : GPUCommandSubmission

    /** Commands failed during submission. */
    data class Failed(val diagnostic: GPUExecutionDiagnostic) : GPUCommandSubmission
}

/**
 * Input facts checked by execution before command submission.
 *
 * The execution package owns this preflight boundary after target preparation
 * and resource materialization. It may cite resource materialization decisions
 * as immutable evidence, but it does not reinterpret material semantics and it
 * must not produce [GPUCommandSubmission.Submitted]. The constructor snapshots
 * caller-owned sets/lists and target/materialization facts so backend overrides
 * observe the same inputs as PM dumps. Backends that can actually submit work
 * may override [GPUExecutionContext.preflight] to add backend evidence before
 * their own real submission path.
 */
class GPUExecutionPreflightRequest(
    val scope: GPUCommandScope,
    target: GPUSurfaceTarget? = null,
    requiredTargetUsageLabels: Set<String> = emptySet(),
    materializationDecision: GPUResourceMaterializationDecision? = null,
    taskIds: List<String> = emptyList(),
    passIds: List<String> = emptyList(),
) {
    /** Target descriptor copied for backend validation and deterministic preflight dumps. */
    val target: GPUSurfaceTarget? = target?.snapshot()

    /** Required target usages copied so caller mutation cannot alter backend validation. */
    val requiredTargetUsageLabels: Set<String> = requiredTargetUsageLabels.toSet()

    /** Resource materialization evidence copied before execution diagnostics are built. */
    val materializationDecision: GPUResourceMaterializationDecision? = materializationDecision?.snapshot()

    /** Task identifiers copied for backend submission and PM evidence. */
    val taskIds: List<String> = taskIds.toList()

    /** Pass identifiers copied for backend submission and PM evidence. */
    val passIds: List<String> = passIds.toList()

    internal val dumpRequiredTargetUsageLabelsSnapshot: Set<String> = this.requiredTargetUsageLabels.toSet()
    internal val dumpTaskIdsSnapshot: List<String> = this.taskIds.toList()
    internal val dumpPassIdsSnapshot: List<String> = this.passIds.toList()
}

/**
 * Backend handoff for the first accepted render route.
 *
 * This request is the narrow bridge between Kanvas-owned planning contracts and
 * a concrete backend submit path. It requires a render preflight request, a
 * non-empty draw pass, a materialized resource decision, a render pipeline plan,
 * and pass-local payload references. The constructor snapshots caller-owned
 * collections and every dumpable label so later mutations cannot rewrite
 * backend inputs or PM evidence. It does not submit work and must not be treated
 * as proof of GPU support; [GPUExecutionContext] still refuses by default until
 * a backend override performs a real submit.
 */
class GPUFirstRouteRenderSubmitRequest(
    preflightRequest: GPUExecutionPreflightRequest,
    pass: GPUDrawPass,
    materialization: GPUResourceMaterializationDecision.Materialized,
    pipelinePlan: GPUPipelineCreationPlan,
    payloadRefs: List<GPUDrawPayloadRef>,
    readbackRequests: List<GPUFrameReadbackRequest> = emptyList(),
) {
    /** Preflight input copied for backend validation and default refusal diagnostics. */
    val preflightRequest: GPUExecutionPreflightRequest = preflightRequest.snapshot()

    /** Draw pass copied so backend overrides cannot observe caller-owned list mutation. */
    val pass: GPUDrawPass = pass.snapshot()

    /** Materialized resource decision copied as stable non-handle evidence. */
    val materialization: GPUResourceMaterializationDecision.Materialized = materialization.snapshot()

    /** Render pipeline plan copied without backend handles. */
    val pipelinePlan: GPUPipelineCreationPlan = pipelinePlan.snapshot()

    /** Pass-local payload references copied for backend submit implementations. */
    val payloadRefs: List<GPUDrawPayloadRef> = payloadRefs.toList()

    /** Optional readback requests copied for post-submit PM evidence. */
    val readbackRequests: List<GPUFrameReadbackRequest> = readbackRequests.toList()

    internal val dumpPassIdsSnapshot: List<String> = listOf(this.pass.passId)
    internal val dumpPipelineKeysSnapshot: List<String> = this.pass.pipelineKeys.toList()
    internal val dumpPayloadCommandIdsSnapshot: List<String> =
        this.payloadRefs.map { ref -> ref.commandIdValue.toString() }.toList()
    internal val dumpReadbackRequestsSnapshot: List<GPUFrameReadbackRequest> = this.readbackRequests.toList()
    internal val dumpPipelineCacheKeySnapshot: String = this.pipelinePlan.cacheKey.value

    init {
        require(this.preflightRequest.scope is GPUCommandScope.Render) {
            "GPUFirstRouteRenderSubmitRequest requires a render command scope"
        }
        require(this.preflightRequest.materializationDecision == this.materialization) {
            "GPUFirstRouteRenderSubmitRequest preflight materialization must match the supplied materialization"
        }
        val preflightTargetId = this.preflightRequest.target?.targetId
        require(preflightTargetId == null || preflightTargetId == this.materialization.targetId) {
            "GPUFirstRouteRenderSubmitRequest preflight target must match materialization target"
        }
        require(this.preflightRequest.dumpPassIdsSnapshot.contains(this.pass.passId)) {
            "GPUFirstRouteRenderSubmitRequest preflight passIds must include ${this.pass.passId}"
        }
        require(this.materialization.dumpOperandBridgeSnapshot.isNotEmpty()) {
            "GPUFirstRouteRenderSubmitRequest requires bridged materialized command operands"
        }
        require(this.pass.invocations.isNotEmpty()) {
            "GPUFirstRouteRenderSubmitRequest pass must contain at least one invocation"
        }
        require(this.pass.pipelineKeys.isNotEmpty()) {
            "GPUFirstRouteRenderSubmitRequest pass must contain at least one pipeline key"
        }
        require(this.pipelinePlan.preimage is GPUPipelineKeyPreimage.Render) {
            "GPUFirstRouteRenderSubmitRequest requires a render pipeline plan"
        }
        require(this.payloadRefs.isNotEmpty()) {
            "GPUFirstRouteRenderSubmitRequest requires at least one payload reference"
        }
    }
}

/**
 * Aggregated execution preflight result.
 *
 * [readyForSubmission] only means no terminal diagnostic was found by this
 * preflight pass; it is not a submission token and is never encoded as
 * [GPUCommandSubmission.Submitted]. Diagnostics are snapshotted for dumps so
 * later mutation of caller-owned collections cannot rewrite PM evidence.
 */
data class GPUExecutionPreflightReport(
    val scopeLabel: String,
    val commandClass: GPUCommandClass,
    val targetId: String?,
    val taskIds: List<String>,
    val passIds: List<String>,
    val diagnostics: List<GPUExecutionDiagnostic>,
) {
    internal val dumpTargetIdSnapshot: String? = targetId
    internal val dumpTaskIdsSnapshot: List<String> = taskIds.toList()
    internal val dumpPassIdsSnapshot: List<String> = passIds.toList()
    internal val dumpDiagnosticsSnapshot: List<GPUExecutionDiagnostic> = diagnostics.toList()

    /** True only when every aggregated diagnostic is non-terminal. */
    val readyForSubmission: Boolean = dumpDiagnosticsSnapshot.none { diagnostic -> diagnostic.terminal }
}

/**
 * Readback result descriptor.
 *
 * Readback is PM evidence, not a route-support shortcut. Completed results
 * report hashes and byte counts only; skipped and refused results must expose
 * stable diagnostic codes so missing readback cannot be treated as success.
 */
sealed interface GPUReadbackResult {
    /**
     * Readback completed and produced a typed payload descriptor.
     *
     * Explicit skip or refusal reasons belong to [Skipped] or [Refused] evidence.
     */
    data class Completed(
        val request: GPUFrameReadbackRequest,
        val payloadHash: String,
        val byteCount: Long,
        val diagnostics: List<GPUExecutionDiagnostic> = emptyList(),
    ) : GPUReadbackResult {
        internal val dumpDiagnosticsSnapshot: List<GPUExecutionDiagnostic> = diagnostics.toList()
    }

    /** Readback was skipped by an explicit policy. */
    data class Skipped(
        val request: GPUFrameReadbackRequest,
        val reasonCode: String,
        val diagnostics: List<GPUExecutionDiagnostic> = emptyList(),
    ) : GPUReadbackResult {
        internal val dumpDiagnosticsSnapshot: List<GPUExecutionDiagnostic> = diagnostics.toList()
    }

    /** Readback was refused before backend work. */
    data class Refused(val request: GPUFrameReadbackRequest, val diagnostic: GPUExecutionDiagnostic) : GPUReadbackResult
}

/** Execution context that owns access to the selected GPU facade. */
interface GPUExecutionContext {
    /** Current device generation used for stale resource detection. */
    val deviceGeneration: GPUDeviceGenerationID

    /** Capability facts for the selected facade implementation. */
    val capabilities: GPUExecutionCapabilities
        get() = GPUExecutionCapabilities.refusing()

    /**
     * Aggregates target, materialization, scope, capability, and backend facts.
     *
     * The default implementation is refusal-first. It is useful as PM evidence
     * for why submission cannot yet happen, but it does not call backend APIs
     * and never constructs `GPUCommandSubmission.Submitted`.
     */
    fun preflight(request: GPUExecutionPreflightRequest): GPUExecutionPreflightReport {
        val diagnostics = mutableListOf<GPUExecutionDiagnostic>()
        val scope = request.scope
        val target = request.target

        if (target != null) {
            diagnostics += target.descriptor.validateForUse(
                requiredUsageLabels = request.dumpRequiredTargetUsageLabelsSnapshot,
                targetLabel = target.targetId,
            )
            if (target.deviceGeneration != deviceGeneration) {
                diagnostics += GPUExecutionDiagnostic.deviceGenerationMismatch(
                    target = target,
                    expectedDeviceGeneration = deviceGeneration,
                )
            }
        }

        request.materializationDecision?.let { decision ->
            diagnostics += decision.executionPreflightDiagnostics()
        }

        if (scope is GPUCommandScope.Render) {
            if (scope.useTokenLabels.isEmpty()) {
                diagnostics += GPUExecutionDiagnostic.emptyRenderScope(scope = scope)
            }
            if (request.dumpPassIdsSnapshot.isEmpty()) {
                diagnostics += GPUExecutionDiagnostic.emptyRenderPass(scope = scope)
            }
        }

        diagnostics += if (capabilities.supports(scope.commandClass)) {
            GPUExecutionDiagnostic.contextUnconfigured(scope = scope)
        } else {
            GPUExecutionDiagnostic.commandClassUnavailable(scope = scope)
        }

        return GPUExecutionPreflightReport(
            scopeLabel = scope.scopeLabel,
            commandClass = scope.commandClass,
            targetId = target?.targetId,
            taskIds = request.dumpTaskIdsSnapshot,
            passIds = request.dumpPassIdsSnapshot,
            diagnostics = diagnostics,
        )
    }

    /**
     * Submits one fully planned first-route render request to a backend.
     *
     * The default implementation is deliberately refusal-first: it reuses
     * preflight diagnostics when available, otherwise reports an unconfigured
     * submit path with the request's vertical evidence facts. Backend
     * implementations may override this only after validating the same target,
     * resource, pass, pipeline, payload, and readback facts and performing an
     * actual facade submission.
     */
    fun submit(request: GPUFirstRouteRenderSubmitRequest): GPUCommandSubmission {
        val report = preflight(request.preflightRequest)
        val diagnostic = report.dumpDiagnosticsSnapshot
            .firstOrNull { candidate -> candidate.terminal }
            ?: GPUExecutionDiagnostic.contextUnconfigured(scope = request.preflightRequest.scope)
        return GPUCommandSubmission.Refused(diagnostic.withFirstRouteRenderSubmitFacts(request))
    }

    /**
     * Submits one encoded command scope to the GPU facade.
     *
     * The base context never calls a backend. It returns [GPUCommandSubmission.Refused] with
     * `context_unconfigured` when the command class is supported but no submitter is installed, or
     * `commandClassUnavailable` when capabilities reject the scope. Backend contexts must override
     * this only after preserving those refusal diagnostics for unsupported paths.
     */
    fun submit(scope: GPUCommandScope): GPUCommandSubmission {
        val diagnostic = if (capabilities.supports(scope.commandClass)) {
            GPUExecutionDiagnostic.contextUnconfigured(scope = scope)
        } else {
            GPUExecutionDiagnostic.commandClassUnavailable(scope = scope)
        }
        return GPUCommandSubmission.Refused(diagnostic)
    }

    /**
     * Reads back a typed request through the GPU facade.
     *
     * The default behavior is evidence-only and produces no bytes: it returns
     * [GPUReadbackResult.Skipped] with either `readback_backend_unconfigured` or
     * `readback_unavailable`. A target advertising [GPUSurfaceTargetDescriptor.readbackAvailable]
     * only proves target capability; an installed backend readback path is still required before
     * R6 evidence may become [GPUReadbackResult.Completed].
     */
    fun readback(request: GPUFrameReadbackRequest): GPUReadbackResult {
        val diagnostic = if (capabilities.readback) {
            GPUExecutionDiagnostic.readbackBackendUnconfigured(request = request)
        } else {
            GPUExecutionDiagnostic.readbackUnavailable(request = request, stage = "readback")
        }
        return GPUReadbackResult.Skipped(
            request = request,
            reasonCode = diagnostic.code,
            diagnostics = listOf(diagnostic),
        )
    }
}

/**
 * Surface or offscreen target selected for execution.
 *
 * This is a non-handle execution fact: [targetId] and [descriptor] identify the target selected for
 * evidence, while [deviceGeneration] ties the target to the device facts used for submission. A
 * backend must refuse or mark the target stale when these generation facts no longer match the
 * execution context.
 */
data class GPUSurfaceTarget(
    val targetId: String,
    val descriptor: GPUSurfaceTargetDescriptor,
    val deviceGeneration: GPUDeviceGenerationID,
)

/**
 * Descriptor for a surface target before acquiring frame resources.
 *
 * [width], [height], and [colorFormat] are validated by [validateForUse] rather than by the
 * constructor so fixtures can carry invalid evidence to diagnostics. [targetGeneration] is the
 * target-side freshness token, [usageLabels] must contain every required render/copy/readback use,
 * and [readbackAvailable] means only that the target may be read back if a backend readback path is
 * configured.
 */
data class GPUSurfaceTargetDescriptor(
    val width: Int,
    val height: Int,
    val colorFormat: String,
    val surfaceBacked: Boolean,
    val targetGeneration: Long = 0L,
    val usageLabels: Set<String> = emptySet(),
    val readbackAvailable: Boolean = false,
) {
    /**
     * Returns target diagnostics that must be handled before command submission.
     *
     * Invalid dimensions, blank formats, or missing usage labels are terminal for the first route;
     * callers must surface these diagnostics instead of attempting backend submission.
     */
    fun validateForUse(
        requiredUsageLabels: Set<String>,
        targetLabel: String,
    ): List<GPUExecutionDiagnostic> {
        val diagnostics = mutableListOf<GPUExecutionDiagnostic>()
        val missingUsage = requiredUsageLabels - usageLabels

        if (width <= 0 || height <= 0 || colorFormat.isBlank()) {
            diagnostics += GPUExecutionDiagnostic.invalidTargetDescriptor(
                targetLabel = targetLabel,
                width = width,
                height = height,
                colorFormat = colorFormat,
            )
        }
        if (missingUsage.isNotEmpty()) {
            diagnostics += GPUExecutionDiagnostic.executionUsageMissing(
                targetLabel = targetLabel,
                missingUsageLabels = missingUsage,
                availableUsageLabels = usageLabels,
            )
        }

        return diagnostics
    }
}

/** Frame submission record including target and device-generation facts. */
data class GPUFrameSubmission(
    val frameId: String,
    val target: GPUSurfaceTarget,
    val commandSubmissions: List<GPUCommandSubmission>,
    val diagnostics: List<GPUExecutionDiagnostic> = emptyList(),
)

/**
 * Diagnostic emitted by execution or submission.
 *
 * Diagnostics own stable reason codes and non-handle evidence facts for command
 * submission and readback failures. [facts] are rendered in sorted key order by
 * dump helpers; they must not contain backend command-buffer handles, surface
 * leases, or other transient object identities.
 */
data class GPUExecutionDiagnostic(
    val code: String,
    val stage: String,
    val message: String,
    val terminal: Boolean,
    val facts: Map<String, String> = emptyMap(),
) {
    internal val dumpFactsSnapshot: Map<String, String> = facts.toMap()

    companion object {
        /** Builds a diagnostic for a command class that this context cannot submit. */
        fun commandClassUnavailable(scope: GPUCommandScope): GPUExecutionDiagnostic {
            val code = when (scope.commandClass) {
                GPUCommandClass.Render -> "unsupported.execution.render_unavailable"
                GPUCommandClass.Compute -> "unsupported.execution.compute_unavailable"
                GPUCommandClass.CopyUpload -> "unsupported.execution.copy_unavailable"
                GPUCommandClass.Readback -> "unsupported.execution.readback_unavailable"
            }
            return GPUExecutionDiagnostic(
                code = code,
                stage = "submit",
                message = "Execution context does not support ${scope.commandClass} command scope ${scope.scopeLabel}.",
                terminal = true,
                facts = mapOf(
                    "commandClass" to scope.commandClass.name,
                    "scopeLabel" to scope.scopeLabel,
                ),
            )
        }

        /** Builds a diagnostic for a context with capability facts but no backend submit path. */
        fun contextUnconfigured(scope: GPUCommandScope): GPUExecutionDiagnostic =
            GPUExecutionDiagnostic(
                code = "unsupported.execution.context_unconfigured",
                stage = "submit",
                message = "Execution context has no configured backend submit path for scope ${scope.scopeLabel}.",
                terminal = true,
                facts = mapOf(
                    "commandClass" to scope.commandClass.name,
                    "scopeLabel" to scope.scopeLabel,
                ),
            )

        /** Builds a diagnostic for a target bound to the wrong device generation. */
        fun deviceGenerationMismatch(
            target: GPUSurfaceTarget,
            expectedDeviceGeneration: GPUDeviceGenerationID,
        ): GPUExecutionDiagnostic =
            GPUExecutionDiagnostic(
                code = "unsupported.execution.device_generation_mismatch",
                stage = "preflight",
                message = "Target ${target.targetId} belongs to device generation ${target.deviceGeneration.value} but execution context uses ${expectedDeviceGeneration.value}.",
                terminal = true,
                facts = mapOf(
                    "actualDeviceGeneration" to target.deviceGeneration.value.toString(),
                    "expectedDeviceGeneration" to expectedDeviceGeneration.value.toString(),
                    "targetId" to target.targetId,
                ),
            )

        /** Builds a diagnostic for a render scope with no use-token evidence. */
        fun emptyRenderScope(scope: GPUCommandScope.Render): GPUExecutionDiagnostic =
            GPUExecutionDiagnostic(
                code = "diagnostic.execution.empty_render_scope",
                stage = "preflight",
                message = "Render scope ${scope.scopeLabel} has no use-token evidence before submission.",
                terminal = false,
                facts = mapOf(
                    "commandClass" to scope.commandClass.name,
                    "scopeLabel" to scope.scopeLabel,
                    "useTokenLabels" to scope.useTokenLabels.toList().dumpSequence(),
                ),
            )

        /** Builds a diagnostic for a render preflight without pass identifiers. */
        fun emptyRenderPass(scope: GPUCommandScope.Render): GPUExecutionDiagnostic =
            GPUExecutionDiagnostic(
                code = "diagnostic.execution.empty_render_pass",
                stage = "preflight",
                message = "Render scope ${scope.scopeLabel} has no render-pass evidence before submission.",
                terminal = false,
                facts = mapOf(
                    "commandClass" to scope.commandClass.name,
                    "passIds" to "none",
                    "scopeLabel" to scope.scopeLabel,
                ),
            )

        /** Builds a target descriptor validation diagnostic. */
        fun invalidTargetDescriptor(
            targetLabel: String,
            width: Int,
            height: Int,
            colorFormat: String,
        ): GPUExecutionDiagnostic =
            GPUExecutionDiagnostic(
                code = "invalid.execution.target_descriptor",
                stage = "target_validation",
                message = "Target $targetLabel has invalid descriptor width=$width height=$height colorFormat=$colorFormat.",
                terminal = true,
                facts = mapOf(
                    "colorFormat" to colorFormat,
                    "height" to height.toString(),
                    "targetLabel" to targetLabel,
                    "width" to width.toString(),
                ),
            )

        /** Builds an execution usage diagnostic for missing target flags. */
        fun executionUsageMissing(
            targetLabel: String,
            missingUsageLabels: Set<String>,
            availableUsageLabels: Set<String>,
        ): GPUExecutionDiagnostic =
            GPUExecutionDiagnostic(
                code = "unsupported.execution.usage_missing",
                stage = "target_validation",
                message = "Target $targetLabel is missing usage ${missingUsageLabels.sorted()} from available ${availableUsageLabels.sorted()}.",
                terminal = true,
                facts = mapOf(
                    "availableUsageLabels" to availableUsageLabels.sorted().joinToString(","),
                    "missingUsageLabels" to missingUsageLabels.sorted().joinToString(","),
                    "targetLabel" to targetLabel,
                ),
            )

        /** Builds a skipped-readback diagnostic. */
        fun readbackUnavailable(
            request: GPUFrameReadbackRequest,
            stage: String,
        ): GPUExecutionDiagnostic =
            GPUExecutionDiagnostic(
                code = "unsupported.execution.readback_unavailable",
                stage = stage,
                message = "Readback ${request.requestId.value} is unavailable for ${request.sourceBounds.dumpLabel()} ${request.pixelFormat.name}.",
                terminal = true,
                facts = mapOf(
                    "bufferOffsetBytes" to request.bufferOffsetBytes.toString(),
                    "pixelFormat" to request.pixelFormat.name,
                    "requestId" to request.requestId.value,
                    "sourceBounds" to request.sourceBounds.dumpLabel(),
                ),
            )

        /** Builds a readback diagnostic for contexts with capability facts but no backend path. */
        fun readbackBackendUnconfigured(request: GPUFrameReadbackRequest): GPUExecutionDiagnostic =
            GPUExecutionDiagnostic(
                code = "unsupported.execution.readback_unconfigured",
                stage = "readback",
                message = "Execution context has readback capability facts but no configured backend readback path for request ${request.requestId.value}.",
                terminal = true,
                facts = mapOf(
                    "bufferOffsetBytes" to request.bufferOffsetBytes.toString(),
                    "pixelFormat" to request.pixelFormat.name,
                    "requestId" to request.requestId.value,
                    "sourceBounds" to request.sourceBounds.dumpLabel(),
                ),
            )
    }
}

/**
 * Emits deterministic PM evidence lines for this execution preflight.
 *
 * The dump reports whether preflight remained only diagnostic or found a
 * terminal refusal. It is deliberately separate from command-submission dumps
 * so unconfigured contexts cannot be mistaken for backend success.
 */
fun GPUExecutionPreflightReport.dumpLines(): List<String> =
    listOf(
        "execution.preflight:${if (readyForSubmission) "ready" else "refused"} " +
            "scope=$scopeLabel " +
            "class=$commandClass " +
            "target=${dumpTargetIdSnapshot ?: "none"} " +
            "tasks=${dumpTaskIdsSnapshot.dumpSequence()} " +
            "passes=${dumpPassIdsSnapshot.dumpSequence()} " +
            "diagnostics=${dumpDiagnosticsSnapshot.dumpCodes()}",
    ) + dumpDiagnosticsSnapshot.dumpLines()

/**
 * Emits deterministic PM evidence lines for this command submission.
 *
 * `Submitted` dumps summarize object-level evidence that was explicitly
 * constructed after validation. The default [GPUExecutionContext.submit]
 * remains refusal-first, so these lines do not create fake GPU success from
 * test doubles or unconfigured contexts.
 */
fun GPUCommandSubmission.dumpLines(): List<String> =
    when (this) {
        is GPUCommandSubmission.Submitted ->
            listOf(
                "execution.submission:submitted " +
                    "id=$submissionId " +
                    "deviceGeneration=${deviceGeneration.value} " +
                    "targetGeneration=$targetGeneration " +
                    "scopes=${dumpScopeLabelsSnapshot.dumpSequence()} " +
                    "tasks=${dumpTaskIdsSnapshot.dumpSequence()} " +
                    "passes=${dumpPassIdsSnapshot.dumpSequence()} " +
                    "resources=${dumpResourceUsageSummarySnapshot.dumpList()} " +
                    "routes=${dumpSubmittedRouteCountsSnapshot.dumpCounts()} " +
                    "readbacks=${dumpReadbackRequestsSnapshot.map { request -> request.requestId.value }.dumpSequence()} " +
                    "diagnostics=${dumpDiagnosticsSnapshot.dumpCodes()}",
            ) + dumpDiagnosticsSnapshot.dumpLines()
        is GPUCommandSubmission.Refused ->
            listOf(
                "execution.submission:refused " +
                    "scope=${diagnostic.dumpFactsSnapshot["scopeLabel"] ?: UNSPECIFIED_DUMP_VALUE} " +
                    "class=${diagnostic.dumpFactsSnapshot["commandClass"] ?: UNSPECIFIED_DUMP_VALUE} " +
                    "code=${diagnostic.code} " +
                    "terminal=${diagnostic.terminal}",
            ) + listOf(diagnostic).dumpLines()
        is GPUCommandSubmission.Failed ->
            listOf(
                "execution.submission:failed " +
                    "code=${diagnostic.code} " +
                    "stage=${diagnostic.stage} " +
                    "terminal=${diagnostic.terminal} " +
                    "facts=${diagnostic.dumpFactsSnapshot.dumpFacts()}",
            ) + listOf(diagnostic).dumpLines()
    }

/**
 * Emits deterministic PM evidence lines for this readback result.
 *
 * Completed dumps contain checksums and byte counts, not pixel bytes or backend
 * handles. Skipped and refused dumps always carry diagnostic codes so PM
 * reports can cite late-stage outcomes without treating absent readback as
 * success.
 */
fun GPUReadbackResult.dumpLines(): List<String> =
    when (this) {
        is GPUReadbackResult.Completed ->
            listOf(
                "execution.readback:completed " +
                    request.dumpFacts() + " " +
                    "bytes=$byteCount " +
                    "payloadHash=$payloadHash " +
                    "diagnostics=${dumpDiagnosticsSnapshot.dumpCodes()}",
            ) + dumpDiagnosticsSnapshot.dumpLines()
        is GPUReadbackResult.Skipped ->
            listOf(
                "execution.readback:skipped " +
                    request.dumpFacts() + " " +
                    "reason=$reasonCode " +
                    "diagnostics=${dumpDiagnosticsSnapshot.dumpCodes()}",
            ) + dumpDiagnosticsSnapshot.dumpLines()
        is GPUReadbackResult.Refused ->
            listOf(
                "execution.readback:refused " +
                    request.dumpFacts() + " " +
                    "code=${diagnostic.code} " +
                    "terminal=${diagnostic.terminal}",
            ) + listOf(diagnostic).dumpLines()
    }

private const val UNSPECIFIED_DUMP_VALUE = "unspecified"

private fun GPUFrameReadbackRequest.dumpFacts(): String =
    "request=${requestId.value} " +
        "bounds=${sourceBounds.dumpLabel()} " +
        "pixelFormat=${pixelFormat.name} " +
        "color=${outputColorInterpretation.value} " +
        "bufferOffsetBytes=$bufferOffsetBytes"

private fun org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds.dumpLabel(): String =
    "$left,$top,$right,$bottom"

private fun GPUExecutionPreflightRequest.snapshot(): GPUExecutionPreflightRequest =
    GPUExecutionPreflightRequest(
        scope = scope,
        target = target?.snapshot(),
        requiredTargetUsageLabels = dumpRequiredTargetUsageLabelsSnapshot,
        materializationDecision = materializationDecision?.snapshot(),
        taskIds = dumpTaskIdsSnapshot,
        passIds = dumpPassIdsSnapshot,
    )

private fun GPUSurfaceTarget.snapshot(): GPUSurfaceTarget =
    copy(descriptor = descriptor.snapshot())

private fun GPUSurfaceTargetDescriptor.snapshot(): GPUSurfaceTargetDescriptor =
    copy(usageLabels = usageLabels.toSet())

private fun GPUDrawPass.snapshot(): GPUDrawPass =
    GPUDrawPass(
        passId = passId,
        targetStateHash = targetStateHash,
        layerScopeId = layerScopeId,
        loadStoreLabel = loadStoreLabel,
        invocations = invocations.toList(),
        pipelineKeys = pipelineKeys.toList(),
        barriers = barriers.toList(),
        diagnostics = diagnostics.toList(),
        drawPackets = drawPackets.toList(),
        provisionalSegmentKey = provisionalSegmentKey,
        batchEligibilityByPacketId = batchEligibilityByPacketId.toMap(),
    )

private fun GPUResourceMaterializationDecision.Materialized.snapshot(): GPUResourceMaterializationDecision.Materialized =
    copy(
        resources = dumpResourcesSnapshot,
        diagnostics = dumpDiagnosticsSnapshot,
        taskIds = dumpTaskIdsSnapshot,
        resourcePlanLabels = dumpResourcePlanLabelsSnapshot,
        operandRefs = dumpOperandRefsSnapshot,
        operandBridge = dumpOperandBridgeSnapshot,
    )

private fun GPUResourceMaterializationDecision.snapshot(): GPUResourceMaterializationDecision =
    when (this) {
        is GPUResourceMaterializationDecision.Materialized -> snapshot()
        is GPUResourceMaterializationDecision.Deferred ->
            copy(
                diagnostics = dumpDiagnosticsSnapshot,
                taskIds = dumpTaskIdsSnapshot,
                resourcePlanLabels = dumpResourcePlanLabelsSnapshot,
            )
        is GPUResourceMaterializationDecision.Refused ->
            copy(
                taskIds = dumpTaskIdsSnapshot,
                resourcePlanLabels = dumpResourcePlanLabelsSnapshot,
                diagnostics = dumpDiagnosticsSnapshot,
            )
    }

private fun GPUPipelineCreationPlan.snapshot(): GPUPipelineCreationPlan =
    copy(
        preimage = preimage.snapshot(),
        requiredCapabilities = requiredCapabilities.toList(),
    )

private fun GPUPipelineKeyPreimage.snapshot(): GPUPipelineKeyPreimage =
    when (this) {
        is GPUPipelineKeyPreimage.Render ->
            copy(capabilityFacts = capabilityFacts.toList())
        is GPUPipelineKeyPreimage.Compute ->
            copy(capabilityFacts = capabilityFacts.toList())
    }

private fun GPUExecutionDiagnostic.withFirstRouteRenderSubmitFacts(
    request: GPUFirstRouteRenderSubmitRequest,
): GPUExecutionDiagnostic =
    copy(facts = request.firstRouteRenderSubmitFacts() + dumpFactsSnapshot)

private fun GPUFirstRouteRenderSubmitRequest.firstRouteRenderSubmitFacts(): Map<String, String> {
    val scope = preflightRequest.scope
    return mapOf(
        "commandClass" to scope.commandClass.name,
        "materializedResourceCount" to materialization.dumpResourcesSnapshot.size.toString(),
        "passIds" to dumpPassIdsSnapshot.dumpSequence(),
        "payloadCommands" to dumpPayloadCommandIdsSnapshot.dumpSequence(),
        "pipelineCacheKey" to dumpPipelineCacheKeySnapshot,
        "pipelineKeys" to dumpPipelineKeysSnapshot.dumpSequence(),
        "readbackRequests" to dumpReadbackRequestsSnapshot.map { request -> request.requestId.value }.dumpSequence(),
        "resourcePlans" to materialization.dumpResourcePlanLabelsSnapshot.dumpList(),
        "scopeLabel" to scope.scopeLabel,
        "targetId" to materialization.targetId,
        "tasks" to materialization.dumpTaskIdsSnapshot.dumpSequence(),
    )
}

private fun List<String>.dumpList(): String =
    if (isEmpty()) "none" else sorted().joinToString(",")

private fun List<String>.dumpSequence(): String =
    if (isEmpty()) "none" else joinToString(",")

private fun Map<String, Int>.dumpCounts(): String =
    if (isEmpty()) {
        "none"
    } else {
        entries.sortedBy { entry -> entry.key }
            .joinToString(",") { entry -> "${entry.key}=${entry.value}" }
    }

private fun List<GPUExecutionDiagnostic>.dumpCodes(): String =
    if (isEmpty()) "none" else map { diagnostic -> diagnostic.code }.sorted().joinToString(",")

private fun List<GPUExecutionDiagnostic>.dumpLines(): List<String> =
    toList().sortedWith(
        compareBy<GPUExecutionDiagnostic> { it.code }
            .thenBy { it.stage }
            .thenBy { it.terminal.toString() }
            .thenBy { it.dumpFactsSnapshot.dumpFacts() }
            .thenBy { it.message },
    )
        .map { diagnostic ->
            "execution.diagnostic " +
                "code=${diagnostic.code} " +
                "stage=${diagnostic.stage} " +
                "terminal=${diagnostic.terminal} " +
                "facts=${diagnostic.dumpFactsSnapshot.dumpFacts()}"
        }

private fun Map<String, String>.dumpFacts(): String =
    if (isEmpty()) {
        "none"
    } else {
        entries.sortedBy { entry -> entry.key }
            .joinToString(";") { entry -> "${entry.key}=${entry.value}" }
    }

private fun GPUResourceMaterializationDecision.executionPreflightDiagnostics(): List<GPUExecutionDiagnostic> =
    when (this) {
        is GPUResourceMaterializationDecision.Materialized ->
            dumpDiagnosticsSnapshot.map { diagnostic ->
                diagnostic.toExecutionPreflightDiagnostic(
                    materializationOutcome = "materialized",
                    targetId = targetId,
                    taskIds = dumpTaskIdsSnapshot,
                    resourcePlanLabels = dumpResourcePlanLabelsSnapshot,
                )
            }
        is GPUResourceMaterializationDecision.Deferred ->
            listOf(
                GPUExecutionDiagnostic(
                    code = reasonCode,
                    stage = "materialization",
                    message = "Resource materialization for target $targetId is deferred: $reasonCode.",
                    terminal = true,
                    facts = materializationFacts(
                        materializationOutcome = "deferred",
                        targetId = targetId,
                        taskIds = dumpTaskIdsSnapshot,
                        resourcePlanLabels = dumpResourcePlanLabelsSnapshot,
                    ),
                ),
            ) + dumpDiagnosticsSnapshot.map { diagnostic ->
                diagnostic.toExecutionPreflightDiagnostic(
                    materializationOutcome = "deferred",
                    targetId = targetId,
                    taskIds = dumpTaskIdsSnapshot,
                    resourcePlanLabels = dumpResourcePlanLabelsSnapshot,
                )
            }
        is GPUResourceMaterializationDecision.Refused ->
            dumpDiagnosticsSnapshot.map { diagnostic ->
                diagnostic.toExecutionPreflightDiagnostic(
                    materializationOutcome = "refused",
                    targetId = targetId,
                    taskIds = dumpTaskIdsSnapshot,
                    resourcePlanLabels = dumpResourcePlanLabelsSnapshot,
                )
            }
    }

private fun GPUResourceDiagnostic.toExecutionPreflightDiagnostic(
    materializationOutcome: String,
    targetId: String,
    taskIds: List<String>,
    resourcePlanLabels: List<String>,
): GPUExecutionDiagnostic =
    GPUExecutionDiagnostic(
        code = code,
        stage = "materialization",
        message = message,
        terminal = terminal,
        facts = dumpFactsSnapshot + materializationFacts(
            materializationOutcome = materializationOutcome,
            targetId = targetId,
            taskIds = taskIds,
            resourcePlanLabels = resourcePlanLabels,
            resourceLabel = resourceLabel,
        ),
    )

private fun materializationFacts(
    materializationOutcome: String,
    targetId: String,
    taskIds: List<String>,
    resourcePlanLabels: List<String>,
    resourceLabel: String? = null,
): Map<String, String> {
    val facts = mutableMapOf(
        "materializationOutcome" to materializationOutcome,
        "resourcePlanLabels" to resourcePlanLabels.dumpList(),
        "targetId" to targetId,
        "taskIds" to taskIds.dumpSequence(),
    )
    if (resourceLabel != null) {
        facts["resourceLabel"] = resourceLabel
    }
    return facts
}
