package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.math.color.ColorF32

/** The recorded effect entry is an authenticated mirror, not a second filter application. */
internal fun colorFilterEffectsMatchPaint(draw: DrawNode): Boolean {
    val filter = draw.paint?.colorFilter ?: return draw.effects is EffectStack.Empty
    val mirror = draw.effects as? EffectStack.Entries ?: return false
    return mirror.effectCount == 1 && mirror.effectAt(0).canonicalId == filter.canonicalId
}

/** Normalizes admitted W5 sources once, before a graph is published Ready. */
public object EffectiveMaterialPlanner {
    /** Original IMAGE/Rect/Path source authority, independent of its W4 construction projection. */
    internal fun planW5eImageSource(draw: DrawNode, deviceBoundsI32: org.graphiks.math.geometry.RectI32,
        maxCellsI64: Long = 9L, constructionEntry: ImageConstructionEntryV1? = null): Result {
        return when (val described = describeW5eImageSource(draw,deviceBoundsI32,maxCellsI64,constructionEntry,false)) {
            is SourceConstructionResultV4.Refused -> Result.Refused(described.diagnosticCode)
            is SourceConstructionResultV4.Built -> try {
                val image = described.value
                image.bind(image.child?.resolvedSource,Math.addExact(image.upload.byteCountI64,image.layout.imageUniformByteCountI64))
            } catch (failure: IllegalArgumentException) { Result.Refused(failure.message?.takeIf {
                it.startsWith("unsupported.") || it.startsWith("resource.") || it.startsWith("invalid.")
            } ?: W5eImagePlanDiagnostics.InvalidContract) }
        }
    }

