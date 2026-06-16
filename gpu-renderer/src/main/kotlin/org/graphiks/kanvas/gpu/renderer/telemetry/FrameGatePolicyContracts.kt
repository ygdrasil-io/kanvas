package org.graphiks.kanvas.gpu.renderer.telemetry

import java.util.Locale
import kotlin.math.sqrt

/** Target lifecycle state for a frame timing gate lane. */
enum class GPUFrameGateState(val label: String) {
    /** Candidate evidence can be reported but cannot block a release. */
    Candidate("candidate"),
    /** Release-blocking is allowed only after complete owned evidence passes the policy. */
    ReleaseBlocking("release-blocking"),
}

/** Final classification for one frame timing evidence lane. */
enum class GPUFrameGateLaneClassification(val label: String) {
    /** Complete candidate evidence that is not allowed to block the release. */
    Candidate("candidate"),
    /** Complete accepted release-blocking evidence. */
    ReleaseBlocking("release-blocking"),
    /** Explicit reporting-only evidence, useful for PM visibility but not readiness. */
    ReportingOnly("reporting-only"),
    /** Complete enough to diagnose, but quarantined by policy or environment. */
    Quarantined("quarantined"),
    /** Missing or skipped measurement lane. */
    Skipped("skipped"),
    /** Negative fixture or live lane exceeded the threshold. */
    ThresholdFailed("threshold-failed"),
    /** Samples exceeded the configured variance limit. */
    VarianceExceeded("variance-exceeded"),
}

/** Threshold evaluation result for one lane. */
enum class GPUFrameGateThresholdStatus(val label: String) {
    /** Stable samples were within threshold and variance limits. */
    WithinThreshold("within-threshold"),
    /** Threshold evaluation was intentionally not run. */
    NotEvaluated("not-evaluated"),
    /** Mean frame time exceeded the configured threshold. */
    ThresholdFailed("threshold-failed"),
    /** Sample variance exceeded the configured limit. */
    VarianceExceeded("variance-exceeded"),
}

/** Warmup, metric, threshold, quarantine, and rebaseline facts for a frame gate. */
data class GPUFrameGateWarmupPolicy(
    val warmupFrameCount: Int,
    val stableFrameCount: Int,
    val metricName: String,
    val metricSource: String,
    val thresholdMs: Double,
    val maxCoefficientOfVariation: Double,
    val quarantineRule: String,
    val rebaselineRule: String,
) {
    init {
        require(warmupFrameCount >= 0) { "GPU frame gate warmupFrameCount must not be negative" }
        require(stableFrameCount > 0) { "GPU frame gate stableFrameCount must be positive" }
        require(metricName.isNotBlank()) { "GPU frame gate metricName must not be blank" }
        require(metricSource.isNotBlank()) { "GPU frame gate metricSource must not be blank" }
        require(thresholdMs.isFinitePositive()) { "GPU frame gate thresholdMs must be finite and positive" }
        require(maxCoefficientOfVariation.isFiniteNonNegative()) {
            "GPU frame gate maxCoefficientOfVariation must be finite and non-negative"
        }
        require(quarantineRule.isNotBlank()) { "GPU frame gate quarantineRule must not be blank" }
        require(rebaselineRule.isNotBlank()) { "GPU frame gate rebaselineRule must not be blank" }
    }
}

/** Raw sample source facts for one frame gate lane. */
data class GPUFrameSampleProvenance(
    val sourceArtifactLabel: String,
    val sourceKind: String,
    val sourceHash: String?,
    val sceneId: String,
    val adapterLabel: String?,
    val rawSampleCount: Int,
    val warmupFrameCount: Int,
    val stableFrameCount: Int,
) {
    init {
        require(sourceArtifactLabel.isNotBlank()) { "GPU frame sample sourceArtifactLabel must not be blank" }
        require(sourceKind.isNotBlank()) { "GPU frame sample sourceKind must not be blank" }
        require(sceneId.isNotBlank()) { "GPU frame sample sceneId must not be blank" }
        require(rawSampleCount >= 0) { "GPU frame sample rawSampleCount must not be negative" }
        require(warmupFrameCount >= 0) { "GPU frame sample warmupFrameCount must not be negative" }
        require(stableFrameCount >= 0) { "GPU frame sample stableFrameCount must not be negative" }
        require(rawSampleCount == warmupFrameCount + stableFrameCount) {
            "GPU frame sample rawSampleCount must equal warmupFrameCount + stableFrameCount"
        }
    }
}

