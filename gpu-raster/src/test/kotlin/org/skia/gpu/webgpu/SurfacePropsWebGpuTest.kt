package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.SurfacePropsGM

@Disabled("STUB.SURFACE_PROPS: SkSurface.MakeRenderTarget(SkSurfaceProps) overload missing")
class SurfacePropsWebGpuTest {
    @Test
    fun `SurfacePropsGM placeholder`() {
        runGpuCrossTest(SurfacePropsGM(), floor = 0.0)
    }
}
