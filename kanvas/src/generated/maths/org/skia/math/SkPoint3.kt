package org.skia.math

import kotlin.Boolean
import kotlin.Int

public open class SkPoint3 public constructor(
  public var fX: Int,
  public var fY: Int,
  public var fZ: Int,
) {
  public fun x(): Int {
    return fX
  }

  public fun y(): Int {
    TODO("Implement y")
  }

  public fun z(): Int {
    return fZ
  }

  public fun `set`(
    x: SkScalar,
    y: SkScalar,
    z: SkScalar,
  ) {
    TODO("Implement set")
  }

  public fun length(): Int {
    TODO("Implement length")
  }

  public fun normalize(): Boolean {
    TODO("Implement normalize")
  }

  public fun makeScale(scale: SkScalar): SkPoint3 {
    return Companion.make(scale * fX, scale * fY, scale * fZ)
  }

  public fun scale(`value`: SkScalar) {
    TODO("Implement scale")
  }

  public operator fun unaryMinus(): SkPoint3 {
    TODO("Implement unaryMinus")
  }

  public operator fun plusAssign(v: SkPoint3) {
    TODO("Implement plusAssign")
  }

  public operator fun minusAssign(v: SkPoint3) {
    TODO("Implement minusAssign")
  }

  public fun isFinite(): Boolean {
    TODO("Implement isFinite")
  }

  public fun dot(vec: SkPoint3): Int {
    TODO("Implement dot")
  }

  public fun cross(vec: SkPoint3): SkPoint3 {
    TODO("Implement cross")
  }

  public companion object {
    public fun make(
      x: SkScalar,
      y: SkScalar,
      z: SkScalar,
    ): SkPoint3 {
      TODO("Implement make")
    }

    public fun length(
      x: SkScalar,
      y: SkScalar,
      z: SkScalar,
    ): Int {
      TODO("Implement length")
    }

    public fun dotProduct(a: SkPoint3, b: SkPoint3): Int {
      TODO("Implement dotProduct")
    }

    public fun crossProduct(a: SkPoint3, b: SkPoint3): SkPoint3 {
      TODO("Implement crossProduct")
    }
  }
}

public typealias SkVector3 = SkPoint3

public typealias SkColor3f = SkPoint3
