package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ConvexPolyClipTest {

    @Test
    fun `ConvexPolyClipGM matches convex_poly_clip_png within tolerance`() {
        val gm = ConvexPolyClipGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image convex_poly_clip.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ConvexPolyClipGM", comparison)
        if (comparison.similarity < 70.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ConvexPolyClipGM", comparison.similarity)
        assertTrue(accepted, "ConvexPolyClipGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 20.0,
            "ConvexPolyClipGM similarity ${"%.2f".format(comparison.similarity)}% < 20% floor",
        )
    }
}
