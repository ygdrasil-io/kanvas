package org.graphiks.kanvas.gpu.renderer.resources

import java.math.BigInteger
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID

/**
 * Resource-owned input for the sole frame-preflight materialization boundary.
 *
 * This contract intentionally lives in `resources`: the resource package never imports the
 * recording or execution packages. The execution preflighter supplies a Task 6 preparation
 * request plus the already validated frame facts.
 */
class GPUFrameResourcePreparationInput(
    val preparation: GPUResourcePreparationRequest,
    val ownerScope: String,
    val deviceGeneration: GPUDeviceGenerationID,
    val resourceGeneration: Long,
    val firstStep: Int,
    val lastStepExclusive: Int,
    val budgetPlan: GPUFrameMemoryBudgetPlan,
    val capabilities: GPUCapabilities,
    val readbackStagingDescriptor: GPUReadbackStagingDescriptorContract? = null,
) {
    init {
        require(ownerScope.isNotBlank()) { "GPUFrameResourcePreparationInput.ownerScope must not be blank" }
        require(resourceGeneration >= 0L) {
            "GPUFrameResourcePreparationInput.resourceGeneration must be non-negative"
        }
        require(firstStep >= 0) { "GPUFrameResourcePreparationInput.firstStep must be non-negative" }
        require(lastStepExclusive > firstStep) {
            "GPUFrameResourcePreparationInput.lastStepExclusive must be greater than firstStep"
        }
        require(
            readbackStagingDescriptor == null || preparation.role == GPUFrameResourceRole.ReadbackStaging,
        ) {
            "A readback staging descriptor requires a ReadbackStaging preparation"
        }
    }
}

/** Handle-free result of one resource-owned frame preparation. */
sealed interface GPUPreparedConcreteResourceRef {
    val value: String

    data class Texture(val ref: GPUTextureResourceRef) : GPUPreparedConcreteResourceRef {
        override val value: String get() = ref.value

        init {
            requireResourceDumpSafe("GPUPreparedConcreteResourceRef.Texture.ref", ref.value)
        }
    }

    data class Buffer(val ref: GPUBufferResourceRef) : GPUPreparedConcreteResourceRef {
        override val value: String get() = ref.value

        init {
            requireResourceDumpSafe("GPUPreparedConcreteResourceRef.Buffer.ref", ref.value)
        }
    }
}

sealed interface GPUFrameResourcePreparationDecision {
    class Prepared(
        val logicalResource: GPUFrameResourceRef,
        val concreteResource: GPUPreparedConcreteResourceRef,
        val role: GPUFrameResourceRole,
        val deviceGeneration: GPUDeviceGenerationID,
        val resourceGeneration: Long,
        val outputOwnedReadbackLease: GPUReadbackStagingLease? = null,
        val textureAllocation: GPUPreparedTextureAllocationEvidence? = null,
    ) : GPUFrameResourcePreparationDecision {
        init {
            require(resourceGeneration >= 0L) {
                "GPUFrameResourcePreparationDecision.Prepared.resourceGeneration must be non-negative"
            }
            require(
                outputOwnedReadbackLease == null || role == GPUFrameResourceRole.ReadbackStaging,
            ) {
                "Only a ReadbackStaging resource may carry output-owned readback state"
            }
            require(textureAllocation == null || concreteResource is GPUPreparedConcreteResourceRef.Texture) {
                "Only a prepared texture may carry logical-to-physical allocation evidence"
            }
            require(textureAllocation == null || role != GPUFrameResourceRole.ReadbackStaging) {
                "Readback staging cannot carry texture allocation evidence"
            }
        }
    }

    data class Refused(val diagnostic: org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic) :
        GPUFrameResourcePreparationDecision
}

/**
 * Resource-side preflight facade. Implementations own their acquisition journal and must release
 * every never-submitted physical reservation in global reverse acquisition order.
 */
interface GPUFrameResourcePreflightProvider : GPUResourceProvider {
    fun beginFramePreparation(
        frameId: Long,
        deviceGeneration: GPUDeviceGenerationID,
    ): GPUFrameResourcePreparationSession

    fun prepareFrameResource(input: GPUFrameResourcePreparationInput): GPUFrameResourcePreparationDecision

    fun rollbackFrameResourcesBeforeSubmit(
        ownerScope: String,
    ): GPUPhysicalPoolMaintenanceDecision<GPUPhysicalPoolRollbackSummary>
}

/** Provider-issued journal namespace unique across all preflighters sharing that provider. */
@JvmInline
value class GPUFrameResourcePreparationSession(val ownerScope: String) {
    init {
        require(ownerScope.isNotBlank()) { "GPUFrameResourcePreparationSession.ownerScope must not be blank" }
        requireResourceDumpSafe("GPUFrameResourcePreparationSession.ownerScope", ownerScope)
    }
}

data class GPUNullBufferMaterializationRequest(
    val label: String,
    val byteSize: Long,
    val deviceGeneration: Long,
) {
    init {
        require(label.isNotBlank()) { "GPUNullBufferMaterializationRequest.label must not be blank" }
        requireResourceDumpSafe(
            fieldName = "GPUNullBufferMaterializationRequest.label",
            value = label,
        )
        require(byteSize > 0L) { "GPUNullBufferMaterializationRequest.byteSize must be positive" }
        require(deviceGeneration >= 0L) {
            "GPUNullBufferMaterializationRequest.deviceGeneration must be non-negative"
        }
    }
}

data class GPUConcreteResourceProviderEvent(
    val lane: String,
    val result: String,
    val keyHash: String,
    val subjectHash: String,
) {
    init {
        require(lane.isNotBlank()) { "GPUConcreteResourceProviderEvent.lane must not be blank" }
        require(result.isNotBlank()) { "GPUConcreteResourceProviderEvent.result must not be blank" }
        require(keyHash.isNotBlank()) { "GPUConcreteResourceProviderEvent.keyHash must not be blank" }
        require(subjectHash.isNotBlank()) { "GPUConcreteResourceProviderEvent.subjectHash must not be blank" }
        requireResourceDumpSafe("GPUConcreteResourceProviderEvent.lane", lane)
        requireResourceDumpSafe("GPUConcreteResourceProviderEvent.result", result)
        requireResourceDumpSafe("GPUConcreteResourceProviderEvent.keyHash", keyHash)
        requireResourceDumpSafe("GPUConcreteResourceProviderEvent.subjectHash", subjectHash)
    }
}

class GPUConcreteResourceProviderTelemetry(
    events: List<GPUConcreteResourceProviderEvent> = emptyList(),
) {
    val dumpEvents: List<GPUConcreteResourceProviderEvent> = events.toList()

    fun plus(event: GPUConcreteResourceProviderEvent): GPUConcreteResourceProviderTelemetry =
        GPUConcreteResourceProviderTelemetry(dumpEvents + event)

    fun dumpLines(): List<String> =
        dumpEvents.map { event ->
            "resource-provider.cache lane=${event.lane} result=${event.result} " +
                "key=${event.keyHash} subject=${event.subjectHash}"
        }
}

