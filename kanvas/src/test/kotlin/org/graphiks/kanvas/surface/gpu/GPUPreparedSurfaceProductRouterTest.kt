package org.graphiks.kanvas.surface.gpu

import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSceneNativeCounters
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameAttemptID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class GPUPreparedSurfaceProductRouterTest {
    @Test
    fun `non-image gate legacy and BGRA never call the execution port`() {
        var calls = 0
        val port = GPUPreparedSurfaceExecutionPort {
            calls++
            error("must not execute")
        }

        val compositeRoute = GPUPreparedSurfaceProductRouter.route(
            listOf(DisplayOp.BeginLayer(null, null)),
            4,
            4,
            PixelFormat.RGBA8,
            RenderConfig.DEFAULT,
            port,
        )
        val bgraRoute = GPUPreparedSurfaceProductRouter.route(
            listOf(rect()), 4, 4, PixelFormat.BGRA8, RenderConfig.DEFAULT, port,
        )

        assertEquals(
            "legacy.surface.prepared.family.composites",
            assertIs<GPUPreparedSurfaceProductRoute.Legacy>(compositeRoute).code,
        )
        assertEquals("legacy.surface.prepared.pixel-format.bgra8", assertIs<GPUPreparedSurfaceProductRoute.Legacy>(bgraRoute).code)
        assertEquals(0, calls)
    }

    @Test
    fun `every valid image family completes through actual prepared execution`() {
        preparedProductImageOperations().forEach { (operation, expectedVisualCount) ->
            val harness = PreparedProductExecutionHarness(width = 8, height = 8)

            val route = GPUPreparedSurfaceProductRouter.route(
                listOf(operation),
                8,
                8,
                PixelFormat.RGBA8,
                RenderConfig.DEFAULT,
                harness.port,
            )

            val prepared = assertIs<GPUPreparedSurfaceProductRoute.Prepared>(
                route,
                operation::class.simpleName,
            )
            assertEquals(expectedVisualCount, prepared.result.stats.opsDispatched)
            assertContentEquals(harness.expectedRgba.toUByteArray(), prepared.result.pixels)
            assertEquals(1, harness.backend.prepareCalls)
            assertEquals(1, harness.backend.session.submitCalls)
            assertTrue(prepared.evidence.routeMarker == GPUPreparedSurfaceExecutionRouteMarker.PreparedSurfaceDirect)
        }
    }

    @Test
    fun `every invalid image family preserves its exact prepared refusal as terminal`() {
        val invalid = preparedProductImage(
            sourceId = "product-invalid-image",
            pixels = null,
        )

        preparedProductImageOperations(invalid).forEach { (operation, _) ->
            val harness = PreparedProductExecutionHarness(width = 8, height = 8)

            val route = GPUPreparedSurfaceProductRouter.route(
                listOf(operation),
                8,
                8,
                PixelFormat.RGBA8,
                RenderConfig.DEFAULT,
                harness.port,
            )

            val terminal = assertIs<GPUPreparedSurfaceProductRoute.Terminal>(
                route,
                operation::class.simpleName,
            )
            assertEquals(GPUPreparedImageRefusalCodes.PIXELS_MISSING, terminal.diagnostic.code.value)
            assertEquals(0, harness.backend.prepareCalls)
            assertEquals(0, harness.backend.session.submitCalls)
        }
    }

    @Test
    fun `direct image blender refusals are terminal before native preparation`() {
        val cases = listOf(
            Paint(blender = Blender.Arithmetic(0f, 1f, 1f, 0f)) to
                mapOf(
                    "reason" to "unsupported_blender",
                    "blenderKind" to "Arithmetic",
                    "boundary" to "surface",
                    "commandId" to "0",
                    "operationIndex" to "0",
                ),
            Paint(blender = Blender.Mode(BlendMode.MULTIPLY)) to
                mapOf(
                    "sourceId" to "prepared-product-image",
                    "blendMode" to BlendMode.MULTIPLY.name,
                    "supportedBlendMode" to BlendMode.SRC_OVER.name,
                    "boundary" to "surface",
                    "commandId" to "0",
                    "operationIndex" to "0",
                ),
        )

        cases.forEach { (paint, expectedFacts) ->
            val harness = PreparedProductExecutionHarness(width = 8, height = 8)
            val operation = preparedProductImageOperations(paint = paint)
                .single { (candidate, _) -> candidate is DisplayOp.DrawImage }
                .first

            val route = GPUPreparedSurfaceProductRouter.route(
                listOf(operation),
                8,
                8,
                PixelFormat.RGBA8,
                RenderConfig.DEFAULT,
                harness.port,
            )

            val terminal = assertIs<GPUPreparedSurfaceProductRoute.Terminal>(route)
            assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, terminal.diagnostic.code.value)
            assertEquals(expectedFacts, terminal.diagnostic.facts)
            assertEquals(0, harness.backend.prepareCalls)
            assertEquals(0, harness.backend.session.submitCalls)
        }
    }

    @Test
    fun `paint effects across every image family are terminal before native preparation`() {
        val paints = listOf(
            Paint(colorFilter = ColorFilter.HighContrast),
            Paint(maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, sigma = 1f)),
            Paint(imageFilter = ImageFilter.Blur(1f, 1f)),
        )

        paints.forEach { paint ->
            preparedProductImageOperations(paint = paint).forEach { (operation, _) ->
                val harness = PreparedProductExecutionHarness(width = 8, height = 8)

                val route = GPUPreparedSurfaceProductRouter.route(
                    listOf(operation),
                    8,
                    8,
                    PixelFormat.RGBA8,
                    RenderConfig.DEFAULT,
                    harness.port,
                )

                val terminal = assertIs<GPUPreparedSurfaceProductRoute.Terminal>(
                    route,
                    operation::class.simpleName,
                )
                assertEquals(
                    GPUPreparedImageRefusalCodes.NATIVE_BINDING,
                    terminal.diagnostic.code.value,
                )
                assertEquals(0, harness.backend.prepareCalls)
                assertEquals(0, harness.backend.session.submitCalls)
            }
        }
    }

    @Test
    fun `before-entry refusal is legacy while terminal failure remains terminal`() {
        val refusal = diagnostic("unsupported.test.builder", "builder refusal")
        val terminal = diagnostic("failed.test.terminal", "terminal failure")

        val legacy = GPUPreparedSurfaceProductRouter.route(
            listOf(rect()), 4, 4, PixelFormat.RGBA8, RenderConfig.DEFAULT,
            GPUPreparedSurfaceExecutionPort {
                GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused(refusal)
            },
        )
        val failed = GPUPreparedSurfaceProductRouter.route(
            listOf(rect()), 4, 4, PixelFormat.RGBA8, RenderConfig.DEFAULT,
            GPUPreparedSurfaceExecutionPort {
                GPUPreparedSurfaceExecutionResult.TerminalFailure(terminal)
            },
        )

        assertEquals(refusal.code.value, assertIs<GPUPreparedSurfaceProductRoute.Legacy>(legacy).code)
        assertEquals(terminal, assertIs<GPUPreparedSurfaceProductRoute.Terminal>(failed).diagnostic)
    }

    @Test
    fun `success converts exact pixels dimensions stats and evidence`() {
        val source = byteArrayOf(1, 2, 3, 4)
        val evidence = evidence(draws = 7, drawIndexed = 5, pipelineBinds = 3)
        val route = GPUPreparedSurfaceProductRouter.route(
            listOf(rect()), 1, 1, PixelFormat.RGBA8, RenderConfig.DEFAULT,
            GPUPreparedSurfaceExecutionPort {
                GPUPreparedSurfaceExecutionResult.Succeeded(source, 1, 2, evidence)
            },
        )
        source[0] = 99

        val prepared = assertIs<GPUPreparedSurfaceProductRoute.Prepared>(route)
        assertContentEquals(ubyteArrayOf(1u, 2u, 3u, 4u), prepared.result.pixels)
        assertEquals(1, prepared.result.width)
        assertEquals(1, prepared.result.height)
        assertEquals(PixelFormat.RGBA8, prepared.result.format)
        assertEquals(1, prepared.result.stats.opsDispatched)
        assertEquals(0, prepared.result.stats.opsRefused)
        assertEquals(3, prepared.result.stats.pipelineCount)
        assertEquals(12, prepared.result.stats.drawCallCount)
        assertEquals(1f, prepared.result.stats.coverage)
        assertEquals(false, prepared.result.stats.coverageMeasured)
        assertEquals(evidence, prepared.evidence)
    }

    @Test
    fun `native stats overflow is a stable terminal diagnostic`() {
        val route = GPUPreparedSurfaceProductRouter.route(
            listOf(rect()), 1, 1, PixelFormat.RGBA8, RenderConfig.DEFAULT,
            GPUPreparedSurfaceExecutionPort {
                GPUPreparedSurfaceExecutionResult.Succeeded(
                    byteArrayOf(1, 2, 3, 4),
                    1,
                    0,
                    evidence(draws = Long.MAX_VALUE, drawIndexed = 1),
                )
            },
        )

        val diagnostic = assertIs<GPUPreparedSurfaceProductRoute.Terminal>(route).diagnostic
        assertEquals("invalid.surface.prepared.render-stats-overflow", diagnostic.code.value)
        assertEquals("drawCallCount", diagnostic.facts["field"])
    }

    private fun evidence(
        draws: Long = 1,
        drawIndexed: Long = 0,
        pipelineBinds: Long = 1,
    ) = GPUPreparedSurfaceExecutionEvidence(
        targetCreations = 1,
        targetCloses = 1,
        frameCoordinatorCreations = 1,
        encoders = 1,
        commandBuffers = 1,
        submits = 1,
        readbackCopies = 1,
        destinationSnapshotCreations = 0,
        destinationReadbackSnapshots = 0,
        renderPasses = 1,
        draws = draws,
        drawIndexed = drawIndexed,
        pipelineBinds = pipelineBinds,
        activeNativePayloads = 0,
        outputOwnedNativePayloads = 0,
        quarantinedNativePayloads = 0,
        retentionRegistrations = 1,
        retentionCompletions = 1,
        retentionQuarantines = 0,
        distinctRetentionTickets = 1,
        routeMarker = GPUPreparedSurfaceExecutionRouteMarker.PreparedSurfaceDirect,
    )

    private fun rect() = DisplayOp.DrawRect(
        RECT,
        Paint.fill(Color.RED).copy(antiAlias = false),
        Matrix33.identity(),
        ClipStack.WideOpen,
    )

    private fun diagnostic(code: String, message: String) = GPUDiagnostic(
        GPUDiagnosticCode(code),
        GPUDiagnosticDomain.Execution,
        GPUDiagnosticSeverity.Error,
        message,
    )

    private companion object {
        val RECT = Rect.fromLTRB(0f, 0f, 4f, 4f)
    }
}

