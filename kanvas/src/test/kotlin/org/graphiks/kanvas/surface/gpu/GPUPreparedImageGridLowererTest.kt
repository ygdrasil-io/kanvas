package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometryClass
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.LatticeFlags
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.RectF32

class GPUPreparedImageGridLowererTest {
    @Test
    fun `nine emits row ordered affine cells with one default linear artifact authority`() {
        val image = imageNine()
        val transform = Matrix3x3F32.of(
            1f, 0.5f, 3f,
            0.25f, 1f, 4f,
        )
        val operation = DisplayOp.DrawImageNine(
            image = image,
            center = RectF32.ofLTRB(2f, 2f, 4f, 4f),
            dst = RectF32.ofLTRB(10f, 20f, 28f, 38f),
            paint = null,
            transform = transform,
            clip = ClipStack.WideOpen,
        )

        val ready = assertIs<GPUPreparedImageGridLowering.Ready>(
            GPUPreparedImageGridLowerer.lowerNine(operation, 7, 19, context()),
        )

        assertEquals(9, ready.commands.size)
        assertEquals((7..15).toList(), ready.commands.map { it.normalized.commandId.value })
        assertEquals((19..27).toList(), ready.commands.map { it.normalized.ordering.paintOrder })
        assertEquals(
            listOf(
                rect(0f, 0f, 2f, 2f), rect(2f, 0f, 4f, 2f), rect(4f, 0f, 6f, 2f),
                rect(0f, 2f, 2f, 4f), rect(2f, 2f, 4f, 4f), rect(4f, 2f, 6f, 4f),
                rect(0f, 4f, 2f, 6f), rect(2f, 4f, 4f, 6f), rect(4f, 4f, 6f, 6f),
            ),
            ready.commands.map {
                val command = assertIs<NormalizedDrawCommand.DrawImageRect>(it.normalized)
                RectF32.ofLTRB(command.src.left, command.src.top, command.src.right, command.src.bottom)
            },
        )
        val facts = ready.commands.map { requireNotNull(it.preparedImage) }
        assertTrue(facts.all { it.sampling == GPUPreparedImageSampling.Linear })
        assertTrue(facts.all { it.geometry.geometryClass == GPUPreparedImageGeometryClass.Quad })
        facts.drop(1).forEach { assertSame(facts.first().artifact, it.artifact) }
        assertEquals(
            listOf(23f to 26.5f, 25f to 27f, 26f to 29f, 24f to 28.5f),
            facts.first().geometry.vertices.map { it.x to it.y },
        )
    }

    @Test
    fun `nine refuses overlapping raw destination cells before filtered decomposition`() {
        val operation = DisplayOp.DrawImageNine(
            image = imageNine(),
            center = RectF32.ofLTRB(2f, 2f, 4f, 4f),
            dst = RectF32.ofLTRB(0f, 0f, 2f, 2f),
            paint = null,
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )

        val refused = assertIs<GPUPreparedImageGridLowering.Refused>(
            GPUPreparedImageGridLowerer.lowerNine(operation, 7, 19, context()),
        )

        assertEquals(GPUPreparedImageRefusalCodes.NINE_GEOMETRY, refused.code)
        assertEquals(-1, refused.operationIndex)
        assertEquals("overlapping_destination_cells", refused.facts["reason"])
    }

    @Test
    fun `sampled grid commands retain no duplicate upload byte payloads`() {
        val image = Image(
            width = 64,
            height = 64,
            sourceId = "prepared-grid-payload-64x64",
            pixels = ByteArray(64 * 64 * 4) { index -> (index % 251).toByte() },
            alphaType = AlphaType.PREMUL,
        )
        val divisions = (8 until 64 step 8).toList()
        val operation = DisplayOp.DrawImageLattice(
            image = image,
            lattice = Lattice(xDivs = divisions, yDivs = divisions),
            dst = RectF32.ofLTRB(0f, 0f, 64f, 64f),
            paint = null,
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
            sampling = SamplingOptions.NEAREST,
        )

        val ready = assertIs<GPUPreparedImageGridLowering.Ready>(
            GPUPreparedImageGridLowerer.lowerLattice(operation, 0, 0, context()),
        )

        assertEquals(64, ready.commands.size)
        val prepared = ready.commands.map { requireNotNull(it.preparedImage) }
        prepared.drop(1).forEach { assertSame(prepared.first().artifact, it.artifact) }
        val retainedPayloads = ready.commands.map { command ->
            assertIs<GPUMaterialDescriptor.ImageDraw>(command.normalized.material).rgbaPixels
        }
        assertTrue(retainedPayloads.all(ByteArray::isEmpty))
        assertEquals(0, retainedPayloads.sumOf(ByteArray::size))
    }

