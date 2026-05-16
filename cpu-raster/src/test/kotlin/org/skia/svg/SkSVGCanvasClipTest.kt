package org.skia.svg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkRRect
import org.skia.math.SkRect
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory

/**
 * B2.3 verification suite for [SkSVGCanvas]'s clip layer.
 *
 * Covers :
 *  - `clipRect` / `clipRRect` / `clipPath` each emit a
 *    `<defs><clipPath id="clip-N">…</clipPath></defs>` def + open a
 *    wrapping `<g clip-path="url(#clip-N)">` ; subsequent draws land
 *    inside the wrapper.
 *  - The clip's CTM at emission time is captured as a `transform=…`
 *    on the inner geometry so the clipped region lands in the right
 *    device-space rect even when later draws change the CTM.
 *  - `save()` / `restore()` correctly close every wrapper opened
 *    inside the matched scope (no leaks, no over-closures).
 *  - Multiple clips inside the same save scope nest as multiple
 *    wrappers (intersection ⇒ nested SVG defs).
 *  - Clip ids are unique across the document — never reused even
 *    after the wrapper they belonged to has been closed.
 *  - `flush()` closes any clip wrappers the caller forgot to pop.
 *  - `kDifference` clip op emits a `<!-- clipOp: kDifference -->`
 *    XML comment **plus** a `System.err` warning, then falls
 *    through to the kIntersect path.
 *  - End-to-end well-formed XML : a clip-heavy document parses
 *    with `javax.xml.parsers.DocumentBuilder`.
 */
class SkSVGCanvasClipTest {

    @Test
    fun `clipRect emits a defs clipPath then opens a wrapping g`() {
        val svg = render { canvas ->
            canvas.clipRect(SkRect.MakeLTRB(10f, 20f, 110f, 220f))
            canvas.drawRect(SkRect.MakeWH(50f, 50f), fillPaint())
        }
        assertTrue(svg.contains("<defs>"))
        assertTrue(svg.contains("<clipPath id=\"clip-0\""))
        // Inner geometry of the clip — rect carrying the clip rect's
        // dimensions in local (clip-time) coords.
        assertTrue(svg.contains("<rect x=\"10\" y=\"20\" width=\"100\" height=\"200\""))
        assertTrue(svg.contains("</clipPath>"))
        assertTrue(svg.contains("<g clip-path=\"url(#clip-0)\""))
        // The drawn <rect> appears AFTER the <g> open and BEFORE the
        // matching </g> — i.e. is wrapped by the clip.
        val gOpen = svg.indexOf("<g clip-path=\"url(#clip-0)\"")
        val drawnRect = svg.indexOf("<rect x=\"0\" y=\"0\" width=\"50\" height=\"50\"", gOpen)
        val gClose = svg.indexOf("</g>", drawnRect)
        assertTrue(gOpen >= 0 && drawnRect > gOpen && gClose > drawnRect, "ordering: $svg")
    }

    @Test
    fun `clipRRect emits a rect with rx ry inside the clipPath def`() {
        val rrect = SkRRect().apply { setRectXY(SkRect.MakeLTRB(0f, 0f, 100f, 60f), 8f, 4f) }
        val svg = render { canvas ->
            canvas.clipRRect(rrect)
            canvas.drawRect(SkRect.MakeWH(50f, 50f), fillPaint())
        }
        assertTrue(svg.contains("<clipPath id=\"clip-0\""))
        assertTrue(svg.contains("rx=\"8\""))
        assertTrue(svg.contains("ry=\"4\""))
    }

    @Test
    fun `clipPath emits a path d inside the clipPath def`() {
        val path = SkPathBuilder().apply {
            moveTo(0f, 0f); lineTo(50f, 0f); lineTo(50f, 50f); close()
        }.detach()
        val svg = render { canvas ->
            canvas.clipPath(path)
            canvas.drawRect(SkRect.MakeWH(50f, 50f), fillPaint())
        }
        assertTrue(svg.contains("<clipPath id=\"clip-0\""))
        assertTrue(svg.contains("<path d=\"M 0 0 L 50 0 L 50 50 Z\""))
    }

    @Test
    fun `clipPath even-odd fill type maps to clip-rule`() {
        val path = SkPathBuilder().apply {
            setFillType(SkPathFillType.kEvenOdd)
            moveTo(0f, 0f); lineTo(50f, 0f); close()
        }.detach()
        val svg = render { canvas ->
            canvas.clipPath(path)
            canvas.drawRect(SkRect.MakeWH(50f, 50f), fillPaint())
        }
        assertTrue(svg.contains("clip-rule=\"evenodd\""))
    }

    @Test
    fun `clip captures CTM at emission time as inner geometry transform`() {
        val svg = render { canvas ->
            canvas.translate(50f, 100f)
            canvas.clipRect(SkRect.MakeWH(20f, 20f))
            // Now translate further and draw — the clip stays in its
            // emission-time local space, the draw's CTM is independent.
            canvas.translate(5f, 5f)
            canvas.drawRect(SkRect.MakeWH(10f, 10f), fillPaint())
        }
        // The clipPath inner rect carries transform="matrix(1 0 0 1 50 100)".
        assertTrue(
            svg.contains("<rect x=\"0\" y=\"0\" width=\"20\" height=\"20\" transform=\"matrix(1 0 0 1 50 100)\"") ||
                svg.contains("transform=\"matrix(1 0 0 1 50 100)\""),
            "clip's inner geometry must carry the clip-time CTM, got: $svg",
        )
        // The drawn rect carries transform="matrix(1 0 0 1 55 105)" (after both translates).
        assertTrue(
            svg.contains("transform=\"matrix(1 0 0 1 55 105)\""),
            "drawn rect must carry full current CTM, got: $svg",
        )
    }

