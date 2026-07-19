package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID

internal enum class GPUWgpu4kCorePrimitiveFramePoolResource {
    VertexBuffer,
    IndexBuffer,
    UniformBuffer,
    BindGroup,
    PathDepthStencilTexture,
    PathDepthStencilView,
    ClipDepthStencilTexture,
    ClipDepthStencilView,
}

/** Native allocation seam. The pool owns every handle returned by this factory. */
internal interface GPUWgpu4kCorePrimitiveFramePoolFactory {
    fun createVertexBuffer(capacityBytes: Long): GPUBuffer
    fun createIndexBuffer(capacityBytes: Long): GPUBuffer
    fun createUniformBuffer(capacityBytes: Long): GPUBuffer
    fun createBindGroup(
        componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
        uniformBuffer: GPUBuffer,
    ): GPUBindGroup
    fun createPathDepthStencilTexture(
        requirement: GPUWgpu4kCorePrimitivePathDepthStencilRequirement,
    ): GPUTexture
    fun createPathDepthStencilView(texture: GPUTexture): GPUTextureView
    fun createClipDepthStencilTexture(
        requirement: GPUWgpu4kCorePrimitiveClipDepthStencilRequirement,
    ): GPUTexture
    fun createClipDepthStencilView(texture: GPUTexture): GPUTextureView
}

internal data class GPUWgpu4kCorePrimitivePathDepthStencilRequirement(
    val width: Int,
    val height: Int,
    val format: GPUTextureFormat,
    val sampleCount: Int,
    val usage: GPUTextureUsage,
) {
    init {
        require(width > 0 && height > 0) {
            "CorePrimitive path depth/stencil extent must be positive"
        }
        require(format == GPUTextureFormat.Depth24PlusStencil8) {
            "CorePrimitive path depth/stencil format must be Depth24PlusStencil8"
        }
        require(sampleCount == 1) {
            "CorePrimitive path depth/stencil attachment must be single-sample"
        }
        require(usage == GPUTextureUsage.RenderAttachment) {
            "CorePrimitive path depth/stencil usage must be exactly RenderAttachment"
        }
    }
}

internal data class GPUWgpu4kCorePrimitiveClipDepthStencilRequirement(
    val width: Int,
    val height: Int,
    val format: GPUTextureFormat,
    val sampleCount: Int,
    val usage: GPUTextureUsage,
) {
    init {
        require(width > 0 && height > 0) {
            "CorePrimitive clip depth/stencil extent must be positive"
        }
        require(format == GPUTextureFormat.Depth24PlusStencil8) {
            "CorePrimitive clip depth/stencil format must be Depth24PlusStencil8"
        }
        require(sampleCount == 1) {
            "CorePrimitive clip depth/stencil attachment must be single-sample"
        }
        require(usage == GPUTextureUsage.RenderAttachment) {
            "CorePrimitive clip depth/stencil usage must be exactly RenderAttachment"
        }
    }
}

internal data class GPUWgpu4kCorePrimitiveFramePoolRequirements(
    val deviceGeneration: GPUDeviceGenerationID,
    val vertexBytes: Long,
    val indexBytes: Long,
    val uniformBytes: Long,
    val pathDepthStencil: GPUWgpu4kCorePrimitivePathDepthStencilRequirement? = null,
    val componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity =
        PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY,
    val clipDepthStencil: GPUWgpu4kCorePrimitiveClipDepthStencilRequirement? = null,
)

internal data class GPUWgpu4kCorePrimitiveFramePoolCapacities(
    val vertexBytes: Long,
    val indexBytes: Long,
    val uniformBytes: Long,
) {
    init {
        require(listOf(vertexBytes, indexBytes, uniformBytes).all { it > 0L && it and (it - 1L) == 0L }) {
            "CorePrimitive frame-pool capacities must be positive powers of two"
        }
    }
}

internal data class GPUWgpu4kCorePrimitiveFramePoolHandles(
    val vertexBuffer: GPUBuffer,
    val indexBuffer: GPUBuffer,
    val uniformBuffer: GPUBuffer,
    val bindGroup: GPUBindGroup,
    val pathDepthStencil: GPUWgpu4kCorePrimitivePathDepthStencilHandles? = null,
    val componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity =
        PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY,
    val clipDepthStencil: GPUWgpu4kCorePrimitiveClipDepthStencilHandles? = null,
)

