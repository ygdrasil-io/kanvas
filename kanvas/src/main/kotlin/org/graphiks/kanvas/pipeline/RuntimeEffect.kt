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
        throw UnsupportedOperationException(
            "RuntimeEffect.makeColorFilter: color filter compilation not yet implemented. " +
            "The wgsl4k library (external/wgsl4k) is available and already integrated in :gpu-renderer " +
            "(see KanvasWGSLValidator, KanvasWGSLReflectionProvider). Use makeShader() instead to use this effect as a paint shader."
        )
    }
    fun makeBlender(uniforms: UniformBlock): Blender {
        throw UnsupportedOperationException(
            "RuntimeEffect as Blender not yet implemented. " +
            "The wgsl4k library is available — this needs shader lowering semantics for blender effects."
        )
    }

    companion object {
        fun compile(wgsl: String): Result<RuntimeEffect> = Result.failure(
            IllegalArgumentException(
                "RuntimeEffect.compile: wgsl4k integration not yet wired. " +
                "The wgsl4k library (external/wgsl4k) is available and provides parse/validate/reflect/generate. " +
                "The existing KanvasWGSLValidator and KanvasWGSLReflectionProvider in :gpu-renderer already use " +
                "wgsl4k — RuntimeEffect.compile needs a simple delegation bridge to these providers."
            )
        )
        fun registered(id: String): RuntimeEffect? = null
    }
}
