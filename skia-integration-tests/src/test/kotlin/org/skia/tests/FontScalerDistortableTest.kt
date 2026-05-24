package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * R-final.9 — variable-font test. The pure Kotlin OpenType backend
 * supports the simple-glyph subset of `gvar`, but this GM still tracks
 * remaining variable-outline interpolation gaps against the upstream
 * FreeType reference. The test floor is conservative: the structural
 * layout still matches.
 */
class FontScalerDistortableTest {

    @Test
    fun `FontScalerDistortableGM matches fontscalerdistortable_png within tolerance`() {
        val gm = FontScalerDistortableGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image fontscalerdistortable.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("FontScalerDistortableGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("FontScalerDistortableGM", comparison.similarity)
        assertTrue(accepted, "FontScalerDistortableGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= EXPECTED_SIMILARITY,
            "FontScalerDistortableGM similarity ${"%.2f".format(comparison.similarity)}% < $EXPECTED_SIMILARITY%",
        )
    }

    private companion object {
        // Variable-outline interpolation is not yet FreeType-complete.
        // Keep the hard floor low enough for the current OpenType
        // backend while the SimilarityTracker ratchets real results.
        const val EXPECTED_SIMILARITY: Double = 75.0
    }
}
