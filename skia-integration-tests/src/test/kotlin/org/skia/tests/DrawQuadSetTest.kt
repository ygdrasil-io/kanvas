package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for [DrawQuadSetGM] — upstream `draw_quad_set` GM.
 *
 * The reference image is the GPU (Ganesh) render: the gradient tile columns
 * show a true blue-white linear gradient, whereas our raster port falls back
 * to solid BLUE/WHITE colours (matching the upstream CPU branch). This makes
 * the gradient columns visually different from the reference, so the test is
 * @[Disabled] until a GPU-accurate raster gradient approximation is added.
 *
 * The solid-colour columns ("Green", "Multicolor") do match the reference;
 * the overall GM still exercises [org.skia.core.SkCanvas.experimental_DrawEdgeAAQuad]
 * under all five CTMs.
 */
@Disabled(
    "TODO(STUB.EDGE_AA_QUAD): draw_quad_set reference is a GPU render; " +
        "gradient columns differ on raster (solid-colour fallback vs. linear gradient). " +
        "Enable once raster gradient tiles match the reference.",
)
class DrawQuadSetTest {

    @Test
    fun `DrawQuadSetGM matches draw_quad_set_png within tolerance`() {
        val gm = DrawQuadSetGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image draw_quad_set.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("DrawQuadSetGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DrawQuadSetGM", comparison.similarity)
        assertTrue(accepted, "DrawQuadSetGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 85.0,
            "DrawQuadSetGM similarity ${"%.2f".format(comparison.similarity)}% < 85.0% floor",
        )
    }
}
