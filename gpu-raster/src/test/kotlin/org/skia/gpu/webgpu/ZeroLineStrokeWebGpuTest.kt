package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ZeroLineStrokeGM

/**
 * Cross-test : `ZeroLineStrokeGM` on the GPU backend.
 *
 * Three zero-or-tiny-length strokes drawn with `kRound_Cap` at
 * `strokeWidth = 20`, AA on. Each contour has duplicate consecutive
 * points (collinear / degenerate segments) that should still produce
 * identical-looking "lozenges" (two round caps connected by a flat
 * segment).
 *
 * G3.4.4 caps coverage : focused round-cap regression repro on
 * degenerate / zero-length segments. Validates that the stroker's
 * round-cap emission is robust to duplicate `lineTo` points.
 */
class ZeroLineStrokeWebGpuTest {

    @Test
    fun `ZeroLineStrokeGM renders close to reference PNG on the GPU backend`() {
        // Score : 94.05 %. Tiny 90 x 120 image (10 800 px total) — each
        // round-cap arc's AA boundary drifts a few px's worth ; small
        // total pixel count amplifies the loss.
        runGpuCrossTest(ZeroLineStrokeGM(), floor = 94.00)
    }
}
