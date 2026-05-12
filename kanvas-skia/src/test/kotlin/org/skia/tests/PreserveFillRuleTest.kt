package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class PreserveFillRuleTest {

    @Test
    fun `PreserveFillRuleBigGM matches preservefillrule_big_png within tolerance`() {
        val gm = PreserveFillRuleBigGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image preservefillrule_big.png")

        // 400 × 400 — four green stars (7-pt and 5-pt, winding and even-odd).
        // Background white, ink solid green. Residuals are AA-edge-only.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("PreserveFillRuleBigGM", comparison)
        if (comparison.similarity < 97.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PreserveFillRuleBigGM", comparison.similarity)
        assertTrue(accepted, "PreserveFillRuleBigGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 97.0,
            "PreserveFillRuleBigGM similarity ${"%.2f".format(comparison.similarity)}% < 97.0% floor",
        )
    }

    @Test
    fun `PreserveFillRuleLittleGM matches preservefillrule_little_png within tolerance`() {
        val gm = PreserveFillRuleLittleGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image preservefillrule_little.png")

        // 40 × 40 — four tiny stars. AA edges dominate at this resolution,
        // so floor is more generous than the big variant.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("PreserveFillRuleLittleGM", comparison)
        if (comparison.similarity < 75.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PreserveFillRuleLittleGM", comparison.similarity)
        assertTrue(accepted, "PreserveFillRuleLittleGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 75.0,
            "PreserveFillRuleLittleGM similarity ${"%.2f".format(comparison.similarity)}% < 75.0% floor",
        )
    }
}
