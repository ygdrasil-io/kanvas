package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * template<typename E>
 * class SkEnumBitMask {
 *     using I = std::underlying_type_t<E>;
 * public:
 *     SK_ALWAYS_INLINE constexpr SkEnumBitMask() : SkEnumBitMask(I(0)) {}
 *     SK_ALWAYS_INLINE constexpr SkEnumBitMask(E e) : SkEnumBitMask(static_cast<I>(e)) {}
 *
 *     SK_ALWAYS_INLINE constexpr explicit operator bool() const { return fValue; }
 *     SK_ALWAYS_INLINE constexpr I value() const                { return fValue; }
 *
 *     SK_ALWAYS_INLINE constexpr bool operator==(SkEnumBitMask m) const { return fValue == m.fValue; }
 *     SK_ALWAYS_INLINE constexpr bool operator!=(SkEnumBitMask m) const { return fValue != m.fValue; }
 *
 *     SK_ALWAYS_INLINE constexpr SkEnumBitMask operator|(SkEnumBitMask m) const {
 *         return SkEnumBitMask(fValue | m.fValue);
 *     }
 *     SK_ALWAYS_INLINE constexpr SkEnumBitMask operator&(SkEnumBitMask m) const {
 *         return SkEnumBitMask(fValue & m.fValue);
 *     }
 *     SK_ALWAYS_INLINE constexpr SkEnumBitMask operator^(SkEnumBitMask m) const {
 *         return SkEnumBitMask(fValue ^ m.fValue);
 *     }
 *     SK_ALWAYS_INLINE constexpr SkEnumBitMask operator~() const { return SkEnumBitMask(~fValue); }
 *
 *     SK_ALWAYS_INLINE SkEnumBitMask& operator|=(SkEnumBitMask m) { return *this = *this | m; }
 *     SK_ALWAYS_INLINE SkEnumBitMask& operator&=(SkEnumBitMask m) { return *this = *this & m; }
 *     SK_ALWAYS_INLINE SkEnumBitMask& operator^=(SkEnumBitMask m) { return *this = *this ^ m; }
 *
 * private:
 *     SK_ALWAYS_INLINE constexpr explicit SkEnumBitMask(I value) : fValue(value) {}
 *
 *     I fValue;
 * }
 * ```
 */
public open class SkEnumBitMask<E> public constructor() {
  /**
   * C++ original:
   * ```cpp
   * I fValue
   * ```
   */
  private var fValue: Int = TODO("Initialize fValue")

  /**
   * C++ original:
   * ```cpp
   * SK_ALWAYS_INLINE constexpr SkEnumBitMask() : SkEnumBitMask(I(0)) {}
   * ```
   */
  public constructor(e: E) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SK_ALWAYS_INLINE constexpr SkEnumBitMask(E e) : SkEnumBitMask(static_cast<I>(e)) {}
   * ```
   */
  public constructor(`value`: I) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SK_ALWAYS_INLINE constexpr I value() const                { return fValue; }
   * ```
   */
  public fun `value`(): Int {
    TODO("Implement value")
  }

  /**
   * C++ original:
   * ```cpp
   * SK_ALWAYS_INLINE constexpr bool operator==(SkEnumBitMask m) const { return fValue == m.fValue; }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * SK_ALWAYS_INLINE constexpr bool operator!=(SkEnumBitMask m) const { return fValue != m.fValue; }
   * ```
   */
  public fun or(m: SkEnumBitMask<E>): SkEnumBitMask<E> {
    TODO("Implement or")
  }

  /**
   * C++ original:
   * ```cpp
   * SK_ALWAYS_INLINE constexpr SkEnumBitMask operator|(SkEnumBitMask m) const {
   *         return SkEnumBitMask(fValue | m.fValue);
   *     }
   * ```
   */
  public fun addressOf(m: SkEnumBitMask<E>): SkEnumBitMask<E> {
    TODO("Implement addressOf")
  }

  /**
   * C++ original:
   * ```cpp
   * SK_ALWAYS_INLINE constexpr SkEnumBitMask operator&(SkEnumBitMask m) const {
   *         return SkEnumBitMask(fValue & m.fValue);
   *     }
   * ```
   */
  public fun xor(m: SkEnumBitMask<E>): SkEnumBitMask<E> {
    TODO("Implement xor")
  }

  /**
   * C++ original:
   * ```cpp
   * SK_ALWAYS_INLINE constexpr SkEnumBitMask operator^(SkEnumBitMask m) const {
   *         return SkEnumBitMask(fValue ^ m.fValue);
   *     }
   * ```
   */
  public operator fun inv(): SkEnumBitMask<E> {
    TODO("Implement inv")
  }

  /**
   * C++ original:
   * ```cpp
   * SK_ALWAYS_INLINE constexpr SkEnumBitMask operator~() const { return SkEnumBitMask(~fValue); }
   * ```
   */
  public fun orAssign(m: SkEnumBitMask<E>) {
    TODO("Implement orAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * SK_ALWAYS_INLINE SkEnumBitMask& operator|=(SkEnumBitMask m) { return *this = *this | m; }
   * ```
   */
  public fun andAssign(m: SkEnumBitMask<E>) {
    TODO("Implement andAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * SK_ALWAYS_INLINE SkEnumBitMask& operator&=(SkEnumBitMask m) { return *this = *this & m; }
   * ```
   */
  public fun xorAssign(m: SkEnumBitMask<E>) {
    TODO("Implement xorAssign")
  }
}