internal class PreparedProductExecutionHarness(
    width: Int,
    height: Int,
) {
    val expectedRgba = ByteArray(width * height * 4) { index -> (index + 1).toByte() }
    val backend = PreparedProductBackend(expectedRgba)
    val port: GPUPreparedSurfaceExecutionPort = GPUPreparedSurfaceFrameExecutor(
        GPUPreparedSurfaceBackendPortFactory { backend },
    )
}

internal class PreparedProductBackend(
    rgba: ByteArray,
) : GPUPreparedSurfaceBackendPort {
    override val capabilities: GPUCapabilities = preparedProductCapabilities()
    override val deviceGeneration = GPUDeviceGenerationID(101)
    override val runtimeTelemetry = GPUBackendRuntimeTelemetry()
    val session = PreparedProductSession(rgba)
    var prepareCalls = 0
        private set

    override fun prepare(request: GPUOffscreenTargetRequest): GPUPreparedSurfaceSessionPort {
        prepareCalls++
        return session
    }

    override fun close() = Unit
}

internal class PreparedProductSession(
    rgba: ByteArray,
) : GPUPreparedSurfaceSessionPort {
    private val ownedRgba = rgba.copyOf()
    var submitCalls = 0
        private set
    private var counterReads = 0
    private var closed = false

    override fun submit(
        taskList: GPUTaskList,
        readbackId: GPUReadbackRequestID,
    ): GPUPreparedSurfaceSubmission {
        submitCalls++
        val attempt = GPUFrameAttemptID("prepared-product-execution")
        return GPUPreparedSurfaceSubmission(
            attemptId = attempt,
            immediateState = GPUPreparedSurfaceImmediateState.Submitted,
            completion = CompletableFuture.completedFuture(
                GPUPreparedSurfaceCompletion(
                    attemptId = attempt,
                    outcome = GPUFrameStructuralOutcome.Succeeded,
                    diagnostic = null,
                    outputKind = GPUPreparedSurfaceOutputKind.ReadbackRgba,
                    readbackId = readbackId,
                    rgba = ownedRgba,
                ),
            ),
        )
    }

    override fun counters(): GPUPreparedSceneNativeCounters {
        counterReads++
        return when {
            closed -> completedCounters().copy(targetCloses = 1)
            counterReads == 1 -> GPUPreparedSceneNativeCounters(targetCreations = 1)
            else -> completedCounters()
        }
    }

    override fun close() {
        closed = true
    }

    private fun completedCounters() = GPUPreparedSceneNativeCounters(
        targetCreations = 1,
        frameCoordinatorCreations = 1,
        encoders = 1,
        commandBuffers = 1,
        submits = 1,
        readbackCopies = 1,
        renderPasses = 1,
        draws = 1,
        pipelineBinds = 1,
        retentionRegistrations = 1,
        retentionCompletions = 1,
        distinctRetentionTickets = 1,
    )
}

