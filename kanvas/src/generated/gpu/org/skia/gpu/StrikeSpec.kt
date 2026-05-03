package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkTypeface

/**
 * C++ original:
 * ```cpp
 * struct StrikeSpec {
 *     StrikeSpec() = default;
 *     StrikeSpec(SkTypefaceID typefaceID, SkDiscardableHandleId discardableHandleId)
 *             : fTypefaceID{typefaceID}, fDiscardableHandleId(discardableHandleId) {}
 *     SkTypefaceID fTypefaceID = 0u;
 *     SkDiscardableHandleId fDiscardableHandleId = 0u;
 * }
 * ```
 */
public data class StrikeSpec public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkTypefaceID fTypefaceID
   * ```
   */
  public var fTypefaceID: SkTypeface,
  /**
   * C++ original:
   * ```cpp
   * SkDiscardableHandleId fDiscardableHandleId
   * ```
   */
  public var fDiscardableHandleId: Int,
)
