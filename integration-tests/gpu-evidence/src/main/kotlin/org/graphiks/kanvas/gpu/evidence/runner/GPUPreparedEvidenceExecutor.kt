package org.graphiks.kanvas.gpu.evidence.runner

import java.util.concurrent.TimeUnit
import org.graphiks.kanvas.gpu.evidence.catalog.*
import org.graphiks.kanvas.gpu.evidence.compare.EvidenceComparator
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSession
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSceneCompletedFrameResult
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSceneFrameSession
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutput
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutputRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef

/** Narrow injectable boundary for host-independent runner tests. */
interface EvidenceBackendPort {
    val capabilities: EvidenceCapabilities?
    val deviceGeneration: Long
    fun telemetry(): GPUBackendRuntimeTelemetry
    fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation
    fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort
}

data class EvidenceCapabilities(val implementation: String, internal val product: GPUCapabilities? = null)
data class EvidenceRecordingRequest(val descriptor: EvidenceSceneDescriptor, val frameOrdinal: Long, val readbackRequestId: String)

sealed interface EvidenceProgramPreparation {
    data class Recorded(val routeId: String, val program: PreparedEvidenceProgram, val diagnostics: List<String>) : EvidenceProgramPreparation
    data class Refused(val routeId: String, val stableReasonCode: String, val message: String, val diagnostics: List<String>) : EvidenceProgramPreparation
}

data class PreparedEvidenceProgram(internal val taskList: org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList?, val readbackRequestId: String)
interface EvidencePreparedFramePort : AutoCloseable { fun render(program: PreparedEvidenceProgram): EvidenceCompletedFrame }

sealed interface EvidenceExecutionResult {
    data class Observed(val observation: SceneObservation) : EvidenceExecutionResult
    data class ExecutionFailure(val stableReasonCode: String, val message: String, val route: RouteEvidence, val diagnostics: List<String>, val environment: EvidenceEnvironment) : EvidenceExecutionResult
}

data class EvidenceCompletedFrame(
    val attemptId: String?, val furthestPhase: String?, val outcome: String, val diagnosticCode: String?, val diagnosticMessage: String?,
    val readbackRequestId: String?, val readbackBytes: ByteArray?, val encodedScopeKinds: List<String>, val events: List<StructuralEventEvidence>, val counters: Map<String, Long>,
    val diagnosticDetails: List<String> = emptyList(),
) {
    companion object {
        fun succeeded(requestId: String, bytes: ByteArray) = EvidenceCompletedFrame("test-attempt", "Completed", "Succeeded", null, null, requestId, bytes, emptyList(), emptyList(), mapOf("queue.submit" to 1L))
    }
}

