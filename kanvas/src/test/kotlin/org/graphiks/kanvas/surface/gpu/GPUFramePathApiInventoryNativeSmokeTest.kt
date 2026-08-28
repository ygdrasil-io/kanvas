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
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.Path
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
