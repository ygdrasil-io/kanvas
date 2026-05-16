package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Visual regression for [Poly2PolyGM] (port of upstream
 * `gm/poly2poly.cpp::Poly2PolyGM`).
 *
 * The 4-cell layout exercises [org.graphiks.math.SkMatrix.MakePolyToPoly]
 * for 1 / 2 / 3 / 4-point fits. The translate, rotate-scale, and
 * affine cells are pixel-faithful; the 4-point perspective cell rides
 * on [org.skia.core.SkCanvas.concat] applying a true projective matrix
 * to the gray frame + diagonals.
 *
 * **Glyph divergence** — upstream injects glyph ID 3 from a custom
 * `fonts/Em.ttf` resource that renders as a stylised square "X". That
 * resource isn't bundled here; we fall through to
 * [org.skia.tools.ToolUtils.DefaultPortableTypeface] and draw the
 * ASCII letter "X". The red glyph silhouette therefore differs in
 * stroke profile across the 4 cells while the surrounding gray
 * geometry stays faithful.
 */
class Poly2PolyTest {

    @Test
    fun `Poly2PolyGM matches poly2poly_png within tolerance`() {
        val gm = Poly2PolyGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image poly2poly.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Poly2PolyGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Poly2PolyGM", comparison.similarity)
        assertTrue(accepted, "Poly2PolyGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 99.3,
            "Poly2PolyGM similarity ${"%.2f".format(comparison.similarity)}% < 99.3% floor",
        )
    }
}
