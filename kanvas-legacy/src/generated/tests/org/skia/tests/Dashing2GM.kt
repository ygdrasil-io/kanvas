package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class Dashing2GM : public skiagm::GM {
 *     SkString getName() const override { return SkString("dashing2"); }
 *
 *     SkISize getISize() override { return {640, 480}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         constexpr int gIntervals[] = {
 *             3,  // 3 dashes: each count [0] followed by intervals [1..count]
 *             2,  10, 10,
 *             4,  20, 5, 5, 5,
 *             2,  2, 2
 *         };
 *
 *         SkPath (*gProc[])(const SkRect&) = {
 *             make_path_line, make_path_rect, make_path_oval, make_path_star,
 *         };
 *
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setStroke(true);
 *         paint.setStrokeWidth(SkIntToScalar(6));
 *
 *         SkRect bounds = SkRect::MakeWH(SkIntToScalar(120), SkIntToScalar(120));
 *         bounds.offset(SkIntToScalar(20), SkIntToScalar(20));
 *         SkScalar dx = bounds.width() * 4 / 3;
 *         SkScalar dy = bounds.height() * 4 / 3;
 *
 *         const int* intervals = &gIntervals[1];
 *         for (int y = 0; y < gIntervals[0]; ++y) {
 *             SkScalar vals[std::size(gIntervals)];  // more than enough
 *             int count = *intervals++;
 *             for (int i = 0; i < count; ++i) {
 *                 vals[i] = SkIntToScalar(*intervals++);
 *             }
 *             SkScalar phase = vals[0] / 2;
 *             paint.setPathEffect(SkDashPathEffect::Make({vals, (size_t)count}, phase));
 *
 *             for (size_t x = 0; x < std::size(gProc); ++x) {
 *                 SkPath path;
 *                 SkRect r = bounds;
 *                 r.offset(x * dx, y * dy);
 *                 canvas->drawPath(gProc[x](r), paint);
 *             }
 *         }
 *     }
 * }
 * ```
 */
public open class Dashing2GM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("dashing2"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {640, 480}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         constexpr int gIntervals[] = {
   *             3,  // 3 dashes: each count [0] followed by intervals [1..count]
   *             2,  10, 10,
   *             4,  20, 5, 5, 5,
   *             2,  2, 2
   *         };
   *
   *         SkPath (*gProc[])(const SkRect&) = {
   *             make_path_line, make_path_rect, make_path_oval, make_path_star,
   *         };
   *
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setStroke(true);
   *         paint.setStrokeWidth(SkIntToScalar(6));
   *
   *         SkRect bounds = SkRect::MakeWH(SkIntToScalar(120), SkIntToScalar(120));
   *         bounds.offset(SkIntToScalar(20), SkIntToScalar(20));
   *         SkScalar dx = bounds.width() * 4 / 3;
   *         SkScalar dy = bounds.height() * 4 / 3;
   *
   *         const int* intervals = &gIntervals[1];
   *         for (int y = 0; y < gIntervals[0]; ++y) {
   *             SkScalar vals[std::size(gIntervals)];  // more than enough
   *             int count = *intervals++;
   *             for (int i = 0; i < count; ++i) {
   *                 vals[i] = SkIntToScalar(*intervals++);
   *             }
   *             SkScalar phase = vals[0] / 2;
   *             paint.setPathEffect(SkDashPathEffect::Make({vals, (size_t)count}, phase));
   *
   *             for (size_t x = 0; x < std::size(gProc); ++x) {
   *                 SkPath path;
   *                 SkRect r = bounds;
   *                 r.offset(x * dx, y * dy);
   *                 canvas->drawPath(gProc[x](r), paint);
   *             }
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
