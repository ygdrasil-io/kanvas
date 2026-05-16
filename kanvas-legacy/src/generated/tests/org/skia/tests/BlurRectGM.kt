package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.CharArray
import kotlin.String
import kotlin.ULong
import org.skia.core.SkCanvas
import org.skia.foundation.SkAlpha
import org.skia.foundation.SkMaskFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.foundation.U8CPU
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class BlurRectGM : public skiagm::GM {
 * public:
 *     BlurRectGM(const char name[], U8CPU alpha) : fName(name), fAlpha(SkToU8(alpha)) {}
 *
 * private:
 *     sk_sp<SkMaskFilter> fMaskFilters[kLastEnum_SkBlurStyle + 1];
 *     const char* fName;
 *     SkAlpha fAlpha;
 *
 *     void onOnceBeforeDraw() override {
 *         for (int i = 0; i <= kLastEnum_SkBlurStyle; ++i) {
 *             fMaskFilters[i] = SkMaskFilter::MakeBlur((SkBlurStyle)i,
 *                                   SkBlurMask::ConvertRadiusToSigma(SkIntToScalar(STROKE_WIDTH/2)));
 *         }
 *     }
 *
 *     SkString getName() const override { return SkString(fName); }
 *
 *     SkISize getISize() override { return {860, 820}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(STROKE_WIDTH*3/2, STROKE_WIDTH*3/2);
 *
 *         SkRect  r = { 0, 0, 100, 50 };
 *         SkScalar scales[] = { SK_Scalar1, 0.6f };
 *
 *         for (size_t s = 0; s < std::size(scales); ++s) {
 *             canvas->save();
 *             for (size_t f = 0; f < std::size(fMaskFilters); ++f) {
 *                 SkPaint paint;
 *                 paint.setMaskFilter(fMaskFilters[f]);
 *                 paint.setAlpha(fAlpha);
 *
 *                 SkPaint paintWithRadial = paint;
 *                 paintWithRadial.setShader(make_radial());
 *
 *                 constexpr Proc procs[] = {
 *                     fill_rect, draw_donut, draw_donut_skewed
 *                 };
 *
 *                 canvas->save();
 *                 canvas->scale(scales[s], scales[s]);
 *                 this->drawProcs(canvas, r, paint, false, procs, std::size(procs));
 *                 canvas->translate(r.width() * 4/3, 0);
 *                 this->drawProcs(canvas, r, paintWithRadial, false, procs, std::size(procs));
 *                 canvas->translate(r.width() * 4/3, 0);
 *                 this->drawProcs(canvas, r, paint, true, procs, std::size(procs));
 *                 canvas->translate(r.width() * 4/3, 0);
 *                 this->drawProcs(canvas, r, paintWithRadial, true, procs, std::size(procs));
 *                 canvas->restore();
 *
 *                 canvas->translate(0, std::size(procs) * r.height() * 4/3 * scales[s]);
 *             }
 *             canvas->restore();
 *             canvas->translate(4 * r.width() * 4/3 * scales[s], 0);
 *         }
 *     }
 *
 *     void drawProcs(SkCanvas* canvas, const SkRect& r, const SkPaint& paint,
 *                    bool doClip, const Proc procs[], size_t procsCount) {
 *         SkAutoCanvasRestore acr(canvas, true);
 *         for (size_t i = 0; i < procsCount; ++i) {
 *             if (doClip) {
 *                 SkRect clipRect(r);
 *                 clipRect.inset(STROKE_WIDTH/2, STROKE_WIDTH/2);
 *                 canvas->save();
 *                 canvas->clipRect(r);
 *             }
 *             procs[i](canvas, r, paint);
 *             if (doClip) {
 *                 canvas->restore();
 *             }
 *             canvas->translate(0, r.height() * 4/3);
 *         }
 *     }
 * }
 * ```
 */
public open class BlurRectGM public constructor(
  name: CharArray,
  alpha: U8CPU,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkMaskFilter> fMaskFilters[kLastEnum_SkBlurStyle + 1]
   * ```
   */
  private var fMaskFilters: Array<SkSp<SkMaskFilter>> = TODO("Initialize fMaskFilters")

  /**
   * C++ original:
   * ```cpp
   * const char* fName
   * ```
   */
  private val fName: String? = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * SkAlpha fAlpha
   * ```
   */
  private var fAlpha: SkAlpha = TODO("Initialize fAlpha")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         for (int i = 0; i <= kLastEnum_SkBlurStyle; ++i) {
   *             fMaskFilters[i] = SkMaskFilter::MakeBlur((SkBlurStyle)i,
   *                                   SkBlurMask::ConvertRadiusToSigma(SkIntToScalar(STROKE_WIDTH/2)));
   *         }
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString(fName); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {860, 820}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->translate(STROKE_WIDTH*3/2, STROKE_WIDTH*3/2);
   *
   *         SkRect  r = { 0, 0, 100, 50 };
   *         SkScalar scales[] = { SK_Scalar1, 0.6f };
   *
   *         for (size_t s = 0; s < std::size(scales); ++s) {
   *             canvas->save();
   *             for (size_t f = 0; f < std::size(fMaskFilters); ++f) {
   *                 SkPaint paint;
   *                 paint.setMaskFilter(fMaskFilters[f]);
   *                 paint.setAlpha(fAlpha);
   *
   *                 SkPaint paintWithRadial = paint;
   *                 paintWithRadial.setShader(make_radial());
   *
   *                 constexpr Proc procs[] = {
   *                     fill_rect, draw_donut, draw_donut_skewed
   *                 };
   *
   *                 canvas->save();
   *                 canvas->scale(scales[s], scales[s]);
   *                 this->drawProcs(canvas, r, paint, false, procs, std::size(procs));
   *                 canvas->translate(r.width() * 4/3, 0);
   *                 this->drawProcs(canvas, r, paintWithRadial, false, procs, std::size(procs));
   *                 canvas->translate(r.width() * 4/3, 0);
   *                 this->drawProcs(canvas, r, paint, true, procs, std::size(procs));
   *                 canvas->translate(r.width() * 4/3, 0);
   *                 this->drawProcs(canvas, r, paintWithRadial, true, procs, std::size(procs));
   *                 canvas->restore();
   *
   *                 canvas->translate(0, std::size(procs) * r.height() * 4/3 * scales[s]);
   *             }
   *             canvas->restore();
   *             canvas->translate(4 * r.width() * 4/3 * scales[s], 0);
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawProcs(SkCanvas* canvas, const SkRect& r, const SkPaint& paint,
   *                    bool doClip, const Proc procs[], size_t procsCount) {
   *         SkAutoCanvasRestore acr(canvas, true);
   *         for (size_t i = 0; i < procsCount; ++i) {
   *             if (doClip) {
   *                 SkRect clipRect(r);
   *                 clipRect.inset(STROKE_WIDTH/2, STROKE_WIDTH/2);
   *                 canvas->save();
   *                 canvas->clipRect(r);
   *             }
   *             procs[i](canvas, r, paint);
   *             if (doClip) {
   *                 canvas->restore();
   *             }
   *             canvas->translate(0, r.height() * 4/3);
   *         }
   *     }
   * ```
   */
  private fun drawProcs(
    canvas: SkCanvas?,
    r: SkRect,
    paint: SkPaint,
    doClip: Boolean,
    procs: Array<Proc>,
    procsCount: ULong,
  ) {
    TODO("Implement drawProcs")
  }
}
