package org.graphiks.kanvas.gpu.evidence.performance

enum class PerformanceTier { P0, P1, Inventory }

data class PerformanceBudget(
    val family: String,
    val tier: PerformanceTier,
    val p50FrameNanos: Long,
    val p95FrameNanos: Long,
    val maxAllocations: Long,
    val maxPipelineBuilds: Long,
    val maxUploadBytes: Long,
    val maxReadbackBytes: Long,
) {
    init {
        require(family.isNotBlank())
        require(p50FrameNanos >= 0L && p95FrameNanos >= p50FrameNanos)
        require(maxAllocations >= 0L && maxPipelineBuilds >= 0L)
        require(maxUploadBytes >= 0L && maxReadbackBytes >= 0L)
    }
}

data class PerformanceTierMetric(val value: Long?, val source: MetricSource, val reason: String? = null)

data class PerformanceTierEvaluation(
    val family: String,
    val tier: PerformanceTier,
    val status: String,
    val metrics: Map<String, PerformanceTierMetric>,
    val diagnostics: List<String>,
    val countsAsReleaseGate: Boolean,
)

/** Reporting-only classification over the existing headless GPU evidence run. */
object PerformanceTiering {
    fun evaluate(run: PerformanceRun, budget: PerformanceBudget): PerformanceTierEvaluation {
        val measured = run.telemetry.measured.delta
        fun counterMetric(name: String, reason: String): PerformanceTierMetric =
            measured[name]?.let { PerformanceTierMetric(it.value, MetricSource.Derived, reason) }
                ?: PerformanceTierMetric(null, MetricSource.Unavailable, "performance.metric.$name-unavailable")
        fun sumMetric(names: List<String>, reason: String): PerformanceTierMetric {
            val values = names.map { measured[it]?.value }
            return if (values.any { it == null }) PerformanceTierMetric(null, MetricSource.Unavailable, "performance.metric.resource-counters-unavailable")
            else PerformanceTierMetric(values.sumOf { it!! }, MetricSource.Derived, reason)
        }
        val metrics = linkedMapOf(
            "frameP50Nanos" to timingMetric(run.timings?.p50Nanos),
            "frameP95Nanos" to timingMetric(run.timings?.p95Nanos),
            "allocations" to sumMetric(listOf("buffersCreated", "texturesCreated", "bindGroupsCreated", "samplersCreated", "uniformSlabsCreated"), "performance.metric.allocations-derived-from-resource-counters"),
            "pipelineBuilds" to counterMetric("cache.execution", "performance.metric.pipeline-builds-derived-from-execution-plans"),
            "uploadBytes" to uploadMetric(measured["uniformSlabBytesAllocated"]),
            "readbackBytes" to PerformanceTierMetric(null, MetricSource.Unavailable, "performance.metric.readback-bytes-unavailable"),
            "intermediateBytes" to PerformanceTierMetric(null, MetricSource.Unavailable, "performance.metric.intermediate-bytes-unavailable"),
        )
        val diagnostics = buildList {
            if (run.verdict !is PerformanceVerdict.EligibleMeasurement) add("performance.correctness-or-eligibility-not-closed")
            if (metrics.getValue("frameP50Nanos").value?.let { it > budget.p50FrameNanos } == true) add("performance.budget.p50-exceeded")
            if (metrics.getValue("frameP95Nanos").value?.let { it > budget.p95FrameNanos } == true) add("performance.budget.p95-exceeded")
            if (metrics.getValue("allocations").value?.let { it > budget.maxAllocations } == true) add("performance.budget.allocations-exceeded")
            if (metrics.getValue("pipelineBuilds").value?.let { it > budget.maxPipelineBuilds } == true) add("performance.budget.pipeline-builds-exceeded")
            if (metrics.getValue("uploadBytes").value?.let { it > budget.maxUploadBytes } == true) add("performance.budget.upload-bytes-exceeded")
            if (metrics.getValue("readbackBytes").value?.let { it > budget.maxReadbackBytes } == true) add("performance.budget.readback-bytes-exceeded")
            add("performance.gate.reporting-only")
        }
        return PerformanceTierEvaluation(budget.family, budget.tier, "reporting-only", metrics, diagnostics, false)
    }
    private fun timingMetric(value: Long?) = if (value == null) PerformanceTierMetric(null, MetricSource.Unavailable, "performance.metric.frame-time-unavailable") else PerformanceTierMetric(value, MetricSource.Observed)
    private fun uploadMetric(metric: PerformanceMetric?) = if (metric == null) PerformanceTierMetric(null, MetricSource.Unavailable, "performance.metric.upload-bytes-unavailable") else PerformanceTierMetric(metric.value, metric.source, metric.reason)
}
