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
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUOrderingFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.text.GPUTextDiagnosticCodes

/** Verifies R5 recording, task-list, ordering, and replay policy evidence for the first FillRect route. */
class GPURecorderTest {
    /** Accepted FillRect capture closes into immutable pre-materialization recording evidence only. */
    @Test
    fun `accepted fill rect records analysis dump compatibility key and prematerialization render task`() {
        val recorder = GPURecorder(
            recordingId = GPURecordingID("recording.accepted"),
            frameId = GPUFrameID(17),
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
        assertEquals("task.render.3", task.taskId.value)
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
            frameId = GPUFrameID(17),
            capabilities = firstSliceCapabilities(),
        )

        recorder.record(
            acceptedFillRect(commandIdValue = 8).copy(
                clip = GPUClipFacts.complexStack(bounds = GPUBounds(0f, 0f, 16f, 16f)),
            ),
        )
        val recording = recorder.close()

        val task = assertIs<GPUTask.Refused>(recording.taskList.tasks.single())
        assertEquals("unsupported.clip.complex_stack", task.diagnostic.code.value)
        assertEquals(GPURecordingID("recording.refused"), task.recordingId)
        assertEquals("task.refused.8", task.taskId.value)
        assertTrue(task.diagnostic.isTerminal)
        assertEquals(listOf(task.diagnostic), recording.taskList.diagnostics)
        assertFalse(recording.taskList.tasks.any { it is GPUTask.Render })
        assertContains(
            recording.analysisDecisionDump.lines,
            "decision:refuse:analysis.fill_rect.8:unsupported.clip.complex_stack",
        )
        assertContains(recording.routeDiagnostics, "refused:unsupported.clip.complex_stack")
    }

    /** Accepted FillRRect capture closes into immutable pre-materialization recording evidence only. */
    @Test
    fun `accepted fill rrect records analysis dump compatibility key and prematerialization render task`() {
        val recorder = GPURecorder(
            recordingId = GPURecordingID("recording.rrect-accepted"),
            frameId = GPUFrameID(17),
            capabilities = firstSliceRRectCapabilities(),
        )

        recorder.record(acceptedFillRRect(commandIdValue = 14))
        val recording = recorder.close()

        assertEquals("analysis.fill_rrect.14", recording.analysis.records.single().recordId)
        assertContains(
            recording.analysisDecisionDump.lines,
            "decision:candidate:analysis.fill_rrect.14:native.fill_rrect.solid",
        )
        assertContains(recording.routeDiagnostics, "route:native.fill_rrect.solid")
        assertContains(recording.featureAssumptions, "capability:first_slice.fill_rrect.native=supported")

        val task = assertIs<GPUTask.Render>(recording.taskList.tasks.single())
        assertEquals("task.render.14", task.taskId.value)
        assertEquals("pass.root.14", task.passId)
        assertEquals("analysis.fill_rrect.14", task.analysisRecordId)
        assertEquals(listOf("rrect.fill.coverage"), task.renderStepIds)
        assertEquals(listOf("pending.pipeline.fill_rrect.solid.rgba8unorm.src_over"), task.pipelineKeyHashes)
        assertTrue(task.preMaterialization)
        assertEquals(emptyList(), task.materializedResourceLabels)

        assertContains(recording.compatibilityKey.dump().lines, "commandShapeVersion=2")
        assertContains(recording.compatibilityKey.dump().lines, "targetFormatClass=rgba8unorm")
    }

    /** Text handoff commands stay visible as terminal refusals until a text GPU route is promoted. */
    @Test
    fun `draw text run records explicit refused task without render work`() {
        val recorder = GPURecorder(
            recordingId = GPURecordingID("recording.text-refused"),
            frameId = GPUFrameID(17),
            capabilities = firstSliceCapabilities(),
        )

        recorder.record(drawTextRun(commandIdValue = 13))
        val recording = recorder.close()

        val task = assertIs<GPUTask.Refused>(recording.taskList.tasks.single())
        assertEquals("unsupported.text.draw_run_route_unavailable", task.diagnostic.code.value)
        assertEquals(GPURecordingID("recording.text-refused"), task.recordingId)
        assertEquals("task.refused.13", task.taskId.value)
        assertTrue(task.diagnostic.isTerminal)
        assertFalse(recording.taskList.tasks.any { it is GPUTask.Render })
        assertContains(
            recording.analysisDecisionDump.lines,
            "decision:refuse:analysis.draw_text_run.13:unsupported.text.draw_run_route_unavailable",
        )
        assertContains(recording.routeDiagnostics, "refused:unsupported.text.draw_run_route_unavailable")
    }

    @Test
    fun `draw text run records stable diagnostics for skia-like payload leakage`() {
        val recorder = GPURecorder(
            recordingId = GPURecordingID("recording.text-leak"),
            frameId = GPUFrameID(17),
            capabilities = firstSliceCapabilities(),
        )

        recorder.record(
            drawTextRun(commandIdValue = 7).copy(
                glyphRunDescriptorRefs = listOf("SkTextBlob#7"),
                uploadDependencyFacts = listOf("cpu-rendered-texture"),
            ),
        )
        val recording = recorder.close()

        assertEquals(
            listOf(
                "unsupported.text.draw_run_route_unavailable",
                GPUTextDiagnosticCodes.SK_TYPE_LEAKED,
                GPUTextDiagnosticCodes.CPU_RENDERED_TEXTURE_FORBIDDEN,
            ),
            recording.analysis.records.single().diagnostics.map { it.code },
        )
    }

