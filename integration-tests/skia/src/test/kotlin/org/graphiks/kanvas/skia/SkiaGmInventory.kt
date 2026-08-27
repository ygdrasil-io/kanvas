package org.graphiks.kanvas.skia

import java.io.File
import java.util.Locale

/** Renderer observations supplied by the inventory replay; deliberately has no fallback semantics. */
data class InventoryRenderEvidence(
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
    val score: Double?,
    val operationCount: Int?,
    val route: String,
    val firstDiagnostic: String?,
)

internal fun loadSkiaGmScores(file: File, registeredNames: Set<String>): Map<String, Double> {
    if (!file.exists()) return emptyMap()
    val scores = linkedMapOf<String, Double>()
    file.forEachLine { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachLine
        val separator = line.indexOf('=')
        require(separator > 0) { "Malformed score row: $line" }
        val name = line.substring(0, separator).trim()
        require(name.isNotEmpty()) { "Empty score name" }
        require(name !in scores) { "Duplicate score row: $name" }
        require(name in registeredNames) { "Orphan score row: $name" }
        val value = line.substring(separator + 1).trim().toDoubleOrNull()
        require(value != null && value.isFinite()) { "Invalid score for $name" }
        scores[name] = value
    }
    return scores
}

fun buildSkiaGmInventory(
    gms: List<SkiaGm>,
    referenceDir: File,
    scoresFile: File,
    renderEvidence: Map<String, InventoryRenderEvidence> = emptyMap(),
): List<SkiaGmInventoryRow> {
    val names = gms.map { it.name }
    require(names.size == names.toSet().size) { "Duplicate registered GM names" }
    require(renderEvidence.keys.all { it in names }) { "Orphan render evidence rows" }
    val scores = loadSkiaGmScores(scoresFile, names.toSet())
    return gms.map { gm ->
        val evidence = renderEvidence[gm.name]
        SkiaGmInventoryRow(
            name = gm.name,
            family = gm.renderFamily.name,
            referenceName = gm.referenceName,
            referenceAvailable = referenceDir.resolve("${gm.referenceName}.png").isFile,
            renderAvailable = evidence != null,
            score = scores[gm.name],
            operationCount = evidence?.operationCount,
            route = evidence?.route ?: "unobserved",
            firstDiagnostic = evidence?.diagnostics?.firstOrNull(),
        )
    }
}

fun renderSkiaGmInventoryJson(rows: List<SkiaGmInventoryRow>): String = buildString {
    appendLine("{")
    appendLine("  \"schemaVersion\": \"gpu-gm-inventory-v2\",")
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
        appendLine("      \"referenceAvailable\": ${row.referenceAvailable},")
        appendLine("      \"renderAvailable\": ${row.renderAvailable},")
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
    val rows = SkiaGmRegistry.all()
    val evidence = rows
        .filter { it.renderFamily != RenderFamily.TEXT && it.renderCost != RenderCost.BLOCKING }
        .associate { gm -> gm.name to SkiaGmRenderer.inventoryEvidence(gm) }
    val inventory = buildSkiaGmInventory(
        rows,
        File("src/test/resources/reference"),
        File("test-similarity-scores.properties"),
        evidence,
    )
    File(args[0]).apply { parentFile?.mkdirs(); writeText(renderSkiaGmInventoryJson(inventory) + "\n") }
}

private fun inventoryJsonEscape(value: String): String = buildString {
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '\"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}
