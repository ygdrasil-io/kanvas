package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BigTileImageFilterTest {

    @Test
    fun `BigTileImageFilterGM matches bigtileimagefilter_png within tolerance`() {
        val gm = BigTileImageFilterGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bigtileimagefilter.png")
        // 512×512 canvas dominated by a black background. Two
        // discrepancies vs upstream we *can't* close in this slice :
        //
        //  - The red-circle tile is sampled with linear filtering on
        //    upstream's GPU sampler (the circle's stroke edges anti-alias
        //    to orange-red on a black background), whereas kanvas-skia's
        //    raster sampler bound to the `Image` filter resolves edges
        //    nearer to pure red. Every circle in the 8×8 tile drifts by
        //    ~50/channel on the stroke-vs-background transition pixels.
        //
        //  - The green-stamp block uses
        //    `translate(320,320) + saveLayer(bound2) + setMatrix(I) +
        //    drawImageRect(dst=(320,320,384,384))`. In upstream Skia the
        //    layer device pixels live at canvas-space (320,320,384,384)
        //    so the global-space drawImageRect lands inside the layer.
        //    kanvas-skia's layer-local CTM model places the layer device
        //    at (0,0,64,64) with a CTM pre-translated by -origin, and
        //    `setMatrix(I)` wipes that compensation — the drawImageRect
        //    dst (320,320,…) falls outside the 64×64 layer storage and
        //    the green stamp doesn't appear. This is a kanvas-skia
        //    layer-CTM model gap (not specific to this GM).
        //
        // Tolerance 8 with a 80% similarity floor captures the
        // correctly-tiled red bulk and gates regressions on the
        // 64-circle grid alignment.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("BigTileImageFilterGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BigTileImageFilterGM", comparison.similarity)
        assertTrue(accepted, "BigTileImageFilterGM regressed below ratchet")
        assertTrue(comparison.similarity >= 80.0,
            "BigTileImageFilterGM similarity ${"%.2f".format(comparison.similarity)}% < 80.0% (t=8 floor)")
    }
}
