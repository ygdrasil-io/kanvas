package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.VerticesGM

@Disabled("STUB.DRAW_VERTICES: SkCanvas.drawVertices not implemented")
class VerticesCrossBackendTest {
    @Test
    fun `VerticesGM placeholder`() {
        runCrossBackendTest(VerticesGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
