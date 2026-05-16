package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.SkHighContrastConfig
import org.skia.effects.SkHighContrastFilter
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkFont
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/highcontrastfilter.cpp::HighContrastFilterGM`.
 *
 * Lays out an 4 × 2 grid (200 × 200 cells, 800 × 420 canvas) of
 * "rect + text + gradients" mini-scenes filtered through every
 * combination of `(grayscale, invertStyle, contrast)` exercised by
 * upstream :
 *
 * | row 0 | NoInvert · 0 | InvertBrightness · 0 | InvertLightness · 0 | InvertLightness · 0.2 |
 * | row 1 | + grayscale on each of the four columns above |
 *
 * Each cell `saveLayer`s with the high-contrast colour filter so that
 * the filter applies to the composite of the rect / text / gradient
 * stack rather than to each draw individually.
 */
public class HighContrastFilterGM : GM() {

    private companion object {
        const val K_SIZE: Float = 200f
    }

    override fun getName(): String = "highcontrastfilter"

    override fun getISize(): SkISize = SkISize.Make(800, 420)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val configs = arrayOf(
            SkHighContrastConfig(false, SkHighContrastConfig.InvertStyle.kNoInvert,         0.0f),
            SkHighContrastConfig(false, SkHighContrastConfig.InvertStyle.kInvertBrightness, 0.0f),
            SkHighContrastConfig(false, SkHighContrastConfig.InvertStyle.kInvertLightness,  0.0f),
            SkHighContrastConfig(false, SkHighContrastConfig.InvertStyle.kInvertLightness,  0.2f),
            SkHighContrastConfig(true,  SkHighContrastConfig.InvertStyle.kNoInvert,         0.0f),
            SkHighContrastConfig(true,  SkHighContrastConfig.InvertStyle.kInvertBrightness, 0.0f),
            SkHighContrastConfig(true,  SkHighContrastConfig.InvertStyle.kInvertLightness,  0.0f),
            SkHighContrastConfig(true,  SkHighContrastConfig.InvertStyle.kInvertLightness,  0.2f),
        )

        for (i in configs.indices) {
            val x = K_SIZE * (i % 4)
            val y = K_SIZE * (i / 4)
            c.save()
            c.translate(x, y)
            c.scale(K_SIZE, K_SIZE)
            drawScene(c, configs[i])
            drawLabel(c, configs[i])
            c.restore()
        }
    }

    private fun drawScene(c: SkCanvas, config: SkHighContrastConfig) {
        val layerBounds = SkRect.MakeLTRB(0f, 0f, 1f, 1f)
        val xferPaint = SkPaint().apply {
            colorFilter = SkHighContrastFilter.Make(config)
        }
        c.saveLayer(layerBounds, xferPaint)

        val paint = SkPaint()
        paint.setARGB(0xff, 0x66, 0x11, 0x11)
        c.drawRect(SkRect.MakeLTRB(0.1f, 0.2f, 0.9f, 0.4f), paint)

        val font = ToolUtils.DefaultPortableFont().apply {
            size = 0.15f
            edging = SkFont.Edging.kAlias
        }

        paint.setARGB(0xff, 0xbb, 0x77, 0x77)
        c.drawString("A", 0.15f, 0.35f, font, paint)

        paint.setARGB(0xff, 0xcc, 0xcc, 0xff)
        c.drawRect(SkRect.MakeLTRB(0.1f, 0.8f, 0.9f, 1.0f), paint)

        paint.setARGB(0xff, 0x88, 0x88, 0xbb)
        c.drawString("Z", 0.75f, 0.95f, font, paint)

        // White → Black gradient.
        val pts = arrayOf(SkPoint(0f, 0f), SkPoint(1f, 0f))
        val pos = floatArrayOf(0.2f, 0.8f)
        paint.shader = SkLinearGradient.Make(
            pts[0], pts[1],
            intArrayOf(SK_ColorWHITE, SK_ColorBLACK),
            pos,
            SkTileMode.kClamp,
        )
        c.drawRect(SkRect.MakeLTRB(0.1f, 0.4f, 0.9f, 0.6f), paint)

        // Green → White gradient.
        paint.shader = SkLinearGradient.Make(
            pts[0], pts[1],
            intArrayOf(SK_ColorGREEN, SK_ColorWHITE),
            pos,
            SkTileMode.kClamp,
        )
        c.drawRect(SkRect.MakeLTRB(0.1f, 0.6f, 0.9f, 0.8f), paint)

        c.restore()
    }

    private fun drawLabel(c: SkCanvas, config: SkHighContrastConfig) {
        val invertStr = when (config.invertStyle) {
            SkHighContrastConfig.InvertStyle.kInvertBrightness -> "InvBright"
            SkHighContrastConfig.InvertStyle.kInvertLightness -> "InvLight"
            SkHighContrastConfig.InvertStyle.kNoInvert -> "NoInvert"
        }
        val grayPrefix = if (config.grayscale) "Gray " else ""
        val label = "${grayPrefix}${invertStr} contrast=${"%.1f".format(config.contrast)}"

        val font = ToolUtils.DefaultPortableFont().apply {
            size = 0.075f
            edging = SkFont.Edging.kAntiAlias
        }
        val width = font.measureText(label, label.length, SkTextEncoding.kUTF8, null)
        c.drawSimpleText(
            label,
            label.length,
            SkTextEncoding.kUTF8,
            0.5f - width / 2f,
            0.16f,
            font,
            SkPaint(),
        )
    }
}

