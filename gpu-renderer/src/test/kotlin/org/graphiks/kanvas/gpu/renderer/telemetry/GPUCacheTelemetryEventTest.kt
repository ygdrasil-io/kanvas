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
    }
}
