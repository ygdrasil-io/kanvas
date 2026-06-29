package org.skia.svg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.SkCodec
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorBLUE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBitmapShader
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.StringWriter
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

/**
 * B2.4 verification suite for [SkSVGCanvas]'s image + gradient layer.
 *
 * Covers :
 *  - `drawImage(image, x, y)` → `<image href="data:image/png;base64,…"/>`
 *    with correct dimensions and CTM-as-`transform`.
 *  - `drawImageRect` honours dst rect ; non-trivial src rect emits
 *    a `<!-- drawImageRect: non-full src rect -->` comment + warning.
 *  - Image data round-trips : the base64 payload decodes back through
 *    [SkCodec] to the original pixel buffer (lossless because PNG).
 *  - Linear gradient on `paint.shader` → `<defs><linearGradient>`
 *    block + `fill="url(#def-N)"` ; stops emitted in order with hex
 *    + opacity decomposition ; tile mode → `spreadMethod`.
 *  - Radial gradient → analogous `<radialGradient>` block.
 *  - Bitmap shader (kRepeat) → `<defs><pattern>` with embedded
 *    `<image>` ; non-`kRepeat` tile modes log warning.
 *  - Local matrix on shader → `gradientTransform` / `patternTransform`.
 *  - Document-monotonic def ids — never reused across linear /
 *    radial / pattern.
 *  - End-to-end well-formed XML on a complex draw.
 */
class SkSVGCanvasImageGradientTest {

    // ─── drawImage ────────────────────────────────────────────────────

    @Test
    fun `drawImage emits an image href data url with correct dimensions`() {
        val image = makeSolidImage(8, 6, SK_ColorRED)
        val svg = render { canvas -> canvas.drawImage(image, 10f, 20f) }
        assertTrue(svg.contains("<image"))
        assertTrue(svg.contains("x=\"10\""))
        assertTrue(svg.contains("y=\"20\""))
        assertTrue(svg.contains("width=\"8\""))
        assertTrue(svg.contains("height=\"6\""))
        assertTrue(svg.contains("href=\"data:image/png;base64,"))
    }

