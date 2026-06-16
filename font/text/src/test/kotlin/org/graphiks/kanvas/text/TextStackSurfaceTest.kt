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
import org.graphiks.kanvas.font.sfnt.CMapFormat12Group
import org.graphiks.kanvas.font.sfnt.CMapFormat12Mapping
import org.graphiks.kanvas.font.sfnt.CMapSubtable
import org.graphiks.kanvas.font.sfnt.CMapTable
import org.graphiks.kanvas.font.sfnt.OpenTypeGposPairAdjustment
import org.graphiks.kanvas.font.sfnt.OpenTypeGposPairTable
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
        val paragraph = ParagraphBuilder(ParagraphStyle(textDirection = 1))
            .append("aa bb c", TextStyle(fontSize = 10f, locale = "en-US"))
            .build()

        val result = layoutEngine.layout(paragraph, maxWidth = 50f)

        assertEquals(
            """
            {
              "schema": "kanvas.paragraph.layout.v1",
              "input": {
                "text": "aa bb c",
                "textLength": 7,
                "paragraphStyle": {"textAlign": "start", "textDirection": 1, "maxLines": null, "ellipsis": null, "lineHeight": null},
                "textStyles": [
                  {"range": "0..6", "typefaceId": null, "fontSize": 10.0, "locale": "en-US", "features": []}
                ],
                "placeholders": []
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

        assertEquals(1L, dump["schemaVersion"])
        assertEquals("paragraph-input-goldens", dump.requiredString("dumpId"))
        assertEquals(listOf("PKT-09C"), dump.requiredStringList("ownerTickets"))
        assertEquals("multi-style-with-placeholder", cases.single().requiredString("caseId"))
        assertEquals("hello [box] world", cases.single().requiredString("text"))
        assertEquals(listOf(0L, 5L), styleRuns[0].requiredLongList("range"))
        assertEquals("Liberation Sans", styleRuns[0].requiredString("family"))
        assertEquals(listOf(12L, 17L), styleRuns[1].requiredLongList("range"))
        assertEquals("Liberation Serif", styleRuns[1].requiredString("family"))
        assertEquals(listOf(listOf(6L, 11L)), cases.single().requiredLongLists("placeholderRanges"))
        assertEquals(
            listOf("invalid-range", "non-finite-placeholder-metric", "unsupported-baseline"),
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

    private fun readJsonProjectFile(relativePath: String): Map<String, Any?> =
        jsonObject(JsonParser(readProjectFile(relativePath)).parse(), relativePath)

    private fun Map<String, Any?>.requiredObject(key: String): Map<String, Any?> =
        jsonObject(this[key], key)

    private fun Map<String, Any?>.requiredString(key: String): String =
        this[key] as? String ?: error("Expected $key to be a string")

    private fun Map<String, Any?>.requiredObjectList(key: String): List<Map<String, Any?>> =
        requiredList(key).mapIndexed { index, value -> jsonObject(value, "$key[$index]") }

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
                    normalized.contains("supported") && !normalized.contains("unsupported"),
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
}
