package org.skia.tests

import kotlin.Array
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkTextBlob
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class BadAppleGM : public skiagm::GM {
 *     SkString getName() const override { return SkString("badapple"); }
 *
 *     SkISize getISize() override { return {kSize, kSize}; }
 *
 *     void onOnceBeforeDraw() override {
 *         this->setBGColor(SK_ColorWHITE);
 *         auto fm = ToolUtils::TestFontMgr();
 *
 *         static const SkString kTexts[] = {
 *                 SkString("Meet"),
 *                 SkString("iPad Pro"),
 *         };
 *
 *         SkFont font = ToolUtils::DefaultPortableFont();
 *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *         font.setSubpixel(true);
 *         font.setSize(256);
 *
 *         fBlobs[0] = make_blob(kTexts[0], font);
 *         fBlobs[1] = make_blob(kTexts[1], font);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setColor(0xFF111111);
 *         canvas->drawTextBlob(fBlobs[0], 10, 260, paint);
 *         canvas->drawTextBlob(fBlobs[1], 10, 500, paint);
 *     }
 *
 * private:
 *     inline static constexpr int kSize = 512;
 *
 *     sk_sp<SkTextBlob> fBlobs[3];
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class BadAppleGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kSize = 512
   * ```
   */
  private var fBlobs: Array<SkSp<SkTextBlob>> = TODO("Initialize fBlobs")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("badapple"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {kSize, kSize}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         this->setBGColor(SK_ColorWHITE);
   *         auto fm = ToolUtils::TestFontMgr();
   *
   *         static const SkString kTexts[] = {
   *                 SkString("Meet"),
   *                 SkString("iPad Pro"),
   *         };
   *
   *         SkFont font = ToolUtils::DefaultPortableFont();
   *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
   *         font.setSubpixel(true);
   *         font.setSize(256);
   *
   *         fBlobs[0] = make_blob(kTexts[0], font);
   *         fBlobs[1] = make_blob(kTexts[1], font);
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *         paint.setColor(0xFF111111);
   *         canvas->drawTextBlob(fBlobs[0], 10, 260, paint);
   *         canvas->drawTextBlob(fBlobs[1], 10, 500, paint);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kSize: Int = TODO("Initialize kSize")
  }
}
