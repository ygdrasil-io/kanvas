package org.graphiks.kanvas.gpu.evidence.programs

import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

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

    private val BACKGROUND = Color.fromRGBA(13f / 255f, 20f / 255f, 33f / 255f, 1f)
}
