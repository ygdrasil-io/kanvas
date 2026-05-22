package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.MakeRasterImageGM

/**
 * Cross-backend test : `MakeRasterImageGM` on raster + GPU.
 *
 * 128 x 128 canvas, single `drawImage(image, 0, 0)` of the decoded
 * `color_wheel.png` resource (no explicit sampling, no paint).
 * Exercises the [org.skia.core.SkCanvas.drawImage] point-positioned
 * overload routing through the bitmap-shader pipeline (G5.1 / G5.4)
 * end-to-end. On raster, `makeRasterImage` is a no-op : the decoded
 * pixels land 1:1, modulo the sRGB -> Rec.2020 reference colorspace
 * transform.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`MakeRasterImageTest`, tol=2) : 95.0 % ;
 *  - GPU (`MakeRasterImageWebGpuTest`, tol=8) : 99.95 % (byte-exact ;
 *    nearest sampling, single 1:1 axis-aligned blit).
 */
class MakeRasterImageCrossBackendTest {

    @Test
    fun `MakeRasterImageGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = MakeRasterImageGM(),
            rasterFloor = 95.0,
            gpuFloor = 99.95,
            rasterTolerance = 2,
        )
    }
}
