package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.GradientsNoTextureGM

/**
 * Cross-backend test : `GradientsNoTextureGM` (dither variant) on
 * raster + GPU.
 *
 * 640 x 615 canvas, 4 x 5 x 2 grid of 50 x 50 axis-aligned drawRects.
 * The grid iterates : 4 colour-stop configurations (1 / 2 / 3 / 4
 * stops from `{red, green, blue, white}`), 5 gradient kinds (linear,
 * radial, sweep, 2pt-radial = conical focal-inside, 2pt-conical =
 * conical focal-outside), 2 alpha values (`0xFF`, `0x40`).
 *
 * All 5 gradient kinds are in scope on the GPU :
 *  - linear (G4.0), radial (G4.1), sweep (G4.3) since the kClamp
 *    landing slices ;
 *  - conical focal-inside since G4.4.1 ;
 *  - conical focal-outside since G4.4.5.
 *
 * Only the `dither = true` variant is cross-tested because the raster
 * suite only has a hand-written test for that variant
 * (`GradientsNoTextureTest`). The `dither = false` variant remains
 * GPU-only (dither is a no-op in our 16-bpc pipeline, so both render
 * identically through us, but cross-backend reuses the established
 * raster floor).
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`GradientsNoTextureTest`, tol=1) : 81.1 % ;
 *  - GPU (`GradientsNoTextureWebGpuTest`, tol=8) : 88.25 % (drift on
 *    focal-outside cone-boundary edges + alpha-modulated premul
 *    rounding).
 */
class GradientsNoTextureCrossBackendTest {

    @Test
    fun `GradientsNoTextureGM dither matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = GradientsNoTextureGM(dither = true),
            rasterFloor = 81.1,
            gpuFloor = 88.25,
            rasterTolerance = 1,
        )
    }
}
