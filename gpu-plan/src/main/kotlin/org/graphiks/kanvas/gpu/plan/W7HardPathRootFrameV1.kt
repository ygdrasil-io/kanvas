package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.CanonicalId
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.math.geometry.SizeI32

internal enum class W7HardPathSlotKindV1 { Path, DrawColor, NoOp }

internal class W7HardPathSlotV1(
    val commandIndexI32: Int,
    val commandFingerprint: CanonicalId,
    val kind: W7HardPathSlotKindV1,
    val path: W4cSealedDrawV1? = null,
    val color: SolidRectDraw? = null,
    val attemptedEdgesBeforeI32: Int,
    val attemptedEdgesAfterI32: Int,
    val pathSourceOccurrenceI32: Int? = null,
) {
    init {
        require(commandIndexI32 >= 0 && attemptedEdgesBeforeI32 >= 0 && attemptedEdgesAfterI32 >= attemptedEdgesBeforeI32)
        require(when (kind) {
            W7HardPathSlotKindV1.Path -> path != null && color == null && pathSourceOccurrenceI32 != null
            W7HardPathSlotKindV1.DrawColor -> path == null && color != null && pathSourceOccurrenceI32 == null
            W7HardPathSlotKindV1.NoOp -> path == null && color == null && pathSourceOccurrenceI32 == null
        })
    }
}

