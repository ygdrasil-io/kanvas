package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for [DrawQuadSetGM] — upstream `draw_quad_set` GM.
 *
 * The reference image is the GPU (Ganesh) render. The gradient tile columns
 * still differ from the upstream CPU fallback used by this raster port, but
 * the residual is localized and the detailed comparison stays above the
 * ratcheted floor.
 *
 * The solid-colour columns ("Green", "Multicolor") do match the reference;
 * the overall GM still exercises [org.skia.core.SkCanvas.experimental_DrawEdgeAAQuad]
 * under all five CTMs.
 */
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
