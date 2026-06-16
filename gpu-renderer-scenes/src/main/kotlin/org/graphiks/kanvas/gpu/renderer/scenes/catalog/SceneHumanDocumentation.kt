package org.graphiks.kanvas.gpu.renderer.scenes.catalog

private fun requireHumanDocField(value: String, fieldName: String) {
    require(value.isNotBlank()) { "$fieldName must not be blank" }
}

data class LocalizedSceneText(
    val intention: String,
    val validates: String,
    val nonClaims: String,
    val evidence: String,
) {
    init {
        requireHumanDocField(intention, "LocalizedSceneText.intention")
        requireHumanDocField(validates, "LocalizedSceneText.validates")
        requireHumanDocField(nonClaims, "LocalizedSceneText.nonClaims")
        requireHumanDocField(evidence, "LocalizedSceneText.evidence")
    }
}

data class SceneHumanDocs(
    val sceneId: SceneId,
    val french: LocalizedSceneText,
)

enum class CandidateSceneStatus(val wireName: String) {
    Candidate("candidate"),
    FixtureReady("fixture-ready"),
    RunnerGap("runner-gap"),
    DependencyGated("dependency-gated"),
    ProductRefusalExpected("product-refusal-expected"),
}

data class CandidateSceneFrenchText(
    val intention: String,
    val validationTarget: String,
    val nonClaims: String,
    val rationale: String,
) {
    init {
        requireHumanDocField(intention, "CandidateSceneFrenchText.intention")
        requireHumanDocField(validationTarget, "CandidateSceneFrenchText.validationTarget")
        requireHumanDocField(nonClaims, "CandidateSceneFrenchText.nonClaims")
        requireHumanDocField(rationale, "CandidateSceneFrenchText.rationale")
    }
}

data class CandidateScene(
    val sceneId: SceneId,
    val title: String,
    val roadmapLinks: List<SceneRoadmapLink>,
    val tags: Set<SceneTag>,
    val status: CandidateSceneStatus,
    val french: CandidateSceneFrenchText,
) {
    init {
        require(title.isNotBlank()) { "candidate ${sceneId.value} title must not be blank" }
        require(roadmapLinks.isNotEmpty()) { "candidate ${sceneId.value} roadmapLinks must not be empty" }
        require(tags.isNotEmpty()) { "candidate ${sceneId.value} tags must not be empty" }
    }
}

internal fun validateSceneHumanDocumentation(
    scenes: List<GPURendererScene<*>>,
    docs: List<SceneHumanDocs>,
    candidateScenes: List<CandidateScene>,
): List<String> {
    val diagnostics = mutableListOf<String>()
    val executableIds = scenes.map { it.sceneId.value }.toSet()
    val docIds = docs.map { it.sceneId.value }
    val duplicateDocIds = docIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    duplicateDocIds.forEach { diagnostics += "duplicate human docs sceneId=$it" }
    docIds.filterNot { it in executableIds }.forEach {
        diagnostics += "human docs sceneId=$it does not match an executable scene"
    }
    executableIds.filterNot { it in docIds }.forEach {
        diagnostics += "missing human docs sceneId=$it"
    }

    val candidateIds = candidateScenes.map { it.sceneId.value }
    val duplicateCandidateIds = candidateIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    duplicateCandidateIds.forEach { diagnostics += "duplicate candidate sceneId=$it" }
    candidateIds.filter { it in executableIds }.forEach {
        diagnostics += "candidate sceneId=$it must not be executable"
    }
    return diagnostics
}

object GPURendererSceneHumanDocs {
    val docs: List<SceneHumanDocs> = emptyList()
    val candidateScenes: List<CandidateScene> = emptyList()

    fun validateAgainst(scenes: List<GPURendererScene<*>>): List<String> =
        validateSceneHumanDocumentation(
            scenes = scenes,
            docs = docs,
            candidateScenes = candidateScenes,
        )
}
