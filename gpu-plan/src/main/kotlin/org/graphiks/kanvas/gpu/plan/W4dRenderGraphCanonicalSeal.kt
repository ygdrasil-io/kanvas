package org.graphiks.kanvas.gpu.plan

import java.security.MessageDigest
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.PathStrokeGeometryF32
import org.graphiks.math.geometry.PathStrokeStyleF64
import org.graphiks.math.geometry.PathStrokeWidthF64
import org.graphiks.math.geometry.RectI32

/** Canonical, length-delimited, raw-bit-stable snapshot used only by the opaque W4d witness. */
@JvmSynthetic
internal fun canonicalW4dGraphDigest(graph: RenderGraph): ByteArray = canonicalW4dGraphDigest(graph.canonicalConstruction())

internal fun canonicalW4dGraphDigest(graph: RenderGraphConstruction): ByteArray {
    val materialV2 = W4dPathStrokePlanCompiler.isW5aMaterialCapabilityId(graph.capabilityId)
    val writer = W4dGraphDigestWriter()
    writer.text("schema", if (materialV2) "w4d-render-graph-witness-w5a-material-v2" else "w4d-render-graph-witness-v1")
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

    val resources = graph.resources()
    writer.i32("resources.count", resources.size)
    resources.forEachIndexed { index, resource -> writer.resource("resources[$index]", resource) }

    val passes = graph.passes()
    writer.i32("passes.count", passes.size)
    passes.forEachIndexed { index, pass -> writer.pass("passes[$index]", pass, materialV2) }

    val dependencies = graph.dependencies()
    writer.i32("dependencies.count", dependencies.size)
    dependencies.forEachIndexed { index, dependency ->
        writer.text("dependencies[$index].before", dependency.before.value)
        writer.text("dependencies[$index].after", dependency.after.value)
    }
    return writer.finish()
}

