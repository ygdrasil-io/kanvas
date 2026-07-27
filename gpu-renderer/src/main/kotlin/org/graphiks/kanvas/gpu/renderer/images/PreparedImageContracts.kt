package org.graphiks.kanvas.gpu.renderer.images

import io.ygdrasil.webgpu.GPUTextureFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import java.security.MessageDigest

enum class AlphaType { OPAQUE, PREMUL, UNPREMUL, UNKNOWN }
enum class GPUPreparedImageSourceClass { DecodedCpu, Encoded, Animated, Yuv, Hdr, Imported }
enum class GPUPreparedImageSourceFormat { Rgba8, Bgra8, A8, Unsupported }
enum class GPUPreparedImageProfile { Srgb, Other, Unresolved }
enum class GPUPreparedImageOrientation { AppliedIdentity, Unresolved }
enum class GPUPreparedImageProvenance { CallerPixels, SurfaceReadback, RegisteredDecode }

internal data class GPUPreparedSdrColorContract(
    val colorSourceTextureFormat: GPUTextureFormat,
    val coverageSourceTextureFormat: GPUTextureFormat,
    val colorUploadEncoding: GPUPreparedColorUploadEncoding,
    val targetTextureFormat: GPUTextureFormat,
    val shaderInterpretation: GPUColorInterpretation,
    val readbackInterpretation: GPUColorInterpretation,
)

internal enum class GPUPreparedColorUploadEncoding {
    StraightEncodedSrgb,
}

internal fun preparedSdrColorContract(): GPUPreparedSdrColorContract =
    GPUPreparedSdrColorContract(
        colorSourceTextureFormat = GPUTextureFormat.RGBA8UnormSrgb,
        coverageSourceTextureFormat = GPUTextureFormat.RGBA8Unorm,
        colorUploadEncoding = GPUPreparedColorUploadEncoding.StraightEncodedSrgb,
        targetTextureFormat = GPUTextureFormat.RGBA8UnormSrgb,
        shaderInterpretation = GPUColorInterpretation.LinearPremul,
        readbackInterpretation = GPUColorInterpretation.EncodedPremulSrgb,
    )

/** Public authority for every stable refusal row in the approved FP-04 contract. */
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

class GPUPreparedImageSourceInput(
    val sourceClass: GPUPreparedImageSourceClass,
    val sourceId: String,
    val width: Int,
    val height: Int,
    val sourceFormat: GPUPreparedImageSourceFormat,
    val alphaType: AlphaType,
    val sourceRowBytes: Long,
    val profile: GPUPreparedImageProfile,
    val orientation: GPUPreparedImageOrientation,
    val provenance: GPUPreparedImageProvenance,
    val sourceGeneration: Long,
    pixelBytes: ByteArray?,
) {
    private val snapshot = pixelBytes?.copyOf()
    internal fun snapshotBytesOrNull(): ByteArray? = snapshot?.copyOf()
}

data class GPUPreparedImagePixelLayout(
    val sourceRowBytes: Long,
    val normalizedRgba8RowBytes: Long,
    val rowCount: Int,
)

class GPUPreparedImageUploadArtifact internal constructor(
    val key: GPUImageUploadArtifactKey,
    val width: Int,
    val height: Int,
    val pixelLayout: GPUPreparedImagePixelLayout,
    val sourceGeneration: Long,
    val contentHash: String,
    val alphaOnly: Boolean,
    val colorInterpretation: String,
    internal val colorUploadEncoding: GPUPreparedColorUploadEncoding?,
    internal val colorUploadInterpretation: String,
    rgba8UploadBytes: ByteArray,
) {
    private val snapshot = rgba8UploadBytes.copyOf()
    fun tightRgba8BytesForUpload(): ByteArray = snapshot.copyOf()
}

sealed interface GPUPreparedImageArtifactResult {
    data class Ready(val artifact: GPUPreparedImageUploadArtifact) : GPUPreparedImageArtifactResult
    data class Refused(val code: String, val facts: Map<String, String>) : GPUPreparedImageArtifactResult
}

