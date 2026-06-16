package org.graphiks.kanvas.gpu.renderer.scenes.reports

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.CandidateScene
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.SceneExpectation
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.SceneHumanDocs

class SceneCatalogReport<TCommand : Any>(
    private val scenes: List<GPURendererScene<TCommand>>,
    private val humanDocs: List<SceneHumanDocs> = emptyList(),
    private val candidateScenes: List<CandidateScene> = emptyList(),
) {
    private val humanDocsBySceneId: Map<String, SceneHumanDocs> =
        humanDocs.associateBy { it.sceneId.value }

    fun toMarkdown(): String = buildString {
        appendLine("# GPU Renderer Scene Catalog")
        appendLine()
        appendLine("| Scene ID | Title | Tags | KGPU | Tickets | R Stages | Expectation |")
        appendLine("|---|---|---|---|---|---|---|")
        scenes.forEach { scene ->
            appendLine(
                "| `${scene.sceneId.value}` | ${scene.title.escapeTable()} | " +
                    "${scene.tags.joinToString(",") { it.name }} | " +
                    "KGPU ${scene.roadmapLinks.map { it.milestone }.distinct().joinToString(",")} | " +
                    scene.roadmapLinks.mapNotNull { it.ticketId }
                        .distinct()
                        .joinToString(",") { "`$it`" } + " | " +
                    "${scene.roadmapLinks.mapNotNull { it.rStage?.name }.distinct().joinToString(",")} | " +
                    "`${scene.expectation.label()}` |",
            )
        }
    }

    fun toFrenchMarkdown(): String = buildString {
        appendLine("# Catalogue des scenes GPU Renderer")
        appendLine()
        appendLine("## Scenes executables")
        scenes.forEach { scene ->
            val docs = humanDocsBySceneId[scene.sceneId.value]
            appendLine()
            appendLine("### ${scene.title.escapeMarkdownHeading()} (`${scene.sceneId.value}`)")
            appendLine(
                "${scene.roadmapLinks.map { it.milestone }.distinct().joinToString(",")} - " +
                    "${scene.tags.joinToString(", ") { it.name }} - `${scene.expectation.label()}`",
            )
            if (docs == null) {
                appendLine()
                appendLine("Documentation francaise manquante pour cette scene.")
            } else {
                appendLine()
                appendLine("Intention: ${docs.french.intention}")
                appendLine("Valide: ${docs.french.validates}")
                appendLine("Ne revendique pas: ${docs.french.nonClaims}")
                appendLine("Preuve: ${docs.french.evidence}")
            }
        }
        appendLine()
        appendLine("## Candidates amont")
        candidateScenes.forEach { candidate ->
            appendLine()
            appendLine("### ${candidate.title.escapeMarkdownHeading()} (`${candidate.sceneId.value}`)")
            appendLine(
                "${candidate.roadmapLinks.map { it.milestone }.distinct().joinToString(",")} - " +
                    "${candidate.tags.joinToString(", ") { it.name }}",
            )
            appendLine()
            appendLine("Statut: `${candidate.status.wireName}`")
            appendLine("Intention: ${candidate.french.intention}")
            appendLine("Validation visee: ${candidate.french.validationTarget}")
            appendLine("Ne revendique pas: ${candidate.french.nonClaims}")
            appendLine("Raison: ${candidate.french.rationale}")
        }
    }

    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"sceneCount\": ${scenes.size},")
        appendLine("  \"scenes\": [")
        scenes.forEachIndexed { index, scene ->
            appendLine("    {")
            appendLine("      \"sceneId\": ${scene.sceneId.value.json()},")
            appendLine("      \"title\": ${scene.title.json()},")
            appendLine("      \"description\": ${scene.description.json()},")
            appendLine("      \"tags\": [${scene.tags.joinToString(",") { it.name.json() }}],")
            appendLine("      \"kgpuMilestones\": [${scene.roadmapLinks.map { it.milestone }.distinct().joinToString(",") { it.json() }}],")
            appendLine("      \"ticketIds\": [${scene.roadmapLinks.mapNotNull { it.ticketId }.distinct().joinToString(",") { it.json() }}],")
            appendLine("      \"rStages\": [${scene.roadmapLinks.mapNotNull { it.rStage?.name }.distinct().joinToString(",") { it.json() }}],")
            append("      \"expectation\": ${scene.expectation.label().json()}")
            humanDocsBySceneId[scene.sceneId.value]?.let { docs ->
                appendLine(",")
                appendLine("      \"humanDocs\": {")
                appendLine("        \"fr\": {")
                appendLine("          \"intention\": ${docs.french.intention.json()},")
                appendLine("          \"validates\": ${docs.french.validates.json()},")
                appendLine("          \"nonClaims\": ${docs.french.nonClaims.json()},")
                appendLine("          \"evidence\": ${docs.french.evidence.json()}")
                appendLine("        }")
                append("      }")
            }
            appendLine()
            append("    }")
            appendLine(if (index == scenes.lastIndex) "" else ",")
        }
        appendLine("  ],")
        appendLine("  \"candidateScenes\": [")
        candidateScenes.forEachIndexed { index, candidate ->
            appendLine("    {")
            appendLine("      \"sceneId\": ${candidate.sceneId.value.json()},")
            appendLine("      \"title\": ${candidate.title.json()},")
            appendLine("      \"status\": ${candidate.status.wireName.json()},")
            appendLine("      \"tags\": [${candidate.tags.joinToString(",") { it.name.json() }}],")
            appendLine("      \"kgpuMilestones\": [${candidate.roadmapLinks.map { it.milestone }.distinct().joinToString(",") { it.json() }}],")
            appendLine("      \"ticketIds\": [${candidate.roadmapLinks.mapNotNull { it.ticketId }.distinct().joinToString(",") { it.json() }}],")
            appendLine("      \"humanDocs\": {")
            appendLine("        \"fr\": {")
            appendLine("          \"intention\": ${candidate.french.intention.json()},")
            appendLine("          \"validationTarget\": ${candidate.french.validationTarget.json()},")
            appendLine("          \"nonClaims\": ${candidate.french.nonClaims.json()},")
            appendLine("          \"rationale\": ${candidate.french.rationale.json()}")
            appendLine("        }")
            appendLine("      }")
            append("    }")
            appendLine(if (index == candidateScenes.lastIndex) "" else ",")
        }
        appendLine("  ]")
        appendLine("}")
    }

    fun writeTo(outputDir: Path) {
        outputDir.createDirectories()
        outputDir.resolve("catalog.md").writeText(toMarkdown())
        outputDir.resolve("catalog.fr.md").writeText(toFrenchMarkdown())
        outputDir.resolve("catalog.json").writeText(toJson())
    }
}

private fun SceneExpectation.label(): String =
    when (this) {
        SceneExpectation.ShouldRender -> "ShouldRender"
        is SceneExpectation.ProductRefusal -> "ProductRefusal:${reason.code}"
    }

private fun String.escapeTable(): String = replace("|", "\\|")

private fun String.escapeMarkdownHeading(): String =
    replace("\n", " ").replace("#", "\\#")

private const val HEX_DIGITS = "0123456789abcdef"

internal fun String.json(): String = buildString {
    append('"')
    this@json.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\n' -> append("\\n")
            '\u000C' -> append("\\f")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            in '\u0000'..'\u001F' -> {
                append("\\u00")
                append(HEX_DIGITS[ch.code ushr 4])
                append(HEX_DIGITS[ch.code and 0xF])
            }
            else -> append(ch)
        }
    }
    append('"')
}
