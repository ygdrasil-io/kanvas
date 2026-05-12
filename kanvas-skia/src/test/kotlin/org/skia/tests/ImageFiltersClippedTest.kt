package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageFiltersClippedTest {

    @Test
    fun `ImageFiltersClippedGM matches imagefiltersclipped_png within tolerance`() {
        val gm = ImageFiltersClippedGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image imagefiltersclipped.png")

        // 8-filter grid — Blur / DropShadow / DisplacementMap / Dilate /
        // Erode / Offset / MatrixTransform / PointLitDiffuse — each
        // exercises a different raster pipeline. Cumulative drift
        // across the 5 × 8 grid + the rightmost Perlin column makes
        // tolerance 12 the right band.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 12)
        TestReport.recordDetailed("ImageFiltersClippedGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageFiltersClippedGM", comparison.similarity)
        assertTrue(accepted, "ImageFiltersClippedGM regressed below ratchet")
    }
}
