#!/usr/bin/env kotlin

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class TestNameRecord(
    val platformId: Int,
    val encodingId: Int,
    val languageId: Int,
    val nameId: Int,
    val bytes: ByteArray,
)

data class TestCMapRecord(
    val platformId: Int,
    val encodingId: Int,
    val subtable: ByteArray,
)

data class TestFormat4Segment(
    val startCode: Int,
    val endCode: Int,
    val idDelta: Int,
)

data class TestHorizontalMetric(
    val advanceWidth: Int?,
    val leftSideBearing: Int,
)

fun testNameRecord(
    platformId: Int,
    encodingId: Int,
    languageId: Int,
    nameId: Int,
    bytes: ByteArray,
): TestNameRecord = TestNameRecord(platformId, encodingId, languageId, nameId, bytes)

fun testCMapRecord(
    platformId: Int,
    encodingId: Int,
    subtable: ByteArray,
): TestCMapRecord = TestCMapRecord(platformId, encodingId, subtable)

fun testFormat4Segment(
    startCode: Int,
    endCode: Int,
    startGlyphId: Int,
): TestFormat4Segment = TestFormat4Segment(
    startCode = startCode,
    endCode = endCode,
    idDelta = startGlyphId - startCode,
)

fun metric(advanceWidth: Int, leftSideBearing: Int): TestHorizontalMetric =
    TestHorizontalMetric(advanceWidth = advanceWidth, leftSideBearing = leftSideBearing)

fun extraLeftSideBearing(leftSideBearing: Int): TestHorizontalMetric =
    TestHorizontalMetric(advanceWidth = null, leftSideBearing = leftSideBearing)

fun ByteArray.writeUInt16(offset: Int, value: Int) {
    this[offset] = ((value ushr 8) and 0xff).toByte()
    this[offset + 1] = (value and 0xff).toByte()
}

fun ByteArray.writeUInt32(offset: Int, value: Long) {
    this[offset] = ((value ushr 24) and 0xff).toByte()
    this[offset + 1] = ((value ushr 16) and 0xff).toByte()
    this[offset + 2] = ((value ushr 8) and 0xff).toByte()
    this[offset + 3] = (value and 0xff).toByte()
}

fun ByteArray.writeInt16(offset: Int, value: Int) {
    writeUInt16(offset, value and 0xffff)
}

fun nameTable(vararg records: TestNameRecord): ByteArray {
    val stringOffset = 6 + records.size * 12
    val table = ByteArray(stringOffset + records.sumOf { it.bytes.size })
    table.writeUInt16(0, 0)
    table.writeUInt16(2, records.size)
    table.writeUInt16(4, stringOffset)
    var stringCursor = 0
    records.forEachIndexed { index, record ->
        val recordOffset = 6 + index * 12
        table.writeUInt16(recordOffset, record.platformId)
        table.writeUInt16(recordOffset + 2, record.encodingId)
        table.writeUInt16(recordOffset + 4, record.languageId)
        table.writeUInt16(recordOffset + 6, record.nameId)
        table.writeUInt16(recordOffset + 8, record.bytes.size)
        table.writeUInt16(recordOffset + 10, stringCursor)
        record.bytes.copyInto(table, stringOffset + stringCursor)
        stringCursor += record.bytes.size
    }
    return table
}

