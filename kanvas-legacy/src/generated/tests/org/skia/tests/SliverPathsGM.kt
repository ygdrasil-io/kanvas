package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class SliverPathsGM : public GM {
 * public:
 *     SliverPathsGM() {
 *         this->setBGColor(SK_ColorBLACK);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("mandoline"); }
 *
 *     SkISize getISize() override { return SkISize::Make(560, 475); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setColor(SK_ColorWHITE);
 *         paint.setAntiAlias(true);
 *
 *         MandolineSlicer mandoline({41, 43});
 *         mandoline.sliceCubic({5, 277}, {381, -74}, {243, 162});
 *         mandoline.sliceLine({41, 43});
 *         canvas->drawPath(mandoline.path(), paint);
 *
 *         mandoline.reset({357.049988f, 446.049988f});
 *         mandoline.sliceCubic({472.750000f, -71.950012f}, {639.750000f, 531.950012f},
 *                              {309.049988f, 347.950012f});
 *         mandoline.sliceLine({309.049988f, 419});
 *         mandoline.sliceLine({357.049988f, 446.049988f});
 *         canvas->drawPath(mandoline.path(), paint);
 *
 *         canvas->save();
 *         canvas->translate(421, 105);
 *         canvas->scale(100, 81);
 *         mandoline.reset({-cosf(SkDegreesToRadians(-60)), sinf(SkDegreesToRadians(-60))});
 *         mandoline.sliceConic({-2, 0},
 *                              {-cosf(SkDegreesToRadians(60)), sinf(SkDegreesToRadians(60))}, .5f);
 *         mandoline.sliceConic({-cosf(SkDegreesToRadians(120))*2, sinf(SkDegreesToRadians(120))*2},
 *                              {1, 0}, .5f);
 *         mandoline.sliceLine({0, 0});
 *         mandoline.sliceLine({-cosf(SkDegreesToRadians(-60)), sinf(SkDegreesToRadians(-60))});
 *         canvas->drawPath(mandoline.path(), paint);
 *         canvas->restore();
 *
 *         canvas->save();
 *         canvas->translate(150, 300);
 *         canvas->scale(75, 75);
 *         mandoline.reset({1, 0});
 *         constexpr int nquads = 5;
 *         for (int i = 0; i < nquads; ++i) {
 *             float theta1 = 2*SK_ScalarPI/nquads * (i + .5f);
 *             float theta2 = 2*SK_ScalarPI/nquads * (i + 1);
 *             mandoline.sliceQuadratic({cosf(theta1)*2, sinf(theta1)*2},
 *                                      {cosf(theta2), sinf(theta2)});
 *         }
 *         canvas->drawPath(mandoline.path(), paint);
 *         canvas->restore();
 *     }
 * }
 * ```
 */
public open class SliverPathsGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("mandoline"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(560, 475); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *         paint.setColor(SK_ColorWHITE);
   *         paint.setAntiAlias(true);
   *
   *         MandolineSlicer mandoline({41, 43});
   *         mandoline.sliceCubic({5, 277}, {381, -74}, {243, 162});
   *         mandoline.sliceLine({41, 43});
   *         canvas->drawPath(mandoline.path(), paint);
   *
   *         mandoline.reset({357.049988f, 446.049988f});
   *         mandoline.sliceCubic({472.750000f, -71.950012f}, {639.750000f, 531.950012f},
   *                              {309.049988f, 347.950012f});
   *         mandoline.sliceLine({309.049988f, 419});
   *         mandoline.sliceLine({357.049988f, 446.049988f});
   *         canvas->drawPath(mandoline.path(), paint);
   *
   *         canvas->save();
   *         canvas->translate(421, 105);
   *         canvas->scale(100, 81);
   *         mandoline.reset({-cosf(SkDegreesToRadians(-60)), sinf(SkDegreesToRadians(-60))});
   *         mandoline.sliceConic({-2, 0},
   *                              {-cosf(SkDegreesToRadians(60)), sinf(SkDegreesToRadians(60))}, .5f);
   *         mandoline.sliceConic({-cosf(SkDegreesToRadians(120))*2, sinf(SkDegreesToRadians(120))*2},
   *                              {1, 0}, .5f);
   *         mandoline.sliceLine({0, 0});
   *         mandoline.sliceLine({-cosf(SkDegreesToRadians(-60)), sinf(SkDegreesToRadians(-60))});
   *         canvas->drawPath(mandoline.path(), paint);
   *         canvas->restore();
   *
   *         canvas->save();
   *         canvas->translate(150, 300);
   *         canvas->scale(75, 75);
   *         mandoline.reset({1, 0});
   *         constexpr int nquads = 5;
   *         for (int i = 0; i < nquads; ++i) {
   *             float theta1 = 2*SK_ScalarPI/nquads * (i + .5f);
   *             float theta2 = 2*SK_ScalarPI/nquads * (i + 1);
   *             mandoline.sliceQuadratic({cosf(theta1)*2, sinf(theta1)*2},
   *                                      {cosf(theta2), sinf(theta2)});
   *         }
   *         canvas->drawPath(mandoline.path(), paint);
   *         canvas->restore();
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
