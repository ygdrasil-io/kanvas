package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.GR_RANDOM_TYPEFACE: textblobrandomfont is GPU-only (upstream returns kSkip on raster) " +
        "and requires SkRandomTypeface (tools/fonts/RandomScalerContext.h) + " +
        "ToolUtils::makeSurface (GPU-backed off-screen surface) + " +
        "GrDirectContext::freeGpuResources() — not available in the CPU raster backend",
)
class TextBlobRandomFontTest {

    @Test
    fun `TextBlobRandomFontGM matches reference`() {
        val gm = TextBlobRandomFontGM()
        TestUtils.runGmTest(gm)
    }
}
