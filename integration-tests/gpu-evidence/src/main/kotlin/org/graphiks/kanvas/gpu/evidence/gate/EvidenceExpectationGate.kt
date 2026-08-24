package org.graphiks.kanvas.gpu.evidence.gate

import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneDescriptor
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation

sealed interface EvidenceVerdict {
    data class Pass(val reason: String) : EvidenceVerdict
    data class Fail(val reason: String) : EvidenceVerdict
    data class Unavailable(val reason: String) : EvidenceVerdict
}

/** Evaluates the closed descriptor/observation matrix without touching WebGPU. */
object EvidenceExpectationGate {
    fun evaluate(
        descriptor: EvidenceSceneDescriptor,
        observation: SceneObservation,
    ): EvidenceVerdict = when (val expectation = descriptor.expectation) {
        EvidenceExpectation.ShouldRender -> when (observation) {
            is SceneObservation.Rendered ->
                if (observation.comparison.passed) {
                    EvidenceVerdict.Pass("rendered image passed comparison")
                } else {
                    EvidenceVerdict.Fail("rendered image failed comparison")
                }

            is SceneObservation.Refused ->
                EvidenceVerdict.Fail("scene refused: ${observation.stableReasonCode}")

            is SceneObservation.Unavailable ->
                EvidenceVerdict.Unavailable("scene unavailable: ${observation.stableReasonCode}")
        }

        is EvidenceExpectation.ShouldRefuse -> when (observation) {
            is SceneObservation.Rendered ->
                EvidenceVerdict.Fail("scene rendered instead of refusing")

            is SceneObservation.Refused -> {
                if (observation.stableReasonCode != expectation.stableReasonCode) {
                    EvidenceVerdict.Fail(
                        "expected refusal ${expectation.stableReasonCode}, got ${observation.stableReasonCode}",
                    )
                } else if (observation.submissionDelta != 0L) {
                    EvidenceVerdict.Fail("refusal submitted ${observation.submissionDelta} command(s)")
                } else {
                    EvidenceVerdict.Pass("exact refusal before submission")
                }
            }

            is SceneObservation.Unavailable ->
                EvidenceVerdict.Unavailable("scene unavailable: ${observation.stableReasonCode}")
        }
    }
}
