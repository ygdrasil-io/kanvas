package org.skia.foundation.opentype

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.LiberationFontMgr
import org.skia.foundation.SkData
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.asSequence

class LiberationOpenTypeFontMgrTest {
    private val expectedFamilyNames = setOf(
        "Liberation Sans",
        "Liberation Serif",
        "Liberation Mono",
    )

    @Test
    fun `portable manager exposes exact bundled Liberation families`() {
        val mgr = portableMgr()

        assertTrue(mgr.countFamilies() > 0)

        val familyNames = (0 until mgr.countFamilies()).map(mgr::getFamilyName)
        assertEquals(expectedFamilyNames.size, familyNames.size)
        assertEquals(expectedFamilyNames, familyNames.toSet())
    }

    @Test
    fun `matchFamilyStyle returns OpenType backed Liberation Sans bold`() {
        val mgr = portableMgr()

        val typeface = requireNotNull(mgr.matchFamilyStyle("Liberation Sans", SkFontStyle.Bold()))

        assertTrue(typeface is OpenTypeTypeface)
        assertEquals("Liberation Sans", typeface.getFamilyName())
        assertEquals(SkFontStyle.Bold(), typeface.fontStyle)
    }

    @Test
    fun `public LiberationFontMgr factory returns pure OpenType manager`() {
        val mgr = LiberationFontMgr.Make()

        assertTrue(mgr is LiberationOpenTypeFontMgr)
        assertEquals(3, mgr.countFamilies())
    }

    @Test
    fun `matchFamily resolves portable aliases and null fallback`() {
        val mgr = portableMgr()

        assertEquals("Liberation Sans", mgr.matchFamilyStyle(null, SkFontStyle.Normal())!!.getFamilyName())
        assertEquals("Liberation Sans", mgr.matchFamilyStyle("sans-serif", SkFontStyle.Normal())!!.getFamilyName())
        assertEquals("Liberation Serif", mgr.matchFamilyStyle("serif", SkFontStyle.Normal())!!.getFamilyName())
        assertEquals("Liberation Mono", mgr.matchFamilyStyle("monospace", SkFontStyle.Normal())!!.getFamilyName())
        assertEquals(0, mgr.matchFamily("unknown-family").count())
        assertEquals(0, mgr.matchFamily("Comic Sans MS").count())
        assertEquals(0, mgr.matchFamily("Noto Serif CJK").count())
    }

    @Test
    fun `matchFamilyStyleCharacter checks glyph coverage`() {
        val mgr = portableMgr()

        assertNotNull(mgr.matchFamilyStyleCharacter("Liberation Sans", SkFontStyle.Normal(), null, 'A'.code))
    }

    @Test
    fun `style set exposes regular bold italic and bold italic names`() {
        val mgr = portableMgr()
        val set = mgr.matchFamily("Liberation Sans")

        assertEquals(4, set.count())

        val styles = (0 until set.count()).associate { index ->
            val name = StringBuilder()
            val style = set.getStyle(index, null, name)
            name.toString() to style
        }

        assertEquals(
            mapOf(
                "Regular" to SkFontStyle.Normal(),
                "Bold" to SkFontStyle.Bold(),
                "Italic" to SkFontStyle.Italic(),
                "Bold Italic" to SkFontStyle.BoldItalic(),
            ),
            styles,
        )

        for ((name, style) in styles) {
            val typeface = requireNotNull(set.matchStyle(style)) { "style=$name" }
            assertTrue(typeface is OpenTypeTypeface, "style=$name")
            assertEquals(style, typeface.fontStyle, "style=$name")
        }
    }

    @Test
    fun `makeFromData still loads bundled Liberation TTF`() {
        val mgr = portableMgr()
        val typeface = requireNotNull(mgr.makeFromData(SkData.MakeWithCopy(resourceBytes("LiberationSerif-Italic.ttf"))))

        assertTrue(typeface is OpenTypeTypeface)
        assertEquals("Liberation Serif", typeface.getFamilyName())
        assertTrue(typeface.countGlyphs() > 100)
    }

