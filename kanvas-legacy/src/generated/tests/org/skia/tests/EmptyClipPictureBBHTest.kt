package org.skia.tests

import org.skia.core.SkCanvas

/**
 * C++ original:
 * ```cpp
 * class EmptyClipPictureBBHTest : public PictureBBHTestBase {
 * public:
 *     EmptyClipPictureBBHTest()
 *         : PictureBBHTestBase(2, 2, 3, 3) {}
 *
 *     void doTest(SkCanvas& playbackCanvas, SkCanvas& recordingCanvas) override {
 *         // intersect with out of bounds rect -> empty clip.
 *         playbackCanvas.clipRect(SkRect::MakeXYWH(10, 10, 1, 1));
 *         SkPaint paint;
 *         recordingCanvas.drawRect(SkRect::MakeWH(3, 3), paint);
 *     }
 *
 *     ~EmptyClipPictureBBHTest() override {}
 * }
 * ```
 */
public open class EmptyClipPictureBBHTest public constructor() : PictureBBHTestBase(TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void doTest(SkCanvas& playbackCanvas, SkCanvas& recordingCanvas) override {
   *         // intersect with out of bounds rect -> empty clip.
   *         playbackCanvas.clipRect(SkRect::MakeXYWH(10, 10, 1, 1));
   *         SkPaint paint;
   *         recordingCanvas.drawRect(SkRect::MakeWH(3, 3), paint);
   *     }
   * ```
   */
  public override fun doTest(playbackCanvas: SkCanvas, recordingCanvas: SkCanvas) {
    TODO("Implement doTest")
  }
}