/** Requested lane evaluation input for frame gate policy. */
data class GPUFrameGateLaneRequest(
    val laneId: String,
    val targetState: GPUFrameGateState,
    val provenance: GPUFrameSampleProvenance,
    val stableFrameMs: List<Double>,
    val reportingOnly: Boolean = false,
    val quarantineReasons: List<String> = emptyList(),
    val skipReasons: List<String> = emptyList(),
) {
    init {
        require(laneId.isNotBlank()) { "GPU frame gate laneId must not be blank" }
        require(stableFrameMs.size == provenance.stableFrameCount) {
            "GPU frame gate stableFrameMs size must match provenance stableFrameCount"
        }
        require(stableFrameMs.all { sample -> sample.isFinitePositive() }) {
            "GPU frame gate stableFrameMs samples must be finite and positive"
        }
        require(quarantineReasons.all { reason -> reason.isNotBlank() }) {
            "GPU frame gate quarantine reasons must not be blank"
        }
        require(skipReasons.all { reason -> reason.isNotBlank() }) {
            "GPU frame gate skip reasons must not be blank"
        }
    }
}

/** Evaluated lane result with deterministic PM dump formatting. */
data class GPUFrameGateLaneEvaluation(
    val laneId: String,
    val state: GPUFrameGateState,
    val classification: GPUFrameGateLaneClassification,
    val provenance: GPUFrameSampleProvenance,
    val thresholdStatus: GPUFrameGateThresholdStatus,
    val meanFrameMs: Double?,
    val coefficientOfVariation: Double?,
    val countsForReleaseGate: Boolean,
    val quarantineReasons: List<String>,
    val skipReasons: List<String>,
) {
    /** Returns one canonical lane dump line. */
    fun dumpLine(): String =
        "frame-gate-lane id=$laneId state=${state.label} classification=${classification.label} " +
            "source=${provenance.sourceArtifactLabel} kind=${provenance.sourceKind.lowercase()} " +
            "hash=${provenance.sourceHash ?: "none"} scene=${provenance.sceneId} " +
            "adapter=${provenance.adapterLabel ?: "none"} rawSamples=${provenance.rawSampleCount} " +
            "warmup=${provenance.warmupFrameCount} stable=${provenance.stableFrameCount} " +
            "meanMs=${meanFrameMs?.formatFrameNumber() ?: "none"} " +
            "cov=${coefficientOfVariation?.formatFrameNumber() ?: "none"} " +
            "thresholdStatus=${thresholdStatus.label} countsRelease=$countsForReleaseGate " +
            "skip=${skipReasons.stableReasonList()} quarantine=${quarantineReasons.stableReasonList()}"
}

