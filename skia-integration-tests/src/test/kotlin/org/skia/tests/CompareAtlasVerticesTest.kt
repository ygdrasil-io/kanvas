package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class CompareAtlasVerticesTest {
    @Test
    fun `CompareAtlasVerticesGM matches compare_atlas_vertices_png within tolerance`() {
        val gm = CompareAtlasVerticesGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("CompareAtlasVerticesGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("CompareAtlasVerticesGM", comparison.similarity)
        assertTrue(accepted, "CompareAtlasVerticesGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 5.0,
            "CompareAtlasVerticesGM similarity ${"%.2f".format(comparison.similarity)}% < 5.0% floor",
        )
    }
}
