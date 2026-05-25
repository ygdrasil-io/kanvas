package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

class AsyncRescaleAndReadAlphaTypeTest {

    @Test
    fun `AsyncRescaleAndReadAlphaTypeGM runs RGBA CPU readback`() {
        val gm = AsyncRescaleAndReadAlphaTypeGM()
        val rendered = TestUtils.runGmTest(gm)
        assertTrue(hasNonWhitePixel(rendered), "AlphaType RGBA async readback should draw visible content")
    }

    @Test
    @Disabled("STUB.ASYNC_RESCALE_AND_READ_PARITY: upstream alpha-type reference parity needs broader GM port")
    fun `AsyncRescaleAndReadAlphaTypeGM matches reference`() {
    }
}
