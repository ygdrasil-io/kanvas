package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class PatchAlphaTest {

    @Test
    fun `PatchAlphaGM matches patch_alpha_png within tolerance`() {
        val gm = PatchAlphaGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image patch_alpha.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("PatchAlphaGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PatchAlphaGM", comparison.similarity)
        assertTrue(accepted, "PatchAlphaGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "PatchAlphaGM similarity ${"%.2f".format(comparison.similarity)}% < 30.0% floor",
        )
    }
}
