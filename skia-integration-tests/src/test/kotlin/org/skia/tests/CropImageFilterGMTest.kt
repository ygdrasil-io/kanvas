package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkTileMode
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class CropImageFilterGMTest {

    @Test
    fun `CropImageFilter decal-decal variant matches reference within tolerance`() {
        runVariant(CropImageFilterGM(SkTileMode.kDecal, SkTileMode.kDecal), "CropImageFilterGM_decal_decal")
    }

    @Test
    fun `CropImageFilter clamp-clamp variant matches reference within tolerance`() {
        runVariant(CropImageFilterGM(SkTileMode.kClamp, SkTileMode.kClamp), "CropImageFilterGM_clamp_clamp")
    }

    @Test
    fun `CropImageFilter repeat-repeat variant matches reference within tolerance`() {
        runVariant(
            CropImageFilterGM(SkTileMode.kRepeat, SkTileMode.kRepeat),
            "CropImageFilterGM_repeat_repeat",
        )
    }

    @Test
    fun `CropImageFilter mirror-mirror variant matches reference within tolerance`() {
        runVariant(
            CropImageFilterGM(SkTileMode.kMirror, SkTileMode.kMirror),
            "CropImageFilterGM_mirror_mirror",
        )
    }

    private fun runVariant(gm: CropImageFilterGM, label: String) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed(label, comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(label, comparison.similarity)
        assertTrue(accepted, "$label regressed below ratchet")
    }
}