object GPUPreparedImageArtifactFactory {
    fun prepare(
        input: GPUPreparedImageSourceInput,
        maxUploadBytes: Long = 64L * 1024L * 1024L,
    ): GPUPreparedImageArtifactResult {
        val bytes = input.snapshotBytesOrNull()
        fun refuse(code: String, vararg facts: Pair<String, Any>): GPUPreparedImageArtifactResult.Refused =
            GPUPreparedImageArtifactResult.Refused(
                code,
                facts.associate { it.first to it.second.toString() } + ("boundary" to "artifact"),
            )

        when (input.sourceClass) {
            GPUPreparedImageSourceClass.DecodedCpu -> Unit
            GPUPreparedImageSourceClass.Encoded ->
                return refuse(GPUPreparedImageRefusalCodes.CODEC_UNREGISTERED, "sourceClass" to input.sourceClass)
            GPUPreparedImageSourceClass.Animated ->
                return refuse(GPUPreparedImageRefusalCodes.ANIMATION, "sourceClass" to input.sourceClass)
            GPUPreparedImageSourceClass.Yuv ->
                return refuse(GPUPreparedImageRefusalCodes.YUV_CONVERSION, "sourceClass" to input.sourceClass)
            GPUPreparedImageSourceClass.Hdr ->
                return refuse(GPUPreparedImageRefusalCodes.HDR_TRANSFER, "sourceClass" to input.sourceClass)
            GPUPreparedImageSourceClass.Imported ->
                return refuse(
                    GPUPreparedImageRefusalCodes.TEXTURE_IMPORT_UNVALIDATED,
                    "sourceClass" to input.sourceClass,
                )
        }
        if (input.sourceFormat == GPUPreparedImageSourceFormat.Unsupported) {
            return refuse(
                GPUPreparedImageRefusalCodes.PIXEL_FORMAT,
                "sourceFormat" to input.sourceFormat,
            )
        }
        when (input.profile) {
            GPUPreparedImageProfile.Srgb -> Unit
            GPUPreparedImageProfile.Other ->
                return refuse(GPUPreparedImageRefusalCodes.GAMUT_TRANSFORM, "profile" to input.profile)
            GPUPreparedImageProfile.Unresolved ->
                return refuse(
                    GPUPreparedImageRefusalCodes.IMAGE_PROFILE_CONVERSION,
                    "profile" to input.profile,
                )
        }
        if (input.orientation != GPUPreparedImageOrientation.AppliedIdentity) {
            return refuse(
                GPUPreparedImageRefusalCodes.ORIENTATION,
                "orientation" to input.orientation,
            )
        }
        if (input.alphaType == AlphaType.UNPREMUL || input.alphaType == AlphaType.UNKNOWN) {
            return refuse(
                GPUPreparedImageRefusalCodes.ALPHA_INTERPRETATION,
                "alphaType" to input.alphaType,
            )
        }
        if (input.sourceFormat == GPUPreparedImageSourceFormat.A8 &&
            input.alphaType != AlphaType.PREMUL
        ) {
            return refuse(
                GPUPreparedImageRefusalCodes.ALPHA_INTERPRETATION,
                "alphaType" to input.alphaType,
            )
        }
        if (input.width <= 0 || input.height <= 0) {
            return refuse(
                GPUPreparedImageRefusalCodes.DIMENSIONS,
                "width" to input.width,
                "height" to input.height,
            )
        }
        if (input.sourceGeneration < 0L) {
            return refuse(
                GPUPreparedImageRefusalCodes.NATIVE_GENERATION,
                "generation" to input.sourceGeneration,
            )
        }

        val bytesPerPixel = when (input.sourceFormat) {
            GPUPreparedImageSourceFormat.Rgba8, GPUPreparedImageSourceFormat.Bgra8 -> 4L
            GPUPreparedImageSourceFormat.A8 -> 1L
            GPUPreparedImageSourceFormat.Unsupported -> error("classified above")
        }
        val sourceTightRowBytes = try { Math.multiplyExact(input.width.toLong(), bytesPerPixel) } catch (_: ArithmeticException) {
            return refuse(GPUPreparedImageRefusalCodes.DIMENSIONS, "width" to input.width)
        }
        if (input.sourceRowBytes < sourceTightRowBytes) {
            return refuse(
                GPUPreparedImageRefusalCodes.PIXEL_ROW_STRIDE,
                "sourceRowBytes" to input.sourceRowBytes,
            )
        }
        if (bytes == null) {
            return refuse(GPUPreparedImageRefusalCodes.PIXELS_MISSING)
        }
        val expectedLength = try { Math.multiplyExact(input.sourceRowBytes, input.height.toLong()) } catch (_: ArithmeticException) {
            return refuse(
                GPUPreparedImageRefusalCodes.PIXEL_LENGTH,
                "sourceRowBytes" to input.sourceRowBytes,
                "height" to input.height,
            )
        }
        if (expectedLength > Int.MAX_VALUE || bytes.size.toLong() != expectedLength) {
            return refuse(
                GPUPreparedImageRefusalCodes.PIXEL_LENGTH,
                "expectedLength" to expectedLength,
                "actualLength" to bytes.size,
            )
        }
        val normalizedRowBytes = try { Math.multiplyExact(input.width.toLong(), 4L) } catch (_: ArithmeticException) {
            return refuse(GPUPreparedImageRefusalCodes.DIMENSIONS, "width" to input.width)
        }
        val normalizedLength = try { Math.multiplyExact(normalizedRowBytes, input.height.toLong()) } catch (_: ArithmeticException) {
            return refuse(
                GPUPreparedImageRefusalCodes.DIMENSIONS,
                "width" to input.width,
                "height" to input.height,
            )
        }
        if (normalizedLength > maxUploadBytes || normalizedLength > Int.MAX_VALUE) {
            return refuse(
                GPUPreparedImageRefusalCodes.UPLOAD_BUDGET_EXCEEDED,
                "uploadBytes" to normalizedLength,
                "maxUploadBytes" to maxUploadBytes,
            )
        }
        if (input.alphaType == AlphaType.OPAQUE && !opaqueAlphaBytes(input, bytes)) {
            return refuse(
                GPUPreparedImageRefusalCodes.ALPHA_INTERPRETATION,
                "alphaType" to input.alphaType,
            )
        }

        val normalized = ByteArray(normalizedLength.toInt())
        for (row in 0 until input.height) {
            val sourceOffset = Math.multiplyExact(row.toLong(), input.sourceRowBytes).toInt()
            val targetOffset = Math.multiplyExact(row.toLong(), normalizedRowBytes).toInt()
            for (column in 0 until input.width) {
                val source = sourceOffset + (column * bytesPerPixel.toInt())
                val target = targetOffset + (column * 4)
                when (input.sourceFormat) {
                    GPUPreparedImageSourceFormat.Rgba8 -> bytes.copyInto(normalized, target, source, source + 4)
                    GPUPreparedImageSourceFormat.Bgra8 -> {
                        normalized[target] = bytes[source + 2]
                        normalized[target + 1] = bytes[source + 1]
                        normalized[target + 2] = bytes[source]
                        normalized[target + 3] = bytes[source + 3]
                    }
                    GPUPreparedImageSourceFormat.A8 -> {
                        val alpha = bytes[source]
                        normalized[target] = alpha
                        normalized[target + 1] = alpha
                        normalized[target + 2] = alpha
                        normalized[target + 3] = alpha
                    }
                    GPUPreparedImageSourceFormat.Unsupported -> error("classified above")
                }
            }
        }
        val alphaOnly = input.sourceFormat == GPUPreparedImageSourceFormat.A8
        if (!alphaOnly) {
            for (pixelOffset in normalized.indices step 4) {
                val alpha = normalized[pixelOffset + 3].toInt() and 0xFF
                for (channel in 0..2) {
                    val premultiplied = normalized[pixelOffset + channel].toInt() and 0xFF
                    normalized[pixelOffset + channel] = when (alpha) {
                        0 -> 0
                        else -> ((premultiplied * 255 + alpha / 2) / alpha)
                            .coerceIn(0, 255)
                            .toByte()
                    }
                }
            }
        }
        val contract = preparedSdrColorContract()
        val uploadEncoding = if (alphaOnly) null else contract.colorUploadEncoding
        val uploadInterpretation = if (alphaOnly) {
            GPUColorInterpretation.LinearPremul
        } else {
            GPUColorInterpretation.StraightEncodedSrgb
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(normalized).joinToString("") { "%02x".format(it) }
        val layout = GPUPreparedImagePixelLayout(input.sourceRowBytes, normalizedRowBytes, input.height)
        val key = GPUImageUploadArtifactKey(
            listOf(
                "prepared-image-v1",
                hash,
                input.width,
                input.height,
                input.sourceRowBytes,
                normalizedRowBytes,
                input.sourceFormat,
                input.alphaType,
                input.profile,
                input.orientation,
                input.provenance,
                input.sourceGeneration,
                uploadEncoding?.name ?: "CoverageLinear",
            ).joinToString("|"),
        )
        return GPUPreparedImageArtifactResult.Ready(
            GPUPreparedImageUploadArtifact(
                key,
                input.width,
                input.height,
                layout,
                input.sourceGeneration,
                hash,
                alphaOnly,
                GPUColorInterpretation.EncodedPremulSrgb.value,
                uploadEncoding,
                uploadInterpretation.value,
                normalized,
            ),
        )
    }

    private fun opaqueAlphaBytes(input: GPUPreparedImageSourceInput, bytes: ByteArray): Boolean {
        if (input.sourceFormat == GPUPreparedImageSourceFormat.A8) return false
        for (row in 0 until input.height) {
            val rowStart = Math.multiplyExact(row.toLong(), input.sourceRowBytes).toInt()
            for (column in 0 until input.width) if ((bytes[rowStart + column * 4 + 3].toInt() and 0xFF) != 255) return false
        }
        return true
    }
}
