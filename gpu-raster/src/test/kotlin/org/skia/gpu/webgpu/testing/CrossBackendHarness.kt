package org.skia.gpu.webgpu.testing

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.skia.foundation.SkBitmap
import org.skia.gpu.webgpu.WebGpuContext
import org.skia.gpu.webgpu.WebGpuSink
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils
import org.skia.tests.GM
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Cross-backend variant of [CrossTestHarness].
 *
 * Where [runGpuCrossTest] runs a [GM] through the WebGPU backend only and
 * checks one floor, [runCrossBackendTest] runs the same GM through
 * **both** the CPU raster backend (`TestUtils.runGmTest`) and the WebGPU
 * backend ([WebGpuSink.draw]) in a single test invocation, asserts that
 * each backend stays at or above its own floor, and publishes both scores
 * under distinct `-raster` / `-gpu` ratchet keys.
 *
 * ## Why bother
 *
 * G7 is the cross-validation phase of the WebGPU port (see
 * MIGRATION_PLAN_GPU_WEBGPU.md §G7). The whole point of running both
 * backends from the same test entry is to surface raster ↔ GPU drift
 * **as it happens**, instead of letting it bake in for weeks until
 * someone hand-diffs the two ratchet files. The harness makes the
 * implicit contract explicit : when a single change degrades only one
 * backend, the failing test name tells you which.
 *
 * ## Diff visual
 *
 * If either backend's score falls **inside the `floor + 2 %` warning
 * band** (or strictly below the floor), or the two backends diverge
 * by more than 2 %, the harness writes a three-PNG dump under
 * `gpu-raster/build/debug-images/` :
 *
 *  - `<gm-name>-raster.png` : raster output
 *  - `<gm-name>-gpu.png`    : GPU output
 *  - `<gm-name>-diff.png`   : per-pixel `|raster - gpu| * 4` (clamped),
 *                             brightness amplified so even sub-LSB drift
 *                             is visible at a glance. Opaque alpha so the
 *                             PNG renders in any viewer.
 *
 * The `*4` amplification is the convention used by Skia's own DM diff
 * viewer ; 1-LSB differences (the typical AA edge case) come out as a
 * dim 4/255 grey, while real geometric drift saturates to white. A
 * triage workflow opens all three files side by side in a file
 * browser ; we deliberately do NOT build a labelled `Graphics2D`-
 * based triptych image because `Graphics2D.drawString` on macOS pulls
 * in the AWT font subsystem on the AppKit main thread, which
 * deadlocks `glfwDestroyWindow` at WebGPU context close (see
 * [saveCrossBackendDiff] for the full rationale).
 *
 * ## Migration pattern
 *
 * A pre-G7-follow-up pair of tests :
 *
 * ```kotlin
 * // gpu-raster/src/test/.../BeziersWebGpuTest.kt
 * class BeziersWebGpuTest {
 *     @Test fun `... GPU backend`() = runGpuCrossTest(BeziersGM(), floor = 96.9)
 * }
 *
 * // skia-integration-tests/src/test/.../BeziersTest.kt
 * class BeziersTest {
 *     @Test fun `... matches beziers_png within tolerance`() { /* 20 lines */ }
 * }
 * ```
 *
 * collapses (on the GPU side) to a single cross-backend call :
 *
 * ```kotlin
 * class BeziersCrossBackendTest {
 *     @Test fun `BeziersGM matches reference on both raster and GPU`() {
 *         runCrossBackendTest(BeziersGM(), rasterFloor = 88.0, gpuFloor = 96.9)
 *     }
 * }
 * ```
 *
 * The single-backend CPU and GPU tests stay around — they're the per-
 * backend CI guard the matrix in `.github/workflows/test.yml` runs.
 * Cross-backend tests are an **additional** layer that fires on each
 * full-stack run.
 *
 * @see CrossTestHarness.runGpuCrossTest single-backend GPU equivalent.
 */
public object CrossBackendHarness {

