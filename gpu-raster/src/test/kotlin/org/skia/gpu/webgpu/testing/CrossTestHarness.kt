package org.skia.gpu.webgpu.testing

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.skia.foundation.SkBitmap
import org.skia.gpu.webgpu.WebGpuContext
import org.skia.gpu.webgpu.WebGpuSink
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils
import org.skia.tests.GM

/**
 * Shared boilerplate for `*WebGpuTest` cross-validation GMs.
 *
 * Every cross-test on the GPU backend used to inline the same 25-line
 * recipe :
 *
 *   1. `WebGpuContext.createOrNull()` + `Assumptions.assumeTrue` (skip
 *      cleanly when no adapter is available).
 *   2. `WebGpuSink.draw(ctx, gm)` to render through the WebGPU device.
 *   3. `TestUtils.loadReferenceBitmap(gm.name())` for the reference PNG.
 *   4. `TestUtils.compareBitmapsDetailed(..., tolerance = TEXTUAL_GM_TOLERANCE)`.
 *   5. A diagnostic `println` line of the form
 *      `[<TestName>] similarity=...%, matching=N/M, maxDiff=...`.
 *   6. `TestUtils.saveDebugImage(bitmap, "<gm-name>-gpu")`.
 *   7. `assertTrue(cmp.similarity >= floor, "<TestName>GM regressed ...")`.
 *
 * G7.1 collapses (1)-(7) into [runGpuCrossTest]. The only per-test
 * inputs that actually differ are :
 *  - the [GM] instance,
 *  - the similarity [floor],
 *  - optionally an override for the reference name (defaults to
 *    `gm.name()`) and the per-channel [tolerance] (defaults to
 *    [TestUtils.TEXTUAL_GM_TOLERANCE]).
 *
 * ## Migration pattern
 *
 * A pre-G7.1 test looked like :
 *
 * ```kotlin
 * class BeziersWebGpuTest {
 *     @Test
 *     fun `BeziersGM renders close to reference PNG on the GPU backend`() {
 *         val context = WebGpuContext.createOrNull()
 *         Assumptions.assumeTrue(context != null, "No WebGPU adapter")
 *         context!!.use { ctx ->
 *             val gm = BeziersGM()
 *             val gpuBitmap = WebGpuSink.draw(ctx, gm)
 *             val reference = TestUtils.loadReferenceBitmap("beziers")
 *                 ?: error("original-888/beziers.png missing")
 *             val cmp = TestUtils.compareBitmapsDetailed(
 *                 gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
 *             )
 *             println("[BeziersWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, ...")
 *             TestUtils.saveDebugImage(gpuBitmap, "beziers-gpu")
 *             val floor = 96.9
 *             assertTrue(cmp.similarity >= floor, "BeziersGM regressed ...")
 *         }
 *     }
 * }
 * ```
 *
 * After G7.1 the same test reduces to :
 *
 * ```kotlin
 * class BeziersWebGpuTest {
 *     @Test
 *     fun `BeziersGM renders close to reference PNG on the GPU backend`() {
 *         runGpuCrossTest(BeziersGM(), floor = 96.9)
 *     }
 * }
 * ```
 *
 * The class-level KDoc — which still carries the GM-specific rationale
 * (what's exercised, why the floor sits where it does) — stays on the
 * test class itself ; the harness only owns the mechanical plumbing.
 *
 * Existing tests using the pre-G7.1 inline pattern keep working — no
 * deprecation, no churn forced on the in-flight WebGPU GM harvest.
 * New GMs and migrations should prefer [runGpuCrossTest].
 */
public object CrossTestHarness {

