package org.graphiks.kanvas.gpu.renderer.resources

import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import java.math.BigInteger
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.validateTextureRequest
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableMap
import org.graphiks.kanvas.gpu.renderer.collections.immutableSet
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity

/** Resource-layer submission identity, intentionally independent from the execution package. */
@JvmInline
value class GPUResourceSubmissionID(val value: Long) {
    init {
        require(value > 0L) { "GPUResourceSubmissionID.value must be positive" }
    }
}

/** Centrally known physical texel-block footprint for a supported scratch format. */
data class GPUTexturePhysicalFootprint(
    val blockWidth: Int,
    val blockHeight: Int,
    val bytesPerBlock: Int,
) {
    init {
        require(blockWidth > 0) { "GPUTexturePhysicalFootprint.blockWidth must be positive" }
        require(blockHeight > 0) { "GPUTexturePhysicalFootprint.blockHeight must be positive" }
        require(bytesPerBlock > 0) { "GPUTexturePhysicalFootprint.bytesPerBlock must be positive" }
    }
}

/** Exact cache key for one normalized physical scratch texture. */
class GPUScratchTextureKey internal constructor(
    val deviceGeneration: GPUDeviceGenerationID,
    val backingWidth: Int,
    val backingHeight: Int,
    val format: GPUColorFormat,
    val sampleCount: Int,
    usages: Set<GPUFrameResourceUsage>,
) {
    val usages: Set<GPUFrameResourceUsage> = immutableSet(usages)

    init {
        require(backingWidth > 0) { "GPUScratchTextureKey.backingWidth must be positive" }
        require(backingHeight > 0) { "GPUScratchTextureKey.backingHeight must be positive" }
        require(sampleCount > 0) { "GPUScratchTextureKey.sampleCount must be positive" }
        require(usages.isNotEmpty()) { "GPUScratchTextureKey.usages must not be empty" }
    }

    override fun equals(other: Any?): Boolean =
        other is GPUScratchTextureKey &&
            deviceGeneration == other.deviceGeneration &&
            backingWidth == other.backingWidth &&
            backingHeight == other.backingHeight &&
            format == other.format &&
            sampleCount == other.sampleCount &&
            usages == other.usages

    override fun hashCode(): Int {
        var result = deviceGeneration.hashCode()
        result = 31 * result + backingWidth
        result = 31 * result + backingHeight
        result = 31 * result + format.hashCode()
        result = 31 * result + sampleCount
        result = 31 * result + usages.hashCode()
        return result
    }
}

/** One typed half-open use interval within a transactional preflight scope. */
data class GPUScratchUseInterval(
    val firstStep: Int,
    val lastStepExclusive: Int,
) {
    init {
        require(firstStep >= 0) { "GPUScratchUseInterval.firstStep must be non-negative" }
        require(lastStepExclusive > firstStep) {
            "GPUScratchUseInterval.lastStepExclusive must be greater than firstStep"
        }
    }

    internal fun overlaps(other: GPUScratchUseInterval): Boolean =
        firstStep < other.lastStepExclusive && other.firstStep < lastStepExclusive
}

/** Scratch reservation input derived from a Task 6 typed texture preparation request. */
data class GPUScratchTextureReservationRequest(
    val reservationId: String,
    val reservationScope: String,
    val preparation: GPUResourcePreparationRequest,
    val deviceGeneration: GPUDeviceGenerationID,
    val firstStep: Int,
    val lastStepExclusive: Int,
) {
    init {
        require(reservationId.isNotBlank()) {
            "GPUScratchTextureReservationRequest.reservationId must not be blank"
        }
        require(reservationScope.isNotBlank()) {
            "GPUScratchTextureReservationRequest.reservationScope must not be blank"
        }
        require(preparation.resource is GPUFrameTextureRef) {
            "GPUScratchTextureReservationRequest.preparation must name a texture"
        }
        require(preparation.descriptor is GPUFrameTextureDescriptor) {
            "GPUScratchTextureReservationRequest.preparation must use GPUFrameTextureDescriptor"
        }
        GPUScratchUseInterval(firstStep, lastStepExclusive)
    }

    val logicalBounds: GPUPixelBounds
        get() = (preparation.descriptor as GPUFrameTextureDescriptor).logicalBounds

    internal val textureDescriptor: GPUFrameTextureDescriptor
        get() = preparation.descriptor as GPUFrameTextureDescriptor

    internal val interval: GPUScratchUseInterval
        get() = GPUScratchUseInterval(firstStep, lastStepExclusive)
}

/** Immutable opaque reservation result; mutable entry state remains private to the pool. */
class GPUScratchTextureLease internal constructor(
    val reservationId: String,
    val reservationScope: String,
    val reservationOrdinal: Long,
    internal val acquisitionToken: Long,
    val resourceRef: GPUTextureResourceRef,
    val key: GPUScratchTextureKey,
    val logicalBounds: GPUPixelBounds,
    val role: GPUFrameResourceRole,
    val physicalBytes: Long,
    val interval: GPUScratchUseInterval,
) {
    val backingWidth: Int get() = key.backingWidth
    val backingHeight: Int get() = key.backingHeight
    val deviceGeneration: GPUDeviceGenerationID get() = key.deviceGeneration
    val usages: Set<GPUFrameResourceUsage> get() = key.usages
}

sealed interface GPUScratchTextureReservationResult {
    data class Accepted(val lease: GPUScratchTextureLease) : GPUScratchTextureReservationResult
    data class Refused(val diagnostic: GPUDiagnostic) : GPUScratchTextureReservationResult
}

enum class GPUScratchTextureState {
    Available,
    Reserved,
    Submitted,
    CompletedAvailable,
    Evicted,
    Invalidated,
    Quarantined,
}

enum class GPUScratchCompletionFailure {
    Failure,
    Uncertain,
    DeviceLost,
    PoolUseTokenExhausted,
}

sealed interface GPUScratchLifecycleResult {
    data class Accepted(val resourceRefs: List<GPUTextureResourceRef>) : GPUScratchLifecycleResult
    data class Refused(val diagnostic: GPUDiagnostic) : GPUScratchLifecycleResult
}

data class GPUScratchRollbackResult(
    val releasedReservationIds: List<String>,
    val resourceRefs: List<GPUTextureResourceRef>,
)

data class GPUScratchTextureEntrySnapshot(
    val resourceRef: GPUTextureResourceRef,
    val state: GPUScratchTextureState,
    val deviceGeneration: GPUDeviceGenerationID,
    val physicalBytes: Long,
    val lastUseToken: Long,
    val quarantineReason: GPUScratchCompletionFailure?,
)

data class GPUScratchTexturePoolTelemetry(
    val residentBytes: Long,
    val reservedBytes: Long,
    val inFlightBytes: Long,
    val reclaimableBytes: Long,
    val quarantinedBytes: Long,
    val categoryTotals: Map<GPUFrameMemoryCategory, Long>,
)

/** Shared accounting for physical pool allocations owned by one concrete provider. */
internal class GPUPhysicalPoolBudgetLedger(
    initialUseToken: Long = 1L,
) {
    private data class Allocation(
        val category: GPUFrameMemoryCategory,
        val physicalBytes: Long,
    )

    private val allocations = linkedMapOf<String, Allocation>()
    private var nextUseToken = initialUseToken

    init {
        require(initialUseToken > 0L) { "initialUseToken must be positive" }
    }

    fun register(
        allocationId: String,
        category: GPUFrameMemoryCategory,
        physicalBytes: Long,
    ) {
        require(allocationId.isNotBlank()) { "allocationId must not be blank" }
        require(physicalBytes >= 0L) { "physicalBytes must be non-negative" }
        check(allocations.putIfAbsent(allocationId, Allocation(category, physicalBytes)) == null) {
            "physical pool allocation $allocationId is already registered"
        }
    }

    fun remove(allocationId: String) {
        check(allocations.remove(allocationId) != null) {
            "physical pool allocation $allocationId is not registered"
        }
    }

    fun residentBytes(category: GPUFrameMemoryCategory): Long = allocations.values
        .asSequence()
        .filter { allocation -> allocation.category == category }
        .fold(0L) { total, allocation -> Math.addExact(total, allocation.physicalBytes) }

    fun managedResidentBytes(): Long = allocations.values
        .fold(0L) { total, allocation -> Math.addExact(total, allocation.physicalBytes) }

    fun takeUseToken(): Long? {
        if (nextUseToken == Long.MAX_VALUE) return null
        return nextUseToken.also { nextUseToken += 1L }
    }

    val useTokenExhausted: Boolean
        get() = nextUseToken == Long.MAX_VALUE
}

/**
 * Completion-safe scratch texture pool for the mono-backend WebGPU renderer.
 *
 * The owning backend/render-queue thread must serialize all calls. This class is intentionally not
 * thread-safe. It borrows Graphite's physical-size/key discipline, not its resource scheduler.
 * Cross-submit reuse is more conservative than Graphite: only accepted GPU completion makes a
 * submitted entry reusable.
 */
