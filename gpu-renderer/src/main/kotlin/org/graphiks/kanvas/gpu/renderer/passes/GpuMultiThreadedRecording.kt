package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

@JvmInline
value class GpuRecordingArenaId(val value: String) {
    init {
        require(value.isNotBlank()) { "GpuRecordingArenaId.value must not be blank" }
    }
}

@JvmInline
value class GpuRecordingThreadId(val value: String) {
    init {
        require(value.isNotBlank()) { "GpuRecordingThreadId.value must not be blank" }
    }
}

data class GpuRecordingToken(
    val sequenceNumber: Long,
    val issuedAtEpochMs: Long,
) {
    init {
        require(sequenceNumber > 0) { "GpuRecordingToken.sequenceNumber must be positive" }
    }
}

class GpuRecordingTokenIssuer {
    private var nextSequence: Long = 1

    fun issue(): GpuRecordingToken {
        val seq = nextSequence++
        return GpuRecordingToken(sequenceNumber = seq, issuedAtEpochMs = 0L)
    }
}

data class GpuRecordingFragment(
    val fragmentId: String,
    val arenaId: GpuRecordingArenaId,
    val commandCount: Int,
    val beginToken: GpuRecordingToken,
    val endToken: GpuRecordingToken,
    /**
     * Ids of atomic scopes (clip atomic group, layer, or destination-read scope) whose commands
     * appear in this fragment. A scope id appearing in more than one fragment indicates an unsafe
     * split (KGPU-M40-002 refusal `fragment_split_unsafe`).
     */
    val atomicScopeIds: Set<String> = emptySet(),
    /** Ids of other fragments this fragment depends on; used for merge-cycle detection. */
    val dependsOnFragmentIds: Set<String> = emptySet(),
) {
    init {
        require(fragmentId.isNotBlank()) { "GpuRecordingFragment.fragmentId must not be blank" }
        require(commandCount >= 0) { "GpuRecordingFragment.commandCount must be non-negative" }
        require(beginToken.sequenceNumber < endToken.sequenceNumber) {
            "GpuRecordingFragment.beginToken must precede endToken"
        }
    }
}

class GpuRecordingArena(
    val arenaId: GpuRecordingArenaId,
    val threadId: GpuRecordingThreadId,
    val capacity: Int,
    fragments: List<GpuRecordingFragment>,
    /** Peak transient thread-bound allocation bytes for this arena. */
    val allocationBytes: Long = 0L,
    /** Whether the arena's thread-bound allocations were released after fragment production. */
    val released: Boolean = false,
) {
    val fragments: List<GpuRecordingFragment> = fragments.toList()

    val fragmentCount: Int
        get() = fragments.size

    val fragmentIds: List<String>
        get() = fragments.map { frag -> frag.fragmentId }

    val totalCommands: Int
        get() = fragments.sumOf { frag -> frag.commandCount }

    init {
        require(capacity > 0) { "GpuRecordingArena.capacity must be positive" }
    }

    fun dumpLines(): List<String> = listOf(
        "passes.mt-arena id=${arenaId.value} thread=${threadId.value} capacity=$capacity fragments=$fragmentCount " +
            "allocBytes=$allocationBytes released=$released",
    ) + fragments.flatMap { frag -> frag.dumpLines() }
}

data class GpuRecordingMergeAnalysis(
    val mergedFragments: List<GpuRecordingFragment>,
    val refusedPairs: List<GpuRecordingMergeRefusal>,
    val arenaCount: Int,
    /** True when the merge was aborted due to a cross-fragment dependency cycle. */
    val aborted: Boolean = false,
) {
    val fragmentCount: Int
        get() = mergedFragments.size

    val totalCommands: Int
        get() = mergedFragments.sumOf { frag -> frag.commandCount }

    fun dumpLines(): List<String> = listOf(
        "passes.mt-recording mergedFragments=$fragmentCount totalCommands=$totalCommands " +
            "arenaCount=$arenaCount aborted=$aborted",
    ) + mergedFragments.flatMap { frag -> frag.dumpLines() } +
        refusedPairs.flatMap { refusal -> refusal.dumpLines() }
}

data class GpuRecordingMergeRefusal(
    val arenaId: GpuRecordingArenaId,
    val fragmentId: String,
    val diagnostic: RefuseDiagnostic,
) {
    fun dumpLines(): List<String> = listOf(
        "passes.mt-recording.refused arena=${arenaId.value} fragment=$fragmentId " +
            "code=${diagnostic.code} message=${diagnostic.message} " +
            "stage=${diagnostic.stage} terminal=${diagnostic.terminal}",
    )
}

fun GpuRecordingFragment.dumpLines(): List<String> = listOf(
    "passes.mt-recording.fragment id=$fragmentId arena=${arenaId.value} " +
        "commands=$commandCount " +
        "beginSeq=${beginToken.sequenceNumber} endSeq=${endToken.sequenceNumber}",
)

/**
 * Deterministically merges thread-bound recording arenas into a single ordered fragment list.
 *
 * Fragments are stably ordered by begin token sequence (then end token, then id) so identical input
 * produces identical output regardless of arena/thread submission order. The merge refuses unsafe
 * splits (an atomic scope spanning more than one fragment) and aborts on cross-fragment dependency
 * cycles, per KGPU-M40-002.
 */
