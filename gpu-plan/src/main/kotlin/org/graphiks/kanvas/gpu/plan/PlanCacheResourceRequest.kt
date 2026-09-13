package org.graphiks.kanvas.gpu.plan

public enum class ImagePhysicalFormatV1(public val bytesPerPixelI32: Int) { RGBA8_UNORM(4), R8_UNORM(1) }

/** Physical allocation identity, with no sampling policy and no native handle. */
public class PlanCacheResourceRequest internal constructor(
    public val canonicalPhysicalIdentity: String,
    public val format: ImagePhysicalFormatV1,
    public val widthI32: Int,
    public val heightI32: Int,
    public val byteSizeI64: Long,
    bytes: ByteArray,
) {
    private val bytes = bytes.copyOf()
    public fun copyUploadBytes(): ByteArray = bytes.copyOf()
    public val kind: PlanResourceKind = PlanResourceKind.Texture2D
    public val abiVersionI32: Int = 1
    public val lifetime: PlanResourceLifetime = PlanResourceLifetime.DeviceSessionCache
    public fun usages(): Set<PlanResourceUsage> = setOf(PlanResourceUsage.Sampled, PlanResourceUsage.CopyDestination)
    init {
        require(canonicalPhysicalIdentity.isNotBlank() && widthI32 > 0 && heightI32 > 0)
        require(byteSizeI64 == checkedTextureBytesI64(format.bytesPerPixelI32, widthI32, heightI32, 1))
        require(bytes.size.toLong() == byteSizeI64)
    }
}
