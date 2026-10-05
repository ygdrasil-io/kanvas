package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.geometry.InverseInteriorCoverageF32
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.matchesCanonicalPathFillGeometryF32
import org.graphiks.math.matrix.TargetLocalDeviceProvenanceI32

/** Compiler-issued W4e inverse-domain AA coverage authority; it is never reconstructed from phases. */
public class PlanW4eInverseAaCoverageSourceBindingV1 private constructor(
    override val ownerPassId: PlanPassId,
    override val commandIndexI32: Int,
    sourcePasses: List<PlanPass.PathRenderPass>,
    passes: List<PlanPass.PathRenderPass>,
    resourceRemapping: Map<PlanResourceId, PlanResourceId>,
    sourceResources: List<PlanResource>,
    resources: List<PlanResource>,
    sourceExtent: SizeI32,
    extent: SizeI32,
    sourceOrigin: Point2I32,
    origin: Point2I32,
    private val sourcePayload: W4eNativePayloadPlan,
    private val payloadValue: W4eNativePayloadPlan,
    private val coordinateProvenanceValue: TargetLocalDeviceProvenanceI32,
) : PlanAaCoverageSourceBindingV1 {
    /** Immutable original/final attachment and coordinate facts for one closed W7 operation. */
    public class OperationFacts internal constructor(
        public val sourcePassId: PlanPassId,
        public val passId: PlanPassId,
        public val sourceTarget: PlanResourceId,
        public val target: PlanResourceId,
        public val sourceResolveTarget: PlanResourceId?,
        public val resolveTarget: PlanResourceId?,
        public val sourceDepthStencil: PlanResourceId?,
        public val depthStencil: PlanResourceId?,
        public val sourcePhase: PathRenderPhase,
        public val phase: PathRenderPhase,
        public val sourceLoad: AttachmentLoadPlan,
        public val load: AttachmentLoadPlan,
        public val sourceStore: AttachmentStorePlan,
        public val store: AttachmentStorePlan,
        public val sourceAtomicGroup: PlanAtomicGroupId?,
        public val atomicGroup: PlanAtomicGroupId?,
        public val sourceCommandIndexI32: Int,
        public val commandIndexI32: Int,
        sourceDomainTargetLocalI32: RectI32,
        domainTargetLocalI32: RectI32,
        sourceScissorI32: RectI32,
        scissorI32: RectI32,
        sourceExtentI32: SizeI32,
        extentI32: SizeI32,
        sourceOriginDeviceI32: Point2I32,
        originDeviceI32: Point2I32,
    ) {
        private val sourceDomainSnapshotI32 = sourceDomainTargetLocalI32.copy()
        private val domainSnapshotI32 = domainTargetLocalI32.copy()
        private val sourceScissorSnapshotI32 = sourceScissorI32.copy()
        private val scissorSnapshotI32 = scissorI32.copy()
        private val sourceExtentSnapshotI32 = sourceExtentI32.copy()
        private val extentSnapshotI32 = extentI32.copy()
        private val sourceOriginSnapshotI32 = Point2I32(sourceOriginDeviceI32.x, sourceOriginDeviceI32.y)
        private val originSnapshotI32 = Point2I32(originDeviceI32.x, originDeviceI32.y)

        public fun copySourceDomainTargetLocalI32(): RectI32 = sourceDomainSnapshotI32.copy()
        public fun copyDomainTargetLocalI32(): RectI32 = domainSnapshotI32.copy()
        public fun copySourceScissorI32(): RectI32 = sourceScissorSnapshotI32.copy()
        public fun copyScissorI32(): RectI32 = scissorSnapshotI32.copy()
        public fun copySourceExtentI32(): SizeI32 = sourceExtentSnapshotI32.copy()
        public fun copyExtentI32(): SizeI32 = extentSnapshotI32.copy()
        public fun copySourceOriginDeviceI32(): Point2I32 = Point2I32(sourceOriginSnapshotI32.x, sourceOriginSnapshotI32.y)
        public fun copyOriginDeviceI32(): Point2I32 = Point2I32(originSnapshotI32.x, originSnapshotI32.y)
    }

    /** Closed native operations; their coordinate, attachment, slice and operand facts are issued together. */
    public sealed interface Operation {
        public val facts: OperationFacts
        public val passId: PlanPassId get() = facts.passId
        public val operands: List<String>
    }
    public class InteriorStencil internal constructor(
        override val facts: OperationFacts, geometryF32: PathFillGeometryF32,
        public val sourceSlice: W4eNativeGeometrySlice, public val slice: W4eNativeGeometrySlice,
        public val mode: InteriorMode, operands: List<String> = listOf("Pipeline", "Vertex", "Index"),
    ) : Operation {
        private val interiorSnapshot = InverseInteriorCoverageF32.Geometry.of(geometryF32)
        override val operands: List<String> = immutableList(operands)
        public fun copyInteriorGeometryF32(): PathFillGeometryF32 = interiorSnapshot.copyGeometryF32()
    }
    public class ZeroWhiteCover internal constructor(
        override val facts: OperationFacts,
        public val sourceSlice: W4eNativeUniformSlice, public val slice: W4eNativeUniformSlice,
        operands: List<String> = listOf("Pipeline", "BindGroup"),
    ) : Operation {
        override val operands: List<String> = immutableList(operands)
    }
    public enum class InteriorMode { DirectReplaceOne, Winding, Parity }
    /** Renderer operand sequence derived from the sealed two-operation recipe, never phase inference. */
    public enum class NativeOperandV1 {
        MsaaColorTarget, ResolveTarget, DepthStencilTarget,
        ProducerPipeline, ProducerVertex, ProducerIndex, CoverPipeline, CoverBindGroup,
    }
    override val sourceCapabilityId: String = W4eClipPlanCompiler.W7_INVERSE_AA_COVERAGE_SOURCE_CAPABILITY_ID
    private val sourcePhaseValues = immutableList(sourcePasses)
    private val sourceIds = immutableList(sourcePhaseValues.map { it.id })
    private val phaseValues = immutableList(passes)
    private val remappingValues = java.util.Collections.unmodifiableMap(LinkedHashMap(resourceRemapping))
    private val sourceResourceValues = immutableList(sourceResources)
    private val resourceValues = immutableList(resources)
    private val sourceExtentValue = sourceExtent.copy()
    private val extentValue = extent.copy()
    private val sourceOriginValue = Point2I32(sourceOrigin.x, sourceOrigin.y)
    private val originValue = Point2I32(origin.x, origin.y)
    override val recipe: W4eInverseAaCoverageSourceNativeSiteRecipeV1
    private val operationValues: List<Operation> = run {
        val sourceProducer = sourcePhaseValues.first(); val sourceCover = sourcePhaseValues.last()
        val producer = phaseValues.first(); val cover = phaseValues.last()
        val sourceInverse = requireNotNull(sourceProducer.draw.inverseDomainClipOrNullV1())
        val inverse = requireNotNull(producer.draw.inverseDomainClipOrNullV1())
        val interior = inverse.geometryF32.interiorCoverageF32 as? org.graphiks.math.geometry.InverseInteriorCoverageF32.Geometry
            ?: error("W7 inverse AA requires Geometry interior")
        val sourceGeometry = requireNotNull(sourcePayload.geometrySlice(sourceProducer.id.value, W4eNativePayloadPlan.INVERSE_DOMAIN_INTERIOR))
        val geometry = requireNotNull(payloadValue.geometrySlice(producer.id.value, W4eNativePayloadPlan.INVERSE_DOMAIN_INTERIOR))
        val sourceUniform = requireNotNull(sourcePayload.uniformSlice(sourceCover.id.value, W4eNativePayloadPlan.INVERSE_DOMAIN_UNIFORM))
        val uniform = requireNotNull(payloadValue.uniformSlice(cover.id.value, W4eNativePayloadPlan.INVERSE_DOMAIN_UNIFORM))
        fun facts(source: PlanPass.PathRenderPass, final: PlanPass.PathRenderPass): OperationFacts {
            val sourceDomain = requireNotNull(source.draw.inverseDomainClipOrNullV1()).geometryF32.copyDomainI32()
            val finalDomain = requireNotNull(final.draw.inverseDomainClipOrNullV1()).geometryF32.copyDomainI32()
            return OperationFacts(source.id, final.id, source.target, final.target, source.resolveTarget, final.resolveTarget,
                source.depthStencil, final.depthStencil, source.phase, final.phase, source.load, final.load, source.store,
                final.store, source.atomicGroup, final.atomicGroup, source.draw.commandIndex, final.draw.commandIndex,
                sourceDomain, finalDomain, source.draw.copyScissorI32(), final.draw.copyScissorI32(), sourceExtentValue,
                extentValue, sourceOriginValue, originValue)
        }
        val mode = when (producer.draw.strategy) {
            PathFillStrategy.DirectTriangle -> InteriorMode.DirectReplaceOne
            PathFillStrategy.StencilCover -> if (interior.copyGeometryF32().fillRule in setOf(org.graphiks.math.geometry.FillRule.EVEN_ODD, org.graphiks.math.geometry.FillRule.INVERSE_EVEN_ODD)) InteriorMode.Parity else InteriorMode.Winding
        }
        require(sourceInverse.geometryF32.interiorCoverageF32 is org.graphiks.math.geometry.InverseInteriorCoverageF32.Geometry)
        immutableList(listOf(
            InteriorStencil(facts(sourceProducer, producer), interior.copyGeometryF32(), sourceGeometry, geometry, mode),
            ZeroWhiteCover(facts(sourceCover, cover), sourceUniform, uniform),
        ))
    }

    override fun sourcePassIds(): List<PlanPassId> = sourceIds
    override fun passes(): List<PlanPass.PathRenderPass> = phaseValues
    override fun resourceRemapping(): Map<PlanResourceId, PlanResourceId> = remappingValues
    override fun resources(): List<PlanResource> = resourceValues
    override fun copyExtentI32(): SizeI32 = extentValue.copy()
    override fun copyOriginDeviceI32(): Point2I32 = Point2I32(originValue.x, originValue.y)
    /** The final occurrence payload.  The renderer must not reconstruct it from phases. */
    public fun payload(): W4eNativePayloadPlan = payloadValue

    internal fun sourcePayload(): W4eNativePayloadPlan = sourcePayload
    /** The factory-issued raster-local payload origin; it is intentionally distinct from owner device origin. */
    public fun copySourceRasterOriginLocalI32(): Point2I32 = sourcePayload.copyOriginDeviceI32()
    public fun coordinateProvenance(): TargetLocalDeviceProvenanceI32 = coordinateProvenanceValue
    public fun operations(): List<Operation> = operationValues
    public fun nativeOperandSequenceV1(): List<NativeOperandV1> {
        require(validatesNativeOperationFacts()) { "W7 inverse-AA operation record no longer matches its issued source/final facts" }
        return buildList {
            add(NativeOperandV1.MsaaColorTarget); add(NativeOperandV1.ResolveTarget); add(NativeOperandV1.DepthStencilTarget)
            operationValues.forEach { operation -> when (operation) {
                is InteriorStencil -> operation.operands.forEach { operand -> add(when (operand) {
                    "Pipeline" -> NativeOperandV1.ProducerPipeline
                    "Vertex" -> NativeOperandV1.ProducerVertex
                    "Index" -> NativeOperandV1.ProducerIndex
                    else -> error("W7 interior recipe has an unsealed operand")
                }) }
                is ZeroWhiteCover -> operation.operands.forEach { operand -> add(when (operand) {
                    "Pipeline" -> NativeOperandV1.CoverPipeline
                    "BindGroup" -> NativeOperandV1.CoverBindGroup
                    else -> error("W7 cover recipe has an unsealed operand")
                }) }
            } }
        }
    }

    /** Re-check the immutable original/final records at every consumer boundary. */
    public fun validatesNativeOperationFacts(): Boolean {
        if (sourcePhaseValues.size != 2 || phaseValues.size != 2 || operationValues.size != 2 ||
            !sameSizeI32(sourceExtentValue, extentValue)) return false
        if (remappingValues.keys != sourceResourceValues.map { it.id }.toSet() ||
            remappingValues.values.toSet() != resourceValues.map { it.id }.toSet()) return false
        val sourceProducer = sourcePhaseValues[0]; val sourceCover = sourcePhaseValues[1]
        val producer = phaseValues[0]; val cover = phaseValues[1]
        val interior = operationValues.getOrNull(0) as? InteriorStencil ?: return false
        val white = operationValues.getOrNull(1) as? ZeroWhiteCover ?: return false
        fun factsMatch(facts: OperationFacts, source: PlanPass.PathRenderPass, final: PlanPass.PathRenderPass): Boolean {
            val sourceInverse = source.draw.inverseDomainClipOrNullV1() ?: return false
            val finalInverse = final.draw.inverseDomainClipOrNullV1() ?: return false
            return facts.sourcePassId == source.id && facts.passId == final.id &&
                facts.sourceTarget == source.target && facts.target == final.target &&
                facts.sourceResolveTarget == source.resolveTarget && facts.resolveTarget == final.resolveTarget &&
                facts.sourceDepthStencil == source.depthStencil && facts.depthStencil == final.depthStencil &&
                facts.sourcePhase == source.phase && facts.phase == final.phase &&
                facts.sourceLoad == source.load && facts.load == final.load && facts.sourceStore == source.store &&
                facts.store == final.store && facts.sourceAtomicGroup == source.atomicGroup && facts.atomicGroup == final.atomicGroup &&
                facts.sourceCommandIndexI32 == source.draw.commandIndex && facts.commandIndexI32 == final.draw.commandIndex &&
                sameRectI32(facts.copySourceDomainTargetLocalI32(), sourceInverse.geometryF32.copyDomainI32()) &&
                sameRectI32(facts.copyDomainTargetLocalI32(), finalInverse.geometryF32.copyDomainI32()) &&
                sameRectI32(facts.copySourceScissorI32(), source.draw.copyScissorI32()) &&
                sameRectI32(facts.copyScissorI32(), final.draw.copyScissorI32()) &&
                sameSizeI32(facts.copySourceExtentI32(), sourceExtentValue) && sameSizeI32(facts.copyExtentI32(), extentValue) &&
                samePointI32(facts.copySourceOriginDeviceI32(), sourceOriginValue) &&
                samePointI32(facts.copyOriginDeviceI32(), originValue)
        }
        val sourceProducerGeometry = (sourceProducer.draw.inverseDomainClipOrNullV1()?.geometryF32?.interiorCoverageF32 as?
            InverseInteriorCoverageF32.Geometry)?.copyGeometryF32() ?: return false
        val producerGeometry = (producer.draw.inverseDomainClipOrNullV1()?.geometryF32?.interiorCoverageF32 as?
            InverseInteriorCoverageF32.Geometry)?.copyGeometryF32() ?: return false
        return coordinateProvenanceValue.matchesBinding(sourceExtentValue,
            interior.facts.copySourceDomainTargetLocalI32(), sourcePayload.copyOriginDeviceI32(), originValue) &&
            samePointI32(payloadValue.copyOriginDeviceI32(), sourcePayload.copyOriginDeviceI32()) &&
            sameRectI32(interior.facts.copySourceDomainTargetLocalI32(), white.facts.copySourceDomainTargetLocalI32()) &&
            containsRectI32(coordinateProvenanceValue.copyTargetLocalDomainI32(), interior.facts.copyDomainTargetLocalI32()) &&
            sourcePayload.matchesDeclaredResources(sourceResourceValues) &&
            payloadValue.matchesDeclaredResources(resourceValues) &&
            factsMatch(interior.facts, sourceProducer, producer) && factsMatch(white.facts, sourceCover, cover) &&
            sourceProducerGeometry.matchesCanonicalPathFillGeometryF32(interior.copyInteriorGeometryF32()) &&
            producerGeometry.matchesCanonicalPathFillGeometryF32(interior.copyInteriorGeometryF32()) &&
            sameGeometrySlice(interior.sourceSlice, sourcePayload.geometrySlice(sourceProducer.id.value, W4eNativePayloadPlan.INVERSE_DOMAIN_INTERIOR)) &&
            sameGeometrySlice(interior.slice, payloadValue.geometrySlice(producer.id.value, W4eNativePayloadPlan.INVERSE_DOMAIN_INTERIOR)) &&
            sameUniformSlice(white.sourceSlice, sourcePayload.uniformSlice(sourceCover.id.value, W4eNativePayloadPlan.INVERSE_DOMAIN_UNIFORM)) &&
            sameUniformSlice(white.slice, payloadValue.uniformSlice(cover.id.value, W4eNativePayloadPlan.INVERSE_DOMAIN_UNIFORM)) &&
            sameReboundGeometrySlice(interior.sourceSlice, interior.slice) &&
            sameReboundUniformSlice(white.sourceSlice, white.slice) &&
            sameRectI32(interior.facts.copyDomainTargetLocalI32(), white.facts.copyDomainTargetLocalI32()) &&
            interior.facts.target == white.facts.target && interior.facts.depthStencil == white.facts.depthStencil &&
            interior.facts.resolveTarget == null && white.facts.resolveTarget != null &&
            interior.operands == listOf("Pipeline", "Vertex", "Index") && white.operands == listOf("Pipeline", "BindGroup")
    }

    init {
        require(commandIndexI32 >= 0 && sourceExtentValue.width > 0 && sourceExtentValue.height > 0 &&
            extentValue.width > 0 && extentValue.height > 0 && sourcePhaseValues.size == phaseValues.size &&
            sourceIds.size == phaseValues.size && sourceIds.isNotEmpty() && sourceIds.distinct().size == sourceIds.size)
        require(phaseValues.size == 2 && phaseValues.all { it.draw.commandIndex == commandIndexI32 &&
            it.draw.coverage == CoveragePlan.StencilAA4 && it.draw.sample == SamplePlan.Multisample4 &&
            it.draw.inverseDomainClipOrNullV1() != null })
        val terminal = when (phaseValues.size) {
            2 -> phaseValues[1].also { cover ->
                val producer = phaseValues[0]
                require(producer.phase == PathRenderPhase.MultisampleStencilProducer &&
                    cover.phase == PathRenderPhase.MultisampleStencilColorCover &&
                    producer.target == cover.target && producer.depthStencil != null && producer.depthStencil == cover.depthStencil &&
                    producer.atomicGroup != null && producer.atomicGroup == cover.atomicGroup &&
                    producer.load == AttachmentLoadPlan.ClearTransparent && producer.store == AttachmentStorePlan.Store &&
                    producer.depthStencilAccess == PlanDepthStencilAccess.Write &&
                    producer.depthStencilLoadStore == PlanDepthStencilLoadStore.ClearZeroStore && producer.resolveTarget == null &&
                    cover.load == AttachmentLoadPlan.Load && cover.store == AttachmentStorePlan.Store &&
                    cover.depthStencilAccess == PlanDepthStencilAccess.ReadWrite &&
                    cover.depthStencilLoadStore == PlanDepthStencilLoadStore.LoadStoreTestReset)
            }
            else -> error("W4e inverse AA coverage requires one ordered stencil pair")
        }
        require(terminal.resolveTarget != null)
        require(remappingValues.keys.size == remappingValues.values.size && remappingValues.values.toSet() == resourceValues.map { it.id }.toSet())
        require(resourceValues.size == 6 && resourceValues.count { it.role == PlanResourceRole.MultisampleColorTarget } == 1 &&
            resourceValues.count { it.role == PlanResourceRole.CoverageSource } == 1 &&
            resourceValues.count { it.role == PlanResourceRole.DepthStencil } == 1 &&
            resourceValues.count { it.role in setOf(PlanResourceRole.VertexData, PlanResourceRole.IndexData, PlanResourceRole.UniformData) } == 3)
        require(resourceValues.single { it.role == PlanResourceRole.CoverageSource }.sampleCountI32 == 1 &&
            resourceValues.single { it.role == PlanResourceRole.MultisampleColorTarget }.sampleCountI32 == 4 &&
            resourceValues.single { it.role == PlanResourceRole.DepthStencil }.sampleCountI32 == 4)
        val target = resourceValues.single { it.id == terminal.target }
        val resolve = resourceValues.single { it.id == terminal.resolveTarget }
        require(target.copyExtent() == extentValue && resolve.copyExtent() == extentValue &&
            target.kind == PlanResourceKind.Texture2D && resolve.kind == PlanResourceKind.Texture2D &&
            target.format == PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL) &&
            target.format == resolve.format && target.usages() == setOf(PlanResourceUsage.RenderAttachment) &&
            resolve.usages() == setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled))
        // The W4e source allocation has a fixed six-row ABI and every Geometry interior uses
        // its D24S8 row, including a DirectTriangle replace-one producer.
        require(resourceValues.map { it.id }.toSet() == buildSet {
            add(target.id); add(resolve.id); add(terminal.drawDataResources.vertex)
            add(terminal.drawDataResources.index); add(terminal.drawDataResources.uniform)
            add(resourceValues.single { it.role == PlanResourceRole.DepthStencil }.id)
        })
        require(resourceValues.all { it.lifetime == PlanResourceLifetime.FrameLocal })
        require(payloadValue.hasCanonicalWhiteUniform(
            requireNotNull(payloadValue.uniformSlice(terminal.id.value, W4eNativePayloadPlan.INVERSE_DOMAIN_UNIFORM)),
        ))
        require(operationValues.size == 2 && operationValues[0] is InteriorStencil && operationValues[1] is ZeroWhiteCover) {
            "W7 inverse-AA operations must retain producer then cover order"
        }
        require(validatesNativeOperationFacts()) { "W7 inverse-AA operation record no longer matches its issued source/final facts" }
        recipe = W4eInverseAaCoverageSourceNativeSiteRecipeV1(this)
    }

    internal companion object {
        fun issue(source: SourceDeferredRenderConstructionV4, ownerPassId: PlanPassId, commandIndexI32: Int,
            passes: List<PlanPass.PathRenderPass>, resourceRemapping: Map<PlanResourceId, PlanResourceId>,
            resources: List<PlanResource>, extent: SizeI32, origin: Point2I32,
            coordinateProvenance: TargetLocalDeviceProvenanceI32): PlanW4eInverseAaCoverageSourceBindingV1 {
            require(source.capabilityId == W4eClipPlanCompiler.W7_INVERSE_AA_COVERAGE_SOURCE_CAPABILITY_ID &&
                source.topology == DeferredLaneTopologyV4.AaResolvedCoverage)
            val sourcePayload = requireNotNull(source.w4ePayload)
            val originalPhases = source.passes().filterIsInstance<PlanPass.PathRenderPass>()
            require(originalPhases.size == 2 && passes.size == 2)
            val sourceDomain = requireNotNull(originalPhases.first().draw.inverseDomainClipOrNullV1()).geometryF32.copyDomainI32()
            require(coordinateProvenance.matchesBinding(source.targetExtent, sourceDomain,
                sourcePayload.copyOriginDeviceI32(), origin)) {
                "W7 inverse-AA binding target-local raster/device owner provenance differs"
            }
            val sourceResourceIds = source.resources().map { it.id }.toSet()
            require(resourceRemapping.keys == sourceResourceIds && resourceRemapping.values.toSet().size == 6 &&
                resourceRemapping.values.toSet() == resources.map { it.id }.toSet())
            require(originalPhases.zip(passes).all { (original, rebound) ->
                requireSameW7InverseDrawFacts(original.draw, rebound.draw)
                original.draw.commandIndex == commandIndexI32 && rebound.draw.commandIndex == commandIndexI32 &&
                    original.phase == rebound.phase && original.draw.strategy == rebound.draw.strategy &&
                    original.draw.materialAuthority.materialPlanRef() == rebound.draw.materialAuthority.materialPlanRef() &&
                    original.draw.blend == BlendPlan.SrcOver && rebound.draw.blend == BlendPlan.SrcOver &&
                    original.atomicGroup == canonicalGeneralPathAtomicGroup(requireNotNull((original.draw as? ClippedGeneralPathDraw)?.source)) &&
                    rebound.atomicGroup == canonicalGeneralPathAtomicGroup(requireNotNull((rebound.draw as? ClippedGeneralPathDraw)?.source)) &&
                    original.drawDataResources.let { data ->
                        resourceRemapping.getValue(data.vertex) == rebound.drawDataResources.vertex &&
                            resourceRemapping.getValue(data.index) == rebound.drawDataResources.index &&
                            resourceRemapping.getValue(data.uniform) == rebound.drawDataResources.uniform
                    } &&
                    resourceRemapping.getValue(original.target) == rebound.target &&
                    (original.resolveTarget?.let(resourceRemapping::getValue) == rebound.resolveTarget) &&
                    (original.depthStencil?.let(resourceRemapping::getValue) == rebound.depthStencil)
            })
            require(sourcePayload.matchesDeclaredResources(source.resources()) &&
                setOf(sourcePayload.vertexResourceId, sourcePayload.indexResourceId, sourcePayload.uniformResourceId)
                    .all { id -> resourceRemapping.containsKey(id) })
            val payload = sourcePayload.rebindForOccurrence(resourceRemapping,
                originalPhases.zip(passes).associate { (original, rebound) -> original.id.value to rebound.id.value })
            require(payload.matchesDeclaredResources(resources) &&
                payload.vertexResourceId == passes.last().drawDataResources.vertex &&
                payload.indexResourceId == passes.last().drawDataResources.index &&
                payload.uniformResourceId == passes.last().drawDataResources.uniform &&
                payload.copyVertexData().contentEquals(sourcePayload.copyVertexData()) &&
                payload.copyIndexData().contentEquals(sourcePayload.copyIndexData()) &&
                payload.copyUniformData().contentEquals(sourcePayload.copyUniformData()))
            require(samePointI32(payload.copyOriginDeviceI32(), sourcePayload.copyOriginDeviceI32()) &&
                payload.hasCanonicalWhiteUniform(
                requireNotNull(payload.uniformSlice(passes.last().id.value, W4eNativePayloadPlan.INVERSE_DOMAIN_UNIFORM)),
            ))
            return PlanW4eInverseAaCoverageSourceBindingV1(ownerPassId, commandIndexI32,
                originalPhases, passes, resourceRemapping, source.resources(), resources, source.targetExtent, extent,
                sourcePayload.copyOriginDeviceI32(), origin, sourcePayload, payload, coordinateProvenance)
        }
    }
}

