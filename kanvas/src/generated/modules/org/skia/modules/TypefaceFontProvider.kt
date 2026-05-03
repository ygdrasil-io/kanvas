package org.skia.modules

import kotlin.CharArray
import kotlin.Int
import kotlin.String
import kotlin.ULong
import org.skia.foundation.SkData
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkSp
import org.skia.foundation.SkStreamAsset
import org.skia.foundation.SkTypeface
import org.skia.foundation.SkUnichar

/**
 * C++ original:
 * ```cpp
 * class TypefaceFontProvider : public SkFontMgr {
 * public:
 *     size_t registerTypeface(sk_sp<SkTypeface> typeface);
 *     size_t registerTypeface(sk_sp<SkTypeface> typeface, const SkString& alias);
 *
 *     int onCountFamilies() const override;
 *
 *     void onGetFamilyName(int index, SkString* familyName) const override;
 *
 *     sk_sp<SkFontStyleSet> onMatchFamily(const char familyName[]) const override;
 *
 *     sk_sp<SkFontStyleSet> onCreateStyleSet(int) const override;
 *     sk_sp<SkTypeface> onMatchFamilyStyle(const char familyName[], const SkFontStyle& pattern) const override;
 *     sk_sp<SkTypeface> onMatchFamilyStyleCharacter(const char[], const SkFontStyle&,
 *                                                   const char*[], int,
 *                                                   SkUnichar) const override {
 *         return nullptr;
 *     }
 *
 *     sk_sp<SkTypeface> onMakeFromData(sk_sp<SkData>, int) const override { return nullptr; }
 *     sk_sp<SkTypeface> onMakeFromStreamIndex(std::unique_ptr<SkStreamAsset>, int) const override {
 *         return nullptr;
 *     }
 *     sk_sp<SkTypeface> onMakeFromStreamArgs(std::unique_ptr<SkStreamAsset>,
 *                                            const SkFontArguments&) const override {
 *         return nullptr;
 *     }
 *     sk_sp<SkTypeface> onMakeFromFile(const char[], int) const override {
 *         return nullptr;
 *     }
 *
 *     sk_sp<SkTypeface> onLegacyMakeTypeface(const char[], SkFontStyle) const override;
 *
 * private:
 *     skia_private::THashMap<SkString, sk_sp<TypefaceFontStyleSet>> fRegisteredFamilies;
 *     skia_private::TArray<SkString> fFamilyNames;
 * }
 * ```
 */
