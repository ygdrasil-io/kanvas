package org.skia.foundation

import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.UByte
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * template <SkAlphaType kAT>
 * struct SkRGBA4f {
 *     float fR;  //!< red component
 *     float fG;  //!< green component
 *     float fB;  //!< blue component
 *     float fA;  //!< alpha component
 *
 *     /** Compares SkRGBA4f with other, and returns true if all components are equal.
 *
 *         @param other  SkRGBA4f to compare
 *         @return       true if SkRGBA4f equals other
 *     */
 *     bool operator==(const SkRGBA4f& other) const {
 *         return fA == other.fA && fR == other.fR && fG == other.fG && fB == other.fB;
 *     }
 *
 *     /** Compares SkRGBA4f with other, and returns true if not all components are equal.
 *
 *         @param other  SkRGBA4f to compare
 *         @return       true if SkRGBA4f is not equal to other
 *     */
 *     bool operator!=(const SkRGBA4f& other) const {
 *         return !(*this == other);
 *     }
 *
 *     /** Returns SkRGBA4f multiplied by scale.
 *
 *         @param scale  value to multiply by
 *         @return       SkRGBA4f as (fR * scale, fG * scale, fB * scale, fA * scale)
 *     */
 *     SkRGBA4f operator*(float scale) const {
 *         return { fR * scale, fG * scale, fB * scale, fA * scale };
 *     }
 *
 *     /** Returns SkRGBA4f multiplied component-wise by scale.
 *
 *         @param scale  SkRGBA4f to multiply by
 *         @return       SkRGBA4f as (fR * scale.fR, fG * scale.fG, fB * scale.fB, fA * scale.fA)
 *     */
 *     SkRGBA4f operator*(const SkRGBA4f& scale) const {
 *         return { fR * scale.fR, fG * scale.fG, fB * scale.fB, fA * scale.fA };
 *     }
 *
 *     /** Returns a pointer to components of SkRGBA4f, for array access.
 *
 *         @return       pointer to array [fR, fG, fB, fA]
 *     */
 *     const float* vec() const { return &fR; }
 *
 *     /** Returns a pointer to components of SkRGBA4f, for array access.
 *
 *         @return       pointer to array [fR, fG, fB, fA]
 *     */
 *     float* vec() { return &fR; }
 *
 *     /** As a std::array<float, 4> */
 *     std::array<float, 4> array() const { return {fR, fG, fB, fA}; }
 *
 *     /** Returns one component. Asserts if index is out of range and SK_DEBUG is defined.
 *
 *         @param index  one of: 0 (fR), 1 (fG), 2 (fB), 3 (fA)
 *         @return       value corresponding to index
 *     */
 *     float operator[](int index) const {
 *         SkASSERT(index >= 0 && index < 4);
 *         return this->vec()[index];
 *     }
 *
 *     /** Returns one component. Asserts if index is out of range and SK_DEBUG is defined.
 *
 *         @param index  one of: 0 (fR), 1 (fG), 2 (fB), 3 (fA)
 *         @return       value corresponding to index
 *     */
 *     float& operator[](int index) {
 *         SkASSERT(index >= 0 && index < 4);
 *         return this->vec()[index];
 *     }
 *
 *     /** Returns true if SkRGBA4f is an opaque color. Asserts if fA is out of range and
 *         SK_DEBUG is defined.
 *
 *         @return       true if SkRGBA4f is opaque
 *     */
 *     bool isOpaque() const {
 *         SkASSERT(fA <= 1.0f && fA >= 0.0f);
 *         return fA == 1.0f;
 *     }
 *
 *     /** Returns true if all channels are in [0, 1]. */
 *     bool fitsInBytes() const {
 *         SkASSERT(fA >= 0.0f && fA <= 1.0f);
 *         return fR >= 0.0f && fR <= 1.0f &&
 *                fG >= 0.0f && fG <= 1.0f &&
 *                fB >= 0.0f && fB <= 1.0f;
 *     }
 *
 *     /** Returns closest SkRGBA4f to SkColor. Only allowed if SkRGBA4f is unpremultiplied.
 *
 *         @param color   Color with Alpha, red, blue, and green components
 *         @return        SkColor as SkRGBA4f
 *
 *         example: https://fiddle.skia.org/c/@RGBA4f_FromColor
 *     */
 *     static SkRGBA4f FromColor(SkColor color);  // impl. depends on kAT
 *
 *     /** Returns closest SkColor to SkRGBA4f. Only allowed if SkRGBA4f is unpremultiplied.
 *
 *         @return       color as SkColor
 *
 *         example: https://fiddle.skia.org/c/@RGBA4f_toSkColor
 *     */
 *     SkColor toSkColor() const;  // impl. depends on kAT
 *
 *     /** Returns closest SkRGBA4f to SkPMColor. Only allowed if SkRGBA4f is premultiplied.
 *
 *         @return        SkPMColor as SkRGBA4f
 *     */
 *     static SkRGBA4f FromPMColor(SkPMColor);  // impl. depends on kAT
 *
 *     /** Returns SkRGBA4f premultiplied by alpha. Asserts at compile time if SkRGBA4f is
 *         already premultiplied.
 *
 *         @return       premultiplied color
 *     */
 *     SkRGBA4f<kPremul_SkAlphaType> premul() const {
 *         static_assert(kAT == kUnpremul_SkAlphaType, "");
 *         return { fR * fA, fG * fA, fB * fA, fA };
 *     }
 *
 *     /** Returns SkRGBA4f unpremultiplied by alpha. Asserts at compile time if SkRGBA4f is
 *         already unpremultiplied.
 *
 *         @return       unpremultiplied color
 *     */
 *     SkRGBA4f<kUnpremul_SkAlphaType> unpremul() const {
 *         static_assert(kAT == kPremul_SkAlphaType, "");
 *
 *         if (fA == 0.0f) {
 *             return { 0, 0, 0, 0 };
 *         } else {
 *             float invAlpha = 1 / fA;
 *             return { fR * invAlpha, fG * invAlpha, fB * invAlpha, fA };
 *         }
 *     }
 *
 *     // This produces bytes in RGBA order (eg GrColor). Impl. is the same, regardless of kAT
 *     uint32_t toBytes_RGBA() const;
 *     static SkRGBA4f FromBytes_RGBA(uint32_t color);
 *
 *     /**
 *       Returns a copy of the SkRGBA4f but with alpha component set to 1.0f.
 *
 *       @return         opaque color
 *     */
 *     SkRGBA4f makeOpaque() const {
 *         return { fR, fG, fB, 1.0f };
 *     }
 *
 *     /**
 *      Returns a copy of the SkRGBA4f but with the alpha component pinned to [0, 1].
 *
 *      @return          color with pinned alpha
 *     */
 *     SkRGBA4f pinAlpha() const {
 *         return { fR, fG, fB, SkTPin(fA, 0.f, 1.f) };
 *     }
 *
 *     /** Returns this color, having replaced its alpha value.
 *      */
 *     SkRGBA4f withAlpha(float a) const {
 *         return { fR, fG, fB, a };
 *     }
 *
 *     /** Returns this color, having replaced its alpha value specified as a byte.
 *      */
 *     SkRGBA4f withAlphaByte(uint8_t a) const {
 *         return { fR, fG, fB, a/255.f };
 *     }
 * }
 * ```
 */
public data class SkRGBA4fkPremulSkAlphaType public constructor(
  /**
   * C++ original:
   * ```cpp
   * float fR
   * ```
   */
  private var fR: Float,
  /**
   * C++ original:
   * ```cpp
   * float fG
   * ```
   */
  private var fG: Float,
  /**
   * C++ original:
   * ```cpp
   * float fB
   * ```
   */
  private var fB: Float,
  /**
   * C++ original:
   * ```cpp
   * float fA
   * ```
   */
  private var fA: Float,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkRGBA4f& other) const {
   *         return fA == other.fA && fR == other.fR && fG == other.fG && fB == other.fB;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkRGBA4f& other) const {
   *         return !(*this == other);
   *     }
   * ```
   */
  private operator fun times(scale: Float): SkRGBA4fkPremulSkAlphaType {
    TODO("Implement times")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRGBA4f operator*(float scale) const {
   *         return { fR * scale, fG * scale, fB * scale, fA * scale };
   *     }
   * ```
   */
  private operator fun times(scale: SkRGBA4f): SkRGBA4fkPremulSkAlphaType {
    TODO("Implement times")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRGBA4f operator*(const SkRGBA4f& scale) const {
   *         return { fR * scale.fR, fG * scale.fG, fB * scale.fB, fA * scale.fA };
   *     }
   * ```
   */
  private fun vec(): Float {
    TODO("Implement vec")
  }

  /**
   * C++ original:
   * ```cpp
   * const float* vec() const { return &fR; }
   * ```
   */
  private fun array(): Array<Float> {
    TODO("Implement array")
  }

  /**
   * C++ original:
   * ```cpp
   * float* vec() { return &fR; }
   * ```
   */
  private operator fun `get`(index: Int): Float {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * std::array<float, 4> array() const { return {fR, fG, fB, fA}; }
   * ```
   */
  private fun isOpaque(): Boolean {
    TODO("Implement isOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * float operator[](int index) const {
   *         SkASSERT(index >= 0 && index < 4);
   *         return this->vec()[index];
   *     }
   * ```
   */
  private fun fitsInBytes(): Boolean {
    TODO("Implement fitsInBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * float& operator[](int index) {
   *         SkASSERT(index >= 0 && index < 4);
   *         return this->vec()[index];
   *     }
   * ```
   */
  private fun toSkColor(): SkColor {
    TODO("Implement toSkColor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isOpaque() const {
   *         SkASSERT(fA <= 1.0f && fA >= 0.0f);
   *         return fA == 1.0f;
   *     }
   * ```
   */
  private fun premul(): Int {
    TODO("Implement premul")
  }

  /**
   * C++ original:
   * ```cpp
   * bool fitsInBytes() const {
   *         SkASSERT(fA >= 0.0f && fA <= 1.0f);
   *         return fR >= 0.0f && fR <= 1.0f &&
   *                fG >= 0.0f && fG <= 1.0f &&
   *                fB >= 0.0f && fB <= 1.0f;
   *     }
   * ```
   */
  private fun unpremul(): Int {
    TODO("Implement unpremul")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColor toSkColor() const
   * ```
   */
  private fun toBytesRGBA(): UInt {
    TODO("Implement toBytesRGBA")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRGBA4f<kPremul_SkAlphaType> premul() const {
   *         static_assert(kAT == kUnpremul_SkAlphaType, "");
   *         return { fR * fA, fG * fA, fB * fA, fA };
   *     }
   * ```
   */
  private fun makeOpaque(): SkRGBA4fkPremulSkAlphaType {
    TODO("Implement makeOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRGBA4f<kUnpremul_SkAlphaType> unpremul() const {
   *         static_assert(kAT == kPremul_SkAlphaType, "");
   *
   *         if (fA == 0.0f) {
   *             return { 0, 0, 0, 0 };
   *         } else {
   *             float invAlpha = 1 / fA;
   *             return { fR * invAlpha, fG * invAlpha, fB * invAlpha, fA };
   *         }
   *     }
   * ```
   */
  private fun pinAlpha(): SkRGBA4fkPremulSkAlphaType {
    TODO("Implement pinAlpha")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t toBytes_RGBA() const
   * ```
   */
  private fun withAlpha(a: Float): SkRGBA4fkPremulSkAlphaType {
    TODO("Implement withAlpha")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRGBA4f makeOpaque() const {
   *         return { fR, fG, fB, 1.0f };
   *     }
   * ```
   */
  private fun withAlphaByte(a: UByte): SkRGBA4fkPremulSkAlphaType {
    TODO("Implement withAlphaByte")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkRGBA4f FromColor(SkColor color)
     * ```
     */
    private fun fromColor(color: SkColor): SkRGBA4fkPremulSkAlphaType {
      TODO("Implement fromColor")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkRGBA4f FromPMColor(SkPMColor)
     * ```
     */
    private fun fromPMColor(param0: SkPMColor): SkRGBA4fkPremulSkAlphaType {
      TODO("Implement fromPMColor")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkRGBA4f FromBytes_RGBA(uint32_t color)
     * ```
     */
    private fun fromBytesRGBA(color: UInt): SkRGBA4fkPremulSkAlphaType {
      TODO("Implement fromBytesRGBA")
    }
  }
}
