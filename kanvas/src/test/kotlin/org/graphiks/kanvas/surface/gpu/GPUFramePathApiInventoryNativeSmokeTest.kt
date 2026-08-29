package org.graphiks.kanvas.surface.gpu

import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutput
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutputRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

@OptIn(ExperimentalUnsignedTypes::class)
class GPUFramePathApiInventoryNativeSmokeTest {
    @Test
    fun `single horizontal hairline renders one pixel row natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.hairline-horizontal")
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply { moveTo(4f, 16f); lineTo(28f, 16f) },
                    Paint.stroke(ColorARGB.Red, 0f).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(inventory, capabilities, targetBounds, readbackId)
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(prepared, GPUSceneFrameOutputRequest.ReadbackRgba(readbackId))
                .completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicHairlineOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `single horizontal hairline with uniform scale renders one pixel row natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.hairline-scaled")
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply { moveTo(4f, 8f); lineTo(14f, 8f) },
                    Paint.stroke(ColorARGB.Red, 0f).copy(antiAlias = false),
                    Matrix3x3F32.scaling(2f, 2f),
                    ClipStack.WideOpen,
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(inventory, capabilities, targetBounds, readbackId)
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(prepared, GPUSceneFrameOutputRequest.ReadbackRgba(readbackId))
                .completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicScaledHairlineOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `single horizontal hairline with uniform scale and translation renders one pixel row natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.hairline-scaled-translated")
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply { moveTo(4f, 8f); lineTo(14f, 8f) },
                    Paint.stroke(ColorARGB.Red, 0f).copy(antiAlias = false),
                    Matrix3x3F32.translation(2f, 3f) * Matrix3x3F32.scaling(2f, 2f),
                    ClipStack.WideOpen,
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(inventory, capabilities, targetBounds, readbackId)
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(prepared, GPUSceneFrameOutputRequest.ReadbackRgba(readbackId))
                .completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicScaledTranslatedHairlineOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `single butt miter stroke with uniform scale renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-scaled")
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply { moveTo(4f, 8f); lineTo(14f, 8f) },
                    Paint.stroke(ColorARGB.Red, 2f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.scaling(2f, 2f),
                    ClipStack.WideOpen,
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(inventory, capabilities, targetBounds, readbackId)
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(prepared, GPUSceneFrameOutputRequest.ReadbackRgba(readbackId))
                .completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicScaledButtMiterStrokeOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `single butt miter stroke with uniform scale and translation renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-scaled-translated")
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply { moveTo(4f, 8f); lineTo(14f, 8f) },
                    Paint.stroke(ColorARGB.Red, 2f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.translation(2f, 3f) * Matrix3x3F32.scaling(2f, 2f),
                    ClipStack.WideOpen,
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(inventory, capabilities, targetBounds, readbackId)
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(prepared, GPUSceneFrameOutputRequest.ReadbackRgba(readbackId))
                .completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicScaledTranslatedButtMiterStrokeOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `single diagonal butt miter stroke with uniform scale and translation renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-diagonal-scaled-translated")
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    // Device endpoints are (10.25,11.25) -> (26.25,20.25); fractional values avoid cap ties.
                    Path().apply { moveTo(4.125f, 4.125f); lineTo(12.125f, 8.625f) },
                    Paint.stroke(ColorARGB.Red, 2f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.translation(2f, 3f) * Matrix3x3F32.scaling(2f, 2f),
                    ClipStack.WideOpen,
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(inventory, capabilities, targetBounds, readbackId)
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(prepared, GPUSceneFrameOutputRequest.ReadbackRgba(readbackId))
                .completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicScaledTranslatedDiagonalButtMiterStrokeOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `single diagonal square miter stroke with uniform scale and translation renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-diagonal-square-scaled-translated")
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    // Device endpoints are (10.25,11.25) -> (26.25,20.25); fractional values avoid cap ties.
                    Path().apply { moveTo(4.125f, 4.125f); lineTo(12.125f, 8.625f) },
                    Paint.stroke(ColorARGB.Red, 2f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.SQUARE,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.translation(2f, 3f) * Matrix3x3F32.scaling(2f, 2f),
                    ClipStack.WideOpen,
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(inventory, capabilities, targetBounds, readbackId)
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(prepared, GPUSceneFrameOutputRequest.ReadbackRgba(readbackId))
                .completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicScaledTranslatedDiagonalSquareMiterStrokeOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `single vertical butt miter stroke renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-vertical")
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply { moveTo(16f, 4f); lineTo(16f, 28f) },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(inventory, capabilities, targetBounds, readbackId)
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(prepared, GPUSceneFrameOutputRequest.ReadbackRgba(readbackId))
                .completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicVerticalButtMiterStrokeOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `single horizontal square miter stroke renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-square")
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply { moveTo(8f, 16f); lineTo(24f, 16f) },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.SQUARE,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(inventory, capabilities, targetBounds, readbackId)
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(prepared, GPUSceneFrameOutputRequest.ReadbackRgba(readbackId))
                .completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicSquareButtMiterStrokeOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `single diagonal butt miter stroke renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-diagonal")
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    // Fractional endpoints avoid rasterizer tie cases at the butt cap.
                    Path().apply { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(inventory, capabilities, targetBounds, readbackId)
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(prepared, GPUSceneFrameOutputRequest.ReadbackRgba(readbackId))
                .completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            val expected = deterministicDiagonalButtMiterStrokeOracle()
            if (!expected.contentEquals(gpu)) {
                val expectedPixels = buildSet {
                    for (y in 0 until 32) for (x in 0 until 32) {
                        val offset = (y * 32 + x) * 4
                        if (expected[offset + 3].toInt() != 0) add("$x,$y")
                    }
                }
                val actualPixels = buildSet {
                    for (y in 0 until 32) for (x in 0 until 32) {
                        val offset = (y * 32 + x) * 4
                        if (gpu[offset + 3].toInt() != 0) add("$x,$y")
                    }
                }
                error(
                    "diagonal stroke mismatch missing=${expectedPixels - actualPixels} " +
                        "extra=${actualPixels - expectedPixels}",
                )
            }
            assertContentEquals(expected, gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `single diagonal butt miter stroke under integral device scissor matches CPU oracle natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-diagonal-scissor")
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(5.25f, 8.25f)
                        lineTo(21.25f, 20.25f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.DeviceRect(RectF32.ofLTRB(8f, 10f, 20f, 19f), antiAlias = false),
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val clipExecution = assertIs<GPUClipExecutionPlan.ScissorOnly>(
            inventory.visualCommands.single().clipExecutionPlan,
        )
        assertEquals(
            org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(8, 10, 20, 19),
            clipExecution.scissor,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicDiagonalButtMiterStrokeScissorOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `even odd difference path clip with a hole matches CPU oracle natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.clip-even-odd-difference")
        val clipPath = Path().apply {
            fillType = FillType.EVEN_ODD
            addRect(RectF32.ofLTRB(3.25f, 3.25f, 28.75f, 28.75f))
            addRect(RectF32.ofLTRB(10.25f, 10.25f, 21.75f, 21.75f))
        }
        // Use a transfer-invariant primary so the byte oracle remains exact on the sRGB target.
        val red = ColorARGB.Red
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(0f, 0f, 32f, 32f),
                    Paint.fill(red).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.Complex(
                        listOf(ClipStackOp.PathOp(clipPath, ClipOp.DIFFERENCE, antiAlias = false)),
                    ),
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicEvenOddDifferencePathClipOracle(red), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `even odd intersect path clip with a hole matches CPU oracle natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.clip-even-odd-intersect")
        val clipPath = Path().apply {
            fillType = FillType.EVEN_ODD
            addRect(RectF32.ofLTRB(3.25f, 3.25f, 28.75f, 28.75f))
            addRect(RectF32.ofLTRB(10.25f, 10.25f, 21.75f, 21.75f))
        }
        // Use a transfer-invariant primary so the byte oracle remains exact on the sRGB target.
        val red = ColorARGB.Red
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(0f, 0f, 32f, 32f),
                    Paint.fill(red).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.Complex(
                        listOf(ClipStackOp.PathOp(clipPath, ClipOp.INTERSECT, antiAlias = false)),
                    ),
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            inventory.visualCommands.single().clipExecutionPlan,
        )
        assertEquals(GPUClipStencilOperation.Invert, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.Invert, execution.producer.backPassOperation)
        assertEquals(GPUClipStencilCompare.NotEqual, execution.consumer.compare)
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicEvenOddIntersectPathClipOracle(red), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `inverse even odd intersect path clip with a hole matches CPU oracle natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.clip-inverse-even-odd-intersect")
        val clipPath = Path().apply {
            fillType = FillType.INVERSE_EVEN_ODD
            addRect(RectF32.ofLTRB(3.25f, 3.25f, 28.75f, 28.75f))
            addRect(RectF32.ofLTRB(10.25f, 10.25f, 21.75f, 21.75f))
        }
        // Use a transfer-invariant primary so the byte oracle remains exact on the sRGB target.
        val red = ColorARGB.Red
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(0f, 0f, 32f, 32f),
                    Paint.fill(red).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.Complex(
                        listOf(ClipStackOp.PathOp(clipPath, ClipOp.INTERSECT, antiAlias = false)),
                    ),
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            inventory.visualCommands.single().clipExecutionPlan,
        )
        assertEquals(GPUClipStencilOperation.Invert, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.Invert, execution.producer.backPassOperation)
        assertEquals(GPUClipStencilCompare.Equal, execution.consumer.compare)
        val geometry = assertIs<GPUClipExecutionGeometry.Path>(execution.producer.geometry)
        assertEquals(GPUClipFillRule.EvenOdd, geometry.fillRule)
        assertTrue(geometry.inverseFill)
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicInverseEvenOddIntersectPathClipOracle(red), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `inverse winding intersect path clip fills outside triangle natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.clip-inverse-winding-intersect")
        val clipPath = Path().apply {
            fillType = FillType.INVERSE_WINDING
            moveTo(4.25f, 4.25f)
            lineTo(27.25f, 4.25f)
            lineTo(4.25f, 27.25f)
            close()
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(0f, 0f, 32f, 32f),
                    Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.Complex(
                        listOf(ClipStackOp.PathOp(clipPath, ClipOp.INTERSECT, antiAlias = false)),
                    ),
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val clipExecution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            inventory.visualCommands.single().clipExecutionPlan,
        )
        assertEquals(GPUClipStencilOperation.IncrementWrap, clipExecution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.DecrementWrap, clipExecution.producer.backPassOperation)
        assertEquals(GPUClipStencilCompare.Equal, clipExecution.consumer.compare)
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicInverseWindingIntersectTriangleOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `translated hard path clip retains device geometry and winding stencil state natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.clip-translated-intersect")
        val clipPath = Path().apply {
            // Local triangle (4.25,4.25)-(27.25,4.25)-(4.25,27.25) translated by (3,2).
            moveTo(7.25f, 6.25f)
            lineTo(30.25f, 6.25f)
            lineTo(7.25f, 29.25f)
            close()
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(0f, 0f, 32f, 32f),
                    Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.Complex(
                        listOf(
                            ClipStackOp.PathOp(
                                clipPath,
                                ClipOp.INTERSECT,
                                antiAlias = false,
                                transformClass = "translate",
                            ),
                        ),
                    ),
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            inventory.visualCommands.single().clipExecutionPlan,
        )
        assertEquals("translate", execution.pathTransformClass)
        assertEquals(GPUClipStencilOperation.IncrementWrap, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.DecrementWrap, execution.producer.backPassOperation)
        assertEquals(GPUClipStencilCompare.NotEqual, execution.consumer.compare)
        val geometry = assertIs<GPUClipExecutionGeometry.Path>(execution.producer.geometry)
        assertEquals(
            listOf(7.25f, 6.25f, 30.25f, 6.25f, 7.25f, 29.25f, 7.25f, 6.25f),
            geometry.vertices,
        )

        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicTranslatedWindingIntersectTriangleOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `translated winding difference path clip fills outside triangle natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.clip-translated-difference")
        val clipPath = Path().apply {
            // Local triangle (4.25,4.25)-(27.25,4.25)-(4.25,27.25) translated by (3,2).
            moveTo(7.25f, 6.25f)
            lineTo(30.25f, 6.25f)
            lineTo(7.25f, 29.25f)
            close()
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(0f, 0f, 32f, 32f),
                    Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.Complex(
                        listOf(
                            ClipStackOp.PathOp(
                                clipPath,
                                ClipOp.DIFFERENCE,
                                antiAlias = false,
                                transformClass = "translate",
                            ),
                        ),
                    ),
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            inventory.visualCommands.single().clipExecutionPlan,
        )
        assertEquals("translate", execution.pathTransformClass)
        assertEquals(GPUClipStencilOperation.IncrementWrap, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.DecrementWrap, execution.producer.backPassOperation)
        assertEquals(GPUClipStencilCompare.Equal, execution.consumer.compare)
        val geometry = assertIs<GPUClipExecutionGeometry.Path>(execution.producer.geometry)
        assertEquals(
            listOf(7.25f, 6.25f, 30.25f, 6.25f, 7.25f, 29.25f, 7.25f, 6.25f),
            geometry.vertices,
        )

        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicTranslatedWindingDifferenceTriangleOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `public canvas translated path clip snapshot renders natively after transform reset`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.public-clip-translated-intersect")
        val localTriangle = Path().apply {
            moveTo(4.25f, 4.25f)
            lineTo(27.25f, 4.25f)
            lineTo(4.25f, 27.25f)
            close()
        }
        val surface = Surface(32, 32)
        surface.canvas {
            translate(3f, 2f)
            clipPath(localTriangle, ClipOp.INTERSECT, antiAlias = false)
            resetMatrix()
            drawRect(RectF32.ofLTRB(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.Red).copy(antiAlias = false))
        }
        val operations = surface.snapshotOps()
        val inventory = GPUFramePathApiInventory.plan(
            operations = operations,
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            inventory.visualCommands.single().clipExecutionPlan,
        )
        assertEquals("translate", execution.pathTransformClass)
        assertEquals(GPUClipStencilOperation.IncrementWrap, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.DecrementWrap, execution.producer.backPassOperation)
        assertEquals(GPUClipStencilCompare.NotEqual, execution.consumer.compare)
        val geometry = assertIs<GPUClipExecutionGeometry.Path>(execution.producer.geometry)
        assertEquals(
            listOf(7.25f, 6.25f, 30.25f, 6.25f, 7.25f, 29.25f, 7.25f, 6.25f),
            geometry.vertices,
        )

        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicTranslatedWindingIntersectTriangleOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `diagonal butt miter stroke under winding path clip renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-diagonal-winding-clip")
        val clipPath = Path().apply {
            moveTo(7.25f, 6.25f)
            lineTo(30.25f, 6.25f)
            lineTo(7.25f, 29.25f)
            close()
        }
        val strokePath = Path().apply {
            // Fractional endpoints avoid rasterizer tie cases at the butt cap.
            moveTo(5.25f, 8.25f)
            lineTo(21.25f, 20.25f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    strokePath,
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.Complex(
                        listOf(ClipStackOp.PathOp(clipPath, ClipOp.INTERSECT, antiAlias = false)),
                    ),
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        assertEquals(
            "native.path_stroke.stencil_cover",
            inventory.recording.analysis.records.single().routeDecisionLabel,
        )
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            inventory.visualCommands.single().clipExecutionPlan,
        )
        assertEquals(GPUClipStencilOperation.IncrementWrap, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.DecrementWrap, execution.producer.backPassOperation)
        assertEquals(GPUClipStencilCompare.NotEqual, execution.consumer.compare)
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicDiagonalButtMiterWindingClipOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `diagonal butt miter stroke under even odd path clip renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-diagonal-even-odd-clip")
        val clipPath = Path().apply {
            fillType = FillType.EVEN_ODD
            addRect(RectF32.ofLTRB(3.25f, 3.25f, 28.75f, 28.75f))
            addRect(RectF32.ofLTRB(10.25f, 10.25f, 21.75f, 21.75f))
        }
        val strokePath = Path().apply {
            // Fractional endpoints avoid rasterizer tie cases at the butt cap.
            moveTo(5.25f, 8.25f)
            lineTo(21.25f, 20.25f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    strokePath,
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.Complex(
                        listOf(ClipStackOp.PathOp(clipPath, ClipOp.INTERSECT, antiAlias = false)),
                    ),
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        assertEquals(
            "native.path_stroke.stencil_cover",
            inventory.recording.analysis.records.single().routeDecisionLabel,
        )
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            inventory.visualCommands.single().clipExecutionPlan,
        )
        assertEquals(GPUClipStencilOperation.Invert, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.Invert, execution.producer.backPassOperation)
        assertEquals(GPUClipStencilCompare.NotEqual, execution.consumer.compare)
        val clipGeometry = assertIs<GPUClipExecutionGeometry.Path>(execution.producer.geometry)
        assertEquals(GPUClipFillRule.EvenOdd, clipGeometry.fillRule)
        assertTrue(!clipGeometry.inverseFill)
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicDiagonalButtMiterEvenOddClipOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `diagonal butt miter stroke under inverse winding path clip renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-diagonal-inverse-winding-clip")
        val clipPath = Path().apply {
            fillType = FillType.INVERSE_WINDING
            moveTo(7.25f, 6.25f)
            lineTo(30.25f, 6.25f)
            lineTo(7.25f, 29.25f)
            close()
        }
        val strokePath = Path().apply {
            // Fractional endpoints avoid rasterizer tie cases at the butt cap.
            moveTo(5.25f, 8.25f)
            lineTo(21.25f, 20.25f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    strokePath,
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.Complex(
                        listOf(ClipStackOp.PathOp(clipPath, ClipOp.INTERSECT, antiAlias = false)),
                    ),
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        assertEquals(
            "native.path_stroke.stencil_cover",
            inventory.recording.analysis.records.single().routeDecisionLabel,
        )
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            inventory.visualCommands.single().clipExecutionPlan,
        )
        assertEquals(GPUClipStencilOperation.IncrementWrap, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.DecrementWrap, execution.producer.backPassOperation)
        assertEquals(GPUClipStencilCompare.Equal, execution.consumer.compare)
        val clipGeometry = assertIs<GPUClipExecutionGeometry.Path>(execution.producer.geometry)
        assertEquals(GPUClipFillRule.Winding, clipGeometry.fillRule)
        assertTrue(clipGeometry.inverseFill)
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicDiagonalButtMiterInverseWindingClipOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `diagonal butt miter stroke under winding difference path clip renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-diagonal-winding-difference-clip")
        val clipPath = Path().apply {
            moveTo(7.25f, 6.25f)
            lineTo(30.25f, 6.25f)
            lineTo(7.25f, 29.25f)
            close()
        }
        val strokePath = Path().apply {
            // Fractional endpoints avoid rasterizer tie cases at the butt cap.
            moveTo(5.25f, 8.25f)
            lineTo(21.25f, 20.25f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    strokePath,
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.Complex(
                        listOf(ClipStackOp.PathOp(clipPath, ClipOp.DIFFERENCE, antiAlias = false)),
                    ),
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        assertEquals(
            "native.path_stroke.stencil_cover",
            inventory.recording.analysis.records.single().routeDecisionLabel,
        )
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            inventory.visualCommands.single().clipExecutionPlan,
        )
        assertEquals(GPUClipStencilOperation.IncrementWrap, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.DecrementWrap, execution.producer.backPassOperation)
        assertEquals(GPUClipStencilCompare.Equal, execution.consumer.compare)
        val clipGeometry = assertIs<GPUClipExecutionGeometry.Path>(execution.producer.geometry)
        assertEquals(GPUClipFillRule.Winding, clipGeometry.fillRule)
        assertTrue(!clipGeometry.inverseFill)
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicDiagonalButtMiterInverseWindingClipOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `scaled translated diagonal butt miter stroke under winding path clip renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-diagonal-scaled-winding-clip")
        val clipPath = Path().apply {
            // Local triangle scaled by 1.5 and translated by (2,1).
            moveTo(6.875f, 5.875f)
            lineTo(24.875f, 5.875f)
            lineTo(6.875f, 23.875f)
            close()
        }
        val strokePath = Path().apply {
            // Local endpoints (4.125,4.125)->(12.125,8.625) become device
            // endpoints (8.1875,7.1875)->(20.1875,13.9375), width 3.
            moveTo(4.125f, 4.125f)
            lineTo(12.125f, 8.625f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    strokePath,
                    Paint.stroke(ColorARGB.Red, 2f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.translation(2f, 1f) * Matrix3x3F32.scaling(1.5f, 1.5f),
                    ClipStack.Complex(
                        listOf(
                            ClipStackOp.PathOp(
                                clipPath,
                                ClipOp.INTERSECT,
                                antiAlias = false,
                                transformClass = "uniform-positive-scale-translate",
                            ),
                        ),
                    ),
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        assertEquals(
            "native.path_stroke.stencil_cover",
            inventory.recording.analysis.records.single().routeDecisionLabel,
        )
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            inventory.visualCommands.single().clipExecutionPlan,
        )
        assertEquals("uniform-positive-scale-translate", execution.pathTransformClass)
        assertEquals(GPUClipStencilOperation.IncrementWrap, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.DecrementWrap, execution.producer.backPassOperation)
        assertEquals(GPUClipStencilCompare.NotEqual, execution.consumer.compare)
        val clipGeometry = assertIs<GPUClipExecutionGeometry.Path>(execution.producer.geometry)
        assertEquals(
            listOf(6.875f, 5.875f, 24.875f, 5.875f, 6.875f, 23.875f, 6.875f, 5.875f),
            clipGeometry.vertices,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicScaledTranslatedDiagonalButtMiterWindingClipOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `right angle rotated diagonal butt miter stroke under winding path clip renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-diagonal-right-angle-clip")
        val clipPath = Path().apply {
            // Local triangle rotated 90 degrees around (16,16), retained in device space.
            moveTo(27.75f, 4.25f)
            lineTo(27.75f, 27.25f)
            lineTo(4.75f, 4.25f)
            close()
        }
        val strokePath = Path().apply {
            // Local endpoints (8.25,8.25)->(20.25,14.25) rotate to
            // device endpoints (23.75,8.25)->(17.75,20.25).
            moveTo(8.25f, 8.25f)
            lineTo(20.25f, 14.25f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    strokePath,
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.rotation(90f, pivotX = 16f, pivotY = 16f),
                    ClipStack.Complex(
                        listOf(
                            ClipStackOp.PathOp(
                                clipPath,
                                ClipOp.INTERSECT,
                                antiAlias = false,
                                transformClass = "right-angle-rotation",
                            ),
                        ),
                    ),
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        assertEquals(
            "native.path_stroke.stencil_cover",
            inventory.recording.analysis.records.single().routeDecisionLabel,
        )
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            inventory.visualCommands.single().clipExecutionPlan,
        )
        assertEquals("right-angle-rotation", execution.pathTransformClass)
        assertEquals(GPUClipStencilOperation.IncrementWrap, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.DecrementWrap, execution.producer.backPassOperation)
        assertEquals(GPUClipStencilCompare.NotEqual, execution.consumer.compare)
        val clipGeometry = assertIs<GPUClipExecutionGeometry.Path>(execution.producer.geometry)
        assertEquals(
            listOf(27.75f, 4.25f, 27.75f, 27.25f, 4.75f, 4.25f, 27.75f, 4.25f),
            clipGeometry.vertices,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicRightAngleDiagonalButtMiterWindingClipOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `right angle rotated diagonal square miter stroke under winding path clip renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-square-right-angle-clip")
        val clipPath = Path().apply {
            moveTo(27.75f, 4.25f)
            lineTo(27.75f, 27.25f)
            lineTo(4.75f, 4.25f)
            close()
        }
        val strokePath = Path().apply {
            // Local endpoints rotate 90 degrees around (16,16) to device endpoints
            // (23.75,8.25)->(17.75,20.25).
            moveTo(8.25f, 8.25f)
            lineTo(20.25f, 14.25f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    strokePath,
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.SQUARE,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.rotation(90f, pivotX = 16f, pivotY = 16f),
                    ClipStack.Complex(
                        listOf(
                            ClipStackOp.PathOp(
                                clipPath,
                                ClipOp.INTERSECT,
                                antiAlias = false,
                                transformClass = "right-angle-rotation",
                            ),
                        ),
                    ),
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            inventory.visualCommands.single().clipExecutionPlan,
        )
        assertEquals("right-angle-rotation", execution.pathTransformClass)
        assertEquals(GPUClipStencilOperation.IncrementWrap, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.DecrementWrap, execution.producer.backPassOperation)
        assertEquals(GPUClipStencilCompare.NotEqual, execution.consumer.compare)
        val clipGeometry = assertIs<GPUClipExecutionGeometry.Path>(execution.producer.geometry)
        assertEquals(
            listOf(27.75f, 4.25f, 27.75f, 27.25f, 4.75f, 4.25f, 27.75f, 4.25f),
            clipGeometry.vertices,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicRightAngleDiagonalSquareMiterWindingClipOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `half turn rotated diagonal butt miter stroke under winding path clip renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-diagonal-half-turn-clip")
        val clipPath = Path().apply {
            moveTo(3.25f, 3.25f)
            lineTo(28.75f, 3.25f)
            lineTo(28.75f, 28.75f)
            close()
        }
        val strokePath = Path().apply {
            // Local endpoints (8.25,8.25)->(20.25,14.25) rotate 180 degrees around
            // (16,10) to device endpoints (23.75,11.75)->(11.75,5.75).
            moveTo(8.25f, 8.25f)
            lineTo(20.25f, 14.25f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    strokePath,
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.rotation(180f, pivotX = 16f, pivotY = 10f),
                    ClipStack.Complex(
                        listOf(
                            ClipStackOp.PathOp(
                                clipPath,
                                ClipOp.INTERSECT,
                                antiAlias = false,
                                transformClass = "right-angle-rotation",
                            ),
                        ),
                    ),
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            inventory.visualCommands.single().clipExecutionPlan,
        )
        assertEquals("right-angle-rotation", execution.pathTransformClass)
        assertEquals(GPUClipStencilOperation.IncrementWrap, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.DecrementWrap, execution.producer.backPassOperation)
        assertEquals(GPUClipStencilCompare.NotEqual, execution.consumer.compare)
        val clipGeometry = assertIs<GPUClipExecutionGeometry.Path>(execution.producer.geometry)
        assertEquals(
            listOf(3.25f, 3.25f, 28.75f, 3.25f, 28.75f, 28.75f, 3.25f, 3.25f),
            clipGeometry.vertices,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicHalfTurnDiagonalButtMiterWindingClipOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `non right angle rotated diagonal stroke remains explicitly refused`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        try {
            val capabilities = requireNotNull(backend.capabilities)
            val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
            val clipPath = Path().apply {
                moveTo(7.25f, 6.25f)
                lineTo(30.25f, 6.25f)
                lineTo(7.25f, 29.25f)
                close()
            }
            val inventory = GPUFramePathApiInventory.plan(
                operations = listOf(
                    DisplayOp.DrawPath(
                        Path().apply {
                            moveTo(8.25f, 8.25f)
                            lineTo(20.25f, 14.25f)
                        },
                        Paint.stroke(ColorARGB.Red, 4f).copy(
                            antiAlias = false,
                            strokeCap = StrokeCap.BUTT,
                            strokeJoin = StrokeJoin.MITER,
                        ),
                        Matrix3x3F32.rotation(45f, pivotX = 16f, pivotY = 16f),
                        ClipStack.Complex(
                            listOf(
                                ClipStackOp.PathOp(
                                    clipPath,
                                    ClipOp.INTERSECT,
                                    antiAlias = false,
                                    transformClass = "affine",
                                ),
                            ),
                        ),
                    ),
                ),
                target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities,
                deviceGeneration = backend.deviceGeneration,
            )
            val record = inventory.recording.analysis.records.single()
            assertEquals("refused.unsupported.geometry.perspective_path", record.routeDecisionLabel)
        } finally {
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `diagonal square cap stroke under winding path clip renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        try {
            val capabilities = requireNotNull(backend.capabilities)
            val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
            val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
            val clipPath = Path().apply {
                moveTo(7.25f, 6.25f)
                lineTo(30.25f, 6.25f)
                lineTo(7.25f, 29.25f)
                close()
            }
            val inventory = GPUFramePathApiInventory.plan(
                operations = listOf(
                    DisplayOp.DrawPath(
                        Path().apply {
                            moveTo(5.25f, 8.25f)
                            lineTo(21.25f, 20.25f)
                        },
                        Paint.stroke(ColorARGB.Red, 4f).copy(
                            antiAlias = false,
                            strokeCap = StrokeCap.SQUARE,
                            strokeJoin = StrokeJoin.MITER,
                        ),
                        Matrix3x3F32.Identity,
                        ClipStack.Complex(
                            listOf(ClipStackOp.PathOp(clipPath, ClipOp.INTERSECT, antiAlias = false)),
                        ),
                    ),
                ),
                target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities,
                deviceGeneration = backend.deviceGeneration,
            )
            assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
            val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
                inventory.visualCommands.single().clipExecutionPlan,
            )
            assertEquals(GPUClipStencilOperation.IncrementWrap, execution.producer.frontPassOperation)
            assertEquals(GPUClipStencilOperation.DecrementWrap, execution.producer.backPassOperation)
            assertEquals(GPUClipStencilCompare.NotEqual, execution.consumer.compare)
            val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
                inventory,
                capabilities,
                targetBounds,
                GPUReadbackRequestID("readback.inventory-core-primitive.stroke-diagonal-square-clip"),
            )
            val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
                preparation,
                (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                    "${it.code.value}: ${it.message}; facts=${it.facts}"
                },
            ).taskList
            val session = backend.prepareSceneFrameSession(
                GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
            )
            try {
                val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-diagonal-square-clip")
                val completed = session.renderFrame(
                    prepared,
                    GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
                ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
                assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
                val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
                assertContentEquals(deterministicDiagonalSquareMiterWindingClipOracle(), gpu)
                assertEquals(1L, session.nativeCounters().submits)
                assertEquals(1L, session.nativeCounters().readbackCopies)
            } finally {
                session.close()
            }
        } finally {
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `uniform scaled translated hard path clip retains device geometry and winding stencil state natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.clip-uniform-scale-translate-intersect")
        val clipPath = Path().apply {
            // Local triangle (3.25,3.25)-(15.25,3.25)-(3.25,15.25), after scale 1.5 and
            // translation (2,1), retained directly in device space below.
            moveTo(6.875f, 5.875f)
            lineTo(24.875f, 5.875f)
            lineTo(6.875f, 23.875f)
            close()
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(0f, 0f, 32f, 32f),
                    Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.Complex(
                        listOf(
                            ClipStackOp.PathOp(
                                clipPath,
                                ClipOp.INTERSECT,
                                antiAlias = false,
                                transformClass = "uniform-positive-scale-translate",
                            ),
                        ),
                    ),
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            inventory.visualCommands.single().clipExecutionPlan,
        )
        assertEquals("uniform-positive-scale-translate", execution.pathTransformClass)
        assertEquals(GPUClipStencilOperation.IncrementWrap, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.DecrementWrap, execution.producer.backPassOperation)
        assertEquals(GPUClipStencilCompare.NotEqual, execution.consumer.compare)
        val geometry = assertIs<GPUClipExecutionGeometry.Path>(execution.producer.geometry)
        assertEquals(
            listOf(6.875f, 5.875f, 24.875f, 5.875f, 6.875f, 23.875f, 6.875f, 5.875f),
            geometry.vertices,
        )

        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicUniformScaledTranslatedWindingIntersectTriangleOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `single diagonal square miter stroke renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(RenderConfig.DEFAULT.mapPreparedGpuColorConfig())
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-diagonal-square")
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    // Fractional endpoints avoid rasterizer tie cases at the cap boundary.
                    Path().apply { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.SQUARE,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(inventory, capabilities, targetBounds, readbackId)
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(prepared, GPUSceneFrameOutputRequest.ReadbackRgba(readbackId))
                .completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicDiagonalSquareMiterStrokeOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `single segment butt miter stroke matches the deterministic CPU pixel oracle natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-butt-miter")
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(4f, 16f)
                        lineTo(28f, 16f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUFramePathApiInventory.prepareNativeTaskList(
                inventory = inventory,
                capabilities = capabilities,
                targetBounds = targetBounds,
                readbackRequestId = readbackId,
            ),
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(
                width = 32,
                height = 32,
                colorFormat = colorMapping.physicalFormat,
                colorInterpretation = colorMapping.interpretation,
            ),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes

            assertContentEquals(deterministicButtMiterStrokeOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `single segment round cap stroke matches the independent CPU pixel oracle natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-round-cap")
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(6f, 16f)
                        lineTo(26f, 16f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.ROUND,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory = inventory,
            capabilities = capabilities,
            targetBounds = targetBounds,
            readbackRequestId = readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let { diagnostic ->
                "${diagnostic.code.value}: ${diagnostic.message}; facts=${diagnostic.facts}"
            },
        )
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(
                width = 32,
                height = 32,
                colorFormat = colorMapping.physicalFormat,
                colorInterpretation = colorMapping.interpretation,
            ),
        )
        try {
            val completed = session.renderFrame(
                prepared.taskList,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes

            assertContentEquals(deterministicRoundCapStrokeOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `public Surface render expands one bounded stroke rect in one native frame`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        GPUBackendRuntimeNativeFactory.dispose()
        val surface = Surface(width = 64, height = 64)
        surface.canvas {
            drawRect(
                RectF32.ofLTRB(16f, 16f, 48f, 48f),
                Paint.stroke(ColorARGB.Red, 6f).copy(antiAlias = false),
            )
        }

        try {
            val result = surface.render()

            assertEquals(4, result.stats.opsDispatched)
            assertEquals(0, result.stats.opsRefused)
            assertEquals(4, result.stats.drawCallCount)
            assertEquals(1L, requireNotNull(GPUBackendRuntimeNativeFactory.createOrNull()).runtimeTelemetry.submissions)
            assertEquals(listOf(255, 0, 0, 255), rgba(result.pixels, 32, 16, 64))
            assertEquals(listOf(255, 0, 0, 255), rgba(result.pixels, 32, 47, 64))
            assertEquals(listOf(255, 0, 0, 255), rgba(result.pixels, 16, 32, 64))
            assertEquals(listOf(255, 0, 0, 255), rgba(result.pixels, 47, 32, 64))
            assertEquals(listOf(0, 0, 0, 0), rgba(result.pixels, 32, 32, 64))
            assertEquals(listOf(0, 0, 0, 0), rgba(result.pixels, 8, 8, 64))
        } finally {
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `round cap stroke under winding path clip remains explicitly refused`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        try {
            val capabilities = requireNotNull(backend.capabilities)
            val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
                RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
            )
            val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
            val clipPath = Path().apply {
                moveTo(3.25f, 3.25f)
                lineTo(28.75f, 3.25f)
                lineTo(3.25f, 28.75f)
                close()
            }
            val inventory = GPUFramePathApiInventory.plan(
                operations = listOf(
                    DisplayOp.DrawPath(
                        Path().apply {
                            moveTo(6f, 16f)
                            lineTo(26f, 16f)
                        },
                        Paint.stroke(ColorARGB.Red, 4f).copy(
                            antiAlias = false,
                            strokeCap = StrokeCap.ROUND,
                        ),
                        Matrix3x3F32.Identity,
                        ClipStack.Complex(
                            listOf(ClipStackOp.PathOp(clipPath, ClipOp.INTERSECT, antiAlias = false)),
                        ),
                    ),
                ),
                target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities,
                deviceGeneration = backend.deviceGeneration,
            )
            assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
            val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
                inventory,
                capabilities,
                targetBounds,
                null,
            )
            val refused = assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(preparation)
            assertEquals("unsupported.recording.core_primitive_path_stencil_clip", refused.diagnostic.code.value)
        } finally {
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `horizontal round cap stroke under integral device scissor renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-round-scissor")
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(6f, 16f)
                        lineTo(26f, 16f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.ROUND,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.DeviceRect(RectF32.ofLTRB(5f, 14f, 18f, 19f), antiAlias = false),
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val clipExecution = assertIs<GPUClipExecutionPlan.ScissorOnly>(
            inventory.visualCommands.single().clipExecutionPlan,
        )
        assertEquals(
            org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(5, 14, 18, 19),
            clipExecution.scissor,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicRoundCapStrokeScissorOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `translated horizontal round cap stroke under integral device scissor renders natively`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.stroke-round-translated-scissor")
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(6f, 16f)
                        lineTo(26f, 16f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.ROUND,
                    ),
                    Matrix3x3F32.translation(3f, 2f),
                    ClipStack.DeviceRect(RectF32.ofLTRB(8f, 16f, 21f, 21f), antiAlias = false),
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = backend.deviceGeneration,
        )
        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val clipExecution = assertIs<GPUClipExecutionPlan.ScissorOnly>(
            inventory.visualCommands.single().clipExecutionPlan,
        )
        assertEquals(
            org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(8, 16, 21, 21),
            clipExecution.scissor,
        )
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            targetBounds,
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        ).taskList
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(32, 32, colorMapping.physicalFormat, colorMapping.interpretation),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
            val gpu = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
            assertContentEquals(deterministicTranslatedRoundCapStrokeScissorOracle(), gpu)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `public Surface render submits a bounded round cap path stroke with the CPU oracle`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        GPUBackendRuntimeNativeFactory.dispose()
        val surface = Surface(width = 32, height = 32)
        surface.canvas {
            drawPath(
                Path().apply {
                    moveTo(6f, 16f)
                    lineTo(26f, 16f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = StrokeCap.ROUND,
                ),
            )
        }

        try {
            val result = surface.render()

            assertEquals(1, result.stats.opsDispatched)
            assertEquals(0, result.stats.opsRefused)
            assertContentEquals(deterministicRoundCapStrokeOracle().asUByteArray(), result.pixels)
            assertEquals(1L, requireNotNull(GPUBackendRuntimeNativeFactory.createOrNull()).runtimeTelemetry.submissions)
        } finally {
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `public Surface render paints exact clamp gradient stroke RGB annulus and preserves transparent center`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        GPUBackendRuntimeNativeFactory.dispose()
        val surface = Surface(width = 64, height = 64)
        surface.canvas {
            drawRect(
                RectF32.ofLTRB(8f, 16f, 56f, 48f),
                Paint.stroke(ColorARGB.Transparent, 4f).copy(
                    shader = Shader.LinearGradient(
                        Point2F32(8.5f, 32.5f), Point2F32(55.5f, 32.5f),
                        listOf(GradientStop(0f, ColorARGB.of(255, 255, 56, 56)), GradientStop(1f, ColorARGB.of(255, 56, 112, 255))),
                    ),
                    antiAlias = false,
                ),
            )
        }

        try {
            val result = surface.render()

            assertEquals(4, result.stats.opsDispatched)
            assertEquals(0, result.stats.opsRefused)
            assertRgbaWithinOneLsb(listOf(255, 56, 56, 255), rgba(result.pixels, 8, 16, 64))
            assertRgbaWithinOneLsb(listOf(56, 112, 255, 255), rgba(result.pixels, 55, 47, 64))
            assertEquals(listOf(0, 0, 0, 0), rgba(result.pixels, 32, 32, 64))
            assertEquals(listOf(0, 0, 0, 0), rgba(result.pixels, 4, 4, 64))
        } finally {
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `public Surface render submits bounded linear radial and sweep CorePrimitive gradients`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        GPUBackendRuntimeNativeFactory.dispose()
        val stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue))
        val surface = Surface(width = 48, height = 16)
        surface.canvas {
            drawRect(
                RectF32.ofLTRB(1f, 1f, 15f, 15f),
                Paint(shader = Shader.LinearGradient(Point2F32(1f, 8f), Point2F32(15f, 8f), stops)).copy(antiAlias = false),
            )
            drawRect(
                RectF32.ofLTRB(17f, 1f, 31f, 15f),
                Paint(shader = Shader.RadialGradient(Point2F32(24f, 8f), 7f, stops)).copy(antiAlias = false),
            )
            drawRect(
                RectF32.ofLTRB(33f, 1f, 47f, 15f),
                Paint(shader = Shader.SweepGradient(Point2F32(40f, 8f), stops = stops)).copy(antiAlias = false),
            )
        }

        try {
            val result = surface.render()

            assertEquals(3, result.stats.opsDispatched)
            assertEquals(0, result.stats.opsRefused)
            assertTrue(result.stats.drawCallCount >= 3)
        } finally {
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `display ops traverse inventory into one canonical native frame`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val generation = backend.deviceGeneration
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.rect-affine")
        val halfRed = ColorARGB.fromRGBA(1f, 0f, 0f, 0.5f)
        val halfGreen = ColorARGB.fromRGBA(0f, 1f, 0f, 0.5f)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(2f, 2f, 20f, 20f),
                    Paint.fill(halfRed).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.DeviceRect(RectF32.ofLTRB(4f, 5f, 18f, 19f), antiAlias = false),
                ),
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(6f, 6f, 12f, 12f),
                    Paint.fill(halfGreen).copy(antiAlias = false),
                    Matrix3x3F32.skewing(0.5f, 0f),
                    ClipStack.WideOpen,
                ),
            ),
            target = GPUTargetFacts(32, 32, colorMapping.physicalFormat.value),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = generation,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUFramePathApiInventory.prepareNativeTaskList(
                inventory = inventory,
                capabilities = capabilities,
                targetBounds = targetBounds,
                readbackRequestId = readbackId,
            ),
        ).taskList
        val framePlan = GPUFramePlanner.plan(prepared)
        val renderPass = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        assertEquals(2, renderPass.drawPackets.size)
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(
                width = 32,
                height = 32,
                colorFormat = colorMapping.physicalFormat,
                colorInterpretation = colorMapping.interpretation,
            ),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                completed.outcome,
                "${completed.diagnostic?.code?.value}: ${completed.diagnostic?.message}",
            )
            val bytes = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes

            // This proves DisplayOp -> inventory -> canonical native frame. It is not yet the active Surface route.
            // The canonical sRGB target encodes the 0.5 linear red channel as 188; alpha stays linear.
            assertPixelEquals(bytes, 6, 8, listOf(188, 0, 0, 128))
            // The later affine half-green draw produces linear-premul (0.25, 0.5, 0, 0.75),
            // encoded by the attachment as (137, 188, 0, 192).
            assertPixelEquals(bytes, 13, 8, listOf(137, 188, 0, 192))
            // Outside the rect scissor and where a vertically mirrored affine draw would appear.
            assertPixelEquals(bytes, 3, 8, listOf(0, 0, 0, 0))
            assertPixelEquals(bytes, 13, 23, listOf(0, 0, 0, 0))

            val counters = session.nativeCounters()
            assertEquals(1L, counters.encoders)
            assertEquals(1L, counters.commandBuffers)
            assertEquals(1L, counters.submits)
            assertEquals(1L, counters.readbackCopies)
            assertEquals(1L, counters.corePrimitiveInvariantCreations)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    private fun assertPixelEquals(
        bytes: ByteArray,
        x: Int,
        y: Int,
        expected: List<Int>,
    ) {
        val offset = (y * 32 + x) * 4
        val actual = (0..3).map { bytes[offset + it].toInt() and 0xff }
        assertEquals(expected, actual, "pixel ($x,$y)")
    }

    private fun deterministicButtMiterStrokeOracle(): ByteArray = ByteArray(32 * 32 * 4).also { rgba ->
        for (y in 14 until 18) {
            for (x in 4 until 28) {
                val offset = (y * 32 + x) * 4
                rgba[offset] = 0xff.toByte()
                rgba[offset + 3] = 0xff.toByte()
            }
        }
    }

    private fun deterministicHairlineOracle(): ByteArray = ByteArray(32 * 32 * 4).also { rgba ->
        for (x in 4 until 28) {
            val offset = (15 * 32 + x) * 4
            rgba[offset] = 0xff.toByte()
            rgba[offset + 3] = 0xff.toByte()
        }
    }

    private fun deterministicScaledHairlineOracle(): ByteArray = ByteArray(32 * 32 * 4).also { rgba ->
        for (x in 8 until 28) {
            val offset = (15 * 32 + x) * 4
            rgba[offset] = 0xff.toByte()
            rgba[offset + 3] = 0xff.toByte()
        }
    }

    private fun deterministicScaledTranslatedHairlineOracle(): ByteArray = ByteArray(32 * 32 * 4).also { rgba ->
        for (x in 10 until 30) {
            val offset = (18 * 32 + x) * 4
            rgba[offset] = 0xff.toByte()
            rgba[offset + 3] = 0xff.toByte()
        }
    }

    private fun deterministicScaledButtMiterStrokeOracle(): ByteArray = ByteArray(32 * 32 * 4).also { rgba ->
        for (y in 14 until 18) {
            for (x in 8 until 28) {
                val offset = (y * 32 + x) * 4
                rgba[offset] = 0xff.toByte()
                rgba[offset + 3] = 0xff.toByte()
            }
        }
    }

    private fun deterministicScaledTranslatedButtMiterStrokeOracle(): ByteArray = ByteArray(32 * 32 * 4).also { rgba ->
        for (y in 17 until 21) {
            for (x in 10 until 30) {
                val offset = (y * 32 + x) * 4
                rgba[offset] = 0xff.toByte()
                rgba[offset + 3] = 0xff.toByte()
            }
        }
    }

    /** Independent pixel-center oracle for the transformed device-space segment. */
    private fun deterministicScaledTranslatedDiagonalButtMiterStrokeOracle(): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            val ax = 10.25f
            val ay = 11.25f
            val bx = 26.25f
            val by = 20.25f
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            val halfWidthSquared = 2f * 2f
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val projection = ((px - ax) * dx + (py - ay) * dy) / lengthSquared
                    if (projection < 0f || projection > 1f) continue
                    val qx = ax + projection * dx
                    val qy = ay + projection * dy
                    val distanceX = px - qx
                    val distanceY = py - qy
                    if (distanceX * distanceX + distanceY * distanceY > halfWidthSquared) continue
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = 0xff.toByte()
                    rgba[offset + 3] = 0xff.toByte()
                }
            }
        }

    /** Independent square-cap oracle in device space: extend by half width along the tangent. */
    private fun deterministicScaledTranslatedDiagonalSquareMiterStrokeOracle(): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            val ax = 10.25f
            val ay = 11.25f
            val bx = 26.25f
            val by = 20.25f
            val dx = bx - ax
            val dy = by - ay
            val length = kotlin.math.sqrt(dx * dx + dy * dy)
            val tangentX = dx / length
            val tangentY = dy / length
            val extension = 2f
            val startX = ax - tangentX * extension
            val startY = ay - tangentY * extension
            val endX = bx + tangentX * extension
            val endY = by + tangentY * extension
            val extendedDx = endX - startX
            val extendedDy = endY - startY
            val lengthSquared = extendedDx * extendedDx + extendedDy * extendedDy
            val halfWidthSquared = extension * extension
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val projection = ((px - startX) * extendedDx + (py - startY) * extendedDy) / lengthSquared
                    if (projection < 0f || projection > 1f) continue
                    val qx = startX + projection * extendedDx
                    val qy = startY + projection * extendedDy
                    val distanceX = px - qx
                    val distanceY = py - qy
                    if (distanceX * distanceX + distanceY * distanceY > halfWidthSquared) continue
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = 0xff.toByte()
                    rgba[offset + 3] = 0xff.toByte()
                }
            }
        }

    private fun deterministicVerticalButtMiterStrokeOracle(): ByteArray = ByteArray(32 * 32 * 4).also { rgba ->
        for (y in 4 until 28) {
            for (x in 14 until 18) {
                val offset = (y * 32 + x) * 4
                rgba[offset] = 0xff.toByte()
                rgba[offset + 3] = 0xff.toByte()
            }
        }
    }

    private fun deterministicSquareButtMiterStrokeOracle(): ByteArray = ByteArray(32 * 32 * 4).also { rgba ->
        for (y in 14 until 18) {
            for (x in 6 until 26) {
                val offset = (y * 32 + x) * 4
                rgba[offset] = 0xff.toByte()
                rgba[offset + 3] = 0xff.toByte()
            }
        }
    }

    private fun deterministicDiagonalButtMiterStrokeOracle(): ByteArray = ByteArray(32 * 32 * 4).also { rgba ->
        val ax = 5.25f
        val ay = 8.25f
        val bx = 21.25f
        val by = 20.25f
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val halfWidthSquared = 2f * 2f
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                val px = x + 0.5f
                val py = y + 0.5f
                val projection = ((px - ax) * dx + (py - ay) * dy) / lengthSquared
                if (projection < 0f || projection > 1f) continue
                val qx = ax + projection * dx
                val qy = ay + projection * dy
                val distanceX = px - qx
                val distanceY = py - qy
                if (distanceX * distanceX + distanceY * distanceY > halfWidthSquared) continue
                val offset = (y * 32 + x) * 4
                rgba[offset] = 0xff.toByte()
                rgba[offset + 3] = 0xff.toByte()
            }
        }
    }

    /** Independent pixel-center oracle for the same diagonal stroke restricted to an integral scissor. */
    private fun deterministicDiagonalButtMiterStrokeScissorOracle(): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            val ax = 5.25f
            val ay = 8.25f
            val bx = 21.25f
            val by = 20.25f
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            val halfWidthSquared = 2f * 2f
            for (y in 10 until 19) {
                for (x in 8 until 20) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val projection = ((px - ax) * dx + (py - ay) * dy) / lengthSquared
                    if (projection < 0f || projection > 1f) continue
                    val qx = ax + projection * dx
                    val qy = ay + projection * dy
                    val distanceX = px - qx
                    val distanceY = py - qy
                    if (distanceX * distanceX + distanceY * distanceY > halfWidthSquared) continue
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = 0xff.toByte()
                    rgba[offset + 3] = 0xff.toByte()
                }
            }
        }

    /** Independent pixel-centre oracle for a diagonal butt/miter stroke intersected with a triangle clip. */
    private fun deterministicDiagonalButtMiterWindingClipOracle(): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            val strokeAx = 5.25f
            val strokeAy = 8.25f
            val strokeBx = 21.25f
            val strokeBy = 20.25f
            val strokeDx = strokeBx - strokeAx
            val strokeDy = strokeBy - strokeAy
            val strokeLengthSquared = strokeDx * strokeDx + strokeDy * strokeDy
            val halfWidthSquared = 2f * 2f
            val clipAx = 7.25f
            val clipAy = 6.25f
            val clipBx = 30.25f
            val clipBy = 6.25f
            val clipCx = 7.25f
            val clipCy = 29.25f
            val denominator = (clipBy - clipCy) * (clipAx - clipCx) +
                (clipCx - clipBx) * (clipAy - clipCy)
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val u = ((clipBy - clipCy) * (px - clipCx) +
                        (clipCx - clipBx) * (py - clipCy)) / denominator
                    val v = ((clipCy - clipAy) * (px - clipCx) +
                        (clipAx - clipCx) * (py - clipCy)) / denominator
                    if (u < 0f || v < 0f || u + v > 1f) continue
                    val projection = ((px - strokeAx) * strokeDx + (py - strokeAy) * strokeDy) /
                        strokeLengthSquared
                    if (projection < 0f || projection > 1f) continue
                    val closestX = strokeAx + projection * strokeDx
                    val closestY = strokeAy + projection * strokeDy
                    val distanceX = px - closestX
                    val distanceY = py - closestY
                    if (distanceX * distanceX + distanceY * distanceY > halfWidthSquared) continue
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = 0xff.toByte()
                    rgba[offset + 3] = 0xff.toByte()
                }
            }
        }

    /** Independent pixel-centre oracle for a diagonal butt/miter stroke outside an inverse Winding clip. */
    private fun deterministicDiagonalButtMiterInverseWindingClipOracle(): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            val strokeAx = 5.25f
            val strokeAy = 8.25f
            val strokeBx = 21.25f
            val strokeBy = 20.25f
            val strokeDx = strokeBx - strokeAx
            val strokeDy = strokeBy - strokeAy
            val strokeLengthSquared = strokeDx * strokeDx + strokeDy * strokeDy
            val halfWidthSquared = 2f * 2f
            val clipAx = 7.25f
            val clipAy = 6.25f
            val clipBx = 30.25f
            val clipBy = 6.25f
            val clipCx = 7.25f
            val clipCy = 29.25f
            val denominator = (clipBy - clipCy) * (clipAx - clipCx) +
                (clipCx - clipBx) * (clipAy - clipCy)
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val u = ((clipBy - clipCy) * (px - clipCx) +
                        (clipCx - clipBx) * (py - clipCy)) / denominator
                    val v = ((clipCy - clipAy) * (px - clipCx) +
                        (clipAx - clipCx) * (py - clipCy)) / denominator
                    if (u >= 0f && v >= 0f && u + v <= 1f) continue
                    val projection = ((px - strokeAx) * strokeDx + (py - strokeAy) * strokeDy) /
                        strokeLengthSquared
                    if (projection < 0f || projection > 1f) continue
                    val closestX = strokeAx + projection * strokeDx
                    val closestY = strokeAy + projection * strokeDy
                    val distanceX = px - closestX
                    val distanceY = py - closestY
                    if (distanceX * distanceX + distanceY * distanceY > halfWidthSquared) continue
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = 0xff.toByte()
                    rgba[offset + 3] = 0xff.toByte()
                }
            }
        }

    /** Independent device-space oracle for the scaled/translated diagonal stroke and clip. */
    private fun deterministicScaledTranslatedDiagonalButtMiterWindingClipOracle(): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            val strokeAx = 8.1875f
            val strokeAy = 7.1875f
            val strokeBx = 20.1875f
            val strokeBy = 13.9375f
            val strokeDx = strokeBx - strokeAx
            val strokeDy = strokeBy - strokeAy
            val strokeLengthSquared = strokeDx * strokeDx + strokeDy * strokeDy
            val halfWidthSquared = 1.5f * 1.5f
            val clipAx = 6.875f
            val clipAy = 5.875f
            val clipBx = 24.875f
            val clipBy = 5.875f
            val clipCx = 6.875f
            val clipCy = 23.875f
            val denominator = (clipBy - clipCy) * (clipAx - clipCx) +
                (clipCx - clipBx) * (clipAy - clipCy)
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val u = ((clipBy - clipCy) * (px - clipCx) +
                        (clipCx - clipBx) * (py - clipCy)) / denominator
                    val v = ((clipCy - clipAy) * (px - clipCx) +
                        (clipAx - clipCx) * (py - clipCy)) / denominator
                    if (u < 0f || v < 0f || u + v > 1f) continue
                    val projection = ((px - strokeAx) * strokeDx + (py - strokeAy) * strokeDy) /
                        strokeLengthSquared
                    if (projection < 0f || projection > 1f) continue
                    val closestX = strokeAx + projection * strokeDx
                    val closestY = strokeAy + projection * strokeDy
                    val distanceX = px - closestX
                    val distanceY = py - closestY
                    if (distanceX * distanceX + distanceY * distanceY > halfWidthSquared) continue
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = 0xff.toByte()
                    rgba[offset + 3] = 0xff.toByte()
                }
            }
        }

    /** Independent device-space oracle for the right-angle rotated diagonal stroke and clip. */
    private fun deterministicRightAngleDiagonalButtMiterWindingClipOracle(): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            val strokeAx = 23.75f
            val strokeAy = 8.25f
            val strokeBx = 17.75f
            val strokeBy = 20.25f
            val strokeDx = strokeBx - strokeAx
            val strokeDy = strokeBy - strokeAy
            val strokeLengthSquared = strokeDx * strokeDx + strokeDy * strokeDy
            val halfWidthSquared = 2f * 2f
            val clipAx = 27.75f
            val clipAy = 4.25f
            val clipBx = 27.75f
            val clipBy = 27.25f
            val clipCx = 4.75f
            val clipCy = 4.25f
            val denominator = (clipBy - clipCy) * (clipAx - clipCx) +
                (clipCx - clipBx) * (clipAy - clipCy)
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val u = ((clipBy - clipCy) * (px - clipCx) +
                        (clipCx - clipBx) * (py - clipCy)) / denominator
                    val v = ((clipCy - clipAy) * (px - clipCx) +
                        (clipAx - clipCx) * (py - clipCy)) / denominator
                    if (u < 0f || v < 0f || u + v > 1f) continue
                    val projection = ((px - strokeAx) * strokeDx + (py - strokeAy) * strokeDy) /
                        strokeLengthSquared
                    if (projection < 0f || projection > 1f) continue
                    val closestX = strokeAx + projection * strokeDx
                    val closestY = strokeAy + projection * strokeDy
                    val distanceX = px - closestX
                    val distanceY = py - closestY
                    if (distanceX * distanceX + distanceY * distanceY > halfWidthSquared) continue
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = 0xff.toByte()
                    rgba[offset + 3] = 0xff.toByte()
                }
            }
        }

    /** Independent device-space oracle for the quarter-turned square-cap stroke and clip. */
    private fun deterministicRightAngleDiagonalSquareMiterWindingClipOracle(): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            val ax = 23.75f
            val ay = 8.25f
            val bx = 17.75f
            val by = 20.25f
            val dx = bx - ax
            val dy = by - ay
            val length = kotlin.math.sqrt(dx * dx + dy * dy)
            val tangentX = dx / length
            val tangentY = dy / length
            val extension = 2f
            val startX = ax - tangentX * extension
            val startY = ay - tangentY * extension
            val endX = bx + tangentX * extension
            val endY = by + tangentY * extension
            val extendedDx = endX - startX
            val extendedDy = endY - startY
            val lengthSquared = extendedDx * extendedDx + extendedDy * extendedDy
            val clipAx = 27.75f
            val clipAy = 4.25f
            val clipBx = 27.75f
            val clipBy = 27.25f
            val clipCx = 4.75f
            val clipCy = 4.25f
            val denominator = (clipBy - clipCy) * (clipAx - clipCx) +
                (clipCx - clipBx) * (clipAy - clipCy)
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val u = ((clipBy - clipCy) * (px - clipCx) +
                        (clipCx - clipBx) * (py - clipCy)) / denominator
                    val v = ((clipCy - clipAy) * (px - clipCx) +
                        (clipAx - clipCx) * (py - clipCy)) / denominator
                    if (u < 0f || v < 0f || u + v > 1f) continue
                    val projection = ((px - startX) * extendedDx + (py - startY) * extendedDy) /
                        lengthSquared
                    if (projection < 0f || projection > 1f) continue
                    val closestX = startX + projection * extendedDx
                    val closestY = startY + projection * extendedDy
                    val distanceX = px - closestX
                    val distanceY = py - closestY
                    if (distanceX * distanceX + distanceY * distanceY > extension * extension) continue
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = 0xff.toByte()
                    rgba[offset + 3] = 0xff.toByte()
                }
            }
        }

    /** Independent device-space oracle for the half-turn rotated diagonal stroke and clip. */
    private fun deterministicHalfTurnDiagonalButtMiterWindingClipOracle(): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            val strokeAx = 23.75f
            val strokeAy = 11.75f
            val strokeBx = 11.75f
            val strokeBy = 5.75f
            val strokeDx = strokeBx - strokeAx
            val strokeDy = strokeBy - strokeAy
            val strokeLengthSquared = strokeDx * strokeDx + strokeDy * strokeDy
            val halfWidthSquared = 2f * 2f
            val clipAx = 3.25f
            val clipAy = 3.25f
            val clipBx = 28.75f
            val clipBy = 3.25f
            val clipCx = 28.75f
            val clipCy = 28.75f
            val denominator = (clipBy - clipCy) * (clipAx - clipCx) +
                (clipCx - clipBx) * (clipAy - clipCy)
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val u = ((clipBy - clipCy) * (px - clipCx) +
                        (clipCx - clipBx) * (py - clipCy)) / denominator
                    val v = ((clipCy - clipAy) * (px - clipCx) +
                        (clipAx - clipCx) * (py - clipCy)) / denominator
                    if (u < 0f || v < 0f || u + v > 1f) continue
                    val projection = ((px - strokeAx) * strokeDx + (py - strokeAy) * strokeDy) /
                        strokeLengthSquared
                    if (projection < 0f || projection > 1f) continue
                    val closestX = strokeAx + projection * strokeDx
                    val closestY = strokeAy + projection * strokeDy
                    val distanceX = px - closestX
                    val distanceY = py - closestY
                    if (distanceX * distanceX + distanceY * distanceY > halfWidthSquared) continue
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = 0xff.toByte()
                    rgba[offset + 3] = 0xff.toByte()
                }
            }
        }

    /** Independent pixel-centre oracle for a diagonal butt/miter stroke intersected with an EvenOdd shell. */
    private fun deterministicDiagonalButtMiterEvenOddClipOracle(): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            val strokeAx = 5.25f
            val strokeAy = 8.25f
            val strokeBx = 21.25f
            val strokeBy = 20.25f
            val strokeDx = strokeBx - strokeAx
            val strokeDy = strokeBy - strokeAy
            val strokeLengthSquared = strokeDx * strokeDx + strokeDy * strokeDy
            val halfWidthSquared = 2f * 2f
            val outerLeft = 3.25f
            val outerTop = 3.25f
            val outerRight = 28.75f
            val outerBottom = 28.75f
            val innerLeft = 10.25f
            val innerTop = 10.25f
            val innerRight = 21.75f
            val innerBottom = 21.75f
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val inOuter = px > outerLeft && px < outerRight && py > outerTop && py < outerBottom
                    val inInner = px > innerLeft && px < innerRight && py > innerTop && py < innerBottom
                    if (!inOuter.xor(inInner)) continue
                    val projection = ((px - strokeAx) * strokeDx + (py - strokeAy) * strokeDy) /
                        strokeLengthSquared
                    if (projection < 0f || projection > 1f) continue
                    val closestX = strokeAx + projection * strokeDx
                    val closestY = strokeAy + projection * strokeDy
                    val distanceX = px - closestX
                    val distanceY = py - closestY
                    if (distanceX * distanceX + distanceY * distanceY > halfWidthSquared) continue
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = 0xff.toByte()
                    rgba[offset + 3] = 0xff.toByte()
                }
            }
        }

    /** Independent pixel-centre oracle for the fixed EvenOdd rectangle-hole difference clip. */
    private fun deterministicEvenOddDifferencePathClipOracle(fill: ColorARGB): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            val outerLeft = 3.25f
            val outerTop = 3.25f
            val outerRight = 28.75f
            val outerBottom = 28.75f
            val innerLeft = 10.25f
            val innerTop = 10.25f
            val innerRight = 21.75f
            val innerBottom = 21.75f
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val inOuter = px > outerLeft && px < outerRight && py > outerTop && py < outerBottom
                    val inInner = px > innerLeft && px < innerRight && py > innerTop && py < innerBottom
                    if (inOuter.xor(inInner)) continue
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = fill.red.toByte()
                    rgba[offset + 1] = fill.green.toByte()
                    rgba[offset + 2] = fill.blue.toByte()
                    rgba[offset + 3] = fill.alpha.toByte()
                }
            }
        }

    /** Independent pixel-centre oracle for the fixed EvenOdd rectangle-hole intersect clip. */
    private fun deterministicEvenOddIntersectPathClipOracle(fill: ColorARGB): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            val outerLeft = 3.25f
            val outerTop = 3.25f
            val outerRight = 28.75f
            val outerBottom = 28.75f
            val innerLeft = 10.25f
            val innerTop = 10.25f
            val innerRight = 21.75f
            val innerBottom = 21.75f
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val inOuter = px > outerLeft && px < outerRight && py > outerTop && py < outerBottom
                    val inInner = px > innerLeft && px < innerRight && py > innerTop && py < innerBottom
                    if (!inOuter.xor(inInner)) continue
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = fill.red.toByte()
                    rgba[offset + 1] = fill.green.toByte()
                    rgba[offset + 2] = fill.blue.toByte()
                    rgba[offset + 3] = fill.alpha.toByte()
                }
            }
        }

    /** Independent pixel-centre oracle for the fixed inverse EvenOdd rectangle-hole intersect clip. */
    private fun deterministicInverseEvenOddIntersectPathClipOracle(fill: ColorARGB): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            val outerLeft = 3.25f
            val outerTop = 3.25f
            val outerRight = 28.75f
            val outerBottom = 28.75f
            val innerLeft = 10.25f
            val innerTop = 10.25f
            val innerRight = 21.75f
            val innerBottom = 21.75f
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val inOuter = px > outerLeft && px < outerRight && py > outerTop && py < outerBottom
                    val inInner = px > innerLeft && px < innerRight && py > innerTop && py < innerBottom
                    if (inOuter && !inInner) continue
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = fill.red.toByte()
                    rgba[offset + 1] = fill.green.toByte()
                    rgba[offset + 2] = fill.blue.toByte()
                    rgba[offset + 3] = fill.alpha.toByte()
                }
            }
        }

    /** Independent barycentric pixel-centre oracle for inverse winding triangle coverage. */
    private fun deterministicInverseWindingIntersectTriangleOracle(): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            val ax = 4.25f
            val ay = 4.25f
            val bx = 27.25f
            val by = 4.25f
            val cx = 4.25f
            val cy = 27.25f
            val denominator = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val u = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / denominator
                    val v = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / denominator
                    if (u >= 0f && v >= 0f && u + v <= 1f) continue
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = 0xff.toByte()
                    rgba[offset + 3] = 0xff.toByte()
                }
            }
        }

    /** Independent barycentric pixel-centre oracle for the device-translated winding triangle. */
    private fun deterministicTranslatedWindingIntersectTriangleOracle(): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            val ax = 7.25f
            val ay = 6.25f
            val bx = 30.25f
            val by = 6.25f
            val cx = 7.25f
            val cy = 29.25f
            val denominator = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val u = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / denominator
                    val v = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / denominator
                    if (u < 0f || v < 0f || u + v > 1f) continue
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = 0xff.toByte()
                    rgba[offset + 3] = 0xff.toByte()
                }
            }
        }

    /** Independent barycentric pixel-centre oracle for the device-translated winding difference clip. */
    private fun deterministicTranslatedWindingDifferenceTriangleOracle(): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            val ax = 7.25f
            val ay = 6.25f
            val bx = 30.25f
            val by = 6.25f
            val cx = 7.25f
            val cy = 29.25f
            val denominator = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val u = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / denominator
                    val v = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / denominator
                    if (u >= 0f && v >= 0f && u + v <= 1f) continue
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = 0xff.toByte()
                    rgba[offset + 3] = 0xff.toByte()
                }
            }
        }

    /** Independent barycentric pixel-centre oracle for the scaled/translated triangle. */
    private fun deterministicUniformScaledTranslatedWindingIntersectTriangleOracle(): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            val ax = 6.875f
            val ay = 5.875f
            val bx = 24.875f
            val by = 5.875f
            val cx = 6.875f
            val cy = 23.875f
            val denominator = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val u = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / denominator
                    val v = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / denominator
                    if (u < 0f || v < 0f || u + v > 1f) continue
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = 0xff.toByte()
                    rgba[offset + 3] = 0xff.toByte()
                }
            }
        }

    /**
     * Independent square-cap oracle: extend the segment by half the stroke width along its
     * tangent, then classify pixel centers by the Euclidean distance to that extended segment.
     */
    private fun deterministicDiagonalSquareMiterStrokeOracle(): ByteArray = ByteArray(32 * 32 * 4).also { rgba ->
        val ax = 5.25f
        val ay = 8.25f
        val bx = 21.25f
        val by = 20.25f
        val dx = bx - ax
        val dy = by - ay
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        val tangentX = dx / length
        val tangentY = dy / length
        val extension = 2f
        val startX = ax - tangentX * extension
        val startY = ay - tangentY * extension
        val endX = bx + tangentX * extension
        val endY = by + tangentY * extension
        val extendedDx = endX - startX
        val extendedDy = endY - startY
        val lengthSquared = extendedDx * extendedDx + extendedDy * extendedDy
        val halfWidthSquared = extension * extension
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                val px = x + 0.5f
                val py = y + 0.5f
                val projection = ((px - startX) * extendedDx + (py - startY) * extendedDy) / lengthSquared
                if (projection < 0f || projection > 1f) continue
                val qx = startX + projection * extendedDx
                val qy = startY + projection * extendedDy
                val distanceX = px - qx
                val distanceY = py - qy
                if (distanceX * distanceX + distanceY * distanceY > halfWidthSquared) continue
                val offset = (y * 32 + x) * 4
                rgba[offset] = 0xff.toByte()
                rgba[offset + 3] = 0xff.toByte()
            }
        }
    }

    /** Independent square-cap oracle for the diagonal stroke intersected with a Winding triangle. */
    private fun deterministicDiagonalSquareMiterWindingClipOracle(): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            val ax = 5.25f
            val ay = 8.25f
            val bx = 21.25f
            val by = 20.25f
            val dx = bx - ax
            val dy = by - ay
            val length = kotlin.math.sqrt(dx * dx + dy * dy)
            val tangentX = dx / length
            val tangentY = dy / length
            val extension = 2f
            val startX = ax - tangentX * extension
            val startY = ay - tangentY * extension
            val endX = bx + tangentX * extension
            val endY = by + tangentY * extension
            val extendedDx = endX - startX
            val extendedDy = endY - startY
            val lengthSquared = extendedDx * extendedDx + extendedDy * extendedDy
            val clipAx = 7.25f
            val clipAy = 6.25f
            val clipBx = 30.25f
            val clipBy = 6.25f
            val clipCx = 7.25f
            val clipCy = 29.25f
            val denominator = (clipBy - clipCy) * (clipAx - clipCx) +
                (clipCx - clipBx) * (clipAy - clipCy)
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val u = ((clipBy - clipCy) * (px - clipCx) +
                        (clipCx - clipBx) * (py - clipCy)) / denominator
                    val v = ((clipCy - clipAy) * (px - clipCx) +
                        (clipAx - clipCx) * (py - clipCy)) / denominator
                    if (u < 0f || v < 0f || u + v > 1f) continue
                    val projection = ((px - startX) * extendedDx + (py - startY) * extendedDy) /
                        lengthSquared
                    if (projection < 0f || projection > 1f) continue
                    val closestX = startX + projection * extendedDx
                    val closestY = startY + projection * extendedDy
                    val distanceX = px - closestX
                    val distanceY = py - closestY
                    if (distanceX * distanceX + distanceY * distanceY > extension * extension) continue
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = 0xff.toByte()
                    rgba[offset + 3] = 0xff.toByte()
                }
            }
        }

    /**
     * Independent analytic oracle: the round-capped segment is the union of its
     * central rectangle and two radius-two disks evaluated at pixel centers.
     */
    private fun deterministicRoundCapStrokeOracle(): ByteArray = ByteArray(32 * 32 * 4).also { rgba ->
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                val sampleX = x + 0.5f
                val sampleY = y + 0.5f
                val inBody = sampleX in 6f..26f && sampleY in 14f..18f
                val inStartCap = (sampleX - 6f) * (sampleX - 6f) + (sampleY - 16f) * (sampleY - 16f) <= 4f
                val inEndCap = (sampleX - 26f) * (sampleX - 26f) + (sampleY - 16f) * (sampleY - 16f) <= 4f
                if (inBody || inStartCap || inEndCap) {
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = 0xff.toByte()
                    rgba[offset + 3] = 0xff.toByte()
                }
            }
        }
    }

    /** Independent round-cap oracle intersected with the integral device scissor. */
    private fun deterministicRoundCapStrokeScissorOracle(): ByteArray = ByteArray(32 * 32 * 4).also { rgba ->
        for (y in 14 until 19) {
            for (x in 5 until 18) {
                val sampleX = x + 0.5f
                val sampleY = y + 0.5f
                val inBody = sampleX in 6f..26f && sampleY in 14f..18f
                val inStartCap = (sampleX - 6f) * (sampleX - 6f) + (sampleY - 16f) * (sampleY - 16f) <= 4f
                val inEndCap = (sampleX - 26f) * (sampleX - 26f) + (sampleY - 16f) * (sampleY - 16f) <= 4f
                if (inBody || inStartCap || inEndCap) {
                    val offset = (y * 32 + x) * 4
                    rgba[offset] = 0xff.toByte()
                    rgba[offset + 3] = 0xff.toByte()
                }
            }
        }
    }

    /** Independent device-space oracle for the translated round-cap stroke and scissor. */
    private fun deterministicTranslatedRoundCapStrokeScissorOracle(): ByteArray =
        ByteArray(32 * 32 * 4).also { rgba ->
            for (y in 16 until 21) {
                for (x in 8 until 21) {
                    val sampleX = x + 0.5f
                    val sampleY = y + 0.5f
                    val inBody = sampleX in 9f..29f && sampleY in 16f..20f
                    val inStartCap = (sampleX - 9f) * (sampleX - 9f) + (sampleY - 18f) * (sampleY - 18f) <= 4f
                    val inEndCap = (sampleX - 29f) * (sampleX - 29f) + (sampleY - 18f) * (sampleY - 18f) <= 4f
                    if (inBody || inStartCap || inEndCap) {
                        val offset = (y * 32 + x) * 4
                        rgba[offset] = 0xff.toByte()
                        rgba[offset + 3] = 0xff.toByte()
                    }
                }
            }
        }

    private fun rgba(bytes: UByteArray, x: Int, y: Int, width: Int): List<Int> {
        val offset = (y * width + x) * 4
        return (0..3).map { channel -> bytes[offset + channel].toInt() }
    }
}

private fun assertRgbaWithinOneLsb(expected: List<Int>, actual: List<Int>) {
    assertTrue(
        expected.zip(actual).all { (expectedChannel, actualChannel) ->
            abs(expectedChannel - actualChannel) <= 1
        },
        "expected RGBA within one LSB of $expected but was $actual",
    )
}
