package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp

/** Drawing families deliberately left on the temporary immediate renderer after Slice 12A. */
enum class LegacyDisplayOpFamily {
    Images,
    Text,
    Vertices,
    Composites,
}

data class GPULegacyImmediatePathDump(
    val invocationCount: Int,
    val invocationsByFamily: Map<LegacyDisplayOpFamily, Int>,
)

/**
 * Closed, diagnostic-only boundary around drawing families not migrated in Slice 12A.
 *
 * This adapter classifies only the DisplayOp family. It never sees blend, coverage,
 * destination snapshots, or backend handles and therefore cannot make routing decisions.
 */
class GPULegacyImmediatePathAdapter {
    private val invocationCounts = linkedMapOf<LegacyDisplayOpFamily, Int>()

    fun accepts(operation: DisplayOp): Boolean = familyOrNull(operation) in allowedFamilies

    fun recordInvocation(operation: DisplayOp) {
        val family = requireNotNull(familyOrNull(operation)) {
            "DisplayOp ${operation.javaClass.simpleName} is outside the closed legacy allowlist"
        }
        require(family in allowedFamilies) {
            "DisplayOp ${operation.javaClass.simpleName} is outside the closed legacy allowlist"
        }
        invocationCounts[family] = (invocationCounts[family] ?: 0) + 1
    }

    fun dump(): GPULegacyImmediatePathDump = GPULegacyImmediatePathDump(
        invocationCount = invocationCounts.values.sum(),
        invocationsByFamily = invocationCounts.toMap(),
    )

    companion object {
        val allowedFamilies: Set<LegacyDisplayOpFamily> = setOf(
            LegacyDisplayOpFamily.Images,
            LegacyDisplayOpFamily.Text,
            LegacyDisplayOpFamily.Vertices,
            LegacyDisplayOpFamily.Composites,
        )

        fun familyOrNull(operation: DisplayOp): LegacyDisplayOpFamily? = when (operation) {
            is DisplayOp.DrawImage,
            is DisplayOp.DrawImageNine,
            is DisplayOp.DrawImageLattice,
            is DisplayOp.DrawAtlas,
            -> LegacyDisplayOpFamily.Images

            is DisplayOp.DrawText -> LegacyDisplayOpFamily.Text

            is DisplayOp.DrawVertices,
            is DisplayOp.DrawMesh,
            -> LegacyDisplayOpFamily.Vertices

            is DisplayOp.DrawPicture,
            is DisplayOp.BeginLayer,
            DisplayOp.EndLayer,
            -> LegacyDisplayOpFamily.Composites

            is DisplayOp.DrawColor,
            is DisplayOp.Clear,
            is DisplayOp.DrawPoint,
            is DisplayOp.DrawPoints,
            is DisplayOp.DrawRect,
            is DisplayOp.DrawRRect,
            is DisplayOp.DrawDRRect,
            is DisplayOp.DrawPath,
            is DisplayOp.SetTransform,
            is DisplayOp.SetClip,
            is DisplayOp.Annotation,
            is DisplayOp.FlushAndSnapshot,
            -> null
        }
    }
}
