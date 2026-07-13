package org.graphiks.kanvas.codec.jpeg2000

import java.util.Collections
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap

/** Resource ceilings applied before a JPEG 2000 codestream is materialized. */
public data class Jpeg2000Limits(
    val maxEncodedBytes: Long = 64L * 1024L * 1024L,
    val maxWidth: Int = 32_768,
    val maxHeight: Int = 32_768,
    val maxPixels: Long = 64L * 1024L * 1024L,
    val maxBoxes: Int = 4_096,
    val maxComponents: Int = 16,
    val maxTiles: Int = 16_384,
    val maxTileParts: Int = 65_536,
    val maxCodeblocks: Long = 16L * 1024L * 1024L,
) {
    init {
        require(maxEncodedBytes >= 2)
        require(maxWidth > 0)
        require(maxHeight > 0)
        require(maxPixels > 0)
        require(maxBoxes > 0)
        require(maxComponents > 0)
        require(maxTiles > 0)
        require(maxTileParts > 0)
        require(maxCodeblocks > 0)
    }
}

/** Stable structural or feature diagnostic from the JPEG 2000 owner. */
public data class Jpeg2000Diagnostic(
    val code: String,
    val offset: Long,
    val result: Codec.Result = Codec.Result.kErrorInInput,
)

/** Result of an open operation. Encoded input never escapes this API as an exception. */
public data class Jpeg2000OpenResult(
    val document: Jpeg2000Document?,
    val diagnostic: Jpeg2000Diagnostic?,
)

/** Result of a decode operation. No fallback decoder is attempted after ownership. */
public data class Jpeg2000DecodeResult(
    val bitmap: SkBitmap?,
    val diagnostic: Jpeg2000Diagnostic?,
)

public enum class Jpeg2000Container {
    J2K,
    JP2,
}

/** The subset of SIZ information safe to expose before entropy decoding. */
public data class Jpeg2000FrameInfo(
    val width: Int,
    val height: Int,
    val components: Int,
    val precision: Int,
)

/** An immutable range record for a JP2 top-level box. */
public class Jpeg2000Box internal constructor(
    public val type: String,
    public val offset: Long,
    internal val payloadOffset: Int,
    internal val payloadSize: Int,
)

/**
 * Immutable, bounded JPEG 2000 document.
 *
 * The parser retains bounded Part 1 structure: raw J2K with `SIZ` directly after
 * `SOC`, or a strictly ordered JP2 core (`jP  `, `ftyp`, `jp2h`/`ihdr`, then
 * `jp2c`), general bounded main-header syntax, and complete `SOT` tile plans.
 * Pixels remain restricted to one unsigned 8-bit grayscale component, one tile,
 * one layer, reversible 5/3 transform and no quantization. Raw codestreams in
 * that profile may use either `Ndecomp=0` with one packet and up to two horizontal
 * codeblocks, the bounded `Ndecomp=1` two-resolution path, or the bounded
 * `Ndecomp=2` three-resolution path. A JP2 wrapper is pixel-decodable only when
 * its embedded codestream and JP2 color declaration satisfy that same proven profile.
 */