private fun sameRectI32(first: RectI32, second: RectI32): Boolean =
    first.left == second.left && first.top == second.top && first.right == second.right && first.bottom == second.bottom

private fun sameSizeI32(first: SizeI32, second: SizeI32): Boolean =
    first.width == second.width && first.height == second.height

private fun containsRectI32(outer: RectI32, inner: RectI32): Boolean =
    outer.left <= inner.left && outer.top <= inner.top && outer.right >= inner.right && outer.bottom >= inner.bottom

private fun samePointI32(first: Point2I32, second: Point2I32): Boolean =
    first.x == second.x && first.y == second.y

private fun sameGeometrySlice(first: W4eNativeGeometrySlice, second: W4eNativeGeometrySlice?): Boolean =
    second != null && first == second

private fun sameUniformSlice(first: W4eNativeUniformSlice, second: W4eNativeUniformSlice?): Boolean =
    second != null && first == second

/** Resource/pass rebinding is the only permitted difference between source and final slice records. */
private fun sameReboundGeometrySlice(source: W4eNativeGeometrySlice, final: W4eNativeGeometrySlice): Boolean =
    source.purpose == final.purpose && source.firstIndex == final.firstIndex && source.indexCount == final.indexCount &&
        source.baseVertex == final.baseVertex && source.vertexCount == final.vertexCount && source.maxLocalIndex == final.maxLocalIndex

