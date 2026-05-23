package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ArctoSkbug9272Test {
    @Test
    fun `ArctoSkbug9272GM matches arcto_skbug_9272_png within tolerance`() {
        val gm = ArctoSkbug9272GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image arcto_skbug_9272.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ArctoSkbug9272GM", comparison)
        if (comparison.similarity < THRESHOLD) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ArctoSkbug9272GM", comparison.similarity)
        assertTrue(accepted, "ArctoSkbug9272GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= THRESHOLD,
            "ArctoSkbug9272GM similarity ${"%.2f".format(comparison.similarity)}% < $THRESHOLD%",
        )
    }

    private companion object {
        private const val THRESHOLD: Double = 99.0
    }
}
