package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

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
    val evictionPolicy: String,
) {
    init {
        require(maxEntries > 0) { "GPUDeferredDisplayListCachePlan.maxEntries must be positive" }
        require(evictionPolicy.isNotBlank()) { "GPUDeferredDisplayListCachePlan.evictionPolicy must not be blank" }
    }
}

sealed interface GpuDeferredDisplayListReplayResult {
    data class Accepted(val replayPlan: GPUDeferredDisplayListReplayPlan) : GpuDeferredDisplayListReplayResult
    data class Refused(val diagnostic: RefuseDiagnostic) : GpuDeferredDisplayListReplayResult
}

fun checkReplayCompatibility(
    displayList: GPUDeferredDisplayList,
    replayKey: GPUDeferredDisplayListCompatibilityKey,
): GpuDeferredDisplayListReplayResult {
    if (displayList.compatibilityKey.recordingId != replayKey.recordingId ||
        displayList.compatibilityKey.commandHash != replayKey.commandHash
    ) {
        return GpuDeferredDisplayListReplayResult.Refused(
            RefuseDiagnostic(
                code = "unsupported.recording.deferred_incompatible_replay",
                message = "An incompatible replay was rejected; mismatched recordingId or commandHash",
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

fun replayDeferred(
    displayList: GPUDeferredDisplayList,
    replayKey: GPUDeferredDisplayListCompatibilityKey,
): GpuDeferredDisplayListReplayResult {
    if (displayList.compatibilityKey.recordingId != replayKey.recordingId) {
        return GpuDeferredDisplayListReplayResult.Refused(
            RefuseDiagnostic(
                code = "unsupported.recording.deferred_incompatible_replay",
                message = "An incompatible replay was rejected for recording ${displayList.recordingId}; mismatched fields: recordingId",
                stage = "recording",
                terminal = true,
            ),
        )
    }
    if (displayList.compatibilityKey.commandHash != replayKey.commandHash) {
        return GpuDeferredDisplayListReplayResult.Refused(
            RefuseDiagnostic(
                code = "unsupported.recording.deferred_incompatible_replay",
                message = "An incompatible replay was rejected for recording ${displayList.recordingId}; mismatched fields: commandHash",
                stage = "recording",
                terminal = true,
            ),
        )
    }
    return GpuDeferredDisplayListReplayResult.Accepted(
        GPUDeferredDisplayListReplayPlan(
            displayListId = displayList.recordingId,
            composedCtmIdentity = "ctm-replay:${displayList.recordingId}",
            intersectionClipIdentity = "clip-replay:${displayList.recordingId}",
            targetSurfaceIdentity = "target-replay:${displayList.recordingId}",
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
            "compatibleFields=${if (replayCompatibleFields.isEmpty()) NONE_DUMP_VALUE else replayCompatibleFields.sorted().joinToString(",")}",
    )

fun GPUDeferredDisplayListCachePlan.dumpLines(): List<String> =
    listOf(
        "passes.deferred-dl-cache maxEntries=$maxEntries policy=$evictionPolicy",
    )
