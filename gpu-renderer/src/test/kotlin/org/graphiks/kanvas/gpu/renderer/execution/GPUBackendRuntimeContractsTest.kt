package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandBinding
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandReference
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision

class GPUBackendRuntimeContractsTest {
    @Test
    fun `offscreen request requires positive dimensions and nonblank format`() {
        val request = GPUOffscreenTargetRequest(width = 320, height = 180, colorFormat = "rgba8unorm")

        assertEquals(320, request.width)
        assertEquals(180, request.height)
        assertEquals("rgba8unorm", request.colorFormat)
        assertFailsWith<IllegalArgumentException> {
            GPUOffscreenTargetRequest(width = 0, height = 180, colorFormat = "rgba8unorm")
        }
        assertFailsWith<IllegalArgumentException> {
            GPUOffscreenTargetRequest(width = 320, height = -1, colorFormat = "rgba8unorm")
        }
        assertFailsWith<IllegalArgumentException> {
            GPUOffscreenTargetRequest(width = 320, height = 180, colorFormat = "")
        }
    }

    @Test
    fun `native surface binding requires positive size and nonblank pointer label keys`() {
        val binding = GPUNativeSurfaceBinding(
            platform = GPUNativePlatform.AppKitMetalLayer,
            width = 1280,
            height = 720,
            pointerLabels = mapOf("layerHandle" to 0L),
        )

        assertEquals(GPUNativePlatform.AppKitMetalLayer, binding.platform)
        assertEquals(0L, binding.pointerLabels.getValue("layerHandle"))
        assertFailsWith<IllegalArgumentException> {
            GPUNativeSurfaceBinding(
                platform = GPUNativePlatform.AppKitMetalLayer,
                width = 1280,
                height = 720,
                pointerLabels = emptyMap(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUNativeSurfaceBinding(
                platform = GPUNativePlatform.AppKitMetalLayer,
                width = 0,
                height = 720,
                pointerLabels = mapOf("nsLayer" to 42L),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUNativeSurfaceBinding(
                platform = GPUNativePlatform.AppKitMetalLayer,
                width = 1280,
                height = 720,
                pointerLabels = mapOf("" to 42L),
            )
        }
    }

    @Test
    fun `clear color stores normalized channel values`() {
        val color = GPUClearColor(red = 0.1, green = 0.2, blue = 0.3, alpha = 1.0)

        assertEquals(0.1, color.red)
        assertEquals(1.0, color.alpha)
        assertFailsWith<IllegalArgumentException> {
            GPUClearColor(red = -0.01, green = 0.2, blue = 0.3, alpha = 1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUClearColor(red = 0.1, green = 1.01, blue = 0.3, alpha = 1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUClearColor(red = 0.1, green = 0.2, blue = -0.01, alpha = 1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUClearColor(red = 0.1, green = 0.2, blue = 0.3, alpha = 1.01)
        }
    }

    @Test
    fun `runtime telemetry defaults to zero counters and deterministic dump`() {
        val telemetry = GPUBackendRuntimeTelemetry()

        assertEquals(0L, telemetry.renderPasses)
        assertEquals(0L, telemetry.offscreenPasses)
        assertEquals(0L, telemetry.windowPasses)
        assertEquals(0L, telemetry.submissions)
        assertEquals(0L, telemetry.commandBuffers)
        assertEquals(0L, telemetry.buffersCreated)
        assertEquals(0L, telemetry.texturesCreated)
        assertEquals(0L, telemetry.intermediateTexturesCreated)
        assertEquals(0L, telemetry.destinationCopies)
        assertEquals(0L, telemetry.destinationReadbackSnapshots)
        assertEquals(0L, telemetry.msaaTargets)
        assertEquals(0L, telemetry.msaaResolves)
        assertEquals(0L, telemetry.bindGroupsCreated)
        assertEquals(0L, telemetry.samplersCreated)
        assertEquals(0L, telemetry.queueWrites)
        assertEquals(0L, telemetry.uniformSlabsCreated)
        assertEquals(0L, telemetry.uniformSlabBytesAllocated)
        assertEquals(0L, telemetry.uniformSlabFallbacks)
        assertEquals(
            listOf(
                "gpu-runtime.telemetry renderPasses=0 offscreenPasses=0 windowPasses=0 " +
                    "submissions=0 commandBuffers=0 buffersCreated=0 texturesCreated=0 " +
                    "intermediateTexturesCreated=0 destinationCopies=0 destinationReadbackSnapshots=0 " +
                    "msaaTargets=0 msaaResolves=0 bindGroupsCreated=0 samplersCreated=0 queueWrites=0 uniformSlabsCreated=0 " +
                    "uniformSlabBytesAllocated=0 uniformSlabFallbacks=0 passBatchPlans=0 " +
                    "passBatchesAccepted=0 passBatchCuts=0 passBatchPackets=0",
            ),
            telemetry.dumpLines(),
        )
        assertTrue(!telemetry.dumpLines().joinToString("\n").contains("@"))
    }

    @Test
    fun `runtime telemetry rejects negative counters`() {
        assertFailsWith<IllegalArgumentException> {
            GPUBackendRuntimeTelemetry(renderPasses = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUBackendRuntimeTelemetry(queueWrites = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUBackendRuntimeTelemetry(commandBuffers = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUBackendRuntimeTelemetry(intermediateTexturesCreated = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUBackendRuntimeTelemetry(destinationCopies = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUBackendRuntimeTelemetry(destinationReadbackSnapshots = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUBackendRuntimeTelemetry(msaaTargets = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUBackendRuntimeTelemetry(msaaResolves = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUBackendRuntimeTelemetry(uniformSlabsCreated = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUBackendRuntimeTelemetry(uniformSlabBytesAllocated = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUBackendRuntimeTelemetry(uniformSlabFallbacks = -1L)
        }
    }

    @Test
    fun `backend session defaults expose empty telemetry and no capabilities`() {
        val session = NoopBackendSession()

        assertEquals(GPUBackendRuntimeTelemetry.Empty, session.runtimeTelemetry)
        assertEquals(GPUBackendRuntimeTelemetry.Empty.dumpLines(), session.runtimeTelemetryDumpLines)
        assertEquals(emptyList(), session.queueDumpLines)
        assertEquals(
            listOf(
                "gpu-phase0.baseline label=session renderPasses=0 offscreenPasses=0 windowPasses=0 " +
                    "submissions=0 commandBuffers=0 buffersCreated=0 texturesCreated=0 " +
                    "bindGroupsCreated=0 samplersCreated=0 queueWrites=0 uniformSlabsCreated=0 " +
                    "uniformSlabBytesAllocated=0 uniformSlabFallbacks=0",
            ),
            session.phase0BaselineDumpLines,
        )
        assertEquals(session.phase0BaselineDumpLines, session.phase0EvidenceDumpLines)
        assertEquals(null, session.capabilities)
    }

    @Test
    fun `uniform payload draw requires provider materialized uniform and bind group bridge`() {
        val accepted = GPUBackendUniformPayloadDraw(
            uniformBytes = byteArrayOf(1, 2, 3, 4),
            materialization = payloadMaterialization(
                GPUMaterializedCommandOperandKind.UniformBuffer,
                GPUMaterializedCommandOperandKind.BindGroup,
            ),
            scissorX = 0,
            scissorY = 0,
            scissorWidth = 4,
            scissorHeight = 4,
        )

        assertEquals(listOf("payload-upload", "bind-group"), accepted.materializedOperandLabels)
        assertFailsWith<IllegalArgumentException> {
            GPUBackendUniformPayloadDraw(
                uniformBytes = byteArrayOf(1, 2, 3, 4),
                materialization = payloadMaterialization(GPUMaterializedCommandOperandKind.UniformBuffer),
                scissorX = 0,
                scissorY = 0,
                scissorWidth = 4,
                scissorHeight = 4,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUBackendUniformPayloadDraw(
                uniformBytes = byteArrayOf(1, 2),
                materialization = payloadMaterialization(
                    GPUMaterializedCommandOperandKind.UniformBuffer,
                    GPUMaterializedCommandOperandKind.BindGroup,
                ),
                scissorX = 0,
                scissorY = 0,
                scissorWidth = 4,
                scissorHeight = 4,
            )
        }
    }

    private fun payloadMaterialization(
        vararg kinds: GPUMaterializedCommandOperandKind,
    ): GPUResourceMaterializationDecision.Materialized =
        GPUResourceMaterializationDecision.Materialized(
            resources = emptyList(),
            operandBridge = kinds.map { kind ->
                GPUMaterializedCommandOperandBinding(
                    packetId = "packet-1",
                    commandLabel = "setBindGroup",
                    operand = GPUMaterializedCommandOperandReference(
                        label = kind.testLabel(),
                        kind = kind,
                        descriptorHash = "descriptor:${kind.name}",
                        deviceGeneration = 1,
                        ownerScope = "payload-scope:pass-a",
                        usageLabels = listOf("uniform"),
                        invalidationPolicy = "pass-end",
                        evidenceFacts = if (kind == GPUMaterializedCommandOperandKind.UniformBuffer) {
                            mapOf("byteSize" to "4")
                        } else {
                            emptyMap()
                        },
                    ),
                )
            },
        )

    private fun GPUMaterializedCommandOperandKind.testLabel(): String =
        when (this) {
            GPUMaterializedCommandOperandKind.UniformBuffer -> "payload-upload"
            GPUMaterializedCommandOperandKind.BindGroup -> "bind-group"
            else -> "other-${name.lowercase()}"
        }

    private class NoopBackendSession : GPUBackendSession {
        override val adapterInfo: GPUBackendAdapterSummary? = null

        override fun createOffscreenTarget(request: GPUOffscreenTargetRequest): GPUBackendOffscreenTarget =
            error("NoopBackendSession cannot create offscreen targets")

        override fun createWindowSurface(binding: GPUNativeSurfaceBinding): GPUBackendWindowSurface =
            error("NoopBackendSession cannot create window surfaces")

        override fun close() = Unit
    }
}
