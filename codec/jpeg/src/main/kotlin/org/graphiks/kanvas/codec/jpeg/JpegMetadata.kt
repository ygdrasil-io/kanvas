package org.graphiks.kanvas.codec.jpeg

import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkEncodedOrigin
import org.skia.foundation.skcms.SkcmsICCProfile
import org.skia.foundation.skcms.skcmsParse

/** Controls how a future JPEG rewriter handles known metadata segments. */
public enum class JpegMetadataPolicy {
    Preserve,
    StripAll,
    ReplaceKnown,
}

internal data class JpegJfifMetadata(
    val majorVersion: Int,
    val minorVersion: Int,
    val densityUnit: Int,
    val xDensity: Int,
    val yDensity: Int,
)

internal data class JpegMetadata(
    val iccProfile: SkcmsICCProfile?,
    val origin: SkEncodedOrigin,
    val jfif: JpegJfifMetadata?,
    val adobeTransform: Int?,
    val xmp: ByteArray?,
)

/**
 * Extracts semantic metadata from immutable JPEG segment ranges.
 *
 * The source remains owned by [JpegDocument]. This reader only copies bytes
 * where an owned metadata value is required (XMP or ICC reassembly).
 */
internal class JpegMetadataReader(
    private val source: ByteArray,
    private val segments: List<JpegSegment>,
) {
    private val diagnostics = ArrayList<JpegDiagnostic>()
    private val iccChunks = ArrayList<IccChunk>()
    private var iccCount: Int? = null
    private var iccOffset: Long? = null

    internal fun read(): Pair<JpegMetadata, List<JpegDiagnostic>> {
        var origin = SkEncodedOrigin.kTopLeft
        var jfif: JpegJfifMetadata? = null
        var adobeTransform: Int? = null
        var xmp: ByteArray? = null

        for (segment in segments) {
            val payload = payloadBounds(segment) ?: continue
            when (segment.marker) {
                MARKER_APP0 -> when (val result = parseJfif(payload)) {
                    is JfifResult.Parsed -> if (jfif == null) jfif = result.value
                    JfifResult.Invalid -> diagnostic("jpeg.metadata.jfif.invalid", segment)
                    JfifResult.NotJfif -> Unit
                }

                MARKER_APP1 -> when {
                    matchesAt(payload.start, EXIF_SIGNATURE) -> {
                        when (val result = parseExifOrigin(payload)) {
                            is ExifResult.Parsed -> origin = result.origin
                            ExifResult.Invalid -> diagnostic("jpeg.metadata.exif.invalid", segment)
                            ExifResult.MissingOrientation -> Unit
                        }
                    }

                    matchesAt(payload.start, XMP_SIGNATURE) -> {
                        if (xmp == null) {
                            xmp = source.copyOfRange(payload.start + XMP_SIGNATURE.size, payload.endExclusive)
                        }
                    }
                }

                MARKER_APP2 -> if (matchesAt(payload.start, ICC_SIGNATURE)) {
                    parseIccChunk(payload)?.let { appendIccChunk(it, segment) }
                        ?: diagnostic("jpeg.metadata.icc.invalid", segment)
                }

                MARKER_APP14 -> if (matchesAt(payload.start, ADOBE_SIGNATURE)) {
                    parseAdobeTransform(payload)?.let { adobeTransform = it }
                        ?: diagnostic("jpeg.metadata.adobe.invalid", segment)
                }
            }
        }

        val iccProfile = readIccProfile()
        return JpegMetadata(iccProfile, origin, jfif, adobeTransform, xmp) to diagnostics.toList()
    }

    private fun parseJfif(payload: PayloadBounds): JfifResult {
        if (!matchesAt(payload.start, JFIF_SIGNATURE)) return JfifResult.NotJfif
        if (payload.size < JFIF_FIXED_SIZE) return JfifResult.Invalid
        val densityUnit = byteAt(payload.start + JFIF_DENSITY_UNIT_OFFSET)
        val thumbnailWidth = byteAt(payload.start + JFIF_THUMBNAIL_WIDTH_OFFSET)
        val thumbnailHeight = byteAt(payload.start + JFIF_THUMBNAIL_HEIGHT_OFFSET)
        val thumbnailBytes = thumbnailWidth.toLong() * thumbnailHeight.toLong() * 3L
        if (densityUnit !in 0..2 || thumbnailBytes != payload.size.toLong() - JFIF_FIXED_SIZE) {
            return JfifResult.Invalid
        }
        return JfifResult.Parsed(
            JpegJfifMetadata(
                majorVersion = byteAt(payload.start + JFIF_MAJOR_VERSION_OFFSET),
                minorVersion = byteAt(payload.start + JFIF_MINOR_VERSION_OFFSET),
                densityUnit = densityUnit,
                xDensity = readU16BE(payload.start + JFIF_X_DENSITY_OFFSET),
                yDensity = readU16BE(payload.start + JFIF_Y_DENSITY_OFFSET),
            ),
        )
    }

    private fun parseExifOrigin(payload: PayloadBounds): ExifResult {
        val tiffStart = payload.start + EXIF_SIGNATURE.size
        if (tiffStart + TIFF_HEADER_SIZE > payload.endExclusive) return ExifResult.Invalid
        val littleEndian = when {
            byteAt(tiffStart) == 'I'.code && byteAt(tiffStart + 1) == 'I'.code -> true
            byteAt(tiffStart) == 'M'.code && byteAt(tiffStart + 1) == 'M'.code -> false
            else -> return ExifResult.Invalid
        }
        if (readU16(tiffStart + 2, littleEndian) != TIFF_MAGIC) return ExifResult.Invalid
        val ifdStart = tiffStart.toLong() + readU32(tiffStart + 4, littleEndian)
        if (ifdStart !in tiffStart.toLong()..(payload.endExclusive - 2).toLong()) return ExifResult.Invalid
        val directoryStart = ifdStart.toInt()
        val count = readU16(directoryStart, littleEndian)
        val entriesStart = directoryStart + TIFF_IFD_COUNT_SIZE
        val entriesEnd = entriesStart.toLong() + count.toLong() * TIFF_ENTRY_SIZE
        if (entriesEnd + TIFF_NEXT_IFD_OFFSET_SIZE > payload.endExclusive) return ExifResult.Invalid
        for (index in 0 until count) {
            val entry = entriesStart + index * TIFF_ENTRY_SIZE
            if (readU16(entry, littleEndian) != EXIF_ORIENTATION_TAG) continue
            if (readU16(entry + 2, littleEndian) != TIFF_TYPE_SHORT || readU32(entry + 4, littleEndian) != 1L) {
                return ExifResult.Invalid
            }
            val value = readU16(entry + 8, littleEndian)
            if (value !in 1..8) return ExifResult.Invalid
            return ExifResult.Parsed(SkEncodedOrigin.fromExifValue(value))
        }
        return ExifResult.MissingOrientation
    }

    private fun parseIccChunk(payload: PayloadBounds): IccChunk? {
        if (payload.size < ICC_SIGNATURE.size + 2) return null
        val index = byteAt(payload.start + ICC_SIGNATURE.size)
        val count = byteAt(payload.start + ICC_SIGNATURE.size + 1)
        if (index == 0 || count == 0 || index > count) return null
        return IccChunk(index, count, payload.start + ICC_SIGNATURE.size + 2, payload.endExclusive)
    }

    private fun appendIccChunk(chunk: IccChunk, segment: JpegSegment) {
        val expectedCount = iccCount
        if (expectedCount == null) {
            iccCount = chunk.count
            iccOffset = segment.offset
        } else if (chunk.count != expectedCount) {
            diagnostic("jpeg.metadata.icc.inconsistent", segment)
            return
        }
        if (iccChunks.any { it.index == chunk.index }) {
            diagnostic("jpeg.metadata.icc.duplicate", segment)
            return
        }
        iccChunks += chunk
    }

    private fun readIccProfile(): SkcmsICCProfile? {
        val expectedCount = iccCount ?: return null
        val offset = requireNotNull(iccOffset)
        if (iccChunks.size != expectedCount || iccChunks.map(IccChunk::index).toSet().size != expectedCount) {
            diagnostic("jpeg.metadata.icc.incomplete", offset)
            return null
        }
        val ordered = iccChunks.sortedBy(IccChunk::index)
        if (ordered.indices.any { ordered[it].index != it + 1 }) {
            diagnostic("jpeg.metadata.icc.incomplete", offset)
            return null
        }
        val totalSize = ordered.sumOf(IccChunk::size)
        val bytes = ByteArray(totalSize)
        var destination = 0
        for (chunk in ordered) {
            source.copyInto(bytes, destination, chunk.start, chunk.endExclusive)
            destination += chunk.size
        }
        val profile = try {
            skcmsParse(bytes)
        } catch (_: Throwable) {
            null
        }
        if (profile == null) diagnostic("jpeg.metadata.icc.profile.invalid", offset)
        return profile
    }

    private fun parseAdobeTransform(payload: PayloadBounds): Int? {
        if (payload.size != ADOBE_PAYLOAD_SIZE) return null
        return byteAt(payload.start + ADOBE_TRANSFORM_OFFSET).takeIf { it in 0..2 }
    }

    private fun payloadBounds(segment: JpegSegment): PayloadBounds? {
        if (segment.range.isEmpty()) return null
        val start = segment.range.first
        val endExclusive = segment.range.last.toLong() + 1L
        if (start < 0 || endExclusive !in start.toLong()..source.size.toLong()) return null
        return PayloadBounds(start, endExclusive.toInt())
    }

    private fun matchesAt(start: Int, signature: ByteArray): Boolean {
        if (start < 0 || start + signature.size > source.size) return false
        for (index in signature.indices) {
            if (source[start + index] != signature[index]) return false
        }
        return true
    }

    private fun byteAt(offset: Int): Int = source[offset].toInt() and 0xFF

    private fun readU16BE(offset: Int): Int = (byteAt(offset) shl 8) or byteAt(offset + 1)

    private fun readU16(offset: Int, littleEndian: Boolean): Int {
        val first = byteAt(offset)
        val second = byteAt(offset + 1)
        return if (littleEndian) (second shl 8) or first else (first shl 8) or second
    }

    private fun readU32(offset: Int, littleEndian: Boolean): Long {
        val b0 = byteAt(offset).toLong()
        val b1 = byteAt(offset + 1).toLong()
        val b2 = byteAt(offset + 2).toLong()
        val b3 = byteAt(offset + 3).toLong()
        return if (littleEndian) {
            (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
        } else {
            (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        }
    }

    private fun diagnostic(code: String, segment: JpegSegment) = diagnostic(code, segment.offset)

    private fun diagnostic(code: String, offset: Long) {
        diagnostics += JpegDiagnostic(code, offset, Codec.Result.kErrorInInput)
    }

    private data class PayloadBounds(val start: Int, val endExclusive: Int) {
        val size: Int get() = endExclusive - start
    }

    private data class IccChunk(val index: Int, val count: Int, val start: Int, val endExclusive: Int) {
        val size: Int get() = endExclusive - start
    }

    private sealed interface JfifResult {
        data class Parsed(val value: JpegJfifMetadata) : JfifResult
        data object Invalid : JfifResult
        data object NotJfif : JfifResult
    }

    private sealed interface ExifResult {
        data class Parsed(val origin: SkEncodedOrigin) : ExifResult
        data object Invalid : ExifResult
        data object MissingOrientation : ExifResult
    }
}

internal fun parseJpegMetadata(
    source: ByteArray,
    segments: List<JpegSegment>,
): Pair<JpegMetadata, List<JpegDiagnostic>> = JpegMetadataReader(source, segments).read()

private const val MARKER_APP0 = 0xE0
private const val MARKER_APP1 = 0xE1
private const val MARKER_APP2 = 0xE2
private const val MARKER_APP14 = 0xEE

private const val JFIF_FIXED_SIZE = 14
private const val JFIF_MAJOR_VERSION_OFFSET = 5
private const val JFIF_MINOR_VERSION_OFFSET = 6
private const val JFIF_DENSITY_UNIT_OFFSET = 7
private const val JFIF_X_DENSITY_OFFSET = 8
private const val JFIF_Y_DENSITY_OFFSET = 10
private const val JFIF_THUMBNAIL_WIDTH_OFFSET = 12
private const val JFIF_THUMBNAIL_HEIGHT_OFFSET = 13

private const val TIFF_HEADER_SIZE = 8
private const val TIFF_MAGIC = 0x002A
private const val TIFF_IFD_COUNT_SIZE = 2
private const val TIFF_ENTRY_SIZE = 12
private const val TIFF_NEXT_IFD_OFFSET_SIZE = 4
private const val EXIF_ORIENTATION_TAG = 0x0112
private const val TIFF_TYPE_SHORT = 3

private const val ADOBE_PAYLOAD_SIZE = 12
private const val ADOBE_TRANSFORM_OFFSET = 11

private val ICC_SIGNATURE = byteArrayOf(
    0x49, 0x43, 0x43, 0x5F, 0x50, 0x52, 0x4F, 0x46, 0x49, 0x4C, 0x45, 0x00,
)
private val EXIF_SIGNATURE = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00)
private val XMP_SIGNATURE = "http://ns.adobe.com/xap/1.0/\u0000".encodeToByteArray()
private val JFIF_SIGNATURE = byteArrayOf(0x4A, 0x46, 0x49, 0x46, 0x00)
private val ADOBE_SIGNATURE = byteArrayOf(0x41, 0x64, 0x6F, 0x62, 0x65)
