package org.graphiks.kanvas.gpu.renderer.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Verifies cache hit/miss facts are telemetry only. */
class GPUCacheTelemetryEventTest {
    /** Cache events update material, module, and pipeline hit/miss counters. */
    @Test
    fun `cache hit and miss events are recorded as deterministic ledger facts`() {
        val ledger = GPUTelemetryLedger.empty()
            .recordCacheEvent(
                GPUCacheTelemetryEvent.material(
                    result = GPUCacheEventResult.Hit,
                    keyHash = "material:solid",
                    subjectHash = "program:solid",
                ),
            )
            .recordCacheEvent(
                GPUCacheTelemetryEvent.module(
                    result = GPUCacheEventResult.Miss,
                    keyHash = "module-preimage:solid",
                    subjectHash = "module:solid-v1",
                ),
            )
            .recordCacheEvent(
                GPUCacheTelemetryEvent.pipeline(
                    result = GPUCacheEventResult.Miss,
                    keyHash = "pipeline:solid-rect",
                    subjectHash = "render-pipeline:solid-rect",
                ),
            )

        assertEquals(
            listOf("material", "module", "pipeline"),
            ledger.cacheEvents.map { it.domain },
        )
        assertEquals(1L, ledger.cacheTelemetry.single { it.cacheName == "material" }.hits)
        assertEquals(1L, ledger.cacheTelemetry.single { it.cacheName == "module" }.misses)
        assertEquals(1L, ledger.cacheTelemetry.single { it.cacheName == "pipeline" }.misses)
    }

    /** Execution cache events record create, failure, stale-generation, eviction, and layout facts. */
    @Test
    fun `execution cache events record materialization outcomes and layout counters`() {
        val ledger = listOf(
            GPUCacheTelemetryEvent.module(
                result = GPUCacheEventResult.Create,
                keyHash = "module-preimage:solid",
                subjectHash = "module:solid-v1",
            ),
            GPUCacheTelemetryEvent.pipeline(
                result = GPUCacheEventResult.Failure,
                keyHash = "pipeline:solid-rect",
                subjectHash = "render-pipeline:solid-rect",
            ),
            GPUCacheTelemetryEvent.pipeline(
                result = GPUCacheEventResult.StaleGeneration,
                keyHash = "pipeline:solid-rect",
                subjectHash = "render-pipeline:solid-rect",
            ),
            GPUCacheTelemetryEvent.pipeline(
                result = GPUCacheEventResult.Evict,
                keyHash = "pipeline:solid-rect",
                subjectHash = "render-pipeline:solid-rect",
            ),
            GPUCacheTelemetryEvent.bindGroupLayout(
                result = GPUCacheEventResult.Create,
                keyHash = "layout:solid-rect",
                subjectHash = "bgl:solid-rect-v1",
            ),
            GPUCacheTelemetryEvent.pipelineLayout(
                result = GPUCacheEventResult.Create,
                keyHash = "pipeline-layout:solid-rect",
                subjectHash = "pipeline-layout:solid-rect-v1",
            ),
        ).fold(GPUTelemetryLedger.empty()) { current, event ->
            current.recordCacheEvent(event)
        }

        assertEquals(1L, ledger.cacheTelemetry.single { it.cacheName == "module" }.creations)
        val pipeline = ledger.cacheTelemetry.single { it.cacheName == "pipeline" }
        assertEquals(1L, pipeline.failures)
        assertEquals(1L, pipeline.staleGenerations)
        assertEquals(1L, pipeline.evictions)
        assertEquals(1L, ledger.cacheTelemetry.single { it.cacheName == "bind-group-layout" }.creations)
        assertEquals(1L, ledger.cacheTelemetry.single { it.cacheName == "pipeline-layout" }.creations)
    }

    /** Cache telemetry does not encode route support decisions. */
    @Test
    fun `cache event facts do not decide route support`() {
        val routeLikeFields = GPUCacheTelemetryEvent::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }
            .filter { fieldName ->
                fieldName.contains("route", ignoreCase = true) ||
                    fieldName.contains("support", ignoreCase = true) ||
                    fieldName.contains("accepted", ignoreCase = true)
            }

        assertFalse(
            actual = routeLikeFields.isNotEmpty(),
            message = "Cache telemetry must not carry route support fields: $routeLikeFields",
        )
    }

    /** Cache event constructors refuse non-canonical domains and blank identity facts. */
    @Test
    fun `cache event facts require canonical domains and non blank hashes`() {
        assertFailsWith<IllegalArgumentException> {
            GPUCacheTelemetryEvent(
                domain = "route",
                result = GPUCacheEventResult.Hit,
                keyHash = "route-key",
                subjectHash = "route-subject",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUCacheTelemetryEvent.material(
                result = GPUCacheEventResult.Hit,
                keyHash = "",
                subjectHash = "program:solid",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUCacheTelemetryEvent.pipeline(
                result = GPUCacheEventResult.Miss,
                keyHash = "pipeline:solid",
                subjectHash = "",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUCacheTelemetryEvent.bindGroupLayout(
                result = GPUCacheEventResult.Create,
                keyHash = "",
                subjectHash = "bgl:solid",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUCacheTelemetryEvent.pipelineLayout(
                result = GPUCacheEventResult.Create,
                keyHash = "",
                subjectHash = "pipeline-layout:solid",
            )
        }
    }
}
