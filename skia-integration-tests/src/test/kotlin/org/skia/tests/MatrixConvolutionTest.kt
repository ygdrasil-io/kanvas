package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class MatrixConvolutionTest {
    /** matrixconvolution (basic, white/grey) */
    @Test
    fun `MatrixConvolutionGM_basic matches matrixconvolution_png within tolerance`() {
        runOne(
            MatrixConvolutionGM(0xFFFFFFFF.toInt(), 0x40404040.toInt(),
                MatrixConvolutionGM.KernelFixture.kBasic, ""),
            "MatrixConvolutionGM_basic",
        )
    }

    /** matrixconvolution_color (basic, red/green) */
    @Test
    fun `MatrixConvolutionGM_basic_color matches matrixconvolution_color_png within tolerance`() {
        runOne(
            MatrixConvolutionGM(0xFFFF0000.toInt(), 0xFF00FF00.toInt(),
                MatrixConvolutionGM.KernelFixture.kBasic, "_color"),
            "MatrixConvolutionGM_basic_color",
        )
    }

    /** matrixconvolution_big (7x7 kernel, white/grey) */
    @Test
    fun `MatrixConvolutionGM_big matches matrixconvolution_big_png within tolerance`() {
        runOne(
            MatrixConvolutionGM(0xFFFFFFFF.toInt(), 0x40404040.toInt(),
                MatrixConvolutionGM.KernelFixture.kLarge, "_big"),
            "MatrixConvolutionGM_big",
        )
    }

    private fun runOne(gm: MatrixConvolutionGM, scoreKey: String) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed(scoreKey, comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(scoreKey, comparison.similarity)
        assertTrue(accepted, "$scoreKey regressed below ratchet")
        assertTrue(comparison.similarity >= 0.0,
            "$scoreKey similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor")
    }
}
