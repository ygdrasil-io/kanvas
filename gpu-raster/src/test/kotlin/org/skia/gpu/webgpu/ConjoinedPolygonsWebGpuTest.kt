package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ConjoinedPolygonsGM

/**
 * Cross-test : `ConjoinedPolygonsGM` (`conjoined_polygons`) on the GPU.
 *
 * 400 x 400, single self-touching 7-vertex bow-tie path drawn AA-filled.
 * Regression for crbug.com/1197461 — the triangulator's handling of a
 * polygon that touches itself at a single vertex. Pure axis-aligned
 * drawPath workout (no rotate / skew / shader).
 */
class ConjoinedPolygonsWebGpuTest {

    @Test
    fun `ConjoinedPolygonsGM renders close to reference PNG on the GPU backend`() {
        // Ratchet : observed 99.85 %. Near-pixel-perfect — only a
        // handful of edge pixels drift on the bow-tie's self-touching
        // vertex from the AA coverage convention.
        runGpuCrossTest(ConjoinedPolygonsGM(), floor = 99.80)
    }
}
