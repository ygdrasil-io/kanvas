package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Tiling2GM

/**
 * O5 batch -- cross-backend tests for [Tiling2GM] (bitmap + gradient).
 * 650 x 610 grid of 3x3 tile-mode cells. Accept-any-result.
 */
class Tiling2CrossBackendTest {

    @Test
    fun `Tiling2GM bitmap renders on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Tiling2GM(kind = Tiling2GM.Kind.Bitmap),
            referenceName = "tilemode_bitmap",
            rasterFloor = 0.0,
            gpuFloor = 0.0,
        )
    }

    @Test
    fun `Tiling2GM gradient renders on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Tiling2GM(kind = Tiling2GM.Kind.Gradient),
            referenceName = "tilemode_gradient",
            rasterFloor = 0.0,
            gpuFloor = 0.0,
        )
    }
}
