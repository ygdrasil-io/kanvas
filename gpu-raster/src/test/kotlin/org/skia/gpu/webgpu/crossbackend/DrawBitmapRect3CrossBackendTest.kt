package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.DrawBitmapRect3

/**
 * Cross-backend test : `DrawBitmapRect3` on raster + GPU.
 *
 * 640 x 480 canvas, single `drawImageRect` of a 3 x 3 colour-stamped
 * bitmap with `srcR = (0.5, 0.5, 2.5, 2.5)` into `dstR = (100, 100,
 * 300, 200)`, default nearest sampling. With nearest sampling the
 * partial src rect produces a 2 x 2 grid of coloured stripes scaled
 * into the 200 x 100 dst rect. Pure `drawImageRect` routing (G5.1) --
 * no shader on paint, just the direct image -> rect path.
 *
 * Reference is rendered into the DM Rec.2020 reference colorspace, so
 * the source bitmap is xformed once at the top of `drawImageRect` and
 * sampled from the converted buffer.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`DrawBitmapRect3Test`, tol=1) : 95.0 % ;
 *  - GPU (`DrawBitmapRect3WebGpuTest`, tol=8) : 99.95 % (byte-exact ;
 *    nearest sampling, kStrict constraint on a small 3 x 3 src).
 */
class DrawBitmapRect3CrossBackendTest {

    @Test
    fun `DrawBitmapRect3 matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = DrawBitmapRect3(),
            rasterFloor = 95.0,
            gpuFloor = 99.95,
            rasterTolerance = 1,
        )
    }
}
