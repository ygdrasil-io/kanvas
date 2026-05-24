package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ClipShaderComplexClipTest {

    private fun runVariant(gm: GM, label: String, floor: Double = 50.0) {
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
        assertTrue(
            comparison.similarity >= floor,
            "$label similarity ${"%.2f".format(comparison.similarity)}% < $floor%",
        )
    }

    @Test
    fun `ClipShaderSimpleGM matches clip_shader_png within tolerance`() =
        runVariant(ClipShaderSimpleGM(), "ClipShaderSimpleGM")

    @Test
    fun `ClipShaderLayerGM matches clip_shader_layer_png within tolerance`() =
        runVariant(ClipShaderLayerGM(), "ClipShaderLayerGM")

    @Test
    fun `ClipShaderNestedGM matches clip_shader_nested_png within tolerance`() =
        runVariant(ClipShaderNestedGM(), "ClipShaderNestedGM")

    @Test
    fun `ClipShaderDifferenceGM matches clip_shader_difference_png within tolerance`() =
        runVariant(ClipShaderDifferenceGM(), "ClipShaderDifferenceGM")
}
