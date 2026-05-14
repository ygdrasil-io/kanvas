package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class PolygonOffsetTest {

    @Test
    fun `PolygonOffsetGM convex variant matches convex-polygon-inset_png within tolerance`() {
        runVariant(convexOnly = true, scoreKey = "PolygonOffsetGM_convex", floor = 79.3)
    }

    @Test
    fun `PolygonOffsetGM simple variant matches simple-polygon-offset_png within tolerance`() {
        runVariant(convexOnly = false, scoreKey = "PolygonOffsetGM_simple", floor = 73.2)
    }

    private fun runVariant(convexOnly: Boolean, scoreKey: String, floor: Double) {
        val gm = PolygonOffsetGM(convexOnly = convexOnly)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")

        // Polygon inset/offset is approximated (centroid shrink for the
        // convex variant ; original-only for the simple variant). The
        // reference shows the true normal-offset polygons, so similarity
        // is structurally bounded.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed(scoreKey, comparison)
        if (comparison.similarity < floor) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(scoreKey, comparison.similarity)
        assertTrue(accepted, "$scoreKey regressed below ratchet")
    }
}
