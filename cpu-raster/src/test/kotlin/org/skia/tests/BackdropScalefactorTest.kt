package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BackdropScalefactorTest {
    @Test
    fun `BackdropScalefactorGM matches backdrop_scalefactor_png within tolerance`() {
        val gm = BackdropScalefactorGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image backdrop_scalefactor.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BackdropScalefactorGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("BackdropScalefactorGM", comparison.similarity)
        assertTrue(accepted, "BackdropScalefactorGM regressed below ratchet")
        assertTrue(comparison.similarity >= EXPECTED_SIMILARITY,
            "BackdropScalefactorGM similarity ${"%.2f".format(comparison.similarity)}% < $EXPECTED_SIMILARITY%")
    }

    private companion object {
        const val EXPECTED_SIMILARITY: Double = 60.0
    }
}
