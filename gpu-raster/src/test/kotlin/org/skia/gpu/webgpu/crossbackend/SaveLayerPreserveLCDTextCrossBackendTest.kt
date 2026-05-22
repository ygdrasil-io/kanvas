package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.SaveLayerPreserveLCDTextGM

/**
 * O6 cross-backend : `SaveLayerPreserveLCDTextGM`
 * (`savelayerpreservelcdtext`, 620x300) on raster + GPU.
 */
class SaveLayerPreserveLCDTextCrossBackendTest {
    @Test
    fun `SaveLayerPreserveLCDTextGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = SaveLayerPreserveLCDTextGM(),
            rasterFloor = 50.0,
            gpuFloor = 50.0,
        )
    }
}
