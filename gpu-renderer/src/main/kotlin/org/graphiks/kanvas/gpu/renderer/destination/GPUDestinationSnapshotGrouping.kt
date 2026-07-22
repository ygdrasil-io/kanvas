package org.graphiks.kanvas.gpu.renderer.destination

import java.math.BigInteger
import java.util.Locale
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateIdentity
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity

/** Exact handle-free identity for destination snapshots that may be shared. */
data class GPUDestinationSnapshotGroupKey(
    val target: GPUTargetIdentity,
    val targetGeneration: Long,
    val deviceGeneration: GPUDeviceGenerationID,
    val format: GPUColorFormat,
    val colorInterpretation: GPUColorInterpretation,
    val sampleContinuation: GPUSampleContinuationKey?,
    val sourceIntermediate: GPUIntermediateIdentity?,
) {
    init {
        require(targetGeneration >= 0L) {
            "GPUDestinationSnapshotGroupKey.targetGeneration must be non-negative"
        }
    }
}

/** Floating-point draw footprint before conservative destination-copy normalization. */
data class GPUDestinationReadFootprint(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
    val aaOutsetPixels: Double,
    val filterOutsetPixels: Double,
    val alignmentPixels: Int,
) {
    init {
        require(aaOutsetPixels >= 0.0) {
            "GPUDestinationReadFootprint.aaOutsetPixels must be non-negative"
        }
        require(filterOutsetPixels >= 0.0) {
            "GPUDestinationReadFootprint.filterOutsetPixels must be non-negative"
        }
        require(alignmentPixels > 0) {
            "GPUDestinationReadFootprint.alignmentPixels must be positive"
        }
    }

    internal val finite: Boolean
        get() = left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
            aaOutsetPixels.isFinite() && filterOutsetPixels.isFinite()
}

/** Future snapshot materialization source, independent of the canonical target selected by blend strategy. */
enum class GPUDestinationSnapshotMaterializationSourceKind {
    CanonicalSceneTarget,
    CanonicalLayerTarget,
    ExternalTexturableIntermediate,
}

/**
 * One ordered target write and optional semantic destination-read request.
 *
 * [strategyGatePlan] proves which canonical target authorized `CopyTarget`; the
 * `materializationSource*` fields independently describe how that future snapshot is populated.
 */
data class GPUTargetAccess(
    val commandId: String,
    val requirement: GPUBlendDestinationReadRequirement,
    val strategyGatePlan: GPUDestinationReadStrategyGatePlan?,
    val key: GPUDestinationSnapshotGroupKey,
    val drawBounds: GPUDestinationReadFootprint,
    val clipBounds: GPUPixelBounds,
    val targetBounds: GPUPixelBounds,
    val layerId: String,
    val filterId: String?,
    val bytesPerPixel: Int = 4,
    val materializationSourceKind: GPUDestinationSnapshotMaterializationSourceKind =
        GPUDestinationSnapshotMaterializationSourceKind.CanonicalSceneTarget,
    val materializationSourceUsageLabels: Set<String> = setOf("render_attachment", "copy_src"),
) {
    internal val materializationSourceUsageLabelsSnapshot: Set<String> =
        materializationSourceUsageLabels.toSet()

    init {
        require(commandId.isNotBlank()) { "GPUTargetAccess.commandId must not be blank" }
        require(layerId.isNotBlank()) { "GPUTargetAccess.layerId must not be blank" }
        require(filterId == null || filterId.isNotBlank()) {
            "GPUTargetAccess.filterId must be null or non-blank"
        }
        require(bytesPerPixel > 0) { "GPUTargetAccess.bytesPerPixel must be positive" }
        require(materializationSourceUsageLabels.none(String::isBlank)) {
            "GPUTargetAccess.materializationSourceUsageLabels must not contain blanks"
        }
        if (requirement == GPUBlendDestinationReadRequirement.None) {
            require(strategyGatePlan == null) {
                "direct target writes must not carry a destination-read strategy selection"
            }
        } else {
            requireNotNull(strategyGatePlan) {
                "destination-reading target accesses require a strategy planner selection"
            }
            require(strategyGatePlan.plan.requirement == requirement) {
                "destination-read strategy selection requirement must match the target access"
            }
            if (strategyGatePlan.plan.strategy == GPUDestinationReadStrategy.CopyTarget) {
                val provenance = requireNotNull(strategyGatePlan.copyTargetProvenance)
                require(provenance.commandId == commandId) {
                    "CopyTarget canonical target provenance must belong to the target access command"
                }
                require(provenance.canonicalTarget == key.target) {
                    "CopyTarget canonical target provenance must match the snapshot group target"
                }
                require(provenance.canonicalTargetGeneration == key.targetGeneration) {
                    "CopyTarget canonical target provenance must match the snapshot group generation"
                }
                require(provenance.canonicalTargetFormat == key.format) {
                    "CopyTarget canonical target provenance must match the snapshot group format"
                }
            }
        }
        if (materializationSourceKind ==
            GPUDestinationSnapshotMaterializationSourceKind.ExternalTexturableIntermediate
        ) {
            require("texture_binding" in materializationSourceUsageLabelsSnapshot) {
                "external destination snapshot sources must be texturable"
            }
            require(key.sourceIntermediate != null) {
                "external destination snapshot sources require an exact intermediate identity"
            }
        } else {
            require("copy_src" in materializationSourceUsageLabelsSnapshot) {
                "canonical scene and layer targets must include copy_src"
            }
        }
    }
}

