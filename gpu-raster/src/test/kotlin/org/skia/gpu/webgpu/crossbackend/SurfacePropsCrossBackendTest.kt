package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.SurfacePropsGM

@Disabled("STUB.SURFACE_PROPS: SkSurface.MakeRenderTarget(SkSurfaceProps) overload missing")
class SurfacePropsCrossBackendTest {
    @Test
    fun `SurfacePropsGM placeholder`() {
        runCrossBackendTest(SurfacePropsGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
