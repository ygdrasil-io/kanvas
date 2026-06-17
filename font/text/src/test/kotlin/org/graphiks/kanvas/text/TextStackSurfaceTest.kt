package org.graphiks.kanvas.text

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import org.graphiks.kanvas.font.FallbackRequest
import org.graphiks.kanvas.font.FontFace
import org.graphiks.kanvas.font.FontResolver
import org.graphiks.kanvas.font.FontSource
import org.graphiks.kanvas.font.FontSourceID
import org.graphiks.kanvas.font.FontSourceKind
import org.graphiks.kanvas.font.ResolvedFontRun
import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.font.TypefaceData
import org.graphiks.kanvas.font.sfnt.DefaultOpenTypeFaceParser
import org.graphiks.kanvas.font.sfnt.CMapFormat12Group
import org.graphiks.kanvas.font.sfnt.CMapFormat12Mapping
import org.graphiks.kanvas.font.sfnt.CMapSubtable
import org.graphiks.kanvas.font.sfnt.CMapTable
import org.graphiks.kanvas.font.sfnt.OpenTypeGsubLigatureSubstitution
import org.graphiks.kanvas.font.sfnt.OpenTypeGsubLigatureSubstitutionLookup
import org.graphiks.kanvas.font.sfnt.OpenTypeGsubContextClassLookup
import org.graphiks.kanvas.font.sfnt.OpenTypeGsubContextClassRule
import org.graphiks.kanvas.font.sfnt.OpenTypeGsubContextClassSubtable
import org.graphiks.kanvas.font.sfnt.OpenTypeGsubContextGlyphLookup
import org.graphiks.kanvas.font.sfnt.OpenTypeGsubContextGlyphRule
import org.graphiks.kanvas.font.sfnt.OpenTypeGsubMultipleSubstitution
import org.graphiks.kanvas.font.sfnt.OpenTypeGsubMultipleSubstitutionLookup
import org.graphiks.kanvas.font.sfnt.OpenTypeGsubNestedLookupRecord
import org.graphiks.kanvas.font.sfnt.OpenTypeGsubSingleSubstitution
import org.graphiks.kanvas.font.sfnt.OpenTypeGsubSingleSubstitutionLookup
import org.graphiks.kanvas.font.sfnt.OpenTypeGsubTable
import org.graphiks.kanvas.font.sfnt.OpenTypeGposPairAdjustment
import org.graphiks.kanvas.font.sfnt.OpenTypeGposPairTable
import org.graphiks.kanvas.font.sfnt.OpenTypeGposSingleAdjustment
import org.graphiks.kanvas.font.sfnt.OpenTypeGposSingleTable
import org.graphiks.kanvas.font.sfnt.OpenTypeGposValueRecord
import org.graphiks.kanvas.font.sfnt.OpenTypeKernCoverage
import org.graphiks.kanvas.font.sfnt.OpenTypeKernFormat0Subtable
import org.graphiks.kanvas.font.sfnt.OpenTypeKernPair
import org.graphiks.kanvas.font.sfnt.OpenTypeKernTable
import org.graphiks.kanvas.text.paragraph.HitTestResult
import org.graphiks.kanvas.text.paragraph.BasicParagraphLayoutEngine
import org.graphiks.kanvas.text.paragraph.LineBreaker
import org.graphiks.kanvas.text.paragraph.LineLayout
import org.graphiks.kanvas.text.paragraph.LineMetrics
import org.graphiks.kanvas.text.paragraph.PARAGRAPH_LAYOUT_CONSTRAINT_NEGATIVE_DIAGNOSTIC_CODE
import org.graphiks.kanvas.text.paragraph.PARAGRAPH_LAYOUT_CONSTRAINT_NON_FINITE_DIAGNOSTIC_CODE
import org.graphiks.kanvas.text.paragraph.PARAGRAPH_LAYOUT_MAX_LINES_ELLIPSIS_UNSUPPORTED_DIAGNOSTIC_CODE
import org.graphiks.kanvas.text.paragraph.Paragraph
import org.graphiks.kanvas.text.paragraph.ParagraphBuilder
import org.graphiks.kanvas.text.paragraph.ParagraphLayoutDiagnostic
import org.graphiks.kanvas.text.paragraph.ParagraphLayoutEngine
import org.graphiks.kanvas.text.paragraph.ParagraphLayoutResult
import org.graphiks.kanvas.text.paragraph.ParagraphStyle
import org.graphiks.kanvas.text.paragraph.PlaceholderStyle
import org.graphiks.kanvas.text.paragraph.SelectionRange
import org.graphiks.kanvas.text.paragraph.SimpleLineBreaker
import org.graphiks.kanvas.text.paragraph.TextBox
import org.graphiks.kanvas.text.paragraph.TextDirection
import org.graphiks.kanvas.text.paragraph.TextPosition
import org.graphiks.kanvas.text.paragraph.TextStyle
import org.graphiks.kanvas.text.shaping.BasicBidiResolver
import org.graphiks.kanvas.text.shaping.BasicOpenTypeShapingEngine
import org.graphiks.kanvas.text.shaping.BasicScriptItemizer
import org.graphiks.kanvas.text.shaping.BasicTextSegmenter
import org.graphiks.kanvas.text.shaping.BasicUnicodeData
import org.graphiks.kanvas.text.shaping.BidiResolver
import org.graphiks.kanvas.text.shaping.BidiRun
import org.graphiks.kanvas.text.shaping.CMapGlyphMapper
import org.graphiks.kanvas.text.shaping.CONFLICTING_FONT_RUN_DIAGNOSTIC_CODE
import org.graphiks.kanvas.text.shaping.EmojiSequenceShaper
import org.graphiks.kanvas.text.shaping.FeatureSet
import org.graphiks.kanvas.text.shaping.FallbackOpenTypeShapingEngine
import org.graphiks.kanvas.text.shaping.GDEFData
import org.graphiks.kanvas.text.shaping.GPOSEngine
import org.graphiks.kanvas.text.shaping.GSUBEngine
import org.graphiks.kanvas.text.shaping.GlyphCluster
import org.graphiks.kanvas.text.shaping.GlyphMapper
import org.graphiks.kanvas.text.shaping.KERN_TABLE_UNAPPLIED_DIAGNOSTIC_CODE
import org.graphiks.kanvas.text.shaping.MISSING_GLYPH_DIAGNOSTIC_CODE
import org.graphiks.kanvas.text.shaping.OpenTypeShapingEngine
import org.graphiks.kanvas.text.shaping.ScriptItemizer
import org.graphiks.kanvas.text.shaping.ScriptRun
import org.graphiks.kanvas.text.shaping.ShapedGlyphRun
import org.graphiks.kanvas.text.shaping.ShapingDiagnostic
import org.graphiks.kanvas.text.shaping.ShapingRequest
import org.graphiks.kanvas.text.shaping.ShapingResult
import org.graphiks.kanvas.text.shaping.TEXT_SHAPING_CLUSTER_INVARIANT_FAILED_DIAGNOSTIC_CODE
import org.graphiks.kanvas.text.shaping.TEXT_SHAPING_LOOKUP_MALFORMED_DIAGNOSTIC_CODE
import org.graphiks.kanvas.text.shaping.TEXT_SHAPING_PARAGRAPH_BIDI_REQUIRED_DIAGNOSTIC_CODE
import org.graphiks.kanvas.text.shaping.TextSegmenter
import org.graphiks.kanvas.text.shaping.UnicodeData
import org.graphiks.kanvas.text.shaping.UNRESOLVED_FONT_RUN_DIAGNOSTIC_CODE

class TextStackSurfaceTest {
    @Test
    fun exposesExpectedPureKotlinTextStackTypes() {
        val shapingTypes = listOf(
            ShapingRequest::class.simpleName,
            ShapingResult::class.simpleName,
            ShapedGlyphRun::class.simpleName,
            GlyphCluster::class.simpleName,
            FeatureSet::class.simpleName,
            ScriptRun::class.simpleName,
            BidiRun::class.simpleName,
            UnicodeData::class.simpleName,
            BasicUnicodeData::class.simpleName,
            TextSegmenter::class.simpleName,
            BasicTextSegmenter::class.simpleName,
            BidiResolver::class.simpleName,
            BasicBidiResolver::class.simpleName,
            ScriptItemizer::class.simpleName,
            BasicScriptItemizer::class.simpleName,
            GlyphMapper::class.simpleName,
            CMapGlyphMapper::class.simpleName,
            OpenTypeShapingEngine::class.simpleName,
            BasicOpenTypeShapingEngine::class.simpleName,
            FallbackOpenTypeShapingEngine::class.simpleName,
            GSUBEngine::class.simpleName,
            GPOSEngine::class.simpleName,
            GDEFData::class.simpleName,
            EmojiSequenceShaper::class.simpleName,
            ShapingDiagnostic::class.simpleName,
        )

        val paragraphTypes = listOf(
            ParagraphBuilder::class.simpleName,
            Paragraph::class.simpleName,
            ParagraphStyle::class.simpleName,
            TextStyle::class.simpleName,
            PlaceholderStyle::class.simpleName,
            ParagraphLayoutEngine::class.simpleName,
            ParagraphLayoutResult::class.simpleName,
            LineBreaker::class.simpleName,
            LineLayout::class.simpleName,
            LineMetrics::class.simpleName,
            TextBox::class.simpleName,
            ParagraphLayoutDiagnostic::class.simpleName,
            HitTestResult::class.simpleName,
            SelectionRange::class.simpleName,
            TextPosition::class.simpleName,
        )

        assertEquals(25, shapingTypes.size)
        assertEquals(15, paragraphTypes.size)
    }

    @Test
    fun shapingDiagnosticCodesUseStableSpecFamilies() {
        assertEquals("text.shaping.fallback-missing", MISSING_GLYPH_DIAGNOSTIC_CODE)
        assertEquals(MISSING_GLYPH_DIAGNOSTIC_CODE, UNRESOLVED_FONT_RUN_DIAGNOSTIC_CODE)
        assertEquals("text.shaping.feature-unsupported", KERN_TABLE_UNAPPLIED_DIAGNOSTIC_CODE)
        assertEquals("text.shaping.cluster-invariant-failed", CONFLICTING_FONT_RUN_DIAGNOSTIC_CODE)
    }

    @Test
    fun paragraphBuilderBuildsImmutableStyledTextAndPlaceholderRanges() {
        val firstStyle = TextStyle(fontSize = 10f)
        val secondStyle = TextStyle(fontSize = 18f)
        val placeholderStyle = PlaceholderStyle(width = 24f, height = 12f)
        val builder = ParagraphBuilder()
            .append("Hi", firstStyle)
            .appendPlaceholder(placeholderStyle)
        val snapshot = builder.build()

        builder.append("!", secondStyle)
        val laterSnapshot = builder.build()

        assertEquals("Hi\uFFFC", snapshot.text)
        assertEquals(mapOf(0..1 to firstStyle), snapshot.textStyles)
        assertEquals(mapOf(2..2 to placeholderStyle), snapshot.placeholders)
        assertEquals("Hi\uFFFC!", laterSnapshot.text)
        assertEquals(mapOf(0..1 to firstStyle, 3..3 to secondStyle), laterSnapshot.textStyles)
    }

    @Test
    fun simpleLineBreakerBreaksOnWhitespaceAndNewlinesDeterministically() {
        val paragraph = ParagraphBuilder()
            .append("aa bb ccc\ndd", TextStyle(fontSize = 10f))
            .build()

        val ranges = SimpleLineBreaker().breakLines(paragraph, maxWidth = 50f)

        assertEquals(listOf(0..4, 6..8, 10..11), ranges)
    }

    @Test
    fun basicParagraphLayoutEngineShapesLinesAndProducesMetricsAndBoxes() {
        val engine = RecordingShapingEngine()
        val layoutEngine = BasicParagraphLayoutEngine(engine)
        val paragraph = ParagraphBuilder()
            .append("aa bb c", TextStyle(fontSize = 10f))
            .build()

        val result = layoutEngine.layout(paragraph, maxWidth = 50f)

        assertEquals(listOf(0..4, 6..6), engine.requests.map { it.textRange })
        assertEquals(2, result.lines.size)
        assertEquals(50f, result.width)
        assertEquals(20f, result.height)
        assertFalse(result.didOverflowWidth)
        assertEquals(LineMetrics(ascent = -8f, descent = 2f, width = 50f, baseline = 8f), result.lines[0].metrics)
        assertEquals(TextBox(textRange = 0..4, left = 0f, top = 0f, right = 50f, bottom = 10f), result.lines[0].boxes.single())
        assertEquals(LineMetrics(ascent = -8f, descent = 2f, width = 10f, baseline = 18f), result.lines[1].metrics)
        assertEquals(TextBox(textRange = 6..6, left = 0f, top = 10f, right = 10f, bottom = 20f), result.lines[1].boxes.single())
    }

