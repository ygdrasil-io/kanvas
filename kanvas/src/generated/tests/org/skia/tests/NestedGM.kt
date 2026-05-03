package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkPathBuilder
import org.skia.math.SkISize
import org.skia.math.SkPathDirection
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class NestedGM : public GM {
 * public:
 *     NestedGM(bool doAA, bool flipped) : fDoAA(doAA), fFlipped(flipped) {
 *         this->setBGColor(0xFFDDDDDD);
 *     }
 *
 * protected:
 *     SkString getName() const override {
 *         SkString name("nested");
 *         if (fFlipped) {
 *             name.append("_flipY");
 *         }
 *         if (fDoAA) {
 *             name.append("_aa");
 *         } else {
 *             name.append("_bw");
 *         }
 *         return name;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(kImageWidth, kImageHeight); }
 *
 *     enum Shapes {
 *         kRect_Shape = 0,
 *         kRRect_Shape,
 *         kOval_Shape,
 *         kShapeCount
 *     };
 *
 *     static void AddShape(SkPathBuilder* b, const SkRect& rect, Shapes shape, SkPathDirection dir) {
 *         switch (shape) {
 *             case kRect_Shape:
 *                 b->addRect(rect, dir);
 *                 break;
 *             case kRRect_Shape: {
 *                 SkRRect rr;
 *                 rr.setRectXY(rect, 5, 5);
 *                 b->addRRect(rr, dir);
 *                 break;
 *                 }
 *             case kOval_Shape:
 *                 b->addOval(rect, dir);
 *                 break;
 *             default:
 *                 break;
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *
 *         SkPaint shapePaint;
 *         shapePaint.setColor(SK_ColorBLACK);
 *         shapePaint.setAntiAlias(fDoAA);
 *
 *         SkRect outerRect = SkRect::MakeWH(40, 40);
 *
 *         SkRect innerRects[] = {
 *             { 10, 10, 30, 30 },     // small
 *             { .5f, 18, 4.5f, 22 }   // smaller and offset to left
 *         };
 *
 *         // draw a background pattern to make transparency errors more apparent
 *         SkRandom rand;
 *
 *         for (int y = 0; y < kImageHeight; y += 10) {
 *             for (int x = 0; x < kImageWidth; x += 10) {
 *                 SkRect r = SkRect::MakeXYWH(SkIntToScalar(x),
 *                                             SkIntToScalar(y),
 *                                             10, 10);
 *                 SkPaint p;
 *                 p.setColor(rand.nextU() | 0xFF000000);
 *                 canvas->drawRect(r, p);
 *             }
 *         }
 *
 *         SkScalar xOff = 2, yOff = 2;
 *         for (int outerShape = 0; outerShape < kShapeCount; ++outerShape) {
 *             for (int innerShape = 0; innerShape < kShapeCount; ++innerShape) {
 *                 for (size_t innerRect = 0; innerRect < std::size(innerRects); ++innerRect) {
 *                     SkPathBuilder builder;
 *
 *                     AddShape(&builder, outerRect, (Shapes) outerShape, SkPathDirection::kCW);
 *                     AddShape(&builder, innerRects[innerRect], (Shapes) innerShape,
 *                              SkPathDirection::kCCW);
 *
 *                     canvas->save();
 *                     if (fFlipped) {
 *                         canvas->scale(1.0f, -1.0f);
 *                         canvas->translate(xOff, -yOff - 40.0f);
 *                     } else {
 *                         canvas->translate(xOff, yOff);
 *                     }
 *
 *                     canvas->drawPath(builder.detach(), shapePaint);
 *                     canvas->restore();
 *
 *                     xOff += 45;
 *                 }
 *             }
 *
 *             xOff = 2;
 *             yOff += 45;
 *         }
 *
 *     }
 *
 * private:
 *     inline static constexpr int kImageWidth = 269;
 *     inline static constexpr int kImageHeight = 134;
 *
 *     bool fDoAA;
 *     bool fFlipped;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class NestedGM public constructor(
  doAA: Boolean,
  flipped: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kImageWidth = 269
   * ```
   */
  private var fDoAA: Boolean = TODO("Initialize fDoAA")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kImageHeight = 134
   * ```
   */
  private var fFlipped: Boolean = TODO("Initialize fFlipped")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString name("nested");
   *         if (fFlipped) {
   *             name.append("_flipY");
   *         }
   *         if (fDoAA) {
   *             name.append("_aa");
   *         } else {
   *             name.append("_bw");
   *         }
   *         return name;
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kImageWidth, kImageHeight); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *
   *         SkPaint shapePaint;
   *         shapePaint.setColor(SK_ColorBLACK);
   *         shapePaint.setAntiAlias(fDoAA);
   *
   *         SkRect outerRect = SkRect::MakeWH(40, 40);
   *
   *         SkRect innerRects[] = {
   *             { 10, 10, 30, 30 },     // small
   *             { .5f, 18, 4.5f, 22 }   // smaller and offset to left
   *         };
   *
   *         // draw a background pattern to make transparency errors more apparent
   *         SkRandom rand;
   *
   *         for (int y = 0; y < kImageHeight; y += 10) {
   *             for (int x = 0; x < kImageWidth; x += 10) {
   *                 SkRect r = SkRect::MakeXYWH(SkIntToScalar(x),
   *                                             SkIntToScalar(y),
   *                                             10, 10);
   *                 SkPaint p;
   *                 p.setColor(rand.nextU() | 0xFF000000);
   *                 canvas->drawRect(r, p);
   *             }
   *         }
   *
   *         SkScalar xOff = 2, yOff = 2;
   *         for (int outerShape = 0; outerShape < kShapeCount; ++outerShape) {
   *             for (int innerShape = 0; innerShape < kShapeCount; ++innerShape) {
   *                 for (size_t innerRect = 0; innerRect < std::size(innerRects); ++innerRect) {
   *                     SkPathBuilder builder;
   *
   *                     AddShape(&builder, outerRect, (Shapes) outerShape, SkPathDirection::kCW);
   *                     AddShape(&builder, innerRects[innerRect], (Shapes) innerShape,
   *                              SkPathDirection::kCCW);
   *
   *                     canvas->save();
   *                     if (fFlipped) {
   *                         canvas->scale(1.0f, -1.0f);
   *                         canvas->translate(xOff, -yOff - 40.0f);
   *                     } else {
   *                         canvas->translate(xOff, yOff);
   *                     }
   *
   *                     canvas->drawPath(builder.detach(), shapePaint);
   *                     canvas->restore();
   *
   *                     xOff += 45;
   *                 }
   *             }
   *
   *             xOff = 2;
   *             yOff += 45;
   *         }
   *
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public enum class Shapes {
    kRect_Shape,
    kRRect_Shape,
    kOval_Shape,
    kShapeCount,
  }

  public companion object {
    private val kImageWidth: Int = TODO("Initialize kImageWidth")

    private val kImageHeight: Int = TODO("Initialize kImageHeight")

    /**
     * C++ original:
     * ```cpp
     * static void AddShape(SkPathBuilder* b, const SkRect& rect, Shapes shape, SkPathDirection dir) {
     *         switch (shape) {
     *             case kRect_Shape:
     *                 b->addRect(rect, dir);
     *                 break;
     *             case kRRect_Shape: {
     *                 SkRRect rr;
     *                 rr.setRectXY(rect, 5, 5);
     *                 b->addRRect(rr, dir);
     *                 break;
     *                 }
     *             case kOval_Shape:
     *                 b->addOval(rect, dir);
     *                 break;
     *             default:
     *                 break;
     *         }
     *     }
     * ```
     */
    protected fun addShape(
      b: SkPathBuilder?,
      rect: SkRect,
      shape: Shapes,
      dir: SkPathDirection,
    ) {
      TODO("Implement addShape")
    }
  }
}
