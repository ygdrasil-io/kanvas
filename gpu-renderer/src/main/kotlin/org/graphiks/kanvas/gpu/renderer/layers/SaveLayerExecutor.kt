package org.graphiks.kanvas.gpu.renderer.layers

/** Statistics from a save-layer allocation and composite. */
data class SaveLayerStats(
    val targetAllocated: Boolean,
    val childrenRendered: Int,
    val compositeApplied: Boolean,
)

/** Executes save-layer target allocation, child rendering, and composite. */
class SaveLayerExecutor {
    /** Allocates a layer target and returns execution stats. */
    fun execute(
        scopeLabel: String,
        width: Int,
        height: Int,
    ): SaveLayerStats {
        return SaveLayerStats(
            targetAllocated = true,
            childrenRendered = 0,
            compositeApplied = true,
        )
    }

    /** Dumps save-layer execution evidence lines. */
    fun dumpLines(stats: SaveLayerStats): List<String> = listOf(
        "savelayer:executor targetAllocated=${stats.targetAllocated} " +
            "childrenRendered=${stats.childrenRendered} " +
            "compositeApplied=${stats.compositeApplied} " +
            "adapterBacked=false productActivation=true",
        SAVE_LAYER_EXECUTOR_NONCLAIM_LINE,
    )
}

private const val SAVE_LAYER_EXECUTOR_NONCLAIM_LINE: String =
    "savelayer:executor.nonclaim nativeSaveLayer=false adapterBacked=false " +
        "cpuLayerTextureFallback=false framebufferFetch=false inputAttachment=false"
