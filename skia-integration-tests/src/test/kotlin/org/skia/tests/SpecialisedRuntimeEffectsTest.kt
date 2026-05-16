package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * GM ratchet tests for the D2.4.d specialised one-off ports —
 * `destcolor` (RGB-invert blender), `image_dither` (stretch blender),
 * `kawase_blur_rt` (multipass Kawase blur with two custom shaders).
 *
 * **Floor strategy** — all three GMs use synthetic stand-in
 * images (no `mandrill_*.png` assets available) and the
 * `image_dither` / `kawase_blur_rt` ports skip features
 * (dithering, full DEBUG layout) that contribute most of the
 * upstream visual structure. Floor stays at 0 % ; the
 * [SimilarityTracker] ratchet still detects regressions of any
 * single SkSL effect's math.
 */
class SpecialisedRuntimeEffectsTest {

    private fun runGm(gm: GM, trackerName: String, floor: Double) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed(trackerName, comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(trackerName, comparison.similarity)
        assertTrue(accepted, "$trackerName regressed below tolerance")
        assertTrue(
            comparison.similarity >= floor,
            "$trackerName similarity ${"%.2f".format(comparison.similarity)}% < $floor% floor",
        )
    }

    @Test
    fun `DestColorGM matches reference`() =
        runGm(DestColorGM(), "DestColorGM", floor = 0.0)

    @Test
    fun `ImageDitherGM matches reference`() =
        runGm(ImageDitherGM(), "ImageDitherGM", floor = 0.0)

    @Test
    fun `KawaseBlurRtGM matches reference`() =
        runGm(KawaseBlurRtGM(), "KawaseBlurRtGM", floor = 0.0)
}
