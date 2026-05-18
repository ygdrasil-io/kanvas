package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ArcToGM

/**
 * Cross-test : `ArcToGM` on the GPU backend.
 *
 * SVG-style `SkPathBuilder.arcTo(rx, ry, xAxisRotate, ArcSize, sweep, x, y)`
 * stressed in three sections : loop section (8 dark-red arcs across two
 * rotations × two oval heights × small-CW/large-CCW), 4-coloured chord
 * permutation (kSmall/kLarge × kCW/kCCW), and zero-length round-cap
 * degenerate arcs. Pure AA stroke + arc flattening workout via G3.4.1
 * SkStroker on cubic-flattened arcs.
 */
class ArcToWebGpuTest {

    @Test
    fun `ArcToGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(ArcToGM(), floor = 96.33)
    }
}
