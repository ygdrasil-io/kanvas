package org.graphiks.kanvas.gpu.renderer.consumer

import io.ygdrasil.webgpu.GPUTextureFormat
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.recording.GPUColorGlyphFrameRecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPUColorGlyphFrameRecordingRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUColorGlyphFrameRecordingResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUResolvedColorGlyphLayer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import kotlin.uuid.Uuid

/** Compiles from a consumer package so only the intended production API is reachable. */
class GPUColorGlyphFrameRecorderTest {
    @Test
    fun `public recorder snapshots resolved layers and derives canonical semantic and task list`() {
        val atlasBytes = byteArrayOf(255.toByte(), 128.toByte())
        val firstColor = mutableListOf(1f, 0f, 0f, 1f)
        val layers = mutableListOf(
            layer(11u, GPUPixelBounds(0, 0, 1, 1), GPUPixelBounds(1, 0, 3, 2), firstColor),
            layer(12u, GPUPixelBounds(1, 0, 2, 1), GPUPixelBounds(0, 1, 2, 3), listOf(0f, 0f, 0.5f, 0.5f)),
        )

        val result = GPUColorGlyphFrameRecorder().record(request(atlasBytes = atlasBytes, layers = layers))
        val recorded = assertIs<GPUColorGlyphFrameRecordingResult.Recorded>(result)
        val semantic = assertIs<GPUDrawSemanticPayload.ColorGlyph>(recorded.semantic)
        val render = assertIs<GPUTask.Render>(recorded.taskList.tasks.single { it is GPUTask.Render })

        assertSame(semantic, render.drawPackets.single().semanticPayload)
        assertEquals(listOf(GPUTask.PrepareResources::class, GPUTask.Render::class), recorded.taskList.tasks.map { it::class })
        assertEquals("text.colrv0.composite", render.drawPackets.single().renderStepId.value)
        assertEquals("clear", render.loadStore.loadOp)
        assertEquals(GPUStorePlan.Store, render.loadStore.storePlan)
        assertEquals("opaque-black", render.loadStore.clearColorLabel)
        assertEquals(GPUPixelBounds(0, 0, 4, 3), semantic.scissorBounds)
        assertEquals(
            listOf(0f, 0f, 0f, 0f, 4f, 0f, 1f, 0f, 4f, 3f, 1f, 1f, 0f, 3f, 0f, 1f),
            semantic.vertexData,
        )
        assertEquals(listOf(0, 1, 2, 0, 2, 3), semantic.indexData)
        val uniform = ByteBuffer.wrap(semantic.uniformBytes.map(Int::toByte).toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(4f, uniform.getFloat(0))
        assertEquals(3f, uniform.getFloat(4))
        assertEquals(2, uniform.getInt(8))
        assertEquals(1f, uniform.getFloat(528))
        assertEquals(0f, uniform.getFloat(532))
        assertEquals(2f, uniform.getFloat(536))
        assertEquals(2f, uniform.getFloat(540))

        atlasBytes.fill(0)
        firstColor.fill(0f)
        layers.clear()

        assertEquals(listOf(255, 128), semantic.atlasA8Bytes)
        assertEquals(listOf(1f, 0f, 0f, 1f), semantic.layers.first().premultipliedRgba)
        assertEquals(2, semantic.layers.size)
        assertTrue(semantic.canonicalHash.isNotBlank())
    }

    @Test
    fun `public recorder exposes optional planned readback without exposing native execution seams`() {
        val result = GPUColorGlyphFrameRecorder().record(
            request(readbackRequestId = GPUReadbackRequestID("readback.consumer.color-glyph")),
        )

        val recorded = assertIs<GPUColorGlyphFrameRecordingResult.Recorded>(result)
        assertEquals(
            listOf(GPUTask.PrepareResources::class, GPUTask.Render::class, GPUTask.Readback::class),
            recorded.taskList.tasks.map { it::class },
        )

        val publicTypes = listOf(
            GPUColorGlyphFrameRecorder::class.java,
            GPUColorGlyphFrameRecordingRequest::class.java,
            GPUResolvedColorGlyphLayer::class.java,
            GPUColorGlyphFrameRecordingResult::class.java,
        )
        publicTypes.forEach { type -> assertTrue(Modifier.isPublic(type.modifiers), type.name) }
        val leakedSignatureTypes = publicTypes
            .flatMap { type ->
                type.methods.flatMap { method -> listOf(method.returnType.name) + method.parameterTypes.map { it.name } } +
                    type.constructors.flatMap { constructor -> constructor.parameterTypes.map { it.name } }
            }
            .filter { typeName ->
                listOf("Preflight", "Executor", "PreparedGPUFrame", "Wgpu4k", "NativeFramePayload")
                    .any(typeName::contains)
            }
        assertEquals(emptyList(), leakedSignatureTypes)
    }

    @Test
    fun `public recorder turns malformed resolved input and planning limits into typed refusals`() {
        val alias = planKey()
        val cases = listOf(
            "missing-layers" to request(layers = emptyList()),
            "too-many-layers" to request(layers = List(17) { index ->
                layer(
                    glyphId = (20 + index).toUInt(),
                    atlasBounds = GPUPixelBounds(index, 0, index + 1, 1),
                    deviceBounds = GPUPixelBounds(0, 0, 1, 1),
                    color = listOf(1f, 1f, 1f, 1f),
                )
            }, atlasBytes = ByteArray(17) { 255.toByte() }, atlasWidth = 17),
            "aliased-artifacts" to request(planArtifactKey = alias, atlasArtifactKey = alias),
            "atlas-byte-count" to request(atlasBytes = byteArrayOf(255.toByte())),
            "overlapping-placement" to request(
                layers = listOf(
                    layer(11u, GPUPixelBounds(0, 0, 2, 1), GPUPixelBounds(0, 0, 2, 1), listOf(1f, 0f, 0f, 1f)),
                    layer(12u, GPUPixelBounds(1, 0, 2, 1), GPUPixelBounds(0, 0, 2, 1), listOf(0f, 0f, 1f, 1f)),
                ),
            ),
            "unresolved-foreground" to request(
                layers = listOf(layer(useForeground = true, foregroundResolved = false)),
            ),
            "missing-limits" to request(capabilities = capabilities().copy(limits = null)),
            "budget" to request(configuredAggregateBudgetBytes = 0L),
        )

        cases.forEach { (name, malformed) ->
            val refused = assertIs<GPUColorGlyphFrameRecordingResult.Refused>(
                GPUColorGlyphFrameRecorder().record(malformed),
                name,
            )
            assertTrue(refused.diagnostic.code.value.isNotBlank(), name)
        }
        val limitsRefusal = assertIs<GPUColorGlyphFrameRecordingResult.Refused>(
            GPUColorGlyphFrameRecorder().record(request(capabilities = capabilities().copy(limits = null))),
        )
        assertEquals("unsupported.recording.color_glyph_limits_unavailable", limitsRefusal.diagnostic.code.value)
    }

    private fun request(
        atlasBytes: ByteArray = byteArrayOf(255.toByte(), 128.toByte()),
        atlasWidth: Int = 2,
        layers: List<GPUResolvedColorGlyphLayer> = listOf(
            layer(11u, GPUPixelBounds(0, 0, 1, 1), GPUPixelBounds(0, 0, 2, 2), listOf(1f, 0f, 0f, 1f)),
            layer(12u, GPUPixelBounds(1, 0, 2, 1), GPUPixelBounds(2, 1, 4, 3), listOf(0f, 0f, 1f, 1f)),
        ),
        planArtifactKey: GPUTextArtifactKey = planKey(),
        atlasArtifactKey: GPUTextArtifactKey = atlasKey(),
        capabilities: GPUCapabilities = capabilities(),
        readbackRequestId: GPUReadbackRequestID? = null,
        configuredAggregateBudgetBytes: Long = 1L shl 30,
    ) = GPUColorGlyphFrameRecordingRequest(
        frameId = GPUFrameID(42L),
        recordingId = GPURecordingID("recording.consumer.color-glyph"),
        capabilities = capabilities,
        deviceGeneration = GPUDeviceGenerationID(9L),
        target = GPUFrameTargetRef("target.consumer.color-glyph"),
        commandIdValue = 41,
        planArtifactKey = planArtifactKey,
        atlasArtifactKey = atlasArtifactKey,
        atlasA8Bytes = atlasBytes,
        atlasWidth = atlasWidth,
        atlasHeight = 1,
        layers = layers,
        targetBounds = GPUPixelBounds(0, 0, 4, 3),
        scissorBounds = GPUPixelBounds(0, 0, 4, 3),
        readbackRequestId = readbackRequestId,
        configuredAggregateBudgetBytes = configuredAggregateBudgetBytes,
    )

    private fun layer(
        glyphId: UInt = 11u,
        atlasBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 1, 1),
        deviceBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 2, 2),
        color: List<Float> = listOf(1f, 0f, 0f, 1f),
        useForeground: Boolean = false,
        foregroundResolved: Boolean = true,
    ) = GPUResolvedColorGlyphLayer(
        layerGlyphID = glyphId,
        paletteIndex = 0,
        atlasBounds = atlasBounds,
        deviceBounds = deviceBounds,
        premultipliedRgba = color,
        useForeground = useForeground,
        foregroundResolved = foregroundResolved,
        strikeSize = 48f,
        strikeSubpixelX = 0,
        strikeSubpixelY = 0,
    )

    private fun planKey() = artifactKey("550e8400-e29b-41d4-a716-446655440081", 7, "consumer-plan")

    private fun atlasKey() = artifactKey("550e8400-e29b-41d4-a716-446655440082", 2, "consumer-atlas")

    private fun artifactKey(id: String, generation: Int, fingerprint: String) = GPUTextArtifactKey(
        GPUTextArtifactID(Uuid.parse(id)),
        GPUTextArtifactGeneration(generation),
        fingerprint,
    )

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "consumer-color-glyph")),
        snapshotId = "capabilities-consumer-9",
        limits = GPULimits(8192, 256, 256, maxBufferSize = 1L shl 30),
        supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm, GPUTextureFormat.R8Unorm),
        rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
    )
}
