package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/coloremoji.cpp` (COLRv0 variant only).
 *
 * Renders the emoji sample text ("\uD83D\uDE00 \u2662" — 😀 ♢) through
 * an emoji-capable typeface in four sweeps:
 *
 * 1. **Size grid** — 3 point sizes (10, 30, 50) at fixed spacing.
 * 2. **Big glyph** — single 256-pt draw to overflow one glyph-cache plot.
 * 3. **Shader / filter grid** — 16-cell cartesian product of
 *    `{linear shader, blur filter, grayscale filter, lighting color filter}`
 *    to validate colour-emoji glyphs through paint-side filter slots.
 * 4. **Clip test** — 4 clip rects (full bounds, upper-left quadrant,
 *    lower-right quadrant, interior inset) with alpha preview + opaque
 *    clipped re-draw.
 *
 * ## Adaptation notes
 *
 * Real emoji typeface dispatch (`EmojiTypeface.create`) is gated behind
 * `STUB.EMOJI_TABLES`. The GM falls back to Liberation Sans.
 *
 * Kanvas [Font] does not expose `setEmbolden`, `getMetrics`, or
 * `measureText` with bounds output. The GM simplifies:
 * - Fake-bold loop skipped.
 * - Fixed vertical spacing replaces metrics-driven layout.
 * - Text bounds approximated via [Font.measureText] + `font.size`.
 * - Separate paint `alpha` field not available; the per-cell alpha
 *   loop is skipped (16 cells instead of 32).
 *
 * Only the COLRv0 variant is registered. CBDT, Sbix, and SVG variants
 * are left in `skia-integration-tests/` pending real emoji font
 * dispatch.
 *
 * @see https://github.com/google/skia/blob/main/gm/coloremoji.cpp
 */
class ColorEmojiGm : SkiaGm {
    override val name = "coloremoji_colrv0"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 650
    override val height = 1200

    private val fallbackTypeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!
    private val sampleText = "\uD83D\uDE00 \u2662"

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.5f, 0.5f, 0.5f)

        val font = Font(fallbackTypeface, antiAlias = false, subpixel = true)
        val text = sampleText

        // ── (1) size grid ──────────────────────────────────────
        val textSizes = floatArrayOf(10f, 30f, 50f)
        var y = 0f
        for (textSize in textSizes) {
            val f = font.copy(size = textSize)
            y += textSize * 0.8f
            canvas.drawSimpleText(text, 10f, y, f, Paint())
            y += textSize * 0.7f
        }

        // ── (2) one 256-pt glyph to overflow a Plot ─────────────
        val bigFont = font.copy(size = 256f)
        canvas.drawSimpleText(text, 190f, 256f * 0.8f, bigFont, Paint())

        y += 20f
        val savedY = y

        // ── (3) shader + filter grid ────────────────────────────
        for (makeLinear in 0..1) {
            for (makeBlur in 0..1) {
                for (makeGray in 0..1) {
                    for (makeMode in 0..1) {
                        val gridFont = font.copy(size = 30f)
                        var gridPaint = Paint()

                        if (makeLinear != 0) {
                            gridPaint = gridPaint.copy(shader = makeLinear())
                        }

                        if (makeBlur != 0 && makeGray != 0) {
                            val grayScale = makeGrayscale(null)
                            val blur = makeBlur(3f, grayScale)
                            gridPaint = gridPaint.copy(imageFilter = blur)
                        } else if (makeBlur != 0) {
                            gridPaint = gridPaint.copy(imageFilter = makeBlur(3f, null))
                        } else if (makeGray != 0) {
                            gridPaint = gridPaint.copy(imageFilter = makeGrayscale(null))
                        }
                        if (makeMode != 0) {
                            gridPaint = gridPaint.copy(colorFilter = makeColorFilter())
                        }

                        y += 30f * 0.8f
                        canvas.drawSimpleText(text, 380f, y, gridFont, gridPaint)
                        y += 30f * 0.7f
                    }
                }
            }
        }

        // ── (4) clip test ───────────────────────────────────────
        val clipFont = font.copy(size = 40f)
        val textWidth = clipFont.measureText(text)
        val textHeight = 40f // approximate — Kanvas Font has no getMetrics

        val bounds = Rect(0f, 0f, textWidth, textHeight)
        val boundsHalfWidth = bounds.width * 0.5f
        val boundsHalfHeight = bounds.height * 0.5f
        val boundsQuarterWidth = boundsHalfWidth * 0.5f
        val boundsQuarterHeight = boundsHalfHeight * 0.5f

        val upperLeftClip = Rect(
            bounds.left, bounds.top,
            bounds.left + boundsHalfWidth, bounds.top + boundsHalfHeight,
        )
        val lowerRightClip = Rect(
            bounds.left + boundsHalfWidth, bounds.top + boundsHalfHeight,
            bounds.right, bounds.bottom,
        )
        val interiorClip = Rect(
            bounds.left + boundsQuarterWidth, bounds.top + boundsQuarterHeight,
            bounds.right - boundsQuarterWidth, bounds.bottom - boundsQuarterHeight,
        )

        val clipRects = arrayOf(bounds, upperLeftClip, lowerRightClip, interiorClip)
        val clipHairline = Paint(color = Color.WHITE, style = PaintStyle.STROKE, strokeWidth = 1f)

        canvas.translate(10f, savedY)
        for (clipRect in clipRects) {
            canvas.translate(0f, textHeight)
            canvas.save()
            canvas.drawRect(clipRect, clipHairline)
            val dimTextPaint = Paint(color = Color.fromRGBA(0f, 0f, 0f, 0.125f))
            canvas.drawSimpleText(text, 0f, 0f, clipFont, dimTextPaint)
            canvas.clipRect(clipRect)
            canvas.drawSimpleText(text, 0f, 0f, clipFont, Paint())
            canvas.restore()
            canvas.translate(0f, 25f)
        }
    }

    // ─── helpers ─────────────────────────────────────────────────

    private fun makeLinear(): Shader = Shader.LinearGradient(
        start = Point(0f, 0f),
        end = Point(32f, 32f),
        stops = listOf(
            GradientStop(0f, Color.fromRGBA(0.5f, 0f, 0.5f, 0.5f)),
            GradientStop(0.5f, Color.fromRGBA(0.94f, 0.94f, 0f, 0.94f)),
            GradientStop(1f, Color.fromRGBA(0f, 0.5f, 0.94f, 0.5f)),
        ),
        tileMode = TileMode.CLAMP,
    )

    private fun makeGrayscale(input: ImageFilter?): ImageFilter? {
        val matrix = floatArrayOf(
            0.2126f, 0.7152f, 0.0722f, 0f, 0f,
            0.2126f, 0.7152f, 0.0722f, 0f, 0f,
            0.2126f, 0.7152f, 0.0722f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        return ImageFilter.ColorFilter(ColorFilter.Matrix(matrix), input)
    }

    private fun makeBlur(amount: Float, input: ImageFilter?): ImageFilter =
        ImageFilter.Blur(amount, amount, input = input)

    private fun makeColorFilter(): ColorFilter = ColorFilter.Lighting(
        mul = Color.fromRGBA(0f, 0.502f, 1f, 1f),
        add = Color.fromRGBA(1f, 0.125f, 0f, 1f),
    )
}
