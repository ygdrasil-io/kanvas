package org.skia.tests

import org.graphiks.kanvas.codec.SkCodec
import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkImageInfo
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/bitmapimage.cpp::BitmapImageGM` (`bitmap-image-srgb-legacy`,
 * 2 × 512 × 2 × 512).
 *
 * GM-registered name : `bitmap-image-srgb-legacy`.
 *
 * Compares the round-trip of `mandrill_512_q075.jpg` through two
 * intermediate canvases — a "legacy" (untagged) N32 canvas and an sRGB
 * (S32) N32 canvas — to verify that the codec-decoded sRGB-tagged bitmap
 * and a [ToolUtils.GetResourceAsImage] decode produce the same output.
 *
 * The reference is captured from upstream Skia's CPU raster pipeline.
 * In upstream the "legacy" canvas skips colour-management because its
 * info carries `nullptr` colorSpace ; our [SkImageInfo] always carries
 * a non-null sRGB colour space, so the legacy / sRGB distinction
 * collapses for sRGB-tagged sources (xform is identity in both
 * directions). Both intermediate canvases therefore render the same
 * pixels — pixel-equivalent to upstream's sRGB row, but the legacy row
 * differs from upstream by the tiny gamut-clipping deltas that the
 * untagged path produces on JPEG decode.
 *
 * C++ original :
 * ```cpp
 * class BitmapImageGM : public GM {
 * public:
 *     BitmapImageGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("bitmap-image-srgb-legacy"); }
 *     SkISize getISize() override { return SkISize::Make(2 * kSize, 2 * kSize); }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         const char* path = "images/mandrill_512_q075.jpg";
 *         sk_sp<SkImage> image = ToolUtils::GetResourceAsImage(path);
 *         if (!image) {
 *             *errorMsg = "Couldn't load images/mandrill_512_q075.jpg. …";
 *             return DrawResult::kFail;
 *         }
 *         std::unique_ptr<SkCodec> codec(SkCodec::MakeFromStream(GetResourceAsStream(path)));
 *         auto [codecImage, _] = codec->getImage();
 *
 *         // Top row : legacy (untagged) N32 canvas.
 *         SkImageInfo linearInfo = SkImageInfo::MakeN32(2*kSize, kSize, kOpaque_SkAlphaType);
 *         SkBitmap legacyBMCanvas;
 *         legacyBMCanvas.allocPixels(linearInfo);
 *         SkCanvas legacyCanvas(legacyBMCanvas);
 *         legacyCanvas.drawImage(image, 0.0f, 0.0f);
 *         legacyCanvas.translate(SkScalar(kSize), 0.0f);
 *         legacyCanvas.drawImage(codecImage, 0.0f, 0.0f);
 *         canvas->drawImage(legacyBMCanvas.asImage(), 0.0f, 0.0f);
 *         canvas->translate(0.0f, SkScalar(kSize));
 *
 *         // Bottom row : sRGB-tagged N32 canvas.
 *         SkImageInfo srgbInfo = SkImageInfo::MakeS32(2*kSize, kSize, kOpaque_SkAlphaType);
 *         SkBitmap srgbBMCanvas;
 *         srgbBMCanvas.allocPixels(srgbInfo);
 *         SkCanvas srgbCanvas(srgbBMCanvas);
 *         srgbCanvas.drawImage(image, 0.0f, 0.0f);
 *         srgbCanvas.translate(SkScalar(kSize), 0.0f);
 *         srgbCanvas.drawImage(codecImage, 0.0f, 0.0f);
 *         canvas->drawImage(srgbBMCanvas.asImage(), 0.0f, 0.0f);
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *     inline static constexpr int kSize = 512;
 * };
 *
 * DEF_GM( return new BitmapImageGM; )
 * ```
 */
public class BitmapImageGM : GM() {

    override fun getName(): String = "bitmap-image-srgb-legacy"

    override fun getISize(): SkISize = SkISize.Make(2 * kSize, 2 * kSize)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val path = "images/mandrill_512_q075.jpg"
        val image = ToolUtils.GetResourceAsImage(path) ?: return

        // Matching codec-decoded bitmap. Upstream takes
        // `SkCodec::MakeFromStream(GetResourceAsStream(path))` and
        // `codec->getImage()`. We use the same path via
        // [ToolUtils.GetResourceAsData] -> [SkCodec.MakeFromData] -> [getImage] :
        // identical decode output (same JPEG bytes, same baseline codec).
        val codecData = ToolUtils.GetResourceAsData(path) ?: return
        val codec = SkCodec.MakeFromData(codecData.toByteArray()) ?: return
        val (codecBitmap, _) = codec.getImage()
        val codecImage = codecBitmap?.asImage() ?: return

        // Top row — "legacy" N32 canvas. Upstream passes nullptr
        // colorSpace ; `:kanvas-skia`'s [SkImageInfo] always carries
        // a non-null colour space (default sRGB), so this collapses to
        // the same path as the sRGB canvas for sRGB-tagged inputs.
        val linearInfo = SkImageInfo.MakeN32(
            width = 2 * kSize,
            height = kSize,
            alphaType = SkAlphaType.kOpaque,
        )
        val legacySurface = SkSurface.MakeRaster(linearInfo)
        val legacyCanvas = legacySurface.canvas
        legacyCanvas.drawImage(image, 0f, 0f)
        legacyCanvas.translate(kSize.toFloat(), 0f)
        legacyCanvas.drawImage(codecImage, 0f, 0f)
        c.drawImage(legacySurface.makeImageSnapshot(), 0f, 0f)
        c.translate(0f, kSize.toFloat())

        // Bottom row — sRGB N32 canvas.
        val srgbInfo = SkImageInfo.MakeN32(
            width = 2 * kSize,
            height = kSize,
            alphaType = SkAlphaType.kOpaque,
        )
        val srgbSurface = SkSurface.MakeRaster(srgbInfo)
        val srgbCanvas = srgbSurface.canvas
        srgbCanvas.drawImage(image, 0f, 0f)
        srgbCanvas.translate(kSize.toFloat(), 0f)
        srgbCanvas.drawImage(codecImage, 0f, 0f)
        c.drawImage(srgbSurface.makeImageSnapshot(), 0f, 0f)
    }

    public companion object {
        private const val kSize: Int = 512
    }
}
