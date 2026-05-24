package org.skia.foundation.opentype

import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkFontStyleSet
import org.skia.foundation.SkTypeface
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Pure-Kotlin system font manager backed by files discovered on disk and
 * parsed through [OpenTypeTypeface].
 *
 * Unsupported files are skipped. In practice this means TrueType-flavoured
 * `.ttf` / `.ttc` fonts are usable today, while OpenType/CFF `.otf` files are
 * accepted by the scanner but may be ignored until the parser grows CFF data.
 */
public class OpenTypeSystemFontMgr private constructor(
    private val families: List<OpenTypeSystemFamily>,
    @Suppress("UNUSED_PARAMETER") marker: ConstructorMarker,
) : SkFontMgr() {
    public constructor(fontFiles: List<Path>) : this(loadFamilies(fontFiles), ConstructorMarker)

    private val familiesByName: Map<String, OpenTypeSystemFamily> =
        families.associateBy { it.name.lowercase() }

    override fun countFamilies(): Int = families.size

    override fun getFamilyName(index: Int): String =
        families.getOrNull(index)?.name
            ?: throw IndexOutOfBoundsException("OpenTypeSystemFontMgr has ${families.size} families ; index=$index")

    override fun createStyleSet(index: Int): SkFontStyleSet =
        families.getOrNull(index)?.styleSet
            ?: throw IndexOutOfBoundsException("OpenTypeSystemFontMgr has ${families.size} families ; index=$index")

    override fun matchFamily(familyName: String?): SkFontStyleSet {
        if (familyName == null) return families.firstOrNull()?.styleSet ?: SkFontStyleSet.CreateEmpty()
        return findFamily(familyName)?.styleSet ?: SkFontStyleSet.CreateEmpty()
    }

    override fun matchFamilyStyle(familyName: String?, style: SkFontStyle): SkTypeface? =
        matchFamily(familyName).matchStyle(style)

    override fun matchFamilyStyleCharacter(
        familyName: String?,
        style: SkFontStyle,
        bcp47: Array<String>?,
        character: Int,
    ): SkTypeface? {
        val preferred = matchFamilyStyle(familyName, style)
        if (preferred != null && preferred.unicharToGlyph(character) != 0) return preferred
        for (family in families) {
            val match = family.styleSet.matchStyle(style)
            if (match != null && match.unicharToGlyph(character) != 0) return match
        }
        return null
    }

    override fun makeFromData(data: org.skia.foundation.SkData, ttcIndex: Int): SkTypeface? {
        if (data.size == 0) return null
        return OpenTypeTypeface.MakeFromBytes(data.toByteArray(), ttcIndex)
    }

    override fun makeFromStream(stream: java.io.InputStream, ttcIndex: Int): SkTypeface? =
        makeFromData(org.skia.foundation.SkData.MakeWithCopy(stream.readBytes()), ttcIndex)

    override fun makeFromFile(path: String, ttcIndex: Int): SkTypeface? {
        val file = File(path)
        if (!file.isFile) return null
        return try {
            file.inputStream().use { makeFromStream(it, ttcIndex) }
        } catch (e: IOException) {
            null
        }
    }

    override fun legacyMakeTypeface(familyName: String?, style: SkFontStyle): SkTypeface? =
        matchFamilyStyle(familyName, style)

    private fun findFamily(familyName: String): OpenTypeSystemFamily? {
        val normalized = familyName.lowercase()
        familiesByName[normalized]?.let { return it }
        return when (normalized) {
            "sans-serif", "sans serif" -> families.firstOrNull { it.name.contains("sans", ignoreCase = true) }
            "serif" -> families.firstOrNull { it.name.contains("serif", ignoreCase = true) }
            "monospace", "monospaced", "mono" -> families.firstOrNull { it.name.contains("mono", ignoreCase = true) }
            else -> null
        }
    }

    public companion object {
        @Suppress("FunctionName")
        public fun Create(): OpenTypeSystemFontMgr =
            OpenTypeSystemFontMgr(SystemFontScanner.scanSystemFontFiles())

        @Suppress("FunctionName")
        public fun CreateFromRoots(roots: List<Path>): OpenTypeSystemFontMgr =
            OpenTypeSystemFontMgr(SystemFontScanner.scanFontFiles(roots))

        private fun loadFamilies(fontFiles: List<Path>): List<OpenTypeSystemFamily> {
            val byFamily = linkedMapOf<String, MutableList<OpenTypeSystemFace>>()
            for (file in fontFiles.distinct()) {
                val bytes = try {
                    Files.readAllBytes(file)
                } catch (e: IOException) {
                    continue
                }
                var index = 0
                while (true) {
                    val parsed = OpenTypeTypeface.MakeFromBytes(bytes, index) ?: break
                    val style = inferStyle(parsed, file)
                    val styled = parsed.withFontStyle(style)
                    val familyName = styled.getFamilyName()
                    val face = OpenTypeSystemFace(styleName(style), style, styled)
                    byFamily.getOrPut(familyName) { ArrayList() }.add(face)
                    index += 1
                }
            }
            return byFamily.entries
                .sortedBy { it.key.lowercase() }
                .map { (familyName, faces) ->
                    OpenTypeSystemFamily(
                        familyName,
                        OpenTypeSystemStyleSet(faces.distinctBy { it.style to it.typeface.getPostScriptName() }),
                    )
                }
        }

        private fun inferStyle(typeface: SkTypeface, file: Path): SkFontStyle {
            val source = buildString {
                append(typeface.getPostScriptName().orEmpty())
                append(' ')
                append(file.name)
            }.lowercase()
            val bold = listOf("bold", "black", "heavy", "semibold", "demibold").any(source::contains)
            val italic = listOf("italic", "oblique").any(source::contains)
            return when {
                bold && italic -> SkFontStyle.BoldItalic()
                bold -> SkFontStyle.Bold()
                italic -> SkFontStyle.Italic()
                else -> SkFontStyle.Normal()
            }
        }

        private fun styleName(style: SkFontStyle): String =
            when {
                style.weight >= SkFontStyle.kBold_Weight &&
                    style.slant != SkFontStyle.Slant.kUpright_Slant -> "Bold Italic"
                style.weight >= SkFontStyle.kBold_Weight -> "Bold"
                style.slant != SkFontStyle.Slant.kUpright_Slant -> "Italic"
                else -> "Regular"
            }
    }
}

