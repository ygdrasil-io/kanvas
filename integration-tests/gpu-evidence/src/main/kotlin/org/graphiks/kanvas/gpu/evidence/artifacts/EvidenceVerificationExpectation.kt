package org.graphiks.kanvas.gpu.evidence.artifacts

import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceCase
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneDescriptor
import org.graphiks.kanvas.gpu.evidence.catalog.OraclePolicy
import org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram
import org.graphiks.kanvas.gpu.evidence.runner.RoutedSceneProgram

/** Closed, code-derived facts against which an evidence bundle is verified. */
data class EvidenceVerificationExpectation(
    val sourceCommit: String,
    val descriptor: EvidenceSceneDescriptor,
    val expectedRgba: ByteArray? = null,
    val checkedInPngBytes: ByteArray? = null,
    val expectedRouteId: String,
) {
    init {
        require(expectedRouteId.isNotBlank()) { "expected route id must not be blank" }
        if (descriptor.expectation is EvidenceExpectation.ShouldRender && descriptor.oracle is OraclePolicy.GeneratedCpu) {
            require(expectedRgba != null) { "generated CPU oracle pixels are required" }
        }
        if (descriptor.oracle is OraclePolicy.CheckedInPng) {
            require(checkedInPngBytes != null) { "checked-in oracle bytes are required" }
        }
    }

    companion object {
        fun fromCase(
            evidenceCase: EvidenceCase,
            sourceCommit: String,
            expectedRgba: ByteArray? = null,
            checkedInPngBytes: ByteArray? = null,
        ): EvidenceVerificationExpectation = EvidenceVerificationExpectation(
            sourceCommit = sourceCommit,
            descriptor = evidenceCase.descriptor,
            expectedRgba = expectedRgba,
            checkedInPngBytes = checkedInPngBytes,
            expectedRouteId = when (val program = evidenceCase.program) {
                is KanvasSurfaceProgram -> program.routeId
                is RoutedSceneProgram -> program.routeId
                else -> error("catalog scene program must carry a route id")
            },
        )
    }
}
