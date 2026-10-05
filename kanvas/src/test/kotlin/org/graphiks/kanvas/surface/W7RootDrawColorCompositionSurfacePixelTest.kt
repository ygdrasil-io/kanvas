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
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** Public 32×32 witnesses for ordered root composition that includes DrawColor. */
class W7RootDrawColorCompositionSurfacePixelTest {
    companion object {
        private const val SIZE = 32
        private const val BYTE_COUNT = SIZE * SIZE * 4
        private val FULL = RectF32.ofLTRB(0f, 0f, SIZE.toFloat(), SIZE.toFloat())

        @AfterAll
        @JvmStatic
        fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun drawColorUsesCapturedClipDespiteFiniteCtm() {
        // DrawColor should fill its already-captured device clip; a later finite CTM is irrelevant.
        val clippedExpected = buffer { x, y ->
            if (x in 4 until 24 && y in 6 until 22) Rgba(255, 0, 0) else Rgba(0, 0, 255)
        }
        val rotatedExpected = solidBuffer(Rgba(255, 0, 0))
        val transformedExpected = solidBuffer(Rgba(0, 255, 0))

        val clipped = Surface(SIZE, SIZE)
        clipped.canvas {
            drawColor(ColorARGB.Blue)
            save()
            clipRect(RectF32.ofLTRB(4f, 6f, 24f, 22f), antiAlias = false)
            translate(7f, 3f)
            scale(2f, 2f)
            drawColor(ColorARGB.Red)
            restore()
        }
        renderAndAssert(clipped, "ctm-captured-clip", clippedExpected)

        val rotated = Surface(SIZE, SIZE, config = RenderConfig(compositionDomain = CompositionDomain.LINEAR))
        rotated.canvas { rotate(90f); drawColor(ColorARGB.Red) }
        renderAndAssert(rotated, "ctm-linear-rotation", rotatedExpected)

        val transformed = Surface(SIZE, SIZE)
        transformed.canvas { translate(5f, 9f); scale(3f, 2f); drawColor(ColorARGB.Green) }
        renderAndAssert(transformed, "ctm-no-clip-finite", transformedExpected)
    }

    @Test
    fun encodedDrawColorUsesCapturedClipAfterIntegralTransformRoundTrip() {
        // Integer SetTransform operations round-trip to identity before DrawColor admission.
        val expected = buffer { x, y ->
            if (x in 4 until 24 && y in 6 until 22) Rgba(255, 0, 0) else Rgba(0, 0, 255)
        }

        val surface = Surface(SIZE, SIZE, config = RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
        surface.canvas {
            drawColor(ColorARGB.Blue)
            save()
            clipRect(RectF32.ofLTRB(4f, 6f, 24f, 22f), antiAlias = false)
            translate(4f, 6f)
            translate(-4f, -6f)
            drawColor(ColorARGB.Red)
            restore()
        }
        renderAndAssert(surface, "encoded-ctm-integral-captured-clip", expected)
    }

    @Test
    fun partialAlphaDrawColorUsesSingleSrcOver() {
        val linearExpected = solidBuffer(Rgba(0, 188, 187))
        val encodedExpected = solidBuffer(Rgba(0, 128, 127))

        val linear = Surface(SIZE, SIZE, config = RenderConfig(compositionDomain = CompositionDomain.LINEAR))
        linear.canvas { drawColor(ColorARGB.Blue); drawColor(ColorARGB.of(128, 0, 255, 0)) }
        renderAndAssert(linear, "partial-alpha-linear", linearExpected)

        val encoded = Surface(SIZE, SIZE, config = RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
        encoded.canvas { drawColor(ColorARGB.Blue); drawColor(ColorARGB.of(128, 0, 255, 0)) }
        renderAndAssert(encoded, "partial-alpha-encoded-identity", encodedExpected)
    }

    @Test
    fun rootRingDrawColorKeepsRecordedOrder() {
        val colorBeforeGradientExpected = rootRingBuffer(Rgba(0, 0, 255), Rgba(255, 0, 0))
        val colorBetweenExpected = rootRingBuffer(Rgba(0, 255, 0), Rgba(255, 0, 0))
        val colorAfterExpected = solidBuffer(Rgba(0, 255, 0))
        val partialBetweenExpected = rootRingBuffer(Rgba(0, 188, 187), Rgba(255, 0, 0))

        val colorBeforeGradient = Surface(SIZE, SIZE)
        colorBeforeGradient.canvas {
            drawColor(ColorARGB.Green)
            drawRect(FULL, constantBlueGradient())
            drawRect(RING, redRing())
        }
        renderAndAssert(colorBeforeGradient, "root-ring-color-before-gradient", colorBeforeGradientExpected)

        val colorBetween = Surface(SIZE, SIZE)
        colorBetween.canvas {
            drawRect(FULL, constantBlueGradient())
            drawColor(ColorARGB.Green)
            drawRect(RING, redRing())
        }
        renderAndAssert(colorBetween, "root-ring-color-between", colorBetweenExpected)

        val colorAfter = Surface(SIZE, SIZE)
        colorAfter.canvas {
            drawRect(FULL, constantBlueGradient())
            drawRect(RING, redRing())
            drawColor(ColorARGB.Green)
        }
        renderAndAssert(colorAfter, "root-ring-color-after", colorAfterExpected)

        val partialBetween = Surface(SIZE, SIZE)
        partialBetween.canvas {
            drawRect(FULL, constantBlueGradient())
            drawColor(ColorARGB.of(128, 0, 255, 0))
            drawRect(RING, redRing())
        }
        renderAndAssert(partialBetween, "root-ring-partial-between", partialBetweenExpected)
    }

    @Test
    fun integerFilteredRectComposesWithDrawColor() {
        val expected = buffer { x, y ->
            if (x in 8 until 24 && y in 8 until 24) Rgba(255, 0, 0) else Rgba(0, 0, 255)
        }
        val identity = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))

        val surface = Surface(SIZE, SIZE)
        surface.canvas {
            drawColor(ColorARGB.Blue)
            drawRect(RectF32.ofLTRB(8f, 8f, 24f, 24f), Paint(
                color = ColorARGB.Red,
                colorFilter = identity,
                antiAlias = false,
            ))
        }
        renderAndAssert(surface, "integer-filtered-rect", expected)
    }

    @Test
    fun hardHairlineClipComposesWithDrawColor() {
        val expected = buffer { x, y ->
            val onRectHairline = x in 8..23 && y in 8..23 && (x !in 9..22 || y !in 9..22)
            if (onRectHairline && x < 16) Rgba(255, 0, 0) else Rgba(0, 0, 255)
        }
        val fullPath = rectPath(0f, 0f, 32f, 32f)
        val rightHalfPath = rectPath(16f, 0f, 32f, 32f)

        val surface = Surface(SIZE, SIZE)
        surface.canvas {
            drawColor(ColorARGB.Blue)
            save()
            clipPath(fullPath, ClipOp.INTERSECT, antiAlias = false)
            clipPath(rightHalfPath, ClipOp.DIFFERENCE, antiAlias = false)
            drawRect(RectF32.ofLTRB(8.5f, 8.5f, 23.5f, 23.5f), Paint(
                color = ColorARGB.Red,
                style = PaintStyle.STROKE,
                strokeWidth = 0f,
                antiAlias = false,
            ))
            restore()
        }
        renderAndAssert(surface, "hard-hairline-path-clip", expected)
    }

    @Test
    fun hardDarkenPathsComposeWithDrawColor() {
        val expected = buffer { x, y ->
            val first = x in 4 until 20 && y in 4 until 20
            val second = x in 12 until 28 && y in 12 until 28
            when {
                first && second -> Rgba(0, 0, 0)
                first -> Rgba(255, 0, 0)
                second -> Rgba(0, 255, 0)
                else -> Rgba(255, 255, 255)
            }
        }
        val redPath = rectPath(4f, 4f, 20f, 20f)
        val greenPath = rectPath(12f, 12f, 28f, 28f)

        val surface = Surface(SIZE, SIZE)
        surface.canvas {
            drawColor(ColorARGB.White)
            drawPath(redPath, Paint(color = ColorARGB.Red, blendMode = BlendMode.DARKEN, antiAlias = false))
            drawPath(greenPath, Paint(color = ColorARGB.Green, blendMode = BlendMode.DARKEN, antiAlias = false))
        }
        renderAndAssert(surface, "hard-darken-paths", expected)
    }

    @Test
    fun invalidStrokeSiblingRefusesAndRecovers() {
        val recoveryExpected = solidBuffer(Rgba(0, 0, 255))
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
        val sentinelBefore = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(FULL, sentinel) }
        println("w7.root-drawcolor.refusal diagnostic=${failure.message}")
        assertTrue(failure.message?.startsWith("unsupported.stroke.rect_anti_alias:") == true, failure.message)
        assertContentEquals(sentinelBefore, sentinel, "refusal must leave the entire caller buffer unchanged")

        surface.discardRecordedOperations()
        surface.canvas { drawColor(ColorARGB.Blue) }
        renderAndAssert(surface, "invalid-stroke-recovery", recoveryExpected)
    }