class GPUScratchTexturePool internal constructor(
    private val budgetLedger: GPUPhysicalPoolBudgetLedger,
) {
    internal constructor() : this(GPUPhysicalPoolBudgetLedger())

    private val entries = mutableListOf<Entry>()
    private val submissions = linkedMapOf<GPUResourceSubmissionID, List<Entry>>()
    private var nextResourceOrdinal = 1L
    private var nextReservationOrdinal = 1L
    internal val trackedEntryCount: Int get() = entries.size

    fun reserve(
        request: GPUScratchTextureReservationRequest,
        budget: GPUFrameMemoryBudgetPlan,
        capabilities: GPUCapabilities,
    ): GPUScratchTextureReservationResult {
        val limits = capabilities.limits ?: return refused(
            code = "unsupported.scratch_texture.device_limits_unknown",
            message = "Scratch texture allocation requires observed device limits.",
            facts = mapOf("reservationId" to request.reservationId),
        )
        val descriptor = request.textureDescriptor
        val invalidUsages = request.preparation.usages - SCRATCH_TEXTURE_USAGES
        if (invalidUsages.isNotEmpty()) {
            return refused(
                code = "unsupported.scratch_texture.usage_invalid",
                message = "Scratch texture usage contains buffer-only or unsupported texture usages.",
                facts = mapOf(
                    "invalidUsages" to invalidUsages.map(GPUFrameResourceUsage::name).sorted().joinToString(","),
                ),
            )
        }
        val normalizedWidth = normalizeApproxSize(descriptor.logicalBounds.width)
            ?: return refused(
                code = "unsupported.scratch_texture.backing_extent_overflow",
                message = "Scratch backing width normalization exceeds the supported Int extent.",
                facts = mapOf("logicalWidth" to descriptor.logicalBounds.width.toString()),
            )
        val normalizedHeight = normalizeApproxSize(descriptor.logicalBounds.height)
            ?: return refused(
                code = "unsupported.scratch_texture.backing_extent_overflow",
                message = "Scratch backing height normalization exceeds the supported Int extent.",
                facts = mapOf("logicalHeight" to descriptor.logicalBounds.height.toString()),
            )
        val backingExtent = "${normalizedWidth}x$normalizedHeight"
        if (normalizedWidth.toLong() > limits.maxTextureDimension2D ||
            normalizedHeight.toLong() > limits.maxTextureDimension2D
        ) {
            return refused(
                code = "unsupported.scratch_texture.backing_dimension_exceeded",
                message = "Normalized scratch backing exceeds maxTextureDimension2D.",
                facts = mapOf(
                    "requestedBackingExtent" to backingExtent,
                    "maxTextureDimension2D" to limits.maxTextureDimension2D.toString(),
                ),
            )
        }

        val formatInfo = scratchFormatInfoFor(descriptor.format) ?: return refused(
            code = "unsupported.scratch_texture.format_footprint_unknown",
            message = "Scratch texture format has no centrally registered physical footprint.",
            facts = mapOf("format" to descriptor.format.value),
        )
        val capabilityUsage = request.preparation.usages.fold(GPUTextureUsage.None) { usage, required ->
            usage or required.toGPUTextureUsage()
        }
        capabilities.validateTextureRequest(
            format = formatInfo.format,
            width = normalizedWidth,
            height = normalizedHeight,
            usage = capabilityUsage,
        )?.let { capabilityDiagnostic ->
            val suffix = when (capabilityDiagnostic.requirementName) {
                "texture.format" -> "format"
                "texture.usage" -> "usage"
                else -> "size"
            }
            return refused(
                code = "unsupported.scratch_texture.capability_texture_$suffix",
                message = "Observed GPU capabilities refuse the normalized scratch texture.",
                facts = mapOf(
                    "capabilityCode" to capabilityDiagnostic.code,
                    "required" to capabilityDiagnostic.required,
                    "observed" to (capabilityDiagnostic.observed ?: "unknown"),
                ),
            )
        }
        val physicalBytes = physicalBytes(
            width = normalizedWidth,
            height = normalizedHeight,
            footprint = formatInfo.footprint,
            sampleCount = descriptor.sampleCount,
        ) ?: return refused(
            code = "unsupported.scratch_texture.physical_size_overflow",
            message = "Normalized scratch physical byte size exceeds signed 64-bit accounting.",
            facts = mapOf(
                "requestedBackingExtent" to backingExtent,
                "format" to descriptor.format.value,
                "sampleCount" to descriptor.sampleCount.toString(),
            ),
        )

        val key = GPUScratchTextureKey(
            deviceGeneration = request.deviceGeneration,
            backingWidth = normalizedWidth,
            backingHeight = normalizedHeight,
            format = descriptor.format,
            sampleCount = descriptor.sampleCount,
            usages = immutableSet(request.preparation.usages),
        )
        val candidate = entries
            .asSequence()
            .filter { entry -> entry.key == key }
            .filter { entry -> entry.canReserve(request.reservationScope, request.interval) }
            .sortedWith(compareBy<Entry>({ it.lastUseToken }, { it.resourceRef.value }))
            .firstOrNull()
        val requestedNewBytes = if (candidate == null) physicalBytes else 0L
        budgetRefusal(
            budget = budget,
            requestedBackingBytes = requestedNewBytes,
            requestedBackingExtent = backingExtent,
        )?.let { diagnostic -> return GPUScratchTextureReservationResult.Refused(diagnostic) }

        if (candidate == null && nextResourceOrdinal == Long.MAX_VALUE) {
            return refused(
                code = "unsupported.scratch_texture.resource_ordinal_overflow",
                message = "Scratch pool resource ordinal is exhausted.",
                facts = mapOf("reservationId" to request.reservationId),
            )
        }

        val useToken = takeUseToken() ?: return refused(
            code = "unsupported.scratch_texture.use_token_overflow",
            message = "Scratch pool monotone use token is exhausted.",
            facts = mapOf("reservationId" to request.reservationId),
        )
        val reservationOrdinal = takeReservationOrdinal() ?: return refused(
            code = "unsupported.scratch_texture.reservation_ordinal_overflow",
            message = "Scratch pool reservation ordinal is exhausted.",
            facts = mapOf("reservationId" to request.reservationId),
        )
        val entry = candidate ?: createEntry(key, physicalBytes, useToken) ?: return refused(
            code = "unsupported.scratch_texture.resource_ordinal_overflow",
            message = "Scratch pool resource ordinal is exhausted.",
            facts = mapOf("reservationId" to request.reservationId),
        )
        if (candidate != null) entry.lastUseToken = useToken
        if (entry.state != GPUScratchTextureState.Reserved) {
            entry.returnStateAfterRollback = entry.state
            entry.state = GPUScratchTextureState.Reserved
            entry.reservationScope = request.reservationScope
            entry.currentLeaseEpochFloor = reservationOrdinal
        }
        val lease = GPUScratchTextureLease(
            reservationId = request.reservationId,
            reservationScope = request.reservationScope,
            reservationOrdinal = reservationOrdinal,
            acquisitionToken = useToken,
            resourceRef = entry.resourceRef,
            key = entry.key,
            logicalBounds = descriptor.logicalBounds,
            role = request.preparation.role,
            physicalBytes = entry.physicalBytes,
            interval = request.interval,
        )
        entry.reservations += lease
        return GPUScratchTextureReservationResult.Accepted(lease)
    }

    fun markSubmitted(
        reservationScope: String,
        submissionId: GPUResourceSubmissionID,
        deviceGeneration: GPUDeviceGenerationID,
    ): GPUScratchLifecycleResult {
        if (submissions.containsKey(submissionId)) {
            return lifecycleRefused(
                "unsupported.scratch_texture.duplicate_submission",
                "Scratch submission ID was already bound.",
                mapOf("submissionId" to submissionId.value.toString()),
            )
        }
        val scopedEntries = entries.filter { entry ->
            entry.state == GPUScratchTextureState.Reserved && entry.reservationScope == reservationScope
        }
        if (scopedEntries.isEmpty()) {
            return lifecycleRefused(
                "unsupported.scratch_texture.reservation_scope_unknown",
                "Scratch reservation scope has no reserved entries.",
                mapOf("reservationScope" to reservationScope),
            )
        }
        if (scopedEntries.any { entry -> entry.key.deviceGeneration != deviceGeneration }) {
            return lifecycleRefused(
                "unsupported.scratch_texture.submission_generation_mismatch",
                "Scratch submission generation does not match every reserved entry.",
                mapOf(
                    "reservationScope" to reservationScope,
                    "deviceGeneration" to deviceGeneration.value.toString(),
                ),
            )
        }
        scopedEntries.forEach { entry ->
            entry.state = GPUScratchTextureState.Submitted
            entry.submissionId = submissionId
        }
        submissions[submissionId] = scopedEntries.toList()
        return lifecycleAccepted(scopedEntries)
    }

    fun acceptCompletion(
        submissionId: GPUResourceSubmissionID,
        deviceGeneration: GPUDeviceGenerationID,
    ): GPUScratchLifecycleResult {
        val submittedEntries = submissions[submissionId] ?: return unknownCompletion(submissionId)
        if (submittedEntries.any { entry -> entry.key.deviceGeneration != deviceGeneration }) {
            return lifecycleRefused(
                "unsupported.scratch_texture.completion_generation_mismatch",
                "Scratch completion generation does not match the bound submission.",
                mapOf(
                    "submissionId" to submissionId.value.toString(),
                    "deviceGeneration" to deviceGeneration.value.toString(),
                ),
            )
        }
        if (submittedEntries.any { entry ->
                entry.state != GPUScratchTextureState.Submitted || entry.submissionId != submissionId
            }
        ) {
            return lifecycleRefused(
                "unsupported.scratch_texture.completion_not_pending",
                "Scratch completion does not identify a pending submitted resource set.",
                mapOf("submissionId" to submissionId.value.toString()),
            )
        }
        val completionUseTokens = submittedEntries.map {
            takeUseToken() ?: run {
                submittedEntries.forEach { entry ->
                    entry.state = GPUScratchTextureState.Quarantined
                    entry.submissionId = null
                    entry.reservations.clear()
                    entry.reservationScope = null
                    entry.failure = GPUScratchCompletionFailure.PoolUseTokenExhausted
                }
                submissions.remove(submissionId)
                return lifecycleRefused(
                    "unsupported.scratch_texture.use_token_overflow",
                    "Scratch completion cannot make resources reusable after use-token exhaustion.",
                    mapOf("submissionId" to submissionId.value.toString()),
                )
            }
        }
        submittedEntries.zip(completionUseTokens).forEach { (entry, useToken) ->
            entry.state = GPUScratchTextureState.CompletedAvailable
            entry.submissionId = null
            entry.reservations.clear()
            entry.reservationScope = null
            entry.returnStateAfterRollback = GPUScratchTextureState.CompletedAvailable
            entry.lastUseToken = useToken
        }
        submissions.remove(submissionId)
        return lifecycleAccepted(submittedEntries)
    }

    fun rejectCompletion(
        submissionId: GPUResourceSubmissionID,
        deviceGeneration: GPUDeviceGenerationID,
        failure: GPUScratchCompletionFailure,
    ): GPUScratchLifecycleResult {
        val submittedEntries = submissions[submissionId] ?: return unknownCompletion(submissionId)
        if (submittedEntries.any { entry -> entry.key.deviceGeneration != deviceGeneration }) {
            return lifecycleRefused(
                "unsupported.scratch_texture.completion_generation_mismatch",
                "Scratch failed completion generation does not match the bound submission.",
                mapOf("submissionId" to submissionId.value.toString()),
            )
        }
        if (submittedEntries.any { entry ->
                entry.state != GPUScratchTextureState.Submitted || entry.submissionId != submissionId
            }
        ) {
            return lifecycleRefused(
                "unsupported.scratch_texture.completion_not_pending",
                "Scratch failed completion does not identify pending submitted resources.",
                mapOf("submissionId" to submissionId.value.toString()),
            )
        }
        submittedEntries.forEach { entry ->
            entry.state = GPUScratchTextureState.Quarantined
            entry.submissionId = null
            entry.reservations.clear()
            entry.reservationScope = null
            entry.failure = failure
        }
        submissions.remove(submissionId)
        return lifecycleAccepted(submittedEntries)
    }

    fun rollbackBeforeSubmit(reservationScope: String): GPUScratchRollbackResult {
        val leases = entries
            .asSequence()
            .filter { entry ->
                entry.state == GPUScratchTextureState.Reserved && entry.reservationScope == reservationScope
            }
            .flatMap { entry -> entry.reservations.asSequence() }
            .sortedByDescending(GPUScratchTextureLease::reservationOrdinal)
            .toList()
        val released = leases.mapNotNull(::rollbackLeaseBeforeSubmit)
        return GPUScratchRollbackResult(
            releasedReservationIds = immutableList(released.flatMap(GPUScratchRollbackResult::releasedReservationIds)),
            resourceRefs = immutableList(released.flatMap(GPUScratchRollbackResult::resourceRefs)),
        )
    }

    internal fun canRollbackBeforeSubmit(lease: GPUScratchTextureLease): Boolean = entries.any { entry ->
        entry.state == GPUScratchTextureState.Reserved &&
            entry.reservationScope == lease.reservationScope &&
            entry.reservations.any { active -> active === lease }
    }

    internal fun rollbackLeaseBeforeSubmit(lease: GPUScratchTextureLease): GPUScratchRollbackResult? {
        val entry = entries.firstOrNull { candidate ->
            candidate.state == GPUScratchTextureState.Reserved &&
                candidate.reservationScope == lease.reservationScope &&
                candidate.reservations.any { active -> active === lease }
        } ?: return null
        entry.reservations.removeAll { active -> active === lease }
        if (entry.reservations.isEmpty()) {
            if (budgetLedger.useTokenExhausted) {
                entry.state = GPUScratchTextureState.Invalidated
                entry.reservationScope = null
                budgetLedger.remove(entry.allocationId)
                entries.remove(entry)
            } else {
                entry.state = entry.returnStateAfterRollback
                entry.reservationScope = null
            }
        }
        return GPUScratchRollbackResult(
            releasedReservationIds = immutableList(listOf(lease.reservationId)),
            resourceRefs = immutableList(listOf(lease.resourceRef)),
        )
    }

    fun invalidateGenerationsBefore(
        currentGeneration: GPUDeviceGenerationID,
    ): List<GPUScratchTextureEntrySnapshot> {
        val changed = entries.filter { entry ->
            entry.key.deviceGeneration.value < currentGeneration.value &&
                entry.state !in setOf(
                    GPUScratchTextureState.Evicted,
                    GPUScratchTextureState.Invalidated,
                )
        }
        changed.forEach { entry ->
            entry.state = GPUScratchTextureState.Invalidated
            entry.reservations.clear()
            entry.reservationScope = null
            entry.submissionId = null
            budgetLedger.remove(entry.allocationId)
        }
        val snapshots = changed.map(Entry::snapshot)
        submissions.entries.removeAll { (_, submittedEntries) ->
            submittedEntries.any { entry -> entry in changed }
        }
        entries.removeAll(changed.toSet())
        return immutableList(snapshots)
    }

    fun evictReclaimableUntil(residentBytesAtMost: Long): List<GPUScratchTextureEntrySnapshot> {
        require(residentBytesAtMost >= 0L) { "residentBytesAtMost must be non-negative" }
        val evicted = mutableListOf<GPUScratchTextureEntrySnapshot>()
        val candidates = entries
            .filter { entry ->
                entry.state == GPUScratchTextureState.Available ||
                    entry.state == GPUScratchTextureState.CompletedAvailable
            }
            .sortedWith(compareBy<Entry>({ it.lastUseToken }, { it.resourceRef.value }))
        for (entry in candidates) {
            if (telemetry().residentBytes <= residentBytesAtMost) break
            entry.state = GPUScratchTextureState.Evicted
            budgetLedger.remove(entry.allocationId)
            evicted += entry.snapshot()
            entries.remove(entry)
        }
        return immutableList(evicted)
    }

    internal fun oldestReclaimableCandidate(): GPUPhysicalPoolEvictionCandidate? = entries
        .asSequence()
        .filter { entry ->
            entry.state == GPUScratchTextureState.Available ||
                entry.state == GPUScratchTextureState.CompletedAvailable
        }
        .minWithOrNull(compareBy<Entry>({ it.lastUseToken }, { it.resourceRef.value }))
        ?.let { entry ->
            GPUPhysicalPoolEvictionCandidate(
                category = GPUFrameMemoryCategory.ReusableScratch,
                resourceId = entry.resourceRef.value,
                lastUseToken = entry.lastUseToken,
                physicalBytes = entry.physicalBytes,
            )
        }

    internal fun reclaimableCandidates(): List<GPUPhysicalPoolEvictionCandidate> = immutableList(
        entries.mapNotNull { entry ->
            if (entry.state != GPUScratchTextureState.Available &&
                entry.state != GPUScratchTextureState.CompletedAvailable
            ) {
                null
            } else {
                GPUPhysicalPoolEvictionCandidate(
                    category = GPUFrameMemoryCategory.ReusableScratch,
                    resourceId = entry.resourceRef.value,
                    lastUseToken = entry.lastUseToken,
                    physicalBytes = entry.physicalBytes,
                )
            }
        },
    )

    internal fun evictCandidate(resourceId: String): GPUScratchTextureEntrySnapshot? {
        val entry = entries.firstOrNull { candidate ->
            candidate.resourceRef.value == resourceId &&
                (candidate.state == GPUScratchTextureState.Available ||
                    candidate.state == GPUScratchTextureState.CompletedAvailable)
        } ?: return null
        entry.state = GPUScratchTextureState.Evicted
        budgetLedger.remove(entry.allocationId)
        val snapshot = entry.snapshot()
        entries.remove(entry)
        return snapshot
    }

    fun stateOf(lease: GPUScratchTextureLease): GPUScratchTextureState {
        val entry = entries.firstOrNull { candidate -> candidate.resourceRef == lease.resourceRef }
            ?: return GPUScratchTextureState.Invalidated
        return if (lease.reservationOrdinal < entry.currentLeaseEpochFloor ||
            (entry.reservations.isNotEmpty() && entry.reservations.none { active -> active === lease })
        ) {
            GPUScratchTextureState.Invalidated
        } else {
            entry.state
        }
    }

    fun telemetry(): GPUScratchTexturePoolTelemetry {
        val resident = entries.filterNot(Entry::terminal)
        val reserved = resident.filter { it.state == GPUScratchTextureState.Reserved }
        val inFlight = resident.filter { it.state == GPUScratchTextureState.Submitted }
        val reclaimable = resident.filter { entry ->
            entry.state == GPUScratchTextureState.Available ||
                entry.state == GPUScratchTextureState.CompletedAvailable
        }
        val quarantined = resident.filter { it.state == GPUScratchTextureState.Quarantined }
        val totals = GPUFrameMemoryCategory.entries.associateWith { category ->
            if (category == GPUFrameMemoryCategory.ReusableScratch) resident.sumBytes() else 0L
        }
        return GPUScratchTexturePoolTelemetry(
            residentBytes = resident.sumBytes(),
            reservedBytes = reserved.sumBytes(),
            inFlightBytes = inFlight.sumBytes(),
            reclaimableBytes = reclaimable.sumBytes(),
            quarantinedBytes = quarantined.sumBytes(),
            categoryTotals = immutableMap(totals),
        )
    }

    private fun createEntry(
        key: GPUScratchTextureKey,
        physicalBytes: Long,
        useToken: Long,
    ): Entry? {
        if (nextResourceOrdinal == Long.MAX_VALUE) return null
        val ordinal = nextResourceOrdinal
        nextResourceOrdinal += 1L
        return Entry(
            resourceRef = GPUTextureResourceRef("scratch-texture:$ordinal"),
            key = key,
            physicalBytes = physicalBytes,
            state = GPUScratchTextureState.Available,
            lastUseToken = useToken,
        ).also { entry ->
            budgetLedger.register(
                allocationId = entry.allocationId,
                category = GPUFrameMemoryCategory.ReusableScratch,
                physicalBytes = physicalBytes,
            )
            entries += entry
        }
    }

    private fun budgetRefusal(
        budget: GPUFrameMemoryBudgetPlan,
        requestedBackingBytes: Long,
        requestedBackingExtent: String,
    ): GPUDiagnostic? {
        if (budget.diagnostic != null) {
            return diagnostic(
                code = "unsupported.scratch_texture.frame_budget_invalid",
                message = "Scratch allocation cannot consume an already refused frame budget.",
                facts = mapOf("frameBudgetCode" to budget.diagnostic.code.value),
            )
        }
        val telemetry = telemetry()
        val categoryTotals = GPUFrameMemoryCategory.entries.associateWith { category ->
            when (category) {
                GPUFrameMemoryCategory.ReusableScratch ->
                    budgetLedger.residentBytes(category).toBigInteger() + requestedBackingBytes.toBigInteger()
                GPUFrameMemoryCategory.ReadbackStaging ->
                    budgetLedger.residentBytes(category).toBigInteger()
                else -> (budget.categoryTotals[category] ?: 0L).toBigInteger()
            }
        }
        val aggregate = categoryTotals.values.fold(BigInteger.ZERO, BigInteger::add)
        if (aggregate <= budget.configuredAggregateBudgetBytes.toBigInteger()) return null

        val facts = linkedMapOf(
            "requestedBackingBytes" to requestedBackingBytes.toString(),
            "requestedBackingExtent" to requestedBackingExtent,
            "residentBytes" to telemetry.residentBytes.toString(),
            "reservedBytes" to telemetry.reservedBytes.toString(),
            "inFlightBytes" to telemetry.inFlightBytes.toString(),
            "reclaimableBytes" to telemetry.reclaimableBytes.toString(),
            "quarantinedBytes" to telemetry.quarantinedBytes.toString(),
            "aggregatePeakBytes" to aggregate.toString(),
            "configuredAggregateBudgetBytes" to budget.configuredAggregateBudgetBytes.toString(),
        )
        GPUFrameMemoryCategory.entries.forEach { category ->
            facts["category.${category.name}"] = categoryTotals.getValue(category).toString()
        }
        return diagnostic(
            code = "unsupported.scratch_texture.aggregate_budget_exceeded",
            message = "Normalized physical scratch backing exceeds the aggregate frame budget.",
            facts = facts,
        )
    }

    private fun takeUseToken(): Long? = budgetLedger.takeUseToken()

    private fun takeReservationOrdinal(): Long? {
        if (nextReservationOrdinal == Long.MAX_VALUE) return null
        return nextReservationOrdinal.also { nextReservationOrdinal += 1L }
    }

    private fun unknownCompletion(submissionId: GPUResourceSubmissionID): GPUScratchLifecycleResult.Refused =
        lifecycleRefused(
            "unsupported.scratch_texture.completion_unknown",
            "Scratch completion does not identify a known submission.",
            mapOf("submissionId" to submissionId.value.toString()),
        )

    private fun lifecycleAccepted(entries: List<Entry>): GPUScratchLifecycleResult.Accepted =
        GPUScratchLifecycleResult.Accepted(
            immutableList(entries.map(Entry::resourceRef).distinct()),
        )

    private fun lifecycleRefused(
        code: String,
        message: String,
        facts: Map<String, String>,
    ): GPUScratchLifecycleResult.Refused =
        GPUScratchLifecycleResult.Refused(diagnostic(code, message, facts))

    private fun refused(
        code: String,
        message: String,
        facts: Map<String, String>,
    ): GPUScratchTextureReservationResult.Refused =
        GPUScratchTextureReservationResult.Refused(diagnostic(code, message, facts))

    private class Entry(
        val resourceRef: GPUTextureResourceRef,
        val key: GPUScratchTextureKey,
        val physicalBytes: Long,
        var state: GPUScratchTextureState,
        var lastUseToken: Long,
    ) {
        val allocationId: String = "scratch:${resourceRef.value}"
        val reservations = mutableListOf<GPUScratchTextureLease>()
        var reservationScope: String? = null
        var returnStateAfterRollback: GPUScratchTextureState = GPUScratchTextureState.Available
        var submissionId: GPUResourceSubmissionID? = null
        var failure: GPUScratchCompletionFailure? = null
        var currentLeaseEpochFloor: Long = 0L

        fun canReserve(scope: String, interval: GPUScratchUseInterval): Boolean = when (state) {
            GPUScratchTextureState.Available,
            GPUScratchTextureState.CompletedAvailable,
            -> true
            GPUScratchTextureState.Reserved ->
                reservationScope == scope && reservations.none { lease -> lease.interval.overlaps(interval) }
            GPUScratchTextureState.Submitted,
            GPUScratchTextureState.Evicted,
            GPUScratchTextureState.Invalidated,
            GPUScratchTextureState.Quarantined,
            -> false
        }

        fun terminal(): Boolean =
            state == GPUScratchTextureState.Evicted || state == GPUScratchTextureState.Invalidated

        fun snapshot(): GPUScratchTextureEntrySnapshot = GPUScratchTextureEntrySnapshot(
            resourceRef = resourceRef,
            state = state,
            deviceGeneration = key.deviceGeneration,
            physicalBytes = physicalBytes,
            lastUseToken = lastUseToken,
            quarantineReason = failure,
        )
    }

    private fun List<Entry>.sumBytes(): Long =
        fold(0L) { total, entry -> Math.addExact(total, entry.physicalBytes) }
}

