package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.TilemodesAlphaGM

/**
 * G-suivi (round 14) cross-test : `TilemodesAlphaGM` -- 512 x 512
 * canvas, 4 x 4 grid of 126 x 126 axis-aligned `drawRect` cells each
 * filled by an image shader sampling the `mandrill_64.png` resource.
 * Rows iterate the Y tile mode, columns iterate the X tile mode over
 * `{kClamp, kRepeat, kMirror, kDecal}`. Each cell uses a translate-
 * only local matrix and a paint colour `(0, 0, 0, 0.5)` -- the paint
 * alpha modulates the shader output (crbug.com/957275 regression).
 *
 * All in-scope after G5.2 :
 *  - axis-aligned CTM (identity),
 *  - axis-aligned local matrix (translate-only),
 *  - drawRect path (rect routing fires the bitmap-shader gate),
 *  - all 4 tile modes including kDecal (G5.1.1).
 */
class TilemodesAlphaWebGpuTest {

    @Test
    fun `TilemodesAlphaGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = TilemodesAlphaGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("tilemodes_alpha")
                ?: error("original-888/tilemodes_alpha.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[TilemodesAlphaWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "tilemodes_alpha-gpu")
            // Landing score 100.00 % -- byte-exact match across all
            // 16 cells of the tile-mode matrix (crbug.com/957275
            // regression sanity).
            val floor = 99.95
            assertTrue(
                cmp.similarity >= floor,
                "TilemodesAlphaGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
