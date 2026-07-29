package org.graphiks.kanvas.surface.gpu

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import org.graphiks.kanvas.font.FontSource
import org.graphiks.kanvas.font.FontSourceKind
import org.graphiks.kanvas.font.colr.CPALV0Parser
import org.graphiks.kanvas.font.scaler.TrueTypeLocaFormat
import org.graphiks.kanvas.font.scaler.TrueTypeLocaTableParser
import org.graphiks.kanvas.font.sfnt.DefaultOpenTypeFaceParser
import org.graphiks.kanvas.font.sfnt.OpenTypeBitmapGlyphSource
import org.graphiks.kanvas.glyph.color.COLR_FOREGROUND_PALETTE_INDEX
import org.graphiks.kanvas.glyph.color.COLRLayerRecord
import org.graphiks.kanvas.glyph.color.COLRV0Parser
import org.graphiks.kanvas.glyph.color.COLRV1Parser
import org.graphiks.kanvas.glyph.color.EmojiGlyphRepresentationPriority
import org.graphiks.kanvas.glyph.color.EmojiGlyphRepresentationRoute
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.PreparedTextOutline
import org.graphiks.kanvas.text.Typeface

/** Resolves and snapshots the exact pure-Kotlin font face used by one text operation. */
fun interface GPUPreparedTextFontResolver {
    fun resolve(typeface: Typeface?): GPUPreparedTextFontResolution
}

/** Glyph representation query bound to one already-snapshotted parsed face. */
fun interface GPUPreparedTextGlyphRepresentationResolver {
    fun resolve(
        glyphId: Int,
        fontSize: Float,
        variationCoordinates: Map<String, Float>,
    ): GPUPreparedTextSourceRepresentation
}

/** Transactional font resolution result. */
sealed interface GPUPreparedTextFontResolution {
    @ConsistentCopyVisibility
    data class Ready private constructor(
        val face: GPUPreparedFontFaceSnapshot,
        val glyphCount: Int,
        val representationResolver: GPUPreparedTextGlyphRepresentationResolver,
    ) : GPUPreparedTextFontResolution {
        companion object {
            @JvmSynthetic
            internal fun create(
                face: GPUPreparedFontFaceSnapshot,
                glyphCount: Int,
                representationResolver: GPUPreparedTextGlyphRepresentationResolver,
            ): Ready {
                require(glyphCount > 0) { "Prepared font glyph count must be positive" }
                return Ready(
                    face = GPUPreparedFontFaceSnapshot.create(
                        sourceId = face.sourceId,
                        typefaceId = face.typefaceId,
                        faceIndex = face.faceIndex,
                        bytes = face.bytes,
                        provenance = face.provenance,
                    ),
                    glyphCount = glyphCount,
                    representationResolver = representationResolver,
                )
            }
        }
    }

    @ConsistentCopyVisibility
    data class Refused private constructor(
        val code: String,
        val message: String,
    ) : GPUPreparedTextFontResolution {
        companion object {
            @JvmSynthetic
            internal fun create(code: String, message: String): Refused =
                Refused(code, message)
        }
    }

    companion object {
        /** Creates a ready resolution while defensively snapshotting its public face payload. */
        @JvmSynthetic
        internal fun ready(
            face: GPUPreparedFontFaceSnapshot,
            glyphCount: Int,
            representationResolver: GPUPreparedTextGlyphRepresentationResolver,
        ): Ready = Ready.create(face, glyphCount, representationResolver)

        /**
         * Creates a terminal public font-resolution refusal.
         *
         * Only codes owned by the font-resolution boundary are accepted, so an
         * injectable resolver cannot invent a second diagnostic taxonomy.
         */
        fun refused(code: String, message: String): Refused {
            require(code in CANONICAL_PREPARED_FONT_REFUSAL_CODES) {
                "Prepared font resolver refusal code is not canonical: $code"
            }
            require(message.isNotBlank()) { "Prepared font resolver refusal message must not be blank" }
            return Refused.create(code, message)
        }
    }
}

