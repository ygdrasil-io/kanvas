package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUOrderingFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUPathFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectCornerRadii
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTexture
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendCoverageMask
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendCoverageMaskRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRectDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSimplePassBatchKind
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilMode
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendTriangleData
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendUniformPayloadDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendVertexColorData
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendVertexPositionUVData
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.execution.GPUSurfaceTarget
import org.graphiks.kanvas.gpu.renderer.geometry.FlattenedPath
import org.graphiks.kanvas.gpu.renderer.geometry.PathTessellator
import org.graphiks.kanvas.gpu.renderer.geometry.Point
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlan
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlanner
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedBlurStyle
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedMaskFilter
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Matrix33
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class GPUMaskBlurDispatchTest {
    @Test
    fun `mask blur records five ordered passes then dispatches once`() {
        val target = CapturingMaskBlurTarget()
        val dispatched = mutableListOf<String>()

        val result = target.renderMaskBlurCommand(
            "scene", solidRectCommand(), readyPlan(NormalizedBlurStyle.NORMAL),
            GPUClearColor(0.0, 0.0, 0.0, 0.0), dispatched, Diagnostics(), "rgba8unorm",
        )

        assertTrue(result.rendered)
        assertEquals(listOf("mask", "blur-h", "blur-v", "style", "scene"), target.passKinds)
        assertEquals(listOf("1"), dispatched)
        assertEquals(4, target.createdTextures.size)
    }

    @Test
    fun `mask blur releases each transient exactly once after final composite`() {
        val target = CapturingMaskBlurTarget()

        assertTrue(
            target.renderMaskBlurCommand(
                "scene",
                solidRectCommand(),
                readyPlan(NormalizedBlurStyle.NORMAL),
                GPUClearColor(0.0, 0.0, 0.0, 0.0),
                mutableListOf(),
                Diagnostics(),
                "rgba8unorm",
            ).rendered,
        )
        assertTrue(
            target.renderMaskBlurCommand(
                "scene",
                solidRectCommand().copy(commandId = GPUDrawCommandID(4)),
                readyPlan(NormalizedBlurStyle.NORMAL),
                GPUClearColor(0.0, 0.0, 0.0, 0.0),
                mutableListOf(),
                Diagnostics(),
                "rgba8unorm",
            ).rendered,
        )

        assertEquals(8, target.createdTextures.size)
        assertEquals(target.createdTextures.map(GPUBackendOffscreenTexture::label), target.releasedTextureLabels)
        assertEquals(8, target.releasedTextureLabels.distinct().size)
    }

    @Test
    fun `mask blur uses unorm local targets before compositing into an srgb scene`() {
        val target = CapturingMaskBlurTarget()

        val result = target.renderMaskBlurCommand(
            "scene", solidRectCommand(), readyPlan(NormalizedBlurStyle.NORMAL),
            GPUClearColor(0.0, 0.0, 0.0, 0.0), mutableListOf(), Diagnostics(), "rgba8unorm-srgb",
        )

        assertTrue(result.rendered)
        assertEquals(
            listOf("rgba8unorm", "rgba8unorm", "rgba8unorm", "rgba8unorm", "rgba8unorm-srgb"),
            target.passColorFormats,
        )
    }

    @Test
    fun `mask blur encodes static modules with padded uniform kernels`() {
        val target = CapturingMaskBlurTarget()

        val result = target.renderMaskBlurCommand(
            "source", solidRectCommand(), readyPlan(NormalizedBlurStyle.NORMAL),
            GPUClearColor(0.0, 0.0, 0.0, 0.0), mutableListOf(), Diagnostics(), "rgba8unorm",
        )

        assertTrue(result.rendered)
        assertEquals(
            listOf(MASK_BLUR_HORIZONTAL_WGSL, MASK_BLUR_VERTICAL_WGSL),
            target.compositePasses.take(2).map { it.wgsl },
        )
        assertEquals(listOf(144, 144), target.compositePasses.take(2).map { it.uniformBytes })
    }

    @Test
    fun `non uniform rrect blur refuses before allocating local textures`() {
        val target = CapturingMaskBlurTarget()
        val dispatched = mutableListOf<String>()
        val diagnostics = Diagnostics()

        val result = target.renderMaskBlurCommand(
            "scene",
            nonUniformRRectCommand().copy(
                maskFilter = NormalizedMaskFilter.Blur(NormalizedBlurStyle.NORMAL, sigma = 2f),
            ),
            readyPlan(NormalizedBlurStyle.NORMAL),
            GPUClearColor(0.0, 0.0, 0.0, 0.0), dispatched, diagnostics, "rgba8unorm",
        )

        assertFalse(result.rendered)
        assertTrue(dispatched.isEmpty())
        assertEquals(0, target.createdTextures.size)
        assertEquals("non_uniform_radii", diagnostics.entries.single().reason)
    }

    @Test
    fun `zero sigma non uniform rrect remains an identity plan`() {
        val command = nonUniformRRectCommand().copy(
            maskFilter = NormalizedMaskFilter.Blur(NormalizedBlurStyle.NORMAL, sigma = 0f),
        )

        assertNull(command.maskBlurPreflightRefusalReasonOrNull())
        assertEquals(
            MaskBlurPlan.Identity,
            MaskBlurPlanner.plan(command.toMaskBlurRequest(64, 64, 4096, RenderConfig.DEFAULT)),
        )
    }

    @Test
    fun `wide open clip plans the full blur halo beyond geometry bounds`() {
        val command = solidRectCommand().copy(
            rect = GPURect(10f, 10f, 20f, 20f),
            clip = GPUClipFacts.wideOpen(GPUBounds(10f, 10f, 20f, 20f)),
            bounds = GPUBounds(10f, 10f, 20f, 20f),
            maskFilter = NormalizedMaskFilter.Blur(NormalizedBlurStyle.NORMAL, sigma = 2f),
        )

        val plan = MaskBlurPlanner.plan(command.toMaskBlurRequest(32, 32, 4096, RenderConfig.DEFAULT))

        assertTrue(plan is MaskBlurPlan.Ready)
        assertEquals(GPUBounds(4f, 4f, 26f, 26f), (plan as MaskBlurPlan.Ready).deviceBounds)
    }

    @Test
    fun `only an exact device rect clip can satisfy the mask blur budget`() {
        val bounds = GPUBounds(0f, 0f, 32f, 32f)
        val budget = RenderConfig(maxMaskBlurIntermediateBytes = 1_024u)
        val clipped = solidRectCommand().copy(
            rect = GPURect(0f, 0f, 32f, 32f),
            bounds = bounds,
            clip = GPUClipFacts.deviceRect(GPUBounds(14f, 14f, 18f, 18f)),
            maskFilter = NormalizedMaskFilter.Blur(NormalizedBlurStyle.NORMAL, sigma = 2f),
        )

        val clippedPlan = MaskBlurPlanner.plan(clipped.toMaskBlurRequest(32, 32, 4096, budget))
        val wideOpenPlan = MaskBlurPlanner.plan(
            clipped.copy(clip = GPUClipFacts.wideOpen(bounds)).toMaskBlurRequest(32, 32, 4096, budget),
        )
        val complexPlan = MaskBlurPlanner.plan(
            clipped.copy(clip = GPUClipFacts.complexStack(GPUBounds(14f, 14f, 18f, 18f)))
                .toMaskBlurRequest(32, 32, 4096, budget),
        )

        assertTrue(clippedPlan is MaskBlurPlan.Ready)
        assertEquals(GPUBounds(14f, 14f, 18f, 18f), (clippedPlan as MaskBlurPlan.Ready).deviceBounds)
        assertEquals(1f, clippedPlan.scale)
        assertTrue(wideOpenPlan is MaskBlurPlan.Refused)
        assertTrue(complexPlan is MaskBlurPlan.Refused)
    }

    @Test
    fun `local path mask scales dash intervals and phase`() {
        val target = CapturingMaskBlurTarget()
        val plan = readyPlan(NormalizedBlurStyle.NORMAL).copy(
            scale = 0.5f,
            deviceBounds = GPUBounds(0f, 0f, 64f, 64f),
        )

        val result = target.renderMaskBlurCommand(
            "scene", dashedPathCommand(), plan,
            GPUClearColor(0.0, 0.0, 0.0, 0.0), mutableListOf(), Diagnostics(), "rgba8unorm",
        )

        assertTrue(result.rendered)
        val expected = strokeToFillGeometry(
            contourVertices = listOf(4f, 4f, 16f, 4f),
            contourStarts = listOf(0),
            strokeWidth = 2f,
            dashArray = floatArrayOf(4f, 2f),
            dashPhase = 1f,
        )
        val expectedEdgeFan = PathTessellator().stencilEdgeFan(
            FlattenedPath(
                points = expected.vertices.chunked(2).map { Point(it[0], it[1]) },
                contourStarts = expected.contourStarts,
            ),
        )
        assertEquals(expectedEdgeFan.vertices.toList(), requireNotNull(target.maskTriangleData).vertices.toList())
    }

    @Test
    fun `positive stroke blur composes scale transform once before mask localization`() {
        assertLocalizedPath(
            command = transformedPathCommand(
                strokeWidth = 4f,
                transform = GPUTransformFacts.scale(2f, 2f),
                deviceBounds = GPUBounds(20f, 16f, 40f, 24f),
            ),
            expectedPlanBounds = GPUBounds(14f, 10f, 46f, 30f),
            expectedVertices = listOf(
                6f, 6f,
                6f, 14f,
                26f, 14f,
                26f, 6f,
            ),
            expectedBounds = GPUBounds(6f, 6f, 26f, 14f),
            expectedScissor = listOf(6, 6, 20, 8),
        )
    }

    @Test
    fun `hairline blur remains one device pixel before scale mask localization`() {
        assertLocalizedPath(
            command = transformedPathCommand(
                strokeWidth = 0f,
                transform = GPUTransformFacts.scale(2f, 2f),
                deviceBounds = GPUBounds(20f, 19.5f, 40f, 20.5f),
            ),
            expectedPlanBounds = GPUBounds(14f, 13.5f, 46f, 26.5f),
            expectedVertices = listOf(
                6f, 6f,
                6f, 7f,
                26f, 7f,
                26f, 6f,
            ),
            expectedBounds = GPUBounds(6f, 6f, 26f, 7f),
            expectedScissor = listOf(6, 6, 20, 1),
        )
    }

    @Test
    fun `positive stroke blur composes nonuniform shear once before mask localization`() {
        assertLocalizedPath(
            command = transformedPathCommand(
                strokeWidth = 4f,
                transform = GPUTransformFacts.affine(
                    scaleX = 2f,
                    skewX = 1f,
                    skewY = 0.5f,
                    scaleY = 3f,
                ),
                deviceBounds = GPUBounds(28f, 29f, 52f, 46f),
            ),
            expectedPlanBounds = GPUBounds(22f, 23f, 58f, 52f),
            expectedVertices = listOf(
                6f, 6f,
                10f, 18f,
                30f, 23f,
                26f, 11f,
            ),
            expectedBounds = GPUBounds(6f, 6f, 30f, 23f),
            expectedScissor = listOf(6, 6, 24, 17),
        )
    }

    @Test
    fun `hairline blur uses device normal before nonuniform shear mask localization`() {
        assertLocalizedPath(
            command = transformedPathCommand(
                strokeWidth = 0f,
                transform = GPUTransformFacts.affine(
                    scaleX = 2f,
                    skewX = 1f,
                    skewY = 0.5f,
                    scaleY = 3f,
                ),
                deviceBounds = GPUBounds(
                    29.878733f,
                    34.514927f,
                    50.121267f,
                    40.485073f,
                ),
            ),
            expectedPlanBounds = GPUBounds(
                23.878733f,
                28.514927f,
                56.121267f,
                46.485073f,
            ),
            expectedVertices = listOf(
                6.242534f, 6f,
                6f, 6.970146f,
                26f, 11.970146f,
                26.242534f, 11f,
            ),
            expectedBounds = GPUBounds(6f, 6f, 26.242534f, 11.970146f),
            expectedScissor = listOf(6, 6, 20, 5),
        )
    }

    @Test
    fun `fill path blur maps local through device into mask and publishes identity`() {
        assertLocalizedPath(
            command = transformedPathCommand(
                strokeWidth = null,
                transform = GPUTransformFacts.scale(2f, 2f),
                deviceBounds = GPUBounds(20f, 20f, 40f, 40f),
                vertices = listOf(
                    10f, 10f,
                    20f, 10f,
                    10f, 20f,
                ),
            ),
            expectedPlanBounds = GPUBounds(14f, 14f, 46f, 46f),
            expectedVertices = listOf(
                6f, 6f,
                26f, 6f,
                6f, 26f,
            ),
            expectedBounds = GPUBounds(6f, 6f, 26f, 26f),
            expectedScissor = listOf(6, 6, 20, 20),
        )
    }

    @Test
    fun `inverse fill blur keeps the complete mask domain outside its contour`() {
        val deviceDomain = GPUBounds(8f, 8f, 56f, 56f)
        val base = transformedPathCommand(
            strokeWidth = null,
            transform = GPUTransformFacts.scale(2f, 2f),
            deviceBounds = deviceDomain,
            vertices = listOf(
                10f, 10f,
                20f, 10f,
                10f, 20f,
            ),
        )
        val command = base.copy(
            pathDescriptor = base.pathDescriptor.copy(inverseFill = true),
            clip = GPUClipFacts.deviceRect(deviceDomain),
            maskFilter = NormalizedMaskFilter.Blur(
                NormalizedBlurStyle.NORMAL,
                sigma = 24f,
            ),
        )
        val plan = MaskBlurPlanner.plan(
            command.toMaskBlurRequest(64, 64, 4096, RenderConfig.DEFAULT),
        )
        assertTrue(plan is MaskBlurPlan.Ready)
        plan as MaskBlurPlan.Ready
        assertEquals(deviceDomain, plan.deviceBounds)
        assertEquals(0.5f, plan.scale)
        assertEquals(24, plan.localWidth)
        assertEquals(24, plan.localHeight)

        val local = command.toLocalMaskCommand(plan)
        assertTrue(local is NormalizedDrawCommand.FillPath)
        local as NormalizedDrawCommand.FillPath
        assertTrue(local.pathDescriptor.inverseFill)
        assertFloatListEquals(
            listOf(
                6f, 6f,
                16f, 6f,
                6f, 16f,
            ),
            local.tessellatedVertices,
        )
        assertBoundsEquals(GPUBounds(0f, 0f, 24f, 24f), local.bounds)

        val target = CapturingMaskBlurTarget()
        val diagnostics = Diagnostics()
        val result = target.renderMaskBlurCommand(
            "scene",
            command,
            plan,
            GPUClearColor(0.0, 0.0, 0.0, 0.0),
            mutableListOf(),
            diagnostics,
            "rgba8unorm",
        )

        assertTrue(result.rendered)
        assertEquals(0, diagnostics.fatalCount)
        assertEquals(listOf(0, 0, 24, 24), target.maskScissor)
    }

    @Test
    fun `zero scale determinant fill blur refuses before mask reconstruction`() {
        val transform = Matrix33.scale(0f, 1f).toGPUTransformFacts()
        assertEquals(GPUTransformType.Affine, transform.type)

        assertAffineDeterminantBlurRefused(
            command = transformedPathCommand(
                strokeWidth = null,
                transform = transform,
                deviceBounds = GPUBounds(0f, 10f, 0f, 20f),
                vertices = listOf(
                    10f, 10f,
                    20f, 10f,
                    10f, 20f,
                ),
            ),
            expectedReason = "unsupported.transform.affine_singular",
        )
    }

    @Test
    fun `zero affine determinant stroke blur refuses before mask reconstruction`() {
        val transform = Matrix33.makeAll(
            1f, 2f, 0f,
            0.5f, 1f, 0f,
        ).toGPUTransformFacts()
        assertEquals(GPUTransformType.Affine, transform.type)

        assertAffineDeterminantBlurRefused(
            command = transformedPathCommand(
                strokeWidth = 4f,
                transform = transform,
                deviceBounds = GPUBounds(28f, 14f, 42f, 21f),
            ),
            expectedReason = "unsupported.transform.affine_singular",
        )
    }

    @Test
    fun `non finite affine determinant blur keeps the canonical refusal`() {
        val transform = Matrix33.makeAll(
            Float.MAX_VALUE, Float.MAX_VALUE, 0f,
            Float.MAX_VALUE, Float.MAX_VALUE, 0f,
        ).toGPUTransformFacts()
        assertEquals(GPUTransformType.Affine, transform.type)

        assertAffineDeterminantBlurRefused(
            command = transformedPathCommand(
                strokeWidth = null,
                transform = transform,
                deviceBounds = GPUBounds(0f, 0f, 64f, 64f),
                vertices = listOf(
                    0f, 0f,
                    1f, 0f,
                    0f, 1f,
                ),
            ),
            expectedReason = "unsupported.transform.non_finite",
        )
    }

    @Test
    fun `one ulp nonzero affine determinant remains admitted`() {
        val transform = Matrix33.makeAll(
            1f, 1f, 0f,
            1f, 1.0000001f, 0f,
        ).toGPUTransformFacts()
        assertEquals(GPUTransformType.Affine, transform.type)
        val command = transformedPathCommand(
            strokeWidth = null,
            transform = transform,
            deviceBounds = GPUBounds(20f, 20f, 30f, 30.000002f),
            vertices = listOf(
                10f, 10f,
                20f, 10f,
                10f, 20f,
            ),
        )
        val plan = readyPlan(NormalizedBlurStyle.NORMAL)

        assertNull(command.maskBlurPreflightRefusalReasonOrNull())
        val local = command.toLocalMaskCommand(plan)
        assertTrue(local is NormalizedDrawCommand.FillPath)
        local as NormalizedDrawCommand.FillPath
        assertEquals(GPUTransformType.Identity, local.transform.type)

        val target = CapturingMaskBlurTarget()
        val diagnostics = Diagnostics()
        val result = target.renderMaskBlurCommand(
            "scene",
            command,
            plan,
            GPUClearColor(0.0, 0.0, 0.0, 0.0),
            mutableListOf(),
            diagnostics,
            "rgba8unorm",
        )

        assertTrue(result.rendered)
        assertEquals(0, diagnostics.fatalCount)
        assertEquals(listOf("mask", "blur-h", "blur-v", "style", "scene"), target.passKinds)
    }

    @Test
    fun `explicit singular and perspective tags keep their stable blur refusals`() {
        listOf(
            GPUTransformFacts.singular() to "unsupported_transform:Singular",
            GPUTransformFacts.perspective() to "unsupported_transform:Perspective",
        ).forEach { (transform, expectedReason) ->
            val command = transformedPathCommand(
                strokeWidth = null,
                transform = transform,
                deviceBounds = GPUBounds(0f, 0f, 64f, 64f),
                vertices = listOf(
                    0f, 0f,
                    1f, 0f,
                    0f, 1f,
                ),
            )
            val plan = readyPlan(NormalizedBlurStyle.NORMAL)
            assertNull(command.maskBlurPreflightRefusalReasonOrNull())
            val local = command.toLocalMaskCommand(plan)
            assertEquals(transform, local.transform)

            val target = CapturingMaskBlurTarget()
            val diagnostics = Diagnostics()
            val dispatched = mutableListOf<String>()
            val result = target.renderMaskBlurCommand(
                "scene",
                command,
                plan,
                GPUClearColor(0.0, 0.0, 0.0, 0.0),
                dispatched,
                diagnostics,
                "rgba8unorm",
            )

            assertFalse(result.rendered)
            assertEquals(expectedReason, diagnostics.entries.single().reason)
            assertTrue(dispatched.isEmpty())
            assertTrue(target.passKinds.isEmpty())
            assertNull(target.maskTriangleData)
        }
    }

    private fun solidRectCommand(): NormalizedDrawCommand.FillRect {
        val target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
        val bounds = GPUBounds(10f, 10f, 30f, 30f)
        return NormalizedDrawCommand.FillRect(
            commandId = GPUDrawCommandID(1),
            rect = GPURect(10f, 10f, 30f, 30f),
            transform = GPUTransformFacts.identity(),
            clip = GPUClipFacts.wideOpen(GPUBounds(0f, 0f, 64f, 64f)),
            layer = GPULayerFacts.root(target),
            material = GPUMaterialDescriptor.SolidColor(1f, 0.25f, 0.5f, 1f),
            blend = GPUBlendFacts.srcOver(),
            bounds = bounds,
            ordering = GPUOrderingFacts(0, dependsOnDestination = false, requiresBarrier = false),
            source = GPUCommandSource(adapter = "unit-test", operation = "fillRect"),
            antiAlias = false,
        )
    }

    private fun nonUniformRRectCommand(): NormalizedDrawCommand.FillRRect {
        val target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
        val bounds = GPUBounds(10f, 10f, 30f, 30f)
        return NormalizedDrawCommand.FillRRect(
            commandId = GPUDrawCommandID(2),
            rrect = GPURRect(
                rect = GPURect(10f, 10f, 30f, 30f),
                topLeft = GPURRectCornerRadii(2f, 2f),
                topRight = GPURRectCornerRadii(3f, 2f),
                bottomRight = GPURRectCornerRadii(2f, 2f),
                bottomLeft = GPURRectCornerRadii(2f, 2f),
            ),
            transform = GPUTransformFacts.identity(),
            clip = GPUClipFacts.wideOpen(GPUBounds(0f, 0f, 64f, 64f)),
            layer = GPULayerFacts.root(target),
            material = GPUMaterialDescriptor.SolidColor(1f, 0.25f, 0.5f, 1f),
            blend = GPUBlendFacts.srcOver(),
            bounds = bounds,
            ordering = GPUOrderingFacts(0, dependsOnDestination = false, requiresBarrier = false),
            source = GPUCommandSource(adapter = "unit-test", operation = "fillRRect"),
            antiAlias = false,
        )
    }

    private fun dashedPathCommand(): NormalizedDrawCommand.FillPath {
        val target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
        return NormalizedDrawCommand.FillPath(
            commandId = GPUDrawCommandID(3),
            pathKey = "dash-path",
            pathDescriptor = GPUPathFacts(
                pathKey = "dash-path",
                verbCount = 2,
                pointCount = 2,
                fillRule = "winding",
                inverseFill = false,
                finiteProof = "all_finite",
                volatility = "static",
                transformClass = "identity",
                edgeCount = 1,
            ),
            tessellatedVertices = listOf(8f, 8f, 32f, 8f),
            contourStarts = listOf(0),
            totalVertexCount = 2,
            edgeCount = 1,
            transform = GPUTransformFacts.identity(),
            clip = GPUClipFacts.wideOpen(GPUBounds(0f, 0f, 64f, 64f)),
            layer = GPULayerFacts.root(target),
            material = GPUMaterialDescriptor.SolidColor(1f, 0.25f, 0.5f, 1f),
            blend = GPUBlendFacts.srcOver(),
            bounds = GPUBounds(8f, 8f, 32f, 8f),
            ordering = GPUOrderingFacts(0, dependsOnDestination = false, requiresBarrier = false),
            source = GPUCommandSource(adapter = "unit-test", operation = "fillPath"),
            stroke = true,
            strokeWidth = 4f,
            dashIntervals = floatArrayOf(8f, 4f),
            dashPhase = 2f,
            antiAlias = false,
        )
    }

    private fun transformedPathCommand(
        strokeWidth: Float?,
        transform: GPUTransformFacts,
        deviceBounds: GPUBounds,
        vertices: List<Float> = listOf(10f, 10f, 20f, 10f),
    ): NormalizedDrawCommand.FillPath {
        val target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
        val vertexCount = vertices.size / 2
        return NormalizedDrawCommand.FillPath(
            commandId = GPUDrawCommandID(5),
            pathKey = "transformed-mask-path",
            pathDescriptor = GPUPathFacts(
                pathKey = "transformed-mask-path",
                verbCount = vertexCount + 1,
                pointCount = vertexCount,
                fillRule = "winding",
                inverseFill = false,
                finiteProof = "all_finite",
                volatility = "static",
                transformClass = transform.type.name.lowercase(),
                edgeCount = (vertexCount - 1).coerceAtLeast(0),
            ),
            tessellatedVertices = vertices,
            contourStarts = listOf(0),
            totalVertexCount = vertexCount,
            edgeCount = (vertexCount - 1).coerceAtLeast(0),
            transform = transform,
            clip = GPUClipFacts.wideOpen(GPUBounds(0f, 0f, 64f, 64f)),
            layer = GPULayerFacts.root(target),
            material = GPUMaterialDescriptor.SolidColor(1f, 0.25f, 0.5f, 1f),
            blend = GPUBlendFacts.srcOver(),
            bounds = deviceBounds,
            ordering = GPUOrderingFacts(0, dependsOnDestination = false, requiresBarrier = false),
            source = GPUCommandSource(adapter = "unit-test", operation = "fillPath"),
            stroke = strokeWidth != null,
            strokeWidth = strokeWidth ?: 1f,
            antiAlias = false,
            maskFilter = NormalizedMaskFilter.Blur(
                NormalizedBlurStyle.NORMAL,
                sigma = 2f,
            ),
        )
    }

    private fun assertLocalizedPath(
        command: NormalizedDrawCommand.FillPath,
        expectedPlanBounds: GPUBounds,
        expectedVertices: List<Float>,
        expectedBounds: GPUBounds,
        expectedScissor: List<Int>,
    ) {
        val plan = MaskBlurPlanner.plan(
            command.toMaskBlurRequest(64, 64, 4096, RenderConfig.DEFAULT),
        )
        assertTrue(plan is MaskBlurPlan.Ready)
        plan as MaskBlurPlan.Ready
        assertEquals(expectedPlanBounds, plan.deviceBounds)
        assertEquals(1f, plan.scale)

        val local = command.toLocalMaskCommand(plan)
        assertTrue(local is NormalizedDrawCommand.FillPath)
        local as NormalizedDrawCommand.FillPath
        assertEquals(GPUTransformType.Identity, local.transform.type)
        assertEquals("identity", local.pathDescriptor.transformClass)
        assertFalse(local.stroke)
        assertFloatListEquals(expectedVertices, local.tessellatedVertices)
        assertBoundsEquals(expectedBounds, local.bounds)
        local.tessellatedVertices.chunked(2).forEach { (x, y) ->
            assertTrue(x in 0f..plan.localWidth.toFloat(), "x=$x outside mask")
            assertTrue(y in 0f..plan.localHeight.toFloat(), "y=$y outside mask")
        }

        val target = CapturingMaskBlurTarget()
        val diagnostics = Diagnostics()
        val result = target.renderMaskBlurCommand(
            "scene",
            command,
            plan,
            GPUClearColor(0.0, 0.0, 0.0, 0.0),
            mutableListOf(),
            diagnostics,
            "rgba8unorm",
        )

        assertTrue(result.rendered)
        assertEquals(0, diagnostics.fatalCount)
        assertPointSetEquals(expectedVertices, target.maskOutlinePoints())
        assertEquals(expectedScissor, target.maskScissor)
    }

    private fun assertAffineDeterminantBlurRefused(
        command: NormalizedDrawCommand.FillPath,
        expectedReason: String,
    ) {
        val plan = readyPlan(NormalizedBlurStyle.NORMAL)

        assertEquals(expectedReason, command.maskBlurPreflightRefusalReasonOrNull())
        val local = command.toLocalMaskCommand(plan)
        assertTrue(local is NormalizedDrawCommand.FillPath)
        local as NormalizedDrawCommand.FillPath
        assertEquals(command.transform, local.transform)
        assertEquals(command.stroke, local.stroke)
        assertFloatListEquals(command.tessellatedVertices, local.tessellatedVertices)

        val target = CapturingMaskBlurTarget()
        val diagnostics = Diagnostics()
        val dispatched = mutableListOf<String>()
        val result = target.renderMaskBlurCommand(
            "scene",
            command,
            plan,
            GPUClearColor(0.0, 0.0, 0.0, 0.0),
            dispatched,
            diagnostics,
            "rgba8unorm",
        )

        assertFalse(result.rendered)
        assertEquals(expectedReason, diagnostics.entries.single().reason)
        assertTrue(dispatched.isEmpty())
        assertTrue(target.createdTextures.isEmpty())
        assertTrue(target.passKinds.isEmpty())
        assertNull(target.maskTriangleData)
    }

    private fun assertFloatListEquals(
        expected: List<Float>,
        actual: List<Float>,
        tolerance: Float = 1e-5f,
    ) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEachIndexed { index, (expectedValue, actualValue) ->
            assertTrue(
                abs(expectedValue - actualValue) <= tolerance,
                "coordinate[$index]: expected=$expectedValue actual=$actualValue",
            )
        }
    }

    private fun assertBoundsEquals(
        expected: GPUBounds,
        actual: GPUBounds,
        tolerance: Float = 1e-5f,
    ) {
        assertTrue(abs(expected.left - actual.left) <= tolerance)
        assertTrue(abs(expected.top - actual.top) <= tolerance)
        assertTrue(abs(expected.right - actual.right) <= tolerance)
        assertTrue(abs(expected.bottom - actual.bottom) <= tolerance)
    }

    private fun assertPointSetEquals(
        expectedCoordinates: List<Float>,
        actual: Set<Pair<Float, Float>>,
        tolerance: Float = 1e-5f,
    ) {
        val expected = expectedCoordinates.chunked(2).map { (x, y) -> x to y }.toSet()
        assertEquals(expected.size, actual.size)
        assertTrue(expected.all { expectedPoint ->
            actual.any { actualPoint ->
                abs(expectedPoint.first - actualPoint.first) <= tolerance &&
                    abs(expectedPoint.second - actualPoint.second) <= tolerance
            }
        })
    }

    private fun readyPlan(style: NormalizedBlurStyle) = MaskBlurPlan.Ready(
        style = style,
        requestedSigma = 2f,
        normalizedSigma = 2f,
        effectiveSigma = 2f,
        halo = 6,
        scale = 1f,
        deviceBounds = GPUBounds(4f, 4f, 36f, 36f),
        localWidth = 32,
        localHeight = 32,
        bytesPerTexture = 4096,
        requiredBytes = 16_384,
        diagnostics = emptyList(),
    )

    private class CapturingMaskBlurTarget : GPUBackendOffscreenTarget {
        data class CompositePass(val wgsl: String, val uniformBytes: Int)

        val passKinds = mutableListOf<String>()
        val passColorFormats = mutableListOf<String>()
        val compositePasses = mutableListOf<CompositePass>()
        val createdTextures = mutableListOf<GPUBackendOffscreenTexture>()
        val releasedTextureLabels = mutableListOf<String>()
        val targetCopyTextureLabels = mutableListOf<String>()
        var maskTriangleData: GPUBackendTriangleData? = null
        var maskScissor: List<Int>? = null

        override val target: GPUSurfaceTarget
            get() = error("target is not used by this pass-planning test")

        override fun encode(clearColor: GPUClearColor, block: GPUBackendRenderRecorder.() -> Unit) =
            error("Unexpected target pass")

        override fun readRgba(): ByteArray = error("Unexpected readback")

        override fun createOffscreenTexture(texture: GPUBackendOffscreenTexture): String {
            createdTextures += texture
            return texture.label
        }

        override fun releaseOffscreenTexture(textureLabel: String) {
            releasedTextureLabels += textureLabel
        }

        override fun snapshotTargetToOffscreenTexture(textureLabel: String) = error("Unexpected snapshot")

        override fun copyTargetToOffscreenTexture(destinationTextureLabel: String) {
            targetCopyTextureLabels += destinationTextureLabel
        }

        override fun encodeOffscreenTexture(
            textureLabel: String,
            clearColor: GPUClearColor?,
            block: GPUBackendRenderRecorder.() -> Unit,
        ) {
            block(CapturingRecorder(textureLabel))
        }

        override fun createCoverageMask(request: GPUBackendCoverageMaskRequest): GPUBackendCoverageMask =
            error("Unexpected coverage mask allocation")

        override fun encodeCoverageMask(
            mask: GPUBackendCoverageMask,
            clearColor: GPUClearColor?,
            block: GPUBackendRenderRecorder.() -> Unit,
        ) = error("Unexpected coverage mask pass")

        override fun releaseCoverageMask(mask: GPUBackendCoverageMask) =
            error("Unexpected coverage mask release")

        override fun copyOffscreenTexture(sourceTextureLabel: String, destinationTextureLabel: String) =
            error("Unexpected GPU-to-GPU copy")

        override fun close() = Unit

        private inner class CapturingRecorder(
            private val destinationLabel: String,
        ) : GPUBackendRenderRecorder {
            override val maxTextureDimension2D: Int
                get() = 4096

            override fun drawFullscreenPass(
                wgsl: String,
                colorFormat: String,
                draws: List<GPUBackendRectDraw>,
                blendMode: GPUFixedFunctionBlendState?,
                passBatchKind: GPUBackendSimplePassBatchKind?,
            ) {
                passKinds += "mask"
                passColorFormats += colorFormat
            }

            override fun drawCompositePass(
                wgsl: String,
                colorFormat: String,
                textureLabel: String,
                draws: List<GPUBackendRawUniformDraw>,
                blendMode: GPUFixedFunctionBlendState?,
            ) {
                compositePasses += CompositePass(wgsl, draws.single().uniformBytes.size)
                passKinds += when {
                    destinationLabel.endsWith(":horizontal") -> "blur-h"
                    destinationLabel.endsWith(":vertical") -> "blur-v"
                    else -> "scene"
                }
                passColorFormats += colorFormat
            }

            override fun drawBlendPass(
                wgsl: String,
                colorFormat: String,
                srcTextureLabel: String,
                dstTextureLabel: String,
                draws: List<GPUBackendRawUniformDraw>,
            ) {
                passKinds += "style"
                passColorFormats += colorFormat
            }

            override fun drawTwoTexturePass(wgsl: String, colorFormat: String, firstTextureLabel: String, secondTextureLabel: String, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUFixedFunctionBlendState?) = unexpected()
            override fun drawThreeTexturePass(wgsl: String, colorFormat: String, firstTextureLabel: String, secondTextureLabel: String, thirdTextureLabel: String, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUFixedFunctionBlendState?) = unexpected()

            override fun drawFullscreenUniformPayloadPass(wgsl: String, colorFormat: String, draws: List<GPUBackendUniformPayloadDraw>, blendMode: GPUFixedFunctionBlendState?, sourceLabel: String, passBatchKind: GPUBackendSimplePassBatchKind?) = unexpected()
            override fun drawFullscreenRawUniformPass(wgsl: String, colorFormat: String, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUFixedFunctionBlendState?, passBatchKind: GPUBackendSimplePassBatchKind?) = unexpected()
            override fun drawFullscreenStencilPass(wgsl: String, colorFormat: String, stencilMode: GPUBackendStencilMode, triangleData: GPUBackendTriangleData?, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUFixedFunctionBlendState?, stencilConfig: org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilCoverConfig) {
                if (stencilMode == GPUBackendStencilMode.Write) {
                    maskTriangleData = triangleData
                } else {
                    passKinds += "mask"
                    val draw = draws.single()
                    maskScissor = listOf(
                        draw.scissorX,
                        draw.scissorY,
                        draw.scissorWidth,
                        draw.scissorHeight,
                    )
                }
            }
            override fun drawFullscreenTextureUniformPass(wgsl: String, colorFormat: String, textureRgba: ByteArray, textureWidth: Int, textureHeight: Int, textureFormat: String, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUFixedFunctionBlendState?, stencilMode: GPUBackendStencilMode?, stencilConfig: org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilCoverConfig) = unexpected()
            override fun createVertexColorBuffer(data: GPUBackendVertexColorData): String = unexpected()
            override fun drawVertexColorIndexed(vertexBufferLabel: String, indexCount: Int, uniformDraw: GPUBackendRawUniformDraw, blendMode: GPUFixedFunctionBlendState?) = unexpected()
            override fun createVertexPositionUVBuffer(data: GPUBackendVertexPositionUVData): String = unexpected()
            override fun drawVertexPositionUVIndexed(vertexBufferLabel: String, indexCount: Int, uniformDraw: GPUBackendRawUniformDraw, textureRgba: ByteArray, textureWidth: Int, textureHeight: Int, textureFormat: String, blendMode: GPUFixedFunctionBlendState?) = unexpected()
            override fun drawVertexPositionDualUVIndexed(vertexBufferLabel: String, indexCount: Int, uniformDraw: GPUBackendRawUniformDraw, texture1Rgba: ByteArray, texture1Width: Int, texture1Height: Int, texture2Rgba: ByteArray, texture2Width: Int, texture2Height: Int, textureFormat: String, blendMode: GPUFixedFunctionBlendState?) = unexpected()
            override fun createOffscreenTexture(texture: GPUBackendOffscreenTexture): String = unexpected()
            override fun encodeOffscreenTexture(textureLabel: String, clearColor: GPUClearColor?, block: GPUBackendRenderRecorder.() -> Unit) = unexpected()
            override fun drawTextAtlasPass(atlasRgba: ByteArray, atlasWidth: Int, atlasHeight: Int, atlasFormat: String, vertexData: FloatArray, indexData: IntArray, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUFixedFunctionBlendState?) = unexpected()
            override fun drawColorGlyphPass(atlasRgba: ByteArray, atlasWidth: Int, atlasHeight: Int, atlasFormat: String, vertexData: FloatArray, indexData: IntArray, draws: List<GPUBackendRawUniformDraw>, blendMode: GPUFixedFunctionBlendState?) = unexpected()

            private fun unexpected(): Nothing = error("Unexpected recorder call")
        }

        fun maskOutlinePoints(): Set<Pair<Float, Float>> {
            val vertices = requireNotNull(maskTriangleData).vertices
            return vertices.asList().chunked(6).flatMap { edge ->
                listOf(edge[2] to edge[3], edge[4] to edge[5])
            }.toSet()
        }
    }
}
