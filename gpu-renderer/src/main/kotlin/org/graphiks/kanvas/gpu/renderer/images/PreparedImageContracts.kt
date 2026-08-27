package org.graphiks.kanvas.gpu.renderer.images

import org.graphiks.kanvas.gpu.renderer.artifacts.GPUImageUploadArtifactKey as ArtifactKey
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImagePixelLayout as ArtifactPixelLayout
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImageUploadArtifact as PreparedUploadArtifact
import org.graphiks.kanvas.gpu.renderer.artifacts.preparedSdrColorContract as artifactSdrColorContract
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes as CanonicalRefusalCodes
import java.security.MessageDigest

enum class AlphaType { OPAQUE, PREMUL, UNPREMUL, UNKNOWN }
enum class GPUPreparedImageSourceClass { DecodedCpu, Encoded, Animated, Yuv, Hdr, Imported }
enum class GPUPreparedImageSourceFormat { Rgba8, Bgra8, A8, Rgb565, Argb4444, RgbaF16, Gray8, Unsupported }
enum class GPUPreparedImageProfile { Srgb, Other, Unresolved }
enum class GPUPreparedImageOrientation { AppliedIdentity, Unresolved }
enum class GPUPreparedImageProvenance { CallerPixels, SurfaceReadback, RegisteredDecode }

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

