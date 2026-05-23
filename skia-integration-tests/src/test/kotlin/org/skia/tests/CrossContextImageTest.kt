package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * `gm/crosscontextimage.cpp::cross_context_image` exercises Skia's
 * `SkImages::CrossContextTextureFromPixmap` (Ganesh `GrDirectContext` GPU
 * upload path). The entire upstream GM is `DEF_SIMPLE_GPU_GM_CAN_FAIL` —
 * it requires a live `GrDirectContext` and explicitly returns
 * `DrawResult::kSkip` when none is available.
 *
 * `:kanvas-skia` is a raster-only backend; [SkImages.CrossContextTextureFromPixmap]
 * is a `TODO("STUB.CROSS_CONTEXT_IMAGE")` that throws [NotImplementedError]
 * at runtime. The [CrossContextImageGM] body is a faithful port of the
 * upstream `onDraw` (it calls the stub), so this test is `@Disabled` until
 * a GPU context / cross-context texture path lands.
 */
@Disabled(
    "STUB.CROSS_CONTEXT_IMAGE: SkImages.CrossContextTextureFromPixmap requires " +
        "GrDirectContext — kanvas-skia is raster-only. " +
        "Body is fully ported in CrossContextImageGM.kt; " +
        "drop @Disabled once a GPU cross-context texture path lands.",
)
class CrossContextImageTest {

    @Test
    fun `CrossContextImageGM matches cross_context_image reference`() {
        val gm = CrossContextImageGM()
        TestUtils.runGmTest(gm)
    }
}
