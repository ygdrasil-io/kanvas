package org.graphiks.kanvas.surface.gpu

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.font.scaler.TrueTypeLocaFormat
import org.graphiks.kanvas.font.scaler.TrueTypeLocaTableParser
import org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.PreparedTextOutline

class GPUPreparedTextFontResolverTest {
    @Test
    fun `prepared text authorities cannot be forged through public JVM constructors or copy methods`() {
        val authorityClasses = listOf(
            GPUPreparedFontFaceSnapshot::class.java,
            GPUPreparedTextRepresentationPolicy::class.java,
            GPUPreparedGlyphInput::class.java,
            GPUPreparedTextDraw::class.java,
            GPUPreparedTextLowering.Ready::class.java,
            GPUPreparedTextLowering.Refused::class.java,
            GPUPreparedTextFontResolution.Ready::class.java,
            GPUPreparedTextFontResolution.Refused::class.java,
        )

        authorityClasses.forEach { authority ->
            assertTrue(
                authority.declaredConstructors.none { constructor ->
                    Modifier.isPublic(constructor.modifiers) && !constructor.isSynthetic
                },
                "${authority.name} exposes a public non-synthetic JVM constructor",
            )
            assertTrue(
                authority.declaredMethods.none { method ->
                    method.name.startsWith("copy") && Modifier.isPublic(method.modifiers)
                },
                "${authority.name} exposes a public JVM copy method",
            )
        }
    }

