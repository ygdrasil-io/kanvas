package org.graphiks.kanvas.text

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.text.shaping.defaultFallbackSegmentationReportJson

class FallbackSegmentationTest {
    @Test
    fun fallbackSegmentationReportGoldenPinsClusterSafeFallbackEvidence() {
        val json = defaultFallbackSegmentationReportJson()

        assertEquals(
            readProjectFile("reports/font/fixtures/expected/fallback/fallback-segmentation-report.json"),
            json,
        )
        assertTrue(json.contains("\"dumpId\": \"fallback-segmentation-report\""))
        assertTrue(json.contains("\"ownerTickets\": [\"KFONT-M7-004\"]"))
        assertTrue(json.contains("\"fixtureName\": \"fallback-cluster-emoji-zwj.txt\""))
        assertTrue(json.contains("\"fixtureName\": \"fallback-cluster-negative-split.txt\""))
        assertTrue(json.contains("\"gate\": \"scaledemoji\""))
        assertTrue(json.contains("\"code\": \"text.shaping.cluster-invariant-failed\""))
    }

    @Test
    fun fallbackSegmentationFixturesAreCheckedInAndNonEmpty() {
        val fixtureNames =
            listOf(
                "fallback-cluster-arabic-mark.txt",
                "fallback-cluster-cjk-vs.txt",
                "fallback-cluster-devanagari.txt",
                "fallback-cluster-emoji-zwj.txt",
                "fallback-cluster-latin-mark.txt",
                "fallback-cluster-negative-split.txt",
                "fallback-cluster-skin-tone.txt",
                "fallback-cluster-thai.txt",
                "fallback-cluster-vs15-vs16.txt",
            )

        fixtureNames.forEach { fixtureName ->
            val content = readProjectFile("reports/font/fixtures/expected/fallback/$fixtureName")
            assertTrue(content.isNotBlank(), "$fixtureName should be checked in and non-empty.")
        }
    }

    private fun readProjectFile(relativePath: String): String =
        Files.readString(projectRoot().resolve(relativePath))

    private fun projectRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (current.parent != null && !Files.isDirectory(current.resolve("reports/pure-kotlin-text"))) {
            current = current.parent
        }
        return current
    }
}
