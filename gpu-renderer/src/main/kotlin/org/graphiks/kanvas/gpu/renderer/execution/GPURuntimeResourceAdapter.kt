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

internal enum class GPUPreparedNativeFrameRegistrationFaultPoint {
    BeforeOwnershipTransfer,
    AfterOwnershipTransfer,
    AfterOwnedHandleReservation,
    AfterBorrowedHandleReservation,
}

internal class GPURuntimeResourceAdapter(
    private val requirePreparedResources: Boolean = false,
    private val nativeRegistrationFaultInjector: (GPUPreparedNativeFrameRegistrationFaultPoint) -> Unit = {},
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
        data class Draft(val token: GPUPreparedNativeFrameToken) : OwnedHandleOwner
        data class PreRegistration(val token: GPUPreparedNativeFrameToken) : OwnedHandleOwner
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
        private val leaseLifecycle = payload.leaseLifecycle
        private var leaseReleasedBeforeSubmit = leaseLifecycle == null
        private var leaseSubmitted = false
        private var leaseReleasedAfterCompletion = leaseLifecycle == null
        private var leaseQuarantined = leaseLifecycle == null

        fun releaseLeaseBeforeSubmit(): Boolean {
            if (leaseReleasedBeforeSubmit) return true
            return applyLeaseTransition { requireNotNull(leaseLifecycle).releaseBeforeSubmit() }
                .also { applied -> if (applied) leaseReleasedBeforeSubmit = true }
        }

        fun markLeaseSubmitted(): Boolean {
            if (leaseLifecycle == null) return true
            if (leaseSubmitted) return false
            return applyLeaseTransition(leaseLifecycle::markSubmitted)
                .also { applied -> if (applied) leaseSubmitted = true }
        }

        fun releaseLeaseAfterCompletion(): Boolean {
            if (leaseReleasedAfterCompletion) return true
            return applyLeaseTransition { requireNotNull(leaseLifecycle).releaseAfterCompletion() }
                .also { applied -> if (applied) leaseReleasedAfterCompletion = true }
        }

        fun quarantineLeaseUncertain(): Boolean {
            if (leaseQuarantined || leaseReleasedBeforeSubmit || leaseReleasedAfterCompletion) return true
            return applyLeaseTransition { requireNotNull(leaseLifecycle).quarantineUncertain() }
                .also { applied -> if (applied) leaseQuarantined = true }
        }

        fun terminalizeLeaseForTeardown(): Boolean = when {
            leaseReleasedBeforeSubmit || leaseReleasedAfterCompletion || leaseQuarantined -> true
            state == PreparedNativePayloadState.Submitted || leaseSubmitted -> quarantineLeaseUncertain()
            else -> releaseLeaseBeforeSubmit()
        }

        private fun applyLeaseTransition(
            transition: () -> GPUPreparedNativeFrameLeaseTransition,
        ): Boolean = try {
            transition() == GPUPreparedNativeFrameLeaseTransition.Applied
        } catch (_: Throwable) {
            false
        }

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
                if (handles.any { it === anchor }) {
                    detached += anchor.ownedHandlesSnapshot()
                } else {
                    anchor.detachOwnedHandles(detached)
                }
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

    private data class QuarantinedNativeDraftEntry(
        val token: GPUPreparedNativeFrameToken,
        val draft: GPUPreparedNativeFrameDraft,
        val owned: MutableList<AutoCloseable>,
        val borrowed: List<AutoCloseable>,
    )

    private data class QuarantinedPreRegistrationLedgerEntry(
        val token: GPUPreparedNativeFrameToken,
        val ledger: GPUPreRegistrationNativeHandleLedger,
    )

    private val preparedNativePayloads = linkedMapOf<GPUPreparedNativeFrameToken, PreparedNativePayloadEntry>()
    private val quarantinedNativePayloads = linkedMapOf<GPUPreparedNativeFrameToken, PreparedNativePayloadEntry>()
    private val quarantinedNativeDrafts = IdentityHashMap<
        GPUPreparedNativeFrameDraft,
        QuarantinedNativeDraftEntry,
    >()
    private val quarantinedPreRegistrationLedgers = IdentityHashMap<
        GPUPreRegistrationNativeHandleLedger,
        QuarantinedPreRegistrationLedgerEntry,
    >()
    private val quarantinedPreRegistrationCloseOwners = Collections.newSetFromMap(
        IdentityHashMap<AutoCloseable, Boolean>(),
    )
    private val outputOwnedNativePayloads = linkedMapOf<GPUPreparedNativeFrameToken, PreparedNativePayloadEntry>()
    private val ownedHandleOwners = IdentityHashMap<AutoCloseable, OwnedHandleOwner>()
    private val borrowedHandleTokens = IdentityHashMap<AutoCloseable, MutableSet<GPUPreparedNativeFrameToken>>()
    private val nativePayloadOrdinal = AtomicLong()
    private val nativePayloadRegistrationCount = AtomicLong()
    private var closed = false
    private val liveLeaseIds = linkedSetOf<String>()
    private val uniformSlabs = linkedMapOf<String, GPUBuffer>()
    private val bindGroups = linkedMapOf<String, GPUBindGroup>()
    private val pendingUniformSlabs = linkedMapOf<String, () -> GPUBuffer>()
    private val pendingBindGroups = linkedMapOf<String, () -> GPUBindGroup>()

    internal val activePreparedNativeFramePayloadCount: Int
        @Synchronized get() = preparedNativePayloads.size

    internal val quarantinedPreparedNativeFramePayloadCount: Int
        @Synchronized get() = quarantinedNativePayloads.size + quarantinedNativeDrafts.size +
            quarantinedPreRegistrationLedgers.size + quarantinedPreRegistrationCloseOwners.size

    internal val outputOwnedPreparedNativeFramePayloadCount: Int
        @Synchronized get() = outputOwnedNativePayloads.size

    internal val preparedNativeFramePayloadRegistrationCount: Long
        get() = nativePayloadRegistrationCount.get()

    @Synchronized
    internal fun registerPreparedNativeFrameDraft(
        draft: GPUPreparedNativeFrameDraft,
    ): GPUPreparedNativeFrameRegistration {
        var entry: PreparedNativePayloadEntry? = null
        var adapterOwnsDraft = false
        return try {
            nativePayloadRegistrationCount.incrementAndGet()
            val payload = draft.payload
            val ordinal = nativePayloadOrdinal.incrementAndGet()
            val token = GPUPreparedNativeFrameToken(
                "native-frame-${payload.identity.frameId.value}-generation-" +
                    "${payload.identity.deviceGeneration.value}-payload-$ordinal",
            )
            entry = PreparedNativePayloadEntry(
                token = token,
                payload = payload,
                state = PreparedNativePayloadState.Draft,
            )
            val ownedConflicts = entry.allOwnedHandles().filterTo(
                Collections.newSetFromMap(IdentityHashMap<AutoCloseable, Boolean>()),
            ) { ownedHandleOwners.containsKey(it) || borrowedHandleTokens[it].orEmpty().isNotEmpty() }
            val borrowedConflicts = entry.borrowed.filterTo(
                Collections.newSetFromMap(IdentityHashMap<AutoCloseable, Boolean>()),
            ) { ownedHandleOwners.containsKey(it) }

            nativeRegistrationFaultInjector(
                GPUPreparedNativeFrameRegistrationFaultPoint.BeforeOwnershipTransfer,
            )
            if (!draft.transferOwnershipToAdapter()) {
                return GPUPreparedNativeFrameRegistration.Refused(
                    "invalid.native-frame-payload.draft-ownership",
                    if (draft.isAdapterOwned()) {
                        GPUPreparedNativeFrameRegistration.RefusalOwnership.ReleasedOrAdapterQuarantined
                    } else {
                        GPUPreparedNativeFrameRegistration.RefusalOwnership.CallerRetained
                    },
                )
            }
            adapterOwnsDraft = true
            nativeRegistrationFaultInjector(
                GPUPreparedNativeFrameRegistrationFaultPoint.AfterOwnershipTransfer,
            )

            if (ownedConflicts.isNotEmpty() || borrowedConflicts.isNotEmpty()) {
                entry.removeOwnedHandles(ownedConflicts)
                borrowedConflicts.forEach(entry::removeBorrowedHandle)
                reservePayloadHandles(entry)
                terminalizeAdapterOwnedRegistration(entry)
                return GPUPreparedNativeFrameRegistration.Refused(
                    "unsupported.native-frame-payload.owned-handle-conflict",
                    GPUPreparedNativeFrameRegistration.RefusalOwnership.ReleasedOrAdapterQuarantined,
                )
            }

            reservePayloadHandles(entry)
            nativeRegistrationFaultInjector(
                GPUPreparedNativeFrameRegistrationFaultPoint.AfterOwnedHandleReservation,
            )
            reserveBorrowedHandles(entry)
            nativeRegistrationFaultInjector(
                GPUPreparedNativeFrameRegistrationFaultPoint.AfterBorrowedHandleReservation,
            )
            if (closed) {
                terminalizeAdapterOwnedRegistration(entry)
                return GPUPreparedNativeFrameRegistration.Refused(
                    "unsupported.native-frame-payload.registry-closed",
                    GPUPreparedNativeFrameRegistration.RefusalOwnership.ReleasedOrAdapterQuarantined,
                )
            }
            preparedNativePayloads[token] = entry
            GPUPreparedNativeFrameRegistration.Registered(
                GPUPreparedNativeFrameOwnership(token, this),
            )
        } catch (_: Throwable) {
            entry?.takeIf { adapterOwnsDraft }?.let(::terminalizeAdapterOwnedRegistration)
            GPUPreparedNativeFrameRegistration.Refused(
                "failed.native-frame-payload.registration",
                if (adapterOwnsDraft) {
                    GPUPreparedNativeFrameRegistration.RefusalOwnership.ReleasedOrAdapterQuarantined
                } else {
                    GPUPreparedNativeFrameRegistration.RefusalOwnership.CallerRetained
                },
            )
        }
    }

    @Synchronized
    internal fun quarantinePreparedNativeFrameDraft(draft: GPUPreparedNativeFrameDraft): Boolean {
        if (draft.isAdapterOwned()) return true
        if (quarantinedNativeDrafts.containsKey(draft)) return true
        val ownedConflicts = draft.reservedOwnedHandlesSnapshot().filterTo(
            Collections.newSetFromMap(IdentityHashMap<AutoCloseable, Boolean>()),
        ) { ownedHandleOwners.containsKey(it) || borrowedHandleTokens[it].orEmpty().isNotEmpty() }
        draft.detachPendingOwnedHandles(ownedConflicts)
        if (ownedConflicts.isNotEmpty()) return false
        val pendingOwned = draft.reservedOwnedHandlesSnapshot()
        val borrowed = draft.payload.borrowedHandlesExcludingAnchors()
            .filterNot { ownedHandleOwners.containsKey(it) }
        if (pendingOwned.isEmpty()) {
            if (draft.disposeBeforeRegistration()) return true
        }
        val ordinal = nativePayloadOrdinal.incrementAndGet()
        val token = GPUPreparedNativeFrameToken(
            "native-frame-${draft.payload.identity.frameId.value}-generation-" +
                "${draft.payload.identity.deviceGeneration.value}-draft-quarantine-$ordinal",
        )
        val owner = OwnedHandleOwner.Draft(token)
        pendingOwned.forEach { ownedHandleOwners[it] = owner }
        borrowed.forEach { handle ->
            borrowedHandleTokens.getOrPut(handle) { linkedSetOf() } += token
        }
        quarantinedNativeDrafts[draft] = QuarantinedNativeDraftEntry(
            token,
            draft,
            pendingOwned.toMutableList(),
            borrowed,
        )
        return true
    }

    @Synchronized
    internal fun releaseOrQuarantinePreparedNativeFrameDraft(
        draft: GPUPreparedNativeFrameDraft,
    ): GPUPreparedNativeOwnerTerminalization {
        if (!quarantinePreparedNativeFrameDraft(draft)) {
            return GPUPreparedNativeOwnerTerminalization.CallerRetained
        }
        val entry = quarantinedNativeDrafts[draft]
            ?: return GPUPreparedNativeOwnerTerminalization.ReleasedOrAdapterQuarantined
        retryQuarantinedDraft(entry)
        return GPUPreparedNativeOwnerTerminalization.ReleasedOrAdapterQuarantined
    }

    private fun retryQuarantinedDraft(entry: QuarantinedNativeDraftEntry) {
        val before = entry.draft.reservedOwnedHandlesSnapshot()
        val released = entry.draft.disposeBeforeRegistration()
        val after = entry.draft.reservedOwnedHandlesSnapshot()
        before.filter { candidate -> after.none { it === candidate } }.forEach { handle ->
            if (ownedHandleOwners[handle] == OwnedHandleOwner.Draft(entry.token)) {
                ownedHandleOwners.remove(handle)
            }
            entry.owned.removeAll { it === handle }
        }
        if (!released) return
        quarantinedNativeDrafts.remove(entry.draft)
        entry.borrowed.forEach { handle ->
            borrowedHandleTokens[handle]?.let { tokens ->
                tokens -= entry.token
                if (tokens.isEmpty()) borrowedHandleTokens.remove(handle)
            }
        }
    }

    @Synchronized
    internal fun quarantinePreRegistrationLedger(ledger: GPUPreRegistrationNativeHandleLedger): Boolean {
        if (quarantinedPreRegistrationLedgers.containsKey(ledger)) return true
        val conflicts = ledger.pendingHandlesSnapshot().filterTo(
            Collections.newSetFromMap(IdentityHashMap<AutoCloseable, Boolean>()),
        ) { ownedHandleOwners.containsKey(it) || borrowedHandleTokens[it].orEmpty().isNotEmpty() }
        ledger.detachPendingHandles(conflicts)
        val handles = ledger.pendingHandlesSnapshot()
        if (handles.isEmpty()) return true
        if (!ledger.transferOwnershipToAdapter()) return false
        val ordinal = nativePayloadOrdinal.incrementAndGet()
        val token = GPUPreparedNativeFrameToken("pre-registration-ledger-$ordinal")
        val owner = OwnedHandleOwner.PreRegistration(token)
        handles.forEach { ownedHandleOwners[it] = owner }
        quarantinedPreRegistrationLedgers[ledger] = QuarantinedPreRegistrationLedgerEntry(token, ledger)
        return true
    }

    @Synchronized
    internal fun releaseOrQuarantinePreRegistrationLedger(
        ledger: GPUPreRegistrationNativeHandleLedger,
    ): GPUPreparedNativeOwnerTerminalization {
        if (!quarantinePreRegistrationLedger(ledger)) {
            return if (ledger.isAdapterOwned()) {
                GPUPreparedNativeOwnerTerminalization.ReleasedOrAdapterQuarantined
            } else {
                GPUPreparedNativeOwnerTerminalization.CallerRetained
            }
        }
        quarantinedPreRegistrationLedgers[ledger]?.let(::retryPreRegistrationLedger)
        return GPUPreparedNativeOwnerTerminalization.ReleasedOrAdapterQuarantined
    }

    @Synchronized
    internal fun quarantinePreRegistrationCloseOwner(owner: AutoCloseable): Boolean {
        quarantinedPreRegistrationCloseOwners += owner
        return true
    }

    private fun retryPreRegistrationLedger(entry: QuarantinedPreRegistrationLedgerEntry) {
        val before = entry.ledger.pendingHandlesSnapshot()
        val released = entry.ledger.closeRetainingFailuresByAdapter()
        val after = entry.ledger.pendingHandlesSnapshot()
        before.filter { candidate -> after.none { it === candidate } }.forEach { handle ->
            if (ownedHandleOwners[handle] == OwnedHandleOwner.PreRegistration(entry.token)) {
                ownedHandleOwners.remove(handle)
            }
        }
        if (released) quarantinedPreRegistrationLedgers.remove(entry.ledger)
    }

    private fun terminalizeAdapterOwnedRegistration(entry: PreparedNativePayloadEntry) {
        preparedNativePayloads.remove(entry.token)
        outputOwnedNativePayloads.remove(entry.token)
        val leaseReleased = entry.releaseLeaseBeforeSubmit()
        val handlesReleased = entry.closeAllOwned { releasePayloadHandle(entry.token, it) }
        if (leaseReleased && handlesReleased) {
            releaseBorrowedHandles(entry)
            return
        }
        val owner = OwnedHandleOwner.Payload(entry.token)
        entry.allOwnedHandles().forEach { handle ->
            if (!ownedHandleOwners.containsKey(handle)) ownedHandleOwners[handle] = owner
        }
        reserveBorrowedHandles(entry)
        quarantinedNativePayloads[entry.token] = entry
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

    private fun closePayloadForTeardown(
        token: GPUPreparedNativeFrameToken,
        entry: PreparedNativePayloadEntry,
    ): Boolean {
        val leaseTerminal = entry.terminalizeLeaseForTeardown()
        val handlesClosed = entry.closeAllOwned { releasePayloadHandle(token, it) }
        return leaseTerminal && handlesClosed
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
        val leaseReleased = entry.releaseLeaseBeforeSubmit()
        val handlesReleased = entry.closeAllOwned { releasePayloadHandle(token, it) }
        if (leaseReleased && handlesReleased) {
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
        if (!entry.markLeaseSubmitted()) return false
        entry.state = PreparedNativePayloadState.Submitted
        return true
    }

    @Synchronized
    override fun releasePreparedNativeFramePayload(token: GPUPreparedNativeFrameToken): Boolean {
        if (closed) return false
        val entry = preparedNativePayloads[token] ?: return false
        if (entry.state != PreparedNativePayloadState.Submitted) return false
        val leaseReleased = entry.releaseLeaseAfterCompletion()
        val handlesReleased = entry.closeCompletionOwned { releasePayloadHandle(token, it) }
        if (!leaseReleased || !handlesReleased) {
            preparedNativePayloads.remove(token)
            if (!leaseReleased) entry.quarantineLeaseUncertain()
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
        val quarantined = entry.quarantineLeaseUncertain()
        quarantinedNativePayloads[token] = entry
        return quarantined
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
            if (closePayloadForTeardown(token, entry)) {
                preparedNativePayloads.remove(token)
                releaseBorrowedHandles(entry)
            } else {
                preparedNativePayloads.remove(token)
                quarantinedNativePayloads[token] = entry
            }
        }
        quarantinedNativeDrafts.values.toList().forEach(::retryQuarantinedDraft)
        quarantinedPreRegistrationLedgers.values.toList().forEach(::retryPreRegistrationLedger)
        quarantinedPreRegistrationCloseOwners.toList().forEach { owner ->
            try {
                owner.close()
                quarantinedPreRegistrationCloseOwners.remove(owner)
            } catch (_: Throwable) {
                // Retain the exact close owner so a later adapter close can retry it.
            }
        }
        outputOwnedNativePayloads.toMap().forEach { (token, entry) ->
            if (entry.outputMappingClaimed) return@forEach
            if (closePayloadForTeardown(token, entry)) {
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
            if (closePayloadForTeardown(token, entry)) {
                quarantinedNativePayloads.remove(token)
                releaseBorrowedHandles(entry)
            }
        }
        check(
            quarantinedNativePayloads.isEmpty() && quarantinedNativeDrafts.isEmpty() &&
                quarantinedPreRegistrationLedgers.isEmpty() &&
                quarantinedPreRegistrationCloseOwners.isEmpty() &&
                outputOwnedNativePayloads.values.none(PreparedNativePayloadEntry::outputMappingClaimed) &&
                bindGroups.isEmpty() && uniformSlabs.isEmpty(),
        ) {
            "Native payload teardown remains incomplete: " +
                "${quarantinedNativePayloads.size} payload(s), ${quarantinedNativeDrafts.size} draft(s), " +
                "${quarantinedPreRegistrationLedgers.size} setup ledger(s), " +
                "${quarantinedPreRegistrationCloseOwners.size} close owner(s), " +
                "${outputOwnedNativePayloads.values.count(PreparedNativePayloadEntry::outputMappingClaimed)} " +
                "claimed output mapping(s), ${bindGroups.size} bind group owner(s), " +
                "${uniformSlabs.size} uniform slab owner(s)"
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

private fun GPUPreparedNativeFramePayload.borrowedHandlesExcludingAnchors(): List<AutoCloseable> {
    val anchored = anchoredHandles()
    return ownedHandles(GPUPreparedNativeOperandOwnership.Borrowed)
        .filterNot { candidate -> anchored.any { it === candidate } }
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
