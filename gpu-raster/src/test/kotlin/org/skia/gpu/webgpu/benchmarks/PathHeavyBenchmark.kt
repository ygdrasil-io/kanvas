package org.skia.gpu.webgpu.benchmarks

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.WebGpuContext
import org.skia.gpu.webgpu.WebGpuSink
import org.skia.testing.TestUtils
import org.skia.tests.ConicPathsGM
import org.skia.tests.ConvexPathsGM
import org.skia.tests.GM
import org.skia.tests.HairlinesGM
import java.io.File

/**
 * G7 finalization — path-heavy GPU vs CPU per-iteration timings.
 *
 * **Purpose**. Phase G8 (compute-shader path tessellation, see
 * [MIGRATION_PLAN_GPU_WEBGPU.md](../../../../../../../../MIGRATION_PLAN_GPU_WEBGPU.md))
 * is gated on "≥ 2× speedup headroom on path-heavy GMs". This file
 * produces the **G7 baseline** the G8 trigger evaluation reads :
 * per-iteration wall-clock timings for the CPU rasterizer and the
 * WebGPU backend on three path-heavy GMs that exercise the most
 * tessellation work in the cross-test corpus.
 *
 * **Why no JMH**. The module's `build.gradle.kts` doesn't carry JMH and
 * pulling it in for one benchmark was deemed not worth the dependency
 * cost — the GM render times are 10-500 ms each, so JMH's per-call
 * timing overhead (microseconds) doesn't shift the signal. A plain
 * `System.nanoTime` outer loop with min / avg / p95 is enough to
 * separate the two backends and flag a ≥ 2× gap.
 *
 * **Why a `@Test` and not a `main`**. JUnit's test wiring already
 * handles the `-XstartOnFirstThread` AppKit dance for the GPU side and
 * the cross-platform classpath setup ; reusing it for the benchmark
 * avoids a duplicate gradle task. The test is tagged `"benchmark"` so
 * the default `:gpu-raster:test` run **does** include it (it produces
 * the baseline whenever the test job runs in CI), but a future opt-in
 * gate can use `-PexcludeTags=benchmark` if iteration time is a concern.
 *
 * **Output**. The benchmark prints a markdown-shaped table to stdout
 * and writes the same table to `gpu-raster/build/bench-baseline.txt`.
 * Re-running overwrites the file ; the most recent run is therefore
 * the canonical baseline.
 *
 * **Skip semantics**. Skips cleanly via `Assumptions.assumeTrue` when
 * no WebGPU adapter is available — same convention as the cross-tests.
 * In that case no baseline file is written ; the CI matrix's raster
 * job won't refresh the baseline (only the GPU job will).
 */
@Tag("benchmark")
class PathHeavyBenchmark {

    /**
     * Render `gm` `iterations` times on each backend (CPU raster,
     * WebGPU), measuring per-iteration wall-clock duration. Excludes
     * one untimed warm-up iteration on each side so JIT compilation
     * and pipeline-cache fills don't pollute the sample.
     */
    @Test
    fun `benchmark — path-heavy GMs across raster + GPU`() {
        // Note : Skip relies on the JUnit `Assumptions` API — the test
        // is reported as skipped (not failed) when no adapter exists.
        val ctxProbe = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(ctxProbe != null, "No WebGPU adapter")
        ctxProbe!!.close()

        val gms: List<Pair<String, () -> GM>> = listOf(
            "ConvexPathsGM" to { ConvexPathsGM() },
            "ConicPathsGM" to { ConicPathsGM() },
            "HairlinesGM" to { HairlinesGM() },
        )

        val iterations = 10
        val results = mutableListOf<BenchResult>()

        for ((label, factory) in gms) {
            // Raster pass — runs entirely outside the WebGPU context.
            val rasterDurations = mutableListOf<Long>()
            // Warm-up : JIT-compile the rasterizer entry points, fill
            // codec caches, prime any colorspace LUTs. Untimed.
            TestUtils.runGmTest(factory())
            repeat(iterations) {
                val t0 = System.nanoTime()
                TestUtils.runGmTest(factory())
                rasterDurations += System.nanoTime() - t0
            }

            // GPU pass — every WebGpuSink.draw call rebuilds the
            // context, mirroring the per-test cost model the cross-
            // tests pay. This is the timing the G8 trigger cares
            // about (compute-shader path tessellation would change
            // the per-draw cost, not the once-only init).
            val gpuDurations = mutableListOf<Long>()
            // Warm-up : pipeline-cache fill, shader module compile,
            // first GLFW window. Untimed.
            renderGpuOnce(factory())
            repeat(iterations) {
                val t0 = System.nanoTime()
                renderGpuOnce(factory())
                gpuDurations += System.nanoTime() - t0
            }

            results += BenchResult(
                label = label,
                raster = Stats.from(rasterDurations),
                gpu = Stats.from(gpuDurations),
            )
        }

        // Print to stdout + write the same payload to
        // `build/bench-baseline.txt`. Markdown-shaped so it pastes
        // cleanly into a PR description without further formatting.
        val payload = renderTable(results, iterations)
        println(payload)
        val outDir = File("build").apply { mkdirs() }
        File(outDir, "bench-baseline.txt").writeText(payload)
    }

