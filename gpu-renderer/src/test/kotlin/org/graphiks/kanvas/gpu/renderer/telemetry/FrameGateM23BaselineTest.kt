package org.graphiks.kanvas.gpu.renderer.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrameGateM23BaselineTest {

    @Test
    fun `m23 baseline threshold matches 60 fps target`() {
        val policy = GPUFrameGateWarmupPolicy.m23Baseline()
        assertEquals(16.6667, policy.thresholdMs, 0.001)
    }

    @Test
    fun `m23 baseline quarantine rule references m series`() {
        val policy = GPUFrameGateWarmupPolicy.m23Baseline()
        assertTrue(policy.quarantineRule.contains("m-series"))
    }

    @Test
    fun `m23 baseline has 3 warmup and 4 stable frames`() {
        val policy = GPUFrameGateWarmupPolicy.m23Baseline()
        assertEquals(3, policy.warmupFrameCount)
        assertEquals(4, policy.stableFrameCount)
    }

    @Test
    fun `m23 constant threshold matches target fps`() {
        assertEquals(16.6667, M23_THRESHOLD_MS, 0.001)
    }

    @Test
    fun `m23 warning threshold matches 30 fps`() {
        assertEquals(33.3333, M23_WARNING_THRESHOLD_MS, 0.001)
    }

    @Test
    fun `m23 adapter constant is apple m series`() {
        assertEquals("apple-m-series", M23_APPLE_M_SERIES_ADAPTER)
    }

    @Test
    fun `m23 baseline has max Cov of 0 dot 05`() {
        val policy = GPUFrameGateWarmupPolicy.m23Baseline()
        assertEquals(0.05, policy.maxCoefficientOfVariation)
    }

    @Test
    fun `m23 baseline lane within threshold passes`() {
        val policy = GPUFrameGateWarmupPolicy.m23Baseline()
        val report = GPUFrameGatePolicyEvaluator.evaluate(
            gateId = "m23-sixty-fps-test",
            warmupPolicy = policy,
            lanes = listOf(
                GPUFrameGateLaneRequest(
                    laneId = "sixty-fps-lane",
                    targetState = GPUFrameGateState.ReleaseBlocking,
                    provenance = GPUFrameSampleProvenance(
                        sourceArtifactLabel = "fixtures/m23-frame-samples.json",
                        sourceKind = "owned-adapter-frame-samples",
                        sourceHash = "sha256:m23-frame-samples",
                        sceneId = "frame-gate-m23-baseline",
                        adapterLabel = "apple-m2-max",
                        rawSampleCount = 7,
                        warmupFrameCount = 3,
                        stableFrameCount = 4,
                    ),
                    stableFrameMs = listOf(10.0, 10.5, 10.2, 10.3),
                ),
            ),
        )
        val lane = report.lanes.single()
        assertEquals(GPUFrameGateLaneClassification.ReleaseBlocking, lane.classification)
        assertTrue(lane.countsForReleaseGate)
    }

    @Test
    fun `m23 lane exceeding threshold fails`() {
        val policy = GPUFrameGateWarmupPolicy.m23Baseline()
        val report = GPUFrameGatePolicyEvaluator.evaluate(
            gateId = "m23-threshold-fail-test",
            warmupPolicy = policy,
            lanes = listOf(
                GPUFrameGateLaneRequest(
                    laneId = "over-threshold-lane",
                    targetState = GPUFrameGateState.ReleaseBlocking,
                    provenance = GPUFrameSampleProvenance(
                        sourceArtifactLabel = "fixtures/m23-threshold-fail.json",
                        sourceKind = "owned-adapter-frame-samples",
                        sourceHash = "sha256:m23-threshold-fail",
                        sceneId = "frame-gate-m23-baseline",
                        adapterLabel = "apple-m2-max",
                        rawSampleCount = 7,
                        warmupFrameCount = 3,
                        stableFrameCount = 4,
                    ),
                    stableFrameMs = listOf(25.0, 26.0, 24.0, 25.5),
                ),
            ),
        )
        val lane = report.lanes.single()
        assertEquals(GPUFrameGateLaneClassification.ThresholdFailed, lane.classification)
        assertEquals(GPUFrameGateThresholdStatus.ThresholdFailed, lane.thresholdStatus)
        assertFalse(lane.countsForReleaseGate)
        assertFalse(report.releaseBlocking)
    }
}
