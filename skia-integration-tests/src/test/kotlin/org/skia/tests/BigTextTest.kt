package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * First textual GM port — see [BigTextGM] for the source-spec mapping.
 *
 * **Tolerance choice (= 8)**: the pure Kotlin OpenType scaler and
 * upstream FreeType still differ in hinting/AA details by a few ulps on
 * glyph edges. With Liberation TTFs feeding both rasterisers, the
 * **outline shape** should stay aligned; tolerance 8 absorbs bordering
 * pixel drift.
 *
 * **Floor (= 95%)**: actual measured similarity is ~98.2%, well above
 * the floor. We pin 95% to match the convention used by axis-aligned
 * rect/path GMs, leaving buffer for platform and scaler drift. The
 * [SimilarityTracker] ratchet locks the real number
 * day-to-day — 95% is just the hard catastrophic-regression bar.
 */
class BigTextTest {

    @Test
    fun `BigTextGM matches bigtext_png within tolerance`() {
        val gm = BigTextGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bigtext.png")

        // Default tolerance for textual GMs using OpenType-vs-FreeType references.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("BigTextGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BigTextGM", comparison.similarity)
        assertTrue(accepted, "BigTextGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 95.0,
            "BigTextGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% floor",
        )
    }
}
