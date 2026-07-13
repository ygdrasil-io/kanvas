package org.graphiks.kanvas.codec.jpeg2000

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
        assertEquals(9L, grid.tileCount)
    }

    @Test
    fun `limits reject nonpositive general J2K budgets`() {
        assertThrows(IllegalArgumentException::class.java) { Jpeg2000Limits(maxComponents = 0) }
        assertThrows(IllegalArgumentException::class.java) { Jpeg2000Limits(maxTiles = 0) }
        assertThrows(IllegalArgumentException::class.java) { Jpeg2000Limits(maxTileParts = 0) }
        assertThrows(IllegalArgumentException::class.java) { Jpeg2000Limits(maxCodeblocks = 0) }
    }
}
