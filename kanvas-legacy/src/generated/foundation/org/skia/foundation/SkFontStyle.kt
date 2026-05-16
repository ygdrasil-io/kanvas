package org.skia.foundation

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkFontStyle {
 * public:
 *     enum Weight {
 *         kInvisible_Weight   =    0,
 *         kThin_Weight        =  100,
 *         kExtraLight_Weight  =  200,
 *         kLight_Weight       =  300,
 *         kNormal_Weight      =  400,
 *         kMedium_Weight      =  500,
 *         kSemiBold_Weight    =  600,
 *         kBold_Weight        =  700,
 *         kExtraBold_Weight   =  800,
 *         kBlack_Weight       =  900,
 *         kExtraBlack_Weight  = 1000,
 *     };
 *
 *     enum Width {
 *         kUltraCondensed_Width   = 1,
 *         kExtraCondensed_Width   = 2,
 *         kCondensed_Width        = 3,
 *         kSemiCondensed_Width    = 4,
 *         kNormal_Width           = 5,
 *         kSemiExpanded_Width     = 6,
 *         kExpanded_Width         = 7,
 *         kExtraExpanded_Width    = 8,
 *         kUltraExpanded_Width    = 9,
 *     };
 *
 *     enum Slant : uint8_t {
 *         kUpright_Slant,
 *         kItalic_Slant,
 *         kOblique_Slant,
 *     };
 *
 *     constexpr SkFontStyle(int weight, int width, Slant slant) : fValue(
 *         (SkTPin<int>(weight, kInvisible_Weight, kExtraBlack_Weight)) +
 *         (SkTPin<int>(width, kUltraCondensed_Width, kUltraExpanded_Width) << 16) +
 *         (SkTPin<int>(slant, kUpright_Slant, kOblique_Slant) << 24)
 *      ) { }
 *
 *     constexpr SkFontStyle() : SkFontStyle{kNormal_Weight, kNormal_Width, kUpright_Slant} { }
 *
 *     bool operator==(const SkFontStyle& rhs) const {
 *         return fValue == rhs.fValue;
 *     }
 *
 *     int weight() const { return fValue & 0xFFFF; }
 *     int width() const { return (fValue >> 16) & 0xFF; }
 *     Slant slant() const { return (Slant)((fValue >> 24) & 0xFF); }
 *
 *     static constexpr SkFontStyle Normal() {
 *         return SkFontStyle(kNormal_Weight, kNormal_Width, kUpright_Slant);
 *     }
 *     static constexpr SkFontStyle Bold() {
 *         return SkFontStyle(kBold_Weight,   kNormal_Width, kUpright_Slant);
 *     }
 *     static constexpr SkFontStyle Italic() {
 *         return SkFontStyle(kNormal_Weight, kNormal_Width, kItalic_Slant );
 *     }
 *     static constexpr SkFontStyle BoldItalic() {
 *         return SkFontStyle(kBold_Weight,   kNormal_Width, kItalic_Slant );
 *     }
 *
 * private:
 *     friend class SkTypefaceProxyPrototype;  // To serialize fValue
 *     int32_t fValue;
 * }
 * ```
 */
public data class SkFontStyle public constructor(
  /**
   * C++ original:
   * ```cpp
   * int32_t fValue
   * ```
   */
  private var fValue: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkFontStyle& rhs) const {
   *         return fValue == rhs.fValue;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * int weight() const { return fValue & 0xFFFF; }
   * ```
   */
  public fun weight(): Int {
    TODO("Implement weight")
  }

  /**
   * C++ original:
   * ```cpp
   * int width() const { return (fValue >> 16) & 0xFF; }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * Slant slant() const { return (Slant)((fValue >> 24) & 0xFF); }
   * ```
   */
  public fun slant(): Slant {
    TODO("Implement slant")
  }

  public enum class Weight {
    kInvisible_Weight,
    kThin_Weight,
    kExtraLight_Weight,
    kLight_Weight,
    kNormal_Weight,
    kMedium_Weight,
    kSemiBold_Weight,
    kBold_Weight,
    kExtraBold_Weight,
    kBlack_Weight,
    kExtraBlack_Weight,
  }

  public enum class Width {
    kUltraCondensed_Width,
    kExtraCondensed_Width,
    kCondensed_Width,
    kSemiCondensed_Width,
    kNormal_Width,
    kSemiExpanded_Width,
    kExpanded_Width,
    kExtraExpanded_Width,
    kUltraExpanded_Width,
  }

  public enum class Slant {
    kUpright_Slant,
    kItalic_Slant,
    kOblique_Slant,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr SkFontStyle Normal() {
     *         return SkFontStyle(kNormal_Weight, kNormal_Width, kUpright_Slant);
     *     }
     * ```
     */
    public fun normal(): SkFontStyle {
      TODO("Implement normal")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr SkFontStyle Bold() {
     *         return SkFontStyle(kBold_Weight,   kNormal_Width, kUpright_Slant);
     *     }
     * ```
     */
    public fun bold(): SkFontStyle {
      TODO("Implement bold")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr SkFontStyle Italic() {
     *         return SkFontStyle(kNormal_Weight, kNormal_Width, kItalic_Slant );
     *     }
     * ```
     */
    public fun italic(): SkFontStyle {
      TODO("Implement italic")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr SkFontStyle BoldItalic() {
     *         return SkFontStyle(kBold_Weight,   kNormal_Width, kItalic_Slant );
     *     }
     * ```
     */
    public fun boldItalic(): SkFontStyle {
      TODO("Implement boldItalic")
    }
  }
}
