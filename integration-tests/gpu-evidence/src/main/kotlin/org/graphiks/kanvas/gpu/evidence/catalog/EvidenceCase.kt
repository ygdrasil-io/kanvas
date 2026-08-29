package org.graphiks.kanvas.gpu.evidence.catalog

import org.graphiks.kanvas.gpu.evidence.oracle.CpuOracle
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceProgram

/**
 * Execution boundary of an evidence case.
 *
 * Only [PublicSurface] cases are current Kanvas rendering claims.  The
 * historical standalone variant is deliberately refusal-only: it preserves
 * the provenance of old promoted bundles without letting an internal
 * recorder prove public Surface support.
 */
enum class EvidenceExecutionBoundary {
    PublicSurface,
    HistoricalStandaloneRefusal,
}

/**
 * An executable evidence case paired with its validation-only CPU oracle.
 *
 * The catalogue validates the execution-boundary/program pairing when it is
 * assembled. Keeping that check at the catalogue boundary also lets runner
 * contract tests inject small prepared-scene fixtures without weakening any
 * public catalogue claim.
 */
data class EvidenceCase(
    val descriptor: EvidenceSceneDescriptor,
    val program: EvidenceProgram,
    val oracle: CpuOracle?,
    val executionBoundary: EvidenceExecutionBoundary = EvidenceExecutionBoundary.PublicSurface,
) {
    init {
        require((descriptor.expectation is EvidenceExpectation.ShouldRender) == (oracle != null)) {
            "render cases require an oracle and refusal cases must not have one"
        }
    }
}
