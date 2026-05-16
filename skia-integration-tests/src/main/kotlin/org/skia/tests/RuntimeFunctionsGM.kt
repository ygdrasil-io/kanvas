package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.effects.SkBuiltinSpecialisedEffects
import org.skia.foundation.SkData
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Port of Skia's `gm/runtimefunctions.cpp::RuntimeFunctions` GM.
 *
 * Renders a `@notargs` procedural pattern via [SkRuntimeEffect]. The
 * SkSL declares a `f(vec3)` helper that the entry-point iterates 32
 * times to march a 3D point along a viewing direction ; the
 * accumulated point's `sin(...) + vec3(2,5,9)` is normalised by
 * its length to produce the RGB output.
 *
 * **kanvas-skia adaptation** : we register
 * [SkBuiltinSpecialisedEffects.RuntimeFunctionsShaderImpl] under the
 * upstream SkSL source so [SkRuntimeEffect.MakeForShader] resolves the
 * hand-ported Kotlin equivalent of the SkSL math. The single uniform
 * `iResolution` is half4 ; we marshal the upstream `SkV4 { 255, 255,
 * 0, 0 }` via [SkData.MakeWithCopy] to keep the wire-format identical.
 * Upstream applies a `SkMatrix::Rotate(90, 128, 128)` local matrix on
 * the shader, which we pass through to [SkRuntimeEffect.makeShader] —
 * the deviceToLocal chain in [org.skia.effects.runtime.SkRuntimeShader]
 * honours it.
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
 */
public class RuntimeFunctionsGM : GM() {

    override fun getName(): String = "runtimefunctions"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val result = SkRuntimeEffect.MakeForShader(
            SkBuiltinSpecialisedEffects.RUNTIME_FUNCTIONS_SHADER_SKSL,
        )
        val effect = result.effect
            ?: error("Failed to compile runtimefunctions shader : ${result.errorText}")

        // Upstream's SkMatrix::setRotate(90, 128, 128) — rotate +90deg
        // around (128, 128). The local matrix is applied to shader
        // coords, so the procedural pattern rotates clockwise about
        // the canvas centre.
        val localM = SkMatrix.MakeRotate(90f, 128f, 128f)

        // SkV4 iResolution = { 255, 255, 0, 0 } — 16 bytes, four
        // floats in native-endian order (upstream writes the raw
        // SkV4 struct, which is 4 × float32). Our impl reads only
        // iResolution.y (offset 4), but we faithfully pack all four
        // components so the uniforms buffer matches the upstream
        // wire-format byte-for-byte.
        val bytes = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder()).apply {
            putFloat(255f)
            putFloat(255f)
            putFloat(0f)
            putFloat(0f)
        }.array()
        val uniforms = SkData.MakeWithCopy(bytes)

        val shader = effect.makeShader(uniforms, emptyArray(), localM)
            ?: error("SkRuntimeEffect.makeShader returned null")

        val paint = SkPaint().apply { this.shader = shader }
        c.drawRect(SkRect.MakeLTRB(0f, 0f, 256f, 256f), paint)
    }
}
