package org.graphiks.kanvas.surface

/**
 * Aggregate counters for a single [Surface.render] pass.
 *
 * @property opsDispatched  number of display-list operations submitted for rendering
 * @property opsRefused     number of operations that were refused (e.g. unsupported state)
 * @property pipelineCount  number of real GPU pipeline binds emitted during the pass; repeated binds count separately
 * @property drawCallCount  number of draw calls issued to the GPU
 * @property coverage       fraction of the target surface that received any drawing (0.0 – 1.0)
 * @property coverageMeasured whether [coverage] comes from a measured coverage source
 */
data class RenderStats(
    val opsDispatched: Int,
    val opsRefused: Int,
    val pipelineCount: Int,
    val drawCallCount: Int,
    val coverage: Float,
    val coverageMeasured: Boolean = false,
)
