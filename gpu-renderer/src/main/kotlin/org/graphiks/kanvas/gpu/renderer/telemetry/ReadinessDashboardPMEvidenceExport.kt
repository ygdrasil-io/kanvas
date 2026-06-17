package org.graphiks.kanvas.gpu.renderer.telemetry

import java.io.File
import java.security.MessageDigest

private const val DASHBOARD_LINES_ARTIFACT = "gpu-renderer-readiness-dashboard-lines.txt"
private const val SUMMARY_ARTIFACT = "gpu-renderer-readiness-dashboard-summary.json"
private const val OUTPUT_MANIFEST_ENTRY = "pm-bundle-manifest-entry.json"

/**
 * Command-line entry point for exporting the KGPU-M9-003 PM readiness dashboard evidence bundle.
 *
 * The bundle is reporting-only: it materializes dashboard rows for PM review without requiring a
 * WebGPU adapter, adding release-blocking gates, activating product routes, or moving readiness.
 */
fun main(args: Array<String>) {
    require(args.size <= 1) {
        "usage: ReadinessDashboardPMEvidenceExportKt [outputDirectory]"
    }

    val outputDirectory = args.firstOrNull()
        ?.let(::File)
        ?: File("build/reports/gpu-renderer-m9-readiness-pm-evidence")
    val written = writeRendererReadinessPMEvidenceBundle(outputDirectory)

    println(
        "gpu-renderer M9 readiness PM evidence bundle written " +
            "root=${outputDirectory.path} artifacts=${written.joinToString(",")}",
    )
}

/** Writes the KGPU-M9-003 PM readiness evidence bundle. */
fun writeRendererReadinessPMEvidenceBundle(outputDirectory: File): List<String> {
    require(outputDirectory.path.isNotBlank()) {
        "GPU renderer readiness evidence output directory must not be blank"
    }
    if (outputDirectory.exists()) {
        require(outputDirectory.isDirectory) {
            "GPU renderer readiness evidence output path is not a directory: ${outputDirectory.path}"
        }
        outputDirectory.deleteRecursively()
    }
    require(outputDirectory.mkdirs() || outputDirectory.isDirectory) {
        "GPU renderer readiness evidence output directory could not be created: ${outputDirectory.path}"
    }

    val dashboard = defaultRendererReadinessDashboard()
    val linesFile = outputDirectory.resolve(DASHBOARD_LINES_ARTIFACT)
    linesFile.writeText(dashboard.dumpLines().joinToString("\n") + "\n")

    val summaryFile = outputDirectory.resolve(SUMMARY_ARTIFACT)
    summaryFile.writeText(defaultRendererReadinessSummaryJson(dashboard, linesFile.sha256()) + "\n")

    val sidecarFile = outputDirectory.resolve(OUTPUT_MANIFEST_ENTRY)
    sidecarFile.writeText(defaultRendererReadinessManifestEntryJson() + "\n")

    return listOf(
        DASHBOARD_LINES_ARTIFACT,
        SUMMARY_ARTIFACT,
        OUTPUT_MANIFEST_ENTRY,
    )
}

private fun defaultRendererReadinessDashboard(): GPURendererReadinessDashboard =
    GPURendererReadinessDashboardIntegrator.integrate(
        dashboardId = "m9-gpu-renderer-readiness",
        correctnessEvidenceRows = listOf("reports/gpu-renderer/2026-06-14-r6-promotion-readiness-boundary.md"),
        activationEvidenceRows = listOf("pipelinePmBundle"),
        cacheReport = defaultCacheSourceMapReport(),
        frameGatePolicyReport = defaultFrameGatePolicyReport(),
    )

private fun defaultCacheSourceMapReport(): GPUCacheTelemetrySourceMapReport =
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
                counterName = "frame.cache.reporting",
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
    )

private fun defaultFrameGatePolicyReport(): GPUFrameGatePolicyReport =
    GPUFrameGatePolicyEvaluator.evaluate(
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
                targetState = GPUFrameGateState.Candidate,
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

private fun defaultRendererReadinessSummaryJson(
    dashboard: GPURendererReadinessDashboard,
    dashboardLinesSha256: String,
): String =
    """
    {
      "artifacts": {
        "$DASHBOARD_LINES_ARTIFACT": "$dashboardLinesSha256"
      },
      "classification": "${dashboard.classification}",
      "dashboardId": "${dashboard.dashboardId}",
      "evidenceRow": "${dashboard.evidenceRow}",
      "nonClaims": [
        "No readiness delta.",
        "No release-blocking gate.",
        "No product activation.",
        "No correctness support inferred from performance evidence.",
        "No derived cache telemetry counted as observed.",
        "No dashboard row promotes readiness."
      ],
      "productRouteActivated": false,
      "readinessDelta": 0.0,
      "releaseBlocking": false,
      "rows": [
    ${dashboard.rows.joinToString(",\n") { row -> row.summaryJsonLine() }}
      ]
    }
    """.trimIndent()

private fun GPURendererReadinessDashboardRow.summaryJsonLine(): String =
    """    {"area": "$area", "source": "$source", "state": "$state"}"""

private fun defaultRendererReadinessManifestEntryJson(): String =
    """
    {
      "artifactDirectory": "release/gpu-renderer-m9-readiness-pm-evidence",
      "claimLevel": "gpu-renderer-m9-readiness-dashboard",
      "classification": "PolicyGated",
      "dashboardId": "m9-gpu-renderer-readiness",
      "dashboardLinesArtifact": "release/gpu-renderer-m9-readiness-pm-evidence/$DASHBOARD_LINES_ARTIFACT",
      "dashboardRows": [
        "correctness",
        "activation",
        "performance",
        "cache",
        "release"
      ],
      "evidenceRow": "gpu-renderer.readiness",
      "generationCommand": "rtk ./gradlew --no-daemon :gpu-renderer:gpuRendererM9ReadinessPmEvidenceBundle",
      "key": "gpuRendererM9ReadinessPmEvidence",
      "manifestEntryJson": "release/gpu-renderer-m9-readiness-pm-evidence/$OUTPUT_MANIFEST_ENTRY",
      "nativeKadreCiRequired": false,
      "nonClaims": [
        "No readiness delta.",
        "No release-blocking gate.",
        "No product activation.",
        "No correctness support inferred from performance evidence.",
        "No derived cache telemetry counted as observed.",
        "No dashboard row promotes readiness."
      ],
      "notice": "GPU renderer M9 readiness dashboard integration separates correctness, activation, performance, cache, and release visibility without moving readiness.",
      "pmPackageCommand": "rtk ./gradlew --no-daemon pipelinePmBundle",
      "productRouteActivated": false,
      "readinessDelta": 0.0,
      "releaseBlocking": false,
      "status": "PolicyGated",
      "summaryArtifact": "release/gpu-renderer-m9-readiness-pm-evidence/$SUMMARY_ARTIFACT",
      "webGpuAdapterRequired": false
    }
    """.trimIndent()

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(readBytes())
    return "sha256:" + digest.joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}
