package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class UnsharpRT : public RuntimeShaderGM {
 * public:
 *     UnsharpRT() : RuntimeShaderGM("unsharp_rt", {512, 256}, R"(
 *         uniform shader child;
 *         half4 main(float2 xy) {
 *             half4 c = child.eval(xy) * 5;
 *             c -= child.eval(xy + float2( 1,  0));
 *             c -= child.eval(xy + float2(-1,  0));
 *             c -= child.eval(xy + float2( 0,  1));
 *             c -= child.eval(xy + float2( 0, -1));
 *             return c;
 *         }
 *     )") {}
 *
 *     sk_sp<SkImage> fMandrill;
 *
 *     void onOnceBeforeDraw() override {
 *         fMandrill = ToolUtils::GetResourceAsImage("images/mandrill_256.png");
 *         this->RuntimeShaderGM::onOnceBeforeDraw();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         // First we draw the unmodified image
 *         canvas->drawImage(fMandrill,      0,   0);
 *
 *         // Now draw the image with our unsharp mask applied
 *         SkRuntimeShaderBuilder builder(fEffect);
 *         const SkSamplingOptions sampling(SkFilterMode::kNearest);
 *         builder.child("child") = fMandrill->makeShader(sampling);
 *
 *         SkPaint paint;
 *         paint.setShader(builder.makeShader());
 *         canvas->translate(256, 0);
 *         canvas->drawRect({ 0, 0, 256, 256 }, paint);
 *     }
 * }
 * ```
 */
public open class UnsharpRT public constructor() : RuntimeShaderGM(TODO(), TODO(), TODO()) {
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
   * void onOnceBeforeDraw() override {
   *         fMandrill = ToolUtils::GetResourceAsImage("images/mandrill_256.png");
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
   *         // First we draw the unmodified image
   *         canvas->drawImage(fMandrill,      0,   0);
   *
   *         // Now draw the image with our unsharp mask applied
   *         SkRuntimeShaderBuilder builder(fEffect);
   *         const SkSamplingOptions sampling(SkFilterMode::kNearest);
   *         builder.child("child") = fMandrill->makeShader(sampling);
   *
   *         SkPaint paint;
   *         paint.setShader(builder.makeShader());
   *         canvas->translate(256, 0);
   *         canvas->drawRect({ 0, 0, 256, 256 }, paint);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
