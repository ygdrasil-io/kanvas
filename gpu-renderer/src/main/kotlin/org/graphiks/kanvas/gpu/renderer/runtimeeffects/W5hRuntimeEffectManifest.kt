package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import org.graphiks.kanvas.gpu.plan.ChildOpacityCpuEvaluatorV1
import org.graphiks.kanvas.gpu.plan.RuntimeEffectExpectationV1
import org.graphiks.kanvas.gpu.plan.W5hPlanDiagnostics
import org.graphiks.kanvas.render.ir.*

/** Renderer ABI declaration only. It neither looks up nor constructs semantic graphs. */
internal object W5hRuntimeEffectManifest {
    private val descriptor = RuntimeEffectDescriptor.of(RuntimeEffectId("kanvas.runtime.child-opacity"),
        RuntimeEffectAbi.SHADER, 1,
        RuntimeUniformBlockV1.of(listOf(RuntimeUniformSlotV2("alpha", RuntimeUniformType.FLOAT, 0, 4, 4, 1, 0)), 16),
        listOf(RuntimeChildSlotV2("child", RuntimeChildType.SHADER, false)))
    fun verify(expectation: RuntimeEffectExpectationV1) {
        require(expectation.id == descriptor.id && expectation.semanticVersionI32 == descriptor.semanticVersionI32 &&
            expectation.abiHash == descriptor.abiHash && descriptor.recomputeAbiHash() == expectation.abiHash) {
            W5hPlanDiagnostics.AbiMismatch
        }
        require(expectation.numericContractId == "kanvas.runtime.child-opacity.numeric-v1" &&
            expectation.cpuEvaluatorId == ChildOpacityCpuEvaluatorV1.id &&
            expectation.cpuEvaluatorVersionI32 == ChildOpacityCpuEvaluatorV1.evaluatorVersionI32) {
            W5hPlanDiagnostics.CpuNumeric
        }
    }
}
