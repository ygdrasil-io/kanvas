package org.skia.modules

import kotlin.Int
import kotlin.String
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkFontStyleSet
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface

/**
 * C++ original:
 * ```cpp
 * class TypefaceFontStyleSet : public SkFontStyleSet {
 * public:
 *     explicit TypefaceFontStyleSet(const SkString& familyName);
 *
 *     int count() override;
 *     void getStyle(int index, SkFontStyle*, SkString* name) override;
 *     sk_sp<SkTypeface> createTypeface(int index) override;
 *     sk_sp<SkTypeface> matchStyle(const SkFontStyle& pattern) override;
 *
 *     SkString getFamilyName() const { return fFamilyName; }
 *     SkString getAlias() const { return fAlias; }
 *     void appendTypeface(sk_sp<SkTypeface> typeface);
 *
 * private:
 *     skia_private::TArray<sk_sp<SkTypeface>> fStyles;
 *     SkString fFamilyName;
 *     SkString fAlias;
 * }
 * ```
 */
public open class TypefaceFontStyleSet public constructor(
  familyName: String,
) : SkFontStyleSet() {
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<SkTypeface>> fStyles
   * ```
   */
  private var fStyles: Int = TODO("Initialize fStyles")

  /**
   * C++ original:
   * ```cpp
   * SkString fFamilyName
   * ```
   */
  private var fFamilyName: Int = TODO("Initialize fFamilyName")

  /**
   * C++ original:
   * ```cpp
   * SkString fAlias
   * ```
   */
  private var fAlias: Int = TODO("Initialize fAlias")

  /**
   * C++ original:
   * ```cpp
   * int TypefaceFontStyleSet::count() { return fStyles.size(); }
   * ```
   */
  public override fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * void TypefaceFontStyleSet::getStyle(int index, SkFontStyle* style, SkString* name) {
   *     SkASSERT(index < fStyles.size());
   *     if (style) {
   *         *style = fStyles[index]->fontStyle();
   *     }
   *     if (name) {
   *         *name = fFamilyName;
   *     }
   * }
   * ```
   */
  public override fun getStyle(
    index: Int,
    style: SkFontStyle?,
    name: String?,
  ) {
    TODO("Implement getStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> TypefaceFontStyleSet::createTypeface(int index) {
   *     SkASSERT(index < fStyles.size());
   *     return fStyles[index];
   * }
   * ```
   */
  public override fun createTypeface(index: Int): Int {
    TODO("Implement createTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> TypefaceFontStyleSet::matchStyle(const SkFontStyle& pattern) {
   *     return this->matchStyleCSS3(pattern);
   * }
   * ```
   */
  public override fun matchStyle(pattern: SkFontStyle): Int {
    TODO("Implement matchStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getFamilyName() const { return fFamilyName; }
   * ```
   */
  public fun getFamilyName(): Int {
    TODO("Implement getFamilyName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getAlias() const { return fAlias; }
   * ```
   */
  public fun getAlias(): Int {
    TODO("Implement getAlias")
  }

  /**
   * C++ original:
   * ```cpp
   * void TypefaceFontStyleSet::appendTypeface(sk_sp<SkTypeface> typeface) {
   *     if (typeface.get() != nullptr) {
   *         fStyles.emplace_back(std::move(typeface));
   *     }
   * }
   * ```
   */
  public fun appendTypeface(typeface: SkSp<SkTypeface>) {
    TODO("Implement appendTypeface")
  }
}