class GPUConcreteResourceProvider(
    private val commandOperandProvider: GPUResourceProvider = ValidatingCommandOperandResourceProvider(),
    private val payloadProvider: GPUResourceProvider = ValidatingPayloadResourceProvider(),
    private val textureSamplerProvider: GPUResourceProvider = ValidatingTextureSamplerResourceProvider(),
    private val leaseFactory: GPUResourceLeaseFactory = EvidenceOnlyGPUResourceLeaseFactory,
) : GPUFrameResourcePreflightProvider {
    internal fun isBackedBy(factory: GPUResourceLeaseFactory): Boolean = leaseFactory === factory

    private val physicalPoolBudgetLedger = GPUPhysicalPoolBudgetLedger()
    private val scratchTexturePool = GPUScratchTexturePool(physicalPoolBudgetLedger)
    private val readbackStagingPool = GPUReadbackStagingPool(physicalPoolBudgetLedger)
    private val pendingPhysicalReservations = mutableListOf<GPUProviderPhysicalReservation>()
    private var framePreparationOrdinal: Long = 0
    internal val pendingPhysicalReservationCount: Int
        @Synchronized get() = pendingPhysicalReservations.size
    private val nullBufferKeys = linkedSetOf<String>()
    private val uniformSlabLeases = linkedMapOf<GPUUniformSlabLeaseCacheKey, GPUResourceLease>()
    private val bindGroupLeases = linkedMapOf<GPUBindGroupLeaseCacheKey, GPUResourceLease>()
    private val textureSamplerLeaseKeys = linkedSetOf<GPUTextureSamplerLeaseCacheKey>()
    private val intermediateTextureLeases = linkedMapOf<GPUIntermediateTextureLeaseCacheKey, GPUResourceLease>()
    private var mutableTelemetry = GPUConcreteResourceProviderTelemetry()

    val telemetry: GPUConcreteResourceProviderTelemetry
        get() = mutableTelemetry

    @Synchronized
    override fun reserveScratchTexture(
        request: GPUScratchTextureReservationRequest,
        budget: GPUFrameMemoryBudgetPlan,
        capabilities: GPUCapabilities,
    ): GPUScratchTextureReservationResult {
        var result = scratchTexturePool.reserve(request, budget, capabilities)
        if (result.isAggregateBudgetRefusal()) {
            purgeForAdmission((result as GPUScratchTextureReservationResult.Refused).diagnostic)
            result = scratchTexturePool.reserve(request, budget, capabilities)
        }
        if (result is GPUScratchTextureReservationResult.Accepted) {
            journalPhysicalReservation(
                GPUProviderPhysicalReservation.Scratch(
                    acquisitionOrdinal = result.lease.acquisitionToken,
                    lease = result.lease,
                ),
            )
        }
        record(
            lane = "scratch-texture",
            result = if (result is GPUScratchTextureReservationResult.Accepted) "reserve" else "refuse",
            keyHash = "physical-pool",
            subjectHash = "reservation",
        )
        return result
    }

    @Synchronized
    override fun beginFramePreparation(
        frameId: Long,
        deviceGeneration: GPUDeviceGenerationID,
    ): GPUFrameResourcePreparationSession {
        require(frameId >= 0L) { "frameId must be non-negative" }
        val ordinal = Math.incrementExact(framePreparationOrdinal)
        framePreparationOrdinal = ordinal
        return GPUFrameResourcePreparationSession(
            "frame-preflight:$frameId:device:${deviceGeneration.value}:attempt:$ordinal",
        )
    }

    @Synchronized
    override fun markScratchSubmitted(
        reservationScope: String,
        submissionId: GPUResourceSubmissionID,
        deviceGeneration: GPUDeviceGenerationID,
    ): GPUScratchLifecycleResult =
        scratchTexturePool.markSubmitted(reservationScope, submissionId, deviceGeneration).also { result ->
            if (result is GPUScratchLifecycleResult.Accepted) {
                pendingPhysicalReservations.removeAll { pending ->
                    pending is GPUProviderPhysicalReservation.Scratch &&
                        pending.lease.reservationScope == reservationScope &&
                        pending.lease.deviceGeneration == deviceGeneration
                }
            }
            recordScratchLifecycle("submit", result)
        }

    @Synchronized
    override fun acceptScratchCompletion(
        submissionId: GPUResourceSubmissionID,
        deviceGeneration: GPUDeviceGenerationID,
    ): GPUScratchLifecycleResult =
        scratchTexturePool.acceptCompletion(submissionId, deviceGeneration).also { result ->
            recordScratchLifecycle("complete", result)
        }

    @Synchronized
    override fun rejectScratchCompletion(
        submissionId: GPUResourceSubmissionID,
        deviceGeneration: GPUDeviceGenerationID,
        failure: GPUScratchCompletionFailure,
    ): GPUScratchLifecycleResult =
        scratchTexturePool.rejectCompletion(submissionId, deviceGeneration, failure).also { result ->
            recordScratchLifecycle("quarantine", result)
        }

    @Synchronized
    override fun reserveReadbackStaging(
        request: GPUReadbackStagingReservationRequest,
    ): GPUReadbackStagingReservationResult {
        var result = readbackStagingPool.reserve(request)
        if (result.isAggregateBudgetRefusal()) {
            purgeForAdmission((result as GPUReadbackStagingReservationResult.Refused).diagnostic)
            result = readbackStagingPool.reserve(request)
        }
        if (result is GPUReadbackStagingReservationResult.Accepted) {
            journalPhysicalReservation(
                GPUProviderPhysicalReservation.Readback(
                    acquisitionOrdinal = result.lease.acquisitionToken,
                    lease = result.lease,
                ),
            )
        }
        record(
            lane = "readback-staging",
            result = if (result is GPUReadbackStagingReservationResult.Accepted) "reserve" else "refuse",
            keyHash = "physical-pool",
            subjectHash = "reservation",
        )
        return result
    }

    @Synchronized
    override fun prepareFrameResource(
        input: GPUFrameResourcePreparationInput,
    ): GPUFrameResourcePreparationDecision {
        val preparation = input.preparation
        val readbackDescriptor = input.readbackStagingDescriptor
        if (readbackDescriptor != null) {
            val bufferDescriptor = preparation.descriptor as? GPUFrameBufferDescriptor
                ?: return GPUFrameResourcePreparationDecision.Refused(
                    physicalPoolDiagnostic(
                        code = "unsupported.readback_staging.buffer_descriptor_missing",
                        message = "Readback staging requires a buffer descriptor.",
                        facts = emptyMap(),
                    ),
                )
            if (bufferDescriptor.byteSize < readbackDescriptor.minimumBufferBytes ||
                bufferDescriptor.byteSize > readbackDescriptor.maxBufferSize
            ) {
                return GPUFrameResourcePreparationDecision.Refused(
                    physicalPoolDiagnostic(
                        code = "unsupported.readback_staging.backing_size_invalid",
                        message = "Readback backing size must contain the logical minimum and fit maxBufferSize.",
                        facts = mapOf(
                            "backingBufferBytes" to bufferDescriptor.byteSize.toString(),
                            "minimumBufferBytes" to readbackDescriptor.minimumBufferBytes.toString(),
                            "maxBufferSize" to readbackDescriptor.maxBufferSize.toString(),
                        ),
                    ),
                )
            }
            val reservation = reserveReadbackStaging(
                GPUReadbackStagingReservationRequest(
                    reservationId = "${input.ownerScope}:${preparation.resource.value}",
                    ownerScope = input.ownerScope,
                    deviceGeneration = input.deviceGeneration,
                    descriptor = readbackDescriptor,
                    backingBufferBytes = bufferDescriptor.byteSize,
                    budgetPlan = input.budgetPlan,
                ),
            )
            return when (reservation) {
                is GPUReadbackStagingReservationResult.Accepted ->
                    GPUFrameResourcePreparationDecision.Prepared(
                        logicalResource = preparation.resource,
                        concreteResource = GPUPreparedConcreteResourceRef.Buffer(reservation.lease.resourceRef),
                        role = preparation.role,
                        deviceGeneration = input.deviceGeneration,
                        resourceGeneration = input.resourceGeneration,
                        outputOwnedReadbackLease = reservation.lease,
                    )
                is GPUReadbackStagingReservationResult.Refused ->
                    GPUFrameResourcePreparationDecision.Refused(reservation.diagnostic)
            }
        }

        if (preparation.resource is GPUFrameTextureRef &&
            preparation.role in setOf(
                GPUFrameResourceRole.DestinationSnapshot,
                GPUFrameResourceRole.CopyScratch,
                GPUFrameResourceRole.LayerTarget,
                GPUFrameResourceRole.FilterTarget,
            ) &&
            preparation.lifetime != GPUFrameResourceLifetime.ImportedExternal &&
            preparation.lifetime != GPUFrameResourceLifetime.SurfaceLease
        ) {
            val reservation = reserveScratchTexture(
                request = GPUScratchTextureReservationRequest(
                    reservationId = "${input.ownerScope}:${preparation.resource.value}",
                    reservationScope = input.ownerScope,
                    preparation = preparation,
                    deviceGeneration = input.deviceGeneration,
                    firstStep = input.firstStep,
                    lastStepExclusive = input.lastStepExclusive,
                ),
                budget = input.budgetPlan,
                capabilities = input.capabilities,
            )
            return when (reservation) {
                is GPUScratchTextureReservationResult.Accepted ->
                    GPUFrameResourcePreparationDecision.Prepared(
                        logicalResource = preparation.resource,
                        concreteResource = GPUPreparedConcreteResourceRef.Texture(reservation.lease.resourceRef),
                        role = preparation.role,
                        deviceGeneration = input.deviceGeneration,
                        resourceGeneration = input.resourceGeneration,
                        textureAllocation = GPUPreparedTextureAllocationEvidence(
                            logicalBounds = reservation.lease.logicalBounds,
                            backingWidth = reservation.lease.backingWidth,
                            backingHeight = reservation.lease.backingHeight,
                            format = reservation.lease.key.format,
                            sampleCount = reservation.lease.key.sampleCount,
                            usages = reservation.lease.usages,
                        ),
                    )
                is GPUScratchTextureReservationResult.Refused ->
                    GPUFrameResourcePreparationDecision.Refused(reservation.diagnostic)
            }
        }

        if (preparation.role == GPUFrameResourceRole.ReadbackStaging) {
            return GPUFrameResourcePreparationDecision.Refused(
                physicalPoolDiagnostic(
                    code = "unsupported.readback_staging.layout_missing",
                    message = "Readback staging requires a validated readback layout descriptor.",
                    facts = emptyMap(),
                ),
            )
        }

        val concreteResource = when (preparation.resource) {
            is GPUFrameBufferRef -> GPUPreparedConcreteResourceRef.Buffer(
                GPUBufferResourceRef("frame-resource:${preparation.resource.value}"),
            )
            is GPUFrameTextureRef, is GPUFrameTargetRef -> GPUPreparedConcreteResourceRef.Texture(
                GPUTextureResourceRef("frame-resource:${preparation.resource.value}"),
            )
        }
        return GPUFrameResourcePreparationDecision.Prepared(
            logicalResource = preparation.resource,
            concreteResource = concreteResource,
            role = preparation.role,
            deviceGeneration = input.deviceGeneration,
            resourceGeneration = input.resourceGeneration,
        )
    }

    override fun materializeCommandOperands(
        request: GPUCommandOperandMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision =
        commandOperandProvider.materializeCommandOperands(request, context)

    @Synchronized
    override fun rollbackFrameResourcesBeforeSubmit(
        ownerScope: String,
    ): GPUPhysicalPoolMaintenanceDecision<GPUPhysicalPoolRollbackSummary> =
        rollbackPhysicalPoolsBeforeSubmit(ownerScope)

    @Synchronized
    override fun markReadbackSubmitted(
        leases: List<GPUReadbackStagingLease>,
        submissionId: GPUResourceSubmissionID,
    ): GPUReadbackStagingLifecycleResult =
        readbackStagingPool.markSubmitted(leases, submissionId).also { result ->
            if (result is GPUReadbackStagingLifecycleResult.Accepted) {
                pendingPhysicalReservations.removeAll { pending ->
                    pending is GPUProviderPhysicalReservation.Readback &&
                        leases.any { lease -> lease === pending.lease }
                }
            }
            recordReadbackLifecycle("submit", result)
        }

    @Synchronized
    override fun acceptReadbackGPUCompletion(
        submissionId: GPUResourceSubmissionID,
        deviceGeneration: GPUDeviceGenerationID,
    ): GPUReadbackStagingLifecycleResult =
        readbackStagingPool.acceptGPUCompletion(submissionId, deviceGeneration).also { result ->
            recordReadbackLifecycle("complete", result)
        }

    @Synchronized
    override fun rejectReadbackGPUCompletion(
        submissionId: GPUResourceSubmissionID,
        deviceGeneration: GPUDeviceGenerationID,
        failure: GPUReadbackCompletionFailure,
    ): GPUReadbackStagingLifecycleResult =
        readbackStagingPool.rejectGPUCompletion(submissionId, deviceGeneration, failure).also { result ->
            recordReadbackLifecycle("quarantine", result)
        }

    @Synchronized
    override fun markReadbackMapped(
        lease: GPUReadbackStagingLease,
    ): GPUReadbackStagingLifecycleResult =
        readbackStagingPool.markMapped(lease).also { result -> recordReadbackLifecycle("map", result) }

    @Synchronized
    override fun markReadbackDepadded(
        lease: GPUReadbackStagingLease,
    ): GPUReadbackStagingLifecycleResult =
        readbackStagingPool.markDepadded(lease).also { result -> recordReadbackLifecycle("depad", result) }

    @Synchronized
    override fun releaseReadbackAfterUnmap(
        lease: GPUReadbackStagingLease,
    ): GPUReadbackStagingLifecycleResult =
        readbackStagingPool.releaseAfterUnmap(lease).also { result ->
            recordReadbackLifecycle("release", result)
        }

    @Synchronized
    override fun markReadbackMapFailed(
        lease: GPUReadbackStagingLease,
        safety: GPUReadbackMapFailureSafety,
    ): GPUReadbackStagingLifecycleResult =
        readbackStagingPool.markMapFailed(lease, safety).also { result ->
            recordReadbackLifecycle("map-failure", result)
        }

    @Synchronized
    override fun quarantineReadbackAfterSubmit(
        lease: GPUReadbackStagingLease,
    ): GPUReadbackStagingLifecycleResult =
        readbackStagingPool.quarantineAfterSubmitFailure(lease).also { result ->
            if (result is GPUReadbackStagingLifecycleResult.Accepted) {
                pendingPhysicalReservations.removeAll { pending ->
                    pending is GPUProviderPhysicalReservation.Readback && pending.lease === lease
                }
            }
            recordReadbackLifecycle("fail-closed", result)
        }

    @Synchronized
    override fun rollbackPhysicalPoolsBeforeSubmit(
        ownerScope: String,
    ): GPUPhysicalPoolMaintenanceDecision<GPUPhysicalPoolRollbackSummary> {
        val pending = pendingPhysicalReservations
            .filter { reservation -> reservation.ownerScope == ownerScope }
            .sortedByDescending(GPUProviderPhysicalReservation::acquisitionOrdinal)
        val stale = pending.firstOrNull { reservation ->
            when (reservation) {
                is GPUProviderPhysicalReservation.Scratch ->
                    !scratchTexturePool.canRollbackBeforeSubmit(reservation.lease)
                is GPUProviderPhysicalReservation.Readback ->
                    !readbackStagingPool.canRollbackBeforeSubmit(reservation.lease)
            }
        }
        if (stale != null) {
            return GPUPhysicalPoolMaintenanceDecision.Refused(
                physicalPoolDiagnostic(
                    code = "unsupported.physical_pool.rollback_journal_stale",
                    message = "Physical pool rollback journal contains a reservation that is no longer rollbackable.",
                    facts = mapOf(
                        "ownerScope" to ownerScope,
                        "reservationId" to stale.reservationId,
                        "acquisitionOrdinal" to stale.acquisitionOrdinal.toString(),
                    ),
                ),
            )
        }

        val scratchReleasedIds = mutableListOf<String>()
        val scratchReleasedRefs = mutableListOf<GPUTextureResourceRef>()
        val readbackReleasedIds = mutableListOf<String>()
        val readbackReleasedRefs = mutableListOf<GPUBufferResourceRef>()
        val releaseOrder = mutableListOf<GPUPhysicalPoolRollbackRelease>()
        pending.forEach { reservation ->
            when (reservation) {
                is GPUProviderPhysicalReservation.Scratch -> {
                    val released = checkNotNull(scratchTexturePool.rollbackLeaseBeforeSubmit(reservation.lease))
                    scratchReleasedIds += released.releasedReservationIds
                    scratchReleasedRefs += released.resourceRefs
                    releaseOrder += GPUPhysicalPoolRollbackRelease.Scratch(
                        reservationId = reservation.lease.reservationId,
                        acquisitionOrdinal = reservation.acquisitionOrdinal,
                        resourceRef = reservation.lease.resourceRef,
                    )
                }
                is GPUProviderPhysicalReservation.Readback -> {
                    val released = checkNotNull(readbackStagingPool.rollbackLeaseBeforeSubmit(reservation.lease))
                    readbackReleasedIds += released.releasedReservationIds
                    readbackReleasedRefs += released.resourceRefs
                    releaseOrder += GPUPhysicalPoolRollbackRelease.Readback(
                        reservationId = reservation.lease.reservationId,
                        acquisitionOrdinal = reservation.acquisitionOrdinal,
                        resourceRef = reservation.lease.resourceRef,
                    )
                }
            }
            pendingPhysicalReservations.remove(reservation)
        }
        return GPUPhysicalPoolMaintenanceDecision.Applied(
            GPUPhysicalPoolRollbackSummary(
                scratch = GPUScratchRollbackResult(scratchReleasedIds.toList(), scratchReleasedRefs.toList()),
                readback = GPUReadbackStagingRollbackResult(
                    readbackReleasedIds.toList(),
                    readbackReleasedRefs.toList(),
                ),
                releaseOrder = releaseOrder,
            ),
        )
    }

    @Synchronized
    override fun invalidatePhysicalPoolsBefore(
        currentGeneration: GPUDeviceGenerationID,
    ): GPUPhysicalPoolMaintenanceDecision<GPUPhysicalPoolInvalidationSummary> {
        val summary = GPUPhysicalPoolInvalidationSummary(
            scratchEntries = scratchTexturePool.invalidateGenerationsBefore(currentGeneration),
            readbackEntries = readbackStagingPool.invalidateGenerationsBefore(currentGeneration),
        )
        pendingPhysicalReservations.removeAll { reservation ->
            reservation.deviceGeneration.value < currentGeneration.value
        }
        return GPUPhysicalPoolMaintenanceDecision.Applied(
            GPUPhysicalPoolInvalidationSummary(
                scratchEntries = summary.scratchEntries,
                readbackEntries = summary.readbackEntries,
            ),
        )
    }

    @Synchronized
    override fun evictPhysicalPoolsUntil(
        residentBytesAtMost: Long,
    ): GPUPhysicalPoolMaintenanceDecision<GPUPhysicalPoolEvictionSummary> {
        if (residentBytesAtMost < 0L) {
            return GPUPhysicalPoolMaintenanceDecision.Refused(
                physicalPoolDiagnostic(
                    code = "unsupported.physical_pool.eviction_target_invalid",
                    message = "Physical pool eviction target must be non-negative.",
                    facts = mapOf("residentBytesAtMost" to residentBytesAtMost.toString()),
                ),
            )
        }
        val plan = planSharedEviction(residentBytesAtMost)
            ?: return GPUPhysicalPoolMaintenanceDecision.Refused(
                physicalPoolDiagnostic(
                    code = "unsupported.physical_pool.insufficient_reclaimable_bytes",
                    message = "Shared physical pools cannot reach the requested resident target safely.",
                    facts = mapOf(
                        "residentBytesAtMost" to residentBytesAtMost.toString(),
                        "residentBytes" to physicalPoolBudgetLedger.managedResidentBytes().toString(),
                        "reclaimableBytes" to sharedReclaimableCandidates()
                            .sumOf(GPUPhysicalPoolEvictionCandidate::physicalBytes)
                            .toString(),
                    ),
                ),
            )
        return GPUPhysicalPoolMaintenanceDecision.Applied(applySharedEviction(plan))
    }

    fun materializeNullBuffer(
        request: GPUNullBufferMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        if (request.deviceGeneration != context.deviceGeneration) {
            val diagnostic = GPUResourceDiagnostic.deviceGenerationStale(
                resourceLabel = request.label,
                expectedDeviceGeneration = context.deviceGeneration,
                actualDeviceGeneration = request.deviceGeneration,
                resourceKind = "resource",
            )
            record("null-buffer", "stale-generation", request.label, context.targetId)
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = diagnostic,
                targetId = context.targetId,
                resourcePlanLabels = listOf(request.label),
            )
        }

        val key = "${context.deviceGeneration}:${request.label}:${request.byteSize}"
        val cacheResult = if (nullBufferKeys.add(key)) {
            GPUResourceLeaseCacheResult.Create
        } else {
            GPUResourceLeaseCacheResult.Reuse
        }
        record("null-buffer", cacheResult.dumpToken, key, context.targetId)
        val lease = GPUResourceLease(
            leaseId = "null-buffer:${request.label}",
            resourceKind = GPUResourceLeaseKind.NullBuffer,
            deviceGeneration = context.deviceGeneration,
            descriptorHash = "null-buffer:${request.byteSize}",
            ownerScope = "resource-provider:null-buffer",
            usageLabels = listOf("uniform"),
            releasePolicy = "device-generation",
            cacheResult = cacheResult,
            evidenceFacts = mapOf(
                "byteSize" to request.byteSize.toString(),
                "zeroFilled" to "true",
            ),
        )

        val operand = GPUMaterializedCommandOperandReference(
            label = "null-buffer:${request.label}",
            kind = GPUMaterializedCommandOperandKind.UniformBuffer,
            descriptorHash = "null-buffer:${request.byteSize}",
            deviceGeneration = context.deviceGeneration,
            ownerScope = "resource-provider:null-buffer",
            usageLabels = listOf("uniform"),
            invalidationPolicy = "device-generation",
            evidenceFacts = mapOf(
                "byteSize" to request.byteSize.toString(),
                "zeroFilled" to "true",
            ),
        )

        return GPUResourceMaterializationDecision.Materialized(
            resources = emptyList(),
            targetId = context.targetId,
            resourcePlanLabels = listOf(request.label),
            resourceLeases = listOf(lease),
            operandBridge = listOf(
                GPUMaterializedCommandOperandBinding(
                    commandLabel = "setBindGroup",
                    operand = operand,
                ),
            ),
        )
    }

    fun materializeFullscreenUniformSlabLease(
        request: GPUUniformSlabLeaseRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        if (request.targetId != context.targetId) {
            val diagnostic = GPUResourceDiagnostic.resourceTargetMismatch(
                resourceLabel = request.leaseId,
                requestTargetId = request.targetId,
                contextTargetId = context.targetId,
            )
            record("uniform-slab", "target-mismatch", request.leaseId, context.targetId)
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = diagnostic,
                targetId = context.targetId,
                resourcePlanLabels = listOf(request.leaseId),
            )
        }

        if (request.deviceGeneration != context.deviceGeneration) {
            val diagnostic = GPUResourceDiagnostic.deviceGenerationStale(
                resourceLabel = request.leaseId,
                expectedDeviceGeneration = context.deviceGeneration,
                actualDeviceGeneration = request.deviceGeneration,
                resourceKind = "resource",
            )
            record("uniform-slab", "stale-generation", request.leaseId, context.targetId)
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = diagnostic,
                targetId = context.targetId,
                resourcePlanLabels = listOf(request.leaseId),
            )
        }

        val key = GPUUniformSlabLeaseCacheKey.from(request)
        uniformSlabLeases[key]?.let { cachedLease ->
            val lease = cachedLease.copy(cacheResult = GPUResourceLeaseCacheResult.Reuse)
            record("uniform-slab", lease.cacheResult.dumpToken, key.dumpToken(), context.targetId)
            return GPUResourceMaterializationDecision.Materialized(
                resources = emptyList(),
                targetId = context.targetId,
                resourcePlanLabels = listOf(request.leaseId),
                resourceLeases = listOf(lease),
            )
        }

        return when (val leaseResult = leaseFactory.createUniformSlab(request)) {
            is GPUResourceLeaseFactoryResult.Failed -> {
                record("uniform-slab", "adapter-failure", request.leaseId, context.targetId)
                GPUResourceMaterializationDecision.Refused(
                    diagnostic = leaseResult.diagnostic,
                    targetId = context.targetId,
                    resourcePlanLabels = listOf(request.leaseId),
                )
            }
            is GPUResourceLeaseFactoryResult.Created -> {
                val lease = leaseResult.lease.snapshotForUniformSlabCache()
                uniformSlabLeases[key] = lease
                record("uniform-slab", lease.cacheResult.dumpToken, key.dumpToken(), context.targetId)
                GPUResourceMaterializationDecision.Materialized(
                    resources = emptyList(),
                    targetId = context.targetId,
                    resourcePlanLabels = listOf(request.leaseId),
                    resourceLeases = listOf(lease),
                )
            }
        }
    }

    fun materializeBindGroupLease(
        request: GPUBindGroupLeaseRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        if (request.deviceGeneration != context.deviceGeneration) {
            val diagnostic = GPUResourceDiagnostic.deviceGenerationStale(
                resourceLabel = request.leaseId,
                expectedDeviceGeneration = context.deviceGeneration,
                actualDeviceGeneration = request.deviceGeneration,
                resourceKind = "resource",
            )
            record("bind-group", "stale-generation", request.leaseId, context.targetId)
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = diagnostic,
                targetId = context.targetId,
                resourcePlanLabels = listOf(request.leaseId),
            )
        }

        val key = GPUBindGroupLeaseCacheKey.from(request)
        bindGroupLeases[key]?.let { cachedLease ->
            val lease = cachedLease.copy(cacheResult = GPUResourceLeaseCacheResult.Reuse)
            record("bind-group", lease.cacheResult.dumpToken, key.dumpToken(), context.targetId)
            return GPUResourceMaterializationDecision.Materialized(
                resources = emptyList(),
                targetId = context.targetId,
                resourcePlanLabels = listOf(request.leaseId),
                resourceLeases = listOf(lease),
            )
        }

        return when (val leaseResult = leaseFactory.createBindGroup(request)) {
            is GPUResourceLeaseFactoryResult.Failed -> {
                record("bind-group", "adapter-failure", request.leaseId, context.targetId)
                GPUResourceMaterializationDecision.Refused(
                    diagnostic = leaseResult.diagnostic,
                    targetId = context.targetId,
                    resourcePlanLabels = listOf(request.leaseId),
                )
            }
            is GPUResourceLeaseFactoryResult.Created -> {
                val lease = leaseResult.lease.snapshotForBindGroupCache()
                bindGroupLeases[key] = lease
                record("bind-group", lease.cacheResult.dumpToken, key.dumpToken(), context.targetId)
                GPUResourceMaterializationDecision.Materialized(
                    resources = emptyList(),
                    targetId = context.targetId,
                    resourcePlanLabels = listOf(request.leaseId),
                    resourceLeases = listOf(lease),
                )
            }
        }
    }

    override fun materializePayloadBindings(
        request: GPUPayloadMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        val decision = payloadProvider.materializePayloadBindings(request, context)
        decision.payloadEvents().forEach { event ->
            record(
                lane = event.lane,
                result = event.result.dumpToken,
                keyHash = event.keyHash,
                subjectHash = event.subjectHash,
            )
        }
        return decision
    }

    fun materializeIntermediateTexture(
        request: GPUIntermediateTextureMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        val descriptor = request.validatedDescriptor
        val diagnostic = when {
            request.targetId != context.targetId ->
                GPUResourceDiagnostic.resourceTargetMismatch(
                    resourceLabel = descriptor.label,
                    requestTargetId = request.targetId,
                    contextTargetId = context.targetId,
                )
            request.deviceGeneration != context.deviceGeneration ->
                GPUResourceDiagnostic.deviceGenerationStale(
                    resourceLabel = descriptor.label,
                    expectedDeviceGeneration = context.deviceGeneration,
                    actualDeviceGeneration = request.deviceGeneration,
                    resourceKind = "intermediate",
                )
            request.actualResourceGeneration != descriptor.generation ->
                GPUResourceDiagnostic(
                    code = "unsupported.intermediate.generation_stale",
                    resourceLabel = descriptor.label,
                    message = "intermediate generation ${request.actualResourceGeneration} != descriptor generation ${descriptor.generation}",
                    terminal = true,
                )
            request.activeAttachmentSampled ->
                GPUResourceDiagnostic(
                    code = "unsupported.destination_read.active_attachment_sampled",
                    resourceLabel = descriptor.label,
                    message = "intermediate texture would sample the active attachment",
                    terminal = true,
                )
            (request.requiredUsageLabels - descriptor.usageLabels.toSet()).isNotEmpty() ->
                GPUResourceDiagnostic.textureUsageMissing(
                    resourceLabel = descriptor.label,
                    missingUsageLabels = request.requiredUsageLabels - descriptor.usageLabels.toSet(),
                    availableUsageLabels = descriptor.usageLabels.toSet(),
                )
            else -> null
        }
        if (diagnostic != null) {
            record("intermediate-texture", "refuse", descriptor.descriptorHash, descriptor.label)
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = diagnostic,
                targetId = context.targetId,
                resourcePlanLabels = listOf(descriptor.label),
            )
        }

        val key = GPUIntermediateTextureLeaseCacheKey.from(request)
        intermediateTextureLeases[key]?.let { cachedLease ->
            val lease = cachedLease.copy(cacheResult = GPUResourceLeaseCacheResult.Reuse)
            record("intermediate-texture", lease.cacheResult.dumpToken, key.dumpToken(), descriptor.label)
            return GPUResourceMaterializationDecision.Materialized(
                resources = listOf(GPUTextureResourceRef("texture-ref:${descriptor.label}")),
                targetId = context.targetId,
                resourcePlanLabels = listOf(descriptor.label),
                resourceLeases = listOf(lease),
            )
        }

        val lease = GPUResourceLease(
            leaseId = "intermediate:${descriptor.label}",
            resourceKind = GPUResourceLeaseKind.Texture,
            deviceGeneration = context.deviceGeneration,
            descriptorHash = descriptor.descriptorHash,
            ownerScope = descriptor.ownerScope,
            usageLabels = descriptor.usageLabels,
            releasePolicy = descriptor.lifetimeClass,
            cacheResult = GPUResourceLeaseCacheResult.Create,
            evidenceFacts = mapOf(
                "purpose" to descriptor.purposeLabel,
                "bounds" to descriptor.boundsLabel,
                "sampleCount" to descriptor.sampleCount.toString(),
            ),
        )
        intermediateTextureLeases[key] = lease
        record("intermediate-texture", lease.cacheResult.dumpToken, key.dumpToken(), descriptor.label)
        return GPUResourceMaterializationDecision.Materialized(
            resources = listOf(GPUTextureResourceRef("texture-ref:${descriptor.label}")),
            targetId = context.targetId,
            resourcePlanLabels = listOf(descriptor.label),
            resourceLeases = listOf(lease),
        )
    }

    override fun materializeTextureSamplerBinding(
        request: GPUTextureSamplerMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        val decision = textureSamplerProvider.materializeTextureSamplerBinding(request, context)
        return when (decision) {
            is GPUResourceMaterializationDecision.Materialized -> {
                val cacheKey = GPUTextureSamplerLeaseCacheKey.from(request)
                val cacheResult = if (textureSamplerLeaseKeys.add(cacheKey)) {
                    GPUResourceLeaseCacheResult.Create
                } else {
                    GPUResourceLeaseCacheResult.Reuse
                }
                val leases = listOf(
                    GPUResourceLease(
                        leaseId = "texture:${request.ownership.ownerLabel}",
                        resourceKind = GPUResourceLeaseKind.Texture,
                        deviceGeneration = request.deviceGeneration,
                        descriptorHash = request.textureDescriptor.materializationDescriptorHashForProvider(),
                        ownerScope = request.ownership.ownerLabel,
                        usageLabels = request.requiredTextureUsageLabelsForProvider(),
                        releasePolicy = request.ownership.releasePolicy,
                        cacheResult = cacheResult,
                    ),
                    GPUResourceLease(
                        leaseId = "texture-view:${request.binding.bindingLabel}",
                        resourceKind = GPUResourceLeaseKind.TextureView,
                        deviceGeneration = request.deviceGeneration,
                        descriptorHash = request.viewDescriptorHashForProvider(),
                        ownerScope = request.ownership.ownerLabel,
                        usageLabels = listOf("texture_binding"),
                        releasePolicy = request.ownership.releasePolicy,
                        cacheResult = cacheResult,
                    ),
                    GPUResourceLease(
                        leaseId = "sampler:${request.binding.bindingLabel}",
                        resourceKind = GPUResourceLeaseKind.Sampler,
                        deviceGeneration = request.deviceGeneration,
                        descriptorHash = request.samplerDescriptorHashForProvider(),
                        ownerScope = "sampler-cache",
                        usageLabels = listOf("sampler"),
                        releasePolicy = "descriptor-cache",
                        cacheResult = cacheResult,
                    ),
                )
                record(
                    lane = "texture-sampler",
                    result = cacheResult.dumpToken,
                    keyHash = cacheKey.dumpToken(),
                    subjectHash = request.binding.bindingLabel,
                )
                decision.withResourceLeases(leases)
            }
            is GPUResourceMaterializationDecision.Refused -> {
                record(
                    lane = "texture-sampler",
                    result = GPUResourceLeaseCacheResult.Refuse.dumpToken,
                    keyHash = request.bindingLayoutHash,
                    subjectHash = request.binding.bindingLabel,
                )
                decision
            }
            is GPUResourceMaterializationDecision.Deferred -> {
                record("texture-sampler", "deferred", request.bindingLayoutHash, request.binding.bindingLabel)
                decision
            }
        }
    }

    private fun record(lane: String, result: String, keyHash: String, subjectHash: String) {
        mutableTelemetry = mutableTelemetry.plus(
            GPUConcreteResourceProviderEvent(
                lane = lane,
                result = result,
                keyHash = keyHash,
                subjectHash = subjectHash,
            ),
        )
    }

    private fun sharedReclaimableCandidates(): List<GPUPhysicalPoolEvictionCandidate> =
        (scratchTexturePool.reclaimableCandidates() + readbackStagingPool.reclaimableCandidates())
            .sortedWith(
                compareBy<GPUPhysicalPoolEvictionCandidate>(
                    { it.lastUseToken },
                    { it.category.ordinal },
                    { it.resourceId },
                ),
            )

    private fun planSharedEviction(
        residentBytesAtMost: Long,
    ): List<GPUPhysicalPoolEvictionCandidate>? {
        var projectedResident = physicalPoolBudgetLedger.managedResidentBytes()
        if (projectedResident <= residentBytesAtMost) return emptyList()
        val selected = mutableListOf<GPUPhysicalPoolEvictionCandidate>()
        for (candidate in sharedReclaimableCandidates()) {
            selected += candidate
            projectedResident = Math.subtractExact(projectedResident, candidate.physicalBytes)
            if (projectedResident <= residentBytesAtMost) return selected
        }
        return null
    }

    private fun applySharedEviction(
        plan: List<GPUPhysicalPoolEvictionCandidate>,
    ): GPUPhysicalPoolEvictionSummary {
        val scratchEvicted = mutableListOf<GPUScratchTextureEntrySnapshot>()
        val readbackEvicted = mutableListOf<GPUReadbackStagingEntrySnapshot>()
        plan.forEach { candidate ->
            when (candidate.category) {
                GPUFrameMemoryCategory.ReusableScratch ->
                    scratchEvicted += checkNotNull(scratchTexturePool.evictCandidate(candidate.resourceId))
                GPUFrameMemoryCategory.ReadbackStaging ->
                    readbackEvicted += checkNotNull(readbackStagingPool.evictCandidate(candidate.resourceId))
                else -> error("Unexpected physical pool category ${candidate.category}")
            }
        }
        return GPUPhysicalPoolEvictionSummary(
            scratchEntries = scratchEvicted,
            readbackEntries = readbackEvicted,
            remainingResidentBytes = physicalPoolBudgetLedger.managedResidentBytes(),
        )
    }

    private fun purgeForAdmission(diagnostic: org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic) {
        val aggregate = diagnostic.facts["aggregatePeakBytes"]?.toBigIntegerOrNull() ?: return
        val configured = diagnostic.facts["configuredAggregateBudgetBytes"]?.toBigIntegerOrNull() ?: return
        val bytesToFree = (aggregate - configured).max(BigInteger.ZERO)
        if (bytesToFree == BigInteger.ZERO) return
        val managedResident = physicalPoolBudgetLedger.managedResidentBytes().toBigInteger()
        val target = (managedResident - bytesToFree).max(BigInteger.ZERO).toLong()
        val plan = planSharedEviction(target) ?: return
        applySharedEviction(plan)
    }

    private fun journalPhysicalReservation(reservation: GPUProviderPhysicalReservation) {
        pendingPhysicalReservations += reservation
    }

    private fun GPUScratchTextureReservationResult.isAggregateBudgetRefusal(): Boolean =
        this is GPUScratchTextureReservationResult.Refused &&
            diagnostic.code.value == "unsupported.scratch_texture.aggregate_budget_exceeded"

    private fun GPUReadbackStagingReservationResult.isAggregateBudgetRefusal(): Boolean =
        this is GPUReadbackStagingReservationResult.Refused &&
            diagnostic.code.value == "unsupported.readback_staging.aggregate_budget_exceeded"

    private fun recordScratchLifecycle(action: String, result: GPUScratchLifecycleResult) {
        record(
            lane = "scratch-texture",
            result = if (result is GPUScratchLifecycleResult.Accepted) action else "refuse",
            keyHash = "physical-pool",
            subjectHash = "lifecycle",
        )
    }

    private fun recordReadbackLifecycle(action: String, result: GPUReadbackStagingLifecycleResult) {
        record(
            lane = "readback-staging",
            result = if (result is GPUReadbackStagingLifecycleResult.Accepted) action else "refuse",
            keyHash = "physical-pool",
            subjectHash = "lifecycle",
        )
    }
}

