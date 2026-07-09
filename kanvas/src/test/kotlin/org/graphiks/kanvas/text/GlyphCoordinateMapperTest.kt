package org.graphiks.kanvas.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.font.scaler.GlyphBounds
import org.graphiks.kanvas.font.scaler.GlyphRepresentation
import org.graphiks.kanvas.font.scaler.OutlineCommand
import org.graphiks.kanvas.font.scaler.ScaledGlyph
import org.graphiks.kanvas.types.Rect

class GlyphCoordinateMapperTest {
    @Test
    fun `maps font y-up bounds to canvas y-down baseline rect`() {
        val glyph = fixtureGlyph(
            commands = listOf(
                OutlineCommand.MoveTo(2.0, 0.0),
                OutlineCommand.LineTo(12.0, 0.0),
                OutlineCommand.LineTo(12.0, 20.0),
                OutlineCommand.LineTo(2.0, 20.0),
                OutlineCommand.Close,
            ),
            bounds = GlyphBounds(2.0, 0.0, 12.0, 20.0),
        )

        val mapped = GlyphCoordinateMapper.map(glyph)

        assertTrue(mapped is MappedGlyph.Drawn)
        assertEquals(Rect.fromLTRB(2f, -20f, 12f, 0f), mapped.baselineRect)
    }

    @Test
    fun `produces y-down outline commands relative to baseline`() {
        val glyph = fixtureGlyph(
            commands = listOf(
                OutlineCommand.MoveTo(0.0, 0.0),
                OutlineCommand.LineTo(0.0, 10.0),
                OutlineCommand.QuadraticTo(5.0, 12.0, 10.0, 10.0),
                OutlineCommand.Close,
            ),
            bounds = GlyphBounds(0.0, 0.0, 10.0, 12.0),
        )

        val mapped = GlyphCoordinateMapper.map(glyph) as MappedGlyph.Drawn

        assertEquals(
            listOf(
                OutlineCommand.MoveTo(0.0, -0.0),
                OutlineCommand.LineTo(0.0, -10.0),
                OutlineCommand.QuadraticTo(5.0, -12.0, 10.0, -10.0),
                OutlineCommand.Close,
            ),
            mapped.outlineCommands,
        )
    }

    @Test
    fun `produces mask-local commands with top row at font max y`() {
        val glyph = fixtureGlyph(
            commands = listOf(
                OutlineCommand.MoveTo(2.0, 0.0),
                OutlineCommand.LineTo(12.0, 0.0),
                OutlineCommand.LineTo(12.0, 20.0),
                OutlineCommand.LineTo(2.0, 20.0),
                OutlineCommand.Close,
            ),
            bounds = GlyphBounds(2.0, 0.0, 12.0, 20.0),
        )

        val mapped = GlyphCoordinateMapper.map(glyph) as MappedGlyph.Drawn

        assertEquals(
            listOf(
                OutlineCommand.MoveTo(0.0, 20.0),
                OutlineCommand.LineTo(10.0, 20.0),
                OutlineCommand.LineTo(10.0, 0.0),
                OutlineCommand.LineTo(0.0, 0.0),
                OutlineCommand.Close,
            ),
            mapped.maskGlyph.commands,
        )
        assertEquals(GlyphBounds(0.0, 0.0, 10.0, 20.0), mapped.maskGlyph.bounds)
    }

    @Test
    fun `returns empty for glyph without drawable commands`() {
        val glyph = fixtureGlyph(commands = emptyList(), bounds = GlyphBounds(0.0, 0.0, 0.0, 0.0))

        assertEquals(MappedGlyph.Empty, GlyphCoordinateMapper.map(glyph))
    }

    @Test
    fun `returns empty for glyph with only move and close commands`() {
        val glyph = fixtureGlyph(
            commands = listOf(
                OutlineCommand.MoveTo(2.0, 3.0),
                OutlineCommand.Close,
            ),
            bounds = GlyphBounds(2.0, 3.0, 12.0, 20.0),
        )

        assertEquals(MappedGlyph.Empty, GlyphCoordinateMapper.map(glyph))
    }

    private fun fixtureGlyph(
        commands: List<OutlineCommand>,
        bounds: GlyphBounds,
    ): ScaledGlyph =
        ScaledGlyph(
            sourceCodepoint = 65,
            glyphId = 1,
            size = 20f,
            advanceWidth = 12f,
            bounds = bounds,
            commands = commands,
            representation = GlyphRepresentation.Outline(commands),
        )
}
