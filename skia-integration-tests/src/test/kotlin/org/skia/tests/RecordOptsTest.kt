package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Visual regression test for [RecordOptsGM] (`recordopts`).
 *
 * Exercises SkPicture recording optimization: the left column of each row
 * draws directly onto the canvas; the right column records the same sequence
 * into an [org.skia.core.SkPictureRecorder] and replays it. Both columns must
 * produce identical pixels.
 *
 * Reactivated after fixing the saveLayer / detector color-filter path:
 * rect draws now honour image filters, picture image filters preserve the
 * layer working format, and color-filter image filters preserve source image
 * color-space metadata.
 */
class RecordOptsTest {

    @Test
    fun `RecordOptsGM matches recordopts_png within tolerance`() {
        val gm = RecordOptsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image recordopts.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 2)
        TestReport.recordDetailed("RecordOptsGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RecordOptsGM", comparison.similarity)
        assertTrue(accepted, "RecordOptsGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 90.0,
            "RecordOptsGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0% floor",
        )
    }
}
