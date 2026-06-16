package org.graphiks.kanvas.gpu.renderer.scenes.windowed

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.SceneDimensions
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.SceneExpectation
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.SceneId
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.SceneTag
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBitmapSource
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneFilterKind
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

class RunGpuRendererSceneKadreMainTest {
    @Test
    fun `unknown scene fails before Kadre setup`() {
        val output = Files.createTempDirectory("gpu-renderer-scenes-windowed-main")
            .resolve("session.json")

        val failure = assertFailsWith<IllegalStateException> {
            runGpuRendererSceneKadre(arrayOf("missing-scene", "60", output.toString()))
        }

        assertContains(failure.message ?: "", "Unknown GPU renderer scene")
        assertFalse(output.exists())
    }

    @Test
    fun `solid card stack frames zero writes dry session report`() {
        val output = Files.createTempDirectory("gpu-renderer-scenes-windowed-main")
            .resolve("session.json")

        runGpuRendererSceneKadre(arrayOf("solid-card-stack", "0", output.toString()))

        val sessionJson = output.readText()
        assertContains(sessionJson, "\"schemaVersion\": 1")
        assertContains(sessionJson, "\"sceneId\": \"solid-card-stack\"")
        assertContains(sessionJson, "\"status\": \"dry-session\"")
        assertContains(sessionJson, "\"requestedFrames\": 0")
        assertContains(sessionJson, "\"presentedFrames\": 0")
        assertContains(sessionJson, "\"manualValidation\": true")
        assertContains(sessionJson, "\"productRefusal\": false")
    }

    @Test
    fun `catalogued rect rrect gradient clip and bitmap scenes launch Kadre runner instead of not yet rendered`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-windowed-main")
        val renderableScenes = listOf(
            "activation-candidate-boundary-board" to 40,
            "first-route-rollback-panel" to 16,
            "cache-pressure-deck" to 17,
            "blend-mode-strip" to 18,
            "translucent-card-overlap" to 19,
            "cache-source-ledger-board" to 20,
            "legacy-route-comparison" to 21,
            "legacy-inventory-hygiene-board" to 33,
            "path-badge-and-stroke" to 22,
            "path-coverage-review-board" to 34,
            "rounded-panel-gradient" to 23,
            "rrect-gradient-route-board" to 39,
            "release-gate-progress-board" to 24,
            "texture-swatch-board" to 25,
            "asset-intake-thumbnail-grid" to 26,
            "codec-provenance-gate-board" to 35,
            "sampler-boundary-gate-board" to 41,
            "clipped-avatar-grid" to 27,
            "filtered-photo-chip" to 28,
            "layered-shadow-card" to 29,
            "text-handoff-boundary-board" to 36,
            "text-representation-gate-board" to 37,
            "runtime-effect-color-tile" to 30,
            "sdr-color-boundary-board" to 38,
            "mesh-ribbon" to 31,
            "filter-dag-refusal-board" to 32,
        )
        val invocations = mutableListOf<RunnerInvocation>()

        withKadreWindowedSceneRunnerLauncher(
            WindowedSceneRunnerLauncher { scene, frames, output ->
                invocations += RunnerInvocation(scene.sceneId.value, frames, output)
                WindowedSceneSessionReport.presented(
                    scene = scene,
                    requestedFrames = frames,
                    surfaceFormat = "BGRA8Unorm",
                    adapterInfo = "test-adapter",
                ).writeTo(output)
            },
        ) {
            renderableScenes.forEach { (sceneId, frames) ->
                val output = root.resolve("$sceneId-session.json")

                runGpuRendererSceneKadre(arrayOf(sceneId, frames.toString(), output.toString()))

                val sessionJson = output.readText()
                assertContains(sessionJson, "\"sceneId\": \"$sceneId\"")
                assertContains(sessionJson, "\"status\": \"presented\"")
                assertContains(sessionJson, "\"requestedFrames\": $frames")
                assertContains(sessionJson, "\"presentedFrames\": $frames")
                if (sceneId == "layered-shadow-card") {
                    assertContains(sessionJson, "saveLayerRoute=scene-fixture.bounded-shadow-card")
                    assertContains(sessionJson, "filterRoutes=scene-fixture.bounded-drop-shadow")
                    assertContains(sessionJson, "generalSaveLayerSupport=false")
                    assertContains(sessionJson, "imageFilterDagSupport=false")
                } else if (sceneId == "mesh-ribbon") {
                    assertContains(sessionJson, "meshRibbonRoute=scene-fixture.bounded-ribbon-strip")
                    assertContains(sessionJson, "generalVerticesSupport=false")
                    assertContains(sessionJson, "vertexIndexBufferSupport=false")
                }
                assertFalse(sessionJson.contains("\"status\": \"not-yet-rendered\""), sceneId)
            }
        }

