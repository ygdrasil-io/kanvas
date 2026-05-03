package org.skia.utils

import kotlin.CharArray
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkData
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkSp
import org.skia.foundation.SkStreamAsset
import org.skia.foundation.SkUnichar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkOrderedFontMgr : public SkFontMgr {
 * public:
 *     SkOrderedFontMgr();
 *     ~SkOrderedFontMgr() override;
 *
 *     void append(sk_sp<SkFontMgr>);
 *
 * protected:
 *     int onCountFamilies() const override;
 *     void onGetFamilyName(int index, SkString* familyName) const override;
 *     sk_sp<SkFontStyleSet> onCreateStyleSet(int index)const override;
 *
 *     sk_sp<SkFontStyleSet> onMatchFamily(const char familyName[]) const override;
 *
 *     sk_sp<SkTypeface> onMatchFamilyStyle(const char familyName[],
 *                                          const SkFontStyle&) const override;
 *     sk_sp<SkTypeface> onMatchFamilyStyleCharacter(const char familyName[], const SkFontStyle&,
 *                                                   const char* bcp47[], int bcp47Count,
 *                                                   SkUnichar character) const override;
 *
 *     // Note: all of these always return null
 *     sk_sp<SkTypeface> onMakeFromData(sk_sp<SkData>, int ttcIndex) const override;
 *     sk_sp<SkTypeface> onMakeFromStreamIndex(std::unique_ptr<SkStreamAsset>,
 *                                             int ttcIndex) const override;
 *     sk_sp<SkTypeface> onMakeFromStreamArgs(std::unique_ptr<SkStreamAsset>,
 *                                            const SkFontArguments&) const override;
 *     sk_sp<SkTypeface> onMakeFromFile(const char path[], int ttcIndex) const override;
 *
 *     sk_sp<SkTypeface> onLegacyMakeTypeface(const char familyName[], SkFontStyle) const override;
 *
 * private:
 *     std::vector<sk_sp<SkFontMgr>> fList;
 * }
 * ```
 */
public open class SkOrderedFontMgr public constructor() : SkFontMgr() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<SkFontMgr>> fList
   * ```
   */
  private var fList: Int = TODO("Initialize fList")

  /**
   * C++ original:
   * ```cpp
   * void SkOrderedFontMgr::append(sk_sp<SkFontMgr> fm) {
   *     fList.push_back(std::move(fm));
   * }
   * ```
   */
  public fun append(fm: SkSp<SkFontMgr>) {
    TODO("Implement append")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkOrderedFontMgr::onCountFamilies() const {
   *     int count = 0;
   *     for (const auto& fm : fList) {
   *         count += fm->countFamilies();
   *     }
   *     return count;
   * }
   * ```
   */
  protected override fun onCountFamilies(): Int {
    TODO("Implement onCountFamilies")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOrderedFontMgr::onGetFamilyName(int index, SkString* familyName) const {
   *     for (const auto& fm : fList) {
   *         const int count = fm->countFamilies();
   *         if (index < count) {
   *             return fm->getFamilyName(index, familyName);
   *         }
   *         index -= count;
   *     }
   * }
   * ```
   */
  protected override fun onGetFamilyName(index: Int, familyName: String?) {
    TODO("Implement onGetFamilyName")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontStyleSet> SkOrderedFontMgr::onCreateStyleSet(int index) const {
   *     for (const auto& fm : fList) {
   *         const int count = fm->countFamilies();
   *         if (index < count) {
   *             return fm->createStyleSet(index);
   *         }
   *         index -= count;
   *     }
   *     return nullptr;
   * }
   * ```
   */
  protected override fun onCreateStyleSet(index: Int): Int {
    TODO("Implement onCreateStyleSet")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontStyleSet> SkOrderedFontMgr::onMatchFamily(const char familyName[]) const {
   *     for (const auto& fm : fList) {
   *         const auto fs = fm->matchFamily(familyName);
   *         if (fs->count() > 0){
   *             return fs;
   *         }
   *     }
   *     return nullptr;
   * }
   * ```
   */
  protected override fun onMatchFamily(familyName: CharArray): Int {
    TODO("Implement onMatchFamily")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkOrderedFontMgr::onMatchFamilyStyle(const char family[],
   *                                                        const SkFontStyle& style) const {
   *     for (const auto& fm : fList) {
   *         if (auto tf = fm->matchFamilyStyle(family, style)) {
   *             return tf;
   *         }
   *     }
   *     return nullptr;
   * }
   * ```
   */
  protected override fun onMatchFamilyStyle(familyName: CharArray, style: SkFontStyle): Int {
    TODO("Implement onMatchFamilyStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkOrderedFontMgr::onMatchFamilyStyleCharacter(
   *     const char familyName[], const SkFontStyle& style,
   *     const char* bcp47[], int bcp47Count,
   *     SkUnichar uni) const
   * {
   *     for (const auto& fm : fList) {
   *         if (auto tf = fm->matchFamilyStyleCharacter(familyName, style, bcp47, bcp47Count, uni)) {
   *             return tf;
   *         }
   *     }
   *     return nullptr;
   * }
   * ```
   */
  protected override fun onMatchFamilyStyleCharacter(
    familyName: CharArray,
    style: SkFontStyle,
    bcp47: Int,
    bcp47Count: Int,
    character: SkUnichar,
  ): Int {
    TODO("Implement onMatchFamilyStyleCharacter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkOrderedFontMgr::onMakeFromData(sk_sp<SkData>, int ttcIndex) const {
   *     return nullptr;
   * }
   * ```
   */
  protected override fun onMakeFromData(param0: SkSp<SkData>, ttcIndex: Int): Int {
    TODO("Implement onMakeFromData")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMakeFromStreamIndex(std::unique_ptr<SkStreamAsset>,
   *                                             int ttcIndex) const override
   * ```
   */
  protected override fun onMakeFromStreamIndex(param0: SkStreamAsset?, ttcIndex: Int): Int {
    TODO("Implement onMakeFromStreamIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMakeFromStreamArgs(std::unique_ptr<SkStreamAsset>,
   *                                            const SkFontArguments&) const override
   * ```
   */
  protected override fun onMakeFromStreamArgs(param0: SkStreamAsset?, param1: SkFontArguments): Int {
    TODO("Implement onMakeFromStreamArgs")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkOrderedFontMgr::onMakeFromFile(const char path[], int ttcIndex) const {
   *     return nullptr;
   * }
   * ```
   */
  protected override fun onMakeFromFile(path: CharArray, ttcIndex: Int): Int {
    TODO("Implement onMakeFromFile")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkOrderedFontMgr::onLegacyMakeTypeface(const char family[], SkFontStyle style) const {
   *     for (const auto& fm : fList) {
   *         if (auto tf = fm->matchFamilyStyle(family, style)) {
   *             return fm->legacyMakeTypeface(family, style);
   *         }
   *     }
   *     return nullptr;
   * }
   * ```
   */
  protected override fun onLegacyMakeTypeface(familyName: CharArray, style: SkFontStyle): Int {
    TODO("Implement onLegacyMakeTypeface")
  }
}
