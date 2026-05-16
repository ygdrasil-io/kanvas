package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class EncodeJpegAlphaOptsGM : public GM {
 * public:
 *     EncodeJpegAlphaOptsGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("encode-alpha-jpeg"); }
 *
 *     SkISize getISize() override { return SkISize::Make(400, 200); }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         sk_sp<SkImage> srcImg = ToolUtils::GetResourceAsImage("images/rainbow-gradient.png");
 *         if (!srcImg) {
 *             *errorMsg = "Could not load images/rainbow-gradient.png. "
 *                         "Did you forget to set the resourcePath?";
 *             return DrawResult::kFail;
 *         }
 *         fStorage.reset(srcImg->width() * srcImg->height() *
 *                 SkColorTypeBytesPerPixel(kRGBA_F16_SkColorType));
 *
 *         SkPixmap src;
 *         SkImageInfo info = SkImageInfo::MakeN32Premul(srcImg->width(), srcImg->height(),
 *                 canvas->imageInfo().colorSpace() ? SkColorSpace::MakeSRGB() : nullptr);
 *         read_into_pixmap(&src, info, fStorage.get(), srcImg);
 *
 *         // Encode 8888 premul.
 *         auto img0 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kIgnore);
 *         auto img1 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kBlendOnBlack);
 *         canvas->drawImage(img0, 0.0f, 0.0f);
 *         canvas->drawImage(img1, 0.0f, 100.0f);
 *
 *         // Encode 8888 unpremul
 *         info = info.makeAlphaType(kUnpremul_SkAlphaType);
 *         read_into_pixmap(&src, info, fStorage.get(), srcImg);
 *         img0 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kIgnore);
 *         img1 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kBlendOnBlack);
 *         canvas->drawImage(img0, 100.0f, 0.0f);
 *         canvas->drawImage(img1, 100.0f, 100.0f);
 *
 *         // Encode F16 premul
 *         info = SkImageInfo::Make(srcImg->width(), srcImg->height(), kRGBA_F16_SkColorType,
 *                 kPremul_SkAlphaType, SkColorSpace::MakeSRGB());
 *         read_into_pixmap(&src, info, fStorage.get(), srcImg);
 *         img0 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kIgnore);
 *         img1 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kBlendOnBlack);
 *         canvas->drawImage(img0, 200.0f, 0.0f);
 *         canvas->drawImage(img1, 200.0f, 100.0f);
 *
 *         // Encode F16 unpremul
 *         info = info.makeAlphaType(kUnpremul_SkAlphaType);
 *         read_into_pixmap(&src, info, fStorage.get(), srcImg);
 *         img0 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kIgnore);
 *         img1 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kBlendOnBlack);
 *         canvas->drawImage(img0, 300.0f, 0.0f);
 *         canvas->drawImage(img1, 300.0f, 100.0f);
 *
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *     AutoTMalloc<uint8_t> fStorage;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class EncodeJpegAlphaOptsGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * AutoTMalloc<uint8_t> fStorage
   * ```
   */
  private var fStorage: Int = TODO("Initialize fStorage")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("encode-alpha-jpeg"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(400, 200); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         sk_sp<SkImage> srcImg = ToolUtils::GetResourceAsImage("images/rainbow-gradient.png");
   *         if (!srcImg) {
   *             *errorMsg = "Could not load images/rainbow-gradient.png. "
   *                         "Did you forget to set the resourcePath?";
   *             return DrawResult::kFail;
   *         }
   *         fStorage.reset(srcImg->width() * srcImg->height() *
   *                 SkColorTypeBytesPerPixel(kRGBA_F16_SkColorType));
   *
   *         SkPixmap src;
   *         SkImageInfo info = SkImageInfo::MakeN32Premul(srcImg->width(), srcImg->height(),
   *                 canvas->imageInfo().colorSpace() ? SkColorSpace::MakeSRGB() : nullptr);
   *         read_into_pixmap(&src, info, fStorage.get(), srcImg);
   *
   *         // Encode 8888 premul.
   *         auto img0 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kIgnore);
   *         auto img1 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kBlendOnBlack);
   *         canvas->drawImage(img0, 0.0f, 0.0f);
   *         canvas->drawImage(img1, 0.0f, 100.0f);
   *
   *         // Encode 8888 unpremul
   *         info = info.makeAlphaType(kUnpremul_SkAlphaType);
   *         read_into_pixmap(&src, info, fStorage.get(), srcImg);
   *         img0 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kIgnore);
   *         img1 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kBlendOnBlack);
   *         canvas->drawImage(img0, 100.0f, 0.0f);
   *         canvas->drawImage(img1, 100.0f, 100.0f);
   *
   *         // Encode F16 premul
   *         info = SkImageInfo::Make(srcImg->width(), srcImg->height(), kRGBA_F16_SkColorType,
   *                 kPremul_SkAlphaType, SkColorSpace::MakeSRGB());
   *         read_into_pixmap(&src, info, fStorage.get(), srcImg);
   *         img0 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kIgnore);
   *         img1 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kBlendOnBlack);
   *         canvas->drawImage(img0, 200.0f, 0.0f);
   *         canvas->drawImage(img1, 200.0f, 100.0f);
   *
   *         // Encode F16 unpremul
   *         info = info.makeAlphaType(kUnpremul_SkAlphaType);
   *         read_into_pixmap(&src, info, fStorage.get(), srcImg);
   *         img0 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kIgnore);
   *         img1 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kBlendOnBlack);
   *         canvas->drawImage(img0, 300.0f, 0.0f);
   *         canvas->drawImage(img1, 300.0f, 100.0f);
   *
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
