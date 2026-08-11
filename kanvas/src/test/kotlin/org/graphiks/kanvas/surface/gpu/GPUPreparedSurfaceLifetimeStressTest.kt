package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
 * FP-10 lifetime stress contract. The native crash class (EXCEPTION_ACCESS_VIOLATION in
 * wgpu_native.dll through Queue.writeBuffer after repeated GPUBackendRuntimeFactory.dispose()
 * /recreate churn — roadmap evidence) is probed with the minimal TDD reproduction: repeated
 * dispose/recreate followed by fullscreen-uniform-slab frames in one JVM. On hosts where the
 * native crash does not fire (this Mac's Metal backend), the assertions pin the FP-10
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
        // Repeated dispose/recreate churn: the documented predecessor of the native AV crash (roadmap FP-10 evidence).
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
}
