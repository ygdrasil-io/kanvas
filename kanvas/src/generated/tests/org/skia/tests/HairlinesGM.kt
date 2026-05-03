package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class HairlinesGM : public GM {
 * protected:
 *     SkString getName() const override { return SkString("hairlines"); }
 *
 *     SkISize getISize() override { return SkISize::Make(1250, 1250); }
 *
 *     void onOnceBeforeDraw() override {
 *         {
 *             SkPathBuilder lineAngles;
 *             constexpr int kNumAngles = 15;
 *             constexpr int kRadius = 40;
 *
 *             for (int i = 0; i < kNumAngles; ++i) {
 *                 SkScalar angle = SK_ScalarPI * SkIntToScalar(i) / kNumAngles;
 *                 SkScalar x = kRadius * SkScalarCos(angle);
 *                 SkScalar y = kRadius * SkScalarSin(angle);
 *                 lineAngles.moveTo(x, y).lineTo(-x, -y);
 *             }
 *             fPaths.push_back(lineAngles.detach());
 *         }
 *
 *         fPaths.push_back(SkPathBuilder().moveTo(0, -10)
 *                                         .quadTo(100, 100, -10, 0)
 *                                         .detach());
 *
 *         fPaths.push_back(SkPathBuilder().moveTo(0, -5)
 *                                         .quadTo(100, 100, -5, 0)
 *                                         .detach());
 *
 *         fPaths.push_back(SkPathBuilder().moveTo(0, -2)
 *                                         .quadTo(100, 100, -2, 0)
 *                                         .detach());
 *
 *         fPaths.push_back(SkPathBuilder().moveTo(0, -1)
 *                                         .quadTo(100, 100, -2 + 306.0f / 4, 75)
 *                                         .detach());
 *
 *         fPaths.push_back(SkPathBuilder().moveTo(0, -1)
 *                                         .quadTo(100, 100, -1, 0)
 *                                         .detach());
 *
 *         fPaths.push_back(SkPathBuilder().moveTo(0, -0)
 *                                         .quadTo(100, 100, 0, 0)
 *                                         .detach());
 *
 *         fPaths.push_back(SkPathBuilder().moveTo(0, -0)
 *                                         .quadTo(100, 100, 75, 75)
 *                                         .detach());
 *
 *         // Two problem cases for gpu hairline renderer found by shapeops testing. These used
 *         // to assert that the computed bounding box didn't contain all the vertices.
 *
 *         fPaths.push_back(SkPathBuilder().moveTo(4, 6)
 *                                         .cubicTo(5, 6, 5, 4, 4, 0)
 *                                         .close()
 *                                         .detach());
 *
 *         fPaths.push_back(SkPathBuilder().moveTo(5, 1)
 *                                         .lineTo( 4.32787323f, 1.67212653f)
 *                                         .cubicTo(2.75223875f, 3.24776125f,
 *                                                  3.00581908f, 4.51236057f,
 *                                                  3.7580452f,  4.37367964f)
 *                                         .cubicTo(4.66472578f, 3.888381f,
 *                                                  5.f,         2.875f,
 *                                                  5.f,         1.f)
 *                                         .close()
 *                                         .detach());
 *
 *         // Three paths that show the same bug (missing end caps)
 *
 *         fPaths.push_back(SkPathBuilder().moveTo(6.5f,5.5f)
 *                                         .lineTo(3.5f,0.5f)
 *                                         .moveTo(0.5f,5.5f)
 *                                         .lineTo(3.5f,0.5f)
 *                                         .detach());
 *
 *         // An X (crbug.com/137317)
 *         fPaths.push_back(SkPathBuilder().moveTo(1, 1)
 *                                         .lineTo(6, 6)
 *                                         .moveTo(1, 6)
 *                                         .lineTo(6, 1)
 *                                         .detach());
 *
 *         // A right angle (crbug.com/137465 and crbug.com/256776)
 *         fPaths.push_back(SkPathBuilder().moveTo(5.5f, 5.5f)
 *                                         .lineTo(5.5f, 0.5f)
 *                                         .lineTo(0.5f, 0.5f)
 *                                         .detach());
 *
 *         {
 *             // Arc example to test imperfect truncation bug (crbug.com/295626)
 *             constexpr SkScalar kRad = SkIntToScalar(2000);
 *             constexpr SkScalar kStartAngle = 262.59717f;
 *             constexpr SkScalar kSweepAngle = SkScalarHalf(17.188717f);
 *
 *             SkPathBuilder bug;
 *
 *             // Add a circular arc
 *             SkRect circle = SkRect::MakeLTRB(-kRad, -kRad, kRad, kRad);
 *             bug.addArc(circle, kStartAngle, kSweepAngle);
 *
 *             // Now add the chord that should cap the circular arc
 *             SkPoint p0 = { kRad * SkScalarCos(SkDegreesToRadians(kStartAngle)),
 *                            kRad * SkScalarSin(SkDegreesToRadians(kStartAngle)) };
 *
 *             SkPoint p1 = { kRad * SkScalarCos(SkDegreesToRadians(kStartAngle + kSweepAngle)),
 *                            kRad * SkScalarSin(SkDegreesToRadians(kStartAngle + kSweepAngle)) };
 *
 *             bug.moveTo(p0);
 *             bug.lineTo(p1);
 *             fPaths.push_back(bug.detach());
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         constexpr SkAlpha kAlphaValue[] = { 0xFF, 0x40 };
 *         constexpr SkScalar kWidths[] = { 0, 0.5f, 1.5f };
 *
 *         enum {
 *             kMargin = 5,
 *         };
 *         int wrapX = 1250 - kMargin;
 *
 *         SkScalar maxH = 0;
 *         canvas->translate(SkIntToScalar(kMargin), SkIntToScalar(kMargin));
 *         canvas->save();
 *
 *         SkScalar x = SkIntToScalar(kMargin);
 *         for (int p = 0; p < fPaths.size(); ++p) {
 *             for (size_t a = 0; a < std::size(kAlphaValue); ++a) {
 *                 for (int aa = 0; aa < 2; ++aa) {
 *                     for (size_t w = 0; w < std::size(kWidths); w++) {
 *                         const SkRect& bounds = fPaths[p].getBounds();
 *
 *                         if (x + bounds.width() > wrapX) {
 *                             canvas->restore();
 *                             canvas->translate(0, maxH + SkIntToScalar(kMargin));
 *                             canvas->save();
 *                             maxH = 0;
 *                             x = SkIntToScalar(kMargin);
 *                         }
 *
 *                         SkPaint paint;
 *                         paint.setARGB(kAlphaValue[a], 0, 0, 0);
 *                         paint.setAntiAlias(SkToBool(aa));
 *                         paint.setStyle(SkPaint::kStroke_Style);
 *                         paint.setStrokeWidth(kWidths[w]);
 *
 *                         canvas->save();
 *                         canvas->translate(-bounds.fLeft, -bounds.fTop);
 *                         canvas->drawPath(fPaths[p], paint);
 *                         canvas->restore();
 *
 *                         maxH = std::max(maxH, bounds.height());
 *
 *                         SkScalar dx = bounds.width() + SkIntToScalar(kMargin);
 *                         x += dx;
 *                         canvas->translate(dx, 0);
 *                     }
 *                 }
 *             }
 *         }
 *         canvas->restore();
 *     }
 *
 * private:
 *     TArray<SkPath> fPaths;
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class HairlinesGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * TArray<SkPath> fPaths
   * ```
   */
  private var fPaths: Int = TODO("Initialize fPaths")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("hairlines"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1250, 1250); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         {
   *             SkPathBuilder lineAngles;
   *             constexpr int kNumAngles = 15;
   *             constexpr int kRadius = 40;
   *
   *             for (int i = 0; i < kNumAngles; ++i) {
   *                 SkScalar angle = SK_ScalarPI * SkIntToScalar(i) / kNumAngles;
   *                 SkScalar x = kRadius * SkScalarCos(angle);
   *                 SkScalar y = kRadius * SkScalarSin(angle);
   *                 lineAngles.moveTo(x, y).lineTo(-x, -y);
   *             }
   *             fPaths.push_back(lineAngles.detach());
   *         }
   *
   *         fPaths.push_back(SkPathBuilder().moveTo(0, -10)
   *                                         .quadTo(100, 100, -10, 0)
   *                                         .detach());
   *
   *         fPaths.push_back(SkPathBuilder().moveTo(0, -5)
   *                                         .quadTo(100, 100, -5, 0)
   *                                         .detach());
   *
   *         fPaths.push_back(SkPathBuilder().moveTo(0, -2)
   *                                         .quadTo(100, 100, -2, 0)
   *                                         .detach());
   *
   *         fPaths.push_back(SkPathBuilder().moveTo(0, -1)
   *                                         .quadTo(100, 100, -2 + 306.0f / 4, 75)
   *                                         .detach());
   *
   *         fPaths.push_back(SkPathBuilder().moveTo(0, -1)
   *                                         .quadTo(100, 100, -1, 0)
   *                                         .detach());
   *
   *         fPaths.push_back(SkPathBuilder().moveTo(0, -0)
   *                                         .quadTo(100, 100, 0, 0)
   *                                         .detach());
   *
   *         fPaths.push_back(SkPathBuilder().moveTo(0, -0)
   *                                         .quadTo(100, 100, 75, 75)
   *                                         .detach());
   *
   *         // Two problem cases for gpu hairline renderer found by shapeops testing. These used
   *         // to assert that the computed bounding box didn't contain all the vertices.
   *
   *         fPaths.push_back(SkPathBuilder().moveTo(4, 6)
   *                                         .cubicTo(5, 6, 5, 4, 4, 0)
   *                                         .close()
   *                                         .detach());
   *
   *         fPaths.push_back(SkPathBuilder().moveTo(5, 1)
   *                                         .lineTo( 4.32787323f, 1.67212653f)
   *                                         .cubicTo(2.75223875f, 3.24776125f,
   *                                                  3.00581908f, 4.51236057f,
   *                                                  3.7580452f,  4.37367964f)
   *                                         .cubicTo(4.66472578f, 3.888381f,
   *                                                  5.f,         2.875f,
   *                                                  5.f,         1.f)
   *                                         .close()
   *                                         .detach());
   *
   *         // Three paths that show the same bug (missing end caps)
   *
   *         fPaths.push_back(SkPathBuilder().moveTo(6.5f,5.5f)
   *                                         .lineTo(3.5f,0.5f)
   *                                         .moveTo(0.5f,5.5f)
   *                                         .lineTo(3.5f,0.5f)
   *                                         .detach());
   *
   *         // An X (crbug.com/137317)
   *         fPaths.push_back(SkPathBuilder().moveTo(1, 1)
   *                                         .lineTo(6, 6)
   *                                         .moveTo(1, 6)
   *                                         .lineTo(6, 1)
   *                                         .detach());
   *
   *         // A right angle (crbug.com/137465 and crbug.com/256776)
   *         fPaths.push_back(SkPathBuilder().moveTo(5.5f, 5.5f)
   *                                         .lineTo(5.5f, 0.5f)
   *                                         .lineTo(0.5f, 0.5f)
   *                                         .detach());
   *
   *         {
   *             // Arc example to test imperfect truncation bug (crbug.com/295626)
   *             constexpr SkScalar kRad = SkIntToScalar(2000);
   *             constexpr SkScalar kStartAngle = 262.59717f;
   *             constexpr SkScalar kSweepAngle = SkScalarHalf(17.188717f);
   *
   *             SkPathBuilder bug;
   *
   *             // Add a circular arc
   *             SkRect circle = SkRect::MakeLTRB(-kRad, -kRad, kRad, kRad);
   *             bug.addArc(circle, kStartAngle, kSweepAngle);
   *
   *             // Now add the chord that should cap the circular arc
   *             SkPoint p0 = { kRad * SkScalarCos(SkDegreesToRadians(kStartAngle)),
   *                            kRad * SkScalarSin(SkDegreesToRadians(kStartAngle)) };
   *
   *             SkPoint p1 = { kRad * SkScalarCos(SkDegreesToRadians(kStartAngle + kSweepAngle)),
   *                            kRad * SkScalarSin(SkDegreesToRadians(kStartAngle + kSweepAngle)) };
   *
   *             bug.moveTo(p0);
   *             bug.lineTo(p1);
   *             fPaths.push_back(bug.detach());
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
   *         constexpr SkAlpha kAlphaValue[] = { 0xFF, 0x40 };
   *         constexpr SkScalar kWidths[] = { 0, 0.5f, 1.5f };
   *
   *         enum {
   *             kMargin = 5,
   *         };
   *         int wrapX = 1250 - kMargin;
   *
   *         SkScalar maxH = 0;
   *         canvas->translate(SkIntToScalar(kMargin), SkIntToScalar(kMargin));
   *         canvas->save();
   *
   *         SkScalar x = SkIntToScalar(kMargin);
   *         for (int p = 0; p < fPaths.size(); ++p) {
   *             for (size_t a = 0; a < std::size(kAlphaValue); ++a) {
   *                 for (int aa = 0; aa < 2; ++aa) {
   *                     for (size_t w = 0; w < std::size(kWidths); w++) {
   *                         const SkRect& bounds = fPaths[p].getBounds();
   *
   *                         if (x + bounds.width() > wrapX) {
   *                             canvas->restore();
   *                             canvas->translate(0, maxH + SkIntToScalar(kMargin));
   *                             canvas->save();
   *                             maxH = 0;
   *                             x = SkIntToScalar(kMargin);
   *                         }
   *
   *                         SkPaint paint;
   *                         paint.setARGB(kAlphaValue[a], 0, 0, 0);
   *                         paint.setAntiAlias(SkToBool(aa));
   *                         paint.setStyle(SkPaint::kStroke_Style);
   *                         paint.setStrokeWidth(kWidths[w]);
   *
   *                         canvas->save();
   *                         canvas->translate(-bounds.fLeft, -bounds.fTop);
   *                         canvas->drawPath(fPaths[p], paint);
   *                         canvas->restore();
   *
   *                         maxH = std::max(maxH, bounds.height());
   *
   *                         SkScalar dx = bounds.width() + SkIntToScalar(kMargin);
   *                         x += dx;
   *                         canvas->translate(dx, 0);
   *                     }
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
