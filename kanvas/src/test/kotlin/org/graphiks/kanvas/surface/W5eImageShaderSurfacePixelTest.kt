@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class W5eImageShaderSurfacePixelTest {
    @Test fun pureDstImageFrameRetainsNoOpOwnershipWithoutSourceWork() {
        for (pathI32 in 0..2) {
            val surface = Surface(2, 1)
            val sourcePaint = paint(Shader.Image(image())).copy(blendMode = BlendMode.DST)
            surface.canvas {
                when (pathI32) {
                    0 -> drawImage(image(), RectF32.ofLTRB(0f, 0f, 2f, 1f), SamplingOptions.NEAREST, sourcePaint.copy(shader = null))
                    1 -> drawRect(RectF32.ofLTRB(0f, 0f, 2f, 1f), sourcePaint)
                    else -> drawPath(Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 2f, 1f)) }, sourcePaint)
                }
            }
            val result = surface.render()
            assertContentEquals((clear + clear).toUByteArray(), result.pixels)
            assertEquals(1, result.stats.opsDispatched)
            assertEquals(0, result.stats.opsRefused)
        }
    }

    @Test fun dstImagesAndShadersPreserveDestinationAndLogicalOrder() {
        val surface = Surface(4, 2)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 2f), Paint(color = ColorARGB.Green, antiAlias = false))
            drawImage(image(), RectF32.ofLTRB(0f, 0f, 4f, 2f), SamplingOptions.NEAREST,
                Paint(blendMode = BlendMode.DST, antiAlias = false))
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 2f), paint(Shader.Image(image())).copy(blendMode = BlendMode.DST))
            drawPath(Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 4f, 2f)) },
                paint(Shader.Image(image())).copy(blendMode = BlendMode.DST))
            drawRect(RectF32.ofLTRB(3f, 0f, 4f, 2f), paint(Shader.Image(image())))
        }
        val result = surface.render()
        for (yI32 in 0..1) for (xI32 in 0..3)
            pixel(result.pixels, 4, xI32, yI32, if (xI32 == 3) blue else listOf(0u, 255u, 0u, 255u))
        assertEquals(5, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test fun ordinaryGradientAndA8ChildShareFrameResources() {
        fun gradient(left: ColorARGB, right: ColorARGB) = Shader.LinearGradient(Point2F32(0f, 0f),
            Point2F32(4f, 0f), listOf(org.graphiks.kanvas.paint.GradientStop(0f, left),
                org.graphiks.kanvas.paint.GradientStop(.5f, left), org.graphiks.kanvas.paint.GradientStop(.5f, right),
                org.graphiks.kanvas.paint.GradientStop(1f, right)))
        val surface = Surface(4, 2)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 1f), paint(gradient(ColorARGB.Red, ColorARGB.Blue)))
            drawImage(Image.fromPixels(1, 1, byteArrayOf(-1), ColorType.ALPHA_8, alphaType = AlphaType.PREMUL),
                RectF32.ofLTRB(0f, 1f, 4f, 2f), SamplingOptions.NEAREST,
                paint(gradient(ColorARGB.Green, ColorARGB.Yellow)))
        }
        val result = surface.render()
        assertContentEquals((red + red + blue + blue + listOf<UByte>(0u, 255u, 0u, 255u, 0u, 255u, 0u, 255u,
            255u, 255u, 0u, 255u, 255u, 255u, 0u, 255u)).toUByteArray(), result.pixels)
        assertEquals(2, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test fun boundedProjectiveShaderUsesSealedImageCoordinates() {
        // Inverse local projection x/(1+x/8) gives source indices 0,1,1,2.
        val source = Image.fromPixels(3, 1, byteArrayOf(-1, 0, 0, -1, 0, -1, 0, -1, 0, 0, -1, -1),
            alphaType = AlphaType.PREMUL)
        val surface = Surface(4, 1)
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 4f, 1f), paint(Shader.WithLocalMatrix(
            Shader.Image(source), Matrix3x3F32(persp0 = -.125f)))) }
        assertContentEquals((red + listOf<UByte>(0u, 255u, 0u, 255u, 0u, 255u, 0u, 255u) + blue).toUByteArray(),
            surface.render().pixels)
    }

    @Test fun nestedLocalMatricesPreserveNoncommutingOrder() {
        // Outer translation(inner scale(image)): image x=(device x-1)/2, not device x/2-1.
        val surface = Surface(6, 1)
        val shader = Shader.WithLocalMatrix(Shader.WithLocalMatrix(Shader.Image(image()),
            Matrix3x3F32.scaling(2f, 1f)), Matrix3x3F32.translation(1f, 0f))
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 6f, 1f), paint(shader)) }
        assertContentEquals((red + red + red + blue + blue + blue).toUByteArray(), surface.render().pixels)
    }

    @Test fun stencilPathImageShaderPreservesHoleAndLogicalDrawCount() {
        val surface = Surface(4, 4)
        val path = Path().apply {
            fillType = org.graphiks.kanvas.geometry.FillType.EVEN_ODD
            addRect(RectF32.ofLTRB(0f, 0f, 4f, 4f)); addRect(RectF32.ofLTRB(1f, 1f, 3f, 3f))
        }
        surface.canvas { drawPath(path, paint(Shader.Image(image()))) }
        val result = surface.render()
        for (yI32 in 0..3) for (xI32 in 0..3)
            pixel(result.pixels, 4, xI32, yI32, if (xI32 in 1..2 && yI32 in 1..2) clear else if (xI32 == 0) red else blue)
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test fun rrectImageShaderRetainsExistingPublicRefusal() = deferred {
        drawRRect(RRectF32.of(RectF32.ofLTRB(0f, 0f, 4f, 4f), CornerRadiiF32.of(1f)),
            paint(Shader.Image(image())).copy(antiAlias = true))
    }

    @Test fun pathStrokeImageShaderRetainsExistingPublicRefusal() = deferred {
        drawPath(Path().apply { moveTo(0f, 2f); lineTo(4f, 2f) },
            paint(Shader.Image(image())).copy(style = PaintStyle.STROKE, strokeWidth = 2f))
    }

    @Test fun pathHairlineImageShaderRetainsExistingPublicRefusal() = deferred {
        drawPath(Path().apply { moveTo(0f, 2f); lineTo(4f, 2f) },
            paint(Shader.Image(image())).copy(style = PaintStyle.STROKE, strokeWidth = 0f))
    }

    @Test fun pointsImageShaderRetainsExistingPublicRefusal() = deferred("unsupported.material.source_unimplemented") {
        drawPoints(PointMode.POINTS, listOf(Point2F32(2f, 2f)),
            paint(Shader.Image(image())).copy(strokeWidth = 2f))
    }

    @Test fun verticesImageShaderRetainsExistingPublicRefusal() = deferred("unsupported.vertices.material") {
        drawVertices(Vertices(VertexMode.TRIANGLES,
            listOf(Point2F32(0f, 0f), Point2F32(4f, 0f), Point2F32(0f, 4f))), paint(Shader.Image(image())))
    }

    @Test fun rectImageShaderUsesIndependentLocalCoordinates() {
        // CTM translation followed by image-local scaling maps centers to .25,.75,1.25,1.75.
        val surface = Surface(6, 2)
        surface.canvas {
            translate(1f, 0f)
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 2f), paint(Shader.WithLocalMatrix(
                Shader.Image(image()), Matrix3x3F32.scaling(2f, 2f))))
        }
        val result = surface.render()
        for (yI32 in 0..1) for (xI32 in 0..5)
            pixel(result.pixels, 6, xI32, yI32, when (xI32) { 1, 2 -> red; 3, 4 -> blue; else -> clear })
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test fun pathFillImageShaderUsesCoverageAndFinalBlend() {
        // SRC with W4e's analytic clip coverage must interpolate destination after source evaluation.
        val surface = Surface(4, 4)
        surface.canvas {
            drawPath(Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 4f, 4f)) }, Paint(color = ColorARGB.Blue, antiAlias = false))
            clipRect(RectF32.ofLTRB(-1f, -1f, .5f, 5f), ClipOp.DIFFERENCE, antiAlias = true)
            drawPath(Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 4f, 4f)) },
                paint(Shader.Image(Image.fromPixels(1, 1, byteArrayOf(-1, 0, 0, -1), alphaType = AlphaType.PREMUL))))
        }
        val result = surface.render()
        pixel(result.pixels, 4, 1, 1, red)
        // W4e's established 2x2 clip samples are at .25/.75: the first column retains two of four.
        val edge = result.pixels.copyOfRange(16, 20).map(UByte::toInt)
        assertEquals(255, edge[3])
        assertEquals(0, edge[1])
        check(edge[0] in 187..189 && edge[2] in 187..189) { "Expected .5 SRC coverage over blue, got $edge" }
        assertEquals(2, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test fun a8ImageShaderMasksPaintColor() {
        // Evaluating the root shader recursively would never produce the green paint color.
        val mask = Image.fromPixels(2, 1, byteArrayOf(0, -1), ColorType.ALPHA_8, alphaType = AlphaType.PREMUL)
        for (path in listOf(false, true)) {
            val surface = Surface(2, 1)
            val paint = paint(Shader.Image(mask)).copy(color = ColorARGB.Green)
            surface.canvas {
                if (path) drawPath(Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 2f, 1f)) }, paint)
                else drawRect(RectF32.ofLTRB(0f, 0f, 2f, 1f), paint)
            }
            assertContentEquals((clear + listOf<UByte>(0u, 255u, 0u, 255u)).toUByteArray(), surface.render().pixels)
        }
    }

    @Test fun imageShaderPictureRoundTripPreservesLocalMatrix() {
        // Reversing the noncommuting matrices changes the red/blue boundary after replay.
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 6f, 1f))
        canvas.translate(1f, 0f)
        canvas.drawRect(RectF32.ofLTRB(0f, 0f, 4f, 1f), paint(Shader.WithLocalMatrix(
            Shader.Image(image()), Matrix3x3F32.scaling(2f, 1f))))
        val picture = requireNotNull(Picture.fromByteArray(recorder.finishRecordingAsPicture().toByteArray()))
        val surface = Surface(6, 1)
        surface.canvas { picture.playback(this) }
        assertContentEquals((clear + red + red + blue + blue + clear).toUByteArray(), surface.render().pixels)
    }

    @Test fun hugeFiniteImageShaderCoordinatesRefuseAndRecover() {
        val surface = Surface(2, 1)
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 2f, 1f), paint(
            Shader.WithLocalMatrix(Shader.Image(image()), Matrix3x3F32.translation(-1e20f, 0f)))) }
        val failure = assertThrows<IllegalStateException> { surface.render() }
        assertEquals("unsupported.material.image.numeric-domain-unbounded", failure.message.orEmpty().substringBefore(':'))
        val healthy = Surface(2, 1)
        healthy.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 2f, 1f), paint(Shader.Image(image()))) }
        assertContentEquals((red + blue).toUByteArray(), healthy.render().pixels)
    }

    @Test fun invalidImageLocalMatricesRefuseAndRecover() {
        for ((matrixF32, code) in listOf(
            Matrix3x3F32(tx = Float.NaN) to "local-matrix-non-finite",
            Matrix3x3F32.scaling(0f, 1f) to "local-matrix-singular",
            Matrix3x3F32.scaling(Float.MIN_VALUE, 1f) to "local-matrix-unrepresentable",
            Matrix3x3F32(persp0 = .5f) to "numeric-domain-unbounded",
        )) {
            val surface = Surface(4, 1)
            surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 4f, 1f),
                paint(Shader.WithLocalMatrix(Shader.Image(image()), matrixF32))) }
            val failure = assertThrows<IllegalStateException> { surface.render() }
            assertEquals("unsupported.material.image.$code", failure.message.orEmpty().substringBefore(':'))
            val healthy = Surface(2, 1)
            healthy.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 2f, 1f), paint(Shader.Image(image()))) }
            assertContentEquals((red + blue).toUByteArray(), healthy.render().pixels)
        }
    }

    @Test fun a8ShaderOpacityAndPaintAlphaApplyExactlyOnce() {
        val mask = Image.fromPixels(1, 1, byteArrayOf(-1), ColorType.ALPHA_8, alphaType = AlphaType.PREMUL)
        val surface = Surface(1, 1)
        val sourcePaint = paint(Shader.Opacity(Shader.Image(mask), .5f)).copy(color = ColorARGB.fromRGBA(0f, 1f, 0f, .5f))
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), sourcePaint) }
        val result = surface.render()
        assertEquals(0, result.pixels[0].toInt())
        assertEquals(0, result.pixels[2].toInt())
        check(result.pixels[1].toInt() in 136..138 && result.pixels[3].toInt() in 63..64) {
            "Expected quarter-alpha green, got ${result.pixels.toList()}"
        }
    }

    @Test fun fractionalDirectImageUsesExistingRectCoverage() {
        val surface = Surface(4, 2)
        surface.canvas { drawImage(image(), RectF32.ofLTRB(.25f, 0f, 3.75f, 2f), SamplingOptions.NEAREST,
            paint(Shader.SolidColor(ColorARGB.White)).copy(shader = null, antiAlias = true)) }
        val result = surface.render()
        pixel(result.pixels, 4, 1, 0, red)
        pixel(result.pixels, 4, 2, 0, blue)
        assertEquals(191, result.pixels[3].toInt())
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test fun affineDirectImagePreservesImageCoordinates() {
        // A quarter turn sends source x to device y. Bounding-box stretching swaps these rows.
        val surface = Surface(2, 4)
        surface.canvas {
            concat(Matrix3x3F32(sx = 0f, kx = -1f, tx = 2f, ky = 1f, sy = 0f))
            drawImage(image(), RectF32.ofLTRB(0f, 0f, 4f, 2f), SamplingOptions.NEAREST,
                Paint(antiAlias = false, blendMode = BlendMode.SRC))
        }
        val result = surface.render()
        for (yI32 in 0..3) for (xI32 in 0..1) pixel(result.pixels, 2, xI32, yI32, if (yI32 < 2) red else blue)
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test fun directImageUsesComplexClipCoverageAndFinalBlend() {
        // A typed W4e clip producer precedes the image shading packet and must never sample the image.
        val surface = Surface(4, 2)
        surface.canvas {
            clipRect(RectF32.ofLTRB(-1f, -1f, .5f, 3f), ClipOp.DIFFERENCE, antiAlias = true)
            drawImage(image(), RectF32.ofLTRB(0f, 0f, 4f, 2f), SamplingOptions.NEAREST,
                Paint(antiAlias = false, blendMode = BlendMode.SRC))
        }
        val result = surface.render()
        pixel(result.pixels, 4, 1, 0, red)
        pixel(result.pixels, 4, 2, 0, blue)
        check(result.pixels[3].toInt() in 127..128)
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test fun projectiveDirectImageUsesBoundedInverseCoordinates() {
        // x'=x/(1+x/16), so device centers .5,1.5,2.5 correspond to source x <1,<1,>1.
        val surface = Surface(4, 2)
        surface.canvas {
            concat(Matrix3x3F32(persp0 = .0625f))
            drawImage(image(), RectF32.ofLTRB(0f, 0f, 4f, 4f), SamplingOptions.NEAREST,
                Paint(antiAlias = false, blendMode = BlendMode.SRC))
        }
        val result = surface.render()
        pixel(result.pixels, 4, 0, 0, red)
        pixel(result.pixels, 4, 1, 0, red)
        pixel(result.pixels, 4, 2, 0, blue)
        pixel(result.pixels, 4, 3, 0, clear)
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    private fun image() = Image.fromPixels(2, 1, byteArrayOf(-1, 0, 0, -1, 0, 0, -1, -1), alphaType = AlphaType.PREMUL)
    private fun deferred(code: String = "unsupported.material.w5a.kind", draw: Canvas.() -> Unit) {
        // Promoting this deferred origin would replace its exact prepared-material refusal.
        val surface = Surface(4, 4)
        surface.canvas(draw)
        val failure = assertThrows<IllegalStateException> { surface.render() }
        assertEquals(code, failure.message.orEmpty().substringBefore(':'))
    }
    private fun paint(shader: Shader) = Paint(color = ColorARGB.White, shader = shader, antiAlias = false, blendMode = BlendMode.SRC)
    private fun pixel(pixels: UByteArray, widthI32: Int, xI32: Int, yI32: Int, expected: List<UByte>) {
        val offsetI32 = (yI32 * widthI32 + xI32) * 4
        assertEquals(expected, pixels.copyOfRange(offsetI32, offsetI32 + 4).toList(), "pixel ($xI32,$yI32)")
    }
    private val red = listOf<UByte>(255u, 0u, 0u, 255u)
    private val blue = listOf<UByte>(0u, 0u, 255u, 255u)
    private val clear = listOf<UByte>(0u, 0u, 0u, 0u)
}
