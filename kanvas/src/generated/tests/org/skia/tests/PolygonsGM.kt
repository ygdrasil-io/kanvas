package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRandom

/**
 * C++ original:
 * ```cpp
 * class PolygonsGM: public GM {
 * public:
 *     PolygonsGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("polygons"); }
 *
 *     SkISize getISize() override {
 *         int width = kNumPolygons * kCellSize + 40;
 *         int height = (kNumJoins * kNumStrokeWidths + kNumExtraStyles) * kCellSize + 40;
 *         return SkISize::Make(width, height);
 *     }
 *
 *     // Construct all polygons
 *     void onOnceBeforeDraw() override {
 *         SkPoint p0[] = {{0, 0}, {60, 0}, {90, 40}};  // triangle
 *         SkPoint p1[] = {{0, 0}, {0, 40}, {60, 40}, {40, 0}};  // trapezoid
 *         SkPoint p2[] = {{0, 0}, {40, 40}, {80, 40}, {40, 0}};  // diamond
 *         SkPoint p3[] = {{10, 0}, {50, 0}, {60, 10}, {60, 30}, {50, 40},
 *                         {10, 40}, {0, 30}, {0, 10}};  // octagon
 *         SkPoint p4[32];  // circle-like polygons with 32-edges.
 *         SkPoint p5[] = {{0, 0}, {20, 20}, {0, 40}, {60, 20}};  // concave polygon with 4 edges
 *         SkPoint p6[] = {{0, 40}, {0, 30}, {15, 30}, {15, 20}, {30, 20},
 *                         {30, 10}, {45, 10}, {45, 0}, {60, 0}, {60, 40}};  // stairs-like polygon
 *         SkPoint p7[] = {{0, 20}, {20, 20}, {30, 0}, {40, 20}, {60, 20},
 *                         {45, 30}, {55, 50}, {30, 40}, {5, 50}, {15, 30}};  // five-point stars
 *
 *         for (size_t i = 0; i < std::size(p4); ++i) {
 *             SkScalar angle = 2 * SK_ScalarPI * i / std::size(p4);
 *             p4[i].set(20 * SkScalarCos(angle) + 20, 20 * SkScalarSin(angle) + 20);
 *         }
 *
 *         struct Polygons {
 *             SkPoint* fPoints;
 *             size_t fPointNum;
 *         } pgs[] = {
 *             { p0, std::size(p0) },
 *             { p1, std::size(p1) },
 *             { p2, std::size(p2) },
 *             { p3, std::size(p3) },
 *             { p4, std::size(p4) },
 *             { p5, std::size(p5) },
 *             { p6, std::size(p6) },
 *             { p7, std::size(p7) }
 *         };
 *
 *         SkASSERT(std::size(pgs) == kNumPolygons);
 *         for (size_t pgIndex = 0; pgIndex < std::size(pgs); ++pgIndex) {
 *             SkPathBuilder b;
 *             b.moveTo(pgs[pgIndex].fPoints[0].fX,
 *                      pgs[pgIndex].fPoints[0].fY);
 *             for (size_t ptIndex = 1; ptIndex < pgs[pgIndex].fPointNum; ++ptIndex) {
 *                 b.lineTo(pgs[pgIndex].fPoints[ptIndex].fX,
 *                          pgs[pgIndex].fPoints[ptIndex].fY);
 *             }
 *             b.close();
 *             fPolygons.push_back(b.detach());
 *         }
 *     }
 *
 *     // Set the location for the current test on the canvas
 *     static void SetLocation(SkCanvas* canvas, int counter, int lineNum) {
 *         SkScalar x = SK_Scalar1 * kCellSize * (counter % lineNum) + 30 + SK_Scalar1 / 4;
 *         SkScalar y = SK_Scalar1 * kCellSize * (counter / lineNum) + 30 + 3 * SK_Scalar1 / 4;
 *         canvas->translate(x, y);
 *     }
 *
 *     static void SetColorAndAlpha(SkPaint* paint, SkRandom* rand) {
 *         SkColor color = rand->nextU();
 *         color |= 0xff000000;
 *         paint->setColor(color);
 *         if (40 == paint->getStrokeWidth()) {
 *             paint->setAlpha(0xA0);
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         // Stroke widths are:
 *         // 0(may use hairline rendering), 10(common case for stroke-style)
 *         // 40(>= geometry width/height, make the contour filled in fact)
 *         constexpr int kStrokeWidths[] = {0, 10, 40};
 *         SkASSERT(kNumStrokeWidths == std::size(kStrokeWidths));
 *
 *         constexpr SkPaint::Join kJoins[] = {
 *             SkPaint::kMiter_Join, SkPaint::kRound_Join, SkPaint::kBevel_Join
 *         };
 *         SkASSERT(kNumJoins == std::size(kJoins));
 *
 *         int counter = 0;
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *
 *         SkRandom rand;
 *         // For stroke style painter
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         for (int join = 0; join < kNumJoins; ++join) {
 *             for (int width = 0; width < kNumStrokeWidths; ++width) {
 *                 for (int i = 0; i < fPolygons.size(); ++i) {
 *                     canvas->save();
 *                     SetLocation(canvas, counter, fPolygons.size());
 *
 *                     SetColorAndAlpha(&paint, &rand);
 *                     paint.setStrokeJoin(kJoins[join]);
 *                     paint.setStrokeWidth(SkIntToScalar(kStrokeWidths[width]));
 *
 *                     canvas->drawPath(fPolygons[i], paint);
 *                     canvas->restore();
 *                     ++counter;
 *                 }
 *             }
 *         }
 *
 *         // For stroke-and-fill style painter and fill style painter
 *         constexpr SkPaint::Style kStyles[] = {
 *             SkPaint::kStrokeAndFill_Style, SkPaint::kFill_Style
 *         };
 *         SkASSERT(kNumExtraStyles == std::size(kStyles));
 *
 *         paint.setStrokeJoin(SkPaint::kMiter_Join);
 *         paint.setStrokeWidth(SkIntToScalar(20));
 *         for (int style = 0; style < kNumExtraStyles; ++style) {
 *             paint.setStyle(kStyles[style]);
 *             for (int i = 0; i < fPolygons.size(); ++i) {
 *                 canvas->save();
 *                 SetLocation(canvas, counter, fPolygons.size());
 *                 SetColorAndAlpha(&paint, &rand);
 *                 canvas->drawPath(fPolygons[i], paint);
 *                 canvas->restore();
 *                 ++counter;
 *             }
 *         }
 *     }
 *
 * private:
 *     inline static constexpr int kNumPolygons = 8;
 *     inline static constexpr int kCellSize = 100;
 *     inline static constexpr int kNumExtraStyles = 2;
 *     inline static constexpr int kNumStrokeWidths = 3;
 *     inline static constexpr int kNumJoins = 3;
 *
 *     TArray<SkPath> fPolygons;
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class PolygonsGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kNumPolygons = 8
   * ```
   */
  private var fPolygons: Int = TODO("Initialize fPolygons")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("polygons"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         int width = kNumPolygons * kCellSize + 40;
   *         int height = (kNumJoins * kNumStrokeWidths + kNumExtraStyles) * kCellSize + 40;
   *         return SkISize::Make(width, height);
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
   *         SkPoint p0[] = {{0, 0}, {60, 0}, {90, 40}};  // triangle
   *         SkPoint p1[] = {{0, 0}, {0, 40}, {60, 40}, {40, 0}};  // trapezoid
   *         SkPoint p2[] = {{0, 0}, {40, 40}, {80, 40}, {40, 0}};  // diamond
   *         SkPoint p3[] = {{10, 0}, {50, 0}, {60, 10}, {60, 30}, {50, 40},
   *                         {10, 40}, {0, 30}, {0, 10}};  // octagon
   *         SkPoint p4[32];  // circle-like polygons with 32-edges.
   *         SkPoint p5[] = {{0, 0}, {20, 20}, {0, 40}, {60, 20}};  // concave polygon with 4 edges
   *         SkPoint p6[] = {{0, 40}, {0, 30}, {15, 30}, {15, 20}, {30, 20},
   *                         {30, 10}, {45, 10}, {45, 0}, {60, 0}, {60, 40}};  // stairs-like polygon
   *         SkPoint p7[] = {{0, 20}, {20, 20}, {30, 0}, {40, 20}, {60, 20},
   *                         {45, 30}, {55, 50}, {30, 40}, {5, 50}, {15, 30}};  // five-point stars
   *
   *         for (size_t i = 0; i < std::size(p4); ++i) {
   *             SkScalar angle = 2 * SK_ScalarPI * i / std::size(p4);
   *             p4[i].set(20 * SkScalarCos(angle) + 20, 20 * SkScalarSin(angle) + 20);
   *         }
   *
   *         struct Polygons {
   *             SkPoint* fPoints;
   *             size_t fPointNum;
   *         } pgs[] = {
   *             { p0, std::size(p0) },
   *             { p1, std::size(p1) },
   *             { p2, std::size(p2) },
   *             { p3, std::size(p3) },
   *             { p4, std::size(p4) },
   *             { p5, std::size(p5) },
   *             { p6, std::size(p6) },
   *             { p7, std::size(p7) }
   *         };
   *
   *         SkASSERT(std::size(pgs) == kNumPolygons);
   *         for (size_t pgIndex = 0; pgIndex < std::size(pgs); ++pgIndex) {
   *             SkPathBuilder b;
   *             b.moveTo(pgs[pgIndex].fPoints[0].fX,
   *                      pgs[pgIndex].fPoints[0].fY);
   *             for (size_t ptIndex = 1; ptIndex < pgs[pgIndex].fPointNum; ++ptIndex) {
   *                 b.lineTo(pgs[pgIndex].fPoints[ptIndex].fX,
   *                          pgs[pgIndex].fPoints[ptIndex].fY);
   *             }
   *             b.close();
   *             fPolygons.push_back(b.detach());
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
   * void onDraw(SkCanvas* canvas) override {
   *         // Stroke widths are:
   *         // 0(may use hairline rendering), 10(common case for stroke-style)
   *         // 40(>= geometry width/height, make the contour filled in fact)
   *         constexpr int kStrokeWidths[] = {0, 10, 40};
   *         SkASSERT(kNumStrokeWidths == std::size(kStrokeWidths));
   *
   *         constexpr SkPaint::Join kJoins[] = {
   *             SkPaint::kMiter_Join, SkPaint::kRound_Join, SkPaint::kBevel_Join
   *         };
   *         SkASSERT(kNumJoins == std::size(kJoins));
   *
   *         int counter = 0;
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *
   *         SkRandom rand;
   *         // For stroke style painter
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         for (int join = 0; join < kNumJoins; ++join) {
   *             for (int width = 0; width < kNumStrokeWidths; ++width) {
   *                 for (int i = 0; i < fPolygons.size(); ++i) {
   *                     canvas->save();
   *                     SetLocation(canvas, counter, fPolygons.size());
   *
   *                     SetColorAndAlpha(&paint, &rand);
   *                     paint.setStrokeJoin(kJoins[join]);
   *                     paint.setStrokeWidth(SkIntToScalar(kStrokeWidths[width]));
   *
   *                     canvas->drawPath(fPolygons[i], paint);
   *                     canvas->restore();
   *                     ++counter;
   *                 }
   *             }
   *         }
   *
   *         // For stroke-and-fill style painter and fill style painter
   *         constexpr SkPaint::Style kStyles[] = {
   *             SkPaint::kStrokeAndFill_Style, SkPaint::kFill_Style
   *         };
   *         SkASSERT(kNumExtraStyles == std::size(kStyles));
   *
   *         paint.setStrokeJoin(SkPaint::kMiter_Join);
   *         paint.setStrokeWidth(SkIntToScalar(20));
   *         for (int style = 0; style < kNumExtraStyles; ++style) {
   *             paint.setStyle(kStyles[style]);
   *             for (int i = 0; i < fPolygons.size(); ++i) {
   *                 canvas->save();
   *                 SetLocation(canvas, counter, fPolygons.size());
   *                 SetColorAndAlpha(&paint, &rand);
   *                 canvas->drawPath(fPolygons[i], paint);
   *                 canvas->restore();
   *                 ++counter;
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kNumPolygons: Int = TODO("Initialize kNumPolygons")

    private val kCellSize: Int = TODO("Initialize kCellSize")

    private val kNumExtraStyles: Int = TODO("Initialize kNumExtraStyles")

    private val kNumStrokeWidths: Int = TODO("Initialize kNumStrokeWidths")

    private val kNumJoins: Int = TODO("Initialize kNumJoins")

    /**
     * C++ original:
     * ```cpp
     * static void SetLocation(SkCanvas* canvas, int counter, int lineNum) {
     *         SkScalar x = SK_Scalar1 * kCellSize * (counter % lineNum) + 30 + SK_Scalar1 / 4;
     *         SkScalar y = SK_Scalar1 * kCellSize * (counter / lineNum) + 30 + 3 * SK_Scalar1 / 4;
     *         canvas->translate(x, y);
     *     }
     * ```
     */
    protected fun setLocation(
      canvas: SkCanvas?,
      counter: Int,
      lineNum: Int,
    ) {
      TODO("Implement setLocation")
    }

    /**
     * C++ original:
     * ```cpp
     * static void SetColorAndAlpha(SkPaint* paint, SkRandom* rand) {
     *         SkColor color = rand->nextU();
     *         color |= 0xff000000;
     *         paint->setColor(color);
     *         if (40 == paint->getStrokeWidth()) {
     *             paint->setAlpha(0xA0);
     *         }
     *     }
     * ```
     */
    protected fun setColorAndAlpha(paint: SkPaint?, rand: SkRandom?) {
      TODO("Implement setColorAndAlpha")
    }
  }
}
