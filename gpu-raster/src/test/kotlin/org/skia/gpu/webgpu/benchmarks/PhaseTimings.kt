package org.skia.gpu.webgpu.benchmarks

/**
 * Per-phase nanosecond accumulators for one benchmark iteration.
 *
 * G7.x phase decomposition. The five phases capture the cost model
 * MIGRATION_PLAN_GPU_WEBGPU.md G8 ("compute-shader path tessellation")
 * is gated on : if [tessellateNs] dominates, the G8 compute migration
 * has headroom ; if [readbackNs] or [similarityNs] dominates, G8 won't
 * shift the needle and the trigger should NOT fire.
 *
 * **CPU vs GPU phase mapping.**
 *
 * | Phase           | CPU rasterizer                                | GPU backend                                          |
 * |-----------------|-----------------------------------------------|------------------------------------------------------|
 * | [setupNs]       | `SkBitmap` alloc + `eraseColor` + `SkCanvas`  | `SkWebGpuDevice` ctor + bg colour                    |
 * | [tessellateNs]  | Path flattening + scanline tessellation       | Path flattening + CPU-side fan tessellation (G3.3b)  |
 * | [submitNs]      | Per-scanline `drawPath` rasterization         | `flush()` (encode + queue.submit + GPU execution)    |
 * | [readbackNs]    | n/a (zero, bitmap is already in main memory)  | `target.readPixels()` (mapAsync + de-pad row stride) |
 * | [similarityNs]  | reference PNG load + per-pixel diff           | reference PNG load + per-pixel diff                  |
 *
 * Note : on the GPU side, [tessellateNs] and [submitNs] are not
 * cleanly separable from outside `SkWebGpuDevice` — the device's
 * `flush()` interleaves vertex-buffer upload (post-tessellation) and
 * `queue.submit()`. The benchmark wrapper instead measures :
 *
 *  - **draw-build phase** (pre-flush) = path enqueue + per-draw CPU
 *    work including fan tessellation. Bucketed into [tessellateNs].
 *  - **flush phase** (encoder + submit + readback) = bucketed into
 *    `submitNs + readbackNs` ; the [WebGpuSink] benchmark wrapper
 *    splits the two by instrumenting the device API call boundaries.
 *
 * This split is adequate for the G8 trigger : it answers "is the
 * CPU-side path work (which G8 moves to a compute shader) a meaningful
 * fraction of the total ?" without needing to instrument the WGSL
 * fragment shaders.
 */
public data class PhaseTimings(
    val setupNs: Long,
    val tessellateNs: Long,
    val submitNs: Long,
    val readbackNs: Long,
    val similarityNs: Long,
) {
    /** Sum of every phase in nanoseconds. */
    public val totalNs: Long get() = setupNs + tessellateNs + submitNs + readbackNs + similarityNs

    public companion object {
        public val ZERO: PhaseTimings = PhaseTimings(0, 0, 0, 0, 0)
    }
}
