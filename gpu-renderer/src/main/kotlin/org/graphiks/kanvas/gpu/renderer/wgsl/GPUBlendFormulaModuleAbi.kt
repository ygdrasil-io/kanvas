package org.graphiks.kanvas.gpu.renderer.wgsl

import org.graphiks.kanvas.gpu.renderer.materials.GPUBlendCoverageKind

/** Entry-point facts that wgsl4k represents in its lowered module. */
data class WgslModuleAbiEntryPoint(
    val name: String,
    val stage: String,
    val workgroupSize: List<Int>?,
)

/** Resource-binding facts that wgsl4k represents in its lowered module. */
data class WgslModuleAbiBinding(
    val group: Int,
    val binding: Int,
    val name: String,
    val resourceKind: String,
    val access: String?,
    val sampleType: String?,
    val viewDimension: String?,
    val storageFormat: String?,
    val minBindingSize: Int?,
)

/** Uniform or storage member facts calculated by the wgsl4k layouter. */
data class WgslModuleAbiMember(
    val name: String,
    val type: String,
    val offset: Int,
    val size: Int,
    val alignment: Int,
    val stride: Int?,
)

/** Uniform or storage layout facts calculated by the wgsl4k layouter. */
data class WgslModuleAbiLayout(
    val structName: String,
    val addressSpace: String,
    val size: Int,
    val alignment: Int,
    val members: List<WgslModuleAbiMember>,
)

/** Complete declared module ABI limited to facts represented by wgsl4k reflection. */
data class WgslModuleAbi(
    val moduleId: String,
    val entryPoints: List<WgslModuleAbiEntryPoint>,
    val bindings: List<WgslModuleAbiBinding>,
    val layouts: List<WgslModuleAbiLayout>,
)

/** Fail-closed result of comparing a reflected WGSL module with its declared ABI. */
sealed interface WgslModuleAbiValidationResult {
    data object Match : WgslModuleAbiValidationResult

    data class Mismatch(val diagnostics: List<String>) : WgslModuleAbiValidationResult
}

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
            access = "read",
            sampleType = null,
            viewDimension = null,
            storageFormat = null,
            minBindingSize = null,
        )
}

/** Compares every reflected ABI fact and rejects missing, extra, or changed facts. */
fun validateWgslModuleAbi(
    declared: WgslModuleAbi,
    reflected: WgslReflectionReport,
): WgslModuleAbiValidationResult {
    val actual = reflected.toModuleAbi(declared.moduleId)
    val diagnostics = buildList {
        if (!reflected.validation.success) {
            add("${declared.moduleId}: wgsl4k validation failed: ${reflected.validation.diagnostics}")
        }
        if (reflected.unsupportedFeatures.isNotEmpty()) {
            add("${declared.moduleId}: wgsl4k reflection has unsupported features: ${reflected.unsupportedFeatures}")
        }
        addAll(compareEntryPoints(declared.entryPoints, actual.entryPoints))
        addAll(compareBindings(declared.bindings, actual.bindings))
        addAll(compareLayouts(declared.layouts, actual.layouts))
    }
    return if (diagnostics.isEmpty()) {
        WgslModuleAbiValidationResult.Match
    } else {
        WgslModuleAbiValidationResult.Mismatch(diagnostics)
    }
}

private fun WgslReflectionReport.toModuleAbi(moduleId: String): WgslModuleAbi =
    WgslModuleAbi(
        moduleId = moduleId,
        entryPoints = entryPoints.map { entryPoint ->
            WgslModuleAbiEntryPoint(entryPoint.name, entryPoint.stage, entryPoint.workgroupSize)
        },
        bindings = bindings.map { binding ->
            WgslModuleAbiBinding(
                group = binding.group,
                binding = binding.binding,
                name = binding.name,
                resourceKind = binding.resourceKind,
                access = binding.access,
                sampleType = binding.sampleType,
                viewDimension = binding.viewDimension,
                storageFormat = binding.storageFormat,
                minBindingSize = binding.minBindingSize,
            )
        },
        layouts = layouts.map { layout ->
            WgslModuleAbiLayout(
                structName = layout.structName,
                addressSpace = layout.addressSpace,
                size = layout.size,
                alignment = layout.alignment,
                members = layout.members.map { member ->
                    WgslModuleAbiMember(
                        name = member.name,
                        type = member.type,
                        offset = member.offset,
                        size = member.size,
                        alignment = member.alignment,
                        stride = member.stride,
                    )
                },
            )
        },
    )