    internal fun describeW5eImageSource(draw: DrawNode,deviceBoundsI32: org.graphiks.math.geometry.RectI32,
        maxCellsI64: Long,constructionEntry: ImageConstructionEntryV1?,deferred: Boolean,
    ): SourceConstructionResultV4<MaterialSourceConstructionV4.ImageMetadata> = try {
            val direct = draw.origin in setOf(org.graphiks.kanvas.render.ir.DrawOrigin.IMAGE, org.graphiks.kanvas.render.ir.DrawOrigin.IMAGE_NINE,
                org.graphiks.kanvas.render.ir.DrawOrigin.IMAGE_LATTICE, org.graphiks.kanvas.render.ir.DrawOrigin.ATLAS)
            val patch = draw.geometry as? org.graphiks.kanvas.render.ir.GeometryNode.ImagePatch
            val nine = draw.geometry as? org.graphiks.kanvas.render.ir.GeometryNode.ImageNine
            val lattice = draw.geometry as? org.graphiks.kanvas.render.ir.GeometryNode.ImageLattice
            val atlas = draw.geometry as? org.graphiks.kanvas.render.ir.GeometryNode.Atlas
            require(constructionEntry == null || constructionEntry.originalDraw === draw) { W5eImagePlanDiagnostics.InvalidContract }
            val physicalCell = constructionEntry?.cell
            val atlasColor = constructionEntry?.atlasEntryColor
            val atlasMode = atlasColor?.let { requireNotNull(draw.operationBlendMode) }
            require(if (direct) patch != null && draw.origin == org.graphiks.kanvas.render.ir.DrawOrigin.IMAGE ||
                nine != null && draw.origin == org.graphiks.kanvas.render.ir.DrawOrigin.IMAGE_NINE ||
                lattice != null && draw.origin == org.graphiks.kanvas.render.ir.DrawOrigin.IMAGE_LATTICE ||
                atlas != null && draw.origin == org.graphiks.kanvas.render.ir.DrawOrigin.ATLAS
                else draw.origin == org.graphiks.kanvas.render.ir.DrawOrigin.RECT &&
                draw.geometry is org.graphiks.kanvas.render.ir.GeometryNode.Rect ||
                draw.origin == org.graphiks.kanvas.render.ir.DrawOrigin.PATH && draw.geometry is org.graphiks.kanvas.render.ir.GeometryNode.Path) {
                W5eImagePlanDiagnostics.UnsupportedSlice
            }
            require(draw.paint?.style?.let { it == org.graphiks.kanvas.render.ir.PaintStyleNode.FILL } != false &&
                (if (deferred) colorFilterEffectsMatchPaint(draw) else draw.effects is EffectStack.Empty) &&
                (draw.operationBlendMode == null || atlas != null)) { W5eImagePlanDiagnostics.UnsupportedSlice }
            var source = draw.material
            val matricesF32 = mutableListOf<org.graphiks.math.matrix.Matrix3x3F32>()
            val wrappers = mutableListOf<SourceUnaryMetadataV4>()
            var opacityF32 = 1f
            var countI32 = 0
            while (source is MaterialNode.WithLocalMatrix || source is MaterialNode.Opacity ||
                deferred && (source is MaterialNode.WithColorFilter || source is MaterialNode.WithWorkingColorSpace)) {
                require(++countI32 <= 64) { W5eImagePlanDiagnostics.UnsupportedSlice }
                when (val node = source) {
                    is MaterialNode.WithLocalMatrix -> { matricesF32 += node.matrix.copy(); source = node.material }
                    is MaterialNode.Opacity -> {
                        require(node.alpha.isFinite() && node.alpha in 0f..1f) { W5aPlanDiagnostics.InvalidOpacity }
                        if (deferred) {
                            if (node.alpha != 1f) wrappers += SourceUnaryMetadataV4.Opacity(node.alpha)
                        } else opacityF32 *= node.alpha
                        source = node.material
                    }
                    is MaterialNode.WithColorFilter -> {
                        wrappers += SourceUnaryMetadataV4.Filter(MaterialSourceConstructionV4.compileFilter(node.filter))
                        source = node.material
                    }
                    is MaterialNode.WithWorkingColorSpace -> source = node.material
                }
            }
            val sample = source as? MaterialNode.ImageSample
                ?: throw IllegalArgumentException(W5eImagePlanDiagnostics.UnsupportedSlice)
            require(atlas == null || sample.sampling == org.graphiks.kanvas.render.ir.ImageSampling.Nearest) { W5eImagePlanDiagnostics.UnsupportedSlice }
            require(nine == null || nine.sampling == org.graphiks.kanvas.render.ir.ImageSampling.Nearest &&
                sample.sampling == org.graphiks.kanvas.render.ir.ImageSampling.Nearest) { W5eImagePlanDiagnostics.UnsupportedSlice }
            val sampling = when (val requestedSampling = sample.sampling) {
                org.graphiks.kanvas.render.ir.ImageSampling.Nearest -> ImageSamplingPlanV1.Nearest
                org.graphiks.kanvas.render.ir.ImageSampling.Linear -> ImageSamplingPlanV1.Linear
                is org.graphiks.kanvas.render.ir.ImageSampling.Cubic -> ImageSamplingPlanV1.Cubic(requestedSampling.b.toRawBits(), requestedSampling.c.toRawBits())
            }
            if (sampling is ImageSamplingPlanV1.Cubic && (!sampling.bF32.isFinite() || sampling.bF32 !in 0f..1f ||
                    !sampling.cF32.isFinite() || sampling.cF32 !in 0f..1f))
                throw IllegalArgumentException(W5eImagePlanDiagnostics.CubicParameters)
            val tileModes = if (direct) ImageTileModePlanV1.ClampClamp else ImageTileModePlanV1(
                ImageTileAxisModePlanV1.valueOf(sample.tileModeX.name), ImageTileAxisModePlanV1.valueOf(sample.tileModeY.name))
            val pixels = sample.image as? org.graphiks.kanvas.render.ir.ImageResourceSnapshot.Pixels
                ?: throw IllegalArgumentException(W5eImagePlanDiagnostics.ExternalResource)
            require(!direct || draw.resource?.canonicalId == pixels.canonicalId && (patch?.image ?: nine?.image ?: lattice?.image ?: atlas?.image)?.id?.value == pixels.sourceId) {
                W5eImagePlanDiagnostics.InvalidContract
            }
            require(direct || draw.resource == null && draw.paint?.shader?.canonicalId == draw.material.canonicalId) {
                W5eImagePlanDiagnostics.InvalidContract
            }
            val channel = when (pixels.pixelFormat) {
                org.graphiks.kanvas.render.ir.ImagePixelFormat.RGBA_8888,
                org.graphiks.kanvas.render.ir.ImagePixelFormat.SRGBA_8888 -> ImageChannelOrderV1.RGBA
                org.graphiks.kanvas.render.ir.ImagePixelFormat.BGRA_8888 -> ImageChannelOrderV1.BGRA
                org.graphiks.kanvas.render.ir.ImagePixelFormat.ALPHA_8 -> ImageChannelOrderV1.ALPHA
                else -> throw IllegalArgumentException(W5eImagePlanDiagnostics.Format)
            }
            val sourceColor = if (channel == ImageChannelOrderV1.ALPHA)
                ImageColorAlphaPlanV1(channel, pixels.alphaType, ImageTransferPlanV1.NONE, ImageGamutPlanV1.NONE)
            else {
                require(pixels.pixelFormat != org.graphiks.kanvas.render.ir.ImagePixelFormat.SRGBA_8888 ||
                    pixels.colorSpace == org.graphiks.kanvas.color.ColorSpace.SRGB) { W5eImagePlanDiagnostics.ColorSpace }
                when (pixels.colorSpace) {
                    org.graphiks.kanvas.color.ColorSpace.SRGB -> ImageColorAlphaPlanV1(channel, pixels.alphaType, ImageTransferPlanV1.SRGB, ImageGamutPlanV1.SRGB)
                    org.graphiks.kanvas.color.ColorSpace.DISPLAY_P3 -> ImageColorAlphaPlanV1(channel, pixels.alphaType, ImageTransferPlanV1.SRGB, ImageGamutPlanV1.DISPLAY_P3)
                    org.graphiks.kanvas.color.ColorSpace.LINEAR_SRGB -> ImageColorAlphaPlanV1(channel, pixels.alphaType, ImageTransferPlanV1.LINEAR, ImageGamutPlanV1.SRGB)
                    else -> throw IllegalArgumentException(W5eImagePlanDiagnostics.ColorSpace)
                }
            }
            val color = sourceColor.copy(premultiplication = pixels.premultiplication)
            val boundsF32 = org.graphiks.math.geometry.RectF32.ofLTRB(deviceBoundsI32.left.toFloat(), deviceBoundsI32.top.toFloat(),
                deviceBoundsI32.right.toFloat(), deviceBoundsI32.bottom.toFloat())
            val baseChild = if (channel != ImageChannelOrderV1.ALPHA) null else if (direct) {
                val childDraw = draw.copy(
                    transform = if (atlas != null) requireNotNull(constructionEntry).copyTransformF32() else draw.transform,
                    paint = if (atlasColor == null) draw.paint else draw.paint?.let { it.copy(color = it.color.withAlpha(255)) })
                if (deferred) when (val captured = normalizeSourcesV4(childDraw,
                    PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL.blendTargetClampV1(),deviceBoundsI32,imageMaskChild=true)) {
                    is SourceNormalizationV4.Source -> captured.captured
                    is SourceNormalizationV4.Refused -> throw IllegalArgumentException(captured.diagnosticCode)
                    SourceNormalizationV4.NoOp -> error(W5eImagePlanDiagnostics.InvalidContract)
                } else when (val planned = planImageMaskSource(childDraw, deviceBoundsI32)) {
                    is Result.Ready -> MaterialSourceConstructionV4.retain(childDraw,planned,boundsF32)
                    is Result.Refused -> throw IllegalArgumentException(planned.diagnosticCode)
                }
            } else {
                // An A8 paint shader masks the paint's solid color. The image root is never its own child.
                val colorF32 = requireNotNull(draw.paint).color.let {
                    ColorF32.of(it.redNormalized, it.greenNormalized, it.blueNormalized, it.alphaNormalized)
                }
                MaterialSourceConstructionV4.retain(draw,Result.Ready(MaterialPlanTable.of(listOf(
                    MaterialPlanEntry(MaterialProgramPlan.SolidLinearPremulV1,
                        MaterialBindingPlan.SolidRgbaF32V1.of(colorF32)))),MaterialPlanRef(0)),boundsF32)
            }
            val child = if (baseChild == null || opacityF32 == 1f || atlasColor != null) baseChild else {
                val resolved = requireNotNull(baseChild.resolvedSource)
                val childEntries = resolved.table.entries() + MaterialPlanEntry(
                    MaterialProgramPlan.OpacityV1(resolved.table.entry(resolved.root).program),
                    MaterialBindingPlan.OpacityF32V1.of(opacityF32))
                MaterialSourceConstructionV4.retain(draw,Result.Ready(MaterialPlanTable.of(childEntries),
                    MaterialPlanRef(childEntries.lastIndex)),boundsF32)
            }
            val latticeCells = (if (physicalCell is ImageCellPlanV1.SolidV1) listOf(
                ImageCellPlanV1.SolidV1(physicalCell.copyDestinationF32(), physicalCell.color, List(4) { true }))
                else if (physicalCell != null) null else lattice?.let { ImageCellDecomposerV1.lattice(pixels.width, pixels.height, it, maxCellsI64) })
                ?.filter { it.copyDestinationF32().let { bounds -> bounds.left != bounds.right && bounds.top != bounds.bottom } }
            val latticeKinds = latticeCells?.joinToString("") { when (it) {
                is ImageCellPlanV1.Sampled -> "s"
                is ImageCellPlanV1.SolidV1 -> "c"
                is ImageCellPlanV1.OmittedV1 -> "o"
            } }
            val upload = ImageUploadPlanV1.seal(pixels)
            val cells = latticeCells ?: nine?.let { ImageCellDecomposerV1.nine(upload.widthI32, upload.heightI32, it.copyCenter(), it.copyDestination()) }
            val coordinates = if (physicalCell != null) ImageCoordinatePlanV1.seal(requireNotNull(constructionEntry).copyTransformF32(),
                (physicalCell as? ImageCellPlanV1.Sampled)?.copySourceF32() ?:
                    org.graphiks.math.geometry.RectF32.ofLTRB(0f, 0f, upload.widthI32.toFloat(), upload.heightI32.toFloat()), physicalCell.copyDestinationF32())
                else if (nine != null || lattice != null) ImageCoordinatePlanV1.seal(draw.transform,
                org.graphiks.math.geometry.RectF32.ofLTRB(0f, 0f, upload.widthI32.toFloat(), upload.heightI32.toFloat()), nine?.copyDestination() ?: requireNotNull(lattice).copyDestination())
                else if (direct) ImageCoordinatePlanV1.seal(draw.transform, requireNotNull(patch).copySource(), patch.copyDestination())
                else ImageCoordinatePlanV1.sealShader(draw.transform, matricesF32)
            val originalPaintAlpha = opacityF32 * (draw.paint?.color?.alphaNormalized ?: 1f)
            val paintAlphaF32 = if ((!deferred && child == null) || atlasColor != null) originalPaintAlpha else 1f
            val ordered = wrappers.asReversed().toMutableList()
            if (deferred && child == null && atlasColor == null && originalPaintAlpha != 1f)
                ordered += SourceUnaryMetadataV4.Opacity(originalPaintAlpha)
            if (deferred) draw.paint?.colorFilter?.let {
                ordered += SourceUnaryMetadataV4.Filter(MaterialSourceConstructionV4.compileFilter(it))
            }
            // RGBA sampled and fixed-color cells share the same ordered outer
            // paint-opacity wrapper. A8 fixed-color cells bypass their mask child
            // and therefore still need the original paint alpha in the header.
            val latticePaintAlphaF32 = if (deferred && child == null && atlasColor == null) 1f else originalPaintAlpha
            SourceConstructionResultV4.Built(MaterialSourceConstructionV4.ImageMetadata(draw,upload,coordinates,
                color,sampling,tileModes,cells,latticeKinds,paintAlphaF32,latticePaintAlphaF32,atlasColor,atlasMode,
                child,deferred,ordered,boundsF32))
        } catch (failure: IllegalArgumentException) {
            sourceConstructionRefusalV4(failure.message?.takeIf { it.startsWith("unsupported.") || it.startsWith("resource.") || it.startsWith("invalid.") }
                ?: W5eImagePlanDiagnostics.InvalidContract)
        }
    public sealed interface Result {
        public data class Ready(
            public val table: MaterialPlanTable,
            public val root: MaterialPlanRef,
            public val blend: BlendPlan = BlendPlan.LegacySrcOverV1,
        ) : Result {
            /** Exact source/coordinate family, issued here rather than guessed by consumers. */
            public val materialAuthority: PlanDrawMaterialAuthority by lazy {
                var leaf = root
                while (table.entry(leaf).bindings is MaterialBindingPlan.OpacityF32V1)
                    leaf = MaterialPlanRef(leaf.indexI32 - 1)
                when (val binding = table.entry(leaf).bindings) {
                    is GradientInterpolationBindingV4 -> PlanDrawMaterialAuthority.MaterialV4(root,binding.sourceProof.coordinates)
                    is ColorFilterBindingV4 -> PlanDrawMaterialAuthority.MaterialV4(root,binding.numericAuthority.outputSourceProof.coordinates)
                    is ImageSampleV3 -> PlanDrawMaterialAuthority.MaterialV3(root,binding.execution.coordinates)
                    is MaterialBindingPlan.GradientV2 -> PlanDrawMaterialAuthority.MaterialV2(root,binding.numericAuthority.coordinates)
                    is MaterialBindingPlan.GradientV1 -> PlanDrawMaterialAuthority.MaterialV1(root,binding.numericAuthority.coordinates)
                    else -> PlanDrawMaterialAuthority.MaterialV1(root)
                }
            }
        }
        public data class Refused(public val diagnosticCode: String) : Result
    }

