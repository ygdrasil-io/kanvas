package org.graphiks.kanvas.gpu.renderer.execution

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesCanonicalizationIdentity
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectUniformValue
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext
import org.graphiks.kanvas.gpu.renderer.materials.stubPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlanner
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendSpecializationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUProvisionalRenderSegmentKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification
import org.graphiks.kanvas.gpu.renderer.passes.GPUTargetBlendFacts
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawPayloadRef
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesPayloadResult
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesTopologyIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.PREPARED_VERTICES_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskPhase
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesLayoutAuthority
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode

/**
 * Native public-wgpu4k proof for prepared-vertices pixels.
 *
 * Every smoke drives the full prepared-surface chain: immutable fixture geometry ->
 * [GPUPreparedVerticesUploadArtifact] -> one closed vertices semantic ->
 * [GPUPreparedSurfaceFrameTaskListBuilder] -> [GPUFramePlanner] ->
 * [GPUPreparedSceneFrameSession] native materialization, encoding, submit, and readback.
 * Pixel output is compared against the Task 13 CPU oracle
 * ([GPUPreparedVerticesCpuOracle], mirrored from the kanvas test sources) with the
 * declared tolerance `maxChannelDelta <= 1` (one LSB of f32 UNORM quantization).
 *
 * Capability assumptions are reported for every run (adapter, device, effective limits,
 * target format, index-format support, and skip reason). A skip is never reported as
 * passing native evidence: skipped smokes print their reason and return without
 * assertions.
 */
class GPUWgpu4kPreparedVerticesNativeSmokeTest {

    @Test
    fun `unindexed triangle matches the CPU oracle`() {
        nativeSmoke(
            name = "unindexed-triangle",
            fixture = GPUPreparedVerticesTestFixtures.edgeInclusionTriangle(),
            indexed = false,
        )
    }

    @Test
    fun `indexed triangle matches the CPU oracle`() {
        nativeSmoke(
            name = "indexed-triangle",
            fixture = GPUPreparedVerticesTestFixtures.indexedTriangleSelection(
                intArrayOf(0, 1, 2),
            ),
            indexed = true,
        )
    }

    @Test
    fun `triangle strip matches the CPU oracle`() {
        nativeSmoke(
            name = "strip",
            fixture = GPUPreparedVerticesTestFixtures.stripQuad(),
            indexed = false,
            topology = GPUPreparedVerticesTopologyIdentity.TriangleStrip,
        )
    }

    @Test
    fun `canonicalized fan matches the CPU oracle`() {
        val fixture = GPUPreparedVerticesTestFixtures.fanQuad()
        nativeSmoke(
            name = "fan",
            fixture = fixture.copyFixtureWithIndices(intArrayOf(0, 1, 2, 0, 2, 3)),
            indexed = true,
        )
    }