    /**
     * Width of the "either backend is within this distance of its floor"
     * warning band, **and** the cross-backend divergence threshold.
     *
     * The diff PNG dump fires when ANY of :
     *  - `rasterScore < rasterFloor + WARNING_BAND_PERCENT`
     *  - `gpuScore < gpuFloor + WARNING_BAND_PERCENT`
     *  - `|rasterScore - gpuScore| > WARNING_BAND_PERCENT`
     *
     * The cross-backend divergence criterion mirrors the original G7
     * plan in MIGRATION_PLAN_GPU_WEBGPU.md ("si raster et GPU divergent
     * de > 2 %, sauvegarder les deux PNGs"). The two floor-band checks
     * cover regressions on either backend in isolation (one backend
     * tanks while the other stays put).
     *
     * For very tight floors (>= 98) the warning-band check is
     * effectively "always on", which is fine — tight-floor tests are
     * exactly the ones where having a permanent diff artefact on disk
     * pays off the moment a regression lands.
     */
    public const val WARNING_BAND_PERCENT: Double = 2.0

    /**
     * Outcome of a cross-backend run. Returned from [runCrossBackendTest]
     * so callers can pile additional invariants on top of the per-backend
     * floors (e.g. assert raster vs GPU parity within X percent).
     */
    public data class Result(
        val raster: BitmapComparison,
        val gpu: BitmapComparison,
    )

    /**
     * Render [gm] through **both** the CPU raster backend and the WebGPU
     * backend, compare each against `original-888/<referenceName>.png`,
     * and assert each meets its respective floor.
     *
     * Skips the whole test (via `Assumptions.assumeTrue`) when no
     * WebGPU adapter is available — same skip semantics as
     * [runGpuCrossTest]. The CI matrix relies on this : the raster job
     * runs `:cpu-raster` + `:skia-integration-tests` (where the
     * per-backend raster tests live) and the GPU job runs `:gpu-raster`
     * (where this harness + the cross-backend tests live). Splitting
     * the raster-only coverage from the cross-backend coverage keeps
     * the matrix' raster job fast and driver-free, and lets the GPU
     * job own the parity gate.
     *
     * @param gm the [GM] to render.
     * @param rasterFloor similarity floor (0..100) for the CPU output.
     * @param gpuFloor similarity floor (0..100) for the GPU output.
     * @param referenceName name of the reference PNG (without `.png`).
     *   Defaults to [GM.name].
     * @param rasterTolerance per-channel byte tolerance for the raster
     *   comparison. Defaults to [TestUtils.TEXTUAL_GM_TOLERANCE] — most
     *   GPU floors are already tuned with this tolerance, but raster
     *   GMs often use `tolerance = 1`. Override per call if needed.
     * @param gpuTolerance per-channel byte tolerance for the GPU
     *   comparison. Defaults to [TestUtils.TEXTUAL_GM_TOLERANCE].
     * @param logTag bracketed prefix on diagnostic `println` lines.
     *   Defaults to a tag derived from [referenceName].
     */
    public fun runCrossBackendTest(
        gm: GM,
        rasterFloor: Double,
        gpuFloor: Double,
        referenceName: String = gm.name(),
        rasterTolerance: Int = TestUtils.TEXTUAL_GM_TOLERANCE,
        gpuTolerance: Int = TestUtils.TEXTUAL_GM_TOLERANCE,
        logTag: String = defaultLogTag(referenceName),
    ): Result {
        // Skip cleanly if no WebGPU adapter is available — same skip
        // semantics as [runGpuCrossTest].
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // All Java2D + SkCanvas + ImageIO work happens INSIDE the
        // WebGPU context's `.use { }` block, mirroring the existing
        // single-backend tests. On macOS, GLFW's `glfwDestroyWindow`
        // is routed through the AppKit main thread, and so is
        // Java2D's Cocoa interop. If Java2D is *only* initialised
        // outside the GLFW window's lifetime (e.g., after a previous
        // test's use block closed), the next test's
        // `glfwDestroyWindow` hangs in a Metal/AppKit deadlock.
        // Keeping every Java2D / Skia call inside the same use block
        // that opened GLFW keeps the AppKit dispatcher consistent
        // across tests in a single JVM session, which is the
        // arrangement the existing 17 GPU tests already rely on.
        val result = context!!.use { ctx ->
            // **GPU draw first**, while GLFW is freshly opened and no
            // CPU rasterizer / Java2D / SkCanvas work has yet run on
            // this thread. The existing single-backend GPU tests do
            // exactly this — call `WebGpuSink.draw` as the FIRST
            // operation inside the use block — and they don't hang at
            // `glfwDestroyWindow`. Doing CPU rasterization or
            // `loadReferenceBitmap` (which goes through `SkCodec` →
            // `ImageIO`) before the GPU draw triggers AWT init on the
            // Test worker thread *between* GLFW init and the first
            // Metal command submission, which on macOS deadlocks the
            // Metal command queue at the next test's `glfwDestroyWindow`.
            val gpuBitmap = WebGpuSink.draw(ctx, gm)

            // Now safe to touch Java2D / Skia codecs / SkCanvas — the
            // GPU pipeline has submitted its work and the AppKit
            // dispatcher is in a consistent state.
            val reference = TestUtils.loadReferenceBitmap(referenceName)
                ?: error("original-888/$referenceName.png missing")

            val gpuCmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = gpuTolerance,
            )
            println(
                "[$logTag/gpu] similarity=${"%.2f".format(gpuCmp.similarity)}%, " +
                    "matching=${gpuCmp.matchingPixels}/${gpuCmp.totalPixels}, " +
                    "maxDiff=${gpuCmp.maxChannelDiff}",
            )

            // Raster half (CPU rasterizer + comparison).
            val rasterBitmap = TestUtils.runGmTest(gm)
            val rasterCmp = TestUtils.compareBitmapsDetailed(
                rasterBitmap, reference, tolerance = rasterTolerance,
            )
            println(
                "[$logTag/raster] similarity=${"%.2f".format(rasterCmp.similarity)}%, " +
                    "matching=${rasterCmp.matchingPixels}/${rasterCmp.totalPixels}, " +
                    "maxDiff=${rasterCmp.maxChannelDiff}",
            )

            // Disk I/O — also kept inside the use block so PNG writes
            // (the second Java2D-touching call site) happen with the
            // GLFW window still open.
            TestUtils.saveDebugImage(rasterBitmap, "$referenceName-raster")
            TestUtils.saveDebugImage(gpuBitmap, "$referenceName-gpu")

            // Diff visual : write the per-pixel diff PNG when ANY of
            // the three signals fires :
            //  1. raster score is within `WARNING_BAND_PERCENT` of its
            //     floor (or strictly below) ;
            //  2. GPU score is within `WARNING_BAND_PERCENT` of its
            //     floor (or strictly below) ;
            //  3. the two backends diverge by more than
            //     `WARNING_BAND_PERCENT` (mirrors the MIGRATION_PLAN
            //     G7 wording — "si raster et GPU divergent de > 2 %").
            // The divergence check catches the case where both
            // backends pass their respective floors but disagree with
            // each other, which is the most interesting failure mode
            // for a cross-validation harness.
            val rasterInWarningBand = rasterCmp.similarity < rasterFloor + WARNING_BAND_PERCENT
            val gpuInWarningBand = gpuCmp.similarity < gpuFloor + WARNING_BAND_PERCENT
            val backendsDiverge = kotlin.math.abs(rasterCmp.similarity - gpuCmp.similarity) > WARNING_BAND_PERCENT
            if (rasterInWarningBand || gpuInWarningBand || backendsDiverge) {
                saveCrossBackendDiff(rasterBitmap, gpuBitmap, referenceName)
            }
            rasterCmp to gpuCmp
        }
        val rasterCmp = result.first
        val gpuCmp = result.second

