package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BigMatrixGM

/**
 * Cross-backend test : `BigMatrixGM` on raster + GPU.
 *
 * Stresses the rasteriser with an extreme CTM
 * (`Translate(6000, -5000) * Scale(3000, 3000) * Rotate(33 deg)`)
 * then draws three sub-pixel-sized primitives mapped back through
 * `M.invert()` so each shows ~6 px on screen :
 *  - filled 6 px circle,
 *  - filled 6 px square,
 *  - shader-filled 6 px square sampling a 2x2 RGBA bitmap under a
 *    kRepeat / kRepeat (1/1000) local-matrix scale.
 *
 * Exercises the #574 bitmap-shader rotated/skewed unlock : the bitmap
 * shader paint is rendered under an extreme rotated + scaled CTM on
 * a small rect. The 50x50 canvas keeps the read-back cheap and the
 * 6 px subprimitive footprints dominate the score.
 *
 * Floors :
 *  - raster (tol=1) : 90.00 %
 *  - GPU (tol=8)    : 92.88 %
 */
class BigMatrixCrossBackendTest {

    @Test
    fun `BigMatrixGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = BigMatrixGM(),
            rasterFloor = 89.95,
            gpuFloor = 92.85,
            rasterTolerance = 1,
        )
    }
}
