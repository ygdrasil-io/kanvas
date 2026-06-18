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
import org.graphiks.kanvas.text.shaping.PinnedScriptItemizer
import org.graphiks.kanvas.text.shaping.ShapingRequest

class DevanagariShapingFixtureTest {
    @Test
    fun pinnedScriptItemizerShapesVendoredPrebaseMatraCaseAsDeva() {
        val face = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440711",
            relativePath = "reports/font/fixtures/fonts/fallback/NotoSansDevanagari-Regular.ttf",
        )
        val result = engineFor(face).shape(
            ShapingRequest(
                text = "\u0915\u093F",
                typefaceId = face.typefaceId,
                fontSize = 20f,
            ),
        )

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(1, result.glyphRuns.size)
        assertEquals("Deva", result.glyphRuns.single().script)
    }

    @Test
    fun basicOpenTypeShapingEngineShapesVendoredDevanagariConsonantCluster() {
        val face = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440712",
            relativePath = "reports/font/fixtures/fonts/fallback/NotoSansDevanagari-Regular.ttf",
        )
        val result = engineFor(face).shape(
            ShapingRequest(
                text = "\u0915\u094D\u0937\u093E",
                typefaceId = face.typefaceId,
                fontSize = 20f,
            ),
        )

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(1, result.glyphRuns.size)
        assertEquals("Deva", result.glyphRuns.single().script)
        assertEquals(2, result.glyphRuns.single().glyphIds.size)
        assertEquals(1, result.glyphRuns.single().clusters.size)
        assertEquals(0..3, result.glyphRuns.single().clusters.single().textRange)
    }

    @Test
    fun basicOpenTypeShapingEngineShapesVendoredDevanagariRephLikeCase() {
        val face = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440713",
            relativePath = "reports/font/fixtures/fonts/fallback/NotoSansDevanagari-Regular.ttf",
        )
        val result = engineFor(face).shape(
            ShapingRequest(
                text = "\u0930\u094D\u0915",
                typefaceId = face.typefaceId,
                fontSize = 20f,
            ),
        )

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(1, result.glyphRuns.size)
        assertEquals("Deva", result.glyphRuns.single().script)
        assertTrue(
            result.glyphRuns.single().clusters.any { it.offsetX != 0f || it.advanceX == 0f },
            "Reph-like case should expose a reordered or zero-advance cluster on the vendored font.",
        )
    }

    @Test
    fun basicOpenTypeShapingEngineShapesVendoredDevanagariMarkPlacementCase() {
        val face = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440714",
            relativePath = "reports/font/fixtures/fonts/fallback/NotoSansDevanagari-Regular.ttf",
        )
        val result = engineFor(face).shape(
            ShapingRequest(
                text = "\u0915\u0902",
                typefaceId = face.typefaceId,
                fontSize = 20f,
            ),
        )

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(1, result.glyphRuns.size)
        assertEquals("Deva", result.glyphRuns.single().script)
        assertTrue(
            result.glyphRuns.single().clusters.any { it.offsetX != 0f || it.offsetY != 0f || it.advanceX == 0f },
            "Devanagari mark case should expose a positioned or zero-advance mark cluster on the vendored font.",
        )
    }

    @Test
    fun devanagariShapingReportGoldenExistsAndTracksFixtureWave() {
        val report = readProjectFile("reports/font/fixtures/expected/shaping/devanagari-shaping-report.json")

        assertContains(report, """"dumpId": "devanagari-shaping-report"""")
        assertContains(report, """"ownerTickets": ["KFONT-M6-008"]""")
        assertContains(report, """"fixtureId": "single-ttf-noto-sans-devanagari"""")
        assertContains(report, """"caseId": "prebase-matra"""")
        assertContains(report, """"caseId": "consonant-cluster"""")
        assertContains(report, """"caseId": "reph-like"""")
        assertContains(report, """"caseId": "mark-placement"""")
        assertContains(report, """"remainingGateId": "indic-syllable-plan-and-phase-evidence"""")
        assertContains(report, """"no-devanagari-shaping-support-claim"""")
        assertContains(report, """"no-native-shaper-oracle-claim"""")
    }

    private fun readProjectFile(relativePath: String): String =
        Files.readString(projectRoot().resolve(relativePath))

    private fun engineFor(face: ParsedFixtureFace): BasicOpenTypeShapingEngine =
        BasicOpenTypeShapingEngine(
            glyphMapper = CMapGlyphMapper(cmapsByTypefaceId = mapOf(face.typefaceId to face.cmap)),
            gsubTablesByTypefaceId = face.gsub?.let { mapOf(face.typefaceId to it) }.orEmpty(),
            gdefTablesByTypefaceId = face.gdef?.let { mapOf(face.typefaceId to it) }.orEmpty(),
            gposTablesByTypefaceId = face.gpos?.let { mapOf(face.typefaceId to it) }.orEmpty(),
            gposSingleTablesByTypefaceId = face.gposSingles?.let { mapOf(face.typefaceId to it) }.orEmpty(),
            gposPairTablesByTypefaceId = face.gposPairs?.let { mapOf(face.typefaceId to it) }.orEmpty(),
            kernUnitsPerEmByTypefaceId = mapOf(face.typefaceId to face.unitsPerEm),
            scriptItemizer = PinnedScriptItemizer(),
        )

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
