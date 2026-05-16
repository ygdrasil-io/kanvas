package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorGRAY
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkFont
import org.skia.foundation.SkMaskFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/samplerstress.cpp::SamplerStressGM`
 * (`gpusamplerstress`, 640 × 480).
 *
 * Originally a GPU-sampler stress test: render one large textured
 * glyph (`"M"` at 72 px) clipped by a small AA round-rect, with both
 * a repeating-tile bitmap shader (red/green stripes on black) and a
 * normal-blur mask filter (σ = 1 px). The point upstream is to fan
 * out the GPU's texture samplers — the texture shader, the
 * glyph-mask sampler, the blur kernel, and the AA clip mask — all
 * within a single draw call.
 *
 * On the kanvas-skia raster pipeline we exercise:
 *  - [SkBitmap.makeShader] with [SkTileMode.kRepeat] for the
 *    striped texture.
 *  - [SkBlurMaskFilter] with [SkBlurStyle.kNormal] (σ = 1).
 *  - [SkCanvas.clipPath] with `doAntiAlias = true` on a round-rect
 *    path.
 *  - The text fill pipeline via [SkCanvas.drawString].
 *
 * Two outline overlays follow the masked draw — a thin black "M" and
 * a grey round-rect — so the resulting cell shows the texture-tinted
 * glyph fragment that survived the clip, with the masked-out region
 * still visible in skeleton form.
 *
 * Stripe layout: black background, red row every 5th line, green
 * column every 7th line (rows beat columns when both match).
 */
public class SamplerStressGM : GM() {

    private val texture: SkBitmap = SkBitmap(16, 16)
    private var textureReady: Boolean = false
    private var shader: SkShader? = null
    private var maskFilter: SkMaskFilter? = null

    override fun getName(): String = "gpusamplerstress"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    private fun createTexture() {
        if (textureReady) return
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val c = when {
                    y % 5 == 0 -> SK_ColorRED
                    x % 7 == 0 -> SK_ColorGREEN
                    else -> SK_ColorBLACK
                }
                texture.setPixel(x, y, c)
            }
        }
        textureReady = true
    }

    private fun createShader() {
        if (shader != null) return
        createTexture()
        shader = texture.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat)
    }

    private fun createMaskFilter() {
        if (maskFilter != null) return
        maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 1f)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        createShader()
        createMaskFilter()

        c.save()

        val paint = SkPaint().apply {
            isAntiAlias = true
            shader = this@SamplerStressGM.shader
            maskFilter = this@SamplerStressGM.maskFilter
        }
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 72f)

        val temp = SkRect.MakeLTRB(115f, 75f, 144f, 110f)
        val path = SkPath.RRect(SkRRect.MakeRectXY(temp, 5f, 5f))

        c.clipPath(path, doAntiAlias = true)
        c.drawString("M", 100f, 100f, font, paint)

        c.restore()

        // Outline overlays for visual landmarks.
        val outline = SkPaint().apply {
            color = SK_ColorBLACK
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 1f
        }
        c.drawString("M", 100f, 100f, font, outline)

        outline.color = SK_ColorGRAY
        c.drawPath(path, outline)
    }
}
