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
 * Floors (round 28 ratchet, post-PR #599 unlock) : both backends now
 * measure 100.00 % byte-exact against the upstream reference (65536 /
 * 65536 matching pixels, maxDiff = 0 on all channels). Round 23
 * originally landed this skip-list entry at GPU 13.98 % / raster
 * 100.00 % -- a ~86 pt drift caused by the kSrc non-opaque + AA-clipRect
 * routing falling through to a coverage-naive blend. PR #599 plumbed
 * the slow-path blend so the GPU now matches raster bit-for-bit. Floors
 * set 0.05 pt below the observed scores per the round-28 harvest
 * convention.
 */
class Crbug892988CrossBackendTest {

    @Test
    fun `Crbug892988GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Crbug892988GM(),
            rasterFloor = 99.95,
            gpuFloor = 99.95,
        )
    }
}
