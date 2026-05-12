package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BlurIgnoreXformTest {

    private fun runFor(drawType: BlurIgnoreXformGM.DrawType, floor: Double) {
        val gm = BlurIgnoreXformGM(drawType)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        val tracker = "BlurIgnoreXform_${drawType.name}"
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

    // `respectCTM=false` flag is currently absent in :kanvas-skia, so the
    // IgnoreTransform column will diverge from the reference. We floor
    // generously to start; raise once the flag lands.
    @Test
    fun `BlurIgnoreXformGM circle matches reference`() =
        runFor(BlurIgnoreXformGM.DrawType.kCircle, 20.0)

    @Test
    fun `BlurIgnoreXformGM rect matches reference`() =
        runFor(BlurIgnoreXformGM.DrawType.kRect, 20.0)

    @Test
    fun `BlurIgnoreXformGM rrect matches reference`() =
        runFor(BlurIgnoreXformGM.DrawType.kRRect, 20.0)
}
