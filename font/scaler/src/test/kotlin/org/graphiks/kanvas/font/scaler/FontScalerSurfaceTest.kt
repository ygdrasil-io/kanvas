package org.graphiks.kanvas.font.scaler

import org.graphiks.kanvas.font.FontSource
import org.graphiks.kanvas.font.FontSourceID
import org.graphiks.kanvas.font.FontSourceKind
import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.font.sfnt.HorizontalGlyphMetric
import org.graphiks.kanvas.font.sfnt.MetricsTables
import org.graphiks.kanvas.font.sfnt.OpenTypeAvarAxisSegmentMap
import org.graphiks.kanvas.font.sfnt.OpenTypeAvarSegment
import org.graphiks.kanvas.font.sfnt.OpenTypeFaceData
import org.graphiks.kanvas.font.sfnt.OpenTypeFixed16Dot16
import org.graphiks.kanvas.font.sfnt.OpenTypeVariationAxis
import org.graphiks.kanvas.font.sfnt.OpenTypeVariationAxisTag
import org.graphiks.kanvas.font.sfnt.SFNTTableDirectory
import org.graphiks.kanvas.font.sfnt.SFNTTableTag
import org.graphiks.kanvas.font.sfnt.VariationTables
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.uuid.Uuid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FontScalerSurfaceTest {
    @Test
    fun exposesScalerGeometryMetricsAndVariationValueObjects() {
        val bounds = GlyphBounds(
            left = 1.0,
            top = 2.0,
            right = 3.0,
            bottom = 4.0,
        )
        val metrics = GlyphMetrics(
            advanceX = 9.0,
            advanceY = 0.0,
            bounds = bounds,
        )
        val outline = GlyphOutline(
            glyphId = 42u,
            contours = emptyList(),
        )
        val position = VariationPosition(axes = mapOf("wght" to 700.0))

        assertEquals(2.0, bounds.top)
        assertEquals(bounds, metrics.bounds)
        assertEquals(42u, outline.glyphId)
        assertEquals(700.0, position.axes.getValue("wght"))
    }

    @Test
    fun boundedVariationNormalizerClampsAndNormalizesConfiguredAxesByTag() {
        val normalizer = BoundedVariationNormalizer(
            listOf(
                VariationAxis(tag = "wght", minimum = 100.0, defaultValue = 400.0, maximum = 900.0),
                VariationAxis(tag = "ital", minimum = 0.0, defaultValue = 0.0, maximum = 1.0),
                VariationAxis(tag = "wdth", minimum = 50.0, defaultValue = 100.0, maximum = 200.0),
            ),
        )

        val normalized = normalizer.normalize(
            VariationPosition(
                axes = mapOf(
                    "wght" to 950.0,
                    "wdth" to 75.0,
                ),
            ),
        )

        assertEquals(listOf("ital", "wdth", "wght"), normalized.keys.toList())
        assertEquals(0.0, normalized.getValue("ital"))
        assertEquals(-0.5, normalized.getValue("wdth"))
        assertEquals(1.0, normalized.getValue("wght"))
    }

    @Test
    fun boundedVariationNormalizerMapsBelowAndAboveDefaultSeparately() {
        val normalizer = BoundedVariationNormalizer(
            listOf(
                VariationAxis(tag = "opsz", minimum = 8.0, defaultValue = 14.0, maximum = 72.0),
                VariationAxis(tag = "slnt", minimum = -12.0, defaultValue = 0.0, maximum = 0.0),
            ),
        )

        val normalized = normalizer.normalize(
            VariationPosition(
                axes = mapOf(
                    "opsz" to 43.0,
                    "slnt" to -6.0,
                ),
            ),
        )

        assertEquals(0.5, normalized.getValue("opsz"))
        assertEquals(-0.5, normalized.getValue("slnt"))
    }

    @Test
    fun boundedVariationNormalizerRejectsNonFiniteAndIncoherentAxes() {
        val nonFinite = assertFailsWith<IllegalArgumentException> {
            VariationAxis(tag = "wght", minimum = 100.0, defaultValue = Double.NaN, maximum = 900.0)
        }
        val invertedDefault = assertFailsWith<IllegalArgumentException> {
            VariationAxis(tag = "wdth", minimum = 100.0, defaultValue = 75.0, maximum = 200.0)
        }
        val duplicateTag = assertFailsWith<IllegalArgumentException> {
            BoundedVariationNormalizer(
                listOf(
                    VariationAxis(tag = "wght", minimum = 100.0, defaultValue = 400.0, maximum = 900.0),
                    VariationAxis(tag = "wght", minimum = 100.0, defaultValue = 400.0, maximum = 900.0),
                ),
            )
        }

        assertTrue(nonFinite.message.orEmpty().contains("defaultValue"))
        assertTrue(invertedDefault.message.orEmpty().contains("minimum"))
        assertTrue(duplicateTag.message.orEmpty().contains("duplicate"))
    }

    @Test
    fun boundedVariationNormalizerRejectsUnknownAndNonFiniteRequestedPositions() {
        val normalizer = BoundedVariationNormalizer(
            listOf(
                VariationAxis(tag = "wght", minimum = 100.0, defaultValue = 400.0, maximum = 900.0),
            ),
        )

        val unknown = assertFailsWith<IllegalArgumentException> {
            normalizer.normalize(VariationPosition(axes = mapOf("wdth" to 100.0)))
        }
        val nonFinite = assertFailsWith<IllegalArgumentException> {
            normalizer.normalize(VariationPosition(axes = mapOf("wght" to Double.POSITIVE_INFINITY)))
        }

        assertTrue(unknown.message.orEmpty().contains("wdth"))
        assertTrue(nonFinite.message.orEmpty().contains("finite"))
    }

    @Test
    fun glyphOutlineKeepsLegacyContoursAndExposesTypedCommands() {
        val outline = GlyphOutline(
            glyphId = 7u,
            contours = listOf("M 0 0", "L 10 0"),
            commands = listOf(
                moveTo(0.0, 0.0),
                lineTo(10.0, 0.0),
                quadraticTo(12.0, 4.0, 10.0, 10.0),
                cubicTo(6.0, 14.0, 3.0, 12.0, 0.0, 10.0),
                close(),
            ),
        )

        assertEquals(listOf("M 0 0", "L 10 0"), outline.contours)
        assertEquals(
            listOf(
                OutlineCommand.MoveTo(0.0, 0.0),
                OutlineCommand.LineTo(10.0, 0.0),
                OutlineCommand.QuadraticTo(12.0, 4.0, 10.0, 10.0),
                OutlineCommand.CubicTo(6.0, 14.0, 3.0, 12.0, 0.0, 10.0),
                OutlineCommand.Close,
            ),
            outline.commands,
        )
    }

    @Test
    fun computesConservativeBoundsFromTypedOutlineCommands() {
        val commands = listOf(
            moveTo(1.0, 2.0),
            lineTo(3.0, -4.0),
            quadraticTo(5.0, 10.0, -2.0, 6.0),
            cubicTo(8.0, -9.0, -7.0, 11.0, 4.0, 0.0),
            close(),
        )

        assertEquals(
            GlyphBounds(left = -7.0, top = -9.0, right = 8.0, bottom = 11.0),
            commands.conservativeBounds(),
        )
        assertNull(listOf(close()).conservativeBounds())
    }

    @Test
    fun scalesBoundsAndTypedOutlinesFromFontUnitsToPixels() {
        val bounds = GlyphBounds(left = -500.0, top = -250.0, right = 1000.0, bottom = 750.0)
        val outline = GlyphOutline(
            glyphId = 9u,
            contours = listOf("legacy-outline"),
            commands = listOf(
                moveTo(0.0, 0.0),
                lineTo(1000.0, 500.0),
                quadraticTo(500.0, 750.0, 250.0, 1000.0),
                cubicTo(-250.0, 1250.0, 1500.0, -500.0, 2000.0, 0.0),
                close(),
            ),
        )

        assertEquals(
            GlyphBounds(left = -8.0, top = -4.0, right = 16.0, bottom = 12.0),
            bounds.fontUnitsToPixels(unitsPerEm = 1000.0, pixelSize = 16.0),
        )
        assertEquals(
            listOf(
                moveTo(0.0, 0.0),
                lineTo(16.0, 8.0),
                quadraticTo(8.0, 12.0, 4.0, 16.0),
                cubicTo(-4.0, 20.0, 24.0, -8.0, 32.0, 0.0),
                close(),
            ),
            outline.fontUnitsToPixels(unitsPerEm = 1000.0, pixelSize = 16.0).commands,
        )
        assertEquals(listOf("legacy-outline"), outline.fontUnitsToPixels(1000.0, 16.0).contours)
    }

    @Test
    fun parsesTrueTypeLocaOffsetsInShortAndLongFormats() {
        val shortLoca = TrueTypeLocaTableParser.parse(
            data = bytes(
                0x00, 0x00,
                0x00, 0x05,
                0x00, 0x05,
                0x00, 0x0c,
            ),
            format = TrueTypeLocaFormat.Short,
            numGlyphs = 3,
        )
        val longLoca = TrueTypeLocaTableParser.parse(
            data = bytes(
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x12,
                0x00, 0x00, 0x00, 0x1a,
                0x00, 0x00, 0x00, 0x1a,
            ),
            format = TrueTypeLocaFormat.Long,
            numGlyphs = 3,
        )

        assertEquals(listOf(0, 10, 10, 24), shortLoca.offsets)
        assertEquals(TrueTypeGlyphDataRange(start = 0, endExclusive = 10), shortLoca.rangeForGlyph(0u))
        assertEquals(TrueTypeGlyphDataRange(start = 10, endExclusive = 10), shortLoca.rangeForGlyph(1u))
        assertEquals(listOf(0, 18, 26, 26), longLoca.offsets)
    }

    @Test
    fun rejectsInvalidTrueTypeLocaTables() {
        val truncated = assertFailsWith<IllegalArgumentException> {
            TrueTypeLocaTableParser.parse(
                data = bytes(0x00, 0x00),
                format = TrueTypeLocaFormat.Short,
                numGlyphs = 1,
            )
        }
        val descending = assertFailsWith<IllegalArgumentException> {
            TrueTypeLocaTableParser.parse(
                data = bytes(
                    0x00, 0x05,
                    0x00, 0x04,
                ),
                format = TrueTypeLocaFormat.Short,
                numGlyphs = 1,
            )
        }

        assertTrue(truncated.message.orEmpty().contains("loca table length"))
        assertTrue(descending.message.orEmpty().contains("monotonic"))
    }

    @Test
    fun parsesTrueTypeSimpleGlyphWithRepeatedFlagsAndDeltaCoordinates() {
        val glyph = assertIs<TrueTypeGlyph.Simple>(
            TrueTypeGlyfTableParser.parseGlyph(
                glyfTable = simpleRepeatedFlagGlyphData(),
                range = TrueTypeGlyphDataRange(start = 0, endExclusive = simpleRepeatedFlagGlyphData().size),
                glyphId = 4u,
            ),
        )

        assertEquals(
            TrueTypeGlyphHeader(
                numberOfContours = 1,
                xMin = 0,
                yMin = 0,
                xMax = 100,
                yMax = 40,
            ),
            glyph.header,
        )
        assertEquals(listOf(3), glyph.endPointsOfContours)
        assertEquals(
            listOf(
                TrueTypeGlyphPoint(x = 0, y = 0, onCurve = true, flags = 0x31),
                TrueTypeGlyphPoint(x = 50, y = 0, onCurve = true, flags = 0x33),
                TrueTypeGlyphPoint(x = 100, y = 0, onCurve = true, flags = 0x33),
                TrueTypeGlyphPoint(x = 100, y = 40, onCurve = true, flags = 0x35),
            ),
            glyph.contours.single().points,
        )
    }

    @Test
    fun convertsSimpleGlyphContoursToTypedOutlineCommands() {
        val glyph = assertIs<TrueTypeGlyph.Simple>(
            TrueTypeGlyfTableParser.parseGlyph(
                glyfTable = simpleQuadraticGlyphData(),
                range = TrueTypeGlyphDataRange(start = 0, endExclusive = simpleQuadraticGlyphData().size),
                glyphId = 6u,
            ),
        )

        assertEquals(
            GlyphOutline(
                glyphId = 6u,
                commands = listOf(
                    moveTo(0.0, 0.0),
                    lineTo(100.0, 0.0),
                    quadraticTo(100.0, 100.0, 0.0, 100.0),
                    close(),
                ),
            ),
            glyph.toGlyphOutline(glyphId = 6u),
        )
    }

    @Test
    fun convertsConsecutiveOffCurvePointsWithImplicitMidpoint() {
        val glyph = TrueTypeGlyph.Simple(
            header = TrueTypeGlyphHeader(
                numberOfContours = 1,
                xMin = 0,
                yMin = 0,
                xMax = 6,
                yMax = 2,
            ),
            endPointsOfContours = listOf(3),
            instructionLength = 0,
            contours = listOf(
                TrueTypeGlyphContour(
                    endPointIndex = 3,
                    points = listOf(
                        TrueTypeGlyphPoint(x = 0, y = 0, onCurve = true, flags = 0x01),
                        TrueTypeGlyphPoint(x = 1, y = 1, onCurve = false, flags = 0x00),
                        TrueTypeGlyphPoint(x = 4, y = 2, onCurve = false, flags = 0x00),
                        TrueTypeGlyphPoint(x = 6, y = 0, onCurve = true, flags = 0x01),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                moveTo(0.0, 0.0),
                quadraticTo(1.0, 1.0, 2.5, 1.5),
                quadraticTo(4.0, 2.0, 6.0, 0.0),
                close(),
            ),
            glyph.toGlyphOutline(glyphId = 10u).commands,
        )
    }

    @Test
    fun parsesCompositeGlyphComponentRecordsWithXyArgumentsScalesAndMoreComponents() {
        val glyphData = compositeGlyphData(
            *componentRecord(
                flags = 0x002b,
                glyphId = 2,
                arg1 = 10,
                arg2 = -20,
                transformWords = intArrayOf(0x6000),
            ),
            *componentRecord(
                flags = 0x0063,
                glyphId = 3,
                arg1 = -2,
                arg2 = 4,
                transformWords = intArrayOf(0x2000, 0xc000),
            ),
            *componentRecord(
                flags = 0x0083,
                glyphId = 4,
                arg1 = 7,
                arg2 = 8,
                transformWords = intArrayOf(0x4000, 0x1000, 0xe000, 0x3000),
            ),
        )
        val glyph = assertIs<TrueTypeGlyph.Composite>(
            TrueTypeGlyfTableParser.parseGlyph(
                glyfTable = glyphData,
                range = TrueTypeGlyphDataRange(start = 0, endExclusive = glyphData.size),
                glyphId = 7u,
            ),
        )

        assertEquals(-1, glyph.header.numberOfContours)
        assertEquals(3, glyph.components.size)
        assertEquals(
            TrueTypeCompositeGlyphComponent(
                glyphId = 2u,
                flags = 0x002b,
                arguments = TrueTypeCompositeGlyphArgument.XyValues(x = 10, y = -20),
                transform = TrueTypeCompositeTransform(xx = 1.5, yy = 1.5, dx = 10.0, dy = -20.0),
            ),
            glyph.components[0],
        )
        assertEquals(
            TrueTypeCompositeGlyphComponent(
                glyphId = 3u,
                flags = 0x0063,
                arguments = TrueTypeCompositeGlyphArgument.XyValues(x = -2, y = 4),
                transform = TrueTypeCompositeTransform(xx = 0.5, yy = -1.0, dx = -2.0, dy = 4.0),
            ),
            glyph.components[1],
        )
        assertEquals(
            TrueTypeCompositeGlyphComponent(
                glyphId = 4u,
                flags = 0x0083,
                arguments = TrueTypeCompositeGlyphArgument.XyValues(x = 7, y = 8),
                transform = TrueTypeCompositeTransform(xx = 1.0, xy = -0.5, yx = 0.25, yy = 0.75, dx = 7.0, dy = 8.0),
            ),
            glyph.components[2],
        )
    }

    @Test
    fun parsesCompositePointMatchingArgumentsAsExplicitUnsupportedRecords() {
        val glyph = assertIs<TrueTypeGlyph.Composite>(
            TrueTypeGlyfTableParser.parseGlyph(
                glyfTable = compositeGlyphData(
                    *componentRecord(
                        flags = 0x0001,
                        glyphId = 2,
                        arg1 = 5,
                        arg2 = 6,
                    ),
                ),
                range = TrueTypeGlyphDataRange(start = 0, endExclusive = 18),
                glyphId = 8u,
            ),
        )

        assertEquals(
            TrueTypeCompositeGlyphArgument.PointMatching(
                compoundPointIndex = 5,
                componentPointIndex = 6,
            ),
            glyph.components.single().arguments,
        )
        assertEquals(TrueTypeCompositeTransform(), glyph.components.single().transform)
    }

    @Test
    fun transformsTypedOutlineCommandsWithCompositeMatrix() {
        val transform = TrueTypeCompositeTransform(
            xx = 2.0,
            xy = 3.0,
            yx = -1.0,
            yy = 0.5,
            dx = 10.0,
            dy = -4.0,
        )

        assertEquals(
            listOf(
                moveTo(18.0, -4.0),
                lineTo(19.0, -6.5),
                quadraticTo(20.0, -9.0, 21.0, -11.5),
                cubicTo(22.0, -14.0, 23.0, -16.5, 24.0, -19.0),
                close(),
            ),
            listOf(
                moveTo(1.0, 2.0),
                lineTo(3.0, 1.0),
                quadraticTo(5.0, 0.0, 7.0, -1.0),
                cubicTo(9.0, -2.0, 11.0, -3.0, 13.0, -4.0),
                close(),
            ).transformed(transform),
        )
    }

    @Test
    fun rejectsInvalidTrueTypeGlyfRangesAndTruncatedGlyphs() {
        val outOfRange = assertFailsWith<IllegalArgumentException> {
            TrueTypeGlyfTableParser.parseGlyph(
                glyfTable = bytes(0x00, 0x00),
                range = TrueTypeGlyphDataRange(start = 0, endExclusive = 20),
                glyphId = 8u,
            )
        }
        val truncatedCoordinates = assertFailsWith<IllegalArgumentException> {
            TrueTypeGlyfTableParser.parseGlyph(
                glyfTable = simpleRepeatedFlagGlyphData().copyOf(simpleRepeatedFlagGlyphData().size - 1),
                range = TrueTypeGlyphDataRange(start = 0, endExclusive = simpleRepeatedFlagGlyphData().size - 1),
                glyphId = 9u,
            )
        }

        assertTrue(outOfRange.message.orEmpty().contains("outside glyf table"))
        assertTrue(truncatedCoordinates.message.orEmpty().contains("glyf glyph 9"))
    }

    @Test
    fun parsedTrueTypeGlyphScalerReturnsScaledOutlineAndMetricsForSimpleGlyph() {
        val simpleSquare = simpleSquareGlyphData()
        val scaler = ParsedTrueTypeGlyphScaler(
            glyfTable = simpleSquare,
            loca = TrueTypeLocaTable(offsets = listOf(0, simpleSquare.size, simpleSquare.size)),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
                1u to TrueTypeGlyphHorizontalMetrics(advanceX = 500.0, leftSideBearing = 0.0),
            ),
            scale = 0.5,
        )

        assertEquals(
            GlyphOutline(
                glyphId = 0u,
                commands = listOf(
                    moveTo(0.0, 0.0),
                    lineTo(50.0, 0.0),
                    lineTo(50.0, 50.0),
                    lineTo(0.0, 50.0),
                    close(),
                ),
            ),
            scaler.outline(glyphId = 0u),
        )
        assertEquals(
            GlyphMetrics(
                advanceX = 300.0,
                advanceY = 0.0,
                bounds = GlyphBounds(left = 0.0, top = 0.0, right = 50.0, bottom = 50.0),
            ),
            scaler.metrics(glyphId = 0u),
        )
    }

    @Test
    fun parsedTrueTypeGlyphEvidenceDumpsSimpleGlyphDeterministically() {
        val simpleSquare = simpleSquareGlyphData()
        val scaler = ParsedTrueTypeGlyphScaler(
            glyfTable = simpleSquare,
            loca = TrueTypeLocaTable(offsets = listOf(0, simpleSquare.size, simpleSquare.size)),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
                1u to TrueTypeGlyphHorizontalMetrics(advanceX = 500.0, leftSideBearing = 0.0),
            ),
        )

        val evidence = scaler.scaledGlyphEvidence(glyphId = 0u)
        val repeated = scaler.scaledGlyphEvidence(glyphId = 0u)
        val dump = evidence.toCanonicalJson()

        assertEquals(evidence, repeated)
        assertEquals(dump, repeated.toCanonicalJson())
        assertEquals(0u, evidence.glyphId)
        assertEquals("truetype-glyf", evidence.scalerFamily)
        assertEquals("font.scaler.truetype-glyf", evidence.route)
        assertEquals(
            TrueTypeLocaRangeEvidence(
                start = 0,
                endExclusive = simpleSquare.size,
                byteLength = simpleSquare.size,
                isEmpty = false,
            ),
            evidence.locaRange,
        )
        assertEquals(emptyList(), evidence.requestedVariationPosition)
        assertEquals(emptyList(), evidence.normalizedVariationPosition)
        assertEquals(
            listOf(
                "M 0.0 0.0",
                "L 100.0 0.0",
                "L 100.0 100.0",
                "L 0.0 100.0",
                "Z",
            ),
            evidence.outlineCommands,
        )
        assertEquals(
            evidence.outlineCommandDump.sha256Hex(),
            evidence.outlineCommandDumpSha256,
        )
        assertEquals(GlyphBounds(left = 0.0, top = 0.0, right = 100.0, bottom = 100.0), evidence.conservativeBounds)
        assertEquals(
            GlyphMetrics(
                advanceX = 600.0,
                advanceY = 0.0,
                bounds = GlyphBounds(left = 0.0, top = 0.0, right = 100.0, bottom = 100.0),
            ),
            evidence.metrics,
        )
        assertEquals(emptyList(), evidence.diagnostics)
        assertTrue(!dump.contains("@"))
        assertTrue(!Regex("""\bSk[A-Za-z0-9_]*""").containsMatchIn(dump))
    }

    @Test
    fun parsedTrueTypeGlyphEvidenceSortsRequestedVariationAxesInDumps() {
        val simpleSquare = simpleSquareGlyphData()
        val scaler = ParsedTrueTypeGlyphScaler(
            glyfTable = simpleSquare,
            loca = TrueTypeLocaTable(offsets = listOf(0, simpleSquare.size, simpleSquare.size)),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
                1u to TrueTypeGlyphHorizontalMetrics(advanceX = 500.0, leftSideBearing = 0.0),
            ),
        )

        val evidence = scaler.scaledGlyphEvidence(
            glyphId = 0u,
            position = VariationPosition(
                axes = mapOf(
                    "wght" to 400.0,
                    "wdth" to 100.0,
                ),
            ),
        )
        val dump = evidence.toCanonicalJson()

        assertEquals(
            listOf(
                VariationCoordinateEvidence(tag = "wdth", value = 100.0),
                VariationCoordinateEvidence(tag = "wght", value = 400.0),
            ),
            evidence.requestedVariationPosition,
        )
        assertTrue(dump.indexOf("\"tag\": \"wdth\"") < dump.indexOf("\"tag\": \"wght\""))
        assertTrue(evidence.diagnostics.any { diagnostic ->
            diagnostic.code == FontScalerDiagnosticCodes.VARIATION_AXIS_UNSUPPORTED &&
                diagnostic.detail == "truetype.gvar-unavailable"
        })
    }

    @Test
    fun trueTypeGlyfEvidenceShowsRequestedAndNormalizedVariationFacts() {
        val simpleSquare = simpleSquareGlyphData().withTrueTypePadding()
        val scaler = TrueTypeGlyfScaler(
            face = syntheticTrueTypeFace(
                rawTables = mapOf(
                    SFNTTableTag("loca") to shortLocaForOffsets(0, simpleSquare.size, simpleSquare.size)
                        .toUnsignedByteList(),
                    SFNTTableTag("glyf") to simpleSquare.toUnsignedByteList(),
                    SFNTTableTag("gvar") to singleAxisGvarWithAllPointDelta().toUnsignedByteList(),
                ),
                variations = VariationTables(
                    axes = listOf(
                        variationAxis(tag = "wght", minimum = 100.0, defaultValue = 400.0, maximum = 900.0),
                    ),
                ),
            ),
        )

        val evidence = scaler.scaledGlyphEvidence(
            glyphId = 0u,
            position = VariationPosition(axes = mapOf("wght" to 900.0)),
        )
        val dump = evidence.toCanonicalJson()

        assertEquals(listOf(VariationCoordinateEvidence(tag = "wght", value = 900.0)), evidence.requestedVariationPosition)
        assertEquals(listOf(VariationCoordinateEvidence(tag = "wght", value = 1.0)), evidence.normalizedVariationPosition)
        assertEquals(
            listOf(
                "M 0.0 0.0",
                "L 100.0 0.0",
                "L 120.0 90.0",
                "L 0.0 100.0",
                "Z",
            ),
            evidence.outlineCommands,
        )
        assertTrue(evidence.diagnostics.any { diagnostic ->
            diagnostic.code == FontScalerDiagnosticCodes.METRICS_VARIATION_UNAVAILABLE
        })
        assertTrue(dump.indexOf("\"requestedVariationPosition\"") < dump.indexOf("\"normalizedVariationPosition\""))
        assertTrue(!dump.contains("@"))
        assertTrue(!Regex("""\bSk[A-Za-z0-9_]*""").containsMatchIn(dump))
    }

    @Test
    fun parsedTrueTypeGlyphEvidenceReportsPartialGvarIupGap() {
        val simpleSquare = simpleSquareGlyphData()
        val scaler = ParsedTrueTypeGlyphScaler(
            glyfTable = simpleSquare,
            loca = TrueTypeLocaTable(offsets = listOf(0, simpleSquare.size, simpleSquare.size)),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
                1u to TrueTypeGlyphHorizontalMetrics(advanceX = 500.0, leftSideBearing = 0.0),
            ),
            gvar = TrueTypeGvarTable.parse(
                data = singleAxisGvarWithPointDelta(),
                axisCount = 1,
                glyphCount = 2,
            ),
            normalizedAxisOrder = listOf("wght"),
        )

        val evidence = scaler.scaledGlyphEvidence(
            glyphId = 0u,
            position = VariationPosition(axes = mapOf("wght" to 1.0)),
        )

        assertEquals(
            listOf(
                "M 0.0 0.0",
                "L 100.0 0.0",
                "L 100.0 100.0",
                "L 0.0 100.0",
                "Z",
            ),
            evidence.outlineCommands,
        )
        assertTrue(evidence.diagnostics.any { diagnostic ->
            diagnostic.code == FontScalerDiagnosticCodes.VARIATION_DATA_MALFORMED &&
                diagnostic.detail == "truetype.gvar-iup-unavailable" &&
                diagnostic.severity == "warning"
        })
        assertTrue(evidence.toCanonicalJson().contains("\"detail\": \"truetype.gvar-iup-unavailable\""))
    }

    @Test
    fun trueTypeGlyfEvidenceReportsUnknownRequestedAxisWithoutThrowing() {
        val simpleSquare = simpleSquareGlyphData().withTrueTypePadding()
        val scaler = variableTrueTypeGlyfScaler(simpleSquare = simpleSquare)

        val evidence = scaler.scaledGlyphEvidence(
            glyphId = 0u,
            position = VariationPosition(axes = mapOf("wdth" to 100.0)),
        )

        assertEquals(listOf(VariationCoordinateEvidence(tag = "wdth", value = 100.0)), evidence.requestedVariationPosition)
        assertEquals(emptyList(), evidence.normalizedVariationPosition)
        assertEquals(
            listOf(
                "M 0.0 0.0",
                "L 100.0 0.0",
                "L 100.0 100.0",
                "L 0.0 100.0",
                "Z",
            ),
            evidence.outlineCommands,
        )
        assertTrue(evidence.diagnostics.any { diagnostic ->
            diagnostic.code == FontScalerDiagnosticCodes.VARIATION_AXIS_UNSUPPORTED &&
                diagnostic.detail == "truetype.gvar-axis"
        })
    }

    @Test
    fun trueTypeGlyfEvidenceReportsNonFiniteRequestedAxisWithoutThrowing() {
        val simpleSquare = simpleSquareGlyphData().withTrueTypePadding()
        val scaler = variableTrueTypeGlyfScaler(simpleSquare = simpleSquare)

        val evidence = scaler.scaledGlyphEvidence(
            glyphId = 0u,
            position = VariationPosition(axes = mapOf("wght" to Double.NaN)),
        )

        assertEquals(emptyList(), evidence.requestedVariationPosition)
        assertEquals(emptyList(), evidence.normalizedVariationPosition)
        assertEquals(
            listOf(
                "M 0.0 0.0",
                "L 100.0 0.0",
                "L 100.0 100.0",
                "L 0.0 100.0",
                "Z",
            ),
            evidence.outlineCommands,
        )
        assertTrue(evidence.diagnostics.any { diagnostic ->
            diagnostic.code == FontScalerDiagnosticCodes.VARIATION_DATA_MALFORMED &&
                diagnostic.detail == "truetype.variation-position-non-finite"
        })
    }

    @Test
    fun trueTypeGlyfEvidenceReportsAvarRemappingNotApplied() {
        val simpleSquare = simpleSquareGlyphData().withTrueTypePadding()
        val scaler = variableTrueTypeGlyfScaler(
            simpleSquare = simpleSquare,
            variations = VariationTables(
                axes = listOf(
                    variationAxis(tag = "wght", minimum = 100.0, defaultValue = 400.0, maximum = 900.0),
                ),
                axisSegmentMaps = listOf(
                    OpenTypeAvarAxisSegmentMap(
                        segments = listOf(
                            OpenTypeAvarSegment(fromCoordinate = -1.0, toCoordinate = -1.0),
                            OpenTypeAvarSegment(fromCoordinate = 0.0, toCoordinate = 0.0),
                            OpenTypeAvarSegment(fromCoordinate = 1.0, toCoordinate = 0.75),
                        ),
                    ),
                ),
            ),
        )

        val evidence = scaler.scaledGlyphEvidence(
            glyphId = 0u,
            position = VariationPosition(axes = mapOf("wght" to 900.0)),
        )

        assertEquals(listOf(VariationCoordinateEvidence(tag = "wght", value = 1.0)), evidence.normalizedVariationPosition)
        assertTrue(evidence.diagnostics.any { diagnostic ->
            diagnostic.code == FontScalerDiagnosticCodes.VARIATION_AXIS_UNSUPPORTED &&
                diagnostic.detail == "truetype.avar-unapplied" &&
                diagnostic.severity == "warning"
        })
        assertTrue(evidence.toCanonicalJson().contains("\"detail\": \"truetype.avar-unapplied\""))
    }

    @Test
    fun parsedTrueTypeGlyphEvidenceReportsCompositePointMatchingRefusalCode() {
        val compositeGlyph = compositeGlyphData(
            *componentRecord(
                flags = 0x0001,
                glyphId = 1,
                arg1 = 0,
                arg2 = 1,
            ),
        )
        val simpleSquare = simpleSquareGlyphData()
        val scaler = ParsedTrueTypeGlyphScaler(
            glyfTable = compositeGlyph + simpleSquare,
            loca = TrueTypeLocaTable(offsets = listOf(0, compositeGlyph.size, compositeGlyph.size + simpleSquare.size)),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
                1u to TrueTypeGlyphHorizontalMetrics(advanceX = 500.0, leftSideBearing = 0.0),
            ),
        )

        val evidence = scaler.scaledGlyphEvidence(glyphId = 0u)

        assertEquals(emptyList(), evidence.outlineCommands)
        assertNull(evidence.conservativeBounds)
        assertEquals(
            listOf(
                FontScalerDiagnostic(
                    code = FontScalerDiagnosticCodes.OUTLINE_FORMAT_UNSUPPORTED,
                    detail = "truetype.composite-point-matching",
                    operation = "outline",
                    glyphId = 0u,
                    severity = "refusal",
                ),
            ),
            evidence.diagnostics,
        )
        assertTrue(evidence.toCanonicalJson().contains("\"code\": \"font.outline-format-unsupported\""))
        assertTrue(evidence.toCanonicalJson().contains("\"detail\": \"truetype.composite-point-matching\""))
    }

    @Test
    fun parsedTrueTypeGlyphScalerAppliesGvarDeltasToSimpleGlyphAtVariationPositionOnly() {
        val simpleSquare = simpleSquareGlyphData()
        val scaler = ParsedTrueTypeGlyphScaler(
            glyfTable = simpleSquare,
            loca = TrueTypeLocaTable(offsets = listOf(0, simpleSquare.size, simpleSquare.size)),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
                1u to TrueTypeGlyphHorizontalMetrics(advanceX = 500.0, leftSideBearing = 0.0),
            ),
            gvar = TrueTypeGvarTable.parse(
                data = singleAxisGvarWithAllPointDelta(),
                axisCount = 1,
                glyphCount = 2,
            ),
            normalizedAxisOrder = listOf("wght"),
        )

        assertEquals(
            listOf(
                moveTo(0.0, 0.0),
                lineTo(100.0, 0.0),
                lineTo(100.0, 100.0),
                lineTo(0.0, 100.0),
                close(),
            ),
            scaler.outline(glyphId = 0u).commands,
        )
        assertEquals(
            listOf(
                moveTo(0.0, 0.0),
                lineTo(100.0, 0.0),
                lineTo(120.0, 90.0),
                lineTo(0.0, 100.0),
                close(),
            ),
            scaler.outline(
                glyphId = 0u,
                position = VariationPosition(axes = mapOf("wght" to 1.0)),
            ).commands,
        )
    }

    @Test
    fun parsedTrueTypeGlyphScalerIgnoresGvarPartialPointDeltasUntilIupIsSupported() {
        val simpleSquare = simpleSquareGlyphData()
        val scaler = ParsedTrueTypeGlyphScaler(
            glyfTable = simpleSquare,
            loca = TrueTypeLocaTable(offsets = listOf(0, simpleSquare.size, simpleSquare.size)),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
                1u to TrueTypeGlyphHorizontalMetrics(advanceX = 500.0, leftSideBearing = 0.0),
            ),
            gvar = TrueTypeGvarTable.parse(
                data = singleAxisGvarWithPointDelta(),
                axisCount = 1,
                glyphCount = 2,
            ),
            normalizedAxisOrder = listOf("wght"),
        )

        assertEquals(
            listOf(
                moveTo(0.0, 0.0),
                lineTo(100.0, 0.0),
                lineTo(100.0, 100.0),
                lineTo(0.0, 100.0),
                close(),
            ),
            scaler.outline(
                glyphId = 0u,
                position = VariationPosition(axes = mapOf("wght" to 1.0)),
            ).commands,
        )
    }

    @Test
    fun trueTypeGvarTableRejectsMalformedTablesWithClearDiagnostics() {
        val failure = assertFailsWith<IllegalArgumentException> {
            TrueTypeGvarTable.parse(
                data = singleAxisGvarWithAllPointDelta().copyOf(10),
                axisCount = 1,
                glyphCount = 2,
            )
        }

        assertTrue(failure.message.orEmpty().contains("gvar"))
        assertTrue(failure.message.orEmpty().contains("header"))
    }

    @Test
    fun parsedTrueTypeGlyphScalerReturnsEmptyOutlineAndMetricsForEmptyGlyph() {
        val simpleSquare = simpleSquareGlyphData()
        val scaler = ParsedTrueTypeGlyphScaler(
            glyfTable = simpleSquare,
            loca = TrueTypeLocaTable(offsets = listOf(0, simpleSquare.size, simpleSquare.size)),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
                1u to TrueTypeGlyphHorizontalMetrics(advanceX = 500.0, leftSideBearing = 0.0),
            ),
        )

        assertEquals(GlyphOutline(glyphId = 1u), scaler.outline(glyphId = 1u))
        assertEquals(
            GlyphMetrics(
                advanceX = 500.0,
                advanceY = 0.0,
                bounds = GlyphBounds(left = 0.0, top = 0.0, right = 0.0, bottom = 0.0),
            ),
            scaler.metrics(glyphId = 1u),
        )
    }

    @Test
    fun parsedTrueTypeGlyphScalerResolvesCompositeGlyphOutlinesRecursively() {
        val compositeGlyph = compositeGlyphData(
            *componentRecord(
                flags = 0x002b,
                glyphId = 1,
                arg1 = 10,
                arg2 = 20,
                transformWords = intArrayOf(0x2000),
            ),
            *componentRecord(
                flags = 0x0043,
                glyphId = 2,
                arg1 = 0,
                arg2 = 10,
                transformWords = intArrayOf(0x6000, 0x4000),
            ),
        )
        val simpleSquare = simpleSquareGlyphData()
        val nestedComposite = compositeGlyphData(
            *componentRecord(
                flags = 0x0003,
                glyphId = 1,
                arg1 = 100,
                arg2 = 0,
            ),
        )
        val glyfTable = compositeGlyph + simpleSquare + nestedComposite
        val scaler = ParsedTrueTypeGlyphScaler(
            glyfTable = glyfTable,
            loca = TrueTypeLocaTable(
                offsets = listOf(
                    0,
                    compositeGlyph.size,
                    compositeGlyph.size + simpleSquare.size,
                    glyfTable.size,
                ),
            ),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
                1u to TrueTypeGlyphHorizontalMetrics(advanceX = 500.0, leftSideBearing = 0.0),
                2u to TrueTypeGlyphHorizontalMetrics(advanceX = 500.0, leftSideBearing = 0.0),
            ),
        )

        assertEquals(
            GlyphOutline(
                glyphId = 0u,
                commands = listOf(
                    moveTo(10.0, 20.0),
                    lineTo(60.0, 20.0),
                    lineTo(60.0, 70.0),
                    lineTo(10.0, 70.0),
                    close(),
                    moveTo(150.0, 10.0),
                    lineTo(300.0, 10.0),
                    lineTo(300.0, 110.0),
                    lineTo(150.0, 110.0),
                    close(),
                ),
            ),
            scaler.outline(glyphId = 0u),
        )
    }

    @Test
    fun parsedTrueTypeGlyphScalerUsesOpenTypeTwoByTwoCoefficientOrder() {
        val compositeGlyph = compositeGlyphData(
            *componentRecord(
                flags = 0x0083,
                glyphId = 1,
                arg1 = 0,
                arg2 = 0,
                transformWords = intArrayOf(
                    0x4000,
                    0x2000,
                    0x1000,
                    0x4000,
                ),
            ),
        )
        val simpleSquare = simpleSquareGlyphData()
        val glyfTable = compositeGlyph + simpleSquare
        val scaler = ParsedTrueTypeGlyphScaler(
            glyfTable = glyfTable,
            loca = TrueTypeLocaTable(offsets = listOf(0, compositeGlyph.size, glyfTable.size)),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
                1u to TrueTypeGlyphHorizontalMetrics(advanceX = 500.0, leftSideBearing = 0.0),
            ),
        )

        assertEquals(
            GlyphOutline(
                glyphId = 0u,
                commands = listOf(
                    moveTo(0.0, 0.0),
                    lineTo(100.0, 50.0),
                    lineTo(125.0, 150.0),
                    lineTo(25.0, 100.0),
                    close(),
                ),
            ),
            scaler.outline(glyphId = 0u),
        )
    }

    @Test
    fun parsedTrueTypeGlyphScalerAppliesScaledComponentOffsetsOnlyWhenRequested() {
        val scaledOffsetComposite = compositeGlyphData(
            *componentRecord(
                flags = 0x080b,
                glyphId = 2,
                arg1 = 10,
                arg2 = 20,
                transformWords = intArrayOf(0x2000),
            ),
        )
        val unscaledOffsetComposite = compositeGlyphData(
            *componentRecord(
                flags = 0x100b,
                glyphId = 2,
                arg1 = 10,
                arg2 = 20,
                transformWords = intArrayOf(0x2000),
            ),
        )
        val simpleSquare = simpleSquareGlyphData()
        val glyfTable = scaledOffsetComposite + unscaledOffsetComposite + simpleSquare
        val scaler = ParsedTrueTypeGlyphScaler(
            glyfTable = glyfTable,
            loca = TrueTypeLocaTable(
                offsets = listOf(
                    0,
                    scaledOffsetComposite.size,
                    scaledOffsetComposite.size + unscaledOffsetComposite.size,
                    glyfTable.size,
                ),
            ),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
                1u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
                2u to TrueTypeGlyphHorizontalMetrics(advanceX = 500.0, leftSideBearing = 0.0),
            ),
        )

        assertEquals(moveTo(5.0, 10.0), scaler.outline(glyphId = 0u).commands.first())
        assertEquals(moveTo(10.0, 20.0), scaler.outline(glyphId = 1u).commands.first())
    }

    @Test
    fun parsedTrueTypeGlyphScalerRejectsCompositePointMatchingArgumentsExplicitly() {
        val compositeGlyph = compositeGlyphData(
            *componentRecord(
                flags = 0x0001,
                glyphId = 1,
                arg1 = 0,
                arg2 = 1,
            ),
        )
        val simpleSquare = simpleSquareGlyphData()
        val glyfTable = compositeGlyph + simpleSquare
        val scaler = ParsedTrueTypeGlyphScaler(
            glyfTable = glyfTable,
            loca = TrueTypeLocaTable(offsets = listOf(0, compositeGlyph.size, glyfTable.size)),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
                1u to TrueTypeGlyphHorizontalMetrics(advanceX = 500.0, leftSideBearing = 0.0),
            ),
        )

        val failure = assertFailsWith<UnsupportedOperationException> {
            scaler.outline(glyphId = 0u)
        }

        assertTrue(failure.message.orEmpty().contains("point-matching"))
        assertTrue(failure.message.orEmpty().contains("glyphId 0"))
    }

    @Test
    fun parsedTrueTypeGlyphScalerStopsRecursiveCompositeResolutionAtDepthCap() {
        val compositeGlyph = compositeGlyphData(
            *componentRecord(
                flags = 0x0003,
                glyphId = 0,
                arg1 = 0,
                arg2 = 0,
            ),
        )
        val scaler = ParsedTrueTypeGlyphScaler(
            glyfTable = compositeGlyph,
            loca = TrueTypeLocaTable(offsets = listOf(0, compositeGlyph.size)),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
            ),
        )

        val failure = assertFailsWith<UnsupportedOperationException> {
            scaler.outline(glyphId = 0u)
        }

        assertTrue(failure.message.orEmpty().contains("depth"))
        assertTrue(failure.message.orEmpty().contains("glyphId 0"))
    }

    @Test
    fun parsedTrueTypeGlyphScalerRejectsOutOfLocaGlyphIdsAndMissingMetrics() {
        val simpleSquare = simpleSquareGlyphData()
        val scaler = ParsedTrueTypeGlyphScaler(
            glyfTable = simpleSquare,
            loca = TrueTypeLocaTable(offsets = listOf(0, simpleSquare.size, simpleSquare.size)),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
            ),
        )

        val outOfLoca = assertFailsWith<IllegalArgumentException> {
            scaler.outline(glyphId = 2u)
        }
        val missingMetrics = assertFailsWith<IllegalArgumentException> {
            scaler.metrics(glyphId = 1u)
        }

        assertTrue(outOfLoca.message.orEmpty().contains("outside loca table glyph count 2"))
        assertTrue(missingMetrics.message.orEmpty().contains("horizontal metrics missing for glyphId 1"))
    }

    @Test
    fun trueTypeGlyphScalerFactoryBuildsScalerFromSfntMetricsAndRawLocaGlyf() {
        val simpleSquare = simpleSquareGlyphData().withTrueTypePadding()
        val scaler = TrueTypeGlyphScalerFactory.create(
            metrics = MetricsTables(
                unitsPerEm = 1000,
                indexToLocFormat = 0,
                numGlyphs = 2,
                horizontalMetrics = listOf(
                    HorizontalGlyphMetric(glyphId = 0, advanceWidth = 600, leftSideBearing = 20),
                    HorizontalGlyphMetric(glyphId = 1, advanceWidth = 500, leftSideBearing = 0),
                ),
            ),
            locaTable = shortLocaForOffsets(0, simpleSquare.size, simpleSquare.size),
            glyfTable = simpleSquare,
            pixelSize = 20.0,
        )

        assertEquals(
            listOf(
                moveTo(0.0, 0.0),
                lineTo(2.0, 0.0),
                lineTo(2.0, 2.0),
                lineTo(0.0, 2.0),
                close(),
            ),
            scaler.outline(glyphId = 0u).commands,
        )
        assertEquals(
            GlyphMetrics(
                advanceX = 12.0,
                advanceY = 0.0,
                bounds = GlyphBounds(left = 0.0, top = 0.0, right = 2.0, bottom = 2.0),
            ),
            scaler.metrics(glyphId = 0u),
        )
        assertEquals(10.0, scaler.metrics(glyphId = 1u).advanceX)
    }

    @Test
    fun trueTypeGlyphScalerFactoryRejectsMissingAndInvalidIndexToLocFormat() {
        val simpleSquare = simpleSquareGlyphData().withTrueTypePadding()
        val missing = assertFailsWith<IllegalArgumentException> {
            TrueTypeGlyphScalerFactory.create(
                metrics = sfntMetricsForFactory(indexToLocFormat = null),
                locaTable = shortLocaForOffsets(0, simpleSquare.size, simpleSquare.size),
                glyfTable = simpleSquare,
            )
        }
        val invalid = assertFailsWith<IllegalArgumentException> {
            TrueTypeGlyphScalerFactory.create(
                metrics = sfntMetricsForFactory(indexToLocFormat = 2),
                locaTable = shortLocaForOffsets(0, simpleSquare.size, simpleSquare.size),
                glyfTable = simpleSquare,
            )
        }

        assertTrue(missing.message.orEmpty().contains("indexToLocFormat is required"))
        assertTrue(invalid.message.orEmpty().contains("indexToLocFormat 2 must be 0 or 1"))
    }

    @Test
    fun trueTypeGlyphScalerFactoryRejectsIncompleteHorizontalMetrics() {
        val simpleSquare = simpleSquareGlyphData().withTrueTypePadding()
        val failure = assertFailsWith<IllegalArgumentException> {
            TrueTypeGlyphScalerFactory.create(
                metrics = sfntMetricsForFactory(
                    horizontalMetrics = listOf(
                        HorizontalGlyphMetric(glyphId = 0, advanceWidth = 600, leftSideBearing = 20),
                    ),
                ),
                locaTable = shortLocaForOffsets(0, simpleSquare.size, simpleSquare.size),
                glyfTable = simpleSquare,
            )
        }

        assertTrue(failure.message.orEmpty().contains("horizontal metrics missing for glyphId 1"))
    }

    @Test
    fun trueTypeGlyphScalerFactoryRejectsInvalidUnitsPerEmAndIncoherentLocaGlyf() {
        val simpleSquare = simpleSquareGlyphData().withTrueTypePadding()
        val invalidUnits = assertFailsWith<IllegalArgumentException> {
            TrueTypeGlyphScalerFactory.create(
                metrics = sfntMetricsForFactory(unitsPerEm = 0),
                locaTable = shortLocaForOffsets(0, simpleSquare.size, simpleSquare.size),
                glyfTable = simpleSquare,
            )
        }
        val incoherentGlyf = assertFailsWith<IllegalArgumentException> {
            TrueTypeGlyphScalerFactory.create(
                metrics = sfntMetricsForFactory(),
                locaTable = shortLocaForOffsets(0, simpleSquare.size + 2, simpleSquare.size + 2),
                glyfTable = simpleSquare,
            )
        }

        assertTrue(invalidUnits.message.orEmpty().contains("unitsPerEm must be positive"))
        assertTrue(incoherentGlyf.message.orEmpty().contains("exceeds glyf table length"))
    }

    @Test
    fun trueTypeGlyphScalerFactoryRejectsNonPositivePixelSize() {
        val simpleSquare = simpleSquareGlyphData().withTrueTypePadding()

        val zero = assertFailsWith<IllegalArgumentException> {
            TrueTypeGlyphScalerFactory.create(
                metrics = sfntMetricsForFactory(),
                locaTable = shortLocaForOffsets(0, simpleSquare.size, simpleSquare.size),
                glyfTable = simpleSquare,
                pixelSize = 0.0,
            )
        }
        val negative = assertFailsWith<IllegalArgumentException> {
            TrueTypeGlyphScalerFactory.create(
                metrics = sfntMetricsForFactory(),
                locaTable = shortLocaForOffsets(0, simpleSquare.size, simpleSquare.size),
                glyfTable = simpleSquare,
                pixelSize = -1.0,
            )
        }

        assertTrue(zero.message.orEmpty().contains("pixelSize"))
        assertTrue(negative.message.orEmpty().contains("pixelSize"))
    }

    @Test
    fun trueTypeGlyfScalerBuildsFromOpenTypeFaceRawTables() {
        val simpleSquare = simpleSquareGlyphData().withTrueTypePadding()
        val scaler = TrueTypeGlyfScaler(
            face = syntheticTrueTypeFace(
                rawTables = mapOf(
                    SFNTTableTag("loca") to shortLocaForOffsets(0, simpleSquare.size, simpleSquare.size)
                        .toUnsignedByteList(),
                    SFNTTableTag("glyf") to simpleSquare.toUnsignedByteList(),
                ),
            ),
        )

        assertEquals(
            listOf(
                moveTo(0.0, 0.0),
                lineTo(100.0, 0.0),
                lineTo(100.0, 100.0),
                lineTo(0.0, 100.0),
                close(),
            ),
            scaler.outline(glyphId = 0u).commands,
        )
        assertEquals(
            GlyphMetrics(
                advanceX = 600.0,
                advanceY = 0.0,
                bounds = GlyphBounds(left = 0.0, top = 0.0, right = 100.0, bottom = 100.0),
            ),
            scaler.metrics(glyphId = 0u),
        )
    }

    @Test
    fun trueTypeGlyfScalerRejectsMissingTrueTypeRawTablesClearly() {
        val failure = assertFailsWith<IllegalArgumentException> {
            TrueTypeGlyfScaler(
                face = syntheticTrueTypeFace(rawTables = emptyMap()),
            ).outline(glyphId = 0u)
        }

        assertTrue(failure.message.orEmpty().contains("loca"))
        assertTrue(failure.message.orEmpty().contains("glyf"))
    }

    @Test
    fun cffScalersRefuseUntilType2CharstringsAreImplemented() {
        val cffOutlineFailure = assertFailsWith<UnsupportedOperationException> {
            CFFScaler(syntheticTrueTypeFace(rawTables = emptyMap())).outline(glyphId = 1u)
        }
        val cffMetricsFailure = assertFailsWith<UnsupportedOperationException> {
            CFFScaler(syntheticTrueTypeFace(rawTables = emptyMap())).metrics(glyphId = 1u)
        }
        val cff2OutlineFailure = assertFailsWith<UnsupportedOperationException> {
            CFF2Scaler(syntheticTrueTypeFace(rawTables = emptyMap())).outline(glyphId = 1u)
        }
        val cff2MetricsFailure = assertFailsWith<UnsupportedOperationException> {
            CFF2Scaler(syntheticTrueTypeFace(rawTables = emptyMap())).metrics(glyphId = 1u)
        }

        listOf(cffOutlineFailure, cffMetricsFailure, cff2OutlineFailure, cff2MetricsFailure).forEach { failure ->
            assertTrue(failure.message.orEmpty().contains("Type 2 charstring"))
            assertTrue(failure.message.orEmpty().contains("glyphId 1"))
        }
    }

    @Test
    fun scalerReadinessFixturesAreVendoredReadableAndNonClaiming() {
        val fixtureRoot = kanvasProjectRoot().resolve("reports/font/fixtures")
        val robotoFlexBytes = Files.readAllBytes(fixtureRoot.resolve("fonts/scaler/RobotoFlex-Variable.ttf"))
        val sourceSerifBytes = Files.readAllBytes(fixtureRoot.resolve("fonts/scaler/SourceSerif4-Regular.otf"))
        val trueTypeReadiness = Files.readString(
            fixtureRoot.resolve("expected/scaler/truetype-variation-readiness.json"),
        )
        val cffReadiness = Files.readString(
            fixtureRoot.resolve("expected/scaler/cff-cff2-readiness.json"),
        )

        assertTrue(robotoFlexBytes.isNotEmpty())
        assertTrue(sourceSerifBytes.isNotEmpty())
        assertTrue(trueTypeReadiness.contains("\"dumpId\": \"truetype-variation-readiness\""))
        assertTrue(trueTypeReadiness.contains("\"scaler-roboto-flex-variable\""))
        assertTrue(trueTypeReadiness.contains("\"no-full-variable-font-support-claim\""))
        assertTrue(trueTypeReadiness.contains("\"no-native-scaler-oracle-claim\""))
        assertTrue(cffReadiness.contains("\"dumpId\": \"cff-cff2-readiness\""))
        assertTrue(cffReadiness.contains("\"scaler-source-serif-cff\""))
        assertTrue(cffReadiness.contains("\"no-cff-rendering-support-claim\""))
        assertTrue(cffReadiness.contains("\"no-cff2-variation-support-claim\""))
        assertTrue(cffReadiness.contains("\"no-native-scaler-oracle-claim\""))
    }

    private fun kanvasProjectRoot(): Path {
        var current = Path.of("").toAbsolutePath()
        while (current.parent != null) {
            if (Files.isDirectory(current.resolve("reports/font/fixtures"))) {
                return current
            }
            current = current.parent
        }
        error("Unable to locate Kanvas project root from ${Path.of("").toAbsolutePath()}")
    }

    private fun simpleSquareGlyphData(): ByteArray = bytes(
        0x00, 0x01,
        0x00, 0x00,
        0x00, 0x00,
        0x00, 0x64,
        0x00, 0x64,
        0x00, 0x03,
        0x00, 0x00,
        0x31,
        0x33,
        0x35,
        0x23,
        0x64,
        0x64,
        0x64,
    )

    private fun simpleRepeatedFlagGlyphData(): ByteArray = bytes(
        0x00, 0x01,
        0x00, 0x00,
        0x00, 0x00,
        0x00, 0x64,
        0x00, 0x28,
        0x00, 0x03,
        0x00, 0x00,
        0x31,
        0x3b, 0x01,
        0x35,
        0x32, 0x32,
        0x28,
    )

    private fun simpleQuadraticGlyphData(): ByteArray = bytes(
        0x00, 0x01,
        0x00, 0x00,
        0x00, 0x00,
        0x00, 0x64,
        0x00, 0x64,
        0x00, 0x03,
        0x00, 0x00,
        0x31,
        0x33,
        0x34,
        0x23,
        0x64, 0x64,
        0x64,
    )

    private fun singleAxisGvarWithPointDelta(): ByteArray = bytes(
        0x00, 0x01,
        0x00, 0x00,
        0x00, 0x01,
        0x00, 0x00,
        0x00, 0x00, 0x00, 0x14,
        0x00, 0x02,
        0x00, 0x01,
        0x00, 0x00, 0x00, 0x20,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x11,
        0x00, 0x00, 0x00, 0x11,
        0x00, 0x01,
        0x00, 0x0a,
        0x00, 0x07,
        0xa0, 0x00,
        0x40, 0x00,
        0x01,
        0x00, 0x02,
        0x00, 0x14,
        0x00, 0xf6,
    )

    private fun singleAxisGvarWithAllPointDelta(): ByteArray = bytes(
        0x00, 0x01,
        0x00, 0x00,
        0x00, 0x01,
        0x00, 0x00,
        0x00, 0x00, 0x00, 0x14,
        0x00, 0x02,
        0x00, 0x01,
        0x00, 0x00, 0x00, 0x20,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x1c,
        0x00, 0x00, 0x00, 0x1c,
        0x00, 0x01,
        0x00, 0x0a,
        0x00, 0x12,
        0x80, 0x00,
        0x40, 0x00,
        0x07,
        0x00, 0x00, 0x14, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x07,
        0x00, 0x00, 0xf6, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    private fun compositeGlyphData(vararg componentBytes: Int): ByteArray = bytes(
        0xff, 0xff,
        0x00, 0x00,
        0x00, 0x00,
        0x00, 0x10,
        0x00, 0x10,
        *componentBytes,
    )

    private fun componentRecord(
        flags: Int,
        glyphId: Int,
        arg1: Int,
        arg2: Int,
        transformWords: IntArray = intArrayOf(),
    ): IntArray {
        val values = mutableListOf<Int>()
        fun addWord(value: Int) {
            values += (value ushr 8) and 0xff
            values += value and 0xff
        }
        addWord(flags)
        addWord(glyphId)
        addWord(arg1)
        addWord(arg2)
        transformWords.forEach(::addWord)
        return values.toIntArray()
    }

    private fun ByteArray.withTrueTypePadding(): ByteArray =
        if (size % 2 == 0) this else this + 0x00.toByte()

    private fun sfntMetricsForFactory(
        unitsPerEm: Int? = 1000,
        indexToLocFormat: Int? = 0,
        horizontalMetrics: List<HorizontalGlyphMetric> = listOf(
            HorizontalGlyphMetric(glyphId = 0, advanceWidth = 600, leftSideBearing = 20),
            HorizontalGlyphMetric(glyphId = 1, advanceWidth = 500, leftSideBearing = 0),
        ),
    ): MetricsTables = MetricsTables(
        unitsPerEm = unitsPerEm,
        indexToLocFormat = indexToLocFormat,
        numGlyphs = 2,
        horizontalMetrics = horizontalMetrics,
    )

    private fun syntheticTrueTypeFace(
        rawTables: Map<SFNTTableTag, List<Int>>,
        metrics: MetricsTables = sfntMetricsForFactory(),
        variations: VariationTables = VariationTables(),
    ): OpenTypeFaceData = OpenTypeFaceData(
        id = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440201")),
        source = FontSource(
            id = FontSourceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440200")),
            kind = FontSourceKind.MEMORY,
            displayName = "Synthetic TrueType",
            bytes = ByteArray(0),
        ),
        directory = SFNTTableDirectory(scalerType = 0x00010000u),
        metrics = metrics,
        variations = variations,
        rawTables = rawTables,
    )

    private fun variableTrueTypeGlyfScaler(
        simpleSquare: ByteArray,
        variations: VariationTables = VariationTables(
            axes = listOf(
                variationAxis(tag = "wght", minimum = 100.0, defaultValue = 400.0, maximum = 900.0),
            ),
        ),
    ): TrueTypeGlyfScaler = TrueTypeGlyfScaler(
        face = syntheticTrueTypeFace(
            rawTables = mapOf(
                SFNTTableTag("loca") to shortLocaForOffsets(0, simpleSquare.size, simpleSquare.size)
                    .toUnsignedByteList(),
                SFNTTableTag("glyf") to simpleSquare.toUnsignedByteList(),
                SFNTTableTag("gvar") to singleAxisGvarWithAllPointDelta().toUnsignedByteList(),
            ),
            variations = variations,
        ),
    )

    private fun variationAxis(
        tag: String,
        minimum: Double,
        defaultValue: Double,
        maximum: Double,
    ): OpenTypeVariationAxis = OpenTypeVariationAxis(
        tag = OpenTypeVariationAxisTag(text = tag, rawValue = tag.toOpenTypeTagValue()),
        minimum = OpenTypeFixed16Dot16(minimum.toFixed16Dot16()),
        defaultValue = OpenTypeFixed16Dot16(defaultValue.toFixed16Dot16()),
        maximum = OpenTypeFixed16Dot16(maximum.toFixed16Dot16()),
        flags = 0,
        nameId = 256,
    )

    private fun shortLocaForOffsets(vararg offsets: Int): ByteArray {
        require(offsets.all { it % 2 == 0 })
        return bytes(
            *offsets.flatMap { offset ->
                val encoded = offset / 2
                listOf((encoded ushr 8) and 0xff, encoded and 0xff)
            }.toIntArray(),
        )
    }

    private fun ByteArray.toUnsignedByteList(): List<Int> = map { it.toInt() and 0xff }

    private fun String.toOpenTypeTagValue(): Int {
        require(length == 4)
        return fold(0) { accumulator, character ->
            (accumulator shl 8) or (character.code and 0xff)
        }
    }

    private fun Double.toFixed16Dot16(): Int = (this * 65536.0).toInt()

    private fun String.sha256Hex(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun bytes(vararg values: Int): ByteArray = values.map { it.toByte() }.toByteArray()
}
