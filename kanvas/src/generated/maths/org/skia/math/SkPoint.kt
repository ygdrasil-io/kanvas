package org.skia.math

import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int

public typealias SkVector = SkPoint

public open class SkPoint public constructor(
  public var fX: Float,
  public var fY: Float,
) {
  public fun x(): Float {
    return fX
  }

  public fun y(): Float {
    return fY
  }

  public fun isZero(): Boolean {
    return fX == 0f && fY == 0f
  }

  public fun `set`(x: Float, y: Float) {
    this.fX = x
    this.fY = y
  }

  public fun iset(x: Int, y: Int) {
    this.fX = x.toFloat()
    this.fY = y.toFloat()
  }

  public fun iset(p: SkIPoint) {
    this.fX = p.fX.toFloat()
    this.fY = p.fY.toFloat()
  }

  public fun setAbs(pt: SkPoint) {
    this.fX = kotlin.math.abs(pt.x())
    this.fY = kotlin.math.abs(pt.y())
  }

  public fun offset(dx: Float, dy: Float) {
    fX += dx
    fY += dy
  }

  public fun length(): Float {
    return Companion.length(fX, fY)
  }

  public fun distanceToOrigin(): Float {
    return this.length()
  }

  public fun normalize(): Boolean {
    val length = Companion.length(fX, fY)
            return if (length > 0) {
                val scale = 1.0f / length
                fX *= scale
                fY *= scale
                true
            } else {
                false
            }
  }

  public fun setNormalize(x: Float, y: Float): Boolean {
    val length = Companion.length(x, y)
    if (length == 0f) {
        set(0f, 0f)
        return false
    }
    val scale = 1f / length
    set(x * scale, y * scale)
    return true
  }

  public fun setLength(length: Float): Boolean {
    val oldLength = this.length()
    if (oldLength.isNaN() || oldLength <= 0) {
        this.set(0f, 0f)
        return false
    }
    val scale = length / oldLength
    this.fX *= scale
    this.fY *= scale
    return true
  }

  public fun setLength(
    x: Float,
    y: Float,
    length: Float,
  ): Boolean {
    return setLength(this.x(), this.y(), length)
  }

  public fun scale(scale: Float, dst: SkPoint?) {
    dst?.let { it.fX = fX * scale; it.fY = fY * scale }
  }

  public fun scale(`value`: Float) {
    this.scale(value, this)
  }

  public fun negate() {
    this.fX = -this.fX
    this.fY = -this.fY
  }

  public operator fun unaryMinus(): SkPoint {
    return Companion.make(-fX, -fY)
  }

  public operator fun plusAssign(v: SkVector) {
    this.fX += v.fX
    this.fY += v.fY
  }

  public operator fun minusAssign(v: SkVector) {
    this.fX -= v.x(); this.fY -= v.y()
  }

  public operator fun times(scale: Float): SkPoint {
    return SkPoint.make(this.x() * scale, this.y() * scale)
  }

  public operator fun timesAssign(scale: Float) {
    this.fX *= scale
    this.fY *= scale
  }

  public fun isFinite(): Boolean {
    return fX.isFinite() && fY.isFinite()
  }

  public override fun equals(other: Any?): Boolean {
    if (other !is org.skia.math.SkPoint) return false
    return this.fX == other.fX && this.fY == other.fY
  }

  public fun cross(vec: SkVector): Float {
    return Companion.crossProduct(this, vec)
  }

  public fun dot(vec: SkVector): Float {
    return Companion.dotProduct(this, vec)
  }

  public companion object {
    public fun make(x: Float, y: Float): SkPoint {
      return Companion.make(x, y)
    }

    public fun offset(
      points: Array<SkPoint>,
      count: Int,
      offset: SkVector,
    ) {
      Companion.offset(points, count, offset)
    }

    public fun offset(
      points: Array<SkPoint>,
      count: Int,
      dx: Float,
      dy: Float,
    ) {
      Companion.offset(points, count, dx, dy)
    }

    public fun length(x: Float, y: Float): Float {
      TODO("Implement length")
    }

    public fun normalize(vec: SkVector?): Float {
      return Companion.normalize(vec?.let { SkVector.make(it.fX, it.fY) })
    }

    public fun distance(a: SkPoint, b: SkPoint): Float {
      return Companion.length(a.x() - b.x(), a.y() - b.y())
    }

    public fun dotProduct(a: SkVector, b: SkVector): Float {
      return Companion.dotProduct(a, b)
    }

    public fun crossProduct(a: SkVector, b: SkVector): Float {
      return Companion.crossProduct(a, b)
    }
  }
}
