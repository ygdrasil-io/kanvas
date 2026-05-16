package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class BitmapImageGM : public GM {
 * public:
 *     BitmapImageGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("bitmap-image-srgb-legacy"); }
 *
 *     SkISize getISize() override { return SkISize::Make(2 * kSize, 2 * kSize); }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         // Create image.
 *         const char* path = "images/mandrill_512_q075.jpg";
 *         sk_sp<SkImage> image = ToolUtils::GetResourceAsImage(path);
 *         if (!image) {
 *             *errorMsg = "Couldn't load images/mandrill_512_q075.jpg. "
 *                         "Did you forget to set the resource path?";
 *             return DrawResult::kFail;
 *         }
 *
 *         // Create matching bitmap.
 *         std::unique_ptr<SkCodec> codec(SkCodec::MakeFromStream(GetResourceAsStream(path)));
 *         auto [codecImage, _] = codec->getImage();
 *
 *         // The GM will be displayed in a 2x2 grid.
 *         // The top two squares show an sRGB image, then bitmap, drawn to a legacy canvas.
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
 *         // The bottom two squares show an sRGB image, then bitmap, drawn to a srgb canvas.
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
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class BitmapImageGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("bitmap-image-srgb-legacy"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(2 * kSize, 2 * kSize); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         // Create image.
   *         const char* path = "images/mandrill_512_q075.jpg";
   *         sk_sp<SkImage> image = ToolUtils::GetResourceAsImage(path);
   *         if (!image) {
   *             *errorMsg = "Couldn't load images/mandrill_512_q075.jpg. "
   *                         "Did you forget to set the resource path?";
   *             return DrawResult::kFail;
   *         }
   *
   *         // Create matching bitmap.
   *         std::unique_ptr<SkCodec> codec(SkCodec::MakeFromStream(GetResourceAsStream(path)));
   *         auto [codecImage, _] = codec->getImage();
   *
   *         // The GM will be displayed in a 2x2 grid.
   *         // The top two squares show an sRGB image, then bitmap, drawn to a legacy canvas.
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
   *         // The bottom two squares show an sRGB image, then bitmap, drawn to a srgb canvas.
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
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kSize: Int = TODO("Initialize kSize")
  }
}
