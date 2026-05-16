package org.skia.tests

import kotlin.Array
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPath
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ZeroLenStrokesGM : public skiagm::GM {
 *     SkPath fMoveHfPath, fMoveZfPath, fDashedfPath, fRefPath[4];
 *     SkPath fCubicPath, fQuadPath, fLinePath;
 * protected:
 *     void onOnceBeforeDraw() override {
 *         auto parse_assert_result = [](const char str[]) {
 *             auto path = SkParsePath::FromSVGString(str);
 *             SkAssertResult(path.has_value());
 *             return *path;
 *         };
 *
 *         fMoveHfPath = parse_assert_result("M0,0h0M10,0h0M20,0h0");
 *         fMoveZfPath = parse_assert_result("M0,0zM10,0zM20,0z");
 *         fDashedfPath = parse_assert_result("M0,0h25");
 *         fCubicPath = parse_assert_result("M 0 0 C 0 0 0 0 0 0");
 *         fQuadPath = parse_assert_result("M 0 0 Q 0 0 0 0");
 *         fLinePath = parse_assert_result("M 0 0 L 0 0");
 *
 *         SkPathBuilder builders[4];
 *         for (int i = 0; i < 3; ++i) {
 *             builders[0].addCircle(i * 10.f, 0, 5);
 *             builders[1].addCircle(i * 10.f, 0, 10);
 *             builders[2].addRect({i * 10.f - 4, -2, i * 10.f + 4, 6});
 *             builders[3].addRect({i * 10.f - 10, -10, i * 10.f + 10, 10});
 *         }
 *         for (int i = 0; i < 4; ++i) {
 *             fRefPath[i] = builders[i].detach();
 *         }
 *     }
 *
 *     SkString getName() const override { return SkString("zeroPath"); }
 *
 *     SkISize getISize() override { return SkISize::Make(W, H * 2); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint fillPaint, strokePaint, dashPaint;
 *         fillPaint.setAntiAlias(true);
 *         strokePaint = fillPaint;
 *         strokePaint.setStyle(SkPaint::kStroke_Style);
 *         for (int i = 0; i < 2; ++i) {
 *             fillPaint.setAlphaf(1.0f);
 *             strokePaint.setAlphaf(1.0f);
 *             strokePaint.setStrokeWidth(i ? 8.f : 10.f);
 *             strokePaint.setStrokeCap(i ? SkPaint::kSquare_Cap : SkPaint::kRound_Cap);
 *             canvas->save();
 *             canvas->translate(10 + i * 100.f, 10);
 *             canvas->drawPath(fMoveHfPath, strokePaint);
 *             canvas->translate(0, 20);
 *             canvas->drawPath(fMoveZfPath, strokePaint);
 *             dashPaint = strokePaint;
 *             const SkScalar intervals[] = { 0, 10 };
 *             dashPaint.setPathEffect(SkDashPathEffect::Make(intervals, 0));
 *             (void)skpathutils::FillPathWithPaint(fDashedfPath, dashPaint);
 *             canvas->translate(0, 20);
 *             canvas->drawPath(fDashedfPath, dashPaint);
 *             canvas->translate(0, 20);
 *             canvas->drawPath(fRefPath[i * 2], fillPaint);
 *             strokePaint.setStrokeWidth(20);
 *             strokePaint.setAlphaf(0.5f);
 *             canvas->translate(0, 50);
 *             canvas->drawPath(fMoveHfPath, strokePaint);
 *             canvas->translate(0, 30);
 *             canvas->drawPath(fMoveZfPath, strokePaint);
 *             canvas->translate(0, 30);
 *             fillPaint.setAlphaf(0.5f);
 *             canvas->drawPath(fRefPath[1 + i * 2], fillPaint);
 *             canvas->translate(0, 30);
 *             canvas->drawPath(fCubicPath, strokePaint);
 *             canvas->translate(0, 30);
 *             canvas->drawPath(fQuadPath, strokePaint);
 *             canvas->translate(0, 30);
 *             canvas->drawPath(fLinePath, strokePaint);
 *             canvas->restore();
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class ZeroLenStrokesGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkPath fMoveHfPath
   * ```
   */
  private var fMoveHfPath: SkPath = TODO("Initialize fMoveHfPath")

  /**
   * C++ original:
   * ```cpp
   * SkPath fMoveHfPath, fMoveZfPath
   * ```
   */
  private var fMoveZfPath: SkPath = TODO("Initialize fMoveZfPath")

  /**
   * C++ original:
   * ```cpp
   * SkPath fMoveHfPath, fMoveZfPath, fDashedfPath
   * ```
   */
  private var fDashedfPath: SkPath = TODO("Initialize fDashedfPath")

  /**
   * C++ original:
   * ```cpp
   * SkPath fMoveHfPath, fMoveZfPath, fDashedfPath, fRefPath[4]
   * ```
   */
  private var fRefPath: Array<SkPath> = TODO("Initialize fRefPath")

  /**
   * C++ original:
   * ```cpp
   * SkPath fCubicPath
   * ```
   */
  private var fCubicPath: SkPath = TODO("Initialize fCubicPath")

  /**
   * C++ original:
   * ```cpp
   * SkPath fCubicPath, fQuadPath
   * ```
   */
  private var fQuadPath: SkPath = TODO("Initialize fQuadPath")

  /**
   * C++ original:
   * ```cpp
   * SkPath fCubicPath, fQuadPath, fLinePath
   * ```
   */
  private var fLinePath: SkPath = TODO("Initialize fLinePath")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         auto parse_assert_result = [](const char str[]) {
   *             auto path = SkParsePath::FromSVGString(str);
   *             SkAssertResult(path.has_value());
   *             return *path;
   *         };
   *
   *         fMoveHfPath = parse_assert_result("M0,0h0M10,0h0M20,0h0");
   *         fMoveZfPath = parse_assert_result("M0,0zM10,0zM20,0z");
   *         fDashedfPath = parse_assert_result("M0,0h25");
   *         fCubicPath = parse_assert_result("M 0 0 C 0 0 0 0 0 0");
   *         fQuadPath = parse_assert_result("M 0 0 Q 0 0 0 0");
   *         fLinePath = parse_assert_result("M 0 0 L 0 0");
   *
   *         SkPathBuilder builders[4];
   *         for (int i = 0; i < 3; ++i) {
   *             builders[0].addCircle(i * 10.f, 0, 5);
   *             builders[1].addCircle(i * 10.f, 0, 10);
   *             builders[2].addRect({i * 10.f - 4, -2, i * 10.f + 4, 6});
   *             builders[3].addRect({i * 10.f - 10, -10, i * 10.f + 10, 10});
   *         }
   *         for (int i = 0; i < 4; ++i) {
   *             fRefPath[i] = builders[i].detach();
   *         }
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("zeroPath"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(W, H * 2); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint fillPaint, strokePaint, dashPaint;
   *         fillPaint.setAntiAlias(true);
   *         strokePaint = fillPaint;
   *         strokePaint.setStyle(SkPaint::kStroke_Style);
   *         for (int i = 0; i < 2; ++i) {
   *             fillPaint.setAlphaf(1.0f);
   *             strokePaint.setAlphaf(1.0f);
   *             strokePaint.setStrokeWidth(i ? 8.f : 10.f);
   *             strokePaint.setStrokeCap(i ? SkPaint::kSquare_Cap : SkPaint::kRound_Cap);
   *             canvas->save();
   *             canvas->translate(10 + i * 100.f, 10);
   *             canvas->drawPath(fMoveHfPath, strokePaint);
   *             canvas->translate(0, 20);
   *             canvas->drawPath(fMoveZfPath, strokePaint);
   *             dashPaint = strokePaint;
   *             const SkScalar intervals[] = { 0, 10 };
   *             dashPaint.setPathEffect(SkDashPathEffect::Make(intervals, 0));
   *             (void)skpathutils::FillPathWithPaint(fDashedfPath, dashPaint);
   *             canvas->translate(0, 20);
   *             canvas->drawPath(fDashedfPath, dashPaint);
   *             canvas->translate(0, 20);
   *             canvas->drawPath(fRefPath[i * 2], fillPaint);
   *             strokePaint.setStrokeWidth(20);
   *             strokePaint.setAlphaf(0.5f);
   *             canvas->translate(0, 50);
   *             canvas->drawPath(fMoveHfPath, strokePaint);
   *             canvas->translate(0, 30);
   *             canvas->drawPath(fMoveZfPath, strokePaint);
   *             canvas->translate(0, 30);
   *             fillPaint.setAlphaf(0.5f);
   *             canvas->drawPath(fRefPath[1 + i * 2], fillPaint);
   *             canvas->translate(0, 30);
   *             canvas->drawPath(fCubicPath, strokePaint);
   *             canvas->translate(0, 30);
   *             canvas->drawPath(fQuadPath, strokePaint);
   *             canvas->translate(0, 30);
   *             canvas->drawPath(fLinePath, strokePaint);
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
