package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class NinePatchStretchGM : public skiagm::GM {
 * public:
 *     sk_sp<SkImage>  fImage;
 *     SkIRect         fCenter;
 *
 *     NinePatchStretchGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("ninepatch-stretch"); }
 *
 *     SkISize getISize() override { return SkISize::Make(760, 800); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         if (!fImage || !fImage->isValid(canvas->baseRecorder())) {
 *             fImage = make_image(canvas, &fCenter);
 *         }
 *
 *         // amount of bm that should not be stretched (unless we have to)
 *         const SkScalar fixed = SkIntToScalar(fImage->width() - fCenter.width());
 *
 *         const SkSize size[] = {
 *             { fixed * 4 / 5, fixed * 4 / 5 },   // shrink in both axes
 *             { fixed * 4 / 5, fixed * 4 },       // shrink in X
 *             { fixed * 4,     fixed * 4 / 5 },   // shrink in Y
 *             { fixed * 4,     fixed * 4 }
 *         };
 *
 *         canvas->drawImage(fImage, 10, 10);
 *
 *         SkScalar x = SkIntToScalar(100);
 *         SkScalar y = SkIntToScalar(100);
 *
 *         SkPaint paint;
 *         for (auto fm : {SkFilterMode::kLinear, SkFilterMode::kNearest}) {
 *             for (int iy = 0; iy < 2; ++iy) {
 *                 for (int ix = 0; ix < 2; ++ix) {
 *                     int i = ix * 2 + iy;
 *                     SkRect r = SkRect::MakeXYWH(x + ix * fixed, y + iy * fixed,
 *                                                 size[i].width(), size[i].height());
 *                     canvas->drawImageNine(fImage.get(), fCenter, r, fm);
 *                 }
 *             }
 *             canvas->translate(0, 400);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class NinePatchStretchGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>  fImage
   * ```
   */
  public var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * SkIRect         fCenter
   * ```
   */
  public var fCenter: SkIRect = TODO("Initialize fCenter")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("ninepatch-stretch"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(760, 800); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         if (!fImage || !fImage->isValid(canvas->baseRecorder())) {
   *             fImage = make_image(canvas, &fCenter);
   *         }
   *
   *         // amount of bm that should not be stretched (unless we have to)
   *         const SkScalar fixed = SkIntToScalar(fImage->width() - fCenter.width());
   *
   *         const SkSize size[] = {
   *             { fixed * 4 / 5, fixed * 4 / 5 },   // shrink in both axes
   *             { fixed * 4 / 5, fixed * 4 },       // shrink in X
   *             { fixed * 4,     fixed * 4 / 5 },   // shrink in Y
   *             { fixed * 4,     fixed * 4 }
   *         };
   *
   *         canvas->drawImage(fImage, 10, 10);
   *
   *         SkScalar x = SkIntToScalar(100);
   *         SkScalar y = SkIntToScalar(100);
   *
   *         SkPaint paint;
   *         for (auto fm : {SkFilterMode::kLinear, SkFilterMode::kNearest}) {
   *             for (int iy = 0; iy < 2; ++iy) {
   *                 for (int ix = 0; ix < 2; ++ix) {
   *                     int i = ix * 2 + iy;
   *                     SkRect r = SkRect::MakeXYWH(x + ix * fixed, y + iy * fixed,
   *                                                 size[i].width(), size[i].height());
   *                     canvas->drawImageNine(fImage.get(), fCenter, r, fm);
   *                 }
   *             }
   *             canvas->translate(0, 400);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
