package org.graphiks.kanvas.gpu.renderer.recording

import io.ygdrasil.webgpu.GPUTextureUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadMember
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroup
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupKey
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupingResult
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotMaterialization
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassDiagnostic
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPURefusalScope
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUComputePipelineKey
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceCopyRegion
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureCopyLayout
import org.graphiks.kanvas.gpu.renderer.resources.GPUUploadLayout
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity

/** Adversarial integrity contract for the immutable, deterministic Task 6 frame-plan boundary. */
class GPUFramePlanIntegrityTest {
    private val taskListFrameId = GPUFrameID(77)
    private val taskListCapabilitySeal = capabilitySeal(taskListFrameId)

    @Test
    fun `capability seal distinguishes unknown texture usage from explicitly unsupported usage`() {
        val frameId = GPUFrameID(78)
        val unknown = GPUFrameCapabilitySeal.capture(
            frameId = frameId,
            deviceGeneration = GPUDeviceGenerationID(3),
            capabilities = integrityCapabilities(supportedTextureUsage = null),
        )
        val explicitlyUnsupported = GPUFrameCapabilitySeal.capture(
            frameId = frameId,
            deviceGeneration = GPUDeviceGenerationID(3),
            capabilities = integrityCapabilities(supportedTextureUsage = GPUTextureUsage.None),
        )

        assertNotEquals(unknown.capabilitySnapshotHash, explicitlyUnsupported.capabilitySnapshotHash)
        assertNotEquals(unknown.sealHash, explicitlyUnsupported.sealHash)
    }

    @Test
    fun `frame plan and representative nested collections are JVM immutable`() {
        val firstPacket = packet(commandId = 1)
        val secondPacket = packet(commandId = 2)
        val render = GPUFrameStep.RenderPassStep(
            target = GPUFrameTargetRef("target.scene"),
            loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
            samplePlan = GPUSamplePlan.SingleSampleFrame,
            drawPackets = listOf(firstPacket, secondPacket),
            sourceTaskIds = listOf(GPUTaskID("task.render.1"), GPUTaskID("task.render.2")),
        )
        val firstRequest = texturePreparation("texture.a")
        val secondRequest = texturePreparation("texture.b")
        val prepare = GPUFrameStep.PrepareResourcesStep(
            requests = listOf(firstRequest, secondRequest),
            sourceTaskIds = listOf(GPUTaskID("task.prepare.1"), GPUTaskID("task.prepare.2")),
        )
        val frameId = GPUFrameID(91)
        val capabilitySeal = capabilitySeal(frameId)
        val plan = GPUFramePlan(
            frameId = frameId,
            capabilitySeal = capabilitySeal,
            recordingSeals = listOf(
                seal("recording.a", 0, capabilitySeal.sealHash),
                seal("recording.b", 1, capabilitySeal.sealHash),
            ),
            steps = listOf(render, prepare),
            memoryBudget = budgetWithEvidence(),
            diagnostics = listOf(diagnostic("diagnostic.a"), diagnostic("diagnostic.b")),
        )

        assertJvmImmutable(plan.steps, prepare)
        assertJvmImmutable(plan.recordingSeals, seal("recording.c", 2, capabilitySeal.sealHash))
        assertJvmImmutable(plan.diagnostics, diagnostic("diagnostic.c"))
        assertJvmImmutable(
            plan.elidedNoOpDraws,
            GPUFrameElidedNoOpDraw(
                taskId = GPUTaskID("task.noop"),
                packetId = GPUDrawPacketID("packet.noop"),
                commandId = GPUDrawCommandID(999),
                mode = GPUBlendMode.DST,
                reason = "immutability fixture",
            ),
        )
        assertJvmImmutable(render.drawPackets, packet(commandId = 3))
        assertJvmImmutable(render.sourceTaskIds, GPUTaskID("task.render.3"))
        assertJvmImmutable(prepare.requests, texturePreparation("texture.c"))
        assertJvmImmutable(prepare.sourceTaskIds, GPUTaskID("task.prepare.3"))
        assertJvmImmutable(firstRequest.usages, GPUFrameResourceUsage.StorageBinding)
        assertJvmImmutable(plan.memoryBudget.categoryTotals, GPUFrameMemoryCategory.DestinationSnapshot, 999L)
        assertJvmImmutable(plan.memoryBudget.deviceLimitFacts, capabilityFact("limit.c", "3"))
        assertJvmImmutable(plan.diagnostics.first().facts, "mutated", "true")
    }

    @Test
    fun `atomically refused frame plan cannot retain encodable steps`() {
        val packet = packet(6, blendPlan = executableBlend())
        val frameId = GPUFrameID(92)
        val capabilitySeal = capabilitySeal(frameId)

        assertFailsWith<IllegalArgumentException> {
            GPUFramePlan(
                frameId = frameId,
                capabilitySeal = capabilitySeal,
                recordingSeals = listOf(seal("recording.a", 0, capabilitySeal.sealHash)),
                steps = listOf(
                    GPUFrameStep.RenderPassStep(
                        target = GPUFrameTargetRef("target.scene"),
                        loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
                        samplePlan = GPUSamplePlan.SingleSampleFrame,
                        drawPackets = listOf(packet),
                        sourceTaskIds = listOf(GPUTaskID("task.render.6")),
                    ),
                ),
                memoryBudget = emptyBudget(),
                diagnostics = listOf(diagnostic("invalid.frame_plan.fixture")),
                atomicallyRefused = true,
            )
        }
    }

    @Test
    fun `frame plan dump preserves the complete capability seal evidence`() {
        val dump = renderPlan(packet(7, blendPlan = executableBlend())).dumpLines().joinToString("\n")

        assertTrue(dump.contains("capability frame=55"), dump)
        assertTrue(dump.contains("deviceGeneration=3"), dump)
        assertTrue(dump.contains("facade=integrity-test-facade"), dump)
        assertTrue(dump.contains("implementation=integrity-test-implementation"), dump)
        assertTrue(dump.contains("adapter=integrity-test-adapter"), dump)
        assertTrue(dump.contains("device=integrity-test-device"), dump)
        assertTrue(dump.contains("snapshotId=integrity-test-capabilities"), dump)
        assertTrue(dump.contains("snapshotHash="), dump)
        assertTrue(dump.contains("copyAsDraw=none"), dump)
    }

