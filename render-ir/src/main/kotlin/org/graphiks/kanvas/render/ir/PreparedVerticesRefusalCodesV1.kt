package org.graphiks.kanvas.render.ir

import java.util.Collections

/** Canonical terminal refusals for the prepared DrawVertices and DrawMesh route. */
public object PreparedVerticesRefusalCodesV1 {
    public const val Topology = "unsupported.vertices.topology"
    public const val PositionCount = "unsupported.vertices.position_count"
    public const val AttributeCount = "unsupported.vertices.attribute_count"
    public const val NonFinite = "unsupported.vertices.non_finite"
    public const val IndexOutOfRange = "unsupported.vertices.index_out_of_range"
    public const val IndexFormat = "unsupported.vertices.index_format"
    public const val AttributeLayout = "unsupported.vertices.attribute_layout"
    public const val Transform = "unsupported.vertices.transform"
    public const val ColorConversion = "unsupported.vertices.color_conversion_unvalidated"
    public const val PrimitiveBlender = "unsupported.vertices.primitive_blender_unregistered"
    public const val Material = "unsupported.vertices.material"
    public const val Budget = "unsupported.vertices.budget"
    public const val ClipCoverage = "unsupported.vertices.clip_coverage"
    public const val MeshBounds = "unsupported.mesh.bounds"
    public const val MeshProgramUnregistered = "unsupported.mesh.program_unregistered"
    public const val MeshProgramCpuUnavailable = "unsupported.mesh.program_cpu_not_available"
    public const val MeshProgramWgslUnavailable = "unsupported.mesh.program_wgsl_not_available"
    public const val MeshProgramWgslValidation = "unsupported.mesh.program_wgsl_validation"
    public const val MeshProgramAbi = "unsupported.mesh.program_abi"
    public const val MeshProgramChild = "unsupported.mesh.program_child"
    public const val MeshProgramResource = "unsupported.mesh.program_resource"
    public const val MeshBudget = "unsupported.mesh.budget"

    public val ALL: Set<String> = Collections.unmodifiableSet(linkedSetOf(
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
        ClipCoverage,
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
