package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class PathArcToSkbug9077Test {
    @Test
    fun `PathArcToSkbug9077GM matches path_arcto_skbug_9077_png within tolerance`() {
        val gm = PathArcToSkbug9077GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image path_arcto_skbug_9077.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("PathArcToSkbug9077GM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PathArcToSkbug9077GM", comparison.similarity)
        assertTrue(accepted, "PathArcToSkbug9077GM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "PathArcToSkbug9077GM similarity ${"%.2f".format(comparison.similarity)}% < 90.0%")
    }
}
