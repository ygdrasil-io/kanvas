package org.graphiks.kanvas.pipeline

import java.util.Collections

data class UniformSlot(val name: String, val binding: Int, val type: UniformType, val size: Int)
data class TextureSlot(val name: String, val binding: Int)
data class UniformLayout(val slots: List<UniformSlot>)
data class ChildSlot(val name: String, val type: ChildType)

class ShaderModule private constructor(
    val source: String,
    val entryPoint: String,
    val uniforms: List<UniformSlot>,
    val textures: List<TextureSlot>,
    val vertexLayout: VertexLayout,
) {
    /** Handle-free defensive snapshot used by prepared draw contracts. */
    internal fun immutableSnapshot(): ShaderModule = ShaderModule(
        source = source,
        entryPoint = entryPoint,
        uniforms = Collections.unmodifiableList(ArrayList(uniforms)),
        textures = Collections.unmodifiableList(ArrayList(textures)),
        vertexLayout = vertexLayout.copy(
            attributes = Collections.unmodifiableList(ArrayList(vertexLayout.attributes)),
        ),
    )

    companion object {
        /** Creates a handle-free shader-module snapshot from public module ABI values. */
        fun of(
            source: String,
            entryPoint: String = "main",
            uniforms: List<UniformSlot> = emptyList(),
            textures: List<TextureSlot> = emptyList(),
            vertexLayout: VertexLayout = VertexLayout(emptyList(), 0),
        ): ShaderModule = ShaderModule(
            source,
            entryPoint,
            Collections.unmodifiableList(ArrayList(uniforms)),
            Collections.unmodifiableList(ArrayList(textures)),
            vertexLayout.copy(attributes = Collections.unmodifiableList(ArrayList(vertexLayout.attributes))),
        )

        fun fromSource(wgsl: String, entry: String = "main"): ShaderModule =
            of(wgsl, entry)
        fun fromResource(path: String, entry: String = "main"): ShaderModule =
            of("resource:$path", entry)
    }
}
