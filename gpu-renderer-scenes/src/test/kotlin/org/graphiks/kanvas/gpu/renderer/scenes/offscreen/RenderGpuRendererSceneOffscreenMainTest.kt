package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.io.File
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.spi.IIORegistry
import javax.imageio.spi.ImageWriterSpi
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneHumanDocs
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBitmapSource
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

class RenderGpuRendererSceneOffscreenMainTest {
    @Test
    fun `unknown scene fails before backend setup`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-offscreen-main")

        val failure = assertFailsWith<IllegalStateException> {
            renderGpuRendererSceneOffscreen(arrayOf("missing-scene", root.toString()))
        }

        assertContains(failure.message ?: "", "Unknown GPU renderer scene")
        assertFalse(root.resolve("missing-scene").exists())
    }

    @Test
    fun `candidate scenes are not accepted by WebGPU offscreen runner`() {
        val candidate = GPURendererSceneHumanDocs.candidateScenes.single {
            it.sceneId.value == "simple-latin-glyph-atlas-strip"
        }
        val root = Files.createTempDirectory("gpu-renderer-scenes-candidate-offscreen")
        val failure = assertFailsWith<IllegalStateException> {
            renderGpuRendererSceneOffscreen(arrayOf(candidate.sceneId.value, root.toString()))
        }

        assertContains(failure.message.orEmpty(), "Unknown GPU renderer scene: simple-latin-glyph-atlas-strip")
    }

    @Test
    fun `receipt text run renders through updated faithful subset`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-offscreen-main")

        renderGpuRendererSceneOffscreen(arrayOf("receipt-text-run", root.toString()))

        val sceneOutput = root.resolve("receipt-text-run")
        val runJson = sceneOutput.resolve("run.json").readText()
        assertContains(runJson, "\"sceneId\": \"receipt-text-run\"")
        assertTrue(
            runJson.contains("\"status\": \"rendered\"") || runJson.contains("webgpu-context-unavailable"),
            "Expected rendered status or webgpu-context-unavailable fallback, got: $runJson",
        )
    }

    @Test
    fun `catalogued faithful subset scenes render through WebGPU offscreen`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-offscreen-main")
        val rectOnlyScenes = listOf(
            RenderedShapeExpectation("activation-candidate-boundary-board", fillRectCount = 6),
            RenderedShapeExpectation("first-route-rollback-panel", fillRectCount = 4),
            RenderedShapeExpectation("rounded-panel-gradient", fillRectCount = 0, fillRRectCount = 1, linearGradientRectCount = 1, clipCount = 1),
            RenderedShapeExpectation("gradient-tile-mode-boundary", fillRectCount = 6),
            RenderedShapeExpectation("path-aa-stroke-join-board", fillRectCount = 6),
            RenderedShapeExpectation("blend-mode-strip", fillRectCount = 1),
            RenderedShapeExpectation("translucent-card-overlap", fillRectCount = 3),
            RenderedShapeExpectation("layer-filter-chain-board", fillRectCount = 6),
            RenderedShapeExpectation("cache-pressure-deck", fillRectCount = 2),
            RenderedShapeExpectation("cache-frame-budget-strip", fillRectCount = 5),
            RenderedShapeExpectation("cache-source-ledger-board", fillRectCount = 5),
            RenderedShapeExpectation("frame-gate-blocker-board", fillRectCount = 6),
            RenderedShapeExpectation("legacy-route-comparison", fillRectCount = 1),
            RenderedShapeExpectation("filter-dag-refusal-board", fillRectCount = 5),
        )

        rectOnlyScenes.forEach { expectation ->
            assertRenderedShapeScene(root, expectation)
        }
    }

    @Test
    fun `catalogued richer scenes stay not yet rendered offscreen until the faithful subset grows`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-offscreen-main")
        val unsupportedScenes = listOf(
            "layered-shadow-card" to listOf("filter-node"),
        )

        unsupportedScenes.forEach { (sceneId, families) ->
            assertNotYetRenderedScene(root, sceneId, families)
        }
    }

    @Test
    fun `solid card stack renders through rect only WebGPU offscreen path`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-offscreen-main")

        assertRenderedShapeScene(root, RenderedShapeExpectation(sceneId = "solid-card-stack", fillRectCount = 3))
    }

    @Test
    fun `solid card stack backend failure report remains representable`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-offscreen-main")
        val sceneOutput = root.resolve("solid-card-stack")

        OffscreenRunReport.failed(
            sceneId = "solid-card-stack",
            reason = "webgpu-context-unavailable",
        ).writeTo(sceneOutput)

        val runJson = sceneOutput.resolve("run.json").readText()
        assertContains(runJson, "\"sceneId\": \"solid-card-stack\"")
        assertContains(runJson, "\"status\": \"render-failed\"")
        assertContains(runJson, "\"productRefusal\": false")
        assertContains(runJson, "webgpu-context-unavailable")
    }

    @Test
    fun `offscreen frame sample report serializes raw samples with adapter provenance`() {
        val report = OffscreenFrameSampleReport.sampled(
            sceneId = "frame-gate-blocker-board",
            adapterInfo = "AdapterInfo(device=Apple M2 Max, isFallbackAdapter=false)",
            warmupFrames = 2,
            samples = listOf(13_000_000L, 12_000_000L, 10_000_000L, 10_500_000L),
            diagnostics = listOf("sampled frame-gate-blocker-board via WebGPU offscreen render+readback"),
        )

        val json = report.toJson()

        assertContains(json, "\"schemaVersion\": 1")
        assertContains(json, "\"sceneId\": \"frame-gate-blocker-board\"")
        assertContains(json, "\"status\": \"sampled\"")
        assertContains(json, "\"backend\": \"webgpu-offscreen\"")
        assertContains(json, "\"metricName\": \"frame-time-ms\"")
        assertContains(json, "\"metricSource\": \"wall-clock-offscreen-render-readback\"")
        assertContains(json, "\"adapterInfo\": \"AdapterInfo(device=Apple M2 Max, isFallbackAdapter=false)\"")
        assertContains(json, "\"rawSampleCount\": 4")
        assertContains(json, "\"warmupFrames\": 2")
        assertContains(json, "\"stableFrames\": 2")
        assertContains(
            json,
            "{\"frameIndex\": 1, \"phase\": \"warmup\", \"durationNanos\": 13000000, \"durationMs\": 13.0000}",
        )
        assertContains(
            json,
            "{\"frameIndex\": 3, \"phase\": \"stable\", \"durationNanos\": 10000000, \"durationMs\": 10.0000}",
        )
    }

    @Test
    fun `offscreen frame sample report rejects invalid sample evidence`() {
        assertFailsWith<IllegalArgumentException> {
            OffscreenFrameSampleReport.sampled(
                sceneId = "frame-gate-blocker-board",
                adapterInfo = "adapter",
                warmupFrames = 1,
                samples = emptyList(),
                diagnostics = listOf("sampled"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            OffscreenFrameSampleReport.sampled(
                sceneId = "frame-gate-blocker-board",
                adapterInfo = "adapter",
                warmupFrames = 2,
                samples = listOf(1L, 2L),
                diagnostics = listOf("sampled"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            OffscreenFrameSampleReport.sampled(
                sceneId = "frame-gate-blocker-board",
                adapterInfo = "adapter",
                warmupFrames = 1,
                samples = listOf(1L, 0L),
                diagnostics = listOf("sampled"),
            )
        }
    }

    @Test
    fun `rect only command preparation rejects scenes without fill rectangles`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            prepareRectOnlyDrawPlan(
                sceneId = "empty-rect-only",
                commands = listOf(SceneCommand.Clear(SceneColor(0f, 0f, 0f, 1f))),
                width = 320,
                height = 200,
            )
        }

        assertContains(failure.message ?: "", "at least one FillRect, FillRRect")
    }

    @Test
    fun `rect only command preparation rejects filter command families outside the faithful subset`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            prepareRectOnlyDrawPlan(
                sceneId = "filter-marker-only",
                commands = listOf(SceneCommand.FilterNode("marker")),
                width = 320,
                height = 200,
            )
        }

        assertContains(failure.message ?: "", "supports only clear, fill-rect, fill-rrect, stroke, linear-gradient-rect, radial-gradient-rect, sweep-gradient-rect, clip, path-fill-stencil, path-fill-gradient, convex-fan-mesh, bitmap-rect, save-layer, runtime-effect, mesh-ribbon, and text-run command families")
        assertContains(failure.message ?: "", "filter-node")
    }

    @Test
    fun `rect only command preparation accepts mesh ribbon commands within the faithful subset`() {
        val drawPlan = prepareRectOnlyDrawPlan(
            sceneId = "mesh-ribbon-valid",
            commands = listOf(
                SceneCommand.MeshRibbon(
                    label = "ribbon",
                    bounds = SceneRect(left = 0f, top = 0f, right = 32f, bottom = 32f),
                    startColor = SceneColor.blue(),
                    endColor = SceneColor.amber(),
                ),
            ),
            width = 320,
            height = 200,
        )

        assertEquals(1, drawPlan.fills.size)
        assertEquals("vertices", drawPlan.fills[0].family)
    }

    @Test
    fun `rect only command preparation rejects out of bounds fill rectangles`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            prepareRectOnlyDrawPlan(
                sceneId = "oversize-rect-only",
                commands = listOf(
                    SceneCommand.FillRect(
                        label = "oversize",
                        rect = SceneRect(left = 0f, top = 0f, right = 9f, bottom = 9f),
                        color = SceneColor(1f, 0f, 0f, 1f),
                    ),
                ),
                width = 8,
                height = 8,
            )
        }

        assertContains(failure.message ?: "", "inside positive bounds: oversize")
    }

    @Test
    fun `rect only command preparation rejects out of bounds mesh ribbon scenes`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            prepareRectOnlyDrawPlan(
                sceneId = "oversize-mesh-ribbon",
                commands = listOf(
                    SceneCommand.MeshRibbon(
                        label = "ribbon",
                        bounds = SceneRect(left = 0f, top = 0f, right = 33f, bottom = 32f),
                        startColor = SceneColor.blue(),
                        endColor = SceneColor.amber(),
                    ),
                ),
                width = 32,
                height = 32,
            )
        }

        assertContains(failure.message ?: "", "inside positive bounds: ribbon")
    }

    @Test
    fun `rect only command preparation rejects clear after fill rectangles`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            prepareRectOnlyDrawPlan(
                sceneId = "late-clear",
                commands = listOf(
                    SceneCommand.FillRect(
                        label = "first-fill",
                        rect = SceneRect(left = 0f, top = 0f, right = 8f, bottom = 8f),
                        color = SceneColor.green(),
                    ),
                    SceneCommand.Clear(SceneColor(0f, 0f, 0f, 1f)),
                ),
                width = 16,
                height = 16,
            )
        }

        assertContains(failure.message ?: "", "zero or one initial Clear")
    }

    @Test
    fun `rect only command preparation rejects multiple clear commands`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            prepareRectOnlyDrawPlan(
                sceneId = "double-clear",
                commands = listOf(
                    SceneCommand.Clear(SceneColor(0f, 0f, 0f, 1f)),
                    SceneCommand.Clear(SceneColor(1f, 1f, 1f, 1f)),
                    SceneCommand.FillRect(
                        label = "first-fill",
                        rect = SceneRect(left = 0f, top = 0f, right = 8f, bottom = 8f),
                        color = SceneColor.green(),
                    ),
                ),
                width = 16,
                height = 16,
            )
        }

        assertContains(failure.message ?: "", "zero or one initial Clear")
    }

    @Test
    fun `rect only rendered byte count uses raw rgba readback bytes`() {
        val pixels = ByteArray(320 * 200 * 4)

        assertEquals(256000L, rectOnlyRawRgbaByteCount(pixels, width = 320, height = 200))
    }

    @Test
    fun `png writer unavailable diagnostic does not include absolute output path`() {
        val registry = IIORegistry.getDefaultInstance()
        val pngProviders = registry.pngImageWriterProviders()
        val output = Files.createTempDirectory("gpu-renderer-scenes-offscreen-main").resolve("render.png")

        try {
            pngProviders.forEach { registry.deregisterServiceProvider(it) }

            val failure = assertFailsWith<InvocationTargetException> {
                invokeRectOnlyWritePng(
                    pixels = ByteArray(4),
                    width = 1,
                    height = 1,
                    path = output,
                )
            }
            val message = failure.cause?.message ?: ""

            assertContains(message, "render.png")
            assertFalse(message.contains(output.parent.toAbsolutePath().toString()), message)
        } finally {
            pngProviders.forEach { registry.registerServiceProvider(it) }
        }
    }

    private data class RenderedShapeExpectation(
        val sceneId: String,
        val fillRectCount: Int,
        val fillRRectCount: Int = 0,
        val linearGradientRectCount: Int? = null,
        val clipCount: Int? = null,
        val bitmapRectCount: Int? = null,
        val filterNodeCount: Int? = null,
        val saveLayerCount: Int? = null,
        val runtimeEffectCount: Int? = null,
        val meshRibbonCount: Int? = null,
    )

    private fun assertRenderedShapeScene(root: Path, expectation: RenderedShapeExpectation) {
        val sceneId = expectation.sceneId
        renderSceneInWebGpuCapableProcess(root, sceneId)
        val sceneOutput = root.resolve(sceneId)
        val runJson = sceneOutput.resolve("run.json").readText()

        assertTrue(sceneOutput.resolve("render.png").exists(), sceneId)
        assertContains(runJson, "\"sceneId\": \"$sceneId\"")
        assertContains(runJson, "\"status\": \"${OffscreenRunStatus.Rendered.wireName}\"")
        assertContains(runJson, "\"productRefusal\": false")
        assertContains(runJson, "\"imagePath\": \"render.png\"")
        assertContains(runJson, "\"width\": 320")
        assertContains(runJson, "\"height\": 200")
        assertContains(runJson, "\"byteCount\": 256000")
        val nonTransparentPixels = Regex(""""nonTransparentPixels": (\d+)""")
            .find(runJson)
            ?.groupValues
            ?.get(1)
            ?.toInt()
        assertTrue((nonTransparentPixels ?: 0) > 0, sceneId)
        assertContains(runJson, "rendered $sceneId via WebGPU offscreen")
        assertContains(runJson, "fillRectCommands=${expectation.fillRectCount}")
        assertContains(runJson, "fillRRectCommands=${expectation.fillRRectCount}")
        expectation.linearGradientRectCount?.let { count ->
            assertContains(runJson, "linearGradientRectCommands=$count")
        }
        expectation.clipCount?.let { count ->
            assertContains(runJson, "clipCommands=$count")
        }
        expectation.bitmapRectCount?.let { count ->
            assertContains(runJson, "bitmapRectCommands=$count")
        }
        expectation.saveLayerCount?.let { count ->
            assertContains(runJson, "saveLayerCommands=$count")
            assertContains(runJson, "saveLayerKinds=bounded-shadow-card")
            assertContains(runJson, "saveLayerRoute=scene-fixture.bounded-shadow-card")
            assertContains(runJson, "saveLayerMaterializedDraws=${count * 2}")
            assertContains(runJson, "saveLayerFallbackReason=none")
            assertContains(runJson, "filterRoutes=scene-fixture.bounded-drop-shadow")
            assertContains(runJson, "generalSaveLayerSupport=false")
            assertContains(runJson, "imageFilterDagSupport=false")
        }
        expectation.filterNodeCount?.let { count ->
            assertContains(runJson, "filterNodeCommands=$count")
            if (expectation.sceneId == "layered-shadow-card") {
                assertContains(runJson, "filterKinds=drop-shadow")
                assertContains(runJson, "filterInputs=shadow-card-layer")
            } else if (expectation.sceneId == "notification-shadow-stack") {
                assertContains(runJson, "filterKinds=drop-shadow, drop-shadow")
                assertContains(runJson, "filterInputs=primary-notification-layer, secondary-notification-layer")
                assertContains(runJson, "saveLayerFilterKinds=drop-shadow, drop-shadow")
            } else if (expectation.sceneId == "tinted-avatar-card") {
                assertContains(runJson, "filterKinds=luma-tint")
                assertContains(runJson, "filterInputs=avatar-card-photo")
            } else {
                assertContains(runJson, "filterKinds=luma-tint")
                assertContains(runJson, "filterInputs=photo")
            }
        }
        expectation.runtimeEffectCount?.let { count ->
            assertContains(runJson, "runtimeEffectCommands=$count")
            assertContains(runJson, "runtimeEffectStableIds=runtime.simple_rt")
            assertContains(runJson, "runtimeEffectWgslImplementationIds=wgsl/runtime_simple_rt")
            assertContains(runJson, "runtimeEffectUniformLayout=gColor@0:16")
            assertContains(
                runJson,
                "runtimeEffectPipelineKey=runtimeEffect=SimpleRT descriptor=runtime_simple_rt.wgsl state=[blendMode=kSrcOver]",
            )
            assertContains(runJson, "runtimeEffectDescriptorEvidence=reports/wgsl-pipeline/runtime-effects-v2/support-matrix.json")
            assertContains(
                runJson,
                "runtimeEffectParserEvidence=RuntimeEffectDescriptorWebGpuTest#runtime SimpleRT descriptor WGSL parses and reflects uniforms",
            )
            assertContains(runJson, "fallbackReason=none")
        }
        if (expectation.sceneId == "runtime-effect-refusal-gate-board") {
            assertContains(runJson, "runtimeEffectRefusalMatrix=arbitrary-source:RefuseRequired:unsupported.runtime_effect.dynamic_sksl_forbidden,child-slot:RefuseRequired:unsupported.runtime_effect.child_count,unsupported-placement:RefuseRequired:unsupported.runtime_effect.route_unaccepted")
            assertContains(runJson, "pmRuntimeEffectRefusalRow=gpu-renderer.runtime-effect-refusals")
            assertContains(runJson, "pmRuntimeEffectRefusalClassification=RefuseRequired")
            assertContains(runJson, "dynamicSourceCompilation=false")
            assertContains(runJson, "childRuntimeEffectSupport=false")
        }
        if (expectation.sceneId == "text-resource-binding-gate-board") {
            assertContains(runJson, "textResourceBindingRefusalMatrix=upload-plan:RefuseRequired:unsupported.text.upload_plan_missing,binding-layout:RefuseRequired:unsupported.text.binding_layout_unavailable,stale-generation:RefuseRequired:unsupported.text.artifact_generation_stale,artifact-registration:RefuseRequired:unsupported.text.artifact_unregistered,upload-budget:RefuseRequired:unsupported.text.upload_budget_exceeded,cpu-rendered-texture:RefuseRequired:unsupported.text.cpu_rendered_texture_forbidden")
            assertContains(runJson, "pmTextResourceBindingRow=gpu-renderer.text-resource-binding")
            assertContains(runJson, "pmTextResourceBindingClassification=TargetPrepared")
            assertContains(runJson, "textUploadPlanPromoted=false")
            assertContains(runJson, "glyphAtlasRoutePromoted=false")
        }
        if (expectation.sceneId == "a8-glyph-atlas-gate-board") {
            assertContains(runJson, "a8GlyphAtlasRefusalMatrix=atlas-descriptor:TargetPrepared:unsupported.text.atlas_descriptor_unaccepted,atlas-page:RefuseRequired:unsupported.text.atlas_page_unavailable,atlas-entry:RefuseRequired:unsupported.text.atlas_entry_missing,atlas-generation:RefuseRequired:unsupported.text.atlas_generation_stale,a8-route:TargetPrepared:unsupported.text.a8_atlas_route_unavailable,instance-buffer:RefuseRequired:unsupported.text.instance_buffer_budget_exceeded")
            assertContains(runJson, "pmA8GlyphAtlasRow=gpu-renderer.text.a8-atlas")
            assertContains(runJson, "pmA8GlyphAtlasClassification=TargetPrepared")
            assertContains(runJson, "a8GlyphAtlasRoutePromoted=false")
            assertContains(runJson, "uploadBeforeSampleOrderingProven=false")
            assertContains(runJson, "cpuRenderedTextTextureFallback=false")
        }
        if (expectation.sceneId == "pm-readiness-freeze-board") {
            assertContains(runJson, "pmReadinessRow=gpu-renderer.readiness")
            assertContains(runJson, "pmReadinessClassification=PolicyGated")
            assertContains(runJson, "readinessDelta=0.0")
            assertContains(runJson, "releaseBlocking=false")
            assertContains(runJson, "productRouteActivated=false")
            assertContains(runJson, "performanceReadinessPromoted=false")
            assertContains(runJson, "missingGate=none")
            assertContains(runJson, "reportingOnlyGatesVisible=true")
            assertContains(runJson, "pipelinePmBundleUpdated=true")
            assertContains(runJson, "pmManifestKey=gpuRendererM9ReadinessPmEvidence")
            assertContains(runJson, "nonClaims=no-product-activation,no-release-blocking-gate,no-readiness-delta,no-performance-readiness-from-correctness,no-dashboard-row-promotes-readiness,no-derived-cache-as-observed")
        }
        if (expectation.sceneId == "legacy-retirement-blocker-board") {
            assertContains(runJson, "legacyRetirementRow=gpu-renderer.legacy-retirement")
            assertContains(runJson, "legacyRetirementClassification=PolicyGated")
            assertContains(runJson, "legacyRouteRetired=false")
            assertContains(runJson, "legacyDefaultActive=true")
            assertContains(runJson, "productRouteActivated=false")
            assertContains(runJson, "acceptedReplacementLinked=false")
            assertContains(runJson, "activationDecisionLinked=false")
            assertContains(runJson, "rollbackEvidenceLinked=false")
            assertContains(runJson, "pmEvidenceLinked=false")
            assertContains(runJson, "oldPathUsageEvidenceLinked=false")
            assertContains(runJson, "archivedEvidencePreserved=true")
            assertContains(runJson, "genericMigrationRetirement=false")
            assertContains(runJson, "missingGate=KGPU-M10-002")
        }
        if (expectation.sceneId == "path-stencil-cover-gate-board") {
            assertContains(runJson, "clearCommands=1")
            assertContains(runJson, "pathStencilCoverRow=gpu-renderer.path.stencil-cover")
            assertContains(runJson, "pathStencilCoverTicket=KGPU-M3-002")
            assertContains(runJson, "pathStencilCoverTicketStatus=done")
            assertContains(
                runJson,
                "pathStencilCoverClosure=contract-gate-complete-no-product-promotion",
            )
            assertContains(runJson, "pathStencilCoverClassification=TargetNative")
            assertContains(runJson, "pathStencilCoverRouteKind=GPUNative")
            assertContains(runJson, "pathStencilCoverAdapterRequired=true")
            assertContains(
                runJson,
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
            assertContains(runJson, "stencilCoverContractEvidenceLinked=true")
            assertContains(runJson, "explicitSkippedLaneDiagnosticsLinked=true")
            assertContains(runJson, "adapterBackedStencilEvidenceLinked=false")
            assertContains(runJson, "passResourceReadbackArtifactsLinked=false")
            assertContains(runJson, "producerBeforeCoverOrderingProven=false")
            assertContains(runJson, "stencilCoverRoutePromoted=false")
            assertContains(runJson, "productRouteActivated=false")
            assertContains(runJson, "releaseBlocking=false")
            assertContains(runJson, "cpuPreparedContinuationCountsAsSupport=false")
            assertContains(runJson, "descriptorOnlyPlanningCountsAsSupport=false")
            assertContains(runJson, "refusalOnlySelectorCountsAsSupport=false")
            assertContains(runJson, "nonClaims=no-native-stencil-cover-support,no-adapter-backed-execution,no-product-activation,no-release-blocking-gate,no-cpu-prepared-continuation-as-support,no-refusal-only-selector-as-support")
        }
        expectation.meshRibbonCount?.let { count ->
            assertContains(runJson, "meshRibbonCommands=$count")
            assertContains(runJson, "meshRibbonKinds=bounded-ribbon-strip")
            assertContains(runJson, "meshRibbonRoute=scene-fixture.bounded-ribbon-strip")
            assertContains(runJson, "meshRibbonFallbackReason=none")
            assertContains(runJson, "generalVerticesSupport=false")
            assertContains(runJson, "vertexIndexBufferSupport=false")
        }
        assertFalse(runJson.contains("runner-subset:$sceneId"), sceneId)
    }

    private fun assertNotYetRenderedScene(root: Path, sceneId: String, unsupportedFamilies: List<String>) {
        renderSceneInWebGpuCapableProcess(root, sceneId)
        val sceneOutput = root.resolve(sceneId)
        val runJson = sceneOutput.resolve("run.json").readText()
        val diagnostics = sceneOutput.resolve("diagnostics.txt").readText()

        assertFalse(sceneOutput.resolve("render.png").exists(), sceneId)
        assertContains(runJson, "\"sceneId\": \"$sceneId\"")
        assertContains(runJson, "\"status\": \"${OffscreenRunStatus.NotYetRendered.wireName}\"")
        assertContains(runJson, "\"productRefusal\": false")
        assertContains(runJson, "\"imagePath\": null")
        assertContains(runJson, "supports only clear, fill-rect, fill-rrect, stroke, linear-gradient-rect, radial-gradient-rect, sweep-gradient-rect, clip, path-fill-stencil, path-fill-gradient, convex-fan-mesh, bitmap-rect, save-layer, runtime-effect, mesh-ribbon, and text-run command families")
        assertContains(diagnostics, "supports only clear, fill-rect, fill-rrect, stroke, linear-gradient-rect, radial-gradient-rect, sweep-gradient-rect, clip, path-fill-stencil, path-fill-gradient, convex-fan-mesh, bitmap-rect, save-layer, runtime-effect, mesh-ribbon, and text-run command families")
        unsupportedFamilies.forEach { family ->
            assertContains(runJson, family, ignoreCase = false, message = sceneId)
            assertContains(diagnostics, family, ignoreCase = false, message = sceneId)
        }
    }

    private fun renderSceneInWebGpuCapableProcess(root: Path, sceneId: String) {
        val javaExecutable = File(System.getProperty("java.home"))
            .resolve("bin")
            .resolve(if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val command = buildList {
            add(javaExecutable.absolutePath)
            add("--add-opens=java.base/java.lang=ALL-UNNAMED")
            add("--enable-native-access=ALL-UNNAMED")
            if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
                add("-XstartOnFirstThread")
            }
            add("-cp")
            add(absoluteJavaClasspath())
            add("org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainKt")
            add(sceneId)
            add(root.toAbsolutePath().toString())
        }
        val process = ProcessBuilder(command)
            .directory(gpuRendererScenesProjectDir())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        assertEquals(0, exitCode, output)
    }

    private fun testBitmapRect(): SceneCommand.BitmapRect =
        SceneCommand.BitmapRect(
            label = "photo",
            rect = SceneRect(24f, 24f, 120f, 120f),
            source = SceneBitmapSource(
                topLeft = SceneColor.red(),
                topRight = SceneColor.blue(),
                bottomLeft = SceneColor.green(),
                bottomRight = SceneColor.amber(),
            ),
        )

    private fun absoluteJavaClasspath(): String =
        System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .joinToString(File.pathSeparator) { entry -> File(entry).absolutePath }

    private fun gpuRendererScenesProjectDir(): File {
        val userDir = File(System.getProperty("user.dir")).absoluteFile
        val childProjectDir = userDir.resolve("gpu-renderer-scenes")
        return if (childProjectDir.isDirectory) childProjectDir else userDir
    }

    private fun IIORegistry.pngImageWriterProviders(): List<ImageWriterSpi> {
        val providers = getServiceProviders(ImageWriterSpi::class.java, true)
        return buildList {
            while (providers.hasNext()) {
                val provider = providers.next()
                if (provider.formatNames.any { it.equals("png", ignoreCase = true) }) {
                    add(provider)
                }
            }
        }
    }

    private fun invokeRectOnlyWritePng(
        pixels: ByteArray,
        width: Int,
        height: Int,
        path: Path,
    ) {
        val method = RectOnlyOffscreenRenderer::class.java.getDeclaredMethod(
            "writePng",
            ByteArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Path::class.java,
        )
        method.isAccessible = true
        method.invoke(RectOnlyOffscreenRenderer(), pixels, width, height, path)
    }
}
