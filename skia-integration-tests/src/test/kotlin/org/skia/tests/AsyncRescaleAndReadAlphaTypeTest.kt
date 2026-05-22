package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.ASYNC_RESCALE_AND_READ: requires SkSurface.asyncRescaleAndReadPixels")
class AsyncRescaleAndReadAlphaTypeTest {

    @Test
    fun `AsyncRescaleAndReadAlphaTypeGM matches reference`() {
        val gm = AsyncRescaleAndReadAlphaTypeGM()
        TestUtils.runGmTest(gm)
    }
}
