package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for [UnpremulGM] — the upstream `unpremul` GM.
 *
 * `MarkGMGood` overlays use a `saveLayer` + alpha=0x50 paint, which
 * exercises the layered compositing path. Tolerance picked to absorb
 * AA-edge drift on the green-circle + checkmark glyphs ; both panels
 * are correct-by-construction in our pipeline (see GM KDoc).
 */
class UnpremulTest {

    @Test
    fun `UnpremulGM matches unpremul_png within tolerance`() {
        val gm = UnpremulGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image unpremul.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("UnpremulGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("UnpremulGM", comparison.similarity)
        assertTrue(accepted, "UnpremulGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 91.7,
            "UnpremulGM similarity ${"%.2f".format(comparison.similarity)}% < 91.7% (t=8 floor)",
        )
    }
}
