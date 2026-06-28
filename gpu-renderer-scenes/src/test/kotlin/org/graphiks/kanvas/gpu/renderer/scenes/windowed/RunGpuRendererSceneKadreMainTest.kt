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
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneHumanDocs
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
    fun `candidate scenes are not accepted by Kadre windowed runner`() {
        val candidate = GPURendererSceneHumanDocs.candidateScenes.single {
            it.sceneId.value == "simple-latin-glyph-atlas-strip"
        }
        val output = Files.createTempDirectory("gpu-renderer-scenes-candidate-windowed")
            .resolve("session.json")
        val failure = assertFailsWith<IllegalStateException> {
            runGpuRendererSceneKadre(arrayOf(candidate.sceneId.value, "0", output.toString()))
        }

        assertContains(failure.message.orEmpty(), "Unknown GPU renderer scene: simple-latin-glyph-atlas-strip")
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
    fun `shared windowed session output also writes stable scene session mirrors`() {
        val output = Files.createTempDirectory("gpu-renderer-scenes-windowed-main")
            .resolve("windowed")
            .resolve("session.json")
        val scenes = listOf(
            "runtime-effect-descriptor-gate-board" to 52,
            "savelayer-isolation-gate-board" to 53,
        )

        withKadreWindowedSceneRunnerLauncher(
            WindowedSceneRunnerLauncher { scene, frames, runnerOutput ->
                WindowedSceneSessionReport.presented(
                    scene = scene,
                    requestedFrames = frames,
                    surfaceFormat = "BGRA8Unorm",
                    adapterInfo = "test-adapter",
                ).writeTo(runnerOutput)
            },
        ) {
            scenes.forEach { (sceneId, frames) ->
                runGpuRendererSceneKadre(arrayOf(sceneId, frames.toString(), output.toString()))

                val sceneSessionJson = output.parent.resolve(sceneId).resolve("session.json").readText()
                assertContains(sceneSessionJson, "\"sceneId\": \"$sceneId\"")
                assertContains(sceneSessionJson, "\"status\": \"presented\"")
                assertContains(sceneSessionJson, "\"requestedFrames\": $frames")
                assertContains(sceneSessionJson, "\"presentedFrames\": $frames")
            }
        }

        val latestJson = output.readText()
        assertContains(latestJson, "\"sceneId\": \"savelayer-isolation-gate-board\"")
        assertContains(latestJson, "\"requestedFrames\": 53")

        val runtimeSessionJson = output.parent
            .resolve("runtime-effect-descriptor-gate-board")
            .resolve("session.json")
            .readText()
        assertContains(runtimeSessionJson, "\"sceneId\": \"runtime-effect-descriptor-gate-board\"")
        assertContains(runtimeSessionJson, "\"requestedFrames\": 52")
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
            "frame-gate-blocker-board" to 46,
            "pm-readiness-freeze-board" to 51,
            "legacy-route-comparison" to 21,
            "legacy-inventory-hygiene-board" to 33,
            "shadow-parity-migration-gate-board" to 47,
            "legacy-retirement-blocker-board" to 54,
            "path-badge-and-stroke" to 22,
            "path-coverage-review-board" to 34,
            "path-stencil-cover-gate-board" to 55,
            "rounded-panel-gradient" to 23,
            "rrect-gradient-route-board" to 39,
            "release-gate-progress-board" to 24,
            "texture-swatch-board" to 25,
            "asset-intake-thumbnail-grid" to 26,
            "photo-contact-sheet" to 57,
            "codec-provenance-gate-board" to 35,
            "sampler-boundary-gate-board" to 41,
            "savelayer-isolation-gate-board" to 43,
            "destination-read-strategy-gate-board" to 44,
            "clipped-avatar-grid" to 27,
            "filtered-photo-chip" to 28,
            "tinted-avatar-card" to 58,
            "layered-shadow-card" to 29,
            "notification-shadow-stack" to 56,
            "text-handoff-boundary-board" to 36,
            "a8-glyph-atlas-gate-board" to 50,
            "text-resource-binding-gate-board" to 49,
            "text-representation-gate-board" to 37,
            "runtime-effect-color-tile" to 30,
            "runtime-effect-descriptor-gate-board" to 42,
            "runtime-effect-refusal-gate-board" to 48,
            "sdr-color-boundary-board" to 38,
            "mesh-ribbon" to 31,
            "vertices-route-gate-board" to 45,
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
                } else if (sceneId == "notification-shadow-stack") {
                    assertContains(sessionJson, "saveLayerCommands=2")
                    assertContains(sessionJson, "saveLayerKinds=bounded-shadow-card, bounded-shadow-card")
                    assertContains(sessionJson, "saveLayerRoute=scene-fixture.bounded-shadow-card")
                    assertContains(sessionJson, "saveLayerMaterializedDraws=4")
                    assertContains(sessionJson, "filterRoutes=scene-fixture.bounded-drop-shadow")
                    assertContains(sessionJson, "generalSaveLayerSupport=false")
                    assertContains(sessionJson, "imageFilterDagSupport=false")
                } else if (sceneId == "mesh-ribbon") {
                    assertContains(sessionJson, "meshRibbonRoute=scene-fixture.bounded-ribbon-strip")
                    assertContains(sessionJson, "generalVerticesSupport=false")
                    assertContains(sessionJson, "vertexIndexBufferSupport=false")
                } else if (sceneId == "runtime-effect-refusal-gate-board") {
                    assertContains(sessionJson, "runtimeEffectRefusalMatrix=arbitrary-source:RefuseRequired:unsupported.runtime_effect.dynamic_sksl_forbidden,child-slot:RefuseRequired:unsupported.runtime_effect.child_count,unsupported-placement:RefuseRequired:unsupported.runtime_effect.route_unaccepted")
                    assertContains(sessionJson, "pmRuntimeEffectRefusalRow=gpu-renderer.runtime-effect-refusals")
                    assertContains(sessionJson, "pmRuntimeEffectRefusalClassification=RefuseRequired")
                    assertContains(sessionJson, "dynamicSourceCompilation=false")
                    assertContains(sessionJson, "childRuntimeEffectSupport=false")
                } else if (sceneId == "text-resource-binding-gate-board") {
                    assertContains(sessionJson, "textResourceBindingRefusalMatrix=upload-plan:RefuseRequired:unsupported.text.upload_plan_missing,binding-layout:RefuseRequired:unsupported.text.binding_layout_unavailable,stale-generation:RefuseRequired:unsupported.text.artifact_generation_stale,artifact-registration:RefuseRequired:unsupported.text.artifact_unregistered,upload-budget:RefuseRequired:unsupported.text.upload_budget_exceeded,cpu-rendered-texture:RefuseRequired:unsupported.text.cpu_rendered_texture_forbidden")
                    assertContains(sessionJson, "pmTextResourceBindingRow=gpu-renderer.text-resource-binding")
                    assertContains(sessionJson, "pmTextResourceBindingClassification=TargetPrepared")
                    assertContains(sessionJson, "textUploadPlanPromoted=false")
                    assertContains(sessionJson, "glyphAtlasRoutePromoted=false")
                } else if (sceneId == "a8-glyph-atlas-gate-board") {
                    assertContains(sessionJson, "a8GlyphAtlasRefusalMatrix=atlas-descriptor:TargetPrepared:unsupported.text.atlas_descriptor_unaccepted,atlas-page:RefuseRequired:unsupported.text.atlas_page_unavailable,atlas-entry:RefuseRequired:unsupported.text.atlas_entry_missing,atlas-generation:RefuseRequired:unsupported.text.atlas_generation_stale,a8-route:TargetPrepared:unsupported.text.a8_atlas_route_unavailable,instance-buffer:RefuseRequired:unsupported.text.instance_buffer_budget_exceeded")
                    assertContains(sessionJson, "pmA8GlyphAtlasRow=gpu-renderer.text.a8-atlas")
                    assertContains(sessionJson, "pmA8GlyphAtlasClassification=TargetPrepared")
                    assertContains(sessionJson, "a8GlyphAtlasRoutePromoted=false")
                    assertContains(sessionJson, "uploadBeforeSampleOrderingProven=false")
                    assertContains(sessionJson, "cpuRenderedTextTextureFallback=false")
                } else if (sceneId == "pm-readiness-freeze-board") {
                    assertContains(sessionJson, "pmReadinessRow=gpu-renderer.readiness")
                    assertContains(sessionJson, "pmReadinessClassification=PolicyGated")
                    assertContains(sessionJson, "readinessDelta=0.0")
                    assertContains(sessionJson, "releaseBlocking=false")
                    assertContains(sessionJson, "productRouteActivated=false")
                    assertContains(sessionJson, "performanceReadinessPromoted=false")
                    assertContains(sessionJson, "missingGate=none")
                    assertContains(sessionJson, "reportingOnlyGatesVisible=true")
                    assertContains(sessionJson, "pipelinePmBundleUpdated=true")
                    assertContains(sessionJson, "pmManifestKey=gpuRendererM9ReadinessPmEvidence")
                    assertContains(sessionJson, "nonClaims=no-product-activation,no-release-blocking-gate,no-readiness-delta,no-performance-readiness-from-correctness,no-dashboard-row-promotes-readiness,no-derived-cache-as-observed")
                } else if (sceneId == "legacy-retirement-blocker-board") {
                    assertContains(sessionJson, "legacyRetirementRow=gpu-renderer.legacy-retirement")
                    assertContains(sessionJson, "legacyRetirementClassification=PolicyGated")
                    assertContains(sessionJson, "legacyRouteRetired=false")
                    assertContains(sessionJson, "legacyDefaultActive=true")
                    assertContains(sessionJson, "productRouteActivated=false")
                    assertContains(sessionJson, "acceptedReplacementLinked=false")
                    assertContains(sessionJson, "activationDecisionLinked=false")
                    assertContains(sessionJson, "rollbackEvidenceLinked=false")
                    assertContains(sessionJson, "pmEvidenceLinked=false")
                    assertContains(sessionJson, "oldPathUsageEvidenceLinked=false")
                    assertContains(sessionJson, "archivedEvidencePreserved=true")
                    assertContains(sessionJson, "genericMigrationRetirement=false")
                    assertContains(sessionJson, "missingGate=KGPU-M10-002")
                } else if (sceneId == "path-stencil-cover-gate-board") {
                    assertContains(sessionJson, "clearCommands=1")
                    assertContains(sessionJson, "fillRectCommands=6")
                    assertContains(sessionJson, "fillRRectCommands=1")
                    assertContains(sessionJson, "linearGradientRectCommands=0")
                    assertContains(sessionJson, "clipCommands=1")
                    assertContains(sessionJson, "bitmapRectCommands=0")
                    assertContains(sessionJson, "pathStencilCoverRow=gpu-renderer.path.stencil-cover")
                    assertContains(sessionJson, "pathStencilCoverTicket=KGPU-M3-002")
                    assertContains(sessionJson, "pathStencilCoverTicketStatus=done")
                    assertContains(
                        sessionJson,
                        "pathStencilCoverClosure=contract-gate-complete-no-product-promotion",
                    )
                    assertContains(sessionJson, "pathStencilCoverClassification=TargetNative")
                    assertContains(sessionJson, "pathStencilCoverRouteKind=GPUNative")
                    assertContains(sessionJson, "pathStencilCoverAdapterRequired=true")
                    assertContains(
                        sessionJson,
                        "pathStencilCoverRefusalMatrix=" +
                            "depth-stencil-capability:RefuseRequired:coverage.stencil-cover-unavailable," +
                            "depth-stencil-evidence:RefuseRequired:unsupported.geometry.stencil_cover_unavailable," +
                            "sample-count-evidence:RefuseRequired:unsupported.geometry.stencil_cover_unavailable," +
                            "target-state:RefuseRequired:unsupported.geometry.stencil_cover_target," +
                            "clip-state:RefuseRequired:unsupported.clip.stencil_cover," +
                            "stencil-route-unavailable:RefuseRequired:unsupported.geometry.stencil_cover_unavailable," +
                            "producer-cover-ordering:RefuseRequired:unsupported.geometry.stencil_cover_ordering_illegal," +
                            "pass-resource-evidence:RefuseRequired:unsupported.geometry.stencil_cover_pass_resources_missing," +
                            "readback-evidence:RefuseRequired:unsupported.execution.readback_unavailable",
                    )
                    assertContains(sessionJson, "stencilCoverContractEvidenceLinked=true")
                    assertContains(sessionJson, "explicitSkippedLaneDiagnosticsLinked=true")
                    assertContains(sessionJson, "adapterBackedStencilEvidenceLinked=false")
                    assertContains(sessionJson, "passResourceReadbackArtifactsLinked=false")
                    assertContains(sessionJson, "producerBeforeCoverOrderingProven=false")
                    assertContains(sessionJson, "stencilCoverRoutePromoted=false")
                    assertContains(sessionJson, "productRouteActivated=false")
                    assertContains(sessionJson, "releaseBlocking=false")
                    assertContains(sessionJson, "cpuPreparedContinuationCountsAsSupport=false")
                    assertContains(sessionJson, "descriptorOnlyPlanningCountsAsSupport=false")
                    assertContains(sessionJson, "refusalOnlySelectorCountsAsSupport=false")
                    assertContains(sessionJson, "nonClaims=no-native-stencil-cover-support,no-adapter-backed-execution,no-product-activation,no-release-blocking-gate,no-cpu-prepared-continuation-as-support,no-refusal-only-selector-as-support")
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
            "\"reason\": \"rect-only windowed render supports only clear, fill-rect, fill-rrect, linear-gradient-rect, clip, fixture-backed bitmap-rect, fixture-backed save-layer, fixture-backed filter-node, fixture-backed runtime-effect, and fixture-backed mesh-ribbon command families: text-run\"",
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

    @Test
    fun `presented session report serializes raw frame timing samples with warmup and stable phases`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("frame-gate-blocker-board")
        val report = WindowedSceneSessionReport.presented(
            scene = scene,
            requestedFrames = 5,
            surfaceFormat = "BGRA8Unorm",
            adapterInfo = "AdapterInfo(device=Apple M2 Max, isFallbackAdapter=false)",
            frameTiming = WindowedFrameTimingReport.wallClockEncodePresent(
                warmupFrames = 2,
                samples = listOf(
                    12_100_000L,
                    11_900_000L,
                    10_400_000L,
                    10_300_000L,
                    10_500_000L,
                ),
            ),
        )

        val sessionJson = report.toJson()

        assertContains(sessionJson, "\"frameTiming\": {")
        assertContains(sessionJson, "\"metricName\": \"frame-time-ms\"")
        assertContains(sessionJson, "\"metricSource\": \"wall-clock-encode-present\"")
        assertContains(sessionJson, "\"rawSampleCount\": 5")
        assertContains(sessionJson, "\"warmupFrames\": 2")
        assertContains(sessionJson, "\"stableFrames\": 3")
        assertContains(
            sessionJson,
            "{\"frameIndex\": 1, \"phase\": \"warmup\", \"durationNanos\": 12100000, \"durationMs\": 12.1000}",
        )
        assertContains(
            sessionJson,
            "{\"frameIndex\": 3, \"phase\": \"stable\", \"durationNanos\": 10400000, \"durationMs\": 10.4000}",
        )
    }

    @Test
    fun `frame timing report rejects missing samples and invalid warmup split`() {
        assertFailsWith<IllegalArgumentException> {
            WindowedFrameTimingReport.wallClockEncodePresent(
                warmupFrames = 1,
                samples = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WindowedFrameTimingReport.wallClockEncodePresent(
                warmupFrames = 3,
                samples = listOf(1L, 2L),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WindowedFrameTimingReport.wallClockEncodePresent(
                warmupFrames = 1,
                samples = listOf(1L, 0L),
            )
        }
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
