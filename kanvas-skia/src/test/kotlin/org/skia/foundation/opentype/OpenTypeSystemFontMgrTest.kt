package org.skia.foundation.opentype

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.skia.foundation.SkFontStyle
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

class OpenTypeSystemFontMgrTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `scanner finds supported font extensions recursively`() {
        val nested = Files.createDirectories(tempDir.resolve("nested"))
        copyFont("LiberationSans-Regular.ttf", nested.resolve("LiberationSans-Regular.ttf"))
        copyFont("LiberationSans-Bold.ttf", nested.resolve("LiberationSans-Bold.OTF"))
        Files.writeString(nested.resolve("not-a-font.txt"), "ignored")

        val files = SystemFontScanner.scanFontFiles(listOf(tempDir))

        assertEquals(
            listOf(
                nested.resolve("LiberationSans-Bold.OTF").toRealPath(),
                nested.resolve("LiberationSans-Regular.ttf").toRealPath(),
            ),
            files,
        )
    }

    @Test
    fun `system manager skips unreadable font bytes`() {
        Files.write(tempDir.resolve("garbage.ttf"), ByteArray(32) { it.toByte() })
        copyFont("LiberationSans-Regular.ttf", tempDir.resolve("LiberationSans-Regular.ttf"))

        val mgr = OpenTypeSystemFontMgr.CreateFromRoots(listOf(tempDir))

        assertEquals(1, mgr.countFamilies())
        assertEquals("Liberation Sans", mgr.getFamilyName(0))
    }

    @Test
    fun `diagnostics report malformed or unsupported files`() {
        Files.write(tempDir.resolve("garbage.ttf"), ByteArray(32) { it.toByte() })
        val diagnostics = mutableListOf<OpenTypeSystemFontDiagnostic>()

        OpenTypeSystemFontMgr.CreateWithPolicy(
            roots = listOf(tempDir),
            diagnosticsSink = diagnostics::add,
        )

        assertTrue(
            diagnostics.any { it is OpenTypeSystemFontDiagnostic.IgnoredFile && it.reason == "unsupported-or-malformed" },
            "Malformed/unsupported fonts should be diagnosable when diagnostics sink is provided",
        )
    }

    @Test
    fun `scanner skips roots with unreadable children`() {
        val unreadable = Files.createDirectories(tempDir.resolve("unreadable"))
        assumeTrue(unreadable.fileSystem.supportedFileAttributeViews().contains("posix"))
        Files.setPosixFilePermissions(unreadable, emptySet<PosixFilePermission>())
        try {
            SystemFontScanner.scanFontFiles(listOf(tempDir))
        } finally {
            Files.setPosixFilePermissions(
                unreadable,
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
            )
        }
    }

    @Test
    fun `system manager indexes OpenType family and styles without AWT`() {
        copyFont("LiberationSans-Regular.ttf", tempDir.resolve("LiberationSans-Regular.ttf"))
        copyFont("LiberationSans-Bold.ttf", tempDir.resolve("LiberationSans-Bold.ttf"))
        copyFont("LiberationSans-Italic.ttf", tempDir.resolve("LiberationSans-Italic.ttf"))
        copyFont("LiberationSans-BoldItalic.ttf", tempDir.resolve("LiberationSans-BoldItalic.ttf"))

        val mgr = OpenTypeSystemFontMgr.CreateFromRoots(listOf(tempDir))

        assertEquals(1, mgr.countFamilies())
        assertEquals("Liberation Sans", mgr.getFamilyName(0))
        assertEquals(4, mgr.matchFamily("Liberation Sans").count())
        assertTrue(mgr.matchFamilyStyle("Liberation Sans", SkFontStyle.Normal()) is OpenTypeTypeface)
        assertEquals(SkFontStyle.Bold(), mgr.matchFamilyStyle("Liberation Sans", SkFontStyle.Bold())?.fontStyle)
        assertEquals(SkFontStyle.Italic(), mgr.matchFamilyStyle("Liberation Sans", SkFontStyle.Italic())?.fontStyle)
        assertEquals(SkFontStyle.BoldItalic(), mgr.matchFamilyStyle("Liberation Sans", SkFontStyle.BoldItalic())?.fontStyle)
    }

    @Test
    fun `system manager derives styles from OpenType tables before filenames`() {
        copyFont("LiberationSans-Regular.ttf", tempDir.resolve("face-a.ttf"))
        copyFont("LiberationSans-Bold.ttf", tempDir.resolve("face-b.ttf"))
        copyFont("LiberationSans-Italic.ttf", tempDir.resolve("face-c.ttf"))
        copyFont("LiberationSans-BoldItalic.ttf", tempDir.resolve("face-d.ttf"))

        val mgr = OpenTypeSystemFontMgr.CreateFromRoots(listOf(tempDir))

        assertEquals(SkFontStyle.Normal(), mgr.matchFamilyStyle("Liberation Sans", SkFontStyle.Normal())?.fontStyle)
        assertEquals(SkFontStyle.Bold(), mgr.matchFamilyStyle("Liberation Sans", SkFontStyle.Bold())?.fontStyle)
        assertEquals(SkFontStyle.Italic(), mgr.matchFamilyStyle("Liberation Sans", SkFontStyle.Italic())?.fontStyle)
        assertEquals(SkFontStyle.BoldItalic(), mgr.matchFamilyStyle("Liberation Sans", SkFontStyle.BoldItalic())?.fontStyle)
    }

    @Test
    fun `regular OpenType style wins over misleading filename`() {
        copyFont("LiberationSans-Regular.ttf", tempDir.resolve("Definitely-Bold-Italic.ttf"))

        val mgr = OpenTypeSystemFontMgr.CreateFromRoots(listOf(tempDir))

        assertEquals(SkFontStyle.Normal(), mgr.matchFamilyStyle("Liberation Sans", SkFontStyle.Normal())?.fontStyle)
        assertEquals(SkFontStyle.Normal(), mgr.matchFamilyStyle("Liberation Sans", SkFontStyle.Bold())?.fontStyle)
    }

    @Test
    fun `system manager supports aliases fallback and direct font loading`() {
        val regular = tempDir.resolve("LiberationSans-Regular.ttf")
        copyFont("LiberationSans-Regular.ttf", regular)

        val mgr = OpenTypeSystemFontMgr.CreateFromRoots(listOf(tempDir))

        assertNotNull(mgr.matchFamilyStyle(null, SkFontStyle.Normal()))
        assertNotNull(mgr.matchFamilyStyle("sans-serif", SkFontStyle.Normal()))
        assertNotNull(mgr.matchFamilyStyleCharacter(null, SkFontStyle.Normal(), null, 'A'.code))
        assertTrue(mgr.makeFromFile(regular.toString()) is OpenTypeTypeface)
    }

    @Test
    fun `fallback planner honors generic alias chains over incidental order`() {
        val available = listOf("Liberation Mono", "Liberation Serif", "Liberation Sans")
        val policy = OpenTypeSystemFallbackPolicy.Default.copy(
            genericFallbackChains = mapOf(
                "sans-serif" to listOf("Liberation Sans"),
                "serif" to listOf("Liberation Serif"),
                "monospace" to listOf("Liberation Mono"),
            ),
            scriptFallbackChains = emptyMap(),
            localeFallbackChains = emptyMap(),
            emojiPreferredFamilies = emptyList(),
        )

        val planned = OpenTypeSystemFontMgr.planFallbackFamilyNames(
            availableFamilyNames = available,
            requestedFamily = "sans-serif",
            bcp47 = null,
            character = 'A'.code,
            policy = policy,
        )

        assertEquals("Liberation Sans", planned.first())
    }

    @Test
    fun `fallback planner applies locale policy zh vs ja`() {
        val available = listOf("Noto Sans CJK JP", "Noto Sans CJK SC", "Liberation Sans")
        val policy = OpenTypeSystemFallbackPolicy.Default.copy(
            genericFallbackChains = mapOf("sans-serif" to listOf("Liberation Sans")),
            scriptFallbackChains = emptyMap(),
            localeFallbackChains = mapOf(
                "zh" to listOf("Noto Sans CJK SC", "Noto Sans CJK JP"),
                "ja" to listOf("Noto Sans CJK JP", "Noto Sans CJK SC"),
            ),
            emojiPreferredFamilies = emptyList(),
        )

        val zhOrder = OpenTypeSystemFontMgr.planFallbackFamilyNames(
            availableFamilyNames = available,
            requestedFamily = null,
            bcp47 = arrayOf("zh"),
            character = 0x5203,
            policy = policy,
        )
        val jaOrder = OpenTypeSystemFontMgr.planFallbackFamilyNames(
            availableFamilyNames = available,
            requestedFamily = null,
            bcp47 = arrayOf("ja"),
            character = 0x5203,
            policy = policy,
        )

        assertEquals("Noto Sans CJK SC", zhOrder.first())
        assertEquals("Noto Sans CJK JP", jaOrder.first())
    }

    @Test
    fun `optional provider can override portable order without being required`() {
        copyFont("LiberationSans-Regular.ttf", tempDir.resolve("LiberationSans-Regular.ttf"))
        val mgr = OpenTypeSystemFontMgr.CreateWithPolicy(
            roots = listOf(tempDir),
            policyProvider = OpenTypeSystemFallbackPolicyProvider { available, portable, _, _ ->
                if (available.contains("Liberation Sans")) listOf("Liberation Sans") + portable else portable
            },
        )
        val tf = mgr.matchFamilyStyleCharacter(null, SkFontStyle.Normal(), arrayOf("en"), 'A'.code)
        assertNotNull(tf)
    }

    private fun copyFont(resourceName: String, target: Path) {
        val resource = "/fonts/liberation/$resourceName"
        val stream = OpenTypeSystemFontMgrTest::class.java.getResourceAsStream(resource)
            ?: error("Missing bundled resource: $resource")
        stream.use { Files.copy(it, target) }
    }
}
