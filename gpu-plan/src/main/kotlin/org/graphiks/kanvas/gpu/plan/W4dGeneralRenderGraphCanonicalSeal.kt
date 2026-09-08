package org.graphiks.kanvas.gpu.plan

import java.security.MessageDigest
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.PathStrokeGeometryF32
import org.graphiks.math.geometry.RectI32

/** Canonical, raw-bit-stable snapshot used only by the opaque W4d.2 compiler witness. */
@JvmSynthetic
internal fun canonicalW4dGeneralGraphDigest(graph: RenderGraph): ByteArray {
    return canonicalGraphDigest(graph, "w4d-general-render-graph-witness-v1")
}

/** Canonical W4e inventory seal.  It shares W4d.2's path/pass serialization seam. */
@JvmSynthetic
internal fun canonicalW4eGraphDigest(graph: RenderGraph): ByteArray {
    return canonicalGraphDigest(graph, "w4e-complex-clip-render-graph-witness-v1")
}

private fun canonicalGraphDigest(graph: RenderGraph, schema: String): ByteArray {
    val writer = W4dGeneralGraphDigestWriter()
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

    graph.resources().forEachIndexed { index, resource -> writer.resource("resources[$index]", resource) }
    graph.passes().forEachIndexed { index, pass -> writer.pass("passes[$index]", pass) }
    graph.dependencies().forEachIndexed { index, dependency ->
        writer.text("dependencies[$index].before", dependency.before.value)
        writer.text("dependencies[$index].after", dependency.after.value)
    }
    return writer.finish()
}

private class W4dGeneralGraphDigestWriter {
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

    fun pass(prefix: String, pass: PlanPass) {
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
                pathDraw("$prefix.draw", pass.draw)
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

    private fun pathDraw(prefix: String, draw: PathRenderDraw) {
        when (draw) {
            is GeneralPathDraw -> {
                text("$prefix.kind", "general")
                generalPathDraw(prefix, draw)
            }
            is ClippedGeneralPathDraw -> {
                text("$prefix.kind", "clipped-general")
                generalPathDraw("$prefix.source", draw.source)
                clipStrategy("$prefix.clip", draw.clip)
            }
            is BinaryMaskedPathDraw -> {
                text("$prefix.kind", "binary-masked")
                generalPathDraw("$prefix.producer", draw.producer)
                text("$prefix.mask", draw.mask.value)
                text("$prefix.mask-fetch", draw.maskFetch.name)
                i32("$prefix.broadcast-sample-count", draw.broadcastSampleCountI32)
            }
            is ClippedBinaryMaskedPathDraw -> {
                text("$prefix.kind", "clipped-binary-masked")
                pathDraw("$prefix.source", draw.source)
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

    private fun generalPathDraw(prefix: String, draw: GeneralPathDraw) {
        i32("$prefix.command-index", draw.commandIndex)
        f32("$prefix.color.red", draw.color.red)
        f32("$prefix.color.green", draw.color.green)
        f32("$prefix.color.blue", draw.color.blue)
        f32("$prefix.color.alpha", draw.color.alpha)
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
