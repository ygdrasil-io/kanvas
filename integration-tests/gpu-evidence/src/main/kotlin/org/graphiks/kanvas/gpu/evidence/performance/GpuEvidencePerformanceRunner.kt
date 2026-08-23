package org.graphiks.kanvas.gpu.evidence.performance

import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceCase
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceEnvironment
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.evidence.compare.EvidenceComparator
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceBackendPort
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceExecutionResult
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceProgramPreparation
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceRecordingRequest
import org.graphiks.kanvas.gpu.evidence.runner.EvidencePreparedFramePort
import org.graphiks.kanvas.gpu.evidence.runner.PreparedEvidenceProgram

fun interface MonotonicClock { fun nanoTime(): Long }

class GpuEvidencePerformanceRunner(
    private val backend: EvidenceBackendPort,
    private val sourceCommit: String,
    private val config: PerformanceConfig = PerformanceConfig(),
    private val clock: MonotonicClock = MonotonicClock { System.nanoTime() },
    private val comparator: EvidenceComparator = EvidenceComparator(),
) {
    private var frameOrdinal = 1L

    fun run(evidenceCase: EvidenceCase): PerformanceRun {
        require(evidenceCase.descriptor.expectation == EvidenceExpectation.ShouldRender) { "performance measures renderable scenes only" }
        val environment = environment()
        val adapterSummary = backend.adapter?.let { org.graphiks.kanvas.gpu.renderer.execution.GPUBackendAdapterSummary(it.summary ?: "", it.vendor, it.device, it.architecture, it.description, it.isFallbackAdapter) }
        val eligibility = PerformanceEligibility.evaluate(adapterSummary)
        if (eligibility !is PerformanceVerdict.EligibleMeasurement) return PerformanceRun(sourceCommit, evidenceCase.descriptor.id.value, config, eligibility, environment, eligibility, diagnostics = listOf("measurement skipped: ${eligibility.reason}"))
        if (backend.capabilities == null) return PerformanceRun(sourceCommit, evidenceCase.descriptor.id.value, config, PerformanceVerdict.Unavailable("GPU capabilities unavailable"), environment, PerformanceVerdict.Unavailable("GPU capabilities unavailable"))
        val before = backend.telemetry()
        val prepared = backend.prepare(evidenceCase.program, EvidenceRecordingRequest(evidenceCase.descriptor, frameOrdinal++, "gpu-performance.${evidenceCase.descriptor.id.value}"))
        if (prepared !is EvidenceProgramPreparation.Recorded) return PerformanceRun(sourceCommit, evidenceCase.descriptor.id.value, config, PerformanceVerdict.Failed("scene preparation refused"), environment, eligibility, diagnostics = prepared.diagnostics())
        val samples = mutableListOf<Long>()
        var coldNanos: Long? = null
        val diagnostics = mutableListOf<String>()
        try {
            backend.prepareSceneFrame(evidenceCase.descriptor.width, evidenceCase.descriptor.height).use { frame ->
                backend.telemetry()
                val coldStart = clock.nanoTime(); val cold = frame.render(prepared.program); coldNanos = elapsed(coldStart); backend.telemetry()
                validateCold(cold, prepared.program, evidenceCase, diagnostics)
                val completionPrepared = backend.prepare(
                    evidenceCase.program,
                    EvidenceRecordingRequest(evidenceCase.descriptor, frameOrdinal++, "gpu-evidence.performance.completion-only"),
                )
                if (completionPrepared !is EvidenceProgramPreparation.Recorded) {
                    diagnostics += "completion-only scene preparation refused"
                }
                val completionProgram = (completionPrepared as? EvidenceProgramPreparation.Recorded)?.program
                backend.telemetry()
                if (completionProgram != null) repeat(config.warmupFrames) { validateCompletion(frame.renderCompletionOnly(completionProgram), diagnostics) }
                backend.telemetry()
                backend.telemetry()
                if (completionProgram != null) repeat(config.measuredFrames) {
                    val start = clock.nanoTime(); val completion = frame.renderCompletionOnly(completionProgram); val elapsed = elapsed(start); validateCompletion(completion, diagnostics); samples += elapsed
                }
                backend.telemetry()
            }
        } catch (failure: Throwable) {
            diagnostics += "${failure::class.simpleName}: ${failure.message ?: "performance execution failed"}"
            return finish(evidenceCase, environment, eligibility, before, samples, coldNanos, diagnostics, PerformanceVerdict.Failed(diagnostics.last()))
        }
        val verdict = if (diagnostics.isEmpty() && samples.size == config.measuredFrames) eligibility else PerformanceVerdict.Failed(diagnostics.firstOrNull() ?: "measured frame count mismatch")
        return finish(evidenceCase, environment, eligibility, before, samples, coldNanos, diagnostics, verdict)
    }

    private fun finish(case_: EvidenceCase, environment: PerformanceEnvironment, eligibility: PerformanceVerdict, before: org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry, samples: List<Long>, cold: Long?, diagnostics: List<String>, verdict: PerformanceVerdict): PerformanceRun {
        val after = backend.telemetry(); val b = before.toPerformanceCounters(); val a = after.toPerformanceCounters()
        val beforeMetrics = b.mapValues { PerformanceMetric(it.value, MetricSource.Observed) }; val afterMetrics = a.mapValues { PerformanceMetric(it.value, MetricSource.Observed) }
        val delta = a.mapValues { (key, value) -> PerformanceMetric(value - (b[key] ?: 0L), MetricSource.Derived) }
        return PerformanceRun(sourceCommit, case_.descriptor.id.value, config, verdict, environment, eligibility, cold, samples.takeIf { it.isNotEmpty() }?.let(FrameTimingSummary::fromSamples), samples, PerformanceTelemetry(beforeMetrics, afterMetrics, delta), diagnostics)
    }
    private fun validateCold(result: org.graphiks.kanvas.gpu.evidence.runner.EvidenceCompletedFrame, program: PreparedEvidenceProgram, case_: EvidenceCase, diagnostics: MutableList<String>) {
        if (result.outcome != "Succeeded" || result.furthestPhase != "Completed" || result.readbackRequestId != program.readbackRequestId || result.readbackBytes?.size != case_.descriptor.width * case_.descriptor.height * 4 || result.counters["queue.submit"] ?: 0L <= 0L) diagnostics += "cold validation did not reach terminal completion with readback"
        val expected = requireNotNull(case_.oracle).render(case_.descriptor.width, case_.descriptor.height)
        if (result.readbackBytes != null) {
            val comparison = comparator.compare(result.readbackBytes, expected, case_.descriptor.width, case_.descriptor.height, requireNotNull(case_.descriptor.comparison))
            if (!comparison.passed) diagnostics += "cold validation CPU oracle comparison failed"
        }
    }
    private fun validateCompletion(result: org.graphiks.kanvas.gpu.evidence.runner.EvidenceCompletedFrame, diagnostics: MutableList<String>) { if (result.outcome != "Succeeded" || result.furthestPhase != "Completed") diagnostics += "measured frame did not reach terminal completion" }
    private fun elapsed(start: Long) = (clock.nanoTime() - start).coerceAtLeast(0L)
    private fun environment() = PerformanceEnvironment(sourceCommit, System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"), System.getProperty("java.version"), backend.adapter, backend.deviceGeneration)
    private fun EvidenceProgramPreparation.diagnostics() = when (this) { is EvidenceProgramPreparation.Refused -> diagnostics; is EvidenceProgramPreparation.Recorded -> diagnostics }
}
