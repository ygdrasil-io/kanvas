package org.skia.math

import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.skia.foundation.U16CPU
import org.skia.foundation.U8CPU

public fun skScalarFraction(x: SkScalar): SkScalar {
  TODO("Implement skScalarFraction")
}

public fun skScalarSquare(x: SkScalar): SkScalar {
  TODO("Implement skScalarSquare")
}

public fun skScalarIsInt(x: SkScalar): Boolean {
  TODO("Implement skScalarIsInt")
}

public fun skScalarSignAsInt(x: SkScalar): Int {
  TODO("Implement skScalarSignAsInt")
}

public fun skScalarSignAsScalar(x: SkScalar): SkScalar {
  TODO("Implement skScalarSignAsScalar")
}

public fun skScalarNearlyZero(x: SkScalar, tolerance: SkScalar = TODO()): Boolean {
  TODO("Implement skScalarNearlyZero")
}

public fun skScalarNearlyEqual(
  x: SkScalar,
  y: SkScalar,
  tolerance: SkScalar = TODO(),
): Boolean {
  TODO("Implement skScalarNearlyEqual")
}

public fun skScalarSinSnapToZero(radians: SkScalar): Float {
  TODO("Implement skScalarSinSnapToZero")
}

public fun skScalarCosSnapToZero(radians: SkScalar): Float {
  TODO("Implement skScalarCosSnapToZero")
}

public fun skScalarInterp(
  a: SkScalar,
  b: SkScalar,
  t: SkScalar,
): SkScalar {
  TODO("Implement skScalarInterp")
}

public fun skScalarsEqual(
  a: Array<SkScalar>,
  b: Array<SkScalar>,
  n: Int,
): Boolean {
  TODO("Implement skScalarsEqual")
}

public fun skFixedRoundToFixed(x: SkFixed): SkFixed {
  TODO("Implement skFixedRoundToFixed")
}

public fun skFixedCeilToFixed(x: SkFixed): SkFixed {
  TODO("Implement skFixedCeilToFixed")
}

public fun skFixedFloorToFixed(x: SkFixed): SkFixed {
  TODO("Implement skFixedFloorToFixed")
}

public fun skFixedMul(a: SkFixed, b: SkFixed): SkFixed {
  TODO("Implement skFixedMul")
}

public fun sk64Mul(a: Long, b: Long): Long {
  TODO("Implement sk64Mul")
}

public fun skLeftShift(`value`: Long, shift: Int): Long {
  TODO("Implement skLeftShift")
}

public fun skMul16ShiftRound(
  a: U16CPU,
  b: U16CPU,
  shift: Int,
): UInt {
  TODO("Implement skMul16ShiftRound")
}

public fun skMulDiv255Round(a: U16CPU, b: U16CPU): U8CPU {
  TODO("Implement skMulDiv255Round")
}

public fun skPathFillTypeIsEvenOdd(ft: SkPathFillType): Boolean {
  TODO("Implement skPathFillTypeIsEvenOdd")
}

public fun skPathFillTypeIsInverse(ft: SkPathFillType): Boolean {
  TODO("Implement skPathFillTypeIsInverse")
}

public fun skPathFillTypeToggleInverse(ft: SkPathFillType): SkPathFillType {
  TODO("Implement skPathFillTypeToggleInverse")
}

public fun skPathFillTypeConvertToNonInverse(ft: SkPathFillType): SkPathFillType {
  TODO("Implement skPathFillTypeConvertToNonInverse")
}
