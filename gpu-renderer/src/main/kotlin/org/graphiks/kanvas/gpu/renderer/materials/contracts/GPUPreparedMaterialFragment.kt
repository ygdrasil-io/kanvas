package org.graphiks.kanvas.gpu.renderer.materials.contracts

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.collections.CanonicalIdentityDigestEncoder
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

internal data class GPUPreparedMaterialFragmentIdentity(
    val fragmentHash: String,
    val abiHash: String,
)

class GPUPreparedMaterialFragment private constructor(
    val declarationsWgsl: String,
    val evaluationFunctionWgsl: String,
    val uniformBinding: GPUPreparedMaterialUniformBinding?,
    sampledBindings: List<GPUPreparedMaterialSampledBinding>,
    internal val authenticatedIdentity: GPUPreparedMaterialFragmentIdentity,
) {
    val evaluationFunction: String = MATERIAL_EVALUATION_FUNCTION
    val colorContract: GPUPreparedMaterialColorContract =
        GPUPreparedMaterialColorContract.LinearPremultipliedRgba
    val coordinateContract: GPUPreparedMaterialCoordinateContract =
        GPUPreparedMaterialCoordinateContract.LocalPosition2D
    val sampledBindings: List<GPUPreparedMaterialSampledBinding> =
        immutableList(sampledBindings)
    val fragmentHash: String = authenticatedIdentity.fragmentHash
    val abiHash: String = authenticatedIdentity.abiHash

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

    internal companion object {
        fun createAuthenticated(
            declarationsWgsl: String,
            evaluationFunctionWgsl: String,
            uniformBinding: GPUPreparedMaterialUniformBinding?,
            sampledBindings: List<GPUPreparedMaterialSampledBinding>,
            reflectedAbiFacts: List<String>,
        ): GPUPreparedMaterialFragment {
            val sampledSnapshot = immutableList(sampledBindings)
            val identity = authenticatedIdentity(
                declarationsWgsl = declarationsWgsl,
                evaluationFunctionWgsl = evaluationFunctionWgsl,
                uniformBinding = uniformBinding,
                sampledBindings = sampledSnapshot,
                reflectedAbiFacts = reflectedAbiFacts,
            )
            return GPUPreparedMaterialFragment(
                declarationsWgsl = declarationsWgsl,
                evaluationFunctionWgsl = evaluationFunctionWgsl,
                uniformBinding = uniformBinding,
                sampledBindings = sampledSnapshot,
                authenticatedIdentity = identity,
            )
        }

        fun authenticatedIdentity(
            declarationsWgsl: String,
            evaluationFunctionWgsl: String,
            uniformBinding: GPUPreparedMaterialUniformBinding?,
            sampledBindings: List<GPUPreparedMaterialSampledBinding>,
            reflectedAbiFacts: List<String>,
        ): GPUPreparedMaterialFragmentIdentity {
            val fragmentSource = declarationsWgsl + "\n\n" + evaluationFunctionWgsl
            val abiHash = CanonicalIdentityDigestEncoder("prepared-material-fragment-abi-v1")
                .text("evaluationFunction", MATERIAL_EVALUATION_FUNCTION)
                .text(
                    "colorContract",
                    GPUPreparedMaterialColorContract.LinearPremultipliedRgba.name,
                )
                .text(
                    "coordinateContract",
                    GPUPreparedMaterialCoordinateContract.LocalPosition2D.name,
                )
                .texts(
                    "uniformBinding",
                    listOfNotNull(
                        uniformBinding?.let {
                            "${it.group}:${it.binding}:${it.minBindingSizeBytes}"
                        },
                    ),
                )
                .texts(
                    "sampledBindings",
                    sampledBindings.map {
                        "${it.resourceIndex}:${it.textureGroup}:${it.textureBinding}:" +
                            "${it.samplerGroup}:${it.samplerBinding}"
                    },
                )
                .texts("reflectedAbiFacts", reflectedAbiFacts)
                .digestIdentity()
            return GPUPreparedMaterialFragmentIdentity(
                fragmentHash = sha256Hex(fragmentSource.encodeToByteArray()),
                abiHash = abiHash,
            )
        }
    }
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private const val MATERIAL_EVALUATION_FUNCTION = "kanvas_evaluate_material"