    /** Callers without a sealed target fact intentionally retain no clamp capability. */
    public fun plan(draw: DrawNode): Result = plan(draw, BlendTargetClampV1.Unavailable)

    internal sealed interface Normalization {
        data object NoOp : Normalization
        data class Source(val table: MaterialPlanTable, val root: MaterialPlanRef, val blend: BlendPlan) : Normalization
        data class Refused(val diagnosticCode: String) : Normalization
    }

    internal sealed interface SourceNormalizationV4 {
        data object NoOp : SourceNormalizationV4
        data class Source(val captured: MaterialSourceConstructionV4) : SourceNormalizationV4
        data class Refused(val diagnosticCode: String) : SourceNormalizationV4
    }

    /** Capture pending gradient data, or retain the original resolved normalization exactly once. */
    internal fun normalizeSourcesV4(draw: DrawNode,targetClamp: BlendTargetClampV1,
        bounds: org.graphiks.math.geometry.RectI32,coverage: CoveragePlan = CoveragePlan.FullOrScissor,
        sample: SamplePlan = SamplePlan.SingleSample,
        legacyGradientBoundsI32: org.graphiks.math.geometry.RectI32? = bounds,
        imageMaskChild: Boolean = false): SourceNormalizationV4 {
        val coordinateNodes = mutableListOf<CoordinateNodeV2>()
        var leaf = if (imageMaskChild) imageMaskMaterial(draw) else draw.material
        var filtered = !imageMaskChild && draw.paint?.colorFilter != null
        var depth = 0
        var workingDomain: org.graphiks.kanvas.render.ir.ColorInterpolation? = null
        while (true) {
            if (++depth > 64) return SourceNormalizationV4.Refused(W5fPlanDiagnostics.Schema)
            leaf = when (val node = leaf) {
                is MaterialNode.WithWorkingColorSpace -> {
                    if (workingDomain == null) workingDomain = node.interpolation
                    node.material
                }
                is MaterialNode.Opacity -> node.material
                is MaterialNode.WithColorFilter -> { filtered = true; node.material }
                is MaterialNode.WithLocalMatrix -> { coordinateNodes += CoordinateNodeV2.LocalMatrix(node.matrix); node.material }
                is MaterialNode.CoordClamp -> { coordinateNodes += CoordinateNodeV2.CoordClamp(node.copySubset()); node.material }
                else -> break
            }
        }
        val leafDomain = when (val source = leaf) {
            is MaterialNode.LinearGradient -> source.interpolation
            is MaterialNode.RadialGradient -> source.interpolation
            is MaterialNode.SweepGradient -> source.interpolation
            is MaterialNode.ConicalGradient -> source.interpolation
            else -> null
        }
        val domain = if (leafDomain == null) null else workingDomain ?: leafDomain
        val actualBounds = org.graphiks.math.geometry.RectF32.ofLTRB(bounds.left.toFloat(),bounds.top.toFloat(),
            bounds.right.toFloat(),bounds.bottom.toFloat())
        if (domain == null || domain == org.graphiks.kanvas.render.ir.ColorInterpolation.SRGB && workingDomain == null && !imageMaskChild) {
            return when (val original = normalize(draw,targetClamp,true,coverage,sample,elideNoOp=!imageMaskChild,
                gradientDeviceBoundsI32=legacyGradientBoundsI32,imageMaskChild=imageMaskChild)) {
                Normalization.NoOp -> SourceNormalizationV4.NoOp
                is Normalization.Refused -> SourceNormalizationV4.Refused(original.diagnosticCode)
                is Normalization.Source -> SourceNormalizationV4.Source(MaterialSourceConstructionV4.retain(draw,
                    Result.Ready(original.table,original.root,original.blend),actualBounds))
            }
        }
        if (!imageMaskChild && filtered && (!colorFilterEffectsMatchPaint(draw) || draw.resource != null || draw.operationBlendMode != null ||
                draw.origin !in setOf(org.graphiks.kanvas.render.ir.DrawOrigin.RECT,org.graphiks.kanvas.render.ir.DrawOrigin.PATH) ||
                draw.paint?.style != org.graphiks.kanvas.render.ir.PaintStyleNode.FILL))
            return SourceNormalizationV4.Refused(if (!colorFilterEffectsMatchPaint(draw)) W5fPlanDiagnostics.Schema else W5fPlanDiagnostics.Unpromoted)
        val blend = FinalBlendPlanner.plan(draw.blend,coverage,sample,targetClamp,
            if (coverage == CoveragePlan.AnalyticScalarAA) BlendCoverageApplicationV1.SourceMultiplication
            else BlendCoverageApplicationV1.DestinationInterpolation)
            ?: return SourceNormalizationV4.Refused(W5aPlanDiagnostics.UnsupportedDrawState)
        if (blend == BlendPlan.NoOpV1 && !imageMaskChild) return SourceNormalizationV4.NoOp
        if (!colorFilterEffectsMatchPaint(draw) || !imageMaskChild && (draw.resource != null || draw.operationBlendMode != null))
            return SourceNormalizationV4.Refused(W5aPlanDiagnostics.UnsupportedDrawState)
        // Original one-stop normalization is a Solid, before coordinate evaluation
        // or domain conversion. Conical must retain its validity mask instead.
        val collapses = when (val source = leaf) {
            is MaterialNode.LinearGradient -> source.stops().size == 1
            is MaterialNode.RadialGradient -> source.stops().size == 1
            is MaterialNode.SweepGradient -> source.stops().size == 1
            else -> false
        }
        val coordinates = if (collapses) SourceCoordinatesV4.None else when (
            val built = MaterialCoordinatePlanV2.fromCtmAndNodes(draw.transform,coordinateNodes)) {
            is MaterialCoordinatePlanV2.Build.Ready -> SourceCoordinatesV4.V2(built.coordinates)
            is MaterialCoordinatePlanV2.Build.Refused -> return SourceNormalizationV4.Refused(built.code)
        }
        return when (val captured = MaterialSourceConstructionV4.capture(draw,coordinates,actualBounds,blend,imageMaskChild)) {
            is SourceConstructionResultV4.Built -> if (!collapses) SourceNormalizationV4.Source(captured.value)
                else when (val solid = collapseOriginalStopV4(captured.value)) {
                    is Result.Ready -> SourceNormalizationV4.Source(MaterialSourceConstructionV4.retain(draw,solid,actualBounds))
                    is Result.Refused -> SourceNormalizationV4.Refused(solid.diagnosticCode)
                }
            is SourceConstructionResultV4.Refused -> SourceNormalizationV4.Refused(captured.diagnosticCode)
        }
    }

