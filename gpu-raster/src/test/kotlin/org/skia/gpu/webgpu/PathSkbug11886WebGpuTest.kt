package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.PathSkbug11886GM

/**
 * G-suivi (round 18) cross-test : `PathSkbug11886GM` -- port of upstream
 * `gm/pathfill.cpp` `DEF_SIMPLE_GM(path_skbug_11886, ...)`.
 *
 * 256 x 256 canvas, single AA-filled cubic-Bezier path starting at
 * `(0, 770)` (well below the canvas) with control points pulling
 * sharply upward through y in {1, -750, -746}. Tests numerical
 * stability at large path coordinates (y jumps by ~1500 across a single
 * cubic).
 *
 * Pure G3.3b.3a stencil-and-cover AA fill on a single-contour open
 * cubic with extreme coord magnitude. The reference is the rasterised
 * result of upstream's CPU rasterizer ; drift here would indicate
 * cubic-flattening precision divergence.
 */
class PathSkbug11886WebGpuTest {

    @Test
    fun `PathSkbug11886GM renders close to reference PNG on the GPU backend`() {
        // Landing score 99.61 %. Floor set 0.05 % below for scoring
        // drift headroom.
        runGpuCrossTest(PathSkbug11886GM(), floor = 99.56)
    }
}
