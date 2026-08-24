package org.graphiks.kanvas.text

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.PathCommand
import org.graphiks.kanvas.geometry.PathVerb
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.math.color.ColorARGB

/** Supplies a glyph-local paint, as Skia's drawable custom glyphs do. */
interface GlyphPaintProvider {
    fun paintForGlyph(glyphId: Int): Paint?
}

/**
 * A serializable path-backed typeface for applications that provide their own glyph outlines.
 *
 * Glyph paths and metrics are expressed in em units.  A [Font] scales them to its requested
 * size.  Drawable glyphs retain a solid fill paint across serialization, matching the useful
 * subset of Skia's `SkCustomTypeface` used by `gm/userfont.cpp`.
 */
class CustomTypeface private constructor(
    override val fontName: String,
    private val metrics: FontMetrics,
    private val glyphs: Map<Int, Glyph>,
) : Typeface, FontMetricsProvider, GlyphPaintProvider {
    private data class Glyph(
        val advance: Float,
        val path: Path,
        val drawablePaint: Paint?,
    )

    override val unitsPerEm: Float = 1f

    override fun glyphIdForCodepoint(codepoint: Int): Int = if (glyphs.containsKey(codepoint)) codepoint else 0

    override fun getAdvance(glyphId: Int, fontSize: Float): Float =
        (glyphs[glyphId]?.advance ?: 0f) * fontSize

    override fun getGlyphPath(glyphId: Int, fontSize: Float): Path? =
        glyphs[glyphId]?.path?.transform(0f, 0f, fontSize, fontSize)

    override fun getMetrics(size: Float): FontMetrics = FontMetrics(
        ascent = metrics.ascent * size,
        descent = metrics.descent * size,
        leading = metrics.leading * size,
        xHeight = metrics.xHeight * size,
        capHeight = metrics.capHeight * size,
    )

    override fun paintForGlyph(glyphId: Int): Paint? = glyphs[glyphId]?.drawablePaint

    fun serialize(): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(SERIALIZATION_MAGIC)
            output.writeUTF(fontName)
            output.writeFloat(metrics.ascent)
            output.writeFloat(metrics.descent)
            output.writeFloat(metrics.leading)
            output.writeFloat(metrics.xHeight)
            output.writeFloat(metrics.capHeight)
            val orderedGlyphs = glyphs.toSortedMap()
            output.writeInt(orderedGlyphs.size)
            orderedGlyphs.forEach { (glyphId, glyph) ->
                output.writeInt(glyphId)
                output.writeFloat(glyph.advance)
                output.writeBoolean(glyph.drawablePaint != null)
                glyph.drawablePaint?.let { paint ->
                    output.writeInt(paint.color.value.toInt())
                    output.writeBoolean(paint.antiAlias)
                }
                writePath(output, glyph.path)
            }
        }
        bytes.toByteArray()
    }

    class Builder(private val fontName: String = "custom-typeface") {
        private var metrics = FontMetrics(ascent = 0.8f, descent = -0.2f, leading = 0f)
        private val glyphs = linkedMapOf<Int, Glyph>()

        fun setMetrics(metrics: FontMetrics): Builder = apply {
            this.metrics = metrics
        }

        fun setGlyph(glyphId: Int, advance: Float, path: Path): Builder = apply {
            setGlyphInternal(glyphId, advance, path, null)
        }

        fun setDrawableGlyph(glyphId: Int, advance: Float, path: Path, paint: Paint): Builder = apply {
            require(paint.shader == null && paint.colorFilter == null && paint.maskFilter == null &&
                paint.pathEffect == null && paint.imageFilter == null && paint.blender == null &&
                paint.style == PaintStyle.FILL) {
                "Custom drawable glyphs support only solid fill Paint instances."
            }
            setGlyphInternal(glyphId, advance, path, paint)
        }

        private fun setGlyphInternal(glyphId: Int, advance: Float, path: Path, paint: Paint?) {
            require(glyphId >= 0) { "Glyph id must be non-negative." }
            require(advance.isFinite() && advance >= 0f) { "Glyph advance must be finite and non-negative." }
            glyphs[glyphId] = Glyph(advance, Path().addPath(path), paint)
        }

        fun build(): CustomTypeface = CustomTypeface(
            fontName = fontName,
            metrics = metrics,
            glyphs = glyphs.mapValues { (_, glyph) ->
                glyph.copy(path = Path().addPath(glyph.path))
            },
        )
    }

    companion object {
        private const val SERIALIZATION_MAGIC = 0x4B544631 // "KTF1"

        fun deserialize(bytes: ByteArray): CustomTypeface = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == SERIALIZATION_MAGIC) { "Unsupported custom typeface serialization." }
            val builder = Builder(input.readUTF())
            builder.setMetrics(
                FontMetrics(
                    ascent = input.readFloat(),
                    descent = input.readFloat(),
                    leading = input.readFloat(),
                    xHeight = input.readFloat(),
                    capHeight = input.readFloat(),
                ),
            )
            repeat(input.readInt()) {
                val glyphId = input.readInt()
                val advance = input.readFloat()
                val hasPaint = input.readBoolean()
                val paint = if (hasPaint) Paint(
                    color = ColorARGB.fromPackedInt(input.readInt()),
                    antiAlias = input.readBoolean(),
                ) else {
                    null
                }
                val path = readPath(input)
                if (paint == null) builder.setGlyph(glyphId, advance, path)
                else builder.setDrawableGlyph(glyphId, advance, path, paint)
            }
            builder.build()
        }

        private fun writePath(output: DataOutputStream, path: Path) {
            output.writeInt(path.fillType.ordinal)
            val commands = path.commands()
            output.writeInt(commands.size)
            commands.forEach { output.writeByte(it.verb.ordinal) }
            output.writeInt(commands.sumOf { it.serializedPairCount })
            fun writePair(x: Float, y: Float) {
                output.writeFloat(x)
                output.writeFloat(y)
            }
            commands.forEach { command ->
                when (command) {
                    is PathCommand.Move -> writePair(command.point.x, command.point.y)
                    is PathCommand.Line -> writePair(command.endpoint.x, command.endpoint.y)
                    is PathCommand.Quad -> {
                        writePair(command.control.x, command.control.y)
                        writePair(command.endpoint.x, command.endpoint.y)
                    }
                    is PathCommand.Cubic -> {
                        writePair(command.control1.x, command.control1.y)
                        writePair(command.control2.x, command.control2.y)
                        writePair(command.endpoint.x, command.endpoint.y)
                    }
                    is PathCommand.ArcTo -> {
                        writePair(command.radius.x, command.radius.y)
                        writePair(command.xAxisRotation, if (command.largeArc) 1f else 0f)
                        writePair(if (command.sweep) 1f else 0f, 0f)
                        writePair(command.endpoint.x, command.endpoint.y)
                    }
                    PathCommand.Close -> Unit
                }
            }
        }

        private fun readPath(input: DataInputStream): Path {
            val path = Path()
            path.fillType = FillType.entries[input.readInt()]
            val verbs = List(input.readInt()) { PathVerb.entries[input.readUnsignedByte()] }
            val values = FloatArray(input.readInt() * 2) { input.readFloat() }
            var pointIndex = 0
            fun nextPair(): Pair<Float, Float> {
                require(pointIndex + 1 < values.size) { "Malformed custom typeface glyph path." }
                val pair = values[pointIndex] to values[pointIndex + 1]
                pointIndex += 2
                return pair
            }
            verbs.forEach { verb ->
                when (verb) {
                    PathVerb.MOVE -> nextPair().let { (x, y) -> path.moveTo(x, y) }
                    PathVerb.LINE -> nextPair().let { (x, y) -> path.lineTo(x, y) }
                    PathVerb.QUAD -> {
                        val (controlX, controlY) = nextPair()
                        val (endX, endY) = nextPair()
                        path.quadTo(controlX, controlY, endX, endY)
                    }
                    PathVerb.CUBIC -> {
                        val (control1X, control1Y) = nextPair()
                        val (control2X, control2Y) = nextPair()
                        val (endX, endY) = nextPair()
                        path.cubicTo(control1X, control1Y, control2X, control2Y, endX, endY)
                    }
                    PathVerb.ARC_TO -> {
                        val (radiusX, radiusY) = nextPair()
                        val (rotation, largeArcValue) = nextPair()
                        val (sweepValue, _) = nextPair()
                        val (endX, endY) = nextPair()
                        path.arcTo(
                            radiusX,
                            radiusY,
                            rotation,
                            largeArcValue > 0f,
                            sweepValue > 0f,
                            endX,
                            endY,
                        )
                    }
                    PathVerb.CLOSE -> path.close()
                }
            }
            require(pointIndex == values.size) { "Malformed custom typeface glyph path." }
            return path
        }

        private val PathCommand.serializedPairCount: Int
            get() = when (this) {
                is PathCommand.Move, is PathCommand.Line -> 1
                is PathCommand.Quad -> 2
                is PathCommand.Cubic -> 3
                is PathCommand.ArcTo -> 4
                PathCommand.Close -> 0
            }
    }
}
