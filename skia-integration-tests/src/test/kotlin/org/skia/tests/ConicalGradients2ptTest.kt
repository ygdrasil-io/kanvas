package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test class for the SkConicalGradient port — exercises the kInside /
 * kOutside / kEdge GM variants from `gm/gradients_2pt_conical.cpp`.
 */
class ConicalGradients2ptTest {

    private fun runGm(gm: org.skia.tests.GM, trackerName: String, floor: Double) {
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

    // ─── Tile-mode variants added in H3 wave 10B ────────────────────

    @Test
    fun `gradients_2pt_conical_inside matches reference`() =
        runGm(ConicalGradients2ptInsideDitherGM(), "ConicalGradients2ptInsideDitherGM", 82.2)

    @Test
    fun `gradients_2pt_conical_inside_repeat matches reference`() =
        runGm(ConicalGradients2ptInsideRepeatGM(), "ConicalGradients2ptInsideRepeatGM", 76.2)

    @Test
    fun `gradients_2pt_conical_inside_mirror matches reference`() =
        runGm(ConicalGradients2ptInsideMirrorGM(), "ConicalGradients2ptInsideMirrorGM", 76.3)

    @Test
    fun `gradients_2pt_conical_outside_repeat matches reference`() =
        runGm(ConicalGradients2ptOutsideRepeatGM(), "ConicalGradients2ptOutsideRepeatGM", 86.8)

    @Test
    fun `gradients_2pt_conical_outside_mirror matches reference`() =
        runGm(ConicalGradients2ptOutsideMirrorGM(), "ConicalGradients2ptOutsideMirrorGM", 86.5)

    @Test
    fun `gradients_2pt_conical_edge_repeat matches reference`() =
        runGm(ConicalGradients2ptEdgeRepeatGM(), "ConicalGradients2ptEdgeRepeatGM", 80.1)

    @Test
    fun `gradients_2pt_conical_edge_mirror matches reference`() =
        runGm(ConicalGradients2ptEdgeMirrorGM(), "ConicalGradients2ptEdgeMirrorGM", 80.2)
}
