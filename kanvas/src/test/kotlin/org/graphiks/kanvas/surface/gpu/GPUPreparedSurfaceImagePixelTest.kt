package org.graphiks.kanvas.surface.gpu

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.kanvas.types.Lattice
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.RectF32

class GPUPreparedSurfaceImagePixelTest {
    @AfterTest
    fun disposeSharedRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    @OptIn(ExperimentalUnsignedTypes::class)
    fun `public drawImage nearest sampling reaches native binding and matches the CPU source oracle`() {
        val image = Image.fromPixels(
            width = 2,
            height = 1,
            pixels = byteArrayOf(
                0, 0, 0, 255.toByte(),
                255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(),
            ),
            sourceId = "public-nearest-sampling",
        )
        val surface = Surface(width = 1, height = 1, format = org.graphiks.kanvas.surface.PixelFormat.RGBA8)
        surface.canvas {
            drawImage(
                image = image,
                dst = RectF32.ofLTRB(0f, 0f, 1f, 1f),
                sampling = SamplingOptions.NEAREST,
            )
        }

        val result = surface.render()
        val cpuNearestOracle = image.pixels!!.copyOfRange(4, 8).unsigned()

        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
        assertPixelExact(result.pixels.toByteArray(), width = 1, x = 0, y = 0, expected = cpuNearestOracle)
    }

    @Test
    fun `public drawImage cubic sampling remains a terminal explicit refusal`() {
        val image = Image.fromPixels(
            width = 1,
            height = 1,
            pixels = byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte()),
            sourceId = "public-cubic-refusal",
        )
        val surface = Surface(width = 1, height = 1)
        surface.canvas {
            drawImage(
                image = image,
                dst = RectF32.ofLTRB(0f, 0f, 1f, 1f),
                sampling = SamplingOptions.Cubic.Mitchell,
            )
        }

        val error = assertFailsWith<GPUPreparedSurfaceTerminalException> { surface.render() }

