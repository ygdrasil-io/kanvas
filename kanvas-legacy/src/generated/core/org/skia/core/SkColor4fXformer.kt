package org.skia.core

import kotlin.Float
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SkColor4fXformer {
 *     SkColor4fXformer(const SkGradientBaseShader* shader,
 *                      SkColorSpace* dst,
 *                      bool forceExplicitPositions = false);
 *
 *     using ColorStorage = skia_private::STArray<4, SkPMColor4f>;
 *     using PositionStorage = skia_private::STArray<4, float>;
 *
 *     ColorStorage fColors;
 *     PositionStorage fPositionStorage;
 *     const float* fPositions;
 *     sk_sp<SkColorSpace> fIntermediateColorSpace;
 * }
 * ```
 */
public data class SkColor4fXformer public constructor(
  /**
   * C++ original:
   * ```cpp
   * ColorStorage fColors
   * ```
   */
  public var fColors: Int,
  /**
   * C++ original:
   * ```cpp
   * PositionStorage fPositionStorage
   * ```
   */
  public var fPositionStorage: Int,
  /**
   * C++ original:
   * ```cpp
   * const float* fPositions
   * ```
   */
  public val fPositions: Float?,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> fIntermediateColorSpace
   * ```
   */
  public var fIntermediateColorSpace: Int,
)
