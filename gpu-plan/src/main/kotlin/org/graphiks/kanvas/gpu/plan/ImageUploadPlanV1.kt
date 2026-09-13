package org.graphiks.kanvas.gpu.plan

import java.security.MessageDigest
import org.graphiks.kanvas.render.ir.ImagePixelFormat
import org.graphiks.kanvas.render.ir.ImageResourceSnapshot

/** Canonical logical rows; neither source padding nor sampling participates in content identity. */
public class ImageUploadPlanV1 private constructor(
    public val widthI32: Int,
    public val heightI32: Int,
    public val logicalRowBytesI64: Long,
    public val sourceRowBytesI64: Long,
    public val logicalFormat: ImagePixelFormat,
    public val physicalFormat: ImagePhysicalFormatV1,
    bytes: ByteArray,
) {
    private val bytes = bytes.copyOf()
    public val byteCountI64: Long = this.bytes.size.toLong()
    public fun copyLogicalBytes(): ByteArray = bytes.copyOf()
    public val contentIdentity: String = run {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("w5e-upload-v1:$widthI32:$heightI32:$logicalRowBytesI64:${logicalFormat.name}:${physicalFormat.name}:".encodeToByteArray())
        for (rowI32 in 0 until heightI32) digest.update(this.bytes, Math.toIntExact(rowI32 * logicalRowBytesI64), logicalRowBytesI64.toInt())
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    public val cacheRequest: PlanCacheResourceRequest = PlanCacheResourceRequest(contentIdentity, physicalFormat,
        widthI32, heightI32, byteCountI64, this.bytes)

    internal companion object {
        fun seal(pixels: ImageResourceSnapshot.Pixels): ImageUploadPlanV1 {
            val format = pixels.pixelFormat
            require(format in setOf(ImagePixelFormat.RGBA_8888, ImagePixelFormat.BGRA_8888,
                ImagePixelFormat.SRGBA_8888, ImagePixelFormat.ALPHA_8)) { W5eImagePlanDiagnostics.Format }
            require(pixels.width > 0 && pixels.height > 0) { W5eImagePlanDiagnostics.Dimensions }
            val logicalI64 = try { Math.multiplyExact(pixels.width.toLong(), format.bytesPerPixel.toLong()) }
            catch (_: ArithmeticException) { throw IllegalArgumentException(W5eImagePlanDiagnostics.Overflow) }
            val sizeI64 = try { Math.multiplyExact(logicalI64, pixels.height.toLong()) }
            catch (_: ArithmeticException) { throw IllegalArgumentException(W5eImagePlanDiagnostics.Overflow) }
            require(pixels.rowBytes.toLong() >= logicalI64) { W5eImagePlanDiagnostics.Stride }
            require(sizeI64 <= Int.MAX_VALUE) { W5eImagePlanDiagnostics.Overflow }
            val source = pixels.copyPixels()
            val requiredPayloadI64 = try { Math.multiplyExact(pixels.rowBytes.toLong(), pixels.height.toLong()) }
            catch (_: ArithmeticException) { throw IllegalArgumentException(W5eImagePlanDiagnostics.Overflow) }
            require(source.size.toLong() >= requiredPayloadI64) { W5eImagePlanDiagnostics.Payload }
            val tight = ByteArray(sizeI64.toInt())
            for (rowI32 in 0 until pixels.height) source.copyInto(tight, Math.toIntExact(rowI32 * logicalI64),
                Math.multiplyExact(rowI32, pixels.rowBytes), Math.addExact(Math.multiplyExact(rowI32, pixels.rowBytes), logicalI64.toInt()))
            return ImageUploadPlanV1(pixels.width, pixels.height, logicalI64, pixels.rowBytes.toLong(),
                format, if (format == ImagePixelFormat.ALPHA_8) ImagePhysicalFormatV1.R8_UNORM else ImagePhysicalFormatV1.RGBA8_UNORM, tight)
        }
    }
}
