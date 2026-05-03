package org.skia.tools

import kotlin.Int
import kotlin.String
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkFontStyleSet
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface

/**
 * C++ original:
 * ```cpp
 * class FontStyleSet final : public SkFontStyleSet {
 * public:
 *     FontStyleSet(const char* familyName) : fFamilyName(familyName) {}
 *     struct TypefaceEntry {
 *         TypefaceEntry(sk_sp<SkTypeface> typeface, SkFontStyle style, const char* styleName)
 *                 : fTypeface(std::move(typeface)), fStyle(style), fStyleName(styleName) {}
 *         sk_sp<SkTypeface> fTypeface;
 *         SkFontStyle       fStyle;
 *         const char*       fStyleName;
 *     };
 *
 *     int count() override { return fTypefaces.size(); }
 *
 *     void getStyle(int index, SkFontStyle* style, SkString* name) override {
 *         if (style) {
 *             *style = fTypefaces[index].fStyle;
 *         }
 *         if (name) {
 *             *name = fTypefaces[index].fStyleName;
 *         }
 *     }
 *
 *     sk_sp<SkTypeface> createTypeface(int index) override {
 *         return fTypefaces[index].fTypeface;
 *     }
 *
 *     sk_sp<SkTypeface> matchStyle(const SkFontStyle& pattern) override {
 *         return this->matchStyleCSS3(pattern);
 *     }
 *
 *     SkString getFamilyName() { return fFamilyName; }
 *
 *     std::vector<TypefaceEntry> fTypefaces;
 *     SkString                   fFamilyName;
 * }
 * ```
 */
public class FontStyleSet public constructor(
  familyName: String?,
) : SkFontStyleSet() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<TypefaceEntry> fTypefaces
   * ```
   */
  public var fTypefaces: Int = TODO("Initialize fTypefaces")

  /**
   * C++ original:
   * ```cpp
   * SkString                   fFamilyName
   * ```
   */
  public var fFamilyName: String = TODO("Initialize fFamilyName")

  /**
   * C++ original:
   * ```cpp
   * int count() override { return fTypefaces.size(); }
   * ```
   */
  public override fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * void getStyle(int index, SkFontStyle* style, SkString* name) override {
   *         if (style) {
   *             *style = fTypefaces[index].fStyle;
   *         }
   *         if (name) {
   *             *name = fTypefaces[index].fStyleName;
   *         }
   *     }
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
   * sk_sp<SkTypeface> createTypeface(int index) override {
   *         return fTypefaces[index].fTypeface;
   *     }
   * ```
   */
  public override fun createTypeface(index: Int): SkSp<SkTypeface> {
    TODO("Implement createTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> matchStyle(const SkFontStyle& pattern) override {
   *         return this->matchStyleCSS3(pattern);
   *     }
   * ```
   */
  public override fun matchStyle(pattern: SkFontStyle): SkSp<SkTypeface> {
    TODO("Implement matchStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getFamilyName() { return fFamilyName; }
   * ```
   */
  public fun getFamilyName(): String {
    TODO("Implement getFamilyName")
  }

  public data class TypefaceEntry public constructor(
    public var fTypeface: SkSp<SkTypeface>,
    public var fStyle: SkFontStyle,
    public val fStyleName: String?,
  )
}
