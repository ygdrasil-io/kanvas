@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.types.Mesh
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/**
 * Small public covering of the W4/W5 lanes that are applicable inside a W6a scope.  Every
 * expected byte is written before the Surface is created; no expectation derives from a plan,
 * renderer, fallback route, or internal counter.
 */
class W6aLayerW4W5SurfacePixelTest {
    @Test
    fun `affine general path retains its native geometry in a translated target`() {
        val expected = rgba(0, 0, 0, 0) + rgba(17, 61, 211) + rgba(17, 61, 211) + rgba(17, 61, 211) + rgba(0, 0, 0, 0)
        val shape = Path().apply {
            moveTo(-2f, -2f); quadTo(2f, -5f, 7f, -2f)
            lineTo(7f, 3f); lineTo(-2f, 3f); close()
        }
        for (layered in listOf(false, true)) {
            val surface = Surface(5, 1)
            surface.canvas {
                clipRect(RectF32.ofLTRB(1f, 0f, 4f, 1f), antiAlias = false)
                if (layered) saveLayer()
                skew(.2f, 0f)
                drawPath(shape, opaque(BLUE))
                if (layered) restore()
            }
            assertContentEquals(expected, surface.render().pixels, "layered=$layered")
        }
    }

    @Test
    fun `ordered rounded and path clips preserve gradient coordinates in a translated target`() {
        // The public gradient's default interpolation is encoded SRGB, not linear light.
        fun encoded(value: Double): Int = kotlin.math.floor(255.0 * value + .5).toInt()
        val expected = rgba(0, 0, 0, 0) + rgba(encoded(.875), 0, encoded(.125)) + rgba(0, 0, 0, 0) +
            rgba(encoded(.375), 0, encoded(.625)) + rgba(encoded(.125), 0, encoded(.875)) + rgba(0, 0, 0, 0)
        val gradient = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(4f, 0f),
            listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)))
        val hole = Path().apply { moveTo(1f, 0f); lineTo(2f, 0f); lineTo(2f, 1f); lineTo(1f, 1f); close() }
        for (layered in listOf(false, true)) {
            val surface = Surface(6, 1)
            surface.canvas {
                clipRect(RectF32.ofLTRB(1f, 0f, 5f, 1f), antiAlias = false)
                if (layered) saveLayer()
                translate(1f, 0f)
                clipRRect(RRectF32.of(RectF32.ofLTRB(0f, 0f, 4f, 1f), CornerRadiiF32.of(.25f)), antiAlias = false)
                clipPath(hole, org.graphiks.kanvas.pipeline.ClipOp.DIFFERENCE, antiAlias = false)
                drawPath(Path().apply { moveTo(0f, 0f); lineTo(4f, 0f); lineTo(4f, 1f); lineTo(0f, 1f); close() },
                    Paint(shader = gradient, antiAlias = false))
                if (layered) restore()
            }
            assertContentEquals(expected, surface.render().pixels, "layered=$layered")
        }
    }

    @Test
    fun `fractional frozen path and path clip rebase at a nonzero layer origin`() {
        val expected = rgba(0, 0, 0, 0) + rgba(17, 61, 211) + rgba(17, 61, 211) +
            rgba(17, 61, 211) + rgba(0, 0, 0, 0) + rgba(0, 0, 0, 0)
        val path = Path().apply {
            moveTo(.1f, 0f); lineTo(3.1f, 0f); lineTo(3.1f, 1f); lineTo(.1f, 1f); close()
        }
        for (layered in listOf(false, true)) {
            val surface = Surface(6, 1)
            surface.canvas {
                clipRect(RectF32.ofLTRB(1f, 0f, 5f, 1f), antiAlias = false)
                if (layered) saveLayer()
                translate(1f, 0f)
                clipPath(path, antiAlias = false)
                drawPath(path, opaque(BLUE))
                if (layered) restore()
            }
            assertContentEquals(expected, surface.render().pixels, "layered=$layered")
        }
    }

    @Test
    fun `fractional frozen RRect rebases at a nonzero layer origin`() {
        val expected = rgba(0, 0, 0, 0) + rgba(228, 48, 69, 229) + rgba(239, 51, 73)
        for (layered in listOf(false, true)) {
            val surface = Surface(3, 1)
            surface.canvas {
                if (layered) saveLayer(RectF32.ofLTRB(1f, 0f, 3f, 1f))
                drawRRect(RRectF32.of(RectF32.ofLTRB(1.1f, -1f, 4.1f, 2f), CornerRadiiF32.of(.25f)),
                    opaque(RED).copy(antiAlias = true))
                if (layered) restore()
            }
            assertContentEquals(expected, surface.render().pixels, "layered=$layered")
        }
    }

    @Test
    fun `fractional frozen Point and Vertices rebase at a nonzero layer origin`() {
        val expected = rgba(0, 0, 0, 0) + rgba(43, 181, 93) + rgba(239, 51, 73)
        val triangle = Vertices(VertexMode.TRIANGLES,
            listOf(Point2F32(2f, -1f), Point2F32(3f, -1f), Point2F32(2f, 2f)))
        val surface = Surface(3, 1)
        surface.canvas {
            saveLayer(RectF32.ofLTRB(1f, 0f, 3f, 1f))
            drawPoint(1.1f, .5f, opaque(GREEN).copy(strokeWidth = 0f))
            save()
            translate(.1f, 0f)
            drawVertices(triangle, opaque(RED))
            restore()
            restore()
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `destination only child lanes preserve the surrounding source`() {
        val expected = rgba(17, 61, 211)
        val vertices = Vertices(VertexMode.TRIANGLES, listOf(Point2F32(0f, 0f), Point2F32(3f, 0f), Point2F32(0f, 3f)))
        for (layered in listOf(false, true)) {
            val surface = Surface(1, 1)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), opaque(BLUE))
                if (layered) saveLayer()
                drawPoint(.5f, .5f, Paint(ColorARGB.Red, strokeWidth = 1f, blendMode = BlendMode.DST, antiAlias = false))
                drawVertices(vertices, BlendMode.SRC_OVER, Paint(ColorARGB.Red, blendMode = BlendMode.DST, antiAlias = false))
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, blendMode = BlendMode.DST, antiAlias = false))
                if (layered) restore()
            }
            assertContentEquals(expected, surface.render().pixels, "layered=$layered")
        }
    }

    @Test
    fun `general curved path retains its geometry authority at a nonzero layer origin`() {
        val expected = rgba(0, 0, 0, 0) + rgba(17, 61, 211) + rgba(17, 61, 211) + rgba(17, 61, 211) + rgba(0, 0, 0, 0)
        val shape = Path().apply {
            moveTo(-2f, -2f); quadTo(2f, -5f, 7f, -2f)
            lineTo(7f, 3f); lineTo(-2f, 3f); close()
        }
        for (layered in listOf(false, true)) {
            val surface = Surface(5, 1)
            surface.canvas {
                if (layered) saveLayer(RectF32.ofLTRB(1f, 0f, 4f, 1f))
                clipRect(RectF32.ofLTRB(1f, 0f, 4f, 1f), antiAlias = false)
                drawPath(shape, opaque(BLUE))
                if (layered) restore()
            }
            assertContentEquals(expected, surface.render().pixels, "layered=$layered")
        }
    }

    @Test
    fun `curved path and hard rounded clip retain their W4 passes inside a translated layer`() {
        val expected = rgba(0, 0, 0, 0) + rgba(17, 61, 211) + rgba(17, 61, 211) + rgba(17, 61, 211) + rgba(0, 0, 0, 0)
        val shape = Path().apply {
            moveTo(-2f, -2f); quadTo(2f, -5f, 7f, -2f)
            lineTo(7f, 3f); lineTo(-2f, 3f); close()
        }
        for (layered in listOf(false, true)) {
            val surface = Surface(5, 1)
            surface.canvas {
                if (layered) saveLayer(RectF32.ofLTRB(1f, 0f, 4f, 1f))
                clipRRect(RRectF32.of(RectF32.ofLTRB(1f, 0f, 4f, 1f), CornerRadiiF32.of(.25f)), antiAlias = false)
                drawPath(shape, opaque(BLUE))
                if (layered) restore()
            }
            assertContentEquals(expected, surface.render().pixels, "layered=$layered")
        }
    }

    @Test
    fun `child destination reads use the layer version and not the root or previous sibling`() {
        val expected = rgba(0, 255, 0) + rgba(255, 255, 0) + rgba(255, 0, 255) + rgba(0, 255, 0)
        val triangle = Vertices(VertexMode.TRIANGLES,
            listOf(Point2F32(-1f, -1f), Point2F32(6f, -1f), Point2F32(-1f, 6f)))
        for (layered in listOf(false, true)) {
            val surface = Surface(4, 1)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 4f, 1f), opaque(ColorARGB.Green))
                if (layered) saveLayer(RectF32.ofLTRB(1f, 0f, 3f, 1f))
                drawRect(RectF32.ofLTRB(1f, 0f, 3f, 1f), opaque(ColorARGB.Blue))
                drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), opaque(ColorARGB.White).copy(blendMode = BlendMode.DIFFERENCE))
                pixelClip(2) { drawVertices(triangle, opaque(ColorARGB.Red).copy(blendMode = BlendMode.DIFFERENCE)) }
                if (layered) restore()
            }
            assertContentEquals(expected, surface.render().pixels, "layered=$layered")
        }
    }

    @Test
    fun `vertices list strip fan and color coordinates share sealed geometry bindings`() {
        val expected = rgba(0, 0, 0, 0) + rgba(255, 0, 0) + rgba(0, 0, 255) + rgba(0, 255, 0) + rgba(0, 0, 0, 0)
        val triangle = listOf(Point2F32(-1f, -1f), Point2F32(7f, -1f), Point2F32(-1f, 7f))
        val cases = listOf(
            Vertices(VertexMode.TRIANGLES, triangle) to ColorARGB.Red,
            Vertices(VertexMode.TRIANGLE_STRIP, triangle, indices = listOf(0, 1, 2)) to ColorARGB.Blue,
            Vertices(VertexMode.TRIANGLE_FAN, triangle, colors = List(3) { ColorARGB.Green },
                texCoords = List(3) { Point2F32(.5f, .5f) }) to ColorARGB.White,
        )
        for (layered in listOf(false, true)) {
            val surface = Surface(5, 1)
            surface.canvas {
                if (layered) saveLayer(RectF32.ofLTRB(1f, 0f, 4f, 1f))
                cases.forEachIndexed { index, (vertices, color) -> pixelClip(index + 1) {
                    drawVertices(vertices, opaque(color))
                } }
                if (layered) restore()
            }
            assertContentEquals(expected, surface.render().pixels, "layered=$layered")
        }
    }

    @Test
    fun `points preserve translated device squares and sibling source order`() {
        val expected = rgba(0, 0, 0, 0) + rgba(239, 51, 73) + rgba(17, 61, 211) + rgba(17, 61, 211) + rgba(43, 181, 93)
        for (layered in listOf(false, true)) {
            val surface = Surface(5, 1)
            surface.canvas {
                if (layered) saveLayer(RectF32.ofLTRB(1f, 0f, 5f, 1f))
                translate(1f, 0f)
                drawPoint(.5f, .5f, opaque(RED).copy(strokeWidth = 0f))
                drawPoints(PointMode.POINTS, listOf(Point2F32(1.5f, .5f), Point2F32(2.5f, .5f)), opaque(BLUE).copy(strokeWidth = 0f))
                drawPoint(3.5f, .5f, opaque(GREEN).copy(strokeWidth = 1f))
                if (layered) restore()
            }
            assertContentEquals(expected, surface.render().pixels, "layered=$layered")
        }
    }

    @Test
    fun `direct image preserves decoded texels through a nonzero layer origin`() {
        val expected = rgba(0, 0, 0, 0) + rgba(239, 51, 73) + rgba(17, 61, 211) + rgba(0, 0, 0, 0)
        val image = Image.fromPixels(2, 1, byteArrayOf(-17, 51, 73, -1, 17, 61, -45, -1), alphaType = AlphaType.UNPREMUL)
        for (layered in listOf(false, true)) {
            val surface = Surface(4, 1)
            surface.canvas {
                if (layered) saveLayer(RectF32.ofLTRB(1f, 0f, 3f, 1f))
                drawImage(image, RectF32.ofLTRB(1f, 0f, 3f, 1f), SamplingOptions.NEAREST, opaque(GREEN))
                if (layered) restore()
            }
            assertContentEquals(expected, surface.render().pixels, "layered=$layered")
        }
    }

    @Test
    fun `path fill stroke and hairline retain stencil through a translated layer`() {
        val expected = rgba(0, 0, 0, 0) + rgba(17, 61, 211) + rgba(43, 181, 93) + rgba(239, 51, 73)
        val surface = Surface(4, 1)
        surface.canvas {
            saveLayer(RectF32.ofLTRB(1f, 0f, 4f, 1f))
            pixelClip(1) { drawPath(Path().apply { addRect(RectF32.ofLTRB(1f, 0f, 2f, 1f)) }, opaque(BLUE)) }
            pixelClip(2) { drawPath(Path().apply { moveTo(1f, .5f); lineTo(4f, .5f) },
                opaque(GREEN).copy(style = PaintStyle.STROKE, strokeWidth = 2f)) }
            pixelClip(3) { drawPath(Path().apply { moveTo(2f, .5f); lineTo(5f, .5f) },
                opaque(RED).copy(style = PaintStyle.STROKE, strokeWidth = 0f)) }
            restore()
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `w4 rrect path stroke hairline and points render through one layer authority`() {
        val red = rgba(239, 51, 73)
        val blue = rgba(17, 61, 211)
        val green = rgba(43, 181, 93)
        val expected = red + blue + green + red + blue + green
        val draws: List<Canvas.() -> Unit> = listOf(
            { pixelClip(0) {
                drawRRect(RRectF32.of(RectF32.ofLTRB(-1f, -1f, 2f, 2f), CornerRadiiF32.of(.25f)),
                    opaque(RED).copy(antiAlias = true))
            } },
            { pixelClip(1) { drawPath(Path().apply { addRect(RectF32.ofLTRB(1f, 0f, 2f, 1f)) }, opaque(BLUE)) } },
            { pixelClip(2) {
                drawPath(Path().apply { moveTo(1f, .5f); lineTo(4f, .5f) },
                    opaque(GREEN).copy(style = PaintStyle.STROKE, strokeWidth = 2f))
            } },
            { pixelClip(3) {
                drawPath(Path().apply { moveTo(2f, .5f); lineTo(5f, .5f) },
                    opaque(RED).copy(style = PaintStyle.STROKE, strokeWidth = 0f))
            } },
            { pixelClip(4) { drawPoint(4.5f, .5f, opaque(BLUE).copy(strokeWidth = 0f)) } },
            { pixelClip(5) { drawPoints(PointMode.POINTS, listOf(Point2F32(5.5f, .5f)), opaque(GREEN).copy(strokeWidth = 0f)) } },
        )
        // Each promoted lane has a direct control. The pre-existing mixed prepared route
        // does not admit this Path/Point combination, and is not the feature under test.
        val controls = draws.indices.map { pixel -> UByteArray(expected.size) { channel ->
            if (channel / 4 == pixel) expected[channel] else 0u
        } }
        draws.forEachIndexed { index, draw ->
            val surface = Surface(6, 1)
            surface.canvas(draw)
            assertContentEquals(controls[index], surface.render().pixels, "direct lane=$index")
        }
        val surface = Surface(6, 1)
        surface.canvas {
            saveLayer()
            draws.forEach { it() }
            restore()
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `vertices mesh direct image and image shader render through one layer authority`() {
        val red = rgba(239, 51, 73)
        val blue = rgba(17, 61, 211)
        // A direct image's paint RGB does not tint its decoded texels.
        val expected = red + blue + blue + blue
        val triangle = Vertices(VertexMode.TRIANGLES,
            listOf(Point2F32(-1f, -1f), Point2F32(6f, -1f), Point2F32(-1f, 6f)), indices = listOf(0, 1, 2))
        val image = Image.fromPixels(1, 1, byteArrayOf(17, 61, -45, -1), alphaType = AlphaType.UNPREMUL)
        val draw: Canvas.() -> Unit = {
            pixelClip(0) { drawVertices(triangle, opaque(RED)) }
            pixelClip(1) { drawMesh(Mesh(triangle, bounds = RectF32.ofLTRB(-1f, -1f, 6f, 6f)), opaque(BLUE), BlendMode.SRC_OVER) }
            pixelClip(2) { drawImage(image, RectF32.ofLTRB(2f, 0f, 3f, 1f), SamplingOptions.NEAREST, opaque(GREEN)) }
            pixelClip(3) {
                drawRect(RectF32.ofLTRB(3f, 0f, 4f, 1f),
                    opaque(BLUE).copy(shader = Shader.Image(image, sampling = SamplingOptions.NEAREST)))
            }
        }
        for (layered in listOf(false, true)) {
            val surface = Surface(4, 1)
            surface.canvas {
                if (layered) saveLayer()
                draw()
                if (layered) restore()
            }
            assertContentEquals(expected, surface.render().pixels, "layered=$layered")
        }
    }

    @Test
    fun `gradient color filter noise composed blend and registered runtime material retain W5 ownership`() {
        val runtime = Shader.RuntimeEffect(
            assertNotNull(RuntimeEffect.registered("kanvas.runtime.child-opacity", 1)),
            UniformBlock { float1("alpha", .5f) }, mapOf("child" to Shader.SolidColor(ColorARGB.Blue)),
        )
        val shaders = listOf(
            Shader.LinearGradient(Point2F32(1f, 0f), Point2F32(3f, 0f),
                listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue))),
            Shader.Opacity(Shader.SolidColor(ColorARGB.Green), .25f),
            Shader.WithColorFilter(Shader.SolidColor(ColorARGB.Red), ColorFilter.Blend(ColorARGB.Blue.withAlpha(96), BlendMode.SRC_OVER)),
            Shader.PerlinNoise(.125f, .25f, 2, 7, null),
            Shader.Blend(BlendMode.SRC_OVER, Shader.SolidColor(ColorARGB.Green.withAlpha(128)), Shader.SolidColor(ColorARGB.Blue.withAlpha(96))),
            runtime,
        )
        // The registered child-opacity equation is evaluated independently as multiplication;
        // the CPU oracle never executes the registered implementation or reads a Surface.
        val oracleShaders = shaders.dropLast(1) + Shader.Opacity(Shader.SolidColor(ColorARGB.Blue), .5f)
        val expected = oracleShaders.mapIndexed { index, shader -> W5fColorCpuOracle.expectedShaderTree(shader,
            finalBlend = BlendMode.SRC, devicePointF32 = Point2F32(index + 1.5f, .5f))
            .also(W5fSurfacePixelFixtures::requireBounded) }
        for (layered in listOf(false, true)) {
            val surface = Surface(8, 1)
            surface.canvas {
                if (layered) saveLayer(RectF32.ofLTRB(1f, 0f, 7f, 1f))
                shaders.forEachIndexed { index, shader ->
                    drawRect(RectF32.ofLTRB(index + 1f, 0f, index + 2f, 1f), opaque(RED).copy(shader = shader, blendMode = BlendMode.SRC))
                }
                if (layered) restore()
            }
            val pixels = surface.render().pixels
            assertContentEquals(rgba(0, 0, 0, 0), pixels.copyOfRange(0, 4))
            assertContentEquals(rgba(0, 0, 0, 0), pixels.copyOfRange(28, 32))
            expected.forEachIndexed { index, value -> WgslFloatEnvelopeV1Oracle.assertAdmits(value,
                pixels.copyOfRange((index + 1) * 4, (index + 2) * 4)) }
        }
    }

    @Test
    fun `composed point source retains the shared W5 material authority`() {
        val shader = Shader.Blend(BlendMode.SRC_OVER, Shader.SolidColor(ColorARGB.Red.withAlpha(128)),
            Shader.SolidColor(ColorARGB.Blue.withAlpha(96)))
        val expected = W5fColorCpuOracle.expectedShaderTree(shader, finalBlend = BlendMode.SRC)
            .also(W5fSurfacePixelFixtures::requireBounded)
        val admittedShader = Shader.RuntimeEffect(assertNotNull(RuntimeEffect.registered("kanvas.runtime.child-opacity", 1)),
            UniformBlock { float1("alpha", 1f) }, mapOf("child" to shader))
        for (layered in listOf(false, true)) {
            val surface = Surface(1, 1)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), opaque(ColorARGB.Transparent))
                if (layered) saveLayer()
                drawPoint(.5f, .5f, opaque(RED).copy(shader = admittedShader, strokeWidth = 0f, blendMode = BlendMode.SRC))
                if (layered) restore()
            }
            val rendered = runCatching { surface.render() }.getOrElse { throw AssertionError("layered=$layered", it) }
            WgslFloatEnvelopeV1Oracle.assertAdmits(expected, rendered.pixels)
        }
    }

    @Test
    fun `nested mixed lanes preserve parent child ordering`() {
        val expected = rgba(239, 51, 73) + rgba(17, 61, 211)
        val surface = Surface(2, 1)
        surface.canvas {
            saveLayer()
            drawRect(RectF32.ofLTRB(0f, 0f, 2f, 1f), opaque(RED))
            saveLayer()
            drawRRect(RRectF32.of(RectF32.ofLTRB(1f, -1f, 3f, 2f), CornerRadiiF32.of(.25f)), opaque(BLUE).copy(antiAlias = true))
            restore()
            restore()
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    private fun opaque(color: ColorARGB): Paint = Paint(color = color, antiAlias = false)
    private fun Canvas.pixelClip(xI32: Int, draw: Canvas.() -> Unit) {
        save()
        clipRect(RectF32.ofLTRB(xI32.toFloat(), 0f, (xI32 + 1).toFloat(), 1f), antiAlias = false)
        draw()
        restore()
    }
    private fun rgba(red: Int, green: Int, blue: Int, alpha: Int = 255): UByteArray =
        ubyteArrayOf(red.toUByte(), green.toUByte(), blue.toUByte(), alpha.toUByte())

    private companion object {
        val RED: ColorARGB = ColorARGB.of(255, 239, 51, 73)
        val BLUE: ColorARGB = ColorARGB.of(255, 17, 61, 211)
        val GREEN: ColorARGB = ColorARGB.of(255, 43, 181, 93)
    }
}
