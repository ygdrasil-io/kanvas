package org.skia.tools

import kotlin.CharArray
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkData
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkFontStyleSet
import org.skia.foundation.SkSp
import org.skia.foundation.SkStreamAsset
import org.skia.foundation.SkTypeface
import org.skia.foundation.SkUnichar

/**
 * C++ original:
 * ```cpp
 * class FontMgr final : public SkFontMgr {
 * public:
 *     FontMgr() {
 *         auto&& list = TestTypeface::Typefaces();
 *         for (auto&& family : list.families) {
 *             auto&& ss = fFamilies.emplace_back(sk_make_sp<FontStyleSet>(family.name));
 *             for (auto&& face : family.faces) {
 *                 ss->fTypefaces.emplace_back(face.typeface, face.typeface->fontStyle(), face.name);
 *                 if (face.isDefault) {
 *                     fDefaultFamily = ss;
 *                     fDefaultTypeface = face.typeface;
 *                 }
 *             }
 *         }
 *         if (!fDefaultFamily) {
 *             SkASSERTF(false, "expected TestTypeface to return a default");
 *             fDefaultFamily = fFamilies[0];
 *             fDefaultTypeface = fDefaultFamily->fTypefaces[0].fTypeface;
 *         }
 *
 * #if defined(SK_ENABLE_SVG)
 *         fFamilies.emplace_back(sk_make_sp<FontStyleSet>("Emoji"));
 *         fFamilies.back()->fTypefaces.emplace_back(
 *                 TestSVGTypeface::Default(), SkFontStyle::Normal(), "Normal");
 *
 *         fFamilies.emplace_back(sk_make_sp<FontStyleSet>("Planet"));
 *         fFamilies.back()->fTypefaces.emplace_back(
 *                 TestSVGTypeface::Planets(), SkFontStyle::Normal(), "Normal");
 * #endif
 *     }
 *
 *     int onCountFamilies() const override { return fFamilies.size(); }
 *
 *     void onGetFamilyName(int index, SkString* familyName) const override {
 *         *familyName = fFamilies[index]->getFamilyName();
 *     }
 *
 *     sk_sp<SkFontStyleSet> onCreateStyleSet(int index) const override {
 *         sk_sp<SkFontStyleSet> ref = fFamilies[index];
 *         return ref;
 *     }
 *
 *     sk_sp<SkFontStyleSet> onMatchFamily(const char familyName[]) const override {
 *         if (familyName) {
 *             if (strstr(familyName, "ono")) {
 *                 return this->createStyleSet(0);
 *             }
 *             if (strstr(familyName, "ans")) {
 *                 return this->createStyleSet(1);
 *             }
 *             if (strstr(familyName, "erif")) {
 *                 return this->createStyleSet(2);
 *             }
 * #if defined(SK_ENABLE_SVG)
 *             if (strstr(familyName, "oji")) {
 *                 return this->createStyleSet(6);
 *             }
 *             if (strstr(familyName, "Planet")) {
 *                 return this->createStyleSet(7);
 *             }
 * #endif
 *         }
 *         return nullptr;
 *     }
 *
 *     sk_sp<SkTypeface> onMatchFamilyStyle(const char         familyName[],
 *                                          const SkFontStyle& style) const override {
 *         sk_sp<SkFontStyleSet> styleSet(this->matchFamily(familyName));
 *         return styleSet->matchStyle(style);
 *     }
 *
 *     sk_sp<SkTypeface> onMatchFamilyStyleCharacter(const char         familyName[],
 *                                                   const SkFontStyle& style,
 *                                                   const char*        bcp47[],
 *                                                   int                bcp47Count,
 *                                                   SkUnichar          character) const override {
 *         (void)bcp47;
 *         (void)bcp47Count;
 *         (void)character;
 *         return this->matchFamilyStyle(familyName, style);
 *     }
 *
 *     sk_sp<SkTypeface> onMakeFromData(sk_sp<SkData>, int ttcIndex) const override { return nullptr; }
 *     sk_sp<SkTypeface> onMakeFromStreamIndex(std::unique_ptr<SkStreamAsset>,
 *                                             int ttcIndex) const override {
 *         return nullptr;
 *     }
 *     sk_sp<SkTypeface> onMakeFromStreamArgs(std::unique_ptr<SkStreamAsset>,
 *                                            const SkFontArguments&) const override {
 *         return nullptr;
 *     }
 *     sk_sp<SkTypeface> onMakeFromFile(const char path[], int ttcIndex) const override {
 *         return nullptr;
 *     }
 *
 *     sk_sp<SkTypeface> onLegacyMakeTypeface(const char  familyName[],
 *                                            SkFontStyle style) const override {
 *         if (familyName == nullptr) {
 *             return sk_sp<SkTypeface>(fDefaultFamily->matchStyle(style));
 *         }
 *         sk_sp<SkTypeface> typeface = sk_sp<SkTypeface>(this->matchFamilyStyle(familyName, style));
 *         if (!typeface) {
 *             typeface = fDefaultTypeface;
 *         }
 *         return typeface;
 *     }
 *
 * private:
 *     std::vector<sk_sp<FontStyleSet>> fFamilies;
 *     sk_sp<FontStyleSet>              fDefaultFamily;
 *     sk_sp<SkTypeface>                fDefaultTypeface;
 * }
 * ```
 */
