package org.graphiks.kanvas.skia.gm.text

import kotlin.math.ceil
import kotlin.math.max
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.FontEdging
import org.graphiks.kanvas.text.FontHinting
import org.graphiks.kanvas.text.Typeface
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Point

/** Port of Skia's `gm/typeface.cpp` (typeface rendering variant).
 *  Draws glyph shapes rendered from typefaces across multiple sizes
 *  to test typeface rendering accuracy.
 *  @see https://github.com/google/skia/blob/main/gm/typeface.cpp
 */
class TypefaceRenderingGm : SkiaGm {
    override val name = "typefacerendering"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 80.0
    override val requiresZeroRefusals = true
    override val width = 640
    override val height = 840
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val typeface = requireNotNull(Typefaces.fromResource("fonts/hintgasp.ttf"))
        drawTypefaceRendering(canvas, typeface, typeface.glyphIdForCodepoint('A'.code))

        // Kept from the original GM: an invalid glyph must not emit an outline.
        drawTypefaceRendering(canvas, typeface, 0xffff)
    }

    private fun drawTypefaceRendering(canvas: GmCanvas, typeface: Typeface, glyphId: Int) {
        val aliasTypes = listOf(
            AliasType(FontEdging.ALIAS, inLayer = false),
            AliasType(FontEdging.ANTI_ALIAS, inLayer = false),
            AliasType(FontEdging.SUBPIXEL_ANTI_ALIAS, inLayer = false),
            AliasType(FontEdging.ANTI_ALIAS, inLayer = true),
            AliasType(FontEdging.SUBPIXEL_ANTI_ALIAS, inLayer = true),
        )
        val textSizes = listOf(9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f)
        val hintingTypes = FontHinting.entries
        val subpixelTypes = listOf(
            SubpixelType(requested = false, offsetX = 0f, offsetY = 0f),
            SubpixelType(requested = true, offsetX = 0f, offsetY = 0f),
            SubpixelType(requested = true, offsetX = 0.25f, offsetY = 0f),
            SubpixelType(requested = true, offsetX = 0.25f, offsetY = 0.25f),
        )

        var y = 0f
        var xMax = 0f
        var xBase = 0f
        for (subpixel in subpixelTypes) {
            y = 0f
            for (alias in aliasTypes) {
                if (alias.inLayer) canvas.saveLayer()
                for (textSize in textSizes) {
                    var x = xBase + 5f
                    val baseFont = font(typeface, textSize, alias, subpixel.requested, FontHinting.NORMAL)
                    y += lineHeight(baseFont)
                    for (hinting in hintingTypes) {
                        for (rotateABit in listOf(false, true)) {
                            val currentFont = baseFont.copy(hinting = hinting)
                            if (rotateABit) {
                                canvas.save()
                                canvas.translate(x + subpixel.offsetX, y + subpixel.offsetY)
                                canvas.rotate(2f)
                                canvas.translate(-x - subpixel.offsetX, -y - subpixel.offsetY)
                            }
                            canvas.drawGlyphs(
                                listOf(glyphId),
                                listOf(Point(x + subpixel.offsetX, y + subpixel.offsetY)),
                                currentFont,
                                Paint(),
                            )
                            if (rotateABit) canvas.restore()
                            x += ceil(currentFont.typeface.getAdvance(glyphId, currentFont.size)).toFloat() + 5f
                            xMax = max(x, xMax)
                        }
                    }
                }
                if (alias.inLayer) canvas.restore()
                y += 10f
            }
            xBase = xMax
        }

        val styleTypes = listOf(
            StyleType(PaintStyle.FILL, 0f),
            StyleType(PaintStyle.STROKE, 0f),
            StyleType(PaintStyle.STROKE, 0.5f),
            StyleType(PaintStyle.STROKE_AND_FILL, 1f),
        )
        for (fakeBold in listOf(false, true)) {
            val baseFont = font(typeface, 16f, aliasTypes.first(), false, FontHinting.NORMAL)
                .copy(isEmbolden = fakeBold)
            y += lineHeight(baseFont)
            var x = 5f
            for (alias in aliasTypes) {
                if (alias.inLayer) canvas.saveLayer()
                val currentFont = baseFont.copy(
                    antiAlias = alias.edging != FontEdging.ALIAS,
                    edging = alias.edging,
                )
                for (style in styleTypes) {
                    canvas.drawGlyphs(
                        listOf(glyphId),
                        listOf(Point(x, y)),
                        currentFont,
                        Paint(style = style.style, strokeWidth = style.strokeWidth),
                    )
                    x += ceil(currentFont.typeface.getAdvance(glyphId, currentFont.size)).toFloat() + 5f
                }
                if (alias.inLayer) canvas.restore()
            }
            y += 10f
        }

        val maskTypes = listOf(
            MaskType(BlurStyle.NORMAL, 0f), MaskType(BlurStyle.SOLID, 0f),
            MaskType(BlurStyle.OUTER, 0f), MaskType(BlurStyle.INNER, 0f),
            MaskType(BlurStyle.NORMAL, 0.5f), MaskType(BlurStyle.SOLID, 0.5f),
            MaskType(BlurStyle.OUTER, 0.5f), MaskType(BlurStyle.INNER, 0.5f),
            MaskType(BlurStyle.NORMAL, 2f), MaskType(BlurStyle.SOLID, 2f),
            MaskType(BlurStyle.OUTER, 2f), MaskType(BlurStyle.INNER, 2f),
        )
        for (alias in aliasTypes) {
            val currentFont = font(typeface, 16f, alias, false, FontHinting.NORMAL)
            y += lineHeight(currentFont)
            var x = 5f
            if (alias.inLayer) canvas.saveLayer()
            for (mask in maskTypes) {
                canvas.drawGlyphs(
                    listOf(glyphId),
                    listOf(Point(x, y)),
                    currentFont,
                    Paint(maskFilter = MaskFilter.Blur(mask.style, mask.sigma)),
                )
                x += ceil(currentFont.typeface.getAdvance(glyphId, currentFont.size)).toFloat() + 5f
            }
            if (alias.inLayer) canvas.restore()
        }
    }

    private fun font(
        typeface: Typeface,
        size: Float,
        alias: AliasType,
        subpixel: Boolean,
        hinting: FontHinting,
    ): Font = Font(
        typeface = typeface,
        size = size,
        antiAlias = alias.edging != FontEdging.ALIAS,
        subpixel = subpixel,
        edging = alias.edging,
        hinting = hinting,
        embeddedBitmaps = true,
    )

    private fun lineHeight(font: Font): Float {
        val metrics = font.getMetrics()
        return ceil(metrics?.let { it.ascent - it.descent + it.leading } ?: font.size).toFloat()
    }

    private data class AliasType(val edging: FontEdging, val inLayer: Boolean)
    private data class SubpixelType(val requested: Boolean, val offsetX: Float, val offsetY: Float)
    private data class StyleType(val style: PaintStyle, val strokeWidth: Float)
    private data class MaskType(val style: BlurStyle, val sigma: Float)
}

class TypefaceRenderingPfaGm : SkiaGm {
    override val name = "typefacerendering_pfa"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 840
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
    }
}

class TypefaceRenderingPfbGm : SkiaGm {
    override val name = "typefacerendering_pfb"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 840
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
    }
}
