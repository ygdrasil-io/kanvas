package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Visual regression for [EmptyStrokeGM] (port of upstream
 * `gm/emptypath.cpp::EmptyStrokeGM`).
 *
 * Exercises stroker handling of zero-length sub-paths : `moveTo`-only,
 * `moveTo + close`, `moveTo + lineTo(same)`, and a mix. Floor 50 %
 * (breadth-first ratchet pass — bump once observed).
 */
class EmptyStrokeTest {

    @Test
    fun `EmptyStrokeGM matches emptystroke_png within tolerance`() {
        val gm = EmptyStrokeGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("EmptyStrokeGM", comparison)
        if (comparison.similarity < 75.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("EmptyStrokeGM", comparison.similarity)
        assertTrue(accepted, "EmptyStrokeGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "EmptyStrokeGM similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
