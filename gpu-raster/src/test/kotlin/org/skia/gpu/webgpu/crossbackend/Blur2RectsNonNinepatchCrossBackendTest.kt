package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Blur2RectsNonNinepatchGM

/**
 * Phase MaskFilter-blur cross-backend test : `Blur2RectsNonNinepatchGM`.
 *
 * Two same-winding rects under a sigma = 4.3 blur, drawn at three
 * positions including a partly-off-canvas translate. Exercises :
 *  - sigma 4.3 -> radius 13, so the shape mask grows wider than the
 *    canonical Blur2RectsGM (sigma = 2.3),
 *  - multi-contour same-winding fill rule (union of the two rects),
 *  - partly-clipped shape mask (the (-30, -150) translate pushes part
 *    of the blur extent off the top-left of the canvas).
 */
class Blur2RectsNonNinepatchCrossBackendTest {

    @Test
    fun `Blur2RectsNonNinepatchGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Blur2RectsNonNinepatchGM(),
            rasterFloor = 97.0,
            gpuFloor = 99.0,
            rasterTolerance = 8,
            gpuTolerance = 8,
        )
    }
}
