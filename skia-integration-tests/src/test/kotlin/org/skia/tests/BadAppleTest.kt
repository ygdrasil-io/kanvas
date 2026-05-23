package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Cross-backend test for [BadAppleGM] (upstream `gm/fontregen.cpp::BadAppleGM`).
 *
 * Renders the two large-glyph blobs (`"Meet"` at 256pt, `"iPad Pro"` at
 * 256pt) and compares against the upstream `badapple.png` reference.
 * Raster downgrades `kSubpixelAntiAlias` to `kAntiAlias` per
 * `MIGRATION_PLAN_TEXT.md §R3`, so a ~2-3 ulp residual on glyph edges
 * is expected ; the ratchet absorbs it via [SimilarityTracker].
 */
class BadAppleTest {

    @Test
    fun `BadAppleGM matches badapple_png within tolerance`() {
        val gm = BadAppleGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image badapple.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("BadAppleGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BadAppleGM", comparison.similarity)
        assertTrue(accepted, "BadAppleGM regressed below ratchet")
    }
}