public class FontMgr public constructor() : SkFontMgr() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<FontStyleSet>> fFamilies
   * ```
   */
  private var fFamilies: Int = TODO("Initialize fFamilies")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<FontStyleSet>              fDefaultFamily
   * ```
   */
  private var fDefaultFamily: SkSp<FontStyleSet> = TODO("Initialize fDefaultFamily")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface>                fDefaultTypeface
   * ```
   */
  private var fDefaultTypeface: SkSp<SkTypeface> = TODO("Initialize fDefaultTypeface")

  /**
   * C++ original:
   * ```cpp
   * int onCountFamilies() const override { return fFamilies.size(); }
   * ```
   */
  public override fun onCountFamilies(): Int {
    TODO("Implement onCountFamilies")
  }

  /**
   * C++ original:
   * ```cpp
   * void onGetFamilyName(int index, SkString* familyName) const override {
   *         *familyName = fFamilies[index]->getFamilyName();
   *     }
   * ```
   */
  public override fun onGetFamilyName(index: Int, familyName: String?) {
    TODO("Implement onGetFamilyName")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontStyleSet> onCreateStyleSet(int index) const override {
   *         sk_sp<SkFontStyleSet> ref = fFamilies[index];
   *         return ref;
   *     }
   * ```
   */
  public override fun onCreateStyleSet(index: Int): SkSp<SkFontStyleSet> {
    TODO("Implement onCreateStyleSet")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontStyleSet> onMatchFamily(const char familyName[]) const override {
   *         if (familyName) {
   *             if (strstr(familyName, "ono")) {
   *                 return this->createStyleSet(0);
   *             }
   *             if (strstr(familyName, "ans")) {
   *                 return this->createStyleSet(1);
   *             }
   *             if (strstr(familyName, "erif")) {
   *                 return this->createStyleSet(2);
   *             }
   * #if defined(SK_ENABLE_SVG)
   *             if (strstr(familyName, "oji")) {
   *                 return this->createStyleSet(6);
   *             }
   *             if (strstr(familyName, "Planet")) {
   *                 return this->createStyleSet(7);
   *             }
   * #endif
   *         }
   *         return nullptr;
   *     }
   * ```
   */
  public override fun onMatchFamily(familyName: CharArray): SkSp<SkFontStyleSet> {
    TODO("Implement onMatchFamily")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMatchFamilyStyle(const char         familyName[],
   *                                          const SkFontStyle& style) const override {
   *         sk_sp<SkFontStyleSet> styleSet(this->matchFamily(familyName));
   *         return styleSet->matchStyle(style);
   *     }
   * ```
   */
  public override fun onMatchFamilyStyle(familyName: CharArray, style: SkFontStyle): SkSp<SkTypeface> {
    TODO("Implement onMatchFamilyStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMatchFamilyStyleCharacter(const char         familyName[],
   *                                                   const SkFontStyle& style,
   *                                                   const char*        bcp47[],
   *                                                   int                bcp47Count,
   *                                                   SkUnichar          character) const override {
   *         (void)bcp47;
   *         (void)bcp47Count;
   *         (void)character;
   *         return this->matchFamilyStyle(familyName, style);
   *     }
   * ```
   */
  public override fun onMatchFamilyStyleCharacter(
    familyName: CharArray,
    style: SkFontStyle,
    bcp47: Int,
    bcp47Count: Int,
    character: SkUnichar,
  ): SkSp<SkTypeface> {
    TODO("Implement onMatchFamilyStyleCharacter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMakeFromData(sk_sp<SkData>, int ttcIndex) const override { return nullptr; }
   * ```
   */
  public override fun onMakeFromData(param0: SkSp<SkData>, ttcIndex: Int): SkSp<SkTypeface> {
    TODO("Implement onMakeFromData")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMakeFromStreamIndex(std::unique_ptr<SkStreamAsset>,
   *                                             int ttcIndex) const override {
   *         return nullptr;
   *     }
   * ```
   */
  public override fun onMakeFromStreamIndex(param0: SkStreamAsset?, ttcIndex: Int): SkSp<SkTypeface> {
    TODO("Implement onMakeFromStreamIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMakeFromStreamArgs(std::unique_ptr<SkStreamAsset>,
   *                                            const SkFontArguments&) const override {
   *         return nullptr;
   *     }
   * ```
   */
  public override fun onMakeFromStreamArgs(param0: SkStreamAsset?, param1: SkFontArguments): SkSp<SkTypeface> {
    TODO("Implement onMakeFromStreamArgs")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMakeFromFile(const char path[], int ttcIndex) const override {
   *         return nullptr;
   *     }
   * ```
   */
  public override fun onMakeFromFile(path: CharArray, ttcIndex: Int): SkSp<SkTypeface> {
    TODO("Implement onMakeFromFile")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onLegacyMakeTypeface(const char  familyName[],
   *                                            SkFontStyle style) const override {
   *         if (familyName == nullptr) {
   *             return sk_sp<SkTypeface>(fDefaultFamily->matchStyle(style));
   *         }
   *         sk_sp<SkTypeface> typeface = sk_sp<SkTypeface>(this->matchFamilyStyle(familyName, style));
   *         if (!typeface) {
   *             typeface = fDefaultTypeface;
   *         }
   *         return typeface;
   *     }
   * ```
   */
  public override fun onLegacyMakeTypeface(familyName: CharArray, style: SkFontStyle): SkSp<SkTypeface> {
    TODO("Implement onLegacyMakeTypeface")
  }
}
