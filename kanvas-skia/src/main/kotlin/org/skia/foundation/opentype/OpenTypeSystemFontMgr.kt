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
    private val fallbackPolicy: OpenTypeSystemFallbackPolicy,
    private val fallbackPolicyProvider: OpenTypeSystemFallbackPolicyProvider?,
    private val diagnosticsSink: ((OpenTypeSystemFontDiagnostic) -> Unit)?,
    @Suppress("UNUSED_PARAMETER") marker: ConstructorMarker,
) : SkFontMgr() {
    public constructor(fontFiles: List<Path>) : this(
        loadFamilies(fontFiles, null),
        OpenTypeSystemFallbackPolicy.Default,
        null,
        null,
        ConstructorMarker,
    )

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
        val available = families.map { it.name }
        val planned = planFallbackFamilyNames(
            availableFamilyNames = available,
            requestedFamily = familyName,
            bcp47 = bcp47,
            character = character,
            policy = fallbackPolicy,
        )
        val providerPlanned = fallbackPolicyProvider
            ?.prioritizeFamilies(available, planned, character, bcp47)
            ?.ifEmpty { planned }
            ?: planned
        emitDiagnostic(
            OpenTypeSystemFontDiagnostic.FallbackPlanned(
                requestedFamily = familyName,
                character = character,
                bcp47 = bcp47?.toList().orEmpty(),
                orderedFamilies = providerPlanned,
            ),
        )
        for (candidate in providerPlanned) {
            val family = findFamily(candidate) ?: continue
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

    private fun emitDiagnostic(event: OpenTypeSystemFontDiagnostic) {
        diagnosticsSink?.invoke(event)
    }

    public companion object {
        @Suppress("FunctionName")
        public fun Create(): OpenTypeSystemFontMgr =
            OpenTypeSystemFontMgr(
                loadFamilies(SystemFontScanner.scanSystemFontFiles(), null),
                OpenTypeSystemFallbackPolicy.Default,
                null,
                null,
                ConstructorMarker,
            )

        @Suppress("FunctionName")
        public fun CreateFromRoots(roots: List<Path>): OpenTypeSystemFontMgr =
            OpenTypeSystemFontMgr(
                loadFamilies(SystemFontScanner.scanFontFiles(roots), null),
                OpenTypeSystemFallbackPolicy.Default,
                null,
                null,
                ConstructorMarker,
            )

        @Suppress("FunctionName")
        public fun CreateWithPolicy(
            roots: List<Path>,
            policy: OpenTypeSystemFallbackPolicy = OpenTypeSystemFallbackPolicy.Default,
            policyProvider: OpenTypeSystemFallbackPolicyProvider? = null,
            diagnosticsSink: ((OpenTypeSystemFontDiagnostic) -> Unit)? = null,
        ): OpenTypeSystemFontMgr = OpenTypeSystemFontMgr(
            loadFamilies(SystemFontScanner.scanFontFiles(roots), diagnosticsSink),
            policy,
            policyProvider,
            diagnosticsSink,
            ConstructorMarker,
        )

        internal fun planFallbackFamilyNames(
            availableFamilyNames: List<String>,
            requestedFamily: String?,
            bcp47: Array<String>?,
            character: Int,
            policy: OpenTypeSystemFallbackPolicy,
        ): List<String> {
            val available = availableFamilyNames
            val seen = linkedSetOf<String>()
            fun add(name: String?) {
                if (name.isNullOrBlank()) return
                val match = available.firstOrNull { it.equals(name, ignoreCase = true) } ?: return
                seen.add(match)
            }
            fun addAll(names: Iterable<String>) = names.forEach(::add)

            add(requestedFamily)
            val normalizedRequested = requestedFamily?.trim()?.lowercase()
            val generic = when {
                normalizedRequested.isNullOrBlank() -> policy.defaultGeneric
                normalizedRequested in policy.genericFallbackChains.keys -> normalizedRequested
                normalizedRequested in setOf("sans serif", "sans-serif") -> "sans-serif"
                normalizedRequested in setOf("mono", "monospace", "monospaced") -> "monospace"
                normalizedRequested == "serif" -> "serif"
                else -> policy.defaultGeneric
            }

            val locales = bcp47.orEmpty()
                .map { it.substringBefore('-').lowercase() }
                .distinct()
            if (!normalizedRequested.isNullOrBlank()) {
                addAll(policy.genericFallbackChains[generic].orEmpty())
            }
            for (locale in locales) addAll(policy.localeFallbackChains[locale].orEmpty())

            val script = classifyScript(character)
            addAll(policy.scriptFallbackChains[script].orEmpty())
            if (script == "emoji") addAll(policy.emojiPreferredFamilies)
            if (normalizedRequested.isNullOrBlank()) {
                addAll(policy.genericFallbackChains[generic].orEmpty())
            }

            addAll(available)
            return seen.toList()
        }

        private fun classifyScript(codePoint: Int): String {
            return when {
                codePoint in 0x1F300..0x1FAFF || codePoint in 0x2600..0x27BF -> "emoji"
                codePoint in 0x3040..0x30FF -> "japanese"
                codePoint in 0x4E00..0x9FFF -> "han"
                codePoint in 0xAC00..0xD7AF -> "korean"
                codePoint in 0x0000..0x024F -> "latin"
                else -> "default"
            }
        }

        private fun loadFamilies(
            fontFiles: List<Path>,
            diagnosticsSink: ((OpenTypeSystemFontDiagnostic) -> Unit)?,
        ): List<OpenTypeSystemFamily> {
            val byFamily = linkedMapOf<String, MutableList<OpenTypeSystemFace>>()
            for (file in fontFiles.distinct()) {
                val bytes = try {
                    Files.readAllBytes(file)
                } catch (e: IOException) {
                    diagnosticsSink?.invoke(OpenTypeSystemFontDiagnostic.IgnoredFile(file, "io-read-failed"))
                    continue
                }
                var index = 0
                var parsedAnyFace = false
                while (true) {
                    val parsed = OpenTypeTypeface.MakeFromBytes(bytes, index) ?: break
                    parsedAnyFace = true
                    val style = inferStyle(parsed, file)
                    val styled = parsed.withFontStyle(style)
                    val familyName = styled.getFamilyName()
                    val face = OpenTypeSystemFace(styleName(style), style, styled)
                    byFamily.getOrPut(familyName) { ArrayList() }.add(face)
                    index += 1
                }
                if (!parsedAnyFace) {
                    diagnosticsSink?.invoke(OpenTypeSystemFontDiagnostic.IgnoredFile(file, "unsupported-or-malformed"))
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
            if (typeface is OpenTypeTypeface && typeface.hasParsedFontStyle) return typeface.fontStyle
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

public data class OpenTypeSystemFallbackPolicy(
    val defaultGeneric: String,
    val genericFallbackChains: Map<String, List<String>>,
    val scriptFallbackChains: Map<String, List<String>>,
    val localeFallbackChains: Map<String, List<String>>,
    val emojiPreferredFamilies: List<String>,
) {
    public companion object {
        public val Default: OpenTypeSystemFallbackPolicy = OpenTypeSystemFallbackPolicy(
            defaultGeneric = "sans-serif",
            genericFallbackChains = mapOf(
                "sans-serif" to listOf("Noto Sans", "Liberation Sans", "Arial", "DejaVu Sans"),
                "serif" to listOf("Noto Serif", "Liberation Serif", "Times New Roman", "DejaVu Serif"),
                "monospace" to listOf("Noto Sans Mono", "Liberation Mono", "Courier New", "DejaVu Sans Mono"),
            ),
            scriptFallbackChains = mapOf(
                "han" to listOf("Noto Sans CJK SC", "Noto Sans CJK JP", "Source Han Sans SC"),
                "japanese" to listOf("Noto Sans CJK JP", "Yu Gothic", "Hiragino Sans"),
                "korean" to listOf("Noto Sans CJK KR", "Malgun Gothic"),
                "emoji" to listOf("Noto Color Emoji", "Apple Color Emoji", "Segoe UI Emoji"),
                "latin" to listOf("Liberation Sans", "Arial"),
            ),
            localeFallbackChains = mapOf(
                "zh" to listOf("Noto Sans CJK SC", "Source Han Sans SC"),
                "ja" to listOf("Noto Sans CJK JP", "Yu Gothic"),
                "ko" to listOf("Noto Sans CJK KR", "Malgun Gothic"),
            ),
            emojiPreferredFamilies = listOf("Noto Color Emoji", "Apple Color Emoji", "Segoe UI Emoji"),
        )
    }
}

public fun interface OpenTypeSystemFallbackPolicyProvider {
    public fun prioritizeFamilies(
        availableFamilies: List<String>,
        portableOrder: List<String>,
        character: Int,
        bcp47: Array<String>?,
    ): List<String>
}

public sealed class OpenTypeSystemFontDiagnostic {
    public data class IgnoredFile(
        val path: Path,
        val reason: String,
    ) : OpenTypeSystemFontDiagnostic()

    public data class FallbackPlanned(
        val requestedFamily: String?,
        val character: Int,
        val bcp47: List<String>,
        val orderedFamilies: List<String>,
    ) : OpenTypeSystemFontDiagnostic()
}
