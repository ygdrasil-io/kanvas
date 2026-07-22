package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetry

class GPURuntimeBaselineSnapshotTest {
    @Test
    fun `dump lines report compact runtime cache and capability evidence`() {
        val snapshot = GPURuntimeBaselineSnapshot(
            label = "phase0",
            telemetry = GPUBackendRuntimeTelemetry(
                renderPasses = 3L,
                offscreenPasses = 2L,
                windowPasses = 1L,
                submissions = 4L,
                commandBuffers = 5L,
                buffersCreated = 6L,
                texturesCreated = 7L,
                bindGroupsCreated = 8L,
                samplersCreated = 9L,
                queueWrites = 10L,
                uniformSlabsCreated = 11L,
                uniformSlabBytesAllocated = 512L,
                uniformSlabFallbacks = 12L,
            ),
            cacheTelemetry = listOf(
                GPUCacheTelemetry(
                    cacheName = "pipeline",
                    hits = 1L,
                    misses = 1L,
                    evictions = 0L,
                    residentBytes = 0L,
                    pressureBytes = 0L,
                    creations = 1L,
                    failures = 0L,
                ),
            ),
            capabilityFacts = listOf(
                GPUCapabilityFact(
                    name = "minUniformBufferOffsetAlignment",
                    source = "device.limits",
                    value = "256",
                    affectsValidity = true,
                    evidenceLabel = "runtime",
                ),
            ),
        )

        val lines = snapshot.dumpLines()

        assertEquals(
            "gpu-phase0.baseline label=phase0 renderPasses=3 offscreenPasses=2 windowPasses=1 " +
                "submissions=4 commandBuffers=5 buffersCreated=6 texturesCreated=7 " +
                "bindGroupsCreated=8 samplersCreated=9 queueWrites=10 uniformSlabsCreated=11 " +
                "uniformSlabBytesAllocated=512 uniformSlabFallbacks=12",
            lines.first(),
        )
        assertTrue(
            lines.contains(
                "gpu-phase0.cache label=phase0 domain=pipeline hits=1 misses=1 creates=1 failures=0",
            ),
        )
        assertTrue(
            lines.contains(
                "gpu-phase0.capability label=phase0 name=minUniformBufferOffsetAlignment " +
                    "source=device.limits value=256 affectsValidity=true evidence=runtime",
            ),
        )
        assertTrue(lines.none { it.contains("@") })
    }

    @Test
    fun `blank label rejects with stable message`() {
        val error = assertFailsWith<IllegalArgumentException> {
            GPURuntimeBaselineSnapshot(
                label = " ",
                telemetry = GPUBackendRuntimeTelemetry.Empty,
            )
        }

        assertEquals("GPURuntimeBaselineSnapshot.label must not be blank", error.message)
    }

    @Test
    fun `rejects labels that are not dump safe`() {
        listOf(
            "phase 0",
            "phase0=value",
            "phase0\nnext",
            "phase@0",
            "0xCAFE12",
            handleLikeToken(),
            externalTextureLikeToken(),
        ).forEach { invalidLabel ->
            val error = assertFailsWith<IllegalArgumentException> {
                GPURuntimeBaselineSnapshot(
                    label = invalidLabel,
                    telemetry = GPUBackendRuntimeTelemetry.Empty,
                )
            }

            assertEquals("GPURuntimeBaselineSnapshot.label must be dump-safe", error.message)
        }
    }

    @Test
    fun `allows normal labels containing 0x inside a larger token`() {
        val snapshot = GPURuntimeBaselineSnapshot(
            label = "phase0x123",
            telemetry = GPUBackendRuntimeTelemetry.Empty,
        )

        assertEquals(
            "gpu-phase0.baseline label=phase0x123 renderPasses=0 offscreenPasses=0 windowPasses=0 " +
                "submissions=0 commandBuffers=0 buffersCreated=0 texturesCreated=0 bindGroupsCreated=0 " +
                "samplersCreated=0 queueWrites=0 uniformSlabsCreated=0 uniformSlabBytesAllocated=0 " +
                "uniformSlabFallbacks=0",
            snapshot.dumpLines().first(),
        )
    }

