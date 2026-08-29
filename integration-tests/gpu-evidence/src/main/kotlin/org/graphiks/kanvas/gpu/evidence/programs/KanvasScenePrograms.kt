package org.graphiks.kanvas.gpu.evidence.programs

import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageRouteCapability
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.matrix.Matrix3x3F32

/** Rendered evidence scenes expressed solely through the public Kanvas Canvas API. */
object KanvasScenePrograms {
    private const val ROUTE_ID = "kanvas.surface.render"

    fun solidCardStack() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawRect(RectF32.ofLTRB(8f, 10f, 56f, 34f), Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)))
        drawRect(RectF32.ofLTRB(14f, 38f, 50f, 54f), Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)))
    })

    /** One immutable known-pixel RGBA8 image under the only native sampler: nearest/clamp. */
    fun boundedRgba8NearestBitmap() = KanvasSurfaceProgram(
        ROUTE_ID,
        renderConfig = RenderConfig(
            preparedImageRouteCapability = GPUPreparedImageRouteCapability.BoundedNearest1To1,
        ),
        record = {
        drawColor(BACKGROUND)
        drawImage(
            Image.fromPixels(
                width = 3,
                height = 2,
                pixels = byteArrayOf(
                    17, 34, 51, 255.toByte(), 221.toByte(), 204.toByte(), 187.toByte(), 255.toByte(), 119, 136.toByte(), 153.toByte(), 255.toByte(),
                    68, 85, 102, 255.toByte(), 16, 32, 48, 255.toByte(), 170.toByte(), 187.toByte(), 204.toByte(), 255.toByte(),
                ),
                sourceId = "gpu-evidence.bounded-rgba8-nearest-bitmap",
            ),
            RectF32.ofLTRB(12f, 16f, 15f, 18f),
            SamplingOptions.NEAREST,
        )
    })

    /** Filtering is deliberately terminal before the image can reach native submission. */
    fun boundedBitmapLinearRefusal() = KanvasSurfaceProgram(
        ROUTE_ID,
        renderConfig = RenderConfig(
            preparedImageRouteCapability = GPUPreparedImageRouteCapability.BoundedNearest1To1,
        ),
        record = {
        drawImage(
            Image.fromPixels(1, 1, byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte()), sourceId = "gpu-evidence.bounded-bitmap-linear-refusal"),
            RectF32.ofLTRB(12f, 16f, 13f, 17f),
            SamplingOptions.LINEAR,
        )
    })

    /**
     * A real public image-filter invocation, deliberately kept refusal-only
     * until the prepared Surface product can own the blur intermediates.
     */
    fun imageFilterBlurRefusal() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawImage(
            Image.fromPixels(
                width = 9,
                height = 9,
                pixels = ByteArray(9 * 9 * 4).also { pixels ->
                    val center = (4 * 9 + 4) * 4
                    pixels[center] = 255.toByte()
                    pixels[center + 3] = 255.toByte()
                },
                sourceId = "gpu-evidence.image-filter-blur-refusal",
            ),
            RectF32.ofLTRB(24f, 24f, 33f, 33f),
            Paint(imageFilter = ImageFilter.Blur(2f, 2f, TileMode.CLAMP)),
        )
    })

    /**
     * Complex path clips are still terminal for round-cap path strokes. Keep
     * this public Surface shape as an explicit refusal until the native
     * stencil-cover route can own the clip/stroke composition.
     */
    fun roundCapStrokeUnderWindingRefusal() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipPath(
            Path {
                moveTo(3.25f, 3.25f)
                lineTo(28.75f, 3.25f)
                lineTo(3.25f, 28.75f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(6f, 16f); lineTo(26f, 16f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(antiAlias = false, strokeCap = StrokeCap.ROUND),
        )
        restore()
    })

    /** Non-right-angle path-stroke rotation remains outside the admitted clip route. */
    fun rotatedDiagonalStrokeUnderWindingRefusal() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipPath(
            Path {
                moveTo(7.25f, 6.25f)
                lineTo(30.25f, 6.25f)
                lineTo(7.25f, 29.25f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        rotate(45f, 16f, 16f)
        drawPath(
            Path { moveTo(8.25f, 8.25f); lineTo(20.25f, 14.25f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    /** Rotating a radial shader's local matrix is not admitted by the native mapping route. */
    fun rotatedRadialStrokeLocalMatrixRefusal() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipPath(
            Path {
                moveTo(7.25f, 6.25f)
                lineTo(30.25f, 6.25f)
                lineTo(7.25f, 29.25f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.WithLocalMatrix(
                    Shader.RadialGradient(
                        Point2F32(16f, 16f),
                        16f,
                        listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                        TileMode.CLAMP,
                    ),
                    Matrix3x3F32.rotation(90f),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    /** Three-stop sweep gradients remain outside the bounded native stroke mapping. */
    fun threeStopSweepStrokeUnderWindingRefusal() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipPath(
            Path {
                moveTo(7.25f, 6.25f)
                lineTo(30.25f, 6.25f)
                lineTo(7.25f, 29.25f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.SweepGradient(
                    Point2F32(16f, 16f),
                    0f,
                    360f,
                    listOf(
                        GradientStop(0f, ColorARGB.Red),
                        GradientStop(.5f, ColorARGB.Green),
                        GradientStop(1f, ColorARGB.Blue),
                    ),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    /** Round-cap material/stencil composition is still refused after route selection. */
    fun linearGradientRoundStrokeUnderWindingRefusal() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipPath(
            Path {
                moveTo(7.25f, 6.25f)
                lineTo(30.25f, 6.25f)
                lineTo(7.25f, 29.25f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(6f, 16f); lineTo(26f, 16f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.LinearGradient(
                    Point2F32(0f, 0f),
                    Point2F32(32f, 0f),
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                    TileMode.CLAMP,
                ),
                antiAlias = false,
                strokeCap = StrokeCap.ROUND,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    /** Three-stop radial gradients remain outside the bounded native stroke mapping. */
    fun threeStopRadialStrokeUnderWindingRefusal() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipPath(
            Path {
                moveTo(7.25f, 6.25f)
                lineTo(30.25f, 6.25f)
                lineTo(7.25f, 29.25f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.RadialGradient(
                    Point2F32(16f, 16f),
                    16f,
                    listOf(
                        GradientStop(0f, ColorARGB.Red),
                        GradientStop(.5f, ColorARGB.Green),
                        GradientStop(1f, ColorARGB.Blue),
                    ),
                    TileMode.CLAMP,
                ),
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    /** A non-right-angle transform keeps sweep path-stroke composition refused. */
    fun nonRightAngleSweepStrokeRefusal() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipPath(
            Path {
                moveTo(6.875f, 5.875f)
                lineTo(24.875f, 5.875f)
                lineTo(6.875f, 23.875f)
                close()
            }.apply { fillType = FillType.INVERSE_WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        rotate(15f)
        drawPath(
            Path { moveTo(4.125f, 4.125f); lineTo(12.125f, 8.625f) },
            Paint.stroke(ColorARGB.Transparent, 2f).copy(
                shader = Shader.SweepGradient(
                    Point2F32(16f, 16f),
                    0f,
                    360f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    /** Empty dash arrays have no exact lowering and must stop before native preparation. */
    fun emptyDashStrokeRefusal() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawPath(
            Path { moveTo(4f, 8f); lineTo(24f, 8f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                pathEffect = PathEffect.Dash(floatArrayOf()),
            ),
        )
    })

    fun basicPrimitivesValidAlpha() = KanvasSurfaceProgram(ROUTE_ID, record = {
        clear(ColorARGB.Transparent)
        drawColor(ColorARGB.fromRGBA(13f / 255f, 20f / 255f, 33f / 255f, .5f))
        drawRect(
            RectF32.ofLTRB(8f, 12f, 56f, 52f),
            Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f, .5f)).copy(antiAlias = false),
        )
    })

    fun basicPrimitivesOutOfBounds() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawRRect(
            RRectF32.of(RectF32.ofLTRB(-32f, -32f, -8f, -8f), radius = 4f),
            Paint.fill(ColorARGB.Red).copy(antiAlias = false),
        )
        drawDRRect(
            RRectF32.of(RectF32.ofLTRB(72f, 72f, 104f, 104f), radius = 6f),
            RRectF32.of(RectF32.ofLTRB(80f, 80f, 96f, 96f), radius = 3f),
            Paint.fill(ColorARGB.Red).copy(antiAlias = false),
        )
        drawRect(RectF32.ofLTRB(-8f, -8f, 4f, 4f), Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false))
    })

    fun basicPrimitivesEmptyRectRefusal() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(RectF32.ofLTRB(12f, 12f, 12f, 32f), Paint.fill(ColorARGB.Red).copy(antiAlias = false))
    })

    fun basicPrimitivesPoints() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawPoints(
            PointMode.POINTS,
            listOf(Point2F32(10f, 12f), Point2F32(30f, 32f), Point2F32(62f, 62f), Point2F32(-6f, -6f)),
            Paint.stroke(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f), 4f).copy(antiAlias = false),
        )
    })

    /** Two opaque scalar-AA rectangles deliberately overlap after an integer scissor clip. */
    fun fractionalAaRectOverlap() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        // The right edge cuts the blue rectangle. This is intentionally not a
        // decorative clip: pixel (45, 30) is blue while (46, 30) stays background.
        clipRect(RectF32.ofLTRB(8f, 8f, 46f, 56f), antiAlias = false)
        drawRect(
            RectF32.ofLTRB(12.5f, 16.5f, 41.5f, 45.5f),
            Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)),
        )
        drawRect(
            RectF32.ofLTRB(28.5f, 24.5f, 52.5f, 49.5f),
            Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)),
        )
        restore()
    })

    fun separableBlurRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(
            RectF32.ofLTRB(16f, 16f, 48f, 48f),
            Paint(
                color = ColorARGB.fromRGBA(0.18f, 0.42f, 0.76f, 1f),
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 3f),
                antiAlias = false,
            ),
        )
    })

    fun translucentCardOverlap() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawRect(
            RectF32.ofLTRB(8f, 10f, 44f, 42f),
            Paint.fill(ColorARGB.fromRGBA(0.25f, 0.5f, 0.75f, 0.5f)),
        )
        drawRect(
            RectF32.ofLTRB(24f, 22f, 56f, 54f),
            Paint.fill(ColorARGB.fromRGBA(0.5f, 0.25f, 0.125f, 0.5f)),
        )
    })

    fun scissorOverlay() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipRect(RectF32.ofLTRB(16f, 16f, 40f, 40f), antiAlias = false)
        drawRect(RectF32.ofLTRB(8f, 8f, 56f, 56f), Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)))
        restore()
        save()
        clipRect(RectF32.ofLTRB(24f, 24f, 48f, 48f), antiAlias = false)
        drawRect(RectF32.ofLTRB(16f, 16f, 56f, 56f), Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)))
        restore()
    })

    fun canvasStateRestoreToCount() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipRect(RectF32.ofLTRB(8f, 8f, 40f, 40f), antiAlias = false)
        drawRect(RectF32.ofLTRB(4f, 4f, 44f, 44f), Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)))
        save()
        clipRect(RectF32.ofLTRB(16f, 16f, 32f, 32f), antiAlias = false)
        restoreToCount(1)
        drawRect(RectF32.ofLTRB(4f, 8f, 20f, 40f), Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)))
        restore()
        drawRect(RectF32.ofLTRB(44f, 8f, 56f, 20f), Paint.fill(ColorARGB.White))
    })

    /** One integer-bounded RGBA8 saveLayer with two opaque children and a single SrcOver group-opacity restore. */
    fun boundedSaveLayerSrcOverOpacity() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(RectF32.ofLTRB(0f, 0f, 64f, 64f), Paint.fill(BACKGROUND).copy(antiAlias = false))
        saveLayer(
            RectF32.ofLTRB(8f, 8f, 56f, 56f),
            Paint(
                color = ColorARGB.fromRGBA(1f, 1f, 1f, 128f / 255f),
                antiAlias = false,
                blendMode = BlendMode.SRC_OVER,
            ),
        )
        drawRect(
            RectF32.ofLTRB(12f, 12f, 44f, 42f),
            Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false),
        )
        drawRect(
            RectF32.ofLTRB(24f, 22f, 52f, 54f),
            Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false),
        )
        restore()
    })

    /** Refuses a bounded layer restore that would require a destination-dependent blend route. */
    fun boundedSaveLayerRestoreBlendRefusal() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(RectF32.ofLTRB(0f, 0f, 64f, 64f), Paint.fill(BACKGROUND).copy(antiAlias = false))
        saveLayer(
            RectF32.ofLTRB(8f, 8f, 56f, 56f),
            Paint(
                color = ColorARGB.fromRGBA(1f, 1f, 1f, 128f / 255f),
                antiAlias = false,
                blendMode = BlendMode.MULTIPLY,
            ),
        )
        drawRect(
            RectF32.ofLTRB(12f, 12f, 44f, 42f),
            Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false),
        )
        restore()
    })

    fun clipRRectSolid() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipRRect(RRectF32.of(RectF32.ofLTRB(8f, 8f, 56f, 56f), radius = 8f), ClipOp.INTERSECT, antiAlias = false)
        drawRect(RectF32.ofLTRB(0f, 0f, 64f, 64f), Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false))
        restore()
    })

    fun clipRRectEllipse() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        val radii = CornerRadiiF32.of(20f, 12f)
        clipRRect(
            RRectF32.of(RectF32.ofLTRB(12f, 20f, 52f, 44f), radii, radii, radii, radii),
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawRect(RectF32.ofLTRB(0f, 0f, 64f, 64f), Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false))
        restore()
    })

    fun clipRRectTwoBands() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipRRect(RRectF32.of(RectF32.ofLTRB(8f, 8f, 56f, 56f), radius = 8f), ClipOp.INTERSECT, antiAlias = false)
        drawRect(RectF32.ofLTRB(0f, 0f, 64f, 64f), Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false))
        drawRect(RectF32.ofLTRB(32f, 0f, 64f, 64f), Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false))
        restore()
    })

    /** A transformed hard RRect clip is frozen in device space before its rect consumer resets the CTM. */
    fun transformedClipRRectSolid() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        translate(4f, 6f)
        scale(1.5f, .75f)
        clipRRect(
            RRectF32.of(RectF32.ofLTRB(4f, 8f, 36f, 56f), radius = 4f),
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        resetMatrix()
        drawRect(
            RectF32.ofLTRB(0f, 0f, 64f, 64f),
            Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false),
        )
        restore()
    })

    fun clipPathTriangleSolid() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawRect(
            RectF32.ofLTRB(0f, 0f, 64f, 64f),
            Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false),
        )
        restore()
    })

    fun clipPathTriangleDifferenceSolid() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.DIFFERENCE,
            antiAlias = false,
        )
        drawRect(
            RectF32.ofLTRB(0f, 0f, 64f, 64f),
            Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false),
        )
        restore()
    })

    fun clipPathConcaveSolid() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(8f, 8f); lineTo(56f, 8f); lineTo(56f, 24f); lineTo(32f, 24f)
                lineTo(32f, 40f); lineTo(56f, 40f); lineTo(56f, 56f); lineTo(8f, 56f); close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawRect(
            RectF32.ofLTRB(0f, 0f, 64f, 64f),
            Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false),
        )
        restore()
    })

    fun clipPathTriangleTwoBands() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawRect(
            RectF32.ofLTRB(0f, 0f, 64f, 64f),
            Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false),
        )
        drawRect(
            RectF32.ofLTRB(32f, 0f, 64f, 64f),
            Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false),
        )
        restore()
    })

    fun clipPathTranslatedTriangleSolid() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        translate(2f, 0f)
        clipPath(
            Path {
                moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawRect(
            RectF32.ofLTRB(0f, 0f, 64f, 64f),
            Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false),
        )
        restore()
    })

    fun clipPathUniformScaledTriangleSolid() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        translate(8f, 4f)
        scale(0.75f, 0.75f)
        clipPath(
            Path {
                moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawRect(
            RectF32.ofLTRB(0f, 0f, 64f, 64f),
            Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false),
        )
        restore()
    })

    fun clipPathUniformScaledTriangleTwoBands() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        translate(8f, 4f)
        scale(0.75f, 0.75f)
        clipPath(
            Path {
                moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawRect(
            RectF32.ofLTRB(0f, 0f, 64f, 64f),
            Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false),
        )
        drawRect(
            RectF32.ofLTRB(32f, 0f, 64f, 64f),
            Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false),
        )
        restore()
    })

    fun clipPathTriangleDirectTriangleSolid() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                .apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            // Keep each consumer edge away from 1x pixel centers: WebGPU's top-left edge
            // ownership is otherwise intentionally distinct from the inclusive CPU oracle.
            Path { moveTo(4f, 4.25f); lineTo(60f, 12f); lineTo(12f, 60f); close() }
                .apply { fillType = FillType.WINDING },
            Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false),
        )
        restore()
    })

    fun clipPathTranslatedTriangleDirectTriangleSolid() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        translate(2f, 0f)
        clipPath(
            Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                .apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            // The same device-space edge placement as the untranslated direct-triangle case.
            Path { moveTo(4f, 4.25f); lineTo(60f, 12f); lineTo(12f, 60f); close() }
                .apply { fillType = FillType.WINDING },
            Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false),
        )
        restore()
    })

    fun clipPathTriangleDirectTriangleOrder() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                .apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            // Match the non-ambiguous device-space edge placement of the solid cases.
            Path { moveTo(4f, 4.25f); lineTo(60f, 12f); lineTo(12f, 60f); close() }
                .apply { fillType = FillType.WINDING },
            Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false),
        )
        drawPath(
            Path { moveTo(20f, 8f); lineTo(56f, 8f); lineTo(20f, 44f); close() }
                .apply { fillType = FillType.WINDING },
            Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false),
        )
        restore()
    })

    fun clipPathTriangleDirectTriangleLinearGradient() = clipPathDirectTriangleLinearGradient(
        Point2F32(20f, 19.3f),
        Point2F32(20f, 23.3f),
    )

    fun clipPathTranslatedTriangleDirectTriangleLinearGradient() = clipPathDirectTriangleLinearGradient(
        Point2F32(20f, 19.3f),
        Point2F32(20f, 23.3f),
    ) { translate(2f, 0f) }

    fun clipPathUniformScaledTriangleDirectTriangleLinearGradient() = clipPathDirectTriangleLinearGradient(
        Point2F32(20f, 19.066666f),
        Point2F32(20f, 24.4f),
    ) {
        translate(8f, 4f)
        scale(0.75f, 0.75f)
    }

    private fun clipPathDirectTriangleLinearGradient(
        start: Point2F32,
        end: Point2F32,
        transform: org.graphiks.kanvas.canvas.Canvas.() -> Unit = {},
    ) =
        KanvasSurfaceProgram(ROUTE_ID, record = {
            drawColor(BACKGROUND)
            save()
            transform()
            clipPath(
                Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                    .apply { fillType = FillType.WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawPath(
                Path { moveTo(4f, 4.25f); lineTo(60f, 12f); lineTo(12f, 60f); close() }
                    .apply { fillType = FillType.WINDING },
                Paint(
                    shader = Shader.LinearGradient(
                        start,
                        end,
                        listOf(
                            GradientStop(0f, ColorARGB.of(255, 0, 0, 0)),
                            GradientStop(1f, ColorARGB.of(255, 4, 4, 4)),
                        ),
                        TileMode.CLAMP,
                    ),
                    antiAlias = false,
                ),
            )
            restore()
        })

    fun clipPathTriangleLinearGradient() = clipPathLinearGradient()

    fun clipPathTriangleRadialGradient() = clipPathTriangleRadialGradientScene {}

    fun clipPathTriangleRadialGradientStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path { moveTo(7.25f, 6.25f); lineTo(30.25f, 6.25f); lineTo(7.25f, 29.25f); close() }
                .apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.RadialGradient(
                    Point2F32(16f, 16f),
                    16f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                    TileMode.CLAMP,
                ),
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
            ),
        )
        restore()
    })

    fun clipPathTranslatedTriangleRadialGradientStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        translate(2f, 0f)
        clipPath(
            Path { moveTo(7.25f, 6.25f); lineTo(30.25f, 6.25f); lineTo(7.25f, 29.25f); close() }
                .apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.RadialGradient(
                    Point2F32(16f, 16f),
                    16f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                    TileMode.CLAMP,
                ),
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
            ),
        )
        restore()
    })

    fun clipPathLocalRadialMatrixStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path { moveTo(7.25f, 6.25f); lineTo(30.25f, 6.25f); lineTo(7.25f, 29.25f); close() }
                .apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.WithLocalMatrix(
                    Shader.RadialGradient(
                        Point2F32(16f, 16f),
                        16f,
                        listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                        TileMode.CLAMP,
                    ),
                    Matrix3x3F32.translation(1.25f, -0.75f),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
            ),
        )
        restore()
    })

    fun clipPathLocalRadialMatrixSquareStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path { moveTo(7.25f, 6.25f); lineTo(30.25f, 6.25f); lineTo(7.25f, 29.25f); close() }
                .apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.WithLocalMatrix(
                    Shader.RadialGradient(
                        Point2F32(16f, 16f),
                        16f,
                        listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                        TileMode.CLAMP,
                    ),
                    Matrix3x3F32.translation(1.25f, -0.75f),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathRightAngleRadialSquareStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path { moveTo(27.75f, 4.25f); lineTo(27.75f, 27.25f); lineTo(4.75f, 4.25f); close() }
                .apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        rotate(90f, 16f, 16f)
        drawPath(
            Path { moveTo(8.25f, 8.25f); lineTo(20.25f, 14.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.RadialGradient(
                    Point2F32(16f, 16f),
                    16f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                    TileMode.CLAMP,
                ),
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
            ),
        )
        restore()
    })

    fun clipPathSweepSquareStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path { moveTo(7.25f, 6.25f); lineTo(30.25f, 6.25f); lineTo(7.25f, 29.25f); close() }
                .apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.SweepGradient(
                    Point2F32(16f, 16f),
                    0f,
                    360f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
            ),
        )
        restore()
    })

    fun clipPathLocalSweepMatrixStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path { moveTo(7.25f, 6.25f); lineTo(30.25f, 6.25f); lineTo(7.25f, 29.25f); close() }
                .apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.WithLocalMatrix(
                    Shader.SweepGradient(
                        Point2F32(16f, 16f),
                        0f,
                        360f,
                        listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                    ),
                    Matrix3x3F32.translation(1.25f, -0.75f),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
            ),
        )
        restore()
    })

    fun clipPathSweepSquareStrokeEvenOddHole() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {}.apply {
                fillType = FillType.EVEN_ODD
                addRect(RectF32.ofLTRB(3.25f, 3.25f, 28.75f, 28.75f))
                addRect(RectF32.ofLTRB(10.25f, 10.25f, 21.75f, 21.75f))
            },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.SweepGradient(
                    Point2F32(16f, 16f),
                    0f,
                    360f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
            ),
        )
        restore()
    })

    fun clipPathSweepSquareStrokeInverseEvenOddHole() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {}.apply {
                fillType = FillType.INVERSE_EVEN_ODD
                addRect(RectF32.ofLTRB(3.25f, 3.25f, 28.75f, 28.75f))
                addRect(RectF32.ofLTRB(10.25f, 10.25f, 21.75f, 21.75f))
            },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.SweepGradient(
                    Point2F32(16f, 16f),
                    0f,
                    360f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
            ),
        )
        restore()
    })

    fun clipPathSweepSquareStrokeEvenOddDifferenceHole() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {}.apply {
                fillType = FillType.EVEN_ODD
                addRect(RectF32.ofLTRB(3.25f, 3.25f, 28.75f, 28.75f))
                addRect(RectF32.ofLTRB(10.25f, 10.25f, 21.75f, 21.75f))
            },
            ClipOp.DIFFERENCE,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.SweepGradient(
                    Point2F32(16f, 16f),
                    0f,
                    360f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
            ),
        )
        restore()
    })

    fun clipPathSweepSquareStrokeInverseWindingDifference() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(7.25f, 6.25f)
                lineTo(30.25f, 6.25f)
                lineTo(7.25f, 29.25f)
                close()
            }.apply { fillType = FillType.INVERSE_WINDING },
            ClipOp.DIFFERENCE,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.SweepGradient(
                    Point2F32(16f, 16f),
                    0f,
                    360f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
            ),
        )
        restore()
    })

    fun clipPathSweepSquareStrokeWindingDifference() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(7.25f, 6.25f)
                lineTo(30.25f, 6.25f)
                lineTo(7.25f, 29.25f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.DIFFERENCE,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.SweepGradient(
                    Point2F32(16f, 16f),
                    0f,
                    360f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathSweepSquareStrokeScaledTranslatedInverseWinding() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        translate(2f, 1f)
        scale(1.5f, 1.5f)
        clipPath(
            Path {
                moveTo(3.25f, 3.25f)
                lineTo(15.25f, 3.25f)
                lineTo(3.25f, 15.25f)
                close()
            }.apply { fillType = FillType.INVERSE_WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(4.125f, 4.125f); lineTo(12.125f, 8.625f) },
            Paint.stroke(ColorARGB.Transparent, 2f).copy(
                shader = Shader.SweepGradient(
                    Point2F32(16f, 16f),
                    0f,
                    360f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathSweepButtStrokeEvenOddHole() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {}.apply {
                fillType = FillType.EVEN_ODD
                addRect(RectF32.ofLTRB(3.25f, 3.25f, 28.75f, 28.75f))
                addRect(RectF32.ofLTRB(10.25f, 10.25f, 21.75f, 21.75f))
            },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.SweepGradient(
                    Point2F32(16f, 16f),
                    0f,
                    360f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathSweepSquareStrokeScaledTranslatedInverseEvenOddDifferenceHole() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        translate(2f, 1f)
        scale(1.5f, 1.5f)
        clipPath(
            Path {}.apply {
                fillType = FillType.INVERSE_EVEN_ODD
                addRect(RectF32.ofLTRB(3.25f, 3.25f, 15.25f, 15.25f))
                addRect(RectF32.ofLTRB(6.25f, 6.25f, 12.25f, 10.25f))
            },
            ClipOp.DIFFERENCE,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(4.125f, 4.125f); lineTo(12.125f, 8.625f) },
            Paint.stroke(ColorARGB.Transparent, 2f).copy(
                shader = Shader.SweepGradient(
                    Point2F32(16f, 16f),
                    0f,
                    360f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathSweepButtStrokeInverseWinding() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(7.25f, 6.25f)
                lineTo(30.25f, 6.25f)
                lineTo(7.25f, 29.25f)
                close()
            }.apply { fillType = FillType.INVERSE_WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.SweepGradient(
                    Point2F32(16f, 16f),
                    0f,
                    360f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathSweepSquareStrokeRightAngleWinding() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(6.875f, 5.875f)
                lineTo(24.875f, 5.875f)
                lineTo(6.875f, 23.875f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        rotate(90f, 16f, 16f)
        drawPath(
            Path { moveTo(4.125f, 4.125f); lineTo(12.125f, 8.625f) },
            Paint.stroke(ColorARGB.Transparent, 2f).copy(
                shader = Shader.SweepGradient(
                    Point2F32(16f, 16f),
                    0f,
                    360f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathSweepButtStrokeWinding() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(7.25f, 6.25f)
                lineTo(30.25f, 6.25f)
                lineTo(7.25f, 29.25f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.SweepGradient(
                    Point2F32(16f, 16f),
                    0f,
                    360f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathLinearGradientSquareStrokeWinding() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(7.25f, 6.25f)
                lineTo(30.25f, 6.25f)
                lineTo(7.25f, 29.25f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.LinearGradient(
                    Point2F32(0f, 0f),
                    Point2F32(32f, 0f),
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathLinearGradientButtStrokeWinding() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(7.25f, 6.25f)
                lineTo(30.25f, 6.25f)
                lineTo(7.25f, 29.25f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.LinearGradient(
                    Point2F32(0f, 0f),
                    Point2F32(32f, 0f),
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathLinearGradientScaledTranslatedButtStrokeWinding() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        translate(2f, 1f)
        scale(1.5f, 1.5f)
        clipPath(
            Path {
                moveTo(3.25f, 3.25f)
                lineTo(15.25f, 3.25f)
                lineTo(3.25f, 15.25f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(4.125f, 4.125f); lineTo(12.125f, 8.625f) },
            Paint.stroke(ColorARGB.Transparent, 2f).copy(
                shader = Shader.LinearGradient(
                    Point2F32(0f, 0f),
                    Point2F32(32f, 0f),
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathLinearGradientButtStrokeWindingDifference() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(7.25f, 6.25f)
                lineTo(30.25f, 6.25f)
                lineTo(7.25f, 29.25f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.DIFFERENCE,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.LinearGradient(
                    Point2F32(0f, 0f),
                    Point2F32(32f, 0f),
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathLinearGradientButtStrokeEvenOddHole() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {}.apply {
                fillType = FillType.EVEN_ODD
                addRect(RectF32.ofLTRB(3.25f, 3.25f, 28.75f, 28.75f))
                addRect(RectF32.ofLTRB(10.25f, 10.25f, 21.75f, 21.75f))
            },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.LinearGradient(
                    Point2F32(0f, 0f),
                    Point2F32(32f, 0f),
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathLinearGradientButtStrokeInverseWinding() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(7.25f, 6.25f)
                lineTo(30.25f, 6.25f)
                lineTo(7.25f, 29.25f)
                close()
            }.apply { fillType = FillType.INVERSE_WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.LinearGradient(
                    Point2F32(0f, 0f),
                    Point2F32(32f, 0f),
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathLinearGradientRightAngleButtStrokeWinding() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(27.75f, 4.25f)
                lineTo(27.75f, 27.25f)
                lineTo(4.75f, 4.25f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        rotate(90f, 16f, 16f)
        drawPath(
            Path { moveTo(8.25f, 8.25f); lineTo(20.25f, 14.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.LinearGradient(
                    Point2F32(0f, 0f),
                    Point2F32(32f, 0f),
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathLinearGradientRightAngleSquareStrokeWinding() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(27.75f, 4.25f)
                lineTo(27.75f, 27.25f)
                lineTo(4.75f, 4.25f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        rotate(90f, 16f, 16f)
        drawPath(
            Path { moveTo(8.25f, 8.25f); lineTo(20.25f, 14.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.LinearGradient(
                    Point2F32(0f, 0f),
                    Point2F32(32f, 0f),
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathHalfTurnButtStrokeWinding() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(3.25f, 3.25f)
                lineTo(28.75f, 3.25f)
                lineTo(28.75f, 28.75f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        rotate(180f, 16f, 10f)
        drawPath(
            Path { moveTo(8.25f, 8.25f); lineTo(20.25f, 14.25f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathDiagonalSquareStrokeWinding() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(7.25f, 6.25f)
                lineTo(30.25f, 6.25f)
                lineTo(7.25f, 29.25f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathTranslatedRadialSquareStrokeWinding() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        translate(2f, -1f)
        scale(1.25f, 1.25f)
        clipPath(
            Path {
                moveTo(0.2f, 2.6f)
                lineTo(23.4f, 2.6f)
                lineTo(0.2f, 25.8f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.RadialGradient(
                    Point2F32(16f, 16f),
                    16f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                    TileMode.CLAMP,
                ),
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathScaledTranslatedDiagonalButtStrokeWinding() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        translate(2f, 1f)
        scale(1.5f, 1.5f)
        clipPath(
            Path {
                moveTo(3.25f, 3.25f)
                lineTo(15.25f, 3.25f)
                lineTo(3.25f, 15.25f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawPath(
            Path { moveTo(4.125f, 4.125f); lineTo(12.125f, 8.625f) },
            Paint.stroke(ColorARGB.Red, 2f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathRightAngleDiagonalButtStrokeWinding() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(27.75f, 4.25f)
                lineTo(27.75f, 27.25f)
                lineTo(4.75f, 4.25f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        rotate(90f, 16f, 16f)
        drawPath(
            Path { moveTo(8.25f, 8.25f); lineTo(20.25f, 14.25f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathRightAngleDiagonalSquareStrokeWinding() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipPath(
            Path {
                moveTo(27.75f, 4.25f)
                lineTo(27.75f, 27.25f)
                lineTo(4.75f, 4.25f)
                close()
            }.apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        rotate(90f, 16f, 16f)
        drawPath(
            Path { moveTo(8.25f, 8.25f); lineTo(20.25f, 14.25f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun clipPathTranslatedTriangleRadialGradient() = clipPathTriangleRadialGradientScene {
        translate(2f, 0f)
    }

    private fun clipPathTriangleRadialGradientScene(transform: org.graphiks.kanvas.canvas.Canvas.() -> Unit) = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        transform()
        clipPath(
            Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                .apply { fillType = FillType.WINDING },
            ClipOp.INTERSECT,
            antiAlias = false,
        )
        drawRect(
            RectF32.ofLTRB(0f, 0f, 64f, 64f),
            Paint(
                shader = Shader.RadialGradient(
                    Point2F32(24.5f, 24.5f),
                    24f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                    TileMode.CLAMP,
                ),
                antiAlias = false,
            ),
        )
        restore()
    })

    fun clipPathTranslatedTriangleLinearGradient() = clipPathLinearGradient {
        translate(2f, 0f)
    }

    fun clipPathUniformScaledTriangleLinearGradient() = clipPathLinearGradient {
        translate(8f, 4f)
        scale(0.75f, 0.75f)
    }

    private fun clipPathLinearGradient(transform: org.graphiks.kanvas.canvas.Canvas.() -> Unit = {}) =
        KanvasSurfaceProgram(ROUTE_ID, record = {
            drawColor(BACKGROUND)
            save()
            transform()
            clipPath(Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }.apply {
                fillType = FillType.WINDING
            }, ClipOp.INTERSECT, antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 64f, 64f), Paint(shader = Shader.LinearGradient(
                Point2F32(8f, 8f), Point2F32(56f, 8f),
                listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)), TileMode.CLAMP,
            )).copy(antiAlias = false))
            restore()
        })

    fun strokeRectOutline() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawRect(
            RectF32.ofLTRB(16f, 16f, 48f, 48f),
            Paint.stroke(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f), 6f).copy(antiAlias = false),
        )
    })

    fun translatedStrokeRectOutline() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        translate(5f, 7f)
        drawRect(
            RectF32.ofLTRB(16f, 16f, 48f, 48f),
            Paint.stroke(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f), 6f).copy(antiAlias = false),
        )
        restore()
    })

    fun roundCapStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawPath(
            Path {
                moveTo(6f, 16f)
                lineTo(26f, 16f)
            },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.ROUND,
            ),
        )
    })

    fun verticalRoundCapStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawPath(
            Path {
                moveTo(16f, 6f)
                lineTo(16f, 26f)
            },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.ROUND,
            ),
        )
    })

    /** The radius-two round-cap route after an exact quarter-turn rotation. */
    fun quarterTurnRoundCapStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        translate(20f, 4f)
        rotate(90f)
        drawPath(
            Path {
                moveTo(4f, 8f)
                lineTo(16f, 8f)
            },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.ROUND,
            ),
        )
        restore()
    })

    /** The radius-two round-cap route after an exact half-turn rotation. */
    fun halfTurnRoundCapStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        translate(28f, 24f)
        rotate(180f)
        drawPath(
            Path {
                moveTo(8f, 4f)
                lineTo(8f, 16f)
            },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.ROUND,
            ),
        )
        restore()
    })

    /** The radius-two round-cap route after an exact negative quarter-turn rotation. */
    fun negativeQuarterTurnRoundCapStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        translate(12f, 24f)
        rotate(-90f)
        drawPath(
            Path {
                moveTo(4f, 8f)
                lineTo(16f, 8f)
            },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.ROUND,
            ),
        )
        restore()
    })

    /** The vertical radius-two round-cap route under an integral device scissor. */
    fun scissoredVerticalRoundCapStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipRect(RectF32.ofLTRB(14f, 5f, 18f, 22f), antiAlias = false)
        drawPath(
            Path {
                moveTo(16f, 6f)
                lineTo(16f, 26f)
            },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.ROUND,
            ),
        )
        restore()
    })

    /** The vertical radius-two round-cap route with the source contour reversed. */
    fun reverseVerticalRoundCapStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawPath(
            Path {
                moveTo(16f, 26f)
                lineTo(16f, 6f)
            },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.ROUND,
            ),
        )
    })

    /** The horizontal radius-two round-cap route with the source contour reversed. */
    fun reverseHorizontalRoundCapStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawPath(
            Path {
                moveTo(26f, 16f)
                lineTo(6f, 16f)
            },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.ROUND,
            ),
        )
    })

    fun scaledTranslatedHorizontalHairline() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        translate(2f, 3f)
        scale(2f, 2f)
        drawPath(
            Path {
                moveTo(4f, 8f)
                lineTo(14f, 8f)
            },
            Paint.stroke(ColorARGB.Red, 0f).copy(antiAlias = false),
        )
        restore()
    })

    fun scaledHorizontalHairline() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        scale(2f, 2f)
        drawPath(
            Path {
                moveTo(4f, 8f)
                lineTo(14f, 8f)
            },
            Paint.stroke(ColorARGB.Red, 0f).copy(antiAlias = false),
        )
        restore()
    })

    fun horizontalHairline() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawPath(
            Path {
                moveTo(4f, 16f)
                lineTo(28f, 16f)
            },
            Paint.stroke(ColorARGB.Red, 0f).copy(antiAlias = false),
        )
    })

    fun verticalButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawPath(
            Path { moveTo(16f, 4f); lineTo(16f, 28f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
    })

    fun horizontalSquareMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawPath(
            Path { moveTo(8f, 16f); lineTo(24f, 16f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
    })

    fun diagonalButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
    })

    fun diagonalSquareMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawPath(
            Path { moveTo(5.25f, 8.25f); lineTo(21.25f, 20.25f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
    })

    fun scaledButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        scale(2f, 2f)
        drawPath(
            Path { moveTo(4f, 8f); lineTo(14f, 8f) },
            Paint.stroke(ColorARGB.Red, 2f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun scaledTranslatedButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        translate(2f, 3f)
        scale(2f, 2f)
        drawPath(
            Path { moveTo(4f, 8f); lineTo(14f, 8f) },
            Paint.stroke(ColorARGB.Red, 2f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun scaledTranslatedDiagonalButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        translate(2f, 3f)
        scale(2f, 2f)
        drawPath(
            Path { moveTo(4.125f, 4.125f); lineTo(12.125f, 8.625f) },
            Paint.stroke(ColorARGB.Red, 2f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun scaledTranslatedDiagonalSquareMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        translate(2f, 3f)
        scale(2f, 2f)
        drawPath(
            Path { moveTo(4.125f, 4.125f); lineTo(12.125f, 8.625f) },
            Paint.stroke(ColorARGB.Red, 2f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.SQUARE,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun horizontalButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawPath(
            Path { moveTo(4f, 16f); lineTo(28f, 16f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
    })

    /** The bounded production dash route: two 8px on-runs separated by 4px gaps. */
    fun horizontalDashedButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawPath(
            Path { moveTo(4f, 16f); lineTo(28f, 16f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
            ),
        )
    })

    /** The bounded production dash route for a vertical segment. */
    fun verticalDashedButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawPath(
            Path { moveTo(16f, 4f); lineTo(16f, 28f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
            ),
        )
    })

    /** The bounded dash route with the path traversed in the reverse direction. */
    fun reverseHorizontalDashedButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawPath(
            Path { moveTo(28f, 16f); lineTo(4f, 16f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
            ),
        )
    })

    /** The bounded dash route with a vertical path traversed from bottom to top. */
    fun reverseVerticalDashedButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawPath(
            Path { moveTo(16f, 28f); lineTo(16f, 4f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
            ),
        )
    })

    /** The bounded vertical dash route with the second admitted phase. */
    fun phaseShiftedVerticalDashedButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawPath(
            Path { moveTo(16f, 4f); lineTo(16f, 28f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 4f),
            ),
        )
    })

    /** The bounded vertical dash route consumed by an integral device scissor. */
    fun scissoredVerticalDashedButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipRect(RectF32.ofLTRB(14f, 8f, 19f, 20f), antiAlias = false)
        drawPath(
            Path { moveTo(16f, 4f); lineTo(16f, 28f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
            ),
        )
        restore()
    })

    /** The bounded vertical dash route under an integral translation of the CTM. */
    fun translatedVerticalDashedButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        translate(3f, 2f)
        drawPath(
            Path { moveTo(16f, 4f); lineTo(16f, 28f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
            ),
        )
        restore()
    })

    /** The bounded horizontal dash route under a positive uniform scale. */
    fun uniformlyScaledHorizontalDashedButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        scale(2f, 2f)
        drawPath(
            Path { moveTo(4f, 8f); lineTo(14f, 8f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
            ),
        )
        restore()
    })

    /** The bounded vertical dash route under a positive uniform scale. */
    fun uniformlyScaledVerticalDashedButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        scale(2f, 2f)
        drawPath(
            Path { moveTo(8f, 4f); lineTo(8f, 14f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
            ),
        )
        restore()
    })

    /** The bounded horizontal dash route under integral scale and translation. */
    fun uniformlyScaledTranslatedHorizontalDashedButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        translate(2f, 4f)
        scale(2f, 2f)
        drawPath(
            Path { moveTo(4f, 4f); lineTo(14f, 4f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
            ),
        )
        restore()
    })

    /** The bounded vertical dash route under integral scale and translation. */
    fun uniformlyScaledTranslatedVerticalDashedButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        translate(2f, 4f)
        scale(2f, 2f)
        drawPath(
            Path { moveTo(8f, 3f); lineTo(8f, 13f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
            ),
        )
        restore()
    })

    /** The bounded horizontal dash route after an exact quarter-turn rotation. */
    fun quarterTurnHorizontalDashedButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        translate(20f, 4f)
        rotate(90f)
        drawPath(
            Path { moveTo(4f, 8f); lineTo(16f, 8f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
            ),
        )
        restore()
    })

    /** The bounded vertical dash route after an exact half-turn rotation. */
    fun halfTurnVerticalDashedButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        translate(32f, 32f)
        rotate(180f)
        drawPath(
            Path { moveTo(8f, 4f); lineTo(8f, 28f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
            ),
        )
        restore()
    })

    /** The same bounded dash route consumed by an integral device scissor. */
    fun scissoredHorizontalDashedButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipRect(RectF32.ofLTRB(8f, 14f, 20f, 19f), antiAlias = false)
        drawPath(
            Path { moveTo(4f, 16f); lineTo(28f, 16f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
            ),
        )
        restore()
    })

    /** The bounded dash route under an integral translation of the CTM. */
    fun translatedHorizontalDashedButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        translate(3f, 2f)
        drawPath(
            Path { moveTo(4f, 16f); lineTo(28f, 16f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
            ),
        )
        restore()
    })

    /** The bounded [8,4] dash route with the second admitted phase (4px). */
    fun phaseShiftedHorizontalDashedButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawPath(
            Path { moveTo(4f, 16f); lineTo(28f, 16f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 4f),
            ),
        )
    })

    /** The admitted phase-four dash route under the same integral device scissor. */
    fun scissoredPhaseShiftedHorizontalDashedButtMiterStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipRect(RectF32.ofLTRB(8f, 14f, 20f, 19f), antiAlias = false)
        drawPath(
            Path { moveTo(4f, 16f); lineTo(28f, 16f) },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 4f),
            ),
        )
        restore()
    })

    fun scissoredRoundCapStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipRect(RectF32.ofLTRB(5f, 14f, 18f, 19f), antiAlias = false)
        drawPath(
            Path {
                moveTo(6f, 16f)
                lineTo(26f, 16f)
            },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.ROUND,
            ),
        )
        restore()
    })

    fun translatedScissoredRoundCapStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipRect(RectF32.ofLTRB(8f, 16f, 21f, 21f), antiAlias = false)
        translate(3f, 2f)
        drawPath(
            Path {
                moveTo(6f, 16f)
                lineTo(26f, 16f)
            },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.ROUND,
            ),
        )
        restore()
    })

    fun scissoredDiagonalButtStroke() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipRect(RectF32.ofLTRB(8f, 10f, 20f, 19f), antiAlias = false)
        drawPath(
            Path {
                moveTo(5.25f, 8.25f)
                lineTo(21.25f, 20.25f)
            },
            Paint.stroke(ColorARGB.Red, 4f).copy(
                antiAlias = false,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
            ),
        )
        restore()
    })

    fun linearGradientLanes() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(
            RectF32.ofLTRB(8f, 16f, 56f, 48f),
            Paint(
                shader = Shader.LinearGradient(
                    Point2F32(8.5f, 32.5f), Point2F32(55.5f, 32.5f),
                    listOf(GradientStop(0f, ColorARGB.of(255, 255, 56, 56)), GradientStop(1f, ColorARGB.of(255, 56, 112, 255))),
                    TileMode.CLAMP,
                ),
                antiAlias = false,
            ),
        )
    })

    fun radialSwatch() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(
            RectF32.ofLTRB(8f, 8f, 56f, 56f),
            Paint(
                shader = Shader.RadialGradient(
                    Point2F32(32.5f, 32.5f), 23.5f,
                    listOf(GradientStop(0f, ColorARGB.of(255, 255, 232, 72)), GradientStop(1f, ColorARGB.of(255, 48, 80, 192))),
                    TileMode.CLAMP,
                ),
                antiAlias = false,
            ),
        )
    })

    fun radialGradientThreeStops() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(RectF32.ofLTRB(8f, 8f, 56f, 56f), Paint(shader = Shader.RadialGradient(
            Point2F32(32.5f, 32.5f), 23.5f,
            listOf(
                GradientStop(0f, ColorARGB.of(255, 255, 232, 72)),
                GradientStop(.5f, ColorARGB.of(255, 64, 208, 144)),
                GradientStop(1f, ColorARGB.of(255, 48, 80, 192)),
            ),
            TileMode.CLAMP,
        ), antiAlias = false))
    })

    fun sweepDisk() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(
            RectF32.ofLTRB(8f, 8f, 56f, 56f),
            Paint(
                shader = Shader.SweepGradient(
                    Point2F32(32.5f, 32.5f), 0f, 360f,
                    listOf(GradientStop(0f, ColorARGB.of(255, 255, 64, 64)), GradientStop(1f, ColorARGB.of(255, 64, 208, 255))),
                    TileMode.CLAMP,
                ),
                antiAlias = false,
            ),
        )
    })

    fun sweepGradientThreeStops() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(RectF32.ofLTRB(8f, 8f, 56f, 56f), Paint(shader = Shader.SweepGradient(
            Point2F32(32.5f, 32.5f), 0f, 360f,
            listOf(
                GradientStop(0f, ColorARGB.of(255, 255, 64, 64)),
                GradientStop(.5f, ColorARGB.of(255, 56, 220, 120)),
                GradientStop(1f, ColorARGB.of(255, 64, 112, 255)),
            ),
            TileMode.CLAMP,
        ), antiAlias = false))
    })

    fun linearGradientThreeStops() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(RectF32.ofLTRB(8f, 16f, 56f, 48f), Paint(shader = Shader.LinearGradient(
            Point2F32(8.5f, 32.5f), Point2F32(55.5f, 32.5f),
            listOf(GradientStop(0f, ColorARGB.of(255, 255, 56, 56)), GradientStop(0.5f, ColorARGB.of(255, 56, 220, 120)), GradientStop(1f, ColorARGB.of(255, 56, 112, 255))),
            TileMode.CLAMP,
        ), antiAlias = false))
    })

    fun sweepGradientPartialAngle() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(RectF32.ofLTRB(8f, 8f, 56f, 56f), Paint(shader = Shader.SweepGradient(
            Point2F32(32.5f, 32.5f), 45f, 315f,
            listOf(GradientStop(0f, ColorARGB.of(255, 255, 64, 64)), GradientStop(1f, ColorARGB.of(255, 64, 208, 255))), TileMode.CLAMP,
        ), antiAlias = false))
    })

    fun affineSolidRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        concat(Matrix3x3F32(sx = 1f, kx = .25f, tx = 4f, sy = 1f))
        drawRect(RectF32.ofLTRB(8f, 16f, 40f, 48f), Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false))
    })

    /** A device-space non-uniform affine scale clip remains valid after the CTM is reset for its consumer. */
    fun affinePathClipColor() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        setMatrix(Matrix3x3F32(sx = .75f, tx = 2f, sy = .5f, ty = 1f))
        clipPath(Path {
            moveTo(8f, 8f)
            lineTo(56f, 8f)
            lineTo(56f, 56f)
            lineTo(8f, 56f)
            close()
        }, antiAlias = false)
        resetMatrix()
        drawColor(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f))
        restore()
    })

    /** General perspective remains a public Surface refusal; no partial frame may be submitted. */
    fun perspectiveTransformRefusal() = KanvasSurfaceProgram(ROUTE_ID, record = {
        setMatrix(Matrix3x3F32(persp0 = .1f))
        drawRect(
            RectF32.ofLTRB(8f, 8f, 56f, 56f),
            Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false),
        )
    })

    fun scissoredRadialGradient() = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipRect(RectF32.ofLTRB(20f, 12f, 52f, 52f), antiAlias = false)
        drawRect(RectF32.ofLTRB(8f, 8f, 56f, 56f), Paint(shader = Shader.RadialGradient(
            Point2F32(32.5f, 32.5f), 23.5f,
            listOf(GradientStop(0f, ColorARGB.of(255, 255, 232, 72)), GradientStop(1f, ColorARGB.of(255, 48, 80, 192))), TileMode.CLAMP,
        ), antiAlias = false))
        restore()
    })

    fun repeatGradientRefusal() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(RectF32.ofLTRB(0f, 16f, 64f, 48f), Paint(shader = Shader.LinearGradient(
            Point2F32(16.5f, 32.5f), Point2F32(31.5f, 32.5f),
            listOf(GradientStop(0f, ColorARGB.of(255, 255, 56, 56)), GradientStop(1f, ColorARGB.of(255, 56, 112, 255))), TileMode.REPEAT,
        ), antiAlias = false))
    })

    fun mirrorLinearGradientFillRectRefusal() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(RectF32.ofLTRB(0f, 16f, 64f, 48f), Paint(shader = Shader.LinearGradient(
            Point2F32(16.5f, 32.5f), Point2F32(31.5f, 32.5f),
            listOf(GradientStop(0f, ColorARGB.of(255, 255, 56, 56)), GradientStop(1f, ColorARGB.of(255, 56, 112, 255))), TileMode.MIRROR,
        ), antiAlias = false))
    })

    fun gradientStrokeRefusal() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(RectF32.ofLTRB(8f, 16f, 56f, 48f), Paint.stroke(ColorARGB.Transparent, 4f).copy(
            shader = Shader.LinearGradient(
                Point2F32(8.5f, 32.5f), Point2F32(55.5f, 32.5f),
                listOf(GradientStop(0f, ColorARGB.of(255, 255, 56, 56)), GradientStop(1f, ColorARGB.of(255, 56, 112, 255))), TileMode.CLAMP,
            ), antiAlias = false,
        ))
    })

    fun linearGradientThreeStopStrokeRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(RectF32.ofLTRB(8f, 16f, 56f, 48f), Paint.stroke(ColorARGB.Transparent, 4f).copy(
            shader = Shader.LinearGradient(
                Point2F32(8.5f, 32.5f), Point2F32(55.5f, 32.5f),
                listOf(
                    GradientStop(0f, ColorARGB.of(255, 255, 56, 56)),
                    GradientStop(.5f, ColorARGB.of(255, 56, 220, 120)),
                    GradientStop(1f, ColorARGB.of(255, 56, 112, 255)),
                ),
                TileMode.CLAMP,
            ),
            antiAlias = false,
        ))
    })

    fun linearGradientTwoStopTranslatedStrokeRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        translate(2f, 3f)
        drawRect(RectF32.ofLTRB(8f, 16f, 56f, 48f), Paint.stroke(ColorARGB.Transparent, 4f).copy(
            shader = Shader.LinearGradient(
                Point2F32(8.5f, 32.5f), Point2F32(55.5f, 32.5f),
                listOf(
                    GradientStop(0f, ColorARGB.of(255, 255, 56, 56)),
                    GradientStop(1f, ColorARGB.of(255, 56, 112, 255)),
                ),
                TileMode.CLAMP,
            ),
            antiAlias = false,
        ))
    })

    fun linearGradientThreeStopTranslatedStrokeRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        translate(2f, 3f)
        drawRect(RectF32.ofLTRB(8f, 16f, 56f, 48f), Paint.stroke(ColorARGB.Transparent, 4f).copy(
            shader = Shader.LinearGradient(Point2F32(8.5f, 32.5f), Point2F32(55.5f, 32.5f), listOf(
                GradientStop(0f, ColorARGB.of(255, 255, 56, 56)),
                GradientStop(.5f, ColorARGB.of(255, 56, 220, 120)),
                GradientStop(1f, ColorARGB.of(255, 56, 112, 255)),
            ), TileMode.CLAMP), antiAlias = false,
        ))
    })

    fun linearGradientTwoStopUniformScaledStrokeRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        translate(2f, 4f)
        scale(2f, 2f)
        drawRect(RectF32.ofLTRB(8f, 8f, 28f, 24f), Paint.stroke(ColorARGB.Transparent, 2f).copy(
            shader = Shader.LinearGradient(Point2F32(8f, 16f), Point2F32(28f, 16f), listOf(
                GradientStop(0f, ColorARGB.of(255, 255, 56, 56)),
                GradientStop(1f, ColorARGB.of(255, 56, 112, 255)),
            ), TileMode.CLAMP), antiAlias = false,
        ))
    })

    fun linearGradientThreeStopUniformScaledStrokeRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        translate(2f, 4f)
        scale(2f, 2f)
        drawRect(RectF32.ofLTRB(8f, 8f, 28f, 24f), Paint.stroke(ColorARGB.Transparent, 2f).copy(
            shader = Shader.LinearGradient(Point2F32(8f, 16f), Point2F32(28f, 16f), listOf(
                GradientStop(0f, ColorARGB.of(255, 255, 56, 56)),
                GradientStop(.5f, ColorARGB.of(255, 56, 220, 120)),
                GradientStop(1f, ColorARGB.of(255, 56, 112, 255)),
            ), TileMode.CLAMP), antiAlias = false,
        ))
    })

    fun radialGradientTwoStopUniformScaledStrokeRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        translate(2f, 4f)
        scale(2f, 2f)
        drawRect(RectF32.ofLTRB(8f, 8f, 28f, 24f), Paint.stroke(ColorARGB.Transparent, 2f).copy(
            shader = Shader.RadialGradient(
                Point2F32(18f, 14f), 8f, listOf(
                    GradientStop(0f, ColorARGB.of(255, 255, 56, 56)),
                    GradientStop(1f, ColorARGB.of(255, 56, 112, 255)),
                ), TileMode.CLAMP,
            ), antiAlias = false,
        ))
    })

    fun radialGradientThreeStopUniformScaledStrokeRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        translate(2f, 4f)
        scale(2f, 2f)
        drawRect(RectF32.ofLTRB(8f, 8f, 28f, 24f), Paint.stroke(ColorARGB.Transparent, 2f).copy(
            shader = Shader.RadialGradient(
                Point2F32(18f, 14f), 8f, listOf(
                    GradientStop(0f, ColorARGB.of(255, 255, 56, 56)),
                    GradientStop(.5f, ColorARGB.of(255, 56, 220, 120)),
                    GradientStop(1f, ColorARGB.of(255, 56, 112, 255)),
                ), TileMode.CLAMP,
            ), antiAlias = false,
        ))
    })

    fun radialGradientTwoStopStrokeRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(RectF32.ofLTRB(8f, 16f, 56f, 48f), Paint.stroke(ColorARGB.Transparent, 4f).copy(
            shader = Shader.RadialGradient(
                Point2F32(32.5f, 32.5f), 23.5f,
                listOf(
                    GradientStop(0f, ColorARGB.of(255, 255, 56, 56)),
                    GradientStop(1f, ColorARGB.of(255, 56, 112, 255)),
                ),
                TileMode.CLAMP,
            ),
            antiAlias = false,
        ))
    })

    fun radialGradientThreeStopStrokeRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(RectF32.ofLTRB(8f, 16f, 56f, 48f), Paint.stroke(ColorARGB.Transparent, 4f).copy(
            shader = Shader.RadialGradient(
                Point2F32(32.5f, 32.5f), 23.5f,
                listOf(
                    GradientStop(0f, ColorARGB.of(255, 255, 56, 56)),
                    GradientStop(.5f, ColorARGB.of(255, 56, 220, 120)),
                    GradientStop(1f, ColorARGB.of(255, 56, 112, 255)),
                ),
                TileMode.CLAMP,
            ),
            antiAlias = false,
        ))
    })

    fun sweepGradientTwoStopStrokeRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(RectF32.ofLTRB(8f, 16f, 56f, 48f), Paint.stroke(ColorARGB.Transparent, 4f).copy(
            shader = Shader.SweepGradient(
                Point2F32(32.5f, 32.5f), 0f, 360f,
                listOf(
                    GradientStop(0f, ColorARGB.of(255, 255, 56, 56)),
                    GradientStop(1f, ColorARGB.of(255, 56, 112, 255)),
                ),
                TileMode.CLAMP,
            ),
            antiAlias = false,
        ))
    })

    fun sweepGradientTwoStopUniformScaledStrokeRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        translate(2f, 4f)
        scale(2f, 2f)
        drawRect(RectF32.ofLTRB(8f, 8f, 28f, 24f), Paint.stroke(ColorARGB.Transparent, 2f).copy(
            shader = Shader.SweepGradient(
                Point2F32(18f, 14f), 0f, 360f,
                listOf(
                    GradientStop(0f, ColorARGB.of(255, 255, 56, 56)),
                    GradientStop(1f, ColorARGB.of(255, 56, 112, 255)),
                ),
                TileMode.CLAMP,
            ),
            antiAlias = false,
        ))
    })

    fun sweepGradientThreeStopUniformScaledStrokeRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        translate(2f, 4f)
        scale(2f, 2f)
        drawRect(RectF32.ofLTRB(8f, 8f, 28f, 24f), Paint.stroke(ColorARGB.Transparent, 2f).copy(
            shader = Shader.SweepGradient(
                Point2F32(18f, 14f), 0f, 360f, listOf(
                    GradientStop(0f, ColorARGB.of(255, 255, 56, 56)),
                    GradientStop(.5f, ColorARGB.of(255, 56, 220, 120)),
                    GradientStop(1f, ColorARGB.of(255, 56, 112, 255)),
                ), TileMode.CLAMP,
            ), antiAlias = false,
        ))
    })

    fun sweepGradientThreeStopStrokeRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(RectF32.ofLTRB(8f, 16f, 56f, 48f), Paint.stroke(ColorARGB.Transparent, 4f).copy(
            shader = Shader.SweepGradient(
                Point2F32(32.5f, 32.5f), 0f, 360f,
                listOf(
                    GradientStop(0f, ColorARGB.of(255, 255, 56, 56)),
                    GradientStop(.5f, ColorARGB.of(255, 56, 220, 120)),
                    GradientStop(1f, ColorARGB.of(255, 56, 112, 255)),
                ),
                TileMode.CLAMP,
            ),
            antiAlias = false,
        ))
    })

    fun scaledSolidRRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        scale(2f, 1f)
        drawRRect(
            RRectF32.of(RectF32.ofLTRB(8f, 16f, 24f, 48f), radius = 4f),
            Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false),
        )
    })

    fun solidDRRectHole() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawDRRect(
            RRectF32.of(RectF32.ofLTRB(8f, 8f, 56f, 56f), radius = 8f),
            RRectF32.of(RectF32.ofLTRB(20f, 20f, 44f, 44f), radius = 4f),
            Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false),
        )
    })

    fun asymmetricSolidRRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawRRect(
            RRectF32.of(
                RectF32.ofLTRB(8f, 8f, 56f, 56f),
                topLeft = CornerRadiiF32.of(4f, 8f),
                topRight = CornerRadiiF32.of(10f, 4f),
                bottomRight = CornerRadiiF32.of(8f, 12f),
                bottomLeft = CornerRadiiF32.of(6f, 3f),
            ),
            Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false),
        )
    })

    fun ellipseSolidRRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        val ellipseRadius = CornerRadiiF32.of(20f, 12f)
        drawRRect(
            RRectF32.of(
                RectF32.ofLTRB(12f, 20f, 52f, 44f),
                ellipseRadius, ellipseRadius, ellipseRadius, ellipseRadius,
            ),
            Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false),
        )
    })

    fun asymmetricSolidDRRectHole() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawDRRect(
            RRectF32.of(
                RectF32.ofLTRB(6f, 8f, 58f, 56f),
                CornerRadiiF32.of(4f, 8f),
                CornerRadiiF32.of(10f, 4f),
                CornerRadiiF32.of(8f, 12f),
                CornerRadiiF32.of(6f, 3f),
            ),
            RRectF32.of(
                RectF32.ofLTRB(20f, 20f, 44f, 44f),
                CornerRadiiF32.of(2f, 4f),
                CornerRadiiF32.of(6f, 2f),
                CornerRadiiF32.of(4f, 6f),
                CornerRadiiF32.of(3f, 2f),
            ),
            Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false),
        )
    })

    fun clipPathSolidRRect() = clipPathRRect(
        RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), radius = 10f),
        ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f),
    )

    fun clipPathAsymmetricSolidRRect() = clipPathRRect(
        RRectF32.of(
            RectF32.ofLTRB(8f, 8f, 52f, 48f),
            CornerRadiiF32.of(4f, 8f), CornerRadiiF32.of(10f, 4f),
            CornerRadiiF32.of(8f, 12f), CornerRadiiF32.of(6f, 3f),
        ),
        ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f),
    )

    fun clipPathEllipseSolidRRect() = clipPathRRect(
        RRectF32.of(
            RectF32.ofLTRB(12f, 20f, 52f, 44f),
            CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f),
            CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f),
        ),
        ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f),
    )

    fun clipPathTranslatedSolidRRect() = clipPathTranslatedRRect(
        RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), radius = 10f),
        ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f),
    )

    fun clipPathTranslatedAsymmetricSolidRRect() = clipPathTranslatedRRect(
        RRectF32.of(
            RectF32.ofLTRB(8f, 8f, 52f, 48f),
            CornerRadiiF32.of(4f, 8f), CornerRadiiF32.of(10f, 4f),
            CornerRadiiF32.of(8f, 12f), CornerRadiiF32.of(6f, 3f),
        ),
        ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f),
    )

    fun clipPathTranslatedEllipseSolidRRect() = clipPathTranslatedRRect(
        RRectF32.of(
            RectF32.ofLTRB(12f, 20f, 52f, 44f),
            CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f),
            CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f),
        ),
        ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f),
    )

    fun clipPathAxisXTranslatedSolidRRect() = clipPathTranslatedRRect(
        RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), radius = 10f),
        ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f), 4f, 0f,
    )

    fun clipPathAxisYTranslatedAsymmetricSolidRRect() = clipPathTranslatedRRect(
        RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), CornerRadiiF32.of(4f, 8f), CornerRadiiF32.of(10f, 4f), CornerRadiiF32.of(8f, 12f), CornerRadiiF32.of(6f, 3f)),
        ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f), 0f, 5f,
    )

    fun clipPathNegativeXTranslatedEllipseSolidRRect() = clipPathTranslatedRRect(
        RRectF32.of(RectF32.ofLTRB(12f, 20f, 52f, 44f), CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f)),
        ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f), -4f, 5f,
    )

    fun clipPathNegativeYTranslatedSolidRRect() = clipPathTranslatedRRect(
        RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), radius = 10f),
        ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f), 4f, -5f,
    )

    fun clipPathInverseAxisXTranslatedSolidRRect() = clipPathInverseTranslatedRRect(
        RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), radius = 10f),
        ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f), 4f, 0f,
    )

    fun clipPathInverseAxisYTranslatedAsymmetricSolidRRect() = clipPathInverseTranslatedRRect(
        RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), CornerRadiiF32.of(4f, 8f), CornerRadiiF32.of(10f, 4f), CornerRadiiF32.of(8f, 12f), CornerRadiiF32.of(6f, 3f)),
        ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f), 0f, 5f,
    )

    fun clipPathInverseNegativeXTranslatedEllipseSolidRRect() = clipPathInverseTranslatedRRect(
        RRectF32.of(RectF32.ofLTRB(12f, 20f, 52f, 44f), CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f)),
        ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f), -4f, 5f,
    )

    fun clipPathInverseNegativeYTranslatedSolidRRect() = clipPathInverseTranslatedRRect(
        RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), radius = 10f),
        ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f), 4f, -5f,
    )

    fun clipPathSolidDRRect() = clipPathDRRect(
        RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), radius = 10f),
        RRectF32.of(RectF32.ofLTRB(22f, 20f, 40f, 38f), radius = 4f),
        ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f),
    )

    fun clipPathAsymmetricSolidDRRect() = clipPathDRRect(
        RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), CornerRadiiF32.of(4f, 8f), CornerRadiiF32.of(10f, 4f), CornerRadiiF32.of(8f, 12f), CornerRadiiF32.of(6f, 3f)),
        RRectF32.of(RectF32.ofLTRB(20f, 18f, 42f, 39f), CornerRadiiF32.of(3f, 5f), CornerRadiiF32.of(6f, 2f), CornerRadiiF32.of(4f, 7f), CornerRadiiF32.of(2f, 3f)),
        ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f),
    )

    fun clipPathEllipseSolidDRRect() = clipPathDRRect(
        RRectF32.of(RectF32.ofLTRB(12f, 20f, 52f, 44f), CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f)),
        RRectF32.of(RectF32.ofLTRB(24f, 26f, 40f, 38f), CornerRadiiF32.of(8f, 6f), CornerRadiiF32.of(8f, 6f), CornerRadiiF32.of(8f, 6f), CornerRadiiF32.of(8f, 6f)),
        ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f),
    )

    fun clipPathTranslatedSolidDRRect() = clipPathTranslatedDRRect(
        RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), radius = 10f),
        RRectF32.of(RectF32.ofLTRB(22f, 20f, 40f, 38f), radius = 4f),
        ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f),
    )

    fun clipPathTranslatedAsymmetricSolidDRRect() = clipPathTranslatedDRRect(
        RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), CornerRadiiF32.of(4f, 8f), CornerRadiiF32.of(10f, 4f), CornerRadiiF32.of(8f, 12f), CornerRadiiF32.of(6f, 3f)),
        RRectF32.of(RectF32.ofLTRB(20f, 18f, 42f, 39f), CornerRadiiF32.of(3f, 5f), CornerRadiiF32.of(6f, 2f), CornerRadiiF32.of(4f, 7f), CornerRadiiF32.of(2f, 3f)),
        ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f),
    )

    fun clipPathTranslatedEllipseSolidDRRect() = clipPathTranslatedDRRect(
        RRectF32.of(RectF32.ofLTRB(12f, 20f, 52f, 44f), CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f)),
        RRectF32.of(RectF32.ofLTRB(24f, 26f, 40f, 38f), CornerRadiiF32.of(8f, 6f), CornerRadiiF32.of(8f, 6f), CornerRadiiF32.of(8f, 6f), CornerRadiiF32.of(8f, 6f)),
        ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f),
    )

    fun clipPathAxisXTranslatedSolidDRRect() = clipPathTranslatedDRRect(
        RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), radius = 10f),
        RRectF32.of(RectF32.ofLTRB(22f, 20f, 40f, 38f), radius = 4f),
        ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f), 4f, 0f,
    )

    fun clipPathAxisYTranslatedAsymmetricSolidDRRect() = clipPathTranslatedDRRect(
        RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), CornerRadiiF32.of(4f, 8f), CornerRadiiF32.of(10f, 4f), CornerRadiiF32.of(8f, 12f), CornerRadiiF32.of(6f, 3f)),
        RRectF32.of(RectF32.ofLTRB(20f, 18f, 42f, 39f), CornerRadiiF32.of(3f, 5f), CornerRadiiF32.of(6f, 2f), CornerRadiiF32.of(4f, 7f), CornerRadiiF32.of(2f, 3f)),
        ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f), 0f, 5f,
    )

    fun clipPathNegativeXTranslatedEllipseSolidDRRect() = clipPathTranslatedDRRect(
        RRectF32.of(RectF32.ofLTRB(12f, 20f, 52f, 44f), CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f)),
        RRectF32.of(RectF32.ofLTRB(24f, 26f, 40f, 38f), CornerRadiiF32.of(8f, 6f), CornerRadiiF32.of(8f, 6f), CornerRadiiF32.of(8f, 6f), CornerRadiiF32.of(8f, 6f)),
        ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f), -4f, 5f,
    )

    fun clipPathNegativeYTranslatedSolidDRRect() = clipPathTranslatedDRRect(
        RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), radius = 10f),
        RRectF32.of(RectF32.ofLTRB(22f, 20f, 40f, 38f), radius = 4f),
        ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f), 4f, -5f,
    )

    private fun clipPathRRect(rrect: RRectF32, color: ColorARGB) = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipPath(Path {
            moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
        }.apply { fillType = FillType.WINDING }, ClipOp.INTERSECT, antiAlias = false)
        drawRRect(rrect, Paint.fill(color).copy(antiAlias = false))
        restore()
    })

    private fun clipPathTranslatedRRect(rrect: RRectF32, color: ColorARGB, translateX: Float = 4f, translateY: Float = 5f) = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipPath(Path {
            moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
        }.apply { fillType = FillType.WINDING }, ClipOp.INTERSECT, antiAlias = false)
        translate(translateX, translateY)
        drawRRect(rrect, Paint.fill(color).copy(antiAlias = false))
        restore()
    })

    private fun clipPathInverseTranslatedRRect(rrect: RRectF32, color: ColorARGB, translateX: Float, translateY: Float) = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipPath(Path {
            moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
        }.apply { fillType = FillType.INVERSE_WINDING }, ClipOp.INTERSECT, antiAlias = false)
        translate(translateX, translateY)
        drawRRect(rrect, Paint.fill(color).copy(antiAlias = false))
        restore()
    })

    private fun clipPathDRRect(outer: RRectF32, inner: RRectF32, color: ColorARGB) = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipPath(Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
            .apply { fillType = FillType.WINDING }, ClipOp.INTERSECT, antiAlias = false)
        drawDRRect(outer, inner, Paint.fill(color).copy(antiAlias = false))
        restore()
    })

    private fun clipPathTranslatedDRRect(outer: RRectF32, inner: RRectF32, color: ColorARGB, translateX: Float = 4f, translateY: Float = 5f) = KanvasSurfaceProgram(ROUTE_ID, record = {
        save()
        clipPath(Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
            .apply { fillType = FillType.WINDING }, ClipOp.INTERSECT, antiAlias = false)
        translate(translateX, translateY)
        drawDRRect(outer, inner, Paint.fill(color).copy(antiAlias = false))
        restore()
    })

    fun solidTrianglePath() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawPath(Path {
            moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
        }.apply { fillType = FillType.WINDING }, Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false))
    })

    fun quadraticPathFill() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawPath(Path {
            moveTo(8f, 56f); quadTo(32f, 4f, 56f, 56f); close()
        }, Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false))
    })

    fun cubicPathFill() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawPath(Path {
            moveTo(8f, 56f); cubicTo(8f, 0f, 56f, 0f, 56f, 56f); close()
        }, Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false))
    })

    fun ovalPathFill() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawPath(Path {}.addOval(RectF32.ofLTRB(10f, 12f, 54f, 52f)), Paint.fill(ColorARGB.fromRGBA(56f / 255f, 220f / 255f, 120f / 255f)).copy(antiAlias = false))
    })

    fun circlePathFill() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawPath(Path {}.addCircle(32f, 32f, 20f), Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false))
    })

    fun solidConcavePath() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawPath(Path {
            moveTo(8f, 8f); lineTo(56f, 8f); lineTo(56f, 24f); lineTo(32f, 24f)
            lineTo(32f, 40f); lineTo(56f, 40f); lineTo(56f, 56f); lineTo(8f, 56f); close()
        }.apply { fillType = FillType.WINDING }, Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false))
    })

    fun evenOddPathHole() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawPath(Path {}.apply {
            addRect(RectF32.ofLTRB(8f, 8f, 56f, 56f))
            addRect(RectF32.ofLTRB(22f, 20f, 44f, 44f))
            fillType = FillType.EVEN_ODD
        }, Paint.fill(ColorARGB.fromRGBA(56f / 255f, 220f / 255f, 120f / 255f)).copy(antiAlias = false))
    })

    fun windingPathHole() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawPath(Path {}.apply {
            addRect(RectF32.ofLTRB(8f, 8f, 56f, 56f))
            reverseAddPath(Path {}.apply { addRect(RectF32.ofLTRB(22f, 20f, 44f, 44f)) })
            fillType = FillType.WINDING
        }, Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false))
    })

    fun inverseWindingTrianglePath() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawPath(Path {
            moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
        }.apply { fillType = FillType.INVERSE_WINDING }, Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false))
    })

    fun inverseEvenOddPathHole() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawPath(Path {}.apply {
            addRect(RectF32.ofLTRB(8f, 8f, 56f, 56f))
            addRect(RectF32.ofLTRB(22f, 20f, 44f, 44f))
            fillType = FillType.INVERSE_EVEN_ODD
        }, Paint.fill(ColorARGB.fromRGBA(56f / 255f, 220f / 255f, 120f / 255f)).copy(antiAlias = false))
    })

    fun evenOddBowTiePath() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawPath(Path {
            moveTo(8f, 8f); lineTo(56f, 56f); lineTo(8f, 56f); lineTo(56f, 8f); close()
        }.apply { fillType = FillType.EVEN_ODD }, Paint.fill(ColorARGB.fromRGBA(56f / 255f, 220f / 255f, 120f / 255f)).copy(antiAlias = false))
    })

    fun reflectedWindingPathHole() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        concat(Matrix3x3F32(sx = -1f, sy = 1f, tx = 64f))
        drawPath(Path {}.apply {
            addRect(RectF32.ofLTRB(8f, 8f, 56f, 56f))
            reverseAddPath(Path {}.apply { addRect(RectF32.ofLTRB(16f, 20f, 34f, 44f)) })
            fillType = FillType.WINDING
        }, Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false))
    })

    fun implicitClosureTrianglePath() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawPath(Path {
            moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f)
        }.apply { fillType = FillType.WINDING }, Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false))
    })

    fun translatedTrianglePath() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        translate(4f, 5f)
        drawPath(Path {
            moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
        }.apply { fillType = FillType.WINDING }, Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)).copy(antiAlias = false))
    })

    fun uniformScaledTrianglePath() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        scale(1.5f, 1.5f)
        drawPath(Path {
            moveTo(8f, 8f); lineTo(40f, 8f); lineTo(8f, 40f); close()
        }.apply { fillType = FillType.WINDING }, Paint.fill(ColorARGB.fromRGBA(56f / 255f, 220f / 255f, 120f / 255f)).copy(antiAlias = false))
    })

    private val BACKGROUND = ColorARGB.fromRGBA(13f / 255f, 20f / 255f, 33f / 255f, 1f)
}
