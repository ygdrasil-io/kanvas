package org.graphiks.kanvas.gpu.renderer.scenes

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.SceneExpectation
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.SceneTag

class LegacyCutoverInventoryTest {
    @Test
    fun `legacy cutover inventory is an exact catalog snapshot`() {
        val rows = Json.parseToJsonElement(
            repoRoot().resolve("gpu-renderer-scenes/legacy-cutover-inventory.json").readText(),
        ).let { element ->
            require(element is JsonArray) { "legacy inventory must be a JSON array" }
            element.map { row -> parseRow(row as? JsonObject ?: error("inventory row must be an object")) }
        }
        val expected = GPURendererSceneRegistry.scenes
            .sortedBy { it.sceneId.value }
            .map(::expectedRow)

        assertEquals(expected, rows)
        assertEquals(rows.size, rows.map { it.sceneId }.toSet().size, "inventory IDs must be unique")
        assertEquals(expected.map { it.sceneId }, rows.map { it.sceneId }, "inventory must be sorted")
        rows.filter { it.disposition == LegacyDisposition.Covered }.forEach { row ->
            assertNotNull(row.replacementSceneId, "covered scene ${row.sceneId} needs a replacement")
        }
    }

    private fun expectedRow(scene: GPURendererScene<*>): LegacyInventoryRow {
        val replacement = coveredByReplacement[scene.sceneId.value]
        return LegacyInventoryRow(
            sceneId = scene.sceneId.value,
            legacyExpectation = when (scene.expectation) {
                SceneExpectation.ShouldRender -> "ShouldRender"
                is SceneExpectation.ShouldRefuse -> "ShouldRefuse"
                is SceneExpectation.ProductRefusal -> "ProductRefusal"
            },
            disposition = disposition(scene),
            replacementSceneId = replacement,
        )
    }

    private fun disposition(scene: GPURendererScene<*>): LegacyDisposition = when {
        scene.sceneId.value in coveredByReplacement -> LegacyDisposition.Covered
        scene.expectation is SceneExpectation.ShouldRefuse -> LegacyDisposition.Unsupported
        scene.tags.any { it in setOf(SceneTag.Text, SceneTag.Image) } -> LegacyDisposition.DependencyGated
        listOf("board", "panel", "review", "deck", "bundle", "milestone")
            .any(scene.sceneId.value::contains) -> LegacyDisposition.Historical
        SceneTag.LegacyComparison in scene.tags -> LegacyDisposition.Duplicate
        else -> LegacyDisposition.FutureCandidate
    }

    private fun repoRoot(): File {
        var current = File(".").canonicalFile
        while (true) {
            if (current.resolve("settings.gradle.kts").isFile) return current
            current = current.parentFile ?: error("Unable to locate repository root")
        }
    }

    private data class LegacyInventoryRow(
        val sceneId: String,
        val legacyExpectation: String,
        val disposition: LegacyDisposition,
        val replacementSceneId: String?,
    )

    private enum class LegacyDisposition {
        Covered,
        Duplicate,
        Historical,
        DependencyGated,
        Unsupported,
        FutureCandidate,
    }

    private companion object {
        val coveredByReplacement = mapOf(
            "solid-card-stack" to "solid-card-stack",
            "custom-runtime-effect-unregistered-refusal" to "custom-runtime-effect-unregistered-refusal",
            "blur-radius-ladder" to "separable-blur-rect",
            "cache-frame-budget-strip" to "aggregate-memory-budget-refusal",
        )
    }

    private fun parseRow(row: JsonObject): LegacyInventoryRow {
        assertEquals(
            setOf("sceneId", "legacyExpectation", "disposition", "replacementSceneId"),
            row.keys,
            "inventory row fields",
        )
        return LegacyInventoryRow(
            sceneId = requireNotNull(row["sceneId"]).jsonPrimitive.content,
            legacyExpectation = requireNotNull(row["legacyExpectation"]).jsonPrimitive.content,
            disposition = LegacyDisposition.valueOf(requireNotNull(row["disposition"]).jsonPrimitive.content),
            replacementSceneId = row["replacementSceneId"]
                ?.takeUnless { it is JsonNull }
                ?.jsonPrimitive
                ?.content,
        )
    }
}
