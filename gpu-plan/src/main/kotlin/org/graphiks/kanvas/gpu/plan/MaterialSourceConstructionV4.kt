package org.graphiks.kanvas.gpu.plan

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
) {
    private val bounds = bounds.copy()
    val deviceBoundsF32: RectF32 get() = bounds.copy()
    val pending: Boolean get() = resolvedSource == null
    val hasGradientStorage: Boolean get() = image?.child?.hasGradientStorage ?: gradient?.stops?.countI32?.let { it > 0 }
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

        fun bind(childSource: EffectiveMaterialPlanner.Result.Ready?,frameBytesI64: Long): EffectiveMaterialPlanner.Result.Ready {
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

    internal class ComposedMetadata(nodes: List<Node>, val layout: ComposedBindingLayoutV1) {
        val nodes: List<Node> = immutableList(nodes)
        class Node(val ownerNodeIndexI32: Int, val original: MaterialNode, children: List<MaterialEvaluationRefV5>,
            val offsetBytesI32: Int, val filter: ColorFilterExecutionPlanV1?) {
            val children: List<MaterialEvaluationRefV5> = immutableList(children)
            val topologyIdentity: String = when (original) {
                MaterialNode.Transparent -> "transparent"
                is MaterialNode.Solid -> "solid"
                is MaterialNode.Opacity -> "opacity"
                is MaterialNode.Blend -> "blend:${original.mode}"
                is MaterialNode.WithColorFilter -> "filter:${requireNotNull(filter).structuralIdentity}"
                is MaterialNode.WithWorkingColorSpace -> "working:${original.interpolation}"
                else -> error(W5gPlanDiagnostics.Unpromoted)
            }
        }
    }

    companion object {
        fun containsComposed(root: MaterialNode): Boolean = when (root) {
            is MaterialNode.Blend -> true
            is MaterialNode.Opacity -> containsComposed(root.material)
            is MaterialNode.WithColorFilter -> containsComposed(root.material)
            is MaterialNode.WithWorkingColorSpace -> containsComposed(root.material)
            is MaterialNode.WithLocalMatrix -> containsComposed(root.material)
            is MaterialNode.CoordClamp -> containsComposed(root.material)
            else -> false
        }
        private fun captureComposed(draw: DrawNode,bounds: RectF32,blend: BlendPlan): MaterialSourceConstructionV4 {
            require(draw.origin in setOf(DrawOrigin.RECT,DrawOrigin.PATH) && draw.paint?.style == PaintStyleNode.FILL &&
                draw.resource == null && draw.operationBlendMode == null) { W5gPlanDiagnostics.Unpromoted }
            require(colorFilterEffectsMatchPaint(draw)) { W5gPlanDiagnostics.Schema }
            val nodes = mutableListOf<ComposedMetadata.Node>()
            val mappings = mutableListOf<ComposedBindingLayoutV1.UniformMapping>()
            val completed = java.util.IdentityHashMap<MaterialNode,MaterialEvaluationRefV5>()
            val active = java.util.IdentityHashMap<MaterialNode,Unit>()
            var owners = 0
            var cursor = 0L
            var occurrences = 0
            val limits = org.graphiks.kanvas.render.ir.GraphLimits()
            // Validate original occurrences separately from immutable metadata reuse
            // and from the two synthesized paint nodes, which are not public input.
            fun validate(node: MaterialNode,depth: Int) {
                require(depth <= limits.maxDepth && ++occurrences <= limits.maxNodes && active.put(node,Unit) == null) { W5gPlanDiagnostics.Schema }
                when (node) {
                    is MaterialNode.Blend -> { validate(node.dst,depth+1); validate(node.src,depth+1) }
                    is MaterialNode.Opacity -> validate(node.material,depth+1)
                    is MaterialNode.WithColorFilter -> validate(node.material,depth+1)
                    is MaterialNode.WithWorkingColorSpace -> validate(node.material,depth+1)
                    else -> Unit
                }
                active.remove(node)
            }
            validate(draw.material,1)
            fun visit(node: MaterialNode): MaterialEvaluationRefV5 {
                require(active.put(node,Unit) == null) { W5gPlanDiagnostics.Schema }
                completed[node]?.let { active.remove(node); return it }
                val owner = owners++
                val filter = (node as? MaterialNode.WithColorFilter)?.let { compileFilter(it.filter) }
                val bytes = when(node) {
                    MaterialNode.Transparent, is MaterialNode.Solid, is MaterialNode.Opacity -> 16L
                    is MaterialNode.WithColorFilter -> requireNotNull(filter).dynamicByteCountI64
                    is MaterialNode.Blend, is MaterialNode.WithWorkingColorSpace -> 0L
                    else -> {
                        validatePendingGradient(node)
                        throw IllegalArgumentException(W5gPlanDiagnostics.Unpromoted)
                    }
                }
                if (node is MaterialNode.Opacity) require(node.alpha.isFinite() && node.alpha in 0f..1f) { W5aPlanDiagnostics.InvalidOpacity }
                val offset = Math.toIntExact(cursor)
                cursor = Math.addExact(cursor,bytes)
                require(cursor <= Int.MAX_VALUE.toLong() && cursor <= UInt.MAX_VALUE.toLong()) { W5gPlanDiagnostics.Uniform }
                if (bytes > 0) mappings += ComposedBindingLayoutV1.UniformMapping(owner,0,offset,Math.toIntExact(bytes),16)
                val children = when(node) {
                    is MaterialNode.Blend -> listOf(visit(node.dst),visit(node.src))
                    is MaterialNode.Opacity -> listOf(visit(node.material))
                    is MaterialNode.WithColorFilter -> listOf(visit(node.material))
                    is MaterialNode.WithWorkingColorSpace -> listOf(visit(node.material))
                    else -> emptyList()
                }
                nodes += ComposedMetadata.Node(owner,node,children,offset,filter)
                active.remove(node)
                return MaterialEvaluationRefV5(nodes.lastIndex).also { completed[node] = it }
            }
            val alpha = draw.paint?.takeIf { it.shader != null }?.color?.alphaNormalized ?: 1f
            var root: MaterialNode = MaterialNode.Opacity(draw.material,alpha)
            draw.paint?.colorFilter?.let { root = MaterialNode.WithColorFilter(root,it) }
            // Prefix owner assignment includes the actual outer paint operations;
            // postorder evaluation refs remain explicit and independently checked.
            visit(root)
            val metadata = ComposedMetadata(nodes,ComposedBindingLayoutV1(mappings,cursor))
            return MaterialSourceConstructionV4(draw.material,draw.paint,SourceCoordinatesV4.None,bounds,blend,
                "captured-composed-v5:${java.util.UUID.randomUUID()}",null,null,composed=metadata)
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
        fun captureImage(metadata: ImageMetadata,bounds: RectF32,blend: BlendPlan): MaterialSourceConstructionV4 {
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
                SourceCoordinatesV4.V3(metadata.coordinates),bounds,blend,identity,null,null,metadata)
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
            blend: BlendPlan,imageMaskChild: Boolean = false): SourceConstructionResultV4<MaterialSourceConstructionV4> = try {
            require(bounds.isFinite() && bounds.isSorted()) { W5fPlanDiagnostics.Schema }
            if (containsComposed(draw.material)) {
                require(!imageMaskChild) { W5gPlanDiagnostics.Unpromoted }
                SourceConstructionResultV4.Built(captureComposed(draw,bounds,blend))
            } else {
            require(imageMaskChild || draw.origin in setOf(DrawOrigin.RECT, DrawOrigin.RRECT, DrawOrigin.PATH) &&
                draw.resource == null && draw.operationBlendMode == null) { W5fPlanDiagnostics.Unpromoted }
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
                coordinates, bounds, blend, identity, null, metadata))
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
