package org.skia.tests

import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.testing.TestUtils

class FontPaletteTest {

    @Test
    fun `FontPaletteGM default renders portable palette glyphs`() {
        val gm = FontPaletteGM.defaultPalette()
        val rendered = TestUtils.runGmTest(gm)

        assertTrue(rendered.countPixelsMatching { it.isDominantBlue() } > 0)
        assertEquals(0, rendered.countPixels(0xFF000000.toInt()))
    }

    @Test
    fun `FontPaletteGM light renders palette selection`() {
        val gm = FontPaletteGM.light()
        val rendered = TestUtils.runGmTest(gm)

        assertTrue(rendered.countPixelsMatching { it.isDominantBlue() } > 0)
        assertTrue(rendered.countPixelsMatching { it.isDominantGreen() } > 0)
        assertEquals(0, rendered.countPixels(0xFF000000.toInt()))
    }

    @Test
    fun `FontPaletteGM dark renders palette selection`() {
        val gm = FontPaletteGM.dark()
        val rendered = TestUtils.runGmTest(gm)

        assertTrue(rendered.countPixelsMatching { it.isDominantBlue() } > 0)
        assertTrue(rendered.countPixelsMatching { it.isDominantRed() } > 0)
        assertEquals(0, rendered.countPixels(0xFF000000.toInt()))
    }

    @Test
    fun `FontPaletteGM one renders single override`() {
        val gm = FontPaletteGM.one()
        val rendered = TestUtils.runGmTest(gm)

        assertTrue(rendered.countPixelsMatching { it.isDominantBlue() } > 0)
        assertTrue(rendered.countPixelsMatching { it.isCyan() } > 0)
        assertEquals(0, rendered.countPixels(0xFF000000.toInt()))
    }

    @Test
    fun `FontPaletteGM all renders override list`() {
        val gm = FontPaletteGM.all()
        val rendered = TestUtils.runGmTest(gm)

        assertTrue(rendered.countPixelsMatching { it.isDominantBlue() } > 0)
        assertTrue(rendered.countPixelsMatching { it.isPurple() } > 0)
        assertTrue(rendered.countPixelsMatching { it.isMagenta() } > 0)
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

private fun Int.isDominantBlue(): Boolean =
    SkColorGetA(this) > 240 &&
        SkColorGetB(this) > 120 &&
        SkColorGetB(this) > SkColorGetR(this) + 50 &&
        SkColorGetB(this) > SkColorGetG(this) + 50

private fun Int.isDominantGreen(): Boolean =
    SkColorGetA(this) > 240 &&
        SkColorGetG(this) > 100 &&
        SkColorGetG(this) > SkColorGetR(this) + 35 &&
        SkColorGetG(this) > SkColorGetB(this) + 35

private fun Int.isDominantRed(): Boolean =
    SkColorGetA(this) > 240 &&
        SkColorGetR(this) > 100 &&
        SkColorGetR(this) > SkColorGetG(this) + 35 &&
        SkColorGetR(this) > SkColorGetB(this) + 35

private fun Int.isCyan(): Boolean =
    SkColorGetA(this) > 240 &&
        SkColorGetG(this) > 120 &&
        SkColorGetB(this) > 120 &&
        SkColorGetG(this) > SkColorGetR(this) + 30 &&
        SkColorGetB(this) > SkColorGetR(this) + 30

private fun Int.isPurple(): Boolean =
    SkColorGetA(this) > 240 &&
        SkColorGetR(this) > 70 &&
        SkColorGetB(this) > 90 &&
        SkColorGetG(this) < 90

private fun Int.isMagenta(): Boolean =
    SkColorGetA(this) > 240 &&
        SkColorGetR(this) > 120 &&
        SkColorGetB(this) > 120 &&
        SkColorGetG(this) < 100
