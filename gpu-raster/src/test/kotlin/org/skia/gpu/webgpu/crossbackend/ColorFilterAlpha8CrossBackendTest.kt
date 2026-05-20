package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ColorFilterAlpha8GM

/**
 * Cross-backend test : `ColorFilterAlpha8GM` on raster + GPU.
 *
 * Re-investigation candidate flagged in round 21 : GPU was 75 %
 * because `drawImage` on `kAlpha_8` didn't route `paint.colorFilter`.
 * H2 (#585) extended `paint.colorFilter` to the 2 bitmap-shader
 * pipelines (`bitmap_shader.wgsl` + `aa_stencil_cover_bitmap_shader.wgsl`)
 * with the same Blend / Matrix payload as the solid-color path.
 *
 * GM (`colorfilteralpha8`, 400 x 400) clears red, allocates a 200 x 200
 * `kAlpha_8` bitmap with alpha 0x88, then `drawImage` with a 4 x 5
 * `Matrix` colorFilter that routes the alpha channel into R/G/B and
 * forces output alpha opaque. The expected output is a solid grey
 * (intensity 0x88) square on red.
 *
 * Floors : GPU 99.95 % / raster 99.95 % (initial run both byte-exact
 * 100.00 % ; ratchet floor 0.05 % below observed). Confirms the
 * round-21 regression note has been closed by H2 (#585).
 */
class ColorFilterAlpha8CrossBackendTest {

    @Test
    fun `ColorFilterAlpha8GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ColorFilterAlpha8GM(),
            rasterFloor = 99.95,
            gpuFloor = 99.95,
        )
    }
}
