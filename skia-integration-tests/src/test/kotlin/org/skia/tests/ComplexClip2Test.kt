package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ComplexClip2Test {

    private fun runFor(clip: ComplexClip2GM.Clip, aa: Boolean, trackerSuffix: String, floor: Double) {
        val gm = ComplexClip2GM(clip, aa)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        val tracker = "ComplexClip2_$trackerSuffix"
        TestReport.recordDetailed(tracker, comparison)
        if (comparison.similarity < 70.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(tracker, comparison.similarity)
        assertTrue(accepted, "$tracker regressed below ratchet")
        assertTrue(
            comparison.similarity >= floor,
            "$tracker similarity ${"%.2f".format(comparison.similarity)}% < $floor% floor",
        )
    }

    @Test
    fun `ComplexClip2 rect bw matches reference`() =
        runFor(ComplexClip2GM.Clip.kRect_Clip, false, "rect_bw", 30.0)

    @Test
    fun `ComplexClip2 rect aa matches reference`() =
        runFor(ComplexClip2GM.Clip.kRect_Clip, true, "rect_aa", 30.0)

    @Test
    fun `ComplexClip2 rrect bw matches reference`() =
        runFor(ComplexClip2GM.Clip.kRRect_Clip, false, "rrect_bw", 30.0)

    @Test
    fun `ComplexClip2 rrect aa matches reference`() =
        runFor(ComplexClip2GM.Clip.kRRect_Clip, true, "rrect_aa", 30.0)

    @Test
    fun `ComplexClip2 path bw matches reference`() =
        runFor(ComplexClip2GM.Clip.kPath_Clip, false, "path_bw", 30.0)

    @Test
    fun `ComplexClip2 path aa matches reference`() =
        runFor(ComplexClip2GM.Clip.kPath_Clip, true, "path_aa", 30.0)
}
