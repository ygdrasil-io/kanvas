package org.graphiks.kanvas.gpu.plan

import java.security.MessageDigest
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.PathSegmentF32
import org.graphiks.math.geometry.PathStrokeGeometryF32
import org.graphiks.math.geometry.RectI32

/** Canonical, raw-bit-stable snapshot used only by the opaque W4d.2 compiler witness. */
@JvmSynthetic
internal fun canonicalW4dGeneralGraphDigest(graph: RenderGraph): ByteArray = canonicalW4dGeneralGraphDigest(graph.canonicalConstruction())

internal fun canonicalW4dGeneralGraphDigest(graph: RenderGraphConstruction): ByteArray {
    val materialV2 = W4dGeneralPathPlanCompiler.isW5aMaterialCapabilityId(graph.capabilityId)
    return canonicalGraphDigest(
        graph,
        if (materialV2) {
            "w4d-general-render-graph-witness-w5a-material-v2"
        } else {
            "w4d-general-render-graph-witness-v1"
        },
        materialV2,
    )
}

/** Canonical W4e inventory seal.  It shares W4d.2's path/pass serialization seam. */
@JvmSynthetic
internal fun canonicalW4eGraphDigest(graph: RenderGraph): ByteArray {
    val materialV2 = W4eClipPlanCompiler.isW5aMaterialCapabilityId(graph.capabilityId)
    return canonicalGraphDigest(
        graph.canonicalConstruction(),
        if (materialV2) {
            "w4e-complex-clip-render-graph-witness-w5a-material-v2"
        } else {
            "w4e-complex-clip-render-graph-witness-v1"
        },
        materialV2,
    )
}

private fun canonicalGraphDigest(graph: RenderGraphConstruction, schema: String, materialV2: Boolean): ByteArray {
    val writer = W4dGeneralGraphDigestWriter(graph.materialPlanTableOrNull())
    writer.text("schema", schema)
    writer.text("plan.id", graph.id.value)
    writer.text("plan.capability-id", graph.capabilityId)
    writer.i32("target.width", graph.targetExtent.width)
    writer.i32("target.height", graph.targetExtent.height)
    writer.text("target.color-format", graph.colorFormat.name)
    writer.capabilities(graph.capabilities)
    writer.i64("budget.frame-local-bytes", graph.budget.maxFrameLocalBytes)
    writer.i32("draw-count", graph.visualCommandCount)
    writer.i64("peak-frame-local-bytes", graph.peakFrameLocalBytes)
    if (materialV2) writer.materialTable(requireNotNull(graph.materialPlanTableOrNull()))

    graph.resources().forEachIndexed { index, resource -> writer.resource("resources[$index]", resource) }
    graph.passes().forEachIndexed { index, pass -> writer.pass("passes[$index]", pass, materialV2) }
    graph.dependencies().forEachIndexed { index, dependency ->
        writer.text("dependencies[$index].before", dependency.before.value)
        writer.text("dependencies[$index].after", dependency.after.value)
    }
    return writer.finish()
}

private class W4dGeneralGraphDigestWriter(private val table: MaterialPlanTable?) {
    private val digest = MessageDigest.getInstance("SHA-256")

    fun finish(): ByteArray = digest.digest()

    fun text(label: String, value: String) {
        token(label)
        token(value)
    }

    fun i32(label: String, value: Int) = text(label, value.toString())
    fun i64(label: String, value: Long) = text(label, value.toString())
    fun f32(label: String, value: Float) = i32(label, value.toRawBits())
    fun f64(label: String, value: Double) = i64(label, value.toRawBits())
    fun bool(label: String, value: Boolean) = text(label, if (value) "1" else "0")

