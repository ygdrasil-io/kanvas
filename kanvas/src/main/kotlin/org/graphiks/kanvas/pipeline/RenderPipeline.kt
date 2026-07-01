package org.graphiks.kanvas.pipeline

data class BlendConfig(
    val colorSrc: BlendFactor = BlendFactor.SRC_ALPHA,
    val colorDst: BlendFactor = BlendFactor.ONE_MINUS_SRC_ALPHA,
    val alphaSrc: BlendFactor = BlendFactor.ONE,
    val alphaDst: BlendFactor = BlendFactor.ONE_MINUS_SRC_ALPHA,
    val colorOp: BlendOp = BlendOp.ADD,
    val alphaOp: BlendOp = BlendOp.ADD,
) {
    companion object { val SRC_OVER = BlendConfig() }
}

data class StencilFaceConfig(
    val compare: CompareOp = CompareOp.ALWAYS,
    val failOp: StencilOp = StencilOp.KEEP,
    val depthFailOp: StencilOp = StencilOp.KEEP,
    val passOp: StencilOp = StencilOp.REPLACE,
)

data class DepthStencilConfig(
    val depthCompare: CompareOp = CompareOp.LESS,
    val depthWrite: Boolean = true,
    val stencilFront: StencilFaceConfig? = null,
    val stencilBack: StencilFaceConfig? = null,
)

data class VertexLayout(
    val attributes: List<VertexAttribute>,
    val stride: Int,
    val stepMode: VertexStepMode = VertexStepMode.VERTEX,
)

data class VertexAttribute(val format: VertexFormat, val offset: Int, val shaderLocation: Int)

data class RenderPipeline(
    val vertexShader: ShaderModule,
    val fragmentShader: ShaderModule,
    val blend: BlendConfig = BlendConfig.SRC_OVER,
    val depthStencil: DepthStencilConfig? = null,
    val topology: PrimitiveTopology = PrimitiveTopology.TRIANGLE_LIST,
) {
    companion object {
        private fun placeholderModule() = ShaderModule.fromSource("@vertex fn vs_main() -> @builtin(position) vec4f { return vec4f(); }")
        val SOLID_COLOR_FILL: RenderPipeline by lazy { RenderPipeline(placeholderModule(), placeholderModule()) }
        val LINEAR_GRADIENT_FILL: RenderPipeline by lazy { RenderPipeline(placeholderModule(), placeholderModule()) }
        val ROUNDED_RECT_SDF: RenderPipeline by lazy { RenderPipeline(placeholderModule(), placeholderModule()) }
        val TEXT_ATLAS_GLYPH: RenderPipeline by lazy { RenderPipeline(placeholderModule(), placeholderModule()) }
        val STENCIL_COVER: RenderPipeline by lazy { RenderPipeline(placeholderModule(), placeholderModule()) }
        val IMAGE_DRAW: RenderPipeline by lazy { RenderPipeline(placeholderModule(), placeholderModule()) }
        val BLUR_PASS: RenderPipeline by lazy { RenderPipeline(placeholderModule(), placeholderModule()) }
    }
}