    @Test
    fun `public refusal factory accepts canonical font codes and rejects arbitrary codes`() {
        assertIs<GPUPreparedTextFontResolution.Refused>(
            GPUPreparedTextFontResolution.refused(
                GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                "malformed fixture",
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedTextFontResolution.refused(
                "unsupported.text.caller_forged",
                "forged fixture",
            )
        }
    }

    @Test
    fun `public resolver seam delegates opaque ready values without exposing a ready factory`() {
        val source = java.io.File(
            "src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextFontResolver.kt",
        ).readText()
        assertTrue("data class Ready private constructor" in source)
        assertTrue("internal fun ready(" in source)
        val delegate = GPUPreparedTextFontResolver { typeface ->
            GPUPreparedFontTypefaceResolver.resolve(typeface)
        }

        assertIs<GPUPreparedTextFontResolution.Ready>(
            delegate.resolve(liberationTypeface()),
        )
    }

    @Test
    fun `equal loca offsets prove a real empty TrueType glyph`() {
        val typeface = liberationTypeface()
        val glyphId = typeface.glyphIdForCodepoint(' '.code)
        val range = locaRange(typeface.fontBytes, glyphId)

        assertEquals(range.first, range.last)
        assertEquals(
            GPUPreparedTextSourceRepresentation.OUTLINE,
            representation(typeface, glyphId),
        )
    }

    @Test
    fun `empty loca range never proves visible notdef glyph zero`() {
        val bytes = liberationTypeface().fontBytes
        val firstOffset = readLocaOffset(bytes, glyphId = 0)
        writeLocaOffset(bytes, glyphId = 1, value = firstOffset)

        assertEquals(
            GPUPreparedTextSourceRepresentation.MISSING,
            representation(FontTypeface(bytes, "empty-notdef"), glyphId = 0),
        )
    }

    @Test
    fun `TTC face one outline proof never consults face zero`() {
        val liberation = liberationTypeface()
        val glyphId = liberation.glyphIdForCodepoint('A'.code)
        val collection = ttcFont(
            colrFontBytes(),
            liberation.fontBytes,
        )

        assertEquals(
            GPUPreparedTextSourceRepresentation.OUTLINE,
            representation(
                FontTypeface(collection, "task4-two-face-ttc", faceIndex = 1),
                glyphId = glyphId,
            ),
        )
    }

    @Test
    fun `malformed non-empty TrueType glyph never falls back to blanket outline support`() {
        val original = liberationTypeface()
        val glyphId = original.glyphIdForCodepoint('A'.code)
        val bytes = original.fontBytes
        val range = locaRange(bytes, glyphId)
        assertTrue(range.last > range.first)
        bytes[table(bytes, "glyf").dataOffset + range.first] = 0x7f
        bytes[table(bytes, "glyf").dataOffset + range.first + 1] = 0xff.toByte()

        assertEquals(
            GPUPreparedTextSourceRepresentation.MISSING,
            representation(FontTypeface(bytes, "malformed-glyf"), glyphId),
        )
    }

    @Test
    fun `malformed loca table refuses the whole font resolution`() {
        val bytes = liberationTypeface().fontBytes
        val loca = table(bytes, "loca")
        val head = table(bytes, "head")
        val format = readU16(bytes, head.dataOffset + 50)
        if (format == 0) {
            writeU16(bytes, loca.dataOffset, 0xffff)
        } else {
            writeU32(bytes, loca.dataOffset, 0x7fff_ffff)
        }

        val refused = assertIs<GPUPreparedTextFontResolution.Refused>(
            GPUPreparedFontTypefaceResolver.resolve(FontTypeface(bytes, "malformed-loca")),
        )
        assertEquals(GPUTextRefusalCodes.FONT_BYTES_MALFORMED, refused.code)
    }

    @Test
    fun `malformed COLRv0 table never falls back to an outline`() {
        val bytes = colrFontBytes()
        setTableLength(bytes, "COLR", 2)

        val refused = assertIs<GPUPreparedTextFontResolution.Refused>(
            GPUPreparedFontTypefaceResolver.resolve(FontTypeface(bytes, "malformed-colrv0")),
        )
        assertEquals(GPUTextRefusalCodes.FONT_BYTES_MALFORMED, refused.code)
    }

    @Test
    fun `COLRv0 requires a valid CPAL table`() {
        val bytes = colrFontBytes()
        setTableLength(bytes, "CPAL", 2)

        val refused = assertIs<GPUPreparedTextFontResolution.Refused>(
            GPUPreparedFontTypefaceResolver.resolve(FontTypeface(bytes, "malformed-cpal")),
        )
        assertEquals(GPUTextRefusalCodes.FONT_BYTES_MALFORMED, refused.code)
    }

    @Test
    fun `COLR version zero accepts an independently versioned CPAL one prefix`() {
        val base = colrFontBytes()
        val tables = sfntTables(base).toMutableMap()
        val cpalV1 = checkNotNull(tables["CPAL"]).copyOf()
        writeU16(cpalV1, 0, 1)
        tables["CPAL"] = cpalV1
        val typeface = FontTypeface(
            sfntFont(base.copyOfRange(0, 4), tables),
            "colrv0-cpalv1",
        )

        assertEquals(
            GPUPreparedTextSourceRepresentation.COLRV0,
            representation(typeface, glyphId = 2),
        )
    }

    @Test
    fun `COLRv0 invalid palette index is not admitted`() {
        val bytes = colrFontBytes()
        val colr = table(bytes, "COLR")
        val firstLayerOffset = readU32(bytes, colr.dataOffset + 8)
        writeU16(bytes, colr.dataOffset + firstLayerOffset + 2, 0x7fff)

        val refused = assertIs<GPUPreparedTextFontResolution.Refused>(
            GPUPreparedFontTypefaceResolver.resolve(
                FontTypeface(bytes, "invalid-colr-palette"),
            ),
        )
        assertEquals(GPUTextRefusalCodes.FONT_BYTES_MALFORMED, refused.code)
    }

    @Test
    fun `COLRv0 requires every layer glyph to have a proved outline or empty range`() {
        val bytes = colrFontBytes()
        val colr = table(bytes, "COLR")
        val firstLayerOffset = readU32(bytes, colr.dataOffset + 8)
        writeU16(bytes, colr.dataOffset + firstLayerOffset, UShort.MAX_VALUE.toInt())

        val refused = assertIs<GPUPreparedTextFontResolution.Refused>(
            GPUPreparedFontTypefaceResolver.resolve(
                FontTypeface(bytes, "invalid-colr-layer-glyph"),
            ),
        )
        assertEquals(GPUTextRefusalCodes.FONT_BYTES_MALFORMED, refused.code)
    }

    @Test
    fun `COLRv0 base glyph id must be inside the parsed face`() {
        val bytes = colrFontBytes()
        val colr = table(bytes, "COLR")
        val firstBaseGlyphOffset = readU32(bytes, colr.dataOffset + 4)
        writeU16(bytes, colr.dataOffset + firstBaseGlyphOffset, UShort.MAX_VALUE.toInt())

        val refused = assertIs<GPUPreparedTextFontResolution.Refused>(
            GPUPreparedFontTypefaceResolver.resolve(
                FontTypeface(bytes, "invalid-colr-base-glyph"),
            ),
        )
        assertEquals(GPUTextRefusalCodes.FONT_BYTES_MALFORMED, refused.code)
    }

    @Test
    fun `COLR table directory diagnostic refuses before outline fallback`() {
        val bytes = colrFontBytes()
        val colr = table(bytes, "COLR")
        writeU32(bytes, colr.directoryOffset + 8, bytes.size - 1)

        val refused = assertIs<GPUPreparedTextFontResolution.Refused>(
            GPUPreparedFontTypefaceResolver.resolve(
                FontTypeface(bytes, "out-of-bounds-colr-directory"),
            ),
        )
        assertEquals(GPUTextRefusalCodes.FONT_BYTES_MALFORMED, refused.code)
    }

    @Test
    fun `COLRv0 duplicate base glyph ids refuse the face`() {
        val bytes = colrFontBytes()
        val colr = table(bytes, "COLR")
        val baseOffset = readU32(bytes, colr.dataOffset + 4)
        val baseCount = readU16(bytes, colr.dataOffset + 2)
        assertTrue(baseCount >= 2)
        val firstGlyphId = readU16(bytes, colr.dataOffset + baseOffset)
        writeU16(bytes, colr.dataOffset + baseOffset + 6, firstGlyphId)

        val refused = assertIs<GPUPreparedTextFontResolution.Refused>(
            GPUPreparedFontTypefaceResolver.resolve(
                FontTypeface(bytes, "duplicate-colrv0-base"),
            ),
        )
        assertEquals(GPUTextRefusalCodes.FONT_BYTES_MALFORMED, refused.code)
    }

    @Test
    fun `COLRv0 base glyph ids must remain strictly sorted`() {
        val bytes = colrFontBytes()
        val colr = table(bytes, "COLR")
        val baseOffset = readU32(bytes, colr.dataOffset + 4)
        val baseCount = readU16(bytes, colr.dataOffset + 2)
        assertTrue(baseCount >= 2)
        val firstGlyphId = readU16(bytes, colr.dataOffset + baseOffset)
        val secondGlyphId = readU16(bytes, colr.dataOffset + baseOffset + 6)
        assertTrue(firstGlyphId < secondGlyphId)
        writeU16(bytes, colr.dataOffset + baseOffset, secondGlyphId)
        writeU16(bytes, colr.dataOffset + baseOffset + 6, firstGlyphId)

        val refused = assertIs<GPUPreparedTextFontResolution.Refused>(
            GPUPreparedFontTypefaceResolver.resolve(
                FontTypeface(bytes, "unsorted-colrv0-base"),
            ),
        )
        assertEquals(GPUTextRefusalCodes.FONT_BYTES_MALFORMED, refused.code)
    }

    @Test
    fun `COLRv0 notdef requires at least one visibly non-empty layer`() {
        val bytes = colrFontBytes()
        val colr = table(bytes, "COLR")
        val baseOffset = readU32(bytes, colr.dataOffset + 4)
        val layerOffset = readU32(bytes, colr.dataOffset + 8)
        val firstLayerIndex = readU16(bytes, colr.dataOffset + baseOffset + 2)
        val layerCount = readU16(bytes, colr.dataOffset + baseOffset + 4)
        assertTrue(layerCount > 0)
        writeU16(bytes, colr.dataOffset + baseOffset, 0)
        repeat(layerCount) { layerIndex ->
            val recordOffset =
                colr.dataOffset + layerOffset + (firstLayerIndex + layerIndex) * 4
            val layerGlyphId = readU16(bytes, recordOffset)
            val startOffset = readLocaOffset(bytes, layerGlyphId)
            writeLocaOffset(bytes, layerGlyphId + 1, startOffset)
        }
        val typeface = FontTypeface(bytes, "empty-colrv0-notdef")
        repeat(layerCount) { layerIndex ->
            val recordOffset =
                colr.dataOffset + layerOffset + (firstLayerIndex + layerIndex) * 4
            val layerGlyphId = readU16(bytes, recordOffset)
            assertEquals(
                PreparedTextOutline.ProvenEmpty,
                typeface.preparedTextOutline(layerGlyphId, 32f),
            )
        }

        assertEquals(
            GPUPreparedTextSourceRepresentation.MISSING,
            representation(typeface, glyphId = 0),
        )
    }

    @Test
    fun `exact sbix source wins over an unpaired CBDT table`() {
        val base = liberationTypeface().fontBytes
        val glyphId = FontTypeface(base, "mixed-bitmap-base")
            .glyphIdForCodepoint('A'.code)
        val tables = sfntTables(base).toMutableMap()
        tables["CBDT"] = byteArrayOf(0, 3, 0, 0)
        tables["sbix"] = sbixTable(
            numGlyphs = readU16(base, table(base, "maxp").dataOffset + 4),
            glyphId = glyphId,
            ppem = 24,
            png = pngPayload(0x11, 0x22),
        )
        val typeface = FontTypeface(
            sfntFont(base.copyOfRange(0, 4), tables),
            "mixed-unpaired-cbdt-sbix",
        )

        assertEquals(
            GPUPreparedTextSourceRepresentation.SBIX,
            representation(typeface, glyphId),
        )
    }

    @Test
    fun `common emoji priority selects bitmap before SVG`() {
        val base = liberationTypeface().fontBytes
        val glyphId = FontTypeface(base, "bitmap-svg-base")
            .glyphIdForCodepoint('A'.code)
        val glyphCount = readU16(base, table(base, "maxp").dataOffset + 4)
        val tables = sfntTables(base).toMutableMap()
        tables["sbix"] = sbixTable(
            numGlyphs = glyphCount,
            glyphId = glyphId,
            ppem = 24,
            png = pngPayload(0x33, 0x44),
        )
        tables["SVG "] = svgTable(
            startGlyphId = glyphId,
            endGlyphId = glyphId,
            bytes = "<svg/>".toByteArray(),
        )
        val typeface = FontTypeface(
            sfntFont(base.copyOfRange(0, 4), tables),
            "bitmap-before-svg",
        )

        assertEquals(
            GPUPreparedTextSourceRepresentation.SBIX,
            representation(typeface, glyphId),
        )
    }

    @Test
    fun `malformed SVG family cannot silently fall back to outline`() {
        val base = liberationTypeface().fontBytes
        val glyphId = FontTypeface(base, "malformed-svg-base")
            .glyphIdForCodepoint('A'.code)
        val tables = sfntTables(base).toMutableMap()
        tables["SVG "] = byteArrayOf(0)
        val typeface = FontTypeface(
            sfntFont(base.copyOfRange(0, 4), tables),
            "malformed-svg",
        )

        assertEquals(
            GPUPreparedTextSourceRepresentation.SVG,
            representation(typeface, glyphId),
        )
    }

    @Test
    fun `unpaired CBDT family cannot silently fall back to outline`() {
        val base = liberationTypeface().fontBytes
        val glyphId = FontTypeface(base, "malformed-cbdt-base")
            .glyphIdForCodepoint('A'.code)
        val tables = sfntTables(base).toMutableMap()
        tables["CBDT"] = byteArrayOf(0, 3, 0, 0)
        val typeface = FontTypeface(
            sfntFont(base.copyOfRange(0, 4), tables),
            "unpaired-cbdt",
        )

        assertEquals(
            GPUPreparedTextSourceRepresentation.CBDT_CBLC,
            representation(typeface, glyphId),
        )
    }

    @Test
    fun `malformed sbix family cannot silently fall back to outline`() {
        val base = liberationTypeface().fontBytes
        val glyphId = FontTypeface(base, "malformed-sbix-base")
            .glyphIdForCodepoint('A'.code)
        val tables = sfntTables(base).toMutableMap()
        tables["sbix"] = byteArrayOf(0, 2)
        val typeface = FontTypeface(
            sfntFont(base.copyOfRange(0, 4), tables),
            "malformed-sbix",
        )

        assertEquals(
            GPUPreparedTextSourceRepresentation.SBIX,
            representation(typeface, glyphId),
        )
    }

    @Test
    fun `valid SVG wins over an independent malformed sbix family`() {
        val base = liberationTypeface().fontBytes
        val glyphId = FontTypeface(base, "valid-svg-malformed-sbix-base")
            .glyphIdForCodepoint('A'.code)
        val tables = sfntTables(base).toMutableMap()
        tables["SVG "] = svgTable(
            startGlyphId = glyphId,
            endGlyphId = glyphId,
            bytes = "<svg/>".toByteArray(),
        )
        tables["sbix"] = byteArrayOf(0, 2)
        val typeface = FontTypeface(
            sfntFont(base.copyOfRange(0, 4), tables),
            "valid-svg-malformed-sbix",
        )

        assertEquals(
            GPUPreparedTextSourceRepresentation.SVG,
            representation(typeface, glyphId),
        )
    }

    @Test
    fun `valid SVG wins when a retained COLRv0 candidate is unavailable at the exact variation`() {
        val base = variableColrv0Font()
        val colr = table(base, "COLR")
        val firstBaseGlyphOffset = readU32(base, colr.dataOffset + 4)
        val baseGlyphId = readU16(base, colr.dataOffset + firstBaseGlyphOffset)
        val tables = sfntTables(base).toMutableMap()
        tables["SVG "] = svgTable(
            startGlyphId = baseGlyphId,
            endGlyphId = baseGlyphId,
            bytes = "<svg/>".toByteArray(),
        )
        val typeface = FontTypeface(
            sfntFont(base.copyOfRange(0, 4), tables),
            "unavailable-colrv0-valid-svg",
        )
        val ready = assertIs<GPUPreparedTextFontResolution.Ready>(
            GPUPreparedFontTypefaceResolver.resolve(typeface),
        )

        assertEquals(
            GPUPreparedTextSourceRepresentation.SVG,
            ready.representationResolver.resolve(
                baseGlyphId,
                32f,
                mapOf("ZZZZ" to 1f),
            ),
        )
    }

    @Test
    fun `COLR version one preserves v1 priority and retained v0-only glyphs`() {
        val bytes = colrV1WithMixedLegacyRecords()
        val colr = table(bytes, "COLR")
        val firstBaseGlyphOffset = readU32(bytes, colr.dataOffset + 4)
        val firstBaseGlyphId = readU16(bytes, colr.dataOffset + firstBaseGlyphOffset)
        val secondBaseGlyphId = readU16(bytes, colr.dataOffset + firstBaseGlyphOffset + 6)
        val ready = assertIs<GPUPreparedTextFontResolution.Ready>(
            GPUPreparedFontTypefaceResolver.resolve(
                FontTypeface(bytes, "mixed-colrv1-retained-v0"),
            ),
        )

        assertEquals(
            GPUPreparedTextSourceRepresentation.COLRV1,
            ready.representationResolver.resolve(firstBaseGlyphId, 32f, emptyMap()),
        )
        assertEquals(
            GPUPreparedTextSourceRepresentation.COLRV0,
            ready.representationResolver.resolve(secondBaseGlyphId, 32f, emptyMap()),
        )
    }

    @Test
    fun `COLR version one accepts the compatible CPAL version one prefix`() {
        val base = colrV1WithMixedLegacyRecords()
        val tables = sfntTables(base).toMutableMap()
        val cpalV1 = checkNotNull(tables["CPAL"]).copyOf()
        writeU16(cpalV1, 0, 1)
        tables["CPAL"] = cpalV1
        val bytes = sfntFont(base.copyOfRange(0, 4), tables)
        val colr = table(bytes, "COLR")
        val firstBaseGlyphOffset = readU32(bytes, colr.dataOffset + 4)
        val retainedGlyphId = readU16(bytes, colr.dataOffset + firstBaseGlyphOffset + 6)
        val ready = assertIs<GPUPreparedTextFontResolution.Ready>(
            GPUPreparedFontTypefaceResolver.resolve(
                FontTypeface(bytes, "mixed-colrv1-cpalv1"),
            ),
        )

        assertEquals(
            GPUPreparedTextSourceRepresentation.COLRV0,
            ready.representationResolver.resolve(retainedGlyphId, 32f, emptyMap()),
        )
    }

    @Test
    fun `COLR version one refuses a malformed announced retained v0 prefix`() {
        val bytes = colrV1WithMixedLegacyRecords()
        val colr = table(bytes, "COLR")
        writeU32(bytes, colr.dataOffset + 4, Int.MAX_VALUE)

        val refused = assertIs<GPUPreparedTextFontResolution.Refused>(
            GPUPreparedFontTypefaceResolver.resolve(
                FontTypeface(bytes, "malformed-retained-v0-prefix"),
            ),
        )

        assertEquals(GPUTextRefusalCodes.FONT_BYTES_MALFORMED, refused.code)
    }

    @Test
    fun `COLRv0 non-notdef accepts an empty glyph zero auxiliary layer`() {
        val bytes = colrFontBytes()
        val colr = table(bytes, "COLR")
        val baseOffset = readU32(bytes, colr.dataOffset + 4)
        val layerOffset = readU32(bytes, colr.dataOffset + 8)
        val baseGlyphCount = readU16(bytes, colr.dataOffset + 2)
        val selectedBaseOffset = (0 until baseGlyphCount)
            .map { index -> colr.dataOffset + baseOffset + index * 6 }
            .first { recordOffset ->
                readU16(bytes, recordOffset) != 0 &&
                    readU16(bytes, recordOffset + 4) >= 2
            }
        val baseGlyphId = readU16(bytes, selectedBaseOffset)
        val firstLayerIndex = readU16(bytes, selectedBaseOffset + 2)
        val layerCount = readU16(bytes, selectedBaseOffset + 4)
        assertTrue(baseGlyphId != 0)
        assertTrue(layerCount >= 2)
        writeU16(bytes, colr.dataOffset + layerOffset + firstLayerIndex * 4, 0)
        writeLocaOffset(bytes, glyphId = 1, value = readLocaOffset(bytes, glyphId = 0))
        val ready = assertIs<GPUPreparedTextFontResolution.Ready>(
            GPUPreparedFontTypefaceResolver.resolve(
                FontTypeface(bytes, "empty-glyph-zero-colrv0-layer"),
            ),
        )

        assertEquals(
            GPUPreparedTextSourceRepresentation.COLRV0,
            ready.representationResolver.resolve(baseGlyphId, 32f, emptyMap()),
        )
    }

    @Test
    fun `resolver builds immutable indexes once instead of scanning parsed tables per glyph`() {
        val source = java.io.File(
            "src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextFontResolver.kt",
        ).readText()

        listOf(
            "colrv0LayerIndex",
            "colrv1PaintIndex",
            "svgGlyphIndex",
            "bitmapGlyphIndex",
            "representationMemo",
        ).forEach { requiredIndex ->
            assertTrue(requiredIndex in source, "Missing one-time resolver index $requiredIndex")
        }
        listOf(
            ".layersForGlyph(glyphId)",
            ".paintForGlyph(glyphId)",
            ".documentForGlyph(glyphId)",
            ".glyph(glyphId)",
        ).forEach { forbiddenLinearLookup ->
            assertTrue(
                forbiddenLinearLookup !in source,
                "Per-glyph resolver must not use linear lookup $forbiddenLinearLookup",
            )
        }
    }

    @Test
    fun `COLRv0 layer outlines are revalidated at the exact variation request`() {
        val bytes = variableColrv0Font()
        val colr = table(bytes, "COLR")
        val firstBaseGlyphOffset = readU32(bytes, colr.dataOffset + 4)
        val baseGlyphId = readU16(bytes, colr.dataOffset + firstBaseGlyphOffset)
        val typeface = FontTypeface(bytes, "variable-colrv0")
        val ready = assertIs<GPUPreparedTextFontResolution.Ready>(
            GPUPreparedFontTypefaceResolver.resolve(typeface),
        )

        assertEquals(
            GPUPreparedTextSourceRepresentation.COLRV0,
            ready.representationResolver.resolve(baseGlyphId, 32f, emptyMap()),
        )
        assertEquals(
            GPUPreparedTextSourceRepresentation.MISSING,
            ready.representationResolver.resolve(
                baseGlyphId,
                32f,
                mapOf("ZZZZ" to 1f),
            ),
        )
    }

    @Test
    fun `malformed COLRv1 retained prefix refuses the font instead of falling back to outline`() {
        val bytes = colrFontBytes()
        val colr = table(bytes, "COLR")
        writeU16(bytes, colr.dataOffset, 1)
        setTableLength(bytes, "COLR", 2)

        val refused = assertIs<GPUPreparedTextFontResolution.Refused>(
            GPUPreparedFontTypefaceResolver.resolve(
                FontTypeface(bytes, "malformed-colrv1"),
            ),
        )
        assertEquals(GPUTextRefusalCodes.FONT_BYTES_MALFORMED, refused.code)
    }

    private fun representation(
        typeface: FontTypeface,
        glyphId: Int,
    ): GPUPreparedTextSourceRepresentation {
        val ready = assertIs<GPUPreparedTextFontResolution.Ready>(
            GPUPreparedFontTypefaceResolver.resolve(typeface),
        )
        return ready.representationResolver.resolve(glyphId, 32f, emptyMap())
    }

    private fun colrFontBytes(): ByteArray = checkNotNull(
        javaClass.classLoader.getResourceAsStream("fonts/skia/colr.ttf"),
    ).use { it.readBytes() }

    private fun variableColrv0Font(): ByteArray {
        val projectRoot = java.io.File("..").canonicalFile
        val variableBytes = projectRoot.resolve(
            "reports/font/fixtures/fonts/scaler/RobotoFlex-Variable.ttf",
        ).readBytes()
        val colorBytes = colrFontBytes()
        val tables = sfntTables(variableBytes).toMutableMap()
        val colorTables = sfntTables(colorBytes)
        tables["COLR"] = checkNotNull(colorTables["COLR"])
        tables["CPAL"] = checkNotNull(colorTables["CPAL"])
        return sfntFont(
            scalerType = variableBytes.copyOfRange(0, 4),
            tables = tables,
        )
    }

    private fun colrV1WithMixedLegacyRecords(): ByteArray {
        val baseBytes = colrFontBytes()
        val tables = sfntTables(baseBytes).toMutableMap()
        val legacy = checkNotNull(tables["COLR"])
        val legacyBaseOffset = readU32(legacy, 4)
        val legacyLayerOffset = readU32(legacy, 8)
        val firstBaseGlyphId = readU16(legacy, legacyBaseOffset)
        val retainedPayloadShift = 34
        val baseGlyphListOffset = retainedPayloadShift + legacy.size
        val paintOffset = baseGlyphListOffset + 10
        val mixed = ByteArray(paintOffset + 5)
        legacy.copyInto(mixed, destinationOffset = retainedPayloadShift)
        writeU16(mixed, 0, 1)
        writeU16(mixed, 2, readU16(legacy, 2))
        writeU32(mixed, 4, legacyBaseOffset + retainedPayloadShift)
        writeU32(mixed, 8, legacyLayerOffset + retainedPayloadShift)
        writeU16(mixed, 12, readU16(legacy, 12))
        writeU32(mixed, 14, baseGlyphListOffset)
        writeU32(mixed, baseGlyphListOffset, 1)
        writeU16(mixed, baseGlyphListOffset + 4, firstBaseGlyphId)
        writeU32(mixed, baseGlyphListOffset + 6, paintOffset - baseGlyphListOffset)
        mixed[paintOffset] = 2
        writeU16(mixed, paintOffset + 1, 0)
        writeU16(mixed, paintOffset + 3, 0x4000)
        tables["COLR"] = mixed
        return sfntFont(
            scalerType = baseBytes.copyOfRange(0, 4),
            tables = tables,
        )
    }

    private fun svgTable(
        startGlyphId: Int,
        endGlyphId: Int,
        bytes: ByteArray,
    ): ByteArray {
        val documentListOffset = 10
        val documentRecordOffset = documentListOffset + 2
        val documentOffset = 14
        return ByteArray(documentListOffset + documentOffset + bytes.size).also { table ->
            writeU16(table, 0, 0)
            writeU32(table, 2, documentListOffset)
            writeU32(table, 6, 0)
            writeU16(table, documentListOffset, 1)
            writeU16(table, documentRecordOffset, startGlyphId)
            writeU16(table, documentRecordOffset + 2, endGlyphId)
            writeU32(table, documentRecordOffset + 4, documentOffset)
            writeU32(table, documentRecordOffset + 8, bytes.size)
            bytes.copyInto(table, documentListOffset + documentOffset)
        }
    }

    private fun sbixTable(
        numGlyphs: Int,
        glyphId: Int,
        ppem: Int,
        png: ByteArray,
    ): ByteArray {
        val strikeOffset = 12
        val offsetsStart = strikeOffset + 4
        val glyphDataOffset = 4 + (numGlyphs + 1) * 4
        val glyphPayload = ByteArray(8 + png.size).also { payload ->
            writeU16(payload, 0, 0)
            writeU16(payload, 2, 0)
            "png ".toByteArray(Charsets.ISO_8859_1).copyInto(payload, 4)
            png.copyInto(payload, 8)
        }
        return ByteArray(strikeOffset + glyphDataOffset + glyphPayload.size).also { table ->
            writeU16(table, 0, 1)
            writeU16(table, 2, 0)
            writeU32(table, 4, 1)
            writeU32(table, 8, strikeOffset)
            writeU16(table, strikeOffset, ppem)
            writeU16(table, strikeOffset + 2, 72)
            repeat(numGlyphs + 1) { index ->
                writeU32(
                    table,
                    offsetsStart + index * 4,
                    if (index <= glyphId) glyphDataOffset else glyphDataOffset + glyphPayload.size,
                )
            }
            glyphPayload.copyInto(table, strikeOffset + glyphDataOffset)
        }
    }

    private fun pngPayload(vararg trailingBytes: Int): ByteArray = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4e,
        0x47,
        0x0d,
        0x0a,
        0x1a,
        0x0a,
        *trailingBytes.map(Int::toByte).toByteArray(),
    )