fun format4Subtable(vararg segments: TestFormat4Segment): ByteArray {
    val segCount = segments.size + 1
    val segCountX2 = segCount * 2
    val searchPower = Integer.highestOneBit(segCount)
    val searchRange = searchPower * 2
    val entrySelector = Integer.numberOfTrailingZeros(searchPower)
    val rangeShift = segCountX2 - searchRange
    val length = 16 + segCount * 8
    val subtable = ByteArray(length)
    subtable.writeUInt16(0, 4)
    subtable.writeUInt16(2, length)
    subtable.writeUInt16(4, 0)
    subtable.writeUInt16(6, segCountX2)
    subtable.writeUInt16(8, searchRange)
    subtable.writeUInt16(10, entrySelector)
    subtable.writeUInt16(12, rangeShift)

    val endCodesOffset = 14
    val startCodesOffset = endCodesOffset + segCountX2 + 2
    val idDeltaOffset = startCodesOffset + segCountX2
    val idRangeOffsetOffset = idDeltaOffset + segCountX2

    val sorted = segments.sortedBy { it.startCode }
    sorted.forEachIndexed { index, segment ->
        val position = endCodesOffset + index * 2
        subtable.writeUInt16(position, segment.endCode)
        subtable.writeUInt16(startCodesOffset + index * 2, segment.startCode)
        subtable.writeInt16(idDeltaOffset + index * 2, segment.idDelta)
        subtable.writeUInt16(idRangeOffsetOffset + index * 2, 0)
    }
    subtable.writeUInt16(endCodesOffset + sorted.size * 2, 0xffff)
    subtable.writeUInt16(startCodesOffset + sorted.size * 2, 0xffff)
    subtable.writeInt16(idDeltaOffset + sorted.size * 2, 1)
    subtable.writeUInt16(idRangeOffsetOffset + sorted.size * 2, 0)
    return subtable
}

fun cmapTable(vararg records: TestCMapRecord): ByteArray {
    val headerSize = 4 + records.size * 8
    val table = ByteArray(headerSize + records.sumOf { it.subtable.size })
    table.writeUInt16(0, 0)
    table.writeUInt16(2, records.size)
    var offset = headerSize
    records.forEachIndexed { index, record ->
        val recordOffset = 4 + index * 8
        table.writeUInt16(recordOffset, record.platformId)
        table.writeUInt16(recordOffset + 2, record.encodingId)
        table.writeUInt32(recordOffset + 4, offset.toLong())
        record.subtable.copyInto(table, offset)
        offset += record.subtable.size
    }
    return table
}

data class OpenTypeFontBounds(
    val xMin: Int,
    val yMin: Int,
    val xMax: Int,
    val yMax: Int,
)

fun headTable(
    unitsPerEm: Int,
    bounds: OpenTypeFontBounds,
    indexToLocFormat: Int,
): ByteArray {
    val table = ByteArray(54)
    table.writeUInt32(0, 0x00010000)
    table.writeUInt32(4, 0)
    table.writeUInt32(8, 0)
    table.writeUInt32(12, 0x5f0f3cf5)
    table.writeUInt16(16, 0)
    table.writeUInt16(18, unitsPerEm)
    table.writeUInt32(20, 0)
    table.writeUInt32(24, 0)
    table.writeUInt32(28, 0)
    table.writeUInt32(32, 0)
    table.writeInt16(36, bounds.xMin)
    table.writeInt16(38, bounds.yMin)
    table.writeInt16(40, bounds.xMax)
    table.writeInt16(42, bounds.yMax)
    table.writeUInt16(44, 0)
    table.writeUInt16(46, 8)
    table.writeInt16(48, 2)
    table.writeInt16(50, indexToLocFormat)
    table.writeInt16(52, 0)
    return table
}

fun hheaTable(
    ascender: Int,
    descender: Int,
    lineGap: Int,
    numberOfHMetrics: Int,
): ByteArray {
    val table = ByteArray(36)
    table.writeUInt32(0, 0x00010000)
    table.writeInt16(4, ascender)
    table.writeInt16(6, descender)
    table.writeInt16(8, lineGap)
    table.writeUInt16(10, 500)
    table.writeInt16(12, 0)
    table.writeInt16(14, 0)
    table.writeInt16(16, 500)
    table.writeInt16(18, 1)
    table.writeInt16(20, 0)
    table.writeInt16(22, 0)
    table.writeInt16(24, 0)
    table.writeInt16(26, 0)
    table.writeInt16(28, 0)
    table.writeInt16(30, 0)
    table.writeInt16(32, 0)
    table.writeUInt16(34, numberOfHMetrics)
    return table
}

fun maxpTable(numGlyphs: Int): ByteArray {
    val table = ByteArray(6)
    table.writeUInt32(0, 0x00010000)
    table.writeUInt16(4, numGlyphs)
    return table
}

