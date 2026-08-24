package org.graphiks.kanvas.surface.gpu

import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutput
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutputRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Color
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.junit.jupiter.api.Assumptions.assumeTrue

class GPUPreparedTextBlurTest {
    @Test
    fun `blur style sigma transform and derived padding alter exact mask identity`() {
        val normal = inventory(blurred(BlurStyle.NORMAL, 0.5f))
        val solid = inventory(blurred(BlurStyle.SOLID, 0.5f))
        val largerSigma = inventory(blurred(BlurStyle.NORMAL, 1f))
        val scaled = inventory(
            blurred(
                BlurStyle.NORMAL,
                0.5f,
                transform = Matrix3x3F32.scaling(2f, 1f),
            ),
        )

        val normalKey = normal.singleMaskKey()
        assertNotEquals(normalKey, solid.singleMaskKey())
        assertNotEquals(normalKey, largerSigma.singleMaskKey())
        assertNotEquals(normalKey, scaled.singleMaskKey())

        val normalPlacement = normal.pages.single().placements.single()
        val largerPlacement = largerSigma.pages.single().placements.single()
        assertTrue(
            largerPlacement.contentRect.right - largerPlacement.contentRect.left >
                normalPlacement.contentRect.right - normalPlacement.contentRect.left,
        )
        assertTrue(
            largerPlacement.contentRect.bottom - largerPlacement.contentRect.top >
                normalPlacement.contentRect.bottom - normalPlacement.contentRect.top,
        )
    }

    @Test
    fun `identical blurred glyph masks deduplicate before atlas packing`() {
        val operation = blurred(BlurStyle.OUTER, 0.75f)
        val ready = preparation(listOf(operation, operation.copy()))

        assertEquals(2, ready.inventory.metrics.instanceCount)
        assertEquals(1, ready.inventory.metrics.uniqueMaskCount)
        assertEquals(
            1,
            ready.inventory.pages.sumOf { page ->
                page.placements.map { placement -> placement.itemKey }.distinct().size
            },
        )
        assertEquals(
            1,
            ready.inventory.maskIdentityByGlyphUse.map { identity ->
                identity.maskKeySha256
            }.distinct().size,
        )
    }

    @Test
    fun `blurred prepared draw bounds preserve the padded mask footprint`() {
        val plain = preparation(listOf(text(Paint.fill(Color.WHITE))))
        val blurred = preparation(listOf(blurred(BlurStyle.NORMAL, 1f)))
        val plainBounds = assertIs<NormalizedDrawCommand.DrawTextRun>(
            plain.mapping.visualCommands.single().normalized,
        ).bounds
        val blurredBounds = assertIs<NormalizedDrawCommand.DrawTextRun>(
            blurred.mapping.visualCommands.single().normalized,
        ).bounds

        assertTrue(blurredBounds.left < plainBounds.left)
        assertTrue(blurredBounds.top < plainBounds.top)
        assertTrue(blurredBounds.right > plainBounds.right)
        assertTrue(blurredBounds.bottom > plainBounds.bottom)
    }

