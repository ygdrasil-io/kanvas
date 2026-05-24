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
        // This GM is a kerning/faux-style ratchet. The pure Kotlin
        // OpenType backend applies supported `kern` / GPOS pair
        // positioning, but style synthesis still differs from upstream.
        // Ratchet against regressions rather than asserting a hard floor.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("TypefaceStylesKerningGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("TypefaceStylesKerningGM", comparison.similarity)
        assertTrue(accepted, "TypefaceStylesKerningGM regressed below ratchet")
    }
}