    @Test
    fun paragraphLayoutResultDumpsCurrentSemanticLayoutFactsDeterministically() {
        val layoutEngine = BasicParagraphLayoutEngine(RecordingShapingEngine())
        val paragraph = ParagraphBuilder(ParagraphStyle(textDirection = TextDirection.LEFT_TO_RIGHT))
            .append("aa bb c", TextStyle(fontSize = 10f, locale = "en-US"))
            .build()

        val result = layoutEngine.layout(paragraph, maxWidth = 50f)

        assertEquals(
            """
            {
              "schema": "kanvas.paragraph.layout.v1",
              "input": {
                "schema": "kanvas.paragraph.input.v1",
                "unicodeVersion": "16.0.0",
                "inputHash": "7f497d3e3f7e110e11f6cb7efd28419214a6aa44d93eb24ae7e04713a95e8a11",
                "text": "aa bb c",
                "textLength": 7,
                "paragraphStyle": {"textAlign": "start", "textDirection": "ltr", "maxLines": null, "ellipsis": null, "ellipsisPolicy": "none", "lineHeight": null, "textHeightBehavior": "font-metrics", "defaultLocale": null},
                "styleRuns": [
                  {"range": "0..6", "fontFamilies": [], "fallbackPreference": "system-default", "typefaceId": null, "fontSize": 10.0, "fontWeight": 400, "fontWidth": 5, "fontSlant": "upright", "syntheticStylePolicy": "allow", "locale": "en-US", "scriptHint": null, "features": [], "variationCoordinates": [], "palette": null, "colorRgba": "000000ff", "decoration": null, "letterSpacing": 0.0, "wordSpacing": 0.0, "heightMultiplier": null}
                ],
                "placeholders": [],
                "diagnostics": []
              },
              "layout": {"maxWidth": 50.0, "width": 50.0, "height": 20.0, "didOverflowWidth": false, "didOverflowHeight": false, "layoutRefused": false},
              "lines": [
                {"index": 0, "textRange": "0..4", "metrics": {"ascent": -8.0, "descent": 2.0, "leading": 0.0, "width": 50.0, "baseline": 8.0}, "boxes": [{"textRange": "0..4", "left": 0.0, "top": 0.0, "right": 50.0, "bottom": 10.0, "direction": 1}], "glyphRunCount": 1},
                {"index": 1, "textRange": "6..6", "metrics": {"ascent": -8.0, "descent": 2.0, "leading": 0.0, "width": 10.0, "baseline": 18.0}, "boxes": [{"textRange": "6..6", "left": 0.0, "top": 10.0, "right": 10.0, "bottom": 20.0, "direction": 1}], "glyphRunCount": 1}
              ],
              "diagnostics": []
            }
            """.trimIndent() + "\n",
            result.dump(),
        )
        assertFalse(result.dump().contains("@"))
        assertFalse(result.dump().contains("Sk"))
    }

