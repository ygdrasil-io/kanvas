package org.graphiks.kanvas.gpu.renderer.wgsl

data class WgslRenderStepModule(
    val renderStepIdentity: String,
    val sourceHash: String,
    val vertexEntryPoint: String,
    val fragmentEntryPoint: String,
)

val rrectFillCoverageModule: WgslRenderStepModule = WgslRenderStepModule(
    renderStepIdentity = "rrect.fill.coverage",
    sourceHash = RRectCoverageSnippetSourceHash,
    vertexEntryPoint = "vs_main",
    fragmentEntryPoint = RRectCoverageEntryPoint,
)

val linearGradientModule: WgslRenderStepModule = WgslRenderStepModule(
    renderStepIdentity = "linear.gradient.fill",
    sourceHash = LinearGradientSnippetSourceHash,
    vertexEntryPoint = "vs_main",
    fragmentEntryPoint = LinearGradientEntryPoint,
)
