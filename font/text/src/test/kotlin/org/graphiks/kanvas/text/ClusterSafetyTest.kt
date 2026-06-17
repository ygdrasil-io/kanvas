package org.graphiks.kanvas.text

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.text.shaping.ClusterSafetyFixture
import org.graphiks.kanvas.text.shaping.ClusterSafetySuite
import org.graphiks.kanvas.text.shaping.defaultClusterSafetyReportJson

class ClusterSafetyTest {
    private val clusterFixtureNames = listOf(
        "cluster-arabic-mark.txt",
        "cluster-cjk-ideographic-variation-sequence.txt",
        "cluster-cjk-standardized-variant.txt",
        "cluster-cjk-variation-selector.txt",
        "cluster-devanagari-conjunct.txt",
        "cluster-emoji-family-zwj.txt",
        "cluster-emoji-skin-tone.txt",
        "cluster-mixed-bidi.txt",
        "cluster-negative-split.txt",
        "cluster-thai-tone.txt",
        "cluster-vs15-vs16.txt",
    )

    @Test
    fun clusterSafetyReportGoldenPinsFixtureMatrixAndLegacyGate() {
        val json = defaultClusterSafetyReportJson()

        assertEquals(
            readProjectFile("reports/font/fixtures/expected/unicode/cluster-safety-report.json"),
            json,
        )
        assertTrue(json.contains("\"dumpId\": \"cluster-safety-report\""))
        assertTrue(json.contains("\"ownerTickets\": [\"KFONT-M5-005\"]"))
        assertTrue(json.contains("\"fixtureName\": \"cluster-emoji-family-zwj.txt\""))
        assertTrue(json.contains("\"fixtureName\": \"cluster-cjk-ideographic-variation-sequence.txt\""))
        assertTrue(json.contains("\"fixtureName\": \"cluster-cjk-standardized-variant.txt\""))
        assertTrue(json.contains("\"fixtureName\": \"cluster-negative-split.txt\""))
        assertTrue(json.contains("\"gate\": \"scaledemoji\""))
        assertTrue(json.contains("\"dumpId\": \"unicode-segments\""))
        assertTrue(json.contains("\"dumpId\": \"bidi-runs\""))
        assertTrue(json.contains("\"dumpId\": \"script-runs\""))
        assertTrue(json.contains("\"code\": \"text.shaping.cluster-invariant-failed\""))
    }

    @Test
    fun clusterSafetyFixturesAreCheckedInAndNonEmpty() {
        val fixtureDir = projectRoot().resolve("reports/font/fixtures/expected/unicode")

        clusterFixtureNames.forEach { fixtureName ->
            val path = fixtureDir.resolve(fixtureName)
            assertTrue(Files.isRegularFile(path), "missing fixture $fixtureName")
            assertTrue(Files.readString(path).isNotEmpty(), "fixture $fixtureName must not be empty")
        }
    }

    @Test
    fun clusterSafetySuitePropagatesUnicodeVersionMismatchDiagnostics() {
        val report = ClusterSafetySuite(expectedUnicodeVersion = "99.0.0").evaluate(
            fixtures = listOf(
                ClusterSafetyFixture(
                    fixtureName = "cluster-emoji-family-zwj.txt",
                    sourceText = "\uD83D\uDC66\uD83C\uDFFB\u200D\uD83D\uDC66",
                    legacyGate = "scaledemoji",
                ),
            ),
            sourceDumpRefs = emptyList(),
        ).toCanonicalJson()

        assertTrue(report.contains("\"code\": \"text.shaping.unicode-data-version-mismatch\""))
        assertTrue(report.contains("\"gate\": \"scaledemoji\""))
    }

    private fun readProjectFile(relativePath: String): String =
        Files.readString(projectRoot().resolve(relativePath))

    private fun projectRoot(): Path =
        generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
}