public class Jpeg2000Document private constructor(
    private val source: ByteArray,
    public val container: Jpeg2000Container,
    public val frame: Jpeg2000FrameInfo,
    private val entropy: J2kEntropyInput?,
    boxes: List<Jpeg2000Box>,
) {
    public val boxes: List<Jpeg2000Box> = Collections.unmodifiableList(ArrayList(boxes))

    /** Returns a defensive copy of a retained JP2 top-level box payload. */
    public fun copyPayload(box: Jpeg2000Box): ByteArray {
        require(boxes.contains(box)) { "box does not belong to this JPEG 2000 document" }
        return source.copyOfRange(box.payloadOffset, box.payloadOffset + box.payloadSize)
    }

    /** Returns an independent copy of the encoded J2K or JP2 bytes. */
    public fun copyEncodedBytes(): ByteArray = source.copyOf()

    /** Decodes only the proven bounded J2K profile, raw or in a validated JP2 wrapper. */
    public fun decode(): Jpeg2000DecodeResult {
        if (
            (container != Jpeg2000Container.J2K && container != Jpeg2000Container.JP2) ||
            entropy == null
        ) {
            return Jpeg2000DecodeResult(
                bitmap = null,
                diagnostic = Jpeg2000Diagnostic(
                    code = "jpeg2000.container.pixel.unimplemented",
                    offset = 0,
                    result = Codec.Result.kUnimplemented,
                ),
            )
        }
        return decodeNarrowRawJ2k(source, frame, entropy)
    }

    /** Whether this document can safely back the bounded [Codec] image facade. */
    internal fun supportsImageCodec(): Boolean =
        (container == Jpeg2000Container.J2K || container == Jpeg2000Container.JP2) && entropy != null &&
            frame.width in 1..NARROW_RAW_J2K_MAX_WIDTH &&
            frame.height in 1..NARROW_RAW_J2K_MAX_HEIGHT

    public companion object {
        /**
         * Opens a structurally bounded Part 1 raw J2K codestream or JP2 wrapper.
         *
         * Opening retains bounded general `SIZ`/`COD`/`QCD` syntax and `SOT` tile plans.
         * Pixels and the [Codec] facade remain limited to the strict proven profile; general
         * pixel decoding and Tier-2 remain unimplemented.
         */
        public fun open(data: ByteArray, limits: Jpeg2000Limits = Jpeg2000Limits()): Jpeg2000OpenResult {
            if (data.size.toLong() > limits.maxEncodedBytes) {
                return Jpeg2000OpenResult(
                    null,
                    Jpeg2000Diagnostic(
                        "jpeg2000.limit.encoded-bytes",
                        limits.maxEncodedBytes,
                        Codec.Result.kOutOfMemory,
                    ),
                )
            }
            return try {
                val parsed = when {
                    isRawCodestream(data) -> parseRaw(data, limits)
                    isJp2Signature(data) -> parseJp2(data, limits)
                    else -> j2kFailure("jpeg2000.signature.missing", 0)
                }
                Jpeg2000OpenResult(
                    Jpeg2000Document(data.copyOf(), parsed.container, parsed.frame, parsed.entropy, parsed.boxes),
                    null,
                )
            } catch (failure: Jpeg2000Failure) {
                Jpeg2000OpenResult(null, failure.diagnostic)
            }
        }

        /** Header-only ownership predicate for the global codec dispatcher. */
        internal fun looksLikeJpeg2000(data: ByteArray): Boolean =
            isRawCodestream(data) || isJp2Signature(data)
    }
}

private data class ParsedJpeg2000(
    val container: Jpeg2000Container,
    val frame: Jpeg2000FrameInfo,
    val entropy: J2kEntropyInput?,
    val boxes: List<Jpeg2000Box>,
)

private data class ParsedRawJ2k(
    val frame: Jpeg2000FrameInfo,
    val syntax: J2kSyntaxModel,
    val plan: J2kDecodePlan,
    val entropy: J2kEntropyInput?,
)

private data class ParsedJp2Header(
    val frame: Jpeg2000FrameInfo,
    val componentSigned: Boolean,
    val supportsPixelFacade: Boolean,
)

internal class Jpeg2000Failure(
    val diagnostic: Jpeg2000Diagnostic,
) : RuntimeException(diagnostic.code)

internal fun j2kFailure(
    code: String,
    offset: Int,
    result: Codec.Result = Codec.Result.kErrorInInput,
): Nothing = throw Jpeg2000Failure(Jpeg2000Diagnostic(code, offset.toLong(), result))

private fun parseRaw(data: ByteArray, limits: Jpeg2000Limits): ParsedJpeg2000 {
    val raw = J2kCodestreamParser(data, 0, data.size, limits).parse()
    return ParsedJpeg2000(
        container = Jpeg2000Container.J2K,
        frame = raw.frame,
        entropy = raw.entropy,
        boxes = emptyList(),
    )
}