/** One destination-reading draw mapped into a snapshot group. */
data class GPUDestinationReadMember(
    val commandId: String,
    val accessIndex: Int,
    val logicalBounds: GPUPixelBounds,
)

/** One deterministic bounded copy and its destination-reading members. */
data class GPUDestinationSnapshotGroup(
    val key: GPUDestinationSnapshotGroupKey,
    val logicalBounds: GPUPixelBounds,
    val members: List<GPUDestinationReadMember>,
    val copiedBytes: Long,
    val decisionDump: List<String>,
)

/** Versioned measured constants that may authorize cross-draw sharing. */
data class SnapshotGroupingCalibration(
    val version: String,
    val copyCostPerByte: Double,
    val passBreakCost: Double,
    val scratchCostPerByte: Double,
) {
    init {
        require(version.isNotBlank()) { "SnapshotGroupingCalibration.version must not be blank" }
        require(copyCostPerByte.isFinite() && copyCostPerByte >= 0.0) {
            "SnapshotGroupingCalibration.copyCostPerByte must be finite and non-negative"
        }
        require(passBreakCost.isFinite() && passBreakCost >= 0.0) {
            "SnapshotGroupingCalibration.passBreakCost must be finite and non-negative"
        }
        require(scratchCostPerByte.isFinite() && scratchCostPerByte >= 0.0) {
            "SnapshotGroupingCalibration.scratchCostPerByte must be finite and non-negative"
        }
    }
}

/** Checked frame budget plus optional versioned grouping calibration. */
data class SnapshotGroupingCostModel(
    val frameMemoryBudgetRequest: GPUFrameMemoryBudgetRequest,
    val calibration: SnapshotGroupingCalibration?,
) {
    internal val frameMemoryBudgetRequestSnapshot: GPUFrameMemoryBudgetRequest =
        frameMemoryBudgetRequest.copy(allocations = frameMemoryBudgetRequest.allocations.toList())

    init {
        require(frameMemoryBudgetRequest.deviceLimits.copyBytesPerRowAlignment > 0L) {
            "SnapshotGroupingCostModel copyBytesPerRowAlignment must be positive"
        }
    }
}

/** Stable refusal for one destination snapshot member. */
data class GPUDestinationSnapshotRefusal(
    val code: String,
    val commandId: String,
    val facts: Map<String, String>,
)

/** Copy materialization stays separate from destination-read strategy selection. */
sealed interface GPUDestinationSnapshotMaterialization {
    val groupIndex: Int
    val logicalBounds: GPUPixelBounds

    data class TextureCopy(
        override val groupIndex: Int,
        override val logicalBounds: GPUPixelBounds,
    ) : GPUDestinationSnapshotMaterialization
}

/** Future texturable sources without CopySrc may draw into the snapshot; this is not a strategy. */
data class CopyAsDrawMaterialization(
    override val groupIndex: Int,
    override val logicalBounds: GPUPixelBounds,
    val sourceIntermediate: GPUIntermediateIdentity,
) : GPUDestinationSnapshotMaterialization

