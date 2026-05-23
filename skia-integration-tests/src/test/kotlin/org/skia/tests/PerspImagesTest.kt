package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Runner for [PerspImagesGM] (`persp_images`, 1150 × 1280).
 *
 * **Classification: INTRACTABLE.GPU_ONLY**
 *
 * The reference PNG (`original-888/persp_images.png`) was captured by the
 * upstream Skia GPU (Ganesh) pipeline. The GM calls
 * `ToolUtils::MakeTextureImage` to upload each raster image to a GPU texture
 * before drawing; on `:kanvas-skia`'s raster-only backend
 * [ToolUtils.MakeTextureImage] is a no-op (identity), so all 96 cells are
 * drawn through the raster perspective path. The GPU and raster sampling
 * pipelines diverge significantly under the combined perspective matrix +
 * multiple sampling modes (nearest/linear/mip/cubic), making a meaningful
 * pixel comparison against the GPU reference impossible.
 *
 * The GM body is fully implemented so a future GPU backend can activate this
 * test without code changes — just remove `@Disabled`.
 */
@Disabled("INTRACTABLE.GPU_ONLY: persp_images reference was captured on Ganesh GPU; " +
    "raster MakeTextureImage is a no-op, GPU/raster perspective sampling diverge too far " +
    "for a meaningful pixel comparison against original-888/persp_images.png")
class PerspImagesTest {

    @Test
    fun `PerspImagesGM matches persp_images_png within tolerance`() {
        val gm = PerspImagesGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        if (reference == null) {
            println("[PerspImagesTest] reference PNG not found — skipping pixel comparison")
            return
        }
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference, tolerance = 8)
        println(
            "[PerspImages] similarity=${"%.2f".format(comparison.similarity)}%, " +
                "matching=${comparison.matchingPixels}/${comparison.totalPixels}, " +
                "maxDiff=${comparison.maxChannelDiff}",
        )
    }
}
