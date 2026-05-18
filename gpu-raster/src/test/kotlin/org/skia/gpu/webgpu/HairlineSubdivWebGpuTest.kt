package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.HairlineSubdivGM

/**
 * Cross-test : `HairlineSubdivGM` on the GPU backend.
 *
 * 512 × 256 canvas, 4 increasingly-large AA hairline (strokeWidth = 1)
 * quadratic Bezier strokes, overlaid at progressively-shifted origins.
 * Each draw exercises a different subdivision count in the Bezier
 * flattener routed through G3.4.3 hairline synthesis + G3.4.1 SkStroker.
 */
class HairlineSubdivWebGpuTest {

    @Test
    fun `HairlineSubdivGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(HairlineSubdivGM(), floor = 97.45)
    }
}
