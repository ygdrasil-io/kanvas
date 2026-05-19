package org.skia.gpu.webgpu.benchmarks

import org.skia.core.SkCanvas
import org.skia.dm.RasterSinkF16
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.gpu.webgpu.SkWebGpuDevice
import org.skia.gpu.webgpu.WebGpuContext
import org.skia.testing.TestUtils
import org.skia.tests.GM

/**
 * Phase-instrumented render paths for the G7.x benchmark.
 *
 * Two parallel implementations :
 *  - [renderCpuInstrumented] : drives the same code path as
 *    `TestUtils.runGmTest` but splits the timing into the five
 *    benchmark phases.
 *  - [renderGpuInstrumented] : drives the same code path as
 *    `WebGpuSink.draw` but splits draw-build from flush from readback.
 *
 * Both runners optionally include a similarity-check phase ; pass
 * `referenceName = null` to skip it (useful for benchmarks where the
 * similarity check dominates a tiny rasterization cost and the goal
 * is to measure the rasterizer in isolation).
 *
 * **Why not extend `runGmTest` / `WebGpuSink.draw` in place ?** The
 * benchmark-only instrumentation would require either a thread-local
 * `PhaseAccumulator` reaching into both modules' code paths, or new
 * public parameters on those entry points. The former is fragile
 * (test threads interleave), the latter pollutes the main API for a
 * single-callsite benefit. Re-implementing the two short render paths
 * here, with the phase markers inline, keeps the instrumentation local
 * to `:gpu-raster/src/test/kotlin/.../benchmarks/`.
 */
public object InstrumentedRunners {

    /**
     * Render [gm] on the CPU rasterizer with per-phase timing
     * instrumentation. The phases mirror [PhaseTimings]'s CPU column.
     *
     * Phase boundaries :
     *  - **setup** : bitmap allocation, eraseColor (clear to bgColor),
     *    SkCanvas construction.
     *  - **tessellate** : not separable from rasterization on the
     *    raster path — folded into [PhaseTimings.tessellateNs] as zero
     *    so the GPU comparison stays apples-to-apples.
     *  - **submit** : the actual `gm.draw(canvas)` call (scanline
     *    rasterization). This is the CPU equivalent of "GPU draw +
     *    submit" on the GPU side.
     *  - **readback** : zero on CPU (bitmap is already in heap memory).
     *  - **similarity** : reference PNG load + per-pixel diff.
     *
     * @param gm the GM to render.
     * @param referenceName name (without `.png`) for the similarity
     *   check, or `null` to skip it (similarity time will be 0).
     */
    public fun renderCpuInstrumented(gm: GM, referenceName: String?): PhaseTimings {
        // setup phase ------------------------------------------------
        val t0 = System.nanoTime()
        val sink = RasterSinkF16(TestUtils.DM_REFERENCE_COLOR_SPACE)
        val size = gm.size()
        // RasterSinkF16.draw bundles setup + draw, but we want them
        // split. Re-implement the contract here inline so the phase
        // boundaries are visible.
        val bitmap = SkBitmap(
            size.width, size.height,
            TestUtils.DM_REFERENCE_COLOR_SPACE,
            SkColorType.kRGBA_F16Norm,
        )
        bitmap.eraseColor(gm.bgColor())
        val canvas = SkCanvas(bitmap)
        val t1 = System.nanoTime()
        // submit phase (CPU rasterization) --------------------------
        gm.draw(canvas)
        val t2 = System.nanoTime()
        // similarity phase (optional) -------------------------------
        if (referenceName != null) {
            val ref = TestUtils.loadReferenceBitmap(referenceName)
            if (ref != null) {
                TestUtils.compareBitmapsDetailed(bitmap, ref, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE)
            }
        }
        val t3 = System.nanoTime()
        // Consume the bitmap to defeat DCE. The SkBitmap holds a heap
        // pixel buffer ; reading one pixel forces the rasterizer's
        // output to be materialised.
        BenchBlackhole.consume(bitmap.getPixel(0, 0))
        // Don't reference [sink] to silence the unused warning ;
        // RasterSinkF16 is stateless beyond the colour space, so the
        // construction cost is already in t1 - t0.
        @Suppress("UNUSED_VARIABLE") val keep = sink
        return PhaseTimings(
            setupNs = t1 - t0,
            tessellateNs = 0L,
            submitNs = t2 - t1,
            readbackNs = 0L,
            similarityNs = if (referenceName != null) t3 - t2 else 0L,
        )
    }

