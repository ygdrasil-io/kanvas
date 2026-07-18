package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.DepthStencilState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUColorWrite
import io.ygdrasil.webgpu.GPUCompareFunction
import io.ygdrasil.webgpu.GPUCullMode
import io.ygdrasil.webgpu.GPUFrontFace
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPUPrimitiveTopology
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUStencilOperation
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUVertexFormat
import io.ygdrasil.webgpu.MultisampleState
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.StencilFaceState
import io.ygdrasil.webgpu.VertexAttribute
import io.ygdrasil.webgpu.VertexBufferLayout
import io.ygdrasil.webgpu.VertexState
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveDirectPathDepthStencilState

/** Closed native programs materialized by the bounded CorePrimitive WebGPU lane. */
internal enum class GPUWgpu4kCorePrimitivePipelineProgram {
    DirectSrcOver,
    DirectSrcOverWithPathDepthStencil,
    PathStencilProducerWinding,
    PathStencilProducerEvenOdd,
    PathStencilCoverRegular,
    PathStencilCoverInverse,
}

internal sealed interface GPUWgpu4kCorePrimitivePipelineMapping {
    data class Mapped(
        val identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity,
    ) : GPUWgpu4kCorePrimitivePipelineMapping

    data class Refused(
        val reason: String,
    ) : GPUWgpu4kCorePrimitivePipelineMapping
}

/**
 * Consumes the handle-free structural authority and accepts only one of the six exact native
 * descriptors. Dynamic geometry, bounds, scissor, load/store, and stencil reference never enter
 * this identity.
 */
internal fun mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(
    structuralKey: GPUCorePrimitiveRenderPipelineStructuralKey,
): GPUWgpu4kCorePrimitivePipelineMapping {
    val program = structuralKey.nativeProgramOrNull()
        ?: return GPUWgpu4kCorePrimitivePipelineMapping.Refused(
            "CorePrimitive structural state is not one exact native direct or path-stencil program.",
        )
    return GPUWgpu4kCorePrimitivePipelineMapping.Mapped(
        GPUWgpu4kCorePrimitiveRenderPipelineIdentity(
            targetFormat = "rgba8unorm",
            sampleCount = 1,
            topology = "triangle-list",
            frontFace = "ccw",
            cullMode = "none",
            program = program,
        ),
    )
}

private fun GPUCorePrimitiveRenderPipelineStructuralKey.nativeProgramOrNull():
    GPUWgpu4kCorePrimitivePipelineProgram? {
    if (sampleCount != 1 || frontFace != GPUCorePrimitiveRenderPipelineStructuralKey.FrontFace.Ccw ||
        cullMode != GPUCorePrimitiveRenderPipelineStructuralKey.CullMode.None ||
        colorFormat != GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8Unorm ||
        clip != GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None
    ) return null

    return when (role) {
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading -> when {
            shader != GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry ||
                topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList ||
                !blend.isCanonicalPremulSrcOver() -> null
            depthStencil == GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None ->
                GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOver
            depthStencil == corePrimitiveDirectPathDepthStencilState() ->
                GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOverWithPathDepthStencil
            else -> null
        }
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer -> when {
            shader != GPUCorePrimitiveRenderPipelineStructuralKey.Shader.PathStencil ||
                topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StencilEdgeFan &&
                topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StrokeStencilEdgeFan ||
                blend != GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ColorWriteNone -> null
            depthStencil == PRODUCER_WINDING_STATE ->
                GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerWinding
            depthStencil == PRODUCER_EVEN_ODD_STATE ->
                GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerEvenOdd
            else -> null
        }
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover -> when {
            shader != GPUCorePrimitiveRenderPipelineStructuralKey.Shader.PathStencil ||
                topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList ||
                !blend.isCanonicalPremulSrcOver() -> null
            depthStencil == REGULAR_COVER_STATE ->
                GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverRegular
            depthStencil == INVERSE_COVER_STATE ->
                GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverInverse
            else -> null
        }
    }
}

