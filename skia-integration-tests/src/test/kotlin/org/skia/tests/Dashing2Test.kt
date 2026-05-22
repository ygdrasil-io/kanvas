package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Visual regression for [Dashing2GM] (port of upstream
 * `gm/dashing.cpp::Dashing2GM`).
 *
 * Exercises [org.skia.foundation.SkDashPathEffect] on a 3 × 4 grid of
 * line / rect / oval / star paths under three patterns, including a
 * 4-element pattern. Floor 50 % (breadth-first ratchet).
 */
class Dashing2Test {

    @Test
    fun `Dashing2GM matches dashing2_png within tolerance`() {
        val gm = Dashing2GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Dashing2GM", comparison)
        if (comparison.similarity < 75.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Dashing2GM", comparison.similarity)
        assertTrue(accepted, "Dashing2GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "Dashing2GM similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
