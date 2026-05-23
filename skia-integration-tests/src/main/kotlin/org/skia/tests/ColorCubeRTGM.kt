package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectBuilder
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsColorCube
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/runtimeshader.cpp::ColorCubeRT` (`color_cube_rt` GM,
 * 512 × 512).
 *
 * Uses a 3-D look-up-table colour transform in a runtime **shader** that reads
 * source pixels from a `child` shader and the LUT from a `color_cube` shader.
 *
 * Draws four quadrants:
 *  - (0, 0)    : `mandrill_256.png` unmodified.
 *  - (0, 256)  : `mandrill_sepia.png` (Photoshop sepia reference).
 *  - (256, 0)  : mandrill through the *identity* cube — must look like the original.
 *  - (256, 256): mandrill through the *sepia* cube — must match the sepia reference.
 *
 * **Assets** : `mandrill_256.png`, `mandrill_sepia.png`, `lut_identity.png`,
 * `lut_sepia.png` via [ToolUtils.GetResourceAsImage]. If absent, synthetic
 * solid-colour images substitute so the test can still run.
 *
 * **SkSL impl** : [SkBuiltinShaderEffectsColorCube.COLOR_CUBE_RT_SKSL] /
 * [SkBuiltinShaderEffectsColorCube.ColorCubeRTImpl].
 *
 * C++ original: `gm/runtimeshader.cpp:269-349`.
 */
public class ColorCubeRTGM : GM() {

    init {
        SkBuiltinShaderEffectsColorCube
    }

    override fun getName(): String = "color_cube_rt"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    private val effect: SkRuntimeEffect by lazy {
        val res = SkRuntimeEffect.MakeForShader(SkBuiltinShaderEffectsColorCube.COLOR_CUBE_RT_SKSL)
        requireNotNull(res.effect) { "ColorCubeRTGM SkSL failed: ${res.errorText}" }
    }

    private fun loadOrSolid(path: String, color: Int): SkImage {
        return ToolUtils.GetResourceAsImage(path) ?: run {
            val bmp = SkBitmap(256, 256)
            for (y in 0 until 256) for (x in 0 until 256) bmp.setPixel(x, y, color)
            bmp.asImage()
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val mandrill = loadOrSolid("images/mandrill_256.png", 0xFF8B4513.toInt())
        val mandrillSepia = loadOrSolid("images/mandrill_sepia.png", 0xFFD2A679.toInt())
        val identityCube = loadOrSolid("images/lut_identity.png", 0xFF888888.toInt())
        val sepiaCube = loadOrSolid("images/lut_sepia.png", 0xFFBBA080.toInt())

        // Draw unmodified and Photoshop-sepia reference on the left.
        c.drawImage(mandrill, 0f, 0f, SkSamplingOptions.Default, null)
        c.drawImage(mandrillSepia, 0f, 256f, SkSamplingOptions.Default, null)

        val kSize = 16.0f
        val sampling = SkSamplingOptions(SkFilterMode.kLinear)
        // LUT normalization: each LUT image is (kSize*kSize) wide, kSize tall.
        val normalize = SkMatrix.MakeScale(1.0f / (kSize * kSize), 1.0f / kSize)

        val builder = SkRuntimeEffectBuilder(effect)
        builder.uniform("rg_scale").set((kSize - 1f) / kSize)
        builder.uniform("rg_bias").set(0.5f / kSize)
        builder.uniform("b_scale").set(kSize - 1f)
        builder.uniform("inv_size").set(1.0f / kSize)
        builder.child("child").set(mandrill.makeShader(sampling))

        val paint = SkPaint()

        // Identity cube: should look like the original mandrill.
        builder.child("color_cube").set(identityCube.makeShader(sampling, normalize))
        paint.shader = builder.makeShader()
        c.save()
        c.translate(256f, 0f)
        c.drawRect(SkRect.MakeLTRB(0f, 0f, 256f, 256f), paint)
        c.restore()

        // Sepia cube: should match the sepia reference.
        builder.child("color_cube").set(sepiaCube.makeShader(sampling, normalize))
        paint.shader = builder.makeShader()
        c.save()
        c.translate(256f, 256f)
        c.drawRect(SkRect.MakeLTRB(0f, 0f, 256f, 256f), paint)
        c.restore()
    }
}
