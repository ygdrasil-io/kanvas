package org.skia.svg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBlendMode
import org.skia.math.SkColorSetARGB
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.math.SkRect
import java.io.PrintStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter

/**
 * B2.2 verification suite for [SkSVGCanvas]'s paint serialiser.
 *
 * Covers :
 *  - Colour : `fill="#rrggbb"` / `stroke="#rrggbb"` (lower-case hex,
 *    alpha decoupled into `*-opacity`).
 *  - Alpha : opaque omits `*-opacity`, translucent emits it as a
 *    `[0, 1]` scalar.
 *  - Style : `kFill` → fill set + stroke="none" ; `kStroke` →
 *    fill="none" + stroke set ; `kStrokeAndFill` → both.
 *  - Stroke surface : width / linecap / linejoin / miterlimit
 *    (with the SVG-default elision rule for `miterlimit == 4`).
 *  - Dash path effect → `stroke-dasharray` + `stroke-dashoffset`.
 *  - Anti-alias OFF → `shape-rendering="crispEdges"`.
 *  - Blend mode : default omits annotation, `kSrc` emits a
 *    `<!-- blend: kSrc -->` comment, every other mode emits the
 *    comment **plus** a `System.err` warning.
 */
class SkSVGCanvasPaintTest {

    // ─── Colour + alpha ───────────────────────────────────────────────

    @Test
    fun `fill paint emits fill hex and stroke none`() {
        val svg = render { canvas ->
            val p = SkPaint(SK_ColorRED).apply { style = SkPaint.Style.kFill_Style }
            canvas.drawRect(SkRect.MakeWH(10f, 10f), p)
        }
        assertTrue(svg.contains("fill=\"#ff0000\""))
        assertTrue(svg.contains("stroke=\"none\""))
        assertFalse(svg.contains("fill-opacity"), "opaque fill must omit fill-opacity, got: $svg")
    }

    @Test
    fun `translucent fill emits fill-opacity`() {
        val svg = render { canvas ->
            val p = SkPaint(SkColorSetARGB(0x80, 0xFF, 0x00, 0x00))
            canvas.drawRect(SkRect.MakeWH(10f, 10f), p)
        }
        // Alpha 0x80 → 128/255 ≈ 0.501961.
        assertTrue(svg.contains("fill-opacity=\"0.501961\""), "got: $svg")
    }

    @Test
    fun `stroke paint emits stroke hex and fill none`() {
        val svg = render { canvas ->
            val p = SkPaint(SK_ColorRED).apply {
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 3f
            }
            canvas.drawLine(0f, 0f, 10f, 10f, p)
        }
        assertTrue(svg.contains("stroke=\"#ff0000\""))
        assertTrue(svg.contains("stroke-width=\"3\""))
        assertTrue(svg.contains("fill=\"none\""))
    }

    @Test
    fun `kStrokeAndFill emits both fill and stroke`() {
        val svg = render { canvas ->
            val p = SkPaint(SkColorSetARGB(0x40, 0x12, 0x34, 0x56)).apply {
                style = SkPaint.Style.kStrokeAndFill_Style
                strokeWidth = 1f
            }
            canvas.drawRect(SkRect.MakeWH(10f, 10f), p)
        }
        assertTrue(svg.contains("fill=\"#123456\""))
        assertTrue(svg.contains("stroke=\"#123456\""))
        assertTrue(svg.contains("fill-opacity="))
        assertTrue(svg.contains("stroke-opacity="))
    }

    // ─── Stroke surface ──────────────────────────────────────────────

    @Test
    fun `stroke linecap maps to SVG names (round and square)`() {
        val round = render { canvas ->
            val p = strokePaint().apply { strokeCap = SkPaint.Cap.kRound_Cap }
            canvas.drawLine(0f, 0f, 10f, 10f, p)
        }
        assertTrue(round.contains("stroke-linecap=\"round\""))

        val square = render { canvas ->
            val p = strokePaint().apply { strokeCap = SkPaint.Cap.kSquare_Cap }
            canvas.drawLine(0f, 0f, 10f, 10f, p)
        }
        assertTrue(square.contains("stroke-linecap=\"square\""))
    }

    @Test
    fun `stroke butt cap is omitted (SVG default)`() {
        val svg = render { canvas ->
            val p = strokePaint().apply { strokeCap = SkPaint.Cap.kButt_Cap }
            canvas.drawLine(0f, 0f, 10f, 10f, p)
        }
        assertFalse(svg.contains("stroke-linecap"), "butt is the SVG default ; must be omitted")
    }

    @Test
    fun `stroke linejoin maps to SVG names (round and bevel)`() {
        val round = render { canvas ->
            val p = strokePaint().apply { strokeJoin = SkPaint.Join.kRound_Join }
            canvas.drawLine(0f, 0f, 10f, 10f, p)
        }
        assertTrue(round.contains("stroke-linejoin=\"round\""))

        val bevel = render { canvas ->
            val p = strokePaint().apply { strokeJoin = SkPaint.Join.kBevel_Join }
            canvas.drawLine(0f, 0f, 10f, 10f, p)
        }
        assertTrue(bevel.contains("stroke-linejoin=\"bevel\""))
    }

