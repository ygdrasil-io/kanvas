package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ComplexClipTest {

    private fun runVariant(gm: ComplexClipGM, label: String, floor: Double = 60.0) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed(label, comparison)
        if (comparison.similarity < floor + 10.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(label, comparison.similarity)
        assertTrue(accepted, "$label regressed below ratchet")
        // Inverse-fill + AA-clip + saveLayer combinations diverge slightly
        // (multiple AA-fill paths over the same pixels). Floors are loose
        // initially; ratchet tightens over time.
        assertTrue(
            comparison.similarity >= floor,
            "$label similarity ${"%.2f".format(comparison.similarity)}% < $floor%",
        )
    }

    @Test fun `complexclip_bw`() = runVariant(ComplexClipBwGM(), "ComplexClipBwGM")
    @Test fun `complexclip_bw_invert`() = runVariant(ComplexClipBwInvertGM(), "ComplexClipBwInvertGM")
    @Test fun `complexclip_bw_layer`() = runVariant(ComplexClipBwLayerGM(), "ComplexClipBwLayerGM")
    @Test fun `complexclip_bw_layer_invert`() =
        runVariant(ComplexClipBwLayerInvertGM(), "ComplexClipBwLayerInvertGM")
    @Test fun `complexclip_aa`() = runVariant(ComplexClipAaGM(), "ComplexClipAaGM")
    @Test fun `complexclip_aa_invert`() = runVariant(ComplexClipAaInvertGM(), "ComplexClipAaInvertGM")
    @Test fun `complexclip_aa_layer`() = runVariant(ComplexClipAaLayerGM(), "ComplexClipAaLayerGM")
    @Test fun `complexclip_aa_layer_invert`() =
        runVariant(ComplexClipAaLayerInvertGM(), "ComplexClipAaLayerInvertGM")
}
