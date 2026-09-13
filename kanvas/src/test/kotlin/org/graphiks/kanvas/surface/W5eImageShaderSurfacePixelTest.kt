@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.SceneRecordingLimitException
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.render.ir.SceneCaptureLimits
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
import org.graphiks.kanvas.surface.WgslFloatEnvelopeV1Oracle.Interval as I

class W5eImageShaderSurfacePixelTest {
    @Test fun tileModesApplyPerTapOnBothAxes() {
        val bytes = byteArrayOf(
            -1, 0, 0, -1, 0, -1, 0, -1,
            0, 0, -1, -1, -1, -1, 0, -1,
            0, -1, -1, -1, -1, 0, -1, -1,
        )
        val image = Image.fromPixels(2, 3, bytes, alphaType = AlphaType.PREMUL)
        for (linear in listOf(false, true)) for (tileX in TileMode.entries) for (tileY in TileMode.entries) {
            val surface = Surface(6, 7)
            val sampling = if (linear) SamplingOptions.LINEAR else SamplingOptions.NEAREST
            // u=s-.5 has fractions .25/.75, so every Linear tile pair crosses both
            // horizontal and vertical tap boundaries rather than exercising one tap.
            val shader = Shader.WithLocalMatrix(Shader.Image(image, tileX, tileY, sampling),
                Matrix3x3F32.translation(1.75f, 2.25f))
            surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 6f, 7f), paint(shader)) }
            val result = surface.render()
            for (yI32 in 0 until 7) for (xI32 in 0 until 6) {
                val expected = W5eDecodedImageCpuOracle.sampledColorPixel(2, 3, bytes, xI32 - 1.25f, yI32 - 1.75f,
                    linear, tileX, tileY)
                require(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { "$sampling/$tileX/$tileY: $expected" }
                WgslFloatEnvelopeV1Oracle.assertAdmits(expected, result.pixels.copyOfRange((yI32 * 6 + xI32) * 4, (yI32 * 6 + xI32 + 1) * 4))
            }
            assertEquals(1, result.stats.opsDispatched, "$sampling/$tileX/$tileY")
            assertEquals(0, result.stats.opsRefused, "$sampling/$tileX/$tileY")
        }
        // Both axes have length one here; REPEAT/MIRROR must still use safe 1/2
        // periods and Decal must preserve its transparent partial Linear taps.
        val singleton = byteArrayOf(-1, 0, 0, -1)
        val singletonImage = Image.fromPixels(1, 1, singleton, alphaType = AlphaType.PREMUL)
        for (linear in listOf(false, true)) for (tileX in TileMode.entries) for (tileY in TileMode.entries) {
            val sampling = if (linear) SamplingOptions.LINEAR else SamplingOptions.NEAREST
            val surface = Surface(1, 1)
            val shader = Shader.WithLocalMatrix(Shader.Image(singletonImage, tileX, tileY, sampling),
                Matrix3x3F32.translation(.75f, .25f))
            surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), paint(shader)) }
            val expected = W5eDecodedImageCpuOracle.sampledColorPixel(1, 1, singleton, -.25f, .25f, linear, tileX, tileY)
            require(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { "$sampling/$tileX/$tileY: $expected" }
            WgslFloatEnvelopeV1Oracle.assertAdmits(expected, surface.render().pixels)
        }
    }

    @Test fun decalLinearDoesNotRenormalizeWeights() {
        val bytes = byteArrayOf(-1, 0, 0, -1)
        val image = Image.fromPixels(1, 1, bytes, alphaType = AlphaType.PREMUL)
        val surface = Surface(1, 1)
        val shader = Shader.WithLocalMatrix(Shader.Image(image, TileMode.DECAL, TileMode.CLAMP, SamplingOptions.LINEAR),
            Matrix3x3F32.translation(.5f, 0f))
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), paint(shader)) }
        val expected = W5eDecodedImageCpuOracle.sampledColorPixel(1, 1, bytes, 0f, .5f, linear = true,
            TileMode.DECAL, TileMode.CLAMP)
        require(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { expected.toString() }
        val result = surface.render()
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, result.pixels)
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test fun imageCaptureByteRefusalRollsBackThenRendersOnTheSameSurface() {
        for (pathI32 in 0..2) {
            val surface = Surface(2, 1, captureLimits = SceneCaptureLimits(maxImageBytesI64 = 8L))
            val oversized = Image.fromPixels(3, 1, ByteArray(12), alphaType = AlphaType.PREMUL)
            val failure = assertThrows<SceneRecordingLimitException> {
                surface.canvas {
                    when (pathI32) {
                        0 -> drawImage(oversized, RectF32.ofLTRB(0f, 0f, 2f, 1f), SamplingOptions.NEAREST)
                        1 -> drawRect(RectF32.ofLTRB(0f, 0f, 2f, 1f), paint(Shader.Image(oversized)))
                        else -> drawPath(Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 2f, 1f)) }, paint(Shader.Image(oversized)))
                    }
                }
            }
            assertEquals("scene-recording-image-bytes-exceeded", failure.diagnostic.code.value)
            // The refused append never entered this Surface's recording. No Clear/reset/discard.
            surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 2f, 1f), paint(Shader.Image(image()))) }
            val result = surface.render()
            assertContentEquals((red + blue).toUByteArray(), result.pixels)
            assertEquals(1, result.stats.opsDispatched)
            assertEquals(0, result.stats.opsRefused)
        }
    }

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
        val oracle = WgslFloatEnvelopeV1Oracle
        val imagePaint = paint(Shader.Image(Image.fromPixels(1, 1, byteArrayOf(-1, 0, 0, -1),
            alphaType = AlphaType.PREMUL))).copy(color = ColorARGB.fromRGBA(0f, 0f, 0f, .5f))
        val destination = oracle.imageSourceAttachment(arrayOf(I.ZERO, I.ZERO, I.ZERO, I.ONE))
        require(destination is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { destination.toString() }
        // One of four samples lies inside the DIFFERENCE rect: .25*255=63.75.
        // The allowed +/-0.6 UNORM conversion error permits only64. Integer DIFFERENCE
        // from255 leaves191, whose texture decode bounds the retained .75 coverage.
        val coverage = oracle.imageUnorm8(191)
        val rawAlpha = oracle.imageUnorm8(255)
        val paintAlpha = I.input(imagePaint.color.alphaNormalized)
        val sourceAlpha = oracle.gradientMultiply(rawAlpha, paintAlpha)
        val straightRed = oracle.gradientDivide(oracle.imageUnorm8(255), rawAlpha)
        val sourceRed = oracle.gradientMultiply(oracle.gradientMultiply(oracle.imageSrgbToLinear(straightRed), rawAlpha), paintAlpha)
        val source = arrayOf(sourceRed, I.ZERO, I.ZERO, sourceAlpha)
        val fullCoverage = oracle.imageSourceAttachment(source)
        require(fullCoverage is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { fullCoverage.toString() }
        val expected = oracle.imageSourceAttachment(Array(4) { channelI32 ->
            val dst = destination.state.linearPremul[channelI32]
            val delta = oracle.gradientSubtract(source[channelI32], dst)
            oracle.gradientHull(oracle.gradientAdd(dst, oracle.gradientMultiply(coverage, delta)),
                oracle.gradientFma(coverage, delta, dst))
        })
        require(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { expected.toString() }
        // Half-alpha SRC over opaque black distinguishes post-blend coverage (alpha~.625)
        // from both SrcOver (alpha1) and source-only multiplication/replacement (alpha~.375).
        val surface = Surface(4, 4)
        surface.canvas {
            drawPath(Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 4f, 4f)) }, Paint(color = ColorARGB.Black, antiAlias = false))
            clipRect(RectF32.ofLTRB(-1f, -1f, .5f, 1.5f), ClipOp.DIFFERENCE, antiAlias = true)
            drawPath(Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 4f, 4f)) }, imagePaint)
        }
        val result = surface.render()
        oracle.assertAdmits(fullCoverage, result.pixels.copyOfRange(20, 24))
        // At pixel(0,1), W4e's established .25/.75 2x2 positions retain three of four samples.
        oracle.assertAdmits(expected, result.pixels.copyOfRange(16, 20))
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

    @Test fun hugeFiniteImageShaderCoordinatesRefuseAndRecoverOnSameRuntime() {
        val surface = Surface(2, 1)
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 2f, 1f), paint(
            Shader.WithLocalMatrix(Shader.Image(image()), Matrix3x3F32.translation(-1e20f, 0f)))) }
        val failure = assertThrows<IllegalStateException> { surface.render() }
        assertEquals("unsupported.material.image.numeric-domain-unbounded", failure.message.orEmpty().substringBefore(':'))
        val healthy = Surface(2, 1)
        healthy.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 2f, 1f), paint(Shader.Image(image()))) }
        assertContentEquals((red + blue).toUByteArray(), healthy.render().pixels)
    }

    @Test fun invalidImageLocalMatricesRefuseAndRecoverOnSameRuntime() {
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
        // Independent source equations: linear(paint.rgb)*paint.a*shaderOpacity*A8.
        val expected = W5eDecodedImageCpuOracle.colorPixel(ColorType.ALPHA_8, AlphaType.PREMUL,
            org.graphiks.kanvas.color.ColorSpace.SRGB, byteArrayOf(-1), paintAlphaF32 = .5f, paintColor = sourcePaint.color)
        require(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { expected.toString() }
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), sourcePaint) }
        val result = surface.render()
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, result.pixels)
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
        val oracle = WgslFloatEnvelopeV1Oracle
        // Two of four clip samples: .5*255=127.5 permits only127/128 under +/-0.6.
        // DIFFERENCE swaps those codes, and the integer fold preserves them.
        val coverage = oracle.gradientHull(oracle.imageUnorm8(127), oracle.imageUnorm8(128))
        val raw = oracle.imageUnorm8(255)
        val sourceRed = oracle.gradientMultiply(oracle.imageSrgbToLinear(oracle.gradientDivide(raw, raw)), raw)
        val expected = oracle.imageSourceAttachment(arrayOf(oracle.gradientMultiply(sourceRed, coverage),
            I.ZERO, I.ZERO, oracle.gradientMultiply(raw, coverage)))
        require(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { expected.toString() }
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
        oracle.assertAdmits(expected, result.pixels.copyOfRange(0, 4))
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