internal fun preparedProductImageOperations(
    image: Image = preparedProductImage(),
    paint: Paint? = null,
): List<Pair<DisplayOp, Int>> {
    val clip = ClipStack.WideOpen
    return listOf(
        DisplayOp.DrawImage(
            image,
            Rect.fromLTRB(0f, 0f, 4f, 4f),
            Rect.fromLTRB(0f, 0f, 4f, 4f),
            paint,
            Matrix33.identity(),
            clip,
        ) to 1,
        DisplayOp.DrawImageNine(
            image,
            Rect.fromLTRB(1f, 1f, 3f, 3f),
            Rect.fromLTRB(0f, 0f, 8f, 8f),
            paint,
            Matrix33.identity(),
            clip,
        ) to 9,
        DisplayOp.DrawImageLattice(
            image,
            Lattice(listOf(2), listOf(2)),
            Rect.fromLTRB(0f, 0f, 8f, 8f),
            paint,
            Matrix33.identity(),
            clip,
        ) to 4,
        DisplayOp.DrawAtlas(
            image,
            listOf(Matrix33.identity()),
            listOf(Rect.fromLTRB(0f, 0f, 2f, 2f)),
            listOf(Color.WHITE),
            BlendMode.SRC_OVER,
            paint ?: Paint.fill(Color.WHITE),
            Matrix33.identity(),
            clip,
        ) to 1,
    )
}

internal fun preparedProductImage(
    sourceId: String = "prepared-product-image",
    pixels: ByteArray? = ByteArray(4 * 4 * 4) { 0xff.toByte() },
) = Image(
    width = 4,
    height = 4,
    colorType = ColorType.RGBA_8888,
    sourceId = sourceId,
    pixels = pixels,
    alphaType = AlphaType.PREMUL,
)

private fun preparedProductCapabilities(): GPUCapabilities {
    val base = GPUProductFlagConfig().buildCapabilities()
    return GPUCapabilities(
        implementation = base.implementation,
        facts = base.facts,
        knownUnsupportedFacts = base.knownUnsupportedFacts,
        snapshotId = "${base.snapshotId}:prepared-product-route",
        limits = GPULimits(
            maxTextureDimension2D = 8192,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        textureFormatSampleSupport = base.textureFormatSampleSupport,
        rendererFeatures = base.rendererFeatures,
        copyAsDrawCapability = base.copyAsDrawCapability,
    )
}
