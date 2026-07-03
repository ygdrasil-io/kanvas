package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.RuntimeFunctionsWgsl
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/runtimefunctions.cpp`.
 *
 * Renders a `@notargs` procedural pattern via a runtime effect. The
 * WGSL declares a helper that the entry-point iterates 32
 * times to march a 3D point along a viewing direction ; the
 * accumulated point's `sin(...) + vec3(2,5,9)` is normalised by
 * its length to produce the RGB output.
 *
 * C++ original (`gm/runtimefunctions.cpp:39-61`):
 * ```cpp
 * class RuntimeFunctions : public skiagm::GM {
 *     bool runAsBench() const override { return true; }
 *     SkString getName() const override { return SkString("runtimefunctions"); }
 *     SkISize getISize() override { return {256, 256}; }
 *     void onDraw(SkCanvas* canvas) override {
 *         SkRuntimeEffect::Result result =
 *                 SkRuntimeEffect::MakeForShader(SkString(RUNTIME_FUNCTIONS_SRC));
 *         SkASSERTF(result.effect, "%s", result.errorText.c_str());
 *         SkMatrix localM;
 *         localM.setRotate(90, 128, 128);
 *         SkV4 iResolution = { 255, 255, 0, 0 };
 *         auto shader = result.effect->makeShader(
 *                 SkData::MakeWithCopy(&iResolution, sizeof(iResolution)), nullptr, 0, &localM);
 *         SkPaint p;
 *         p.setShader(std::move(shader));
 *         canvas->drawRect({0, 0, 256, 256}, p);
 *     }
 * };
 * DEF_GM(return new RuntimeFunctions;)
 * ```
 * @see https://github.com/google/skia/blob/main/gm/runtimefunctions.cpp
 */
class RuntimeFunctionsGm : SkiaGm {
    override val name = "runtimefunctions"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 10.25848388671875
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(RuntimeFunctionsWgsl).getOrThrow()
        val shader = effect.makeShader(UniformBlock { })
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(shader = shader))
    }
}
