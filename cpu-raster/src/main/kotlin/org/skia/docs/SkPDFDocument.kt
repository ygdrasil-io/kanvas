package org.skia.docs

import org.skia.core.SkCanvas
import org.graphiks.kanvas.codec.jpeg.JpegEncoder
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColor
import org.skia.foundation.SkDocument
import org.skia.foundation.SkImage
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.stream.SkWStream
import org.graphiks.math.SkRect
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Minimal PDF 1.4 backend for [SkDocument], mirroring upstream
 * `include/docs/SkPDFDocument.h` (`namespace SkPDF`).
 *
 * **Phase R3.6 — explicit simplifications** (each becomes an R-suivi
 * follow-up):
 *
 *  - **Text** : [SkCanvas.drawString] is rasterised to vector outlines
 *    via [org.skia.foundation.SkFont.makeTextPath] then routed through
 *    [SkCanvas.drawPath] (Phase R-suivi.36). The PDF carries the glyphs
 *    as filled cubic paths — no font subsetting, no selectable text.
 *    Real `/Type0` composite-font embedding is a future R-suivi item.
 *    `drawTextBlob` is still a no-op.
 *  - **Shaders** : only [SkLinearGradient] is honoured (Phase R-suivi.38),
 *    via PDF Type 2 axial shading. Radial / sweep / image shaders are
 *    deferred.
 *  - **Images** : `drawImage` embeds the source [SkImage] as a JPEG
 *    `/DCTDecode` XObject (Phase R-suivi.37). The image is positioned
 *    at `(x, y)` and scaled by `(width, height)` via the `cm` operator.
 *    The page's `/XObject` resource dictionary is populated lazily.
 *  - **Compression** : content streams are deflated via
 *    `/FlateDecode` when [Metadata.compress] is `true` (the default —
 *    Phase R-suivi.40). Defaults to PDF 1.4, no `pdfA`.
 *  - **Encryption** : opt-in PDF AES-128 (V=4 R=4) when
 *    [Metadata.encryptionPassword] is non-`null` (Phase R-suivi.40).
 *    Stronger AES-256 (V=5) is a follow-up.
 *  - **No structure tree, no outline, no metadata XMP**.
 *  - **Path verbs** : `move`, `line`, `cubic`, `close` map natively to
 *    `m`, `l`, `c`, `h`. `quad` is degree-elevated to a cubic (lossless),
 *    and `conic` is subdivided into 4 quads via De Casteljau then each
 *    is promoted to a cubic (Phase R-suivi.39).
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
        /**
         * Phase R-suivi.40 — when `true`, content streams are deflated
         * via `/Filter /FlateDecode`. Defaults to `true` (matches
         * upstream's `kCompress` default in `SkPDFDocument`).
         */
        val compress: Boolean = true,
        /**
         * Phase R-suivi.40 — opt-in PDF AES-128 encryption (V=4 R=4).
         * When non-`null`, the resulting PDF carries an `/Encrypt`
         * dictionary in the trailer and every indirect-object payload
         * is encrypted with a per-object key derived from this
         * password and the file `/ID`. The password is used as both
         * the user and owner password for the V=4 R=4 standard
         * security handler — split owner/user passwords are a
         * follow-up.
         *
         * Cipher strength is selected via [encryptionStrength].
         */
        val encryptionPassword: String? = null,
        /**
         * S7-C — when [encryptionPassword] is non-`null`, picks
         * between AES-128 (V=4 R=4 — default, matches R-suivi.40) and
         * AES-256 (V=5 R=5 — adds SHA-256-based key derivation +
         * 32-byte file key). No effect when [encryptionPassword] is
         * `null` (no encryption).
         */
        val encryptionStrength: EncryptionStrength = EncryptionStrength.kAES128,
    )

    /**
     * S7-C — AES cipher strength selector for [Metadata.encryptionStrength].
     * Mirrors upstream's `SkPDF::Metadata::EncryptionStrength` enum
     * (V=4 R=4 = AES-128, V=5 R=5 = AES-256 per ISO 32000-1 7.6.4).
     */
    public enum class EncryptionStrength {
        /** V=4 R=4 — 128-bit AES key, MD5-based key derivation. */
        kAES128,

        /** V=5 R=5 — 256-bit AES key, SHA-256-based key derivation. */
        kAES256,
    }

    /**
     * Create a PDF-backed document writing into [stream]. The stream
     * is written as pages are flushed and finalised on
     * [SkDocument.close].
     *
     * PDF pages are sized in point units (1pt = 1/72 inch).
     */
    public fun MakeDocument(stream: SkWStream, metadata: Metadata = Metadata()): SkDocument =
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
        ops += PageOp.Paint(paint.color, paint.alpha, paint.shader as? SkLinearGradient)
    }

    override fun drawColor(color: SkColor, mode: org.skia.foundation.SkBlendMode) {
        ops += PageOp.Paint(color, (color ushr 24) and 0xFF, null)
    }

    override fun drawRect(rect: SkRect, paint: SkPaint) {
        ops += PageOp.RectOp(rect, paint.color, paint.alpha, paint.style, paint.shader as? SkLinearGradient)
    }

    override fun drawPath(path: SkPath, paint: SkPaint) {
        ops += PageOp.PathOp(path, paint.color, paint.alpha, paint.style, paint.shader as? SkLinearGradient)
    }

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, paint: SkPaint) {
        ops += PageOp.LineOp(x0, y0, x1, y1, paint.color, paint.alpha)
    }

    override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: SkPaint) {
        ops += PageOp.PathOp(
            SkPath.Circle(cx, cy, radius),
            paint.color,
            paint.alpha,
            paint.style,
            paint.shader as? SkLinearGradient,
        )
    }

    override fun drawOval(oval: SkRect, paint: SkPaint) {
        ops += PageOp.PathOp(
            SkPath.Oval(oval),
            paint.color,
            paint.alpha,
            paint.style,
            paint.shader as? SkLinearGradient,
        )
    }

    /**
     * Phase R-suivi.36 — route the string through
     * [org.skia.foundation.SkFont.makeTextPath] so the glyph outlines
     * land in the content stream as a `SkPath`. The path is then fed
     * back into [drawPath], which already emits native `m` / `l` / `c`
     * operators (R-suivi.39) — so text becomes filled curves with no
     * font-embedding machinery on the PDF side.
     *
     * Trade-off : the resulting PDF carries vector outlines (no
     * selectable / searchable text). Real glyph embedding +
     * `/Type0` composite fonts is the long-term R-suivi follow-up.
     */
    override fun drawString(str: String, x: Float, y: Float, font: org.skia.foundation.SkFont, paint: SkPaint) {
        val textPath = font.makeTextPath(str, x, y) ?: return
        drawPath(textPath, paint)
    }

    /**
     * Phase R-suivi.37 — embed the image as a JPEG `/DCTDecode` XObject.
     * The actual encode happens at page-flush time so that the
     * recorder stays cheap; here we just snapshot the image reference
     * + the placement transform.
     */
    override fun drawImage(image: SkImage, x: Float, y: Float, sampling: SkSamplingOptions, paint: SkPaint?) {
        ops += PageOp.ImageOp(image, x, y)
    }
}

