@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode as CanvasBlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/** Public full-buffer witnesses for ordered DrawColor and hard-path root composition. */
class W7HardPathRootDrawColorSurfacePixelTest {
    companion object {
        private const val SIZE = 32
        private const val BYTE_COUNT = SIZE * SIZE * 4
        private const val HISTORICAL_PATH_STENCIL_REFUSAL =
            "invalid.preflight.core_primitive_path_stencil: Path stencil CorePrimitive requires exactly one prepared render pass."
        private val FULL = RectF32.ofLTRB(0f, 0f, SIZE.toFloat(), SIZE.toFloat())

        @AfterAll
        @JvmStatic
        fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun drawColorBeforeBetweenAfterHardPathsPreservesOrder() {
        val beforeExpected = orderedPathsBuffer(middle = null, after = false)
        val betweenExpected = orderedPathsBuffer(middle = ColorARGB.Blue, after = false)
        val afterExpected = solidBuffer(Rgba(0, 0, 255))
        val before = Surface(SIZE, SIZE)
        before.canvas {
            drawColor(ColorARGB.White)
            drawPath(redPath(), darken(ColorARGB.Red))
            drawPath(greenPath(), darken(ColorARGB.Green))
        }
        val between = Surface(SIZE, SIZE)
        between.canvas {
            drawColor(ColorARGB.White)
            drawPath(redPath(), darken(ColorARGB.Red))
            drawColor(ColorARGB.Blue)
            drawPath(greenPath(), darken(ColorARGB.Green))
        }
        val after = Surface(SIZE, SIZE)
        after.canvas {
            drawColor(ColorARGB.White)
            drawPath(redPath(), darken(ColorARGB.Red))
            drawPath(greenPath(), darken(ColorARGB.Green))
            drawColor(ColorARGB.Blue)
        }

        assertAll(listOf(
            { renderAndAssert(before, "order-before", beforeExpected) },
            { renderAndAssert(between, "order-between", betweenExpected) },
            { renderAndAssert(after, "order-after", afterExpected) },
        ))
    }

    @Test
    fun partialAlphaBetweenHardPathsRefreshesDestination() {
        val expected = buffer { x, y ->
            val first = x in 4 until 20 && y in 4 until 20
            val second = x in 12 until 28 && y in 12 until 28
            when {
                first && second -> Rgba(0, 0, 0)
                first -> Rgba(187, 0, 188)
                second -> Rgba(0, 187, 0)
                else -> Rgba(187, 187, 255)
            }
        }
        val surface = Surface(SIZE, SIZE, config = RenderConfig(compositionDomain = CompositionDomain.LINEAR))
        surface.canvas {
            drawColor(ColorARGB.White)
            drawPath(redPath(), darken(ColorARGB.Red))
            drawColor(ColorARGB.of(128, 0, 0, 255))
            drawPath(greenPath(), darken(ColorARGB.Green))
        }
        renderAndAssert(surface, "partial-alpha-between-paths", expected)
    }

    @Test
    fun directTriangleAndStencilContourShareFrameOrder() {
        val expected = buffer { x, y ->
            val inTriangle = x >= 4 && y >= 4 &&
                16.0 * (x + 0.5 - 4.0) + 15.0 * (y + 0.5 - 4.0) < 240.0
            val inStencil = x in 8 until 24 && y in 8 until 24
            when {
                inTriangle && inStencil -> Rgba(0, 0, 0)
                inTriangle -> Rgba(187, 0, 188)
                inStencil -> Rgba(0, 187, 0)
                else -> Rgba(187, 187, 255)
            }
        }
        val triangle = Path().apply {
            moveTo(4f, 4f)
            lineTo(19f, 4f)
            lineTo(4f, 20f)
            close()
        }
        val contour = rectPath(8f, 8f, 24f, 24f)
        val surface = Surface(SIZE, SIZE, config = RenderConfig(compositionDomain = CompositionDomain.LINEAR))
        surface.canvas {
            drawColor(ColorARGB.White)
            drawPath(triangle, Paint(ColorARGB.Red, antiAlias = false))
            drawColor(ColorARGB.of(128, 0, 0, 255))
            drawPath(contour, darken(ColorARGB.Green))
        }
        renderAndAssert(surface, "triangle-and-stencil-order", expected)
    }

    @Test
    fun noOpPathDoesNotShiftMaterialOrColorOccurrences() {
        val expected = orderedPathsBuffer(middle = null, after = false)
        val surface = Surface(SIZE, SIZE)
        surface.canvas {
            drawColor(ColorARGB.White)
            drawPath(redPath(), darken(ColorARGB.Red))
            drawPath(rectPath(0f, 0f, 32f, 32f), Paint(ColorARGB.Blue, blendMode = CanvasBlendMode.DST, antiAlias = false))
            drawPath(greenPath(), darken(ColorARGB.Green))
        }
        renderAndAssert(surface, "dst-path-between-hard-paths", expected)
    }

    @Test
    fun homogeneousControlsRemainNative() {
        val colorsExpected = solidBuffer(Rgba(0, 255, 0))
        val pathsExpected = buffer { x, y ->
            val first = x in 4 until 20 && y in 4 until 20
            val second = x in 12 until 28 && y in 12 until 28
            when {
                first && second -> Rgba(0, 0, 0)
                first -> Rgba(255, 0, 0)
                second -> Rgba(0, 255, 0)
                else -> Rgba(0, 0, 0, 0)
            }
        }
        val colorsOnly = Surface(SIZE, SIZE)
        colorsOnly.canvas { drawColor(ColorARGB.White); drawColor(ColorARGB.Blue); drawColor(ColorARGB.Green) }
        val pathsOnly = Surface(SIZE, SIZE)
        pathsOnly.canvas {
            drawPath(redPath(), darken(ColorARGB.Red))
            drawPath(greenPath(), darken(ColorARGB.Green))
        }
        assertAll(listOf(
            { renderAndAssert(colorsOnly, "homogeneous-colors", colorsExpected) },
            { renderAndAssert(pathsOnly, "homogeneous-paths", pathsExpected) },
        ))
    }

    @Test
    fun mixedCommandBoundaryKeepsHistoricalContinuation() {
        val expected512 = orderedPathsBuffer(middle = null, after = false)
        val recoveryExpected = solidBuffer(Rgba(0, 0, 255))
        assertAll(listOf(
            {
                val surface = Surface(SIZE, SIZE)
                recordBoundaryScene(surface, colorCount = 510)
                renderAndAssert(surface, "boundary-512", expected512)
            },
            {
                val surface = Surface(SIZE, SIZE)
                recordBoundaryScene(surface, colorCount = 511)
                assertHistoricalPathStencilRefusalAndRecover(surface, "boundary-513", recoveryExpected)
            },
        ))
    }

    @Test
    fun mixedFrameBudgetBoundaryRefusesAndRecovers() {
        val budgetB = 69_664L
        val expected = orderedPathsBuffer(middle = null, after = false)
        val recoveryExpected = solidBuffer(Rgba(0, 0, 255))
        assertAll(listOf(
            {
                val admitted = Surface(SIZE, SIZE, config = RenderConfig(frameLocalBudgetBytes = budgetB))
                recordBudgetScene(admitted)
                renderAndAssert(admitted, "budget-B", expected)
            },
            {
                val refused = Surface(SIZE, SIZE, config = RenderConfig(frameLocalBudgetBytes = budgetB - 1L))
                recordBudgetScene(refused)
                val sentinel = UByteArray(BYTE_COUNT) { 0x5au }
                val before = sentinel.copyOf()
                val failure = runCatching { refused.readPixels(FULL, sentinel) }.exceptionOrNull()
                val sentinelUnchanged = sentinel.contentEquals(before)
                println("w7.hard-path-root.budget-refusal diagnostic=${failure?.message}")
                refused.discardRecordedOperations()
                refused.canvas { drawColor(ColorARGB.Blue) }
                renderAndAssert(refused, "budget-B-minus-1-recovery", recoveryExpected)
                assertTrue(sentinelUnchanged, "B-1 refusal must leave the entire caller buffer unchanged")
                assertTrue(failure is IllegalStateException, failure?.toString() ?: "B-1 did not refuse")
                assertTrue(
                    failure.message?.startsWith("resource-limit.w5b.destination-budget") == true,
                    failure.message,
                )
            },
        ))
    }

    @Test
    fun foreignRootFamiliesRefuseAndRecover() {
        val recoveryExpected = solidBuffer(Rgba(0, 0, 255))
        assertAll(listOf(
            {
                val surface = Surface(SIZE, SIZE)
                surface.canvas {
                    drawRect(FULL, constantBlueGradient())
                    drawColor(ColorARGB.Green)
                    drawRect(RectF32.ofLTRB(8f, 8f, 24f, 24f), Paint(
                        shader = Shader.SolidColor(ColorARGB.Red),
                        antiAlias = true,
                        style = PaintStyle.STROKE,
                        strokeWidth = 1f,
                    ))
                }
                val sentinel = UByteArray(BYTE_COUNT) { 0x5au }
                val before = sentinel.copyOf()
                val failure = assertFailsWith<IllegalStateException> { surface.readPixels(FULL, sentinel) }
                println("w7.hard-path-root.foreign-stroke-refusal type=${failure::class.qualifiedName} diagnostic=${failure.message}")
                assertTrue(failure.message?.startsWith("unsupported.stroke.rect_anti_alias:") == true, failure.message)
                assertContentEquals(before, sentinel)
                surface.discardRecordedOperations()
                surface.canvas { drawColor(ColorARGB.Blue) }
                renderAndAssert(surface, "foreign-stroke-recovery", recoveryExpected)
            },
            {
                val surface = Surface(SIZE, SIZE)
                surface.canvas {
                    drawColor(ColorARGB.White)
                    drawPath(redPath(), darken(ColorARGB.Red))
                    drawPath(greenPath(), Paint(
                        color = ColorARGB.Green,
                        shader = Shader.SolidColor(ColorARGB.Green),
                        blendMode = CanvasBlendMode.DARKEN,
                        antiAlias = false,
                    ))
                }
                assertHistoricalPathStencilRefusalAndRecover(surface, "foreign-shader-path", recoveryExpected)
            },
        ))
    }

    private fun recordBudgetScene(surface: Surface) = surface.canvas {
        drawColor(ColorARGB.White)
        drawPath(redPath(), darken(ColorARGB.Red))
        drawPath(greenPath(), darken(ColorARGB.Green))
    }

    private fun recordBoundaryScene(surface: Surface, colorCount: Int) = surface.canvas {
        repeat(colorCount) { drawColor(ColorARGB.White) }
        drawPath(redPath(), darken(ColorARGB.Red))
        drawPath(greenPath(), darken(ColorARGB.Green))
    }

    private fun assertHistoricalPathStencilRefusalAndRecover(
        surface: Surface,
        label: String,
        recoveryExpected: UByteArray,
    ) {
        val callerBuffer = UByteArray(BYTE_COUNT) { 0x5au }
        val before = callerBuffer.copyOf()
        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(FULL, callerBuffer) }
        retainCallerBuffer(callerBuffer, "$label-caller-sentinel")
        println("w7.hard-path-root.refusal label=$label type=${failure.javaClass.name} diagnostic=${failure.message}")
        assertEquals(
            "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceTerminalException",
            failure.javaClass.name,
            "$label refusal exception type",
        )
        assertEquals(HISTORICAL_PATH_STENCIL_REFUSAL, failure.message, "$label refusal message")
        assertContentEquals(before, callerBuffer, "$label refusal must leave all caller sentinel bytes unchanged")
        surface.discardRecordedOperations()
        surface.canvas { drawColor(ColorARGB.Blue) }
        renderAndAssert(surface, "$label-recovery", recoveryExpected)
    }

    private fun darken(color: ColorARGB) = Paint(color, blendMode = CanvasBlendMode.DARKEN, antiAlias = false)

    private fun constantBlueGradient() = Paint(
        shader = Shader.LinearGradient(
            Point2F32(0f, 0f), Point2F32(SIZE.toFloat(), 0f),
            listOf(org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Blue),
                org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue)),
        ),
        antiAlias = false,
    )

    private fun rectPath(left: Float, top: Float, right: Float, bottom: Float) =
        Path().apply { addRect(RectF32.ofLTRB(left, top, right, bottom)) }

    private fun redPath() = rectPath(4f, 4f, 20f, 20f)
    private fun greenPath() = rectPath(12f, 12f, 28f, 28f)

    private fun orderedPathsBuffer(middle: ColorARGB?, after: Boolean): UByteArray = buffer { x, y ->
        val first = x in 4 until 20 && y in 4 until 20
        val second = x in 12 until 28 && y in 12 until 28
        val result = when {
            first && second -> Rgba(0, 0, 0)
            first -> Rgba(255, 0, 0)
            second -> Rgba(0, 255, 0)
            else -> Rgba(255, 255, 255)
        }
        when {
            after -> Rgba(0, 0, 255)
            middle != null && second -> Rgba(0, 0, 0)
            middle != null -> Rgba(0, 0, 255)
            else -> result
        }
    }

    private fun renderAndAssert(surface: Surface, label: String, expected: UByteArray): RenderResult {
        val first = surface.render()
        retainActualBuffer(first, "$label-1")
        assertNative(first, "$label first")
        assertFullBuffer(expected, first, "$label first")
        val second = surface.render()
        retainActualBuffer(second, "$label-2")
        assertNative(second, "$label second")
        assertFullBuffer(expected, second, "$label second")
        assertContentEquals(first.pixels, second.pixels, "$label repeated render must be byte-identical")
        return first
    }

    private fun assertNative(result: RenderResult, label: String) {
        val evidence = "$label size=${result.width}x${result.height} format=${result.format} " +
            "stats=${result.stats} steps=${result.structuralSteps} counters=${result.nativeEvidenceCounters} " +
            "scopes=${result.nativeEvidenceScopeKinds} diagnostics=${result.diagnostics.summary()} bytes=${result.pixels.size}"
        println("w7.hard-path-root.native $evidence")
        assertEquals(SIZE, result.width, evidence)
        assertEquals(SIZE, result.height, evidence)
        assertEquals(PixelFormat.RGBA8, result.format, evidence)
        assertEquals(BYTE_COUNT, result.pixels.size, evidence)
        assertTrue(result.isClean, evidence)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), evidence)
        assertEquals(0, result.stats.opsRefused, evidence)
        assertTrue(result.stats.opsDispatched > 0, evidence)
        val submitted = result.structuralSteps.indexOf("QueueSubmitted")
        val completed = result.structuralSteps.indexOf("CompletionSucceeded")
        assertTrue(submitted >= 0, evidence)
        assertTrue(completed > submitted, evidence)
        assertEquals(1L, result.nativeEvidenceCounters["submits"], evidence)
        assertEquals(1L, result.nativeEvidenceCounters["readbackCopies"], evidence)
    }

    private fun assertFullBuffer(expected: UByteArray, result: RenderResult, label: String) {
        assertEquals(BYTE_COUNT, expected.size, "$label oracle must cover the full RGBA8 target")
        assertEquals(BYTE_COUNT, result.pixels.size, "$label returned full RGBA8 target")
        for (offset in expected.indices) {
            val want = expected[offset].toInt()
            val got = result.pixels[offset].toInt()
            if (offset % 4 == 3) assertEquals(want, got, "$label alpha pixel=${offset / 4}")
            else assertTrue(abs(want - got) <= 2, "$label RGB byte=$offset expected=$want actual=$got")
        }
    }

    private fun retainActualBuffer(result: RenderResult, label: String) {
        val evidenceRoot = System.getProperty("w7.ordinaryAaEvidenceDir") ?: return
        val directory = File(evidenceRoot)
        check(directory.isDirectory) { "W7 evidence directory does not exist: $directory" }
        val file = File(directory, "w7-hard-path-root-$label.rgba")
        check(!file.exists()) { "Refusing to overwrite W7 pixel evidence: $file" }
        val bytes = ByteArray(result.pixels.size) { index -> result.pixels[index].toByte() }
        file.outputStream().use { output -> output.write(bytes) }
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        println("w7.hard-path-root.actual-buffer path=${file.absolutePath} byteCount=${bytes.size} sha256=$sha256")
    }

    private fun retainCallerBuffer(pixels: UByteArray, label: String) {
        val evidenceRoot = System.getProperty("w7.ordinaryAaEvidenceDir") ?: return
        val directory = File(evidenceRoot)
        check(directory.isDirectory) { "W7 evidence directory does not exist: $directory" }
        val file = File(directory, "w7-hard-path-root-$label.rgba")
        check(!file.exists()) { "Refusing to overwrite W7 caller-buffer evidence: $file" }
        val bytes = ByteArray(pixels.size) { index -> pixels[index].toByte() }
        file.outputStream().use { output -> output.write(bytes) }
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        println("w7.hard-path-root.caller-buffer path=${file.absolutePath} byteCount=${bytes.size} sha256=$sha256")
    }

    private fun solidBuffer(color: Rgba) = buffer { _, _ -> color }

    private fun buffer(colorAt: (x: Int, y: Int) -> Rgba): UByteArray = UByteArray(BYTE_COUNT).also { pixels ->
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val color = colorAt(x, y)
            val offset = (y * SIZE + x) * 4
            pixels[offset] = color.red.toUByte()
            pixels[offset + 1] = color.green.toUByte()
            pixels[offset + 2] = color.blue.toUByte()
            pixels[offset + 3] = color.alpha.toUByte()
        }
    }

    private data class Rgba(val red: Int, val green: Int, val blue: Int, val alpha: Int = 255)
}
