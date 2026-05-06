package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Visual regression for [ColorMatrixGM] (port of upstream
 * `gm/colormatrix.cpp::ColorMatrixGM`).
 *
 * **Validation milestone for Phase 7a (Group A — effects pipeline
 * foundation).** The 6-column × 2-row grid stresses the
 * [org.skia.foundation.SkColorFilter] family end-to-end :
 *
 *  - [org.skia.foundation.SkColorFilters.Matrix] applied via
 *    `paint.colorFilter` on `drawImage` (the per-sampled-pixel filter
 *    application path I wired in this slice).
 *  - Matrix variants : identity, saturation 0/0.5/1/2, "red → alpha".
 *  - Two source bitmaps : opaque RG-gradient + alpha-gradient
 *    (transparent → opaque). The latter validates that the filter
 *    correctly handles transparent input pixels.
 *
 * If this passes, the `paint.colorFilter` integration is end-to-end
 * correct on the `drawImage` path.
 *
 * **Floor 65 %** — Phase 7e bumped this from the original 49 %
 * baseline (Phase 7a) to 69 % by re-ordering the pipeline so the
 * colour filter runs in sRGB *before* the working-space xform :
 * `paint.color → colorFilter (sRGB) → xform → blend`. The Rec.709
 * luma weights baked into `saturationMatrix` therefore measure
 * luminance in the space they were tuned for. The residual ~30 %
 * gap vs upstream is the encoded-vs-linear-sRGB gamma curve
 * difference (Skia evaluates the matrix in linear sRGB ; we still
 * apply it in encoded sRGB). Closing that gap is a Phase 7e' refinement.
 */
class ColorMatrixTest {

    @Test
    fun `ColorMatrixGM matches colormatrix_png within tolerance`() {
        val gm = ColorMatrixGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ColorMatrixGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ColorMatrixGM", comparison.similarity)
        assertTrue(accepted, "ColorMatrixGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 65.0,
            "ColorMatrixGM similarity ${"%.2f".format(comparison.similarity)}% < 65% floor",
        )
    }
}
