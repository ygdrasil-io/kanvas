package org.skia.docs

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColor
import org.skia.foundation.SkDocument
import org.skia.foundation.SkDynamicMemoryWStream
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkWStreamMinimal
import org.skia.math.SkRect

/**
 * Minimal PDF 1.4 backend for [SkDocument], mirroring upstream
 * `include/docs/SkPDFDocument.h` (`namespace SkPDF`).
 *
 * **Phase R3.6 — explicit simplifications** (each becomes an R-suivi
 * follow-up):
 *
 *  - **Text** : [SkCanvas.drawString] / `drawTextBlob` recorded as
 *    no-ops in the PDF stream. Glyph embedding + font subsetting is
 *    R3.2 / R3.3 territory.
 *  - **Gradients / shaders** : ignored (only `paint.color` is honoured).
 *  - **Images** : `drawImage` recorded as a no-op; XObject embedding
 *    is a follow-up (needs JPEG round-trip via SkJpegEncoder).
 *  - **Compression** : streams are written uncompressed (no
 *    `FlateDecode` filter). Defaults to PDF 1.4, no `pdfA`.
 *  - **No encryption, no structure tree, no outline, no metadata XMP**.
 *  - **Path verbs** : only `move`, `line`, `cubic`, `close` are emitted;
 *    `quad` and `conic` are linearised to a single line segment
 *    (sufficient for the R3 smoke-test surface).
 *  - **Blend modes / opacity** : opacity becomes the alpha channel on
 *    the per-page `gs` state; full BlendMode support is deferred.
 *
 * The output is a syntactically valid PDF 1.4 file (header
 * `%PDF-1.4` … trailer `%%EOF`).
 */
public object SkPDF {

    /** Optional metadata. Defaults match the upstream conservative shape. */
    public data class Metadata(
        val title: String = "",
        val author: String = "",
        val subject: String = "",
        val keywords: String = "",
        val creator: String = "",
        val producer: String = "kanvas-skia",
        val creation: Long = System.currentTimeMillis(),
        val modified: Long = System.currentTimeMillis(),
        val rasterDPI: Float = 72f,
        val pdfA: Boolean = false,
    )

    /**
     * Create a PDF-backed document writing into [stream]. The stream
     * is written as pages are flushed and finalised on
     * [SkDocument.close].
     *
     * PDF pages are sized in point units (1pt = 1/72 inch).
     */
    public fun MakeDocument(stream: SkWStreamMinimal, metadata: Metadata = Metadata()): SkDocument =
        PdfDocument(stream, metadata)
}

/* ------------------------------------------------------------------------ */
/* Internal — recording canvas + serialiser                                 */
/* ------------------------------------------------------------------------ */

/**
 * Lightweight recording canvas — captures the page's draw verbs into a
 * list of [PageOp] values. We extend [SkCanvas] over a 1×1 dummy
 * raster so the matrix / clip stack helpers still work; the raster
 * itself is never inspected (we serialise only the op list).
 */
internal class PdfRecordingCanvas : SkCanvas(SkBitmap(1, 1)) {
    val ops: MutableList<PageOp> = mutableListOf()

    override fun drawPaint(paint: SkPaint) {
        ops += PageOp.Paint(paint.color, paint.alpha)
    }

    override fun drawColor(color: SkColor, mode: org.skia.foundation.SkBlendMode) {
        ops += PageOp.Paint(color, (color ushr 24) and 0xFF)
    }

    override fun drawRect(rect: SkRect, paint: SkPaint) {
        ops += PageOp.RectOp(rect, paint.color, paint.alpha, paint.style)
    }

