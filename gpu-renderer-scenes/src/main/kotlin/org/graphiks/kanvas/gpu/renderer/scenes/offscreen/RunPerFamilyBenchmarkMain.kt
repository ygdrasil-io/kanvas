package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Path
import org.graphiks.kanvas.gpu.renderer.telemetry.FrameGatePolicy

fun main(args: Array<String>) = runPerFamilyBenchmark(args)

/**
 * KGPU-M27-001/002/003: runs the per-family benchmark and writes the three M27
 * performance reports to the output directory:
 *
 * - `per-family-benchmark.json` (FPS/frame-time per draw family),
 * - `pipeline-cache-telemetry.json` (per-scene hit rate, eviction, module count),
 * - `frame-gate-policy.json` (60fps target / 30fps warning / quarantine).
 *
 * The benchmark requires a GPU adapter; without one every family is skipped with a
 * stable diagnostic and no performance claim is promoted.
 */
fun runPerFamilyBenchmark(args: Array<String>) {
    require(args.isNotEmpty() && args.size <= 3) {
        "Usage: RunPerFamilyBenchmarkMainKt <output-root> [warmupFrames] [measuredFrames]"
    }
    val outputDir = Path.of(args[0])
    val warmupFrames = args.getOrNull(1)?.let(::parsePositiveOrZero) ?: PerFamilyBenchmark.DEFAULT_WARMUP_FRAMES
    val measuredFrames = args.getOrNull(2)?.let(::parsePositive) ?: PerFamilyBenchmark.DEFAULT_MEASURED_FRAMES

    val benchmark = PerFamilyBenchmark().run(outputDir, warmupFrames, measuredFrames)

    val frameCount = warmupFrames + measuredFrames
    PipelineCacheTelemetryReport.forBenchmarkFamilies(frameCount).writeTo(outputDir)

    FrameGatePolicy().evaluateAll(benchmark.sampledMeasurements()).writeTo(outputDir)

    val sampled = benchmark.results.count { it.status == BenchmarkFamilyStatus.Sampled }
    println(
        "Per-family benchmark complete: backend=${benchmark.backend} " +
            "adapter=${benchmark.adapterInfo ?: "none"} sampled=$sampled/${benchmark.results.size} " +
            "frames=$warmupFrames+$measuredFrames output=${outputDir.toAbsolutePath()}",
    )
}

private fun parsePositive(raw: String): Int {
    val value = raw.toIntOrNull() ?: throw IllegalArgumentException("expected an Int: $raw")
    require(value > 0) { "value must be positive: $value" }
    return value
}

private fun parsePositiveOrZero(raw: String): Int {
    val value = raw.toIntOrNull() ?: throw IllegalArgumentException("expected an Int: $raw")
    require(value >= 0) { "value must not be negative: $value" }
    return value
}
