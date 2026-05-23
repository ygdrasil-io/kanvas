package org.skia.tests

import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SkColorSetRGB
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkData
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkTypeface
import org.skia.foundation.emoji.EmojiTypeface
import org.skia.tools.ToolUtils

/**
 * Port of upstream Skia's [`gm/coloremoji.cpp::ColorEmojiGM`](
 *   https://github.com/google/skia/blob/main/gm/coloremoji.cpp).
 *
 * Renders a UTF-8 emoji sample text — `"\U0001F600 ♢"` (😀 ♢)
 * by default, `"abcdefghij"` for the upstream `SVG` variant — through
 * an emoji-capable [SkTypeface] in three sweeps :
 *
 *   1. A 6-row grid of `{ fakeBold ∈ {false, true} } × { size ∈ {10,
 *      30, 50} }` simple draws — exercises bold-emulation × size
 *      pairing on the bitmap-emit (CBDT / sbix) / layered (COLRv0) /
 *      SVG-in-OT pipelines.
 *   2. One big 256-pt draw to overflow a single GPU glyph-cache plot
 *      (the comment upstream specifically asks for "max out one Plot"
 *      — irrelevant for the raster port, but kept for fidelity).
 *   3. A 32-cell `{ linear shader, blur filter, grayscale filter,
 *      lighting color filter, alpha 0.5 } × {0,1}` cartesian product
 *      to validate that colour-emoji glyphs round-trip correctly
 *      through every paint-side filter slot.
 *   4. A 4-step "different clips" demo : full bounds, upper-left
 *      quadrant, lower-right quadrant, interior inset — each draws
 *      the text at α=0x20 then re-draws at α=1 under the clip, with
 *      a white hairline outlining the clip rectangle.
 *
 * Upstream declares 5 `DEF_GM` instances (`Cbdt`, `Sbix`, `ColrV0`,
 * `Svg`, `Test`). The Kotlin port mirrors the four format variants
 * that map onto our [EmojiTypeface.Format] dispatch surface (the
 * `Test` variant maps to `ToolUtils::CreatePortableTypeface("Emoji",
 * SkFontStyle())` upstream, which would require an `"Emoji"` family
 * registered in [ToolUtils.CreatePortableTypeface] — not currently
 * shipped). The no-arg constructor used by the JUnit harness defaults
 * to `COLRv0` (the canonical "active" colour-emoji format in 2024+
 * fonts).
 *
 * ## Port status
 *
 * Body fully ported against the live API surface — every call
 * compiles. At runtime [EmojiTypeface.create] throws
 * `STUB.EMOJI_TABLES` (see
 * [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md))
 * because the AWT scaler cannot decode SBIX / CBDT / COLRv0 / SVG
 * glyph tables — those need FreeType (or librsvg for SVG) via JNI.
 * The matching [ColorEmojiTest] is `@Disabled("STUB.EMOJI_TABLES")`
 * until that dispatch lands. Sibling [ColoremojiBlendmodesGM] holds
 * the same contract.
 */