private fun parseJp2(data: ByteArray, limits: Jpeg2000Limits): ParsedJpeg2000 {
    val boxes = Jp2BoxParser(data, 0, data.size, limits.maxBoxes).parseTopLevel()
    val signature = requireSingleBox(
        boxes = boxes,
        type = "jP  ",
        missingCode = "jpeg2000.jp2.signature.missing",
        duplicateCode = "jpeg2000.jp2.signature.duplicate",
        missingOffset = 0,
    )
    if (boxes.firstOrNull() !== signature) j2kFailure("jpeg2000.jp2.signature.order", signature.offset.toInt())
    if (signature.payloadSize != JP2_SIGNATURE_PAYLOAD.size ||
        !data.rangeEquals(signature.payloadOffset, JP2_SIGNATURE_PAYLOAD, 0, JP2_SIGNATURE_PAYLOAD.size)
    ) {
        j2kFailure("jpeg2000.jp2.signature.missing", signature.offset.toInt())
    }
    val ftyp = requireSingleBox(
        boxes = boxes,
        type = "ftyp",
        missingCode = "jpeg2000.jp2.ftyp.missing",
        duplicateCode = "jpeg2000.jp2.ftyp.duplicate",
        missingOffset = data.size,
    )
    if (boxes.getOrNull(1) !== ftyp) j2kFailure("jpeg2000.jp2.ftyp.order", ftyp.offset.toInt())
    validateFtyp(data, ftyp)
    val header = requireSingleBox(
        boxes = boxes,
        type = "jp2h",
        missingCode = "jpeg2000.jp2.jp2h.missing",
        duplicateCode = "jpeg2000.jp2.jp2h.duplicate",
        missingOffset = ftyp.offset.toInt(),
    )
    val jp2c = requireSingleBox(
        boxes = boxes,
        type = "jp2c",
        missingCode = "jpeg2000.jp2.jp2c.missing",
        duplicateCode = "jpeg2000.jp2.jp2c.duplicate",
        missingOffset = header.offset.toInt(),
    )
    if (jp2c.offset < header.offset) j2kFailure("jpeg2000.jp2.jp2c.order", jp2c.offset.toInt())
    if (boxes.getOrNull(2) !== header) j2kFailure("jpeg2000.jp2.jp2h.order", header.offset.toInt())
    if (boxes.getOrNull(3) !== jp2c) j2kFailure("jpeg2000.jp2.jp2c.order", jp2c.offset.toInt())
    val imageHeader = parseJp2Header(data, header)
    val raw = J2kCodestreamParser(
        data,
        jp2c.payloadOffset,
        jp2c.payloadOffset + jp2c.payloadSize,
        limits,
    ).parse()
    val frame = raw.frame
    if (
        imageHeader.frame != frame ||
        raw.syntax.mainHeader.geometry.components.any { component ->
            component.precision != imageHeader.frame.precision || component.signed != imageHeader.componentSigned
        }
    ) {
        j2kFailure("jpeg2000.jp2.ihdr.mismatch", header.offset.toInt())
    }
    return ParsedJpeg2000(
        Jpeg2000Container.JP2,
        frame,
        raw.entropy?.takeIf { imageHeader.supportsPixelFacade },
        boxes,
    )
}

private fun requireSingleBox(
    boxes: List<Jpeg2000Box>,
    type: String,
    missingCode: String,
    duplicateCode: String,
    missingOffset: Int,
): Jpeg2000Box {
    val matches = boxes.filter { it.type == type }
    return when (matches.size) {
        0 -> j2kFailure(missingCode, missingOffset)
        1 -> matches.single()
        else -> j2kFailure(duplicateCode, matches[1].offset.toInt())
    }
}

private fun validateFtyp(data: ByteArray, box: Jpeg2000Box) {
    if (box.payloadSize < 8 || (box.payloadSize - 8) % 4 != 0) {
        j2kFailure("jpeg2000.jp2.ftyp.invalid", box.offset.toInt())
    }
    val majorBrand = data.ascii4(box.payloadOffset)
    val compatible = (box.payloadOffset + 8 until box.payloadOffset + box.payloadSize step 4)
        .map { data.ascii4(it) }
    if (majorBrand != "jp2 " && "jp2 " !in compatible) {
        j2kFailure("jpeg2000.jp2.ftyp.invalid", box.offset.toInt())
    }
}

