package org.graphiks.kanvas.gpu.renderer.execution

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputRef
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUSceneTarget
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome

class GPUWindowFrameLifecycleTest {
    @Test
    fun `scene output algebra includes opaque prepared window presentation`() {
        assertEquals(
            "PresentToWindow",
            GPUSceneFrameOutputRequest.PresentToWindow::class.simpleName,
        )
    }

    @Test
    fun `completion is armed before present and synchronous delivery waits until after present`() {
        val events = mutableListOf<String>()
        val completion = SynchronousCompletion(events)
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingBackend(events),
            completion = completion,
            retention = RecordingRetention(events),
            presenter = GPUPostSubmitPresentAccess {
                events += "present"
                GPUPostSubmitPresentResult.Presented
            },
        ).execute(GPUFrameCoreTestFixture.preparedFrame(withHostActions = true))

        assertIs<GPUFrameImmediateState.Submitted>(handle.immediateState)
        assertTrue(handle.completion.toCompletableFuture().isDone)
        assertEquals(
            listOf("completion:arm", "present", "retention:complete"),
            events.filter { it in setOf("completion:arm", "present", "retention:complete") },
        )
    }

    @Test
    fun `throwing present is captured while successful queue completion still releases retention`() {
        val events = mutableListOf<String>()
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingBackend(events),
            completion = SynchronousCompletion(events),
            retention = RecordingRetention(events),
            presenter = GPUPostSubmitPresentAccess {
                events += "present:failed"
                error("present failed")
            },
        ).execute(GPUFrameCoreTestFixture.preparedFrame(withHostActions = true))

        assertIs<GPUFrameImmediateState.FailedAfterSubmit>(handle.immediateState)
        assertEquals(
            "failed.frame-execution.window-present-callback",
            handle.completion.toCompletableFuture().get().diagnostic?.code?.value,
        )
        assertTrue("retention:complete" in events)
        assertTrue("retention:quarantine" !in events)
    }

    @Test
    fun `failed present discards acquired output exactly once without completing or releasing retention`() {
        val events = mutableListOf<String>()
        val completion = ManualCompletion(events)
        val presenter = object : GPUPostSubmitPresentAccess {
            override fun present(output: GPUAcquiredSurfaceOutput): GPUPostSubmitPresentResult {
                events += "present:failed"
                return GPUPostSubmitPresentResult.Failed(
                    executionDiagnostic("failed.window.present", "present refused"),
                )
            }

            override fun discardAfterSubmit(output: GPUAcquiredSurfaceOutput): GPUSurfaceReleaseResult {
                events += "surface:discard"
                return GPUSurfaceReleaseResult.Released
            }
        }

        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingBackend(events),
            completion = completion,
            retention = RecordingRetention(events),
            presenter = presenter,
        ).execute(GPUFrameCoreTestFixture.preparedFrame(withHostActions = true))

        val immediate = assertIs<GPUFrameImmediateState.FailedAfterSubmit>(handle.immediateState)
        assertEquals("failed.window.present", immediate.diagnostic.code.value)
        assertEquals(1, events.count { it == "surface:discard" })
        assertFalse(handle.completion.toCompletableFuture().isDone)
        assertTrue("retention:complete" !in events)
        assertTrue("retention:quarantine" !in events)

        completion.deliver(GPUQueueCompletionOutcome.Success)

        assertEquals(
            "failed.window.present",
            handle.completion.toCompletableFuture().get().diagnostic?.code?.value,
        )
        assertEquals(1, events.count { it == "surface:discard" })
        assertTrue("retention:complete" in events)
    }

    @Test
    fun `throwing present keeps primary diagnostic and attaches failing discard while completion stays pending`() {
        val events = mutableListOf<String>()
        val completion = ManualCompletion(events)
        val presenter = object : GPUPostSubmitPresentAccess {
            override fun present(output: GPUAcquiredSurfaceOutput): GPUPostSubmitPresentResult {
                events += "present:throw"
                error("present exploded")
            }

            override fun discardAfterSubmit(output: GPUAcquiredSurfaceOutput): GPUSurfaceReleaseResult {
                events += "surface:discard"
                return GPUSurfaceReleaseResult.Failed(
                    executionDiagnostic("failed.window.cleanup", "cleanup refused"),
                )
            }
        }

        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingBackend(events),
            completion = completion,
            retention = RecordingRetention(events),
            presenter = presenter,
        ).execute(GPUFrameCoreTestFixture.preparedFrame(withHostActions = true))

        val immediate = assertIs<GPUFrameImmediateState.FailedAfterSubmit>(handle.immediateState)
        assertEquals("failed.frame-execution.window-present-callback", immediate.diagnostic.code.value)
        assertEquals("failed.window.cleanup", immediate.diagnostic.facts["surfaceCleanupCode"])
        assertEquals("cleanup refused", immediate.diagnostic.facts["surfaceCleanupMessage"])
        assertEquals(1, events.count { it == "surface:discard" })
        assertFalse(handle.completion.toCompletableFuture().isDone)

        completion.deliver(GPUQueueCompletionOutcome.Success)

        assertEquals(
            "failed.frame-execution.window-present-callback",
            handle.completion.toCompletableFuture().get().diagnostic?.code?.value,
        )
        assertTrue("retention:complete" in events)
    }

    @Test
    fun `simultaneous present and completion failure reports each diagnostic at its own boundary`() {
        val events = mutableListOf<String>()
        val completion = ManualCompletion(events)
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingBackend(events),
            completion = completion,
            retention = RecordingRetention(events),
            presenter = object : GPUPostSubmitPresentAccess {
                override fun present(output: GPUAcquiredSurfaceOutput) = GPUPostSubmitPresentResult.Failed(
                    executionDiagnostic("failed.window.present", "present refused"),
                )

                override fun discardAfterSubmit(output: GPUAcquiredSurfaceOutput) =
                    GPUSurfaceReleaseResult.Released
            },
        ).execute(GPUFrameCoreTestFixture.preparedFrame(withHostActions = true))

        assertEquals(
            "failed.window.present",
            assertIs<GPUFrameImmediateState.FailedAfterSubmit>(handle.immediateState).diagnostic.code.value,
        )

        completion.deliver(GPUQueueCompletionOutcome.Failure(GPUQueueCompletionFailureKind.DeviceLost))

        assertEquals(
            "failed.frame-execution.queue-completion",
            handle.completion.toCompletableFuture().get().diagnostic?.code?.value,
        )
    }

    @Test
    fun `completion arm refusal discards acquired output exactly once and never presents`() {
        val events = mutableListOf<String>()
        val presenter = object : GPUPostSubmitPresentAccess {
            override fun present(output: GPUAcquiredSurfaceOutput): GPUPostSubmitPresentResult {
                events += "present"
                return GPUPostSubmitPresentResult.Presented
            }

            override fun discardAfterSubmit(output: GPUAcquiredSurfaceOutput): GPUSurfaceReleaseResult {
                events += "surface:discard"
                return GPUSurfaceReleaseResult.Released
            }
        }
        val completion = object : SynchronousCompletion(events) {
            override fun armAfterSubmit(
                ticket: GPUQueueCompletionTicket,
                sink: GPUQueueCompletionSink,
            ): GPUQueueCompletionArmResult = GPUQueueCompletionArmResult.Refused(
                ticket.ticketId,
                GPUQueueCompletionFailureKind.CallbackFailure,
            )
        }

        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingBackend(events),
            completion = completion,
            retention = RecordingRetention(events),
            presenter = presenter,
        ).execute(GPUFrameCoreTestFixture.preparedFrame(withHostActions = true))

        assertIs<GPUFrameImmediateState.FailedAfterSubmit>(handle.immediateState)
        assertEquals(1, events.count { it == "surface:discard" })
        assertTrue("present" !in events)
    }

    @Test
    fun `queue completion failure remains independent after successful present`() {
        val events = mutableListOf<String>()
        val completion = SynchronousCompletion(
            events,
            GPUQueueCompletionOutcome.Failure(GPUQueueCompletionFailureKind.DeviceLost),
        )
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingBackend(events),
            completion = completion,
            retention = RecordingRetention(events),
            presenter = GPUPostSubmitPresentAccess {
                events += "present"
                GPUPostSubmitPresentResult.Presented
            },
        ).execute(GPUFrameCoreTestFixture.preparedFrame(withHostActions = true))

        assertIs<GPUFrameImmediateState.Submitted>(handle.immediateState)
        assertEquals(
            "failed.frame-execution.queue-completion",
            handle.completion.toCompletableFuture().get().diagnostic?.code?.value,
        )
        assertEquals(listOf("completion:arm", "present", "retention:quarantine"), events.filter {
            it in setOf("completion:arm", "present", "retention:quarantine")
        })
    }

    @Test
    fun `prepared output appends one terminal output task with independent surface generation`() {
        val output = preparedOutput()

        val decorated = output.attachToFrame(
            GPUFrameCoreTestFixture.taskList(),
            GPUFrameTargetRef("target.scene"),
        )

        val task = assertIs<GPUTask.Output>(decorated.tasks.single())
        assertEquals("target.scene", task.scene.value)
        assertEquals(9, task.descriptor.targetGeneration)
        assertEquals("bgra8unorm", task.descriptor.format.value)
    }

    @Test
    fun `prepared session accepts only the prepared output and decorates the common task list`() {
        var captured: org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList? = null
        val output = preparedOutput()
        val session = GPUPreparedSceneFrameSession(
            deviceGeneration = GPUDeviceGenerationID(1L),
            coordinatorFactory = GPUFrameCoordinatorFactory { taskList, _ ->
                captured = taskList
                error("captured")
            },
            sceneTargetResolver = { GPUFrameTargetRef("target.scene") },
        )

        session.renderFrame(
            GPUFrameCoreTestFixture.taskList(),
            GPUSceneFrameOutputRequest.PresentToWindow(output),
        )

        assertIs<GPUTask.Output>(captured?.tasks?.single())
    }

    @Test
    fun `dependency gated native output refuses before coordinator creation`() {
        var coordinatorCreations = 0
        val output = GPUPreparedWindowOutput(RecordingWindowController(dependencyGated = true))
        val session = GPUPreparedSceneFrameSession(
            deviceGeneration = GPUDeviceGenerationID(1L),
            coordinatorFactory = GPUFrameCoordinatorFactory { _, _ ->
                coordinatorCreations += 1
                error("must not create")
            },
        )

        val completed = session.renderFrame(
            GPUFrameCoreTestFixture.taskList(),
            GPUSceneFrameOutputRequest.PresentToWindow(output),
        ).completion.toCompletableFuture().get()

        assertEquals(0, coordinatorCreations)
        assertEquals("unsupported.wgpu4k.surface-status-v29", completed.diagnostic?.code?.value)
    }

    @Test
    fun `generic session reaches late acquire final blit one present and pending dedicated completion`() {
        val events = mutableListOf<String>()
        val completion = ManualCompletion(events)
        val controller = RecordingWindowController(
            outputRef = GPUSurfaceOutputRef("surface.main"),
            events = events,
        )
        val output = GPUPreparedWindowOutput(controller)
        val session = GPUPreparedSceneFrameSession(
            deviceGeneration = GPUDeviceGenerationID(1L),
            coordinatorFactory = GPUFrameCoordinatorFactory { submittedTasks, outputRequest ->
                val outputTask = assertIs<GPUTask.Output>(submittedTasks.tasks.single())
                assertEquals(GPUSurfaceOutputRef("surface.main"), outputTask.descriptor.output)
                GPUFrameCoordinator(
                    planner = GPUFramePlanningPort { plannedTasks ->
                        assertTrue(plannedTasks === submittedTasks)
                        GPUFrameCoreTestFixture.preparedFrame(
                            withHostActions = true,
                            surfaceGeneration = 9,
                        ).semanticPlan
                    },
                    preflighter = GPUFramePreflightPort { framePlan ->
                        assertIs<org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.AcquireSurfaceOutput>(
                            framePlan.steps[framePlan.steps.lastIndex - 2],
                        )
                        assertIs<org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.SurfaceBlitRenderPassStep>(
                            framePlan.steps[framePlan.steps.lastIndex - 1],
                        )
                        assertIs<org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.PostSubmitPresentAction>(
                            framePlan.steps.last(),
                        )
                        assertIs<GPUSurfaceAcquisitionResult.Acquired>(
                            controller.acquire(
                                GPUSurfaceAcquisitionRequest(outputTask.descriptor, GPUDeviceGenerationID(7)),
                            ),
                        )
                        GPUFramePreflightResult.Prepared(
                            GPUFrameCoreTestFixture.preparedFrame(
                                withHostActions = true,
                                surfaceGeneration = 9,
                            ),
                        )
                    },
                    executor = GPUFrameExecutor(
                        sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
                        backend = RecordingBackend(events),
                        completion = completion,
                        retention = RecordingRetention(events),
                        presenter = controller,
                    ),
                ).also {
                    assertIs<GPUSceneFrameOutputRequest.PresentToWindow>(outputRequest)
                }
            },
            sceneTargetResolver = { GPUFrameTargetRef("target.scene") },
        )

        val handle = session.renderFrame(
            GPUFrameCoreTestFixture.taskList(),
            GPUSceneFrameOutputRequest.PresentToWindow(output),
        )

        assertIs<GPUFrameImmediateState.Submitted>(handle.immediateState)
        assertFalse(handle.completion.toCompletableFuture().isDone)
        assertEquals(1, events.count { it == "surface:acquire" })
        assertEquals(1, events.count { it == "submit" })
        assertEquals(1, events.count { it == "present" })
        assertEquals(1, events.count { it == "encode:SurfaceBlit" })

        val deliveryThread = AtomicReference<String>()
        handle.completion.thenRun { deliveryThread.set(Thread.currentThread().name) }
        val deliveryExecutor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "window-completion-delivery")
        }
        try {
            deliveryExecutor.submit { completion.deliver(GPUQueueCompletionOutcome.Success) }.get()
            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                handle.completion.toCompletableFuture().get().outcome,
            )
            assertEquals("window-completion-delivery", deliveryThread.get())
        } finally {
            deliveryExecutor.shutdownNow()
            session.close()
            output.close()
        }
    }

    @Test
    fun `surface generation is independent from the canonical scene target generation`() {
        val frame = GPUFrameCoreTestFixture.preparedFrame(
            withHostActions = true,
            surfaceGeneration = 9,
        )

        assertEquals(1, frame.generationSeal.targetGeneration)
        assertEquals(9, frame.acquiredSurfaceOutput?.targetGeneration)
    }

    @Test
    fun `surface status failures retain stable recovery classification`() {
        assertEquals(
            "unsupported.preflight.surface_reconfigure",
            gpuSurfaceAcquisitionDiagnostic(GPUSurfaceAcquisitionStatus.Lost).code.value,
        )
        assertEquals(
            "unsupported.preflight.surface_reconfigure",
            gpuSurfaceAcquisitionDiagnostic(GPUSurfaceAcquisitionStatus.Outdated).code.value,
        )
        assertEquals(
            "unsupported.preflight.surface_timeout",
            gpuSurfaceAcquisitionDiagnostic(GPUSurfaceAcquisitionStatus.Timeout).code.value,
        )
        listOf(GPUSurfaceAcquisitionStatus.OutOfMemory, GPUSurfaceAcquisitionStatus.DeviceLost).forEach { status ->
            assertEquals("failed.preflight.surface_terminal", gpuSurfaceAcquisitionDiagnostic(status).code.value)
        }
        assertEquals(
            "unsupported.wgpu4k.surface-status-v29",
            gpuSurfaceAcquisitionDiagnostic(GPUSurfaceAcquisitionStatus.DependencyUnavailable).code.value,
        )
    }

    @Test
    fun `resize advances only the next surface generation`() {
        val controller = RecordingWindowController()
        val output = GPUPreparedWindowOutput(controller)
        val before = assertIs<GPUTask.Output>(
            output.attachToFrame(GPUFrameCoreTestFixture.taskList(), GPUFrameTargetRef("target.scene")).tasks.single(),
        )

        output.resize(8, 6)
        val after = assertIs<GPUTask.Output>(
            output.attachToFrame(GPUFrameCoreTestFixture.taskList(), GPUFrameTargetRef("target.scene")).tasks.single(),
        )

        assertEquals(9, before.descriptor.targetGeneration)
        assertEquals(10, after.descriptor.targetGeneration)
        assertEquals(4, before.descriptor.width)
        assertEquals(8, after.descriptor.width)
    }

    private fun preparedOutput(): GPUPreparedWindowOutput = GPUPreparedWindowOutput(
        controller = RecordingWindowController(),
    )

    private class RecordingWindowController(
        private val dependencyGated: Boolean = false,
        private val outputRef: GPUSurfaceOutputRef = GPUSurfaceOutputRef("surface.window"),
        private val events: MutableList<String>? = null,
    ) : GPUPreparedWindowOutputController {
        override val deviceGeneration = GPUDeviceGenerationID(7)
        override val adapterInfo: GPUBackendAdapterSummary? = null
        override val availabilityDiagnostic: GPUDiagnostic?
            get() = if (dependencyGated) {
                executionDiagnostic(
                    "unsupported.wgpu4k.surface-status-v29",
                    "dependency gated",
                )
            } else {
                null
            }
        private var width = 4
        private var height = 4
        private var generation = 9L

        override fun snapshot() = GPUPreparedWindowOutputSnapshot(
            output = outputRef,
            width = width,
            height = height,
            format = GPUColorFormat("bgra8unorm"),
            surfaceGeneration = generation,
        )

        override fun resize(width: Int, height: Int) {
            this.width = width
            this.height = height
            generation += 1
        }

        override fun acquire(request: GPUSurfaceAcquisitionRequest): GPUSurfaceAcquisitionResult {
            events?.add("surface:acquire")
            return GPUSurfaceAcquisitionResult.Acquired(
                GPUAcquiredSurfaceOutput(request.descriptor.output, request.deviceGeneration, generation, "fake.surface"),
            )
        }
        override fun release(output: GPUAcquiredSurfaceOutput) = GPUSurfaceReleaseResult.Released
        override fun present(output: GPUAcquiredSurfaceOutput): GPUPostSubmitPresentResult {
            events?.add("present")
            return GPUPostSubmitPresentResult.Presented
        }
        override fun discardAfterSubmit(output: GPUAcquiredSurfaceOutput) = GPUSurfaceReleaseResult.Released
        override fun close() = Unit
    }

    private class RecordingBackend(private val events: MutableList<String>) : GPUFrameEncodingBackend {
        override val deviceGeneration = GPUDeviceGenerationID(7)

        override fun createCommandEncoder(label: String): GPUFrameCommandEncoder =
            object : GPUFrameCommandEncoder {
                override fun encode(
                    scope: GPUCommandEncoderScopePlan,
                    preparedFrame: PreparedGPUFrame,
                    sceneTarget: GPUSceneTarget,
                    nativeOperand: GPUPreparedNativeScopeOperand?,
                ) {
                    events += "encode:${scope.operationKind}"
                }

                override fun finish(): GPUFrameCommandBuffer = GPUFrameCommandBuffer("window.command")

                override fun discard(): GPUFrameDiscardResult = GPUFrameDiscardResult.Discarded
            }

        override fun discard(commandBuffer: GPUFrameCommandBuffer): GPUFrameDiscardResult =
            GPUFrameDiscardResult.Discarded

        override fun submit(commandBuffer: GPUFrameCommandBuffer) {
            events += "submit"
        }
    }

    private open class SynchronousCompletion(
        private val events: MutableList<String>,
        private val outcome: GPUQueueCompletionOutcome = GPUQueueCompletionOutcome.Success,
    ) : GPUQueueCompletionAccess {
        override fun reserveTicket(request: GPUQueueCompletionTicketRequest): GPUQueueCompletionTicketReservation =
            GPUQueueCompletionTicketReservation.Reserved(
                GPUQueueCompletionTicket(
                    GPUQueueCompletionTicketID("unused"),
                    GPUFrameID(99),
                    GPUDeviceGenerationID(7),
                ),
            )

        override fun abandonReservedTicket(ticket: GPUQueueCompletionTicket): GPUQueueCompletionTicketAbandonResult =
            GPUQueueCompletionTicketAbandonResult.Abandoned(ticket.ticketId)

        open override fun armAfterSubmit(
            ticket: GPUQueueCompletionTicket,
            sink: GPUQueueCompletionSink,
        ): GPUQueueCompletionArmResult {
            events += "completion:arm"
            sink.accept(GPUQueueCompletionDelivery.Accepted(ticket.ticketId, outcome))
            return GPUQueueCompletionArmResult.Armed(ticket.ticketId)
        }

        override suspend fun awaitCompletion(ticket: GPUQueueCompletionTicket): GPUQueueCompletionDelivery =
            error("not used")

        override fun cancel(ticket: GPUQueueCompletionTicket): GPUQueueCompletionDelivery =
            GPUQueueCompletionDelivery.Accepted(
                ticket.ticketId,
                GPUQueueCompletionOutcome.Failure(GPUQueueCompletionFailureKind.Cancelled),
            )
    }

    private class ManualCompletion(
        private val events: MutableList<String>,
    ) : GPUQueueCompletionAccess {
        private var ticket: GPUQueueCompletionTicket? = null
        private var sink: GPUQueueCompletionSink? = null

        override fun reserveTicket(request: GPUQueueCompletionTicketRequest): GPUQueueCompletionTicketReservation =
            error("not used")

        override fun abandonReservedTicket(ticket: GPUQueueCompletionTicket): GPUQueueCompletionTicketAbandonResult =
            error("not used")

        override fun armAfterSubmit(
            ticket: GPUQueueCompletionTicket,
            sink: GPUQueueCompletionSink,
        ): GPUQueueCompletionArmResult {
            events += "completion:arm"
            this.ticket = ticket
            this.sink = sink
            return GPUQueueCompletionArmResult.Armed(ticket.ticketId)
        }

        fun deliver(outcome: GPUQueueCompletionOutcome) {
            val armedTicket = requireNotNull(ticket)
            requireNotNull(sink).accept(GPUQueueCompletionDelivery.Accepted(armedTicket.ticketId, outcome))
        }

        override suspend fun awaitCompletion(ticket: GPUQueueCompletionTicket): GPUQueueCompletionDelivery =
            error("not used")

        override fun cancel(ticket: GPUQueueCompletionTicket): GPUQueueCompletionDelivery =
            error("completion must remain armed")
    }

    private class RecordingRetention(private val events: MutableList<String>) : GPUFrameResourceRetention {
        override fun registerAfterSubmit(registration: GPUFrameRetentionRegistration) {
            events += "retention:register"
        }

        override fun complete(ticket: GPUQueueCompletionTicket, outcome: GPUQueueCompletionOutcome) {
            events += "retention:complete"
        }

        override fun quarantine(registration: GPUFrameRetentionRegistration, diagnostic: GPUDiagnostic) {
            events += "retention:quarantine"
        }
    }
}
