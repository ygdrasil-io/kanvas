package org.graphiks.kanvas.gpu.evidence.catalog

import org.graphiks.kanvas.gpu.evidence.oracle.CpuOracle
import org.graphiks.kanvas.gpu.evidence.runner.SceneProgram

/** A closed, executable evidence case paired with its validation-only CPU oracle. */
data class EvidenceCase(
    val descriptor: EvidenceSceneDescriptor,
    val program: SceneProgram,
    val oracle: CpuOracle?,
) {
    init {
        require((descriptor.expectation is EvidenceExpectation.ShouldRender) == (oracle != null)) {
            "render cases require an oracle and refusal cases must not have one"
        }
    }
}
