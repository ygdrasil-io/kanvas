package org.graphiks.kanvas.gpu.plan

/** Handle-free raw V2 binding layout, shared by capability sealing and native packing. */
public class RawMaterialRequirementsV2 private constructor(
    public val bindingCountI32: Int,
    public val uniformByteCountI64: Long,
) {
    public companion object {
        public const val BINDING_STRIDE_BYTES_I64: Long = 16L

        public fun of(table: MaterialPlanTable, root: MaterialPlanRef): RawMaterialRequirementsV2 {
            require(root.indexI32 in 0 until table.sizeI32)
            var indexI32 = root.indexI32
            var countI32 = 1
            while (table.entry(MaterialPlanRef(indexI32)).bindings is MaterialBindingPlan.OpacityF32V1) {
                require(indexI32 > 0) { "Raw material opacity requires its captured child" }
                indexI32--
                countI32 = Math.addExact(countI32, 1)
            }
            val gradient = table.entry(MaterialPlanRef(indexI32)).bindings is MaterialBindingPlan.GradientV1
            return RawMaterialRequirementsV2(countI32, Math.addExact(
                Math.multiplyExact(countI32.toLong(), BINDING_STRIDE_BYTES_I64),
                when (table.entry(MaterialPlanRef(indexI32)).bindings) {
                    is MaterialBindingPlan.GradientV2 -> 64L + requireNotNull(table.coordinatesV2(root)).uniformByteSizeI64
                    is MaterialBindingPlan.LinearGradientV1 -> 112L // common 80 + 2 sealed scalar vectors
                    is MaterialBindingPlan.ConicalGradientV1 -> 176L // common 80 + 4 scalar vectors + 2 flag vectors
                    is MaterialBindingPlan.SweepGradientV1 -> 96L
                    else -> if (gradient) 80L else 0L
                }))
        }
    }
}
