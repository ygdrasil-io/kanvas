package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ThinRectsGM

/**
 * G2.3b acceptance test — first real GM cross-test on GPU.
 *
 * `ThinRectsGM` was chosen because the [audit done in G2.3b planning
 * (cf. MIGRATION_PLAN_GPU_WEBGPU.md)] showed it has **no blockers**
 * against the current SkWebGpuDevice surface : pure fill-style rects,
 * opaque WHITE / GREEN colors, kSrcOver only, AA on, fractional
 * axis-aligned positions, no `clipRect` or `clipPath`, no stroke, no
 * non-axis-aligned CTM. Every feature it touches is implemented in
 * G2.3a or earlier.
 *
 * The test runs the GM through [WebGpuSink], loads the reference PNG
 * (`original-888/thinrects.png`), and asserts the per-channel
 * similarity score is at or above the recorded ratchet in
 * [test-similarity-scores-webgpu.properties](../../../../../test-similarity-scores-webgpu.properties).
 * The plan target is ≥ 90% ; the actual current score is captured in
 * the ratchet file.
 */
class ThinRectsWebGpuTest {

    @Test
    fun `ThinRectsGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(
            context != null,
            "No WebGPU adapter available — skipping GPU cross-test",
        )

        context!!.use { ctx ->
            val gm = ThinRectsGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("thinrects")
                ?: error(
                    "original-888/thinrects.png missing from test classpath. " +
                        "Check gpu-raster/build.gradle.kts sourceSets.test.resources.srcDir.",
                )

            val tolerance = TestUtils.TEXTUAL_GM_TOLERANCE  // 8 — matches the raster ratchet for AA edges
            val cmp = TestUtils.compareBitmapsDetailed(gpuBitmap, reference, tolerance = tolerance)

            println(
                "[ThinRectsWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )

            // Save the GPU output as a debug image so a failing run lets us
            // eyeball the diff with `tools/diff-image` (or any image viewer).
            TestUtils.saveDebugImage(gpuBitmap, "thinrects-gpu")

            // G2.3b initial baseline : assert the floor (a passing run on
            // master). Tighter ratchet handling can be added in a follow-up
            // (mirroring SimilarityTracker on the raster side) once the
            // first cohort of GMs is established.
            val floor = 90.0
            assertTrue(
                cmp.similarity >= floor,
                "ThinRectsGM on GPU regressed below ratchet floor : " +
                    "${cmp.similarity}% < $floor%. See build/debug-images/thinrects-gpu.png.",
            )
        }
    }
}