private fun sameReboundUniformSlice(source: W4eNativeUniformSlice, final: W4eNativeUniformSlice): Boolean =
    source.purpose == final.purpose && source.offsetBytes == final.offsetBytes && source.byteSize == final.byteSize

/** Fail closed with the first differing immutable source/rebound fact; this does not alter admission. */
private fun requireSameW7InverseDrawFacts(original: PathRenderDraw, rebound: PathRenderDraw) {
    val source = original as? ClippedGeneralPathDraw
    require(source != null) { "W7 inverse-AA source/rebound mismatch: original draw is not ClippedGeneralPathDraw" }
    val final = rebound as? ClippedGeneralPathDraw
    require(final != null) { "W7 inverse-AA source/rebound mismatch: final draw is not ClippedGeneralPathDraw" }
    val sourceInverse = source.clip as? ClipPlanStrategy.InverseDomain
    require(sourceInverse != null) { "W7 inverse-AA source/rebound mismatch: original clip is not InverseDomain" }
    val finalInverse = final.clip as? ClipPlanStrategy.InverseDomain
    require(finalInverse != null) { "W7 inverse-AA source/rebound mismatch: final clip is not InverseDomain" }
    val sourceInterior = sourceInverse.geometryF32.interiorCoverageF32 as? InverseInteriorCoverageF32.Geometry
    require(sourceInterior != null) { "W7 inverse-AA source/rebound mismatch: original interior is not Geometry" }
    val finalInterior = finalInverse.geometryF32.interiorCoverageF32 as? InverseInteriorCoverageF32.Geometry
    require(finalInterior != null) { "W7 inverse-AA source/rebound mismatch: final interior is not Geometry" }
    val sourceFiniteMesh = (source.copyPathGeometry() as? PathDrawGeometry.Fill)?.valueF32
    require(sourceFiniteMesh != null) {
        "W7 inverse-AA source/rebound mismatch: original finite construction mesh is not Fill"
    }
    val finalFiniteMesh = (final.copyPathGeometry() as? PathDrawGeometry.Fill)?.valueF32
    require(finalFiniteMesh != null) {
        "W7 inverse-AA source/rebound mismatch: final finite construction mesh is not Fill"
    }
    require(sameRectI32(sourceInverse.geometryF32.copyDomainI32(), finalInverse.geometryF32.copyDomainI32())) {
        "W7 inverse-AA source/rebound mismatch: inverse domain differs"
    }
    require(sameRectI32(source.copyScissorI32(), final.copyScissorI32())) {
        "W7 inverse-AA source/rebound mismatch: hard scissor differs"
    }
    require(sourceFiniteMesh.matchesCanonicalPathFillGeometryF32(finalFiniteMesh)) {
        "W7 inverse-AA source/rebound mismatch: finite construction mesh differs"
    }
    require(sourceInterior.copyGeometryF32().matchesCanonicalPathFillGeometryF32(finalInterior.copyGeometryF32())) {
        "W7 inverse-AA source/rebound mismatch: canonical interior geometry differs"
    }
}

