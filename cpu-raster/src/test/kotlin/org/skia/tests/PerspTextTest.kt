package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class PerspTextTest {

    @Test
    fun `PerspTextGM matches persptext_png within tolerance`() {
        val gm = PerspTextGM(fMinimal = false)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image persptext.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("PerspTextGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PerspTextGM", comparison.similarity)
        assertTrue(accepted, "PerspTextGM regressed below ratchet")
    }

    @Test
    fun `PerspTextGM minimal matches persptext_minimal_png within tolerance`() {
        val gm = PerspTextGM.minimal()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image persptext_minimal.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("PerspTextMinimalGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PerspTextMinimalGM", comparison.similarity)
        assertTrue(accepted, "PerspTextMinimalGM regressed below ratchet")
    }
}
