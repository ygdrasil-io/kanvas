package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.*
import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan

/** Session-scoped W6c target residency; preflight is its only policy decision point. */
internal class GPUW6cSpatialFilterSessionCache(
    private val device: GPUDevice,
    private val generationI64: Long,
) : AutoCloseable {
    internal class Entry(val key: SpatialFilterCacheKeyV1, val texture: GPUTexture, val view: GPUTextureView) {
        var consumersI32 = 0
        var reusable = false
        var quarantined = false
        fun close() { view.close(); texture.close() }
    }

    /** Immutable full-chain projection consumed by materialization without cache policy. */
    internal class Binding internal constructor(
        private val owner: GPUW6cSpatialFilterSessionCache,
        private val entries: Map<PlanResourceId, Entry>,
        private val missOutputs: Set<PlanResourceId>,
        private val reuseOutputs: Set<PlanResourceId>,
    ) : GPUPreparedNativeFrameLeaseLifecycle {
        private enum class State { CheckedOut, Submitted, Terminal }
        private var state = State.CheckedOut
        fun view(output: PlanResourceId): GPUTextureView = synchronized(owner) { entries.getValue(output).view }
        fun texture(output: PlanResourceId): GPUTexture = synchronized(owner) { entries.getValue(output).texture }
        fun usesCachedTarget(output: PlanResourceId): Boolean = output in entries
        fun skipsFilterPass(output: PlanResourceId): Boolean = output in reuseOutputs
        @Synchronized override fun releaseBeforeSubmit(): GPUPreparedNativeFrameLeaseTransition {
            if (state != State.CheckedOut) return refused()
            owner.release(entries.values)
            owner.discardUnsubmitted(entries.filterKeys { it in missOutputs }.values)
            state = State.Terminal
            return GPUPreparedNativeFrameLeaseTransition.Applied
        }
        @Synchronized override fun markSubmitted(): GPUPreparedNativeFrameLeaseTransition {
            if (state != State.CheckedOut) return refused()
            state = State.Submitted
            return GPUPreparedNativeFrameLeaseTransition.Applied
        }
        @Synchronized override fun releaseAfterCompletion(): GPUPreparedNativeFrameLeaseTransition {
            if (state != State.Submitted) return refused()
            owner.promote(entries.filterKeys { it in missOutputs }.values)
            owner.release(entries.values)
            state = State.Terminal
            return GPUPreparedNativeFrameLeaseTransition.Applied
        }
        @Synchronized override fun quarantineUncertain(): GPUPreparedNativeFrameLeaseTransition {
            if (state == State.Terminal) return refused()
            owner.quarantine(entries.filterKeys { it in missOutputs }.values)
            owner.release(entries.values)
            state = State.Terminal
            return GPUPreparedNativeFrameLeaseTransition.Applied
        }
        private fun refused() = GPUPreparedNativeFrameLeaseTransition.Refused("invalid-w6c-spatial-binding-state:$state")
    }

    private val entries = LinkedHashMap<SpatialFilterCacheKeyV1, Entry>()
    /** Invalid targets cannot be reused, but stay session-owned after an uncertain submit. */
    private val quarantinedEntries = linkedSetOf<Entry>()
    private val prepared = mutableMapOf<Long, Binding>()
    private var closed = false
    private var retired = false
    val isClosed: Boolean get() = closed

    /** A partial hit becomes a full cold chain; that preserves producer dependency closure. */
    @Synchronized fun prepare(framePlan: GPUFramePlan): Boolean {
        if (closed || retired || framePlan.capabilitySeal.deviceGeneration.value != generationI64) return false
        val frame = framePlan.w6aLayerFrameV1 ?: return true
        val plans = frame.physical.spatialCachePlans()
        if (plans.isEmpty()) return true
        if (prepared.containsKey(framePlan.frameId.value)) return false
        val filterOutputs = frame.graph.passes().filterIsInstance<PlanPass.FilterPass>().map { it.output }.toSet()
        if (plans.map(SpatialFilterCachePlanV1::outputResourceId).toSet() != filterOutputs) return false
        val existing = plans.mapNotNull { entries[it.key] }
        if (existing.any { !it.reusable && it.consumersI32 > 0 }) return false
        val fullHit = existing.size == plans.size && existing.all { it.reusable && !it.quarantined }
        val selected = linkedMapOf<PlanResourceId, Entry>()
        try {
            plans.forEach { plan ->
                val entry = entries[plan.key] ?: create(plan, frame).also { entries[plan.key] = it }
                if (entry.quarantined) return false
                entry.consumersI32 = Math.addExact(entry.consumersI32, 1)
                selected[plan.outputResourceId] = entry
            }
            prepared[framePlan.frameId.value] = Binding(this, selected,
                if (fullHit) emptySet() else selected.keys, if (fullHit) selected.keys else emptySet())
            return true
        } catch (_: Throwable) {
            release(selected.values)
            discardUnsubmitted(selected.values.filter { !it.reusable })
            return false
        }
    }

    /** Consumes the preflight projection; no key reconstruction or allocation occurs downstream. */
    @Synchronized fun consume(framePlan: GPUFramePlan): Binding? = prepared.remove(framePlan.frameId.value)
    @Synchronized fun discardPrepared(framePlan: GPUFramePlan) { prepared.remove(framePlan.frameId.value)?.releaseBeforeSubmit() }

    /** Device/queue loss invalidates residency but never closes a target that may still be in flight. */
    @Synchronized fun retireGeneration() {
        if (closed || retired) return
        retired = true
        entries.values.forEach { entry -> entry.reusable = false; entry.quarantined = true; quarantinedEntries += entry }
        entries.clear()
    }

    private fun create(plan: SpatialFilterCachePlanV1, frame: org.graphiks.kanvas.gpu.renderer.recording.GPUW6aLayerFramePlan): Entry {
        val row = frame.physical.resource(plan.outputResourceId)
        val extent = requireNotNull(row.copyExtent())
        require(row.byteSize == plan.reservedBytesI64 && row.sampleCountI32 == 1)
        val format = when (row.format) {
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL) -> GPUTextureFormat.RGBA8UnormSrgb
            else -> error("W6c spatial cache requires frozen RGBA8 target")
        }
        val texture = device.createTexture(TextureDescriptor(size = Extent3D(extent.width.toUInt(), extent.height.toUInt()),
            format = format, usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding or GPUTextureUsage.CopySrc,
            sampleCount = 1u, label = "w6c.spatial.cache"))
        return try { Entry(plan.key, texture, texture.createView()) } catch (failure: Throwable) { texture.close(); throw failure }
    }
    @Synchronized private fun release(values: Collection<Entry>) { values.forEach { it.consumersI32-- } }
    @Synchronized private fun promote(values: Collection<Entry>) { values.forEach { require(!it.quarantined); it.reusable = true } }
    @Synchronized private fun discardUnsubmitted(values: Collection<Entry>) { values.forEach { entry ->
        if (entry.consumersI32 == 0 && !entry.reusable) { entries.remove(entry.key); entry.close() }
    } }
    @Synchronized private fun quarantine(values: Collection<Entry>) { values.forEach { entry ->
        entry.reusable = false; entry.quarantined = true; entries.remove(entry.key); quarantinedEntries += entry
    } }
    @Synchronized override fun close() {
        if (!closed) {
            check(prepared.isEmpty())
            (entries.values + quarantinedEntries).forEach(Entry::close)
            entries.clear(); quarantinedEntries.clear(); closed = true
        }
    }
}
