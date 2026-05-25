package org.skia.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.skia.foundation.SkBitmap
import org.skia.testing.TestUtils

class PaletteTest {

    @Test
    fun `PaletteGM renders portable palette override glyphs`() {
        val gm = PaletteGM()
        val rendered = TestUtils.runGmTest(gm)

        assertTrue(rendered.countPixelsMatching { it.isDominantGreen() } > 0)
        assertTrue(rendered.countPixelsMatching { it.isDominantBlue() } > 0)
        assertEquals(0, rendered.countPixels(0xFF000000.toInt()))
    }
}

private fun SkBitmap.countPixels(color: Int): Int {
    var count = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (getPixel(x, y) == color) count++
        }
    }
    return count
}

private fun SkBitmap.countPixelsMatching(predicate: (Int) -> Boolean): Int {
    var count = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (predicate(getPixel(x, y))) count++
        }
    }
    return count
}

private fun Int.isDominantGreen(): Boolean =
    SkColorGetA(this) > 240 &&
        SkColorGetG(this) > 120 &&
        SkColorGetG(this) > SkColorGetR(this) + 50 &&
        SkColorGetG(this) > SkColorGetB(this) + 50

private fun Int.isDominantBlue(): Boolean =
    SkColorGetA(this) > 240 &&
        SkColorGetB(this) > 120 &&
        SkColorGetB(this) > SkColorGetR(this) + 50 &&
        SkColorGetB(this) > SkColorGetG(this) + 50
