package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket

/** One W4e native operand recipe, shared by standalone and graph-bound layered execution. */
internal fun w4eNativeOperandKeysV6(w4ePacket: GPUDrawPacket,
    commonSource: Boolean = w4ePacket.w5bFinalFrameWitnessV3?.w4eLane?.owns(w4ePacket) == true): List<GPUPreparedNativeOperandKey> {
    fun key(role: GPUPreparedNativeOperandRole, kind: GPUPreparedNativeOperandKind, binding: String) =
        GPUPreparedNativeOperandKey(role, kind, gpuPreparedNativeBindingKey(binding), GPUPreparedNativeOperandOwnership.Borrowed)
    val preparedPass = w4ePacket.w4ePreparedClipPass
    val preparedPath = w4ePacket.w4ePreparedPath
    return when {
        preparedPass is org.graphiks.kanvas.gpu.renderer.passes
            .GPUW4ePreparedClipPassAuthority.Initialize ||
            preparedPass is org.graphiks.kanvas.gpu.renderer.passes
            .GPUW4ePreparedClipPassAuthority.PathMaskClear ->
            listOf(
                key(GPUPreparedNativeOperandRole.RenderColorTarget,
                    GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:target"),
                key(GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:clear"),
            )
        preparedPass is org.graphiks.kanvas.gpu.renderer.passes
            .GPUW4ePreparedClipPassAuthority.Producer -> buildList {
            if (preparedPass.sampleCount == 4) {
                add(key(GPUPreparedNativeOperandRole.RenderMsaaColorTarget,
                    GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:target"))
                add(key(GPUPreparedNativeOperandRole.RenderResolveTarget,
                    GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:resolve"))
            } else {
                add(key(GPUPreparedNativeOperandRole.RenderColorTarget,
                    GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:target"))
            }
            preparedPass.depthStencilResourceId?.let {
                add(key(GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                    GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:depth"))
            }
            if (preparedPass.geometry is org.graphiks.kanvas.gpu.renderer.passes
                    .GPUW4ePreparedClipGeometry.Path
            ) {
                val pathGeometry = preparedPass.geometry.copyPathGeometryF32()
                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:path-producer"))
                add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer,
                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:path-vertices"))
                add(key(GPUPreparedNativeOperandRole.RenderIndexBuffer,
                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:path-indices"))
                if (pathGeometry.copyDirectTriangleF32OrNull() == null) {
                    add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                        GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:path-cover"))
                }
            } else {
                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:pipeline"))
                add(key(GPUPreparedNativeOperandRole.RenderBindGroup,
                    GPUPreparedNativeOperandKind.BindGroup, "w4e:${w4ePacket.passId}:producer"))
            }
        }
        preparedPass is org.graphiks.kanvas.gpu.renderer.passes
            .GPUW4ePreparedClipPassAuthority.Fold -> listOf(
            key(GPUPreparedNativeOperandRole.RenderColorTarget,
                GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:target"),
            key(GPUPreparedNativeOperandRole.RenderPipeline,
                GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:pipeline"),
            key(GPUPreparedNativeOperandRole.RenderBindGroup,
                GPUPreparedNativeOperandKind.BindGroup, "w4e:${w4ePacket.passId}:fold"),
        )
        preparedPath != null -> buildList {
            if (preparedPath.sample == org.graphiks.kanvas.gpu.plan.SamplePlan.Multisample4) {
                add(key(GPUPreparedNativeOperandRole.RenderMsaaColorTarget,
                    GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:target"))
                preparedPath.resolveTargetResourceId?.let {
                    add(key(GPUPreparedNativeOperandRole.RenderResolveTarget,
                        GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:resolve"))
                }
            } else {
                add(key(GPUPreparedNativeOperandRole.RenderColorTarget,
                    GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:target"))
            }
            if (preparedPath.depthStencilResourceId != null) {
                add(key(GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                    GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:depth"))
            }
            val directPath = when (val geometry = preparedPath.copyGeometry()) {
                is org.graphiks.kanvas.gpu.plan.PathDrawGeometry.Fill ->
                    geometry.valueF32.copyDirectTriangleF32OrNull() != null
                is org.graphiks.kanvas.gpu.plan.PathDrawGeometry.Stroke ->
                    geometry.valueF32.copyFillGeometryF32().copyDirectTriangleF32OrNull() != null
                is org.graphiks.kanvas.gpu.plan.PathDrawGeometry.InverseDomainSource -> false
                org.graphiks.kanvas.gpu.plan.PathDrawGeometry.Empty -> false
            }
            val inverseDomainConsumer = w4ePacket.w4ePreparedClipConsumer as?
                org.graphiks.kanvas.gpu.renderer.passes.GPUW4ePreparedClipConsumerAuthority.InverseDomain
            val stencilProducer = preparedPath.phase in setOf(
                org.graphiks.kanvas.gpu.plan.PathRenderPhase.SingleSampleStencilProducer,
                org.graphiks.kanvas.gpu.plan.PathRenderPhase.MultisampleStencilProducer,
                org.graphiks.kanvas.gpu.plan.PathRenderPhase.HardEdgeMaskStencilProducer,
            )
            val hardMaskStencilCover = preparedPath.phase ==
                org.graphiks.kanvas.gpu.plan.PathRenderPhase.HardEdgeMaskStencilCover
            val hardMaskProducer = preparedPath.phase ==
                org.graphiks.kanvas.gpu.plan.PathRenderPhase.HardEdgeMaskProducer
            val stencilCover = preparedPath.phase in setOf(
                org.graphiks.kanvas.gpu.plan.PathRenderPhase.SingleSampleStencilColorCover,
                org.graphiks.kanvas.gpu.plan.PathRenderPhase.MultisampleStencilColorCover,
                org.graphiks.kanvas.gpu.plan.PathRenderPhase.HardEdgeMaskStencilCover,
            )
            if (hardMaskProducer) {
                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:hard-mask-producer"))
                add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer,
                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:hard-mask-vertices"))
                add(key(GPUPreparedNativeOperandRole.RenderIndexBuffer,
                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:hard-mask-indices"))
            } else if (stencilProducer) {
                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:stencil-producer"))
                add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer,
                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:stencil-vertices"))
                add(key(GPUPreparedNativeOperandRole.RenderIndexBuffer,
                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:stencil-indices"))
            } else if (hardMaskStencilCover) {
                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:stencil-cover"))
            } else if (inverseDomainConsumer?.interiorCoverage is org.graphiks.kanvas.gpu.renderer.passes
                    .GPUW4ePreparedInverseInteriorCoverage.Geometry && stencilCover
            ) {
                if (!commonSource) {
                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:inverse-domain-interior"))
                add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer,
                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:inverse-domain-interior-vertices"))
                add(key(GPUPreparedNativeOperandRole.RenderIndexBuffer,
                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:inverse-domain-interior-indices"))
                }
                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:inverse-domain-cover"))
                add(key(GPUPreparedNativeOperandRole.RenderBindGroup,
                    GPUPreparedNativeOperandKind.BindGroup, "w4e:${w4ePacket.passId}:inverse-domain-color"))
            } else if (inverseDomainConsumer != null && inverseDomainConsumer.interiorCoverage is
                org.graphiks.kanvas.gpu.renderer.passes.GPUW4ePreparedInverseInteriorCoverage.Zero
            ) {
                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:inverse-domain-cover"))
                add(key(GPUPreparedNativeOperandRole.RenderBindGroup,
                    GPUPreparedNativeOperandKind.BindGroup, "w4e:${w4ePacket.passId}:inverse-domain-color"))
            } else if (inverseDomainConsumer != null) {
                if (!commonSource) {
                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:inverse-domain-main"))
                add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer,
                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:inverse-domain-main-vertices"))
                add(key(GPUPreparedNativeOperandRole.RenderIndexBuffer,
                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:inverse-domain-main-indices"))
                }
                if (inverseDomainConsumer.interiorCoverage is org.graphiks.kanvas.gpu.renderer.passes
                        .GPUW4ePreparedInverseInteriorCoverage.Geometry
                ) {
                    add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                        GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:inverse-domain-interior"))
                    add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer,
                        GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:inverse-domain-interior-vertices"))
                    add(key(GPUPreparedNativeOperandRole.RenderIndexBuffer,
                        GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:inverse-domain-interior-indices"))
                }
                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:inverse-domain-cover"))
                add(key(GPUPreparedNativeOperandRole.RenderBindGroup,
                    GPUPreparedNativeOperandKind.BindGroup, "w4e:${w4ePacket.passId}:inverse-domain-color"))
            } else {
                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:pipeline"))
                add(key(GPUPreparedNativeOperandRole.RenderBindGroup,
                    GPUPreparedNativeOperandKind.BindGroup, "w4e:${w4ePacket.passId}:consumer"))
                if (directPath && !stencilCover && preparedPath.phase !=
                    org.graphiks.kanvas.gpu.plan.PathRenderPhase.HardEdgeBinaryColorCover
                ) {
                    add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer,
                        GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:consumer-vertices"))
                    add(key(GPUPreparedNativeOperandRole.RenderIndexBuffer,
                        GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:consumer-indices"))
                }
            }
        }
        else -> error("W4e packet has no sealed pass authority")
    }
}

