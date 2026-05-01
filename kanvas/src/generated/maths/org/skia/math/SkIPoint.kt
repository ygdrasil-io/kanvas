package org.skia.math

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

public typealias SkIVector = SkIPoint

public open class SkIPoint public constructor(
  public var fX: Int,
  public var fY: Int,
) {
  public fun x(): Int {
    TODO("Implement x")
  }

  public fun y(): Int {
    return fY
  }

  public fun isZero(): Boolean {
    return (fX or fY) == 0
  }

  public fun `set`(x: Int, y: Int) {
    this.fX = x
    this.fY = y
  }

  public operator fun unaryMinus(): SkIPoint {
    return SkIPoint.Companion.make(-x(), -y())
  }

  public operator fun plusAssign(v: SkIVector) {
    TODO("Implement plusAssign")
  }

  public operator fun minusAssign(v: SkIVector) {
    TODO("Implement minusAssign")
  }

  public override fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  public companion object {
    public fun make(x: Int, y: Int): SkIPoint {
      TODO("Implement make")
    }
  }
}
