package org.graphiks.kanvas.text

import org.graphiks.kanvas.font.FontSource
import org.graphiks.kanvas.font.FontSourceID
import org.graphiks.kanvas.font.FontSourceKind
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
import kotlin.uuid.Uuid

class FontTypeface(
    val fontBytes: ByteArray,
    override val fontName: String = "unknown",
) : Typeface {
    internal val scaler: GlyphScaler? = try {
        GlyphScaler.fromBytes(fontBytes)
    } catch (_: NoClassDefFoundError) {
        null
    } catch (_: ClassNotFoundException) {
        null
    } catch (_: Exception) {
        null
    }

    private data class CffBridge(
        val face: OpenTypeFaceData,
        val scaler: OutlineScaler,
        val unitsPerEm: Float,
    )

    private val parsedFace: OpenTypeFaceData? by lazy {
        runCatching {
            DefaultOpenTypeFaceParser().parse(
                FontSource(
                    id = FontSourceID(Uuid.parse("10000000-0000-0000-0000-000000000001")),
                    kind = FontSourceKind.MEMORY,
                    displayName = fontName,
                    bytes = fontBytes,
                ),
            )
        }.getOrNull()
    }

    /**
     * CFF outlines are parsed by the pure Kotlin CFF scaler instead of the legacy
     * `GlyphScaler`, whose CFF route intentionally reports a refusal.
     */
    private val cffBridge: CffBridge? by lazy {
        parsedFace?.let { face ->
            runCatching {
                val scaler = when {
                    face.rawTables.keys.any { it.value == "CFF " } -> CFFScaler(face)
                    face.rawTables.keys.any { it.value == "CFF2" } -> CFF2Scaler(face)
                    else -> return@runCatching null
                }
                CffBridge(
                    face = face,
                    scaler = scaler,
                    unitsPerEm = (face.metrics.unitsPerEm ?: 1_000).toFloat(),
                )
            }.getOrNull()
        }
    }

    /** Pure Kotlin variable TrueType scaler, activated only for a non-default variation request. */
    private val trueTypeVariationBridge: CffBridge? by lazy {
        parsedFace?.let { face ->
            runCatching {
                if (
                    face.rawTables.keys.none { it.value == "glyf" } ||
                    face.rawTables.keys.none { it.value == "loca" } ||
                    face.rawTables.keys.none { it.value == "gvar" }
                ) {
                    return@runCatching null
                }
                CffBridge(
                    face = face,
                    scaler = TrueTypeGlyfScaler(face),
                    unitsPerEm = (face.metrics.unitsPerEm ?: 1_000).toFloat(),
                )
            }.getOrNull()
        }
    }

    internal val usesCffOutlines: Boolean
        get() = cffBridge != null

    /**
     * Distinguishes a valid CFF glyph with no ink (for example a space) from a
     * parser refusal. Canvas may omit the former when lowering a text run to
     * paths, but must retain the latter for its normal diagnostic route.
     */
    internal fun isCffGlyphWithoutOutline(
        glyphId: Int,
        variationCoordinates: Map<String, Float> = emptyMap(),
    ): Boolean = cffBridge?.let { bridge ->
        runCatching {
            bridge.scaler.outline(glyphId.toUInt(), variationCoordinates.toVariationPosition()).commands.isEmpty()
        }.getOrDefault(false)
    } ?: false

    override val unitsPerEm: Float
        get() = cffBridge?.unitsPerEm ?: scaler?.unitsPerEmInt?.toFloat() ?: 1_000f

    override fun glyphIdForCodepoint(codepoint: Int): Int {
        cffBridge?.let { bridge ->
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
        cffBridge?.let { bridge ->
            // The Type 2 `width` operand is a delta from the CFF private
            // dictionary's nominal/default width.  Until those private values
            // are exposed by the scaler, use OpenType's authoritative `hmtx`
            // metric rather than mistaking that delta for an absolute advance.
            val advance = bridge.face.metrics.horizontalMetrics
                .firstOrNull { it.glyphId == glyphId }
                ?.advanceWidth
            return (advance?.toFloat() ?: fontSize * 0.5f) * fontSize / bridge.unitsPerEm
        }
        if (variationCoordinates.isNotEmpty()) {
            trueTypeVariationBridge?.let { bridge ->
                return try {
                    (
                        bridge.scaler.metrics(glyphId.toUInt(), variationCoordinates.toVariationPosition()).advanceX *
                            fontSize / bridge.unitsPerEm
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
        cffBridge?.let { bridge ->
            return try {
                val outline = bridge.scaler.outline(glyphId.toUInt(), variationCoordinates.toVariationPosition())
                if (outline.commands.isEmpty()) null else outline.commands.toPath(fontSize / bridge.unitsPerEm)
            } catch (_: Exception) {
                null
            }
        }
        if (variationCoordinates.isNotEmpty()) {
            trueTypeVariationBridge?.let { bridge ->
                return try {
                    val outline = bridge.scaler.outline(glyphId.toUInt(), variationCoordinates.toVariationPosition())
                    if (outline.commands.isEmpty()) null else outline.commands.toPath(fontSize / bridge.unitsPerEm)
                } catch (_: Exception) {
                    null
                }
            }
        }
        val s = scaler ?: return null
        val result = s.scaleGlyphOrDiagnostic(glyphId, fontSize)
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
