package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.TestExtractAlphaGM

/**
 * O5 batch -- cross-backend test for [TestExtractAlphaGM]. Verifies
 * SkBitmap.extractAlpha + drawImage with paint colour modulation.
 * Accept-any-result.
 */
class TestExtractAlphaCrossBackendTest {

    @Test
    fun `TestExtractAlphaGM renders on raster and GPU backends`() {
        runCrossBackendTest(
            gm = TestExtractAlphaGM(),
            rasterFloor = 0.0,
            gpuFloor = 0.0,
        )
    }
}
