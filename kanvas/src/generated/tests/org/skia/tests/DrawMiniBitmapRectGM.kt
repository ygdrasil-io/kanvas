package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class DrawMiniBitmapRectGM : public skiagm::GM {
 * public:
 *     DrawMiniBitmapRectGM(bool antiAlias) : fAA(antiAlias) {
 *         fName.set("drawminibitmaprect");
 *         if (fAA) {
 *             fName.appendf("_aa");
 *         }
 *     }
 *
 * protected:
 *     SkString getName() const override { return fName; }
 *
 *     SkISize getISize() override { return SkISize::Make(gSize, gSize); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         if (nullptr == fImage) {
 *             fImage = ToolUtils::MakeTextureImage(canvas, makebm(gSurfaceSize, gSurfaceSize));
 *         }
 *
 *         const SkRect dstRect = { 0, 0, SkIntToScalar(64), SkIntToScalar(64)};
 *         const int kMaxSrcRectSize = 1 << (SkNextLog2(gSurfaceSize) + 2);
 *
 *         constexpr int kPadX = 30;
 *         constexpr int kPadY = 40;
 *
 *         int rowCount = 0;
 *         canvas->translate(SkIntToScalar(kPadX), SkIntToScalar(kPadY));
 *         canvas->save();
 *         SkRandom random;
 *
 *         SkPaint paint;
 *         paint.setAntiAlias(fAA);
 *         for (int w = 1; w <= kMaxSrcRectSize; w *= 3) {
 *             for (int h = 1; h <= kMaxSrcRectSize; h *= 3) {
 *
 *                 const SkIRect srcRect =
 *                         SkIRect::MakeXYWH((gSurfaceSize - w) / 2, (gSurfaceSize - h) / 2, w, h);
 *                 canvas->save();
 *                 switch (random.nextU() % 3) {
 *                     case 0:
 *                         canvas->rotate(random.nextF() * 10.f);
 *                         break;
 *                     case 1:
 *                         canvas->rotate(-random.nextF() * 10.f);
 *                         break;
 *                     case 2:
 *                         // rect stays rect
 *                         break;
 *                 }
 *                 canvas->drawImageRect(fImage.get(), SkRect::Make(srcRect), dstRect,
 *                                       SkSamplingOptions(), &paint,
 *                                       SkCanvas::kFast_SrcRectConstraint);
 *                 canvas->restore();
 *
 *                 canvas->translate(dstRect.width() + SK_Scalar1 * kPadX, 0);
 *                 ++rowCount;
 *                 if ((dstRect.width() + 2 * kPadX) * rowCount > gSize) {
 *                     canvas->restore();
 *                     canvas->translate(0, dstRect.height() + SK_Scalar1 * kPadY);
 *                     canvas->save();
 *                     rowCount = 0;
 *                 }
 *             }
 *         }
 *         canvas->restore();
 *     }
 *
 * private:
 *     bool            fAA;
 *     sk_sp<SkImage>  fImage;
 *     SkString        fName;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class DrawMiniBitmapRectGM public constructor(
  antiAlias: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * bool            fAA
   * ```
   */
  private var fAA: Boolean = TODO("Initialize fAA")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>  fImage
   * ```
   */
  private var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * SkString        fName
   * ```
   */
  private var fName: String = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return fName; }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(gSize, gSize); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         if (nullptr == fImage) {
   *             fImage = ToolUtils::MakeTextureImage(canvas, makebm(gSurfaceSize, gSurfaceSize));
   *         }
   *
   *         const SkRect dstRect = { 0, 0, SkIntToScalar(64), SkIntToScalar(64)};
   *         const int kMaxSrcRectSize = 1 << (SkNextLog2(gSurfaceSize) + 2);
   *
   *         constexpr int kPadX = 30;
   *         constexpr int kPadY = 40;
   *
   *         int rowCount = 0;
   *         canvas->translate(SkIntToScalar(kPadX), SkIntToScalar(kPadY));
   *         canvas->save();
   *         SkRandom random;
   *
   *         SkPaint paint;
   *         paint.setAntiAlias(fAA);
   *         for (int w = 1; w <= kMaxSrcRectSize; w *= 3) {
   *             for (int h = 1; h <= kMaxSrcRectSize; h *= 3) {
   *
   *                 const SkIRect srcRect =
   *                         SkIRect::MakeXYWH((gSurfaceSize - w) / 2, (gSurfaceSize - h) / 2, w, h);
   *                 canvas->save();
   *                 switch (random.nextU() % 3) {
   *                     case 0:
   *                         canvas->rotate(random.nextF() * 10.f);
   *                         break;
   *                     case 1:
   *                         canvas->rotate(-random.nextF() * 10.f);
   *                         break;
   *                     case 2:
   *                         // rect stays rect
   *                         break;
   *                 }
   *                 canvas->drawImageRect(fImage.get(), SkRect::Make(srcRect), dstRect,
   *                                       SkSamplingOptions(), &paint,
   *                                       SkCanvas::kFast_SrcRectConstraint);
   *                 canvas->restore();
   *
   *                 canvas->translate(dstRect.width() + SK_Scalar1 * kPadX, 0);
   *                 ++rowCount;
   *                 if ((dstRect.width() + 2 * kPadX) * rowCount > gSize) {
   *                     canvas->restore();
   *                     canvas->translate(0, dstRect.height() + SK_Scalar1 * kPadY);
   *                     canvas->save();
   *                     rowCount = 0;
   *                 }
   *             }
   *         }
   *         canvas->restore();
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
