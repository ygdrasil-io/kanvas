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
    fun `non first route scene writes not yet rendered report under scene directory`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-offscreen-main")

        renderGpuRendererSceneOffscreen(arrayOf("mesh-ribbon", root.toString()))

        val runJson = root.resolve("mesh-ribbon").resolve("run.json").readText()
        assertContains(runJson, "\"sceneId\": \"mesh-ribbon\"")
        assertContains(runJson, "\"status\": \"not-yet-rendered\"")
        assertContains(runJson, "\"productRefusal\": false")
        assertContains(runJson, "\"imagePath\": null")
        assertContains(runJson, "runner-subset:mesh-ribbon")
    }

    @Test
    fun `catalogued rect rrect gradient clip and bitmap scenes route to WebGPU offscreen instead of runner subset`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-offscreen-main")
        val rectOnlyScenes = listOf(
            RenderedShapeExpectation("blend-mode-strip", fillRectCount = 1),
            RenderedShapeExpectation("cache-pressure-deck", fillRectCount = 2),
            RenderedShapeExpectation("legacy-route-comparison", fillRectCount = 1),
            RenderedShapeExpectation(
                sceneId = "rounded-panel-gradient",
                fillRectCount = 0,
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
                sceneId = "texture-swatch-board",
                fillRectCount = 0,
                bitmapRectCount = 2,
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
