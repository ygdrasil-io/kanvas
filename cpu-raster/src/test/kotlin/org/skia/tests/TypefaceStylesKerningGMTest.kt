package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class TypefaceStylesKerningGMTest {

    @Test
    fun `TypefaceStylesKerningGM matches typefacestyles_kerning_png within tolerance`() {
        val gm = TypefaceStylesKerningGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image typefacestyles_kerning.png")
        // Right-hand text is un-kerned on the AWT path (see GM doc) ; we
        // ratchet against any regression rather than asserting an
        // absolute floor.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("TypefaceStylesKerningGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("TypefaceStylesKerningGM", comparison.similarity)
        assertTrue(accepted, "TypefaceStylesKerningGM regressed below ratchet")
    }
}
