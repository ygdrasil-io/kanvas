package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class PictureShaderCacheGM : public skiagm::GM {
 * public:
 *     PictureShaderCacheGM(SkScalar tileSize)
 *         : fTileSize(tileSize) {
 *     }
 *
 *  protected:
 *     void drawTile(SkCanvas* canvas) {
 *         SkPaint paint;
 *         paint.setColor(SK_ColorGREEN);
 *         paint.setStyle(SkPaint::kFill_Style);
 *         paint.setAntiAlias(true);
 *
 *         canvas->drawCircle(fTileSize / 4, fTileSize / 4, fTileSize / 4, paint);
 *         canvas->drawRect(SkRect::MakeXYWH(fTileSize / 2, fTileSize / 2,
 *                                           fTileSize / 2, fTileSize / 2), paint);
 *
 *         paint.setColor(SK_ColorRED);
 *         canvas->drawLine(fTileSize / 2, fTileSize * 1 / 3,
 *                          fTileSize / 2, fTileSize * 2 / 3, paint);
 *         canvas->drawLine(fTileSize * 1 / 3, fTileSize / 2,
 *                          fTileSize * 2 / 3, fTileSize / 2, paint);
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         SkPictureRecorder recorder;
 *         SkCanvas* pictureCanvas = recorder.beginRecording(fTileSize, fTileSize);
 *         this->drawTile(pictureCanvas);
 *         fPicture = recorder.finishRecordingAsPicture();
 *     }
 *
 *     SkString getName() const override { return SkString("pictureshadercache"); }
 *
 *     SkISize getISize() override { return SkISize::Make(100, 100); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setShader(fPicture->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
 *                                              SkFilterMode::kNearest));
 *
 *         {
 *             // Render in a funny color space that converts green to yellow.
 *             skcms_Matrix3x3 greenToYellow = {{
 *                 { 1, 1, 0 },
 *                 { 0, 1, 0 },
 *                 { 0, 0, 1 },
 *             }};
 *             sk_sp<SkColorSpace> gty = SkColorSpace::MakeRGB(SkNamedTransferFn::kSRGB,
 *                                                             greenToYellow);
 *             SkImageInfo info = SkImageInfo::MakeN32Premul(100, 100, std::move(gty));
 *             sk_sp<SkSurface> surface(SkSurfaces::Raster(info));
 *             surface->getCanvas()->drawRect(SkRect::MakeWH(fTileSize, fTileSize), paint);
 *         }
 *
 *         // When we draw to the canvas, we should see green because we should *not* reuse the
 *         // cached picture shader.
 *         canvas->drawRect(SkRect::MakeWH(fTileSize, fTileSize), paint);
 *     }
 *
 * private:
 *     SkScalar         fTileSize;
 *     sk_sp<SkPicture> fPicture;
 *     SkBitmap         fBitmap;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class PictureShaderCacheGM public constructor(
  tileSize: SkScalar,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkScalar         fTileSize
   * ```
   */
  private var fTileSize: SkScalar = TODO("Initialize fTileSize")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> fPicture
   * ```
   */
  private var fPicture: SkSp<SkPicture> = TODO("Initialize fPicture")

  /**
   * C++ original:
   * ```cpp
   * SkBitmap         fBitmap
   * ```
   */
  private var fBitmap: SkBitmap = TODO("Initialize fBitmap")

  /**
   * C++ original:
   * ```cpp
   * void drawTile(SkCanvas* canvas) {
   *         SkPaint paint;
   *         paint.setColor(SK_ColorGREEN);
   *         paint.setStyle(SkPaint::kFill_Style);
   *         paint.setAntiAlias(true);
   *
   *         canvas->drawCircle(fTileSize / 4, fTileSize / 4, fTileSize / 4, paint);
   *         canvas->drawRect(SkRect::MakeXYWH(fTileSize / 2, fTileSize / 2,
   *                                           fTileSize / 2, fTileSize / 2), paint);
   *
   *         paint.setColor(SK_ColorRED);
   *         canvas->drawLine(fTileSize / 2, fTileSize * 1 / 3,
   *                          fTileSize / 2, fTileSize * 2 / 3, paint);
   *         canvas->drawLine(fTileSize * 1 / 3, fTileSize / 2,
   *                          fTileSize * 2 / 3, fTileSize / 2, paint);
   *     }
   * ```
   */
  protected fun drawTile(canvas: SkCanvas?) {
    TODO("Implement drawTile")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         SkPictureRecorder recorder;
   *         SkCanvas* pictureCanvas = recorder.beginRecording(fTileSize, fTileSize);
   *         this->drawTile(pictureCanvas);
   *         fPicture = recorder.finishRecordingAsPicture();
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("pictureshadercache"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(100, 100); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *         paint.setShader(fPicture->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
   *                                              SkFilterMode::kNearest));
   *
   *         {
   *             // Render in a funny color space that converts green to yellow.
   *             skcms_Matrix3x3 greenToYellow = {{
   *                 { 1, 1, 0 },
   *                 { 0, 1, 0 },
   *                 { 0, 0, 1 },
   *             }};
   *             sk_sp<SkColorSpace> gty = SkColorSpace::MakeRGB(SkNamedTransferFn::kSRGB,
   *                                                             greenToYellow);
   *             SkImageInfo info = SkImageInfo::MakeN32Premul(100, 100, std::move(gty));
   *             sk_sp<SkSurface> surface(SkSurfaces::Raster(info));
   *             surface->getCanvas()->drawRect(SkRect::MakeWH(fTileSize, fTileSize), paint);
   *         }
   *
   *         // When we draw to the canvas, we should see green because we should *not* reuse the
   *         // cached picture shader.
   *         canvas->drawRect(SkRect::MakeWH(fTileSize, fTileSize), paint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
