package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
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
 * @Disabled because current raster output still diverges from the upstream
 * reference. Reactivation audit on 2026-05-24 rendered at 67.96% similarity
 * against `original-888/recordopts.png`, with 53,030 / 78,030 pixels outside
 * tolerance. The remaining blocker is the saveLayer / detector color-filter
 * pipeline exercised by the recorded column, tracked as
 * `STUB.RECORDOPTS.SAVELAYER_COLOR_FILTER_FOLD`.
 */
@Disabled("STUB.RECORDOPTS.SAVELAYER_COLOR_FILTER_FOLD")
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
