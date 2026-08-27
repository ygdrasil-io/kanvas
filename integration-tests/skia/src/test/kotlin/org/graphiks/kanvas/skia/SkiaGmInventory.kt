package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.pipeline.RuntimeEffectWgsl4kWiring
import java.io.File
import java.util.Locale

/** Renderer observations supplied by the inventory replay; deliberately has no fallback semantics. */
data class InventoryRenderEvidence(
    val attempted: Boolean,
    val renderSucceeded: Boolean,
    val terminalFailure: Boolean,
    val operationCount: Int,
    val diagnostics: List<String> = emptyList(),
    val route: String = "gpu",
) {
    init { require(operationCount >= 0) { "operationCount must be non-negative" } }
}

data class SkiaGmInventoryRow(
    val name: String,
    val family: String,
    val referenceName: String,
    val referenceAvailable: Boolean,
    val renderAvailable: Boolean,
    val attempted: Boolean,
    val terminalFailure: Boolean,
    val score: Double?,
    val operationCount: Int?,
    val route: String,
    val firstDiagnostic: String?,
    val referenceStatus: String,
)

data class SkiaGmScoreAudit(val orphanRows: List<String>, val strict: Boolean)

internal fun loadSkiaGmScores(file: File, registeredNames: Set<String>, allowOrphans: Boolean = false): Map<String, Double> {
    require(file.exists()) { "Scores file not found: ${file.path}" }
    val scores = linkedMapOf<String, Double>()
    file.forEachLine { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachLine
        val separator = line.indexOf('=')
        require(separator > 0) { "Malformed score row: $line" }
        val name = line.substring(0, separator).trim()
        require(name.isNotEmpty()) { "Empty score name" }
        require(name !in scores) { "Duplicate score row: $name" }
        if (!allowOrphans) require(name in registeredNames) { "Orphan score row: $name" }
        val value = line.substring(separator + 1).trim().toDoubleOrNull()
        require(value != null && value.isFinite()) { "Invalid score for $name" }
        scores[name] = value
    }
    return scores
}

internal fun auditSkiaGmScores(file: File, registeredNames: Set<String>): SkiaGmScoreAudit {
    require(file.exists()) { "Scores file not found: ${file.path}" }
    val orphans = file.readLines().asSequence().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { it.substringBefore('=', "").trim().takeIf(String::isNotEmpty) }
        .filter { it !in registeredNames }.distinct().sorted().toList()
    return SkiaGmScoreAudit(orphans, strict = orphans.isEmpty())
}

fun buildSkiaGmInventory(
    gms: List<SkiaGm>,
    referenceDir: File,
    scoresFile: File,
    renderEvidence: Map<String, InventoryRenderEvidence> = emptyMap(),
    allowOrphanScores: Boolean = false,
): List<SkiaGmInventoryRow> {
    val names = gms.map { it.name }
    require(names.size == names.toSet().size) { "Duplicate registered GM names" }
    require(renderEvidence.keys.all { it in names }) { "Orphan render evidence rows" }
    val scores = loadSkiaGmScores(scoresFile, names.toSet(), allowOrphanScores)
    return gms.map { gm ->
        val evidence = renderEvidence[gm.name]
        SkiaGmInventoryRow(
            name = gm.name,
            family = gm.renderFamily.name,
            referenceName = gm.referenceName,
            referenceAvailable = referenceDir.resolve("${gm.referenceName}.png").isFile && !gm.referenceStatus.untrustable,
            renderAvailable = evidence?.renderSucceeded == true,
            attempted = evidence?.attempted == true,
            terminalFailure = evidence?.terminalFailure == true,
            score = scores[gm.name],
            operationCount = evidence?.operationCount,
            route = evidence?.route ?: "unobserved",
            firstDiagnostic = evidence?.diagnostics?.firstOrNull(),
            referenceStatus = when {
                !referenceDir.resolve("${gm.referenceName}.png").isFile -> "missing"
                gm.referenceStatus.untrustable -> "untrustable"
                else -> "trusted"
            },
        )
    }
}

