package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ShallowGradientLinearGM

/**
 * G4.1 acceptance test — first gradient GM cross-test on GPU.
 *
 * `ShallowGradientLinearGM` paints a single 800x800 drawRect filled
 * with a `SkLinearGradient` (clamp tile mode) from `(0, 0)` to
 * `(800, 800)`, colours `0xFF555555 -> 0xFF444444`. No other features
 * are touched (no clip, no CTM, no stroke, no other shaders), making
 * it the smallest possible GM scope for the G4.1 slice.
 *
 * Compared against `original-888/shallow_gradient_linear.png` at the
 * standard cross-test tolerance (8). Score is recorded in
 * [test-similarity-scores-webgpu.properties].
 */
class ShallowGradientLinearWebGpuTest {

    @Test
    fun `ShallowGradientLinearGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(
            context != null,
            "No WebGPU adapter available — skipping GPU cross-test",
        )

        context!!.use { ctx ->
            val gm = ShallowGradientLinearGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("shallow_gradient_linear")
                ?: error(
                    "original-888/shallow_gradient_linear.png missing from test classpath. " +
                        "Check gpu-raster/build.gradle.kts sourceSets.test.resources.srcDir.",
                )

            val tolerance = TestUtils.TEXTUAL_GM_TOLERANCE
            val cmp = TestUtils.compareBitmapsDetailed(gpuBitmap, reference, tolerance = tolerance)

            println(
                "[ShallowGradientLinearWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )

            TestUtils.saveDebugImage(gpuBitmap, "shallow_gradient_linear-gpu")

            // Floor : the gradient values are very close (0x55 vs 0x44) and
            // a 1-LSB drift would show up as 4-byte difference per channel
            // (the present pass amplifies dark-value drift through the
            // Rec.2020 OETF non-linearity). 90 % keeps us above the G2
            // floor while leaving room for sub-pixel gradient drift the
            // reference's dither baked in.
            val floor = 90.0
            assertTrue(
                cmp.similarity >= floor,
                "ShallowGradientLinearGM on GPU regressed below ratchet floor : " +
                    "${cmp.similarity}% < $floor%. " +
                    "See build/debug-images/shallow_gradient_linear-gpu.png.",
            )
        }
    }
}
