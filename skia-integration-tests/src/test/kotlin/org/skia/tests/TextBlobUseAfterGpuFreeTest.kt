package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class TextBlobUseAfterGpuFreeTest {

    @Test
    fun `TextBlobUseAfterGpuFreeGM matches textblobuseaftergpufree_png within tolerance`() {
        val gm = TextBlobUseAfterGpuFreeGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image textblobuseaftergpufree.png")

        // Two identical "Hamburgefons" text-blob draws at 20pt on a white
        // background. Most of the canvas is colour-space-invariant white ;
        // glyph ink residuals are dominated by the AWT-vs-FreeType scaler
        // delta on small text (~1-2 ulp on AA edges).
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("TextBlobUseAfterGpuFreeGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TextBlobUseAfterGpuFreeGM", comparison.similarity)
        assertTrue(accepted, "TextBlobUseAfterGpuFreeGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 95.0,
            "TextBlobUseAfterGpuFreeGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% floor",
        )
    }
}
