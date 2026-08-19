package org.graphiks.kanvas.gpu.renderer.text

import org.graphiks.kanvas.font.atlas.AtlasRegion
import org.graphiks.kanvas.font.atlas.GlyphAtlasPlacement
import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlan
import org.graphiks.kanvas.font.glyph.GlyphStrikeKey
import org.graphiks.kanvas.font.handoff.GlyphDescriptor
import org.graphiks.kanvas.font.handoff.GlyphRunDescriptor
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUOrderingFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GPUTextA8RouteAcceptanceTest {
    @Test
    fun `recorder accepts prepared DrawTextRun without legacy atlas byte descriptor`() {
        val artifactRef = GPUTextArtifactRef(
            artifactType = "PreparedTextA8AtlasPage",
            artifactId = "prepared-page-1",
            artifactKeyHash = "prepared-page-content",
            generation = GPUTextArtifactGeneration(5),
            routeHint = "AtlasMaskSample",
        )
        val target = GPUTargetFacts(width = 128, height = 64, colorFormat = "rgba8unorm")
        val command = NormalizedDrawCommand.DrawTextRun(
            commandId = GPUDrawCommandID(0),
            textLayoutResultId = "prepared-layout",
            glyphRunId = "prepared-run",
            glyphRunDescriptorRefs = listOf("prepared-run"),
            glyphRunDescriptor = null,
            artifactRefs = listOf(artifactRef),
            artifactKeyHashes = listOf(artifactRef.artifactKeyHash),
            atlasGenerations = listOf(artifactRef.generation),
            uploadDependencyFacts = listOf("upload-before-sample:prepared-page-content"),
            routeDiagnostics = emptyList(),
            transform = GPUTransformFacts.identity(),
            clip = GPUClipFacts.wideOpen(bounds = GPUBounds(0f, 0f, 128f, 64f)),
            layer = GPULayerFacts.root(target),
            material = GPUMaterialDescriptor.SolidColor(1f, 1f, 1f, 1f),
            bounds = GPUBounds(1f, 2f, 17f, 20f),
            ordering = GPUOrderingFacts(0, false, false),
            source = GPUCommandSource("unit-test", "drawText.prepared"),
        )

        val recording = GPURecorder(
            recordingId = GPURecordingID("prepared-a8-no-legacy-descriptor"),
            frameId = org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID(25),
            capabilities = textA8Capabilities(),
        ).also { recorder -> recorder.record(command) }.close()

        assertTrue(recording.taskList.diagnostics.none { diagnostic -> diagnostic.isTerminal })
        assertEquals(
            "text.a8_mask.sample",
            recording.taskList.tasks.filterIsInstance<GPUTask.Render>()
                .single().drawPackets.single().renderStepId.value,
        )
    }

    @Test
    fun `recorder accepts DrawTextRun with valid A8 atlas descriptor`() {
        val strikeKey = GlyphStrikeKey(glyphId = 65, size = 16f, subpixelX = 0, subpixelY = 0)
        val placement = GlyphAtlasPlacement(
            strikeKey = strikeKey,
            region = AtlasRegion(x = 0, y = 0, width = 16, height = 16),
        )
        val glyphDesc = GlyphDescriptor(
            strikeKey = strikeKey,
            placement = placement,
            drawX = 0f,
            drawY = 0f,
        )
        val atlasPlan = GlyphAtlasUploadPlan.Accepted(
            atlasWidth = 64,
            atlasHeight = 64,
            atlasBytes = ByteArray(64 * 64),
            placements = listOf(placement),
        )
        val descriptor = GlyphRunDescriptor(
            glyphs = listOf(glyphDesc),
            atlasPlan = atlasPlan,
        )

        val artifactRef = GPUTextArtifactRef(
            artifactType = "GlyphAtlasArtifact",
            artifactId = "artifact-1",
            artifactKeyHash = "glyph-atlas-sha256",
            generation = GPUTextArtifactGeneration(1),
            routeHint = "AtlasMaskSample",
        )
        val command = NormalizedDrawCommand.DrawTextRun(
            commandId = GPUDrawCommandID(1),
            textLayoutResultId = "layout-1",
            glyphRunId = "run-1",
            glyphRunDescriptorRefs = listOf("run-1"),
            glyphRunDescriptor = descriptor,
            artifactRefs = listOf(artifactRef),
            artifactKeyHashes = listOf("glyph-atlas-sha256"),
            atlasGenerations = listOf(GPUTextArtifactGeneration(1)),
            uploadDependencyFacts = listOf("upload-before-sample"),
            routeDiagnostics = emptyList(),
            transform = GPUTransformFacts.identity(),
            clip = GPUClipFacts.wideOpen(bounds = GPUBounds(0f, 0f, 128f, 64f)),
            layer = GPULayerFacts.root(
                target = GPUTargetFacts(width = 128, height = 64, colorFormat = "rgba8unorm"),
            ),
            material = GPUMaterialDescriptor.SolidColor(r = 0f, g = 0f, b = 0f, a = 1f),
            bounds = GPUBounds(0f, 0f, 128f, 64f),
            ordering = GPUOrderingFacts(paintOrder = 1, dependsOnDestination = false, requiresBarrier = false),
            source = GPUCommandSource(adapter = "unit-test", operation = "drawTextRun"),
        )

        val recorder = GPURecorder(
            recordingId = GPURecordingID("test-a8-accept"),
            frameId = org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID(23),
            capabilities = textA8Capabilities(),
        )
        recorder.record(command)
        val recording = recorder.close()

        val tasks = recording.taskList.tasks
        assertTrue(tasks.any { it is GPUTask.Render }, "Expected at least one Render task")
        val renderTask = tasks.filterIsInstance<GPUTask.Render>().single()
        assertEquals("task.render.1", renderTask.taskId.value)
    }

    @Test
    fun `recorder refuses DrawTextRun with refused atlas plan`() {
        val strikeKey = GlyphStrikeKey(glyphId = 66, size = 16f, subpixelX = 0, subpixelY = 0)
        val glyphDesc = GlyphDescriptor(
            strikeKey = strikeKey,
            placement = GlyphAtlasPlacement(
                strikeKey = strikeKey,
                region = AtlasRegion(x = 0, y = 0, width = 32, height = 32),
            ),
            drawX = 10f,
            drawY = 20f,
        )
        val refusedPlan = GlyphAtlasUploadPlan.Refused(reason = "atlas overflow")
        val descriptor = GlyphRunDescriptor(
            glyphs = listOf(glyphDesc),
            atlasPlan = refusedPlan,
        )

        val command = NormalizedDrawCommand.DrawTextRun(
            commandId = GPUDrawCommandID(2),
            textLayoutResultId = "layout-2",
            glyphRunId = "run-2",
            glyphRunDescriptorRefs = listOf("run-2"),
            glyphRunDescriptor = descriptor,
            artifactRefs = emptyList(),
            artifactKeyHashes = emptyList(),
            atlasGenerations = emptyList(),
            uploadDependencyFacts = emptyList(),
            routeDiagnostics = emptyList(),
            transform = GPUTransformFacts.identity(),
            clip = GPUClipFacts.wideOpen(bounds = GPUBounds(0f, 0f, 128f, 64f)),
            layer = GPULayerFacts.root(
                target = GPUTargetFacts(width = 128, height = 64, colorFormat = "rgba8unorm"),
            ),
            material = GPUMaterialDescriptor.SolidColor(r = 0f, g = 0f, b = 0f, a = 1f),
            bounds = GPUBounds(0f, 0f, 128f, 64f),
            ordering = GPUOrderingFacts(paintOrder = 2, dependsOnDestination = false, requiresBarrier = false),
            source = GPUCommandSource(adapter = "unit-test", operation = "drawTextRun"),
        )

        val recorder = GPURecorder(
            recordingId = GPURecordingID("test-a8-refuse"),
            frameId = org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID(23),
            capabilities = textA8Capabilities(),
        )
        recorder.record(command)
        val recording = recorder.close()

        val tasks = recording.taskList.tasks
        assertTrue(tasks.all { it is GPUTask.Refused }, "Expected all Refused tasks")
        val refusedTask = assertIs<GPUTask.Refused>(tasks.single())
        assertEquals("task.refused.2", refusedTask.taskId.value)
    }

    private fun textA8Capabilities(): GPUCapabilities =
        GPUCapabilities(
            implementation = GPUImplementationIdentity(
                facadeName = "test-gpu",
                implementationName = "unit",
                adapterName = "fixture-adapter",
                deviceName = "fixture-device",
            ),
            facts = listOf(
                GPUCapabilityFact(
                    name = "first_slice.fill_rect.native",
                    source = "unit-test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "first-route-fixture",
                ),
            ),
            snapshotId = "text-a8-route-test",
        )
}