/** Resource-layer view of the exact output staging descriptor planned by execution. */
interface GPUReadbackStagingDescriptorContract {
    val minimumBufferBytes: Long
    val maxBufferSize: Long
    val mapOffsetBytes: Long
    val usages: Set<GPUFrameResourceUsage>
}

private data class GPUReadbackStagingDescriptorSnapshot(
    val minimumBufferBytes: Long,
    val maxBufferSize: Long,
    val mapOffsetBytes: Long,
    val usages: Set<GPUFrameResourceUsage>,
)

data class GPUReadbackStagingReservationRequest(
    val reservationId: String,
    val ownerScope: String,
    val deviceGeneration: GPUDeviceGenerationID,
    val descriptor: GPUReadbackStagingDescriptorContract,
    val backingBufferBytes: Long,
    /** Aggregate Task 4 budget owned by the same provider as scratch textures. */
    val budgetPlan: GPUFrameMemoryBudgetPlan,
) {
    init {
        require(reservationId.isNotBlank()) {
            "GPUReadbackStagingReservationRequest.reservationId must not be blank"
        }
        require(ownerScope.isNotBlank()) {
            "GPUReadbackStagingReservationRequest.ownerScope must not be blank"
        }
    }
}

class GPUReadbackStagingLease internal constructor(
    val reservationId: String,
    val ownerScope: String,
    val deviceGeneration: GPUDeviceGenerationID,
    val resourceRef: GPUBufferResourceRef,
    val reservationOrdinal: Long,
    internal val acquisitionToken: Long,
    val logicalMinimumBytes: Long,
    val backingBufferBytes: Long,
    usages: Set<GPUFrameResourceUsage>,
) {
    val usages: Set<GPUFrameResourceUsage> = immutableSet(usages)
}

