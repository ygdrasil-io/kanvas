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
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePathStencilStructuralProgram
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilStructuralProgram
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskStructuralProgram
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.passes.clipStencilStructuralProgramOrNull
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveDirectPathDepthStencilState
import org.graphiks.kanvas.gpu.renderer.passes.coverageMaskStructuralProgramOrNull
import org.graphiks.kanvas.gpu.renderer.passes.pathStencilStructuralProgramOrNull

/** Closed native programs materialized by the bounded CorePrimitive WebGPU lane. */
internal enum class GPUWgpu4kCorePrimitivePipelineProgram {
    DirectSrcOver,
    DirectSrcOverWithPathDepthStencil,
    AnalyticClipRectHard,
    AnalyticClipRectAA,
    AnalyticClipRRectHard,
    AnalyticClipRRectAA,
    AnalyticClipIntersection4,
    PathStencilProducerWinding,
    PathStencilProducerEvenOdd,
    PathStencilCoverRegular,
    PathStencilCoverInverse,
    ClipStencilProducerWinding,
    ClipStencilProducerEvenOdd,
    ClipStencilConsumerRegular,
    ClipStencilConsumerInverse,
    CoverageMaskProducerRectIntersect,
    CoverageMaskProducerRectDifference,
    CoverageMaskProducerRRectIntersect,
    CoverageMaskProducerRRectDifference,
    CoverageMaskConsumerNearest,
}

internal sealed interface GPUWgpu4kCorePrimitivePipelineMapping {
    data class Mapped(
        val identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity,
        val componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
    ) : GPUWgpu4kCorePrimitivePipelineMapping

    data class Refused(
        val reason: String,
    ) : GPUWgpu4kCorePrimitivePipelineMapping
}

