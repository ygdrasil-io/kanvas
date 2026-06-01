package org.skia.kadre.runtime

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class M84NativeFrameTimingCandidateTest {
    @Test
    fun nativeFrameTimingCandidateKeepsMeasuredPayloadReportingOnly() {
        val evidence = buildM84FrameTimingEvidence(Path("..").toAbsolutePath().normalize())
        val json = evidence.toJsonElement().toString()

        assertEquals("frame.kadre-windowed", evidence.lane)
        assertEquals("m83-display-list-pm-scene-v1", evidence.sceneContractId)
        assertEquals(60, evidence.warmupFrameCount)
        assertEquals(120, evidence.measuredSampleCount)
        assertEquals(true, evidence.p50Ms > 0.0)
        assertEquals(true, evidence.p95Ms >= evidence.p50Ms)
        assertEquals(true, evidence.worstMs >= evidence.p95Ms)
        assertEquals("candidate-reporting-only", evidence.gateStatus)
        assertEquals(false, evidence.countedAsMeasuredGate)
        assertContains(json, "\"packId\":\"m84-native-frame-timing-candidate-v1\"")
        assertContains(json, "\"status\":\"measured\"")
        assertContains(json, "\"m84.reporting-only-until-owner-accepts-variance\"")
        assertContains(json, "\"estimatedMetricCount\":0")
    }

    @Test
    fun negativeFixtureFailsThresholdWithoutMutatingBaseline() {
        val evidence = buildM84FrameTimingEvidence(Path("..").toAbsolutePath().normalize())
        val negative = evidence.negativeFixture().toString()

        assertContains(negative, "\"status\":\"expected-fail\"")
        assertContains(negative, "\"reason\":\"m84.negative-fixture-p95-threshold-exceeded\"")
        assertContains(negative, "\"mutatesBaseline\":false")
    }
}
