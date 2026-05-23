package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.COLR_V1 / STUB.FONTATIONS / STUB.FIXTURE: " +
        "SkFontArguments::Palette palette-override on COLR v1 colour fonts " +
        "requires (a) `fonts/test_glyphs-glyf_colr_1.ttf` under " +
        "`kanvas-legacy/src/test/resources/fonts/` (not shipped), " +
        "(b) FreeType+HarfBuzz COLR v1 paint-graph resolution via JNI " +
        "(see `SkColrV1` + `API_FINALIZATION_PLAN.md`), (c) Fontations " +
        "Rust-crate binding for the palette-override path " +
        "(see `SkTypeface_Fontations`). The GM body is fully ported " +
        "against the live `SkFontArguments.Palette` / `SkTypeface.makeClone` " +
        "/ `SkCanvas.drawSimpleText` surface — drop this `@Disabled` once " +
        "the JNI + fixture trio lands. Same blocker as sibling `ColrV1Test`.",
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
