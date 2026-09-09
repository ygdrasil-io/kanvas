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
        override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.transparent()
    }

    public data object SolidLinearPremulV1 : MaterialProgramPlan {
        override val versionI32: Int = 1
        override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId("w5a-solid-linear-premul-v1")
        override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.solid()
    }

    /** Child topology is code shape, while alpha remains a dynamic binding value. */
    public class OpacityV1(public val child: MaterialProgramPlan) : MaterialProgramPlan {
        override val versionI32: Int = 1
        override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId("w5a-opacity-v1(${child.structuralId.value})")
        override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.opacity()
    }
}

/** Immutable value/binding half of a W5a material. */
public sealed interface MaterialBindingPlan {
    public val versionI32: Int

    public data object EmptyV1 : MaterialBindingPlan { override val versionI32: Int = 1 }

    /** Exact public Solid input, in straight sRGB; the program performs conversion and premultiplication. */
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
        public companion object { public fun of(alphaF32: Float): OpacityF32V1 = OpacityF32V1(alphaF32) }
    }
}

public data class MaterialPlanEntry(public val program: MaterialProgramPlan, public val bindings: MaterialBindingPlan)

public class MaterialPlanTable private constructor(entries: List<MaterialPlanEntry>) {
    private data class StoredEntry(val programIndex: Int, val bindings: MaterialBindingPlan)
    private val storedPrograms: List<MaterialProgramPlan>
    private val storedEntries: List<StoredEntry>

    init {
        val programIndexById = linkedMapOf<MaterialProgramPlanId, Int>()
        val programs = mutableListOf<MaterialProgramPlan>()
        storedEntries = immutableList(entries.map { entry ->
            val index = programIndexById.getOrPut(entry.program.structuralId) {
                programs += entry.program
                programs.lastIndex
            }
            require(programs[index]::class == entry.program::class) { "Structural program IDs must not alias different programs" }
            StoredEntry(index, entry.bindings)
        })
        storedPrograms = immutableList(programs)
    }
    public val sizeI32: Int get() = storedEntries.size
    /** Number of interned code-shaped programs, deliberately independent from bindings. */
    public val programCountI32: Int get() = storedPrograms.size

    public fun entry(ref: MaterialPlanRef): MaterialPlanEntry = storedEntries.getOrElse(ref.indexI32) {
        throw IllegalArgumentException("Material plan reference is outside the sealed table")
    }.let { MaterialPlanEntry(storedPrograms[it.programIndex], it.bindings) }

    public fun entries(): List<MaterialPlanEntry> = storedEntries.indices.map { entry(MaterialPlanRef(it)) }

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
                        require(index > 0 && entries[index - 1].program.structuralId == program.child.structuralId) { "Opacity child topology must precede its parent" }
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
