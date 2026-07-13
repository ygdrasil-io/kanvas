package org.graphiks.kanvas.codec.jpeg2000

import org.graphiks.kanvas.codec.Codec

internal class J2kMainHeaderParser(
    private val data: ByteArray,
    private val start: Int,
    private val end: Int,
    private val limits: Jpeg2000Limits,
) {
    private var position: Int = start
    private var geometry: J2kGeometryModel? = null
    private var coding: J2kCodingStyle? = null
    private var quantization: J2kQuantizationStyle? = null

    init {
        if (start < 0 || end < start || end > data.size) {
            j2kFailure("jpeg2000.main-header.bounds.invalid", start.coerceIn(0, data.size))
        }
    }

    fun parse(): J2kMainHeader {
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
                SIZ -> j2kFailure("jpeg2000.siz.duplicate", markerOffset)
                COD -> parseCod(markerOffset)
                QCD -> parseQcd(markerOffset)
                COM -> skipSegment(markerOffset)
                SOT -> return J2kMainHeader(
                    geometry = geometry ?: j2kFailure("jpeg2000.siz.missing", markerOffset),
                    coding = coding ?: j2kFailure("jpeg2000.cod.missing", markerOffset),
                    quantization = quantization ?: j2kFailure("jpeg2000.qcd.missing", markerOffset),
                    nextMarkerOffset = markerOffset,
                )
                else -> j2kFailure("jpeg2000.marker.unsupported", markerOffset, Codec.Result.kUnimplemented)
            }
        }
        j2kFailure("jpeg2000.sot.missing", end)
    }

    private fun parseSiz(markerOffset: Int) {
        if (geometry != null) j2kFailure("jpeg2000.siz.duplicate", markerOffset)
        val segment = readSegment(markerOffset)
        if (segment.payloadSize < 39) j2kFailure("jpeg2000.siz.invalid", markerOffset)
        val p = segment.payloadOffset
        val componentCount = data.u16(p + 34)
        if (componentCount <= 0 || segment.payloadSize != 36 + (componentCount * 3)) {
            j2kFailure("jpeg2000.siz.invalid", markerOffset)
        }
        if (componentCount > limits.maxComponents) {
            j2kFailure("jpeg2000.limit.components", markerOffset, Codec.Result.kOutOfMemory)
        }

        val imageX1 = data.u32(p + 2)
        val imageY1 = data.u32(p + 6)
        val imageX0 = data.u32(p + 10)
        val imageY0 = data.u32(p + 14)
        val tileWidth = data.u32(p + 18)
        val tileHeight = data.u32(p + 22)
        val tileX0 = data.u32(p + 26)
        val tileY0 = data.u32(p + 30)
        if (
            imageX1 <= imageX0 || imageY1 <= imageY0 ||
            tileWidth == 0L || tileHeight == 0L ||
            tileX0 > imageX0 || tileY0 > imageY0 ||
            imageX0 > Int.MAX_VALUE || imageY0 > Int.MAX_VALUE ||
            imageX1 > Int.MAX_VALUE || imageY1 > Int.MAX_VALUE ||
            tileX0 > Int.MAX_VALUE || tileY0 > Int.MAX_VALUE ||
            tileWidth > Int.MAX_VALUE || tileHeight > Int.MAX_VALUE
        ) {
            j2kFailure("jpeg2000.siz.invalid", markerOffset)
        }

        val width = imageX1 - imageX0
        val height = imageY1 - imageY0
        if (width > limits.maxWidth.toLong() || height > limits.maxHeight.toLong()) {
            j2kFailure("jpeg2000.limit.pixels", markerOffset, Codec.Result.kOutOfMemory)
        }
        val pixels = checkedProduct(width, height, "jpeg2000.limit.pixels", markerOffset)
        if (pixels > limits.maxPixels) j2kFailure("jpeg2000.limit.pixels", markerOffset, Codec.Result.kOutOfMemory)

        val components = List(componentCount) { index -> parseComponent(p + 36 + (index * 3)) }
        if (components.any { it.precision !in 1..38 || it.xSampling == 0 || it.ySampling == 0 }) {
            j2kFailure("jpeg2000.siz.invalid", markerOffset)
        }

        val columns = tileAxisCount(imageX0, imageX1, tileX0, tileWidth)
        val rows = tileAxisCount(imageY0, imageY1, tileY0, tileHeight)
        val tileCount = checkedTileCount(columns, rows, limits, markerOffset)
        if (columns > Int.MAX_VALUE || rows > Int.MAX_VALUE || tileCount > Int.MAX_VALUE) {
            j2kFailure("jpeg2000.limit.tiles", markerOffset, Codec.Result.kOutOfMemory)
        }
        geometry = J2kGeometryModel(
            frame = Jpeg2000FrameInfo(
                width = width.toInt(),
                height = height.toInt(),
                components = componentCount,
                precision = components.maxOf { it.precision },
            ),
            components = components,
            tileGrid = J2kTileGrid(
                imageX0 = imageX0.toInt(),
                imageY0 = imageY0.toInt(),
                imageX1 = imageX1.toInt(),
                imageY1 = imageY1.toInt(),
                tileX0 = tileX0.toInt(),
                tileY0 = tileY0.toInt(),
                tileWidth = tileWidth.toInt(),
                tileHeight = tileHeight.toInt(),
                columns = columns.toInt(),
                rows = rows.toInt(),
            ),
        )
    }

    private fun parseCod(markerOffset: Int) {
        if (coding != null) j2kFailure("jpeg2000.cod.duplicate", markerOffset)
        val segment = readSegment(markerOffset)
        if (segment.payloadSize < 10) j2kFailure("jpeg2000.cod.invalid", markerOffset)
        val p = segment.payloadOffset
        val scod = data[p].u8()
        val progression = data[p + 1].u8()
        val layers = data.u16(p + 2)
        val multiComponentTransform = data[p + 4].u8()
        val decompositions = data[p + 5].u8()
        val codeBlockWidth = data[p + 6].u8()
        val codeBlockHeight = data[p + 7].u8()
        val style = data[p + 8].u8()
        val transform = data[p + 9].u8()
        val precinctsPresent = scod and 1 != 0
        val usesSopMarkers = scod and 0x02 != 0
        val usesEphMarkers = scod and 0x04 != 0
        val precinctCount = if (precinctsPresent) decompositions + 1 else 0
        if (
            scod and 0xF8 != 0 || progression !in 0..4 || layers == 0 || decompositions > 32 ||
            multiComponentTransform !in 0..1 || codeBlockWidth !in 0..8 ||
            codeBlockHeight !in 0..8 || codeBlockWidth + codeBlockHeight > 8 ||
            style and 0xC0 != 0 || transform !in 0..1 ||
            segment.payloadSize != 10 + precinctCount
        ) {
            j2kFailure("jpeg2000.cod.invalid", markerOffset)
        }
        val precinctExponents = if (precinctsPresent) {
            List(precinctCount) { index ->
                val precinct = data[p + 10 + index].u8()
                (precinct and 0x0F) to (precinct ushr 4)
            }
        } else {
            List(decompositions + 1) { 15 to 15 }
        }
        coding = J2kCodingStyle(
            progression = J2kProgressionOrder.entries[progression],
            layers = layers,
            multiComponentTransform = multiComponentTransform,
            usesSopMarkers = usesSopMarkers,
            usesEphMarkers = usesEphMarkers,
            decompositions = decompositions,
            codeBlockWidth = codeBlockWidth,
            codeBlockHeight = codeBlockHeight,
            style = style,
            transform = transform,
            precinctExponents = precinctExponents,
        )
    }

    private fun parseQcd(markerOffset: Int) {
        if (quantization != null) j2kFailure("jpeg2000.qcd.duplicate", markerOffset)
        val currentCoding = coding ?: j2kFailure("jpeg2000.cod.missing", markerOffset)
        val segment = readSegment(markerOffset)
        if (segment.payloadSize < 2) j2kFailure("jpeg2000.qcd.invalid", markerOffset)
        val p = segment.payloadOffset
        val sqcd = data[p].u8()
        val style = sqcd and 0x1F
        if (style !in 0..2) j2kFailure("jpeg2000.qcd.invalid", markerOffset)
        val entryCount = if (style == 1) 1 else 1 + (3 * currentCoding.decompositions)
        val entrySize = if (style == 0) 1 else 2
        if (segment.payloadSize != 1 + (entryCount * entrySize)) {
            j2kFailure("jpeg2000.qcd.invalid", markerOffset)
        }
        val exponents = IntArray(entryCount)
        val mantissas = if (style == 0) null else IntArray(entryCount)
        for (index in 0 until entryCount) {
            val entryOffset = p + 1 + (index * entrySize)
            if (style == 0) {
                val entry = data[entryOffset].u8()
                if (entry and 0x07 != 0) j2kFailure("jpeg2000.qcd.invalid", markerOffset)
                exponents[index] = entry ushr 3
            } else {
                val entry = data.u16(entryOffset)
                exponents[index] = entry ushr 11
                mantissas!![index] = entry and 0x07FF
            }
        }
        quantization = J2kQuantizationStyle(
            guardBits = sqcd ushr 5,
            style = style,
            reversible = style == 0,
            exponents = exponents,
            mantissas = mantissas,
        )
    }

    private fun parseComponent(offset: Int): J2kComponentSpec = J2kComponentSpec(
        precision = (data[offset].u8() and 0x7F) + 1,
        signed = data[offset].u8() and 0x80 != 0,
        xSampling = data[offset + 1].u8(),
        ySampling = data[offset + 2].u8(),
    )

    private fun skipSegment(markerOffset: Int) {
        readSegment(markerOffset)
    }

    private fun readMarker(): Int {
        if (position + 2 > end || data[position].u8() != 0xFF) j2kFailure("jpeg2000.marker.truncated", position)
        return data[position + 1].u8().also { position += 2 }
    }

    private fun readSegment(markerOffset: Int): J2kMainHeaderSegmentBounds {
        if (position + 2 > end) j2kFailure("jpeg2000.marker.length.truncated", markerOffset)
        val length = data.u16(position)
        if (length < 2 || length > end - position) j2kFailure("jpeg2000.marker.length.invalid", markerOffset)
        return J2kMainHeaderSegmentBounds(position + 2, length - 2).also { position += length }
    }
}