private sealed interface GPUProviderPhysicalReservation {
    val acquisitionOrdinal: Long
    val reservationId: String
    val ownerScope: String
    val deviceGeneration: GPUDeviceGenerationID

    data class Scratch(
        override val acquisitionOrdinal: Long,
        val lease: GPUScratchTextureLease,
    ) : GPUProviderPhysicalReservation {
        override val reservationId: String get() = lease.reservationId
        override val ownerScope: String get() = lease.reservationScope
        override val deviceGeneration: GPUDeviceGenerationID get() = lease.deviceGeneration
    }

    data class Readback(
        override val acquisitionOrdinal: Long,
        val lease: GPUReadbackStagingLease,
    ) : GPUProviderPhysicalReservation {
        override val reservationId: String get() = lease.reservationId
        override val ownerScope: String get() = lease.ownerScope
        override val deviceGeneration: GPUDeviceGenerationID get() = lease.deviceGeneration
    }
}

private data class GPUBindGroupLeaseCacheKey(
    val leaseId: String,
    val descriptorHash: String,
    val ownerScope: String,
    val usageLabels: List<String>,
    val deviceGeneration: Long,
    val releasePolicy: String,
) {
    fun dumpToken(): String =
        "lease=$leaseId;descriptor=$descriptorHash;owner=$ownerScope;" +
            "usage=${usageLabels.joinToString("+")};deviceGeneration=$deviceGeneration;release=$releasePolicy"

    companion object {
        fun from(request: GPUBindGroupLeaseRequest): GPUBindGroupLeaseCacheKey =
            GPUBindGroupLeaseCacheKey(
                leaseId = request.leaseId,
                descriptorHash = request.descriptorHash,
                ownerScope = request.ownerScope,
                usageLabels = request.usageLabels.sorted(),
                deviceGeneration = request.deviceGeneration,
                releasePolicy = request.releasePolicy,
            )
    }
}

