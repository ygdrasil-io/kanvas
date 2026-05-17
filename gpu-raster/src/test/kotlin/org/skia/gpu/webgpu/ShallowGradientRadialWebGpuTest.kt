package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ShallowGradientRadialGM

/**
 * G4.2 acceptance test -- first radial-gradient GM cross-test on GPU.
 *
 * `ShallowGradientRadialGM` paints a single 800x800 drawRect filled
 * with a `SkRadialGradient` (kClamp tile mode) centred at `(400, 400)`,
 * radius `400`, colours `0xFF555555 -> 0xFF444444`. Sibling shape to
 * `ShallowGradientLinearGM` (the G4.1 ratchet GM) -- only the gradient
 * type differs, so it's the smallest possible scope for the G4.2 slice.
 *
 * Compared against `original-888/shallow_gradient_radial.png` at the
 * standard cross-test tolerance (8). Score is recorded in
 * [test-similarity-scores-webgpu.properties].
 */
class ShallowGradientRadialWebGpuTest {

    @Test
    fun `ShallowGradientRadialGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(
            context != null,
            "No WebGPU adapter available -- skipping GPU cross-test",
        )

        context!!.use { ctx ->
            val gm = ShallowGradientRadialGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("shallow_gradient_radial")
                ?: error(
                    "original-888/shallow_gradient_radial.png missing from test classpath. " +
                        "Check gpu-raster/build.gradle.kts sourceSets.test.resources.srcDir.",
                )

            val tolerance = TestUtils.TEXTUAL_GM_TOLERANCE
            val cmp = TestUtils.compareBitmapsDetailed(gpuBitmap, reference, tolerance = tolerance)

            println(
                "[ShallowGradientRadialWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )

            TestUtils.saveDebugImage(gpuBitmap, "shallow_gradient_radial-gpu")

            // Floor : same shape as the linear ratchet GM, the gradient
            // values are very close (0x55 vs 0x44) so a 1-LSB drift in
            // the present pass shows up as ~4 bytes per channel. 90 %
            // keeps us above the G2 floor while leaving room for sub-
            // pixel gradient drift that the reference's dither baked in.
            val floor = 90.0
            assertTrue(
                cmp.similarity >= floor,
                "ShallowGradientRadialGM on GPU regressed below ratchet floor : " +
                    "${cmp.similarity}% < $floor%. " +
                    "See build/debug-images/shallow_gradient_radial-gpu.png.",
            )
        }
    }
}
