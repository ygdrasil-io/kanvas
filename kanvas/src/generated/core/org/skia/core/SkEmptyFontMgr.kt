package org.skia.core

import kotlin.CharArray
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SkEmptyFontMgr : public SkFontMgr {
 * protected:
 *     int onCountFamilies() const override {
 *         return 0;
 *     }
 *     void onGetFamilyName(int index, SkString* familyName) const override {
 *         SkDEBUGFAIL("onGetFamilyName called with bad index");
 *     }
 *     sk_sp<SkFontStyleSet> onCreateStyleSet(int index) const override {
 *         SkDEBUGFAIL("onCreateStyleSet called with bad index");
 *         return nullptr;
 *     }
 *     sk_sp<SkFontStyleSet> onMatchFamily(const char[]) const override {
 *         return SkFontStyleSet::CreateEmpty();
 *     }
 *
 *     sk_sp<SkTypeface> onMatchFamilyStyle(const char[], const SkFontStyle&) const override {
 *         return nullptr;
 *     }
 *     sk_sp<SkTypeface> onMatchFamilyStyleCharacter(const char familyName[],
 *                                                   const SkFontStyle& style,
 *                                                   const char* bcp47[],
 *                                                   int bcp47Count,
 *                                                   SkUnichar character) const override {
 *         return nullptr;
 *     }
 *
 *     sk_sp<SkTypeface> onMakeFromData(sk_sp<SkData>, int) const override {
 *         return nullptr;
 *     }
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
 *     sk_sp<SkTypeface> onLegacyMakeTypeface(const char [], SkFontStyle) const override {
 *         return nullptr;
 *     }
 * }
 * ```
 */
public open class SkEmptyFontMgr : SkFontMgr() {
  /**
   * C++ original:
   * ```cpp
   * int onCountFamilies() const override {
   *         return 0;
   *     }
   * ```
   */
  protected override fun onCountFamilies(): Int {
    TODO("Implement onCountFamilies")
  }

  /**
   * C++ original:
   * ```cpp
   * void onGetFamilyName(int index, SkString* familyName) const override {
   *         SkDEBUGFAIL("onGetFamilyName called with bad index");
   *     }
   * ```
   */
  protected override fun onGetFamilyName(index: Int, familyName: String?) {
    TODO("Implement onGetFamilyName")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontStyleSet> onCreateStyleSet(int index) const override {
   *         SkDEBUGFAIL("onCreateStyleSet called with bad index");
   *         return nullptr;
   *     }
   * ```
   */
  protected override fun onCreateStyleSet(index: Int): SkSp<SkFontStyleSet> {
    TODO("Implement onCreateStyleSet")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontStyleSet> onMatchFamily(const char[]) const override {
   *         return SkFontStyleSet::CreateEmpty();
   *     }
   * ```
   */
  protected override fun onMatchFamily(param0: CharArray): SkSp<SkFontStyleSet> {
    TODO("Implement onMatchFamily")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMatchFamilyStyle(const char[], const SkFontStyle&) const override {
   *         return nullptr;
   *     }
   * ```
   */
  protected override fun onMatchFamilyStyle(param0: CharArray, param1: SkFontStyle): SkSp<SkTypeface> {
    TODO("Implement onMatchFamilyStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMatchFamilyStyleCharacter(const char familyName[],
   *                                                   const SkFontStyle& style,
   *                                                   const char* bcp47[],
   *                                                   int bcp47Count,
   *                                                   SkUnichar character) const override {
   *         return nullptr;
   *     }
   * ```
   */
  protected override fun onMatchFamilyStyleCharacter(
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
   * sk_sp<SkTypeface> onMakeFromData(sk_sp<SkData>, int) const override {
   *         return nullptr;
   *     }
   * ```
   */
  protected override fun onMakeFromData(param0: SkSp<SkData>, param1: Int): SkSp<SkTypeface> {
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
  protected override fun onMakeFromStreamIndex(param0: SkStreamAsset?, param1: Int): SkSp<SkTypeface> {
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
  protected override fun onMakeFromStreamArgs(param0: SkStreamAsset?, param1: SkFontArguments): SkSp<SkTypeface> {
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
  protected override fun onMakeFromFile(param0: CharArray, param1: Int): SkSp<SkTypeface> {
    TODO("Implement onMakeFromFile")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onLegacyMakeTypeface(const char [], SkFontStyle) const override {
   *         return nullptr;
   *     }
   * ```
   */
  protected override fun onLegacyMakeTypeface(param0: CharArray, param1: SkFontStyle): SkSp<SkTypeface> {
    TODO("Implement onLegacyMakeTypeface")
  }
}
