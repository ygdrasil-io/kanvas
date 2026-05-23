package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/image.cpp::new_texture_image`
 * (`DEF_SIMPLE_GM_CAN_FAIL(new_texture_image, canvas, errorMsg, 280, 115)`).
 *
 * The upstream GM creates 5 image varieties (raster bitmap, encoded PNG,
 * encoded JPEG/YUV, picture-backed, GPU texture) and then promotes each
 * to a GPU texture via `SkImages::TextureFromImage`, drawing two rows
 * (no-mip / with-mip). It has an unconditional early-exit guard:
 * ```cpp
 * if (!isGPU) {
 *     *errorMsg = skiagm::GM::kErrorMsg_DrawSkippedGpuOnly;
 *     return skiagm::DrawResult::kSkip;
 * }
 * ```
 * so on a CPU/raster context it draws nothing at all.
 *
 * `SkImages::TextureFromImage` is GPU-only and has no raster equivalent in
 * kanvas-skia — the test is disabled pending the GPU port.
 *
 * Tracked as STUB.GPU_TEXTURE_FROM_IMAGE.
 */
public class NewTextureImageGM : GM() {
    override fun getName(): String = "new_texture_image"
    override fun getISize(): SkISize = SkISize.Make(280, 115)

    override fun onDraw(canvas: SkCanvas?) {
        // GPU-only GM — SkImages::TextureFromImage is not implemented in the
        // kanvas-skia raster backend. The upstream GM returns kSkip on any
        // non-GPU context, so we mirror that by doing nothing here.
        TODO("STUB.GPU_TEXTURE_FROM_IMAGE")
    }
}
