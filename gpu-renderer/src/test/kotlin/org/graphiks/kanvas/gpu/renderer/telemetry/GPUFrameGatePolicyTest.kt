package org.graphiks.kanvas.gpu.renderer.telemetry

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Verifies frame timing gates stay explicit and non-promotional until accepted. */
class GPUFrameGatePolicyTest {
    /** Candidate, reporting-only, quarantined, and skipped lanes cannot become release blockers. */
    @Test
    fun `frame gate policy records provenance warmup variance quarantine and skipped lanes`() {
        val report = GPUFrameGatePolicyEvaluator.evaluate(
            gateId = "m9-frame-gate-policy",
            warmupPolicy = frameWarmupPolicy(),
            lanes = listOf(
                frameLane(
                    laneId = "owned-adapter-candidate",
                    targetState = GPUFrameGateState.Candidate,
                    stableFrameMs = listOf(10.5, 10.5, 10.5, 10.5),
                    sourceArtifactLabel = "fixtures/m9-frame-gate-owned-samples.json",
                    sourceHash = frameGateOwnedSampleFixtureSha,
                    adapterLabel = "apple-m2-max",
                ),
                frameLane(
                    laneId = "windowed-frame-count-reporting",
                    targetState = GPUFrameGateState.Candidate,
                    sourceKind = "reporting-only",
                    stableFrameMs = emptyList(),
                    reportingOnly = true,
                    sourceArtifactLabel = "fixtures/m9-frame-gate-owned-samples.json",
                    sourceHash = frameGateOwnedSampleFixtureSha,
                    adapterLabel = "apple-m2-max",
                    rawSampleCount = 0,
                    warmupFrameCount = 0,
                    stableFrameCount = 0,
                ),
                frameLane(
                    laneId = "local-adapter-quarantine",
                    targetState = GPUFrameGateState.ReleaseBlocking,
                    stableFrameMs = listOf(11.0, 11.0, 11.0, 11.0),
                    sourceArtifactLabel = "fixtures/m9-frame-gate-owned-samples.json",
                    sourceHash = frameGateOwnedSampleFixtureSha,
                    adapterLabel = "apple-m2-max",
                    quarantineReasons = listOf("local-adapter-not-release-lane"),
                ),
                frameLane(
                    laneId = "timestamp-query-missing",
                    targetState = GPUFrameGateState.ReleaseBlocking,
                    sourceKind = "owned-adapter-frame-samples",
                    stableFrameMs = emptyList(),
                    sourceArtifactLabel = "missing:timestamp-query-samples",
                    sourceHash = null,
                    adapterLabel = null,
                    skipReasons = listOf("timestamp-query-unavailable"),
                    rawSampleCount = 0,
                    warmupFrameCount = 0,
                    stableFrameCount = 0,
                ),
            ),
        )

        assertEquals(
            listOf(
                GPUFrameGateLaneClassification.Candidate,
                GPUFrameGateLaneClassification.ReportingOnly,
                GPUFrameGateLaneClassification.Quarantined,
                GPUFrameGateLaneClassification.Skipped,
            ),
            report.lanes.map { lane -> lane.classification },
        )
        assertEquals(emptyList(), report.releaseBlockingLaneIds())
        assertFalse(report.releaseBlocking)
        assertFalse(report.productRouteActivated)
        assertEquals(0.0, report.readinessDelta)
        assertEquals(
            listOf(
                "frame-gate-policy id=m9-frame-gate-policy lanes=4 warmupFrames=3 stableFrames=4 metric=frame-time-ms source=wall-clock thresholdMs=16.6700 maxCov=0.0500 quarantineRule=known-env-or-adapter-issue-only rebaselineRule=versioned-artifact-required releaseBlocking=false productRouteActivated=false readinessDelta=0.0",
                "frame-gate-lane id=owned-adapter-candidate state=candidate classification=candidate source=fixtures/m9-frame-gate-owned-samples.json kind=owned-adapter-frame-samples hash=$frameGateOwnedSampleFixtureSha scene=frame-gate-blocker-board adapter=apple-m2-max rawSamples=7 warmup=3 stable=4 meanMs=10.5000 cov=0.0000 thresholdStatus=within-threshold countsRelease=false skip=- quarantine=-",
                "frame-gate-lane id=windowed-frame-count-reporting state=candidate classification=reporting-only source=fixtures/m9-frame-gate-owned-samples.json kind=reporting-only hash=$frameGateOwnedSampleFixtureSha scene=frame-gate-blocker-board adapter=apple-m2-max rawSamples=0 warmup=0 stable=0 meanMs=none cov=none thresholdStatus=not-evaluated countsRelease=false skip=- quarantine=-",
                "frame-gate-lane id=local-adapter-quarantine state=release-blocking classification=quarantined source=fixtures/m9-frame-gate-owned-samples.json kind=owned-adapter-frame-samples hash=$frameGateOwnedSampleFixtureSha scene=frame-gate-blocker-board adapter=apple-m2-max rawSamples=7 warmup=3 stable=4 meanMs=11.0000 cov=0.0000 thresholdStatus=not-evaluated countsRelease=false skip=- quarantine=local-adapter-not-release-lane",
                "frame-gate-lane id=timestamp-query-missing state=release-blocking classification=skipped source=missing:timestamp-query-samples kind=owned-adapter-frame-samples hash=none scene=frame-gate-blocker-board adapter=none rawSamples=0 warmup=0 stable=0 meanMs=none cov=none thresholdStatus=not-evaluated countsRelease=false skip=missing-adapter-label,missing-source-hash,timestamp-query-unavailable quarantine=-",
                "pm:gpu-renderer.frame-gate-policy classification=PolicyGated candidate=1 releaseBlocking=0 reportingOnly=1 quarantined=1 skipped=1 thresholdFailed=0 varianceExceeded=0 readinessDelta=0.0 releaseBlocking=false",
                "nonclaim:no-release-blocking-gate no-readiness-delta no-product-activation no-correctness-claim no-derived-timings",
            ),
            report.dumpLines(),
        )
    }

