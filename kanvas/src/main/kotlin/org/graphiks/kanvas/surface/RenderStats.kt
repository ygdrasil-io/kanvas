package org.graphiks.kanvas.surface

data class RenderStats(
    val opsDispatched: Int,
    val opsRefused: Int,
    val pipelineCount: Int,
    val drawCallCount: Int,
    val coverage: Float,
)