fun hmtxTable(vararg metrics: TestHorizontalMetric): ByteArray {
    val metricCount = metrics.count { it.advanceWidth != null }
    val table = ByteArray(metricCount * 4 + (metrics.size - metricCount) * 2)
    var offset = 0
    metrics.forEach { metric ->
        if (metric.advanceWidth != null) {
            table.writeUInt16(offset, metric.advanceWidth)
            table.writeInt16(offset + 2, metric.leftSideBearing)
            offset += 4
        } else {
            table.writeInt16(offset, metric.leftSideBearing)
            offset += 2
        }
    }
    return table
}

fun sfntFont(vararg tables: Pair<String, ByteArray>): ByteArray {
    val directoryLength = 12 + tables.size * 16
    val totalLength = directoryLength + tables.sumOf { it.second.size }
    val font = ByteArray(totalLength)
    font.writeUInt32(0, 0x00010000)
    font.writeUInt16(4, tables.size)

    var payloadOffset = directoryLength
    tables.forEachIndexed { index, (tag, payload) ->
        require(tag.length == 4) { "SFNT tag must be four characters: $tag" }
        val recordOffset = 12 + index * 16
        tag.toByteArray(Charsets.ISO_8859_1).copyInto(font, recordOffset)
        font.writeUInt32(recordOffset + 4, checksum(payload).toLong())
        font.writeUInt32(recordOffset + 8, payloadOffset.toLong())
        font.writeUInt32(recordOffset + 12, payload.size.toLong())
        payload.copyInto(font, payloadOffset)
        payloadOffset += payload.size
    }
    return font
}

fun checksum(bytes: ByteArray): Int {
    var sum = 0L
    var index = 0
    while (index < bytes.size) {
        var value = 0L
        repeat(4) { shift ->
            value = (value shl 8) or ((bytes.getOrNull(index + shift)?.toInt() ?: 0) and 0xff).toLong()
        }
        sum = (sum + value) and 0xffffffffL
        index += 4
    }
    return sum.toInt()
}

fun baseFont(
    family: String,
    cmapSegments: List<TestFormat4Segment>,
    numGlyphs: Int,
    layoutTag: String,
    layoutTable: ByteArray,
): ByteArray =
    sfntFont(
        "name" to nameTable(
            testNameRecord(
                platformId = 3,
                encodingId = 1,
                languageId = 0x0409,
                nameId = 1,
                bytes = family.toByteArray(Charsets.UTF_16BE),
            ),
        ),
        "cmap" to cmapTable(
            testCMapRecord(
                platformId = 3,
                encodingId = 1,
                subtable = format4Subtable(*cmapSegments.toTypedArray()),
            ),
        ),
        "head" to headTable(
            unitsPerEm = 1000,
            bounds = OpenTypeFontBounds(xMin = 0, yMin = 0, xMax = 1000, yMax = 1000),
            indexToLocFormat = 0,
        ),
        "hhea" to hheaTable(
            ascender = 800,
            descender = -200,
            lineGap = 0,
            numberOfHMetrics = 2,
        ),
        "maxp" to maxpTable(numGlyphs = numGlyphs),
        "hmtx" to hmtxTable(
            metric(advanceWidth = 500, leftSideBearing = 0),
            metric(advanceWidth = 450, leftSideBearing = 0),
            *Array(maxOf(0, numGlyphs - 2)) { extraLeftSideBearing(leftSideBearing = 0) },
        ),
        layoutTag to layoutTable,
    )

