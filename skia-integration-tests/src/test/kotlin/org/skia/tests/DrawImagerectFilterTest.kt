package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DrawImagerectFilterTest {

    @Test
    fun `DrawImagerectFilterGM matches drawimagerect_filter_png within tolerance`() {
        val gm = DrawImagerectFilterGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image drawimagerect_filter.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DrawImagerectFilterGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DrawImagerectFilterGM", comparison.similarity)
        assertTrue(accepted, "DrawImagerectFilterGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 30.0,
            "DrawImagerectFilterGM similarity ${"%.2f".format(comparison.similarity)}% < 30.0% floor",
        )
    }
}
