package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import java.util.concurrent.atomic.AtomicLong
import java.util.Collections
import java.util.IdentityHashMap
import org.graphiks.kanvas.gpu.renderer.resources.GPUBindGroupLeaseRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseFactoryResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabLeaseRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseFactory

internal class GPURuntimeResourceAdapter(
    private val requirePreparedResources: Boolean = false,
) : GPUResourceLeaseFactory,
    GPUPreparedNativeFramePayloadAccess,
    AutoCloseable {
    private enum class PreparedNativePayloadState {
        Draft,
        Ready,
        Consumed,
        Submitted,
    }

    private sealed interface OwnedHandleOwner {
        data class Payload(val token: GPUPreparedNativeFrameToken) : OwnedHandleOwner
        data class UniformSlab(val leaseId: String) : OwnedHandleOwner
        data class BindGroup(val leaseId: String) : OwnedHandleOwner
    }

    private data class PreparedNativePayloadEntry(
        val token: GPUPreparedNativeFrameToken,
        val payload: GPUPreparedNativeFramePayload,
        var state: PreparedNativePayloadState,
        val completionOwned: MutableList<AutoCloseable> =
            payload.ownedHandles(GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion),
        val outputOwned: MutableList<AutoCloseable> =
            payload.ownedHandles(GPUPreparedNativeOperandOwnership.OutputOwnedReadback),
        val anchored: MutableList<AutoCloseable> = payload.anchoredHandles(),
        val borrowed: MutableList<AutoCloseable> =
            payload.ownedHandles(GPUPreparedNativeOperandOwnership.Borrowed)
                .filterNotTo(mutableListOf()) { candidate -> anchored.any { it === candidate } },
        var outputMappingClaimed: Boolean = false,
    ) {
        fun closeCompletionOwned(onClosed: (AutoCloseable) -> Unit = {}): Boolean =
            completionOwned.closeRetainingFailures(
                onClosed = onClosed,
                onAnchoredClosed = { anchoredHandle ->
                    anchored.removeAll { it === anchoredHandle }
                    onClosed(anchoredHandle)
                },
            )
        fun closeOutputOwned(onClosed: (AutoCloseable) -> Unit = {}): Boolean =
            outputOwned.closeRetainingFailures(onClosed)
        fun closeAllOwned(onClosed: (AutoCloseable) -> Unit = {}): Boolean =
            closeCompletionOwned(onClosed) and closeOutputOwned(onClosed)
        fun allOwnedHandles(): List<AutoCloseable> = completionOwned + outputOwned + anchored
        fun removeOwnedHandles(handles: Set<AutoCloseable>) {
            val detached = Collections.newSetFromMap(IdentityHashMap<AutoCloseable, Boolean>())
            detached += handles
            completionOwned.filterIsInstance<GPUPreparedNativeCompletionAnchor>().forEach { anchor ->
                if (handles.any { it === anchor }) detached += anchor.ownedHandlesSnapshot()
                anchor.detachOwnedHandles(detached)
            }
            completionOwned.removeAllByIdentity(handles)
            outputOwned.removeAllByIdentity(handles)
            anchored.removeAllByIdentity(detached)
        }
        fun addBorrowedHandle(handle: AutoCloseable) {
            if (borrowed.none { it === handle }) borrowed += handle
        }
        fun removeBorrowedHandle(handle: AutoCloseable) {
            borrowed.removeAll { it === handle }
        }
        val hasOutputOwned: Boolean get() = outputOwned.isNotEmpty()
    }

    private val preparedNativePayloads = linkedMapOf<GPUPreparedNativeFrameToken, PreparedNativePayloadEntry>()
    private val quarantinedNativePayloads = linkedMapOf<GPUPreparedNativeFrameToken, PreparedNativePayloadEntry>()
    private val outputOwnedNativePayloads = linkedMapOf<GPUPreparedNativeFrameToken, PreparedNativePayloadEntry>()
    private val ownedHandleOwners = IdentityHashMap<AutoCloseable, OwnedHandleOwner>()
    private val borrowedHandleTokens = IdentityHashMap<AutoCloseable, MutableSet<GPUPreparedNativeFrameToken>>()
    private val nativePayloadOrdinal = AtomicLong()
    private var closed = false
    private val liveLeaseIds = linkedSetOf<String>()
    private val uniformSlabs = linkedMapOf<String, GPUBuffer>()
    private val bindGroups = linkedMapOf<String, GPUBindGroup>()
    private val pendingUniformSlabs = linkedMapOf<String, () -> GPUBuffer>()
    private val pendingBindGroups = linkedMapOf<String, () -> GPUBindGroup>()

    internal val activePreparedNativeFramePayloadCount: Int
        @Synchronized get() = preparedNativePayloads.size

    internal val quarantinedPreparedNativeFramePayloadCount: Int
        @Synchronized get() = quarantinedNativePayloads.size

    internal val outputOwnedPreparedNativeFramePayloadCount: Int
        @Synchronized get() = outputOwnedNativePayloads.size

    internal val preparedNativeFramePayloadRegistrationCount: Long
        get() = nativePayloadOrdinal.get()

    @Synchronized
    internal fun registerPreparedNativeFrameDraft(
        draft: GPUPreparedNativeFrameDraft,
    ): GPUPreparedNativeFrameRegistration = registerPreparedNativeFramePayload(
        draft.payload,
        PreparedNativePayloadState.Draft,
    )

    private fun registerPreparedNativeFramePayload(
        payload: GPUPreparedNativeFramePayload,
        initialState: PreparedNativePayloadState,
    ): GPUPreparedNativeFrameRegistration {
        val ordinal = nativePayloadOrdinal.incrementAndGet()
        val token = GPUPreparedNativeFrameToken(
            "native-frame-${payload.identity.frameId.value}-generation-" +
                "${payload.identity.deviceGeneration.value}-payload-$ordinal",
        )
        val entry = PreparedNativePayloadEntry(
            token = token,
            payload = payload,
            state = initialState,
        )
        val ownedConflicts = entry.allOwnedHandles().filterTo(
            Collections.newSetFromMap(IdentityHashMap<AutoCloseable, Boolean>()),
        ) { ownedHandleOwners.containsKey(it) || borrowedHandleTokens[it].orEmpty().isNotEmpty() }
        val borrowedConflict = entry.borrowed.any { ownedHandleOwners.containsKey(it) }
        if (ownedConflicts.isNotEmpty() || borrowedConflict) {
            entry.removeOwnedHandles(ownedConflicts)
            reservePayloadHandles(entry)
            if (!entry.closeAllOwned { releasePayloadHandle(token, it) }) {
                quarantinedNativePayloads[token] = entry
            }
            return GPUPreparedNativeFrameRegistration.Refused(
                "unsupported.native-frame-payload.owned-handle-conflict",
            )
        }
        reservePayloadHandles(entry)
        reserveBorrowedHandles(entry)
        if (closed) {
            if (!entry.closeAllOwned { releasePayloadHandle(token, it) }) {
                quarantinedNativePayloads[token] = entry
            } else {
                releaseBorrowedHandles(entry)
            }
            return GPUPreparedNativeFrameRegistration.Refused(
                "unsupported.native-frame-payload.registry-closed",
            )
        }
        preparedNativePayloads[token] = entry
        return GPUPreparedNativeFrameRegistration.Registered(
            GPUPreparedNativeFrameOwnership(token, this),
        )
    }

    private fun reservePayloadHandles(entry: PreparedNativePayloadEntry) {
        val owner = OwnedHandleOwner.Payload(entry.token)
        entry.allOwnedHandles().forEach { handle ->
            check(ownedHandleOwners.put(handle, owner) == null) {
                "Payload owned handle reservation must be conflict-free"
            }
        }
    }

    private fun reserveBorrowedHandles(entry: PreparedNativePayloadEntry) {
        entry.borrowed.forEach { handle ->
            borrowedHandleTokens.getOrPut(handle) { linkedSetOf() } += entry.token
        }
    }

    private fun releaseBorrowedHandles(entry: PreparedNativePayloadEntry) {
        entry.borrowed.forEach { handle ->
            borrowedHandleTokens[handle]?.let { tokens ->
                tokens -= entry.token
                if (tokens.isEmpty()) borrowedHandleTokens.remove(handle)
            }
        }
    }

    private fun releasePayloadHandle(token: GPUPreparedNativeFrameToken, handle: AutoCloseable) {
        if (ownedHandleOwners[handle] == OwnedHandleOwner.Payload(token)) ownedHandleOwners.remove(handle)
    }

    @Synchronized
    override fun bindLateSurface(
        token: GPUPreparedNativeFrameToken,
        acquiredSurface: GPUAcquiredSurfaceOutput?,
        binding: GPUPreparedNativeFrameLateSurfaceBinding,
    ): GPUPreparedNativeFrameBindingResult {
        if (closed) return GPUPreparedNativeFrameBindingResult.Refused(
            "unsupported.native-frame-payload.registry-closed",
            "Native payload registry is closed.",
        )
        val entry = preparedNativePayloads[token]
            ?: return GPUPreparedNativeFrameBindingResult.Refused(
                "unsupported.native-frame-payload.token-missing",
                "Native payload token is missing.",
            )
        if (entry.state != PreparedNativePayloadState.Draft) {
            return GPUPreparedNativeFrameBindingResult.Refused(
                "unsupported.native-frame-payload.draft-state",
                "Native payload is not awaiting late surface binding.",
            )
        }
        val lateBorrowedHandle = (binding as? GPUPreparedNativeFrameLateSurfaceBinding.Bound)
            ?.target
            ?.takeIf { it.ownership == GPUPreparedNativeOperandOwnership.Borrowed }
            ?.nativeHandle()
        val reservedLateBorrowed = lateBorrowedHandle != null && entry.borrowed.none { it === lateBorrowedHandle }
        if (reservedLateBorrowed) {
            if (ownedHandleOwners.containsKey(lateBorrowedHandle)) {
                return GPUPreparedNativeFrameBindingResult.Refused(
                    "unsupported.native-frame-payload.owned-handle-conflict",
                    "Late surface target conflicts with an owned native handle.",
                )
            }
            entry.addBorrowedHandle(lateBorrowedHandle)
            borrowedHandleTokens.getOrPut(lateBorrowedHandle) { linkedSetOf() } += token
        }
        if (!entry.payload.bindLateSurface(acquiredSurface, binding) || !entry.payload.lateSurfaceReady) {
            if (reservedLateBorrowed) {
                borrowedHandleTokens[lateBorrowedHandle]?.let { tokens ->
                    tokens -= token
                    if (tokens.isEmpty()) borrowedHandleTokens.remove(lateBorrowedHandle)
                }
                entry.removeBorrowedHandle(lateBorrowedHandle)
            }
            return GPUPreparedNativeFrameBindingResult.Refused(
                "unsupported.native-frame-payload.surface-binding-mismatch",
                "Late surface binding does not exactly match the prepared draft.",
            )
        }
        entry.state = PreparedNativePayloadState.Ready
        return GPUPreparedNativeFrameBindingResult.Ready
    }

    @Synchronized
    override fun consumePreparedNativeFramePayload(
        token: GPUPreparedNativeFrameToken,
        expectedIdentity: GPUPreparedNativeFrameIdentity,
    ): GPUPreparedNativeFrameConsumption {
        if (closed) return GPUPreparedNativeFrameConsumption.Refused(
            "unsupported.native-frame-payload.registry-closed",
        )
        val entry = preparedNativePayloads[token]
            ?: return GPUPreparedNativeFrameConsumption.Refused(
                "unsupported.native-frame-payload.token-missing",
            )
        if (entry.payload.identity != expectedIdentity) {
            return GPUPreparedNativeFrameConsumption.Refused(
                "stale.native-frame-payload.identity-mismatch",
            )
        }
        if (entry.state == PreparedNativePayloadState.Draft) {
            return GPUPreparedNativeFrameConsumption.Refused(
                "unsupported.native-frame-payload.draft-not-ready",
            )
        }
        if (entry.state != PreparedNativePayloadState.Ready) {
            return GPUPreparedNativeFrameConsumption.Refused(
                "unsupported.native-frame-payload.token-already-consumed",
            )
        }
        entry.state = PreparedNativePayloadState.Consumed
        return GPUPreparedNativeFrameConsumption.Consumed(entry.payload)
    }

    @Synchronized
    override fun rollbackPreparedNativeFramePayload(token: GPUPreparedNativeFrameToken): Boolean {
        if (closed) return false
        val entry = preparedNativePayloads[token] ?: return false
        if (entry.state == PreparedNativePayloadState.Submitted) return false
        if (entry.closeAllOwned { releasePayloadHandle(token, it) }) {
            preparedNativePayloads.remove(token)
            releaseBorrowedHandles(entry)
            return true
        }
        preparedNativePayloads.remove(token)
        quarantinedNativePayloads[token] = entry
        return false
    }

    @Synchronized
    override fun markPreparedNativeFrameSubmitted(token: GPUPreparedNativeFrameToken): Boolean {
        if (closed) return false
        val entry = preparedNativePayloads[token] ?: return false
        if (entry.state != PreparedNativePayloadState.Consumed) return false
        entry.state = PreparedNativePayloadState.Submitted
        return true
    }

    @Synchronized
    override fun releasePreparedNativeFramePayload(token: GPUPreparedNativeFrameToken): Boolean {
        if (closed) return false
        val entry = preparedNativePayloads[token] ?: return false
        if (entry.state != PreparedNativePayloadState.Submitted) return false
        if (!entry.closeCompletionOwned { releasePayloadHandle(token, it) }) {
            preparedNativePayloads.remove(token)
            quarantinedNativePayloads[token] = entry
            return false
        }
        preparedNativePayloads.remove(token)
        if (entry.hasOutputOwned) {
            outputOwnedNativePayloads[token] = entry
        } else {
            releaseBorrowedHandles(entry)
        }
        return true
    }

    @Synchronized
    override fun claimOutputOwnedPreparedNativeFramePayloadMapping(
        token: GPUPreparedNativeFrameToken,
    ): Boolean {
        if (closed) return false
        val entry = outputOwnedNativePayloads[token] ?: return false
        if (entry.outputMappingClaimed || !entry.hasOutputOwned) return false
        entry.outputMappingClaimed = true
        return true
    }

    @Synchronized
    override fun releaseOutputOwnedPreparedNativeFramePayload(token: GPUPreparedNativeFrameToken): Boolean {
        val entry = outputOwnedNativePayloads[token] ?: return false
        if (!entry.outputMappingClaimed) return false
        if (entry.closeOutputOwned { releasePayloadHandle(token, it) }) {
            outputOwnedNativePayloads.remove(token)
            releaseBorrowedHandles(entry)
            return true
        }
        outputOwnedNativePayloads.remove(token)
        quarantinedNativePayloads[token] = entry
        return false
    }

    @Synchronized
    override fun quarantineOutputOwnedPreparedNativeFramePayload(token: GPUPreparedNativeFrameToken): Boolean {
        val entry = outputOwnedNativePayloads[token] ?: return false
        if (closed && !entry.outputMappingClaimed) return false
        outputOwnedNativePayloads.remove(token)
        quarantinedNativePayloads[token] = entry
        return true
    }

    @Synchronized
    override fun quarantinePreparedNativeFramePayload(token: GPUPreparedNativeFrameToken): Boolean {
        if (closed) return false
        val entry = preparedNativePayloads.remove(token) ?: return false
        quarantinedNativePayloads[token] = entry
        return true
    }

    fun prepareUniformSlab(leaseId: String, createBuffer: () -> GPUBuffer) {
        pendingUniformSlabs[leaseId] = createBuffer
    }

    fun uniformSlabBuffer(leaseId: String): GPUBuffer? = uniformSlabs[leaseId]

    fun clearPreparedUniformSlab(leaseId: String) {
        pendingUniformSlabs.remove(leaseId)
    }

    fun prepareBindGroup(leaseId: String, createBindGroup: () -> GPUBindGroup) {
        pendingBindGroups[leaseId] = createBindGroup
    }

    fun bindGroup(leaseId: String): GPUBindGroup? = bindGroups[leaseId]

    fun clearPreparedBindGroup(leaseId: String) {
        pendingBindGroups.remove(leaseId)
    }

    @Synchronized
    override fun createUniformSlab(request: GPUUniformSlabLeaseRequest): GPUResourceLeaseFactoryResult {
        val buffer = uniformSlabs[request.leaseId] ?: run {
            val createBuffer = pendingUniformSlabs.remove(request.leaseId)
            if (createBuffer != null) {
                val created = try {
                    createBuffer()
                } catch (_: Throwable) {
                    return GPUResourceLeaseFactoryResult.Failed(
                        diagnostic = GPUResourceDiagnostic.adapterCreateFailed(
                            resourceLabel = request.leaseId,
                            reason = "uniform-slab-create-failed",
                        ),
                    )
                }
                if (!reserveOwnedHandle(created, OwnedHandleOwner.UniformSlab(request.leaseId))) {
                    return GPUResourceLeaseFactoryResult.Failed(
                        diagnostic = GPUResourceDiagnostic.adapterCreateFailed(
                            resourceLabel = request.leaseId,
                            reason = "owned-handle-conflict",
                        ),
                    )
                }
                created.also { uniformSlabs[request.leaseId] = it }
            } else if (requirePreparedResources) {
                return GPUResourceLeaseFactoryResult.Failed(
                    diagnostic = GPUResourceDiagnostic.adapterCreateFailed(
                        resourceLabel = request.leaseId,
                        reason = "uniform-slab-preparation-missing",
                    ),
                )
            } else {
                null
            }
        }
        if (buffer != null) {
            uniformSlabs[request.leaseId] = buffer
        }
        liveLeaseIds += request.leaseId
        return GPUResourceLeaseFactoryResult.Created(
            GPUResourceLease(
                leaseId = request.leaseId,
                resourceKind = GPUResourceLeaseKind.UniformSlab,
                deviceGeneration = request.deviceGeneration,
                descriptorHash = request.descriptorHash,
                ownerScope = request.frameId,
                usageLabels = listOf("copy_dst", "uniform"),
                releasePolicy = request.releasePolicy,
                cacheResult = GPUResourceLeaseCacheResult.Create,
                evidenceFacts = mapOf(
                    "alignment" to request.alignmentBytes.toString(),
                    "payloadCount" to request.payloadCount.toString(),
                    "target" to request.targetId,
                    "totalBytes" to request.totalBytes.toString(),
                ),
            ),
        )
    }

    @Synchronized
    override fun createBindGroup(request: GPUBindGroupLeaseRequest): GPUResourceLeaseFactoryResult {
        val prerequisiteLeaseId = request.fullscreenPrerequisiteLeaseId()
        if (prerequisiteLeaseId != null && prerequisiteLeaseId !in liveLeaseIds) {
            return GPUResourceLeaseFactoryResult.Failed(
                diagnostic = GPUResourceDiagnostic.adapterCreateFailed(
                    resourceLabel = request.leaseId,
                    reason = "uniform-slab-lease-missing",
                ),
            )
        }

        val bindGroup = bindGroups[request.leaseId] ?: run {
            val createBindGroup = pendingBindGroups.remove(request.leaseId)
            if (createBindGroup != null) {
                val created = try {
                    createBindGroup()
                } catch (_: Throwable) {
                    return GPUResourceLeaseFactoryResult.Failed(
                        diagnostic = GPUResourceDiagnostic.adapterCreateFailed(
                            resourceLabel = request.leaseId,
                            reason = "bind-group-create-failed",
                        ),
                    )
                }
                if (!reserveOwnedHandle(created, OwnedHandleOwner.BindGroup(request.leaseId))) {
                    return GPUResourceLeaseFactoryResult.Failed(
                        diagnostic = GPUResourceDiagnostic.adapterCreateFailed(
                            resourceLabel = request.leaseId,
                            reason = "owned-handle-conflict",
                        ),
                    )
                }
                created.also { bindGroups[request.leaseId] = it }
            } else if (requirePreparedResources) {
                return GPUResourceLeaseFactoryResult.Failed(
                    diagnostic = GPUResourceDiagnostic.adapterCreateFailed(
                        resourceLabel = request.leaseId,
                        reason = "bind-group-preparation-missing",
                    ),
                )
            } else {
                null
            }
        }
        if (bindGroup != null) {
            bindGroups[request.leaseId] = bindGroup
        }
        liveLeaseIds += request.leaseId
        return GPUResourceLeaseFactoryResult.Created(
            GPUResourceLease(
                leaseId = request.leaseId,
                resourceKind = GPUResourceLeaseKind.BindGroup,
                deviceGeneration = request.deviceGeneration,
                descriptorHash = request.descriptorHash,
                ownerScope = request.ownerScope,
                usageLabels = request.usageLabels,
                releasePolicy = request.releasePolicy,
                cacheResult = GPUResourceLeaseCacheResult.Create,
            ),
        )
    }

    fun containsLease(leaseId: String): Boolean = leaseId in liveLeaseIds

    private fun reserveOwnedHandle(handle: AutoCloseable, owner: OwnedHandleOwner): Boolean {
        if (ownedHandleOwners.containsKey(handle) || borrowedHandleTokens[handle].orEmpty().isNotEmpty()) {
            return false
        }
        ownedHandleOwners[handle] = owner
        return true
    }

    @Synchronized
    override fun close() {
        closed = true
        bindGroups.closeValuesRetainingFailures { leaseId, handle ->
            if (ownedHandleOwners[handle] == OwnedHandleOwner.BindGroup(leaseId)) {
                ownedHandleOwners.remove(handle)
            }
        }
        uniformSlabs.closeValuesRetainingFailures { leaseId, handle ->
            if (ownedHandleOwners[handle] == OwnedHandleOwner.UniformSlab(leaseId)) {
                ownedHandleOwners.remove(handle)
            }
        }
        pendingBindGroups.clear()
        pendingUniformSlabs.clear()
        liveLeaseIds.clear()

        preparedNativePayloads.toMap().forEach { (token, entry) ->
            if (entry.closeAllOwned { releasePayloadHandle(token, it) }) {
                preparedNativePayloads.remove(token)
                releaseBorrowedHandles(entry)
            } else {
                preparedNativePayloads.remove(token)
                quarantinedNativePayloads[token] = entry
            }
        }
        outputOwnedNativePayloads.toMap().forEach { (token, entry) ->
            if (entry.outputMappingClaimed) return@forEach
            if (entry.closeAllOwned { releasePayloadHandle(token, it) }) {
                outputOwnedNativePayloads.remove(token)
                releaseBorrowedHandles(entry)
            } else {
                outputOwnedNativePayloads.remove(token)
                quarantinedNativePayloads[token] = entry
            }
        }
        // Retry quarantine exactly once after active and unclaimed output payload cleanup.
        // Successful handles were removed from their entry by the first pass and cannot be
        // closed twice; a repeated failure remains visible for a later parent teardown retry.
        val terminalCloseRetry = quarantinedNativePayloads.keys.toSet()
        terminalCloseRetry.forEach { token ->
            val entry = quarantinedNativePayloads[token] ?: return@forEach
            if (entry.closeAllOwned { releasePayloadHandle(token, it) }) {
                quarantinedNativePayloads.remove(token)
                releaseBorrowedHandles(entry)
            }
        }
    }
}

