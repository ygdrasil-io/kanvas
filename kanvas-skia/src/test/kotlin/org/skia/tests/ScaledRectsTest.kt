package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ScaledRectsTest {

    @Test
    fun `ScaledRectsGM matches scaledrects_png within tolerance`() {
        val gm = ScaledRectsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image scaledrects.png")
        // Phase 6 entry: ScaledRectsGM exercises full 3x3 SkMatrix +
        // SkBlendMode.kPlus. Both layers (blue rect, red+blue=magenta band)
        // are opaque, so kPlus is bit-equivalent to Skia's premul reference
        // — the only sources of disagreement are the rasterizer rounding on
        // the rotated rect edges.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ScaledRectsGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ScaledRectsGM", comparison.similarity)
        assertTrue(accepted, "ScaledRectsGM regressed below tolerance")
        assertTrue(comparison.similarity >= 85.0,
            "ScaledRectsGM similarity ${"%.2f".format(comparison.similarity)}% < 85.0% (Phase 5+ floor)")
    }
}
