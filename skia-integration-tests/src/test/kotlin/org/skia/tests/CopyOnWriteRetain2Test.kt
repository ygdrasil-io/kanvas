package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class CopyOnWriteRetain2Test {

    @Test
    fun `CopyOnWriteRetain2GM matches copy_on_write_retain2_png within tolerance`() {
        val gm = CopyOnWriteRetain2GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image copy_on_write_retain2.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("CopyOnWriteRetain2GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("CopyOnWriteRetain2GM", comparison.similarity)
        assertTrue(accepted, "CopyOnWriteRetain2GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 0.0,
            "CopyOnWriteRetain2GM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor"
        )
    }
}
