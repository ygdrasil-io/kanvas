package org.graphiks.kanvas.gpu.renderer.recording

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand

/** Verifies R5 recording, task-list, ordering, and replay policy evidence for the first FillRect route. */
class GPURecorderTest {
    /** Accepted FillRect capture closes into immutable pre-materialization recording evidence only. */
    @Test
    fun `accepted fill rect records analysis dump compatibility key and prematerialization render task`() {
        val recorder = GPURecorder(
            recordingId = GPURecordingID("recording.accepted"),
            capabilities = firstSliceCapabilities(),
        )

        recorder.record(acceptedFillRect(commandIdValue = 3))
        val recording = recorder.close()

        assertEquals("analysis.recording.accepted", recording.analysis.analysisId)
        assertEquals("analysis.fill_rect.3", recording.analysis.records.single().recordId)
        assertEquals(recording.analysisDecisionDump.decisionHash, recording.analysisHash)
        assertContains(
            recording.analysisDecisionDump.lines,
            "decision:candidate:analysis.fill_rect.3:native.fill_rect.solid",
        )
        assertContains(recording.routeDiagnostics, "route:native.fill_rect.solid")
        assertContains(recording.featureAssumptions, "capability:first_slice.fill_rect.native=supported")

        val task = assertIs<GPUTask.Render>(recording.taskList.tasks.single())
        assertEquals("task.render.3", task.taskId)
        assertEquals("pass.root.3", task.passId)
        assertEquals("analysis.fill_rect.3", task.analysisRecordId)
        assertEquals(listOf("rect.fill.coverage"), task.renderStepIds)
        assertEquals(listOf("pending.pipeline.fill_rect.solid.rgba8unorm.src_over"), task.pipelineKeyHashes)
        assertTrue(task.preMaterialization)
        assertEquals(emptyList(), task.materializedResourceLabels)
        assertEquals(emptyList(), recording.taskList.dependencies)
        assertFalse(recording.taskList.tasks.any { it is GPUTask.PrepareResources })
        assertFalse(recording.taskList.tasks.any { it is GPUTask.Upload })
        assertFalse(recording.taskList.tasks.any { it is GPUTask.Copy })

        assertContains(
            recording.taskList.dumpLines(),
            "task:render:task.render.3:pass.root.3:analysis.fill_rect.3:pre_materialization",
        )

        val compatibilityDump = recording.compatibilityKey.dump()
        assertEquals(recording.compatibilityKey.keyHash, compatibilityDump.keyHash)
        assertContains(compatibilityDump.lines, "replayPolicy=one-shot")
        assertContains(compatibilityDump.lines, "targetFormatClass=rgba8unorm")
        assertContains(compatibilityDump.lines, "resourceTopologyClass=pre_materialization.no_concrete_resources")
    }

    /** Refused analysis remains visible as a refused task and terminal recording diagnostic. */
    @Test
    fun `refused fill rect records refused task and terminal diagnostics without render work`() {
        val recorder = GPURecorder(
            recordingId = GPURecordingID("recording.refused"),
            capabilities = firstSliceCapabilities(),
        )

        recorder.record(
            acceptedFillRect(commandIdValue = 8).copy(
                clip = GPUClipFacts.complexStack(bounds = GPUBounds(0f, 0f, 16f, 16f)),
            ),
        )
        val recording = recorder.close()

        val task = assertIs<GPUTask.Refused>(recording.taskList.tasks.single())
        assertEquals("unsupported.clip.complex_stack", task.diagnostic.code)
        assertEquals(GPURecordingID("recording.refused"), task.diagnostic.recordingId)
        assertEquals("task.refused.8", task.diagnostic.taskId)
        assertTrue(task.diagnostic.terminal)
        assertEquals(listOf(task.diagnostic), recording.taskList.diagnostics)
        assertFalse(recording.taskList.tasks.any { it is GPUTask.Render })
        assertContains(
            recording.analysisDecisionDump.lines,
            "decision:refuse:analysis.fill_rect.8:unsupported.clip.complex_stack",
        )
        assertContains(recording.routeDiagnostics, "refused:unsupported.clip.complex_stack")
    }

