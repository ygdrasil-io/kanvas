package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkAutoPixmapStorage
import org.skia.core.SkSurface
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.gpu.Recorder
import org.skia.gpu.Recording
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class Redrawer {
 * public:
 *     Redrawer(skiatest::Reporter* reporter, Recorder* recorder)
 *             : fReporter(reporter)
 *             , fRecorder(recorder) {
 *         SkImageInfo ii = SkImageInfo::Make(kSurfaceSize,
 *                                            kRGBA_8888_SkColorType,
 *                                            kPremul_SkAlphaType);
 *         fReadbackPM.alloc(ii);
 *     }
 *
 *     void init(SkImage* imageToDraw) {
 *         SkImageInfo ii = SkImageInfo::Make(kSurfaceSize,
 *                                            kRGBA_8888_SkColorType,
 *                                            kPremul_SkAlphaType);
 *         fImgDrawSurface = SkSurfaces::RenderTarget(fRecorder, ii, Mipmapped::kNo);
 *         REPORTER_ASSERT(fReporter, fImgDrawSurface);
 *
 *         fImgDrawRecording = MakeRedrawRecording(fRecorder, fImgDrawSurface.get(), imageToDraw);
 *     }
 *
 *     Recording* imgDrawRecording() {
 *         return fImgDrawRecording.get();
 *     }
 *
 *     // This is here bc it uses a lot from the Redrawer (i.e., its recorder, its surface, etc.).
 *     void checkResult(Context* context,
 *                      int testcaseID,
 *                      bool useTwoRecorders,
 *                      bool withMips,
 *                      const SkColor4f& expectedColor) {
 *
 *         fReadbackPM.erase(SkColors::kTransparent);
 *
 *         if (!fImgDrawSurface->readPixels(fReadbackPM, 0, 0)) {
 *             ERRORF(fReporter, "readPixels failed");
 *         }
 *
 *         auto error = std::function<ComparePixmapsErrorReporter>(
 *                 [&](int x, int y, const float diffs[4]) {
 *                     ERRORF(fReporter,
 *                            "case %d%c - %s: "
 *                            "expected (%.1f %.1f %.1f %.1f) "
 *                            "- diffs (%.1f, %.1f, %.1f, %.1f)",
 *                            testcaseID, useTwoRecorders ? 'b' : 'a',
 *                            withMips ? "mipmapped" : "not-mipmapped",
 *                            expectedColor.fR, expectedColor.fG, expectedColor.fB, expectedColor.fA,
 *                            diffs[0], diffs[1], diffs[2], diffs[3]);
 *                 });
 *
 *         static constexpr float kTol[] = {0, 0, 0, 0};
 *         CheckSolidPixels(expectedColor, fReadbackPM, kTol, error);
 *     }
 *
 * private:
 *     static std::unique_ptr<Recording> MakeRedrawRecording(Recorder* recorder,
 *                                                           SkSurface* surfaceToDrawTo,
 *                                                           SkImage* imageToDraw) {
 *         SkSamplingOptions sampling = SkSamplingOptions(SkFilterMode::kLinear,
 *                                                        SkMipmapMode::kNearest);
 *
 *         SkCanvas* canvas = surfaceToDrawTo->getCanvas();
 *
 *         canvas->clear(SkColors::kTransparent);
 *         canvas->drawImageRect(imageToDraw,
 *                               SkRect::MakeWH(kSurfaceSize.width(), kSurfaceSize.height()),
 *                               sampling);
 *
 *         return recorder->snap();
 *     }
 *
 *     skiatest::Reporter* fReporter;
 *     Recorder* fRecorder;
 *
 *     sk_sp<SkSurface> fImgDrawSurface;
 *     std::unique_ptr<Recording> fImgDrawRecording;
 *
 *     SkAutoPixmapStorage fReadbackPM;
 * }
 * ```
 */
public data class Redrawer public constructor(
  /**
   * C++ original:
   * ```cpp
   * skiatest::Reporter* fReporter
   * ```
   */
  private var fReporter: Reporter?,
  /**
   * C++ original:
   * ```cpp
   * Recorder* fRecorder
   * ```
   */
  private var fRecorder: Recorder?,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface> fImgDrawSurface
   * ```
   */
  private var fImgDrawSurface: SkSp<SkSurface>,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<Recording> fImgDrawRecording
   * ```
   */
  private var fImgDrawRecording: Int,
  /**
   * C++ original:
   * ```cpp
   * SkAutoPixmapStorage fReadbackPM
   * ```
   */
  private var fReadbackPM: SkAutoPixmapStorage,
) {
  /**
   * C++ original:
   * ```cpp
   * void init(SkImage* imageToDraw) {
   *         SkImageInfo ii = SkImageInfo::Make(kSurfaceSize,
   *                                            kRGBA_8888_SkColorType,
   *                                            kPremul_SkAlphaType);
   *         fImgDrawSurface = SkSurfaces::RenderTarget(fRecorder, ii, Mipmapped::kNo);
   *         REPORTER_ASSERT(fReporter, fImgDrawSurface);
   *
   *         fImgDrawRecording = MakeRedrawRecording(fRecorder, fImgDrawSurface.get(), imageToDraw);
   *     }
   * ```
   */
  public fun `init`(imageToDraw: SkImage?) {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * Recording* imgDrawRecording() {
   *         return fImgDrawRecording.get();
   *     }
   * ```
   */
  public fun imgDrawRecording(): Recording {
    TODO("Implement imgDrawRecording")
  }

  /**
   * C++ original:
   * ```cpp
   * void checkResult(Context* context,
   *                      int testcaseID,
   *                      bool useTwoRecorders,
   *                      bool withMips,
   *                      const SkColor4f& expectedColor) {
   *
   *         fReadbackPM.erase(SkColors::kTransparent);
   *
   *         if (!fImgDrawSurface->readPixels(fReadbackPM, 0, 0)) {
   *             ERRORF(fReporter, "readPixels failed");
   *         }
   *
   *         auto error = std::function<ComparePixmapsErrorReporter>(
   *                 [&](int x, int y, const float diffs[4]) {
   *                     ERRORF(fReporter,
   *                            "case %d%c - %s: "
   *                            "expected (%.1f %.1f %.1f %.1f) "
   *                            "- diffs (%.1f, %.1f, %.1f, %.1f)",
   *                            testcaseID, useTwoRecorders ? 'b' : 'a',
   *                            withMips ? "mipmapped" : "not-mipmapped",
   *                            expectedColor.fR, expectedColor.fG, expectedColor.fB, expectedColor.fA,
   *                            diffs[0], diffs[1], diffs[2], diffs[3]);
   *                 });
   *
   *         static constexpr float kTol[] = {0, 0, 0, 0};
   *         CheckSolidPixels(expectedColor, fReadbackPM, kTol, error);
   *     }
   * ```
   */
  public fun checkResult(
    context: Context?,
    testcaseID: Int,
    useTwoRecorders: Boolean,
    withMips: Boolean,
    expectedColor: SkColor4f,
  ) {
    TODO("Implement checkResult")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static std::unique_ptr<Recording> MakeRedrawRecording(Recorder* recorder,
     *                                                           SkSurface* surfaceToDrawTo,
     *                                                           SkImage* imageToDraw) {
     *         SkSamplingOptions sampling = SkSamplingOptions(SkFilterMode::kLinear,
     *                                                        SkMipmapMode::kNearest);
     *
     *         SkCanvas* canvas = surfaceToDrawTo->getCanvas();
     *
     *         canvas->clear(SkColors::kTransparent);
     *         canvas->drawImageRect(imageToDraw,
     *                               SkRect::MakeWH(kSurfaceSize.width(), kSurfaceSize.height()),
     *                               sampling);
     *
     *         return recorder->snap();
     *     }
     * ```
     */
    private fun makeRedrawRecording(
      recorder: Recorder?,
      surfaceToDrawTo: SkSurface?,
      imageToDraw: SkImage?,
    ): Int {
      TODO("Implement makeRedrawRecording")
    }
  }
}
