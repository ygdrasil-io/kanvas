package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectBuilder
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsChildren
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/runtimeshader.cpp::UnsharpRT` (`unsharp_rt` GM,
 * 512 × 256).
 *
 * Left half: the original `mandrill_256.png` image drawn unmodified.
 * Right half: the same image sharpened via a 5-tap unsharp-mask
 * runtime shader (centre × 5 minus the four 4-connected neighbours).
 *
 * The shader exercises the feature tested by skbug.com/40042955 —
 * that sampling a child with *both* unmodified and explicit coordinates
 * in the same program works correctly.
 *
 * **Asset** : loads `images/mandrill_256.png` via [ToolUtils.GetResourceAsImage].
 * Falls back to a synthetic 256 × 256 gradient if the file is absent so
 * the test can still run without the bundled Skia resources.
 *
 * **SkSL impl** : [SkBuiltinShaderEffectsChildren.UNSHARP_RT_SKSL] /
 * [SkBuiltinShaderEffectsChildren.UnsharpRTImpl].
 *
 * C++ original: `gm/runtimeshader.cpp:231-267`.
 */
public class UnsharpRTGM : GM() {

    init {
        SkBuiltinShaderEffectsChildren
    }

    override fun getName(): String = "unsharp_rt"
    override fun getISize(): SkISize = SkISize.Make(512, 256)

    private val effect: SkRuntimeEffect by lazy {
        val res = SkRuntimeEffect.MakeForShader(SkBuiltinShaderEffectsChildren.UNSHARP_RT_SKSL)
        requireNotNull(res.effect) { "UnsharpRTGM SkSL failed: ${res.errorText}" }
    }

    private fun makeMandrillImage() =
        ToolUtils.GetResourceAsImage("images/mandrill_256.png") ?: syntheticMandrill()

    private fun syntheticMandrill(): SkImage {
        val bmp = SkBitmap(256, 256)
        for (y in 0 until 256) {
            for (x in 0 until 256) {
                val r = x and 0xFF
                val g = y and 0xFF
                val b = ((x + y) / 2) and 0xFF
                bmp.setPixel(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        return bmp.asImage()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val mandrill = makeMandrillImage()

        // Left: unmodified image.
        c.drawImage(mandrill, 0f, 0f, SkSamplingOptions.Default, null)

        // Right: unsharp-masked via the runtime shader.
        val sampling = SkSamplingOptions(SkFilterMode.kNearest)
        val childShader = mandrill.makeShader(sampling)

        val builder = SkRuntimeEffectBuilder(effect)
        builder.child("child").set(childShader)

        val paint = SkPaint()
        paint.shader = builder.makeShader()
        c.save()
        c.translate(256f, 0f)
        c.drawRect(SkRect.MakeLTRB(0f, 0f, 256f, 256f), paint)
        c.restore()
    }
}
