package org.skia.tests

import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.testing.TestUtils
import org.skia.tools.ToolUtils

class ColrV1Test {

    @Test
    fun `COLRv1 upstream fixtures are bundled with license`() {
        assertEquals(21568, fixtureSize("fonts/test_glyphs-glyf_colr_1.ttf"))
        assertEquals(53096, fixtureSize("fonts/test_glyphs-glyf_colr_1_variable.ttf"))
        assertNotNull(ToolUtils.GetResourceAsData("fonts/test_glyphs_colrv1_LICENSE-Apache-2.0.txt"))
        assertNotNull(ToolUtils.GetResourceAsData("fonts/test_glyphs_colrv1_NOTICE.md"))
    }

    @Test
    fun `ColrV1GM renders bundled upstream fixture subset`() {
        val gm = ColrV1GM()
        val rendered = TestUtils.runGmTest(gm)

        assertTrue(gm.didLoadBundledFixture)
        assertTrue(rendered.countPixelsMatching { it.isDominantRed() } > 0)
        assertTrue(rendered.countPixelsMatching { it.isDominantGreen() } > 0)
        assertTrue(rendered.countPixelsMatching { it.isDominantBlue() } > 0)
    }
}

private fun fixtureSize(path: String): Int =
    ToolUtils.GetResourceAsData(path)?.size ?: error("Missing fixture: $path")

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

private fun Int.isDominantRed(): Boolean =
    SkColorGetA(this) > 240 &&
        SkColorGetR(this) > 100 &&
        SkColorGetR(this) > SkColorGetG(this) + 35 &&
        SkColorGetR(this) > SkColorGetB(this) + 35

private fun Int.isDominantGreen(): Boolean =
    SkColorGetA(this) > 240 &&
        SkColorGetG(this) > 100 &&
        SkColorGetG(this) > SkColorGetR(this) + 35 &&
        SkColorGetG(this) > SkColorGetB(this) + 35

private fun Int.isDominantBlue(): Boolean =
    SkColorGetA(this) > 240 &&
        SkColorGetB(this) > 120 &&
        SkColorGetB(this) > SkColorGetR(this) + 50 &&
        SkColorGetB(this) > SkColorGetG(this) + 50
