package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName.SCISSOR_NATIVE
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedAtlasSourceBlend
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometryClass
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class GPUPreparedAtlasLowererTest {
    @Test
    fun `array and color lengths refuse transactionally before any sprite`() {
        val transformMismatch = atlasOperation(
            transforms = listOf(Matrix3x3F32.Identity),
            texRects = listOf(quadrant(0, 0), quadrant(1, 0)),
        )
        val colorMismatch = atlasOperation(
            transforms = listOf(Matrix3x3F32.Identity, Matrix3x3F32.Identity),
            texRects = listOf(quadrant(0, 0), quadrant(1, 0)),
            colors = listOf(Color.RED),
        )

        listOf(transformMismatch, colorMismatch).forEach { operation ->
            val refused = assertIs<GPUPreparedAtlasLowering.Refused>(
                GPUPreparedAtlasLowerer.lower(operation, 7, 13, context()),
            )
            assertEquals(GPUPreparedImageRefusalCodes.ATLAS_ARRAY_LENGTHS, refused.code)
            assertEquals(null, refused.spriteIndex)
        }
    }

    @Test
    fun `every source rect must be finite nonempty and inside the artifact`() {
        val invalidRects = listOf(
            Rect.fromLTRB(Float.NaN, 0f, 1f, 1f),
            Rect.fromLTRB(1f, 1f, 1f, 2f),
            Rect.fromLTRB(-1f, 0f, 1f, 1f),
            Rect.fromLTRB(0f, 0f, 5f, 1f),
        )

        invalidRects.forEach { invalid ->
            val refused = assertIs<GPUPreparedAtlasLowering.Refused>(
                GPUPreparedAtlasLowerer.lower(
                    atlasOperation(
                        transforms = listOf(Matrix3x3F32.Identity, Matrix3x3F32.Identity),
                        texRects = listOf(quadrant(0, 0), invalid),
                        colors = listOf(Color.RED, Color.GREEN),
                    ),
                    0,
                    0,
                    context(),
                ),
            )
            assertEquals(GPUPreparedImageRefusalCodes.ATLAS_GEOMETRY, refused.code)
            assertEquals(1, refused.spriteIndex)
            assertEquals("invalid_source_rect", refused.facts["reason"])
        }
    }

    @Test
    fun `identity translation scale rotation reflection and skew preserve four corners UVs and order`() {
        val spriteTransforms = listOf(
            Matrix3x3F32.Identity,
            Matrix3x3F32.translation(7f, 11f),
            Matrix3x3F32.scaling(2f, 3f),
            Matrix3x3F32.rotation(90f),
            Matrix3x3F32.of(-1f, 0f, 0f, 0f, 1f, 0f),
            Matrix3x3F32.skewing(0.5f, 0.25f),
        )
        val outer = Matrix3x3F32.translation(3f, 5f) * Matrix3x3F32.scaling(2f, 2f)
        val rects = listOf(
            quadrant(0, 0),
            quadrant(1, 0),
            quadrant(0, 1),
            quadrant(1, 1),
            quadrant(0, 0),
            quadrant(1, 0),
        )
        val ready = assertIs<GPUPreparedAtlasLowering.Ready>(
            GPUPreparedAtlasLowerer.lower(
                atlasOperation(
                    transforms = spriteTransforms,
                    texRects = rects,
                    colors = List(spriteTransforms.size) { Color.WHITE },
                    transform = outer,
                ),
                17,
                29,
                context(),
            ),
        )

        assertEquals((17..22).toList(), ready.commands.map { it.normalized.commandId.value })
        assertEquals((29..34).toList(), ready.commands.map { it.normalized.ordering.paintOrder })
        ready.commands.forEachIndexed { index, command ->
            val combined = outer * spriteTransforms[index]
            val rect = rects[index]
            val expectedPositions = listOf(
                Point2F32(rect.left, rect.top),
                Point2F32(rect.right, rect.top),
                Point2F32(rect.right, rect.bottom),
                Point2F32(rect.left, rect.bottom),
            ).map(combined::transform).map { it.x to it.y }
            val geometry = requireNotNull(command.preparedImage).geometry
            assertEquals(expectedPositions, geometry.vertices.map { it.x to it.y }, "positions $index")
            assertEquals(
                listOf(
                    rect.left / 4f to rect.top / 4f,
                    rect.right / 4f to rect.top / 4f,
                    rect.right / 4f to rect.bottom / 4f,
                    rect.left / 4f to rect.bottom / 4f,
                ),
                geometry.vertices.map { it.u to it.v },
                "UVs $index",
            )
            val expectedClass = if (combined.kx == 0f && combined.ky == 0f) {
                GPUPreparedImageGeometryClass.Rect
            } else {
                GPUPreparedImageGeometryClass.Quad
            }
            assertEquals(expectedClass, geometry.geometryClass, "geometry class $index")
        }
    }

    @Test
    fun `skewed sprite remains a quad and is never substituted by its bounding box`() {
        val rect = quadrant(0, 0)
        val transform = Matrix3x3F32.of(1f, 0.75f, 4f, 0.25f, 1f, 6f)
        val ready = assertIs<GPUPreparedAtlasLowering.Ready>(
            GPUPreparedAtlasLowerer.lower(
                atlasOperation(
                    transforms = listOf(transform),
                    texRects = listOf(rect),
                    colors = listOf(Color.WHITE),
                ),
                0,
                0,
                context(),
            ),
        )

        val geometry = requireNotNull(ready.commands.single().preparedImage).geometry
        assertEquals(GPUPreparedImageGeometryClass.Quad, geometry.geometryClass)
        assertEquals(
            listOf(4f to 6f, 6f to 6.5f, 7.5f to 8.5f, 5.5f to 8f),
            geometry.vertices.map { it.x to it.y },
        )
        assertTrue(geometry.vertices[1].x != geometry.vertices[2].x)
        assertTrue(geometry.vertices[0].y != geometry.vertices[1].y)
    }

    @Test
    fun `perspective singular and later invalid transforms refuse the entire atlas`() {
        val perspective = Matrix3x3F32.of(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0.01f, 0f, 1f,
        )
        val singular = Matrix3x3F32.of(1f, 2f, 0f, 2f, 4f, 0f)

        listOf(perspective, singular).forEach { invalid ->
            val refused = assertIs<GPUPreparedAtlasLowering.Refused>(
                GPUPreparedAtlasLowerer.lower(
                    atlasOperation(
                        transforms = listOf(Matrix3x3F32.Identity, invalid),
                        texRects = listOf(quadrant(0, 0), quadrant(1, 0)),
                        colors = listOf(Color.RED, Color.GREEN),
                    ),
                    0,
                    0,
                    context(),
                ),
            )
            assertEquals(GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING, refused.code)
            assertEquals(1, refused.spriteIndex)
        }
    }

    @Test
    fun `sprites share one artifact and sampler choice but keep distinct dynamic uniforms`() {
        val colors = listOf(Color.fromArgb(128, 128, 0, 0), Color.fromArgb(64, 0, 64, 0))
        val ready = assertIs<GPUPreparedAtlasLowering.Ready>(
            GPUPreparedAtlasLowerer.lower(
                atlasOperation(
                    transforms = listOf(Matrix3x3F32.Identity, Matrix3x3F32.translation(8f, 0f)),
                    texRects = listOf(quadrant(0, 0), quadrant(1, 0)),
                    colors = colors,
                    paint = Paint.fill(Color.fromArgb(128, 255, 255, 255)),
                ),
                3,
                9,
                context(),
            ),
        )

        val first = requireNotNull(ready.commands[0].preparedImage)
        val second = requireNotNull(ready.commands[1].preparedImage)
        assertSame(first.artifact, second.artifact)
        assertEquals(first.sampling, second.sampling)
        assertNotSame(first, second)
        assertTrue(first.geometry.vertices != second.geometry.vertices)
        assertTrue(first.atlasColorPremultipliedRgba != second.atlasColorPremultipliedRgba)
        assertEquals(listOf(128f / 255f, 128f / 255f, 128f / 255f, 128f / 255f), first.tintPremultipliedRgba)
        assertEquals(
            listOf((128f / 255f) * (128f / 255f), 0f, 0f, 128f / 255f),
            first.atlasColorPremultipliedRgba,
        )
        assertEquals(
            listOf(0f, (64f / 255f) * (64f / 255f), 0f, 64f / 255f),
            second.atlasColorPremultipliedRgba,
        )
    }

    @Test
    fun `closed source blend set maps exactly and every other BlendMode refuses transactionally`() {
        val accepted = mapOf(
            BlendMode.SRC to GPUPreparedAtlasSourceBlend.Src,
            BlendMode.DST to GPUPreparedAtlasSourceBlend.Dst,
            BlendMode.SRC_OVER to GPUPreparedAtlasSourceBlend.SrcOver,
            BlendMode.PLUS to GPUPreparedAtlasSourceBlend.Plus,
            BlendMode.MODULATE to GPUPreparedAtlasSourceBlend.Modulate,
        )

        accepted.forEach { (mode, expected) ->
            val ready = assertIs<GPUPreparedAtlasLowering.Ready>(
                GPUPreparedAtlasLowerer.lower(
                    atlasOperation(
                        transforms = listOf(Matrix3x3F32.Identity),
                        texRects = listOf(quadrant(0, 0)),
                        colors = listOf(Color.RED),
                        blendMode = mode,
                    ),
                    0,
                    0,
                    context(),
                ),
            )
            assertEquals(expected, ready.commands.single().preparedImage?.atlasSourceBlend)
        }

        (BlendMode.entries - accepted.keys).forEach { mode ->
            val refused = assertIs<GPUPreparedAtlasLowering.Refused>(
                GPUPreparedAtlasLowerer.lower(
                    atlasOperation(
                        transforms = listOf(Matrix3x3F32.Identity, Matrix3x3F32.Identity),
                        texRects = listOf(quadrant(0, 0), quadrant(1, 0)),
                        colors = listOf(Color.RED, Color.GREEN),
                        blendMode = mode,
                    ),
                    0,
                    0,
                    context(),
                ),
            )
            assertEquals(GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND, refused.code, mode.name)
            assertEquals(null, refused.spriteIndex, mode.name)
            assertEquals(mode.name, refused.facts["blendMode"], mode.name)
        }
    }

    @Test
    fun `sprite color paint alpha clip destination blend and order are each applied once`() {
        val clip = ClipStack.DeviceRect(
            rect = Rect.fromLTRB(2f, 3f, 31f, 37f),
            antiAlias = false,
        )
        val paint = Paint.fill(Color.fromArgb(128, 255, 255, 255)).copy(
            blendMode = BlendMode.PLUS,
            blender = Blender.Mode(BlendMode.SRC_OVER),
        )
        val ready = assertIs<GPUPreparedAtlasLowering.Ready>(
            GPUPreparedAtlasLowerer.lower(
                atlasOperation(
                    transforms = listOf(Matrix3x3F32.Identity),
                    texRects = listOf(quadrant(0, 0)),
                    colors = listOf(Color.fromArgb(128, 255, 64, 0)),
                    blendMode = BlendMode.MODULATE,
                    paint = paint,
                    clip = clip,
                ),
                41,
                73,
                context(),
            ),
        )

        val visual = ready.commands.single()
        val command = assertIs<NormalizedDrawCommand.DrawImageRect>(visual.normalized)
        val prepared = requireNotNull(visual.preparedImage)
        assertEquals(41, command.commandId.value)
        assertEquals(73, command.ordering.paintOrder)
        assertEquals(GPUBlendMode.SRC_OVER, command.blend.mode)
        assertEquals(listOf(128f / 255f, 128f / 255f, 128f / 255f, 128f / 255f), prepared.tintPremultipliedRgba)
        assertEquals(
            listOf(
                128f / 255f,
                (64f / 255f) * (128f / 255f),
                0f,
                128f / 255f,
            ),
            prepared.atlasColorPremultipliedRgba,
        )
        assertEquals(GPUPreparedAtlasSourceBlend.Modulate, prepared.atlasSourceBlend)
        assertEquals(command.clip, visual.normalized.clip)
        assertEquals(
            GPUClipCoveragePlan.Scissor(
                org.graphiks.kanvas.gpu.renderer.clips.GPUBounds(2f, 3f, 31f, 37f),
            ),
            visual.clipCoverage,
        )
        assertEquals(
            GPUClipExecutionPlan.ScissorOnly(GPUPixelBounds(2, 3, 31, 37)),
            visual.clipExecutionPlan,
        )
        assertEquals(visual.clipCoverage, command.clip.coveragePlan)
        assertEquals(visual.clipExecutionPlan, command.clip.executionPlan)
    }

    @Test
    fun `atlas device scissor clamps to target and invalid bounds refuse without escaping`() {
        val ready = assertIs<GPUPreparedAtlasLowering.Ready>(
            GPUPreparedAtlasLowerer.lower(
                atlasOperation(
                    transforms = listOf(Matrix3x3F32.Identity),
                    texRects = listOf(quadrant(0, 0)),
                    clip = ClipStack.DeviceRect(
                        Rect.fromLTRB(-4f, 3f, 70f, 37f),
                        antiAlias = false,
                    ),
                ),
                0,
                0,
                context(),
            ),
        )
        val visual = ready.commands.single()
        assertEquals(
            GPUClipCoveragePlan.Scissor(
                org.graphiks.kanvas.gpu.renderer.clips.GPUBounds(0f, 3f, 64f, 37f),
            ),
            visual.clipCoverage,
        )
        assertEquals(
            GPUClipExecutionPlan.ScissorOnly(GPUPixelBounds(0, 3, 64, 37)),
            visual.clipExecutionPlan,
        )

        val invalid = listOf(
            Rect.fromLTRB(16f, 3f, 4f, 37f),
            Rect.fromLTRB(4f, 3f, Float.POSITIVE_INFINITY, 37f),
            Rect.fromLTRB(70f, 3f, 80f, 37f),
        )
        invalid.forEach { rect ->
            val refused = assertIs<GPUPreparedAtlasLowering.Refused>(
                GPUPreparedAtlasLowerer.lower(
                    atlasOperation(
                        transforms = listOf(Matrix3x3F32.Identity),
                        texRects = listOf(quadrant(0, 0)),
                        clip = ClipStack.DeviceRect(rect, antiAlias = false),
                    ),
                    0,
                    0,
                    context(),
                ),
            )
            assertEquals("unsupported.surface.prepared.image-clip", refused.code)
        }
    }

    @Test
    fun `antialiased and complex atlas clips refuse the whole logical operation`() {
        val invalidClips = listOf(
            ClipStack.DeviceRect(
                rect = Rect.fromLTRB(2f, 3f, 31f, 37f),
                antiAlias = true,
            ),
            ClipStack.Complex(emptyList()),
        )

        invalidClips.forEach { clip ->
            val refused = assertIs<GPUPreparedAtlasLowering.Refused>(
                GPUPreparedAtlasLowerer.lower(
                    atlasOperation(
                        transforms = listOf(Matrix3x3F32.Identity, Matrix3x3F32.translation(8f, 0f)),
                        texRects = listOf(quadrant(0, 0), quadrant(1, 0)),
                        colors = listOf(Color.RED, Color.GREEN),
                        clip = clip,
                    ),
                    11,
                    19,
                    context(),
                ),
            )

            assertEquals("unsupported.surface.prepared.image-clip", refused.code)
            assertEquals(null, refused.spriteIndex)
            assertEquals("unsupported_clip_plan", refused.facts["reason"])
        }
    }

    @Test
    fun `arithmetic destination blender and non SrcOver destination mode refuse without a prefix`() {
        val invalidPaints = listOf(
            Paint().copy(blender = Blender.Arithmetic(0f, 1f, 1f, 0f)),
            Paint().copy(blendMode = BlendMode.PLUS),
        )

        invalidPaints.forEach { paint ->
            val refused = assertIs<GPUPreparedAtlasLowering.Refused>(
                GPUPreparedAtlasLowerer.lower(
                    atlasOperation(
                        transforms = listOf(Matrix3x3F32.Identity, Matrix3x3F32.Identity),
                        texRects = listOf(quadrant(0, 0), quadrant(1, 0)),
                        colors = listOf(Color.RED, Color.GREEN),
                        paint = paint,
                    ),
                    0,
                    0,
                    context(),
                ),
            )
            assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, refused.code)
            assertEquals(null, refused.spriteIndex)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unsupportedAtlasPaintEffects")
    fun `atlas paint effects refuse exactly before any command prefix`(
        paintField: String,
        paint: Paint,
    ) {
        val refused = assertIs<GPUPreparedAtlasLowering.Refused>(
            GPUPreparedAtlasLowerer.lower(
                atlasOperation(
                    transforms = listOf(Matrix3x3F32.Identity, Matrix3x3F32.Identity),
                    texRects = listOf(quadrant(0, 0), quadrant(1, 0)),
                    colors = listOf(Color.RED, Color.GREEN),
                    paint = paint,
                ),
                0,
                0,
                context(),
            ),
        )

        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, refused.code)
        assertEquals(null, refused.spriteIndex)
        assertEquals(
            mapOf(
                "reason" to "unsupported_paint_effect",
                "paintField" to paintField,
            ),
            refused.facts,
        )
    }

    private fun atlasOperation(
        transforms: List<Matrix3x3F32>,
        texRects: List<Rect>,
        colors: List<Color>? = null,
        blendMode: BlendMode = BlendMode.SRC_OVER,
        paint: Paint? = null,
        transform: Matrix3x3F32 = Matrix3x3F32.Identity,
        clip: ClipStack = ClipStack.WideOpen,
    ) = DisplayOp.DrawAtlas(
        atlas = atlasImage(),
        transforms = transforms,
        texRects = texRects,
        colors = colors,
        blendMode = blendMode,
        paint = paint,
        transform = transform,
        clip = clip,
    )

    private fun atlasImage() = Image(
        width = GPUPreparedImageTestFixtures.atlas4x4Width,
        height = GPUPreparedImageTestFixtures.atlas4x4Height,
        colorType = GPUPreparedImageTestFixtures.atlas4x4ColorType,
        sourceId = "task-9-atlas",
        pixels = GPUPreparedImageTestFixtures.atlas4x4Bytes,
        alphaType = AlphaType.PREMUL,
    )

    private fun quadrant(x: Int, y: Int): Rect = Rect.fromLTRB(
        x * 2f,
        y * 2f,
        x * 2f + 2f,
        y * 2f + 2f,
    )

    private fun context() = GPUPreparedImageLoweringContext(
        provenance = org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance.GmContent,
        target = GPUTargetFacts(64, 64, "rgba8unorm-srgb"),
        config = RenderConfig.DEFAULT,
        capabilities = GPUCapabilities(
            implementation = GPUImplementationIdentity(
                facadeName = "test",
                implementationName = "fake",
                adapterName = "mock",
                deviceName = "mock-device",
            ),
            facts = listOf(
                GPUCapabilityFact(
                    name = SCISSOR_NATIVE,
                    source = "test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "test:$SCISSOR_NATIVE",
                ),
            ),
            knownUnsupportedFacts = emptyList(),
            snapshotId = "task-9-atlas-lowerer",
            limits = GPULimits(
                maxTextureDimension2D = 8192,
                copyBytesPerRowAlignment = 256,
                minUniformBufferOffsetAlignment = 256,
                maxBufferSize = 1L shl 30,
                maxDynamicUniformBuffersPerPipelineLayout = 1,
            ),
            rendererFeatures = setOf(GPURendererFeature.RenderPass),
        ),
    )

    companion object {
        @JvmStatic
        fun unsupportedAtlasPaintEffects(): Stream<Arguments> = Stream.of(
            Arguments.of(
                "colorFilter",
                Paint(colorFilter = ColorFilter.HighContrast),
            ),
            Arguments.of(
                "maskFilter",
                Paint(maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, sigma = 1f)),
            ),
            Arguments.of(
                "imageFilter",
                Paint(imageFilter = ImageFilter.Blur(1f, 1f)),
            ),
        )
    }
}
