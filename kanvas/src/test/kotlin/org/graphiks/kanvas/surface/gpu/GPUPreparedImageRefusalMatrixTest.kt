package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactFactory
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.ColorSpace
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class GPUPreparedImageRefusalMatrixTest {
    @Test
    fun `artifact authority emits the stable code and boundary for every source row`() {
        val rows = GPUPreparedImageRefusalMatrix.sourceRefusalCases +
            GPUPreparedImageRefusalMatrix.uploadBudgetCase

        rows.forEach { row ->
            val result = if (row === GPUPreparedImageRefusalMatrix.uploadBudgetCase) {
                GPUPreparedImageArtifactFactory.prepare(row.input, maxUploadBytes = 64)
            } else {
                GPUPreparedImageArtifactFactory.prepare(row.input)
            }
            val refused = assertIs<GPUPreparedImageArtifactResult.Refused>(
                result,
                row.name,
            )
            assertEquals(row.expectedCode, refused.code, row.name)
            assertEquals("artifact", refused.facts["boundary"], row.name)
        }
    }

    @Test
    fun `constructible source refusals preserve one code through source surface recording and preflight`() {
        val cases = listOf(
            ConstructibleCase(
                "missing-pixels",
                Image(
                    width = 1,
                    height = 1,
                    colorType = ColorType.RGBA_8888,
                    sourceId = "missing-pixels",
                    pixels = null,
                    alphaType = AlphaType.PREMUL,
                ),
                GPUPreparedImageRefusalCodes.PIXELS_MISSING,
            ),
            ConstructibleCase(
                "unsupported-format",
                Image(
                    width = 1,
                    height = 1,
                    colorType = ColorType.GRAY_8,
                    sourceId = "unsupported-format",
                    pixels = byteArrayOf(127),
                    alphaType = AlphaType.PREMUL,
                ),
                GPUPreparedImageRefusalCodes.PIXEL_FORMAT,
            ),
            ConstructibleCase(
                "unpremultiplied-alpha",
                Image(
                    width = 1,
                    height = 1,
                    colorType = ColorType.RGBA_8888,
                    sourceId = "unpremultiplied-alpha",
                    pixels = byteArrayOf(1, 2, 3, 4),
                    alphaType = AlphaType.UNPREMUL,
                ),
                GPUPreparedImageRefusalCodes.ALPHA_INTERPRETATION,
            ),
            ConstructibleCase(
                "unknown-alpha",
                Image(
                    width = 1,
                    height = 1,
                    colorType = ColorType.RGBA_8888,
                    sourceId = "unknown-alpha",
                    pixels = byteArrayOf(1, 2, 3, 4),
                    alphaType = AlphaType.UNKNOWN,
                ),
                GPUPreparedImageRefusalCodes.ALPHA_INTERPRETATION,
            ),
            ConstructibleCase(
                "display-p3",
                Image(
                    width = 1,
                    height = 1,
                    colorType = ColorType.RGBA_8888,
                    sourceId = "display-p3",
                    pixels = byteArrayOf(1, 2, 3, 4),
                    colorSpace = ColorSpace.DISPLAY_P3,
                    alphaType = AlphaType.PREMUL,
                ),
                GPUPreparedImageRefusalCodes.GAMUT_TRANSFORM,
            ),
            ConstructibleCase(
                "short-pixels",
                Image(
                    width = 2,
                    height = 1,
                    colorType = ColorType.RGBA_8888,
                    sourceId = "short-pixels",
                    pixels = byteArrayOf(1, 2, 3, 4),
                    alphaType = AlphaType.PREMUL,
                ),
                GPUPreparedImageRefusalCodes.PIXEL_LENGTH,
            ),
            ConstructibleCase(
                "opaque-alpha-mismatch",
                Image(
                    width = 1,
                    height = 1,
                    colorType = ColorType.RGBA_8888,
                    sourceId = "opaque-alpha-mismatch",
                    pixels = byteArrayOf(1, 2, 3, 4),
                    alphaType = AlphaType.OPAQUE,
                ),
                GPUPreparedImageRefusalCodes.ALPHA_INTERPRETATION,
            ),
        )

        cases.forEach { row ->
            val operation = drawImage(row.image)
            val capabilities = capabilities()
            val source = assertIs<GPUPreparedImageArtifactResult.Refused>(
                GPUPreparedSurfaceImageSource.prepare(row.image),
                row.name,
            )
            val inventory = GPUFramePathApiInventory.plan(
                operations = listOf(operation),
                target = TARGET,
                config = RenderConfig.DEFAULT,
                capabilities = capabilities,
            )
            val recording = assertIs<GPUPreparedSurfaceFrameResult.Refused>(
                GPUFramePathApiInventory.preparePreparedNativeTaskList(
                    inventory = inventory,
                    capabilities = capabilities,
                    targetBounds = BOUNDS,
                ),
                row.name,
            )
            val surface = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(
                GPUPreparedSurfaceFrameBuilder.build(buildRequest(listOf(operation), capabilities)),
                row.name,
            )
            val backend = RefusalBackend(capabilities)
            val execution = assertIs<GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused>(
                GPUPreparedSurfaceFrameExecutor(
                    backendFactory = GPUPreparedSurfaceBackendPortFactory { backend },
                ).execute(executionRequest(listOf(operation))),
                row.name,
            )

            assertEquals(row.code, source.code, "${row.name}:source")
            assertEquals(row.code, inventory.preparedRefusal?.code, "${row.name}:recording")
            assertEquals(row.code, recording.diagnostic.code.value, "${row.name}:preflight")
            assertEquals(row.code, surface.diagnostic.code.value, "${row.name}:surface")
            assertEquals(row.code, execution.diagnostic.code.value, "${row.name}:executor")
            assertEquals("artifact", source.facts["boundary"], "${row.name}:source facts")
            assertEquals("inventory", recording.diagnostic.facts["boundary"], "${row.name}:preflight facts")
            assertEquals("surface", surface.diagnostic.facts["boundary"], "${row.name}:surface facts")
            assertEquals(0, inventory.legacyDump.invocationCount, "${row.name}:fallback")
            assertEquals(0, backend.prepareCalls, "${row.name}:native handles")
        }

        val zeroWidth = Image(
            width = 0,
            height = 1,
            colorType = ColorType.RGBA_8888,
            sourceId = "zero-width",
            pixels = byteArrayOf(),
            alphaType = AlphaType.PREMUL,
        )
        val zeroWidthSource = assertIs<GPUPreparedImageArtifactResult.Refused>(
            GPUPreparedSurfaceImageSource.prepare(zeroWidth),
            "zero-width: strongest constructible production envelope",
        )
        assertEquals(GPUPreparedImageRefusalCodes.DIMENSIONS, zeroWidthSource.code)
        assertEquals("artifact", zeroWidthSource.facts["boundary"])
    }

    @Test
    fun `constructible sampling and atlas table rows refuse canonically without fallback`() {
        val image = fixtureImage("refusal-sampling")
        val rejected = buildList {
            add(
                RuntimeRefusalCase(
                    "cubic",
                    drawImage(
                        image,
                        Shader.Image(image, sampling = SamplingOptions.Cubic.Mitchell),
                    ),
                    GPUPreparedImageRefusalCodes.SAMPLING_CUBIC,
                ),
            )
            add(
                RuntimeRefusalCase(
                    "repeat-x",
                    drawImage(
                        image,
                        Shader.Image(
                            image,
                            tileModeX = TileMode.REPEAT,
                            sampling = SamplingOptions.NEAREST,
                        ),
                    ),
                    GPUPreparedImageRefusalCodes.TILE_MODE,
                ),
            )
            add(
                RuntimeRefusalCase(
                    "perspective",
                    drawImage(image).copy(
                        transform = Matrix33.makeAll(
                            1f, 0f, 0f,
                            0f, 1f, 0f,
                            0.1f, 0f, 1f,
                        ),
                    ),
                    GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING,
                ),
            )
            add(
                RuntimeRefusalCase(
                    "atlas-array-lengths",
                    atlas(image).copy(texRects = emptyList()),
                    GPUPreparedImageRefusalCodes.ATLAS_ARRAY_LENGTHS,
                ),
            )
            add(
                RuntimeRefusalCase(
                    "atlas-geometry",
                    atlas(image).copy(texRects = listOf(Rect.fromLTRB(-1f, 0f, 1f, 1f))),
                    GPUPreparedImageRefusalCodes.ATLAS_GEOMETRY,
                ),
            )
            add(
                RuntimeRefusalCase(
                    "nine-geometry",
                    DisplayOp.DrawImageNine(
                        image = image,
                        center = Rect.fromLTRB(3f, 0f, 2f, 2f),
                        dst = Rect.fromLTRB(0f, 0f, 8f, 8f),
                        paint = null,
                        transform = Matrix33.identity(),
                        clip = ClipStack.WideOpen,
                    ),
                    GPUPreparedImageRefusalCodes.NINE_GEOMETRY,
                ),
            )
            add(
                RuntimeRefusalCase(
                    "lattice-geometry",
                    DisplayOp.DrawImageLattice(
                        image = image,
                        lattice = Lattice(
                            xDivs = listOf(image.width + 1),
                            yDivs = emptyList(),
                        ),
                        dst = Rect.fromLTRB(0f, 0f, 8f, 8f),
                        paint = null,
                        transform = Matrix33.identity(),
                        clip = ClipStack.WideOpen,
                    ),
                    GPUPreparedImageRefusalCodes.LATTICE_GEOMETRY,
                ),
            )
            add(
                RuntimeRefusalCase(
                    "native-binding",
                    atlas(image).copy(
                        paint = Paint.fill(Color.WHITE).copy(
                            blender = Blender.Arithmetic(0f, 1f, 1f, 0f),
                        ),
                    ),
                    GPUPreparedImageRefusalCodes.NATIVE_BINDING,
                ),
            )
            GPUPreparedImageRefusalMatrix.atlasBlendCases
                .filterNot(AtlasBlendCase::accepted)
                .forEach { blend ->
                    add(
                        RuntimeRefusalCase(
                            "atlas-${blend.blendMode}",
                            atlas(image).copy(blendMode = blend.blendMode),
                            requireNotNull(blend.refusalCode),
                        ),
                    )
                }
        }

        rejected.forEach { row ->
            val capabilities = capabilities()
            val inventory = GPUFramePathApiInventory.plan(
                operations = listOf(row.operation),
                target = TARGET,
                config = RenderConfig.DEFAULT,
                capabilities = capabilities,
            )
            val preflight = assertIs<GPUPreparedSurfaceFrameResult.Refused>(
                GPUFramePathApiInventory.preparePreparedNativeTaskList(
                    inventory = inventory,
                    capabilities = capabilities,
                    targetBounds = BOUNDS,
                ),
                row.name,
            )
            val surface = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(
                GPUPreparedSurfaceFrameBuilder.build(
                    buildRequest(listOf(row.operation), capabilities),
                ),
                row.name,
            )
            val backend = RefusalBackend(capabilities)
            val execution = assertIs<GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused>(
                GPUPreparedSurfaceFrameExecutor(
                    backendFactory = GPUPreparedSurfaceBackendPortFactory { backend },
                ).execute(executionRequest(listOf(row.operation))),
                row.name,
            )

            assertEquals(row.code, inventory.preparedRefusal?.code, "${row.name}:recording")
            assertTrue(inventory.visualCommands.isEmpty(), "${row.name}:transaction")
            assertEquals(row.code, preflight.diagnostic.code.value, "${row.name}:preflight")
            assertEquals(row.code, surface.diagnostic.code.value, "${row.name}:surface")
            assertEquals(row.code, execution.diagnostic.code.value, "${row.name}:executor")
            assertEquals(0, inventory.legacyDump.invocationCount, "${row.name}:fallback")
            assertEquals(0, backend.prepareCalls, "${row.name}:native session")
        }
    }

    private fun executionRequest(
        operations: List<DisplayOp>,
    ): GPUPreparedSurfaceExecutionRequest {
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        return GPUPreparedSurfaceExecutionRequest(
            candidate = GPUPreparedSurfaceEligibility.Candidate(
                operations = operations,
                config = RenderConfig.DEFAULT,
                color = color,
            ),
            width = 16,
            height = 16,
        )
    }

    private fun buildRequest(
        operations: List<DisplayOp>,
        capabilities: GPUCapabilities,
    ): GPUPreparedSurfaceFrameBuildRequest =
        GPUPreparedSurfaceFrameBuildRequest(
            candidate = executionRequest(operations).candidate,
            targetFacts = TARGET,
            targetBounds = BOUNDS,
            capabilities = capabilities,
            deviceGeneration = GPUDeviceGenerationID(17),
            target = GPUFrameTargetRef("refusal-target"),
            recordingId = GPURecordingID("refusal-recording"),
            frameId = GPUFrameID(17),
            readbackRequestId = GPUReadbackRequestID("refusal-readback"),
        )

    private fun drawImage(
        image: Image,
        shader: Shader.Image = Shader.Image(image, sampling = SamplingOptions.NEAREST),
    ) = DisplayOp.DrawImage(
        image = image,
        src = Rect.fromLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat()),
        dst = Rect.fromLTRB(0f, 0f, 4f, 4f),
        paint = Paint.fill(Color.WHITE).copy(shader = shader),
        transform = Matrix33.identity(),
        clip = ClipStack.WideOpen,
    )

    private fun atlas(image: Image) = DisplayOp.DrawAtlas(
        atlas = image,
        transforms = listOf(Matrix33.identity()),
        texRects = listOf(Rect.fromLTRB(0f, 0f, 2f, 2f)),
        colors = listOf(Color.WHITE),
        blendMode = BlendMode.SRC_OVER,
        paint = Paint.fill(Color.WHITE),
        transform = Matrix33.identity(),
        clip = ClipStack.WideOpen,
    )

    private fun fixtureImage(sourceId: String) = Image(
        width = GPUPreparedImageTestFixtures.atlas4x4Width,
        height = GPUPreparedImageTestFixtures.atlas4x4Height,
        colorType = GPUPreparedImageTestFixtures.atlas4x4ColorType,
        sourceId = sourceId,
        pixels = GPUPreparedImageTestFixtures.atlas4x4Bytes,
        alphaType = AlphaType.PREMUL,
    )

    private fun capabilities(): GPUCapabilities {
        val base = GPUProductFlagConfig().buildCapabilities()
        return GPUCapabilities(
            implementation = base.implementation,
            facts = base.facts,
            knownUnsupportedFacts = base.knownUnsupportedFacts,
            snapshotId = "${base.snapshotId}:refusal-matrix",
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

    private data class ConstructibleCase(
        val name: String,
        val image: Image,
        val code: String,
    )

    private data class RuntimeRefusalCase(
        val name: String,
        val operation: DisplayOp,
        val code: String,
    )

    private class RefusalBackend(
        override val capabilities: GPUCapabilities,
    ) : GPUPreparedSurfaceBackendPort {
        override val deviceGeneration = GPUDeviceGenerationID(17)
        override val runtimeTelemetry = GPUBackendRuntimeTelemetry()
        var prepareCalls = 0

        override fun prepare(request: GPUOffscreenTargetRequest): GPUPreparedSurfaceSessionPort {
            prepareCalls++
            error("refusal must happen before preparing a native session")
        }

        override fun close() = Unit
    }

    private companion object {
        val TARGET = GPUTargetFacts(16, 16, "rgba8unorm-srgb")
        val BOUNDS = GPUPixelBounds(0, 0, 16, 16)
    }
}
