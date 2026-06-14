package org.graphiks.kanvas.gpu.renderer.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
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

        val plan = GPUFirstRoutePlanner(capabilities = firstSliceCapabilities()).plan(command)
        assertIs<GPURouteDecision.Native>(plan.routeDecision)

        assertEquals("bounds:4.0,5.0,16.0,17.0", plan.pass.invocations.single().scissorBoundsHash)
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

    /** Capability snapshot with no validity facts for missing-capability refusal tests. */
    private fun emptyCapabilities(): GPUCapabilities =
        firstSliceCapabilities().copy(facts = emptyList())

    /** Command plus capability facts for one refusal fixture. */
    private data class RefusalFixture(
        val command: NormalizedDrawCommand.FillRect,
        val capabilities: GPUCapabilities,
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
    }
}
