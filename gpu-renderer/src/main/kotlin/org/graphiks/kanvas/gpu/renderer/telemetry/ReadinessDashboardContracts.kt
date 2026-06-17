package org.graphiks.kanvas.gpu.renderer.telemetry

/** One PM dashboard row for GPU renderer readiness evidence. */
data class GPURendererReadinessDashboardRow(
    val area: String,
    val state: String,
    val source: String,
    val classification: String = "PolicyGated",
    val readinessDelta: Double = 0.0,
    val releaseBlocking: Boolean = false,
    val productRouteActivated: Boolean = false,
    val reportingOnly: Boolean = true,
) {
    init {
        require(area.isNotBlank()) { "GPU renderer readiness dashboard area must not be blank" }
        require(state.isNotBlank()) { "GPU renderer readiness dashboard state must not be blank" }
        require(source.isNotBlank()) { "GPU renderer readiness dashboard source must not be blank" }
        require(classification == "PolicyGated") {
            "GPU renderer readiness dashboard rows must stay PolicyGated"
        }
        require(readinessDelta == 0.0) {
            "GPU renderer readiness dashboard rows must not move readiness"
        }
        require(!releaseBlocking) {
            "GPU renderer readiness dashboard rows must not create release-blocking gates"
        }
        require(!productRouteActivated) {
            "GPU renderer readiness dashboard rows must not activate product routes"
        }
    }

    /** Returns one deterministic dashboard row line. */
    fun dumpLine(): String =
        "readiness-row area=$area state=$state classification=$classification source=$source " +
            "readinessDelta=$readinessDelta releaseBlocking=$releaseBlocking " +
            "productRouteActivated=$productRouteActivated reportingOnly=$reportingOnly"
}

/** PM dashboard integration for GPU renderer readiness visibility. */
data class GPURendererReadinessDashboard(
    val dashboardId: String,
    val rows: List<GPURendererReadinessDashboardRow>,
    val evidenceRow: String = "gpu-renderer.readiness",
    val classification: String = "PolicyGated",
    val readinessDelta: Double = 0.0,
    val releaseBlocking: Boolean = false,
    val productRouteActivated: Boolean = false,
) {
    init {
        require(dashboardId.isNotBlank()) { "GPU renderer readiness dashboard id must not be blank" }
        require(rows.isNotEmpty()) { "GPU renderer readiness dashboard rows must not be empty" }
        require(rows.map { row -> row.area }.distinct().size == rows.size) {
            "GPU renderer readiness dashboard areas must be unique"
        }
        require(classification == "PolicyGated") { "GPU renderer readiness dashboard must stay PolicyGated" }
        require(readinessDelta == 0.0) { "GPU renderer readiness dashboard must not move readiness" }
        require(!releaseBlocking) { "GPU renderer readiness dashboard must not create release-blocking gates" }
        require(!productRouteActivated) { "GPU renderer readiness dashboard must not activate product routes" }
    }

    /** Returns deterministic dashboard lines for PM evidence and validators. */
    fun dumpLines(): List<String> {
        val states = rows.associate { row -> row.area to row.state }
        return listOf(
            "readiness-dashboard id=$dashboardId row=$evidenceRow classification=$classification " +
                "rows=${rows.size} readinessDelta=$readinessDelta releaseBlocking=$releaseBlocking " +
                "productRouteActivated=$productRouteActivated",
        ) + rows.map { row -> row.dumpLine() } + listOf(
            "pm:gpu-renderer.readiness classification=$classification " +
                "correctness=${states["correctness"] ?: "missing-gate"} " +
                "activation=${states["activation"] ?: "missing-gate"} " +
                "performance=${states["performance"] ?: "missing-gate"} " +
                "cache=${states["cache"] ?: "missing-gate"} " +
                "release=${states["release"] ?: "missing-gate"} " +
                "readinessDelta=$readinessDelta releaseBlocking=$releaseBlocking",
            "nonclaim:no-readiness-delta no-release-blocking-gate no-product-activation " +
                "no-correctness-from-performance no-cache-derived-as-observed no-dashboard-promotion",
        )
    }
}