sealed interface GPUReadbackStagingReservationResult {
    data class Accepted(val lease: GPUReadbackStagingLease) : GPUReadbackStagingReservationResult
    data class Refused(val diagnostic: GPUDiagnostic) : GPUReadbackStagingReservationResult
}

enum class GPUReadbackStagingState {
    Reserved,
    Submitted,
    GPUCompletedMappingPending,
    Mapped,
    Depadded,
    Releasable,
    Quarantined,
    Evicted,
    Invalidated,
}

enum class GPUReadbackCompletionFailure {
    Failure,
    Uncertain,
    DeviceLost,
}

enum class GPUReadbackMapFailureSafety {
    SafeUnmapAndReleaseProven,
    ReleaseUncertain,
    DeviceLost,
}

sealed interface GPUReadbackStagingLifecycleResult {
    data class Accepted(val resourceRefs: List<GPUBufferResourceRef>) : GPUReadbackStagingLifecycleResult
    data class Refused(val diagnostic: GPUDiagnostic) : GPUReadbackStagingLifecycleResult
}

data class GPUReadbackStagingRollbackResult(
    val releasedReservationIds: List<String>,
    val resourceRefs: List<GPUBufferResourceRef>,
)

data class GPUReadbackStagingEntrySnapshot(
    val resourceRef: GPUBufferResourceRef,
    val state: GPUReadbackStagingState,
    val deviceGeneration: GPUDeviceGenerationID,
    val backingBufferBytes: Long,
    val lastUseToken: Long,
    val completionFailure: GPUReadbackCompletionFailure?,
    val mapFailureSafety: GPUReadbackMapFailureSafety?,
)

