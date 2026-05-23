package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageFiltersTransformedOriginalTest {

    @Test
    fun `ImageFiltersTransformedOriginalGM matches imagefilterstransformed_png within tolerance`() {
        val gm = ImageFiltersTransformedOriginalGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image imagefilterstransformed.png")
        // Five image filters (Blur, DropShadow, DisplacementMap, Dilate, Erode) rendered
        // through scale / rotate / skew CTMs. Bilinear sampling and the displacement map
        // accumulate per-pixel rounding vs upstream; floor 30 % is generous but safe.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ImageFiltersTransformedOriginalGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageFiltersTransformedOriginalGM", comparison.similarity)
        assertTrue(accepted, "ImageFiltersTransformedOriginalGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "ImageFiltersTransformedOriginalGM similarity ${"%.2f".format(comparison.similarity)}% < 30% floor",
        )
    }
}
