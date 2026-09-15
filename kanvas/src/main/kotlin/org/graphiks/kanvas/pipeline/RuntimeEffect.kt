package org.graphiks.kanvas.pipeline

import java.util.Collections
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.render.ir.RuntimeEffectAbi
import org.graphiks.kanvas.render.ir.RuntimeEffectDescriptor
import org.graphiks.kanvas.gpu.plan.RuntimeEffectSemanticCatalog

class RuntimeEffect private constructor(private val definition: Definition) {
    private sealed interface Definition {
        data class PositiveV3(val descriptor: RuntimeEffectDescriptor) : Definition
        data class LegacyV0(val id: String, val module: ShaderModule, val uniformLayout: UniformLayout, val children: List<ChildSlot>) : Definition
    }
    @Deprecated("Legacy v0 construction only; use registered(id, semanticVersionI32)")
    internal constructor(id: String, module: ShaderModule, uniformLayout: UniformLayout, children: List<ChildSlot>, registerOnConstruction: Boolean = true) :
        this(Definition.LegacyV0(id, module.immutableSnapshot(),
            UniformLayout(Collections.unmodifiableList(ArrayList(uniformLayout.slots))), Collections.unmodifiableList(ArrayList(children)))) {
        if (registerOnConstruction) register(this)
    }
    val descriptor: RuntimeEffectDescriptor? get() = (definition as? Definition.PositiveV3)?.descriptor
    val id: String get() = when (val value = definition) {
        is Definition.PositiveV3 -> value.descriptor.id.value
        is Definition.LegacyV0 -> value.id
    }
    val semanticVersionI32: Int get() = descriptor?.semanticVersionI32 ?: 0
    val abiHash: String? get() = descriptor?.abiHash
    val kind: RuntimeEffectAbi? get() = descriptor?.abi
    val moduleOrNull: ShaderModule? get() = (definition as? Definition.LegacyV0)?.module
    @Deprecated("Legacy WGSL only; use descriptor for registered effects")
    val module: ShaderModule get() = moduleOrNull ?: throw UnsupportedOperationException("Registered runtime effects have no WGSL module")
    @Deprecated("Legacy physical layout only; use descriptor.uniformBlock")
    val uniformLayout: UniformLayout get() = (definition as? Definition.LegacyV0)?.uniformLayout
        ?: throw UnsupportedOperationException("Registered runtime effects have no legacy uniform layout")
    @Deprecated("Use descriptor.childSlots for logical type and nullability")
    val children: List<ChildSlot> = when (val value = definition) {
        is Definition.LegacyV0 -> value.children
        is Definition.PositiveV3 -> Collections.unmodifiableList(value.descriptor.childSlots.map { ChildSlot(it.name, ChildType.valueOf(it.type.name)) })
    }
    fun makeShader(
        uniforms: UniformBlock,
        children: Map<String, Shader> = emptyMap(),
    ): Shader.RuntimeEffect = makeShader(uniforms, children, RuntimeEffectResourceBindings.Empty)
    fun makeShader(uniforms: UniformBlock, children: Map<String, Shader>, resources: RuntimeEffectResourceBindings): Shader.RuntimeEffect {
        requireFactory(RuntimeEffectAbi.SHADER, resources)
        return Shader.RuntimeEffect(this, uniforms, Collections.unmodifiableMap(LinkedHashMap(children)), resources)
    }
    fun makeColorFilter(
        uniforms: UniformBlock,
        children: Map<String, ColorFilter> = emptyMap(),
    ): ColorFilter = makeColorFilter(uniforms, children, RuntimeEffectResourceBindings.Empty)
    fun makeColorFilter(uniforms: UniformBlock, children: Map<String, ColorFilter>, resources: RuntimeEffectResourceBindings): ColorFilter {
        requireFactory(RuntimeEffectAbi.COLOR_FILTER, resources)
        val result = if (semanticVersionI32 == 0) makeColorFilterHook?.invoke(this, uniforms, children) else null
        if (result != null) return result
        return ColorFilter.RuntimeEffect(this, uniforms, Collections.unmodifiableMap(LinkedHashMap(children)), resources)
    }
    fun makeBlender(uniforms: UniformBlock): Blender = makeBlender(uniforms, RuntimeEffectResourceBindings.Empty)
    fun makeBlender(uniforms: UniformBlock, resources: RuntimeEffectResourceBindings): Blender {
        requireFactory(RuntimeEffectAbi.BLENDER, resources)
        val result = if (semanticVersionI32 == 0) makeBlenderHook?.invoke(this, uniforms) else null
        if (result != null) return result
        throw UnsupportedOperationException(
            "RuntimeEffect as Blender not yet implemented."
        )
    }
    private fun requireFactory(abi: RuntimeEffectAbi, resources: RuntimeEffectResourceBindings) {
        if (semanticVersionI32 > 0) {
            require(kind == abi) { "Runtime effect kind mismatch" }
            resources.requireMatches(requireNotNull(descriptor))
        } else require(resources.sizeI32 == 0) { "invalid.material.runtime_effect.legacy_resources" }
    }