/** Issuer-bound facts from one preparation of the unmodified original scene. */
internal class W7HardPathRootFrameV1 internal constructor(
    private val admission: W4cOriginalFrameAdmissionV1,
    slots: List<W7HardPathSlotV1>,
    val materialPlanTable: MaterialPlanTable,
    val sourceTable: MaterialSourceConstructionTableV4,
    val totalAttemptedEdgesI32: Int,
) {
    val scene: SceneSnapshot = admission.scene
    val target: RenderTargetDescriptor = admission.target
    val sceneFingerprint: CanonicalId = scene.canonicalId
    val targetFingerprint: CanonicalId = target.canonicalId
    internal val owner: W4cPathFillPlanCompiler get() = admission.owner
    private val commandFingerprints: List<CanonicalId> = immutableList(scene.map { it.canonicalId })
    private val preparedSlots: List<W7HardPathSlotV1> = immutableList(slots)
    private val publicationIssuerToken: Any = Any()

    init {
        require(admission.mode == W4cOriginalFrameModeV1.HardPathRoot)
        require(totalAttemptedEdgesI32 >= 0)
        require(preparedSlots.zipWithNext().all { (before, after) -> before.commandIndexI32 < after.commandIndexI32 })
        require(preparedSlots.all { slot ->
            commandFingerprints.getOrNull(slot.commandIndexI32) == slot.commandFingerprint
        })
        require(preparedSlots.count { it.kind == W7HardPathSlotKindV1.DrawColor } > 0)
        require(preparedSlots.count { it.kind == W7HardPathSlotKindV1.Path } > 0)
    }

    internal fun authenticates(owner: W4cPathFillPlanCompiler): Boolean =
        admission.owner === owner && owner.authenticates(admission) &&
            admission.scene === scene && admission.target.canonicalId == targetFingerprint

    internal fun commandFingerprints(): List<CanonicalId> = commandFingerprints
    internal fun slots(): List<W7HardPathSlotV1> = preparedSlots
    internal fun pathSlots(): List<W7HardPathSlotV1> = preparedSlots.filter { it.kind == W7HardPathSlotKindV1.Path }
    internal fun colorSlots(): List<W7HardPathSlotV1> = preparedSlots.filter { it.kind == W7HardPathSlotKindV1.DrawColor }

    internal fun issuePublication(
        id: PlanId,
        capabilityId: String,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
        resources: List<PlanResource>,
        passes: List<PlanPass>,
        dependencies: List<PlanPassDependency>,
        peakFrameLocalBytes: Long,
        visualCommandCount: Int,
        pathPartition: PhysicalPartitionFacts,
        colorPartition: PhysicalPartitionFacts,
        sourceOccurrenceRefs: List<MaterialPlanRef>,
    ): Publication {
        check(authenticates(owner))
        require(pathPartition.kind == PartitionKind.Path && colorPartition.kind == PartitionKind.DrawColor)
        require(sourceOccurrenceRefs.size == pathSlots().size)
        return Publication(
            frame = this,
            issuerToken = publicationIssuerToken,
            id = id,
            capabilityId = capabilityId,
            capabilities = capabilities,
            budget = budget,
            targetExtent = SizeI32(target.extent.width, target.extent.height),
            resources = resources,
            passes = passes,
            dependencies = dependencies,
            peakFrameLocalBytes = peakFrameLocalBytes,
            visualCommandCount = visualCommandCount,
            pathPartition = pathPartition,
            colorPartition = colorPartition,
            sourceOccurrenceRefs = sourceOccurrenceRefs,
        )
    }

    internal fun authenticatesPublicationIssuer(token: Any): Boolean = token === publicationIssuerToken

    public enum class PartitionKind { Path, DrawColor }

    public class CommandSlot internal constructor(
        public val originalCommandIndexI32: Int,
        public val commandFingerprint: CanonicalId,
        public val kind: SlotKind,
        public val draw: PlanDraw?,
        public val attemptedEdgesBeforeI32: Int,
        public val attemptedEdgesAfterI32: Int,
        public val pathSourceOccurrenceI32: Int?,
        public val materialRef: MaterialPlanRef?,
    )

    public enum class SlotKind { Path, DrawColor, NoOp }

    public class PhysicalPartition internal constructor(
        public val kind: PartitionKind,
        commandIndicesI32: List<Int>,
        resourceIds: List<PlanResourceId>,
        public val drawDataResources: PlanDrawDataResources,
        public val depthStencil: PlanResourceId?,
    ) {
        private val commandIndices = immutableList(commandIndicesI32)
        private val storedResourceIds = immutableList(resourceIds)
        public fun commandIndicesI32(): List<Int> = commandIndices
        public fun resourceIds(): List<PlanResourceId> = storedResourceIds
    }

    /** Immutable V1 source-budget and original-chronology publication proof. */
    public class Publication internal constructor(
        private val frame: W7HardPathRootFrameV1,
        issuerToken: Any,
        private val id: PlanId,
        private val capabilityId: String,
        private val capabilities: PlanCapabilitySnapshot,
        private val budget: PlanBudget,
        targetExtent: SizeI32,
        resources: List<PlanResource>,
        passes: List<PlanPass>,
        dependencies: List<PlanPassDependency>,
        private val peakFrameLocalBytes: Long,
        private val visualCommandCount: Int,
        pathPartition: PhysicalPartitionFacts,
        colorPartition: PhysicalPartitionFacts,
        sourceOccurrenceRefs: List<MaterialPlanRef>,
    ) {
        private val issuerAuthenticated: Boolean = frame.authenticatesPublicationIssuer(issuerToken)
        private val storedTargetExtent = targetExtent.copy()
        private val storedResources = immutableList(resources)
        private val storedPasses = immutableList(passes)
        private val storedDependencies = immutableList(dependencies)
        private val storedPathPartition = pathPartition.toPublic()
        private val storedColorPartition = colorPartition.toPublic()
        private val storedSourceOccurrenceRefs = immutableList(sourceOccurrenceRefs)
        private val commandSlots: List<CommandSlot> = immutableList(frame.slots().map { slot ->
            val draw = when (slot.kind) {
                W7HardPathSlotKindV1.Path -> pathDrawByCommand(slot.commandIndexI32)
                W7HardPathSlotKindV1.DrawColor -> colorDrawByCommand(slot.commandIndexI32)
                W7HardPathSlotKindV1.NoOp -> null
            }
            val materialRef = (draw?.materialAuthority as? PlanDrawMaterialAuthority.MaterialV1)?.ref
            CommandSlot(
                slot.commandIndexI32,
                slot.commandFingerprint,
                when (slot.kind) {
                    W7HardPathSlotKindV1.Path -> SlotKind.Path
                    W7HardPathSlotKindV1.DrawColor -> SlotKind.DrawColor
                    W7HardPathSlotKindV1.NoOp -> SlotKind.NoOp
                },
                draw,
                slot.attemptedEdgesBeforeI32,
                slot.attemptedEdgesAfterI32,
                slot.pathSourceOccurrenceI32,
                materialRef,
            )
        })
        private val sealToken = Any()

        init {
            require(issuerAuthenticated)
            require(capabilityId == W4cPathFillPlanCompiler.W7_HARD_PATH_ROOT_CAPABILITY_ID)
            require(storedPathPartition.kind == PartitionKind.Path && storedColorPartition.kind == PartitionKind.DrawColor)
            require(frame.authenticates(frame.owner))
            require(storedSourceOccurrenceRefs.size == frame.pathSlots().size)
            require(storedSourceOccurrenceRefs == frame.pathSlots().map { requireNotNull(it.path).material })
            require(storedPathPartition.commandIndicesI32() == frame.pathSlots().map { it.commandIndexI32 })
            require(storedColorPartition.commandIndicesI32() == frame.colorSlots().map { it.commandIndexI32 })
            val resourceIds = storedResources.map { it.id }.toSet()
            val resourcesById = storedResources.associateBy { it.id }
            require(storedPathPartition.resourceIds().all { it in resourceIds } &&
                storedColorPartition.resourceIds().all { it in resourceIds })
            require(storedPathPartition.resourceIds().map { resourcesById.getValue(it).role }.toSet() ==
                setOf(PlanResourceRole.VertexData, PlanResourceRole.IndexData, PlanResourceRole.UniformData) +
                    if (storedPathPartition.depthStencil == null) emptySet() else setOf(PlanResourceRole.DepthStencil))
            require(storedColorPartition.resourceIds().map { resourcesById.getValue(it).role }.toSet() ==
                setOf(PlanResourceRole.VertexData, PlanResourceRole.IndexData, PlanResourceRole.UniformData))
            require(storedPathPartition.drawDataResources.let {
                it.vertex in storedPathPartition.resourceIds() && it.index in storedPathPartition.resourceIds() &&
                    it.uniform in storedPathPartition.resourceIds()
            } && storedColorPartition.drawDataResources.let {
                it.vertex in storedColorPartition.resourceIds() && it.index in storedColorPartition.resourceIds() &&
                    it.uniform in storedColorPartition.resourceIds()
            })
            require(storedPathPartition.depthStencil == null ||
                storedPathPartition.depthStencil in storedPathPartition.resourceIds())
            require(visualCommandCount == commandSlots.count { it.draw != null })
            require(commandSlots.filter { it.kind != SlotKind.NoOp }.all { it.draw != null })
            require(commandSlots.filter { it.kind == SlotKind.NoOp }.all { it.draw == null })
            require(commandSlots.filter { it.kind == SlotKind.Path }.all {
                it.draw?.materialAuthority is PlanDrawMaterialAuthority.MaterialV1 && it.materialRef != null
            })
            require(commandSlots.filter { it.kind == SlotKind.DrawColor }.all {
                it.draw?.materialAuthority is PlanDrawMaterialAuthority.LegacyColorV1 && it.materialRef == null
            })
            require(commandSlots.indices.all { index ->
                val slot = frame.slots()[index]
                val published = commandSlots[index]
                published.originalCommandIndexI32 == slot.commandIndexI32 &&
                    published.commandFingerprint == slot.commandFingerprint &&
                    published.attemptedEdgesBeforeI32 == slot.attemptedEdgesBeforeI32 &&
                    published.attemptedEdgesAfterI32 == slot.attemptedEdgesAfterI32 &&
                    published.pathSourceOccurrenceI32 == slot.pathSourceOccurrenceI32 &&
                    (published.draw == null || published.draw.commandIndex == slot.commandIndexI32)
            })
            require(frame.slots().zipWithNext().all { (before, after) ->
                before.attemptedEdgesAfterI32 == after.attemptedEdgesBeforeI32
            } && (frame.slots().lastOrNull()?.attemptedEdgesAfterI32 ?: 0) == frame.totalAttemptedEdgesI32)
            require(frame.pathSlots().mapNotNull { it.pathSourceOccurrenceI32 } == frame.pathSlots().indices.toList())
            val chronologicalDraws = storedPasses.flatMap { pass -> pass.drawsForHardPathPublication() }
            val retainedDraws = commandSlots.filter { it.draw != null }.map { it.draw }
            require(chronologicalDraws.size == retainedDraws.size &&
                chronologicalDraws.indices.all { chronologicalDraws[it] === retainedDraws[it] })
            require(storedDependencies.size == (storedPasses.size - 1).coerceAtLeast(0) &&
                storedDependencies.indices.all { index ->
                    storedDependencies[index].before == storedPasses[index].id &&
                        storedDependencies[index].after == storedPasses[index + 1].id
                })
        }

        public fun commandSlots(): List<CommandSlot> = commandSlots
        public fun physicalPartitions(): List<PhysicalPartition> = listOf(storedPathPartition, storedColorPartition)
        public fun authenticates(graph: RenderGraph): Boolean =
            graph.hardPathRootPublicationInternal() === this &&
                graph.hardPathRootSealTokenInternal() === sealToken && graphFactsMatch(
                    graph.id,
                    graph.capabilityId,
                    graph.targetExtent,
                    graph.capabilities,
                    graph.budget,
                    graph.visualCommandCount,
                    graph.resources(),
                    graph.passes(),
                    graph.dependencies(),
                    graph.peakFrameLocalBytes,
                    graph.materialPlanTableOrNull(),
                )

        internal fun authenticatesConstruction(
            candidateId: PlanId,
            candidateCapabilityId: String,
            candidateTargetExtent: SizeI32,
            candidateCapabilities: PlanCapabilitySnapshot,
            candidateBudget: PlanBudget,
            candidateVisualCommandCount: Int,
            candidateResources: List<PlanResource>,
            candidatePasses: List<PlanPass>,
            candidateDependencies: List<PlanPassDependency>,
            candidatePeak: Long,
            candidateMaterialTable: MaterialPlanTable?,
        ): Boolean = issuerAuthenticated && frame.authenticates(frame.owner) &&
            candidateMaterialTable === frame.materialPlanTable && graphFactsMatch(
                candidateId, candidateCapabilityId, candidateTargetExtent, candidateCapabilities, candidateBudget,
                candidateVisualCommandCount, candidateResources, candidatePasses, candidateDependencies,
                candidatePeak, candidateMaterialTable,
            )

        internal fun sealToken(): Any = sealToken
        internal fun frameIsAuthentic(): Boolean = issuerAuthenticated && frame.authenticates(frame.owner)

        private fun graphFactsMatch(
            candidateId: PlanId,
            candidateCapabilityId: String,
            candidateTargetExtent: SizeI32,
            candidateCapabilities: PlanCapabilitySnapshot,
            candidateBudget: PlanBudget,
            candidateVisualCommandCount: Int,
            candidateResources: List<PlanResource>,
            candidatePasses: List<PlanPass>,
            candidateDependencies: List<PlanPassDependency>,
            candidatePeak: Long,
            candidateMaterialTable: MaterialPlanTable?,
        ): Boolean =
            candidateId == id && candidateCapabilityId == capabilityId &&
                candidateTargetExtent == storedTargetExtent && candidateCapabilities == capabilities &&
                candidateBudget == budget && candidateVisualCommandCount == visualCommandCount &&
                candidatePeak == peakFrameLocalBytes && candidateMaterialTable === frame.materialPlanTable &&
                sameIdentity(storedResources, candidateResources) && sameIdentity(storedPasses, candidatePasses) &&
                sameIdentity(storedDependencies, candidateDependencies) &&
                frame.commandFingerprints().size == frame.scene.size &&
                frame.scene.canonicalId == frame.sceneFingerprint && frame.target.canonicalId == frame.targetFingerprint

        private fun pathDrawByCommand(commandIndexI32: Int): PlanDraw? =
            storedPasses.asSequence().flatMap { pass -> pass.drawsForHardPathPublication() }
                .firstOrNull { it.commandIndex == commandIndexI32 &&
                    it.materialAuthority is PlanDrawMaterialAuthority.MaterialV1 }

        private fun colorDrawByCommand(commandIndexI32: Int): PlanDraw? =
            storedPasses.asSequence().flatMap { pass -> pass.drawsForHardPathPublication() }
                .firstOrNull { it.commandIndex == commandIndexI32 && it.materialAuthority is PlanDrawMaterialAuthority.LegacyColorV1 }

        private fun sameIdentity(expected: List<*>, actual: List<*>): Boolean =
            expected.size == actual.size && expected.indices.all { expected[it] === actual[it] }
    }
}

internal class PhysicalPartitionFacts(
    val kind: W7HardPathRootFrameV1.PartitionKind,
    val commandIndicesI32: List<Int>,
    val resourceIds: List<PlanResourceId>,
    val drawDataResources: PlanDrawDataResources,
    val depthStencil: PlanResourceId?,
) {
    fun toPublic(): W7HardPathRootFrameV1.PhysicalPartition = W7HardPathRootFrameV1.PhysicalPartition(
        kind, commandIndicesI32, resourceIds, drawDataResources, depthStencil,
    )
}

private fun PlanPass.drawsForHardPathPublication(): List<PlanDraw> = when (this) {
    is PlanPass.RenderPass -> draws()
    is PlanPass.StencilCover -> listOf(draw)
    else -> emptyList()
}
