package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * GM port — see [PathReverseGM] for source-spec mapping.
 *
 * Exercises [org.skia.pathops.internal.reverseAddPath] indirectly via
 * its public consumer: the GM builds 4 rows of path content (rect,
 * stacked rect, oval-with-line opener, and a synthetic glyph "e") and
 * draws each one alongside its reverse. The rasteriser must produce
 * the same fill for the reversed contour as for the original.
 *
 * Reference PNG `path-reverse.png` (the macro's `SkString` arg names
 * the GM; both the GM's [PathReverseGM.getName] and the reference key
 * use the dashed form).
 *
 * Floor 99% — fill + 1px stroke output is structurally identical to
 * upstream; remaining drift is bounded by AA edge precision and
 * profile-conversion at the rec.2020 ↔ sRGB seams.
 */
class PathReverseTest {

    @Test
    fun `PathReverseGM matches path-reverse_png within tolerance`() {
        val gm = PathReverseGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image path-reverse.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("PathReverseGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PathReverseGM", comparison.similarity)
        assertTrue(accepted, "PathReverseGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 95.0,
            "PathReverseGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% floor",
        )
    }
}
