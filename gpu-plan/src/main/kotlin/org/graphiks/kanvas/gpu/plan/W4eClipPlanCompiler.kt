package org.graphiks.kanvas.gpu.plan

import java.security.MessageDigest
import java.util.Collections
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.ClipEntry
import org.graphiks.kanvas.render.ir.ClipOperation
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.ClipTransformSnapshot
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.PaintStyleNode
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneSemanticValidationResult
import org.graphiks.kanvas.render.ir.SceneSemanticValidator
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.math.geometry.ClipGeometryF32
import org.graphiks.math.geometry.ClipOperation as MathClipOperation
import org.graphiks.math.geometry.ClipPreparedEntryF32
import org.graphiks.math.geometry.ClipPreparationPolicyF64
import org.graphiks.math.geometry.ClipStackPreparationResult
import org.graphiks.math.geometry.ClipWorkUsageI64
import org.graphiks.math.geometry.CornerRadiiF64
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.InversePathDrawMode
import org.graphiks.math.geometry.InversePathGeometryF32
import org.graphiks.math.geometry.InverseInteriorCoverageF32
import org.graphiks.math.geometry.InversePathPreparationResult
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokePolicyF64
import org.graphiks.math.geometry.RRectF64
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.matrix.ClipTransformGeometryF64
import org.graphiks.math.matrix.ClipTransformInputF64
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.matrix.prepareTransformedClipStackGeometryF32
import org.graphiks.math.matrix.prepareTransformedInversePathGeometryF32
import org.graphiks.math.matrix.toMatrix3x3F64

/**
 * W4e planning authority.  Color rendering is deliberately delegated to the W4d.2 graph
 * constructor; this compiler owns only clip preparation, pooled mask resources, and typed
 * consumer insertion.
 */