sealed interface GPUPreparedImageArtifactResult {
    data class Ready(val artifact: PreparedUploadArtifact) : GPUPreparedImageArtifactResult
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
                return refuse(CanonicalRefusalCodes.CODEC_UNREGISTERED, "sourceClass" to input.sourceClass)
            GPUPreparedImageSourceClass.Animated ->
                return refuse(CanonicalRefusalCodes.ANIMATION, "sourceClass" to input.sourceClass)
            GPUPreparedImageSourceClass.Yuv ->
                return refuse(CanonicalRefusalCodes.YUV_CONVERSION, "sourceClass" to input.sourceClass)
            GPUPreparedImageSourceClass.Hdr ->
                return refuse(CanonicalRefusalCodes.HDR_TRANSFER, "sourceClass" to input.sourceClass)
            GPUPreparedImageSourceClass.Imported ->
                return refuse(
                    CanonicalRefusalCodes.TEXTURE_IMPORT_UNVALIDATED,
                    "sourceClass" to input.sourceClass,
                )
        }
        if (input.sourceFormat == GPUPreparedImageSourceFormat.Unsupported) {
            return refuse(
                CanonicalRefusalCodes.PIXEL_FORMAT,
                "sourceFormat" to input.sourceFormat,
            )
        }
        when (input.profile) {
            GPUPreparedImageProfile.Srgb -> Unit
            GPUPreparedImageProfile.Other ->
                return refuse(CanonicalRefusalCodes.GAMUT_TRANSFORM, "profile" to input.profile)
            GPUPreparedImageProfile.Unresolved ->
                return refuse(
                    CanonicalRefusalCodes.IMAGE_PROFILE_CONVERSION,
                    "profile" to input.profile,
                )
        }
        if (input.orientation != GPUPreparedImageOrientation.AppliedIdentity) {
            return refuse(
                CanonicalRefusalCodes.ORIENTATION,
                "orientation" to input.orientation,
            )
        }
        if (input.alphaType == AlphaType.UNKNOWN) {
            return refuse(
                CanonicalRefusalCodes.ALPHA_INTERPRETATION,
                "alphaType" to input.alphaType,
            )
        }
        if (input.sourceFormat == GPUPreparedImageSourceFormat.A8 &&
            input.alphaType != AlphaType.PREMUL
        ) {
            return refuse(
                CanonicalRefusalCodes.ALPHA_INTERPRETATION,
                "alphaType" to input.alphaType,
            )
        }
        if (input.sourceFormat in setOf(
                GPUPreparedImageSourceFormat.Rgb565,
                GPUPreparedImageSourceFormat.Gray8,
            ) && input.alphaType != AlphaType.OPAQUE
        ) {
            return refuse(
                CanonicalRefusalCodes.ALPHA_INTERPRETATION,
                "alphaType" to input.alphaType,
            )
        }
        if (input.sourceFormat in setOf(
                GPUPreparedImageSourceFormat.Argb4444,
                GPUPreparedImageSourceFormat.RgbaF16,
            ) && input.alphaType != AlphaType.PREMUL
        ) {
            return refuse(
                CanonicalRefusalCodes.ALPHA_INTERPRETATION,
                "alphaType" to input.alphaType,
            )
        }
        if (input.width <= 0 || input.height <= 0) {
            return refuse(
                CanonicalRefusalCodes.DIMENSIONS,
                "width" to input.width,
                "height" to input.height,
            )
        }
        if (input.sourceGeneration < 0L) {
            return refuse(
                CanonicalRefusalCodes.NATIVE_GENERATION,
                "generation" to input.sourceGeneration,
            )
        }

        val bytesPerPixel = when (input.sourceFormat) {
            GPUPreparedImageSourceFormat.Rgba8, GPUPreparedImageSourceFormat.Bgra8 -> 4L
            GPUPreparedImageSourceFormat.A8, GPUPreparedImageSourceFormat.Gray8 -> 1L
            GPUPreparedImageSourceFormat.Rgb565, GPUPreparedImageSourceFormat.Argb4444 -> 2L
            GPUPreparedImageSourceFormat.RgbaF16 -> 8L
            GPUPreparedImageSourceFormat.Unsupported -> error("classified above")
        }
        val sourceTightRowBytes = try { Math.multiplyExact(input.width.toLong(), bytesPerPixel) } catch (_: ArithmeticException) {
            return refuse(CanonicalRefusalCodes.DIMENSIONS, "width" to input.width)
        }
        if (input.sourceRowBytes < sourceTightRowBytes) {
            return refuse(
                CanonicalRefusalCodes.PIXEL_ROW_STRIDE,
                "sourceRowBytes" to input.sourceRowBytes,
            )
        }
        if (bytes == null) {
            return refuse(CanonicalRefusalCodes.PIXELS_MISSING)
        }
        val expectedLength = try { Math.multiplyExact(input.sourceRowBytes, input.height.toLong()) } catch (_: ArithmeticException) {
            return refuse(
                CanonicalRefusalCodes.PIXEL_LENGTH,
                "sourceRowBytes" to input.sourceRowBytes,
                "height" to input.height,
            )
        }
        if (expectedLength > Int.MAX_VALUE || bytes.size.toLong() != expectedLength) {
            return refuse(
                CanonicalRefusalCodes.PIXEL_LENGTH,
                "expectedLength" to expectedLength,
                "actualLength" to bytes.size,
            )
        }
        val normalizedRowBytes = try { Math.multiplyExact(input.width.toLong(), 4L) } catch (_: ArithmeticException) {
            return refuse(CanonicalRefusalCodes.DIMENSIONS, "width" to input.width)
        }
        val normalizedLength = try { Math.multiplyExact(normalizedRowBytes, input.height.toLong()) } catch (_: ArithmeticException) {
            return refuse(
                CanonicalRefusalCodes.DIMENSIONS,
                "width" to input.width,
                "height" to input.height,
            )
        }
        if (normalizedLength > maxUploadBytes || normalizedLength > Int.MAX_VALUE) {
            return refuse(
                CanonicalRefusalCodes.UPLOAD_BUDGET_EXCEEDED,
                "uploadBytes" to normalizedLength,
                "maxUploadBytes" to maxUploadBytes,
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
                    GPUPreparedImageSourceFormat.Rgb565 -> {
                        val packed = unsignedShortLe(bytes, source)
                        normalized[target] = ((packed ushr 11 and 0x1f) * 255 / 31).toByte()
                        normalized[target + 1] = ((packed ushr 5 and 0x3f) * 255 / 63).toByte()
                        normalized[target + 2] = ((packed and 0x1f) * 255 / 31).toByte()
                        normalized[target + 3] = 0xff.toByte()
                    }
                    GPUPreparedImageSourceFormat.Argb4444 -> {
                        val packed = unsignedShortLe(bytes, source)
                        val alpha = (packed ushr 12 and 0x0f) / 15f
                        normalized[target + 3] = unitFloatToByte(alpha)
                        if (alpha > 0f) {
                            normalized[target] = unitFloatToByte((packed ushr 8 and 0x0f) / 15f / alpha)
                            normalized[target + 1] = unitFloatToByte((packed ushr 4 and 0x0f) / 15f / alpha)
                            normalized[target + 2] = unitFloatToByte((packed and 0x0f) / 15f / alpha)
                        }
                    }
                    GPUPreparedImageSourceFormat.RgbaF16 -> {
                        val alpha = halfToUnitFloat(unsignedShortLe(bytes, source + 6))
                        normalized[target + 3] = unitFloatToByte(alpha)
                        if (alpha > 0f) {
                            normalized[target] = unitFloatToByte(halfToUnitFloat(unsignedShortLe(bytes, source)) / alpha)
                            normalized[target + 1] = unitFloatToByte(halfToUnitFloat(unsignedShortLe(bytes, source + 2)) / alpha)
                            normalized[target + 2] = unitFloatToByte(halfToUnitFloat(unsignedShortLe(bytes, source + 4)) / alpha)
                        }
                    }
                    GPUPreparedImageSourceFormat.Gray8 -> {
                        val gray = bytes[source]
                        normalized[target] = gray
                        normalized[target + 1] = gray
                        normalized[target + 2] = gray
                        normalized[target + 3] = 0xff.toByte()
                    }
                    GPUPreparedImageSourceFormat.Unsupported -> error("classified above")
                }
            }
        }
        val alphaOnly = input.sourceFormat == GPUPreparedImageSourceFormat.A8
        val decodedStraight = input.sourceFormat in setOf(
            GPUPreparedImageSourceFormat.Argb4444,
            GPUPreparedImageSourceFormat.RgbaF16,
        )
        if (input.alphaType == AlphaType.PREMUL && !alphaOnly && !decodedStraight) {
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
        if (input.alphaType == AlphaType.OPAQUE && !opaqueAlphaBytes(normalized)) {
            return refuse(
                CanonicalRefusalCodes.ALPHA_INTERPRETATION,
                "alphaType" to input.alphaType,
            )
        }
        val contract = artifactSdrColorContract()
        val uploadEncoding = if (alphaOnly) null else contract.colorUploadEncoding
        val uploadInterpretation = if (alphaOnly) {
            GPUColorInterpretation.LinearPremul
        } else {
            GPUColorInterpretation.StraightEncodedSrgb
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(normalized).joinToString("") { "%02x".format(it) }
        val layout = ArtifactPixelLayout(input.sourceRowBytes, normalizedRowBytes, input.height)
        val key = ArtifactKey(
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
            PreparedUploadArtifact(
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

    private fun opaqueAlphaBytes(rgba: ByteArray): Boolean =
        (3 until rgba.size step 4).all { (rgba[it].toInt() and 0xFF) == 255 }

    private fun unsignedShortLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun unitFloatToByte(value: Float): Byte =
        (value.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()

    private fun halfToUnitFloat(bits: Int): Float {
        val sign = bits ushr 15
        val exponent = bits ushr 10 and 0x1f
        val fraction = bits and 0x03ff
        val value = when (exponent) {
            0 -> fraction * 5.9604645e-8f
            31 -> if (fraction == 0) Float.POSITIVE_INFINITY else Float.NaN
            else -> Math.scalb(1f + fraction / 1024f, exponent - 15)
        }
        return if (sign == 0) value else -value
    }
}
