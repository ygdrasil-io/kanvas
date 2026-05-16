package org.skia.tests

import kotlin.Float
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class ClipSuperRRect : public RuntimeShaderGM {
 * public:
 *     ClipSuperRRect(const char* name, float power) : RuntimeShaderGM(name, {500, 500}, R"(
 *         uniform float power_minus1;
 *         uniform float2 stretch_factor;
 *         uniform float2x2 derivatives;
 *         half4 main(float2 xy) {
 *             xy = max(abs(xy) + stretch_factor, 0);
 *             float2 exp_minus1 = pow(xy, power_minus1.xx);  // If power == 3.5: xy * xy * sqrt(xy)
 *             float f = dot(exp_minus1, xy) - 1;  // f = x^n + y^n - 1
 *             float2 grad = exp_minus1 * derivatives;
 *             float fwidth = abs(grad.x) + abs(grad.y) + 1e-12;  // 1e-12 to avoid a divide by zero.
 *             return half4(saturate(.5 - f/fwidth)); // Approx coverage by riding the gradient to f=0.
 *         }
 *     )"), fPower(power) {}
 *
 *     void drawSuperRRect(SkCanvas* canvas, const SkRect& superRRect, float radX, float radY,
 *                         SkColor color) {
 *         SkPaint paint;
 *         paint.setColor(color);
 *
 *         if (fPower == 2) {
 *             // Draw a normal round rect for the sake of testing.
 *             SkRRect rrect = SkRRect::MakeRectXY(superRRect, radX, radY);
 *             paint.setAntiAlias(true);
 *             canvas->drawRRect(rrect, paint);
 *             return;
 *         }
 *
 *         SkRuntimeShaderBuilder builder(fEffect);
 *         builder.uniform("power_minus1") = fPower - 1;
 *
 *         // Size the corners such that the "apex" of our "super" rounded corner is in the same
 *         // location that the apex of a circular rounded corner would be with the given radii. We
 *         // define the apex as the point on the rounded corner that is 45 degrees between the
 *         // horizontal and vertical edges.
 *         float scale = (1 - SK_ScalarRoot2Over2) / (1 - exp2f(-1/fPower));
 *         float cornerWidth = radX * scale;
 *         float cornerHeight = radY * scale;
 *         cornerWidth = std::min(cornerWidth, superRRect.width() * .5f);
 *         cornerHeight = std::min(cornerHeight, superRRect.height() * .5f);
 *         // The stretch factor controls how long the flat edge should be between rounded corners.
 *         builder.uniform("stretch_factor") = SkV2{1 - superRRect.width()*.5f / cornerWidth,
 *                                                  1 - superRRect.height()*.5f / cornerHeight};
 *
 *         // Calculate a 2x2 "derivatives" matrix that the shader will use to find the gradient.
 *         //
 *         //     f = s^n + t^n - 1   [s,t are "super" rounded corner coords in normalized 0..1 space]
 *         //
 *         //     gradient = [df/dx  df/dy] = [ns^(n-1)  nt^(n-1)] * |ds/dx  ds/dy|
 *         //                                                        |dt/dx  dt/dy|
 *         //
 *         //              = [s^(n-1)  t^(n-1)] * |n  0| * |ds/dx  ds/dy|
 *         //                                     |0  n|   |dt/dx  dt/dy|
 *         //
 *         //              = [s^(n-1)  t^(n-1)] * |2n/cornerWidth   0| * mat2x2(canvasMatrix)^-1
 *         //                                     |0  2n/cornerHeight|
 *         //
 *         //              = [s^(n-1)  t^(n-1)] * "derivatives"
 *         //
 *         const SkMatrix& M = canvas->getTotalMatrix();
 *         float a=M.getScaleX(), b=M.getSkewX(), c=M.getSkewY(), d=M.getScaleY();
 *         float determinant = a*d - b*c;
 *         float dx = fPower / (cornerWidth * determinant);
 *         float dy = fPower / (cornerHeight * determinant);
 *         builder.uniform("derivatives") = SkV4{d*dx, -c*dy, -b*dx, a*dy};
 *
 *         // This matrix will be inverted by the effect system, giving a matrix that converts local
 *         // coordinates to (almost) coner coordinates. To get the rest of the way to the nearest
 *         // corner's space, the shader will have to take the absolute value, add the stretch_factor,
 *         // then clamp above zero.
 *         SkMatrix cornerToLocal;
 *         cornerToLocal.setScaleTranslate(cornerWidth, cornerHeight, superRRect.centerX(),
 *                                         superRRect.centerY());
 *         canvas->clipShader(builder.makeShader(&cornerToLocal));
 *
 *         // Bloat the outer edges of the rect we will draw so it contains all the antialiased pixels.
 *         // Bloat by a full pixel instead of half in case Skia is in a mode that draws this rect with
 *         // unexpected AA of its own.
 *         float inverseDet = 1 / fabsf(determinant);
 *         float bloatX = (fabsf(d) + fabsf(c)) * inverseDet;
 *         float bloatY = (fabsf(b) + fabsf(a)) * inverseDet;
 *         canvas->drawRect(superRRect.makeOutset(bloatX, bloatY), paint);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkRandom rand(2);
 *
 *         canvas->save();
 *         canvas->translate(canvas->imageInfo().width() / 2.f, canvas->imageInfo().height() / 2.f);
 *
 *         canvas->save();
 *         canvas->rotate(21);
 *         this->drawSuperRRect(canvas, SkRect::MakeXYWH(-5, 25, 175, 100), 50, 30,
 *                              rand.nextU() | 0xff808080);
 *         canvas->restore();
 *
 *         canvas->save();
 *         canvas->rotate(94);
 *         this->drawSuperRRect(canvas, SkRect::MakeXYWH(95, 75, 125, 100), 30, 30,
 *                              rand.nextU() | 0xff808080);
 *         canvas->restore();
 *
 *         canvas->save();
 *         canvas->rotate(132);
 *         this->drawSuperRRect(canvas, SkRect::MakeXYWH(0, 75, 150, 100), 40, 30,
 *                              rand.nextU() | 0xff808080);
 *         canvas->restore();
 *
 *         canvas->save();
 *         canvas->rotate(282);
 *         this->drawSuperRRect(canvas, SkRect::MakeXYWH(15, -20, 100, 100), 20, 20,
 *                              rand.nextU() | 0xff808080);
 *         canvas->restore();
 *
 *         canvas->save();
 *         canvas->rotate(0);
 *         this->drawSuperRRect(canvas, SkRect::MakeXYWH(140, -50, 90, 110), 25, 25,
 *                              rand.nextU() | 0xff808080);
 *         canvas->restore();
 *
 *         canvas->save();
 *         canvas->rotate(-35);
 *         this->drawSuperRRect(canvas, SkRect::MakeXYWH(160, -60, 60, 90), 18, 18,
 *                              rand.nextU() | 0xff808080);
 *         canvas->restore();
 *
 *         canvas->save();
 *         canvas->rotate(65);
 *         this->drawSuperRRect(canvas, SkRect::MakeXYWH(220, -120, 60, 90), 18, 18,
 *                              rand.nextU() | 0xff808080);
 *         canvas->restore();
 *
 *         canvas->save();
 *         canvas->rotate(265);
 *         this->drawSuperRRect(canvas, SkRect::MakeXYWH(150, -129, 80, 160), 24, 39,
 *                              rand.nextU() | 0xff808080);
 *         canvas->restore();
 *
 *         canvas->restore();
 *     }
 *
 * private:
 *     const float fPower;
 * }
 * ```
 */
public open class ClipSuperRRect public constructor(
  name: String?,
  power: Float,
) : RuntimeShaderGM(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const float fPower
   * ```
   */
  private val fPower: Float = TODO("Initialize fPower")

  /**
   * C++ original:
   * ```cpp
   * void drawSuperRRect(SkCanvas* canvas, const SkRect& superRRect, float radX, float radY,
   *                         SkColor color) {
   *         SkPaint paint;
   *         paint.setColor(color);
   *
   *         if (fPower == 2) {
   *             // Draw a normal round rect for the sake of testing.
   *             SkRRect rrect = SkRRect::MakeRectXY(superRRect, radX, radY);
   *             paint.setAntiAlias(true);
   *             canvas->drawRRect(rrect, paint);
   *             return;
   *         }
   *
   *         SkRuntimeShaderBuilder builder(fEffect);
   *         builder.uniform("power_minus1") = fPower - 1;
   *
   *         // Size the corners such that the "apex" of our "super" rounded corner is in the same
   *         // location that the apex of a circular rounded corner would be with the given radii. We
   *         // define the apex as the point on the rounded corner that is 45 degrees between the
   *         // horizontal and vertical edges.
   *         float scale = (1 - SK_ScalarRoot2Over2) / (1 - exp2f(-1/fPower));
   *         float cornerWidth = radX * scale;
   *         float cornerHeight = radY * scale;
   *         cornerWidth = std::min(cornerWidth, superRRect.width() * .5f);
   *         cornerHeight = std::min(cornerHeight, superRRect.height() * .5f);
   *         // The stretch factor controls how long the flat edge should be between rounded corners.
   *         builder.uniform("stretch_factor") = SkV2{1 - superRRect.width()*.5f / cornerWidth,
   *                                                  1 - superRRect.height()*.5f / cornerHeight};
   *
   *         // Calculate a 2x2 "derivatives" matrix that the shader will use to find the gradient.
   *         //
   *         //     f = s^n + t^n - 1   [s,t are "super" rounded corner coords in normalized 0..1 space]
   *         //
   *         //     gradient = [df/dx  df/dy] = [ns^(n-1)  nt^(n-1)] * |ds/dx  ds/dy|
   *         //                                                        |dt/dx  dt/dy|
   *         //
   *         //              = [s^(n-1)  t^(n-1)] * |n  0| * |ds/dx  ds/dy|
   *         //                                     |0  n|   |dt/dx  dt/dy|
   *         //
   *         //              = [s^(n-1)  t^(n-1)] * |2n/cornerWidth   0| * mat2x2(canvasMatrix)^-1
   *         //                                     |0  2n/cornerHeight|
   *         //
   *         //              = [s^(n-1)  t^(n-1)] * "derivatives"
   *         //
   *         const SkMatrix& M = canvas->getTotalMatrix();
   *         float a=M.getScaleX(), b=M.getSkewX(), c=M.getSkewY(), d=M.getScaleY();
   *         float determinant = a*d - b*c;
   *         float dx = fPower / (cornerWidth * determinant);
   *         float dy = fPower / (cornerHeight * determinant);
   *         builder.uniform("derivatives") = SkV4{d*dx, -c*dy, -b*dx, a*dy};
   *
   *         // This matrix will be inverted by the effect system, giving a matrix that converts local
   *         // coordinates to (almost) coner coordinates. To get the rest of the way to the nearest
   *         // corner's space, the shader will have to take the absolute value, add the stretch_factor,
   *         // then clamp above zero.
   *         SkMatrix cornerToLocal;
   *         cornerToLocal.setScaleTranslate(cornerWidth, cornerHeight, superRRect.centerX(),
   *                                         superRRect.centerY());
   *         canvas->clipShader(builder.makeShader(&cornerToLocal));
   *
   *         // Bloat the outer edges of the rect we will draw so it contains all the antialiased pixels.
   *         // Bloat by a full pixel instead of half in case Skia is in a mode that draws this rect with
   *         // unexpected AA of its own.
   *         float inverseDet = 1 / fabsf(determinant);
   *         float bloatX = (fabsf(d) + fabsf(c)) * inverseDet;
   *         float bloatY = (fabsf(b) + fabsf(a)) * inverseDet;
   *         canvas->drawRect(superRRect.makeOutset(bloatX, bloatY), paint);
   *     }
   * ```
   */
  public fun drawSuperRRect(
    canvas: SkCanvas?,
    superRRect: SkRect,
    radX: Float,
    radY: Float,
    color: SkColor,
  ) {
    TODO("Implement drawSuperRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkRandom rand(2);
   *
   *         canvas->save();
   *         canvas->translate(canvas->imageInfo().width() / 2.f, canvas->imageInfo().height() / 2.f);
   *
   *         canvas->save();
   *         canvas->rotate(21);
   *         this->drawSuperRRect(canvas, SkRect::MakeXYWH(-5, 25, 175, 100), 50, 30,
   *                              rand.nextU() | 0xff808080);
   *         canvas->restore();
   *
   *         canvas->save();
   *         canvas->rotate(94);
   *         this->drawSuperRRect(canvas, SkRect::MakeXYWH(95, 75, 125, 100), 30, 30,
   *                              rand.nextU() | 0xff808080);
   *         canvas->restore();
   *
   *         canvas->save();
   *         canvas->rotate(132);
   *         this->drawSuperRRect(canvas, SkRect::MakeXYWH(0, 75, 150, 100), 40, 30,
   *                              rand.nextU() | 0xff808080);
   *         canvas->restore();
   *
   *         canvas->save();
   *         canvas->rotate(282);
   *         this->drawSuperRRect(canvas, SkRect::MakeXYWH(15, -20, 100, 100), 20, 20,
   *                              rand.nextU() | 0xff808080);
   *         canvas->restore();
   *
   *         canvas->save();
   *         canvas->rotate(0);
   *         this->drawSuperRRect(canvas, SkRect::MakeXYWH(140, -50, 90, 110), 25, 25,
   *                              rand.nextU() | 0xff808080);
   *         canvas->restore();
   *
   *         canvas->save();
   *         canvas->rotate(-35);
   *         this->drawSuperRRect(canvas, SkRect::MakeXYWH(160, -60, 60, 90), 18, 18,
   *                              rand.nextU() | 0xff808080);
   *         canvas->restore();
   *
   *         canvas->save();
   *         canvas->rotate(65);
   *         this->drawSuperRRect(canvas, SkRect::MakeXYWH(220, -120, 60, 90), 18, 18,
   *                              rand.nextU() | 0xff808080);
   *         canvas->restore();
   *
   *         canvas->save();
   *         canvas->rotate(265);
   *         this->drawSuperRRect(canvas, SkRect::MakeXYWH(150, -129, 80, 160), 24, 39,
   *                              rand.nextU() | 0xff808080);
   *         canvas->restore();
   *
   *         canvas->restore();
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
