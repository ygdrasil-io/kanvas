package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.MakeRasterImageGM

/**
 * G5.4 cross-test : `makeRasterImage` -- 128 x 128 canvas, a single
 * `drawImage(image, 0, 0)` of the decoded `color_wheel.png` resource
 * (no explicit sampling, no paint). Exercises the
 * [org.skia.core.SkCanvas.drawImage] point-positioned overload routing
 * through the bitmap-shader pipeline (G5.1 / G5.4) end-to-end.
 *
 * In-scope after G5.4 :
 *  - `drawImage(image, x, y)` -> `drawImageRect(src=(0,0,w,h),
 *    dst=(x,y,x+w,y+h), sampling=Default(kNearest/kNone), constraint=kFast)`,
 *  - axis-aligned CTM (no explicit transforms),
 *  - default sampling (kNearest -- the routing default in [SkCanvas]).
 */
class MakeRasterImageWebGpuTest {

    @Test
    fun `MakeRasterImageGM renders close to reference PNG on the GPU backend`() {
        // Landing score : 100.00 % (byte-exact ; nearest sampling, single
        // 1:1 axis-aligned blit so no filtering or sub-pixel rounding).
        runGpuCrossTest(MakeRasterImageGM(), floor = 99.95)
    }
}
