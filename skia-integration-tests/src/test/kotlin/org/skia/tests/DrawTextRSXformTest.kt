package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for [DrawTextRSXformGM].
 * Disabled: [SkTextBlob.MakeFromRSXformGlyphs] is a STUB.RSXBLOB that
 * throws NotImplementedError at runtime; the GM body calls it so
 * execution cannot proceed.
 */
@Disabled("STUB.RSXBLOB")
class DrawTextRSXformTest {
    @Test
    fun `DrawTextRSXformGM matches drawTextRSXform_png within tolerance`() {
        val gm = DrawTextRSXformGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DrawTextRSXformGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DrawTextRSXformGM", comparison.similarity)
        assertTrue(accepted, "DrawTextRSXformGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "DrawTextRSXformGM similarity ${"%.2f".format(comparison.similarity)}% < 30.0% floor",
        )
    }
}
