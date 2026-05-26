package org.skia.pipeline

data class ProductionRouteDiagnostics(
    val mode: String,
    val backend: BackendKind,
    val drawKind: String,
    val selectedRoute: String,
    val fallbackRoute: String,
    val fallbackReason: String?,
    val coveragePlan: String,
    val touchedPixels: Int? = null,
    val pipelineKey: String? = null,
    val cacheCounters: String? = null,
) {
    fun dump(): String = buildString {
        appendLine("ProductionRouteDiagnostics(v1)")
        appendLine("mode=$mode")
        appendLine("backend=$backend")
        appendLine("drawKind=$drawKind")
        appendLine("selectedRoute=$selectedRoute")
        appendLine("fallbackRoute=$fallbackRoute")
        appendLine("fallbackReason=${fallbackReason ?: "none"}")
        appendLine("coveragePlan=$coveragePlan")
        appendLine("touchedPixels=${touchedPixels ?: "n/a"}")
        appendLine("pipelineKey=${pipelineKey ?: "none"}")
        appendLine("cacheCounters=${cacheCounters ?: "none"}")
    }.trimEnd()
}