fun renderSkiaGmInventoryJson(rows: List<SkiaGmInventoryRow>, scoreAudit: SkiaGmScoreAudit = SkiaGmScoreAudit(emptyList(), true)): String = buildString {
    val orphanRows = scoreAudit.orphanRows.joinToString(",") { "\"${inventoryJsonEscape(it)}\"" }
    appendLine("{")
    appendLine("  \"schemaVersion\": \"gpu-gm-inventory-v2\",")
    appendLine("  \"scoreAudit\": {\"strict\": ${scoreAudit.strict}, \"orphanCount\": ${scoreAudit.orphanRows.size}, \"orphanRows\": [$orphanRows]},")
    appendLine("  \"rows\": [")
    rows.forEachIndexed { index, row ->
        val comma = if (index + 1 == rows.size) "" else ","
        val score = row.score?.let { String.format(Locale.US, "%.10f", it) } ?: "null"
        val operationCount = row.operationCount?.toString() ?: "null"
        val firstDiagnostic = row.firstDiagnostic?.let { "\"${inventoryJsonEscape(it)}\"" } ?: "null"
        appendLine("    {")
        appendLine("      \"name\": \"${inventoryJsonEscape(row.name)}\",")
        appendLine("      \"family\": \"${row.family}\",")
        appendLine("      \"referenceName\": \"${inventoryJsonEscape(row.referenceName)}\",")
        appendLine("      \"referenceStatus\": \"${row.referenceStatus}\",")
        appendLine("      \"referenceAvailable\": ${row.referenceAvailable},")
        appendLine("      \"renderAvailable\": ${row.renderAvailable},")
        appendLine("      \"attempted\": ${row.attempted},")
        appendLine("      \"terminalFailure\": ${row.terminalFailure},")
        appendLine("      \"score\": $score,")
        appendLine("      \"operationCount\": $operationCount,")
        appendLine("      \"route\": \"${inventoryJsonEscape(row.route)}\",")
        appendLine("      \"firstDiagnostic\": $firstDiagnostic")
        appendLine("    }$comma")
    }
    appendLine("  ]")
    appendLine("}")
}.trimEnd()

fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: SkiaGmInventory <output.json>" }
    RuntimeEffectWgsl4kWiring.install()
    try {
        val entries = SkiaGmRegistry.entries()
        val rows = entries.mapNotNull { it.gm }
        val evidence = rows.associate { gm ->
            gm.name to when {
                gm.renderFamily == RenderFamily.TEXT -> InventoryRenderEvidence(false, false, false, 0, listOf("excluded:text-dependency-gated"), "excluded:text-dependency-gated")
                gm.renderCost == RenderCost.BLOCKING -> InventoryRenderEvidence(false, false, false, 0, listOf("excluded:blocking-by-policy"), "excluded:blocking-by-policy")
                else -> SkiaGmRenderer.inventoryEvidence(gm)
            }
        }
        val scoreFile = File("test-similarity-scores.properties")
        val scoreAudit = auditSkiaGmScores(scoreFile, rows.map { it.name }.toSet())
        val inventory = buildSkiaGmInventory(rows, File("src/test/resources/reference"), scoreFile, evidence, allowOrphanScores = true)
        val failedProviders = entries.filter { it.gm == null }
        val allRows = inventory + failedProviders.map { entry ->
            SkiaGmInventoryRow(entry.provider, "UNKNOWN", entry.provider, false, false, false, true, null, 0, "provider-unloadable", entry.diagnostic, "missing")
        }
        File(args[0]).apply { parentFile?.mkdirs(); writeText(renderSkiaGmInventoryJson(allRows, scoreAudit) + "\n") }
    } finally {
        GPUBackendRuntimeFactory.dispose()
    }
}

private fun inventoryJsonEscape(value: String): String = buildString {
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '\"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            in '\u0000'..'\u001f' -> append("\\u%04x".format(char.code))
            else -> append(char)
        }
    }
}
