package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkV3 {
 *     float x, y, z;
 *
 *     bool operator==(const SkV3& v) const {
 *         return x == v.x && y == v.y && z == v.z;
 *     }
 *     bool operator!=(const SkV3& v) const { return !(*this == v); }
 *
 *     static SkScalar Dot(const SkV3& a, const SkV3& b) { return a.x*b.x + a.y*b.y + a.z*b.z; }
 *     static SkV3   Cross(const SkV3& a, const SkV3& b) {
 *         return { a.y*b.z - a.z*b.y, a.z*b.x - a.x*b.z, a.x*b.y - a.y*b.x };
 *     }
 *     static SkV3 Normalize(const SkV3& v) { return v * (1.0f / v.length()); }
 *
 *     SkV3 operator-() const { return {-x, -y, -z}; }
 *     SkV3 operator+(const SkV3& v) const { return { x + v.x, y + v.y, z + v.z }; }
 *     SkV3 operator-(const SkV3& v) const { return { x - v.x, y - v.y, z - v.z }; }
 *
 *     SkV3 operator*(const SkV3& v) const {
 *         return { x*v.x, y*v.y, z*v.z };
 *     }
 *     friend SkV3 operator*(const SkV3& v, SkScalar s) {
 *         return { v.x*s, v.y*s, v.z*s };
 *     }
 *     friend SkV3 operator*(SkScalar s, const SkV3& v) { return v*s; }
 *
 *     void operator+=(SkV3 v) { *this = *this + v; }
 *     void operator-=(SkV3 v) { *this = *this - v; }
 *     void operator*=(SkV3 v) { *this = *this * v; }
 *     void operator*=(SkScalar s) { *this = *this * s; }
 *
 *     SkScalar lengthSquared() const { return Dot(*this, *this); }
 *     SkScalar length() const { return SkScalarSqrt(Dot(*this, *this)); }
 *
 *     SkScalar dot(const SkV3& v) const { return Dot(*this, v); }
 *     SkV3   cross(const SkV3& v) const { return Cross(*this, v); }
 *     SkV3 normalize()            const { return Normalize(*this); }
 *
 *     const float* ptr() const { return &x; }
 *     float* ptr() { return &x; }
 * }
 * ```
 */
public data class SkV3 public constructor(
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
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkV3& v) const {
   *         return x == v.x && y == v.y && z == v.z;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkV3& v) const { return !(*this == v); }
   * ```
   */
  public operator fun unaryMinus(): SkV3 {
    TODO("Implement unaryMinus")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV3 operator-() const { return {-x, -y, -z}; }
   * ```
   */
  public operator fun plus(v: SkV3): SkV3 {
    TODO("Implement plus")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV3 operator+(const SkV3& v) const { return { x + v.x, y + v.y, z + v.z }; }
   * ```
   */
  public operator fun minus(v: SkV3): SkV3 {
    TODO("Implement minus")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV3 operator-(const SkV3& v) const { return { x - v.x, y - v.y, z - v.z }; }
   * ```
   */
  public operator fun times(v: SkV3): SkV3 {
    TODO("Implement times")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV3 operator*(const SkV3& v) const {
   *         return { x*v.x, y*v.y, z*v.z };
   *     }
   * ```
   */
  public operator fun plusAssign(v: SkV3) {
    TODO("Implement plusAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator+=(SkV3 v) { *this = *this + v; }
   * ```
   */
  public operator fun minusAssign(v: SkV3) {
    TODO("Implement minusAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator-=(SkV3 v) { *this = *this - v; }
   * ```
   */
  public operator fun timesAssign(v: SkV3) {
    TODO("Implement timesAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator*=(SkV3 v) { *this = *this * v; }
   * ```
   */
  public operator fun timesAssign(s: SkScalar) {
    TODO("Implement timesAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator*=(SkScalar s) { *this = *this * s; }
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
  public fun dot(v: SkV3): Int {
    TODO("Implement dot")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar dot(const SkV3& v) const { return Dot(*this, v); }
   * ```
   */
  public fun cross(v: SkV3): SkV3 {
    TODO("Implement cross")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV3   cross(const SkV3& v) const { return Cross(*this, v); }
   * ```
   */
  public fun normalize(): SkV3 {
    TODO("Implement normalize")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV3 normalize()            const { return Normalize(*this); }
   * ```
   */
  public fun ptr(): Float {
    TODO("Implement ptr")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkScalar Dot(const SkV3& a, const SkV3& b) { return a.x*b.x + a.y*b.y + a.z*b.z; }
     * ```
     */
    public fun dot(a: SkV3, b: SkV3): Int {
      TODO("Implement dot")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkV3   Cross(const SkV3& a, const SkV3& b) {
     *         return { a.y*b.z - a.z*b.y, a.z*b.x - a.x*b.z, a.x*b.y - a.y*b.x };
     *     }
     * ```
     */
    public fun cross(a: SkV3, b: SkV3): SkV3 {
      TODO("Implement cross")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkV3 Normalize(const SkV3& v) { return v * (1.0f / v.length()); }
     * ```
     */
    public fun normalize(v: SkV3): SkV3 {
      TODO("Implement normalize")
    }
  }
}
