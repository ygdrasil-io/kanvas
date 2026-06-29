package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Tests for the R-final.5 [OrientationGM] family.
 *
 * **R-final.8 EXIF fix.** The JPEG codec now parses
 * the EXIF Orientation tag (0x0112) and applies the corresponding
 * rotation/flip to the decoded pixels via
 * [org.skia.utils.PixmapUtils.Orient]. The tests retain a permissive
 * `floor` parameter so the [SimilarityTracker] ratchet drives the
 * monotonic improvement record — the fix is expected to lift both GMs
 * well above the pre-R-final.8 19.87 % baseline.
 */
class OrientationTest {

    private fun runGm(gm: GM, trackerName: String, floor: Double = 1.0) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed(trackerName, comparison)
        if (comparison.similarity < 30.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(trackerName, comparison.similarity)
        assertTrue(accepted, "$trackerName regressed below ratchet")
        assertTrue(
            comparison.similarity >= floor,
            "$trackerName similarity ${"%.2f".format(comparison.similarity)}% < $floor% floor",
        )
    }

    @Test
    fun `Orientation444GM matches orientation_444_png within tolerance`() =
        runGm(Orientation444GM(), "Orientation444GM")

    @Test
    fun `RespectOrientationJpegGM matches respect_orientation_jpeg_png within tolerance`() =
        runGm(RespectOrientationJpegGM(), "RespectOrientationJpegGM")
}
