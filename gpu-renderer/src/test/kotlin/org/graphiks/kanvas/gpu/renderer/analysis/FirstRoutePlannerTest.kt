package org.graphiks.kanvas.gpu.renderer.analysis

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipAtomicGroupID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipOrderingToken
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilConsumerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilLoadOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilProducerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilStoreOperation
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSourceKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUApplyFilterCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawLayerCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillPathCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPULinearGradientCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawImageRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUOrderingFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectCornerRadii
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.GPUPathFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedBlurStyle
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedMaskFilter
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterGraphDescriptor
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterNodeDescriptor
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterNodeID
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterSourcePlan
import org.graphiks.kanvas.gpu.renderer.filters.GPUSimpleFilterBounds
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterCropPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_AFFINE_FILL_RECT_CAPABILITY
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_AFFINE_FILL_RECT_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.preparedImageScissorAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.sealedDeviceGeometryInput
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterSamplingPlan
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.routing.GPUFirstRouteDecisionBuilder
import org.graphiks.kanvas.gpu.renderer.routing.GPURouteDecision

/** Verifies the first native FillRect analysis, route, and pass builder. */
class FirstRoutePlannerTest {
    @Test
    fun `three stop linear gradient requires the typed stroke source and its distinct capability`() {
        val material = GPUMaterialDescriptor.LinearGradient(
            startX = 8.5f, startY = 32.5f, endX = 55.5f, endY = 32.5f,
            startR = 1f, startG = 0f, startB = 0f, startA = 1f,
            endR = 0f, endG = 0f, endB = 1f, endA = 1f,
            allStopPositions = floatArrayOf(0f, .5f, 1f),
            allStopColors = floatArrayOf(1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 1f),
        )
        fun command(sourceKind: GPUCommandSourceKind) = GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(37),
            rect = GPURect(6f, 14f, 58f, 18f),
            target = GPUTargetFacts(64, 64, "rgba8unorm-srgb"),
            material = material,
            source = GPUCommandSource("unit-test", "stroke-band", kind = sourceKind),
        ).copy(antiAlias = false)
        val capabilities = firstSliceWithLinearGradientCapabilities().copy(
            facts = firstSliceWithLinearGradientCapabilities().facts + GPUCapabilityFact(
                name = GPUFirstSliceCapabilityName.STROKE_RECT_LINEAR_GRADIENT_THREE_STOP_NATIVE,
                source = "unit-test",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "three-stop-stroke-fixture",
            ),
        )

