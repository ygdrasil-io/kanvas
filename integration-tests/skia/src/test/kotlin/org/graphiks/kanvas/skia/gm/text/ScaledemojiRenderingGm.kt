package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/scaledemoji_rendering.cpp`.
 * Iterates over five emoji font-format slots (COLRv0, Sbix, CBDT, Test,
 * SVG) and renders each format's canonical text at two point sizes (70 pt
 * and 150 pt) with aliased subpixel rendering.
 *
 * ## Adaptation notes
 *
 * Real emoji typeface dispatch (`EmojiTypeface.create`) is gated behind
 * `STUB.EMOJI_TABLES`. All five slots fall back to Liberation Sans so the
 * GM produces a deterministic, though non-emoji, render. When emoji
 * typefaces are wired in Kanvas, replace the fallback load with per-slot
 * typeface creation.
 *
 * Kanvas [Font] does not expose `setEmbolden`, `getMetrics`, or
 * `measureText` with bounds output. The GM simplifies the upstream body
 * by skipping the fake-bold loop, using [Font.measureText] for advance
 * only, and falling back to fixed vertical spacing instead of
 * metrics-driven layout.
 *
 * @see https://github.com/google/skia/blob/main/gm/scaledemoji_rendering.cpp
 */
class ScaledemojiRenderingGm : SkiaGm {
    override val name = "scaledemoji_rendering"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 1200
    override val height = 1200

    private data class EmojiSample(
        val sampleText: String,
        val font: Font,
    )

    private val fallbackTypeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    private val samples: Array<EmojiSample> = arrayOf(
        // Slot 0 — COLRv0
        EmojiSample(
            sampleText = "\uD83D\uDE00 \u2662",
            font = Font(fallbackTypeface, antiAlias = false, subpixel = true),
        ),
        // Slot 1 — Sbix
        EmojiSample(
            sampleText = "\uD83D\uDE00 \u2662",
            font = Font(fallbackTypeface, antiAlias = false, subpixel = true),
        ),
        // Slot 2 — CBDT
        EmojiSample(
            sampleText = "\uD83D\uDE00 \u2662",
            font = Font(fallbackTypeface, antiAlias = false, subpixel = true),
        ),
        // Slot 3 — Test
        EmojiSample(
            sampleText = "\uD83D\uDE00 \u2662",
            font = Font(fallbackTypeface, antiAlias = false, subpixel = true),
        ),
        // Slot 4 — SVG ("abcdefghij")
        EmojiSample(
            sampleText = "abcdefghij",
            font = Font(fallbackTypeface, antiAlias = false, subpixel = true),
        ),
    )

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.5f, 0.5f, 0.5f)

        val textPaint = Paint(color = Color.fromRGBA(0f, 1f, 1f))

        var y = 0f

        for (sample in samples) {
            val text = sample.sampleText

            for (textSize in floatArrayOf(70f, 150f)) {
                val font = sample.font.copy(size = textSize)

                // Fixed spacing approximates upstream metric-driven y advance.
                // upstream: font.getMetrics(metrics); y += -metrics.fAscent
                y += textSize * 0.8f

                val advance = font.measureText(text)
                canvas.drawSimpleText(text, 0f, y, font, textPaint)

                // upstream: x += bounds.width() * 1.2 (with bounds measure)
                val xAdvance = advance * 1.2f

                // Second pass — upstream uses fakeBold. Kanvas Font has no
                // embolden toggle, so we render the same text again shifted
                // horizontally to preserve the row layout footprint.
                canvas.drawSimpleText(text, xAdvance, y, font, textPaint)

                // upstream: y += metrics.fDescent + metrics.fLeading
                y += textSize * 0.4f
            }
        }
    }
}
