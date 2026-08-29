package org.graphiks.kanvas.skia

enum class GmConformanceScope(val wireName: String) {
    ELIGIBLE("eligible"),
    EXCLUDED_FONT("excluded-font"),
    EXCLUDED_CODEC("excluded-codec"),
    ACCEPTED_SKIA_GAP("accepted-skia-gap"),
}

data class GmConformanceDecision(
    val scope: GmConformanceScope,
    val reason: String? = null,
    val owner: String? = null,
) {
    init {
        when (scope) {
            GmConformanceScope.ELIGIBLE -> require(reason == null && owner == null)
            GmConformanceScope.EXCLUDED_FONT,
            GmConformanceScope.EXCLUDED_CODEC,
            -> require(!reason.isNullOrBlank() && owner == null)
            GmConformanceScope.ACCEPTED_SKIA_GAP ->
                require(!reason.isNullOrBlank() && !owner.isNullOrBlank())
        }
    }

    val mustAttempt: Boolean
        get() = scope == GmConformanceScope.ELIGIBLE ||
            scope == GmConformanceScope.ACCEPTED_SKIA_GAP
}

object SkiaGmConformance {
    private const val CODEC_REASON = "direct-codec-decode-or-encode"
    private const val FONT_REASON = "direct-font-output"

    private val excludedCodecGmNames = setOf(
        "clip_shader_difference", "clip_shader_layer", "clip_shader_persp", "clip_shader",
        "destcolor", "ducky_yuv_blend", "encode", "hslcolorfilter", "HSL_duck",
        "imagefilter_composed_transform", "imagefilter_convolve_subset", "imagefilters_effect_order",
        "imagefilter_matrix_localmatrix", "patch_image", "patch_image_persp", "savelayer_initfromprev",
        "all_bitmap_configs", "AnimCodecPlayerExif_required.webp", "AnimCodecPlayerExif_required.gif",
        "AnimCodecPlayerExif_stoplight_h.webp", "animatedGif", "bitmap-image-srgb-legacy",
        "bitmap_subset_shader", "colorwheel_alphatypes", "colorwheel", "compositor_quads_image",
        "coordclampshader", "copyTo4444", "draw_bitmap_rect_skbug4734", "encode-alpha-jpeg",
        "encode-color-types-webp-lossless", "encode-platform", "encode-srgb-png", "filterindiabox",
        "grayscalejpg", "imagefilter_transformed_image", "imagemakewithfilter", "imageshader_tinyscale",
        "localmatriximageshader_filtering", "localmatrixshader_persp", "local_matrix_shader_rt",
        "localmatrix_order", "makecolorspace", "makeRasterImage", "persp_images", "readpixelscodec",
        "reinterpretcolorspace", "repeated_bitmap", "repeated_bitmap_jpg", "showmiplevels_explicit",
        "mesh_with_effects", "mesh_with_image", "mesh_with_paint_color", "mesh_with_paint_image",
    )

    private val acceptedSkiaGaps = emptyMap<String, GmConformanceDecision>()

    fun decisionFor(
        gm: SkiaGm,
        observedDependencies: Set<GmExternalDependency> = emptySet(),
    ): GmConformanceDecision = when {
        gm.name in excludedCodecGmNames ->
            GmConformanceDecision(GmConformanceScope.EXCLUDED_CODEC, CODEC_REASON)
        gm.renderFamily == RenderFamily.TEXT || GmExternalDependency.FONT in observedDependencies ->
            GmConformanceDecision(GmConformanceScope.EXCLUDED_FONT, FONT_REASON)
        gm.name in acceptedSkiaGaps -> acceptedSkiaGaps.getValue(gm.name)
        else -> GmConformanceDecision(GmConformanceScope.ELIGIBLE)
    }
}
