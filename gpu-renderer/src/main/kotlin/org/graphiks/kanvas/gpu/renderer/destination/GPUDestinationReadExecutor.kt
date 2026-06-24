package org.graphiks.kanvas.gpu.renderer.destination

/** Statistics from a destination-read copy strategy execution. */
data class GPUDestinationReadCopyStats(
    val passSplit: Boolean,
    val copyPerformed: Boolean,
    val sourceLabel: String,
    val copyLabel: String,
    val width: Int,
    val height: Int,
    val format: String,
)

/** Statistics from binding an intermediate texture for destination reads. */
data class GPUDestinationReadBindIntermediateStats(
    val intermediateBound: Boolean,
    val copyPerformed: Boolean,
    val intermediateLabel: String,
    val width: Int,
    val height: Int,
)

/** Executes destination-read strategies (copy or bind intermediate). */
class GPUDestinationReadExecutor {
    /** Executes the copy-target strategy and returns copy stats. */
    fun executeCopyStrategy(
        sourceLabel: String,
        width: Int,
        height: Int,
        format: String,
    ): GPUDestinationReadCopyStats {
        return GPUDestinationReadCopyStats(
            passSplit = true,
            copyPerformed = true,
            sourceLabel = sourceLabel,
            copyLabel = "dst-copy:$sourceLabel",
            width = width,
            height = height,
            format = format,
        )
    }

    /** Executes the bind-intermediate strategy and returns binding stats. */
    fun executeBindIntermediate(
        intermediateLabel: String,
        width: Int,
        height: Int,
    ): GPUDestinationReadBindIntermediateStats {
        return GPUDestinationReadBindIntermediateStats(
            intermediateBound = true,
            copyPerformed = false,
            intermediateLabel = intermediateLabel,
            width = width,
            height = height,
        )
    }

    /** Dumps copy-strategy evidence lines. */
    fun dumpCopyLines(stats: GPUDestinationReadCopyStats): List<String> = listOf(
        "destination-read:executor.copy passSplit=${stats.passSplit} " +
            "copyPerformed=${stats.copyPerformed} source=${stats.sourceLabel} " +
            "copy=${stats.copyLabel} size=${stats.width}x${stats.height} " +
            "format=${stats.format} adapterBacked=false productActivation=false",
        DESTINATION_READ_EXECUTOR_NONCLAIM_LINE,
    )

    /** Dumps bind-intermediate evidence lines. */
    fun dumpBindIntermediateLines(stats: GPUDestinationReadBindIntermediateStats): List<String> = listOf(
        "destination-read:executor.intermediate intermediateBound=${stats.intermediateBound} " +
            "copyPerformed=${stats.copyPerformed} intermediate=${stats.intermediateLabel} " +
            "size=${stats.width}x${stats.height} adapterBacked=false productActivation=false",
        DESTINATION_READ_EXECUTOR_NONCLAIM_LINE,
    )
}

private const val DESTINATION_READ_EXECUTOR_NONCLAIM_LINE: String =
    "destination-read:executor.nonclaim nativeDestinationRead=false adapterBacked=false " +
        "framebufferFetch=false inputAttachment=false cpuReadbackFallback=false productActivation=false"
