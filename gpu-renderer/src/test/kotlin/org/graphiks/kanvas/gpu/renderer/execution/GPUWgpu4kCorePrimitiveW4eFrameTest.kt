package org.graphiks.kanvas.gpu.renderer.execution

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.GpuPlanSelection
import org.graphiks.kanvas.gpu.plan.PlanBudget
import org.graphiks.kanvas.gpu.plan.W4eClipPlanCompiler
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanCapabilityAdapterResult
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanLoweringRequest
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanLoweringResult
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanTaskListLowerer
import org.graphiks.kanvas.gpu.renderer.planning.toPlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipEntry
import org.graphiks.kanvas.render.ir.ClipOperation
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.ClipTransformSnapshot
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.PaintNode
import org.graphiks.kanvas.render.ir.PaintStyleNode
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.kanvas.render.ir.StrokeCapNode
import org.graphiks.kanvas.render.ir.StrokeJoinNode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

/** Native public behavior proof for the sealed W4e materializer route. */
class GPUWgpu4kCorePrimitiveW4eFrameTest {
    @Test
    fun `public W4e failure behavior rolls back each phase and recovers readback`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null, "wgpu4k native adapter unavailable; skipping W4e public failure matrix")
        backend!!
        try {
            val capabilities = requireNotNull(backend.capabilities)
            val scene = maskPipelineScene()
            val planCapabilities = (capabilities.toPlanCapabilitySnapshot(backend.deviceGeneration) as? GpuPlanCapabilityAdapterResult.Supported)
                ?.snapshot
            assumeTrue(planCapabilities != null, "native adapter lacks the W4e planning capability inventory")
            val compiler = W4eClipPlanCompiler()
            val candidate = compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace)) as? GpuPlanSelection.Candidate
            assumeTrue(candidate != null, "native adapter cannot admit the W4e failure-matrix scene")
            val graph = (compiler.plan(candidate!!.candidate, planCapabilities!!, PlanBudget(1L shl 20)) as? RenderPlanResult.Ready)
                ?.plan
            assumeTrue(graph != null, "native adapter cannot materialize the W4e failure-matrix inventory")
            val taskList = assertIs<GpuPlanLoweringResult.Lowered>(GpuPlanTaskListLowerer().lower(
                GpuPlanLoweringRequest(
                    graph = graph!!,
                    capabilities = capabilities,
                    deviceGeneration = backend.deviceGeneration,
                    currentBudget = graph!!.budget,
                    frameId = GPUFrameID(71_100L),
                    recordingId = GPURecordingID("w4e.public.failure-matrix"),
                ),
            )).taskList
            val readbackId = GPUReadbackRequestID("w4e.${graph!!.id.value}.readback")
            val target = GPUOffscreenTargetRequest(16, 16, GPUColorFormat.RGBA8UnormSrgb, GPUColorInterpretation.LinearPremul)

            listOf(
                GPUW4eFrameFailurePoint.Allocation,
                GPUW4eFrameFailurePoint.Pipeline,
                GPUW4eFrameFailurePoint.BindGroup,
                GPUW4eFrameFailurePoint.Encoder,
            ).forEach { point ->
                val session = backend.prepareSceneFrameSession(target, OneShotW4eFailure(point))
                try {
                    val failed = await(session, taskList, readbackId)
                    assertTrue(failed.outcome != GPUFrameStructuralOutcome.Succeeded, point.name)
                    assertNull(failed.output, "$point must not publish a partial readback")
                    assertTrue(failed.encodedScopeKinds.isEmpty(), "$point must not publish partial encoder scopes")
                    val recovered = await(session, taskList, readbackId)
                    assertEquals(GPUFrameStructuralOutcome.Succeeded, recovered.outcome, "$point must leave the pool and lease reusable")
                    assertIs<GPUSceneFrameOutput.ReadbackRgba>(recovered.output)
                } finally {
                    session.close()
                }
            }

            val closeSession = backend.prepareSceneFrameSession(target, OneShotW4eFailure(GPUW4eFrameFailurePoint.Close))
            assertEquals(GPUFrameStructuralOutcome.Succeeded, await(closeSession, taskList, readbackId).outcome)
            assertFailsWith<IllegalStateException> { closeSession.close() }
            closeSession.close()
            val recoveredSession = backend.prepareSceneFrameSession(target)
            try {
                assertEquals(GPUFrameStructuralOutcome.Succeeded, await(recoveredSession, taskList, readbackId).outcome)
            } finally {
                recoveredSession.close()
            }
        } finally {
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }
    @Test
    fun `public W4e mask initialize producer and fold reach readback`() {
        val terminal = renderNativeFrame(maskPipelineScene(), frameIdValue = 71_003L)

        assertEquals(
            GPUFrameStructuralOutcome.Succeeded,
            terminal.outcome,
            "${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message}",
        )
        val bytes = assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output).bytes
        assertTrue(alphaAt(bytes, 4, 4) > 0, "the intersected mask region must be rendered")
        assertEquals(0, alphaAt(bytes, 7, 7), "the folded Difference region must remain transparent")
        assertEquals(0, alphaAt(bytes, 15, 15), "pixels outside the sealed clip domain must remain transparent")
    }

    @Test
    fun `public W4e inverse-domain path reaches completion and preserves its non-rectangular hole`() {
        val terminal = renderNativeFrame(inverseDomainScene())

        assertEquals(
            GPUFrameStructuralOutcome.Succeeded,
            terminal.outcome,
            "${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message}",
        )
        val bytes = assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output).bytes
        assertEquals(16 * 16 * 4, bytes.size)
        assertEquals(255, alphaAt(bytes, 0, 0), "inverse-domain must cover the finite domain exterior")
        assertEquals(0, alphaAt(bytes, 5, 5), "the original non-rectangular path must remain an interior hole")
        assertTrue(
            (0 until 16 * 16).any { pixel -> alphaAt(bytes, pixel % 16, pixel / 16) in 1..254 },
            "AA4 must retain fractional coverage on the non-rectangular boundary",
        )
    }

    @Test
    fun `public hard W4e inverse-domain interior completes with its declared D24S8`() {
        val terminal = renderNativeFrame(
            inverseDomainScene(CoverageRequest.HARD_EDGE, clipAntiAlias = false),
            frameIdValue = 71_002L,
        )

        assertEquals(
            GPUFrameStructuralOutcome.Succeeded,
            terminal.outcome,
            "${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message}",
        )
        val bytes = assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output).bytes
        assertEquals(255, alphaAt(bytes, 0, 0), "the finite inverse-domain exterior must render")
        assertEquals(0, alphaAt(bytes, 5, 5), "the non-rectangular interior must remain a hole")
    }

    private fun renderNativeFrame(
        scene: SceneSnapshot,
        frameIdValue: Long = 71_001L,
    ): GPUPreparedSceneCompletedFrameResult {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null, "wgpu4k native adapter unavailable; skipping W4e public frame proof")
        backend!!
        try {
            val capabilities = requireNotNull(backend.capabilities)
            val generation = backend.deviceGeneration
            val planCapabilities = (capabilities.toPlanCapabilitySnapshot(generation) as? GpuPlanCapabilityAdapterResult.Supported)
                ?.snapshot
            assumeTrue(planCapabilities != null, "native adapter lacks the W4e planning capability inventory")

            val compiler = W4eClipPlanCompiler()
            val candidate = compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace))
                as? GpuPlanSelection.Candidate
            assumeTrue(candidate != null, "native adapter cannot admit the W4e complex clip scene")
            val planned = compiler.plan(candidate!!.candidate, planCapabilities!!, PlanBudget(1L shl 20))
            val graph = (planned as? RenderPlanResult.Ready)
                ?.plan
            assumeTrue(graph != null, "native adapter cannot materialize the required W4e AA inventory: $planned")

            val frameId = GPUFrameID(frameIdValue)
            val lowered = GpuPlanTaskListLowerer().lower(
                GpuPlanLoweringRequest(
                    graph = graph!!,
                    capabilities = capabilities,
                    deviceGeneration = generation,
                    currentBudget = graph.budget,
                    frameId = frameId,
                    recordingId = GPURecordingID("w4e.public.inverse-domain.$frameIdValue"),
                ),
            )
            val taskList = assertIs<GpuPlanLoweringResult.Lowered>(lowered).taskList
            val readbackId = GPUReadbackRequestID("w4e.${graph.id.value}.readback")
            val session = backend.prepareSceneFrameSession(
                GPUOffscreenTargetRequest(
                    width = 16,
                    height = 16,
                    colorFormat = GPUColorFormat.RGBA8UnormSrgb,
                    colorInterpretation = GPUColorInterpretation.LinearPremul,
                ),
            )
            try {
                return session.renderFrame(
                    taskList,
                    GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
                ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            } finally {
                session.close()
            }
        } finally {
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    private fun inverseDomainScene(
        coverage: CoverageRequest = CoverageRequest.ANTIALIASED,
        clipAntiAlias: Boolean = true,
    ): SceneSnapshot {
        val color = ColorARGB.fromPackedUInt(0xFFFF0000u)
        val path = PathBuilder(FillRule.INVERSE_WINDING)
            .moveTo(3.25f, 3.25f)
            .lineTo(12.75f, 3.25f)
            .lineTo(4.25f, 12.75f)
            .close()
            .build()
        return SceneSnapshot.of(
            SceneExtent(16, 16),
            ColorSpace.SRGB,
            listOf(
                SceneCommand.Draw(
                    DrawNode(
                        geometry = GeometryNode.Path(path),
                        material = MaterialNode.Solid(color),
                        coverage = coverage,
                        clip = ClipStackNode.Operations.of(
                            listOf(
                                ClipEntry(
                                    geometry = GeometryNode.Path(PathBuilder().build()),
                                    operation = ClipOperation.DIFFERENCE,
                                    antiAlias = clipAntiAlias,
                                    transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity),
                                ),
                            ),
                        ),
                        blend = BlendNode.SrcOver,
                        effects = EffectStack.Empty,
                        transform = Matrix3x3F32.Identity,
                        origin = DrawOrigin.PATH,
                        paint = PaintNode(
                            color,
                            null,
                            BlendMode.SRC_OVER,
                            null,
                            null,
                            null,
                            null,
                            null,
                            PaintStyleNode.FILL,
                            0f,
                            StrokeCapNode.BUTT,
                            StrokeJoinNode.MITER,
                            4f,
                            true,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun maskPipelineScene(): SceneSnapshot {
        val color = ColorARGB.fromPackedUInt(0xFFFF0000u)
        val path = PathBuilder(FillRule.WINDING)
            .moveTo(1f, 1f)
            .lineTo(15f, 1f)
            .lineTo(1f, 15f)
            .close()
            .build()
        fun clip(rect: RectF32, operation: ClipOperation) = ClipEntry(
            geometry = GeometryNode.Path(
                PathBuilder(FillRule.WINDING)
                    .moveTo(rect.left, rect.top)
                    .lineTo(rect.right, rect.top)
                    .lineTo(rect.right, rect.bottom)
                    .lineTo(rect.left, rect.bottom)
                    .close()
                    .build(),
            ),
            operation = operation,
            antiAlias = true,
            transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity),
        )
        return SceneSnapshot.of(
            SceneExtent(16, 16),
            ColorSpace.SRGB,
            listOf(SceneCommand.Draw(DrawNode(
                geometry = GeometryNode.Path(path),
                material = MaterialNode.Solid(color),
                coverage = CoverageRequest.HARD_EDGE,
                clip = ClipStackNode.Operations.of(listOf(
                    clip(RectF32(2f, 2f, 14f, 14f), ClipOperation.INTERSECT).copy(antiAlias = false),
                    clip(RectF32(6f, 6f, 10f, 10f), ClipOperation.DIFFERENCE).copy(antiAlias = false),
                )),
                blend = BlendNode.SrcOver,
                effects = EffectStack.Empty,
                transform = Matrix3x3F32.Identity,
                origin = DrawOrigin.PATH,
                paint = PaintNode(
                    color, null, BlendMode.SRC_OVER, null, null, null, null, null,
                    PaintStyleNode.FILL, 0f, StrokeCapNode.BUTT, StrokeJoinNode.MITER, 4f, true,
                ),
            ))),
        )
    }

    private fun alphaAt(bytes: ByteArray, x: Int, y: Int): Int =
        bytes[(y * 16 + x) * 4 + 3].toInt() and 0xff

    private fun await(
        session: GPUPreparedSceneFrameSession,
        taskList: org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList,
        readbackId: GPUReadbackRequestID,
    ): GPUPreparedSceneCompletedFrameResult = session.renderFrame(
        taskList,
        GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
    ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)

    private class OneShotW4eFailure(
        private val point: GPUW4eFrameFailurePoint,
    ) : GPUW4eFrameFailureBehavior {
        private var pending: Boolean = true

        override fun shouldFail(point: GPUW4eFrameFailurePoint): Boolean =
            pending && point == this.point && run { pending = false; true }
    }
}
