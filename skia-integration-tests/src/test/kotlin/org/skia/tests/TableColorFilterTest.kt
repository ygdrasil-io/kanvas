package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class TableColorFilterTest {

    @Test
    fun `TableColorFilterGM matches tablecolorfilter_png within tolerance`() {
        val gm = TableColorFilterGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image tablecolorfilter.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("TableColorFilterGM", comparison)
        if (comparison.similarity < 55.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TableColorFilterGM", comparison.similarity)
        assertTrue(accepted, "TableColorFilterGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 55.0,
            "TableColorFilterGM similarity ${"%.2f".format(comparison.similarity)}% < 55.0% floor",
        )
    }
}