private fun GPUCorePrimitiveRenderPipelineStructuralKey.Blend.isCanonicalPremulSrcOver(): Boolean {
    val fixed = this as? GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed ?: return false
    return fixed.mode == GPUBlendMode.SRC_OVER &&
        fixed.sourceCoverage == GPUSourceCoverageEncoding.None &&
        fixed.state.color.sourceFactor == "one" &&
        fixed.state.color.destinationFactor == "one-minus-src-alpha" &&
        fixed.state.color.operation == "add" &&
        fixed.state.alpha.sourceFactor == "one" &&
        fixed.state.alpha.destinationFactor == "one-minus-src-alpha" &&
        fixed.state.alpha.operation == "add" &&
        fixed.state.writeMask == "rgba"
}

private val PRODUCER_WINDING_STATE = stencilState(
    front = structuralFace(pass = GPUClipStencilOperation.IncrementWrap),
    back = structuralFace(pass = GPUClipStencilOperation.DecrementWrap),
    writeMask = 0xffu,
)

private val PRODUCER_EVEN_ODD_STATE = stencilState(
    front = structuralFace(pass = GPUClipStencilOperation.Invert),
    back = structuralFace(pass = GPUClipStencilOperation.Invert),
    writeMask = 0x01u,
)

private val REGULAR_COVER_STATE = stencilState(
    front = structuralFace(
        compare = GPUClipStencilCompare.NotEqual,
        pass = GPUClipStencilOperation.Zero,
        depthFail = GPUClipStencilOperation.Zero,
    ),
    back = structuralFace(
        compare = GPUClipStencilCompare.NotEqual,
        pass = GPUClipStencilOperation.Zero,
        depthFail = GPUClipStencilOperation.Zero,
    ),
    writeMask = 0xffu,
)

private val INVERSE_COVER_STATE = stencilState(
    front = structuralFace(
        compare = GPUClipStencilCompare.Equal,
        pass = GPUClipStencilOperation.Keep,
        fail = GPUClipStencilOperation.Zero,
    ),
    back = structuralFace(
        compare = GPUClipStencilCompare.Equal,
        pass = GPUClipStencilOperation.Keep,
        fail = GPUClipStencilOperation.Zero,
    ),
    writeMask = 0xffu,
)

private fun stencilState(
    front: GPUCorePrimitiveRenderPipelineStructuralKey.StencilFace,
    back: GPUCorePrimitiveRenderPipelineStructuralKey.StencilFace,
    writeMask: UInt,
) = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.Stencil(
    format = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencilFormat.Depth24PlusStencil8,
    front = front,
    back = back,
    readMask = 0xffu,
    writeMask = writeMask,
)

private fun structuralFace(
    compare: GPUClipStencilCompare = GPUClipStencilCompare.Always,
    pass: GPUClipStencilOperation,
    fail: GPUClipStencilOperation = GPUClipStencilOperation.Keep,
    depthFail: GPUClipStencilOperation = GPUClipStencilOperation.Keep,
) = GPUCorePrimitiveRenderPipelineStructuralKey.StencilFace(compare, pass, fail, depthFail)

/** Pure descriptor lowering kept separate from native allocation and cache transactions. */
internal fun corePrimitiveWgpu4kRenderPipelineDescriptor(
    identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity,
    shader: GPUShaderModule,
    pipelineLayout: GPUPipelineLayout,
): RenderPipelineDescriptor {
    require(isSupportedCorePrimitiveRenderPipelineIdentity(identity)) {
        "Unsupported CorePrimitive native pipeline identity: $identity"
    }
    val stencilProgram = identity.program != GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOver
    val producer = identity.program == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerWinding ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerEvenOdd
    return RenderPipelineDescriptor(
        label = "Kanvas.session.corePrimitive.pipeline.${identity.program.name}",
        layout = pipelineLayout,
        vertex = VertexState(
            module = shader,
            entryPoint = CORE_PRIMITIVE_NATIVE_VERTEX_ENTRY_POINT,
            buffers = listOf(
                VertexBufferLayout(
                    arrayStride = 8uL,
                    attributes = listOf(
                        VertexAttribute(
                            shaderLocation = 0u,
                            offset = 0uL,
                            format = GPUVertexFormat.Float32x2,
                        ),
                    ),
                ),
            ),
        ),
        primitive = PrimitiveState(
            topology = GPUPrimitiveTopology.TriangleList,
            frontFace = GPUFrontFace.CCW,
            cullMode = GPUCullMode.None,
        ),
        depthStencil = if (stencilProgram) identity.program.depthStencilState() else null,
        multisample = MultisampleState(count = 1u),
        fragment = FragmentState(
            module = shader,
            entryPoint = if (producer) {
                CORE_PRIMITIVE_NATIVE_STENCIL_FRAGMENT_ENTRY_POINT
            } else {
                CORE_PRIMITIVE_NATIVE_COLOR_FRAGMENT_ENTRY_POINT
            },
            targets = listOf(
                ColorTargetState(
                    format = GPUTextureFormat.RGBA8Unorm,
                    blend = if (producer) null else premulSrcOverBlendState(),
                    writeMask = if (producer) GPUColorWrite.None else GPUColorWrite.All,
                ),
            ),
        ),
    )
}

