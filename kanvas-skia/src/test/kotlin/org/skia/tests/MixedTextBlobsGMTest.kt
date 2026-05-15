package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class MixedTextBlobsGMTest {

    @Test
    fun `MixedTextBlobsGM matches mixedtextblobs_png within tolerance`() {
        val gm = MixedTextBlobsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image mixedtextblobs.png")
        // Ratchet against any regression — the reference contains colour
        // emoji glyphs that we render as missing/.notdef, so absolute
        // similarity will be low ; we still want to catch drift.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("MixedTextBlobsGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("MixedTextBlobsGM", comparison.similarity)
        assertTrue(accepted, "MixedTextBlobsGM regressed below ratchet")
    }
}
