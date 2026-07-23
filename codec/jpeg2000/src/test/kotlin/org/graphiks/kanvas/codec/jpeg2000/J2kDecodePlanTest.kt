package org.graphiks.kanvas.codec.jpeg2000

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class J2kDecodePlanTest {

    @Test
    fun `decode plan orders tile parts and rejects a missing part`() {
        val ordered = J2kDecodePlan.create(
            syntax(
                tileParts = listOf(tilePart(1, 1, 2), tilePart(0, 0, 1), tilePart(1, 0, 2)),
                columns = 2,
            ),
            Jpeg2000Limits(),
        )

        assertEquals(2, ordered.tilePartsByTile.size)
        assertEquals(listOf(0, 1), ordered.tilePartsByTile[1].map(J2kTilePart::partIndex))

        assertThrows(Jpeg2000Failure::class.java) {
            J2kDecodePlan.create(syntax(tileParts = listOf(tilePart(0, 1, 2))), Jpeg2000Limits())
        }
    }

    @Test
    fun `decode plan rejects an Isot outside the declared tile grid`() {
        val failure = assertThrows(Jpeg2000Failure::class.java) {
            J2kDecodePlan.create(
                syntax(tileParts = listOf(tilePart(1, 0, 1))),
                Jpeg2000Limits(),
            )
        }

        assertEquals("jpeg2000.sot.sequence.invalid", failure.diagnostic.code)
    }

    @Test
    fun `decode plan rejects a declared grid tile without a complete sequence`() {
        val failure = assertThrows(Jpeg2000Failure::class.java) {
            J2kDecodePlan.create(
                syntax(tileParts = listOf(tilePart(0, 0, 1)), columns = 2),
                Jpeg2000Limits(),
            )
        }

        assertEquals("jpeg2000.sot.sequence.invalid", failure.diagnostic.code)
    }

    @Test
    fun `decode plan rejects an upper codeblock bound above its budget`() {
        val failure = assertThrows(Jpeg2000Failure::class.java) {
            J2kDecodePlan.create(
                syntax(tileParts = listOf(tilePart(0, 0, 1))),
                Jpeg2000Limits(maxCodeblocks = 1),
            )
        }

        assertEquals("jpeg2000.limit.codeblocks", failure.diagnostic.code)
    }

    @Test
    fun `decode plan rejects mixed declared and unknown tile part counts`() {
        val failure = assertThrows(Jpeg2000Failure::class.java) {
            J2kDecodePlan.create(
                syntax(tileParts = listOf(tilePart(0, 0, 0), tilePart(0, 1, 2))),
                Jpeg2000Limits(),
            )
        }

        assertEquals("jpeg2000.sot.sequence.invalid", failure.diagnostic.code)
    }

    @Test
    fun `decode plan orders unknown tile part counts without duplicates`() {
        val plan = J2kDecodePlan.create(
            syntax(tileParts = listOf(tilePart(0, 1, 0), tilePart(0, 0, 0))),
            Jpeg2000Limits(),
        )

        assertEquals(listOf(0, 1), plan.tilePartsByTile[0].map(J2kTilePart::partIndex))
    }

    @Test
    fun `decode plan rejects unknown tile part sequence containing TPsot 255`() {
        val failure = assertThrows(Jpeg2000Failure::class.java) {
            J2kDecodePlan.create(
                syntax(tileParts = (0..255).map { partIndex -> tilePart(0, partIndex, 0) }),
                Jpeg2000Limits(),
            )
        }

        assertEquals("jpeg2000.sot.sequence.invalid", failure.diagnostic.code)
    }

    private fun tilePart(tileIndex: Int, partIndex: Int, partCount: Int): J2kTilePart = J2kTilePart(
        tileIndex = tileIndex,
        partIndex = partIndex,
        partCount = partCount,
        headerOffset = 0,
        dataOffset = 0,
        dataLength = 1,
    )

    private fun syntax(tileParts: List<J2kTilePart>, columns: Int = 1): J2kSyntaxModel = J2kSyntaxModel(
        mainHeader = J2kMainHeader(
            geometry = J2kGeometryModel(
                frame = Jpeg2000FrameInfo(width = 32 * columns, height = 32, components = 1, precision = 8),
                components = listOf(J2kComponentSpec(precision = 8, signed = false, xSampling = 1, ySampling = 1)),
                tileGrid = J2kTileGrid(0, 0, 32 * columns, 32, 0, 0, 32, 32, columns = columns, rows = 1),
            ),
            coding = J2kCodingStyle(
                progression = J2kProgressionOrder.LRCP,
                layers = 1,
                multiComponentTransform = 0,
                usesSopMarkers = false,
                usesEphMarkers = false,
                decompositions = 1,
                codeBlockWidth = 3,
                codeBlockHeight = 3,
                style = 0,
                transform = 1,
                precinctExponents = listOf(15 to 15, 15 to 15),
            ),
            quantization = J2kQuantizationStyle(
                guardBits = 2,
                style = 0,
                reversible = true,
                exponents = intArrayOf(8, 9, 9, 10),
                mantissas = null,
            ),
            nextMarkerOffset = 0,
        ),
        tileParts = tileParts,
    )
}
