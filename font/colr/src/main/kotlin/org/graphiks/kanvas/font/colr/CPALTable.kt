package org.graphiks.kanvas.font.colr

/**
 * Stores a CPAL palette in pure Kotlin form.
 *
 * @property index palette index selected from the font.
 * @property colors packed ARGB colors in palette order.
 * @property labels optional human-readable palette labels.
 */
data class CPALPalette(
    val index: Int,
    val colors: List<Int>,
    val labels: List<String> = emptyList(),
)

/**
 * Describes one caller-supplied CPAL palette entry override.
 *
 * Overrides are renderer-neutral color substitutions applied after a palette has been selected
 * from a [CPALTable]. They are keyed by palette entry index, not by glyph ID or paint node ID.
 * The [color] value is a packed ARGB integer in the same representation used by [CPALPalette].
 *
 * @property index zero-based palette entry index to replace in the selected palette.
 * @property color packed ARGB color that replaces the selected palette entry when [index] is in
 * range for that palette.
 */
data class CPALPaletteOverride(
    val index: Int,
    val color: Int,
)

/**
 * Selects a CPAL palette and applies palette entry overrides without renderer dependencies.
 *
 * The selection mirrors only the pure CPAL concepts from font argument data while deliberately
 * avoiding graphics, color-management, platform image, and platform-specific execution
 * dependencies. [index] chooses the palette by table order. [overrides] are then applied in list
 * order to the selected palette; duplicate override indexes therefore use the last supplied color.
 * Overrides whose indexes are outside the selected palette are ignored so callers can share
 * override lists across fonts with different palette sizes.
 *
 * @property index zero-based palette index requested from a CPAL table.
 * @property overrides renderer-neutral color substitutions keyed by selected palette entry index.
 */
data class CPALPaletteSelection(
    val index: Int = 0,
    val overrides: List<CPALPaletteOverride> = emptyList(),
) {
    /**
     * Resolves this selection against a parsed CPAL table.
     *
     * @param table CPAL table whose palette list supplies the base palette.
     * @return the selected palette with in-range overrides applied, or null when [index] does not
     * name a palette in [table].
     */
    fun select(table: CPALTable): CPALPalette? {
        val palette = table.palettes.getOrNull(index) ?: return null
        if (overrides.isEmpty()) return palette

        val colors = palette.colors.toMutableList()
        overrides.forEach { override ->
            if (override.index in colors.indices) {
                colors[override.index] = override.color
            }
        }
        return palette.copy(colors = colors.toList())
    }

    companion object {
        /**
         * Default CPAL selection: palette zero with no color overrides.
         */
        val Default: CPALPaletteSelection = CPALPaletteSelection()
    }
}

/**
 * Stores the parsed contents of a CPAL version 0 table.
 *
 * The table keeps palettes in font order and preserves colors as packed ARGB integers. OpenType
 * stores CPAL v0 color records as BGRA bytes; [CPALV0Parser] converts that byte order once during
 * parsing so downstream color glyph planning does not need to know the table encoding.
 *
 * @property numPaletteEntries number of color entries expected in every parsed palette.
 * @property numColorRecords number of raw color records advertised by the CPAL table.
 * @property palettes parsed palettes in table order.
 */
data class CPALTable(
    val numPaletteEntries: Int,
    val numColorRecords: Int,
    val palettes: List<CPALPalette>,
)
