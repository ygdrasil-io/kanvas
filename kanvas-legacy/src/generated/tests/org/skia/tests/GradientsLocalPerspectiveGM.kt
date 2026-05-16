package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class GradientsLocalPerspectiveGM : public skiagm::GM {
 * public:
 *     GradientsLocalPerspectiveGM(bool dither) : fDither(dither) {
 *         this->setBGColor(0xFFDDDDDD);
 *     }
 *
 * private:
 *     SkString getName() const override {
 *         return SkString(fDither ? "gradients_local_perspective" :
 *                                   "gradients_local_perspective_nodither");
 *     }
 *
 *     SkISize getISize() override { return {840, 815}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPoint pts[2] = {
 *             { 0, 0 },
 *             { SkIntToScalar(100), SkIntToScalar(100) }
 *         };
 *         SkTileMode tm = SkTileMode::kClamp;
 *         SkRect r = { 0, 0, SkIntToScalar(100), SkIntToScalar(100) };
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setDither(fDither);
 *
 *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
 *         for (size_t i = 0; i < std::size(gGradData); i++) {
 *             canvas->save();
 *             for (size_t j = 0; j < std::size(gGradMakers); j++) {
 *                 // apply an increasing y perspective as we move to the right
 *                 SkMatrix perspective;
 *                 perspective.setIdentity();
 *                 perspective.setPerspY(SkIntToScalar(i+1) / 500);
 *                 perspective.setSkewX(SkIntToScalar(i+1) / 10);
 *
 *                 paint.setShader(gGradMakers[j](pts, gGradData[i], tm, perspective));
 *                 canvas->drawRect(r, paint);
 *                 canvas->translate(0, SkIntToScalar(120));
 *             }
 *             canvas->restore();
 *             canvas->translate(SkIntToScalar(120), 0);
 *         }
 *     }
 *
 *     bool fDither;
 * }
 * ```
 */
public open class GradientsLocalPerspectiveGM public constructor(
  dither: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * bool fDither
   * ```
   */
  private var fDither: Boolean = TODO("Initialize fDither")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         return SkString(fDither ? "gradients_local_perspective" :
   *                                   "gradients_local_perspective_nodither");
   *     }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {840, 815}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPoint pts[2] = {
   *             { 0, 0 },
   *             { SkIntToScalar(100), SkIntToScalar(100) }
   *         };
   *         SkTileMode tm = SkTileMode::kClamp;
   *         SkRect r = { 0, 0, SkIntToScalar(100), SkIntToScalar(100) };
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setDither(fDither);
   *
   *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
   *         for (size_t i = 0; i < std::size(gGradData); i++) {
   *             canvas->save();
   *             for (size_t j = 0; j < std::size(gGradMakers); j++) {
   *                 // apply an increasing y perspective as we move to the right
   *                 SkMatrix perspective;
   *                 perspective.setIdentity();
   *                 perspective.setPerspY(SkIntToScalar(i+1) / 500);
   *                 perspective.setSkewX(SkIntToScalar(i+1) / 10);
   *
   *                 paint.setShader(gGradMakers[j](pts, gGradData[i], tm, perspective));
   *                 canvas->drawRect(r, paint);
   *                 canvas->translate(0, SkIntToScalar(120));
   *             }
   *             canvas->restore();
   *             canvas->translate(SkIntToScalar(120), 0);
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
