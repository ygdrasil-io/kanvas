package org.graphiks.kanvas.paint

import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock

data class MeshProgram(
    val effect: RuntimeEffect,
    val uniforms: UniformBlock = UniformBlock.EMPTY,
    val children: MeshChildren = MeshChildren.EMPTY,
)