    private fun collapseOriginalStopV4(source: MaterialSourceConstructionV4): Result {
        val metadata = requireNotNull(source.gradient)
        val color = requireNotNull(metadata.stops.solidColor)
        require(metadata.family != GradientFamilyV2.CONICAL && source.coordinates == SourceCoordinatesV4.None)
        var table = MaterialPlanTable.of(listOf(MaterialPlanEntry(MaterialProgramPlan.SolidLinearPremulV1,
            MaterialBindingPlan.SolidRgbaF32V1.of(ColorF32.of(color.redNormalized,color.greenNormalized,
                color.blueNormalized,color.alphaNormalized)))))
        for (wrapper in metadata.wrappers) {
            val root = MaterialPlanRef(table.sizeI32-1)
            val child = table.entry(root).program
            val entry = when (wrapper) {
                is SourceUnaryMetadataV4.Opacity -> MaterialPlanEntry(MaterialProgramPlan.OpacityV1(child),
                    MaterialBindingPlan.OpacityF32V1.of(wrapper.alphaF32))
                is SourceUnaryMetadataV4.Filter -> {
                    val proof = when (val sealed = ColorSourceProofCompilerV1.seal(table,root,SourceCoordinatesV4.None,source.deviceBoundsF32)) {
                        is ColorSourceProofResultV1.Ready -> sealed.source
                        is ColorSourceProofResultV1.Refused -> return Result.Refused(sealed.diagnosticCode)
                    }
                    val numeric = ColorNumericAuthorityV1.seal(wrapper.execution,proof)
                        ?: return Result.Refused(W5fPlanDiagnostics.NumericDomainUnbounded)
                    MaterialPlanEntry(ColorFilteredProgramV4(child,wrapper.execution.structuralIdentity),
                        ColorFilterBindingV4.seal(wrapper.execution,proof,numeric))
                }
            }
            table = MaterialPlanTable.of(table.entries()+entry)
        }
        return Result.Ready(table,MaterialPlanRef(table.sizeI32-1),source.blend)
    }

    /** Compatibility boundary: existing owners cannot promote destination-read draws. */
    public fun plan(draw: DrawNode, targetClamp: BlendTargetClampV1): Result =
        when (val source = normalize(draw, targetClamp)) {
            Normalization.NoOp -> error("Compatibility normalization cannot elide draws")
            is Normalization.Refused -> Result.Refused(source.diagnosticCode)
            is Normalization.Source -> if (source.blend is BlendPlan.DestinationReadV1) {
                Result.Refused("unsupported.w5b.destination-read.task-2")
            } else Result.Ready(source.table, source.root, source.blend)
        }

    /**
     * Normalizes an admitted W5 source and final blend together. Destination-read candidates and
     * `DST` remain plans so prepared-family bridges can carry them to the frame sealer unchanged.
     */
    public fun planW5b(
        draw: DrawNode,
        targetClamp: BlendTargetClampV1,
        coverage: CoveragePlan = CoveragePlan.FullOrScissor,
        sample: SamplePlan = SamplePlan.SingleSample,
        gradientDeviceBoundsI32: org.graphiks.math.geometry.RectI32? = null,
    ): Result = when (
        val source = normalize(
            draw = draw,
            targetClamp = targetClamp,
            allowDestinationCandidate = true,
            coverage = coverage,
            sample = sample,
            elideNoOp = false,
            gradientDeviceBoundsI32 = gradientDeviceBoundsI32,
        )
    ) {
        Normalization.NoOp -> error("W5b source normalization must retain NoOp authority")
        is Normalization.Refused -> Result.Refused(source.diagnosticCode)
        is Normalization.Source -> Result.Ready(source.table, source.root, source.blend)
    }

    /** Paint child of an A8 image: original geometry/CTM remain the coordinate authority. */
    internal fun planImageMaskSource(draw: DrawNode, deviceBoundsI32: org.graphiks.math.geometry.RectI32): Result {
        imageMaskMaterial(draw)
        return when (val result = normalize(draw, PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL.blendTargetClampV1(),
            allowDestinationCandidate = true, elideNoOp = false, gradientDeviceBoundsI32 = deviceBoundsI32, imageMaskChild = true)) {
            is Normalization.Source -> Result.Ready(result.table, result.root, result.blend)
            is Normalization.Refused -> Result.Refused(result.diagnosticCode)
            Normalization.NoOp -> error("Image child cannot elide its source")
        }
    }

    internal fun imageMaskMaterial(draw: DrawNode): MaterialNode {
        require(draw.origin == org.graphiks.kanvas.render.ir.DrawOrigin.IMAGE &&
            draw.geometry is org.graphiks.kanvas.render.ir.GeometryNode.ImagePatch ||
            draw.origin == org.graphiks.kanvas.render.ir.DrawOrigin.IMAGE_NINE && draw.geometry is org.graphiks.kanvas.render.ir.GeometryNode.ImageNine ||
            draw.origin == org.graphiks.kanvas.render.ir.DrawOrigin.IMAGE_LATTICE && draw.geometry is org.graphiks.kanvas.render.ir.GeometryNode.ImageLattice ||
            draw.origin == org.graphiks.kanvas.render.ir.DrawOrigin.ATLAS && draw.geometry is org.graphiks.kanvas.render.ir.GeometryNode.Atlas)
        return draw.paint?.shader ?: draw.paint?.let { MaterialNode.Solid(it.color) }
            ?: MaterialNode.Solid(org.graphiks.math.color.ColorARGB.Black)
    }