    @Test
    fun `portable font paths do not import AWT imageio or JNI`() {
        val projectRoot = findProjectRoot()
        val portableFontPaths = listOf(
            projectRoot.resolve("kanvas-skia/src/main/kotlin/org/skia/foundation/LiberationFontMgr.kt"),
            projectRoot.resolve("kanvas-skia/src/main/kotlin/org/skia/foundation/SkFont.kt"),
            projectRoot.resolve("kanvas-skia/src/main/kotlin/org/skia/foundation/SkFontArguments.kt"),
            projectRoot.resolve("kanvas-skia/src/main/kotlin/org/skia/foundation/SkFontHinting.kt"),
            projectRoot.resolve("kanvas-skia/src/main/kotlin/org/skia/foundation/SkFontMgr.kt"),
            projectRoot.resolve("kanvas-skia/src/main/kotlin/org/skia/foundation/SkFontPriv.kt"),
            projectRoot.resolve("kanvas-skia/src/main/kotlin/org/skia/foundation/SkFontStyleSet.kt"),
            projectRoot.resolve("kanvas-skia/src/main/kotlin/org/skia/foundation/SkFontVariation.kt"),
            projectRoot.resolve("kanvas-skia/src/main/kotlin/org/skia/foundation/SkTextEncoding.kt"),
            projectRoot.resolve("kanvas-skia/src/main/kotlin/org/skia/foundation/SkTypeface.kt"),
            projectRoot.resolve("kanvas-skia/src/main/kotlin/org/skia/foundation/opentype"),
            projectRoot.resolve("kanvas-skia/src/test/kotlin/org/skia/foundation/SkFontArgumentsTest.kt"),
            projectRoot.resolve("kanvas-skia/src/test/kotlin/org/skia/foundation/SkFontHygieneTest.kt"),
            projectRoot.resolve("kanvas-skia/src/test/kotlin/org/skia/foundation/SkFontMgrFromDataTest.kt"),
            projectRoot.resolve("kanvas-skia/src/test/kotlin/org/skia/foundation/SkFontMgrTest.kt"),
            projectRoot.resolve("kanvas-skia/src/test/kotlin/org/skia/foundation/SkFontStyleSetTest.kt"),
            projectRoot.resolve("kanvas-skia/src/test/kotlin/org/skia/foundation/SkFontTest.kt"),
            projectRoot.resolve("kanvas-skia/src/test/kotlin/org/skia/foundation/SkFontTextToGlyphsTest.kt"),
            projectRoot.resolve("kanvas-skia/src/test/kotlin/org/skia/foundation/SkTextBlobGetInterceptsTest.kt"),
            projectRoot.resolve("skia-integration-tests/src/main/kotlin/org/skia/tests/FontMgrBoundsGM.kt"),
            projectRoot.resolve("skia-integration-tests/src/main/kotlin/org/skia/tests/FontMgrGM.kt"),
            projectRoot.resolve("skia-integration-tests/src/main/kotlin/org/skia/tests/FontMgrMatchGM.kt"),
            projectRoot.resolve("skia-integration-tests/src/test/kotlin/org/skia/tests/FontMgrBoundsTest.kt"),
            projectRoot.resolve("skia-integration-tests/src/test/kotlin/org/skia/tests/FontMgrMatchTest.kt"),
            projectRoot.resolve("skia-integration-tests/src/test/kotlin/org/skia/tests/FontMgrTest.kt"),
        )

        for (path in portableFontPaths) {
            assertTrue(path.existsForGuard(), "Missing portable font path: $path")
        }

        val forbiddenPatterns = listOf(
            Regex("""^\s*import\s+java\.awt(\.|$)""", RegexOption.MULTILINE),
            Regex("""^\s*import\s+javax\.imageio(\.|$)""", RegexOption.MULTILINE),
            Regex("""^\s*import\s+org\.skia\.foundation\.awt(\.|$)""", RegexOption.MULTILINE),
            Regex("""^\s*import\s+.*\bjni\b""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
        )

        val offenders = portableFontPaths
            .asSequence()
            .flatMap { it.kotlinFilesForGuard().asSequence() }
            .flatMap { path ->
                val text = path.readText()
                forbiddenPatterns
                    .filter { it.containsMatchIn(text) }
                    .map { path }
            }
            .distinct()
            .toList()

        assertTrue(offenders.isEmpty(), "Forbidden desktop/native imports in portable font paths: $offenders")
    }

    private fun portableMgr(): SkFontMgr {
        val manager = invokeCompanionFactory(
            "org.skia.foundation.opentype.LiberationOpenTypeFontMgr",
            "Create",
        ) ?: invokeCompanionFactory(
            "org.skia.foundation.opentype.OpenTypeFontMgr",
            "CreatePortable",
        )

        assertNotNull(
            manager,
            "A pure-Kotlin bundled Liberation OpenType manager factory must be available",
        )

        return manager!!
    }

    private fun invokeCompanionFactory(className: String, methodName: String): SkFontMgr? {
        val clazz = runCatching { Class.forName(className) }.getOrNull() ?: return null
        val companion = runCatching { clazz.getField("Companion").get(null) }.getOrNull() ?: return null
        val method = companion.javaClass.methods
            .firstOrNull { it.name == methodName && it.parameterCount == 0 }
            ?: return null
        return method.invoke(companion) as SkFontMgr
    }

    private fun resourceBytes(fileName: String): ByteArray {
        val resource = "/fonts/liberation/$fileName"
        val stream = LiberationOpenTypeFontMgrTest::class.java.getResourceAsStream(resource)
            ?: error("Missing bundled resource: $resource")
        return stream.use { it.readBytes() }
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

    private fun Path.existsForGuard(): Boolean = isDirectory() || Files.isRegularFile(this)

    private fun Path.kotlinFilesForGuard(): List<Path> =
        if (isDirectory()) {
            Files.walk(this).use { paths ->
                paths.asSequence()
                    .filter { it.name.endsWith(".kt") }
                    .toList()
            }
        } else {
            listOf(this).filter { it.name.endsWith(".kt") }
        }
}
