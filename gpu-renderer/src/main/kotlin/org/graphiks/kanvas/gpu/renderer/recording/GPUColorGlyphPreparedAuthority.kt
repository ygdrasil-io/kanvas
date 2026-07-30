package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
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

internal const val COLOR_GLYPH_PACKET_PASS_AUTHORITY_CODE =
    "invalid.preflight.color_glyph_packet_authority"

internal const val COLOR_GLYPH_PACKET_PASS_AUTHORITY_MESSAGE =
    "ColorGlyph packet and pass state must match the exact prepared native route authority."

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

internal data class GPUPreparedColorGlyphPacketAuthorityRefusal(
    val code: String,
    val message: String,
)

/** Canonical ColorGlyph packet authority shared by recording and preflight. */
internal fun preparedColorGlyphPacketAuthorityRefusal(
    packet: GPUDrawPacket,
    semantic: GPUDrawSemanticPayload.ColorGlyph,
): GPUPreparedColorGlyphPacketAuthorityRefusal? {
    if (packet.uniformSlot != semantic.payloadRef.uniformSlot) {
        return GPUPreparedColorGlyphPacketAuthorityRefusal(
            code = "invalid.preflight.color_glyph_semantic_packet_slot_mismatch",
            message = "ColorGlyph uniform slot differs from its packet evidence.",
        )
    }
    if (!semantic.hasCanonicalHashIntegrity()) {
        return GPUPreparedColorGlyphPacketAuthorityRefusal(
            code = "invalid.preflight.color_glyph_canonical_hash_mismatch",
            message = "ColorGlyph canonical hash does not match its immutable payload fields.",
        )
    }
    if (packet.renderPipelineKey != COLOR_GLYPH_RENDER_PIPELINE_KEY ||
        packet.bindingLayoutHash != COLOR_GLYPH_BINDING_LAYOUT_HASH ||
        packet.vertexSourceLabel != COLOR_GLYPH_VERTEX_SOURCE_LABEL ||
        packet.targetStateHash != COLOR_GLYPH_TARGET_STATE_HASH ||
        packet.scissorBoundsHash != colorGlyphScissorAuthority(semantic.scissorBounds)
    ) {
        return GPUPreparedColorGlyphPacketAuthorityRefusal(
            code = COLOR_GLYPH_PACKET_PASS_AUTHORITY_CODE,
            message = COLOR_GLYPH_PACKET_PASS_AUTHORITY_MESSAGE,
        )
    }
    return null
}
