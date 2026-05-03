package org.skia.math

import kotlin.Any
import kotlin.Boolean
import kotlin.Float
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkV2 {
 *     float x, y;
 *
 *     bool operator==(SkV2 v) const { return x == v.x && y == v.y; }
 *     bool operator!=(SkV2 v) const { return !(*this == v); }
 *
 *     static SkScalar   Dot(SkV2 a, SkV2 b) { return a.x * b.x + a.y * b.y; }
 *     static SkScalar Cross(SkV2 a, SkV2 b) { return a.x * b.y - a.y * b.x; }
 *     static SkV2 Normalize(SkV2 v) { return v * (1.0f / v.length()); }
 *
 *     SkV2 operator-() const { return {-x, -y}; }
 *     SkV2 operator+(SkV2 v) const { return {x+v.x, y+v.y}; }
 *     SkV2 operator-(SkV2 v) const { return {x-v.x, y-v.y}; }
 *
 *     SkV2 operator*(SkV2 v) const { return {x*v.x, y*v.y}; }
 *     friend SkV2 operator*(SkV2 v, SkScalar s) { return {v.x*s, v.y*s}; }
 *     friend SkV2 operator*(SkScalar s, SkV2 v) { return {v.x*s, v.y*s}; }
 *     friend SkV2 operator/(SkV2 v, SkScalar s) { return {v.x/s, v.y/s}; }
 *     friend SkV2 operator/(SkScalar s, SkV2 v) { return {s/v.x, s/v.y}; }
 *
 *     void operator+=(SkV2 v) { *this = *this + v; }
 *     void operator-=(SkV2 v) { *this = *this - v; }
 *     void operator*=(SkV2 v) { *this = *this * v; }
 *     void operator*=(SkScalar s) { *this = *this * s; }
 *     void operator/=(SkScalar s) { *this = *this / s; }
 *
 *     SkScalar lengthSquared() const { return Dot(*this, *this); }
 *     SkScalar length() const { return SkScalarSqrt(this->lengthSquared()); }
 *
 *     SkScalar   dot(SkV2 v) const { return Dot(*this, v); }
 *     SkScalar cross(SkV2 v) const { return Cross(*this, v); }
 *     SkV2 normalize()       const { return Normalize(*this); }
 *
 *     const float* ptr() const { return &x; }
 *     float* ptr() { return &x; }
 * }
 * ```
 */
public data class SkV2 public constructor(
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
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(SkV2 v) const { return x == v.x && y == v.y; }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(SkV2 v) const { return !(*this == v); }
   * ```
   */
  public operator fun unaryMinus(): SkV2 {
    TODO("Implement unaryMinus")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV2 operator-() const { return {-x, -y}; }
   * ```
   */
  public operator fun plus(v: SkV2): SkV2 {
    TODO("Implement plus")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV2 operator+(SkV2 v) const { return {x+v.x, y+v.y}; }
   * ```
   */
  public operator fun minus(v: SkV2): SkV2 {
    TODO("Implement minus")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV2 operator-(SkV2 v) const { return {x-v.x, y-v.y}; }
   * ```
   */
  public operator fun times(v: SkV2): SkV2 {
    TODO("Implement times")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV2 operator*(SkV2 v) const { return {x*v.x, y*v.y}; }
   * ```
   */
  public operator fun plusAssign(v: SkV2) {
    TODO("Implement plusAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator+=(SkV2 v) { *this = *this + v; }
   * ```
   */
  public operator fun minusAssign(v: SkV2) {
    TODO("Implement minusAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator-=(SkV2 v) { *this = *this - v; }
   * ```
   */
  public operator fun timesAssign(v: SkV2) {
    TODO("Implement timesAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator*=(SkV2 v) { *this = *this * v; }
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
  public operator fun divAssign(s: SkScalar) {
    TODO("Implement divAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator/=(SkScalar s) { *this = *this / s; }
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
   * SkScalar length() const { return SkScalarSqrt(this->lengthSquared()); }
   * ```
   */
  public fun dot(v: SkV2): Int {
    TODO("Implement dot")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar   dot(SkV2 v) const { return Dot(*this, v); }
   * ```
   */
  public fun cross(v: SkV2): Int {
    TODO("Implement cross")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar cross(SkV2 v) const { return Cross(*this, v); }
   * ```
   */
  public fun normalize(): SkV2 {
    TODO("Implement normalize")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV2 normalize()       const { return Normalize(*this); }
   * ```
   */
  public fun ptr(): Float {
    TODO("Implement ptr")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkScalar   Dot(SkV2 a, SkV2 b) { return a.x * b.x + a.y * b.y; }
     * ```
     */
    public fun dot(a: SkV2, b: SkV2): Int {
      TODO("Implement dot")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkScalar Cross(SkV2 a, SkV2 b) { return a.x * b.y - a.y * b.x; }
     * ```
     */
    public fun cross(a: SkV2, b: SkV2): Int {
      TODO("Implement cross")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkV2 Normalize(SkV2 v) { return v * (1.0f / v.length()); }
     * ```
     */
    public fun normalize(v: SkV2): SkV2 {
      TODO("Implement normalize")
    }
  }
}