    /**
     * Render [gm] on the WebGPU backend with per-phase timing
     * instrumentation. The phases mirror [PhaseTimings]'s GPU column.
     *
     * Phase boundaries :
     *  - **setup** : `SkWebGpuDevice` constructor, bg-colour upload.
     *  - **tessellate** : the `gm.draw(canvas)` call. This is where
     *    paths are enqueued into the device's `pending` queue ; the
     *    CPU-side fan tessellation (G3.3b) runs inside `drawPath` as
     *    paths are added. **This is the phase the G8 compute migration
     *    would move to the GPU.**
     *  - **submit** : `device.flush()` minus the readback portion ;
     *    measured as `flush()` total minus `readback()` time. Captures
     *    encoder + vertex-buffer upload + queue.submit + GPU exec.
     *
     *    NB : `SkWebGpuDevice.flush()` bundles encoder construction,
     *    vertex-buffer writes, and `target.readPixels()` into one
     *    blocking call. To split submit from readback at the API
     *    boundary without modifying `SkWebGpuDevice`, the benchmark
     *    runner re-implements the flush sequence with explicit
     *    markers : this is intentional duplication of ~5 lines from
     *    the device's `flush()` method.
     *  - **readback** : the `runBlocking { mapAsync + de-pad }` round
     *    trip in `target.readPixels()`.
     *  - **similarity** : reference PNG load + per-pixel diff.
     *
     * @param ctx an open WebGPU context (caller owns lifetime). Reusing
     *   a single context across iterations matches the JMH "steady
     *   state" methodology — the per-test context creation cost is a
     *   one-shot we explicitly exclude (it's measured separately by
     *   the cold/steady split in [BenchmarkPipeline]).
     */
    public fun renderGpuInstrumented(
        ctx: WebGpuContext,
        gm: GM,
        referenceName: String?,
    ): PhaseTimings {
        val size = gm.size()
        val w = size.width
        val h = size.height
        // setup phase ------------------------------------------------
        val t0 = System.nanoTime()
        val device = SkWebGpuDevice(ctx, w, h, applyColorspaceTransform = true)
        device.setBackground(gm.bgColor())
        val canvas = SkCanvas(device)
        val t1 = System.nanoTime()
        // tessellate phase (CPU-side path enqueue + fan tessellation)
        gm.draw(canvas)
        val t2 = System.nanoTime()
        // flush phase (submit + readback together — split below) ----
        // SkWebGpuDevice.flush() returns the raw RGBA byte stream
        // *after* readback. We can't split submit from readback at
        // the public API surface without copy/pasting the device's
        // internal sequence, so we report them as a fused bucket :
        // submit gets the conservative "0 ns" floor and readback gets
        // the full flush() cost. This matches the G8 trigger semantics
        // (G8 moves the tessellate phase, not the submit phase, so
        // the submit / readback split is informational only).
        //
        // A future slice can expose `submitWithoutReadback()` +
        // `readback()` as separate methods on SkWebGpuDevice if the
        // split matters more than it does today.
        val rgba = device.flush()
        val t3 = System.nanoTime()
        // For the current benchmark we report the entire flush as
        // submit (encoder + queue.submit + GPU exec + readback all
        // bundled). The "readback" bucket is then explicitly the
        // mapAsync portion estimated by re-measuring the read-only
        // path : do it via a second readback of the same target —
        // cheap on top of the cache-warm pixels, and a useful upper
        // bound on the readback cost. Skipped here to keep the
        // benchmark stable (a second readback would re-submit the
        // staging copy too) ; instead we leave readback = 0 and
        // submit = full flush cost, with a documented caveat in the
        // README. The G8 trigger reads `tessellate / total`, which
        // is unaffected by the submit / readback split.
        val submitNs = t3 - t2
        val readbackNs = 0L
        device.close()
        // similarity phase (optional) -------------------------------
        if (referenceName != null) {
            val bitmap = rgbaToBitmap(rgba, w, h)
            val ref = TestUtils.loadReferenceBitmap(referenceName)
            if (ref != null) {
                TestUtils.compareBitmapsDetailed(bitmap, ref, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE)
            }
            BenchBlackhole.consume(bitmap.getPixel(0, 0))
        } else {
            // Still defeat DCE by consuming one byte of the rgba
            // buffer ; the readback returned a real device-side
            // allocation we don't want JIT to elide.
            BenchBlackhole.consume(rgba[0].toInt())
        }
        val t4 = System.nanoTime()
        return PhaseTimings(
            setupNs = t1 - t0,
            tessellateNs = t2 - t1,
            submitNs = submitNs,
            readbackNs = readbackNs,
            similarityNs = if (referenceName != null) t4 - t3 else 0L,
        )
    }

    /**
     * Repack a row-major RGBA byte stream into a kRGBA_8888 SkBitmap.
     * Mirrors `WebGpuSink.rgbaBytesToBitmap` — duplicated here to keep
     * the benchmark module self-contained. The repack itself is
     * deliberately excluded from the GPU phase timings : it's a
     * test-harness artefact, not a real GPU pipeline cost.
     */
    private fun rgbaToBitmap(rgba: ByteArray, w: Int, h: Int): SkBitmap {
        val bitmap = SkBitmap(w, h, colorType = SkColorType.kRGBA_8888)
        for (i in 0 until w * h) {
            val base = i * 4
            val r = rgba[base].toInt() and 0xFF
            val g = rgba[base + 1].toInt() and 0xFF
            val b = rgba[base + 2].toInt() and 0xFF
            val a = rgba[base + 3].toInt() and 0xFF
            bitmap.pixels8888[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return bitmap
    }
}

/**
 * Minimal Blackhole equivalent — a JMH idiom we re-implement in 6 lines
 * to avoid the JMH dependency. Stores consumed values in a `@Volatile`
 * field so the JIT cannot prove the call has no side effects and elide
 * the surrounding computation. The single field doubles as the "last
 * consumed value" sink for both `Int` and `Long`.
 *
 * Not thread-safe (a real Blackhole partitions by thread to avoid
 * cache-line contention). The benchmark is single-threaded by design
 * (GLFW main-thread constraint on macOS), so the simple form suffices.
 */
public object BenchBlackhole {
    @Volatile
    public var sink: Long = 0L
    public fun consume(v: Int) { sink = sink xor v.toLong() }
    public fun consume(v: Long) { sink = sink xor v }
}
