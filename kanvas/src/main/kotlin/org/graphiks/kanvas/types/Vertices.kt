package org.graphiks.kanvas.types

import org.graphiks.math.geometry.Point2F32

data class Vertices(
    val mode: VertexMode,
    val positions: List<Point2F32>,
    val texCoords: List<Point2F32>? = null,
    val colors: List<Color>? = null,
    val indices: List<Int>? = null,
)

enum class VertexMode { TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN }
