package org.graphiks.kanvas.gpu.renderer.telemetry

/** Counter observed by GPU renderer telemetry. */
data class GPUTelemetryCounter(
    val name: String,
    val value: Long,
    val unit: String,
    val scope: String,
)

/** Cache telemetry facts. */
data class GPUCacheTelemetry(
    val cacheName: String,
    val hits: Long,
    val misses: Long,
    val evictions: Long,
    val residentBytes: Long,
    val pressureBytes: Long,
)

/** Budget telemetry facts. */
data class GPUBudgetTelemetry(
    val budgetName: String,
    val requested: Long,
    val limit: Long,
    val unit: String,
    val exceeded: Boolean,
)

/** Evidence gathered for feature promotion. */
data class GPUPromotionEvidence(
    val evidenceId: String,
    val routeKindLabel: String,
    val artifactPaths: List<String>,
    val diagnosticCodes: List<String>,
)

/** Performance gate observation, not a route decision. */
data class GPUPerformanceGate(
    val gateName: String,
    val metricName: String,
    val threshold: Double,
    val comparator: String,
    val status: String,
)

/** Telemetry ledger for one renderer scope. */
data class GPUTelemetryLedger(
    val counters: List<GPUTelemetryCounter>,
    val cacheTelemetry: List<GPUCacheTelemetry>,
    val budgetTelemetry: List<GPUBudgetTelemetry>,
    val promotionEvidence: List<GPUPromotionEvidence>,
)