/** Pure grouping product consumed later by frame/resource materialization. */
data class GPUDestinationSnapshotGroupingResult(
    val groups: List<GPUDestinationSnapshotGroup>,
    val materializations: List<GPUDestinationSnapshotMaterialization>,
    val totalCopiedBytes: Long,
    val refusals: List<GPUDestinationSnapshotRefusal>,
    val decisionDump: List<String>,
)

/** Pure ordered destination-snapshot grouping with no GPU handles or allocation identity. */
class GPUDestinationSnapshotGrouper(
    private val costModel: SnapshotGroupingCostModel,
) {
    fun group(orderedAccesses: List<GPUTargetAccess>): GPUDestinationSnapshotGroupingResult {
        val accessSnapshot = orderedAccesses.toList()
        val mutableGroups = mutableListOf<MutableSnapshotGroup>()
        val refusals = mutableListOf<GPUDestinationSnapshotRefusal>()
        val decisions = mutableListOf(
                "destination-snapshot:policy calibration=${costModel.calibration?.version ?: "missing"} " +
                "crossDrawSharing=${costModel.calibration != null} " +
                "frameBudgetBytes=${costModel.frameMemoryBudgetRequestSnapshot.configuredAggregateBudgetBytes} " +
                "copyRowAlignmentBytes=${costModel.copyRowAlignmentBytes}",
        )
        var aggregateBytes = BigInteger.ZERO

        accessSnapshot.forEachIndexed { accessIndex, access ->
            if (access.requirement == GPUBlendDestinationReadRequirement.None) {
                return@forEachIndexed
            }
            val selectedStrategy = requireNotNull(access.strategyGatePlan).plan.strategy
            if (selectedStrategy != GPUDestinationReadStrategy.CopyTarget) {
                decisions += "destination-snapshot:member command=${access.commandId} decision=skip " +
                    "selectedStrategy=${selectedStrategy.name}"
                return@forEachIndexed
            }
            val provenance = requireNotNull(access.strategyGatePlan.copyTargetProvenance)
            decisions += "destination-snapshot:member command=${access.commandId} " +
                "selectedStrategy=CopyTarget selectedCommand=${provenance.commandId} " +
                "selectedTarget=${provenance.canonicalTarget.value} " +
                "selectedTargetGeneration=${provenance.canonicalTargetGeneration} " +
                "selectedTargetUsage=${provenance.canonicalTargetUsageLabel} " +
                "selectedTargetFormat=${provenance.canonicalTargetFormat.value} " +
                "materializationSource=${access.materializationSourceKind.name} " +
                "materializationSourceUsage=" +
                access.materializationSourceUsageLabelsSnapshot.sorted().joinToString(",")

            val bounds = access.normalizedBounds()
            if (bounds is BoundsResult.Refused) {
                refusals += refusal(bounds.code, access, bounds.facts)
                decisions += "destination-snapshot:member command=${access.commandId} decision=refuse " +
                    "reason=${bounds.code}"
                return@forEachIndexed
            }
            val logicalBounds = (bounds as BoundsResult.Accepted).bounds
            if (logicalBounds.isEmpty) {
                decisions += "destination-snapshot:member command=${access.commandId} decision=skip-empty " +
                    "bounds=${logicalBounds.dumpLabel()}"
                return@forEachIndexed
            }

            val memberBytes = logicalBounds.checkedAlignedBytesOrNull(
                bytesPerPixel = access.bytesPerPixel,
                rowAlignmentBytes = costModel.copyRowAlignmentBytes,
            )
            if (memberBytes == null) {
                val code = "unsupported.destination_snapshot.byte_accounting_overflow"
                refusals += refusal(code, access, mapOf("bounds" to logicalBounds.dumpLabel()))
                decisions += "destination-snapshot:member command=${access.commandId} decision=refuse reason=$code"
                return@forEachIndexed
            }

            val member = GPUDestinationReadMember(access.commandId, accessIndex, logicalBounds)
            val lastGroup = mutableGroups.lastOrNull()
            val separation = lastGroup?.separationReason(access, accessIndex, logicalBounds, accessSnapshot)
            val union = if (lastGroup != null && separation == null && costModel.calibration != null) {
                lastGroup.unionCandidate(access, member, costModel)
            } else {
                null
            }
            var unionBudgetRefusalCode: String? = null

            if (union != null && union.accepted) {
                val sharingGroup = requireNotNull(lastGroup)
                val prospectiveGroup = sharingGroup.group.copy(
                    logicalBounds = union.logicalBounds,
                    members = sharingGroup.group.members + member,
                    copiedBytes = union.copiedBytes,
                )
                val budgetDiagnostic = costModel.budgetDiagnostic(
                    mutableGroups.dropLast(1).map(MutableSnapshotGroup::group) + prospectiveGroup,
                )
                if (budgetDiagnostic == null) {
                    val candidateAggregate = aggregateBytes - sharingGroup.group.copiedBytes.toBigInteger() +
                        union.copiedBytes.toBigInteger()
                    aggregateBytes = candidateAggregate
                    sharingGroup.merge(member, union, accessIndex)
                    decisions += union.dumpLine(access.commandId, "share")
                    return@forEachIndexed
                }
                unionBudgetRefusalCode = budgetDiagnostic.code.value
            }

            if (lastGroup != null) {
                decisions += when {
                    unionBudgetRefusalCode != null ->
                        "destination-snapshot:group command=${access.commandId} decision=separate " +
                            "reason=union-budget diagnostic=$unionBudgetRefusalCode"
                    separation != null ->
                        "destination-snapshot:group command=${access.commandId} decision=separate $separation"
                    union != null -> union.dumpLine(access.commandId, "separate")
                    costModel.calibration == null ->
                        "destination-snapshot:group command=${access.commandId} decision=separate " +
                            "reason=uncalibrated-no-sharing"
                    else ->
                        "destination-snapshot:group command=${access.commandId} decision=separate " +
                            "reason=no-compatible-predecessor"
                }
            }

            val newGroup = MutableSnapshotGroup(
                group = GPUDestinationSnapshotGroup(
                    key = access.key,
                    logicalBounds = logicalBounds,
                    members = listOf(member),
                    copiedBytes = memberBytes,
                    decisionDump = listOf(
                        "destination-snapshot:group bounds=${logicalBounds.dumpLabel()} " +
                            "members=${access.commandId} copiedBytes=$memberBytes",
                    ),
                ),
                layerId = access.layerId,
                filterId = access.filterId,
                bytesPerPixel = access.bytesPerPixel,
                firstAccessIndex = accessIndex,
                lastAccessIndex = accessIndex,
                materializationSourceKind = access.materializationSourceKind,
                materializationSourceUsageLabels = access.materializationSourceUsageLabelsSnapshot,
            )
            val budgetDiagnostic = costModel.budgetDiagnostic(
                mutableGroups.map(MutableSnapshotGroup::group) + newGroup.group,
            )
            if (budgetDiagnostic != null) {
                addBudgetRefusal(access, budgetDiagnostic.code.value, budgetDiagnostic.facts, refusals, decisions)
                return@forEachIndexed
            }
            aggregateBytes += memberBytes.toBigInteger()
            mutableGroups += newGroup
        }

        val groups = mutableGroups.map(MutableSnapshotGroup::group)
        val materializations = mutableGroups.mapIndexed { index, mutableGroup ->
            mutableGroup.materialization(index)
        }
        val totalCopiedBytes = aggregateBytes.longValueExact()
        val groupDump = groups.flatMapIndexed { index, group -> group.dumpLines(index) }
        val refusalDump = refusals.map { refusal ->
            "destination-snapshot:refused command=${refusal.commandId} reason=${refusal.code} " +
                "facts=${refusal.facts.toSortedMap().entries.joinToString(";") { (key, value) -> "$key=$value" }}"
        }
        return GPUDestinationSnapshotGroupingResult(
            groups = groups,
            materializations = materializations,
            totalCopiedBytes = totalCopiedBytes,
            refusals = refusals.toList(),
            decisionDump = (decisions + groupDump + refusalDump).toList(),
        )
    }

    private fun addBudgetRefusal(
        access: GPUTargetAccess,
        code: String,
        facts: Map<String, String>,
        refusals: MutableList<GPUDestinationSnapshotRefusal>,
        decisions: MutableList<String>,
    ) {
        refusals += refusal(
            code = code,
            access = access,
            facts = facts,
        )
        decisions += "destination-snapshot:member command=${access.commandId} decision=refuse reason=$code " +
            "facts=${facts.toSortedMap().entries.joinToString(";") { (key, value) -> "$key=$value" }}"
    }
}

