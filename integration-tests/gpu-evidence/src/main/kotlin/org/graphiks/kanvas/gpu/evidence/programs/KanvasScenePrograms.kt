package org.graphiks.kanvas.gpu.evidence.programs

import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Point

/** Rendered evidence scenes expressed solely through the public Kanvas Canvas API. */
object KanvasScenePrograms {
    private const val ROUTE_ID = "kanvas.surface.render"

    fun solidCardStack() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawRect(Rect.fromLTRB(8f, 10f, 56f, 34f), Paint.fill(Color.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)))
        drawRect(Rect.fromLTRB(14f, 38f, 50f, 54f), Paint.fill(Color.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)))
    })

    fun separableBlurRect() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(
            Rect.fromLTRB(16f, 16f, 48f, 48f),
            Paint(
                color = Color.fromRGBA(0.18f, 0.42f, 0.76f, 1f),
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 3f),
                antiAlias = false,
            ),
        )
    })

    fun translucentCardOverlap() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawRect(
            Rect.fromLTRB(8f, 10f, 44f, 42f),
            Paint.fill(Color.fromRGBA(0.25f, 0.5f, 0.75f, 0.5f)),
        )
        drawRect(
            Rect.fromLTRB(24f, 22f, 56f, 54f),
            Paint.fill(Color.fromRGBA(0.5f, 0.25f, 0.125f, 0.5f)),
        )
    })

    fun scissorOverlay() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        save()
        clipRect(Rect.fromLTRB(16f, 16f, 40f, 40f), antiAlias = false)
        drawRect(Rect.fromLTRB(8f, 8f, 56f, 56f), Paint.fill(Color.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)))
        restore()
        save()
        clipRect(Rect.fromLTRB(24f, 24f, 48f, 48f), antiAlias = false)
        drawRect(Rect.fromLTRB(16f, 16f, 56f, 56f), Paint.fill(Color.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)))
        restore()
    })

    fun strokeRectOutline() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawColor(BACKGROUND)
        drawRect(
            Rect.fromLTRB(16f, 16f, 48f, 48f),
            Paint.stroke(Color.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f), 6f).copy(antiAlias = false),
        )
    })

    fun linearGradientLanes() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(
            Rect.fromLTRB(8f, 16f, 56f, 48f),
            Paint(
                shader = Shader.LinearGradient(
                    Point(8.5f, 32.5f), Point(55.5f, 32.5f),
                    listOf(GradientStop(0f, Color.fromArgb(255, 255, 56, 56)), GradientStop(1f, Color.fromArgb(255, 56, 112, 255))),
                    TileMode.CLAMP,
                ),
                antiAlias = false,
            ),
        )
    })

    fun radialSwatch() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(
            Rect.fromLTRB(8f, 8f, 56f, 56f),
            Paint(
                shader = Shader.RadialGradient(
                    Point(32.5f, 32.5f), 23.5f,
                    listOf(GradientStop(0f, Color.fromArgb(255, 255, 232, 72)), GradientStop(1f, Color.fromArgb(255, 48, 80, 192))),
                    TileMode.CLAMP,
                ),
                antiAlias = false,
            ),
        )
    })

    fun sweepDisk() = KanvasSurfaceProgram(ROUTE_ID, record = {
        drawRect(
            Rect.fromLTRB(8f, 8f, 56f, 56f),
            Paint(
                shader = Shader.SweepGradient(
                    Point(32.5f, 32.5f), 0f, 360f,
                    listOf(GradientStop(0f, Color.fromArgb(255, 255, 64, 64)), GradientStop(1f, Color.fromArgb(255, 64, 208, 255))),
                    TileMode.CLAMP,
                ),
                antiAlias = false,
            ),
        )
    })

    private val BACKGROUND = Color.fromRGBA(13f / 255f, 20f / 255f, 33f / 255f, 1f)
}