    @Test
    fun `task list and render batching collections are JVM immutable`() {
        val first = renderTask("task.render.1", "recording.a", listOf(packet(1), packet(2)))
        val second = renderTask("task.render.2", "recording.a", listOf(packet(3), packet(4)))
        val dependency = dependency(first, second)
        val taskList = taskList(
            tasks = listOf(first, second),
            dependencies = listOf(dependency, dependency.copy(reasonCode = "second.edge")),
        )

        assertJvmImmutable(taskList.tasks, first)
        assertJvmImmutable(taskList.dependencies, dependency)
        assertJvmImmutable(taskList.phaseOrder, GPUTaskPhase.Output)
        assertJvmImmutable(first.drawPackets, packet(5))
        assertJvmImmutable(
            first.batchEligibilityByPacketId,
            GPUDrawPacketID("packet.99"),
            eligibility(),
        )
    }

    @Test
    fun `canonical hash includes every uniform and resource slot fact`() {
        val base = packet(
            commandId = 1,
            uniform = uniformSlot("uniform.slot", "uniform.fp.a", 0),
            resource = resourceSlot("resource.slot", "resource.fp.a", 0),
        )
        val baseHash = renderPlan(base).stableHash()

        assertNotEquals(
            baseHash,
            renderPlan(base.copyPacket(uniform = uniformSlot("uniform.slot", "uniform.fp.b", 0))).stableHash(),
        )
        assertNotEquals(
            baseHash,
            renderPlan(base.copyPacket(uniform = uniformSlot("uniform.slot", "uniform.fp.a", 256))).stableHash(),
        )
        assertNotEquals(
            baseHash,
            renderPlan(base.copyPacket(resource = resourceSlot("resource.slot", "resource.fp.b", 0))).stableHash(),
        )
        assertNotEquals(
            baseHash,
            renderPlan(base.copyPacket(resource = resourceSlot("resource.slot", "resource.fp.a", 7))).stableHash(),
        )
    }

    @Test
    fun `canonical hash preserves structural boundaries nulls and resource subtypes`() {
        assertNotEquals(
            barrierPlan(reason = "x tokens=y", tokens = listOf("z")).stableHash(),
            barrierPlan(reason = "x", tokens = listOf("y tokens=z")).stableHash(),
        )
        assertNotEquals(
            barrierPlan(reason = "boundary", tokens = listOf("a,b")).stableHash(),
            barrierPlan(reason = "boundary", tokens = listOf("a", "b")).stableHash(),
        )
        assertNotEquals(
            renderPlan(packet(commandId = 1, scissor = null)).stableHash(),
            renderPlan(packet(commandId = 1, scissor = "none")).stableHash(),
        )
        assertNotEquals(
            copyPlan(GPUFrameTextureRef("resource.same")).stableHash(),
            copyPlan(GPUFrameBufferRef("resource.same")).stableHash(),
        )
    }

