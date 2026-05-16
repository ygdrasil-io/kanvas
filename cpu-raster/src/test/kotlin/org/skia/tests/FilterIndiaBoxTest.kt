package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for [FilterIndiaBoxGM] — the upstream `filterindiabox` GM.
 *
 * Two rows × four samplers (nearest, linear, linear+mipmap, Mitchell
 * cubic) of `images/box.gif` downscaled by `(150/200, 30/55)`. Our
 * raster pipeline doesn't apply mipmap filtering and
 * [org.skia.core.SkCanvas.drawImageRect] drops draws under
 * non-axis-aligned CTMs, so row 2 (rotated 30°) renders blank in our
 * output. Similarity therefore caps well below the no-issue ports.
 */
class FilterIndiaBoxTest {

    @Test
    fun `FilterIndiaBoxGM matches filterindiabox_png within tolerance`() {
        val gm = FilterIndiaBoxGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image filterindiabox.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("FilterIndiaBoxGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("FilterIndiaBoxGM", comparison.similarity)
        assertTrue(accepted, "FilterIndiaBoxGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 40.0,
            "FilterIndiaBoxGM similarity ${"%.2f".format(comparison.similarity)}% < 40.0% floor",
        )
    }
}