public object SystemFontScanner {
    public fun scanSystemFontFiles(): List<Path> =
        scanFontFiles(systemFontRoots())

    public fun scanFontFiles(roots: List<Path>): List<Path> {
        val out = linkedSetOf<Path>()
        for (root in roots) {
            if (!Files.isDirectory(root)) continue
            try {
                Files.walk(root).use { stream ->
                    stream
                        .filter { it.isRegularFile() }
                        .filter { it.extension.lowercase() in supportedExtensions }
                        .map { it.toRealPath() }
                        .forEach(out::add)
                }
            } catch (e: IOException) {
                continue
            } catch (e: UncheckedIOException) {
                continue
            } catch (e: SecurityException) {
                continue
            }
        }
        return out.sortedBy { it.toString() }
    }

    public fun systemFontRoots(): List<Path> {
        val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() }?.let(Path::of)
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> {
                val windir = System.getenv("WINDIR") ?: "C:\\Windows"
                listOf(Path.of(windir, "Fonts"))
            }
            os.contains("mac") || os.contains("darwin") -> {
                buildList {
                    add(Path.of("/System/Library/Fonts"))
                    add(Path.of("/Library/Fonts"))
                    if (home != null) add(home.resolve("Library/Fonts"))
                }
            }
            else -> {
                val xdgDataHome = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(Path::of)
                val xdgDataDirs = System.getenv("XDG_DATA_DIRS")
                    ?.takeIf { it.isNotBlank() }
                    ?.split(File.pathSeparator)
                    ?.filter { it.isNotBlank() }
                    ?.map { Path.of(it).resolve("fonts") }
                    .orEmpty()
                buildList {
                    if (xdgDataHome != null) add(xdgDataHome.resolve("fonts"))
                    add(Path.of("/usr/share/fonts"))
                    add(Path.of("/usr/local/share/fonts"))
                    if (home != null) {
                        add(home.resolve(".fonts"))
                        add(home.resolve(".local/share/fonts"))
                    }
                    addAll(xdgDataDirs)
                }
            }
        }
    }

    private val supportedExtensions = setOf("ttf", "otf", "ttc", "otc")
}

private data class OpenTypeSystemFamily(
    val name: String,
    val styleSet: OpenTypeSystemStyleSet,
)

private data class OpenTypeSystemFace(
    val styleName: String,
    val style: SkFontStyle,
    val typeface: SkTypeface,
)

private class OpenTypeSystemStyleSet(
    private val faces: List<OpenTypeSystemFace>,
) : SkFontStyleSet() {
    override fun count(): Int = faces.size

    override fun getStyle(index: Int, style: SkFontStyle?, name: StringBuilder?): SkFontStyle {
        val face = faces.getOrNull(index)
            ?: throw IndexOutOfBoundsException("OpenTypeSystemStyleSet has ${faces.size} typefaces ; index=$index")
        name?.append(face.styleName)
        return face.style
    }

    override fun createTypeface(index: Int): SkTypeface? = faces.getOrNull(index)?.typeface

    override fun matchStyle(pattern: SkFontStyle): SkTypeface? = matchStyleCSS3(pattern)
}

private object ConstructorMarker
