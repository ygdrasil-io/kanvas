package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.SrcRectConstraintGM

@Disabled("STUB.SRC_RECT_CONSTRAINT: SkCanvas.drawImageRect lacks SrcRectConstraint param")
class SrcRectConstraintCrossBackendTest {
    @Test
    fun `SrcRectConstraintGM placeholder`() {
        runCrossBackendTest(SrcRectConstraintGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
