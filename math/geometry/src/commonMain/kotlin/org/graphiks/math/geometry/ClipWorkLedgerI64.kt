package org.graphiks.math.geometry

/** Immutable cumulative clip preparation accounting. */
public data class ClipWorkUsageI64(
    public val attemptedEdgeCountI64: Long = 0L,
    public val emittedVertexCountI64: Long = 0L,
    public val emittedIndexCountI64: Long = 0L,
    public val snapshotByteCountI64: Long = 0L,
) { init { require(attemptedEdgeCountI64 >= 0L); require(emittedVertexCountI64 >= 0L); require(emittedIndexCountI64 >= 0L); require(snapshotByteCountI64 >= 0L) } }

/** Stable reason for a transactional clip preparation refusal. */
public enum class ClipPreparationResourceLimitReason {
    EntryAttemptedEdgeLimit, StackAttemptedEdgeLimit, FrameAttemptedEdgeLimit,
    EntryVertexLimit, StackVertexLimit, FrameVertexLimit,
    EntryIndexLimit, StackIndexLimit, FrameIndexLimit,
    EntrySnapshotByteLimit, StackSnapshotByteLimit, FrameSnapshotByteLimit, HostSizeOverflow,
}

internal class ClipPreparationAbort(val reason: ClipPreparationResourceLimitReason) : RuntimeException()

/** Mutable ledger confined to the module; callers can publish only immutable snapshots. */
internal class ClipWorkLedgerI64(
    stackWorkUsageBeforeI64: ClipWorkUsageI64,
    frameWorkUsageBeforeI64: ClipWorkUsageI64,
    private val policyF64: ClipPreparationPolicyF64,
) {
    private var stackUsageI64 = stackWorkUsageBeforeI64
    private var frameUsageI64 = frameWorkUsageBeforeI64
    init { requireStackI64(stackUsageI64); requireFrameI64(frameUsageI64) }
    fun beginEntryI64(entryWorkUsageBeforeI64: ClipWorkUsageI64): ClipEntryWorkLedgerI64 { requireEntryI64(entryWorkUsageBeforeI64); return ClipEntryWorkLedgerI64(this, entryWorkUsageBeforeI64) }
    fun snapshotStackUsageAfterI64(): ClipWorkUsageI64 = stackUsageI64
    fun snapshotFrameUsageAfterI64(): ClipWorkUsageI64 = frameUsageI64
    internal fun debitI64(entryI64: ClipEntryWorkLedgerI64, deltaI64: ClipWorkUsageI64) {
        val nextEntryI64 = addClipUsageI64(entryI64.usageI64, deltaI64)
        val nextStackI64 = addClipUsageI64(stackUsageI64, deltaI64)
        val nextFrameI64 = addClipUsageI64(frameUsageI64, deltaI64)
        requireEntryI64(nextEntryI64); requireStackI64(nextStackI64); requireFrameI64(nextFrameI64)
        entryI64.usageI64 = nextEntryI64; stackUsageI64 = nextStackI64; frameUsageI64 = nextFrameI64
    }
    private fun requireEntryI64(usageI64: ClipWorkUsageI64) = requireUsageI64(usageI64, policyF64.limitsI32.maxAttemptedEdgesPerEntryI32, policyF64.limitsI32.maxEmittedVertexCountPerEntryI32, policyF64.limitsI32.maxEmittedIndexCountPerEntryI32, policyF64.limitsI64.maxSnapshotByteCountPerEntryI64, ClipPreparationResourceLimitReason.EntryAttemptedEdgeLimit, ClipPreparationResourceLimitReason.EntryVertexLimit, ClipPreparationResourceLimitReason.EntryIndexLimit, ClipPreparationResourceLimitReason.EntrySnapshotByteLimit)
    private fun requireStackI64(usageI64: ClipWorkUsageI64) = requireUsageI64(usageI64, policyF64.limitsI32.maxAttemptedEdgesPerStackI32, policyF64.limitsI32.maxEmittedVertexCountPerStackI32, policyF64.limitsI32.maxEmittedIndexCountPerStackI32, policyF64.limitsI64.maxSnapshotByteCountPerStackI64, ClipPreparationResourceLimitReason.StackAttemptedEdgeLimit, ClipPreparationResourceLimitReason.StackVertexLimit, ClipPreparationResourceLimitReason.StackIndexLimit, ClipPreparationResourceLimitReason.StackSnapshotByteLimit)
    private fun requireFrameI64(usageI64: ClipWorkUsageI64) = requireUsageI64(usageI64, policyF64.limitsI32.maxAttemptedEdgesPerFrameI32, policyF64.limitsI32.maxEmittedVertexCountPerFrameI32, policyF64.limitsI32.maxEmittedIndexCountPerFrameI32, policyF64.limitsI64.maxSnapshotByteCountPerFrameI64, ClipPreparationResourceLimitReason.FrameAttemptedEdgeLimit, ClipPreparationResourceLimitReason.FrameVertexLimit, ClipPreparationResourceLimitReason.FrameIndexLimit, ClipPreparationResourceLimitReason.FrameSnapshotByteLimit)
}

internal class ClipEntryWorkLedgerI64(private val parentI64: ClipWorkLedgerI64, internal var usageI64: ClipWorkUsageI64) {
    fun debitBeforeEmissionI64(deltaI64: ClipWorkUsageI64) { parentI64.debitI64(this, deltaI64) }
    fun snapshotUsageAfterI64(): ClipWorkUsageI64 = usageI64
}

private fun requireUsageI64(usageI64: ClipWorkUsageI64, attemptedLimitI32: Int, vertexLimitI32: Int, indexLimitI32: Int, byteLimitI64: Long, attemptedReason: ClipPreparationResourceLimitReason, vertexReason: ClipPreparationResourceLimitReason, indexReason: ClipPreparationResourceLimitReason, byteReason: ClipPreparationResourceLimitReason) {
    if (usageI64.attemptedEdgeCountI64 > attemptedLimitI32.toLong()) throw ClipPreparationAbort(attemptedReason)
    if (usageI64.emittedVertexCountI64 > vertexLimitI32.toLong()) throw ClipPreparationAbort(vertexReason)
    if (usageI64.emittedIndexCountI64 > indexLimitI32.toLong()) throw ClipPreparationAbort(indexReason)
    if (usageI64.snapshotByteCountI64 > byteLimitI64) throw ClipPreparationAbort(byteReason)
}

private fun addClipUsageI64(firstI64: ClipWorkUsageI64, secondI64: ClipWorkUsageI64): ClipWorkUsageI64 = try {
    ClipWorkUsageI64(checkedClipAddI64(firstI64.attemptedEdgeCountI64, secondI64.attemptedEdgeCountI64), checkedClipAddI64(firstI64.emittedVertexCountI64, secondI64.emittedVertexCountI64), checkedClipAddI64(firstI64.emittedIndexCountI64, secondI64.emittedIndexCountI64), checkedClipAddI64(firstI64.snapshotByteCountI64, secondI64.snapshotByteCountI64))
} catch (_: IllegalStateException) { throw ClipPreparationAbort(ClipPreparationResourceLimitReason.HostSizeOverflow) }

private fun checkedClipAddI64(firstI64: Long, secondI64: Long): Long { if (firstI64 < 0L || secondI64 < 0L || firstI64 > Long.MAX_VALUE - secondI64) throw IllegalStateException(); return firstI64 + secondI64 }