private data class J2kMainHeaderSegmentBounds(val payloadOffset: Int, val payloadSize: Int)

private fun checkedTileCount(columns: Long, rows: Long, limits: Jpeg2000Limits, markerOffset: Int): Long {
    val count = checkedProduct(columns, rows, "jpeg2000.limit.tiles", markerOffset)
    if (count > limits.maxTiles) j2kFailure("jpeg2000.limit.tiles", markerOffset, Codec.Result.kOutOfMemory)
    return count
}

private fun checkedProduct(left: Long, right: Long, code: String, markerOffset: Int): Long = try {
    Math.multiplyExact(left, right)
} catch (_: ArithmeticException) {
    j2kFailure(code, markerOffset, Codec.Result.kOutOfMemory)
}

private fun tileAxisCount(imageStart: Long, imageEnd: Long, tileStart: Long, tileExtent: Long): Long {
    val imageTilesEnd = ceilDivide(imageEnd - tileStart, tileExtent)
    val imageTilesStart = (imageStart - tileStart) / tileExtent
    return imageTilesEnd - imageTilesStart
}

private fun ceilDivide(value: Long, divisor: Long): Long = ((value - 1) / divisor) + 1

private fun Byte.u8(): Int = toInt() and 0xFF

private fun ByteArray.u16(offset: Int): Int = (this[offset].u8() shl 8) or this[offset + 1].u8()

private fun ByteArray.u32(offset: Int): Long =
    (this[offset].u8().toLong() shl 24) or
        (this[offset + 1].u8().toLong() shl 16) or
        (this[offset + 2].u8().toLong() shl 8) or
        this[offset + 3].u8().toLong()

private const val SOC: Int = 0x4F
private const val SIZ: Int = 0x51
private const val COD: Int = 0x52
private const val QCD: Int = 0x5C
private const val COM: Int = 0x64
private const val SOT: Int = 0x90
