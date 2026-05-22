package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.TilemodesAlphaGM

/**
 * Cross-backend test : `TilemodesAlphaGM` on raster + GPU.
 *
 * 512 x 512 canvas, 4 x 4 grid of 126 x 126 axis-aligned `drawRect`
 * cells each filled by an image shader sampling the `mandrill_64.png`
 * resource. Rows iterate the Y tile mode, columns iterate the X tile
 * mode over `{kClamp, kRepeat, kMirror, kDecal}`. Each cell uses a
 * translate-only local matrix and a paint colour `(0, 0, 0, 0.5)` --
 * the paint alpha modulates the shader output (crbug.com/957275
 * regression).
 *
 * All in-scope after G5.2 :
 *  - axis-aligned CTM (identity),
 *  - axis-aligned local matrix (translate-only),
 *  - drawRect path (rect routing fires the bitmap-shader gate),
 *  - all 4 tile modes including kDecal (G5.1.1).
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`TilemodesAlphaTest`, tol=2) : 70.0 % (paint-alpha float
 *    quantisation + premul F16 vs upstream unpremul Rec.2020 round-
 *    trip gap) ;
 *  - GPU (`TilemodesAlphaWebGpuTest`, tol=8) : 99.95 % (byte-exact
 *    across all 16 tile-mode cells).
 */
class TilemodesAlphaCrossBackendTest {

    @Test
    fun `TilemodesAlphaGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = TilemodesAlphaGM(),
            rasterFloor = 70.0,
            gpuFloor = 99.95,
            rasterTolerance = 2,
        )
    }
}
