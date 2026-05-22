package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.PathOpsSkbug10155GM

/**
 * Cross-backend test : `PathOpsSkbug10155GM` on raster + GPU.
 *
 * 256 x 256, regression cover for skbug.com/10155. Two SVG-defined
 * cubic-Bezier paths (small numerical-precision shapes near (475, 27))
 * are unioned via [org.skia.pathops.SkOpBuilder] then drawn under a
 * `translate + scale(200/width)` axis-aligned CTM. Three drawPath
 * calls per frame :
 *   - red 0-width AA-stroked outline of path0 ;
 *   - red 0-width AA-stroked outline of path1 ;
 *   - blue 0-width AA-stroked outline of the unioned result, which
 *     should (nearly) overdraw all red except at the intersection.
 *
 * Pure G3.4.3 hairline AA-stroke workout on cubic-flattened paths
 * built via [org.skia.utils.SkParsePath.FromSVGString]. The path-ops
 * union is computed CPU-side -- the GPU only sees the resolved path.
 *
 * The raster vs GPU gap is large : the raster hairline path renders
 * 0-width strokes as filled paths (a stroker artefact, not pathops),
 * while the GPU has a proper AA-hairline emitter. Two distinct floors
 * are explicit in the call below.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`PathOpsSkbug10155Test`, tol=1) : 50.0 % (current
 *    similarity ~67 %, floor low to cover the hairline-as-fill drift) ;
 *  - GPU (`PathOpsSkbug10155WebGpuTest`, tol=8) : 98.94 %.
 */
class PathOpsSkbug10155CrossBackendTest {

    @Test
    fun `PathOpsSkbug10155GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = PathOpsSkbug10155GM(),
            rasterFloor = 50.0,
            gpuFloor = 98.94,
            rasterTolerance = 1,
        )
    }
}
