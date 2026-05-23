package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkImages
import org.skia.foundation.SkTextureCompressionType
import org.graphiks.math.SkISize

/**
 * R-final.S — **STUB.COMPRESSED_TEXTURES** consumer GM.
 *
 * Iso-aligned port of upstream Skia's `gm/exoticformats.cpp::ExoticFormatsGM`.
 *
 * The upstream GM exercises handling of exotic compressed-texture formats
 * (ETC1 and BC1/DXT1) loaded from external KTX and DDS files, then uploaded
 * to the GPU as native compressed textures. Specifically it :
 *
 *  1. Loads `images/flower-etc1.ktx` via a minimal KTX parser and creates an
 *     ETC1/ETC2-RGB8_UNORM image (128 × 128, no mipmaps).
 *  2. Loads `images/flower-bc1.dds` via a minimal DDS parser and creates a
 *     BC1-RGB8_UNORM image (128 × 128, no mipmaps).
 *  3. On GPU : uploads both as native compressed GPU textures
 *     (`SkImages::TextureFromCompressedTextureData`). On raster (no GPU
 *     context) : decodes to raster via
 *     `SkImages::RasterFromCompressedTextureData`. If the backend doesn't
 *     support native compressed sampling a red stroke is drawn around the
 *     image to flag that the data was decompressed on the CPU.
 *  4. Draws both images side-by-side with a [kPad]-pixel gutter on a black
 *     background.
 *
 * Canvas dimensions: `(2×128 + 3×4) × (128 + 2×4)` = `268 × 136`.
 *
 * In `:kanvas-skia` the entire compressed-texture decode + upload pipeline is
 * out of scope — [SkImages.RasterFromCompressedTextureData] throws
 * `STUB.COMPRESSED_TEXTURES`. The body calls that factory so that the compile
 * contract is enforced and [ExoticFormatsTest] records the gap as `@Disabled`.
 *
 * C++ original:
 * ```cpp
 * class ExoticFormatsGM : public GM {
 *   static const int kImgWidthHeight = 128;
 *   static const int kPad = 4;
 *   sk_sp<SkImage> fETC1Image;
 *   sk_sp<SkImage> fBC1Image;
 *   ...
 *   void onDraw(SkCanvas* canvas) override {
 *     this->drawImage(canvas, fETC1Image.get(), kPad, kPad);
 *     this->drawImage(canvas, fBC1Image.get(), kImgWidthHeight + 2 * kPad, kPad);
 *   }
 * };
 * ```
 */
public class ExoticFormatsGM : GM() {

    override fun getName(): String = "exoticformats"

    override fun getISize(): SkISize {
        val w = 2 * IMG_WIDTH_HEIGHT + 3 * PAD
        val h = IMG_WIDTH_HEIGHT + 2 * PAD
        return SkISize.Make(w, h)
    }

    override fun onDraw(canvas: SkCanvas?) {
        // Touch the stubbed dispatch — throws STUB.COMPRESSED_TEXTURES at runtime.
        // This mirrors upstream's `data_to_img(nullptr, data, info)` raster path
        // which calls `SkImages::RasterFromCompressedTextureData(...)`.
        val placeholderData = ByteArray(0)
        SkImages.RasterFromCompressedTextureData(
            placeholderData,
            IMG_WIDTH_HEIGHT,
            IMG_WIDTH_HEIGHT,
            SkTextureCompressionType.kETC2_RGB8_UNORM,
        )
        SkImages.RasterFromCompressedTextureData(
            placeholderData,
            IMG_WIDTH_HEIGHT,
            IMG_WIDTH_HEIGHT,
            SkTextureCompressionType.kBC1_RGB8_UNORM,
        )
        // TODO: STUB.COMPRESSED_TEXTURES — draw fETC1Image and fBC1Image
        //   side-by-side once ETC1/ETC2 + BC1/DXT1 decode is implemented.
    }

    private companion object {
        const val IMG_WIDTH_HEIGHT = 128
        const val PAD = 4
    }
}
