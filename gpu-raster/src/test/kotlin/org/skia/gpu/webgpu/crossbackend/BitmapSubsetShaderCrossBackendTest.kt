package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BitmapSubsetShaderGM

/**
 * Cross-backend test : `BitmapSubsetShaderGM` on raster + GPU.
 *
 * Loads `images/color_wheel.png`, extracts the left and right halves
 * as subset bitmaps, and tiles each one as an SkBitmapShader with a
 * `scale(0.75) * rotate(30 deg)` local matrix. The two shaders fill
 * the top and bottom halves of a 256x256 canvas respectively.
 *
 * Exercises the #574 bitmap-shader rotated/skewed unlock : two
 * drawRect calls, each with an SkBitmapShader carrying a non-trivial
 * (rotate + scale) localMatrix, in kRepeat / kRepeat tile mode.
 *
 * Floors :
 *  - raster (tol=1) : 97.44 %
 *  - GPU (tol=8)    : 99.99 %
 */
class BitmapSubsetShaderCrossBackendTest {

    @Test
    fun `BitmapSubsetShaderGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = BitmapSubsetShaderGM(),
            rasterFloor = 97.40,
            gpuFloor = 99.94,
            rasterTolerance = 1,
        )
    }
}
