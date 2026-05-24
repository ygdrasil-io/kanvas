package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.SurfacePropsGM

class SurfacePropsCrossBackendTest {
    @Test
    fun `SurfacePropsGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(SurfacePropsGM(), rasterFloor = 95.0, gpuFloor = 95.0)
    }
}
