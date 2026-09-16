package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.GeometryNode

import org.graphiks.kanvas.color.ColorInterpolationProgramV1
import org.graphiks.kanvas.render.ir.ColorInterpolation
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.GradientStop
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.PaintNode
import org.graphiks.kanvas.render.ir.PaintStyleNode
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32

internal sealed interface SourceConstructionResultV4<out T> {
    data class Built<T>(val value: T) : SourceConstructionResultV4<T>
    data class Refused(val failure: RenderPlanResult<Nothing>,val diagnosticCode: String) : SourceConstructionResultV4<Nothing>
}

internal fun sourceConstructionRefusalV4(code: String): SourceConstructionResultV4.Refused {
    val resource = code.startsWith("resource.") || code.startsWith("resource-limit.")
    val invalid = code.startsWith("invalid.")
    val diagnostics = listOf(RenderDiagnostic(RenderDiagnosticCode(code),
        if (resource) RenderDiagnosticDomain.RESOURCE else if (invalid) RenderDiagnosticDomain.SCENE
        else RenderDiagnosticDomain.CAPABILITY, RenderDiagnosticSeverity.ERROR, code))
    return SourceConstructionResultV4.Refused(when {
        resource -> RenderPlanResult.ResourceLimitExceeded(diagnostics)
        invalid -> RenderPlanResult.InvalidScene(diagnostics)
        else -> RenderPlanResult.GapOnPromotedScope(diagnostics)
    },code)
}

internal sealed interface SourceUnaryMetadataV4 {
    data class Opacity(val alphaF32: Float) : SourceUnaryMetadataV4
    data class Filter(val execution: ColorFilterExecutionPlanV1) : SourceUnaryMetadataV4
}

