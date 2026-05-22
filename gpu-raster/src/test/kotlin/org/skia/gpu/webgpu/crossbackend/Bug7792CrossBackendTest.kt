package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Bug7792GM

/**
 * Cross-backend test : `Bug7792GM` on raster + GPU.
 *
 * 16 line-only paths exercising `moveTo` / `close` edge cases for the
 * non-AA fill rasterizer (skbug.com/40039046 reductions). Each path is
 * a small variation of "rect with extra moveTo or duplicate close" --
 * many are multi-contour with degenerate sub-contours. Default `SkPaint`
 * = `kWinding` fill, non-AA -- exercises G3.3b.2b stencil-and-cover
 * multi-contour fill exclusively. Both backends byte-stable at 99.99 %.
 *
 * Floors : GPU 99.94 % / raster 99.94 % (initial run 99.99 % / 99.996 %).
 */
class Bug7792CrossBackendTest {

    @Test
    fun `Bug7792GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Bug7792GM(),
            rasterFloor = 99.94,
            gpuFloor = 99.94,
        )
    }
}
