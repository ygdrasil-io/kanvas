package org.graphiks.kanvas.text

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import org.graphiks.kanvas.font.FontSource
import org.graphiks.kanvas.font.FontSourceID
import org.graphiks.kanvas.font.FontSourceKind
import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.font.sfnt.CMapTable
import org.graphiks.kanvas.font.sfnt.DefaultOpenTypeFaceParser
import org.graphiks.kanvas.font.sfnt.OpenTypeGdefTable
import org.graphiks.kanvas.font.sfnt.OpenTypeGposPairTable
import org.graphiks.kanvas.font.sfnt.OpenTypeGposSingleTable
import org.graphiks.kanvas.font.sfnt.OpenTypeGposTable
import org.graphiks.kanvas.font.sfnt.OpenTypeGsubTable
import org.graphiks.kanvas.text.shaping.BasicBidiResolver
import org.graphiks.kanvas.text.shaping.BasicOpenTypeShapingEngine
import org.graphiks.kanvas.text.shaping.CMapGlyphMapper
import org.graphiks.kanvas.text.shaping.PinnedUnicodeDataGenerator
import org.graphiks.kanvas.text.shaping.PinnedUnicodeDataSetResources
import org.graphiks.kanvas.text.shaping.RequiredScriptFeaturePolicies
import org.graphiks.kanvas.text.shaping.ScriptExtensionsItemizer
import org.graphiks.kanvas.text.shaping.ShapingRequest
import org.graphiks.kanvas.text.shaping.TEXT_SHAPING_GDEF_REQUIRED_DIAGNOSTIC_CODE
import org.graphiks.kanvas.text.shaping.TEXT_SHAPING_PARAGRAPH_BIDI_REQUIRED_DIAGNOSTIC_CODE

