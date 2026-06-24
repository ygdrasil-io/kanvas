package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import org.graphiks.kanvas.gpu.renderer.telemetry.GPUPipelineCacheTelemetry

/**
 * KGPU-M27-002: derives pipeline-cache telemetry from the exact pipeline passes
 * that [RectOnlyOffscreenRenderer.renderToPixels] emits for a draw plan.
 *
 * The renderer assembles one WGSL module + pipeline per active draw family group
 * (solid rects, each gradient kind, blur, colorMatrix, stroke, bitmap, text,
 * runtime-effect, save-layer). Across a steady-state per-scene render loop the
 * first frame creates (misses) each pipeline and later frames reuse (hit) it, so
 * this telemetry models a warm cache deterministically from the plan.
 *
 * This is draw-plan-derived telemetry, not a backend pipeline-cache observation;
 * it carries no GPU support or performance claim by itself.
 */
internal fun rectOnlyPipelineCacheTelemetry(
    drawPlan: RectOnlyDrawPlan,
    sceneId: String,
    frameCount: Int,
): GPUPipelineCacheTelemetry {
    require(frameCount >= 1) { "pipeline cache telemetry frameCount must be >= 1: $frameCount" }
    val passFamilies = rectOnlyPipelinePassFamilies(drawPlan)
    val distinctPipelines = passFamilies.size.toLong()
    return GPUPipelineCacheTelemetry(
        sceneId = sceneId,
        hitCount = distinctPipelines * (frameCount - 1).toLong(),
        missCount = distinctPipelines,
        evictionCount = 0L,
        moduleCount = distinctPipelines,
        pipelineCreationCountsByFamily = passFamilies.associateWith { 1L },
    )
}

/**
 * Returns the distinct pipeline-pass family labels the renderer assembles for a
 * draw plan, mirroring the grouping in [RectOnlyOffscreenRenderer.renderToPixels].
 */
internal fun rectOnlyPipelinePassFamilies(drawPlan: RectOnlyDrawPlan): List<String> {
    val effectFamilies = setOf(
        "linear-gradient-rect", "radial-gradient-rect", "sweep-gradient-rect",
        "blur-rect", "color-matrix-rect", "stroke-rect",
        "bitmap-rect", "runtime-effect", "text-run", "save-layer",
    )
    val hasSolid = drawPlan.fills.any { it.family !in effectFamilies && it.family != "vertices" }
    fun has(family: String): Boolean = drawPlan.fills.any { it.family == family }
    return buildList {
        if (hasSolid) add("SolidRect")
        if (has("linear-gradient-rect")) add("LinearGradient")
        if (has("radial-gradient-rect")) add("RadialGradient")
        if (has("sweep-gradient-rect")) add("SweepGradient")
        if (has("blur-rect")) add("Blur")
        if (has("color-matrix-rect")) add("ColorMatrix")
        if (has("stroke-rect")) add("Stroke")
        if (has("bitmap-rect")) add("BitmapRect")
        if (has("runtime-effect")) add("RuntimeEffect")
        if (has("text-run")) add("Text")
        if (has("save-layer")) add("SaveLayer")
    }
}