private fun compareEntryPoints(
    declared: List<WgslModuleAbiEntryPoint>,
    actual: List<WgslModuleAbiEntryPoint>,
): List<String> = exactListDiagnostics("entryPoints", declared, actual)

private fun compareBindings(
    declared: List<WgslModuleAbiBinding>,
    actual: List<WgslModuleAbiBinding>,
): List<String> {
    val declaredBySlot = declared.associateBy { it.group to it.binding }
    val actualBySlot = actual.associateBy { it.group to it.binding }
    return buildList {
        addAll(duplicateSlotDiagnostics("declared", declared))
        addAll(duplicateSlotDiagnostics("reflected", actual))
        (declaredBySlot.keys - actualBySlot.keys).sortedWith(slotComparator).forEach { (group, binding) ->
            add("missing reflected binding group=$group,binding=$binding")
        }
        (actualBySlot.keys - declaredBySlot.keys).sortedWith(slotComparator).forEach { (group, binding) ->
            add("unexpected reflected binding group=$group,binding=$binding")
        }
        (declaredBySlot.keys intersect actualBySlot.keys).sortedWith(slotComparator).forEach { slot ->
            val expected = requireNotNull(declaredBySlot[slot])
            val reflected = requireNotNull(actualBySlot[slot])
            if (expected != reflected) {
                val fields = bindingFieldMismatches(expected, reflected).joinToString()
                add("binding group=${slot.first},binding=${slot.second} mismatched fields: $fields")
            }
        }
    }
}

private fun compareLayouts(
    declared: List<WgslModuleAbiLayout>,
    actual: List<WgslModuleAbiLayout>,
): List<String> = exactListDiagnostics("layouts", declared, actual)

private fun <T> exactListDiagnostics(label: String, declared: List<T>, actual: List<T>): List<String> =
    if (declared == actual) emptyList() else listOf("$label mismatch: declared=$declared reflected=$actual")

private fun duplicateSlotDiagnostics(label: String, bindings: List<WgslModuleAbiBinding>): List<String> =
    bindings.groupBy { it.group to it.binding }
        .filterValues { it.size != 1 }
        .keys
        .sortedWith(slotComparator)
        .map { (group, binding) -> "$label ABI has duplicate binding group=$group,binding=$binding" }

private fun bindingFieldMismatches(
    declared: WgslModuleAbiBinding,
    actual: WgslModuleAbiBinding,
): List<String> = buildList {
    if (declared.name != actual.name) add("name declared=${declared.name} reflected=${actual.name}")
    if (declared.resourceKind != actual.resourceKind) {
        add("resourceKind declared=${declared.resourceKind} reflected=${actual.resourceKind}")
    }
    if (declared.access != actual.access) add("access declared=${declared.access} reflected=${actual.access}")
    if (declared.sampleType != actual.sampleType) {
        add("sampleType declared=${declared.sampleType} reflected=${actual.sampleType}")
    }
    if (declared.viewDimension != actual.viewDimension) {
        add("viewDimension declared=${declared.viewDimension} reflected=${actual.viewDimension}")
    }
    if (declared.storageFormat != actual.storageFormat) {
        add("storageFormat declared=${declared.storageFormat} reflected=${actual.storageFormat}")
    }
    if (declared.minBindingSize != actual.minBindingSize) {
        add("minBindingSize declared=${declared.minBindingSize} reflected=${actual.minBindingSize}")
    }
}

private val slotComparator = compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second }