    @Test
    fun paragraphLayoutDiagnosesMaxLineEllipsisUnsupportedInResultDump() {
        val layoutEngine = BasicParagraphLayoutEngine(RecordingShapingEngine())
        val paragraph = ParagraphBuilder(ParagraphStyle(maxLines = 1, ellipsis = "..."))
            .append("aa bb c", TextStyle(fontSize = 10f))
            .build()

        val result = layoutEngine.layout(paragraph, maxWidth = 50f)

        assertEquals(1, result.lines.size)
        assertTrue(result.didOverflowHeight)
        assertEquals(
            listOf(
                ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_LAYOUT_MAX_LINES_ELLIPSIS_UNSUPPORTED_DIAGNOSTIC_CODE,
                    message = "maxLines ellipsis is not implemented by the current paragraph engine.",
                    textRange = 6..6,
                    severity = "refusal",
                ),
            ),
            result.diagnostics,
        )
        assertTrue(result.dump().contains("\"code\": \"text.paragraph.max-lines-ellipsis-unsupported\""))
        assertTrue(result.dump().contains("\"textRange\": \"6..6\""))
    }

    @Test
    fun paragraphLayoutMergesShapingDiagnosticsIntoResultDump() {
        val shapingDiagnostic = ShapingDiagnostic(
            code = MISSING_GLYPH_DIAGNOSTIC_CODE,
            message = "Missing glyph U+0041.",
            textRange = 0..0,
        )
        val layoutEngine = BasicParagraphLayoutEngine(
            RecordingShapingEngine(diagnostics = listOf(shapingDiagnostic)),
        )
        val paragraph = ParagraphBuilder()
            .append("A", TextStyle(fontSize = 10f))
            .build()

        val result = layoutEngine.layout(paragraph, maxWidth = 50f)

        assertEquals(
            ParagraphLayoutDiagnostic(
                code = MISSING_GLYPH_DIAGNOSTIC_CODE,
                message = "Missing glyph U+0041.",
                textRange = 0..0,
                severity = "diagnostic",
            ),
            result.diagnostics.single(),
        )
        assertTrue(result.dump().contains("\"code\": \"text.shaping.fallback-missing\""))
        assertTrue(result.dump().contains("\"textRange\": \"0..0\""))
    }

    @Test
    fun paragraphLayoutDumpsInvalidStylePolicyRefusals() {
        val negativeMaxLines = ParagraphBuilder(ParagraphStyle(maxLines = -1))
            .append("abc", TextStyle(fontSize = 10f))
            .build()
        val nonFiniteLineHeight = ParagraphBuilder(ParagraphStyle(lineHeight = Float.POSITIVE_INFINITY))
            .append("abc", TextStyle(fontSize = 10f))
            .build()
        val layoutEngine = BasicParagraphLayoutEngine(RecordingShapingEngine())

        val maxLinesResult = layoutEngine.layout(negativeMaxLines, maxWidth = 50f)
        val lineHeightResult = layoutEngine.layout(nonFiniteLineHeight, maxWidth = 50f)

        assertTrue(maxLinesResult.layoutRefused)
        assertTrue(lineHeightResult.layoutRefused)
        assertEquals("text.paragraph.max-lines-invalid", maxLinesResult.diagnostics.single().code)
        assertEquals("text.paragraph.line-height-non-finite", lineHeightResult.diagnostics.single().code)
        assertTrue(maxLinesResult.dump().contains("\"code\": \"text.paragraph.max-lines-invalid\""))
        assertTrue(lineHeightResult.dump().contains("\"code\": \"text.paragraph.line-height-non-finite\""))
    }

    @Test
    fun basicParagraphLayoutEngineHandlesEmptyParagraphWithoutShaping() {
        val engine = RecordingShapingEngine()

        val result = BasicParagraphLayoutEngine(engine).layout(ParagraphBuilder().build(), maxWidth = 50f)

        assertEquals(emptyList(), engine.requests)
        assertEquals(ParagraphLayoutResult(paragraph = result.paragraph, maxWidth = 50f), result)
    }

    @Test
    fun simpleLineBreakerKeepsSurrogatePairRangesIntactWhenWidthIsZero() {
        val paragraph = ParagraphBuilder()
            .append("a\uD83D\uDE00b", TextStyle(fontSize = 10f))
            .build()

        val ranges = SimpleLineBreaker().breakLines(paragraph, maxWidth = 0f)

        assertEquals(listOf(0..0, 1..2, 3..3), ranges)
    }

    @Test
    fun basicParagraphLayoutEngineDoesNotDuplicateEmojiWhenLineBreakerOverflowsSingleCluster() {
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'a'.code to 11,
                0x1F600 to 600,
                'b'.code to 12,
            ),
        )
        val paragraph = ParagraphBuilder()
            .append("a\uD83D\uDE00b", TextStyle(fontSize = 10f))
            .build()

        val result = BasicParagraphLayoutEngine(engine).layout(paragraph, maxWidth = 0f)

        assertEquals(listOf(0..0, 1..2, 3..3), result.lines.map { it.textRange })
        assertEquals(listOf(11, 600, 12), result.lines.flatMap { line -> line.glyphRuns.flatMap { it.glyphIds } })
    }

    @Test
    fun paragraphLayoutDumpsInvalidMaxWidthRefusals() {
        val paragraph = ParagraphBuilder()
            .append("abc", TextStyle(fontSize = 10f))
            .build()
        val lineBreaker = SimpleLineBreaker()
        val layoutEngine = BasicParagraphLayoutEngine(RecordingShapingEngine())

        val breakerNaN = assertFailsWith<IllegalArgumentException> {
            lineBreaker.breakLines(paragraph, Float.NaN)
        }
        val breakerNegative = assertFailsWith<IllegalArgumentException> {
            lineBreaker.breakLines(paragraph, -1f)
        }
        val layoutNaN = layoutEngine.layout(paragraph, Float.NaN)
        val layoutNegative = layoutEngine.layout(paragraph, -1f)

        assertTrue(breakerNaN.message.orEmpty().contains("maxWidth"))
        assertTrue(breakerNegative.message.orEmpty().contains("maxWidth"))
        assertEquals(
            ParagraphLayoutDiagnostic(
                code = PARAGRAPH_LAYOUT_CONSTRAINT_NON_FINITE_DIAGNOSTIC_CODE,
                message = "maxWidth must be finite.",
                severity = "refusal",
            ),
            layoutNaN.diagnostics.single(),
        )
        assertEquals(
            ParagraphLayoutDiagnostic(
                code = PARAGRAPH_LAYOUT_CONSTRAINT_NEGATIVE_DIAGNOSTIC_CODE,
                message = "maxWidth must be non-negative.",
                severity = "refusal",
            ),
            layoutNegative.diagnostics.single(),
        )
        assertTrue(layoutNaN.layoutRefused)
        assertTrue(layoutNegative.layoutRefused)
        assertTrue(layoutNaN.dump().contains("\"maxWidth\": \"NaN\""))
        assertTrue(layoutNegative.dump().contains("\"maxWidth\": -1.0"))
    }

    @Test
    fun basicTextSegmenterKeepsSurrogatePairsAndCombiningMarksTogether() {
        val text = "a\u0301\uD83D\uDE00b"

        val ranges = BasicTextSegmenter().segment(text)

        assertEquals(listOf(0..1, 2..3, 4..4), ranges)
    }

    @Test
    fun basicUnicodeDataCoversMinimalScriptsBidiClassesAndIgnorables() {
        val data = BasicUnicodeData

        assertEquals("Latn", data.scriptOf('A'.code))
        assertEquals("Arab", data.scriptOf(0x0627))
        assertEquals("Hebr", data.scriptOf(0x05D0))
        assertEquals("Zyyy", data.scriptOf('-'.code))
        assertEquals("Zinh", data.scriptOf(0x0301))
        assertEquals("Zsye", data.scriptOf(0x1F600))

        assertEquals("L", data.bidiClassOf('A'.code))
        assertEquals("AL", data.bidiClassOf(0x0627))
        assertEquals("R", data.bidiClassOf(0x05D0))
        assertEquals("NSM", data.bidiClassOf(0x0301))
        assertEquals("BN", data.bidiClassOf(0x200D))

        assertTrue(data.isDefaultIgnorable(0x200D))
        assertTrue(data.isDefaultIgnorable(0xFE0F))
        assertFalse(data.isDefaultIgnorable('A'.code))
    }

    @Test
    fun unicode16SourceManifestPinsOfflineMetadataAndNonClaims() {
        val manifest = readProjectFile("reports/font/fixtures/expected/unicode/unicode-16-source-manifest.json")

        assertTrue(manifest.contains("\"unicodeVersion\": \"16.0.0\""))
        assertTrue(manifest.contains("\"ordinaryValidationPolicy\": \"offline\""))
        assertTrue(manifest.contains("\"no-complete-ucd-claim\""))
        assertTrue(manifest.contains("\"no-bidi-or-script-itemizer-replacement-claim\""))
        assertTrue(manifest.contains("\"no-complete-uax29-claim\""))
        assertTrue(manifest.contains("\"no-shaping-support-promotion\""))
        assertTrue(manifest.contains("\"no-paragraph-support-claim\""))
        assertTrue(manifest.contains("\"no-gpu-text-route-claim\""))
    }

    @Test
    fun latinGsubGposGoldenPinsFixtureNonClaims() {
        val dump = readJsonProjectFile(
            "reports/font/fixtures/expected/shaping/latin-gsub-gpos-goldens.json",
        )
        val cases = dump.requiredObjectList("cases")

        assertEquals(1L, dump["schemaVersion"])
        assertEquals("latin-gsub-gpos-goldens", dump.requiredString("dumpId"))
        assertEquals(listOf("PKT-07B"), dump.requiredStringList("ownerTickets"))
        assertEquals("font-source-liberation-core", dump.requiredString("fixtureId"))
        assertEquals(listOf("latin-fi-liga-requested", "latin-kern-requested-off"), cases.map { it.requiredString("caseId") })
        assertEquals("office", cases[0].requiredString("text"))
        assertEquals(mapOf("liga" to true, "kern" to true), cases[0].requiredObject("features"))
        assertEquals(
            listOf("feature-order", "glyph-sequence", "cluster-map", "advance-adjustments"),
            cases[0].requiredStringList("requiredDumpFields"),
        )
        assertEquals("AV", cases[1].requiredString("text"))
        assertEquals(mapOf("liga" to false, "kern" to false), cases[1].requiredObject("features"))
        assertEquals(
            listOf("feature-order", "glyph-sequence", "cluster-map"),
            cases[1].requiredStringList("requiredDumpFields"),
        )
        assertEquals(
            listOf(
                "no-complete-gsub-gpos-support-claim",
                "no-greek-cyrillic-hebrew-promotion-claim",
                "no-native-shaper-oracle-claim",
            ),
            dump.requiredStringList("nonClaims"),
        )
        assertNoSupportPromotionClaims(dump)
    }

    @Test
    fun gsubTraceGoldenPinsFixtureBackedLatinCasesAndMalformedDiagnostics() {
        val dump = readJsonProjectFile("reports/font/fixtures/expected/shaping/gsub-trace.json")
        val cases = dump.requiredObjectList("cases")

        assertEquals(1L, dump.requiredLong("schemaVersion"))
        assertEquals("gsub-trace", dump.requiredString("dumpId"))
        assertEquals(listOf("KFONT-M6-002", "KFONT-M6-003"), dump.requiredStringList("ownerTickets"))
        assertEquals("latin-gsub-gpos-fixtures", dump.requiredString("fixtureFamilyId"))
        assertEquals(
            listOf(
                "single-substitution",
                "multiple-substitution",
                "ligature-fi",
                "context-format1-match",
                "context-format1-no-match",
                "context-format2-class",
                "context-format3-coverage",
                "context-nested-cycle",
                "context-malformed-classdef",
                "coverage-malformed",
                "ligature-bad-component",
            ),
            cases.map { it.requiredString("caseId") },
        )
        assertEquals(listOf("ccmp"), cases[0].requiredStringList("featureOrder"))
        assertEquals(listOf(552L), cases[0].requiredLongList("inputGlyphIds"))
        assertEquals(listOf(101L), cases[0].requiredLongList("outputGlyphIds"))
        assertEquals("preserved", cases[0].requiredObjectList("lookups").single().requiredString("clusterAction"))
        assertEquals(listOf(553L), cases[1].requiredLongList("inputGlyphIds"))
        assertEquals(listOf(101L, 102L), cases[1].requiredLongList("outputGlyphIds"))
        assertEquals("expanded-single-cluster", cases[1].requiredObjectList("lookups").single().requiredString("clusterAction"))
        assertEquals(listOf("liga"), cases[2].requiredStringList("featureOrder"))
        assertEquals(listOf(557L, 560L), cases[2].requiredLongList("inputGlyphIds"))
        assertEquals(listOf(103L), cases[2].requiredLongList("outputGlyphIds"))
        assertEquals("merged-clusters", cases[2].requiredObjectList("lookups").single().requiredString("clusterAction"))
        assertEquals(listOf("calt"), cases[3].requiredStringList("featureOrder"))
        assertEquals(listOf(552L, 553L), cases[3].requiredLongList("inputGlyphIds"))
        assertEquals(listOf(555L, 553L), cases[3].requiredLongList("outputGlyphIds"))
        assertEquals(1L, cases[3].requiredObjectList("lookups").single().requiredLong("contextFormat"))
        assertEquals(listOf(0L, 0L), cases[3].requiredObjectList("lookups").single().requiredLongLists("nestedLookupRecords")[0])
        assertEquals(listOf(552L, 554L), cases[4].requiredLongList("outputGlyphIds"))
        assertEquals(false, cases[4].requiredObjectList("lookups").single()["ruleMatched"])
        assertEquals(2L, cases[5].requiredObjectList("lookups").single().requiredLong("contextFormat"))
        assertEquals(listOf(1L, 2L, 3L), cases[5].requiredObjectList("lookups").single().requiredLongList("inputClassIds"))
        assertEquals(3L, cases[6].requiredObjectList("lookups").single().requiredLong("contextFormat"))
        assertEquals(listOf(listOf(552L), listOf(553L, 554L), listOf(555L)), cases[6].requiredLongLists("coverageGlyphSets"))
        assertEquals(
            "text.shaping.lookup-cycle-detected",
            cases[7].requiredObjectList("diagnostics").single().requiredString("code"),
        )
        assertEquals(
            "font.sfnt.optional-table-malformed",
            cases[8].requiredObjectList("diagnostics").single().requiredString("code"),
        )
        assertEquals(
            "font.sfnt.optional-table-malformed",
            cases[9].requiredObjectList("diagnostics").single().requiredString("code"),
        )
        assertNoSupportPromotionClaims(dump)
    }

    @Test
    fun gposTraceGoldenPinsFixtureBackedLatinCasesAndMalformedDiagnostics() {
        val dump = readJsonProjectFile("reports/font/fixtures/expected/shaping/gpos-trace.json")
        val cases = dump.requiredObjectList("cases")

        assertEquals(1L, dump.requiredLong("schemaVersion"))
        assertEquals("gpos-trace", dump.requiredString("dumpId"))
        assertEquals(listOf("KFONT-M6-004"), dump.requiredStringList("ownerTickets"))
        assertEquals("latin-gsub-gpos-fixtures", dump.requiredString("fixtureFamilyId"))
        assertEquals(
            listOf(
                "single-adjustment",
                "pair-format1-kerning",
                "pair-format2-class",
                "valueformat-malformed",
                "pair-out-of-range",
            ),
            cases.map { it.requiredString("caseId") },
        )
        assertEquals(listOf("kern"), cases[0].requiredStringList("featureOrder"))
        assertEquals(listOf(520L), cases[0].requiredLongList("glyphIds"))
        assertEquals(listOf(200L, 0L, 0L), cases[0].requiredLongLists("before")[0])
        assertEquals(listOf(192L, 10L, 0L), cases[0].requiredLongLists("after")[0])
        assertEquals(listOf(520L, 541L), cases[1].requiredLongList("glyphIds"))
        assertEquals(listOf(189L, 0L, 0L), cases[1].requiredLongLists("after")[0])
        assertEquals(listOf(188L, 0L, 0L), cases[2].requiredLongLists("after")[0])
        assertEquals(listOf(0L, 1L), cases[2].requiredObjectList("lookups").single().requiredLongList("classIds"))
        assertEquals(
            "font.sfnt.optional-table-malformed",
            cases[3].requiredObjectList("diagnostics").single().requiredString("code"),
        )
        assertEquals(
            "font.sfnt.optional-table-malformed",
            cases[4].requiredObjectList("diagnostics").single().requiredString("code"),
        )
        assertNoSupportPromotionClaims(dump)
    }

    @Test
    fun shapedGlyphRunGoldenPinsFixtureBackedGsubAndGposRuns() {
        val dump = readJsonProjectFile("reports/font/fixtures/expected/shaping/shaped-glyph-run.json")
        val cases = dump.requiredObjectList("cases")

        assertEquals(1L, dump.requiredLong("schemaVersion"))
        assertEquals("shaped-glyph-run", dump.requiredString("dumpId"))
        assertEquals(listOf("KFONT-M6-002", "KFONT-M6-003", "KFONT-M6-004"), dump.requiredStringList("ownerTickets"))
        assertEquals("latin-gsub-gpos-fixtures", dump.requiredString("fixtureFamilyId"))
        assertEquals(
            listOf(
                "gsub-single-substitution",
                "gsub-multiple-substitution",
                "gsub-ligature-fi",
                "gsub-context-format1-match",
                "gsub-context-format1-no-match",
                "gsub-context-format2-class",
                "gsub-context-format3-coverage",
                "gsub-context-nested-cycle",
                "gpos-single-adjustment",
                "gpos-pair-format1-kerning",
                "gpos-pair-format2-class",
            ),
            cases.map { it.requiredString("caseId") },
        )
        assertEquals(listOf(101L), cases[0].requiredLongList("glyphIds"))
        assertEquals("gsub-trace#single-substitution", cases[0].requiredString("traceRef"))
        assertEquals(listOf(101L, 102L), cases[1].requiredLongList("glyphIds"))
        assertEquals("0..1", cases[2].requiredObjectList("clusters").single().requiredString("textRange"))
        assertEquals(listOf(555L, 553L), cases[3].requiredLongList("glyphIds"))
        assertEquals("gsub-trace#context-format1-match", cases[3].requiredString("traceRef"))
        assertEquals(listOf(552L, 554L), cases[4].requiredLongList("glyphIds"))
        assertEquals(400L, cases[4].requiredLong("advanceX10"))
        assertEquals(listOf(556L, 553L, 554L), cases[5].requiredLongList("glyphIds"))
        assertEquals(listOf(557L, 553L, 555L), cases[6].requiredLongList("glyphIds"))
        assertEquals(
            "text.shaping.lookup-cycle-detected",
            cases[7].requiredObjectList("diagnostics").single().requiredString("code"),
        )
        assertEquals("gpos-trace#single-adjustment", cases[8].requiredString("traceRef"))
        assertEquals(listOf(192L, 10L, 0L), cases[8].requiredLongLists("clusterMetrics")[0])
        assertEquals(listOf(189L, 0L, 0L), cases[9].requiredLongLists("clusterMetrics")[0])
        assertEquals(listOf(188L, 0L, 0L), cases[10].requiredLongLists("clusterMetrics")[0])
        assertNoSupportPromotionClaims(dump)
    }

    @Test
    fun arabicSeedReadinessGoldenPinsDiagnosticsWithoutSupportClaim() {
        val dump = readJsonProjectFile(
            "reports/font/fixtures/expected/shaping/arabic-seed-readiness.json",
        )

        assertEquals(1L, dump["schemaVersion"])
        assertEquals("arabic-seed-readiness", dump.requiredString("dumpId"))
        assertEquals(listOf("PKT-08B"), dump.requiredStringList("ownerTickets"))
        assertEquals("Arabic", dump.requiredString("script"))
        assertEquals(
            listOf("joining-forms", "lam-alef", "marks", "cursive-attachment", "mixed-bidi"),
            dump.requiredStringList("cases"),
        )
        assertEquals(
            listOf(
                "text.shaping.cursive-attachment-unavailable",
                "text.shaping.mark-positioning-unavailable",
                "text.shaping.gdef-required",
                "text.shaping.paragraph-bidi-required",
            ),
            dump.requiredStringList("requiredDiagnostics"),
        )
        assertEquals(
            listOf(
                "no-arabic-shaping-support-claim",
                "no-complex-shaping-support-claim",
                "no-native-shaper-oracle-claim",
            ),
            dump.requiredStringList("nonClaims"),
        )
        assertNoSupportPromotionClaims(dump)
    }

    @Test
    fun paragraphInputGoldenPinsSchemaCasesAndNonClaims() {
        val dump = readJsonProjectFile(
            "reports/font/fixtures/expected/paragraph/paragraph-input-goldens.json",
        )
        val cases = dump.requiredObjectList("cases")
        val styleRuns = cases.single().requiredObjectList("styleRuns")

        assertEquals(2L, dump["schemaVersion"])
        assertEquals("paragraph-input-goldens", dump.requiredString("dumpId"))
        assertEquals(listOf("PKT-09C", "KFONT-M8-001"), dump.requiredStringList("ownerTickets"))
        assertEquals("rich-style-with-placeholder", cases.single().requiredString("caseId"))
        assertEquals("hello [box] world", cases.single().requiredString("text"))
        assertEquals(
            listOf("inputHash", "unicodeVersion", "paragraphStyle", "styleRuns", "placeholders", "diagnostics"),
            cases.single().requiredStringList("requiredDumpFields"),
        )
        assertEquals(listOf(0L, 5L), styleRuns[0].requiredLongList("range"))
        assertEquals("Liberation Sans", styleRuns[0].requiredString("family"))
        assertEquals("prefer-declared-families", styleRuns[0].requiredString("fallbackPreference"))
        assertEquals(listOf("wdth", "wght"), styleRuns[0].requiredStringList("variationAxes"))
        assertEquals(listOf(12L, 17L), styleRuns[1].requiredLongList("range"))
        assertEquals("Liberation Serif", styleRuns[1].requiredString("family"))
        assertEquals(listOf("kern"), styleRuns[1].requiredStringList("features"))
        assertEquals(listOf(listOf(6L, 11L)), cases.single().requiredLongLists("placeholderRanges"))
        assertEquals(
            listOf(
                "invalid-range",
                "invalid-font-size",
                "invalid-variation-coordinate",
                "non-finite-placeholder-metric",
                "unsupported-baseline",
                "unsupported-strut-policy",
            ),
            dump.requiredStringList("negativeCases"),
        )
        assertEquals(
            listOf("no-complete-paragraph-layout-claim", "no-skia-paragraph-parity-claim"),
            dump.requiredStringList("nonClaims"),
        )
        assertNoSupportPromotionClaims(dump)
    }

    @Test
    fun basicScriptItemizerAttachesCommonAndInheritedToAdjacentStrongScripts() {
        val request = ShapingRequest("ab-\u0301cd \u05D0\u05D1!")

        val runs = BasicScriptItemizer().itemize(request)

        assertEquals(
            listOf(
                ScriptRun(0..6, "Latn"),
                ScriptRun(7..9, "Hebr"),
            ),
            runs,
        )
    }

    @Test
    fun basicBidiResolverUsesParagraphDirectionAndBaseRtlScripts() {
        val resolver = BasicBidiResolver()

        val mixedRuns = resolver.resolve(ShapingRequest("ab \u05D0\u05D1!"))
        val explicitRtlRuns = resolver.resolve(ShapingRequest("ab", paragraphDirection = -1))

        assertEquals(
            listOf(
                BidiRun(0..2, level = 0, isRightToLeft = false),
                BidiRun(3..5, level = 1, isRightToLeft = true),
            ),
            mixedRuns,
        )
        assertEquals(listOf(BidiRun(0..1, level = 2, isRightToLeft = false)), explicitRtlRuns)
    }

    @Test
    fun basicOpenTypeShapingEngineMapsSurrogatePairWithoutCuttingCluster() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440301"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(0x1F600 to 600),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "a\uD83D\uDE00b",
                textRange = 2..2,
                typefaceId = typefaceId,
                fontSize = 20f,
            ),
        )

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(600),
                    clusters = listOf(GlyphCluster(textRange = 1..2, glyphRange = 0..0, advanceX = 20f)),
                    advanceX = 20f,
                    script = "Zsye",
                    bidiLevel = 0,
                    typefaceId = typefaceId,
                    fontSize = 20f,
                ),
            ),
            result.glyphRuns,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineExpandsRequestedRangeToClusterForScriptAndBidi() {
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'a'.code to 11,
                0x0301 to 12,
            ),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "a\u0301",
                textRange = 1..1,
                fontSize = 14f,
            ),
        )

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(11, 12),
                    clusters = listOf(GlyphCluster(textRange = 0..1, glyphRange = 0..1, advanceX = 14f)),
                    advanceX = 14f,
                    script = "Latn",
                    bidiLevel = 0,
                    fontSize = 14f,
                ),
            ),
            result.glyphRuns,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineGroupsScriptAndBidiRunsAndReportsMissingGlyphs() {
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'a'.code to 11,
                ' '.code to 12,
                0x05D0 to 21,
            ),
        )

        val result = engine.shape(ShapingRequest("a \u05D0\u2603", fontSize = 10f))

        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(11, 12),
                    clusters = listOf(
                        GlyphCluster(textRange = 0..0, glyphRange = 0..0, advanceX = 10f),
                        GlyphCluster(textRange = 1..1, glyphRange = 1..1, advanceX = 10f),
                    ),
                    advanceX = 20f,
                    script = "Latn",
                    bidiLevel = 0,
                    fontSize = 10f,
                ),
                ShapedGlyphRun(
                    glyphIds = listOf(21),
                    clusters = listOf(GlyphCluster(textRange = 2..2, glyphRange = 0..0, advanceX = 10f)),
                    advanceX = 10f,
                    script = "Hebr",
                    bidiLevel = 1,
                    fontSize = 10f,
                ),
                ShapedGlyphRun(
                    glyphIds = listOf(0),
                    clusters = listOf(GlyphCluster(textRange = 3..3, glyphRange = 0..0, advanceX = 10f)),
                    advanceX = 10f,
                    script = "Zsye",
                    bidiLevel = 1,
                    fontSize = 10f,
                ),
            ),
            result.glyphRuns,
        )
        assertEquals(
            listOf(
                ShapingDiagnostic(
                    code = TEXT_SHAPING_PARAGRAPH_BIDI_REQUIRED_DIAGNOSTIC_CODE,
                    message = "Paragraph-level visual bidi ordering is required for mixed-direction text; M8 owns line ordering.",
                    textRange = 0..3,
                ),
                ShapingDiagnostic(
                    code = MISSING_GLYPH_DIAGNOSTIC_CODE,
                    message = "Missing glyph for U+2603.",
                    textRange = 3..3,
                ),
            ),
            result.diagnostics,
        )
    }

    @Test
    fun cmapGlyphMapperUsesSfntCMapLookupForBasicShapingAndTypefaceRouting() {
        val primaryTypefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440401"))
        val alternateTypefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440402"))
        val mapper = CMapGlyphMapper(
            cmapsByTypefaceId = mapOf(
                primaryTypefaceId to cmapTable('A'.code to 101),
                alternateTypefaceId to cmapTable('A'.code to 201),
            ),
            defaultCMap = cmapTable('A'.code to 31),
        )
        val engine = BasicOpenTypeShapingEngine(glyphMapper = mapper)

        val primaryResult = engine.shape(
            ShapingRequest(
                text = "A\u2603",
                typefaceId = primaryTypefaceId,
                fontSize = 18f,
            ),
        )
        val alternateResult = engine.shape(
            ShapingRequest(
                text = "A",
                typefaceId = alternateTypefaceId,
                fontSize = 18f,
            ),
        )
        val fallbackResult = engine.shape(
            ShapingRequest(
                text = "A",
                fontSize = 18f,
            ),
        )

        assertEquals(listOf(101, 0), primaryResult.glyphRuns.flatMap { it.glyphIds })
        assertEquals(
            listOf(
                ShapingDiagnostic(
                    code = MISSING_GLYPH_DIAGNOSTIC_CODE,
                    message = "Missing glyph for U+2603.",
                    textRange = 1..1,
                ),
            ),
            primaryResult.diagnostics,
        )
        assertEquals(listOf(201), alternateResult.glyphRuns.flatMap { it.glyphIds })
        assertEquals(listOf(31), fallbackResult.glyphRuns.flatMap { it.glyphIds })
        assertEquals(null, mapper.glyphIdFor(primaryTypefaceId, 'B'.code))
        assertEquals(null, CMapGlyphMapper().glyphIdFor(null, 'A'.code))
    }

    @Test
    fun cmapGlyphMapperDoesNotUseDefaultCMapForUnknownExplicitTypeface() {
        val unknownTypefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440403"))
        val mapper = CMapGlyphMapper(
            defaultCMap = cmapTable('A'.code to 31),
        )
        val engine = BasicOpenTypeShapingEngine(glyphMapper = mapper)

        val unknownTypefaceResult = engine.shape(
            ShapingRequest(
                text = "A",
                typefaceId = unknownTypefaceId,
                fontSize = 18f,
            ),
        )
        val defaultTypefaceResult = engine.shape(
            ShapingRequest(
                text = "A",
                fontSize = 18f,
            ),
        )

        assertEquals(listOf(0), unknownTypefaceResult.glyphRuns.flatMap { it.glyphIds })
        assertEquals(
            listOf(
                ShapingDiagnostic(
                    code = MISSING_GLYPH_DIAGNOSTIC_CODE,
                    message = "Missing glyph for U+0041.",
                    textRange = 0..0,
                ),
            ),
            unknownTypefaceResult.diagnostics,
        )
        assertEquals(unknownTypefaceId, unknownTypefaceResult.glyphRuns.single().typefaceId)
        assertEquals(listOf(31), defaultTypefaceResult.glyphRuns.flatMap { it.glyphIds })
        assertEquals(emptyList(), defaultTypefaceResult.diagnostics)
    }

    @Test
    fun basicOpenTypeShapingEngineAppliesParsedKernPairsInFontSizeUnits() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440404"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'A'.code to 7,
                'V'.code to 11,
                'T'.code to 19,
            ),
            kernTablesByTypefaceId = mapOf(
                typefaceId to openTypeKernTable(
                    OpenTypeKernPair(leftGlyphId = 7, rightGlyphId = 11, value = -80),
                    OpenTypeKernPair(leftGlyphId = 11, rightGlyphId = 19, value = 40),
                ),
            ),
            kernUnitsPerEmByTypefaceId = mapOf(typefaceId to 1000),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "AVT",
                typefaceId = typefaceId,
                fontSize = 20f,
            ),
        )

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(7, 11, 19),
                    clusters = listOf(
                        GlyphCluster(textRange = 0..0, glyphRange = 0..0, advanceX = 18.4f),
                        GlyphCluster(textRange = 1..1, glyphRange = 1..1, advanceX = 20.8f),
                        GlyphCluster(textRange = 2..2, glyphRange = 2..2, advanceX = 20f),
                    ),
                    advanceX = 59.2f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = typefaceId,
                    fontSize = 20f,
                ),
            ),
            result.glyphRuns,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineAppliesParsedGposPairsInFontSizeUnits() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440408"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'A'.code to 7,
                'V'.code to 11,
            ),
            gposPairTablesByTypefaceId = mapOf(
                typefaceId to OpenTypeGposPairTable(
                    pairs = listOf(
                        OpenTypeGposPairAdjustment(leftGlyphId = 7, rightGlyphId = 11, xAdvance = -50),
                    ),
                ),
            ),
            kernUnitsPerEmByTypefaceId = mapOf(typefaceId to 1000),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "AV",
                typefaceId = typefaceId,
                fontSize = 20f,
            ),
        )

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(7, 11),
                    clusters = listOf(
                        GlyphCluster(textRange = 0..0, glyphRange = 0..0, advanceX = 19f),
                        GlyphCluster(textRange = 1..1, glyphRange = 1..1, advanceX = 20f),
                    ),
                    advanceX = 39f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = typefaceId,
                    fontSize = 20f,
                ),
            ),
            result.glyphRuns,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineAppliesParsedGposSingleAndPairValueRecordsInFontSizeUnits() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440409"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'A'.code to 7,
                'V'.code to 11,
            ),
            gposSingleTablesByTypefaceId = mapOf(
                typefaceId to OpenTypeGposSingleTable(
                    adjustments = listOf(
                        OpenTypeGposSingleAdjustment(
                            glyphId = 7,
                            valueRecord = OpenTypeGposValueRecord(
                                xPlacement = 50,
                                yPlacement = -25,
                            ),
                        ),
                    ),
                ),
            ),
            gposPairTablesByTypefaceId = mapOf(
                typefaceId to OpenTypeGposPairTable(
                    pairs = listOf(
                        OpenTypeGposPairAdjustment(
                            leftGlyphId = 7,
                            rightGlyphId = 11,
                            firstValueRecord = OpenTypeGposValueRecord(xAdvance = -40),
                            secondValueRecord = OpenTypeGposValueRecord(
                                xPlacement = 20,
                                xAdvance = 10,
                            ),
                        ),
                    ),
                ),
            ),
            kernUnitsPerEmByTypefaceId = mapOf(typefaceId to 1000),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "AV",
                typefaceId = typefaceId,
                fontSize = 20f,
            ),
        )

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(7, 11),
                    clusters = listOf(
                        GlyphCluster(
                            textRange = 0..0,
                            glyphRange = 0..0,
                            advanceX = 19.2f,
                            offsetX = 1f,
                            offsetY = -0.5f,
                        ),
                        GlyphCluster(
                            textRange = 1..1,
                            glyphRange = 1..1,
                            advanceX = 20.2f,
                            offsetX = 0.4f,
                        ),
                    ),
                    advanceX = 39.4f,
                    advanceY = 0f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = typefaceId,
                    fontSize = 20f,
                ),
            ),
            result.glyphRuns,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineSkipsGposPairAdjustmentsWhenKernFeatureIsDisabled() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-44665544040a"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'A'.code to 7,
                'V'.code to 11,
            ),
            gposPairTablesByTypefaceId = mapOf(
                typefaceId to OpenTypeGposPairTable(
                    pairs = listOf(
                        OpenTypeGposPairAdjustment(
                            leftGlyphId = 7,
                            rightGlyphId = 11,
                            firstValueRecord = OpenTypeGposValueRecord(xAdvance = -60),
                        ),
                    ),
                ),
            ),
            kernUnitsPerEmByTypefaceId = mapOf(typefaceId to 1000),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "AV",
                typefaceId = typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("kern" to 0)),
            ),
        )

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(7, 11),
                    clusters = listOf(
                        GlyphCluster(textRange = 0..0, glyphRange = 0..0, advanceX = 20f),
                        GlyphCluster(textRange = 1..1, glyphRange = 1..1, advanceX = 20f),
                    ),
                    advanceX = 40f,
                    advanceY = 0f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = typefaceId,
                    fontSize = 20f,
                ),
            ),
            result.glyphRuns,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineAppliesParsedGsubSingleMultipleAndLigatureLookups() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440406"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'a'.code to 5,
                'b'.code to 6,
                'f'.code to 7,
                'i'.code to 8,
            ),
            gsubTablesByTypefaceId = mapOf(
                typefaceId to OpenTypeGsubTable(
                    lookups = listOf(
                        OpenTypeGsubSingleSubstitutionLookup(
                            featureTag = "ccmp",
                            substitutions = listOf(
                                OpenTypeGsubSingleSubstitution(
                                    inputGlyphId = 5,
                                    replacementGlyphId = 15,
                                ),
                            ),
                        ),
                        OpenTypeGsubMultipleSubstitutionLookup(
                            featureTag = "ccmp",
                            substitutions = listOf(
                                OpenTypeGsubMultipleSubstitution(
                                    inputGlyphId = 6,
                                    replacementGlyphIds = listOf(16, 17),
                                ),
                            ),
                        ),
                        OpenTypeGsubLigatureSubstitutionLookup(
                            featureTag = "liga",
                            substitutions = listOf(
                                OpenTypeGsubLigatureSubstitution(
                                    inputGlyphIds = listOf(7, 8),
                                    replacementGlyphId = 42,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "abfi",
                typefaceId = typefaceId,
                fontSize = 20f,
            ),
        )

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(15, 16, 17, 42),
                    clusters = listOf(
                        GlyphCluster(textRange = 0..0, glyphRange = 0..0, advanceX = 20f),
                        GlyphCluster(textRange = 1..1, glyphRange = 1..2, advanceX = 20f),
                        GlyphCluster(textRange = 2..3, glyphRange = 3..3, advanceX = 20f),
                    ),
                    advanceX = 60f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = typefaceId,
                    fontSize = 20f,
                ),
            ),
            result.glyphRuns,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineRespectsDisabledParsedGsubLigatureFeature() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440407"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'a'.code to 5,
                'b'.code to 6,
                'f'.code to 7,
                'i'.code to 8,
            ),
            gsubTablesByTypefaceId = mapOf(
                typefaceId to OpenTypeGsubTable(
                    lookups = listOf(
                        OpenTypeGsubSingleSubstitutionLookup(
                            featureTag = "ccmp",
                            substitutions = listOf(
                                OpenTypeGsubSingleSubstitution(
                                    inputGlyphId = 5,
                                    replacementGlyphId = 15,
                                ),
                            ),
                        ),
                        OpenTypeGsubMultipleSubstitutionLookup(
                            featureTag = "ccmp",
                            substitutions = listOf(
                                OpenTypeGsubMultipleSubstitution(
                                    inputGlyphId = 6,
                                    replacementGlyphIds = listOf(16, 17),
                                ),
                            ),
                        ),
                        OpenTypeGsubLigatureSubstitutionLookup(
                            featureTag = "liga",
                            substitutions = listOf(
                                OpenTypeGsubLigatureSubstitution(
                                    inputGlyphIds = listOf(7, 8),
                                    replacementGlyphId = 42,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "abfi",
                typefaceId = typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("liga" to 0)),
            ),
        )

        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(15, 16, 17, 7, 8),
                    clusters = listOf(
                        GlyphCluster(textRange = 0..0, glyphRange = 0..0, advanceX = 20f),
                        GlyphCluster(textRange = 1..1, glyphRange = 1..2, advanceX = 20f),
                        GlyphCluster(textRange = 2..2, glyphRange = 3..3, advanceX = 20f),
                        GlyphCluster(textRange = 3..3, glyphRange = 4..4, advanceX = 20f),
                    ),
                    advanceX = 80f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = typefaceId,
                    fontSize = 20f,
                ),
            ),
            result.glyphRuns,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineAppliesReviewedGsubFixtureFontsFromRepo() {
        val single = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440611",
            relativePath = "reports/font/fixtures/fonts/shaping/gsub-single-substitution.otf",
        )
        val multiple = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440612",
            relativePath = "reports/font/fixtures/fonts/shaping/gsub-multiple-substitution.otf",
        )
        val ligature = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440613",
            relativePath = "reports/font/fixtures/fonts/shaping/gsub-ligature-fi.otf",
        )
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = CMapGlyphMapper(
                cmapsByTypefaceId = mapOf(
                    single.typefaceId to single.cmap,
                    multiple.typefaceId to multiple.cmap,
                    ligature.typefaceId to ligature.cmap,
                ),
            ),
            gsubTablesByTypefaceId = mapOf(
                single.typefaceId to requireNotNull(single.gsub),
                multiple.typefaceId to requireNotNull(multiple.gsub),
                ligature.typefaceId to requireNotNull(ligature.gsub),
            ),
        )

        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(101),
                    clusters = listOf(GlyphCluster(textRange = 0..0, glyphRange = 0..0, advanceX = 20f)),
                    advanceX = 20f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = single.typefaceId,
                    fontSize = 20f,
                ),
            ),
            engine.shape(
                ShapingRequest(
                    text = "a",
                    typefaceId = single.typefaceId,
                    fontSize = 20f,
                ),
            ).glyphRuns,
        )
        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(101, 102),
                    clusters = listOf(GlyphCluster(textRange = 0..0, glyphRange = 0..1, advanceX = 20f)),
                    advanceX = 20f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = multiple.typefaceId,
                    fontSize = 20f,
                ),
            ),
            engine.shape(
                ShapingRequest(
                    text = "b",
                    typefaceId = multiple.typefaceId,
                    fontSize = 20f,
                ),
            ).glyphRuns,
        )
        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(103),
                    clusters = listOf(GlyphCluster(textRange = 0..1, glyphRange = 0..0, advanceX = 20f)),
                    advanceX = 20f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = ligature.typefaceId,
                    fontSize = 20f,
                ),
            ),
            engine.shape(
                ShapingRequest(
                    text = "fi",
                    typefaceId = ligature.typefaceId,
                    fontSize = 20f,
                ),
            ).glyphRuns,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineAppliesReviewedGsubContextFixtureFontsFromRepo() {
        val format1 = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440614",
            relativePath = "reports/font/fixtures/fonts/shaping/gsub-context-format1.otf",
        )
        val format2 = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440615",
            relativePath = "reports/font/fixtures/fonts/shaping/gsub-context-format2-class.otf",
        )
        val format3 = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440616",
            relativePath = "reports/font/fixtures/fonts/shaping/gsub-context-format3-coverage.otf",
        )
        val cycle = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440617",
            relativePath = "reports/font/fixtures/fonts/shaping/gsub-context-nested-cycle.otf",
        )
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = CMapGlyphMapper(
                cmapsByTypefaceId = mapOf(
                    format1.typefaceId to format1.cmap,
                    format2.typefaceId to format2.cmap,
                    format3.typefaceId to format3.cmap,
                    cycle.typefaceId to cycle.cmap,
                ),
            ),
            gsubTablesByTypefaceId = mapOf(
                format1.typefaceId to requireNotNull(format1.gsub),
                format2.typefaceId to requireNotNull(format2.gsub),
                format3.typefaceId to requireNotNull(format3.gsub),
                cycle.typefaceId to requireNotNull(cycle.gsub),
            ),
        )

        val format1Match = engine.shape(
            ShapingRequest(
                text = "ab",
                typefaceId = format1.typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("ccmp" to 0, "calt" to 1)),
            ),
        )
        assertEquals(emptyList(), format1Match.diagnostics)
        assertEquals(listOf(555, 553), format1Match.glyphRuns.single().glyphIds)

        val format1Miss = engine.shape(
            ShapingRequest(
                text = "ac",
                typefaceId = format1.typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("ccmp" to 0, "calt" to 1)),
            ),
        )
        assertEquals(emptyList(), format1Miss.diagnostics)
        assertEquals(listOf(552, 554), format1Miss.glyphRuns.single().glyphIds)

        val format2Match = engine.shape(
            ShapingRequest(
                text = "abc",
                typefaceId = format2.typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("ccmp" to 0, "calt" to 1)),
            ),
        )
        assertEquals(emptyList(), format2Match.diagnostics)
        assertEquals(listOf(556, 553, 554), format2Match.glyphRuns.single().glyphIds)

        val format3Match = engine.shape(
            ShapingRequest(
                text = "abd",
                typefaceId = format3.typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("ccmp" to 0, "calt" to 1)),
            ),
        )
        assertEquals(emptyList(), format3Match.diagnostics)
        assertEquals(listOf(557, 553, 555), format3Match.glyphRuns.single().glyphIds)

        val cycleResult = engine.shape(
            ShapingRequest(
                text = "ab",
                typefaceId = cycle.typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("ccmp" to 0, "calt" to 1)),
            ),
        )
        assertEquals(
            listOf(
                ShapingDiagnostic(
                    code = "text.shaping.lookup-cycle-detected",
                    message = "GSUB contextual nested lookup cycle detected.",
                    textRange = 0..1,
                ),
            ),
            cycleResult.diagnostics,
        )
        assertEquals(listOf(552, 553), cycleResult.glyphRuns.single().glyphIds)
    }

    @Test
    fun basicOpenTypeShapingEngineRequiresCoverageGateForParsedGsubContextClassRules() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440618"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'a'.code to 552,
                'x'.code to 600,
                'b'.code to 553,
                'c'.code to 554,
            ),
            gsubTablesByTypefaceId = mapOf(
                typefaceId to OpenTypeGsubTable(
                    lookups = listOf(
                        OpenTypeGsubSingleSubstitutionLookup(
                            featureTag = "ccmp",
                            lookupIndex = 0,
                            substitutions = listOf(
                                OpenTypeGsubSingleSubstitution(inputGlyphId = 552, replacementGlyphId = 556),
                            ),
                        ),
                        OpenTypeGsubContextClassLookup(
                            featureTag = "calt",
                            lookupIndex = 1,
                            firstGlyphCoverage = setOf(552),
                            classDefinitions = mapOf(
                                552 to 1,
                                600 to 1,
                                553 to 2,
                                554 to 3,
                            ),
                            rules = listOf(
                                OpenTypeGsubContextClassRule(
                                    inputClasses = listOf(1, 2, 3),
                                    nestedLookups = listOf(
                                        OpenTypeGsubNestedLookupRecord(sequenceIndex = 0, lookupIndex = 0),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val positive = engine.shape(
            ShapingRequest(
                text = "abc",
                typefaceId = typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("ccmp" to 0, "calt" to 1)),
            ),
        )
        assertEquals(emptyList(), positive.diagnostics)
        assertEquals(listOf(556, 553, 554), positive.glyphRuns.single().glyphIds)

        val negative = engine.shape(
            ShapingRequest(
                text = "xbc",
                typefaceId = typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("ccmp" to 0, "calt" to 1)),
            ),
        )
        assertEquals(emptyList(), negative.diagnostics)
        assertEquals(listOf(600, 553, 554), negative.glyphRuns.single().glyphIds)
    }

    @Test
    fun basicOpenTypeShapingEngineKeepsNestedLookupSequencePositionsStableAfterExpansion() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440619"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'a'.code to 5,
                'b'.code to 6,
            ),
            gsubTablesByTypefaceId = mapOf(
                typefaceId to OpenTypeGsubTable(
                    lookups = listOf(
                        OpenTypeGsubMultipleSubstitutionLookup(
                            featureTag = "ccmp",
                            lookupIndex = 0,
                            substitutions = listOf(
                                OpenTypeGsubMultipleSubstitution(
                                    inputGlyphId = 5,
                                    replacementGlyphIds = listOf(15, 16),
                                ),
                            ),
                        ),
                        OpenTypeGsubSingleSubstitutionLookup(
                            featureTag = "ccmp",
                            lookupIndex = 1,
                            substitutions = listOf(
                                OpenTypeGsubSingleSubstitution(inputGlyphId = 6, replacementGlyphId = 26),
                            ),
                        ),
                        OpenTypeGsubContextGlyphLookup(
                            featureTag = "calt",
                            lookupIndex = 2,
                            rules = listOf(
                                OpenTypeGsubContextGlyphRule(
                                    inputGlyphIds = listOf(5, 6),
                                    nestedLookups = listOf(
                                        OpenTypeGsubNestedLookupRecord(sequenceIndex = 0, lookupIndex = 0),
                                        OpenTypeGsubNestedLookupRecord(sequenceIndex = 1, lookupIndex = 1),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "ab",
                typefaceId = typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("ccmp" to 0, "calt" to 1)),
            ),
        )

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(listOf(15, 16, 26), result.glyphRuns.single().glyphIds)
    }

    @Test
    fun basicOpenTypeShapingEngineDiagnosesOutOfRangeContextNestedSequenceIndices() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440620"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'a'.code to 5,
                'b'.code to 6,
            ),
            gsubTablesByTypefaceId = mapOf(
                typefaceId to OpenTypeGsubTable(
                    lookups = listOf(
                        OpenTypeGsubSingleSubstitutionLookup(
                            featureTag = "ccmp",
                            lookupIndex = 0,
                            substitutions = listOf(
                                OpenTypeGsubSingleSubstitution(inputGlyphId = 5, replacementGlyphId = 15),
                            ),
                        ),
                        OpenTypeGsubContextGlyphLookup(
                            featureTag = "calt",
                            lookupIndex = 1,
                            rules = listOf(
                                OpenTypeGsubContextGlyphRule(
                                    inputGlyphIds = listOf(5, 6),
                                    nestedLookups = listOf(
                                        OpenTypeGsubNestedLookupRecord(sequenceIndex = 2, lookupIndex = 0),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "ab",
                typefaceId = typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("ccmp" to 0, "calt" to 1)),
            ),
        )

        assertEquals(listOf(5, 6), result.glyphRuns.single().glyphIds)
        assertEquals(
            listOf(
                ShapingDiagnostic(
                    code = TEXT_SHAPING_LOOKUP_MALFORMED_DIAGNOSTIC_CODE,
                    message = "GSUB contextual nested lookup sequence index is outside the matched glyph range.",
                    textRange = 0..1,
                ),
            ),
            result.diagnostics,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineDiagnosesMissingContextNestedLookupIndices() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440632"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'a'.code to 5,
                'b'.code to 6,
            ),
            gsubTablesByTypefaceId = mapOf(
                typefaceId to OpenTypeGsubTable(
                    lookups = listOf(
                        OpenTypeGsubContextGlyphLookup(
                            featureTag = "calt",
                            lookupIndex = 0,
                            rules = listOf(
                                OpenTypeGsubContextGlyphRule(
                                    inputGlyphIds = listOf(5, 6),
                                    nestedLookups = listOf(
                                        OpenTypeGsubNestedLookupRecord(sequenceIndex = 0, lookupIndex = 7),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "ab",
                typefaceId = typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("calt" to 1)),
            ),
        )

        assertEquals(listOf(5, 6), result.glyphRuns.single().glyphIds)
        assertEquals(
            listOf(
                ShapingDiagnostic(
                    code = TEXT_SHAPING_LOOKUP_MALFORMED_DIAGNOSTIC_CODE,
                    message = "GSUB contextual nested lookup index is missing from the lookup list.",
                    textRange = 0..1,
                ),
            ),
            result.diagnostics,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineRollsBackEarlierNestedMutationsWhenALaterContextLookupFails() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440634"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'a'.code to 5,
                'b'.code to 6,
            ),
            gsubTablesByTypefaceId = mapOf(
                typefaceId to OpenTypeGsubTable(
                    lookups = listOf(
                        OpenTypeGsubSingleSubstitutionLookup(
                            featureTag = "salt",
                            lookupIndex = 0,
                            substitutions = listOf(
                                OpenTypeGsubSingleSubstitution(inputGlyphId = 6, replacementGlyphId = 16),
                            ),
                        ),
                        OpenTypeGsubContextGlyphLookup(
                            featureTag = "calt",
                            lookupIndex = 1,
                            rules = listOf(
                                OpenTypeGsubContextGlyphRule(
                                    inputGlyphIds = listOf(5, 6),
                                    nestedLookups = listOf(
                                        OpenTypeGsubNestedLookupRecord(sequenceIndex = 1, lookupIndex = 0),
                                        OpenTypeGsubNestedLookupRecord(sequenceIndex = 0, lookupIndex = 7),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "ab",
                typefaceId = typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("salt" to 0, "calt" to 1)),
            ),
        )

        assertEquals(listOf(5, 6), result.glyphRuns.single().glyphIds)
        assertEquals(
            listOf(
                ShapingDiagnostic(
                    code = TEXT_SHAPING_LOOKUP_MALFORMED_DIAGNOSTIC_CODE,
                    message = "GSUB contextual nested lookup index is missing from the lookup list.",
                    textRange = 0..1,
                ),
            ),
            result.diagnostics,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineRefusesContextLookupsThatBreakClusterInvariant() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440633"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'a'.code to 5,
                'b'.code to 6,
                'c'.code to 7,
            ),
            gsubTablesByTypefaceId = mapOf(
                typefaceId to OpenTypeGsubTable(
                    lookups = listOf(
                        OpenTypeGsubLigatureSubstitutionLookup(
                            featureTag = "liga",
                            lookupIndex = 0,
                            substitutions = listOf(
                                OpenTypeGsubLigatureSubstitution(
                                    inputGlyphIds = listOf(5, 6, 7),
                                    replacementGlyphId = 15,
                                ),
                            ),
                        ),
                        OpenTypeGsubContextGlyphLookup(
                            featureTag = "calt",
                            lookupIndex = 1,
                            rules = listOf(
                                OpenTypeGsubContextGlyphRule(
                                    inputGlyphIds = listOf(5, 6),
                                    nestedLookups = listOf(
                                        OpenTypeGsubNestedLookupRecord(sequenceIndex = 0, lookupIndex = 0),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "abc",
                typefaceId = typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("liga" to 0, "calt" to 1)),
            ),
        )

        assertEquals(listOf(5, 6, 7), result.glyphRuns.single().glyphIds)
        assertEquals(
            listOf(
                ShapingDiagnostic(
                    code = TEXT_SHAPING_CLUSTER_INVARIANT_FAILED_DIAGNOSTIC_CODE,
                    message = "GSUB contextual lookup left the matched cluster range.",
                    textRange = 0..1,
                ),
            ),
            result.diagnostics,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineRefusesLaterNestedLookupsThatEscapeTheMatchedCluster() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440635"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'a'.code to 5,
                'b'.code to 6,
                'c'.code to 7,
            ),
            gsubTablesByTypefaceId = mapOf(
                typefaceId to OpenTypeGsubTable(
                    lookups = listOf(
                        OpenTypeGsubLigatureSubstitutionLookup(
                            featureTag = "liga",
                            lookupIndex = 0,
                            substitutions = listOf(
                                OpenTypeGsubLigatureSubstitution(
                                    inputGlyphIds = listOf(6, 7),
                                    replacementGlyphId = 17,
                                ),
                            ),
                        ),
                        OpenTypeGsubContextGlyphLookup(
                            featureTag = "calt",
                            lookupIndex = 1,
                            rules = listOf(
                                OpenTypeGsubContextGlyphRule(
                                    inputGlyphIds = listOf(5, 6),
                                    nestedLookups = listOf(
                                        OpenTypeGsubNestedLookupRecord(sequenceIndex = 1, lookupIndex = 0),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "abc",
                typefaceId = typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("liga" to 0, "calt" to 1)),
            ),
        )

        assertEquals(listOf(5, 6, 7), result.glyphRuns.single().glyphIds)
        assertEquals(
            listOf(
                ShapingDiagnostic(
                    code = TEXT_SHAPING_CLUSTER_INVARIANT_FAILED_DIAGNOSTIC_CODE,
                    message = "GSUB contextual lookup left the matched cluster range.",
                    textRange = 0..1,
                ),
            ),
            result.diagnostics,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineRefusesNestedContextLookupsThatEscapeTheOuterMatchedCluster() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440636"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'a'.code to 5,
                'b'.code to 6,
                'c'.code to 7,
            ),
            gsubTablesByTypefaceId = mapOf(
                typefaceId to OpenTypeGsubTable(
                    lookups = listOf(
                        OpenTypeGsubLigatureSubstitutionLookup(
                            featureTag = "liga",
                            lookupIndex = 0,
                            substitutions = listOf(
                                OpenTypeGsubLigatureSubstitution(
                                    inputGlyphIds = listOf(6, 7),
                                    replacementGlyphId = 17,
                                ),
                            ),
                        ),
                        OpenTypeGsubContextGlyphLookup(
                            featureTag = "clig",
                            lookupIndex = 1,
                            rules = listOf(
                                OpenTypeGsubContextGlyphRule(
                                    inputGlyphIds = listOf(6, 7),
                                    nestedLookups = listOf(
                                        OpenTypeGsubNestedLookupRecord(sequenceIndex = 0, lookupIndex = 0),
                                    ),
                                ),
                            ),
                        ),
                        OpenTypeGsubContextGlyphLookup(
                            featureTag = "calt",
                            lookupIndex = 2,
                            rules = listOf(
                                OpenTypeGsubContextGlyphRule(
                                    inputGlyphIds = listOf(5, 6),
                                    nestedLookups = listOf(
                                        OpenTypeGsubNestedLookupRecord(sequenceIndex = 1, lookupIndex = 1),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "abc",
                typefaceId = typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("liga" to 0, "clig" to 0, "calt" to 1)),
            ),
        )

        assertEquals(listOf(5, 6, 7), result.glyphRuns.single().glyphIds)
        assertEquals(
            listOf(
                ShapingDiagnostic(
                    code = TEXT_SHAPING_CLUSTER_INVARIANT_FAILED_DIAGNOSTIC_CODE,
                    message = "GSUB contextual lookup left the matched cluster range.",
                    textRange = 0..1,
                ),
            ),
            result.diagnostics,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineKeepsFormat2SubtablesIndependentWithinOneLookup() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440621"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'a'.code to 10,
                'x'.code to 20,
                'b'.code to 11,
            ),
            gsubTablesByTypefaceId = mapOf(
                typefaceId to OpenTypeGsubTable(
                    lookups = listOf(
                        OpenTypeGsubSingleSubstitutionLookup(
                            featureTag = "ccmp",
                            lookupIndex = 0,
                            substitutions = listOf(
                                OpenTypeGsubSingleSubstitution(inputGlyphId = 10, replacementGlyphId = 30),
                            ),
                        ),
                        OpenTypeGsubSingleSubstitutionLookup(
                            featureTag = "ccmp",
                            lookupIndex = 1,
                            substitutions = listOf(
                                OpenTypeGsubSingleSubstitution(inputGlyphId = 20, replacementGlyphId = 40),
                            ),
                        ),
                        OpenTypeGsubContextClassLookup(
                            featureTag = "calt",
                            lookupIndex = 2,
                            subtables = listOf(
                                OpenTypeGsubContextClassSubtable(
                                    firstGlyphCoverage = setOf(10),
                                    classDefinitions = mapOf(10 to 1, 11 to 2),
                                    rules = listOf(
                                        OpenTypeGsubContextClassRule(
                                            inputClasses = listOf(1, 2),
                                            nestedLookups = listOf(
                                                OpenTypeGsubNestedLookupRecord(sequenceIndex = 0, lookupIndex = 0),
                                            ),
                                        ),
                                    ),
                                ),
                                OpenTypeGsubContextClassSubtable(
                                    firstGlyphCoverage = setOf(20),
                                    classDefinitions = mapOf(20 to 1, 11 to 2),
                                    rules = listOf(
                                        OpenTypeGsubContextClassRule(
                                            inputClasses = listOf(1, 2),
                                            nestedLookups = listOf(
                                                OpenTypeGsubNestedLookupRecord(sequenceIndex = 0, lookupIndex = 1),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val aResult = engine.shape(
            ShapingRequest(
                text = "ab",
                typefaceId = typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("ccmp" to 0, "calt" to 1)),
            ),
        )
        assertEquals(emptyList(), aResult.diagnostics)
        assertEquals(listOf(30, 11), aResult.glyphRuns.single().glyphIds)

        val xResult = engine.shape(
            ShapingRequest(
                text = "xb",
                typefaceId = typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("ccmp" to 0, "calt" to 1)),
            ),
        )
        assertEquals(emptyList(), xResult.diagnostics)
        assertEquals(listOf(40, 11), xResult.glyphRuns.single().glyphIds)
    }

    @Test
    fun basicOpenTypeShapingEngineAppliesStandardFiLigatureWhenAvailable() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440406"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'f'.code to 7,
                'i'.code to 8,
                0xFB01 to 64257,
            ),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "fi",
                typefaceId = typefaceId,
                fontSize = 20f,
            ),
        )

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(64257),
                    clusters = listOf(GlyphCluster(textRange = 0..1, glyphRange = 0..0, advanceX = 20f)),
                    advanceX = 20f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = typefaceId,
                    fontSize = 20f,
                ),
            ),
            result.glyphRuns,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineRespectsDisabledStandardLigatureFeature() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440407"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'f'.code to 7,
                'i'.code to 8,
                0xFB01 to 64257,
            ),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "fi",
                typefaceId = typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("liga" to 0)),
            ),
        )

        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(7, 8),
                    clusters = listOf(
                        GlyphCluster(textRange = 0..0, glyphRange = 0..0, advanceX = 20f),
                        GlyphCluster(textRange = 1..1, glyphRange = 1..1, advanceX = 20f),
                    ),
                    advanceX = 40f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = typefaceId,
                    fontSize = 20f,
                ),
            ),
            result.glyphRuns,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineAppliesReviewedGposFixtureFontsFromRepo() {
        val single = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440621",
            relativePath = "reports/font/fixtures/fonts/shaping/gpos-single-adjustment.otf",
        )
        val pairFormat1 = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440622",
            relativePath = "reports/font/fixtures/fonts/shaping/gpos-pair-format1-kerning.otf",
        )
        val pairFormat2 = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440623",
            relativePath = "reports/font/fixtures/fonts/shaping/gpos-pair-format2-class.otf",
        )
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = CMapGlyphMapper(
                cmapsByTypefaceId = mapOf(
                    single.typefaceId to single.cmap,
                    pairFormat1.typefaceId to pairFormat1.cmap,
                    pairFormat2.typefaceId to pairFormat2.cmap,
                ),
            ),
            gposSingleTablesByTypefaceId = mapOf(
                single.typefaceId to requireNotNull(single.gposSingles),
            ),
            gposPairTablesByTypefaceId = mapOf(
                pairFormat1.typefaceId to requireNotNull(pairFormat1.gposPairs),
                pairFormat2.typefaceId to requireNotNull(pairFormat2.gposPairs),
            ),
            kernUnitsPerEmByTypefaceId = mapOf(
                single.typefaceId to single.unitsPerEm,
                pairFormat1.typefaceId to pairFormat1.unitsPerEm,
                pairFormat2.typefaceId to pairFormat2.unitsPerEm,
            ),
        )

        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(520),
                    clusters = listOf(
                        GlyphCluster(
                            textRange = 0..0,
                            glyphRange = 0..0,
                            advanceX = 19.2f,
                            offsetX = 1f,
                        ),
                    ),
                    advanceX = 19.2f,
                    advanceY = 0f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = single.typefaceId,
                    fontSize = 20f,
                ),
            ),
            engine.shape(
                ShapingRequest(
                    text = "A",
                    typefaceId = single.typefaceId,
                    fontSize = 20f,
                ),
            ).glyphRuns,
        )
        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(520, 541),
                    clusters = listOf(
                        GlyphCluster(textRange = 0..0, glyphRange = 0..0, advanceX = 18.9f),
                        GlyphCluster(textRange = 1..1, glyphRange = 1..1, advanceX = 20f),
                    ),
                    advanceX = 38.9f,
                    advanceY = 0f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = pairFormat1.typefaceId,
                    fontSize = 20f,
                ),
            ),
            engine.shape(
                ShapingRequest(
                    text = "AV",
                    typefaceId = pairFormat1.typefaceId,
                    fontSize = 20f,
                ),
            ).glyphRuns,
        )
        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(520, 541),
                    clusters = listOf(
                        GlyphCluster(textRange = 0..0, glyphRange = 0..0, advanceX = 18.8f),
                        GlyphCluster(textRange = 1..1, glyphRange = 1..1, advanceX = 20f),
                    ),
                    advanceX = 38.8f,
                    advanceY = 0f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = pairFormat2.typefaceId,
                    fontSize = 20f,
                ),
            ),
            engine.shape(
                ShapingRequest(
                    text = "AV",
                    typefaceId = pairFormat2.typefaceId,
                    fontSize = 20f,
                ),
            ).glyphRuns,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineReportsKernTableThatCannotApplyWithoutUnitsPerEm() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440405"))
        val engine = BasicOpenTypeShapingEngine(
            glyphMapper = mapGlyphs(
                'A'.code to 7,
                'V'.code to 11,
            ),
            kernTablesByTypefaceId = mapOf(
                typefaceId to openTypeKernTable(
                    OpenTypeKernPair(leftGlyphId = 7, rightGlyphId = 11, value = -80),
                ),
            ),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "AV",
                typefaceId = typefaceId,
                fontSize = 20f,
            ),
        )

        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(7, 11),
                    clusters = listOf(
                        GlyphCluster(textRange = 0..0, glyphRange = 0..0, advanceX = 20f),
                        GlyphCluster(textRange = 1..1, glyphRange = 1..1, advanceX = 20f),
                    ),
                    advanceX = 40f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = typefaceId,
                    fontSize = 20f,
                ),
            ),
            result.glyphRuns,
        )
        assertEquals(
            listOf(
                ShapingDiagnostic(
                    code = KERN_TABLE_UNAPPLIED_DIAGNOSTIC_CODE,
                    message = "Kern table for typeface ${typefaceId.value} cannot apply because unitsPerEm is missing.",
                    textRange = 0..1,
                ),
            ),
            result.diagnostics,
        )
    }

    @Test
    fun fallbackOpenTypeShapingEngineShapesResolvedRunsWithAbsoluteClusterRanges() {
        val latin = testFace("550e8400-e29b-41d4-a716-446655440501", "Alpha Sans")
        val emoji = testFace("550e8400-e29b-41d4-a716-446655440502", "Noto Color Emoji")
        val resolver = RecordingFontResolver(
            listOf(
                ResolvedFontRun(start = 0, end = 1, face = latin),
                ResolvedFontRun(start = 1, end = 3, face = emoji),
            ),
        )
        val engine = FallbackOpenTypeShapingEngine(
            fontResolver = resolver,
            glyphMapper = CMapGlyphMapper(
                cmapsByTypefaceId = mapOf(
                    latin.typeface.id to cmapTable('A'.code to 101),
                    emoji.typeface.id to cmapTable(0x1F600 to 600),
                ),
            ),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "xA\uD83D\uDE00z",
                textRange = 1..3,
                fontSize = 16f,
                locale = "en-US",
                preferredFamilies = listOf("Alpha Sans"),
            ),
        )

        assertEquals(
            listOf(
                FallbackRequest(
                    text = "A\uD83D\uDE00",
                    locale = "en-US",
                    preferredFamilies = listOf("Alpha Sans"),
                ),
            ),
            resolver.requests,
        )
        assertEquals(emptyList(), result.diagnostics)
        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(101),
                    clusters = listOf(GlyphCluster(textRange = 1..1, glyphRange = 0..0, advanceX = 16f)),
                    advanceX = 16f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = latin.typeface.id,
                    fontSize = 16f,
                ),
                ShapedGlyphRun(
                    glyphIds = listOf(600),
                    clusters = listOf(GlyphCluster(textRange = 2..3, glyphRange = 0..0, advanceX = 16f)),
                    advanceX = 16f,
                    script = "Zsye",
                    bidiLevel = 0,
                    typefaceId = emoji.typeface.id,
                    fontSize = 16f,
                ),
            ),
            result.glyphRuns,
        )
    }

    @Test
    fun fallbackOpenTypeShapingEngineMergesResolverRunsSplitInsideClusterForSameTypeface() {
        val latin = testFace("550e8400-e29b-41d4-a716-446655440505", "Alpha Sans")
        val resolver = RecordingFontResolver(
            listOf(
                ResolvedFontRun(start = 0, end = 1, face = latin),
                ResolvedFontRun(start = 1, end = 2, face = latin),
            ),
        )
        val engine = FallbackOpenTypeShapingEngine(
            fontResolver = resolver,
            glyphMapper = CMapGlyphMapper(
                cmapsByTypefaceId = mapOf(
                    latin.typeface.id to cmapTable(
                        'a'.code to 101,
                        0x0301 to 102,
                    ),
                ),
            ),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "a\u0301",
                fontSize = 14f,
            ),
        )

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(101, 102),
                    clusters = listOf(GlyphCluster(textRange = 0..1, glyphRange = 0..1, advanceX = 14f)),
                    advanceX = 14f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = latin.typeface.id,
                    fontSize = 14f,
                ),
            ),
            result.glyphRuns,
        )
    }

    @Test
    fun fallbackOpenTypeShapingEngineReportsConflictingTypefaceRunsInsideCluster() {
        val latin = testFace("550e8400-e29b-41d4-a716-446655440506", "Alpha Sans")
        val accent = testFace("550e8400-e29b-41d4-a716-446655440507", "Accent Sans")
        val resolver = RecordingFontResolver(
            listOf(
                ResolvedFontRun(start = 0, end = 1, face = latin),
                ResolvedFontRun(start = 1, end = 2, face = accent),
            ),
        )
        val engine = FallbackOpenTypeShapingEngine(
            fontResolver = resolver,
            glyphMapper = CMapGlyphMapper(
                cmapsByTypefaceId = mapOf(
                    latin.typeface.id to cmapTable(
                        'a'.code to 101,
                        0x0301 to 102,
                    ),
                    accent.typeface.id to cmapTable(0x0301 to 202),
                ),
            ),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "a\u0301",
                fontSize = 14f,
            ),
        )

        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(101, 102),
                    clusters = listOf(GlyphCluster(textRange = 0..1, glyphRange = 0..1, advanceX = 14f)),
                    advanceX = 14f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = latin.typeface.id,
                    fontSize = 14f,
                ),
            ),
            result.glyphRuns,
        )
        assertEquals(
            listOf(
                ShapingDiagnostic(
                    code = CONFLICTING_FONT_RUN_DIAGNOSTIC_CODE,
                    message = "Conflicting resolved fonts for cluster 0..1; using typeface ${latin.typeface.id.value} and skipping ${accent.typeface.id.value}.",
                    textRange = 0..1,
                ),
            ),
            result.diagnostics,
        )
    }

    @Test
    fun fallbackOpenTypeShapingEnginePreservesParsedKernPairsThroughDelegate() {
        val latin = testFace("550e8400-e29b-41d4-a716-446655440504", "Alpha Sans")
        val resolver = RecordingFontResolver(
            listOf(
                ResolvedFontRun(start = 0, end = 2, face = latin),
            ),
        )
        val engine = FallbackOpenTypeShapingEngine(
            fontResolver = resolver,
            glyphMapper = CMapGlyphMapper(
                cmapsByTypefaceId = mapOf(
                    latin.typeface.id to cmapTable(
                        'A'.code to 7,
                        'V'.code to 11,
                    ),
                ),
            ),
            kernTablesByTypefaceId = mapOf(
                latin.typeface.id to openTypeKernTable(
                    OpenTypeKernPair(leftGlyphId = 7, rightGlyphId = 11, value = -100),
                ),
            ),
            kernUnitsPerEmByTypefaceId = mapOf(latin.typeface.id to 1000),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "xAVz",
                textRange = 1..2,
                fontSize = 20f,
            ),
        )

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(7, 11),
                    clusters = listOf(
                        GlyphCluster(textRange = 1..1, glyphRange = 0..0, advanceX = 18f),
                        GlyphCluster(textRange = 2..2, glyphRange = 1..1, advanceX = 20f),
                    ),
                    advanceX = 38f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = latin.typeface.id,
                    fontSize = 20f,
                ),
            ),
            result.glyphRuns,
        )
    }

    @Test
    fun fallbackOpenTypeShapingEngineReportsUnresolvedRunsAndPreservesDelegateDiagnostics() {
        val latin = testFace("550e8400-e29b-41d4-a716-446655440503", "Alpha Sans")
        val resolver = RecordingFontResolver(
            listOf(
                ResolvedFontRun(start = 0, end = 1, face = latin),
            ),
        )
        val engine = FallbackOpenTypeShapingEngine(
            fontResolver = resolver,
            glyphMapper = CMapGlyphMapper(cmapsByTypefaceId = mapOf(latin.typeface.id to cmapTable())),
        )

        val result = engine.shape(
            ShapingRequest(
                text = "xA\u2603",
                textRange = 1..2,
                fontSize = 12f,
                preferredFamilies = listOf("Alpha Sans"),
            ),
        )

        assertEquals(
            listOf(
                ShapedGlyphRun(
                    glyphIds = listOf(0),
                    clusters = listOf(GlyphCluster(textRange = 1..1, glyphRange = 0..0, advanceX = 12f)),
                    advanceX = 12f,
                    script = "Latn",
                    bidiLevel = 0,
                    typefaceId = latin.typeface.id,
                    fontSize = 12f,
                ),
            ),
            result.glyphRuns,
        )
        assertEquals(
            listOf(
                ShapingDiagnostic(
                    code = MISSING_GLYPH_DIAGNOSTIC_CODE,
                    message = "Missing glyph for U+0041.",
                    textRange = 1..1,
                ),
                ShapingDiagnostic(
                    code = UNRESOLVED_FONT_RUN_DIAGNOSTIC_CODE,
                    message = "No resolved font for text range 2..2.",
                    textRange = 2..2,
                ),
            ),
            result.diagnostics,
        )
    }

    @Test
    fun gsubEnginePreservesRunWhenNoConcreteLookupDataIsSupplied() {
        val run = ShapedGlyphRun(
            glyphIds = listOf(7, 8),
            clusters = listOf(
                GlyphCluster(textRange = 0..0, glyphRange = 0..0, advanceX = 12f),
                GlyphCluster(textRange = 1..1, glyphRange = 1..1, advanceX = 12f),
            ),
            advanceX = 24f,
            script = "Latn",
            fontSize = 12f,
        )

        assertEquals(run, GSUBEngine().substitute(run, FeatureSet(mapOf("liga" to 1))))
    }

    @Test
    fun gposEnginePreservesRunWhenNoConcreteLookupDataIsSupplied() {
        val run = ShapedGlyphRun(
            glyphIds = listOf(7, 11),
            clusters = listOf(
                GlyphCluster(textRange = 0..0, glyphRange = 0..0, advanceX = 18f),
                GlyphCluster(textRange = 1..1, glyphRange = 1..1, advanceX = 20f),
            ),
            advanceX = 38f,
            script = "Latn",
            fontSize = 20f,
        )

        assertEquals(run, GPOSEngine().position(run, FeatureSet(mapOf("kern" to 1))))
    }

    @Test
    fun emojiSequenceShaperFindsEmojiStyleAndZwjClustersInsideRequestedRange() {
        val text = "x\u2603\uFE0Fy\uD83D\uDC68\u200D\uD83D\uDC69z"

        val clusters = EmojiSequenceShaper().shapeEmoji(
            ShapingRequest(
                text = text,
                textRange = 1..8,
                fontSize = 18f,
            ),
        )

        assertEquals(
            listOf(
                GlyphCluster(textRange = 1..2, glyphRange = 0..0, advanceX = 18f),
                GlyphCluster(textRange = 4..8, glyphRange = 1..1, advanceX = 18f),
            ),
            clusters,
        )
    }

    @Test
    fun emojiSequenceShaperDumpsVS15SkinToneAndZwjFamilyFixtures() {
        val text = "a\u2603\uFE0E b\uD83D\uDC4B\uD83C\uDFFD c\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67d"

        val clusters = EmojiSequenceShaper().shapeEmoji(
            ShapingRequest(
                text = text,
                textRange = 1..18,
                fontSize = 20f,
            ),
        )

        assertEquals(
            listOf(
                GlyphCluster(textRange = 1..2, glyphRange = 0..0, advanceX = 20f),
                GlyphCluster(textRange = 5..8, glyphRange = 1..1, advanceX = 20f),
                GlyphCluster(textRange = 11..18, glyphRange = 2..2, advanceX = 20f),
            ),
            clusters,
        )
    }

    private fun mapGlyphs(vararg mappings: Pair<Int, Int>): GlyphMapper {
        val glyphsByCodePoint = mappings.toMap()
        return object : GlyphMapper {
            override fun glyphIdFor(typefaceId: TypefaceID?, codePoint: Int): Int? =
                glyphsByCodePoint[codePoint]
        }
    }

    private class RecordingFontResolver(
        private val runs: List<ResolvedFontRun>,
    ) : FontResolver {
        val requests = mutableListOf<FallbackRequest>()

        override fun resolve(request: FallbackRequest): List<ResolvedFontRun> {
            requests += request
            return runs
        }
    }

    private class RecordingShapingEngine(
        private val diagnostics: List<ShapingDiagnostic> = emptyList(),
    ) : OpenTypeShapingEngine {
        val requests = mutableListOf<ShapingRequest>()

        override fun shape(request: ShapingRequest): ShapingResult {
            requests += request
            val clusters = request.textRange.mapIndexed { glyphIndex, textIndex ->
                GlyphCluster(
                    textRange = textIndex..textIndex,
                    glyphRange = glyphIndex..glyphIndex,
                    advanceX = request.fontSize,
                )
            }
            return ShapingResult(
                glyphRuns = listOf(
                    ShapedGlyphRun(
                        glyphIds = clusters.indices.toList(),
                        clusters = clusters,
                        advanceX = clusters.sumOf { it.advanceX.toDouble() }.toFloat(),
                        typefaceId = request.typefaceId,
                        fontSize = request.fontSize,
                    ),
                ),
                diagnostics = diagnostics,
            )
        }
    }

    private fun cmapTable(vararg mappings: Pair<Int, Int>): CMapTable =
        CMapTable(
            mappings = listOf(
                CMapSubtable(
                    platformId = 3,
                    encodingId = 10,
                    offset = 0,
                    length = 16 + mappings.size * 12,
                    format = 12,
                    mapping = CMapFormat12Mapping(
                        mappings.map { (codePoint, glyphId) ->
                            CMapFormat12Group(
                                startCharCode = codePoint,
                                endCharCode = codePoint,
                                startGlyphId = glyphId,
                            )
                        },
                    ),
                ),
            ),
        )

    private fun openTypeKernTable(vararg pairs: OpenTypeKernPair): OpenTypeKernTable =
        OpenTypeKernTable(
            subtables = listOf(
                OpenTypeKernFormat0Subtable(
                    version = 0,
                    length = 14 + pairs.size * 6,
                    coverage = OpenTypeKernCoverage(raw = 0x0001),
                    searchRange = 0,
                    entrySelector = 0,
                    rangeShift = 0,
                    pairs = pairs.toList(),
                ),
            ),
        )

    private fun readProjectFile(relativePath: String): String =
        Files.readString(projectRoot().resolve(relativePath))

    private fun parsedFixtureFace(uuid: String, relativePath: String): ParsedFixtureFace {
        val typefaceId = TypefaceID(Uuid.parse(uuid))
        val path = projectRoot().resolve(relativePath)
        val source = FontSource(
            id = FontSourceID(Uuid.parse(uuid.replaceRange(uuid.length - 1, uuid.length, "0"))),
            kind = FontSourceKind.FILE,
            displayName = path.fileName.toString(),
            bytes = Files.readAllBytes(path),
        )
        val parsed = DefaultOpenTypeFaceParser().parse(source)
        assertEquals(emptyList(), parsed.diagnostics, relativePath)
        return ParsedFixtureFace(
            typefaceId = typefaceId,
            cmap = parsed.cmap,
            unitsPerEm = requireNotNull(parsed.metrics.unitsPerEm),
            gsub = parsed.layout.gsub,
            gposSingles = parsed.layout.gposSingles,
            gposPairs = parsed.layout.gposPairs,
        )
    }

    private fun readJsonProjectFile(relativePath: String): Map<String, Any?> =
        jsonObject(JsonParser(readProjectFile(relativePath)).parse(), relativePath)

    private fun Map<String, Any?>.requiredObject(key: String): Map<String, Any?> =
        jsonObject(this[key], key)

    private fun Map<String, Any?>.requiredString(key: String): String =
        this[key] as? String ?: error("Expected $key to be a string")

    private fun Map<String, Any?>.requiredObjectList(key: String): List<Map<String, Any?>> =
        requiredList(key).mapIndexed { index, value -> jsonObject(value, "$key[$index]") }

    private fun Map<String, Any?>.requiredLong(key: String): Long =
        this[key] as? Long ?: error("Expected $key to be a number")

    private fun Map<String, Any?>.requiredStringList(key: String): List<String> =
        requiredList(key).mapIndexed { index, value ->
            value as? String ?: error("Expected $key[$index] to be a string")
        }

    private fun Map<String, Any?>.requiredLongList(key: String): List<Long> =
        requiredList(key).mapIndexed { index, value ->
            value as? Long ?: error("Expected $key[$index] to be a number")
        }

    private fun Map<String, Any?>.requiredLongLists(key: String): List<List<Long>> =
        requiredList(key).mapIndexed { index, value ->
            jsonList(value, "$key[$index]").mapIndexed { nestedIndex, nestedValue ->
                nestedValue as? Long ?: error("Expected $key[$index][$nestedIndex] to be a number")
            }
        }

    private fun Map<String, Any?>.requiredList(key: String): List<Any?> =
        jsonList(this[key], key)

    @Suppress("UNCHECKED_CAST")
    private fun jsonObject(value: Any?, path: String): Map<String, Any?> =
        (value as? Map<String, Any?>) ?: error("Expected $path to be a JSON object")

    @Suppress("UNCHECKED_CAST")
    private fun jsonList(value: Any?, path: String): List<Any?> =
        (value as? List<Any?>) ?: error("Expected $path to be a JSON array")

    private fun assertNoSupportPromotionClaims(value: Any?, path: String = "$", insideNonClaims: Boolean = false) {
        when (value) {
            is Map<*, *> -> value.forEach { (key, child) ->
                assertTrue(key is String, "Expected JSON object key at $path to be a string")
                assertFalse(key == "supportClaim", "Unexpected supportClaim key at $path")
                assertNoSupportPromotionClaims(child, "$path.$key", insideNonClaims = key == "nonClaims")
            }
            is List<*> -> value.forEachIndexed { index, child ->
                assertNoSupportPromotionClaims(child, "$path[$index]", insideNonClaims)
            }
            is String -> if (!insideNonClaims) {
                val normalized = value.lowercase()
                assertFalse(normalized.contains("supportclaim"), "Unexpected support claim value at $path: $value")
                assertFalse(
                    normalized.contains("supported") &&
                        !normalized.contains("unsupported") &&
                        !normalized.contains("not supported"),
                    "Unexpected support promotion value at $path: $value",
                )
            }
        }
    }

    private class JsonParser(private val source: String) {
        private var index = 0

        fun parse(): Any? {
            val value = parseValue()
            skipWhitespace()
            require(index == source.length) { "Unexpected trailing JSON content at offset $index" }
            return value
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            return when (peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            skipWhitespace()
            val result = linkedMapOf<String, Any?>()
            if (consumeIf('}')) return result
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                result[key] = parseValue()
                skipWhitespace()
                if (consumeIf('}')) return result
                expect(',')
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            skipWhitespace()
            val result = mutableListOf<Any?>()
            if (consumeIf(']')) return result
            while (true) {
                result += parseValue()
                skipWhitespace()
                if (consumeIf(']')) return result
                expect(',')
            }
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()
            while (index < source.length) {
                val ch = source[index++]
                when (ch) {
                    '"' -> return result.toString()
                    '\\' -> result.append(parseEscape())
                    else -> result.append(ch)
                }
            }
            error("Unterminated JSON string")
        }

        private fun parseEscape(): Char =
            when (val escaped = source.getOrNull(index++) ?: error("Unterminated JSON escape")) {
                '"', '\\', '/' -> escaped
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    val hex = source.substring(index, index + 4)
                    index += 4
                    hex.toInt(16).toChar()
                }
                else -> error("Unsupported JSON escape \\$escaped at offset ${index - 1}")
            }

        private fun parseNumber(): Long {
            val start = index
            if (peek() == '-') index += 1
            while (peekOrNull()?.isDigit() == true) index += 1
            require(start < index) { "Expected JSON value at offset $index" }
            return source.substring(start, index).toLong()
        }

        private fun parseLiteral(token: String, value: Any?): Any? {
            require(source.startsWith(token, index)) { "Expected $token at offset $index" }
            index += token.length
            return value
        }

        private fun skipWhitespace() {
            while (peekOrNull()?.isWhitespace() == true) index += 1
        }

        private fun expect(expected: Char) {
            val actual = peek()
            require(actual == expected) { "Expected $expected at offset $index but found $actual" }
            index += 1
        }

        private fun consumeIf(expected: Char): Boolean {
            if (peekOrNull() != expected) return false
            index += 1
            return true
        }

        private fun peek(): Char =
            peekOrNull() ?: error("Unexpected end of JSON input")

        private fun peekOrNull(): Char? =
            source.getOrNull(index)
    }

    private fun projectRoot(): Path =
        generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }

    private fun testFace(uuid: String, familyName: String, styleName: String = "Regular"): FontFace {
        val sourceId = FontSourceID(Uuid.parse(uuid.replaceRange(uuid.length - 1, uuid.length, "0")))
        val typefaceId = TypefaceID(Uuid.parse(uuid))
        return FontFace(
            typeface = TypefaceData(
                id = typefaceId,
                source = FontSource(
                    id = sourceId,
                    kind = FontSourceKind.MEMORY,
                    displayName = "$familyName $styleName",
                    bytes = ByteArray(0),
                ),
                familyName = familyName,
                styleName = styleName,
            ),
        )
    }

    private data class ParsedFixtureFace(
        val typefaceId: TypefaceID,
        val cmap: CMapTable,
        val unitsPerEm: Int,
        val gsub: OpenTypeGsubTable?,
        val gposSingles: OpenTypeGposSingleTable?,
        val gposPairs: OpenTypeGposPairTable?,
    )
}
