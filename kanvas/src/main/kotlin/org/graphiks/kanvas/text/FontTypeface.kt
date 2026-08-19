package org.graphiks.kanvas.text

import org.graphiks.kanvas.font.FontIdentityAuthority
import org.graphiks.kanvas.font.FontSource
import org.graphiks.kanvas.font.FontSourceID
import org.graphiks.kanvas.font.FontSourceKind
import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.font.scaler.CFF2Scaler
import org.graphiks.kanvas.font.scaler.CFFScaler
import org.graphiks.kanvas.font.scaler.GlyphScaleResult
import org.graphiks.kanvas.font.scaler.GlyphScaler
import org.graphiks.kanvas.font.scaler.OutlineScaler
import org.graphiks.kanvas.font.scaler.OutlineCommand
import org.graphiks.kanvas.font.scaler.TrueTypeGlyfScaler
import org.graphiks.kanvas.font.scaler.VariationPosition
import org.graphiks.kanvas.font.sfnt.DefaultOpenTypeFaceParser
import org.graphiks.kanvas.font.sfnt.OpenTypeFaceData
import org.graphiks.kanvas.geometry.Path

internal sealed interface PreparedTextOutline {
    data class ProvenNonEmpty(val path: Path) : PreparedTextOutline
    data object ProvenEmpty : PreparedTextOutline
    data object Unavailable : PreparedTextOutline
}

