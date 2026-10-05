package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.*
import org.graphiks.kanvas.gpu.plan.PlanCacheResourceRequest
import org.graphiks.kanvas.render.ir.RuntimeSamplerTypeV1

/** Generation-scoped bounded storage/sampler ownership. Textures remain in the decoded-image cache. */
internal class GPUW5hRuntimeResourceSessionCache(
    private val device: GPUDevice,
    private val queue: GPUQueue,
    val deviceGenerationI64: Long,
) : AutoCloseable {
    private data class Key(val deviceGenerationI64: Long,val resourceIdentity: String)
    internal class Entry(val request: PlanCacheResourceRequest,var buffer: GPUBuffer? = null,var sampler: GPUSampler? = null) : AutoCloseable {
        var leasesI32=0
        override fun close() {
            buffer?.close(); buffer=null
            sampler?.close(); sampler=null
        }
    }
    internal class Lease(private val owner: GPUW5hRuntimeResourceSessionCache,private val entry: Entry) : AutoCloseable {
        private var released=false
        fun matches(request: PlanCacheResourceRequest,generationI64: Long): Boolean = synchronized(owner) {
            !released && !owner.closing && owner.deviceGenerationI64 == generationI64 &&
                entry.request.canonicalPhysicalIdentity == request.canonicalPhysicalIdentity &&
                entry.request.byteSizeI64 == request.byteSizeI64
        }
        fun binding(bindingI32: Int): BindGroupEntry = synchronized(owner) {
            check(!released && !owner.closing)
            when(entry.request) {
                is PlanCacheResourceRequest.Storage -> BindGroupEntry(binding=bindingI32.toUInt(),resource=BufferBinding(
                    buffer=requireNotNull(entry.buffer),offset=0uL,size=entry.request.byteSizeI64.toULong()))
                is PlanCacheResourceRequest.Sampler -> BindGroupEntry(binding=bindingI32.toUInt(),resource=requireNotNull(entry.sampler))
                is PlanCacheResourceRequest.Texture -> error("Texture ownership belongs to the decoded-image cache")
            }
        }
        override fun close() = synchronized(owner) {
            if(!released) { check(entry.leasesI32 > 0); entry.leasesI32--; released=true }
            owner.closeRetiredWhenUnleased()
        }
    }
    private val entries=LinkedHashMap<Key,Entry>(16,.75f,true)
    private val quarantine=mutableListOf<Entry>()
    private var closing=false
    var isClosed=false
        private set
    init { require(deviceGenerationI64 >= 0L) }
    @Synchronized fun retireGeneration() { closing=true; closeRetiredWhenUnleased() }
    private fun closeRetiredWhenUnleased() { if(closing && entries.values.all { it.leasesI32 == 0 }) close() }

    /** Called only by the post-witness materializer; each call issues a distinct completion lease. */
    @Synchronized fun acquire(request: PlanCacheResourceRequest,generationI64: Long): Lease {
        check(!closing && !isClosed && generationI64 == deviceGenerationI64) { "stale.material.runtime_effect.device-generation" }
        require(request is PlanCacheResourceRequest.Storage || request is PlanCacheResourceRequest.Sampler)
        require(entries.values.sumOf { it.leasesI32.toLong() } < PlanCacheResourceRequest.MAX_RUNTIME_LEASES_I32) {
            "resource.material.runtime_effect.cache-leases"
        }
        val key=Key(deviceGenerationI64,request.canonicalPhysicalIdentity)
        entries[key]?.let { entry ->
            require(entry.request.byteSizeI64 == request.byteSizeI64 && entry.request::class == request::class)
            entry.leasesI32=Math.addExact(entry.leasesI32,1)
            return Lease(this,entry)
        }
        require(request.byteSizeI64 <= PlanCacheResourceRequest.MAX_RUNTIME_BYTES_I64)
        fun residentBytesI64(): Long=(entries.values+quarantine).fold(0L) { bytes,entry -> Math.addExact(bytes,entry.request.byteSizeI64) }
        while(entries.size+quarantine.size >= PlanCacheResourceRequest.MAX_RUNTIME_ENTRIES_I32 ||
            Math.addExact(residentBytesI64(),request.byteSizeI64) > PlanCacheResourceRequest.MAX_RUNTIME_BYTES_I64) {
            val victim=entries.entries.firstOrNull { it.value.leasesI32 == 0 }
                ?: error("resource.material.runtime_effect.cache-budget")
            try { victim.value.close() } catch(failure: Throwable) {
                entries.remove(victim.key); quarantine+=victim.value; throw failure
            }
            entries.remove(victim.key)
        }
        val entry=Entry(request)
        try {
            when(request) {
                is PlanCacheResourceRequest.Storage -> {
                    val buffer=device.createBuffer(BufferDescriptor(label="Kanvas.w5h.runtime-storage-v1",
                        size=request.byteSizeI64.toULong(),usage=GPUBufferUsage.Storage or GPUBufferUsage.CopyDst))
                    entry.buffer=buffer
                    val bytes=request.copyUploadBytes()
                    queue.writeBuffer(buffer,0uL,ArrayBuffer.of(bytes),0uL,bytes.size.toULong())
                }
                is PlanCacheResourceRequest.Sampler -> {
                    val mode=if(request.type == RuntimeSamplerTypeV1.FILTERING) GPUFilterMode.Linear else GPUFilterMode.Nearest
                    entry.sampler=device.createSampler(SamplerDescriptor(label="Kanvas.w5h.runtime-sampler-v1",
                        magFilter=mode,minFilter=mode))
                }
                is PlanCacheResourceRequest.Texture -> error("Texture ownership belongs to the decoded-image cache")
            }
            entry.leasesI32=1; entries[key]=entry
            return Lease(this,entry)
        } catch(failure: Throwable) {
            try { entry.close() } catch(cleanup: Throwable) { quarantine+=entry; failure.addSuppressed(cleanup) }
            throw failure
        }
    }
    @Synchronized override fun close() {
        closing=true
        if(isClosed) return
        check(entries.values.all { it.leasesI32 == 0 }) { "W5h cache still has GPU completion leases" }
        val live=entries.entries.iterator()
        while(live.hasNext()) { live.next().value.close(); live.remove() }
        val pending=quarantine.iterator()
        while(pending.hasNext()) { pending.next().close(); pending.remove() }
        isClosed=true
    }
}
