package org.graphiks.kanvas.gpu.renderer.scenes.catalog

@JvmInline
value class SceneId(val value: String) {
    init {
        require(value.matches(Regex("[a-z][a-z0-9-]{2,63}"))) {
            "SceneId must be a readable lowercase business identifier: $value"
        }
        require(!value.startsWith("kgpu-")) { "SceneId must not be a roadmap ticket id: $value" }
        require('-' in value) { "SceneId must contain a hyphenated business name: $value" }
    }
}

data class SceneDimensions(val width: Int, val height: Int) {
    init {
        require(width > 0) { "SceneDimensions.width must be positive" }
        require(height > 0) { "SceneDimensions.height must be positive" }
    }
}

enum class SceneTag {
    Rect,
    RRect,
    Gradient,
    Clip,
    Path,
    Stroke,
    Image,
    Layer,
    Filter,
    Text,
    RuntimeEffect,
    Blend,
    Vertices,
    Cache,
    LegacyComparison,
}

enum class RStage { R0, R1, R2, R3, R4, R5, R6 }

data class SceneRoadmapLink(
    val milestone: String,
    val ticketId: String?,
    val rStage: RStage?,
) {
    init {
        require(milestone.matches(Regex("M[0-9]+"))) { "milestone must look like M0..M10: $milestone" }
        ticketId?.let {
            require(it.matches(Regex("KGPU-M[0-9]+-[0-9]{3}"))) { "ticketId must be a KGPU ticket id: $it" }
        }
    }

    companion object {
        fun milestone(milestone: String, rStage: RStage? = null): SceneRoadmapLink =
            SceneRoadmapLink(milestone = milestone, ticketId = null, rStage = rStage)

        fun ticket(ticketId: String, rStage: RStage? = null): SceneRoadmapLink {
            val milestone = Regex("KGPU-(M[0-9]+)-[0-9]{3}")
                .matchEntire(ticketId)
                ?.groupValues
                ?.get(1)
                ?: error("Invalid KGPU ticket id: $ticketId")
            return SceneRoadmapLink(milestone = milestone, ticketId = ticketId, rStage = rStage)
        }
    }
}

enum class ProductRefusalReason(val code: String) {
    BudgetExceeded("budget-exceeded"),
    UnsupportedTargetFormat("unsupported-target-format"),
    ArbitrarySkSLSource("arbitrary-sksl-source"),
}

sealed interface SceneExpectation {
    data object ShouldRender : SceneExpectation
    data class ProductRefusal(val reason: ProductRefusalReason) : SceneExpectation
}

data class GPURendererScene<TCommand>(
    val sceneId: SceneId,
    val title: String,
    val description: String,
    val dimensions: SceneDimensions,
    val tags: Set<SceneTag>,
    val roadmapLinks: List<SceneRoadmapLink>,
    val expectation: SceneExpectation,
    val commands: List<TCommand>,
) {
    init {
        require(title.isNotBlank()) { "scene ${sceneId.value} title must not be blank" }
        require(description.isNotBlank()) { "scene ${sceneId.value} description must not be blank" }
        require(tags.isNotEmpty()) { "scene ${sceneId.value} tags must not be empty" }
        require(commands.isNotEmpty()) { "scene ${sceneId.value} commands must not be empty" }
    }
}

class SceneRegistry<TCommand>(val scenes: List<GPURendererScene<TCommand>>) {
    fun requireScene(sceneId: String): GPURendererScene<TCommand> =
        scenes.singleOrNull { it.sceneId.value == sceneId }
            ?: error("Unknown GPU renderer scene: $sceneId")

    fun validate(): List<String> {
        val diagnostics = mutableListOf<String>()
        val ids = LinkedHashSet<String>()
        scenes.forEach { scene ->
            if (!ids.add(scene.sceneId.value)) {
                diagnostics += "duplicate sceneId=${scene.sceneId.value}"
            }
            if (scene.roadmapLinks.isEmpty()) {
                diagnostics += "scene ${scene.sceneId.value} roadmapLinks must not be empty"
            }
            if (scene.expectation is SceneExpectation.ProductRefusal &&
                scene.expectation.reason !in ProductRefusalReason.entries
            ) {
                diagnostics += "scene ${scene.sceneId.value} has invalid product refusal"
            }
        }
        return diagnostics
    }
}
