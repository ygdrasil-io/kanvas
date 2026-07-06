package org.graphiks.kanvas.gpu.renderer.wgsl

import kotlinx.serialization.Serializable
import org.graphiks.wgsl.proc.Alignment
import org.graphiks.wgsl.proc.Layouter
import org.graphiks.wgsl.arena.Handle
import org.graphiks.wgsl.ir.AccessMode
import org.graphiks.wgsl.ir.ArraySize
import org.graphiks.wgsl.ir.EntryPoint
import org.graphiks.wgsl.ir.GlobalVariable
import org.graphiks.wgsl.ir.Module
import org.graphiks.wgsl.ir.ScalarKind
import org.graphiks.wgsl.ir.ShaderStage
import org.graphiks.wgsl.ir.StorageClass
import org.graphiks.wgsl.ir.Type
import org.graphiks.wgsl.ir.TypeInner
import org.graphiks.wgsl.ir.VectorSize
import org.graphiks.wgsl.ir.value

@Serializable
data class WgslReflectionReport(
    val sourceId: String,
    val moduleHash: String? = null,
    val validation: WgslValidationSummary = WgslValidationSummary(success = true),
    val entryPoints: List<WgslEntryPointReflection> = emptyList(),
    val bindings: List<WgslBindingReflection> = emptyList(),
    val layouts: List<WgslLayoutReflection> = emptyList(),
    val unsupportedFeatures: List<String> = emptyList(),
)

@Serializable
data class WgslValidationSummary(
    val success: Boolean,
    val diagnostics: List<WgslReflectionDiagnostic> = emptyList(),
)

@Serializable
data class WgslReflectionDiagnostic(
    val reason: String,
    val message: String,
)

@Serializable
data class WgslEntryPointReflection(
    val name: String,
    val stage: String,
    val workgroupSize: List<Int>? = null,
)

@Serializable
data class WgslBindingReflection(
    val group: Int,
    val binding: Int,
    val name: String,
    val resourceKind: String,
    val visibility: List<String> = emptyList(),
    val access: String? = null,
    val sampleType: String? = null,
    val viewDimension: String? = null,
    val storageFormat: String? = null,
    val minBindingSize: Int? = null,
)

@Serializable
data class WgslLayoutReflection(
    val structName: String,
    val addressSpace: String,
    val size: Int,
    val alignment: Int,
    val members: List<WgslLayoutMemberReflection>,
)

@Serializable
data class WgslLayoutMemberReflection(
    val name: String,
    val type: String,
    val offset: Int,
    val size: Int,
    val alignment: Int,
    val stride: Int? = null,
)

fun Module.reflectWgslModule(
    sourceId: String,
    moduleHash: String? = null,
): WgslReflectionReport {
    val layouter = Layouter().also { it.update(this) }
    val bindingReflections = globalVariables.toList()
        .filter { it.binding != null }
        .sortedWith(compareBy<GlobalVariable> { it.binding!!.group }.thenBy { it.binding!!.index })
        .map { it.toWgslBindingReflection(this, layouter) }

    return WgslReflectionReport(
        sourceId = sourceId,
        moduleHash = moduleHash,
        entryPoints = entryPoints.map { it.toWgslEntryPointReflection() },
        bindings = bindingReflections,
        layouts = reflectWgslLayouts(layouter),
        unsupportedFeatures = bindingReflections
            .filter { it.resourceKind == "unsupported" }
            .map { "wgsl.reflection.feature_unrepresented:${it.name}:${unrepresentedTypeName(it.name)}" },
    )
}

private fun EntryPoint.toWgslEntryPointReflection(): WgslEntryPointReflection =
    WgslEntryPointReflection(
        name = name,
        stage = when (stage) {
            ShaderStage.Vertex -> "vertex"
            ShaderStage.Fragment -> "fragment"
            ShaderStage.Compute -> "compute"
        },
        workgroupSize = workgroupSize,
    )

