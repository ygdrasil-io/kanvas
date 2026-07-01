package org.graphiks.kanvas.types

data class Vertices(
    val mode: VertexMode,
    val positions: List<Point>,
    val texCoords: List<Point>? = null,
    val colors: List<Color>? = null,
    val indices: List<Int>? = null,
)

enum class VertexMode { TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN }
