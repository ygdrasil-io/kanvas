@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.ColorSpaceInterpolation
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/** Public whole-frame W3 witnesses for ordered DrawColor plus two real Rect materials. */
class W7W3DrawColorMaterialSurfacePixelTest {
    companion object {
        private const val SIZE = 32
        private const val BYTE_COUNT = SIZE * SIZE * 4
        private val FULL = RectF32.ofLTRB(0f, 0f, SIZE.toFloat(), SIZE.toFloat())
        private val FIRST = RectF32.ofLTRB(4f, 4f, 20f, 20f)
        private val SECOND = RectF32.ofLTRB(12f, 12f, 28f, 28f)

        @AfterAll
        @JvmStatic
        fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun drawColorBeforeBetweenAfterMaterialsPreservesOrder() {
        val beforeExpected = twoRectBuffer(
            outside = Rgba(0, 0, 255), firstOnly = Rgba(255, 0, 0), second = Rgba(0, 255, 0),
        )
        val betweenExpected = twoRectBuffer(
            outside = Rgba(255, 255, 255), firstOnly = Rgba(255, 255, 255), second = Rgba(0, 255, 0),
        )
        val afterExpected = solidBuffer(Rgba(0, 0, 255))

        assertAll(listOf(
            {
                val surface = Surface(SIZE, SIZE)
                surface.canvas {
                    drawColor(ColorARGB.Blue)
                    drawRect(FIRST, resolvedRedPaint())
                    drawRect(SECOND, pendingGreenPaint())
                }
                renderAndAssert(surface, "before-materials", beforeExpected)
            },
            {
                val surface = Surface(SIZE, SIZE)
                surface.canvas {
                    drawColor(ColorARGB.Blue)
                    drawRect(FIRST, resolvedRedPaint())
                    drawColor(ColorARGB.White)
                    drawRect(SECOND, pendingGreenPaint())
                }
                renderAndAssert(surface, "between-materials", betweenExpected)
            },
            {
                val surface = Surface(SIZE, SIZE)
                surface.canvas {
                    drawRect(FIRST, resolvedRedPaint())
                    drawRect(SECOND, pendingGreenPaint())
                    drawColor(ColorARGB.Blue)
                }
                renderAndAssert(surface, "after-materials", afterExpected)
            },
        ))
    }

    @Test
    fun partialAlphaBetweenMaterialsIsAppliedOnce() {
        val expected = buffer { x, y ->
            when {
                x in SECOND.left.toInt() until SECOND.right.toInt() &&
                    y in SECOND.top.toInt() until SECOND.bottom.toInt() -> Rgba(0, 255, 0)
                x in FIRST.left.toInt() until FIRST.right.toInt() &&
                    y in FIRST.top.toInt() until FIRST.bottom.toInt() -> Rgba(187, 188, 0)
                else -> Rgba(0, 188, 187)
            }
        }

        val surface = Surface(
            SIZE, SIZE,
            config = RenderConfig(compositionDomain = CompositionDomain.LINEAR),
        )
        surface.canvas {
            drawColor(ColorARGB.Blue)
            drawRect(FIRST, resolvedRedPaint())
            drawColor(ColorARGB.of(128, 0, 255, 0))
            drawRect(SECOND, pendingGreenPaint())
        }
        renderAndAssert(surface, "partial-alpha-between-materials", expected)
    }

    @Test
    fun pendingAndResolvedMaterialsKeepTheirOwnIndices() {
        val resolvedThenPendingExpected = twoRectBuffer(
            outside = Rgba(0, 0, 255), firstOnly = Rgba(255, 0, 0), second = Rgba(0, 255, 0),
        )
        val pendingThenResolvedExpected = twoRectBuffer(
            outside = Rgba(0, 0, 255), firstOnly = Rgba(255, 0, 0), second = Rgba(0, 255, 0),
            firstWinsOverlap = true,
        )

        assertAll(listOf(
            {
                val surface = Surface(SIZE, SIZE)
                surface.canvas {
                    drawColor(ColorARGB.Blue)
                    drawRect(FIRST, resolvedRedPaint())
                    drawColor(ColorARGB.Transparent)
                    drawRect(SECOND, pendingGreenPaint())
                }
                renderAndAssert(surface, "resolved-then-pending", resolvedThenPendingExpected)
            },
            {
                val surface = Surface(SIZE, SIZE)
                surface.canvas {
                    drawColor(ColorARGB.Blue)
                    drawRect(SECOND, pendingGreenPaint())
                    drawColor(ColorARGB.Transparent)
                    drawRect(FIRST, resolvedRedPaint())
                }
                renderAndAssert(surface, "pending-then-resolved", pendingThenResolvedExpected)
            },
        ))
    }

    @Test
    fun homogeneousControlsRemainNative() {
        val materialExpected = twoRectBuffer(
            outside = Rgba(0, 0, 0, 0), firstOnly = Rgba(255, 0, 0), second = Rgba(0, 255, 0),
        )
        val colorExpected = solidBuffer(Rgba(0, 0, 255))

        val materialOnly = Surface(SIZE, SIZE)
        materialOnly.canvas {
            drawRect(FIRST, resolvedRedPaint())
            drawRect(SECOND, pendingGreenPaint())
        }
        renderAndAssert(materialOnly, "homogeneous-materials", materialExpected)

        val colorOnly = Surface(SIZE, SIZE)
        colorOnly.canvas { drawColor(ColorARGB.Blue) }
        renderAndAssert(colorOnly, "homogeneous-color", colorExpected)

        printPassiveInventoryEvidence()
    }

    @Test
    fun w3CommandBoundaryRefusesAndRecovers() {
        val blueExpected = solidBuffer(Rgba(0, 0, 255))
        val atLimit = Surface(SIZE, SIZE)
        atLimit.canvas { repeat(512) { drawColor(ColorARGB.Blue) } }
        renderAndAssert(atLimit, "command-boundary-512", blueExpected)

        val overLimit = Surface(SIZE, SIZE)
        overLimit.canvas { repeat(513) { drawColor(ColorARGB.Blue) } }
        val sentinel = UByteArray(BYTE_COUNT) { 0x5au }
        val sentinelBefore = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> {
            overLimit.readPixels(FULL, sentinel)
        }
        println("w7.w3-drawcolor-material.command-boundary diagnostic=${failure.message}")
        assertContentEquals(sentinelBefore, sentinel, "513-command refusal must preserve the full caller buffer")

        overLimit.discardRecordedOperations()
        overLimit.canvas { drawColor(ColorARGB.Blue) }
        renderAndAssert(overLimit, "command-boundary-recovery", blueExpected)
    }

    private fun resolvedRedPaint() = Paint(
        color = ColorARGB.Red,
        colorFilter = ColorFilter.Matrix(ColorMatrixF32.ofIdentity()),
        antiAlias = false,
    )

    private fun pendingGreenPaint() = Paint(
        shader = Shader.LinearGradient(
            Point2F32(0f, 0f),
            Point2F32(SIZE.toFloat(), 0f),
            listOf(GradientStop(0f, ColorARGB.Green), GradientStop(1f, ColorARGB.Green)),
            interpolation = ColorSpaceInterpolation.LINEAR,
        ),
        antiAlias = false,
    )

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
            "scopes=${result.nativeEvidenceScopeKinds} diagnostics=${result.diagnostics.summary()} " +
            "bytes=${result.pixels.size}"
        println("w7.w3-drawcolor-material.native $evidence")
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

    private fun printPassiveInventoryEvidence() {
        val session = GPUBackendRuntimeFactory.createOrNull()
        if (session == null) {
            println("w7.w3-drawcolor-material.inventory session=unavailable")
            return
        }
        val limits = session.capabilities?.limits
        if (limits == null) {
            println("w7.w3-drawcolor-material.inventory capabilities-limits=unavailable")
        } else {
            println(
                "w7.w3-drawcolor-material.inventory " +
                    "copyBytesPerRowAlignment=${limits.copyBytesPerRowAlignment} " +
                    "minUniformBufferOffsetAlignment=${limits.minUniformBufferOffsetAlignment} " +
                    "maxBufferSize=${limits.maxBufferSize} " +
                    "maxDynamicUniformBuffersPerPipelineLayout=${limits.maxDynamicUniformBuffersPerPipelineLayout}",
            )
        }
        val runtimeLines = session.runtimeTelemetryDumpLines
        if (runtimeLines.isEmpty()) {
            println("w7.w3-drawcolor-material.runtimeTelemetryDumpLines=unavailable-or-empty")
        } else runtimeLines.forEach { line ->
            println("w7.w3-drawcolor-material.runtime $line")
        }
        val resourceLines = session.resourceProviderDumpLines
        if (resourceLines.isEmpty()) {
            println("w7.w3-drawcolor-material.resourceProviderDumpLines=unavailable-or-empty")
        } else resourceLines.forEach { line ->
            println("w7.w3-drawcolor-material.resource $line")
        }
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
        val file = File(directory, "w7-w3-drawcolor-material-$label.rgba")
        check(!file.exists()) { "Refusing to overwrite W7 pixel evidence: $file" }
        val bytes = ByteArray(result.pixels.size) { index -> result.pixels[index].toByte() }
        file.outputStream().use { output -> output.write(bytes) }
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        println("w7.w3-drawcolor-material.actual-buffer path=${file.absolutePath} byteCount=${bytes.size} sha256=$sha256")
    }

    private fun twoRectBuffer(
        outside: Rgba,
        firstOnly: Rgba,
        second: Rgba,
        firstWinsOverlap: Boolean = false,
    ) = buffer { x, y ->
        val inFirst = x in 4 until 20 && y in 4 until 20
        val inSecond = x in 12 until 28 && y in 12 until 28
        when {
            inFirst && inSecond -> if (firstWinsOverlap) firstOnly else second
            inFirst -> firstOnly
            inSecond -> second
            else -> outside
        }
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
