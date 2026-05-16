package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class FontMgrGM : public skiagm::GM {
 *     sk_sp<SkFontMgr> fFM;
 *
 *     void onOnceBeforeDraw() override {
 *         SkGraphics::SetFontCacheLimit(16 * 1024 * 1024);
 *         fFM = ToolUtils::TestFontMgr();
 *     }
 *
 *     SkString getName() const override { return SkString("fontmgr_iter"); }
 *
 *     SkISize getISize() override { return {1536, 768}; }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         SkScalar y = 20;
 *         SkFont font = ToolUtils::DefaultFont();
 *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *         font.setSubpixel(true);
 *         font.setSize(17);
 *
 *         SkFontMgr* fm = fFM.get();
 *         int count = std::min(fm->countFamilies(), MAX_FAMILIES);
 *         if (count == 0) {
 *             *errorMsg = "No families in SkFontMgr";
 *             return DrawResult::kSkip;
 *         }
 *
 *         for (int i = 0; i < count; ++i) {
 *             SkString familyName;
 *             fm->getFamilyName(i, &familyName);
 *             font.setTypeface(ToolUtils::DefaultTypeface());
 *             (void)drawString(canvas, familyName, 20, y, font);
 *
 *             SkScalar x = 220;
 *
 *             sk_sp<SkFontStyleSet> set(fm->createStyleSet(i));
 *             for (int j = 0; j < set->count(); ++j) {
 *                 SkString sname;
 *                 SkFontStyle fs;
 *                 set->getStyle(j, &fs, &sname);
 *                 sname.appendf(" [%d %d %d]", fs.weight(), fs.width(), fs.slant());
 *
 *                 font.setTypeface(sk_sp<SkTypeface>(set->createTypeface(j)));
 *                 x = drawString(canvas, sname, x, y, font) + 20;
 *
 *                 // check to see that we get different glyphs in japanese and chinese
 *                 x = drawCharacter(canvas, 0x5203, x, y, font, fm, familyName.c_str(), &zh, 1, fs);
 *                 x = drawCharacter(canvas, 0x5203, x, y, font, fm, familyName.c_str(), &ja, 1, fs);
 *                 // check that emoji characters are found
 *                 x = drawCharacter(canvas, 0x1f601, x, y, font, fm, familyName.c_str(), nullptr,0, fs);
 *             }
 *             y += 24;
 *         }
 *         return DrawResult::kOk;
 *     }
 * }
 * ```
 */
public open class FontMgrGM : GM() {
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
   *         SkGraphics::SetFontCacheLimit(16 * 1024 * 1024);
   *         fFM = ToolUtils::TestFontMgr();
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("fontmgr_iter"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {1536, 768}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         SkScalar y = 20;
   *         SkFont font = ToolUtils::DefaultFont();
   *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
   *         font.setSubpixel(true);
   *         font.setSize(17);
   *
   *         SkFontMgr* fm = fFM.get();
   *         int count = std::min(fm->countFamilies(), MAX_FAMILIES);
   *         if (count == 0) {
   *             *errorMsg = "No families in SkFontMgr";
   *             return DrawResult::kSkip;
   *         }
   *
   *         for (int i = 0; i < count; ++i) {
   *             SkString familyName;
   *             fm->getFamilyName(i, &familyName);
   *             font.setTypeface(ToolUtils::DefaultTypeface());
   *             (void)drawString(canvas, familyName, 20, y, font);
   *
   *             SkScalar x = 220;
   *
   *             sk_sp<SkFontStyleSet> set(fm->createStyleSet(i));
   *             for (int j = 0; j < set->count(); ++j) {
   *                 SkString sname;
   *                 SkFontStyle fs;
   *                 set->getStyle(j, &fs, &sname);
   *                 sname.appendf(" [%d %d %d]", fs.weight(), fs.width(), fs.slant());
   *
   *                 font.setTypeface(sk_sp<SkTypeface>(set->createTypeface(j)));
   *                 x = drawString(canvas, sname, x, y, font) + 20;
   *
   *                 // check to see that we get different glyphs in japanese and chinese
   *                 x = drawCharacter(canvas, 0x5203, x, y, font, fm, familyName.c_str(), &zh, 1, fs);
   *                 x = drawCharacter(canvas, 0x5203, x, y, font, fm, familyName.c_str(), &ja, 1, fs);
   *                 // check that emoji characters are found
   *                 x = drawCharacter(canvas, 0x1f601, x, y, font, fm, familyName.c_str(), nullptr,0, fs);
   *             }
   *             y += 24;
   *         }
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
