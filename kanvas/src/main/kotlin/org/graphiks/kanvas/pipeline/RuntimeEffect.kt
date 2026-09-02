package org.graphiks.kanvas.pipeline

import java.util.concurrent.ConcurrentHashMap
import java.util.Collections
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Shader

class RuntimeEffect internal constructor(
    val id: String,
    module: ShaderModule,
    uniformLayout: UniformLayout,
    children: List<ChildSlot>,
) {
    val module: ShaderModule = module.immutableSnapshot()
    val uniformLayout: UniformLayout = UniformLayout(Collections.unmodifiableList(ArrayList(uniformLayout.slots)))
    val children: List<ChildSlot> = Collections.unmodifiableList(ArrayList(children))

    init {
        register(this)
    }
    fun makeShader(
        uniforms: UniformBlock,
        children: Map<String, Shader> = emptyMap(),
    ): Shader.RuntimeEffect = Shader.RuntimeEffect(this, uniforms, children)
    fun makeColorFilter(
        uniforms: UniformBlock,
        children: Map<String, ColorFilter> = emptyMap(),
    ): ColorFilter {
        val result = makeColorFilterHook?.invoke(this, uniforms)
        if (result != null) return result
        return ColorFilter.RuntimeEffect(this, uniforms, children)
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

        /**
         * Installs a handle-free runtime descriptor for scene reconstruction.
         * Compilation installs the resulting value; callers may re-install an
         * existing runtime effect on an application-owned reconstruction path.
         */
        fun register(effect: RuntimeEffect): RuntimeEffect {
            val installed = registeredEffects.putIfAbsent(effect.id, effect)
            if (installed != null) {
                require(installed.hasCompatibleDescriptor(effect)) {
                    "Runtime effect id ${effect.id} is already registered with an incompatible descriptor"
                }
            }
            return installed ?: effect
        }

        fun registered(id: String): RuntimeEffect? = registeredEffects[id] ?: lookupRegistered?.invoke(id)

        /** Backend hooks installed by :gpu-renderer's RuntimeEffectCompileProvider. */
        private val registeredEffects = ConcurrentHashMap<String, RuntimeEffect>()
        internal var compileWgsl: ((String) -> RuntimeEffect?)? = null
        internal var lookupRegistered: ((String) -> RuntimeEffect?)? = null
        internal var makeColorFilterHook: ((RuntimeEffect, UniformBlock) -> ColorFilter?)? = null
        internal var makeBlenderHook: ((RuntimeEffect, UniformBlock) -> Blender?)? = null

        private fun RuntimeEffect.hasCompatibleDescriptor(other: RuntimeEffect): Boolean =
            id == other.id &&
                module.source == other.module.source &&
                module.entryPoint == other.module.entryPoint &&
                module.uniforms == other.module.uniforms &&
                module.textures == other.module.textures &&
                module.vertexLayout == other.module.vertexLayout &&
                uniformLayout == other.uniformLayout &&
                children == other.children
    }
}
