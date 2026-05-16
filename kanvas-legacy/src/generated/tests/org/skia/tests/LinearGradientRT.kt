package org.skia.tests

import org.skia.core.SkCanvas

/**
 * C++ original:
 * ```cpp
 * class LinearGradientRT : public RuntimeShaderGM {
 * public:
 *     LinearGradientRT() : RuntimeShaderGM("linear_gradient_rt", {256 + 10, 128 + 15}, R"(
 *         layout(color) uniform vec4 in_colors0;
 *         layout(color) uniform vec4 in_colors1;
 *
 *         vec4 main(vec2 p) {
 *             float t = p.x / 256;
 *             if (p.y < 32) {
 *                 return mix(in_colors0, in_colors1, t);
 *             } else {
 *                 vec3 linColor0 = toLinearSrgb(in_colors0.rgb);
 *                 vec3 linColor1 = toLinearSrgb(in_colors1.rgb);
 *                 vec3 linColor = mix(linColor0, linColor1, t);
 *                 return fromLinearSrgb(linColor).rgb1;
 *             }
 *         }
 *     )") {}
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         // Colors chosen to use values other than 0 and 1 - so that it's obvious if the conversion
 *         // intrinsics are doing anything. (Most transfer functions map 0 -> 0 and 1 -> 1).
 *         SkRuntimeShaderBuilder builder(fEffect);
 *         builder.uniform("in_colors0") = SkColor4f{0.75f, 0.25f, 0.0f, 1.0f};
 *         builder.uniform("in_colors1") = SkColor4f{0.0f, 0.75f, 0.25f, 1.0f};
 *         SkPaint paint;
 *         paint.setShader(builder.makeShader());
 *
 *         canvas->save();
 *         canvas->clear(SK_ColorWHITE);
 *         canvas->translate(5, 5);
 *
 *         // We draw everything twice. First to a surface with no color management, where the
 *         // intrinsics should do nothing (eg, the top bar should look the same in the top and bottom
 *         // halves). Then to an sRGB surface, where they should produce linearly interpolated
 *         // gradients (the bottom half of the second bar should be brighter than the top half).
 *         for (auto cs : {static_cast<SkColorSpace*>(nullptr), sk_srgb_singleton()}) {
 *             SkImageInfo info = SkImageInfo::Make(
 *                     256, 64, kN32_SkColorType, kPremul_SkAlphaType, sk_ref_sp(cs));
 *             auto surface = canvas->makeSurface(info);
 *             if (!surface) {
 *                 surface = SkSurfaces::Raster(info);
 *             }
 *
 *             surface->getCanvas()->drawRect({0, 0, 256, 64}, paint);
 *             canvas->drawImage(surface->makeImageSnapshot(), 0, 0);
 *             canvas->translate(0, 64 + 5);
 *         }
 *
 *         canvas->restore();
 *     }
 * }
 * ```
 */
public open class LinearGradientRT public constructor() : RuntimeShaderGM(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         // Colors chosen to use values other than 0 and 1 - so that it's obvious if the conversion
   *         // intrinsics are doing anything. (Most transfer functions map 0 -> 0 and 1 -> 1).
   *         SkRuntimeShaderBuilder builder(fEffect);
   *         builder.uniform("in_colors0") = SkColor4f{0.75f, 0.25f, 0.0f, 1.0f};
   *         builder.uniform("in_colors1") = SkColor4f{0.0f, 0.75f, 0.25f, 1.0f};
   *         SkPaint paint;
   *         paint.setShader(builder.makeShader());
   *
   *         canvas->save();
   *         canvas->clear(SK_ColorWHITE);
   *         canvas->translate(5, 5);
   *
   *         // We draw everything twice. First to a surface with no color management, where the
   *         // intrinsics should do nothing (eg, the top bar should look the same in the top and bottom
   *         // halves). Then to an sRGB surface, where they should produce linearly interpolated
   *         // gradients (the bottom half of the second bar should be brighter than the top half).
   *         for (auto cs : {static_cast<SkColorSpace*>(nullptr), sk_srgb_singleton()}) {
   *             SkImageInfo info = SkImageInfo::Make(
   *                     256, 64, kN32_SkColorType, kPremul_SkAlphaType, sk_ref_sp(cs));
   *             auto surface = canvas->makeSurface(info);
   *             if (!surface) {
   *                 surface = SkSurfaces::Raster(info);
   *             }
   *
   *             surface->getCanvas()->drawRect({0, 0, 256, 64}, paint);
   *             canvas->drawImage(surface->makeImageSnapshot(), 0, 0);
   *             canvas->translate(0, 64 + 5);
   *         }
   *
   *         canvas->restore();
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
