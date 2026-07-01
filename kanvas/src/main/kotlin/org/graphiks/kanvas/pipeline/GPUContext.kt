package org.graphiks.kanvas.pipeline

interface GPUContext {
    fun createShaderModule(source: String): ShaderModule
    fun createPipeline(desc: RenderPipeline): GPUHandle
    fun createUniformBuffer(data: UniformBlock): GPUHandle
    fun beginRenderPass(desc: RenderPassDescriptor): RenderPass
}