        assertEquals(
            renderableScenes.map { (sceneId, frames) ->
                RunnerInvocation(sceneId, frames, root.resolve("$sceneId-session.json"))
            },
            invocations,
        )
    }

    @Test
    fun `rect only WGSL uses transparent fallback when scene has no clear command`() {
        listOf("blend-mode-strip", "cache-pressure-deck", "legacy-route-comparison").forEach { sceneId ->
            val wgsl = WindowedRectOnlySceneShader.wgsl(GPURendererSceneRegistry.registry.requireScene(sceneId))

            assertContains(
                wgsl,
                Regex(
                    """var color = vec4<f32>\(\s*0\.000000,\s*0\.000000,\s*0\.000000,\s*0\.000000\s*\);""",
                ),
                message = sceneId,
            )
            assertFalse(wgsl.contains("0.035000"), sceneId)
        }
    }

    @Test
    fun `windowed WGSL materializes registered SimpleRT color tile without dynamic SkSL`() {
        val wgsl = WindowedRectOnlySceneShader.wgsl(
            GPURendererSceneRegistry.registry.requireScene("runtime-effect-color-tile"),
        )

        assertContains(wgsl, "runtime_simple_rt_color")
        assertContains(wgsl, "gColor")
        assertContains(wgsl, "pos.x * (1.0 / 255.0)")
        assertFalse(wgsl.contains("SkSL"))
    }

    @Test
    fun `windowed WGSL materializes bounded shadow card layer without general saveLayer claims`() {
        val wgsl = WindowedRectOnlySceneShader.wgsl(
            GPURendererSceneRegistry.registry.requireScene("layered-shadow-card"),
        )

        assertContains(wgsl, "shadow_card_layer_shadow")
        assertContains(wgsl, "shadow_card_layer_content")
        assertContains(wgsl, "drop_shadow_strength")
        assertFalse(wgsl.contains("saveLayer stack"))
    }

    @Test
    fun `windowed WGSL materializes bounded mesh ribbon without general vertices claims`() {
        val wgsl = WindowedRectOnlySceneShader.wgsl(
            GPURendererSceneRegistry.registry.requireScene("mesh-ribbon"),
        )

        assertContains(wgsl, "mesh_ribbon_coverage")
        assertContains(wgsl, "ribbon")
        assertFalse(wgsl.contains("vertex/index buffer"))
    }

    @Test
    fun `windowed WGSL refuses runtime effects outside the registered SimpleRT contract when called directly`() {
        val scene = windowedTestScene(
            sceneId = "windowed-runtime-effect-wrong-descriptor",
            commands = listOf(
                SceneCommand.RuntimeEffectTile(
                    label = "wrong-runtime-effect",
                    rect = SceneRect(0f, 0f, 16f, 16f),
                    stableId = "runtime.spiral_rt",
                    wgslImplementationId = "wgsl/runtime_spiral_rt",
                    uniformColor = SceneColor.blue(),
                ),
            ),
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            WindowedRectOnlySceneShader.wgsl(scene)
        }

        assertContains(
            failure.message ?: "",
            "supports only registered runtime.simple_rt RuntimeEffectTile payloads: wrong-runtime-effect",
        )
    }

    @Test
    fun `rect only unsupported command sequences write detailed not yet rendered reports before launcher handoff`() {
        val cases = listOf(
            UnsupportedRectOnlyCase(
                scene = windowedTestScene(
                    sceneId = "windowed-no-fill",
                    commands = listOf(SceneCommand.Clear(SceneColor(0f, 0f, 0f, 1f))),
                ),
                reason = "rect-only windowed render requires at least one FillRect, FillRRect, LinearGradientRect, BitmapRect, SaveLayer, RuntimeEffectTile, or MeshRibbon command",
            ),
            UnsupportedRectOnlyCase(
                scene = windowedTestScene(
                    sceneId = "windowed-bitmap-marker",
                    commands = listOf(SceneCommand.BitmapRect("marker")),
                ),
                reason = "rect-only windowed render requires fixture-backed BitmapRect payloads: marker",
            ),
            UnsupportedRectOnlyCase(
                scene = windowedTestScene(
                    sceneId = "windowed-filter-marker",
                    commands = listOf(testBitmapRect(), SceneCommand.FilterNode("marker")),
                ),
                reason = "rect-only windowed render requires fixture-backed FilterNode payloads: marker",
            ),
            UnsupportedRectOnlyCase(
                scene = windowedTestScene(
                    sceneId = "windowed-save-layer-marker",
                    commands = listOf(SceneCommand.SaveLayer("marker")),
                ),
                reason = "rect-only windowed render requires fixture-backed SaveLayer payloads: marker",
            ),
            UnsupportedRectOnlyCase(
                scene = windowedTestScene(
                    sceneId = "windowed-save-layer-out-of-bounds",
                    commands = listOf(
                        SceneCommand.SaveLayer(
                            label = "shadow-card-layer",
                            bounds = SceneRect(0f, 0f, 64f, 64f),
                            contentRect = SceneRect(8f, 8f, 48f, 48f),
                            radius = 4f,
                            contentColor = SceneColor.green(),
                            shadowColor = SceneColor(0f, 0f, 0f, 0.25f),
                        ),
                        SceneCommand.FilterNode(
                            label = "shadow-blur",
                            inputLabel = "shadow-card-layer",
                            kind = SceneFilterKind.DropShadow,
                        ),
                    ),
                ),
                reason = "rect-only windowed render requires SaveLayer materialized draws inside positive bounds: shadow-card-layer-shadow, shadow-card-layer-content",
            ),
            UnsupportedRectOnlyCase(
                scene = windowedTestScene(
                    sceneId = "windowed-runtime-effect-marker",
                    commands = listOf(SceneCommand.RuntimeEffectTile("marker")),
                ),
                reason = "rect-only windowed render requires fixture-backed RuntimeEffectTile payloads: marker",
            ),
            UnsupportedRectOnlyCase(
                scene = windowedTestScene(
                    sceneId = "windowed-runtime-effect-wrong-descriptor",
                    commands = listOf(
                        SceneCommand.RuntimeEffectTile(
                            label = "wrong-runtime-effect",
                            rect = SceneRect(0f, 0f, 16f, 16f),
                            stableId = "runtime.spiral_rt",
                            wgslImplementationId = "wgsl/runtime_spiral_rt",
                            uniformColor = SceneColor.blue(),
                        ),
                    ),
                ),
                reason = "rect-only windowed render supports only registered runtime.simple_rt RuntimeEffectTile payloads: wrong-runtime-effect",
            ),
            UnsupportedRectOnlyCase(
                scene = windowedTestScene(
                    sceneId = "windowed-mesh-ribbon-marker",
                    commands = listOf(SceneCommand.MeshRibbon("marker")),
                ),
                reason = "rect-only windowed render requires fixture-backed MeshRibbon payloads: marker",
            ),
            UnsupportedRectOnlyCase(
                scene = windowedTestScene(
                    sceneId = "windowed-mesh-ribbon-out-of-bounds",
                    commands = listOf(
                        SceneCommand.MeshRibbon(
                            label = "ribbon",
                            bounds = SceneRect(0f, 0f, 40f, 32f),
                            startColor = SceneColor.blue(),
                            endColor = SceneColor.amber(),
                        ),
                    ),
                ),
                reason = "rect-only windowed render requires MeshRibbon bounds inside positive target: ribbon",
            ),
            UnsupportedRectOnlyCase(
                scene = windowedTestScene(
                    sceneId = "windowed-late-clear",
                    commands = listOf(
                        testFillRect(),
                        SceneCommand.Clear(SceneColor(0f, 0f, 0f, 1f)),
                    ),
                ),
                reason = "rect-only windowed render supports zero or one initial Clear before drawable commands",
            ),
            UnsupportedRectOnlyCase(
                scene = windowedTestScene(
                    sceneId = "windowed-double-clear",
                    commands = listOf(
                        SceneCommand.Clear(SceneColor(0f, 0f, 0f, 1f)),
                        SceneCommand.Clear(SceneColor(1f, 1f, 1f, 1f)),
                        testFillRect(),
                    ),
                ),
                reason = "rect-only windowed render supports zero or one initial Clear before drawable commands",
            ),
        )

        cases.forEach { case ->
            assertUnsupportedSceneWritesNotYetRenderedWithoutLauncher(case.scene, case.reason)
        }
    }

    @Test
    fun `text run writes stable text route unavailable diagnostics without opening Kadre`() {
        val output = Files.createTempDirectory("gpu-renderer-scenes-windowed-main")
            .resolve("session.json")
        var launchCount = 0

        withKadreWindowedSceneRunnerLauncher(
            WindowedSceneRunnerLauncher { _, _, _ ->
                launchCount++
                error("receipt-text-run must not launch Kadre")
            },
        ) {
            runGpuRendererSceneKadre(arrayOf("receipt-text-run", "60", output.toString()))
        }

        val sessionJson = output.readText()
        assertContains(sessionJson, "\"sceneId\": \"receipt-text-run\"")
        assertContains(sessionJson, "\"status\": \"not-yet-rendered\"")
        assertContains(
            sessionJson,
            "\"reason\": \"unsupported.text.draw_run_route_unavailable\"",
        )
        assertContains(sessionJson, "commandFamily=text-run")
        assertContains(sessionJson, "fontFamily=Liberation Sans")
        assertContains(sessionJson, "glyphRoute=font.glyph.outline-path")
        assertContains(sessionJson, "lowerLevelTextRoutesAvailable=font.glyph.outline-path,webgpu.text.glyph-atlas.simple-latin")
        assertContains(sessionJson, "sceneRoutePromoted=false")
        assertContains(sessionJson, "nonClaims=no fake glyph substitute,no CPU-rendered text texture,no system font fallback,no broad shaping fallback emoji SDF LCD Kadre-windowed claim")
        assertContains(sessionJson, "\"requestedFrames\": 60")
        assertContains(sessionJson, "\"presentedFrames\": 0")
        assertContains(sessionJson, "\"manualValidation\": true")
        assertContains(sessionJson, "\"productRefusal\": false")
        assertEquals(0, launchCount)
    }

    @Test
    fun `frames must be a non negative integer`() {
        val output = Files.createTempDirectory("gpu-renderer-scenes-windowed-main")
            .resolve("session.json")

        val invalid = assertFailsWith<IllegalArgumentException> {
            runGpuRendererSceneKadre(arrayOf("solid-card-stack", "abc", output.toString()))
        }
        val negative = assertFailsWith<IllegalArgumentException> {
            runGpuRendererSceneKadre(arrayOf("solid-card-stack", "-1", output.toString()))
        }

        assertContains(invalid.message ?: "", "frames must be an Int")
        assertContains(negative.message ?: "", "frames must be >= 0")
        assertFalse(output.exists())
    }

    @Test
    fun `runner invocation failure overwrites stale session report`() {
        val output = Files.createTempDirectory("gpu-renderer-scenes-windowed-main")
            .resolve("session.json")
        WindowedSceneSessionReport.drySession(scene = solidCardStackScene(), requestedFrames = 0)
            .writeTo(output)
        val originalLauncher = kadreWindowedSceneRunnerLauncher
        kadreWindowedSceneRunnerLauncher = WindowedSceneRunnerLauncher { _, _, _ ->
            error("simulated runner failure")
        }

        try {
            runGpuRendererSceneKadre(arrayOf("solid-card-stack", "60", output.toString()))
        } finally {
            kadreWindowedSceneRunnerLauncher = originalLauncher
        }

        val sessionJson = output.readText()
        assertContains(sessionJson, "\"status\": \"blocked\"")
        assertContains(sessionJson, "\"reason\": \"kadre-windowed-runner-invocation-failed\"")
        assertContains(sessionJson, "IllegalStateException: simulated runner failure")
        assertFalse(sessionJson.contains("\"status\": \"dry-session\""))
    }

    @Test
    fun `runner loading failures overwrite stale session report and rethrow`() {
        val output = Files.createTempDirectory("gpu-renderer-scenes-windowed-main")
            .resolve("session.json")
        WindowedSceneSessionReport.drySession(scene = solidCardStackScene(), requestedFrames = 0)
            .writeTo(output)
        val originalLauncher = kadreWindowedSceneRunnerLauncher
        kadreWindowedSceneRunnerLauncher = WindowedSceneRunnerLauncher { _, _, _ ->
            throw ClassNotFoundException("missing test runner")
        }

        val failure = try {
            assertFailsWith<ClassNotFoundException> {
                runGpuRendererSceneKadre(arrayOf("solid-card-stack", "60", output.toString()))
            }
        } finally {
            kadreWindowedSceneRunnerLauncher = originalLauncher
        }

        val sessionJson = output.readText()
        assertContains(failure.message ?: "", "missing test runner")
        assertContains(sessionJson, "\"status\": \"blocked\"")
        assertContains(sessionJson, "\"reason\": \"kadre-windowed-runner-loading-failed\"")
        assertContains(sessionJson, "ClassNotFoundException: missing test runner")
        assertFalse(sessionJson.contains("\"status\": \"dry-session\""))
    }

    @Test
    fun `session report status invariants are enforced`() {
        val scene = solidCardStackScene()
        assertFailsWith<IllegalArgumentException> {
            WindowedSceneSessionReport(
                sceneId = scene.sceneId.value,
                runStatus = WindowedSceneSessionStatus.DrySession,
                reason = "frames-zero-dry-session",
                requestedFrames = 1,
                presentedFrames = 0,
                surface = WindowedSceneSurface(scene.dimensions.width, scene.dimensions.height, null),
                adapterInfo = null,
                error = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WindowedSceneSessionReport(
                sceneId = scene.sceneId.value,
                runStatus = WindowedSceneSessionStatus.NotYetRendered,
                reason = " ",
                requestedFrames = 60,
                presentedFrames = 0,
                surface = WindowedSceneSurface(scene.dimensions.width, scene.dimensions.height, null),
                adapterInfo = null,
                error = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WindowedSceneSessionReport(
                sceneId = scene.sceneId.value,
                runStatus = WindowedSceneSessionStatus.Blocked,
                reason = "kadre-windowed-runner-invocation-failed",
                requestedFrames = 60,
                presentedFrames = 0,
                surface = WindowedSceneSurface(scene.dimensions.width, scene.dimensions.height, null),
                adapterInfo = null,
                error = "",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WindowedSceneSessionReport(
                sceneId = scene.sceneId.value,
                runStatus = WindowedSceneSessionStatus.Presented,
                reason = "kadre-windowed-presented-frames",
                requestedFrames = 60,
                presentedFrames = 59,
                surface = WindowedSceneSurface(scene.dimensions.width, scene.dimensions.height, "BGRA8Unorm"),
                adapterInfo = "adapter",
                error = null,
            )
        }

        val presented = WindowedSceneSessionReport.presented(
            scene = scene,
            requestedFrames = 60,
            surfaceFormat = "BGRA8Unorm",
            adapterInfo = "adapter",
        )
        assertEquals("presented", presented.status)
        assertContains(presented.toJson(), "\"productRefusal\": false")
    }

    private fun solidCardStackScene() =
        GPURendererSceneRegistry.registry.requireScene("solid-card-stack")

    private fun assertUnsupportedSceneWritesNotYetRenderedWithoutLauncher(
        scene: GPURendererScene<SceneCommand>,
        expectedReason: String,
    ) {
        val output = Files.createTempDirectory("gpu-renderer-scenes-windowed-main")
            .resolve("${scene.sceneId.value}-session.json")
        val launcher = WindowedSceneRunnerLauncher { _, _, _ ->
            error("${scene.sceneId.value} must not launch Kadre")
        }

        val reason = scene.kadreWindowedRectOnlyUnsupportedReason()
        if (reason == null) {
            launcher.run(scene, 60, output)
        } else {
            WindowedSceneSessionReport.notYetRendered(scene, 60, reason).writeTo(output)
        }

        assertEquals(expectedReason, reason)
        val sessionJson = output.readText()
        assertContains(sessionJson, "\"sceneId\": \"${scene.sceneId.value}\"")
        assertContains(sessionJson, "\"status\": \"not-yet-rendered\"")
        assertContains(sessionJson, "\"reason\": \"$expectedReason\"")
        assertContains(sessionJson, "\"requestedFrames\": 60")
        assertContains(sessionJson, "\"presentedFrames\": 0")
        assertContains(sessionJson, "\"productRefusal\": false")
    }

    private fun windowedTestScene(
        sceneId: String,
        commands: List<SceneCommand>,
    ): GPURendererScene<SceneCommand> =
        GPURendererScene(
            sceneId = SceneId(sceneId),
            title = sceneId,
            description = "Windowed rect-only rule test scene.",
            dimensions = SceneDimensions(32, 32),
            tags = setOf(SceneTag.Rect),
            roadmapLinks = emptyList(),
            expectation = SceneExpectation.ShouldRender,
            commands = commands,
        )

    private fun testFillRect(): SceneCommand.FillRect =
        SceneCommand.FillRect(
            label = "test-fill",
            rect = SceneRect(0f, 0f, 8f, 8f),
            color = SceneColor.green(),
        )

    private fun testBitmapRect(): SceneCommand.BitmapRect =
        SceneCommand.BitmapRect(
            label = "photo",
            rect = SceneRect(0f, 0f, 16f, 16f),
            source = SceneBitmapSource(
                topLeft = SceneColor.red(),
                topRight = SceneColor.blue(),
                bottomLeft = SceneColor.green(),
                bottomRight = SceneColor.amber(),
            ),
        )

    private fun withKadreWindowedSceneRunnerLauncher(
        launcher: WindowedSceneRunnerLauncher,
        block: () -> Unit,
    ) {
        val originalLauncher = kadreWindowedSceneRunnerLauncher
        kadreWindowedSceneRunnerLauncher = launcher
        try {
            block()
        } finally {
            kadreWindowedSceneRunnerLauncher = originalLauncher
        }
    }

    private data class RunnerInvocation(
        val sceneId: String,
        val frames: Int,
        val output: Path,
    )

    private data class UnsupportedRectOnlyCase(
        val scene: GPURendererScene<SceneCommand>,
        val reason: String,
    )
}
