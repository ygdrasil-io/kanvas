package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.ColorFilterNode

public sealed interface ColorFilterCompileResultV1 {
    public data class Ready(public val execution: ColorFilterExecutionPlanV1) : ColorFilterCompileResultV1
    public data class Refused(public val diagnosticCode: String) : ColorFilterCompileResultV1
}
public object ColorFilterPlanCompilerV1 {
    public fun compile(filter: ColorFilterNode): ColorFilterCompileResultV1 {
        if (filter !is ColorFilterNode.Matrix) return ColorFilterCompileResultV1.Refused(W5fPlanDiagnostics.Unpromoted)
        if (filter.values.sizeI32 != 20 || (0 until 20).any { !filter.values[it].isFinite() })
            return ColorFilterCompileResultV1.Refused(W5fPlanDiagnostics.Matrix)
        return ColorFilterCompileResultV1.Ready(ColorFilterExecutionPlanV1.matrix(filter))
    }
}
