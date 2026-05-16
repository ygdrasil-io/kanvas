package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Validates [Crbug1313579GM] against the upstream Skia reference
 * `crbug_1313579.png`. Exercises the Phase G6 [org.skia.core.SaveLayerRec]
 * `backdrop` path + [org.skia.foundation.SkImageFilters.Blur] with
 * `tileMode = kClamp` and a `cropRect`.
 *
 * The canvas is 110 × 110 — the inner 100 × 100 is white-on-green
 * with a 50σ clamp-blur backdrop applied. With a faithful clamp-mode
 * backdrop the green margin survives untouched and the centre is
 * solid white (the blur of a uniform white field is white).
 *
 * Floor 60 % : kanvas-skia's image-filter pipeline produces an
 * exact backdrop reproduction in the centre but the 5 px transition
 * band at the white/green edge differs from upstream's downsampled
 * blur pyramid (which is implementation-defined). The whole-canvas
 * similarity at tolerance 8 captures the shape match while accepting
 * sub-pixel halo drift.
 */
class Crbug1313579Test {

    @Test
    fun `Crbug1313579GM matches crbug_1313579_png within tolerance`() {
        val gm = Crbug1313579GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_1313579.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("Crbug1313579GM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug1313579GM", comparison.similarity)
        assertTrue(accepted, "Crbug1313579GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 60.0,
            "Crbug1313579GM similarity ${"%.2f".format(comparison.similarity)}% < 60.0% floor",
        )
    }
}
