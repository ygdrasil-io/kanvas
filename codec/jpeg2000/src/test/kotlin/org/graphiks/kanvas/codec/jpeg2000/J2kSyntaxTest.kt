package org.graphiks.kanvas.codec.jpeg2000

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class J2kSyntaxTest {

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
