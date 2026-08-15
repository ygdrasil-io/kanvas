package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Lifetime stress contract. The native crash class (EXCEPTION_ACCESS_VIOLATION in
 * wgpu_native.dll through Queue.writeBuffer after repeated GPUBackendRuntimeFactory.dispose()
 * /recreate churn) is probed with the minimal TDD reproduction: repeated
 * dispose/recreate followed by fullscreen-uniform-slab frames in one JVM. On hosts where the
 * native crash does not fire (this Mac's Metal backend), the assertions pin the
 * acceptance deterministically: compatible frames reuse one prepared session — the target
 * is created on the first frame, checked in (not closed) after every frame, and released
 * only when the runtime is disposed.
 */
class GPUPreparedSurfaceLifetimeStressTest {
    private fun assumeGpu() {
        assumeTrue(
            GPUBackendRuntimeFactory.createOrNull() != null,
            "GPU backend unavailable in current environment",
        )
    }

    private fun lifetimeRequest(width: Int = 64, height: Int = 64): GPUPreparedSurfaceExecutionRequest {
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        return GPUPreparedSurfaceExecutionRequest(
            candidate = GPUPreparedSurfaceEligibility.Candidate(
                operations = listOf(
                    DisplayOp.DrawRect(
                        Rect(0f, 0f, width.toFloat(), height.toFloat()),
                        Paint.fill(Color.RED),
                        Matrix33.identity(),
                        ClipStack.WideOpen,
                    ),
                ),
                config = RenderConfig.DEFAULT,
                color = color,
            ),
            width = width,
            height = height,
        )
    }

    @Test
    fun `compatible frames reuse one prepared session target across renders`() {
        assumeGpu()
        try {
            val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory)
            val firstExecution = executor.execute(lifetimeRequest())
            val first = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(firstExecution, firstExecution.toString())
            assertEquals(1L, first.evidence.targetCreations, "the first frame creates the session target")
            assertEquals(0L, first.evidence.targetCloses, "the session is checked in, not closed, after the first frame")
            val secondExecution = executor.execute(lifetimeRequest())
            val second = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(secondExecution, secondExecution.toString())
            assertEquals(
                0L, second.evidence.targetCreations,
                "a compatible frame must reuse the prepared session target",
            )
            assertEquals(0L, second.evidence.targetCloses, "a reused frame does not close the session")
            assertEquals(
                1L, second.evidence.frameCoordinatorCreations,
                "each frame still creates exactly one frame-local coordinator",
            )
        } finally {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun `completion only and readback outputs share one session boundary`() {
        assumeGpu()
        try {
            val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory)
            val completionOnlyExecution = executor.execute(lifetimeRequest().copy(
                output = GPUPreparedSurfaceRequestedOutput.CompletionOnly,
            ))
            val completionOnly = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(completionOnlyExecution, completionOnlyExecution.toString())
            assertEquals(1L, completionOnly.evidence.targetCreations)
            val readbackExecution = executor.execute(lifetimeRequest())
            val readback = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(readbackExecution, readbackExecution.toString())
            assertEquals(
                0L, readback.evidence.targetCreations,
                "a readback frame after a completion-only frame reuses the same session",
            )
            assertEquals(64 * 64 * 4, readback.rgba.size)
        } finally {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun `repeated dispose and recreate churn completes every frame without native failure`() {
        assumeGpu()
        // Repeated dispose/recreate churn: the documented predecessor of the native AV crash.
        repeat(16) { cycle ->
            try {
                val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory)
                val result = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
                    executor.execute(lifetimeRequest()),
                    "churn cycle $cycle must render",
                )
                assertEquals(0, result.stateEventCount)
            } finally {
                GPUBackendRuntimeFactory.dispose()
            }
        }
    }

    @Test
    fun `size transition is deterministic and reuses after the transition`() {
        assumeGpu()
        try {
            val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory)
            val first = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
                executor.execute(lifetimeRequest(width = 64, height = 64)),
            )
            assertEquals(1L, first.evidence.targetCreations)
            val transition = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
                executor.execute(lifetimeRequest(width = 32, height = 32)),
            )
            assertEquals(1L, transition.evidence.targetCreations, "size change creates a new session target")
            assertEquals(1L, transition.evidence.targetCloses, "size change closes the old session exactly once")
            val after = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
                executor.execute(lifetimeRequest(width = 32, height = 32)),
            )
            assertEquals(0L, after.evidence.targetCreations, "the new size is reused after the transition")
            assertEquals(0L, after.evidence.targetCloses)
        } finally {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun `dispose between frames advances the generation and reuses the new session`() {
        assumeGpu()
        try {
            val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory)
            val before = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
                executor.execute(lifetimeRequest()),
            )
            assertEquals(1L, before.evidence.targetCreations)
            GPUBackendRuntimeFactory.dispose()
            val after = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
                executor.execute(lifetimeRequest()),
            )
            assertEquals(1L, after.evidence.targetCreations, "a disposed backend reopens one fresh session")
            assertEquals(0L, after.evidence.targetCloses, "the disposed session is invalidated without a second close")
            val reused = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
                executor.execute(lifetimeRequest()),
            )
            assertEquals(0L, reused.evidence.targetCreations, "the reopened session is reused")
            assertEquals(0L, reused.evidence.targetCloses)
        } finally {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun `cache creation and reuse counters grow monotonically within one session`() {
        assumeGpu()
        try {
            val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory)
            val first = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
                executor.execute(lifetimeRequest()),
            )
            executor.execute(lifetimeRequest())
            val third = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
                executor.execute(lifetimeRequest()),
            )
            assertTrue(
                first.evidence.invariantCounters.corePrimitiveCreations > 0L,
                "the first frame creates the core-primitive invariants",
            )
            assertEquals(
                0L, third.evidence.invariantCounters.corePrimitiveCreations,
                "the third frame reuses the invariants instead of creating new ones",
            )
            assertTrue(
                third.evidence.invariantCounters.corePrimitiveReuses > 0L,
                "the third frame reuses the session's core-primitive invariants",
            )
        } finally {
            GPUBackendRuntimeFactory.dispose()
        }
    }
}
