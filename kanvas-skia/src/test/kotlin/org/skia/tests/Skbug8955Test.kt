package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Skbug8955Test {

    @Test
    fun `Skbug8955GM matches skbug_8955_png within tolerance`() {
        val gm = Skbug8955GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image skbug_8955.png")

        // Single "+" glyph at 50pt — mostly white background, small glyph
        // ink ; the bulk of the canvas is colour-space-invariant white so
        // we can run a very tight tolerance and floor.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Skbug8955GM", comparison)
        if (comparison.similarity < 99.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Skbug8955GM", comparison.similarity)
        assertTrue(accepted, "Skbug8955GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 99.0,
            "Skbug8955GM similarity ${"%.2f".format(comparison.similarity)}% < 99.0% floor",
        )
    }
}
