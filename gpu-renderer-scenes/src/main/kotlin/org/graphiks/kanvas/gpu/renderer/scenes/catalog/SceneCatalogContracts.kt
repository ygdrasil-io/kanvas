package org.graphiks.kanvas.gpu.renderer.scenes.catalog

private val sceneIdPattern = Regex("[a-z][a-z0-9]*(?:-[a-z0-9]+)+")
private val roadmapLikeSceneIdPattern = Regex("m[0-9]+(?:-[a-z])?(?:-[0-9]{3})?")
private val plainMilestonePattern = Regex("M(?:[0-9]|1[0-9])")
private val activeRuntimeMilestonePattern = Regex("M70-[ABC]")
private val ticketIdPattern = Regex("KGPU-(M[0-9]+)-[0-9]{3}")

private fun extractTicketMilestone(ticketId: String): String? =
    ticketIdPattern.matchEntire(ticketId)?.groupValues?.get(1)

private fun isValidMilestone(milestone: String): Boolean =
    milestone.matches(plainMilestonePattern) || milestone.matches(activeRuntimeMilestonePattern)

private fun List<*>.containsNullElement(): Boolean =
    any { it == null }

@JvmInline
value class SceneId(val value: String) {
    init {
        require(value.length in 3..64 && value.matches(sceneIdPattern)) {
            "SceneId must be a readable lowercase business identifier: $value"
        }
        require(!value.startsWith("kgpu-")) { "SceneId must not be a roadmap ticket id: $value" }
        require(!value.matches(roadmapLikeSceneIdPattern)) {
            "SceneId must not be a roadmap milestone or ticket label: $value"
        }
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
        require(isValidMilestone(milestone)) {
            "milestone must be M0..M10 or one of M70-A, M70-B, M70-C: $milestone"
        }
        ticketId?.let {
            val ticketMilestone = extractTicketMilestone(it)
            require(ticketMilestone != null) { "ticketId must be a KGPU ticket id: $it" }
            require(ticketMilestone == milestone) {
                "ticketId milestone $ticketMilestone must match milestone $milestone: $it"
            }
        }
    }

    companion object {
        fun milestone(milestone: String, rStage: RStage? = null): SceneRoadmapLink =
            SceneRoadmapLink(milestone = milestone, ticketId = null, rStage = rStage)

        fun ticket(ticketId: String, rStage: RStage? = null): SceneRoadmapLink {
            val milestone = requireNotNull(extractTicketMilestone(ticketId)) {
                "Invalid KGPU ticket id: $ticketId"
            }
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

data class GPURendererScene<TCommand : Any>(
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
        require(!commands.containsNullElement()) { "scene ${sceneId.value} commands must not contain null" }
    }
}

class SceneRegistry<TCommand : Any>(val scenes: List<GPURendererScene<TCommand>>) {
    fun requireScene(sceneId: String): GPURendererScene<TCommand> {
        val matches = scenes.filter { it.sceneId.value == sceneId }
        return when (matches.size) {
            0 -> error("Unknown GPU renderer scene: $sceneId")
            1 -> matches.single()
            else -> error("Duplicate GPU renderer scene: $sceneId")
        }
    }

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
        }
        return diagnostics
    }
}