fun gsubSingleSubstitutionTable(): ByteArray {
    val table = ByteArray(70)
    val scriptListOffset = 10
    val featureListOffset = 30
    val lookupListOffset = 44
    val scriptStart = scriptListOffset + 8
    val langSysStart = scriptStart + 4
    val featureStart = featureListOffset + 8
    val lookupStart = lookupListOffset + 4
    val subtableStart = lookupStart + 8

    table.writeUInt16(0, 1)
    table.writeUInt16(2, 0)
    table.writeUInt16(4, scriptListOffset)
    table.writeUInt16(6, featureListOffset)
    table.writeUInt16(8, lookupListOffset)
    table.writeUInt16(scriptListOffset, 1)
    "latn".toByteArray(Charsets.ISO_8859_1).copyInto(table, scriptListOffset + 2)
    table.writeUInt16(scriptListOffset + 6, 8)
    table.writeUInt16(scriptStart, 4)
    table.writeUInt16(scriptStart + 2, 0)
    table.writeUInt16(langSysStart, 0)
    table.writeUInt16(langSysStart + 2, 0xffff)
    table.writeUInt16(langSysStart + 4, 1)
    table.writeUInt16(langSysStart + 6, 0)
    table.writeUInt16(featureListOffset, 1)
    "ccmp".toByteArray(Charsets.ISO_8859_1).copyInto(table, featureListOffset + 2)
    table.writeUInt16(featureListOffset + 6, 8)
    table.writeUInt16(featureStart, 0)
    table.writeUInt16(featureStart + 2, 1)
    table.writeUInt16(featureStart + 4, 0)
    table.writeUInt16(lookupListOffset, 1)
    table.writeUInt16(lookupListOffset + 2, 4)
    table.writeUInt16(lookupStart, 1)
    table.writeUInt16(lookupStart + 2, 0)
    table.writeUInt16(lookupStart + 4, 1)
    table.writeUInt16(lookupStart + 6, 8)
    table.writeUInt16(subtableStart, 2)
    table.writeUInt16(subtableStart + 2, 8)
    table.writeUInt16(subtableStart + 4, 1)
    table.writeUInt16(subtableStart + 6, 15)
    table.writeUInt16(subtableStart + 8, 1)
    table.writeUInt16(subtableStart + 10, 1)
    table.writeUInt16(subtableStart + 12, 5)
    return table
}

fun gsubMultipleSubstitutionTable(): ByteArray {
    val table = ByteArray(76)
    val scriptListOffset = 10
    val featureListOffset = 30
    val lookupListOffset = 44
    val scriptStart = scriptListOffset + 8
    val langSysStart = scriptStart + 4
    val featureStart = featureListOffset + 8
    val lookupStart = lookupListOffset + 4
    val subtableStart = lookupStart + 8

    table.writeUInt16(0, 1)
    table.writeUInt16(2, 0)
    table.writeUInt16(4, scriptListOffset)
    table.writeUInt16(6, featureListOffset)
    table.writeUInt16(8, lookupListOffset)
    table.writeUInt16(scriptListOffset, 1)
    "latn".toByteArray(Charsets.ISO_8859_1).copyInto(table, scriptListOffset + 2)
    table.writeUInt16(scriptListOffset + 6, 8)
    table.writeUInt16(scriptStart, 4)
    table.writeUInt16(scriptStart + 2, 0)
    table.writeUInt16(langSysStart, 0)
    table.writeUInt16(langSysStart + 2, 0xffff)
    table.writeUInt16(langSysStart + 4, 1)
    table.writeUInt16(langSysStart + 6, 0)
    table.writeUInt16(featureListOffset, 1)
    "ccmp".toByteArray(Charsets.ISO_8859_1).copyInto(table, featureListOffset + 2)
    table.writeUInt16(featureListOffset + 6, 8)
    table.writeUInt16(featureStart, 0)
    table.writeUInt16(featureStart + 2, 1)
    table.writeUInt16(featureStart + 4, 0)
    table.writeUInt16(lookupListOffset, 1)
    table.writeUInt16(lookupListOffset + 2, 4)
    table.writeUInt16(lookupStart, 2)
    table.writeUInt16(lookupStart + 2, 0)
    table.writeUInt16(lookupStart + 4, 1)
    table.writeUInt16(lookupStart + 6, 8)
    table.writeUInt16(subtableStart, 1)
    table.writeUInt16(subtableStart + 2, 14)
    table.writeUInt16(subtableStart + 4, 1)
    table.writeUInt16(subtableStart + 6, 8)
    table.writeUInt16(subtableStart + 8, 2)
    table.writeUInt16(subtableStart + 10, 16)
    table.writeUInt16(subtableStart + 12, 17)
    table.writeUInt16(subtableStart + 14, 1)
    table.writeUInt16(subtableStart + 16, 1)
    table.writeUInt16(subtableStart + 18, 6)
    return table
}

