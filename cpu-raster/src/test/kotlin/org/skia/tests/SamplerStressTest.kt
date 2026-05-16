package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Visual regression for [SamplerStressGM] (port of upstream
 * `gm/samplerstress.cpp::SamplerStressGM`, exposed as
 * `gpusamplerstress`).
 *
 * Renders a single 72 px `"M"` glyph through the full filter stack —
 * a 16×16 repeating bitmap shader (red/green stripes on black) plus a
 * σ = 1 normal-blur mask filter, clipped by an AA round-rect — then
 * overlays the same glyph + clip outline in skeleton form.
 *
 * Originally a GPU pipeline stress on Skia; on the raster backend it
 * still exercises [org.skia.foundation.SkBitmap.makeShader] with
 * [org.skia.foundation.SkTileMode.kRepeat],
 * [org.skia.foundation.SkBlurMaskFilter] under
 * [org.skia.foundation.SkBlurStyle.kNormal], and AA `clipPath`
 * together — three independent samplers in the same paint.
 */
class SamplerStressTest {

    @Test
    fun `SamplerStressGM matches gpusamplerstress_png within tolerance`() {
        val gm = SamplerStressGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image gpusamplerstress.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("SamplerStressGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("SamplerStressGM", comparison.similarity)
        assertTrue(accepted, "SamplerStressGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 99.5,
            "SamplerStressGM similarity ${"%.2f".format(comparison.similarity)}% < 99.5% floor",
        )
    }
}
