package org.graphiks.kanvas.gpu.renderer.state

import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadAction
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadBounds
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadRequirement
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategy
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategyGatePlan
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategyPlanner
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategyRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BlendAllowlistGateTest {
    @Test
    fun fixedFunctionAllowlistedModesProduceStateAndPipelineEvidenceDumps() {
        val planner = GPUBlendAllowlistPlanner()
        val src = planner.plan(blendRequest(mode = GPUBlendMode.Src, commandId = "blend:src"))
        val srcOver = planner.plan(blendRequest(mode = GPUBlendMode.SrcOver, commandId = "blend:src-over"))
        val dstOver = planner.plan(blendRequest(mode = GPUBlendMode.DstOver, commandId = "blend:dst-over"))

        assertEquals("gpu-renderer.blend-allowlist", srcOver.evidenceRow)
        assertEquals("GPUNative", srcOver.routeKind)
        assertEquals("TargetNative", srcOver.classification)
        assertFalse(srcOver.promoted)
        assertTrue(srcOver.productActivation)
        assertFalse(srcOver.materialized)
        assertEquals(GPUDestinationReadRequirement.FixedFunctionBlend, srcOver.destinationReadRequirement)
        assertEquals(GPUDestinationReadStrategy.FixedFunction, srcOver.destinationReadStrategy)
        assertEquals(GPUDestinationReadAction.UseFixedFunctionBlend, srcOver.destinationReadAction)
        assertFalse(srcOver.plan.requiresDestinationRead)
        assertEquals("accepted.blend.fixed_function", srcOver.diagnostics.single().code)
        assertFalse(srcOver.diagnostics.single().terminal)
        assertEquals("accepted.blend.fixed_function", src.diagnostics.single().code)
        assertNotEquals(src.blendStateHash, srcOver.blendStateHash)
        assertNotEquals(srcOver.blendStateHash, dstOver.blendStateHash)
        assertNotEquals(srcOver.pipelineKeyHash, dstOver.pipelineKeyHash)

        assertEquals(
            listOf(
                "blend:allowlist row=gpu-renderer.blend-allowlist routeKind=GPUNative classification=TargetNative promoted=false productActivation=true materialized=false command=blend:src-over mode=SrcOver plan=FixedFunctionBlend target=rgba8unorm state=${srcOver.blendStateHash} pipeline=${srcOver.pipelineBlendStateKey}",
                "blend:alpha input=premultiplied output=premultiplied premultiply=false clamp=true",
                "blend:fixed-function mode=SrcOver color=src=one,dst=one-minus-src-alpha,op=add alpha=src=one,dst=one-minus-src-alpha,op=add writeMask=rgba destinationRead=FixedFunctionAttachmentBlend",
                "blend:pipeline-key material=material:solid renderStep=rect-fill blendState=${srcOver.blendStateHash} pipelineKey=${srcOver.pipelineKeyHash}",
                "blend:diagnostic code=accepted.blend.fixed_function terminal=false mode=SrcOver",
                "blend:nonclaim nativeAdvancedBlend=false shaderBlend=false framebufferFetch=false inputAttachment=false destinationReadTexture=false productActivation=true",
            ),
            srcOver.dumpLines(),
        )
    }

    @Test
    fun destinationReadBlendModesRefuseWithoutAcceptedStrategy() {
        val planner = GPUBlendAllowlistPlanner()
        val cases = listOf(GPUBlendMode.Screen to "screen", GPUBlendMode.Multiply to "multiply")

        cases.forEach { (mode, label) ->
            val result = planner.plan(blendRequest(mode = mode, commandId = "blend:$label"))

            assertEquals("RefuseDiagnostic", result.routeKind)
            assertEquals(GPUDestinationReadRequirement.TargetCopy, result.destinationReadRequirement)
            assertEquals(GPUDestinationReadStrategy.Refuse, result.destinationReadStrategy)
            assertEquals(GPUDestinationReadAction.Refuse, result.destinationReadAction)
            assertEquals("unsupported.blend.dst_read_requires_intermediate", result.diagnostics.single().code)
            assertEquals(
                listOf(
                    "blend:allowlist.refused row=gpu-renderer.blend-allowlist routeKind=RefuseDiagnostic classification=TargetNative promoted=false productActivation=true materialized=false command=blend:$label mode=$mode plan=ShaderBlendWithDstRead reason=unsupported.blend.dst_read_requires_intermediate target=rgba8unorm",
                    "blend:destination-read mode=$mode requirement=ShaderBlend strategy=RefuseDiagnostic action=Refuse plan=missing planStrategy=none activeAttachmentSampled=false",
                    "blend:nonclaim nativeAdvancedBlend=false shaderBlend=false framebufferFetch=false inputAttachment=false destinationReadTexture=false productActivation=true",
                ),
                result.dumpLines(),
            )
        }

        val custom = planner.plan(blendRequest(mode = GPUBlendMode.Custom, commandId = "blend:custom"))

        assertEquals("RefuseDiagnostic", custom.routeKind)
        assertEquals(GPUDestinationReadRequirement.Refused, custom.destinationReadRequirement)
        assertEquals(GPUDestinationReadStrategy.Refuse, custom.destinationReadStrategy)
        assertEquals(GPUDestinationReadAction.Refuse, custom.destinationReadAction)
        assertEquals("unsupported.blend.mode_unimplemented", custom.diagnostics.single().code)
    }

    @Test
    fun `screen blend accepts shader route when destination read plan is valid`() {
        val destination = GPUDestinationReadStrategyPlanner().plan(
            GPUDestinationReadStrategyRequest(
                commandId = "cmd-screen",
                requirement = GPUDestinationReadRequirement.TargetCopy,
                strategy = GPUDestinationReadStrategy.CopyTarget,
                action = GPUDestinationReadAction.SplitPassAndCopyTarget,
                bounds = GPUDestinationReadBounds(
                    boundsLabel = "bounds:screen",
                    conservative = true,
                    pixelAligned = true,
                    copyBoundsLabel = "copy:screen",
                    width = 32,
                    height = 32,
                    targetWidth = 320,
                    targetHeight = 200,
                ),
                sourceTargetLabel = "surface:main",
                sourceUsageLabels = setOf("render_attachment", "copy_src"),
                copyUsageLabels = setOf("copy_dst", "texture_binding"),
                targetFormatClass = "rgba8unorm",
                targetGeneration = 7,
            ),
        )

        val plan = GPUBlendAllowlistPlanner().plan(
            GPUBlendAllowlistRequest(
                commandId = "cmd-screen",
                mode = GPUBlendMode.Screen,
                targetFormatClass = "rgba8unorm",
                materialKeyHash = "material:screen",
                renderStepIdentity = "rect-fill",
                destinationReadPlan = destination,
                destinationReadCopyBoundsLabel = "copy:screen",
                destinationReadGeneration = 7,
            ),
        )

        assertEquals(GPUBlendPlanKind.ShaderBlendWithDstRead, plan.planKind)
        assertEquals("GPUNative", plan.routeKind)
        assertEquals(false, plan.diagnostics.any { it.terminal })
        assertEquals(GPUDestinationReadStrategy.CopyTarget, plan.destinationReadStrategy)
        assertEquals(GPUDestinationReadAction.SplitPassAndCopyTarget, plan.destinationReadAction)
        assertTrue(plan.dumpLines().any { it.contains("shaderBlend=true") })
    }

    @Test
    fun destinationReadStrategyMismatchRefusesShaderBlendRoutes() {
        val destinationReadPlan = GPUDestinationReadStrategyPlanner().plan(destinationReadRequest())

        val mismatchedPlan = GPUBlendAllowlistPlanner().plan(
            blendRequest(
                mode = GPUBlendMode.Screen,
                commandId = "blend:multiply",
                destinationReadPlan = destinationReadPlan,
            ),
        )

        assertEquals("unsupported.blend.destination_read_plan_mismatch", mismatchedPlan.diagnostics.single().code)
        assertEquals(GPUDestinationReadStrategy.Refuse, mismatchedPlan.destinationReadStrategy)
        assertEquals(GPUDestinationReadAction.Refuse, mismatchedPlan.destinationReadAction)

        val mismatchedIntermediatePlan = GPUDestinationReadStrategyPlanner().plan(
            destinationReadRequest(
                requirement = GPUDestinationReadRequirement.ExistingIntermediate,
                strategy = GPUDestinationReadStrategy.BindIntermediate,
                action = GPUDestinationReadAction.UseExistingIntermediate,
                targetFormatClass = "bgra8unorm",
            ),
        )
        val mismatchedIntermediate = GPUBlendAllowlistPlanner().plan(
            blendRequest(
                mode = GPUBlendMode.Screen,
                commandId = "blend:screen",
                destinationReadPlan = mismatchedIntermediatePlan,
            ),
        )

        assertEquals(
            "unsupported.blend.destination_read_plan_mismatch",
            mismatchedIntermediate.diagnostics.single().code,
        )
        assertEquals(GPUDestinationReadStrategy.Refuse, mismatchedIntermediate.destinationReadStrategy)
        assertEquals(GPUDestinationReadAction.Refuse, mismatchedIntermediate.destinationReadAction)
    }

    @Test
    fun activeAttachmentDestinationSamplingRefusesBeforeStrategyLookup() {
        val result = GPUBlendAllowlistPlanner().plan(
            blendRequest(
                mode = GPUBlendMode.Screen,
                commandId = "blend:screen",
                activeAttachmentSampled = true,
            ),
        )

        assertEquals("unsupported.destination_read.active_attachment_sampled", result.diagnostics.single().code)
        assertEquals(
            listOf(
                "blend:allowlist.refused row=gpu-renderer.blend-allowlist routeKind=RefuseDiagnostic classification=TargetNative promoted=false productActivation=true materialized=false command=blend:screen mode=Screen plan=ShaderBlendWithDstRead reason=unsupported.destination_read.active_attachment_sampled target=rgba8unorm",
                "blend:destination-read mode=Screen requirement=ShaderBlend strategy=RefuseDiagnostic action=Refuse plan=missing planStrategy=none activeAttachmentSampled=true",
                "blend:nonclaim nativeAdvancedBlend=false shaderBlend=false framebufferFetch=false inputAttachment=false destinationReadTexture=false productActivation=true",
            ),
            result.dumpLines(),
        )

        val fixedFunctionResult = GPUBlendAllowlistPlanner().plan(
            blendRequest(
                mode = GPUBlendMode.SrcOver,
                commandId = "blend:src-over",
                activeAttachmentSampled = true,
            ),
        )

        assertEquals("unsupported.destination_read.active_attachment_sampled", fixedFunctionResult.diagnostics.single().code)
        assertEquals("RefuseDiagnostic", fixedFunctionResult.routeKind)
        assertEquals(GPUDestinationReadStrategy.Refuse, fixedFunctionResult.destinationReadStrategy)
        assertEquals(GPUDestinationReadAction.Refuse, fixedFunctionResult.destinationReadAction)
    }

    @Test
    fun fixedFunctionBlendRefusesUnacceptedAlphaPlan() {
        val result = GPUBlendAllowlistPlanner().plan(
            blendRequest(
                mode = GPUBlendMode.SrcOver,
                commandId = "blend:src-over",
                alphaPlan = GPUAlphaPlan(
                    inputAlpha = "unpremultiplied",
                    outputAlpha = "premultiplied",
                    premultiply = true,
                    clamp = true,
                ),
            ),
        )

        assertEquals("RefuseDiagnostic", result.routeKind)
        assertEquals(GPUDestinationReadStrategy.Refuse, result.destinationReadStrategy)
        assertEquals(GPUDestinationReadAction.Refuse, result.destinationReadAction)
        assertEquals("unsupported.blend.alpha_plan_unaccepted", result.diagnostics.single().code)
        assertEquals(
            listOf(
                "blend:allowlist.refused row=gpu-renderer.blend-allowlist routeKind=RefuseDiagnostic classification=TargetNative promoted=false productActivation=true materialized=false command=blend:src-over mode=SrcOver plan=FixedFunctionBlend reason=unsupported.blend.alpha_plan_unaccepted target=rgba8unorm",
                "blend:destination-read mode=SrcOver requirement=FixedFunctionOnly strategy=RefuseDiagnostic action=Refuse plan=missing planStrategy=none activeAttachmentSampled=false",
                "blend:nonclaim nativeAdvancedBlend=false shaderBlend=false framebufferFetch=false inputAttachment=false destinationReadTexture=false productActivation=true",
            ),
            result.dumpLines(),
        )
    }
}