public class W4eInverseAaCoverageSourceNativeSiteRecipeV1 internal constructor(
    public val binding: PlanW4eInverseAaCoverageSourceBindingV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = NativeSiteOwnerV1(binding.ownerPassId, 0, 0)
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W4eInverseAaCoverageSource
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("owner", binding.ownerPassId.value); text("capability", binding.sourceCapabilityId)
        int("command", binding.commandIndexI32)
        text("source-payload", binding.sourcePayloadIdentity()); text("payload", binding.payloadIdentity())
        binding.copyExtentI32().let { extent -> int("extent.width", extent.width); int("extent.height", extent.height) }
        binding.copyOriginDeviceI32().let { origin -> int("origin.x", origin.x); int("origin.y", origin.y) }
        binding.coordinateProvenance().let { provenance ->
            provenance.copySourceRasterDomainLocalI32().encodeRecipeRectI32("coordinate.source-raster-local-domain") { name, value -> int(name, value) }
            provenance.copyTargetLocalDomainI32().encodeRecipeRectI32("coordinate.target-local-domain") { name, value -> int(name, value) }
            provenance.copySourceDeviceDomainI32().encodeRecipeRectI32("coordinate.source-device-domain") { name, value -> int(name, value) }
            point("coordinate.source-raster-local-origin", provenance.copySourceRasterOriginLocalI32())
            point("coordinate.owner-device-origin", provenance.copyOwnerOriginDeviceI32())
            text("coordinate.mapping.present", (provenance.mappingOrNull() != null).toString())
            provenance.mappingOrNull()?.let { mapping ->
                point("coordinate.mapping.origin", mapping.copyLayerOriginDeviceI32())
                listOf(mapping.copyLocalToDeviceF64(), mapping.copyDeviceToLayerF64(), mapping.copyLocalToLayerF64())
                    .forEachIndexed { index, matrix ->
                        listOf(matrix.sxF64, matrix.kxF64, matrix.txF64, matrix.kyF64, matrix.syF64, matrix.tyF64,
                            matrix.persp0F64, matrix.persp1F64, matrix.persp2F64).forEachIndexed { coefficient, value ->
                            double("coordinate.mapping.$index.$coefficient", value)
                        }
                    }
            }
        }
        binding.nativeOperandSequenceV1().forEachIndexed { index, operand -> enum("operand.$index", operand) }
        binding.sourcePassIds().zip(binding.passes()).forEachIndexed { index, (source, pass) ->
            text("phase.$index.source", source.value); text("phase.$index.pass", pass.id.value)
            enum("phase.$index.kind", pass.phase); enum("phase.$index.load", pass.load); enum("phase.$index.store", pass.store)
            text("phase.$index.target", pass.target.value); pass.resolveTarget?.let { text("phase.$index.resolve", it.value) }
            pass.depthStencil?.let { text("phase.$index.depth", it.value) }; pass.atomicGroup?.let { text("phase.$index.group", it.value) }
            text("phase.$index.vertex", pass.drawDataResources.vertex.value); text("phase.$index.index", pass.drawDataResources.index.value)
            text("phase.$index.uniform", pass.drawDataResources.uniform.value); blend("phase.$index.blend", pass.draw.blend)
        }
        binding.operations().forEachIndexed { index, operation ->
            text("operation.$index.pass", operation.passId.value); text("operation.$index.kind", operation::class.simpleName ?: "")
            operation.operands.forEachIndexed { operand, value -> text("operation.$index.operand.$operand", value) }
            operation.facts.let { facts ->
                text("operation.$index.source-pass", facts.sourcePassId.value)
                text("operation.$index.source-target", facts.sourceTarget.value); text("operation.$index.target", facts.target.value)
                facts.sourceResolveTarget?.let { text("operation.$index.source-resolve", it.value) }
                facts.resolveTarget?.let { text("operation.$index.resolve", it.value) }
                facts.sourceDepthStencil?.let { text("operation.$index.source-depth", it.value) }
                facts.depthStencil?.let { text("operation.$index.depth", it.value) }
                enum("operation.$index.source-phase", facts.sourcePhase); enum("operation.$index.phase", facts.phase)
                enum("operation.$index.source-load", facts.sourceLoad); enum("operation.$index.load", facts.load)
                enum("operation.$index.source-store", facts.sourceStore); enum("operation.$index.store", facts.store)
                facts.sourceAtomicGroup?.let { text("operation.$index.source-group", it.value) }
                facts.atomicGroup?.let { text("operation.$index.group", it.value) }
                int("operation.$index.source-command", facts.sourceCommandIndexI32); int("operation.$index.command", facts.commandIndexI32)
                facts.copySourceDomainTargetLocalI32().encodeRecipeRectI32("operation.$index.source-domain") { name, value -> int(name, value) }
                facts.copyDomainTargetLocalI32().encodeRecipeRectI32("operation.$index.domain") { name, value -> int(name, value) }
                facts.copySourceScissorI32().encodeRecipeRectI32("operation.$index.source-scissor") { name, value -> int(name, value) }
                facts.copyScissorI32().encodeRecipeRectI32("operation.$index.scissor") { name, value -> int(name, value) }
                facts.copySourceExtentI32().let { extent -> int("operation.$index.source-extent.width", extent.width); int("operation.$index.source-extent.height", extent.height) }
                facts.copyExtentI32().let { extent -> int("operation.$index.extent.width", extent.width); int("operation.$index.extent.height", extent.height) }
                facts.copySourceOriginDeviceI32().let { origin -> int("operation.$index.source-origin.x", origin.x); int("operation.$index.source-origin.y", origin.y) }
                facts.copyOriginDeviceI32().let { origin -> int("operation.$index.origin.x", origin.x); int("operation.$index.origin.y", origin.y) }
            }
            when (operation) {
                is PlanW4eInverseAaCoverageSourceBindingV1.InteriorStencil -> {
                    enum("operation.$index.mode", operation.mode)
                    text("operation.$index.source-geometry-slice", operation.sourceSlice.inverseRecipeIdentity())
                    text("operation.$index.geometry-slice", operation.slice.inverseRecipeIdentity())
                    text("operation.$index.geometry", operation.copyInteriorGeometryF32().inverseRecipeIdentity())
                }
                is PlanW4eInverseAaCoverageSourceBindingV1.ZeroWhiteCover -> {
                    text("operation.$index.source-uniform-slice", operation.sourceSlice.inverseRecipeIdentity())
                    text("operation.$index.uniform-slice", operation.slice.inverseRecipeIdentity())
                }
            }
        }
        binding.resourceRemapping().entries.sortedBy { it.key.value }.forEachIndexed { index, entry ->
            text("remap.$index.source", entry.key.value); text("remap.$index.final", entry.value.value)
        }
        binding.resources().sortedBy { it.id.value }.forEachIndexed { index, row ->
            text("resource.$index.id", row.id.value); enum("resource.$index.role", row.role)
            enum("resource.$index.kind", row.kind); text("resource.$index.format", row.format.toString())
            long("resource.$index.bytes", row.byteSize); int("resource.$index.samples", row.sampleCountI32)
            enum("resource.$index.lifetime", row.lifetime); int("resource.$index.first", row.firstPassIndex)
            int("resource.$index.last", row.lastPassIndexExclusive)
            row.copyExtent()?.let { extent -> int("resource.$index.width", extent.width); int("resource.$index.height", extent.height) }
            row.usages().sortedBy { it.name }.forEachIndexed { useIndex, use -> enum("resource.$index.use.$useIndex", use) }
        }
    }
}