private data class GPUUniformSlabLeaseCacheKey(
    val leaseId: String,
    val targetId: String,
    val frameId: String,
    val descriptorHash: String,
    val deviceGeneration: Long,
    val totalBytes: Long,
    val alignmentBytes: Long,
    val payloadCount: Int,
    val releasePolicy: String,
) {
    fun dumpToken(): String =
        "lease=$leaseId;target=$targetId;frame=$frameId;descriptor=$descriptorHash;" +
            "deviceGeneration=$deviceGeneration;totalBytes=$totalBytes;" +
            "alignmentBytes=$alignmentBytes;payloadCount=$payloadCount;release=$releasePolicy"

    companion object {
        fun from(request: GPUUniformSlabLeaseRequest): GPUUniformSlabLeaseCacheKey =
            GPUUniformSlabLeaseCacheKey(
                leaseId = request.leaseId,
                targetId = request.targetId,
                frameId = request.frameId,
                descriptorHash = request.descriptorHash,
                deviceGeneration = request.deviceGeneration,
                totalBytes = request.totalBytes,
                alignmentBytes = request.alignmentBytes,
                payloadCount = request.payloadCount,
                releasePolicy = request.releasePolicy,
            )
    }
}

private data class GPUTextureSamplerLeaseCacheKey(
    val targetId: String,
    val bindingLayoutHash: String,
    val bindingLabel: String,
    val ownerLabel: String,
    val lifetimeClass: String,
    val releasePolicy: String,
    val canAliasScratch: Boolean,
    val textureWidth: Int,
    val textureHeight: Int,
    val textureFormat: String,
    val textureSampleCount: Int,
    val textureUsageLabels: List<String>,
    val viewTextureDescriptorHash: String,
    val viewDimension: String,
    val viewMipRangeFirst: Int,
    val viewMipRangeLast: Int,
    val viewArrayLayerRangeFirst: Int,
    val viewArrayLayerRangeLast: Int,
    val samplerAddressModeU: String,
    val samplerAddressModeV: String,
    val samplerMagFilter: String,
    val samplerMinFilter: String,
    val samplerMipmapFilter: String,
    val samplerLodMinClamp: String,
    val samplerLodMaxClamp: String,
    val samplerCompareMode: String,
    val samplerMaxAnisotropy: Int,
    val samplerCapabilityRequirements: List<String>,
    val deviceGeneration: Long,
    val actualResourceGeneration: Long,
) {
    fun dumpToken(): String =
        "target=$targetId;layout=$bindingLayoutHash;binding=$bindingLabel;owner=$ownerLabel;" +
            "lifetime=$lifetimeClass;release=$releasePolicy;canAliasScratch=$canAliasScratch;" +
            "texture=${textureWidth}x$textureHeight:$textureFormat:" +
            "samples=$textureSampleCount:usage=${textureUsageLabels.joinToString("+")};" +
            "view=$viewTextureDescriptorHash:$viewDimension:$viewMipRangeFirst..$viewMipRangeLast:" +
            "$viewArrayLayerRangeFirst..$viewArrayLayerRangeLast;" +
            "sampler=$samplerAddressModeU:$samplerAddressModeV:$samplerMagFilter:$samplerMinFilter:" +
            "$samplerMipmapFilter:$samplerLodMinClamp:$samplerLodMaxClamp:$samplerCompareMode:" +
            "$samplerMaxAnisotropy:${samplerCapabilityRequirements.joinToString("+")};" +
            "deviceGeneration=$deviceGeneration;resourceGeneration=$actualResourceGeneration"

    companion object {
        fun from(request: GPUTextureSamplerMaterializationRequest): GPUTextureSamplerLeaseCacheKey =
            GPUTextureSamplerLeaseCacheKey(
                targetId = request.targetId,
                bindingLayoutHash = request.bindingLayoutHash,
                bindingLabel = request.binding.bindingLabel,
                ownerLabel = request.ownership.ownerLabel,
                lifetimeClass = request.ownership.lifetimeClass,
                releasePolicy = request.ownership.releasePolicy,
                canAliasScratch = request.ownership.canAliasScratch,
                textureWidth = request.textureDescriptor.width,
                textureHeight = request.textureDescriptor.height,
                textureFormat = request.textureDescriptor.format,
                textureSampleCount = request.textureDescriptor.sampleCount,
                textureUsageLabels = request.textureDescriptor.usageLabels.sorted(),
                viewTextureDescriptorHash = request.viewDescriptor.textureDescriptorHash,
                viewDimension = request.viewDescriptor.viewDimension,
                viewMipRangeFirst = request.viewDescriptor.mipRange.first,
                viewMipRangeLast = request.viewDescriptor.mipRange.last,
                viewArrayLayerRangeFirst = request.viewDescriptor.arrayLayerRange.first,
                viewArrayLayerRangeLast = request.viewDescriptor.arrayLayerRange.last,
                samplerAddressModeU = request.samplerDescriptor.addressModeU,
                samplerAddressModeV = request.samplerDescriptor.addressModeV,
                samplerMagFilter = request.samplerDescriptor.magFilter,
                samplerMinFilter = request.samplerDescriptor.minFilter,
                samplerMipmapFilter = request.samplerDescriptor.mipmapFilter,
                samplerLodMinClamp = request.samplerDescriptor.lodMinClamp,
                samplerLodMaxClamp = request.samplerDescriptor.lodMaxClamp,
                samplerCompareMode = request.samplerDescriptor.compareMode,
                samplerMaxAnisotropy = request.samplerDescriptor.maxAnisotropy,
                samplerCapabilityRequirements = request.samplerDescriptor.capabilityRequirements.sorted(),
                deviceGeneration = request.deviceGeneration,
                actualResourceGeneration = request.actualResourceGeneration,
            )
    }
}

