package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class ThresholdRT : public RuntimeShaderGM {
 * public:
 *     ThresholdRT() : RuntimeShaderGM("threshold_rt", {256, 256}, R"(
 *         uniform shader before_map;
 *         uniform shader after_map;
 *         uniform shader threshold_map;
 *
 *         uniform float cutoff;
 *         uniform float slope;
 *
 *         float smooth_cutoff(float x) {
 *             x = x * slope + (0.5 - slope * cutoff);
 *             return clamp(x, 0, 1);
 *         }
 *
 *         half4 main(float2 xy) {
 *             half4 before = before_map.eval(xy);
 *             half4 after = after_map.eval(xy);
 *
 *             float m = smooth_cutoff(threshold_map.eval(xy).a);
 *             return mix(before, after, m);
 *         }
 *     )", kAnimate_RTFlag | kBench_RTFlag) {}
 *
 *     sk_sp<SkShader> fBefore, fAfter, fThreshold;
 *
 *     void onOnceBeforeDraw() override {
 *         const SkISize size = {256, 256};
 *         fThreshold = make_threshold(size);
 *         fBefore = make_shader(ToolUtils::GetResourceAsImage("images/mandrill_256.png"), size);
 *         fAfter = make_shader(ToolUtils::GetResourceAsImage("images/dog.jpg"), size);
 *
 *         this->RuntimeShaderGM::onOnceBeforeDraw();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkRuntimeShaderBuilder builder(fEffect);
 *
 *         builder.uniform("cutoff") = sinf(fSecs) * 0.55f + 0.5f;
 *         builder.uniform("slope")  = 10.0f;
 *
 *         builder.child("before_map")    = fBefore;
 *         builder.child("after_map")     = fAfter;
 *         builder.child("threshold_map") = fThreshold;
 *
 *         SkPaint paint;
 *         paint.setShader(builder.makeShader());
 *         canvas->drawRect({0, 0, 256, 256}, paint);
 *
 *         auto draw = [&](SkScalar x, SkScalar y, sk_sp<SkShader> shader) {
 *             paint.setShader(shader);
 *             canvas->save();
 *             canvas->translate(x, y);
 *             canvas->drawRect({0, 0, 256, 256}, paint);
 *             canvas->restore();
 *         };
 *         draw(256,   0, fThreshold);
 *         draw(  0, 256, fBefore);
 *         draw(256, 256, fAfter);
 *     }
 * }
 * ```
 */
public open class ThresholdRT public constructor() : RuntimeShaderGM(TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fBefore
   * ```
   */
  public var fBefore: SkSp<SkShader> = TODO("Initialize fBefore")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fBefore, fAfter
   * ```
   */
  public var fAfter: SkSp<SkShader> = TODO("Initialize fAfter")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fBefore, fAfter, fThreshold
   * ```
   */
  public var fThreshold: SkSp<SkShader> = TODO("Initialize fThreshold")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         const SkISize size = {256, 256};
   *         fThreshold = make_threshold(size);
   *         fBefore = make_shader(ToolUtils::GetResourceAsImage("images/mandrill_256.png"), size);
   *         fAfter = make_shader(ToolUtils::GetResourceAsImage("images/dog.jpg"), size);
   *
   *         this->RuntimeShaderGM::onOnceBeforeDraw();
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkRuntimeShaderBuilder builder(fEffect);
   *
   *         builder.uniform("cutoff") = sinf(fSecs) * 0.55f + 0.5f;
   *         builder.uniform("slope")  = 10.0f;
   *
   *         builder.child("before_map")    = fBefore;
   *         builder.child("after_map")     = fAfter;
   *         builder.child("threshold_map") = fThreshold;
   *
   *         SkPaint paint;
   *         paint.setShader(builder.makeShader());
   *         canvas->drawRect({0, 0, 256, 256}, paint);
   *
   *         auto draw = [&](SkScalar x, SkScalar y, sk_sp<SkShader> shader) {
   *             paint.setShader(shader);
   *             canvas->save();
   *             canvas->translate(x, y);
   *             canvas->drawRect({0, 0, 256, 256}, paint);
   *             canvas->restore();
   *         };
   *         draw(256,   0, fThreshold);
   *         draw(  0, 256, fBefore);
   *         draw(256, 256, fAfter);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
