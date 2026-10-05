package org.graphiks.kanvas.font

import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.uuid.Uuid

/**
 * Stable identifier for a physical or virtual font source known to the pure Kotlin font stack.
 *
 * The identifier uses the Kotlin 2.4 standard `Uuid` type so it has value
 * semantics, deterministic text formatting, and no dependency on JVM-only
 * `java.util.UUID`.
 *
 * @property value Opaque UUID that remains stable across parse and cache layers.
 */
@JvmInline
value class FontSourceID(
    val value: Uuid,
)

/**
 * Stable identifier for one parsed typeface within a font source.
 *
 * The identifier uses the Kotlin 2.4 standard `Uuid` type. The UUID must be
 * derived from all facts that influence the resolved face, including source
 * identity, collection index, variation coordinates, palette, and parser
 * generation.
 *
 * @property value Opaque UUID for a single face, including collection index or
 * variation identity when needed.
 */
@JvmInline
value class TypefaceID(
    val value: Uuid,
)

/**
 * Describes where a font source originated before it entered the pure Kotlin font stack.
 */
enum class FontSourceKind {
    /** Font bytes supplied directly by application memory. */
    MEMORY,

    /** Font discovered through the host system font registry. */
    SYSTEM,

    /** Font loaded from a file path or application asset path. */
    FILE,

    /** Font loaded through an application or remote resource URI. */
    RESOURCE,

    /** Font bytes supplied directly by application memory with caller provenance. */
    USER_DATA,

    /** Caller stream copied into bounded immutable font data before parsing. */
    USER_STREAM,

    /** Caller file copied or hashed under an explicit content policy. */
    USER_FILE,

    /** Pure Kotlin scan result from host directories, never normative until bytes are captured. */
    SYSTEM_SCANNED,
}

/**
 * Stable provenance facts used as part of [FontSourceIdentityPreimage].
 *
 * This model deliberately carries display-level source facts, not filesystem
 * paths, native handles, or platform font API objects.
 */
data class FontSourceProvenance(
    val kind: FontSourceKind,
    val declaredName: String,
    val licenseId: String? = null,
    val originPath: String? = null,
    val hostDependent: Boolean = kind.isHostDependentByDefault(),
) {
    init {
        require(declaredName.isNotBlank()) { "declaredName must not be blank." }
        require(originPath == null || originPath.isNotBlank()) {
            "originPath must not be blank when present."
        }
    }

    internal fun toCanonicalJson(indent: String): String = buildString {
        append(indent).append("{\n")
        appendFontJsonField("kind", kind.serializedName, indent = "$indent  ", comma = true)
        appendFontJsonField("declaredName", declaredName, indent = "$indent  ", comma = true)
        append(indent).append("  ").append("\"licenseId\": ")
        append(licenseId.toFontJsonNullableString())
        append(",\n")
        append(indent).append("  ").append("\"originPath\": ")
        append(originPath.toFontJsonNullableString())
        append(",\n")
        appendFontJsonField("hostDependent", hostDependent, indent = "$indent  ", comma = false)
        append(indent).append("}")
    }
}

/**
 * Stable source-level diagnostic included before [FontSourceID] derivation.
 *
 * [FontSourceDiagnostic] is still used after a source ID exists. This value
 * object avoids circular identity derivation by storing only stable source
 * diagnostic facts in the preimage.
 */
data class FontSourceIdentityDiagnostic(
    val code: String,
    val detail: String? = null,
) {
    init {
        require(code.isStableDiagnosticCode()) { "code must be a stable one-line diagnostic code." }
    }

    internal fun toCanonicalJson(indent: String): String = buildString {
        append(indent).append("{\n")
        appendFontJsonField("code", code, indent = "$indent  ", comma = true)
        append(indent).append("  ").append("\"detail\": ")
        append(detail.toFontJsonNullableString())
        append("\n")
        append(indent).append("}")
    }
}

/**
 * Canonical preimage for deriving and auditing [FontSourceID].
 *
 * It includes only deterministic provenance and parser-supplied source facts.
 * The font core does not parse tables here; callers provide face counts,
 * table tags, diagnostics, and parser generation after their owning parser or
 * scanner has produced those facts.
 */
class FontSourceIdentityPreimage(
    val provenance: FontSourceProvenance,
    val contentSha256: String?,
    val byteLength: Long?,
    val faceCount: Int,
    tableTags: List<String>,
    val parserGeneration: Int,
    diagnostics: List<FontSourceIdentityDiagnostic> = emptyList(),
) {
    val tableTags: List<String> = tableTags.normalizedTableTags()
    val diagnostics: List<FontSourceIdentityDiagnostic> =
        diagnostics.sortedWith(compareBy<FontSourceIdentityDiagnostic> { it.code }.thenBy { it.detail.orEmpty() })
    val kind: FontSourceKind
        get() = provenance.kind
    val hostDependent: Boolean
        get() = provenance.hostDependent

    init {
        require((contentSha256 == null) == (byteLength == null)) {
            "contentSha256 and byteLength must both be present or both be absent."
        }
        require(contentSha256 == null || contentSha256.isLowercaseSha256Hex()) {
            "contentSha256 must be a lowercase hexadecimal SHA-256 digest."
        }
        require(byteLength == null || byteLength >= 0) { "byteLength must be non-negative." }
        require(faceCount >= 0) { "faceCount must be non-negative." }
        require(parserGeneration >= 0) { "parserGeneration must be non-negative." }
    }

    /**
     * Serializes every [FontSourceID]-affecting fact as canonical JSON.
     */
    fun toCanonicalJson(): String = buildString {
        append("{\n")
        appendFontJsonField("schema", PreimageSchema, indent = "  ", comma = true)
        append("  \"provenance\": ")
        append(provenance.toCanonicalJson(indent = "  ").trimStart())
        append(",\n")
        append("  \"contentSha256\": ")
        append(contentSha256.toFontJsonNullableString())
        append(",\n")
        append("  \"byteLength\": ")
        append(byteLength?.toString() ?: "null")
        append(",\n")
        appendFontJsonField("faceCount", faceCount, indent = "  ", comma = true)
        append("  \"tableTags\": ")
        append(tableTags.joinToString(prefix = "[", postfix = "]", separator = ", ") { it.evidenceQuoted() })
        append(",\n")
        appendFontJsonField("parserGeneration", parserGeneration, indent = "  ", comma = true)
        append("  \"diagnostics\": ")
        if (diagnostics.isEmpty()) {
            append("[]\n")
        } else {
            append("[\n")
            append(diagnostics.joinToString(",\n") { it.toCanonicalJson(indent = "    ") })
            append("\n  ]\n")
        }
        append("}\n")
    }

    /**
     * Derives a deterministic UUID-backed source ID from [toCanonicalJson].
     */
    fun deriveFontSourceID(): FontSourceID =
        FontSourceID(stableUuidFromSha256("kanvas-font-source-id-v1\n${toCanonicalJson()}"))

    fun sourceId(): FontSourceID = deriveFontSourceID()

    override fun equals(other: Any?): Boolean =
        this === other || other is FontSourceIdentityPreimage &&
            provenance == other.provenance &&
            contentSha256 == other.contentSha256 &&
            byteLength == other.byteLength &&
            faceCount == other.faceCount &&
            tableTags == other.tableTags &&
            parserGeneration == other.parserGeneration &&
            diagnostics == other.diagnostics

    override fun hashCode(): Int {
        var result = provenance.hashCode()
        result = 31 * result + (contentSha256?.hashCode() ?: 0)
        result = 31 * result + (byteLength?.hashCode() ?: 0)
        result = 31 * result + faceCount
        result = 31 * result + tableTags.hashCode()
        result = 31 * result + parserGeneration
        result = 31 * result + diagnostics.hashCode()
        return result
    }

    companion object {
        const val PreimageSchema: String = "org.graphiks.kanvas.font.FontSourceIdentityPreimage.v1"

        /**
         * Creates an identity preimage for sources whose bytes were captured.
         */
        fun fromCapturedBytes(
            kind: FontSourceKind,
            declaredName: String,
            licenseId: String?,
            bytes: ByteArray,
            faceCount: Int,
            tableTags: List<String> = emptyList(),
            parserGeneration: Int,
            diagnostics: List<FontSourceIdentityDiagnostic> = emptyList(),
            hostDependent: Boolean = kind.isHostDependentByDefault(),
            originPath: String? = null,
        ): FontSourceIdentityPreimage =
            FontSourceIdentityPreimage(
                provenance = FontSourceProvenance(
                    kind = kind,
                    declaredName = declaredName,
                    licenseId = licenseId,
                    originPath = originPath,
                    hostDependent = hostDependent,
                ),
                contentSha256 = bytes.sha256Hex(),
                byteLength = bytes.size.toLong(),
                faceCount = faceCount,
                tableTags = tableTags,
                parserGeneration = parserGeneration,
                diagnostics = diagnostics,
            )

        /**
         * Creates an identity preimage for a host-dependent system scan.
         */
        fun systemScanned(
            declaredName: String,
            faceCount: Int,
            tableTags: List<String> = emptyList(),
            parserGeneration: Int,
            diagnostics: List<FontSourceIdentityDiagnostic> = emptyList(),
        ): FontSourceIdentityPreimage =
            FontSourceIdentityPreimage(
                provenance = FontSourceProvenance(
                    kind = FontSourceKind.SYSTEM_SCANNED,
                    declaredName = declaredName,
                    licenseId = null,
                    originPath = null,
                    hostDependent = true,
                ),
                contentSha256 = null,
                byteLength = null,
                faceCount = faceCount,
                tableTags = tableTags,
                parserGeneration = parserGeneration,
                diagnostics = diagnostics,
            )
    }
}

enum class TypefaceOutlineFormat(
    val serializedName: String,
) {
    TRUE_TYPE_GLYF("TrueTypeGlyf"),
    CFF("CFF"),
    CFF2("CFF2"),
    BITMAP_ONLY("BitmapOnly"),
    UNKNOWN("Unknown"),
}

/**
 * Stable scaler route facts included in [TypefaceIdentityPreimage].
 */
enum class TypefaceScalerMode(
    val serializedName: String,
) {
    OUTLINE("Outline"),
    A8("A8"),
    SDF("SDF"),
    DRIFT_ONLY("DriftOnly"),
}

/**
 * Canonical facts for the selected character map used by a typeface.
 */
data class TypefaceCMapSelection(
    val platformId: Int,
    val encodingId: Int,
    val format: Int,
    val language: Int,
    val unicode: Boolean,
) {
    init {
        require(platformId >= 0) { "platformId must be non-negative." }
        require(encodingId >= 0) { "encodingId must be non-negative." }
        require(format >= 0) { "format must be non-negative." }
        require(language >= 0) { "language must be non-negative." }
    }

    internal fun toCanonicalJson(indent: String): String = buildString {
        append(indent).append("{\n")
        appendFontJsonField("platformId", platformId, indent = "$indent  ", comma = true)
        appendFontJsonField("encodingId", encodingId, indent = "$indent  ", comma = true)
        appendFontJsonField("format", format, indent = "$indent  ", comma = true)
        appendFontJsonField("language", language, indent = "$indent  ", comma = true)
        appendFontJsonField("unicode", unicode, indent = "$indent  ", comma = false)
        append(indent).append("}")
    }
}