/** Runs an evidence case exclusively through the prepared scene session route. */
class GPUPreparedEvidenceExecutor(
    private val backend: EvidenceBackendPort,
    private val sourceCommit: String,
    private val comparator: EvidenceComparator = EvidenceComparator(),
) {
    private var nextFrameOrdinal = 1L

    fun execute(evidenceCase: EvidenceCase): EvidenceExecutionResult {
        val environment = environmentOf(backend, sourceCommit)
        if (backend.capabilities == null) return EvidenceExecutionResult.Observed(SceneObservation.Unavailable(
            "unavailable.gpu.capabilities", "GPU backend session did not expose capabilities.", environment,
        ))
        val before = backend.telemetry()
        val requestId = "gpu-evidence.${evidenceCase.descriptor.id.value}"
        val prepared = backend.prepare(evidenceCase.program, EvidenceRecordingRequest(evidenceCase.descriptor, nextFrameOrdinal++, requestId))
        return when (prepared) {
            is EvidenceProgramPreparation.Refused -> refusal(prepared, before, environment)
            is EvidenceProgramPreparation.Recorded -> render(evidenceCase, prepared, before, environment)
        }
    }

    private fun refusal(prepared: EvidenceProgramPreparation.Refused, before: GPUBackendRuntimeTelemetry, environment: EvidenceEnvironment): EvidenceExecutionResult.Observed {
        val delta = backend.telemetry() - before
        return EvidenceExecutionResult.Observed(SceneObservation.Refused(prepared.stableReasonCode, prepared.message, delta.submissions, RouteEvidence(
            prepared.routeId, null, null, "refused", emptyList(), emptyList(), emptyMap(), delta,
        ), prepared.diagnostics, environment))
    }

    private fun render(evidenceCase: EvidenceCase, prepared: EvidenceProgramPreparation.Recorded, before: GPUBackendRuntimeTelemetry, environment: EvidenceEnvironment): EvidenceExecutionResult {
        val descriptor = evidenceCase.descriptor
        val completed = try {
            backend.prepareSceneFrame(descriptor.width, descriptor.height).use { frame -> frame.render(prepared.program) }
        } catch (failure: Exception) {
            val delta = backend.telemetry() - before
            return EvidenceExecutionResult.ExecutionFailure("failed.gpu.prepared-session", failure.message ?: "Prepared scene session failed.", RouteEvidence(prepared.routeId, null, null, "failed", emptyList(), emptyList(), emptyMap(), delta), prepared.diagnostics + failure::class.simpleName.orEmpty(), environment)
        }
        val delta = backend.telemetry() - before
        val route = RouteEvidence(
            prepared.routeId,
            completed.attemptId,
            completed.furthestPhase,
            if (completed.outcome == "Succeeded" &&
                completed.readbackRequestId == prepared.program.readbackRequestId &&
                completed.readbackBytes?.size == descriptor.width * descriptor.height * 4
            ) "rendered" else "refused",
            completed.encodedScopeKinds,
            completed.events,
            completed.counters,
            delta,
        )
        if (completed.outcome != "Succeeded" || completed.furthestPhase != "Completed" || completed.readbackRequestId != prepared.program.readbackRequestId || completed.readbackBytes?.size != descriptor.width * descriptor.height * 4 || completed.counters.getOrDefault("queue.submit", 0L) <= 0L || delta.submissions <= 0L) {
            val message = completed.diagnosticMessage ?: when {
                completed.furthestPhase != "Completed" -> "Prepared scene frame did not reach Completed."
                completed.counters.getOrDefault("queue.submit", 0L) <= 0L -> "Prepared scene frame did not record queue.submit."
                delta.submissions <= 0L -> "Prepared scene frame completed without a positive runtime submission delta."
                else -> "Prepared scene frame did not produce the requested RGBA readback."
            }
            return EvidenceExecutionResult.ExecutionFailure(completed.diagnosticCode ?: "failed.gpu.execution", message, route.copy(outcome = "failed"), prepared.diagnostics + completed.diagnosticDetails, environment)
        }
        val expected = requireNotNull(evidenceCase.oracle).render(descriptor.width, descriptor.height)
        return EvidenceExecutionResult.Observed(SceneObservation.Rendered(completed.readbackBytes, route, prepared.diagnostics + completed.diagnosticDetails, environment, comparator.compare(completed.readbackBytes, expected, descriptor.width, descriptor.height, requireNotNull(descriptor.comparison))))
    }
}

/** Real adapter around the existing product [GPUBackendSession] and prepared session APIs. */
class ProductEvidenceBackendPort(private val backend: GPUBackendSession) : EvidenceBackendPort {
    override val capabilities: EvidenceCapabilities? = backend.capabilities?.let { EvidenceCapabilities(it.implementation.implementationName, it) }
    override val deviceGeneration: Long get() = backend.deviceGeneration.value
    override fun telemetry(): GPUBackendRuntimeTelemetry = backend.runtimeTelemetry

