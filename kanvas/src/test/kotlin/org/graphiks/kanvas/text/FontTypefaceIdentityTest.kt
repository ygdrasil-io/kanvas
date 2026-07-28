package org.graphiks.kanvas.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

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
}
