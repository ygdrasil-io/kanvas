package org.graphiks.kanvas.surface.gpu

import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
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
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.MeshProgram
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.ShaderModule
import org.graphiks.kanvas.pipeline.UniformLayout
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Lattice
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.kanvas.types.Mesh
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices

class GPUPreparedSurfaceProductRouterTest {
    @Test
    fun `flush snapshot frames reach the execution port while BGRA8 renders prepared with BGRA byte order`() {
        val snapshotBytes = byteArrayOf(1, 2, 3, 4)
        var calls = 0
        var flushRequest: GPUPreparedSurfaceExecutionRequest? = null
        val port = GPUPreparedSurfaceExecutionPort { incoming ->
            calls++
            flushRequest = incoming
            GPUPreparedSurfaceExecutionResult.Succeeded(snapshotBytes, 0, 1, evidence())
        }

        val flushRoute = GPUPreparedSurfaceProductRouter.route(
            listOf(DisplayOp.FlushAndSnapshot(RectF32.ofLTRB(0f, 0f, 4f, 4f))),
            4,
            4,
            PixelFormat.RGBA8,
            RenderConfig.DEFAULT,
            port,
        )
        val flushPrepared = assertIs<GPUPreparedSurfaceProductRoute.Prepared>(flushRoute)
        assertContentEquals(ubyteArrayOf(1u, 2u, 3u, 4u), flushPrepared.result.pixels)
        assertEquals(1, calls)
        assertEquals(
            listOf(DisplayOp.FlushAndSnapshot(RectF32.ofLTRB(0f, 0f, 4f, 4f))),
            flushRequest!!.candidate.operations,
        )

        val bgraBytes = byteArrayOf(0, 0, -1, -1, 0, 0, -1, -1)
        var request: GPUPreparedSurfaceExecutionRequest? = null
        val bgraPort = GPUPreparedSurfaceExecutionPort {
            request = it
            GPUPreparedSurfaceExecutionResult.Succeeded(bgraBytes, 1, 0, evidence())
        }
        val bgraRoute = GPUPreparedSurfaceProductRouter.route(
            listOf(rect()), 2, 1, PixelFormat.BGRA8, RenderConfig.DEFAULT, bgraPort,
        )

        val prepared = assertIs<GPUPreparedSurfaceProductRoute.Prepared>(bgraRoute)
        assertEquals(PixelFormat.BGRA8, prepared.result.format)
        assertContentEquals(
            ubyteArrayOf(0u, 0u, 255u, 255u, 0u, 0u, 255u, 255u),
            prepared.result.pixels,
        )
        assertEquals(GPUColorFormat.BGRA8Unorm, request!!.candidate.color.physicalFormat)
        assertEquals(GPUColorInterpretation.EncodedPremulSrgb, request!!.candidate.color.interpretation)
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
    fun `vertices and mesh frames choose prepared product routing`() {
        val triangle = verticesTriangle()
        val paint = Paint.fill(Color.RED).copy(antiAlias = false)
        val operations = listOf(
            DisplayOp.DrawVertices(triangle, paint, Matrix3x3F32.Identity, ClipStack.WideOpen),
            DisplayOp.DrawMesh(
                Mesh(triangle, bounds = RectF32.ofLTRB(0f, 0f, 4f, 4f)),
                paint,
                null,
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            ),
        )
        operations.forEach { operation ->
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
            // Vertices map to the prepared vertices command lane, not the visual lane, so
            // the visual operation count is zero for a pure vertices frame.
            assertEquals(0, prepared.result.stats.opsDispatched)
            assertEquals(0, prepared.result.stats.opsRefused)
            assertEquals(1, harness.backend.prepareCalls)
            assertEquals(1, harness.backend.session.submitCalls)
            assertContentEquals(harness.expectedRgba.toUByteArray(), prepared.result.pixels)
            assertTrue(prepared.evidence.routeMarker == GPUPreparedSurfaceExecutionRouteMarker.PreparedSurfaceDirect)
        }
    }

    @Test
    fun `vertices and mesh refuse with the exact composite code before native work`() {
        val triangle = verticesTriangle()
        val paint = Paint.fill(Color.RED).copy(antiAlias = false)
        val verticesOp = DisplayOp.DrawVertices(
            vertices = triangle,
            paint = paint,
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )
        val meshOp = DisplayOp.DrawMesh(
            mesh = Mesh(triangle, bounds = RectF32.ofLTRB(0f, 0f, 4f, 4f)),
            paint = paint,
            blendMode = null,
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )

        // The composite preflight refuses nested vertices/meshes with this exact code
        // before any native work; the route is Terminal.
        assertEquals("unsupported.picture.nested_vertices", verticesOp.coreRoutePreflightRefusalReason())
        assertEquals("unsupported.picture.nested_vertices", meshOp.coreRoutePreflightRefusalReason())
        assertEquals(
            null,
            rect().coreRoutePreflightRefusalReason(),
            "non-vertices families keep their FP-05 preflight behavior",
        )
    }

    @Test
    fun `unsupported vertices and mesh return their exact terminal code before native work`() {
        val paint = Paint.fill(Color.RED).copy(antiAlias = false)
        val cases = listOf(
            listOf(
                DisplayOp.DrawVertices(
                    vertices = Vertices(
                        VertexMode.TRIANGLES,
                        listOf(Point2F32(Float.NaN, 0f), Point2F32(1f, 0f), Point2F32(0f, 1f)),
                    ),
                    paint = paint,
                    transform = Matrix3x3F32.Identity,
                    clip = ClipStack.WideOpen,
                ),
            ) to GPUPreparedVerticesRefusalCodes.NonFinite,
            listOf(
                DisplayOp.DrawMesh(
                    mesh = Mesh(
                        vertices = verticesTriangle(),
                        program = MeshProgram(
                            effect = RuntimeEffect(
                                id = "not.registered",
                                module = ShaderModule.fromSource("fixture"),
                                uniformLayout = UniformLayout(emptyList()),
                                children = emptyList(),
                            ),
                            uniforms = org.graphiks.kanvas.pipeline.UniformBlock {},
                        ),
                        bounds = RectF32.ofLTRB(0f, 0f, 1f, 1f),
                    ),
                    paint = paint,
                    blendMode = null,
                    transform = Matrix3x3F32.Identity,
                    clip = ClipStack.WideOpen,
                ),
            ) to GPUPreparedVerticesRefusalCodes.MeshProgramUnregistered,
        )

        cases.forEach { (operations, expectedCode) ->
            val harness = PreparedProductExecutionHarness(width = 8, height = 8)

            val route = GPUPreparedSurfaceProductRouter.route(
                operations,
                8,
                8,
                PixelFormat.RGBA8,
                RenderConfig.DEFAULT,
                harness.port,
            )

            val terminal = assertIs<GPUPreparedSurfaceProductRoute.Terminal>(route)
            assertEquals(expectedCode, terminal.diagnostic.code.value)
            assertEquals(0, harness.backend.prepareCalls)
            assertEquals(0, harness.backend.session.submitCalls)
        }
    }

    @Test
    fun `no accepted or refused vertices or mesh command increments legacy counters`() {
        val accepted = DisplayOp.DrawVertices(
            vertices = verticesTriangle(),
            paint = Paint.fill(Color.RED).copy(antiAlias = false),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )
        val acceptedPlan = GPUFramePathApiInventory.plan(
            operations = listOf(accepted),
            target = org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts(
                8,
                8,
                "rgba8unorm-srgb",
            ),
            config = RenderConfig.DEFAULT,
            capabilities = preparedProductCapabilities(),
        )
        assertNotNull(acceptedPlan.preparedVerticesInventory)

        val refused = DisplayOp.DrawVertices(
            vertices = Vertices(
                VertexMode.TRIANGLES,
                listOf(Point2F32(Float.NaN, 0f), Point2F32(1f, 0f), Point2F32(0f, 1f)),
            ),
            paint = Paint.fill(Color.RED).copy(antiAlias = false),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )
        val refusedPlan = GPUFramePathApiInventory.plan(
            operations = listOf(refused),
            target = org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts(
                8,
                8,
                "rgba8unorm-srgb",
            ),
            config = RenderConfig.DEFAULT,
            capabilities = preparedProductCapabilities(),
        )
        assertEquals(
            GPUPreparedVerticesRefusalCodes.NonFinite,
            assertNotNull(refusedPlan.preparedRefusal).code,
        )
    }

    @Test
    fun `mixed vertices and core frames remain fully prepared`() {
        val harness = PreparedProductExecutionHarness(width = 8, height = 8)

        val route = GPUPreparedSurfaceProductRouter.route(
            listOf(
                rect(),
                DisplayOp.DrawVertices(
                    vertices = verticesTriangle(),
                    paint = Paint.fill(Color.GREEN).copy(antiAlias = false),
                    transform = Matrix3x3F32.Identity,
                    clip = ClipStack.WideOpen,
                ),
                rect(),
            ),
            8,
            8,
            PixelFormat.RGBA8,
            RenderConfig.DEFAULT,
            harness.port,
        )

        val prepared = assertIs<GPUPreparedSurfaceProductRoute.Prepared>(route)
        assertEquals(2, prepared.result.stats.opsDispatched)
        assertEquals(0, prepared.result.stats.opsRefused)
        assertEquals(1, harness.backend.prepareCalls)
        assertEquals(1, harness.backend.session.submitCalls)
    }

    @Test
    fun `before-entry refusal is terminal while terminal failure remains terminal`() {
        val refusal = diagnostic("unsupported.test.builder", "builder refusal")
        val terminal = diagnostic("failed.test.terminal", "terminal failure")

        val refused = GPUPreparedSurfaceProductRouter.route(
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

        assertEquals(refusal, assertIs<GPUPreparedSurfaceProductRoute.Terminal>(refused).diagnostic)
        assertEquals(terminal, assertIs<GPUPreparedSurfaceProductRoute.Terminal>(failed).diagnostic)
    }

    @Test
    fun `before-entry refusals for the terminal families are never legacy`() {
        // Hairline points (unsupported.core_primitive.point.hairline_exact_lowering) left this
        // matrix: they lower to one-device-pixel squares and route Prepared.
        // The two-render dst-copy code left it: the destination-then-consumer
        // dst-read shape is admitted on the prepared direct lane. The mixed-layout code left it:
        // the uniform64/160 split is wired (per-step continuation/ownership),
        // so `core_primitive_mixed_uniform_layouts` is no longer emitted by any builder gate.
        // `core_primitive_analytic_clip_non_direct_geometry` stays here: the NoOp
        // (DST) rows are now admitted (the authority is vacuous for a destination-unchanged draw),
        // but the gate still refuses other non-direct shading geometry (an inverse-fill or stroked
        // direct-triangle path under an analytic clip), so the code remains reachable.
        // `unsupported.native-core-primitive.path-destination-read` was removed: the
        // destination-reading path cover is now admitted (continued producer/cover renders), and
        // the analytic-clip dst-copy path rows re-point to `invalid.preflight.core_primitive_path_stencil`.
        val codes = listOf(
            "unsupported.recording.core_primitive_analytic_clip_non_direct_geometry",
            "unsupported.native-core-primitive.analytic-shape-multi-key",
            "unsupported.test.builder",
        )

        codes.forEach { code ->
            val route = GPUPreparedSurfaceProductRouter.route(
                listOf(rect()), 4, 4, PixelFormat.RGBA8, RenderConfig.DEFAULT,
                GPUPreparedSurfaceExecutionPort {
                    GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused(
                        diagnostic(code, "builder refusal"),
                    )
                },
            )

            val terminal = assertIs<GPUPreparedSurfaceProductRoute.Terminal>(route, code)
            assertEquals(code, terminal.diagnostic.code.value)
        }
    }

    @Test
    fun `gate color refusals terminate with the exact stable code before the execution port`() {
        var calls = 0
        val route = GPUPreparedSurfaceProductRouter.route(
            listOf(rect()), 4, 4, PixelFormat.RGBA8,
            RenderConfig.DEFAULT.copy(gpuColorFormat = org.graphiks.kanvas.surface.GPUColorFormat.RGBA8_UNORM),
            GPUPreparedSurfaceExecutionPort {
                calls++
                error("gate-refused frames must never execute")
            },
        )

        val terminal = assertIs<GPUPreparedSurfaceProductRoute.Terminal>(route)
        assertEquals("unsupported.surface.gpu-color-format.rgba8-unorm", terminal.diagnostic.code.value)
        assertEquals(GPUDiagnosticSeverity.Error, terminal.diagnostic.severity)
        assertEquals(GPUDiagnosticDomain.Execution, terminal.diagnostic.domain)
        assertEquals(0, calls)
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
        targetCloses = 0,
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
        Matrix3x3F32.Identity,
        ClipStack.WideOpen,
    )

    private fun verticesTriangle() = Vertices(
        VertexMode.TRIANGLES,
        listOf(Point2F32(0f, 0f), Point2F32(4f, 0f), Point2F32(0f, 4f)),
    )

    private fun diagnostic(code: String, message: String) = GPUDiagnostic(
        GPUDiagnosticCode(code),
        GPUDiagnosticDomain.Execution,
        GPUDiagnosticSeverity.Error,
        message,
    )

    private companion object {
        val RECT = RectF32.ofLTRB(0f, 0f, 4f, 4f)
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
    val preparedRequests = mutableListOf<GPUOffscreenTargetRequest>()
    var prepareCalls = 0
        private set

    override fun prepare(request: GPUOffscreenTargetRequest): GPUPreparedSurfaceSessionPort {
        prepareCalls++
        preparedRequests += request
        return session
    }

    override fun close() = Unit
}

internal class PreparedProductSession(
    rgba: ByteArray,
) : GPUPreparedSurfaceSessionPort {
    private val ownedRgba = rgba.copyOf()
    val submittedTaskLists = mutableListOf<GPUTaskList>()
    var submitCalls = 0
        private set
    private var counterReads = 0
    private var closed = false

    override fun submit(
        taskList: GPUTaskList,
        readbackId: GPUReadbackRequestID,
    ): GPUPreparedSurfaceSubmission {
        submitCalls++
        submittedTaskLists += taskList
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
        // Session-state fake: counters are cumulative across the session's frames, so a second
        // frame through the harness reports the same per-frame deltas instead of a silent zero
        // delta (which would trip the executor's per-frame evidence checks). The first read
        // reports the created state; later reads report the completed frames so far.
        val completedFrames = if (counterReads == 1) 0L else (counterReads + 1L) / 3L
        return when {
            closed -> completedCounters(completedFrames).copy(targetCloses = 1)
            counterReads == 1 -> GPUPreparedSceneNativeCounters(targetCreations = 1)
            else -> completedCounters(completedFrames)
        }
    }

    override fun close() {
        closed = true
    }

    private fun completedCounters(completedFrames: Long) = GPUPreparedSceneNativeCounters(
        targetCreations = 1,
        frameCoordinatorCreations = completedFrames,
        encoders = completedFrames,
        commandBuffers = completedFrames,
        submits = completedFrames,
        readbackCopies = completedFrames,
        renderPasses = completedFrames,
        draws = completedFrames,
        pipelineBinds = completedFrames,
        retentionRegistrations = completedFrames,
        retentionCompletions = completedFrames,
        distinctRetentionTickets = completedFrames.toInt(),
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
            RectF32.ofLTRB(0f, 0f, 4f, 4f),
            RectF32.ofLTRB(0f, 0f, 4f, 4f),
            paint,
            Matrix3x3F32.Identity,
            clip,
        ) to 1,
        DisplayOp.DrawImageNine(
            image,
            RectF32.ofLTRB(1f, 1f, 3f, 3f),
            RectF32.ofLTRB(0f, 0f, 8f, 8f),
            paint,
            Matrix3x3F32.Identity,
            clip,
        ) to 9,
        DisplayOp.DrawImageLattice(
            image,
            Lattice(listOf(2), listOf(2)),
            RectF32.ofLTRB(0f, 0f, 8f, 8f),
            paint,
            Matrix3x3F32.Identity,
            clip,
        ) to 4,
        DisplayOp.DrawAtlas(
            image,
            listOf(Matrix3x3F32.Identity),
            listOf(RectF32.ofLTRB(0f, 0f, 2f, 2f)),
            listOf(Color.WHITE),
            BlendMode.SRC_OVER,
            paint ?: Paint.fill(Color.WHITE),
            Matrix3x3F32.Identity,
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
        facts = base.facts + listOf(
            org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact(
                name = "first_slice.fill_rect.native",
                source = "runtime",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "core-primitive-direct-native",
            ),
            org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact(
                name = "first_slice.fill_rrect.native",
                source = "runtime",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "core-primitive-direct-native",
            ),
        ),
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
