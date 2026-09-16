package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.*
import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterialTextureUploadV1

/** One physical texture/view cache per device generation, independent of target and frame. */
internal class GPUW5eDecodedImageSessionCache(
    private val device: GPUDevice,
    private val queue: GPUQueue,
    val deviceGenerationI64: Long,
    private val rowAlignmentI64: Long,
    private val maxBufferBytesI64: Long,
    private val maxEntriesI32: Int = 128,
    private val maxBytesI64: Long = 64L * 1024L * 1024L,
) : AutoCloseable {
    internal class Entry(val request: PlanCacheResourceRequest.Texture, private var texture: GPUTexture?, var view: GPUTextureView?) : AutoCloseable {
        var leasesI32: Int = 0
        override fun close() {
            view?.close()
            view = null
            texture?.close()
            texture = null
        }
    }
    internal class Lease(private val owner: GPUW5eDecodedImageSessionCache, private val entry: Entry) : AutoCloseable {
        private var released = false
        val view: GPUTextureView get() = synchronized(owner) { check(!released && !owner.closing); requireNotNull(entry.view) }
        val generationI64: Long get() = owner.deviceGenerationI64
        fun matches(request: PlanCacheResourceRequest.Texture, generationI64: Long): Boolean = synchronized(owner) {
            !released && !owner.closing && entry.view != null && owner.deviceGenerationI64 == generationI64 &&
                entry.request.canonicalPhysicalIdentity == request.canonicalPhysicalIdentity &&
                entry.request.widthI32 == request.widthI32 && entry.request.heightI32 == request.heightI32 &&
                entry.request.format == request.format && entry.request.byteSizeI64 == request.byteSizeI64
        }
        override fun close() = synchronized(owner) {
            if (!released) { check(entry.leasesI32 > 0); entry.leasesI32--; released = true }
            owner.closeRetiredWhenUnleased()
        }
    }
    private val entries = LinkedHashMap<String, Entry>(16, .75f, true)
    private val quarantine = mutableListOf<Entry>()
    private var closing = false
    var isClosed: Boolean = false
        private set
    init { require(maxEntriesI32 > 0 && maxBytesI64 > 0L && deviceGenerationI64 >= 0L) }

    /** Queue failure retires the generation before callbacks release any still-in-flight leases. */
    @Synchronized fun retireGeneration() {
        closing = true
        closeRetiredWhenUnleased()
    }

    private fun closeRetiredWhenUnleased() {
        if (closing && entries.values.all { it.leasesI32 == 0 }) close()
    }

    @Synchronized fun acquire(request: PlanCacheResourceRequest.Texture): Lease {
        check(!closing && !isClosed) { "stale.material.image.device-generation" }
        require(request.kind == PlanResourceKind.Texture2D && request.abiVersionI32 == 1 &&
            request.lifetime == PlanResourceLifetime.DeviceSessionCache &&
            request.usages() == setOf(PlanResourceUsage.Sampled, PlanResourceUsage.CopyDestination)) { W5eImagePlanDiagnostics.InvalidContract }
        val key = "$deviceGenerationI64:${request.canonicalPhysicalIdentity}"
        entries[key]?.let { entry ->
            require(entry.request.format == request.format && entry.request.widthI32 == request.widthI32 &&
                entry.request.heightI32 == request.heightI32 && entry.request.byteSizeI64 == request.byteSizeI64)
            entry.leasesI32 = Math.addExact(entry.leasesI32, 1)
            return Lease(this, entry)
        }
        require(request.byteSizeI64 <= maxBytesI64) { "resource.material.image.cache-budget" }
        fun residentI64(): Long = (entries.values + quarantine).fold(0L) { total, entry -> Math.addExact(total, entry.request.byteSizeI64) }
        while (entries.size + quarantine.size >= maxEntriesI32 || Math.addExact(residentI64(), request.byteSizeI64) > maxBytesI64) {
            val victim = entries.entries.firstOrNull { it.value.leasesI32 == 0 }
                ?: throw IllegalStateException("resource.material.image.cache-budget")
            try { victim.value.close() } catch (failure: Throwable) {
                // A partly closed victim can never become a cache hit again. Keep its
                // residual ownership and capacity charge until session teardown retries it.
                entries.remove(victim.key)
                quarantine += victim.value
                throw failure
            }
            entries.remove(victim.key)
        }
        val upload = GPUMaterialTextureUploadV1.of(request.widthI32, request.heightI32, request.format.bytesPerPixelI32,
            request.copyUploadBytes(), rowAlignmentI64, maxBufferBytesI64)
        val format = when (request.format) { ImagePhysicalFormatV1.RGBA8_UNORM -> GPUTextureFormat.RGBA8Unorm; ImagePhysicalFormatV1.R8_UNORM -> GPUTextureFormat.R8Unorm }
        val texture = device.createTexture(TextureDescriptor(size = Extent3D(request.widthI32.toUInt(), request.heightI32.toUInt(), 1u),
            format = format, usage = GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding,
            mipLevelCount = 1u, sampleCount = 1u, label = "Kanvas.w5e.decoded-image-v1"))
        val entry = Entry(request, texture, null)
        try {
            entry.view = texture.createView(TextureViewDescriptor(format = format, dimension = GPUTextureViewDimension.TwoD,
                usage = GPUTextureUsage.TextureBinding, baseMipLevel = 0u, mipLevelCount = 1u, baseArrayLayer = 0u, arrayLayerCount = 1u))
            queue.writeTexture(TexelCopyTextureInfo(texture = texture), ArrayBuffer.of(upload.copyBytes()),
                TexelCopyBufferLayout(offset = 0uL, bytesPerRow = upload.bytesPerRowI64.toUInt(), rowsPerImage = request.heightI32.toUInt()),
                Extent3D(request.widthI32.toUInt(), request.heightI32.toUInt(), 1u))
            entry.leasesI32 = 1
            entries[key] = entry
            return Lease(this, entry)
        } catch (failure: Throwable) {
            try { entry.close() } catch (cleanup: Throwable) { quarantine += entry; failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    @Synchronized override fun close() {
        closing = true
        if (isClosed) return
        check(entries.values.all { it.leasesI32 == 0 }) { "W5e cache still has GPU completion leases" }
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) { iterator.next().value.close(); iterator.remove() }
        val pending = quarantine.iterator()
        while (pending.hasNext()) { pending.next().close(); pending.remove() }
        isClosed = true
    }
}
