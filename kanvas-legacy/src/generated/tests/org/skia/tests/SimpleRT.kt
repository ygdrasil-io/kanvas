package org.skia.tests

import org.skia.core.SkCanvas

/**
 * C++ original:
 * ```cpp
 * class SimpleRT : public RuntimeShaderGM {
 * public:
 *     SimpleRT() : RuntimeShaderGM("runtime_shader", {512, 256}, R"(
 *         uniform half4 gColor;
 *
 *         half4 main(float2 p) {
 *             return half4(p*(1.0/255), gColor.b, 1);
 *         }
 *     )", kBench_RTFlag) {}
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkRuntimeShaderBuilder builder(fEffect);
 *
 *         SkMatrix localM;
 *         localM.setRotate(90, 128, 128);
 *         builder.uniform("gColor") = SkColor4f{1, 0, 0, 1};
 *
 *         SkPaint p;
 *         p.setShader(builder.makeShader(&localM));
 *         canvas->drawRect({0, 0, 256, 256}, p);
 *     }
 * }
 * ```
 */
public open class SimpleRT public constructor() : RuntimeShaderGM(TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkRuntimeShaderBuilder builder(fEffect);
   *
   *         SkMatrix localM;
   *         localM.setRotate(90, 128, 128);
   *         builder.uniform("gColor") = SkColor4f{1, 0, 0, 1};
   *
   *         SkPaint p;
   *         p.setShader(builder.makeShader(&localM));
   *         canvas->drawRect({0, 0, 256, 256}, p);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