internal data class GPUWgpu4kCorePrimitivePathDepthStencilHandles(
    val requirement: GPUWgpu4kCorePrimitivePathDepthStencilRequirement,
    val texture: GPUTexture,
    val view: GPUTextureView,
)

internal data class GPUWgpu4kCorePrimitiveClipDepthStencilHandles(
    val requirement: GPUWgpu4kCorePrimitiveClipDepthStencilRequirement,
    val texture: GPUTexture,
    val view: GPUTextureView,
)

internal sealed interface GPUWgpu4kCorePrimitiveFramePoolRefusal {
    data class DeviceGenerationMismatch(
        val expected: GPUDeviceGenerationID,
        val observed: GPUDeviceGenerationID,
    ) : GPUWgpu4kCorePrimitiveFramePoolRefusal

    data class InvalidCapacity(
        val resource: GPUWgpu4kCorePrimitiveFramePoolResource,
        val requestedBytes: Long,
    ) : GPUWgpu4kCorePrimitiveFramePoolRefusal

    data class AllocationFailed(
        val resource: GPUWgpu4kCorePrimitiveFramePoolResource,
        val failureType: String,
        val message: String,
    ) : GPUWgpu4kCorePrimitiveFramePoolRefusal

    data class Saturated(val maxSlots: Int) : GPUWgpu4kCorePrimitiveFramePoolRefusal
    data object Closing : GPUWgpu4kCorePrimitiveFramePoolRefusal
    data object Closed : GPUWgpu4kCorePrimitiveFramePoolRefusal
}

internal sealed interface GPUWgpu4kCorePrimitiveFramePoolCheckout {
    data class Acquired(
        val lease: GPUWgpu4kCorePrimitiveFramePoolLease,
    ) : GPUWgpu4kCorePrimitiveFramePoolCheckout

    data class Refused(
        val reason: GPUWgpu4kCorePrimitiveFramePoolRefusal,
    ) : GPUWgpu4kCorePrimitiveFramePoolCheckout
}

internal sealed interface GPUWgpu4kCorePrimitiveFramePoolLeaseTransition {
    data object Applied : GPUWgpu4kCorePrimitiveFramePoolLeaseTransition

    data class Refused(
        val reason: String,
    ) : GPUWgpu4kCorePrimitiveFramePoolLeaseTransition
}

internal class GPUWgpu4kCorePrimitiveFramePoolCloseRefused(
    val liveLeaseIds: List<Long>,
) : IllegalStateException(
    "CorePrimitive frame pool is waiting for live leases ${liveLeaseIds.joinToString()}",
)

internal class GPUWgpu4kCorePrimitiveFramePoolCloseFailure(
    val retainedHandleCount: Int,
) : IllegalStateException(
    "CorePrimitive frame pool retained $retainedHandleCount native handle(s)",
)

internal class GPUWgpu4kCorePrimitiveFramePoolLease internal constructor(
    private val owner: GPUWgpu4kCorePrimitiveFramePool,
    val leaseId: Long,
    val slotId: Int,
    val deviceGeneration: GPUDeviceGenerationID,
    val capacities: GPUWgpu4kCorePrimitiveFramePoolCapacities,
    val handles: GPUWgpu4kCorePrimitiveFramePoolHandles,
) {
    fun rollbackBeforeSubmit(): GPUWgpu4kCorePrimitiveFramePoolLeaseTransition = owner.rollback(this)

    fun markSubmitted(): GPUWgpu4kCorePrimitiveFramePoolLeaseTransition = owner.markSubmitted(this)

    fun completeSuccessfully(): GPUWgpu4kCorePrimitiveFramePoolLeaseTransition = owner.completeSuccessfully(this)

    fun quarantineUncertain(): GPUWgpu4kCorePrimitiveFramePoolLeaseTransition = owner.quarantineUncertain(this)
}

/**
 * Session-confined, non-blocking pool for CorePrimitive frame slabs, bind group, and optional exact
 * path and clip depth/stencil attachments. Their types and handles are strictly separate. Slots are
 * bounded to three and never cross a WebGPU device generation.
 */