    fun capabilities(capabilities: PlanCapabilitySnapshot) {
        i64("capabilities.device-generation", capabilities.deviceGeneration)
        i32("capabilities.max-texture-dimension-2d", capabilities.maxTextureDimension2D)
        i64("capabilities.max-buffer-size-bytes", capabilities.maxBufferSizeBytes)
        i32("capabilities.copy-bytes-per-row-alignment", capabilities.copyBytesPerRowAlignment)
        enumSet("capabilities.formats", capabilities.supportedFormats().map { it.name })
        i32("capabilities.min-uniform-offset-alignment", capabilities.minUniformBufferOffsetAlignment)
        i32("capabilities.max-dynamic-uniforms-per-layout", capabilities.maxDynamicUniformBuffersPerPipelineLayout)
        enumSet("capabilities.operations", capabilities.supportedOperations().map { it.name })
        i64("capabilities.buffer-policy.vertex-floor", capabilities.bufferAllocationPolicy.vertexFloorBytes)
        i64("capabilities.buffer-policy.index-floor", capabilities.bufferAllocationPolicy.indexFloorBytes)
        i64("capabilities.buffer-policy.uniform-floor", capabilities.bufferAllocationPolicy.uniformFloorBytes)
        text("capabilities.buffer-policy.growth", capabilities.bufferAllocationPolicy.growth.name)
        enumSet("capabilities.depth-stencil-formats", capabilities.supportedDepthStencilFormats().map { it.name })
        capabilities.supportedTextureSampleSupports()
            .sortedBy { "${it.format}:${it.sampleCountI32}:${it.usages().sortedBy { usage -> usage.name }}" }
            .forEachIndexed { index, support ->
                textureFormat("capabilities.texture-sample-supports[$index].format", support.format)
                i32("capabilities.texture-sample-supports[$index].sample-count", support.sampleCountI32)
                enumSet("capabilities.texture-sample-supports[$index].usages", support.usages().map { it.name })
            }
        capabilities.supportedTextureResolveSupports()
            .sortedBy { "${it.format}:${it.sourceSampleCountI32}:${it.destinationSampleCountI32}" }
            .forEachIndexed { index, support ->
                textureFormat("capabilities.texture-resolve-supports[$index].format", support.format)
                i32("capabilities.texture-resolve-supports[$index].source-sample-count", support.sourceSampleCountI32)
                i32("capabilities.texture-resolve-supports[$index].destination-sample-count", support.destinationSampleCountI32)
            }
    }

    fun materialTable(table: MaterialPlanTable) {
        i32("material-table.entry-count", table.sizeI32)
        table.entries().forEachIndexed { index, entry ->
            val prefix = "material-table.entries[$index]"
            i32("$prefix.program.version", entry.program.versionI32)
            text("$prefix.program.id", entry.program.structuralId.value)
            when (val binding = entry.bindings) {
                is ComposedMaterialBindingV5 -> {
                    text("$prefix.binding",binding.definition.capturedIdentity)
                    text("$prefix.source-proof",binding.sourceProof.canonicalIdentity)
                    text("$prefix.composed-layout",binding.definition.layout.composedBindingLayoutHash)
                }
                is GradientInterpolationBindingV4 -> {
                    text("$prefix.binding",binding.canonicalIdentity)
                    text("$prefix.source-proof",binding.sourceProof.canonicalIdentity)
                    text("$prefix.coordinates",binding.sourceProof.coordinates.identityV4())
                    text("$prefix.stop-slab",requireNotNull(table.gradientStopSlab).canonicalIdentity)
                }
                is ColorFilterBindingV4 -> {
                    text("$prefix.binding",binding.canonicalIdentity)
                    text("$prefix.source-proof",binding.numericAuthority.outputSourceProof.canonicalIdentity)
                    text("$prefix.coordinates",binding.sourceProof.coordinates.identityV4())
                }
                is ImageSampleV3 -> error(W5eImagePlanDiagnostics.InvalidContract)
                is MaterialBindingPlan.GradientV2 -> {
                    text("$prefix.binding", "gradient-v2")
                    text("$prefix.numeric-authority", binding.numericAuthority.canonicalIdentity)
                    binding.copyUniformValuesF32().forEachIndexed { valueIndexI32, valueF32 ->
                        f32("$prefix.uniform[$valueIndexI32]", valueF32)
                    }
                    i64("$prefix.stop-base", binding.stopRange.baseIndexU32.toLong())
                    i64("$prefix.stop-count", binding.stopRange.countU32.toLong())
                    text("$prefix.stop-slab", requireNotNull(table.gradientStopSlab).canonicalIdentity)
                    text("$prefix.degenerate-average", binding.degenerateAverageSrgbaF32.toString())
                }
                is MaterialBindingPlan.GradientV1 -> {
                    text("$prefix.binding", binding.toString())
                    text("$prefix.stop-slab", requireNotNull(table.gradientStopSlab).canonicalIdentity)
                }
                MaterialBindingPlan.EmptyV1 -> text("$prefix.binding", "empty-v1")
                is MaterialBindingPlan.SolidRgbaF32V1 -> {
                    text("$prefix.binding", "solid-rgba-f32-v1")
                    val color = binding.copyRgbaF32()
                    f32("$prefix.red", color.red); f32("$prefix.green", color.green)
                    f32("$prefix.blue", color.blue); f32("$prefix.alpha", color.alpha)
                }
                is MaterialBindingPlan.OpacityF32V1 -> {
                    text("$prefix.binding", "opacity-f32-v1")
                    f32("$prefix.alpha", binding.alphaF32)
                }
            }
        }
    }

