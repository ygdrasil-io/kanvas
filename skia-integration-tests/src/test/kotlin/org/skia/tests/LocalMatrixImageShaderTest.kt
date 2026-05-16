package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LocalMatrixImageShaderTest {

    @Test
    fun `LocalMatrixImageShaderGM matches localmatriximageshader_png within tolerance`() {
        val gm = LocalMatrixImageShaderGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image localmatriximageshader.png")
        // Four overlapping shader-on-rect draws : one red & one blue image
        // shader, each pair drawn twice (with / without the right-side
        // translate). The composed local matrices `translate · rotate`
        // vs `rotate · translate` reproduce upstream's
        // `makeWithLocalMatrix(...) ∘ makeShader(...)` pipeline.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("LocalMatrixImageShaderGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LocalMatrixImageShaderGM", comparison.similarity)
        assertTrue(accepted, "LocalMatrixImageShaderGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 99.5,
            "LocalMatrixImageShaderGM similarity ${"%.2f".format(comparison.similarity)}% < 99.5% floor",
        )
    }
}