    @Test
    fun `stroke miter limit emitted only when non-default`() {
        val defaultMiter = render { canvas ->
            val p = strokePaint() // default miter = 4
            canvas.drawLine(0f, 0f, 10f, 10f, p)
        }
        assertFalse(defaultMiter.contains("stroke-miterlimit"))

        val customMiter = render { canvas ->
            val p = strokePaint().apply { strokeMiter = 8f }
            canvas.drawLine(0f, 0f, 10f, 10f, p)
        }
        assertTrue(customMiter.contains("stroke-miterlimit=\"8\""))
    }

    // ─── Dash ────────────────────────────────────────────────────────

    @Test
    fun `dash path effect emits stroke-dasharray with no offset at phase 0`() {
        val svg = render { canvas ->
            val p = strokePaint().apply {
                pathEffect = SkDashPathEffect.Make(floatArrayOf(4f, 2f, 1f, 3f), 0f)
            }
            canvas.drawLine(0f, 0f, 10f, 10f, p)
        }
        assertTrue(svg.contains("stroke-dasharray=\"4 2 1 3\""))
        assertFalse(svg.contains("stroke-dashoffset"), "phase 0 must omit dashoffset")
    }

    @Test
    fun `dash path effect emits stroke-dashoffset when phase is non-zero`() {
        val svg = render { canvas ->
            val p = strokePaint().apply {
                pathEffect = SkDashPathEffect.Make(floatArrayOf(5f, 5f), 2.5f)
            }
            canvas.drawLine(0f, 0f, 10f, 10f, p)
        }
        assertTrue(svg.contains("stroke-dasharray=\"5 5\""))
        assertTrue(svg.contains("stroke-dashoffset=\"2.5\""))
    }

    // ─── Anti-alias ──────────────────────────────────────────────────

    @Test
    fun `aliased paint emits shape-rendering crispEdges`() {
        val svg = render { canvas ->
            val p = SkPaint(SK_ColorBLACK).apply { isAntiAlias = false }
            canvas.drawRect(SkRect.MakeWH(10f, 10f), p)
        }
        assertTrue(svg.contains("shape-rendering=\"crispEdges\""))
    }

    @Test
    fun `anti-aliased paint omits shape-rendering attribute (SVG default)`() {
        val svg = render { canvas ->
            val p = SkPaint(SK_ColorBLACK).apply { isAntiAlias = true }
            canvas.drawRect(SkRect.MakeWH(10f, 10f), p)
        }
        assertFalse(svg.contains("shape-rendering"))
    }

    // ─── Blend mode ──────────────────────────────────────────────────

    @Test
    fun `default kSrcOver omits the blend annotation`() {
        val svg = render { canvas ->
            val p = SkPaint(SK_ColorBLACK) // blend defaults to kSrcOver
            canvas.drawRect(SkRect.MakeWH(10f, 10f), p)
        }
        assertFalse(svg.contains("blend:"), "default blend must not produce a comment")
    }

    @Test
    fun `kSrc emits a blend comment but no warning`() {
        val (svg, stderr) = renderCapturingStderr { canvas ->
            val p = SkPaint(SK_ColorBLACK).apply { blendMode = SkBlendMode.kSrc }
            canvas.drawRect(SkRect.MakeWH(10f, 10f), p)
        }
        assertTrue(svg.contains("<!-- blend: kSrc -->"), "got: $svg")
        assertEquals("", stderr, "kSrc must not warn ; got stderr: $stderr")
    }

    @Test
    fun `non-default non-kSrc blend emits comment and stderr warning`() {
        val (svg, stderr) = renderCapturingStderr { canvas ->
            val p = SkPaint(SK_ColorBLACK).apply { blendMode = SkBlendMode.kMultiply }
            canvas.drawRect(SkRect.MakeWH(10f, 10f), p)
        }
        assertTrue(svg.contains("<!-- blend: kMultiply -->"))
        assertTrue(stderr.contains("blend mode"))
        assertTrue(stderr.contains("kMultiply"))
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private fun render(block: (SkSVGCanvas) -> Unit): String {
        val sw = StringWriter()
        val canvas = SkSVGCanvas(sw, 100f, 100f)
        block(canvas)
        canvas.flush()
        return sw.toString()
    }

    /**
     * Drive [block] like [render] but also capture anything written
     * to `System.err` during the call. Returns `(svg, stderr)`.
     */
    private fun renderCapturingStderr(block: (SkSVGCanvas) -> Unit): Pair<String, String> {
        val original = System.err
        val baos = ByteArrayOutputStream()
        try {
            System.setErr(PrintStream(baos))
            val svg = render(block)
            System.err.flush()
            return svg to baos.toString()
        } finally {
            System.setErr(original)
        }
    }

    private fun strokePaint(): SkPaint = SkPaint(SK_ColorBLACK).apply {
        style = SkPaint.Style.kStroke_Style
        strokeWidth = 1f
    }
}
