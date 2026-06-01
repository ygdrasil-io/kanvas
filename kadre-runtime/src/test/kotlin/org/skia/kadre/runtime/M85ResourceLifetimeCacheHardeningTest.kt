package org.skia.kadre.runtime

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class M85ResourceLifetimeCacheHardeningTest {
    @Test
    fun resourceTelemetryReportsBoundedCachePressure() {
        val evidence = buildM85ResourceLifetimeEvidence(Path("..").toAbsolutePath().normalize())
        val json = evidence.toJsonElement().toString()

        assertEquals("pass", evidence.status)
        assertEquals("frame.kadre-windowed", evidence.lane)
        assertEquals("m83-display-list-pm-scene-v1", evidence.sceneContractId)
        assertEquals(120, evidence.measuredSampleCount)
        assertEquals(2, evidence.resizeReconfigureCount)
        assertEquals(0, evidence.resizeFailureCount)
        assertEquals(1, evidence.cachePressure.pipelineCacheMisses)
        assertEquals(179, evidence.cachePressure.pipelineCacheHits)
        assertEquals(0, evidence.cachePressure.invalidResourceReuseCount)
        assertContains(json, "\"packId\":\"m85-resource-lifetime-cache-hardening-v1\"")
        assertContains(json, "\"observedRuntimeCounters\":false")
        assertContains(json, "\"countedAsCacheReadinessGate\":false")
        assertContains(json, "\"counterSource\":\"derived-selected-scene-resource-ledger\"")
        assertContains(json, "\"generationsStrictlyAdvance\":true")
        assertContains(json, "\"generationSequenceMonotonic\":true")
        assertContains(json, "\"pipelineKeyPolicy\":\"layout-code-resource-pipeline-state-only\"")
        assertContains(json, "\"uniformValuesInPipelineKey\":false")
        assertContains(json, "\"m85.cache-keyspace-bounded\"")
    }

    @Test
    fun deviceLossIsStableUnsupportedDiagnosticNotFakeRecovery() {
        val evidence = buildM85ResourceLifetimeEvidence(Path("..").toAbsolutePath().normalize())
        val json = evidence.toJsonElement().toString()

        assertContains(json, "\"status\":\"expected-unsupported\"")
        assertContains(json, "\"reason\":\"m85.device-loss-recreate-observation-unsupported\"")
        assertContains(json, "\"recreateClaimed\":false")
    }
}
