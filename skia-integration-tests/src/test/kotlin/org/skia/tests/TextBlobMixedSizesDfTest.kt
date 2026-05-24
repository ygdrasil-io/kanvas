package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Cross-backend ratchet driver for [TextBlobMixedSizesGM] (DFT variant,
 * name `textblobmixedsizes_df`).
 *
 * Disabled until the raster backend grows a distance-field-text rendering
 * path compatible with [org.skia.foundation.SkSurfaceProps.kUseDeviceIndependentFonts_Flag].
 *
 * Upstream renders this GM through a Ganesh surface with the DF-text flag set,
 * producing glyphs via a signed-distance-field shader. `:kanvas-skia` raster
 * has no SDF text pipeline — glyph paths are drawn via the regular coverage-AA
 * path instead — so the rendered output will not be pixel-identical to the
 * upstream GPU reference. The GM body is fully ported and exercises the
 * offscreen-surface + surface-props + matrix-threading plumbing.
 *
 * Drop this `@Disabled` once raster grows an SDF text shader path
 * (`STUB.DF_TEXT_RASTER` — same gap as the sibling [DFTextGM] / [DFTextBlobPerspGM]).
 */
@Disabled(
    "STUB.DF_TEXT_RASTER: GPU-only distance-field text path. " +
        "Body fully ported against SkSurfaceProps.kUseDeviceIndependentFonts_Flag " +
        "+ offscreen surface + matrix plumbing — raster output is path-based, not SDF, " +
        "so pixels will not match the upstream Ganesh reference. " +
        "Drop this @Disabled once raster grows an SDF text shader path.",
)
class TextBlobMixedSizesDfTest {

    @Test
    fun `TextBlobMixedSizesGM (df) matches reference`() {
        val gm = TextBlobMixedSizesGM(useDFT = true)
        TestUtils.runGmTest(gm)
    }
}
