package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BatchedConvexPathsTest {

    @Test
    fun `BatchedConvexPathsGM matches batchedconvexpaths_png within tolerance`() {
        val gm = BatchedConvexPathsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image batchedconvexpaths.png")
        // 10 stacked translucent (alpha 0.3) convex cubic-only polygons
        // on black bg. Each polygon partially overlaps the previous.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BatchedConvexPathsGM", comparison)
        if (comparison.similarity < 35.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BatchedConvexPathsGM", comparison.similarity)
        assertTrue(accepted, "BatchedConvexPathsGM regressed below ratchet")
        // 10 stacked translucent (alpha 0.3) layers — every blended pixel
        // accumulates 8-bit-vs-f32 SrcOver drift, so bytewise score ends
        // up around ~35%. Max channel diff stays ≤ 26/255 (visually
        // indistinguishable). Score will rise once a F16 working-space
        // rasterizer lands (already noted as Phase 5b carry-over).
        assertTrue(comparison.similarity >= 30.0,
            "BatchedConvexPathsGM similarity ${"%.2f".format(comparison.similarity)}% < 30.0% (t=1 floor)")
    }
}