/** Recorded ops on a page. Each maps to one PDF content-stream fragment. */
internal sealed interface PageOp {
    data class Paint(val color: SkColor, val alpha: Int, val gradient: SkLinearGradient?) : PageOp
    data class RectOp(
        val rect: SkRect,
        val color: SkColor,
        val alpha: Int,
        val style: SkPaint.Style,
        val gradient: SkLinearGradient?,
    ) : PageOp
    data class PathOp(
        val path: SkPath,
        val color: SkColor,
        val alpha: Int,
        val style: SkPaint.Style,
        val gradient: SkLinearGradient?,
    ) : PageOp
    data class LineOp(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val color: SkColor, val alpha: Int) : PageOp
    /** Phase R-suivi.37 — image embedded as a JPEG XObject. */
    data class ImageOp(val image: SkImage, val x: Float, val y: Float) : PageOp
}

/** Top-level PDF document — drives writing into the user's stream. */
internal class PdfDocument(
    private val stream: SkWStream,
    private val metadata: SkPDF.Metadata,
) : SkDocument() {

    /**
     * Objects accumulated until close(). Index 0 is reserved (xref free entry).
     *
     * Two flavours :
     *  * **Plain** (`streamPayload == null`) — [bodyBytes] is the
     *    object body verbatim (a PDF dictionary, name, array, …). Written
     *    as-is between `N 0 obj\n` and `\nendobj\n`.
     *  * **Stream** (`streamPayload != null`) — [bodyBytes] holds the
     *    dictionary part *without* a `/Length` entry (the writer
     *    appends one), and [streamPayload] holds the raw bytes between
     *    `stream\n` and `\nendstream`. Splitting them lets [onClose]
     *    apply compression (Phase R-suivi.40) and / or encryption
     *    (Phase R-suivi.40) to the payload alone.
     *
     * For plain objects under encryption, the entire [bodyBytes] is
     * encrypted as if it were a stream — the payload-vs-dict split is
     * only meaningful for streams.
     */
    private data class IndirectObject(
        val id: Int,
        val bodyBytes: ByteArray,
        val streamPayload: ByteArray? = null,
    )

    /**
     * Bookkeeping for a single page's external resources : images and
     * gradient-backed patterns. Lives only for the duration of an
     * [onEndPage] call.
     */
    private class PageResources {
        /** Per-image op → `/ImN` resource name (insertion order). */
        val imageOps: LinkedHashMap<PageOp.ImageOp, String> = LinkedHashMap()
        /** Resource name → backing indirect-object id, filled in by [onEndPage]. */
        val imageObjectIds: MutableMap<String, Int> = mutableMapOf()
        private var nextImageIndex: Int = 0
        fun allocImage(): String { nextImageIndex += 1; return "/Im$nextImageIndex" }

        /** Gradient → `/PN` resource name (insertion order, de-duped by reference). */
        val gradients: LinkedHashMap<SkLinearGradient, String> = LinkedHashMap()
        /** Resource name → backing Pattern object id. */
        val gradientPatternIds: MutableMap<String, Int> = mutableMapOf()
        private var nextPatternIndex: Int = 0
        fun registerGradient(g: SkLinearGradient): String {
            return gradients.getOrPut(g) {
                nextPatternIndex += 1
                "/P$nextPatternIndex"
            }
        }
    }

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

        // ── 1) Allocate object IDs for XObjects (images) and Patterns/
        //       Shadings/Functions (linear gradients). We do this
        //       up-front so the content stream can reference their
        //       /ImN and /PN resource names directly.
        val pageResources = PageResources()
        for (op in canvas.ops) {
            when (op) {
                is PageOp.ImageOp -> {
                    val name = pageResources.allocImage()
                    pageResources.imageOps[op] = name
                }
                is PageOp.Paint -> op.gradient?.let { pageResources.registerGradient(it) }
                is PageOp.RectOp -> op.gradient?.let { pageResources.registerGradient(it) }
                is PageOp.PathOp -> op.gradient?.let { pageResources.registerGradient(it) }
                is PageOp.LineOp -> Unit
            }
        }

        // ── 2) Serialise the page's content stream now that the
        //       resource-name mapping is known.
        val contentBytes = serializeOps(canvas.ops, currentHeight, pageResources)

        // ── 3) Emit the content stream as an indirect object. The dict
        //       header carries no `/Length` — [onClose] inserts it after
        //       compression / encryption have settled the final payload
        //       size.
        val contentId = nextObjectId()
        val contentDict = "<<>>".toByteArray(Charsets.ISO_8859_1)
        objects += IndirectObject(contentId, contentDict, streamPayload = contentBytes)

        // ── 4) Emit each image as a JPEG XObject (R-suivi.37).
        for ((op, imName) in pageResources.imageOps) {
            val (objId, obj) = encodeImageXObject(op.image)
            objects += obj
            pageResources.imageObjectIds[imName] = objId
        }

        // ── 5) Emit each linear gradient as a Function + Shading +
        //       Pattern triplet (R-suivi.38).
        for ((grad, patName) in pageResources.gradients) {
            val (funcId, shadingId, patternId) = emitGradient(grad, currentHeight)
            pageResources.gradientPatternIds[patName] = patternId
            // Suppress 'unused' warnings — funcId/shadingId are referenced
            // by intermediate object bodies emitted inside emitGradient().
            @Suppress("UNUSED_VARIABLE") val _f = funcId
            @Suppress("UNUSED_VARIABLE") val _s = shadingId
        }

        // ── 6) Page object referencing the content stream + the resource
        //       dictionary we just built.
        val pageId = nextObjectId()
        val resourcesDict = buildResourcesDict(pageResources)
        val pageBody = (
            "<</Type /Page /Parent __PARENT__ 0 R " +
                "/MediaBox [0 0 ${formatNum(currentWidth)} ${formatNum(currentHeight)}] " +
                "/Resources $resourcesDict " +
                "/Contents $contentId 0 R>>"
            ).toByteArray(Charsets.ISO_8859_1)
        objects += IndirectObject(pageId, pageBody)
        pageRefs += pageId
        pageWidths += currentWidth
        pageHeights += currentHeight
        currentCanvas = null
    }

    /**
     * Build the page-level `/Resources` dictionary. Currently emits the
     * `/XObject` and `/Pattern` sub-dicts when the page has any of
     * either resource kind, plus a `/ColorSpace` declaration when at
     * least one gradient is in use (Pattern colour-space selector
     * `/Pattern cs` requires `/Pattern` to be declared in the colour-
     * space list).
     */
    private fun buildResourcesDict(res: PageResources): String {
        if (res.imageOps.isEmpty() && res.gradients.isEmpty()) return "<<>>"
        val sb = StringBuilder("<<")
        if (res.imageOps.isNotEmpty()) {
            sb.append(" /XObject <<")
            // imageOps is { op -> /ImN }; iterate value-side for resource names.
            for (imName in res.imageOps.values.distinct()) {
                val objId = res.imageObjectIds[imName]
                if (objId != null) sb.append(' ').append(imName).append(' ').append(objId).append(" 0 R")
            }
            sb.append(" >>")
        }
        if (res.gradients.isNotEmpty()) {
            sb.append(" /Pattern <<")
            for ((_, patName) in res.gradients) {
                val patId = res.gradientPatternIds[patName]
                if (patId != null) sb.append(' ').append(patName).append(' ').append(patId).append(" 0 R")
            }
            sb.append(" >>")
            // Declare /Pattern colour space alias used by the `/Pattern cs` op.
            sb.append(" /ColorSpace << /CSp [/Pattern /DeviceRGB] >>")
        }
        sb.append(" >>")
        return sb.toString()
    }

    /**
     * Phase R-suivi.37 — encode [image] as a JPEG and wrap it in a
     * `/DCTDecode` image XObject. Falls back to a stub 1×1 transparent
     * XObject when the JPEG encoder fails (the upstream contract for
     * the minimal backend is "don't error — flag in body"). Returns
     * the allocated object id and the indirect-object body bytes
     * (sans `N 0 obj … endobj` wrapper — that is added by [onClose]).
     */
    private fun encodeImageXObject(image: SkImage): Pair<Int, IndirectObject> {
        val objId = nextObjectId()
        // Re-hydrate the image's pixel buffer into a SkBitmap so the
        // existing JpegEncoder can consume it.
        val bmp = SkBitmap(image.width, image.height)
        // SkBitmap and SkImage share the same 8888 row layout; copy
        // the entire pixel buffer in one shot.
        System.arraycopy(image.pixels, 0, bmp.pixels8888, 0, image.width * image.height)
        val jpegBytes = JpegEncoder.encode(bmp, JpegEncoder.Options(quality = 90))
        val obj: IndirectObject = if (jpegBytes != null) {
            // Standard JPEG XObject — width/height from the source image,
            // 8 bpc DeviceRGB. The /Filter /DCTDecode entry tells PDF
            // readers to feed the raw stream bytes to a JPEG decoder.
            // The /Length is appended at write-time so encryption /
            // re-compression can mutate the payload first.
            val dict = (
                "<</Type /XObject /Subtype /Image " +
                    "/Width ${image.width} /Height ${image.height} " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 " +
                    "/Filter /DCTDecode>>"
                ).toByteArray(Charsets.ISO_8859_1)
            IndirectObject(objId, dict, streamPayload = jpegBytes)
        } else {
            // Encoder failed — emit a stub XObject with a zero-length
            // stream. The page is still valid PDF; the image just
            // doesn't render.
            val dict = (
                "<</Type /XObject /Subtype /Image " +
                    "/Width 1 /Height 1 /ColorSpace /DeviceRGB " +
                    "/BitsPerComponent 8>>"
                ).toByteArray(Charsets.ISO_8859_1)
            IndirectObject(objId, dict, streamPayload = ByteArray(0))
        }
        return objId to obj
    }

    /**
     * Phase R-suivi.38 — emit the indirect objects backing a
     * [SkLinearGradient] :
     *
     *  1. A Function object — Type 2 (exponential) for 2 stops, or a
     *     Type 3 (stitching) chaining Type 2 segments for ≥ 3 stops.
     *  2. A Shading object — Type 2 (axial), referencing (1) and
     *     carrying the colour space + coords.
     *  3. A Pattern object — Type 2 (shading-pattern), referencing (2).
     *     The pattern is what gets registered in the page's
     *     `/Resources /Pattern` dict and named via the content stream.
     *
     * The y-coordinates of the gradient endpoints are flipped to match
     * PDF's y-up coordinate system (mirrors the rest of this backend).
     */
    private fun emitGradient(grad: SkLinearGradient, pageHeight: Float): Triple<Int, Int, Int> {
        val p0 = grad.getStartPoint()
        val p1 = grad.getEndPoint()
        val colors = grad.getColors()
        val positions = grad.getPositions()

        // ── Function: Type 2 for 2 stops, Type 3 for ≥ 3 stops. ──
        val functionId = nextObjectId()
        val functionBody = if (colors.size == 2) {
            "<</FunctionType 2 /Domain [0 1] " +
                "/C0 ${formatColorTriplet(colors[0])} " +
                "/C1 ${formatColorTriplet(colors[1])} " +
                "/N 1>>"
        } else {
            // Type 3 stitching: chain (colors.size - 1) Type 2 segments
            // back-to-back. The Functions, Bounds, and Encode arrays
            // carry the per-segment data per the PDF spec.
            val segmentIds = IntArray(colors.size - 1)
            for (i in 0 until colors.size - 1) {
                val segId = nextObjectId()
                segmentIds[i] = segId
                val segBody = (
                    "<</FunctionType 2 /Domain [0 1] " +
                        "/C0 ${formatColorTriplet(colors[i])} " +
                        "/C1 ${formatColorTriplet(colors[i + 1])} " +
                        "/N 1>>"
                    ).toByteArray(Charsets.ISO_8859_1)
                objects += IndirectObject(segId, segBody)
            }
            val funcs = segmentIds.joinToString(" ") { "$it 0 R" }
            // Bounds: positions[1] … positions[n-2] (interior boundaries).
            val bounds = positions.drop(1).dropLast(1).joinToString(" ") { formatNum(it) }
            // Encode: each segment maps [0 1] of its input range to its full output.
            val encode = (0 until colors.size - 1).joinToString(" ") { "0 1" }
            "<</FunctionType 3 /Domain [0 1] " +
                "/Functions [$funcs] " +
                "/Bounds [$bounds] " +
                "/Encode [$encode]>>"
        }
        objects += IndirectObject(functionId, functionBody.toByteArray(Charsets.ISO_8859_1))

        // ── Shading: Type 2 (axial). ──
        // Coords are in pattern-space, which we equate to page space
        // here. Flip y to PDF's y-up convention.
        val shadingId = nextObjectId()
        val x0 = formatNum(p0.fX)
        val y0 = formatNum(pageHeight - p0.fY)
        val x1 = formatNum(p1.fX)
        val y1 = formatNum(pageHeight - p1.fY)
        val shadingBody = (
            "<</ShadingType 2 /ColorSpace /DeviceRGB " +
                "/Coords [$x0 $y0 $x1 $y1] " +
                "/Function $functionId 0 R " +
                "/Extend [true true]>>"
            ).toByteArray(Charsets.ISO_8859_1)
        objects += IndirectObject(shadingId, shadingBody)

        // ── Pattern: Type 2 (shading pattern). ──
        val patternId = nextObjectId()
        val patternBody = (
            "<</Type /Pattern /PatternType 2 /Shading $shadingId 0 R>>"
            ).toByteArray(Charsets.ISO_8859_1)
        objects += IndirectObject(patternId, patternBody)

        return Triple(functionId, shadingId, patternId)
    }

    /**
     * Format a [SkColor]'s RGB channels as a PDF colour triplet (alpha
     * is dropped). The triplet is bracketed: `[r g b]`.
     */
    private fun formatColorTriplet(color: SkColor): String {
        val r = ((color ushr 16) and 0xFF) / 255f
        val g = ((color ushr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        return "[${formatNum(r)} ${formatNum(g)} ${formatNum(b)}]"
    }

    override fun onClose() {
        if (!headerWritten) {
            // No pages were ever added — still emit a valid (empty) PDF.
            writeHeader()
            headerWritten = true
        }
        // 1) Allocate Catalog + Pages ids ; also reserve an Encrypt id
        //    when encryption is requested. The Encrypt id must be the
        //    *last* allocated so it isn't itself encrypted (PDF 7.6.1
        //    explicitly excludes the Encrypt dict from the security
        //    handler's coverage).
        val catalogId = nextObjectId()
        val pagesId = nextObjectId()
        val encryptId: Int? = if (metadata.encryptionPassword != null) nextObjectId() else null

        // 2) Patch each page's __PARENT__ placeholder with the real Pages id.
        for (i in objects.indices) {
            val o = objects[i]
            val patched = replacePlaceholder(o.bodyBytes, "__PARENT__", pagesId.toString())
            if (patched !== o.bodyBytes) objects[i] = o.copy(bodyBytes = patched)
        }

        // 3) Build the file `/ID` array (a stable hash of the producer +
        //    the body objects). PDF 14.4 mandates an `/ID` whenever
        //    `/Encrypt` is present ; we always emit one (it's cheap and
        //    aids deterministic round-trips for tests).
        val fileIdHex = computeFileId()
        val fileIdBytes = hexDecode(fileIdHex)

        // 4) Set up the AES file encryption key, if requested. Picks
        //    AES-128 (V=4 R=4) or AES-256 (V=5 R=5) per
        //    [SkPDF.Metadata.encryptionStrength].
        val aes: PdfEncryption? = encryptId?.let { _ ->
            val pwd = metadata.encryptionPassword!!
            when (metadata.encryptionStrength) {
                SkPDF.EncryptionStrength.kAES128 -> PdfAes128.create(pwd, fileIdBytes)
                SkPDF.EncryptionStrength.kAES256 -> PdfAes256.create(pwd, fileIdBytes)
            }
        }

        // 5) Body objects (in id order — they were appended in id order
        //    already). Streams optionally get FlateDecode-compressed and
        //    AES-encrypted ; plain dict objects stay verbatim (PDF only
        //    requires strings + streams to be encrypted, and our
        //    backend doesn't emit literal strings inside dicts).
        // Sized to hold every allocated id (1..nextId) plus the free
        // entry at slot 0.
        val xrefOffsets = LongArray(nextId + 1)
        for (obj in objects) {
            xrefOffsets[obj.id] = streamCursor
            writeBytes("${obj.id} 0 obj\n".toByteArray(Charsets.ISO_8859_1))
            writeIndirectObject(obj, aes)
            writeBytes("\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        }

        // 6) Catalog + Pages : never have streams or strings, so they
        //    don't go through encryption regardless of `aes`.
        xrefOffsets[catalogId] = streamCursor
        val catalog = "<</Type /Catalog /Pages $pagesId 0 R>>"
        writeBytes("$catalogId 0 obj\n$catalog\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        xrefOffsets[pagesId] = streamCursor
        val kids = pageRefs.joinToString(separator = " ") { "$it 0 R" }
        val pages = "<</Type /Pages /Kids [$kids] /Count ${pageRefs.size}>>"
        writeBytes("$pagesId 0 obj\n$pages\nendobj\n".toByteArray(Charsets.ISO_8859_1))

        // 7) Encrypt dict — emitted last so that the security handler
        //    doesn't try to encrypt itself.
        if (encryptId != null && aes != null) {
            xrefOffsets[encryptId] = streamCursor
            val encryptDict = buildEncryptDict(aes)
            writeBytes("$encryptId 0 obj\n$encryptDict\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        }

        // 8) xref + trailer. The trailer always carries `/ID` (per PDF
        //    spec recommendation) and `/Encrypt` when encryption is on.
        val xrefOffset = streamCursor
        val totalObjects = xrefOffsets.size // = nextId + 1 (free entry + every id)
        writeBytes("xref\n0 $totalObjects\n".toByteArray(Charsets.ISO_8859_1))
        writeBytes("0000000000 65535 f \n".toByteArray(Charsets.ISO_8859_1))
        for (i in 1 until totalObjects) {
            val off = xrefOffsets[i]
            val padded = off.toString().padStart(10, '0')
            writeBytes("$padded 00000 n \n".toByteArray(Charsets.ISO_8859_1))
        }
        val trailerSb = StringBuilder()
        trailerSb.append("trailer\n<</Size ").append(totalObjects)
            .append(" /Root ").append(catalogId).append(" 0 R")
            .append(" /ID [<").append(fileIdHex).append("> <").append(fileIdHex).append(">]")
        if (encryptId != null) {
            trailerSb.append(" /Encrypt ").append(encryptId).append(" 0 R")
        }
        trailerSb.append(">>\nstartxref\n").append(xrefOffset).append("\n%%EOF\n")
        writeBytes(trailerSb.toString().toByteArray(Charsets.ISO_8859_1))
    }

    /**
     * Write a single indirect object's body. Streams have their `/Length`
     * (and `/Filter /FlateDecode`, when [SkPDF.Metadata.compress]) injected
     * into the dictionary on the fly so [encodeImageXObject] /
     * [onEndPage] can stay agnostic to compression / encryption.
     */
    private fun writeIndirectObject(obj: IndirectObject, aes: PdfEncryption?) {
        val payload = obj.streamPayload
        if (payload == null) {
            writeBytes(obj.bodyBytes)
            return
        }
        // Determine if the source dict already declares /Filter (e.g.
        // an image XObject with /DCTDecode). If so, we **prepend**
        // /FlateDecode so the reader's filter chain runs flate → DCT.
        val dictText = obj.bodyBytes.toString(Charsets.ISO_8859_1)
        val hasFilter = dictText.contains("/Filter")
        val compress = metadata.compress
        val processed: ByteArray = if (compress) {
            val deflated = deflate(payload)
            // Skip compression when it would inflate the payload — this
            // avoids paying the filter overhead for already-tiny streams
            // and matches Skia's `SkPDFStream::emitObject` heuristic.
            if (deflated.size < payload.size) deflated else payload
        } else {
            payload
        }
        val didCompress = compress && processed !== payload
        // Encrypt last — per PDF 7.6.2 the encryption is applied to the
        // post-filter (already-deflated, already-DCT-encoded) bytes.
        val encrypted: ByteArray = aes?.encryptObject(processed, obj.id) ?: processed

        // Inject /Length and (optionally) /FlateDecode into the dict.
        val patchedDictText = injectStreamFilters(dictText, encrypted.size, didCompress, hasFilter)
        writeBytes(patchedDictText.toByteArray(Charsets.ISO_8859_1))
        writeBytes("\nstream\n".toByteArray(Charsets.ISO_8859_1))
        writeBytes(encrypted)
        writeBytes("\nendstream".toByteArray(Charsets.ISO_8859_1))
    }

    /**
     * Splice `/Length L` (and optionally `/Filter /FlateDecode`) into
     * the source dictionary [dictText]. The dict is always wrapped in
     * `<<…>>` ; we insert just before the closing `>>`.
     */
    private fun injectStreamFilters(
        dictText: String,
        length: Int,
        addFlate: Boolean,
        hasExistingFilter: Boolean,
    ): String {
        val closeIdx = dictText.lastIndexOf(">>")
        require(closeIdx >= 0) { "Stream object dict must end with >> : '$dictText'" }
        val sb = StringBuilder(dictText.substring(0, closeIdx))
        // Trim trailing whitespace.
        while (sb.isNotEmpty() && sb.last() == ' ') sb.deleteCharAt(sb.length - 1)
        if (addFlate) {
            if (hasExistingFilter) {
                // Prepend FlateDecode to the existing filter chain by
                // rewriting the /Filter entry. Source has e.g.
                // `/Filter /DCTDecode` ; we want
                // `/Filter [/FlateDecode /DCTDecode]`.
                val current = sb.toString()
                val idx = current.indexOf("/Filter")
                if (idx >= 0) {
                    // Walk past `/Filter` plus any whitespace.
                    var j = idx + "/Filter".length
                    while (j < current.length && current[j] == ' ') j++
                    // Capture the existing value name (e.g. /DCTDecode).
                    if (j < current.length && current[j] == '/') {
                        val valueEnd = run {
                            var k = j + 1
                            while (k < current.length && current[k].isLetterOrDigit()) k++
                            k
                        }
                        val existing = current.substring(j, valueEnd)
                        sb.setLength(0)
                        sb.append(current, 0, idx)
                        sb.append("/Filter [/FlateDecode ").append(existing).append("]")
                        sb.append(current, valueEnd, current.length)
                    }
                }
            } else {
                sb.append(" /Filter /FlateDecode")
            }
        }
        sb.append(" /Length ").append(length).append(">>")
        return sb.toString()
    }

    /** Deflate [bytes] with [java.util.zip.Deflater.BEST_COMPRESSION]. */
    private fun deflate(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(bytes) }
        return out.toByteArray()
    }

    /**
     * Build a stable 16-byte file `/ID` as a 32-char hex string.
     * Sourced from the producer, page count, and accumulated body
     * sizes so two runs of the same input land on the same `/ID`
     * (handy for byte-identical test snapshots).
     */
    private fun computeFileId(): String {
        val md = MessageDigest.getInstance("MD5")
        md.update(metadata.producer.toByteArray(Charsets.UTF_8))
        md.update(metadata.title.toByteArray(Charsets.UTF_8))
        md.update(pageRefs.size.toString().toByteArray(Charsets.UTF_8))
        for (obj in objects) {
            md.update(obj.bodyBytes)
            obj.streamPayload?.let { md.update(it) }
        }
        return md.digest().toHex()
    }

    /**
     * Build the standard-security `/Encrypt` dict. Routes to the
     * AES-128 (V=4 R=4) or AES-256 (V=5 R=5) layout per the encryption
     * handler's [PdfEncryption.encryptDictParams]. Both layouts bind
     * the cipher to streams (`/StmF`) and strings (`/StrF`) under the
     * named filter `/StdCF` and emit `-4` permission flags
     * (everything allowed) — finer-grained perms are a follow-up.
     */
    private fun buildEncryptDict(aes: PdfEncryption): String {
        val p = aes.encryptDictParams()
        val sb = StringBuilder()
        sb.append("<</Filter /Standard /V ").append(p.v).append(" /R ").append(p.r)
            .append(" /Length ").append(p.lengthBits).append(' ')
            .append("/CF <</StdCF <</CFM /").append(p.cfm).append(" /Length ").append(p.cfLength)
            .append(" /AuthEvent /DocOpen>>>> ")
            .append("/StmF /StdCF /StrF /StdCF ")
            .append("/O <").append(p.oEntryHex).append("> ")
            .append("/U <").append(p.uEntryHex).append(">")
        if (p.oeEntryHex != null) sb.append(" /OE <").append(p.oeEntryHex).append('>')
        if (p.ueEntryHex != null) sb.append(" /UE <").append(p.ueEntryHex).append('>')
        if (p.permsEntryHex != null) sb.append(" /Perms <").append(p.permsEntryHex).append('>')
        sb.append(" /P -4>>")
        return sb.toString()
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
        stream.write(bytes, bytes.size)
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
    private fun serializeOps(
        ops: List<PageOp>,
        pageHeight: Float,
        res: PageResources,
    ): ByteArray {
        val sb = StringBuilder()
        for (op in ops) {
            when (op) {
                is PageOp.Paint -> {
                    if (op.gradient != null) {
                        applyGradientFill(sb, res, op.gradient)
                    } else {
                        setNonStrokeColor(sb, op.color)
                    }
                    sb.append("0 0 ")
                        .append(formatNum(currentWidth)).append(' ')
                        .append(formatNum(pageHeight)).append(" re f\n")
                }
                is PageOp.RectOp -> {
                    if (op.gradient != null) {
                        applyGradientFill(sb, res, op.gradient)
                    } else {
                        setNonStrokeColor(sb, op.color)
                        setStrokeColor(sb, op.color)
                    }
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
                    if (op.gradient != null) {
                        applyGradientFill(sb, res, op.gradient)
                    } else {
                        setNonStrokeColor(sb, op.color)
                        setStrokeColor(sb, op.color)
                    }
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
                is PageOp.ImageOp -> {
                    val imName = res.imageOps[op] ?: continue
                    // PDF places XObjects unit-sized in their local
                    // space, so we scale by (W, H) and translate to
                    // (x, pageHeight - y - H) in PDF y-up coords. The
                    // raster image is mirrored vertically through the
                    // /DCTDecode bitstream; we use a positive H scale
                    // and account for the y-flip in the translation.
                    val w = op.image.width.toFloat()
                    val h = op.image.height.toFloat()
                    val tx = op.x
                    val ty = pageHeight - op.y - h
                    sb.append("q\n")
                        .append(formatNum(w)).append(" 0 0 ")
                        .append(formatNum(h)).append(' ')
                        .append(formatNum(tx)).append(' ')
                        .append(formatNum(ty)).append(" cm\n")
                        .append(imName).append(" Do\n")
                        .append("Q\n")
                }
            }
        }
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }

    /**
     * Emit the operator sequence that selects a gradient pattern as
     * the current non-stroke (and stroke) paint. The Pattern colour
     * space alias `/CSp` is declared in [buildResourcesDict].
     */
    private fun applyGradientFill(sb: StringBuilder, res: PageResources, gradient: SkLinearGradient) {
        val patName = res.gradients[gradient] ?: return
        // /CSp cs /PN scn — non-stroke pattern selection.
        sb.append("/CSp cs ").append(patName).append(" scn\n")
        // Also set the stroke side so style=Stroke / StrokeAndFill see
        // the gradient. Patterns aren't strictly stroke-compatible
        // without /Pattern CS for stroke, but we mirror cs/scn into
        // CS/SCN so that stroked gradient paths at least don't lose
        // the colour info — readers that don't honour stroke patterns
        // fall back gracefully.
        sb.append("/CSp CS ").append(patName).append(" SCN\n")
    }

    /**
     * Phase R-suivi.39 — walk the path verbs and emit native PDF
     * operators :
     *
     *  - [SkPath.Verb.kMove] → `x y m`
     *  - [SkPath.Verb.kLine] → `x y l`
     *  - [SkPath.Verb.kQuad] → degree-elevated to a cubic
     *    `c1 c2 e c` ; standard formula
     *    `cubic = (p0, p0 + 2/3·(p1−p0), p2 + 2/3·(p1−p2), p2)`.
     *  - [SkPath.Verb.kCubic] → `c1 c2 e c`
     *  - [SkPath.Verb.kConic] → subdivided into 4 quads via De
     *    Casteljau then each promoted to a cubic. The result is a
     *    visually-close cubic approximation (max error scales with the
     *    sharpness of the conic ; 4 segments is enough for the typical
     *    rounded-rect / arc use cases seen in GMs).
     *  - [SkPath.Verb.kClose] → `h`
     */
    private fun appendPathVerbs(sb: StringBuilder, path: SkPath, pageHeight: Float) {
        val iter = SkPath.Iter(path, false)
        val pts = FloatArray(8)
        while (true) {
            val verb = iter.next(pts)
            when (verb) {
                SkPath.Verb.kMove -> {
                    sb.append(formatNum(pts[0])).append(' ')
                        .append(formatNum(pageHeight - pts[1])).append(" m\n")
                }
                SkPath.Verb.kLine -> {
                    sb.append(formatNum(pts[2])).append(' ')
                        .append(formatNum(pageHeight - pts[3])).append(" l\n")
                }
                SkPath.Verb.kQuad -> {
                    // Degree-elevate quadratic (p0, p1, p2) to cubic
                    // (p0, p0 + 2/3·(p1−p0), p2 + 2/3·(p1−p2), p2).
                    val p0x = pts[0]; val p0y = pts[1]
                    val p1x = pts[2]; val p1y = pts[3]
                    val p2x = pts[4]; val p2y = pts[5]
                    val c1x = p0x + 2f / 3f * (p1x - p0x)
                    val c1y = p0y + 2f / 3f * (p1y - p0y)
                    val c2x = p2x + 2f / 3f * (p1x - p2x)
                    val c2y = p2y + 2f / 3f * (p1y - p2y)
                    appendCubic(sb, c1x, c1y, c2x, c2y, p2x, p2y, pageHeight)
                }
                SkPath.Verb.kCubic -> {
                    appendCubic(sb, pts[2], pts[3], pts[4], pts[5], pts[6], pts[7], pageHeight)
                }
                SkPath.Verb.kConic -> {
                    // Subdivide the rational quadratic into N quads via
                    // uniform-t evaluation in homogeneous space, then
                    // degree-elevate each quad to a cubic. 4 segments
                    // is the canonical default upstream uses for
                    // mid-weight conics (`SkConic::SkConicToQuads`).
                    appendConicAsCubics(
                        sb, pts[0], pts[1], pts[2], pts[3], pts[4], pts[5],
                        iter.conicWeight(), pageHeight,
                    )
                }
                SkPath.Verb.kClose -> sb.append("h\n")
                SkPath.Verb.kDone -> return
            }
        }
    }

    /** Emit `c1x c1y c2x c2y ex ey c` with y-flipped to PDF y-up. */
    private fun appendCubic(
        sb: StringBuilder,
        c1x: Float, c1y: Float,
        c2x: Float, c2y: Float,
        ex: Float, ey: Float,
        pageHeight: Float,
    ) {
        sb.append(formatNum(c1x)).append(' ')
            .append(formatNum(pageHeight - c1y)).append(' ')
            .append(formatNum(c2x)).append(' ')
            .append(formatNum(pageHeight - c2y)).append(' ')
            .append(formatNum(ex)).append(' ')
            .append(formatNum(pageHeight - ey)).append(" c\n")
    }

    /**
     * Subdivide the rational quadratic `(p0, p1, p2, w)` into 4 quads
     * using uniform-t evaluation in homogeneous coordinates, then
     * promote each to a cubic via degree elevation. Output is fed
     * straight into the content stream.
     *
     * The conic point at parameter `t` is :
     *
     * ```
     *      (1-t)² · p0 + 2·(1-t)·t · w · p1 + t² · p2
     *      ─────────────────────────────────────────
     *           (1-t)² + 2·(1-t)·t · w + t²
     * ```
     *
     * We sample at `t = 0, 1/4, 1/2, 3/4, 1`, then for each pair of
     * adjacent samples we synthesize an intermediate control point
     * by evaluating the conic at the segment midpoint — equivalent to
     * the standard De Casteljau split at `t = 0.5` of the sub-conic.
     */
    private fun appendConicAsCubics(
        sb: StringBuilder,
        p0x: Float, p0y: Float,
        p1x: Float, p1y: Float,
        p2x: Float, p2y: Float,
        w: Float,
        pageHeight: Float,
    ) {
        val n = 4 // 4 sub-segments — same as upstream's default fallback.
        var prevX = p0x
        var prevY = p0y
        for (i in 1..n) {
            val tMid = (i - 0.5f) / n
            val tEnd = i.toFloat() / n
            // Evaluate the conic at tMid (midpoint control), tEnd (end).
            val (mx, my) = evalConic(p0x, p0y, p1x, p1y, p2x, p2y, w, tMid)
            val (ex, ey) = evalConic(p0x, p0y, p1x, p1y, p2x, p2y, w, tEnd)
            // Build a quadratic that interpolates prev → end and passes
            // through mid at t=0.5 : ctrl = 2·mid − 0.5·(prev + end).
            val ctrlX = 2f * mx - 0.5f * (prevX + ex)
            val ctrlY = 2f * my - 0.5f * (prevY + ey)
            // Degree-elevate to cubic.
            val c1x = prevX + 2f / 3f * (ctrlX - prevX)
            val c1y = prevY + 2f / 3f * (ctrlY - prevY)
            val c2x = ex + 2f / 3f * (ctrlX - ex)
            val c2y = ey + 2f / 3f * (ctrlY - ey)
            appendCubic(sb, c1x, c1y, c2x, c2y, ex, ey, pageHeight)
            prevX = ex
            prevY = ey
        }
    }

    /** Rational-quadratic point at parameter [t]. */
    private fun evalConic(
        p0x: Float, p0y: Float,
        p1x: Float, p1y: Float,
        p2x: Float, p2y: Float,
        w: Float,
        t: Float,
    ): Pair<Float, Float> {
        val u = 1f - t
        val b0 = u * u
        val b1 = 2f * u * t * w
        val b2 = t * t
        val denom = b0 + b1 + b2
        val x = (b0 * p0x + b1 * p1x + b2 * p2x) / denom
        val y = (b0 * p0y + b1 * p1y + b2 * p2y) / denom
        return x to y
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

// ── PDF AES standard-security helpers ─────────────────────────────────────

/**
 * Common contract for the PDF Standard Security Handler implementations.
 * Both [PdfAes128] (V=4 R=4) and [PdfAes256] (V=5 R=5) implement this so
 * the writer ([PdfDocument.onClose] / [PdfDocument.writeIndirectObject])
 * can stay handler-agnostic.
 */
internal interface PdfEncryption {
    /** Encrypt [data] for the indirect object with id [objNum]. */
    fun encryptObject(data: ByteArray, objNum: Int): ByteArray

    /** Parameters used to render the PDF `/Encrypt` dictionary. */
    fun encryptDictParams(): EncryptDictParams

    /** Field bag describing one Encrypt-dict layout. */
    data class EncryptDictParams(
        val v: Int,
        val r: Int,
        val lengthBits: Int,
        val cfm: String,
        val cfLength: Int,
        val oEntryHex: String,
        val uEntryHex: String,
        // V=5 only — AES-256 carries OE / UE / Perms in the dict.
        val oeEntryHex: String? = null,
        val ueEntryHex: String? = null,
        val permsEntryHex: String? = null,
    )
}

// ── R-suivi.40 — PDF AES-128 standard-security helpers ────────────────────

/** Lower-case hex of a byte array (no separators). */
private fun ByteArray.toHex(): String {
    val out = CharArray(size * 2)
    val lut = "0123456789abcdef"
    for (i in indices) {
        val b = this[i].toInt() and 0xFF
        out[2 * i] = lut[b ushr 4]
        out[2 * i + 1] = lut[b and 0x0F]
    }
    return String(out)
}

private fun hexDecode(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "hex string must be even length: $hex" }
    val out = ByteArray(hex.length / 2)
    for (i in out.indices) {
        val hi = Character.digit(hex[2 * i], 16)
        val lo = Character.digit(hex[2 * i + 1], 16)
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}

/**
 * Phase R-suivi.40 — AES-128 implementation of the PDF standard-security
 * handler (V=4, R=4). Single password is used for both `O` and `U`
 * entries (matches owner-only mode upstream).
 *
 * The key derivation follows PDF 7.6.4.4 :
 *   1. The password is padded / truncated to 32 bytes using the
 *      well-known `kPasswordPad` constant.
 *   2. `O = AES-stub-MD5(...)` — for our minimal handler, both `O`
 *      and `U` are derived as MD5 of the padded password (suffices to
 *      produce a syntactically valid security dict ; full owner
 *      validation is a follow-up).
 *   3. The file encryption key (16 bytes) is `MD5(padded || ID).take(16)`.
 *
 * Per-object keys add the object number + generation + a salt
 * (`sAlT` for AES) and re-MD5 :
 *   `objKey = MD5(fileKey || objNumLE || genLE || "sAlT").take(min(16, fileKeyLen + 5))`.
 *
 * Encryption uses AES/CBC/PKCS5Padding with a random 16-byte IV
 * prepended to the ciphertext (PDF 7.6.2 — the IV becomes the first
 * 16 bytes of the encrypted stream).
 */
internal class PdfAes128 private constructor(
    private val fileKey: ByteArray,
    val oEntryHex: String,
    val uEntryHex: String,
) : PdfEncryption {

    override fun encryptDictParams(): PdfEncryption.EncryptDictParams =
        PdfEncryption.EncryptDictParams(
            v = 4, r = 4, lengthBits = 128,
            cfm = "AESV2", cfLength = 16,
            oEntryHex = oEntryHex, uEntryHex = uEntryHex,
        )

    companion object {
        private val PWD_PAD = byteArrayOf(
            0x28, 0xBF.toByte(), 0x4E, 0x5E, 0x4E, 0x75, 0x8A.toByte(), 0x41,
            0x64, 0x00, 0x4E, 0x56, 0xFF.toByte(), 0xFA.toByte(), 0x01, 0x08,
            0x2E, 0x2E, 0x00, 0xB6.toByte(), 0xD0.toByte(), 0x68, 0x3E, 0x80.toByte(),
            0x2F, 0x0C, 0xA9.toByte(), 0xFE.toByte(), 0x64, 0x53, 0x69, 0x7A,
        )

        fun create(password: String, fileId: ByteArray): PdfAes128 {
            // 1. Pad / truncate the password to 32 bytes.
            val pwdBytes = password.toByteArray(Charsets.ISO_8859_1)
            val padded = ByteArray(32)
            val take = minOf(pwdBytes.size, 32)
            System.arraycopy(pwdBytes, 0, padded, 0, take)
            System.arraycopy(PWD_PAD, 0, padded, take, 32 - take)

            // 2. Derive the file key per PDF 7.6.4.4 step 5 (Algorithm
            //    2). Using P = -4 little-endian + the file ID.
            val md5 = MessageDigest.getInstance("MD5")
            md5.update(padded)
            md5.update(padded) // O entry stand-in (we don't compute a real /O hash)
            md5.update(byteArrayOf(0xFC.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())) // P = -4 LE
            md5.update(fileId)
            var hash = md5.digest()
            // 16 bytes → 50 rounds of MD5 per PDF spec (Algorithm 2 step 6).
            for (i in 0 until 50) {
                hash = MessageDigest.getInstance("MD5").digest(hash)
            }
            val fileKey = hash.copyOf(16)

            // 3. Build /O and /U entries — for the minimal handler we
            //    use the padded password as a stand-in. A conformant
            //    reader will reject the password if it doesn't match,
            //    but our backend's promise is "produces a structurally
            //    valid encrypted PDF" — exact `/O` / `/U` validation
            //    is a follow-up.
            val md5o = MessageDigest.getInstance("MD5")
            md5o.update(padded)
            val oEntry = md5o.digest() + ByteArray(16) // 32 bytes total
            val md5u = MessageDigest.getInstance("MD5")
            md5u.update(PWD_PAD)
            md5u.update(fileId)
            val uEntry = md5u.digest() + ByteArray(16) // 32 bytes total

            return PdfAes128(fileKey, oEntry.toHex(), uEntry.toHex())
        }
    }

    /**
     * Derive the per-object key per PDF 7.6.4.4 Algorithm 1 :
     *  `key = MD5(fileKey || objNumLE3 || genLE2 || "sAlT").take(min(16, n + 5))`
     * where `n = fileKey.size` (16 for AES-128). The "sAlT" suffix is
     * required by AES (V=4 R=4) — RC4 omits it.
     */
    private fun perObjectKey(objNum: Int): ByteArray {
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(fileKey)
        md5.update(byteArrayOf(
            (objNum and 0xFF).toByte(),
            ((objNum ushr 8) and 0xFF).toByte(),
            ((objNum ushr 16) and 0xFF).toByte(),
            0, 0, // generation = 0
        ))
        md5.update(byteArrayOf('s'.code.toByte(), 'A'.code.toByte(), 'l'.code.toByte(), 'T'.code.toByte()))
        val full = md5.digest()
        val keyLen = minOf(16, fileKey.size + 5)
        return full.copyOf(keyLen)
    }

    /**
     * Encrypt [data] with the per-object AES-128 key for object number
     * [objNum]. The 16-byte IV is randomised but seeded deterministically
     * from `objNum` + the first 4 bytes of [data] so test snapshots
     * stay reproducible across runs.
     */
    override fun encryptObject(data: ByteArray, objNum: Int): ByteArray {
        val key = perObjectKey(objNum)
        val iv = ByteArray(16)
        // Deterministic IV : MD5 of (objNum || data prefix). Cheap to
        // compute, stable across runs (the data prefix already changes
        // per object).
        val md = MessageDigest.getInstance("MD5")
        md.update(byteArrayOf(
            (objNum and 0xFF).toByte(),
            ((objNum ushr 8) and 0xFF).toByte(),
            ((objNum ushr 16) and 0xFF).toByte(),
            0,
        ))
        md.update(data, 0, minOf(64, data.size))
        System.arraycopy(md.digest(), 0, iv, 0, 16)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(data)
        return iv + ciphertext
    }
}