        val typedPlan = GPUFirstRoutePlanner(capabilities).plan(
            command(GPUCommandSourceKind.AnalyticStrokeRectBand),
        )
        val typedDecision = typedPlan.routeDecision
        assertTrue(typedDecision is GPURouteDecision.Native, typedDecision.toString())
        assertEquals("native.stroke_rect.linear_gradient_three_stop", typedPlan.analysisRecord.routeDecisionLabel)
        assertEquals(
            listOf(GPUFirstSliceCapabilityName.STROKE_RECT_LINEAR_GRADIENT_THREE_STOP_NATIVE),
            assertIs<GPURouteDecision.Native>(typedDecision).route.requirements,
        )
        assertTrue(
            GPUFirstRoutePlanner(firstSliceWithLinearGradientCapabilities()).plan(
                command(GPUCommandSourceKind.PublicFillRect),
            ).routeDecision is GPURouteDecision.Native,
            "W32 PublicFillRect three-stop admission must not consume the W37 stroke capability",
        )
        assertEquals(
            "unsupported.material.mapping.linear_gradient_stop_count",
            assertIs<GPURouteDecision.Refused>(
                GPUFirstRoutePlanner(capabilities).plan(command(GPUCommandSourceKind.Generic)).routeDecision,
            ).diagnostic.code,
        )
        assertEquals(
            "unsupported.material.mapping.linear_gradient_stop_count",
            assertIs<GPURouteDecision.Refused>(
                GPUFirstRoutePlanner(
                    capabilities.copy(
                        facts = capabilities.facts.filterNot {
                            it.name == GPUFirstSliceCapabilityName.STROKE_RECT_LINEAR_GRADIENT_THREE_STOP_NATIVE
                        },
                    ),
                ).plan(command(GPUCommandSourceKind.AnalyticStrokeRectBand)).routeDecision,
            ).diagnostic.code,
        )
    }

    @Test
    fun `two stop radial gradient requires typed stroke provenance and its dedicated capability`() {
        val material = GPUMaterialDescriptor.RadialGradient(
            centerX = 32.5f, centerY = 32.5f, radius = 23.5f,
            startR = 1f, startG = 0f, startB = 0f, startA = 1f,
            endR = 0f, endG = 0f, endB = 1f, endA = 1f,
            allStopPositions = floatArrayOf(0f, 1f),
            allStopColors = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f),
        )
        fun command(sourceKind: GPUCommandSourceKind) = GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(38), rect = GPURect(6f, 14f, 58f, 18f),
            target = GPUTargetFacts(64, 64, "rgba8unorm-srgb"), material = material,
            source = GPUCommandSource("unit-test", "radial-stroke-band", kind = sourceKind),
        ).copy(antiAlias = false)
        val base = firstSliceWithLinearGradientCapabilities().copy(
            facts = firstSliceWithLinearGradientCapabilities().facts + listOf(
                GPUCapabilityFact("first_slice.radial_gradient.native", "unit-test", "supported", true, "radial-fixture"),
                GPUCapabilityFact(
                    GPUFirstSliceCapabilityName.STROKE_RECT_RADIAL_GRADIENT_TWO_STOP_NATIVE,
                    "unit-test", "supported", true, "two-stop-radial-stroke-fixture",
                ),
            ),
        )
        val typed = GPUFirstRoutePlanner(base).plan(command(GPUCommandSourceKind.AnalyticStrokeRectBand))
        assertEquals("native.stroke_rect.radial_gradient_two_stop", typed.analysisRecord.routeDecisionLabel)
        assertEquals(
            listOf(GPUFirstSliceCapabilityName.STROKE_RECT_RADIAL_GRADIENT_TWO_STOP_NATIVE),
            assertIs<GPURouteDecision.Native>(typed.routeDecision).route.requirements,
        )
        assertEquals(
            "native.fill_rect.radial_gradient",
            GPUFirstRoutePlanner(base).plan(command(GPUCommandSourceKind.PublicFillRect)).analysisRecord.routeDecisionLabel,
        )
        assertEquals(
            "native.fill_rect.radial_gradient",
            GPUFirstRoutePlanner(base).plan(command(GPUCommandSourceKind.Generic)).analysisRecord.routeDecisionLabel,
        )
        assertEquals(
            "unsupported.stroke.rect_radial_gradient_two_stop_capability",
            assertIs<GPURouteDecision.Refused>(
                GPUFirstRoutePlanner(base.copy(facts = base.facts.filterNot {
                    it.name == GPUFirstSliceCapabilityName.STROKE_RECT_RADIAL_GRADIENT_TWO_STOP_NATIVE
                })).plan(command(GPUCommandSourceKind.AnalyticStrokeRectBand)).routeDecision,
            ).diagnostic.code,
        )
    }

    @Test
    fun `two stop full sweep gradient uses the dedicated stroke route only for analytic provenance`() {
        val material = GPUMaterialDescriptor.SweepGradient(
            32.5f, 32.5f, 0f, 360f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f,
            allStopPositions = floatArrayOf(0f, 1f), allStopColors = floatArrayOf(1f,0f,0f,1f,0f,0f,1f,1f),
        )
        fun command(kind: GPUCommandSourceKind) = GPUFillRectCommandBuilder.build(
            GPUDrawCommandID(39), GPURect(6f,14f,58f,18f), GPUTargetFacts(64,64,"rgba8unorm-srgb"), material,
            source = GPUCommandSource("unit-test", "sweep-stroke-band", kind = kind),
        ).copy(antiAlias = false)
        val capabilities = firstSliceWithLinearGradientCapabilities().copy(facts = firstSliceWithLinearGradientCapabilities().facts + listOf(
            GPUCapabilityFact("first_slice.sweep_gradient.native", "unit-test", "supported", true, "sweep"),
            GPUCapabilityFact(GPUFirstSliceCapabilityName.STROKE_RECT_SWEEP_GRADIENT_TWO_STOP_NATIVE, "unit-test", "supported", true, "sweep-stroke"),
        ))
        val typed = GPUFirstRoutePlanner(capabilities).plan(command(GPUCommandSourceKind.AnalyticStrokeRectBand))
        assertEquals("native.stroke_rect.sweep_gradient_two_stop", typed.analysisRecord.routeDecisionLabel)
        assertEquals(listOf(GPUFirstSliceCapabilityName.STROKE_RECT_SWEEP_GRADIENT_TWO_STOP_NATIVE), assertIs<GPURouteDecision.Native>(typed.routeDecision).route.requirements)
        assertEquals("native.fill_rect.sweep_gradient", GPUFirstRoutePlanner(capabilities).plan(command(GPUCommandSourceKind.PublicFillRect)).analysisRecord.routeDecisionLabel)
        assertEquals("native.fill_rect.sweep_gradient", GPUFirstRoutePlanner(capabilities).plan(command(GPUCommandSourceKind.Generic)).analysisRecord.routeDecisionLabel)
        assertEquals("unsupported.stroke.rect_sweep_gradient_two_stop_capability", assertIs<GPURouteDecision.Refused>(GPUFirstRoutePlanner(capabilities.copy(facts = capabilities.facts.filterNot { it.name == GPUFirstSliceCapabilityName.STROKE_RECT_SWEEP_GRADIENT_TWO_STOP_NATIVE })).plan(command(GPUCommandSourceKind.AnalyticStrokeRectBand)).routeDecision).diagnostic.code)
    }

    @Test
    fun `native FillRect route builder retains its four argument JVM descriptor`() {
        val methods = GPUFirstRouteDecisionBuilder::class.java.methods.filter { method ->
            method.name == "nativeFillRect"
        }

        assertEquals(1, methods.size)
        assertEquals(4, methods.single().parameterCount)
        assertEquals(
            listOf(
                Int::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
                List::class.java,
            ),
            methods.single().parameterTypes.toList(),
        )
    }

    /** Unsupported image sampling facts stay refused until the prepared semantic can represent them. */
    @Test
    fun `draw image rect rejects unsupported sampling facts before route selection`() {
        val command = GPUDrawImageRectCommandBuilder.build(
            commandId = GPUDrawCommandID(70),
            imageSourceId = IMAGE_DRAW_SOURCE_ID,
            src = GPURect(left = 0f, top = 0f, right = 2f, bottom = 2f),
            dst = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.ImageDraw(imageSourceId = IMAGE_DRAW_SOURCE_ID, imageWidth = 2, imageHeight = 2),
            samplingTileModeX = "repeat",
            samplingTileModeY = IMAGE_DRAW_SAMPLING_TILE_MODE_Y,
            samplingFilterMode = "cubic",
            samplingMipmapMode = "linear",
            pixelsWidth = IMAGE_DRAW_PIXELS_WIDTH,
            pixelsHeight = IMAGE_DRAW_PIXELS_HEIGHT,
            pixelsFormat = IMAGE_DRAW_PIXELS_FORMAT,
            pixelsRowBytes = IMAGE_DRAW_PIXELS_ROW_BYTES,
            pixelsAlphaType = IMAGE_DRAW_PIXELS_ALPHA,
            pixelsColorProfileLabel = IMAGE_DRAW_PIXELS_COLOR_PROFILE,
            pixelsOrientationState = IMAGE_DRAW_PIXELS_ORIENTATION,
            pixelsGeneration = IMAGE_DRAW_PIXELS_GENERATION,
            pixelsContentHash = IMAGE_DRAW_PIXELS_CONTENT_HASH,
            pixelsProvenance = IMAGE_DRAW_PIXELS_PROVENANCE,
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)

        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals(GPUPreparedImageRefusalCodes.TILE_MODE, plan.pass.diagnostics.single().code)
    }

    /** Cubic, mipmap, and anisotropic sampling remain explicit refusals in the prepared lane. */
    @Test
    fun `draw image rect uses dedicated unsupported sampler refusal codes`() {
        val base = GPUDrawImageRectCommandBuilder.build(
            commandId = GPUDrawCommandID(71), imageSourceId = IMAGE_DRAW_SOURCE_ID,
            src = GPURect(0f, 0f, 2f, 2f), dst = GPURect(2f, 3f, 18f, 21f),
            target = GPUTargetFacts(64, 64, "rgba8unorm"),
            material = GPUMaterialDescriptor.ImageDraw(IMAGE_DRAW_SOURCE_ID, 2, 2),
            pixelsWidth = IMAGE_DRAW_PIXELS_WIDTH, pixelsHeight = IMAGE_DRAW_PIXELS_HEIGHT,
            pixelsFormat = IMAGE_DRAW_PIXELS_FORMAT, pixelsRowBytes = IMAGE_DRAW_PIXELS_ROW_BYTES,
            pixelsAlphaType = IMAGE_DRAW_PIXELS_ALPHA, pixelsColorProfileLabel = IMAGE_DRAW_PIXELS_COLOR_PROFILE,
            pixelsOrientationState = IMAGE_DRAW_PIXELS_ORIENTATION, pixelsGeneration = IMAGE_DRAW_PIXELS_GENERATION,
            pixelsContentHash = IMAGE_DRAW_PIXELS_CONTENT_HASH, pixelsProvenance = IMAGE_DRAW_PIXELS_PROVENANCE,
        )
        fun refusal(command: NormalizedDrawCommand.DrawImageRect) =
            GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command).pass.diagnostics.single().code

        assertEquals("unsupported.image.sampling_cubic", refusal(base.copy(samplingFilterMode = "cubic")))
        assertEquals(GPUPreparedImageRefusalCodes.MIP_REQUIRED, refusal(base.copy(samplingMipmapMode = "linear")))
        assertEquals(
            GPUPreparedImageRefusalCodes.SAMPLING_ANISOTROPIC,
            refusal(base.copy(samplingAnisotropy = 2)),
        )
        assertEquals(
            GPUPreparedImageRefusalCodes.PIXELS_MISSING,
            refusal(base.copy(pixelsProvenance = "")),
        )
    }

    @Test
    fun `draw image device scissor is total clamped and typed for invalid bounds`() {
        val target = GPUTargetFacts(64, 64, "rgba8unorm")
        fun command(bounds: GPUBounds) = GPUDrawImageRectCommandBuilder.build(
            commandId = GPUDrawCommandID(73),
            imageSourceId = IMAGE_DRAW_SOURCE_ID,
            src = GPURect(0f, 0f, 2f, 2f),
            dst = GPURect(2f, 3f, 18f, 21f),
            target = target,
            material = GPUMaterialDescriptor.ImageDraw(IMAGE_DRAW_SOURCE_ID, 2, 2),
            clip = GPUClipFacts.deviceRect(bounds),
            pixelsWidth = IMAGE_DRAW_PIXELS_WIDTH,
            pixelsHeight = IMAGE_DRAW_PIXELS_HEIGHT,
            pixelsFormat = IMAGE_DRAW_PIXELS_FORMAT,
            pixelsRowBytes = IMAGE_DRAW_PIXELS_ROW_BYTES,
            pixelsAlphaType = IMAGE_DRAW_PIXELS_ALPHA,
            pixelsColorProfileLabel = IMAGE_DRAW_PIXELS_COLOR_PROFILE,
            pixelsOrientationState = IMAGE_DRAW_PIXELS_ORIENTATION,
            pixelsGeneration = IMAGE_DRAW_PIXELS_GENERATION,
            pixelsContentHash = IMAGE_DRAW_PIXELS_CONTENT_HASH,
            pixelsProvenance = IMAGE_DRAW_PIXELS_PROVENANCE,
        )
        val planner = GPUFirstRoutePlanner(firstSliceWithScissorCapabilities())
        val accepted = listOf(
            GPUBounds(-4f, 5f, 16f, 17f) to GPUPixelBounds(0, 5, 16, 17),
            GPUBounds(60f, 5f, 80f, 17f) to GPUPixelBounds(60, 5, 64, 17),
        )

        accepted.forEach { (bounds, expected) ->
            val plan = planner.plan(command(bounds))
            assertIs<GPURouteDecision.Prepared>(plan.routeDecision)
            val packet = plan.pass.drawPackets.single()
            assertEquals(preparedImageScissorAuthority(expected), packet.scissorBoundsHash)
            assertEquals(
                GPUClipCoveragePlan.Scissor(
                    GPUBounds(
                        expected.left.toFloat(),
                        expected.top.toFloat(),
                        expected.right.toFloat(),
                        expected.bottom.toFloat(),
                    ),
                ),
                packet.clipCoveragePlan,
            )
            assertEquals(GPUClipExecutionPlan.ScissorOnly(expected), packet.clipExecutionPlan)
        }

        val refused = listOf(
            GPUBounds(16f, 5f, 4f, 17f) to "unsupported.clip.scissor_invalid",
            GPUBounds(4f, 5f, Float.POSITIVE_INFINITY, 17f) to
                "unsupported.bounds.non_finite",
            GPUBounds(70f, 5f, 80f, 17f) to "unsupported.clip.scissor_empty",
        )
        refused.forEach { (bounds, code) ->
            val plan = planner.plan(command(bounds))
            assertEquals(code, assertIs<GPURouteDecision.Refused>(plan.routeDecision).diagnostic.code)
            assertTrue(plan.pass.drawPackets.isEmpty())
        }
    }

    @Test
    fun `draw image recording preserves canonical prepared refusal codes with boundary facts`() {
        val base = GPUDrawImageRectCommandBuilder.build(
            commandId = GPUDrawCommandID(72),
            imageSourceId = IMAGE_DRAW_SOURCE_ID,
            src = GPURect(0f, 0f, 2f, 2f),
            dst = GPURect(2f, 3f, 18f, 21f),
            target = GPUTargetFacts(64, 64, "rgba8unorm"),
            material = GPUMaterialDescriptor.ImageDraw(IMAGE_DRAW_SOURCE_ID, 2, 2),
            pixelsWidth = IMAGE_DRAW_PIXELS_WIDTH,
            pixelsHeight = IMAGE_DRAW_PIXELS_HEIGHT,
            pixelsFormat = IMAGE_DRAW_PIXELS_FORMAT,
            pixelsRowBytes = IMAGE_DRAW_PIXELS_ROW_BYTES,
            pixelsAlphaType = IMAGE_DRAW_PIXELS_ALPHA,
            pixelsColorProfileLabel = IMAGE_DRAW_PIXELS_COLOR_PROFILE,
            pixelsOrientationState = IMAGE_DRAW_PIXELS_ORIENTATION,
            pixelsGeneration = IMAGE_DRAW_PIXELS_GENERATION,
            pixelsContentHash = IMAGE_DRAW_PIXELS_CONTENT_HASH,
            pixelsProvenance = IMAGE_DRAW_PIXELS_PROVENANCE,
        )
        val cases = listOf(
            GPUPreparedImageRefusalCodes.PIXELS_MISSING to base.copy(pixelsContentHash = ""),
            GPUPreparedImageRefusalCodes.PIXEL_FORMAT to base.copy(pixelsFormat = "Gray8"),
            GPUPreparedImageRefusalCodes.ALPHA_INTERPRETATION to
                base.copy(pixelsAlphaType = "Unpremul"),
            GPUPreparedImageRefusalCodes.PIXEL_ROW_STRIDE to base.copy(pixelsRowBytes = 3),
            GPUPreparedImageRefusalCodes.NATIVE_GENERATION to base.copy(pixelsGeneration = -1),
        )

        cases.forEach { (expectedCode, command) ->
            val diagnostic = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities())
                .plan(command)
                .analysisRecord
                .diagnostics
                .single()

            assertEquals(expectedCode, diagnostic.code)
            assertEquals("recording", diagnostic.facts["boundary"])
            assertFalse(diagnostic.code.startsWith("unsupported.surface.prepared.image-source."))
        }
    }

    /** Accepted solid FillRect produces pre-materialization analysis, native route, and pass records only. */
    @Test
    fun `solid fill rect builds native route and draw pass without materialized resources`() {
        val command = GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(4),
            rect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)
        val routeDecision = assertIs<GPURouteDecision.Native>(plan.routeDecision)
        val analysisDecision = assertIs<GPUDrawAnalysisDecision.Candidate>(plan.analysisDecision)
        val invocation = plan.pass.invocations.single()

        assertEquals("analysis.fill_rect.4", plan.analysisRecord.recordId)
        assertEquals("native.fill_rect.solid", analysisDecision.routeDecisionLabel)
        assertEquals("native.fill_rect.solid", routeDecision.route.consumerKind)
        assertEquals("rect.fill.coverage", routeDecision.route.renderStepIdentity)
        assertEquals(listOf("first_slice.fill_rect.native"), routeDecision.route.requirements)
        assertEquals(emptyList(), analysisDecision.resourceDeclarations)
        assertEquals("pass.root.4", plan.pass.passId)
        assertEquals(listOf("pending.pipeline.fill_rect.solid.rgba8unorm.src_over"), plan.pass.pipelineKeys)
        assertEquals("pending.pipeline.fill_rect.solid.rgba8unorm.src_over", invocation.pipelineKeyHash)
        assertEquals("analysis.fill_rect.4", invocation.analysisRecordId)
        assertEquals(4, invocation.commandIdValue)
        assertEquals(0, invocation.renderStepIndex)
        assertEquals("rect.fill.coverage", invocation.renderStepId.value)
        assertEquals("root", invocation.layerScopeId)
        assertEquals(0, invocation.sortKey)
        assertEquals("bounds:2.0,3.0,18.0,21.0", invocation.boundsHash)
        assertNull(invocation.scissorBoundsHash)
        assertNull(invocation.uniformSlot)
        assertNull(invocation.resourceSlot)
    }

    @Test
    fun `solid fill rect accepts sRGB target without erasing format identity`() {
        val linearFixture =
            firstRouteCommand(
                target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            )
        val srgbFixture =
            firstRouteCommand(
                target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm-srgb"),
            )
        val incompatibleFixture =
            firstRouteCommand(
                target = GPUTargetFacts(width = 64, height = 64, colorFormat = "bgra8unorm-srgb"),
            )

        val linear =
            GPUFirstRoutePlanner(capabilities = linearFixture.capabilities).plan(linearFixture.command)
        val srgb =
            GPUFirstRoutePlanner(capabilities = srgbFixture.capabilities).plan(srgbFixture.command)
        val incompatible =
            GPUFirstRoutePlanner(capabilities = incompatibleFixture.capabilities)
                .plan(incompatibleFixture.command)

        assertIs<GPURouteDecision.Native>(srgb.routeDecision)
        assertEquals("target.rgba8unorm-srgb.64x64", srgb.pass.targetStateHash)
        assertEquals(
            listOf("pending.pipeline.fill_rect.solid.rgba8unorm-srgb.src_over"),
            srgb.pass.pipelineKeys,
        )
        assertNotEquals(linear.pass.targetStateHash, srgb.pass.targetStateHash)
        assertNotEquals(linear.pass.pipelineKeys, srgb.pass.pipelineKeys)
        assertEquals(
            "unsupported.target.format_blend_incompatible",
            assertIs<GPURouteDecision.Refused>(incompatible.routeDecision).diagnostic.code,
        )
    }

    @Test
    fun `solid fill rect packet owns the gatherers exact rectangle color and zero radii payload`() {
        val command = GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(41),
            rect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 0.75f),
        )

        val packet = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command).pass.drawPackets.single()
        val semantic = assertIs<GPUDrawSemanticPayload.SolidRect>(packet.semanticPayload)
        val ref = semantic.payloadRef
        val block = assertNotNull(ref.uniformBlock)
        val floats = java.nio.ByteBuffer.wrap(block.bytes.map(Int::toByte).toByteArray())
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
            .let { buffer -> List(12) { buffer.get(it) } }

        assertEquals(packet.commandIdValue, ref.commandIdValue)
        assertEquals(packet.renderStepId.value, ref.renderStepIdentity)
        assertEquals(packet.uniformSlot, ref.uniformSlot)
        assertNull(ref.resourceSlot)
        assertEquals(
            listOf(2f, 3f, 18f, 21f, 0f, 0f, 0f, 0f, 1f, 0.25f, 0.5f, 0.75f),
            floats,
        )
    }

    /** Translate-like transforms remain in the native first FillRect route. */
    @Test
    fun `translated solid fill rect remains native`() {
        val command = GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(5),
            rect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
            transform = GPUTransformFacts.translation(x = 7f, y = 11f),
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)
        val routeDecision = assertIs<GPURouteDecision.Native>(plan.routeDecision)

        assertEquals("native.fill_rect.solid", routeDecision.route.consumerKind)
        assertEquals("transform:translate", plan.analysisRecord.diagnostics.single().code)
        assertEquals("target.rgba8unorm.64x64", plan.pass.targetStateHash)
    }

    @Test
    fun `fill rect derives sealed axis aligned or affine direct triangle authority from transform facts`() {
        val target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
        val cases = listOf(
            GPUTransformFacts.identity() to GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
            GPUTransformFacts.affine(-1f, 0f, 0f, 1f) to
                GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
            GPUTransformFacts.affine(0f, -1f, 1f, 0f) to
                GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1,
            GPUTransformFacts.affine(1f, 0.25f, 0.125f, 1f) to
                GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1,
        )

        cases.forEachIndexed { index, (transform, expectedAuthority) ->
            val command = GPUFillRectCommandBuilder.build(
                commandId = GPUDrawCommandID(70 + index),
                rect = firstRouteRect,
                target = target,
                material = GPUMaterialDescriptor.SolidColor(1f, 0.25f, 0.5f, 1f),
                transform = transform,
                source = GPUCommandSource("forged-adapter", "drawPath"),
            ).copy(antiAlias = false)

            val plan = GPUFirstRoutePlanner(firstSliceAffineFillRectCapabilities()).plan(command)

            assertIs<GPURouteDecision.Native>(plan.routeDecision)
            assertEquals("analysis.fill_rect.${command.commandId.value}", plan.analysisRecord.recordId)
            assertEquals("FillRect", plan.analysisRecord.commandFamily)
            assertEquals(expectedAuthority, plan.analysisRecord.corePrimitiveRectRouteAuthority)
            assertEquals(
                corePrimitiveRectGeometryAuthority(command.rect, command.transform),
                plan.analysisRecord.corePrimitiveRectGeometryAuthority,
            )
            assertTrue(
                requireNotNull(plan.analysisRecord.corePrimitiveRectGeometryAuthority)
                    .matchesCorePrimitiveRectGeometry(command.rect, command.transform),
            )
            assertEquals(
                if (expectedAuthority == GPUCorePrimitiveRectRouteAuthority.RectAxisAligned) {
                    "rect.fill.coverage"
                } else {
                    CORE_PRIMITIVE_AFFINE_FILL_RECT_STEP_IDENTITY
                },
                plan.pass.drawPackets.single().renderStepId.value,
            )
        }
    }

    @Test
    fun `bounded radial and sweep fill rects receive the sealed axis aligned CorePrimitive authority`() {
        val materials = listOf(
            GPUMaterialDescriptor.RadialGradient(
                centerX = 8f, centerY = 8f, radius = 8f,
                startR = 1f, startG = 0f, startB = 0f, startA = 1f,
                endR = 0f, endG = 0f, endB = 1f, endA = 1f,
            ),
            GPUMaterialDescriptor.SweepGradient(
                centerX = 8f, centerY = 8f, startAngle = 0f, endAngle = 360f,
                startR = 1f, startG = 0f, startB = 0f, startA = 1f,
                endR = 0f, endG = 0f, endB = 1f, endA = 1f,
            ),
        )

        materials.forEachIndexed { index, material ->
            val plan = GPUFirstRoutePlanner(firstSliceWithRadialAndSweepGradientCapabilities()).plan(
                GPUFillRectCommandBuilder.build(
                    commandId = GPUDrawCommandID(90 + index),
                    rect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
                    target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
                    material = material,
                ).copy(antiAlias = false),
            )

            assertIs<GPURouteDecision.Native>(plan.routeDecision)
            assertEquals(
                GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
                plan.analysisRecord.corePrimitiveRectRouteAuthority,
            )
            assertNotNull(plan.analysisRecord.corePrimitiveRectGeometryAuthority)
        }
    }

    @Test
    fun `linear gradient facts refuse before route selection when they are not supported`() {
        val material = GPUMaterialDescriptor.LinearGradient(
            startX = 2f, startY = 3f, endX = 18f, endY = 21f,
            startR = 1f, startG = 0f, startB = 0f, startA = 1f,
            endR = 0f, endG = 0f, endB = 1f, endA = 1f,
        ).withGradientFacts(GPUMaterialDescriptor.GradientFacts(interpolation = "linear"))

        val plan = GPUFirstRoutePlanner(firstSliceWithLinearGradientCapabilities()).plan(
            GPUFillRectCommandBuilder.build(
                commandId = GPUDrawCommandID(99),
                rect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
                target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
                material = material,
            ).copy(antiAlias = false),
        )

        assertEquals(
            "unsupported.material.mapping.gradient_interpolation",
            assertIs<GPURouteDecision.Refused>(plan.routeDecision).diagnostic.code,
        )
    }

    /** Repeat is admitted only for the bounded native linear-gradient FillRect route. */
    @Test
    fun `linear repeat gradient routes natively while adjacent tile modes and families remain refused`() {
        fun linear(tileMode: String) = GPUMaterialDescriptor.LinearGradient(
            startX = 0f, startY = 0f, endX = 8f, endY = 0f,
            startR = 1f, startG = 0f, startB = 0f, startA = 1f,
            endR = 0f, endG = 0f, endB = 1f, endA = 1f,
            tileMode = tileMode,
        )
        fun plan(material: GPUMaterialDescriptor) = GPUFirstRoutePlanner(
            firstSliceWithLinearGradientCapabilities(),
        ).plan(
            GPUFillRectCommandBuilder.build(
                commandId = GPUDrawCommandID(980),
                rect = GPURect(left = -4f, top = 0f, right = 20f, bottom = 8f),
                target = GPUTargetFacts(width = 32, height = 16, colorFormat = "rgba8unorm"),
                material = material,
            ).copy(antiAlias = false),
        )

        val accepted = plan(linear("repeat"))

        assertIs<GPURouteDecision.Native>(accepted.routeDecision)
        assertEquals(
            "pending.pipeline.fill_rect.linear_gradient.repeat.rgba8unorm.src_over",
            accepted.pass.pipelineKeys.single(),
        )

        listOf(
            linear("mirror"),
            linear("decal"),
            GPUMaterialDescriptor.RadialGradient(
                centerX = 4f, centerY = 4f, radius = 4f,
                startR = 1f, startG = 0f, startB = 0f, startA = 1f,
                endR = 0f, endG = 0f, endB = 1f, endA = 1f,
                tileMode = "repeat",
            ),
            GPUMaterialDescriptor.SweepGradient(
                centerX = 4f, centerY = 4f, startAngle = 0f, endAngle = 360f,
                startR = 1f, startG = 0f, startB = 0f, startA = 1f,
                endR = 0f, endG = 0f, endB = 1f, endA = 1f,
                tileMode = "repeat",
            ),
        ).forEach { refused ->
            assertEquals(
                "unsupported.material.gradient_tile_mode_unsupported",
                assertIs<GPURouteDecision.Refused>(plan(refused).routeDecision).diagnostic.code,
            )
        }
    }

    /** Repeat is a bounded unfiltered FillRect exception, not a shared gradient admission. */
    @Test
    fun `linear repeat remains refused for rrect path and mask filtered fill rect`() {
        fun repeatLinear() = GPUMaterialDescriptor.LinearGradient(
            startX = 0f, startY = 0f, endX = 8f, endY = 0f,
            startR = 1f, startG = 0f, startB = 0f, startA = 1f,
            endR = 0f, endG = 0f, endB = 1f, endA = 1f,
            tileMode = "repeat",
        )
        fun assertTileModeRefusal(plan: GPUFirstRoutePlan) {
            assertEquals(
                "unsupported.material.gradient_tile_mode_unsupported",
                assertIs<GPURouteDecision.Refused>(plan.routeDecision).diagnostic.code,
            )
        }

        assertTileModeRefusal(
            GPUFirstRoutePlanner(firstSliceRRectWithLinearGradientCapabilities()).plan(
                GPUFillRRectCommandBuilder.build(
                    commandId = GPUDrawCommandID(981),
                    rrect = GPURRect(GPURect(0f, 0f, 16f, 16f), radiusX = 3f, radiusY = 3f),
                    target = GPUTargetFacts(32, 32, "rgba8unorm"),
                    material = repeatLinear(),
                ),
            ),
        )
        assertTileModeRefusal(
            GPUFirstRoutePlanner(firstSlicePathFillWithLinearGradientCapabilities()).plan(
                GPUFillPathCommandBuilder.build(
                    commandId = GPUDrawCommandID(982),
                    pathKey = "path:repeat-triangle:v1",
                    pathDescriptor = GPUPathFacts(
                        pathKey = "path:repeat-triangle:v1",
                        verbCount = 4,
                        pointCount = 3,
                        fillRule = "NonZero",
                        inverseFill = false,
                        finiteProof = "finite",
                        volatility = "immutable",
                        transformClass = "identity",
                        edgeCount = 3,
                    ),
                    tessellatedVertices = listOf(0f, 0f, 16f, 0f, 8f, 16f),
                    contourStarts = listOf(0),
                    edgeCount = 3,
                    target = GPUTargetFacts(32, 32, "rgba8unorm"),
                    material = repeatLinear(),
                ),
            ),
        )
        assertTileModeRefusal(
            GPUFirstRoutePlanner(firstSliceWithLinearGradientCapabilities()).plan(
                GPUFillRectCommandBuilder.build(
                    commandId = GPUDrawCommandID(983),
                    rect = GPURect(0f, 0f, 16f, 16f),
                    target = GPUTargetFacts(32, 32, "rgba8unorm"),
                    material = repeatLinear(),
                ).copy(
                    maskFilter = NormalizedMaskFilter.Blur(
                        style = NormalizedBlurStyle.NORMAL,
                        sigma = 4f,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `bounded radial and sweep fill rects select their CorePrimitive programs`() {
        val cases = listOf(
            GPUMaterialDescriptor.RadialGradient(
                centerX = 8f, centerY = 8f, radius = 8f,
                startR = 1f, startG = 0f, startB = 0f, startA = 1f,
                endR = 0f, endG = 0f, endB = 1f, endA = 1f,
            ) to Triple(
                "native.fill_rect.radial_gradient",
                "radial.gradient.fill",
                "first_slice.radial_gradient.native",
            ),
            GPUMaterialDescriptor.SweepGradient(
                centerX = 8f, centerY = 8f, startAngle = 0f, endAngle = 360f,
                startR = 1f, startG = 0f, startB = 0f, startA = 1f,
                endR = 0f, endG = 0f, endB = 1f, endA = 1f,
            ) to Triple(
                "native.fill_rect.sweep_gradient",
                "sweep.gradient.fill",
                "first_slice.sweep_gradient.native",
            ),
        )

        cases.forEachIndexed { index, (material, expected) ->
            val plan = GPUFirstRoutePlanner(firstSliceWithRadialAndSweepGradientCapabilities()).plan(
                GPUFillRectCommandBuilder.build(
                    commandId = GPUDrawCommandID(100 + index),
                    rect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
                    target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
                    material = material,
                ).copy(antiAlias = false),
            )

            val route = assertIs<GPURouteDecision.Native>(plan.routeDecision).route
            assertEquals(expected.first, plan.analysisRecord.routeDecisionLabel)
            assertEquals(listOf(expected.second), plan.analysisRecord.renderStepCandidates)
            assertEquals("pending.pipeline.fill_rect.${expected.first.substringAfterLast('.')}.rgba8unorm.src_over", plan.pass.pipelineKeys.single())
            assertEquals(expected.first, route.consumerKind)
            assertEquals(expected.second, route.renderStepIdentity)
            assertEquals(listOf(expected.third), route.requirements)
        }
    }

    @Test
    fun `radial and sweep antialias fill rects refuse before analytic recording`() {
        val materials = listOf(
            GPUMaterialDescriptor.RadialGradient(
                centerX = 8f, centerY = 8f, radius = 8f,
                startR = 1f, startG = 0f, startB = 0f, startA = 1f,
                endR = 0f, endG = 0f, endB = 1f, endA = 1f,
            ),
            GPUMaterialDescriptor.SweepGradient(
                centerX = 8f, centerY = 8f, startAngle = 0f, endAngle = 360f,
                startR = 1f, startG = 0f, startB = 0f, startA = 1f,
                endR = 0f, endG = 0f, endB = 1f, endA = 1f,
            ),
        )

        materials.forEachIndexed { index, material ->
            val plan = GPUFirstRoutePlanner(firstSliceWithRadialAndSweepGradientCapabilities()).plan(
                GPUFillRectCommandBuilder.build(
                    commandId = GPUDrawCommandID(110 + index),
                    rect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
                    target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
                    material = material,
                ).copy(antiAlias = true),
            )

            assertEquals(
                "unsupported.material.gradient_antialias",
                assertIs<GPURouteDecision.Refused>(plan.routeDecision).diagnostic.code,
            )
            assertEquals(
                "unsupported.material.gradient_antialias",
                assertIs<GPUDrawAnalysisDecision.Refuse>(plan.analysisDecision).diagnostic.code,
            )
            assertTrue(plan.pass.drawPackets.isEmpty())
        }
    }

    @Test
    fun `scale and affine fill rect accept only solid material while identity and translate keep gradient support`() {
        val target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
        val rect = firstRouteRect
        val gradient = GPUMaterialDescriptor.LinearGradient(
            startX = 2f, startY = 3f, endX = 18f, endY = 21f,
            startR = 1f, startG = 0.25f, startB = 0.5f, startA = 1f,
            endR = 0f, endG = 0.75f, endB = 0.5f, endA = 1f,
        )
        val nonSolidCases = listOf(
            GPUTransformFacts.scale(2f, 3f) to gradient,
            GPUTransformFacts.affine(2f, 0f, 0f, 3f) to gradient,
            GPUTransformFacts.affine(1f, 0.25f, 0.125f, 1f) to
                GPUMaterialDescriptor.RadialGradient(
                    centerX = 8f, centerY = 8f, radius = 8f,
                    startR = 1f, startG = 0f, startB = 0f, startA = 1f,
                    endR = 0f, endG = 0f, endB = 1f, endA = 1f,
                ),
            GPUTransformFacts.affine(1f, 0.25f, 0.125f, 1f) to
                GPUMaterialDescriptor.SweepGradient(
                    centerX = 8f, centerY = 8f, startAngle = 0f, endAngle = 360f,
                    startR = 1f, startG = 0f, startB = 0f, startA = 1f,
                    endR = 0f, endG = 0f, endB = 1f, endA = 1f,
                ),
            GPUTransformFacts.scale(2f, 3f) to GPUMaterialDescriptor.RuntimeEffect("runtime.test"),
            GPUTransformFacts.affine(2f, 0f, 0f, 3f) to
                GPUMaterialDescriptor.RuntimeEffect("runtime.test"),
        )

        nonSolidCases.forEachIndexed { index, (transform, material) ->
            val command = GPUFillRectCommandBuilder.build(
                commandId = GPUDrawCommandID(90 + index),
                rect = rect,
                target = target,
                material = material,
                transform = transform,
            ).copy(antiAlias = false)
            val plan = GPUFirstRoutePlanner(firstSliceWithLinearGradientCapabilities()).plan(command)

            assertIs<GPURouteDecision.Refused>(plan.routeDecision)
            assertEquals("unsupported.transform.affine_material", plan.pass.diagnostics.single().code)
        }

        listOf(GPUTransformFacts.identity(), GPUTransformFacts.translation(4f, 5f)).forEachIndexed {
                index,
                transform,
            ->
            val command = GPULinearGradientCommandBuilder.build(
                commandId = GPUDrawCommandID(100 + index),
                rect = rect,
                target = target,
                material = gradient,
                transform = transform,
            )

            assertIs<GPURouteDecision.Native>(
                GPUFirstRoutePlanner(firstSliceWithLinearGradientCapabilities()).plan(command).routeDecision,
            )
        }
    }

    @Test
    fun `affine fill rect rejects aa perspective singular non finite missing capability and non solid facts`() {
        val target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
        val solid = GPUMaterialDescriptor.SolidColor(1f, 0.25f, 0.5f, 1f)
        fun command(
            transform: GPUTransformFacts,
            material: GPUMaterialDescriptor = solid,
            antiAlias: Boolean = false,
        ) = GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(80),
            rect = firstRouteRect,
            target = target,
            material = material,
            transform = transform,
        ).copy(antiAlias = antiAlias)
        val affine = GPUTransformFacts.affine(1f, 0.25f, 0.125f, 1f)
        val cases = listOf(
            Triple(
                "unsupported.transform.affine_antialias",
                command(affine, antiAlias = true),
                firstSliceAffineFillRectCapabilities(),
            ),
            Triple(
                "unsupported.transform.perspective",
                command(GPUTransformFacts.perspective()),
                firstSliceAffineFillRectCapabilities(),
            ),
            Triple(
                "unsupported.transform.singular",
                command(GPUTransformFacts.singular()),
                firstSliceAffineFillRectCapabilities(),
            ),
            Triple(
                "unsupported.transform.affine_singular",
                command(GPUTransformFacts.affine(1f, 2f, 0.5f, 1f)),
                firstSliceAffineFillRectCapabilities(),
            ),
            Triple(
                "unsupported.transform.non_finite",
                command(GPUTransformFacts.affine(1f, Float.NaN, 0f, 1f)),
                firstSliceAffineFillRectCapabilities(),
            ),
            Triple(
                "unsupported.transform.affine_capability_missing",
                command(affine),
                firstSliceCapabilities(),
            ),
            Triple(
                "unsupported.transform.affine_material",
                command(
                    affine,
                    GPUMaterialDescriptor.LinearGradient(
                        0f, 0f, 8f, 8f,
                        1f, 0f, 0f, 1f,
                        0f, 0f, 1f, 1f,
                    ),
                ),
                firstSliceAffineFillRectCapabilities(),
            ),
        )

        cases.forEach { (expectedCode, command, capabilities) ->
            val plan = GPUFirstRoutePlanner(capabilities).plan(command)
            assertEquals(expectedCode, assertIs<GPURouteDecision.Refused>(plan.routeDecision).diagnostic.code)
            assertEquals(expectedCode, assertIs<GPUDrawAnalysisDecision.Refuse>(plan.analysisDecision).diagnostic.code)
            assertTrue(plan.pass.drawPackets.isEmpty())
        }
    }

    /** A simple device-rectangle clip is accepted only when its scissor evidence reaches the pass. */
    @Test
    fun `device rect clip remains native and records scissor bounds`() {
        val command = GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(6),
            rect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
            clip = GPUClipFacts.deviceRect(
                bounds = GPUBounds(left = 4f, top = 5f, right = 16f, bottom = 17f),
            ),
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceWithScissorCapabilities()).plan(command)
        assertIs<GPURouteDecision.Native>(plan.routeDecision)

        assertEquals("scissor_4.0_5.0_16.0_17.0", plan.pass.invocations.single().scissorBoundsHash)
    }

    /** A DeviceRect clip without the scissor capability refuses with a specific diagnostic. */
    @Test
    fun `device rect clip without scissor capability refuses diagnostically`() {
        val command = GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(7),
            rect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
            clip = GPUClipFacts.deviceRect(
                bounds = GPUBounds(left = 4f, top = 5f, right = 16f, bottom = 17f),
            ),
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.clip.scissor_capability_missing", plan.pass.diagnostics.single().code)
    }

    /** Accepted FillRect with LinearGradient material routes to native with gradient render step. */
    @Test
    fun `linear gradient fill rect routes natively with gradient step and pipeline key`() {
        val command = GPULinearGradientCommandBuilder.build(
            commandId = GPUDrawCommandID(8),
            rect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.LinearGradient(
                startX = 2f, startY = 3f, endX = 18f, endY = 21f,
                startR = 1f, startG = 0.25f, startB = 0.5f, startA = 1f,
                endR = 0f, endG = 0.75f, endB = 0.5f, endA = 1f,
            ),
        )

        val plan = GPUFirstRoutePlanner(
            capabilities = firstSliceWithLinearGradientCapabilities(),
        ).plan(command)
        val routeDecision = assertIs<GPURouteDecision.Native>(plan.routeDecision)
        val analysisDecision = assertIs<GPUDrawAnalysisDecision.Candidate>(plan.analysisDecision)
        val invocation = plan.pass.invocations.single()

        assertEquals("native.fill_rect.linear_gradient", routeDecision.route.consumerKind)
        assertEquals("linear.gradient.fill", routeDecision.route.renderStepIdentity)
        assertEquals(
            listOf("first_slice.linear_gradient.native"),
            routeDecision.route.requirements,
        )
        assertEquals("native.fill_rect.linear_gradient", analysisDecision.routeDecisionLabel)
        assertEquals(listOf("linear.gradient.fill"), analysisDecision.renderStepCandidates)
        assertEquals(
            listOf("pending.pipeline.fill_rect.linear_gradient.rgba8unorm.src_over"),
            plan.pass.pipelineKeys,
        )
        assertEquals(
            "pending.pipeline.fill_rect.linear_gradient.rgba8unorm.src_over",
            invocation.pipelineKeyHash,
        )
        assertEquals("linear.gradient.fill", invocation.renderStepId.value)
        assertEquals("pending.material.linear_gradient", plan.analysisRecord.materialKeyHash)
    }

    /** FillRect with LinearGradient material refuses when the linear gradient capability is missing. */
    @Test
    fun `linear gradient fill rect without capability refuses diagnostically`() {
        val command = GPULinearGradientCommandBuilder.build(
            commandId = GPUDrawCommandID(9),
            rect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.LinearGradient(
                startX = 2f, startY = 3f, endX = 18f, endY = 21f,
                startR = 1f, startG = 0.25f, startB = 0.5f, startA = 1f,
                endR = 0f, endG = 0.75f, endB = 0.5f, endA = 1f,
            ),
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals(
            "unsupported.material.linear_gradient_capability_missing",
            plan.pass.diagnostics.single().code,
        )
    }

    @Test
    fun `linear gradient route refuses transform target and clip facts before semantic gathering`() {
        val material = GPUMaterialDescriptor.LinearGradient(
            startX = 2f, startY = 3f, endX = 18f, endY = 21f,
            startR = 1f, startG = 0.25f, startB = 0.5f, startA = 1f,
            endR = 0f, endG = 0.75f, endB = 0.5f, endA = 1f,
        )
        val target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
        fun command(
            transform: GPUTransformFacts = GPUTransformFacts.identity(),
            clip: GPUClipFacts = GPUClipFacts.wideOpen(bounds = GPUBounds(0f, 0f, 64f, 64f)),
            commandTarget: GPUTargetFacts = target,
        ) = GPULinearGradientCommandBuilder.build(
            commandId = GPUDrawCommandID(40),
            rect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = commandTarget,
            material = material,
            transform = transform,
            clip = clip,
        ).copy(antiAlias = false)

        val cases = listOf(
            "transform" to Pair(
                command(transform = GPUTransformFacts.affine(1f, 0.25f, 0.125f, 1f)),
                firstSliceWithLinearGradientCapabilities(),
            ),
            "device-scissor" to Pair(
                command(clip = GPUClipFacts.deviceRect(GPUBounds(4f, 5f, 16f, 17f))),
                firstSliceWithLinearGradientCapabilities(),
            ),
            "complex-clip" to Pair(
                command(clip = GPUClipFacts.complexStack(bounds = GPUBounds(0f, 0f, 64f, 64f))),
                firstSliceWithLinearGradientCapabilities(),
            ),
            "target" to Pair(
                command(commandTarget = target.copy(colorFormat = "bgra8unorm-srgb")),
                firstSliceWithLinearGradientCapabilities(),
            ),
        )
        val expectedCodes = listOf(
            "unsupported.transform.affine_material",
            "unsupported.clip.scissor_capability_missing",
            "unsupported.clip.complex_stack",
            "unsupported.target.format_blend_incompatible",
        )

        cases.zip(expectedCodes).forEach { (case, expectedCode) ->
            val fixture = case.second
            val plan = GPUFirstRoutePlanner(fixture.second).plan(fixture.first)
            assertEquals(expectedCode, assertIs<GPURouteDecision.Refused>(plan.routeDecision).diagnostic.code)
            assertTrue(plan.pass.drawPackets.isEmpty())
        }
    }

    @Test
    fun `typed unsupported material refusal outranks material kind and capability checks`() {
        val command = GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(10),
            rect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.Unsupported(
                reason = org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX,
                originalKind = org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind.ImageDraw,
            ),
        )

        val plan = GPUFirstRoutePlanner(capabilities = emptyCapabilities()).plan(command)
        val routeDecision = assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        val analysisDecision = assertIs<GPUDrawAnalysisDecision.Refuse>(plan.analysisDecision)

        assertEquals(
            "unsupported.material.mapping.local_matrix",
            routeDecision.diagnostic.code,
        )
        assertEquals(routeDecision.diagnostic.code, analysisDecision.diagnostic.code)
        assertEquals(listOf(routeDecision.diagnostic.code), plan.pass.diagnostics.map { it.code })
    }

    @Test
    fun `radial and sweep gradient fact refusals precede material kind and capability checks`() {
        val radial = GPUMaterialDescriptor.RadialGradient(
            centerX = 8f,
            centerY = 8f,
            radius = 8f,
            startR = 1f,
            startG = 0f,
            startB = 0f,
            startA = 1f,
            endR = 0f,
            endG = 0f,
            endB = 1f,
            endA = 1f,
        )
        val sweep = GPUMaterialDescriptor.SweepGradient(
            centerX = 8f,
            centerY = 8f,
            startAngle = 0f,
            endAngle = 360f,
            startR = 1f,
            startG = 0f,
            startB = 0f,
            startA = 1f,
            endR = 0f,
            endG = 0f,
            endB = 1f,
            endA = 1f,
        )
        val nonIdentityMatrix = listOf(
            1f, 0f, 2f,
            0f, 1f, 3f,
            0f, 0f, 1f,
        )
        val cases = listOf(
            "unsupported.material.mapping.gradient_interpolation" to radial.withGradientFacts(
                org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.GradientFacts(
                    interpolation = "linear",
                ),
            ),
            "unsupported.material.mapping.local_matrix" to radial.withGradientFacts(
                org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.GradientFacts(
                    localMatrix = nonIdentityMatrix,
                ),
            ),
            "unsupported.material.mapping.gradient_interpolation" to sweep.withGradientFacts(
                org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.GradientFacts(
                    interpolation = "linear",
                ),
            ),
            "unsupported.material.mapping.local_matrix" to sweep.withGradientFacts(
                org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.GradientFacts(
                    localMatrix = nonIdentityMatrix,
                ),
            ),
        )

        cases.forEachIndexed { index, (expectedCode, material) ->
            val command = GPUFillRectCommandBuilder.build(
                commandId = GPUDrawCommandID(11 + index),
                rect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
                target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
                material = material,
            )

            val plan = GPUFirstRoutePlanner(capabilities = emptyCapabilities()).plan(command)
            val routeDecision = assertIs<GPURouteDecision.Refused>(plan.routeDecision)
            val analysisDecision = assertIs<GPUDrawAnalysisDecision.Refuse>(plan.analysisDecision)

            assertEquals(expectedCode, routeDecision.diagnostic.code)
            assertEquals(expectedCode, analysisDecision.diagnostic.code)
            assertEquals(listOf(expectedCode), plan.pass.diagnostics.map { it.code })
        }

        listOf(
            radial to "unsupported.material.radial_gradient_capability_missing",
            sweep to "unsupported.material.sweep_gradient_capability_missing",
        ).forEachIndexed { index, (material, expectedCode) ->
            val plan = GPUFirstRoutePlanner(capabilities = emptyCapabilities()).plan(
                GPUFillRectCommandBuilder.build(
                    commandId = GPUDrawCommandID(20 + index),
                    rect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
                    target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
                    material = material,
                ),
            )

            assertEquals(expectedCode, assertIs<GPURouteDecision.Refused>(plan.routeDecision).diagnostic.code)
            assertTrue(plan.pass.invocations.isEmpty())
        }
    }

    /** FillRect blur metadata identifies an executable mask-blur route. */
    @Test
    fun `fill rect with blur mask filter builds executable mask blur route`() {
        val command = GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(26),
            rect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        ).copy(
            maskFilter = NormalizedMaskFilter.Blur(
                style = NormalizedBlurStyle.NORMAL,
                sigma = 4f,
            ),
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)
        val routeDecision = assertIs<GPURouteDecision.Prepared>(plan.routeDecision)
        val analysisDecision = assertIs<GPUDrawAnalysisDecision.Candidate>(plan.analysisDecision)

        assertEquals("executable.fill_rect.mask_blur", analysisDecision.routeDecisionLabel)
        assertEquals("mask-blur.rect-fill", routeDecision.route.consumerKind)
        assertEquals("rect.fill.mask_blur", plan.pass.invocations.single().renderStepId.value)
        assertEquals(listOf("mask-blur.rect-fill.rgba8unorm.src_over"), plan.pass.pipelineKeys)
        assertFalse(plan.pass.pipelineKeys.single().startsWith("pending."))
        assertContains(routeDecision.route.invalidationFacts, "requested-sigma=4.0")
        assertContains(routeDecision.route.invalidationFacts, "normalized-style=normal")
        assertContains(routeDecision.route.invalidationFacts, "kanvas.surface.gpu.GPUMaskBlurDispatch")
    }

    /** Accepted solid FillRRect produces pre-materialization rrect analysis, native route, and pass records only. */
    @Test
    fun `solid fill rrect builds native route and draw pass without materialized resources`() {
        val command = GPUFillRRectCommandBuilder.build(
            commandId = GPUDrawCommandID(14),
            rrect = GPURRect(
                rect = GPURect(left = 2f, top = 3f, right = 22f, bottom = 25f),
                radiusX = 4f,
                radiusY = 5f,
            ),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceRRectCapabilities()).plan(command)
        val routeDecision = assertIs<GPURouteDecision.Native>(plan.routeDecision)
        val analysisDecision = assertIs<GPUDrawAnalysisDecision.Candidate>(plan.analysisDecision)
        val invocation = plan.pass.invocations.single()

        assertEquals("analysis.fill_rrect.14", plan.analysisRecord.recordId)
        assertEquals("FillRRect", plan.analysisRecord.commandFamily)
        assertEquals("native.fill_rrect.solid", analysisDecision.routeDecisionLabel)
        assertEquals("native.fill_rrect.solid", routeDecision.route.consumerKind)
        assertEquals("rrect.fill.coverage", routeDecision.route.renderStepIdentity)
        assertEquals(listOf("first_slice.fill_rrect.native"), routeDecision.route.requirements)
        assertContains(
            plan.analysisRecord.diagnostics.map { it.code },
            "geometry:rrect.corner_radii=tl(4.0,5.0);tr(4.0,5.0);br(4.0,5.0);bl(4.0,5.0)",
        )
        assertEquals(emptyList(), analysisDecision.resourceDeclarations)
        assertEquals("pass.root.14", plan.pass.passId)
        assertEquals(listOf("pending.pipeline.fill_rrect.solid.rgba8unorm.src_over"), plan.pass.pipelineKeys)
        assertEquals("pending.pipeline.fill_rrect.solid.rgba8unorm.src_over", invocation.pipelineKeyHash)
        assertEquals("analysis.fill_rrect.14", invocation.analysisRecordId)
        assertEquals(14, invocation.commandIdValue)
        assertEquals("rrect.fill.coverage", invocation.renderStepId.value)
        assertEquals("bounds:2.0,3.0,22.0,25.0", invocation.boundsHash)
        assertNull(invocation.scissorBoundsHash)
        assertNull(invocation.uniformSlot)
        assertNull(invocation.resourceSlot)
    }

    @Test
    fun `solid fill drrect keeps its dedicated route and analytic-hole diagnostic identities`() {
        val outer = GPURRect(GPURect(2f, 2f, 26f, 22f), radiusX = 4f, radiusY = 4f)
        val command = NormalizedDrawCommand.FillDRRect(
            commandId = GPUDrawCommandID(141),
            outer = outer,
            inner = GPURRect(GPURect(8f, 8f, 20f, 16f), radiusX = 2f, radiusY = 2f),
            transform = GPUTransformFacts.identity(),
            clip = GPUClipFacts.wideOpen(GPUBounds(2f, 2f, 26f, 22f)),
            layer = GPULayerFacts.root(GPUTargetFacts(32, 24, "rgba8unorm")),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
            bounds = GPUBounds(2f, 2f, 26f, 22f),
            ordering = GPUOrderingFacts(0, dependsOnDestination = false, requiresBarrier = false),
            source = GPUCommandSource(adapter = "unit-test", operation = "fillDRRect"),
            antiAlias = false,
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceRRectCapabilities()).plan(command)
        val route = assertIs<GPURouteDecision.Native>(plan.routeDecision).route

        assertEquals("analysis.fill_drrect.141", plan.analysisRecord.recordId)
        assertEquals("native.fill_drrect.solid_analytic_hole", plan.analysisRecord.routeDecisionLabel)
        assertEquals("native.fill_drrect.solid_analytic_hole", assertIs<GPUDrawAnalysisDecision.Candidate>(plan.analysisDecision).routeDecisionLabel)
        assertEquals("route.fill_drrect.141", route.routeId)
        assertEquals("native.fill_drrect.solid_analytic_hole", route.consumerKind)
        assertEquals(listOf("first_slice.fill_drrect.native"), route.requirements)
    }

    @Test
    fun `drrect refuses non solid translucent and runtime-effect materials without throwing`() {
        val cases = listOf(
            GPUMaterialDescriptor.ImageDraw(imageSourceId = "not-solid") to
                "unsupported.core_primitive.drrect.analytic_material",
            GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 0.5f) to
                "unsupported.core_primitive.drrect.analytic_alpha",
            GPUMaterialDescriptor.RuntimeEffect("runtime.not_admitted") to
                "unsupported.core_primitive.drrect.analytic_material",
        )

        cases.forEachIndexed { index, (material, expectedCode) ->
            val plan = GPUFirstRoutePlanner(capabilities = firstSliceRRectCapabilities())
                .plan(analyticDRRectCommand(commandId = 145 + index, material = material))

            assertEquals(expectedCode, assertIs<GPUDrawAnalysisDecision.Refuse>(plan.analysisDecision).diagnostic.code)
            assertEquals(expectedCode, assertIs<GPURouteDecision.Refused>(plan.routeDecision).diagnostic.code)
            assertEquals(expectedCode, plan.pass.diagnostics.single().code)
            assertTrue(plan.pass.invocations.isEmpty())
            assertTrue(plan.pass.pipelineKeys.isEmpty())
        }
    }

    @Test
    fun `drrect requires its dedicated native capability fact`() {
        val capabilitiesWithoutDRRect = firstSliceRRectCapabilities().copy(
            facts = firstSliceRRectCapabilities().facts.filterNot {
                it.name == "first_slice.fill_drrect.native"
            },
        )

        val plan = GPUFirstRoutePlanner(capabilities = capabilitiesWithoutDRRect)
            .plan(analyticDRRectCommand(commandId = 149))

        assertEquals(
            "unsupported.pipeline.capability_missing",
            assertIs<GPUDrawAnalysisDecision.Refuse>(plan.analysisDecision).diagnostic.code,
        )
        assertEquals(
            "unsupported.pipeline.capability_missing",
            assertIs<GPURouteDecision.Refused>(plan.routeDecision).diagnostic.code,
        )
    }

    @Test
    fun `fill rrect seals raw source normalized geometry and transform in one opaque authority`() {
        val target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
        val command = GPUFillRRectCommandBuilder.build(
            commandId = GPUDrawCommandID(114),
            rrect = GPURRect(
                rect = GPURect(left = 2f, top = 3f, right = 14f, bottom = 13f),
                topLeft = GPURRectCornerRadii(x = 8f, y = 2f),
                topRight = GPURRectCornerRadii(x = 8f, y = 6f),
                bottomRight = GPURRectCornerRadii(x = 4f, y = 6f),
                bottomLeft = GPURRectCornerRadii(x = 2f, y = 2f),
            ),
            target = target,
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        )

        val plan = GPUFirstRoutePlanner(firstSliceRRectCapabilities()).plan(command)
        val authority = requireNotNull(plan.analysisRecord.corePrimitiveRRectGeometryAuthority)

        assertTrue(authority.matchesCorePrimitiveRRectGeometry(command.rrect, command.transform))
        assertFalse(
            authority.matchesCorePrimitiveRRectGeometry(
                command.rrect.copy(topLeft = command.rrect.topLeft.copy(x = 7f)),
                command.transform,
            ),
        )
        assertFalse(
            authority.matchesCorePrimitiveRRectGeometry(
                command.rrect,
                command.transform.copy(translateX = 1f),
            ),
        )

        val sameNormalizedButDifferentSource = command.copy(
            commandId = GPUDrawCommandID(116),
            rrect = command.rrect.copy(
                topLeft = command.rrect.topLeft.copy(
                    x = command.rrect.topLeft.x * 2f,
                    y = command.rrect.topLeft.y * 2f,
                ),
                topRight = command.rrect.topRight.copy(
                    x = command.rrect.topRight.x * 2f,
                    y = command.rrect.topRight.y * 2f,
                ),
                bottomRight = command.rrect.bottomRight.copy(
                    x = command.rrect.bottomRight.x * 2f,
                    y = command.rrect.bottomRight.y * 2f,
                ),
                bottomLeft = command.rrect.bottomLeft.copy(
                    x = command.rrect.bottomLeft.x * 2f,
                    y = command.rrect.bottomLeft.y * 2f,
                ),
            ),
        )
        val otherAuthority = requireNotNull(
            GPUFirstRoutePlanner(firstSliceRRectCapabilities())
                .plan(sameNormalizedButDifferentSource)
                .analysisRecord
                .corePrimitiveRRectGeometryAuthority,
        )
        assertEquals(authority.sealedDeviceGeometryInput(), otherAuthority.sealedDeviceGeometryInput())
        assertNotEquals(authority, otherAuthority)
    }

    @Test
    fun `valid scaled rrect retains geometry authority while malformed rrect does not`() {
        val target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
        val validRefused = firstRRectRouteCommand(
            target = target,
            transform = GPUTransformFacts.scale(x = 2f, y = 2f),
        )
        val malformed = firstRRectRouteCommand(
            target = target,
            rrect = firstRouteRRect.copy(
                bottomRight = firstRouteRRect.bottomRight.copy(x = -1f),
            ),
        )

        val validScalePlan = GPUFirstRoutePlanner(validRefused.capabilities).plan(
            validRefused.command.copy(antiAlias = false, maskFilter = null),
        )
        val malformedPlan = GPUFirstRoutePlanner(malformed.capabilities).plan(malformed.command)

        assertIs<GPURouteDecision.Native>(validScalePlan.routeDecision)
        assertNotNull(validScalePlan.analysisRecord.corePrimitiveRRectGeometryAuthority)
        assertIs<GPURouteDecision.Refused>(malformedPlan.routeDecision)
        assertNull(malformedPlan.analysisRecord.corePrimitiveRRectGeometryAuthority)
    }

    @Test
    fun `scaled rrect remains refused outside the solid non aa unfiltered route`() {
        val target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
        val solid = firstRRectRouteCommand(
            target = target,
            transform = GPUTransformFacts.scale(2f, 3f),
        ).command.copy(antiAlias = false, maskFilter = null)
        val linearGradient = GPUMaterialDescriptor.LinearGradient(
            startX = 0f, startY = 0f, endX = 16f, endY = 16f,
            startR = 1f, startG = 0f, startB = 0f, startA = 1f,
            endR = 0f, endG = 0f, endB = 1f, endA = 1f,
        )
        val cases = listOf(
            "anti-alias" to solid.copy(antiAlias = true),
            "linear-gradient" to solid.copy(material = linearGradient),
            "mask-blur" to solid.copy(
                maskFilter = NormalizedMaskFilter.Blur(NormalizedBlurStyle.NORMAL, sigma = 2f),
            ),
        )

        cases.forEach { (label, command) ->
            val plan = GPUFirstRoutePlanner(firstSliceRRectWithLinearGradientCapabilities()).plan(command)

            assertEquals(
                "unsupported.transform.rrect_scale_unproven",
                assertIs<GPURouteDecision.Refused>(plan.routeDecision, label).diagnostic.code,
                label,
            )
        }
    }

    @Test
    fun `rrect authority refuses incoherent transform facts and invalid device geometry before signing`() {
        val target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
        val collapsedSource = GPURRect(
            rect = GPURect(1f, 0f, Math.nextUp(1f), 10f),
            radiusX = 0f,
            radiusY = 0f,
        )
        val cases = listOf(
            Triple(
                "identity-hidden-scale",
                "invalid.core_primitive.rrect.transform_facts",
                firstRRectRouteCommand(
                    target,
                    transform = GPUTransformFacts(GPUTransformType.Identity, scaleX = 2f),
                ),
            ),
            Triple(
                "identity-hidden-skew",
                "invalid.core_primitive.rrect.transform_facts",
                firstRRectRouteCommand(
                    target,
                    transform = GPUTransformFacts(GPUTransformType.Identity, skewX = 0.25f),
                ),
            ),
            Triple(
                "translate-hidden-scale",
                "invalid.core_primitive.rrect.transform_facts",
                firstRRectRouteCommand(
                    target,
                    transform = GPUTransformFacts(
                        GPUTransformType.Translate,
                        translateX = 4f,
                        scaleX = 2f,
                    ),
                ),
            ),
            Triple(
                "translate-hidden-skew",
                "invalid.core_primitive.rrect.transform_facts",
                firstRRectRouteCommand(
                    target,
                    transform = GPUTransformFacts(
                        GPUTransformType.Translate,
                        translateX = 4f,
                        skewY = 0.25f,
                    ),
                ),
            ),
            Triple(
                "identity-signed-zero",
                "invalid.core_primitive.rrect.transform_facts",
                firstRRectRouteCommand(
                    target,
                    transform = GPUTransformFacts(GPUTransformType.Identity, translateX = -0f),
                ),
            ),
            Triple(
                "scale-non-finite-determinant",
                "invalid.core_primitive.rrect.transform_facts",
                firstRRectRouteCommand(
                    target,
                    transform = GPUTransformFacts.scale(Float.MAX_VALUE, Float.MAX_VALUE),
                ),
            ),
            Triple(
                "affine-zero-determinant",
                "invalid.core_primitive.rrect.transform_facts",
                firstRRectRouteCommand(
                    target,
                    transform = GPUTransformFacts.affine(1f, 1f, 1f, 1f),
                ),
            ),
            Triple(
                "non-finite-coefficient",
                "unsupported.transform.non_finite",
                firstRRectRouteCommand(
                    target,
                    transform = GPUTransformFacts.translation(Float.POSITIVE_INFINITY, 0f),
                ),
            ),
            Triple(
                "scale-zero-determinant",
                "invalid.core_primitive.rrect.transform_facts",
                firstRRectRouteCommand(
                    target,
                    transform = GPUTransformFacts.scale(0f, 1f),
                ),
            ),
            Triple(
                "translation-overflow",
                "invalid.core_primitive.rrect.device_geometry",
                firstRRectRouteCommand(
                    target,
                    transform = GPUTransformFacts.translation(Float.MAX_VALUE, 0f),
                ),
            ),
            Triple(
                "scale-collapse",
                "invalid.core_primitive.rrect.device_geometry",
                firstRRectRouteCommand(
                    target,
                    rrect = collapsedSource,
                    transform = GPUTransformFacts.scale(Float.MIN_VALUE, 1f),
                ),
            ),
            Triple(
                "affine-skew",
                "unsupported.transform.rrect_affine_unproven",
                firstRRectRouteCommand(
                    target,
                    transform = GPUTransformFacts.affine(1f, 0.25f, 0f, 1f),
                ),
            ),
            Triple(
                "perspective",
                "unsupported.transform.perspective",
                firstRRectRouteCommand(target, transform = GPUTransformFacts.perspective()),
            ),
            Triple(
                "singular",
                "unsupported.transform.singular",
                firstRRectRouteCommand(target, transform = GPUTransformFacts.singular()),
            ),
        )

        cases.forEach { (label, expectedCode, fixture) ->
            val plan = GPUFirstRoutePlanner(fixture.capabilities).plan(fixture.command)

            assertEquals(
                expectedCode,
                assertIs<GPURouteDecision.Refused>(plan.routeDecision, label).diagnostic.code,
                label,
            )
            assertNull(
                plan.analysisRecord.corePrimitiveRRectGeometryAuthority,
                "$label must not retain a partial device authority",
            )
        }

        val validScale = firstRRectRouteCommand(
            target,
            transform = GPUTransformFacts.scale(2f, 3f),
        )
        val validScalePlan = GPUFirstRoutePlanner(validScale.capabilities).plan(
            validScale.command.copy(antiAlias = false, maskFilter = null),
        )
        val routeDecision = assertIs<GPURouteDecision.Native>(validScalePlan.routeDecision)
        assertEquals("native.fill_rrect.solid", routeDecision.route.consumerKind)
        assertNotNull(validScalePlan.analysisRecord.corePrimitiveRRectGeometryAuthority)
    }

    /** FillRRect blur metadata identifies an executable mask-blur route. */
    @Test
    fun `fill rrect with blur mask filter builds executable mask blur route`() {
        val command = GPUFillRRectCommandBuilder.build(
            commandId = GPUDrawCommandID(28),
            rrect = GPURRect(
                rect = GPURect(left = 2f, top = 3f, right = 22f, bottom = 25f),
                radiusX = 4f,
                radiusY = 5f,
            ),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        ).copy(
            maskFilter = NormalizedMaskFilter.Blur(
                style = NormalizedBlurStyle.OUTER,
                sigma = 5f,
            ),
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceRRectCapabilities()).plan(command)
        val routeDecision = assertIs<GPURouteDecision.Prepared>(plan.routeDecision)
        val analysisDecision = assertIs<GPUDrawAnalysisDecision.Candidate>(plan.analysisDecision)

        assertEquals("executable.fill_rrect.mask_blur", analysisDecision.routeDecisionLabel)
        assertEquals("mask-blur.rrect-fill", routeDecision.route.consumerKind)
        assertEquals("rrect.fill.mask_blur", plan.pass.invocations.single().renderStepId.value)
        assertEquals(listOf("mask-blur.rrect-fill.rgba8unorm.src_over"), plan.pass.pipelineKeys)
        assertFalse(plan.pass.pipelineKeys.single().startsWith("pending."))
        assertContains(routeDecision.route.invalidationFacts, "requested-sigma=5.0")
        assertContains(routeDecision.route.invalidationFacts, "normalized-style=outer")
        assertContains(routeDecision.route.invalidationFacts, "kanvas.surface.gpu.GPUMaskBlurDispatch")
    }

    /** Accepted non-uniform rrect radii are captured deterministically before materialization. */
    @Test
    fun `solid fill rrect records per corner radii facts deterministically`() {
        val command = GPUFillRRectCommandBuilder.build(
            commandId = GPUDrawCommandID(15),
            rrect = GPURRect(
                rect = GPURect(left = 2f, top = 3f, right = 42f, bottom = 53f),
                topLeft = GPURRectCornerRadii(x = 3f, y = 4f),
                topRight = GPURRectCornerRadii(x = 5f, y = 6f),
                bottomRight = GPURRectCornerRadii(x = 7f, y = 8f),
                bottomLeft = GPURRectCornerRadii(x = 9f, y = 10f),
            ),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceRRectCapabilities()).plan(command)

        assertIs<GPURouteDecision.Native>(plan.routeDecision)
        assertContains(
            plan.analysisRecord.diagnostics.map { it.code },
            "geometry:rrect.corner_radii=tl(3.0,4.0);tr(5.0,6.0);br(7.0,8.0);bl(9.0,10.0)",
        )
    }

    @Test
    fun `solid fill rrect accepts Skia normalized overlapping radii and records exact facts`() {
        val command = GPUFillRRectCommandBuilder.build(
            commandId = GPUDrawCommandID(115),
            rrect = GPURRect(
                rect = GPURect(left = 2f, top = 3f, right = 14f, bottom = 13f),
                topLeft = GPURRectCornerRadii(x = 8f, y = 2f),
                topRight = GPURRectCornerRadii(x = 8f, y = 6f),
                bottomRight = GPURRectCornerRadii(x = 4f, y = 6f),
                bottomLeft = GPURRectCornerRadii(x = 2f, y = 2f),
            ),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceRRectCapabilities()).plan(command)

        assertIs<GPURouteDecision.Native>(plan.routeDecision)
        assertContains(
            plan.analysisRecord.diagnostics.map { it.code },
            "geometry:rrect.corner_radii=tl(6.0,1.5);tr(6.0,4.5);br(3.0,4.5);bl(1.5,1.5)",
        )
        assertContains(plan.analysisRecord.diagnostics.map { it.code }, "geometry:rrect.radius_scale=0.75")
    }

    @Test
    fun `solid fill rrect squares zero component corners and scales oversized radii`() {
        val target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
        val squareCorner = firstRRectRouteCommand(
            target = target,
            rrect = firstRouteRRect.copy(topLeft = firstRouteRRect.topLeft.copy(x = 0f)),
        )
        val oversizedCorner = firstRRectRouteCommand(
            target = target,
            rrect = firstRouteRRect.copy(bottomLeft = firstRouteRRect.bottomLeft.copy(x = 99f)),
        )

        val squareCornerPlan = GPUFirstRoutePlanner(capabilities = squareCorner.capabilities).plan(squareCorner.command)
        val oversizedCornerPlan = GPUFirstRoutePlanner(capabilities = oversizedCorner.capabilities).plan(oversizedCorner.command)

        assertIs<GPURouteDecision.Native>(squareCornerPlan.routeDecision)
        assertContains(
            squareCornerPlan.analysisRecord.diagnostics.map { it.code },
            "geometry:rrect.corner_radii=tl(0.0,0.0);tr(4.0,5.0);br(4.0,5.0);bl(4.0,5.0)",
        )
        assertIs<GPURouteDecision.Native>(oversizedCornerPlan.routeDecision)
        assertTrue(
            oversizedCornerPlan.analysisRecord.diagnostics.any {
                it.code.startsWith("geometry:rrect.radius_scale=")
            },
        )
    }

    @Test
    fun `finite pure translated rrects are admitted through winding and inverse winding analytic hard path clip consumers`() {
        val target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
        val identityStencil = hardWindingStencilClip(pathTransformClass = "identity")
        val accepted = listOf(
            GPUTransformFacts.translation(x = 4f, y = 0f) to listOf(6f, 3f, 26f, 25f),
            GPUTransformFacts.translation(x = 0f, y = 5f) to listOf(2f, 8f, 22f, 30f),
            GPUTransformFacts.translation(x = -4f, y = 5f) to listOf(-2f, 8f, 18f, 30f),
            GPUTransformFacts.translation(x = 4f, y = -5f) to listOf(6f, -2f, 26f, 20f),
        )
        accepted.forEach { (transform, bounds) ->
            val fixture = firstRRectRouteCommand(target = target, transform = transform, clip = identityStencil)
            val plan = GPUFirstRoutePlanner(fixture.capabilities).plan(fixture.command.copy(antiAlias = false))
            assertIs<GPURouteDecision.Native>(plan.routeDecision)
            val device = assertNotNull(plan.analysisRecord.corePrimitiveRRectGeometryAuthority).sealedDeviceGeometryInput()
            assertEquals(bounds[0], device.left)
            assertEquals(bounds[1], device.top)
            assertEquals(bounds[2], device.right)
            assertEquals(bounds[3], device.bottom)
        }

        accepted.forEach { (transform, bounds) ->
            val fixture = firstRRectRouteCommand(
                target = target,
                transform = transform,
                clip = hardWindingStencilClip(pathTransformClass = "identity", inverseFill = true),
            )
            val plan = GPUFirstRoutePlanner(fixture.capabilities).plan(fixture.command.copy(antiAlias = false))
            assertIs<GPURouteDecision.Native>(plan.routeDecision)
            val device = assertNotNull(plan.analysisRecord.corePrimitiveRRectGeometryAuthority).sealedDeviceGeometryInput()
            assertEquals(bounds, listOf(device.left, device.top, device.right, device.bottom))
        }

        val refusals = listOf(
            Triple(GPUTransformFacts.translation(x = 0f, y = 0f), identityStencil, "unsupported.clip.complex_stack"),
            Triple(GPUTransformFacts.translation(x = Float.NaN, y = 5f), identityStencil, "unsupported.transform.non_finite"),
            Triple(GPUTransformFacts.scale(x = 2f, y = 2f), identityStencil, "unsupported.clip.complex_stack"),
            Triple(GPUTransformFacts.affine(1f, 0.25f, 0f, 1f), identityStencil, "unsupported.transform.rrect_affine_unproven"),
            Triple(GPUTransformFacts.translation(x = 4f, y = 0f), hardWindingStencilClip(pathTransformClass = "translate"), "unsupported.clip.complex_stack"),
            Triple(GPUTransformFacts.translation(x = 4f, y = 0f), hardWindingStencilClip(
                pathTransformClass = "identity",
                inverseFill = true,
                fillRule = GPUClipFillRule.EvenOdd,
            ), "unsupported.clip.complex_stack"),
        )
        refusals.forEach { (transform, clip, expectedCode) ->
            val fixture = firstRRectRouteCommand(target = target, transform = transform, clip = clip)
            val refused = GPUFirstRoutePlanner(fixture.capabilities).plan(fixture.command.copy(antiAlias = false))
            assertEquals(
                expectedCode,
                assertIs<GPURouteDecision.Refused>(refused.routeDecision).diagnostic.code,
            )
        }
    }

    @Test
    fun `finite non-zero translated drrect is admitted by the analytic hard path clip consumer`() {
        val target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
        val identityStencil = hardWindingStencilClip(pathTransformClass = "identity")
        val translations = listOf(
            GPUTransformFacts.translation(x = 4f, y = 0f) to listOf(6f, 2f, 30f, 22f, 12f, 8f, 24f, 16f),
            GPUTransformFacts.translation(x = 0f, y = 5f) to listOf(2f, 7f, 26f, 27f, 8f, 13f, 20f, 21f),
            GPUTransformFacts.translation(x = -4f, y = 5f) to listOf(-2f, 7f, 22f, 27f, 4f, 13f, 16f, 21f),
            GPUTransformFacts.translation(x = 4f, y = -5f) to listOf(6f, -3f, 30f, 17f, 12f, 3f, 24f, 11f),
        )
        translations.forEachIndexed { index, (transform, bounds) ->
            val accepted = GPUFirstRoutePlanner(firstSliceRRectCapabilities()).plan(
                analyticDRRectCommand(commandId = 151 + index).copy(
                    transform = transform, clip = identityStencil, layer = GPULayerFacts.root(target),
                ),
            )
            assertIs<GPURouteDecision.Native>(accepted.routeDecision)
            val outer = assertNotNull(accepted.analysisRecord.corePrimitiveDRRectOuterGeometryAuthority).sealedDeviceGeometryInput()
            val inner = assertNotNull(accepted.analysisRecord.corePrimitiveDRRectInnerGeometryAuthority).sealedDeviceGeometryInput()
            assertEquals(bounds, listOf(outer.left, outer.top, outer.right, outer.bottom, inner.left, inner.top, inner.right, inner.bottom))
        }
        val translated = analyticDRRectCommand(commandId = 155).copy(
            transform = GPUTransformFacts.translation(x = 4f, y = 0f), clip = identityStencil, layer = GPULayerFacts.root(target),
        )

        val transformRefusals = listOf(
            Triple(translated.copy(clip = GPUClipFacts.wideOpen(firstRouteBounds)), "wide-open translated", "unsupported.core_primitive.drrect.analytic_transform"),
            Triple(translated.copy(transform = GPUTransformFacts.translation(x = 0f, y = 0f)), "zero translation", "unsupported.core_primitive.drrect.analytic_transform"),
            Triple(translated.copy(transform = GPUTransformFacts.translation(x = Float.NaN, y = 5f)), "non-finite translation", "unsupported.transform.non_finite"),
            Triple(translated.copy(transform = GPUTransformFacts.scale(x = 2f, y = 2f)), "scale", "unsupported.core_primitive.drrect.analytic_transform"),
            Triple(translated.copy(transform = GPUTransformFacts.affine(1f, 0.25f, 0f, 1f)), "affine", "unsupported.core_primitive.drrect.analytic_transform"),
        )
        transformRefusals.forEach { (command, label, expectedCode) ->
            val refused = GPUFirstRoutePlanner(firstSliceRRectCapabilities()).plan(command)
            assertEquals(
                expectedCode,
                assertIs<GPURouteDecision.Refused>(refused.routeDecision, label).diagnostic.code,
            )
        }

        val transformedClip = translated.copy(clip = hardWindingStencilClip(pathTransformClass = "translate"))
        assertEquals(
            "unsupported.core_primitive.drrect.analytic_clip",
            assertIs<GPURouteDecision.Refused>(
                GPUFirstRoutePlanner(firstSliceRRectCapabilities()).plan(transformedClip).routeDecision,
            ).diagnostic.code,
        )

        val inverseTranslated = translated.copy(
            clip = hardWindingStencilClip(pathTransformClass = "identity", inverseFill = true),
        )
        val inverseTranslatedPlan = GPUFirstRoutePlanner(firstSliceRRectCapabilities()).plan(inverseTranslated)
        assertEquals(
            "unsupported.core_primitive.drrect.analytic_clip",
            assertIs<GPURouteDecision.Refused>(inverseTranslatedPlan.routeDecision).diagnostic.code,
        )

        val identityInverse = inverseTranslated.copy(transform = GPUTransformFacts.identity())
        assertIs<GPURouteDecision.Native>(GPUFirstRoutePlanner(firstSliceRRectCapabilities()).plan(identityInverse).routeDecision)

        val differenceTranslated = translated.copy(
            clip = hardWindingStencilClip(pathTransformClass = "identity", consumerInverseFill = true),
        )
        assertEquals(
            "unsupported.core_primitive.drrect.analytic_clip",
            assertIs<GPURouteDecision.Refused>(
                GPUFirstRoutePlanner(firstSliceRRectCapabilities()).plan(differenceTranslated).routeDecision,
            ).diagnostic.code,
        )
    }

    /** Accepted FillRRect with LinearGradient material routes natively with gradient render step. */
    @Test
    fun `linear gradient fill rrect routes natively with gradient step and pipeline key`() {
        val command = GPUFillRRectCommandBuilder.build(
            commandId = GPUDrawCommandID(24),
            rrect = GPURRect(
                rect = GPURect(left = 2f, top = 3f, right = 22f, bottom = 25f),
                radiusX = 4f,
                radiusY = 5f,
            ),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.LinearGradient(
                startX = 2f, startY = 3f, endX = 18f, endY = 21f,
                startR = 1f, startG = 0.25f, startB = 0.5f, startA = 1f,
                endR = 0f, endG = 0.75f, endB = 0.5f, endA = 1f,
            ),
        )

        val plan = GPUFirstRoutePlanner(
            capabilities = firstSliceRRectWithLinearGradientCapabilities(),
        ).plan(command)
        val routeDecision = assertIs<GPURouteDecision.Native>(plan.routeDecision)
        val analysisDecision = assertIs<GPUDrawAnalysisDecision.Candidate>(plan.analysisDecision)
        val invocation = plan.pass.invocations.single()

        assertEquals("native.fill_rrect.linear_gradient", routeDecision.route.consumerKind)
        assertEquals("linear.gradient.fill", routeDecision.route.renderStepIdentity)
        assertEquals(
            listOf("first_slice.linear_gradient.native"),
            routeDecision.route.requirements,
        )
        assertEquals("native.fill_rrect.linear_gradient", analysisDecision.routeDecisionLabel)
        assertEquals(listOf("linear.gradient.fill"), analysisDecision.renderStepCandidates)
        assertEquals(
            listOf("pending.pipeline.fill_rrect.linear_gradient.rgba8unorm.src_over"),
            plan.pass.pipelineKeys,
        )
        assertEquals(
            "pending.pipeline.fill_rrect.linear_gradient.rgba8unorm.src_over",
            invocation.pipelineKeyHash,
        )
        assertEquals("linear.gradient.fill", invocation.renderStepId.value)
        assertEquals("pending.material.linear_gradient", plan.analysisRecord.materialKeyHash)
    }

    /** FillRRect with LinearGradient material refuses when the linear gradient capability is missing. */
    @Test
    fun `linear gradient fill rrect without capability refuses diagnostically`() {
        val command = GPUFillRRectCommandBuilder.build(
            commandId = GPUDrawCommandID(25),
            rrect = GPURRect(
                rect = GPURect(left = 2f, top = 3f, right = 22f, bottom = 25f),
                radiusX = 4f,
                radiusY = 5f,
            ),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.LinearGradient(
                startX = 2f, startY = 3f, endX = 18f, endY = 21f,
                startR = 1f, startG = 0.25f, startB = 0.5f, startA = 1f,
                endR = 0f, endG = 0.75f, endB = 0.5f, endA = 1f,
            ),
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceRRectCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals(
            "unsupported.material.linear_gradient_capability_missing",
            plan.pass.diagnostics.single().code,
        )
    }

    /** Unsupported rrect variants refuse with canonical diagnostics and no pass work. */
    @Test
    fun `unsupported fill rrect variants produce canonical refusal diagnostics`() {
        val target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
        val cases = listOf(
            "unsupported.geometry.rrect_bounds" to firstRRectRouteCommand(
                target = target,
                rrect = firstRouteRRect.copy(
                    rect = firstRouteRRect.rect.copy(right = firstRouteRRect.rect.left),
                ),
            ),
            "unsupported.geometry.rrect_bounds" to firstRRectRouteCommand(
                target = target,
                rrect = firstRouteRRect.copy(
                    rect = firstRouteRRect.rect.copy(right = firstRouteRRect.rect.left - 1f),
                ),
            ),
            "unsupported.geometry.rrect_bounds" to firstRRectRouteCommand(
                target = target,
                rrect = firstRouteRRect.copy(rect = firstRouteRRect.rect.copy(left = Float.NaN)),
            ),
            "unsupported.geometry.rrect_radii_non_finite" to firstRRectRouteCommand(
                target = target,
                rrect = firstRouteRRect.copy(topRight = firstRouteRRect.topRight.copy(y = Float.POSITIVE_INFINITY)),
            ),
            "unsupported.geometry.rrect_radii_non_finite" to firstRRectRouteCommand(
                target = target,
                rrect = firstRouteRRect.copy(topLeft = firstRouteRRect.topLeft.copy(x = Float.NaN)),
            ),
            "unsupported.geometry.rrect_radii_negative" to firstRRectRouteCommand(
                target = target,
                rrect = firstRouteRRect.copy(bottomRight = firstRouteRRect.bottomRight.copy(x = -1f)),
            ),
            "unsupported.transform.rrect_affine_unproven" to firstRRectRouteCommand(
                target = target,
                transform = GPUTransformFacts.affine(scaleX = 1f, skewX = 0.25f, skewY = 0f, scaleY = 1f),
            ),
            "unsupported.transform.perspective" to firstRRectRouteCommand(
                target = target,
                transform = GPUTransformFacts.perspective(),
            ),
            "unsupported.clip.complex_stack" to firstRRectRouteCommand(
                target = target,
                clip = GPUClipFacts.complexStack(bounds = firstRouteBounds),
            ),
            "unsupported.target.format_blend_incompatible" to firstRRectRouteCommand(
                target = target.copy(colorFormat = "bgra8unorm-srgb"),
            ),
            "unsupported.pipeline.capability_missing" to firstRRectRouteCommand(
                target = target,
                capabilities = emptyCapabilities(),
            ),
        )

        for ((expectedCode, fixture) in cases) {
            val plan = GPUFirstRoutePlanner(capabilities = fixture.capabilities).plan(fixture.command)
            val routeDecision = assertIs<GPURouteDecision.Refused>(plan.routeDecision)
            val analysisDecision = assertIs<GPUDrawAnalysisDecision.Refuse>(plan.analysisDecision)

            assertEquals(expectedCode, routeDecision.diagnostic.code)
            assertEquals(expectedCode, analysisDecision.diagnostic.code)
            assertEquals(emptyList(), plan.analysisRecord.renderStepCandidates)
            assertEquals(listOf(expectedCode), plan.pass.diagnostics.map { it.code })
            assertEquals(emptyList(), plan.pass.invocations)
            assertEquals(emptyList(), plan.pass.pipelineKeys)
        }
    }

    /** Unsupported first-route variants refuse with canonical diagnostics and no pass work. */
    @Test
    fun `unsupported fill rect variants produce canonical refusal diagnostics`() {
        val target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")
        val cases = listOf(
            "unsupported.transform.non_finite" to firstRouteCommand(
                target = target,
                transform = GPUTransformFacts.translation(x = Float.POSITIVE_INFINITY, y = 0f),
            ),
            "unsupported.bounds.nan" to firstRouteCommand(
                target = target,
                rect = firstRouteRect.copy(left = Float.NaN),
            ),
            "unsupported.bounds.non_finite" to firstRouteCommand(
                target = target,
                clip = GPUClipFacts.deviceRect(
                    bounds = firstRouteBounds.copy(right = Float.NEGATIVE_INFINITY),
                ),
            ),
            "unsupported.transform.perspective" to firstRouteCommand(
                target = target,
                transform = GPUTransformFacts.perspective(),
            ),
            "unsupported.clip.complex_stack" to firstRouteCommand(
                target = target,
                clip = GPUClipFacts.complexStack(bounds = firstRouteBounds),
            ),
            "unsupported.layer.elision_proof_missing" to firstRouteCommand(
                target = target,
                layer = GPULayerFacts.saveLayer(target = target),
            ),
            "unsupported.layer.filter_chain" to firstRouteCommand(
                target = target,
                layer = GPULayerFacts.root(target = target).copy(requiresFilter = true),
            ),
            "unsupported.target.format_blend_incompatible" to firstRouteCommand(
                target = target.copy(colorFormat = "bgra8unorm-srgb"),
            ),
            "unsupported.pipeline.capability_missing" to firstRouteCommand(
                target = target,
                capabilities = emptyCapabilities(),
            ),
        )

        for ((expectedCode, fixture) in cases) {
            val plan = GPUFirstRoutePlanner(capabilities = fixture.capabilities).plan(fixture.command)
            val routeDecision = assertIs<GPURouteDecision.Refused>(plan.routeDecision)
            val analysisDecision = assertIs<GPUDrawAnalysisDecision.Refuse>(plan.analysisDecision)

            assertEquals(expectedCode, routeDecision.diagnostic.code)
            assertEquals(expectedCode, analysisDecision.diagnostic.code)
            assertEquals(emptyList(), plan.analysisRecord.renderStepCandidates)
            assertEquals(listOf(expectedCode), plan.pass.diagnostics.map { it.code })
            assertEquals(emptyList(), plan.pass.invocations)
            assertEquals(emptyList(), plan.pass.pipelineKeys)
        }
    }

    /** Accepted solid FillPath produces pre-materialization CPU-prepared GPU route. */
    @Test
    fun `solid fill path builds prepared CPU route without materialized resources`() {
        val command = GPUFillPathCommandBuilder.build(
            commandId = GPUDrawCommandID(20),
            pathKey = "path:triangle:v1",
            pathDescriptor = GPUPathFacts(
                pathKey = "path:triangle:v1",
                verbCount = 4,
                pointCount = 3,
                fillRule = "NonZero",
                inverseFill = false,
                finiteProof = "finite",
                volatility = "immutable",
                transformClass = "identity",
                edgeCount = 3,
            ),
            tessellatedVertices = listOf(0f, 0f, 16f, 0f, 8f, 16f),
            contourStarts = listOf(0),
            edgeCount = 3,
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSlicePathFillCapabilities()).plan(command)
        val routeDecision = assertIs<GPURouteDecision.Prepared>(plan.routeDecision)
        val analysisDecision = assertIs<GPUDrawAnalysisDecision.Candidate>(plan.analysisDecision)
        val invocation = plan.pass.invocations.single()

        assertEquals("analysis.fill_path.20", plan.analysisRecord.recordId)
        assertEquals("FillPath", plan.analysisRecord.commandFamily)
        assertEquals("prepared.path_fill.tessellated", analysisDecision.routeDecisionLabel)
        assertEquals("coverage-mask.sample.path-fill", routeDecision.route.consumerKind)
        assertEquals("path-fill-tessellation", routeDecision.route.artifactType)
        assertEquals("path.fill.coverage_mask", invocation.renderStepId.value)
        assertEquals(listOf("pending.pipeline.fill_path.tessellated.rgba8unorm.src_over"), plan.pass.pipelineKeys)
        assertEquals("pending.pipeline.fill_path.tessellated.rgba8unorm.src_over", invocation.pipelineKeyHash)
        assertEquals("analysis.fill_path.20", invocation.analysisRecordId)
        assertEquals(20, invocation.commandIdValue)
        assertEquals("path_fill", invocation.role)
        assertEquals("pass.path_fill.20", plan.pass.passId)
        assertNull(invocation.scissorBoundsHash)
    }

    /** Accepted FillPath with linear gradient material builds CPU-prepared GPU route. */
    @Test
    fun `linear gradient fill path builds prepared CPU route`() {
        val command = GPUFillPathCommandBuilder.build(
            commandId = GPUDrawCommandID(21),
            pathKey = "path:triangle:v1",
            pathDescriptor = GPUPathFacts(
                pathKey = "path:triangle:v1",
                verbCount = 4,
                pointCount = 3,
                fillRule = "NonZero",
                inverseFill = false,
                finiteProof = "finite",
                volatility = "immutable",
                transformClass = "identity",
                edgeCount = 3,
            ),
            tessellatedVertices = listOf(0f, 0f, 16f, 0f, 8f, 16f),
            contourStarts = listOf(0),
            edgeCount = 3,
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.LinearGradient(
                startX = 0f, startY = 0f, endX = 16f, endY = 16f,
                startR = 1f, startG = 0f, startB = 0f, startA = 1f,
                endR = 0f, endG = 0f, endB = 1f, endA = 1f,
            ),
        )

        val plan = GPUFirstRoutePlanner(
            capabilities = firstSlicePathFillWithLinearGradientCapabilities(),
        ).plan(command)
        assertIs<GPURouteDecision.Prepared>(plan.routeDecision)
        assertEquals("prepared.path_fill.tessellated", plan.analysisRecord.routeDecisionLabel)
        assertEquals("pending.material.lineargradient", plan.analysisRecord.materialKeyHash)
    }

    /** FillPath promoted to native stencil-cover route when stencil-cover capability is present. */
    @Test
    fun `fill path with stencil cover capability builds native route`() {
        val command = GPUFillPathCommandBuilder.build(
            commandId = GPUDrawCommandID(22),
            pathKey = "path:triangle:v1",
            pathDescriptor = GPUPathFacts(
                pathKey = "path:triangle:v1",
                verbCount = 4,
                pointCount = 3,
                fillRule = "NonZero",
                inverseFill = false,
                finiteProof = "finite",
                volatility = "immutable",
                transformClass = "identity",
                edgeCount = 3,
            ),
            tessellatedVertices = listOf(0f, 0f, 16f, 0f, 8f, 16f),
            contourStarts = listOf(0),
            edgeCount = 3,
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        )

        val plan = GPUFirstRoutePlanner(
            capabilities = firstSlicePathFillStencilCoverCapabilities(),
        ).plan(command)
        val routeDecision = assertIs<GPURouteDecision.Native>(plan.routeDecision)

        assertEquals("native.path_fill.stencil_cover", routeDecision.route.consumerKind)
        assertEquals("path.fill.stencil_cover", routeDecision.route.renderStepIdentity)
        assertEquals(listOf("first_slice.path_fill.stencil_cover"), routeDecision.route.requirements)
        assertEquals("native.path_fill.stencil_cover", plan.analysisRecord.routeDecisionLabel)
    }

    @Test
    fun `stencil cover capability alone does not promote stroke or mask prepared paths`() {
        val base = GPUFillPathCommandBuilder.build(
            commandId = GPUDrawCommandID(122),
            pathKey = "path:triangle:stencil-authority",
            pathDescriptor = GPUPathFacts(
                pathKey = "path:triangle:stencil-authority",
                verbCount = 4,
                pointCount = 3,
                fillRule = "NonZero",
                inverseFill = false,
                finiteProof = "finite",
                volatility = "immutable",
                transformClass = "identity",
                edgeCount = 3,
            ),
            tessellatedVertices = listOf(0f, 0f, 16f, 0f, 8f, 16f),
            contourStarts = listOf(0),
            edgeCount = 3,
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        )
        val variants = listOf(
            base.copy(stroke = true),
            base.copy(
                maskFilter = NormalizedMaskFilter.Blur(
                    style = NormalizedBlurStyle.NORMAL,
                    sigma = 2f,
                ),
            ),
        )

        variants.forEach { command ->
            val plan = GPUFirstRoutePlanner(
                capabilities = firstSlicePathFillStencilCoverCapabilities(),
            ).plan(command)

            assertIs<GPURouteDecision.Refused>(plan.routeDecision)
            assertEquals("unsupported.pipeline.capability_missing", plan.pass.diagnostics.single().code)
        }
    }

    /** FillPath with empty vertices is now accepted (empty non-inverse paths draw nothing). */
    @Test
    fun `fill path with empty vertices accepted as empty draw`() {
        val command = GPUFillPathCommandBuilder.build(
            commandId = GPUDrawCommandID(23),
            pathKey = "path:triangle:v1",
            pathDescriptor = GPUPathFacts(
                pathKey = "path:triangle:v1",
                verbCount = 4,
                pointCount = 3,
                fillRule = "NonZero",
                inverseFill = false,
                finiteProof = "finite",
                volatility = "immutable",
                transformClass = "identity",
                edgeCount = 3,
            ),
            tessellatedVertices = emptyList(),
            contourStarts = emptyList(),
            edgeCount = 0,
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSlicePathFillCapabilities()).plan(command)
        assertIs<GPURouteDecision.Prepared>(plan.routeDecision)
        assertEquals("prepared.path_fill.tessellated", plan.analysisRecord.routeDecisionLabel)
    }

    /** FillPath refuses for unsupported material kinds. */
    @Test
    fun `fill path with unsupported material refuses diagnostically`() {
        val command = GPUFillPathCommandBuilder.build(
            commandId = GPUDrawCommandID(24),
            pathKey = "path:triangle:v1",
            pathDescriptor = GPUPathFacts(
                pathKey = "path:triangle:v1",
                verbCount = 4,
                pointCount = 3,
                fillRule = "NonZero",
                inverseFill = false,
                finiteProof = "finite",
                volatility = "immutable",
                transformClass = "identity",
                edgeCount = 3,
            ),
            tessellatedVertices = listOf(0f, 0f, 16f, 0f, 8f, 16f),
            contourStarts = listOf(0),
            edgeCount = 3,
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.RadialGradient(
                centerX = 8f, centerY = 8f, radius = 8f,
                startR = 1f, startG = 0f, startB = 0f, startA = 1f,
                endR = 0f, endG = 0f, endB = 1f, endA = 1f,
            ),
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSlicePathFillCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.material.source_unimplemented", plan.pass.diagnostics.single().code)
    }

    /** FillPath refuses when path fill capability is missing. */
    @Test
    fun `fill path without path fill capability refuses diagnostically`() {
        val command = GPUFillPathCommandBuilder.build(
            commandId = GPUDrawCommandID(25),
            pathKey = "path:triangle:v1",
            pathDescriptor = GPUPathFacts(
                pathKey = "path:triangle:v1",
                verbCount = 4,
                pointCount = 3,
                fillRule = "NonZero",
                inverseFill = false,
                finiteProof = "finite",
                volatility = "immutable",
                transformClass = "identity",
                edgeCount = 3,
            ),
            tessellatedVertices = listOf(0f, 0f, 16f, 0f, 8f, 16f),
            contourStarts = listOf(0),
            edgeCount = 3,
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        )

        val plan = GPUFirstRoutePlanner(capabilities = emptyCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.pipeline.capability_missing", plan.pass.diagnostics.single().code)
    }

    /** FillPath with basic stroke (butt cap, miter join) builds prepared CPU stroke route. */
    @Test
    fun `fill path with basic stroke builds prepared CPU route`() {
        val command = GPUFillPathCommandBuilder.build(
            commandId = GPUDrawCommandID(26),
            pathKey = "path:triangle:v1",
            pathDescriptor = GPUPathFacts(
                pathKey = "path:triangle:v1",
                verbCount = 4,
                pointCount = 3,
                fillRule = "NonZero",
                inverseFill = false,
                finiteProof = "finite",
                volatility = "immutable",
                transformClass = "identity",
                edgeCount = 3,
            ),
            tessellatedVertices = listOf(0f, 0f, 16f, 0f, 8f, 16f),
            contourStarts = listOf(0),
            edgeCount = 3,
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
            stroke = true,
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSlicePathFillCapabilities()).plan(command)
        val routeDecision = assertIs<GPURouteDecision.Prepared>(plan.routeDecision)
        val analysisDecision = assertIs<GPUDrawAnalysisDecision.Candidate>(plan.analysisDecision)

        assertEquals("analysis.fill_path.26", plan.analysisRecord.recordId)
        assertEquals("FillPath", plan.analysisRecord.commandFamily)
        assertEquals("prepared.path_stroke.tessellated", analysisDecision.routeDecisionLabel)
        assertEquals("stroke-strip.render-step", routeDecision.route.consumerKind)
        assertEquals("stroke-tessellation", routeDecision.route.artifactType)
        assertEquals("path.stroke.tessellated", plan.pass.invocations.single().renderStepId.value)
        assertEquals(listOf("pending.pipeline.fill_stroke.tessellated.rgba8unorm.src_over"), plan.pass.pipelineKeys)
        assertEquals("pending.pipeline.fill_stroke.tessellated.rgba8unorm.src_over", plan.pass.invocations.single().pipelineKeyHash)
        assertEquals("analysis.fill_path.26", plan.pass.invocations.single().analysisRecordId)
        assertEquals(26, plan.pass.invocations.single().commandIdValue)
        assertEquals("path_fill", plan.pass.invocations.single().role)
        assertEquals("pass.path_fill.26", plan.pass.passId)
        assertEquals(
            "prepared.stroke.path_triangle_v1.w1.0.butt.miter.e3",
            routeDecision.route.artifactKey.value,
        )
    }

    /** FillPath stroke analysis consumes the captured miter limit instead of a hard-coded default. */
    @Test
    fun `fill path stroke refuses the captured subminimum miter limit`() {
        val command = GPUFillPathCommandBuilder.build(
            commandId = GPUDrawCommandID(127),
            pathKey = "path:miter:v1",
            pathDescriptor = GPUPathFacts(
                pathKey = "path:miter:v1",
                verbCount = 4,
                pointCount = 3,
                fillRule = "NonZero",
                inverseFill = false,
                finiteProof = "finite",
                volatility = "immutable",
                transformClass = "identity",
                edgeCount = 3,
            ),
            tessellatedVertices = listOf(0f, 0f, 16f, 0f, 8f, 16f),
            contourStarts = listOf(0),
            edgeCount = 3,
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
            stroke = true,
        ).copy(strokeMiterLimit = 0.5f)

        val plan = GPUFirstRoutePlanner(capabilities = firstSlicePathFillCapabilities()).plan(command)

        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.stroke.miter_limit", plan.pass.diagnostics.single().code)
    }

    /** FillPath with stroke and SimpleRepeat dash (≤4 elements) builds prepared CPU stroke route. */
    @Test
    fun `fill path with stroke and simple repeat dash builds prepared CPU route`() {
        val command = GPUFillPathCommandBuilder.build(
            commandId = GPUDrawCommandID(28),
            pathKey = "path:line:v1",
            pathDescriptor = GPUPathFacts(
                pathKey = "path:line:v1",
                verbCount = 2,
                pointCount = 2,
                fillRule = "NonZero",
                inverseFill = false,
                finiteProof = "finite",
                volatility = "immutable",
                transformClass = "identity",
                edgeCount = 1,
            ),
            tessellatedVertices = listOf(0f, 0f, 100f, 0f),
            contourStarts = listOf(0),
            edgeCount = 1,
            target = GPUTargetFacts(width = 128, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0f, b = 0f, a = 1f),
            stroke = true,
            strokeWidth = 6f,
            dashIntervals = floatArrayOf(10f, 10f),
            dashPhase = 0f,
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSlicePathFillCapabilities()).plan(command)
        val routeDecision = assertIs<GPURouteDecision.Prepared>(plan.routeDecision)
        val analysisDecision = assertIs<GPUDrawAnalysisDecision.Candidate>(plan.analysisDecision)

        assertEquals("analysis.fill_path.28", plan.analysisRecord.recordId)
        assertEquals("FillPath", plan.analysisRecord.commandFamily)
        assertEquals("prepared.path_stroke.tessellated", analysisDecision.routeDecisionLabel)
        assertEquals("stroke-strip.render-step", routeDecision.route.consumerKind)
        assertEquals("stroke-tessellation", routeDecision.route.artifactType)
        assertEquals("path.stroke.tessellated", plan.pass.invocations.single().renderStepId.value)
        assertContains(
            routeDecision.route.artifactKey.value,
            "prepared.stroke.path_line_v1.w6.0.butt.miter.d10.0_10.0.e",
        )
    }

    /** FillPath with stroke and ComplexPattern dash (>4 elements) still refused. */
    @Test
    fun `fill path with stroke and complex dash pattern refused`() {
        val command = GPUFillPathCommandBuilder.build(
            commandId = GPUDrawCommandID(29),
            pathKey = "path:line:v1",
            pathDescriptor = GPUPathFacts(
                pathKey = "path:line:v1",
                verbCount = 2,
                pointCount = 2,
                fillRule = "NonZero",
                inverseFill = false,
                finiteProof = "finite",
                volatility = "immutable",
                transformClass = "identity",
                edgeCount = 1,
            ),
            tessellatedVertices = listOf(0f, 0f, 100f, 0f),
            contourStarts = listOf(0),
            edgeCount = 1,
            target = GPUTargetFacts(width = 128, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0f, b = 0f, a = 1f),
            stroke = true,
            strokeWidth = 6f,
            dashIntervals = floatArrayOf(10f, 5f, 3f, 2f, 1f, 1f),
            dashPhase = 0f,
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSlicePathFillCapabilities()).plan(command)
        val routeDecision = assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.stroke.dash_complex", routeDecision.diagnostic.code)
    }

    /** Accepted FillPath with blur MaskFilter builds an executable blur route. */
    @Test
    fun `fill path with blur mask filter builds executable blur mask route`() {
        val command = GPUFillPathCommandBuilder.build(
            commandId = GPUDrawCommandID(27),
            pathKey = "path:triangle:v1",
            pathDescriptor = GPUPathFacts(
                pathKey = "path:triangle:v1",
                verbCount = 4,
                pointCount = 3,
                fillRule = "NonZero",
                inverseFill = false,
                finiteProof = "finite",
                volatility = "immutable",
                transformClass = "identity",
                edgeCount = 3,
            ),
            tessellatedVertices = listOf(0f, 0f, 16f, 0f, 8f, 16f),
            contourStarts = listOf(0),
            edgeCount = 3,
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
            maskFilter = NormalizedMaskFilter.Blur(
                style = NormalizedBlurStyle.NORMAL,
                sigma = 6.2735f,
            ),
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSlicePathFillCapabilities()).plan(command)
        val routeDecision = assertIs<GPURouteDecision.Prepared>(plan.routeDecision)
        val analysisDecision = assertIs<GPUDrawAnalysisDecision.Candidate>(plan.analysisDecision)

        assertEquals("analysis.fill_path.27", plan.analysisRecord.recordId)
        assertEquals("FillPath", plan.analysisRecord.commandFamily)
        assertEquals("executable.path_fill.mask_blur", analysisDecision.routeDecisionLabel)
        assertEquals("mask-blur.path-fill", routeDecision.route.consumerKind)
        assertEquals("path.fill.mask_blur", plan.pass.invocations.single().renderStepId.value)
        assertEquals(listOf("mask-blur.path-fill.rgba8unorm.src_over"), plan.pass.pipelineKeys)
        assertFalse(plan.pass.pipelineKeys.single().startsWith("pending."))
        assertEquals("mask-blur.path-fill.rgba8unorm.src_over", plan.pass.invocations.single().pipelineKeyHash)
        assertEquals("analysis.fill_path.27", plan.pass.invocations.single().analysisRecordId)
        assertEquals(27, plan.pass.invocations.single().commandIdValue)
        assertEquals("path_fill", plan.pass.invocations.single().role)
        assertEquals("pass.path_fill.27", plan.pass.passId)
        assertEquals(
            "blur-mask.path-fill.path_triangle_v1.blur:normal_sigma=6.2735",
            routeDecision.route.artifactKey.value,
        )
        assertContains(routeDecision.route.invalidationFacts, "requested-sigma=6.2735")
        assertContains(routeDecision.route.invalidationFacts, "normalized-style=normal")
        assertContains(routeDecision.route.invalidationFacts, "kanvas.surface.gpu.GPUMaskBlurDispatch")
    }

    /** Accepted FillPath with all blur style variants produces expected blur mask routes. */
    @Test
    fun `fill path with all blur style variants produces blur mask routes`() {
        val styles = listOf(
            NormalizedBlurStyle.NORMAL,
            NormalizedBlurStyle.SOLID,
            NormalizedBlurStyle.OUTER,
            NormalizedBlurStyle.INNER,
        )
        for (style in styles) {
            val command = GPUFillPathCommandBuilder.build(
                commandId = GPUDrawCommandID(31),
                pathKey = "path:rect:v1",
                pathDescriptor = GPUPathFacts(
                    pathKey = "path:rect:v1",
                    verbCount = 5,
                    pointCount = 4,
                    fillRule = "NonZero",
                    inverseFill = false,
                    finiteProof = "finite",
                    volatility = "immutable",
                    transformClass = "identity",
                    edgeCount = 4,
                ),
                tessellatedVertices = listOf(0f, 0f, 32f, 0f, 32f, 32f, 0f, 32f),
                contourStarts = listOf(0),
                edgeCount = 4,
                target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
                material = GPUMaterialDescriptor.SolidColor(r = 0f, g = 0.5f, b = 1f, a = 1f),
                maskFilter = NormalizedMaskFilter.Blur(style = style, sigma = 3f),
            )

            val plan = GPUFirstRoutePlanner(capabilities = firstSlicePathFillCapabilities()).plan(command)
            val analysisDecision = assertIs<GPUDrawAnalysisDecision.Candidate>(plan.analysisDecision)

            assertEquals("executable.path_fill.mask_blur", analysisDecision.routeDecisionLabel)
            assertContains(plan.pass.invocations.single().renderStepId.value, "mask_blur")
        }
    }

    /** Accepted DrawImageRect with decoded pixels builds CPU-prepared GPU route. */
    @Test
    fun `draw image rect builds prepared route with upload and consume pipeline`() {
        val command = GPUDrawImageRectCommandBuilder.build(
            commandId = GPUDrawCommandID(30),
            imageSourceId = "image:checker:v1",
            src = GPURect(left = 0f, top = 0f, right = 2f, bottom = 2f),
            dst = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.ImageDraw(imageSourceId = "image:checker:v1", imageWidth = 2, imageHeight = 2),
            samplingFilterMode = "linear",
            pixelsWidth = 2,
            pixelsHeight = 2,
            pixelsRowBytes = 8,
            pixelsAlphaType = "Premul",
            pixelsColorProfileLabel = "srgb",
            pixelsOrientationState = "Applied",
            pixelsGeneration = 3,
            pixelsContentHash = "sha256:checker-pixels-v1",
            pixelsProvenance = "unit-test",
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)
        val routeDecision = assertIs<GPURouteDecision.Prepared>(plan.routeDecision)
        val analysisDecision = assertIs<GPUDrawAnalysisDecision.Candidate>(plan.analysisDecision)
        val invocation = plan.pass.invocations.single()

        assertEquals("analysis.draw_image_rect.30", plan.analysisRecord.recordId)
        assertEquals("DrawImageRect", plan.analysisRecord.commandFamily)
        assertEquals("prepared.draw_image_rect.decoded_pixels", analysisDecision.routeDecisionLabel)
        assertEquals("sampled-image.draw_image_rect", routeDecision.route.consumerKind)
        assertEquals("decoded-image-upload", routeDecision.route.artifactType)
        assertEquals("recording-local", routeDecision.route.lifetimeClass)
        assertEquals("image.draw.texture_upload", invocation.renderStepId.value)
        assertEquals(listOf("pending.pipeline.draw_image_rect.decoded_pixels.rgba8unorm.src_over"), plan.pass.pipelineKeys)
        assertEquals("pending.pipeline.draw_image_rect.decoded_pixels.rgba8unorm.src_over", invocation.pipelineKeyHash)
        assertEquals("analysis.draw_image_rect.30", invocation.analysisRecordId)
        assertEquals(30, invocation.commandIdValue)
        assertEquals("image_draw", invocation.role)
        assertEquals("pass.image_draw.30", plan.pass.passId)
        assertEquals("pending.material.imagedraw", plan.analysisRecord.materialKeyHash)
        assertNull(invocation.scissorBoundsHash)
    }

    /** DrawImageRect with unsupported material refuses diagnostically. */
    @Test
    fun `draw image rect with unsupported material refuses diagnostically`() {
        val command = GPUDrawImageRectCommandBuilder.build(
            commandId = GPUDrawCommandID(31),
            imageSourceId = "image:checker:v1",
            src = GPURect(left = 0f, top = 0f, right = 2f, bottom = 2f),
            dst = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0f, b = 0f, a = 1f),
            samplingFilterMode = IMAGE_DRAW_SAMPLING_FILTER,
            pixelsWidth = IMAGE_DRAW_PIXELS_WIDTH,
            pixelsHeight = IMAGE_DRAW_PIXELS_HEIGHT,
            pixelsRowBytes = IMAGE_DRAW_PIXELS_ROW_BYTES,
            pixelsAlphaType = IMAGE_DRAW_PIXELS_ALPHA,
            pixelsColorProfileLabel = IMAGE_DRAW_PIXELS_COLOR_PROFILE,
            pixelsOrientationState = IMAGE_DRAW_PIXELS_ORIENTATION,
            pixelsGeneration = IMAGE_DRAW_PIXELS_GENERATION,
            pixelsContentHash = IMAGE_DRAW_PIXELS_CONTENT_HASH,
            pixelsProvenance = IMAGE_DRAW_PIXELS_PROVENANCE,
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.material.source_unimplemented", plan.pass.diagnostics.single().code)
    }

    /** Blank source provenance does not replace the decoded-pixel admission authority. */
    @Test
    fun `draw image rect admits valid pixels with blank source provenance`() {
        val command = GPUDrawImageRectCommandBuilder.build(
            commandId = GPUDrawCommandID(32),
            imageSourceId = "",
            src = GPURect(left = 0f, top = 0f, right = 2f, bottom = 2f),
            dst = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.ImageDraw(imageSourceId = "", imageWidth = 2, imageHeight = 2),
            samplingFilterMode = IMAGE_DRAW_SAMPLING_FILTER,
            pixelsWidth = IMAGE_DRAW_PIXELS_WIDTH,
            pixelsHeight = IMAGE_DRAW_PIXELS_HEIGHT,
            pixelsRowBytes = IMAGE_DRAW_PIXELS_ROW_BYTES,
            pixelsAlphaType = IMAGE_DRAW_PIXELS_ALPHA,
            pixelsColorProfileLabel = IMAGE_DRAW_PIXELS_COLOR_PROFILE,
            pixelsOrientationState = IMAGE_DRAW_PIXELS_ORIENTATION,
            pixelsGeneration = IMAGE_DRAW_PIXELS_GENERATION,
            pixelsContentHash = IMAGE_DRAW_PIXELS_CONTENT_HASH,
            pixelsProvenance = IMAGE_DRAW_PIXELS_PROVENANCE,
        )

        val blankSourcePlan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)
        val namedSourcePlan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(
            command.copy(
                imageSourceId = "provenance-only",
                material = GPUMaterialDescriptor.ImageDraw(
                    imageSourceId = "provenance-only",
                    imageWidth = 2,
                    imageHeight = 2,
                ),
            ),
        )

        val blankRoute = assertIs<GPURouteDecision.Prepared>(blankSourcePlan.routeDecision)
        val namedRoute = assertIs<GPURouteDecision.Prepared>(namedSourcePlan.routeDecision)
        assertEquals(blankRoute.route.artifactKey, namedRoute.route.artifactKey)
        assertTrue(blankSourcePlan.pass.diagnostics.none {
            it.code == GPUPreparedImageRefusalCodes.PIXELS_MISSING
        })
    }

    /** DrawImageRect with NaN source rect refuses diagnostically. */
    @Test
    fun `draw image rect with nan src rect refuses diagnostically`() {
        val command = GPUDrawImageRectCommandBuilder.build(
            commandId = GPUDrawCommandID(33),
            imageSourceId = IMAGE_DRAW_SOURCE_ID,
            src = GPURect(left = Float.NaN, top = 0f, right = 2f, bottom = 2f),
            dst = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.ImageDraw(imageSourceId = IMAGE_DRAW_SOURCE_ID, imageWidth = 2, imageHeight = 2),
            samplingFilterMode = IMAGE_DRAW_SAMPLING_FILTER,
            pixelsWidth = IMAGE_DRAW_PIXELS_WIDTH,
            pixelsHeight = IMAGE_DRAW_PIXELS_HEIGHT,
            pixelsRowBytes = IMAGE_DRAW_PIXELS_ROW_BYTES,
            pixelsAlphaType = IMAGE_DRAW_PIXELS_ALPHA,
            pixelsColorProfileLabel = IMAGE_DRAW_PIXELS_COLOR_PROFILE,
            pixelsOrientationState = IMAGE_DRAW_PIXELS_ORIENTATION,
            pixelsGeneration = IMAGE_DRAW_PIXELS_GENERATION,
            pixelsContentHash = IMAGE_DRAW_PIXELS_CONTENT_HASH,
            pixelsProvenance = IMAGE_DRAW_PIXELS_PROVENANCE,
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.image.src_rect_nan", plan.pass.diagnostics.single().code)
    }

    /** DrawImageRect with NaN destination rect triggers bounds-nan via coordinate check. */
    @Test
    fun `draw image rect with nan dst rect triggers bounds nan`() {
        val command = GPUDrawImageRectCommandBuilder.build(
            commandId = GPUDrawCommandID(34),
            imageSourceId = IMAGE_DRAW_SOURCE_ID,
            src = GPURect(left = 0f, top = 0f, right = 2f, bottom = 2f),
            dst = GPURect(left = Float.NaN, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.ImageDraw(imageSourceId = IMAGE_DRAW_SOURCE_ID, imageWidth = 2, imageHeight = 2),
            samplingFilterMode = IMAGE_DRAW_SAMPLING_FILTER,
            pixelsWidth = IMAGE_DRAW_PIXELS_WIDTH,
            pixelsHeight = IMAGE_DRAW_PIXELS_HEIGHT,
            pixelsRowBytes = IMAGE_DRAW_PIXELS_ROW_BYTES,
            pixelsAlphaType = IMAGE_DRAW_PIXELS_ALPHA,
            pixelsColorProfileLabel = IMAGE_DRAW_PIXELS_COLOR_PROFILE,
            pixelsOrientationState = IMAGE_DRAW_PIXELS_ORIENTATION,
            pixelsGeneration = IMAGE_DRAW_PIXELS_GENERATION,
            pixelsContentHash = IMAGE_DRAW_PIXELS_CONTENT_HASH,
            pixelsProvenance = IMAGE_DRAW_PIXELS_PROVENANCE,
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.bounds.nan", plan.pass.diagnostics.single().code)
    }

    /** DrawImageRect with infinite destination rect triggers bounds-non-finite via coordinate check. */
    @Test
    fun `draw image rect with infinite dst rect triggers bounds non finite`() {
        val command = GPUDrawImageRectCommandBuilder.build(
            commandId = GPUDrawCommandID(35),
            imageSourceId = IMAGE_DRAW_SOURCE_ID,
            src = GPURect(left = 0f, top = 0f, right = 2f, bottom = 2f),
            dst = GPURect(left = 2f, top = 3f, right = Float.POSITIVE_INFINITY, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.ImageDraw(imageSourceId = IMAGE_DRAW_SOURCE_ID, imageWidth = 2, imageHeight = 2),
            samplingFilterMode = IMAGE_DRAW_SAMPLING_FILTER,
            pixelsWidth = IMAGE_DRAW_PIXELS_WIDTH,
            pixelsHeight = IMAGE_DRAW_PIXELS_HEIGHT,
            pixelsRowBytes = IMAGE_DRAW_PIXELS_ROW_BYTES,
            pixelsAlphaType = IMAGE_DRAW_PIXELS_ALPHA,
            pixelsColorProfileLabel = IMAGE_DRAW_PIXELS_COLOR_PROFILE,
            pixelsOrientationState = IMAGE_DRAW_PIXELS_ORIENTATION,
            pixelsGeneration = IMAGE_DRAW_PIXELS_GENERATION,
            pixelsContentHash = IMAGE_DRAW_PIXELS_CONTENT_HASH,
            pixelsProvenance = IMAGE_DRAW_PIXELS_PROVENANCE,
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.bounds.non_finite", plan.pass.diagnostics.single().code)
    }

    /** DrawImageRect with invalid pixels descriptor refuses diagnostically. */
    @Test
    fun `draw image rect with zero width pixels descriptor refuses diagnostically`() {
        val command = GPUDrawImageRectCommandBuilder.build(
            commandId = GPUDrawCommandID(36),
            imageSourceId = IMAGE_DRAW_SOURCE_ID,
            src = GPURect(left = 0f, top = 0f, right = 2f, bottom = 2f),
            dst = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.ImageDraw(imageSourceId = IMAGE_DRAW_SOURCE_ID, imageWidth = 2, imageHeight = 2),
            samplingFilterMode = IMAGE_DRAW_SAMPLING_FILTER,
            pixelsWidth = 0,
            pixelsHeight = IMAGE_DRAW_PIXELS_HEIGHT,
            pixelsRowBytes = IMAGE_DRAW_PIXELS_ROW_BYTES,
            pixelsAlphaType = IMAGE_DRAW_PIXELS_ALPHA,
            pixelsColorProfileLabel = IMAGE_DRAW_PIXELS_COLOR_PROFILE,
            pixelsOrientationState = IMAGE_DRAW_PIXELS_ORIENTATION,
            pixelsGeneration = IMAGE_DRAW_PIXELS_GENERATION,
            pixelsContentHash = IMAGE_DRAW_PIXELS_CONTENT_HASH,
            pixelsProvenance = IMAGE_DRAW_PIXELS_PROVENANCE,
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals(GPUPreparedImageRefusalCodes.DIMENSIONS, plan.pass.diagnostics.single().code)
    }

    /** DrawImageRect with stroke refuses diagnostically. */
    @Test
    fun `draw image rect with stroke refuses diagnostically`() {
        val command = GPUDrawImageRectCommandBuilder.build(
            commandId = GPUDrawCommandID(37),
            imageSourceId = IMAGE_DRAW_SOURCE_ID,
            src = GPURect(left = 0f, top = 0f, right = 2f, bottom = 2f),
            dst = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.ImageDraw(imageSourceId = IMAGE_DRAW_SOURCE_ID, imageWidth = 2, imageHeight = 2),
            samplingFilterMode = IMAGE_DRAW_SAMPLING_FILTER,
            pixelsWidth = IMAGE_DRAW_PIXELS_WIDTH,
            pixelsHeight = IMAGE_DRAW_PIXELS_HEIGHT,
            pixelsRowBytes = IMAGE_DRAW_PIXELS_ROW_BYTES,
            pixelsAlphaType = IMAGE_DRAW_PIXELS_ALPHA,
            pixelsColorProfileLabel = IMAGE_DRAW_PIXELS_COLOR_PROFILE,
            pixelsOrientationState = IMAGE_DRAW_PIXELS_ORIENTATION,
            pixelsGeneration = IMAGE_DRAW_PIXELS_GENERATION,
            pixelsContentHash = IMAGE_DRAW_PIXELS_CONTENT_HASH,
            pixelsProvenance = IMAGE_DRAW_PIXELS_PROVENANCE,
            stroke = true,
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.stroke.unimplemented", plan.pass.diagnostics.single().code)
    }

    /** DrawImageRect with complex clip stack refuses diagnostically. */
    @Test
    fun `draw image rect with complex clip stack refuses diagnostically`() {
        val command = GPUDrawImageRectCommandBuilder.build(
            commandId = GPUDrawCommandID(38),
            imageSourceId = IMAGE_DRAW_SOURCE_ID,
            src = GPURect(left = 0f, top = 0f, right = 2f, bottom = 2f),
            dst = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.ImageDraw(imageSourceId = IMAGE_DRAW_SOURCE_ID, imageWidth = 2, imageHeight = 2),
            samplingFilterMode = IMAGE_DRAW_SAMPLING_FILTER,
            pixelsWidth = IMAGE_DRAW_PIXELS_WIDTH,
            pixelsHeight = IMAGE_DRAW_PIXELS_HEIGHT,
            pixelsRowBytes = IMAGE_DRAW_PIXELS_ROW_BYTES,
            pixelsAlphaType = IMAGE_DRAW_PIXELS_ALPHA,
            pixelsColorProfileLabel = IMAGE_DRAW_PIXELS_COLOR_PROFILE,
            pixelsOrientationState = IMAGE_DRAW_PIXELS_ORIENTATION,
            pixelsGeneration = IMAGE_DRAW_PIXELS_GENERATION,
            pixelsContentHash = IMAGE_DRAW_PIXELS_CONTENT_HASH,
            pixelsProvenance = IMAGE_DRAW_PIXELS_PROVENANCE,
            clip = GPUClipFacts.complexStack(bounds = GPUBounds(left = 2f, top = 3f, right = 18f, bottom = 21f)),
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.clip.complex_stack", plan.pass.diagnostics.single().code)
    }

    /** DrawImageRect with perspective transform refuses diagnostically. */
    @Test
    fun `draw image rect with perspective transform refuses diagnostically`() {
        val command = GPUDrawImageRectCommandBuilder.build(
            commandId = GPUDrawCommandID(39),
            imageSourceId = IMAGE_DRAW_SOURCE_ID,
            src = GPURect(left = 0f, top = 0f, right = 2f, bottom = 2f),
            dst = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.ImageDraw(imageSourceId = IMAGE_DRAW_SOURCE_ID, imageWidth = 2, imageHeight = 2),
            samplingFilterMode = IMAGE_DRAW_SAMPLING_FILTER,
            pixelsWidth = IMAGE_DRAW_PIXELS_WIDTH,
            pixelsHeight = IMAGE_DRAW_PIXELS_HEIGHT,
            pixelsRowBytes = IMAGE_DRAW_PIXELS_ROW_BYTES,
            pixelsAlphaType = IMAGE_DRAW_PIXELS_ALPHA,
            pixelsColorProfileLabel = IMAGE_DRAW_PIXELS_COLOR_PROFILE,
            pixelsOrientationState = IMAGE_DRAW_PIXELS_ORIENTATION,
            pixelsGeneration = IMAGE_DRAW_PIXELS_GENERATION,
            pixelsContentHash = IMAGE_DRAW_PIXELS_CONTENT_HASH,
            pixelsProvenance = IMAGE_DRAW_PIXELS_PROVENANCE,
            transform = GPUTransformFacts.perspective(),
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.transform.perspective", plan.pass.diagnostics.single().code)
    }

    /** Accepted DrawLayer builds CPU-prepared GPU route with composite render step. */
    @Test
    fun `draw layer builds prepared route with composite pipeline`() {
        val command = GPUDrawLayerCommandBuilder.build(
            commandId = GPUDrawCommandID(50),
            scopeId = "layer:card",
            target = GPUTargetFacts(width = 256, height = 256, colorFormat = "rgba8unorm"),
            bounds = GPUBounds(left = 0f, top = 0f, right = 64f, bottom = 48f),
            childCommandIds = listOf("draw-rect", "draw-image"),
            parentScopeId = "root",
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceDrawLayerCapabilities()).plan(command)
        val routeDecision = assertIs<GPURouteDecision.Prepared>(plan.routeDecision)
        val analysisDecision = assertIs<GPUDrawAnalysisDecision.Candidate>(plan.analysisDecision)
        val invocation = plan.pass.invocations.single()

        assertEquals("analysis.draw_layer.50", plan.analysisRecord.recordId)
        assertEquals("DrawLayer", plan.analysisRecord.commandFamily)
        assertEquals("prepared.draw_layer.composite", analysisDecision.routeDecisionLabel)
        assertEquals("composite-layer.draw_layer", routeDecision.route.consumerKind)
        assertEquals("savelayer-filtered-compositor", routeDecision.route.artifactType)
        assertEquals("recording-local", routeDecision.route.lifetimeClass)
        assertEquals("layer.composite", invocation.renderStepId.value)
        assertEquals(listOf("pending.pipeline.draw_layer.composite.rgba8unorm.src_over"), plan.pass.pipelineKeys)
        assertEquals("pending.pipeline.draw_layer.composite.rgba8unorm.src_over", invocation.pipelineKeyHash)
        assertEquals("analysis.draw_layer.50", invocation.analysisRecordId)
        assertEquals(50, invocation.commandIdValue)
        assertEquals("draw_layer", invocation.role)
        assertEquals("layer:card", invocation.layerScopeId)
        assertEquals("pass.draw_layer.50", plan.pass.passId)
        assertEquals("pending.material.draw_layer", plan.analysisRecord.materialKeyHash)
        assertNull(invocation.scissorBoundsHash)
    }

    /** DrawLayer promoted to native isolated-target route when native isolation capability is present. */
    @Test
    fun `draw layer with native isolation capability builds native route`() {
        val command = GPUDrawLayerCommandBuilder.build(
            commandId = GPUDrawCommandID(51),
            scopeId = "layer:dialog",
            target = GPUTargetFacts(width = 256, height = 256, colorFormat = "rgba8unorm"),
            bounds = GPUBounds(left = 0f, top = 0f, right = 64f, bottom = 48f),
            childCommandIds = listOf("draw-rect"),
            parentScopeId = "root",
        )

        val plan = GPUFirstRoutePlanner(
            capabilities = firstSliceDrawLayerNativeCapabilities(),
        ).plan(command)
        val routeDecision = assertIs<GPURouteDecision.Native>(plan.routeDecision)

        assertEquals("native.draw_layer.isolated_target", routeDecision.route.consumerKind)
        assertEquals("layer.isolated_target", routeDecision.route.renderStepIdentity)
        assertEquals(listOf("first_slice.draw_layer.native_isolation"), routeDecision.route.requirements)
        assertEquals("native.draw_layer.isolated_target", plan.analysisRecord.routeDecisionLabel)
        assertEquals("layer.isolated_target", plan.pass.invocations.single().renderStepId.value)
    }

    /** DrawLayer refuses when initPrevious is requested. */
    @Test
    fun `draw layer with init previous refuses diagnostically`() {
        val command = GPUDrawLayerCommandBuilder.build(
            commandId = GPUDrawCommandID(52),
            scopeId = "layer:card",
            target = GPUTargetFacts(width = 256, height = 256, colorFormat = "rgba8unorm"),
            bounds = GPUBounds(left = 0f, top = 0f, right = 64f, bottom = 48f),
            initWithPrevious = true,
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceDrawLayerCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.layer.init_previous_unaccepted", plan.pass.diagnostics.single().code)
    }

    /** DrawLayer refuses for unsupported restore blend mode. */
    @Test
    fun `draw layer with unsupported blend refuses diagnostically`() {
        val command = GPUDrawLayerCommandBuilder.build(
            commandId = GPUDrawCommandID(53),
            scopeId = "layer:card",
            target = GPUTargetFacts(width = 256, height = 256, colorFormat = "rgba8unorm"),
            bounds = GPUBounds(left = 0f, top = 0f, right = 64f, bottom = 48f),
            restoreBlendMode = "multiply",
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceDrawLayerCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.layer.restore_blend", plan.pass.diagnostics.single().code)
    }

    /** DrawLayer refuses for unsupported save/restore state variants. */
    @Test
    fun `unsupported draw layer variants produce canonical refusal diagnostics`() {
        val target = GPUTargetFacts(width = 256, height = 256, colorFormat = "rgba8unorm")
        val cases = listOf(
            "unsupported.layer.backdrop_filter" to firstDrawLayerCommand(
                target = target,
                backdropRequired = true,
            ),
            "unsupported.layer.filter_chain" to firstDrawLayerCommand(
                target = target,
                sourceFilterCount = 1,
                requiresFilter = true,
            ),
            "unsupported.layer.cpu_fallback_forbidden" to firstDrawLayerCommand(
                target = target,
                cpuFallbackRequested = true,
            ),
            "unsupported.layer.preserve_lcd_text" to firstDrawLayerCommand(
                target = target,
                preserveLCDText = true,
            ),
            "unsupported.layer.f16_unavailable" to firstDrawLayerCommand(
                target = target,
                f16Requested = true,
            ),
            "unsupported.layer.scope_id_empty" to firstDrawLayerCommand(
                target = target,
                scopeId = "",
            ),
            "unsupported.target.format_blend_incompatible" to firstDrawLayerCommand(
                target = target.copy(colorFormat = "bgra8unorm-srgb"),
            ),
            "unsupported.pipeline.capability_missing" to firstDrawLayerCommand(
                target = target,
                capabilities = emptyCapabilities(),
            ),
        )

        for ((expectedCode, fixture) in cases) {
            val plan = GPUFirstRoutePlanner(capabilities = fixture.capabilities).plan(fixture.command)
            val routeDecision = assertIs<GPURouteDecision.Refused>(plan.routeDecision)
            val analysisDecision = assertIs<GPUDrawAnalysisDecision.Refuse>(plan.analysisDecision)

            assertEquals(expectedCode, routeDecision.diagnostic.code)
            assertEquals(expectedCode, analysisDecision.diagnostic.code)
            assertEquals(emptyList(), plan.analysisRecord.renderStepCandidates)
            assertEquals(listOf(expectedCode), plan.pass.diagnostics.map { it.code })
            assertEquals(emptyList(), plan.pass.invocations)
            assertEquals(emptyList(), plan.pass.pipelineKeys)
        }
    }

    /** DrawLayer refuses with stroke. */
    @Test
    fun `draw layer with stroke refuses diagnostically`() {
        val command = GPUDrawLayerCommandBuilder.build(
            commandId = GPUDrawCommandID(54),
            scopeId = "layer:card",
            target = GPUTargetFacts(width = 256, height = 256, colorFormat = "rgba8unorm"),
            bounds = GPUBounds(left = 0f, top = 0f, right = 64f, bottom = 48f),
            stroke = true,
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceDrawLayerCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.stroke.unimplemented", plan.pass.diagnostics.single().code)
    }

    /** Capability snapshot that enables the DrawLayer prepared route. */
    private fun firstSliceDrawLayerCapabilities(): GPUCapabilities =
        GPUCapabilities(
            implementation = GPUImplementationIdentity(
                facadeName = "test-gpu",
                implementationName = "unit",
                adapterName = "fixture-adapter",
                deviceName = "fixture-device",
            ),
            facts = listOf(
                GPUCapabilityFact(
                    name = "first_slice.draw_layer.prepared",
                    source = "unit-test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "draw-layer-fixture",
                ),
            ),
            snapshotId = "draw-layer-test",
        )

    /** Capability snapshot that enables DrawLayer with native isolation promotion. */
    private fun firstSliceDrawLayerNativeCapabilities(): GPUCapabilities =
        firstSliceDrawLayerCapabilities().copy(
            facts = firstSliceDrawLayerCapabilities().facts + GPUCapabilityFact(
                name = "first_slice.draw_layer.native_isolation",
                source = "unit-test",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "draw-layer-native-fixture",
            ),
            snapshotId = "draw-layer-native-test",
        )

    /** Builds the common accepted DrawLayer command while allowing one refused fact to vary. */
    private fun firstDrawLayerCommand(
        target: GPUTargetFacts,
        scopeId: String = "layer:card",
        backdropRequired: Boolean = false,
        sourceFilterCount: Int = 0,
        requiresFilter: Boolean = false,
        cpuFallbackRequested: Boolean = false,
        preserveLCDText: Boolean = false,
        f16Requested: Boolean = false,
        capabilities: GPUCapabilities = firstSliceDrawLayerCapabilities(),
    ): DrawLayerRefusalFixture =
        DrawLayerRefusalFixture(
            command = GPUDrawLayerCommandBuilder.build(
                commandId = GPUDrawCommandID(59),
                scopeId = scopeId,
                target = target,
                bounds = GPUBounds(left = 0f, top = 0f, right = 64f, bottom = 48f),
                childCommandIds = listOf("draw-rect"),
                parentScopeId = "root",
                backdropRequired = backdropRequired,
                sourceFilterCount = sourceFilterCount,
                requiresFilter = requiresFilter,
                cpuFallbackRequested = cpuFallbackRequested,
                preserveLCDText = preserveLCDText,
                f16Requested = f16Requested,
            ),
            capabilities = capabilities,
        )

    /** DrawLayer command plus capability facts for one refusal fixture. */
    private data class DrawLayerRefusalFixture(
        val command: NormalizedDrawCommand.DrawLayer,
        val capabilities: GPUCapabilities,
    )

    /** Capability snapshot that enables only the first native FillRect route. */
    private fun firstSliceCapabilities(): GPUCapabilities =
        GPUCapabilities(
            implementation = GPUImplementationIdentity(
                facadeName = "test-gpu",
                implementationName = "unit",
                adapterName = "fixture-adapter",
                deviceName = "fixture-device",
            ),
            facts = listOf(
                GPUCapabilityFact(
                    name = "first_slice.fill_rect.native",
                    source = "unit-test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "first-route-fixture",
                ),
                GPUCapabilityFact(
                    name = "first_slice.mask_blur.native",
                    source = "unit-test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "first-route-fixture",
                ),
            ),
            snapshotId = "first-route-test",
        )

    private fun firstSliceAffineFillRectCapabilities(): GPUCapabilities =
        firstSliceCapabilities().copy(
            facts = firstSliceCapabilities().facts + GPUCapabilityFact(
                name = CORE_PRIMITIVE_AFFINE_FILL_RECT_CAPABILITY,
                source = "unit-test",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "affine-fill-rect-fixture",
            ),
            snapshotId = "affine-fill-rect-test",
        )

    /** Capability snapshot that enables only the native FillRRect expansion route. */
    private fun firstSliceRRectCapabilities(): GPUCapabilities =
        firstSliceCapabilities().copy(
            facts = listOf(
                GPUCapabilityFact(
                    name = "first_slice.fill_rrect.native",
                    source = "unit-test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "rrect-route-fixture",
                ),
                GPUCapabilityFact(
                    name = "first_slice.fill_drrect.native",
                    source = "unit-test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "drrect-route-fixture",
                ),
                GPUCapabilityFact(
                    name = "first_slice.mask_blur.native",
                    source = "unit-test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "rrect-route-fixture",
                ),
            ),
            snapshotId = "rrect-route-test",
        )

    private fun analyticDRRectCommand(
        commandId: Int,
        material: GPUMaterialDescriptor = GPUMaterialDescriptor.SolidColor(
            r = 1f,
            g = 0.25f,
            b = 0.5f,
            a = 1f,
        ),
    ): NormalizedDrawCommand.FillDRRect = NormalizedDrawCommand.FillDRRect(
        commandId = GPUDrawCommandID(commandId),
        outer = GPURRect(GPURect(2f, 2f, 26f, 22f), radiusX = 4f, radiusY = 4f),
        inner = GPURRect(GPURect(8f, 8f, 20f, 16f), radiusX = 2f, radiusY = 2f),
        transform = GPUTransformFacts.identity(),
        clip = GPUClipFacts.wideOpen(GPUBounds(2f, 2f, 26f, 22f)),
        layer = GPULayerFacts.root(GPUTargetFacts(32, 24, "rgba8unorm")),
        material = material,
        bounds = GPUBounds(2f, 2f, 26f, 22f),
        ordering = GPUOrderingFacts(0, dependsOnDestination = false, requiresBarrier = false),
        source = GPUCommandSource(adapter = "unit-test", operation = "fillDRRect"),
        antiAlias = false,
    )

    /** Capability snapshot that enables the FillRect route plus the scissor clip. */
    private fun firstSliceWithScissorCapabilities(): GPUCapabilities =
        firstSliceCapabilities().copy(
            facts = firstSliceCapabilities().facts + GPUCapabilityFact(
                name = "first_slice.scissor.native",
                source = "unit-test",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "scissor-fixture",
            ),
            snapshotId = "scissor-test",
        )

    /** Capability snapshot that enables the FillRect route plus linear gradient material. */
    private fun firstSliceWithLinearGradientCapabilities(): GPUCapabilities =
        firstSliceCapabilities().copy(
            facts = firstSliceCapabilities().facts + GPUCapabilityFact(
                name = "first_slice.linear_gradient.native",
                source = "unit-test",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "linear-gradient-fixture",
            ),
            snapshotId = "linear-gradient-test",
        )

    private fun firstSliceWithRadialAndSweepGradientCapabilities(): GPUCapabilities =
        firstSliceCapabilities().copy(
            facts = firstSliceCapabilities().facts + listOf(
                GPUCapabilityFact(
                    name = "first_slice.radial_gradient.native",
                    source = "unit-test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "radial-gradient-fixture",
                ),
                GPUCapabilityFact(
                    name = "first_slice.sweep_gradient.native",
                    source = "unit-test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "sweep-gradient-fixture",
                ),
            ),
            snapshotId = "radial-sweep-gradient-test",
        )

    /** Capability snapshot that enables the FillRRect expansion route plus linear gradient material. */
    private fun firstSliceRRectWithLinearGradientCapabilities(): GPUCapabilities =
        firstSliceRRectCapabilities().copy(
            facts = firstSliceRRectCapabilities().facts + GPUCapabilityFact(
                name = "first_slice.linear_gradient.native",
                source = "unit-test",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "rrect-linear-gradient-fixture",
            ),
            snapshotId = "rrect-linear-gradient-test",
        )

    /** Capability snapshot that enables the FillPath prepared route. */
    private fun firstSlicePathFillCapabilities(): GPUCapabilities =
        GPUCapabilities(
            implementation = GPUImplementationIdentity(
                facadeName = "test-gpu",
                implementationName = "unit",
                adapterName = "fixture-adapter",
                deviceName = "fixture-device",
            ),
            facts = listOf(
                GPUCapabilityFact(
                    name = "first_slice.path_fill.native",
                    source = "unit-test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "path-fill-fixture",
                ),
                GPUCapabilityFact(
                    name = "first_slice.mask_blur.native",
                    source = "unit-test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "path-fill-fixture",
                ),
            ),
            snapshotId = "path-fill-test",
        )

    /** Capability snapshot that enables FillPath with linear gradient. */
    private fun firstSlicePathFillWithLinearGradientCapabilities(): GPUCapabilities =
        firstSlicePathFillCapabilities().copy(
            facts = firstSlicePathFillCapabilities().facts + GPUCapabilityFact(
                name = "first_slice.linear_gradient.native",
                source = "unit-test",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "linear-gradient-fixture",
            ),
            snapshotId = "path-fill-linear-gradient-test",
        )

    /** Capability snapshot that enables FillPath with stencil-cover promotion. */
    private fun firstSlicePathFillStencilCoverCapabilities(): GPUCapabilities =
        firstSlicePathFillCapabilities().copy(
            facts = listOf(GPUCapabilityFact(
                name = "first_slice.path_fill.stencil_cover",
                source = "unit-test",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "stencil-cover-fixture",
            )),
            snapshotId = "path-fill-stencil-cover-test",
        )

    /** Builds the common accepted command while allowing one refused fact to vary. */
    private fun firstRouteCommand(
        target: GPUTargetFacts,
        rect: GPURect = firstRouteRect,
        transform: GPUTransformFacts = GPUTransformFacts.identity(),
        clip: GPUClipFacts = GPUClipFacts.wideOpen(bounds = firstRouteBounds),
        layer: GPULayerFacts = GPULayerFacts.root(target = target),
        blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        capabilities: GPUCapabilities = firstSliceCapabilities(),
    ): RefusalFixture =
        RefusalFixture(
            command = GPUFillRectCommandBuilder.build(
                commandId = GPUDrawCommandID(9),
                rect = rect,
                target = target,
                material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
                transform = transform,
                clip = clip,
                layer = layer,
                blend = blend,
            ),
            capabilities = capabilities,
        )

    /** Builds the common accepted rrect command while allowing one refused fact to vary. */
    private fun firstRRectRouteCommand(
        target: GPUTargetFacts,
        rrect: GPURRect = firstRouteRRect,
        transform: GPUTransformFacts = GPUTransformFacts.identity(),
        clip: GPUClipFacts = GPUClipFacts.wideOpen(bounds = firstRouteBounds),
        layer: GPULayerFacts = GPULayerFacts.root(target = target),
        blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        capabilities: GPUCapabilities = firstSliceRRectCapabilities(),
    ): RRectRefusalFixture =
        RRectRefusalFixture(
            command = GPUFillRRectCommandBuilder.build(
                commandId = GPUDrawCommandID(19),
                rrect = rrect,
                target = target,
                material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
                transform = transform,
                clip = clip,
                layer = layer,
                blend = blend,
            ),
            capabilities = capabilities,
        )

    private fun hardWindingStencilClip(
        pathTransformClass: String,
        inverseFill: Boolean = false,
        fillRule: GPUClipFillRule = GPUClipFillRule.Winding,
        consumerInverseFill: Boolean = false,
    ): GPUClipFacts = GPUClipFacts(
        kind = GPUClipKind.ComplexStack,
        bounds = firstRouteBounds,
        executionPlan = GPUClipExecutionPlan.StencilCoverage(
            contentKey = "clip.hard-winding",
            bounds = GPUPixelBounds(0, 0, 64, 64),
            sampleCount = 1,
            atomicGroup = GPUClipAtomicGroupID("clip.hard-winding.atomic"),
            orderingToken = GPUClipOrderingToken("clip.hard-winding.order"),
            producer = GPUClipStencilProducerPlan(
                geometry = GPUClipExecutionGeometry.Path(
                    vertices = listOf(8f, 8f, 56f, 8f, 8f, 55f),
                    contourStarts = listOf(0),
                    fillRule = fillRule,
                    inverseFill = inverseFill,
                ),
                scissor = null,
                fillRule = fillRule,
                reference = 0u,
                compare = GPUClipStencilCompare.Always,
                frontPassOperation = GPUClipStencilOperation.IncrementWrap,
                backPassOperation = GPUClipStencilOperation.DecrementWrap,
                loadOperation = GPUClipStencilLoadOperation.Clear,
                storeOperation = GPUClipStencilStoreOperation.Store,
                clearValue = 0u,
            ),
            consumer = GPUClipStencilConsumerPlan(
                scissor = null,
                reference = 0u,
                compare = if (inverseFill xor consumerInverseFill) GPUClipStencilCompare.Equal else GPUClipStencilCompare.NotEqual,
            ),
            consumerInverseFill = consumerInverseFill,
            pathTransformClass = pathTransformClass,
        ),
    )

    /** Capability snapshot with no validity facts for missing-capability refusal tests. */
    private fun emptyCapabilities(): GPUCapabilities =
        firstSliceCapabilities().copy(facts = emptyList())

    /** Command plus capability facts for one refusal fixture. */
    private data class RefusalFixture(
        val command: NormalizedDrawCommand.FillRect,
        val capabilities: GPUCapabilities,
    )

    /** RRect command plus capability facts for one refusal fixture. */
    private data class RRectRefusalFixture(
        val command: NormalizedDrawCommand.FillRRect,
        val capabilities: GPUCapabilities,
    )

    /** Accepted ApplyFilter with ColorMatrix node builds native filter route. */
    @Test
    fun `apply filter with color matrix node builds native filter route and draw pass`() {
        val command = GPUApplyFilterCommandBuilder.build(
            commandId = GPUDrawCommandID(50),
            filterGraph = filterGraph(node("cf-1", "ColorFilter")),
            filterSource = GPUFilterSourcePlan(
                sourceLabel = "layer-source",
                boundsLabel = "0,0,64,48",
                colorTreatment = "premul-srgb",
            ),
            filterBounds = GPUSimpleFilterBounds(
                inputBoundsLabel = "0,0,64,48",
                outputBoundsLabel = "0,0,64,48",
                conservative = true,
                finite = true,
                width = 64,
                height = 48,
            ),
            target = GPUTargetFacts(width = 64, height = 48, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        )

        val plan = GPUFirstRoutePlanner(capabilities = filterCapabilities()).plan(command)
        val routeDecision = assertIs<GPURouteDecision.Native>(plan.routeDecision)
        val analysisDecision = assertIs<GPUDrawAnalysisDecision.Candidate>(plan.analysisDecision)
        val invocation = plan.pass.invocations.single()

        assertEquals("analysis.apply_filter.50", plan.analysisRecord.recordId)
        assertEquals("ApplyFilter", plan.analysisRecord.commandFamily)
        assertEquals("native.apply_filter.simple_node", analysisDecision.routeDecisionLabel)
        assertEquals("native.apply_filter.simple_node", routeDecision.route.consumerKind)
        assertContains(routeDecision.route.renderStepIdentity, "filter-render:colorfilter")
        assertEquals(listOf("first_slice.color_matrix_filter.native"), routeDecision.route.requirements)
        assertEquals(emptyList(), analysisDecision.resourceDeclarations)
        assertEquals("pass.filter.50", plan.pass.passId)
        assertEquals(1, plan.pass.pipelineKeys.size)
        assertContains(plan.pass.pipelineKeys.single(), "sha256:")
        assertEquals("filter", invocation.role)
        assertEquals("root", invocation.layerScopeId)
        assertEquals("bounds:0.0,0.0,64.0,48.0", invocation.boundsHash)
        assertNull(invocation.scissorBoundsHash)
        assertNull(invocation.uniformSlot)
        assertNull(invocation.resourceSlot)
    }

    /** Accepted ApplyFilter with GaussianBlur node builds native filter route. */
    @Test
    fun `apply filter with gaussian blur node builds native filter route`() {
        val command = GPUApplyFilterCommandBuilder.build(
            commandId = GPUDrawCommandID(51),
            filterGraph = filterGraph(node("blur-1", "GaussianBlur")),
            filterSource = GPUFilterSourcePlan(
                sourceLabel = "layer-source",
                boundsLabel = "0,0,64,48",
                colorTreatment = "premul-srgb",
            ),
            filterBounds = GPUSimpleFilterBounds(
                inputBoundsLabel = "0,0,64,48",
                outputBoundsLabel = "0,0,64,48",
                conservative = true,
                finite = true,
                width = 64,
                height = 48,
            ),
            target = GPUTargetFacts(width = 64, height = 48, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        )

        val plan = GPUFirstRoutePlanner(capabilities = filterCapabilities()).plan(command)
        assertIs<GPURouteDecision.Native>(plan.routeDecision)
        assertEquals("native.apply_filter.simple_node", plan.analysisRecord.routeDecisionLabel)
        assertEquals("ApplyFilter", plan.analysisRecord.commandFamily)
        assertEquals("pass.filter.51", plan.pass.passId)
    }

    /** ApplyFilter without any filter capability refuses diagnostically. */
    @Test
    fun `apply filter without filter capability refuses diagnostically`() {
        val command = GPUApplyFilterCommandBuilder.build(
            commandId = GPUDrawCommandID(52),
            filterGraph = filterGraph(node("cf-1", "ColorFilter")),
            filterSource = GPUFilterSourcePlan(
                sourceLabel = "layer-source",
                boundsLabel = "0,0,64,48",
                colorTreatment = "premul-srgb",
            ),
            filterBounds = GPUSimpleFilterBounds(
                inputBoundsLabel = "0,0,64,48",
                outputBoundsLabel = "0,0,64,48",
                conservative = true,
                finite = true,
                width = 64,
                height = 48,
            ),
            target = GPUTargetFacts(width = 64, height = 48, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        )

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.pipeline.capability_missing", plan.pass.diagnostics.single().code)
    }

    /** ApplyFilter with unsupported node kind refuses diagnostically. */
    @Test
    fun `apply filter with unsupported node kind refuses diagnostically`() {
        val command = GPUApplyFilterCommandBuilder.build(
            commandId = GPUDrawCommandID(53),
            filterGraph = filterGraph(node("rt-1", "RuntimeShader")),
            filterSource = GPUFilterSourcePlan(
                sourceLabel = "layer-source",
                boundsLabel = "0,0,64,48",
                colorTreatment = "premul-srgb",
            ),
            filterBounds = GPUSimpleFilterBounds(
                inputBoundsLabel = "0,0,64,48",
                outputBoundsLabel = "0,0,64,48",
                conservative = true,
                finite = true,
                width = 64,
                height = 48,
            ),
            target = GPUTargetFacts(width = 64, height = 48, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        )

        val plan = GPUFirstRoutePlanner(capabilities = filterCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.filter.node_unimplemented", plan.pass.diagnostics.single().code)
    }

    /** ApplyFilter with multi-node DAG refuses diagnostically. */
    @Test
    fun `apply filter with multi node dag refuses diagnostically`() {
        val command = GPUApplyFilterCommandBuilder.build(
            commandId = GPUDrawCommandID(54),
            filterGraph = filterGraph(
                node("cf-1", "ColorFilter"),
                node("blur-1", "GaussianBlur"),
            ),
            filterSource = GPUFilterSourcePlan(
                sourceLabel = "layer-source",
                boundsLabel = "0,0,64,48",
                colorTreatment = "premul-srgb",
            ),
            filterBounds = GPUSimpleFilterBounds(
                inputBoundsLabel = "0,0,64,48",
                outputBoundsLabel = "0,0,64,48",
                conservative = true,
                finite = true,
                width = 64,
                height = 48,
            ),
            target = GPUTargetFacts(width = 64, height = 48, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        )

        val plan = GPUFirstRoutePlanner(capabilities = filterCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.filter.graph_node_limit", plan.pass.diagnostics.single().code)
    }

    /** ApplyFilter with invalid bounds refuses diagnostically. */
    @Test
    fun `apply filter with zero width bounds refuses diagnostically`() {
        val command = GPUApplyFilterCommandBuilder.build(
            commandId = GPUDrawCommandID(55),
            filterGraph = filterGraph(node("cf-1", "ColorFilter")),
            filterSource = GPUFilterSourcePlan(
                sourceLabel = "layer-source",
                boundsLabel = "0,0,0,48",
                colorTreatment = "premul-srgb",
            ),
            filterBounds = GPUSimpleFilterBounds(
                inputBoundsLabel = "0,0,0,48",
                outputBoundsLabel = "0,0,0,48",
                conservative = true,
                finite = true,
                width = 0,
                height = 48,
            ),
            target = GPUTargetFacts(width = 64, height = 48, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        )

        val plan = GPUFirstRoutePlanner(capabilities = filterCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.filter.bounds_invalid", plan.pass.diagnostics.single().code)
    }

    /** ApplyFilter with unbounded filter refuses diagnostically. */
    @Test
    fun `apply filter with unbounded filter refuses diagnostically`() {
        val command = GPUApplyFilterCommandBuilder.build(
            commandId = GPUDrawCommandID(56),
            filterGraph = filterGraph(node("cf-1", "ColorFilter")),
            filterSource = GPUFilterSourcePlan(
                sourceLabel = "layer-source",
                boundsLabel = "0,0,64,48",
                colorTreatment = "premul-srgb",
            ),
            filterBounds = GPUSimpleFilterBounds(
                inputBoundsLabel = "0,0,64,48",
                outputBoundsLabel = "0,0,64,48",
                conservative = true,
                finite = false,
                width = 64,
                height = 48,
            ),
            target = GPUTargetFacts(width = 64, height = 48, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
        )

        val plan = GPUFirstRoutePlanner(capabilities = filterCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.filter.bounds_unbounded", plan.pass.diagnostics.single().code)
    }

    /** ApplyFilter with perspective transform refuses diagnostically. */
    @Test
    fun `apply filter with perspective transform refuses diagnostically`() {
        val command = GPUApplyFilterCommandBuilder.build(
            commandId = GPUDrawCommandID(57),
            filterGraph = filterGraph(node("cf-1", "ColorFilter")),
            filterSource = GPUFilterSourcePlan(
                sourceLabel = "layer-source",
                boundsLabel = "0,0,64,48",
                colorTreatment = "premul-srgb",
            ),
            filterBounds = GPUSimpleFilterBounds(
                inputBoundsLabel = "0,0,64,48",
                outputBoundsLabel = "0,0,64,48",
                conservative = true,
                finite = true,
                width = 64,
                height = 48,
            ),
            target = GPUTargetFacts(width = 64, height = 48, colorFormat = "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
            transform = GPUTransformFacts.perspective(),
        )

        val plan = GPUFirstRoutePlanner(capabilities = filterCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.transform.perspective", plan.pass.diagnostics.single().code)
    }

    /** Capability snapshot that enables filter routes. */
    private fun filterCapabilities(): GPUCapabilities =
        GPUCapabilities(
            implementation = GPUImplementationIdentity(
                facadeName = "test-gpu",
                implementationName = "unit",
                adapterName = "fixture-adapter",
                deviceName = "fixture-device",
            ),
            facts = listOf(
                GPUCapabilityFact(
                    name = "first_slice.blur_filter.native",
                    source = "unit-test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "blur-filter-fixture",
                ),
                GPUCapabilityFact(
                    name = "first_slice.color_matrix_filter.native",
                    source = "unit-test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "color-matrix-filter-fixture",
                ),
            ),
            snapshotId = "filter-test",
        )

    /** Builds a filter graph with given nodes. */
    private fun filterGraph(vararg nodes: GPUFilterNodeDescriptor): GPUFilterGraphDescriptor =
        GPUFilterGraphDescriptor(
            graphId = "filter-card",
            version = 1,
            sourceRole = "layer-source",
            nodes = nodes.toList(),
            edges = nodes
                .toList()
                .windowed(size = 2)
                .map { pair -> "${pair[0].nodeId.value}->${pair[1].nodeId.value}" },
            coordinateSpaces = listOf("layer", "target"),
            provenance = "test-fixture",
        )

    /** Builds a filter node descriptor. */
    private fun node(id: String, kind: String): GPUFilterNodeDescriptor =
        GPUFilterNodeDescriptor(
            nodeId = GPUFilterNodeID(id),
            nodeKind = kind,
            inputLabels = listOf("source"),
            parameterHash = "$kind:params",
        )

    private companion object {
        /** Shared rectangle for first-route refusal fixtures. */
        val firstRouteRect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f)

        /** Shared bounds for first-route refusal fixtures. */
        val firstRouteBounds = GPUBounds(
            left = 2f,
            top = 3f,
            right = 18f,
            bottom = 21f,
        )

        /** Shared rounded rectangle for first-expansion refusal fixtures. */
        val firstRouteRRect = GPURRect(
            rect = GPURect(left = 2f, top = 3f, right = 22f, bottom = 25f),
            radiusX = 4f,
            radiusY = 5f,
        )

        /** Default image draw test fixture parameters (reusable inline in builder calls). */
        const val IMAGE_DRAW_SOURCE_ID = "image:checker:v1"
        const val IMAGE_DRAW_SAMPLING_TILE_MODE_X = "clamp"
        const val IMAGE_DRAW_SAMPLING_TILE_MODE_Y = "clamp"
        const val IMAGE_DRAW_SAMPLING_FILTER = "linear"
        const val IMAGE_DRAW_SAMPLING_MIPMAP = "none"
        const val IMAGE_DRAW_PIXELS_WIDTH = 2
        const val IMAGE_DRAW_PIXELS_HEIGHT = 2
        const val IMAGE_DRAW_PIXELS_FORMAT = "RGBA8Unorm"
        const val IMAGE_DRAW_PIXELS_ROW_BYTES: Long = 8
        const val IMAGE_DRAW_PIXELS_ALPHA = "Premul"
        const val IMAGE_DRAW_PIXELS_COLOR_PROFILE = "srgb"
        const val IMAGE_DRAW_PIXELS_ORIENTATION = "Applied"
        const val IMAGE_DRAW_PIXELS_GENERATION: Long = 3
        const val IMAGE_DRAW_PIXELS_CONTENT_HASH = "sha256:checker-pixels-v1"
        const val IMAGE_DRAW_PIXELS_PROVENANCE = "unit-test"
    }
}