private val CANONICAL_PREPARED_FONT_REFUSAL_CODES = setOf(
    org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.TYPEFACE_MISSING,
    org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.TYPEFACE_UNSUPPORTED,
    org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_IDENTITY_UNSTABLE,
    org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
)

/**
 * Canonical resolver for exact [FontTypeface] instances.
 *
 * The resolver copies bytes before parsing and never retains a caller-owned
 * byte array. Other Typeface implementations do not expose the source identity
 * and bytes required by prepared GPU text and therefore fail closed.
 */
object GPUPreparedFontTypefaceResolver : GPUPreparedTextFontResolver {
    override fun resolve(typeface: Typeface?): GPUPreparedTextFontResolution {
        if (typeface == null) {
            return refused(
                org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.TYPEFACE_MISSING,
                "Prepared text requires an exact typeface",
            )
        }
        val exact = typeface as? FontTypeface ?: return refused(
            org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.TYPEFACE_UNSUPPORTED,
            "Prepared text requires FontTypeface identity and source bytes",
        )
        val bytes = exact.fontBytes.copyOf()
        if (bytes.isEmpty()) {
            return refused(
                org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                "Prepared font bytes are empty",
            )
        }
        val source = FontSource(
            id = exact.sourceId,
            kind = FontSourceKind.MEMORY,
            displayName = exact.fontName,
            bytes = bytes,
        )
        val parsed = runCatching {
            DefaultOpenTypeFaceParser().parse(source, exact.faceIndex)
        }.getOrElse {
            return refused(
                org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                "Prepared font bytes cannot be parsed",
            )
        }
        val typefaceId = exact.typefaceId
            ?: return refused(
                org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_IDENTITY_UNSTABLE,
                "Parsed typeface identity is unavailable",
            )
        if (typefaceId != parsed.id || parsed.source.id != exact.sourceId) {
            return refused(
                org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_IDENTITY_UNSTABLE,
                "Prepared font parser identity differs from the typeface authority",
            )
        }
        val glyphCount = parsed.metrics.numGlyphs
            ?: return refused(
                org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                "Prepared font has no parsed maxp glyph count",
            )
        if (glyphCount <= 0) {
            return refused(
                org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                "Prepared font glyph count must be positive",
            )
        }
        parsed.diagnostics.firstOrNull { diagnostic ->
            diagnostic.table?.value == "COLR" || diagnostic.table?.value == "CPAL"
        }?.let { diagnostic ->
            return refused(
                org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                "Prepared ${diagnostic.table?.value} table produced a parser diagnostic",
            )
        }

        val rawTables = parsed.rawTables.entries.associate { (tag, values) ->
            tag.value to values.map(Int::toByte).toByteArray()
        }
        val glyfBytes = rawTables["glyf"]
        val locaBytes = rawTables["loca"]
        if ((glyfBytes == null) != (locaBytes == null)) {
            return refused(
                org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                "Prepared TrueType font must provide both loca and glyf tables",
            )
        }
        val trueTypeLoca = if (glyfBytes != null && locaBytes != null) {
            val locaFormat = when (parsed.metrics.indexToLocFormat) {
                0 -> TrueTypeLocaFormat.Short
                1 -> TrueTypeLocaFormat.Long
                else -> return refused(
                    org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                    "Prepared TrueType font has no valid head.indexToLocFormat",
                )
            }
            val loca = runCatching {
                TrueTypeLocaTableParser.parse(
                    data = locaBytes,
                    format = locaFormat,
                    numGlyphs = glyphCount,
                )
            }.getOrElse {
                return refused(
                    org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                    "Prepared TrueType loca table is malformed",
                )
            }
            if (loca.offsets.last() > glyfBytes.size) {
                return refused(
                    org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                    "Prepared TrueType loca table exceeds glyf bounds",
                )
            }
            loca
        } else {
            null
        }

        val colrBytes = rawTables["COLR"]
        val colrVersion = colrBytes?.let { bytesValue ->
            if (bytesValue.size < 2) {
                return refused(
                    org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                    "Prepared COLR table is truncated before its version",
                )
            }
            readPreparedTextU16(bytesValue, 0)
        }
        val colrv0 = when (colrVersion) {
            0 -> runCatching {
                COLRV0Parser.parse(checkNotNull(colrBytes))
            }.getOrNull() ?: return refused(
                    org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                    "Prepared COLRv0 table is malformed",
                )
            1 -> runCatching {
                COLRV0Parser.parseRetainedV0Records(checkNotNull(colrBytes))
            }.getOrNull() ?: return refused(
                org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                "Prepared COLRv1 retained version 0 records are malformed",
            )
            else -> null
        }
        val cpal = if (colrv0 != null) {
            val cpalBytes = rawTables["CPAL"]
                ?: return refused(
                    org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                    "Prepared COLRv0 font has no CPAL table",
                )
            runCatching {
                if (colrVersion == 1) {
                    CPALV0Parser.parseCompatibleV0Prefix(cpalBytes)
                } else {
                    CPALV0Parser.parse(cpalBytes)
                }
            }.getOrNull()
                ?: return refused(
                    org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                    "Prepared COLRv0 CPAL table is malformed",
                )
        } else {
            null
        }
        val colrv0LayerIndex: Map<Int, List<COLRLayerRecord>> =
            if (colrv0 != null && cpal != null) {
            val selectedPalette = cpal.palettes.firstOrNull()
                ?: return refused(
                    org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                    "Prepared COLRv0 CPAL table has no palette zero",
                )
            val mutableIndex = LinkedHashMap<Int, List<COLRLayerRecord>>()
            var previousBaseGlyphId = -1
            for (baseRecord in colrv0.baseGlyphRecords) {
                if (baseRecord.glyphId !in 0 until glyphCount) {
                    return refused(
                        org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                        "Prepared COLRv0 base glyph ${baseRecord.glyphId} is out of range",
                    )
                }
                if (baseRecord.glyphId <= previousBaseGlyphId) {
                    return refused(
                        org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                        "Prepared COLRv0 base glyph ids must be unique and strictly increasing",
                    )
                }
                previousBaseGlyphId = baseRecord.glyphId
                val fromIndex = baseRecord.firstLayerIndex
                val toIndexLong = fromIndex.toLong() + baseRecord.numLayers.toLong()
                if (
                    baseRecord.numLayers <= 0 ||
                    fromIndex < 0 ||
                    toIndexLong > colrv0.layerRecords.size.toLong()
                ) {
                    return refused(
                        org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                        "Prepared COLRv0 base glyph ${baseRecord.glyphId} has an invalid layer slice",
                    )
                }
                val layers = Collections.unmodifiableList(
                    ArrayList(
                        colrv0.layerRecords.subList(
                            fromIndex = fromIndex,
                            toIndex = toIndexLong.toInt(),
                        ),
                    ),
                )
                for (layer in layers) {
                    if (layer.glyphId !in 0 until glyphCount) {
                        return refused(
                            org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                            "Prepared COLRv0 layer glyph ${layer.glyphId} is out of range",
                        )
                    }
                    if (
                        layer.paletteIndex != COLR_FOREGROUND_PALETTE_INDEX &&
                        layer.paletteIndex !in selectedPalette.indices
                    ) {
                        return refused(
                            org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                            "Prepared COLRv0 palette index ${layer.paletteIndex} is unavailable",
                        )
                    }
                }
                mutableIndex[baseRecord.glyphId] = layers
            }
            Collections.unmodifiableMap(mutableIndex)
        } else {
            emptyMap()
        }
        val colrv1 = if (colrVersion != null && colrVersion >= 1) {
            runCatching { COLRV1Parser.parse(checkNotNull(colrBytes)) }.getOrNull()
        } else {
            null
        }
        val malformedColrv1 = colrVersion != null && colrVersion >= 1 && colrv1 == null
        val colrv1PaintIndex = LinkedHashMap<Int, Any>().also { index ->
            colrv1?.baseGlyphPaintRecords?.forEach { record ->
                if (record.glyphId !in 0 until glyphCount || index.put(record.glyphId, record.paint) != null) {
                    return refused(
                        org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                        "Prepared COLRv1 base glyph ids must be unique and inside the parsed face",
                    )
                }
            }
        }.let(Collections::unmodifiableMap)
        val svgGlyphIndex = BooleanArray(glyphCount)
        var previousSvgEnd = -1
        parsed.color.svg?.documents?.forEach { document ->
            if (
                document.startGlyphId !in 0 until glyphCount ||
                document.endGlyphId !in 0 until glyphCount ||
                document.startGlyphId > document.endGlyphId ||
                document.startGlyphId <= previousSvgEnd
            ) {
                return refused(
                    org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                    "Prepared SVG glyph ranges must be sorted, disjoint, and inside the parsed face",
                )
            }
            for (glyphId in document.startGlyphId..document.endGlyphId) {
                svgGlyphIndex[glyphId] = true
            }
            previousSvgEnd = document.endGlyphId
        }
        val bitmapGlyphIndex = Collections.unmodifiableMap(
            LinkedHashMap(parsed.color.bitmap?.glyphs.orEmpty()),
        )
        if (bitmapGlyphIndex.keys.any { glyphId -> glyphId !in 0 until glyphCount }) {
            return refused(
                org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                "Prepared bitmap glyph ids must remain inside the parsed face",
            )
        }
        val malformedOptionalTables = parsed.diagnostics.asSequence()
            .filter { diagnostic ->
                diagnostic.causeCode == "font.sfnt.optional-table-malformed"
            }
            .mapNotNull { diagnostic -> diagnostic.table?.value }
            .toSet()
        val malformedSvg = "SVG " in malformedOptionalTables
        val malformedCbdtCblc =
            "CBDT" in malformedOptionalTables || "CBLC" in malformedOptionalTables
        val malformedSbix = "sbix" in malformedOptionalTables
        val representationMemo =
            ConcurrentHashMap<PreparedTextRepresentationMemoKey, GPUPreparedTextSourceRepresentation>()

        return GPUPreparedTextFontResolution.ready(
            face = GPUPreparedFontFaceSnapshot.create(
                sourceId = exact.sourceId,
                typefaceId = typefaceId,
                faceIndex = exact.faceIndex,
                bytes = bytes.map { it.toInt() and 0xff },
                provenance = "memory:${exact.fontName}",
            ),
            glyphCount = glyphCount,
            representationResolver = GPUPreparedTextGlyphRepresentationResolver {
                    glyphId,
                    fontSize,
                    variations,
                ->
                val memoKey = PreparedTextRepresentationMemoKey.create(
                    glyphId = glyphId,
                    fontSize = fontSize,
                    variationCoordinates = variations,
                )
                representationMemo.computeIfAbsent(memoKey) {
                    val colrv0Layers = colrv0LayerIndex[glyphId].orEmpty()
                    val colrv0Proven = if (colrv0Layers.isNotEmpty()) {
                        val layerOutlines = colrv0Layers.map { layer ->
                            exact.preparedTextOutline(
                                glyphId = layer.glyphId,
                                fontSize = fontSize,
                                variationCoordinates = variations,
                            )
                        }
                        layerOutlines.none { outline -> outline is PreparedTextOutline.Unavailable } &&
                            (
                                glyphId != 0 ||
                                    layerOutlines.any { outline ->
                                        outline is PreparedTextOutline.ProvenNonEmpty
                                    }
                                )
                    } else {
                        false
                    }
                    val colrRepresentation = when {
                        glyphId in colrv1PaintIndex ->
                            GPUPreparedTextSourceRepresentation.COLRV1
                        colrv0Proven ->
                            GPUPreparedTextSourceRepresentation.COLRV0
                        else -> null
                    }
                    val colrv0CandidateUnavailable = colrv0Layers.isNotEmpty() && !colrv0Proven
                    val bitmapGlyph = bitmapGlyphIndex[glyphId]
                    val outlineAvailable = exact.provesPreparedTextOutlineOrEmpty(
                        glyphId = glyphId,
                        fontSize = fontSize,
                        variationCoordinates = variations,
                    )
                    val validRepresentation = when (
                        EmojiGlyphRepresentationPriority.select(
                            colrAvailable = colrRepresentation != null,
                            bitmapAvailable = bitmapGlyph != null,
                            pngAvailable = false,
                            svgAvailable = glyphId in svgGlyphIndex.indices && svgGlyphIndex[glyphId],
                            outlineAvailable = false,
                        )
                    ) {
                        EmojiGlyphRepresentationRoute.COLR -> checkNotNull(colrRepresentation)
                        EmojiGlyphRepresentationRoute.BITMAP -> when (bitmapGlyph?.source) {
                            OpenTypeBitmapGlyphSource.CBDT_CBLC ->
                                GPUPreparedTextSourceRepresentation.CBDT_CBLC
                            OpenTypeBitmapGlyphSource.SBIX ->
                                GPUPreparedTextSourceRepresentation.SBIX
                            null -> null
                        }
                        EmojiGlyphRepresentationRoute.SVG ->
                            GPUPreparedTextSourceRepresentation.SVG
                        EmojiGlyphRepresentationRoute.PNG,
                        EmojiGlyphRepresentationRoute.OUTLINE,
                        null,
                        -> null
                    }
                    validRepresentation ?: when {
                        malformedColrv1 -> GPUPreparedTextSourceRepresentation.COLRV1
                        malformedCbdtCblc -> GPUPreparedTextSourceRepresentation.CBDT_CBLC
                        malformedSbix -> GPUPreparedTextSourceRepresentation.SBIX
                        malformedSvg -> GPUPreparedTextSourceRepresentation.SVG
                        colrv0CandidateUnavailable -> GPUPreparedTextSourceRepresentation.MISSING
                        outlineAvailable -> GPUPreparedTextSourceRepresentation.OUTLINE
                        else -> GPUPreparedTextSourceRepresentation.MISSING
                    }
                }
            },
        )
    }

