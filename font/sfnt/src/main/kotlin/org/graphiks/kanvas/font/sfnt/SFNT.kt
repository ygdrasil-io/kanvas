package org.graphiks.kanvas.font.sfnt

import org.graphiks.kanvas.font.FontSource
import org.graphiks.kanvas.font.FontSourceID
import org.graphiks.kanvas.font.TypefaceID

/**
 * Four-byte SFNT table tag represented as text.
 *
 * @property value Tag text such as `cmap`, `name`, `glyf`, or `CFF `.
 */
@JvmInline
value class SFNTTableTag(
    val value: String,
)

/**
 * Top-level SFNT table directory read from an OpenType or TrueType container.
 *
 * @property scalerType Raw scaler type from the SFNT header.
 * @property tables Ordered table records advertised by the font file.
 */
data class SFNTTableDirectory(
    val scalerType: UInt,
    val tables: List<SFNTTableRecord> = emptyList(),
)

/**
 * Directory entry describing one SFNT table payload.
 *
 * @property tag Four-byte table tag.
 * @property checksum Raw checksum stored in the directory.
 * @property offset Byte offset from the start of the font data.
 * @property length Byte length of the table payload.
 */
data class SFNTTableRecord(
    val tag: SFNTTableTag,
    val checksum: UInt,
    val offset: UInt,
    val length: UInt,
)

/**
 * Low-level reader for SFNT table directories and table byte ranges.
 */
interface SFNTReader {
    /**
     * Reads the SFNT directory from raw font data.
     *
     * @param source Source bytes to inspect.
     * @return Parsed SFNT directory.
     */
    fun readDirectory(source: FontSource): SFNTTableDirectory = TODO("Implement SFNT directory reading.")

    /**
     * Reads the raw bytes for a single SFNT table.
     *
     * @param source Source bytes containing the table.
     * @param record Directory record describing table location and length.
     * @return Raw table payload.
     */
    fun readTable(source: FontSource, record: SFNTTableRecord): ByteArray = TODO("Implement SFNT table reading.")
}

/**
 * Parser that converts OpenType or TrueType source bytes into typed table containers.
 */
interface OpenTypeFaceParser {
    /**
     * Parses one face from a font source.
     *
     * @param source Raw font source to parse.
     * @param faceIndex Zero-based face index for TrueType/OpenType collections.
     * @return Parsed OpenType face data.
     */
    fun parse(source: FontSource, faceIndex: Int = 0): OpenTypeFaceData = TODO("Implement OpenType face parsing.")
}

/**
 * Parsed OpenType face data used by higher-level font APIs and scalers.
 *
 * @property id Stable identifier assigned to this parsed face.
 * @property source Raw source that produced the face.
 * @property directory SFNT table directory for the face.
 * @property cmap Parsed character-to-glyph mapping tables.
 * @property names Parsed naming metadata.
 * @property metrics Parsed horizontal, vertical, and global metric tables.
 * @property variations Parsed variable-font tables.
 * @property layout Parsed OpenType Layout tables.
 * @property color Parsed color font tables.
 * @property diagnostics Non-fatal parse diagnostics.
 */
data class OpenTypeFaceData(
    val id: TypefaceID,
    val source: FontSource,
    val directory: SFNTTableDirectory,
    val cmap: CMapTable = CMapTable(),
    val names: NameTable = NameTable(),
    val metrics: MetricsTables = MetricsTables(),
    val variations: VariationTables = VariationTables(),
    val layout: OpenTypeLayoutTables = OpenTypeLayoutTables(),
    val color: ColorFontTables = ColorFontTables(),
    val diagnostics: List<OpenTypeParseDiagnostic> = emptyList(),
)

/**
 * Parsed `cmap` table container for Unicode to glyph ID mappings.
 *
 * @property subtables Opaque named cmap subtable payloads as immutable byte
 * values until full parsing is implemented.
 */
data class CMapTable(
    val subtables: Map<String, List<Int>> = emptyMap(),
)

/**
 * Parsed `name` table container for family, style, and localized strings.
 *
 * @property records Opaque name records keyed by implementation-defined labels.
 */
data class NameTable(
    val records: Map<String, String> = emptyMap(),
)

/**
 * Parsed metric table container for `head`, `hhea`, `hmtx`, `vhea`, `vmtx`, `OS/2`, and related data.
 *
 * @property unitsPerEm Design units per em when known.
 * @property ascender Typographic ascender in font units when known.
 * @property descender Typographic descender in font units when known.
 */
data class MetricsTables(
    val unitsPerEm: Int? = null,
    val ascender: Int? = null,
    val descender: Int? = null,
)

/**
 * Parsed variable-font table container for `fvar`, `gvar`, `avar`, `HVAR`, `MVAR`, and related data.
 *
 * @property axes Axis defaults keyed by axis tag.
 */
data class VariationTables(
    val axes: Map<String, Double> = emptyMap(),
)

/**
 * Parsed OpenType Layout table container for shaping-related tables.
 *
 * @property tables Opaque layout table payloads keyed by table tag and stored
 * as immutable byte values.
 */
data class OpenTypeLayoutTables(
    val tables: Map<SFNTTableTag, List<Int>> = emptyMap(),
)

/**
 * Parsed color font table container for COLR, CPAL, CBDT, CBLC, sbix, and SVG data.
 *
 * @property tables Opaque color table payloads keyed by table tag and stored
 * as immutable byte values.
 */
data class ColorFontTables(
    val tables: Map<SFNTTableTag, List<Int>> = emptyMap(),
)

/**
 * Non-fatal diagnostic produced while parsing OpenType or SFNT data.
 *
 * @property sourceId Source that produced the diagnostic.
 * @property message Human-readable parse diagnostic.
 * @property table Optional table tag associated with the diagnostic.
 * @property causeCode Optional stable machine-readable parse cause code.
 * @property causeMessage Optional dumpable parse cause detail without retaining
 * a platform exception object.
 */
data class OpenTypeParseDiagnostic(
    val sourceId: FontSourceID,
    val message: String,
    val table: SFNTTableTag? = null,
    val causeCode: String? = null,
    val causeMessage: String? = null,
)
