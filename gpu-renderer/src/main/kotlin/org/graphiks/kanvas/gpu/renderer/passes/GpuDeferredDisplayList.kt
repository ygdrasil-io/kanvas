package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

enum class GPUCacheEvictionPolicy {
    LRU,
    FIFO,
    CLOCK,
}

data class GPUDeferredDisplayList(
    val recordingId: String,
    val recordedCommandIds: List<String>,
    val analysisHash: String,
    val layerPlanIds: List<String>,
    val compatibilityKey: GPUDeferredDisplayListCompatibilityKey,
) {
    init {
        require(recordingId.isNotBlank()) { "GPUDeferredDisplayList.recordingId must not be blank" }
        require(analysisHash.isNotBlank()) { "GPUDeferredDisplayList.analysisHash must not be blank" }
        require(compatibilityKey.recordingId == recordingId) {
            "GPUDeferredDisplayList.compatibilityKey recordingId ${compatibilityKey.recordingId} does not match displayList recordingId $recordingId"
        }
    }
}

data class GPUDeferredDisplayListCompatibilityKey(
    val recordingId: String,
    val commandHash: Long,
    val replayCompatibleFields: Set<String>,
    val targetFormatClass: String? = null,
    val capabilityClass: String? = null,
    val deviceIdentity: String? = null,
) {
    init {
        require(recordingId.isNotBlank()) { "GPUDeferredDisplayListCompatibilityKey.recordingId must not be blank" }
    }
}

data class GPUDeferredDisplayListReplayPlan(
    val displayListId: String,
    val composedCtmIdentity: String,
    val intersectionClipIdentity: String,
    val targetSurfaceIdentity: String,
) {
    init {
        require(displayListId.isNotBlank()) { "GPUDeferredDisplayListReplayPlan.displayListId must not be blank" }
        require(composedCtmIdentity.isNotBlank()) { "GPUDeferredDisplayListReplayPlan.composedCtmIdentity must not be blank" }
        require(intersectionClipIdentity.isNotBlank()) { "GPUDeferredDisplayListReplayPlan.intersectionClipIdentity must not be blank" }
        require(targetSurfaceIdentity.isNotBlank()) { "GPUDeferredDisplayListReplayPlan.targetSurfaceIdentity must not be blank" }
    }
}

data class GPUDeferredDisplayListCachePlan(
    val maxEntries: Int,
    val evictionPolicy: GPUCacheEvictionPolicy,
) {
    init {
        require(maxEntries > 0) { "GPUDeferredDisplayListCachePlan.maxEntries must be positive" }
    }

    companion object {
        fun fromPolicyLabel(maxEntries: Int, policyLabel: String): GPUDeferredDisplayListCachePlan {
            require(policyLabel.isNotBlank()) { "eviction policy label must not be blank" }
            val policy = when (policyLabel.lowercase()) {
                "lru" -> GPUCacheEvictionPolicy.LRU
                "fifo" -> GPUCacheEvictionPolicy.FIFO
                "clock" -> GPUCacheEvictionPolicy.CLOCK
                else -> throw IllegalArgumentException("Unrecognised eviction policy: $policyLabel")
            }
            return GPUDeferredDisplayListCachePlan(maxEntries = maxEntries, evictionPolicy = policy)
        }
    }
}

sealed interface GpuDeferredDisplayListReplayResult {
    data class Accepted(val replayPlan: GPUDeferredDisplayListReplayPlan) : GpuDeferredDisplayListReplayResult
    data class Refused(val diagnostic: RefuseDiagnostic) : GpuDeferredDisplayListReplayResult
}

fun checkReplayCompatibility(
    displayList: GPUDeferredDisplayList,
    replayKey: GPUDeferredDisplayListCompatibilityKey,
): GpuDeferredDisplayListReplayResult = checkReplay(
    displayList, replayKey, "checkReplayCompatibility",
)

fun replayDeferred(
    displayList: GPUDeferredDisplayList,
    replayKey: GPUDeferredDisplayListCompatibilityKey,
): GpuDeferredDisplayListReplayResult = checkReplay(
    displayList, replayKey, "replayDeferred",
)

private fun checkReplay(
    displayList: GPUDeferredDisplayList,
    replayKey: GPUDeferredDisplayListCompatibilityKey,
    origin: String,
): GpuDeferredDisplayListReplayResult {
    val storedKey = displayList.compatibilityKey
    val mismatches = mutableListOf<String>()

    if (storedKey.recordingId != replayKey.recordingId) {
        mismatches.add("recordingId")
    }
    if (storedKey.commandHash != replayKey.commandHash) {
        mismatches.add("commandHash")
    }
    if (storedKey.targetFormatClass != null && replayKey.targetFormatClass != null &&
        storedKey.targetFormatClass != replayKey.targetFormatClass
    ) {
        mismatches.add("targetFormatClass")
    }
    if (storedKey.capabilityClass != null && replayKey.capabilityClass != null &&
        storedKey.capabilityClass != replayKey.capabilityClass
    ) {
        mismatches.add("capabilityClass")
    }
    if (storedKey.deviceIdentity != null && replayKey.deviceIdentity != null &&
        storedKey.deviceIdentity != replayKey.deviceIdentity
    ) {
        mismatches.add("deviceIdentity")
    }

    if (mismatches.isNotEmpty()) {
        return GpuDeferredDisplayListReplayResult.Refused(
            RefuseDiagnostic(
                code = "unsupported.recording.deferred_incompatible_replay",
                message = "An incompatible replay was rejected for recording ${displayList.recordingId} from $origin; mismatched fields: ${mismatches.joinToString(",")}",
                stage = "recording",
                terminal = true,
            ),
        )
    }

    return GpuDeferredDisplayListReplayResult.Accepted(
        GPUDeferredDisplayListReplayPlan(
            displayListId = displayList.recordingId,
            composedCtmIdentity = "ctm-replay:${replayKey.replayCompatibleFields.contains("composedCtm")}",
            intersectionClipIdentity = "clip-replay:${replayKey.replayCompatibleFields.contains("intersectionClip")}",
            targetSurfaceIdentity = "target-replay:${replayKey.replayCompatibleFields.contains("targetSurface")}",
        ),
    )
}

private const val NONE_DUMP_VALUE = "none"

fun GPUDeferredDisplayList.dumpLines(): List<String> =
    listOf(
        "passes.deferred-dl id=$recordingId " +
            "commands=${if (recordedCommandIds.isEmpty()) NONE_DUMP_VALUE else recordedCommandIds.joinToString(",")} " +
            "analysis=$analysisHash " +
            "layers=${if (layerPlanIds.isEmpty()) NONE_DUMP_VALUE else layerPlanIds.joinToString(",")}",
    )

fun GPUDeferredDisplayListCompatibilityKey.dumpLines(): List<String> =
    listOf(
        "passes.deferred-dl-compat-key recording=$recordingId " +
            "commandHash=$commandHash " +
            "format=${targetFormatClass ?: NONE_DUMP_VALUE} " +
            "capability=${capabilityClass ?: NONE_DUMP_VALUE} " +
            "device=${deviceIdentity ?: NONE_DUMP_VALUE} " +
            "compatibleFields=${if (replayCompatibleFields.isEmpty()) NONE_DUMP_VALUE else replayCompatibleFields.sorted().joinToString(",")}",
    )

fun GPUDeferredDisplayListCachePlan.dumpLines(): List<String> =
    listOf(
        "passes.deferred-dl-cache maxEntries=$maxEntries policy=$evictionPolicy",
    )
