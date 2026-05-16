package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for [PointsGM] — the upstream `points` GM. 99 random points
 * drawn three times (polygon, lines, points) with three colours,
 * plus a final white pass overlaying the blue circles.
 *
 * Most of the canvas is left as the background white, so the
 * similarity baseline is high. The fine 4-px polygon stroke and the
 * hairline `kLines` mode are where any AA / stroke-quantisation
 * difference shows up.
 */
class PointsTest {

    @Test
    fun `PointsGM matches points_png within tolerance`() {
        val gm = PointsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image points.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("PointsGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PointsGM", comparison.similarity)
        assertTrue(accepted, "PointsGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 70.0,
            "PointsGM similarity ${"%.2f".format(comparison.similarity)}% < 70.0% floor",
        )
    }
}
