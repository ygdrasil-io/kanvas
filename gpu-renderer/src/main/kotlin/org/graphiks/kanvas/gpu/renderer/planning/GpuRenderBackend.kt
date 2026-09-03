package org.graphiks.kanvas.gpu.renderer.planning

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.HashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.GpuPlanCompiler
import org.graphiks.kanvas.gpu.plan.PlanBudget
import org.graphiks.kanvas.gpu.plan.PlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameImmediateState
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSceneCompletedFrameResult
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutput
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutputRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.render.ir.RenderBackend
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.kanvas.render.ir.RenderExecutionResult
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderSubmission
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.kanvas.render.ir.SubmissionId

/**
 * Weak identity key: equal semantic graphs remain distinct, while collected
 * graph keys are removed through the owning backend's reference queue.
 */
private class WeakRenderGraphKey(
    graph: RenderGraph,
    queue: ReferenceQueue<RenderGraph>? = null,
) : WeakReference<RenderGraph>(graph, queue) {
    private val identityHash: Int = System.identityHashCode(graph)

    override fun hashCode(): Int = identityHash

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WeakRenderGraphKey || identityHash != other.identityHash) return false
        val graph = get() ?: return false
        return graph === other.get()
    }
}

public data class GpuRenderTargetConfig(
    public val extent: SceneExtent,
    public val colorSpace: ColorSpace,
    public val frameLocalBudgetBytes: Long,
    public val internalFormat: PlanLogicalColorFormat =
        PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL,
) {
    init {
        require(colorSpace == ColorSpace.SRGB)
        require(frameLocalBudgetBytes > 0L)
        require(internalFormat == PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)
    }
}

