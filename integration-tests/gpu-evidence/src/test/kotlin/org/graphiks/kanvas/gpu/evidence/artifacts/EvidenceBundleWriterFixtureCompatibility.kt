package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.Path
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneDescriptor
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.evidence.catalog.RouteEvidence

/** Test-only fixture adapter; it supplies the observed route to the strict writer path. */
fun EvidenceBundleWriter.writeGenerated(
    descriptor: EvidenceSceneDescriptor,
    observation: SceneObservation,
    expectedRgba: ByteArray? = null,
    attemptId: String = observation.fixtureRoute().attemptId ?: "attempt-1",
    checkedInPngBytes: ByteArray? = null,
): Path = writeGeneratedStrict(
    descriptor = descriptor,
    observation = observation,
    expectedRgba = expectedRgba,
    attemptId = attemptId,
    checkedInPngBytes = checkedInPngBytes,
    expectedRouteId = observation.fixtureRoute().routeId,
)

private fun SceneObservation.fixtureRoute(): RouteEvidence = when (this) {
    is SceneObservation.Rendered -> route
    is SceneObservation.Refused -> route
    is SceneObservation.Unavailable -> error("unavailable observations cannot produce bundles")
}
