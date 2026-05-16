package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface

/**
 * C++ original:
 * ```cpp
 * class FontArguments {
 * public:
 *     FontArguments(const SkFontArguments&);
 *     FontArguments(const FontArguments&) = default;
 *     FontArguments(FontArguments&&) = default;
 *
 *     FontArguments& operator=(const FontArguments&) = default;
 *     FontArguments& operator=(FontArguments&&) = default;
 *
 *     sk_sp<SkTypeface> CloneTypeface(const sk_sp<SkTypeface>& typeface) const;
 *
 *     friend bool operator==(const FontArguments& a, const FontArguments& b);
 *     friend bool operator!=(const FontArguments& a, const FontArguments& b);
 *     friend struct std::hash<FontArguments>;
 *
 * private:
 *     FontArguments() = delete;
 *
 *     int fCollectionIndex;
 *     std::vector<SkFontArguments::VariationPosition::Coordinate> fCoordinates;
 *     int fPaletteIndex;
 *     std::vector<SkFontArguments::Palette::Override> fPaletteOverrides;
 * }
 * ```
 */
public data class FontArguments public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fCollectionIndex
   * ```
   */
  private var fCollectionIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<SkFontArguments::VariationPosition::Coordinate> fCoordinates
   * ```
   */
  private var fCoordinates: Int,
  /**
   * C++ original:
   * ```cpp
   * int fPaletteIndex
   * ```
   */
  private var fPaletteIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<SkFontArguments::Palette::Override> fPaletteOverrides
   * ```
   */
  private var fPaletteOverrides: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * FontArguments& operator=(const FontArguments&) = default
   * ```
   */
  public fun assign(param0: FontArguments) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * FontArguments& operator=(FontArguments&&) = default
   * ```
   */
  public fun cloneTypeface(typeface: SkSp<SkTypeface>): Int {
    TODO("Implement cloneTypeface")
  }
}