private fun blendRequest(
    mode: GPUBlendMode,
    commandId: String,
    destinationReadPlan: GPUDestinationReadStrategyGatePlan? = null,
    activeAttachmentSampled: Boolean = false,
    alphaPlan: GPUAlphaPlan = GPUAlphaPlan(
        inputAlpha = "premultiplied",
        outputAlpha = "premultiplied",
        premultiply = false,
        clamp = true,
    ),
): GPUBlendAllowlistRequest = GPUBlendAllowlistRequest(
    commandId = commandId,
    mode = mode,
    targetFormatClass = "rgba8unorm",
    materialKeyHash = "material:solid",
    renderStepIdentity = "rect-fill",
    alphaPlan = alphaPlan,
    destinationReadPlan = destinationReadPlan,
    destinationReadCopyBoundsLabel = destinationReadPlan?.plan?.bounds?.copyBoundsLabel,
    destinationReadGeneration = destinationReadPlan?.plan?.binding?.generation,
    activeAttachmentSampled = activeAttachmentSampled,
)

private fun destinationReadRequest(
    requirement: GPUDestinationReadRequirement = GPUDestinationReadRequirement.TargetCopy,
    strategy: GPUDestinationReadStrategy = GPUDestinationReadStrategy.CopyTarget,
    action: GPUDestinationReadAction = GPUDestinationReadAction.SplitPassAndCopyTarget,
    targetFormatClass: String = "rgba8unorm",
): GPUDestinationReadStrategyRequest = GPUDestinationReadStrategyRequest(
    label = "accepted",
    commandId = "blend:screen",
    requirement = requirement,
    strategy = strategy,
    action = action,
    bounds = destinationBounds(),
    sourceTargetLabel = "target:main",
    sourceUsageLabels = setOf("render_attachment", "copy_src"),
    copyUsageLabels = setOf("copy_dst", "texture_binding"),
    targetFormatClass = targetFormatClass,
    targetGeneration = 42,
)

private fun destinationBounds(): GPUDestinationReadBounds = GPUDestinationReadBounds(
    boundsLabel = "shape-local",
    conservative = true,
    pixelAligned = true,
    requestedBoundsLabel = "shape-local",
    unclippedBoundsLabel = "0,0,80,40",
    clippedBoundsLabel = "4,8,64,32",
    copyBoundsLabel = "4,8,64,32",
    originX = 4,
    originY = 8,
    width = 64,
    height = 32,
    targetWidth = 128,
    targetHeight = 96,
)
