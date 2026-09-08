package org.graphiks.kanvas.gpu.plan

import java.security.MessageDigest
import java.util.Collections
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.ClipEntry
import org.graphiks.kanvas.render.ir.ClipOperation
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.ClipTransformSnapshot
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.GeometryNode
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
import org.graphiks.math.geometry.InversePathPreparationResult
import org.graphiks.math.geometry.PathBuilder
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
public class W4eClipPlanCompiler internal constructor(
    private val clipPolicyF64: ClipPreparationPolicyF64,
) : GpuPlanCompiler {
    public constructor() : this(ClipPreparationPolicyF64())

    private val w4dSeam = W4dGeneralPathPlanCompiler(
        strokePolicyF64 = org.graphiks.math.geometry.PathStrokePolicyF64(),
        acceptsNarrowTransforms = true,
    )

    override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection {
        if (scene.extent != target.extent || scene.colorSpace != target.colorSpace) return invalid("Scene and target differ")
        if (SceneSemanticValidator.validate(scene) is SceneSemanticValidationResult.Invalid) return invalid("Scene validation failed")
        if (scene.colorSpace != ColorSpace.SRGB) return gap("W4e supports only sRGB")

        val domain = RectI32(0, 0, scene.extent.width, scene.extent.height)
        val normalized = mutableListOf<SceneCommand>()
        val preparedByKey = linkedMapOf<ClipStackReuseKey, PreparedStack>()
        val inverseByCommand = mutableMapOf<Int, InversePathGeometryF32>()
        var frameUsage = ClipWorkUsageI64()
        var visualDrawCount = 0
        var ownsComplexClip = false

        scene.withIndex().forEach { (index, command) ->
            when (command) {
                is SceneCommand.Draw -> {
                    val operations = command.node.clip as? ClipStackNode.Operations
                    if (operations == null) {
                        normalized += command
                        return@forEach
                    }
                    if (!command.node.isW4eFillScope()) return gap("Complex clip draw is outside W4e fill scope")
                    visualDrawCount = Math.addExact(visualDrawCount, 1)
                    if (visualDrawCount > MAX_DRAWS) return limit("W4e accepts at most 512 visual path draws")
                    ownsComplexClip = true
                    val inverse = when (val preparedInverse = command.node.prepareInversePathOrNull(domain)) {
                        is InverseResult.None -> null
                        is InverseResult.Ready -> preparedInverse.geometry
                        is InverseResult.Invalid -> return invalid(preparedInverse.message)
                        is InverseResult.Limit -> return limit(preparedInverse.message)
                    }
                    val key = reuseKey(operations, target, command.node.coverage)
                        ?: return legacy("LegacyUnavailable clip transforms cannot be promoted")
                    val prepared = preparedByKey[key] ?: when (val value = prepare(operations, domain, frameUsage, inverse != null)) {
                        is PreparedResult.Ready -> {
                            frameUsage = value.stack.frameUsageAfterI64
                            value.stack.also { preparedByKey[key] = it }
                        }
                        is PreparedResult.Invalid -> return invalid(value.message)
                        is PreparedResult.Limit -> return limit(value.message)
                    }
                    if (inverse != null) {
                        prepared.forceMask()
                        inverseByCommand[index] = inverse
                    }
                    normalized += SceneCommand.Draw(command.node.normalizedForW4d())
                    // The candidate keeps the prepared stack map keyed by this exact canonical stack identity.
                    prepared.consumerIndexes += index
                }
                else -> normalized += command
            }
        }
        if (!ownsComplexClip) return gap("W4e requires an explicitly captured complex clip")

        val normalizedScene = SceneSnapshot.of(scene.extent, scene.colorSpace, normalized)
        val base = when (val selected = w4dSeam.select(normalizedScene, target)) {
            is GpuPlanSelection.Candidate -> selected.candidate
            is GpuPlanSelection.NotCandidate -> return gap("W4e draw scope is outside the W4d.2 construction seam")
            is GpuPlanSelection.InvalidScene -> return invalid("W4d.2 rejected normalized W4e draw facts")
            is GpuPlanSelection.ResourceLimitExceeded -> return limit("W4d.2 rejected normalized W4e draw resources")
        }
        return GpuPlanSelection.Candidate(Candidate(
            this, scene.canonicalId, target, base, preparedByKey.values.toList(), inverseByCommand,
        ))
    }

    override fun plan(candidate: GpuPlanCandidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget): RenderPlanResult<RenderGraph> {
        val selected = candidate as? Candidate ?: return invalidCandidate()
        if (selected.owner !== this || !selected.matches()) return invalidCandidate()
        val requiresAa = selected.base.capabilityId == W4dGeneralPathPlanCompiler.AA_CAPABILITY_ID
        val maskStacks = selected.stacks.filter { it.realization == Realization.Mask }
        capabilityRefusal(capabilities, maskStacks)?.let { return it }
        val base = when (val result = w4dSeam.plan(selected.base, capabilities, budget)) {
            is RenderPlanResult.Ready -> result.plan
            is RenderPlanResult.GapNotMigrated -> return RenderPlanResult.GapNotMigrated(listOf(
                diag(W4ePlanDiagnostics.CommandNotMigrated, RenderDiagnosticDomain.SCENE, "W4d.2 construction seam declined W4e draw facts"),
            ))
            is RenderPlanResult.InvalidScene -> return invalidCandidate()
            is RenderPlanResult.GapOnPromotedScope -> return promoted("W4d.2 construction capability is unavailable")
            is RenderPlanResult.ResourceLimitExceeded -> return resource(W4ePlanDiagnostics.BudgetFrameLocalExceeded, "W4d.2 frame resources are exceeded")
        }
        return try {
            val graph = insertClips(base, selected, capabilities, budget, requiresAa)
            RenderPlanResult.Ready(graph)
        } catch (_: ClipBudgetExceeded) {
            resource(W4ePlanDiagnostics.BudgetFrameLocalExceeded, "W4e pooled clip resources exceed the frame budget")
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
    ): RenderGraph {
        val maskStacks = selected.stacks.filter { it.realization == Realization.Mask }
        val prefix = mutableListOf<PlanPass>()
        val resources = mutableListOf<PlanResource>()
        val strategyByCommand = mutableMapOf<Int, ClipPlanStrategy>()
        val extent = base.targetExtent
        val domain = RectI32(0, 0, extent.width, extent.height)
        val prefixCount = selected.stacks.sumOf { stack ->
            if (stack.realization == Realization.Mask) 1 + stack.entries.count { it.geometryF32 != ClipGeometryF32.Empty } * 2 else 0
        }

        selected.stacks.forEachIndexed { ordinal, stack ->
            val strategy = when (stack.realization) {
                Realization.Scissor -> ClipPlanStrategy.Scissor(requireNotNull(stack.scissor))
                Realization.Mask -> {
                    val ids = maskResources(resources, stack, ordinal, extent, prefix.size, prefixCount, base)
                    val group = PlanAtomicGroupId("w4e.clip:$ordinal")
                    var accumulator = ids.firstAccumulator
                    prefix += PlanPass.ClipMaskInitialize(ordinal, accumulator, domain, 1f, group)
                    stack.entries.forEachIndexed { entryIndex, entry ->
                        if (entry.geometryF32 == ClipGeometryF32.Empty) return@forEachIndexed
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
                    val resource = (strategy as? ClipPlanStrategy.Mask)?.resource
                        ?: error("Inverse path draws require a finite mask realization")
                    ClipPlanStrategy.InverseMask(inverse, resource)
                } ?: strategy
            }
        }

        val clippedGeneralBySource = mutableMapOf<GeneralPathDraw, ClippedGeneralPathDraw>()
        val transformedPasses = base.passes().map { pass ->
            pass.withClipStrategies(strategyByCommand, clippedGeneralBySource)
        }
        val shiftedResources = base.resources().map { resource -> resource.shifted(prefixCount) }
        val allResources = resources + shiftedResources
        val allPasses = prefix + transformedPasses
        val dependencies = allPasses.zipWithNext().map { (before, after) -> PlanPassDependency(before.id, after.id) }
        val peakFrameLocalBytes = peak(allResources, allPasses.size)
        if (peakFrameLocalBytes > budget.maxFrameLocalBytes) throw ClipBudgetExceeded()
        return RenderGraph.issueW4eCompilerWitness(RenderGraph.of(
            id = PlanId(identity(selected, capabilities, budget, frameAa)),
            capabilityId = if (frameAa) AA_CAPABILITY_ID else HARD_CAPABILITY_ID,
            targetExtent = extent,
            colorFormat = base.colorFormat,
            capabilities = capabilities,
            budget = budget,
            visualCommandCount = base.visualCommandCount,
            resources = allResources,
            passes = allPasses,
            dependencies = dependencies,
            peakFrameLocalBytes = peakFrameLocalBytes,
        ))
    }

    private fun maskResources(
        resources: MutableList<PlanResource>,
        stack: PreparedStack,
        ordinal: Int,
        extent: SizeI32,
        firstPass: Int,
        prefixCount: Int,
        base: RenderGraph,
    ): MaskResourceIds {
        val hasAaProducer = stack.entries.any { it.antiAlias && it.geometryF32 !is ClipGeometryF32.Rect }
        val hasHardPathProducer = stack.entries.any { !it.antiAlias && it.geometryF32 is ClipGeometryF32.Path }
        val first = planResourceId(PlanResourceRole.CoverageMaskAccumulator, ordinal * 2)
        val second = planResourceId(PlanResourceRole.CoverageMaskAccumulator, ordinal * 2 + 1)
        val scratch = planResourceId(PlanResourceRole.CoverageMaskScratch, ordinal)
        val multisample = hasAaProducer.thenId(PlanResourceRole.CoverageMaskMultisampleScratch, ordinal)
        val aaDepth = hasAaProducer.thenId(PlanResourceRole.CoverageMaskDepthStencil, ordinal * 2)
        val hardDepth = hasHardPathProducer.thenId(PlanResourceRole.CoverageMaskDepthStencil, ordinal * 2 + 1)
        val lastUses = mutableMapOf<PlanResourceId, Int>()
        fun use(id: PlanResourceId, index: Int) { lastUses[id] = maxOf(lastUses[id] ?: -1, index) }
        var accumulator = first
        use(first, firstPass)
        var pass = firstPass + 1
        stack.entries.forEach { entry ->
            if (entry.geometryF32 == ClipGeometryF32.Empty) return@forEach
            val usesAa = entry.antiAlias && entry.geometryF32 !is ClipGeometryF32.Rect
            val target = if (usesAa) requireNotNull(multisample) else scratch
            val resolved = if (usesAa) scratch else target
            use(target, pass)
            if (usesAa) use(requireNotNull(aaDepth), pass)
            if (!usesAa && entry.geometryF32 is ClipGeometryF32.Path) use(requireNotNull(hardDepth), pass)
            val output = if (accumulator == first) second else first
            use(accumulator, pass + 1); use(resolved, pass + 1); use(output, pass + 1)
            accumulator = output
            pass += 2
        }
        val consumerIndices = base.passes().withIndex().filter { (_, value) ->
            value is PlanPass.PathRenderPass && value.phase in setOf(
                PathRenderPhase.SingleSampleDirectColor,
                PathRenderPhase.SingleSampleStencilColorCover,
                PathRenderPhase.MultisampleDirectColor,
                PathRenderPhase.MultisampleStencilColorCover,
                PathRenderPhase.HardEdgeBinaryColorCover,
            ) && value.draw.commandIndex in stack.consumerIndexes
        }.map { it.index + prefixCount }
        consumerIndices.forEach { use(accumulator, it) }
        fun texture(role: PlanResourceRole, resourceOrdinal: Int, format: PlanTextureFormat, samples: Int, id: PlanResourceId): PlanResource {
            val bytes = ClipPlanBudget.checkedMaskTextureBytesI64(extent.width, extent.height, samples)
            return PlanResource.of(
                role, resourceOrdinal, PlanResourceKind.Texture2D, format, extent, bytes,
                if (format == PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8)) {
                    setOf(PlanResourceUsage.DepthStencilAttachment)
                } else if (samples == 1) {
                    setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled)
                } else {
                    setOf(PlanResourceUsage.RenderAttachment)
                },
                PlanResourceLifetime.FrameLocal, firstPass, requireNotNull(lastUses[id]) + 1, samples,
            )
        }
        resources += texture(PlanResourceRole.CoverageMaskAccumulator, ordinal * 2, PlanTextureFormat.CoverageMask, 1, first)
        resources += texture(PlanResourceRole.CoverageMaskAccumulator, ordinal * 2 + 1, PlanTextureFormat.CoverageMask, 1, second)
        resources += texture(PlanResourceRole.CoverageMaskScratch, ordinal, PlanTextureFormat.CoverageMask, 1, scratch)
        multisample?.let { resources += texture(PlanResourceRole.CoverageMaskMultisampleScratch, ordinal, PlanTextureFormat.CoverageMask, 4, it) }
        aaDepth?.let { resources += texture(PlanResourceRole.CoverageMaskDepthStencil, ordinal * 2, PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), 4, it) }
        hardDepth?.let { resources += texture(PlanResourceRole.CoverageMaskDepthStencil, ordinal * 2 + 1, PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), 1, it) }
        return MaskResourceIds(first, second, scratch, multisample, aaDepth, hardDepth)
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
        val anyAa = stacks.any { stack -> stack.entries.any { it.antiAlias && it.geometryF32 !is ClipGeometryF32.Rect } }
        if (anyAa && (!capabilities.supportsTexture(PlanTextureFormat.CoverageMask, 4, setOf(PlanResourceUsage.RenderAttachment)) ||
                !capabilities.supportsResolve(PlanTextureFormat.CoverageMask, 4, 1) ||
                !capabilities.supportsTexture(PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), 4, setOf(PlanResourceUsage.DepthStencilAttachment)))) {
            return promoted(W4ePlanDiagnostics.SampleCountUnavailable, "W4e AA clip producer support is unavailable")
        }
        val hardPath = stacks.any { stack -> stack.entries.any { !it.antiAlias && it.geometryF32 is ClipGeometryF32.Path } }
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
        if (operations.entryCount > MAX_CLIP_ENTRIES) {
            return PreparedResult.Limit("W4e accepts at most $MAX_CLIP_ENTRIES clip entries per stack")
        }
        val inputs = buildList {
            operations.forEach { entry ->
                val input = entry.toTransformInputOrNull() ?: return PreparedResult.Invalid("LegacyUnavailable clip transform")
                add(input)
            }
        }
        return when (val result = prepareTransformedClipStackGeometryF32(inputs, domain, clipPolicyF64, frameWorkUsageBeforeI64 = frameBefore)) {
            is ClipStackPreparationResult.Ready -> PreparedResult.Ready(
                PreparedStack(
                    result.entriesF32,
                    result.frameWorkUsageAfterI64,
                    if (forceMask) Realization.Mask else selectRealization(result.entriesF32),
                ),
            )
            is ClipStackPreparationResult.InvalidScene -> PreparedResult.Invalid("Clip math rejected non-finite geometry")
            is ClipStackPreparationResult.ResourceLimitExceeded -> PreparedResult.Limit("Clip math limit: ${result.reason}")
        }
    }

    private fun selectRealization(entries: List<ClipPreparedEntryF32>): Realization =
        if (entries.size == 1 && entries.single().operation == MathClipOperation.Intersect &&
            !entries.single().antiAlias && !entries.single().inverseFill && entries.single().geometryF32 is ClipGeometryF32.Rect &&
            !entries.single().copyConservativeScissorI32().isEmpty
        ) Realization.Scissor else Realization.Mask

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

    private fun DrawNode.normalizedForW4d(): DrawNode {
        val path = geometry as? GeometryNode.Path ?: return copy(clip = ClipStackNode.Empty)
        val rule = when (path.path.fillRule) {
            FillRule.INVERSE_WINDING -> FillRule.WINDING
            FillRule.INVERSE_EVEN_ODD -> FillRule.EVEN_ODD
            else -> path.path.fillRule
        }
        val normalizedGeometry = if (rule == path.path.fillRule) geometry else GeometryNode.Path(PathBuilder(rule).addPath(path.path).build())
        return copy(geometry = normalizedGeometry, clip = ClipStackNode.Empty)
    }

    private fun DrawNode.isW4eFillScope(): Boolean =
        geometry is GeometryNode.Path &&
            material is org.graphiks.kanvas.render.ir.MaterialNode.Solid &&
            blend == org.graphiks.kanvas.render.ir.BlendNode.SrcOver &&
            paint?.style == org.graphiks.kanvas.render.ir.PaintStyleNode.FILL

    private fun DrawNode.prepareInversePathOrNull(domain: RectI32): InverseResult {
        val path = (geometry as? GeometryNode.Path)?.path ?: return InverseResult.None
        if (path.fillRule !in setOf(FillRule.INVERSE_WINDING, FillRule.INVERSE_EVEN_ODD)) return InverseResult.None
        if (paint?.style != org.graphiks.kanvas.render.ir.PaintStyleNode.FILL) {
            return InverseResult.Invalid("W4e currently supports inverse fills only")
        }
        return when (val prepared = transform.toMatrix3x3F64().prepareTransformedInversePathGeometryF32(
            path,
            styleF64 = null,
            mode = InversePathDrawMode.Fill,
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

    private fun peak(resources: List<PlanResource>, passCount: Int): Long = (0 until passCount).maxOf { index ->
        resources.filter { it.firstPassIndex <= index && index < it.lastPassIndexExclusive }
            .fold(0L) { sum, resource -> Math.addExact(sum, resource.byteSize) }
    }

    private fun identity(selected: Candidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget, aa: Boolean): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val fields = listOf(
            "w4e-clip-plan-v1", selected.sceneCanonicalId.value, selected.target.canonicalId.value,
            if (aa) AA_CAPABILITY_ID else HARD_CAPABILITY_ID, budget.maxFrameLocalBytes.toString(),
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
    private sealed interface InverseResult {
        data object None : InverseResult
        data class Ready(val geometry: InversePathGeometryF32) : InverseResult
        data class Invalid(val message: String) : InverseResult
        data class Limit(val message: String) : InverseResult
    }
    private enum class Realization { Scissor, Mask }
    private data class ClipStackReuseKey(val canonicalStackId: String, val extentI32: SizeI32, val format: PlanLogicalColorFormat, val samplePlan: SamplePlan, val transformIds: List<String>)
    private class PreparedStack(val entries: List<ClipPreparedEntryF32>, val frameUsageAfterI64: ClipWorkUsageI64, realization: Realization) {
        var realization: Realization = realization
            private set
        val consumerIndexes: MutableList<Int> = mutableListOf()
        val scissor: RectI32? get() = if (realization == Realization.Scissor) entries.single().copyConservativeScissorI32() else null
        val identity: String = entries.joinToString("/") { "${it.operation}:${it.antiAlias}:${it.inverseFill}:${it.copyConservativeScissorI32()}" }
        fun forceMask() { realization = Realization.Mask }
    }
    private data class MaskResourceIds(val firstAccumulator: PlanResourceId, val secondAccumulator: PlanResourceId, val scratch: PlanResourceId, val multisampleScratch: PlanResourceId?, val aaDepth: PlanResourceId?, val hardDepth: PlanResourceId?)
    private class Candidate(
        val owner: W4eClipPlanCompiler,
        override val sceneCanonicalId: org.graphiks.kanvas.render.ir.CanonicalId,
        override val target: RenderTargetDescriptor,
        val base: GpuPlanCandidate,
        stacks: List<PreparedStack>,
        inverseByCommand: Map<Int, InversePathGeometryF32>,
    ) : GpuPlanCandidate {
        override val capabilityId: String = if (base.capabilityId == W4dGeneralPathPlanCompiler.AA_CAPABILITY_ID) AA_CAPABILITY_ID else HARD_CAPABILITY_ID
        val stacks: List<PreparedStack> = Collections.unmodifiableList(stacks)
        val inverseByCommand: Map<Int, InversePathGeometryF32> = Collections.unmodifiableMap(inverseByCommand.toMap())
        private val sceneFingerprint = sceneCanonicalId
        private val targetFingerprint = target.canonicalId
        fun matches(): Boolean = sceneCanonicalId == sceneFingerprint && target.canonicalId == targetFingerprint && (capabilityId == HARD_CAPABILITY_ID || capabilityId == AA_CAPABILITY_ID)
    }

    private class ClipBudgetExceeded : RuntimeException()

    private fun Boolean.thenId(role: PlanResourceRole, ordinal: Int): PlanResourceId? =
        if (this) planResourceId(role, ordinal) else null

    public companion object {
        public const val HARD_CAPABILITY_ID: String = "solid-path-complex-clip-hard-1x-src-over-srgb-v1"
        public const val AA_CAPABILITY_ID: String = "solid-path-complex-clip-mixed-aa4-src-over-srgb-v1"
        private const val MAX_DRAWS: Int = 512
        private const val MAX_CLIP_ENTRIES: Int = 512
    }
}