public class W4eClipPlanCompiler(
    private val clipPolicyF64: ClipPreparationPolicyF64,
) : GpuPlanCompiler {
    public constructor() : this(ClipPreparationPolicyF64())

    /**
     * Producer-only W4e seam for an already admitted non-Path color consumer. All clip math,
     * mask selection, producer samples and storage remain owned by this compiler.
     */
    public fun sealClipOnly(
        clip: ClipStackNode,
        extent: SizeI32,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): W4eClipOnlyPlan {
        require(extent.width in 1..capabilities.maxTextureDimension2D && extent.height in 1..capabilities.maxTextureDimension2D) {
            "unsupported.w4e.clip-only-extent"
        }
        val operations = when (clip) {
            is ClipStackNode.Operations -> clip
            is ClipStackNode.DeviceRect -> ClipStackNode.Operations.of(listOf(ClipEntry(
                GeometryNode.Rect.of(clip.copyBounds()), ClipOperation.INTERSECT, clip.antiAlias)))
            else -> error("unsupported.w4e.clip-only-empty")
        }
        val domain = RectI32(0, 0, extent.width, extent.height)
        val prepared = prepare(operations, domain, ClipWorkUsageI64(), forceMask = true)
        require(prepared is PreparedResult.Ready) { "unsupported.w4e.clip-only-preparation" }
        val stack = prepared.stack
        require(stack.realization == Realization.Mask) { "unsupported.w4e.clip-only-mask" }
        require(capabilityRefusal(capabilities, listOf(stack)) == null) { "unsupported.w4e.clip-only-capability" }
        val prefixCountI32 = Math.addExact(1, Math.multiplyExact(stack.emittedEntries.size, 2))
        // The producer token exports one post-prefix read. The owning color graph seals
        // the final mask lifetime against its actual consumers, before any allocation.
        val lastConsumerPassIndexExclusiveI32 = Math.addExact(prefixCountI32, 1)
        val aa = stack.emittedEntries.any { it.antiAlias && it.geometryF32 !is ClipGeometryF32.Rect }
        val hardPath = stack.emittedEntries.any { !it.antiAlias && it.geometryF32 is ClipGeometryF32.Path }
        val oneBytesI64 = ClipPlanBudget.checkedMaskTextureBytesI64(extent.width, extent.height, 1)
        val fourBytesI64 = ClipPlanBudget.checkedMaskTextureBytesI64(extent.width, extent.height, 4)
        val resources = mutableListOf<PlanResource>()
        val ids = maskResources(resources, MaskResourceLayout(0, 0, oneBytesI64, fourBytesI64,
            lastConsumerPassIndexExclusiveI32, lastConsumerPassIndexExclusiveI32, prefixCountI32,
            prefixCountI32.takeIf { aa }, prefixCountI32.takeIf { aa }, prefixCountI32.takeIf { hardPath }, aa, hardPath), extent)
        val group = PlanAtomicGroupId("w4e.clip-only:0")
        var accumulator = ids.firstAccumulator
        val passes = mutableListOf<PlanPass>(PlanPass.ClipMaskInitialize(0, accumulator, domain, 1f, group))
        stack.emittedEntries.forEachIndexed { indexI32, entry ->
            val useAa = entry.antiAlias && entry.geometryF32 !is ClipGeometryF32.Rect
            val target = if (useAa) requireNotNull(ids.multisampleScratch) else ids.scratch
            val resolve = if (useAa) ids.scratch else null
            val depth = when {
                useAa -> ids.aaDepth
                entry.geometryF32 is ClipGeometryF32.Path -> ids.hardDepth
                else -> null
            }
            passes += PlanPass.ClipMaskProducer(indexI32, target, resolve, depth, if (useAa) 4 else 1,
                entry.geometryF32, group, inverseCoverage = entry.inverseFill, antiAlias = entry.antiAlias)
            val output = if (accumulator == ids.firstAccumulator) ids.secondAccumulator else ids.firstAccumulator
            passes += PlanPass.ClipMaskFold(indexI32, accumulator, resolve ?: target, output,
                entry.operation.toPlanOperation(), domain, group)
            accumulator = output
        }
        resources.forEach { resource -> require(capabilities.supportsTexture(requireNotNull(resource.format),
            resource.sampleCountI32, resource.usages())) { "unsupported.w4e.clip-only-capability" } }
        fun dataResource(role: PlanResourceRole, usage: PlanResourceUsage, bytesI64: Long) = PlanResource.of(
            role, 1, PlanResourceKind.Buffer, null, null, bytesI64,
            setOf(PlanResourceUsage.CopyDestination, usage), PlanResourceLifetime.FrameLocal, 0, prefixCountI32)
        val data = PlanDrawDataResources(planResourceId(PlanResourceRole.VertexData, 1),
            planResourceId(PlanResourceRole.IndexData, 1), planResourceId(PlanResourceRole.UniformData, 1))
        val declarations = listOf(dataResource(PlanResourceRole.VertexData, PlanResourceUsage.Vertex, 32L),
            dataResource(PlanResourceRole.IndexData, PlanResourceUsage.Index, 24L),
            dataResource(PlanResourceRole.UniformData, PlanResourceUsage.Uniform, 256L))
        val payload = requireNotNull(W4eNativePayloadPlan.fromClipPrefix(passes, resources + declarations,
            extent, capabilities, data)) { "resource.w4e.clip-only-native-payload" }
        require(capabilities.maxBindGroupsI32?.let { it >= 1 } == true &&
            capabilities.maxBindingsPerBindGroupI32?.let { it >= 2 } == true &&
            capabilities.maxSampledTexturesPerShaderStageI32?.let { it >= 2 } == true &&
            capabilities.maxUniformBuffersPerShaderStageI32?.let { it >= 1 } == true &&
            payload.uniformSlices.all { it.byteSize <= (capabilities.maxUniformBufferBindingSizeBytesI64 ?: 0L) }) {
            "unsupported.w4e.clip-only-binding-capability"
        }
        resources += listOf(dataResource(PlanResourceRole.VertexData, PlanResourceUsage.Vertex, payload.vertexCapacityBytes),
            dataResource(PlanResourceRole.IndexData, PlanResourceUsage.Index, payload.indexCapacityBytes),
            dataResource(PlanResourceRole.UniformData, PlanResourceUsage.Uniform, payload.uniformCapacityBytes))
        require(resources.fold(0L) { totalI64, resource -> Math.addExact(totalI64, resource.byteSize) } <= budget.maxFrameLocalBytes) {
            "resource.w4e.clip-only-budget"
        }
        return W4eClipOnlyPlan(operations, extent, capabilities, budget, passes, resources, accumulator, payload)
    }

    private val w4dHardSeam = W4dGeneralPathPlanCompiler(
        strokePolicyF64 = org.graphiks.math.geometry.PathStrokePolicyF64(),
        acceptsNarrowTransforms = true,
    )
    private val w4dAaSeam = W4dGeneralPathPlanCompiler(
        strokePolicyF64 = org.graphiks.math.geometry.PathStrokePolicyF64(),
        acceptsNarrowTransforms = true,
        forceAaFrame = true,
    )

    override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection {
        if (scene.extent != target.extent || scene.colorSpace != target.colorSpace) return invalid("Scene and target differ")
        if (SceneSemanticValidator.validate(scene) is SceneSemanticValidationResult.Invalid) return invalid("Scene validation failed")
        if (scene.colorSpace != ColorSpace.SRGB) return gap("W4e supports only sRGB")
        w4dHardSeam.finiteSceneError(scene)?.let { return invalid(it) }
        if (scene.any { it !is SceneCommand.Draw && it !is SceneCommand.SetTransform &&
            it !is SceneCommand.SetClip && it !is SceneCommand.Annotation })
            return gap("W4e scene commands are outside the construction seam")
        val drawCommands = scene.filterIsInstance<SceneCommand.Draw>()
        val operationDraws = drawCommands
            .filter { it.node.clip is ClipStackNode.Operations }
        val inverseDraws = drawCommands.filter { it.node.hasInversePathFillRule() }
        if (operationDraws.isEmpty() && inverseDraws.isEmpty()) {
            return gap("W4e requires an explicitly captured complex clip or inverse path draw")
        }
        val finalBlendsByCommandI32 = scene.withIndex().mapNotNull { (indexI32, command) ->
            val node = (command as? SceneCommand.Draw)?.node ?: return@mapNotNull null
            val blend = FinalBlendPlanner.plan(node.blend, CoveragePlan.FullOrScissor, SamplePlan.SingleSample,
                PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL.blendTargetClampV1(),
                BlendCoverageApplicationV1.SourceMultiplication,
                if (node.clip is ClipStackNode.Operations || node.hasInversePathFillRule())
                    BlendCoverageEncodingV1.ScalarCoverageInShader else BlendCoverageEncodingV1.FullOrScissor)
                ?: return@mapNotNull null
            indexI32 to if (blend is BlendPlan.FixedFunctionV1 && blend.mode == org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER)
                BlendPlan.SrcOver else blend
        }.toMap()
        val noOpCommandsI32 = finalBlendsByCommandI32.filterValues { it == BlendPlan.NoOpV1 }.keys
        val activeDrawCommands = scene.withIndex().filter { it.value is SceneCommand.Draw && it.index !in noOpCommandsI32 }
            .map { it.value as SceneCommand.Draw }
        if (finalBlendsByCommandI32.values.any { it != BlendPlan.SrcOver } &&
            drawCommands.any { it.node.geometry !is GeometryNode.Path })
            return gap("W5b W4e color ownership retains actual Path geometry")
        if (activeDrawCommands.isEmpty()) return GpuPlanSelection.Candidate(NoOpCandidate(this, scene.canonicalId, target))
        activeDrawCommands.filter { it.node.clip is ClipStackNode.Operations }.forEach { command ->
            val operations = command.node.clip as ClipStackNode.Operations
            if (operations.entryCount > maxClipEntriesPerStackI32()) {
                return limit("W4e accepts at most ${maxClipEntriesPerStackI32()} clip entries per stack")
            }
        }
        val totalVisualDrawCount = activeDrawCommands.size
        if (totalVisualDrawCount > MAX_DRAWS) return limit("W4e accepts at most 512 visual path draws")

        val domain = RectI32(0, 0, scene.extent.width, scene.extent.height)
        val normalized = mutableListOf<SceneCommand>()
        val preparedByKey = linkedMapOf<ClipStackReuseKey, PreparedStack>()
        val inverseByCommand = mutableMapOf<Int, InversePathGeometryF32>()
        val inverseDomainSourcesByCommand = mutableMapOf<Int, PathDrawGeometry.InverseDomainSource>()
        val actuallyEmptyInverseCommands = mutableSetOf<Int>()
        var frameUsage = ClipWorkUsageI64()
        var ownsW4eFeature = noOpCommandsI32.isNotEmpty()

        scene.withIndex().forEach { (index, command) ->
            when (command) {
                is SceneCommand.Draw -> {
                    if (index in noOpCommandsI32) {
                        normalized += SceneCommand.Annotation.of(org.graphiks.math.geometry.RectF32(0f, 0f, 0f, 0f),
                            "w5b.w4e.no-op", index.toString())
                        return@forEach
                    }
                    val operations = command.node.clip as? ClipStackNode.Operations
                    // A W4d DeviceRect remains the construction seam's scissor authority.  An
                    // inverse domain is bounded by that same device-space rectangle before the
                    // W4e strategy is sealed, rather than relying on a later packet scissor.
                    val inverseDomain = command.node.w4dDeviceRectDomainOrNull(domain) ?: domain
                    val inverse = when (val preparedInverse = command.node.prepareInversePathOrNull(inverseDomain)) {
                        is InverseResult.None -> null
                        is InverseResult.Ready -> preparedInverse.geometry
                        is InverseResult.Invalid -> return invalid(preparedInverse.message)
                        is InverseResult.Limit -> return limit(preparedInverse.message)
                    }
                    val prepared = operations?.let { stack ->
                        ownsW4eFeature = true
                        val key = reuseKey(stack, target, command.node.coverage)
                            ?: return legacy("LegacyUnavailable clip transforms cannot be promoted")
                        preparedByKey[key] ?: when (val value = prepare(stack, domain, frameUsage, inverse != null)) {
                            is PreparedResult.Ready -> {
                                frameUsage = value.stack.frameUsageAfterI64
                                value.stack.also { preparedByKey[key] = it }
                            }
                            is PreparedResult.Invalid -> return invalid(value.message)
                            is PreparedResult.Limit -> return limit(value.message)
                        }
                    }
                    if (inverse != null) {
                        ownsW4eFeature = true
                        prepared?.forceMaskForInverse()
                        inverseByCommand[index] = inverse
                        if (inverse.interiorCoverageF32 == InverseInteriorCoverageF32.Zero) {
                            val sourcePath = requireNotNull(command.node.geometry as? GeometryNode.Path) {
                                "W4e inverse planning requires a path source"
                            }.path
                            if (sourcePath.segmentCount == 0) {
                                actuallyEmptyInverseCommands += index
                            } else {
                                inverseDomainSourcesByCommand[index] = PathDrawGeometry.InverseDomainSource.of(
                                    sourcePath,
                                    command.node.transform,
                                )
                            }
                        }
                    }
                    val sealed = finalBlendsByCommandI32[index]
                    val construction = command.node.normalizedForW4dConstructionSeam(domain, inverse)
                    normalized += SceneCommand.Draw(if (sealed == null) construction else construction.copy(blend = org.graphiks.kanvas.render.ir.BlendNode.SrcOver))
                    // The candidate keeps the prepared stack map keyed by this exact canonical stack identity.
                    prepared?.consumerIndexes?.add(index)
                }
                else -> normalized += command
            }
        }
        if (!ownsW4eFeature) return gap("W4e requires an explicitly captured complex clip or inverse path draw")

        val normalizedScene = SceneSnapshot.of(scene.extent, scene.colorSpace, normalized)
        val forceAaFrame = preparedByKey.values.any { it.requiresAaFrame }
        val constructionSeam = if (forceAaFrame) w4dAaSeam else w4dHardSeam
        val base = when (val selected = constructionSeam.select(normalizedScene, target)) {
            is GpuPlanSelection.MaterialOnlyRefusal -> return GpuPlanSelection.MaterialOnlyRefusal(
                if (selected.capabilityId == W4dGeneralPathPlanCompiler.W5A_AA_CAPABILITY_ID) W5A_AA_CAPABILITY_ID else W5A_HARD_CAPABILITY_ID,
                scene.canonicalId, target, selected.materialRefusals,
            )
            is GpuPlanSelection.Candidate -> selected.candidate
            is GpuPlanSelection.NotCandidate -> return gap("W4e draw scope is outside the W4d.2 construction seam")
            is GpuPlanSelection.InvalidScene -> return invalid("W4d.2 rejected normalized W4e draw facts")
            is GpuPlanSelection.ResourceLimitExceeded -> return limit("W4d.2 rejected normalized W4e draw resources")
        }
        return GpuPlanSelection.Candidate(Candidate(
            this, scene.canonicalId, target, constructionSeam, base, preparedByKey.values.toList(), inverseByCommand,
            inverseDomainSourcesByCommand, actuallyEmptyInverseCommands, finalBlendsByCommandI32,
        ))
    }

    override fun plan(candidate: GpuPlanCandidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget): RenderPlanResult<RenderGraph> {
        if (candidate is NoOpCandidate) {
            if (candidate.owner !== this) return invalidCandidate()
            return try {
                RenderPlanResult.Ready(RenderGraph.issueW5bGeometry(W5bGeometryLanePlanV3.clearOnly(
                    PlanId("w5b.w4e.no-op.${candidate.sceneCanonicalId.value}"), W5B_HARD_CAPABILITY_ID,
                    SizeI32(candidate.target.extent.width, candidate.target.extent.height), capabilities, budget, null)))
            } catch (_: ArithmeticException) {
                resource(W4ePlanDiagnostics.SizeOverflow, "W4e clear-only resources overflowed")
            } catch (_: IllegalArgumentException) {
                resource(W4ePlanDiagnostics.BudgetFrameLocalExceeded, "W4e clear-only frame exceeds its resource contract")
            }
        }
        val selected = candidate as? Candidate ?: return invalidCandidate()
        if (selected.owner !== this || !selected.matches()) return invalidCandidate()
        val requiresAa = selected.capabilityId == W5A_AA_CAPABILITY_ID
        val survivingFinalBlendsByCommandI32 = selected.finalBlendsByCommandI32.filterValues { it != BlendPlan.NoOpV1 }
        val successor = survivingFinalBlendsByCommandI32.values.any { it != BlendPlan.SrcOver }
        val elidedNoOps = selected.finalBlendsByCommandI32.values.any { it == BlendPlan.NoOpV1 }
        fun constructionGap(diagnostics: List<RenderDiagnostic>): RenderPlanResult<Nothing> =
            if (successor || elidedNoOps) RenderPlanResult.GapOnPromotedScope(diagnostics)
            else RenderPlanResult.GapNotMigrated(diagnostics)
        // The original W4e feature may belong only to an elided NoOp. Its surviving plain
        // Paths retain the construction seam's General authority, not an empty W4e inventory.
        if (selected.stacks.isEmpty() && selected.inverseByCommand.isEmpty() && elidedNoOps) {
            return when (val result = selected.constructionSeam.plan(selected.base, capabilities, budget)) {
                is RenderPlanResult.Ready -> when {
                    !successor && requiresAa -> result
                    requiresAa -> promoted("W5b final blending requires the admitted single-sample path topology")
                    else -> try {
                        RenderPlanResult.Ready(issueW5bGeneralPathGraph(result.plan, survivingFinalBlendsByCommandI32))
                    } catch (_: IllegalArgumentException) {
                        resource(W4ePlanDiagnostics.PlanIdentityInvalid, "W4e plain survivor graph is invalid")
                    }
                }
                is RenderPlanResult.GapNotMigrated -> constructionGap(result.diagnostics)
                else -> result
            }
        }
        val maskStacks = selected.stacks.filter { it.realization == Realization.Mask }
        capabilityRefusal(capabilities, maskStacks)?.let { return it }
        val basePreview = when (val result = selected.constructionSeam.preflightFrame(selected.base, capabilities, budget)) {
            is RenderPlanResult.Ready -> result.plan
            is RenderPlanResult.GapNotMigrated -> return constructionGap(listOf(
                diag(W4ePlanDiagnostics.CommandNotMigrated, RenderDiagnosticDomain.SCENE, "W4d.2 construction seam declined W4e draw facts"),
            ))
            is RenderPlanResult.InvalidScene -> return invalidCandidate()
            is RenderPlanResult.GapOnPromotedScope -> return promoted("W4d.2 construction capability is unavailable")
            is RenderPlanResult.ResourceLimitExceeded -> return resource(W4ePlanDiagnostics.BudgetFrameLocalExceeded, "W4d.2 frame resources are exceeded")
        }
        val framePreview = try {
            preflightCombinedFrame(selected, basePreview)
        } catch (_: ArithmeticException) {
            return resource(W4ePlanDiagnostics.SizeOverflow, "W4e resource accounting overflowed")
        } catch (error: IllegalArgumentException) {
            return resource(W4ePlanDiagnostics.PlanIdentityInvalid, "W4e frame preflight failed: ${error.message}")
        }
        if (framePreview.peakFrameLocalBytes > budget.maxFrameLocalBytes) {
            return resource(W4ePlanDiagnostics.BudgetFrameLocalExceeded, "W4e pooled clip resources exceed the frame budget")
        }
        // Both W4d and W4e inventories are admitted above; only then is the shared seam asked
        // to issue its graph, under the caller's real budget rather than an unbounded surrogate.
        val base = when (val result = selected.constructionSeam.plan(selected.base, capabilities, budget)) {
            is RenderPlanResult.Ready -> result.plan
            is RenderPlanResult.GapNotMigrated -> return constructionGap(listOf(
                diag(W4ePlanDiagnostics.CommandNotMigrated, RenderDiagnosticDomain.SCENE, "W4d.2 construction seam declined W4e draw facts"),
            ))
            is RenderPlanResult.InvalidScene -> return invalidCandidate()
            is RenderPlanResult.GapOnPromotedScope -> return promoted("W4d.2 construction capability is unavailable")
            is RenderPlanResult.ResourceLimitExceeded -> return resource(W4ePlanDiagnostics.BudgetFrameLocalExceeded, "W4d.2 frame resources are exceeded")
        }
        return try {
            val graph = insertClips(base, selected, capabilities, budget, requiresAa, framePreview)
            if (successor && requiresAa) return promoted("W5b final blending requires the admitted single-sample W4e topology")
            // Preserve the admitted hard NoOp envelope and its logical target identity.
            val hardNoOpEnvelope = elidedNoOps && !requiresAa
            RenderPlanResult.Ready(if (successor || hardNoOpEnvelope)
                issueW5bW4ePathGraph(graph, survivingFinalBlendsByCommandI32) else graph)
        } catch (_: W4eNativePayloadLimit) {
            resource(
                W4ePlanDiagnostics.BudgetFrameLocalExceeded,
                "W4e native vertex, index, or uniform buffers exceed the sealed device limit",
            )
        } catch (_: ArithmeticException) {
            resource(W4ePlanDiagnostics.SizeOverflow, "W4e resource accounting overflowed")
        } catch (error: IllegalArgumentException) {
            resource(W4ePlanDiagnostics.PlanIdentityInvalid, "W4e graph invariants failed: ${error.message}")
        }
    }

    private fun insertClips(
        base: RenderGraph,
        selected: Candidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
        frameAa: Boolean,
        framePreview: W4eFramePreview,
    ): RenderGraph {
        val prefix = mutableListOf<PlanPass>()
        val resources = mutableListOf<PlanResource>()
        val strategyByCommand = mutableMapOf<Int, ClipPlanStrategy>()
        val extent = base.targetExtent
        val domain = RectI32(0, 0, extent.width, extent.height)
        val prefixCount = framePreview.prefixPassCount

        selected.stacks.forEachIndexed { ordinal, stack ->
            val strategy = when (stack.realization) {
                is Realization.Scissor -> ClipPlanStrategy.Scissor(requireNotNull(stack.scissor))
                Realization.Mask -> {
                    val ids = maskResources(resources, framePreview.maskLayouts.getValue(ordinal), extent)
                    val group = PlanAtomicGroupId("w4e.clip:$ordinal")
                    var accumulator = ids.firstAccumulator
                    prefix += PlanPass.ClipMaskInitialize(ordinal, accumulator, domain, 1f, group)
                    stack.emittedEntries.forEachIndexed { entryIndex, entry ->
                        val useAa = entry.antiAlias && entry.geometryF32 !is ClipGeometryF32.Rect
                        val target = if (useAa) requireNotNull(ids.multisampleScratch) else ids.scratch
                        val resolve = if (useAa) ids.scratch else null
                        val depth = when {
                            useAa -> ids.aaDepth
                            entry.geometryF32 is ClipGeometryF32.Path -> ids.hardDepth
                            else -> null
                        }
                        prefix += PlanPass.ClipMaskProducer(
                            ordinal * MAX_CLIP_ENTRIES + entryIndex,
                            target,
                            resolve,
                            depth,
                            if (useAa) 4 else 1,
                            entry.geometryF32,
                            group,
                            inverseCoverage = entry.inverseFill,
                            antiAlias = entry.antiAlias,
                        )
                        val output = if (accumulator == ids.firstAccumulator) ids.secondAccumulator else ids.firstAccumulator
                        prefix += PlanPass.ClipMaskFold(
                            ordinal * MAX_CLIP_ENTRIES + entryIndex,
                            accumulator,
                            resolve ?: target,
                            output,
                            entry.operation.toPlanOperation(),
                            domain,
                            group,
                        )
                        accumulator = output
                    }
                    ClipPlanStrategy.Mask(accumulator)
                }
            }
            stack.consumerIndexes.forEach { commandIndex ->
                strategyByCommand[commandIndex] = selected.inverseByCommand[commandIndex]?.let { inverse ->
                    when {
                        stack.isZeroCoverage -> strategy
                        strategy is ClipPlanStrategy.Mask -> ClipPlanStrategy.InverseMask(inverse, strategy.resource)
                        else -> ClipPlanStrategy.InverseDomain(inverse)
                    }
                } ?: strategy
            }
        }
        // An inverse draw without an operation stack is still a W4e-owned bounded-domain
        // consumer.  Its strategy is sealed before W4d packets are materialized.
        selected.inverseByCommand.forEach { (commandIndex, inverse) ->
            strategyByCommand.putIfAbsent(commandIndex, ClipPlanStrategy.InverseDomain(inverse))
        }

        val clippedGeneralBySource = mutableMapOf<GeneralPathDraw, ClippedGeneralPathDraw>()
        val sealedInverseDrawsByConstructionSource = mutableMapOf<GeneralPathDraw, GeneralPathDraw>()
        val clippedPasses = base.passes().map { pass ->
            // W4d.2's finite proxy is an admission-only construction detail.  Every W4e zero
            // interior restores either the exact original non-empty source or the one true Empty.
            pass.sealW4eInverseDomainZero(
                selected.inverseDomainSourcesByCommand,
                selected.actuallyEmptyInverseCommands,
                sealedInverseDrawsByConstructionSource,
            ).withClipStrategies(strategyByCommand, clippedGeneralBySource)
        }
        // `InverseDomain.Geometry` is a W4e scene operation, not a W4d.2 path-phase
        // side effect.  A direct triangle therefore still needs a declared scene D24S8 to
        // rasterize its finite interior before the domain cover.  Reuse an exact W4d scene
        // attachment when one exists; otherwise declare one here with its real lifetime.
        val inverseInteriorPaths = clippedPasses.mapIndexedNotNull { index, pass ->
            val path = pass as? PlanPass.PathRenderPass ?: return@mapIndexedNotNull null
            val inverse = path.draw.clipStrategyOrNullForW4e() as? ClipPlanStrategy.InverseDomain
                ?: return@mapIndexedNotNull null
            if (inverse.geometryF32.interiorCoverageF32 !is InverseInteriorCoverageF32.Geometry) return@mapIndexedNotNull null
            index to path
        }
        val existingSceneDepthBySamples = base.resources()
            .filter { resource -> resource.role == PlanResourceRole.DepthStencil }
            .associateBy(PlanResource::sampleCountI32)
        val newSceneDepthBySamples = linkedMapOf<Int, PlanResourceId>()
        inverseInteriorPaths.map { (_, path) -> path.draw.sample }.distinct().forEach { sample ->
            val sampleCount = if (sample == SamplePlan.Multisample4) 4 else 1
            if (existingSceneDepthBySamples[sampleCount] == null) {
                val ordinal = base.resources().filter { it.role == PlanResourceRole.DepthStencil }.maxOfOrNull(PlanResource::ordinal)
                    ?.let { value -> value + 1 + newSceneDepthBySamples.size } ?: newSceneDepthBySamples.size
                val id = planResourceId(PlanResourceRole.DepthStencil, ordinal)
                val uses = inverseInteriorPaths.filter { (_, path) ->
                    (if (path.draw.sample == SamplePlan.Multisample4) 4 else 1) == sampleCount
                }.map { (index, _) -> Math.addExact(prefixCount, index) }
                resources += PlanResource.of(
                    PlanResourceRole.DepthStencil,
                    ordinal,
                    PlanResourceKind.Texture2D,
                    PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
                    extent,
                    checkedTextureBytesI64(4, extent.width, extent.height, sampleCount),
                    setOf(PlanResourceUsage.DepthStencilAttachment),
                    PlanResourceLifetime.FrameLocal,
                    uses.min(),
                    Math.addExact(uses.max(), 1),
                    sampleCount,
                )
                newSceneDepthBySamples[sampleCount] = id
            }
        }
        val sceneDepthForSample = existingSceneDepthBySamples.mapValues { (_, resource) -> resource.id } + newSceneDepthBySamples
        val transformedPasses = clippedPasses.map { pass ->
            pass.withW4eInverseDomainSceneDepth(sceneDepthForSample)
        }
        val allPasses = prefix + transformedPasses
        // Retain exactly the scene D24S8 resources used by the source geometry.  A genuinely
        // empty inverse source has no such pass and therefore no D24S8 allocation.
        val retainedSceneDepthIds = transformedPasses
            .filterIsInstance<PlanPass.PathRenderPass>()
            .mapNotNull(PlanPass.PathRenderPass::depthStencil)
            .toSet()
        val shiftedResources = base.resources()
            .filterNot { resource ->
                resource.role == PlanResourceRole.DepthStencil && resource.id !in retainedSceneDepthIds
            }
            .map { resource -> resource.shifted(prefixCount) }
        val unsealedResources = resources + shiftedResources
        val nativePayload = W4eNativePayloadPlan.from(
            passes = allPasses,
            resources = unsealedResources,
            targetExtent = extent,
            capabilities = capabilities,
            materialPlanTable = base.materialPlanTableOrNull(),
        ) ?: throw W4eNativePayloadLimit()
        val nativePrefixFirstUseById = linkedMapOf<PlanResourceId, Int>()
        fun retainNativePrefixFirstUse(resourceId: PlanResourceId, passIndex: Int) {
            nativePrefixFirstUseById.putIfAbsent(resourceId, passIndex)
        }
        allPasses.forEachIndexed { passIndex, pass ->
            if (pass !is PlanPass.ClipMaskProducer) return@forEachIndexed
            when (pass.copyGeometryF32()) {
                is ClipGeometryF32.Path -> {
                    retainNativePrefixFirstUse(nativePayload.vertexResourceId, passIndex)
                    retainNativePrefixFirstUse(nativePayload.indexResourceId, passIndex)
                }
                else -> retainNativePrefixFirstUse(nativePayload.uniformResourceId, passIndex)
            }
        }
        val allResources = unsealedResources.map { resource ->
            resource.withW4eNativeCapacity(nativePayload, nativePrefixFirstUseById[resource.id])
                .withW4eFrameResidentLifetime()
        }
        val dependencies = allPasses.zipWithNext().map { (before, after) -> PlanPassDependency(before.id, after.id) }
        require(allPasses.size == framePreview.totalPassCount) { "W4e preflight pass count drifted" }
        val actualPeakFrameLocalBytes = peakFrameLocalBytesI64(
            allResources.map { resource ->
                FrameResourceSpan(resource.byteSize, resource.firstPassIndex, resource.lastPassIndexExclusive)
            },
            allPasses.size,
        )
        if (actualPeakFrameLocalBytes > budget.maxFrameLocalBytes) {
            throw W4eNativePayloadLimit()
        }
        val graph = RenderGraph.of(
            id = PlanId(identity(selected, capabilities, budget, frameAa)),
            capabilityId = if (frameAa) W5A_AA_CAPABILITY_ID else W5A_HARD_CAPABILITY_ID,
            targetExtent = extent,
            colorFormat = base.colorFormat,
            capabilities = capabilities,
            budget = budget,
            visualCommandCount = base.visualCommandCount,
            resources = allResources,
            passes = allPasses,
            dependencies = dependencies,
            peakFrameLocalBytes = actualPeakFrameLocalBytes,
            materialPlanTable = base.materialPlanTableOrNull(),
        )
        return RenderGraph.issueW4eCompilerWitness(graph, nativePayload)
    }

    private fun maskResources(
        resources: MutableList<PlanResource>,
        layout: MaskResourceLayout,
        extent: SizeI32,
    ): MaskResourceIds {
        val first = planResourceId(PlanResourceRole.CoverageMaskAccumulator, layout.ordinal * 2)
        val second = planResourceId(PlanResourceRole.CoverageMaskAccumulator, layout.ordinal * 2 + 1)
        val scratch = planResourceId(PlanResourceRole.CoverageMaskScratch, layout.ordinal)
        val multisample = layout.hasAaProducer.thenId(PlanResourceRole.CoverageMaskMultisampleScratch, layout.ordinal)
        val aaDepth = layout.hasAaProducer.thenId(PlanResourceRole.CoverageMaskDepthStencil, layout.ordinal * 2)
        val hardDepth = layout.hasHardPathProducer.thenId(PlanResourceRole.CoverageMaskDepthStencil, layout.ordinal * 2 + 1)
        fun texture(
            role: PlanResourceRole,
            resourceOrdinal: Int,
            format: PlanTextureFormat,
            bytes: Long,
            lastPassExclusive: Int,
            samples: Int,
        ): PlanResource {
            return PlanResource.of(
                role, resourceOrdinal, PlanResourceKind.Texture2D, format, extent, bytes,
                if (format == PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8)) {
                    setOf(PlanResourceUsage.DepthStencilAttachment)
                } else if (samples == 1) {
                    setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled)
                } else {
                    setOf(PlanResourceUsage.RenderAttachment)
                },
                PlanResourceLifetime.FrameLocal, layout.firstPassIndex, lastPassExclusive, samples,
            )
        }
        resources += texture(PlanResourceRole.CoverageMaskAccumulator, layout.ordinal * 2, PlanTextureFormat.CoverageMask,
            layout.oneSampleBytes, layout.firstAccumulatorLastPassExclusive, 1)
        resources += texture(PlanResourceRole.CoverageMaskAccumulator, layout.ordinal * 2 + 1, PlanTextureFormat.CoverageMask,
            layout.oneSampleBytes, layout.secondAccumulatorLastPassExclusive, 1)
        resources += texture(PlanResourceRole.CoverageMaskScratch, layout.ordinal, PlanTextureFormat.CoverageMask,
            layout.oneSampleBytes, layout.scratchLastPassExclusive, 1)
        multisample?.let {
            resources += texture(PlanResourceRole.CoverageMaskMultisampleScratch, layout.ordinal, PlanTextureFormat.CoverageMask,
                layout.fourSampleBytes, requireNotNull(layout.multisampleLastPassExclusive), 4)
        }
        aaDepth?.let {
            resources += texture(PlanResourceRole.CoverageMaskDepthStencil, layout.ordinal * 2,
                PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), layout.fourSampleBytes,
                requireNotNull(layout.aaDepthLastPassExclusive), 4)
        }
        hardDepth?.let {
            resources += texture(PlanResourceRole.CoverageMaskDepthStencil, layout.ordinal * 2 + 1,
                PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), layout.oneSampleBytes,
                requireNotNull(layout.hardDepthLastPassExclusive), 1)
        }
        return MaskResourceIds(first, second, scratch, multisample, aaDepth, hardDepth)
    }

    /**
     * Merges primitive W4d and W4e lifetimes before either compiler constructs graph resources.
     * Clip preparation and W4d selection are transactional CPU admission only; this is the first
     * point at which the complete physical frame can be accepted or refused.
     */
    private fun preflightCombinedFrame(
        selected: Candidate,
        base: W4dGeneralFramePreview,
    ): W4eFramePreview {
        val prefixPassCount = selected.stacks.sumOf { stack ->
            if (stack.realization === Realization.Mask) Math.addExact(1, Math.multiplyExact(stack.emittedEntries.size, 2)) else 0
        }
        val layouts = linkedMapOf<Int, MaskResourceLayout>()
        var firstPassIndex = 0
        selected.stacks.forEachIndexed { ordinal, stack ->
            if (stack.realization !== Realization.Mask) return@forEachIndexed
            val layout = preflightMaskResources(stack, ordinal, firstPassIndex, prefixPassCount, base)
            layouts[ordinal] = layout
            firstPassIndex = Math.addExact(firstPassIndex, 1 + stack.emittedEntries.size * 2)
        }
        require(firstPassIndex == prefixPassCount) { "W4e clip prefix accounting drifted" }
        val totalPassCount = Math.addExact(prefixPassCount, base.passCount)
        val spans = buildList {
            layouts.values.forEach { addAll(it.resourceSpans()) }
            base.resources.forEach { span ->
                add(FrameResourceSpan(
                    span.byteSize,
                    Math.addExact(span.firstPassIndex, prefixPassCount),
                    Math.addExact(span.lastPassIndexExclusive, prefixPassCount),
                ))
            }
        }
        return W4eFramePreview(
            prefixPassCount = prefixPassCount,
            totalPassCount = totalPassCount,
            peakFrameLocalBytes = peakFrameLocalBytesI64(spans, totalPassCount),
            maskLayouts = Collections.unmodifiableMap(layouts.toMap()),
        )
    }

    private fun preflightMaskResources(
        stack: PreparedStack,
        ordinal: Int,
        firstPassIndex: Int,
        prefixPassCount: Int,
        base: W4dGeneralFramePreview,
    ): MaskResourceLayout {
        val hasAaProducer = stack.emittedEntries.any { it.antiAlias && it.geometryF32 !is ClipGeometryF32.Rect }
        val hasHardPathProducer = stack.emittedEntries.any { !it.antiAlias && it.geometryF32 is ClipGeometryF32.Path }
        val lastUses = mutableMapOf<MaskResourceSlot, Int>()
        fun use(slot: MaskResourceSlot, passIndex: Int) {
            lastUses[slot] = maxOf(lastUses[slot] ?: -1, passIndex)
        }
        var accumulator = MaskResourceSlot.FirstAccumulator
        use(accumulator, firstPassIndex)
        var passIndex = Math.addExact(firstPassIndex, 1)
        stack.emittedEntries.forEach { entry ->
            val usesAa = entry.antiAlias && entry.geometryF32 !is ClipGeometryF32.Rect
            val target = if (usesAa) MaskResourceSlot.MultisampleScratch else MaskResourceSlot.Scratch
            val resolved = if (usesAa) MaskResourceSlot.Scratch else target
            use(target, passIndex)
            if (usesAa) use(MaskResourceSlot.AaDepthStencil, passIndex)
            if (!usesAa && entry.geometryF32 is ClipGeometryF32.Path) use(MaskResourceSlot.HardDepthStencil, passIndex)
            val output = if (accumulator == MaskResourceSlot.FirstAccumulator) {
                MaskResourceSlot.SecondAccumulator
            } else {
                MaskResourceSlot.FirstAccumulator
            }
            val foldPass = Math.addExact(passIndex, 1)
            use(accumulator, foldPass)
            use(resolved, foldPass)
            use(output, foldPass)
            accumulator = output
            passIndex = Math.addExact(passIndex, 2)
        }
        stack.consumerIndexes.forEach { commandIndex ->
            val baseConsumerPass = requireNotNull(base.colorConsumerPassByCommand[commandIndex]) {
                "W4d.2 preflight omitted a clipped consumer"
            }
            use(accumulator, Math.addExact(prefixPassCount, baseConsumerPass))
        }
        val oneSampleBytes = ClipPlanBudget.checkedMaskTextureBytesI64(
            base.extent.width,
            base.extent.height,
            1,
        )
        val fourSampleBytes = ClipPlanBudget.checkedMaskTextureBytesI64(
            base.extent.width, base.extent.height, 4,
        )
        fun last(slot: MaskResourceSlot): Int = Math.addExact(requireNotNull(lastUses[slot]) { "W4e mask slot was never consumed" }, 1)
        return MaskResourceLayout(
            ordinal = ordinal,
            firstPassIndex = firstPassIndex,
            oneSampleBytes = oneSampleBytes,
            fourSampleBytes = fourSampleBytes,
            firstAccumulatorLastPassExclusive = last(MaskResourceSlot.FirstAccumulator),
            secondAccumulatorLastPassExclusive = last(MaskResourceSlot.SecondAccumulator),
            scratchLastPassExclusive = last(MaskResourceSlot.Scratch),
            multisampleLastPassExclusive = if (hasAaProducer) last(MaskResourceSlot.MultisampleScratch) else null,
            aaDepthLastPassExclusive = if (hasAaProducer) last(MaskResourceSlot.AaDepthStencil) else null,
            hardDepthLastPassExclusive = if (hasHardPathProducer) last(MaskResourceSlot.HardDepthStencil) else null,
            hasAaProducer = hasAaProducer,
            hasHardPathProducer = hasHardPathProducer,
        )
    }

    private fun capabilityRefusal(
        capabilities: PlanCapabilitySnapshot,
        stacks: List<PreparedStack>,
    ): RenderPlanResult.GapOnPromotedScope? {
        if (stacks.isEmpty()) return null
        val one = setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled)
        if (!capabilities.supportsTexture(PlanTextureFormat.CoverageMask, 1, one)) {
            return promoted(W4ePlanDiagnostics.MaskFormatUnavailable, "W4e requires a sampled linear RGBA8 mask")
        }
        val anyAa = stacks.any { stack -> stack.emittedEntries.any { it.antiAlias && it.geometryF32 !is ClipGeometryF32.Rect } }
        if (anyAa && (!capabilities.supportsTexture(PlanTextureFormat.CoverageMask, 4, setOf(PlanResourceUsage.RenderAttachment)) ||
                !capabilities.supportsResolve(PlanTextureFormat.CoverageMask, 4, 1) ||
                !capabilities.supportsTexture(PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), 4, setOf(PlanResourceUsage.DepthStencilAttachment)))) {
            return promoted(W4ePlanDiagnostics.SampleCountUnavailable, "W4e AA clip producer support is unavailable")
        }
        val hardPath = stacks.any { stack -> stack.emittedEntries.any { !it.antiAlias && it.geometryF32 is ClipGeometryF32.Path } }
        return if (hardPath && !capabilities.supportsTexture(
                PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), 1,
                setOf(PlanResourceUsage.DepthStencilAttachment),
            )) promoted(W4ePlanDiagnostics.SampleCountUnavailable, "W4e hard path clip depth-stencil support is unavailable") else null
    }

    private fun prepare(
        operations: ClipStackNode.Operations,
        domain: RectI32,
        frameBefore: ClipWorkUsageI64,
        forceMask: Boolean,
    ): PreparedResult {
        if (operations.entryCount > maxClipEntriesPerStackI32()) {
            return PreparedResult.Limit("W4e accepts at most ${maxClipEntriesPerStackI32()} clip entries per stack")
        }
        val inputs = buildList {
            operations.forEach { entry ->
                val input = entry.toTransformInputOrNull() ?: return PreparedResult.Invalid("LegacyUnavailable clip transform")
                add(input)
            }
        }
        return when (val result = prepareTransformedClipStackGeometryF32(inputs, domain, clipPolicyF64, frameWorkUsageBeforeI64 = frameBefore)) {
            is ClipStackPreparationResult.Ready -> {
                val selection = selectRealization(result.entriesF32, domain)
                PreparedResult.Ready(
                    PreparedStack(
                        entries = result.entriesF32,
                        emittedEntries = selection.emittedEntries,
                        frameUsageAfterI64 = result.frameWorkUsageAfterI64,
                        realization = if (forceMask && selection.realization is Realization.Scissor &&
                            selection.emittedEntries.isNotEmpty()
                        ) Realization.Mask else selection.realization,
                    ),
                )
            }
            is ClipStackPreparationResult.InvalidScene -> PreparedResult.Invalid("Clip math rejected non-finite geometry")
            is ClipStackPreparationResult.ResourceLimitExceeded -> PreparedResult.Limit("Clip math limit: ${result.reason}")
        }
    }

    /**
     * Removes only algebraic identity entries.  A finite empty intersects to zero while an
     * inverse empty subtracts the complete domain; both are terminal zero coverage states.
     */
    private fun selectRealization(
        entries: List<ClipPreparedEntryF32>,
        domain: RectI32,
    ): RealizationSelection {
        val emitted = mutableListOf<ClipPreparedEntryF32>()
        entries.forEach { entry ->
            if (entry.geometryF32 != ClipGeometryF32.Empty) {
                emitted += entry
            } else {
                val emptyCoverageIsFull = entry.inverseFill
                val zeroesAccumulator =
                    (entry.operation == MathClipOperation.Intersect && !emptyCoverageIsFull) ||
                        (entry.operation == MathClipOperation.Difference && emptyCoverageIsFull)
                if (zeroesAccumulator) return RealizationSelection(emptyList(), Realization.Scissor(RectI32.Empty))
            }
        }
        if (emitted.isEmpty()) return RealizationSelection(emptyList(), Realization.Scissor(domain))
        val single = emitted.singleOrNull()
        val scissor = single?.takeIf {
            it.operation == MathClipOperation.Intersect && !it.antiAlias && !it.inverseFill &&
                it.geometryF32 is ClipGeometryF32.Rect && !it.copyConservativeScissorI32().isEmpty
        }?.copyConservativeScissorI32()
        return if (scissor != null) {
            RealizationSelection(emitted, Realization.Scissor(scissor))
        } else {
            RealizationSelection(emitted, Realization.Mask)
        }
    }

    private fun reuseKey(operations: ClipStackNode.Operations, target: RenderTargetDescriptor, coverage: org.graphiks.kanvas.render.ir.CoverageRequest): ClipStackReuseKey? {
        val transforms = operations.map { entry -> entry.transform.canonicalId.value }
        if (operations.any { it.transform is ClipTransformSnapshot.LegacyUnavailable }) return null
        return ClipStackReuseKey(
            operations.canonicalId.value,
            SizeI32(target.extent.width, target.extent.height),
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL,
            if (coverage == org.graphiks.kanvas.render.ir.CoverageRequest.ANTIALIASED) SamplePlan.Multisample4 else SamplePlan.SingleSample,
            transforms,
        )
    }

    private fun ClipEntry.toTransformInputOrNull(): ClipTransformInputF64? {
        val transform = (transform as? ClipTransformSnapshot.Known)?.copyMatrixF32()?.toMatrix3x3F64() ?: return null
        val geometry = when (val source = geometry) {
            is GeometryNode.Rect -> ClipTransformGeometryF64.Rect(source.copyBounds().toRectF64())
            is GeometryNode.RRect -> source.copyShape().toRRectF64().let(ClipTransformGeometryF64::RRect)
            is GeometryNode.Path -> ClipTransformGeometryF64.Path(source.path)
            else -> return null
        }
        return ClipTransformInputF64.of(geometry, transform, operation.toMathOperation(), antiAlias)
    }

    /** W4d.2 needs a finite admission shape for an inverse source with no finite interior. */
    private fun DrawNode.normalizedForW4dConstructionSeam(
        domain: RectI32,
        inverse: InversePathGeometryF32?,
    ): DrawNode {
        if (inverse?.interiorCoverageF32 == InverseInteriorCoverageF32.Zero) {
            return copy(
                geometry = GeometryNode.Path(constructionProxyPath(domain)),
                transform = Matrix3x3F32.Identity,
                clip = w4dConstructionClip(),
            )
        }
        val sourcePath = when (val source = geometry) {
            is GeometryNode.Rect -> PathBuilder().addRect(source.copyBounds()).build()
            is GeometryNode.RRect -> PathBuilder().addRRect(source.copyShape()).build()
            is GeometryNode.Path -> source.path
            else -> return copy(clip = w4dConstructionClip())
        }
        val rule = when (sourcePath.fillRule) {
            FillRule.INVERSE_WINDING -> FillRule.WINDING
            FillRule.INVERSE_EVEN_ODD -> FillRule.EVEN_ODD
            else -> sourcePath.fillRule
        }
        val normalizedPath = if (rule == sourcePath.fillRule) sourcePath else PathBuilder(rule).addPath(sourcePath).build()
        return copy(
            geometry = GeometryNode.Path(normalizedPath),
            origin = DrawOrigin.PATH,
            clip = w4dConstructionClip(),
        )
    }

    /** Keeps only the W4d construction seam's native scissor clip on non-W4e-owned draws. */
    private fun DrawNode.w4dConstructionClip(): ClipStackNode =
        (clip as? ClipStackNode.DeviceRect) ?: ClipStackNode.Empty

    /** Returns the finite target-domain intersection used by both W4d scissoring and W4e inverse coverage. */
    private fun DrawNode.w4dDeviceRectDomainOrNull(domain: RectI32): RectI32? {
        val deviceRect = clip as? ClipStackNode.DeviceRect ?: return null
        val bounds = deviceRect.copyBounds()
        val integral = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
            .map { value ->
                value.toLong().takeIf { long ->
                    long in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() && long.toFloat() == value
                }
            }
            .takeIf { values -> values.none { it == null } }
            ?.let { values ->
                RectI32(values[0]!!.toInt(), values[1]!!.toInt(), values[2]!!.toInt(), values[3]!!.toInt())
                    .takeUnless(RectI32::isEmpty64)
            }
            ?: return null
        return integral.copy().takeIf { it.intersect(domain) }
    }

    private fun constructionProxyPath(domain: RectI32) : org.graphiks.math.geometry.PathF32 = PathBuilder()
        .moveTo(domain.left.toFloat(), domain.top.toFloat())
        .lineTo(domain.right.toFloat(), domain.top.toFloat())
        .lineTo(domain.left.toFloat(), domain.bottom.toFloat())
        .close()
        .build()

    private fun DrawNode.hasInversePathFillRule(): Boolean =
        (geometry as? GeometryNode.Path)?.path?.fillRule in setOf(FillRule.INVERSE_WINDING, FillRule.INVERSE_EVEN_ODD)

    /** Public Paint capture preserves its explicit SRC_OVER provenance as [BlendNode.Paint]. */
    private fun org.graphiks.kanvas.render.ir.BlendNode.isSrcOverWithoutCustomBlender(): Boolean = when (this) {
        org.graphiks.kanvas.render.ir.BlendNode.SrcOver -> true
        is org.graphiks.kanvas.render.ir.BlendNode.Mode -> mode == org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER
        is org.graphiks.kanvas.render.ir.BlendNode.Paint -> mode == org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER && blender == null
        is org.graphiks.kanvas.render.ir.BlendNode.Custom -> false
    }

    private fun DrawNode.prepareInversePathOrNull(domain: RectI32): InverseResult {
        val path = (geometry as? GeometryNode.Path)?.path ?: return InverseResult.None
        if (path.fillRule !in setOf(FillRule.INVERSE_WINDING, FillRule.INVERSE_EVEN_ODD)) return InverseResult.None
        val paint = paint ?: return InverseResult.Invalid("W4e inverse path requires paint")
        val (styleF64, mode) = when (paint.style) {
            PaintStyleNode.FILL -> null to InversePathDrawMode.Fill
            PaintStyleNode.STROKE_AND_FILL -> try {
                w4PathStrokeStyleF64(paint, PathStrokeDrawMode.StrokeAndFill) to InversePathDrawMode.StrokeAndFill
            } catch (_: IllegalArgumentException) {
                return InverseResult.Invalid("Inverse stroke style is invalid")
            }
            PaintStyleNode.STROKE -> return InverseResult.Invalid("W4e does not define inverse stroke-only coverage")
        }
        return when (val prepared = transform.toMatrix3x3F64().prepareTransformedInversePathGeometryF32(
            path,
            styleF64 = styleF64,
            mode = mode,
            domainI32 = domain,
            policyF64 = PathStrokePolicyF64(),
        )) {
            is InversePathPreparationResult.Ready -> InverseResult.Ready(prepared.geometryF32)
            is InversePathPreparationResult.InvalidScene -> InverseResult.Invalid("Inverse path geometry is invalid")
            is InversePathPreparationResult.ResourceLimitExceeded -> InverseResult.Limit("Inverse path geometry exceeds W4e limits")
        }
    }

    private fun ClipOperation.toMathOperation(): MathClipOperation = when (this) {
        ClipOperation.INTERSECT -> MathClipOperation.Intersect
        ClipOperation.DIFFERENCE -> MathClipOperation.Difference
    }

    private fun MathClipOperation.toPlanOperation(): ClipCombineOperation = when (this) {
        MathClipOperation.Intersect -> ClipCombineOperation.Intersect
        MathClipOperation.Difference -> ClipCombineOperation.Difference
    }

    private fun maxClipEntriesPerStackI32(): Int = minOf(
        MAX_CLIP_ENTRIES,
        clipPolicyF64.limitsI32.maxClipEntryCountPerStackI32,
    )

    private fun org.graphiks.math.geometry.RectF32.toRectF64(): RectF64 = RectF64(left.toDouble(), top.toDouble(), right.toDouble(), bottom.toDouble())
    private fun org.graphiks.math.geometry.RRectF32.toRRectF64(): RRectF64 = RRectF64.of(
        rect.toRectF64(),
        CornerRadiiF64.of(topLeft.x.toDouble(), topLeft.y.toDouble()),
        CornerRadiiF64.of(topRight.x.toDouble(), topRight.y.toDouble()),
        CornerRadiiF64.of(bottomRight.x.toDouble(), bottomRight.y.toDouble()),
        CornerRadiiF64.of(bottomLeft.x.toDouble(), bottomLeft.y.toDouble()),
    )

    private fun PlanResource.shifted(offset: Int): PlanResource = PlanResource.of(
        role, ordinal, kind, format, copyExtent(), byteSize, usages(), lifetime,
        Math.addExact(firstPassIndex, offset), Math.addExact(lastPassIndexExclusive, offset), sampleCountI32,
    )

    /**
     * W4e takes ownership of the W4d construction seam's one shared V/I/U resource triple.
     * The IDs and lifetimes remain graph-visible.  Prefix clip producers are native consumers
     * too, so their first binding extends the W4d lifetime before the checked capacity is
     * replaced by the complete W4e payload (clip producers, inverse interiors, and color paths).
     */
    private fun PlanResource.withW4eNativeCapacity(
        payload: W4eNativePayloadPlan,
        prefixFirstUse: Int?,
    ): PlanResource {
        val capacity = when (id) {
            payload.vertexResourceId -> payload.vertexCapacityBytes
            payload.indexResourceId -> payload.indexCapacityBytes
            payload.uniformResourceId -> payload.uniformCapacityBytes
            else -> return this
        }
        return PlanResource.of(
            role,
            ordinal,
            kind,
            format,
            copyExtent(),
            capacity,
            usages(),
            lifetime,
            minOf(firstPassIndex, prefixFirstUse ?: firstPassIndex),
            lastPassIndexExclusive,
            sampleCountI32,
        )
    }

    /** The canonical target is frame-resident while W4e emits its clip prefix. */
    private fun PlanResource.withW4eFrameResidentLifetime(): PlanResource {
        if (role != PlanResourceRole.LogicalTarget || firstPassIndex == 0) return this
        return PlanResource.of(
            role,
            ordinal,
            kind,
            format,
            copyExtent(),
            byteSize,
            usages(),
            lifetime,
            0,
            lastPassIndexExclusive,
            sampleCountI32,
        )
    }

    private fun PlanPass.withClipStrategies(
        strategies: Map<Int, ClipPlanStrategy>,
        clippedGeneralBySource: MutableMap<GeneralPathDraw, ClippedGeneralPathDraw>,
    ): PlanPass = when (this) {
        is PlanPass.PathRenderPass -> {
            if (phase !in setOf(
                    PathRenderPhase.SingleSampleDirectColor,
                    PathRenderPhase.SingleSampleStencilProducer,
                    PathRenderPhase.SingleSampleStencilColorCover,
                    PathRenderPhase.MultisampleDirectColor,
                    PathRenderPhase.MultisampleStencilProducer,
                    PathRenderPhase.MultisampleStencilColorCover,
                    PathRenderPhase.HardEdgeBinaryColorCover,
                )) return this
            val strategy = strategies[draw.commandIndex] ?: return this
            val clipped = when (draw) {
                is GeneralPathDraw -> clippedGeneralBySource.getOrPut(draw) {
                    ClippedGeneralPathDraw.of(draw, strategy)
                }
                is BinaryMaskedPathDraw -> ClippedBinaryMaskedPathDraw.of(draw, strategy)
                is ClippedGeneralPathDraw, is ClippedBinaryMaskedPathDraw -> draw
            }
            PlanPass.PathRenderPass(ordinal, target, clipped, phase, drawDataResources, atomicGroup, depthStencil, load, store, depthStencilAccess, depthStencilLoadStore, resolveTarget)
        }
        else -> this
    }

    private fun PathRenderDraw.clipStrategyOrNullForW4e(): ClipPlanStrategy? = when (this) {
        is ClippedGeneralPathDraw -> clip
        is ClippedBinaryMaskedPathDraw -> clip
        is GeneralPathDraw,
        is BinaryMaskedPathDraw,
        -> null
    }

    /** Binds only a declared inverse-domain interior to its exact scene D24S8 inventory. */
    private fun PlanPass.withW4eInverseDomainSceneDepth(
        sceneDepthForSample: Map<Int, PlanResourceId>,
    ): PlanPass = when (this) {
        is PlanPass.PathRenderPass -> {
            val inverse = draw.clipStrategyOrNullForW4e() as? ClipPlanStrategy.InverseDomain
                ?: return this
            if (inverse.geometryF32.interiorCoverageF32 !is InverseInteriorCoverageF32.Geometry ||
                depthStencil != null
            ) return this
            val sampleCount = if (draw.sample == SamplePlan.Multisample4) 4 else 1
            val depth = requireNotNull(sceneDepthForSample[sampleCount]) {
                "W4e inverse-domain interior is missing its declared scene D24S8 attachment"
            }
            PlanPass.PathRenderPass(
                ordinal,
                target,
                draw,
                phase,
                drawDataResources,
                atomicGroup,
                depth,
                load,
                store,
                depthStencilAccess,
                depthStencilLoadStore,
                resolveTarget,
            )
        }
        else -> this
    }

    /** Replaces W4d.2's admission proxy with the exact sealed W4e inverse-domain source. */
    private fun PlanPass.sealW4eInverseDomainZero(
        inverseDomainSourcesByCommand: Map<Int, PathDrawGeometry.InverseDomainSource>,
        actuallyEmptyInverseCommands: Set<Int>,
        sealedDrawsByConstructionSource: MutableMap<GeneralPathDraw, GeneralPathDraw>,
    ): PlanPass = when (this) {
        is PlanPass.PathRenderPass -> {
            val sourceGeometry = inverseDomainSourcesByCommand[draw.commandIndex]
            val actuallyEmpty = draw.commandIndex in actuallyEmptyInverseCommands
            if (sourceGeometry == null && !actuallyEmpty) return this
            val sealedDraw = when (val source = draw) {
                is GeneralPathDraw -> sealedDrawsByConstructionSource.getOrPut(source) {
                    if (sourceGeometry == null) {
                        GeneralPathDraw.w4eActuallyEmptyInverseDomainOf(source)
                    } else {
                        GeneralPathDraw.w4eInverseDomainSourceOf(source, sourceGeometry)
                    }
                }
                is BinaryMaskedPathDraw -> BinaryMaskedPathDraw.of(
                    sealedDrawsByConstructionSource.getOrPut(source.producer) {
                        if (sourceGeometry == null) {
                            GeneralPathDraw.w4eActuallyEmptyInverseDomainOf(source.producer)
                        } else {
                            GeneralPathDraw.w4eInverseDomainSourceOf(source.producer, sourceGeometry)
                        }
                    },
                    source.mask,
                )
                is ClippedGeneralPathDraw,
                is ClippedBinaryMaskedPathDraw,
                -> error("W4e construction seam must not return a pre-clipped draw.")
            }
            PlanPass.PathRenderPass(
                ordinal,
                target,
                sealedDraw,
                phase,
                drawDataResources,
                atomicGroup,
                null,
                load,
                store,
                null,
                null,
                resolveTarget,
            )
        }
        else -> this
    }

    private fun identity(selected: Candidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget, aa: Boolean): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val fields = listOf(
            "w4e-clip-plan-v2-material-v1", selected.sceneCanonicalId.value, selected.target.canonicalId.value,
            if (aa) W5A_AA_CAPABILITY_ID else W5A_HARD_CAPABILITY_ID, budget.maxFrameLocalBytes.toString(),
        ) + selected.stacks.map { it.identity } + planCapabilityIdentityFacts(capabilities)
        fields.forEach { field ->
            val bytes = field.encodeToByteArray()
            digest.update(bytes.size.toString().encodeToByteArray()); digest.update(0); digest.update(bytes); digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun gap(message: String): GpuPlanSelection.NotCandidate = GpuPlanSelection.NotCandidate(listOf(diag(W4ePlanDiagnostics.CommandNotMigrated, RenderDiagnosticDomain.SCENE, message)))
    private fun legacy(message: String): GpuPlanSelection.NotCandidate = GpuPlanSelection.NotCandidate(listOf(diag(W4ePlanDiagnostics.LegacyUnavailable, RenderDiagnosticDomain.SCENE, message)))
    private fun invalid(message: String): GpuPlanSelection.InvalidScene = GpuPlanSelection.InvalidScene(listOf(diag(W4ePlanDiagnostics.SceneInvalid, RenderDiagnosticDomain.SCENE, message)))
    private fun limit(message: String): GpuPlanSelection.ResourceLimitExceeded = GpuPlanSelection.ResourceLimitExceeded(listOf(diag(W4ePlanDiagnostics.GeometryLimit, RenderDiagnosticDomain.RESOURCE, message)))
    private fun resource(code: org.graphiks.kanvas.render.ir.RenderDiagnosticCode, message: String): RenderPlanResult.ResourceLimitExceeded = RenderPlanResult.ResourceLimitExceeded(listOf(diag(code, RenderDiagnosticDomain.RESOURCE, message)))
    private fun promoted(message: String): RenderPlanResult.GapOnPromotedScope = promoted(W4ePlanDiagnostics.CapabilityUnavailable, message)
    private fun promoted(code: org.graphiks.kanvas.render.ir.RenderDiagnosticCode, message: String): RenderPlanResult.GapOnPromotedScope = RenderPlanResult.GapOnPromotedScope(listOf(diag(code, RenderDiagnosticDomain.CAPABILITY, message)))
    private fun invalidCandidate(): RenderPlanResult.InvalidScene = RenderPlanResult.InvalidScene(listOf(diag(W4ePlanDiagnostics.SceneInvalid, RenderDiagnosticDomain.SCENE, "Candidate does not belong to W4e")))
    private fun diag(code: org.graphiks.kanvas.render.ir.RenderDiagnosticCode, domain: RenderDiagnosticDomain, message: String): RenderDiagnostic = W4ePlanDiagnostics.diagnostic(code, domain, message)

    private sealed interface PreparedResult {
        data class Ready(val stack: PreparedStack) : PreparedResult
        data class Invalid(val message: String) : PreparedResult
        data class Limit(val message: String) : PreparedResult
    }

    private class W4eNativePayloadLimit : IllegalArgumentException()
    private sealed interface InverseResult {
        data object None : InverseResult
        data class Ready(val geometry: InversePathGeometryF32) : InverseResult
        data class Invalid(val message: String) : InverseResult
        data class Limit(val message: String) : InverseResult
    }
    private sealed interface Realization {
        class Scissor(domain: RectI32) : Realization {
            private val domainSnapshot: RectI32 = domain.copy()
            fun copyDomain(): RectI32 = domainSnapshot.copy()
        }
        data object Mask : Realization
    }
    private data class RealizationSelection(
        val emittedEntries: List<ClipPreparedEntryF32>,
        val realization: Realization,
    )
    private data class ClipStackReuseKey(val canonicalStackId: String, val extentI32: SizeI32, val format: PlanLogicalColorFormat, val samplePlan: SamplePlan, val transformIds: List<String>)
    private class PreparedStack(
        val entries: List<ClipPreparedEntryF32>,
        val emittedEntries: List<ClipPreparedEntryF32>,
        val frameUsageAfterI64: ClipWorkUsageI64,
        realization: Realization,
    ) {
        var realization: Realization = realization
            private set
        val consumerIndexes: MutableList<Int> = mutableListOf()
        val scissor: RectI32? get() = (realization as? Realization.Scissor)?.copyDomain()
        val isZeroCoverage: Boolean get() = scissor?.isEmpty == true
        val requiresAaFrame: Boolean get() = realization === Realization.Mask &&
            emittedEntries.any { it.antiAlias && it.geometryF32 !is ClipGeometryF32.Rect }
        val identity: String = entries.joinToString("/") { "${it.operation}:${it.antiAlias}:${it.inverseFill}:${it.copyConservativeScissorI32()}" }
        fun forceMaskForInverse() {
            if (realization is Realization.Scissor && !isZeroCoverage && emittedEntries.isNotEmpty()) {
                realization = Realization.Mask
            }
        }
    }
    private enum class MaskResourceSlot {
        FirstAccumulator,
        SecondAccumulator,
        Scratch,
        MultisampleScratch,
        AaDepthStencil,
        HardDepthStencil,
    }
    private data class MaskResourceLayout(
        val ordinal: Int,
        val firstPassIndex: Int,
        val oneSampleBytes: Long,
        val fourSampleBytes: Long,
        val firstAccumulatorLastPassExclusive: Int,
        val secondAccumulatorLastPassExclusive: Int,
        val scratchLastPassExclusive: Int,
        val multisampleLastPassExclusive: Int?,
        val aaDepthLastPassExclusive: Int?,
        val hardDepthLastPassExclusive: Int?,
        val hasAaProducer: Boolean,
        val hasHardPathProducer: Boolean,
    ) {
        fun resourceSpans(): List<FrameResourceSpan> = buildList {
            add(FrameResourceSpan(oneSampleBytes, firstPassIndex, firstAccumulatorLastPassExclusive))
            add(FrameResourceSpan(oneSampleBytes, firstPassIndex, secondAccumulatorLastPassExclusive))
            add(FrameResourceSpan(oneSampleBytes, firstPassIndex, scratchLastPassExclusive))
            multisampleLastPassExclusive?.let { add(FrameResourceSpan(fourSampleBytes, firstPassIndex, it)) }
            aaDepthLastPassExclusive?.let { add(FrameResourceSpan(fourSampleBytes, firstPassIndex, it)) }
            hardDepthLastPassExclusive?.let { add(FrameResourceSpan(oneSampleBytes, firstPassIndex, it)) }
        }
    }
    private data class W4eFramePreview(
        val prefixPassCount: Int,
        val totalPassCount: Int,
        val peakFrameLocalBytes: Long,
        val maskLayouts: Map<Int, MaskResourceLayout>,
    )
    private data class MaskResourceIds(val firstAccumulator: PlanResourceId, val secondAccumulator: PlanResourceId, val scratch: PlanResourceId, val multisampleScratch: PlanResourceId?, val aaDepth: PlanResourceId?, val hardDepth: PlanResourceId?)
    private class NoOpCandidate(
        val owner: W4eClipPlanCompiler,
        override val sceneCanonicalId: org.graphiks.kanvas.render.ir.CanonicalId,
        override val target: RenderTargetDescriptor,
    ) : GpuPlanCandidate { override val capabilityId: String = W5B_HARD_CAPABILITY_ID }

    private class Candidate(
        val owner: W4eClipPlanCompiler,
        override val sceneCanonicalId: org.graphiks.kanvas.render.ir.CanonicalId,
        override val target: RenderTargetDescriptor,
        val constructionSeam: W4dGeneralPathPlanCompiler,
        val base: GpuPlanCandidate,
        stacks: List<PreparedStack>,
        inverseByCommand: Map<Int, InversePathGeometryF32>,
        inverseDomainSourcesByCommand: Map<Int, PathDrawGeometry.InverseDomainSource>,
        actuallyEmptyInverseCommands: Set<Int>,
        finalBlendsByCommandI32: Map<Int, BlendPlan>,
    ) : GpuPlanCandidate {
        val finalBlendsByCommandI32 = Collections.unmodifiableMap(finalBlendsByCommandI32.toMap())
        override val capabilityId: String = if (base.capabilityId == W4dGeneralPathPlanCompiler.W5A_AA_CAPABILITY_ID) W5A_AA_CAPABILITY_ID else W5A_HARD_CAPABILITY_ID
        val stacks: List<PreparedStack> = Collections.unmodifiableList(stacks)
        val inverseByCommand: Map<Int, InversePathGeometryF32> = Collections.unmodifiableMap(inverseByCommand.toMap())
        val inverseDomainSourcesByCommand: Map<Int, PathDrawGeometry.InverseDomainSource> =
            Collections.unmodifiableMap(inverseDomainSourcesByCommand.toMap())
        val actuallyEmptyInverseCommands: Set<Int> = Collections.unmodifiableSet(actuallyEmptyInverseCommands.toSet())
        private val sceneFingerprint = sceneCanonicalId
        private val targetFingerprint = target.canonicalId
        fun matches(): Boolean = sceneCanonicalId == sceneFingerprint && target.canonicalId == targetFingerprint && isW5aMaterialCapabilityId(capabilityId)
    }

    private fun Boolean.thenId(role: PlanResourceRole, ordinal: Int): PlanResourceId? =
        if (this) planResourceId(role, ordinal) else null

    public companion object {
        public const val W5B_HARD_CAPABILITY_ID: String = "w5b-w4e-path-hard-final-blend-v3"
        public const val HARD_CAPABILITY_ID: String = "solid-path-complex-clip-hard-1x-src-over-srgb-v1"
        public const val AA_CAPABILITY_ID: String = "solid-path-complex-clip-mixed-aa4-src-over-srgb-v1"
        /** W5a material-bearing successor to the historical [HARD_CAPABILITY_ID] contract. */
        public const val W5A_HARD_CAPABILITY_ID: String = "solid-path-complex-clip-hard-1x-src-over-srgb-w5a-material-v2"
        /** W5a material-bearing successor to the historical [AA_CAPABILITY_ID] contract. */
        public const val W5A_AA_CAPABILITY_ID: String = "solid-path-complex-clip-mixed-aa4-src-over-srgb-w5a-material-v2"

        public fun isLegacyCapabilityId(capabilityId: String): Boolean =
            capabilityId == HARD_CAPABILITY_ID || capabilityId == AA_CAPABILITY_ID

        public fun isW5aMaterialCapabilityId(capabilityId: String): Boolean =
            capabilityId == W5A_HARD_CAPABILITY_ID || capabilityId == W5A_AA_CAPABILITY_ID
        private const val MAX_DRAWS: Int = 512
        private const val MAX_CLIP_ENTRIES: Int = 512
    }
}

/** Immutable, material-free W4e producer authority consumed by the W5b Point graph. */
public class W4eClipOnlyPlan internal constructor(
    public val operations: ClipStackNode.Operations,
    extent: SizeI32,
    public val capabilities: PlanCapabilitySnapshot,
    public val budget: PlanBudget,
    passes: List<PlanPass>,
    resources: List<PlanResource>,
    public val maskResource: PlanResourceId,
    public val nativePayload: W4eNativePayloadPlan,
) {
    private val extentSnapshot = extent.copy()
    private val passSnapshot = Collections.unmodifiableList(passes.toList())
    private val resourceSnapshot = Collections.unmodifiableList(resources.toList())
    public fun copyExtentI32(): SizeI32 = extentSnapshot.copy()
    public fun passes(): List<PlanPass> = passSnapshot
    public fun resources(): List<PlanResource> = resourceSnapshot
}
