package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

class AsyncRescaleAndReadNoBleedTest {

    @Test
    fun `AsyncRescaleAndReadNoBleedGM runs RGBA CPU readback`() {
        val gm = AsyncRescaleAndReadNoBleedGM()
        val rendered = TestUtils.runGmTest(gm)
        assertTrue(hasNonWhitePixel(rendered), "NoBleed RGBA async readback should draw visible content")
    }

    @Test
    @Disabled("STUB.ASYNC_RESCALE_AND_READ_PARITY: upstream no-bleed reference parity needs broader GM port")
    fun `AsyncRescaleAndReadNoBleedGM matches reference`() {
    }
}
