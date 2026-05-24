package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of upstream Skia `gm/ycbcrimage.cpp`
 * (`DEF_GM(return new YCbCrImageGM;)`).
 *
 * Canvas size: `(2*kPad + kImageSize) × (2*kPad + kImageSize)` where
 * `kImageSize = 112` and `kPad = 8` → **128 × 128**.
 *
 * Background colour: `0xFFCCCCCC`.
 *
 * ## What the upstream GM does
 *
 * This GM exercises the **native Vulkan YCbCr image format** on the
 * Ganesh and Graphite GPU backends.  The entire file is guarded by
 * `#ifdef SK_VULKAN`, so it has no meaning outside a Vulkan context.
 *
 * The setup path (`onGpuSetup`):
 *
 *  1. Verifies that the active backend is Vulkan (returns `kSkip`
 *     otherwise).
 *  2. Creates a `VkYcbcrSamplerHelper` that allocates a real Vulkan
 *     image in the I420 (YCbCr) format and fills it with test colour
 *     data.
 *  3. Wraps the Vulkan backend texture in an `SkImage` via
 *     `SkImages::WrapTexture` (Graphite) or `SkImages::BorrowTextureFrom`
 *     (Ganesh).
 *
 * The draw path (`onDraw`) just calls
 * `canvas->drawImage(fYCbCrImage, kPad, kPad, linear-sampling)`.
 *
 * ## Missing APIs
 *
 * The entire pipeline relies on Vulkan-internal infrastructure that has
 * no CPU-raster equivalent in `:kanvas-skia`:
 *
 *  - `VkYcbcrSamplerHelper` — Vulkan helper that allocates / fills
 *    I420 Vulkan images and exposes a `VkSamplerYcbcrConversion`.
 *  - `SkImages::WrapTexture` (Graphite, Vulkan backend)
 *  - `SkImages::BorrowTextureFrom` (Ganesh, Vulkan backend)
 *  - `VkYcbcrSamplerHelper::isYCbCrSupported()` — hardware feature query
 *
 * Calling [onDraw] throws [TODO] tagged `STUB.VULKAN_YCBCR_SAMPLER`.
 * The matching [YCbCrImageTest] is `@Disabled`.
 */
public class YCbCrImageGM : GM() {

    init {
        setBGColor(0xFFCCCCCC.toInt())
    }

    override fun getName(): String = "ycbcrimage"

    override fun getISize(): SkISize = SkISize.Make(
        2 * kPad + kImageSize,
        2 * kPad + kImageSize,
    )

    override fun onDraw(canvas: SkCanvas?) {
        // Vulkan-only GM — VkYcbcrSamplerHelper + SkImages::WrapTexture /
        // BorrowTextureFrom require a live Vulkan context and hardware YCbCr
        // sampler support. There is no CPU-raster equivalent in kanvas-skia.
        // The upstream GM is entirely inside #ifdef SK_VULKAN and returns
        // kSkip on any non-Vulkan backend.
        TODO("STUB.VULKAN_YCBCR_SAMPLER: ycbcrimage requires VkYcbcrSamplerHelper + Vulkan-backed SkImage — no CPU-raster equivalent in kanvas-skia")
    }

    private companion object {
        const val kImageSize = 112
        const val kPad = 8
    }
}
