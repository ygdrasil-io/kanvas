package org.graphiks.kanvas.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.graphiks.kanvas.font.scaler.GlyphScaleResult
import org.graphiks.kanvas.font.scaler.OutlineCommand
import org.graphiks.kanvas.geometry.Path

class FontTypefaceIdentityTest {
    @Test
    fun `font typeface snapshots constructor bytes`() {
        val source = liberationFontBytes()
        val typeface = FontTypeface(source, "LiberationSans", faceIndex = 0)
        val sourceId = typeface.sourceId

        source.fill(0)

        assertEquals(sourceId, typeface.sourceId)
        assertFalse(typeface.fontBytes.all { it == 0.toByte() })
        val returnedBytes = typeface.fontBytes
        returnedBytes.fill(0)
        assertFalse(typeface.fontBytes.all { it == 0.toByte() })
    }

    @Test
    fun `font typeface rejects a negative face index`() {
        assertFailsWith<IllegalArgumentException> {
            FontTypeface(liberationFontBytes(), "LiberationSans", faceIndex = -1)
        }
    }

    @Test
    fun `collection faces share source identity but have distinct typeface identities`() {
        val face = liberationFontBytes()
        val collection = ttcFont(face, face)

        val first = FontTypeface(collection, "LiberationSansCollection", faceIndex = 0)
        val second = FontTypeface(collection, "LiberationSansCollection", faceIndex = 1)

        assertEquals(first.sourceId, second.sourceId)
        assertNotNull(first.typefaceId)
        assertNotNull(second.typefaceId)
        assertNotEquals(first.typefaceId, second.typefaceId)
    }

    @Test
    fun `static TrueType public APIs retain legacy scaler results`() {
        val typeface = FontTypeface(liberationFontBytes(), "LiberationSans")
        val scaler = checkNotNull(typeface.scaler)
        val glyphId = checkNotNull(scaler.glyphIdForCodepoint('A'.code))
        val fontSize = 37f
        val scaled = assertIs<GlyphScaleResult.Success>(
            scaler.scaleGlyphOrDiagnostic(glyphId, fontSize),
        ).glyph
        val expectedPath = scaled.commands.toPath()
        val actualPath = assertNotNull(typeface.getGlyphPath(glyphId, fontSize))

        assertEquals(scaler.unitsPerEmInt.toFloat(), typeface.unitsPerEm)
        assertEquals(glyphId, typeface.glyphIdForCodepoint('A'.code))
        assertEquals(scaled.advanceWidth, typeface.getAdvance(glyphId, fontSize))
        assertEquals(expectedPath.verbs(), actualPath.verbs())
        assertEquals(expectedPath.points(), actualPath.points())
    }

    @Test
    fun `collection TrueType public APIs retain legacy scaler fallback`() {
        val face = liberationFontBytes()
        val typeface = FontTypeface(
            ttcFont(face, face),
            "LiberationSansCollection",
            faceIndex = 1,
        )
        val fontSize = 37f

        assertNull(typeface.scaler)
        assertEquals(1_000f, typeface.unitsPerEm)
        assertEquals(0, typeface.glyphIdForCodepoint('A'.code))
        assertEquals(fontSize * 0.5f, typeface.getAdvance(glyphId = 36, fontSize))
        assertNull(typeface.getGlyphPath(glyphId = 36, fontSize))
    }

    private fun liberationFontBytes(): ByteArray =
        checkNotNull(javaClass.classLoader.getResourceAsStream("fonts/liberation/LiberationSans-Regular.ttf")) {
            "Missing Liberation Sans test fixture."
        }.use { stream -> stream.readBytes() }

    private fun ttcFont(vararg faces: ByteArray): ByteArray {
        val headerLength = 12 + faces.size * 4
        val collection = ByteArray(headerLength + faces.sumOf(ByteArray::size))
        collection.writeUInt32(offset = 0, value = 0x74746366)
        collection.writeUInt32(offset = 4, value = 0x00010000)
        collection.writeUInt32(offset = 8, value = faces.size)

        var cursor = headerLength
        faces.forEachIndexed { index, face ->
            collection.writeUInt32(offset = 12 + index * 4, value = cursor)
            val routedFace = face.copyOf()
            val tableCount = routedFace.readUInt16(offset = 4)
            repeat(tableCount) { tableIndex ->
                val recordOffset = 12 + tableIndex * 16
                routedFace.writeUInt32(
                    offset = recordOffset + 8,
                    value = cursor + routedFace.readUInt32(offset = recordOffset + 8),
                )
            }
            routedFace.copyInto(collection, destinationOffset = cursor)
            cursor += routedFace.size
        }
        return collection
    }

    private fun ByteArray.readUInt16(offset: Int): Int =
        ((this[offset].toInt() and 0xff) shl 8) or
            (this[offset + 1].toInt() and 0xff)

    private fun ByteArray.readUInt32(offset: Int): Int =
        ((this[offset].toInt() and 0xff) shl 24) or
            ((this[offset + 1].toInt() and 0xff) shl 16) or
            ((this[offset + 2].toInt() and 0xff) shl 8) or
            (this[offset + 3].toInt() and 0xff)

    private fun ByteArray.writeUInt32(offset: Int, value: Int) {
        this[offset] = (value ushr 24).toByte()
        this[offset + 1] = (value ushr 16).toByte()
        this[offset + 2] = (value ushr 8).toByte()
        this[offset + 3] = value.toByte()
    }

    private fun List<OutlineCommand>.toPath(): Path = Path {
        for (command in this@toPath) {
            when (command) {
                is OutlineCommand.MoveTo -> moveTo(command.x.toFloat(), command.y.toFloat())
                is OutlineCommand.LineTo -> lineTo(command.x.toFloat(), command.y.toFloat())
                is OutlineCommand.QuadraticTo -> quadTo(
                    command.controlX.toFloat(),
                    command.controlY.toFloat(),
                    command.x.toFloat(),
                    command.y.toFloat(),
                )
                is OutlineCommand.CubicTo -> cubicTo(
                    command.controlX1.toFloat(),
                    command.controlY1.toFloat(),
                    command.controlX2.toFloat(),
                    command.controlY2.toFloat(),
                    command.x.toFloat(),
                    command.y.toFloat(),
                )
                is OutlineCommand.Close -> close()
            }
        }
    }
}
