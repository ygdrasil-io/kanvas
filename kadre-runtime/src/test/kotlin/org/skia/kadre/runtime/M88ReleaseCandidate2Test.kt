package org.skia.kadre.runtime

import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class M88ReleaseCandidate2Test {
    @Test
    fun rc2EvidenceFreezesApiGatesAndReadinessWithoutOverclaiming() {
        val evidence = buildM88ReleaseCandidate2Evidence(Path("..").toAbsolutePath().normalize())
        val json = evidence.toJsonElement().toString()

        assertEquals("pass", evidence.status)
        assertContains(json, "\"packId\":\"m88-realtime-renderer-rc2-v1\"")
        assertContains(json, "\"readinessBefore\":67.75")
        assertContains(json, "\"readinessAfter\":67.75")
        assertContains(json, "\"readinessDelta\":0.0")
        assertContains(json, "\"claimLevel\":\"realtime-renderer-rc2-freeze-package\"")
        assertContains(json, "\"status\":\"frozen-for-rc2\"")
        assertContains(json, "\"m88.fidelity-counters\"")
        assertContains(json, "\"m88.runtime-effect-nonclaim\"")
        assertContains(json, "\"No full Skia parity claim.\"")
        assertContains(json, "\"No arbitrary SkSL runtime-effect support.\"")
    }

    @Test
    fun gateFreezeKeepsNativeTimingAndCacheEvidenceNonBlocking() {
        val evidence = buildM88ReleaseCandidate2Evidence(Path("..").toAbsolutePath().normalize())
        val json = evidence.gateFreeze().toString()

        assertContains(json, "\"name\":\"m84 frame.kadre-windowed\"")
        assertContains(json, "\"phase\":\"reporting-only\"")
        assertContains(json, "\"name\":\"m85 resource/cache ledger\"")
        assertContains(json, "\"pipelinePerformanceReleaseGate\"")
        assertFalse(json.contains("\"m84 frame.kadre-windowed\",\"phase\":\"release-blocking\""))
    }

    @Test
    fun supportRefusalMatrixClassifiesRc2Boundaries() {
        val evidence = buildM88ReleaseCandidate2Evidence(Path("..").toAbsolutePath().normalize())
        val json = evidence.supportRefusalMatrix().toString()

        assertContains(json, "\"category\":\"supported\"")
        assertContains(json, "\"category\":\"expected-unsupported\"")
        assertContains(json, "\"category\":\"dependency-gated\"")
        assertContains(json, "\"category\":\"implementation-gap\"")
        assertContains(json, "\"category\":\"reporting-only\"")
        assertContains(json, "\"fallbackReason\":\"runtime-effect.arbitrary-sksl-unsupported\"")
        assertContains(json, "\"fallbackReason\":\"m85.device-loss-recreate-observation-unsupported\"")
        assertTrue(evidence.pmDemoScript().contains("M88 PM Demo Script"))
    }

    @Test
    fun writtenEvidenceValidatesArtifactPathsAndGateFields() {
        val projectRoot = Path("..").toAbsolutePath().normalize()
        val outputRoot = createTempDirectory("m88-rc2-test")
        buildM88ReleaseCandidate2Evidence(projectRoot, outputRoot).writeArtifacts()

        validateM88ReleaseCandidate2Evidence(projectRoot, outputRoot)
    }
}
