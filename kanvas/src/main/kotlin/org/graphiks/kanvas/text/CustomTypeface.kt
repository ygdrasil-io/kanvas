package org.graphiks.kanvas.text

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.PathVerb
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.types.Color

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
                    output.writeInt(paint.color.packed.toInt())
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
                    color = Color.fromArgbInt(input.readInt()),
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
            val verbs = path.verbs()
            output.writeInt(verbs.size)
            verbs.forEach { output.writeByte(it.ordinal) }
            val points = path.points()
            output.writeInt(points.size)
            points.forEach { point ->
                output.writeFloat(point.x)
                output.writeFloat(point.y)
            }
        }

        private fun readPath(input: DataInputStream): Path {
            val path = Path()
            path.fillType = FillType.entries[input.readInt()]
            val verbs = List(input.readInt()) { PathVerb.entries[input.readUnsignedByte()] }
            val points = List(input.readInt()) { org.graphiks.kanvas.types.Point(input.readFloat(), input.readFloat()) }
            var pointIndex = 0
            verbs.forEach { verb ->
                when (verb) {
                    PathVerb.MOVE -> points[pointIndex++].let { path.moveTo(it.x, it.y) }
                    PathVerb.LINE -> points[pointIndex++].let { path.lineTo(it.x, it.y) }
                    PathVerb.QUAD -> {
                        val control = points[pointIndex++]
                        val end = points[pointIndex++]
                        path.quadTo(control.x, control.y, end.x, end.y)
                    }
                    PathVerb.CUBIC -> {
                        val control1 = points[pointIndex++]
                        val control2 = points[pointIndex++]
                        val end = points[pointIndex++]
                        path.cubicTo(control1.x, control1.y, control2.x, control2.y, end.x, end.y)
                    }
                    PathVerb.ARC_TO -> {
                        val radius = points[pointIndex++]
                        val rotationAndLargeArc = points[pointIndex++]
                        val sweep = points[pointIndex++]
                        val end = points[pointIndex++]
                        path.arcTo(
                            radius.x,
                            radius.y,
                            rotationAndLargeArc.x,
                            rotationAndLargeArc.y > 0f,
                            sweep.x > 0f,
                            end.x,
                            end.y,
                        )
                    }
                    PathVerb.CLOSE -> path.close()
                }
            }
            require(pointIndex == points.size) { "Malformed custom typeface glyph path." }
            return path
        }
    }
}
