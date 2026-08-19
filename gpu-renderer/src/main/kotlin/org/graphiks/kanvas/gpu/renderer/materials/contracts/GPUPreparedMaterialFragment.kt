package org.graphiks.kanvas.gpu.renderer.materials.contracts

import org.graphiks.kanvas.gpu.renderer.collections.immutableList

enum class GPUPreparedMaterialColorContract {
    LinearPremultipliedRgba,
}

enum class GPUPreparedMaterialCoordinateContract {
    LocalPosition2D,
}

data class GPUPreparedMaterialUniformBinding(
    val group: Int = 1,
    val binding: Int = 0,
    val minBindingSizeBytes: Int,
)

data class GPUPreparedMaterialSampledBinding(
    val resourceIndex: Int,
    val textureGroup: Int = 1,
    val textureBinding: Int,
    val samplerGroup: Int = 1,
    val samplerBinding: Int,
)

class GPUPreparedMaterialFragment private constructor(
    admission: GPUPreparedMaterialFragmentAdmission,
) {
    val declarationsWgsl: String = admission.declarationsWgsl
    val evaluationFunctionWgsl: String = admission.evaluationFunctionWgsl
    val uniformBinding: GPUPreparedMaterialUniformBinding? = admission.uniformBinding
    val evaluationFunction: String = MATERIAL_EVALUATION_FUNCTION
    val colorContract: GPUPreparedMaterialColorContract =
        GPUPreparedMaterialColorContract.LinearPremultipliedRgba
    val coordinateContract: GPUPreparedMaterialCoordinateContract =
        GPUPreparedMaterialCoordinateContract.LocalPosition2D
    val sampledBindings: List<GPUPreparedMaterialSampledBinding> =
        immutableList(admission.sampledBindings)
    val fragmentHash: String = admission.fragmentHash()
    val abiHash: String = admission.fragmentAbiHash()

    init {
        require(fragmentHash.matches(Regex("[0-9a-f]{64}")))
        require(abiHash.matches(Regex("sha256:[0-9a-f]{64}")))
        require(this.sampledBindings.map { it.resourceIndex } == this.sampledBindings.indices.toList())
        require(
            this.sampledBindings.all { binding ->
                val textureBinding = Math.addExact(
                    1,
                    Math.multiplyExact(binding.resourceIndex, 2),
                )
                binding.textureGroup == 1 &&
                    binding.textureBinding == textureBinding &&
                    binding.samplerGroup == 1 &&
                    binding.samplerBinding == Math.addExact(textureBinding, 1)
            },
        )
        require(
            this.sampledBindings.flatMap {
                listOf(
                    it.textureGroup to it.textureBinding,
                    it.samplerGroup to it.samplerBinding,
                )
            }.distinct().size == this.sampledBindings.size * 2,
        )
    }

    companion object {
        @JvmSynthetic
        internal fun createAuthenticated(
            admission: GPUPreparedMaterialFragmentAdmission,
        ): GPUPreparedMaterialFragment = GPUPreparedMaterialFragment(admission)
    }
}

private const val MATERIAL_EVALUATION_FUNCTION = "kanvas_evaluate_material"
