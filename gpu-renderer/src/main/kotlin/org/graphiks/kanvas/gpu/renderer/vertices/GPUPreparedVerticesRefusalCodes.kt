package org.graphiks.kanvas.gpu.renderer.vertices

import java.util.Collections

/** Canonical terminal refusals for the prepared DrawVertices and DrawMesh route. */
object GPUPreparedVerticesRefusalCodes {
    const val Topology = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.Topology
    const val PositionCount = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.PositionCount
    const val AttributeCount = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.AttributeCount
    const val NonFinite = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.NonFinite
    const val IndexOutOfRange = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.IndexOutOfRange
    const val IndexFormat = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.IndexFormat
    const val AttributeLayout = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.AttributeLayout
    const val Transform = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.Transform
    const val ColorConversion = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.ColorConversion
    const val PrimitiveBlender = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.PrimitiveBlender
    const val Material = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.Material
    const val Budget = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.Budget
    const val ClipCoverage = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.ClipCoverage
    const val MeshBounds = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.MeshBounds
    const val MeshProgramUnregistered = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.MeshProgramUnregistered
    const val MeshProgramCpuUnavailable = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.MeshProgramCpuUnavailable
    const val MeshProgramWgslUnavailable = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.MeshProgramWgslUnavailable
    const val MeshProgramWgslValidation = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.MeshProgramWgslValidation
    const val MeshProgramAbi = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.MeshProgramAbi
    const val MeshProgramChild = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.MeshProgramChild
    const val MeshProgramResource = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.MeshProgramResource
    const val MeshBudget = org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1.MeshBudget

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