    /**
     * Run [gm] through the WebGPU device, compare the readback against
     * `original-888/<referenceName>.png`, and assert that the similarity
     * score stays at or above [floor].
     *
     * Skips (via `Assumptions.assumeTrue`) when no WebGPU adapter is
     * available — same skip semantics as every pre-G7.1 test.
     *
     * @param gm the [GM] to render.
     * @param floor the similarity ratchet floor (0..100). A test fails
     *   if the GPU output similarity drops strictly below this.
     * @param referenceName name (without `.png`) of the reference image
     *   in `kanvas-legacy/src/test/resources/original-888/`. Defaults to
     *   [GM.name] — every existing GM port matches this convention.
     * @param tolerance per-channel byte tolerance for the bitmap
     *   comparison. Defaults to [TestUtils.TEXTUAL_GM_TOLERANCE].
     * @param debugImageSuffix suffix appended to [referenceName] when
     *   saving the debug image. Defaults to `"-gpu"`.
     * @param logTag the bracketed prefix on the diagnostic `println`
     *   line. Defaults to a tag derived from [referenceName]
     *   (e.g. `"AddarcWebGpu"` for `referenceName = "addarc"`) ;
     *   override when a test wants its old log tag preserved.
     * @return the [BitmapComparison] from the underlying call — useful
     *   when a test wants to assert additional invariants beyond the
     *   floor (e.g. max-diff bounds).
     */
    public fun runGpuCrossTest(
        gm: GM,
        floor: Double,
        referenceName: String = gm.name(),
        tolerance: Int = TestUtils.TEXTUAL_GM_TOLERANCE,
        debugImageSuffix: String = "-gpu",
        logTag: String = defaultLogTag(referenceName),
    ): BitmapComparison {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        return context!!.use { ctx ->
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val cmp = compareAndReport(
                bitmap = gpuBitmap,
                referenceName = referenceName,
                tolerance = tolerance,
                debugImageName = "$referenceName$debugImageSuffix",
                logTag = logTag,
            )
            assertFloor(gm.javaClass.simpleName, cmp, floor)
            cmp
        }
    }

    /**
     * Render [gm] through [WebGpuSink], return the resulting bitmap
     * without any reference comparison or floor assertion. Useful for
     * cross-tests that compare the GPU output to a freshly-rendered CPU
     * output (raster-vs-GPU parity) rather than to a frozen PNG.
     *
     * Like [runGpuCrossTest], skips when no WebGPU adapter is available.
     */
    public fun renderGpu(gm: GM): SkBitmap {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        return context!!.use { ctx ->
            WebGpuSink.draw(ctx, gm)
        }
    }

    /**
     * Diagnostic + debug-image side effects shared by both the canonical
     * [runGpuCrossTest] path and any custom in-test invocations. Pulled
     * out so tests with a bespoke render path (multi-sub-test files,
     * non-standard color spaces, etc.) can still get the consistent log
     * format and PNG dump without re-inlining the boilerplate.
     */
    public fun compareAndReport(
        bitmap: SkBitmap,
        referenceName: String,
        tolerance: Int = TestUtils.TEXTUAL_GM_TOLERANCE,
        debugImageName: String = "$referenceName-gpu",
        logTag: String = defaultLogTag(referenceName),
    ): BitmapComparison {
        val reference = TestUtils.loadReferenceBitmap(referenceName)
            ?: error("original-888/$referenceName.png missing")
        val cmp = TestUtils.compareBitmapsDetailed(bitmap, reference, tolerance = tolerance)
        println(
            "[$logTag] similarity=${"%.2f".format(cmp.similarity)}%, " +
                "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                "maxDiff=${cmp.maxChannelDiff}",
        )
        TestUtils.saveDebugImage(bitmap, debugImageName)
        return cmp
    }

    /**
     * Assert that [cmp].similarity stays at or above [floor]. Mirrors
     * the pre-G7.1 inline assertion message format so failure messages
     * stay grep-compatible with the historical logs.
     */
    public fun assertFloor(label: String, cmp: BitmapComparison, floor: Double) {
        assertTrue(
            cmp.similarity >= floor,
            "$label regressed below floor : ${cmp.similarity}% < $floor%.",
        )
    }

    /**
     * Default tag for the diagnostic `println` line. `"addarc"` becomes
     * `"AddarcWebGpu"`, matching the historical inline format.
     */
    private fun defaultLogTag(referenceName: String): String =
        referenceName.replaceFirstChar { it.uppercaseChar() } + "WebGpu"
}

/**
 * Top-level alias for [CrossTestHarness.runGpuCrossTest] — the call
 * site reads as `runGpuCrossTest(MyGM(), floor = 97.5)` which is the
 * spelling used by all migrated tests.
 */
public fun runGpuCrossTest(
    gm: GM,
    floor: Double,
    referenceName: String = gm.name(),
    tolerance: Int = TestUtils.TEXTUAL_GM_TOLERANCE,
    debugImageSuffix: String = "-gpu",
    logTag: String? = null,
): BitmapComparison = CrossTestHarness.runGpuCrossTest(
    gm = gm,
    floor = floor,
    referenceName = referenceName,
    tolerance = tolerance,
    debugImageSuffix = debugImageSuffix,
    logTag = logTag ?: (referenceName.replaceFirstChar { it.uppercaseChar() } + "WebGpu"),
)
