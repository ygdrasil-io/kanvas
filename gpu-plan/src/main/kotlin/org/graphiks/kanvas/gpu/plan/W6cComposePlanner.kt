package org.graphiks.kanvas.gpu.plan

/** Small W6c contextual-binding seam; it adds no graph, allocator, cache, or submit authority. */
internal object W6cComposePlanner {
    fun colorUniformIdentity(execution: ColorFilterExecutionPlanV1): String =
        "w6c.color-filter:${execution.canonicalIdentity}"
}
