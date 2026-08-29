package org.graphiks.kanvas.glyph.gpu

import org.graphiks.kanvas.font.FontTextRefusalCodes

/**
 * Canonical stable refusal-code authority for the prepared GPU text pipeline.
 *
 * The one code consumed by font core aliases its neutral font-domain
 * authority. Every renderer-only refusal remains owned by this GPU API layer.
 */
object GPUTextRefusalCodes {
    const val TYPEFACE_MISSING: String = "unsupported.text.typeface_missing"
    const val TYPEFACE_UNSUPPORTED: String = "unsupported.text.typeface_unsupported"
    const val FONT_IDENTITY_UNSTABLE: String = "unsupported.text.font_identity_unstable"
    const val FONT_BYTES_MALFORMED: String = "unsupported.text.font_bytes_malformed"
    const val GLYPH_ID_INVALID: String = "unsupported.text.glyph_id_invalid"
    const val NOTDEF_UNAVAILABLE: String = "unsupported.text.notdef_unavailable"
    const val POSITION_COUNT_MISMATCH: String = "unsupported.text.position_count_mismatch"
    const val POSITION_NONFINITE: String = "unsupported.text.position_nonfinite"
    const val FONT_SIZE_INVALID: String = "unsupported.text.font_size_invalid"
    const val ORIGIN_NONFINITE: String = "unsupported.text.origin_nonfinite"
    const val REPRESENTATION_MISSING: String = "unsupported.text.representation_missing"
    const val BITMAP_CBDT_CBLC_UNSUPPORTED: String =
        "unsupported.text.bitmap_cbdt_cblc_unsupported"
    const val BITMAP_SBIX_UNSUPPORTED: String = "unsupported.text.bitmap_sbix_unsupported"
    const val COLRV1_UNPROVED: String = "unsupported.text.colrv1_unproved"
    const val TRANSFORM_NONFINITE: String = "unsupported.text.transform_nonfinite"
    const val TRANSFORM_SINGULAR: String = "unsupported.text.transform_singular"
    const val TRANSFORM_PERSPECTIVE: String = "unsupported.text.transform_perspective"
    const val PAINT_STYLE_UNSUPPORTED: String = "unsupported.text.paint_style_unsupported"
    const val MATERIAL_UNSUPPORTED: String = "unsupported.text.material_unsupported"
    const val BLEND_UNSUPPORTED: String = "unsupported.text.blend_unsupported"
    const val IMAGE_FILTER_REQUIRES_COMPOSITE: String =
        "unsupported.text.image_filter_requires_composite"
    const val MASK_FILTER_UNSUPPORTED: String = "unsupported.text.mask_filter_unsupported"
    const val PATH_EFFECT_UNSUPPORTED: String = "unsupported.text.path_effect_unsupported"