/**
 * One normalized OpenType variation coordinate.
 */
class TypefaceVariationCoordinate(
    val axisTag: String,
    value: Double,
) {
    val value: Double = value.normalizedTypefaceVariationValue()

    init {
        require(axisTag.isStableSfntTableTag()) {
            "axisTag must be a four-character printable ASCII OpenType tag."
        }
        require(!value.isNaN() && !value.isInfinite()) {
            "variation coordinate value must be finite."
        }
    }

    internal fun toCanonicalJson(indent: String): String = buildString {
        append(indent).append("{\n")
        appendFontJsonField("axisTag", axisTag, indent = "$indent  ", comma = true)
        append(indent).append("  ").append("value".evidenceQuoted()).append(": ")
        append(value.toTypefaceJsonNumber())
        append("\n")
        append(indent).append("}")
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is TypefaceVariationCoordinate &&
            axisTag == other.axisTag &&
            value == other.value

    override fun hashCode(): Int {
        var result = axisTag.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }

override fun toString(): String =
        "TypefaceVariationCoordinate(axisTag=$axisTag, value=$value)"
}

/**
 * One named variable-font instance advertised by a typeface for fallback selection.
 */
class TypefaceNamedInstance(
    val name: String,
    coordinates: List<TypefaceVariationCoordinate>,
) {
    val coordinates: List<TypefaceVariationCoordinate> = coordinates.normalizedVariationCoordinates()

    init {
        require(name.isNotBlank()) { "named instance name must not be blank." }
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is TypefaceNamedInstance &&
            name == other.name &&
            coordinates == other.coordinates

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + coordinates.hashCode()
        return result
    }

    override fun toString(): String =
        "TypefaceNamedInstance(name=$name, coordinates=$coordinates)"
}

/**
 * One variable-font axis advertised by a typeface for fallback selection.
 */
class TypefaceVariationAxisRange(
    val axisTag: String,
    val minimum: Double,
    val defaultValue: Double,
    val maximum: Double,
) {
    init {
        require(axisTag.isStableSfntTableTag()) {
            "variation axis tag must be a four-character printable ASCII OpenType tag."
        }
        require(minimum.isFinite()) { "variation axis minimum must be finite." }
        require(defaultValue.isFinite()) { "variation axis default value must be finite." }
        require(maximum.isFinite()) { "variation axis maximum must be finite." }
        require(minimum <= defaultValue) {
            "variation axis default value must be greater than or equal to the minimum."
        }
        require(defaultValue <= maximum) {
            "variation axis default value must be less than or equal to the maximum."
        }
    }

    fun clamp(requestedValue: Double): Double =
        requestedValue.coerceIn(minimum, maximum).normalizedTypefaceVariationValue()
}

/**
 * Palette selection facts that can affect color glyph output.
 */
class TypefacePaletteSelection(
    val index: Int,
    overrides: List<String> = emptyList(),
) {
    val overrides: List<String> = overrides.distinct().sorted()

    init {
        require(index >= 0) { "palette index must be non-negative." }
        require(this.overrides.all { override -> override.isNotBlank() && override.none { it < ' ' } }) {
            "palette overrides must be stable one-line strings."
        }
    }

    internal fun toCanonicalJson(indent: String): String = buildString {
        append(indent).append("{\n")
        appendFontJsonField("index", index, indent = "$indent  ", comma = true)
        append(indent).append("  ").append("overrides".evidenceQuoted()).append(": ")
        append(overrides.joinToString(prefix = "[", postfix = "]", separator = ", ") { it.evidenceQuoted() })
        append("\n")
        append(indent).append("}")
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is TypefacePaletteSelection &&
            index == other.index &&
            overrides == other.overrides

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + overrides.hashCode()
        return result
    }

    override fun toString(): String =
        "TypefacePaletteSelection(index=$index, overrides=$overrides)"
}

/**
 * Stable diagnostic included in typeface identity evidence.
 */
data class TypefaceIdentityDiagnostic(
    val code: String,
    val detail: String? = null,
) {
    init {
        require(code.isStableDiagnosticCode()) { "code must be a stable one-line diagnostic code." }
    }

    internal fun toCanonicalJson(indent: String): String = buildString {
        append(indent).append("{\n")
        appendFontJsonField("code", code, indent = "$indent  ", comma = true)
        append(indent).append("  ").append("detail".evidenceQuoted()).append(": ")
        append(detail.toFontJsonNullableString())
        append("\n")
        append(indent).append("}")
    }
}

/**
 * Canonical preimage for deriving and auditing [TypefaceID].
 */
class TypefaceIdentityPreimage(
    val sourceId: FontSourceID,
    val collectionIndex: Int,
    val postScriptName: String?,
    val familyName: String,
    val styleName: String,
    val style: FontStyle,
    val outlineFormat: TypefaceOutlineFormat,
    val selectedCMap: TypefaceCMapSelection,
    val scalerMode: TypefaceScalerMode,
    variationCoordinates: List<TypefaceVariationCoordinate> = emptyList(),
    val palette: TypefacePaletteSelection? = null,
    val fallbackCatalogGeneration: Int? = null,
    tableTags: List<String> = emptyList(),
    diagnostics: List<TypefaceIdentityDiagnostic> = emptyList(),
) {
    val variationCoordinates: List<TypefaceVariationCoordinate> = variationCoordinates.normalizedVariationCoordinates()
    val tableTags: List<String> = tableTags.normalizedTableTags()
    val diagnostics: List<TypefaceIdentityDiagnostic> =
        diagnostics.sortedWith(compareBy<TypefaceIdentityDiagnostic> { it.code }.thenBy { it.detail.orEmpty() })

    init {
        require(collectionIndex >= 0) { "collectionIndex must be non-negative." }
        require(postScriptName == null || postScriptName.isNotBlank()) {
            "postScriptName must not be blank when present."
        }
        require(familyName.isNotBlank()) { "familyName must not be blank." }
        require(styleName.isNotBlank()) { "styleName must not be blank." }
        require(selectedCMap.unicode) { "selectedCMap must be a usable Unicode cmap." }
        require(fallbackCatalogGeneration == null || fallbackCatalogGeneration >= 0) {
            "fallbackCatalogGeneration must be non-negative when present."
        }
    }

    /**
     * Serializes every [TypefaceID]-affecting fact as canonical JSON.
     */
    fun toCanonicalJson(): String = buildString {
        append("{\n")
        appendFontJsonField("schema", PreimageSchema, indent = "  ", comma = true)
        appendFontJsonField("sourceId", sourceId.value.toHexDashString(), indent = "  ", comma = true)
        appendFontJsonField("collectionIndex", collectionIndex, indent = "  ", comma = true)
        append("  ").append("postScriptName".evidenceQuoted()).append(": ")
        append(postScriptName.toFontJsonNullableString())
        append(",\n")
        appendFontJsonField("familyName", familyName, indent = "  ", comma = true)
        appendFontJsonField("styleName", styleName, indent = "  ", comma = true)
        append("  ").append("style".evidenceQuoted()).append(": {\n")
        appendFontJsonField("weight", style.weight, indent = "    ", comma = true)
        appendFontJsonField("width", style.width, indent = "    ", comma = true)
        appendFontJsonField("slant", style.slant.typefaceSerializedName, indent = "    ", comma = false)
        append("  },\n")
        appendFontJsonField("outlineFormat", outlineFormat.serializedName, indent = "  ", comma = true)
        append("  ").append("selectedCMap".evidenceQuoted()).append(": ")
        append(selectedCMap.toCanonicalJson(indent = "  ").trimStart())
        append(",\n")
        appendFontJsonField("scalerMode", scalerMode.serializedName, indent = "  ", comma = true)
        append("  ").append("variationCoordinates".evidenceQuoted()).append(": ")
        if (variationCoordinates.isEmpty()) {
            append("[]")
        } else {
            append("[\n")
            append(variationCoordinates.joinToString(",\n") { it.toCanonicalJson(indent = "    ") })
            append("\n  ]")
        }
        append(",\n")
        append("  ").append("palette".evidenceQuoted()).append(": ")
        append(palette?.toCanonicalJson(indent = "  ")?.trimStart() ?: "null")
        append(",\n")
        append("  ").append("fallbackCatalogGeneration".evidenceQuoted()).append(": ")
        append(fallbackCatalogGeneration?.toString() ?: "null")
        append(",\n")
        append("  ").append("tableTags".evidenceQuoted()).append(": ")
        append(tableTags.joinToString(prefix = "[", postfix = "]", separator = ", ") { it.evidenceQuoted() })
        append(",\n")
        append("  ").append("diagnostics".evidenceQuoted()).append(": ")
        if (diagnostics.isEmpty()) {
            append("[]\n")
        } else {
            append("[\n")
            append(diagnostics.joinToString(",\n") { it.toCanonicalJson(indent = "    ") })
            append("\n  ]\n")
        }
        append("}\n")
    }

    /**
     * Derives a deterministic UUID-backed typeface ID from [toCanonicalJson].
     */
    fun deriveTypefaceID(): TypefaceID =
        TypefaceID(stableUuidFromSha256("kanvas-typeface-id-v1\n${toCanonicalJson()}"))

    fun typefaceId(): TypefaceID = deriveTypefaceID()

    override fun equals(other: Any?): Boolean =
        this === other || other is TypefaceIdentityPreimage &&
            sourceId == other.sourceId &&
            collectionIndex == other.collectionIndex &&
            postScriptName == other.postScriptName &&
            familyName == other.familyName &&
            styleName == other.styleName &&
            style == other.style &&
            outlineFormat == other.outlineFormat &&
            selectedCMap == other.selectedCMap &&
            scalerMode == other.scalerMode &&
            variationCoordinates == other.variationCoordinates &&
            palette == other.palette &&
            fallbackCatalogGeneration == other.fallbackCatalogGeneration &&
            tableTags == other.tableTags &&
            diagnostics == other.diagnostics

    override fun hashCode(): Int {
        var result = sourceId.hashCode()
        result = 31 * result + collectionIndex
        result = 31 * result + (postScriptName?.hashCode() ?: 0)
        result = 31 * result + familyName.hashCode()
        result = 31 * result + styleName.hashCode()
        result = 31 * result + style.hashCode()
        result = 31 * result + outlineFormat.hashCode()
        result = 31 * result + selectedCMap.hashCode()
        result = 31 * result + scalerMode.hashCode()
        result = 31 * result + variationCoordinates.hashCode()
        result = 31 * result + (palette?.hashCode() ?: 0)
        result = 31 * result + (fallbackCatalogGeneration ?: 0)
        result = 31 * result + tableTags.hashCode()
        result = 31 * result + diagnostics.hashCode()
        return result
    }

    companion object {
        const val PreimageSchema: String = "org.graphiks.kanvas.font.TypefaceIdentityPreimage.v1"
    }
}

fun typefaceIdentityPreimage(
    sourceId: FontSourceID,
    collectionIndex: Int,
    postScriptName: String?,
    familyName: String,
    styleName: String,
    style: FontStyle = FontStyle.fromStyleName(styleName),
    outlineFormat: TypefaceOutlineFormat,
    selectedCMap: TypefaceCMapSelection,
    scalerMode: TypefaceScalerMode,
    variationCoordinates: List<TypefaceVariationCoordinate> = emptyList(),
    palette: TypefacePaletteSelection? = null,
    fallbackCatalogGeneration: Int? = null,
    tableTags: List<String> = emptyList(),
    diagnostics: List<TypefaceIdentityDiagnostic> = emptyList(),
): TypefaceIdentityPreimage = TypefaceIdentityPreimage(
    sourceId = sourceId,
    collectionIndex = collectionIndex,
    postScriptName = postScriptName,
    familyName = familyName,
    styleName = styleName,
    style = style,
    outlineFormat = outlineFormat,
    selectedCMap = selectedCMap,
    scalerMode = scalerMode,
    variationCoordinates = variationCoordinates,
    palette = palette,
    fallbackCatalogGeneration = fallbackCatalogGeneration,
    tableTags = tableTags,
    diagnostics = diagnostics,
)

/**
 * Slant axis used by portable font style matching.
 */
enum class FontSlant {
    /** Upright roman style. */
    UPRIGHT,

    /** Italic style selected by authored italic faces. */
    ITALIC,

    /** Oblique style selected by slanted synthetic or authored faces. */
    OBLIQUE,
}

/**
 * Portable style request and face metadata used by font fallback.
 *
 * @property weight CSS/OpenType-like weight in the inclusive `1..1000` range.
 * @property width CSS/OpenType-like width class in the inclusive `1..9` range,
 * where `5` is normal width.
 * @property slant Upright, italic, or oblique style.
 */
data class FontStyle(
    val weight: Int = 400,
    val width: Int = 5,
    val slant: FontSlant = FontSlant.UPRIGHT,
) {
    init {
        require(weight in 1..1000) { "weight must be in 1..1000." }
        require(width in 1..9) { "width must be in 1..9." }
    }

    companion object {
        /**
         * Derives a conservative style from a font style name.
         *
         * @param styleName Name such as `Regular`, `Bold Italic`, or `Condensed`.
         * @return Parsed style metadata suitable for deterministic fallback matching.
         */
        fun fromStyleName(styleName: String): FontStyle {
            val key = styleName.lowercase()
            val weight = when {
                "thin" in key -> 100
                "extralight" in key || "extra light" in key || "ultralight" in key || "ultra light" in key -> 200
                "light" in key -> 300
                "medium" in key -> 500
                "semibold" in key || "semi bold" in key || "demibold" in key || "demi bold" in key -> 600
                "extrabold" in key || "extra bold" in key || "ultrabold" in key || "ultra bold" in key -> 800
                "black" in key || "heavy" in key -> 900
                "bold" in key -> 700
                else -> 400
            }
            val width = when {
                "ultracondensed" in key || "ultra condensed" in key -> 1
                "extracondensed" in key || "extra condensed" in key -> 2
                "condensed" in key -> 3
                "semicondensed" in key || "semi condensed" in key -> 4
                "semiexpanded" in key || "semi expanded" in key -> 6
                "extraexpanded" in key || "extra expanded" in key -> 8
                "ultraexpanded" in key || "ultra expanded" in key -> 9
                "expanded" in key -> 7
                else -> 5
            }
            val slant = when {
                "italic" in key -> FontSlant.ITALIC
                "oblique" in key -> FontSlant.OBLIQUE
                else -> FontSlant.UPRIGHT
            }
            return FontStyle(weight = weight, width = width, slant = slant)
        }
    }
}

/**
 * Raw font input plus provenance information before SFNT parsing or scaler selection.
 *
 * @property id Stable identifier used by diagnostics, caches, and downstream parsed faces.
 * @property kind Origin category for policy decisions such as sandboxing or system fallback.
 * @property displayName Human-readable name for logs, diagnostics, and tooling.
 * @property bytes Raw font bytes in their original container format.
 */
data class FontSource(
    val id: FontSourceID,
    val kind: FontSourceKind,
    val displayName: String,
    val bytes: ByteArray,
) {
    /**
     * Compares font sources by metadata and byte content instead of JVM array identity.
     *
     * @param other Candidate object to compare with this source.
     * @return True when all metadata fields and raw bytes match.
     */
    override fun equals(other: Any?): Boolean =
        this === other || other is FontSource &&
            id == other.id &&
            kind == other.kind &&
            displayName == other.displayName &&
            bytes.contentEquals(other.bytes)

    /**
     * Produces a hash code that includes the raw byte content.
     *
     * @return Hash value suitable for collections keyed by font source.
     */
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

/**
 * Non-fatal diagnostic reported while loading, parsing, resolving, or preparing a font source.
 *
 * @property sourceId Source that produced the diagnostic.
 * @property message Human-readable diagnostic text.
 * @property causeCode Optional stable machine-readable cause code.
 * @property causeMessage Optional dumpable cause detail without retaining a
 * platform exception object.
 */
data class FontSourceDiagnostic(
    val sourceId: FontSourceID,
    val message: String,
    val causeCode: String? = null,
    val causeMessage: String? = null,
) {
    init {
        require(causeCode == null || causeCode.isStableDiagnosticCode()) {
            "causeCode must be a stable one-line diagnostic code."
        }
    }

    /**
     * Serializes the diagnostic as one stable line for route and provenance evidence.
     *
     * @return Dumpable diagnostic text without retaining platform exception identity.
     */
    fun dump(): String = buildString {
        append(causeCode ?: "font.source.diagnostic")
        append(" sourceId=")
        append(sourceId.value.toHexDashString())
        append(" message=")
        append(message.evidenceQuoted())
        causeMessage?.let {
            append(" causeMessage=")
            append(it.evidenceQuoted())
        }
    }
}

/**
 * Deterministic provenance and parser-supplied evidence for one font source.
 *
 * `font/core` does not parse SFNT/OpenType data here. Callers provide face
 * counts, table tags, and diagnostics after bounded parsing or explicit
 * refusal; this value object only makes those facts stable and dumpable.
 *
 * @property sourceId Stable identity of the source.
 * @property kind Provenance classification for the source.
 * @property displayName Human-readable source name for diagnostics.
 * @property contentSha256 SHA-256 digest of captured source bytes.
 * @property hostDependent True when the source came from host system scanning.
 * @property faceCount Number of faces reported by the caller.
 * @property tableTags Supported SFNT/OpenType table tags, sorted and deduplicated.
 * @property diagnostics Stable diagnostics associated with the source.
 */
data class FontSourceEvidence(
    val sourceId: FontSourceID,
    val kind: FontSourceKind,
    val displayName: String,
    val contentSha256: String,
    val hostDependent: Boolean,
    val faceCount: Int,
    val tableTags: List<String> = emptyList(),
    val diagnostics: List<FontSourceDiagnostic> = emptyList(),
) {
    init {
        require(faceCount >= 0) { "faceCount must be non-negative." }
        require(contentSha256.isLowercaseSha256Hex()) {
            "contentSha256 must be a lowercase hexadecimal SHA-256 digest."
        }
        require(tableTags == tableTags.normalizedTableTags()) {
            "tableTags must be four-character printable ASCII SFNT tags, sorted and deduplicated."
        }
    }

    /**
     * Serializes provenance and parser-supplied facts as one stable line.
     *
     * @return Deterministic evidence suitable for tests and PM dumps.
     */
    fun dump(): String = buildString {
        append("sourceId=")
        append(sourceId.value.toHexDashString())
        append(" kind=")
        append(kind.name)
        append(" displayName=")
        append(displayName.evidenceQuoted())
        append(" contentSha256=")
        append(contentSha256)
        append(" hostDependent=")
        append(hostDependent)
        append(" faceCount=")
        append(faceCount)
        append(" tableTags=")
        append(tableTags.joinToString(prefix = "[", postfix = "]", separator = ","))
        append(" diagnostics=")
        append(diagnostics.joinToString(prefix = "[", postfix = "]", separator = ";") { it.dump() })
    }

    companion object {
        /**
         * Builds evidence from captured source bytes and caller-provided parser facts.
         *
         * @param source Font source with captured bytes.
         * @param faceCount Number of faces reported by parsing or explicit refusal.
         * @param tableTags Table tags reported by parsing.
         * @param diagnostics Source diagnostics to include in the dump.
         * @return Normalized source evidence.
         */
        fun fromSource(
            source: FontSource,
            faceCount: Int,
            tableTags: List<String> = emptyList(),
            diagnostics: List<FontSourceDiagnostic> = emptyList(),
        ): FontSourceEvidence =
            FontSourceEvidence(
                sourceId = source.id,
                kind = source.kind,
                displayName = source.displayName,
                contentSha256 = source.bytes.sha256Hex(),
                hostDependent = source.kind.isHostDependentByDefault(),
                faceCount = faceCount,
                tableTags = tableTags.normalizedTableTags(),
                diagnostics = diagnostics,
            )
    }
}

/**
 * Builds deterministic provenance evidence for this captured font source.
 *
 * @param faceCount Number of faces reported by parsing or explicit refusal.
 * @param tableTags Supported table tags reported by parsing.
 * @param diagnostics Source diagnostics to include in the dump.
 * @return Normalized source evidence without scanning paths or parsing font bytes.
 */
fun FontSource.provenanceEvidence(
    faceCount: Int,
    tableTags: List<String> = emptyList(),
    diagnostics: List<FontSourceDiagnostic> = emptyList(),
): FontSourceEvidence =
    FontSourceEvidence.fromSource(
        source = this,
        faceCount = faceCount,
        tableTags = tableTags,
        diagnostics = diagnostics,
    )

/**
 * Parsed typeface metadata shared by resolvers, fallback catalogs, and scaler implementations.
 *
 * @property id Stable identifier for this parsed face.
 * @property source Raw source that produced the typeface.
 * @property familyName Preferred family name exposed to font matching.
 * @property styleName Preferred style name such as Regular, Italic, or Bold.
 * @property style Portable style metadata used when multiple faces share one family.
 * @property diagnostics Non-fatal diagnostics collected while preparing the typeface.
 */
data class TypefaceData(
    val id: TypefaceID,
    val source: FontSource,
    val familyName: String,
    val styleName: String,
    val style: FontStyle = FontStyle.fromStyleName(styleName),
    val variationAxes: List<TypefaceVariationAxisRange> = emptyList(),
    val variationCoordinates: List<TypefaceVariationCoordinate> = emptyList(),
    val namedInstances: List<TypefaceNamedInstance> = emptyList(),
    val variationMetricsSupported: Boolean = true,
    val identityPreimage: TypefaceIdentityPreimage? = null,
    val diagnostics: List<FontSourceDiagnostic> = emptyList(),
) {
    init {
        require(variationAxes.map { axis -> axis.axisTag }.distinct().size == variationAxes.size) {
            "variationAxes must use unique axis tags."
        }
        require(
            variationAxes.map { axis -> axis.axisTag } ==
                variationAxes.map { axis -> axis.axisTag }.sorted(),
        ) {
            "variationAxes must be sorted by axis tag."
        }
        require(variationMetricsSupported || variationAxes.isNotEmpty() || variationCoordinates.isEmpty()) {
            "variationMetricsSupported may be false only for variable faces."
        }
        require(namedInstances.map { instance -> instance.name }.distinct().size == namedInstances.size) {
            "namedInstances must use unique names."
        }
        require(namedInstances.map { instance -> instance.name } == namedInstances.map { instance -> instance.name }.sorted()) {
            "namedInstances must be sorted by name."
        }
        val variationAxisTags = variationAxes.map { axis -> axis.axisTag }.toSet()
        require(namedInstances.all { instance -> instance.coordinates.all { coordinate -> coordinate.axisTag in variationAxisTags } }) {
            "namedInstances coordinates must reference declared variationAxes."
        }
        if (identityPreimage != null) {
            require(identityPreimage.variationCoordinates == variationCoordinates.normalizedVariationCoordinates()) {
                "identityPreimage variationCoordinates must match TypefaceData variationCoordinates."
            }
        }
    }
}

/**
 * Public face entry used by font collections and fallback runs.
 *
 * @property typeface Parsed typeface metadata for the face.
 */
data class FontFace(
    val typeface: TypefaceData,
)

/**
 * Immutable group of font faces available to a resolver.
 *
 * @property faces Ordered faces in matching priority order.
 */
data class FontCollection(
    val faces: List<FontFace> = emptyList(),
)

/**
 * Diagnostic emitted while scanning explicit font roots.
 *
 * The scanner records non-fatal failures instead of throwing so callers can
 * surface missing roots, unreadable directories, or unreadable candidate files
 * in deterministic logs.
 *
 * @property code Stable machine-readable diagnostic code.
 * @property root Root that was being scanned when the diagnostic was emitted.
 * @property path Optional child path related to the failure.
 * @property message Human-readable detail suitable for dumps and test evidence.
 */
data class FontFileScanDiagnostic(
    val code: String,
    val root: Path,
    val path: Path? = null,
    val message: String = "",
) {
    /**
     * Serializes the diagnostic as one stable line.
     *
     * @return Deterministic diagnostic text for reports and assertions.
     */
    fun dump(): String = buildString {
        append(code)
        append(" root=")
        append(root.toAbsolutePath().normalize())
        path?.let {
            append(" path=")
            append(it.toAbsolutePath().normalize())
        }
        if (message.isNotBlank()) {
            append(" message=")
            append(message.evidenceQuoted())
        }
    }
}

/**
 * Result of scanning explicit filesystem roots for font files.
 *
 * @property files Supported font files, resolved to real paths and sorted
 * deterministically by their textual path.
 * @property diagnostics Non-fatal scan diagnostics in discovery order.
 */
data class FontFileScanResult(
    val files: List<Path>,
    val diagnostics: List<FontFileScanDiagnostic> = emptyList(),
) {
    /**
     * Serializes all diagnostics as newline-separated stable lines.
     *
     * @return Empty string when there are no diagnostics.
     */
    fun dumpDiagnostics(): String = diagnostics.joinToString(separator = "\n") { it.dump() }
}

/**
 * Pure Kotlin/JVM scanner for font files under caller-provided roots.
 *
 * The scanner never reads platform system font directories on its own. It only
 * walks roots passed to [scanRoots], accepts `.ttf`, `.otf`, `.ttc`, and `.otc`
 * files case-insensitively, and reports non-fatal root/file failures through
 * [FontFileScanDiagnostic].
 */
object FontFileScanner {
    /**
     * Supported font container extensions, lower-cased and without leading dots.
     */
    val supportedExtensions: Set<String> = setOf("ttf", "otf", "ttc", "otc")

    /**
     * Scans explicit roots for supported font files.
     *
     * @param roots Directories to scan recursively.
     * @param reportSkippedFiles True to emit diagnostics for regular files
     * under the explicit roots that are skipped because they do not use a
     * supported font container extension.
     * @return Deterministically ordered files plus non-fatal diagnostics.
     */
    fun scanRoots(
        roots: List<Path>,
        reportSkippedFiles: Boolean = false,
    ): FontFileScanResult {
        val files = linkedSetOf<Path>()
        val diagnostics = mutableListOf<FontFileScanDiagnostic>()

        for (root in roots) {
            when {
                !Files.exists(root) -> {
                    diagnostics += rootDiagnostic("font.scan.root-missing", root, "Root does not exist.")
                    continue
                }
                !Files.isDirectory(root) -> {
                    diagnostics += rootDiagnostic("font.scan.root-not-directory", root, "Root is not a directory.")
                    continue
                }
            }

            try {
                Files.walk(root).use { stream ->
                    val iterator = stream.iterator()
                    while (iterator.hasNext()) {
                        val candidate = iterator.next()
                        if (!candidate.isSupportedFontFile()) {
                            if (reportSkippedFiles && candidate.isSkippedRegularFile()) {
                                diagnostics += fileDiagnostic(
                                    code = "font.scan.file-skipped",
                                    root = root,
                                    path = candidate,
                                    message = "Unsupported font file extension.",
                                )
                            }
                            continue
                        }
                        val realPath = try {
                            candidate.toRealPath()
                        } catch (e: IOException) {
                            diagnostics += fileDiagnostic(
                                code = "font.scan.file-unreadable",
                                root = root,
                                path = candidate,
                                message = e.message ?: "Font file path could not be resolved.",
                            )
                            continue
                        } catch (e: SecurityException) {
                            diagnostics += fileDiagnostic(
                                code = "font.scan.file-unreadable",
                                root = root,
                                path = candidate,
                                message = e.message ?: "Font file path is not readable.",
                            )
                            continue
                        }
                        files.add(realPath)
                    }
                }
            } catch (e: IOException) {
                diagnostics += rootDiagnostic(
                    code = "font.scan.root-unreadable",
                    root = root,
                    message = e.message ?: "Root could not be scanned.",
                )
            } catch (e: UncheckedIOException) {
                diagnostics += rootDiagnostic(
                    code = "font.scan.root-unreadable",
                    root = root,
                    message = e.message ?: "Root could not be scanned.",
                )
            } catch (e: SecurityException) {
                diagnostics += rootDiagnostic(
                    code = "font.scan.root-unreadable",
                    root = root,
                    message = e.message ?: "Root is not readable.",
                )
            }
        }

        return FontFileScanResult(
            files = files.sortedBy { it.toString() },
            diagnostics = diagnostics,
        )
    }

    private fun Path.isSupportedFontFile(): Boolean {
        val supported = try {
            isRegularFile() && extension.lowercase() in supportedExtensions
        } catch (e: SecurityException) {
            false
        }
        return supported
    }

    private fun Path.isSkippedRegularFile(): Boolean {
        val skipped = try {
            isRegularFile() && extension.lowercase() !in supportedExtensions
        } catch (e: SecurityException) {
            false
        }
        return skipped
    }

    private fun rootDiagnostic(
        code: String,
        root: Path,
        message: String,
    ): FontFileScanDiagnostic =
        FontFileScanDiagnostic(
            code = code,
            root = root,
            message = message,
        )

    private fun fileDiagnostic(
        code: String,
        root: Path,
        path: Path,
        message: String,
    ): FontFileScanDiagnostic =
        FontFileScanDiagnostic(
            code = code,
            root = root,
            path = path,
            message = message,
        )
}

/**
 * Catalog entry for one discovered font file.
 *
 * This does not parse SFNT/OpenType tables. It records deterministic file-level
 * metadata that can feed later parsing, family indexing, or fallback tooling.
 *
 * @property path Real filesystem path for the font file.
 * @property displayName File name suitable for diagnostics and tooling.
 * @property extension Lower-cased supported extension without a leading dot.
 * @property sourceKind Provenance used when converting the entry into a
 * [FontSource].
 */
data class FontFileCatalogEntry(
    val path: Path,
    val displayName: String,
    val extension: String,
    val sourceKind: FontSourceKind = FontSourceKind.FILE,
) {
    companion object {
        /**
         * Creates a catalog entry from a discovered path.
         *
         * @param path Font file path, normally from [FontFileScanResult.files].
         * @return File-level catalog entry with normalized extension metadata.
         */
        fun fromPath(path: Path): FontFileCatalogEntry =
            FontFileCatalogEntry(
                path = path,
                displayName = path.fileName?.toString() ?: path.toString(),
                extension = path.extension.lowercase(),
            )
    }
}

/**
 * Deterministic catalog of font files discovered from explicit roots.
 *
 * @property entries File-level entries in scan order.
 * @property diagnostics Non-fatal diagnostics from the scan that produced the
 * catalog.
 */
data class FontFileCatalog(
    val entries: List<FontFileCatalogEntry>,
    val diagnostics: List<FontFileScanDiagnostic> = emptyList(),
) {
    /**
     * Serializes all scan diagnostics as newline-separated stable lines.
     *
     * @return Empty string when there are no diagnostics.
     */
    fun dumpDiagnostics(): String = diagnostics.joinToString(separator = "\n") { it.dump() }

    companion object {
        /**
         * Scans explicit roots and catalogs supported font files.
         *
         * @param roots Directories to scan recursively.
         * @return File catalog with scan diagnostics.
         */
        fun scanRoots(roots: List<Path>): FontFileCatalog =
            fromScan(FontFileScanner.scanRoots(roots))

        /**
         * Builds a catalog from an existing scan result.
         *
         * @param scan Scan result to convert.
         * @return File catalog preserving scan file order and diagnostics.
         */
        fun fromScan(scan: FontFileScanResult): FontFileCatalog =
            FontFileCatalog(
                entries = scan.files.map(FontFileCatalogEntry::fromPath),
                diagnostics = scan.diagnostics,
            )
    }
}

/**
 * Resolves text and requested family information to concrete font runs.
 */
interface FontResolver {
    /**
     * Resolves a fallback request into ordered font runs.
     *
     * @param request Text, locale, and family preferences to resolve.
     * @return Runs that cover the request text with concrete font faces.
     */
    fun resolve(request: FallbackRequest): List<ResolvedFontRun>
}

/**
 * Answers whether a parsed typeface can draw a Unicode code point.
 *
 * `font/core` does not parse cmap, glyph coverage, OpenType, SFNT, Skia, GPU,
 * or platform font data directly. Callers provide this boundary so fallback
 * resolution can assign faces while real coverage evidence stays in the parser,
 * host registry, or higher-level font stack that owns those facts.
 */
fun interface FontCoverageProvider {
    /**
     * Reports coverage for one typeface/code point pair.
     *
     * @param typefaceId Typeface being considered for the requested code point.
     * @param codePoint Unicode scalar value from the request text.
     * @return True when the typeface is known to support the code point; false
     * when support is absent or unknown.
     */
    fun supports(typefaceId: TypefaceID, codePoint: Int): Boolean
}

/**
 * Concrete catalog-backed resolver for portable font fallback assignments.
 *
 * The resolver walks [FallbackRequest.text] as Unicode code points over the
 * original UTF-16 string, including surrogate pairs, and emits
 * [ResolvedFontRun] ranges in UTF-16 offsets. For each code point it asks
 * [policy] to order catalog families, prefixes that plan with all available
 * [FallbackRequest.preferredFamilies] in request order, then selects the first
 * planned face whose [coverage] reports support. If no planned face reports
 * support, the resolver deliberately assigns the first planned face when one
 * exists so downstream shaping or raster code can surface `.notdef` instead of
 * hiding missing coverage. When the catalog has no usable faces, resolution
 * returns an empty list.
 *
 * @property catalog Family-to-face catalog available for resolution.
 * @property policy Portable family fallback policy used per code point.
 * @property coverage External source of real glyph coverage facts.
 */
class CatalogFontResolver(
    private val catalog: FallbackCatalog,
    private val policy: FontFallbackPolicy,
    private val coverage: FontCoverageProvider,
) : FontResolver {
    data class AxisSelection(
        val selectedVariationCoordinates: List<TypefaceVariationCoordinate>,
        val selectedNamedInstance: String?,
        val supportedAxes: List<String>,
        val unsupportedAxes: List<String>,
        val clampedAxes: List<String>,
        val metricsVariationUnavailable: Boolean,
        val namedInstanceMatched: Boolean,
        val namedInstanceRequestedButUnmatched: Boolean,
    )

    private data class CandidateResolution(
        val familyName: String,
        val face: FontFace,
        val selectedFace: FontFace,
        val trace: FallbackCandidateTrace,
        val covered: Boolean,
        val unsupportedAxisCount: Int,
        val clampedAxisCount: Int,
        val metricsVariationUnavailable: Boolean,
        val namedInstanceMatched: Boolean,
        val namedInstanceRequestedButUnmatched: Boolean,
    )

    /**
     * Resolves text to contiguous face runs using catalog order, fallback policy,
     * and external coverage facts.
     *
     * @param request Text, locale, and preferred families to resolve.
     * @return Contiguous UTF-16 ranges assigned to concrete faces, or an empty
     * list when no catalog face can be planned.
     */
    override fun resolve(request: FallbackRequest): List<ResolvedFontRun> {
        val runs = mutableListOf<ResolvedFontRun>()
        for (decision in resolveDecisions(request)) {
            val face = decision.face
            if (face != null) {
                val previous = runs.lastOrNull()
                if (previous != null && previous.face == face && previous.end == decision.trace.start) {
                    runs[runs.lastIndex] = previous.copy(end = decision.trace.end)
                } else {
                    runs += ResolvedFontRun(start = decision.trace.start, end = decision.trace.end, face = face)
                }
            }
        }
        return runs
    }

    /**
     * Records the per-code-point fallback decisions made by [resolve].
     *
     * @param request Text, locale, and preferred families to resolve.
     * @return Deterministic trace with one decision per Unicode code point.
     */
    fun trace(request: FallbackRequest): FallbackResolutionTrace =
        FallbackResolutionTrace(decisions = resolveDecisions(request).map { it.trace })

    private fun resolveDecisions(request: FallbackRequest): List<ResolvedFallbackDecision> {
        if (request.text.isEmpty()) return emptyList()

        val decisions = mutableListOf<ResolvedFallbackDecision>()
        var offset = 0
        while (offset < request.text.length) {
            val codePoint = request.text.codePointAt(offset)
            val nextOffset = offset + Character.charCount(codePoint)
            decisions += resolveDecision(
                request = request,
                codePoint = codePoint,
                start = offset,
                end = nextOffset,
            )
            offset = nextOffset
        }
        return decisions
    }

    private fun resolveDecision(
        request: FallbackRequest,
        codePoint: Int,
        start: Int,
        end: Int,
    ): ResolvedFallbackDecision {
        val requestedVariationCoordinates = request.variationCoordinates.normalizedVariationCoordinates()
        val availableFamilyNames = catalog.availableFamilyNames()
        val plan = policy.planFamilyNames(
            availableFamilyNames = availableFamilyNames,
            requestedFamily = request.preferredFamilies.firstOrNull(),
            locales = listOfNotNull(request.locale),
            codePoint = codePoint,
        )
        val candidateFamilies = preferredThenPlannedFamilies(
            preferredFamilies = request.preferredFamilies,
            availableFamilyNames = availableFamilyNames,
            plannedFamilies = plan.orderedFamilies,
        )
        val genericChain = policy.genericFallbackChains[plan.genericFamily].orEmpty()
        val localeChains = plan.locales.flatMap { locale -> policy.localeFallbackChains[locale].orEmpty() }
        val scriptChain = policy.scriptFallbackChains[plan.script].orEmpty()
        var firstPlannedCandidate: CandidateResolution? = null
        val candidates = mutableListOf<CandidateResolution>()
        for (family in candidateFamilies) {
            for (face in catalog.families[family]?.faces.orEmpty().orderedByStyleDistance(request.style)) {
                val axisSelection = face.typeface.selectVariationForFallback(
                    requestedVariationCoordinates = requestedVariationCoordinates,
                    requestedNamedInstance = request.namedInstance,
                )
                val selectedFace = face.withResolvedVariationCoordinates(axisSelection.selectedVariationCoordinates)
                val covered = coverage.supports(face.typeface.id, codePoint)
                val reasons = buildList {
                    if (familyMatchesRequested(family, request.preferredFamilies)) add("requested-family")
                    if (family in localeChains) add("locale-hint")
                    if (family in scriptChain) add("script-fallback")
                    if (plan.script == "emoji" && family in policy.emojiPreferredFamilies) add("emoji-preference")
                    if (family in genericChain) add("generic-family")
                    if (axisSelection.namedInstanceMatched) add("named-instance")
                    if (axisSelection.supportedAxes.isNotEmpty()) add("variation-axis")
                    if (covered) add("glyph-coverage")
                }
                val candidate = CandidateResolution(
                    familyName = family,
                    face = face,
                    selectedFace = selectedFace,
                    trace = FallbackCandidateTrace(
                        familyName = family,
                        typefaceId = selectedFace.typeface.id,
                        covered = covered,
                        styleDistance = face.typeface.style.distanceTo(request.style),
                        reasons = reasons.ifEmpty { listOf("catalog-order") },
                        selectedNamedInstance = axisSelection.selectedNamedInstance,
                        supportedAxes = axisSelection.supportedAxes,
                        selectedVariationCoordinates = axisSelection.selectedVariationCoordinates,
                    ),
                    covered = covered,
                    unsupportedAxisCount = axisSelection.unsupportedAxes.size,
                    clampedAxisCount = axisSelection.clampedAxes.size,
                    metricsVariationUnavailable = axisSelection.metricsVariationUnavailable,
                    namedInstanceMatched = axisSelection.namedInstanceMatched,
                    namedInstanceRequestedButUnmatched = axisSelection.namedInstanceRequestedButUnmatched,
                )
                candidates += candidate
                if (firstPlannedCandidate == null) {
                    firstPlannedCandidate = candidate
                }
            }
        }
        val selectedCovered = candidates.filter { candidate -> candidate.covered }
            .minWithOrNull(
                compareBy<CandidateResolution> { candidate -> candidate.unsupportedAxisCount }
                    .thenBy { candidate -> candidate.clampedAxisCount }
                    .thenBy { candidate -> if (request.namedInstance != null && !candidate.namedInstanceMatched) 1 else 0 }
                    .thenBy { candidate -> if (candidate.metricsVariationUnavailable) 1 else 0 }
                    .thenBy { candidate -> candidate.trace.styleDistance },
            )
        if (selectedCovered != null) {
            return ResolvedFallbackDecision(
                trace = FallbackDecisionTrace(
                    start = start,
                    end = end,
                    codePoint = codePoint,
                    requestedFamilies = request.preferredFamilies,
                    genericFamily = plan.genericFamily,
                    script = plan.script,
                    locales = plan.locales,
                    candidateFamilies = candidateFamilies,
                    candidates = candidates.map { candidate -> candidate.trace }.markSelected(selectedCovered.selectedFace.typeface.id, null),
                    selectedFamily = selectedCovered.familyName,
                    selectedTypefaceId = selectedCovered.selectedFace.typeface.id,
                    requestedVariationCoordinates = requestedVariationCoordinates,
                    selectedNamedInstance = selectedCovered.trace.selectedNamedInstance,
                    selectedVariationCoordinates = selectedCovered.selectedFace.typeface.variationCoordinates,
                    covered = true,
                    diagnosticCode = selectedCovered.fallbackDiagnosticCode(),
                ),
                face = selectedCovered.selectedFace,
            )
        }
        return if (firstPlannedCandidate != null) {
            ResolvedFallbackDecision(
                trace = FallbackDecisionTrace(
                    start = start,
                    end = end,
                    codePoint = codePoint,
                    requestedFamilies = request.preferredFamilies,
                    genericFamily = plan.genericFamily,
                    script = plan.script,
                    locales = plan.locales,
                    candidateFamilies = candidateFamilies,
                    candidates = candidates.map { candidate -> candidate.trace }
                        .markSelected(firstPlannedCandidate.selectedFace.typeface.id, "glyph-missing"),
                    selectedFamily = firstPlannedCandidate.familyName,
                    selectedTypefaceId = firstPlannedCandidate.selectedFace.typeface.id,
                    requestedVariationCoordinates = requestedVariationCoordinates,
                    selectedNamedInstance = firstPlannedCandidate.trace.selectedNamedInstance,
                    selectedVariationCoordinates = firstPlannedCandidate.selectedFace.typeface.variationCoordinates,
                    covered = false,
                    diagnosticCode = firstPlannedCandidate.fallbackDiagnosticCode(defaultDiagnosticCode = "font.fallback-glyph-unavailable"),
                ),
                face = firstPlannedCandidate.selectedFace,
            )
        } else {
            ResolvedFallbackDecision(
                trace = FallbackDecisionTrace(
                    start = start,
                    end = end,
                    codePoint = codePoint,
                    requestedFamilies = request.preferredFamilies,
                    genericFamily = plan.genericFamily,
                    script = plan.script,
                    locales = plan.locales,
                    candidateFamilies = candidateFamilies,
                    candidates = emptyList(),
                    selectedFamily = null,
                    selectedTypefaceId = null,
                    requestedVariationCoordinates = requestedVariationCoordinates,
                    selectedNamedInstance = null,
                    selectedVariationCoordinates = emptyList(),
                    covered = false,
                    diagnosticCode = "font.fallback-family-unavailable",
                ),
                face = null,
            )
        }
    }

    private fun preferredThenPlannedFamilies(
        preferredFamilies: List<String>,
        availableFamilyNames: List<String>,
        plannedFamilies: List<String>,
    ): List<String> {
        val availableByKey = linkedMapOf<String, String>()
        for (family in availableFamilyNames) {
            val key = family.familyKey()
            if (key.isNotEmpty()) availableByKey.putIfAbsent(key, family)
        }

        val ordered = mutableListOf<String>()
        val seen = linkedSetOf<String>()
        fun addAvailableFamily(family: String) {
            val key = family.familyKey()
            val available = availableByKey[key] ?: return
            if (seen.add(available.familyKey())) ordered += available
        }

        preferredFamilies.forEach(::addAvailableFamily)
        plannedFamilies.forEach(::addAvailableFamily)
        return ordered
    }

    private data class ResolvedFallbackDecision(
        val trace: FallbackDecisionTrace,
        val face: FontFace?,
    )

    private fun CandidateResolution.fallbackDiagnosticCode(
        defaultDiagnosticCode: String? = null,
    ): String? =
        when {
            unsupportedAxisCount > 0 -> "font.variation-axis-unsupported"
            metricsVariationUnavailable -> "font.metrics-variation-unavailable"
            clampedAxisCount > 0 -> "font.fallback.axis-clamped"
            namedInstanceRequestedButUnmatched -> "text.fallback.variation-defaulted"
            else -> defaultDiagnosticCode
        }
}

/**
 * Catalog of family names to collections used during fallback resolution.
 *
 * @property families Mapping from normalized or display family names to font collections.
 */
data class FallbackCatalog(
    val families: Map<String, FontCollection> = emptyMap(),
) {
    /**
     * Returns available family names in deterministic display order.
     *
     * @return Catalog family names sorted case-insensitively with display-name
     * tie-breaking.
     */
    fun availableFamilyNames(): List<String> =
        families.keys.sortedWith(compareBy<String> { it.lowercase() }.thenBy { it })

    companion object {
        /**
         * Builds a fallback catalog from already parsed font faces.
         *
         * This is the pure Kotlin equivalent of the family grouping previously
         * done by the renderer-specific system font manager. The method does
         * not parse SFNT/OpenType data and does not infer styles; callers pass
         * fully prepared [FontFace] objects, usually after a parser module has
         * converted source bytes into [TypefaceData]. Families with blank names
         * are ignored because they cannot be requested or matched
         * deterministically. Duplicate typeface ids inside one family keep the
         * first occurrence so scanner retries or repeated sources do not
         * inflate fallback order.
         *
         * @param faces Parsed faces to group by display family name.
         * @return Catalog keyed by trimmed family names in deterministic family
         * order, while preserving first-seen face order within each family.
         */
        fun fromFaces(faces: Iterable<FontFace>): FallbackCatalog {
            val displayNameByKey = linkedMapOf<String, String>()
            val grouped = linkedMapOf<String, MutableList<FontFace>>()
            val seenByFamily = linkedMapOf<String, MutableSet<TypefaceID>>()

            for (face in faces) {
                val familyName = face.typeface.familyName.trim()
                if (familyName.isEmpty()) continue
                val familyKey = familyName.familyKey()
                val displayName = displayNameByKey.getOrPut(familyKey) { familyName }
                val seen = seenByFamily.getOrPut(familyKey) { linkedSetOf() }
                if (!seen.add(face.typeface.id)) continue
                grouped.getOrPut(displayName) { mutableListOf() } += face
            }

            val orderedFamilies = grouped.keys.sortedWith(compareBy<String> { it.lowercase() }.thenBy { it })
            return FallbackCatalog(
                families = orderedFamilies.associateWith { familyName ->
                    FontCollection(faces = grouped.getValue(familyName).toList())
                },
            )
        }
    }
}

/**
 * Request object for portable font fallback planning.
 *
 * @property availableFamilyNames Families known to the caller, in any order.
 * @property requestedFamily Optional family or generic family requested by
 * style.
 * @property locales Optional BCP 47 locale tags that can influence fallback
 * order.
 * @property codePoint Optional Unicode code point being resolved.
 */
data class FontFallbackPlanRequest(
    val availableFamilyNames: List<String>,
    val requestedFamily: String? = null,
    val locales: List<String> = emptyList(),
    val codePoint: Int? = null,
)

/**
 * Portable family-order fallback plan.
 *
 * @property orderedFamilies Available families ordered by requested family,
 * locale, script, emoji, generic, and final catalog fallback policy.
 * @property requestedFamily Family name requested by the caller, if any.
 * @property genericFamily Normalized generic family used by the plan.
 * @property script Script bucket inferred from [FontFallbackPlanRequest.codePoint].
 * @property locales Normalized language tags used by locale chains.
 */
data class FontFallbackPlan(
    val orderedFamilies: List<String>,
    val requestedFamily: String?,
    val genericFamily: String,
    val script: String,
    val locales: List<String>,
) {
    /**
     * Serializes the plan as one stable line for diagnostics.
     *
     * @return Dumpable fallback planning evidence.
     */
    fun dump(): String = buildString {
        append("requested=")
        append(requestedFamily ?: "none")
        append(" generic=")
        append(genericFamily)
        append(" script=")
        append(script)
        append(" locales=")
        append(locales.joinToString(prefix = "[", postfix = "]"))
        append(" families=")
        append(orderedFamilies.joinToString(prefix = "[", postfix = "]"))
    }
}

/**
 * Portable fallback family policy independent of Skia APIs.
 *
 * The policy contains generic chains such as `sans-serif`, locale chains keyed
 * by BCP 47 language subtags, script chains keyed by coarse script buckets, and
 * emoji-preferred families. Planning only orders families that are present in
 * the caller-provided availability list.
 *
 * @property defaultGeneric Generic family used when no requested generic can be
 * inferred.
 * @property genericFallbackChains Generic family to ordered family chain map.
 * @property scriptFallbackChains Script bucket to ordered family chain map.
 * @property localeFallbackChains Locale language to ordered family chain map.
 * @property emojiPreferredFamilies Families that should be tried early for
 * emoji code points.
 */
data class FontFallbackPolicy(
    val defaultGeneric: String,
    val genericFallbackChains: Map<String, List<String>>,
    val scriptFallbackChains: Map<String, List<String>>,
    val localeFallbackChains: Map<String, List<String>>,
    val emojiPreferredFamilies: List<String>,
) {
    /**
     * Plans fallback family order for explicit inputs.
     *
     * @param availableFamilyNames Families that may be returned.
     * @param requestedFamily Optional requested display or generic family.
     * @param locales Optional BCP 47 locale tags.
     * @param codePoint Optional Unicode code point used for script policy.
     * @return Ordered family plan containing only available family names.
     */
    fun planFamilyNames(
        availableFamilyNames: List<String>,
        requestedFamily: String? = null,
        locales: List<String> = emptyList(),
        codePoint: Int? = null,
    ): FontFallbackPlan =
        plan(
            FontFallbackPlanRequest(
                availableFamilyNames = availableFamilyNames,
                requestedFamily = requestedFamily,
                locales = locales,
                codePoint = codePoint,
            ),
        )

    /**
     * Plans fallback family order for an existing fallback request and catalog.
     *
     * @param request Text and family request from the public resolver surface.
     * @param catalog Available families from the caller.
     * @return Ordered family plan for the request's first code point.
     */
    fun plan(request: FallbackRequest, catalog: FallbackCatalog): FontFallbackPlan =
        planFamilyNames(
            availableFamilyNames = catalog.availableFamilyNames(),
            requestedFamily = request.preferredFamilies.firstOrNull(),
            locales = listOfNotNull(request.locale),
            codePoint = request.text.firstCodePointOrNull(),
        )

    /**
     * Plans fallback family order from a structured request.
     *
     * @param request Explicit fallback planning request.
     * @return Ordered family plan containing only available family names.
     */
    fun plan(request: FontFallbackPlanRequest): FontFallbackPlan {
        val availableByKey = linkedMapOf<String, String>()
        for (family in request.availableFamilyNames) {
            val key = family.familyKey()
            if (key.isNotEmpty()) availableByKey.putIfAbsent(key, family)
        }

        val ordered = mutableListOf<String>()
        val seen = linkedSetOf<String>()
        fun addFamily(name: String?) {
            val key = name?.familyKey().orEmpty()
            if (key.isEmpty()) return
            val available = availableByKey[key] ?: return
            if (seen.add(available.familyKey())) ordered.add(available)
        }
        fun addFamilies(names: Iterable<String>) {
            for (name in names) addFamily(name)
        }

        val normalizedLocales = request.locales.normalizedLocaleLanguages()
        val script = classifyFallbackScript(request.codePoint)
        val generic = genericFor(request.requestedFamily)

        addFamily(request.requestedFamily)
        for (locale in normalizedLocales) addFamilies(localeFallbackChains[locale].orEmpty())
        addFamilies(scriptFallbackChains[script].orEmpty())
        if (script == "emoji") addFamilies(emojiPreferredFamilies)
        addFamilies(genericFallbackChains[generic].orEmpty())
        addFamilies(request.availableFamilyNames)

        return FontFallbackPlan(
            orderedFamilies = ordered,
            requestedFamily = request.requestedFamily,
            genericFamily = generic,
            script = script,
            locales = normalizedLocales,
        )
    }

    private fun genericFor(requestedFamily: String?): String {
        val requestedGeneric = requestedFamily?.toGenericFamilyKey()
        val defaultKey = defaultGeneric.toGenericFamilyKey() ?: defaultGeneric.familyKey()
        return when {
            requestedGeneric != null && requestedGeneric in genericFallbackChains -> requestedGeneric
            defaultKey in genericFallbackChains -> defaultKey
            else -> genericFallbackChains.keys.firstOrNull() ?: defaultKey
        }
    }

    companion object {
        /**
         * Conservative portable fallback policy based on widely deployed
         * families and coarse Unicode script ranges.
         */
        val Default: FontFallbackPolicy = FontFallbackPolicy(
            defaultGeneric = "sans-serif",
            genericFallbackChains = mapOf(
                "sans-serif" to listOf("Noto Sans", "Liberation Sans", "Arial", "DejaVu Sans"),
                "serif" to listOf("Noto Serif", "Liberation Serif", "Times New Roman", "DejaVu Serif"),
                "monospace" to listOf("Noto Sans Mono", "Liberation Mono", "Courier New", "DejaVu Sans Mono"),
            ),
            scriptFallbackChains = mapOf(
                "arabic" to listOf("Noto Naskh Arabic", "Noto Sans Arabic", "Arial"),
                "devanagari" to listOf("Noto Sans Devanagari", "Mangal"),
                "han" to listOf("Noto Sans CJK SC", "Noto Sans CJK JP", "Source Han Sans SC"),
                "hebrew" to listOf("Noto Sans Hebrew", "Arial"),
                "japanese" to listOf("Noto Sans CJK JP", "Yu Gothic", "Hiragino Sans"),
                "korean" to listOf("Noto Sans CJK KR", "Malgun Gothic"),
                "emoji" to listOf("Noto Color Emoji", "Apple Color Emoji", "Segoe UI Emoji"),
                "latin" to listOf("Liberation Sans", "Arial"),
            ),
            localeFallbackChains = mapOf(
                "ar" to listOf("Noto Naskh Arabic", "Noto Sans Arabic"),
                "hi" to listOf("Noto Sans Devanagari", "Mangal"),
                "ja" to listOf("Noto Sans CJK JP", "Yu Gothic"),
                "ko" to listOf("Noto Sans CJK KR", "Malgun Gothic"),
                "zh" to listOf("Noto Sans CJK SC", "Source Han Sans SC", "Noto Sans CJK JP"),
            ),
            emojiPreferredFamilies = listOf("Noto Color Emoji", "Apple Color Emoji", "Segoe UI Emoji"),
        )
    }
}

/**
 * Input to fallback resolution for a span of text.
 *
 * @property text Text whose code points require font coverage.
 * @property locale Optional BCP 47 locale tag for script and regional fallback policy.
 * @property preferredFamilies Ordered family names requested by caller style.
 * @property style Requested style used to choose among faces inside a matching family.
 */
data class FallbackRequest(
    val text: String,
    val locale: String? = null,
    val preferredFamilies: List<String> = emptyList(),
    val style: FontStyle = FontStyle(),
    val variationCoordinates: List<TypefaceVariationCoordinate> = emptyList(),
    val namedInstance: String? = null,
)

/**
 * Concrete font assignment for a contiguous UTF-16 range in a fallback request.
 *
 * @property start Inclusive UTF-16 start offset in the request text.
 * @property end Exclusive UTF-16 end offset in the request text.
 * @property face Font face selected for this run.
 */
data class ResolvedFontRun(
    val start: Int,
    val end: Int,
    val face: FontFace,
)

data class FallbackResolutionTrace(
    val decisions: List<FallbackDecisionTrace>,
) {
    /**
     * Serializes all decisions as newline-separated stable lines.
     *
     * @return Empty string when the traced request has no text.
     */
    fun dump(): String = decisions.joinToString(separator = "\n") { it.dump() }
}

/**
 * One catalog fallback decision for one Unicode code point.
 *
 * @property start Inclusive UTF-16 start offset in the request text.
 * @property end Exclusive UTF-16 end offset in the request text.
 * @property codePoint Unicode scalar value being resolved.
 * @property requestedFamilies Caller-preferred families in request order.
 * @property candidateFamilies Available candidate families in actual fallback order.
 * @property selectedFamily Family selected for this code point, when any face
 * could be planned.
 * @property selectedTypefaceId Typeface selected for this code point, when any
 * face could be planned.
 * @property covered True when [FontCoverageProvider] reported direct glyph
 * coverage for the selected face.
 * @property diagnosticCode Stable reason code for fallback refusals or `.notdef`
 * routing, or null when coverage was found.
 */
data class FallbackDecisionTrace(
    val start: Int,
    val end: Int,
    val codePoint: Int,
    val requestedFamilies: List<String>,
    val genericFamily: String = "sans-serif",
    val script: String = "default",
    val locales: List<String> = emptyList(),
    val candidateFamilies: List<String>,
    val candidates: List<FallbackCandidateTrace> = emptyList(),
    val selectedFamily: String?,
    val selectedTypefaceId: TypefaceID?,
    val requestedVariationCoordinates: List<TypefaceVariationCoordinate> = emptyList(),
    val selectedNamedInstance: String? = null,
    val selectedVariationCoordinates: List<TypefaceVariationCoordinate> = emptyList(),
    val covered: Boolean,
    val diagnosticCode: String? = null,
) {
    init {
        require(start >= 0) { "start must be non-negative." }
        require(end > start) { "end must be greater than start." }
        require(Character.isValidCodePoint(codePoint)) { "codePoint must be a valid Unicode scalar value." }
        require(diagnosticCode == null || diagnosticCode.isStableDiagnosticCode()) {
            "diagnosticCode must be a stable one-line diagnostic code."
        }
        require((selectedFamily == null) == (selectedTypefaceId == null)) {
            "selectedFamily and selectedTypefaceId must both be present or both be absent."
        }
        require(genericFamily.isNotBlank()) { "genericFamily must not be blank." }
        require(script.isNotBlank()) { "script must not be blank." }
    }

    /**
     * Serializes the decision as one stable line for fallback evidence.
     *
     * @return Dumpable fallback decision evidence without object identity or live handles.
     */
    fun dump(): String = buildString {
        append("start=")
        append(start)
        append(" end=")
        append(end)
        append(" codePoint=")
        append(codePoint.toCodePointEvidence())
        append(" requestedFamilies=")
        append(requestedFamilies.joinToString(prefix = "[", postfix = "]", separator = ","))
        append(" genericFamily=")
        append(genericFamily)
        append(" script=")
        append(script)
        append(" locales=")
        append(locales.joinToString(prefix = "[", postfix = "]", separator = ","))
        append(" candidateFamilies=")
        append(candidateFamilies.joinToString(prefix = "[", postfix = "]", separator = ","))
        append(" selectedFamily=")
        append(selectedFamily?.evidenceQuoted() ?: "none")
        append(" selectedTypefaceId=")
        append(selectedTypefaceId?.value?.toHexDashString() ?: "none")
        if (requestedVariationCoordinates.isNotEmpty()) {
            append(" requestedVariationCoordinates=")
            append(requestedVariationCoordinates.joinToString(prefix = "[", postfix = "]") { coordinate ->
                "${coordinate.axisTag}=${coordinate.value.toTypefaceJsonNumber()}"
            })
        }
        if (selectedNamedInstance != null) {
            append(" selectedNamedInstance=")
            append(selectedNamedInstance.evidenceQuoted())
        }
        if (selectedVariationCoordinates.isNotEmpty()) {
            append(" selectedVariationCoordinates=")
            append(selectedVariationCoordinates.joinToString(prefix = "[", postfix = "]") { coordinate ->
                "${coordinate.axisTag}=${coordinate.value.toTypefaceJsonNumber()}"
            })
        }
        append(" covered=")
        append(covered)
        append(" diagnostic=")
        append(diagnosticCode ?: "none")
    }
}

data class FallbackCandidateTrace(
    val familyName: String,
    val typefaceId: TypefaceID,
    val covered: Boolean,
    val styleDistance: Int,
    val reasons: List<String>,
    val selectedNamedInstance: String? = null,
    val supportedAxes: List<String> = emptyList(),
    val selectedVariationCoordinates: List<TypefaceVariationCoordinate> = emptyList(),
    val selected: Boolean = false,
    val rejectionReason: String? = null,
) {
    init {
        require(familyName.isNotBlank()) { "familyName must not be blank." }
        require(styleDistance >= 0) { "styleDistance must be non-negative." }
        require(reasons.isNotEmpty()) { "reasons must not be empty." }
        require(reasons.all { it.isStableDiagnosticCode() }) {
            "reasons must use stable one-line tokens."
        }
        require(rejectionReason == null || rejectionReason.isStableDiagnosticCode()) {
            "rejectionReason must use a stable one-line token when present."
        }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendFontCompactJsonField("familyName", familyName, comma = true)
        appendFontCompactJsonField("typefaceId", typefaceId.value.toHexDashString(), comma = true)
        appendFontCompactJsonField("covered", covered, comma = true)
        append("styleDistance".evidenceQuoted()).append(":").append(styleDistance).append(",")
        appendFontCompactJsonField("reason", primaryReason(), comma = true)
        appendStringArrayField("reasons", reasons, comma = true)
        if (selectedNamedInstance != null) {
            appendFontCompactJsonField("selectedNamedInstance", selectedNamedInstance, comma = true)
        }
        if (supportedAxes.isNotEmpty()) {
            appendStringArrayField("supportedAxes", supportedAxes, comma = true)
        }
        if (selectedVariationCoordinates.isNotEmpty()) {
            append("selectedVariationCoordinates".evidenceQuoted()).append(":")
            append(selectedVariationCoordinates.toCompactJson())
            append(",")
        }
        appendFontCompactJsonField("selected", selected, comma = true)
        append("rejectionReason".evidenceQuoted()).append(":").append(rejectionReason.toFontJsonNullableString())
        append("}")
    }

    private fun primaryReason(): String =
        reasons.firstOrNull { it != "glyph-coverage" } ?: reasons.first()
}

private fun String.familyKey(): String = trim().lowercase()

private fun FontSourceKind.isHostDependentByDefault(): Boolean =
    this == FontSourceKind.SYSTEM || this == FontSourceKind.SYSTEM_SCANNED

val FontSourceKind.serializedName: String
    get() = when (this) {
        FontSourceKind.USER_DATA -> "UserDataFontSource"
        FontSourceKind.USER_STREAM -> "UserStreamFontSource"
        FontSourceKind.USER_FILE -> "UserFileFontSource"
        FontSourceKind.SYSTEM_SCANNED -> "SystemScannedFontSource"
        FontSourceKind.MEMORY -> "UserDataFontSource"
        FontSourceKind.SYSTEM -> "SystemScannedFontSource"
        FontSourceKind.FILE -> "UserFileFontSource"
        FontSourceKind.RESOURCE -> "UserDataFontSource"
    }

private fun List<String>.normalizedTableTags(): List<String> {
    for (tag in this) {
        require(tag.isStableSfntTableTag()) {
            "tableTags must contain four-character printable ASCII SFNT tags."
        }
    }
    return distinct().sorted()
}

private fun List<String>.normalizedManifestStrings(fieldName: String): List<String> {
    for (value in this) {
        require(value.isStableManifestString()) { "$fieldName must contain stable single-line strings." }
    }
    return distinct().sorted()
}

private fun List<TypefaceVariationCoordinate>.normalizedVariationCoordinates(): List<TypefaceVariationCoordinate> {
    val sorted = sortedBy { coordinate -> coordinate.axisTag }
    require(sorted.map { coordinate -> coordinate.axisTag }.distinct().size == sorted.size) {
        "variation coordinates must have unique axis tags."
    }
    return sorted
}

private fun List<TypefaceVariationCoordinate>.mergeVariationCoordinates(
    overrides: List<TypefaceVariationCoordinate>,
): List<TypefaceVariationCoordinate> {
    val merged = linkedMapOf<String, TypefaceVariationCoordinate>()
    normalizedVariationCoordinates().forEach { coordinate -> merged[coordinate.axisTag] = coordinate }
    overrides.normalizedVariationCoordinates().forEach { coordinate -> merged[coordinate.axisTag] = coordinate }
    return merged.values.toList().normalizedVariationCoordinates()
}

private fun List<TypefaceVariationCoordinate>.toCompactJson(): String =
    normalizedVariationCoordinates().joinToString(prefix = "[", postfix = "]", separator = ",") { coordinate ->
        buildString {
            append("{")
            appendFontCompactJsonField("axisTag", coordinate.axisTag, comma = true)
            append("value".evidenceQuoted()).append(":").append(coordinate.value.toTypefaceJsonNumber())
            append("}")
        }
    }

private fun String.isStableSfntTableTag(): Boolean =
    length == 4 && all { character -> character.code in 0x20..0x7E }

private fun String.isStableManifestToken(): Boolean =
    isNotBlank() && all { character -> character.code in 0x21..0x7E }

private fun String.isStableManifestString(): Boolean =
    isNotBlank() && all { character -> character.code in 0x20..0x7E }

private val FontSlant.typefaceSerializedName: String
    get() = when (this) {
        FontSlant.UPRIGHT -> "upright"
        FontSlant.ITALIC -> "italic"
        FontSlant.OBLIQUE -> "oblique"
    }

private fun String.isStableDiagnosticCode(): Boolean =
    isNotEmpty() && all { character -> character.code in 0x21..0x7E }

private fun String.isLowercaseSha256Hex(): Boolean =
    length == 64 && all { character -> character in '0'..'9' || character in 'a'..'f' }

private fun Double.toTypefaceJsonNumber(): String {
    return normalizedTypefaceVariationValue().toString()
}

private fun Double.normalizedTypefaceVariationValue(): Double =
    if (this == 0.0) 0.0 else this

private fun ByteArray.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    val chars = CharArray(digest.size * 2)
    for (index in digest.indices) {
        val value = digest[index].toInt() and 0xFF
        chars[index * 2] = HexDigits[value ushr 4]
        chars[index * 2 + 1] = HexDigits[value and 0x0F]
    }
    return chars.concatToString()
}

private fun stableUuidFromSha256(preimage: String): Uuid {
    val digest = MessageDigest.getInstance("SHA-256").digest(preimage.toByteArray(Charsets.UTF_8))
    val uuidBytes = digest.copyOfRange(0, 16)
    uuidBytes[6] = ((uuidBytes[6].toInt() and 0x0f) or 0x50).toByte()
    uuidBytes[8] = ((uuidBytes[8].toInt() and 0x3f) or 0x80).toByte()
    return Uuid.parse(uuidBytes.toUuidString())
}

private fun ByteArray.toUuidString(): String = buildString(36) {
    for (index in this@toUuidString.indices) {
        if (index == 4 || index == 6 || index == 8 || index == 10) {
            append('-')
        }
        val value = this@toUuidString[index].toInt() and 0xff
        append(value.toString(16).padStart(2, '0'))
    }
}

private fun String?.toFontJsonNullableString(): String =
    this?.evidenceQuoted() ?: "null"

private fun StringBuilder.appendFontJsonField(
    name: String,
    value: String,
    indent: String,
    comma: Boolean,
) {
    append(indent).append(name.evidenceQuoted()).append(": ").append(value.evidenceQuoted())
    if (comma) append(",")
    append("\n")
}

private fun StringBuilder.appendFontJsonField(
    name: String,
    value: Int,
    indent: String,
    comma: Boolean,
) {
    append(indent).append(name.evidenceQuoted()).append(": ").append(value)
    if (comma) append(",")
    append("\n")
}

private fun StringBuilder.appendFontJsonField(
    name: String,
    value: Boolean,
    indent: String,
    comma: Boolean,
) {
    append(indent).append(name.evidenceQuoted()).append(": ").append(value)
    if (comma) append(",")
    append("\n")
}

private fun StringBuilder.appendFontCompactJsonField(
    name: String,
    value: String,
    comma: Boolean,
) {
    append(name.evidenceQuoted())
    append(":")
    append(value.evidenceQuoted())
    if (comma) append(",")
}

private fun StringBuilder.appendFontCompactJsonField(
    name: String,
    value: Boolean,
    comma: Boolean,
) {
    append(name.evidenceQuoted())
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendStringArrayField(
    name: String,
    values: List<String>,
    comma: Boolean,
) {
    append(name.evidenceQuoted())
    append(":")
    append(values.joinToString(prefix = "[", postfix = "]", separator = ",") { value -> value.evidenceQuoted() })
    if (comma) append(",")
}

private fun List<String>.normalizedCatalogFacts(fieldName: String): List<String> {
    require(all { value -> value.isNotBlank() && value.isStableManifestString() }) {
        "$fieldName must use stable non-blank one-line strings."
    }
    return distinct().sorted()
}

private fun List<FallbackCandidateTrace>.markSelected(
    selectedTypefaceId: TypefaceID,
    uncoveredRejectionReason: String?,
): List<FallbackCandidateTrace> =
    map { candidate ->
        when {
            candidate.typefaceId == selectedTypefaceId -> candidate.copy(selected = true, rejectionReason = null)
            candidate.covered -> candidate.copy(rejectionReason = "earlier-candidate-selected")
            else -> candidate.copy(rejectionReason = uncoveredRejectionReason ?: "glyph-missing")
        }
    }

private fun TypefaceData.selectVariationForFallback(
    requestedVariationCoordinates: List<TypefaceVariationCoordinate>,
    requestedNamedInstance: String? = null,
): CatalogFontResolver.AxisSelection {
    val normalizedRequestedCoordinates = requestedVariationCoordinates.normalizedVariationCoordinates()
    val matchedNamedInstance = requestedNamedInstance?.let { namedInstance ->
        namedInstances.firstOrNull { instance -> instance.name == namedInstance }
    }
    val effectiveRequestedCoordinates = matchedNamedInstance
        ?.coordinates
        ?.mergeVariationCoordinates(overrides = normalizedRequestedCoordinates)
        ?: normalizedRequestedCoordinates
    if (effectiveRequestedCoordinates.isEmpty()) {
        return CatalogFontResolver.AxisSelection(
            selectedVariationCoordinates = variationCoordinates,
            selectedNamedInstance = null,
            supportedAxes = emptyList(),
            unsupportedAxes = emptyList(),
            clampedAxes = emptyList(),
            metricsVariationUnavailable = false,
            namedInstanceMatched = false,
            namedInstanceRequestedButUnmatched = requestedNamedInstance != null,
        )
    }
    val axesByTag = variationAxes.associateBy { axis -> axis.axisTag }
    val supportedAxes = mutableListOf<String>()
    val unsupportedAxes = mutableListOf<String>()
    val clampedAxes = mutableListOf<String>()
    val selectedCoordinates = mutableListOf<TypefaceVariationCoordinate>()
    for (coordinate in effectiveRequestedCoordinates) {
        val axis = axesByTag[coordinate.axisTag]
        if (axis == null) {
            unsupportedAxes += coordinate.axisTag
            continue
        }
        supportedAxes += coordinate.axisTag
        val selectedValue = axis.clamp(coordinate.value)
        if (selectedValue != coordinate.value.normalizedTypefaceVariationValue()) {
            clampedAxes += coordinate.axisTag
        }
        selectedCoordinates += TypefaceVariationCoordinate(axisTag = coordinate.axisTag, value = selectedValue)
    }
    val normalizedSelectedCoordinates = selectedCoordinates.normalizedVariationCoordinates()
    val selectedNamedInstance = matchedNamedInstance
        ?.takeIf { instance -> instance.coordinates == normalizedSelectedCoordinates }
        ?.name
    return CatalogFontResolver.AxisSelection(
        selectedVariationCoordinates = normalizedSelectedCoordinates,
        selectedNamedInstance = selectedNamedInstance,
        supportedAxes = supportedAxes.distinct().sorted(),
        unsupportedAxes = unsupportedAxes.distinct().sorted(),
        clampedAxes = clampedAxes.distinct().sorted(),
        metricsVariationUnavailable = supportedAxes.isNotEmpty() && !variationMetricsSupported,
        namedInstanceMatched = selectedNamedInstance != null,
        namedInstanceRequestedButUnmatched = requestedNamedInstance != null && selectedNamedInstance == null,
    )
}

private fun FontFace.withResolvedVariationCoordinates(
    selectedVariationCoordinates: List<TypefaceVariationCoordinate>,
): FontFace {
    val normalizedCoordinates = selectedVariationCoordinates.normalizedVariationCoordinates()
    if (normalizedCoordinates == typeface.variationCoordinates.normalizedVariationCoordinates()) return this
    val preimage = typeface.identityPreimage ?: return this
    val updatedPreimage = typefaceIdentityPreimage(
        sourceId = preimage.sourceId,
        collectionIndex = preimage.collectionIndex,
        postScriptName = preimage.postScriptName,
        familyName = preimage.familyName,
        styleName = preimage.styleName,
        style = preimage.style,
        outlineFormat = preimage.outlineFormat,
        selectedCMap = preimage.selectedCMap,
        scalerMode = preimage.scalerMode,
        variationCoordinates = normalizedCoordinates,
        palette = preimage.palette,
        fallbackCatalogGeneration = preimage.fallbackCatalogGeneration,
        tableTags = preimage.tableTags,
        diagnostics = preimage.diagnostics,
    )
    return copy(
        typeface = typeface.copy(
            id = updatedPreimage.typefaceId(),
            variationCoordinates = normalizedCoordinates,
            identityPreimage = updatedPreimage,
        ),
    )
}

private fun familyMatchesRequested(
    familyName: String,
    requestedFamilies: List<String>,
): Boolean {
    val key = familyName.familyKey()
    return requestedFamilies.any { requested -> requested.familyKey() == key }
}

private fun FallbackDecisionTrace.primaryFallbackReason(): String? =
    when {
        covered && candidates.any { candidate -> candidate.selected && "emoji-preference" in candidate.reasons } -> "emoji-preference"
        covered && candidates.any { candidate -> candidate.selected && "locale-hint" in candidate.reasons } -> "locale-hint"
        covered && candidates.any { candidate -> candidate.selected && "script-fallback" in candidate.reasons } -> "script-fallback"
        covered && candidates.any { candidate -> candidate.selected && "generic-family" in candidate.reasons } -> "generic-family"
        !covered && diagnosticCode == "font.fallback-glyph-unavailable" -> "missing-glyph"
        !covered && diagnosticCode == "font.fallback-family-unavailable" -> "family-unavailable"
        else -> null
    }

private fun FallbackDecisionTrace.shapingDiagnosticCode(): String? =
    when (diagnosticCode) {
        "font.fallback-family-unavailable", "font.fallback-glyph-unavailable" -> "text.shaping.fallback-missing"
        else -> null
    }

private fun String.evidenceQuoted(): String = buildString {
    append('"')
    for (character in this@evidenceQuoted) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (character < ' ') {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
    append('"')
}

private const val HexDigits: String = "0123456789abcdef"

private fun String.toGenericFamilyKey(): String? =
    when (familyKey()) {
        "sans", "sans serif", "sans-serif", "ui-sans-serif" -> "sans-serif"
        "serif", "ui-serif" -> "serif"
        "mono", "monospace", "monospaced", "ui-monospace" -> "monospace"
        else -> null
    }

private fun List<String>.normalizedLocaleLanguages(): List<String> {
    val languages = linkedSetOf<String>()
    for (locale in this) {
        val language = locale
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.substringBefore('-')
            ?.substringBefore('_')
            ?.lowercase()
            .orEmpty()
        if (language.isNotEmpty()) languages.add(language)
    }
    return languages.toList()
}

private fun String.firstCodePointOrNull(): Int? =
    if (isEmpty()) null else codePointAt(0)

private fun List<FontFace>.orderedByStyleDistance(requestedStyle: FontStyle): List<FontFace> =
    withIndex()
        .sortedWith(
            compareBy<IndexedValue<FontFace>> { (_, face) -> face.typeface.style.distanceTo(requestedStyle) }
                .thenBy { (index, _) -> index },
        )
        .map { (_, face) -> face }

private fun FontStyle.distanceTo(requestedStyle: FontStyle): Int {
    val slantPenalty = if (slant == requestedStyle.slant) 0 else 10_000
    val weightPenalty = kotlin.math.abs(weight - requestedStyle.weight) * 10
    val widthPenalty = kotlin.math.abs(width - requestedStyle.width) * 100
    return slantPenalty + weightPenalty + widthPenalty
}

private fun classifyFallbackScript(codePoint: Int?): String =
    when {
        codePoint == null -> "default"
        codePoint in 0x1F300..0x1FAFF ||
            codePoint in 0x2600..0x27BF -> "emoji"
        codePoint in 0x3040..0x30FF -> "japanese"
        codePoint in 0x4E00..0x9FFF -> "han"
        codePoint in 0xAC00..0xD7AF -> "korean"
        codePoint in 0x0600..0x06FF -> "arabic"
        codePoint in 0x0590..0x05FF -> "hebrew"
        codePoint in 0x0900..0x097F -> "devanagari"
        codePoint.isLatinLetterCodePoint() -> "latin"
        else -> "default"
    }

private fun Int.isLatinLetterCodePoint(): Boolean =
    Character.isValidCodePoint(this) &&
        Character.isLetter(this) &&
        Character.UnicodeScript.of(this) == Character.UnicodeScript.LATIN

private fun Int.toCodePointEvidence(): String {
    val minimumWidth = if (this <= 0xFFFF) 4 else 6
    return "U+" + toString(radix = 16).uppercase().padStart(minimumWidth, '0')
}