fun gsubLigatureTable(): ByteArray {
    val table = ByteArray(80)
    val scriptListOffset = 10
    val featureListOffset = 30
    val lookupListOffset = 44
    val scriptStart = scriptListOffset + 8
    val langSysStart = scriptStart + 4
    val featureStart = featureListOffset + 8
    val lookupStart = lookupListOffset + 4
    val subtableStart = lookupStart + 8

    table.writeUInt16(0, 1)
    table.writeUInt16(2, 0)
    table.writeUInt16(4, scriptListOffset)
    table.writeUInt16(6, featureListOffset)
    table.writeUInt16(8, lookupListOffset)
    table.writeUInt16(scriptListOffset, 1)
    "latn".toByteArray(Charsets.ISO_8859_1).copyInto(table, scriptListOffset + 2)
    table.writeUInt16(scriptListOffset + 6, 8)
    table.writeUInt16(scriptStart, 4)
    table.writeUInt16(scriptStart + 2, 0)
    table.writeUInt16(langSysStart, 0)
    table.writeUInt16(langSysStart + 2, 0xffff)
    table.writeUInt16(langSysStart + 4, 1)
    table.writeUInt16(langSysStart + 6, 0)
    table.writeUInt16(featureListOffset, 1)
    "liga".toByteArray(Charsets.ISO_8859_1).copyInto(table, featureListOffset + 2)
    table.writeUInt16(featureListOffset + 6, 8)
    table.writeUInt16(featureStart, 0)
    table.writeUInt16(featureStart + 2, 1)
    table.writeUInt16(featureStart + 4, 0)
    table.writeUInt16(lookupListOffset, 1)
    table.writeUInt16(lookupListOffset + 2, 4)
    table.writeUInt16(lookupStart, 4)
    table.writeUInt16(lookupStart + 2, 0)
    table.writeUInt16(lookupStart + 4, 1)
    table.writeUInt16(lookupStart + 6, 8)
    table.writeUInt16(subtableStart, 1)
    table.writeUInt16(subtableStart + 2, 18)
    table.writeUInt16(subtableStart + 4, 1)
    table.writeUInt16(subtableStart + 6, 8)
    table.writeUInt16(subtableStart + 8, 1)
    table.writeUInt16(subtableStart + 10, 4)
    table.writeUInt16(subtableStart + 12, 42)
    table.writeUInt16(subtableStart + 14, 2)
    table.writeUInt16(subtableStart + 16, 10)
    table.writeUInt16(subtableStart + 18, 1)
    table.writeUInt16(subtableStart + 20, 1)
    table.writeUInt16(subtableStart + 22, 7)
    return table
}

fun gposPairAdjustmentFormat1Table(
    leftGlyphId: Int,
    rightGlyphId: Int,
    xAdvance: Int,
    declaredPairSetCount: Int = 1,
): ByteArray {
    val table = ByteArray(80)
    val scriptListOffset = 10
    val featureListOffset = 30
    val lookupListOffset = 44
    val scriptStart = scriptListOffset + 8
    val langSysStart = scriptStart + 4
    val featureStart = featureListOffset + 8
    val lookupStart = lookupListOffset + 4
    val subtableStart = lookupStart + 8
    val coverageOffset = 12
    val pairSetOffset = 18

    table.writeUInt16(0, 1)
    table.writeUInt16(2, 0)
    table.writeUInt16(4, scriptListOffset)
    table.writeUInt16(6, featureListOffset)
    table.writeUInt16(8, lookupListOffset)
    table.writeUInt16(scriptListOffset, 1)
    "latn".toByteArray(Charsets.ISO_8859_1).copyInto(table, scriptListOffset + 2)
    table.writeUInt16(scriptListOffset + 6, 8)
    table.writeUInt16(scriptStart, 4)
    table.writeUInt16(scriptStart + 2, 0)
    table.writeUInt16(langSysStart, 0)
    table.writeUInt16(langSysStart + 2, 0xffff)
    table.writeUInt16(langSysStart + 4, 1)
    table.writeUInt16(langSysStart + 6, 0)
    table.writeUInt16(featureListOffset, 1)
    "kern".toByteArray(Charsets.ISO_8859_1).copyInto(table, featureListOffset + 2)
    table.writeUInt16(featureListOffset + 6, 8)
    table.writeUInt16(featureStart, 0)
    table.writeUInt16(featureStart + 2, 1)
    table.writeUInt16(featureStart + 4, 0)
    table.writeUInt16(lookupListOffset, 1)
    table.writeUInt16(lookupListOffset + 2, 4)
    table.writeUInt16(lookupStart, 2)
    table.writeUInt16(lookupStart + 2, 0)
    table.writeUInt16(lookupStart + 4, 1)
    table.writeUInt16(lookupStart + 6, 8)
    table.writeUInt16(subtableStart, 1)
    table.writeUInt16(subtableStart + 2, coverageOffset)
    table.writeUInt16(subtableStart + 4, 0x0004)
    table.writeUInt16(subtableStart + 6, 0)
    table.writeUInt16(subtableStart + 8, declaredPairSetCount)
    table.writeUInt16(subtableStart + 10, pairSetOffset)
    table.writeUInt16(subtableStart + coverageOffset, 1)
    table.writeUInt16(subtableStart + coverageOffset + 2, 1)
    table.writeUInt16(subtableStart + coverageOffset + 4, leftGlyphId)
    table.writeUInt16(subtableStart + pairSetOffset, 1)
    table.writeUInt16(subtableStart + pairSetOffset + 2, rightGlyphId)
    table.writeInt16(subtableStart + pairSetOffset + 4, xAdvance)
    return table
}

