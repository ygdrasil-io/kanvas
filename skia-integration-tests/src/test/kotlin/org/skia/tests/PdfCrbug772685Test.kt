package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Visual regression for [PdfCrbug772685GM] (port of upstream
 * `gm/pdf_never_embed.cpp::pdf_crbug_772685`).
 *
 * Regression test for https://crbug.com/772685. The GM draws a white
 * canvas via a `drawRect` fully covered by two intersected clips:
 * the output should be entirely white. Any non-white pixel indicates
 * the clip math or the scale/translate sequencing regressed.
 *
 * All primitives (`clipRect`, `translate`, `scale`, `drawRect`) are
 * natively supported in `:kanvas-skia`; no font or resource fallback
 * paths are involved. Similarity should be very high against the
 * upstream reference (which is uniformly white).
 */
class PdfCrbug772685Test {

    @Test
    fun `PdfCrbug772685GM matches pdf_crbug_772685_png within tolerance`() {
        val gm = PdfCrbug772685GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image pdf_crbug_772685.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("PdfCrbug772685GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PdfCrbug772685GM", comparison.similarity)
        assertTrue(accepted, "PdfCrbug772685GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 95.0,
            "PdfCrbug772685GM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% floor",
        )
    }
}
