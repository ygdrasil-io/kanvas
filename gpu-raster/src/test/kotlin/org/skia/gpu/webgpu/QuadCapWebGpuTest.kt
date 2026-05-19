package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.QuadCapGM

/**
 * Cross-test : `QuadCapGM` on the GPU backend.
 *
 * Two AA-stroked quadratic Beziers at `strokeWidth = 0` (hairline). The
 * first uses default `kButt_Cap` and is extended by `pi/8` along the
 * tangent at both ends ; the second uses `kRound_Cap` on the original
 * unextended quad. They should reach the same pixel boundary — i.e. a
 * round-cap reaches as far as a butt-cap extended by `pi/8`.
 *
 * G3.4.4 caps coverage : butt vs round cap geometry comparison on hairline
 * strokes — lands on the G3.4.3 `1 / resScale` hairline-synthesis path.
 */
class QuadCapWebGpuTest {

    @Test
    fun `QuadCapGM renders close to reference PNG on the GPU backend`() {
        // Score : 99.80 %. Hairline strokes, tiny 200 x 200 image,
        // butt+tangent-extension vs round-cap geometry match almost
        // pixel-perfect through the G3.4.3 `1 / resScale` synthesis.
        runGpuCrossTest(QuadCapGM(), floor = 99.75, logTag = "QuadCapWebGpu")
    }
}
