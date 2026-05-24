package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for [FlippityGM] — ratchets the rendered output against the
 * upstream reference `original-888/flippity.png`.
 *
 * The upstream GM is a GPU-test in spirit (the `kBottomLeft` vs
 * `kTopLeft` `GrSurfaceOrigin` axis is what makes rows 1 / 2 differ on
 * Ganesh), but its raster sink — which is what produced the shipped
 * reference PNG — collapses both rows onto a single labelled
 * `RasterFromBitmap` image. We mirror that behaviour, so the kanvas-skia
 * render compares directly to the reference.
 *
 * Tolerance is widened to 8 to absorb the residual drift between our
 * OpenType-backed text rasteriser (label glyphs) and upstream's libfreetype
 * path — the labels carry small advance-width / hinting differences
 * that don't affect the matrix-driven image-orientation invariants
 * the GM is actually probing.
 */
class FlippityTest {

    @Test
    fun `FlippityGM matches flippity within label-glyph tolerance`() {
        val gm = FlippityGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image flippity.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("FlippityGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("FlippityGM", comparison.similarity)
        assertTrue(accepted, "FlippityGM regressed below ratchet")
    }
}
