package org.graphiks.kanvas.gpu.evidence.programs

import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.BlurStyle
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

    fun strokeRectOutline() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawRect(
            RectF32.ofLTRB(16f, 16f, 48f, 48f),
            Paint.stroke(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f), 6f).copy(antiAlias = false),
        )
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

    fun gradientStrokeRefusal() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(RectF32.ofLTRB(8f, 16f, 56f, 48f), Paint.stroke(ColorARGB.Transparent, 4f).copy(
            shader = Shader.LinearGradient(
                Point2F32(8.5f, 32.5f), Point2F32(55.5f, 32.5f),
                listOf(GradientStop(0f, ColorARGB.of(255, 255, 56, 56)), GradientStop(1f, ColorARGB.of(255, 56, 112, 255))), TileMode.CLAMP,
            ), antiAlias = false,
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

    fun solidTrianglePath() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawPath(Path {
            moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
        }.apply { fillType = FillType.WINDING }, Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)).copy(antiAlias = false))
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

    private val BACKGROUND = ColorARGB.fromRGBA(13f / 255f, 20f / 255f, 33f / 255f, 1f)
}
