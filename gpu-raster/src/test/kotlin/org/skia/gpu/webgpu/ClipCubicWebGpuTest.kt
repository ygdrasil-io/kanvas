package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ClipCubicGM

/**
 * Cross-test : `ClipCubicGM` (`clipcubic`) on the GPU backend.
 *
 * 400 x 410, two pairs of vertical / horizontal cubic curves, each
 * drawn unclipped + clipped to a centred 100 x 120 rect on a 565-
 * quantised purple background. The horizontal variant is built by
 * 90-degree-rotating the vertical one's path geometry via
 * `SkPath.makeTransform` — the CTM stays pure translate (axis-aligned).
 * Stresses clip-edge arithmetic on a cubic curve fill + outline.
 */
class ClipCubicWebGpuTest {

    @Test
    fun `ClipCubicGM renders close to reference PNG on the GPU backend`() {
        // Ratchet : observed 99.32 %. Residual drift on the AA cubic
        // edges where the clipRect crosses the curve (clip-edge
        // arithmetic on a smoothly-shaped fill).
        runGpuCrossTest(ClipCubicGM(), floor = 99.27)
    }
}
