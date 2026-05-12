package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BitmapTiledTest {

    @Test
    fun `BitmapTiledFractionalHorizontalManualGM matches reference within tolerance`() {
        val gm = BitmapTiledFractionalHorizontalManualGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BitmapTiledFractionalHorizontalManualGM", comparison)
        if (comparison.similarity < 99.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(
            "BitmapTiledFractionalHorizontalManualGM", comparison.similarity,
        )
        assertTrue(accepted, "BitmapTiledFractionalHorizontalManualGM regressed below ratchet")
    }

    @Test
    fun `BitmapTiledFractionalVerticalManualGM matches reference within tolerance`() {
        val gm = BitmapTiledFractionalVerticalManualGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BitmapTiledFractionalVerticalManualGM", comparison)
        if (comparison.similarity < 99.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(
            "BitmapTiledFractionalVerticalManualGM", comparison.similarity,
        )
        assertTrue(accepted, "BitmapTiledFractionalVerticalManualGM regressed below ratchet")
    }
}
