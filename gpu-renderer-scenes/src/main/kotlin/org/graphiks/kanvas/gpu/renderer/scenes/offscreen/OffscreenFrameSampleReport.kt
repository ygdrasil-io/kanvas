package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.graphiks.kanvas.gpu.renderer.scenes.reports.json

enum class OffscreenFrameSampleStatus(val wireName: String) {
    NotYetRendered("not-yet-rendered"),
    RenderFailed("render-failed"),
    Sampled("sampled"),
}

data class OffscreenFrameSampleReport(
    val sceneId: String,
    val runStatus: OffscreenFrameSampleStatus,
    val backend: String,
    val metricName: String?,
    val metricSource: String?,
    val adapterInfo: String?,
    val rawSampleCount: Int,
    val warmupFrames: Int,
    val stableFrames: Int,
    val samples: List<OffscreenFrameSample>,
    val diagnostics: List<String>,
) {
    val status: String get() = runStatus.wireName
    val productRefusal: Boolean get() = false

    init {
        require(sceneId.isNotBlank()) { "sceneId must not be blank" }
        require(backend.isNotBlank()) { "backend must not be blank" }
        require(rawSampleCount >= 0) { "rawSampleCount must not be negative" }
        require(warmupFrames >= 0) { "warmupFrames must not be negative" }
        require(stableFrames >= 0) { "stableFrames must not be negative" }
        require(rawSampleCount == warmupFrames + stableFrames) {
            "rawSampleCount must equal warmupFrames + stableFrames"
        }
        require(samples.size == rawSampleCount) { "samples size must equal rawSampleCount" }
        require(diagnostics.isNotEmpty()) { "diagnostics must not be empty" }
        require(diagnostics.all { it.isNotBlank() }) { "diagnostics must not contain blank entries" }
        requireStatusInvariants()
    }

    private fun requireStatusInvariants() {
        when (runStatus) {
            OffscreenFrameSampleStatus.Sampled -> {
                require(metricName == "frame-time-ms") { "sampled reports must use frame-time-ms metricName" }
                require(metricSource == "wall-clock-offscreen-render-readback") {
                    "sampled reports must use wall-clock-offscreen-render-readback metricSource"
                }
                require(!adapterInfo.isNullOrBlank()) { "sampled reports must include adapterInfo" }
                require(rawSampleCount > 0) { "sampled reports must include samples" }
                require(stableFrames > 0) { "sampled reports must include stable samples" }
                require(samples.map { sample -> sample.frameIndex } == (1..samples.size).toList()) {
                    "sample frameIndex values must be one-based and contiguous"
                }
                samples.forEachIndexed { index, sample ->
                    val expectedPhase = if (index < warmupFrames) "warmup" else "stable"
                    require(sample.phase == expectedPhase) { "sample phase must match warmup split" }
                }
            }
            OffscreenFrameSampleStatus.NotYetRendered,
            OffscreenFrameSampleStatus.RenderFailed -> {
                require(metricName == null) { "${runStatus.wireName} reports must not include metricName" }
                require(metricSource == null) { "${runStatus.wireName} reports must not include metricSource" }
                require(rawSampleCount == 0) { "${runStatus.wireName} reports must not include samples" }
                require(warmupFrames == 0) { "${runStatus.wireName} reports must not include warmup samples" }
                require(stableFrames == 0) { "${runStatus.wireName} reports must not include stable samples" }
                require(samples.isEmpty()) { "${runStatus.wireName} reports must not include sample entries" }
            }
        }
    }

    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"sceneId\": ${sceneId.json()},")
        appendLine("  \"status\": ${status.json()},")
        appendLine("  \"productRefusal\": $productRefusal,")
        appendLine("  \"backend\": ${backend.json()},")
        appendLine("  \"metricName\": ${metricName?.json() ?: "null"},")
        appendLine("  \"metricSource\": ${metricSource?.json() ?: "null"},")
        appendLine("  \"adapterInfo\": ${adapterInfo?.json() ?: "null"},")
        appendLine("  \"rawSampleCount\": $rawSampleCount,")
        appendLine("  \"warmupFrames\": $warmupFrames,")
        appendLine("  \"stableFrames\": $stableFrames,")
        appendLine("  \"samples\": [")
        appendLine(samples.joinToString(",\n") { sample -> "    ${sample.toJson()}" })
        appendLine("  ],")
        appendLine("  \"diagnostics\": [${diagnostics.joinToString(",") { it.json() }}]")
        appendLine("}")
    }

    fun diagnosticsText(): String =
        diagnostics.joinToString(separator = "\n", postfix = "\n")

    fun writeTo(outputDir: Path) {
        outputDir.createDirectories()
        outputDir.resolve("frame-samples.json").writeText(toJson())
        outputDir.resolve("frame-samples-diagnostics.txt").writeText(diagnosticsText())
    }

    companion object {
        fun sampled(
            sceneId: String,
            adapterInfo: String,
            warmupFrames: Int,
            samples: List<Long>,
            diagnostics: List<String>,
        ): OffscreenFrameSampleReport {
            require(samples.all { sample -> sample > 0L }) {
                "offscreen frame samples must be positive"
            }
            require(warmupFrames >= 0) { "warmupFrames must not be negative" }
            require(warmupFrames < samples.size) { "warmupFrames must leave at least one stable sample" }
            return OffscreenFrameSampleReport(
                sceneId = sceneId,
                runStatus = OffscreenFrameSampleStatus.Sampled,
                backend = "webgpu-offscreen",
                metricName = "frame-time-ms",
                metricSource = "wall-clock-offscreen-render-readback",
                adapterInfo = adapterInfo,
                rawSampleCount = samples.size,
                warmupFrames = warmupFrames,
                stableFrames = samples.size - warmupFrames,
                samples = samples.mapIndexed { index, durationNanos ->
                    OffscreenFrameSample(
                        frameIndex = index + 1,
                        phase = if (index < warmupFrames) "warmup" else "stable",
                        durationNanos = durationNanos,
                    )
                },
                diagnostics = diagnostics,
            )
        }

        fun notYetRendered(sceneId: String, reason: String): OffscreenFrameSampleReport =
            empty(
                sceneId = sceneId,
                runStatus = OffscreenFrameSampleStatus.NotYetRendered,
                diagnostics = listOf(reason),
            )

        fun failed(sceneId: String, reason: String): OffscreenFrameSampleReport =
            empty(
                sceneId = sceneId,
                runStatus = OffscreenFrameSampleStatus.RenderFailed,
                diagnostics = listOf(reason),
            )

        private fun empty(
            sceneId: String,
            runStatus: OffscreenFrameSampleStatus,
            diagnostics: List<String>,
        ): OffscreenFrameSampleReport =
            OffscreenFrameSampleReport(
                sceneId = sceneId,
                runStatus = runStatus,
                backend = "webgpu-offscreen",
                metricName = null,
                metricSource = null,
                adapterInfo = null,
                rawSampleCount = 0,
                warmupFrames = 0,
                stableFrames = 0,
                samples = emptyList(),
                diagnostics = diagnostics,
            )
    }
}

data class OffscreenFrameSample(
    val frameIndex: Int,
    val phase: String,
    val durationNanos: Long,
) {
    init {
        require(frameIndex > 0) { "frameIndex must be positive" }
        require(phase == "warmup" || phase == "stable") { "phase must be warmup or stable" }
        require(durationNanos > 0L) { "durationNanos must be positive" }
    }

    val durationMs: Double get() = durationNanos / 1_000_000.0

    fun toJson(): String =
        "{\"frameIndex\": $frameIndex, \"phase\": ${phase.json()}, \"durationNanos\": $durationNanos, " +
            "\"durationMs\": ${durationMs.formatOffscreenFrameMs()}}"
}

private fun Double.formatOffscreenFrameMs(): String =
    String.format(Locale.US, "%.4f", this)