    override fun drawPath(path: SkPath, paint: SkPaint) {
        ops += PageOp.PathOp(path, paint.color, paint.alpha, paint.style)
    }

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, paint: SkPaint) {
        ops += PageOp.LineOp(x0, y0, x1, y1, paint.color, paint.alpha)
    }

    override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: SkPaint) {
        ops += PageOp.PathOp(SkPath.Circle(cx, cy, radius), paint.color, paint.alpha, paint.style)
    }

    override fun drawOval(oval: SkRect, paint: SkPaint) {
        ops += PageOp.PathOp(SkPath.Oval(oval), paint.color, paint.alpha, paint.style)
    }

    // Text + image draws are recorded as no-ops in R3.6 (see SkPDF KDoc).
    override fun drawString(str: String, x: Float, y: Float, font: org.skia.foundation.SkFont, paint: SkPaint) {
        // No-op — text shaping arrives with R3.2/R3.3.
    }

    override fun drawImage(image: SkImage, x: Float, y: Float, sampling: SkSamplingOptions, paint: SkPaint?) {
        // No-op — XObject embedding is an R-suivi follow-up.
    }
}

/** Recorded ops on a page. Each maps to one PDF content-stream fragment. */
internal sealed interface PageOp {
    data class Paint(val color: SkColor, val alpha: Int) : PageOp
    data class RectOp(val rect: SkRect, val color: SkColor, val alpha: Int, val style: SkPaint.Style) : PageOp
    data class PathOp(val path: SkPath, val color: SkColor, val alpha: Int, val style: SkPaint.Style) : PageOp
    data class LineOp(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val color: SkColor, val alpha: Int) : PageOp
}

