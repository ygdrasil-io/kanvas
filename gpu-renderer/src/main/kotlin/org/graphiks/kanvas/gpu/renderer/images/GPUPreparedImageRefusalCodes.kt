package org.graphiks.kanvas.gpu.renderer.images

import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes as CanonicalRefusalCodes

/**
 * Source-compatible facade for callers that still import the former images
 * package authority.
 */
@Deprecated(
    message = "Use org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes",
    replaceWith = ReplaceWith(
        expression = "GPUPreparedImageRefusalCodes",
        imports = ["org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes"],
    ),
)
object GPUPreparedImageRefusalCodes {
    const val PIXELS_MISSING = CanonicalRefusalCodes.PIXELS_MISSING
    const val DIMENSIONS = CanonicalRefusalCodes.DIMENSIONS
    const val PIXEL_ROW_STRIDE = CanonicalRefusalCodes.PIXEL_ROW_STRIDE
    const val PIXEL_LENGTH = CanonicalRefusalCodes.PIXEL_LENGTH
    const val PIXEL_FORMAT = CanonicalRefusalCodes.PIXEL_FORMAT
    const val ALPHA_INTERPRETATION = CanonicalRefusalCodes.ALPHA_INTERPRETATION
    const val IMAGE_PROFILE_CONVERSION = CanonicalRefusalCodes.IMAGE_PROFILE_CONVERSION
    const val GAMUT_TRANSFORM = CanonicalRefusalCodes.GAMUT_TRANSFORM
    const val ORIENTATION = CanonicalRefusalCodes.ORIENTATION
    const val YUV_CONVERSION = CanonicalRefusalCodes.YUV_CONVERSION
    const val HDR_TRANSFER = CanonicalRefusalCodes.HDR_TRANSFER
    const val CODEC_UNREGISTERED = CanonicalRefusalCodes.CODEC_UNREGISTERED
    const val ANIMATION = CanonicalRefusalCodes.ANIMATION
    const val TEXTURE_IMPORT_UNVALIDATED = CanonicalRefusalCodes.TEXTURE_IMPORT_UNVALIDATED
    const val UPLOAD_BUDGET_EXCEEDED = CanonicalRefusalCodes.UPLOAD_BUDGET_EXCEEDED
    const val TEXTURE_LIMIT = CanonicalRefusalCodes.TEXTURE_LIMIT
    const val MIP_REQUIRED = CanonicalRefusalCodes.MIP_REQUIRED
    const val SAMPLING_CUBIC = CanonicalRefusalCodes.SAMPLING_CUBIC
    const val SAMPLING_ANISOTROPIC = CanonicalRefusalCodes.SAMPLING_ANISOTROPIC
    const val TILE_MODE = CanonicalRefusalCodes.TILE_MODE
    const val PERSPECTIVE_SAMPLING = CanonicalRefusalCodes.PERSPECTIVE_SAMPLING
    const val NINE_GEOMETRY = CanonicalRefusalCodes.NINE_GEOMETRY
    const val LATTICE_GEOMETRY = CanonicalRefusalCodes.LATTICE_GEOMETRY
    const val ATLAS_ARRAY_LENGTHS = CanonicalRefusalCodes.ATLAS_ARRAY_LENGTHS
    const val ATLAS_GEOMETRY = CanonicalRefusalCodes.ATLAS_GEOMETRY
    const val ATLAS_SOURCE_BLEND = CanonicalRefusalCodes.ATLAS_SOURCE_BLEND
    const val NATIVE_GENERATION = CanonicalRefusalCodes.NATIVE_GENERATION
    const val NATIVE_BINDING = CanonicalRefusalCodes.NATIVE_BINDING
    const val WGSL_VALIDATION = CanonicalRefusalCodes.WGSL_VALIDATION

    val ALL: Set<String>
        get() = CanonicalRefusalCodes.ALL
}