private sealed interface BoundsResult {
    data class Accepted(val bounds: GPUPixelBounds) : BoundsResult
    data class Refused(val code: String, val facts: Map<String, String>) : BoundsResult
}

private val SnapshotGroupingCostModel.copyRowAlignmentBytes: Long
    get() = frameMemoryBudgetRequestSnapshot.deviceLimits.copyBytesPerRowAlignment

private fun SnapshotGroupingCostModel.budgetDiagnostic(
    groups: List<GPUDestinationSnapshotGroup>,
) = GPUFrameMemoryBudgetPlanner.plan(
    frameMemoryBudgetRequestSnapshot.copy(
        allocations = frameMemoryBudgetRequestSnapshot.allocations + groups.mapIndexed { index, group ->
            GPUFrameMemoryAllocation(
                label = "destination-snapshot:$index",
                category = GPUFrameMemoryCategory.DestinationSnapshot,
                bytes = group.copiedBytes,
                resourceKind = GPUFrameMemoryResourceKind.Texture2D,
                extent = group.logicalBounds,
            )
        },
    ),
).diagnostic

private data class MutableSnapshotGroup(
    var group: GPUDestinationSnapshotGroup,
    val layerId: String,
    val filterId: String?,
    val bytesPerPixel: Int,
    val firstAccessIndex: Int,
    var lastAccessIndex: Int,
    val materializationSourceKind: GPUDestinationSnapshotMaterializationSourceKind,
    val materializationSourceUsageLabels: Set<String>,
) {
    fun separationReason(
        access: GPUTargetAccess,
        accessIndex: Int,
        readBounds: GPUPixelBounds,
        orderedAccesses: List<GPUTargetAccess>,
    ): String? {
        if (group.key != access.key) return "reason=group-key-mismatch"
        if (layerId != access.layerId) return "reason=layer-change"
        if (filterId != access.filterId) return "reason=filter-change"
        if (bytesPerPixel != access.bytesPerPixel) return "reason=bytes-per-pixel-change"
        if (materializationSourceKind != access.materializationSourceKind ||
            materializationSourceUsageLabels != access.materializationSourceUsageLabelsSnapshot
        ) {
            return "reason=source-materialization-change"
        }

        for (index in firstAccessIndex until accessIndex) {
            val earlier = orderedAccesses[index]
            val intervening = index > lastAccessIndex
            if (intervening) {
                if (earlier.key.target != access.key.target) {
                    return "hazard=intervening-target-boundary writer=${earlier.commandId}"
                }
                if (earlier.key.targetGeneration != access.key.targetGeneration) {
                    return "hazard=intervening-generation-boundary writer=${earlier.commandId}"
                }
                if (earlier.key != access.key) {
                    return "hazard=intervening-group-key-boundary writer=${earlier.commandId}"
                }
                if (earlier.layerId != layerId) {
                    return "hazard=intervening-layer-boundary writer=${earlier.commandId}"
                }
                if (earlier.filterId != filterId) {
                    return "hazard=intervening-filter-boundary writer=${earlier.commandId}"
                }
            }
            if (earlier.key.target != access.key.target ||
                earlier.key.targetGeneration != access.key.targetGeneration
            ) {
                continue
            }
            val earlierBounds = earlier.normalizedBounds()
            if (earlierBounds is BoundsResult.Accepted && earlierBounds.bounds.intersects(readBounds)) {
                return "hazard=intersecting-write writer=${earlier.commandId}"
            }
            if (intervening && earlier.requirement == GPUBlendDestinationReadRequirement.None) {
                return "hazard=direct-intervening-draw writer=${earlier.commandId}"
            }
        }
        return null
    }

    fun unionCandidate(
        access: GPUTargetAccess,
        member: GPUDestinationReadMember,
        costModel: SnapshotGroupingCostModel,
    ): UnionCandidate {
        val unionBounds = group.logicalBounds.union(member.logicalBounds)
        val unionBytes = unionBounds.checkedAlignedBytesOrNull(
            bytesPerPixel = access.bytesPerPixel,
            rowAlignmentBytes = costModel.copyRowAlignmentBytes,
        ) ?: return UnionCandidate.refused(
            logicalBounds = unionBounds,
            copiedBytes = Long.MAX_VALUE,
            inflation = Double.POSITIVE_INFINITY,
            reason = "byte-accounting-overflow",
        )
        val memberArea = member.logicalBounds.checkedAreaOrNull()
            ?: return UnionCandidate.refused(unionBounds, unionBytes, Double.POSITIVE_INFINITY, "area-overflow")
        val existingMemberArea = group.members.fold(BigInteger.ZERO) { total, existing ->
            total + existing.logicalBounds.width.toBigInteger() * existing.logicalBounds.height.toBigInteger()
        }
        val exactMemberArea = existingMemberArea + memberArea.toBigInteger()
        val exactUnionArea = unionBounds.width.toBigInteger() * unionBounds.height.toBigInteger()
        val inflation = exactUnionArea.toDouble() / exactMemberArea.toDouble()
        if (!inflation.isFinite() || inflation > MAX_UNION_INFLATION) {
            return UnionCandidate.refused(unionBounds, unionBytes, inflation, "union-inflation")
        }

        val calibration = requireNotNull(costModel.calibration)
        val separateCost = calibration.copyCostPerByte * (group.copiedBytes.toDouble() +
            member.logicalBounds.checkedAlignedBytesOrNull(
                bytesPerPixel = access.bytesPerPixel,
                rowAlignmentBytes = costModel.copyRowAlignmentBytes,
            )!!.toDouble()) +
            calibration.scratchCostPerByte * (group.copiedBytes.toDouble() + unionBytes.toDouble()) +
            calibration.passBreakCost * 2.0
        val unionCost = (calibration.copyCostPerByte + calibration.scratchCostPerByte) *
            unionBytes.toDouble() + calibration.passBreakCost
        if (!separateCost.isFinite() || !unionCost.isFinite()) {
            return UnionCandidate.refused(
                logicalBounds = unionBounds,
                copiedBytes = unionBytes,
                inflation = inflation,
                reason = "calibrated-cost-overflow",
            )
        }
        return UnionCandidate(
            logicalBounds = unionBounds,
            copiedBytes = unionBytes,
            inflation = inflation,
            accepted = unionCost <= separateCost,
            reason = if (unionCost <= separateCost) "calibrated-cost" else "calibrated-cost-regression",
        )
    }

    fun merge(member: GPUDestinationReadMember, union: UnionCandidate, accessIndex: Int) {
        val members = group.members + member
        group = group.copy(
            logicalBounds = union.logicalBounds,
            members = members,
            copiedBytes = union.copiedBytes,
            decisionDump = listOf(
                "destination-snapshot:group bounds=${union.logicalBounds.dumpLabel()} " +
                    "members=${members.joinToString(",", transform = GPUDestinationReadMember::commandId)} " +
                    "copiedBytes=${union.copiedBytes}",
            ),
        )
        lastAccessIndex = accessIndex
    }

    fun materialization(groupIndex: Int): GPUDestinationSnapshotMaterialization =
        if ("copy_src" in materializationSourceUsageLabels) {
            GPUDestinationSnapshotMaterialization.TextureCopy(groupIndex, group.logicalBounds)
        } else {
            CopyAsDrawMaterialization(
                groupIndex = groupIndex,
                logicalBounds = group.logicalBounds,
                sourceIntermediate = requireNotNull(group.key.sourceIntermediate) {
                    "CopyAsDrawMaterialization requires an exact source intermediate identity"
                },
            )
        }
}

