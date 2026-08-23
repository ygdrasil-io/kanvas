package org.graphiks.kanvas.gpu.evidence.performance

import kotlin.math.ceil
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceAdapter
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry

const val GPU_EVIDENCE_PERFORMANCE_SCHEMA = "gpu-evidence-performance-v1"

data class PerformanceConfig(
    val warmupFrames: Int = 10,
    val measuredFrames: Int = 90,
    val gateVersion: Int = 1,
) {
    init {
        require(warmupFrames >= 0) { "warmupFrames must be non-negative" }
        require(measuredFrames > 0) { "measuredFrames must be positive" }
        require(gateVersion > 0) { "gateVersion must be positive" }
    }
}

enum class MetricSource { Observed, Derived, Unavailable }

data class FrameTimingSummary(
    val sampleCount: Int,
    val p50Nanos: Long,
    val p95Nanos: Long,
    val source: MetricSource,
) {
    init {
        require(sampleCount > 0) { "sampleCount must be positive" }
        require(p50Nanos >= 0L && p95Nanos >= p50Nanos) { "timing percentiles must be ordered and non-negative" }
    }

    companion object {
        fun fromSamples(samples: List<Long>, source: MetricSource = MetricSource.Observed): FrameTimingSummary {
            require(samples.isNotEmpty()) { "timing samples must not be empty" }
            require(samples.all { it >= 0L }) { "timing samples must be non-negative" }
            val sorted = samples.sorted()
            fun nearestRank(percent: Int): Long = sorted[ceil(sorted.size * percent / 100.0).toInt() - 1]
            return FrameTimingSummary(sorted.size, nearestRank(50), nearestRank(95), source)
        }
    }
}

sealed interface PerformanceVerdict {
    val reason: String
    data class EligibleMeasurement(override val reason: String) : PerformanceVerdict
    data class DiagnosticOnly(override val reason: String) : PerformanceVerdict
    data class Unavailable(override val reason: String) : PerformanceVerdict
    data class Failed(override val reason: String) : PerformanceVerdict
}

data class PerformanceMetric(
    val value: Long?,
    val source: MetricSource,
    val reason: String? = null,
) {
    init {
        require(source == MetricSource.Unavailable || value != null) { "available metric requires a value" }
        require(source == MetricSource.Unavailable || value!! >= 0L) { "metric value must be non-negative" }
        require(source != MetricSource.Unavailable || !reason.isNullOrBlank()) { "unavailable metric requires a reason" }
    }
}

data class PerformanceTelemetry(
    val before: Map<String, PerformanceMetric>,
    val after: Map<String, PerformanceMetric>,
    val delta: Map<String, PerformanceMetric>,
)

data class PerformanceEnvironment(
    val sourceCommit: String,
    val osName: String,
    val osVersion: String,
    val osArchitecture: String,
    val javaVersion: String,
    val adapter: EvidenceAdapter?,
    val deviceGeneration: Long?,
)

data class PerformanceRun(
    val sourceCommit: String,
    val sceneId: String,
    val config: PerformanceConfig,
    val verdict: PerformanceVerdict,
    val environment: PerformanceEnvironment,
    val eligibility: PerformanceVerdict = verdict,
    val coldReadbackNanos: Long? = null,
    val timings: FrameTimingSummary? = null,
    val timingSamplesNanos: List<Long> = emptyList(),
    val telemetry: PerformanceTelemetry = PerformanceTelemetry(emptyMap(), emptyMap(), emptyMap()),
    val diagnostics: List<String> = emptyList(),
) {
    init {
        require(sourceCommit.matches(Regex("[0-9a-f]{40}"))) { "sourceCommit must be lowercase 40-hex" }
        require(sceneId.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) { "sceneId must use lower-kebab-case" }
        require(timingSamplesNanos.all { it >= 0L }) { "timing samples must be non-negative" }
        require(coldReadbackNanos == null || coldReadbackNanos >= 0L) { "cold readback timing must be non-negative" }
    }

    companion object {
        fun fixture(sourceCommit: String = "a".repeat(40)): PerformanceRun = PerformanceRun(
            sourceCommit = sourceCommit,
            sceneId = "solid-card-stack",
            config = PerformanceConfig(),
            verdict = PerformanceVerdict.EligibleMeasurement("hardware adapter"),
            environment = PerformanceEnvironment(sourceCommit, "test", "1", "test", "test", null, 1L),
            coldReadbackNanos = 10L,
            timings = FrameTimingSummary.fromSamples(List(90) { 100L + it }),
            timingSamplesNanos = List(90) { 100L + it },
        )
    }
}

internal fun GPUBackendRuntimeTelemetry.toPerformanceCounters(): Map<String, Long> = linkedMapOf(
    "submissions" to submissions,
    "commandBuffers" to commandBuffers,
    "renderPasses" to renderPasses,
    "buffersCreated" to buffersCreated,
    "texturesCreated" to texturesCreated,
    "queueWrites" to queueWrites,
    "uniformSlabsCreated" to uniformSlabsCreated,
    "bindGroupsCreated" to bindGroupsCreated,
    "cache.execution" to passBatchPlans,
)
