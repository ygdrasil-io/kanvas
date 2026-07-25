package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUOrderingFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class GPUPreparedSurfaceSemanticBuilderTest {
    @Test
    fun `gathers core image core into one ordered heterogeneous semantic map`() {
        val fixture = mixedFixture()

        val result = GPUPreparedSurfaceSemanticBuilder.gather(
                visualCommands = fixture.visuals,
                recording = fixture.recording,
                targetBounds = bounds,
                imageArtifactsByCommandId = mapOf(1 to fixture.artifact),
                blendAuthorityPolicy = GPUCorePrimitiveBlendAuthorityPolicy.InventoryHarness,
            )
        val gathered = assertIs<GPUPreparedSurfaceSemanticGatherResult.Gathered>(
            result,
            (result as? GPUPreparedSurfaceSemanticGatherResult.Refused)?.diagnostic.toString(),
        )

        assertEquals(listOf(0, 1, 2), gathered.semanticsByCommandId.keys.toList())
        assertIs<GPUDrawSemanticPayload.CorePrimitive>(gathered.semanticsByCommandId.getValue(0))
        assertIs<GPUDrawSemanticPayload.SampledImage>(gathered.semanticsByCommandId.getValue(1))
        assertIs<GPUDrawSemanticPayload.CorePrimitive>(gathered.semanticsByCommandId.getValue(2))
    }

    @Test
    fun `duplicate command identity refuses without returning a partial map`() {
        val fixture = mixedFixture()
        val duplicate = fixture.visuals + fixture.visuals.last().copy(
            normalized = (fixture.visuals.last().normalized as NormalizedDrawCommand.FillRect).copy(),
        )

        val refused = assertIs<GPUPreparedSurfaceSemanticGatherResult.Refused>(
            GPUPreparedSurfaceSemanticBuilder.gather(
                visualCommands = duplicate,
                recording = fixture.recording,
                targetBounds = bounds,
                imageArtifactsByCommandId = mapOf(1 to fixture.artifact),
                blendAuthorityPolicy = GPUCorePrimitiveBlendAuthorityPolicy.InventoryHarness,
            ),
        )

        assertEquals("invalid.surface.prepared.semantic-command-bijection", refused.diagnostic.code.value)
    }

    @Test
    fun `extra recording command identities refuse the semantic bijection`() {
        val fixture = mixedFixture()

        val refused = assertIs<GPUPreparedSurfaceSemanticGatherResult.Refused>(
            GPUPreparedSurfaceSemanticBuilder.gather(
                visualCommands = fixture.visuals.dropLast(1),
                recording = fixture.recording,
                targetBounds = bounds,
                imageArtifactsByCommandId = mapOf(1 to fixture.artifact),
                blendAuthorityPolicy = GPUCorePrimitiveBlendAuthorityPolicy.InventoryHarness,
            ),
        )

        assertEquals("invalid.surface.prepared.semantic-command-bijection", refused.diagnostic.code.value)
    }

    @Test
    fun `native bitmap image step is refused until it has its own prepared semantic route`() {
        val fixture = mixedFixture(nativeImage = true)

        val refused = assertIs<GPUPreparedSurfaceSemanticGatherResult.Refused>(
            GPUPreparedSurfaceSemanticBuilder.gather(
                visualCommands = fixture.visuals,
                recording = fixture.recording,
                targetBounds = bounds,
                imageArtifactsByCommandId = mapOf(1 to fixture.artifact),
                blendAuthorityPolicy = GPUCorePrimitiveBlendAuthorityPolicy.InventoryHarness,
            ),
        )

        assertEquals("invalid.surface.prepared.image-recording-authority", refused.diagnostic.code.value)
    }

    private fun mixedFixture(nativeImage: Boolean = false): MixedFixture {
        val image = Image(
            width = 3,
            height = 1,
            sourceId = "semantic-image",
            pixels = byteArrayOf(1, 2, 3, -1, 4, 5, 6, -1, 7, 8, 9, -1),
            alphaType = AlphaType.PREMUL,
        )
        val operations = listOf(
            rect(1f),
            DisplayOp.DrawImage(
                image,
                Rect.fromLTRB(0f, 0f, 3f, 1f),
                Rect.fromLTRB(4f, 2f, 10f, 6f),
                null,
                Matrix33.identity(),
                ClipStack.WideOpen,
            ),
            rect(12f),
        )
        val initial = GPUFramePathApiInventory.plan(
            operations,
            GPUTargetFacts(32, 24, "rgba8unorm"),
            RenderConfig.DEFAULT,
            capabilities(nativeImage),
        )
        val artifact = (
            GPUPreparedSurfaceImageSource.prepare(image) as GPUPreparedImageArtifactResult.Ready
            ).artifact
        val coreFirst = initial.visualCommands.first()
        val coreLast = initial.visualCommands.last().let { visual ->
            val command = visual.normalized as NormalizedDrawCommand.FillRect
            visual.copy(
                normalized = command.copy(
                    commandId = GPUDrawCommandID(2),
                    ordering = GPUOrderingFacts(2, false, false),
                ),
            )
        }
        val imageCommand = (operations[1] as DisplayOp.DrawImage)
            .toImageRectCommand(GPUDrawCommandID(1), GPUTargetFacts(32, 24, "rgba8unorm"))
            .copy(
                material = (
                    (operations[1] as DisplayOp.DrawImage)
                        .toImageRectCommand(GPUDrawCommandID(1), GPUTargetFacts(32, 24, "rgba8unorm"))
                        .material as GPUMaterialDescriptor.ImageDraw
                    ).copy(rgbaPixels = artifact.tightRgba8BytesForUpload()),
                pixelsRowBytes = 12,
                pixelsGeneration = artifact.sourceGeneration,
                pixelsContentHash = artifact.contentHash,
                pixelsProvenance = "semantic-builder-test",
                ordering = GPUOrderingFacts(1, false, false),
            )
        val imageVisual = coreFirst.copy(
            normalized = imageCommand,
            targetSpaceBounds = imageCommand.bounds,
        )
        val visuals = listOf(coreFirst, imageVisual, coreLast)
        val recorder = GPURecorder(
            GPURecordingID("prepared-surface-semantics"),
            GPUFrameID(23),
            capabilities(nativeImage),
        )
        visuals.forEach { recorder.record(it.normalized) }
        return MixedFixture(visuals, recorder.close(), artifact)
    }

    private fun rect(left: Float) = DisplayOp.DrawRect(
        Rect.fromLTRB(left, 1f, left + 6f, 7f),
        Paint.fill(Color.RED).copy(antiAlias = false),
        Matrix33.identity(),
        ClipStack.WideOpen,
    )

    private fun capabilities(nativeImage: Boolean = false): GPUCapabilities {
        val base = GPUProductFlagConfig().buildCapabilities()
        return GPUCapabilities(
            implementation = base.implementation,
            facts = base.facts.filterNot { fact ->
                !nativeImage && fact.name == "first_slice.bitmap_rect.native"
            } + listOf(
                GPUCapabilityFact(
                    "first_slice.fill_rect.native",
                    "test",
                    "supported",
                    true,
                    "prepared-surface-semantics",
                ),
                GPUCapabilityFact(
                    "first_slice.draw_image_rect.prepared",
                    "test",
                    "supported",
                    true,
                    "prepared-surface-semantics",
                ),
            ),
            knownUnsupportedFacts = base.knownUnsupportedFacts,
            snapshotId = "${base.snapshotId}:prepared-surface-semantics",
            limits = base.limits,
        )
    }

    private data class MixedFixture(
        val visuals: List<GPUFramePathVisualCommand>,
        val recording: org.graphiks.kanvas.gpu.renderer.recording.GPURecording,
        val artifact: org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageUploadArtifact,
    )

    private companion object {
        val bounds = GPUPixelBounds(0, 0, 32, 24)
    }
}
