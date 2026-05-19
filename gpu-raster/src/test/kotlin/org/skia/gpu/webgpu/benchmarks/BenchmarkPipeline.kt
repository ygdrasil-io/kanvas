package org.skia.gpu.webgpu.benchmarks

import org.skia.gpu.webgpu.WebGpuContext
import org.skia.tests.GM

/**
 * The G7.x JMH-style benchmark pipeline.
 *
 * The methodology mirrors what JMH (`@Fork(3) @Warmup(5) @Measurement(10)
 * @BenchmarkMode(AverageTime)`) would have produced, implemented
 * manually because pulling in the `me.champeau.jmh` Gradle plugin
 * collides with our two existing constraints :
 *
 * 1. The `:gpu-raster:test` task carries macOS `-XstartOnFirstThread`
 *    + `--add-opens` + `--enable-native-access` JVM args required by
 *    GLFW / wgpu4k. The JMH plugin spawns its own JVMs with its own
 *    arg list ; mirroring the existing args is possible but adds a
 *    duplicate maintenance surface.
 * 2. The JMH plugin creates a separate `jmh` source set that does not
 *    extend `test`. `WebGpuSink`, `runGmTest`, and the cross-test
 *    GMs all live under `src/test/kotlin/...` or in `:cpu-raster:main`
 *    only as test fixtures ; wiring the `jmh` source set to see all
 *    of them is non-trivial.
 *
 * Bail decision : ship the **statistical methodology** without the
 * framework. Multi-fork JIT washout is the only thing we lose by not
 * forking (single-JVM tier-N JIT can lock in a slow shape) ; for
 * 10-500 ms per-GM costs the practical impact is small, and we report
 * stddev + relative error so a noisy run is visible.
 *
 * ## Iteration model
 *
 * For each `(GM, backend)` pair :
 *  - **Warmup** : [WARMUP_ITERATIONS] untimed iterations. Drives the
 *    JIT through tier-1 → tier-2 → tier-3 for the entire render path
 *    (paint state, scanline rasterizer, GPU pipeline cache fill).
 *  - **Cold sample** : the next [COLD_ITERATIONS] iterations. Captures
 *    the post-warmup pipeline-cache transitional state ; reported as
 *    a separate stats block so the steady-state numbers aren't
 *    polluted by the tail of warmup.
 *  - **Steady sample** : [STEADY_ITERATIONS] more iterations. The
 *    canonical numbers reported as "steady state" in the README and
 *    used for the G8 trigger evaluation.
 *
 * Each iteration captures a [PhaseTimings] ; the cold and steady
 * samples each fold into a [PhaseStats] for reporting.
 *
 * ## GPU context lifetime
 *
 * The GPU pass reuses a single [WebGpuContext] across all iterations
 * of a single GM. Per-GM teardown of the context would introduce a
 * fixed ~150 ms overhead that JMH would NOT include in its steady
 * state — the cross-test cost model is different (each cross-test
 * pays for context creation), but the benchmark answers a different
 * question ("how fast is a single draw once the device is hot ?").
 *
 * Why not share the context across GMs too ? Because some GMs swap
 * the device's pipeline-cache contents in incompatible ways
 * (gradient vs bitmap shader). Per-GM context teardown is the
 * smallest unit that guarantees clean cache state for the next GM ;
 * within a single GM, the cache is reused (steady-state semantic).
 */
public object BenchmarkPipeline {

    public const val WARMUP_ITERATIONS: Int = 5
    public const val COLD_ITERATIONS: Int = 3
    public const val STEADY_ITERATIONS: Int = 10

    public data class GmCase(
        val gm: () -> GM,
        val label: String,
        val klass: GmClass,
        val referenceName: String? = null,
    )

    /**
     * GM workload class — labels the rough cost profile so the report
     * can group similar GMs and so the G8 trigger evaluation focuses
     * on the right rows.
     */
    public enum class GmClass {
        /** Many paths / heavy CPU-side tessellation. */
        PATH_HEAVY,
        /** Trivial geometry (drawRect only). Control row : should be ~1× ratio. */
        PATH_LIGHT,
        /** Gradient shader. CPU LUT vs GPU fragment cost. */
        GRADIENT,
        /** Bitmap shader / drawImageRect. CPU sampler vs GPU sampler cost. */
        BITMAP,
    }

    public data class GmResult(
        val case: GmCase,
        val cpuCold: PhaseStats,
        val cpuSteady: PhaseStats,
        val gpuCold: PhaseStats,
        val gpuSteady: PhaseStats,
    )

    /**
     * Run the full pipeline (warmup + cold + steady) on a single GM,
     * both backends. The caller is responsible for opening the GPU
     * context ; this method does not skip — checking adapter
     * availability is the test's job.
     */
    public fun runGm(ctx: WebGpuContext, case: GmCase): GmResult {
        // -------- CPU --------
        repeat(WARMUP_ITERATIONS) { InstrumentedRunners.renderCpuInstrumented(case.gm(), case.referenceName) }
        val cpuCold = ArrayList<PhaseTimings>(COLD_ITERATIONS)
        repeat(COLD_ITERATIONS) { cpuCold += InstrumentedRunners.renderCpuInstrumented(case.gm(), case.referenceName) }
        val cpuSteady = ArrayList<PhaseTimings>(STEADY_ITERATIONS)
        repeat(STEADY_ITERATIONS) { cpuSteady += InstrumentedRunners.renderCpuInstrumented(case.gm(), case.referenceName) }

        // -------- GPU --------
        repeat(WARMUP_ITERATIONS) { InstrumentedRunners.renderGpuInstrumented(ctx, case.gm(), case.referenceName) }
        val gpuCold = ArrayList<PhaseTimings>(COLD_ITERATIONS)
        repeat(COLD_ITERATIONS) { gpuCold += InstrumentedRunners.renderGpuInstrumented(ctx, case.gm(), case.referenceName) }
        val gpuSteady = ArrayList<PhaseTimings>(STEADY_ITERATIONS)
        repeat(STEADY_ITERATIONS) { gpuSteady += InstrumentedRunners.renderGpuInstrumented(ctx, case.gm(), case.referenceName) }

        return GmResult(
            case = case,
            cpuCold = PhaseStats.from(cpuCold),
            cpuSteady = PhaseStats.from(cpuSteady),
            gpuCold = PhaseStats.from(gpuCold),
            gpuSteady = PhaseStats.from(gpuSteady),
        )
    }
}
