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
    fun `receipt text run writes stable text route unavailable report under scene directory`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-offscreen-main")

        renderGpuRendererSceneOffscreen(arrayOf("receipt-text-run", root.toString()))

        val sceneOutput = root.resolve("receipt-text-run")
        val runJson = sceneOutput.resolve("run.json").readText()
        val diagnostics = sceneOutput.resolve("diagnostics.txt").readText()
        assertContains(runJson, "\"sceneId\": \"receipt-text-run\"")
        assertContains(runJson, "\"status\": \"not-yet-rendered\"")
        assertContains(runJson, "\"productRefusal\": false")
        assertContains(runJson, "\"imagePath\": null")
        assertContains(runJson, "unsupported.text.draw_run_route_unavailable")
        assertContains(runJson, "commandFamily=text-run")
        assertContains(runJson, "fontFamily=Liberation Sans")
        assertContains(runJson, "glyphRoute=font.glyph.outline-path")
        assertContains(runJson, "lowerLevelTextRoutesAvailable=font.glyph.outline-path,webgpu.text.glyph-atlas.simple-latin")
        assertContains(runJson, "sceneRoutePromoted=false")
        assertContains(runJson, "nonClaims=no fake glyph substitute,no CPU-rendered text texture,no system font fallback,no broad shaping fallback emoji SDF LCD Kadre-windowed claim")
        assertContains(diagnostics, "fallbackReason=unsupported.text.draw_run_route_unavailable")
        assertFalse(runJson.contains("runner-subset:receipt-text-run"))
        assertFalse(sceneOutput.resolve("render.png").exists())
    }

    @Test
    fun `catalogued rect rrect gradient clip and bitmap scenes route to WebGPU offscreen instead of runner subset`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-offscreen-main")
        val rectOnlyScenes = listOf(
            RenderedShapeExpectation("activation-candidate-boundary-board", fillRectCount = 6),
            RenderedShapeExpectation("first-route-rollback-panel", fillRectCount = 4),
            RenderedShapeExpectation("blend-mode-strip", fillRectCount = 1),
            RenderedShapeExpectation("translucent-card-overlap", fillRectCount = 3),
            RenderedShapeExpectation("cache-pressure-deck", fillRectCount = 2),
            RenderedShapeExpectation("cache-source-ledger-board", fillRectCount = 5),
            RenderedShapeExpectation("legacy-route-comparison", fillRectCount = 1),
            RenderedShapeExpectation(
                sceneId = "legacy-inventory-hygiene-board",
                fillRectCount = 7,
                fillRRectCount = 1,
                clipCount = 1,
            ),
            RenderedShapeExpectation(
                sceneId = "rounded-panel-gradient",
                fillRectCount = 0,
                fillRRectCount = 1,
                linearGradientRectCount = 1,
                clipCount = 1,
            ),
            RenderedShapeExpectation(
                sceneId = "rrect-gradient-route-board",
                fillRectCount = 4,
                fillRRectCount = 2,
                linearGradientRectCount = 1,
                clipCount = 1,
            ),
            RenderedShapeExpectation(
                sceneId = "release-gate-progress-board",
                fillRectCount = 1,
                fillRRectCount = 1,
                linearGradientRectCount = 1,
                clipCount = 1,
            ),
            RenderedShapeExpectation(
                sceneId = "path-badge-and-stroke",
                fillRectCount = 1,
                fillRRectCount = 1,
            ),
            RenderedShapeExpectation(
                sceneId = "path-coverage-review-board",
                fillRectCount = 4,
                fillRRectCount = 1,
                clipCount = 1,
            ),
            RenderedShapeExpectation(
                sceneId = "texture-swatch-board",
                fillRectCount = 0,
                bitmapRectCount = 2,
            ),
            RenderedShapeExpectation(
                sceneId = "asset-intake-thumbnail-grid",
                fillRectCount = 0,
                fillRRectCount = 1,
                clipCount = 1,
                bitmapRectCount = 2,
            ),
            RenderedShapeExpectation(
                sceneId = "codec-provenance-gate-board",
                fillRectCount = 3,
                fillRRectCount = 1,
                clipCount = 1,
                bitmapRectCount = 1,
            ),
            RenderedShapeExpectation(
                sceneId = "sampler-boundary-gate-board",
                fillRectCount = 4,
                fillRRectCount = 1,
                clipCount = 1,
                bitmapRectCount = 2,
            ),
            RenderedShapeExpectation(
                sceneId = "savelayer-isolation-gate-board",
                fillRectCount = 7,
                fillRRectCount = 1,
                clipCount = 1,
            ),
            RenderedShapeExpectation(
                sceneId = "clipped-avatar-grid",
                fillRectCount = 0,
                clipCount = 1,
                bitmapRectCount = 1,
            ),
            RenderedShapeExpectation(
                sceneId = "filtered-photo-chip",
                fillRectCount = 0,
                bitmapRectCount = 1,
                filterNodeCount = 1,
            ),
            RenderedShapeExpectation("filter-dag-refusal-board", fillRectCount = 5),
            RenderedShapeExpectation(
                sceneId = "layered-shadow-card",
                fillRectCount = 0,
                saveLayerCount = 1,
                filterNodeCount = 1,
            ),
            RenderedShapeExpectation(
                sceneId = "text-handoff-boundary-board",
                fillRectCount = 4,
                fillRRectCount = 1,
                clipCount = 1,
            ),
            RenderedShapeExpectation(
                sceneId = "text-representation-gate-board",
                fillRectCount = 8,
                fillRRectCount = 1,
                clipCount = 1,
            ),
            RenderedShapeExpectation(
                sceneId = "runtime-effect-color-tile",
                fillRectCount = 0,
                runtimeEffectCount = 1,
            ),
            RenderedShapeExpectation(
                sceneId = "runtime-effect-descriptor-gate-board",
                fillRectCount = 6,
                fillRRectCount = 1,
                clipCount = 1,
                runtimeEffectCount = 1,
            ),
            RenderedShapeExpectation(
                sceneId = "sdr-color-boundary-board",
                fillRectCount = 7,
                fillRRectCount = 1,
                clipCount = 1,
            ),
            RenderedShapeExpectation(
                sceneId = "mesh-ribbon",
                fillRectCount = 0,
                meshRibbonCount = 1,
            ),
        )

        rectOnlyScenes.forEach { expectation ->
            assertRenderedShapeScene(root, expectation)
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
    fun `rect only command preparation rejects scenes without fill rectangles`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            prepareRectOnlyDrawPlan(
                sceneId = "empty-rect-only",
                commands = listOf(SceneCommand.Clear(SceneColor(0f, 0f, 0f, 1f))),
                width = 320,
                height = 200,
            )
        }

        assertContains(failure.message ?: "", "at least one FillRect")
    }

    @Test
    fun `rect only command preparation rejects bitmap markers without fixture payloads`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            prepareRectOnlyDrawPlan(
                sceneId = "bitmap-marker-only",
                commands = listOf(SceneCommand.BitmapRect("marker")),
                width = 320,
                height = 200,
            )
        }

        assertContains(failure.message ?: "", "fixture-backed BitmapRect payloads: marker")
    }

    @Test
    fun `rect only command preparation rejects filter markers without fixture payloads`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            prepareRectOnlyDrawPlan(
                sceneId = "filter-marker-only",
                commands = listOf(testBitmapRect(), SceneCommand.FilterNode("marker")),
                width = 320,
                height = 200,
            )
        }

        assertContains(failure.message ?: "", "fixture-backed FilterNode payloads: marker")
    }

    @Test
    fun `rect only command preparation rejects save layer markers without fixture payloads`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            prepareRectOnlyDrawPlan(
                sceneId = "save-layer-marker-only",
                commands = listOf(SceneCommand.SaveLayer("marker")),
                width = 320,
                height = 200,
            )
        }

        assertContains(failure.message ?: "", "fixture-backed SaveLayer payloads: marker")
    }

    @Test
    fun `rect only command preparation rejects runtime effect markers without fixture payloads`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            prepareRectOnlyDrawPlan(
                sceneId = "runtime-effect-marker-only",
                commands = listOf(SceneCommand.RuntimeEffectTile("marker")),
                width = 320,
                height = 200,
            )
        }

        assertContains(failure.message ?: "", "fixture-backed RuntimeEffectTile payloads: marker")
    }

    @Test
    fun `rect only command preparation rejects mesh ribbon markers without fixture payloads`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            prepareRectOnlyDrawPlan(
                sceneId = "mesh-ribbon-marker-only",
                commands = listOf(SceneCommand.MeshRibbon("marker")),
                width = 320,
                height = 200,
            )
        }

        assertContains(failure.message ?: "", "fixture-backed MeshRibbon payloads: marker")
    }

    @Test
    fun `rect only command preparation rejects runtime effects outside the registered SimpleRT contract`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            prepareRectOnlyDrawPlan(
                sceneId = "runtime-effect-wrong-descriptor",
                commands = listOf(
                    SceneCommand.RuntimeEffectTile(
                        label = "wrong-runtime-effect",
                        rect = SceneRect(16f, 16f, 96f, 96f),
                        stableId = "runtime.spiral_rt",
                        wgslImplementationId = "wgsl/runtime_spiral_rt",
                        uniformColor = SceneColor.blue(),
                    ),
                ),
                width = 320,
                height = 200,
            )
        }

        assertContains(failure.message ?: "", "supports only registered runtime.simple_rt RuntimeEffectTile payloads")
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
    fun `rect only command preparation rejects out of bounds mesh ribbons`() {
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

        assertContains(failure.message ?: "", "MeshRibbon bounds inside positive target: ribbon")
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
            assertContains(runJson, "saveLayerMaterializedDraws=2")
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
