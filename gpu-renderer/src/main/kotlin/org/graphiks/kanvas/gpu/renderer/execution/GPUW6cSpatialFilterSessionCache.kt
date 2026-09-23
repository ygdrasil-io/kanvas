package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.*
import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan

/** Session-scoped, bounded W6c residency. Preflight is its sole cache policy point. */
internal class GPUW6cSpatialFilterSessionCache(
    private val device: GPUDevice,
    private val generationI64: Long,
    private val maxEntriesI32: Int = 128,
    private val maxBytesI64: Long = 64L * 1024L * 1024L,
) : AutoCloseable {
    internal class Entry(
        val key: SpatialFilterCacheKeyV1,
        val byteSizeI64: Long,
        val texture: GPUTexture,
        val view: GPUTextureView,
    ) {
        var consumersI32 = 0
        var reusable = false
        var quarantined = false
        fun close() { view.close(); texture.close() }
    }

    /** Immutable per-frame mapping and schedule projection; no downstream cache choice exists. */
    internal class Binding internal constructor(
        private val owner: GPUW6cSpatialFilterSessionCache,
        private val entries: Map<PlanResourceId, Entry>,
        private val missOutputs: Set<PlanResourceId>,
        private val reuseOutputs: Set<PlanResourceId>,
        /** Cold duplicate targets prevent concurrent frames from writing one stable entry. */
        private val transientEntries: Set<Entry>,
    ) : GPUPreparedNativeFrameLeaseLifecycle {
        private enum class State { CheckedOut, Submitted, Terminal }
        private var state = State.CheckedOut
        fun view(output: PlanResourceId): GPUTextureView = synchronized(owner) { checkLive(); entries.getValue(output).view }
        fun texture(output: PlanResourceId): GPUTexture = synchronized(owner) { checkLive(); entries.getValue(output).texture }
        fun usesCachedTarget(output: PlanResourceId): Boolean = output in entries
        /** True only for a dependency-closed reusable subgraph. */
        fun skipsFilterPass(output: PlanResourceId): Boolean = output in reuseOutputs
        @Synchronized override fun releaseBeforeSubmit(): GPUPreparedNativeFrameLeaseTransition {
            if (state != State.CheckedOut) return refused()
            owner.release(entries.values)
            owner.discardUnsubmitted(entries.filterKeys { it in missOutputs }.values.filterNot { it in transientEntries })
            owner.discardUnsubmitted(transientEntries)
            state = State.Terminal
            return GPUPreparedNativeFrameLeaseTransition.Applied
        }
        @Synchronized override fun markSubmitted(): GPUPreparedNativeFrameLeaseTransition {
            if (state != State.CheckedOut || !owner.bindingLive(entries.values)) return refused()
            state = State.Submitted
            return GPUPreparedNativeFrameLeaseTransition.Applied
        }
        @Synchronized override fun releaseAfterCompletion(): GPUPreparedNativeFrameLeaseTransition {
            if (state != State.Submitted || !owner.bindingLive(entries.values)) return refused()
            owner.promote(entries.filterKeys { it in missOutputs }.values.filterNot { it in transientEntries })
            owner.release(entries.values)
            owner.discardUnsubmitted(transientEntries)
            state = State.Terminal
            return GPUPreparedNativeFrameLeaseTransition.Applied
        }
        @Synchronized override fun quarantineUncertain(): GPUPreparedNativeFrameLeaseTransition {
            if (state == State.Terminal) return refused()
            owner.quarantine(entries.values)
            owner.release(entries.values)
            state = State.Terminal
            return GPUPreparedNativeFrameLeaseTransition.Applied
        }
        private fun checkLive() { check(state == State.CheckedOut && owner.bindingLive(entries.values)) }
        private fun refused() = GPUPreparedNativeFrameLeaseTransition.Refused("invalid-w6c-spatial-binding-state:$state")
    }

    private val entries = LinkedHashMap<SpatialFilterCacheKeyV1, Entry>(16, .75f, true)
    /** Per-binding cold targets: charged residents, never future hits, and closed on completion. */
    private val transientEntries = linkedSetOf<Entry>()
    /** Quarantined entries retain capacity/ownership until teardown and can never be hits. */
    private val quarantine = linkedSetOf<Entry>()
    private val prepared = mutableMapOf<Long, Binding>()
    private var closed = false
    private var retired = false
    val isClosed: Boolean get() = closed

    init { require(maxEntriesI32 > 0 && maxBytesI64 > 0L && generationI64 >= 0L) }

    /**
     * Projects reusable cacheable passes only. Non-cacheable W6b filters remain ordinary cold
     * work; a cacheable pass may skip only if its filter-producing ancestors also skip.
     */
    @Synchronized fun prepare(framePlan: GPUFramePlan): Boolean {
        if (closed || retired || framePlan.capabilitySeal.deviceGeneration.value != generationI64) return false
        val frame = framePlan.w6aLayerFrameV1 ?: return true
        val plans = frame.physical.spatialCachePlans()
        if (plans.isEmpty()) return true
        if (prepared.containsKey(framePlan.frameId.value)) return false
        val passesByOutput = frame.graph.passes().filterIsInstance<PlanPass.FilterPass>().associateBy { it.output }
        val plansByOutput = plans.associateBy(SpatialFilterCachePlanV1::outputResourceId)
        if (plansByOutput.size != plans.size || !plansByOutput.keys.all { it in passesByOutput }) return false
        val selected = linkedMapOf<PlanResourceId, Entry>()
        val transientForBinding = linkedSetOf<Entry>()
        try {
            plans.forEach { plan ->
                val resident = entries[plan.key]
                // A non-reusable resident is a target being written. A second frame gets a
                // separately owned cold target rather than racing that write.
                val entry = if (resident != null && !resident.reusable && resident.consumersI32 > 0) {
                    createBounded(plan, frame).also { transientEntries += it; transientForBinding += it }
                } else resident ?: createBounded(plan, frame).also { entries[plan.key] = it }
                if (entry.quarantined) return false
                entry.consumersI32 = Math.addExact(entry.consumersI32, 1)
                selected[plan.outputResourceId] = entry
            }
            // A pass is a hit only where its entire filter dependency closure is itself a hit.
            val reusable = mutableSetOf<PlanResourceId>()
            fun reusableClosure(output: PlanResourceId): Boolean {
                if (output in reusable) return true
                val entry = selected[output] ?: return false
                if (!entry.reusable || entry.quarantined) return false
                val pass = passesByOutput.getValue(output)
                val safe = pass.inputs().all { input ->
                    val producer = passesByOutput[input]
                    producer == null || (input in plansByOutput && reusableClosure(input))
                }
                if (safe) reusable += output
                return safe
            }
            selected.keys.forEach(::reusableClosure)
            val misses = selected.keys - reusable
            misses.forEach { output ->
                val entry = selected.getValue(output)
                // A valid resident may be a hit for an older frame yet require recomputation
                // here because this frame's dependency closure is cold. Do not overwrite it.
                if (entry.reusable && entry.consumersI32 > 1) {
                    val plan = plansByOutput.getValue(output)
                    val replacement = createBounded(plan, frame)
                    replacement.consumersI32 = 1
                    // Register before replacing the selected map so any later failure rolls
                    // this target back without touching the older frame's resident lease.
                    transientForBinding += replacement
                    transientEntries += replacement
                    selected[output] = replacement
                    release(listOf(entry))
                }
                selected.getValue(output).reusable = false
            }
            prepared[framePlan.frameId.value] = Binding(this, selected, misses, reusable, transientForBinding)
            return true
        } catch (_: Throwable) {
            val rollbackLeases = selected.values.toMutableList()
            transientForBinding.filter { transient -> selected.values.none { it === transient } }
                .forEach(rollbackLeases::add)
            release(rollbackLeases)
            discardUnsubmitted(rollbackLeases.distinct().filter { !it.reusable })
            return false
        }
    }

    /** Consumes the preflight projection; retired/quarantined entries can never reach a NoOp. */
    @Synchronized fun consume(framePlan: GPUFramePlan): Binding? =
        if (retired || closed) null else prepared.remove(framePlan.frameId.value)
    @Synchronized fun discardPrepared(framePlan: GPUFramePlan) { prepared.remove(framePlan.frameId.value)?.releaseBeforeSubmit() }

    /** Queue loss terminalizes every not-yet-submitted binding and quarantines in-flight targets. */
    @Synchronized fun retireGeneration() {
        if (closed || retired) return
        retired = true
        prepared.values.toList().forEach { it.quarantineUncertain() }
        prepared.clear()
        quarantine(entries.values.toList())
        quarantine(transientEntries.toList())
    }

    private fun createBounded(plan: SpatialFilterCachePlanV1,
        frame: org.graphiks.kanvas.gpu.renderer.recording.GPUW6aLayerFramePlan): Entry {
        require(plan.reservedBytesI64 <= maxBytesI64) { "w6c.spatial.cache-budget" }
        while (entries.size + transientEntries.size + quarantine.size >= maxEntriesI32 ||
            Math.addExact(residentBytesI64(), plan.reservedBytesI64) > maxBytesI64) {
            val victim = entries.entries.firstOrNull { it.value.consumersI32 == 0 }
                ?: throw IllegalStateException("w6c.spatial.cache-budget")
            try { victim.value.close() } catch (failure: Throwable) {
                entries.remove(victim.key); quarantine += victim.value; throw failure
            }
            entries.remove(victim.key)
        }
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
        return try { Entry(plan.key, plan.reservedBytesI64, texture, texture.createView()) }
        catch (failure: Throwable) { texture.close(); throw failure }
    }

    @Synchronized private fun bindingLive(values: Collection<Entry>): Boolean = !retired && !closed && values.none(Entry::quarantined)
    @Synchronized private fun residentBytesI64(): Long = (entries.values + transientEntries + quarantine).fold(0L) { total, entry ->
        Math.addExact(total, entry.byteSizeI64)
    }
    @Synchronized private fun release(values: Collection<Entry>) { values.forEach { check(it.consumersI32 > 0); it.consumersI32-- } }
    @Synchronized private fun promote(values: Collection<Entry>) { values.forEach { require(!it.quarantined); it.reusable = true } }
    @Synchronized private fun discardUnsubmitted(values: Collection<Entry>) { values.forEach { entry ->
        if (entry.consumersI32 == 0 && !entry.reusable) {
            try { entry.close() } catch (failure: Throwable) {
                // A partially closed target is never a future hit and still consumes capacity
                // until session teardown can retry it, exactly like the W5 cache quarantine.
                detach(entry); quarantine += entry; throw failure
            }
            detach(entry)
        }
    } }
    @Synchronized private fun quarantine(values: Collection<Entry>) { values.forEach { entry ->
        entry.reusable = false; entry.quarantined = true; detach(entry); quarantine += entry
    } }
    @Synchronized override fun close() {
        if (!closed) {
            check(prepared.isEmpty())
            check(entries.values.all { it.consumersI32 == 0 } &&
                transientEntries.all { it.consumersI32 == 0 } && quarantine.all { it.consumersI32 == 0 })
            (entries.values + transientEntries + quarantine).forEach(Entry::close)
            entries.clear(); transientEntries.clear(); quarantine.clear(); closed = true
        }
    }

    /** Remove by identity: a transient may intentionally have the same cache key as a resident. */
    private fun detach(entry: Entry) {
        if (entries[entry.key] === entry) entries.remove(entry.key)
        transientEntries.remove(entry)
    }
}