public class GpuRenderBackend(
    private val compiler: GpuPlanCompiler,
    private val context: GpuRenderContext,
    private val targetConfig: GpuRenderTargetConfig,
) : RenderBackend<RenderGraph, GpuFrameOutput> {
    private val ids = AtomicLong(0)
    private val issuedPlanReferences = ReferenceQueue<RenderGraph>()
    private val issuedPlans = HashMap<WeakRenderGraphKey, Unit>()

    override fun plan(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
    ): RenderPlanResult<RenderGraph> {
        if (target.extent != targetConfig.extent || target.colorSpace != targetConfig.colorSpace) {
            return RenderPlanResult.InvalidScene(
                listOf(diag("w3.lowering.incompatible_plan", "Target does not match the bound GPU backend.")),
            )
        }
        compiler.classify(scene, target)?.let { return it }
        val acquired = try {
            context.acquirePlanningCapabilities()
        } catch (_: Throwable) {
            GpuPlanningCapabilityAcquisition.Unavailable
        }
        val capabilities = when (acquired) {
            is GpuPlanningCapabilityAcquisition.Ready -> acquired.snapshot
            is GpuPlanningCapabilityAcquisition.Unsupported -> {
                return RenderPlanResult.GapOnPromotedScope(listOf(acquired.diagnostic))
            }
            GpuPlanningCapabilityAcquisition.Unavailable -> {
                return RenderPlanResult.GapOnPromotedScope(
                    listOf(diag("w3.execution.device_failure", "GPU runtime is unavailable.")),
                )
            }
        }
        return compiler.plan(scene, target, capabilities, PlanBudget(targetConfig.frameLocalBudgetBytes)).also { result ->
            if (result is RenderPlanResult.Ready) {
                synchronized(issuedPlans) {
                    removeCollectedPlans()
                    issuedPlans[WeakRenderGraphKey(result.plan, issuedPlanReferences)] = Unit
                }
            }
        }
    }

    override fun submit(plan: RenderGraph): RenderSubmission<GpuFrameOutput> {
        val id = SubmissionId(ids.incrementAndGet())
        val result = CompletableDeferred<RenderExecutionResult<GpuFrameOutput>>()
        val accepted = try {
            context.launchWorker {
                var issue: RenderExecutionResult<GpuFrameOutput> = device(
                    "w3.execution.device_failure",
                    "GPU execution stopped before preparing a frame.",
                )
                try {
                    issue = execute(plan)
                } catch (_: Throwable) {
                    issue = device(
                        "w3.execution.device_failure",
                        "GPU execution failed during preparation.",
                    )
                } finally {
                    result.complete(issue)
                }
            }
        } catch (_: Throwable) {
            false
        }
        if (!accepted) {
            result.complete(device("w3.execution.device_failure", "GPU render context is closed."))
        }
        return object : RenderSubmission<GpuFrameOutput> {
            override val id: SubmissionId = id

            override suspend fun await(): RenderExecutionResult<GpuFrameOutput> = result.await()
        }
    }

    private suspend fun execute(plan: RenderGraph): RenderExecutionResult<GpuFrameOutput> {
        val snapshot = try {
            context.capabilities()
        } catch (_: Throwable) {
            return device("w3.execution.device_failure", "GPU runtime preparation failed.")
        } ?: return device("w3.execution.device_failure", "GPU runtime is unavailable.")

        if (!isAuthenticatedForTarget(plan, snapshot)) {
            return invalid("The submitted plan is not an authenticated plan for this backend target.")
        }

        val lowering = try {
            val capabilities = context.backendCapabilities()
                ?: return device("w3.execution.device_failure", "GPU capabilities are unavailable.")
            GpuPlanTaskListLowerer().lower(
                GpuPlanLoweringRequest(
                    graph = plan,
                    capabilities = capabilities,
                    deviceGeneration = GPUDeviceGenerationID(snapshot.deviceGeneration),
                    currentBudget = plan.budget,
                    frameId = GPUFrameID(1),
                    recordingId = GPURecordingID("w3.${plan.id.value}"),
                ),
            )
        } catch (_: Throwable) {
            return device("w3.execution.device_failure", "GPU frame preparation failed.")
        }
        val loweredPlan = when (lowering) {
            is GpuPlanLoweringResult.Lowered -> lowering
            is GpuPlanLoweringResult.UnsupportedCapability ->
                return RenderExecutionResult.UnsupportedCapability(listOf(lowering.diagnostic))
            is GpuPlanLoweringResult.InvalidPlan ->
                return RenderExecutionResult.InvalidPlan(listOf(lowering.diagnostic))
        }

        val key = GpuRenderSessionKey(
            deviceGeneration = snapshot.deviceGeneration,
            width = targetConfig.extent.width,
            height = targetConfig.extent.height,
            internalFormat = targetConfig.internalFormat,
        )
        val reservation = when (val prepared = try {
            context.acquirePrepared(key)
        } catch (_: Throwable) {
            return device("w3.execution.device_failure", "GPU target preparation failed.")
        }) {
            is GpuPreparedSessionAcquisition.Ready -> prepared.reservation
            is GpuPreparedSessionAcquisition.GenerationMismatch -> {
                context.invalidateDeviceGeneration(prepared.expectedGeneration)
                return device(
                    "w3.execution.device_failure",
                    "Prepared GPU target changed device generation before submission.",
                )
            }
            GpuPreparedSessionAcquisition.Unavailable -> {
                return device("w3.execution.device_failure", "Prepared GPU session is unavailable.")
            }
        }

        val outcome = try {
            context.withLease(reservation) { session ->
                executeFrame(session, loweredPlan, plan.visualCommandCount)
            }
        } catch (_: Throwable) {
            return device("w3.execution.device_failure", "GPU session lease preparation failed.")
        } ?: return invalid("Prepared session lease became stale before rendering.")

        if (outcome is RenderExecutionResult.DeviceFailure &&
            outcome.diagnostics.any { it.code.value == "w3.execution.device_failure" }
        ) {
            context.invalidateDeviceGeneration(GPUDeviceGenerationID(snapshot.deviceGeneration))
        }
        return outcome
    }

    private suspend fun executeFrame(
        session: GpuPreparedSceneSessionPort,
        loweredPlan: GpuPlanLoweringResult.Lowered,
        visualCommandCount: Int,
    ): RenderExecutionResult<GpuFrameOutput> {
        val handle = try {
            session.renderFrame(
                loweredPlan.taskList,
                GPUSceneFrameOutputRequest.ReadbackRgba(GPUReadbackRequestID(loweredPlan.readbackRequestId)),
                visualCommandCount,
            )
        } catch (_: Throwable) {
            return device("w3.execution.submit_failure", "GPU submission failed synchronously.")
        }
        return try {
            when (val immediate = handle.immediateState) {
                is GPUFrameImmediateState.Refused -> immediateFailure(
                    immediate.diagnostic,
                    "GPU submission was refused.",
                )
                is GPUFrameImmediateState.FailedBeforeSubmit -> immediateFailure(
                    immediate.diagnostic,
                    "GPU submission failed before queue submission.",
                )
                is GPUFrameImmediateState.FailedAfterSubmit -> immediateFailure(
                    immediate.diagnostic,
                    "GPU submission failed after queue submission.",
                )
                is GPUFrameImmediateState.Submitted -> complete(
                    handle.metricsSnapshot,
                    context.completionAwaiter.await(handle.completion),
                )
            }
        } catch (_: Throwable) {
            device("w3.execution.readback_failure", "GPU readback or output conversion failed.")
        } finally {
            runCatching { handle.release() }
        }
    }

    private fun complete(
        metricsSnapshot: GpuPreparedFrameMetricsSnapshot,
        completed: GPUPreparedSceneCompletedFrameResult,
    ): RenderExecutionResult<GpuFrameOutput> {
        if (completed.outcome != GPUFrameStructuralOutcome.Succeeded) {
            return if (isDeviceLoss(completed.diagnostic)) {
                device("w3.execution.device_failure", "GPU device failed during completion.")
            } else {
                device("w3.execution.readback_failure", "GPU frame did not complete successfully.")
            }
        }
        val bytes = (completed.output as? GPUSceneFrameOutput.ReadbackRgba)?.bytes
            ?: return device("w3.execution.readback_failure", "GPU frame did not provide RGBA readback.")
        val drawCalls = Math.toIntExact(Math.addExact(metricsSnapshot.draws, metricsSnapshot.drawIndexed))
        val rowStrideBytes = Math.multiplyExact(targetConfig.extent.width, 4)
        return RenderExecutionResult.Completed(
            GpuFrameOutput.of(
                width = targetConfig.extent.width,
                height = targetConfig.extent.height,
                rowStrideBytes = rowStrideBytes,
                channelOrder = GpuFrameChannelOrder.RGBA,
                bytes = bytes,
                metrics = GpuFrameMetrics(
                    opsDispatched = metricsSnapshot.visualCommandCount,
                    pipelineCount = Math.toIntExact(metricsSnapshot.pipelineBinds),
                    drawCallCount = drawCalls,
                    coverage = 0f,
                    coverageMeasured = false,
                ),
                diagnostics = completed.diagnostic?.let { listOf(nativeDiagnostic(it)) }.orEmpty(),
                structuralSteps = completed.telemetry.events.map { it.kind.name },
                nativeEvidenceCounters = metricsSnapshot.nativeCounters,
                nativeEvidenceScopeKinds = completed.encodedScopeKinds.map { it.name },
            ),
        )
    }

    private fun immediateFailure(
        diagnostic: GPUDiagnostic,
        submitMessage: String,
    ): RenderExecutionResult.DeviceFailure = if (isDeviceLoss(diagnostic)) {
        device("w3.execution.device_failure", "GPU device failed during submission.")
    } else {
        device("w3.execution.submit_failure", submitMessage)
    }

    private fun isAuthenticatedForTarget(
        plan: RenderGraph,
        snapshot: PlanCapabilitySnapshot,
    ): Boolean = synchronized(issuedPlans) {
        removeCollectedPlans()
        issuedPlans.containsKey(WeakRenderGraphKey(plan))
    } &&
            plan.targetExtent.width == targetConfig.extent.width &&
            plan.targetExtent.height == targetConfig.extent.height &&
            plan.colorFormat == targetConfig.internalFormat &&
            plan.capabilities == snapshot &&
            plan.budget == PlanBudget(targetConfig.frameLocalBudgetBytes)

    @Suppress("UNCHECKED_CAST")
    private fun removeCollectedPlans() {
        while (true) {
            val collected = issuedPlanReferences.poll() as? WeakRenderGraphKey ?: return
            issuedPlans.remove(collected)
        }
    }

    private fun isDeviceLoss(diagnostic: GPUDiagnostic?): Boolean =
        diagnostic?.facts?.get("kind") == "DeviceLost" ||
            diagnostic?.code?.value?.contains("device", ignoreCase = true) == true

    private fun nativeDiagnostic(diagnostic: GPUDiagnostic): RenderDiagnostic =
        diag(diagnostic.code.value, diagnostic.message)

    private fun invalid(message: String): RenderExecutionResult.InvalidPlan =
        RenderExecutionResult.InvalidPlan(listOf(diag("w3.lowering.incompatible_plan", message)))

    private fun device(code: String, message: String): RenderExecutionResult.DeviceFailure =
        RenderExecutionResult.DeviceFailure(listOf(diag(code, message)))

    private fun diag(code: String, message: String): RenderDiagnostic = RenderDiagnostic(
        RenderDiagnosticCode(code),
        RenderDiagnosticDomain.EXECUTION,
        RenderDiagnosticSeverity.ERROR,
        message,
    )
}
