package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SkCaptureManager : public SkRefCnt {
 * public:
 *     SkCaptureManager();
 *
 *     SkCanvas* makeCaptureCanvas(SkCanvas* canvas);
 *     void snapPictures();
 *     void snapPicture(SkSurface*);
 *
 *     void toggleCapture(bool capturing);
 *
 *     bool isCurrentlyCapturing() const {
 *         return fIsCurrentlyCapturing;
 *     }
 *
 *     sk_sp<SkCapture> getLastCapture() const;
 *
 * private:
 *     std::atomic<bool> fIsCurrentlyCapturing = false;
 *     skia_private::TArray<std::unique_ptr<SkCaptureCanvas>> fTrackedCanvases;
 *     skia_private::TArray<sk_sp<SkPicture>>  fPictures;
 *
 *     sk_sp<SkCapture> fLastCapture;
 * }
 * ```
 */
public open class SkCaptureManager public constructor() : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * std::atomic<bool> fIsCurrentlyCapturing
   * ```
   */
  private var fIsCurrentlyCapturing: Int = TODO("Initialize fIsCurrentlyCapturing")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<SkPicture>>  fPictures
   * ```
   */
  private var fPictures: Int = TODO("Initialize fPictures")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkCapture> fLastCapture
   * ```
   */
  private var fLastCapture: SkSp<SkCapture> = TODO("Initialize fLastCapture")

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* SkCaptureManager::makeCaptureCanvas(SkCanvas* canvas) {
   *     auto newCanvas = std::make_unique<SkCaptureCanvas>(canvas, this);
   *     auto rawCanvasPtr = newCanvas.get();
   *     fTrackedCanvases.emplace_back(std::move(newCanvas));
   *     return rawCanvasPtr;
   * }
   * ```
   */
  public fun makeCaptureCanvas(canvas: SkCanvas?): SkCanvas {
    TODO("Implement makeCaptureCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureManager::snapPictures() {
   *     for (auto& canvas : fTrackedCanvases) {
   *         if (canvas) {
   *             auto picture = canvas->snapPicture();
   *             if (picture) {
   *                 fPictures.emplace_back(picture);
   *             }
   *         }
   *     }
   * }
   * ```
   */
  public fun snapPictures() {
    TODO("Implement snapPictures")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureManager::snapPicture(SkSurface* surface) {
   *     for (auto& canvas : fTrackedCanvases) {
   *         if (canvas) {
   *             if (canvas->getSurface() == surface) {
   *                 auto picture = canvas->snapPicture();
   *                 if (picture) {
   *                     // TODO(412351769): for every storing of a picture, we should track a content id
   *                     // and the surface it was drawn to.
   *                     fPictures.emplace_back(picture);
   *                 }
   *                 return;
   *             }
   *         }
   *     }
   * }
   * ```
   */
  public fun snapPicture(surface: SkSurface?) {
    TODO("Implement snapPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureManager::toggleCapture(bool capturing) {
   *     if (capturing != fIsCurrentlyCapturing && !capturing) {
   *         // on capture stop, save the capture and reset
   *         this->snapPictures();
   *         fLastCapture = SkCapture::MakeFromPictures(fPictures);
   *         fPictures.clear();
   *     }
   *     fIsCurrentlyCapturing = capturing;
   * }
   * ```
   */
  public fun toggleCapture(capturing: Boolean) {
    TODO("Implement toggleCapture")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isCurrentlyCapturing() const {
   *         return fIsCurrentlyCapturing;
   *     }
   * ```
   */
  public fun isCurrentlyCapturing(): Boolean {
    TODO("Implement isCurrentlyCapturing")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkCapture> SkCaptureManager::getLastCapture() const {
   *    return fLastCapture;
   * }
   * ```
   */
  public fun getLastCapture(): SkSp<SkCapture> {
    TODO("Implement getLastCapture")
  }
}