    private fun normalizeOrderedColor(draw: DrawNode, targetClamp: BlendTargetClampV1,
        allowDestinationCandidate: Boolean, coverage: CoveragePlan, sample: SamplePlan,
        elideNoOp: Boolean, deviceBoundsI32: org.graphiks.math.geometry.RectI32?, imageMaskChild: Boolean): Normalization {
        val filter = draw.paint?.colorFilter?.takeUnless { imageMaskChild }
        if (!colorFilterEffectsMatchPaint(draw)) return Normalization.Refused(
            if (filter != null) W5fPlanDiagnostics.Schema else W5aPlanDiagnostics.UnsupportedDrawState)
        if (!imageMaskChild && (draw.resource != null || draw.operationBlendMode != null ||
            draw.origin !in setOf(org.graphiks.kanvas.render.ir.DrawOrigin.RECT, org.graphiks.kanvas.render.ir.DrawOrigin.PATH) ||
            draw.paint?.style != org.graphiks.kanvas.render.ir.PaintStyleNode.FILL))
            return Normalization.Refused(W5fPlanDiagnostics.Unpromoted)
        val bounds = if (imageMaskChild) deviceBoundsI32?.let {
            org.graphiks.math.geometry.RectF32.ofLTRB(it.left.toFloat(),it.top.toFloat(),it.right.toFloat(),it.bottom.toFloat())
        } ?: return Normalization.Refused(W5fPlanDiagnostics.NumericDomainUnbounded)
        else when (val geometry = draw.geometry) {
            is org.graphiks.kanvas.render.ir.GeometryNode.Rect -> {
                val local = geometry.copyBounds()
                val corners = listOf(org.graphiks.math.geometry.Point2F32(local.left,local.top),
                    org.graphiks.math.geometry.Point2F32(local.right,local.top),
                    org.graphiks.math.geometry.Point2F32(local.left,local.bottom),
                    org.graphiks.math.geometry.Point2F32(local.right,local.bottom)).map(draw.transform::transform)
                org.graphiks.math.geometry.RectF32.ofLTRB(kotlin.math.floor(corners.minOf { it.x }),
                    kotlin.math.floor(corners.minOf { it.y }),kotlin.math.ceil(corners.maxOf { it.x }),kotlin.math.ceil(corners.maxOf { it.y }))
            }
            is org.graphiks.kanvas.render.ir.GeometryNode.Path -> deviceBoundsI32?.let {
                org.graphiks.math.geometry.RectF32.ofLTRB(it.left.toFloat(),it.top.toFloat(),it.right.toFloat(),it.bottom.toFloat())
            } ?: return Normalization.Refused(W5fPlanDiagnostics.NumericDomainUnbounded)
            else -> return Normalization.Refused(W5fPlanDiagnostics.Unpromoted)
        }
        val blend = FinalBlendPlanner.plan(draw.blend, coverage, sample, targetClamp,
            if (coverage == CoveragePlan.AnalyticScalarAA) BlendCoverageApplicationV1.SourceMultiplication
            else BlendCoverageApplicationV1.DestinationInterpolation)
            ?: return Normalization.Refused(W5aPlanDiagnostics.UnsupportedDrawState)
        if (blend is BlendPlan.DestinationReadV1 && !allowDestinationCandidate)
            return Normalization.Refused("unsupported.w5b.destination-read.task-2")
        if (allowDestinationCandidate && elideNoOp && blend == BlendPlan.NoOpV1) return Normalization.NoOp
        val wrappers = mutableListOf<MaterialNode>()
        var leaf = if (imageMaskChild) imageMaskMaterial(draw) else draw.material
        while (leaf is MaterialNode.Opacity || leaf is MaterialNode.WithColorFilter || leaf is MaterialNode.WithWorkingColorSpace) {
            if (wrappers.size >= 64) return Normalization.Refused(W5fPlanDiagnostics.Schema)
            wrappers += leaf
            leaf = when (leaf) { is MaterialNode.Opacity -> leaf.material
                is MaterialNode.WithColorFilter -> leaf.material
                is MaterialNode.WithWorkingColorSpace -> leaf.material }
        }
        val base = when (leaf) {
            MaterialNode.Transparent -> MaterialPlanEntry(MaterialProgramPlan.TransparentV1, MaterialBindingPlan.EmptyV1)
            is MaterialNode.Solid -> MaterialPlanEntry(MaterialProgramPlan.SolidLinearPremulV1,
                MaterialBindingPlan.SolidRgbaF32V1.of(ColorF32.of(leaf.color.redNormalized,
                    leaf.color.greenNormalized,leaf.color.blueNormalized,leaf.color.alphaNormalized)))
            else -> return Normalization.Refused(W5fPlanDiagnostics.Unpromoted)
        }
        var table = MaterialPlanTable.of(listOf(base))
        fun opacity(alphaF32: Float): String? {
            if (!alphaF32.isFinite() || alphaF32 !in 0f..1f) return W5aPlanDiagnostics.InvalidOpacity
            if (alphaF32 != 1f) table = MaterialPlanTable.of(table.entries() + MaterialPlanEntry(
                MaterialProgramPlan.OpacityV1(table.entry(MaterialPlanRef(table.sizeI32-1)).program),
                MaterialBindingPlan.OpacityF32V1.of(alphaF32)))
            return null
        }
        fun apply(filterNode: org.graphiks.kanvas.render.ir.ColorFilterNode): String? {
            val execution = when (val result = ColorFilterPlanCompilerV1.compile(filterNode)) {
                is ColorFilterCompileResultV1.Ready -> result.execution
                is ColorFilterCompileResultV1.Refused -> return result.diagnosticCode
            }
            val root = MaterialPlanRef(table.sizeI32-1)
            val source = when (val result = ColorSourceProofCompilerV1.seal(table,root,SourceCoordinatesV4.None,bounds)) {
                is ColorSourceProofResultV1.Ready -> result.source
                is ColorSourceProofResultV1.Refused -> return result.diagnosticCode
            }
            val numeric = ColorNumericAuthorityV1.seal(execution,source) ?: return W5fPlanDiagnostics.NumericDomainUnbounded
            table = MaterialPlanTable.of(table.entries() + MaterialPlanEntry(
                ColorFilteredProgramV4(table.entry(root).program,execution.structuralIdentity),
                ColorFilterBindingV4.seal(execution,source,numeric)))
            return null
        }
        for (wrapper in wrappers.asReversed()) {
            val refusal = when (wrapper) {
                is MaterialNode.Opacity -> opacity(wrapper.alpha)
                is MaterialNode.WithColorFilter -> apply(wrapper.filter)
                is MaterialNode.WithWorkingColorSpace -> null
                else -> error("Unary wrapper")
            }
            if (refusal != null) return Normalization.Refused(refusal)
        }
        opacity(draw.paint?.takeIf { it.shader != null }?.color?.alphaNormalized ?: 1f)?.let {
            return Normalization.Refused(it)
        }
        filter?.let { apply(it)?.let { code -> return Normalization.Refused(code) } }
        return Normalization.Source(table,MaterialPlanRef(table.sizeI32-1),blend)
    }

