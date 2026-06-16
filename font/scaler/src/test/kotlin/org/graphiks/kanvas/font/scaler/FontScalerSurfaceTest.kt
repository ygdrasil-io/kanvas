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
    fun trueTypeGlyfEvidenceAppliesAvarCoordinateMappingFixture() {
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

        assertEquals(listOf(VariationCoordinateEvidence(tag = "wght", value = 0.75)), evidence.normalizedVariationPosition)
        assertTrue(evidence.diagnostics.none { diagnostic -> diagnostic.detail == "truetype.avar-unapplied" })
    }

    @Test
    fun parsedTrueTypeGlyphEvidenceReportsInvalidCompositeGlyphPointIndexRefusalCode() {
        val compositeGlyph = compositeGlyphData(
            *componentRecord(
                flags = 0x0001,
                glyphId = 1,
                arg1 = 4,
                arg2 = 0,
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
                    detail = "truetype.composite-point-index",
                    operation = "outline",
                    glyphId = 0u,
                    severity = "refusal",
                ),
            ),
            evidence.diagnostics,
        )
        assertTrue(evidence.toCanonicalJson().contains("\"code\": \"font.outline-format-unsupported\""))
        assertTrue(evidence.toCanonicalJson().contains("\"detail\": \"truetype.composite-point-index\""))
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
    fun parsedTrueTypeGlyphScalerAppliesGvarDeltasToCompositeComponents() {
        val compositeGlyph = compositeGlyphData(
            *componentRecord(
                flags = 0x0003,
                glyphId = 1,
                arg1 = 0,
                arg2 = 0,
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
            gvar = TrueTypeGvarTable.parse(
                data = singleAxisGvarWithGlyphOneAllPointDelta(),
                axisCount = 1,
                glyphCount = 2,
            ),
            normalizedAxisOrder = listOf("wght"),
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
    fun parsedTrueTypeGlyphEvidenceIncludesCompositeComponentTrace() {
        val compositeGlyph = compositeGlyphData(
            *componentRecord(
                flags = 0x002b,
                glyphId = 1,
                arg1 = 10,
                arg2 = 20,
                transformWords = intArrayOf(0x2000),
            ),
            *componentRecord(
                flags = 0x0003,
                glyphId = 2,
                arg1 = 30,
                arg2 = 40,
            ),
        )
        val nestedComposite = compositeGlyphData(
            *componentRecord(
                flags = 0x0003,
                glyphId = 1,
                arg1 = 5,
                arg2 = 6,
            ),
        )
        val simpleSquare = simpleSquareGlyphData()
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

        val evidence = scaler.scaledGlyphEvidence(glyphId = 0u)

        assertEquals(
            listOf(
                TrueTypeCompositeComponentEvidence(
                    depth = 0,
                    parentGlyphId = 0u,
                    componentIndex = 0,
                    componentGlyphId = 1u,
                    flags = "0x002b",
                    argumentKind = "xy",
                    argument1 = 10,
                    argument2 = 20,
                    xx = 0.5,
                    xy = 0.0,
                    yx = 0.0,
                    yy = 0.5,
                    dx = 10.0,
                    dy = 20.0,
                ),
                TrueTypeCompositeComponentEvidence(
                    depth = 0,
                    parentGlyphId = 0u,
                    componentIndex = 1,
                    componentGlyphId = 2u,
                    flags = "0x0003",
                    argumentKind = "xy",
                    argument1 = 30,
                    argument2 = 40,
                    xx = 1.0,
                    xy = 0.0,
                    yx = 0.0,
                    yy = 1.0,
                    dx = 30.0,
                    dy = 40.0,
                ),
                TrueTypeCompositeComponentEvidence(
                    depth = 1,
                    parentGlyphId = 2u,
                    componentIndex = 0,
                    componentGlyphId = 1u,
                    flags = "0x0003",
                    argumentKind = "xy",
                    argument1 = 5,
                    argument2 = 6,
                    xx = 1.0,
                    xy = 0.0,
                    yx = 0.0,
                    yy = 1.0,
                    dx = 5.0,
                    dy = 6.0,
                ),
            ),
            evidence.compositeComponents,
        )
        assertTrue(evidence.toCanonicalJson().contains("\"compositeComponents\""))
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
    fun parsedTrueTypeGlyphScalerUsesFirstCompositeGlyphUseMyMetricsSource() {
        val compositeGlyph = compositeGlyphData(
            *componentRecord(
                flags = 0x0223,
                glyphId = 1,
                arg1 = 10,
                arg2 = 20,
            ),
            *componentRecord(
                flags = 0x0203,
                glyphId = 2,
                arg1 = 30,
                arg2 = 40,
            ),
        )
        val simpleSquare = simpleSquareGlyphData()
        val glyfTable = compositeGlyph + simpleSquare + simpleSquare
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
                2u to TrueTypeGlyphHorizontalMetrics(advanceX = 700.0, leftSideBearing = 30.0),
            ),
        )

        assertEquals(
            GlyphMetrics(
                advanceX = 500.0,
                advanceY = 0.0,
                bounds = GlyphBounds(left = 0.0, top = 0.0, right = 16.0, bottom = 16.0),
            ),
            scaler.metrics(glyphId = 0u),
        )
        assertEquals(500.0, scaler.scaledGlyphEvidence(glyphId = 0u).metrics?.advanceX)
    }

    @Test
    fun parsedTrueTypeGlyphScalerAlignsCompositeGlyphComponentPoints() {
        val compositeGlyph = compositeGlyphData(
            *componentRecord(
                flags = 0x0023,
                glyphId = 1,
                arg1 = 50,
                arg2 = 50,
            ),
            *componentRecord(
                flags = 0x0001,
                glyphId = 2,
                arg1 = 1,
                arg2 = 0,
            ),
        )
        val simpleSquare = simpleSquareGlyphData()
        val glyfTable = compositeGlyph + simpleSquare + simpleSquare
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
                    moveTo(50.0, 50.0),
                    lineTo(150.0, 50.0),
                    lineTo(150.0, 150.0),
                    lineTo(50.0, 150.0),
                    close(),
                    moveTo(150.0, 50.0),
                    lineTo(250.0, 50.0),
                    lineTo(250.0, 150.0),
                    lineTo(150.0, 150.0),
                    close(),
                ),
            ),
            scaler.outline(glyphId = 0u),
        )
    }

    @Test
    fun parsedTrueTypeGlyphScalerRejectsInvalidCompositeGlyphPointIndicesExplicitly() {
        val compositeGlyph = compositeGlyphData(
            *componentRecord(
                flags = 0x0001,
                glyphId = 1,
                arg1 = 4,
                arg2 = 0,
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

        assertTrue(failure.message.orEmpty().contains("point index"))
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
    fun parsedTrueTypeGlyphScalerRejectsCompositeGlyphsAboveComponentCountCap() {
        val compositeGlyph = excessiveCompositeComponentGlyphData()
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

        val failure = assertFailsWith<FontScalerRefusalException> {
            scaler.outline(glyphId = 0u)
        }
        val evidence = scaler.scaledGlyphEvidence(glyphId = 0u)

        assertEquals(FontScalerDiagnosticCodes.OUTLINE_FORMAT_UNSUPPORTED, failure.diagnostic.code)
        assertEquals("truetype.composite-component-count", failure.diagnostic.detail)
        assertTrue(failure.message.orEmpty().contains("component count"))
        assertTrue(failure.message.orEmpty().contains("glyphId 0"))
        assertTrue(evidence.diagnostics.any { diagnostic ->
            diagnostic.code == FontScalerDiagnosticCodes.OUTLINE_FORMAT_UNSUPPORTED &&
                diagnostic.detail == "truetype.composite-component-count"
        })
        assertTrue(evidence.toCanonicalJson().contains("\"detail\": \"truetype.composite-component-count\""))
    }

    @Test
    fun parsedTrueTypeGlyphEvidenceReportsCompositeGlyphCycleAndInvalidComponentDiagnostics() {
        val cyclicComposite = compositeGlyphData(
            *componentRecord(
                flags = 0x0003,
                glyphId = 0,
                arg1 = 0,
                arg2 = 0,
            ),
        )
        val cycleScaler = ParsedTrueTypeGlyphScaler(
            glyfTable = cyclicComposite,
            loca = TrueTypeLocaTable(offsets = listOf(0, cyclicComposite.size)),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
            ),
        )
        val invalidComponent = compositeGlyphData(
            *componentRecord(
                flags = 0x0003,
                glyphId = 2,
                arg1 = 0,
                arg2 = 0,
            ),
        )
        val invalidScaler = ParsedTrueTypeGlyphScaler(
            glyfTable = invalidComponent,
            loca = TrueTypeLocaTable(offsets = listOf(0, invalidComponent.size)),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
            ),
        )

        val cycleEvidence = cycleScaler.scaledGlyphEvidence(glyphId = 0u)
        val invalidEvidence = invalidScaler.scaledGlyphEvidence(glyphId = 0u)

        assertTrue(cycleEvidence.diagnostics.any { diagnostic ->
            diagnostic.code == FontScalerDiagnosticCodes.OUTLINE_FORMAT_UNSUPPORTED &&
                diagnostic.detail == "truetype.composite-recursion-depth"
        })
        assertTrue(invalidEvidence.diagnostics.any { diagnostic ->
            diagnostic.code == FontScalerDiagnosticCodes.OUTLINE_FORMAT_UNSUPPORTED &&
                diagnostic.detail == "truetype.composite-component-glyph-id"
        })
        assertTrue(cycleEvidence.toCanonicalJson().contains("\"detail\": \"truetype.composite-recursion-depth\""))
        assertTrue(invalidEvidence.toCanonicalJson().contains("\"detail\": \"truetype.composite-component-glyph-id\""))
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
    fun cffType2FixtureInterpreterBuildsLineCurveAndFlexEvidence() {
        val interpreter = CFFType2CharStringInterpreter()
        val charString = type2CharString(
            type2Number(100),
            type2Number(200),
            type2Operator(21),
            type2Number(50),
            type2Number(0),
            type2Number(0),
            type2Number(50),
            type2Operator(5),
            type2Number(10),
            type2Number(0),
            type2Number(20),
            type2Number(30),
            type2Number(40),
            type2Number(30),
            type2Operator(8),
            type2Number(5),
            type2Number(0),
            type2Number(10),
            type2Number(10),
            type2Number(15),
            type2Number(0),
            type2Number(15),
            type2Number(0),
            type2Number(10),
            type2Number(-10),
            type2Number(5),
            type2Number(0),
            type2Number(50),
            type2EscapedOperator(35),
            type2Operator(14),
        )

        val evidence = interpreter.interpretEvidence(
            charString = charString,
            glyphId = 3u,
            format = "cff",
        )

        assertEquals("cff", evidence.format)
        assertEquals(3u, evidence.glyphId)
        assertEquals(
            listOf(
                "M 100.0 200.0",
                "L 150.0 200.0",
                "L 150.0 250.0",
                "C 160.0 250.0 180.0 280.0 220.0 310.0",
                "C 225.0 310.0 235.0 320.0 250.0 320.0",
                "C 265.0 320.0 275.0 310.0 280.0 310.0",
                "Z",
            ),
            evidence.outlineCommands,
        )
        assertEquals(evidence.outlineCommands.joinToString("\n"), evidence.outlineCommandDump)
        assertEquals(evidence.outlineCommandDump.sha256Hex(), evidence.outlineCommandDumpSha256)
        assertEquals(emptyList(), evidence.callTrace)
        assertEquals(emptyList(), evidence.diagnostics)
        assertTrue(evidence.toCanonicalJson().contains("\"outlineCommandDumpSha256\""))
        assertTrue(!Regex("""\bSk[A-Za-z0-9_]*""").containsMatchIn(evidence.toCanonicalJson()))
    }

    @Test
    fun cffType2FixtureInterpreterTracesLocalAndGlobalSubroutines() {
        val interpreter = CFFType2CharStringInterpreter(
            localSubroutines = listOf(
                type2CharString(
                    type2Number(25),
                    type2Number(0),
                    type2Operator(5),
                    type2Operator(11),
                ),
            ),
            globalSubroutines = listOf(
                type2CharString(
                    type2Number(0),
                    type2Number(25),
                    type2Operator(5),
                    type2Operator(11),
                ),
            ),
        )
        val charString = type2CharString(
            type2Number(0),
            type2Number(0),
            type2Operator(21),
            type2Number(-107),
            type2Operator(10),
            type2Number(-107),
            type2Operator(29),
            type2Operator(14),
        )

        val evidence = interpreter.interpretEvidence(
            charString = charString,
            glyphId = 4u,
            format = "cff",
        )

        assertEquals(
            listOf(
                "M 0.0 0.0",
                "L 25.0 0.0",
                "L 25.0 25.0",
                "Z",
            ),
            evidence.outlineCommands,
        )
        assertEquals(
            listOf(
                CFFCharStringCallTrace(
                    depth = 0,
                    scope = "local",
                    encodedIndex = -107,
                    resolvedIndex = 0,
                ),
                CFFCharStringCallTrace(
                    depth = 0,
                    scope = "global",
                    encodedIndex = -107,
                    resolvedIndex = 0,
                ),
            ),
            evidence.callTrace,
        )
        assertTrue(evidence.toCanonicalJson().contains("\"callTrace\""))
    }

    @Test
    fun cff2FixtureInterpreterAppliesVsindexBlendEvidence() {
        val interpreter = CFFType2CharStringInterpreter(
            blendAxisTagsByVsIndex = mapOf(0 to listOf("wght")),
        )
        val charString = type2CharString(
            type2Number(0),
            type2Number(0),
            type2Operator(21),
            type2Number(0),
            type2Operator(15),
            type2Number(50),
            type2Number(10),
            type2Number(1),
            type2Operator(16),
            type2Number(0),
            type2Operator(5),
            type2Operator(14),
        )

        val evidence = interpreter.interpretEvidence(
            charString = charString,
            glyphId = 5u,
            format = "cff2",
            position = VariationPosition(axes = mapOf("wght" to 0.5)),
        )

        assertEquals(0, evidence.cff2VsIndex)
        assertEquals(listOf(VariationCoordinateEvidence(tag = "wght", value = 0.5)), evidence.variationPosition)
        assertEquals(
            listOf(
                "M 0.0 0.0",
                "L 55.0 0.0",
                "Z",
            ),
            evidence.outlineCommands,
        )
        assertTrue(evidence.toCanonicalJson().contains("\"format\": \"cff2\""))
        assertTrue(evidence.toCanonicalJson().contains("\"variationPosition\""))
    }

    @Test
    fun cffType2FixtureInterpreterReportsStackAndOperatorRefusals() {
        val interpreter = CFFType2CharStringInterpreter()

        val stackFailure = assertFailsWith<FontScalerRefusalException> {
            interpreter.interpretEvidence(
                charString = type2CharString(
                    type2Number(50),
                    type2Operator(21),
                ),
                glyphId = 6u,
                format = "cff",
            )
        }
        assertEquals(FontScalerDiagnosticCodes.CFF_STACK_MALFORMED, stackFailure.diagnostic.code)
        assertEquals("cff.stack-underflow", stackFailure.diagnostic.detail)
        assertEquals("charstring", stackFailure.diagnostic.operation)
        assertEquals(6u, stackFailure.diagnostic.glyphId)
        assertTrue(stackFailure.message.orEmpty().contains("operator offset"))

        val operatorFailure = assertFailsWith<FontScalerRefusalException> {
            interpreter.interpretEvidence(
                charString = type2CharString(type2EscapedOperator(0)),
                glyphId = 7u,
                format = "cff",
            )
        }
        assertEquals(FontScalerDiagnosticCodes.CFF_OPERATOR_UNSUPPORTED, operatorFailure.diagnostic.code)
        assertEquals("cff.escaped-operator-0", operatorFailure.diagnostic.detail)
        assertEquals("charstring", operatorFailure.diagnostic.operation)
        assertEquals(7u, operatorFailure.diagnostic.glyphId)
        assertTrue(operatorFailure.message.orEmpty().contains("operator offset"))
    }

    @Test
    fun cffType2FixtureInterpreterCoversRemainingCurveAndFlexOperators() {
        val interpreter = CFFType2CharStringInterpreter()

        assertEquals(
            listOf("M 0.0 0.0", "C 10.0 0.0 30.0 5.0 60.0 5.0", "Z"),
            interpreter.interpretEvidence(
                charString = type2CharString(
                    type2Number(0),
                    type2Number(0),
                    type2Operator(21),
                    type2Number(10),
                    type2Number(20),
                    type2Number(5),
                    type2Number(30),
                    type2Operator(27),
                    type2Operator(14),
                ),
                glyphId = 8u,
            ).outlineCommands,
        )
        assertEquals(
            listOf("M 0.0 0.0", "C 0.0 10.0 20.0 15.0 20.0 45.0", "Z"),
            interpreter.interpretEvidence(
                charString = type2CharString(
                    type2Number(0),
                    type2Number(0),
                    type2Operator(21),
                    type2Number(10),
                    type2Number(20),
                    type2Number(5),
                    type2Number(30),
                    type2Operator(26),
                    type2Operator(14),
                ),
                glyphId = 9u,
            ).outlineCommands,
        )
        assertEquals(
            listOf("M 0.0 0.0", "C 10.0 0.0 30.0 5.0 30.0 35.0", "Z"),
            interpreter.interpretEvidence(
                charString = type2CharString(
                    type2Number(0),
                    type2Number(0),
                    type2Operator(21),
                    type2Number(10),
                    type2Number(20),
                    type2Number(5),
                    type2Number(30),
                    type2Operator(31),
                    type2Operator(14),
                ),
                glyphId = 10u,
            ).outlineCommands,
        )
        assertEquals(
            listOf("M 0.0 0.0", "C 0.0 10.0 20.0 15.0 50.0 15.0", "Z"),
            interpreter.interpretEvidence(
                charString = type2CharString(
                    type2Number(0),
                    type2Number(0),
                    type2Operator(21),
                    type2Number(10),
                    type2Number(20),
                    type2Number(5),
                    type2Number(30),
                    type2Operator(30),
                    type2Operator(14),
                ),
                glyphId = 11u,
            ).outlineCommands,
        )
        assertEquals(
            listOf(
                "M 0.0 0.0",
                "C 10.0 0.0 30.0 5.0 60.0 5.0",
                "C 100.0 5.0 150.0 0.0 210.0 0.0",
                "Z",
            ),
            interpreter.interpretEvidence(
                charString = type2CharString(
                    type2Number(0),
                    type2Number(0),
                    type2Operator(21),
                    type2Number(10),
                    type2Number(20),
                    type2Number(5),
                    type2Number(30),
                    type2Number(40),
                    type2Number(50),
                    type2Number(60),
                    type2EscapedOperator(34),
                    type2Operator(14),
                ),
                glyphId = 12u,
            ).outlineCommands,
        )
        assertEquals(
            listOf(
                "M 0.0 0.0",
                "C 10.0 2.0 30.0 7.0 60.0 7.0",
                "C 100.0 7.0 150.0 4.0 210.0 0.0",
                "Z",
            ),
            interpreter.interpretEvidence(
                charString = type2CharString(
                    type2Number(0),
                    type2Number(0),
                    type2Operator(21),
                    type2Number(10),
                    type2Number(2),
                    type2Number(20),
                    type2Number(5),
                    type2Number(30),
                    type2Number(40),
                    type2Number(50),
                    type2Number(-3),
                    type2Number(60),
                    type2EscapedOperator(36),
                    type2Operator(14),
                ),
                glyphId = 13u,
            ).outlineCommands,
        )
        assertEquals(
            listOf(
                "M 0.0 0.0",
                "C 10.0 1.0 30.0 3.0 60.0 6.0",
                "C 100.0 10.0 150.0 15.0 210.0 0.0",
                "Z",
            ),
            interpreter.interpretEvidence(
                charString = type2CharString(
                    type2Number(0),
                    type2Number(0),
                    type2Operator(21),
                    type2Number(10),
                    type2Number(1),
                    type2Number(20),
                    type2Number(2),
                    type2Number(30),
                    type2Number(3),
                    type2Number(40),
                    type2Number(4),
                    type2Number(50),
                    type2Number(5),
                    type2Number(60),
                    type2EscapedOperator(37),
                    type2Operator(14),
                ),
                glyphId = 14u,
            ).outlineCommands,
        )
    }

    @Test
    fun cffType2FixtureInterpreterRecordsWidthAndHintMaskMetadata() {
        val interpreter = CFFType2CharStringInterpreter()

        val evidence = interpreter.interpretEvidence(
            charString = type2CharString(
                type2Number(450),
                type2Number(10),
                type2Number(5),
                type2Operator(1),
                type2Number(20),
                type2Number(5),
                type2Operator(23),
                type2Operator(19),
                intArrayOf(0xff),
                type2Number(0),
                type2Number(0),
                type2Operator(21),
                type2Operator(14),
            ),
            glyphId = 15u,
        )

        assertEquals(450.0, evidence.width)
        assertEquals(2, evidence.stemHintCount)
        assertEquals(1, evidence.hintMaskByteCount)
        assertEquals(listOf("M 0.0 0.0", "Z"), evidence.outlineCommands)
        assertTrue(evidence.toCanonicalJson().contains("\"width\": 450.0"))
        assertTrue(evidence.toCanonicalJson().contains("\"hintMaskByteCount\": 1"))
    }

    @Test
    fun cffScalerUsesGeneratedCffTableCharstringsSubrsAndMetrics() {
        val cffTable = generatedCFFTable(
            charStrings = listOf(
                type2CharString(type2Number(0), type2Number(0), type2Operator(21), type2Operator(14)),
                type2CharString(
                    type2Number(475),
                    type2Number(100),
                    type2Number(200),
                    type2Operator(21),
                    type2Number(-107),
                    type2Operator(10),
                    type2Number(-107),
                    type2Operator(29),
                    type2Number(10),
                    type2Number(0),
                    type2Number(20),
                    type2Number(30),
                    type2Number(40),
                    type2Number(30),
                    type2Operator(8),
                    type2Operator(14),
                ),
            ),
            localSubroutines = listOf(
                type2CharString(
                    type2Number(50),
                    type2Number(0),
                    type2Number(0),
                    type2Number(50),
                    type2Operator(5),
                    type2Operator(11),
                ),
            ),
            globalSubroutines = listOf(
                type2CharString(
                    type2Number(0),
                    type2Number(-25),
                    type2Operator(5),
                    type2Operator(11),
                ),
            ),
        )
        val scaler = CFFScaler(
            face = syntheticCFFFace(
                scalerType = 0x4f54544fu,
                tableTag = "CFF ",
                tableBytes = cffTable,
            ),
        )

        val outline = scaler.outline(glyphId = 1u)
        val metrics = scaler.metrics(glyphId = 1u)

        assertEquals(
            listOf(
                moveTo(100.0, 200.0),
                lineTo(150.0, 200.0),
                lineTo(150.0, 250.0),
                lineTo(150.0, 225.0),
                cubicTo(160.0, 225.0, 180.0, 255.0, 220.0, 285.0),
                close(),
            ),
            outline.commands,
        )
        assertEquals(
            GlyphMetrics(
                advanceX = 475.0,
                advanceY = 0.0,
                bounds = GlyphBounds(left = 100.0, top = 200.0, right = 220.0, bottom = 285.0),
            ),
            metrics,
        )
    }

    @Test
    fun cff2ScalerUsesGeneratedCff2TableAndVariationBlend() {
        val cff2Table = generatedCFF2Table(
            charStrings = listOf(
                type2CharString(
                    type2Number(0),
                    type2Number(0),
                    type2Operator(21),
                    type2Number(0),
                    type2Operator(15),
                    type2Number(50),
                    type2Number(10),
                    type2Number(1),
                    type2Operator(16),
                    type2Number(0),
                    type2Operator(5),
                    type2Operator(14),
                ),
            ),
        )
        val scaler = CFF2Scaler(
            face = syntheticCFFFace(
                scalerType = 0x43464632u,
                tableTag = "CFF2",
                tableBytes = cff2Table,
                variations = VariationTables(
                    axes = listOf(
                        variationAxis(tag = "wght", minimum = 100.0, defaultValue = 400.0, maximum = 900.0),
                    ),
                ),
            ),
        )

        val outline = scaler.outline(glyphId = 0u, position = VariationPosition(axes = mapOf("wght" to 0.5)))
        val metrics = scaler.metrics(glyphId = 0u, position = VariationPosition(axes = mapOf("wght" to 0.5)))

        assertEquals(
            GlyphOutline(
                glyphId = 0u,
                commands = listOf(
                    moveTo(0.0, 0.0),
                    lineTo(55.0, 0.0),
                    close(),
                ),
            ),
            outline,
        )
        assertEquals(
            GlyphMetrics(
                advanceX = 600.0,
                advanceY = 0.0,
                bounds = GlyphBounds(left = 0.0, top = 0.0, right = 55.0, bottom = 0.0),
            ),
            metrics,
        )
    }

    @Test
    fun cff2ScalerUsesVariationStoreRegionScalarsForBlendAndMetricsBounds() {
        val cff2Table = generatedCFF2Table(
            charStrings = listOf(
                type2CharString(
                    type2Number(0),
                    type2Number(0),
                    type2Operator(21),
                    type2Number(0),
                    type2Operator(15),
                    type2Number(50),
                    type2Number(20),
                    type2Number(1),
                    type2Operator(16),
                    type2Number(0),
                    type2Operator(5),
                    type2Operator(14),
                ),
            ),
            variationStore = generatedCFF2VariationStoreOneAxis(start = 0.0, peak = 0.5, end = 1.0),
        )
        val scaler = CFF2Scaler(
            face = syntheticCFFFace(
                scalerType = 0x43464632u,
                tableTag = "CFF2",
                tableBytes = cff2Table,
                variations = VariationTables(
                    axes = listOf(
                        variationAxis(tag = "wght", minimum = 100.0, defaultValue = 400.0, maximum = 900.0),
                    ),
                ),
            ),
        )

        val defaultOutline = scaler.outline(glyphId = 0u, position = VariationPosition(axes = mapOf("wght" to 0.0)))
        val variedOutline = scaler.outline(glyphId = 0u, position = VariationPosition(axes = mapOf("wght" to 0.25)))
        val variedMetrics = scaler.metrics(glyphId = 0u, position = VariationPosition(axes = mapOf("wght" to 0.25)))
        val tableEvidence = scaler.tableEvidence()

        assertEquals(listOf(moveTo(0.0, 0.0), lineTo(50.0, 0.0), close()), defaultOutline.commands)
        assertEquals(listOf(moveTo(0.0, 0.0), lineTo(60.0, 0.0), close()), variedOutline.commands)
        assertEquals(GlyphBounds(left = 0.0, top = 0.0, right = 60.0, bottom = 0.0), variedMetrics.bounds)
        assertTrue("cff.dict.variation-store" in tableEvidence.topDictOperators)
    }

    @Test
    fun cffScalersExposeDeterministicTableEvidenceDumps() {
        val cffScaler = CFFScaler(
            face = syntheticCFFFace(
                scalerType = 0x4f54544fu,
                tableTag = "CFF ",
                tableBytes = generatedCFFTable(
                    charStrings = listOf(
                        type2CharString(type2Number(0), type2Number(0), type2Operator(21), type2Operator(14)),
                    ),
                    localSubroutines = listOf(type2CharString(type2Operator(11))),
                    globalSubroutines = listOf(type2CharString(type2Operator(11))),
                ),
            ),
        )
        val cff2Scaler = CFF2Scaler(
            face = syntheticCFFFace(
                scalerType = 0x43464632u,
                tableTag = "CFF2",
                tableBytes = generatedCFF2Table(
                    charStrings = listOf(
                        type2CharString(type2Number(0), type2Number(0), type2Operator(21), type2Operator(14)),
                    ),
                ),
                variations = VariationTables(
                    axes = listOf(
                        variationAxis(tag = "wght", minimum = 100.0, defaultValue = 400.0, maximum = 900.0),
                    ),
                ),
            ),
        )

        val cffEvidence = cffScaler.tableEvidence()
        val cff2Evidence = cff2Scaler.tableEvidence()

        assertEquals("cff", cffEvidence.format)
        assertEquals(1, cffEvidence.charStringCount)
        assertEquals(1, cffEvidence.localSubroutineCount)
        assertEquals(1, cffEvidence.globalSubroutineCount)
        assertEquals(true, cffEvidence.hasPrivateDict)
        assertTrue("cff.dict.charstrings" in cffEvidence.topDictOperators)
        assertTrue("cff.dict.private" in cffEvidence.topDictOperators)
        assertEquals(emptyList(), cffEvidence.variationAxisTags)
        assertTrue(cffEvidence.toCanonicalJson().contains("\"format\": \"cff\""))

        assertEquals("cff2", cff2Evidence.format)
        assertEquals(1, cff2Evidence.charStringCount)
        assertEquals(0, cff2Evidence.localSubroutineCount)
        assertEquals(0, cff2Evidence.globalSubroutineCount)
        assertEquals(false, cff2Evidence.hasPrivateDict)
        assertEquals(listOf("wght"), cff2Evidence.variationAxisTags)
        assertTrue(cff2Evidence.toCanonicalJson().contains("\"variationAxisTags\": [\"wght\"]"))
    }

    @Test
    fun cffTableEvidenceRefusesMalformedIndexAndDictDeterministically() {
        val malformedIndex = bytes(
            0x01,
            0x00,
            0x04,
            0x01,
            0x00,
            0x01,
            0x00,
        )
        val missingCharStrings = bytes(0x01, 0x00, 0x04, 0x01) +
            cffIndex(listOf("Broken".toByteArray(Charsets.US_ASCII))) +
            cffIndex(listOf(byteArrayOf())) +
            cffIndex(emptyList()) +
            cffIndex(emptyList())

        val malformedIndexFailure = assertFailsWith<FontScalerRefusalException> {
            CFFScaler(
                face = syntheticCFFFace(
                    scalerType = 0x4f54544fu,
                    tableTag = "CFF ",
                    tableBytes = malformedIndex,
                ),
            ).tableEvidence()
        }
        val missingDictFailure = assertFailsWith<FontScalerRefusalException> {
            CFFScaler(
                face = syntheticCFFFace(
                    scalerType = 0x4f54544fu,
                    tableTag = "CFF ",
                    tableBytes = missingCharStrings,
                ),
            ).tableEvidence()
        }

        listOf(malformedIndexFailure, missingDictFailure).forEach { failure ->
            assertEquals(FontScalerDiagnosticCodes.CFF_TABLE_MALFORMED, failure.diagnostic.code)
            assertEquals("cff.table-malformed", failure.diagnostic.detail)
            assertEquals("table", failure.diagnostic.operation)
            assertTrue(failure.message.orEmpty().contains("CFF table malformed"))
        }
    }

    @Test
    fun cffScalersRefuseWhenRequiredRawTablesAreMissing() {
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
            assertTrue(failure.message.orEmpty().contains("table bytes"))
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

    @Test
    fun truetypeCompositeGlyphReadinessGoldenMatchesGeneratedEvidence() {
        val expected = Files.readString(
            kanvasProjectRoot().resolve("reports/font/fixtures/expected/scaler/truetype-composite-glyphs.json"),
        ).trimEnd()

        assertEquals(expected, truetypeCompositeGlyphReadinessDump())
    }

    private fun truetypeCompositeGlyphReadinessDump(): String {
        val simpleSquare = simpleSquareGlyphData()
        val supportedComposite = compositeGlyphData(
            *componentRecord(
                flags = 0x0223,
                glyphId = 1,
                arg1 = 50,
                arg2 = 50,
            ),
            *componentRecord(
                flags = 0x0001,
                glyphId = 2,
                arg1 = 1,
                arg2 = 0,
            ),
        )
        val supportedGlyf = supportedComposite + simpleSquare + simpleSquare
        val supportedEvidence = ParsedTrueTypeGlyphScaler(
            glyfTable = supportedGlyf,
            loca = TrueTypeLocaTable(
                offsets = listOf(
                    0,
                    supportedComposite.size,
                    supportedComposite.size + simpleSquare.size,
                    supportedGlyf.size,
                ),
            ),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
                1u to TrueTypeGlyphHorizontalMetrics(advanceX = 500.0, leftSideBearing = 0.0),
                2u to TrueTypeGlyphHorizontalMetrics(advanceX = 700.0, leftSideBearing = 30.0),
            ),
        ).scaledGlyphEvidence(glyphId = 0u)
        val cycleComposite = compositeGlyphData(
            *componentRecord(
                flags = 0x0003,
                glyphId = 0,
                arg1 = 0,
                arg2 = 0,
            ),
        )
        val cycleEvidence = ParsedTrueTypeGlyphScaler(
            glyfTable = cycleComposite,
            loca = TrueTypeLocaTable(offsets = listOf(0, cycleComposite.size)),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
            ),
        ).scaledGlyphEvidence(glyphId = 0u)
        val invalidComposite = compositeGlyphData(
            *componentRecord(
                flags = 0x0003,
                glyphId = 2,
                arg1 = 0,
                arg2 = 0,
            ),
        )
        val invalidEvidence = ParsedTrueTypeGlyphScaler(
            glyfTable = invalidComposite,
            loca = TrueTypeLocaTable(offsets = listOf(0, invalidComposite.size)),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
            ),
        ).scaledGlyphEvidence(glyphId = 0u)
        val excessiveComposite = excessiveCompositeComponentGlyphData()
        val excessiveGlyf = excessiveComposite + simpleSquare
        val excessiveEvidence = ParsedTrueTypeGlyphScaler(
            glyfTable = excessiveGlyf,
            loca = TrueTypeLocaTable(offsets = listOf(0, excessiveComposite.size, excessiveGlyf.size)),
            horizontalMetrics = mapOf(
                0u to TrueTypeGlyphHorizontalMetrics(advanceX = 600.0, leftSideBearing = 20.0),
                1u to TrueTypeGlyphHorizontalMetrics(advanceX = 500.0, leftSideBearing = 0.0),
            ),
        ).scaledGlyphEvidence(glyphId = 0u)

        return """
            {
              "schemaVersion": 1,
              "dumpId": "truetype-composite-glyphs",
              "ownerTickets": [
                "KFONT-M3-001"
              ],
              "fixtureIds": [
                "truetype-scaler-truetype-composite-glyph-transform"
              ],
              "requiredEvidence": [
                "glyph-outline.json",
                "glyph-metrics.json",
                "component-trace",
                "diagnostic-snapshot"
              ],
              "pathHashArtifacts": {
                "outlineCommandDumpSha256": "${supportedEvidence.outlineCommandDumpSha256}",
                "outlineCommandCount": ${supportedEvidence.outlineCommands.size},
                "componentTraceCount": ${supportedEvidence.compositeComponents.size}
              },
              "useMyMetricsFacts": [
                {
                  "parentGlyphId": 0,
                  "componentIndex": 0,
                  "componentGlyphId": 1,
                  "useMyMetrics": true
                }
              ],
              "supportedCompositeEvidence": ${supportedEvidence.toCanonicalJson().prependIndent("              ").trimStart()},
              "diagnosticSnapshots": {
                "cycle": [
                  ${cycleEvidence.diagnostics.joinToString(",\n                  ") { diagnostic -> diagnostic.toCanonicalJson() }}
                ],
                "invalidComponentGlyphId": [
                  ${invalidEvidence.diagnostics.joinToString(",\n                  ") { diagnostic -> diagnostic.toCanonicalJson() }}
                ],
                "componentCount": [
                  ${excessiveEvidence.diagnostics.joinToString(",\n                  ") { diagnostic -> diagnostic.toCanonicalJson() }}
                ]
              },
              "nonClaims": [
                "no-complete-target-support-claim",
                "no-a8-or-sdf-artifact-claim",
                "no-gpu-text-route-claim",
                "no-native-scaler-oracle-claim",
                "no-cff-or-cff2-outline-claim",
                "no-hinting-vm-claim",
                "no-full-iup-interpolation-claim",
                "no-phantom-point-metrics-claim",
                "no-vertical-metrics-claim",
                "no-complete-variable-font-support-claim",
                "no-shaping-fallback-or-paragraph-claim"
              ]
            }
        """.trimIndent()
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

    private fun singleAxisGvarWithGlyphOneAllPointDelta(): ByteArray = bytes(
        0x00, 0x01,
        0x00, 0x00,
        0x00, 0x01,
        0x00, 0x00,
        0x00, 0x00, 0x00, 0x14,
        0x00, 0x02,
        0x00, 0x01,
        0x00, 0x00, 0x00, 0x20,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
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

    private fun excessiveCompositeComponentGlyphData(): ByteArray {
        val componentRecords = (0 until 65).flatMap { index ->
            componentRecord(
                flags = if (index == 64) 0x0003 else 0x0023,
                glyphId = 1,
                arg1 = 0,
                arg2 = 0,
            ).toList()
        }.toIntArray()
        return compositeGlyphData(*componentRecords)
    }

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

    private fun type2CharString(vararg chunks: IntArray): ByteArray =
        bytes(*chunks.flatMap { chunk -> chunk.toList() }.toIntArray())

    private fun type2Operator(value: Int): IntArray = intArrayOf(value)

    private fun type2EscapedOperator(value: Int): IntArray = intArrayOf(12, value)

    private fun type2Number(value: Int): IntArray =
        when (value) {
            in -107..107 -> intArrayOf(value + 139)
            in 108..1131 -> {
                val encoded = value - 108
                intArrayOf(247 + encoded / 256, encoded % 256)
            }
            in -1131..-108 -> {
                val encoded = -value - 108
                intArrayOf(251 + encoded / 256, encoded % 256)
            }
            in Short.MIN_VALUE..Short.MAX_VALUE -> intArrayOf(28, (value ushr 8) and 0xff, value and 0xff)
            else -> intArrayOf(
                29,
                (value ushr 24) and 0xff,
                (value ushr 16) and 0xff,
                (value ushr 8) and 0xff,
                value and 0xff,
            )
        }

    private fun generatedCFFTable(
        charStrings: List<ByteArray>,
        localSubroutines: List<ByteArray> = emptyList(),
        globalSubroutines: List<ByteArray> = emptyList(),
    ): ByteArray {
        val header = bytes(0x01, 0x00, 0x04, 0x01)
        val nameIndex = cffIndex(listOf("KanvasCFF".toByteArray(Charsets.US_ASCII)))
        val stringIndex = cffIndex(emptyList())
        val globalSubrIndex = cffIndex(globalSubroutines)
        val localSubrIndex = cffIndex(localSubroutines)

        var privateDict = cffDictNumber(0) + intArrayOf(19)
        repeat(4) {
            privateDict = cffDictNumber(privateDict.size) + intArrayOf(19)
        }

        var topDict = byteArrayOf()
        repeat(6) {
            val topDictIndex = cffIndex(listOf(topDict))
            val beforePrivateSize = header.size + nameIndex.size + topDictIndex.size + stringIndex.size + globalSubrIndex.size
            val privateOffset = beforePrivateSize
            val charStringsOffset = beforePrivateSize + privateDict.size + localSubrIndex.size
            topDict = bytes(
                *(cffDictNumber(charStringsOffset) + intArrayOf(17) +
                    cffDictNumber(privateDict.size) + cffDictNumber(privateOffset) + intArrayOf(18)),
            )
        }

        return header +
            nameIndex +
            cffIndex(listOf(topDict)) +
            stringIndex +
            globalSubrIndex +
            bytes(*privateDict) +
            localSubrIndex +
            cffIndex(charStrings)
    }

    private fun generatedCFF2Table(
        charStrings: List<ByteArray>,
        globalSubroutines: List<ByteArray> = emptyList(),
        variationStore: ByteArray? = null,
    ): ByteArray {
        val globalSubrIndex = cffIndex(globalSubroutines)
        var topDict = byteArrayOf()
        repeat(6) {
            val variationStoreOffset = variationStore?.let { 5 + topDict.size + globalSubrIndex.size }
            val charStringsOffset = 5 + topDict.size + globalSubrIndex.size + (variationStore?.size ?: 0)
            topDict = bytes(
                *(cffDictNumber(charStringsOffset) + intArrayOf(17) +
                    (variationStoreOffset?.let { cffDictNumber(it) + intArrayOf(24) } ?: intArrayOf())),
            )
        }
        return bytes(
            0x02,
            0x00,
            0x05,
            (topDict.size ushr 8) and 0xff,
            topDict.size and 0xff,
        ) + topDict + globalSubrIndex + (variationStore ?: byteArrayOf()) + cffIndex(charStrings)
    }

    private fun generatedCFF2VariationStoreOneAxis(
        start: Double,
        peak: Double,
        end: Double,
    ): ByteArray =
        bytes(
            0x00, 0x01,
            0x00, 0x00, 0x00, 0x0c,
            0x00, 0x01,
            0x00, 0x00, 0x00, 0x16,
            0x00, 0x01,
            0x00, 0x01,
            *f2Dot14(start),
            *f2Dot14(peak),
            *f2Dot14(end),
            0x00, 0x01,
            0x00, 0x00,
            0x00, 0x01,
            0x00, 0x00,
            0x00,
        )

    private fun f2Dot14(value: Double): IntArray {
        val encoded = kotlin.math.round(value * 16384.0).toInt()
        return intArrayOf((encoded ushr 8) and 0xff, encoded and 0xff)
    }

    private fun cffIndex(objects: List<ByteArray>): ByteArray {
        if (objects.isEmpty()) return bytes(0x00, 0x00)
        val offsets = mutableListOf(1)
        objects.forEach { item -> offsets += offsets.last() + item.size }
        val offSize = when {
            offsets.last() <= 0xff -> 1
            offsets.last() <= 0xffff -> 2
            offsets.last() <= 0xffffff -> 3
            else -> 4
        }
        val values = mutableListOf((objects.size ushr 8) and 0xff, objects.size and 0xff, offSize)
        offsets.forEach { offset -> values += cffOffset(offset, offSize).toList() }
        objects.forEach { item -> values += item.toUnsignedByteList() }
        return bytes(*values.toIntArray())
    }

    private fun cffOffset(value: Int, offSize: Int): IntArray =
        (offSize - 1 downTo 0)
            .map { shiftIndex -> (value ushr (shiftIndex * 8)) and 0xff }
            .toIntArray()

    private fun cffDictNumber(value: Int): IntArray = type2Number(value)

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

    private fun syntheticCFFFace(
        scalerType: UInt,
        tableTag: String,
        tableBytes: ByteArray,
        metrics: MetricsTables = sfntMetricsForFactory(),
        variations: VariationTables = VariationTables(),
    ): OpenTypeFaceData = OpenTypeFaceData(
        id = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440211")),
        source = FontSource(
            id = FontSourceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440210")),
            kind = FontSourceKind.MEMORY,
            displayName = "Synthetic CFF",
            bytes = ByteArray(0),
        ),
        directory = SFNTTableDirectory(scalerType = scalerType),
        metrics = metrics,
        variations = variations,
        rawTables = mapOf(SFNTTableTag(tableTag) to tableBytes.toUnsignedByteList()),
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
