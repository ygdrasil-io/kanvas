package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.AsyncRescaleAndReadNoBleedGM

@Disabled("STUB.ASYNC_RESCALE_AND_READ: requires SkSurface.asyncRescaleAndReadPixels")
class AsyncRescaleAndReadNoBleedWebGpuTest {
    @Test
    fun `AsyncRescaleAndReadNoBleedGM placeholder`() {
        runGpuCrossTest(AsyncRescaleAndReadNoBleedGM(), floor = 0.0)
    }
}
