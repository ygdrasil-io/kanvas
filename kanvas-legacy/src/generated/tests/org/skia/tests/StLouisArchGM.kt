package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class StLouisArchGM : public GM {
 * protected:
 *     SkString getName() const override { return SkString("stlouisarch"); }
 *
 *     SkISize getISize() override { return SkISize::Make((int)kWidth, (int)kHeight); }
 *
 *     void onOnceBeforeDraw() override {
 *         {
 *             SkPath bigQuad = SkPathBuilder()
 *                              .moveTo(0, 0)
 *                              .quadTo(kWidth/2, kHeight, kWidth, 0)
 *                              .detach();
 *             fPaths.push_back(bigQuad);
 *         }
 *
 *         {
 *             SkScalar yPos = kHeight / 2 + 10;
 *             SkPath degenBigQuad = SkPathBuilder()
 *                                   .moveTo(0, yPos)
 *                                   .quadTo(0, yPos, kWidth, yPos)
 *                                   .detach();
 *             fPaths.push_back(degenBigQuad);
 *         }
 *
 *         {
 *             SkPath bigCubic = SkPathBuilder()
 *                               .moveTo(0, 0)
 *                               .cubicTo(0, kHeight, kWidth, kHeight, kWidth, 0)
 *                               .detach();
 *             fPaths.push_back(bigCubic);
 *         }
 *
 *         {
 *             SkScalar yPos = kHeight / 2;
 *             SkPath degenBigCubic = SkPathBuilder()
 *                                    .moveTo(0, yPos)
 *                                    .cubicTo(0, yPos, 0, yPos, kWidth, yPos)
 *                                    .detach();
 *             fPaths.push_back(degenBigCubic);
 *         }
 *
 *         {
 *             SkPath bigConic = SkPathBuilder()
 *                               .moveTo(0, 0)
 *                               .conicTo(kWidth/2, kHeight, kWidth, 0, .5)
 *                               .detach();
 *             fPaths.push_back(bigConic);
 *         }
 *
 *         {
 *             SkScalar yPos = kHeight / 2 - 10;
 *             SkPath degenBigConic = SkPathBuilder()
 *                                    .moveTo(0, yPos)
 *                                    .conicTo(0, yPos, kWidth, yPos, .5)
 *                                    .detach();
 *             fPaths.push_back(degenBigConic);
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->save();
 *         canvas->scale(1, -1);
 *         canvas->translate(0, -kHeight);
 *         for (int p = 0; p < fPaths.size(); ++p) {
 *             SkPaint paint;
 *             paint.setARGB(0xff, 0, 0, 0);
 *             paint.setAntiAlias(true);
 *             paint.setStyle(SkPaint::kStroke_Style);
 *             paint.setStrokeWidth(0);
 *             canvas->drawPath(fPaths[p], paint);
 *         }
 *         canvas->restore();
 *     }
 *
 *     const SkScalar kWidth = 256;
 *     const SkScalar kHeight = 256;
 *
 * private:
 *     TArray<SkPath> fPaths;
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class StLouisArchGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * const SkScalar kWidth = 256
   * ```
   */
  protected val kWidth: SkScalar = TODO("Initialize kWidth")

  /**
   * C++ original:
   * ```cpp
   * const SkScalar kHeight = 256
   * ```
   */
  protected val kHeight: SkScalar = TODO("Initialize kHeight")

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
   * SkString getName() const override { return SkString("stlouisarch"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make((int)kWidth, (int)kHeight); }
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
   *             SkPath bigQuad = SkPathBuilder()
   *                              .moveTo(0, 0)
   *                              .quadTo(kWidth/2, kHeight, kWidth, 0)
   *                              .detach();
   *             fPaths.push_back(bigQuad);
   *         }
   *
   *         {
   *             SkScalar yPos = kHeight / 2 + 10;
   *             SkPath degenBigQuad = SkPathBuilder()
   *                                   .moveTo(0, yPos)
   *                                   .quadTo(0, yPos, kWidth, yPos)
   *                                   .detach();
   *             fPaths.push_back(degenBigQuad);
   *         }
   *
   *         {
   *             SkPath bigCubic = SkPathBuilder()
   *                               .moveTo(0, 0)
   *                               .cubicTo(0, kHeight, kWidth, kHeight, kWidth, 0)
   *                               .detach();
   *             fPaths.push_back(bigCubic);
   *         }
   *
   *         {
   *             SkScalar yPos = kHeight / 2;
   *             SkPath degenBigCubic = SkPathBuilder()
   *                                    .moveTo(0, yPos)
   *                                    .cubicTo(0, yPos, 0, yPos, kWidth, yPos)
   *                                    .detach();
   *             fPaths.push_back(degenBigCubic);
   *         }
   *
   *         {
   *             SkPath bigConic = SkPathBuilder()
   *                               .moveTo(0, 0)
   *                               .conicTo(kWidth/2, kHeight, kWidth, 0, .5)
   *                               .detach();
   *             fPaths.push_back(bigConic);
   *         }
   *
   *         {
   *             SkScalar yPos = kHeight / 2 - 10;
   *             SkPath degenBigConic = SkPathBuilder()
   *                                    .moveTo(0, yPos)
   *                                    .conicTo(0, yPos, kWidth, yPos, .5)
   *                                    .detach();
   *             fPaths.push_back(degenBigConic);
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
   *         canvas->save();
   *         canvas->scale(1, -1);
   *         canvas->translate(0, -kHeight);
   *         for (int p = 0; p < fPaths.size(); ++p) {
   *             SkPaint paint;
   *             paint.setARGB(0xff, 0, 0, 0);
   *             paint.setAntiAlias(true);
   *             paint.setStyle(SkPaint::kStroke_Style);
   *             paint.setStrokeWidth(0);
   *             canvas->drawPath(fPaths[p], paint);
   *         }
   *         canvas->restore();
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
