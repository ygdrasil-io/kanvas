package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Visual regression for [StrokeRectGM] (port of upstream
 * `gm/strokerect.cpp::StrokeRectGM`).
 *
 * The 12 × 6 grid stresses rect-stroking across normal / inverted /
 * tiny / `FLT_EPSILON` geometries × three join types × stroke vs
 * stroke-and-fill. Each cell layers a wide gray stroke, the
 * stroker's red outline path, and red landmark points — so the
 * similarity score is sensitive to:
 *  - Join geometry at degenerate corners (zero-length edges, etc.).
 *  - The stroker's emission order at `FLT_EPSILON` widths.
 *  - The point-mode dot rendering for the landmarks.
 */
class StrokeRectTest {

    @Test
    fun `StrokeRectGM matches strokerect_png within tolerance`() {
        val gm = StrokeRectGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image strokerect.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("StrokeRectGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("StrokeRectGM", comparison.similarity)
        assertTrue(accepted, "StrokeRectGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 93.5,
            "StrokeRectGM similarity ${"%.2f".format(comparison.similarity)}% < 93.5% floor",
        )
    }
}
