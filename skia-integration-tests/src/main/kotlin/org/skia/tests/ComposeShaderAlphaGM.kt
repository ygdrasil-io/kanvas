package org.skia.tests

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.core.withSave
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkShaders
import org.skia.foundation.SkTileMode

/**
 * Port of upstream Skia `gm/composeshader.cpp::ComposeShaderAlphaGM`.
 *
 * Draws two rows of the same composed gradient shader under decreasing
 * outer paint alpha. The first row uses `kDstIn`, the second `kSrcOver`.
 * Each cell paints an opaque green background first, then overlays the
 * composed shader with alpha `0xff, 0xd7, ...`.
 */
public class ComposeShaderAlphaGM : GM() {
    override fun getName(): String = "composeshader_alpha"
    override fun getISize(): SkISize = SkISize.Make(750, 220)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val shaders = arrayOf(
            makeShader(SkBlendMode.kDstIn),
            makeShader(SkBlendMode.kSrcOver),
        )
        val paint = SkPaint().apply { color = SK_ColorGREEN }
        val r = SkRect.MakeXYWH(5f, 5f, 100f, 100f)

        for (shader in shaders) {
            c.withSave {
                var alpha = 0xFF
                while (alpha > 0) {
                    paint.alphaf = 1f
                    paint.shader = null
                    drawRect(r, paint)

                    paint.alpha = alpha
                    paint.shader = shader
                    drawRect(r, paint)

                    translate(r.width() + 5f, 0f)
                    alpha -= 0x28
                }
            }
            c.translate(0f, r.height() + 5f)
        }
    }

    private fun makeShader(mode: SkBlendMode): SkShader {
        val shaderA = SkLinearGradient.Make(
            p0 = SkPoint.Make(0f, 0f),
            p1 = SkPoint.Make(100f, 0f),
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val shaderB = SkLinearGradient.Make(
            p0 = SkPoint.Make(0f, 0f),
            p1 = SkPoint.Make(0f, 100f),
            colors = intArrayOf(SK_ColorBLACK, SkColorSetARGB(0x80, 0, 0, 0)),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        return SkShaders.Blend(mode, shaderA, shaderB)
    }
}
