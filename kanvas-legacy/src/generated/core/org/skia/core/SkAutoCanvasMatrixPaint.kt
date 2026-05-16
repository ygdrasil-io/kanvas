package org.skia.core

import kotlin.Int
import org.skia.foundation.SkNoncopyable
import org.skia.foundation.SkPaint
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class [[nodiscard]] SkAutoCanvasMatrixPaint : SkNoncopyable {
 * public:
 *     SkAutoCanvasMatrixPaint(SkCanvas*, const SkMatrix*, const SkPaint*, const SkRect& bounds);
 *     ~SkAutoCanvasMatrixPaint();
 *
 * private:
 *     SkCanvas*   fCanvas;
 *     int         fSaveCount;
 * }
 * ```
 */
public open class SkAutoCanvasMatrixPaint public constructor(
  canvas: SkCanvas?,
  matrix: SkMatrix?,
  paint: SkPaint?,
  bounds: SkRect,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * SkCanvas*   fCanvas
   * ```
   */
  private var fCanvas: SkCanvas? = TODO("Initialize fCanvas")

  /**
   * C++ original:
   * ```cpp
   * int         fSaveCount
   * ```
   */
  private var fSaveCount: Int = TODO("Initialize fSaveCount")
}
