package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.RepeatedBitmapGM

/**
 * Cross-backend test : `RepeatedBitmapGM` on raster + GPU.
 *
 * 4x4 grid of rotated cells. Each cell paints a grey rounded
 * background rect on top of a 12-px checkerboard, then draws a scaled
 * randPixels.png (8x8 native, upscaled to 128x128) on top. Cell
 * `(i, j)` is rotated by `18 deg * (i + 4 * j)` so every grid step
 * adds 18 deg -- exercises the full rotation range on the bitmap
 * shader pipeline.
 *
 * The bitmap shader is rendered through a composed `translate *
 * rotate * scale * translate` CTM with a `localMatrix` for the
 * `(px, py)` recentre. The GPU bitmap shader pipeline must apply
 * the inverse of `ctm * localMatrix` to each fragment to derive
 * image-pixel coords ; PR #574 widened the gate to accept this
 * composition.
 *
 * Floors (round 28 ratchet, post-#582 unlock) : both backends measure
 * 99.98 % against the upstream reference (matching=331709/331776 GPU,
 * 331706/331776 raster ; 3-pixel cross-backend delta on the AA edges
 * of the rotated bitmap shader cells). The two scores are now within
 * 0.001 pt of each other, well inside the `WARNING_BAND_PERCENT = 2 %`
 * cross-backend divergence gate. Round 21 originally landed this test
 * at GPU 65.42 % / raster 99.98 % (~33 pt gap) ; the bitmap-shader
 * rotated CTM composition fix (PR #582) closed the gap to bit-stability.
 * Floors set 0.05 pt below the observed scores per the round-28 harvest
 * convention (lock in the gain without false-positive flapping on the
 * trailing-byte AA edge).
 */
class RepeatedBitmapCrossBackendTest {

    @Test
    fun `RepeatedBitmapGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = RepeatedBitmapGM(),
            rasterFloor = 99.93,
            gpuFloor = 99.93,
            rasterTolerance = 1,
        )
    }
}
