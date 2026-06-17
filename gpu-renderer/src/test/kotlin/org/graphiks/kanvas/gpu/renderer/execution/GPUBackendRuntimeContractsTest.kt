package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
}
