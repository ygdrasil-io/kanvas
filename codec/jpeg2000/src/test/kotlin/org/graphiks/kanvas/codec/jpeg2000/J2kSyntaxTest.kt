package org.graphiks.kanvas.codec.jpeg2000

import java.io.ByteArrayOutputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class J2kSyntaxTest {

    @Test
    fun `main header retains two signed components tiled geometry and RLCP coding`() {
        val bytes = mainHeader(
            width = 33,
            height = 17,
            tileWidth = 16,
            tileHeight = 8,
            components = listOf(ComponentInput(12, true, 2, 1), ComponentInput(8, false, 1, 1)),
            progression = 1,
            layers = 3,
            decompositions = 4,
        )
        val header = J2kMainHeaderParser(
            data = bytes,
            start = 0,
            end = bytes.size,
            limits = Jpeg2000Limits(),
        ).parse()

        assertEquals(2, header.geometry.components.size)
        assertEquals(9L, header.geometry.tileGrid.tileCount)
        assertEquals(J2kProgressionOrder.RLCP, header.coding.progression)
        assertEquals(3, header.coding.layers)
    }

    @Test
    fun `main header retains precinct SOP and EPH COD flags`() {
        val bytes = mainHeader(
            width = 1,
            height = 1,
            tileWidth = 16,
            tileHeight = 8,
            components = listOf(ComponentInput(8, false, 1, 1)),
            progression = 0,
            layers = 1,
            decompositions = 1,
            scod = 0x07,
        )
        val header = J2kMainHeaderParser(
            data = bytes,
            start = 0,
            end = bytes.size,
            limits = Jpeg2000Limits(),
        ).parse()

        assertTrue(header.coding.usesSopMarkers)
        assertTrue(header.coding.usesEphMarkers)
    }

    @Test
    fun `main header rejects more than 32 COD decompositions`() {
        val bytes = mainHeader(
            width = 1,
            height = 1,
            tileWidth = 16,
            tileHeight = 8,
            components = listOf(ComponentInput(8, false, 1, 1)),
            progression = 0,
            layers = 1,
            decompositions = 33,
        )

        val failure = assertThrows(Jpeg2000Failure::class.java) {
            J2kMainHeaderParser(bytes, 0, bytes.size, Jpeg2000Limits()).parse()
        }

        assertEquals("jpeg2000.cod.invalid", failure.diagnostic.code)
    }

    @Test
    fun `main header rejects reserved QCD bits for no quantization`() {
        val bytes = mainHeader(
            width = 1,
            height = 1,
            tileWidth = 16,
            tileHeight = 8,
            components = listOf(ComponentInput(8, false, 1, 1)),
            progression = 0,
            layers = 1,
            decompositions = 0,
            qcdEntry = 0x41,
        )

        val failure = assertThrows(Jpeg2000Failure::class.java) {
            J2kMainHeaderParser(bytes, 0, bytes.size, Jpeg2000Limits()).parse()
        }

        assertEquals("jpeg2000.qcd.invalid", failure.diagnostic.code)
    }

    @Test
    fun `main header rejects SIZ coordinates outside the geometry model range`() {
        val bytes = mainHeader(
            width = 1,
            height = 1,
            tileWidth = 16,
            tileHeight = 8,
            components = listOf(ComponentInput(8, false, 1, 1)),
            progression = 0,
            layers = 1,
            decompositions = 0,
        )
        bytes[8] = 0x80.toByte()
        bytes[11] = 1
        bytes[16] = 0x80.toByte()

        val failure = assertThrows(Jpeg2000Failure::class.java) {
            J2kMainHeaderParser(bytes, 0, bytes.size, Jpeg2000Limits()).parse()
        }

        assertEquals("jpeg2000.siz.invalid", failure.diagnostic.code)
    }

    @Test
    fun `syntax records retain signed sampling and tile count`() {
        val component = J2kComponentSpec(precision = 12, signed = true, xSampling = 2, ySampling = 1)
        val grid = J2kTileGrid(0, 0, 33, 17, 0, 0, 16, 8, columns = 3, rows = 3)

        assertEquals(12, component.precision)
        assertTrue(component.signed)
        assertEquals(2, component.xSampling)
        assertEquals(1, component.ySampling)
        assertEquals(9L, grid.tileCount)
    }

    @Test
    fun `quantization style isolates input and exposed arrays`() {
        val inputExponents = intArrayOf(5, 6)
        val inputMantissas = intArrayOf(12, 13)
        val quantization = J2kQuantizationStyle(
            guardBits = 2,
            style = 1,
            reversible = false,
            exponents = inputExponents,
            mantissas = inputMantissas,
        )

        inputExponents[0] = 99
        inputMantissas[0] = 99

        assertArrayEquals(intArrayOf(5, 6), quantization.exponents)
        assertArrayEquals(intArrayOf(12, 13), requireNotNull(quantization.mantissas))

        quantization.exponents[1] = 99
        requireNotNull(quantization.mantissas)[1] = 99

        assertArrayEquals(intArrayOf(5, 6), quantization.exponents)
        assertArrayEquals(intArrayOf(12, 13), requireNotNull(quantization.mantissas))
    }

    @Test
    fun `quantization styles use structural equality`() {
        val first = J2kQuantizationStyle(
            guardBits = 2,
            style = 1,
            reversible = false,
            exponents = intArrayOf(5, 6),
            mantissas = intArrayOf(12, 13),
        )
        val second = J2kQuantizationStyle(
            guardBits = 2,
            style = 1,
            reversible = false,
            exponents = intArrayOf(5, 6),
            mantissas = intArrayOf(12, 13),
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `quantization style copy preserves array isolation`() {
        val original = J2kQuantizationStyle(
            guardBits = 2,
            style = 1,
            reversible = false,
            exponents = intArrayOf(5, 6),
            mantissas = intArrayOf(12, 13),
        )

        val copied = original.copy()
        copied.exponents[0] = 99
        requireNotNull(copied.mantissas)[0] = 99

        assertEquals(original, copied)
        assertArrayEquals(intArrayOf(5, 6), original.exponents)
        assertArrayEquals(intArrayOf(12, 13), requireNotNull(original.mantissas))
        assertArrayEquals(intArrayOf(5, 6), copied.exponents)
        assertArrayEquals(intArrayOf(12, 13), requireNotNull(copied.mantissas))
    }

    @Test
    fun `quantization primary constructor is internal and copyable`() {
        val original = J2kQuantizationStyle(
            2,
            1,
            false,
            J2kQuantizationArrays(intArrayOf(5, 6), intArrayOf(12, 13)),
        )

        val copied = original.copy()

        assertEquals(original, copied)
        assertArrayEquals(intArrayOf(5, 6), copied.exponents)
        assertArrayEquals(intArrayOf(12, 13), requireNotNull(copied.mantissas))
    }

    @Test
    fun `limits reject nonpositive general J2K budgets`() {
        assertThrows(IllegalArgumentException::class.java) { Jpeg2000Limits(maxComponents = 0) }
        assertThrows(IllegalArgumentException::class.java) { Jpeg2000Limits(maxTiles = 0) }
        assertThrows(IllegalArgumentException::class.java) { Jpeg2000Limits(maxTileParts = 0) }
        assertThrows(IllegalArgumentException::class.java) { Jpeg2000Limits(maxCodeblocks = 0) }
    }
}

internal data class ComponentInput(
    val precision: Int,
    val signed: Boolean,
    val xSampling: Int,
    val ySampling: Int,
)

internal fun mainHeader(
    width: Int,
    height: Int,
    tileWidth: Int,
    tileHeight: Int,
    components: List<ComponentInput>,
    progression: Int,
    layers: Int,
    decompositions: Int,
    scod: Int = 0,
    qcdEntry: Int = 0x40,
): ByteArray = ByteArrayOutputStream().also { output ->
    output.writeMarker(0x4F)
    output.writeSegment(0x51, ByteArrayOutputStream().also { siz ->
        siz.writeU16(0)
        siz.writeU32(width); siz.writeU32(height)
        siz.writeU32(0); siz.writeU32(0)
        siz.writeU32(tileWidth); siz.writeU32(tileHeight)
        siz.writeU32(0); siz.writeU32(0)
        siz.writeU16(components.size)
        components.forEach { component ->
            siz.write((component.precision - 1) or if (component.signed) 0x80 else 0)
            siz.write(component.xSampling)
            siz.write(component.ySampling)
        }
    }.toByteArray())
    output.writeSegment(0x52, ByteArrayOutputStream().also { cod ->
        cod.write(scod)
        cod.write(progression)
        cod.writeU16(layers)
        cod.write(0)
        cod.write(decompositions)
        cod.write(4)
        cod.write(4)
        cod.write(0)
        cod.write(1)
        if (scod and 1 != 0) repeat(decompositions + 1) { cod.write(0xFF) }
    }.toByteArray())
    output.writeSegment(0x5C, ByteArray(2 + (3 * decompositions)) { index ->
        if (index == 0) 0x40.toByte() else qcdEntry.toByte()
    })
    output.writeMarker(0x90)
}.toByteArray()

internal fun ByteArrayOutputStream.writeMarker(marker: Int) {
    write(0xFF)
    write(marker)
}

internal fun ByteArrayOutputStream.writeSegment(marker: Int, payload: ByteArray) {
    writeMarker(marker)
    writeU16(payload.size + 2)
    write(payload)
}

internal fun ByteArrayOutputStream.writeU16(value: Int) {
    write(value ushr 8)
    write(value and 0xFF)
}

internal fun ByteArrayOutputStream.writeU32(value: Int) {
    write(value ushr 24)
    write(value ushr 16)
    write(value ushr 8)
    write(value and 0xFF)
}
