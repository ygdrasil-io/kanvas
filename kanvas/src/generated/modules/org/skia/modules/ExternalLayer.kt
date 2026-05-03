package org.skia.modules

import kotlin.Double
import org.skia.core.SkCanvas
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class ExternalLayer : public SkRefCnt {
 * public:
 *     /** Render layer content into the given canvas.
 *      *
 *      * @param canvas  Destination canvas
 *      * @param t       Time in seconds, relative to the layer in-point (start time)
 *      */
 *     virtual void render(SkCanvas* canvas, double t) = 0;
 * }
 * ```
 */
public abstract class ExternalLayer : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * virtual void render(SkCanvas* canvas, double t) = 0
   * ```
   */
  public abstract fun render(canvas: SkCanvas?, t: Double)
}