/** Metadata-only scan of the immutable recorded stops, never a converted stop snapshot. */
internal class GradientStopCursorV4 private constructor(
    private val input: List<GradientStop>,
    private val effectiveTileMode: GradientTileModeV2,
    val solidColor: ColorARGB?,
) {
    data class Stop(val positionF32: Float, val color: ColorARGB)

    fun values(): Sequence<Stop> = sequence {
        if (solidColor != null) return@sequence
        if (input.size == 1) {
            yield(Stop(0f, input.single().color)); yield(Stop(1f, input.single().color))
            return@sequence
        }
        var previous = 0f
        var position = maxOf(0f, input.first().position.coerceIn(0f, 1f))
        var first = input.first().color
        var last = first
        var runCount = 0
        var firstRun = true
        suspend fun SequenceScope<Stop>.emitRun() {
            if (firstRun && position > 0f) yield(Stop(0f, first))
            val exteriorStart = effectiveTileMode != GradientTileModeV2.CLAMP && position == 0f
            val exteriorEnd = effectiveTileMode != GradientTileModeV2.CLAMP && position == 1f
            if (runCount == 1 || !exteriorStart) yield(Stop(position, first))
            if (runCount > 1 && !exteriorEnd) yield(Stop(position, last))
            firstRun = false
        }
        for (stop in input) {
            val next = maxOf(previous, stop.position.coerceIn(0f, 1f)).let { if (it == 0f) 0f else it }
            if (runCount != 0 && next != position) {
                emitRun(); runCount = 0; first = stop.color
            }
            position = next; last = stop.color; runCount++; previous = next
        }
        emitRun()
        if (position < 1f) yield(Stop(1f, last))
    }

    val countI32: Int = values().count()
    val byteSizeI64: Long = Math.multiplyExact(countI32.toLong(), 32L)

    fun sameSequence(other: GradientStopCursorV4): Boolean {
        if (countI32 != other.countI32 || solidColor != other.solidColor) return false
        val left = values().iterator(); val right = other.values().iterator()
        while (left.hasNext()) if (left.next() != right.next()) return false
        return !right.hasNext()
    }

    /** Hash accelerator only; range allocation must also call sameSequence on a collision. */
    val sequenceIdentity: String by lazy {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        fun word(bits: Int) { repeat(4) { byte -> digest.update((bits ushr (byte * 8)).toByte()) } }
        word(countI32)
        for (stop in values()) { word(stop.positionF32.toRawBits()); word(stop.color.value.toInt()) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun of(input: List<GradientStop>, effectiveTileMode: GradientTileModeV2,
            preserveValidityMask: Boolean): GradientStopCursorV4 {
            require(input.isNotEmpty()) { W5cPlanDiagnostics.EmptyStops }
            require(input.all { it.position.isFinite() }) { W5cPlanDiagnostics.NonFinite }
            require(input.size.toLong() + 2L <= 65_538L) { W5cPlanDiagnostics.StopBudget }
            val cursor = GradientStopCursorV4(input, effectiveTileMode,
                input.singleOrNull()?.color?.takeUnless { preserveValidityMask })
            var previous: Float? = null
            for (stop in cursor.values()) {
                previous?.let { first -> require(stop.positionF32 == first ||
                    stop.positionF32 - first >= java.lang.Float.MIN_NORMAL) { W5cPlanDiagnostics.NumericDomainUnbounded } }
                previous = stop.positionF32
            }
            require(cursor.countI32 == 0 || cursor.countI32 in 2..65_538) { W5cPlanDiagnostics.StopBudget }
            return cursor
        }
    }
}

/** Original source metadata; pending sources have no table, tuple, proof or execution witness. */
internal class MaterialSourceConstructionV4 private constructor(
    val original: MaterialNode,
    val paint: PaintNode?,
    val coordinates: SourceCoordinatesV4,
    bounds: RectF32,
    val blend: BlendPlan,
    val canonicalIdentity: String,
    val resolvedSource: EffectiveMaterialPlanner.Result.Ready?,
    val gradient: GradientMetadata?,
    val image: ImageMetadata? = null,
    val composed: ComposedMetadata? = null,
    private val runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot? = null,
) {
    private val bounds = bounds.copy()
    val deviceBoundsF32: RectF32 get() = bounds.copy()
    val pending: Boolean get() = resolvedSource == null
    val hasGradientStorage: Boolean get() = composed?.nodes?.any { it.gradientSource?.hasGradientStorage == true }
        ?: image?.child?.hasGradientStorage ?: gradient?.stops?.countI32?.let { it > 0 }
        ?: (resolvedSource?.table?.gradientStopSlab != null)
    val wrappers: List<SourceUnaryMetadataV4> get() = if (composed != null) emptyList() else image?.wrappers ?: requireNotNull(gradient).wrappers

    fun uniformBytesI64(sourceOnly: Boolean = false): Long {
        composed?.let { return it.layout.uniformBytesI64 }
        resolvedSource?.let { resolved ->
            if (resolved.materialAuthority is PlanDrawMaterialAuthority.MaterialV4) {
                val footprint = RawMaterialRequirementsV2.measureV4(resolved.table,resolved.root)
                return if (sourceOnly) footprint.sourceUniformByteCountI64 else footprint.uniformByteCountI64
            }
            return RawMaterialRequirementsV2.measureLegacy(resolved.table,resolved.root).uniformByteCountI64
        }
        val base = image?.let { Math.addExact(it.layout.imageUniformByteCountI64,it.child?.uniformBytesI64(sourceOnly) ?: 0L) }
            ?: GradientInterpolationUniformLayoutV4.leafByteCountI64(this)
        return wrappers.fold(base) { bytes,wrapper -> Math.addExact(bytes,when (wrapper) {
            is SourceUnaryMetadataV4.Opacity -> 16L
            is SourceUnaryMetadataV4.Filter -> if (sourceOnly) 0L else wrapper.execution.dynamicByteCountI64
        }) }
    }

    /** Original V3 classification/capture, retained until the final frame binds its child. */
    class ImageMetadata internal constructor(
        val originalDraw: DrawNode,
        val upload: ImageUploadPlanV1,
        val coordinates: ImageCoordinatePlanV1,
        val color: ImageColorAlphaPlanV1,
        val sampling: ImageSamplingPlanV1,
        val tileModes: ImageTileModePlanV1,
        cells: List<ImageCellPlanV1>?,
        val latticeKinds: String?,
        val paintAlphaF32: Float,
        val latticePaintAlphaF32: Float,
        val atlasColor: ColorARGB?,
        val atlasMode: org.graphiks.kanvas.render.ir.BlendMode?,
        val child: MaterialSourceConstructionV4?,
        val deferredColor: Boolean,
        wrappers: List<SourceUnaryMetadataV4>,
        bounds: RectF32,
    ) {
        val cells = cells?.let(::immutableList)
        val wrappers = immutableList(wrappers)
        private val bounds = bounds.copy()
        val layout: ImageSourceLayoutV3 get() = ImageSourceLayoutV3(child?.hasGradientStorage == true,
            cells != null,latticeKinds != null,maxOf(1,cells?.size ?: 9),atlasColor != null)

        fun bind(childSource: EffectiveMaterialPlanner.Result.Ready?,frameBytesI64: Long,
            upload: ImageUploadPlanV1 = this.upload): EffectiveMaterialPlanner.Result.Ready {
            require(this.upload.sharesOwnerAndPhysicalFacts(upload)) { W5eImagePlanDiagnostics.InvalidContract }
            require((child == null) == (childSource == null)) { W5eImagePlanDiagnostics.InvalidContract }
            val program = if (childSource == null) ImageMaterialProgramV3.ColorV3(color.channelOrder,
                color.alphaType,color.transfer,color.gamut,sampling,tileModes,cells != null,latticeKinds,
                atlasMode,color.premultiplication)
            else ImageMaterialProgramV3.MaskV3(childSource.table.entry(childSource.root).program,color.alphaType,
                sampling,tileModes,cells != null,latticeKinds,atlasMode)
            val numeric = ImageNumericAuthorityV1.seal(program,upload,coordinates,bounds,paintAlphaF32,
                sampling,tileModes,cells,latticePaintAlphaF32)
                ?: throw IllegalArgumentException(W5eImagePlanDiagnostics.NumericDomainUnbounded)
            val childIdentity = childSource?.table?.sourceIdentity(childSource.root)
            val atlasBlend = atlasColor?.let { entryColor ->
                val authority = if (deferredColor) {
                    val childProof = childSource?.let { source ->
                        val childCoordinates = when (val authority = source.materialAuthority) {
                            is PlanDrawMaterialAuthority.MaterialV4 -> authority.coordinates
                            is PlanDrawMaterialAuthority.MaterialV2 -> SourceCoordinatesV4.V2(authority.coordinates)
                            is PlanDrawMaterialAuthority.MaterialV1 -> authority.coordinates?.let(SourceCoordinatesV4::V1) ?: SourceCoordinatesV4.None
                            else -> throw IllegalArgumentException(W5eImagePlanDiagnostics.InvalidContract)
                        }
                        (ColorSourceProofCompilerV1.seal(source.table,source.root,childCoordinates,bounds)
                            as? ColorSourceProofResultV1.Ready)?.source
                            ?: throw IllegalArgumentException(W5eImagePlanDiagnostics.NumericDomainUnbounded)
                    }
                    ImageAtlasBlendNumericAuthorityV1.sealForSource(requireNotNull(atlasMode),entryColor,
                        upload,coordinates,numeric,paintAlphaF32,childProof)
                } else ImageAtlasBlendNumericAuthorityV1.seal(requireNotNull(atlasMode),entryColor,
                    upload,color,childSource != null,childIdentity,
                    if (childSource != null && originalDraw.paint?.shader == null) originalDraw.paint?.color?.withAlpha(255) else null)
                authority ?: throw IllegalArgumentException(W5eImagePlanDiagnostics.NumericDomainUnbounded) }
            val execution = ImageSampleExecutionPlanV1(upload,coordinates,color,numeric,paintAlphaF32,childIdentity,
                frameBytesI64,sampling,tileModes,atlasBlend)
            val entries = childSource?.table?.entries().orEmpty() + MaterialPlanEntry(program,ImageSampleV3.of(execution))
            return EffectiveMaterialPlanner.Result.Ready(MaterialPlanTable.of(entries),MaterialPlanRef(entries.lastIndex))
        }
    }

    class GradientMetadata internal constructor(
        val leaf: MaterialNode,
        val interpolation: ColorInterpolation,
        val family: GradientFamilyV2,
        val tile: GradientTileOperationGraphV2,
        val degeneracy: GradientDegeneracyV1,
        val stops: GradientStopCursorV4,
        wrappers: List<SourceUnaryMetadataV4>,
    ) {
        val wrappers: List<SourceUnaryMetadataV4> = immutableList(wrappers)
        val recipeIdentity: String? = when (interpolation) {
            ColorInterpolation.SRGB -> null
            ColorInterpolation.LINEAR -> "linear-srgb-eotf-ordered-f32-v1"
            ColorInterpolation.OKLAB -> ColorInterpolationProgramV1.OKLAB_RECIPE_VERSION
            ColorInterpolation.HSL -> ColorInterpolationProgramV1.recipe(ColorInterpolationProgramV1.RecipeKind.SRGB_TO_HSL_STOP).identity
            ColorInterpolation.OKLCH -> ColorInterpolationProgramV1.recipe(ColorInterpolationProgramV1.RecipeKind.SRGB_TO_OKLCH_STOP).identity
        }
        val rangeIdentity: String = "stop-domain-v4:$interpolation:$recipeIdentity:${stops.sequenceIdentity}"
    }

    internal class ImageChildMetadata(val original: MaterialNode.ImageSample,
        val description: EffectiveMaterialPlanner.ImageSampleDescription,val coordinates: MaterialCoordinatePlanV2,
        val origin: DrawNode? = null,
        val projection: ImageCoordinatePlanV1 = ImageCoordinatePlanV1.sealShader(org.graphiks.math.matrix.Matrix3x3F32(),emptyList())) {
        val headerBytesI64: Long = ImageSourceLayoutV3(false,false,false,9,false).imageUniformByteCountI64
        val uniformBytesI64: Long = Math.addExact(headerBytesI64,coordinates.uniformByteSizeI64)
    }

    internal class NoiseChildMetadata(val parameters: NoiseParametersV1, val coordinates: MaterialCoordinatePlanV2) {
        val uniformBytesI64: Long = Math.addExact(NoiseOperationGraphV1.HEADER_BYTES_I64, coordinates.uniformByteSizeI64)
    }

    /** Semantic admission only: no DAG owner/index, scalar field, source table or resource lease. */
    internal class PreparedAuthentication internal constructor(
        internal val draw: DrawNode,
        internal val material: MaterialNode,
        internal val imageOrigin: ImageChildMetadata?,
        internal val tailAlphaF32: Float,
        internal val runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot,
        internal val runtimeEntries: java.util.IdentityHashMap<MaterialNode.RuntimeEffect, RuntimeEffectSemanticEntryV1>,
    )

    internal class ComposedMetadata(nodes: List<Node>, val layout: ComposedBindingLayoutV1,
        val primitiveEvaluationRef: MaterialEvaluationRefV5? = null,
        val primitiveBlendMode: org.graphiks.kanvas.render.ir.BlendMode? = null,
        val primitiveGeometry: GeometryNode.IndexedMesh? = null) {
        val nodes: List<Node> = immutableList(nodes)
        val runtimeResources: List<CapturedRuntimeResourceV1> = immutableList(this.nodes.filter { it.runtime != null }
            .distinctBy { it.ownerNodeIndexI32 }.sortedBy { it.ownerNodeIndexI32 }.flatMap { node ->
                val original = node.original as MaterialNode.RuntimeEffect
                original.descriptor.logicalResources.map { slot ->
                    CapturedRuntimeResourceV1(node.ownerNodeIndexI32,original.descriptor,slot,
                        original.resources.single { it.logicalSlotI32 == slot.logicalSlotI32 }.binding,
                        layout.resources.single { it.ownerNodeIndexI32 == node.ownerNodeIndexI32 && it.logicalSlotI32 == slot.logicalSlotI32 })
                }
            })
        class Node(val ownerNodeIndexI32: Int, val original: MaterialNode, children: List<MaterialEvaluationRefV5>,
            val offsetBytesI32: Int, val filter: ColorFilterExecutionPlanV1?,
            val gradientSource: MaterialSourceConstructionV4? = null,
            val imageSource: ImageChildMetadata? = null,
            val noiseSource: NoiseChildMetadata? = null,
            val runtime: RuntimeEffectSemanticEntryV1? = null) {
            val children: List<MaterialEvaluationRefV5> = immutableList(children)
            val topologyIdentity: String = when (original) {
                MaterialNode.Transparent -> "transparent"
                is MaterialNode.Solid -> "solid"
                is MaterialNode.Opacity -> "opacity"
                is MaterialNode.RuntimeEffect -> "runtime-effect-v1"
                is MaterialNode.Blend -> "blend:${original.mode}"
                is MaterialNode.WithColorFilter -> "filter:${requireNotNull(filter).structuralIdentity}"
                is MaterialNode.WithWorkingColorSpace -> "working:${original.interpolation}"
                is MaterialNode.WithLocalMatrix -> "local-matrix"
                is MaterialNode.CoordClamp -> "coord-clamp"
                is MaterialNode.ImageSample -> requireNotNull(imageSource).let {
                    "image:${it.description.color}:${it.description.sampling.topologyId}:${it.description.tileModes.topologyId}:${it.coordinates.topologyIdentity}:origin=${it.origin != null}"
                }
                is MaterialNode.PerlinNoise, is MaterialNode.FractalNoise ->
                    "noise-v1:${requireNotNull(noiseSource).coordinates.topologyIdentity}"
                is MaterialNode.LinearGradient,is MaterialNode.RadialGradient,is MaterialNode.SweepGradient,
                is MaterialNode.ConicalGradient -> requireNotNull(gradientSource).let {
                    "gradient:${it.gradient!!.family}:${it.gradient.interpolation}:${it.gradient.tile.contractId}:" +
                        "${it.gradient.tile.requestedMode}:${it.gradient.tile.effectiveMode}:${it.coordinates.let { c ->
                            (c as? SourceCoordinatesV4.V2)?.plan?.topologyIdentity ?: "none" }}"
                }
                else -> error(W5gPlanDiagnostics.Unpromoted)
            }
        }
    }

    companion object {
        fun containsComposed(root: MaterialNode): Boolean = when (root) {
            is MaterialNode.Blend, is MaterialNode.PerlinNoise, is MaterialNode.FractalNoise, is MaterialNode.RuntimeEffect -> true
            is MaterialNode.Opacity -> containsComposed(root.material)
            is MaterialNode.WithColorFilter -> containsComposed(root.material)
            is MaterialNode.WithWorkingColorSpace -> containsComposed(root.material)
            is MaterialNode.WithLocalMatrix -> containsComposed(root.material)
            is MaterialNode.CoordClamp -> containsComposed(root.material)
            else -> false
        }
        private fun describeImageOrigin(draw: DrawNode): ImageChildMetadata {
            val patch = draw.geometry as? GeometryNode.ImagePatch
            require(draw.origin == DrawOrigin.IMAGE && patch != null && colorFilterEffectsMatchPaint(draw)) { W5eImagePlanDiagnostics.InvalidContract }
            val sample = draw.material as? MaterialNode.ImageSample ?: error(W5eImagePlanDiagnostics.InvalidContract)
            val description = EffectiveMaterialPlanner.describeImageSample(sample,true) { pixels ->
                require(draw.resource === pixels && patch.image.id.value == pixels.sourceId) { W5eImagePlanDiagnostics.InvalidContract }
            }
            description.validateUploadMetadata()
            val coordinates = when (val result = MaterialCoordinatePlanV2.fromCtmAndNodes(org.graphiks.math.matrix.Matrix3x3F32(),emptyList())) {
                is MaterialCoordinatePlanV2.Build.Ready -> result.coordinates
                is MaterialCoordinatePlanV2.Build.Refused -> throw IllegalArgumentException(result.code)
            }
            return ImageChildMetadata(sample,description,coordinates,draw,
                ImageCoordinatePlanV1.seal(draw.transform,patch.copySource(),patch.copyDestination()))
        }

        fun captureImageOrigin(draw: DrawNode,bounds: RectF32,blend: BlendPlan,
            runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot): MaterialSourceConstructionV4 {
            return captureAuthenticated(authenticatePrepared(draw, runtimeCatalog), bounds, blend)
        }

        fun authenticatePrepared(draw: DrawNode,
            runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot): PreparedAuthentication {
            if (draw.origin != DrawOrigin.IMAGE) {
                validateComposedDraw(draw, draw.material, null)
                val entries = authenticateComposed(draw.material, runtimeCatalog)
                authenticateComposedMetadata(draw, draw.material, null)
                return PreparedAuthentication(draw, draw.material, null,
                    draw.paint?.takeIf { it.shader != null }?.color?.alphaNormalized ?: 1f, runtimeCatalog, entries)
            }
            val origin = describeImageOrigin(draw)
            val sample = origin.original
            val description = origin.description
            val mask = description.color.channelOrder == ImageChannelOrderV1.ALPHA
            val material = if (mask) MaterialNode.Blend(org.graphiks.kanvas.render.ir.BlendMode.MODULATE,
                EffectiveMaterialPlanner.imageMaskMaterial(draw),sample) else sample
            val alpha = if (!mask || draw.paint?.shader != null) draw.paint?.color?.alphaNormalized ?: 1f else 1f
            validateComposedDraw(draw, material, origin)
            val authenticated = if (mask) (material as MaterialNode.Blend).dst else material
            val entries = authenticateComposed(authenticated, runtimeCatalog)
            authenticateComposedMetadata(draw, authenticated, origin)
            return PreparedAuthentication(draw, material, origin, alpha, runtimeCatalog, entries)
        }

        fun captureAuthenticated(authentication: PreparedAuthentication, bounds: RectF32,
            blend: BlendPlan): MaterialSourceConstructionV4 = captureComposed(
            authentication.draw, bounds, blend, authentication.runtimeCatalog, authentication.material,
            authentication.imageOrigin, authentication.tailAlphaF32, authentication,
        )
        /** Shared pre-layout admission; elided origins authenticate without constructing a source. */
        private fun authenticateComposed(material: MaterialNode,
            runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot): java.util.IdentityHashMap<MaterialNode.RuntimeEffect,RuntimeEffectSemanticEntryV1> {
            val active = java.util.IdentityHashMap<MaterialNode,Unit>()
            var occurrences = 0
            var maximumDepthI32 = 0
            var runtimeUniformBytesI64 = 0L
            val runtimeNodes = mutableListOf<MaterialNode.RuntimeEffect>()
            val runtimeEntries = java.util.IdentityHashMap<MaterialNode.RuntimeEffect,RuntimeEffectSemanticEntryV1>()
            val limits = org.graphiks.kanvas.render.ir.GraphLimits()
            // Validate original occurrences separately from immutable metadata reuse
            // and the synthesized paint nodes. This pass must not resolve semantics:
            // an unknown runtime parent cannot hide an oversized descendant graph.
            fun validateStructure(node: MaterialNode,depth: Int) {
                if (node is MaterialNode.RuntimeEffect) runtimeNodes += node
                require(depth <= limits.maxDepth && ++occurrences <= limits.maxNodes) {
                    if (runtimeNodes.isEmpty()) W5gPlanDiagnostics.Schema else W5hPlanDiagnostics.Budget
                }
                require(active.put(node,Unit) == null) { W5gPlanDiagnostics.Schema }
                maximumDepthI32=maxOf(maximumDepthI32,depth)
                when (node) {
                    is MaterialNode.Blend -> { validateStructure(node.dst,depth+1); validateStructure(node.src,depth+1) }
                    is MaterialNode.Opacity -> validateStructure(node.material,depth+1)
                    is MaterialNode.WithColorFilter -> validateStructure(node.material,depth+1)
                    is MaterialNode.WithWorkingColorSpace -> validateStructure(node.material,depth+1)
                    is MaterialNode.WithLocalMatrix -> validateStructure(node.material,depth+1)
                    is MaterialNode.CoordClamp -> validateStructure(node.material,depth+1)
                    is MaterialNode.RuntimeEffect -> {
                        // Count the declared block extent per occurrence, including padding,
                        // without ABI validation or copying any captured uniform value.
                        runtimeUniformBytesI64 = Math.addExact(runtimeUniformBytesI64,
                            node.descriptor.uniformBlock.sizeBytesI32.toLong())
                        require(runtimeUniformBytesI64 <= 64L * 1024L * 1024L) { W5hPlanDiagnostics.Budget }
                        node.forEach { validateStructure(it.material,depth+1) }
                    }
                    else -> Unit
                }
                active.remove(node)
            }
            validateStructure(material,1)
            // Only a structurally admitted graph may perform exact catalogue/ABI lookup.
            // Repeated captured owners reuse their single semantic result in the later DAG.
            runtimeNodes.forEach { node ->
                runtimeEntries.getOrPut(node) { RuntimeEffectExpectationV1.validate(node,runtimeCatalog) }
            }
            require(runtimeEntries.values.all { occurrences <= it.graphLimits.maxNodes && maximumDepthI32 <= it.graphLimits.maxDepth }) {
                W5hPlanDiagnostics.Budget
            }
            return runtimeEntries
        }

        fun authenticateElidedImageOrigin(draw: DrawNode,runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot) {
            val origin = describeImageOrigin(draw)
            // RGBA paint shaders are not source consumers, including on eliminated draws.
            val material = if (origin.description.color.channelOrder == ImageChannelOrderV1.ALPHA)
                EffectiveMaterialPlanner.imageMaskMaterial(draw) else origin.original
            authenticateComposed(material,runtimeCatalog)
            authenticateComposedMetadata(draw, material, origin)
        }

        /** This metadata walk is shared by authenticated projection and the historical image elision. */
        private fun authenticateComposedMetadata(draw: DrawNode, material: MaterialNode, origin: ImageChildMetadata?) {
            draw.paint?.colorFilter?.let(::compileFilter)
            fun visit(node: MaterialNode,coordinates: List<CoordinateNodeV2>) {
                validateComposedNode(node)
                composedNoiseParameters(node)
                val needsCoordinates = node is MaterialNode.ImageSample || node is MaterialNode.PerlinNoise ||
                    node is MaterialNode.FractalNoise || node is MaterialNode.LinearGradient ||
                    node is MaterialNode.RadialGradient || node is MaterialNode.SweepGradient || node is MaterialNode.ConicalGradient
                if (needsCoordinates && node !== origin?.original && !gradientCollapsesCoordinates(node)) {
                    when (val result = MaterialCoordinatePlanV2.fromCtmAndNodes(draw.transform,coordinates)) {
                        is MaterialCoordinatePlanV2.Build.Ready -> Unit
                        is MaterialCoordinatePlanV2.Build.Refused -> throw IllegalArgumentException(result.code)
                    }
                }
                if (node is MaterialNode.ImageSample && node !== origin?.original)
                    EffectiveMaterialPlanner.describeImageSample(node,false).validateUploadMetadata()
                when (node) {
                    is MaterialNode.Blend -> { visit(node.dst,coordinates); visit(node.src,coordinates) }
                    is MaterialNode.Opacity -> visit(node.material,coordinates)
                    is MaterialNode.WithColorFilter -> visit(node.material,coordinates)
                    is MaterialNode.WithWorkingColorSpace -> visit(node.material,coordinates)
                    is MaterialNode.WithLocalMatrix -> visit(node.material,coordinates+CoordinateNodeV2.LocalMatrix(node.matrix))
                    is MaterialNode.CoordClamp -> visit(node.material,coordinates+CoordinateNodeV2.CoordClamp(node.copySubset()))
                    is MaterialNode.RuntimeEffect -> node.forEach { visit(it.material,coordinates) }
                    else -> Unit
                }
            }
            visit(material,emptyList())
        }

        /** The same prefix metadata checks serve consumers and authenticated elisions. */
        private fun validateComposedNode(node: MaterialNode): ColorFilterExecutionPlanV1? {
            validatePendingGradient(node)
            if (node is MaterialNode.Opacity) require(node.alpha.isFinite() && node.alpha in 0f..1f) { W5aPlanDiagnostics.InvalidOpacity }
            return when (node) {
                is MaterialNode.WithColorFilter -> compileFilter(node.filter)
                MaterialNode.Transparent,is MaterialNode.Solid,is MaterialNode.Opacity,is MaterialNode.Blend,
                is MaterialNode.RuntimeEffect,is MaterialNode.WithWorkingColorSpace,is MaterialNode.WithLocalMatrix,
                is MaterialNode.CoordClamp,is MaterialNode.ImageSample,is MaterialNode.PerlinNoise,is MaterialNode.FractalNoise,
                is MaterialNode.LinearGradient,is MaterialNode.RadialGradient,is MaterialNode.SweepGradient,is MaterialNode.ConicalGradient -> null
            }
        }

        private fun composedNoiseParameters(node: MaterialNode): NoiseParametersV1? = when (node) {
            is MaterialNode.PerlinNoise -> NoiseParametersV1(node.baseX,node.baseY,node.numOctaves,node.seed,node.tileSize,false)
            is MaterialNode.FractalNoise -> NoiseParametersV1(node.baseX,node.baseY,node.numOctaves,node.seed,node.tileSize,true)
            else -> null
        }

        private fun gradientCollapsesCoordinates(node: MaterialNode): Boolean = when (node) {
            is MaterialNode.LinearGradient -> node.stops().size == 1
            is MaterialNode.RadialGradient -> node.stops().size == 1
            is MaterialNode.SweepGradient -> node.stops().size == 1
            else -> false
        }

        private fun captureComposed(draw: DrawNode,bounds: RectF32,blend: BlendPlan,
            runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot, material: MaterialNode = draw.material,
            imageOrigin: ImageChildMetadata? = null,
            tailAlphaF32: Float = draw.paint?.takeIf { it.shader != null }?.color?.alphaNormalized ?: 1f,
            authentication: PreparedAuthentication? = null): MaterialSourceConstructionV4 {
            val mesh = draw.geometry as? org.graphiks.kanvas.render.ir.GeometryNode.IndexedMesh
            val preparedMesh = draw.origin in setOf(DrawOrigin.VERTICES, DrawOrigin.MESH) &&
                mesh != null && mesh.program == null && mesh.meshProgram == null
            val runtimeEntries = if (authentication == null) {
                validateComposedDraw(draw, material, imageOrigin)
                // The mask product is synthesized, like paint alpha/filter, not a captured occurrence.
                authenticateComposed(if (imageOrigin != null && material is MaterialNode.Blend)
                    material.dst else material, runtimeCatalog)
            } else {
                require(authentication.draw === draw && authentication.material === material &&
                    authentication.imageOrigin === imageOrigin && authentication.runtimeCatalog === runtimeCatalog &&
                    authentication.tailAlphaF32 == tailAlphaF32) { W5gPlanDiagnostics.Schema }
                authentication.runtimeEntries
            }
            return captureComposedNodes(draw, bounds, blend, runtimeCatalog, material, imageOrigin, tailAlphaF32,
                preparedMesh, mesh, runtimeEntries)
        }

        private fun validateComposedDraw(draw: DrawNode, material: MaterialNode, imageOrigin: ImageChildMetadata?) {
            var sliceLeaf = material
            while (true) sliceLeaf = when (val node = sliceLeaf) {
                is MaterialNode.Opacity -> node.material
                is MaterialNode.WithColorFilter -> node.material
                is MaterialNode.WithWorkingColorSpace -> node.material
                is MaterialNode.WithLocalMatrix -> node.material
                is MaterialNode.CoordClamp -> node.material
                else -> break
            }
            val sliceCode = if (sliceLeaf is MaterialNode.PerlinNoise || sliceLeaf is MaterialNode.FractalNoise)
                W5gPlanDiagnostics.NoiseUnpromoted else W5gPlanDiagnostics.Unpromoted
            val mesh = draw.geometry as? org.graphiks.kanvas.render.ir.GeometryNode.IndexedMesh
            val preparedMesh = draw.origin in setOf(DrawOrigin.VERTICES, DrawOrigin.MESH) &&
                mesh != null && mesh.program == null && mesh.meshProgram == null
            require(imageOrigin?.origin === draw || (draw.origin in setOf(DrawOrigin.RECT,DrawOrigin.RRECT,DrawOrigin.PATH) && draw.paint?.style == PaintStyleNode.FILL ||
                draw.origin == DrawOrigin.PATH && draw.paint?.style == PaintStyleNode.STROKE ||
                draw.origin in setOf(DrawOrigin.POINT,DrawOrigin.POINTS,DrawOrigin.TEXT) || preparedMesh) &&
                draw.resource == null && (draw.operationBlendMode == null || preparedMesh)) { sliceCode }
            require(colorFilterEffectsMatchPaint(draw)) { W5gPlanDiagnostics.Schema }
        }

        private fun captureComposedNodes(draw: DrawNode, bounds: RectF32, blend: BlendPlan,
            runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot, material: MaterialNode,
            imageOrigin: ImageChildMetadata?, tailAlphaF32: Float, preparedMesh: Boolean,
            mesh: org.graphiks.kanvas.render.ir.GeometryNode.IndexedMesh?,
            runtimeEntries: java.util.IdentityHashMap<MaterialNode.RuntimeEffect, RuntimeEffectSemanticEntryV1>): MaterialSourceConstructionV4 {
            val nodes = mutableListOf<ComposedMetadata.Node>()
            val mappings = mutableListOf<ComposedBindingLayoutV1.UniformMapping>()
            data class Context(val coordinateNodes: List<MaterialNode>,val domain: ColorInterpolation?)
            data class Field(val owner: Int,val ordinal: Int,val bytes: Long)
            data class Draft(val owner: Int,val original: MaterialNode,val children: List<MaterialEvaluationRefV5>,
                val field: Field?,val filter: ColorFilterExecutionPlanV1?,val gradient: MaterialSourceConstructionV4?,
                val image: ImageChildMetadata?, val noise: NoiseChildMetadata?, val runtime: RuntimeEffectSemanticEntryV1?)
            val completed = java.util.IdentityHashMap<MaterialNode,MutableMap<Context,MaterialEvaluationRefV5>>()
            val ownerIndices = java.util.IdentityHashMap<MaterialNode,Int>()
            val scalarFields = java.util.IdentityHashMap<MaterialNode,Field>()
            val fields = mutableListOf<Field>()
            val drafts = mutableListOf<Draft>()
            val active = java.util.IdentityHashMap<MaterialNode,Unit>()
            var owners = 0
            var cursor = 0L
            fun gradientSource(node: MaterialNode,context: Context): MaterialSourceConstructionV4 {
                val coordinateNodes = context.coordinateNodes.map { when(it) {
                    is MaterialNode.WithLocalMatrix -> CoordinateNodeV2.LocalMatrix(it.matrix)
                    is MaterialNode.CoordClamp -> CoordinateNodeV2.CoordClamp(it.copySubset())
                    else -> error(W5gPlanDiagnostics.Schema)
                } }
                val solidStop=gradientCollapsesCoordinates(node)
                val coordinates = if(solidStop) SourceCoordinatesV4.None else when(val built=MaterialCoordinatePlanV2.fromCtmAndNodes(draw.transform,coordinateNodes)) {
                    is MaterialCoordinatePlanV2.Build.Ready -> SourceCoordinatesV4.V2(built.coordinates)
                    is MaterialCoordinatePlanV2.Build.Refused -> throw IllegalArgumentException(built.code)
                }
                var wrapped = context.domain?.let { MaterialNode.WithWorkingColorSpace(node,it) } ?: node
                context.coordinateNodes.asReversed().forEach { wrapper -> wrapped=when(wrapper) {
                    is MaterialNode.WithLocalMatrix -> MaterialNode.WithLocalMatrix(wrapped,wrapper.matrix)
                    is MaterialNode.CoordClamp -> MaterialNode.CoordClamp(wrapped,wrapper.copySubset())
                    else -> error(W5gPlanDiagnostics.Schema)
                } }
                val childDraw = draw.copy(material=wrapped,paint=draw.paint?.copy(shader=wrapped,
                    color=ColorARGB.White,colorFilter=null),effects=org.graphiks.kanvas.render.ir.EffectStack.Empty)
                return when(val captured=capture(childDraw,coordinates,bounds,blend,runtimeCatalog=runtimeCatalog,
                    imageOriginLeaf=imageOrigin != null)) {
                    is SourceConstructionResultV4.Built -> captured.value
                    is SourceConstructionResultV4.Refused -> throw IllegalArgumentException(captured.diagnosticCode)
                }
            }
            fun visit(node: MaterialNode,context: Context): MaterialEvaluationRefV5 {
                require(active.put(node,Unit) == null) { W5gPlanDiagnostics.Schema }
                completed[node]?.get(context)?.let { active.remove(node); return it }
                val owner = ownerIndices.getOrPut(node) { owners++ }
                val runtime = (node as? MaterialNode.RuntimeEffect)?.let { runtimeEntries.getValue(it) }
                val filter = validateComposedNode(node)
                val gradient = when(node) {
                    is MaterialNode.LinearGradient,is MaterialNode.RadialGradient,is MaterialNode.SweepGradient,
                    is MaterialNode.ConicalGradient -> gradientSource(node,context)
                    else -> null
                }
                val image = (node as? MaterialNode.ImageSample)?.let { sample ->
                    if (imageOrigin?.original === sample) imageOrigin else {
                    val description=EffectiveMaterialPlanner.describeImageSample(sample,false)
                    description.validateUploadMetadata()
                    val coordinates=when(val built=MaterialCoordinatePlanV2.fromCtmAndNodes(draw.transform,
                        context.coordinateNodes.map { when(it) {
                            is MaterialNode.WithLocalMatrix -> CoordinateNodeV2.LocalMatrix(it.matrix)
                            is MaterialNode.CoordClamp -> CoordinateNodeV2.CoordClamp(it.copySubset())
                            else -> error(W5gPlanDiagnostics.Schema)
                        } })) {
                        is MaterialCoordinatePlanV2.Build.Ready -> built.coordinates
                        is MaterialCoordinatePlanV2.Build.Refused -> throw IllegalArgumentException(built.code)
                    }
                    ImageChildMetadata(sample,description,coordinates)
                    }
                }
                val parameters = composedNoiseParameters(node)
                val noise = parameters?.let {
                    val coordinates = when (val built = MaterialCoordinatePlanV2.fromCtmAndNodes(draw.transform,
                        context.coordinateNodes.map { wrapper -> when (wrapper) {
                            is MaterialNode.WithLocalMatrix -> CoordinateNodeV2.LocalMatrix(wrapper.matrix)
                            is MaterialNode.CoordClamp -> CoordinateNodeV2.CoordClamp(wrapper.copySubset())
                            else -> error(W5gPlanDiagnostics.Schema)
                        } })) {
                        is MaterialCoordinatePlanV2.Build.Ready -> built.coordinates
                        is MaterialCoordinatePlanV2.Build.Refused -> throw IllegalArgumentException(built.code)
                    }
                    NoiseChildMetadata(it,coordinates)
                }
                val bytes = when(node) {
                    MaterialNode.Transparent, is MaterialNode.Solid, is MaterialNode.Opacity -> 16L
                    is MaterialNode.RuntimeEffect -> requireNotNull(runtime).descriptor.uniformBlock.sizeBytesI32.toLong()
                    is MaterialNode.WithColorFilter -> requireNotNull(filter).dynamicByteCountI64
                    is MaterialNode.Blend, is MaterialNode.WithWorkingColorSpace,
                    is MaterialNode.WithLocalMatrix,is MaterialNode.CoordClamp -> 0L
                    is MaterialNode.LinearGradient,is MaterialNode.RadialGradient,is MaterialNode.SweepGradient,
                    is MaterialNode.ConicalGradient -> requireNotNull(gradient).uniformBytesI64()
                    is MaterialNode.ImageSample -> requireNotNull(image).uniformBytesI64
                    is MaterialNode.PerlinNoise, is MaterialNode.FractalNoise -> requireNotNull(noise).uniformBytesI64
                    else -> {
                        validatePendingGradient(node)
                        throw IllegalArgumentException(W5gPlanDiagnostics.Unpromoted)
                    }
                }
                val field = if(bytes == 0L) null else if(gradient != null || image != null || noise != null) Field(owner,fields.size,bytes).also { fields += it }
                    else scalarFields.getOrPut(node) { Field(owner,fields.size,bytes).also { fields += it } }
                val children = when(node) {
                    is MaterialNode.Blend -> listOf(visit(node.dst,context),visit(node.src,context))
                    is MaterialNode.Opacity -> listOf(visit(node.material,context))
                    is MaterialNode.WithColorFilter -> listOf(visit(node.material,context))
                    is MaterialNode.WithWorkingColorSpace -> listOf(visit(node.material,
                        context.copy(domain=context.domain ?: node.interpolation)))
                    is MaterialNode.WithLocalMatrix -> listOf(visit(node.material,
                        context.copy(coordinateNodes=context.coordinateNodes+node)))
                    is MaterialNode.CoordClamp -> listOf(visit(node.material,
                        context.copy(coordinateNodes=context.coordinateNodes+node)))
                    is MaterialNode.RuntimeEffect -> node.map { visit(it.material,context) }
                    else -> emptyList()
                }
                drafts += Draft(owner,node,children,field,filter,gradient,image,noise,runtime)
                active.remove(node)
                return MaterialEvaluationRefV5(drafts.lastIndex).also { completed.getOrPut(node) { mutableMapOf() }[context] = it }
            }
            var root: MaterialNode = MaterialNode.Opacity(material,tailAlphaF32)
            draw.paint?.colorFilter?.let { root = MaterialNode.WithColorFilter(root,it) }
            // Prefix owner assignment includes the actual outer paint operations;
            // postorder evaluation refs remain explicit and independently checked.
            visit(root,Context(emptyList(),null))
            val primitiveRef = if (preparedMesh && mesh!!.copyColors() != null)
                completed.getValue(draw.material).getValue(Context(emptyList(), null)) else null
            val offsets = mutableMapOf<Field,Int>()
            for(owner in 0 until owners) {
                val base=cursor
                fields.filter { it.owner == owner }.forEach { field ->
                    offsets[field]=Math.toIntExact(cursor)
                    val runtime = drafts.first { it.field === field }.runtime
                    if (runtime == null) mappings += ComposedBindingLayoutV1.UniformMapping(owner,Math.toIntExact(cursor-base),
                        Math.toIntExact(cursor),Math.toIntExact(field.bytes),16)
                    else runtime.descriptor.uniformBlock.slots.forEach { slot ->
                        mappings += ComposedBindingLayoutV1.UniformMapping(owner,slot.offsetBytesI32,
                            Math.toIntExact(Math.addExact(cursor,slot.offsetBytesI32.toLong())),slot.sizeBytesI32,slot.alignmentBytesI32)
                    }
                    cursor=Math.addExact(cursor,field.bytes)
                    require(cursor <= Int.MAX_VALUE.toLong() && cursor <= UInt.MAX_VALUE.toLong()) { W5gPlanDiagnostics.Uniform }
                }
            }
            drafts.forEach { nodes += ComposedMetadata.Node(it.owner,it.original,it.children,
                it.field?.let(offsets::getValue) ?: 0,it.filter,it.gradient,it.image,it.noise,it.runtime) }
            val firstGradientOwner=nodes.filter { it.gradientSource?.hasGradientStorage == true }.minOfOrNull { it.ownerNodeIndexI32 }
            val firstNoiseOwner=nodes.filter { it.noiseSource != null }
                .minOfOrNull { it.ownerNodeIndexI32 }
            val resourceOwners=nodes.filter { it.imageSource != null }.map { it.ownerNodeIndexI32 to 0 }.distinct() +
                listOfNotNull(firstGradientOwner,firstNoiseOwner).map { it to 0 } + nodes.filter { it.runtime != null }
                    .distinctBy { it.ownerNodeIndexI32 }.flatMap { node -> requireNotNull(node.runtime).descriptor.logicalResources.map {
                        node.ownerNodeIndexI32 to it.logicalSlotI32 } }
            val resources=resourceOwners.sortedWith(compareBy({it.first},{it.second})).mapIndexed { index,(owner,logicalSlot) ->
                val runtime = nodes.firstOrNull { it.ownerNodeIndexI32 == owner }?.runtime
                if (runtime != null) when (val facts = runtime.descriptor.logicalResources.single { it.logicalSlotI32 == logicalSlot }.facts) {
                    is org.graphiks.kanvas.render.ir.RuntimeLogicalResourceFactsV1.StorageRead -> ComposedBindingLayoutV1.Resource(owner,logicalSlot,1,index+1,2u,1u,
                        buffer=ComposedBindingLayoutV1.Buffer(2u,alignRuntimeStorageBytesI64(facts.minBindingSizeBytesI64),storageKind=ComposedBindingLayoutV1.StorageKind.RUNTIME_READ))
                    is org.graphiks.kanvas.render.ir.RuntimeLogicalResourceFactsV1.Texture2DFloatFilterable -> ComposedBindingLayoutV1.Resource(owner,logicalSlot,1,index+1,2u,2u,
                        texture=ComposedBindingLayoutV1.Texture(1u,1u,facts.multisampled))
                    is org.graphiks.kanvas.render.ir.RuntimeLogicalResourceFactsV1.Sampler -> ComposedBindingLayoutV1.Resource(owner,logicalSlot,1,index+1,2u,3u,
                        sampler=ComposedBindingLayoutV1.Sampler(when(facts.type) {
                            org.graphiks.kanvas.render.ir.RuntimeSamplerTypeV1.FILTERING -> 1u
                            org.graphiks.kanvas.render.ir.RuntimeSamplerTypeV1.NON_FILTERING -> 2u
                        }))
                }
                else if(owner == firstGradientOwner) ComposedBindingLayoutV1.Resource(owner,0,1,index+1,2u,1u,
                    buffer=ComposedBindingLayoutV1.Buffer(2u,32L))
                else if(owner == firstNoiseOwner) ComposedBindingLayoutV1.Resource(owner,0,1,index+1,2u,1u,
                    buffer=ComposedBindingLayoutV1.Buffer(2u,16L,storageKind=ComposedBindingLayoutV1.StorageKind.NOISE_U32))
                else ComposedBindingLayoutV1.Resource(owner,0,1,index+1,2u,2u,texture=ComposedBindingLayoutV1.Texture(1u,1u))
            }
            val metadata = ComposedMetadata(nodes,ComposedBindingLayoutV1(mappings,cursor,resources), primitiveRef,
                primitiveRef?.let { draw.operationBlendMode ?: org.graphiks.kanvas.render.ir.BlendMode.MODULATE },
                primitiveRef?.let { draw.geometry as GeometryNode.IndexedMesh })
            return MaterialSourceConstructionV4(draw.material,draw.paint,SourceCoordinatesV4.None,bounds,blend,
                "captured-composed-v6:${java.util.UUID.randomUUID()}",null,null,composed=metadata,runtimeCatalog=runtimeCatalog)
        }
        /** At this leaf's prefix visit only: validate recorded metadata, never prepare a pending source. */
        private fun validatePendingGradient(source: MaterialNode) {
            val stops: List<GradientStop>
            val requested: GradientTileModeV2
            val degeneracy: GradientDegeneracyV1
            when (source) {
                is MaterialNode.LinearGradient -> {
                    require(listOf(source.start.x,source.start.y,source.end.x,source.end.y).all(Float::isFinite)) { W5cPlanDiagnostics.NonFinite }
                    stops = source.stops(); requested = GradientTileModeV2.valueOf(source.tileMode.name)
                    degeneracy = LinearGradientDegeneracyV1.of(source.start,source.end)
                }
                is MaterialNode.RadialGradient -> {
                    require(listOf(source.center.x,source.center.y,source.radius).all(Float::isFinite)) { W5cPlanDiagnostics.NonFinite }
                    require(source.radius >= 0f) { W5cPlanDiagnostics.NegativeRadius }
                    stops = source.stops(); requested = GradientTileModeV2.valueOf(source.tileMode.name)
                    degeneracy = RadialGradientDegeneracyV1(source.radius,source.radius <= 0.000030517578125f)
                }
                is MaterialNode.SweepGradient -> {
                    require(listOf(source.center.x,source.center.y,source.startAngle,source.endAngle).all(Float::isFinite)) { W5cPlanDiagnostics.NonFinite }
                    stops = source.stops(); requested = GradientTileModeV2.valueOf(source.tileMode.name)
                    degeneracy = SweepGradientDegeneracyV1.of(source.startAngle,source.endAngle)
                    require(!degeneracy.sweepOrderingInvalid) { W5cPlanDiagnostics.SweepOrdering }
                }
                is MaterialNode.ConicalGradient -> {
                    require(listOf(source.start.x,source.start.y,source.end.x,source.end.y,
                        source.startRadius,source.endRadius).all(Float::isFinite)) { W5cPlanDiagnostics.NonFinite }
                    require(source.startRadius >= 0f && source.endRadius >= 0f) { W5cPlanDiagnostics.NegativeRadius }
                    stops = source.stops(); requested = GradientTileModeV2.valueOf(source.tileMode.name)
                    degeneracy = ConicalGradientDegeneracyV1.of(source.start,source.startRadius,source.end,source.endRadius)
                }
                else -> return
            }
            val scalars = when (degeneracy) {
                is LinearGradientDegeneracyV1 -> degeneracy.copyScalarsF32()
                is RadialGradientDegeneracyV1 -> listOf(degeneracy.radialRadiusF32)
                is SweepGradientDegeneracyV1 -> listOf(degeneracy.startAngleDegreesF32,degeneracy.endAngleDegreesF32,degeneracy.sweepSpanDegreesF32)
                is ConicalGradientDegeneracyV1 -> degeneracy.copyScalarsF32()
            }
            require(scalars.all(Float::isFinite)) { W5cPlanDiagnostics.NumericDomainUnbounded }
            val tile = requested.operationGraph((degeneracy as? SweepGradientDegeneracyV1)?.sweepFullCoverage == true)
            GradientStopCursorV4.of(stops,tile.effectiveMode,source is MaterialNode.ConicalGradient)
        }
        fun captureImage(metadata: ImageMetadata,bounds: RectF32,blend: BlendPlan,
            runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot): MaterialSourceConstructionV4 {
            val identity = buildString {
                append("captured-image-source-v4:").append(metadata.upload.contentIdentity)
                append(':').append(metadata.coordinates.canonicalIdentity).append(':').append(metadata.color)
                append(':').append(metadata.sampling).append(':').append(metadata.tileModes)
                append(':').append(metadata.cells?.joinToString { it.canonicalIdentity })
                append(':').append(metadata.latticeKinds).append(':').append(metadata.paintAlphaF32.toRawBits())
                append(':').append(metadata.latticePaintAlphaF32.toRawBits()).append(':').append(metadata.atlasColor)
                append(':').append(metadata.atlasMode).append(':').append(metadata.child?.canonicalIdentity)
                metadata.wrappers.forEach { append(':').append(when (it) {
                    is SourceUnaryMetadataV4.Opacity -> "opacity:${it.alphaF32.toRawBits()}"
                    is SourceUnaryMetadataV4.Filter -> it.execution.canonicalIdentity
                }) }
                append(':').append(bounds.left.toRawBits()).append(':').append(bounds.top.toRawBits())
                append(':').append(bounds.right.toRawBits()).append(':').append(bounds.bottom.toRawBits())
            }
            return MaterialSourceConstructionV4(metadata.originalDraw.material,metadata.originalDraw.paint,
                SourceCoordinatesV4.V3(metadata.coordinates),bounds,blend,identity,null,null,metadata,runtimeCatalog=runtimeCatalog)
        }

        fun retain(draw: DrawNode, resolved: EffectiveMaterialPlanner.Result.Ready, bounds: RectF32): MaterialSourceConstructionV4 =
            MaterialSourceConstructionV4(draw.material, draw.paint, when (val authority = resolved.materialAuthority) {
                is PlanDrawMaterialAuthority.MaterialV5 -> SourceCoordinatesV4.None
                is PlanDrawMaterialAuthority.MaterialV4 -> authority.coordinates
                is PlanDrawMaterialAuthority.MaterialV3 -> SourceCoordinatesV4.V3(authority.imageCoordinates)
                is PlanDrawMaterialAuthority.MaterialV2 -> SourceCoordinatesV4.V2(authority.coordinates)
                is PlanDrawMaterialAuthority.MaterialV1 ->
                    (authority.coordinates ?: MaterialCoordinatePlanV1.fromCtm(draw.transform))
                        ?.let(SourceCoordinatesV4::V1) ?: SourceCoordinatesV4.None
                is PlanDrawMaterialAuthority.LegacyColorV1 -> error(W5fPlanDiagnostics.Schema)
            }, bounds, resolved.blend, resolved.table.sourceIdentity(resolved.root), resolved, null)

        fun capture(draw: DrawNode, coordinates: SourceCoordinatesV4, bounds: RectF32,
            blend: BlendPlan,imageMaskChild: Boolean = false,
            runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot,
            composedV6: Boolean = false,
            imageOriginLeaf: Boolean = false): SourceConstructionResultV4<MaterialSourceConstructionV4> = try {
            require(bounds.isFinite() && bounds.isSorted()) { W5fPlanDiagnostics.Schema }
            if (composedV6 || containsComposed(draw.material)) {
                require(!imageMaskChild) { W5gPlanDiagnostics.Unpromoted }
                SourceConstructionResultV4.Built(captureComposed(draw,bounds,blend,runtimeCatalog))
            } else {
            val preparedMesh = (draw.geometry as? org.graphiks.kanvas.render.ir.GeometryNode.IndexedMesh)?.let {
                draw.origin in setOf(DrawOrigin.VERTICES, DrawOrigin.MESH) && it.program == null && it.meshProgram == null
            } == true
            require(imageMaskChild || imageOriginLeaf && draw.origin == DrawOrigin.IMAGE && draw.geometry is GeometryNode.ImagePatch ||
                (draw.origin in setOf(DrawOrigin.RECT, DrawOrigin.RRECT, DrawOrigin.PATH,
                DrawOrigin.POINT, DrawOrigin.POINTS, DrawOrigin.TEXT) || preparedMesh) &&
                draw.resource == null && (draw.operationBlendMode == null || preparedMesh)) { W5fPlanDiagnostics.Unpromoted }
            require(colorFilterEffectsMatchPaint(draw)) { W5fPlanDiagnostics.Schema }
            val wrappers = mutableListOf<SourceUnaryMetadataV4>()
            val coordinateNodes = mutableListOf<CoordinateNodeV2>()
            val original = if (imageMaskChild) EffectiveMaterialPlanner.imageMaskMaterial(draw) else draw.material
            var leaf = original
            var depth = 0
            var selectedDomain: ColorInterpolation? = null
            while (true) {
                require(++depth <= 64) { W5fPlanDiagnostics.Schema }
                when (val node = leaf) {
                    is MaterialNode.WithWorkingColorSpace -> {
                        if (selectedDomain == null) selectedDomain = node.interpolation
                        leaf = node.material
                    }
                    is MaterialNode.Opacity -> {
                        require(node.alpha.isFinite() && node.alpha in 0f..1f) { W5aPlanDiagnostics.InvalidOpacity }
                        if (node.alpha != 1f) wrappers += SourceUnaryMetadataV4.Opacity(node.alpha)
                        leaf = node.material
                    }
                    is MaterialNode.WithColorFilter -> {
                        wrappers += SourceUnaryMetadataV4.Filter(compileFilter(node.filter)); leaf = node.material
                    }
                    is MaterialNode.WithLocalMatrix -> {
                        coordinateNodes += CoordinateNodeV2.LocalMatrix(node.matrix); leaf = node.material
                    }
                    is MaterialNode.CoordClamp -> {
                        coordinateNodes += CoordinateNodeV2.CoordClamp(node.copySubset()); leaf = node.material
                    }
                    else -> break
                }
            }
            var interpolation: ColorInterpolation
            val family: GradientFamilyV2
            val requested: GradientTileModeV2
            val stops: List<GradientStop>
            val degeneracy: GradientDegeneracyV1
            when (val source = leaf) {
                is MaterialNode.LinearGradient -> {
                    require(listOf(source.start.x, source.start.y, source.end.x, source.end.y).all(Float::isFinite)) { W5cPlanDiagnostics.NonFinite }
                    interpolation = source.interpolation; family = GradientFamilyV2.LINEAR
                    requested = GradientTileModeV2.valueOf(source.tileMode.name); stops = source.stops()
                    degeneracy = LinearGradientDegeneracyV1.of(source.start, source.end)
                }
                is MaterialNode.RadialGradient -> {
                    require(listOf(source.center.x, source.center.y, source.radius).all(Float::isFinite)) { W5cPlanDiagnostics.NonFinite }
                    require(source.radius >= 0f) { W5cPlanDiagnostics.NegativeRadius }
                    interpolation = source.interpolation; family = GradientFamilyV2.RADIAL
                    requested = GradientTileModeV2.valueOf(source.tileMode.name); stops = source.stops()
                    degeneracy = RadialGradientDegeneracyV1(source.radius, source.radius <= 0.000030517578125f)
                }
                is MaterialNode.SweepGradient -> {
                    require(listOf(source.center.x, source.center.y, source.startAngle, source.endAngle).all(Float::isFinite)) { W5cPlanDiagnostics.NonFinite }
                    interpolation = source.interpolation; family = GradientFamilyV2.SWEEP
                    requested = GradientTileModeV2.valueOf(source.tileMode.name); stops = source.stops()
                    degeneracy = SweepGradientDegeneracyV1.of(source.startAngle, source.endAngle)
                    require(!degeneracy.sweepOrderingInvalid) { W5cPlanDiagnostics.SweepOrdering }
                }
                is MaterialNode.ConicalGradient -> {
                    require(listOf(source.start.x, source.start.y, source.end.x, source.end.y,
                        source.startRadius, source.endRadius).all(Float::isFinite)) { W5cPlanDiagnostics.NonFinite }
                    require(source.startRadius >= 0f && source.endRadius >= 0f) { W5cPlanDiagnostics.NegativeRadius }
                    interpolation = source.interpolation; family = GradientFamilyV2.CONICAL
                    requested = GradientTileModeV2.valueOf(source.tileMode.name); stops = source.stops()
                    degeneracy = ConicalGradientDegeneracyV1.of(source.start, source.startRadius, source.end, source.endRadius)
                }
                else -> error(W5fPlanDiagnostics.Unpromoted)
            }
            interpolation = selectedDomain ?: interpolation
            val scalars = when (degeneracy) {
                is LinearGradientDegeneracyV1 -> degeneracy.copyScalarsF32()
                is RadialGradientDegeneracyV1 -> listOf(degeneracy.radialRadiusF32)
                is SweepGradientDegeneracyV1 -> listOf(degeneracy.startAngleDegreesF32, degeneracy.endAngleDegreesF32, degeneracy.sweepSpanDegreesF32)
                is ConicalGradientDegeneracyV1 -> degeneracy.copyScalarsF32()
            }
            require(scalars.all(Float::isFinite)) { W5cPlanDiagnostics.NumericDomainUnbounded }
            val tile = requested.operationGraph((degeneracy as? SweepGradientDegeneracyV1)?.sweepFullCoverage == true)
            val cursor = GradientStopCursorV4.of(stops, tile.effectiveMode, family == GradientFamilyV2.CONICAL)
            if (cursor.solidColor == null) {
                require(coordinates is SourceCoordinatesV4.V2) { W5fPlanDiagnostics.Schema }
                when (val expected = MaterialCoordinatePlanV2.fromCtmAndNodes(draw.transform,coordinateNodes)) {
                    is MaterialCoordinatePlanV2.Build.Ready -> require(coordinates.plan == expected.coordinates) { W5fPlanDiagnostics.Schema }
                    is MaterialCoordinatePlanV2.Build.Refused -> throw IllegalArgumentException(expected.code)
                }
            } else require(coordinates == SourceCoordinatesV4.None) { W5fPlanDiagnostics.Schema }
            val orderedWrappers = wrappers.asReversed().toMutableList()
            val paintAlpha = draw.paint?.takeIf { it.shader != null }?.color?.alphaNormalized ?: 1f
            if (paintAlpha != 1f) orderedWrappers += SourceUnaryMetadataV4.Opacity(paintAlpha)
            if (!imageMaskChild) draw.paint?.colorFilter?.let { orderedWrappers += SourceUnaryMetadataV4.Filter(compileFilter(it)) }
            if (!imageMaskChild && orderedWrappers.any { it is SourceUnaryMetadataV4.Filter }) require(
                draw.origin in setOf(DrawOrigin.RECT, DrawOrigin.PATH) && draw.paint?.style == PaintStyleNode.FILL
            ) { W5fPlanDiagnostics.Unpromoted }
            val metadata = GradientMetadata(leaf, interpolation, family, tile, degeneracy, cursor, orderedWrappers)
            val identity = "captured-source-v4:${draw.material.canonicalId.value}:${draw.paint?.canonicalId?.value}:" +
                (if (imageMaskChild) "child:${original.canonicalId.value}:" else "") +
                "${coordinates.identityV4()}:${bounds.left.toRawBits()}:${bounds.top.toRawBits()}:" +
                "${bounds.right.toRawBits()}:${bounds.bottom.toRawBits()}:$blend:${metadata.rangeIdentity}"
            SourceConstructionResultV4.Built(MaterialSourceConstructionV4(original, draw.paint,
                coordinates, bounds, blend, identity, null, metadata,runtimeCatalog=runtimeCatalog))
            }
        } catch (failure: IllegalArgumentException) {
            sourceConstructionRefusalV4(failure.message ?: W5fPlanDiagnostics.Schema)
        } catch (failure: IllegalStateException) {
            sourceConstructionRefusalV4(failure.message ?: W5fPlanDiagnostics.Schema)
        }

        internal fun compileFilter(node: org.graphiks.kanvas.render.ir.ColorFilterNode): ColorFilterExecutionPlanV1 =
            when (val result = ColorFilterPlanCompilerV1.compile(node)) {
                is ColorFilterCompileResultV1.Ready -> result.execution
                is ColorFilterCompileResultV1.Refused -> throw IllegalArgumentException(result.diagnosticCode)
            }
    }
}

/** Source occurrence order only: actual draw refs are checked by the deferred lane factory. */
internal class MaterialSourceConstructionTableV4 private constructor(sources: List<MaterialSourceConstructionV4>) {
    private val stored = immutableList(sources)
    fun sources(): List<MaterialSourceConstructionV4> = stored
    fun source(symbolicRoot: MaterialPlanRef): MaterialSourceConstructionV4 = stored.getOrElse(symbolicRoot.indexI32) {
        throw IllegalArgumentException(W5fPlanDiagnostics.Schema)
    }
    companion object {
        fun of(sources: List<MaterialSourceConstructionV4>): SourceConstructionResultV4<MaterialSourceConstructionTableV4> =
            SourceConstructionResultV4.Built(MaterialSourceConstructionTableV4(sources))
    }
}
