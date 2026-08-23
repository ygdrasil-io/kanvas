package org.graphiks.kanvas.gpu.evidence.performance

import kotlin.test.Test
import kotlin.test.assertEquals

class PerformanceStatisticsTest {
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
