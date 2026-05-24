package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class MultipleFiltersTest {

    @Test
    fun `MultipleFiltersGM matches reference`() {
        val gm = MultipleFiltersGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("MultipleFiltersGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("MultipleFiltersGM", comparison.similarity)
        assertTrue(accepted, "MultipleFiltersGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "MultipleFiltersGM similarity ${"%.2f".format(comparison.similarity)}% < 50.0% floor",
        )
    }
}
