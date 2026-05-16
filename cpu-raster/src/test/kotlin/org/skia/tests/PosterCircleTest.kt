package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Smoke test for [PosterCircleGM]. The upstream `poster_circle.png`
 * captures the static `fTime = 0` pose; our raster backend flattens the
 * `concat(SkM44)` perspective to its 3×3 projection via `asM33`, so
 * Y-rotated posters land at their projected screen positions but
 * without the depth-driven shading the GPU pipeline applies. The
 * resulting similarity floor reflects that approximation.
 */
class PosterCircleTest {
    @Test
    fun `PosterCircleGM matches poster_circle_png within tolerance`() {
        val gm = PosterCircleGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image poster_circle.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("PosterCircleGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("PosterCircleGM", comparison.similarity)
        assertTrue(accepted, "PosterCircleGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= EXPECTED_SIMILARITY,
            "PosterCircleGM similarity ${"%.2f".format(comparison.similarity)}% < $EXPECTED_SIMILARITY%",
        )
    }

    private companion object {
        const val EXPECTED_SIMILARITY: Double = 36.8
    }
}
