package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class Xfermodes2GM : public GM {
 * public:
 *     Xfermodes2GM(bool grayscale) : fGrayscale(grayscale) {}
 *
 * protected:
 *     SkString getName() const override {
 *         return fGrayscale ? SkString("xfermodes2_gray") : SkString("xfermodes2");
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(455, 475); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(SkIntToScalar(10), SkIntToScalar(20));
 *
 *         const SkScalar w = SkIntToScalar(kSize);
 *         const SkScalar h = SkIntToScalar(kSize);
 *
 *         SkFont font = ToolUtils::DefaultPortableFont();
 *
 *         const int W = 6;
 *
 *         SkScalar x = 0, y = 0;
 *         for (size_t m = 0; m < kSkBlendModeCount; m++) {
 *             SkBlendMode mode = static_cast<SkBlendMode>(m);
 *
 *             canvas->save();
 *
 *             canvas->translate(x, y);
 *             SkPaint p;
 *             p.setAntiAlias(false);
 *             p.setStyle(SkPaint::kFill_Style);
 *             p.setShader(fBG);
 *             SkRect r = SkRect::MakeWH(w, h);
 *             canvas->drawRect(r, p);
 *
 *             canvas->saveLayer(&r, nullptr);
 *
 *             p.setShader(fDst);
 *             canvas->drawRect(r, p);
 *             p.setShader(fSrc);
 *             p.setBlendMode(mode);
 *             canvas->drawRect(r, p);
 *
 *             canvas->restore();
 *
 *             r.inset(-SK_ScalarHalf, -SK_ScalarHalf);
 *             p.setStyle(SkPaint::kStroke_Style);
 *             p.setShader(nullptr);
 *             p.setBlendMode(SkBlendMode::kSrcOver);
 *             canvas->drawRect(r, p);
 *
 *             canvas->restore();
 *
 * #if 1
 *             SkTextUtils::DrawString(canvas, SkBlendMode_Name(mode), x + w/2, y - font.getSize()/2, font, SkPaint(),
 *                                     SkTextUtils::kCenter_Align);
 * #endif
 *             x += w + SkIntToScalar(10);
 *             if ((m % W) == W - 1) {
 *                 x = 0;
 *                 y += h + SkIntToScalar(30);
 *             }
 *         }
 *     }
 *
 * private:
 *     void onOnceBeforeDraw() override {
 *         const uint32_t kCheckData[] = {
 *             SkPackARGB32(0xFF, 0x42, 0x41, 0x42),
 *             SkPackARGB32(0xFF, 0xD6, 0xD3, 0xD6),
 *             SkPackARGB32(0xFF, 0xD6, 0xD3, 0xD6),
 *             SkPackARGB32(0xFF, 0x42, 0x41, 0x42)
 *         };
 *         SkBitmap bg;
 *         bg.allocN32Pixels(2, 2, true);
 *         memcpy(bg.getPixels(), kCheckData, sizeof(kCheckData));
 *
 *         SkMatrix lm;
 *         lm.setScale(SkIntToScalar(16), SkIntToScalar(16));
 *         fBG = bg.makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat, SkSamplingOptions(), lm);
 *
 *         SkBitmap srcBmp;
 *         srcBmp.allocN32Pixels(kSize, kSize);
 *         SkPMColor* pixels = reinterpret_cast<SkPMColor*>(srcBmp.getPixels());
 *
 *         for (int y = 0; y < kSize; ++y) {
 *             int c = y * (1 << kShift);
 *             SkPMColor rowColor = fGrayscale ? SkPackARGB32(c, c, c, c) : SkPackARGB32(c, c, 0, c/2);
 *             for (int x = 0; x < kSize; ++x) {
 *                 pixels[kSize * y + x] = rowColor;
 *             }
 *         }
 *         fSrc = srcBmp.makeShader(SkSamplingOptions());
 *         SkBitmap dstBmp;
 *         dstBmp.allocN32Pixels(kSize, kSize);
 *         pixels = reinterpret_cast<SkPMColor*>(dstBmp.getPixels());
 *
 *         for (int x = 0; x < kSize; ++x) {
 *             int c = x * (1 << kShift);
 *             SkPMColor colColor = fGrayscale ? SkPackARGB32(c, c, c, c) : SkPackARGB32(c, 0, c, c/2);
 *             for (int y = 0; y < kSize; ++y) {
 *                 pixels[kSize * y + x] = colColor;
 *             }
 *         }
 *         fDst = dstBmp.makeShader(SkSamplingOptions());
 *     }
 *
 *     enum {
 *         kShift = 2,
 *         kSize = 256 >> kShift,
 *     };
 *
 *     bool fGrayscale;
 *     sk_sp<SkShader> fBG;
 *     sk_sp<SkShader> fSrc;
 *     sk_sp<SkShader> fDst;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class Xfermodes2GM public constructor(
  grayscale: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * bool fGrayscale
   * ```
   */
  private var fGrayscale: Boolean = TODO("Initialize fGrayscale")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fBG
   * ```
   */
  private var fBG: SkSp<SkShader> = TODO("Initialize fBG")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fSrc
   * ```
   */
  private var fSrc: SkSp<SkShader> = TODO("Initialize fSrc")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fDst
   * ```
   */
  private var fDst: SkSp<SkShader> = TODO("Initialize fDst")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         return fGrayscale ? SkString("xfermodes2_gray") : SkString("xfermodes2");
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(455, 475); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->translate(SkIntToScalar(10), SkIntToScalar(20));
   *
   *         const SkScalar w = SkIntToScalar(kSize);
   *         const SkScalar h = SkIntToScalar(kSize);
   *
   *         SkFont font = ToolUtils::DefaultPortableFont();
   *
   *         const int W = 6;
   *
   *         SkScalar x = 0, y = 0;
   *         for (size_t m = 0; m < kSkBlendModeCount; m++) {
   *             SkBlendMode mode = static_cast<SkBlendMode>(m);
   *
   *             canvas->save();
   *
   *             canvas->translate(x, y);
   *             SkPaint p;
   *             p.setAntiAlias(false);
   *             p.setStyle(SkPaint::kFill_Style);
   *             p.setShader(fBG);
   *             SkRect r = SkRect::MakeWH(w, h);
   *             canvas->drawRect(r, p);
   *
   *             canvas->saveLayer(&r, nullptr);
   *
   *             p.setShader(fDst);
   *             canvas->drawRect(r, p);
   *             p.setShader(fSrc);
   *             p.setBlendMode(mode);
   *             canvas->drawRect(r, p);
   *
   *             canvas->restore();
   *
   *             r.inset(-SK_ScalarHalf, -SK_ScalarHalf);
   *             p.setStyle(SkPaint::kStroke_Style);
   *             p.setShader(nullptr);
   *             p.setBlendMode(SkBlendMode::kSrcOver);
   *             canvas->drawRect(r, p);
   *
   *             canvas->restore();
   *
   * #if 1
   *             SkTextUtils::DrawString(canvas, SkBlendMode_Name(mode), x + w/2, y - font.getSize()/2, font, SkPaint(),
   *                                     SkTextUtils::kCenter_Align);
   * #endif
   *             x += w + SkIntToScalar(10);
   *             if ((m % W) == W - 1) {
   *                 x = 0;
   *                 y += h + SkIntToScalar(30);
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         const uint32_t kCheckData[] = {
   *             SkPackARGB32(0xFF, 0x42, 0x41, 0x42),
   *             SkPackARGB32(0xFF, 0xD6, 0xD3, 0xD6),
   *             SkPackARGB32(0xFF, 0xD6, 0xD3, 0xD6),
   *             SkPackARGB32(0xFF, 0x42, 0x41, 0x42)
   *         };
   *         SkBitmap bg;
   *         bg.allocN32Pixels(2, 2, true);
   *         memcpy(bg.getPixels(), kCheckData, sizeof(kCheckData));
   *
   *         SkMatrix lm;
   *         lm.setScale(SkIntToScalar(16), SkIntToScalar(16));
   *         fBG = bg.makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat, SkSamplingOptions(), lm);
   *
   *         SkBitmap srcBmp;
   *         srcBmp.allocN32Pixels(kSize, kSize);
   *         SkPMColor* pixels = reinterpret_cast<SkPMColor*>(srcBmp.getPixels());
   *
   *         for (int y = 0; y < kSize; ++y) {
   *             int c = y * (1 << kShift);
   *             SkPMColor rowColor = fGrayscale ? SkPackARGB32(c, c, c, c) : SkPackARGB32(c, c, 0, c/2);
   *             for (int x = 0; x < kSize; ++x) {
   *                 pixels[kSize * y + x] = rowColor;
   *             }
   *         }
   *         fSrc = srcBmp.makeShader(SkSamplingOptions());
   *         SkBitmap dstBmp;
   *         dstBmp.allocN32Pixels(kSize, kSize);
   *         pixels = reinterpret_cast<SkPMColor*>(dstBmp.getPixels());
   *
   *         for (int x = 0; x < kSize; ++x) {
   *             int c = x * (1 << kShift);
   *             SkPMColor colColor = fGrayscale ? SkPackARGB32(c, c, c, c) : SkPackARGB32(c, 0, c, c/2);
   *             for (int y = 0; y < kSize; ++y) {
   *                 pixels[kSize * y + x] = colColor;
   *             }
   *         }
   *         fDst = dstBmp.makeShader(SkSamplingOptions());
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  public companion object {
    public val kShift: Int = TODO("Initialize kShift")

    public val kSize: Int = TODO("Initialize kSize")
  }
}