    const val PAYLOAD_NONDUMPABLE: String = "unsupported.text.payload_nondumpable"
    const val SK_TYPE_LEAKED: String = "unsupported.text.sk_type_leaked"
    const val ARTIFACT_UNREGISTERED: String = FontTextRefusalCodes.ARTIFACT_UNREGISTERED
    const val ARTIFACT_MISSING: String = "unsupported.text.artifact_missing"
    const val ARTIFACT_KEY_NONDETERMINISTIC: String =
        "unsupported.text.artifact_key_nondeterministic"
    const val ARTIFACT_GENERATION_STALE: String = "unsupported.text.artifact_generation_stale"
    const val ARTIFACT_BUDGET_EXCEEDED: String = "unsupported.text.artifact_budget_exceeded"
    const val UPLOAD_PLAN_MISSING: String = "unsupported.text.upload_plan_missing"
    const val UPLOAD_BUDGET_EXCEEDED: String = "unsupported.text.upload_budget_exceeded"
    const val UPLOAD_FAILED: String = "unsupported.text.upload_failed"
    const val ATLAS_DESCRIPTOR_UNACCEPTED: String =
        "unsupported.text.atlas_descriptor_unaccepted"
    const val ATLAS_PAGE_UNAVAILABLE: String = "unsupported.text.atlas_page_unavailable"
    const val ATLAS_ENTRY_MISSING: String = "unsupported.text.atlas_entry_missing"
    const val ATLAS_GENERATION_STALE: String = "unsupported.text.atlas_generation_stale"
    const val A8_ATLAS_ROUTE_UNAVAILABLE: String =
        "unsupported.text.a8_atlas_route_unavailable"
    const val SDF_ROUTE_UNAVAILABLE: String = "unsupported.text.sdf_route_unavailable"
    const val SDF_PARAMS_MISSING: String = "unsupported.text.sdf_params_missing"
    const val SDF_TRANSFORM_UNSUPPORTED: String = "unsupported.text.sdf_transform_unsupported"
    const val OUTLINE_ROUTE_UNAVAILABLE: String = "unsupported.text.outline_route_unavailable"
    const val COLOR_PLAN_UNSUPPORTED: String = "unsupported.text.color_plan_unsupported"
    const val COLOR_COMPOSITE_UNSUPPORTED: String =
        "unsupported.text.color_composite_unsupported"
    const val COLOR_FONT_FORMAT_UNAVAILABLE: String =
        "unsupported.text.color_font.format_unavailable"
    const val COLOR_FONT_LAYER_COUNT_EXCEEDED: String =
        "unsupported.text.color_font.layer_count_exceeded"
    const val BITMAP_ROUTE_UNSUPPORTED: String = "unsupported.text.bitmap_route_unsupported"
    const val SVG_PLAN_UNSUPPORTED: String = "unsupported.text.svg_plan_unsupported"
    const val EMOJI_COLOR_GLYPH_UNAVAILABLE: String =
        "dependency.text.emoji_color_glyph_unavailable"
    const val LCD_FUTURE_RESEARCH: String = "unsupported.text.lcd_future_research"
    const val INSTANCE_BUFFER_BUDGET_EXCEEDED: String =
        "unsupported.text.instance_buffer_budget_exceeded"
    const val BINDING_LAYOUT_UNAVAILABLE: String = "unsupported.text.binding_layout_unavailable"
    const val DESTINATION_READ_UNACCEPTED: String =
        "unsupported.text.destination_read_unaccepted"
    const val CLIP_ROUTE_UNACCEPTED: String = "unsupported.text.clip_route_unaccepted"
    const val CPU_RENDERED_TEXTURE_FORBIDDEN: String =
        "unsupported.text.cpu_rendered_texture_forbidden"
    const val SUBPIXEL_PIXEL_GEOMETRY: String = "unsupported.text.subpixel_pixel_geometry"
    const val SUBPIXEL_TARGET_FORMAT: String = "unsupported.text.subpixel_target_format"
    const val FALLBACK_EXHAUSTED: String = "unsupported.text.fallback_exhausted"
    const val EVICTION_BEFORE_DEPENDENT_DRAW: String =
        "unsupported.text.eviction_before_dependent_draw"
    const val INSTANCE_UPLOAD_AFTER_DRAW: String =
        "unsupported.text.instance_upload_after_draw"
    const val DRAW_RUN_ROUTE_UNAVAILABLE: String =
        "unsupported.text.draw_run_route_unavailable"
    const val OUTLINE_NO_TYPEFACE: String = "unsupported.text.outline.no_typeface"
    const val OUTLINE_NO_SCALER: String = "unsupported.text.outline.no_scaler"
    const val COLOR_NO_TYPEFACE: String = "unsupported.text.color.no_typeface"
    const val COLOR_NO_SCALER: String = "unsupported.text.color.no_scaler"
    const val COLOR_BITMAP_GLYPH: String = "unsupported.text.color_bitmap_glyph"

    const val ATLAS_PAGE_BUDGET_EXCEEDED: String =
        "unsupported.text.atlas_page_budget_exceeded"
    const val ATLAS_PAGE_BYTES_EXCEEDED: String =
        "unsupported.text.atlas_page_bytes_exceeded"
    const val ATLAS_TOTAL_BYTES_EXCEEDED: String =
        "unsupported.text.atlas_total_bytes_exceeded"
    const val GLYPH_BUDGET_EXCEEDED: String = "unsupported.text.glyph_budget_exceeded"
    const val SUBRUN_BUDGET_EXCEEDED: String = "unsupported.text.subrun_budget_exceeded"
    const val INSTANCE_BYTES_EXCEEDED: String = "unsupported.text.instance_bytes_exceeded"
    const val MASK_GENERATION_FAILED: String = "unsupported.text.mask_generation_failed"
    const val ABI_UNAVAILABLE: String = "unsupported.text.abi_unavailable"
    const val OWNERSHIP_INVALID: String = "unsupported.text.ownership_invalid"
    const val RASTERIZATION_FAILED: String = "unsupported.text.rasterization_failed"
    const val PACKING_FAILED: String = "unsupported.text.packing_failed"
}
