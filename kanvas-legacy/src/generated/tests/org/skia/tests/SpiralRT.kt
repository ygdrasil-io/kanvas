package org.skia.tests

import org.skia.core.SkCanvas

/**
 * C++ original:
 * ```cpp
 * class SpiralRT : public RuntimeShaderGM {
 * public:
 *     SpiralRT() : RuntimeShaderGM("spiral_rt", {512, 512}, R"(
 *         uniform float rad_scale;
 *         uniform float2 in_center;
 *         layout(color) uniform float4 in_colors0;
 *         layout(color) uniform float4 in_colors1;
 *
 *         half4 main(float2 p) {
 *             float2 pp = p - in_center;
 *             float radius = length(pp);
 *             radius = sqrt(radius);
 *             float angle = atan(pp.y / pp.x);
 *             float t = (angle + 3.1415926/2) / (3.1415926);
 *             t += radius * rad_scale;
 *             t = fract(t);
 *             return in_colors0 * (1-t) + in_colors1 * t;
 *         }
 *     )", kAnimate_RTFlag | kBench_RTFlag) {}
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkRuntimeShaderBuilder builder(fEffect);
 *
 *         builder.uniform("rad_scale")  = std::sin(fSecs * 0.5f + 2.0f) / 5;
 *         builder.uniform("in_center")  = SkV2{256, 256};
 *         builder.uniform("in_colors0") = SkColors::kRed;
 *         builder.uniform("in_colors1") = SkColors::kGreen;
 *
 *         SkPaint paint;
 *         paint.setShader(builder.makeShader());
 *         canvas->drawRect({0, 0, 512, 512}, paint);
 *     }
 * }
 * ```
 */
public open class SpiralRT public constructor() : RuntimeShaderGM(TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkRuntimeShaderBuilder builder(fEffect);
   *
   *         builder.uniform("rad_scale")  = std::sin(fSecs * 0.5f + 2.0f) / 5;
   *         builder.uniform("in_center")  = SkV2{256, 256};
   *         builder.uniform("in_colors0") = SkColors::kRed;
   *         builder.uniform("in_colors1") = SkColors::kGreen;
   *
   *         SkPaint paint;
   *         paint.setShader(builder.makeShader());
   *         canvas->drawRect({0, 0, 512, 512}, paint);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
