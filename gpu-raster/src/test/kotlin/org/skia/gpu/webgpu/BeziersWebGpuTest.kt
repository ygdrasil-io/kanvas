package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.BeziersGM

/**
 * Cross-test : `BeziersGM` on the GPU backend.
 *
 * Two panes of 10 random AA-stroked Bezier paths each — top uses
 * `moveTo + 2x quadTo`, bottom uses `moveTo + 2x cubicTo`. Stroke
 * widths span ~1-25 px. Exercises the G3.4.1 SkStroker integration on
 * quad + cubic flatten flows, with per-path random opaque colors.
 */
class BeziersWebGpuTest {

    @Test
    fun `BeziersGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(BeziersGM(), floor = 96.9)
    }
}
