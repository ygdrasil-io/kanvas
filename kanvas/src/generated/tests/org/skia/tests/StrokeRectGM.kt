package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class StrokeRectGM : public skiagm::GM {
 * public:
 *     StrokeRectGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("strokerect"); }
 *
 *     SkISize getISize() override { return SkISize::Make(1400, 740); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->drawColor(SK_ColorWHITE);
 *         canvas->translate(STROKE_WIDTH*3/2, STROKE_WIDTH*3/2);
 *
 *         SkPaint paint;
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         paint.setStrokeWidth(STROKE_WIDTH);
 *
 *         constexpr SkPaint::Join gJoins[] = {
 *             SkPaint::kMiter_Join, SkPaint::kRound_Join, SkPaint::kBevel_Join
 *         };
 *
 *         constexpr SkScalar W = 80;
 *         constexpr SkScalar H = 80;
 *         constexpr SkRect gRects[] = {
 *             { 0, 0, W, H },
 *             { W, 0, 0, H },
 *             { 0, H, W, 0 },
 *             { 0, 0, STROKE_WIDTH, H },
 *             { 0, 0, W, STROKE_WIDTH },
 *             { 0, 0, STROKE_WIDTH/2, STROKE_WIDTH/2 },
 *             { 0, 0, W, 0 },
 *             { 0, 0, 0, H },
 *             { 0, 0, 0, 0 },
 *             { 0, 0, W, FLT_EPSILON },
 *             { 0, 0, FLT_EPSILON, H },
 *             { 0, 0, FLT_EPSILON, FLT_EPSILON },
 *         };
 *
 *         for (int doFill = 0; doFill <= 1; ++doFill) {
 *             for (size_t i = 0; i < std::size(gJoins); ++i) {
 *                 SkPaint::Join join = gJoins[i];
 *                 paint.setStrokeJoin(join);
 *
 *                 SkAutoCanvasRestore acr(canvas, true);
 *                 for (size_t j = 0; j < std::size(gRects); ++j) {
 *                     const SkRect& r = gRects[j];
 *
 *                     SkPath fillPath = skpathutils::FillPathWithPaint(SkPath::Rect(r), paint);
 *                     draw_path(canvas, fillPath, r, join, doFill);
 *
 *                     canvas->translate(W + 2 * STROKE_WIDTH, 0);
 *                 }
 *                 acr.restore();
 *                 canvas->translate(0, H + 2 * STROKE_WIDTH);
 *             }
 *             paint.setStyle(SkPaint::kStrokeAndFill_Style);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class StrokeRectGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("strokerect"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1400, 740); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->drawColor(SK_ColorWHITE);
   *         canvas->translate(STROKE_WIDTH*3/2, STROKE_WIDTH*3/2);
   *
   *         SkPaint paint;
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setStrokeWidth(STROKE_WIDTH);
   *
   *         constexpr SkPaint::Join gJoins[] = {
   *             SkPaint::kMiter_Join, SkPaint::kRound_Join, SkPaint::kBevel_Join
   *         };
   *
   *         constexpr SkScalar W = 80;
   *         constexpr SkScalar H = 80;
   *         constexpr SkRect gRects[] = {
   *             { 0, 0, W, H },
   *             { W, 0, 0, H },
   *             { 0, H, W, 0 },
   *             { 0, 0, STROKE_WIDTH, H },
   *             { 0, 0, W, STROKE_WIDTH },
   *             { 0, 0, STROKE_WIDTH/2, STROKE_WIDTH/2 },
   *             { 0, 0, W, 0 },
   *             { 0, 0, 0, H },
   *             { 0, 0, 0, 0 },
   *             { 0, 0, W, FLT_EPSILON },
   *             { 0, 0, FLT_EPSILON, H },
   *             { 0, 0, FLT_EPSILON, FLT_EPSILON },
   *         };
   *
   *         for (int doFill = 0; doFill <= 1; ++doFill) {
   *             for (size_t i = 0; i < std::size(gJoins); ++i) {
   *                 SkPaint::Join join = gJoins[i];
   *                 paint.setStrokeJoin(join);
   *
   *                 SkAutoCanvasRestore acr(canvas, true);
   *                 for (size_t j = 0; j < std::size(gRects); ++j) {
   *                     const SkRect& r = gRects[j];
   *
   *                     SkPath fillPath = skpathutils::FillPathWithPaint(SkPath::Rect(r), paint);
   *                     draw_path(canvas, fillPath, r, join, doFill);
   *
   *                     canvas->translate(W + 2 * STROKE_WIDTH, 0);
   *                 }
   *                 acr.restore();
   *                 canvas->translate(0, H + 2 * STROKE_WIDTH);
   *             }
   *             paint.setStyle(SkPaint::kStrokeAndFill_Style);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
