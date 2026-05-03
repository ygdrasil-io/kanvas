package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyleSet
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class FontMgrMatchGM : public skiagm::GM {
 *     sk_sp<SkFontMgr> fFM;
 *
 *     void onOnceBeforeDraw() override {
 *         fFM = ToolUtils::TestFontMgr();
 *         SkGraphics::SetFontCacheLimit(16 * 1024 * 1024);
 *     }
 *
 *     SkString getName() const override { return SkString("fontmgr_match"); }
 *
 *     SkISize getISize() override { return {640, 1024}; }
 *
 *     void iterateFamily(SkCanvas* canvas, const SkFont& font, SkFontStyleSet* fset) {
 *         SkFont f(font);
 *         SkScalar y = 0;
 *
 *         for (int j = 0; j < fset->count(); ++j) {
 *             SkString sname;
 *             SkFontStyle fs;
 *             fset->getStyle(j, &fs, &sname);
 *
 *             sname.appendf(" [%d %d]", fs.weight(), fs.width());
 *
 *             f.setTypeface(sk_sp<SkTypeface>(fset->createTypeface(j)));
 *             SkScalar x = 0;
 *             x = drawString(canvas, sname, x, y, f) + 20;
 *             // check to see that we get different glyphs in japanese and chinese
 *             // and the style matches with no name
 *             x = drawCharacter(canvas, 0x5203, x, y, font, fFM.get(), nullptr, &zh, 1, fs);
 *             x = drawCharacter(canvas, 0x5203, x, y, font, fFM.get(), nullptr, &ja, 1, fs);
 *             y += 24;
 *         }
 *     }
 *
 *     void exploreFamily(SkCanvas* canvas, const SkFont& font, SkFontStyleSet* fset) {
 *         SkFont f(font);
 *         SkScalar y = 0;
 *
 *         for (int weight = 100; weight <= 900; weight += 200) {
 *             for (int width = 1; width <= 9; width += 2) {
 *                 SkFontStyle fs(weight, width, SkFontStyle::kUpright_Slant);
 *                 sk_sp<SkTypeface> face(fset->matchStyle(fs));
 *                 if (face) {
 *                     SkString str;
 *                     str.printf("request [%d %d]", fs.weight(), fs.width());
 *                     f.setTypeface(std::move(face));
 *                     SkScalar x = 0;
 *                     x = drawString(canvas, str, x, y, f) + 20;
 *                     // check to see that we get different glyphs in japanese and chinese
 *                     // and the style matches with no name
 *                     x = drawCharacter(canvas, 0x5203, x, y, font, fFM.get(), nullptr, &zh, 1, fs);
 *                     x = drawCharacter(canvas, 0x5203, x, y, font, fFM.get(), nullptr, &ja, 1, fs);
 *                     y += 24;
 *                 }
 *             }
 *         }
 *     }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         SkFont font = ToolUtils::DefaultFont();
 *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *         font.setSubpixel(true);
 *         font.setSize(17);
 *
 *         const char* gNames[] = {
 *             "Helvetica Neue", "Arial", "sans", "Roboto"
 *         };
 *
 *         sk_sp<SkFontStyleSet> fset;
 *         for (size_t i = 0; i < std::size(gNames); ++i) {
 *             fset = fFM->matchFamily(gNames[i]);
 *             if (fset->count() > 0) {
 *                 break;
 *             }
 *         }
 *         if (!fset || fset->count() == 0) {
 *             *errorMsg = "No SkFontStyleSet";
 *             return DrawResult::kSkip;
 *         }
 *
 *         canvas->translate(20, 40);
 *         this->exploreFamily(canvas, font, fset.get());
 *         canvas->translate(350, 0);
 *         this->iterateFamily(canvas, font, fset.get());
 *         return DrawResult::kOk;
 *     }
 * }
 * ```
 */
public open class FontMgrMatchGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontMgr> fFM
   * ```
   */
  private var fFM: SkSp<SkFontMgr> = TODO("Initialize fFM")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fFM = ToolUtils::TestFontMgr();
   *         SkGraphics::SetFontCacheLimit(16 * 1024 * 1024);
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("fontmgr_match"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {640, 1024}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void iterateFamily(SkCanvas* canvas, const SkFont& font, SkFontStyleSet* fset) {
   *         SkFont f(font);
   *         SkScalar y = 0;
   *
   *         for (int j = 0; j < fset->count(); ++j) {
   *             SkString sname;
   *             SkFontStyle fs;
   *             fset->getStyle(j, &fs, &sname);
   *
   *             sname.appendf(" [%d %d]", fs.weight(), fs.width());
   *
   *             f.setTypeface(sk_sp<SkTypeface>(fset->createTypeface(j)));
   *             SkScalar x = 0;
   *             x = drawString(canvas, sname, x, y, f) + 20;
   *             // check to see that we get different glyphs in japanese and chinese
   *             // and the style matches with no name
   *             x = drawCharacter(canvas, 0x5203, x, y, font, fFM.get(), nullptr, &zh, 1, fs);
   *             x = drawCharacter(canvas, 0x5203, x, y, font, fFM.get(), nullptr, &ja, 1, fs);
   *             y += 24;
   *         }
   *     }
   * ```
   */
  private fun iterateFamily(
    canvas: SkCanvas?,
    font: SkFont,
    fset: SkFontStyleSet?,
  ) {
    TODO("Implement iterateFamily")
  }

  /**
   * C++ original:
   * ```cpp
   * void exploreFamily(SkCanvas* canvas, const SkFont& font, SkFontStyleSet* fset) {
   *         SkFont f(font);
   *         SkScalar y = 0;
   *
   *         for (int weight = 100; weight <= 900; weight += 200) {
   *             for (int width = 1; width <= 9; width += 2) {
   *                 SkFontStyle fs(weight, width, SkFontStyle::kUpright_Slant);
   *                 sk_sp<SkTypeface> face(fset->matchStyle(fs));
   *                 if (face) {
   *                     SkString str;
   *                     str.printf("request [%d %d]", fs.weight(), fs.width());
   *                     f.setTypeface(std::move(face));
   *                     SkScalar x = 0;
   *                     x = drawString(canvas, str, x, y, f) + 20;
   *                     // check to see that we get different glyphs in japanese and chinese
   *                     // and the style matches with no name
   *                     x = drawCharacter(canvas, 0x5203, x, y, font, fFM.get(), nullptr, &zh, 1, fs);
   *                     x = drawCharacter(canvas, 0x5203, x, y, font, fFM.get(), nullptr, &ja, 1, fs);
   *                     y += 24;
   *                 }
   *             }
   *         }
   *     }
   * ```
   */
  private fun exploreFamily(
    canvas: SkCanvas?,
    font: SkFont,
    fset: SkFontStyleSet?,
  ) {
    TODO("Implement exploreFamily")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         SkFont font = ToolUtils::DefaultFont();
   *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
   *         font.setSubpixel(true);
   *         font.setSize(17);
   *
   *         const char* gNames[] = {
   *             "Helvetica Neue", "Arial", "sans", "Roboto"
   *         };
   *
   *         sk_sp<SkFontStyleSet> fset;
   *         for (size_t i = 0; i < std::size(gNames); ++i) {
   *             fset = fFM->matchFamily(gNames[i]);
   *             if (fset->count() > 0) {
   *                 break;
   *             }
   *         }
   *         if (!fset || fset->count() == 0) {
   *             *errorMsg = "No SkFontStyleSet";
   *             return DrawResult::kSkip;
   *         }
   *
   *         canvas->translate(20, 40);
   *         this->exploreFamily(canvas, font, fset.get());
   *         canvas->translate(350, 0);
   *         this->iterateFamily(canvas, font, fset.get());
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