    @Test
    fun `color interpolation with partial alpha matches the CPU oracle`() {
        // The partial alpha is carried by the premultiplied vertex colours (alpha 128) while
        // the material stays opaque white, so the sRGB attachment round-trips the stored
        // primitive bytes and the declared `maxChannelDelta <= 1` policy applies.
        val fixture = GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
            colorsRgba8 = byteArrayOf(
                128.toByte(), 0, 0, 128.toByte(),
                128.toByte(), 0, 0, 128.toByte(),
                128.toByte(), 0, 0, 128.toByte(),
            ),
            topology = GPUPreparedVerticesTopology.TRIANGLES,
            pixelWidth = 2,
            pixelHeight = 2,
        )
        nativeSmoke(
            name = "color-partial-alpha",
            fixture = fixture,
            indexed = false,
        )
    }

    @Test
    fun `two stop linear gradient v2 matches its CPU shader oracle`() {
        // This deliberately uses intermediate sRGB channels and a non-identity local matrix.
        // It proves the prepared-material v2 gradient ABI rather than the legacy CorePrimitive
        // gradient payload path, and checks the same decode -> premultiply -> interpolate math
        // the WGSL source performs before its sRGB target-store conversion.
        val descriptor = GPUMaterialDescriptor.LinearGradient(
            startX = 0f, startY = 0f, endX = 2f, endY = 0f,
            startR = 0.25f, startG = 0.5f, startB = 0.75f, startA = 1f,
            endR = 0.9f, endG = 0.1f, endB = 0.4f, endA = 1f,
            tileMode = "clamp",
        ).withGradientFacts(
            GPUMaterialDescriptor.GradientFacts(
                localMatrix = listOf(
                    1f, 0f, 0.25f,
                    0f, 1f, 0f,
                    0f, 0f, 1f,
                ),
            ),
        )
        val material = compiledPreparedVerticesMaterial(descriptor)
        assertEquals(576, material.uniformBytes.size)
        assertTrue("gradient.localMatrix0" in material.wgslSource)
        assertTrue("gradient.localMatrix1" in material.wgslSource)
        nativeSmoke(
            name = "two-stop-linear-gradient-v2",
            fixture = GPUPreparedVerticesTestFixtures.edgeInclusionTriangle(),
            indexed = false,
            material = material,
            expected = twoStopLinearGradientCpuOracle(
                GPUPreparedVerticesTestFixtures.edgeInclusionTriangle(),
                descriptor,
            ),
        )
    }

    @Test
    fun `registered MeshProgram uniform material matches the CPU oracle`() {
        // The registered runtime-effect material is bound in group one and evaluated per
        // fragment. An opaque white uniform yields the white material result the oracle
        // models, proving the uniform ABI while keeping the declared comparison policy.
        val material = compiledPreparedVerticesMaterial(
            GPUMaterialDescriptor.RuntimeEffect(
                effectId = "runtime.simple_rt",
                descriptorVersion = 1,
                uniforms = mapOf(
                    "gColor" to GPURuntimeEffectUniformValue.Float4(1f, 1f, 1f, 1f),
                ),
            ),
        )
        nativeSmoke(
            name = "mesh-program-uniforms",
            fixture = GPUPreparedVerticesTestFixtures.barycentricColorTriangle(),
            indexed = false,
            material = material,
        )
    }

    @Test
    fun `registered child slots validate but the registry program lacks WGSL source`() {
        // The registered `runtime.compose_cf` effect declares two exact color-filter child
        // slots; the registered program authority has no WGSL source for it yet, so the
        // typed children material refuses deterministically at compile time with zero native
        // side effects. The registered-child pixel family is therefore not yet an accepted
        // native family; this smoke proves the refusal is exact and reports it as evidence.
        val identityMatrix = listOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val result = GPUPreparedMaterialProgramCompiler.compile(
            descriptor = GPUMaterialDescriptor.RuntimeEffect.withChildDescriptors(
                effectId = "runtime.compose_cf",
                descriptorVersion = 1,
                uniforms = emptyMap(),
                childDescriptors = linkedMapOf(
                    "inner" to org.graphiks.kanvas.gpu.renderer.commands
                        .GPURuntimeEffectChildDescriptor.ColorFilter(
                            org.graphiks.kanvas.gpu.renderer.commands
                                .GPUPreparedColorFilterChildDescriptor.Matrix(identityMatrix),
                        ),
                    "outer" to org.graphiks.kanvas.gpu.renderer.commands
                        .GPURuntimeEffectChildDescriptor.ColorFilter(
                            org.graphiks.kanvas.gpu.renderer.commands
                                .GPUPreparedColorFilterChildDescriptor.Matrix(identityMatrix),
                        ),
                ),
            ),
            paintAlpha = 1f,
            context = GPUMaterialLoweringContext(
                capabilityClass = "webgpu-test",
                targetFormatClass = "rgba8unorm",
                dictionaryVersion = "material-dictionary:prepared-material:v1",
                runtimeEffectResolver =
                    org.graphiks.kanvas.gpu.renderer.runtimeeffects
                        .KanvasPreparedRuntimeEffectResolver(),
            ),
        )
        val refused = assertIs<GPUPreparedMaterialProgramResult.Refused>(result)
        assertEquals("unsupported.material.runtime_effect.wgsl_not_available", refused.code)
        println(
            "task14.native registered-children prepared=false skipped=0 " +
                "refusal=${refused.code} boundary=material-compile",
        )
    }

    @Test
    fun `affine transform and clip match the CPU oracle`() {
        nativeSmoke(
            name = "affine-transform",
            fixture = GPUPreparedVerticesTestFixtures.translatedTriangle(),
            indexed = false,
        )
        nativeSmoke(
            name = "scissor-clip",
            fixture = GPUPreparedVerticesTestFixtures.clippedTriangle(),
            indexed = false,
        )
    }

    @Test
    fun `supported final blend classes match the CPU oracle`() {
        listOf(
            GPUPreparedVerticesBlendMode.SRC_OVER,
            GPUPreparedVerticesBlendMode.SRC,
            GPUPreparedVerticesBlendMode.SRC_IN,
            GPUPreparedVerticesBlendMode.PLUS,
        ).forEach { blendMode ->
            nativeSmoke(
                name = "blend-${blendMode.name}",
                fixture = GPUPreparedVerticesTestFixtures.solidColorTriangle(
                    intArrayOf(128, 64, 32, 160),
                    blendMode,
                    paintAlpha = 1f,
                ),
                indexed = false,
            )
        }
    }

    @Test
    fun `sampled image UV family refuses at the documented materializer boundary`() {
        // The vertices materializer documents that sampled-material resources require a
        // dedicated sampled-material materializer (Task 11 boundary); the image-UV family
        // is refused at native materialization with zero submission side effects. The CPU
        // oracle keeps proving the UV math; this smoke proves the GPU refusal is exact.
        val fixture = GPUPreparedVerticesTestFixtures.texturedTriangle(
            GPUPreparedVerticesFilterMode.NEAREST,
        )
        val material = sampledPreparedVerticesMaterialProgram("smoke:image-uv")
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null, "wgpu4k native adapter unavailable; skipping image-UV refusal smoke")
        reportCapabilities(backend!!, "sampled-image-uv")
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(
                fixture.pixelWidth,
                fixture.pixelHeight,
                colorFormat = GPUColorFormat.RGBA8UnormSrgb,
                colorInterpretation = org.graphiks.kanvas.gpu.renderer.color
                    .GPUColorInterpretation.LinearPremul,
            ),
        )
        try {
            val run = verticesRenderRun(
                fixture,
                material,
                indexed = false,
                GPUPreparedVerticesTopologyIdentity.Triangles,
                backend.deviceGeneration,
            )
            val readbackId = GPUReadbackRequestID("readback.image-uv-refusal")
            val taskList = preparedVerticesTaskList(
                run,
                requireNotNull(backend.capabilities),
                readbackId,
            )
            val terminal = session.renderFrame(
                taskList,
                GPUSceneFrameOutputRequest.ReadbackRgba(
                    GPUReadbackRequestID("readback.image-uv-refusal"),
                ),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(
                org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome.Refused,
                terminal.outcome,
            )
            assertEquals(
                "unsupported.prepared-vertices.sampled-material",
                terminal.diagnostic?.code?.value,
            )
            assertEquals(0, session.nativeCounters().submits)
        } finally {
            runCatching { session.close() }
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    // ---- Harness ----------------------------------------------------------------

    private data class PreparedVerticesRun(
        val semantic: GPUDrawSemanticPayload.Vertices,
        val blendPlan: GPUBlendPlan.FixedFunctionBlend,
        val targetBounds: GPUPixelBounds,
        val deviceGeneration: GPUDeviceGenerationID,
    )

    private fun nativeSmoke(
        name: String,
        fixture: GPUPreparedVerticesTestFixture,
        indexed: Boolean,
        material: GPUPreparedMaterialProgram =
            stubPreparedMaterialProgram(paintAlpha = fixture.paintAlpha),
        topology: GPUPreparedVerticesTopologyIdentity = GPUPreparedVerticesTopologyIdentity.Triangles,
        expected: ByteArray = GPUPreparedVerticesCpuOracle.renderVertices(fixture),
    ) {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null, "wgpu4k native adapter unavailable; skipping $name smoke")
        reportCapabilities(backend!!, name)
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(
                fixture.pixelWidth,
                fixture.pixelHeight,
                colorFormat = GPUColorFormat.RGBA8UnormSrgb,
                colorInterpretation = org.graphiks.kanvas.gpu.renderer.color
                    .GPUColorInterpretation.LinearPremul,
            ),
        )
        try {
            val run = verticesRenderRun(fixture, material, indexed, topology, backend.deviceGeneration)
            val readbackId = GPUReadbackRequestID("readback.vertices.$name")
            val taskList = preparedVerticesTaskList(
                run,
                requireNotNull(backend.capabilities),
                readbackId,
            )
            val rgba = render(session, taskList, name)
            val delta = GPUPreparedVerticesCpuOracle.comparePixels(rgba, expected)
            assertTrue(
                delta.matchesWithinOneLsb,
                "$name maxChannelDelta=${delta.maxChannelDelta} " +
                    "differing=${delta.differingChannels}/${delta.comparedChannels}",
            )
            println(
                "task14.native $name prepared=true skipped=0 submits=" +
                    "${session.nativeCounters().submits} readbacks=" +
                    "${session.nativeCounters().readbackCopies} " +
                    "maxChannelDelta=${delta.maxChannelDelta} differing=" +
                    "${delta.differingChannels}/${delta.comparedChannels}",
            )
            if (name == "two-stop-linear-gradient-v2") {
                println(
                    "task3.native-gradient-v2 oracleRgba=" +
                        expected.joinToString(",") { byte -> (byte.toInt() and 0xff).toString() } +
                        " actualRgba=" +
                        rgba.joinToString(",") { byte -> (byte.toInt() and 0xff).toString() },
                )
            }
        } finally {
            runCatching { session.close() }
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    /** CPU oracle mirrored from the bounded v2 linear-gradient WGSL source. */
    private fun twoStopLinearGradientCpuOracle(
        fixture: GPUPreparedVerticesTestFixture,
        descriptor: GPUMaterialDescriptor.LinearGradient,
    ): ByteArray {
        val coverage = GPUPreparedVerticesCpuOracle.renderVertices(fixture)
        val localMatrix = descriptor.localMatrix
        val start = floatArrayOf(descriptor.startR, descriptor.startG, descriptor.startB, descriptor.startA)
        val end = floatArrayOf(descriptor.endR, descriptor.endG, descriptor.endB, descriptor.endA)
        val axisX = descriptor.endX - descriptor.startX
        val axisY = descriptor.endY - descriptor.startY
        val lengthSquared = axisX * axisX + axisY * axisY
        return coverage.copyOf().also { output ->
            for (y in 0 until fixture.pixelHeight) {
                for (x in 0 until fixture.pixelWidth) {
                    val pixel = (y * fixture.pixelWidth + x) * 4
                    if ((coverage[pixel + 3].toInt() and 0xff) == 0) continue
                    val deviceX = x + 0.5f
                    val deviceY = y + 0.5f
                    val localX = localMatrix[0] * deviceX + localMatrix[1] * deviceY + localMatrix[2]
                    val localY = localMatrix[3] * deviceX + localMatrix[4] * deviceY + localMatrix[5]
                    val projection = (localX - descriptor.startX) * axisX +
                        (localY - descriptor.startY) * axisY
                    val t = if (lengthSquared < 1e-12f) 0f else (projection / lengthSquared).coerceIn(0f, 1f)
                    repeat(3) { channel ->
                        val linear = (1f - t) * srgbToLinear(start[channel]) * start[3] +
                            t * srgbToLinear(end[channel]) * end[3]
                        output[pixel + channel] = (linearToSrgb(linear) * 255f)
                            .roundToInt().coerceIn(0, 255).toByte()
                    }
                    output[pixel + 3] = 255.toByte()
                }
            }
        }
    }

    private fun srgbToLinear(channel: Float): Float =
        if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

    private fun linearToSrgb(channel: Float): Float =
        if (channel <= 0.0031308f) channel * 12.92f else 1.055f * channel.pow(1f / 2.4f) - 0.055f

    private fun render(
        session: GPUPreparedSceneFrameSession,
        taskList: GPUTaskList,
        name: String,
    ): ByteArray {
        val terminal = session.renderFrame(
            taskList,
            GPUSceneFrameOutputRequest.ReadbackRgba(
                GPUReadbackRequestID("readback.vertices.$name"),
            ),
        ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
        assertEquals(
            GPUFrameStructuralOutcome.Succeeded,
            terminal.outcome,
            "$name ${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message}",
        )
        return assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output).bytes
    }

    private fun verticesRenderRun(
        fixture: GPUPreparedVerticesTestFixture,
        material: GPUPreparedMaterialProgram,
        indexed: Boolean,
        topology: GPUPreparedVerticesTopologyIdentity,
        deviceGeneration: GPUDeviceGenerationID,
    ): PreparedVerticesRun {
        val positions = fixture.positionsCopy
        val colors = fixture.colorsRgba8Copy
        val texCoords = fixture.texCoordsCopy
        val indices = fixture.indicesCopy ?: IntArray(positions.size / 2) { it }
        val hasColors = colors != null
        val hasTexCoords = texCoords != null
        val layout = GPUPreparedVerticesLayoutAuthority.layout(hasColors, hasTexCoords)
        val stride = layout.strideBytes
        val vertexCount = positions.size / 2
        val vertexBytes = ByteBuffer.allocate(vertexCount * stride)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                repeat(vertexCount) { vertex ->
                    val positionOffset = vertex * stride
                    putFloat(
                        positionOffset + layout.offsets.getValue("position"),
                        positions[vertex * 2],
                    )
                    putFloat(
                        positionOffset + layout.offsets.getValue("position") + 4,
                        positions[vertex * 2 + 1],
                    )
                    if (hasColors) {
                        val color = colors!!
                        val colorOffset = positionOffset + layout.offsets.getValue("color")
                        (0 until 4).forEach { channel ->
                            put(colorOffset + channel, color[vertex * 4 + channel])
                        }
                    }
                    if (hasTexCoords) {
                        val uv = texCoords!!
                        val uvOffset = positionOffset + layout.offsets.getValue("texcoord")
                        putFloat(uvOffset, uv[vertex * 2])
                        putFloat(uvOffset + 4, uv[vertex * 2 + 1])
                    }
                }
            }.array()
        val indexBytes = if (indexed) {
            ByteBuffer.allocate(indices.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
                indices.forEach { putShort(it.toShort()) }
            }.array()
        } else {
            null
        }
        val artifact = GPUPreparedVerticesUploadArtifact(
            topology = when (topology) {
                GPUPreparedVerticesTopologyIdentity.Triangles -> GPUVertexMode.Triangles
                GPUPreparedVerticesTopologyIdentity.TriangleStrip -> GPUVertexMode.TriangleStrip
            },
            layout = layout,
            vertexBytes = vertexBytes,
            indexBytes = indexBytes,
            vertexCount = vertexCount,
            indexCount = if (indexed) indices.size else null,
            indexFormat = if (indexed) "uint16" else null,
            provenance = "smoke",
            canonicalizationIdentity = GPUPreparedVerticesCanonicalizationIdentity.IdentityV1,
        )
        val transform = fixture.transform
        val transformBytes = listOf(
            transform.scaleX, transform.skewX, transform.transX,
            transform.skewY, transform.scaleY, transform.transY,
            0f, 0f, 1f,
        ).map(Float::toRawBits)
        val targetBounds = GPUPixelBounds(
            0, 0, fixture.pixelWidth, fixture.pixelHeight,
        )
        val scissor = fixture.clip?.let { clip ->
            GPUPixelBounds(clip.left, clip.top, clip.right, clip.bottom)
        } ?: targetBounds
        val blendPlan = blendPlanFor(fixture.blendMode)
        val primitiveBlendPlan = if (hasColors) {
            blendPlanFor(GPUPreparedVerticesBlendMode.SRC_OVER)
        } else {
            null
        }
        val result = GPUPreparedVerticesPayloadGatherer.gather(
            GPUPreparedVerticesPayloadInput(
                payloadRef = GPUDrawPayloadRef(0, PREPARED_VERTICES_RENDER_STEP_IDENTITY),
                artifact = artifact,
                material = material,
                topologyIdentity = topology,
                transformBytes = transformBytes,
                targetBounds = targetBounds,
                scissorBounds = scissor,
                targetFormat = "rgba8unorm-srgb",
                clipIdentity = if (fixture.clip == null) "clip:none" else "clip:scissor",
                clipCoverageIdentity = if (fixture.clip == null) "none" else "scissor",
                primitiveColorPresent = hasColors,
                primitiveBlendIdentity = primitiveBlendPlan?.canonicalIdentity(),
                finalBlendIdentity = blendPlan.canonicalIdentity(),
                capabilitySnapshotHash = "capability:smoke:$deviceGeneration",
                drawProvenance = "smoke",
                frameProvenance = GPUFrameProvenance.GmContent,
            ),
        )
        val semantic = assertIs<GPUPreparedVerticesPayloadResult.Ready>(
            result,
            (result as? GPUPreparedVerticesPayloadResult.Refused)?.facts.toString(),
        ).payload
        return PreparedVerticesRun(semantic, blendPlan, targetBounds, deviceGeneration)
    }

    private fun blendPlanFor(
        mode: GPUPreparedVerticesBlendMode,
        targetFormatClass: String = "rgba8unorm-srgb",
    ): GPUBlendPlan.FixedFunctionBlend {
        val gpuMode = when (mode) {
            GPUPreparedVerticesBlendMode.SRC_OVER -> GPUBlendMode.SRC_OVER
            GPUPreparedVerticesBlendMode.SRC -> GPUBlendMode.SRC
            GPUPreparedVerticesBlendMode.SRC_IN -> GPUBlendMode.SRC_IN
            GPUPreparedVerticesBlendMode.PLUS -> GPUBlendMode.PLUS
        }
        val plan = GPUBlendPlanner().plan(
            GPUBlendSpecializationRequest(
                mode = gpuMode,
                coverage = GPUCoverageConsumption.FullOrScissor,
                sourceAlpha = GPUSourceAlphaClassification.Translucent,
                target = GPUTargetBlendFacts(
                    formatClass = targetFormatClass,
                    clampsNormalizedColorWrites = true,
                    premultipliedAlpha = true,
                ),
                samplePlan = GPUSamplePlan.SingleSampleFrame,
            ),
        )
        return assertIs<GPUBlendPlan.FixedFunctionBlend>(
            plan,
            "smoke blend $mode must specialize to a fixed-function blend: $plan",
        )
    }

    private fun preparedVerticesTaskList(
        run: PreparedVerticesRun,
        capabilities: org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities,
        readbackId: GPUReadbackRequestID,
    ): GPUTaskList {
        val frameId = GPUFrameID(14_001)
        val recordingId = GPURecordingID("recording.vertices.smoke")
        val seal = GPUFrameCapabilitySeal.capture(
            frameId,
            run.deviceGeneration,
            capabilities,
        )
        val target = GPUFrameTargetRef("target.prepared-surface")
        val packet = GPUDrawPacket(
            packetId = GPUDrawPacketID("packet.vertices.0"),
            commandIdValue = 0,
            analysisRecordId = "analysis.vertices.0",
            passId = "pass.vertices.0",
            layerId = "root",
            bindingListId = "bindings.vertices.0",
            insertionReasonCode = "prepared-vertices",
            sortKey = 0L,
            sortKeyPreimage = "paint-order:0",
            renderStepId = org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID(PREPARED_VERTICES_RENDER_STEP_IDENTITY),
            renderStepVersion = 1,
            role = GPUDrawPacketRole.Shading,
            blendPlan = run.blendPlan,
            renderPipelineKey = GPURenderPipelineKey("pending.pipeline.vertices"),
            bindingLayoutHash = "pending.layout.vertices",
            vertexSourceLabel = "prepared-vertices",
            targetStateHash =
                "target.rgba8unorm-srgb.${run.targetBounds.width}x${run.targetBounds.height}",
            originalPaintOrder = 0,
            resourceGeneration = 0L,
            frameProvenance = GPUFrameProvenance.GmContent,
            clipCoveragePlan = GPUClipCoveragePlan.NoClip,
            clipExecutionPlan = GPUClipExecutionPlan.NoClip,
            scissorBoundsHash = "none",
        )
        val base = GPUTaskList(
            frameId = frameId,
            capabilitySeal = seal,
            recordingSeals = listOf(
                GPURecordingSeal(
                    recordingId,
                    0L,
                    "compat:smoke",
                    "replay:smoke",
                    seal.sealHash,
                ),
            ),
            expectedReplayKeyHash = "replay:smoke",
            tasks = listOf(
                GPUTask.Render(
                    taskId = GPUTaskID("task.base.vertices.0"),
                    recordingId = recordingId,
                    phase = GPUTaskPhase.Render,
                    target = target,
                    loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
                    samplePlan = GPUSamplePlan.SingleSampleFrame,
                    provisionalSegmentKey = GPUProvisionalRenderSegmentKey(
                        "segment.vertices.0",
                    ),
                    drawPackets = listOf(packet),
                    batchEligibilityByPacketId = mapOf(
                        packet.packetId to GPUPassBatchEligibility(
                            kind = GPUPassBatchKind.Isolated,
                            queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                        ),
                    ),
                ),
            ),
            dependencies = emptyList(),
            phaseOrder = GPUTaskPhase.entries,
            memoryBudget = GPUFrameMemoryBudgetPlan(
                peakFrameTransientBytes = 0,
                targetResidentBytes = 0,
                categoryTotals = GPUFrameMemoryCategory.entries.associateWith { 0L },
                deviceLimitFacts = emptyList(),
                configuredAggregateBudgetBytes = 1,
                diagnostic = null,
            ),
        )
        val built = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                GPUPreparedSurfaceFrameRequest(
                    baseTaskList = base,
                    capabilities = capabilities,
                    target = target,
                    targetBounds = run.targetBounds,
                    semanticsByCommandId = mapOf(0 to run.semantic),
                    readbackRequestId = readbackId,
                    targetFormat = GPUColorFormat.RGBA8UnormSrgb,
                ),
            ),
            "vertices smoke task-list construction failed",
        )
        return built.taskList
    }

    private fun reportCapabilities(
        backend: GPUBackendSession,
        smoke: String,
    ) {
        val capabilities = backend.capabilities
        val implementation = capabilities?.implementation
        val adapter = backend.adapterInfo?.summary
            ?: implementation?.let { impl ->
                "${impl.adapterName} (${impl.deviceName})"
            }
            ?: "unknown"
        val deviceGeneration = backend.deviceGeneration.value
        val limits = capabilities?.limits
        val effectiveLimits = if (limits == null) {
            "unobserved"
        } else {
            "maxBufferSize=${limits.maxBufferSize} " +
                "minUniformBufferOffsetAlignment=${limits.minUniformBufferOffsetAlignment} " +
                "maxDynamicUniformBuffersPerPipelineLayout=" +
                "${limits.maxDynamicUniformBuffersPerPipelineLayout}"
        }
        val indexFormats = capabilities?.facts.orEmpty()
            .filter { fact -> fact.name.startsWith("vertices.index_") }
            .joinToString(",") { fact -> "${fact.name}=${fact.value}" }
            .ifEmpty { "no index-format capability facts emitted" }
        val targetFormats = capabilities?.textureFormatSampleSupport?.keys
            ?.joinToString(",") { it.name }
            .orEmpty()
        println(
            "task14.capabilities smoke=$smoke adapter=$adapter deviceGeneration=$deviceGeneration " +
                "target=rgba8unorm-srgb targetFormats=[$targetFormats] " +
                "indexFormats=[$indexFormats] limits=[$effectiveLimits]",
        )
    }
}