public open class TypefaceFontProvider : SkFontMgr() {
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<SkString> fFamilyNames
   * ```
   */
  private var fFamilyNames: Int = TODO("Initialize fFamilyNames")

  /**
   * C++ original:
   * ```cpp
   * size_t TypefaceFontProvider::registerTypeface(sk_sp<SkTypeface> typeface) {
   *     if (typeface == nullptr) {
   *         return 0;
   *     }
   *
   *     SkString familyName;
   *     typeface->getFamilyName(&familyName);
   *
   *     return registerTypeface(std::move(typeface), std::move(familyName));
   * }
   * ```
   */
  public fun registerTypeface(typeface: SkSp<SkTypeface>): ULong {
    TODO("Implement registerTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t TypefaceFontProvider::registerTypeface(sk_sp<SkTypeface> typeface, const SkString& familyName) {
   *     if (familyName.size() == 0) {
   *         return 0;
   *     }
   *
   *     auto found = fRegisteredFamilies.find(familyName);
   *     if (found == nullptr) {
   *         found = fRegisteredFamilies.set(familyName, sk_make_sp<TypefaceFontStyleSet>(familyName));
   *         fFamilyNames.emplace_back(familyName);
   *     }
   *
   *     (*found)->appendTypeface(std::move(typeface));
   *
   *     return 1;
   * }
   * ```
   */
  public fun registerTypeface(typeface: SkSp<SkTypeface>, alias: String): ULong {
    TODO("Implement registerTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * int TypefaceFontProvider::onCountFamilies() const { return fRegisteredFamilies.count(); }
   * ```
   */
  public override fun onCountFamilies(): Int {
    TODO("Implement onCountFamilies")
  }

  /**
   * C++ original:
   * ```cpp
   * void TypefaceFontProvider::onGetFamilyName(int index, SkString* familyName) const {
   *     SkASSERT(index < fRegisteredFamilies.count());
   *     familyName->set(fFamilyNames[index]);
   * }
   * ```
   */
  public override fun onGetFamilyName(index: Int, familyName: String?) {
    TODO("Implement onGetFamilyName")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontStyleSet> TypefaceFontProvider::onMatchFamily(const char familyName[]) const {
   *     auto found = fRegisteredFamilies.find(SkString(familyName));
   *     return found ? *found : nullptr;
   * }
   * ```
   */
  public override fun onMatchFamily(familyName: CharArray): Int {
    TODO("Implement onMatchFamily")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontStyleSet> TypefaceFontProvider::onCreateStyleSet(int index) const {
   *     SkASSERT(index < fRegisteredFamilies.count());
   *     auto found = fRegisteredFamilies.find(fFamilyNames[index]);
   *     return found ? *found : nullptr;
   * }
   * ```
   */
  public override fun onCreateStyleSet(index: Int): Int {
    TODO("Implement onCreateStyleSet")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> TypefaceFontProvider::onMatchFamilyStyle(const char familyName[], const SkFontStyle& pattern) const {
   *     sk_sp<SkFontStyleSet> sset(this->matchFamily(familyName));
   *     if (sset) {
   *         return sset->matchStyle(pattern);
   *     }
   *
   *     return nullptr;
   * }
   * ```
   */
  public override fun onMatchFamilyStyle(familyName: CharArray, pattern: SkFontStyle): Int {
    TODO("Implement onMatchFamilyStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMatchFamilyStyleCharacter(const char[], const SkFontStyle&,
   *                                                   const char*[], int,
   *                                                   SkUnichar) const override {
   *         return nullptr;
   *     }
   * ```
   */
  public override fun onMatchFamilyStyleCharacter(
    param0: CharArray,
    param1: SkFontStyle,
    param2: Int,
    param3: Int,
    param4: SkUnichar,
  ): Int {
    TODO("Implement onMatchFamilyStyleCharacter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMakeFromData(sk_sp<SkData>, int) const override { return nullptr; }
   * ```
   */
  public override fun onMakeFromData(param0: SkSp<SkData>, param1: Int): Int {
    TODO("Implement onMakeFromData")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMakeFromStreamIndex(std::unique_ptr<SkStreamAsset>, int) const override {
   *         return nullptr;
   *     }
   * ```
   */
  public override fun onMakeFromStreamIndex(param0: SkStreamAsset?, param1: Int): Int {
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
  public override fun onMakeFromStreamArgs(param0: SkStreamAsset?, param1: SkFontArguments): Int {
    TODO("Implement onMakeFromStreamArgs")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMakeFromFile(const char[], int) const override {
   *         return nullptr;
   *     }
   * ```
   */
  public override fun onMakeFromFile(param0: CharArray, param1: Int): Int {
    TODO("Implement onMakeFromFile")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> TypefaceFontProvider::onLegacyMakeTypeface(const char familyName[],
   *                                                              SkFontStyle style) const {
   *     if (familyName) {
   *         sk_sp<SkTypeface> matchedByFamily = this->matchFamilyStyle(familyName, style);
   *         if (matchedByFamily) {
   *             return matchedByFamily;
   *         }
   *     }
   *     if (this->countFamilies() == 0) {
   *         return nullptr;
   *     }
   *     sk_sp<SkFontStyleSet> defaultFamily = this->createStyleSet(0);
   *     if (!defaultFamily) {
   *         return nullptr;
   *     }
   *     return defaultFamily->matchStyle(style);
   * }
   * ```
   */
  public override fun onLegacyMakeTypeface(familyName: CharArray, style: SkFontStyle): Int {
    TODO("Implement onLegacyMakeTypeface")
  }
}
