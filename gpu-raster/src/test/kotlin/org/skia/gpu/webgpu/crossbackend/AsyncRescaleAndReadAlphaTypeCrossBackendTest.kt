package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.AsyncRescaleAndReadAlphaTypeGM

@Disabled("STUB.ASYNC_RESCALE_AND_READ: requires SkSurface.asyncRescaleAndReadPixels")
class AsyncRescaleAndReadAlphaTypeCrossBackendTest {
    @Test
    fun `AsyncRescaleAndReadAlphaTypeGM placeholder`() {
        runCrossBackendTest(AsyncRescaleAndReadAlphaTypeGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
