package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class EmbossmaskfilterTest {
    @Test
    fun `EmbossmaskfilterGM matches embossmaskfilter_png within tolerance`() {
        val gm = EmbossmaskfilterGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image embossmaskfilter.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("EmbossmaskfilterGM", comparison)
        if (comparison.similarity < 65.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("EmbossmaskfilterGM", comparison.similarity)
        assertTrue(accepted, "EmbossmaskfilterGM regressed below ratchet")
        assertTrue(comparison.similarity >= 65.0,
            "EmbossmaskfilterGM similarity ${"%.2f".format(comparison.similarity)}% < 65.0%")
    }
}
