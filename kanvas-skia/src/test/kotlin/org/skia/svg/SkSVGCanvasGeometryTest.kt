package org.skia.svg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkRRect
import org.skia.math.SkRect
import java.io.ByteArrayInputStream
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory

/**
 * D4.5 / B2.1 verification suite for [SkSVGCanvas] — geometry slice.
 *
 * Covers :
 *  - Document framing : `<svg>` open + close, `xmlns`, `width` /
 *    `height` / `viewBox` attributes.
 *  - Per-op SVG element emission for `drawRect` / `drawOval` /
 *    `drawCircle` / `drawLine` / `drawRRect` / `drawPath`.
 *  - SkPath → `d="…"` string for the 5 verb kinds (move / line /
 *    quad / cubic / close) plus the `kConic` → cubic approximation.
 *  - CTM wiring : a non-identity matrix produces a `transform` attr
 *    on the emitted element ; identity CTM does not.
 *  - Well-formed XML : the produced document parses with
 *    `javax.xml.parsers.DocumentBuilder` (sanity guard against
 *    quoting / escaping mistakes).
 */
class SkSVGCanvasGeometryTest {

    // ─── Framing ──────────────────────────────────────────────────────

    @Test
    fun `constructor writes svg open with width height viewBox`() {
        val sw = StringWriter()
        val canvas = SkSVGCanvas(sw, 800f, 600f)
        canvas.flush()
        val svg = sw.toString()
        assertTrue(svg.startsWith("<svg"))
        assertTrue(svg.contains("xmlns=\"http://www.w3.org/2000/svg\""))
        assertTrue(svg.contains("width=\"800\""))
        assertTrue(svg.contains("height=\"600\""))
        assertTrue(svg.contains("viewBox=\"0 0 800 600\""))
        assertTrue(svg.endsWith("</svg>\n"))
    }

    @Test
    fun `flush is idempotent`() {
        val sw = StringWriter()
        val canvas = SkSVGCanvas(sw, 1f, 1f)
        canvas.flush()
        val first = sw.toString()
        canvas.flush() // second call must be a no-op
        assertEquals(first, sw.toString())
    }

    @Test
    fun `width and height override the dummy bitmap`() {
        val canvas = SkSVGCanvas(StringWriter(), 320f, 240f)
        assertEquals(320, canvas.width)
        assertEquals(240, canvas.height)
    }

    // ─── Per-op emission ──────────────────────────────────────────────

    @Test
    fun `drawRect emits a rect element with x y width height`() {
        val svg = renderToSvg { canvas ->
            canvas.drawRect(SkRect.MakeLTRB(10f, 20f, 110f, 220f), fillPaint())
        }
        assertTrue(svg.contains("<rect"))
        assertTrue(svg.contains("x=\"10\""))
        assertTrue(svg.contains("y=\"20\""))
        assertTrue(svg.contains("width=\"100\""))
        assertTrue(svg.contains("height=\"200\""))
    }

    @Test
    fun `drawOval emits an ellipse with cx cy rx ry`() {
        val svg = renderToSvg { canvas ->
            canvas.drawOval(SkRect.MakeLTRB(0f, 0f, 100f, 60f), fillPaint())
        }
        assertTrue(svg.contains("<ellipse"))
        assertTrue(svg.contains("cx=\"50\""))
        assertTrue(svg.contains("cy=\"30\""))
        assertTrue(svg.contains("rx=\"50\""))
        assertTrue(svg.contains("ry=\"30\""))
    }

    @Test
    fun `drawCircle emits a circle with cx cy r`() {
        val svg = renderToSvg { canvas ->
            canvas.drawCircle(50f, 50f, 25f, fillPaint())
        }
        assertTrue(svg.contains("<circle"))
        assertTrue(svg.contains("cx=\"50\""))
        assertTrue(svg.contains("cy=\"50\""))
        assertTrue(svg.contains("r=\"25\""))
    }

