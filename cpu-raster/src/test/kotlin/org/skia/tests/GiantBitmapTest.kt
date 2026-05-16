package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class GiantBitmapTest {

    private fun runGm(gm: GM, trackerName: String, floor: Double = 5.0) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed(trackerName, comparison)
        if (comparison.similarity < 50.0) {
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
    fun `GiantBitmapClampPointScale matches reference`() =
        runGm(GiantBitmapClampPointScale(), "GiantBitmapClampPointScale")

    @Test
    fun `GiantBitmapRepeatPointScale matches reference`() =
        runGm(GiantBitmapRepeatPointScale(), "GiantBitmapRepeatPointScale")

    @Test
    fun `GiantBitmapMirrorPointScale matches reference`() =
        runGm(GiantBitmapMirrorPointScale(), "GiantBitmapMirrorPointScale")

    @Test
    fun `GiantBitmapClampBilerpScale matches reference`() =
        runGm(GiantBitmapClampBilerpScale(), "GiantBitmapClampBilerpScale")

    @Test
    fun `GiantBitmapRepeatBilerpScale matches reference`() =
        runGm(GiantBitmapRepeatBilerpScale(), "GiantBitmapRepeatBilerpScale")

    @Test
    fun `GiantBitmapMirrorBilerpScale matches reference`() =
        runGm(GiantBitmapMirrorBilerpScale(), "GiantBitmapMirrorBilerpScale")

    @Test
    fun `GiantBitmapClampPointRotate matches reference`() =
        runGm(GiantBitmapClampPointRotate(), "GiantBitmapClampPointRotate")

    @Test
    fun `GiantBitmapRepeatPointRotate matches reference`() =
        runGm(GiantBitmapRepeatPointRotate(), "GiantBitmapRepeatPointRotate")

    @Test
    fun `GiantBitmapMirrorPointRotate matches reference`() =
        runGm(GiantBitmapMirrorPointRotate(), "GiantBitmapMirrorPointRotate")

    @Test
    fun `GiantBitmapClampBilerpRotate matches reference`() =
        runGm(GiantBitmapClampBilerpRotate(), "GiantBitmapClampBilerpRotate")

    @Test
    fun `GiantBitmapRepeatBilerpRotate matches reference`() =
        runGm(GiantBitmapRepeatBilerpRotate(), "GiantBitmapRepeatBilerpRotate")

    @Test
    fun `GiantBitmapMirrorBilerpRotate matches reference`() =
        runGm(GiantBitmapMirrorBilerpRotate(), "GiantBitmapMirrorBilerpRotate")
}
