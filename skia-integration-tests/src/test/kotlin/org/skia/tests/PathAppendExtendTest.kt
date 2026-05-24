package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * PathAppendExtendGM ports gm/patharcto.cpp `path_append_extend`.
 */
class PathAppendExtendTest {
    @Test
    fun `PathAppendExtendGM matches path_append_extend_png within tolerance`() {
        val gm = PathAppendExtendGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image path_append_extend.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("PathAppendExtendGM", comparison)
        if (comparison.similarity < THRESHOLD) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PathAppendExtendGM", comparison.similarity)
        assertTrue(accepted, "PathAppendExtendGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= THRESHOLD,
            "PathAppendExtendGM similarity ${"%.2f".format(comparison.similarity)}% < $THRESHOLD%",
        )
    }

    private companion object {
        private const val THRESHOLD: Double = 95.0
    }
}
