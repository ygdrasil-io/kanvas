package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Visual regression for [DashingGM] (port of upstream
 * `gm/dashing.cpp::DashingGM`).
 *
 * **Validation milestone for Phase 7b ([SkDashPathEffect]).** The
 * 12-row main grid + 4-row edge case section stresses the dash
 * pathEffect end-to-end through the `drawLine` → `pathEffect.filterPath`
 * → stroker → fill pipeline.
 *
 * **Floor 50 %** — first ratchet target. The dash decomposition is
 * line-only (no curve flattening exercised here), so the residual
 * gap vs upstream comes from stroker / AA precision on the per-dash
 * caps, not from the dash algorithm. We pin the floor low enough to
 * leave room for stroker refinements while still catching dash
 * regressions.
 */
class DashingTest {

    @Test
    fun `DashingGM matches dashing_png within tolerance`() {
        val gm = DashingGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DashingGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DashingGM", comparison.similarity)
        assertTrue(accepted, "DashingGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 50.0,
            "DashingGM similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
