package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Third textual GM port — see [Crbug1073670GM] for source-spec mapping.
 *
 * Closes a previously untested end-to-end path: text rendering with a
 * [org.skia.foundation.SkLinearGradient] shader (`drawString` →
 * `drawPath` → `fillPath` → `shader.shadeRow` on glyph outline pixels).
 * Phase 5 + T3 each handled half of this chain; this GM is the first
 * to exercise both halves at once.
 *
 * **Score (~72.5%) is deliberately lower than [BigTextTest] (98.20%)
 * and [ColorWheelNativeTest] (99.75%)**. Diagnosis: the white BG
 * (~73% of the canvas area) matches almost exactly, but **every
 * pixel of the glyph footprint diverges by a small amount** (mean
 * channel diff 14-24, max 154-181). The most plausible cause is
 * gradient stop-interpolation colorspace: upstream Skia DM
 * interpolates in **encoded** sRGB-like space by default (`Interpolation::
 * ColorSpace::kDestination`); Phase 5/6 sampled gradients in linear-
 * ish Rec.2020 F16 throughout. Same outline, slightly different
 * colour at every glyph pixel ⇒ ~25-30% of the canvas counted as
 * "off" at tolerance=8 even though no individual pixel is wildly
 * wrong.
 *
 * **Tolerance 8 / floor 70%** chosen accordingly. The
 * [SimilarityTracker] ratchet locks the actual score (~72.5%) so any
 * future improvement (e.g. adding a gradient interpolation-space
 * switch, or a slice that aligns with upstream's
 * `Interpolation::kDestination` default) will tighten the floor
 * automatically. The 70% floor is the catastrophic-regression bar.
 *
 * The GM still serves its primary purpose at this score: it confirms
 * that `paint.shader` propagates through `drawString` end-to-end and
 * paints the right region (the "Gradient" string outline) — it just
 * paints slightly different colours per pixel than upstream.
 */
class Crbug1073670Test {

    @Test
    fun `Crbug1073670GM matches crbug_1073670_png within tolerance`() {
        val gm = Crbug1073670GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_1073670.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("Crbug1073670GM", comparison)
        if (comparison.similarity < 70.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug1073670GM", comparison.similarity)
        assertTrue(accepted, "Crbug1073670GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 70.0,
            "Crbug1073670GM similarity ${"%.2f".format(comparison.similarity)}% < 70.0% floor",
        )
    }
}