        val label = gm.javaClass.simpleName
        assertTrue(
            rasterCmp.similarity >= rasterFloor,
            "$label regressed on raster backend : " +
                "${"%.2f".format(rasterCmp.similarity)}% < ${"%.2f".format(rasterFloor)}%. " +
                "See gpu-raster/build/debug-images/$referenceName-{raster,gpu,diff}.png.",
        )
        assertTrue(
            gpuCmp.similarity >= gpuFloor,
            "$label regressed on GPU backend : " +
                "${"%.2f".format(gpuCmp.similarity)}% < ${"%.2f".format(gpuFloor)}%. " +
                "See gpu-raster/build/debug-images/$referenceName-{raster,gpu,diff}.png.",
        )
        return Result(raster = rasterCmp, gpu = gpuCmp)
    }

    /**
     * Save the per-pixel diff PNG under
     * `gpu-raster/build/debug-images/<baseName>-diff.png`. Pixel-diff
     * convention : per-channel `|raster - gpu| * 4` clamped to 255.
     * The alpha channel is forced to 255 so the resulting PNG is
     * opaque and viewable in any image tool.
     *
     * The raster + GPU sources are dumped separately as
     * `<baseName>-raster.png` and `<baseName>-gpu.png` by the harness
     * itself (so a triage workflow opens all three side by side in a
     * file browser). We deliberately do NOT build a `Graphics2D`-based
     * "triptych" image — on macOS, the AWT font / text-rendering
     * machinery `Graphics2D.drawString` pulls in shares the AppKit
     * main thread with GLFW, and instantiating it inside a WebGPU
     * `.use { }` block can deadlock the test in `glfwDestroyWindow`
     * at context close. A plain `setRGB` PNG (which is all this fn
     * does) only touches `BufferedImage` + `ImageIO`, both of which
     * are already initialised by the per-bitmap `saveDebugImage`
     * calls upstream and don't pull in the font subsystem.
     *
     * Exposed `public` so a test with a bespoke render path (multi-
     * sub-test files, custom color spaces) can still produce the same
     * diff artefact without re-inlining the loop.
     */
    public fun saveCrossBackendDiff(
        raster: SkBitmap,
        gpu: SkBitmap,
        baseName: String,
    ) {
        val dir = File("build/debug-images").apply { mkdirs() }
        val diff = pixelDiff(raster, gpu)
        ImageIO.write(diff, "png", File(dir, "$baseName-diff.png"))
    }

    /**
     * Build the per-pixel diff image : output ARGB at each pixel is
     * `(255, R, G, B)` where each channel is `min(255, |a - b| * 4)`.
     * The `*4` amplification matches Skia DM's "diff viewer" convention
     * so a 1-LSB AA edge difference is still visible (4/255 ≈ 1.6 %
     * brightness) without saturating the image to white on every miss.
     *
     * Reads both bitmaps through the colorType-aware [SkBitmap.getPixel]
     * accessor : raster output is `kRGBA_F16Norm` (see [RasterSinkF16])
     * and GPU output is `kRGBA_8888` (see [WebGpuSink.draw]), so the
     * cheaper `pixels` direct-array access would read into an empty
     * array for one of the two sides. [SkBitmap.getPixel] returns a
     * non-premul 8-bit ARGB int for either colorType, which is what the
     * diff amplification expects.
     */
    public fun pixelDiff(a: SkBitmap, b: SkBitmap): BufferedImage {
        require(a.width == b.width && a.height == b.height) {
            "pixelDiff requires same-size bitmaps " +
                "(a=${a.width}x${a.height}, b=${b.width}x${b.height})"
        }
        val w = a.width
        val h = a.height
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val pa = a.getPixel(x, y)
                val pb = b.getPixel(x, y)
                val dR = kotlin.math.min(255, kotlin.math.abs(((pa ushr 16) and 0xFF) - ((pb ushr 16) and 0xFF)) * 4)
                val dG = kotlin.math.min(255, kotlin.math.abs(((pa ushr 8) and 0xFF) - ((pb ushr 8) and 0xFF)) * 4)
                val dB = kotlin.math.min(255, kotlin.math.abs((pa and 0xFF) - (pb and 0xFF)) * 4)
                out[y * w + x] = (0xFF shl 24) or (dR shl 16) or (dG shl 8) or dB
            }
        }
        img.setRGB(0, 0, w, h, out, 0, w)
        return img
    }

    /**
     * Default tag for diagnostic `println` lines. `"beziers"` becomes
     * `"BeziersCross"`, matching the historical `*WebGpu` / `*Cross`
     * tag style.
     */
    private fun defaultLogTag(referenceName: String): String =
        referenceName.replaceFirstChar { it.uppercaseChar() } + "Cross"
}

/**
 * Top-level alias for [CrossBackendHarness.runCrossBackendTest] — the
 * call site reads as
 * `runCrossBackendTest(MyGM(), rasterFloor = 88.0, gpuFloor = 96.9)`,
 * mirroring the `runGpuCrossTest(MyGM(), floor = 96.9)` convention.
 */
public fun runCrossBackendTest(
    gm: GM,
    rasterFloor: Double,
    gpuFloor: Double,
    referenceName: String = gm.name(),
    rasterTolerance: Int = TestUtils.TEXTUAL_GM_TOLERANCE,
    gpuTolerance: Int = TestUtils.TEXTUAL_GM_TOLERANCE,
    logTag: String? = null,
): CrossBackendHarness.Result = CrossBackendHarness.runCrossBackendTest(
    gm = gm,
    rasterFloor = rasterFloor,
    gpuFloor = gpuFloor,
    referenceName = referenceName,
    rasterTolerance = rasterTolerance,
    gpuTolerance = gpuTolerance,
    logTag = logTag ?: (referenceName.replaceFirstChar { it.uppercaseChar() } + "Cross"),
)
