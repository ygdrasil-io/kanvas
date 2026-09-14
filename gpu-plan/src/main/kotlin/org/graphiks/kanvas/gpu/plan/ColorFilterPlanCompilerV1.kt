package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.ColorFilterNode

public sealed interface ColorFilterCompileResultV1 {
    public data class Ready(public val execution: ColorFilterExecutionPlanV1) : ColorFilterCompileResultV1
    public data class Refused(public val diagnosticCode: String) : ColorFilterCompileResultV1
}
public object ColorFilterPlanCompilerV1 {
    public fun compile(filter: ColorFilterNode): ColorFilterCompileResultV1 {
        data class Frame(val node: ColorFilterNode, val depthI32: Int, val finish: Boolean)
        val stack = java.util.ArrayDeque<Frame>()
        val compiled = java.util.IdentityHashMap<ColorFilterNode, ColorFilterExecutionPlanV1>()
        val postOrder = mutableListOf<ColorFilterNode>()
        stack.addLast(Frame(filter, 1, false))
        var countI32 = 0
        while (stack.isNotEmpty()) {
            val frame = stack.removeLast()
            val node = frame.node
            if (!frame.finish) {
                if (frame.depthI32 > 64 || ++countI32 > 4096)
                    return ColorFilterCompileResultV1.Refused(W5fPlanDiagnostics.Schema)
                when (node) {
                    is ColorFilterNode.Matrix -> {
                        if (node.values.sizeI32 != 20 || (0 until 20).any { !node.values[it].isFinite() })
                            return ColorFilterCompileResultV1.Refused(W5fPlanDiagnostics.Matrix)
                    }
                    is ColorFilterNode.Compose -> Unit
                    is ColorFilterNode.Table -> if (node.table.sizeI32 != 256)
                        return ColorFilterCompileResultV1.Refused(W5fPlanDiagnostics.Table)
                    is ColorFilterNode.Lighting, is ColorFilterNode.Blend, ColorFilterNode.SRGBToLinear, ColorFilterNode.LinearToSRGB -> Unit
                    is ColorFilterNode.Lerp -> if (!node.t.isFinite() || node.t !in 0f..1f)
                        return ColorFilterCompileResultV1.Refused(W5fPlanDiagnostics.Lerp)
                    else -> return ColorFilterCompileResultV1.Refused(W5fPlanDiagnostics.Unpromoted)
                }
                stack.addLast(frame.copy(finish = true))
                when (node) {
                    is ColorFilterNode.Compose -> {
                        stack.addLast(Frame(node.outer, frame.depthI32 + 1, false))
                        stack.addLast(Frame(node.inner, frame.depthI32 + 1, false))
                    }
                    is ColorFilterNode.Lerp -> {
                        stack.addLast(Frame(node.src, frame.depthI32 + 1, false))
                        stack.addLast(Frame(node.dst, frame.depthI32 + 1, false))
                    }
                    else -> Unit
                }
            } else postOrder += node
        }
        // Validate the entire occurrence-expanded metadata traversal before recipes
        // copy records or substitute child operation graphs (including direct IR calls).
        postOrder.forEach { node -> compiled[node] = when (node) {
                is ColorFilterNode.Matrix -> ColorFilterExecutionPlanV1.matrix(node)
                is ColorFilterNode.Table -> ColorFilterExecutionPlanV1.table(node)
                is ColorFilterNode.Lighting -> ColorFilterExecutionPlanV1.lighting(node)
                is ColorFilterNode.Blend -> ColorFilterExecutionPlanV1.blend(node)
                ColorFilterNode.SRGBToLinear, ColorFilterNode.LinearToSRGB -> ColorFilterExecutionPlanV1.transfer(node)
                is ColorFilterNode.Compose -> ColorFilterExecutionPlanV1.compose(node,
                    requireNotNull(compiled[node.outer]), requireNotNull(compiled[node.inner]))
                is ColorFilterNode.Lerp -> ColorFilterExecutionPlanV1.lerp(node,
                    requireNotNull(compiled[node.dst]), requireNotNull(compiled[node.src]))
                else -> error("Metadata traversal admits only implemented filter recipes")
            }
        }
        return ColorFilterCompileResultV1.Ready(requireNotNull(compiled[filter]))
    }
}