    @Test
    fun `native blurred text matches an independent CPU convolution within one channel`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        if (backend == null) {
            println("$NATIVE_EVIDENCE available=false executed=0 skipped=1")
            assumeTrue(false, "GPU backend unavailable in current environment")
        }
        backend!!
        try {
            val capabilities = requireNotNull(backend.capabilities)
            val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
                RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
            )
            val targetFacts = GPUTargetFacts(
                width = NATIVE_WIDTH,
                height = NATIVE_HEIGHT,
                colorFormat = color.physicalFormat.value,
            )
            val plainOperation = text(Paint.fill(Color.WHITE))
            val plainInventory = assertIs<GPUPreparedTextFramePreparation.Ready>(
                GPUPreparedTextFramePreparer.prepare(
                    operations = listOf(plainOperation),
                    target = targetFacts,
                    config = RenderConfig.DEFAULT,
                    capabilities = capabilities,
                    generation = GPUTextArtifactGeneration(12),
                ),
            ).inventory
            val expected = independentBlurredWhiteOracle(
                inventory = plainInventory,
                sigma = NATIVE_SIGMA,
            )
            val blurredOperation = plainOperation.copy(
                paint = plainOperation.paint.copy(
                    maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, NATIVE_SIGMA),
                ),
            )
            val inventory = GPUFramePathApiInventory.plan(
                operations = listOf(blurredOperation),
                target = targetFacts,
                config = RenderConfig.DEFAULT,
                capabilities = capabilities,
                deviceGeneration = backend.deviceGeneration,
            )
            val requestId = GPUReadbackRequestID("readback.fp05.task12.text-blur")
            val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
                GPUFramePathApiInventory.preparePreparedNativeTaskList(
                    inventory = inventory,
                    capabilities = capabilities,
                    targetBounds = GPUPixelBounds(0, 0, NATIVE_WIDTH, NATIVE_HEIGHT),
                    readbackRequestId = requestId,
                ),
            ).taskList
            val framePlan = GPUFramePlanner.plan(taskList)
            val renderSemantics = framePlan.steps
                .filterIsInstance<GPUFrameStep.RenderPassStep>()
                .flatMap { step -> step.drawPackets }
            assertEquals(1, renderSemantics.size)
            val binding = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                .flatMap { step -> step.preparedTextBindingsByPacketId.values }
                .single()
            val preparedResources = taskList.tasks
                .filterIsInstance<GPUTask.PrepareResources>()
                .flatMap { task -> task.requests }
                .map { request -> request.resource }
                .toSet()
            val requiredResources = buildList {
                add(binding.atlasResourcePlan.stagingRef)
                add(binding.atlasResourcePlan.frameTextureRef)
                add(binding.instanceBufferPlan.bufferRef)
                add(binding.drawUniformBufferPlan.bufferRef)
                binding.materialUniformBufferPlan?.let { add(it.bufferRef) }
            }
            assertTrue(requiredResources.all(preparedResources::contains))

            val session = backend.prepareSceneFrameSession(
                GPUOffscreenTargetRequest(
                    width = NATIVE_WIDTH,
                    height = NATIVE_HEIGHT,
                    colorFormat = color.physicalFormat,
                    colorInterpretation = color.interpretation,
                ),
            )
            try {
                val completed = session.renderFrame(
                    taskList,
                    GPUSceneFrameOutputRequest.ReadbackRgba(requestId),
                ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
                assertEquals(
                    GPUFrameStructuralOutcome.Succeeded,
                    completed.outcome,
                    completed.diagnostic.toString(),
                )
                val actual = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
                val maximumDelta = maximumChannelDelta(expected, actual)
                assertTrue(
                    maximumDelta <= 1,
                    "maxChannelDelta=$maximumDelta",
                )
                val counters = session.nativeCounters()
                assertEquals(1L, counters.encoders)
                assertEquals(1L, counters.commandBuffers)
                assertEquals(1L, counters.submits)
                assertEquals(1L, counters.readbackCopies)
                println(
                    "$NATIVE_EVIDENCE available=true executed=1 skipped=0 " +
                        "maxChannelDelta=$maximumDelta",
                )
            } finally {
                session.close()
            }
        } finally {
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    private fun independentBlurredWhiteOracle(
        inventory: PreparedTextFrameInventory,
        sigma: Float,
    ): ByteArray {
        val page = inventory.pages.single()
        val subRun = inventory.subRunsByOperationIndex.values.single().single()
        val instance = subRun.instances.single()
        val identity = inventory.maskIdentityByGlyphUse.single()
        val placement = page.placements.single { candidate ->
            candidate.itemKey == identity.maskKeySha256
        }
        val sourceWidth = placement.contentRect.right - placement.contentRect.left
        val sourceHeight = placement.contentRect.bottom - placement.contentRect.top
        val source = IntArray(sourceWidth * sourceHeight) { index ->
            val row = index / sourceWidth
            val column = index % sourceWidth
            page.bytes[
                (placement.contentRect.top + row) * page.rowBytes +
                    placement.contentRect.left + column
            ]
        }
        val padding = ceil(3.0 * sigma.toDouble()).toInt()
        val blurredWidth = sourceWidth + padding * 2
        val blurredHeight = sourceHeight + padding * 2
        val weights = DoubleArray(padding * 2 + 1) { index ->
            val distance = (index - padding).toDouble()
            exp(-(distance * distance) / (2.0 * sigma * sigma))
        }
        val normalization = weights.sum()
        val blurred = IntArray(blurredWidth * blurredHeight)
        for (outputY in 0 until blurredHeight) {
            for (outputX in 0 until blurredWidth) {
                var accumulated = 0.0
                for (sourceY in 0 until sourceHeight) {
                    val kernelY = outputY - (sourceY + padding)
                    if (kernelY !in -padding..padding) continue
                    for (sourceX in 0 until sourceWidth) {
                        val kernelX = outputX - (sourceX + padding)
                        if (kernelX !in -padding..padding) continue
                        accumulated += source[sourceY * sourceWidth + sourceX] *
                            weights[kernelX + padding] *
                            weights[kernelY + padding]
                    }
                }
                blurred[outputY * blurredWidth + outputX] =
                    (accumulated / (normalization * normalization))
                        .roundToInt()
                        .coerceIn(0, 255)
            }
        }

        val left = instance.deviceQuad[0].roundToInt() - padding
        val top = instance.deviceQuad[1].roundToInt() - padding
        val expected = ByteArray(NATIVE_WIDTH * NATIVE_HEIGHT * 4)
        for (row in 0 until blurredHeight) {
            for (column in 0 until blurredWidth) {
                val x = left + column
                val y = top + row
                require(x in 0 until NATIVE_WIDTH && y in 0 until NATIVE_HEIGHT)
                val coverage = blurred[row * blurredWidth + column]
                val offset = (y * NATIVE_WIDTH + x) * 4
                val encoded = linearToSrgbByte(coverage / 255f)
                expected[offset] = encoded.toByte()
                expected[offset + 1] = encoded.toByte()
                expected[offset + 2] = encoded.toByte()
                expected[offset + 3] = coverage.toByte()
            }
        }
        return expected
    }

    private fun linearToSrgbByte(linear: Float): Int {
        val encoded = if (linear <= 0.0031308f) {
            linear * 12.92f
        } else {
            (1.055 * linear.toDouble().pow(1.0 / 2.4) - 0.055).toFloat()
        }
        return (encoded * 255f).roundToInt().coerceIn(0, 255)
    }

    private fun maximumChannelDelta(expected: ByteArray, actual: ByteArray): Int {
        assertEquals(expected.size, actual.size)
        return expected.indices.maxOf { index ->
            abs(
                (expected[index].toInt() and 0xff) -
                    (actual[index].toInt() and 0xff),
            )
        }
    }

    private fun inventory(operation: DisplayOp.DrawText): PreparedTextFrameInventory =
        preparation(listOf(operation)).inventory

    private fun preparation(
        operations: List<DisplayOp>,
    ): GPUPreparedTextFramePreparation.Ready = assertIs<GPUPreparedTextFramePreparation.Ready>(
        GPUPreparedTextFramePreparer.prepare(
            operations = operations,
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities(),
            generation = GPUTextArtifactGeneration(12),
        ),
    )

    private fun blurred(
        style: BlurStyle,
        sigma: Float,
        transform: Matrix3x3F32 = Matrix3x3F32.Identity,
    ): DisplayOp.DrawText = text(
        Paint.fill(Color.WHITE).copy(maskFilter = MaskFilter.Blur(style, sigma)),
        transform,
    )

    private fun text(
        paint: Paint,
        transform: Matrix3x3F32 = Matrix3x3F32.Identity,
    ): DisplayOp.DrawText = DisplayOp.DrawText(
        blob = TextBlob(
            glyphRuns = listOf(
                KanvasGlyphRun(
                    glyphs = listOf(36u),
                    positions = listOf(Point2F32(0f, 0f)),
                    fontSize = 16f,
                ),
            ),
            typeface = liberationTypeface(),
            fontSize = 16f,
        ),
        x = 24f,
        y = 40f,
        paint = paint,
        transform = transform,
        clip = ClipStack.WideOpen,
    )

    private fun PreparedTextFrameInventory.singleMaskKey(): String =
        maskIdentityByGlyphUse.single().maskKeySha256

    private companion object {
        const val NATIVE_WIDTH = 64
        const val NATIVE_HEIGHT = 64
        const val NATIVE_SIGMA = 0.75f
        const val NATIVE_EVIDENCE = "fp05.task12.text-blur.native"
    }
}
