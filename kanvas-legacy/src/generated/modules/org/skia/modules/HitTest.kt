package org.skia.modules

import org.skia.foundation.SkSp
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct HitTest {
 *     const SkPoint           pt;
 *     sk_sp<sksg::RenderNode> node;
 * }
 * ```
 */
public data class HitTest public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkPoint           pt
   * ```
   */
  public val pt: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> node
   * ```
   */
  public var node: SkSp<RenderNode>,
)
