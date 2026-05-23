package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Cross-backend ratchet driver for [DFTextBlobPerspGM]. Disabled until
 * the raster backend grows a distance-field-text rendering path
 * compatible with [org.skia.foundation.SkSurfaceProps.kUseDeviceIndependentFonts_Flag].
 *
 * Upstream renders the GM through a Ganesh surface with the DF-text
 * flag set, producing glyphs via a signed-distance-field shader.
 * `:kanvas-skia` raster renders glyphs as paths (via
 * `SkFont.getPath(gid)` → `SkCanvas.drawPath`), so the rendered
 * output for this GM exists and exercises the perspective CTM /
 * clip / text-blob plumbing, but will not be pixel-identical to the
 * upstream GPU reference. The cross-backend comparison is parked
 * until the SDF path lands (`STUB.DF_TEXT_RASTER` — same gap as the
 * sibling [DFTextGM]).
 */
@Disabled(
    "STUB.DF_TEXT_RASTER: GPU-only distance-field text path. " +
        "Body fully ported against `SkSurfaceProps.kUseDeviceIndependentFonts_Flag` " +
        "+ perspective text-blob plumbing — raster output is path-based, not SDF, " +
        "so pixels will not match the upstream Ganesh reference. " +
        "Drop this `@Disabled` once raster grows an SDF text shader path.",
)
class DFTextBlobPerspTest {

    @Test
    fun `DFTextBlobPerspGM matches reference`() {
        val gm = DFTextBlobPerspGM()
        TestUtils.runGmTest(gm)
    }
}
