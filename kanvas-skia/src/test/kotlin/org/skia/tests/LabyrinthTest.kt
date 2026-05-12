package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Three GM ports from `labyrinth.cpp` — square, round, butt caps. See
 * the GM-side header in [LabyrinthSquareGM] for the maze geometry and
 * the underlying coverage-counting bug repro (crbug.com/913223).
 *
 * Floor 70% — the maze is rendered as hairline-style strokes
 * (0.1-px width pre-40×-scale) and the kanvas-skia stroker's coverage
 * over abutted joins still differs from upstream's analytic-coverage
 * path at the same pixel locations that motivated the original bug.
 * The dominant black structure (the maze body) matches; the
 * residual is concentrated around cap/join intersections.
 */
class LabyrinthTest {

    @Test
    fun `LabyrinthSquareGM matches labyrinth_square_png within tolerance`() {
        runOne(LabyrinthSquareGM(), "LabyrinthSquareGM")
    }

    @Test
    fun `LabyrinthRoundGM matches labyrinth_round_png within tolerance`() {
        runOne(LabyrinthRoundGM(), "LabyrinthRoundGM")
    }

    @Test
    fun `LabyrinthButtGM matches labyrinth_butt_png within tolerance`() {
        runOne(LabyrinthButtGM(), "LabyrinthButtGM")
    }

    private fun runOne(gm: GM, displayName: String) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed(displayName, comparison)
        if (comparison.similarity < 70.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(displayName, comparison.similarity)
        assertTrue(accepted, "$displayName regressed below ratchet")
        assertTrue(
            comparison.similarity >= 70.0,
            "$displayName similarity ${"%.2f".format(comparison.similarity)}% < 70.0% floor",
        )
    }
}
