package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.foundation.SkTileMode
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ClippedBitmapShadersGM

/**
 * Cross-backend test : `ClippedBitmapShadersGM` (3 default-sampling
 * variants on raster + GPU).
 *
 * 300 x 300 canvas, 3 x 3 grid (centre cell omitted) of clipped
 * rectangles filled by a `SkBitmap.makeShader`-backed paint. The 2 x 2
 * source bitmap (R / G / black / B) is scaled 8x via the shader's local
 * matrix (`scale(8).postTranslate(150, 150)`). Each cell `save() ;
 * clipRect(rect) ; drawRect(rect, paint) ; restore()` exercises the
 * axis-aligned bitmap-shader fast path (G5.2 dispatch) on the GPU and
 * the equivalent CPU sampler on raster.
 *
 * Only the 3 nearest-sampling variants (Clamp / Repeat / Mirror) are
 * cross-tested here. The `-hq` (Mitchell bicubic) variants are covered
 * by `ClippedBitmapShadersTest` and the WebGPU-only
 * `ClippedBitmapShadersWebGpuTest`; they should be promoted here only
 * after both backend floors are verified together.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`ClippedBitmapShadersTest`, tol=1) : 99.0 % per variant ;
 *  - GPU (`ClippedBitmapShadersWebGpuTest`, tol=8) : 99.95 % per variant
 *    (all three byte-exact upstream, floor absorbs scoring drift).
 */
class ClippedBitmapShadersCrossBackendTest {

    @Test
    fun `ClippedBitmapShadersGM kClamp matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ClippedBitmapShadersGM(SkTileMode.kClamp),
            rasterFloor = 99.0,
            gpuFloor = 99.95,
            rasterTolerance = 1,
        )
    }

    @Test
    fun `ClippedBitmapShadersGM kRepeat matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ClippedBitmapShadersGM(SkTileMode.kRepeat),
            rasterFloor = 99.0,
            gpuFloor = 99.95,
            rasterTolerance = 1,
        )
    }

    @Test
    fun `ClippedBitmapShadersGM kMirror matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ClippedBitmapShadersGM(SkTileMode.kMirror),
            rasterFloor = 99.0,
            gpuFloor = 99.95,
            rasterTolerance = 1,
        )
    }
}