private class W4dGraphDigestWriter {
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
        i32(
            "capabilities.max-dynamic-uniforms-per-layout",
            capabilities.maxDynamicUniformBuffersPerPipelineLayout,
        )
        enumSet("capabilities.operations", capabilities.supportedOperations().map { it.name })
        i64("capabilities.buffer-policy.vertex-floor", capabilities.bufferAllocationPolicy.vertexFloorBytes)
        i64("capabilities.buffer-policy.index-floor", capabilities.bufferAllocationPolicy.indexFloorBytes)
        i64("capabilities.buffer-policy.uniform-floor", capabilities.bufferAllocationPolicy.uniformFloorBytes)
        text("capabilities.buffer-policy.growth", capabilities.bufferAllocationPolicy.growth.name)
        enumSet(
            "capabilities.depth-stencil-formats",
            capabilities.supportedDepthStencilFormats().map { it.name },
        )
        val textureSampleSupports = capabilities.supportedTextureSampleSupports().sortedBy { support ->
            "${support.format}:${support.sampleCountI32}:${support.usages().sortedBy { it.name }}"
        }
        i32("capabilities.texture-sample-supports.count", textureSampleSupports.size)
        textureSampleSupports.forEachIndexed { index, support ->
            textureFormat("capabilities.texture-sample-supports[$index].format", support.format)
            i32("capabilities.texture-sample-supports[$index].sample-count", support.sampleCountI32)
            enumSet(
                "capabilities.texture-sample-supports[$index].usages",
                support.usages().map { it.name },
            )
        }
        val textureResolveSupports = capabilities.supportedTextureResolveSupports().sortedBy { support ->
            "${support.format}:${support.sourceSampleCountI32}:${support.destinationSampleCountI32}"
        }
        i32("capabilities.texture-resolve-supports.count", textureResolveSupports.size)
        textureResolveSupports.forEachIndexed { index, support ->
            textureFormat("capabilities.texture-resolve-supports[$index].format", support.format)
            i32("capabilities.texture-resolve-supports[$index].source-sample-count", support.sourceSampleCountI32)
            i32(
                "capabilities.texture-resolve-supports[$index].destination-sample-count",
                support.destinationSampleCountI32,
            )
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
                is MaterialBindingPlan.GradientV2 -> error(W5dPlanDiagnostics.CoordinatePlanSchema)
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
        when (val format = resource.format) {
            is PlanTextureFormat.ImageV1 -> error("W4d resources cannot contain a W5e cache request")
            null -> text("$prefix.format.kind", "none")
            is PlanTextureFormat.Color -> {
                text("$prefix.format.kind", "color")
                text("$prefix.format.value", format.value.name)
            }
            is PlanTextureFormat.DepthStencil -> {
                text("$prefix.format.kind", "depth-stencil")
                text("$prefix.format.value", format.value.name)
            }
            PlanTextureFormat.CoverageMask -> text("$prefix.format.kind", "coverage-mask")
        }
        val extent = resource.copyExtent()
        bool("$prefix.extent.present", extent != null)
        if (extent != null) {
            i32("$prefix.extent.width", extent.width)
            i32("$prefix.extent.height", extent.height)
        }
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
            is PlanPass.RenderPass -> {
                text("$prefix.kind", "render")
                text("$prefix.target", pass.target.value)
                text("$prefix.load", pass.load.name)
                text("$prefix.store", pass.store.name)
                drawData("$prefix.draw-data", pass.drawDataResources)
                val draws = pass.draws()
                i32("$prefix.draws.count", draws.size)
                draws.forEachIndexed { index, draw -> pathDraw("$prefix.draws[$index]", draw, materialV2) }
            }
            is PlanPass.StencilProducer -> {
                text("$prefix.kind", "stencil-producer")
                stencilPass(prefix, pass.target, pass.depthStencil, pass.draw, pass.drawDataResources,
                    pass.atomicGroup, pass.load, pass.store, pass.depthStencilAccess,
                    pass.depthStencilLoadStore, materialV2)
            }
            is PlanPass.StencilCover -> {
                text("$prefix.kind", "stencil-cover")
                stencilPass(prefix, pass.target, pass.depthStencil, pass.draw, pass.drawDataResources,
                    pass.atomicGroup, pass.load, pass.store, pass.depthStencilAccess,
                    pass.depthStencilLoadStore, materialV2)
            }
            is PlanPass.ReadbackPass -> {
                text("$prefix.kind", "readback")
                text("$prefix.source", pass.source.value)
                text("$prefix.staging", pass.staging.value)
                i64("$prefix.bytes-per-row", pass.bytesPerRow)
            }
            else -> throw IllegalArgumentException("W4d witness cannot seal pass ${pass.role}")
        }
    }

    private fun stencilPass(
        prefix: String,
        target: PlanResourceId,
        depthStencil: PlanResourceId,
        draw: PathDraw,
        drawDataResources: PlanDrawDataResources,
        atomicGroup: PlanAtomicGroupId,
        load: AttachmentLoadPlan,
        store: AttachmentStorePlan,
        depthStencilAccess: PlanDepthStencilAccess,
        depthStencilLoadStore: PlanDepthStencilLoadStore,
        materialV2: Boolean,
    ) {
        text("$prefix.target", target.value)
        text("$prefix.depth-stencil", depthStencil.value)
        pathDraw("$prefix.draw", draw, materialV2)
        drawData("$prefix.draw-data", drawDataResources)
        text("$prefix.atomic-group", atomicGroup.value)
        text("$prefix.load", load.name)
        text("$prefix.store", store.name)
        text("$prefix.depth-stencil-access", depthStencilAccess.name)
        text("$prefix.depth-stencil-load-store", depthStencilLoadStore.name)
    }

    private fun drawData(prefix: String, resources: PlanDrawDataResources?) {
        bool("$prefix.present", resources != null)
        if (resources != null) {
            text("$prefix.vertex", resources.vertex.value)
            text("$prefix.index", resources.index.value)
            text("$prefix.uniform", resources.uniform.value)
        }
    }

    private fun pathDraw(prefix: String, draw: PlanDraw, materialV2: Boolean) {
        val pathDraw = draw as? PathDraw
            ?: throw IllegalArgumentException("W4d witness accepts only path draws")
        text("$prefix.type", when (pathDraw) {
            is PathFillDraw -> "fill"
            is PathStrokeDraw -> "stroke"
            is W5bW4ePathDraw -> error("W4e geometry requires its own native authority")
            is GeneralPathDraw -> error("W4d narrow witness cannot consume General geometry")
        })
        i32("$prefix.command-index", pathDraw.commandIndex)
        if (materialV2) {
            when (val authority = pathDraw.materialAuthority) {
                is PlanDrawMaterialAuthority.MaterialV5 -> error(W5gPlanDiagnostics.Unpromoted)
                is PlanDrawMaterialAuthority.MaterialV4 -> error(W5fPlanDiagnostics.Unpromoted)
                is PlanDrawMaterialAuthority.MaterialV3 -> error(W5eImagePlanDiagnostics.InvalidContract)
                is PlanDrawMaterialAuthority.MaterialV2 -> error(W5dPlanDiagnostics.CoordinatePlanSchema)
                is PlanDrawMaterialAuthority.MaterialV1 -> {
                    text("$prefix.material-authority", "material-v1")
                    i32("$prefix.material-ref", authority.ref.indexI32)
                    text("$prefix.material-coordinates", authority.coordinates?.canonicalIdentity ?: "none")
                }
                is PlanDrawMaterialAuthority.LegacyColorV1 ->
                    throw IllegalArgumentException("W5a v2 seal requires a material authority")
            }
        } else {
            val color = (pathDraw.materialAuthority as? PlanDrawMaterialAuthority.LegacyColorV1)
                ?.copyColorF32()
                ?: throw IllegalArgumentException("Historical v1 seal requires a legacy color authority")
                f32("$prefix.color.red", color.red)
                f32("$prefix.color.green", color.green)
                f32("$prefix.color.blue", color.blue)
                f32("$prefix.color.alpha", color.alpha)
        }
        text("$prefix.coverage", pathDraw.coverage.name)
        text("$prefix.sample", pathDraw.sample.name)
        text("$prefix.blend", pathDraw.blend.canonicalLabel)
        text("$prefix.strategy", pathDraw.strategy.name)
        rect("$prefix.scissor", pathDraw.copyScissorI32())
        when (pathDraw) {
            is W5bW4ePathDraw -> error("W4e geometry requires its own native authority")
            is GeneralPathDraw -> error("W4d narrow witness cannot consume General geometry")
            is PathFillDraw -> fillGeometry("$prefix.geometry.fill", pathDraw.copyGeometryF32())
            is PathStrokeDraw -> {
                strokeGeometry("$prefix.geometry.stroke", pathDraw.copyGeometryF32())
                text("$prefix.mode", pathDraw.mode.name)
                strokeStyle("$prefix.style", pathDraw.styleF64)
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
        val direct = geometry.copyDirectTriangleF32OrNull()
        bool("$prefix.direct.present", direct != null)
        if (direct != null) {
            floats("$prefix.direct.vertices", direct.copyVerticesF32())
            ints("$prefix.direct.indices", direct.copyIndicesI32())
        }
        val fan = geometry.copyStencilEdgeFanF32OrNull()
        bool("$prefix.stencil.present", fan != null)
        if (fan != null) {
            floats("$prefix.stencil.vertices", fan.copyVerticesF32())
            ints("$prefix.stencil.indices", fan.copyIndicesI32())
            ints("$prefix.stencil.contour-starts", fan.copyContourStartsI32())
        }
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

    private fun strokeStyle(prefix: String, style: PathStrokeStyleF64) {
        when (val width = style.widthF64) {
            PathStrokeWidthF64.Hairline -> text("$prefix.width.kind", "hairline")
            is PathStrokeWidthF64.Finite -> {
                text("$prefix.width.kind", "finite")
                f64("$prefix.width.value", width.valueF64)
            }
        }
        text("$prefix.cap", style.cap.name)
        text("$prefix.join", style.join.name)
        f64("$prefix.miter-limit", style.miterLimitF64)
        val dash = style.dashF64
        bool("$prefix.dash.present", dash != null)
        if (dash != null) {
            doubles("$prefix.dash.intervals", dash.copyIntervalsF64())
            f64("$prefix.dash.phase", dash.phaseF64)
        }
    }

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

    private fun doubles(prefix: String, values: DoubleArray) {
        i32("$prefix.count", values.size)
        values.forEachIndexed { index, value -> f64("$prefix[$index]", value) }
    }

    private fun ints(prefix: String, values: IntArray) {
        i32("$prefix.count", values.size)
        values.forEachIndexed { index, value -> i32("$prefix[$index]", value) }
    }

    private fun enumSet(prefix: String, values: List<String>) {
        val sorted = values.sorted()
        i32("$prefix.count", sorted.size)
        sorted.forEachIndexed { index, value -> text("$prefix[$index]", value) }
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
        digest.update(
            byteArrayOf(
                (bytes.size ushr 24).toByte(),
                (bytes.size ushr 16).toByte(),
                (bytes.size ushr 8).toByte(),
                bytes.size.toByte(),
            ),
        )
        digest.update(bytes)
    }
}
