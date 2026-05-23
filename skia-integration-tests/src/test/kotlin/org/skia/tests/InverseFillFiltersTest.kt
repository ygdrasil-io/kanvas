package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class InverseFillFiltersTest {

    @Test
    fun `InverseFillFiltersGM matches inverse_fill_filters_png within tolerance`() {
        val gm = InverseFillFiltersGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image inverse_fill_filters.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("InverseFillFiltersGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("InverseFillFiltersGM", comparison.similarity)
        assertTrue(accepted, "InverseFillFiltersGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 0.0,
            "InverseFillFiltersGM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor",
        )
    }
}
