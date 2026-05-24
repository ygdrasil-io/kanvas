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
    fun `opentype source files do not import AWT or JNI`() {
        val opentypeSources = findProjectRoot()
            .resolve("kanvas-skia/src/main/kotlin/org/skia/foundation/opentype")

        assertTrue(opentypeSources.isDirectory(), "Missing opentype source directory: $opentypeSources")

        val forbiddenPatterns = listOf(
            Regex("""^\s*import\s+java\.awt(\.|$)""", RegexOption.MULTILINE),
            Regex("""^\s*import\s+.*\bjni\b""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
        )

        val offenders = Files.walk(opentypeSources).use { paths ->
            paths.asSequence()
                .filter { it.name.endsWith(".kt") }
                .flatMap { path ->
                    val text = path.readText()
                    forbiddenPatterns
                        .filter { it.containsMatchIn(text) }
                        .map { path }
                }
                .distinct()
                .toList()
        }

        assertTrue(offenders.isEmpty(), "Forbidden AWT/JNI references in opentype sources: $offenders")
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
}
