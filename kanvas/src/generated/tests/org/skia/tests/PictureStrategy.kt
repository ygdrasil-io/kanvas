package org.skia.tests

import kotlin.Int
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkImageInfo
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class PictureStrategy : public RecordingStrategy {
 *     // This version draws the entire scene into an SkPictureRecorder.
 *     // Then it then replays the scene through a clip rectangle.
 *     // This backend proved to be buggy.
 *  public:
 *     PictureStrategy(const SkImageInfo& imageInfo) {
 *         fBitmap.allocPixels(imageInfo);
 *         fWidth  = imageInfo.width();
 *         fHeight = imageInfo.height();
 *     }
 *
 *     const SkBitmap& recordAndReplay(const Drawer& drawer, const SkRect& intoClip,
 *                                     SkBlendMode mode) override {
 *         SkRTreeFactory factory;
 *         SkPictureRecorder recorder;
 *         SkRect canvasRect(SkRect::MakeWH(SkIntToScalar(fWidth),SkIntToScalar(fHeight)));
 *         SkCanvas* canvas = recorder.beginRecording(SkIntToScalar(fWidth),
 *                                                    SkIntToScalar(fHeight),
 *                                                    &factory);
 *         drawer.draw(canvas, canvasRect, mode);
 *         sk_sp<SkPicture> picture(recorder.finishRecordingAsPicture());
 *
 *         SkCanvas replayCanvas(fBitmap);
 *         replayCanvas.clear(0xffffffff);
 *         replayCanvas.clipRect(intoClip);
 *         picture->playback(&replayCanvas);
 *         return fBitmap;
 *     }
 *
 *  private:
 *     SkBitmap fBitmap;
 *     int fWidth;
 *     int fHeight;
 * }
 * ```
 */
public open class PictureStrategy public constructor(
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
   * int fWidth
   * ```
   */
  private var fWidth: Int = TODO("Initialize fWidth")

  /**
   * C++ original:
   * ```cpp
   * int fHeight
   * ```
   */
  private var fHeight: Int = TODO("Initialize fHeight")

  /**
   * C++ original:
   * ```cpp
   * const SkBitmap& recordAndReplay(const Drawer& drawer, const SkRect& intoClip,
   *                                     SkBlendMode mode) override {
   *         SkRTreeFactory factory;
   *         SkPictureRecorder recorder;
   *         SkRect canvasRect(SkRect::MakeWH(SkIntToScalar(fWidth),SkIntToScalar(fHeight)));
   *         SkCanvas* canvas = recorder.beginRecording(SkIntToScalar(fWidth),
   *                                                    SkIntToScalar(fHeight),
   *                                                    &factory);
   *         drawer.draw(canvas, canvasRect, mode);
   *         sk_sp<SkPicture> picture(recorder.finishRecordingAsPicture());
   *
   *         SkCanvas replayCanvas(fBitmap);
   *         replayCanvas.clear(0xffffffff);
   *         replayCanvas.clipRect(intoClip);
   *         picture->playback(&replayCanvas);
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
