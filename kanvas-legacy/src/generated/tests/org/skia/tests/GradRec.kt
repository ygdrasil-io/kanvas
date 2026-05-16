package org.skia.tests

import kotlin.Float
import kotlin.Int
import org.skia.core.SkShaderBase
import org.skia.effects.SkGradient
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.foundation.SkTileMode
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * struct GradRec {
 *     int fColorCount;
 *     const SkColor4f* fColors;
 *     const float* fPos;
 *     const SkPoint* fPoint;   // 2
 *     const float* fRadius; // 2
 *     SkTileMode fTileMode;
 *
 *     SkGradient grad() const {
 *         SkSpan<const float> pos;
 *         if (fPos) {
 *             pos = {fPos, (size_t)fColorCount};
 *         }
 *         return {{{fColors, (size_t)fColorCount}, pos, fTileMode}, {}};
 *     }
 *
 *     void gradCheck(skiatest::Reporter* reporter,
 *                    const sk_sp<SkShader>& shader,
 *                    SkShaderBase::GradientInfo* info,
 *                    SkShaderBase::GradientType gt,
 *                    const SkMatrix& localMatrix = SkMatrix::I()) const {
 *         AutoTMalloc<SkColor4f> colorStorage(fColorCount);
 *         AutoTMalloc<SkScalar> posStorage(fColorCount);
 *
 *         info->fColorCount = fColorCount;
 *         info->fColors = colorStorage;
 *         info->fColorOffsets = posStorage.get();
 *         SkMatrix shaderLocalMatrix;
 *         REPORTER_ASSERT(reporter, as_SB(shader)->asGradient(info, &shaderLocalMatrix) == gt);
 *         REPORTER_ASSERT(reporter, shaderLocalMatrix == localMatrix);
 *
 *         REPORTER_ASSERT(reporter, info->fColorCount == fColorCount);
 *         REPORTER_ASSERT(reporter,
 *                         !memcmp(info->fColors, fColors, fColorCount * sizeof(SkColor4f)));
 *         REPORTER_ASSERT(reporter,
 *                         !memcmp(info->fColorOffsets, fPos, fColorCount * sizeof(SkScalar)));
 *         REPORTER_ASSERT(reporter, fTileMode == (SkTileMode)info->fTileMode);
 *     }
 * }
 * ```
 */
public data class GradRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fColorCount
   * ```
   */
  public var fColorCount: Int,
  /**
   * C++ original:
   * ```cpp
   * const SkColor4f* fColors
   * ```
   */
  public val fColors: SkColor4f?,
  /**
   * C++ original:
   * ```cpp
   * const float* fPos
   * ```
   */
  public val fPos: Float?,
  /**
   * C++ original:
   * ```cpp
   * const SkPoint* fPoint
   * ```
   */
  public val fPoint: SkPoint?,
  /**
   * C++ original:
   * ```cpp
   * const float* fRadius
   * ```
   */
  public val fRadius: Float?,
  /**
   * C++ original:
   * ```cpp
   * SkTileMode fTileMode
   * ```
   */
  public var fTileMode: SkTileMode,
) {
  /**
   * C++ original:
   * ```cpp
   * SkGradient grad() const {
   *         SkSpan<const float> pos;
   *         if (fPos) {
   *             pos = {fPos, (size_t)fColorCount};
   *         }
   *         return {{{fColors, (size_t)fColorCount}, pos, fTileMode}, {}};
   *     }
   * ```
   */
  public fun grad(): SkGradient {
    TODO("Implement grad")
  }

  /**
   * C++ original:
   * ```cpp
   * void gradCheck(skiatest::Reporter* reporter,
   *                    const sk_sp<SkShader>& shader,
   *                    SkShaderBase::GradientInfo* info,
   *                    SkShaderBase::GradientType gt,
   *                    const SkMatrix& localMatrix = SkMatrix::I()) const {
   *         AutoTMalloc<SkColor4f> colorStorage(fColorCount);
   *         AutoTMalloc<SkScalar> posStorage(fColorCount);
   *
   *         info->fColorCount = fColorCount;
   *         info->fColors = colorStorage;
   *         info->fColorOffsets = posStorage.get();
   *         SkMatrix shaderLocalMatrix;
   *         REPORTER_ASSERT(reporter, as_SB(shader)->asGradient(info, &shaderLocalMatrix) == gt);
   *         REPORTER_ASSERT(reporter, shaderLocalMatrix == localMatrix);
   *
   *         REPORTER_ASSERT(reporter, info->fColorCount == fColorCount);
   *         REPORTER_ASSERT(reporter,
   *                         !memcmp(info->fColors, fColors, fColorCount * sizeof(SkColor4f)));
   *         REPORTER_ASSERT(reporter,
   *                         !memcmp(info->fColorOffsets, fPos, fColorCount * sizeof(SkScalar)));
   *         REPORTER_ASSERT(reporter, fTileMode == (SkTileMode)info->fTileMode);
   *     }
   * ```
   */
  public fun gradCheck(
    reporter: Reporter?,
    shader: SkSp<SkShader>,
    info: SkShaderBase.GradientInfo?,
    gt: SkShaderBase.GradientType,
    localMatrix: SkMatrix = TODO(),
  ) {
    TODO("Implement gradCheck")
  }
}