    @Test
    fun `mixed lattice honors nearest and lowers sampled fixed and transparent cells exactly once`() {
        val image = imageNine()
        val operation = DisplayOp.DrawImageLattice(
            image = image,
            lattice = Lattice(
                xDivs = listOf(2, 4),
                yDivs = emptyList(),
                colors = listOf(Color.TRANSPARENT, Color.fromArgb(128, 255, 0, 0), Color.TRANSPARENT),
                flags = listOf(
                    LatticeFlags.DEFAULT,
                    LatticeFlags.FIXED_COLOR,
                    LatticeFlags.TRANSPARENT,
                ),
            ),
            dst = RectF32.ofLTRB(10f, 12f, 34f, 18f),
            paint = Paint.fill(Color.fromArgb(128, 20, 30, 40)).copy(
                antiAlias = false,
            ),
            transform = Matrix3x3F32.translation(2f, 3f),
            clip = ClipStack.WideOpen,
            sampling = SamplingOptions.NEAREST,
        )

        val ready = assertIs<GPUPreparedImageGridLowering.Ready>(
            GPUPreparedImageGridLowerer.lowerLattice(operation, 4, 9, context()),
        )

        assertEquals(2, ready.commands.size)
        assertEquals(listOf(4, 5), ready.commands.map { it.normalized.commandId.value })
        assertEquals(listOf(9, 10), ready.commands.map { it.normalized.ordering.paintOrder })

        val sampled = ready.commands[0]
        assertIs<NormalizedDrawCommand.DrawImageRect>(sampled.normalized)
        assertEquals(GPUPreparedImageSampling.Nearest, sampled.preparedImage?.sampling)

        val fixed = ready.commands[1]
        val fixedCommand = assertIs<NormalizedDrawCommand.FillRect>(fixed.normalized)
        val material = assertIs<GPUMaterialDescriptor.SolidColor>(fixedCommand.material)
        assertEquals(1f, material.r)
        assertEquals(0f, material.g)
        assertEquals(0f, material.b)
        assertEquals(64f / 255f, material.a)
        assertEquals(GPUBlendMode.SRC_OVER, fixedCommand.blend.mode)
        assertNull(fixed.preparedImage)
    }

    @Test
    fun `fixed color only lattice creates no image command or prepared binding`() {
        val operation = DisplayOp.DrawImageLattice(
            image = imageNine(),
            lattice = Lattice(
                xDivs = listOf(2),
                yDivs = emptyList(),
                colors = listOf(Color.GREEN, Color.BLUE),
                flags = listOf(LatticeFlags.FIXED_COLOR, LatticeFlags.FIXED_COLOR),
            ),
            dst = RectF32.ofLTRB(0f, 0f, 12f, 6f),
            paint = Paint.fill(Color.WHITE).copy(
                blendMode = BlendMode.PLUS,
                antiAlias = false,
            ),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )

        val ready = assertIs<GPUPreparedImageGridLowering.Ready>(
            GPUPreparedImageGridLowerer.lowerLattice(operation, 0, 0, context()),
        )

        assertEquals(2, ready.commands.size)
        assertTrue(ready.commands.all { it.normalized is NormalizedDrawCommand.FillRect })
        assertTrue(ready.commands.all { it.preparedImage == null })
        assertTrue(ready.commands.all { it.normalized.blend.mode == GPUBlendMode.PLUS })
    }

