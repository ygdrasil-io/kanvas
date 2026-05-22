package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Visual regression for [DiscardGM] (port of `discard.cpp::DiscardGM`).
 *
 * Note : because the GM uses [org.skia.tools.SkRandom] internally, the
 * exact mosaic depends on the LCG seed. Our `SkRandom(0)` defaults
 * match upstream's `SkRandom()` (also seed 0), so the rendered output
 * is byte-stable across runs.
 */
class DiscardTest {
    @Test
    fun `DiscardGM matches discard_png within tolerance`() {
        val gm = DiscardGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("DiscardGM", comparison)
        if (comparison.similarity < 75.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DiscardGM", comparison.similarity)
        assertTrue(accepted, "DiscardGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "DiscardGM similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