sealed interface GPUPhysicalPoolMaintenanceDecision<out T> {
    data class Applied<T>(val value: T) : GPUPhysicalPoolMaintenanceDecision<T>
    data class Refused(val diagnostic: GPUDiagnostic) : GPUPhysicalPoolMaintenanceDecision<Nothing>
}

sealed interface GPUPhysicalPoolRollbackRelease {
    val reservationId: String
    val acquisitionOrdinal: Long

    data class Scratch(
        override val reservationId: String,
        override val acquisitionOrdinal: Long,
        val resourceRef: GPUTextureResourceRef,
    ) : GPUPhysicalPoolRollbackRelease

    data class Readback(
        override val reservationId: String,
        override val acquisitionOrdinal: Long,
        val resourceRef: GPUBufferResourceRef,
    ) : GPUPhysicalPoolRollbackRelease
}

class GPUPhysicalPoolRollbackSummary(
    val scratch: GPUScratchRollbackResult,
    val readback: GPUReadbackStagingRollbackResult,
    releaseOrder: List<GPUPhysicalPoolRollbackRelease>,
) {
    val releaseOrder: List<GPUPhysicalPoolRollbackRelease> = immutableList(releaseOrder)
}

class GPUPhysicalPoolEvictionSummary(
    scratchEntries: List<GPUScratchTextureEntrySnapshot>,
    readbackEntries: List<GPUReadbackStagingEntrySnapshot>,
    val remainingResidentBytes: Long,
) {
    val scratchEntries: List<GPUScratchTextureEntrySnapshot> = immutableList(scratchEntries)
    val readbackEntries: List<GPUReadbackStagingEntrySnapshot> = immutableList(readbackEntries)
}

class GPUPhysicalPoolInvalidationSummary(
    scratchEntries: List<GPUScratchTextureEntrySnapshot>,
    readbackEntries: List<GPUReadbackStagingEntrySnapshot>,
) {
    val scratchEntries: List<GPUScratchTextureEntrySnapshot> = immutableList(scratchEntries)
    val readbackEntries: List<GPUReadbackStagingEntrySnapshot> = immutableList(readbackEntries)
}

internal data class GPUPhysicalPoolEvictionCandidate(
    val category: GPUFrameMemoryCategory,
    val resourceId: String,
    val lastUseToken: Long,
    val physicalBytes: Long,
)

data class GPUReadbackStagingPoolTelemetry(
    val residentBytes: Long,
    val reservedBytes: Long,
    val inFlightBytes: Long,
    val mappingOwnedBytes: Long,
    val reclaimableBytes: Long,
    val quarantinedBytes: Long,
    val categoryTotals: Map<GPUFrameMemoryCategory, Long>,
)

/**
 * Output-owned staging pool. GPU completion transfers ownership to mapping; it never releases it.
 * All calls are confined to the backend/render-queue owner thread and are not thread-safe.
 */