/** Frame gate policy report with explicit non-promotional defaults. */
data class GPUFrameGatePolicyReport(
    val gateId: String,
    val warmupPolicy: GPUFrameGateWarmupPolicy,
    val lanes: List<GPUFrameGateLaneEvaluation>,
    val readinessDelta: Double = 0.0,
    val productRouteActivated: Boolean = false,
) {
    init {
        require(gateId.isNotBlank()) { "GPU frame gate policy gateId must not be blank" }
        require(lanes.isNotEmpty()) { "GPU frame gate policy must include at least one lane" }
        require(readinessDelta == 0.0) { "GPU frame gate policy readinessDelta must remain zero" }
        require(!productRouteActivated) { "GPU frame gate policy must not activate product routes" }
    }

    /** True only when at least one complete accepted lane is explicitly release-blocking. */
    val releaseBlocking: Boolean = lanes.any { lane -> lane.countsForReleaseGate }

    /** Lane ids that currently count as accepted release-blocking evidence. */
    fun releaseBlockingLaneIds(): List<String> =
        lanes
            .filter { lane -> lane.countsForReleaseGate }
            .map { lane -> lane.laneId }

    /** Returns canonical report lines for tests and PM evidence. */
    fun dumpLines(): List<String> {
        val counts = lanes.groupingBy { lane -> lane.classification }.eachCount()

        return listOf(
            "frame-gate-policy id=$gateId lanes=${lanes.size} " +
                "warmupFrames=${warmupPolicy.warmupFrameCount} stableFrames=${warmupPolicy.stableFrameCount} " +
                "metric=${warmupPolicy.metricName} source=${warmupPolicy.metricSource} " +
                "thresholdMs=${warmupPolicy.thresholdMs.formatFrameNumber()} " +
                "maxCov=${warmupPolicy.maxCoefficientOfVariation.formatFrameNumber()} " +
                "quarantineRule=${warmupPolicy.quarantineRule} rebaselineRule=${warmupPolicy.rebaselineRule} " +
                "releaseBlocking=$releaseBlocking productRouteActivated=$productRouteActivated " +
                "readinessDelta=$readinessDelta",
        ) + lanes.map { lane -> lane.dumpLine() } + listOf(
            "pm:gpu-renderer.frame-gate-policy classification=PolicyGated " +
                "candidate=${counts[GPUFrameGateLaneClassification.Candidate] ?: 0} " +
                "releaseBlocking=${counts[GPUFrameGateLaneClassification.ReleaseBlocking] ?: 0} " +
                "reportingOnly=${counts[GPUFrameGateLaneClassification.ReportingOnly] ?: 0} " +
                "quarantined=${counts[GPUFrameGateLaneClassification.Quarantined] ?: 0} " +
                "skipped=${counts[GPUFrameGateLaneClassification.Skipped] ?: 0} " +
                "thresholdFailed=${counts[GPUFrameGateLaneClassification.ThresholdFailed] ?: 0} " +
                "varianceExceeded=${counts[GPUFrameGateLaneClassification.VarianceExceeded] ?: 0} " +
                "readinessDelta=$readinessDelta releaseBlocking=$releaseBlocking",
            "nonclaim:no-release-blocking-gate no-readiness-delta no-product-activation " +
                "no-correctness-claim no-derived-timings",
        )
    }
}

/** Evaluates frame timing evidence lanes against an explicit non-promotional policy. */
object GPUFrameGatePolicyEvaluator {
    /** Builds a deterministic policy report for frame gate lanes. */
    fun evaluate(
        gateId: String,
        warmupPolicy: GPUFrameGateWarmupPolicy,
        lanes: List<GPUFrameGateLaneRequest>,
    ): GPUFrameGatePolicyReport =
        GPUFrameGatePolicyReport(
            gateId = gateId,
            warmupPolicy = warmupPolicy,
            lanes = lanes.map { lane -> evaluateLane(warmupPolicy, lane) },
        )

    private fun evaluateLane(
        warmupPolicy: GPUFrameGateWarmupPolicy,
        lane: GPUFrameGateLaneRequest,
    ): GPUFrameGateLaneEvaluation {
        val normalizedSourceKind = lane.provenance.sourceKind.lowercase()
        val skipReasons = lane.explicitAndImplicitSkipReasons(normalizedSourceKind, warmupPolicy)
        val thresholdFacts = if (
            lane.reportingOnly ||
            normalizedSourceKind == "reporting-only" ||
            skipReasons.isNotEmpty()
        ) {
            ThresholdFacts(
                status = GPUFrameGateThresholdStatus.NotEvaluated,
                meanFrameMs = null,
                coefficientOfVariation = null,
            )
        } else {
            val evaluated = lane.stableFrameMs.thresholdFacts(warmupPolicy)
            if (lane.quarantineReasons.isNotEmpty()) {
                evaluated.copy(status = GPUFrameGateThresholdStatus.NotEvaluated)
            } else {
                evaluated
            }
        }
        val classification = classifyLane(lane, normalizedSourceKind, skipReasons, thresholdFacts.status)

        return GPUFrameGateLaneEvaluation(
            laneId = lane.laneId,
            state = lane.targetState,
            classification = classification,
            provenance = lane.provenance.copy(sourceKind = normalizedSourceKind),
            thresholdStatus = thresholdFacts.status,
            meanFrameMs = thresholdFacts.meanFrameMs,
            coefficientOfVariation = thresholdFacts.coefficientOfVariation,
            countsForReleaseGate = classification == GPUFrameGateLaneClassification.ReleaseBlocking,
            quarantineReasons = lane.quarantineReasons,
            skipReasons = skipReasons,
        )
    }

