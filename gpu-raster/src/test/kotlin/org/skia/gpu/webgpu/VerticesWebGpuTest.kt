package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.VerticesGM

@Disabled("STUB.DRAW_VERTICES: SkCanvas.drawVertices not implemented")
class VerticesWebGpuTest {
    @Test
    fun `VerticesGM placeholder`() {
        runGpuCrossTest(VerticesGM(), floor = 0.0)
    }
}