class GPUReadbackStagingPool internal constructor(
    private val budgetLedger: GPUPhysicalPoolBudgetLedger,
) {
    internal constructor() : this(GPUPhysicalPoolBudgetLedger())

    private val entries = mutableListOf<ReadbackEntry>()
    private val submissions = linkedMapOf<GPUResourceSubmissionID, List<ReadbackEntry>>()
    private var nextResourceOrdinal = 1L
    private var nextReservationOrdinal = 1L
    internal val trackedEntryCount: Int get() = entries.size

    fun reserve(request: GPUReadbackStagingReservationRequest): GPUReadbackStagingReservationResult {
        val descriptor = GPUReadbackStagingDescriptorSnapshot(
            minimumBufferBytes = request.descriptor.minimumBufferBytes,
            maxBufferSize = request.descriptor.maxBufferSize,
            mapOffsetBytes = request.descriptor.mapOffsetBytes,
            usages = immutableSet(request.descriptor.usages),
        )
        val exactUsages = setOf(
            GPUFrameResourceUsage.MapRead,
            GPUFrameResourceUsage.CopyDestination,
        )
        val refusal = when {
            descriptor.minimumBufferBytes <= 0L -> readbackStagingDiagnostic(
                "unsupported.readback_staging.minimum_buffer_invalid",
                "Readback staging minimum buffer size must be positive.",
                mapOf("minimumBufferBytes" to descriptor.minimumBufferBytes.toString()),
            )
            descriptor.maxBufferSize <= 0L -> readbackStagingDiagnostic(
                "unsupported.readback_staging.max_buffer_size_invalid",
                "Readback staging maxBufferSize must be positive.",
                mapOf("maxBufferSize" to descriptor.maxBufferSize.toString()),
            )
            request.backingBufferBytes <= 0L -> readbackStagingDiagnostic(
                "unsupported.readback_staging.backing_invalid",
                "Readback staging backing size must be positive.",
                mapOf("backingBufferBytes" to request.backingBufferBytes.toString()),
            )
            descriptor.usages != exactUsages -> readbackStagingDiagnostic(
                "unsupported.readback_staging.usage_invalid",
                "Readback staging usage must be exactly MapRead and CopyDestination.",
                mapOf("usages" to descriptor.usages.map { it.name }.sorted().joinToString(",")),
            )
            descriptor.mapOffsetBytes != 0L -> readbackStagingDiagnostic(
                "unsupported.readback_staging.map_offset_invalid",
                "Readback staging must map from offset zero.",
                mapOf("mapOffsetBytes" to descriptor.mapOffsetBytes.toString()),
            )
            request.backingBufferBytes < descriptor.minimumBufferBytes -> readbackStagingDiagnostic(
                "unsupported.readback_staging.backing_too_small",
                "Readback staging backing is smaller than the exact Dawn minimum.",
                mapOf(
                    "minimumBufferBytes" to descriptor.minimumBufferBytes.toString(),
                    "backingBufferBytes" to request.backingBufferBytes.toString(),
                ),
            )
            request.backingBufferBytes > descriptor.maxBufferSize -> readbackStagingDiagnostic(
                "unsupported.readback_staging.max_buffer_size_exceeded",
                "Readback staging backing exceeds the observed maxBufferSize.",
                mapOf(
                    "backingBufferBytes" to request.backingBufferBytes.toString(),
                    "maxBufferSize" to descriptor.maxBufferSize.toString(),
                ),
            )
            else -> null
        }
        if (refusal != null) return GPUReadbackStagingReservationResult.Refused(refusal)

        val reusable = entries
            .filter { entry ->
                entry.state == GPUReadbackStagingState.Releasable &&
                    entry.deviceGeneration == request.deviceGeneration &&
                    entry.backingBufferBytes == request.backingBufferBytes &&
                    entry.usages == exactUsages
            }
            .minWithOrNull(compareBy<ReadbackEntry>({ it.lastUseToken }, { it.resourceRef.value }))
        val requestedNewBytes = if (reusable == null) request.backingBufferBytes else 0L
        readbackBudgetRefusal(request.budgetPlan, requestedNewBytes)?.let { diagnostic ->
            return GPUReadbackStagingReservationResult.Refused(diagnostic)
        }

        if (reusable == null && nextResourceOrdinal == Long.MAX_VALUE) {
            return stagingReservationRefused(
                "unsupported.readback_staging.resource_ordinal_overflow",
                "Readback staging resource ordinal is exhausted.",
            )
        }

        val reservationOrdinal = takeReservationOrdinal() ?: return stagingReservationRefused(
            "unsupported.readback_staging.reservation_ordinal_overflow",
            "Readback staging reservation ordinal is exhausted.",
        )
        val useToken = takeReadbackUseToken() ?: return stagingReservationRefused(
            "unsupported.readback_staging.use_token_overflow",
            "Readback staging monotone use token is exhausted.",
        )
        val resourceOrdinal = if (reusable == null) {
            takeResourceOrdinal() ?: return stagingReservationRefused(
                "unsupported.readback_staging.resource_ordinal_overflow",
                "Readback staging resource ordinal is exhausted.",
            )
        } else {
            null
        }

        val entry = reusable ?: ReadbackEntry(
            resourceRef = GPUBufferResourceRef("readback-staging:$resourceOrdinal"),
            deviceGeneration = request.deviceGeneration,
            backingBufferBytes = request.backingBufferBytes,
            usages = immutableSet(exactUsages),
            state = GPUReadbackStagingState.Releasable,
            lastUseToken = useToken,
        ).also { created ->
            budgetLedger.register(
                allocationId = created.allocationId,
                category = GPUFrameMemoryCategory.ReadbackStaging,
                physicalBytes = created.backingBufferBytes,
            )
            entries += created
        }
        entry.lastUseToken = useToken
        entry.state = GPUReadbackStagingState.Reserved
        entry.activeReservationId = request.reservationId
        entry.ownerScope = request.ownerScope
        entry.submissionId = null
        entry.completionFailure = null
        entry.mapFailureSafety = null
        val lease = GPUReadbackStagingLease(
            reservationId = request.reservationId,
            ownerScope = request.ownerScope,
            deviceGeneration = request.deviceGeneration,
            resourceRef = entry.resourceRef,
            reservationOrdinal = reservationOrdinal,
            acquisitionToken = useToken,
            logicalMinimumBytes = descriptor.minimumBufferBytes,
            backingBufferBytes = request.backingBufferBytes,
            usages = exactUsages,
        )
        entry.activeLease = lease
        return GPUReadbackStagingReservationResult.Accepted(lease)
    }

    fun markSubmitted(
        leases: List<GPUReadbackStagingLease>,
        submissionId: GPUResourceSubmissionID,
    ): GPUReadbackStagingLifecycleResult {
        if (leases.isEmpty() || leases.map { it.resourceRef }.distinct().size != leases.size) {
            return stagingLifecycleRefused(
                "unsupported.readback_staging.submission_membership_invalid",
                "Readback staging submission requires a non-empty unique lease set.",
            )
        }
        if (submissions.containsKey(submissionId)) {
            return stagingLifecycleRefused(
                "unsupported.readback_staging.duplicate_submission",
                "Readback staging submission ID was already bound.",
            )
        }
        val selected = leases.mapNotNull { lease -> entryFor(lease) }
        if (selected.size != leases.size || selected.any { it.state != GPUReadbackStagingState.Reserved }) {
            return stagingLifecycleRefused(
                "unsupported.readback_staging.lease_not_reserved",
                "Readback staging submission contains a foreign or non-reserved lease.",
            )
        }
        if (leases.map(GPUReadbackStagingLease::deviceGeneration).distinct().size != 1) {
            return stagingLifecycleRefused(
                "unsupported.readback_staging.submission_generation_mixed",
                "Readback staging submission must contain one device generation.",
            )
        }
        if (leases.map(GPUReadbackStagingLease::ownerScope).distinct().size != 1) {
            return stagingLifecycleRefused(
                "unsupported.readback_staging.submission_owner_mixed",
                "Readback staging submission must contain one owner scope.",
            )
        }
        selected.forEach { entry ->
            entry.state = GPUReadbackStagingState.Submitted
            entry.submissionId = submissionId
        }
        submissions[submissionId] = selected
        return stagingLifecycleAccepted(selected)
    }

    fun acceptGPUCompletion(
        submissionId: GPUResourceSubmissionID,
        deviceGeneration: GPUDeviceGenerationID,
    ): GPUReadbackStagingLifecycleResult {
        val selected = submissions[submissionId] ?: return stagingLifecycleRefused(
            "unsupported.readback_staging.completion_unknown",
            "Readback staging completion does not identify a known submission.",
        )
        if (selected.any { it.deviceGeneration != deviceGeneration }) {
            return stagingLifecycleRefused(
                "unsupported.readback_staging.completion_generation_mismatch",
                "Readback staging completion generation is stale or mismatched.",
            )
        }
        if (selected.any { it.state != GPUReadbackStagingState.Submitted || it.submissionId != submissionId }) {
            return stagingLifecycleRefused(
                "unsupported.readback_staging.completion_not_pending",
                "Readback staging submission is no longer pending GPU completion.",
            )
        }
        selected.forEach { entry ->
            entry.state = GPUReadbackStagingState.GPUCompletedMappingPending
        }
        submissions.remove(submissionId)
        return stagingLifecycleAccepted(selected)
    }

    fun rejectGPUCompletion(
        submissionId: GPUResourceSubmissionID,
        deviceGeneration: GPUDeviceGenerationID,
        failure: GPUReadbackCompletionFailure,
    ): GPUReadbackStagingLifecycleResult {
        val selected = submissions[submissionId] ?: return stagingLifecycleRefused(
            "unsupported.readback_staging.completion_unknown",
            "Readback staging failed completion does not identify a known submission.",
        )
        if (selected.any { entry -> entry.deviceGeneration != deviceGeneration }) {
            return stagingLifecycleRefused(
                "unsupported.readback_staging.completion_generation_mismatch",
                "Readback staging failed completion generation is stale or mismatched.",
            )
        }
        if (selected.any { entry ->
                entry.state != GPUReadbackStagingState.Submitted || entry.submissionId != submissionId
            }
        ) {
            return stagingLifecycleRefused(
                "unsupported.readback_staging.completion_not_pending",
                "Readback staging failed completion is no longer pending.",
            )
        }
        selected.forEach { entry ->
            entry.state = GPUReadbackStagingState.Quarantined
            entry.completionFailure = failure
        }
        submissions.remove(submissionId)
        return stagingLifecycleAccepted(selected)
    }

    fun rollbackBeforeSubmit(ownerScope: String): GPUReadbackStagingRollbackResult {
        val leases = entries.asSequence()
            .filter { entry ->
                entry.state == GPUReadbackStagingState.Reserved && entry.ownerScope == ownerScope
            }
            .mapNotNull(ReadbackEntry::activeLease)
            .sortedByDescending(GPUReadbackStagingLease::reservationOrdinal)
            .toList()
        val released = leases.mapNotNull(::rollbackLeaseBeforeSubmit)
        return GPUReadbackStagingRollbackResult(
            releasedReservationIds = immutableList(
                released.flatMap(GPUReadbackStagingRollbackResult::releasedReservationIds),
            ),
            resourceRefs = immutableList(released.flatMap(GPUReadbackStagingRollbackResult::resourceRefs)),
        )
    }

    internal fun canRollbackBeforeSubmit(lease: GPUReadbackStagingLease): Boolean =
        entries.any { entry ->
            entry.state == GPUReadbackStagingState.Reserved &&
                entry.ownerScope == lease.ownerScope &&
                entry.activeLease === lease
        }

    internal fun rollbackLeaseBeforeSubmit(
        lease: GPUReadbackStagingLease,
    ): GPUReadbackStagingRollbackResult? {
        val entry = entries.firstOrNull { candidate ->
            candidate.state == GPUReadbackStagingState.Reserved &&
                candidate.ownerScope == lease.ownerScope &&
                candidate.activeLease === lease
        } ?: return null
        if (budgetLedger.useTokenExhausted) {
            terminalizeReadback(entry)
        } else {
            entry.state = GPUReadbackStagingState.Releasable
            entry.activeReservationId = null
            entry.ownerScope = null
            entry.activeLease = null
        }
        return GPUReadbackStagingRollbackResult(
            releasedReservationIds = immutableList(listOf(lease.reservationId)),
            resourceRefs = immutableList(listOf(lease.resourceRef)),
        )
    }

    fun markMapped(lease: GPUReadbackStagingLease): GPUReadbackStagingLifecycleResult =
        transition(
            lease = lease,
            expected = GPUReadbackStagingState.GPUCompletedMappingPending,
            next = GPUReadbackStagingState.Mapped,
            code = "unsupported.readback_staging.map_not_pending",
        )

    fun markDepadded(lease: GPUReadbackStagingLease): GPUReadbackStagingLifecycleResult =
        transition(
            lease = lease,
            expected = GPUReadbackStagingState.Mapped,
            next = GPUReadbackStagingState.Depadded,
            code = "unsupported.readback_staging.depad_not_mapped",
        )

    fun releaseAfterUnmap(lease: GPUReadbackStagingLease): GPUReadbackStagingLifecycleResult =
        transition(
            lease = lease,
            expected = GPUReadbackStagingState.Depadded,
            next = GPUReadbackStagingState.Releasable,
            code = "unsupported.readback_staging.release_not_depadded",
        )

    fun markMapFailed(
        lease: GPUReadbackStagingLease,
        safety: GPUReadbackMapFailureSafety,
    ): GPUReadbackStagingLifecycleResult {
        val entry = entryFor(lease) ?: return stagingLifecycleRefused(
            "unsupported.readback_staging.lease_unknown",
            "Readback staging lease is foreign or no longer active.",
        )
        if (entry.state !in setOf(
                GPUReadbackStagingState.GPUCompletedMappingPending,
                GPUReadbackStagingState.Mapped,
            )
        ) {
            return stagingLifecycleRefused(
                "unsupported.readback_staging.map_failure_not_pending",
                "Readback map failure is not legal in the current ownership state.",
            )
        }
        entry.state = when (safety) {
            GPUReadbackMapFailureSafety.SafeUnmapAndReleaseProven -> GPUReadbackStagingState.Releasable
            GPUReadbackMapFailureSafety.ReleaseUncertain,
            GPUReadbackMapFailureSafety.DeviceLost,
            -> GPUReadbackStagingState.Quarantined
        }
        entry.mapFailureSafety = safety
        if (entry.state == GPUReadbackStagingState.Releasable) {
            entry.submissionId = null
            val useToken = takeReadbackUseToken() ?: run {
                terminalizeReadback(entry)
                return stagingLifecycleRefused(
                    "unsupported.readback_staging.use_token_overflow",
                    "Readback staging cannot become reusable after use-token exhaustion.",
                )
            }
            entry.lastUseToken = useToken
        }
        return stagingLifecycleAccepted(listOf(entry))
    }

    fun stateOf(lease: GPUReadbackStagingLease): GPUReadbackStagingState {
        val entry = entries.firstOrNull { candidate -> candidate.resourceRef == lease.resourceRef }
            ?: return GPUReadbackStagingState.Invalidated
        return if (entry.activeLease !== lease) GPUReadbackStagingState.Invalidated else entry.state
    }

    fun snapshotOf(lease: GPUReadbackStagingLease): GPUReadbackStagingEntrySnapshot {
        val entry = entries.firstOrNull { candidate -> candidate.resourceRef == lease.resourceRef }
        return if (entry == null || entry.activeLease !== lease) {
            GPUReadbackStagingEntrySnapshot(
                resourceRef = lease.resourceRef,
                state = GPUReadbackStagingState.Invalidated,
                deviceGeneration = lease.deviceGeneration,
                backingBufferBytes = lease.backingBufferBytes,
                lastUseToken = 0,
                completionFailure = null,
                mapFailureSafety = null,
            )
        } else {
            entry.snapshot()
        }
    }

    fun evictReclaimableUntil(residentBytesAtMost: Long): List<GPUReadbackStagingEntrySnapshot> {
        require(residentBytesAtMost >= 0L) { "residentBytesAtMost must be non-negative" }
        val evicted = mutableListOf<GPUReadbackStagingEntrySnapshot>()
        val candidates = entries
            .filter { entry -> entry.state == GPUReadbackStagingState.Releasable }
            .sortedWith(compareBy<ReadbackEntry>({ it.lastUseToken }, { it.resourceRef.value }))
        for (entry in candidates) {
            if (telemetry().residentBytes <= residentBytesAtMost) break
            entry.state = GPUReadbackStagingState.Evicted
            entry.activeLease = null
            budgetLedger.remove(entry.allocationId)
            evicted += entry.snapshot()
            entries.remove(entry)
        }
        return immutableList(evicted)
    }

    internal fun oldestReclaimableCandidate(): GPUPhysicalPoolEvictionCandidate? = entries
        .asSequence()
        .filter { entry -> entry.state == GPUReadbackStagingState.Releasable }
        .minWithOrNull(compareBy<ReadbackEntry>({ it.lastUseToken }, { it.resourceRef.value }))
        ?.let { entry ->
            GPUPhysicalPoolEvictionCandidate(
                category = GPUFrameMemoryCategory.ReadbackStaging,
                resourceId = entry.resourceRef.value,
                lastUseToken = entry.lastUseToken,
                physicalBytes = entry.backingBufferBytes,
            )
        }

    internal fun reclaimableCandidates(): List<GPUPhysicalPoolEvictionCandidate> = immutableList(
        entries.mapNotNull { entry ->
            if (entry.state != GPUReadbackStagingState.Releasable) {
                null
            } else {
                GPUPhysicalPoolEvictionCandidate(
                    category = GPUFrameMemoryCategory.ReadbackStaging,
                    resourceId = entry.resourceRef.value,
                    lastUseToken = entry.lastUseToken,
                    physicalBytes = entry.backingBufferBytes,
                )
            }
        },
    )

    internal fun evictCandidate(resourceId: String): GPUReadbackStagingEntrySnapshot? {
        val entry = entries.firstOrNull { candidate ->
            candidate.resourceRef.value == resourceId &&
                candidate.state == GPUReadbackStagingState.Releasable
        } ?: return null
        entry.state = GPUReadbackStagingState.Evicted
        budgetLedger.remove(entry.allocationId)
        val snapshot = entry.snapshot()
        entry.activeLease = null
        entries.remove(entry)
        return snapshot
    }

    fun invalidateGenerationsBefore(
        currentGeneration: GPUDeviceGenerationID,
    ): List<GPUReadbackStagingEntrySnapshot> {
        val invalidated = entries.filter { entry ->
            entry.deviceGeneration.value < currentGeneration.value && !entry.terminal()
        }
        invalidated.forEach { entry ->
            entry.state = GPUReadbackStagingState.Invalidated
            entry.activeLease = null
            entry.activeReservationId = null
            entry.ownerScope = null
            entry.submissionId = null
            budgetLedger.remove(entry.allocationId)
        }
        val snapshots = invalidated.map(ReadbackEntry::snapshot)
        submissions.entries.removeAll { (_, submittedEntries) ->
            submittedEntries.any { entry -> entry in invalidated }
        }
        entries.removeAll(invalidated.toSet())
        return immutableList(snapshots)
    }

    fun telemetry(): GPUReadbackStagingPoolTelemetry {
        val resident = entries.filterNot(ReadbackEntry::terminal)
        val reserved = resident.filter { it.state == GPUReadbackStagingState.Reserved }
        val inFlight = resident.filter { it.state == GPUReadbackStagingState.Submitted }
        val mappingOwned = resident.filter { entry ->
            entry.state in setOf(
                GPUReadbackStagingState.GPUCompletedMappingPending,
                GPUReadbackStagingState.Mapped,
                GPUReadbackStagingState.Depadded,
            )
        }
        val reclaimable = resident.filter { it.state == GPUReadbackStagingState.Releasable }
        val quarantined = resident.filter { it.state == GPUReadbackStagingState.Quarantined }
        val categoryTotals = GPUFrameMemoryCategory.entries.associateWith { category ->
            if (category == GPUFrameMemoryCategory.ReadbackStaging) resident.readbackBytes() else 0L
        }
        return GPUReadbackStagingPoolTelemetry(
            residentBytes = resident.readbackBytes(),
            reservedBytes = reserved.readbackBytes(),
            inFlightBytes = inFlight.readbackBytes(),
            mappingOwnedBytes = mappingOwned.readbackBytes(),
            reclaimableBytes = reclaimable.readbackBytes(),
            quarantinedBytes = quarantined.readbackBytes(),
            categoryTotals = immutableMap(categoryTotals),
        )
    }

    private fun transition(
        lease: GPUReadbackStagingLease,
        expected: GPUReadbackStagingState,
        next: GPUReadbackStagingState,
        code: String,
    ): GPUReadbackStagingLifecycleResult {
        val entry = entryFor(lease) ?: return stagingLifecycleRefused(
            "unsupported.readback_staging.lease_unknown",
            "Readback staging lease is foreign or no longer active.",
        )
        if (entry.state != expected) {
            return stagingLifecycleRefused(
                code,
                "Readback staging transition requires state $expected but observed ${entry.state}.",
            )
        }
        entry.state = next
        if (next == GPUReadbackStagingState.Releasable) {
            entry.submissionId = null
            val useToken = takeReadbackUseToken() ?: run {
                terminalizeReadback(entry)
                return stagingLifecycleRefused(
                    "unsupported.readback_staging.use_token_overflow",
                    "Readback staging cannot become reusable after use-token exhaustion.",
                )
            }
            entry.lastUseToken = useToken
        }
        return stagingLifecycleAccepted(listOf(entry))
    }

    private fun entryFor(lease: GPUReadbackStagingLease): ReadbackEntry? =
        entries.firstOrNull { entry ->
            entry.resourceRef == lease.resourceRef && entry.activeLease === lease
        }

    private fun terminalizeReadback(entry: ReadbackEntry) {
        entry.state = GPUReadbackStagingState.Invalidated
        entry.activeLease = null
        entry.activeReservationId = null
        entry.ownerScope = null
        entry.submissionId = null
        budgetLedger.remove(entry.allocationId)
        entries.remove(entry)
    }

    private fun stagingLifecycleAccepted(
        entries: List<ReadbackEntry>,
    ): GPUReadbackStagingLifecycleResult.Accepted =
        GPUReadbackStagingLifecycleResult.Accepted(
            immutableList(entries.map(ReadbackEntry::resourceRef).distinct()),
        )

    private fun stagingLifecycleRefused(
        code: String,
        message: String,
    ): GPUReadbackStagingLifecycleResult.Refused =
        GPUReadbackStagingLifecycleResult.Refused(
            readbackStagingDiagnostic(code, message, emptyMap()),
        )

    private fun readbackBudgetRefusal(
        budget: GPUFrameMemoryBudgetPlan,
        requestedBytes: Long,
    ): GPUDiagnostic? {
        if (budget.diagnostic != null) {
            return readbackStagingDiagnostic(
                "unsupported.readback_staging.frame_budget_invalid",
                "Readback staging cannot consume an already refused frame budget.",
                mapOf("frameBudgetCode" to budget.diagnostic.code.value),
            )
        }
        val telemetry = telemetry()
        val categoryTotals = GPUFrameMemoryCategory.entries.associateWith { category ->
            when (category) {
                GPUFrameMemoryCategory.ReusableScratch ->
                    budgetLedger.residentBytes(category).toBigInteger()
                GPUFrameMemoryCategory.ReadbackStaging ->
                    budgetLedger.residentBytes(category).toBigInteger() + requestedBytes.toBigInteger()
                else -> (budget.categoryTotals[category] ?: 0L).toBigInteger()
            }
        }
        val aggregate = categoryTotals.values.fold(BigInteger.ZERO, BigInteger::add)
        if (aggregate <= budget.configuredAggregateBudgetBytes.toBigInteger()) {
            return null
        }
        val facts = linkedMapOf(
            "requestedBackingBytes" to requestedBytes.toString(),
            "residentBytes" to telemetry.residentBytes.toString(),
            "reservedBytes" to telemetry.reservedBytes.toString(),
            "inFlightBytes" to telemetry.inFlightBytes.toString(),
            "mappingOwnedBytes" to telemetry.mappingOwnedBytes.toString(),
            "reclaimableBytes" to telemetry.reclaimableBytes.toString(),
            "quarantinedBytes" to telemetry.quarantinedBytes.toString(),
            "aggregatePeakBytes" to aggregate.toString(),
            "configuredAggregateBudgetBytes" to budget.configuredAggregateBudgetBytes.toString(),
        )
        GPUFrameMemoryCategory.entries.forEach { category ->
            facts["category.${category.name}"] = categoryTotals.getValue(category).toString()
        }
        return readbackStagingDiagnostic(
            "unsupported.readback_staging.aggregate_budget_exceeded",
            "Physical readback staging backing exceeds the aggregate frame budget.",
            facts,
        )
    }

    private data class ReadbackEntry(
        val resourceRef: GPUBufferResourceRef,
        val deviceGeneration: GPUDeviceGenerationID,
        val backingBufferBytes: Long,
        val usages: Set<GPUFrameResourceUsage>,
        var state: GPUReadbackStagingState,
        var lastUseToken: Long,
        var activeReservationId: String? = null,
        var ownerScope: String? = null,
        var submissionId: GPUResourceSubmissionID? = null,
        var activeLease: GPUReadbackStagingLease? = null,
        var completionFailure: GPUReadbackCompletionFailure? = null,
        var mapFailureSafety: GPUReadbackMapFailureSafety? = null,
    ) {
        val allocationId: String = "readback:${resourceRef.value}"

        fun terminal(): Boolean =
            state == GPUReadbackStagingState.Evicted || state == GPUReadbackStagingState.Invalidated

        fun snapshot(): GPUReadbackStagingEntrySnapshot = GPUReadbackStagingEntrySnapshot(
            resourceRef = resourceRef,
            state = state,
            deviceGeneration = deviceGeneration,
            backingBufferBytes = backingBufferBytes,
            lastUseToken = lastUseToken,
            completionFailure = completionFailure,
            mapFailureSafety = mapFailureSafety,
        )
    }

    private fun takeResourceOrdinal(): Long? {
        if (nextResourceOrdinal == Long.MAX_VALUE) return null
        return nextResourceOrdinal.also { nextResourceOrdinal += 1L }
    }

    private fun takeReservationOrdinal(): Long? {
        if (nextReservationOrdinal == Long.MAX_VALUE) return null
        return nextReservationOrdinal.also { nextReservationOrdinal += 1L }
    }

    private fun takeReadbackUseToken(): Long? {
        return budgetLedger.takeUseToken()
    }

    private fun stagingReservationRefused(
        code: String,
        message: String,
    ): GPUReadbackStagingReservationResult.Refused =
        GPUReadbackStagingReservationResult.Refused(
            readbackStagingDiagnostic(code, message, emptyMap()),
        )

    private fun List<ReadbackEntry>.readbackBytes(): Long =
        fold(0L) { total, entry -> Math.addExact(total, entry.backingBufferBytes) }
}

