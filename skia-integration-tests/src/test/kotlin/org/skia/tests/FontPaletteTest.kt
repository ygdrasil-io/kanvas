package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.FIXTURE / GM_REFERENCES: SkFontArguments.Palette clones are covered " +
        "by pure Kotlin unit fixtures, but this upstream GM still requires " +
        "(a) `fonts/test_glyphs-glyf_colr_1.ttf` under " +
        "`kanvas-legacy/src/test/resources/fonts/` (not shipped), " +
        "(b) accepted reference images, and (c) integration of the " +
        "stream-load-with-args palette route. See #1020.",
)
class FontPaletteTest {

    @Test
    fun `FontPaletteGM default matches reference`() {
        val gm = FontPaletteGM.defaultPalette()
        TestUtils.runGmTest(gm)
    }

    @Test
    fun `FontPaletteGM light matches reference`() {
        val gm = FontPaletteGM.light()
        TestUtils.runGmTest(gm)
    }

    @Test
    fun `FontPaletteGM dark matches reference`() {
        val gm = FontPaletteGM.dark()
        TestUtils.runGmTest(gm)
    }

    @Test
    fun `FontPaletteGM one matches reference`() {
        val gm = FontPaletteGM.one()
        TestUtils.runGmTest(gm)
    }

    @Test
    fun `FontPaletteGM all matches reference`() {
        val gm = FontPaletteGM.all()
        TestUtils.runGmTest(gm)
    }
}
