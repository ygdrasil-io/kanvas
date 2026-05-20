package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Crbug892988GM

/**
 * Cross-backend test : `Crbug892988GM` on raster + GPU.
 *
 * Reproduces a clip-leak bug : a 1-px AA stroke at half-pixel
 * boundaries (`(11.5, 0.5, 245.5, 245.5)`) followed by an AA
 * `clipRect(12, 1, 244, 244)`, then a `drawRect(12, 1, 244, 244)`
 * with paint `(color = 0xF0FFFFFF, kSrc, AA)`. The non-opaque kSrc
 * paint exercises the AA-edge + kSrc routing : at edge pixels with
 * coverage < 1, kSrc's `(One, Zero)` factor pair would multiply only
 * the source by coverage and discard dst entirely, leaking the
 * background through at every AA edge. The expected behaviour is
 * srcOver-style pre-blend at AA edges (cf. Skia's
 * `GrBlend::handleSrcModeNonOpaque`).
 *
 * Floors : sized after the fix lands. The bug shows up as ~5-10 %
 * GPU divergence concentrated on the 4 AA edges of the clipped rect.
 */
class Crbug892988CrossBackendTest {

    @Test
    fun `Crbug892988GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Crbug892988GM(),
            rasterFloor = 99.0,
            gpuFloor = 99.0,
        )
    }
}
