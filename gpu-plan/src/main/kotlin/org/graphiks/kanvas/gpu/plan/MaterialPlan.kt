package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.color.ColorF32

@JvmInline
public value class MaterialPlanRef(public val indexI32: Int) {
    init { require(indexI32 >= 0) { "Material plan references must be non-negative" } }
}

@JvmInline
public value class MaterialProgramPlanId(public val value: String) {
    init { require(value.isNotBlank()) { "Material program IDs must not be blank" } }
}

/** Immutable code-shaping half of a W5a material. */
public sealed interface MaterialProgramPlan {
    public val versionI32: Int
    public val structuralId: MaterialProgramPlanId
    public fun copyNumericOperationGraphV1(): NumericOperationGraphV1

    public data object TransparentV1 : MaterialProgramPlan {
        override val versionI32: Int = 1
        override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId("w5a-transparent-v1")
        override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.Transparent
    }

    public data object SolidLinearPremulV1 : MaterialProgramPlan {
        override val versionI32: Int = 1
        override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId("w5a-solid-linear-premul-v1")
        override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 {
            val input = NumericOperationGraphV1.Node(NumericOperationGraphV1.Operation.INPUT_SRGB_RGBA)
            val decoded = NumericOperationGraphV1.Node(NumericOperationGraphV1.Operation.SRGB_TO_LINEAR, listOf(input))
            val premul = NumericOperationGraphV1.Node(NumericOperationGraphV1.Operation.PREMULTIPLY, listOf(decoded))
            val coverage = NumericOperationGraphV1.Node(NumericOperationGraphV1.Operation.COVERAGE_F32, listOf(premul))
            val clamped = NumericOperationGraphV1.Node(NumericOperationGraphV1.Operation.CLAMP_01, listOf(coverage))
            return NumericOperationGraphV1.Node(NumericOperationGraphV1.Operation.QUANTIZE_UNORM8, listOf(clamped))
        }
    }

    public class OpacityV1(public val child: MaterialPlanRef) : MaterialProgramPlan {
        override val versionI32: Int = 1
        override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId("w5a-opacity-v1")
        override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 {
            val input = NumericOperationGraphV1.Node(NumericOperationGraphV1.Operation.INPUT_SRGB_RGBA)
            val opacity = NumericOperationGraphV1.Node(NumericOperationGraphV1.Operation.OPACITY_F32, listOf(input))
            val coverage = NumericOperationGraphV1.Node(NumericOperationGraphV1.Operation.COVERAGE_F32, listOf(opacity))
            val clamped = NumericOperationGraphV1.Node(NumericOperationGraphV1.Operation.CLAMP_01, listOf(coverage))
            return NumericOperationGraphV1.Node(NumericOperationGraphV1.Operation.QUANTIZE_UNORM8, listOf(clamped))
        }
    }
}

/** Immutable value/binding half of a W5a material. */
public sealed interface MaterialBindingPlan {
    public val versionI32: Int

    public data object EmptyV1 : MaterialBindingPlan { override val versionI32: Int = 1 }

    public class SolidRgbaF32V1 private constructor(private val rgbaF32: ColorF32) : MaterialBindingPlan {
        override val versionI32: Int = 1
        public fun copyRgbaF32(): ColorF32 = ColorF32.of(rgbaF32.red, rgbaF32.green, rgbaF32.blue, rgbaF32.alpha)
        public companion object {
            public fun of(rgbaF32: ColorF32): SolidRgbaF32V1 = SolidRgbaF32V1(
                ColorF32.of(rgbaF32.red, rgbaF32.green, rgbaF32.blue, rgbaF32.alpha),
            )
        }
    }

    public class OpacityF32V1 private constructor(public val alphaF32: Float) : MaterialBindingPlan {
        override val versionI32: Int = 1
        init { require(alphaF32.isFinite() && alphaF32 in 0f..1f) }
        public companion object {
            public fun of(alphaF32: Float): OpacityF32V1 = OpacityF32V1(alphaF32)
        }
    }
}

public data class MaterialPlanEntry(public val program: MaterialProgramPlan, public val bindings: MaterialBindingPlan)

public class MaterialPlanTable private constructor(entries: List<MaterialPlanEntry>) {
    private val storedEntries: List<MaterialPlanEntry> = immutableList(entries)
    public val sizeI32: Int get() = storedEntries.size

    public fun entry(ref: MaterialPlanRef): MaterialPlanEntry = storedEntries.getOrElse(ref.indexI32) {
        throw IllegalArgumentException("Material plan reference is outside the sealed table")
    }

    public fun entries(): List<MaterialPlanEntry> = storedEntries

    public companion object {
        public fun of(entries: List<MaterialPlanEntry>): MaterialPlanTable {
            require(entries.isNotEmpty()) { "A material table must contain a root entry" }
            entries.forEachIndexed { index, entry ->
                when (val program = entry.program) {
                    MaterialProgramPlan.TransparentV1 -> require(entry.bindings is MaterialBindingPlan.EmptyV1) {
                        "Transparent programs require empty bindings"
                    }
                    MaterialProgramPlan.SolidLinearPremulV1 -> require(entry.bindings is MaterialBindingPlan.SolidRgbaF32V1) {
                        "Solid programs require RGBA bindings"
                    }
                    is MaterialProgramPlan.OpacityV1 -> {
                        require(entry.bindings is MaterialBindingPlan.OpacityF32V1) {
                            "Opacity programs require opacity bindings"
                        }
                        require(program.child.indexI32 < index) { "Opacity children must precede their parent" }
                    }
                }
            }
            return MaterialPlanTable(entries)
        }
    }
}

/** Closed draw authority: W5 material references cannot coexist with legacy colours. */
public sealed interface PlanDrawMaterialAuthority {
    public data class MaterialV1(public val ref: MaterialPlanRef) : PlanDrawMaterialAuthority

    public class LegacyColorV1 private constructor(private val colorF32: ColorF32) : PlanDrawMaterialAuthority {
        public fun copyColorF32(): ColorF32 = ColorF32.of(colorF32.red, colorF32.green, colorF32.blue, colorF32.alpha)
        public companion object {
            public fun of(colorF32: ColorF32): LegacyColorV1 = LegacyColorV1(
                ColorF32.of(colorF32.red, colorF32.green, colorF32.blue, colorF32.alpha),
            )
        }
    }
}
