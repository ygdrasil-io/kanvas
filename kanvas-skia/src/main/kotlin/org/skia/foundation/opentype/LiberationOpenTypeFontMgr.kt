package org.skia.foundation.opentype

import org.skia.foundation.SkData
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkFontStyleSet
import org.skia.foundation.SkTypeface
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * Portable pure-Kotlin font manager backed by the bundled Liberation TTFs.
 *
 * This mirrors Skia's test portable font manager shape without routing
 * through AWT or JNI: the twelve Liberation resources are parsed with
 * [OpenTypeTypeface] and exposed as three four-style families.
 */
public class LiberationOpenTypeFontMgr private constructor(
    private val families: List<LiberationFamily>,
) : SkFontMgr() {
    public constructor() : this(loadFamilies())

    private val familiesByName: Map<String, LiberationFamily> =
        families.associateBy { it.name.lowercase() }

    override fun countFamilies(): Int = families.size

    override fun getFamilyName(index: Int): String =
        families.getOrNull(index)?.name
            ?: throw IndexOutOfBoundsException("LiberationOpenTypeFontMgr has ${families.size} families ; index=$index")

    override fun createStyleSet(index: Int): SkFontStyleSet =
        families.getOrNull(index)?.styleSet
            ?: throw IndexOutOfBoundsException("LiberationOpenTypeFontMgr has ${families.size} families ; index=$index")

    override fun matchFamily(familyName: String?): SkFontStyleSet =
        findFamily(familyName)?.styleSet ?: SkFontStyleSet.CreateEmpty()

    override fun matchFamilyStyle(familyName: String?, style: SkFontStyle): SkTypeface? =
        findFamily(familyName)?.styleSet?.matchStyle(style)

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

    override fun makeFromData(data: SkData, ttcIndex: Int): SkTypeface? {
        if (data.size == 0) return null
        return OpenTypeTypeface.MakeFromBytes(data.toByteArray(), ttcIndex)
    }

    override fun makeFromStream(stream: InputStream, ttcIndex: Int): SkTypeface? =
        makeFromData(SkData.MakeWithCopy(stream.readBytes()), ttcIndex)

    override fun makeFromFile(path: String, ttcIndex: Int): SkTypeface? {
        val file = File(path)
        if (!file.isFile) return null
        return try {
            file.inputStream().use { makeFromStream(it, ttcIndex) }
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    override fun legacyMakeTypeface(familyName: String?, style: SkFontStyle): SkTypeface? =
        matchFamilyStyle(familyName, style)

    private fun findFamily(familyName: String?): LiberationFamily? {
        val normalized = familyName?.lowercase() ?: return familiesByName.getValue("liberation sans")
        familiesByName[normalized]?.let { return it }
        return when (normalized) {
            "monospace" -> familiesByName.getValue("liberation mono")
            "sans-serif", "sans serif" -> familiesByName.getValue("liberation sans")
            "serif" -> familiesByName.getValue("liberation serif")
            else -> null
        }
    }

    public companion object {
        private const val RESOURCE_DIR: String = "/fonts/liberation"

        private val FAMILY_SPECS: List<FamilySpec> = listOf(
            FamilySpec("Liberation Sans", "LiberationSans"),
            FamilySpec("Liberation Serif", "LiberationSerif"),
            FamilySpec("Liberation Mono", "LiberationMono"),
        )

        private val STYLE_SPECS: List<StyleSpec> = listOf(
            StyleSpec("Regular", SkFontStyle.Normal(), "Regular"),
            StyleSpec("Bold", SkFontStyle.Bold(), "Bold"),
            StyleSpec("Italic", SkFontStyle.Italic(), "Italic"),
            StyleSpec("Bold Italic", SkFontStyle.BoldItalic(), "BoldItalic"),
        )

        @Suppress("FunctionName")
        public fun Create(): LiberationOpenTypeFontMgr =
            LiberationOpenTypeFontMgr()

        private fun loadFamilies(): List<LiberationFamily> =
            FAMILY_SPECS.map { family ->
                val faces = STYLE_SPECS.map { style ->
                    val resource = "$RESOURCE_DIR/${family.resourcePrefix}-${style.resourceSuffix}.ttf"
                    val stream = LiberationOpenTypeFontMgr::class.java.getResourceAsStream(resource)
                        ?: error("Missing bundled Liberation font resource: $resource")
                    val typeface = stream.use { OpenTypeTypeface.MakeFromBytes(it.readBytes()) }
                        ?: error("Failed to parse bundled Liberation font resource: $resource")
                    LiberationFace(style.styleName, style.style, typeface.withFontStyle(style.style))
                }
                LiberationFamily(family.name, LiberationStyleSet(faces))
            }
    }
}

private data class FamilySpec(val name: String, val resourcePrefix: String)

private data class StyleSpec(
    val styleName: String,
    val style: SkFontStyle,
    val resourceSuffix: String,
)

private data class LiberationFamily(
    val name: String,
    val styleSet: LiberationStyleSet,
)

private data class LiberationFace(
    val styleName: String,
    val style: SkFontStyle,
    val typeface: SkTypeface,
)

private class LiberationStyleSet(
    private val faces: List<LiberationFace>,
) : SkFontStyleSet() {
    override fun count(): Int = faces.size

    override fun getStyle(index: Int, style: SkFontStyle?, name: StringBuilder?): SkFontStyle {
        val face = faces.getOrNull(index)
            ?: throw IndexOutOfBoundsException("LiberationStyleSet has ${faces.size} typefaces ; index=$index")
        name?.append(face.styleName)
        return face.style
    }

    override fun createTypeface(index: Int): SkTypeface? = faces.getOrNull(index)?.typeface

    override fun matchStyle(pattern: SkFontStyle): SkTypeface? = matchStyleCSS3(pattern)
}
