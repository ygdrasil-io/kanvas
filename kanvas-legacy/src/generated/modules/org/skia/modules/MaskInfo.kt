package org.skia.modules

import kotlin.Boolean
import org.skia.foundation.SkBlendMode

/**
 * C++ original:
 * ```cpp
 * struct MaskInfo {
 *     SkBlendMode       fBlendMode;      // used when masking with layers/blending
 *     sksg::Merge::Mode fMergeMode;      // used when clipping
 *     bool              fInvertGeometry;
 * }
 * ```
 */
public data class MaskInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkBlendMode       fBlendMode
   * ```
   */
  public var fBlendMode: SkBlendMode,
  /**
   * C++ original:
   * ```cpp
   * sksg::Merge::Mode fMergeMode
   * ```
   */
  public var fMergeMode: Merge.Mode,
  /**
   * C++ original:
   * ```cpp
   * bool              fInvertGeometry
   * ```
   */
  public var fInvertGeometry: Boolean,
)
