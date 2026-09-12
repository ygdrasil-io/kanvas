package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.gpu.plan.GradientStopSlabPlanV1

/** One completion-owned allocation/upload, borrowed by every gradient group in this frame. */
internal fun materializeGradientStopsV1(device: GPUDevice, queue: GPUQueue, slab: GradientStopSlabPlanV1,
    owned: GPUW5aSourceOwnedHandlesV2): GPUBuffer {
    val bytes = ByteBuffer.allocate(Math.toIntExact(slab.byteSizeI64)).order(ByteOrder.LITTLE_ENDIAN)
    slab.copyStops().forEach { stop ->
        bytes.putFloat(stop.positionF32)
        repeat(3) { bytes.putFloat(0f) }
        val color = stop.straightSrgbF32
        listOf(color.red, color.green, color.blue, color.alpha).forEach(bytes::putFloat)
    }
    return owned.own(device.createBuffer(BufferDescriptor(label = "Kanvas.w5c.frame-stops-v1",
        size = slab.byteSizeI64.toULong(), usage = GPUBufferUsage.Storage or GPUBufferUsage.CopyDst))).also {
        queue.writeBuffer(it, 0uL, ArrayBuffer.of(bytes.array()), 0uL, slab.byteSizeI64.toULong())
    }
}
