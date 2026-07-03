package org.graphiks.kanvas.types

import org.graphiks.kanvas.paint.MeshProgram

data class Mesh(
    val vertices: Vertices,
    val program: MeshProgram? = null,
    val bounds: Rect,
)
