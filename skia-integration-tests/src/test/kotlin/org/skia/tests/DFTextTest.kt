package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.DF_TEXT: `gm/dftext.cpp` is GPU-only upstream — the reference " +
        "`original-888/dftext.png` was rendered by a Ganesh sink with the " +
        "distance-field-text shader path (`SkSurfaceProps::" +
        "kUseDeviceIndependentFonts_Flag`). `:kanvas-skia` is raster-only and " +
        "has no SDF text pipeline (the shader + atlas-page management live " +
        "entirely on the GPU side in upstream Skia's " +
        "`src/gpu/ganesh/text/GrAtlasManager.cpp`). The body is fully ported " +
        "against the regular coverage-AA text path (`drawSimpleText` / " +
        "`drawTextBlob`) and the offscreen surface carries the upstream " +
        "`kUseDeviceIndependentFonts_Flag` for API round-tripping, but " +
        "glyph pixels won't match the SDF reference. Drop this `@Disabled` " +
        "once SDF text rasterisation lands on either backend.",
)
class DFTextTest {

    @Test
    fun `DFTextGM matches reference`() {
        val gm = DFTextGM()
        TestUtils.runGmTest(gm)
    }
}
