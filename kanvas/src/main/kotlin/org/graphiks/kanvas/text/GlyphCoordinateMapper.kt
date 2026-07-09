package org.graphiks.kanvas.text

import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.font.scaler.GlyphBounds
import org.graphiks.kanvas.font.scaler.GlyphRepresentation
import org.graphiks.kanvas.font.scaler.OutlineCommand
import org.graphiks.kanvas.font.scaler.ScaledGlyph
import org.graphiks.kanvas.types.Rect

sealed interface MappedGlyph {
    data object Empty : MappedGlyph

    data class Drawn(
        val baselineRect: Rect,
        val maskGlyph: ScaledGlyph,
        val outlineCommands: List<OutlineCommand>,
    ) : MappedGlyph
}

object GlyphCoordinateMapper {
    fun map(glyph: ScaledGlyph): MappedGlyph {
        if (glyph.commands.isEmpty()) return MappedGlyph.Empty

        val minX = floor(glyph.bounds.left).toFloat()
        val minY = floor(glyph.bounds.top).toFloat()
        val maxX = ceil(glyph.bounds.right).toFloat()
        val maxY = ceil(glyph.bounds.bottom).toFloat()
        val width = maxX - minX
        val height = maxY - minY
        if (width <= 0f || height <= 0f) return MappedGlyph.Empty

        val outlineCommands = glyph.commands.map { command ->
            command.mapPoints { x, y -> x to -y }
        }
        val maskCommands = glyph.commands.map { command ->
            command.mapPoints { x, y -> (x - minX) to (maxY - y) }
        }

        return MappedGlyph.Drawn(
            baselineRect = Rect.fromLTRB(minX, -maxY, minX + width, -maxY + height),
            maskGlyph = glyph.copy(
                bounds = GlyphBounds(0.0, 0.0, width.toDouble(), height.toDouble()),
                commands = maskCommands,
                representation = GlyphRepresentation.Outline(maskCommands),
            ),
            outlineCommands = outlineCommands,
        )
    }

    private inline fun OutlineCommand.mapPoints(
        transform: (Double, Double) -> Pair<Double, Double>,
    ): OutlineCommand =
        when (this) {
            is OutlineCommand.MoveTo -> {
                val (x, y) = transform(x, y)
                OutlineCommand.MoveTo(x, y)
            }
            is OutlineCommand.LineTo -> {
                val (x, y) = transform(x, y)
                OutlineCommand.LineTo(x, y)
            }
            is OutlineCommand.QuadraticTo -> {
                val (cx, cy) = transform(controlX, controlY)
                val (x, y) = transform(x, y)
                OutlineCommand.QuadraticTo(cx, cy, x, y)
            }
            is OutlineCommand.CubicTo -> {
                val (c1x, c1y) = transform(controlX1, controlY1)
                val (c2x, c2y) = transform(controlX2, controlY2)
                val (x, y) = transform(x, y)
                OutlineCommand.CubicTo(c1x, c1y, c2x, c2y, x, y)
            }
            OutlineCommand.Close -> OutlineCommand.Close
        }
}
