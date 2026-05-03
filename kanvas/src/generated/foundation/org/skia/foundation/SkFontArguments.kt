package org.skia.foundation

import kotlin.Float
import kotlin.Int
import kotlin.UShort

/**
 * C++ original:
 * ```cpp
 * struct SkFontArguments {
 *     struct VariationPosition {
 *         struct Coordinate {
 *             SkFourByteTag axis;
 *             float value;
 *         };
 *         const Coordinate* coordinates;
 *         int coordinateCount;
 *     };
 *
 *     /** Specify a palette to use and overrides for palette entries.
 *      *
 *      *  `overrides` is a list of pairs of palette entry index and color.
 *      *  The overriden palette entries will use the associated color.
 *      *  Override pairs with palette entry indices out of range will not be applied.
 *      *  Later override entries override earlier ones.
 *      */
 *     struct Palette {
 *         struct Override {
 *             uint16_t index;
 *             SkColor color;
 *         };
 *         int index;
 *         const Override* overrides;
 *         int overrideCount;
 *     };
 *
 *     SkFontArguments()
 *             : fCollectionIndex(0)
 *             , fVariationDesignPosition{nullptr, 0}
 *             , fPalette{0, nullptr, 0} {}
 *
 *     /** Specify the index of the desired font.
 *      *
 *      *  Font formats like ttc, dfont, cff, cid, pfr, t42, t1, and fon may actually be indexed
 *      *  collections of fonts.
 *      */
 *     SkFontArguments& setCollectionIndex(int collectionIndex) {
 *         fCollectionIndex = collectionIndex;
 *         return *this;
 *     }
 *
 *     /** Specify a position in the variation design space.
 *      *
 *      *  Any axis not specified will use the default value.
 *      *  Any specified axis not actually present in the font will be ignored.
 *      *
 *      *  @param position not copied. The value must remain valid for life of SkFontArguments.
 *      */
 *     SkFontArguments& setVariationDesignPosition(VariationPosition position) {
 *         fVariationDesignPosition.coordinates = position.coordinates;
 *         fVariationDesignPosition.coordinateCount = position.coordinateCount;
 *         return *this;
 *     }
 *
 *     int getCollectionIndex() const {
 *         return fCollectionIndex;
 *     }
 *
 *     VariationPosition getVariationDesignPosition() const {
 *         return fVariationDesignPosition;
 *     }
 *
 *     SkFontArguments& setPalette(Palette palette) {
 *         fPalette.index = palette.index;
 *         fPalette.overrides = palette.overrides;
 *         fPalette.overrideCount = palette.overrideCount;
 *         return *this;
 *     }
 *
 *     Palette getPalette() const { return fPalette; }
 *
 * private:
 *     int fCollectionIndex;
 *     VariationPosition fVariationDesignPosition;
 *     Palette fPalette;
 * }
 * ```
 */
public data class SkFontArguments public constructor(
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
   * VariationPosition fVariationDesignPosition
   * ```
   */
  private var fVariationDesignPosition: VariationPosition,
  /**
   * C++ original:
   * ```cpp
   * Palette fPalette
   * ```
   */
  private var fPalette: Palette,
) {
  /**
   * C++ original:
   * ```cpp
   * SkFontArguments& setCollectionIndex(int collectionIndex) {
   *         fCollectionIndex = collectionIndex;
   *         return *this;
   *     }
   * ```
   */
  public fun setCollectionIndex(collectionIndex: Int): SkFontArguments {
    TODO("Implement setCollectionIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFontArguments& setVariationDesignPosition(VariationPosition position) {
   *         fVariationDesignPosition.coordinates = position.coordinates;
   *         fVariationDesignPosition.coordinateCount = position.coordinateCount;
   *         return *this;
   *     }
   * ```
   */
  public fun setVariationDesignPosition(position: VariationPosition): SkFontArguments {
    TODO("Implement setVariationDesignPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * int getCollectionIndex() const {
   *         return fCollectionIndex;
   *     }
   * ```
   */
  public fun getCollectionIndex(): Int {
    TODO("Implement getCollectionIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * VariationPosition getVariationDesignPosition() const {
   *         return fVariationDesignPosition;
   *     }
   * ```
   */
  public fun getVariationDesignPosition(): VariationPosition {
    TODO("Implement getVariationDesignPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFontArguments& setPalette(Palette palette) {
   *         fPalette.index = palette.index;
   *         fPalette.overrides = palette.overrides;
   *         fPalette.overrideCount = palette.overrideCount;
   *         return *this;
   *     }
   * ```
   */
  public fun setPalette(palette: Palette): SkFontArguments {
    TODO("Implement setPalette")
  }

  /**
   * C++ original:
   * ```cpp
   * Palette getPalette() const { return fPalette; }
   * ```
   */
  public fun getPalette(): Palette {
    TODO("Implement getPalette")
  }

  public data class VariationPosition public constructor(
    public val coordinates: VariationPosition.Coordinate?,
    public var coordinateCount: Int,
  ) {
    public data class Coordinate public constructor(
      public var axis: Int,
      public var `value`: Float,
    )
  }

  public data class Palette public constructor(
    public var index: Int,
    public val overrides: Palette.Override?,
    public var overrideCount: Int,
  ) {
    public data class Override public constructor(
      public var index: UShort,
      public var color: Int,
    )
  }
}