private fun GPUResourceLease.snapshotForUniformSlabCache(): GPUResourceLease =
    copy(
        usageLabels = dumpUsageLabelsSnapshot,
        evidenceFacts = dumpEvidenceFactsSnapshot,
    )

private fun GPUResourceLease.snapshotForBindGroupCache(): GPUResourceLease =
    copy(
        usageLabels = dumpUsageLabelsSnapshot,
        evidenceFacts = dumpEvidenceFactsSnapshot,
    )

private fun GPUResourceMaterializationDecision.Materialized.withResourceLeases(
    leases: List<GPUResourceLease>,
): GPUResourceMaterializationDecision.Materialized = copy(resourceLeases = leases)

private fun GPUResourceMaterializationDecision.payloadEvents(): List<GPUPayloadMaterializationTelemetryEvent> =
    when (this) {
        is GPUResourceMaterializationDecision.Materialized -> dumpPayloadTelemetrySnapshot
        is GPUResourceMaterializationDecision.Refused -> dumpPayloadTelemetrySnapshot
        is GPUResourceMaterializationDecision.Deferred -> emptyList()
    }

private fun GPUTextureDescriptor.materializationDescriptorHashForProvider(): String =
    listOf(
        "texture",
        "${width}x$height",
        format,
        "samples=$sampleCount",
        usageLabels.sorted().joinToString("+"),
    ).joinToString(":")

private fun GPUTextureSamplerMaterializationRequest.requiredTextureUsageLabelsForProvider(): List<String> =
    dumpRequiredTextureUsageLabelsSnapshot.sorted()

private fun GPUTextureSamplerMaterializationRequest.viewDescriptorHashForProvider(): String =
    listOf(
        "texture-view",
        viewDescriptor.textureDescriptorHash,
        viewDescriptor.viewDimension,
        viewDescriptor.mipRange.toString(),
        viewDescriptor.arrayLayerRange.toString(),
    ).joinToString(":")

private fun GPUTextureSamplerMaterializationRequest.samplerDescriptorHashForProvider(): String =
    listOf(
        "sampler",
        samplerDescriptor.addressModeU,
        samplerDescriptor.addressModeV,
        samplerDescriptor.magFilter,
        samplerDescriptor.minFilter,
        samplerDescriptor.mipmapFilter,
        samplerDescriptor.lodMinClamp,
        samplerDescriptor.lodMaxClamp,
        samplerDescriptor.compareMode,
        samplerDescriptor.maxAnisotropy.toString(),
        samplerDescriptor.capabilityRequirements.sorted().joinToString("+"),
    ).joinToString(":")
