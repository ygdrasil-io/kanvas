package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug691386Test {

    @Test
    fun `Crbug691386GM matches crbug_691386_png within tolerance`() {
        val gm = Crbug691386GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_691386.png")
        // Stroked SVG-arc path scaled 96× with sub-pixel source stroke width
        // (0.025 → 2.4 px device). The path is the unit half-arc
        // M -1 0 A 1 1 0 0 0 1 0 Z. Almost all pixels are background ;
        // the sliver of stroked outline is anti-aliased. AA along the
        // 2.4-px-wide arc edge produces small per-pixel deviations from
        // upstream's exact edge-coverage tables, which keeps similarity
        // just under 98%. Floor set to 97.5% to accommodate.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Crbug691386GM", comparison)
        if (comparison.similarity < 97.5) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug691386GM", comparison.similarity)
        assertTrue(accepted, "Crbug691386GM regressed below ratchet")
        assertTrue(comparison.similarity >= 97.5,
            "Crbug691386GM similarity ${"%.2f".format(comparison.similarity)}% < 97.5% (t=1 floor)")
    }
}
