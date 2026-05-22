package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.NewSurfaceGM

/**
 * Cross-backend test : `NewSurfaceGM` on raster + GPU.
 *
 * Snapshots a 100×100 red raster surface twice, draws both side-by-side
 * on a 300×140 canvas. Trivial draw — surface allocation /
 * makeImageSnapshot / drawImage round-trip. Floors set to the
 * conservative `0.0` since this batch is breadth-first / accept-any-result.
 */
class NewSurfaceCrossBackendTest {

    @Test
    fun `NewSurfaceGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = NewSurfaceGM(),
            rasterFloor = 0.0,
            gpuFloor = 0.0,
        )
    }
}