    override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation {
        val capability = capabilities?.product ?: return EvidenceProgramPreparation.Refused("product.unknown", "unavailable.gpu.capabilities", "GPU backend session did not expose capabilities.", emptyList())
        val readback = GPUReadbackRequestID(context.readbackRequestId)
        return when (val preparation = program.prepare(SceneRecordingContext(capability, backend.deviceGeneration, GPUFrameTargetRef("target.scene"), GPUPixelBounds(0, 0, context.descriptor.width, context.descriptor.height), context.frameOrdinal, readback))) {
            is ScenePreparation.Recorded -> EvidenceProgramPreparation.Recorded(preparation.routeId, PreparedEvidenceProgram(preparation.taskList, readback.value), preparation.diagnostics)
            is ScenePreparation.Refused -> EvidenceProgramPreparation.Refused(
                (program as? RoutedSceneProgram)?.routeId ?: error("scene program must carry a product route identity"),
                preparation.stableReasonCode,
                preparation.message,
                preparation.diagnostics,
            )
        }
    }

    override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = ProductPreparedFramePort(backend.prepareSceneFrameSession(GPUOffscreenTargetRequest(width, height)))

    private class ProductPreparedFramePort(private val frame: GPUPreparedSceneFrameSession) : EvidencePreparedFramePort {
        override fun render(program: PreparedEvidenceProgram): EvidenceCompletedFrame {
            val result = frame.renderFrame(requireNotNull(program.taskList), GPUSceneFrameOutputRequest.ReadbackRgba(GPUReadbackRequestID(program.readbackRequestId))).completion.toCompletableFuture().get(30, TimeUnit.SECONDS)
            return result.toEvidenceCompletedFrame()
        }
        override fun close() = frame.close()
    }
}

private fun GPUPreparedSceneCompletedFrameResult.toEvidenceCompletedFrame(): EvidenceCompletedFrame {
    val output = output as? GPUSceneFrameOutput.ReadbackRgba
    return EvidenceCompletedFrame(attemptId.value, furthestPhase.name, outcome.name, diagnostic?.code?.value, diagnostic?.message, output?.requestId?.value, output?.bytes, encodedScopeKinds.map { it.name }, telemetry.events.map { StructuralEventEvidence(it.kind.name, it.phase.name, it.label) }, telemetry.counters.mapKeys { it.key.label }, diagnostic?.let(::completionDiagnosticLines).orEmpty())
}

internal fun completionDiagnosticLines(diagnostic: org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic): List<String> = buildList {
    add("diagnostic.code=${diagnostic.code.value}"); add("diagnostic.domain=${diagnostic.domain.name}"); add("diagnostic.severity=${diagnostic.severity.name}"); add("diagnostic.message=${diagnostic.message}"); add("diagnostic.terminal=${diagnostic.isTerminal}"); add("diagnostic.retryable=${diagnostic.isRetryable}")
    diagnostic.facts.toSortedMap().forEach { (key, value) -> add("diagnostic.fact.$key=$value") }
}

private fun environmentOf(port: EvidenceBackendPort, sourceCommit: String) = EvidenceEnvironment(sourceCommit, System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"), System.getProperty("java.version"), null, port.deviceGeneration, port.capabilities?.implementation, port.capabilities != null)

private operator fun GPUBackendRuntimeTelemetry.minus(before: GPUBackendRuntimeTelemetry) = GPUBackendRuntimeTelemetry(
    renderPasses - before.renderPasses, offscreenPasses - before.offscreenPasses, windowPasses - before.windowPasses, submissions - before.submissions, commandBuffers - before.commandBuffers, buffersCreated - before.buffersCreated, texturesCreated - before.texturesCreated, intermediateTexturesCreated - before.intermediateTexturesCreated, coverageMasksDestroyed - before.coverageMasksDestroyed, destinationCopies - before.destinationCopies, destinationReadbackSnapshots - before.destinationReadbackSnapshots, msaaTargets - before.msaaTargets, msaaResolves - before.msaaResolves, bindGroupsCreated - before.bindGroupsCreated, samplersCreated - before.samplersCreated, queueWrites - before.queueWrites, uniformSlabsCreated - before.uniformSlabsCreated, uniformSlabBytesAllocated - before.uniformSlabBytesAllocated, uniformSlabFallbacks - before.uniformSlabFallbacks, passBatchPlans - before.passBatchPlans, passBatchesAccepted - before.passBatchesAccepted, passBatchCuts - before.passBatchCuts, passBatchPackets - before.passBatchPackets,
)
