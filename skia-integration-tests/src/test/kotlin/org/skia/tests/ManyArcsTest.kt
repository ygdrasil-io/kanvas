package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Regression coverage for huge-sweep [SkPathBuilder.arcTo] inputs. The
 * upstream GM includes sweeps up to `3934723942837.3 × 180° ≈ 7.08e14°`,
 * which must be normalised before conic decomposition instead of allocating
 * one segment per raw 90° slice.
 */
class ManyArcsTest {
    @Test
    fun `ManyArcsGM matches manyarcs_png within tolerance`() {
        val gm = ManyArcsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image manyarcs.png")
        // 160 html-canvas-style stroked arc paths spanning degenerate
        // sweeps (0, ±1e-6) up through multi-turn winds (4.3 × 180°,
        // 3.93e12 × 180°). Stresses SkPathBuilder.arcTo's sweep-mod-360
        // + cubic emitter on the limits.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ManyArcsGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ManyArcsGM", comparison.similarity)
        assertTrue(accepted, "ManyArcsGM regressed below ratchet")
        assertTrue(comparison.similarity >= 85.0,
            "ManyArcsGM similarity ${"%.2f".format(comparison.similarity)}% < 85.0%")
    }
}
