package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class CopyTo4444Test {

    @Test
    fun `CopyTo4444GM matches copyTo4444_png within tolerance`() {
        val gm = CopyTo4444GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image copyTo4444.png")

        // Right-half is an 8888 → 4444 colourtype conversion. Our
        // backing 4444 storage doesn't dither during the conversion
        // (upstream uses SkRasterPipeline's auto-dither on this code
        // path), so banding in the dog's gradient background drives
        // sub-pixel deltas vs reference. Tolerance kept loose (=12).
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 12)
        TestReport.recordDetailed("CopyTo4444GM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("CopyTo4444GM", comparison.similarity)
        assertTrue(accepted, "CopyTo4444GM regressed below ratchet")
    }
}
