package org.skia.modules

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
 * class RecordMatchFamilyStyleSkFontMgr : public SkFontMgr {
 * public:
 *     const SkFontStyle* styleRequestedWhenMatchingFamily(const char* family) const {
 *         auto s = fStyleRequestedWhenMatchingFamily.find(family);
 *         return s != fStyleRequestedWhenMatchingFamily.end() ? &s->second : nullptr;
 *     }
 *
 * private:
 *     int onCountFamilies() const override { return 0; }
 *     void onGetFamilyName(int index, SkString* familyName) const override {}
 *     sk_sp<SkFontStyleSet> onCreateStyleSet(int index) const override { return nullptr; }
 *
 *     sk_sp<SkFontStyleSet> onMatchFamily(const char[]) const override { return nullptr; }
 *
 *     sk_sp<SkTypeface> onMatchFamilyStyle(const char family[],
 *                                          const SkFontStyle& style) const override {
 *         fStyleRequestedWhenMatchingFamily[family] = style;
 *         return nullptr;
 *     }
 *     sk_sp<SkTypeface> onMatchFamilyStyleCharacter(const char familyName[], const SkFontStyle& style,
 *                                                   const char* bcp47[], int bcp47Count,
 *                                                   SkUnichar character) const override {
 *         fStyleRequestedWhenMatchingFamily[familyName] = style;
 *         return nullptr;
 *     }
 *
 *     sk_sp<SkTypeface> onMakeFromData(sk_sp<SkData>, int ttcIndex) const override {
 *         return nullptr;
 *     }
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
 *     sk_sp<SkTypeface> onLegacyMakeTypeface(const char familyName[], SkFontStyle) const override {
 *         return nullptr;
 *     }
 *
 *     mutable std::unordered_map<std::string, SkFontStyle> fStyleRequestedWhenMatchingFamily;
 * }
 * ```
 */
public open class RecordMatchFamilyStyleSkFontMgr : SkFontMgr() {
  /**
   * C++ original:
   * ```cpp
   * mutable std::unordered_map<std::string, SkFontStyle> fStyleRequestedWhenMatchingFamily
   * ```
   */
  private var fStyleRequestedWhenMatchingFamily: Int =
      TODO("Initialize fStyleRequestedWhenMatchingFamily")

  /**
   * C++ original:
   * ```cpp
   * const SkFontStyle* styleRequestedWhenMatchingFamily(const char* family) const {
   *         auto s = fStyleRequestedWhenMatchingFamily.find(family);
   *         return s != fStyleRequestedWhenMatchingFamily.end() ? &s->second : nullptr;
   *     }
   * ```
   */
  public fun styleRequestedWhenMatchingFamily(family: String?): SkFontStyle {
    TODO("Implement styleRequestedWhenMatchingFamily")
  }

  /**
   * C++ original:
   * ```cpp
   * int onCountFamilies() const override { return 0; }
   * ```
   */
  public override fun onCountFamilies(): Int {
    TODO("Implement onCountFamilies")
  }

  /**
   * C++ original:
   * ```cpp
   * void onGetFamilyName(int index, SkString* familyName) const override {}
   * ```
   */
  public override fun onGetFamilyName(index: Int, familyName: String?) {
    TODO("Implement onGetFamilyName")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontStyleSet> onCreateStyleSet(int index) const override { return nullptr; }
   * ```
   */
  public override fun onCreateStyleSet(index: Int): SkSp<SkFontStyleSet> {
    TODO("Implement onCreateStyleSet")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontStyleSet> onMatchFamily(const char[]) const override { return nullptr; }
   * ```
   */
  public override fun onMatchFamily(param0: CharArray): SkSp<SkFontStyleSet> {
    TODO("Implement onMatchFamily")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMatchFamilyStyle(const char family[],
   *                                          const SkFontStyle& style) const override {
   *         fStyleRequestedWhenMatchingFamily[family] = style;
   *         return nullptr;
   *     }
   * ```
   */
  public override fun onMatchFamilyStyle(family: CharArray, style: SkFontStyle): SkSp<SkTypeface> {
    TODO("Implement onMatchFamilyStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMatchFamilyStyleCharacter(const char familyName[], const SkFontStyle& style,
   *                                                   const char* bcp47[], int bcp47Count,
   *                                                   SkUnichar character) const override {
   *         fStyleRequestedWhenMatchingFamily[familyName] = style;
   *         return nullptr;
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
   * sk_sp<SkTypeface> onMakeFromData(sk_sp<SkData>, int ttcIndex) const override {
   *         return nullptr;
   *     }
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
   * sk_sp<SkTypeface> onLegacyMakeTypeface(const char familyName[], SkFontStyle) const override {
   *         return nullptr;
   *     }
   * ```
   */
  public override fun onLegacyMakeTypeface(familyName: CharArray, param1: SkFontStyle): SkSp<SkTypeface> {
    TODO("Implement onLegacyMakeTypeface")
  }
}