fun gposSingleAdjustmentFormat1Table(
    glyphId: Int,
    xPlacement: Int = 0,
    yPlacement: Int = 0,
    xAdvance: Int = 0,
    valueFormat: Int = 0x0007,
): ByteArray {
    val table = ByteArray(78)
    val scriptListOffset = 10
    val featureListOffset = 30
    val lookupListOffset = 44
    val scriptStart = scriptListOffset + 8
    val langSysStart = scriptStart + 4
    val featureStart = featureListOffset + 8
    val lookupStart = lookupListOffset + 4
    val subtableStart = lookupStart + 8
    val coverageOffset = 12
    val valueRecordStart = subtableStart + 6

    table.writeUInt16(0, 1)
    table.writeUInt16(2, 0)
    table.writeUInt16(4, scriptListOffset)
    table.writeUInt16(6, featureListOffset)
    table.writeUInt16(8, lookupListOffset)
    table.writeUInt16(scriptListOffset, 1)
    "latn".toByteArray(Charsets.ISO_8859_1).copyInto(table, scriptListOffset + 2)
    table.writeUInt16(scriptListOffset + 6, 8)
    table.writeUInt16(scriptStart, 4)
    table.writeUInt16(scriptStart + 2, 0)
    table.writeUInt16(langSysStart, 0)
    table.writeUInt16(langSysStart + 2, 0xffff)
    table.writeUInt16(langSysStart + 4, 1)
    table.writeUInt16(langSysStart + 6, 0)
    table.writeUInt16(featureListOffset, 1)
    "kern".toByteArray(Charsets.ISO_8859_1).copyInto(table, featureListOffset + 2)
    table.writeUInt16(featureListOffset + 6, 8)
    table.writeUInt16(featureStart, 0)
    table.writeUInt16(featureStart + 2, 1)
    table.writeUInt16(featureStart + 4, 0)
    table.writeUInt16(lookupListOffset, 1)
    table.writeUInt16(lookupListOffset + 2, 4)
    table.writeUInt16(lookupStart, 1)
    table.writeUInt16(lookupStart + 2, 0)
    table.writeUInt16(lookupStart + 4, 1)
    table.writeUInt16(lookupStart + 6, 8)
    table.writeUInt16(subtableStart, 1)
    table.writeUInt16(subtableStart + 2, coverageOffset)
    table.writeUInt16(subtableStart + 4, valueFormat)
    table.writeInt16(valueRecordStart, xPlacement)
    table.writeInt16(valueRecordStart + 2, yPlacement)
    table.writeInt16(valueRecordStart + 4, xAdvance)
    table.writeUInt16(subtableStart + coverageOffset, 1)
    table.writeUInt16(subtableStart + coverageOffset + 2, 1)
    table.writeUInt16(subtableStart + coverageOffset + 4, glyphId)
    return table
}