private fun parseJp2Header(data: ByteArray, header: Jpeg2000Box): ParsedJp2Header {
    val children = Jp2BoxParser(
        data,
        header.payloadOffset,
        header.payloadOffset + header.payloadSize,
        MAX_JP2_HEADER_BOXES,
    ).parseNested()
    val ihdr = requireSingleBox(
        boxes = children,
        type = "ihdr",
        missingCode = "jpeg2000.jp2.ihdr.missing",
        duplicateCode = "jpeg2000.jp2.ihdr.duplicate",
        missingOffset = header.offset.toInt(),
    )
    if (children.firstOrNull() !== ihdr) j2kFailure("jpeg2000.jp2.ihdr.order", ihdr.offset.toInt())
    if (ihdr.payloadSize != 14) j2kFailure("jpeg2000.jp2.ihdr.invalid", ihdr.offset.toInt())
    val height = data.u32(ihdr.payloadOffset).toInt()
    val width = data.u32(ihdr.payloadOffset + 4).toInt()
    val components = data.u16(ihdr.payloadOffset + 8)
    val bitsPerComponent = data[ihdr.payloadOffset + 10].u8()
    val compression = data[ihdr.payloadOffset + 11].u8()
    val unknownColorSpace = data[ihdr.payloadOffset + 12].u8()
    val intellectualProperty = data[ihdr.payloadOffset + 13].u8()
    val precision = (bitsPerComponent and 0x7F) + 1
    if (
        width <= 0 || height <= 0 || components <= 0 || precision !in 1..38 ||
        compression != 7 || unknownColorSpace !in 0..1 || intellectualProperty !in 0..1
    ) j2kFailure("jpeg2000.jp2.ihdr.invalid", ihdr.offset.toInt())
    val colorBoxes = children.filter { it.type == "colr" }
    val colorIsExactGrayscale = colorBoxes.map { color -> isJp2GrayscalePixelColor(data, color) }
    val supportsPixelFacade =
        components == 1 && bitsPerComponent == 7 && unknownColorSpace == 0 && intellectualProperty == 0 &&
            (colorBoxes.isEmpty() || (colorBoxes.size == 1 && colorIsExactGrayscale.single()))
    children.forEach { child ->
        when (child.type) {
            "ihdr" -> Unit
            "colr" -> Unit
            else -> j2kFailure("jpeg2000.jp2.profile.unsupported", child.offset.toInt(), Codec.Result.kUnimplemented)
        }
    }
    return ParsedJp2Header(
        Jpeg2000FrameInfo(width, height, components, precision),
        bitsPerComponent and 0x80 != 0,
        supportsPixelFacade,
    )
}

private fun isJp2GrayscalePixelColor(data: ByteArray, color: Jpeg2000Box): Boolean {
    if (color.payloadSize < 3) j2kFailure("jpeg2000.jp2.colr.invalid", color.offset.toInt())
    return when (data[color.payloadOffset].u8()) {
        1 -> {
            if (color.payloadSize != 7) j2kFailure("jpeg2000.jp2.colr.invalid", color.offset.toInt())
            data[color.payloadOffset + 1].u8() == 0 &&
                data[color.payloadOffset + 2].u8() == 0 &&
                data.u32(color.payloadOffset + 3) == JP2_ENUMERATED_GRAYSCALE
        }
        2, 3 -> {
            if (color.payloadSize == 3) j2kFailure("jpeg2000.jp2.colr.invalid", color.offset.toInt())
            false
        }
        else -> j2kFailure("jpeg2000.jp2.colr.invalid", color.offset.toInt())
    }
}