/**
 * Consumes the handle-free structural authority and accepts only one of the twenty exact native
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
        componentIdentity = when {
            program.isClipStencilProducer() ->
                PRODUCTION_CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_COMPONENT_IDENTITY
            program.isCoverageMaskProducer() ->
                PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_COMPONENT_IDENTITY
            program.isCoverageMaskConsumer() ->
                PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_COMPONENT_IDENTITY
            program.isAnalyticIntersection4() ->
                PRODUCTION_CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_COMPONENT_IDENTITY
            program.isAnalyticClip() -> PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY
            else -> PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY
        },
    )
}

private fun GPUCorePrimitiveRenderPipelineStructuralKey.nativeProgramOrNull():
    GPUWgpu4kCorePrimitivePipelineProgram? {
    if (sampleCount != 1 || frontFace != GPUCorePrimitiveRenderPipelineStructuralKey.FrontFace.Ccw ||
        cullMode != GPUCorePrimitiveRenderPipelineStructuralKey.CullMode.None ||
        colorFormat != GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8Unorm
    ) return null

    return when (role) {
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading -> when {
            shader != GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry ||
                topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList ||
                !blend.isCanonicalPremulSrcOver() -> null
            clip is GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Analytic &&
                depthStencil == GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None ->
                clip.nativeAnalyticProgramOrNull()
            clip == GPUCorePrimitiveRenderPipelineStructuralKey.Clip.AnalyticIntersection4 &&
                depthStencil == GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None ->
                GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipIntersection4
            clip != GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None -> null
            depthStencil == GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None ->
                GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOver
            depthStencil == corePrimitiveDirectPathDepthStencilState() ->
                GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOverWithPathDepthStencil
            else -> null
        }
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer -> when {
            clip != GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None -> null
            shader != GPUCorePrimitiveRenderPipelineStructuralKey.Shader.PathStencil ||
                topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StencilEdgeFan &&
                topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StrokeStencilEdgeFan ||
                blend != GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ColorWriteNone -> null
            depthStencil.pathStencilStructuralProgramOrNull() ==
                GPUCorePrimitivePathStencilStructuralProgram.ProducerWinding ->
                GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerWinding
            depthStencil.pathStencilStructuralProgramOrNull() ==
                GPUCorePrimitivePathStencilStructuralProgram.ProducerEvenOdd ->
                GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerEvenOdd
            else -> null
        }
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover -> when {
            clip != GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None -> null
            shader != GPUCorePrimitiveRenderPipelineStructuralKey.Shader.PathStencil ||
                topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList ||
                !blend.isCanonicalPremulSrcOver() -> null
            depthStencil.pathStencilStructuralProgramOrNull() ==
                GPUCorePrimitivePathStencilStructuralProgram.CoverRegular ->
                GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverRegular
            depthStencil.pathStencilStructuralProgramOrNull() ==
                GPUCorePrimitivePathStencilStructuralProgram.CoverInverse ->
                GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverInverse
            else -> null
        }
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilProducer -> when {
            clip != GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None ||
                shader != GPUCorePrimitiveRenderPipelineStructuralKey.Shader.ClipStencilProducer ||
                topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StencilEdgeFan ||
                blend != GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ColorWriteNone -> null
            clipStencilStructuralProgramOrNull() ==
                GPUCorePrimitiveClipStencilStructuralProgram.ProducerWinding ->
                GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerWinding
            clipStencilStructuralProgramOrNull() ==
                GPUCorePrimitiveClipStencilStructuralProgram.ProducerEvenOdd ->
                GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerEvenOdd
            else -> null
        }
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilConsumer -> when {
            clip != GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None ||
                shader != GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry ||
                topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList ||
                !blend.isCanonicalPremulSrcOver() -> null
            clipStencilStructuralProgramOrNull() ==
                GPUCorePrimitiveClipStencilStructuralProgram.ConsumerRegular ->
                GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRegular
            clipStencilStructuralProgramOrNull() ==
                GPUCorePrimitiveClipStencilStructuralProgram.ConsumerInverse ->
                GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerInverse
            else -> null
        }
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskProducer -> when (
            coverageMaskStructuralProgramOrNull()
        ) {
            GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRectIntersect ->
                GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectIntersect
            GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRectDifference ->
                GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectDifference
            GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRRectIntersect ->
                GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectIntersect
            GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRRectDifference ->
                GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectDifference
            GPUCorePrimitiveCoverageMaskStructuralProgram.ConsumerNearest,
            null,
            -> null
        }
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskConsumer -> when {
            coverageMaskStructuralProgramOrNull() ==
                GPUCorePrimitiveCoverageMaskStructuralProgram.ConsumerNearest &&
                blend.isCanonicalPremulSrcOver() ->
                GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskConsumerNearest
            else -> null
        }
    }
}

private fun GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Analytic.nativeAnalyticProgramOrNull():
    GPUWgpu4kCorePrimitivePipelineProgram? = when (geometry) {
    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect -> if (antiAlias) {
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRectAA
    } else {
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRectHard
    }
    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect -> if (antiAlias) {
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRRectAA
    } else {
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRRectHard
    }
    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Path -> null
}

internal fun GPUWgpu4kCorePrimitivePipelineProgram.isAnalyticClip(): Boolean = when (this) {
    GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRectHard,
    GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRectAA,
    GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRRectHard,
    GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRRectAA,
    -> true
    else -> false
}

internal fun GPUWgpu4kCorePrimitivePipelineProgram.isAnalyticIntersection4(): Boolean =
    this == GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipIntersection4

internal fun GPUWgpu4kCorePrimitivePipelineProgram.isClipStencilProducer(): Boolean = when (this) {
    GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerWinding,
    GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerEvenOdd,
    -> true
    else -> false
}

internal fun GPUWgpu4kCorePrimitivePipelineProgram.isCoverageMaskProducer(): Boolean = when (this) {
    GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectIntersect,
    GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectDifference,
    GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectIntersect,
    GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectDifference,
    -> true
    else -> false
}

internal fun GPUWgpu4kCorePrimitivePipelineProgram.isCoverageMaskConsumer(): Boolean =
    this == GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskConsumerNearest

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

/** Pure descriptor lowering kept separate from native allocation and cache transactions. */
internal fun corePrimitiveWgpu4kRenderPipelineDescriptor(
    identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity,
    shader: GPUShaderModule,
    pipelineLayout: GPUPipelineLayout,
): RenderPipelineDescriptor {
    require(isSupportedCorePrimitiveRenderPipelineIdentity(identity)) {
        "Unsupported CorePrimitive native pipeline identity: $identity"
    }
    val stencilProgram = identity.program ==
        GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOverWithPathDepthStencil ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerWinding ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerEvenOdd ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverRegular ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverInverse ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerWinding ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerEvenOdd ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRegular ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerInverse
    val producer = identity.program == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerWinding ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerEvenOdd ||
        identity.program.isClipStencilProducer()
    val coverageMaskProducer = identity.program.isCoverageMaskProducer()
    return RenderPipelineDescriptor(
        label = "Kanvas.session.corePrimitive.pipeline.${identity.program.name}",
        layout = pipelineLayout,
        vertex = VertexState(
            module = shader,
            entryPoint = if (identity.program.isClipStencilProducer()) {
                CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_NATIVE_VERTEX_ENTRY_POINT
            } else if (coverageMaskProducer) {
                CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_VERTEX_ENTRY_POINT
            } else if (identity.program.isCoverageMaskConsumer()) {
                CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_VERTEX_ENTRY_POINT
            } else {
                CORE_PRIMITIVE_NATIVE_VERTEX_ENTRY_POINT
            },
            buffers = if (coverageMaskProducer) emptyList() else listOf(
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
            entryPoint = if (identity.program.isClipStencilProducer()) {
                CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_NATIVE_FRAGMENT_ENTRY_POINT
            } else if (identity.program.isCoverageMaskProducer()) {
                identity.program.coverageMaskProducerFragmentEntryPoint()
            } else if (identity.program.isCoverageMaskConsumer()) {
                CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_FRAGMENT_ENTRY_POINT
            } else if (producer) {
                CORE_PRIMITIVE_NATIVE_STENCIL_FRAGMENT_ENTRY_POINT
            } else {
                CORE_PRIMITIVE_NATIVE_COLOR_FRAGMENT_ENTRY_POINT
            },
            targets = listOf(
                ColorTargetState(
                    format = GPUTextureFormat.RGBA8Unorm,
                    blend = when {
                        producer -> null
                        coverageMaskProducer -> identity.program.coverageMaskProducerBlendState()
                        else -> premulSrcOverBlendState()
                    },
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
        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerWinding ->
            NativeStencilState(
                face(pass = GPUStencilOperation.IncrementWrap),
                face(pass = GPUStencilOperation.DecrementWrap),
                0xffu,
                0xffu,
            )
        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerEvenOdd ->
            NativeStencilState(
                face(pass = GPUStencilOperation.Invert),
                face(pass = GPUStencilOperation.Invert),
                0xffu,
                0xffu,
            )
        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRegular ->
            NativeStencilState(
                face(compare = GPUCompareFunction.NotEqual, pass = GPUStencilOperation.Keep),
                face(compare = GPUCompareFunction.NotEqual, pass = GPUStencilOperation.Keep),
                0xffu,
                0u,
            )
        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerInverse ->
            NativeStencilState(
                face(compare = GPUCompareFunction.Equal, pass = GPUStencilOperation.Keep),
                face(compare = GPUCompareFunction.Equal, pass = GPUStencilOperation.Keep),
                0xffu,
                0u,
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
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRectHard,
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRectAA,
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRRectHard,
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRRectAA,
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipIntersection4,
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectIntersect,
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectDifference,
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectIntersect,
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectDifference,
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskConsumerNearest,
        -> error("Analytic clip programs have no depth/stencil state")
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

private fun GPUWgpu4kCorePrimitivePipelineProgram.coverageMaskProducerFragmentEntryPoint(): String =
    when (this) {
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectIntersect,
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectDifference,
        -> CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_RECT_FRAGMENT_ENTRY_POINT
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectIntersect,
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectDifference,
        -> CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_RRECT_FRAGMENT_ENTRY_POINT
        else -> error("$this is not a coverage-mask producer")
    }

private fun GPUWgpu4kCorePrimitivePipelineProgram.coverageMaskProducerBlendState(): BlendState {
    val destinationFactor = when (this) {
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectIntersect,
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectIntersect,
        -> GPUBlendFactor.SrcAlpha
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectDifference,
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectDifference,
        -> GPUBlendFactor.OneMinusSrcAlpha
        else -> error("$this is not a coverage-mask producer")
    }
    return BlendState(
        color = BlendComponent(GPUBlendOperation.Add, GPUBlendFactor.Zero, destinationFactor),
        alpha = BlendComponent(GPUBlendOperation.Add, GPUBlendFactor.Zero, destinationFactor),
    )
}