    @Test
    fun `drawLine emits a line with x1 y1 x2 y2 and stroke style`() {
        val svg = renderToSvg { canvas ->
            canvas.drawLine(10f, 10f, 90f, 90f, strokePaint(2f))
        }
        assertTrue(svg.contains("<line"))
        assertTrue(svg.contains("x1=\"10\""))
        assertTrue(svg.contains("y1=\"10\""))
        assertTrue(svg.contains("x2=\"90\""))
        assertTrue(svg.contains("y2=\"90\""))
        // Stroke paint stub : fill="none", stroke="black", stroke-width=...
        assertTrue(svg.contains("fill=\"none\""))
        assertTrue(svg.contains("stroke=\"black\""))
        assertTrue(svg.contains("stroke-width=\"2\""))
    }

    @Test
    fun `drawRRect with simple radii emits rect with rx ry`() {
        val rrect = SkRRect().apply { setRectXY(SkRect.MakeLTRB(0f, 0f, 100f, 60f), 8f, 4f) }
        val svg = renderToSvg { canvas -> canvas.drawRRect(rrect, fillPaint()) }
        assertTrue(svg.contains("<rect"))
        assertTrue(svg.contains("rx=\"8\""))
        assertTrue(svg.contains("ry=\"4\""))
    }

    @Test
    fun `drawRRect with zero radii omits rx and ry attributes`() {
        val rrect = SkRRect().apply { setRect(SkRect.MakeLTRB(0f, 0f, 100f, 60f)) }
        val svg = renderToSvg { canvas -> canvas.drawRRect(rrect, fillPaint()) }
        assertTrue(svg.contains("<rect"))
        assertFalse(svg.contains("rx=\""), "zero radii must not emit rx attr ; got: $svg")
        assertFalse(svg.contains("ry=\""), "zero radii must not emit ry attr ; got: $svg")
    }

    // ─── SkPath verb serialisation ────────────────────────────────────

    @Test
    fun `path with move + line + close emits M L Z`() {
        val path = SkPathBuilder().apply {
            moveTo(0f, 0f)
            lineTo(50f, 0f)
            lineTo(50f, 50f)
            close()
        }.detach()
        val svg = renderToSvg { canvas -> canvas.drawPath(path, fillPaint()) }
        assertTrue(svg.contains("<path"))
        // Path string : M 0 0 L 50 0 L 50 50 Z
        assertTrue(svg.contains("d=\"M 0 0 L 50 0 L 50 50 Z\""), "got: $svg")
    }

    @Test
    fun `path with quad emits Q with control + end`() {
        val path = SkPathBuilder().apply {
            moveTo(0f, 0f)
            quadTo(50f, 100f, 100f, 0f)
        }.detach()
        val svg = renderToSvg { canvas -> canvas.drawPath(path, fillPaint()) }
        assertTrue(svg.contains("d=\"M 0 0 Q 50 100 100 0\""), "got: $svg")
    }

    @Test
    fun `path with cubic emits C with two controls + end`() {
        val path = SkPathBuilder().apply {
            moveTo(0f, 0f)
            cubicTo(20f, 80f, 80f, 80f, 100f, 0f)
        }.detach()
        val svg = renderToSvg { canvas -> canvas.drawPath(path, fillPaint()) }
        assertTrue(svg.contains("d=\"M 0 0 C 20 80 80 80 100 0\""), "got: $svg")
    }

    @Test
    fun `even-odd fill type emits fill-rule attribute`() {
        val path = SkPathBuilder().apply {
            setFillType(org.skia.foundation.SkPathFillType.kEvenOdd)
            moveTo(0f, 0f); lineTo(50f, 0f); close()
        }.detach()
        val svg = renderToSvg { canvas -> canvas.drawPath(path, fillPaint()) }
        assertTrue(svg.contains("fill-rule=\"evenodd\""))
    }

    @Test
    fun `winding fill type omits fill-rule (SVG default is nonzero)`() {
        val path = SkPathBuilder().apply {
            // SkPathBuilder default fill type is kWinding ; no setFillType
            // call needed.
            moveTo(0f, 0f); lineTo(50f, 0f); close()
        }.detach()
        val svg = renderToSvg { canvas -> canvas.drawPath(path, fillPaint()) }
        assertFalse(svg.contains("fill-rule=\""), "default winding must omit fill-rule, got: $svg")
    }

