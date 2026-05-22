package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.COLR_V1 / STUB.FONTATIONS / STUB.FIXTURE: COLR v1 colour-glyph " +
        "rendering requires (a) `fonts/test_glyphs-glyf_colr_1.ttf` + its " +
        "`_variable` sibling under `kanvas-legacy/src/test/resources/fonts/` " +
        "(not shipped), (b) FreeType+HarfBuzz COLR v1 paint-graph " +
        "resolution via JNI (see `SkColrV1` + `API_FINALIZATION_PLAN.md`), " +
        "(c) Fontations Rust-crate binding for the variable-axis path " +
        "(see `SkTypeface_Fontations`). The GM body is fully ported " +
        "against the live `SkFont` / `SkTypeface.makeClone` / " +
        "`SkCanvas.drawSimpleText` surface — drop this `@Disabled` once " +
        "the JNI + fixture trio lands.",
)
class ColrV1Test {

    @Test
    fun `ColrV1GM matches reference`() {
        val gm = ColrV1GM()
        TestUtils.runGmTest(gm)
    }
}
