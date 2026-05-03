package org.skia.tests

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class RecordingStrategy {
 *  public:
 *     virtual ~RecordingStrategy() {}
 *     virtual const SkBitmap& recordAndReplay(const Drawer& drawer,
 *                                             const SkRect& intoClip,
 *                                             SkBlendMode) = 0;
 * }
 * ```
 */
public abstract class RecordingStrategy {
  /**
   * C++ original:
   * ```cpp
   * virtual const SkBitmap& recordAndReplay(const Drawer& drawer,
   *                                             const SkRect& intoClip,
   *                                             SkBlendMode) = 0
   * ```
   */
  public abstract fun recordAndReplay(
    drawer: Drawer,
    intoClip: SkRect,
    param2: SkBlendMode,
  ): SkBitmap
}
