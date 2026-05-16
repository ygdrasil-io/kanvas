package org.skia.tests

import kotlin.Array
import kotlin.Int
import kotlin.String
import kotlin.collections.List
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.math.SkISize
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class SharedCornersGM : public GM {
 * public:
 *     SharedCornersGM() { this->setBGColor(ToolUtils::color_to_565(0xFF1A65D7)); }
 *
 * protected:
 *     SkString getName() const override { return SkString("sharedcorners"); }
 *
 *     SkISize getISize() override {
 *         constexpr int numRows = 3 * 2;
 *         constexpr int numCols = (1 + std::size(kJitters)) * 2;
 *         return SkISize::Make(numCols * (kBoxSize + kPadSize) + kPadSize,
 *                              numRows * (kBoxSize + kPadSize) + kPadSize);
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         fFillPaint.setColor(SK_ColorWHITE);
 *         fFillPaint.setAntiAlias(true);
 *
 *         fWireFramePaint = fFillPaint;
 *         fWireFramePaint.setStyle(SkPaint::kStroke_Style);
 *
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(kPadSize, kPadSize);
 *         canvas->save();
 *
 *         // Adjacent rects.
 *         this->drawTriangleBoxes(canvas,
 *                 {{0,  0}, {40,  0}, {80,  0}, {120,  0},
 *                  {0, 20}, {40, 20}, {80, 20}, {120, 20},
 *                           {40, 40}, {80, 40},
 *                           {40, 60}, {80, 60}},
 *                 {{{0, 1, 4}}, {{1, 5, 4}},
 *                  {{5, 1, 6}}, {{1, 2, 6}},
 *                  {{2, 3, 6}}, {{3, 7, 6}},
 *                  {{8, 5, 9}}, {{5, 6, 9}},
 *                  {{10, 8, 11}}, {{8, 9, 11}}});
 *
 *         // Obtuse angles.
 *         this->drawTriangleBoxes(canvas,
 *                 {{ 0, 0}, {10, 0}, {20, 0},
 *                  { 0, 2},          {20, 2},
 *                           {10, 4},
 *                  { 0, 6},          {20, 6},
 *                  { 0, 8}, {10, 8}, {20, 8}},
 *                 {{{3, 1, 4}}, {{4, 5, 3}}, {{6, 5, 7}}, {{7, 9, 6}},
 *                  {{0, 1, 3}}, {{1, 2, 4}},
 *                  {{3, 5, 6}}, {{5, 4, 7}},
 *                  {{6, 9, 8}}, {{9, 7, 10}}});
 *
 *         canvas->restore();
 *         canvas->translate((kBoxSize + kPadSize) * 4, 0);
 *
 *         // Right angles.
 *         this->drawTriangleBoxes(canvas,
 *                 {{0, 0}, {-1, 0}, {0, -1}, {1, 0}, {0, 1}},
 *                 {{{0, 1, 2}}, {{0, 2, 3}}, {{0, 3, 4}}, {{0, 4, 1}}});
 *
 *         // Acute angles.
 *         SkRandom rand;
 *         std::vector<SkPoint> pts;
 *         std::vector<std::array<int, 3>> indices;
 *         SkScalar theta = 0;
 *         pts.push_back({0, 0});
 *         while (theta < 2*SK_ScalarPI) {
 *             pts.push_back({SkScalarCos(theta), SkScalarSin(theta)});
 *             if (pts.size() > 2) {
 *                 indices.push_back({{0, (int)pts.size() - 2, (int)pts.size() - 1}});
 *             }
 *             theta += rand.nextRangeF(0, SK_ScalarPI/3);
 *         }
 *         indices.push_back({{0, (int)pts.size() - 1, 1}});
 *         this->drawTriangleBoxes(canvas, pts, indices);
 *     }
 *
 *     void drawTriangleBoxes(SkCanvas* canvas, const std::vector<SkPoint>& points,
 *                            const std::vector<std::array<int, 3>>& triangles) {
 *         SkPathBuilder builder(SkPathFillType::kEvenOdd);
 *         builder.setIsVolatile(true);
 *         for (const std::array<int, 3>& triangle : triangles) {
 *             builder.moveTo(points[triangle[0]]);
 *             builder.lineTo(points[triangle[1]]);
 *             builder.lineTo(points[triangle[2]]);
 *             builder.close();
 *         }
 *         const SkRect bounds = builder.computeBounds();
 *         SkScalar scale = kBoxSize / std::max(bounds.height(), bounds.width());
 *         builder.transform(SkMatrix::Scale(scale, scale));
 *         SkPath path = builder.detach();
 *
 *         this->drawRow(canvas, path);
 *         canvas->translate(0, kBoxSize + kPadSize);
 *
 *         SkMatrix rot;
 *         rot.setRotate(45, path.getBounds().centerX(), path.getBounds().centerY());
 *         path = path.makeTransform(rot);
 *         this->drawRow(canvas, path);
 *         canvas->translate(0, kBoxSize + kPadSize);
 *
 *         rot.setRotate(-45 - 69.38111f, path.getBounds().centerX(), path.getBounds().centerY());
 *         path = path.makeTransform(rot);
 *         this->drawRow(canvas, path);
 *         canvas->translate(0, kBoxSize + kPadSize);
 *     }
 *
 *     void drawRow(SkCanvas* canvas, const SkPath& path) {
 *         SkAutoCanvasRestore acr(canvas, true);
 *         const SkRect& bounds = path.getBounds();
 *         canvas->translate((kBoxSize - bounds.width()) / 2 - bounds.left(),
 *                           (kBoxSize - bounds.height()) / 2 - bounds.top());
 *
 *         canvas->drawPath(path, fWireFramePaint);
 *         canvas->translate(kBoxSize + kPadSize, 0);
 *
 *         for (SkPoint jitter : kJitters) {
 *             {
 *                 SkAutoCanvasRestore acr2(canvas, true);
 *                 canvas->translate(jitter.x(), jitter.y());
 *                 canvas->drawPath(path, fFillPaint);
 *             }
 *             canvas->translate(kBoxSize + kPadSize, 0);
 *         }
 *     }
 *
 *     SkPaint fWireFramePaint;
 *     SkPaint fFillPaint;
 * }
 * ```
 */
public open class SharedCornersGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkPaint fWireFramePaint
   * ```
   */
  protected var fWireFramePaint: SkPaint = TODO("Initialize fWireFramePaint")

  /**
   * C++ original:
   * ```cpp
   * SkPaint fFillPaint
   * ```
   */
  protected var fFillPaint: SkPaint = TODO("Initialize fFillPaint")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("sharedcorners"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         constexpr int numRows = 3 * 2;
   *         constexpr int numCols = (1 + std::size(kJitters)) * 2;
   *         return SkISize::Make(numCols * (kBoxSize + kPadSize) + kPadSize,
   *                              numRows * (kBoxSize + kPadSize) + kPadSize);
   *     }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fFillPaint.setColor(SK_ColorWHITE);
   *         fFillPaint.setAntiAlias(true);
   *
   *         fWireFramePaint = fFillPaint;
   *         fWireFramePaint.setStyle(SkPaint::kStroke_Style);
   *
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->translate(kPadSize, kPadSize);
   *         canvas->save();
   *
   *         // Adjacent rects.
   *         this->drawTriangleBoxes(canvas,
   *                 {{0,  0}, {40,  0}, {80,  0}, {120,  0},
   *                  {0, 20}, {40, 20}, {80, 20}, {120, 20},
   *                           {40, 40}, {80, 40},
   *                           {40, 60}, {80, 60}},
   *                 {{{0, 1, 4}}, {{1, 5, 4}},
   *                  {{5, 1, 6}}, {{1, 2, 6}},
   *                  {{2, 3, 6}}, {{3, 7, 6}},
   *                  {{8, 5, 9}}, {{5, 6, 9}},
   *                  {{10, 8, 11}}, {{8, 9, 11}}});
   *
   *         // Obtuse angles.
   *         this->drawTriangleBoxes(canvas,
   *                 {{ 0, 0}, {10, 0}, {20, 0},
   *                  { 0, 2},          {20, 2},
   *                           {10, 4},
   *                  { 0, 6},          {20, 6},
   *                  { 0, 8}, {10, 8}, {20, 8}},
   *                 {{{3, 1, 4}}, {{4, 5, 3}}, {{6, 5, 7}}, {{7, 9, 6}},
   *                  {{0, 1, 3}}, {{1, 2, 4}},
   *                  {{3, 5, 6}}, {{5, 4, 7}},
   *                  {{6, 9, 8}}, {{9, 7, 10}}});
   *
   *         canvas->restore();
   *         canvas->translate((kBoxSize + kPadSize) * 4, 0);
   *
   *         // Right angles.
   *         this->drawTriangleBoxes(canvas,
   *                 {{0, 0}, {-1, 0}, {0, -1}, {1, 0}, {0, 1}},
   *                 {{{0, 1, 2}}, {{0, 2, 3}}, {{0, 3, 4}}, {{0, 4, 1}}});
   *
   *         // Acute angles.
   *         SkRandom rand;
   *         std::vector<SkPoint> pts;
   *         std::vector<std::array<int, 3>> indices;
   *         SkScalar theta = 0;
   *         pts.push_back({0, 0});
   *         while (theta < 2*SK_ScalarPI) {
   *             pts.push_back({SkScalarCos(theta), SkScalarSin(theta)});
   *             if (pts.size() > 2) {
   *                 indices.push_back({{0, (int)pts.size() - 2, (int)pts.size() - 1}});
   *             }
   *             theta += rand.nextRangeF(0, SK_ScalarPI/3);
   *         }
   *         indices.push_back({{0, (int)pts.size() - 1, 1}});
   *         this->drawTriangleBoxes(canvas, pts, indices);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawTriangleBoxes(SkCanvas* canvas, const std::vector<SkPoint>& points,
   *                            const std::vector<std::array<int, 3>>& triangles) {
   *         SkPathBuilder builder(SkPathFillType::kEvenOdd);
   *         builder.setIsVolatile(true);
   *         for (const std::array<int, 3>& triangle : triangles) {
   *             builder.moveTo(points[triangle[0]]);
   *             builder.lineTo(points[triangle[1]]);
   *             builder.lineTo(points[triangle[2]]);
   *             builder.close();
   *         }
   *         const SkRect bounds = builder.computeBounds();
   *         SkScalar scale = kBoxSize / std::max(bounds.height(), bounds.width());
   *         builder.transform(SkMatrix::Scale(scale, scale));
   *         SkPath path = builder.detach();
   *
   *         this->drawRow(canvas, path);
   *         canvas->translate(0, kBoxSize + kPadSize);
   *
   *         SkMatrix rot;
   *         rot.setRotate(45, path.getBounds().centerX(), path.getBounds().centerY());
   *         path = path.makeTransform(rot);
   *         this->drawRow(canvas, path);
   *         canvas->translate(0, kBoxSize + kPadSize);
   *
   *         rot.setRotate(-45 - 69.38111f, path.getBounds().centerX(), path.getBounds().centerY());
   *         path = path.makeTransform(rot);
   *         this->drawRow(canvas, path);
   *         canvas->translate(0, kBoxSize + kPadSize);
   *     }
   * ```
   */
  protected fun drawTriangleBoxes(
    canvas: SkCanvas?,
    points: List<SkPoint>,
    triangles: List<Array<Int>>,
  ) {
    TODO("Implement drawTriangleBoxes")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawRow(SkCanvas* canvas, const SkPath& path) {
   *         SkAutoCanvasRestore acr(canvas, true);
   *         const SkRect& bounds = path.getBounds();
   *         canvas->translate((kBoxSize - bounds.width()) / 2 - bounds.left(),
   *                           (kBoxSize - bounds.height()) / 2 - bounds.top());
   *
   *         canvas->drawPath(path, fWireFramePaint);
   *         canvas->translate(kBoxSize + kPadSize, 0);
   *
   *         for (SkPoint jitter : kJitters) {
   *             {
   *                 SkAutoCanvasRestore acr2(canvas, true);
   *                 canvas->translate(jitter.x(), jitter.y());
   *                 canvas->drawPath(path, fFillPaint);
   *             }
   *             canvas->translate(kBoxSize + kPadSize, 0);
   *         }
   *     }
   * ```
   */
  protected fun drawRow(canvas: SkCanvas?, path: SkPath) {
    TODO("Implement drawRow")
  }
}
