package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class ColorCubeRT : public RuntimeShaderGM {
 * public:
 *     ColorCubeRT() : RuntimeShaderGM("color_cube_rt", {512, 512}, R"(
 *         uniform shader child;
 *         uniform shader color_cube;
 *
 *         uniform float rg_scale;
 *         uniform float rg_bias;
 *         uniform float b_scale;
 *         uniform float inv_size;
 *
 *         half4 main(float2 xy) {
 *             float4 c = unpremul(child.eval(xy));
 *
 *             // Map to cube coords:
 *             float3 cubeCoords = float3(c.rg * rg_scale + rg_bias, c.b * b_scale);
 *
 *             // Compute slice coordinate
 *             float2 coords1 = float2((floor(cubeCoords.b) + cubeCoords.r) * inv_size, cubeCoords.g);
 *             float2 coords2 = float2(( ceil(cubeCoords.b) + cubeCoords.r) * inv_size, cubeCoords.g);
 *
 *             // Two bilinear fetches, plus a manual lerp for the third axis:
 *             half4 color = mix(color_cube.eval(coords1), color_cube.eval(coords2),
 *                               fract(cubeCoords.b));
 *
 *             // Premul again
 *             color.rgb *= color.a;
 *
 *             return color;
 *         }
 *     )") {}
 *
 *     sk_sp<SkImage> fMandrill, fMandrillSepia, fIdentityCube, fSepiaCube;
 *
 *     void onOnceBeforeDraw() override {
 *         fMandrill = ToolUtils::GetResourceAsImage("images/mandrill_256.png");
 *         fMandrillSepia = ToolUtils::GetResourceAsImage("images/mandrill_sepia.png");
 *         fIdentityCube = ToolUtils::GetResourceAsImage("images/lut_identity.png");
 *         fSepiaCube = ToolUtils::GetResourceAsImage("images/lut_sepia.png");
 *
 *         this->RuntimeShaderGM::onOnceBeforeDraw();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkRuntimeShaderBuilder builder(fEffect);
 *
 *         // First we draw the unmodified image, and a copy that was sepia-toned in Photoshop:
 *         canvas->drawImage(fMandrill,      0,   0);
 *         canvas->drawImage(fMandrillSepia, 0, 256);
 *
 *         // LUT dimensions should be (kSize^2, kSize)
 *         constexpr float kSize = 16.0f;
 *
 *         const SkSamplingOptions sampling(SkFilterMode::kLinear);
 *
 *         builder.uniform("rg_scale")     = (kSize - 1) / kSize;
 *         builder.uniform("rg_bias")      = 0.5f / kSize;
 *         builder.uniform("b_scale")      = kSize - 1;
 *         builder.uniform("inv_size")     = 1.0f / kSize;
 *
 *         builder.child("child")        = fMandrill->makeShader(sampling);
 *
 *         SkPaint paint;
 *
 *         // TODO: Should we add SkImage::makeNormalizedShader() to handle this automatically?
 *         SkMatrix normalize = SkMatrix::Scale(1.0f / (kSize * kSize), 1.0f / kSize);
 *
 *         // Now draw the image with an identity color cube - it should look like the original
 *         builder.child("color_cube") = fIdentityCube->makeShader(sampling, normalize);
 *         paint.setShader(builder.makeShader());
 *         canvas->translate(256, 0);
 *         canvas->drawRect({ 0, 0, 256, 256 }, paint);
 *
 *         // ... and with a sepia-tone color cube. This should match the sepia-toned image.
 *         builder.child("color_cube") = fSepiaCube->makeShader(sampling, normalize);
 *         paint.setShader(builder.makeShader());
 *         canvas->translate(0, 256);
 *         canvas->drawRect({ 0, 0, 256, 256 }, paint);
 *     }
 * }
 * ```
 */
public open class ColorCubeRT public constructor() : RuntimeShaderGM(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fMandrill
   * ```
   */
  public var fMandrill: SkSp<SkImage> = TODO("Initialize fMandrill")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fMandrill, fMandrillSepia
   * ```
   */
  public var fMandrillSepia: SkSp<SkImage> = TODO("Initialize fMandrillSepia")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fMandrill, fMandrillSepia, fIdentityCube
   * ```
   */
  public var fIdentityCube: SkSp<SkImage> = TODO("Initialize fIdentityCube")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fMandrill, fMandrillSepia, fIdentityCube, fSepiaCube
   * ```
   */
  public var fSepiaCube: SkSp<SkImage> = TODO("Initialize fSepiaCube")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fMandrill = ToolUtils::GetResourceAsImage("images/mandrill_256.png");
   *         fMandrillSepia = ToolUtils::GetResourceAsImage("images/mandrill_sepia.png");
   *         fIdentityCube = ToolUtils::GetResourceAsImage("images/lut_identity.png");
   *         fSepiaCube = ToolUtils::GetResourceAsImage("images/lut_sepia.png");
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
   *         // First we draw the unmodified image, and a copy that was sepia-toned in Photoshop:
   *         canvas->drawImage(fMandrill,      0,   0);
   *         canvas->drawImage(fMandrillSepia, 0, 256);
   *
   *         // LUT dimensions should be (kSize^2, kSize)
   *         constexpr float kSize = 16.0f;
   *
   *         const SkSamplingOptions sampling(SkFilterMode::kLinear);
   *
   *         builder.uniform("rg_scale")     = (kSize - 1) / kSize;
   *         builder.uniform("rg_bias")      = 0.5f / kSize;
   *         builder.uniform("b_scale")      = kSize - 1;
   *         builder.uniform("inv_size")     = 1.0f / kSize;
   *
   *         builder.child("child")        = fMandrill->makeShader(sampling);
   *
   *         SkPaint paint;
   *
   *         // TODO: Should we add SkImage::makeNormalizedShader() to handle this automatically?
   *         SkMatrix normalize = SkMatrix::Scale(1.0f / (kSize * kSize), 1.0f / kSize);
   *
   *         // Now draw the image with an identity color cube - it should look like the original
   *         builder.child("color_cube") = fIdentityCube->makeShader(sampling, normalize);
   *         paint.setShader(builder.makeShader());
   *         canvas->translate(256, 0);
   *         canvas->drawRect({ 0, 0, 256, 256 }, paint);
   *
   *         // ... and with a sepia-tone color cube. This should match the sepia-toned image.
   *         builder.child("color_cube") = fSepiaCube->makeShader(sampling, normalize);
   *         paint.setShader(builder.makeShader());
   *         canvas->translate(0, 256);
   *         canvas->drawRect({ 0, 0, 256, 256 }, paint);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