private data class UnionCandidate(
    val logicalBounds: GPUPixelBounds,
    val copiedBytes: Long,
    val inflation: Double,
    val accepted: Boolean,
    val reason: String,
) {
    fun dumpLine(commandId: String, decision: String): String =
        "destination-snapshot:group command=$commandId decision=$decision " +
            "unionBounds=${logicalBounds.dumpLabel()} unionInflation=${inflation.stableDecimal()} " +
            "copiedBytes=$copiedBytes reason=$reason"

    companion object {
        fun refused(
            logicalBounds: GPUPixelBounds,
            copiedBytes: Long,
            inflation: Double,
            reason: String,
        ): UnionCandidate = UnionCandidate(
            logicalBounds = logicalBounds,
            copiedBytes = copiedBytes,
            inflation = inflation,
            accepted = false,
            reason = reason,
        )
    }
}

private fun GPUTargetAccess.normalizedBounds(): BoundsResult {
    if (!drawBounds.finite) {
        return BoundsResult.Refused(
            code = "unsupported.destination_snapshot.bounds_non_finite",
            facts = mapOf("command" to commandId),
        )
    }
    if (drawBounds.right < drawBounds.left || drawBounds.bottom < drawBounds.top) {
        return BoundsResult.Refused(
            code = "unsupported.destination_snapshot.bounds_inverted",
            facts = mapOf("command" to commandId),
        )
    }

    val intersectionLeft = maxOf(clipBounds.left, targetBounds.left).toDouble()
    val intersectionTop = maxOf(clipBounds.top, targetBounds.top).toDouble()
    val intersectionRight = minOf(clipBounds.right, targetBounds.right).toDouble()
    val intersectionBottom = minOf(clipBounds.bottom, targetBounds.bottom).toDouble()
    if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) {
        val emptyLeft = maxOf(clipBounds.left, targetBounds.left)
        val emptyTop = maxOf(clipBounds.top, targetBounds.top)
        return BoundsResult.Accepted(GPUPixelBounds(emptyLeft, emptyTop, emptyLeft, emptyTop))
    }

    val outset = drawBounds.aaOutsetPixels + drawBounds.filterOutsetPixels
    val expandedLeft = drawBounds.left - outset
    val expandedTop = drawBounds.top - outset
    val expandedRight = drawBounds.right + outset
    val expandedBottom = drawBounds.bottom + outset
    if (!expandedLeft.isFinite() || !expandedTop.isFinite() ||
        !expandedRight.isFinite() || !expandedBottom.isFinite()
    ) {
        return BoundsResult.Refused(
            code = "unsupported.destination_snapshot.bounds_expansion_overflow",
            facts = mapOf("command" to commandId),
        )
    }
    if (expandedRight <= intersectionLeft || expandedLeft >= intersectionRight ||
        expandedBottom <= intersectionTop || expandedTop >= intersectionBottom
    ) {
        val emptyX = when {
            expandedRight <= intersectionLeft -> intersectionLeft.toInt()
            expandedLeft >= intersectionRight -> intersectionRight.toInt()
            else -> maxOf(intersectionLeft, expandedLeft).toInt()
        }
        val emptyY = when {
            expandedBottom <= intersectionTop -> intersectionTop.toInt()
            expandedTop >= intersectionBottom -> intersectionBottom.toInt()
            else -> maxOf(intersectionTop, expandedTop).toInt()
        }
        return BoundsResult.Accepted(GPUPixelBounds(emptyX, emptyY, emptyX, emptyY))
    }

    val alignment = drawBounds.alignmentPixels.toLong()
    val roundedLeft = expandedLeft.coerceAtLeast(intersectionLeft).let(Math::floor).toLong()
    val roundedTop = expandedTop.coerceAtLeast(intersectionTop).let(Math::floor).toLong()
    val roundedRight = expandedRight.coerceAtMost(intersectionRight).let(Math::ceil).toLong()
    val roundedBottom = expandedBottom.coerceAtMost(intersectionBottom).let(Math::ceil).toLong()
    val alignedLeft = Math.floorDiv(roundedLeft, alignment) * alignment
    val alignedTop = Math.floorDiv(roundedTop, alignment) * alignment
    val alignedRight = alignUpOrNull(roundedRight, alignment)
        ?: return BoundsResult.Refused(
            "unsupported.destination_snapshot.bounds_alignment_overflow",
            mapOf("edge" to "right"),
        )
    val alignedBottom = alignUpOrNull(roundedBottom, alignment)
        ?: return BoundsResult.Refused(
            "unsupported.destination_snapshot.bounds_alignment_overflow",
            mapOf("edge" to "bottom"),
        )

    val left = maxOf(alignedLeft, intersectionLeft.toLong())
    val top = maxOf(alignedTop, intersectionTop.toLong())
    val right = minOf(alignedRight, intersectionRight.toLong()).coerceAtLeast(left)
    val bottom = minOf(alignedBottom, intersectionBottom.toLong()).coerceAtLeast(top)
    if (left !in 0..Int.MAX_VALUE.toLong() || top !in 0..Int.MAX_VALUE.toLong() ||
        right !in 0..Int.MAX_VALUE.toLong() || bottom !in 0..Int.MAX_VALUE.toLong()
    ) {
        return BoundsResult.Refused(
            code = "unsupported.destination_snapshot.bounds_integer_range",
            facts = mapOf("command" to commandId),
        )
    }
    return BoundsResult.Accepted(
        GPUPixelBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt()),
    )
}