private fun readbackStagingDiagnostic(
    code: String,
    message: String,
    facts: Map<String, String>,
): GPUDiagnostic = diagnostic(code, message, facts)

private data class ScratchTextureFormatInfo(
    val format: GPUTextureFormat,
    val footprint: GPUTexturePhysicalFootprint,
)

private fun scratchFormatInfoFor(format: GPUColorFormat): ScratchTextureFormatInfo? = when (format.value) {
    "r8unorm" -> ScratchTextureFormatInfo(
        GPUTextureFormat.R8Unorm,
        GPUTexturePhysicalFootprint(1, 1, 1),
    )
    "rgba8unorm" ->
        ScratchTextureFormatInfo(GPUTextureFormat.RGBA8Unorm, GPUTexturePhysicalFootprint(1, 1, 4))
    "rgba8unorm-srgb" ->
        ScratchTextureFormatInfo(GPUTextureFormat.RGBA8UnormSrgb, GPUTexturePhysicalFootprint(1, 1, 4))
    "bgra8unorm" ->
        ScratchTextureFormatInfo(GPUTextureFormat.BGRA8Unorm, GPUTexturePhysicalFootprint(1, 1, 4))
    "bgra8unorm-srgb" ->
        ScratchTextureFormatInfo(GPUTextureFormat.BGRA8UnormSrgb, GPUTexturePhysicalFootprint(1, 1, 4))
    else -> null
}