    internal fun normalize(draw: DrawNode, targetClamp: BlendTargetClampV1, allowDestinationCandidate: Boolean = false,
        coverage: CoveragePlan = CoveragePlan.FullOrScissor, sample: SamplePlan = SamplePlan.SingleSample,
        elideNoOp: Boolean = true,
        gradientDeviceBoundsI32: org.graphiks.math.geometry.RectI32? = null, imageMaskChild: Boolean = false): Normalization {
        val sourceMaterial = if (imageMaskChild) imageMaskMaterial(draw) else draw.material
        var filteredMaterial = sourceMaterial
        var filteredDepthI32 = 0
        while (filteredMaterial is MaterialNode.Opacity || filteredMaterial is MaterialNode.WithWorkingColorSpace) {
            if (++filteredDepthI32 > 64) return Normalization.Refused(W5fPlanDiagnostics.Schema)
            filteredMaterial = when (filteredMaterial) {
                is MaterialNode.Opacity -> filteredMaterial.material
                is MaterialNode.WithWorkingColorSpace -> filteredMaterial.material
            }
        }
        if (!imageMaskChild && draw.paint?.colorFilter != null || filteredMaterial is MaterialNode.WithColorFilter)
            return normalizeOrderedColor(draw, targetClamp, allowDestinationCandidate, coverage, sample,
                elideNoOp, gradientDeviceBoundsI32, imageMaskChild)
        val blend = FinalBlendPlanner.plan(draw.blend, coverage, sample, targetClamp,
            if (coverage == CoveragePlan.AnalyticScalarAA) BlendCoverageApplicationV1.SourceMultiplication
            else BlendCoverageApplicationV1.DestinationInterpolation)
            ?: return Normalization.Refused(W5aPlanDiagnostics.UnsupportedDrawState)
        if (blend is BlendPlan.DestinationReadV1 && !allowDestinationCandidate) {
            return Normalization.Refused("unsupported.w5b.destination-read.task-2")
        }
        if (allowDestinationCandidate && elideNoOp && blend == BlendPlan.NoOpV1) return Normalization.NoOp
        if ((if (imageMaskChild) !colorFilterEffectsMatchPaint(draw) else draw.effects !is EffectStack.Empty) ||
            draw.resource != null && !imageMaskChild || draw.operationBlendMode != null && !imageMaskChild) {
            return Normalization.Refused(W5aPlanDiagnostics.UnsupportedDrawState)
        }
        val addressing = GradientAddressingCaptureV2.capture(sourceMaterial)
        if (addressing is GradientAddressingCaptureV2.Ready &&
            (addressing.coordinateNodes.isNotEmpty() || when (val leaf = addressing.leaf) {
                is MaterialNode.LinearGradient -> leaf.tileMode
                is MaterialNode.RadialGradient -> leaf.tileMode
                is MaterialNode.SweepGradient -> leaf.tileMode
                is MaterialNode.ConicalGradient -> leaf.tileMode
                else -> null
            }?.let {
                it != org.graphiks.kanvas.render.ir.TileMode.CLAMP
            } == true))
            return normalizeW5d(draw, blend, addressing, gradientDeviceBoundsI32, imageMaskChild)
        var material = sourceMaterial
        val opacityInnerToOuter = mutableListOf<Float>()
        var visited = 0
        while (material is MaterialNode.Opacity || material is MaterialNode.WithWorkingColorSpace) {
            if (++visited > 64) return Normalization.Refused(W5fPlanDiagnostics.Schema)
            if (material is MaterialNode.WithWorkingColorSpace) { material = material.material; continue }
            material as MaterialNode.Opacity
            if (!material.alpha.isFinite() || material.alpha !in 0f..1f) {
                return Normalization.Refused(W5aPlanDiagnostics.InvalidOpacity)
            }
            opacityInnerToOuter += material.alpha
            material = material.material
        }
        val base = when (material) {
            MaterialNode.Transparent -> return Normalization.Source(
                MaterialPlanTable.of(listOf(MaterialPlanEntry(MaterialProgramPlan.TransparentV1, MaterialBindingPlan.EmptyV1))),
                MaterialPlanRef(0), blend,
            )
            is MaterialNode.Solid -> MaterialPlanEntry(
                MaterialProgramPlan.SolidLinearPremulV1,
                MaterialBindingPlan.SolidRgbaF32V1.of(ColorF32.of(
                    material.color.redNormalized,
                    material.color.greenNormalized,
                    material.color.blueNormalized,
                    material.color.alphaNormalized,
                )),
            )
            is MaterialNode.LinearGradient, is MaterialNode.RadialGradient, is MaterialNode.SweepGradient, is MaterialNode.ConicalGradient -> {
                val linear = material as? MaterialNode.LinearGradient
                val radial = material as? MaterialNode.RadialGradient
                val sweep = material as? MaterialNode.SweepGradient
                val conical = material as? MaterialNode.ConicalGradient
                val tileMode = linear?.tileMode ?: radial?.tileMode ?: sweep?.tileMode ?: requireNotNull(conical).tileMode
                val interpolation = linear?.interpolation ?: radial?.interpolation ?: sweep?.interpolation ?: requireNotNull(conical).interpolation
                if (conical != null && listOf(conical.start.x, conical.start.y, conical.end.x, conical.end.y,
                        conical.startRadius, conical.endRadius).any { !it.isFinite() })
                    return Normalization.Refused(W5cPlanDiagnostics.NonFinite)
                if (conical != null && (conical.startRadius < 0f || conical.endRadius < 0f))
                    return Normalization.Refused(W5cPlanDiagnostics.NegativeRadius)
                val conicalDegeneracy = conical?.let { ConicalGradientDegeneracyV1.of(it.start, it.startRadius, it.end, it.endRadius) }
                if (conicalDegeneracy != null && conicalDegeneracy.copyScalarsF32().any { !it.isFinite() })
                    return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                if (sweep != null && listOf(sweep.center.x, sweep.center.y, sweep.startAngle, sweep.endAngle).any { !it.isFinite() })
                    return Normalization.Refused(W5cPlanDiagnostics.NonFinite)
                val sweepDegeneracy = sweep?.let { SweepGradientDegeneracyV1.of(it.startAngle, it.endAngle) }
                if (sweepDegeneracy?.sweepOrderingInvalid == true) return Normalization.Refused(W5cPlanDiagnostics.SweepOrdering)
                if (sweepDegeneracy != null && !sweepDegeneracy.sweepSpanDegreesF32.isFinite())
                    return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                if (radial != null && (!radial.radius.isFinite() || !radial.center.x.isFinite() || !radial.center.y.isFinite()))
                    return Normalization.Refused(W5cPlanDiagnostics.NonFinite)
                if (radial != null && radial.radius < 0f)
                    return Normalization.Refused(W5cPlanDiagnostics.NegativeRadius)
                if (tileMode != org.graphiks.kanvas.render.ir.TileMode.CLAMP ||
                    interpolation != org.graphiks.kanvas.render.ir.ColorInterpolation.SRGB ||
                    draw.origin !in setOf(org.graphiks.kanvas.render.ir.DrawOrigin.RECT,
                        org.graphiks.kanvas.render.ir.DrawOrigin.RRECT, org.graphiks.kanvas.render.ir.DrawOrigin.PATH) && !imageMaskChild)
                    return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
                when (val stops = normalizeGradientStopsV1(linear?.stops() ?: radial?.stops() ?: sweep?.stops()
                    ?: requireNotNull(conical).stops(), preserveValidityMask = conical != null)) {
                    is NormalizedGradientStopsV1.Refused -> return Normalization.Refused(stops.code)
                    is NormalizedGradientStopsV1.Solid -> MaterialPlanEntry(MaterialProgramPlan.SolidLinearPremulV1,
                        MaterialBindingPlan.SolidRgbaF32V1.of(stops.colorF32))
                    is NormalizedGradientStopsV1.Stops -> {
                        val coordinates = MaterialCoordinatePlanV1.fromCtm(draw.transform)
                            ?: return Normalization.Refused(W5cPlanDiagnostics.CoordinatesUnavailable)
                        val inverseF32 = coordinates.copyInverseCtmF32()
                        val bounds = when (val geometry = draw.geometry) {
                            is org.graphiks.kanvas.render.ir.GeometryNode.Rect -> geometry.copyBounds()
                            is org.graphiks.kanvas.render.ir.GeometryNode.RRect -> geometry.copyShape().rect
                            is org.graphiks.kanvas.render.ir.GeometryNode.Path -> null
                            is org.graphiks.kanvas.render.ir.GeometryNode.ImagePatch -> if (imageMaskChild) geometry.copyDestination()
                                else return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
                            is org.graphiks.kanvas.render.ir.GeometryNode.ImageNine -> if (imageMaskChild) geometry.copyDestination()
                                else return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
                            is org.graphiks.kanvas.render.ir.GeometryNode.ImageLattice -> if (imageMaskChild) geometry.copyDestination()
                                else return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
                            is org.graphiks.kanvas.render.ir.GeometryNode.Atlas -> if (imageMaskChild) null
                                else return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
                            else -> return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
                        }
                        if (bounds == null && (gradientDeviceBoundsI32 == null || gradientDeviceBoundsI32.isEmpty))
                            return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                        val uniformValuesF32 = if (linear != null) listOf(linear.start.x, linear.start.y, linear.end.x, linear.end.y)
                            else if (radial != null) listOf(radial.center.x, radial.center.y, radial.radius, 0f)
                            else if (conical != null) listOf(conical.start.x, conical.start.y, conical.end.x, conical.end.y) +
                                requireNotNull(conicalDegeneracy).copyScalarsF32()
                            else requireNotNull(sweep).let { listOf(it.center.x, it.center.y, it.startAngle, it.endAngle,
                                requireNotNull(sweepDegeneracy).sweepSpanDegreesF32) }
                        val valuesF32 = uniformValuesF32 + listOf(
                            bounds?.left ?: 0f, bounds?.top ?: 0f, bounds?.right ?: 0f, bounds?.bottom ?: 0f)
                        // Include the inverse-mapped raster footprint in the finite local domain.
                        val radiusF64 = (kotlin.math.abs(inverseF32.sx.toDouble()) + kotlin.math.abs(inverseF32.kx.toDouble()) +
                            kotlin.math.abs(inverseF32.ky.toDouble()) + kotlin.math.abs(inverseF32.sy.toDouble())) * 2.0
                        val deviceCorners = if (bounds == null) emptyList() else listOf(org.graphiks.math.geometry.Point2F32(bounds.left, bounds.top),
                            org.graphiks.math.geometry.Point2F32(bounds.right, bounds.top),
                            org.graphiks.math.geometry.Point2F32(bounds.left, bounds.bottom),
                            org.graphiks.math.geometry.Point2F32(bounds.right, bounds.bottom)).map(draw.transform::transform)
                        // W4 already owns the conservative stroke/hairline raster bounds. Use
                        // those facts directly; never rebuild an outline for material planning.
                        val deviceXF64 = maxOf(deviceCorners.maxOfOrNull { kotlin.math.abs(it.x.toDouble()) } ?: 0.0,
                            gradientDeviceBoundsI32?.let { maxOf(kotlin.math.abs(it.left.toDouble()), kotlin.math.abs(it.right.toDouble())) } ?: 0.0) + 2.0
                        val deviceYF64 = maxOf(deviceCorners.maxOfOrNull { kotlin.math.abs(it.y.toDouble()) } ?: 0.0,
                            gradientDeviceBoundsI32?.let { maxOf(kotlin.math.abs(it.top.toDouble()), kotlin.math.abs(it.bottom.toDouble())) } ?: 0.0) + 2.0
                        val mappingBoundF64 = maxOf(
                            kotlin.math.abs(inverseF32.sx.toDouble()) * deviceXF64 + kotlin.math.abs(inverseF32.kx.toDouble()) * deviceYF64 + kotlin.math.abs(inverseF32.tx.toDouble()),
                            kotlin.math.abs(inverseF32.ky.toDouble()) * deviceXF64 + kotlin.math.abs(inverseF32.sy.toDouble()) * deviceYF64 + kotlin.math.abs(inverseF32.ty.toDouble()),
                        ) * 1.00001
                        if (valuesF32.any { !it.isFinite() || kotlin.math.abs(it.toDouble()) + radiusF64 > 1e8 } ||
                            !mappingBoundF64.isFinite() || mappingBoundF64 > 1e8 ||
                            inverseF32.persp0 != 0f || inverseF32.persp1 != 0f || inverseF32.persp2 != 1f)
                            return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                        val stopRange = GradientStopRangeV1(0u, stops.slab.copyStops().size.toUInt())
                        val uniformMagnitudeF64 = uniformValuesF32.maxOf { kotlin.math.abs(it.toDouble()) }
                        if (conical != null) {
                            val degeneracy = requireNotNull(conicalDegeneracy)
                            val numericAuthority = GradientNumericAuthorityV1.sealConical(coordinates, conical.start, conical.end,
                                degeneracy, stops.slab, mappingBoundF64, uniformMagnitudeF64)
                                ?: return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                            MaterialPlanEntry(MaterialProgramPlan.ConicalGradientClampSrgbV1,
                                MaterialBindingPlan.ConicalGradientV1(conical.start, conical.end, stopRange, degeneracy, numericAuthority), stops.slab)
                        } else if (sweep != null) {
                            val degeneracy = requireNotNull(sweepDegeneracy)
                            val numericAuthority = GradientNumericAuthorityV1.sealSweep(coordinates, sweep.center, degeneracy,
                                stops.slab, mappingBoundF64, uniformMagnitudeF64)
                                ?: return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                            MaterialPlanEntry(MaterialProgramPlan.SweepGradientClampSrgbV1,
                                MaterialBindingPlan.SweepGradientV1(sweep.center, stopRange, degeneracy, numericAuthority), stops.slab)
                        } else if (radial != null) {
                            val degeneracy = RadialGradientDegeneracyV1(radial.radius, radial.radius <= 0.000030517578125f)
                            val numericAuthority = GradientNumericAuthorityV1.sealRadial(coordinates, radial.center, radial.radius,
                                degeneracy, stops.slab, mappingBoundF64, uniformMagnitudeF64)
                                ?: return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                            MaterialPlanEntry(MaterialProgramPlan.RadialGradientClampSrgbV1,
                                MaterialBindingPlan.RadialGradientV1(radial.center, radial.radius, stopRange, degeneracy, numericAuthority), stops.slab)
                        } else {
                            requireNotNull(linear)
                            val degeneracy = LinearGradientDegeneracyV1.of(linear.start, linear.end)
                            val numericAuthority = GradientNumericAuthorityV1.sealLinear(coordinates, linear.start, linear.end,
                                degeneracy, stops.slab, mappingBoundF64, uniformMagnitudeF64)
                                ?: return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                            MaterialPlanEntry(MaterialProgramPlan.LinearGradientClampSrgbV1,
                                MaterialBindingPlan.LinearGradientV1(linear.start, linear.end, stopRange, degeneracy, numericAuthority), stops.slab)
                        }
                    }
                }
            }
            else -> return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
        }
        val shaderAlpha = opacityInnerToOuter.asReversed().fold(1f) { accumulated, alpha -> accumulated * alpha }
        val paintAlpha = draw.paint?.takeIf { it.shader != null }?.color?.alphaNormalized ?: 1f
        if (!shaderAlpha.isFinite() || shaderAlpha !in 0f..1f || !paintAlpha.isFinite() || paintAlpha !in 0f..1f) {
            return Normalization.Refused(W5aPlanDiagnostics.InvalidOpacity)
        }
        if (shaderAlpha == 0f || paintAlpha == 0f) {
            return Normalization.Source(
                MaterialPlanTable.of(listOf(MaterialPlanEntry(MaterialProgramPlan.TransparentV1, MaterialBindingPlan.EmptyV1))),
                MaterialPlanRef(0), blend,
            )
        }
        val entries = mutableListOf(base)
        if (shaderAlpha != 1f) {
            val child = entries.last().program
            entries += MaterialPlanEntry(
                MaterialProgramPlan.OpacityV1(child),
                MaterialBindingPlan.OpacityF32V1.of(shaderAlpha),
            )
        }
        if (paintAlpha != 1f) {
            val child = entries.last().program
            entries += MaterialPlanEntry(
                MaterialProgramPlan.OpacityV1(child),
                MaterialBindingPlan.OpacityF32V1.of(paintAlpha),
            )
        }
        return Normalization.Source(MaterialPlanTable.of(entries), MaterialPlanRef(entries.lastIndex), blend)
    }

