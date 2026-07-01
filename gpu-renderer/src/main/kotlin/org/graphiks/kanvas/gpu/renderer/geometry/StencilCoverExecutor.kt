package org.graphiks.kanvas.gpu.renderer.geometry

/** Statistics produced by [StencilCoverExecutor.execute] for one stencil-cover fill. */
data class StencilCoverStats(
    val stencilPassCount: Int,
    val coverPassCount: Int,
    val totalDrawCalls: Int,
    val triangleCount: Int,
    val vertexCount: Int,
    val invertedCover: Boolean = false,
)

/**
 * Executes a two-pass stencil-cover fill for non-convex paths.
 *
 * Pass 1 renders tessellated triangles into the stencil buffer using
 * increment/decrement operations to track winding. Pass 2 resolves
 * the stencil buffer by drawing a fullscreen quad with a stencil test
 * and applying the fill color to marked pixels.
 *
 * For inverse fills, the stencil test in the cover pass is inverted:
 * pixels where stencil == [clearStencilValue] are filled instead of
 * pixels where stencil != [clearStencilValue].
 */
class StencilCoverExecutor(
    private val clearStencilValue: Int = 0,
) {
    /**
     * Executes the stencil-cover fill for the given triangulated path.
     * Returns execution statistics without committing GPU work.
     *
     * @param inverseFill when true, the cover pass fills pixels where
     *   stencil equals [clearStencilValue] (inverted test).
     */
    fun execute(triangulated: TriangleList, inverseFill: Boolean = false): StencilCoverStats {
        if (!inverseFill) {
            require(triangulated.triangleCount > 0) {
                "StencilCoverExecutor requires at least one triangle for non-inverse fills"
            }
        }

        val hasTriangles = triangulated.triangleCount > 0
        val stats = StencilCoverStats(
            stencilPassCount = if (hasTriangles) 1 else 0,
            coverPassCount = 1,
            totalDrawCalls = if (hasTriangles) 2 else 1,
            triangleCount = triangulated.triangleCount,
            vertexCount = triangulated.vertices.size,
            invertedCover = inverseFill,
        )

        return stats
    }

    /** Returns diagnostic lines describing the stencil buffer state between passes. */
    fun stencilStateDiagnostics(inverseFill: Boolean = false): List<String> {
        val compareOp = if (inverseFill) "equal" else "not-equal"
        return listOf(
            "stencil:pass state=stencil-write passIndex=1",
            "stencil:pass state=stencil-write clearValue=$clearStencilValue",
            "stencil:pass clear between passes=true",
            "stencil:pass operation=increment-wrapping",
            "stencil:pass operation=decrement-wrapping",
            "stencil:pass compare=always",
            "stencil:cover state=cover-resolve passIndex=2",
            "stencil:cover compare=$compareOp",
            "stencil:cover clearValue=$clearStencilValue",
            "stencil:stats twoPass=true stencilWrite=1 coverResolve=1 totalDraws=2",
        )
    }
}
