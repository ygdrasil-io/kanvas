package org.graphiks.kanvas.gpu.evidence.runner

import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceCase
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceEnvironment
import org.graphiks.kanvas.gpu.evidence.catalog.RouteEvidence
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.evidence.compare.EvidenceComparator
import org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.graphiks.kanvas.surface.RenderResult

/** Public Surface render handle; the evidence backend observes only its runtime telemetry. */
fun interface KanvasSurfaceRenderSession {
    fun render(): RenderResult
}

/** Executes [KanvasSurfaceProgram] instances through [KanvasSurfaceRenderSession.render]. */
@OptIn(ExperimentalUnsignedTypes::class)
class KanvasSurfaceEvidenceExecutor(
    private val backend: EvidenceBackendPort,
    private val sourceCommit: String,
    private val comparator: EvidenceComparator = EvidenceComparator(),
) {
    fun execute(evidenceCase: EvidenceCase): EvidenceExecutionResult {
        val program = evidenceCase.program as? KanvasSurfaceProgram
            ?: error("KanvasSurfaceEvidenceExecutor requires KanvasSurfaceProgram")
        val environment = environmentOf(backend, sourceCommit)
        val session = try {
            program.openSession(evidenceCase.descriptor.width, evidenceCase.descriptor.height)
        } catch (failure: Exception) {
            return failure(program.routeId, failure.message ?: "Surface session setup failed.", GPUBackendRuntimeTelemetry(), environment, failure)
        }
        val before = backend.telemetry()
        val result = try {
            session.render()
        } catch (failure: Exception) {
            val delta = backend.telemetry() - before
            return failure(program.routeId, failure.message ?: "Surface.render failed.", delta, environment, failure)
        }
        val delta = backend.telemetry() - before
        val route = route(program.routeId, result, delta)
        val descriptor = evidenceCase.descriptor
        if (result.width != descriptor.width || result.height != descriptor.height ||
            result.pixels.size != descriptor.width * descriptor.height * 4 ||
            result.stats.drawCallCount <= 0 || result.stats.pipelineCount <= 0 || delta.submissions != 1L
        ) {
            val message = when {
                result.width != descriptor.width || result.height != descriptor.height -> "Surface result dimensions did not match the evidence descriptor."
                result.pixels.size != descriptor.width * descriptor.height * 4 -> "Surface result did not produce descriptor-sized RGBA pixels."
                result.stats.drawCallCount <= 0 -> "Surface result did not report draw work."
                result.stats.pipelineCount <= 0 -> "Surface result did not report pipeline work."
                else -> "Surface result did not produce exactly one runtime submission."
            }
            return EvidenceExecutionResult.ExecutionFailure("failed.gpu.execution", message, route.copy(outcome = "failed"), result.diagnostics.entries.map { it.code }, environment)
        }
        val actual = result.pixels.toByteArray()
        val expected = requireNotNull(evidenceCase.oracle).render(descriptor.width, descriptor.height)
        return EvidenceExecutionResult.Observed(SceneObservation.Rendered(
            actual,
            route,
            result.diagnostics.entries.map { it.code },
            environment,
            comparator.compare(actual, expected, descriptor.width, descriptor.height, requireNotNull(descriptor.comparison)),
        ))
    }

    private fun failure(
        routeId: String,
        message: String,
        delta: GPUBackendRuntimeTelemetry,
        environment: EvidenceEnvironment,
        failure: Exception,
    ) = EvidenceExecutionResult.ExecutionFailure(
        "failed.kanvas.surface",
        message,
        RouteEvidence(routeId, null, "Completed", "failed", emptyList(), emptyList(), mapOf("queue.submit" to delta.submissions), delta),
        listOf(failure::class.simpleName.orEmpty()),
        environment,
    )

    private fun route(routeId: String, result: RenderResult, delta: GPUBackendRuntimeTelemetry) = RouteEvidence(
        routeId,
        null,
        "Completed",
        "rendered",
        emptyList(),
        emptyList(),
        mapOf(
            "queue.submit" to delta.submissions,
            "render.draw" to result.stats.drawCallCount.toLong(),
            "render.pipelineBind" to result.stats.pipelineCount.toLong(),
        ),
        delta,
    )
}

/** Routes each evidence program to its one permitted execution boundary. */
class EvidenceCaseExecutor(
    backend: EvidenceBackendPort,
    sourceCommit: String,
) {
    private val prepared = GPUPreparedEvidenceExecutor(backend, sourceCommit)
    private val surface = KanvasSurfaceEvidenceExecutor(backend, sourceCommit)

    fun execute(evidenceCase: EvidenceCase): EvidenceExecutionResult = when (evidenceCase.program) {
        is KanvasSurfaceProgram -> surface.execute(evidenceCase)
        is SceneProgram -> prepared.execute(evidenceCase)
        else -> error("Unsupported evidence program: ${evidenceCase.program::class.qualifiedName}")
    }
}