private class Jp2BoxParser(
    private val data: ByteArray,
    private val start: Int,
    private val end: Int,
    private val maxBoxes: Int,
) {
    private var position: Int = start

    fun parseTopLevel(): List<Jpeg2000Box> = parseBoxes()

    fun parseNested(): List<Jpeg2000Box> = parseBoxes()

    private fun parseBoxes(): List<Jpeg2000Box> {
        val boxes = ArrayList<Jpeg2000Box>()
        while (position < end) {
            if (boxes.size == maxBoxes) j2kFailure("jpeg2000.jp2.limit.boxes", position, Codec.Result.kOutOfMemory)
            if (end - position < 8) j2kFailure("jpeg2000.jp2.box.truncated", position)
            val offset = position
            val length = data.u32(position)
            val type = data.ascii4(position + 4)
            if (length == 1L) j2kFailure("jpeg2000.jp2.box.extended-length.unsupported", offset, Codec.Result.kUnimplemented)
            if (length == 0L) j2kFailure("jpeg2000.jp2.box.to-eof.unsupported", offset, Codec.Result.kUnimplemented)
            if (length < 8L || length > (end - position).toLong()) j2kFailure("jpeg2000.jp2.box.truncated", offset)
            val size = length.toInt()
            boxes += Jpeg2000Box(type, offset.toLong(), offset + 8, size - 8)
            position += size
        }
        return boxes
    }
}

private class J2kCodestreamParser(
    private val data: ByteArray,
    private val start: Int,
    private val end: Int,
    private val limits: Jpeg2000Limits,
) {
    private var position: Int = start

    fun parse(): ParsedRawJ2k {
        val mainHeader = J2kMainHeaderParser(data, start, end, limits).parse()
        position = mainHeader.nextMarkerOffset
        val tileParts = ArrayList<J2kTilePart>()
        while (position < end) {
            val markerOffset = position
            when (readMarker()) {
                SOT -> parseTilePart(markerOffset, mainHeader, tileParts)
                EOC -> {
                    if (position != end) j2kFailure("jpeg2000.eoc.trailing", position)
                    val syntax = J2kSyntaxModel(mainHeader, tileParts)
                    val plan = J2kDecodePlan.create(syntax, limits)
                    return ParsedRawJ2k(
                        frame = mainHeader.geometry.frame,
                        syntax = syntax,
                        plan = plan,
                        entropy = narrowEntropyInputOrNull(mainHeader, tileParts, data),
                    )
                }
                else -> j2kFailure("jpeg2000.eoc.missing", markerOffset)
            }
        }
        j2kFailure("jpeg2000.eoc.missing", end)
    }

    private fun parseTilePart(
        markerOffset: Int,
        mainHeader: J2kMainHeader,
        tileParts: MutableList<J2kTilePart>,
    ) {
        if (tileParts.size >= limits.maxTileParts) {
            j2kFailure("jpeg2000.limit.tile-parts", markerOffset, Codec.Result.kOutOfMemory)
        }
        if (end - position < 2) j2kFailure("jpeg2000.sot.invalid", markerOffset)
        val length = data.u16(position)
        if (length != 10 || length > end - position) j2kFailure("jpeg2000.sot.invalid", markerOffset)
        val p = position + 2
        position += length
        val tileIndex = data.u16(p)
        val tilePartLength = data.u32(p + 2)
        val tilePartIndex = data[p + 6].u8()
        val tilePartCount = data[p + 7].u8()
        if (
            tileIndex.toLong() >= mainHeader.geometry.tileGrid.tileCount ||
            tilePartCount != 0 && tilePartIndex >= tilePartCount ||
            tilePartLength != 0L && tilePartLength < 14L
        ) j2kFailure("jpeg2000.sot.invalid", markerOffset)
        val declaredTilePartEnd = if (tilePartLength == 0L) null else markerOffset.toLong() + tilePartLength
        if (
            declaredTilePartEnd != null &&
            (declaredTilePartEnd > end.toLong() || declaredTilePartEnd < position + 2L)
        ) j2kFailure("jpeg2000.sot.invalid", markerOffset)
        if (readMarker() != SOD) j2kFailure("jpeg2000.sod.missing", position - 2)
        val dataOffset = position
        val tilePartEnd = declaredTilePartEnd?.toInt() ?: openEndedTilePartEnd(dataOffset, markerOffset)
        position = tilePartEnd
        tileParts += J2kTilePart(
            tileIndex = tileIndex,
            partIndex = tilePartIndex,
            partCount = tilePartCount,
            headerOffset = markerOffset,
            dataOffset = dataOffset,
            dataLength = tilePartEnd - dataOffset,
            isOpenEndedLength = declaredTilePartEnd == null,
        )
    }

    private fun openEndedTilePartEnd(dataOffset: Int, markerOffset: Int): Int {
        val finalEocOffset = end - 2
        if (
            finalEocOffset < dataOffset || data[finalEocOffset].u8() != 0xFF ||
            data[finalEocOffset + 1].u8() != EOC ||
            containsNestedSotOrEoc(dataOffset, finalEocOffset)
        ) {
            j2kFailure("jpeg2000.sot.invalid", markerOffset)
        }
        return finalEocOffset
    }

    private fun containsNestedSotOrEoc(start: Int, endExclusive: Int): Boolean {
        var cursor = start
        while (endExclusive - cursor >= 2) {
            if (
                data[cursor].u8() == 0xFF &&
                (data[cursor + 1].u8() == SOT || data[cursor + 1].u8() == EOC)
            ) return true
            cursor++
        }
        return false
    }

    private fun readMarker(): Int {
        if (end - position < 2 || data[position].u8() != 0xFF) j2kFailure("jpeg2000.marker.truncated", position)
        return data[position + 1].u8().also { position += 2 }
    }

}

