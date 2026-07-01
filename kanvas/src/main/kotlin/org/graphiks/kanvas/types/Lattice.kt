package org.graphiks.kanvas.types

data class Lattice(
    val xDivs: List<Int>,
    val yDivs: List<Int>,
    val rects: List<Rect>? = null,
    val colors: List<Color>? = null,
    val flags: List<LatticeFlags>? = null,
)

enum class LatticeFlags { DEFAULT, TRANSPARENT }