    private data class SfntTable(
        val directoryOffset: Int,
        val dataOffset: Int,
        val length: Int,
    )

    private fun table(bytes: ByteArray, tag: String): SfntTable {
        val tableCount = readU16(bytes, 4)
        repeat(tableCount) { index ->
            val directoryOffset = 12 + index * 16
            val observedTag = String(bytes, directoryOffset, 4, Charsets.ISO_8859_1)
            if (observedTag == tag) {
                return SfntTable(
                    directoryOffset = directoryOffset,
                    dataOffset = readU32(bytes, directoryOffset + 8),
                    length = readU32(bytes, directoryOffset + 12),
                )
            }
        }
        error("Missing SFNT table $tag")
    }

    private fun setTableLength(bytes: ByteArray, tag: String, length: Int) {
        writeU32(bytes, table(bytes, tag).directoryOffset + 12, length)
    }

    private fun readLocaOffset(bytes: ByteArray, glyphId: Int): Int {
        val loca = table(bytes, "loca")
        val head = table(bytes, "head")
        return when (readU16(bytes, head.dataOffset + 50)) {
            0 -> readU16(bytes, loca.dataOffset + glyphId * 2) * 2
            1 -> readU32(bytes, loca.dataOffset + glyphId * 4)
            else -> error("Unsupported fixture loca format")
        }
    }

