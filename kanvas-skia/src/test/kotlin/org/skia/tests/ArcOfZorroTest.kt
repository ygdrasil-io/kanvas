package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ArcOfZorroTest {

    @Test
    fun `ArcOfZorroGM matches arcofzorro_png within tolerance`() {
        val gm = ArcOfZorroGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image arcofzorro.png")
        // 200 stroked arcs at width 35 stress the path stroker (Phase 3c)
        // + arcTo cubic flattening (Phase 3b). Every cell renders a
        // boustrophedon-positioned open arc with default kButt_Cap +
        // kMiter_Join (both supported). Random colours from a bit-compatible
        // SkRandom match the reference per-cell.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ArcOfZorroGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ArcOfZorroGM", comparison.similarity)
        assertTrue(accepted, "ArcOfZorroGM regressed below ratchet")
        assertTrue(comparison.similarity >= 95.0,
            "ArcOfZorroGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% (t=1 floor)")
    }
}
