package org.graphiks.kanvas.font

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
)

/**
 * Parsed typeface metadata shared by resolvers, fallback catalogs, and scaler implementations.
 *
 * @property id Stable identifier for this parsed face.
 * @property source Raw source that produced the typeface.
 * @property familyName Preferred family name exposed to font matching.
 * @property styleName Preferred style name such as Regular, Italic, or Bold.
 * @property diagnostics Non-fatal diagnostics collected while preparing the typeface.
 */
data class TypefaceData(
    val id: TypefaceID,
    val source: FontSource,
    val familyName: String,
    val styleName: String,
    val diagnostics: List<FontSourceDiagnostic> = emptyList(),
)

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
 * Resolves text and requested family information to concrete font runs.
 */
interface FontResolver {
    /**
     * Resolves a fallback request into ordered font runs.
     *
     * @param request Text, locale, and family preferences to resolve.
     * @return Runs that cover the request text with concrete font faces.
     */
    fun resolve(request: FallbackRequest): List<ResolvedFontRun> = TODO("Implement font fallback resolution.")
}

/**
 * Catalog of family names to collections used during fallback resolution.
 *
 * @property families Mapping from normalized or display family names to font collections.
 */
data class FallbackCatalog(
    val families: Map<String, FontCollection> = emptyMap(),
)

/**
 * Input to fallback resolution for a span of text.
 *
 * @property text Text whose code points require font coverage.
 * @property locale Optional BCP 47 locale tag for script and regional fallback policy.
 * @property preferredFamilies Ordered family names requested by caller style.
 */
data class FallbackRequest(
    val text: String,
    val locale: String? = null,
    val preferredFamilies: List<String> = emptyList(),
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
