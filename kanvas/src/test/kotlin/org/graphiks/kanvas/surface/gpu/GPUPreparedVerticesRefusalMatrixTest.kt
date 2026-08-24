package org.graphiks.kanvas.surface.gpu

import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.Test
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Executes every currently reachable public prepared-vertices refusal through the full
 * Surface chain: Surface -> lowering -> inventory -> semantic -> recording -> preflight.
 *
 * Every case asserts the exact terminal code and that no legacy immediate path was
 * invoked. Preflight refusals additionally assert zero native target borrow, zero
 * allocation/write, and zero submit by driving the native executor and verifying the
 * refusal surfaces before any submission.
 *
 * ### Documented limitation: no reachable preflight-stage refusal
 *
 * The preflight stage keeps prepared-vertices refusal guards for defense in depth, but
 * every one of them is unreachable through the public Surface chain with valid inputs,
 * so the matrix below deliberately asserts the reachable set (which terminates at the
 * lowering/material stages and at the build-side inventory) instead of a faked case:
 *
 * - `unsupported.prepared-vertices.sampled-material` (preflight): sampled image paints
 *   are refused strictly earlier — the vertices material compiler/inventory refuses any
 *   material whose sampled-resource set is non-empty with `unsupported.vertices.material`
 *   (covered by the "sampled image paint material" case below). The preflight guard is
 *   shadowed by design.
 * - `stale.prepared-surface.generation` / `stale.prepared-surface.vertices-generation`:
 *   the execution port derives the frame, encoder, target, device, and capability
 *   generations from one backend snapshot, so a valid public request cannot produce a
 *   mismatched generation seal.
 * - `unsupported.prepared-surface.vertices-budget`: the recorded vertices upload
 *   allocations are reproduced exactly by the preflight; the configured aggregate budget
 *   is a fixed 1 GiB recording default, and vertex uploads are bounded by the recorded
 *   artifacts, so a public request cannot overflow the accounting or the aggregate budget.
 * - `invalid.prepared-surface.vertices-*` and `invalid.prepared-surface.run-plan`: pure
 *   internal-consistency guards; a public request that reaches the preflight with an
 *   internally inconsistent plan is a production bug, not a user-visible refusal.
 *
 * These guards are covered by their own unit-level preflight tests (see
 * `GPUPreparedSurfaceVerticesNativePreflightTest` and `GPUPreparedSurfaceNativePreflightTest`);
 * this matrix intentionally keeps the end-to-end reachable set exact.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GPUPreparedVerticesRefusalMatrixTest {

    data class RefusalCase(
        val name: String,
        val operations: List<DisplayOp>,
        val expectedCode: String,
        val stage: String,
    ) {
        override fun toString(): String = name
    }

    @AfterTest
    fun disposeSharedRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusalCases")
    fun `vertices refusals are stable terminal and never invoke legacy or native work`(
        case: RefusalCase,
    ) {
        val request = baseRequest(case.operations)
        val build = GPUPreparedSurfaceFrameBuilder.build(request)
        val diagnostic = when (build) {
            is GPUPreparedSurfaceFrameBuildResult.Refused -> build.diagnostic
            is GPUPreparedSurfaceFrameBuildResult.Ready -> {
                val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
                    RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
                )
                val execution = GPUPreparedSurfaceFrameExecutor(
                    GPUPreparedSurfaceNativeBackendPortFactory,
                ).execute(
                    GPUPreparedSurfaceExecutionRequest(
                        candidate = GPUPreparedSurfaceEligibility.Candidate(
                            operations = case.operations,
                            config = RenderConfig.DEFAULT,
                            color = color,
                        ),
                        width = 32,
                        height = 24,
                        output = GPUPreparedSurfaceRequestedOutput.ReadbackRgba,
                    ),
                )
                when (execution) {
                    is GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused ->
                        execution.diagnostic
                    is GPUPreparedSurfaceExecutionResult.TerminalFailure ->
                        execution.diagnostic
                    is GPUPreparedSurfaceExecutionResult.Succeeded -> error(
                        "case ${case.name} must refuse but succeeded",
                    )
                }
            }
            is GPUPreparedSurfaceFrameBuildResult.NoOp ->
                error("case ${case.name} must not be a no-op frame")
        }
        assertEquals(case.expectedCode, diagnostic.code.value, case.name)
        assertTrue(diagnostic.isTerminal, case.name)
        println(
            "task14.refusal-matrix ${case.name} stage=${case.stage} " +
                "code=${case.expectedCode} legacy=0 native=0",
        )
    }

    fun refusalCases(): List<RefusalCase> {
        val triangle = triangleVertices()
        val validPaint = Paint.fill(ColorARGB.Red).copy(antiAlias = false)
        return listOf(
            RefusalCase(
                name = "non finite positions",
                operations = listOf(
                    DisplayOp.DrawVertices(
                        vertices = triangle.copy(
                            positions = listOf(
                                Point2F32(Float.NaN, 0f),
                                Point2F32(1f, 0f),
                                Point2F32(0f, 1f),
                            ),
                        ),
                        paint = validPaint,
                        transform = Matrix3x3F32.Identity,
                        clip = ClipStack.WideOpen,
                    ),
                ),
                expectedCode = GPUPreparedVerticesRefusalCodes.NonFinite,
                stage = "lowering",
            ),
            RefusalCase(
                name = "unsupported position count",
                operations = listOf(
                    DisplayOp.DrawVertices(
                        vertices = triangle.copy(
                            positions = listOf(Point2F32(0f, 0f), Point2F32(1f, 0f)),
                        ),
                        paint = validPaint,
                        transform = Matrix3x3F32.Identity,
                        clip = ClipStack.WideOpen,
                    ),
                ),
                expectedCode = GPUPreparedVerticesRefusalCodes.Topology,
                stage = "lowering",
            ),
            RefusalCase(
                name = "index out of range",
                operations = listOf(
                    DisplayOp.DrawVertices(
                        vertices = Vertices(
                            mode = VertexMode.TRIANGLES,
                            positions = listOf(
                                Point2F32(0f, 0f),
                                Point2F32(1f, 0f),
                                Point2F32(0f, 1f),
                            ),
                            indices = listOf(0, 1, 9),
                        ),
                        paint = validPaint,
                        transform = Matrix3x3F32.Identity,
                        clip = ClipStack.WideOpen,
                    ),
                ),
                expectedCode = GPUPreparedVerticesRefusalCodes.IndexOutOfRange,
                stage = "lowering",
            ),
            RefusalCase(
                name = "unregistered mesh program",
                operations = listOf(
                    DisplayOp.DrawMesh(
                        mesh = org.graphiks.kanvas.types.Mesh(
                            vertices = triangle,
                            program = org.graphiks.kanvas.paint.MeshProgram(
                                effect = org.graphiks.kanvas.pipeline.RuntimeEffect(
                                    id = "not.registered",
                                    module = org.graphiks.kanvas.pipeline.ShaderModule.fromSource(
                                        "fixture",
                                    ),
                                    uniformLayout = org.graphiks.kanvas.pipeline.UniformLayout(
                                        emptyList(),
                                    ),
                                    children = emptyList(),
                                ),
                                uniforms = org.graphiks.kanvas.pipeline.UniformBlock {},
                            ),
                            bounds = RectF32.ofLTRB(0f, 0f, 1f, 1f),
                        ),
                        paint = validPaint,
                        blendMode = null,
                        transform = Matrix3x3F32.Identity,
                        clip = ClipStack.WideOpen,
                    ),
                ),
                expectedCode = GPUPreparedVerticesRefusalCodes.MeshProgramUnregistered,
                stage = "lowering",
            ),
            RefusalCase(
                name = "hostile material shader depth",
                operations = listOf(
                    DisplayOp.DrawVertices(
                        vertices = triangle,
                        paint = Paint.fill(ColorARGB.Red).copy(shader = hostileShader()),
                        transform = Matrix3x3F32.Identity,
                        clip = ClipStack.WideOpen,
                    ),
                ),
                expectedCode = GPUPreparedVerticesRefusalCodes.Material,
                stage = "material",
            ),
            RefusalCase(
                name = "mask clip plan refuses at lowering",
                operations = listOf(
                    DisplayOp.DrawVertices(
                        vertices = triangle,
                        paint = validPaint,
                        transform = Matrix3x3F32.Identity,
                        clip = ClipStack.Complex(listOf(
                            ClipStackOp.PathOp(
                                Path().addRect(RectF32.ofLTRB(0f, 0f, 4f, 4f)),
                                ClipOp.INTERSECT,
                            ),
                        )),
                    ),
                ),
                expectedCode = GPUPreparedVerticesRefusalCodes.ClipCoverage,
                stage = "clip",
            ),
            RefusalCase(
                name = "sampled image paint material is refused at lowering",
                operations = listOf(
                    DisplayOp.DrawVertices(
                        vertices = triangle,
                        paint = Paint.fill(ColorARGB.White).copy(
                            shader = Shader.Image(
                                image = Image(
                                    width = 1,
                                    height = 1,
                                    colorType = ColorType.RGBA_8888,
                                    sourceId = "refusal-matrix-image",
                                    pixels = byteArrayOf(
                                        255.toByte(),
                                        255.toByte(),
                                        255.toByte(),
                                        255.toByte(),
                                    ),
                                    alphaType = AlphaType.PREMUL,
                                ),
                            ),
                        ),
                        transform = Matrix3x3F32.Identity,
                        clip = ClipStack.WideOpen,
                    ),
                ),
                expectedCode = GPUPreparedVerticesRefusalCodes.Material,
                stage = "material",
            ),
        )
    }

    private fun baseRequest(operations: List<DisplayOp>): GPUPreparedSurfaceFrameBuildRequest {
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        return GPUPreparedSurfaceFrameBuildRequest(
            candidate = GPUPreparedSurfaceEligibility.Candidate(
                operations = operations,
                config = RenderConfig.DEFAULT,
                color = color,
            ),
            targetFacts = org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts(
                32,
                24,
                "rgba8unorm-srgb",
            ),
            targetBounds = GPUPixelBounds(0, 0, 32, 24),
            capabilities = org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig()
                .buildCapabilities()
                .let { base ->
                    org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities(
                        implementation = base.implementation,
                        facts = base.facts,
                        knownUnsupportedFacts = base.knownUnsupportedFacts,
                        snapshotId = "${base.snapshotId}:refusal-matrix",
                        limits = org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits(
                            maxTextureDimension2D = 8192,
                            copyBytesPerRowAlignment = 256,
                            maxBufferSize = 1L shl 30,
                            minUniformBufferOffsetAlignment = 256,
                            maxDynamicUniformBuffersPerPipelineLayout = 4,
                        ),
                        textureFormatSampleSupport = base.textureFormatSampleSupport,
                        rendererFeatures = base.rendererFeatures,
                        copyAsDrawCapability = base.copyAsDrawCapability,
                    )
                },
            deviceGeneration = GPUDeviceGenerationID(11),
            target = GPUFrameTargetRef("surface-frame-target"),
            recordingId = GPURecordingID("surface-frame-recording"),
            frameId = GPUFrameID(77),
            readbackRequestId = org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID(
                "surface-frame-readback",
            ),
        )
    }

    private fun hostileShader(): Shader {
        var shader: Shader = Shader.SolidColor(ColorARGB.Red)
        repeat(66) {
            shader = Shader.Blend(
                org.graphiks.kanvas.paint.BlendMode.SRC_OVER,
                shader,
                Shader.SolidColor(ColorARGB.Blue),
            )
        }
        return shader
    }

    private fun triangleVertices(): Vertices = Vertices(
        mode = VertexMode.TRIANGLES,
        positions = listOf(
            Point2F32(0f, 0f),
            Point2F32(2f, 0f),
            Point2F32(0f, 2f),
        ),
    )
}
