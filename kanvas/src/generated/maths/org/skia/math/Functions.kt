package org.skia.math

import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.skia.foundation.U16CPU
import org.skia.foundation.U8CPU

/**
 * C++ original:
 * ```cpp
 * static inline SkScalar SkScalarFraction(SkScalar x) {
 *     return x - SkScalarTruncToScalar(x);
 * }
 * ```
 */
public fun skScalarFraction(x: SkScalar): SkScalar {
  TODO("Implement skScalarFraction")
}

/**
 * C++ original:
 * ```cpp
 * static inline SkScalar SkScalarSquare(SkScalar x) { return x * x; }
 * ```
 */
public fun skScalarSquare(x: SkScalar): SkScalar {
  TODO("Implement skScalarSquare")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool SkScalarIsInt(SkScalar x) {
 *     return x == SkScalarFloorToScalar(x);
 * }
 * ```
 */
public fun skScalarIsInt(x: SkScalar): Boolean {
  TODO("Implement skScalarIsInt")
}

/**
 * C++ original:
 * ```cpp
 * static inline int SkScalarSignAsInt(SkScalar x) {
 *     return x < 0 ? -1 : (x > 0);
 * }
 * ```
 */
public fun skScalarSignAsInt(x: SkScalar): Int {
  TODO("Implement skScalarSignAsInt")
}

/**
 * C++ original:
 * ```cpp
 * static inline SkScalar SkScalarSignAsScalar(SkScalar x) {
 *     return x < 0 ? -SK_Scalar1 : ((x > 0) ? SK_Scalar1 : 0);
 * }
 * ```
 */
public fun skScalarSignAsScalar(x: SkScalar): SkScalar {
  TODO("Implement skScalarSignAsScalar")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool SkScalarNearlyZero(SkScalar x,
 *                                       SkScalar tolerance = SK_ScalarNearlyZero) {
 *     SkASSERT(tolerance >= 0);
 *     return SkScalarAbs(x) <= tolerance;
 * }
 * ```
 */
public fun skScalarNearlyZero(x: SkScalar, tolerance: SkScalar = TODO()): Boolean {
  TODO("Implement skScalarNearlyZero")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool SkScalarNearlyEqual(SkScalar x, SkScalar y,
 *                                        SkScalar tolerance = SK_ScalarNearlyZero) {
 *     SkASSERT(tolerance >= 0);
 *     return SkScalarAbs(x-y) <= tolerance;
 * }
 * ```
 */
public fun skScalarNearlyEqual(
  x: SkScalar,
  y: SkScalar,
  tolerance: SkScalar = TODO(),
): Boolean {
  TODO("Implement skScalarNearlyEqual")
}

/**
 * C++ original:
 * ```cpp
 * static inline float SkScalarSinSnapToZero(SkScalar radians) {
 *     float v = SkScalarSin(radians);
 *     return SkScalarNearlyZero(v, SK_ScalarSinCosNearlyZero) ? 0.0f : v;
 * }
 * ```
 */
public fun skScalarSinSnapToZero(radians: SkScalar): Float {
  TODO("Implement skScalarSinSnapToZero")
}

/**
 * C++ original:
 * ```cpp
 * static inline float SkScalarCosSnapToZero(SkScalar radians) {
 *     float v = SkScalarCos(radians);
 *     return SkScalarNearlyZero(v, SK_ScalarSinCosNearlyZero) ? 0.0f : v;
 * }
 * ```
 */
public fun skScalarCosSnapToZero(radians: SkScalar): Float {
  TODO("Implement skScalarCosSnapToZero")
}

/**
 * C++ original:
 * ```cpp
 * static inline SkScalar SkScalarInterp(SkScalar A, SkScalar B, SkScalar t) {
 *     SkASSERT(t >= 0 && t <= SK_Scalar1);
 *     return A + (B - A) * t;
 * }
 * ```
 */
public fun skScalarInterp(
  a: SkScalar,
  b: SkScalar,
  t: SkScalar,
): SkScalar {
  TODO("Implement skScalarInterp")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool SkScalarsEqual(const SkScalar a[], const SkScalar b[], int n) {
 *     SkASSERT(n >= 0);
 *     for (int i = 0; i < n; ++i) {
 *         if (a[i] != b[i]) {
 *             return false;
 *         }
 *     }
 *     return true;
 * }
 * ```
 */
public fun skScalarsEqual(
  a: Array<SkScalar>,
  b: Array<SkScalar>,
  n: Int,
): Boolean {
  TODO("Implement skScalarsEqual")
}

/**
 * C++ original:
 * ```cpp
 * static inline SkFixed SkFixedRoundToFixed(SkFixed x) {
 *     return (SkFixed)( (uint32_t)(x + SK_FixedHalf) & 0xFFFF0000 );
 * }
 * ```
 */
public fun skFixedRoundToFixed(x: SkFixed): SkFixed {
  TODO("Implement skFixedRoundToFixed")
}

/**
 * C++ original:
 * ```cpp
 * static inline SkFixed SkFixedCeilToFixed(SkFixed x) {
 *     return (SkFixed)( (uint32_t)(x + SK_Fixed1 - 1) & 0xFFFF0000 );
 * }
 * ```
 */
public fun skFixedCeilToFixed(x: SkFixed): SkFixed {
  TODO("Implement skFixedCeilToFixed")
}

/**
 * C++ original:
 * ```cpp
 * static inline SkFixed SkFixedFloorToFixed(SkFixed x) {
 *     return (SkFixed)( (uint32_t)x & 0xFFFF0000 );
 * }
 * ```
 */
public fun skFixedFloorToFixed(x: SkFixed): SkFixed {
  TODO("Implement skFixedFloorToFixed")
}

/**
 * C++ original:
 * ```cpp
 * static inline SkFixed SkFixedMul(SkFixed a, SkFixed b) {
 *     return (SkFixed)((int64_t)a * b >> 16);
 * }
 * ```
 */
public fun skFixedMul(a: SkFixed, b: SkFixed): SkFixed {
  TODO("Implement skFixedMul")
}

/**
 * C++ original:
 * ```cpp
 * static inline int64_t sk_64_mul(int64_t a, int64_t b) {
 *     return a * b;
 * }
 * ```
 */
public fun sk64Mul(a: Long, b: Long): Long {
  TODO("Implement sk64Mul")
}

/**
 * C++ original:
 * ```cpp
 * static inline constexpr int64_t SkLeftShift(int64_t value, int32_t shift) {
 *     return (int64_t) ((uint64_t) value << shift);
 * }
 * ```
 */
public fun skLeftShift(`value`: Long, shift: Int): Long {
  TODO("Implement skLeftShift")
}

/**
 * C++ original:
 * ```cpp
 * static inline unsigned SkMul16ShiftRound(U16CPU a, U16CPU b, int shift) {
 *     SkASSERT(a <= 32767);
 *     SkASSERT(b <= 32767);
 *     SkASSERT(shift > 0 && shift <= 8);
 *     unsigned prod = a*b + (1 << (shift - 1));
 *     return (prod + (prod >> shift)) >> shift;
 * }
 * ```
 */
public fun skMul16ShiftRound(
  a: U16CPU,
  b: U16CPU,
  shift: Int,
): UInt {
  TODO("Implement skMul16ShiftRound")
}

/**
 * C++ original:
 * ```cpp
 * static inline U8CPU SkMulDiv255Round(U16CPU a, U16CPU b) {
 *     return SkMul16ShiftRound(a, b, 8);
 * }
 * ```
 */
public fun skMulDiv255Round(a: U16CPU, b: U16CPU): U8CPU {
  TODO("Implement skMulDiv255Round")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool SkPathFillType_IsEvenOdd(SkPathFillType ft) {
 *     return (static_cast<int>(ft) & 1) != 0;
 * }
 * ```
 */
public fun skPathFillTypeIsEvenOdd(ft: SkPathFillType): Boolean {
  TODO("Implement skPathFillTypeIsEvenOdd")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool SkPathFillType_IsInverse(SkPathFillType ft) {
 *     return (static_cast<int>(ft) & 2) != 0;
 * }
 * ```
 */
public fun skPathFillTypeIsInverse(ft: SkPathFillType): Boolean {
  TODO("Implement skPathFillTypeIsInverse")
}

/**
 * C++ original:
 * ```cpp
 * static inline SkPathFillType SkPathFillType_ToggleInverse(SkPathFillType ft) {
 *     return static_cast<SkPathFillType>(static_cast<int>(ft) ^ 2);
 * }
 * ```
 */
public fun skPathFillTypeToggleInverse(ft: SkPathFillType): SkPathFillType {
  TODO("Implement skPathFillTypeToggleInverse")
}

/**
 * C++ original:
 * ```cpp
 * static inline SkPathFillType SkPathFillType_ConvertToNonInverse(SkPathFillType ft) {
 *     return static_cast<SkPathFillType>(static_cast<int>(ft) & 1);
 * }
 * ```
 */
public fun skPathFillTypeConvertToNonInverse(ft: SkPathFillType): SkPathFillType {
  TODO("Implement skPathFillTypeConvertToNonInverse")
}