    private fun writeLocaOffset(bytes: ByteArray, glyphId: Int, value: Int) {
        val loca = table(bytes, "loca")
        val head = table(bytes, "head")
        when (readU16(bytes, head.dataOffset + 50)) {
            0 -> {
                require(value % 2 == 0)
                writeU16(bytes, loca.dataOffset + glyphId * 2, value / 2)
            }
            1 -> writeU32(bytes, loca.dataOffset + glyphId * 4, value)
            else -> error("Unsupported fixture loca format")
        }
    }

    private fun locaRange(bytes: ByteArray, glyphId: Int): IntRange {
        val locaRecord = table(bytes, "loca")
        val headRecord = table(bytes, "head")
        val maxpRecord = table(bytes, "maxp")
        val format = when (readU16(bytes, headRecord.dataOffset + 50)) {
            0 -> TrueTypeLocaFormat.Short
            1 -> TrueTypeLocaFormat.Long
            else -> error("Unsupported fixture loca format")
        }
        val loca = TrueTypeLocaTableParser.parse(
            data = bytes.copyOfRange(
                locaRecord.dataOffset,
                locaRecord.dataOffset + locaRecord.length,
            ),
            format = format,
            numGlyphs = readU16(bytes, maxpRecord.dataOffset + 4),
        )
        val range = loca.rangeForGlyph(glyphId.toUInt())
        return range.start..range.endExclusive
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or
            (bytes[offset + 1].toInt() and 0xff)

    private fun readU32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun ttcFont(vararg faces: ByteArray): ByteArray {
        val headerLength = 12 + faces.size * 4
        val collection = ByteArray(headerLength + faces.sumOf(ByteArray::size))
        writeU32(collection, 0, 0x74746366)
        writeU32(collection, 4, 0x00010000)
        writeU32(collection, 8, faces.size)

        var cursor = headerLength
        faces.forEachIndexed { index, face ->
            writeU32(collection, 12 + index * 4, cursor)
            val routedFace = face.copyOf()
            val tableCount = readU16(routedFace, 4)
            repeat(tableCount) { tableIndex ->
                val recordOffset = 12 + tableIndex * 16
                writeU32(
                    routedFace,
                    recordOffset + 8,
                    cursor + readU32(routedFace, recordOffset + 8),
                )
            }
            routedFace.copyInto(collection, destinationOffset = cursor)
            cursor += routedFace.size
        }
        return collection
    }

    private fun sfntTables(bytes: ByteArray): Map<String, ByteArray> {
        val tables = LinkedHashMap<String, ByteArray>()
        repeat(readU16(bytes, 4)) { index ->
            val recordOffset = 12 + index * 16
            val tag = String(bytes, recordOffset, 4, Charsets.ISO_8859_1)
            val dataOffset = readU32(bytes, recordOffset + 8)
            val length = readU32(bytes, recordOffset + 12)
            tables[tag] = bytes.copyOfRange(dataOffset, dataOffset + length)
        }
        return tables
    }

    private fun sfntFont(
        scalerType: ByteArray,
        tables: Map<String, ByteArray>,
    ): ByteArray {
        val sortedTables = tables.toSortedMap()
        val tableCount = sortedTables.size
        val directoryLength = 12 + tableCount * 16
        val payloadLength = sortedTables.values.sumOf { payload ->
            (payload.size + 3) and -4
        }
        val font = ByteArray(directoryLength + payloadLength)
        scalerType.copyInto(font, destinationOffset = 0)
        writeU16(font, 4, tableCount)
        val largestPowerOfTwo = Integer.highestOneBit(tableCount)
        writeU16(font, 6, largestPowerOfTwo * 16)
        writeU16(font, 8, Integer.numberOfTrailingZeros(largestPowerOfTwo))
        writeU16(font, 10, tableCount * 16 - largestPowerOfTwo * 16)

        var payloadOffset = directoryLength
        var headPayloadOffset: Int? = null
        sortedTables.entries.forEachIndexed { index, (tag, sourcePayload) ->
            val payload = sourcePayload.copyOf()
            if (tag == "head" && payload.size >= 12) {
                writeU32(payload, 8, 0)
            }
            val recordOffset = 12 + index * 16
            tag.toByteArray(Charsets.ISO_8859_1).copyInto(font, destinationOffset = recordOffset)
            writeU32(font, recordOffset + 4, sfntChecksum(payload))
            writeU32(font, recordOffset + 8, payloadOffset)
            writeU32(font, recordOffset + 12, payload.size)
            payload.copyInto(font, destinationOffset = payloadOffset)
            if (tag == "head") headPayloadOffset = payloadOffset
            payloadOffset += (payload.size + 3) and -4
        }
        headPayloadOffset?.let { offset ->
            val adjustment = (0xB1B0AFBAL - sfntChecksum(font).toLong().and(0xffff_ffffL))
                .and(0xffff_ffffL)
                .toInt()
            writeU32(font, offset + 8, adjustment)
        }
        return font
    }

    private fun sfntChecksum(bytes: ByteArray): Int {
        var checksum = 0L
        var offset = 0
        while (offset < bytes.size) {
            var word = 0L
            repeat(4) { index ->
                word = word shl 8
                if (offset + index < bytes.size) {
                    word = word or (bytes[offset + index].toLong() and 0xff)
                }
            }
            checksum = (checksum + word).and(0xffff_ffffL)
            offset += 4
        }
        return checksum.toInt()
    }
}
