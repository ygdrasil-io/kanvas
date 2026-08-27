package org.graphiks.kanvas.gpu.evidence.catalog

import org.graphiks.kanvas.gpu.evidence.oracle.CpuOracle
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceProgram
import org.graphiks.kanvas.gpu.evidence.runner.RoutedSceneProgram
import org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram

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

/** A closed, executable evidence case paired with its validation-only CPU oracle. */
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
        when (executionBoundary) {
            EvidenceExecutionBoundary.PublicSurface -> require(program is KanvasSurfaceProgram) {
                "public evidence cases must execute through KanvasSurfaceProgram"
            }
            EvidenceExecutionBoundary.HistoricalStandaloneRefusal -> {
                require(descriptor.expectation is EvidenceExpectation.ShouldRefuse) {
                    "historical standalone evidence must be a stable refusal"
                }
                require(program is RoutedSceneProgram) {
                    "historical standalone refusal must carry a routed product diagnostic"
                }
            }
        }
    }
}
