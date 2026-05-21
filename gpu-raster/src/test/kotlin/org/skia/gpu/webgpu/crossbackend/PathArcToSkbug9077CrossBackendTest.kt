package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.PathArcToSkbug9077GM

/**
 * Cross-backend test : `PathArcToSkbug9077GM` on raster + GPU.
 *
 * Regression from skia bug 9077 — `SkPath::arcTo` near a degenerate
 * configuration. The expected behaviour is to terminate cleanly with
 * no infinite-loop cubic emission. Cross-backend test ensures both
 * implementations agree on the (small, but specific) pixel output.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`PathArcToSkbug9077Test`, tol=1) : 90.0 %
 *  - GPU (`PathArcToSkbug9077WebGpuTest`, tol=8) : 98.95 %
 */
class PathArcToSkbug9077CrossBackendTest {

    @Test
    fun `PathArcToSkbug9077GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = PathArcToSkbug9077GM(),
            rasterFloor = 90.0,
            gpuFloor = 98.95,
            rasterTolerance = 1,
        )
    }
}
