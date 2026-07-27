package org.graphiks.kanvas.gpu.renderer.diagnostics

/**
 * Canonical authority for every stable prepared-image refusal code in the
 * approved FP-04 contract.
 */
object GPUPreparedImageRefusalCodes {
    const val PIXELS_MISSING = "unsupported.image.pixels_missing"
    const val DIMENSIONS = "unsupported.image.dimensions"
    const val PIXEL_ROW_STRIDE = "unsupported.image.pixel.row_stride"
    const val PIXEL_LENGTH = "unsupported.image.pixel.length"
    const val PIXEL_FORMAT = "unsupported.image.pixel.format"
    const val ALPHA_INTERPRETATION = "unsupported.image.alpha_interpretation"
    const val IMAGE_PROFILE_CONVERSION = "unsupported.color.image_profile_conversion"
    const val GAMUT_TRANSFORM = "unsupported.color.gamut_transform"
    const val ORIENTATION = "unsupported.image.orientation"
    const val YUV_CONVERSION = "unsupported.color.yuv_conversion"
    const val HDR_TRANSFER = "unsupported.color.hdr_transfer"
    const val CODEC_UNREGISTERED = "unsupported.image.codec.unregistered"
    const val ANIMATION = "unsupported.image.animation"
    const val TEXTURE_IMPORT_UNVALIDATED = "unsupported.texture.import_unvalidated"
    const val UPLOAD_BUDGET_EXCEEDED = "unsupported.image.upload.budget_exceeded"
    const val TEXTURE_LIMIT = "unsupported.image.texture_limit"
    const val MIP_REQUIRED = "unsupported.image.mip_required"
    const val SAMPLING_CUBIC = "unsupported.image.sampling_cubic"
    const val SAMPLING_ANISOTROPIC = "unsupported.image.sampling_anisotropic"
    const val TILE_MODE = "unsupported.image.tile_mode"
    const val PERSPECTIVE_SAMPLING = "unsupported.image.perspective_sampling"
    const val NINE_GEOMETRY = "unsupported.image.nine_geometry"
    const val LATTICE_GEOMETRY = "unsupported.image.lattice_geometry"
    const val ATLAS_ARRAY_LENGTHS = "unsupported.image.atlas.array_lengths"
    const val ATLAS_GEOMETRY = "unsupported.image.atlas.geometry"
    const val ATLAS_SOURCE_BLEND = "unsupported.image.atlas.source_blend"
    const val NATIVE_GENERATION = "unsupported.image.native_generation"
    const val NATIVE_BINDING = "unsupported.image.native_binding"
    const val WGSL_VALIDATION = "unsupported.image.wgsl_validation"

    val ALL: Set<String> = setOf(
        PIXELS_MISSING,
        DIMENSIONS,
        PIXEL_ROW_STRIDE,
        PIXEL_LENGTH,
        PIXEL_FORMAT,
        ALPHA_INTERPRETATION,
        IMAGE_PROFILE_CONVERSION,
        GAMUT_TRANSFORM,
        ORIENTATION,
        YUV_CONVERSION,
        HDR_TRANSFER,
        CODEC_UNREGISTERED,
        ANIMATION,
        TEXTURE_IMPORT_UNVALIDATED,
        UPLOAD_BUDGET_EXCEEDED,
        TEXTURE_LIMIT,
        MIP_REQUIRED,
        SAMPLING_CUBIC,
        SAMPLING_ANISOTROPIC,
        TILE_MODE,
        PERSPECTIVE_SAMPLING,
        NINE_GEOMETRY,
        LATTICE_GEOMETRY,
        ATLAS_ARRAY_LENGTHS,
        ATLAS_GEOMETRY,
        ATLAS_SOURCE_BLEND,
        NATIVE_GENERATION,
        NATIVE_BINDING,
        WGSL_VALIDATION,
    )
}
