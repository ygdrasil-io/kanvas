package org.graphiks.kanvas.text

import org.graphiks.kanvas.font.scaler.GlyphScaleResult
import org.graphiks.kanvas.font.scaler.GlyphScaler
import org.graphiks.kanvas.font.scaler.OutlineCommand
import org.graphiks.kanvas.geometry.Path

class FontTypeface(
    private val fontBytes: ByteArray,
    override val fontName: String = "unknown",
) : Typeface {
    private val scaler: GlyphScaler? = try {
        GlyphScaler.fromBytes(fontBytes)
    } catch (_: NoClassDefFoundError) {
        null
    } catch (_: ClassNotFoundException) {
        null
    } catch (_: Exception) {
        null
    }

    override fun glyphIdForCodepoint(codepoint: Int): Int {
        return try {
            scaler?.glyphIdForCodepoint(codepoint) ?: 0
        } catch (_: Exception) {
            0
        }
    }

    override fun getAdvance(glyphId: Int, fontSize: Float): Float {
        return try {
            scaler?.scaleGlyph(glyphId, fontSize)?.advanceWidth ?: (fontSize * 0.5f)
        } catch (_: Exception) {
            fontSize * 0.5f
        }
    }

    override fun getGlyphPath(glyphId: Int, fontSize: Float): Path? {
        val s = scaler ?: return null
        val result = s.scaleGlyphOrDiagnostic(glyphId, fontSize)
        if (result !is GlyphScaleResult.Success) return null
        val scaled = result.glyph
        if (scaled.commands.isEmpty()) return null
        return Path {
            for (cmd in scaled.commands) {
                when (cmd) {
                    is OutlineCommand.MoveTo -> moveTo(cmd.x.toFloat(), cmd.y.toFloat())
                    is OutlineCommand.LineTo -> lineTo(cmd.x.toFloat(), cmd.y.toFloat())
                    is OutlineCommand.QuadraticTo -> quadTo(cmd.controlX.toFloat(), cmd.controlY.toFloat(), cmd.x.toFloat(), cmd.y.toFloat())
                    is OutlineCommand.CubicTo -> cubicTo(
                        cmd.controlX1.toFloat(), cmd.controlY1.toFloat(),
                        cmd.controlX2.toFloat(), cmd.controlY2.toFloat(),
                        cmd.x.toFloat(), cmd.y.toFloat(),
                    )
                    is OutlineCommand.Close -> close()
                }
            }
        }
    }
}
