package org.graphiks.kanvas.gpu.plan

import java.util.ArrayDeque
import org.graphiks.kanvas.render.ir.CapturedFilterInputV1
import org.graphiks.kanvas.render.ir.CapturedFilterNodeId
import org.graphiks.kanvas.render.ir.CapturedFilterNodeV1
import org.graphiks.kanvas.render.ir.CapturedFilterRootV1
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneSnapshot

/**
 * W6b's sole captured-filter admission seam.  Task 2 deliberately freezes no executable
 * operation: Task 3 adds typed passes here after the renderer has a native implementation.
 */
internal object W6bFilterGraphConstruction {
    internal fun owns(scene: SceneSnapshot): Boolean = ownership(scene).isOwned

    internal fun admissionRefusalOrNull(scene: SceneSnapshot): RenderDiagnostic? {
        val ownership = ownership(scene)
        if (!ownership.isOwned) return null
        if (ownership.hasBackdrop) return W6bFilterDiagnostics.refusal(
            W6bFilterDiagnostics.UnsupportedBackdrop,
            "W6b does not admit backdrop filters.",
        )
        if (ownership.hasFilteredPrevious) return W6bFilterDiagnostics.refusal(
            W6bFilterDiagnostics.FilteredPrevious,
            "W6b does not admit initWithPrevious combined with a spatial filter.",
        )
        if (ownership.hasUnsupportedImageFamily(scene)) return W6bFilterDiagnostics.refusal(
            W6bFilterDiagnostics.UnsupportedFamily,
            "The captured image-filter family belongs to W6c or W6d.",
        )
        return W6bFilterDiagnostics.refusal(
            W6bFilterDiagnostics.NativeExecutionUnimplemented,
            "W6b filter execution is not materialized until the typed native pass is available.",
        )
    }

    private fun ownership(scene: SceneSnapshot): Ownership {
        val roots = mutableListOf<CapturedFilterRootV1>()
        var mask = false
        var backdrop = false
        var filteredPrevious = false
        scene.forEach { command ->
            when (command) {
                is SceneCommand.Draw -> {
                    command.node.paint?.imageFilter?.let(roots::add)
                    mask = mask || command.node.paint?.maskFilter != null
                }
                is SceneCommand.BeginLayer -> {
                    command.descriptor.paint?.imageFilter?.let(roots::add)
                    mask = mask || command.descriptor.paint?.maskFilter != null
                    val descriptorBackdrop = command.descriptor.backdrop
                    if (descriptorBackdrop !is EffectStack.Empty) {
                        backdrop = true
                        (descriptorBackdrop as? EffectStack.Entries)?.filterIsInstance<CapturedFilterRootV1>()?.forEach(roots::add)
                    }
                    filteredPrevious = filteredPrevious || command.descriptor.initWithPrevious &&
                        (command.descriptor.paint?.imageFilter != null || command.descriptor.paint?.maskFilter != null)
                }
                else -> Unit
            }
        }
        return Ownership(roots, mask, backdrop, filteredPrevious)
    }

    private class Ownership(
        private val roots: List<CapturedFilterRootV1>,
        private val hasMask: Boolean,
        val hasBackdrop: Boolean,
        val hasFilteredPrevious: Boolean,
    ) {
        val isOwned: Boolean get() = roots.isNotEmpty() || hasMask || hasBackdrop

        fun hasUnsupportedImageFamily(scene: SceneSnapshot): Boolean {
            val pending = ArrayDeque<CapturedFilterNodeId>()
            roots.forEach { pending.addLast(it.id) }
            val seen = BooleanArray(scene.filterTable.nodeCount)
            while (pending.isNotEmpty()) {
                val id = pending.removeLast()
                if (seen[id.value]) continue
                seen[id.value] = true
                when (val node = scene.filterTable.nodeAt(id)) {
                    is CapturedFilterNodeV1.Blur -> node.input.enqueueNodeOrUnsupported(pending)?.let { return true }
                    is CapturedFilterNodeV1.DropShadow -> node.input.enqueueNodeOrUnsupported(pending)?.let { return true }
                    else -> return true
                }
            }
            return false
        }
    }

    /** Returns true for a reserved W6d input; W6b executes only captured-node or source inputs. */
    private fun CapturedFilterInputV1.enqueueNodeOrUnsupported(pending: ArrayDeque<CapturedFilterNodeId>): Boolean? = when (this) {
        CapturedFilterInputV1.ImplicitSource, CapturedFilterInputV1.TransparentBlack -> null
        is CapturedFilterInputV1.Node -> {
            pending.addLast(id)
            null
        }
        is CapturedFilterInputV1.Picture, is CapturedFilterInputV1.Backdrop -> true
    }
}
