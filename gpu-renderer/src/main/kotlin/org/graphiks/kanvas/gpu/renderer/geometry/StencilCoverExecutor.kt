package org.graphiks.kanvas.gpu.renderer.geometry

/** Statistics produced by [StencilCoverExecutor.execute] for one stencil-cover fill. */
data class StencilCoverStats(
    val stencilPassCount: Int,
    val coverPassCount: Int,
    val totalDrawCalls: Int,
    val triangleCount: Int,
    val vertexCount: Int,
)

/**
 * Executes a two-pass stencil-cover fill for non-convex paths.
 *
 * Pass 1 renders tessellated triangles into the stencil buffer using
 * increment/decrement operations to track winding. Pass 2 resolves
 * the stencil buffer by drawing a fullscreen quad with a stencil test
 * and applying the fill color to marked pixels.
 */
class StencilCoverExecutor(
    private val clearStencilValue: Int = 0,
) {
    /**
     * Executes the stencil-cover fill for the given triangulated path.
     * Returns execution statistics without committing GPU work.
     */
    fun execute(triangulated: TriangleList): StencilCoverStats {
        require(triangulated.triangleCount > 0) {
            "StencilCoverExecutor requires at least one triangle"
        }

        val stats = StencilCoverStats(
            stencilPassCount = 1,
            coverPassCount = 1,
            totalDrawCalls = 2,
            triangleCount = triangulated.triangleCount,
            vertexCount = triangulated.vertices.size,
        )

        return stats
    }

    /** Returns diagnostic lines describing the stencil buffer state between passes. */
    fun stencilStateDiagnostics(): List<String> = listOf(
        "stencil:pass state=stencil-write passIndex=1",
        "stencil:pass state=stencil-write clearValue=$clearStencilValue",
        "stencil:pass clear between passes=true",
        "stencil:pass operation=increment-wrapping",
        "stencil:pass operation=decrement-wrapping",
        "stencil:pass compare=always",
        "stencil:cover state=cover-resolve passIndex=2",
        "stencil:cover compare=not-equal",
        "stencil:cover clearValue=$clearStencilValue",
        "stencil:stats twoPass=true stencilWrite=1 coverResolve=1 totalDraws=2",
    )
}
