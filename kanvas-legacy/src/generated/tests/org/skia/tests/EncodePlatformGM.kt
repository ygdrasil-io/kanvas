package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class EncodePlatformGM : public GM {
 * public:
 *     EncodePlatformGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("encode-platform"); }
 *
 *     SkISize getISize() override { return SkISize::Make(256 * std::size(gRecs), 256 * 3); }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         SkBitmap opaqueBm, premulBm, unpremulBm;
 *
 *         if (!ToolUtils::GetResourceAsBitmap("images/mandrill_256.png", &opaqueBm)) {
 *             *errorMsg = "Could not load images/mandrill_256.png.png. "
 *                         "Did you forget to set the resourcePath?";
 *             return DrawResult::kFail;
 *         }
 *         SkBitmap tmp;
 *         if (!ToolUtils::GetResourceAsBitmap("images/yellow_rose.png", &tmp)) {
 *             *errorMsg = "Could not load images/yellow_rose.png. "
 *                         "Did you forget to set the resourcePath?";
 *             return DrawResult::kFail;
 *         }
 *         tmp.extractSubset(&premulBm, SkIRect::MakeWH(256, 256));
 *         tmp.reset();
 *         unpremulBm.allocPixels(premulBm.info().makeAlphaType(kUnpremul_SkAlphaType));
 *         SkAssertResult(premulBm.readPixels(unpremulBm.pixmap()));
 *
 *         for (const auto& rec : gRecs) {
 *             auto fmt = rec.format; int q = rec.quality;
 *             auto opaqueImage = SkImages::DeferredFromEncodedData(encode_data(fmt, opaqueBm, q));
 *             auto premulImage = SkImages::DeferredFromEncodedData(encode_data(fmt, premulBm, q));
 *             auto unpremulImage = SkImages::DeferredFromEncodedData(encode_data(fmt, unpremulBm, q));
 *
 *             canvas->drawImage(opaqueImage.get(), 0.0f, 0.0f);
 *             canvas->drawImage(premulImage.get(), 0.0f, 256.0f);
 *             canvas->drawImage(unpremulImage.get(), 0.0f, 512.0f);
 *
 *             canvas->translate(256.0f, 0.0f);
 *         }
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class EncodePlatformGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("encode-platform"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(256 * std::size(gRecs), 256 * 3); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         SkBitmap opaqueBm, premulBm, unpremulBm;
   *
   *         if (!ToolUtils::GetResourceAsBitmap("images/mandrill_256.png", &opaqueBm)) {
   *             *errorMsg = "Could not load images/mandrill_256.png.png. "
   *                         "Did you forget to set the resourcePath?";
   *             return DrawResult::kFail;
   *         }
   *         SkBitmap tmp;
   *         if (!ToolUtils::GetResourceAsBitmap("images/yellow_rose.png", &tmp)) {
   *             *errorMsg = "Could not load images/yellow_rose.png. "
   *                         "Did you forget to set the resourcePath?";
   *             return DrawResult::kFail;
   *         }
   *         tmp.extractSubset(&premulBm, SkIRect::MakeWH(256, 256));
   *         tmp.reset();
   *         unpremulBm.allocPixels(premulBm.info().makeAlphaType(kUnpremul_SkAlphaType));
   *         SkAssertResult(premulBm.readPixels(unpremulBm.pixmap()));
   *
   *         for (const auto& rec : gRecs) {
   *             auto fmt = rec.format; int q = rec.quality;
   *             auto opaqueImage = SkImages::DeferredFromEncodedData(encode_data(fmt, opaqueBm, q));
   *             auto premulImage = SkImages::DeferredFromEncodedData(encode_data(fmt, premulBm, q));
   *             auto unpremulImage = SkImages::DeferredFromEncodedData(encode_data(fmt, unpremulBm, q));
   *
   *             canvas->drawImage(opaqueImage.get(), 0.0f, 0.0f);
   *             canvas->drawImage(premulImage.get(), 0.0f, 256.0f);
   *             canvas->drawImage(unpremulImage.get(), 0.0f, 512.0f);
   *
   *             canvas->translate(256.0f, 0.0f);
   *         }
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