    private fun refused(code: String, message: String): GPUPreparedTextFontResolution.Refused =
        GPUPreparedTextFontResolution.refused(code, message)
}

private data class PreparedTextRepresentationMemoKey(
    val glyphId: Int,
    val fontSizeBits: Int,
    val canonicalVariationBits: List<Pair<String, Int>>,
) {
    companion object {
        fun create(
            glyphId: Int,
            fontSize: Float,
            variationCoordinates: Map<String, Float>,
        ): PreparedTextRepresentationMemoKey = PreparedTextRepresentationMemoKey(
            glyphId = glyphId,
            fontSizeBits = fontSize.toRawBits(),
            canonicalVariationBits = Collections.unmodifiableList(
                variationCoordinates.entries
                    .sortedBy { entry -> entry.key }
                    .map { entry -> entry.key to entry.value.toRawBits() },
            ),
        )
    }
}

private fun FontTypeface.provesPreparedTextOutlineOrEmpty(
    glyphId: Int,
    fontSize: Float,
    variationCoordinates: Map<String, Float>,
): Boolean = when (
    preparedTextOutline(
        glyphId = glyphId,
        fontSize = fontSize,
        variationCoordinates = variationCoordinates,
    )
) {
    is PreparedTextOutline.ProvenNonEmpty -> true
    PreparedTextOutline.ProvenEmpty -> glyphId != 0
    PreparedTextOutline.Unavailable -> false
}

private fun readPreparedTextU16(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xff) shl 8) or
        (bytes[offset + 1].toInt() and 0xff)
