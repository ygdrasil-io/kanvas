package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.wgsl.WgslBindingVisibility
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslModuleAbi
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslModuleAbiBinding
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslModuleAbiEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslModuleAbiLayout
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslModuleAbiMember

/** Complete ABI declarations for the Full, Scalar, and LCD validation-module topologies. */
object GPUBlendFormulaModuleAbi {
    private val entryPoints = listOf(
        WgslModuleAbiEntryPoint(name = "vs_main", stage = "vertex", workgroupSize = null),
        WgslModuleAbiEntryPoint(name = "fs_main", stage = "fragment", workgroupSize = null),
    )
    private val baseBindings = listOf(
        uniformBinding(group = 0, binding = 0, name = "blendValidationUniforms", minBindingSize = 16),
        sampledTexture(group = 1, binding = 1, name = "srcTexture"),
        sampler(group = 1, binding = 2, name = "srcSampler"),
        sampledTexture(group = 1, binding = 3, name = "dstTexture"),
        sampler(group = 1, binding = 4, name = "dstSampler"),
    )
    private val coverageBindings = listOf(
        sampledTexture(group = 1, binding = 5, name = "coverageTexture"),
        sampler(group = 1, binding = 6, name = "coverageSampler"),
    )
    private val layouts = listOf(
        WgslModuleAbiLayout(
            structName = "BlendValidationUniforms",
            addressSpace = "uniform",
            size = 16,
            alignment = 16,
            members = listOf(
                WgslModuleAbiMember(
                    name = "_pad",
                    type = "vec4<u32>",
                    offset = 0,
                    size = 16,
                    alignment = 16,
                    stride = null,
                ),
            ),
        ),
    )
    private val declarations = mapOf(
        GPUBlendCoverageKind.Full to declaration("blend-validation-full@v1", baseBindings),
        GPUBlendCoverageKind.Scalar to declaration("blend-validation-scalar@v1", baseBindings + coverageBindings),
        GPUBlendCoverageKind.LCD to declaration("blend-validation-lcd@v1", baseBindings + coverageBindings),
    )

    fun declaredFor(coverageKind: GPUBlendCoverageKind): WgslModuleAbi =
        requireNotNull(declarations[coverageKind]) { "Missing blend formula ABI declaration for $coverageKind" }

    private fun declaration(moduleId: String, bindings: List<WgslModuleAbiBinding>) =
        WgslModuleAbi(moduleId = moduleId, entryPoints = entryPoints, bindings = bindings, layouts = layouts)

    private fun uniformBinding(group: Int, binding: Int, name: String, minBindingSize: Int) =
        WgslModuleAbiBinding(
            group = group,
            binding = binding,
            name = name,
            resourceKind = "uniformBuffer",
            visibilityState = WgslBindingVisibility.Unavailable,
            access = "read",
            sampleType = null,
            viewDimension = null,
            storageFormat = null,
            minBindingSize = minBindingSize,
        )

    private fun sampledTexture(group: Int, binding: Int, name: String) =
        WgslModuleAbiBinding(
            group = group,
            binding = binding,
            name = name,
            resourceKind = "sampledTexture",
            visibilityState = WgslBindingVisibility.Unavailable,
            access = "read",
            sampleType = "float",
            viewDimension = "2d",
            storageFormat = null,
            minBindingSize = null,
        )

    private fun sampler(group: Int, binding: Int, name: String) =
        WgslModuleAbiBinding(
            group = group,
            binding = binding,
            name = name,
            resourceKind = "sampler",
            visibilityState = WgslBindingVisibility.Unavailable,
            access = "read",
            sampleType = null,
            viewDimension = null,
            storageFormat = null,
            minBindingSize = null,
        )
}
