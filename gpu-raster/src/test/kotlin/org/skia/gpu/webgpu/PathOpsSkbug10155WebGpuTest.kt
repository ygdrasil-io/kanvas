package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.PathOpsSkbug10155GM

/**
 * G-suivi (round 18) cross-test : `PathOpsSkbug10155GM` -- port of
 * upstream `gm/pathopsinverse.cpp::pathops_skbug_10155`.
 *
 * 256 x 256, regression cover for skbug.com/10155. Two SVG-defined
 * cubic-Bezier paths (small numerical-precision shapes near (475, 27))
 * are unioned via [org.skia.pathops.SkOpBuilder] then drawn under a
 * `translate + scale(200/width)` axis-aligned CTM. Three drawPath calls
 * per frame :
 *   - red 0-width AA-stroked outline of path0 ;
 *   - red 0-width AA-stroked outline of path1 ;
 *   - blue 0-width AA-stroked outline of the unioned result, which
 *     should (nearly) overdraw all red except at the intersection.
 *
 * Pure G3.4.3 hairline AA-stroke workout on cubic-flattened paths
 * built via [org.skia.utils.SkParsePath.FromSVGString]. The path-ops
 * union is computed CPU-side -- the GPU only sees the resulting
 * resolved path.
 */
class PathOpsSkbug10155WebGpuTest {

    @Test
    fun `PathOpsSkbug10155GM renders close to reference PNG on the GPU backend`() {
        // Landing score 98.99 %. Floor set 0.05 % below for scoring
        // drift headroom.
        runGpuCrossTest(PathOpsSkbug10155GM(), floor = 98.94)
    }
}