private fun GlobalVariable.toWgslBindingReflection(
    module: Module,
    layouter: Layouter,
): WgslBindingReflection {
    val binding = binding!!
    val type = module.types[type]
    val resourceKind = resourceKind(type)
    return WgslBindingReflection(
        group = binding.group,
        binding = binding.index,
        name = name,
        resourceKind = resourceKind,
        visibility = emptyList(),
        access = accessString(resourceKind),
        sampleType = sampleType(type),
        viewDimension = viewDimension(type),
        storageFormat = storageFormat(type),
        minBindingSize = minBindingSize(this.type, layouter),
    )
}

private fun GlobalVariable.resourceKind(type: Type): String =
    when (storageClass) {
        StorageClass.Uniform -> "uniformBuffer"
        StorageClass.Storage -> "storageBuffer"
        StorageClass.Handle -> handleResourceKind(type)
        else -> storageClass.name.lowerInitial()
    }

private fun handleResourceKind(type: Type): String {
    val name = (type.inner as? TypeInner.Opaque)?.name ?: return "unsupported"
    return when {
        name == "sampler" -> "sampler"
        name == "sampler_comparison" -> "comparisonSampler"
        name == "texture_external" -> "unsupported"
        name.startsWith("texture_storage") -> "storageTexture"
        name.startsWith("texture_multisampled") -> "multisampledTexture"
        name.startsWith("texture_") -> "sampledTexture"
        else -> "unsupported"
    }
}

private fun GlobalVariable.accessString(resourceKind: String): String? =
    when (storageClass) {
        StorageClass.Uniform -> "read"
        StorageClass.Storage -> when (accessMode ?: AccessMode.ReadWrite) {
            AccessMode.Read -> "read"
            AccessMode.Write -> "write"
            AccessMode.ReadWrite -> "read_write"
        }
        StorageClass.Handle -> when (resourceKind) {
            "sampledTexture", "multisampledTexture", "sampler", "comparisonSampler" -> "read"
            else -> null
        }
        else -> null
    }

private fun minBindingSize(handle: Handle<Type>, layouter: Layouter): Int? =
    layouter[handle].size.takeIf { it > 0 }

private fun sampleType(type: Type): String? {
    val name = (type.inner as? TypeInner.Opaque)?.name ?: return null
    val parameter = name.substringAfter("<", "").substringBefore(">", "")
    return when (parameter.substringBefore(",")) {
        "f32" -> "float"
        "i32" -> "sint"
        "u32" -> "uint"
        else -> null
    }
}

private fun viewDimension(type: Type): String? {
    val name = (type.inner as? TypeInner.Opaque)?.name ?: return null
    val base = name.substringBefore("<")
    val texture = base
        .removePrefix("texture_storage_")
        .removePrefix("texture_multisampled_")
        .removePrefix("texture_depth_multisampled_")
        .removePrefix("texture_depth_")
        .removePrefix("texture_")
    return when (texture) {
        "1d" -> "1d"
        "1d_array" -> "1d_array"
        "2d" -> "2d"
        "2d_array" -> "2d_array"
        "3d" -> "3d"
        "cube" -> "cube"
        "cube_array" -> "cube_array"
        else -> null
    }
}

private fun storageFormat(type: Type): String? {
    val name = (type.inner as? TypeInner.Opaque)?.name ?: return null
    if (!name.startsWith("texture_storage")) return null
    return name.substringAfter("<", "").substringBefore(",", "").ifBlank { null }
}

private fun Module.reflectWgslLayouts(layouter: Layouter): List<WgslLayoutReflection> {
    val layouts = mutableListOf<WgslLayoutReflection>()
    val seen = mutableSetOf<Pair<String, String>>()
    for (global in globalVariables) {
        val addressSpace = when (global.storageClass) {
            StorageClass.Uniform -> "uniform"
            StorageClass.Storage -> "storage"
            else -> null
        } ?: continue
        val structHandle = structHandleForLayout(global.type) ?: continue
        val layout = reflectStructLayout(structHandle, addressSpace, layouter)
        if (seen.add(layout.structName to layout.addressSpace)) {
            layouts.add(layout)
        }
    }
    return layouts
}