    /** A release-blocking lane with owned samples still fails closed when threshold is exceeded. */
    @Test
    fun `negative threshold fixture fails closed without creating release blocking authority`() {
        val report = GPUFrameGatePolicyEvaluator.evaluate(
            gateId = "m9-frame-gate-negative-fixture",
            warmupPolicy = frameWarmupPolicy(),
            lanes = listOf(
                frameLane(
                    laneId = "negative-over-threshold",
                    targetState = GPUFrameGateState.ReleaseBlocking,
                    stableFrameMs = listOf(21.0, 21.0, 21.0, 21.0),
                    sourceArtifactLabel = "fixtures/m9-frame-gate-negative.json",
                    sourceHash = "sha256:negative-frame-fixture",
                    adapterLabel = "owned-ci-adapter",
                ),
            ),
        )

        val lane = report.lanes.single()
        assertEquals(GPUFrameGateLaneClassification.ThresholdFailed, lane.classification)
        assertEquals(GPUFrameGateThresholdStatus.ThresholdFailed, lane.thresholdStatus)
        assertFalse(lane.countsForReleaseGate)
        assertFalse(report.releaseBlocking)
        assertContains(
            report.dumpLines(),
            "pm:gpu-renderer.frame-gate-policy classification=PolicyGated candidate=0 releaseBlocking=0 reportingOnly=0 quarantined=0 skipped=0 thresholdFailed=1 varianceExceeded=0 readinessDelta=0.0 releaseBlocking=false",
        )
    }

    /** Policy facts reject ambiguous gate and provenance data before PM dumps can claim them. */
    @Test
    fun `frame gate policy rejects blank ids invalid thresholds and sample count mismatches`() {
        assertFailsWith<IllegalArgumentException> {
            frameWarmupPolicy(stableFrameCount = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUFrameSampleProvenance(
                sourceArtifactLabel = "",
                sourceKind = "owned-adapter-frame-samples",
                sourceHash = "sha256:frame",
                sceneId = "frame-gate-blocker-board",
                adapterLabel = "owned-adapter",
                rawSampleCount = 7,
                warmupFrameCount = 3,
                stableFrameCount = 4,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUFrameGatePolicyEvaluator.evaluate(
                gateId = "",
                warmupPolicy = frameWarmupPolicy(),
                lanes = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            frameLane(
                laneId = "mismatch",
                stableFrameMs = listOf(10.0, 10.0),
                sourceHash = frameGateOwnedSampleFixtureSha,
                adapterLabel = "apple-m2-max",
            )
        }
    }
}

private fun frameWarmupPolicy(
    stableFrameCount: Int = 4,
): GPUFrameGateWarmupPolicy =
    GPUFrameGateWarmupPolicy(
        warmupFrameCount = 3,
        stableFrameCount = stableFrameCount,
        metricName = "frame-time-ms",
        metricSource = "wall-clock",
        thresholdMs = 16.67,
        maxCoefficientOfVariation = 0.05,
        quarantineRule = "known-env-or-adapter-issue-only",
        rebaselineRule = "versioned-artifact-required",
    )

private const val frameGateOwnedSampleFixtureSha =
    "sha256:test-owned-frame-samples"

private fun frameLane(
    laneId: String,
    targetState: GPUFrameGateState = GPUFrameGateState.Candidate,
    sourceKind: String = "owned-adapter-frame-samples",
    sourceArtifactLabel: String = "reports/gpu-renderer-scenes/windowed/frame-gate-blocker-board/session.json",
    sourceHash: String? = null,
    sceneId: String = "frame-gate-blocker-board",
    adapterLabel: String? = null,
    rawSampleCount: Int = 7,
    warmupFrameCount: Int = 3,
    stableFrameCount: Int = 4,
    stableFrameMs: List<Double> = listOf(10.5, 10.5, 10.5, 10.5),
    reportingOnly: Boolean = false,
    quarantineReasons: List<String> = emptyList(),
    skipReasons: List<String> = emptyList(),
): GPUFrameGateLaneRequest =
    GPUFrameGateLaneRequest(
        laneId = laneId,
        targetState = targetState,
        provenance = GPUFrameSampleProvenance(
            sourceArtifactLabel = sourceArtifactLabel,
            sourceKind = sourceKind,
            sourceHash = sourceHash,
            sceneId = sceneId,
            adapterLabel = adapterLabel,
            rawSampleCount = rawSampleCount,
            warmupFrameCount = warmupFrameCount,
            stableFrameCount = stableFrameCount,
        ),
        stableFrameMs = stableFrameMs,
        reportingOnly = reportingOnly,
        quarantineReasons = quarantineReasons,
        skipReasons = skipReasons,
    )
