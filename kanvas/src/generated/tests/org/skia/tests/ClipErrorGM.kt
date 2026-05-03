package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ClipErrorGM : public skiagm::GM {
 * public:
 *     ClipErrorGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("cliperror"); }
 *
 *     SkISize getISize() override { return SkISize::Make(WIDTH, HEIGHT); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), 256);
 *
 *         // setup up maskfilter
 *         const SkScalar kSigma = SkBlurMask::ConvertRadiusToSigma(SkIntToScalar(50));
 *
 *         SkPaint blurPaint(paint);
 *         blurPaint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, kSigma));
 *
 *         const char text[] = "hambur";
 *         auto blob = SkTextBlob::MakeFromText(text, strlen(text), font);
 *
 *         SkPaint clearPaint(paint);
 *         clearPaint.setColor(SK_ColorWHITE);
 *
 *         canvas->save();
 *         canvas->translate(0, 0);
 *         canvas->clipRect(SkRect::MakeLTRB(0, 0, WIDTH, 256));
 *         draw_text(canvas, blob, paint, blurPaint, clearPaint);
 *         canvas->restore();
 *
 *         canvas->save();
 *         canvas->translate(0, 256);
 *         canvas->clipRect(SkRect::MakeLTRB(0, 256, WIDTH, 510));
 *         draw_text(canvas, blob, paint, blurPaint, clearPaint);
 *         canvas->restore();
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class ClipErrorGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("cliperror"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(WIDTH, HEIGHT); }
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
   *         paint.setAntiAlias(true);
   *
   *         SkFont font(ToolUtils::DefaultPortableTypeface(), 256);
   *
   *         // setup up maskfilter
   *         const SkScalar kSigma = SkBlurMask::ConvertRadiusToSigma(SkIntToScalar(50));
   *
   *         SkPaint blurPaint(paint);
   *         blurPaint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, kSigma));
   *
   *         const char text[] = "hambur";
   *         auto blob = SkTextBlob::MakeFromText(text, strlen(text), font);
   *
   *         SkPaint clearPaint(paint);
   *         clearPaint.setColor(SK_ColorWHITE);
   *
   *         canvas->save();
   *         canvas->translate(0, 0);
   *         canvas->clipRect(SkRect::MakeLTRB(0, 0, WIDTH, 256));
   *         draw_text(canvas, blob, paint, blurPaint, clearPaint);
   *         canvas->restore();
   *
   *         canvas->save();
   *         canvas->translate(0, 256);
   *         canvas->clipRect(SkRect::MakeLTRB(0, 256, WIDTH, 510));
   *         draw_text(canvas, blob, paint, blurPaint, clearPaint);
   *         canvas->restore();
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