    private fun classifyLane(
        lane: GPUFrameGateLaneRequest,
        normalizedSourceKind: String,
        skipReasons: List<String>,
        thresholdStatus: GPUFrameGateThresholdStatus,
    ): GPUFrameGateLaneClassification =
        when {
            lane.reportingOnly || normalizedSourceKind == "reporting-only" ->
                GPUFrameGateLaneClassification.ReportingOnly
            skipReasons.isNotEmpty() -> GPUFrameGateLaneClassification.Skipped
            lane.quarantineReasons.isNotEmpty() -> GPUFrameGateLaneClassification.Quarantined
            thresholdStatus == GPUFrameGateThresholdStatus.ThresholdFailed ->
                GPUFrameGateLaneClassification.ThresholdFailed
            thresholdStatus == GPUFrameGateThresholdStatus.VarianceExceeded ->
                GPUFrameGateLaneClassification.VarianceExceeded
            lane.targetState == GPUFrameGateState.ReleaseBlocking ->
                GPUFrameGateLaneClassification.ReleaseBlocking
            else -> GPUFrameGateLaneClassification.Candidate
        }

    private fun GPUFrameGateLaneRequest.explicitAndImplicitSkipReasons(
        normalizedSourceKind: String,
        warmupPolicy: GPUFrameGateWarmupPolicy,
    ): List<String> {
        val implicit = mutableListOf<String>()
        if (normalizedSourceKind != "owned-adapter-frame-samples" && normalizedSourceKind != "reporting-only") {
            implicit += "unsupported-source-kind"
        }
        if (normalizedSourceKind == "owned-adapter-frame-samples" && provenance.sourceHash.isNullOrBlank()) {
            implicit += "missing-source-hash"
        }
        if (normalizedSourceKind == "owned-adapter-frame-samples" && provenance.adapterLabel.isNullOrBlank()) {
            implicit += "missing-adapter-label"
        }
        if (
            normalizedSourceKind == "owned-adapter-frame-samples" &&
            provenance.stableFrameCount != 0 &&
            provenance.stableFrameCount < warmupPolicy.stableFrameCount
        ) {
            implicit += "insufficient-stable-samples"
        }

        return (skipReasons + implicit).distinct()
    }

    private fun List<Double>.thresholdFacts(warmupPolicy: GPUFrameGateWarmupPolicy): ThresholdFacts {
        if (isEmpty()) {
            return ThresholdFacts(
                status = GPUFrameGateThresholdStatus.NotEvaluated,
                meanFrameMs = null,
                coefficientOfVariation = null,
            )
        }

        val mean = average()
        val variance = sumOf { sample -> (sample - mean) * (sample - mean) } / size
        val coefficientOfVariation = sqrt(variance) / mean
        val status = when {
            mean > warmupPolicy.thresholdMs -> GPUFrameGateThresholdStatus.ThresholdFailed
            coefficientOfVariation > warmupPolicy.maxCoefficientOfVariation ->
                GPUFrameGateThresholdStatus.VarianceExceeded
            else -> GPUFrameGateThresholdStatus.WithinThreshold
        }

        return ThresholdFacts(
            status = status,
            meanFrameMs = mean,
            coefficientOfVariation = coefficientOfVariation,
        )
    }
}

private data class ThresholdFacts(
    val status: GPUFrameGateThresholdStatus,
    val meanFrameMs: Double?,
    val coefficientOfVariation: Double?,
)

private fun Double.isFinitePositive(): Boolean =
    !isNaN() && !isInfinite() && this > 0.0

private fun Double.isFiniteNonNegative(): Boolean =
    !isNaN() && !isInfinite() && this >= 0.0

private fun Double.formatFrameNumber(): String =
    String.format(Locale.US, "%.4f", this)

private fun List<String>.stableReasonList(): String =
    if (isEmpty()) {
        "-"
    } else {
        sorted().joinToString(",")
    }
