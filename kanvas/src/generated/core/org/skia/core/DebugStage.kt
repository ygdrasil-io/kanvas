package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct DebugStage {
 *     std::vector<SkBitmap> panels;
 *     // Should be only ops that start with debug_
 *     std::vector<SkRasterPipelineOp> ops;
 * }
 * ```
 */
public data class DebugStage public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::vector<SkBitmap> panels
   * ```
   */
  public var panels: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<SkRasterPipelineOp> ops
   * ```
   */
  public var ops: Int,
)
