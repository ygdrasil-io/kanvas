package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Crbug788500GM

/**
 * Cross-backend test : `Crbug788500GM` on raster + GPU.
 *
 * 300 x 300 single `drawPath`. Path has a leading `moveTo(0, 0)` then a
 * second `moveTo(245.5, 98.5)` followed by a single cubic to
 * `(260, 75)`. Filled `kEvenOdd` with AA. The leading degenerate
 * `moveTo` exercises the multi-subpath handling on a cubic that doesn't
 * close back to its start.
 *
 * Pure G3.3b.1 cubic flatten + G3.3b.3a AA stencil-and-cover, no
 * shader / image-filter / mask-filter on the path.
 *
 * Floors : raster 99.90 %, GPU 99.92 % (post-ratchet ~0.05 % below
 * measured).
 */
class Crbug788500CrossBackendTest {

    @Test
    fun `Crbug788500GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Crbug788500GM(),
            rasterFloor = 99.90,
            gpuFloor = 99.92,
        )
    }
}
