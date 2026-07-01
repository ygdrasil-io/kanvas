package org.graphiks.kanvas.pipeline

data class ColorAttachment(
    val texture: GPUHandle,
    val loadOp: LoadOp = LoadOp.CLEAR,
    val storeOp: StoreOp = StoreOp.STORE,
    val clearColor: org.graphiks.kanvas.types.Color = org.graphiks.kanvas.types.Color.TRANSPARENT,
)

data class DepthStencilAttachment(val texture: GPUHandle)

data class RenderPassDescriptor(
    val colorAttachments: List<ColorAttachment>,
    val depthStencilAttachment: DepthStencilAttachment? = null,
)

interface RenderPass {
    fun setPipeline(handle: GPUHandle)
    fun bindVertexBuffer(slot: Int, buffer: GPUHandle)
    fun bindUniform(group: Int, binding: Int, buffer: GPUHandle)
    fun bindTexture(group: Int, binding: Int, texture: GPUHandle)
    fun draw(vertexCount: Int, instanceCount: Int = 1)
    fun drawIndexed(indexCount: Int)
    fun end()
}
