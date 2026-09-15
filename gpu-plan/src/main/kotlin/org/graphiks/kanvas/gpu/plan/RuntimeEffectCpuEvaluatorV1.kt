package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.RuntimeUniformType

/** Linear-premultiplied RGBA, before attachment encoding. */
public data class RuntimeEffectCpuColorF32(public val rF32: Float, public val gF32: Float, public val bF32: Float, public val aF32: Float) {
    init { require(listOf(rF32, gF32, bF32, aF32).all { it.isFinite() }) { W5hPlanDiagnostics.CpuNumeric } }
}
public sealed interface RuntimeEffectCpuUniformV1 {
    public data class FloatValue(public val name: String, public val valueF32: Float) : RuntimeEffectCpuUniformV1 {
        init { require(name.isNotBlank() && valueF32.isFinite()) { W5hPlanDiagnostics.CpuUniforms } }
    }
    public data class IntValue(public val name: String, public val valueI32: Int) : RuntimeEffectCpuUniformV1 {
        init { require(name.isNotBlank()) { W5hPlanDiagnostics.CpuUniforms } }
    }
    public class FloatComponents(public val name: String, public val type: RuntimeUniformType, componentsF32: List<Float>) : RuntimeEffectCpuUniformV1 {
        public val componentsF32: List<Float> = immutableList(componentsF32)
        init {
            val countI32 = when (type) {
                RuntimeUniformType.FLOAT2 -> 2
                RuntimeUniformType.FLOAT3 -> 3
                RuntimeUniformType.FLOAT4 -> 4
                RuntimeUniformType.MAT3X3 -> 9
                RuntimeUniformType.MAT4X4 -> 16
                else -> throw IllegalArgumentException(W5hPlanDiagnostics.CpuUniforms)
            }
            require(name.isNotBlank() && this.componentsF32.size == countI32 && this.componentsF32.all { it.isFinite() }) { W5hPlanDiagnostics.CpuUniforms }
        }
    }
}
public class RuntimeEffectCpuInputsV1(children: List<RuntimeEffectCpuColorF32?>, uniforms: List<RuntimeEffectCpuUniformV1>) {
    public val children: List<RuntimeEffectCpuColorF32?> = immutableList(children)
    public val uniforms: List<RuntimeEffectCpuUniformV1> = immutableList(uniforms)
}
public interface RuntimeEffectCpuEvaluatorV1 {
    public val id: String
    public val evaluatorVersionI32: Int
    public fun evaluate(inputs: RuntimeEffectCpuInputsV1): RuntimeEffectCpuColorF32
}
public object ChildOpacityCpuEvaluatorV1 : RuntimeEffectCpuEvaluatorV1 {
    override val id: String = "kanvas.runtime.child-opacity.cpu-v1"
    override val evaluatorVersionI32: Int = 1
    override fun evaluate(inputs: RuntimeEffectCpuInputsV1): RuntimeEffectCpuColorF32 {
        require(inputs.children.size == 1 && inputs.children[0] != null) { "invalid.material.runtime_effect.cpu_children" }
        require(inputs.uniforms.size == 1) { "invalid.material.runtime_effect.cpu_uniforms" }
        val alpha = inputs.uniforms[0] as? RuntimeEffectCpuUniformV1.FloatValue
            ?: throw IllegalArgumentException("invalid.material.runtime_effect.cpu_uniforms")
        require(alpha.name == "alpha" && alpha.valueF32.isFinite() && alpha.valueF32 in 0f..1f) {
            "invalid.material.runtime_effect.cpu_uniforms"
        }
        val child = requireNotNull(inputs.children[0])
        require(listOf(child.rF32, child.gF32, child.bF32, child.aF32).all { it.isFinite() }) {
            "invalid.material.runtime_effect.cpu_numeric"
        }
        val out = RuntimeEffectCpuColorF32(child.rF32 * alpha.valueF32, child.gF32 * alpha.valueF32,
            child.bF32 * alpha.valueF32, child.aF32 * alpha.valueF32)
        require(listOf(out.rF32, out.gF32, out.bF32, out.aF32).all { it.isFinite() }) {
            "invalid.material.runtime_effect.cpu_numeric"
        }
        return out
    }
}