    fun resource(prefix: String, resource: PlanResource) {
        text("$prefix.id", resource.id.value)
        text("$prefix.role", resource.role.name)
        i32("$prefix.ordinal", resource.ordinal)
        text("$prefix.kind", resource.kind.name)
        resource.format?.let { textureFormat("$prefix.format", it) } ?: text("$prefix.format", "none")
        resource.copyExtent()?.let {
            i32("$prefix.extent.width", it.width)
            i32("$prefix.extent.height", it.height)
        } ?: text("$prefix.extent", "none")
        i64("$prefix.byte-size", resource.byteSize)
        i32("$prefix.sample-count", resource.sampleCountI32)
        enumSet("$prefix.usages", resource.usages().map { it.name })
        text("$prefix.lifetime", resource.lifetime.name)
        i32("$prefix.first-pass", resource.firstPassIndex)
        i32("$prefix.last-pass-exclusive", resource.lastPassIndexExclusive)
    }

    fun pass(prefix: String, pass: PlanPass, materialV2: Boolean) {
        text("$prefix.id", pass.id.value)
        text("$prefix.role", pass.role.name)
        i32("$prefix.ordinal", pass.ordinal)
        when (pass) {
            is PlanPass.PathMaskClearPass -> {
                text("$prefix.kind", "path-mask-clear")
                text("$prefix.target", pass.target.value)
                text("$prefix.atomic-group", pass.atomicGroup.value)
                text("$prefix.load", pass.load.name)
                text("$prefix.store", pass.store.name)
            }
            is PlanPass.PathRenderPass -> {
                text("$prefix.kind", "path-render")
                text("$prefix.target", pass.target.value)
                text("$prefix.phase", pass.phase.name)
                pathDraw("$prefix.draw", pass.draw, materialV2)
                drawData("$prefix.draw-data", pass.drawDataResources)
                nullableText("$prefix.atomic-group", pass.atomicGroup?.value)
                nullableText("$prefix.depth-stencil", pass.depthStencil?.value)
                text("$prefix.load", pass.load.name)
                text("$prefix.store", pass.store.name)
                nullableText("$prefix.depth-stencil-access", pass.depthStencilAccess?.name)
                nullableText("$prefix.depth-stencil-load-store", pass.depthStencilLoadStore?.name)
                nullableText("$prefix.resolve-target", pass.resolveTarget?.value)
            }
            is PlanPass.ReadbackPass -> {
                text("$prefix.kind", "readback")
                text("$prefix.source", pass.source.value)
                text("$prefix.staging", pass.staging.value)
                i64("$prefix.bytes-per-row", pass.bytesPerRow)
            }
            is PlanPass.ClipMaskInitialize -> {
                text("$prefix.kind", "clip-mask-initialize")
                text("$prefix.output", pass.output.value)
                rect("$prefix.domain", pass.copyDomainI32())
                f32("$prefix.clear-coverage", pass.clearCoverageF32)
                text("$prefix.atomic-group", pass.atomicGroup.value)
            }
            is PlanPass.ClipMaskProducer -> {
                text("$prefix.kind", "clip-mask-producer")
                text("$prefix.target", pass.target.value)
                nullableText("$prefix.resolve-target", pass.resolveTarget?.value)
                nullableText("$prefix.depth-stencil", pass.depthStencil?.value)
                i32("$prefix.sample-count", pass.sampleCountI32)
                clipGeometry("$prefix.geometry", pass.copyGeometryF32())
                text("$prefix.atomic-group", pass.atomicGroup.value)
                bool("$prefix.inverse-coverage", pass.inverseCoverage)
                bool("$prefix.anti-alias", pass.antiAlias)
            }
            is PlanPass.ClipMaskFold -> {
                text("$prefix.kind", "clip-mask-fold")
                text("$prefix.previous", pass.previous.value)
                text("$prefix.source", pass.source.value)
                text("$prefix.output", pass.output.value)
                text("$prefix.operation", pass.operation.name)
                rect("$prefix.domain", pass.copyDomainI32())
                text("$prefix.atomic-group", pass.atomicGroup.value)
            }
            else -> throw IllegalArgumentException("Path compiler witness cannot seal pass ${pass.role}")
        }
    }

