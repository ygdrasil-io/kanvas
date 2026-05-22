package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.SaveLayerPreserveLCDTextGM

/**
 * O6 cross-test : `SaveLayerPreserveLCDTextGM`
 * (`savelayerpreservelcdtext`, 620x300) on the GPU backend. Two
 * `saveLayer`s with / without `kPreserveLCDText_SaveLayerFlag`,
 * rendering 36-px text in each layer. Our raster path is grayscale
 * AA only ; the GPU path inherits the same fallback. Both rows
 * therefore render with the same grayscale glyphs vs upstream's
 * LCD-fringed top row.
 */
class SaveLayerPreserveLCDTextWebGpuTest {
    @Test
    fun `SaveLayerPreserveLCDTextGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(SaveLayerPreserveLCDTextGM(), floor = 50.0)
    }
}
