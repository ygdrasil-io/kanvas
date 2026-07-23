package org.graphiks.kanvas.types

data class Lattice(
    val xDivs: List<Int>,
    val yDivs: List<Int>,
    val rects: List<Rect>? = null,
    val colors: List<Color>? = null,
    val flags: List<LatticeFlags>? = null,
)

/** Per-cell rendering behavior for [Lattice]. */
enum class LatticeFlags {
    /** Sample the corresponding source cell from the image. */
    DEFAULT,
    /** Leave the corresponding destination cell untouched. */
    TRANSPARENT,
    /** Fill the corresponding destination cell with its [Lattice.colors] entry. */
    FIXED_COLOR,
}
