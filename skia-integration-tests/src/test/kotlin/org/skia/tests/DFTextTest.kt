package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.DF_TEXT_FULL_GM: the portable raster SDF slice currently covers " +
        "`dftext_blob_persp` text-blob glyph sampling. `gm/dftext.cpp` still " +
        "mixes drawSimpleText blocks, gamma/background checks, and an optional " +
        "emoji branch whose reference was captured from Ganesh. Keep this " +
        "broader GM font/reference-gated until those paths are routed through " +
        "the SDF sampler or split into narrower ratchets.",
)
class DFTextTest {

    @Test
    fun `DFTextGM matches reference`() {
        val gm = DFTextGM()
        TestUtils.runGmTest(gm)
    }
}
