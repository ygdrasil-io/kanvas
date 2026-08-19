package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.TextureDescriptor

internal data class GPUDestinationCopySnapshotSpec(
    val backingWidth: Int,
    val backingHeight: Int,
) {
    init {
        require(backingWidth > 0 && backingHeight > 0)
    }
}

internal data class GPUDestinationCopyNativeCacheCounters(
    val snapshotCreations: Long = 0L,
    val snapshotReuses: Long = 0L,
)

internal class GPUWgpu4kDestinationCopySnapshotHandles(
    val spec: GPUDestinationCopySnapshotSpec,
    val texture: GPUTexture,
    val view: GPUTextureView,
    private val owned: GPUDestinationCopyCachedHandleSet,
) : AutoCloseable by owned

/** Session-owned snapshot backings for the serialized prepared-frame destination route. */
internal class GPUWgpu4kDestinationCopySessionCache(
    private val device: GPUDevice,
) : AutoCloseable {
    private val preRegistrationHandles = GPUPreRegistrationNativeHandleLedger()
    private var snapshots: List<GPUWgpu4kDestinationCopySnapshotHandles> = emptyList()
    private var closed = false
    private var snapshotCreations = 0L
    private var snapshotReuses = 0L

    @Synchronized
    fun acquire(specs: List<GPUDestinationCopySnapshotSpec>): List<GPUWgpu4kDestinationCopySnapshotHandles> {
        check(!closed) { "The destination-copy native session cache is closed" }
        require(specs.isNotEmpty()) { "At least one destination snapshot is required" }
        if (snapshots.map { it.spec } == specs) {
            snapshotReuses = Math.addExact(snapshotReuses, specs.size.toLong())
            return snapshots
        }
        closeSnapshots()
        requireCleanSetupLedger()
        val created = mutableListOf<GPUWgpu4kDestinationCopySnapshotHandles>()
        return try {
            specs.forEachIndexed { index, spec ->
                created += createSnapshot(index, spec)
            }
            snapshots = created.toList()
            preRegistrationHandles.transferAll()
            snapshotCreations = Math.addExact(snapshotCreations, created.size.toLong())
            snapshots
        } catch (failure: Throwable) {
            preRegistrationHandles.closeRetainingFailures()
            throw failure
        }
    }

    @Synchronized
    fun counters(): GPUDestinationCopyNativeCacheCounters =
        GPUDestinationCopyNativeCacheCounters(snapshotCreations, snapshotReuses)

    @Synchronized
    override fun close() {
        if (closed && snapshots.isEmpty() && preRegistrationHandles.pendingHandleCount == 0) return
        closed = true
        var firstFailure = runCatching { closeSnapshots() }.exceptionOrNull()
        if (!preRegistrationHandles.closeRetainingFailures()) {
            val failure = IllegalStateException(
                "Destination-copy session cache retained " +
                    "${preRegistrationHandles.pendingHandleCount} failed setup handle(s)",
            )
            if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
        }
        firstFailure?.let { throw it }
    }

    private fun createSnapshot(
        index: Int,
        spec: GPUDestinationCopySnapshotSpec,
    ): GPUWgpu4kDestinationCopySnapshotHandles {
        val pending = mutableListOf<AutoCloseable>()
        fun <T : AutoCloseable> T.track(): T = also {
            pending += it
            preRegistrationHandles.track(it)
        }
        val texture = device.createTexture(
                TextureDescriptor(
                    size = Extent3D(spec.backingWidth.toUInt(), spec.backingHeight.toUInt()),
                    format = GPUTextureFormat.RGBA8Unorm,
                    usage = GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding,
                    label = "Kanvas.session.destinationCopy.snapshot.$index",
                ),
        ).track()
        val view = texture.createView().track()
        return GPUWgpu4kDestinationCopySnapshotHandles(
            spec,
            texture,
            view,
            GPUDestinationCopyCachedHandleSet(pending.toList()),
        )
    }

    private fun requireCleanSetupLedger() {
        check(preRegistrationHandles.closeRetainingFailures()) {
            "Destination-copy cache cannot allocate while failed setup handles remain quarantined"
        }
    }

    private fun closeSnapshots() {
        var firstFailure: Throwable? = null
        val iterator = snapshots.toMutableList().iterator()
        while (iterator.hasNext()) {
            try {
                iterator.next().close()
                iterator.remove()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }
        if (firstFailure == null) snapshots = emptyList()
        firstFailure?.let { throw IllegalStateException("Destination-copy cached snapshots remain live", it) }
    }
}

internal class GPUDestinationCopyCachedHandleSet(handles: List<AutoCloseable>) : AutoCloseable {
    private val pending = handles.asReversed().toMutableList()

    @Synchronized
    override fun close() {
        var firstFailure: Throwable? = null
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            try {
                iterator.next().close()
                iterator.remove()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }
        firstFailure?.let { throw IllegalStateException("Destination-copy cached handles remain live", it) }
    }
}