    private fun clipGeometry(prefix: String, geometry: org.graphiks.math.geometry.ClipGeometryF32) {
        when (geometry) {
            is org.graphiks.math.geometry.ClipGeometryF32.Rect -> {
                text("$prefix.kind", "rect")
                val value = geometry.copyRectF32()
                f32("$prefix.left", value.left); f32("$prefix.top", value.top)
                f32("$prefix.right", value.right); f32("$prefix.bottom", value.bottom)
            }
            is org.graphiks.math.geometry.ClipGeometryF32.RRect -> {
                text("$prefix.kind", "rrect")
                val value = geometry.copyRRectF32()
                val bounds = value.rect
                f32("$prefix.left", bounds.left); f32("$prefix.top", bounds.top)
                f32("$prefix.right", bounds.right); f32("$prefix.bottom", bounds.bottom)
                f32("$prefix.top-left.x", value.topLeft.x); f32("$prefix.top-left.y", value.topLeft.y)
                f32("$prefix.top-right.x", value.topRight.x); f32("$prefix.top-right.y", value.topRight.y)
                f32("$prefix.bottom-right.x", value.bottomRight.x); f32("$prefix.bottom-right.y", value.bottomRight.y)
                f32("$prefix.bottom-left.x", value.bottomLeft.x); f32("$prefix.bottom-left.y", value.bottomLeft.y)
            }
            is org.graphiks.math.geometry.ClipGeometryF32.Path -> {
                text("$prefix.kind", "path")
                fillGeometry(prefix, geometry.copyPathGeometryF32())
            }
            org.graphiks.math.geometry.ClipGeometryF32.Empty -> text("$prefix.kind", "empty")
        }
    }

    private fun drawData(prefix: String, resources: PlanDrawDataResources) {
        text("$prefix.vertex", resources.vertex.value)
        text("$prefix.index", resources.index.value)
        text("$prefix.uniform", resources.uniform.value)
    }

    private fun pathDraw(prefix: String, draw: PathRenderDraw, materialV2: Boolean) {
        when (draw) {
            is GeneralPathDraw -> {
                text("$prefix.kind", "general")
                generalPathDraw(prefix, draw, materialV2)
            }
            is ClippedGeneralPathDraw -> {
                text("$prefix.kind", "clipped-general")
                generalPathDraw("$prefix.source", draw.source, materialV2)
                clipStrategy("$prefix.clip", draw.clip)
            }
            is BinaryMaskedPathDraw -> {
                text("$prefix.kind", "binary-masked")
                generalPathDraw("$prefix.producer", draw.producer, materialV2)
                text("$prefix.mask", draw.mask.value)
                text("$prefix.mask-fetch", draw.maskFetch.name)
                i32("$prefix.broadcast-sample-count", draw.broadcastSampleCountI32)
            }
            is ClippedBinaryMaskedPathDraw -> {
                text("$prefix.kind", "clipped-binary-masked")
                pathDraw("$prefix.source", draw.source, materialV2)
                clipStrategy("$prefix.clip", draw.clip)
            }
        }
    }

