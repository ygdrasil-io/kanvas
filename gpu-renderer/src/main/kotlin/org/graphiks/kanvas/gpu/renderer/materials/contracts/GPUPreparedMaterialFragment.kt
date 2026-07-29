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

class GPUPreparedMaterialFragment internal constructor(
    val declarationsWgsl: String,
    val evaluationFunctionWgsl: String,
    val evaluationFunction: String,
    val uniformBinding: GPUPreparedMaterialUniformBinding?,
    sampledBindings: List<GPUPreparedMaterialSampledBinding>,
    val colorContract: GPUPreparedMaterialColorContract,
    val coordinateContract: GPUPreparedMaterialCoordinateContract,
    val fragmentHash: String,
    val abiHash: String,
) {
    val sampledBindings: List<GPUPreparedMaterialSampledBinding> =
        immutableList(sampledBindings)

    init {
        require(evaluationFunction == "kanvas_evaluate_material")
        require(fragmentHash.matches(Regex("[0-9a-f]{64}")))
        require(abiHash.isNotBlank())
        require(this.sampledBindings.map { it.resourceIndex } == this.sampledBindings.indices.toList())
        require(
            this.sampledBindings.flatMap {
                listOf(
                    it.textureGroup to it.textureBinding,
                    it.samplerGroup to it.samplerBinding,
                )
            }.distinct().size == this.sampledBindings.size * 2,
        )
    }
}
