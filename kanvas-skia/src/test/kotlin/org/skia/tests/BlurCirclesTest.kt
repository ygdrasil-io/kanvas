package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Visual regression for [BlurCirclesGM] (port of upstream
 * `gm/blurcircles.cpp::BlurCirclesGM`).
 *
 * **Validation milestone for Phase 7c ([SkBlurMaskFilter]).** The
 * 4 × 4 grid of `drawCircle` + Gaussian-blur exercises the full
 * mask-filter pipeline (`rasterise to alpha mask → separable
 * Gaussian blur → composite tinted mask onto device`) end-to-end,
 * across 4 sigma values and 4 circle sizes with a per-cell rotation.
 *
 * **Floor 50 %** — first ratchet target. The dominant residual gap
 * vs the upstream reference is :
 *   - The 1D Gaussian kernel uses [SkBlurMaskFilter]'s simple
 *     `ceil(3σ)` clamp, while Skia upstream evaluates the analytic
 *     2D Gaussian convolution via a separable + edge-aware integral
 *     image trick (`SkBlurMask` proper). The kanvas-skia kernel is
 *     ~1 % off in mass conservation for small-σ cells.
 *   - Sigma rounding : we round the 1D kernel coefficients to 32-bit
 *     float ; Skia uses higher precision in the integral pass.
 *
 * Both gaps are visually subliminal but accumulate across the 16
 * cells. Closing them is a Phase 7c' refinement.
 */
class BlurCirclesTest {

    @Test
    fun `BlurCirclesGM matches blurcircles_png within tolerance`() {
        val gm = BlurCirclesGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BlurCirclesGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BlurCirclesGM", comparison.similarity)
        assertTrue(accepted, "BlurCirclesGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 50.0,
            "BlurCirclesGM similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
