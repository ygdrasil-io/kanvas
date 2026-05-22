package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ArcOfZorroGM

/**
 * Cross-backend test : `ArcOfZorroGM` on raster + GPU.
 *
 * 4-arc kStroke_Style "Z" pattern reduced from a Chromium stroker
 * arc-corner regression. Open-arc stroke wraps + caps + joins, all
 * routed through SkStroker -> AA fill.
 *
 * Floors : GPU 99.68 % / raster 99.68 % (initial run 99.73 % / 99.73 %).
 */
class ArcOfZorroCrossBackendTest {

    @Test
    fun `ArcOfZorroGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ArcOfZorroGM(),
            rasterFloor = 99.68,
            gpuFloor = 99.68,
        )
    }
}
