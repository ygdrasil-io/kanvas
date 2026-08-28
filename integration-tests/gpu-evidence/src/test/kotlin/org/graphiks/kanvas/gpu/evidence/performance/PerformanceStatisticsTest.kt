package org.graphiks.kanvas.gpu.evidence.performance

import kotlin.test.Test
import kotlin.test.assertEquals

class PerformanceStatisticsTest {
    @Test fun `tier diagnostics expose frame and resource budgets with explicit metric sources`() {
        val evaluation = PerformanceTiering.evaluate(
            PerformanceRun.fixture(),
            PerformanceBudget(
                family = "solid-card-stack",
                tier = PerformanceTier.P0,
                p50FrameNanos = 200L,
                p95FrameNanos = 300L,
                maxAllocations = 10L,
                maxPipelineBuilds = 10L,
                maxUploadBytes = 10L,
                maxReadbackBytes = 10L,
            ),
        )

        assertEquals(PerformanceTier.P0, evaluation.tier)
        assertEquals("reporting-only", evaluation.status)
        assertEquals("Derived", evaluation.metrics.getValue("allocations").source.name)
        assertEquals("Unavailable", evaluation.metrics.getValue("readbackBytes").source.name)
        assertEquals("performance.metric.readback-bytes-unavailable", evaluation.metrics.getValue("readbackBytes").reason)
        assertEquals(false, evaluation.countsAsReleaseGate)
    }

    @Test fun `missing measured counters remain unavailable instead of becoming zero`() {
        val run = PerformanceRun.fixture().copy(
            telemetry = PerformanceTelemetry(
                PerformanceTelemetrySnapshot(emptyMap(), emptyMap(), emptyMap()),
                PerformanceRun.fixture().telemetry.warmup,
                PerformanceTelemetrySnapshot(emptyMap(), emptyMap(), emptyMap()),
            ),
        )
        val evaluation = PerformanceTiering.evaluate(run, PerformanceBudget("solid-card-stack", PerformanceTier.P1, 200L, 300L, 1L, 1L, 1L, 1L))
        assertEquals(null, evaluation.metrics.getValue("allocations").value)
        assertEquals(MetricSource.Unavailable, evaluation.metrics.getValue("allocations").source)
    }

    @Test fun `nearest rank percentiles use sorted odd samples`() {
        assertEquals(FrameTimingSummary(5, 3L, 5L, MetricSource.Observed), FrameTimingSummary.fromSamples(listOf(5L, 1L, 3L, 2L, 4L)))
    }

    @Test fun `nearest rank percentiles use sorted even samples`() {
        assertEquals(FrameTimingSummary(4, 2L, 4L, MetricSource.Observed), FrameTimingSummary.fromSamples(listOf(4L, 1L, 2L, 3L)))
    }

    @Test fun `default config requires ten warmup and ninety measured frames`() {
        val config = PerformanceConfig()
        assertEquals(10, config.warmupFrames)
        assertEquals(90, config.measuredFrames)
        assertEquals(1, config.gateVersion)
    }
}
