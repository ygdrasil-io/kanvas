package org.skia.utils

import kotlin.Int
import org.skia.core.SkCanvas

/**
 * C++ original:
 * ```cpp
 * class SkCanvasState {
 * public:
 *     SkCanvasState(int32_t version, SkCanvas* canvas) {
 *         SkASSERT(canvas);
 *         this->version = version;
 *         width = canvas->getBaseLayerSize().width();
 *         height = canvas->getBaseLayerSize().height();
 *
 *     }
 *
 *     /**
 *      * The version this struct was built with.  This field must always appear
 *      * first in the struct so that when the versions don't match (and the
 *      * remaining contents and size are potentially different) we can still
 *      * compare the version numbers.
 *      */
 *     int32_t version;
 *     int32_t width;
 *     int32_t height;
 *     int32_t alignmentPadding;
 * }
 * ```
 */
public open class SkCanvasState public constructor(
  version: Int,
  canvas: SkCanvas?,
) {
  /**
   * C++ original:
   * ```cpp
   * int32_t version
   * ```
   */
  public var version: Int = TODO("Initialize version")

  /**
   * C++ original:
   * ```cpp
   * int32_t width
   * ```
   */
  public var width: Int = TODO("Initialize width")

  /**
   * C++ original:
   * ```cpp
   * int32_t height
   * ```
   */
  public var height: Int = TODO("Initialize height")

  /**
   * C++ original:
   * ```cpp
   * int32_t alignmentPadding
   * ```
   */
  public var alignmentPadding: Int = TODO("Initialize alignmentPadding")
}

public typealias SkCanvasStateV1INHERITED = SkCanvasState