    /**
     * One full GPU render of [gm] : open a fresh context, draw,
     * close. Mirrors the cross-test cost model — every cross-test
     * pays for context creation, single draw, context destruction.
     * A future "long-lived context" benchmark could amortise the
     * init across many draws, but G8 evaluation only cares about
     * the per-iteration draw cost as the cross-tests see it.
     */
    private fun renderGpuOnce(gm: GM) {
        val ctx = WebGpuContext.createOrNull()
            ?: error("WebGPU adapter disappeared mid-run")
        ctx.use { WebGpuSink.draw(it, gm) }
    }

    /**
     * Per-backend timing summary : min, avg, p95 (all in
     * milliseconds). p95 is the 95th-percentile sample —
     * `samples[ceil(0.95 * n) - 1]` after sort, matching the
     * "ignore the worst 5 %" reading.
     */
    private data class Stats(val minMs: Double, val avgMs: Double, val p95Ms: Double) {
        companion object {
            fun from(durationsNs: List<Long>): Stats {
                require(durationsNs.isNotEmpty()) { "empty sample" }
                val sortedMs = durationsNs.map { it / 1_000_000.0 }.sorted()
                val min = sortedMs.first()
                val avg = sortedMs.average()
                val p95Index = kotlin.math.ceil(0.95 * sortedMs.size).toInt() - 1
                val p95 = sortedMs[p95Index.coerceIn(0, sortedMs.size - 1)]
                return Stats(min, avg, p95)
            }
        }
    }

    private data class BenchResult(val label: String, val raster: Stats, val gpu: Stats)

    private fun renderTable(results: List<BenchResult>, iterations: Int): String {
        val sb = StringBuilder()
        sb.appendLine("# `gpu-raster` path-heavy benchmark baseline")
        sb.appendLine()
        sb.appendLine("Iterations per GM (per backend, after one untimed warm-up) : $iterations")
        sb.appendLine()
        sb.appendLine("| GM | raster min | raster avg | raster p95 | gpu min | gpu avg | gpu p95 | gpu/raster avg |")
        sb.appendLine("|----|-----------:|-----------:|-----------:|--------:|--------:|--------:|---------------:|")
        for (r in results) {
            val ratio = r.gpu.avgMs / r.raster.avgMs
            sb.appendLine(
                "| %s | %.2f ms | %.2f ms | %.2f ms | %.2f ms | %.2f ms | %.2f ms | %.2fx |".format(
                    r.label,
                    r.raster.minMs, r.raster.avgMs, r.raster.p95Ms,
                    r.gpu.minMs, r.gpu.avgMs, r.gpu.p95Ms,
                    ratio,
                ),
            )
        }
        sb.appendLine()
        sb.appendLine("Reading : `gpu/raster avg < 1.0` means the GPU backend is faster on this GM. ")
        sb.appendLine("G8 (compute-shader path tessellation) is gated on the GPU backend showing ")
        sb.appendLine("at least 2× headroom against the CPU rasterizer on path-heavy GMs — i.e. ")
        sb.appendLine("`gpu/raster avg <= 0.5`. The current baseline is below.")
        return sb.toString()
    }
}