private fun Byte.u8(): Int = toInt() and 0xFF

private fun ByteArray.u16(offset: Int): Int = (this[offset].u8() shl 8) or this[offset + 1].u8()

private fun ByteArray.u32(offset: Int): Long =
    (this[offset].u8().toLong() shl 24) or
        (this[offset + 1].u8().toLong() shl 16) or
        (this[offset + 2].u8().toLong() shl 8) or
        this[offset + 3].u8().toLong()

private fun ByteArray.ascii4(offset: Int): String = String(copyOfRange(offset, offset + 4), Charsets.ISO_8859_1)

private fun ByteArray.rangeEquals(offset: Int, other: ByteArray, otherOffset: Int, count: Int): Boolean {
    if (offset < 0 || otherOffset < 0 || offset + count > size || otherOffset + count > other.size) return false
    for (index in 0 until count) if (this[offset + index] != other[otherOffset + index]) return false
    return true
}

private fun isRawCodestream(data: ByteArray): Boolean =
    data.size >= 2 && data[0].u8() == 0xFF && data[1].u8() == SOC

private fun isJp2Signature(data: ByteArray): Boolean =
    data.size >= 12 && data.rangeEquals(0, JP2_SIGNATURE_BOX, 0, JP2_SIGNATURE_BOX.size)

private const val SOC: Int = 0x4F
private const val SOT: Int = 0x90
private const val SOD: Int = 0x93
private const val EOC: Int = 0xD9
private const val MAX_JP2_HEADER_BOXES: Int = 128
private const val JP2_ENUMERATED_GRAYSCALE: Long = 17L
internal const val RAW_J2K_CODEBLOCK_WIDTH: Int = 64
internal const val NARROW_RAW_J2K_MAX_WIDTH: Int = 128
internal const val NARROW_RAW_J2K_MAX_HEIGHT: Int = 64
private val JP2_SIGNATURE_PAYLOAD: ByteArray = byteArrayOf(0x0D, 0x0A, 0x87.toByte(), 0x0A)
private val JP2_SIGNATURE_BOX: ByteArray = byteArrayOf(
    0, 0, 0, 12,
    'j'.code.toByte(), 'P'.code.toByte(), ' '.code.toByte(), ' '.code.toByte(),
    0x0D, 0x0A, 0x87.toByte(), 0x0A,
)
