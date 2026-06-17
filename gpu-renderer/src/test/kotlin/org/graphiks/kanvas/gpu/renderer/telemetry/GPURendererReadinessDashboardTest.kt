package org.graphiks.kanvas.gpu.renderer.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Verifies PM readiness dashboards keep correctness, activation, performance, cache, and release separate. */
class GPURendererReadinessDashboardTest {
    @Test
    fun `readiness dashboard separates reporting rows without moving readiness`() {
        val cacheReport = cacheSourceMapReport()
        val frameReport = frameGatePolicyReport()

        val dashboard = GPURendererReadinessDashboardIntegrator.integrate(
            dashboardId = "m9-gpu-renderer-readiness",
            correctnessEvidenceRows = listOf("reports/gpu-renderer/2026-06-14-r6-promotion-readiness-boundary.md"),
            activationEvidenceRows = listOf("pipelinePmBundle"),
            cacheReport = cacheReport,
            frameGatePolicyReport = frameReport,
        )

        assertEquals("gpu-renderer.readiness", dashboard.evidenceRow)
        assertEquals("PolicyGated", dashboard.classification)
        assertEquals(0.0, dashboard.readinessDelta)
        assertFalse(dashboard.releaseBlocking)
        assertFalse(dashboard.productRouteActivated)
        assertEquals(
            listOf("correctness", "activation", "performance", "cache", "release"),
            dashboard.rows.map { row -> row.area },
        )
        assertEquals(
            listOf(
                "readiness-dashboard id=m9-gpu-renderer-readiness row=gpu-renderer.readiness classification=PolicyGated rows=5 readinessDelta=0.0 releaseBlocking=false productRouteActivated=false",
                "readiness-row area=correctness state=evidence-present classification=PolicyGated source=reports/gpu-renderer/2026-06-14-r6-promotion-readiness-boundary.md readinessDelta=0.0 releaseBlocking=false productRouteActivated=false reportingOnly=true",
                "readiness-row area=activation state=policy-gated classification=PolicyGated source=pipelinePmBundle readinessDelta=0.0 releaseBlocking=false productRouteActivated=false reportingOnly=true",
                "readiness-row area=performance state=candidate-nonblocking classification=PolicyGated source=m9-frame-gate-policy readinessDelta=0.0 releaseBlocking=false productRouteActivated=false reportingOnly=true",
                "readiness-row area=cache state=observed-and-reporting classification=PolicyGated source=m9-cache-source-map readinessDelta=0.0 releaseBlocking=false productRouteActivated=false reportingOnly=true",
                "readiness-row area=release state=non-release-blocking classification=PolicyGated source=m9-frame-gate-policy readinessDelta=0.0 releaseBlocking=false productRouteActivated=false reportingOnly=true",
                "pm:gpu-renderer.readiness classification=PolicyGated correctness=evidence-present activation=policy-gated performance=candidate-nonblocking cache=observed-and-reporting release=non-release-blocking readinessDelta=0.0 releaseBlocking=false",
                "nonclaim:no-readiness-delta no-release-blocking-gate no-product-activation no-correctness-from-performance no-cache-derived-as-observed no-dashboard-promotion",
            ),
            dashboard.dumpLines(),
        )
    }

    @Test
    fun `dashboard refuses source reports and rows that would move readiness`() {
        assertFailsWith<IllegalArgumentException> {
            GPURendererReadinessDashboardRow(
                area = "performance",
                state = "candidate",
                source = "manual",
                readinessDelta = 0.1,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPURendererReadinessDashboardRow(
                area = "release",
                state = "release-blocking",
                source = "manual",
                releaseBlocking = true,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPURendererReadinessDashboardIntegrator.integrate(
                dashboardId = "m9-gpu-renderer-readiness",
                correctnessEvidenceRows = listOf("reports/gpu-renderer/2026-06-14-r6-promotion-readiness-boundary.md"),
                activationEvidenceRows = listOf("pipelinePmBundle"),
                cacheReport = cacheSourceMapReport(readinessDelta = 0.25),
                frameGatePolicyReport = frameGatePolicyReport(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPURendererReadinessDashboardIntegrator.integrate(
                dashboardId = "m9-gpu-renderer-readiness",
                correctnessEvidenceRows = listOf("reports/gpu-renderer/2026-06-14-r6-promotion-readiness-boundary.md"),
                activationEvidenceRows = listOf("pipelinePmBundle"),
                cacheReport = cacheSourceMapReport(),
                frameGatePolicyReport = frameGatePolicyReport(releaseBlocking = true),
            )
        }
    }
}

private fun cacheSourceMapReport(
    readinessDelta: Double = 0.0,
): GPUCacheTelemetrySourceMapReport =
    GPUCacheTelemetrySourceMapReport(
        mapId = "m9-cache-source-map",
        entries = listOf(
            GPUCacheTelemetrySourceMapEntry(
                counterName = "pipeline.cache.hit_rate",
                cacheDomain = "pipeline",
                classification = GPUCacheTelemetrySourceClassification.Observed,
                sourceArtifactLabel = "artifact:executed-webgpu-cache.json",
                sourceKind = "adapter-runtime-artifact",
                sourceHash = "sha256:pipeline-cache",
                requiredFields = setOf("hits", "misses"),
                observedFields = setOf("hits", "misses"),
                derivedFrom = emptyList(),
                countsForObservedReadiness = true,
            ),
            GPUCacheTelemetrySourceMapEntry(
                counterName = "pipeline.cache.commentary",
                cacheDomain = "pipeline",
                classification = GPUCacheTelemetrySourceClassification.ReportingOnly,
                sourceArtifactLabel = "policy:m9-cache-reporting-only",
                sourceKind = "reporting-only",
                sourceHash = null,
                requiredFields = emptySet(),
                observedFields = emptySet(),
                derivedFrom = emptyList(),
                countsForObservedReadiness = false,
            ),
        ),
        readinessDelta = readinessDelta,
    )

private fun frameGatePolicyReport(
    releaseBlocking: Boolean = false,
): GPUFrameGatePolicyReport {
    val targetState = if (releaseBlocking) {
        GPUFrameGateState.ReleaseBlocking
    } else {
        GPUFrameGateState.Candidate
    }
    return GPUFrameGatePolicyEvaluator.evaluate(
        gateId = "m9-frame-gate-policy",
        warmupPolicy = GPUFrameGateWarmupPolicy(
            warmupFrameCount = 3,
            stableFrameCount = 4,
            metricName = "frame-time-ms",
            metricSource = "wall-clock",
            thresholdMs = 16.67,
            maxCoefficientOfVariation = 0.05,
            quarantineRule = "known-env-or-adapter-issue-only",
            rebaselineRule = "versioned-artifact-required",
        ),
        lanes = listOf(
            GPUFrameGateLaneRequest(
                laneId = "owned-adapter-candidate",
                targetState = targetState,
                provenance = GPUFrameSampleProvenance(
                    sourceArtifactLabel = "fixtures/m9-frame-gate-owned-samples.json",
                    sourceKind = "owned-adapter-frame-samples",
                    sourceHash = "sha256:test-owned-frame-samples",
                    sceneId = "frame-gate-blocker-board",
                    adapterLabel = "apple-m2-max",
                    rawSampleCount = 7,
                    warmupFrameCount = 3,
                    stableFrameCount = 4,
                ),
                stableFrameMs = listOf(10.5, 10.5, 10.5, 10.5),
            ),
        ),
    )
}