private fun RectI32.encodeRecipeRectI32(prefix: String, encode: (String, Int) -> Unit) {
    encode("$prefix.left", left); encode("$prefix.top", top); encode("$prefix.right", right); encode("$prefix.bottom", bottom)
}

private fun PathFillGeometryF32.inverseRecipeIdentity(): String = buildString {
    append(fillRule).append(':').append(attemptedEdgeCountI32).append(':').append(emittedNonZeroClosedEdgeCountI32).append(':')
    copyConservativeScissorI32().let { rect -> append(rect.left).append(':').append(rect.top).append(':').append(rect.right).append(':').append(rect.bottom).append(':') }
    copyDirectTriangleF32OrNull()?.let { direct ->
        append("direct:").append(direct.copyVerticesF32().joinToString(",") { it.toRawBits().toString() }).append(':')
        append(direct.copyIndicesI32().joinToString(",")).append(':')
    }
    copyStencilEdgeFanF32OrNull()?.let { fan ->
        append("fan:").append(fan.copyVerticesF32().joinToString(",") { it.toRawBits().toString() }).append(':')
        append(fan.copyIndicesI32().joinToString(",")).append(':')
        append(fan.copyContourStartsI32().joinToString(",")).append(':')
    }
}

private fun PlanW4eInverseAaCoverageSourceBindingV1.payloadIdentity(): String = payload().inverseRecipeIdentity()

