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
 * class MockFontMgr : public SkFontMgr {
 *  public:
 *     MockFontMgr(sk_sp<SkTypeface> test_font) : fTestFont(std::move(test_font)) {}
 *
 *     int onCountFamilies() const override { return 1; }
 *     void onGetFamilyName(int index, SkString* familyName) const override {}
 *     sk_sp<SkFontStyleSet> onCreateStyleSet(int index) const override { return nullptr; }
 *     sk_sp<SkFontStyleSet> onMatchFamily(const char familyName[]) const override {
 *         return nullptr;
 *     }
 *     sk_sp<SkTypeface> onMatchFamilyStyle(const char familyName[],
 *                                          const SkFontStyle& fontStyle) const override {
 *         return nullptr;
 *     }
 *     sk_sp<SkTypeface> onMatchFamilyStyleCharacter(const char familyName[], const SkFontStyle&,
 *                                                   const char* bcp47[], int bcp47Count,
 *                                                   SkUnichar character) const override {
 *         return nullptr;
 *     }
 *     sk_sp<SkTypeface> onMakeFromData(sk_sp<SkData>, int ttcIndex) const override {
 *         return fTestFont;
 *     }
 *     sk_sp<SkTypeface> onMakeFromStreamIndex(std::unique_ptr<SkStreamAsset>,
 *                                                 int ttcIndex) const override {
 *         return fTestFont;
 *     }
 *     sk_sp<SkTypeface> onMakeFromStreamArgs(std::unique_ptr<SkStreamAsset>,
 *                                                const SkFontArguments&) const override {
 *         return fTestFont;
 *     }
 *     sk_sp<SkTypeface> onMakeFromFile(const char path[], int ttcIndex) const override {
 *         return fTestFont;
 *     }
 *     sk_sp<SkTypeface> onLegacyMakeTypeface(const char familyName[], SkFontStyle) const override {
 *         return fTestFont;
 *     }
 *  private:
 *     sk_sp<SkTypeface> fTestFont;
 * }
 * ```
 */
public open class MockFontMgr public constructor(
  testFont: SkSp<SkTypeface>,
) : SkFontMgr() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> fTestFont
   * ```
   */
  private var fTestFont: SkSp<SkTypeface> = TODO("Initialize fTestFont")

  /**
   * C++ original:
   * ```cpp
   * int onCountFamilies() const override { return 1; }
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
   * sk_sp<SkFontStyleSet> onMatchFamily(const char familyName[]) const override {
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
   * sk_sp<SkTypeface> onMatchFamilyStyle(const char familyName[],
   *                                          const SkFontStyle& fontStyle) const override {
   *         return nullptr;
   *     }
   * ```
   */
  public override fun onMatchFamilyStyle(familyName: CharArray, fontStyle: SkFontStyle): SkSp<SkTypeface> {
    TODO("Implement onMatchFamilyStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMatchFamilyStyleCharacter(const char familyName[], const SkFontStyle&,
   *                                                   const char* bcp47[], int bcp47Count,
   *                                                   SkUnichar character) const override {
   *         return nullptr;
   *     }
   * ```
   */
  public override fun onMatchFamilyStyleCharacter(
    familyName: CharArray,
    param1: SkFontStyle,
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
   *         return fTestFont;
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
   *                                                 int ttcIndex) const override {
   *         return fTestFont;
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
   *                                                const SkFontArguments&) const override {
   *         return fTestFont;
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
   *         return fTestFont;
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
   *         return fTestFont;
   *     }
   * ```
   */
  public override fun onLegacyMakeTypeface(familyName: CharArray, param1: SkFontStyle): SkSp<SkTypeface> {
    TODO("Implement onLegacyMakeTypeface")
  }
}
