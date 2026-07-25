package org.graphiks.kanvas.gpu.renderer.images

import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import java.security.MessageDigest

enum class AlphaType { OPAQUE, PREMUL, UNPREMUL, UNKNOWN }
enum class GPUPreparedImageSourceClass { DecodedCpu, Encoded, Animated, Yuv, Hdr, Imported }
enum class GPUPreparedImageSourceFormat { Rgba8, Bgra8, A8, Unsupported }
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
    rgba8PremulBytes: ByteArray,
) {
    private val snapshot = rgba8PremulBytes.copyOf()
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
            GPUPreparedImageArtifactResult.Refused(code, facts.associate { it.first to it.second.toString() })

        if (input.sourceClass != GPUPreparedImageSourceClass.DecodedCpu) return refuse("image.source.class", "sourceClass" to input.sourceClass)
        if (input.sourceFormat == GPUPreparedImageSourceFormat.Unsupported) return refuse("image.format", "sourceFormat" to input.sourceFormat)
        if (input.profile != GPUPreparedImageProfile.Srgb) return refuse("image.profile", "profile" to input.profile)
        if (input.orientation != GPUPreparedImageOrientation.AppliedIdentity) return refuse("image.orientation", "orientation" to input.orientation)
        if (input.alphaType == AlphaType.UNPREMUL || input.alphaType == AlphaType.UNKNOWN) return refuse("image.alpha.unpremul", "alphaType" to input.alphaType)
        if (input.sourceFormat == GPUPreparedImageSourceFormat.A8 && input.alphaType != AlphaType.PREMUL) return refuse("image.alpha.a8_requires_premul", "alphaType" to input.alphaType)
        if (input.width <= 0 || input.height <= 0) return refuse("image.dimensions", "width" to input.width, "height" to input.height)
        if (input.sourceGeneration < 0L) return refuse("image.generation", "generation" to input.sourceGeneration)

        val bytesPerPixel = when (input.sourceFormat) {
            GPUPreparedImageSourceFormat.Rgba8, GPUPreparedImageSourceFormat.Bgra8 -> 4L
            GPUPreparedImageSourceFormat.A8 -> 1L
            GPUPreparedImageSourceFormat.Unsupported -> error("classified above")
        }
        val sourceTightRowBytes = try { Math.multiplyExact(input.width.toLong(), bytesPerPixel) } catch (_: ArithmeticException) {
            return refuse("image.dimensions", "width" to input.width)
        }
        if (input.sourceRowBytes < sourceTightRowBytes) return refuse("image.stride", "sourceRowBytes" to input.sourceRowBytes)
        val expectedLength = try { Math.multiplyExact(input.sourceRowBytes, input.height.toLong()) } catch (_: ArithmeticException) {
            return refuse("image.length", "sourceRowBytes" to input.sourceRowBytes, "height" to input.height)
        }
        if (expectedLength > Int.MAX_VALUE || bytes == null || bytes.size.toLong() != expectedLength) return refuse("image.length", "expectedLength" to expectedLength, "actualLength" to (bytes?.size ?: -1))
        val normalizedRowBytes = try { Math.multiplyExact(input.width.toLong(), 4L) } catch (_: ArithmeticException) {
            return refuse("image.dimensions", "width" to input.width)
        }
        val normalizedLength = try { Math.multiplyExact(normalizedRowBytes, input.height.toLong()) } catch (_: ArithmeticException) {
            return refuse("image.length", "width" to input.width, "height" to input.height)
        }
        if (normalizedLength > maxUploadBytes || normalizedLength > Int.MAX_VALUE) return refuse("image.budget", "uploadBytes" to normalizedLength, "maxUploadBytes" to maxUploadBytes)
        if (input.alphaType == AlphaType.OPAQUE && !opaqueAlphaBytes(input, bytes)) return refuse("image.alpha.opaque_bytes", "alphaType" to input.alphaType)

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
        val hash = MessageDigest.getInstance("SHA-256").digest(normalized).joinToString("") { "%02x".format(it) }
        val layout = GPUPreparedImagePixelLayout(input.sourceRowBytes, normalizedRowBytes, input.height)
        val key = GPUImageUploadArtifactKey(
            listOf("prepared-image-v1", hash, input.width, input.height, input.sourceRowBytes, normalizedRowBytes, input.sourceFormat, input.alphaType, input.profile, input.orientation, input.provenance, input.sourceGeneration).joinToString("|"),
        )
        return GPUPreparedImageArtifactResult.Ready(
            GPUPreparedImageUploadArtifact(key, input.width, input.height, layout, input.sourceGeneration, hash, input.sourceFormat == GPUPreparedImageSourceFormat.A8, GPUColorInterpretation.EncodedPremulSrgb.value, normalized),
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
