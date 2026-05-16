package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Visual regression for [SrcModeGM] (port of upstream
 * `gm/srcmode.cpp::SrcModeGM`).
 *
 * 5 procs × 3 blend modes × 2 shaders × 2 AA settings = 60-cell
 * sweep, rendered through an intermediate raster surface (white
 * background) so [org.skia.foundation.SkBlendMode.kClear] writes
 * transparent pixels into the offscreen.
 *
 * **Blend-mode ceiling** — the kanvas-skia rasterizer only honours
 * [org.skia.foundation.SkBlendMode.kSrcOver]; the `kSrc` and `kClear`
 * columns (8 out of every 30-cell block) fall back to `kSrcOver`.
 * That accounts for the bulk of the similarity gap vs the upstream
 * reference, which truly evaluates the per-mode equations. The ratchet
 * floor reflects this — see `SkBlendMode.kt` KDoc for the rasterizer
 * note.
 */
class SrcModeTest {

    @Test
    fun `SrcModeGM matches srcmode_png within tolerance`() {
        val gm = SrcModeGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image srcmode.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("SrcModeGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("SrcModeGM", comparison.similarity)
        assertTrue(accepted, "SrcModeGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 88.3,
            "SrcModeGM similarity ${"%.2f".format(comparison.similarity)}% < 88.3% floor",
        )
    }
}
