package org.skia.kadre.runtime

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReplaySceneRegistryTest {
    @Test
    fun m73ReplayPackKeepsPmJsonContract() {
        val packJson = replayPackJson("demo", "  ")

        assertContains(packJson, "\"id\": \"m73-kadre-replay-pack-v1\"")
        assertContains(packJson, "\"sceneCount\": 5")
        assertContains(packJson, "\"renderableSceneCount\": 4")
        assertContains(packJson, "\"unsupportedSceneCount\": 1")
        assertContains(packJson, "\"sceneIds\"")
        assertContains(packJson, "\"unsupportedSceneIds\"")
        assertContains(packJson, "m73-nested-rrect-clip-refusal-v1")

        val defaultScene = requireNotNull(M73_REPLAY_SCENES_BY_ID[M73_DEFAULT_SCENE_CONTRACT_ID])
        val sceneJson = defaultScene.toJson("  ")
        assertContains(sceneJson, "\"source\": \"kanvas-replay-data\"")
        assertContains(sceneJson, "\"sourceSceneId\": \"linear-gradient-rect\"")
        assertContains(sceneJson, "\"commandCounters\"")
        assertContains(sceneJson, "\"total\": 2")
        assertContains(sceneJson, "\"supported\": 2")
        assertContains(sceneJson, "\"unsupported\": 0")
        assertContains(sceneJson, "\"backgroundClear\": 1")
        assertContains(sceneJson, "\"fillRect\": 1")
        assertContains(sceneJson, "\"srcOver\": 1")
        assertContains(sceneJson, "\"partialAlpha\": 0")
        assertContains(sceneJson, "\"blendMode\": \"SrcOver\"")
        assertContains(sceneJson, "\"alpha\": 1.0000")
        assertContains(sceneJson, "\"unsupportedCommands\": []")
    }

    @Test
    fun unsupportedReplaySceneKeepsStableReason() {
        val scene = requireNotNull(M73_REPLAY_SCENES_BY_ID["m73-nested-rrect-clip-refusal-v1"])

        assertEquals("expected-unsupported", scene.status)
        assertEquals(false, scene.renderedByKadre)
        assertEquals(2, scene.totalCommandCount)
        assertEquals(1, scene.supportedCommandCount)
        assertEquals(1, scene.unsupportedCommandCount)
        assertEquals(listOf("nested-rrect-difference-clip"), scene.unsupportedCommands)
        assertContains(scene.toJson("  "), "\"unsupportedCommands\": [\"nested-rrect-difference-clip\"]")
    }

    @Test
    fun smokeModeDoesNotExposeReplayPack() {
        assertEquals("null", replayPackJson("smoke", "  "))
    }

    @Test
    fun replaySceneRequiresExactlyOneClearCommand() {
        val error = assertFailsWith<IllegalArgumentException> {
            ReplaySceneEvidence(
                id = "invalid-no-clear",
                title = "invalid",
                source = "test",
                sourceSceneId = "test",
                version = 1,
                commandSource = "test",
                commands = listOf(
                    ReplayCommand.FillRect("rect", 0.0, 0.0, 1.0, 1.0, ReplayColor(1.0, 0.0, 0.0)),
                ),
                dashboardRow = "test",
                cpuRoute = "test",
                gpuRoute = "test",
                pipelineKey = "test",
            )
        }

        assertContains(error.message.orEmpty(), "must contain exactly one ReplayCommand.Clear")
    }

    @Test
    fun m75ReplayPackEvidenceKeepsPerSceneSchema() {
        val evidence = buildReplayPackEvidence()
        val json = evidence.toJson()

        assertEquals(5, evidence.sceneCount)
        assertEquals(4, evidence.renderableSceneCount)
        assertEquals(1, evidence.expectedUnsupportedSceneCount)
        assertEquals(0, evidence.failedSceneCount)
        assertContains(json, "\"packId\": \"m75-kadre-replay-pack-evidence-v1\"")
        assertContains(json, "\"linearIssues\": [\"FOR-91\", \"FOR-110\", \"FOR-111\", \"FOR-112\", \"FOR-113\"]")
        assertContains(json, "\"cpuReference\"")
        assertContains(json, "\"nativeEvidence\"")
        assertContains(json, "\"readbackImage\": \"reports/wgsl-pipeline/m70-kadre-native/native-demo-readback.png\"")
        assertContains(json, "\"reason\": \"m73.kadre-replay-scene-expected-unsupported\"")
        assertContains(json, "\"status\": \"not-generated\"")
    }

    @Test
    fun m76GeneratedMetadataReplayMapsSelectedScenesAndRefusesUnsupportedMetadata() {
        val evidence = buildGeneratedMetadataReplayEvidence(m76ManifestFixture())
        val json = evidence.toJson()

        assertEquals(6, evidence.sceneCount)
        assertEquals(4, evidence.mappedSceneCount)
        assertEquals(2, evidence.refusedMetadataCount)
        assertEquals(0, evidence.failedSceneCount)
        assertContains(json, "\"packId\": \"m76-generated-metadata-replay-v1\"")
        assertContains(json, "\"sourceManifest\": \"reports/wgsl-pipeline/scenes/generated/results.json\"")
        assertContains(json, "\"replaySceneId\": \"m76-solid-rect-metadata-replay-v1\"")
        assertContains(json, "\"replaySceneId\": \"m76-linear-gradient-rect-metadata-replay-v1\"")
        assertContains(json, "\"reason\": \"m76.metadata.source-status-not-pass\"")
        assertContains(json, "\"reason\": \"m76.metadata.unsupported-route-family\"")
        assertContains(json, "\"sourceSceneId\": \"path-aa-convexpaths-edge-budget\"")
        assertContains(json, "\"sourceSceneId\": \"runtime-effect-simple\"")
    }

    @Test
    fun m77BlendAlphaReplayCoversSrcOverAlphaAndUnsupportedBlend() {
        val evidence = buildBlendAlphaReplayEvidence()
        val json = evidence.toJson()

        assertEquals(3, evidence.sceneCount)
        assertEquals(2, evidence.renderableSceneCount)
        assertEquals(1, evidence.expectedUnsupportedSceneCount)
        assertEquals(0, evidence.failedSceneCount)
        assertEquals(2, evidence.partialAlphaSceneCount)
        assertEquals(4, evidence.srcOverCommandCount)
        assertEquals(3, evidence.partialAlphaCommandCount)
        assertContains(json, "\"packId\": \"m77-blend-alpha-replay-v1\"")
        assertContains(json, "\"linearIssues\": [\"FOR-93\", \"FOR-119\", \"FOR-120\", \"FOR-121\", \"FOR-122\", \"FOR-123\"]")
        assertContains(json, "\"id\": \"m77-alpha-srcover-stack-replay-v1\"")
        assertContains(json, "\"id\": \"m77-gradient-alpha-srcover-replay-v1\"")
        assertContains(json, "\"id\": \"m77-multiply-blend-refusal-v1\"")
        assertContains(json, "\"unsupportedBlendReason\": \"m77.unsupported-blend-mode.kMultiply\"")
        assertContains(json, "\"blendMode\": \"SrcOver\"")
        assertContains(json, "\"alpha\": 0.3500")
        assertContains(json, "\"endAlpha\": 0.7000")
        assertContains(json, "\"oracle\": \"src-over-partial-alpha-sampled-reference\"")
    }

    @Test
    fun m77SceneCommandsExposeBlendAlphaSemanticsAndCpuOracleFacts() {
        val alphaStack = M77_BLEND_ALPHA_REPLAY_SCENES.single { it.id == "m77-alpha-srcover-stack-replay-v1" }
        val unsupported = M77_BLEND_ALPHA_REPLAY_SCENES.single { it.id == "m77-multiply-blend-refusal-v1" }

        assertEquals("renderable", alphaStack.status)
        assertEquals(3, alphaStack.totalCommandCount)
        assertEquals(2, alphaStack.fillRectCount)
        assertEquals(2, alphaStack.srcOverCommandCount)
        assertEquals(1, alphaStack.partialAlphaCommandCount)
        assertContains(alphaStack.toJson("  "), "\"blendMode\": \"SrcOver\"")
        assertContains(alphaStack.toJson("  "), "\"alpha\": 0.4500")

        val cpuReference = renderCpuReference(640, 420, alphaStack)
        assertEquals(true, cpuReference.first != 0L)
        assertEquals(true, cpuReference.second > 0)

        assertEquals("expected-unsupported", unsupported.status)
        assertEquals(1, unsupported.unsupportedCommandCount)
        assertEquals(listOf("m77.unsupported-blend-mode.kMultiply"), unsupported.unsupportedCommands)
        assertContains(unsupported.toJson("  "), "\"unsupportedCommands\": [\"m77.unsupported-blend-mode.kMultiply\"]")
    }

    private fun m76ManifestFixture(): String = """
        {
          "scenes": [
            ${m76SceneFixture("solid-rect", "Solid filled rect", "pass", "reports/wgsl-pipeline/2026-05-30-m46-solid-rect-generated-evidence.md", "artifacts/solid-rect", listOf("source.generated", "feature.shape.solid", "feature.coverage.analytic-rect", "route.cpu.descriptor", "route.gpu.webgpu"))},
            ${m76SceneFixture("linear-gradient-rect", "Linear gradient rect generated WGSL route", "pass", "reports/wgsl-pipeline/2026-05-28-m39-gradient-srcover-dashboard-scenes.md", "artifacts/linear-gradient-rect", listOf("source.generated", "feature.gradient.linear", "feature.coverage.analytic-rect", "route.cpu.shader", "route.gpu.webgpu"))},
            ${m76SceneFixture("bitmap-rect-nearest", "Bitmap rect nearest sampling", "pass", "reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect-skbug4734-resolution.md", "artifacts/bitmap-rect-nearest", listOf("source.generated", "feature.image.bitmap", "feature.sampling.nearest", "route.cpu.image-rect", "route.gpu.webgpu"))},
            ${m76SceneFixture("gradient-color-filter-linear-kplus", "Linear gradient color-filter kPlus route", "pass", "reports/wgsl-pipeline/2026-05-31-m48-paint-blend-transform-generated-evidence.md", "artifacts/gradient-color-filter-linear-kplus", listOf("source.generated", "feature.gradient.linear", "feature.color-filter", "feature.blend.plus", "route.gpu.webgpu"))},
            ${m76SceneFixture("path-aa-convexpaths-edge-budget", "Path AA ConvexPaths edge-budget refusal", "expected-unsupported", "reports/wgsl-pipeline/2026-05-31-m48-expected-unsupported-breadth-evidence.md", "artifacts/path-aa-convexpaths-edge-budget", listOf("source.generated", "feature.path-aa", "feature.coverage.aa", "route.cpu.oracle", "route.gpu.expected-unsupported"))},
            ${m76SceneFixture("runtime-effect-simple", "Registered SimpleRT runtime-effect route", "pass", "reports/wgsl-pipeline/2026-05-31-m47-runtime-effect-simple-generated-evidence.md", "artifacts/runtime-effect-simple", listOf("source.generated", "feature.runtime-effect", "feature.coverage.analytic-rect", "route.cpu.descriptor", "route.gpu.webgpu"))}
          ]
        }
    """.trimIndent()

    private fun m76SceneFixture(
        id: String,
        title: String,
        status: String,
        sourceReport: String,
        artifactRoot: String,
        tags: List<String>,
    ): String = """
        {
          "id": "$id",
          "title": "$title",
          "status": "$status",
          "tags": [${tags.joinToString(", ") { "\"$it\"" }}],
          "generation": {
            "sourceReport": "$sourceReport",
            "artifactRoot": "$artifactRoot"
          }
        }
    """.trimIndent()
}