    @Test
    fun `drawImage data url is lossless — round-trips through SkCodec`() {
        val src = makeSolidImage(4, 4, SkColorSetARGB(0xFF, 0x12, 0x34, 0x56))
        val svg = render { canvas -> canvas.drawImage(src, 0f, 0f) }
        val dataUrl = extractDataUrl(svg)
        val pngBytes = Base64.getDecoder().decode(dataUrl.substringAfter("base64,"))
        val (decoded, result) = SkCodec.MakeFromData(pngBytes)!!.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(decoded)
        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals(
                src.peekPixel(x, y),
                decoded!!.getPixel(x, y),
                "PNG round-trip must be lossless at ($x,$y)",
            )
        }
    }

    @Test
    fun `drawImageRect with src equal to full bounds is silent`() {
        val image = makeSolidImage(4, 4, SK_ColorBLUE)
        val (svg, stderr) = renderCapturingStderr { canvas ->
            canvas.drawImageRect(
                image = image,
                src = SkRect.MakeWH(4f, 4f),
                dst = SkRect.MakeXYWH(10f, 10f, 40f, 40f),
            )
        }
        assertFalse(svg.contains("non-full src rect"), "full src rect must not warn, got: $svg")
        assertEquals("", stderr)
        assertTrue(svg.contains("width=\"40\""))
        assertTrue(svg.contains("height=\"40\""))
    }

    @Test
    fun `drawImageRect with sub-rect src emits comment + warning`() {
        val image = makeSolidImage(8, 8, SK_ColorBLUE)
        val (svg, stderr) = renderCapturingStderr { canvas ->
            canvas.drawImageRect(
                image = image,
                src = SkRect.MakeXYWH(2f, 2f, 4f, 4f), // sub-rect
                dst = SkRect.MakeXYWH(0f, 0f, 8f, 8f),
            )
        }
        assertTrue(svg.contains("non-full src rect"))
        assertTrue(stderr.contains("drawImageRect"))
    }

    @Test
    fun `drawImage CTM lands as transform attr`() {
        val image = makeSolidImage(4, 4, SK_ColorRED)
        val svg = render { canvas ->
            canvas.translate(50f, 100f)
            canvas.drawImage(image, 0f, 0f)
        }
        assertTrue(svg.contains("transform=\"matrix(1 0 0 1 50 100)\""))
    }

    // ─── Linear gradient ──────────────────────────────────────────────

    @Test
    fun `linear gradient shader emits defs and fill url`() {
        val shader = SkLinearGradient.Make(
            p0 = SkPoint(0f, 0f),
            p1 = SkPoint(100f, 0f),
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = floatArrayOf(0f, 1f),
            tileMode = SkTileMode.kClamp,
        )
        val svg = render { canvas ->
            val p = SkPaint(SK_ColorBLACK).apply { this.shader = shader }
            canvas.drawRect(SkRect.MakeWH(100f, 50f), p)
        }
        assertTrue(svg.contains("<linearGradient id=\"def-0\""))
        assertTrue(svg.contains("gradientUnits=\"userSpaceOnUse\""))
        assertTrue(svg.contains("x1=\"0\""))
        assertTrue(svg.contains("x2=\"100\""))
        assertTrue(svg.contains("<stop offset=\"0\" stop-color=\"#ff0000\""))
        assertTrue(svg.contains("<stop offset=\"1\" stop-color=\"#0000ff\""))
        assertTrue(svg.contains("fill=\"url(#def-0)\""))
        assertFalse(
            svg.contains("spreadMethod"),
            "kClamp → 'pad' is the SVG default and must be omitted",
        )
    }

    @Test
    fun `linear gradient tile modes map to spreadMethod`() {
        val repeat = renderGradient(SkTileMode.kRepeat)
        assertTrue(repeat.contains("spreadMethod=\"repeat\""))
        val mirror = renderGradient(SkTileMode.kMirror)
        assertTrue(mirror.contains("spreadMethod=\"reflect\""))
    }

    @Test
    fun `gradient stop with non-opaque alpha emits stop-opacity`() {
        val shader = SkLinearGradient.Make(
            p0 = SkPoint(0f, 0f),
            p1 = SkPoint(10f, 0f),
            colors = intArrayOf(SkColorSetARGB(0x80, 0xFF, 0x00, 0x00)),
            positions = floatArrayOf(0.5f),
            tileMode = SkTileMode.kClamp,
        )
        val svg = render { canvas ->
            val p = SkPaint(SK_ColorBLACK).apply { this.shader = shader }
            canvas.drawRect(SkRect.MakeWH(10f, 10f), p)
        }
        assertTrue(svg.contains("stop-opacity=\"0.501961\""))
    }

    @Test
    fun `gradient with localMatrix emits gradientTransform`() {
        val shader = SkLinearGradient.Make(
            p0 = SkPoint(0f, 0f),
            p1 = SkPoint(10f, 0f),
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
            localMatrix = SkMatrix(sx = 2f, sy = 2f, tx = 5f, ty = 5f),
        )
        val svg = render { canvas ->
            val p = SkPaint(SK_ColorBLACK).apply { this.shader = shader }
            canvas.drawRect(SkRect.MakeWH(10f, 10f), p)
        }
        assertTrue(svg.contains("gradientTransform=\"matrix(2 0 0 2 5 5)\""))
    }

    // ─── Radial gradient ──────────────────────────────────────────────

    @Test
    fun `radial gradient shader emits radialGradient def`() {
        val shader = SkRadialGradient.Make(
            center = SkPoint(50f, 50f),
            radius = 25f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = floatArrayOf(0f, 1f),
            tileMode = SkTileMode.kClamp,
        )
        val svg = render { canvas ->
            val p = SkPaint(SK_ColorBLACK).apply { this.shader = shader }
            canvas.drawCircle(50f, 50f, 25f, p)
        }
        assertTrue(svg.contains("<radialGradient id=\"def-0\""))
        assertTrue(svg.contains("cx=\"50\""))
        assertTrue(svg.contains("cy=\"50\""))
        assertTrue(svg.contains("r=\"25\""))
        assertTrue(svg.contains("fill=\"url(#def-0)\""))
    }

    // ─── Bitmap shader → <pattern> ────────────────────────────────────

    @Test
    fun `bitmap shader with kRepeat emits pattern with embedded image`() {
        val image = makeSolidImage(4, 4, SK_ColorRED)
        val shader = SkBitmapShader(image, SkTileMode.kRepeat, SkTileMode.kRepeat, SkSamplingOptions.Default, SkMatrix.Identity)
        val (svg, stderr) = renderCapturingStderr { canvas ->
            val p = SkPaint(SK_ColorBLACK).apply { this.shader = shader }
            canvas.drawRect(SkRect.MakeWH(20f, 20f), p)
        }
        assertTrue(svg.contains("<pattern id=\"def-0\""))
        assertTrue(svg.contains("patternUnits=\"userSpaceOnUse\""))
        assertTrue(svg.contains("width=\"4\""))
        assertTrue(svg.contains("height=\"4\""))
        assertTrue(svg.contains("href=\"data:image/png;base64,"))
        assertTrue(svg.contains("fill=\"url(#def-0)\""))
        assertEquals("", stderr, "kRepeat is the natural SVG <pattern> mode — no warning")
    }

    @Test
    fun `bitmap shader with non-repeat tile mode emits comment and warning`() {
        val image = makeSolidImage(4, 4, SK_ColorRED)
        val shader = SkBitmapShader(image, SkTileMode.kClamp, SkTileMode.kClamp, SkSamplingOptions.Default, SkMatrix.Identity)
        val (svg, stderr) = renderCapturingStderr { canvas ->
            val p = SkPaint(SK_ColorBLACK).apply { this.shader = shader }
            canvas.drawRect(SkRect.MakeWH(20f, 20f), p)
        }
        assertTrue(svg.contains("<!-- bitmap shader tile=(kClamp, kClamp)"))
        assertTrue(stderr.contains("not natively representable"))
        // The pattern is still emitted so the SVG is well-formed.
        assertTrue(svg.contains("<pattern id=\"def-0\""))
    }

    // ─── Document-monotonic ids across def kinds ─────────────────────

    @Test
    fun `def ids are monotonic across linear, radial, and pattern`() {
        val image = makeSolidImage(2, 2, SK_ColorRED)
        val linear = SkLinearGradient.Make(SkPoint(0f, 0f), SkPoint(1f, 0f), intArrayOf(SK_ColorRED, SK_ColorBLUE), null, SkTileMode.kClamp)
        val radial = SkRadialGradient.Make(SkPoint(5f, 5f), 5f, intArrayOf(SK_ColorRED, SK_ColorBLUE), null, SkTileMode.kClamp)
        val pattern = SkBitmapShader(image, SkTileMode.kRepeat, SkTileMode.kRepeat, SkSamplingOptions.Default, SkMatrix.Identity)
        val svg = render { canvas ->
            canvas.drawRect(SkRect.MakeWH(10f, 10f), SkPaint(SK_ColorBLACK).apply { this.shader = linear })
            canvas.drawRect(SkRect.MakeWH(10f, 10f), SkPaint(SK_ColorBLACK).apply { this.shader = radial })
            canvas.drawRect(SkRect.MakeWH(10f, 10f), SkPaint(SK_ColorBLACK).apply { this.shader = pattern })
        }
        assertTrue(svg.contains("def-0"))
        assertTrue(svg.contains("def-1"))
        assertTrue(svg.contains("def-2"))
    }

    // ─── End-to-end ───────────────────────────────────────────────────

    @Test
    fun `complex draw with image + gradient produces well-formed XML`() {
        val image = makeSolidImage(4, 4, SK_ColorRED)
        val gradient = SkLinearGradient.Make(SkPoint(0f, 0f), SkPoint(50f, 0f), intArrayOf(SK_ColorRED, SK_ColorBLUE), null, SkTileMode.kClamp)
        val svg = render { canvas ->
            canvas.drawImage(image, 0f, 0f)
            canvas.translate(20f, 0f)
            canvas.drawRect(SkRect.MakeWH(50f, 50f), SkPaint(SK_ColorBLACK).apply { this.shader = gradient })
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

    private fun renderGradient(tile: SkTileMode): String {
        val shader = SkLinearGradient.Make(
            p0 = SkPoint(0f, 0f),
            p1 = SkPoint(10f, 0f),
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = tile,
        )
        return render { canvas ->
            val p = SkPaint(SK_ColorBLACK).apply { this.shader = shader }
            canvas.drawRect(SkRect.MakeWH(10f, 10f), p)
        }
    }

    private fun makeSolidImage(width: Int, height: Int, color: Int): org.skia.foundation.SkImage {
        val bitmap = SkBitmap(width, height, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        for (y in 0 until height) for (x in 0 until width) {
            bitmap.pixels[y * width + x] = color
        }
        return bitmap.asImage()
    }

    private fun extractDataUrl(svg: String): String {
        val anchor = "href=\"data:image/png;base64,"
        val start = svg.indexOf(anchor)
        check(start >= 0) { "no data URL found in : $svg" }
        val urlStart = start + "href=\"".length
        val urlEnd = svg.indexOf('"', urlStart)
        return svg.substring(urlStart, urlEnd)
    }

    private fun assertWellFormedXml(svg: String) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isValidating = false
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(svg.toByteArray()))
        assertNotNull(doc.documentElement)
    }
}
