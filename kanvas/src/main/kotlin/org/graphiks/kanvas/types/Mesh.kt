package org.graphiks.kanvas.types

import org.graphiks.math.geometry.RectF32

import org.graphiks.kanvas.paint.MeshProgram

data class Mesh(
    val vertices: Vertices,
    val program: MeshProgram? = null,
    val bounds: RectF32,
)
