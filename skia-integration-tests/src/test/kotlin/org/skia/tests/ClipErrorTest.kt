package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * GM port — see [ClipErrorGM] for source-spec mapping.
 *
 * Validates that glyphs whose pre-blur bounding box exceeds the
 * atlas size still translate + clip correctly. Two stacked
 * 256-px-text rows, each clipped to a sub-region and drawn first
 * blurred (sigma ≈ 29.4) then crisp.
 *
 * Floor 60% — the blurred halo's per-pixel colour profile diverges
 * mildly from upstream because our blur kernel summing scheme
 * differs by sub-pixel offsets, even though the AA mask + clip
 * outcomes coincide structurally. The crisp pass also leans on the
 * OpenType-backed scaler which carries the usual edge drift.
 */
class ClipErrorTest {

    @Test
    fun `ClipErrorGM matches cliperror_png within tolerance`() {
        val gm = ClipErrorGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image cliperror.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ClipErrorGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ClipErrorGM", comparison.similarity)
        assertTrue(accepted, "ClipErrorGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 60.0,
            "ClipErrorGM similarity ${"%.2f".format(comparison.similarity)}% < 60.0% floor",
        )
    }
}