    /** First-slice recordings are one-shot even when the target key is otherwise identical. */
    @Test
    fun `one shot recording refuses replay with stable diagnostic and key dump available`() {
        val recorder = GPURecorder(
            recordingId = GPURecordingID("recording.one-shot"),
            capabilities = firstSliceCapabilities(),
        )

        recorder.record(acceptedFillRect(commandIdValue = 10))
        val recording = recorder.close()

        val replay = assertIs<GPURecordingReplayResult.Refused>(
            recording.checkReplayCompatibility(recording.compatibilityKey),
        )

        assertEquals("replay.one_shot_recording", replay.diagnostic.code)
        assertEquals(GPURecordingID("recording.one-shot"), replay.diagnostic.recordingId)
        assertTrue(replay.diagnostic.terminal)
        assertEquals(recording.compatibilityKey.keyHash, replay.compatibilityKeyDump.keyHash)
    }

    /** Multiple commands and recordings expose explicit render-order and insertion dependency tokens. */
    @Test
    fun `ordered commands and ordered recordings expose dependency token evidence`() {
        val recorder = GPURecorder(
            recordingId = GPURecordingID("recording.sequence"),
            capabilities = firstSliceCapabilities(),
        )

        recorder.record(acceptedFillRect(commandIdValue = 1, paintOrder = 0))
        recorder.record(acceptedFillRect(commandIdValue = 2, paintOrder = 1))
        val recording = recorder.close()

        val dependency = recording.taskList.dependencies.single()
        assertEquals("task.render.1", dependency.fromTaskId)
        assertEquals("task.render.2", dependency.toTaskId)
        assertEquals("render-order", dependency.dependencyKind)
        assertEquals("recording.recording.sequence.render.0->1", dependency.useTokenLabel)
        assertContains(
            recording.taskList.dumpLines(),
            "dependency:render-order:task.render.1->task.render.2:recording.recording.sequence.render.0->1",
        )

        val first = singleCommandRecording("recording.ordered-a", commandIdValue = 11)
        val second = singleCommandRecording("recording.ordered-b", commandIdValue = 12)
        val ordered = GPURecordingOrder.orderedRecordings(
            recordings = listOf(first, second),
            targetScope = GPUFrameScope("frame.unit"),
        )

        assertEquals(GPURecordingID("recording.ordered-a"), ordered[0].recordingId)
        assertEquals(0L, ordered[0].insertionOrder)
        assertEquals(emptyList(), ordered[0].dependencyTokens)
        assertEquals(GPURecordingID("recording.ordered-b"), ordered[1].recordingId)
        assertEquals(1L, ordered[1].insertionOrder)
        val token = ordered[1].dependencyTokens.single()
        assertEquals(GPURecordingID("recording.ordered-a"), token.fromRecordingId)
        assertEquals(GPURecordingID("recording.ordered-b"), token.toRecordingId)
        assertEquals("recording-order", token.dependencyKind)
        assertEquals("ordered.recording.ordered-a->recording.ordered-b", token.tokenLabel)
    }

    /** Builds one closed first-route recording for ordered-recording fixtures. */
    private fun singleCommandRecording(recordingIdValue: String, commandIdValue: Int): GPURecording {
        val recorder = GPURecorder(
            recordingId = GPURecordingID(recordingIdValue),
            capabilities = firstSliceCapabilities(),
        )
        recorder.record(acceptedFillRect(commandIdValue = commandIdValue))
        return recorder.close()
    }

    /** Accepted first-route command with optional paint order. */
    private fun acceptedFillRect(
        commandIdValue: Int,
        paintOrder: Int = 0,
    ): NormalizedDrawCommand.FillRect =
        GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(commandIdValue),
            rect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = firstRouteTarget,
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
            transform = GPUTransformFacts.identity(),
            layer = GPULayerFacts.root(target = firstRouteTarget),
            paintOrder = paintOrder,
            source = GPUCommandSource(adapter = "unit-test", operation = "fillRect"),
        )

    /** Capability snapshot that enables only the first native FillRect route. */
    private fun firstSliceCapabilities(): GPUCapabilities =
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
            snapshotId = "first-route-test",
        )

    private companion object {
        /** Shared accepted target for first-route recording fixtures. */
        val firstRouteTarget = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
    }
}