private fun GPUFrameResourceUsage.toGPUTextureUsage(): GPUTextureUsage = when (this) {
    GPUFrameResourceUsage.RenderAttachment -> GPUTextureUsage.RenderAttachment
    GPUFrameResourceUsage.TextureBinding -> GPUTextureUsage.TextureBinding
    GPUFrameResourceUsage.StorageBinding -> GPUTextureUsage.StorageBinding
    GPUFrameResourceUsage.CopySource -> GPUTextureUsage.CopySrc
    GPUFrameResourceUsage.CopyDestination -> GPUTextureUsage.CopyDst
    GPUFrameResourceUsage.Vertex,
    GPUFrameResourceUsage.Index,
    GPUFrameResourceUsage.Uniform,
    GPUFrameResourceUsage.Storage,
    GPUFrameResourceUsage.MapRead,
    -> GPUTextureUsage.None
}

private val SCRATCH_TEXTURE_USAGES: Set<GPUFrameResourceUsage> = setOf(
    GPUFrameResourceUsage.RenderAttachment,
    GPUFrameResourceUsage.TextureBinding,
    GPUFrameResourceUsage.StorageBinding,
    GPUFrameResourceUsage.CopySource,
    GPUFrameResourceUsage.CopyDestination,
)

private fun normalizeApproxSize(value: Int): Int? {
    if (value <= 0) return null
    val clamped = maxOf(16, value)
    if (clamped and (clamped - 1) == 0) return clamped
    val floorPowerOfTwo = Integer.highestOneBit(clamped)
    val ceilingPowerOfTwo = floorPowerOfTwo.toLong() shl 1
    if (ceilingPowerOfTwo > Int.MAX_VALUE) return null
    if (clamped <= 1024) return ceilingPowerOfTwo.toInt()
    val midpoint = floorPowerOfTwo.toLong() + floorPowerOfTwo.toLong() / 2L
    return if (clamped.toLong() <= midpoint) midpoint.toInt() else ceilingPowerOfTwo.toInt()
}

private fun physicalBytes(
    width: Int,
    height: Int,
    footprint: GPUTexturePhysicalFootprint,
    sampleCount: Int,
): Long? = try {
    val blocksWide = ceilDiv(width.toLong(), footprint.blockWidth.toLong())
    val blocksHigh = ceilDiv(height.toLong(), footprint.blockHeight.toLong())
    Math.multiplyExact(
        Math.multiplyExact(
            Math.multiplyExact(blocksWide, blocksHigh),
            footprint.bytesPerBlock.toLong(),
        ),
        sampleCount.toLong(),
    )
} catch (_: ArithmeticException) {
    null
}

private fun ceilDiv(value: Long, divisor: Long): Long =
    value / divisor + if (value % divisor == 0L) 0L else 1L

private fun diagnostic(
    code: String,
    message: String,
    facts: Map<String, String>,
): GPUDiagnostic = GPUDiagnostic(
    code = GPUDiagnosticCode(code),
    domain = GPUDiagnosticDomain.Resources,
    severity = GPUDiagnosticSeverity.Error,
    message = message,
    facts = immutableMap(facts),
)

internal fun unconfiguredPoolDiagnostic(
    code: String,
    message: String,
): GPUDiagnostic = diagnostic(code, message, emptyMap())

internal fun physicalPoolDiagnostic(
    code: String,
    message: String,
    facts: Map<String, String>,
): GPUDiagnostic = diagnostic(code, message, facts)
