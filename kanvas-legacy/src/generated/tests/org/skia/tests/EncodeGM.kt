package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class EncodeGM : public GM {
 * public:
 *     EncodeGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("encode"); }
 *
 *     SkISize getISize() override { return SkISize::Make(1024, 600); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkBitmap orig;
 *         ToolUtils::GetResourceAsBitmap("images/mandrill_512_q075.jpg", &orig);
 *         sk_sp<SkData> pngData = SkPngEncoder::Encode(orig.pixmap(), {});
 *         SkASSERT_RELEASE(pngData);
 *
 *         sk_sp<SkData> jpgData = SkJpegEncoder::Encode(orig.pixmap(), {});
 *         SkASSERT_RELEASE(jpgData);
 *
 *         sk_sp<SkImage> pngImage = SkImages::DeferredFromEncodedData(pngData);
 *         sk_sp<SkImage> jpgImage = SkImages::DeferredFromEncodedData(jpgData);
 *         canvas->drawImage(pngImage.get(), 0.0f, 0.0f);
 *         canvas->drawImage(jpgImage.get(), 512.0f, 0.0f);
 *
 *         SkFont font = ToolUtils::DefaultPortableFont();
 *         font.setEdging(SkFont::Edging::kAlias);
 *         canvas->drawString("Images should look identical.", 450.0f, 550.0f, font, SkPaint());
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class EncodeGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("encode"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1024, 600); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkBitmap orig;
   *         ToolUtils::GetResourceAsBitmap("images/mandrill_512_q075.jpg", &orig);
   *         sk_sp<SkData> pngData = SkPngEncoder::Encode(orig.pixmap(), {});
   *         SkASSERT_RELEASE(pngData);
   *
   *         sk_sp<SkData> jpgData = SkJpegEncoder::Encode(orig.pixmap(), {});
   *         SkASSERT_RELEASE(jpgData);
   *
   *         sk_sp<SkImage> pngImage = SkImages::DeferredFromEncodedData(pngData);
   *         sk_sp<SkImage> jpgImage = SkImages::DeferredFromEncodedData(jpgData);
   *         canvas->drawImage(pngImage.get(), 0.0f, 0.0f);
   *         canvas->drawImage(jpgImage.get(), 512.0f, 0.0f);
   *
   *         SkFont font = ToolUtils::DefaultPortableFont();
   *         font.setEdging(SkFont::Edging::kAlias);
   *         canvas->drawString("Images should look identical.", 450.0f, 550.0f, font, SkPaint());
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
