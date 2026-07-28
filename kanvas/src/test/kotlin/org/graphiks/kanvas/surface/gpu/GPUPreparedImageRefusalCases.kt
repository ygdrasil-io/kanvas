package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.images.AlphaType
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageOrientation
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProfile
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProvenance
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceClass
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceFormat
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceInput
import org.graphiks.kanvas.paint.BlendMode

data class ImageRefusalCase(
    val name: String,
    val apiFamily: String,
    val input: GPUPreparedImageSourceInput,
    val expectedCode: String,
)

data class AtlasBlendCase(
    val blendMode: BlendMode,
    val accepted: Boolean,
    val refusalCode: String?,
)

private fun defaultInput(
    sourceClass: GPUPreparedImageSourceClass = GPUPreparedImageSourceClass.DecodedCpu,
    sourceId: String = "rgba",
    width: Int = 1,
    height: Int = 1,
    format: GPUPreparedImageSourceFormat = GPUPreparedImageSourceFormat.Rgba8,
    alphaType: AlphaType = AlphaType.PREMUL,
    sourceRowBytes: Long = -1,
    profile: GPUPreparedImageProfile = GPUPreparedImageProfile.Srgb,
    orientation: GPUPreparedImageOrientation = GPUPreparedImageOrientation.AppliedIdentity,
    provenance: GPUPreparedImageProvenance = GPUPreparedImageProvenance.CallerPixels,
    generation: Long = 7,
    bytes: ByteArray? = byteArrayOf(1, 2, 3, 4),
) = GPUPreparedImageSourceInput(
    sourceClass, sourceId, width, height, format, alphaType,
    if (sourceRowBytes >= 0) sourceRowBytes else width.toLong() * when (format) {
        GPUPreparedImageSourceFormat.A8 -> 1L
        else -> 4L
    },
    profile, orientation, provenance, generation, bytes?.copyOf(),
)

private fun rgba4(w: Int = 1, h: Int = 1, vararg bytes: Byte) = defaultInput(width = w, height = h, bytes = bytes.copyOf())

private fun bgra4(w: Int = 1, h: Int = 1, vararg bytes: Byte) = defaultInput(format = GPUPreparedImageSourceFormat.Bgra8, sourceId = "bgra", width = w, height = h, bytes = bytes.copyOf())

private fun a8(w: Int = 1, h: Int = 1, vararg bytes: Byte) = defaultInput(format = GPUPreparedImageSourceFormat.A8, sourceId = "a8", width = w, height = h, bytes = bytes.copyOf())

private fun fromClass(sourceClass: GPUPreparedImageSourceClass) = defaultInput(sourceClass = sourceClass, sourceId = "test")

object GPUPreparedImageRefusalMatrix {

