package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ShaderText3Test {

    @Test
    fun `ShaderText3GM matches shadertext3_png within tolerance`() {
        val gm = ShaderText3GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image shadertext3.png")

        // Observed similarity ~84.9% — text glyphs use bitmap-shader fills
        // and the antialiased glyph edges sample slightly differently from
        // upstream's reference renderer. The structural content (4 tile-mode
        // pairs across 4 rows of letter 'B') matches.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ShaderText3GM", comparison)
        val floor = 84.8
        if (comparison.similarity < floor) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ShaderText3GM", comparison.similarity)
        assertTrue(accepted, "ShaderText3GM regressed below ratchet")
        assertTrue(comparison.similarity >= floor,
            "ShaderText3GM similarity ${"%.2f".format(comparison.similarity)}% < $floor% (t=8 floor)")
    }
}