    private fun constantBlueGradient() = Paint(
        shader = Shader.LinearGradient(
            Point2F32(0f, 0f),
            Point2F32(SIZE.toFloat(), 0f),
            listOf(GradientStop(0f, ColorARGB.Blue), GradientStop(1f, ColorARGB.Blue)),
        ),
        antiAlias = false,
    )

    private fun redRing() = Paint(
        color = ColorARGB.Red,
        style = PaintStyle.STROKE,
        strokeWidth = 1f,
        antiAlias = true,
    )

    private fun rectPath(left: Float, top: Float, right: Float, bottom: Float) =
        Path().apply { addRect(RectF32.ofLTRB(left, top, right, bottom)) }

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
        println("w7.root-drawcolor.native $evidence")
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
        val file = File(directory, "w7-root-drawcolor-$label.rgba")
        check(!file.exists()) { "Refusing to overwrite W7 pixel evidence: $file" }
        val bytes = ByteArray(result.pixels.size) { index -> result.pixels[index].toByte() }
        file.outputStream().use { output -> output.write(bytes) }
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        println("w7.root-drawcolor.actual-buffer path=${file.absolutePath} byteCount=${bytes.size} sha256=$sha256")
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

    private fun rootRingBuffer(fill: Rgba, ring: Rgba) = buffer { x, y ->
        if (x in 2..5 && y in 2..5 && (x !in 3..4 || y !in 3..4)) ring else fill
    }

    private data class Rgba(val red: Int, val green: Int, val blue: Int, val alpha: Int = 255)

    private val RING = RectF32.ofLTRB(2.5f, 2.5f, 5.5f, 5.5f)
}
