package org.graphiks.kanvas.gpu.evidence.performance

import kotlin.math.ceil
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceAdapter
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry

const val GPU_EVIDENCE_PERFORMANCE_SCHEMA = "gpu-evidence-performance-v1"

data class PerformanceConfig(
    val warmupFrames: Int = 10,
    val measuredFrames: Int = 90,
    val gateVersion: Int = 1,
) {
    init {
        require(warmupFrames == 10) { "warmupFrames must be exactly 10" }
        require(measuredFrames == 90) { "measuredFrames must be exactly 90" }
        require(gateVersion == 1) { "gateVersion must be exactly 1" }
    }
}

enum class MetricSource { Observed, Derived, Unavailable }

internal val PERFORMANCE_COUNTER_KEYS = setOf(
    "submissions", "commandBuffers", "renderPasses", "buffersCreated", "texturesCreated",
    "intermediateTexturesCreated", "destinationReadbackSnapshots", "queueWrites",
    "uniformSlabsCreated", "uniformSlabBytesAllocated", "bindGroupsCreated", "samplersCreated", "cache.execution",
)

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

data class PerformanceTelemetrySnapshot(
    val before: Map<String, PerformanceMetric>,
    val after: Map<String, PerformanceMetric>,
    val delta: Map<String, PerformanceMetric>,
) {
    companion object { fun empty() = PerformanceTelemetrySnapshot(emptyMap(), emptyMap(), emptyMap()) }
}

data class PerformanceTelemetry(
    val cold: PerformanceTelemetrySnapshot,
    val warmup: PerformanceTelemetrySnapshot,
    val measured: PerformanceTelemetrySnapshot,
    val total: PerformanceTelemetrySnapshot? = null,
) {
    constructor(before: Map<String, PerformanceMetric>, after: Map<String, PerformanceMetric>, delta: Map<String, PerformanceMetric>) :
        this(PerformanceTelemetrySnapshot(before, after, delta), PerformanceTelemetrySnapshot(before, after, delta), PerformanceTelemetrySnapshot(before, after, delta))

    companion object {
        val Empty = PerformanceTelemetry(PerformanceTelemetrySnapshot(emptyMap(), emptyMap(), emptyMap()), PerformanceTelemetrySnapshot(emptyMap(), emptyMap(), emptyMap()), PerformanceTelemetrySnapshot(emptyMap(), emptyMap(), emptyMap()))
    }
}

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
    val telemetry: PerformanceTelemetry = PerformanceTelemetry.Empty,
    val diagnostics: List<String> = emptyList(),
) {
    init {
        require(sourceCommit.matches(Regex("[0-9a-f]{40}"))) { "sourceCommit must be lowercase 40-hex" }
        require(GpuEvidenceCatalog.cases.any { it.descriptor.id.value == sceneId && it.descriptor.expectation is EvidenceExpectation.ShouldRender }) {
            "sceneId must identify a catalogued ShouldRender case"
        }
        require(timingSamplesNanos.all { it >= 0L }) { "timing samples must be non-negative" }
        require(coldReadbackNanos == null || coldReadbackNanos >= 0L) { "cold readback timing must be non-negative" }
    }

    companion object {
        fun fixture(sourceCommit: String = "a".repeat(40)): PerformanceRun = PerformanceRun(
            sourceCommit = sourceCommit,
            sceneId = "solid-card-stack",
            config = PerformanceConfig(),
            verdict = PerformanceVerdict.EligibleMeasurement("hardware adapter"),
            environment = PerformanceEnvironment(sourceCommit, "test", "1", "test", "test", EvidenceAdapter("Apple GPU", "Apple", "M2 Max", "Apple", "fixture", false), 1L),
            coldReadbackNanos = 10L,
            timings = FrameTimingSummary.fromSamples(List(90) { 100L + it }),
            timingSamplesNanos = List(90) { 100L + it },
            telemetry = PerformanceTelemetry(telemetryFixture(1L), telemetryFixture(10L), telemetryFixture(90L), telemetryFixture(101L)),
        )
    }
}

private fun telemetryFixture(delta: Long): PerformanceTelemetrySnapshot {
    val before = PERFORMANCE_COUNTER_KEYS.associateWith { PerformanceMetric(0L, MetricSource.Observed) }
    val after = PERFORMANCE_COUNTER_KEYS.associateWith { PerformanceMetric(delta, MetricSource.Observed) }
    val changes = PERFORMANCE_COUNTER_KEYS.associateWith { PerformanceMetric(delta, MetricSource.Derived) }
    return PerformanceTelemetrySnapshot(before, after, changes)
}

internal fun GPUBackendRuntimeTelemetry.toPerformanceCounters(): Map<String, Long> = linkedMapOf(
    "submissions" to submissions,
    "commandBuffers" to commandBuffers,
    "renderPasses" to renderPasses,
    "buffersCreated" to buffersCreated,
    "texturesCreated" to texturesCreated,
    "intermediateTexturesCreated" to intermediateTexturesCreated,
    "destinationReadbackSnapshots" to destinationReadbackSnapshots,
    "queueWrites" to queueWrites,
    "uniformSlabsCreated" to uniformSlabsCreated,
    "uniformSlabBytesAllocated" to uniformSlabBytesAllocated,
    "bindGroupsCreated" to bindGroupsCreated,
    "samplersCreated" to samplersCreated,
    "cache.execution" to passBatchPlans,
)