internal fun isSupportedCorePrimitiveRenderPipelineIdentity(
    identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity,
): Boolean = identity.targetFormat == "rgba8unorm" && identity.sampleCount == 1 &&
    identity.topology == "triangle-list" && identity.frontFace == "ccw" &&
    identity.cullMode == "none"

private fun GPUWgpu4kCorePrimitivePipelineProgram.depthStencilState(): DepthStencilState {
    val (front, back, readMask, writeMask) = when (this) {
        GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerWinding ->
            NativeStencilState(
                face(pass = GPUStencilOperation.IncrementWrap),
                face(pass = GPUStencilOperation.DecrementWrap),
                0xffu,
                0xffu,
            )
        GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerEvenOdd ->
            NativeStencilState(
                face(pass = GPUStencilOperation.Invert),
                face(pass = GPUStencilOperation.Invert),
                0xffu,
                0x01u,
            )
        GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverRegular ->
            NativeStencilState(
                face(
                    compare = GPUCompareFunction.NotEqual,
                    depthFail = GPUStencilOperation.Zero,
                    pass = GPUStencilOperation.Zero,
                ),
                face(
                    compare = GPUCompareFunction.NotEqual,
                    depthFail = GPUStencilOperation.Zero,
                    pass = GPUStencilOperation.Zero,
                ),
                0xffu,
                0xffu,
            )
        GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverInverse ->
            NativeStencilState(
                face(
                    compare = GPUCompareFunction.Equal,
                    fail = GPUStencilOperation.Zero,
                    pass = GPUStencilOperation.Keep,
                ),
                face(
                    compare = GPUCompareFunction.Equal,
                    fail = GPUStencilOperation.Zero,
                    pass = GPUStencilOperation.Keep,
                ),
                0xffu,
                0xffu,
            )
        GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOverWithPathDepthStencil ->
            NativeStencilState(
                face(pass = GPUStencilOperation.Keep),
                face(pass = GPUStencilOperation.Keep),
                0u,
                0u,
            )
        GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOver ->
            error("DirectSrcOver has no depth/stencil state")
    }
    return DepthStencilState(
        format = GPUTextureFormat.Depth24PlusStencil8,
        depthWriteEnabled = false,
        depthCompare = GPUCompareFunction.Always,
        stencilFront = front,
        stencilBack = back,
        stencilReadMask = readMask,
        stencilWriteMask = writeMask,
    )
}

private data class NativeStencilState(
    val front: StencilFaceState,
    val back: StencilFaceState,
    val readMask: UInt,
    val writeMask: UInt,
)

private fun face(
    compare: GPUCompareFunction = GPUCompareFunction.Always,
    fail: GPUStencilOperation = GPUStencilOperation.Keep,
    depthFail: GPUStencilOperation = GPUStencilOperation.Keep,
    pass: GPUStencilOperation,
) = StencilFaceState(compare, fail, depthFail, pass)

private fun premulSrcOverBlendState() = BlendState(
    color = BlendComponent(
        GPUBlendOperation.Add,
        GPUBlendFactor.One,
        GPUBlendFactor.OneMinusSrcAlpha,
    ),
    alpha = BlendComponent(
        GPUBlendOperation.Add,
        GPUBlendFactor.One,
        GPUBlendFactor.OneMinusSrcAlpha,
    ),
)