    private fun clipStrategy(prefix: String, clip: ClipPlanStrategy) {
        when (clip) {
            is ClipPlanStrategy.Mask -> text("$prefix.mask", clip.resource.value)
            is ClipPlanStrategy.InverseMask -> {
                text("$prefix.inverse.mask", clip.resource.value)
                rect("$prefix.inverse.domain", clip.geometryF32.copyDomainI32())
                when (val interior = clip.geometryF32.interiorCoverageF32) {
                    org.graphiks.math.geometry.InverseInteriorCoverageF32.Zero -> text("$prefix.inverse.interior", "zero")
                    is org.graphiks.math.geometry.InverseInteriorCoverageF32.Geometry -> {
                        text("$prefix.inverse.interior", "geometry")
                        fillGeometry("$prefix.inverse.geometry", interior.copyGeometryF32())
                    }
                }
            }
            is ClipPlanStrategy.InverseDomain -> {
                text("$prefix.inverse.domain-only", "1")
                rect("$prefix.inverse.domain", clip.geometryF32.copyDomainI32())
                when (val interior = clip.geometryF32.interiorCoverageF32) {
                    org.graphiks.math.geometry.InverseInteriorCoverageF32.Zero -> text("$prefix.inverse.interior", "zero")
                    is org.graphiks.math.geometry.InverseInteriorCoverageF32.Geometry -> {
                        text("$prefix.inverse.interior", "geometry")
                        fillGeometry("$prefix.inverse.geometry", interior.copyGeometryF32())
                    }
                }
            }
            is ClipPlanStrategy.Stencil -> {
                text("$prefix.stencil", clip.depthStencil.value)
                clip.child?.let { clipStrategy("$prefix.child", it) }
            }
            is ClipPlanStrategy.Scissor -> {
                rect("$prefix.scissor", clip.copyDomainI32())
                clip.child?.let { clipStrategy("$prefix.child", it) }
            }
        }
    }

    private fun generalPathDraw(prefix: String, draw: GeneralPathDraw, materialV2: Boolean) {
        i32("$prefix.command-index", draw.commandIndex)
        if (materialV2) {
            when (val authority = draw.materialAuthority) {
                is PlanDrawMaterialAuthority.MaterialV5 -> {
                    requireNotNull(table).colorSourceProofV5(authority.ref)
                    text("$prefix.material-authority","material-v5")
                    i32("$prefix.material-ref",authority.ref.indexI32)
                    text("$prefix.material-coordinates","none-v4")
                }
                is PlanDrawMaterialAuthority.MaterialV4 -> {
                    require(draw.copyPathGeometry() is PathDrawGeometry.Fill ||
                        draw.copyPathGeometry() is PathDrawGeometry.Stroke && table?.isUnfilteredGradientV4(authority.ref) == true) {
                        W5fPlanDiagnostics.Unpromoted
                    }
                    text("$prefix.material-authority", "material-v4")
                    i32("$prefix.material-ref", authority.ref.indexI32)
                    text("$prefix.material-coordinates", authority.coordinates.identityV4())
                }
                is PlanDrawMaterialAuthority.MaterialV3 -> error(W5eImagePlanDiagnostics.InvalidContract)
                is PlanDrawMaterialAuthority.MaterialV2 -> {
                    text("$prefix.material-authority", "material-v2")
                    i32("$prefix.material-ref", authority.ref.indexI32)
                    text("$prefix.material-coordinates", authority.coordinates.canonicalIdentity)
                }
                is PlanDrawMaterialAuthority.MaterialV1 -> {
                    text("$prefix.material-authority", "material-v1")
                    i32("$prefix.material-ref", authority.ref.indexI32)
                    text("$prefix.material-coordinates", authority.coordinates?.canonicalIdentity ?: "none")
                }
                is PlanDrawMaterialAuthority.LegacyColorV1 ->
                    throw IllegalArgumentException("W5a v2 seal requires a material authority")
            }
        } else {
            val color = (draw.materialAuthority as? PlanDrawMaterialAuthority.LegacyColorV1)
                ?.copyColorF32()
                ?: throw IllegalArgumentException("Historical v1 seal requires a legacy color authority")
                f32("$prefix.color.red", color.red)
                f32("$prefix.color.green", color.green)
                f32("$prefix.color.blue", color.blue)
                f32("$prefix.color.alpha", color.alpha)
        }
        text("$prefix.strategy", draw.strategy.name)
        text("$prefix.coverage", draw.coverage.name)
        text("$prefix.sample", draw.sample.name)
        rect("$prefix.scissor", draw.copyScissorI32())
        geometry("$prefix.geometry", draw.copyPathGeometry())
    }

