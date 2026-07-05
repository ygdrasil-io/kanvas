package org.graphiks.kanvas.surface

/**
 * Listener for per-operation pipeline events during rendering.
 *
 * When [DebugLevel.TRACE] is active, the GPU renderer calls these methods
 * for each [DisplayOp] as it is processed. Set via [Surface.renderOpListener].
 */
interface RenderOpListener {
    /** Called when an operation is successfully dispatched to the GPU pipeline. */
    fun onOpDispatched(
        index: Int,
        opType: String,
        route: String,
        shaders: List<String>,
        vertexCount: Int,
        blendMode: String,
    )

    /** Called when an operation is refused by the GPU pipeline. */
    fun onOpRefused(
        index: Int,
        opType: String,
        code: String,
        reason: String,
    )
}
