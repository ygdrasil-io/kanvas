package org.skia.tests

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkImageInfo
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class BitmapBackedCanvasStrategy : public RecordingStrategy {
 *     // This version just draws into a bitmap-backed canvas.
 *  public:
 *     BitmapBackedCanvasStrategy(const SkImageInfo& imageInfo) {
 *         fBitmap.allocPixels(imageInfo);
 *     }
 *
 *     const SkBitmap& recordAndReplay(const Drawer& drawer, const SkRect& intoClip,
 *                                     SkBlendMode mode) override {
 *         SkCanvas canvas(fBitmap);
 *         canvas.clear(0xffffffff);
 *         // Note that the scene is drawn just into the clipped region!
 *         canvas.clipRect(intoClip);
 *         drawer.draw(&canvas, intoClip, mode); // Shouild be canvas-wide...
 *         return fBitmap;
 *     }
 *
 *  private:
 *     SkBitmap fBitmap;
 * }
 * ```
 */
public open class BitmapBackedCanvasStrategy public constructor(
  imageInfo: SkImageInfo,
) : RecordingStrategy() {
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fBitmap
   * ```
   */
  private var fBitmap: SkBitmap = TODO("Initialize fBitmap")

  /**
   * C++ original:
   * ```cpp
   * const SkBitmap& recordAndReplay(const Drawer& drawer, const SkRect& intoClip,
   *                                     SkBlendMode mode) override {
   *         SkCanvas canvas(fBitmap);
   *         canvas.clear(0xffffffff);
   *         // Note that the scene is drawn just into the clipped region!
   *         canvas.clipRect(intoClip);
   *         drawer.draw(&canvas, intoClip, mode); // Shouild be canvas-wide...
   *         return fBitmap;
   *     }
   * ```
   */
  public override fun recordAndReplay(
    drawer: Drawer,
    intoClip: SkRect,
    mode: SkBlendMode,
  ): SkBitmap {
    TODO("Implement recordAndReplay")
  }
}
