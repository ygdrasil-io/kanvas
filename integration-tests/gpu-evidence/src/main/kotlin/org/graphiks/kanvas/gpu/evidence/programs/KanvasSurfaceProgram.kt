package org.graphiks.kanvas.gpu.evidence.programs

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceProgram
import org.graphiks.kanvas.gpu.evidence.runner.KanvasSurfaceRenderSession
import org.graphiks.kanvas.surface.Surface

/** Evidence program recorded and rendered solely through the public Kanvas [Surface] API. */
class KanvasSurfaceProgram(
    val routeId: String,
    private val record: Canvas.() -> Unit,
    internal val sessionFactory: (Int, Int, Canvas.() -> Unit) -> KanvasSurfaceRenderSession =
        { width, height, commands -> Surface(width, height).let { surface ->
            surface.canvas(commands)
            KanvasSurfaceRenderSession { surface.render() }
        } },
) : EvidenceProgram {
    private var session: KanvasSurfaceRenderSession? = null

    init {
        require(routeId.isNotBlank()) { "KanvasSurfaceProgram.routeId must not be blank" }
    }

    /** Opens and records the Surface exactly once; later calls reuse its render session. */
    fun openSession(width: Int, height: Int): KanvasSurfaceRenderSession =
        session ?: sessionFactory(width, height, record).also { session = it }
}
