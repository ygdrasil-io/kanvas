package org.graphiks.kanvas.text

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
import org.graphiks.kanvas.text.shaping.FeatureSet
import org.graphiks.kanvas.text.shaping.PinnedUnicodeDataSetResources
import org.graphiks.kanvas.text.shaping.ScriptExtensionsItemizer
import org.graphiks.kanvas.text.shaping.ScriptItemizer
import org.graphiks.kanvas.text.shaping.ScriptRun
import org.graphiks.kanvas.text.shaping.ShapingRequest

class ThaiCjkBoundaryFixtureTest {
    @Test
    fun basicOpenTypeShapingEngineShapesVendoredThaiToneMarkCase() {
        val face = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440721",
            relativePath = "reports/font/fixtures/fonts/fallback/NotoSansThai-Regular.ttf",
        )
        val result = engineFor(face).shape(
            ShapingRequest(
                text = "\u0E01\u0E49",
                typefaceId = face.typefaceId,
                fontSize = 20f,
            ),
        )

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(1, result.glyphRuns.size)
        assertEquals("Thai", result.glyphRuns.single().script)
        assertTrue(
            result.glyphRuns.single().clusters.any { it.offsetX != 0f || it.offsetY != 0f || it.advanceX == 0f },
            "Thai tone-mark case should expose a positioned or zero-advance mark cluster on the vendored font.",
        )
    }

    @Test
    fun basicOpenTypeShapingEnginePreservesVendoredMixedLatinThaiScriptBoundaries() {
        val face = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440722",
            relativePath = "reports/font/fixtures/fonts/fallback/NotoSansThai-Regular.ttf",
        )
        val result = engineFor(face).shape(
            ShapingRequest(
                text = "A\u0E01\u0E49A",
                typefaceId = face.typefaceId,
                fontSize = 20f,
            ),
        )

        assertEquals(emptyList(), result.diagnostics)
        assertEquals(listOf("Latn", "Thai", "Latn"), result.glyphRuns.map { it.script })
    }

    @Test
    fun basicOpenTypeShapingEngineAppliesVendoredCjkKanaVerticalAlternateWhenVertRequested() {
        val face = parsedFixtureFace(
            uuid = "550e8400-e29b-41d4-a716-446655440723",
            relativePath = "reports/font/fixtures/fonts/fallback/NotoSansSC-Regular.otf",
        )
        val defaultResult = engineFor(face).shape(
            ShapingRequest(
                text = "\u30A2",
                typefaceId = face.typefaceId,
                fontSize = 20f,
            ),
        )
        val verticalResult = engineFor(face).shape(
            ShapingRequest(
                text = "\u30A2",
                typefaceId = face.typefaceId,
                fontSize = 20f,
                features = FeatureSet(mapOf("vert" to 1)),
            ),
        )

        assertEquals(emptyList(), defaultResult.diagnostics)
        assertEquals(emptyList(), verticalResult.diagnostics)
        assertEquals("Kana", defaultResult.glyphRuns.single().script)
        assertEquals("Kana", verticalResult.glyphRuns.single().script)
        assertEquals(1, defaultResult.glyphRuns.single().clusters.size)
        assertEquals(1, verticalResult.glyphRuns.single().clusters.size)
        assertNotEquals(defaultResult.glyphRuns.single().glyphIds, verticalResult.glyphRuns.single().glyphIds)
    }

    @Test
    fun thaiCjkBoundaryReportGoldenExistsAndTracksFixtureWave() {
        val report = readProjectFile("reports/font/fixtures/expected/shaping/thai-cjk-boundary-report.json")

        assertContains(report, """"dumpId": "thai-cjk-boundary-report"""")
        assertContains(report, """"ownerTickets": ["KFONT-M6-009"]""")
        assertContains(report, """"fixtureId": "single-ttf-noto-sans-thai"""")
        assertContains(report, """"fixtureId": "single-otf-noto-sans-sc"""")
        assertContains(report, """"caseId": "thai-tone-mark"""")
        assertContains(report, """"caseId": "thai-latin-mixed-boundary"""")
        assertContains(report, """"caseId": "cjk-kana-vertical-alternate"""")
        assertContains(report, """"remainingGateId": "thai-dictionary-paragraph-evidence"""")
        assertContains(report, """"remainingGateId": "cjk-variation-selector-evidence"""")
        assertContains(report, """"no-thai-shaping-support-claim"""")
        assertContains(report, """"no-cjk-shaping-support-claim"""")
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
            scriptItemizer = pinnedScriptItemizer(),
        )

    private fun pinnedScriptItemizer(): ScriptItemizer {
        val delegate = ScriptExtensionsItemizer(PinnedUnicodeDataSetResources.load())
        return object : ScriptItemizer {
            override fun itemize(request: ShapingRequest): List<ScriptRun> {
                val requestedTextRange = codePointSafeTextRange(request.text, request.textRange) ?: return emptyList()
                val scopedText = request.text.substring(requestedTextRange.first, requestedTextRange.last + 1)
                return delegate.itemize(scopedText).runs.map { run ->
                    ScriptRun(
                        textRange = (run.utf16Range.first + requestedTextRange.first)..(run.utf16Range.last + requestedTextRange.first),
                        script = run.selectedScript,
                    )
                }
            }
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

    private fun codePointSafeTextRange(text: String, requestedRange: IntRange): IntRange? {
        val normalizedRange = normalizedTextRange(text, requestedRange) ?: return null
        var first = normalizedRange.first
        var last = normalizedRange.last
        if (first > 0 && text[first].isLowSurrogate() && text[first - 1].isHighSurrogate()) {
            first -= 1
        }
        if (last < text.lastIndex && text[last].isHighSurrogate() && text[last + 1].isLowSurrogate()) {
            last += 1
        }
        return first..last
    }

    private fun normalizedTextRange(text: String, requestedRange: IntRange): IntRange? {
        if (text.isEmpty()) return null
        val first = requestedRange.first.coerceAtLeast(0)
        val last = requestedRange.last.coerceAtMost(text.lastIndex)
        return if (first <= last) first..last else null
    }

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
