package org.graphiks.kanvas.paint

sealed interface MeshChild
data class ShaderChild(val shader: Shader) : MeshChild
data class ColorFilterChild(val filter: ColorFilter) : MeshChild
data class BlenderChild(val blender: Blender) : MeshChild