fun gposPairAdjustmentFormat2Class0Table(
    coverageGlyphCount: Int,
    xAdvance: Int,
): ByteArray {
    val table = ByteArray(92)
    val scriptListOffset = 10
    val featureListOffset = 30
    val lookupListOffset = 44
    val scriptStart = scriptListOffset + 8
    val langSysStart = scriptStart + 4
    val featureStart = featureListOffset + 8
    val lookupStart = lookupListOffset + 4
    val subtableStart = lookupStart + 8
    val coverageOffset = 18
    val classDef1Offset = 28
    val classDef2Offset = 32

    table.writeUInt16(0, 1)
    table.writeUInt16(2, 0)
    table.writeUInt16(4, scriptListOffset)
    table.writeUInt16(6, featureListOffset)
    table.writeUInt16(8, lookupListOffset)
    table.writeUInt16(scriptListOffset, 1)
    "latn".toByteArray(Charsets.ISO_8859_1).copyInto(table, scriptListOffset + 2)
    table.writeUInt16(scriptListOffset + 6, 8)
    table.writeUInt16(scriptStart, 4)
    table.writeUInt16(scriptStart + 2, 0)
    table.writeUInt16(langSysStart, 0)
    table.writeUInt16(langSysStart + 2, 0xffff)
    table.writeUInt16(langSysStart + 4, 1)
    table.writeUInt16(langSysStart + 6, 0)
    table.writeUInt16(featureListOffset, 1)
    "kern".toByteArray(Charsets.ISO_8859_1).copyInto(table, featureListOffset + 2)
    table.writeUInt16(featureListOffset + 6, 8)
    table.writeUInt16(featureStart, 0)
    table.writeUInt16(featureStart + 2, 1)
    table.writeUInt16(featureStart + 4, 0)
    table.writeUInt16(lookupListOffset, 1)
    table.writeUInt16(lookupListOffset + 2, 4)
    table.writeUInt16(lookupStart, 2)
    table.writeUInt16(lookupStart + 2, 0)
    table.writeUInt16(lookupStart + 4, 1)
    table.writeUInt16(lookupStart + 6, 8)
    table.writeUInt16(subtableStart, 2)
    table.writeUInt16(subtableStart + 2, coverageOffset)
    table.writeUInt16(subtableStart + 4, 0x0004)
    table.writeUInt16(subtableStart + 6, 0)
    table.writeUInt16(subtableStart + 8, classDef1Offset)
    table.writeUInt16(subtableStart + 10, classDef2Offset)
    table.writeUInt16(subtableStart + 12, 1)
    table.writeUInt16(subtableStart + 14, 1)
    table.writeInt16(subtableStart + 16, xAdvance)
    table.writeUInt16(subtableStart + coverageOffset, 2)
    table.writeUInt16(subtableStart + coverageOffset + 2, 1)
    table.writeUInt16(subtableStart + coverageOffset + 4, 0)
    table.writeUInt16(subtableStart + coverageOffset + 6, coverageGlyphCount - 1)
    table.writeUInt16(subtableStart + coverageOffset + 8, 0)
    table.writeUInt16(subtableStart + classDef1Offset, 2)
    table.writeUInt16(subtableStart + classDef1Offset + 2, 0)
    table.writeUInt16(subtableStart + classDef2Offset, 2)
    table.writeUInt16(subtableStart + classDef2Offset + 2, 0)
    return table
}

fun projectRoot(): Path =
    generateSequence(Paths.get("").toAbsolutePath().normalize()) { it.parent }
        .first { Files.exists(it.resolve("settings.gradle.kts")) }

val root = projectRoot()
val shapingDir = root.resolve("reports/font/fixtures/fonts/shaping")
Files.createDirectories(shapingDir)