    @Test
    fun `restore closes the wrapping g — subsequent draws are not clipped`() {
        val svg = render { canvas ->
            canvas.save()
            canvas.clipRect(SkRect.MakeWH(20f, 20f))
            canvas.drawRect(SkRect.MakeWH(10f, 10f), fillPaint())
            canvas.restore()
            canvas.drawRect(SkRect.MakeLTRB(50f, 50f, 60f, 60f), fillPaint())
        }
        // Find the </g> close, then ensure the second rect comes AFTER it.
        val gClose = svg.indexOf("</g>")
        val secondRect = svg.indexOf("<rect x=\"50\"", gClose)
        assertTrue(gClose >= 0)
        assertTrue(secondRect > gClose, "second rect must be outside the wrapper, got: $svg")
    }

    @Test
    fun `multiple clips inside the same save nest as multiple wrappers`() {
        val svg = render { canvas ->
            canvas.save()
            canvas.clipRect(SkRect.MakeWH(100f, 100f))
            canvas.clipRect(SkRect.MakeWH(50f, 50f))
            canvas.drawRect(SkRect.MakeWH(10f, 10f), fillPaint())
            canvas.restore()
        }
        assertTrue(svg.contains("clip-0"))
        assertTrue(svg.contains("clip-1"))
        // Two open <g>s and two close </g>s.
        val openCount = svg.split("<g clip-path=\"url(#clip-").size - 1
        val closeCount = svg.split("</g>").size - 1
        assertEquals(2, openCount, "expected 2 wrapping <g>s, got: $svg")
        assertEquals(2, closeCount, "expected 2 </g> closes, got: $svg")
    }

    @Test
    fun `clip ids are never reused even after the wrapper is closed`() {
        val svg = render { canvas ->
            canvas.save()
            canvas.clipRect(SkRect.MakeWH(100f, 100f))
            canvas.drawRect(SkRect.MakeWH(10f, 10f), fillPaint())
            canvas.restore()
            // Second clip — must be clip-1, not clip-0.
            canvas.clipRect(SkRect.MakeWH(50f, 50f))
            canvas.drawRect(SkRect.MakeWH(10f, 10f), fillPaint())
        }
        assertTrue(svg.contains("clip-0"))
        assertTrue(svg.contains("clip-1"))
    }

    @Test
    fun `flush closes leaked clip wrappers without crashing`() {
        // Forgot to call restore() — flush must still produce valid SVG.
        val svg = render { canvas ->
            canvas.save()
            canvas.clipRect(SkRect.MakeWH(20f, 20f))
            canvas.drawRect(SkRect.MakeWH(10f, 10f), fillPaint())
            // No restore()
        }
        // Document must still close cleanly with a </svg>.
        assertTrue(svg.endsWith("</svg>\n"))
        // Every <g> that opened must be matched by a </g>.
        val opens = svg.split("<g ").size - 1
        val closes = svg.split("</g>").size - 1
        assertEquals(opens, closes, "unbalanced <g>/</g>, got: $svg")
        // Document parses as valid XML.
        assertWellFormedXml(svg)
    }

    @Test
    fun `kDifference clip op emits comment and warning, falls through to kIntersect`() {
        val (svg, stderr) = renderCapturingStderr { canvas ->
            canvas.clipRect(SkRect.MakeWH(20f, 20f), SkClipOp.kDifference, doAntiAlias = false)
            canvas.drawRect(SkRect.MakeWH(10f, 10f), fillPaint())
        }
        assertTrue(svg.contains("<!-- clipOp: kDifference"))
        assertTrue(stderr.contains("kDifference"))
        // Despite the warning, the geometry is still emitted as a clip
        // wrapper (kIntersect fallback).
        assertTrue(svg.contains("<clipPath id=\"clip-0\""))
        assertTrue(svg.contains("<g clip-path=\"url(#clip-0)\""))
    }

    @Test
    fun `complex clip + draw render produces well-formed XML`() {
        val svg = render { canvas ->
            canvas.translate(20f, 20f)
            canvas.clipRect(SkRect.MakeLTRB(0f, 0f, 60f, 60f))
            canvas.drawCircle(30f, 30f, 25f, fillPaint())
            canvas.save()
            canvas.scale(2f, 2f)
            val path = SkPathBuilder().apply {
                moveTo(0f, 0f); lineTo(15f, 0f); lineTo(15f, 15f); close()
            }.detach()
            canvas.clipPath(path)
            canvas.drawRect(SkRect.MakeWH(10f, 10f), fillPaint())
            canvas.restore()
            canvas.drawCircle(50f, 50f, 8f, fillPaint())
        }
        assertWellFormedXml(svg)
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private fun render(block: (SkSVGCanvas) -> Unit): String {
        val sw = StringWriter()
        val canvas = SkSVGCanvas(sw, 200f, 200f)
        block(canvas)
        canvas.flush()
        return sw.toString()
    }

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

    private fun assertWellFormedXml(svg: String) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isValidating = false
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(svg.toByteArray()))
        assertNotNull(doc.documentElement)
    }

    private fun fillPaint(): SkPaint = SkPaint(SK_ColorBLACK).apply {
        style = SkPaint.Style.kFill_Style
    }
}
