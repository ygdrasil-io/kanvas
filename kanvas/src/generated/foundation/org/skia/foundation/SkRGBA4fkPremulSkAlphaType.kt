package org.skia.foundation

import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.UByte
import kotlin.UInt

public data class SkRGBA4fkPremulSkAlphaType public constructor(
  private var fR: Float,
  private var fG: Float,
  private var fB: Float,
  private var fA: Float,
) {
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  private operator fun times(scale: Float): SkRGBA4fkPremulSkAlphaType {
    TODO("Implement times")
  }

  private operator fun times(scale: SkRGBA4f): SkRGBA4fkPremulSkAlphaType {
    TODO("Implement times")
  }

  private fun vec(): Float {
    TODO("Implement vec")
  }

  private fun array(): Array<Float> {
    TODO("Implement array")
  }

  private operator fun `get`(index: Int): Float {
    TODO("Implement get")
  }

  private fun isOpaque(): Boolean {
    TODO("Implement isOpaque")
  }

  private fun fitsInBytes(): Boolean {
    TODO("Implement fitsInBytes")
  }

  private fun toSkColor(): SkColor {
    TODO("Implement toSkColor")
  }

  private fun premul(): Int {
    TODO("Implement premul")
  }

  private fun unpremul(): Int {
    TODO("Implement unpremul")
  }

  private fun toBytesRGBA(): UInt {
    TODO("Implement toBytesRGBA")
  }

  private fun makeOpaque(): SkRGBA4fkPremulSkAlphaType {
    TODO("Implement makeOpaque")
  }

  private fun pinAlpha(): SkRGBA4fkPremulSkAlphaType {
    TODO("Implement pinAlpha")
  }

  private fun withAlpha(a: Float): SkRGBA4fkPremulSkAlphaType {
    TODO("Implement withAlpha")
  }

  private fun withAlphaByte(a: UByte): SkRGBA4fkPremulSkAlphaType {
    TODO("Implement withAlphaByte")
  }

  public companion object {
    private fun fromColor(color: SkColor): SkRGBA4fkPremulSkAlphaType {
      TODO("Implement fromColor")
    }

    private fun fromPMColor(param0: SkPMColor): SkRGBA4fkPremulSkAlphaType {
      TODO("Implement fromPMColor")
    }

    private fun fromBytesRGBA(color: UInt): SkRGBA4fkPremulSkAlphaType {
      TODO("Implement fromBytesRGBA")
    }
  }
}
