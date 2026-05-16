package org.skia.tests

import kotlin.CharArray
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class DrawBitmapRectGM : public skiagm::GM {
 * public:
 *     DrawBitmapRectGM(DrawRectRectProc proc, const char suffix[]) : fProc(proc) {
 *         fName.set("drawbitmaprect");
 *         if (suffix) {
 *             fName.append(suffix);
 *         }
 *     }
 *
 *     DrawRectRectProc*   fProc;
 *     SkBitmap            fLargeBitmap;
 *     sk_sp<SkImage>      fImage;
 *     SkString            fName;
 *
 * protected:
 *     SkString getName() const override { return fName; }
 *
 *     SkISize getISize() override { return SkISize::Make(gSize, gSize); }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         if (!fImage || !fImage->isValid(canvas->baseRecorder())) {
 *             fImage = ToolUtils::MakeTextureImage(canvas,
 *                                                  makebm(canvas, &fLargeBitmap, gBmpSize, gBmpSize));
 *             if (!fImage) {
 *                 *errorMsg = "Image creation failed";
 *                 return DrawResult::kSkip;
 *             }
 *         }
 *
 *         SkRect dstRect = { 0, 0, SkIntToScalar(64), SkIntToScalar(64)};
 *         const int kMaxSrcRectSize = 1 << (SkNextLog2(gBmpSize) + 2);
 *
 *         const int kPadX = 30;
 *         const int kPadY = 40;
 *         SkPaint alphaPaint;
 *         alphaPaint.setAlphaf(0.125f);
 *         canvas->drawImageRect(fImage, SkRect::MakeIWH(gSize, gSize), SkSamplingOptions(),
 *                               &alphaPaint);
 *         canvas->translate(SK_Scalar1 * kPadX / 2,
 *                           SK_Scalar1 * kPadY / 2);
 *         SkPaint blackPaint;
 *         SkScalar titleHeight = SK_Scalar1 * 24;
 *         blackPaint.setColor(SK_ColorBLACK);
 *         blackPaint.setAntiAlias(true);
 *
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), titleHeight);
 *
 *         SkString title;
 *         title.printf("Bitmap size: %d x %d", gBmpSize, gBmpSize);
 *         canvas->drawString(title, 0, titleHeight, font, blackPaint);
 *
 *         canvas->translate(0, SK_Scalar1 * kPadY / 2  + titleHeight);
 *         int rowCount = 0;
 *         canvas->save();
 *         for (int w = 1; w <= kMaxSrcRectSize; w *= 4) {
 *             for (int h = 1; h <= kMaxSrcRectSize; h *= 4) {
 *
 *                 SkIRect srcRect = SkIRect::MakeXYWH((gBmpSize - w) / 2, (gBmpSize - h) / 2, w, h);
 *                 fProc(canvas, fImage, fLargeBitmap, srcRect, dstRect, SkSamplingOptions(),
 *                       nullptr);
 *
 *                 SkString label;
 *                 label.appendf("%d x %d", w, h);
 *                 blackPaint.setAntiAlias(true);
 *                 blackPaint.setStyle(SkPaint::kFill_Style);
 *                 font.setSize(SK_Scalar1 * 10);
 *                 SkScalar baseline = dstRect.height() + font.getSize() + SK_Scalar1 * 3;
 *                 canvas->drawString(label, 0, baseline, font, blackPaint);
 *                 blackPaint.setStyle(SkPaint::kStroke_Style);
 *                 blackPaint.setStrokeWidth(SK_Scalar1);
 *                 blackPaint.setAntiAlias(false);
 *                 canvas->drawRect(dstRect, blackPaint);
 *
 *                 canvas->translate(dstRect.width() + SK_Scalar1 * kPadX, 0);
 *                 ++rowCount;
 *                 if ((dstRect.width() + kPadX) * rowCount > gSize) {
 *                     canvas->restore();
 *                     canvas->translate(0, dstRect.height() + SK_Scalar1 * kPadY);
 *                     canvas->save();
 *                     rowCount = 0;
 *                 }
 *             }
 *         }
 *
 *         {
 *             // test the following code path:
 *             // SkGpuDevice::drawPath() -> SkGpuDevice::drawWithMaskFilter()
 *             SkIRect srcRect;
 *             SkPaint maskPaint;
 *             SkBitmap bm = make_chessbm(5, 5);
 *             sk_sp<SkImage> img = ToolUtils::MakeTextureImage(canvas, bm.asImage());
 *
 *             srcRect.setXYWH(1, 1, 3, 3);
 *             maskPaint.setMaskFilter(SkMaskFilter::MakeBlur(
 *                 kNormal_SkBlurStyle,
 *                 SkBlurMask::ConvertRadiusToSigma(SkIntToScalar(5))));
 *
 *             fProc(canvas, img, bm, srcRect, dstRect,
 *                   SkSamplingOptions(SkFilterMode::kLinear), &maskPaint);
 *         }
 *
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class DrawBitmapRectGM public constructor(
  proc: DrawRectRectProc,
  suffix: CharArray,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * DrawRectRectProc*   fProc
   * ```
   */
  public var fProc: DrawRectRectProc? = TODO("Initialize fProc")

  /**
   * C++ original:
   * ```cpp
   * SkBitmap            fLargeBitmap
   * ```
   */
  public var fLargeBitmap: SkBitmap = TODO("Initialize fLargeBitmap")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>      fImage
   * ```
   */
  public var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * SkString            fName
   * ```
   */
  public var fName: String = TODO("Initialize fName")

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
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         if (!fImage || !fImage->isValid(canvas->baseRecorder())) {
   *             fImage = ToolUtils::MakeTextureImage(canvas,
   *                                                  makebm(canvas, &fLargeBitmap, gBmpSize, gBmpSize));
   *             if (!fImage) {
   *                 *errorMsg = "Image creation failed";
   *                 return DrawResult::kSkip;
   *             }
   *         }
   *
   *         SkRect dstRect = { 0, 0, SkIntToScalar(64), SkIntToScalar(64)};
   *         const int kMaxSrcRectSize = 1 << (SkNextLog2(gBmpSize) + 2);
   *
   *         const int kPadX = 30;
   *         const int kPadY = 40;
   *         SkPaint alphaPaint;
   *         alphaPaint.setAlphaf(0.125f);
   *         canvas->drawImageRect(fImage, SkRect::MakeIWH(gSize, gSize), SkSamplingOptions(),
   *                               &alphaPaint);
   *         canvas->translate(SK_Scalar1 * kPadX / 2,
   *                           SK_Scalar1 * kPadY / 2);
   *         SkPaint blackPaint;
   *         SkScalar titleHeight = SK_Scalar1 * 24;
   *         blackPaint.setColor(SK_ColorBLACK);
   *         blackPaint.setAntiAlias(true);
   *
   *         SkFont font(ToolUtils::DefaultPortableTypeface(), titleHeight);
   *
   *         SkString title;
   *         title.printf("Bitmap size: %d x %d", gBmpSize, gBmpSize);
   *         canvas->drawString(title, 0, titleHeight, font, blackPaint);
   *
   *         canvas->translate(0, SK_Scalar1 * kPadY / 2  + titleHeight);
   *         int rowCount = 0;
   *         canvas->save();
   *         for (int w = 1; w <= kMaxSrcRectSize; w *= 4) {
   *             for (int h = 1; h <= kMaxSrcRectSize; h *= 4) {
   *
   *                 SkIRect srcRect = SkIRect::MakeXYWH((gBmpSize - w) / 2, (gBmpSize - h) / 2, w, h);
   *                 fProc(canvas, fImage, fLargeBitmap, srcRect, dstRect, SkSamplingOptions(),
   *                       nullptr);
   *
   *                 SkString label;
   *                 label.appendf("%d x %d", w, h);
   *                 blackPaint.setAntiAlias(true);
   *                 blackPaint.setStyle(SkPaint::kFill_Style);
   *                 font.setSize(SK_Scalar1 * 10);
   *                 SkScalar baseline = dstRect.height() + font.getSize() + SK_Scalar1 * 3;
   *                 canvas->drawString(label, 0, baseline, font, blackPaint);
   *                 blackPaint.setStyle(SkPaint::kStroke_Style);
   *                 blackPaint.setStrokeWidth(SK_Scalar1);
   *                 blackPaint.setAntiAlias(false);
   *                 canvas->drawRect(dstRect, blackPaint);
   *
   *                 canvas->translate(dstRect.width() + SK_Scalar1 * kPadX, 0);
   *                 ++rowCount;
   *                 if ((dstRect.width() + kPadX) * rowCount > gSize) {
   *                     canvas->restore();
   *                     canvas->translate(0, dstRect.height() + SK_Scalar1 * kPadY);
   *                     canvas->save();
   *                     rowCount = 0;
   *                 }
   *             }
   *         }
   *
   *         {
   *             // test the following code path:
   *             // SkGpuDevice::drawPath() -> SkGpuDevice::drawWithMaskFilter()
   *             SkIRect srcRect;
   *             SkPaint maskPaint;
   *             SkBitmap bm = make_chessbm(5, 5);
   *             sk_sp<SkImage> img = ToolUtils::MakeTextureImage(canvas, bm.asImage());
   *
   *             srcRect.setXYWH(1, 1, 3, 3);
   *             maskPaint.setMaskFilter(SkMaskFilter::MakeBlur(
   *                 kNormal_SkBlurStyle,
   *                 SkBlurMask::ConvertRadiusToSigma(SkIntToScalar(5))));
   *
   *             fProc(canvas, img, bm, srcRect, dstRect,
   *                   SkSamplingOptions(SkFilterMode::kLinear), &maskPaint);
   *         }
   *
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