val fixtures = linkedMapOf(
    "gsub-single-substitution.otf" to baseFont(
        family = "KFONT GSUB Single",
        cmapSegments = listOf(testFormat4Segment(startCode = 0x0061, endCode = 0x0061, startGlyphId = 5)),
        numGlyphs = 32,
        layoutTag = "GSUB",
        layoutTable = gsubSingleSubstitutionTable(),
    ),
    "gsub-multiple-substitution.otf" to baseFont(
        family = "KFONT GSUB Multiple",
        cmapSegments = listOf(testFormat4Segment(startCode = 0x0062, endCode = 0x0062, startGlyphId = 6)),
        numGlyphs = 32,
        layoutTag = "GSUB",
        layoutTable = gsubMultipleSubstitutionTable(),
    ),
    "gsub-ligature-fi.otf" to baseFont(
        family = "KFONT GSUB Ligature",
        cmapSegments = listOf(testFormat4Segment(startCode = 0x0066, endCode = 0x0069, startGlyphId = 7)),
        numGlyphs = 64,
        layoutTag = "GSUB",
        layoutTable = gsubLigatureTable(),
    ),
    "gsub-coverage-malformed.otf" to baseFont(
        family = "KFONT GSUB Bad Coverage",
        cmapSegments = listOf(testFormat4Segment(startCode = 0x0061, endCode = 0x0061, startGlyphId = 5)),
        numGlyphs = 32,
        layoutTag = "GSUB",
        layoutTable = gsubSingleSubstitutionTable().also { it.writeUInt16(58, 3) },
    ),
    "gsub-ligature-bad-component.otf" to baseFont(
        family = "KFONT GSUB Bad Ligature",
        cmapSegments = listOf(testFormat4Segment(startCode = 0x0066, endCode = 0x0069, startGlyphId = 7)),
        numGlyphs = 64,
        layoutTag = "GSUB",
        layoutTable = gsubLigatureTable().also { it.writeUInt16(70, 1) },
    ),
    "gpos-single-adjustment.otf" to baseFont(
        family = "KFONT GPOS Single",
        cmapSegments = listOf(testFormat4Segment(startCode = 0x0041, endCode = 0x0041, startGlyphId = 7)),
        numGlyphs = 32,
        layoutTag = "GPOS",
        layoutTable = gposSingleAdjustmentFormat1Table(glyphId = 7, xPlacement = 40, yPlacement = -20, xAdvance = -30),
    ),
    "gpos-pair-format1-kerning.otf" to baseFont(
        family = "KFONT GPOS Pair1",
        cmapSegments = listOf(testFormat4Segment(startCode = 0x0041, endCode = 0x0056, startGlyphId = 7)),
        numGlyphs = 32,
        layoutTag = "GPOS",
        layoutTable = gposPairAdjustmentFormat1Table(leftGlyphId = 7, rightGlyphId = 11, xAdvance = -55),
    ),
    "gpos-pair-format2-class.otf" to baseFont(
        family = "KFONT GPOS Pair2",
        cmapSegments = listOf(testFormat4Segment(startCode = 0x0041, endCode = 0x0056, startGlyphId = 7)),
        numGlyphs = 260,
        layoutTag = "GPOS",
        layoutTable = gposPairAdjustmentFormat2Class0Table(coverageGlyphCount = 2, xAdvance = -40),
    ),
    "gpos-valueformat-malformed.otf" to baseFont(
        family = "KFONT GPOS Bad Value",
        cmapSegments = listOf(testFormat4Segment(startCode = 0x0041, endCode = 0x0056, startGlyphId = 7)),
        numGlyphs = 32,
        layoutTag = "GPOS",
        layoutTable = gposPairAdjustmentFormat1Table(leftGlyphId = 7, rightGlyphId = 11, xAdvance = -55)
            .also { it.writeUInt16(60, 0x03ff) },
    ),
    "gpos-pair-out-of-range.otf" to baseFont(
        family = "KFONT GPOS Pair OOR",
        cmapSegments = listOf(testFormat4Segment(startCode = 0x0041, endCode = 0x0056, startGlyphId = 7)),
        numGlyphs = 32,
        layoutTag = "GPOS",
        layoutTable = gposPairAdjustmentFormat1Table(leftGlyphId = 7, rightGlyphId = 11, xAdvance = -55, declaredPairSetCount = 2),
    ),
)

fixtures.forEach { (name, bytes) ->
    Files.write(shapingDir.resolve(name), bytes)
    println("wrote reports/font/fixtures/fonts/shaping/$name (${bytes.size} bytes)")
}
