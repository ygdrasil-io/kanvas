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
}
