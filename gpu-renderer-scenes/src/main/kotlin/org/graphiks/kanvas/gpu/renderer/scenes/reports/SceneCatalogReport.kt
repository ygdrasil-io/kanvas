package org.graphiks.kanvas.gpu.renderer.scenes.reports

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.SceneExpectation

class SceneCatalogReport<TCommand : Any>(private val scenes: List<GPURendererScene<TCommand>>) {
    fun toMarkdown(): String = buildString {
        appendLine("# GPU Renderer Scene Catalog")
        appendLine()
        appendLine("| Scene ID | Title | Tags | KGPU | R Stages | Expectation |")
        appendLine("|---|---|---|---|---|---|")
        scenes.forEach { scene ->
            appendLine(
                "| `${scene.sceneId.value}` | ${scene.title.escapeTable()} | " +
                    "${scene.tags.joinToString(",") { it.name }} | " +
                    "KGPU ${scene.roadmapLinks.map { it.milestone }.distinct().joinToString(",")} | " +
                    "${scene.roadmapLinks.mapNotNull { it.rStage?.name }.distinct().joinToString(",")} | " +
                    "`${scene.expectation.label()}` |",
            )
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
            appendLine("      \"rStages\": [${scene.roadmapLinks.mapNotNull { it.rStage?.name }.distinct().joinToString(",") { it.json() }}],")
            appendLine("      \"expectation\": ${scene.expectation.label().json()}")
            append("    }")
            appendLine(if (index == scenes.lastIndex) "" else ",")
        }
        appendLine("  ]")
        appendLine("}")
    }

    fun writeTo(outputDir: Path) {
        outputDir.createDirectories()
        outputDir.resolve("catalog.md").writeText(toMarkdown())
        outputDir.resolve("catalog.json").writeText(toJson())
    }
}

private fun SceneExpectation.label(): String =
    when (this) {
        SceneExpectation.ShouldRender -> "ShouldRender"
        is SceneExpectation.ProductRefusal -> "ProductRefusal:${reason.code}"
    }

private fun String.escapeTable(): String = replace("|", "\\|")

internal fun String.json(): String = buildString {
    append('"')
    this@json.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
    append('"')
}
