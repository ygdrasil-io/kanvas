package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LocalMatrixShaderTest {

    @Test
    fun `LocalMatrixShaderGM matches localmatrixshader_nested_png within tolerance`() {
        val gm = LocalMatrixShaderGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image localmatrixshader_nested.png")
        // Four factory variants drawn in three columns. Each variant
        // exercises a different shader topology that R-final.2 folding
        // collapses to the same effective `outer · inner` local
        // matrix. Tolerance generous (8) — large bitmap with axis-
        // aligned scaling, dominant per-pixel drift is the AA edge
        // around the green circle.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("LocalMatrixShaderGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LocalMatrixShaderGM", comparison.similarity)
        assertTrue(accepted, "LocalMatrixShaderGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "LocalMatrixShaderGM similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
