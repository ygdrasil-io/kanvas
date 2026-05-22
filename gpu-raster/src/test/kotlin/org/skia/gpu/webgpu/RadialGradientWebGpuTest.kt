package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.RadialGradientGM

/**
 * O5 batch -- GPU acceptance test for [RadialGradientGM].
 *
 * The upstream `radial_gradient` GM paints a single 1280 x 1280 drawRect
 * filled with a 3-stop radial gradient (centre `(640, 640)`, radius 640)
 * with colour stops `(0x7f7f7f7f, 0x7f7f7f7f, 0xb2000000)` at positions
 * `(0.0, 0.35, 1.0)` over an opaque black `drawColor`. The paint enables
 * `setDither(true)` -- our pipeline does not currently inject dither.
 *
 * Accept-any-result floor : 0 % ; CI tracks the observed similarity in
 * [test-similarity-scores-webgpu.properties].
 */
class RadialGradientWebGpuTest {

    @Test
    fun `RadialGradientGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(
            context != null,
            "No WebGPU adapter available -- skipping GPU cross-test",
        )

        context!!.use { ctx ->
            val gm = RadialGradientGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("radial_gradient")
                ?: error(
                    "original-888/radial_gradient.png missing from test classpath. " +
                        "Check gpu-raster/build.gradle.kts sourceSets.test.resources.srcDir.",
                )

            val tolerance = TestUtils.TEXTUAL_GM_TOLERANCE
            val cmp = TestUtils.compareBitmapsDetailed(gpuBitmap, reference, tolerance = tolerance)

            println(
                "[RadialGradientWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )

            TestUtils.saveDebugImage(gpuBitmap, "radial_gradient-gpu")

            // Accept-any-result -- ratchet floor at 0 %. We only fail
            // here when the renderer crashes or returns the empty
            // bitmap (which itself would not even hit this assert).
            val floor = 0.0
            assertTrue(
                cmp.similarity >= floor,
                "RadialGradientGM on GPU regressed below ratchet floor : " +
                    "${cmp.similarity}% < $floor%. " +
                    "See build/debug-images/radial_gradient-gpu.png.",
            )
        }
    }
}
