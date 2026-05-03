package org.skia.core

import org.skia.math.SkRect
import org.skia.memory.SkArenaAlloc
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * struct SkStageRec {
 *     SkRasterPipeline*       fPipeline;
 *     SkArenaAlloc*           fAlloc;
 *     SkColorType             fDstColorType;
 *     SkColorSpace*           fDstCS;         // may be nullptr
 *     SkColor4f               fPaintColor;
 *     const SkSurfaceProps&   fSurfaceProps;
 *     // The device-space bounding box of the geometry being drawn.
 *     // An empty value can be used when it is expensive to compute,
 *     // in which case a heuristic will be used if necessary.
 *     SkRect fDstBounds;
 * }
 * ```
 */
public data class SkStageRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRasterPipeline*       fPipeline
   * ```
   */
  public var fPipeline: SkRasterPipeline?,
  /**
   * C++ original:
   * ```cpp
   * SkArenaAlloc*           fAlloc
   * ```
   */
  public var fAlloc: SkArenaAlloc?,
  /**
   * C++ original:
   * ```cpp
   * SkColorType             fDstColorType
   * ```
   */
  public var fDstColorType: SkColorType,
  /**
   * C++ original:
   * ```cpp
   * SkColorSpace*           fDstCS
   * ```
   */
  public var fDstCS: SkColorSpace?,
  /**
   * C++ original:
   * ```cpp
   * SkColor4f               fPaintColor
   * ```
   */
  public var fPaintColor: SkColor4f,
  /**
   * C++ original:
   * ```cpp
   * const SkSurfaceProps&   fSurfaceProps
   * ```
   */
  public val fSurfaceProps: SkSurfaceProps,
  /**
   * C++ original:
   * ```cpp
   * SkRect fDstBounds
   * ```
   */
  public var fDstBounds: SkRect,
)