    val sourceRefusalCases: List<ImageRefusalCase> = listOf(

        ImageRefusalCase(
            name = "encoded source",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = fromClass(GPUPreparedImageSourceClass.Encoded),
            expectedCode = GPUPreparedImageRefusalCodes.CODEC_UNREGISTERED,
        ),
        ImageRefusalCase(
            name = "animated source",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = fromClass(GPUPreparedImageSourceClass.Animated),
            expectedCode = GPUPreparedImageRefusalCodes.ANIMATION,
        ),
        ImageRefusalCase(
            name = "YUV source",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = fromClass(GPUPreparedImageSourceClass.Yuv),
            expectedCode = GPUPreparedImageRefusalCodes.YUV_CONVERSION,
        ),
        ImageRefusalCase(
            name = "HDR source",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = fromClass(GPUPreparedImageSourceClass.Hdr),
            expectedCode = GPUPreparedImageRefusalCodes.HDR_TRANSFER,
        ),
        ImageRefusalCase(
            name = "imported texture",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = fromClass(GPUPreparedImageSourceClass.Imported),
            expectedCode = GPUPreparedImageRefusalCodes.TEXTURE_IMPORT_UNVALIDATED,
        ),

        ImageRefusalCase(
            name = "unsupported pixel format",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = defaultInput(format = GPUPreparedImageSourceFormat.Unsupported),
            expectedCode = GPUPreparedImageRefusalCodes.PIXEL_FORMAT,
        ),

        ImageRefusalCase(
            name = "non-sRGB profile unresolved",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = defaultInput(profile = GPUPreparedImageProfile.Unresolved),
            expectedCode = GPUPreparedImageRefusalCodes.IMAGE_PROFILE_CONVERSION,
        ),
        ImageRefusalCase(
            name = "non-sRGB profile other gamut",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = defaultInput(profile = GPUPreparedImageProfile.Other),
            expectedCode = GPUPreparedImageRefusalCodes.GAMUT_TRANSFORM,
        ),

        ImageRefusalCase(
            name = "unresolved orientation",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = defaultInput(orientation = GPUPreparedImageOrientation.Unresolved),
            expectedCode = GPUPreparedImageRefusalCodes.ORIENTATION,
        ),

        ImageRefusalCase(
            name = "UNPREMUL alpha",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = defaultInput(alphaType = AlphaType.UNPREMUL),
            expectedCode = GPUPreparedImageRefusalCodes.ALPHA_INTERPRETATION,
        ),
        ImageRefusalCase(
            name = "UNKNOWN alpha",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = defaultInput(alphaType = AlphaType.UNKNOWN),
            expectedCode = GPUPreparedImageRefusalCodes.ALPHA_INTERPRETATION,
        ),
        ImageRefusalCase(
            name = "A8 with OPAQUE alpha",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = defaultInput(format = GPUPreparedImageSourceFormat.A8, alphaType = AlphaType.OPAQUE, sourceId = "a8", bytes = byteArrayOf(1)),
            expectedCode = GPUPreparedImageRefusalCodes.ALPHA_INTERPRETATION,
        ),

        ImageRefusalCase(
            name = "zero width",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = defaultInput(width = 0),
            expectedCode = GPUPreparedImageRefusalCodes.DIMENSIONS,
        ),
        ImageRefusalCase(
            name = "zero height",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = defaultInput(height = 0),
            expectedCode = GPUPreparedImageRefusalCodes.DIMENSIONS,
        ),
        ImageRefusalCase(
            name = "negative width",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = defaultInput(width = -1),
            expectedCode = GPUPreparedImageRefusalCodes.DIMENSIONS,
        ),

        ImageRefusalCase(
            name = "negative source generation",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = defaultInput(generation = -1),
            expectedCode = GPUPreparedImageRefusalCodes.NATIVE_GENERATION,
        ),

        ImageRefusalCase(
            name = "source row stride below tight bytes",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = defaultInput(width = 2, sourceRowBytes = 3, bytes = byteArrayOf(1, 2, 3, 4, 5, 6)),
            expectedCode = GPUPreparedImageRefusalCodes.PIXEL_ROW_STRIDE,
        ),

        ImageRefusalCase(
            name = "null pixel bytes",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = defaultInput(bytes = null),
            expectedCode = GPUPreparedImageRefusalCodes.PIXELS_MISSING,
        ),

        ImageRefusalCase(
            name = "pixel byte length below stride x height",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = defaultInput(width = 2, height = 4, sourceRowBytes = 8, bytes = byteArrayOf(1, 2, 3, 4, 1, 2, 3, 4)),
            expectedCode = GPUPreparedImageRefusalCodes.PIXEL_LENGTH,
        ),

        ImageRefusalCase(
            name = "OPAQUE with non-255 alpha bytes",
            apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
            input = defaultInput(alphaType = AlphaType.OPAQUE, bytes = byteArrayOf(1, 2, 3, 4)),
            expectedCode = GPUPreparedImageRefusalCodes.ALPHA_INTERPRETATION,
        ),
    )

    val uploadBudgetCase: ImageRefusalCase = ImageRefusalCase(
        name = "upload budget exceeded",
        apiFamily = "drawImage, drawImageNine, drawImageLattice, drawAtlas",
        input = rgba4(8, 8, *ByteArray(256) { 1 }),
        expectedCode = GPUPreparedImageRefusalCodes.UPLOAD_BUDGET_EXCEEDED,
    )

    val atlasBlendCases: List<AtlasBlendCase> = listOf(
        AtlasBlendCase(BlendMode.SRC, accepted = true, refusalCode = null),
        AtlasBlendCase(BlendMode.DST, accepted = true, refusalCode = null),
        AtlasBlendCase(BlendMode.SRC_OVER, accepted = true, refusalCode = null),
        AtlasBlendCase(BlendMode.PLUS, accepted = true, refusalCode = null),
        AtlasBlendCase(BlendMode.MODULATE, accepted = true, refusalCode = null),
        AtlasBlendCase(BlendMode.CLEAR, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.DST_OVER, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.SRC_IN, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.DST_IN, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.SRC_OUT, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.DST_OUT, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.SRC_ATOP, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.DST_ATOP, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.XOR, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.MULTIPLY, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.SCREEN, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.OVERLAY, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.DARKEN, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.LIGHTEN, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.COLOR_DODGE, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.COLOR_BURN, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.HARD_LIGHT, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.SOFT_LIGHT, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.DIFFERENCE, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.EXCLUSION, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.HUE, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.SATURATION, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.COLOR, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
        AtlasBlendCase(BlendMode.LUMINOSITY, accepted = false, refusalCode = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND),
    )

    val acceptedAtlasBlendModes: Set<BlendMode> = atlasBlendCases
        .filter { it.accepted }
        .map { it.blendMode }
        .toSet()

}
