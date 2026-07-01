package org.graphiks.kanvas.pipeline

import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Shader

class RuntimeEffect internal constructor(
    val id: String,
    val module: ShaderModule,
    val uniformLayout: UniformLayout,
    val children: List<ChildSlot>,
) {
    fun makeShader(uniforms: UniformBlock): Shader.RuntimeEffect = Shader.RuntimeEffect(this, uniforms)
    fun makeColorFilter(uniforms: UniformBlock): ColorFilter {
        val result = makeColorFilterHook?.invoke(this, uniforms)
        if (result != null) return result
        throw UnsupportedOperationException(
            "RuntimeEffect.makeColorFilter: wgsl4k is available but color filter lowering is not yet implemented."
        )
    }
    fun makeBlender(uniforms: UniformBlock): Blender {
        val result = makeBlenderHook?.invoke(this, uniforms)
        if (result != null) return result
        throw UnsupportedOperationException(
            "RuntimeEffect as Blender not yet implemented."
        )
    }

    companion object {
        fun compile(wgsl: String): Result<RuntimeEffect> {
            return try {
                val effect = compileWgsl?.invoke(wgsl)
                if (effect != null) Result.success(effect)
                else Result.failure(IllegalArgumentException("WGSL compilation failed: could not parse or reflect the source"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        fun registered(id: String): RuntimeEffect? = lookupRegistered?.invoke(id)

        /** Backend hooks installed by :gpu-renderer's RuntimeEffectCompileProvider. */
        internal var compileWgsl: ((String) -> RuntimeEffect?)? = null
        internal var lookupRegistered: ((String) -> RuntimeEffect?)? = null
        internal var makeColorFilterHook: ((RuntimeEffect, UniformBlock) -> ColorFilter?)? = null
        internal var makeBlenderHook: ((RuntimeEffect, UniformBlock) -> Blender?)? = null
    }
}
