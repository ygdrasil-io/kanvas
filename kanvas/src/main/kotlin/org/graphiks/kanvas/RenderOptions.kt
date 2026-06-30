package org.graphiks.kanvas

/**
 * Configuration options for the Kanvas rendering pipeline.
 *
 * Controls tessellation budgets, precision thresholds, and other
 * rendering parameters. Passed to [Canvas] at construction time.
 *
 * @property maxPathVertices Maximum number of vertices a single path
 *   may produce after flattening and triangulation. Paths that exceed
 *   this budget throw [IllegalStateException]. Default 256.
 * @property pathTolerance Maximum allowed error (in pixels) when
 *   approximating quadratic and cubic bezier curves with line segments.
 *   Lower values produce smoother curves at the cost of more vertices.
 *   Default 0.25.
 */
data class RenderOptions(
    val maxPathVertices: Int = 256,
    val pathTolerance: Float = 0.25f,
)