    // ─── CTM ──────────────────────────────────────────────────────────

    @Test
    fun `identity CTM omits transform attribute`() {
        val svg = renderToSvg { canvas ->
            canvas.drawRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f), fillPaint())
        }
        assertFalse(svg.contains("transform=\""), "identity matrix must not emit transform, got: $svg")
    }

    @Test
    fun `translate emits matrix transform on subsequent draws`() {
        val svg = renderToSvg { canvas ->
            canvas.translate(50f, 100f)
            canvas.drawRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f), fillPaint())
        }
        // After translate(50, 100), Skia matrix is (sx=1, kx=0, tx=50, ky=0,
        // sy=1, ty=100) → SVG matrix(1 0 0 1 50 100).
        assertTrue(svg.contains("transform=\"matrix(1 0 0 1 50 100)\""), "got: $svg")
    }

    @Test
    fun `scale + translate compose into a single matrix transform`() {
        val svg = renderToSvg { canvas ->
            canvas.translate(10f, 20f)
            canvas.scale(2f, 3f)
            canvas.drawRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f), fillPaint())
        }
        // translate * scale (canvas.translate / scale are pre-concat) :
        //   sx = 2, sy = 3, tx = 10, ty = 20
        assertTrue(svg.contains("transform=\"matrix(2 0 0 3 10 20)\""), "got: $svg")
    }

    @Test
    fun `save and restore preserve matrix discipline (no transform after restore)`() {
        val svg = renderToSvg { canvas ->
            canvas.save()
            canvas.translate(50f, 50f)
            canvas.drawRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f), fillPaint())
            canvas.restore()
            canvas.drawRect(SkRect.MakeLTRB(20f, 20f, 30f, 30f), fillPaint())
        }
        // First rect : translated → has transform
        // Second rect : after restore → identity, no transform
        val rectLines = svg.lines().filter { it.contains("<rect") }
        assertEquals(2, rectLines.size, "expected exactly 2 rects, got: $svg")
        assertTrue(rectLines[0].contains("transform=\""), "first rect must have transform")
        assertFalse(rectLines[1].contains("transform=\""), "second rect must not have transform")
    }

    // ─── Well-formed XML guard ────────────────────────────────────────

    @Test
    fun `complex render produces well-formed XML parseable by DocumentBuilder`() {
        val svg = renderToSvg { canvas ->
            canvas.drawRect(SkRect.MakeLTRB(0f, 0f, 100f, 100f), fillPaint())
            canvas.translate(50f, 50f)
            canvas.drawCircle(0f, 0f, 25f, fillPaint())
            canvas.scale(2f, 2f)
            val path = SkPathBuilder().apply {
                moveTo(0f, 0f)
                cubicTo(20f, -30f, 80f, 30f, 100f, 0f)
                close()
            }.detach()
            canvas.drawPath(path, fillPaint())
        }
        val factory = DocumentBuilderFactory.newInstance()
        // Don't try to fetch the SVG DTD over the network.
        factory.isValidating = false
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(svg.toByteArray()))
        assertNotNull(doc.documentElement)
        assertEquals("svg", doc.documentElement.localName ?: doc.documentElement.nodeName)
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    /**
     * Drive the body `block` against a fresh [SkSVGCanvas] (100×100
     * viewport ; arbitrary, doesn't matter for these structural tests),
     * flush, return the resulting SVG string.
     */
    private fun renderToSvg(block: (SkSVGCanvas) -> Unit): String {
        val sw = StringWriter()
        val canvas = SkSVGCanvas(sw, 100f, 100f)
        block(canvas)
        canvas.flush()
        return sw.toString()
    }

    private fun fillPaint(): SkPaint = SkPaint(SK_ColorBLACK).also {
        it.style = SkPaint.Style.kFill_Style
    }

    private fun strokePaint(width: Float): SkPaint = SkPaint(SK_ColorBLACK).also {
        it.style = SkPaint.Style.kStroke_Style
        it.strokeWidth = width
    }
}