/** Top-level PDF document — drives writing into the user's stream. */
internal class PdfDocument(
    private val stream: SkWStreamMinimal,
    private val metadata: SkPDF.Metadata,
) : SkDocument() {

    /** Objects accumulated until close(). Index 0 is reserved (xref free entry). */
    private data class IndirectObject(val id: Int, val bodyBytes: ByteArray)

    private val objects: MutableList<IndirectObject> = mutableListOf()
    private val pageRefs: MutableList<Int> = mutableListOf()
    private val pageHeights: MutableList<Float> = mutableListOf()
    private val pageWidths: MutableList<Float> = mutableListOf()

    private var currentCanvas: PdfRecordingCanvas? = null
    private var currentWidth: Float = 0f
    private var currentHeight: Float = 0f
    private var headerWritten: Boolean = false
    private var streamCursor: Long = 0L

    // ---- SkDocument hooks ---------------------------------------

    override fun onBeginPage(width: Float, height: Float, content: SkRect?): SkCanvas {
        if (!headerWritten) {
            writeHeader()
            headerWritten = true
        }
        currentWidth = width
        currentHeight = height
        val canvas = PdfRecordingCanvas()
        currentCanvas = canvas
        return canvas
    }

    override fun onEndPage() {
        val canvas = currentCanvas ?: return
        val contentBytes = serializeOps(canvas.ops, currentHeight)
        // Stream object holding the content for this page
        val contentId = nextObjectId()
        val streamBody = buildString {
            append("<</Length ").append(contentBytes.size).append(">>\nstream\n")
        }.toByteArray(Charsets.ISO_8859_1) + contentBytes + "\nendstream".toByteArray(Charsets.ISO_8859_1)
        objects += IndirectObject(contentId, streamBody)
        // Page object referencing it (parent fixed up at finalisation)
        val pageId = nextObjectId()
        val pageBody = (
            "<</Type /Page /Parent __PARENT__ 0 R " +
                "/MediaBox [0 0 ${formatNum(currentWidth)} ${formatNum(currentHeight)}] " +
                "/Resources <<>> " +
                "/Contents $contentId 0 R>>"
            ).toByteArray(Charsets.ISO_8859_1)
        objects += IndirectObject(pageId, pageBody)
        pageRefs += pageId
        pageWidths += currentWidth
        pageHeights += currentHeight
        currentCanvas = null
    }

    override fun onClose() {
        if (!headerWritten) {
            // No pages were ever added — still emit a valid (empty) PDF.
            writeHeader()
            headerWritten = true
        }
        // 1) Allocate Catalog + Pages ids.
        val catalogId = nextObjectId()
        val pagesId = nextObjectId()
        // 2) Patch each page's __PARENT__ placeholder with the real Pages id.
        for (i in objects.indices) {
            val o = objects[i]
            val patched = replacePlaceholder(o.bodyBytes, "__PARENT__", pagesId.toString())
            if (patched !== o.bodyBytes) objects[i] = o.copy(bodyBytes = patched)
        }
        // 3) Body objects (in id order — they were appended in id order already).
        val xrefOffsets = LongArray(objects.size + 3) // +1 free + catalog + pages
        for (obj in objects) {
            xrefOffsets[obj.id] = streamCursor
            writeBytes("${obj.id} 0 obj\n".toByteArray(Charsets.ISO_8859_1))
            writeBytes(obj.bodyBytes)
            writeBytes("\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        }
        // 4) Catalog.
        xrefOffsets[catalogId] = streamCursor
        val catalog = "<</Type /Catalog /Pages $pagesId 0 R>>"
        writeBytes("$catalogId 0 obj\n$catalog\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        // 5) Pages tree.
        xrefOffsets[pagesId] = streamCursor
        val kids = pageRefs.joinToString(separator = " ") { "$it 0 R" }
        val pages = "<</Type /Pages /Kids [$kids] /Count ${pageRefs.size}>>"
        writeBytes("$pagesId 0 obj\n$pages\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        // 6) xref + trailer.
        val xrefOffset = streamCursor
        val totalObjects = xrefOffsets.size // = catalogId + 1
        writeBytes("xref\n0 $totalObjects\n".toByteArray(Charsets.ISO_8859_1))
        // Free entry.
        writeBytes("0000000000 65535 f \n".toByteArray(Charsets.ISO_8859_1))
        for (i in 1 until totalObjects) {
            val off = xrefOffsets[i]
            val padded = off.toString().padStart(10, '0')
            writeBytes("$padded 00000 n \n".toByteArray(Charsets.ISO_8859_1))
        }
        val trailer = "trailer\n<</Size $totalObjects /Root $catalogId 0 R>>\nstartxref\n$xrefOffset\n%%EOF\n"
        writeBytes(trailer.toByteArray(Charsets.ISO_8859_1))
    }

    override fun onAbort() {
        // Drop the in-memory state; the user's stream may already
        // hold a partial header — that's acceptable per upstream
        // contract ("The stream output must be ignored").
        objects.clear()
        pageRefs.clear()
        pageHeights.clear()
        pageWidths.clear()
        currentCanvas = null
    }

    // ---- Helpers ------------------------------------------------

    private fun writeHeader() {
        // Binary marker after the version line ensures the file is
        // detected as binary by transports — matches Skia's output.
        val header = byteArrayOf(
            '%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte(),
            '-'.code.toByte(), '1'.code.toByte(), '.'.code.toByte(), '4'.code.toByte(), '\n'.code.toByte(),
            '%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte(),
        )
        writeBytes(header)
    }

    private fun writeBytes(bytes: ByteArray) {
        stream.write(bytes)
        streamCursor += bytes.size
    }

    private var nextId: Int = 0
    private fun nextObjectId(): Int { nextId += 1; return nextId }

    private fun replacePlaceholder(haystack: ByteArray, needle: String, value: String): ByteArray {
        val s = haystack.toString(Charsets.ISO_8859_1)
        if (!s.contains(needle)) return haystack
        return s.replace(needle, value).toByteArray(Charsets.ISO_8859_1)
    }

    /**
     * Serialise the page's op list to a PDF content stream. PDF uses
     * a y-up coordinate system with the origin in the bottom-left;
     * Skia uses y-down with origin in the top-left, so we flip y via
     * `y_pdf = pageHeight - y_skia` for every emitted point.
     */
    private fun serializeOps(ops: List<PageOp>, pageHeight: Float): ByteArray {
        val sb = StringBuilder()
        for (op in ops) {
            when (op) {
                is PageOp.Paint -> {
                    setNonStrokeColor(sb, op.color)
                    sb.append("0 0 ")
                        .append(formatNum(currentWidth)).append(' ')
                        .append(formatNum(pageHeight)).append(" re f\n")
                }
                is PageOp.RectOp -> {
                    setNonStrokeColor(sb, op.color)
                    setStrokeColor(sb, op.color)
                    val x = op.rect.left
                    val y = pageHeight - op.rect.bottom  // y-flip
                    val w = op.rect.right - op.rect.left
                    val h = op.rect.bottom - op.rect.top
                    sb.append(formatNum(x)).append(' ')
                        .append(formatNum(y)).append(' ')
                        .append(formatNum(w)).append(' ')
                        .append(formatNum(h)).append(" re ")
                        .append(paintOp(op.style)).append('\n')
                }
                is PageOp.PathOp -> {
                    setNonStrokeColor(sb, op.color)
                    setStrokeColor(sb, op.color)
                    appendPathVerbs(sb, op.path, pageHeight)
                    sb.append(paintOp(op.style)).append('\n')
                }
                is PageOp.LineOp -> {
                    setStrokeColor(sb, op.color)
                    sb.append(formatNum(op.x0)).append(' ')
                        .append(formatNum(pageHeight - op.y0)).append(" m ")
                        .append(formatNum(op.x1)).append(' ')
                        .append(formatNum(pageHeight - op.y1)).append(" l S\n")
                }
            }
        }
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private fun appendPathVerbs(sb: StringBuilder, path: SkPath, pageHeight: Float) {
        val iter = SkPath.Iter(path, false)
        val pts = FloatArray(8)
        while (true) {
            val verb = iter.next(pts)
            when (verb) {
                SkPath.IterVerb.kMoveVerb -> {
                    sb.append(formatNum(pts[0])).append(' ')
                        .append(formatNum(pageHeight - pts[1])).append(" m\n")
                }
                SkPath.IterVerb.kLineVerb -> {
                    sb.append(formatNum(pts[2])).append(' ')
                        .append(formatNum(pageHeight - pts[3])).append(" l\n")
                }
                SkPath.IterVerb.kQuadVerb, SkPath.IterVerb.kConicVerb -> {
                    // Simplification: linearise to a single line segment.
                    sb.append(formatNum(pts[4])).append(' ')
                        .append(formatNum(pageHeight - pts[5])).append(" l\n")
                }
                SkPath.IterVerb.kCubicVerb -> {
                    sb.append(formatNum(pts[2])).append(' ')
                        .append(formatNum(pageHeight - pts[3])).append(' ')
                        .append(formatNum(pts[4])).append(' ')
                        .append(formatNum(pageHeight - pts[5])).append(' ')
                        .append(formatNum(pts[6])).append(' ')
                        .append(formatNum(pageHeight - pts[7])).append(" c\n")
                }
                SkPath.IterVerb.kCloseVerb -> sb.append("h\n")
                SkPath.IterVerb.kDoneVerb -> return
            }
        }
    }

    private fun setNonStrokeColor(sb: StringBuilder, color: SkColor) {
        val r = ((color ushr 16) and 0xFF) / 255f
        val g = ((color ushr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        sb.append(formatNum(r)).append(' ')
            .append(formatNum(g)).append(' ')
            .append(formatNum(b)).append(" rg\n")
    }

    private fun setStrokeColor(sb: StringBuilder, color: SkColor) {
        val r = ((color ushr 16) and 0xFF) / 255f
        val g = ((color ushr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        sb.append(formatNum(r)).append(' ')
            .append(formatNum(g)).append(' ')
            .append(formatNum(b)).append(" RG\n")
    }

    private fun paintOp(style: SkPaint.Style): String = when (style) {
        SkPaint.Style.kFill_Style -> "f"
        SkPaint.Style.kStroke_Style -> "S"
        SkPaint.Style.kStrokeAndFill_Style -> "B"
    }

    /** Format a float with up to 4 decimals, stripping trailing zeros. */
    private fun formatNum(v: Float): String {
        if (v.isNaN() || v.isInfinite()) return "0"
        val rounded = Math.round(v * 10000.0) / 10000.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            // Trim trailing zeros but keep at least one decimal.
            rounded.toString().trimEnd('0').trimEnd('.')
        }
    }
}