class ArabicShapingFixtureTest {
    @Test
    fun basicOpenTypeShapingEngineAppliesVendoredArabicJoiningForms() {
        val face = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440703",
            relativePath = "reports/font/fixtures/fonts/fallback/NotoNaskhArabic-Regular.ttf",
        )
        val text = "\u0633\u0644\u0627\u0645"
        val rawGlyphIds = listOf('\u0633'.code, '\u0644'.code, '\u0627'.code, '\u0645'.code)
            .map { codePoint -> requireNotNull(face.cmap.lookupGlyphId(codePoint)) }
        val rawVisualOrderGlyphIds = rawGlyphIds.reversed()
        val result = engineFor(face).shape(
            ShapingRequest(
                text = text,
                typefaceId = face.typefaceId,
                fontSize = 20f,
            ),
        )
        val shapedRun = result.glyphRuns.single()

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(1, result.glyphRuns.size)
        assertEquals(4, shapedRun.clusters.size)
        assertEquals(4, shapedRun.glyphIds.size)
        assertFalse(
            shapedRun.glyphIds == rawVisualOrderGlyphIds,
            "Arabic joining fixture should prove more than RTL reordering of raw cmap glyph IDs.",
        )
    }

    @Test
    fun basicOpenTypeShapingEngineAppliesVendoredArabicMarkPositioning() {
        val face = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440704",
            relativePath = "reports/font/fixtures/fonts/fallback/NotoNaskhArabic-Regular.ttf",
        )
        val result = engineFor(face).shape(
            ShapingRequest(
                text = "\u0627\u064E",
                typefaceId = face.typefaceId,
                fontSize = 20f,
            ),
        )
        val shapedRun = result.glyphRuns.single()
        val baseCluster = shapedRun.clusters.first { cluster -> cluster.textRange == 0..0 }
        val markCluster = shapedRun.clusters.first { cluster -> cluster.textRange == 1..1 }

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(1, result.glyphRuns.size)
        assertEquals(2, shapedRun.clusters.size)
        assertTrue(baseCluster.advanceX > 0f, "Arabic base cluster should keep a positive advance.")
        assertTrue(
            markCluster.advanceX == 0f || markCluster.offsetX != 0f || markCluster.offsetY != 0f,
            "Arabic mark fixture should prove positioning or zero-advance on the mark glyph cluster itself.",
        )
    }

    @Test
    fun basicOpenTypeShapingEngineChangesVendoredArabicLamAlefSequenceWithoutClosingLigatureGate() {
        val face = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440705",
            relativePath = "reports/font/fixtures/fonts/fallback/NotoNaskhArabic-Regular.ttf",
        )
        val text = "\u0644\u0627"
        val rawGlyphIds = listOf('\u0644'.code, '\u0627'.code)
            .map { codePoint -> requireNotNull(face.cmap.lookupGlyphId(codePoint)) }
        val result = engineFor(face).shape(
            ShapingRequest(
                text = text,
                typefaceId = face.typefaceId,
                fontSize = 20f,
            ),
        )
        val rawVisualOrderGlyphIds = rawGlyphIds.reversed()
        val shapedRun = result.glyphRuns.single()

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(1, result.glyphRuns.size)
        assertEquals(2, shapedRun.glyphIds.size)
        assertTrue(
            shapedRun.glyphIds.zip(rawVisualOrderGlyphIds).all { (shapedGlyphId, rawGlyphId) -> shapedGlyphId != rawGlyphId },
            "Arabic lam-alef bounded runtime check should prove both visual-order component glyph IDs change beyond pure RTL reordering without closing the ticket-local ligature gate.",
        )
    }

    @Test
    fun basicOpenTypeShapingEngineEmitsParagraphBidiDiagnosticForVendoredArabicMixedFixture() {
        val face = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440702",
            relativePath = "reports/font/fixtures/fonts/fallback/NotoNaskhArabic-Regular.ttf",
        )
        val mixedBidiText = readProjectFile("reports/font/fixtures/expected/shaping/arabic-mixed-bidi.txt").trimEnd()
        val result = engineFor(face).shape(
            ShapingRequest(
                text = mixedBidiText,
                typefaceId = face.typefaceId,
                fontSize = 20f,
            ),
        )

        assertTrue(result.glyphRuns.isNotEmpty(), "Mixed bidi diagnostic should not be described as an empty-run refusal.")
        assertContains(
            result.diagnostics.map { it.code },
            TEXT_SHAPING_PARAGRAPH_BIDI_REQUIRED_DIAGNOSTIC_CODE,
        )
    }

    @Test
    fun basicOpenTypeShapingEngineEmitsReviewedGenericGdefRequiredDiagnosticForArabicBasePlusMarkFixture() {
        val face = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440706",
            relativePath = "reports/font/fixtures/fonts/shaping/gpos-missing-gdef.otf",
        )
        val result = engineFor(face).shape(
            ShapingRequest(
                text = "\u0627\u064E",
                typefaceId = face.typefaceId,
                fontSize = 20f,
            ),
        )

        assertEquals(1, result.glyphRuns.size)
        assertEquals(
            listOf(TEXT_SHAPING_GDEF_REQUIRED_DIAGNOSTIC_CODE),
            result.diagnostics.map { it.code },
        )
        assertEquals(listOf(20f, 20f), result.glyphRuns.single().clusters.map { it.advanceX })
    }

    @Test
    fun arabicShapingReportGoldenExistsAndTracksFixtureWave() {
        val report = readJsonProjectFile("reports/font/fixtures/expected/shaping/arabic-shaping-report.json")
        val cases = report.requiredObjectList("cases")

        assertEquals(1L, report.requiredLong("schemaVersion"))
        assertEquals("arabic-shaping-report", report.requiredString("dumpId"))
        assertEquals(listOf("KFONT-M6-007"), report.requiredStringList("ownerTickets"))
        assertEquals("single-ttf-noto-naskh-arabic", report.requiredString("fixtureId"))
        assertEquals(
            listOf("joining-forms", "marks", "missing-mark-gdef-required", "mixed-bidi-paragraph-required"),
            cases.map { it.requiredString("caseId") },
        )
        assertEquals("positive", cases[0].requiredString("status"))
        assertEquals("positive", cases[1].requiredString("status"))
        assertEquals("diagnostic", cases[2].requiredString("status"))
        assertEquals("diagnostic", cases[3].requiredString("status"))
        assertEquals(null, cases[1]["requiredDiagnostics"])
        assertEquals(
            listOf(TEXT_SHAPING_GDEF_REQUIRED_DIAGNOSTIC_CODE),
            cases[2].requiredStringList("requiredDiagnostics"),
        )
        assertEquals(
            "reports/font/fixtures/fonts/shaping/gpos-missing-gdef.otf",
            cases[2].requiredString("fixtureFont"),
        )
        assertEquals(
            listOf(TEXT_SHAPING_PARAGRAPH_BIDI_REQUIRED_DIAGNOSTIC_CODE),
            cases[3].requiredStringList("requiredDiagnostics"),
        )
        assertEquals(
            listOf(
                "lam-alef-positive-evidence",
                "cursive-positive-on-vendored-arabic",
                "arabic-specific-refusal-fixtures-and-codes",
            ),
            report.requiredObjectList("remainingGates").map { it.requiredString("remainingGateId") },
        )
        assertEquals(
            listOf(
                "no-arabic-shaping-support-claim",
                "no-complex-shaping-support-claim",
                "no-native-shaper-oracle-claim",
                "no-cpu-or-gpu-rendering-claim",
            ),
            report.requiredStringList("nonClaims"),
        )
        assertNoSupportPromotionClaims(report)
    }

    @Test
    fun arabicShapedGlyphRunGoldenMatchesRepoGolden() {
        val actual = arabicShapedGlyphRunEvidenceJson()
        val expected = readProjectFile("reports/font/fixtures/expected/shaping/arabic-shaped-glyph-run.json")

        assertEquals(expected.trimEnd(), actual.trimEnd())
        assertContains(actual, """"dumpId": "arabic-shaped-glyph-run"""")
        assertContains(actual, """"fixtureFamilyId": "arabic-shaping-fixtures"""")
        assertContains(actual, """"caseId": "lam-alef-bounded-runtime-divergence"""")
        assertContains(actual, TEXT_SHAPING_GDEF_REQUIRED_DIAGNOSTIC_CODE)
    }

    @Test
    fun arabicShapingPlanGoldenMatchesRepoGolden() {
        val actual = arabicShapingPlanEvidenceJson()
        val expected = readProjectFile("reports/font/fixtures/expected/shaping/arabic-shaping-plan.json")

        assertEquals(expected.trimEnd(), actual.trimEnd())
        assertContains(actual, """"dumpId": "arabic-shaping-plan"""")
        assertContains(actual, """"fixtureFamilyId": "arabic-shaping-fixtures"""")
        assertContains(actual, """"requiredDefaults": ["init", "medi", "fina", "isol", "rlig", "liga", "calt", "mark", "mkmk", "curs"]""")
        assertContains(actual, """"requiredRefusals": ["mark", "mkmk", "cursive-attachment"]""")
    }

    private fun engineFor(face: ParsedFixtureFace): BasicOpenTypeShapingEngine =
        BasicOpenTypeShapingEngine(
            glyphMapper = CMapGlyphMapper(cmapsByTypefaceId = mapOf(face.typefaceId to face.cmap)),
            gsubTablesByTypefaceId = face.gsub?.let { mapOf(face.typefaceId to it) }.orEmpty(),
            gdefTablesByTypefaceId = face.gdef?.let { mapOf(face.typefaceId to it) }.orEmpty(),
            gposTablesByTypefaceId = face.gpos?.let { mapOf(face.typefaceId to it) }.orEmpty(),
            gposSingleTablesByTypefaceId = face.gposSingles?.let { mapOf(face.typefaceId to it) }.orEmpty(),
            gposPairTablesByTypefaceId = face.gposPairs?.let { mapOf(face.typefaceId to it) }.orEmpty(),
            kernUnitsPerEmByTypefaceId = mapOf(face.typefaceId to face.unitsPerEm),
        )

    private fun readProjectFile(relativePath: String): String =
        Files.readString(projectRoot().resolve(relativePath))

    private fun readJsonProjectFile(relativePath: String): Map<String, Any?> =
        jsonObject(JsonParser(readProjectFile(relativePath)).parse(), relativePath)

    private fun arabicShapedGlyphRunEvidenceJson(): String {
        val cases = listOf(
            buildArabicShapedGlyphRunCase(
                caseId = "joining-forms",
                status = "positive",
                reportRef = "arabic-shaping-report#joining-forms",
                uuid = "550e8400-e29b-41d4-a716-446655440703",
                relativePath = "reports/font/fixtures/fonts/fallback/NotoNaskhArabic-Regular.ttf",
                inputText = "\u0633\u0644\u0627\u0645",
            ),
            buildArabicShapedGlyphRunCase(
                caseId = "marks",
                status = "positive",
                reportRef = "arabic-shaping-report#marks",
                uuid = "550e8400-e29b-41d4-a716-446655440704",
                relativePath = "reports/font/fixtures/fonts/fallback/NotoNaskhArabic-Regular.ttf",
                inputText = "\u0627\u064E",
            ),
            buildArabicShapedGlyphRunCase(
                caseId = "lam-alef-bounded-runtime-divergence",
                status = "bounded",
                reportRef = "arabic-shaping-report#lam-alef-positive-evidence",
                uuid = "550e8400-e29b-41d4-a716-446655440705",
                relativePath = "reports/font/fixtures/fonts/fallback/NotoNaskhArabic-Regular.ttf",
                inputText = "\u0644\u0627",
            ),
            buildArabicShapedGlyphRunCase(
                caseId = "missing-mark-gdef-required",
                status = "diagnostic",
                reportRef = "arabic-shaping-report#missing-mark-gdef-required",
                uuid = "550e8400-e29b-41d4-a716-446655440706",
                relativePath = "reports/font/fixtures/fonts/shaping/gpos-missing-gdef.otf",
                inputText = "\u0627\u064E",
            ),
        )
        return buildString {
            append("{\n")
            append("  \"schemaVersion\": 1,\n")
            append("  \"dumpId\": \"arabic-shaped-glyph-run\",\n")
            append("  \"ownerTickets\": [\"KFONT-M6-007\"],\n")
            append("  \"fixtureFamilyId\": \"arabic-shaping-fixtures\",\n")
            append("  \"cases\": [\n")
            append(cases.joinToString(",\n") { it.toCanonicalJson().prependIndent("    ") })
            append("\n  ],\n")
            append(
                "  \"nonClaims\": [\"producer-only\", \"no-arabic-shaping-support-claim\", " +
                    "\"no-complex-shaping-support-claim\", \"no-native-shaper-oracle-claim\", " +
                    "\"no-cpu-or-gpu-rendering-claim\"]\n",
            )
            append("}\n")
        }
    }

    private fun arabicShapingPlanEvidenceJson(): String {
        val cases = listOf(
            buildArabicShapingPlanCase(
                caseId = "joining-forms",
                status = "positive",
                reportRef = "arabic-shaping-report#joining-forms",
                uuid = "550e8400-e29b-41d4-a716-446655440703",
                relativePath = "reports/font/fixtures/fonts/fallback/NotoNaskhArabic-Regular.ttf",
                inputText = "\u0633\u0644\u0627\u0645",
            ),
            buildArabicShapingPlanCase(
                caseId = "marks",
                status = "positive",
                reportRef = "arabic-shaping-report#marks",
                uuid = "550e8400-e29b-41d4-a716-446655440704",
                relativePath = "reports/font/fixtures/fonts/fallback/NotoNaskhArabic-Regular.ttf",
                inputText = "\u0627\u064E",
            ),
            buildArabicShapingPlanCase(
                caseId = "lam-alef-bounded-runtime-divergence",
                status = "bounded",
                reportRef = "arabic-shaping-report#lam-alef-positive-evidence",
                uuid = "550e8400-e29b-41d4-a716-446655440705",
                relativePath = "reports/font/fixtures/fonts/fallback/NotoNaskhArabic-Regular.ttf",
                inputText = "\u0644\u0627",
            ),
            buildArabicShapingPlanCase(
                caseId = "missing-mark-gdef-required",
                status = "diagnostic",
                reportRef = "arabic-shaping-report#missing-mark-gdef-required",
                uuid = "550e8400-e29b-41d4-a716-446655440706",
                relativePath = "reports/font/fixtures/fonts/shaping/gpos-missing-gdef.otf",
                inputText = "\u0627\u064E",
                expectedDiagnostics = listOf(TEXT_SHAPING_GDEF_REQUIRED_DIAGNOSTIC_CODE),
            ),
        )
        return buildString {
            append("{\n")
            append("  \"schemaVersion\": 1,\n")
            append("  \"dumpId\": \"arabic-shaping-plan\",\n")
            append("  \"ownerTickets\": [\"KFONT-M6-007\"],\n")
            append("  \"fixtureFamilyId\": \"arabic-shaping-fixtures\",\n")
            append("  \"unicodeVersion\": ").append(jsonString(PinnedUnicodeDataGenerator.PinnedUnicodeVersion)).append(",\n")
            append("  \"sourceTextHashAlgorithm\": \"SHA-256-UTF-8\",\n")
            append("  \"cases\": [\n")
            append(cases.joinToString(",\n") { it.toCanonicalJson().prependIndent("    ") })
            append("\n  ],\n")
            append(
                "  \"nonClaims\": [\"producer-only\", \"no-arabic-shaping-support-claim\", " +
                    "\"no-complex-shaping-support-claim\", \"no-native-shaper-oracle-claim\", " +
                    "\"no-cpu-or-gpu-rendering-claim\"]\n",
            )
            append("}\n")
        }
    }

    private fun parsedFixtureFace(
        uuid: String,
        relativePath: String,
    ): ParsedFixtureFace {
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
            gdef = parsed.layout.gdef,
            gpos = parsed.layout.gpos,
            gposSingles = parsed.layout.gposSingles,
            gposPairs = parsed.layout.gposPairs,
        )
    }

    private fun projectRoot(): Path =
        generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }

    private data class ParsedFixtureFace(
        val typefaceId: TypefaceID,
        val cmap: CMapTable,
        val unitsPerEm: Int,
        val gsub: OpenTypeGsubTable?,
        val gdef: OpenTypeGdefTable?,
        val gpos: OpenTypeGposTable?,
        val gposSingles: OpenTypeGposSingleTable?,
        val gposPairs: OpenTypeGposPairTable?,
    )

    private data class ArabicShapedGlyphRunCase(
        val caseId: String,
        val status: String,
        val reportRef: String,
        val fixtureFont: String,
        val inputText: String,
        val typefaceId: TypefaceID,
        val glyphRun: org.graphiks.kanvas.text.shaping.ShapedGlyphRun,
        val diagnostics: List<org.graphiks.kanvas.text.shaping.ShapingDiagnostic>,
    )

    private data class ArabicShapingPlanCase(
        val caseId: String,
        val status: String,
        val reportRef: String,
        val fixtureFont: String,
        val inputText: String,
        val sourceTextHash: String,
        val typefaceId: TypefaceID,
        val scriptRun: org.graphiks.kanvas.text.shaping.ScriptItemizationRun,
        val bidiLevel: Int,
        val bidiDirection: String,
        val languageSystem: String,
        val enabledFeatures: List<String>,
        val defaultedFeatures: List<String>,
        val requiredRefusals: List<String>,
        val expectedDiagnostics: List<String>,
        val hasGdef: Boolean,
        val hasGsub: Boolean,
        val hasGpos: Boolean,
    )

    private fun buildArabicShapedGlyphRunCase(
        caseId: String,
        status: String,
        reportRef: String,
        uuid: String,
        relativePath: String,
        inputText: String,
    ): ArabicShapedGlyphRunCase {
        val face = parsedFixtureFace(uuid = uuid, relativePath = relativePath)
        val result = engineFor(face).shape(
            ShapingRequest(
                text = inputText,
                typefaceId = face.typefaceId,
                fontSize = 20f,
            ),
        )
        assertEquals(1, result.glyphRuns.size, caseId)
        return ArabicShapedGlyphRunCase(
            caseId = caseId,
            status = status,
            reportRef = reportRef,
            fixtureFont = relativePath,
            inputText = inputText,
            typefaceId = face.typefaceId,
            glyphRun = result.glyphRuns.single(),
            diagnostics = result.diagnostics,
        )
    }

    private fun buildArabicShapingPlanCase(
        caseId: String,
        status: String,
        reportRef: String,
        uuid: String,
        relativePath: String,
        inputText: String,
        expectedDiagnostics: List<String> = emptyList(),
    ): ArabicShapingPlanCase {
        val face = parsedFixtureFace(uuid = uuid, relativePath = relativePath)
        val unicodeData = PinnedUnicodeDataSetResources.load()
        val scriptRun = ScriptExtensionsItemizer(unicodeData).itemize(inputText).runs.single()
        val featureSet = RequiredScriptFeaturePolicies.resolve(scriptRun = scriptRun, requested = emptyList())
        val bidiRun = BasicBidiResolver().resolve(
            ShapingRequest(
                text = inputText,
                typefaceId = face.typefaceId,
                fontSize = 20f,
            ),
        ).single()
        val arabicPolicy = RequiredScriptFeaturePolicies.rows.first { row -> row.scriptFamily == "Arabic" }
        return ArabicShapingPlanCase(
            caseId = caseId,
            status = status,
            reportRef = reportRef,
            fixtureFont = relativePath,
            inputText = inputText,
            sourceTextHash = sha256Utf8(inputText),
            typefaceId = face.typefaceId,
            scriptRun = scriptRun,
            bidiLevel = bidiRun.level,
            bidiDirection = if (bidiRun.isRightToLeft) "RTL" else "LTR",
            languageSystem = featureSet.languageSystem ?: "dflt",
            enabledFeatures = featureSet.enabled.map { feature -> feature.tag },
            defaultedFeatures = featureSet.defaulted.map { feature -> feature.tag },
            requiredRefusals = arabicPolicy.refusalWhenMissing,
            expectedDiagnostics = expectedDiagnostics,
            hasGdef = face.gdef != null,
            hasGsub = face.gsub != null,
            hasGpos = face.gpos != null,
        )
    }

    private fun ArabicShapedGlyphRunCase.toCanonicalJson(): String = buildString {
        append("{\n")
        append("  \"caseId\": ").append(jsonString(caseId)).append(",\n")
        append("  \"ticketId\": \"KFONT-M6-007\",\n")
        append("  \"status\": ").append(jsonString(status)).append(",\n")
        append("  \"reportRef\": ").append(jsonString(reportRef)).append(",\n")
        append("  \"fixtureFont\": ").append(jsonString(fixtureFont)).append(",\n")
        append("  \"inputText\": ").append(jsonString(inputText)).append(",\n")
        append("  \"typefaceId\": ").append(jsonString(typefaceId.value.toString())).append(",\n")
        append("  \"glyphIds\": ").append(glyphRun.glyphIds.toCanonicalIntArrayJson()).append(",\n")
        append("  \"clusters\": ").append(glyphRun.clusters.toCanonicalClusterArrayJson()).append(",\n")
        append("  \"clusterMetrics\": ").append(glyphRun.clusters.toCanonicalClusterMetricsJson()).append(",\n")
        append("  \"advanceX10\": ").append(glyphRun.advanceX.toTenthsJson())
        if (diagnostics.isNotEmpty()) {
            append(",\n")
            append("  \"diagnostics\": ").append(diagnostics.toCanonicalDiagnosticsJson())
        }
        append("\n}")
    }

    private fun ArabicShapingPlanCase.toCanonicalJson(): String = buildString {
        append("{\n")
        append("  \"caseId\": ").append(jsonString(caseId)).append(",\n")
        append("  \"ticketId\": \"KFONT-M6-007\",\n")
        append("  \"status\": ").append(jsonString(status)).append(",\n")
        append("  \"reportRef\": ").append(jsonString(reportRef)).append(",\n")
        append("  \"fixtureFont\": ").append(jsonString(fixtureFont)).append(",\n")
        append("  \"inputText\": ").append(jsonString(inputText)).append(",\n")
        append("  \"sourceTextHash\": ").append(jsonString(sourceTextHash)).append(",\n")
        append("  \"textRange\": ").append(jsonString(scriptRun.utf16Range.toRangeLabel())).append(",\n")
        append("  \"typefaceId\": ").append(jsonString(typefaceId.value.toString())).append(",\n")
        append("  \"scriptRun\": ").append(scriptRun.toCanonicalJson()).append(",\n")
        append("  \"bidi\": {\"level\": ").append(bidiLevel).append(", \"direction\": ").append(jsonString(bidiDirection)).append("},\n")
        append("  \"languageSystem\": ").append(jsonString(languageSystem)).append(",\n")
        append("  \"requiredDefaults\": ").append(enabledFeatures.toCanonicalStringArrayJson()).append(",\n")
        append("  \"defaultedFeatures\": ").append(defaultedFeatures.toCanonicalStringArrayJson()).append(",\n")
        append("  \"requiredRefusals\": ").append(requiredRefusals.toCanonicalStringArrayJson()).append(",\n")
        append("  \"tableAvailability\": {\"gdef\": ").append(hasGdef).append(", \"gsub\": ").append(hasGsub).append(", \"gpos\": ").append(hasGpos).append("}")
        if (expectedDiagnostics.isNotEmpty()) {
            append(",\n")
            append("  \"expectedDiagnostics\": ").append(expectedDiagnostics.toCanonicalStringArrayJson())
        }
        append("\n}")
    }

    private fun List<Int>.toCanonicalIntArrayJson(): String =
        joinToString(prefix = "[", postfix = "]")

    private fun List<String>.toCanonicalStringArrayJson(): String =
        joinToString(prefix = "[", postfix = "]") { value -> jsonString(value) }

    private fun List<org.graphiks.kanvas.text.shaping.GlyphCluster>.toCanonicalClusterArrayJson(): String =
        joinToString(prefix = "[", postfix = "]") { cluster ->
            """{"textRange":${jsonString(cluster.textRange.toRangeLabel())},"glyphRange":${jsonString(cluster.glyphRange.toRangeLabel())}}"""
        }

    private fun List<org.graphiks.kanvas.text.shaping.GlyphCluster>.toCanonicalClusterMetricsJson(): String =
        joinToString(prefix = "[", postfix = "]") { cluster ->
            "[${cluster.advanceX.toTenthsJson()}, ${cluster.offsetX.toTenthsJson()}, ${cluster.offsetY.toTenthsJson()}]"
        }

    private fun List<org.graphiks.kanvas.text.shaping.ShapingDiagnostic>.toCanonicalDiagnosticsJson(): String =
        joinToString(prefix = "[", postfix = "]") { diagnostic ->
            buildString {
                append("{")
                append(""""code": """).append(jsonString(diagnostic.code))
                append(", ").append(""""message": """).append(jsonString(diagnostic.message))
                diagnostic.textRange?.let { range ->
                    append(", ").append(""""textRange": """).append(jsonString(range.toRangeLabel()))
                }
                append("}")
            }
        }

    private fun Float.toTenthsJson(): String =
        (this * 10f).roundToLong().toString()

    private fun org.graphiks.kanvas.text.shaping.ScriptItemizationRun.toCanonicalJson(): String = buildString {
        append("{")
        append(""""clusterRange": """).append(jsonString(clusterRange.toRangeLabel()))
        append(", ").append(""""utf16Range": """).append(jsonString(utf16Range.toRangeLabel()))
        append(", ").append(""""codePointRange": """).append(jsonString(codePointRange.toRangeLabel()))
        append(", ").append(""""selectedScript": """).append(jsonString(selectedScript))
        append(", ").append(""""openTypeScriptTags": """).append(openTypeScriptTags.toCanonicalStringArrayJson())
        append(", ").append(""""extensionCandidates": """).append(extensionCandidates.toCanonicalStringArrayJson())
        append(", ").append(""""languageHint": """).append(languageHint?.let(::jsonString) ?: "null")
        append(", ").append(""""reason": """).append(jsonString(reason))
        append("}")
    }

    private fun IntRange.toRangeLabel(): String =
        if (isEmpty()) "" else "$first..$last"

    private fun sha256Utf8(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun jsonString(value: String): String =
        "\"" + jsonStringContent(value) + "\""

    private fun jsonStringContent(value: String): String = buildString {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
    }

    private fun Map<String, Any?>.requiredLong(key: String): Long =
        this[key] as? Long ?: error("Expected $key to be a number")

    private fun Map<String, Any?>.requiredString(key: String): String =
        this[key] as? String ?: error("Expected $key to be a string")

    private fun Map<String, Any?>.requiredObjectList(key: String): List<Map<String, Any?>> =
        requiredList(key).mapIndexed { index, value -> jsonObject(value, "$key[$index]") }

    private fun Map<String, Any?>.requiredStringList(key: String): List<String> =
        requiredList(key).mapIndexed { index, value ->
            value as? String ?: error("Expected $key[$index] to be a string")
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
            require(consumeIf(expected)) { "Expected '$expected' at offset $index" }
        }

        private fun consumeIf(expected: Char): Boolean =
            if (peekOrNull() == expected) {
                index += 1
                true
            } else {
                false
            }

        private fun peek(): Char =
            peekOrNull() ?: error("Unexpected end of JSON input at offset $index")

        private fun peekOrNull(): Char? =
            source.getOrNull(index)
    }
}
