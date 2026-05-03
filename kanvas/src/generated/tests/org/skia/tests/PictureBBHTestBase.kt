package org.skia.tests

import kotlin.Int
import org.skia.core.SkBBHFactory
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap

/**
 * C++ original:
 * ```cpp
 * class PictureBBHTestBase {
 * public:
 *     PictureBBHTestBase(int playbackWidth, int playbackHeight,
 *         int recordWidth, int recordHeight) {
 *
 *         fResultBitmap.allocN32Pixels(playbackWidth, playbackHeight);
 *         fPictureWidth = recordWidth;
 *         fPictureHeight = recordHeight;
 *     }
 *
 *     virtual ~PictureBBHTestBase() { }
 *
 *     virtual void doTest(SkCanvas& playbackCanvas, SkCanvas& recordingCanvas) = 0;
 *
 *     void run(skiatest::Reporter* reporter) {
 *         // No BBH
 *         this->run(nullptr, reporter);
 *
 *         // With an R-Tree
 *         SkRTreeFactory RTreeFactory;
 *         this->run(&RTreeFactory, reporter);
 *     }
 *
 * private:
 *     void run(SkBBHFactory* factory, skiatest::Reporter* reporter) {
 *         SkCanvas playbackCanvas(fResultBitmap);
 *         playbackCanvas.clear(SK_ColorGREEN);
 *         SkPictureRecorder recorder;
 *         SkCanvas* recordCanvas = recorder.beginRecording(SkIntToScalar(fPictureWidth),
 *                                                          SkIntToScalar(fPictureHeight),
 *                                                          factory);
 *         this->doTest(playbackCanvas, *recordCanvas);
 *         sk_sp<SkPicture> picture(recorder.finishRecordingAsPicture());
 *         playbackCanvas.drawPicture(picture);
 *         REPORTER_ASSERT(reporter, SK_ColorGREEN == fResultBitmap.getColor(0, 0));
 *     }
 *
 *     SkBitmap fResultBitmap;
 *     int fPictureWidth, fPictureHeight;
 * }
 * ```
 */
public abstract class PictureBBHTestBase public constructor(
  playbackWidth: Int,
  playbackHeight: Int,
  recordWidth: Int,
  recordHeight: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fResultBitmap
   * ```
   */
  private var fResultBitmap: SkBitmap = TODO("Initialize fResultBitmap")

  /**
   * C++ original:
   * ```cpp
   * int fPictureWidth
   * ```
   */
  private var fPictureWidth: Int = TODO("Initialize fPictureWidth")

  /**
   * C++ original:
   * ```cpp
   * int fPictureWidth, fPictureHeight
   * ```
   */
  private var fPictureHeight: Int = TODO("Initialize fPictureHeight")

  /**
   * C++ original:
   * ```cpp
   * virtual void doTest(SkCanvas& playbackCanvas, SkCanvas& recordingCanvas) = 0
   * ```
   */
  public abstract fun doTest(playbackCanvas: SkCanvas, recordingCanvas: SkCanvas)

  /**
   * C++ original:
   * ```cpp
   * void run(skiatest::Reporter* reporter) {
   *         // No BBH
   *         this->run(nullptr, reporter);
   *
   *         // With an R-Tree
   *         SkRTreeFactory RTreeFactory;
   *         this->run(&RTreeFactory, reporter);
   *     }
   * ```
   */
  public fun run(reporter: Reporter?) {
    TODO("Implement run")
  }

  /**
   * C++ original:
   * ```cpp
   * void run(SkBBHFactory* factory, skiatest::Reporter* reporter) {
   *         SkCanvas playbackCanvas(fResultBitmap);
   *         playbackCanvas.clear(SK_ColorGREEN);
   *         SkPictureRecorder recorder;
   *         SkCanvas* recordCanvas = recorder.beginRecording(SkIntToScalar(fPictureWidth),
   *                                                          SkIntToScalar(fPictureHeight),
   *                                                          factory);
   *         this->doTest(playbackCanvas, *recordCanvas);
   *         sk_sp<SkPicture> picture(recorder.finishRecordingAsPicture());
   *         playbackCanvas.drawPicture(picture);
   *         REPORTER_ASSERT(reporter, SK_ColorGREEN == fResultBitmap.getColor(0, 0));
   *     }
   * ```
   */
  private fun run(factory: SkBBHFactory?, reporter: Reporter?) {
    TODO("Implement run")
  }
}
