package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class EmbossTest {
    @Test
    fun `EmbossGM matches emboss_png within tolerance`() {
        val gm = EmbossGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image emboss.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("EmbossGM", comparison)
        if (comparison.similarity < 65.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("EmbossGM", comparison.similarity)
        assertTrue(accepted, "EmbossGM regressed below ratchet")
        assertTrue(comparison.similarity >= 65.0,
            "EmbossGM similarity ${"%.2f".format(comparison.similarity)}% < 65.0%")
    }
}
