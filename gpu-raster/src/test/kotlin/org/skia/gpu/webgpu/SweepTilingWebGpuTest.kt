package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.SweepTilingGM

/**
 * Cross-test : `SweepTilingGM` on the GPU backend.
 *
 * 690 x 512 canvas, 3 rows x 4 columns of 160 x 160 drawRects each filled
 * with a `SkSweepGradient`. Rows iterate the tile modes
 * `{kClamp, kRepeat, kMirror}` (G4.3 + G4.3.1 unlocked all three on rect
 * with axis-aligned CTM). Columns iterate four `{startAngle, endAngle}`
 * pairs producing partial / full / overflowing sweeps, so each cell
 * exercises a different t-range that visibly differentiates the tile
 * modes in the unmapped half. Per-row translate is axis-aligned. First
 * cross-test GM exercising kRepeat and kMirror on sweep alongside kClamp.
 */
class SweepTilingWebGpuTest {

    @Test
    fun `SweepTilingGM renders close to reference PNG on the GPU backend`() {
        // Ratchet : observed 100.00 % byte-exact. Floor pinned at
        // 99.95 % to absorb sub-LSB drift if future driver / pipeline
        // changes shift the per-tile-mode lerp arithmetic.
        runGpuCrossTest(SweepTilingGM(), floor = 99.95, logTag = "SweepTilingWebGpu")
    }
}