    /** First-slice recordings are one-shot even when the target key is otherwise identical. */
    @Test
    fun `one shot recording refuses replay with stable diagnostic and key dump available`() {
        val recorder = GPURecorder(
            recordingId = GPURecordingID("recording.one-shot"),
            frameId = GPUFrameID(17),
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
            frameId = GPUFrameID(17),
            capabilities = firstSliceCapabilities(),
        )

        recorder.record(acceptedFillRect(commandIdValue = 1, paintOrder = 0))
        recorder.record(acceptedFillRect(commandIdValue = 2, paintOrder = 1))
        val recording = recorder.close()

        val dependency = recording.taskList.dependencies.single()
        assertEquals("task.render.1", dependency.fromTaskId.value)
        assertEquals("task.render.2", dependency.toTaskId.value)
        assertEquals("render-order", dependency.dependencyKind)
        assertEquals("recording.recording.sequence.render.0->1", dependency.useToken?.value)
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

    @Test
    fun `refused command remains in canonical paint order between accepted render tasks`() {
        val recorder = GPURecorder(
            recordingId = GPURecordingID("recording.mixed-order"),
            frameId = GPUFrameID(17),
            capabilities = firstSliceCapabilities(),
        )
        recorder.record(acceptedFillRect(commandIdValue = 1, paintOrder = 0))
        recorder.record(
            acceptedFillRect(commandIdValue = 2, paintOrder = 1).copy(
                clip = GPUClipFacts.complexStack(bounds = GPUBounds(0f, 0f, 16f, 16f)),
            ),
        )
        recorder.record(acceptedFillRect(commandIdValue = 3, paintOrder = 2))

        val recording = recorder.close()
        val plan = GPUFramePlanner.plan(recording.taskList)

        assertEquals(
            listOf(
                GPUTaskID("task.render.1") to GPUTaskID("task.refused.2"),
                GPUTaskID("task.refused.2") to GPUTaskID("task.render.3"),
            ),
            recording.taskList.dependencies.map { dependency ->
                dependency.fromTaskId to dependency.toTaskId
            },
        )
        assertEquals(
            listOf("task.render.1", "task.refused.2", "task.render.3"),
            plan.steps.map { step -> step.sourceTaskIds.single().value },
        )
        assertIs<GPUFrameStep.RefusedLeafDrawStep>(plan.steps[1])
    }

    /** Builds one closed first-route recording for ordered-recording fixtures. */
    private fun singleCommandRecording(recordingIdValue: String, commandIdValue: Int): GPURecording {
        val recorder = GPURecorder(
            recordingId = GPURecordingID(recordingIdValue),
            frameId = GPUFrameID(17),
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

    /** Accepted first-expansion rrect command with optional paint order. */
    private fun acceptedFillRRect(
        commandIdValue: Int,
        paintOrder: Int = 0,
    ): NormalizedDrawCommand.FillRRect =
        GPUFillRRectCommandBuilder.build(
            commandId = GPUDrawCommandID(commandIdValue),
            rrect = GPURRect(
                rect = GPURect(left = 2f, top = 3f, right = 22f, bottom = 25f),
                radiusX = 4f,
                radiusY = 5f,
            ),
            target = firstRouteTarget,
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
            transform = GPUTransformFacts.identity(),
            layer = GPULayerFacts.root(target = firstRouteTarget),
            paintOrder = paintOrder,
            source = GPUCommandSource(adapter = "unit-test", operation = "fillRRect"),
        )

    /** Text command fixture with dumpable command-owned facts and no backend route activation. */
    private fun drawTextRun(commandIdValue: Int): NormalizedDrawCommand.DrawTextRun {
        val bounds = GPUBounds(0f, 0f, 128f, 64f)
        val target = GPUTargetFacts(width = 128, height = 64, colorFormat = "rgba8unorm")
        return NormalizedDrawCommand.DrawTextRun(
            commandId = GPUDrawCommandID(commandIdValue),
            textLayoutResultId = "layout-$commandIdValue",
            glyphRunId = "glyph-run-$commandIdValue",
            glyphRunDescriptorRefs = listOf("glyph-run-$commandIdValue"),
            artifactRefs = emptyList(),
            artifactKeyHashes = emptyList(),
            atlasGenerationTokens = emptyList(),
            uploadDependencyFacts = emptyList(),
            routeDiagnostics = emptyList(),
            transform = GPUTransformFacts.identity(),
            clip = GPUClipFacts.wideOpen(bounds = bounds),
            layer = GPULayerFacts.root(target = target),
            material = GPUMaterialDescriptor.SolidColor(r = 0f, g = 0f, b = 0f, a = 1f),
            bounds = bounds,
            ordering = GPUOrderingFacts(
                paintOrder = commandIdValue,
                dependsOnDestination = false,
                requiresBarrier = false,
            ),
            source = GPUCommandSource(adapter = "unit-test", operation = "drawTextRun"),
        )
    }

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

    /** Capability snapshot that enables only the native FillRRect expansion route. */
    private fun firstSliceRRectCapabilities(): GPUCapabilities =
        firstSliceCapabilities().copy(
            facts = listOf(
                GPUCapabilityFact(
                    name = "first_slice.fill_rrect.native",
                    source = "unit-test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "rrect-route-fixture",
                ),
            ),
            snapshotId = "rrect-route-test",
        )

    private companion object {
        /** Shared accepted target for first-route recording fixtures. */
        val firstRouteTarget = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
    }
}