/** Builds non-promotional PM readiness dashboards from accepted M9 telemetry policy reports. */
object GPURendererReadinessDashboardIntegrator {
    /** Integrates correctness, activation, cache, performance, and release visibility rows. */
    fun integrate(
        dashboardId: String,
        correctnessEvidenceRows: List<String>,
        activationEvidenceRows: List<String>,
        cacheReport: GPUCacheTelemetrySourceMapReport,
        frameGatePolicyReport: GPUFrameGatePolicyReport,
    ): GPURendererReadinessDashboard {
        require(cacheReport.readinessDelta == 0.0) {
            "GPU renderer readiness dashboard cannot ingest cache readiness movement"
        }
        require(!cacheReport.releaseBlocking) {
            "GPU renderer readiness dashboard cannot ingest release-blocking cache rows"
        }
        require(!cacheReport.productRouteActivated) {
            "GPU renderer readiness dashboard cannot ingest cache product activation"
        }
        require(frameGatePolicyReport.readinessDelta == 0.0) {
            "GPU renderer readiness dashboard cannot ingest frame readiness movement"
        }
        require(!frameGatePolicyReport.releaseBlocking) {
            "GPU renderer readiness dashboard cannot ingest release-blocking frame gates"
        }
        require(!frameGatePolicyReport.productRouteActivated) {
            "GPU renderer readiness dashboard cannot ingest frame product activation"
        }

        return GPURendererReadinessDashboard(
            dashboardId = dashboardId,
            rows = listOf(
                GPURendererReadinessDashboardRow(
                    area = "correctness",
                    state = if (correctnessEvidenceRows.isEmpty()) "missing-gate" else "evidence-present",
                    source = correctnessEvidenceRows.stableDashboardSource("missing:correctness-evidence"),
                ),
                GPURendererReadinessDashboardRow(
                    area = "activation",
                    state = if (activationEvidenceRows.isEmpty()) "missing-gate" else "policy-gated",
                    source = activationEvidenceRows.stableDashboardSource("missing:activation-policy"),
                ),
                GPURendererReadinessDashboardRow(
                    area = "performance",
                    state = frameGatePolicyReport.performanceDashboardState(),
                    source = frameGatePolicyReport.gateId,
                ),
                GPURendererReadinessDashboardRow(
                    area = "cache",
                    state = cacheReport.cacheDashboardState(),
                    source = cacheReport.mapId,
                ),
                GPURendererReadinessDashboardRow(
                    area = "release",
                    state = "non-release-blocking",
                    source = frameGatePolicyReport.gateId,
                ),
            ),
        )
    }

    private fun GPUCacheTelemetrySourceMapReport.cacheDashboardState(): String =
        when {
            observedReadinessCounters().isNotEmpty() &&
                entries.any { entry -> !entry.countsForObservedReadiness } -> "observed-and-reporting"
            observedReadinessCounters().isNotEmpty() -> "observed"
            entries.isNotEmpty() -> "reporting-only"
            else -> "missing-gate"
        }

    private fun GPUFrameGatePolicyReport.performanceDashboardState(): String =
        when {
            lanes.any { lane -> lane.classification == GPUFrameGateLaneClassification.Candidate } ->
                "candidate-nonblocking"
            lanes.any { lane -> lane.classification == GPUFrameGateLaneClassification.ThresholdFailed } ->
                "threshold-not-ready"
            lanes.any { lane -> lane.classification == GPUFrameGateLaneClassification.VarianceExceeded } ->
                "variance-not-ready"
            lanes.any { lane -> lane.classification == GPUFrameGateLaneClassification.Quarantined } ->
                "quarantined"
            lanes.any { lane -> lane.classification == GPUFrameGateLaneClassification.Skipped } ->
                "skipped"
            lanes.any { lane -> lane.classification == GPUFrameGateLaneClassification.ReportingOnly } ->
                "reporting-only"
            else -> "missing-gate"
        }
}

private fun List<String>.stableDashboardSource(fallback: String): String =
    if (isEmpty()) {
        fallback
    } else {
        sorted().joinToString(",")
    }
