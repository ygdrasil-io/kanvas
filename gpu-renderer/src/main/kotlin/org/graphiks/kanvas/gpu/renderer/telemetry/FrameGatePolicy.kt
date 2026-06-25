package org.graphiks.kanvas.gpu.renderer.telemetry

import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Frame gate decision for one draw family.
 *
 * The status is an observation against the Apple M-series performance budget.
 * It never flips a product route ON; quarantine is a policy flag that marks a
 * family as regression-blocked for opt-in evidence, not a runtime activation.
 */
enum class FrameGateStatus(val wireName: String) {
    /** Family met the 60fps target budget. */
    Pass("pass"),
    /** Family is below the 60fps target but above the 30fps warning threshold. */
    Warn("warn"),
    /** Family is below the 30fps warning threshold and is quarantined. */
    Quarantine("quarantine"),
}

/** Frame gate evaluation result for one draw family. */
data class FrameGateResult(
    val family: String,
    val frameTimeMs: Double,
    val fps: Double,
    val status: FrameGateStatus,
) {
    /** True when the family fell below the warning threshold and is regression-quarantined. */
    val quarantined: Boolean get() = status == FrameGateStatus.Quarantine

    /** Single-line diagnostic for PM evidence and tests. */
    fun dumpLine(): String =
        "frame-gate family=$family frameTimeMs=${frameTimeMs.fmt()} fps=${fps.fmt()} status=${status.wireName}"

    /** JSON object for the frame gate policy report. */
    fun toJson(): String =
        "{\"family\": ${family.jsonString()}, \"frameTimeMs\": ${frameTimeMs.fmt()}, " +
            "\"fps\": ${fps.fmt()}, \"status\": ${status.wireName.jsonString()}, " +
            "\"quarantined\": $quarantined}"
}

/**
 * Frame gate policy report for the wired draw families.
 *
 * `productActivation` stays false: this report measures and gates, it does not
 * activate or flip any renderer route.
 */
data class FrameGatePolicyReport(
    val hardwareBaseline: String,
    val targetFps: Int,
    val warnFps: Int,
    val targetFrameMs: Double,
    val warnFrameMs: Double,
    val results: List<FrameGateResult>,
    val productActivation: Boolean = false,
) {
    /** True when any evaluated family is quarantined. */
    val anyQuarantined: Boolean get() = results.any { it.quarantined }

    /** Canonical dump lines for PM evidence and tests. */
    fun dumpLines(): List<String> =
        listOf(
            "frame-gate-policy baseline=$hardwareBaseline targetFps=$targetFps warnFps=$warnFps " +
                "targetFrameMs=${targetFrameMs.fmt()} warnFrameMs=${warnFrameMs.fmt()} " +
                "anyQuarantined=$anyQuarantined",
        ) + results.map { it.dumpLine() } + listOf(
            "nonclaim:frame-gate-policy no-product-activation no-cross-platform-claim apple-m-series-baseline-only",
        )

    /** JSON report written to `frame-gate-policy.json`. */
    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"hardwareBaseline\": ${hardwareBaseline.jsonString()},")
        appendLine("  \"targetFps\": $targetFps,")
        appendLine("  \"warnFps\": $warnFps,")
        appendLine("  \"targetFrameMs\": ${targetFrameMs.fmt()},")
        appendLine("  \"warnFrameMs\": ${warnFrameMs.fmt()},")
        appendLine("  \"anyQuarantined\": $anyQuarantined,")
        appendLine("  \"productActivation\": $productActivation,")
        appendLine("  \"families\": [")
        appendLine(results.joinToString(",\n") { "    ${it.toJson()}" })
        appendLine("  ]")
        appendLine("}")
    }

    /** Writes `frame-gate-policy.json` and a diagnostics transcript to [outputDir]. */
    fun writeTo(outputDir: Path) {
        outputDir.createDirectories()
        outputDir.resolve("frame-gate-policy.json").writeText(toJson())
        outputDir.resolve("frame-gate-policy-diagnostics.txt")
            .writeText(dumpLines().joinToString(separator = "\n", postfix = "\n"))
    }
}

/**
 * Frame gate policy with a 60fps target and a 30fps warning threshold.
 *
 * Measurements are anchored to the Apple M-series baseline. A family below the
 * warning threshold is quarantined (regression-blocked) but no product route is
 * activated, flipped, or disabled at runtime by this policy.
 */
class FrameGatePolicy(
    val targetFps: Int = 60,
    val warnFps: Int = 30,
    val hardwareBaseline: String = "Apple M-series",
) {
    init {
        require(targetFps > 0) { "frame gate targetFps must be positive" }
        require(warnFps > 0) { "frame gate warnFps must be positive" }
        require(targetFps > warnFps) { "frame gate targetFps must exceed warnFps" }
        require(hardwareBaseline.isNotBlank()) { "frame gate hardwareBaseline must not be blank" }
    }

    /** Frame budget in milliseconds for the 60fps target. */
    val targetFrameMs: Double get() = 1000.0 / targetFps

    /** Frame budget in milliseconds for the 30fps warning threshold. */
    val warnFrameMs: Double get() = 1000.0 / warnFps

    /** Evaluates one family's measured frame time against the budget. */
    fun evaluate(family: String, frameTimeMs: Double): FrameGateResult {
        require(family.isNotBlank()) { "frame gate family must not be blank" }
        require(frameTimeMs > 0.0) { "frame gate frameTimeMs must be positive: $frameTimeMs" }
        val status = when {
            frameTimeMs <= targetFrameMs -> FrameGateStatus.Pass
            frameTimeMs <= warnFrameMs -> FrameGateStatus.Warn
            else -> FrameGateStatus.Quarantine
        }
        return FrameGateResult(
            family = family,
            frameTimeMs = frameTimeMs,
            fps = 1000.0 / frameTimeMs,
            status = status,
        )
    }

    /** Evaluates every family measurement into a policy report. */
    fun evaluateAll(measurements: List<Pair<String, Double>>): FrameGatePolicyReport =
        FrameGatePolicyReport(
            hardwareBaseline = hardwareBaseline,
            targetFps = targetFps,
            warnFps = warnFps,
            targetFrameMs = targetFrameMs,
            warnFrameMs = warnFrameMs,
            results = measurements.map { (family, frameTimeMs) -> evaluate(family, frameTimeMs) },
        )
}

private fun Double.fmt(): String = String.format(Locale.US, "%.4f", this)

private fun String.jsonString(): String = buildString {
    append('"')
    this@jsonString.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
    append('"')
}
