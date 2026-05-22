package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.AsyncRescaleAndReadNoBleedGM

@Disabled("STUB.ASYNC_RESCALE_AND_READ: requires SkSurface.asyncRescaleAndReadPixels")
class AsyncRescaleAndReadNoBleedCrossBackendTest {
    @Test
    fun `AsyncRescaleAndReadNoBleedGM placeholder`() {
        runCrossBackendTest(AsyncRescaleAndReadNoBleedGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
