package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ShapesGM : public GM {
 * protected:
 *     ShapesGM(const char* name, bool antialias) : fName(name), fAntialias(antialias) {
 *         if (!antialias) {
 *             fName.append("_bw");
 *         }
 *     }
 *
 *     SkString getName() const override { return fName; }
 *     SkISize getISize() override { return SkISize::Make(500, 500); }
 *
 *     void onOnceBeforeDraw() override {
 *         fShapes.push_back().setOval(SkRect::MakeXYWH(-5, 25, 200, 100));
 *         fRotations.push_back(21);
 *
 *         fShapes.push_back().setRect(SkRect::MakeXYWH(95, 75, 125, 100));
 *         fRotations.push_back(94);
 *
 *         fShapes.push_back().setRectXY(SkRect::MakeXYWH(0, 75, 150, 100), 1e-5f, 1e-5f);
 *         fRotations.push_back(132);
 *
 *         fShapes.push_back().setRectXY(SkRect::MakeXYWH(15, -20, 100, 100), 20, 15);
 *         fRotations.push_back(282);
 *
 *         fSimpleShapeCount = fShapes.size();
 *
 *         fShapes.push_back().setNinePatch(SkRect::MakeXYWH(140, -50, 90, 110), 10, 5, 25, 35);
 *         fRotations.push_back(0);
 *
 *         fShapes.push_back().setNinePatch(SkRect::MakeXYWH(160, -60, 60, 90), 10, 60, 50, 30);
 *         fRotations.push_back(-35);
 *
 *         fShapes.push_back().setNinePatch(SkRect::MakeXYWH(220, -120, 60, 90), 1, 89, 59, 1);
 *         fRotations.push_back(65);
 *
 *         SkVector radii[4] = {{4, 6}, {12, 8}, {24, 16}, {32, 48}};
 *         fShapes.push_back().setRectRadii(SkRect::MakeXYWH(150, -129, 80, 160), radii);
 *         fRotations.push_back(265);
 *
 *         SkVector radii2[4] = {{0, 0}, {80, 60}, {0, 0}, {80, 60}};
 *         fShapes.push_back().setRectRadii(SkRect::MakeXYWH(180, -30, 80, 60), radii2);
 *         fRotations.push_back(295);
 *
 *         fPaint.setAntiAlias(fAntialias);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->clear(SK_ColorWHITE);
 *
 *         canvas->save();
 *         canvas->translate(canvas->imageInfo().width() / 2.f, canvas->imageInfo().height() / 2.f);
 *         this->drawShapes(canvas);
 *         canvas->restore();
 *     }
 *
 *     virtual void drawShapes(SkCanvas* canvas) const = 0;
 *
 * protected:
 *     SkString             fName;
 *     bool                 fAntialias;
 *     SkPaint              fPaint;
 *     TArray<SkRRect>    fShapes;
 *     TArray<SkScalar>   fRotations;
 *     int                  fSimpleShapeCount;
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public abstract class ShapesGM public constructor(
  name: String?,
  antialias: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString             fName
   * ```
   */
  protected var fName: String = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * bool                 fAntialias
   * ```
   */
  protected var fAntialias: Boolean = TODO("Initialize fAntialias")

  /**
   * C++ original:
   * ```cpp
   * SkPaint              fPaint
   * ```
   */
  protected var fPaint: SkPaint = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * TArray<SkRRect>    fShapes
   * ```
   */
  protected var fShapes: Int = TODO("Initialize fShapes")

  /**
   * C++ original:
   * ```cpp
   * TArray<SkScalar>   fRotations
   * ```
   */
  protected var fRotations: Int = TODO("Initialize fRotations")

  /**
   * C++ original:
   * ```cpp
   * int                  fSimpleShapeCount
   * ```
   */
  protected var fSimpleShapeCount: Int = TODO("Initialize fSimpleShapeCount")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return fName; }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(500, 500); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fShapes.push_back().setOval(SkRect::MakeXYWH(-5, 25, 200, 100));
   *         fRotations.push_back(21);
   *
   *         fShapes.push_back().setRect(SkRect::MakeXYWH(95, 75, 125, 100));
   *         fRotations.push_back(94);
   *
   *         fShapes.push_back().setRectXY(SkRect::MakeXYWH(0, 75, 150, 100), 1e-5f, 1e-5f);
   *         fRotations.push_back(132);
   *
   *         fShapes.push_back().setRectXY(SkRect::MakeXYWH(15, -20, 100, 100), 20, 15);
   *         fRotations.push_back(282);
   *
   *         fSimpleShapeCount = fShapes.size();
   *
   *         fShapes.push_back().setNinePatch(SkRect::MakeXYWH(140, -50, 90, 110), 10, 5, 25, 35);
   *         fRotations.push_back(0);
   *
   *         fShapes.push_back().setNinePatch(SkRect::MakeXYWH(160, -60, 60, 90), 10, 60, 50, 30);
   *         fRotations.push_back(-35);
   *
   *         fShapes.push_back().setNinePatch(SkRect::MakeXYWH(220, -120, 60, 90), 1, 89, 59, 1);
   *         fRotations.push_back(65);
   *
   *         SkVector radii[4] = {{4, 6}, {12, 8}, {24, 16}, {32, 48}};
   *         fShapes.push_back().setRectRadii(SkRect::MakeXYWH(150, -129, 80, 160), radii);
   *         fRotations.push_back(265);
   *
   *         SkVector radii2[4] = {{0, 0}, {80, 60}, {0, 0}, {80, 60}};
   *         fShapes.push_back().setRectRadii(SkRect::MakeXYWH(180, -30, 80, 60), radii2);
   *         fRotations.push_back(295);
   *
   *         fPaint.setAntiAlias(fAntialias);
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
   *         canvas->clear(SK_ColorWHITE);
   *
   *         canvas->save();
   *         canvas->translate(canvas->imageInfo().width() / 2.f, canvas->imageInfo().height() / 2.f);
   *         this->drawShapes(canvas);
   *         canvas->restore();
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void drawShapes(SkCanvas* canvas) const = 0
   * ```
   */
  protected abstract fun drawShapes(canvas: SkCanvas?)
}

public typealias SimpleShapesGMINHERITED = ShapesGM

public typealias InnerShapesGMINHERITED = ShapesGM
