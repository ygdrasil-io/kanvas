package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class GradientsViewPerspectiveGM : public GradientsGM {
 * public:
 *     GradientsViewPerspectiveGM(bool dither) : INHERITED(dither) { }
 *
 * private:
 *     SkString getName() const override {
 *         return SkString(fDither ? "gradients_view_perspective" :
 *                                   "gradients_view_perspective_nodither");
 *     }
 *
 *     SkISize getISize() override { return {840, 500}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkMatrix perspective;
 *         perspective.setIdentity();
 *         perspective.setPerspY(0.001f);
 *         perspective.setSkewX(SkIntToScalar(8) / 25);
 *         canvas->concat(perspective);
 *         this->INHERITED::onDraw(canvas);
 *     }
 *
 * private:
 *     using INHERITED = GradientsGM;
 * }
 * ```
 */
public open class GradientsViewPerspectiveGM public constructor(
  dither: Boolean,
) : GradientsGM(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         return SkString(fDither ? "gradients_view_perspective" :
   *                                   "gradients_view_perspective_nodither");
   *     }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {840, 500}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkMatrix perspective;
   *         perspective.setIdentity();
   *         perspective.setPerspY(0.001f);
   *         perspective.setSkewX(SkIntToScalar(8) / 25);
   *         canvas->concat(perspective);
   *         this->INHERITED::onDraw(canvas);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