        assertEquals("unsupported.image.sampling_cubic", error.diagnostic.code.value)
    }

    @Test
    @OptIn(ExperimentalUnsignedTypes::class)
    fun `bounded image shader rect executes nearest alpha tint with a local translation`() {
        val alphaMask = Image.fromPixels(
            width = 1,
            height = 1,
            pixels = byteArrayOf(0x80.toByte()),
            colorType = ColorType.ALPHA_8,
            sourceId = "bounded-nearest-alpha-mask",
        )
        val surface = Surface(width = 1, height = 1, format = org.graphiks.kanvas.surface.PixelFormat.RGBA8)
        surface.canvas {
            drawRect(
                RectF32.ofLTRB(0f, 0f, 1f, 1f),
                Paint(
                    color = ColorARGB.fromRGBA(1f, 0f, 0f, 0.5f),
                    shader = Shader.WithLocalMatrix(
                        Shader.Image(alphaMask, sampling = SamplingOptions.NEAREST),
                        Matrix3x3F32.translation(0.25f, 0f),
                    ),
                    antiAlias = false,
                ),
            )
        }

        val result = surface.render()

        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
        assertPixelExact(result.pixels.toByteArray(), width = 1, x = 0, y = 0, expected = listOf(137, 0, 0, 64))
    }

    @Test
    fun `nearest image families retain the direct prepared route and native pixel contract`() {
        val rgba = fixtureImage(
            "pixel-rgba",
            GPUPreparedImageTestFixtures.rgbaPremul2x2Width,
            GPUPreparedImageTestFixtures.rgbaPremul2x2Height,
            GPUPreparedImageTestFixtures.rgbaPremul2x2ColorType,
            GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes,
        )
        val bgra = fixtureImage(
            "pixel-bgra",
            GPUPreparedImageTestFixtures.bgraOpaque2x2Width,
            GPUPreparedImageTestFixtures.bgraOpaque2x2Height,
            GPUPreparedImageTestFixtures.bgraOpaque2x2ColorType,
            GPUPreparedImageTestFixtures.bgraOpaque2x2Bytes,
        )
        val coverage = fixtureImage(
            "pixel-a8",
            GPUPreparedImageTestFixtures.a8_3x1Width,
            GPUPreparedImageTestFixtures.a8_3x1Height,
            GPUPreparedImageTestFixtures.a8_3x1ColorType,
            GPUPreparedImageTestFixtures.a8_3x1Bytes,
        )
        val grid = fixtureImage(
            "pixel-grid",
            GPUPreparedImageTestFixtures.imageNine6x6Width,
            GPUPreparedImageTestFixtures.imageNine6x6Height,
            GPUPreparedImageTestFixtures.imageNine6x6ColorType,
            GPUPreparedImageTestFixtures.imageNine6x6Bytes,
        )
        val atlas = fixtureImage(
            "pixel-atlas",
            GPUPreparedImageTestFixtures.atlas4x4Width,
            GPUPreparedImageTestFixtures.atlas4x4Height,
            GPUPreparedImageTestFixtures.atlas4x4ColorType,
            GPUPreparedImageTestFixtures.atlas4x4Bytes,
        )
        val operations = listOf(
            DisplayOp.DrawRect(
                rect = RectF32.ofLTRB(60f, 28f, 64f, 32f),
                paint = Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                transform = Matrix3x3F32.Identity,
                clip = ClipStack.WideOpen,
            ),
            drawImage(rgba, RectF32.ofLTRB(0f, 0f, 2f, 2f), SamplingOptions.NEAREST),
            drawImage(bgra, RectF32.ofLTRB(3f, 0f, 5f, 2f), SamplingOptions.NEAREST),
            drawImage(
                coverage,
                RectF32.ofLTRB(6f, 0f, 9f, 1f),
                SamplingOptions.NEAREST,
                Paint.fill(ColorARGB.Red),
            ),
            DisplayOp.DrawImageLattice(
                image = grid,
                lattice = Lattice(
                    xDivs = listOf(2, 4),
                    yDivs = emptyList(),
                ),
                dst = RectF32.ofLTRB(20f, 4f, 38f, 10f),
                paint = Paint.fill(ColorARGB.White).copy(antiAlias = false),
                transform = Matrix3x3F32.Identity,
                clip = ClipStack.WideOpen,
                sampling = SamplingOptions.NEAREST,
            ),
        )
        val capabilities = preparedCapabilities()
        val inventory = GPUFramePathApiInventory.plan(
            operations = operations,
            target = GPUTargetFacts(64, 32, "rgba8unorm-srgb"),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = GPUDeviceGenerationID(9),
        )

        assertEquals(null, inventory.preparedRefusal)

        val buildResult = GPUPreparedSurfaceFrameBuilder.build(
            buildRequest(operations, capabilities),
        )
        val build = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            buildResult,
            buildResult.toString(),
        )
        val semantics = build.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .mapNotNull(GPUDrawPacket::semanticPayload)
            .filterIsInstance<GPUDrawSemanticPayload.SampledImage>()
        val colorSemantic = semantics.first { !it.artifact.alphaOnly }
        val coverageSemantic = semantics.first { it.artifact.alphaOnly }

        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        val execution = GPUPreparedSurfaceFrameExecutor(
            GPUPreparedSurfaceNativeBackendPortFactory,
        ).execute(
            GPUPreparedSurfaceExecutionRequest(
                candidate = GPUPreparedSurfaceEligibility.Candidate(
                    operations = operations,
                    config = RenderConfig.DEFAULT,
                    color = color,
                ),
                width = 64,
                height = 32,
            ),
        )
        val result = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            execution,
            execution.toString(),
        )
        assertEquals(
            "prepared.surface.direct",
            result.evidence.routeMarker.stableLabel,
        )

        assertPixelExact(result.rgba, 64, 0, 0, listOf(188, 0, 0, 128))
        assertPixelExact(result.rgba, 64, 3, 0, listOf(255, 0, 0, 255))
        assertPixelExact(result.rgba, 64, 7, 0, listOf(188, 0, 0, 128))
        assertPixelExact(result.rgba, 64, 21, 5, listOf(255, 255, 255, 255))
        assertPixelExact(result.rgba, 64, 27, 5, listOf(255, 0, 0, 255))
        val colorContractDiagnostic = build.taskList.diagnostics.single {
            it.code.value == "info.recording.prepared_image_color_contract"
        }
        val actualSdrDump = listOf(
            "source.color=${colorSemantic.artifactUploadFormat}",
            "source.coverage=${coverageSemantic.artifactUploadFormat}",
            "source.colorUploadEncoding=${colorSemantic.artifactUploadEncoding}",
            "target=${colorSemantic.pipelineKey.targetFormat}",
            "shaderInterpretation=${colorSemantic.shaderInterpretation}",
            "attachmentSrgbConversion=" +
                colorContractDiagnostic.facts.getValue("image.attachment.srgbConversion"),
        ).joinToString(separator = "\n")
        assertEquals(
            """
            source.color=RGBA8UnormSrgb
            source.coverage=RGBA8Unorm
            source.colorUploadEncoding=StraightEncodedSrgb
            target=RGBA8UnormSrgb
            shaderInterpretation=linear-premul
            attachmentSrgbConversion=true
            """.trimIndent(),
            actualSdrDump,
        )
        assertEquals(1L, result.evidence.submits)
        assertEquals(0, result.evidence.activeNativePayloads)
        assertEquals(0, result.evidence.outputOwnedNativePayloads)
        assertEquals(0, result.evidence.quarantinedNativePayloads)
    }

    private fun buildRequest(
        operations: List<DisplayOp>,
        capabilities: GPUCapabilities,
    ): GPUPreparedSurfaceFrameBuildRequest {
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        return GPUPreparedSurfaceFrameBuildRequest(
            candidate = GPUPreparedSurfaceEligibility.Candidate(
                operations = operations,
                config = RenderConfig.DEFAULT,
                color = color,
            ),
            targetFacts = GPUTargetFacts(64, 32, "rgba8unorm-srgb"),
            targetBounds = GPUPixelBounds(0, 0, 64, 32),
            capabilities = capabilities,
            deviceGeneration = GPUDeviceGenerationID(9),
            target = GPUFrameTargetRef("pixel-evidence-target"),
            recordingId = GPURecordingID("pixel-evidence-recording"),
            frameId = GPUFrameID(9),
            readbackRequestId = GPUReadbackRequestID("pixel-evidence-readback"),
        )
    }

    private fun preparedCapabilities(): GPUCapabilities {
        val base = GPUProductFlagConfig().buildCapabilities()
        return GPUCapabilities(
            implementation = base.implementation,
            facts = base.facts + GPUCapabilityFact(
                name = "first_slice.fill_rect.native",
                source = "test",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "test:first_slice.fill_rect.native",
            ),
            knownUnsupportedFacts = base.knownUnsupportedFacts,
            snapshotId = "${base.snapshotId}:pixel-evidence",
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

    private fun drawImage(
        image: Image,
        dst: RectF32,
        sampling: SamplingOptions,
        paint: Paint = Paint.fill(ColorARGB.White),
    ) = DisplayOp.DrawImage(
        image = image,
        src = RectF32.ofLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat()),
        dst = dst,
        paint = paint.copy(shader = Shader.Image(image, sampling = sampling)),
        transform = Matrix3x3F32.Identity,
        clip = ClipStack.WideOpen,
    )

    private fun fixtureImage(
        sourceId: String,
        width: Int,
        height: Int,
        colorType: ColorType,
        bytes: ByteArray,
    ) = Image(
        width = width,
        height = height,
        colorType = colorType,
        sourceId = sourceId,
        pixels = bytes,
        alphaType = AlphaType.PREMUL,
    )

    private fun quadrant(x: Int, y: Int): RectF32 = RectF32.ofLTRB(
        x * 2f,
        y * 2f,
        x * 2f + 2f,
        y * 2f + 2f,
    )

    private fun assertPixelExact(
        bytes: ByteArray,
        width: Int,
        x: Int,
        y: Int,
        expected: List<Int>,
    ) {
        val offset = (y * width + x) * 4
        assertEquals(
            expected,
            (0..3).map { bytes[offset + it].toInt() and 0xff },
            "nearest pixel ($x,$y)",
        )
    }

    private fun assertPixelWithinOne(
        bytes: ByteArray,
        width: Int,
        x: Int,
        y: Int,
        expected: ByteArray,
    ) {
        val offset = (y * width + x) * 4
        val actual = bytes.copyOfRange(offset, offset + 4)
        assertTrue(
            GPUPreparedImagePixelOracle.maxChannelDelta(actual, expected) <= 1,
            "linear pixel ($x,$y): expected=${expected.unsigned()} actual=${actual.unsigned()}",
        )
    }

    private fun ByteArray.unsigned(): List<Int> = map { it.toInt() and 0xff }
}
