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

val radialGradientModule: WgslRenderStepModule = WgslRenderStepModule(
    renderStepIdentity = "radial.gradient.fill",
    sourceHash = RadialGradientSnippetSourceHash,
    vertexEntryPoint = "vs_main",
    fragmentEntryPoint = RadialGradientEntryPoint,
)

val sweepGradientModule: WgslRenderStepModule = WgslRenderStepModule(
    renderStepIdentity = "sweep.gradient.fill",
    sourceHash = SweepGradientSnippetSourceHash,
    vertexEntryPoint = "vs_main",
    fragmentEntryPoint = SweepGradientEntryPoint,
)
