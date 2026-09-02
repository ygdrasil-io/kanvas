package org.graphiks.kanvas.pipeline

import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.wgsl.arena.Handle
import org.graphiks.wgsl.ir.Module
import org.graphiks.wgsl.ir.ScalarKind
import org.graphiks.wgsl.ir.StorageClass
import org.graphiks.wgsl.ir.Type
import org.graphiks.wgsl.ir.TypeInner
import org.graphiks.wgsl.ir.VectorSize
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult

/**
 * Wires the wgsl4k library into [RuntimeEffect.compile] at init time.
 * Uses compileOnly dependency — catches NoClassDefFoundError if
 * wgsl4k JARs are not on the runtime classpath.
 *
 * Call [install] once during GPU backend initialization.
 */
object RuntimeEffectWgsl4kWiring {

    private var installed = false

    @Synchronized
    fun install() {
        if (installed) return
        installed = true

        RuntimeEffect.compileWgsl = { wgsl ->
            try { wgsl4kCompile(wgsl) }
            catch (_: NoClassDefFoundError) { null }
            catch (_: ClassNotFoundException) { null }
        }

        RuntimeEffect.makeColorFilterHook = { effect, uniforms, children ->
            ColorFilter.RuntimeEffect(effect, uniforms, children)
        }
    }

    private fun wgsl4kCompile(wgsl: String): RuntimeEffect? {
        val parsed = parseWgslResult(wgsl)
        if (!parsed.isSuccess) return null
        val module = Lowerer().lower(parsed.translationUnit)
        val entryName = if (module.entryPoints.isNotEmpty()) {
            module.entryPoints.first().name
        } else if (module.functions.isNotEmpty()) {
            module.functions.first().name
        } else return null
        val shaderModule = ShaderModule.fromSource(wgsl, entryName)

        val uniformSlots = module.runtimeUniformSlots() ?: return null
        val childSlots = module.runtimeTextureSlots()

        return RuntimeEffect(
            "compiled-${wgsl.hashCode().toUInt().toString(16)}",
            shaderModule,
            UniformLayout(uniformSlots),
            childSlots,
        )
    }

    /**
     * RuntimeEffect exposes the values expected by its public [UniformBlock],
     * rather than WGSL binding points. WGSL represents those values as fields of
     * uniform structs, so flatten each supported struct in declaration order and
     * give every public slot a distinct stable index.
     */
    private fun Module.runtimeUniformSlots(): List<UniformSlot>? {
        val slots = mutableListOf<Pair<String, UniformType>>()
        for (global in globalVariables) {
            if (global.storageClass != StorageClass.Uniform) continue
            val type = types[global.type]
            val fields = when (val inner = type.inner) {
                is TypeInner.Struct -> inner.members.map { member ->
                    member.name to runtimeUniformType(member.type)
                }
                else -> listOf(global.name to runtimeUniformType(global.type))
            }
            for ((name, uniformType) in fields) {
                if (uniformType == null || slots.any { it.first == name }) return null
                slots += name to uniformType
            }
        }
        return slots.mapIndexed { binding, (name, type) ->
            UniformSlot(name, binding, type, 0)
        }
    }

    private fun Module.runtimeUniformType(handle: Handle<Type>): UniformType? = when (val inner = types[handle].inner) {
        is TypeInner.Scalar -> when (inner.kind) {
            ScalarKind.F32 -> UniformType.FLOAT
            ScalarKind.Sint, ScalarKind.S32 -> UniformType.INT1
            else -> null
        }
        is TypeInner.Vector -> {
            val scalar = types[inner.scalar].inner as? TypeInner.Scalar ?: return null
            if (scalar.kind != ScalarKind.F32) return null
            when (inner.size) {
                VectorSize.Bi -> UniformType.FLOAT2
                VectorSize.Tri -> UniformType.FLOAT3
                VectorSize.Quad -> UniformType.FLOAT4
                else -> null
            }
        }
        is TypeInner.Matrix -> {
            val scalar = types[inner.scalar].inner as? TypeInner.Scalar ?: return null
            if (scalar.kind != ScalarKind.F32) return null
            when (inner.columns to inner.rows) {
                VectorSize.Tri to VectorSize.Tri -> UniformType.MAT3X3
                VectorSize.Quad to VectorSize.Quad -> UniformType.MAT4X4
                else -> null
            }
        }
        else -> null
    }

    /** A WGSL sampler accompanies a texture, but is not a RuntimeEffect child. */
    private fun Module.runtimeTextureSlots(): List<ChildSlot> = globalVariables.mapNotNull { global ->
        if (global.storageClass != StorageClass.Handle) return@mapNotNull null
        val opaque = types[global.type].inner as? TypeInner.Opaque ?: return@mapNotNull null
        if (!opaque.name.startsWith("texture_")) return@mapNotNull null
        ChildSlot(global.name, ChildType.SHADER)
    }
}
