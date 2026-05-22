package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.AsyncRescaleAndReadAlphaTypeGM

@Disabled("STUB.ASYNC_RESCALE_AND_READ: requires SkSurface.asyncRescaleAndReadPixels")
class AsyncRescaleAndReadAlphaTypeWebGpuTest {
    @Test
    fun `AsyncRescaleAndReadAlphaTypeGM placeholder`() {
        runGpuCrossTest(AsyncRescaleAndReadAlphaTypeGM(), floor = 0.0)
    }
}
