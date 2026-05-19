package org.skia.gpu.webgpu.benchmarks

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.WebGpuContext
import org.skia.tests.BigRectGM
import org.skia.tests.ConicPathsGM
import org.skia.tests.ConvexPathsGM
import org.skia.tests.DrawBitmapRect3
import org.skia.tests.HairlinesGM
import org.skia.tests.ShallowGradientLinearGM
import java.io.File

/**
 * G7.x — benchmark methodology revision.
 *
 * **Replaces the pre-G7.x `PathHeavyBenchmark`** (PR #577). That file
 * used a `System.nanoTime` outer loop with 1 warmup iteration ; the
 * methodology was acknowledged as weak (cf. its KDoc) but accepted as
 * a quick baseline because JMH's Gradle wiring conflicted with the
 * macOS GLFW thread-0 constraint. G7.x ships the **statistical
 * methodology** without the framework dependency :
 *
 *  - 5 warmup iterations (drives JIT through tier-1 → tier-3).
 *  - 3 cold + 10 steady iterations sampled separately ; cold catches
 *    the pipeline-cache transitional state ; steady is the headline.
 *  - Per-phase decomposition ([PhaseTimings]) on every iteration so
 *    the G8 trigger (*"path tessellation > 30 % of total time"*) can
 *    be evaluated from the data, not inferred.
 *  - Cold + steady reported separately ; full distribution (min /
 *    avg / median / p95 / stddev / relErr%) per phase per backend.
 *  - JSON output to `gpu-raster/build/bench/results.json` (JMH-style
 *    shape — see [BenchmarkReport.renderJson]).
 *  - Markdown summary to `gpu-raster/build/bench/summary.md` and
 *    stdout.
 *
 * **GM set** :
 *
 *  - `ConvexPathsGM`, `ConicPathsGM`, `HairlinesGM` — path-heavy
 *    rows that drive the G8 trigger evaluation.
 *  - `BigRectGM` — path-light control. drawRect-only ; the ratio
 *    should land near 1× and the tessellate phase should be near 0 %.
 *  - `ShallowGradientLinearGM` — gradient-heavy row.
 *  - `DrawBitmapRect3` — bitmap-shader row (drawImageRect smoke).
 *
 * **Why no JMH ?** The `me.champeau.jmh` Gradle plugin (0.7.x) creates
 * a separate `jmh` source set that does NOT extend `test`. The
 * benchmark needs `WebGpuSink`, `SkWebGpuDevice`, the cross-test GMs
 * (`:skia-integration-tests:test`), and `:cpu-raster`'s `TestUtils` —
 * wiring the `jmh` source set to see all of them is non-trivial. The
 * macOS-specific `-XstartOnFirstThread` JVM flag would also need to
 * be duplicated onto every JMH fork.
 *
 * The methodology in this file mirrors the JMH defaults the user
 * asked for (`@Fork(3) @Warmup(5) @Measurement(10) AverageTime`) :
 *  - `Warmup(5)` ⇒ [BenchmarkPipeline.WARMUP_ITERATIONS] = 5.
 *  - `Measurement(10)` ⇒ [BenchmarkPipeline.STEADY_ITERATIONS] = 10.
 *  - `AverageTime` ⇒ the headline metric is `avgMs`.
 *  - `Fork(3)` is the one JMH guarantee we don't reproduce — see the
 *    KDoc on [BenchmarkPipeline] for the trade-off rationale.
 */
@Tag("benchmark")
public class PathHeavyBenchmark {

    @Test
    public fun `bench - jmh-style methodology with phase decomposition`() {
        val ctxProbe = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(ctxProbe != null, "No WebGPU adapter")
        // We open one context for the entire benchmark sweep so the
        // per-GM warmup gets a hot device. WebGpuContext.createOrNull
        // logs the adapter info on creation — that line is the only
        // GPU-adapter signal we expose to the report.
        val ctx = ctxProbe!!
        try {
            val env = BenchmarkReport.Environment.capture(gpuAdapter = ctx.adapterInfo)

            // 4-class GM set : path-heavy ×3, path-light ×1,
            // gradient ×1, bitmap ×1. The classification drives the
            // G8 trigger verdict (only PATH_HEAVY rows participate).
            val cases = listOf(
                BenchmarkPipeline.GmCase(
                    gm = { ConvexPathsGM() },
                    label = "ConvexPathsGM",
                    klass = BenchmarkPipeline.GmClass.PATH_HEAVY,
                    referenceName = ConvexPathsGM().name(),
                ),
                BenchmarkPipeline.GmCase(
                    gm = { ConicPathsGM() },
                    label = "ConicPathsGM",
                    klass = BenchmarkPipeline.GmClass.PATH_HEAVY,
                    referenceName = ConicPathsGM().name(),
                ),
                BenchmarkPipeline.GmCase(
                    gm = { HairlinesGM() },
                    label = "HairlinesGM",
                    klass = BenchmarkPipeline.GmClass.PATH_HEAVY,
                    referenceName = HairlinesGM().name(),
                ),
                BenchmarkPipeline.GmCase(
                    gm = { BigRectGM() },
                    label = "BigRectGM",
                    klass = BenchmarkPipeline.GmClass.PATH_LIGHT,
                    referenceName = BigRectGM().name(),
                ),
                BenchmarkPipeline.GmCase(
                    gm = { ShallowGradientLinearGM() },
                    label = "ShallowGradientLinearGM",
                    klass = BenchmarkPipeline.GmClass.GRADIENT,
                    referenceName = ShallowGradientLinearGM().name(),
                ),
                BenchmarkPipeline.GmCase(
                    gm = { DrawBitmapRect3() },
                    label = "DrawBitmapRect3",
                    klass = BenchmarkPipeline.GmClass.BITMAP,
                    referenceName = DrawBitmapRect3().name(),
                ),
            )

            val results = cases.map { case -> BenchmarkPipeline.runGm(ctx, case) }

            val markdown = BenchmarkReport.renderMarkdown(env, results)
            val json = BenchmarkReport.renderJson(env, results)
            println(markdown)

            val outDir = File("build/bench").apply { mkdirs() }
            File(outDir, "summary.md").writeText(markdown)
            File(outDir, "results.json").writeText(json)
            // Keep the legacy `build/bench-baseline.txt` path alive
            // for tooling that has already wired against it (the
            // pre-G7.x baseline was at this location).
            File("build").apply { mkdirs() }
            File("build/bench-baseline.txt").writeText(markdown)
        } finally {
            ctx.close()
        }
    }
}