    private fun geometry(prefix: String, geometry: PathDrawGeometry) {
        when (geometry) {
            is PathDrawGeometry.Fill -> {
                text("$prefix.kind", "fill")
                fillGeometry(prefix, geometry.valueF32)
            }
            is PathDrawGeometry.Stroke -> {
                text("$prefix.kind", "stroke")
                strokeGeometry(prefix, geometry.valueF32)
            }
            is PathDrawGeometry.InverseDomainSource -> {
                text("$prefix.kind", "w4e-inverse-domain-source")
                val path = geometry.copySourcePath()
                text("$prefix.source.fill-rule", path.fillRule.name)
                i32("$prefix.source.segment-count", path.segmentCount)
                path.forEachIndexed { index, segment -> pathSegment("$prefix.source.segments[$index]", segment) }
                val transform = geometry.copySourceTransform()
                f32("$prefix.source.transform.sx", transform.sx)
                f32("$prefix.source.transform.kx", transform.kx)
                f32("$prefix.source.transform.tx", transform.tx)
                f32("$prefix.source.transform.ky", transform.ky)
                f32("$prefix.source.transform.sy", transform.sy)
                f32("$prefix.source.transform.ty", transform.ty)
                f32("$prefix.source.transform.persp0", transform.persp0)
                f32("$prefix.source.transform.persp1", transform.persp1)
                f32("$prefix.source.transform.persp2", transform.persp2)
            }
            PathDrawGeometry.Empty -> text("$prefix.kind", "w4e-inverse-domain-zero")
        }
    }

    private fun pathSegment(prefix: String, segment: PathSegmentF32) {
        fun point(label: String, point: org.graphiks.math.geometry.Point2F32) {
            f32("$label.x", point.x)
            f32("$label.y", point.y)
        }
        when (segment) {
            is PathSegmentF32.MoveTo -> {
                text("$prefix.kind", "move")
                point("$prefix.point", segment.point)
            }
            is PathSegmentF32.LineTo -> {
                text("$prefix.kind", "line")
                point("$prefix.point", segment.point)
            }
            is PathSegmentF32.QuadTo -> {
                text("$prefix.kind", "quad")
                point("$prefix.control", segment.control)
                point("$prefix.point", segment.point)
            }
            is PathSegmentF32.CubicTo -> {
                text("$prefix.kind", "cubic")
                point("$prefix.control1", segment.control1)
                point("$prefix.control2", segment.control2)
                point("$prefix.point", segment.point)
            }
            is PathSegmentF32.ArcTo -> {
                text("$prefix.kind", "arc")
                f32("$prefix.radius.x", segment.radius.x)
                f32("$prefix.radius.y", segment.radius.y)
                f32("$prefix.rotation", segment.xAxisRotation)
                bool("$prefix.large-arc", segment.largeArc)
                bool("$prefix.sweep", segment.sweep)
                point("$prefix.point", segment.point)
            }
            PathSegmentF32.Close -> text("$prefix.kind", "close")
        }
    }