    private fun normalizeW5d(draw: DrawNode, blend: BlendPlan, capture: GradientAddressingCaptureV2.Ready,
        gradientDeviceBoundsI32: org.graphiks.math.geometry.RectI32?, imageMaskChild: Boolean = false): Normalization {
        val linear = capture.leaf as? MaterialNode.LinearGradient
        val radial = capture.leaf as? MaterialNode.RadialGradient
        val sweep = capture.leaf as? MaterialNode.SweepGradient
        val conical = capture.leaf as? MaterialNode.ConicalGradient
        val interpolation = linear?.interpolation ?: radial?.interpolation ?: sweep?.interpolation
            ?: conical?.interpolation ?: return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
        if (interpolation != org.graphiks.kanvas.render.ir.ColorInterpolation.SRGB ||
            draw.origin !in setOf(org.graphiks.kanvas.render.ir.DrawOrigin.RECT,
                org.graphiks.kanvas.render.ir.DrawOrigin.RRECT, org.graphiks.kanvas.render.ir.DrawOrigin.PATH) && !imageMaskChild)
            return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
        val family = when {
            linear != null -> GradientFamilyV2.LINEAR
            radial != null -> GradientFamilyV2.RADIAL
            sweep != null -> GradientFamilyV2.SWEEP
            else -> GradientFamilyV2.CONICAL
        }
        val sweepDegeneracy = sweep?.let { SweepGradientDegeneracyV1.of(it.startAngle, it.endAngle) }
        if (linear != null && listOf(linear.start.x, linear.start.y, linear.end.x, linear.end.y).any { !it.isFinite() })
            return Normalization.Refused(W5cPlanDiagnostics.NonFinite)
        if (sweep != null && listOf(sweep.center.x, sweep.center.y, sweep.startAngle, sweep.endAngle).any { !it.isFinite() })
            return Normalization.Refused(W5cPlanDiagnostics.NonFinite)
        if (sweepDegeneracy?.sweepOrderingInvalid == true) return Normalization.Refused(W5cPlanDiagnostics.SweepOrdering)
        if (radial != null && listOf(radial.center.x, radial.center.y, radial.radius).any { !it.isFinite() })
            return Normalization.Refused(W5cPlanDiagnostics.NonFinite)
        if (conical != null && listOf(conical.start.x, conical.start.y, conical.end.x, conical.end.y,
                conical.startRadius, conical.endRadius).any { !it.isFinite() })
            return Normalization.Refused(W5cPlanDiagnostics.NonFinite)
        if (radial != null && radial.radius < 0f ||
            conical != null && (conical.startRadius < 0f || conical.endRadius < 0f))
            return Normalization.Refused(W5cPlanDiagnostics.NegativeRadius)
        // Family validity precedes stop normalization, including the Solid
        // shortcut: finite inputs can still overflow a derived delta/span/square.
        val familyDegeneracy: GradientDegeneracyV1 = when {
            linear != null -> LinearGradientDegeneracyV1.of(linear.start, linear.end)
            radial != null -> RadialGradientDegeneracyV1(radial.radius, radial.radius <= 0.000030517578125f)
            sweep != null -> requireNotNull(sweepDegeneracy)
            else -> requireNotNull(conical).let { ConicalGradientDegeneracyV1.of(it.start, it.startRadius, it.end, it.endRadius) }
        }
        val derivedScalarsF32 = when (familyDegeneracy) {
            is LinearGradientDegeneracyV1 -> familyDegeneracy.copyScalarsF32()
            is RadialGradientDegeneracyV1 -> listOf(familyDegeneracy.radialRadiusF32)
            is SweepGradientDegeneracyV1 -> with(familyDegeneracy) {
                listOf(startAngleDegreesF32, endAngleDegreesF32, sweepSpanDegreesF32)
            }
            is ConicalGradientDegeneracyV1 -> familyDegeneracy.copyScalarsF32()
        }
        if (derivedScalarsF32.any { !it.isFinite() }) return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
        val requested = linear?.tileMode ?: radial?.tileMode ?: sweep?.tileMode ?: requireNotNull(conical).tileMode
        // Full coverage changes only effective addressing/stop normalization;
        // the original requested mode remains part of the sealed program identity.
        val tileGraph = GradientTileModeV2.valueOf(requested.name).operationGraph(
            fullCoverageClamp = sweepDegeneracy?.sweepFullCoverage == true)
        fun source(base: MaterialPlanEntry): Normalization {
            val entries = mutableListOf(base)
            val paintAlphaF32 = draw.paint?.takeIf { it.shader != null }?.color?.alphaNormalized ?: 1f
            for (alphaF32 in listOf(capture.opacityF32, paintAlphaF32)) {
                if (!alphaF32.isFinite() || alphaF32 !in 0f..1f) return Normalization.Refused(W5aPlanDiagnostics.InvalidOpacity)
                if (alphaF32 != 1f) entries += MaterialPlanEntry(MaterialProgramPlan.OpacityV1(entries.last().program),
                    MaterialBindingPlan.OpacityF32V1.of(alphaF32))
            }
            return Normalization.Source(MaterialPlanTable.of(entries), MaterialPlanRef(entries.lastIndex), blend)
        }
        val inputStops = linear?.stops() ?: radial?.stops() ?: sweep?.stops() ?: requireNotNull(conical).stops()
        val stops = when (val normalized = normalizeGradientStopsV2(inputStops, tileGraph.effectiveMode, preserveValidityMask = conical != null)) {
            is NormalizedGradientStopsV1.Stops -> normalized.slab
            is NormalizedGradientStopsV1.Refused -> return Normalization.Refused(normalized.code)
            is NormalizedGradientStopsV1.Solid -> return source(MaterialPlanEntry(MaterialProgramPlan.SolidLinearPremulV1,
                MaterialBindingPlan.SolidRgbaF32V1.of(normalized.colorF32)))
        }
        val coordinates = when (val result = MaterialCoordinatePlanV2.fromCtmAndNodes(draw.transform, capture.coordinateNodes)) {
            is MaterialCoordinatePlanV2.Build.Ready -> result.coordinates
            is MaterialCoordinatePlanV2.Build.Refused -> return Normalization.Refused(result.code)
        }
        val bounds = when (val geometry = draw.geometry) {
            is org.graphiks.kanvas.render.ir.GeometryNode.Rect -> geometry.copyBounds()
            is org.graphiks.kanvas.render.ir.GeometryNode.RRect -> geometry.copyShape().rect
            is org.graphiks.kanvas.render.ir.GeometryNode.Path -> null
            is org.graphiks.kanvas.render.ir.GeometryNode.ImagePatch -> if (imageMaskChild) geometry.copyDestination()
                else return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
            is org.graphiks.kanvas.render.ir.GeometryNode.ImageNine -> if (imageMaskChild) geometry.copyDestination()
                else return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
            is org.graphiks.kanvas.render.ir.GeometryNode.ImageLattice -> if (imageMaskChild) geometry.copyDestination()
                else return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
            is org.graphiks.kanvas.render.ir.GeometryNode.Atlas -> if (imageMaskChild) null
                else return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
            else -> return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
        }
        val deviceCorners = bounds?.let { listOf(org.graphiks.math.geometry.Point2F32(bounds.left, bounds.top),
            org.graphiks.math.geometry.Point2F32(bounds.right, bounds.top),
            org.graphiks.math.geometry.Point2F32(bounds.left, bounds.bottom),
            org.graphiks.math.geometry.Point2F32(bounds.right, bounds.bottom)).map(draw.transform::transform) }
        // W3/W4a own axis-aligned device rectangles. Outward pixel edges enclose every
        // fragment center (including fractional-edge AA), without losing X/Y correlation
        // to an unrelated absolute-magnitude square before projective evaluation.
        val deviceBoundsF32 = if (deviceCorners != null) org.graphiks.math.geometry.RectF32.ofLTRB(
            kotlin.math.floor(deviceCorners.minOf { it.x }), kotlin.math.floor(deviceCorners.minOf { it.y }),
            kotlin.math.ceil(deviceCorners.maxOf { it.x }), kotlin.math.ceil(deviceCorners.maxOf { it.y }))
        else gradientDeviceBoundsI32?.takeUnless { it.isEmpty }?.let {
            org.graphiks.math.geometry.RectF32.ofLTRB(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
        } ?: return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
        val magnitudeF64 = coordinates.proveCoordinateDomainF64(deviceBoundsF32)
            ?: return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
        val consumesAverage = familyDegeneracy.consumesAverage(tileGraph.effectiveMode)
        val averageSrgbaF32 = if (consumesAverage) stops.exactAverageSrgbaF32() else null
        val program = GradientAddressingProgramV2(family, tileGraph.requestedMode,
            tileGraph.effectiveMode, tileGraph.contractId, coordinates.topologyIdentity, consumesAverage)
        val range = GradientStopRangeV1(0u, stops.copyStops().size.toUInt())
        fun magnitude(valuesF32: List<Float>): Double = valuesF32.maxOf { kotlin.math.abs(it.toDouble()) }
        val binding: MaterialBindingPlan.GradientV2 = when {
            linear != null -> {
                val degeneracy = familyDegeneracy as LinearGradientDegeneracyV1
                val numeric = GradientNumericAuthorityV2.sealLinear(program, coordinates, linear.start, linear.end,
                    degeneracy, stops, magnitudeF64, magnitude(listOf(linear.start.x, linear.start.y, linear.end.x, linear.end.y)), averageSrgbaF32)
                    ?: return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                MaterialBindingPlan.LinearGradientV2(linear.start, linear.end, range, degeneracy, numeric, averageSrgbaF32)
            }
            radial != null -> {
                val degeneracy = familyDegeneracy as RadialGradientDegeneracyV1
                val numeric = GradientNumericAuthorityV2.sealRadial(program, coordinates, radial.center, radial.radius,
                    degeneracy, stops, magnitudeF64, magnitude(listOf(radial.center.x, radial.center.y, radial.radius)), averageSrgbaF32)
                    ?: return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                MaterialBindingPlan.RadialGradientV2(radial.center, radial.radius, range, degeneracy, numeric, averageSrgbaF32)
            }
            sweep != null -> {
                val degeneracy = requireNotNull(sweepDegeneracy)
                val numeric = GradientNumericAuthorityV2.sealSweep(program, coordinates, sweep.center, degeneracy,
                    stops, magnitudeF64, magnitude(listOf(sweep.center.x, sweep.center.y, sweep.startAngle,
                        sweep.endAngle, degeneracy.sweepSpanDegreesF32)), averageSrgbaF32)
                    ?: return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                MaterialBindingPlan.SweepGradientV2(sweep.center, range, degeneracy, numeric, averageSrgbaF32)
            }
            else -> {
                requireNotNull(conical)
                val degeneracy = familyDegeneracy as ConicalGradientDegeneracyV1
                val numeric = GradientNumericAuthorityV2.sealConical(program, coordinates, conical.start, conical.end,
                    degeneracy, stops, magnitudeF64, magnitude(listOf(conical.start.x, conical.start.y,
                        conical.end.x, conical.end.y) + degeneracy.copyScalarsF32()), averageSrgbaF32)
                    ?: return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                MaterialBindingPlan.ConicalGradientV2(conical.start, conical.end, range, degeneracy, numeric, averageSrgbaF32)
            }
        }
        return source(MaterialPlanEntry(program, binding, stops))
    }
}