    @Test
    fun `session snapshot deduplicates capability facts that differ only by evidence label`() {
        val limits = GPULimits(
            maxTextureDimension2D = 4096L,
            copyBytesPerRowAlignment = 256L,
            minUniformBufferOffsetAlignment = 256L,
        )
        val session = object : GPUBackendSession {
            override val adapterInfo: GPUBackendAdapterSummary? = null
            override val deviceGeneration = GPUDeviceGenerationID(19L)
            override val capabilities: GPUCapabilities = GPUCapabilities(
                implementation = GPUImplementationIdentity(
                    facadeName = "facade",
                    implementationName = "impl",
                    adapterName = "adapter",
                    deviceName = "device",
                ),
                facts = limits.capabilityFacts(evidenceLabel = "reported") + GPUCapabilityFact(
                    name = "sampleCount",
                    source = "device.features",
                    value = "4",
                    affectsValidity = false,
                    evidenceLabel = "runtime",
                ),
                snapshotId = "snapshot-1",
                limits = limits,
            )

            override fun createOffscreenTarget(request: GPUOffscreenTargetRequest): GPUBackendOffscreenTarget =
                error("not used in test")

            override fun prepareSceneFrameSession(request: GPUOffscreenTargetRequest): GPUPreparedSceneFrameSession =
                error("not used in test")

            override fun close() = Unit
        }

        val capabilityLines = session.phase0BaselineSnapshot(label = "phase0").dumpLines()
            .filter { line -> line.startsWith("gpu-phase0.capability ") }

        assertEquals(4, capabilityLines.size)
        assertEquals(
            1,
            capabilityLines.count { line ->
                line ==
                    "gpu-phase0.capability label=phase0 name=minUniformBufferOffsetAlignment " +
                    "source=device.limits value=256 affectsValidity=true evidence=runtime"
            },
        )
        assertEquals(
            0,
            capabilityLines.count { line ->
                line ==
                    "gpu-phase0.capability label=phase0 name=minUniformBufferOffsetAlignment " +
                    "source=device.limits value=256 affectsValidity=true evidence=reported"
            },
        )
        assertEquals(
            1,
            capabilityLines.count { line ->
                line ==
                    "gpu-phase0.capability label=phase0 name=maxTextureDimension2D " +
                    "source=device.limits value=4096 affectsValidity=true evidence=runtime"
            },
        )
        assertEquals(
            1,
            capabilityLines.count { line ->
                line ==
                    "gpu-phase0.capability label=phase0 name=copyBytesPerRowAlignment " +
                    "source=device.limits value=256 affectsValidity=true evidence=runtime"
            },
        )
        assertEquals(
            1,
            capabilityLines.count { line ->
                line ==
                    "gpu-phase0.capability label=phase0 name=sampleCount " +
                    "source=device.features value=4 affectsValidity=false evidence=runtime"
            },
        )
    }

    @Test
    fun `dump lines reject non dump safe capability tokens`() {
        val handleLike = handleLikeToken()

        val snapshot = GPURuntimeBaselineSnapshot(
            label = "phase0",
            telemetry = GPUBackendRuntimeTelemetry.Empty,
            capabilityFacts = listOf(
                GPUCapabilityFact(
                    name = "minUniformBufferOffsetAlignment",
                    source = handleLike,
                    value = "256",
                    affectsValidity = true,
                    evidenceLabel = "runtime",
                ),
            ),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            snapshot.dumpLines()
        }

        assertEquals("GPURuntimeBaselineSnapshot.capabilityFacts.source must be dump-safe", error.message)
    }

    @Test
    fun `dump lines reject non dump safe capability values and evidence labels`() {
        val pointerLike = "0x" + "CAFE12"
        val externalTextureLike = externalTextureLikeToken()

        val valueError = assertFailsWith<IllegalArgumentException> {
            GPURuntimeBaselineSnapshot(
                label = "phase0",
                telemetry = GPUBackendRuntimeTelemetry.Empty,
                capabilityFacts = listOf(
                    GPUCapabilityFact(
                        name = "minUniformBufferOffsetAlignment",
                        source = "device.limits",
                        value = pointerLike,
                        affectsValidity = true,
                        evidenceLabel = "runtime",
                    ),
                ),
            ).dumpLines()
        }
        assertEquals("GPURuntimeBaselineSnapshot.capabilityFacts.value must be dump-safe", valueError.message)

        val evidenceError = assertFailsWith<IllegalArgumentException> {
            GPURuntimeBaselineSnapshot(
                label = "phase0",
                telemetry = GPUBackendRuntimeTelemetry.Empty,
                capabilityFacts = listOf(
                    GPUCapabilityFact(
                        name = "minUniformBufferOffsetAlignment",
                        source = "device.limits",
                        value = "256",
                        affectsValidity = true,
                        evidenceLabel = externalTextureLike,
                    ),
                ),
            ).dumpLines()
        }
        assertEquals(
            "GPURuntimeBaselineSnapshot.capabilityFacts.evidenceLabel must be dump-safe",
            evidenceError.message,
        )
    }

    @Test
    fun `dump lines reject non dump safe cache tokens`() {
        val handleLike = handleLikeToken()

        val snapshot = GPURuntimeBaselineSnapshot(
            label = "phase0",
            telemetry = GPUBackendRuntimeTelemetry.Empty,
            cacheTelemetry = listOf(
                GPUCacheTelemetry(
                    cacheName = handleLike,
                    hits = 1L,
                    misses = 0L,
                    evictions = 0L,
                    residentBytes = 0L,
                    pressureBytes = 0L,
                ),
            ),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            snapshot.dumpLines()
        }

        assertEquals("GPURuntimeBaselineSnapshot.cacheTelemetry.domain must be dump-safe", error.message)
    }

    private fun handleLikeToken(): String = "GPU" + "Buffer" + "Handle" + "0x" + "DEADBEEF"

    private fun externalTextureLikeToken(): String = "External" + "Texture" + "Handle" + "0x" + "CAFE12"
}
