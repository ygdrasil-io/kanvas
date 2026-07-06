package org.graphiks.kanvas.gpu.renderer.analysis

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
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
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectCornerRadii
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUPathFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedBlurStyle
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedMaskFilter
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterGraphDescriptor
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterNodeDescriptor
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterNodeID
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterSourcePlan
import org.graphiks.kanvas.gpu.renderer.filters.GPUSimpleFilterBounds
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterCropPlan
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterSamplingPlan
import org.graphiks.kanvas.gpu.renderer.routing.GPURouteDecision

/** Verifies the first native FillRect analysis, route, and pass builder. */
class FirstRoutePlannerTest {
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

        assertEquals("bounds:4.0,5.0,16.0,17.0", plan.pass.invocations.single().scissorBoundsHash)
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
            "unsupported.geometry.rrect_radii" to firstRRectRouteCommand(
                target = target,
                rrect = firstRouteRRect.copy(topLeft = firstRouteRRect.topLeft.copy(x = 0f)),
            ),
            "unsupported.geometry.rrect_radii" to firstRRectRouteCommand(
                target = target,
                rrect = firstRouteRRect.copy(topRight = firstRouteRRect.topRight.copy(y = Float.POSITIVE_INFINITY)),
            ),
            "unsupported.geometry.rrect_radii" to firstRRectRouteCommand(
                target = target,
                rrect = firstRouteRRect.copy(bottomRight = firstRouteRRect.bottomRight.copy(x = -1f)),
            ),
            "unsupported.geometry.rrect_radii" to firstRRectRouteCommand(
                target = target,
                rrect = firstRouteRRect.copy(bottomLeft = firstRouteRRect.bottomLeft.copy(x = 99f)),
            ),
            "unsupported.transform.rrect_scale_unproven" to firstRRectRouteCommand(
                target = target,
                transform = GPUTransformFacts.scale(x = 2f, y = 2f),
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
            "unsupported.blend.mode_unimplemented" to firstRRectRouteCommand(
                target = target,
                blend = GPUBlendFacts.unsupported(modeLabel = "multiply"),
            ),
            "unsupported.target.format_blend_incompatible" to firstRRectRouteCommand(
                target = target.copy(colorFormat = "bgra8unorm"),
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
            "unsupported.blend.mode_unimplemented" to firstRouteCommand(
                target = target,
                blend = GPUBlendFacts.unsupported(modeLabel = "multiply"),
            ),
            "unsupported.layer.elision_proof_missing" to firstRouteCommand(
                target = target,
                layer = GPULayerFacts.saveLayer(target = target),
            ),
            "unsupported.layer.filter_chain" to firstRouteCommand(
                target = target,
                layer = GPULayerFacts.root(target = target).copy(requiresFilter = true),
            ),
            "unsupported.destination_read.required" to firstRouteCommand(
                target = target,
                blend = GPUBlendFacts.destinationReadRequired(),
            ),
            "unsupported.target.format_blend_incompatible" to firstRouteCommand(
                target = target.copy(colorFormat = "bgra8unorm"),
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

    /** Accepted FillPath with blur MaskFilter builds blur-aware prepared CPU route. */
    @Test
    fun `fill path with blur mask filter builds blur mask prepared route`() {
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
        assertEquals("prepared.path_fill.blur_mask", analysisDecision.routeDecisionLabel)
        assertEquals("blur-mask.sample.path-fill", routeDecision.route.consumerKind)
        assertEquals("path.fill.blur_mask", plan.pass.invocations.single().renderStepId.value)
        assertEquals(listOf("pending.pipeline.fill_path.blur_mask.rgba8unorm.src_over"), plan.pass.pipelineKeys)
        assertEquals("pending.pipeline.fill_path.blur_mask.rgba8unorm.src_over", plan.pass.invocations.single().pipelineKeyHash)
        assertEquals("analysis.fill_path.27", plan.pass.invocations.single().analysisRecordId)
        assertEquals(27, plan.pass.invocations.single().commandIdValue)
        assertEquals("path_fill", plan.pass.invocations.single().role)
        assertEquals("pass.path_fill.27", plan.pass.passId)
        assertEquals(
            "blur-mask.path-fill.path_triangle_v1.blur:normal_sigma=6.2735",
            routeDecision.route.artifactKey.value,
        )
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

            assertEquals("prepared.path_fill.blur_mask", analysisDecision.routeDecisionLabel)
            assertContains(plan.pass.invocations.single().renderStepId.value, "blur_mask")
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

    /** DrawImageRect with blank image source ID refuses diagnostically. */
    @Test
    fun `draw image rect with blank source id refuses diagnostically`() {
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

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)
        assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        assertEquals("unsupported.image.source_id_empty", plan.pass.diagnostics.single().code)
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
        assertEquals("unsupported.image.pixels_descriptor_invalid", plan.pass.diagnostics.single().code)
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
                target = target.copy(colorFormat = "bgra8unorm"),
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
            ),
            snapshotId = "first-route-test",
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
            ),
            snapshotId = "rrect-route-test",
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
            facts = firstSlicePathFillCapabilities().facts + GPUCapabilityFact(
                name = "first_slice.path_fill.stencil_cover",
                source = "unit-test",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "stencil-cover-fixture",
            ),
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
