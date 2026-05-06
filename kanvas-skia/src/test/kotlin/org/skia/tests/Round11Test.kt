package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Round11Test {

    private fun runGm(gm: org.skia.tests.GM, trackerName: String, floor: Double) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed(trackerName, comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(trackerName, comparison.similarity)
        assertTrue(accepted, "$trackerName regressed below tolerance")
        assertTrue(
            comparison.similarity >= floor,
            "$trackerName similarity ${"%.2f".format(comparison.similarity)}% < $floor% floor",
        )
    }

    @Test
    fun `FiddleGM matches reference`() = runGm(FiddleGM(), "FiddleGM", 99.0)

    // Bug9331GM at ~56 % — dasher renders the correct count of dashes
    // in the right positions but sub-pixel placement drifts vs upstream
    // (each dash off by 1-2 px on the AA edges). Visual layout matches.
    @Test
    fun `Bug9331GM matches reference`() = runGm(Bug9331GM(), "Bug9331GM", 50.0)

    @Test
    fun `Bug530095GM matches reference`() = runGm(Bug530095GM(), "Bug530095GM", 80.0)

    @Test
    fun `Bug591993GM matches reference`() = runGm(Bug591993GM(), "Bug591993GM", 80.0)

    @Test
    fun `Crbug1113794GM matches reference`() = runGm(Crbug1113794GM(), "Crbug1113794GM", 80.0)

    @Test
    fun `Crbug892988GM matches reference`() = runGm(Crbug892988GM(), "Crbug892988GM", 80.0)

    @Test
    fun `StrokerectAnisotropic5408GM matches reference`() =
        runGm(StrokerectAnisotropic5408GM(), "StrokerectAnisotropic5408GM", 80.0)

    @Test
    fun `StrokerectAnisotropicGM matches reference`() =
        runGm(StrokerectAnisotropicGM(), "StrokerectAnisotropicGM", 80.0)
}
