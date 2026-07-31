package org.graphiks.kanvas.gpu.renderer.vertices

import java.util.Collections

/** Canonical terminal refusals for the prepared DrawVertices and DrawMesh route. */
object GPUPreparedVerticesRefusalCodes {
    const val Topology = "unsupported.vertices.topology"
    const val PositionCount = "unsupported.vertices.position_count"
    const val AttributeCount = "unsupported.vertices.attribute_count"
    const val NonFinite = "unsupported.vertices.non_finite"
    const val IndexOutOfRange = "unsupported.vertices.index_out_of_range"
    const val IndexFormat = "unsupported.vertices.index_format"
    const val AttributeLayout = "unsupported.vertices.attribute_layout"
    const val Transform = "unsupported.vertices.transform"
    const val ColorConversion = "unsupported.vertices.color_conversion_unvalidated"
    const val PrimitiveBlender = "unsupported.vertices.primitive_blender_unregistered"
    const val Material = "unsupported.vertices.material"
    const val Budget = "unsupported.vertices.budget"
    const val MeshBounds = "unsupported.mesh.bounds"
    const val MeshProgramUnregistered = "unsupported.mesh.program_unregistered"
    const val MeshProgramCpuUnavailable = "unsupported.mesh.program_cpu_not_available"
    const val MeshProgramWgslUnavailable = "unsupported.mesh.program_wgsl_not_available"
    const val MeshProgramWgslValidation = "unsupported.mesh.program_wgsl_validation"
    const val MeshProgramAbi = "unsupported.mesh.program_abi"
    const val MeshProgramChild = "unsupported.mesh.program_child"
    const val MeshProgramResource = "unsupported.mesh.program_resource"
    const val MeshBudget = "unsupported.mesh.budget"

    val ALL: Set<String> = Collections.unmodifiableSet(linkedSetOf(
        Topology,
        PositionCount,
        AttributeCount,
        NonFinite,
        IndexOutOfRange,
        IndexFormat,
        AttributeLayout,
        Transform,
        ColorConversion,
        PrimitiveBlender,
        Material,
        Budget,
        MeshBounds,
        MeshProgramUnregistered,
        MeshProgramCpuUnavailable,
        MeshProgramWgslUnavailable,
        MeshProgramWgslValidation,
        MeshProgramAbi,
        MeshProgramChild,
        MeshProgramResource,
        MeshBudget,
    ))
}
