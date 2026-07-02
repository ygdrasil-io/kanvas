package org.graphiks.kanvas.gpu.renderer.wgsl

/** A render-step module identified by its shader stage entry points and source hash. */
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

val linearGradientRepeatModule: WgslRenderStepModule = WgslRenderStepModule(
    renderStepIdentity = "linear.gradient.repeat.fill",
    sourceHash = LinearGradientSnippetSourceHash,
    vertexEntryPoint = "vs_main",
    fragmentEntryPoint = LinearGradientRepeatEntryPoint,
)

val linearGradientMirrorModule: WgslRenderStepModule = WgslRenderStepModule(
    renderStepIdentity = "linear.gradient.mirror.fill",
    sourceHash = LinearGradientSnippetSourceHash,
    vertexEntryPoint = "vs_main",
    fragmentEntryPoint = LinearGradientMirrorEntryPoint,
)

val linearGradientDecalModule: WgslRenderStepModule = WgslRenderStepModule(
    renderStepIdentity = "linear.gradient.decal.fill",
    sourceHash = LinearGradientSnippetSourceHash,
    vertexEntryPoint = "vs_main",
    fragmentEntryPoint = LinearGradientDecalEntryPoint,
)

val radialGradientRepeatModule: WgslRenderStepModule = WgslRenderStepModule(
    renderStepIdentity = "radial.gradient.repeat.fill",
    sourceHash = RadialGradientSnippetSourceHash,
    vertexEntryPoint = "vs_main",
    fragmentEntryPoint = RadialGradientRepeatEntryPoint,
)

val radialGradientMirrorModule: WgslRenderStepModule = WgslRenderStepModule(
    renderStepIdentity = "radial.gradient.mirror.fill",
    sourceHash = RadialGradientSnippetSourceHash,
    vertexEntryPoint = "vs_main",
    fragmentEntryPoint = RadialGradientMirrorEntryPoint,
)

val radialGradientDecalModule: WgslRenderStepModule = WgslRenderStepModule(
    renderStepIdentity = "radial.gradient.decal.fill",
    sourceHash = RadialGradientSnippetSourceHash,
    vertexEntryPoint = "vs_main",
    fragmentEntryPoint = RadialGradientDecalEntryPoint,
)

val sweepGradientRepeatModule: WgslRenderStepModule = WgslRenderStepModule(
    renderStepIdentity = "sweep.gradient.repeat.fill",
    sourceHash = SweepGradientSnippetSourceHash,
    vertexEntryPoint = "vs_main",
    fragmentEntryPoint = SweepGradientRepeatEntryPoint,
)

val sweepGradientMirrorModule: WgslRenderStepModule = WgslRenderStepModule(
    renderStepIdentity = "sweep.gradient.mirror.fill",
    sourceHash = SweepGradientSnippetSourceHash,
    vertexEntryPoint = "vs_main",
    fragmentEntryPoint = SweepGradientMirrorEntryPoint,
)

val sweepGradientDecalModule: WgslRenderStepModule = WgslRenderStepModule(
    renderStepIdentity = "sweep.gradient.decal.fill",
    sourceHash = SweepGradientSnippetSourceHash,
    vertexEntryPoint = "vs_main",
    fragmentEntryPoint = SweepGradientDecalEntryPoint,
)