private fun PlanW4eInverseAaCoverageSourceBindingV1.sourcePayloadIdentity(): String = sourcePayload().inverseRecipeIdentity()

private fun W4eNativePayloadPlan.inverseRecipeIdentity(): String =
    buildString {
        append(vertexResourceId.value).append(':').append(indexResourceId.value).append(':')
            .append(uniformResourceId.value).append(':').append(vertexUsefulBytes).append(':')
            .append(indexUsefulBytes).append(':').append(uniformUsefulBytes).append(':')
            .append(vertexCapacityBytes).append(':').append(indexCapacityBytes).append(':')
            .append(uniformCapacityBytes).append(':')
        append(copyVertexData().joinToString(",")).append(':')
        append(copyIndexData().joinToString(",")).append(':')
        append(copyUniformData().joinToString(",") { byte -> (byte.toInt() and 0xff).toString() }).append(':')
        geometrySlices.forEach { slice -> append(slice.passId).append(':').append(slice.purpose).append(':')
            .append(slice.firstIndex).append(':').append(slice.indexCount).append(':').append(slice.baseVertex).append(':')
            .append(slice.vertexCount).append(':').append(slice.maxLocalIndex).append(':') }
        uniformSlices.forEach { slice -> append(slice.passId).append(':').append(slice.purpose).append(':')
            .append(slice.offsetBytes).append(':').append(slice.byteSize).append(':') }
    }

private fun W4eNativeGeometrySlice.inverseRecipeIdentity(): String =
    "$passId:$purpose:$firstIndex:$indexCount:$baseVertex:$vertexCount:$maxLocalIndex"

private fun W4eNativeUniformSlice.inverseRecipeIdentity(): String =
    "$passId:$purpose:$offsetBytes:$byteSize"

internal fun PathRenderDraw.inverseDomainClipOrNullV1(): ClipPlanStrategy.InverseDomain? = when (this) {
    is ClippedGeneralPathDraw -> clip as? ClipPlanStrategy.InverseDomain
    is ClippedBinaryMaskedPathDraw -> clip as? ClipPlanStrategy.InverseDomain
    is GeneralPathDraw, is BinaryMaskedPathDraw -> null
}
