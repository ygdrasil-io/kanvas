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
        "passes.mt-arena id=${arenaId.value} thread=${threadId.value} capacity=$capacity fragments=$fragmentCount",
    ) + fragments.flatMap { frag -> frag.dumpLines() }
}

data class GpuRecordingMergeAnalysis(
    val mergedFragments: List<GpuRecordingFragment>,
    val refusedPairs: List<GpuRecordingMergeRefusal>,
    val arenaCount: Int,
) {
    val fragmentCount: Int
        get() = mergedFragments.size

    val totalCommands: Int
        get() = mergedFragments.sumOf { frag -> frag.commandCount }

    fun dumpLines(): List<String> = listOf(
        "passes.mt-recording mergedFragments=$fragmentCount totalCommands=$totalCommands arenaCount=$arenaCount",
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

fun mergeArenas(arenas: List<GpuRecordingArena>): GpuRecordingMergeAnalysis {
    val allFragments = arenas.flatMap { arena -> arena.fragments }
    val sorted = allFragments.sortedWith(
        compareBy<GpuRecordingFragment> { frag -> frag.beginToken.sequenceNumber }
            .thenBy { frag -> frag.endToken.sequenceNumber }
    )

    val refusedPairs = mutableListOf<GpuRecordingMergeRefusal>()

    val seenOrder = mutableMapOf<GpuRecordingArenaId, Long>()
    val seenTokens = mutableSetOf<Long>()

    for (frag in sorted) {
        val lastOrder = seenOrder[frag.arenaId]
        val beginSeq = frag.beginToken.sequenceNumber

        if (seenTokens.contains(beginSeq)) {
            refusedPairs.add(
                GpuRecordingMergeRefusal(
                    arenaId = frag.arenaId,
                    fragmentId = frag.fragmentId,
                    diagnostic = RefuseDiagnostic(
                        code = "unsupported.recording.mt_duplicate_token",
                        message = "Duplicate begin token sequence $beginSeq for fragment ${frag.fragmentId}",
                        stage = "mt-recording",
                        terminal = true,
                    ),
                ),
            )
        }

        seenTokens.add(beginSeq)
        if (lastOrder == null || beginSeq > lastOrder) {
            seenOrder[frag.arenaId] = beginSeq
        }
    }

    return GpuRecordingMergeAnalysis(
        mergedFragments = sorted,
        refusedPairs = refusedPairs,
        arenaCount = arenas.size,
    )
}

object GpuMultiThreadedRecordingReason {
    const val DUPLICATE_TOKEN = "unsupported.recording.mt_duplicate_token"
    const val FRAGMENT_TOKEN_INVERSION = "unsupported.recording.mt_token_inversion"
}
