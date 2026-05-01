package org.skia.math

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

public data class SkSize public constructor(
  public var fWidth: Int,
  public var fHeight: Int,
) {
  public fun `set`(w: SkScalar, h: SkScalar) {
    TODO("Implement set")
  }

  public fun isZero(): Boolean {
    return fWidth == 0 && fHeight == 0
  }

  public fun isEmpty(): Boolean {
    return fWidth <= 0 || fHeight <= 0
  }

  public fun setEmpty() {
    this.fWidth = 0
    this.fHeight = 0
  }

  public fun width(): Int {
    return fWidth
  }

  public fun height(): Int {
    return fHeight
  }

  public override fun equals(other: Any?): Boolean {
    return other is SkSize && fWidth == other.fWidth && fHeight == other.fHeight
  }

  public fun toRound(): SkISize {
    TODO("Implement toRound")
  }

  public fun toCeil(): SkISize {
    TODO("Implement toCeil")
  }

  public fun toFloor(): SkISize {
    TODO("Implement toFloor")
  }

  public companion object {
    public fun make(w: SkScalar, h: SkScalar): SkSize {
      TODO("Implement make")
    }

    public fun make(src: SkISize): SkSize {
      TODO("Implement make")
    }

    public fun makeEmpty(): SkSize {
      TODO("Implement makeEmpty")
    }
  }
}