private fun alignUpOrNull(value: Long, alignment: Long): Long? =
    try {
        val remainder = Math.floorMod(value, alignment)
        if (remainder == 0L) value else Math.addExact(value, alignment - remainder)
    } catch (_: ArithmeticException) {
        null
    }

private fun GPUPixelBounds.checkedAlignedBytesOrNull(
    bytesPerPixel: Int,
    rowAlignmentBytes: Long,
): Long? = try {
    val rowBytes = Math.multiplyExact(width.toLong(), bytesPerPixel.toLong())
    val alignedRowBytes = alignUpOrNull(rowBytes, rowAlignmentBytes) ?: return null
    Math.multiplyExact(alignedRowBytes, height.toLong())
} catch (_: ArithmeticException) {
    null
}

private fun GPUPixelBounds.checkedAreaOrNull(): Long? = try {
    Math.multiplyExact(width.toLong(), height.toLong())
} catch (_: ArithmeticException) {
    null
}

private fun GPUPixelBounds.intersects(other: GPUPixelBounds): Boolean =
    left < other.right && other.left < right && top < other.bottom && other.top < bottom

private fun GPUPixelBounds.union(other: GPUPixelBounds): GPUPixelBounds = GPUPixelBounds(
    left = minOf(left, other.left),
    top = minOf(top, other.top),
    right = maxOf(right, other.right),
    bottom = maxOf(bottom, other.bottom),
)

