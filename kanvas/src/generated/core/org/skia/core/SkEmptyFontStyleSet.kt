package org.skia.core

import kotlin.Int
import kotlin.String
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SkEmptyFontStyleSet : public SkFontStyleSet {
 * public:
 *     int count() override { return 0; }
 *     void getStyle(int, SkFontStyle*, SkString*) override {
 *         SkDEBUGFAIL("SkFontStyleSet::getStyle called on empty set");
 *     }
 *     sk_sp<SkTypeface> createTypeface(int index) override {
 *         SkDEBUGFAIL("SkFontStyleSet::createTypeface called on empty set");
 *         return nullptr;
 *     }
 *     sk_sp<SkTypeface> matchStyle(const SkFontStyle&) override {
 *         return nullptr;
 *     }
 * }
 * ```
 */
public open class SkEmptyFontStyleSet : SkFontStyleSet() {
  /**
   * C++ original:
   * ```cpp
   * int count() override { return 0; }
   * ```
   */
  public override fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * void getStyle(int, SkFontStyle*, SkString*) override {
   *         SkDEBUGFAIL("SkFontStyleSet::getStyle called on empty set");
   *     }
   * ```
   */
  public override fun getStyle(
    param0: Int,
    param1: SkFontStyle?,
    param2: String?,
  ) {
    TODO("Implement getStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> createTypeface(int index) override {
   *         SkDEBUGFAIL("SkFontStyleSet::createTypeface called on empty set");
   *         return nullptr;
   *     }
   * ```
   */
  public override fun createTypeface(index: Int): SkSp<SkTypeface> {
    TODO("Implement createTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> matchStyle(const SkFontStyle&) override {
   *         return nullptr;
   *     }
   * ```
   */
  public override fun matchStyle(param0: SkFontStyle): SkSp<SkTypeface> {
    TODO("Implement matchStyle")
  }
}
