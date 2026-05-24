package org.skia.foundation.awt

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AwtFontIsolationTest {

    @Test
    fun `AWT font imports stay confined to explicit legacy font surfaces`() {
        val projectRoot = findProjectRoot()
        val sourceRoot = projectRoot.resolve("cpu-raster/src/main/kotlin")
        val allowedPaths = setOf(
            "org/skia/foundation/awt/AwtTypeface.kt",
            "org/skia/foundation/awt/LiberationFontMgr.kt",
            "org/skia/foundation/awt/AwtPathConverter.kt",
            "org/skia/shaper/JavaTextLayoutShaper.kt",
            "org/skia/shaper/SkShaper.kt",
            "org/skia/testing/DiffImage.kt",
        )
        val forbiddenImports = listOf(
            Regex("""^\s*import\s+org\.skia\.foundation\.awt(?:\..*)?(?:\s+as\s+\w+)?\s*$""", RegexOption.MULTILINE),
            Regex("""^\s*import\s+java\.awt(?:\.\*|\.Font(?:\..*)?)(?:\s+as\s+\w+)?\s*$""", RegexOption.MULTILINE),
            Regex("""^\s*import\s+java\.awt\.font(?:\..*)?(?:\s+as\s+\w+)?\s*$""", RegexOption.MULTILINE),
            Regex("""^\s*import\s+java\.awt\.geom(?:\..*)?(?:\s+as\s+\w+)?\s*$""", RegexOption.MULTILINE),
            Regex("""^\s*import\s+java\.awt\.GraphicsEnvironment(?:\s+as\s+\w+)?\s*$""", RegexOption.MULTILINE),
        )

        val offenders = Files.walk(sourceRoot).use { paths ->
            paths.iterator().asSequence()
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                .filter { path ->
                    val relative = sourceRoot.relativize(path).toString()
                    relative !in allowedPaths && forbiddenImports.any { it.containsMatchIn(Files.readString(path)) }
                }
                .map { sourceRoot.relativize(it).toString() }
                .toList()
        }

        assertTrue(
            offenders.isEmpty(),
            "AWT font imports must stay in explicit legacy/optional surfaces: $offenders",
        )
    }

    private fun findProjectRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (current.fileName != null) {
            if (Files.exists(current.resolve("settings.gradle.kts")) ||
                Files.exists(current.resolve("settings.gradle"))
            ) {
                return current
            }
            current = current.parent
        }
        error("Could not find project root from ${System.getProperty("user.dir")}")
    }
}