private fun Module.structHandleForLayout(handle: Handle<Type>): Handle<Type>? {
    return when (val inner = types[handle].inner) {
        is TypeInner.Struct -> handle
        is TypeInner.Array -> structHandleForLayout(inner.element)
        is TypeInner.Pointer -> structHandleForLayout(inner.base)
        else -> null
    }
}

private fun Module.reflectStructLayout(
    handle: Handle<Type>,
    addressSpace: String,
    layouter: Layouter,
): WgslLayoutReflection {
    val type = types[handle]
    val struct = type.inner as TypeInner.Struct
    var nextOffset = 0
    val members = struct.members.map { member ->
        val memberLayout = layouter[member.type]
        val offset = if (member.offset != 0) {
            member.offset
        } else {
            memberLayout.alignment.roundUp(nextOffset)
        }
        nextOffset = offset + memberLayout.size
        WgslLayoutMemberReflection(
            name = member.name,
            type = typeName(member.type),
            offset = offset,
            size = memberLayout.size,
            alignment = memberLayout.alignment.value,
            stride = stride(member.type, layouter),
        )
    }
    val layout = layouter[handle]
    return WgslLayoutReflection(
        structName = "struct_${handle.index}",
        addressSpace = addressSpace,
        size = layout.size,
        alignment = layout.alignment.value,
        members = members,
    )
}

private fun Module.typeName(handle: Handle<Type>): String {
    val type = types[handle]
    return when (val inner = type.inner) {
        is TypeInner.Scalar -> scalarName(inner.kind, inner.width)
        is TypeInner.Vector -> "vec${inner.size.value}<${typeName(inner.scalar)}>"
        is TypeInner.Matrix -> "mat${inner.columns.value}x${inner.rows.value}<${typeName(inner.scalar)}>"
        is TypeInner.Array -> {
            val size = when (val arraySize = inner.size) {
                is ArraySize.Constant -> ", ${arraySize.value}"
                is ArraySize.Dynamic -> ""
            }
            "array<${typeName(inner.element)}$size>"
        }
        is TypeInner.Atomic -> "atomic<${typeName(inner.inner)}>"
        is TypeInner.Struct -> "struct"
        is TypeInner.Pointer -> "ptr<${inner.addressSpace.name.lowerInitial()}, ${typeName(inner.base)}>"
        is TypeInner.ValuePointer -> "ptr<${typeName(inner.base)}>"
        is TypeInner.Opaque -> inner.name
        TypeInner.Error -> "error"
        is TypeInner.Abstract -> "abstract_${inner.scalar.name.lowerInitial()}"
    }
}

private fun scalarName(kind: ScalarKind, width: Int): String =
    when (kind) {
        ScalarKind.Bool -> "bool"
        ScalarKind.Sint, ScalarKind.S32 -> "i${width * 8}"
        ScalarKind.Uint, ScalarKind.U32 -> "u${width * 8}"
        ScalarKind.S16 -> "i16"
        ScalarKind.U16 -> "u16"
        ScalarKind.S64 -> "i64"
        ScalarKind.U64 -> "u64"
        ScalarKind.F16 -> "f16"
        ScalarKind.F32 -> "f32"
        ScalarKind.F64 -> "f64"
        ScalarKind.AbstractInt -> "abstract_int"
        ScalarKind.AbstractFloat -> "abstract_float"
    }

private fun Module.stride(handle: Handle<Type>, layouter: Layouter): Int? =
    when (val inner = types[handle].inner) {
        is TypeInner.Array -> {
            val element = layouter[inner.element]
            element.alignment.roundUp(element.size)
        }
        is TypeInner.Matrix -> {
            val scalar = layouter[inner.scalar]
            val rows = inner.rows.value
            val alignCount = if (rows == 3) 4 else rows
            Alignment(scalar.alignment.value * alignCount).roundUp(scalar.size * rows)
        }
        else -> null
    }

private fun String.lowerInitial(): String =
    if (isEmpty()) this else substring(0, 1).lowercase() + substring(1)

private fun Module.unrepresentedTypeName(globalName: String): String {
    val global = globalVariables.toList().firstOrNull { it.name == globalName } ?: return "unknown"
    return typeName(global.type)
}
