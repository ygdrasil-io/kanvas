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
 * class DrawBitmapRect4 : public skiagm::GM {
 *     bool fUseIRect;
 *     sk_sp<SkImage> fBigImage;
 *
 * public:
 *     DrawBitmapRect4(bool useIRect) : fUseIRect(useIRect) {
 *         this->setBGColor(0x88444444);
 *     }
 *
 * protected:
 *     SkString getName() const override {
 *         SkString str;
 *         str.printf("bigbitmaprect_%s", fUseIRect ? "i" : "s");
 *         return str;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         if (!fBigImage) {
 *             fBigImage = make_big_bitmap(canvas);
 *         }
 *
 *         SkPaint paint;
 *         paint.setAlpha(128);
 *         paint.setBlendMode(SkBlendMode::kXor);
 *         SkSamplingOptions sampling;
 *
 *         SkRect srcR1 = { 0.0f, 0.0f, 4096.0f, 2040.0f };
 *         SkRect dstR1 = { 10.1f, 10.1f, 629.9f, 400.9f };
 *
 *         SkRect srcR2 = { 4085.0f, 10.0f, 4087.0f, 12.0f };
 *         SkRect dstR2 = { 10, 410, 30, 430 };
 *
 *         if (!fUseIRect) {
 *             canvas->drawImageRect(fBigImage, srcR1, dstR1, sampling, &paint,
 *                                    SkCanvas::kStrict_SrcRectConstraint);
 *             canvas->drawImageRect(fBigImage, srcR2, dstR2, sampling, &paint,
 *                                    SkCanvas::kStrict_SrcRectConstraint);
 *         } else {
 *             canvas->drawImageRect(fBigImage, SkRect::Make(srcR1.roundOut()), dstR1, sampling,
 *                                   &paint, SkCanvas::kStrict_SrcRectConstraint);
 *             canvas->drawImageRect(fBigImage, SkRect::Make(srcR2.roundOut()), dstR2, sampling,
 *                                   &paint, SkCanvas::kStrict_SrcRectConstraint);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class DrawBitmapRect4 public constructor(
  useIRect: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * bool fUseIRect
   * ```
   */
  private var fUseIRect: Boolean = TODO("Initialize fUseIRect")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fBigImage
   * ```
   */
  private var fBigImage: SkSp<SkImage> = TODO("Initialize fBigImage")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString str;
   *         str.printf("bigbitmaprect_%s", fUseIRect ? "i" : "s");
   *         return str;
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(640, 480); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         if (!fBigImage) {
   *             fBigImage = make_big_bitmap(canvas);
   *         }
   *
   *         SkPaint paint;
   *         paint.setAlpha(128);
   *         paint.setBlendMode(SkBlendMode::kXor);
   *         SkSamplingOptions sampling;
   *
   *         SkRect srcR1 = { 0.0f, 0.0f, 4096.0f, 2040.0f };
   *         SkRect dstR1 = { 10.1f, 10.1f, 629.9f, 400.9f };
   *
   *         SkRect srcR2 = { 4085.0f, 10.0f, 4087.0f, 12.0f };
   *         SkRect dstR2 = { 10, 410, 30, 430 };
   *
   *         if (!fUseIRect) {
   *             canvas->drawImageRect(fBigImage, srcR1, dstR1, sampling, &paint,
   *                                    SkCanvas::kStrict_SrcRectConstraint);
   *             canvas->drawImageRect(fBigImage, srcR2, dstR2, sampling, &paint,
   *                                    SkCanvas::kStrict_SrcRectConstraint);
   *         } else {
   *             canvas->drawImageRect(fBigImage, SkRect::Make(srcR1.roundOut()), dstR1, sampling,
   *                                   &paint, SkCanvas::kStrict_SrcRectConstraint);
   *             canvas->drawImageRect(fBigImage, SkRect::Make(srcR2.roundOut()), dstR2, sampling,
   *                                   &paint, SkCanvas::kStrict_SrcRectConstraint);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
