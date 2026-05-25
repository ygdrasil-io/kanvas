package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.testing.TestUtils

class AsyncRescaleAndReadGridTest {

    @Test
    fun `AsyncRescaleAndReadGridGM runs RGBA CPU grid`() {
        val gm = AsyncRescaleAndReadGridGM()
        val rendered = TestUtils.runGmTest(gm)
        assertTrue(hasNonWhitePixel(rendered), "RGBA async readback grid should draw visible cells")
    }

    @Test
    @Disabled("STUB.ASYNC_RESCALE_READ_YUV: YUV/YUVA async readback variants remain codec-media gated")
    fun `AsyncRescaleAndReadGridGM YUV variants remain gated`() {
    }
}

internal fun hasNonWhitePixel(bitmap: SkBitmap): Boolean {
    for (y in 0 until bitmap.height) {
        for (x in 0 until bitmap.width) {
            if (bitmap.getPixel(x, y) != 0xFFFFFFFF.toInt()) return true
        }
    }
    return false
}
