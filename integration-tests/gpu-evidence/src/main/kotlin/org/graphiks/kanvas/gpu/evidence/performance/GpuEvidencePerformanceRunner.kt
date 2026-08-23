package org.graphiks.kanvas.gpu.evidence.performance

import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceCase
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation
import org.graphiks.kanvas.gpu.evidence.compare.EvidenceComparator
import org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceBackendPort
import org.graphiks.kanvas.gpu.evidence.runner.KanvasSurfaceRenderSession
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.graphiks.kanvas.surface.RenderResult

fun interface MonotonicClock { fun nanoTime(): Long }

class GpuEvidencePerformanceRunner(
    private val backend: EvidenceBackendPort,
    private val sourceCommit: String,
    private val config: PerformanceConfig = PerformanceConfig(),
    private val clock: MonotonicClock = MonotonicClock { System.nanoTime() },
    private val comparator: EvidenceComparator = EvidenceComparator(),
) {
    fun run(evidenceCase: EvidenceCase): PerformanceRun {
        require(evidenceCase.descriptor.expectation == EvidenceExpectation.ShouldRender) { "performance measures renderable scenes only" }
        val program = evidenceCase.program as? KanvasSurfaceProgram
            ?: return unavailable(evidenceCase, "performance requires a Kanvas Surface program")
        val environment = environment()
        val adapterSummary = backend.adapter?.let {
            org.graphiks.kanvas.gpu.renderer.execution.GPUBackendAdapterSummary(
                it.summary ?: "", it.vendor, it.device, it.architecture, it.description, it.isFallbackAdapter,
            )
        }
        val eligibility = PerformanceEligibility.evaluate(adapterSummary)
        if (eligibility !is PerformanceVerdict.EligibleMeasurement) {
            return PerformanceRun(
                sourceCommit, evidenceCase.descriptor.id.value, config, eligibility, environment, eligibility,
                diagnostics = listOf("measurement skipped: ${eligibility.reason}"),
            )
        }
        if (backend.capabilities == null) {
            return PerformanceRun(
                sourceCommit, evidenceCase.descriptor.id.value, config,
                PerformanceVerdict.Unavailable("GPU capabilities unavailable"), environment, eligibility,
            )
        }

        val before = backend.telemetry()
        val samples = mutableListOf<Long>()
        var coldNanos: Long? = null
        val diagnostics = mutableListOf<String>()
        var coldSnapshot = PerformanceTelemetrySnapshot.empty()
        var warmupSnapshot = PerformanceTelemetrySnapshot.empty()
        var measuredSnapshot = PerformanceTelemetrySnapshot.empty()
        try {
            val session = program.openSession(evidenceCase.descriptor.width, evidenceCase.descriptor.height)

            val coldBefore = backend.telemetry()
            val cold = timedRender(session, evidenceCase, diagnostics)
            coldNanos = cold.nanos
            val coldAfter = backend.telemetry()
            coldSnapshot = snapshot(coldBefore, coldAfter)
            validateCold(cold.result, evidenceCase, diagnostics)

            val warmupBefore = backend.telemetry()
            repeat(config.warmupFrames) { renderAndValidate(session, evidenceCase, diagnostics) }
            val warmupAfter = backend.telemetry()
            warmupSnapshot = snapshot(warmupBefore, warmupAfter)

            val measuredBefore = backend.telemetry()
            repeat(config.measuredFrames) {
                samples += timedRender(session, evidenceCase, diagnostics).nanos
            }
            val measuredAfter = backend.telemetry()
            measuredSnapshot = snapshot(measuredBefore, measuredAfter)
        } catch (failure: Throwable) {
            diagnostics += "${failure::class.simpleName}: ${failure.message ?: "performance execution failed"}"
            return finish(
                evidenceCase, environment, eligibility, before, samples, coldNanos, diagnostics,
                PerformanceVerdict.Failed(diagnostics.last()), coldSnapshot, warmupSnapshot, measuredSnapshot,
            )
        }

        validateSubmissionDelta(coldSnapshot, 1L, "cold", diagnostics)
        validateSubmissionDelta(warmupSnapshot, config.warmupFrames.toLong(), "warmup", diagnostics)
        validateSubmissionDelta(measuredSnapshot, config.measuredFrames.toLong(), "measured", diagnostics)
        val total = snapshot(before, backend.telemetry())
        validateSubmissionDelta(total, 1L + config.warmupFrames + config.measuredFrames, "total", diagnostics)
        val verdict = if (diagnostics.isEmpty() && samples.size == config.measuredFrames) {
            eligibility
        } else {
            PerformanceVerdict.Failed(diagnostics.firstOrNull() ?: "measured frame count mismatch")
        }
        return finish(
            evidenceCase, environment, eligibility, before, samples, coldNanos, diagnostics, verdict,
            coldSnapshot, warmupSnapshot, measuredSnapshot,
        )
    }

    private fun unavailable(evidenceCase: EvidenceCase, reason: String): PerformanceRun {
        val environment = environment()
        val verdict = PerformanceVerdict.Unavailable(reason)
        return PerformanceRun(sourceCommit, evidenceCase.descriptor.id.value, config, verdict, environment, verdict)
    }

    private fun timedRender(
        session: KanvasSurfaceRenderSession,
        evidenceCase: EvidenceCase,
        diagnostics: MutableList<String>,
    ): TimedSurfaceRender {
        val before = backend.telemetry()
        val start = clock.nanoTime()
        val result = session.render()
        val nanos = elapsed(start)
        val after = backend.telemetry()
        validateRender(result, evidenceCase, before, after, diagnostics)
        return TimedSurfaceRender(result, nanos)
    }

    private fun renderAndValidate(
        session: KanvasSurfaceRenderSession,
        evidenceCase: EvidenceCase,
        diagnostics: MutableList<String>,
    ): RenderResult {
        val before = backend.telemetry()
        val result = session.render()
        val after = backend.telemetry()
        validateRender(result, evidenceCase, before, after, diagnostics)
        return result
    }

    private fun validateRender(
        result: RenderResult,
        evidenceCase: EvidenceCase,
        before: GPUBackendRuntimeTelemetry,
        after: GPUBackendRuntimeTelemetry,
        diagnostics: MutableList<String>,
    ) {
        val descriptor = evidenceCase.descriptor
        when {
            result.width != descriptor.width || result.height != descriptor.height ->
                diagnostics += "Surface render dimensions did not match the evidence descriptor"
            result.pixels.size != descriptor.width * descriptor.height * 4 ->
                diagnostics += "Surface render did not produce descriptor-sized RGBA pixels"
            result.stats.drawCallCount <= 0 -> diagnostics += "Surface render did not report draw work"
            result.stats.pipelineCount <= 0 -> diagnostics += "Surface render did not report pipeline work"
            after.submissions - before.submissions != 1L ->
                diagnostics += "Surface render did not produce exactly one runtime submission"
        }
    }

    private fun validateCold(result: RenderResult, evidenceCase: EvidenceCase, diagnostics: MutableList<String>) {
        val descriptor = evidenceCase.descriptor
        if (result.pixels.size != descriptor.width * descriptor.height * 4) return
        val expected = requireNotNull(evidenceCase.oracle).render(descriptor.width, descriptor.height)
        val comparison = comparator.compare(
            result.pixels.toByteArray(), expected, descriptor.width, descriptor.height,
            requireNotNull(descriptor.comparison),
        )
        if (!comparison.passed) diagnostics += "cold validation CPU oracle comparison failed"
    }

    private fun validateSubmissionDelta(
        snapshot: PerformanceTelemetrySnapshot,
        expected: Long,
        phase: String,
        diagnostics: MutableList<String>,
    ) {
        if (snapshot.delta["submissions"]?.value != expected) {
            diagnostics += "$phase Surface telemetry submissions must equal $expected"
        }
    }

    private fun finish(
        case_: EvidenceCase,
        environment: PerformanceEnvironment,
        eligibility: PerformanceVerdict,
        before: GPUBackendRuntimeTelemetry,
        samples: List<Long>,
        cold: Long?,
        diagnostics: List<String>,
        verdict: PerformanceVerdict,
        coldSnapshot: PerformanceTelemetrySnapshot,
        warmupSnapshot: PerformanceTelemetrySnapshot,
        measuredSnapshot: PerformanceTelemetrySnapshot,
    ): PerformanceRun {
        val after = backend.telemetry()
        val total = snapshot(before, after)
        return PerformanceRun(
            sourceCommit, case_.descriptor.id.value, config, verdict, environment, eligibility, cold,
            samples.takeIf { it.isNotEmpty() }?.let(FrameTimingSummary::fromSamples), samples,
            PerformanceTelemetry(coldSnapshot, warmupSnapshot, measuredSnapshot, total), diagnostics,
        )
    }

    private fun snapshot(
        before: GPUBackendRuntimeTelemetry,
        after: GPUBackendRuntimeTelemetry,
    ): PerformanceTelemetrySnapshot {
        val b = before.toPerformanceCounters()
        val a = after.toPerformanceCounters()
        val keys = (b.keys + a.keys).toSortedSet()
        val beforeMetrics = keys.associateWith { key -> PerformanceMetric(b[key] ?: 0L, MetricSource.Observed) }
        val afterMetrics = keys.associateWith { key -> PerformanceMetric(a[key] ?: 0L, MetricSource.Observed) }
        val delta = keys.associateWith { key -> PerformanceMetric((a[key] ?: 0L) - (b[key] ?: 0L), MetricSource.Derived) }
        return PerformanceTelemetrySnapshot(beforeMetrics, afterMetrics, delta)
    }

    private fun elapsed(start: Long) = (clock.nanoTime() - start).coerceAtLeast(0L)

    private data class TimedSurfaceRender(val result: RenderResult, val nanos: Long)

    private fun environment() = PerformanceEnvironment(
        sourceCommit, System.getProperty("os.name"), System.getProperty("os.version"),
        System.getProperty("os.arch"), System.getProperty("java.version"), backend.adapter, backend.deviceGeneration,
    )
}
