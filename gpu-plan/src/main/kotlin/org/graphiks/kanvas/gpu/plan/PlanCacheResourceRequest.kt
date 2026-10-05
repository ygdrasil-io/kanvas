package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.ImmutableBytes
import org.graphiks.kanvas.render.ir.RuntimeSamplerTypeV1

public enum class ImagePhysicalFormatV1(public val bytesPerPixelI32: Int) { RGBA8_UNORM(4), R8_UNORM(1) }

internal fun alignRuntimeStorageBytesI64(bytesI64: Long): Long = maxOf(4L,bytesI64).let {
    Math.addExact(it,(4L-it%4L)%4L)
}

/** Typed physical allocation request, with captured ownership and no native handles. */
public sealed class PlanCacheResourceRequest {
    public abstract val canonicalPhysicalIdentity: String
    public abstract val byteSizeI64: Long
    public val abiVersionI32: Int = 1
    public val lifetime: PlanResourceLifetime = PlanResourceLifetime.DeviceSessionCache

    public class Texture internal constructor(
        override val canonicalPhysicalIdentity: String,
        public val format: ImagePhysicalFormatV1,
        public val widthI32: Int,
        public val heightI32: Int,
        override val byteSizeI64: Long,
        bytes: ByteArray,
    ) : PlanCacheResourceRequest() {
        private val bytes = bytes.copyOf()
        public fun copyUploadBytes(): ByteArray = bytes.copyOf()
        public val kind: PlanResourceKind = PlanResourceKind.Texture2D
        public fun usages(): Set<PlanResourceUsage> = setOf(PlanResourceUsage.Sampled, PlanResourceUsage.CopyDestination)
        init {
            require(canonicalPhysicalIdentity.isNotBlank() && widthI32 > 0 && heightI32 > 0)
            require(byteSizeI64 == checkedTextureBytesI64(format.bytesPerPixelI32, widthI32, heightI32, 1))
            require(bytes.size.toLong() == byteSizeI64)
        }
    }

    public class Storage internal constructor(public val abiHash: String, public val logicalSlotI32: Int,
        internal val captured: ImmutableBytes) : PlanCacheResourceRequest() {
        public val byteCountI64: Long = captured.sizeBytesI32.toLong()
        override val byteSizeI64: Long = alignRuntimeStorageBytesI64(byteCountI64)
        public val contentSha256: String = java.security.MessageDigest.getInstance("SHA-256")
            .digest(captured.copyToByteArray()).joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') }
        override val canonicalPhysicalIdentity: String = org.graphiks.kanvas.render.ir.CanonicalHashBytesV1("kanvas-runtime-storage-v1")
            .text(abiHash).i32(logicalSlotI32).text(contentSha256).i64(byteCountI64).sha256Hex()
        public fun copyUploadBytes(): ByteArray = captured.copyToByteArray().copyOf(Math.toIntExact(byteSizeI64))
        init { require(abiHash.matches(Regex("[0-9a-f]{64}")) && logicalSlotI32 >= 0 && byteCountI64 > 0) }
    }

    public class Sampler internal constructor(public val type: RuntimeSamplerTypeV1) : PlanCacheResourceRequest() {
        override val canonicalPhysicalIdentity: String = "runtime-sampler-v1:${type.name}"
        override val byteSizeI64: Long = 0L
    }

    public companion object {
        public const val MAX_RUNTIME_ENTRIES_I32: Int = 128
        public const val MAX_RUNTIME_BYTES_I64: Long = 64L * 1024L * 1024L
        public const val MAX_RUNTIME_LEASES_I32: Int = 4096
    }
}
