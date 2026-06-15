package org.graphiks.kanvas.gpu.renderer.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Verifies cache telemetry source maps separate observed counters from derived reporting. */
class GPUCacheTelemetrySourceMapTest {
    /** Source maps classify all cache telemetry evidence lanes without moving readiness. */
    @Test
    fun `source map distinguishes observed partial derived unavailable and reporting only counters`() {
        val report = GPUCacheTelemetrySourceMapper.map(
            mapId = "m9-cache-source-map",
            requests = listOf(
                sourceRequest(
                    counterName = "pipeline.cache.hit_rate",
                    sourceKind = "adapter-runtime-artifact",
                    sourceArtifactLabel = "artifact:executed-webgpu-cache.json",
                    sourceHash = "sha256:pipeline-cache",
                    requiredFields = setOf("hits", "misses"),
                    observedFields = setOf("hits", "misses"),
                ),
                sourceRequest(
                    counterName = "resource.cache.live_bytes",
                    sourceKind = "adapter-runtime-artifact",
                    sourceArtifactLabel = "artifact:executed-webgpu-cache.json",
                    sourceHash = "sha256:pipeline-cache",
                    requiredFields = setOf("residentBytes", "pressureBytes"),
                    observedFields = setOf("residentBytes"),
                ),
                sourceRequest(
                    counterName = "pipeline.cache.commentary",
                    sourceKind = "comment",
                    sourceArtifactLabel = "report:implementation-roadmap-progress",
                    derivedFrom = listOf("markdown-summary"),
                ),
                sourceRequest(
                    counterName = "atlas.cache.hit_rate",
                    sourceKind = "unavailable",
                    sourceArtifactLabel = "missing:atlas-runtime-artifact",
                ),
                sourceRequest(
                    counterName = "frame.cache.reporting",
                    sourceKind = "reporting-only",
                    sourceArtifactLabel = "policy:m9-cache-reporting-only",
                ),
            ),
        )

        assertEquals(
            listOf(
                GPUCacheTelemetrySourceClassification.Observed,
                GPUCacheTelemetrySourceClassification.ObservedPartial,
                GPUCacheTelemetrySourceClassification.Derived,
                GPUCacheTelemetrySourceClassification.Unavailable,
                GPUCacheTelemetrySourceClassification.ReportingOnly,
            ),
            report.entries.map { it.classification },
        )
        assertEquals(0.0, report.readinessDelta)
        assertFalse(report.releaseBlocking)
        assertFalse(report.productRouteActivated)
        assertEquals(
            listOf(
                "cache-source-map id=m9-cache-source-map entries=5 readinessDelta=0.0 releaseBlocking=false productRouteActivated=false",
                "cache-source counter=pipeline.cache.hit_rate domain=pipeline classification=observed source=artifact:executed-webgpu-cache.json kind=adapter-runtime-artifact hash=sha256:pipeline-cache fields=hits,misses required=hits,misses countsObserved=true",
                "cache-source counter=resource.cache.live_bytes domain=pipeline classification=observed-partial source=artifact:executed-webgpu-cache.json kind=adapter-runtime-artifact hash=sha256:pipeline-cache fields=residentBytes required=pressureBytes,residentBytes countsObserved=false",
                "cache-source counter=pipeline.cache.commentary domain=pipeline classification=derived source=report:implementation-roadmap-progress kind=comment hash=none fields=- required=- countsObserved=false",
                "cache-source counter=atlas.cache.hit_rate domain=pipeline classification=unavailable source=missing:atlas-runtime-artifact kind=unavailable hash=none fields=- required=- countsObserved=false",
                "cache-source counter=frame.cache.reporting domain=pipeline classification=reporting-only source=policy:m9-cache-reporting-only kind=reporting-only hash=none fields=- required=- countsObserved=false",
                "pm:gpu-renderer.cache-telemetry-source-map classification=PolicyGated observed=1 observedPartial=1 derived=1 unavailable=1 reportingOnly=1 readinessDelta=0.0 releaseBlocking=false",
                "nonclaim:no-release-blocking-gate no-readiness-delta no-product-activation no-derived-as-observed no-synthetic-comment-counters",
            ),
            report.dumpLines(),
        )
    }

    /** Derived, partial, unavailable, and reporting-only counters cannot count as observed readiness. */
    @Test
    fun `only fully observed artifact counters count as observed readiness inputs`() {
        val report = GPUCacheTelemetrySourceMapper.map(
            mapId = "m9-cache-source-map",
            requests = listOf(
                sourceRequest(
                    counterName = "pipeline.cache.hit_rate",
                    sourceKind = "adapter-runtime-artifact",
                    sourceArtifactLabel = "artifact:runtime-cache.json",
                    sourceHash = "sha256:runtime-cache",
                    requiredFields = setOf("hits", "misses"),
                    observedFields = setOf("hits", "misses"),
                ),
                sourceRequest(
                    counterName = "pipeline.cache.synthetic",
                    sourceKind = "synthetic-ledger",
                    sourceArtifactLabel = "ledger:synthetic",
                    observedFields = setOf("hits", "misses"),
                ),
                sourceRequest(
                    counterName = "pipeline.cache.report",
                    sourceKind = "report-text",
                    sourceArtifactLabel = "report:text-only",
                    observedFields = setOf("hits", "misses"),
                ),
            ),
        )

        assertEquals(listOf("pipeline.cache.hit_rate"), report.observedReadinessCounters())
        assertEquals(2, report.entries.count { it.classification == GPUCacheTelemetrySourceClassification.Derived })
    }

    /** Source map requests must keep every counter tied to a named source. */
    @Test
    fun `source map requests reject blank counters sources and observed artifacts without hashes`() {
        assertFailsWith<IllegalArgumentException> {
            sourceRequest(counterName = "", sourceArtifactLabel = "artifact:cache.json")
        }
        assertFailsWith<IllegalArgumentException> {
            sourceRequest(counterName = "pipeline.cache.hit_rate", sourceArtifactLabel = "")
        }
        val missingHash = GPUCacheTelemetrySourceMapper.map(
            mapId = "m9-cache-source-map",
            requests = listOf(
                sourceRequest(
                    counterName = "pipeline.cache.hit_rate",
                    sourceKind = "adapter-runtime-artifact",
                    sourceArtifactLabel = "artifact:cache.json",
                    sourceHash = null,
                    requiredFields = setOf("hits"),
                    observedFields = setOf("hits"),
                ),
            ),
        )

        assertEquals(GPUCacheTelemetrySourceClassification.Unavailable, missingHash.entries.single().classification)
        assertFalse(missingHash.entries.single().countsForObservedReadiness)
    }
}

private fun sourceRequest(
    counterName: String,
    sourceArtifactLabel: String,
    sourceKind: String = "adapter-runtime-artifact",
    sourceHash: String? = null,
    requiredFields: Set<String> = emptySet(),
    observedFields: Set<String> = emptySet(),
    derivedFrom: List<String> = emptyList(),
): GPUCacheTelemetrySourceMapRequest =
    GPUCacheTelemetrySourceMapRequest(
        counterName = counterName,
        cacheDomain = "pipeline",
        sourceArtifactLabel = sourceArtifactLabel,
        sourceKind = sourceKind,
        sourceHash = sourceHash,
        requiredFields = requiredFields,
        observedFields = observedFields,
        derivedFrom = derivedFrom,
    )
