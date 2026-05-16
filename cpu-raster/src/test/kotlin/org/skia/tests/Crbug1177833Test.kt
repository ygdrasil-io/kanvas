package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for [Crbug1177833GM] — upstream `crbug_1177833` GM.
 *
 * Three quads dumped verbatim from SkiaRenderer (bit-exact
 * `Float.fromBits` constants), each calling
 * `experimental_DrawEdgeAAQuad` with a single-edge AA flag (so the
 * CPU back-end shortcuts to non-AA per
 * [org.skia.core.QuadAAFlags] semantics). Almost the entire canvas
 * is the black background ; the visible quads are 1–2-px-wide slivers,
 * so the similarity baseline is high.
 */
class Crbug1177833Test {

    @Test
    fun `Crbug1177833GM matches crbug_1177833_png within tolerance`() {
        val gm = Crbug1177833GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_1177833.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("Crbug1177833GM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug1177833GM", comparison.similarity)
        assertTrue(accepted, "Crbug1177833GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 85.0,
            "Crbug1177833GM similarity ${"%.2f".format(comparison.similarity)}% < 85.0% floor",
        )
    }
}
