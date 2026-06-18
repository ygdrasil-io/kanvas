package org.graphiks.kanvas.text

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
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
import org.graphiks.kanvas.text.shaping.BasicOpenTypeShapingEngine
import org.graphiks.kanvas.text.shaping.CMapGlyphMapper
import org.graphiks.kanvas.text.shaping.ShapingRequest
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
        val result = engineFor(face).shape(
            ShapingRequest(
                text = text,
                typefaceId = face.typefaceId,
                fontSize = 20f,
            ),
        )

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(1, result.glyphRuns.size)
        assertEquals(4, result.glyphRuns.single().clusters.size)
        assertTrue(
            result.glyphRuns.single().glyphIds != rawGlyphIds,
            "Arabic joining fixture should not stay on raw cmap glyph IDs when contextual forms are applied.",
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

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(1, result.glyphRuns.size)
        assertEquals(2, result.glyphRuns.single().clusters.size)
        assertTrue(
            result.glyphRuns.single().clusters.any { it.offsetY != 0f || it.advanceX == 0f },
            "Arabic mark fixture should carry a positioned or zero-advance mark cluster.",
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
        val shapedRun = result.glyphRuns.single()

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(1, result.glyphRuns.size)
        assertTrue(
            shapedRun.glyphIds.size < rawGlyphIds.size || shapedRun.glyphIds != rawGlyphIds,
            "Arabic lam-alef bounded runtime check should not stay on the raw cmap glyph sequence.",
        )
    }

    @Test
    fun basicOpenTypeShapingEngineRequiresParagraphBidiContextForVendoredArabicMixedFixture() {
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

        assertContains(
            result.diagnostics.map { it.code },
            TEXT_SHAPING_PARAGRAPH_BIDI_REQUIRED_DIAGNOSTIC_CODE,
        )
    }

    @Test
    fun arabicShapingReportGoldenExistsAndTracksFixtureWave() {
        val report = readProjectFile("reports/font/fixtures/expected/shaping/arabic-shaping-report.json")

        assertContains(report, """"dumpId": "arabic-shaping-report"""")
        assertContains(report, """"ownerTickets": ["KFONT-M6-007"]""")
        assertContains(report, """"fixtureId": "single-ttf-noto-naskh-arabic"""")
        assertContains(report, """"caseId": "joining-forms"""")
        assertContains(report, """"remainingGateId": "lam-alef-positive-evidence"""")
        assertContains(report, """"caseId": "mixed-bidi-paragraph-required"""")
        assertContains(report, """"no-arabic-shaping-support-claim"""")
        assertContains(report, """"no-native-shaper-oracle-claim"""")
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
}