public class ColorEmojiGM(
    private val format: EmojiTypeface.Format,
) : GM() {

    // No-arg constructor for the JUnit harness's `runGmTest(GM())`
    // pattern — defaults to the upstream `ColrV0` variant.
    public constructor() : this(EmojiTypeface.Format.COLRv0)

    /**
     * Mirrors upstream's `ToolUtils::EmojiTestSample { typeface, sampleText }`.
     * The typeface is resolved through the [EmojiTypeface.create] dispatch
     * — which throws `STUB.EMOJI_TABLES` until the FreeType / librsvg JNI
     * backend lands — so this `lateinit` is filled in [onOnceBeforeDraw].
     */
    private lateinit var emojiTypeface: SkTypeface
    private var sampleText: String = "😀 ♢"  // 😀 ♢ — matches upstream

    override fun onOnceBeforeDraw() {
        // Upstream:
        //   case Svg:
        //       sample.typeface = CreateTypefaceFromResource("fonts/SampleSVG.ttf");
        //       sample.sampleText = "abcdefghij";
        //       break;
        //   default:
        //       sample.typeface = CreateTypefaceFromResource("fonts/<format>.ttf");
        //       sample.sampleText = "😀 ♢";
        if (format == EmojiTypeface.Format.SVG) {
            sampleText = "abcdefghij"
        }
        // The dispatch throws `STUB.EMOJI_TABLES` for every format — keep
        // the call so the contract is exercised, matching the sibling
        // `ColoremojiBlendmodesGM` pattern.
        emojiTypeface = EmojiTypeface.create(format, SkData.MakeWithCopy(ByteArray(0)))
    }

    override fun getName(): String = "coloremoji_" + nameForFormat(format)
    override fun getISize(): SkISize = SkISize.Make(650, 1200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.drawColor(SK_ColorGRAY)

        val font = SkFont(emojiTypeface)
        val text = sampleText
        val textLen = text.toByteArray(Charsets.UTF_8).size

        // ── (1) draw text at different point sizes ─────────────────
        val textSizes = floatArrayOf(10f, 30f, 50f)
        val metrics = SkFontMetrics()
        var y = 0f
        for (fakeBold in booleanArrayOf(false, true)) {
            font.isEmbolden = fakeBold
            for (textSize in textSizes) {
                font.size = textSize
                font.getMetrics(metrics)
                y += -metrics.fAscent
                c.drawSimpleText(text, textLen, SkTextEncoding.kUTF8, 10f, y, font, SkPaint())
                y += metrics.fDescent + metrics.fLeading
            }
        }

        // ── (2) one more big one to max out one Plot ───────────────
        font.size = 256f
        font.getMetrics(metrics)
        c.drawSimpleText(text, textLen, SkTextEncoding.kUTF8, 190f, -metrics.fAscent, font, SkPaint())

        y += 20f
        val savedY = y

        // ── (3) draw with shaders and image filters ────────────────
        for (makeLinear in 0..1) {
            for (makeBlur in 0..1) {
                for (makeGray in 0..1) {
                    for (makeMode in 0..1) {
                        for (alpha in 0..1) {
                            val shaderFont = SkFont(font.typeface)
                            val shaderPaint = SkPaint()
                            if (makeLinear != 0) {
                                shaderPaint.shader = makeLinear()
                            }

                            if (makeBlur != 0 && makeGray != 0) {
                                val grayScale: SkImageFilter? = makeGrayscale(null)
                                val blur: SkImageFilter? = makeBlur(3f, grayScale)
                                shaderPaint.imageFilter = blur
                            } else if (makeBlur != 0) {
                                shaderPaint.imageFilter = makeBlur(3f, null)
                            } else if (makeGray != 0) {
                                shaderPaint.imageFilter = makeGrayscale(null)
                            }
                            if (makeMode != 0) {
                                shaderPaint.colorFilter = makeColorFilter()
                            }
                            if (alpha != 0) {
                                shaderPaint.alphaf = 0.5f
                            }
                            shaderFont.size = 30f
                            shaderFont.getMetrics(metrics)
                            y += -metrics.fAscent
                            c.drawSimpleText(
                                text, textLen, SkTextEncoding.kUTF8, 380f, y,
                                shaderFont, shaderPaint,
                            )
                            y += metrics.fDescent + metrics.fLeading
                        }
                    }
                }
            }
        }

        // ── (4) setup work needed to draw text with different clips ─
        c.translate(10f, savedY)
        font.size = 40f

        // compute the bounds of the text
        val bounds = SkRect.MakeEmpty()
        font.measureText(text, textLen, SkTextEncoding.kUTF8, bounds)

        val boundsHalfWidth = bounds.width() * 0.5f
        val boundsHalfHeight = bounds.height() * 0.5f
        val boundsQuarterWidth = boundsHalfWidth * 0.5f
        val boundsQuarterHeight = boundsHalfHeight * 0.5f

        val upperLeftClip = SkRect.MakeXYWH(
            bounds.left, bounds.top,
            boundsHalfWidth, boundsHalfHeight,
        )
        val lowerRightClip = SkRect.MakeXYWH(
            bounds.centerX(), bounds.centerY(),
            boundsHalfWidth, boundsHalfHeight,
        )
        val interiorClip = SkRect.MakeLTRB(bounds.left, bounds.top, bounds.right, bounds.bottom)
        interiorClip.inset(boundsQuarterWidth, boundsQuarterHeight)

        val clipRects = arrayOf(bounds, upperLeftClip, lowerRightClip, interiorClip)

        val clipHairline = SkPaint().apply {
            color = org.graphiks.math.SK_ColorWHITE
            style = SkPaint.Style.kStroke_Style
        }

        val paint = SkPaint()
        for (clipRect in clipRects) {
            c.translate(0f, bounds.height())
            c.save()
            c.drawRect(clipRect, clipHairline)
            paint.alpha = 0x20
            c.drawSimpleText(text, textLen, SkTextEncoding.kUTF8, 0f, 0f, font, paint)
            c.clipRect(clipRect)
            paint.alphaf = 1f
            c.drawSimpleText(text, textLen, SkTextEncoding.kUTF8, 0f, 0f, font, paint)
            c.restore()
            c.translate(0f, 25f)
        }
    }

    // ─── Upstream free-function helpers, scoped here as private members ────

    /**
     * Mirrors upstream's static `MakeLinear()` — a 3-stop linear gradient
     * (semi-transparent magenta → opaque yellow-ish → semi-transparent
     * cyan-ish) along `(0,0)→(32,32)` with [SkTileMode.kClamp]. Used as
     * a paint shader to verify gradient × colour-emoji compositing.
     */
    private fun makeLinear(): SkShader = SkLinearGradient.Make(
        SkPoint(0f, 0f),
        SkPoint(32f, 32f),
        intArrayOf(0x80F00080.toInt(), 0xF0F08000.toInt(), 0x800080F0.toInt()),
        floatArrayOf(0f, 0.5f, 1f),
        SkTileMode.kClamp,
    )

    /**
     * Mirrors upstream's `make_grayscale(input)` — a 5×4 luminance-matrix
     * colour filter (BT.709 weights) wrapped in [SkImageFilters.ColorFilter].
     * Identity for alpha (`matrix[18] = 1`).
     */
    private fun makeGrayscale(input: SkImageFilter?): SkImageFilter? {
        val matrix = FloatArray(20)
        matrix[0] = 0.2126f; matrix[5] = 0.2126f; matrix[10] = 0.2126f
        matrix[1] = 0.7152f; matrix[6] = 0.7152f; matrix[11] = 0.7152f
        matrix[2] = 0.0722f; matrix[7] = 0.0722f; matrix[12] = 0.0722f
        matrix[18] = 1f
        val filter: SkColorFilter = SkColorFilters.Matrix(matrix)
        return SkImageFilters.ColorFilter(filter, input)
    }

    /** Mirrors upstream's `make_blur(amount, input)`. */
    private fun makeBlur(amount: Float, input: SkImageFilter?): SkImageFilter? =
        SkImageFilters.Blur(amount, amount, input)

    /**
     * Mirrors upstream's `make_color_filter()` — a [SkColorFilters.Lighting]
     * filter with `mul = #0080FF`, `add = #FF2000`. Bright-cyan multiply
     * + warm-red bias, applied per-channel.
     */
    private fun makeColorFilter(): SkColorFilter = SkColorFilters.Lighting(
        SkColorSetRGB(0x00, 0x80, 0xFF),
        SkColorSetRGB(0xFF, 0x20, 0x00),
    )

    /**
     * Mirrors upstream's `ToolUtils::NameForFontFormat(format)` —
     * the `coloremoji_<name>` GM-name suffix used as the screenshot
     * filename root.
     */
    private fun nameForFormat(format: EmojiTypeface.Format): String = when (format) {
        EmojiTypeface.Format.CBDT   -> "cbdt"
        EmojiTypeface.Format.Sbix   -> "sbix"
        EmojiTypeface.Format.COLRv0 -> "colrv0"
        EmojiTypeface.Format.SVG    -> "svg"
    }
}