fun mergeArenas(arenas: List<GpuRecordingArena>): GpuRecordingMergeAnalysis {
    val allFragments = arenas.flatMap { arena -> arena.fragments }
    val sorted = allFragments.sortedWith(
        compareBy<GpuRecordingFragment> { frag -> frag.beginToken.sequenceNumber }
            .thenBy { frag -> frag.endToken.sequenceNumber }
            .thenBy { frag -> frag.fragmentId },
    )

    val refusedPairs = mutableListOf<GpuRecordingMergeRefusal>()

    // Unsafe split: an atomic scope (clip atomic group / layer / dst-read scope) whose commands are
    // distributed across more than one fragment cannot be merged safely.
    val fragmentsByScope = linkedMapOf<String, MutableList<GpuRecordingFragment>>()
    for (frag in sorted) {
        for (scope in frag.atomicScopeIds.sorted()) {
            fragmentsByScope.getOrPut(scope) { mutableListOf() }.add(frag)
        }
    }
    for ((scope, frags) in fragmentsByScope) {
        if (frags.map { it.fragmentId }.toSet().size > 1) {
            for (frag in frags) {
                refusedPairs.add(
                    GpuRecordingMergeRefusal(
                        arenaId = frag.arenaId,
                        fragmentId = frag.fragmentId,
                        diagnostic = RefuseDiagnostic(
                            code = GpuMultiThreadedRecordingReason.FRAGMENT_SPLIT_UNSAFE,
                            message = "Atomic scope '$scope' split across fragments " +
                                frags.joinToString(",") { it.fragmentId } +
                                "; recording fragment split is unsafe",
                            stage = "mt-recording.merge",
                            terminal = true,
                        ),
                    ),
                )
            }
        }
    }

    // Cross-fragment dependency cycle: abort the merge.
    val cycle = detectFragmentDependencyCycle(sorted)
    if (cycle != null) {
        for (fragId in cycle.distinct()) {
            val frag = sorted.first { it.fragmentId == fragId }
            refusedPairs.add(
                GpuRecordingMergeRefusal(
                    arenaId = frag.arenaId,
                    fragmentId = frag.fragmentId,
                    diagnostic = RefuseDiagnostic(
                        code = GpuMultiThreadedRecordingReason.FRAGMENT_MERGE_CYCLE,
                        message = "Cross-fragment dependency cycle detected: " +
                            cycle.joinToString(" -> "),
                        stage = "mt-recording.merge",
                        terminal = true,
                    ),
                ),
            )
        }
    }

    return GpuRecordingMergeAnalysis(
        mergedFragments = sorted,
        refusedPairs = refusedPairs,
        arenaCount = arenas.size,
        aborted = cycle != null,
    )
}

/** Returns a cyclic fragment-id path if the dependency graph contains a cycle, else null. */
private fun detectFragmentDependencyCycle(
    fragments: List<GpuRecordingFragment>,
): List<String>? {
    val byId = fragments.associateBy { it.fragmentId }
    val visiting = mutableSetOf<String>()
    val visited = mutableSetOf<String>()
    val stack = mutableListOf<String>()

    fun dfs(id: String): List<String>? {
        if (id in visited) return null
        if (id in visiting) {
            val start = stack.indexOf(id)
            return if (start >= 0) stack.subList(start, stack.size).toList() + id else listOf(id, id)
        }
        val frag = byId[id] ?: return null
        visiting.add(id)
        stack.add(id)
        for (dep in frag.dependsOnFragmentIds.sorted()) {
            val found = dfs(dep)
            if (found != null) return found
        }
        stack.removeAt(stack.size - 1)
        visiting.remove(id)
        visited.add(id)
        return null
    }

    for (frag in fragments) {
        val found = dfs(frag.fragmentId)
        if (found != null) return found
    }
    return null
}

/** Recording concurrency telemetry (KGPU-M40-002 scope + evidence). */
data class GpuConcurrencyTelemetry(
    val fragmentCount: Int,
    val mergeDurationMs: Double,
    val contentionEvents: Int,
) {
    fun dumpLines(): List<String> = listOf(
        "passes.mt-telemetry fragmentCount=$fragmentCount mergeDurationMs=$mergeDurationMs " +
            "contentionEvents=$contentionEvents",
    )
}

/** Derives concurrency telemetry from a merge analysis; contention events are refused fragment merges. */
fun buildConcurrencyTelemetry(
    analysis: GpuRecordingMergeAnalysis,
    mergeDurationMs: Double,
): GpuConcurrencyTelemetry = GpuConcurrencyTelemetry(
    fragmentCount = analysis.fragmentCount,
    mergeDurationMs = mergeDurationMs,
    contentionEvents = analysis.refusedPairs.size,
)

/** Thread-bound arena memory release report (KGPU-M40-002 evidence: zero-leak after production). */
data class GpuArenaReleaseReport(
    val totalAllocationBytes: Long,
    val leakedBytes: Long,
    val allReleased: Boolean,
) {
    fun dumpLines(): List<String> = listOf(
        "passes.mt-arena-release totalAllocBytes=$totalAllocationBytes " +
            "leakedBytes=$leakedBytes allReleased=$allReleased",
    )
}

/** Reports arena allocation totals and any bytes leaked by arenas not released after production. */
fun arenaReleaseReport(arenas: List<GpuRecordingArena>): GpuArenaReleaseReport {
    val total = arenas.sumOf { arena -> arena.allocationBytes }
    val leaked = arenas.filter { arena -> !arena.released }.sumOf { arena -> arena.allocationBytes }
    return GpuArenaReleaseReport(
        totalAllocationBytes = total,
        leakedBytes = leaked,
        allReleased = arenas.all { arena -> arena.released },
    )
}

object GpuMultiThreadedRecordingReason {
    const val FRAGMENT_SPLIT_UNSAFE = "unsupported.recording.fragment_split_unsafe"
    const val FRAGMENT_MERGE_CYCLE = "unsupported.recording.fragment_merge_cycle"
}
