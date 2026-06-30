package org.graphiks.kanvas.pipeline

import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Shader

class RuntimeEffect private constructor(
    val id: String,
    val module: ShaderModule,
    val uniformLayout: UniformLayout,
    val children: List<ChildSlot>,
) {
    fun makeShader(uniforms: UniformBlock): Shader.RuntimeEffect = Shader.RuntimeEffect(this, uniforms)
    fun makeColorFilter(uniforms: UniformBlock): ColorFilter {
        throw UnsupportedOperationException("RuntimeEffect as ColorFilter not yet implemented")
    }
    fun makeBlender(uniforms: UniformBlock): Blender {
        throw UnsupportedOperationException("RuntimeEffect as Blender not yet implemented")
    }

    companion object {
        fun compile(wgsl: String): Result<RuntimeEffect> = Result.failure(
            IllegalArgumentException("WGSL compilation not yet integrated (requires wgsl4k)")
        )
        fun registered(id: String): RuntimeEffect? = null
    }
}
