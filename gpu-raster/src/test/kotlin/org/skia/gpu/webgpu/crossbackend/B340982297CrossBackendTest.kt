package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.B340982297GM

/**
 * Cross-backend test : `B340982297GM` on raster + GPU.
 *
 * Two AA-filled self-intersecting line polygons (Skia upstream
 * `gm/strokes.cpp::b_340982297`, 80 x 50). Originally exposed a
 * triangulator bug in Skia where the close-after-crossing edge
 * produced an inverted winding contribution. In `:gpu-raster` the
 * equivalent issue surfaced as a self-intersecting polygon
 * incorrectly routed through the convex AA fast path
 * (`aa_polygon.wgsl`) -- the consecutive-triple turn-sign check in
 * `isPolygonConvex` accepted the figure-8 because every triple turned
 * the same way. The convex fast path uses min-of-signed-perp-distance
 * which models the polygon as the intersection of edge-line
 * half-planes ; that intersection is empty for a figure-8, so the
 * GPU output was essentially blank (52.93 % vs raster 96.23 %).
 *
 * Fix (J6) -- `isPolygonConvex` now additionally requires polygon
 * simplicity (no two non-adjacent edges cross). Self-intersecting
 * shapes fall through to the stencil-and-cover route, where the
 * stencil winding count handles the figure-8 correctly via kWinding.
 *
 * Post-fix observed scores : raster 96.23 % / GPU 96.58 %
 * (cross-backend drift < 0.5 %, mirrors the round-25 plan note).
 * Floors ratcheted 0.5 % below the observed run.
 */
class B340982297CrossBackendTest {

    @Test
    fun `B340982297GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = B340982297GM(),
            rasterFloor = 95.7,
            gpuFloor = 96.0,
        )
    }
}
