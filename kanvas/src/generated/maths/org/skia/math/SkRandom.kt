package org.skia.math

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.UInt

public data class SkRandom public constructor(
  private var fK: Int,
  private var fJ: Int,
) {
  public fun assign(rand: SkRandom) {
    fK = rand.fK
    fJ = rand.fJ
  }

  public fun nextU(): Int {
    return (((fK shl 16) or (fK ushr 16)) + fJ).toInt()
  }

  public fun nextS(): Int {
    return this.nextU()
  }

  public fun nextF(): Float {
    TODO("Implement nextF")
  }

  public fun nextRangeF(min: Float, max: Float): Float {
    return min + this.nextF() * (max - min)
  }

  public fun nextBits(bitCount: UInt): Int {
    return this.nextU() ushr (32 - bitCount.toInt())
  }

  public fun nextRangeU(min: UInt, max: UInt): Int {
    return nextRangeU(min, max).toInt()
  }

  public fun nextULessThan(count: UInt): Int {
    return nextRangeU(0u, count - 1u).toInt()
  }

  public fun nextUScalar1(): SkScalar {
    TODO("Implement nextUScalar1")
  }

  public fun nextRangeScalar(min: SkScalar, max: SkScalar): SkScalar {
    return nextUScalar1() * (max - min) + min
  }

  public fun nextSScalar1(): SkScalar {
    TODO("Implement nextSScalar1")
  }

  public fun nextBool(): Boolean {
    return this.nextU() >= 0x80000000
  }

  public fun nextBiasedBool(fractionTrue: SkScalar): Boolean {
    return nextUScalar1() <= fractionTrue
  }

  public fun setSeed(seed: UInt) {
    init(seed)
        Companion.nextLCG(seed)
  }

  private fun `init`(seed: UInt) {
    TODO("Implement init")
  }

  private fun nextUFixed1(): SkFixed {
    TODO("Implement nextUFixed1")
  }

  private fun nextSFixed1(): SkFixed {
    TODO("Implement nextSFixed1")
  }

  public companion object {
    public val kMul: Int = TODO("Initialize kMul")

    public val kAdd: Int = TODO("Initialize kAdd")

    public val kKMul: Int = TODO("Initialize kKMul")

    public val kJMul: Int = TODO("Initialize kJMul")

    private fun nextLCG(seed: UInt): Int {
      TODO("Implement nextLCG")
    }
  }
}
