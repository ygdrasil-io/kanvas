package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.RectF32

internal object ColorSourceProofCompilerV1 {
    fun seal(table: MaterialPlanTable, root: MaterialPlanRef, coordinates: SourceCoordinatesV4,
        deviceBoundsF32: RectF32): ColorSourceProofResultV1 {
        if (coordinates != SourceCoordinatesV4.None || listOf(deviceBoundsF32.left,deviceBoundsF32.top,
                deviceBoundsF32.right,deviceBoundsF32.bottom).any { !it.isFinite() })
            return ColorSourceProofResultV1.Refused(W5fPlanDiagnostics.Unpromoted)
        val owners = mutableListOf<MaterialBindingPlan>()
        var firstI32 = root.indexI32
        while (table.entry(MaterialPlanRef(firstI32)).bindings.let {
                it is MaterialBindingPlan.OpacityF32V1 || it is ColorFilterBindingV4 }) {
            if (firstI32 == 0) return ColorSourceProofResultV1.Refused(W5fPlanDiagnostics.Schema)
            firstI32--
        }
        val words = linkedMapOf<Long,Int>()
        val tables = linkedMapOf<Long,org.graphiks.kanvas.render.ir.ImmutableUBytes>()
        var values: List<ColorOperationGraphV1.Scalar>? = null
        var offsetU32 = 0L
        for (indexI32 in firstI32..root.indexI32) {
            val binding = table.entry(MaterialPlanRef(indexI32)).bindings
            owners += binding
            values = when (binding) {
                MaterialBindingPlan.EmptyV1 -> List(4) { ColorOperationGraphV1.constant(0f) }
                is MaterialBindingPlan.SolidRgbaF32V1 -> {
                    val color = binding.copyRgbaF32()
                    val channels = listOf(color.red,color.green,color.blue,color.alpha)
                    if (channels.any { !it.isFinite() || it !in 0f..1f }) return ColorSourceProofResultV1.Refused(W5fPlanDiagnostics.Schema)
                    channels.forEachIndexed { channelI32, valueF32 -> words[offsetU32+channelI32]=valueF32.toRawBits() }
                    val alpha = ColorOperationGraphV1.Scalar.DynamicF32(offsetU32+3)
                    List(4) { if(it==3) alpha else ColorOperationGraphV1.Scalar.Multiply(
                        ColorOperationGraphV1.eotf(ColorOperationGraphV1.Scalar.DynamicF32(offsetU32+it)),alpha) }
                }
                is MaterialBindingPlan.OpacityF32V1 -> {
                    words[offsetU32] = binding.alphaF32.toRawBits()
                    val alpha = ColorOperationGraphV1.Scalar.DynamicF32(offsetU32)
                    requireNotNull(values).map { ColorOperationGraphV1.Scalar.Multiply(it,alpha) }
                }
                is ColorFilterBindingV4 -> {
                    binding.execution.forEachWord { offset, value -> words[Math.addExact(offsetU32, offset)] = value }
                    binding.execution.forEachTable { offset, table -> tables[Math.addExact(offsetU32,offset)] = table }
                    binding.execution.copyOperationGraph().bindInput(
                        ColorOperationGraphV1(requireNotNull(values)), offsetU32).outputs
                }
                else -> return ColorSourceProofResultV1.Refused(W5fPlanDiagnostics.Unpromoted)
            }
            offsetU32 = Math.addExact(offsetU32, if (binding is ColorFilterBindingV4)
                binding.execution.dynamicByteCountI64 / 4L else 4L)
        }
        val proof = ColorSourceProofV1.issue(table,root,coordinates,deviceBoundsF32,
            ColorOperationGraphV1(requireNotNull(values)),owners,words,tables)
            ?: return ColorSourceProofResultV1.Refused(W5fPlanDiagnostics.NumericDomainUnbounded)
        return ColorSourceProofResultV1.Ready(proof)
    }
}
