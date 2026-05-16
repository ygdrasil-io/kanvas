package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Visual regression for [PathEffectGM] (port of upstream
 * `gm/patheffects.cpp::PathEffectGM`).
 *
 * **Validation milestone for the SkPathEffect family.** The 5 + 3
 * cell layout exercises every concrete pathEffect shipped across
 * Phase 7p (Dash) / Phase 7p2 (Corner + Discrete) / Phase 7p3
 * (MakeCompose) / Phase 7p_t (1D + 2D).
 *
 * **Floor 40 %** — first ratchet target. Residual gap vs upstream :
 *  - 1D path effect uses [SkPath1DPathEffect.Style.kRotate] (we
 *    don't ship `kMorph` yet — Skia's GM uses kRotate so this
 *    matches).
 *  - 2D path effect tiles a circle stamp ; positioning matches Skia
 *    upstream's `MakeScale(12, 12)` × circle(5) basis.
 *  - Corner smoothing of dashed paths : our [SkCornerPathEffect] is
 *    line-only ; the closing arc segments of dash stipples skip the
 *    smoothing step, leaving sharp dash endpoints. Visually-close
 *    but quantifiable.
 */
class PathEffectTest {

    @Test
    fun `PathEffectGM matches patheffect_png within tolerance`() {
        val gm = PathEffectGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("PathEffectGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PathEffectGM", comparison.similarity)
        assertTrue(accepted, "PathEffectGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 40.0,
            "PathEffectGM similarity ${"%.2f".format(comparison.similarity)}% < 40% floor",
        )
    }
}
