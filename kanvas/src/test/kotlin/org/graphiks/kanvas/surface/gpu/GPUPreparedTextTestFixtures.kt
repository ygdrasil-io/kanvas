package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.text.FontTypeface

/**
 * Deterministic FP-05 inputs. Accessors intentionally allocate fresh arrays/lists so tests
 * cannot turn a later cold frame into a shared mutable or cache-like sample.
 */
object GPUPreparedTextTestFixtures {
    fun a8CoverageLevels(): ByteArray =
        byteArrayOf(0, 1, 128.toByte(), 255.toByte())

    fun diagonalAntialiasedGlyph(): ByteArray = byteArrayOf(
        255.toByte(), 128.toByte(), 1, 0,
        128.toByte(), 255.toByte(), 128.toByte(), 1,
        1, 128.toByte(), 255.toByte(), 128.toByte(),
        0, 1, 128.toByte(), 255.toByte(),
    )

    fun repeatedGlyphPageSharing(): List<Int> = listOf(7, 7, 8, 7)

    fun fontFaces(): List<FontTypeface> {
        val collection = ttcFont(
            colrFontBytesWithForegroundLayer(),
            colrFontBytesWithForegroundLayer(),
        )
        return listOf(
            FontTypeface(collection, "task13-ttc-face-0", faceIndex = 0),
            FontTypeface(collection, "task13-ttc-face-1", faceIndex = 1),
        )
    }

    fun fontWithoutNotdef(source: FontTypeface): FontTypeface {
        val bytes = source.fontBytes
        val loca = sfntTableOffset(bytes, "loca")
        val head = sfntTableOffset(bytes, "head")
        when (readU16(bytes, head + 50)) {
            0 -> writeU16(bytes, loca + 2, readU16(bytes, loca))
            1 -> writeU32(bytes, loca + 4, readU32(bytes, loca))
            else -> error("Unsupported task13 loca format")
        }
        return FontTypeface(bytes, "task13-missing-notdef")
    }

    fun colrPaletteAndForeground(): ColrPaletteCase = ColrPaletteCase(
        paletteArgb = listOf(0xffff2a2a.toInt(), 0xff2244ee.toInt()),
        foregroundArgb = 0x9f8040bf.toInt(),
    )

    fun colrFontBytesWithForegroundLayer(): ByteArray {
        val bytes = requireNotNull(
            javaClass.classLoader.getResourceAsStream("fonts/skia/colr.ttf"),
        ).use { stream -> stream.readBytes() }
        require(bytes.sha256Hex() == PINNED_SKIA_COLR_SHA256)
        val colr = sfntTableOffset(bytes, "COLR")
        require(readU16(bytes, colr) == 0)
        val baseRecords = colr + readU32(bytes, colr + 4)
        val layerRecords = colr + readU32(bytes, colr + 8)
        val baseRecord = (0 until readU16(bytes, colr + 2))
            .map { index -> baseRecords + index * 6 }
            .single { offset -> readU16(bytes, offset) == COLOR_BASE_GLYPH_ID }
        val firstLayerIndex = readU16(bytes, baseRecord + 2)
        require(readU16(bytes, baseRecord + 4) == 2)
        writeU16(
            bytes,
            layerRecords + (firstLayerIndex + 1) * 4 + 2,
            FOREGROUND_PALETTE_INDEX,
        )
        patchSimpleRectangleGlyph(bytes, A8_GLYPH_ID, 0, -375, 500, 375)
        patchSimpleRectangleGlyph(bytes, 8, 125, -250, 375, 250)
        require(bytes.sha256Hex() == PATCHED_SOURCE_FONT_SHA256)
        return bytes.copyOf()
    }

    data class ColrPaletteCase(
        val paletteArgb: List<Int>,
        val foregroundArgb: Int,
    )

    private fun patchSimpleRectangleGlyph(
        bytes: ByteArray,
        glyphId: Int,
        xMin: Int,
        yMin: Int,
        xMax: Int,
        yMax: Int,
    ) {
        val slot = glyphSlotRange(bytes, glyphId)
        bytes.fill(0, slot.first, slot.last + 1)
        var cursor = slot.first
        fun signed(value: Int) {
            writeU16(bytes, cursor, value and 0xffff)
            cursor += 2
        }
        signed(1)
        listOf(xMin, yMin, xMax, yMax).forEach(::signed)
        writeU16(bytes, cursor, 3)
        cursor += 2
        writeU16(bytes, cursor, 0)
        cursor += 2
        repeat(4) { bytes[cursor++] = 0x01 }
        listOf(xMin, xMax - xMin, 0, xMin - xMax).forEach(::signed)
        listOf(yMin, 0, yMax - yMin, 0).forEach(::signed)
        require(cursor == slot.first + SIMPLE_RECTANGLE_GLYPH_BYTES)
    }

    private fun glyphSlotRange(bytes: ByteArray, glyphId: Int): IntRange {
        val head = sfntTableOffset(bytes, "head")
        require(readU16(bytes, head + 50) == 0)
        val loca = sfntTableOffset(bytes, "loca")
        val glyf = sfntTableOffset(bytes, "glyf")
        val start = glyf + readU16(bytes, loca + glyphId * 2) * 2
        val end = glyf + readU16(bytes, loca + (glyphId + 1) * 2) * 2
        require(end - start >= SIMPLE_RECTANGLE_GLYPH_BYTES)
        return start until end
    }

    private fun sfntTableOffset(bytes: ByteArray, wantedTag: String): Int {
        repeat(readU16(bytes, 4)) { index ->
            val entry = 12 + index * 16
            if (String(bytes, entry, 4, Charsets.ISO_8859_1) == wantedTag) {
                return readU32(bytes, entry + 8)
            }
        }
        error("Missing $wantedTag table")
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or
            (bytes[offset + 1].toInt() and 0xff)

    private fun readU32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun ttcFont(vararg faces: ByteArray): ByteArray {
        val headerLength = 12 + faces.size * 4
        val collection = ByteArray(headerLength + faces.sumOf(ByteArray::size))
        writeU32(collection, 0, 0x74746366)
        writeU32(collection, 4, 0x00010000)
        writeU32(collection, 8, faces.size)
        var cursor = headerLength
        faces.forEachIndexed { index, face ->
            writeU32(collection, 12 + index * 4, cursor)
            val routedFace = face.copyOf()
            repeat(readU16(routedFace, 4)) { tableIndex ->
                val recordOffset = 12 + tableIndex * 16
                writeU32(
                    routedFace,
                    recordOffset + 8,
                    cursor + readU32(routedFace, recordOffset + 8),
                )
            }
            routedFace.copyInto(collection, destinationOffset = cursor)
            cursor += routedFace.size
        }
        return collection
    }

    private fun ByteArray.sha256Hex(): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    const val A8_GLYPH_ID = 7
    const val COLOR_BASE_GLYPH_ID = 2
    const val FOREGROUND_PALETTE_INDEX = 0xffff
    private const val SIMPLE_RECTANGLE_GLYPH_BYTES = 34
    private const val PINNED_SKIA_COLR_SHA256 =
        "77d9465a9a1c2bccceda4666fe3cebbd96a85cdfd07dbc42c2b310bc7767372e"
    private const val PATCHED_SOURCE_FONT_SHA256 =
        "7fe253c74758df56226679d9e43965e78bbdb2437d2b7d4788d918805323874d"
}
