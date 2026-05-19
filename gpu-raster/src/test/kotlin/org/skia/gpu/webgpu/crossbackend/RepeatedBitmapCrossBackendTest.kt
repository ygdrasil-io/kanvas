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
 * Floors : raster floor is byte-near-exact (`tol=1`, 99.5 %+ measured
 * upstream) ; GPU floor matches the raster within a small AA / pixel-
 * snap delta.
 */
class RepeatedBitmapCrossBackendTest {

    @Test
    fun `RepeatedBitmapGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = RepeatedBitmapGM(),
            rasterFloor = 95.0,
            gpuFloor = 95.0,
            rasterTolerance = 1,
        )
    }
}
