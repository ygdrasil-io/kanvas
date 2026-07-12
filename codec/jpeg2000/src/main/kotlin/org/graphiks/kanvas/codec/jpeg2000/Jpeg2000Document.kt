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
) {
    init {
        require(maxEncodedBytes >= 2)
        require(maxWidth > 0)
        require(maxHeight > 0)
        require(maxPixels > 0)
        require(maxBoxes > 0)
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
 * The parser deliberately accepts only the declared narrow Part 1 profile:
 * raw J2K with `SIZ` directly after `SOC`, or a strictly ordered JP2 core
 * (`jP  `, `ftyp`, `jp2h`/`ihdr`, then `jp2c`), one unsigned 8-bit grayscale
 * component, one tile, one resolution, one layer, reversible 5/3 transform
 * and no quantization. Raw codestreams in that profile may additionally use
 * the bounded one-packet Tier-2/MQ/EBCOT path; JP2 remains structural only.
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

    /** Decodes only the proven raw one-packet profile; containers stay explicit refusals. */
    public fun decode(): Jpeg2000DecodeResult {
        if (container != Jpeg2000Container.J2K || entropy == null) {
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

    public companion object {
        /** Opens a bounded raw J2K codestream or JP2 wrapper. */
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
    val entropy: J2kEntropyInput,
)

private class Jpeg2000Failure(
    val diagnostic: Jpeg2000Diagnostic,
) : RuntimeException(diagnostic.code)

private fun j2kFailure(
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
        imageHeader.width != frame.width || imageHeader.height != frame.height ||
        imageHeader.components != frame.components || imageHeader.precision != frame.precision
    ) {
        j2kFailure("jpeg2000.jp2.ihdr.mismatch", header.offset.toInt())
    }
    return ParsedJpeg2000(Jpeg2000Container.JP2, frame, null, boxes)
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

private fun parseJp2Header(data: ByteArray, header: Jpeg2000Box): Jpeg2000FrameInfo {
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
    if (width <= 0 || height <= 0 || components <= 0) j2kFailure("jpeg2000.jp2.ihdr.invalid", ihdr.offset.toInt())
    if (components != 1 || bitsPerComponent != 7 || compression != 7 || unknownColorSpace != 0 || intellectualProperty != 0) {
        j2kFailure("jpeg2000.jp2.profile.unsupported", ihdr.offset.toInt(), Codec.Result.kUnimplemented)
    }
    return Jpeg2000FrameInfo(width, height, components, bitsPerComponent + 1)
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
    private var frame: Jpeg2000FrameInfo? = null
    private var sawCod: Boolean = false
    private var sawQcd: Boolean = false

    fun parse(): ParsedRawJ2k {
        if (end - start < 2 || data[start].u8() != 0xFF || data[start + 1].u8() != SOC) {
            j2kFailure("jpeg2000.soc.missing", start)
        }
        position += 2
        val sizOffset = position
        if (readMarker() != SIZ) j2kFailure("jpeg2000.siz.order", sizOffset)
        parseSiz(sizOffset)
        while (position < end) {
            val markerOffset = position
            when (readMarker()) {
                SIZ -> parseSiz(markerOffset)
                COD -> parseCod(markerOffset)
                QCD -> parseQcd(markerOffset)
                COM -> skipSegment(markerOffset)
                SOT -> return parseTilePart(markerOffset)
                else -> j2kFailure("jpeg2000.marker.unsupported", markerOffset, Codec.Result.kUnimplemented)
            }
        }
        j2kFailure("jpeg2000.sot.missing", end)
    }

    private fun parseSiz(markerOffset: Int) {
        if (frame != null) j2kFailure("jpeg2000.siz.duplicate", markerOffset)
        val segment = readSegment(markerOffset)
        if (segment.payloadSize < 39) j2kFailure("jpeg2000.siz.invalid", markerOffset)
        val p = segment.payloadOffset
        val components = data.u16(p + 34)
        if (components <= 0 || segment.payloadSize != 36 + components * 3) j2kFailure("jpeg2000.siz.invalid", markerOffset)
        val rsiz = data.u16(p)
        val xSize = data.u32(p + 2)
        val ySize = data.u32(p + 6)
        val xOrigin = data.u32(p + 10)
        val yOrigin = data.u32(p + 14)
        val xTile = data.u32(p + 18)
        val yTile = data.u32(p + 22)
        val xTileOrigin = data.u32(p + 26)
        val yTileOrigin = data.u32(p + 30)
        val width = xSize - xOrigin
        val height = ySize - yOrigin
        if (width !in 1..limits.maxWidth.toLong() || height !in 1..limits.maxHeight.toLong()) {
            j2kFailure("jpeg2000.limit.pixels", markerOffset, Codec.Result.kOutOfMemory)
        }
        if (width * height > limits.maxPixels) j2kFailure("jpeg2000.limit.pixels", markerOffset, Codec.Result.kOutOfMemory)
        val component = p + 36
        val ssiz = data[component].u8()
        val xSampling = data[component + 1].u8()
        val ySampling = data[component + 2].u8()
        if (
            rsiz != 0 || components != 1 || ssiz != 7 || xSampling != 1 || ySampling != 1 ||
            xOrigin != 0L || yOrigin != 0L || xTileOrigin != 0L || yTileOrigin != 0L ||
            xTile != width || yTile != height
        ) {
            j2kFailure("jpeg2000.frame.unsupported", markerOffset, Codec.Result.kUnimplemented)
        }
        frame = Jpeg2000FrameInfo(width.toInt(), height.toInt(), components, 8)
    }

    private fun parseCod(markerOffset: Int) {
        if (sawCod) j2kFailure("jpeg2000.cod.duplicate", markerOffset)
        val segment = readSegment(markerOffset)
        if (segment.payloadSize != 10) j2kFailure("jpeg2000.cod.invalid", markerOffset)
        val p = segment.payloadOffset
        val scod = data[p].u8()
        val progression = data[p + 1].u8()
        val layers = data.u16(p + 2)
        val multiComponentTransform = data[p + 4].u8()
        val decompositions = data[p + 5].u8()
        val codeBlockWidth = data[p + 6].u8()
        val codeBlockHeight = data[p + 7].u8()
        val codeBlockStyle = data[p + 8].u8()
        val transform = data[p + 9].u8()
        if (
            scod != 0 || progression != 0 || layers != 1 || multiComponentTransform != 0 ||
            decompositions != 0 || codeBlockWidth != 4 || codeBlockHeight != 4 ||
            codeBlockStyle != 0 || transform != 1
        ) {
            j2kFailure("jpeg2000.cod.profile.unsupported", markerOffset, Codec.Result.kUnimplemented)
        }
        sawCod = true
    }

    private fun parseQcd(markerOffset: Int) {
        if (sawQcd) j2kFailure("jpeg2000.qcd.duplicate", markerOffset)
        val segment = readSegment(markerOffset)
        // A reversible 8-bit Part 1 codestream has no quantization
        // (Sqcd style 0), two guard bits and the single LL exponent 8:
        // Sqcd=0x40, SPqcd=0x40.  This is the form emitted by OpenJPEG for
        // the declared one-resolution 5/3 profile; 00 00 is not a valid
        // substitute for it.
        if (
            segment.payloadSize != 2 ||
            data[segment.payloadOffset].u8() != 0x40 ||
            data[segment.payloadOffset + 1].u8() != 0x40
        ) {
            j2kFailure("jpeg2000.qcd.profile.unsupported", markerOffset, Codec.Result.kUnimplemented)
        }
        sawQcd = true
    }

    private fun parseTilePart(markerOffset: Int): ParsedRawJ2k {
        val currentFrame = frame ?: j2kFailure("jpeg2000.siz.missing", markerOffset)
        if (!sawCod) j2kFailure("jpeg2000.cod.missing", markerOffset)
        if (!sawQcd) j2kFailure("jpeg2000.qcd.missing", markerOffset)
        val segment = readSegment(markerOffset)
        if (segment.payloadSize != 8) j2kFailure("jpeg2000.sot.invalid", markerOffset)
        val p = segment.payloadOffset
        val tileIndex = data.u16(p)
        val tilePartLength = data.u32(p + 2)
        val tilePartIndex = data[p + 6].u8()
        val tilePartCount = data[p + 7].u8()
        if (tileIndex != 0 || tilePartIndex != 0 || tilePartCount != 1 || tilePartLength < 14L) {
            j2kFailure("jpeg2000.sot.profile.unsupported", markerOffset, Codec.Result.kUnimplemented)
        }
        val tilePartEnd = markerOffset.toLong() + tilePartLength
        if (tilePartEnd > end || tilePartEnd < position + 2L) j2kFailure("jpeg2000.sot.invalid", markerOffset)
        if (readMarker() != SOD) j2kFailure("jpeg2000.sod.missing", position - 2)
        val packetOffset = position
        position = tilePartEnd.toInt()
        if (readMarker() != EOC) j2kFailure("jpeg2000.eoc.missing", position - 2)
        if (position != end) j2kFailure("jpeg2000.eoc.trailing", position)
        return ParsedRawJ2k(
            frame = currentFrame,
            entropy = J2kEntropyInput(packetOffset, tilePartEnd.toInt() - packetOffset),
        )
    }

    private fun skipSegment(markerOffset: Int) {
        readSegment(markerOffset)
    }

    private fun readMarker(): Int {
        if (position + 2 > end || data[position].u8() != 0xFF) j2kFailure("jpeg2000.marker.truncated", position)
        return data[position + 1].u8().also { position += 2 }
    }

    private fun readSegment(markerOffset: Int): SegmentBounds {
        if (position + 2 > end) j2kFailure("jpeg2000.marker.length.truncated", markerOffset)
        val length = data.u16(position)
        if (length < 2 || position + length > end) j2kFailure("jpeg2000.marker.length.invalid", markerOffset)
        val result = SegmentBounds(position + 2, length - 2)
        position += length
        return result
    }
}

private data class SegmentBounds(val payloadOffset: Int, val payloadSize: Int)

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
private const val SIZ: Int = 0x51
private const val COD: Int = 0x52
private const val QCD: Int = 0x5C
private const val COM: Int = 0x64
private const val SOT: Int = 0x90
private const val SOD: Int = 0x93
private const val EOC: Int = 0xD9
private const val MAX_JP2_HEADER_BOXES: Int = 128
private val JP2_SIGNATURE_PAYLOAD: ByteArray = byteArrayOf(0x0D, 0x0A, 0x87.toByte(), 0x0A)
private val JP2_SIGNATURE_BOX: ByteArray = byteArrayOf(
    0, 0, 0, 12,
    'j'.code.toByte(), 'P'.code.toByte(), ' '.code.toByte(), ' '.code.toByte(),
    0x0D, 0x0A, 0x87.toByte(), 0x0A,
)