    companion object {
        fun registered(id: String, semanticVersionI32: Int): RuntimeEffect? =
            RuntimeEffectSemanticCatalog.builtinSnapshot().builtinDescriptor(id, semanticVersionI32)?.let(::fromBuiltin)

        private fun fromBuiltin(descriptor: RuntimeEffectDescriptor): RuntimeEffect {
            val entry = requireNotNull(RuntimeEffectSemanticCatalog.builtinSnapshot().find(descriptor.id, descriptor.semanticVersionI32, descriptor.abiHash))
            require(entry.descriptor == descriptor && descriptor.recomputeAbiHash() == descriptor.abiHash)
            return RuntimeEffect(Definition.PositiveV3(descriptor))
        }

        @Deprecated("Legacy WGSL only; use registered(id, semanticVersionI32)")
        fun compile(wgsl: String): Result<RuntimeEffect> {
            return try {
                val effect = compileWgsl?.invoke(wgsl)
                if (effect != null && effect.semanticVersionI32 == 0) Result.success(effect)
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
        @Deprecated("Legacy v0 registration only; positive built-ins are immutable")
        fun register(effect: RuntimeEffect): RuntimeEffect {
            require(effect.semanticVersionI32 == 0) { "Positive runtime effects cannot be registered" }
            return synchronized(registryLock) {
                val installed = registeredEffects[effect.id]
                if (installed != null) {
                    require(installed.hasCompatibleDescriptor(effect)) {
                        "Runtime effect id ${effect.id} is already registered with an incompatible descriptor"
                    }
                } else {
                    registeredEffects = immutableRegistry(registeredEffects + (effect.id to effect))
                }
                installed ?: effect
            }
        }

        @Deprecated("Legacy v0 lookup only; use registered(id, semanticVersionI32)")
        fun registered(id: String): RuntimeEffect? = (registeredEffects[id] ?: lookupRegistered?.invoke(id))?.takeIf { it.semanticVersionI32 == 0 }

        /**
         * Builds a descriptor while a compatibility archive is still being
         * decoded.  The caller must install it with [registerDecoded] only
         * after the whole archive has passed validation.
         */
        internal fun detached(
            id: String,
            module: ShaderModule,
            uniformLayout: UniformLayout,
            children: List<ChildSlot>,
        ): RuntimeEffect = RuntimeEffect(id, module, uniformLayout, children, registerOnConstruction = false)

        /** Installs a fully decoded archive's descriptors as one transaction. */
        internal fun registerDecoded(effects: List<RuntimeEffect>): Boolean = synchronized(registryLock) {
            val decodedById = LinkedHashMap<String, RuntimeEffect>()
            for (effect in effects) {
                if (effect.semanticVersionI32 != 0) return false
                val decoded = decodedById.putIfAbsent(effect.id, effect)
                if (decoded != null && !decoded.hasCompatibleDescriptor(effect)) return false
                val installed = registeredEffects[effect.id]
                if (installed != null && !installed.hasCompatibleDescriptor(effect)) return false
            }
            registeredEffects = immutableRegistry(registeredEffects + decodedById.filterKeys { it !in registeredEffects })
            true
        }

        /** Backend hooks installed by :gpu-renderer's RuntimeEffectCompileProvider. */
        @Volatile private var registeredEffects: Map<String, RuntimeEffect> = emptyMap()
        private val registryLock = Any()
        internal var compileWgsl: ((String) -> RuntimeEffect?)? = null
        internal var lookupRegistered: ((String) -> RuntimeEffect?)? = null
        internal var makeColorFilterHook: ((RuntimeEffect, UniformBlock, Map<String, ColorFilter>) -> ColorFilter?)? = null
        internal var makeBlenderHook: ((RuntimeEffect, UniformBlock) -> Blender?)? = null

        private fun RuntimeEffect.hasCompatibleDescriptor(other: RuntimeEffect): Boolean =
            semanticVersionI32 == 0 && other.semanticVersionI32 == 0 && id == other.id &&
                module.source == other.module.source &&
                module.entryPoint == other.module.entryPoint &&
                module.uniforms == other.module.uniforms &&
                module.textures == other.module.textures &&
                module.vertexLayout == other.module.vertexLayout &&
                uniformLayout == other.uniformLayout &&
                children == other.children

        private fun immutableRegistry(entries: Map<String, RuntimeEffect>): Map<String, RuntimeEffect> =
            Collections.unmodifiableMap(LinkedHashMap(entries))
    }
}