private fun GPUPreparedNativeFramePayload.ownedHandles(
    ownership: GPUPreparedNativeOperandOwnership,
): MutableList<AutoCloseable> {
    val closedHandles = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
    return (
        scopeOperands.flatMap(GPUPreparedNativeScopeOperand::operands)
            .filter { it.ownership == ownership }
            .map(GPUPreparedNativeOperand::nativeHandle) +
            auxiliaryOwnedHandles.filter { it.ownership == ownership }.map { it.handle }
        )
        .filter { closedHandles.add(it) }
        .toMutableList()
}

private fun GPUPreparedNativeFramePayload.anchoredHandles(): MutableList<AutoCloseable> {
    val identities = Collections.newSetFromMap(IdentityHashMap<AutoCloseable, Boolean>())
    return auxiliaryOwnedHandles
        .mapNotNull { it.handle as? GPUPreparedNativeCompletionAnchor }
        .flatMap(GPUPreparedNativeCompletionAnchor::ownedHandlesSnapshot)
        .filter { identities.add(it) }
        .toMutableList()
}

private fun MutableList<AutoCloseable>.closeRetainingFailures(
    onClosed: (AutoCloseable) -> Unit = {},
    onAnchoredClosed: (AutoCloseable) -> Unit = {},
): Boolean {
    val iterator = iterator()
    while (iterator.hasNext()) {
        val handle = iterator.next()
        val anchoredBefore = (handle as? GPUPreparedNativeCompletionAnchor)
            ?.ownedHandlesSnapshot()
            .orEmpty()
        var closed = false
        try {
            handle.close()
            closed = true
        } catch (_: Throwable) {
            // Ownership remains in this entry so close can be retried or quarantined safely.
        }
        val anchoredAfter = (handle as? GPUPreparedNativeCompletionAnchor)
            ?.ownedHandlesSnapshot()
            .orEmpty()
        anchoredBefore.filter { candidate -> anchoredAfter.none { it === candidate } }
            .forEach(onAnchoredClosed)
        if (closed) {
            iterator.remove()
            onClosed(handle)
        }
    }
    return isEmpty()
}

private fun <K, V : AutoCloseable> MutableMap<K, V>.closeValuesRetainingFailures(
    onClosed: (K, V) -> Unit = { _, _ -> },
) {
    val iterator = entries.iterator()
    while (iterator.hasNext()) {
        val entry = iterator.next()
        try {
            entry.value.close()
            onClosed(entry.key, entry.value)
            iterator.remove()
        } catch (_: Throwable) {
            // The closed adapter retains failed handles for a later terminal close retry.
        }
    }
}

private fun MutableList<AutoCloseable>.removeAllByIdentity(handles: Set<AutoCloseable>) {
    removeAll { candidate -> handles.any { it === candidate } }
}

private fun GPUBindGroupLeaseRequest.fullscreenPrerequisiteLeaseId(): String? {
    val prefix = "bind-group:fullscreen:"
    if (!leaseId.startsWith(prefix)) return null
    val suffix = leaseId.removePrefix(prefix).substringBefore(":slot:")
    return "uniform-slab:fullscreen:$suffix"
}
