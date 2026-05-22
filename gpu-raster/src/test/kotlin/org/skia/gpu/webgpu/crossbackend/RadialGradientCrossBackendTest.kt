package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.RadialGradientGM

/**
 * O5 batch -- cross-backend test for [RadialGradientGM].
 *
 * 1280 x 1280 single drawRect filled with a 3-stop radial gradient
 * (centre `(640, 640)`, radius 640, stops at `(0.0, 0.35, 1.0)`,
 * colours `(0x7f7f7f7f, 0x7f7f7f7f, 0xb2000000)`) over an opaque black
 * background.
 *
 * Accept-any-result floors (0.0 / 0.0) -- we only care that both
 * backends produce a valid bitmap. CI captures the real similarity
 * deltas in the per-backend properties files.
 */
class RadialGradientCrossBackendTest {

    @Test
    fun `RadialGradientGM renders on raster and GPU backends`() {
        runCrossBackendTest(
            gm = RadialGradientGM(),
            rasterFloor = 0.0,
            gpuFloor = 0.0,
        )
    }
}
