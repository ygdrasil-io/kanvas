package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Tiling2Test {

    @Test
    fun `Tiling2GM bitmap matches tilemode_bitmap_png within tolerance`() {
        val gm = Tiling2GM(kind = Tiling2GM.Kind.Bitmap)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image tilemode_bitmap.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("Tiling2BitmapGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Tiling2BitmapGM", comparison.similarity)
        assertTrue(accepted, "Tiling2BitmapGM regressed below ratchet")
    }

    @Test
    fun `Tiling2GM gradient matches tilemode_gradient_png within tolerance`() {
        val gm = Tiling2GM(kind = Tiling2GM.Kind.Gradient)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image tilemode_gradient.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("Tiling2GradientGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Tiling2GradientGM", comparison.similarity)
        assertTrue(accepted, "Tiling2GradientGM regressed below ratchet")
    }
}
