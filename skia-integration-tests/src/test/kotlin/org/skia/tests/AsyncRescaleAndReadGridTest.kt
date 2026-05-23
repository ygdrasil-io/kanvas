package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.ASYNC_RESCALE_READ: requires SkSurface.asyncRescaleAndReadPixels (all 8 grid variants)")
class AsyncRescaleAndReadGridTest {

    @Test
    fun `AsyncRescaleAndReadGridGM matches reference`() {
        val gm = AsyncRescaleAndReadGridGM()
        TestUtils.runGmTest(gm)
    }
}
