package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState

/** Exact packet identities accepted by the prepared COLRv0 native route. */
internal val COLOR_GLYPH_RENDER_PIPELINE_KEY =
    GPURenderPipelineKey("pipeline.color-glyph.rgba8unorm.src-over")

internal const val COLOR_GLYPH_BINDING_LAYOUT_HASH =
    "layout.color-glyph.group0.uniform-atlas-sampler"

internal const val COLOR_GLYPH_VERTEX_SOURCE_LABEL = "color-glyph-indexed-quad"

internal const val COLOR_GLYPH_TARGET_STATE_HASH = "target.rgba8unorm.single-sample"

internal fun preparedColorGlyphBlendPlan(): GPUBlendPlan.FixedFunctionBlend =
    GPUBlendPlan.FixedFunctionBlend(
        mode = GPUBlendMode.SRC_OVER,
        state = GPUFixedFunctionBlendState(
            stateId = "one_isa",
            color = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            alpha = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            writeMask = "rgba",
        ),
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )

/** Dump-safe exact encoding shared by recording, preflight, and native materialization. */
internal fun colorGlyphScissorAuthority(bounds: GPUPixelBounds): String =
    "scissor-${bounds.left}-${bounds.top}-${bounds.right}-${bounds.bottom}"
