package org.graphiks.kanvas.gpu.renderer.filters

import java.util.Collections

/** Mask-filter kind accepted by the prepared composite scaffold. */
enum class GPUPreparedMaskFilterKind {
    Blur,
    Shader,
    Table,
}

/** Coverage format produced by a mask filter. */
enum class GPUPreparedCoverageFormat {
    A8,
}

/** Planned mask-filter coverage execution for one captured draw. */
class GPUPreparedMaskFilterPlan(
    val kind: GPUPreparedMaskFilterKind,
    val coverageFormat: GPUPreparedCoverageFormat,
    val executionIdentity: String,
    tableEntries: List<Int> = emptyList(),
) {
    val tableEntries: List<Int> =
        Collections.unmodifiableList(tableEntries.toList())
}

/** Lowering result for a normalized mask filter. */
sealed interface GPUPreparedMaskFilterLowering {
    data class Ready(val plan: GPUPreparedMaskFilterPlan) :
        GPUPreparedMaskFilterLowering

    class Refused(
        val code: String,
        facts: Map<String, String>,
    ) : GPUPreparedMaskFilterLowering {
        val facts: Map<String, String> =
            Collections.unmodifiableMap(LinkedHashMap(facts))
    }
}