    @Test
    fun `fixed color only lattice refuses an unbound image filter transactionally`() {
        val operation = DisplayOp.DrawImageLattice(
            image = imageNine(),
            lattice = Lattice(
                xDivs = listOf(2),
                yDivs = emptyList(),
                colors = listOf(Color.GREEN, Color.BLUE),
                flags = listOf(LatticeFlags.FIXED_COLOR, LatticeFlags.FIXED_COLOR),
            ),
            dst = RectF32.ofLTRB(0f, 0f, 12f, 6f),
            paint = Paint(imageFilter = ImageFilter.Blur(1f, 1f)),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )

        val refused = assertIs<GPUPreparedImageGridLowering.Refused>(
            GPUPreparedImageGridLowerer.lowerLattice(operation, 0, 0, context()),
        )

        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, refused.code)
        assertEquals(-1, refused.operationIndex)
        assertEquals("unsupported_paint_effect", refused.facts["reason"])
        assertEquals("imageFilter", refused.facts["paintField"])
    }

    @Test
    fun `fixed color lattice honors mode blender over legacy blend mode`() {
        val operation = DisplayOp.DrawImageLattice(
            image = imageNine(),
            lattice = Lattice(
                xDivs = listOf(2),
                yDivs = emptyList(),
                colors = listOf(Color.GREEN, Color.BLUE),
                flags = listOf(LatticeFlags.FIXED_COLOR, LatticeFlags.FIXED_COLOR),
            ),
            dst = RectF32.ofLTRB(0f, 0f, 12f, 6f),
            paint = Paint.fill(Color.WHITE).copy(
                blendMode = BlendMode.SRC,
                blender = Blender.Mode(BlendMode.PLUS),
                antiAlias = false,
            ),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )

        val ready = assertIs<GPUPreparedImageGridLowering.Ready>(
            GPUPreparedImageGridLowerer.lowerLattice(operation, 0, 0, context()),
        )

        assertEquals(2, ready.commands.size)
        assertTrue(ready.commands.all { it.normalized.blend.mode == GPUBlendMode.PLUS })
    }

    @Test
    fun `unsupported lattice blender refuses the whole logical operation`() {
        val operation = DisplayOp.DrawImageLattice(
            image = imageNine(),
            lattice = Lattice(
                xDivs = listOf(2),
                yDivs = emptyList(),
                colors = listOf(Color.GREEN, Color.BLUE),
                flags = listOf(LatticeFlags.FIXED_COLOR, LatticeFlags.FIXED_COLOR),
            ),
            dst = RectF32.ofLTRB(0f, 0f, 12f, 6f),
            paint = Paint.fill(Color.WHITE).copy(
                blender = Blender.Arithmetic(0f, 1f, 0f, 0f),
                antiAlias = false,
            ),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )

        val refused = assertIs<GPUPreparedImageGridLowering.Refused>(
            GPUPreparedImageGridLowerer.lowerLattice(operation, 0, 0, context()),
        )

        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, refused.code)
        assertEquals(-1, refused.operationIndex)
        assertEquals("unsupported_blender", refused.facts["reason"])
    }

    @Test
    fun `later invalid lattice cell refuses without a valid prefix`() {
        val operation = DisplayOp.DrawImageLattice(
            image = imageNine(),
            lattice = Lattice(
                xDivs = listOf(2, 4),
                yDivs = emptyList(),
                colors = null,
                flags = listOf(
                    LatticeFlags.DEFAULT,
                    LatticeFlags.FIXED_COLOR,
                    LatticeFlags.TRANSPARENT,
                ),
            ),
            dst = RectF32.ofLTRB(0f, 0f, 18f, 6f),
            paint = null,
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )

        val refused = assertIs<GPUPreparedImageGridLowering.Refused>(
            GPUPreparedImageGridLowerer.lowerLattice(operation, 3, 3, context()),
        )

        assertEquals(GPUPreparedImageRefusalCodes.LATTICE_GEOMETRY, refused.code)
        assertEquals(1, refused.operationIndex)
    }

    @Test
    fun `later sampled cell with unsupported native destination blend refuses whole lattice`() {
        val operation = DisplayOp.DrawImageLattice(
            image = imageNine(),
            lattice = Lattice(
                xDivs = listOf(2),
                yDivs = emptyList(),
                colors = listOf(Color.GREEN, Color.TRANSPARENT),
                flags = listOf(LatticeFlags.FIXED_COLOR, LatticeFlags.DEFAULT),
            ),
            dst = RectF32.ofLTRB(0f, 0f, 12f, 6f),
            paint = Paint.fill(Color.WHITE).copy(
                blendMode = BlendMode.MULTIPLY,
                antiAlias = false,
            ),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )

        val refused = assertIs<GPUPreparedImageGridLowering.Refused>(
            GPUPreparedImageGridLowerer.lowerLattice(operation, 0, 0, context()),
        )

        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, refused.code)
        assertEquals(1, refused.operationIndex)
    }

    @Test
    fun `invalid nine lattice image sampling and transform use canonical refusals`() {
        val validNine = DisplayOp.DrawImageNine(
            imageNine(),
            RectF32.ofLTRB(2f, 2f, 4f, 4f),
            RectF32.ofLTRB(0f, 0f, 12f, 12f),
            null,
            Matrix3x3F32.Identity,
            ClipStack.WideOpen,
        )
        val invalidNine = validNine.copy(center = RectF32.ofLTRB(4f, 2f, 2f, 4f))
        assertEquals(
            GPUPreparedImageRefusalCodes.NINE_GEOMETRY,
            assertIs<GPUPreparedImageGridLowering.Refused>(
                GPUPreparedImageGridLowerer.lowerNine(invalidNine, 0, 0, context()),
            ).code,
        )

        val missingImage = Image(
            6,
            6,
            sourceId = "missing-grid-pixels",
            pixels = null,
            alphaType = AlphaType.PREMUL,
        )
        assertEquals(
            GPUPreparedImageRefusalCodes.PIXELS_MISSING,
            assertIs<GPUPreparedImageGridLowering.Refused>(
                GPUPreparedImageGridLowerer.lowerNine(
                    validNine.copy(image = missingImage),
                    0,
                    0,
                    context(),
                ),
            ).code,
        )

        val baseLattice = DisplayOp.DrawImageLattice(
            imageNine(),
            Lattice(xDivs = listOf(2), yDivs = emptyList()),
            RectF32.ofLTRB(0f, 0f, 12f, 6f),
            null,
            Matrix3x3F32.Identity,
            ClipStack.WideOpen,
        )
        assertEquals(
            GPUPreparedImageRefusalCodes.LATTICE_GEOMETRY,
            assertIs<GPUPreparedImageGridLowering.Refused>(
                GPUPreparedImageGridLowerer.lowerLattice(
                    baseLattice.copy(lattice = Lattice(xDivs = listOf(7), yDivs = emptyList())),
                    0,
                    0,
                    context(),
                ),
            ).code,
        )
        assertEquals(
            GPUPreparedImageRefusalCodes.LATTICE_GEOMETRY,
            assertIs<GPUPreparedImageGridLowering.Refused>(
                GPUPreparedImageGridLowerer.lowerLattice(
                    baseLattice.copy(dst = RectF32.ofLTRB(0f, 0f, 2f, 6f)),
                    0,
                    0,
                    context(),
                ),
            ).code,
        )
        val budgetImage = Image(
            width = 66,
            height = 65,
            sourceId = "lattice-cell-budget",
            pixels = ByteArray(66 * 65 * 4),
            alphaType = AlphaType.PREMUL,
        )
        assertEquals(
            GPUPreparedImageRefusalCodes.LATTICE_GEOMETRY,
            assertIs<GPUPreparedImageGridLowering.Refused>(
                GPUPreparedImageGridLowerer.lowerLattice(
                    baseLattice.copy(
                        image = budgetImage,
                        lattice = Lattice(
                            xDivs = (1..65).toList(),
                            yDivs = (1..64).toList(),
                        ),
                    ),
                    0,
                    0,
                    context(),
                ),
            ).code,
        )
        assertEquals(
            GPUPreparedImageRefusalCodes.SAMPLING_CUBIC,
            assertIs<GPUPreparedImageGridLowering.Refused>(
                GPUPreparedImageGridLowerer.lowerLattice(
                    baseLattice.copy(sampling = SamplingOptions.Cubic.Mitchell),
                    0,
                    0,
                    context(),
                ),
            ).code,
        )
        assertEquals(
            GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING,
            assertIs<GPUPreparedImageGridLowering.Refused>(
                GPUPreparedImageGridLowerer.lowerLattice(
                    baseLattice.copy(
                        transform = Matrix3x3F32.of(
                            1f, 0f, 0f,
                            0f, 1f, 0f,
                            0.01f, 0f, 1f,
                        ),
                    ),
                    0,
                    0,
                    context(),
                ),
            ).code,
        )
    }

    private fun imageNine(): Image = Image(
        width = GPUPreparedImageTestFixtures.imageNine6x6Width,
        height = GPUPreparedImageTestFixtures.imageNine6x6Height,
        colorType = GPUPreparedImageTestFixtures.imageNine6x6ColorType,
        sourceId = "prepared-grid-6x6",
        pixels = GPUPreparedImageTestFixtures.imageNine6x6Bytes,
        alphaType = AlphaType.PREMUL,
    )

    private fun context() = GPUPreparedImageLoweringContext(
        provenance = GPUFrameProvenance.GmContent,
        target = GPUTargetFacts(64, 64, "rgba8unorm-srgb"),
        config = RenderConfig.DEFAULT,
        capabilities = capabilities(),
    )

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity(
            facadeName = "test",
            implementationName = "fake",
            adapterName = "mock",
            deviceName = "mock-device",
        ),
        facts = emptyList(),
        knownUnsupportedFacts = emptyList(),
        snapshotId = "prepared-grid-lowerer-test",
        limits = GPULimits(
            maxTextureDimension2D = 8192,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        rendererFeatures = setOf(GPURendererFeature.RenderPass),
    )

    private fun rect(left: Float, top: Float, right: Float, bottom: Float): RectF32 =
        RectF32.ofLTRB(left, top, right, bottom)
}