    private fun fillGeometry(prefix: String, geometry: PathFillGeometryF32) {
        text("$prefix.fill-rule", geometry.fillRule.name)
        i32("$prefix.attempted-edges", geometry.attemptedEdgeCountI32)
        i32("$prefix.emitted-edges", geometry.emittedNonZeroClosedEdgeCountI32)
        i64("$prefix.vertex-cost", geometry.vertexCostI64)
        i64("$prefix.index-cost", geometry.indexCostI64)
        i64("$prefix.snapshot-byte-cost", geometry.snapshotByteCostI64)
        rect("$prefix.conservative-scissor", geometry.copyConservativeScissorI32())
        geometry.copyDirectTriangleF32OrNull()?.let {
            text("$prefix.direct", "present")
            floats("$prefix.direct.vertices", it.copyVerticesF32())
            ints("$prefix.direct.indices", it.copyIndicesI32())
        } ?: text("$prefix.direct", "absent")
        geometry.copyStencilEdgeFanF32OrNull()?.let {
            text("$prefix.stencil", "present")
            floats("$prefix.stencil.vertices", it.copyVerticesF32())
            ints("$prefix.stencil.indices", it.copyIndicesI32())
            ints("$prefix.stencil.contour-starts", it.copyContourStartsI32())
        } ?: text("$prefix.stencil", "absent")
    }

    private fun strokeGeometry(prefix: String, geometry: PathStrokeGeometryF32) {
        fillGeometry("$prefix.fill", geometry.copyFillGeometryF32())
        val bounds = geometry.copyConservativeBoundsF32()
        f32("$prefix.bounds.left", bounds.left)
        f32("$prefix.bounds.top", bounds.top)
        f32("$prefix.bounds.right", bounds.right)
        f32("$prefix.bounds.bottom", bounds.bottom)
        i64("$prefix.vertex-cost", geometry.vertexCostI64)
        i64("$prefix.index-cost", geometry.indexCostI64)
        i64("$prefix.snapshot-byte-cost", geometry.snapshotByteCostI64)
        i64("$prefix.work.attempted-units", geometry.workUsageI64.attemptedGeometryUnitCountI64)
        i64("$prefix.work.emitted-vertices", geometry.workUsageI64.emittedVertexCountI64)
        i64("$prefix.work.emitted-indices", geometry.workUsageI64.emittedIndexCountI64)
        i64("$prefix.work.snapshot-bytes", geometry.workUsageI64.snapshotByteCountI64)
    }

    private fun nullableText(label: String, value: String?) = text(label, value ?: "none")
    private fun rect(prefix: String, rect: RectI32) {
        i32("$prefix.left", rect.left)
        i32("$prefix.top", rect.top)
        i32("$prefix.right", rect.right)
        i32("$prefix.bottom", rect.bottom)
    }
    private fun floats(prefix: String, values: FloatArray) {
        i32("$prefix.count", values.size)
        values.forEachIndexed { index, value -> f32("$prefix[$index]", value) }
    }
    private fun ints(prefix: String, values: IntArray) {
        i32("$prefix.count", values.size)
        values.forEachIndexed { index, value -> i32("$prefix[$index]", value) }
    }
    private fun enumSet(prefix: String, values: List<String>) {
        values.sorted().forEachIndexed { index, value -> text("$prefix[$index]", value) }
    }
    private fun textureFormat(prefix: String, format: PlanTextureFormat) {
        when (format) {
            is PlanTextureFormat.ImageV1 -> {
                text("$prefix.kind", "image-v1")
                text("$prefix.value", format.value.name)
            }
            is PlanTextureFormat.Color -> {
                text("$prefix.kind", "color")
                text("$prefix.value", format.value.name)
            }
            is PlanTextureFormat.DepthStencil -> {
                text("$prefix.kind", "depth-stencil")
                text("$prefix.value", format.value.name)
            }
            PlanTextureFormat.CoverageMask -> text("$prefix.kind", "coverage-mask")
        }
    }
    private fun token(value: String) {
        val bytes = value.encodeToByteArray()
        digest.update(byteArrayOf(
            (bytes.size ushr 24).toByte(),
            (bytes.size ushr 16).toByte(),
            (bytes.size ushr 8).toByte(),
            bytes.size.toByte(),
        ))
        digest.update(bytes)
    }
}