    @Test
    fun `composite refusal cannot consume an arbitrary task without typed scope membership`() {
        val child = renderTask("task.child", "recording.a", listOf(packet(10)))
        val composite = refusedComposite(
            taskId = "task.composite",
            recordingId = "recording.a",
            commandId = 11,
            consumedChildren = listOf(child.taskId),
        )

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(child, composite),
                dependencies = listOf(dependency(child, composite)),
            ),
        )

        assertTrue(plan.atomicallyRefused)
        assertTrue(plan.steps.isEmpty())
        assertEquals("invalid.frame_plan.composite_scope", plan.diagnostics.last().code.value)
    }

    @Test
    fun `destination consumer cannot be a child absorbed by a composite refusal`() {
        val scopeId = GPUCompositeScopeID("scope.destination-consumer")
        val token = GPUCompositeProvenanceToken("composite.child.destination-consumer")
        val destination = parentDestinationSnapshotTask(
            commandId = 50,
            renderTaskId = "task.render.destination-child",
        )
        val child = renderTask(
            taskId = "task.render.destination-child",
            recordingId = "recording.a",
            packets = listOf(packet(50, blendPlan = destinationReadBlend())),
            compositeMembership = GPUTaskCompositeMembership(
                scopeId = scopeId,
                parentCommandId = GPUDrawCommandID(500),
                childOrdinal = 0,
                provenanceToken = token,
            ),
        )
        val refused = refusedComposite(
            taskId = "task.refused.destination-parent",
            recordingId = "recording.a",
            commandId = 500,
            consumedChildren = listOf(child.taskId),
            compositeScopeId = scopeId,
            provenanceTokens = listOf(token),
        )

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, child, refused),
                dependencies = listOf(dependency(destination, child), dependency(child, refused)),
            ),
        )

        assertTrue(plan.atomicallyRefused)
        assertTrue(plan.steps.isEmpty())
        assertEquals("invalid.frame_plan.destination_consumer_binding", plan.diagnostics.last().code.value)
    }

    @Test
    fun `composite refusal cannot consume a child from another recording`() {
        val compositeScopeId = GPUCompositeScopeID("scope.cross-recording")
        val provenanceToken = GPUCompositeProvenanceToken("composite.child.0")
        val child = renderTask(
            "task.child",
            "recording.a",
            listOf(packet(10)),
            compositeMembership = GPUTaskCompositeMembership(
                scopeId = compositeScopeId,
                parentCommandId = GPUDrawCommandID(11),
                childOrdinal = 0,
                provenanceToken = provenanceToken,
            ),
        )
        val composite = refusedComposite(
            taskId = "task.composite",
            recordingId = "recording.b",
            commandId = 11,
            consumedChildren = listOf(child.taskId),
            compositeScopeId = compositeScopeId,
            provenanceTokens = listOf(provenanceToken),
        )
        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(child, composite),
                dependencies = listOf(dependency(child, composite)),
                seals = listOf(seal("recording.a", 0), seal("recording.b", 1)),
            ),
        )

        assertTrue(plan.atomicallyRefused)
        assertTrue(plan.steps.isEmpty())
        assertEquals("invalid.frame_plan.composite_scope", plan.diagnostics.last().code.value)
    }

    @Test
    fun `render task rejects non-render packet roles atomically`() {
        val forbiddenRoles = listOf(
            GPUDrawPacketRole.Compute,
            GPUDrawPacketRole.Copy,
            GPUDrawPacketRole.Upload,
            GPUDrawPacketRole.Readback,
            GPUDrawPacketRole.Discard,
        )

        forbiddenRoles.forEachIndexed { index, role ->
            val task = renderTask(
                taskId = "task.render.$role",
                recordingId = "recording.a",
                packets = listOf(packet(commandId = 20 + index, role = role)),
            )
            val plan = GPUFramePlanner.plan(taskList(tasks = listOf(task)))

            assertTrue(plan.atomicallyRefused, role.name)
            assertTrue(plan.steps.isEmpty(), role.name)
            assertEquals("invalid.frame_plan.render_packet_role", plan.diagnostics.last().code.value, role.name)
        }
    }

    @Test
    fun `render task with mixed pass identities refuses instead of throwing`() {
        val task = renderTask(
            taskId = "task.render.mixed-pass",
            recordingId = "recording.a",
            packets = listOf(packet(1, passId = "pass.a"), packet(2, passId = "pass.b")),
        )

        val plan = GPUFramePlanner.plan(taskList(tasks = listOf(task)))

        assertTrue(plan.atomicallyRefused)
        assertTrue(plan.steps.isEmpty())
        assertEquals("invalid.frame_plan.render_packet_pass", plan.diagnostics.last().code.value)
    }

    @Test
    fun `surface output is unique and terminal`() {
        val firstOutput = outputTask("task.output.1", "surface.1")
        val secondOutput = outputTask("task.output.2", "surface.2")
        val duplicatePlan = GPUFramePlanner.plan(
            taskList(tasks = listOf(firstOutput, secondOutput)),
        )
        assertTrue(duplicatePlan.atomicallyRefused)
        assertTrue(duplicatePlan.steps.isEmpty())
        assertEquals("invalid.frame_plan.output_count", duplicatePlan.diagnostics.last().code.value)

        val render = renderTask("task.render.after-output", "recording.a", listOf(packet(1)))
        val earlyOutputPlan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(firstOutput, render),
                dependencies = listOf(dependency(firstOutput, render)),
            ),
        )
        assertTrue(earlyOutputPlan.atomicallyRefused)
        assertTrue(earlyOutputPlan.steps.isEmpty())
        assertEquals("invalid.frame_plan.output_not_terminal", earlyOutputPlan.diagnostics.last().code.value)
    }

    @Test
    fun `valid surface output leaves post submit present as the final frame step`() {
        val render = renderTask("task.render", "recording.a", listOf(packet(1)))
        val output = outputTask("task.output", "surface.output")

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(render, output),
                dependencies = listOf(dependency(render, output)),
            ),
        )

        assertFalse(plan.atomicallyRefused)
        assertTrue(plan.steps.last() is GPUFrameStep.PostSubmitPresentAction)
    }

    @Test
    fun `target transitions form a nested enter composite return stack`() {
        val transitions = listOf(
            transition("enter.layer", "root", "layer", GPUTargetTransitionKind.EnterChild),
            transition("enter.nested", "layer", "nested", GPUTargetTransitionKind.EnterChild),
            transition("composite.nested", "layer", "nested", GPUTargetTransitionKind.CompositeChild),
            transition("return.nested", "layer", "nested", GPUTargetTransitionKind.ReturnToParent),
            transition("composite.layer", "root", "layer", GPUTargetTransitionKind.CompositeChild),
            transition("return.layer", "root", "layer", GPUTargetTransitionKind.ReturnToParent),
        )
        val plan = GPUFramePlanner.plan(
            taskList(tasks = transitions, dependencies = orderedDependencies(transitions)),
        )

        assertFalse(plan.atomicallyRefused)
        assertEquals(6, plan.steps.filterIsInstance<GPUFrameStep.TargetTransitionStep>().size)
    }

    @Test
    fun `invalid or unclosed target transition stacks refuse atomically`() {
        val returnWithoutEnter = transition(
            "return.without-enter",
            "root",
            "layer",
            GPUTargetTransitionKind.ReturnToParent,
        )
        val returnPlan = GPUFramePlanner.plan(taskList(tasks = listOf(returnWithoutEnter)))
        assertTrue(returnPlan.atomicallyRefused)
        assertEquals("invalid.frame_plan.target_transition", returnPlan.diagnostics.last().code.value)

        val unclosedEnter = transition("enter.unclosed", "root", "layer", GPUTargetTransitionKind.EnterChild)
        val unclosedPlan = GPUFramePlanner.plan(taskList(tasks = listOf(unclosedEnter)))
        assertTrue(unclosedPlan.atomicallyRefused)
        assertEquals("invalid.frame_plan.target_transition", unclosedPlan.diagnostics.last().code.value)

        val crossed = listOf(
            transition("enter.layer", "root", "layer", GPUTargetTransitionKind.EnterChild),
            transition("enter.nested", "layer", "nested", GPUTargetTransitionKind.EnterChild),
            transition("composite.wrong", "root", "layer", GPUTargetTransitionKind.CompositeChild),
        )
        val crossedPlan = GPUFramePlanner.plan(
            taskList(tasks = crossed, dependencies = orderedDependencies(crossed)),
        )
        assertTrue(crossedPlan.atomicallyRefused)
        assertEquals("invalid.frame_plan.target_transition", crossedPlan.diagnostics.last().code.value)
    }

    @Test
    fun `active child refuses compute copy upload and readback work touching its parent`() {
        val parent = GPUFrameTargetRef("root")
        val staging = GPUFrameBufferRef("buffer.staging")
        val work = listOf(
            "compute" to GPUTask.Compute(
                taskId = GPUTaskID("task.compute.parent"),
                recordingId = GPURecordingID("recording.a"),
                phase = GPUTaskPhase.Compute,
                target = parent,
                resourceUses = listOf(
                    GPUFrameResourceUse(
                        resource = parent,
                        role = GPUFrameResourceRole.SceneTarget,
                        usage = GPUFrameResourceUsage.StorageBinding,
                        lifetime = GPUFrameResourceLifetime.FrameLocal,
                        write = true,
                    ),
                ),
                dispatches = listOf(GPUComputeDispatch(GPUComputePipelineKey("compute.parent"), 1, 1, 1)),
            ),
            "copy" to GPUTask.Copy(
                taskId = GPUTaskID("task.copy.parent"),
                recordingId = GPURecordingID("recording.a"),
                phase = GPUTaskPhase.Copy,
                source = GPUFrameBufferRef("buffer.source"),
                destination = parent,
                regions = listOf(GPUResourceCopyRegion(0, 0, null, 16)),
            ),
            "upload" to GPUTask.Upload(
                taskId = GPUTaskID("task.upload.parent"),
                recordingId = GPURecordingID("recording.a"),
                phase = GPUTaskPhase.Upload,
                staging = staging,
                destination = parent,
                layout = GPUUploadLayout(0, 256, 1, 256),
            ),
            "readback" to GPUTask.Readback(
                taskId = GPUTaskID("task.readback.parent"),
                recordingId = GPURecordingID("recording.a"),
                phase = GPUTaskPhase.Readback,
                source = parent,
                staging = staging,
                request = GPUFrameReadbackRequest(
                    requestId = GPUReadbackRequestID("readback.parent"),
                    sourceBounds = GPUPixelBounds(0, 0, 4, 4),
                    pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
                    outputColorInterpretation = GPUColorInterpretation("srgb-premul"),
                ),
            ),
        )

        val violations = work.mapNotNull { (label, parentWork) ->
            val tasks = listOf(
                transition("enter.parent-$label", "root", "layer", GPUTargetTransitionKind.EnterChild),
                parentWork,
                transition("composite.parent-$label", "root", "layer", GPUTargetTransitionKind.CompositeChild),
                transition("return.parent-$label", "root", "layer", GPUTargetTransitionKind.ReturnToParent),
            )
            val plan = GPUFramePlanner.plan(
                taskList(tasks = tasks, dependencies = orderedDependencies(tasks)),
            )

            when {
                !plan.atomicallyRefused -> "$label: frame accepted"
                plan.steps.isNotEmpty() -> "$label: refusal retained ${plan.steps.size} steps"
                plan.diagnostics.last().code.value != "invalid.frame_plan.target_transition" ->
                    "$label: diagnostic=${plan.diagnostics.last().code.value}"
                else -> null
            }
        }

        assertTrue(violations.isEmpty(), violations.joinToString(separator = "\n"))
    }

    @Test
    fun `active child refuses destination snapshot work that touches its parent`() {
        val destination = parentDestinationSnapshotTask(commandId = 50, renderTaskId = "task.render.child")
        val childRender = renderTask(
            taskId = "task.render.child",
            recordingId = "recording.a",
            packets = listOf(
                packet(
                    commandId = 50,
                    targetStateHash = "target.layer.rgba8",
                    blendPlan = destinationReadBlend(),
                ),
            ),
            target = GPUFrameTargetRef("layer"),
        )
        val tasks = listOf(
            transition("enter.parent-destination", "root", "layer", GPUTargetTransitionKind.EnterChild),
            destination,
            childRender,
            transition(
                "composite.parent-destination",
                "root",
                "layer",
                GPUTargetTransitionKind.CompositeChild,
            ),
            transition("return.parent-destination", "root", "layer", GPUTargetTransitionKind.ReturnToParent),
        )
        val plan = GPUFramePlanner.plan(
            taskList(tasks = tasks, dependencies = orderedDependencies(tasks)),
        )

        assertTrue(plan.atomicallyRefused)
        assertTrue(plan.steps.isEmpty())
        assertEquals("invalid.frame_plan.target_transition", plan.diagnostics.last().code.value)
    }

    @Test
    fun `composed child accepts only its exact return to parent transition`() {
        val lateChildRender = renderTask(
            taskId = "task.render.after-composite",
            recordingId = "recording.a",
            packets = listOf(packet(55, blendPlan = executableBlend())),
            target = GPUFrameTargetRef("layer"),
        )
        val tasks = listOf(
            transition("enter.after-composite", "root", "layer", GPUTargetTransitionKind.EnterChild),
            transition(
                "composite.after-composite",
                "root",
                "layer",
                GPUTargetTransitionKind.CompositeChild,
            ),
            lateChildRender,
            transition("return.after-composite", "root", "layer", GPUTargetTransitionKind.ReturnToParent),
        )

        val plan = GPUFramePlanner.plan(
            taskList(tasks = tasks, dependencies = orderedDependencies(tasks)),
        )

        assertTrue(plan.atomicallyRefused)
        assertTrue(plan.steps.isEmpty())
        assertEquals("invalid.frame_plan.target_transition", plan.diagnostics.last().code.value)
    }

    @Test
    fun `render task with mixed target state hashes refuses atomically`() {
        val task = renderTask(
            taskId = "task.render.mixed-target-state",
            recordingId = "recording.a",
            packets = listOf(
                packet(60, targetStateHash = "target.scene.rgba8", blendPlan = executableBlend()),
                packet(61, targetStateHash = "target.scene.rgba16", blendPlan = executableBlend()),
            ),
        )

        val plan = GPUFramePlanner.plan(taskList(tasks = listOf(task)))

        assertTrue(plan.atomicallyRefused)
        assertTrue(plan.steps.isEmpty())
        assertEquals("invalid.frame_plan.render_packet_target", plan.diagnostics.last().code.value)
    }

    @Test
    fun `packet diagnostics cannot be mutated to rewrite a frame plan hash`() {
        val packet = packet(
            commandId = 70,
            diagnostics = listOf(
                passDiagnostic("diagnostic.first", 70),
                passDiagnostic("diagnostic.second", 70),
            ),
        )
        val plan = renderPlan(packet)
        val originalHash = plan.stableHash()

        val mutationFailure = runCatching {
            (packet.diagnostics as MutableList<GPUPassDiagnostic>) += passDiagnostic("diagnostic.mutated", 70)
        }.exceptionOrNull()

        assertEquals(originalHash, plan.stableHash(), "packet diagnostics rewrote canonical frame evidence")
        assertTrue(
            mutationFailure is UnsupportedOperationException || mutationFailure is ClassCastException,
            "GPUDrawPacket.diagnostics must be JVM immutable",
        )
    }

    @Test
    fun `dependency evidence changes frame plan dump and hash`() {
        val first = renderTask("task.render.dependency.1", "recording.a", listOf(packet(80, blendPlan = executableBlend())))
        val second = renderTask("task.render.dependency.2", "recording.a", listOf(packet(81, blendPlan = executableBlend())))
        val baselineDependency = dependency(first, second)
        val changedDependency = baselineDependency.copy(
            dependencyKind = "integrity-order-changed",
            useToken = GPUTaskUseToken("changed-token"),
            reasonCode = "integrity.changed",
        )

        val baseline = GPUFramePlanner.plan(
            taskList(tasks = listOf(first, second), dependencies = listOf(baselineDependency)),
        )
        val changed = GPUFramePlanner.plan(
            taskList(tasks = listOf(first, second), dependencies = listOf(changedDependency)),
        )

        assertNotEquals(baseline.dumpLines(), changed.dumpLines(), "dependency evidence is absent from dump")
        assertNotEquals(baseline.stableHash(), changed.stableHash(), "dependency evidence is absent from hash")
    }

    @Test
    fun `phase order evidence changes frame plan dump and hash`() {
        val task = renderTask(
            "task.render.phase-order",
            "recording.a",
            listOf(packet(90, blendPlan = executableBlend())),
        )
        val baseline = GPUFramePlanner.plan(
            taskList(tasks = listOf(task), phaseOrder = GPUTaskPhase.entries),
        )
        val changed = GPUFramePlanner.plan(
            taskList(tasks = listOf(task), phaseOrder = GPUTaskPhase.entries.reversed()),
        )

        assertNotEquals(baseline.dumpLines(), changed.dumpLines(), "phase order evidence is absent from dump")
        assertNotEquals(baseline.stableHash(), changed.stableHash(), "phase order evidence is absent from hash")
    }

    @Test
    fun `render pass step rejects batches that are not an exact ordered packet partition`() {
        val first = packet(100)
        val second = packet(101)
        val invalidPartitions = listOf(
            "missing" to listOf(
                GPUFrameRenderBatch(
                    batchId = "batch.incomplete",
                    kind = GPUPassBatchKind.SolidFill,
                    packets = listOf(first),
                    sourceTaskIds = listOf(GPUTaskID("task.render.100")),
                ),
            ),
            "reordered" to listOf(
                GPUFrameRenderBatch(
                    batchId = "batch.reordered",
                    kind = GPUPassBatchKind.SolidFill,
                    packets = listOf(second, first),
                    sourceTaskIds = listOf(GPUTaskID("task.render.101"), GPUTaskID("task.render.100")),
                ),
            ),
        )

        val acceptedMalformedPartitions = invalidPartitions.mapNotNull { (label, batches) ->
            val failure = runCatching {
                GPUFrameStep.RenderPassStep(
                    target = GPUFrameTargetRef("target.scene"),
                    loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
                    samplePlan = GPUSamplePlan.SingleSampleFrame,
                    drawPackets = listOf(first, second),
                    sourceTaskIds = listOf(GPUTaskID("task.render.100"), GPUTaskID("task.render.101")),
                    batches = batches,
                )
            }.exceptionOrNull()
            if (failure is IllegalArgumentException) null else "$label: ${failure?.javaClass?.simpleName ?: "accepted"}"
        }

        assertTrue(
            acceptedMalformedPartitions.isEmpty(),
            acceptedMalformedPartitions.joinToString(separator = "\n"),
        )
    }

    @Test
    fun `render pass step rejects mixed target states even when constructed directly`() {
        val first = packet(105, targetStateHash = "target.scene.rgba8")
        val second = packet(106, targetStateHash = "target.scene.rgba16")

        assertFailsWith<IllegalArgumentException> {
            GPUFrameStep.RenderPassStep(
                target = GPUFrameTargetRef("target.scene"),
                loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
                samplePlan = GPUSamplePlan.SingleSampleFrame,
                drawPackets = listOf(first, second),
                sourceTaskIds = listOf(GPUTaskID("task.render.105"), GPUTaskID("task.render.106")),
            )
        }
    }

    @Test
    fun `render pass step rejects batch source ids inconsistent with the step`() {
        val first = packet(110)
        val second = packet(111)

        assertFailsWith<IllegalArgumentException> {
            GPUFrameStep.RenderPassStep(
                target = GPUFrameTargetRef("target.scene"),
                loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
                samplePlan = GPUSamplePlan.SingleSampleFrame,
                drawPackets = listOf(first, second),
                sourceTaskIds = listOf(GPUTaskID("task.render.110"), GPUTaskID("task.render.111")),
                batches = listOf(
                    GPUFrameRenderBatch(
                        batchId = "batch.inconsistent-source",
                        kind = GPUPassBatchKind.SolidFill,
                        packets = listOf(first, second),
                        sourceTaskIds = listOf(GPUTaskID("task.render.110")),
                    ),
                ),
            )
        }
    }

    @Test
    fun `buffer preparation rejects byte size disagreement with its descriptor`() {
        assertFailsWith<IllegalArgumentException> {
            GPUResourcePreparationRequest(
                resource = GPUFrameBufferRef("buffer.mismatch"),
                descriptor = GPUFrameBufferDescriptor(byteSize = 1_024, alignmentBytes = 4),
                role = GPUFrameResourceRole.UploadStaging,
                usages = setOf(GPUFrameResourceUsage.CopySource),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = 16,
                diagnosticLabel = "buffer.mismatch",
            )
        }
    }

    private fun renderPlan(packet: GPUDrawPacket): GPUFramePlan =
        framePlan(
            GPUFrameStep.RenderPassStep(
                target = GPUFrameTargetRef("target.scene"),
                loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
                samplePlan = GPUSamplePlan.SingleSampleFrame,
                drawPackets = listOf(packet),
                sourceTaskIds = listOf(GPUTaskID("task.render")),
            ),
        )

    private fun barrierPlan(reason: String, tokens: List<String>): GPUFramePlan =
        framePlan(
            GPUFrameStep.DependencyBarrierStep(
                orderedUseTokens = tokens.map(::GPUTaskUseToken),
                reasonCode = reason,
                sourceTaskIds = listOf(GPUTaskID("task.barrier")),
            ),
        )

    private fun copyPlan(source: GPUFrameResourceRef): GPUFramePlan =
        framePlan(
            GPUFrameStep.CopyResourceStep(
                source = source,
                destination = GPUFrameBufferRef("resource.destination"),
                regions = listOf(GPUResourceCopyRegion(0, 0, null, 16)),
                sourceTaskIds = listOf(GPUTaskID("task.copy")),
            ),
        )

    private fun framePlan(step: GPUFrameStep): GPUFramePlan {
        val frameId = GPUFrameID(55)
        val capabilitySeal = capabilitySeal(frameId)
        return GPUFramePlan(
            frameId = frameId,
            capabilitySeal = capabilitySeal,
            recordingSeals = listOf(seal("recording.a", 0, capabilitySeal.sealHash)),
            steps = listOf(step),
            memoryBudget = emptyBudget(),
            diagnostics = emptyList(),
        )
    }

    private fun renderTask(
        taskId: String,
        recordingId: String,
        packets: List<GPUDrawPacket>,
        compositeMembership: GPUTaskCompositeMembership? = null,
        target: GPUFrameTargetRef = GPUFrameTargetRef("target.scene"),
    ): GPUTask.Render = GPUTask.Render(
        taskId = GPUTaskID(taskId),
        recordingId = GPURecordingID(recordingId),
        phase = GPUTaskPhase.Render,
        target = target,
        loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
        samplePlan = GPUSamplePlan.SingleSampleFrame,
        drawPackets = packets,
        batchEligibilityByPacketId = packets.associate { it.packetId to eligibility() },
        compositeMembership = compositeMembership,
    )

    private fun refusedComposite(
        taskId: String,
        recordingId: String,
        commandId: Int,
        consumedChildren: List<GPUTaskID>,
        compositeScopeId: GPUCompositeScopeID = GPUCompositeScopeID("scope.$commandId"),
        provenanceTokens: List<GPUCompositeProvenanceToken> = consumedChildren.indices.map { index ->
            GPUCompositeProvenanceToken("composite.child.$index")
        },
    ): GPUTask.Refused = GPUTask.Refused(
        taskId = GPUTaskID(taskId),
        recordingId = GPURecordingID(recordingId),
        phase = GPUTaskPhase.Refusal,
        commandId = GPUDrawCommandID(commandId),
        scope = GPURefusalScope.RefusedCompositeCommand,
        compositeScopeId = compositeScopeId,
        provenanceTokens = provenanceTokens,
        consumedChildTaskIds = consumedChildren,
        diagnostic = diagnostic("unsupported.composite.$commandId"),
    )

    private fun outputTask(taskId: String, output: String): GPUTask.Output = GPUTask.Output(
        taskId = GPUTaskID(taskId),
        recordingId = GPURecordingID("recording.a"),
        phase = GPUTaskPhase.Output,
        scene = GPUFrameTargetRef("target.scene"),
        descriptor = GPUSurfaceOutputDescriptor(
            output = GPUSurfaceOutputRef(output),
            width = 16,
            height = 16,
            format = GPUColorFormat("rgba8unorm"),
            targetGeneration = 1,
        ),
    )

    private fun transition(
        taskId: String,
        parent: String,
        child: String,
        kind: GPUTargetTransitionKind,
    ): GPUTask.TargetTransition = GPUTask.TargetTransition(
        taskId = GPUTaskID("task.transition.$taskId"),
        recordingId = GPURecordingID("recording.a"),
        phase = GPUTaskPhase.Transition,
        parent = GPUFrameTargetRef(parent),
        child = GPUFrameTargetRef(child),
        transitionKind = kind,
    )

    private fun orderedDependencies(tasks: List<GPUTask>): List<GPUTaskDependency> =
        tasks.zipWithNext().map { (from, to) -> dependency(from, to) }

    private fun dependency(from: GPUTask, to: GPUTask): GPUTaskDependency = GPUTaskDependency(
        fromTaskId = from.taskId,
        toTaskId = to.taskId,
        dependencyKind = "integrity-order",
        useToken = GPUTaskUseToken("${from.taskId.value}->${to.taskId.value}"),
        reasonCode = "integrity.order",
    )

    private fun taskList(
        tasks: List<GPUTask>,
        dependencies: List<GPUTaskDependency> = emptyList(),
        seals: List<GPURecordingSeal> = listOf(seal("recording.a", 0)),
        phaseOrder: List<GPUTaskPhase> = GPUTaskPhase.entries,
    ): GPUTaskList = GPUTaskList(
        frameId = taskListFrameId,
        capabilitySeal = taskListCapabilitySeal,
        recordingSeals = seals,
        expectedReplayKeyHash = "replay.same",
        tasks = tasks,
        dependencies = dependencies,
        phaseOrder = phaseOrder,
        memoryBudget = emptyBudget(),
    )

    private fun packet(
        commandId: Int,
        passId: String = "pass.scene",
        role: GPUDrawPacketRole = GPUDrawPacketRole.Shading,
        uniform: GPUUniformPayloadSlot? = null,
        resource: GPUResourceBindingSlot? = null,
        scissor: String? = "scissor.default",
        targetStateHash: String = "target.scene.rgba8",
        blendPlan: GPUBlendPlan = GPUBlendPlan.NoOp(GPUBlendMode.DST, "integrity"),
        diagnostics: List<GPUPassDiagnostic> = emptyList(),
    ): GPUDrawPacket = GPUDrawPacket(
        packetId = GPUDrawPacketID("packet.$commandId"),
        commandIdValue = commandId,
        analysisRecordId = "analysis.$commandId",
        passId = passId,
        layerId = "root",
        bindingListId = "bindings.$commandId",
        insertionReasonCode = "paint-order",
        sortKey = commandId.toLong(),
        sortKeyPreimage = "paint-order:$commandId",
        renderStepId = GPURenderStepID("rect.fill"),
        renderStepVersion = 1,
        role = role,
        blendPlan = blendPlan,
        renderPipelineKey = when (role) {
            GPUDrawPacketRole.Compute,
            GPUDrawPacketRole.Copy,
            GPUDrawPacketRole.Upload,
            GPUDrawPacketRole.Readback,
            GPUDrawPacketRole.Discard,
            -> null
            else -> GPURenderPipelineKey("pipeline.rect")
        },
        computePipelineKey = if (role == GPUDrawPacketRole.Compute) {
            GPUComputePipelineKey("pipeline.compute")
        } else {
            null
        },
        bindingLayoutHash = "layout.rect",
        uniformSlot = uniform,
        resourceSlot = resource,
        vertexSourceLabel = "vertices.rect",
        scissorBoundsHash = scissor,
        targetStateHash = targetStateHash,
        originalPaintOrder = commandId,
        resourceGeneration = 1,
        diagnostics = diagnostics,
    )

    private fun GPUDrawPacket.copyPacket(
        uniform: GPUUniformPayloadSlot? = uniformSlot,
        resource: GPUResourceBindingSlot? = resourceSlot,
    ): GPUDrawPacket = packet(
        commandId = commandIdValue,
        passId = passId,
        role = role,
        uniform = uniform,
        resource = resource,
        scissor = scissorBoundsHash,
        targetStateHash = targetStateHash,
        blendPlan = requireNotNull(blendPlan),
        diagnostics = diagnostics,
    )

    private fun executableBlend(): GPUBlendPlan = GPUBlendPlan.ShaderBlendNoDstRead(
        mode = GPUBlendMode.SRC_OVER,
        formulaId = "src-over@integrity",
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )

    private fun destinationReadBlend(): GPUBlendPlan = GPUBlendPlan.ShaderBlendWithDstRead(
        mode = GPUBlendMode.MULTIPLY,
        formulaId = "multiply@integrity",
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )

    private fun passDiagnostic(code: String, commandId: Int): GPUPassDiagnostic =
        GPUPassDiagnostic(
            code = code,
            passId = "pass.scene",
            invocationId = "invocation.$commandId",
            terminal = false,
        )

    private fun parentDestinationSnapshotTask(
        commandId: Int,
        renderTaskId: String,
    ): GPUTask.DestinationSnapshots {
        val parent = GPUFrameTargetRef("root")
        val bounds = GPUPixelBounds(0, 0, 4, 4)
        val groupingCommandId = "draw.$commandId"
        val targetIdentity = GPUTargetIdentity(parent.value)
        val colorFormat = GPUColorFormat("rgba8unorm")
        val colorInterpretation = GPUColorInterpretation("srgb-premul")
        val group = GPUDestinationSnapshotGroup(
            key = GPUDestinationSnapshotGroupKey(
                target = targetIdentity,
                targetGeneration = 1,
                deviceGeneration = GPUDeviceGenerationID(3),
                format = colorFormat,
                colorInterpretation = colorInterpretation,
                sampleContinuation = GPUSampleContinuationKey(
                    target = targetIdentity,
                    targetGeneration = 1,
                    deviceGeneration = GPUDeviceGenerationID(3),
                    colorFormat = colorFormat,
                    colorInterpretation = colorInterpretation,
                    samplePlan = GPUSamplePlan.MultisampleFrame(4),
                    colorAttachment = GPUTargetIdentity("root.msaa"),
                    depthStencilAttachment = null,
                ),
                sourceIntermediate = null,
            ),
            logicalBounds = bounds,
            members = listOf(GPUDestinationReadMember(groupingCommandId, 0, bounds)),
            copiedBytes = 64,
            decisionDump = listOf("parent-target-integrity-fixture"),
        )
        val grouping = GPUDestinationSnapshotGroupingResult(
            groups = listOf(group),
            materializations = listOf(GPUDestinationSnapshotMaterialization.TextureCopy(0, bounds)),
            totalCopiedBytes = 64,
            refusals = emptyList(),
            decisionDump = listOf("parent-target-integrity-fixture"),
        )
        val consumer = GPUDestinationSnapshotConsumerRef(
            groupingCommandId = groupingCommandId,
            renderTaskId = GPUTaskID(renderTaskId),
            packetId = GPUDrawPacketID("packet.$commandId"),
            commandId = GPUDrawCommandID(commandId),
        )
        return GPUTask.DestinationSnapshots(
            taskId = GPUTaskID("task.destination.parent"),
            recordingId = GPURecordingID("recording.a"),
            phase = GPUTaskPhase.Copy,
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping,
                operations = listOf(
                    GPUDestinationSnapshotOperation.TextureCopy(
                        groupIndex = 0,
                        source = parent,
                        snapshot = GPUFrameTextureRef("snapshot.parent"),
                        logicalBounds = bounds,
                        copyLayout = GPUTextureCopyLayout(bytesPerRow = 256, rowsPerImage = 4),
                        consumers = listOf(consumer),
                    ),
                ),
            ),
        )
    }

    private fun uniformSlot(id: String, fingerprint: String, offset: Long): GPUUniformPayloadSlot =
        GPUUniformPayloadSlot(
            slotId = GPUPayloadSlotID(id),
            fingerprint = GPUPayloadFingerprint(fingerprint),
            byteOffset = offset,
        )

    private fun resourceSlot(id: String, fingerprint: String, index: Int): GPUResourceBindingSlot =
        GPUResourceBindingSlot(
            slotId = GPUPayloadSlotID(id),
            fingerprint = GPUPayloadFingerprint(fingerprint),
            bindingIndex = index,
        )

    private fun eligibility(): GPUPassBatchEligibility = GPUPassBatchEligibility(
        kind = GPUPassBatchKind.SolidFill,
        queueGuard = GPUPassBatchQueueGuard(
            requiredRetainedRefs = listOf("resource.a", "resource.b"),
            retainedRefs = listOf("resource.a", "resource.b"),
        ),
    )

    private fun texturePreparation(id: String): GPUResourcePreparationRequest =
        GPUResourcePreparationRequest(
            resource = GPUFrameTextureRef(id),
            descriptor = GPUFrameTextureDescriptor(
                logicalBounds = GPUPixelBounds(0, 0, 4, 4),
                format = GPUColorFormat("rgba8unorm"),
                sampleCount = 1,
            ),
            role = GPUFrameResourceRole.DestinationSnapshot,
            usages = setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.TextureBinding,
            ),
            lifetime = GPUFrameResourceLifetime.FrameLocal,
            byteSize = 64,
            diagnosticLabel = id,
        )

    private fun seal(
        recordingId: String,
        insertionOrder: Long,
        capabilitySealHash: String = taskListCapabilitySeal.sealHash,
    ): GPURecordingSeal = GPURecordingSeal(
        recordingId = GPURecordingID(recordingId),
        insertionOrder = insertionOrder,
        compatibilityKeyHash = "compat.same",
        replayKeyHash = "replay.same",
        capabilitySealHash = capabilitySealHash,
    )

    private fun capabilitySeal(frameId: GPUFrameID): GPUFrameCapabilitySeal =
        GPUFrameCapabilitySeal.capture(
            frameId = frameId,
            deviceGeneration = GPUDeviceGenerationID(3),
            capabilities = integrityCapabilities(),
        )

    private fun integrityCapabilities(
        supportedTextureUsage: GPUTextureUsage? = null,
    ): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity(
            facadeName = "integrity-test-facade",
            implementationName = "integrity-test-implementation",
            adapterName = "integrity-test-adapter",
            deviceName = "integrity-test-device",
        ),
        facts = listOf(capabilityFact("frame.integrity", "supported")),
        snapshotId = "integrity-test-capabilities",
        supportedTextureUsage = supportedTextureUsage,
    )

    private fun diagnostic(code: String): GPUDiagnostic = GPUDiagnostic(
        code = GPUDiagnosticCode(code),
        domain = GPUDiagnosticDomain.Recording,
        severity = GPUDiagnosticSeverity.Error,
        message = code,
        facts = linkedMapOf("first" to "1", "second" to "2"),
        isTerminal = true,
    )

    private fun capabilityFact(name: String, value: String): GPUCapabilityFact = GPUCapabilityFact(
        name = name,
        source = "integrity-test",
        value = value,
        affectsValidity = true,
        evidenceLabel = "integrity",
    )

    private fun budgetWithEvidence(): GPUFrameMemoryBudgetPlan = GPUFrameMemoryBudgetPlan(
        peakFrameTransientBytes = 64,
        targetResidentBytes = 64,
        categoryTotals = linkedMapOf(
            GPUFrameMemoryCategory.CanonicalTarget to 64,
            GPUFrameMemoryCategory.DestinationSnapshot to 64,
        ),
        deviceLimitFacts = listOf(
            capabilityFact("limit.a", "1"),
            capabilityFact("limit.b", "2"),
        ),
        configuredAggregateBudgetBytes = 256,
        diagnostic = diagnostic("budget.info"),
    )

    private fun emptyBudget(): GPUFrameMemoryBudgetPlan = GPUFrameMemoryBudgetPlan(
        peakFrameTransientBytes = 0,
        targetResidentBytes = 0,
        categoryTotals = GPUFrameMemoryCategory.entries.associateWith { 0L },
        deviceLimitFacts = emptyList(),
        configuredAggregateBudgetBytes = 1,
        diagnostic = null,
    )

    private fun <T> assertJvmImmutable(values: List<T>, replacement: T) {
        val failure = assertFails { (values as MutableList<T>).add(replacement) }
        assertImmutableMutationFailure(failure)
    }

    private fun <T> assertJvmImmutable(values: Set<T>, replacement: T) {
        val failure = assertFails { (values as MutableSet<T>).add(replacement) }
        assertImmutableMutationFailure(failure)
    }

    private fun <K, V> assertJvmImmutable(values: Map<K, V>, key: K, value: V) {
        val failure = assertFails { (values as MutableMap<K, V>)[key] = value }
        assertImmutableMutationFailure(failure)
    }

    private fun assertImmutableMutationFailure(failure: Throwable) {
        assertTrue(
            failure is UnsupportedOperationException || failure is ClassCastException,
            "Expected a JVM immutable collection, got ${failure::class.qualifiedName}: ${failure.message}",
        )
    }
}
