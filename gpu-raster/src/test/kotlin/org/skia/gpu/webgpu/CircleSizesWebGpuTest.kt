package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.CircleSizesGM

/**
 * Cross-test : `CircleSizesGM` (`circle_sizes`) on the GPU backend.
 *
 * 128 x 128, 4 x 4 grid of AA circles with radii 1..16. Centres at
 * (14 + 32 i, 14 + 32 j). Pure axis-aligned drawCircle workout —
 * regression fixture from crbug.com/772953, hits every radius bucket
 * for the AA rasterizer with the smallest being a 1-px disc.
 */
class CircleSizesWebGpuTest {

    @Test
    fun `CircleSizesGM renders close to reference PNG on the GPU backend`() {
        // Ratchet : observed 96.94 %. Residual drift on AA circle
        // edges across the 1..16 px radius bucket — the 1-px disc
        // and 2-px circles in particular drift sub-LSB vs the
        // raster's analytic small-circle path.
        runGpuCrossTest(CircleSizesGM(), floor = 96.89)
    }
}
