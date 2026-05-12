package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ColorFilterAlpha8Test {

    @Test
    fun `ColorFilterAlpha8GM matches colorfilteralpha8_png within tolerance`() {
        val gm = ColorFilterAlpha8GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image colorfilteralpha8.png")

        // Phase G4a proof-of-concept GM. Tolerance kept loose (=2) — the
        // SkColorFilter / Alpha8 sampling path has subtle xform interactions
        // through the Rec.2020 working space. Tightening can come once a
        // dedicated F16 Alpha8 path is ratchet-validated.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 2)
        TestReport.recordDetailed("ColorFilterAlpha8GM", comparison)
        if (comparison.similarity < 99.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ColorFilterAlpha8GM", comparison.similarity)
        assertTrue(accepted, "ColorFilterAlpha8GM regressed below ratchet")
    }
}