internal class GPUWgpu4kCorePrimitiveFramePool(
    private val deviceGeneration: GPUDeviceGenerationID,
    private val factory: GPUWgpu4kCorePrimitiveFramePoolFactory,
) : AutoCloseable {
    private enum class SlotState {
        Available,
        CheckedOut,
        Submitted,
        Quarantined,
    }

    private data class Slot(
        val slotId: Int,
        var capacities: GPUWgpu4kCorePrimitiveFramePoolCapacities,
        var handles: GPUWgpu4kCorePrimitiveFramePoolHandles,
        var state: SlotState = SlotState.Available,
        var activeLeaseId: Long? = null,
    )

    private class AllocationFailure(
        val resource: GPUWgpu4kCorePrimitiveFramePoolResource,
        cause: Throwable,
    ) : RuntimeException(cause)

    private class PendingCloseHandle(
        val handle: AutoCloseable,
        val prerequisite: PendingCloseHandle? = null,
    ) {
        var closed: Boolean = false
    }

    private val slots = mutableListOf<Slot>()
    private val pendingClose = mutableListOf<PendingCloseHandle>()
    private var nextLeaseId = 1L
    private var closing = false
    private var closed = false

    @Synchronized
    fun acquire(
        requirements: GPUWgpu4kCorePrimitiveFramePoolRequirements,
    ): GPUWgpu4kCorePrimitiveFramePoolCheckout {
        if (closed) return GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused(
            GPUWgpu4kCorePrimitiveFramePoolRefusal.Closed,
        )
        if (closing) return GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused(
            GPUWgpu4kCorePrimitiveFramePoolRefusal.Closing,
        )
        if (requirements.deviceGeneration != deviceGeneration) {
            return GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused(
                GPUWgpu4kCorePrimitiveFramePoolRefusal.DeviceGenerationMismatch(
                    deviceGeneration,
                    requirements.deviceGeneration,
                ),
            )
        }
        val requiredCapacities = capacitiesFor(requirements)
            ?: return GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused(
                invalidCapacityRefusal(requirements),
            )
        val available = slots.filter { it.state == SlotState.Available }
        var slot = selectAvailableSlot(
            available,
            requiredCapacities,
            requirements.pathDepthStencil,
            requirements.clipDepthStencil,
            requirements.componentIdentity,
        )
        if (slot != null &&
            (!slot.capacities.contains(requiredCapacities) ||
                !slot.handles.matches(
                    requirements.pathDepthStencil,
                    requirements.clipDepthStencil,
                    requirements.componentIdentity,
                ))
        ) {
            grow(
                slot,
                requiredCapacities,
                requirements.pathDepthStencil,
                requirements.clipDepthStencil,
                requirements.componentIdentity,
            )?.let { refusal ->
                return GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused(refusal)
            }
        }
        if (slot == null) {
            if (slots.size == MAX_SLOTS) {
                return GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused(
                    GPUWgpu4kCorePrimitiveFramePoolRefusal.Saturated(MAX_SLOTS),
                )
            }
            when (
                val created = createSlot(
                    slots.size,
                    requiredCapacities,
                    requirements.pathDepthStencil,
                    requirements.clipDepthStencil,
                    requirements.componentIdentity,
                )
            ) {
                is SlotCreation.Created -> {
                    slot = created.slot
                    slots += created.slot
                }
                is SlotCreation.Refused -> {
                    return GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused(created.reason)
                }
            }
        }
        val acquiredSlot = requireNotNull(slot)
        val leaseId = nextLeaseId++
        acquiredSlot.state = SlotState.CheckedOut
        acquiredSlot.activeLeaseId = leaseId
        return GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired(
            GPUWgpu4kCorePrimitiveFramePoolLease(
                this,
                leaseId,
                acquiredSlot.slotId,
                deviceGeneration,
                acquiredSlot.capacities,
                acquiredSlot.handles,
            ),
        )
    }

    @Synchronized
    internal fun rollback(
        lease: GPUWgpu4kCorePrimitiveFramePoolLease,
    ): GPUWgpu4kCorePrimitiveFramePoolLeaseTransition = transition(
        lease,
        expected = setOf(SlotState.CheckedOut),
        next = SlotState.Available,
        terminal = true,
    )

    @Synchronized
    internal fun markSubmitted(
        lease: GPUWgpu4kCorePrimitiveFramePoolLease,
    ): GPUWgpu4kCorePrimitiveFramePoolLeaseTransition = transition(
        lease,
        expected = setOf(SlotState.CheckedOut),
        next = SlotState.Submitted,
        terminal = false,
    )

    @Synchronized
    internal fun completeSuccessfully(
        lease: GPUWgpu4kCorePrimitiveFramePoolLease,
    ): GPUWgpu4kCorePrimitiveFramePoolLeaseTransition = transition(
        lease,
        expected = setOf(SlotState.Submitted),
        next = SlotState.Available,
        terminal = true,
    )

    @Synchronized
    internal fun quarantineUncertain(
        lease: GPUWgpu4kCorePrimitiveFramePoolLease,
    ): GPUWgpu4kCorePrimitiveFramePoolLeaseTransition = transition(
        lease,
        expected = setOf(SlotState.CheckedOut, SlotState.Submitted),
        next = SlotState.Quarantined,
        terminal = true,
    )

    @Synchronized
    override fun close() {
        if (!closed) {
            closing = true
            val liveLeaseIds = slots.mapNotNull { slot ->
                slot.activeLeaseId?.takeIf {
                    slot.state == SlotState.CheckedOut || slot.state == SlotState.Submitted
                }
            }
            if (liveLeaseIds.isNotEmpty()) throw GPUWgpu4kCorePrimitiveFramePoolCloseRefused(liveLeaseIds)
            slots.asReversed().forEach { slot ->
                enqueueRetirement(
                    slot.handles.bindGroup,
                    slot.handles.uniformBuffer,
                    slot.handles.indexBuffer,
                    slot.handles.vertexBuffer,
                    slot.handles.pathDepthStencil?.view,
                    slot.handles.pathDepthStencil?.texture,
                    slot.handles.clipDepthStencil?.view,
                    slot.handles.clipDepthStencil?.texture,
                )
            }
            slots.clear()
            closed = true
        }
        closePending()
        if (pendingClose.isNotEmpty()) {
            throw GPUWgpu4kCorePrimitiveFramePoolCloseFailure(pendingClose.size)
        }
    }

    private fun transition(
        lease: GPUWgpu4kCorePrimitiveFramePoolLease,
        expected: Set<SlotState>,
        next: SlotState,
        terminal: Boolean,
    ): GPUWgpu4kCorePrimitiveFramePoolLeaseTransition {
        val slot = slots.getOrNull(lease.slotId)
        if (slot == null || slot.activeLeaseId != lease.leaseId) {
            return GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Refused("stale-lease")
        }
        if (slot.state !in expected) {
            return GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Refused(
                "invalid-state:${slot.state.name}",
            )
        }
        slot.state = next
        if (terminal) slot.activeLeaseId = null
        return GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Applied
    }

    private fun createSlot(
        slotId: Int,
        capacities: GPUWgpu4kCorePrimitiveFramePoolCapacities,
        pathDepthStencilRequirement: GPUWgpu4kCorePrimitivePathDepthStencilRequirement?,
        clipDepthStencilRequirement: GPUWgpu4kCorePrimitiveClipDepthStencilRequirement?,
        componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
    ): SlotCreation {
        var vertex: GPUBuffer? = null
        var index: GPUBuffer? = null
        var uniform: GPUBuffer? = null
        var bindGroup: GPUBindGroup? = null
        var pathDepthStencilTexture: GPUTexture? = null
        var pathDepthStencilView: GPUTextureView? = null
        var clipDepthStencilTexture: GPUTexture? = null
        var clipDepthStencilView: GPUTextureView? = null
        return try {
            vertex = allocate(GPUWgpu4kCorePrimitiveFramePoolResource.VertexBuffer) {
                factory.createVertexBuffer(capacities.vertexBytes)
            }
            index = allocate(GPUWgpu4kCorePrimitiveFramePoolResource.IndexBuffer) {
                factory.createIndexBuffer(capacities.indexBytes)
            }
            uniform = allocate(GPUWgpu4kCorePrimitiveFramePoolResource.UniformBuffer) {
                factory.createUniformBuffer(capacities.uniformBytes)
            }
            bindGroup = allocate(GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup) {
                factory.createBindGroup(componentIdentity, requireNotNull(uniform))
            }
            if (pathDepthStencilRequirement != null) {
                pathDepthStencilTexture = allocate(
                    GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilTexture,
                ) {
                    factory.createPathDepthStencilTexture(pathDepthStencilRequirement)
                }
                pathDepthStencilView = allocate(
                    GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilView,
                ) {
                    factory.createPathDepthStencilView(requireNotNull(pathDepthStencilTexture))
                }
            }
            if (clipDepthStencilRequirement != null) {
                clipDepthStencilTexture = allocate(
                    GPUWgpu4kCorePrimitiveFramePoolResource.ClipDepthStencilTexture,
                ) {
                    factory.createClipDepthStencilTexture(clipDepthStencilRequirement)
                }
                clipDepthStencilView = allocate(
                    GPUWgpu4kCorePrimitiveFramePoolResource.ClipDepthStencilView,
                ) {
                    factory.createClipDepthStencilView(requireNotNull(clipDepthStencilTexture))
                }
            }
            SlotCreation.Created(
                Slot(
                    slotId,
                    capacities,
                    GPUWgpu4kCorePrimitiveFramePoolHandles(
                        requireNotNull(vertex),
                        requireNotNull(index),
                        requireNotNull(uniform),
                        requireNotNull(bindGroup),
                        pathDepthStencilRequirement?.let { requirement ->
                            GPUWgpu4kCorePrimitivePathDepthStencilHandles(
                                requirement,
                                requireNotNull(pathDepthStencilTexture),
                                requireNotNull(pathDepthStencilView),
                            )
                        },
                        componentIdentity,
                        clipDepthStencilRequirement?.let { requirement ->
                            GPUWgpu4kCorePrimitiveClipDepthStencilHandles(
                                requirement,
                                requireNotNull(clipDepthStencilTexture),
                                requireNotNull(clipDepthStencilView),
                            )
                        },
                    ),
                ),
            )
        } catch (failure: AllocationFailure) {
            retire(
                bindGroup,
                uniform,
                index,
                vertex,
                pathDepthStencilView,
                pathDepthStencilTexture,
                clipDepthStencilView,
                clipDepthStencilTexture,
            )
            SlotCreation.Refused(failure.refusal())
        }
    }

    private fun grow(
        slot: Slot,
        capacities: GPUWgpu4kCorePrimitiveFramePoolCapacities,
        pathDepthStencilRequirement: GPUWgpu4kCorePrimitivePathDepthStencilRequirement?,
        clipDepthStencilRequirement: GPUWgpu4kCorePrimitiveClipDepthStencilRequirement?,
        componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
    ): GPUWgpu4kCorePrimitiveFramePoolRefusal? {
        val oldHandles = slot.handles
        var vertex: GPUBuffer? = null
        var index: GPUBuffer? = null
        var uniform: GPUBuffer? = null
        var bindGroup: GPUBindGroup? = null
        var pathDepthStencilTexture: GPUTexture? = null
        var pathDepthStencilView: GPUTextureView? = null
        var clipDepthStencilTexture: GPUTexture? = null
        var clipDepthStencilView: GPUTextureView? = null
        val replacePathDepthStencil = pathDepthStencilRequirement != null &&
            oldHandles.pathDepthStencil?.requirement != pathDepthStencilRequirement
        val replaceClipDepthStencil = clipDepthStencilRequirement != null &&
            oldHandles.clipDepthStencil?.requirement != clipDepthStencilRequirement
        val replaceBindGroup = capacities.uniformBytes > slot.capacities.uniformBytes ||
            oldHandles.componentIdentity != componentIdentity
        return try {
            if (capacities.vertexBytes > slot.capacities.vertexBytes) {
                vertex = allocate(GPUWgpu4kCorePrimitiveFramePoolResource.VertexBuffer) {
                    factory.createVertexBuffer(capacities.vertexBytes)
                }
            }
            if (capacities.indexBytes > slot.capacities.indexBytes) {
                index = allocate(GPUWgpu4kCorePrimitiveFramePoolResource.IndexBuffer) {
                    factory.createIndexBuffer(capacities.indexBytes)
                }
            }
            if (capacities.uniformBytes > slot.capacities.uniformBytes) {
                uniform = allocate(GPUWgpu4kCorePrimitiveFramePoolResource.UniformBuffer) {
                    factory.createUniformBuffer(capacities.uniformBytes)
                }
            }
            if (replaceBindGroup) {
                bindGroup = allocate(GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup) {
                    factory.createBindGroup(componentIdentity, uniform ?: oldHandles.uniformBuffer)
                }
            }
            if (replacePathDepthStencil) {
                pathDepthStencilTexture = allocate(
                    GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilTexture,
                ) {
                    factory.createPathDepthStencilTexture(requireNotNull(pathDepthStencilRequirement))
                }
                pathDepthStencilView = allocate(
                    GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilView,
                ) {
                    factory.createPathDepthStencilView(requireNotNull(pathDepthStencilTexture))
                }
            }
            if (replaceClipDepthStencil) {
                clipDepthStencilTexture = allocate(
                    GPUWgpu4kCorePrimitiveFramePoolResource.ClipDepthStencilTexture,
                ) {
                    factory.createClipDepthStencilTexture(requireNotNull(clipDepthStencilRequirement))
                }
                clipDepthStencilView = allocate(
                    GPUWgpu4kCorePrimitiveFramePoolResource.ClipDepthStencilView,
                ) {
                    factory.createClipDepthStencilView(requireNotNull(clipDepthStencilTexture))
                }
            }
            val replacement = GPUWgpu4kCorePrimitiveFramePoolHandles(
                vertex ?: oldHandles.vertexBuffer,
                index ?: oldHandles.indexBuffer,
                uniform ?: oldHandles.uniformBuffer,
                bindGroup ?: oldHandles.bindGroup,
                if (replacePathDepthStencil) {
                    GPUWgpu4kCorePrimitivePathDepthStencilHandles(
                        requireNotNull(pathDepthStencilRequirement),
                        requireNotNull(pathDepthStencilTexture),
                        requireNotNull(pathDepthStencilView),
                    )
                } else {
                    oldHandles.pathDepthStencil
                },
                componentIdentity,
                if (replaceClipDepthStencil) {
                    GPUWgpu4kCorePrimitiveClipDepthStencilHandles(
                        requireNotNull(clipDepthStencilRequirement),
                        requireNotNull(clipDepthStencilTexture),
                        requireNotNull(clipDepthStencilView),
                    )
                } else {
                    oldHandles.clipDepthStencil
                },
            )
            slot.handles = replacement
            slot.capacities = GPUWgpu4kCorePrimitiveFramePoolCapacities(
                vertexBytes = maxOf(slot.capacities.vertexBytes, capacities.vertexBytes),
                indexBytes = maxOf(slot.capacities.indexBytes, capacities.indexBytes),
                uniformBytes = maxOf(slot.capacities.uniformBytes, capacities.uniformBytes),
            )
            retire(
                oldHandles.bindGroup.takeIf { bindGroup != null },
                oldHandles.uniformBuffer.takeIf { uniform != null },
                oldHandles.indexBuffer.takeIf { index != null },
                oldHandles.vertexBuffer.takeIf { vertex != null },
                oldHandles.pathDepthStencil?.view.takeIf { replacePathDepthStencil },
                oldHandles.pathDepthStencil?.texture.takeIf { replacePathDepthStencil },
                oldHandles.clipDepthStencil?.view.takeIf { replaceClipDepthStencil },
                oldHandles.clipDepthStencil?.texture.takeIf { replaceClipDepthStencil },
            )
            null
        } catch (failure: AllocationFailure) {
            retire(
                bindGroup,
                uniform,
                index,
                vertex,
                pathDepthStencilView,
                pathDepthStencilTexture,
                clipDepthStencilView,
                clipDepthStencilTexture,
            )
            failure.refusal()
        }
    }

    private inline fun <T : AutoCloseable> allocate(
        resource: GPUWgpu4kCorePrimitiveFramePoolResource,
        block: () -> T,
    ): T = try {
        block()
    } catch (failure: Throwable) {
        throw AllocationFailure(resource, failure)
    }

    private fun AllocationFailure.refusal() = GPUWgpu4kCorePrimitiveFramePoolRefusal.AllocationFailed(
        resource,
        cause?.let { it::class.simpleName }.orEmpty(),
        cause?.message.orEmpty(),
    )

    private fun retire(
        bindGroup: GPUBindGroup?,
        uniformBuffer: GPUBuffer?,
        indexBuffer: GPUBuffer?,
        vertexBuffer: GPUBuffer?,
        pathDepthStencilView: GPUTextureView? = null,
        pathDepthStencilTexture: GPUTexture? = null,
        clipDepthStencilView: GPUTextureView? = null,
        clipDepthStencilTexture: GPUTexture? = null,
    ) {
        enqueueRetirement(
            bindGroup,
            uniformBuffer,
            indexBuffer,
            vertexBuffer,
            pathDepthStencilView,
            pathDepthStencilTexture,
            clipDepthStencilView,
            clipDepthStencilTexture,
        )
        closePending()
    }

    private fun enqueueRetirement(
        bindGroup: GPUBindGroup?,
        uniformBuffer: GPUBuffer?,
        indexBuffer: GPUBuffer?,
        vertexBuffer: GPUBuffer?,
        pathDepthStencilView: GPUTextureView? = null,
        pathDepthStencilTexture: GPUTexture? = null,
        clipDepthStencilView: GPUTextureView? = null,
        clipDepthStencilTexture: GPUTexture? = null,
    ) {
        val pathDepthStencilViewClose = pathDepthStencilView?.let(::PendingCloseHandle)
        pathDepthStencilViewClose?.let(pendingClose::add)
        pathDepthStencilTexture?.let {
            pendingClose += PendingCloseHandle(it, pathDepthStencilViewClose)
        }
        val clipDepthStencilViewClose = clipDepthStencilView?.let(::PendingCloseHandle)
        clipDepthStencilViewClose?.let(pendingClose::add)
        clipDepthStencilTexture?.let {
            pendingClose += PendingCloseHandle(it, clipDepthStencilViewClose)
        }
        val bindGroupClose = bindGroup?.let(::PendingCloseHandle)
        bindGroupClose?.let(pendingClose::add)
        uniformBuffer?.let { pendingClose += PendingCloseHandle(it, bindGroupClose) }
        indexBuffer?.let { pendingClose += PendingCloseHandle(it) }
        vertexBuffer?.let { pendingClose += PendingCloseHandle(it) }
    }

    private fun closePending() {
        val iterator = pendingClose.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (pending.prerequisite?.closed == false) continue
            try {
                pending.handle.close()
                pending.closed = true
                iterator.remove()
            } catch (_: Throwable) {
                // Retain only the exact failed handle for a later pool-close retry.
            }
        }
    }

    private fun capacitiesFor(
        requirements: GPUWgpu4kCorePrimitiveFramePoolRequirements,
    ): GPUWgpu4kCorePrimitiveFramePoolCapacities? {
        val vertex = powerOfTwoCapacity(requirements.vertexBytes, VERTEX_FLOOR_BYTES) ?: return null
        val index = powerOfTwoCapacity(requirements.indexBytes, INDEX_FLOOR_BYTES) ?: return null
        val uniform = powerOfTwoCapacity(requirements.uniformBytes, UNIFORM_FLOOR_BYTES) ?: return null
        return GPUWgpu4kCorePrimitiveFramePoolCapacities(vertex, index, uniform)
    }

    private fun invalidCapacityRefusal(
        requirements: GPUWgpu4kCorePrimitiveFramePoolRequirements,
    ): GPUWgpu4kCorePrimitiveFramePoolRefusal.InvalidCapacity {
        val (resource, bytes) = listOf(
            GPUWgpu4kCorePrimitiveFramePoolResource.VertexBuffer to requirements.vertexBytes,
            GPUWgpu4kCorePrimitiveFramePoolResource.IndexBuffer to requirements.indexBytes,
            GPUWgpu4kCorePrimitiveFramePoolResource.UniformBuffer to requirements.uniformBytes,
        ).first { (_, requested) -> requested <= 0L || powerOfTwoCapacity(requested, 1L) == null }
        return GPUWgpu4kCorePrimitiveFramePoolRefusal.InvalidCapacity(resource, bytes)
    }

    private fun powerOfTwoCapacity(requestedBytes: Long, floorBytes: Long): Long? {
        if (requestedBytes <= 0L) return null
        val required = maxOf(requestedBytes, floorBytes)
        var capacity = floorBytes
        while (capacity < required) {
            if (capacity > Long.MAX_VALUE / 2L) return null
            capacity *= 2L
        }
        return capacity
    }

    private fun GPUWgpu4kCorePrimitiveFramePoolCapacities.contains(
        other: GPUWgpu4kCorePrimitiveFramePoolCapacities,
    ): Boolean = vertexBytes >= other.vertexBytes && indexBytes >= other.indexBytes &&
        uniformBytes >= other.uniformBytes

    private fun GPUWgpu4kCorePrimitiveFramePoolHandles.matches(
        pathRequirement: GPUWgpu4kCorePrimitivePathDepthStencilRequirement?,
        clipRequirement: GPUWgpu4kCorePrimitiveClipDepthStencilRequirement?,
        componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
    ): Boolean = this.componentIdentity == componentIdentity &&
        (pathRequirement == null || pathDepthStencil?.requirement == pathRequirement) &&
        (clipRequirement == null || clipDepthStencil?.requirement == clipRequirement)

    private fun selectAvailableSlot(
        available: List<Slot>,
        requiredCapacities: GPUWgpu4kCorePrimitiveFramePoolCapacities,
        pathDepthStencilRequirement: GPUWgpu4kCorePrimitivePathDepthStencilRequirement?,
        clipDepthStencilRequirement: GPUWgpu4kCorePrimitiveClipDepthStencilRequirement?,
        componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
    ): Slot? {
        fun Slot.hasCapacities() = capacities.contains(requiredCapacities)
        fun Slot.hasComponent() = handles.componentIdentity == componentIdentity
        if (pathDepthStencilRequirement == null && clipDepthStencilRequirement == null) {
            return available.firstOrNull { slot -> slot.hasCapacities() && slot.hasComponent() }
                ?: available.firstOrNull { slot -> slot.hasComponent() }
                ?: available.firstOrNull { slot -> slot.hasCapacities() }
                ?: available.firstOrNull()
        }
        return available.firstOrNull { slot ->
            slot.hasCapacities() && slot.handles.matches(
                pathDepthStencilRequirement,
                clipDepthStencilRequirement,
                componentIdentity,
            )
        } ?: available.firstOrNull { slot ->
            slot.handles.matches(
                pathDepthStencilRequirement,
                clipDepthStencilRequirement,
                componentIdentity,
            )
        } ?: available.firstOrNull { slot ->
            slot.hasCapacities() && slot.hasComponent() &&
                (pathDepthStencilRequirement == null || slot.handles.pathDepthStencil == null) &&
                (clipDepthStencilRequirement == null || slot.handles.clipDepthStencil == null)
        } ?: available.firstOrNull { slot ->
            slot.hasCapacities() && slot.hasComponent()
        } ?: available.firstOrNull { slot ->
            slot.hasComponent() &&
                (pathDepthStencilRequirement == null || slot.handles.pathDepthStencil == null) &&
                (clipDepthStencilRequirement == null || slot.handles.clipDepthStencil == null)
        } ?: available.firstOrNull { slot ->
            slot.hasComponent()
        } ?: available.firstOrNull { slot ->
            slot.hasCapacities() &&
                (pathDepthStencilRequirement == null || slot.handles.pathDepthStencil == null) &&
                (clipDepthStencilRequirement == null || slot.handles.clipDepthStencil == null)
        } ?: available.firstOrNull { slot ->
            slot.hasCapacities()
        } ?: available.firstOrNull { slot ->
            (pathDepthStencilRequirement == null || slot.handles.pathDepthStencil == null) &&
                (clipDepthStencilRequirement == null || slot.handles.clipDepthStencil == null)
        } ?: available.firstOrNull()
    }

    private sealed interface SlotCreation {
        data class Created(val slot: Slot) : SlotCreation
        data class Refused(val reason: GPUWgpu4kCorePrimitiveFramePoolRefusal) : SlotCreation
    }

    private companion object {
        const val MAX_SLOTS = 3
        const val VERTEX_FLOOR_BYTES = 16L * 1024L
        const val INDEX_FLOOR_BYTES = 4L * 1024L
        const val UNIFORM_FLOOR_BYTES = 4L * 1024L
    }
}