class FontTypeface(
    fontBytes: ByteArray,
    override val fontName: String = "unknown",
    val faceIndex: Int = 0,
) : Typeface {
    private val fontBytesSnapshot: ByteArray = fontBytes.copyOf()

    val fontBytes: ByteArray
        get() = fontBytesSnapshot.copyOf()

    val sourceId: FontSourceID =
        FontIdentityAuthority.memorySource(fontBytesSnapshot, fontName).sourceId()

    val typefaceId: TypefaceID?
        get() = parsedFace?.id

    init {
        require(faceIndex >= 0) { "faceIndex must be non-negative." }
    }

    /**
     * Legacy scaler retained for historical consumers. Prepared text must use
     * [preparedTextOutline], whose parsed-face authority includes [faceIndex].
     */
    internal val scaler: GlyphScaler? = try {
        GlyphScaler.fromBytes(fontBytesSnapshot)
    } catch (_: NoClassDefFoundError) {
        null
    } catch (_: ClassNotFoundException) {
        null
    } catch (_: Exception) {
        null
    }

    private data class ExactOutlineBridge(
        val face: OpenTypeFaceData,
        val scaler: OutlineScaler,
        val unitsPerEm: Float,
        val cff: Boolean,
        val variableTrueType: Boolean,
    )

    private val parsedFace: OpenTypeFaceData? by lazy {
        runCatching {
            DefaultOpenTypeFaceParser().parse(
                FontSource(
                    id = sourceId,
                    kind = FontSourceKind.MEMORY,
                    displayName = fontName,
                    bytes = fontBytesSnapshot,
                ),
                faceIndex = faceIndex,
            )
        }.getOrNull()
    }

    /** Exact face-indexed outline authority shared by prepared text and Canvas. */
    private val exactOutlineBridge: ExactOutlineBridge? by lazy {
        parsedFace?.let { face ->
            runCatching {
                val (scaler, cff) = when {
                    face.rawTables.keys.any { it.value == "CFF " } -> CFFScaler(face) to true
                    face.rawTables.keys.any { it.value == "CFF2" } -> CFF2Scaler(face) to true
                    face.rawTables.keys.any { it.value == "glyf" } &&
                        face.rawTables.keys.any { it.value == "loca" } ->
                        TrueTypeGlyfScaler(face) to false
                    else -> return@runCatching null
                }
                ExactOutlineBridge(
                    face = face,
                    scaler = scaler,
                    unitsPerEm = (face.metrics.unitsPerEm ?: 1_000).toFloat(),
                    cff = cff,
                    variableTrueType = !cff && face.rawTables.keys.any { it.value == "gvar" },
                )
            }.getOrNull()
        }
    }

    internal val usesCffOutlines: Boolean
        get() = exactOutlineBridge?.cff == true

    /**
     * Classifies one glyph with the exact parsed collection face and variation position.
     *
     * This is the sole outline proof used by prepared text. A parser/scaler
     * failure remains [PreparedTextOutline.Unavailable], never an empty glyph.
     */
    internal fun preparedTextOutline(
        glyphId: Int,
        fontSize: Float,
        variationCoordinates: Map<String, Float> = emptyMap(),
    ): PreparedTextOutline {
        if (glyphId < 0 || !fontSize.isFinite() || fontSize < 0f) {
            return PreparedTextOutline.Unavailable
        }
        val bridge = exactOutlineBridge ?: return PreparedTextOutline.Unavailable
        val outline = runCatching {
            bridge.scaler.outline(
                glyphId.toUInt(),
                variationCoordinates.toVariationPosition(),
            )
        }.getOrElse {
            return PreparedTextOutline.Unavailable
        }
        if (outline.commands.isEmpty()) return PreparedTextOutline.ProvenEmpty
        return PreparedTextOutline.ProvenNonEmpty(
            outline.commands.toPath(fontSize / bridge.unitsPerEm),
        )
    }

    override val unitsPerEm: Float
        get() = exactOutlineBridge
            ?.takeIf { bridge -> bridge.cff }
            ?.unitsPerEm
            ?: scaler?.unitsPerEmInt?.toFloat()
            ?: 1_000f

    override fun glyphIdForCodepoint(codepoint: Int): Int {
        exactOutlineBridge?.takeIf { bridge -> bridge.cff }?.let { bridge ->
            return try {
                bridge.face.cmap.lookupGlyphId(codepoint) ?: 0
            } catch (_: Exception) {
                0
            }
        }
        return try {
            scaler?.glyphIdForCodepoint(codepoint) ?: 0
        } catch (_: Exception) {
            0
        }
    }

    override fun getAdvance(glyphId: Int, fontSize: Float): Float {
        return getAdvance(glyphId, fontSize, emptyMap())
    }

    override fun getAdvance(
        glyphId: Int,
        fontSize: Float,
        variationCoordinates: Map<String, Float>,
    ): Float {
        exactOutlineBridge?.takeIf { bridge -> bridge.cff }?.let { bridge ->
            // Type 2 width operands are deltas; hmtx remains the exact advance authority.
            val advance = bridge.face.metrics.horizontalMetrics
                .firstOrNull { metric -> metric.glyphId == glyphId }
                ?.advanceWidth
            return (advance?.toFloat() ?: fontSize * 0.5f) * fontSize / bridge.unitsPerEm
        }
        if (variationCoordinates.isNotEmpty()) {
            exactOutlineBridge
                ?.takeIf { bridge -> bridge.variableTrueType }
                ?.let { bridge ->
                    return try {
                        (
                            bridge.scaler.metrics(
                                glyphId.toUInt(),
                                variationCoordinates.toVariationPosition(),
                            ).advanceX * fontSize / bridge.unitsPerEm
                            ).toFloat()
                    } catch (_: Exception) {
                        fontSize * 0.5f
                    }
                }
        }
        return try {
            scaler?.scaleGlyph(glyphId, fontSize)?.advanceWidth ?: (fontSize * 0.5f)
        } catch (_: Exception) {
            fontSize * 0.5f
        }
    }

    override fun getGlyphPath(glyphId: Int, fontSize: Float): Path? {
        return getGlyphPath(glyphId, fontSize, emptyMap())
    }

    override fun getGlyphPath(
        glyphId: Int,
        fontSize: Float,
        variationCoordinates: Map<String, Float>,
    ): Path? {
        exactOutlineBridge?.takeIf { bridge -> bridge.cff }?.let { bridge ->
            return try {
                val outline = bridge.scaler.outline(
                    glyphId.toUInt(),
                    variationCoordinates.toVariationPosition(),
                )
                if (outline.commands.isEmpty()) null else outline.commands.toPath(fontSize / bridge.unitsPerEm)
            } catch (_: Exception) {
                null
            }
        }
        if (variationCoordinates.isNotEmpty()) {
            exactOutlineBridge
                ?.takeIf { bridge -> bridge.variableTrueType }
                ?.let { bridge ->
                    return try {
                        val outline = bridge.scaler.outline(
                            glyphId.toUInt(),
                            variationCoordinates.toVariationPosition(),
                        )
                        if (outline.commands.isEmpty()) {
                            null
                        } else {
                            outline.commands.toPath(fontSize / bridge.unitsPerEm)
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
        }
        val legacyScaler = scaler ?: return null
        val result = legacyScaler.scaleGlyphOrDiagnostic(glyphId, fontSize)
        if (result !is GlyphScaleResult.Success) return null
        val scaled = result.glyph
        if (scaled.commands.isEmpty()) return null
        return scaled.commands.toPath()
    }
}

private fun Map<String, Float>.toVariationPosition(): VariationPosition =
    VariationPosition(mapValues { (_, value) -> value.toDouble() })

private fun List<OutlineCommand>.toPath(scale: Float = 1f): Path = Path {
    for (cmd in this@toPath) {
        when (cmd) {
            is OutlineCommand.MoveTo -> moveTo(cmd.x.toFloat() * scale, cmd.y.toFloat() * scale)
            is OutlineCommand.LineTo -> lineTo(cmd.x.toFloat() * scale, cmd.y.toFloat() * scale)
            is OutlineCommand.QuadraticTo -> quadTo(
                cmd.controlX.toFloat() * scale,
                cmd.controlY.toFloat() * scale,
                cmd.x.toFloat() * scale,
                cmd.y.toFloat() * scale,
            )
            is OutlineCommand.CubicTo -> cubicTo(
                cmd.controlX1.toFloat() * scale,
                cmd.controlY1.toFloat() * scale,
                cmd.controlX2.toFloat() * scale,
                cmd.controlY2.toFloat() * scale,
                cmd.x.toFloat() * scale,
                cmd.y.toFloat() * scale,
            )
            is OutlineCommand.Close -> close()
        }
    }
}
