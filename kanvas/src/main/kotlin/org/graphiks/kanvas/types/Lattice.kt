package org.graphiks.kanvas.types

import org.graphiks.math.geometry.RectF32

data class Lattice(
    val xDivs: List<Int>,
    val yDivs: List<Int>,
    val rects: List<RectF32>? = null,
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