private fun GPUPixelBounds.dumpLabel(): String = "$left,$top,$right,$bottom"

private fun GPUDestinationSnapshotGroup.dumpLines(index: Int): List<String> =
    listOf(
        "destination-snapshot:group index=$index key=${key.dumpLabel()} " +
            "bounds=${logicalBounds.dumpLabel()} copiedBytes=$copiedBytes " +
            "members=${members.joinToString(",", transform = GPUDestinationReadMember::commandId)}",
    ) + decisionDump

private fun GPUDestinationSnapshotGroupKey.dumpLabel(): String {
    val continuation = sampleContinuation?.let { value ->
        "sampleTarget=${value.target.value};" +
            "sampleTargetGeneration=${value.targetGeneration};" +
            "sampleDeviceGeneration=${value.deviceGeneration.value};" +
            "sampleFormat=${value.colorFormat.value};" +
            "sampleColor=${value.colorInterpretation.value};" +
            "sampleCount=${value.samplePlan.sampleCount};" +
            "attachmentAuthority=${value.attachmentAuthority.name};" +
            "colorAttachment=${value.colorAttachment.value};" +
            "depthStencilAttachment=${value.depthStencilAttachment?.value ?: "none"};"
    } ?: "sampleContinuation=none;"
    return "target=${target.value};targetGeneration=$targetGeneration;" +
        "deviceGeneration=${deviceGeneration.value};format=${format.value};" +
        "color=${colorInterpretation.value};$continuation" +
        "sourceIntermediate=${sourceIntermediate?.value ?: "none"}"
}

private fun refusal(
    code: String,
    access: GPUTargetAccess,
    facts: Map<String, String>,
): GPUDestinationSnapshotRefusal = GPUDestinationSnapshotRefusal(
    code = code,
    commandId = access.commandId,
    facts = facts.toSortedMap(),
)

private fun Double.stableDecimal(): String = when {
    isNaN() -> "nan"
    this == Double.POSITIVE_INFINITY -> "infinity"
    this == Double.NEGATIVE_INFINITY -> "-infinity"
    else -> String.format(Locale.ROOT, "%.6f", this)
}

private const val MAX_UNION_INFLATION = 2.0
