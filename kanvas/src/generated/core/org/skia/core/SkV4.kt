package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Float
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkV4 {
 *     float x, y, z, w;
 *
 *     bool operator==(const SkV4& v) const {
 *         return x == v.x && y == v.y && z == v.z && w == v.w;
 *     }
 *     bool operator!=(const SkV4& v) const { return !(*this == v); }
 *
 *     static SkScalar Dot(const SkV4& a, const SkV4& b) {
 *         return a.x*b.x + a.y*b.y + a.z*b.z + a.w*b.w;
 *     }
 *     static SkV4 Normalize(const SkV4& v) { return v * (1.0f / v.length()); }
 *
 *     SkV4 operator-() const { return {-x, -y, -z, -w}; }
 *     SkV4 operator+(const SkV4& v) const { return { x + v.x, y + v.y, z + v.z, w + v.w }; }
 *     SkV4 operator-(const SkV4& v) const { return { x - v.x, y - v.y, z - v.z, w - v.w }; }
 *
 *     SkV4 operator*(const SkV4& v) const {
 *         return { x*v.x, y*v.y, z*v.z, w*v.w };
 *     }
 *     friend SkV4 operator*(const SkV4& v, SkScalar s) {
 *         return { v.x*s, v.y*s, v.z*s, v.w*s };
 *     }
 *     friend SkV4 operator*(SkScalar s, const SkV4& v) { return v*s; }
 *
 *     SkScalar lengthSquared() const { return Dot(*this, *this); }
 *     SkScalar length() const { return SkScalarSqrt(Dot(*this, *this)); }
 *
 *     SkScalar dot(const SkV4& v) const { return Dot(*this, v); }
 *     SkV4 normalize()            const { return Normalize(*this); }
 *
 *     const float* ptr() const { return &x; }
 *     float* ptr() { return &x; }
 *
 *     float operator[](int i) const {
 *         SkASSERT(i >= 0 && i < 4);
 *         return this->ptr()[i];
 *     }
 *     float& operator[](int i) {
 *         SkASSERT(i >= 0 && i < 4);
 *         return this->ptr()[i];
 *     }
 * }
 * ```
 */
public data class SkV4 public constructor(
  /**
   * C++ original:
   * ```cpp
   * float x
   * ```
   */
  public var x: Float,
  /**
   * C++ original:
   * ```cpp
   * float x, y
   * ```
   */
  public var y: Float,
  /**
   * C++ original:
   * ```cpp
   * float x, y, z
   * ```
   */
  public var z: Float,
  /**
   * C++ original:
   * ```cpp
   * float x, y, z, w
   * ```
   */
  public var w: Float,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkV4& v) const {
   *         return x == v.x && y == v.y && z == v.z && w == v.w;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkV4& v) const { return !(*this == v); }
   * ```
   */
  public operator fun unaryMinus(): SkV4 {
    TODO("Implement unaryMinus")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV4 operator-() const { return {-x, -y, -z, -w}; }
   * ```
   */
  public operator fun plus(v: SkV4): SkV4 {
    TODO("Implement plus")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV4 operator+(const SkV4& v) const { return { x + v.x, y + v.y, z + v.z, w + v.w }; }
   * ```
   */
  public operator fun minus(v: SkV4): SkV4 {
    TODO("Implement minus")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV4 operator-(const SkV4& v) const { return { x - v.x, y - v.y, z - v.z, w - v.w }; }
   * ```
   */
  public operator fun times(v: SkV4): SkV4 {
    TODO("Implement times")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV4 operator*(const SkV4& v) const {
   *         return { x*v.x, y*v.y, z*v.z, w*v.w };
   *     }
   * ```
   */
  public fun lengthSquared(): Int {
    TODO("Implement lengthSquared")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar lengthSquared() const { return Dot(*this, *this); }
   * ```
   */
  public fun length(): Int {
    TODO("Implement length")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar length() const { return SkScalarSqrt(Dot(*this, *this)); }
   * ```
   */
  public fun dot(v: SkV4): Int {
    TODO("Implement dot")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar dot(const SkV4& v) const { return Dot(*this, v); }
   * ```
   */
  public fun normalize(): SkV4 {
    TODO("Implement normalize")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV4 normalize()            const { return Normalize(*this); }
   * ```
   */
  public fun ptr(): Float {
    TODO("Implement ptr")
  }

  /**
   * C++ original:
   * ```cpp
   * const float* ptr() const { return &x; }
   * ```
   */
  public operator fun `get`(i: Int): Float {
    TODO("Implement get")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkScalar Dot(const SkV4& a, const SkV4& b) {
     *         return a.x*b.x + a.y*b.y + a.z*b.z + a.w*b.w;
     *     }
     * ```
     */
    public fun dot(a: SkV4, b: SkV4): Int {
      TODO("Implement dot")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkV4 Normalize(const SkV4& v) { return v * (1.0f / v.length()); }
     * ```
     */
    public fun normalize(v: SkV4): SkV4 {
      TODO("Implement normalize")
    }
  }
}
