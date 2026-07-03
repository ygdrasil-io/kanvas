package org.graphiks.kanvas.pipeline

import org.graphiks.kanvas.paint.ColorFilter
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

    fun install() {
        if (installed) return
        installed = true

        RuntimeEffect.compileWgsl = { wgsl ->
            try { wgsl4kCompile(wgsl) }
            catch (_: NoClassDefFoundError) { null }
            catch (_: ClassNotFoundException) { null }
        }

        RuntimeEffect.makeColorFilterHook = { effect, uniforms ->
            ColorFilter.RuntimeEffect(effect, uniforms)
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

        val uniformSlots = module.globalVariables.mapNotNull { gv ->
            val b = gv.binding ?: return@mapNotNull null
            val ty = module.types[gv.type] ?: return@mapNotNull null
            val ut = when (ty.inner) {
                is TypeInner.Scalar -> UniformType.FLOAT
                is TypeInner.Vector -> when ((ty.inner as TypeInner.Vector).size) {
                    VectorSize.Bi -> UniformType.FLOAT2
                    VectorSize.Tri -> UniformType.FLOAT3
                    VectorSize.Quad -> UniformType.FLOAT4
                    else -> UniformType.FLOAT
                }
                is TypeInner.Matrix -> when ((ty.inner as TypeInner.Matrix).columns) {
                    VectorSize.Tri -> UniformType.MAT3X3
                    else -> UniformType.MAT4X4
                }
                else -> return@mapNotNull null
            }
            UniformSlot(gv.name, b.index, ut, 0)
        }

        val childSlots = module.globalVariables.mapNotNull { gv ->
            val b = gv.binding ?: return@mapNotNull null
            val ty = module.types[gv.type] ?: return@mapNotNull null
            if (ty.inner is TypeInner.Opaque)
                ChildSlot(gv.name, ChildType.SHADER)
            else null
        }

        return RuntimeEffect(
            "compiled-${wgsl.hashCode().toUInt().toString(16)}",
            shaderModule,
            UniformLayout(uniformSlots),
            childSlots,
        )
    }
}
