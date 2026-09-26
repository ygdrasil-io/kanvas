package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathStrokeGeometryF32
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeStyleF64
import org.graphiks.math.geometry.PathStrokeCap
import org.graphiks.math.geometry.PathStrokeJoin
import org.graphiks.math.geometry.PathStrokeWidthF64
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.ClipGeometryF32
import org.graphiks.math.geometry.InversePathGeometryF32
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.matrix.Matrix3x3F32

public enum class CoveragePlan { FullOrScissor, AnalyticScalarAA, StencilAA4, BinaryMaskCover4 }
public enum class SamplePlan { SingleSample, Multisample4 }
public enum class AttachmentLoadPlan { ClearTransparent, Load }
public enum class AttachmentStorePlan { Store }
public enum class PlanDepthStencilLoadStore { ClearZeroStore, LoadStoreTestReset }
public enum class PlanDepthStencilAccess { Write, ReadWrite }
public enum class PlanPassRole {
    MainRender,
    PathMaskClear,
    PathRender,
    StencilProducer,
    StencilCover,
    TextureCopy,
    Filter,
    Resolve,
    Readback,
    ClipMaskInitialize,
    ClipMaskProducer,
    ClipMaskFold,
    LayerComposite,
    FilterSourceClear,
    FilterCoverageSource,
    FilterCoverageRetain,
    PictureAggregateBegin,
    PictureAggregateSeal,
    PictureSource,
    PictureComposite,
    FilterComposite,
}
public enum class ClipCombineOperation { Intersect, Difference }

/**
 * The one parent-target composition selected at a W6b occurrence boundary.  It retains the
 * previously selected W5/W6 blend or restore facts; native execution is intentionally deferred.
 */
public sealed interface FilterCompositeOperationV1 {
    /** A null composite scissor with [noOp] is a sealed terminal clip decision, not a renderer fallback. */
    public data class Draw(public val blend: BlendPlan, public val noOp: Boolean = false) : FilterCompositeOperationV1
    /** A null composite scissor with [noOp] is a sealed terminal clip decision, not a renderer fallback. */
    public data class Layer(public val restore: LayerRestorePlanV1, public val noOp: Boolean = false) : FilterCompositeOperationV1
    /** Captured locator is provenance; the terminal owns every executable composite operand. */
    public data class Picture(public val sourceSceneCanonicalId: String, public val sourceCommandIndexI32: Int,
        public val terminal: PictureCompositeOperandsV1? = null) : FilterCompositeOperationV1 {
        init { require(sourceSceneCanonicalId.isNotBlank() && sourceCommandIndexI32 >= 0) }
    }
}

/** The clip realization selected for one consumer draw. */
public sealed interface ClipPlanStrategy {
    public class Scissor(domainI32: RectI32, public val child: ClipPlanStrategy? = null) : ClipPlanStrategy {
        private val domainSnapshotI32: RectI32 = domainI32.copy()
        public fun copyDomainI32(): RectI32 = domainSnapshotI32.copy()
    }
    public class Stencil(public val depthStencil: PlanResourceId, public val child: ClipPlanStrategy? = null) : ClipPlanStrategy
    public class Mask(public val resource: PlanResourceId) : ClipPlanStrategy
    public class InverseMask(
        public val geometryF32: InversePathGeometryF32,
        public val resource: PlanResourceId,
    ) : ClipPlanStrategy
    /** Bounded inverse coverage over the full target domain when no clip texture is needed. */
    public class InverseDomain(public val geometryF32: InversePathGeometryF32) : ClipPlanStrategy
}
public enum class PathFillStrategy { DirectTriangle, StencilCover }
public enum class PathRenderPhase {
    SingleSampleDirectColor,
    SingleSampleStencilProducer,
    SingleSampleStencilColorCover,
    MultisampleDirectColor,
    MultisampleStencilProducer,
    MultisampleStencilColorCover,
    HardEdgeMaskProducer,
    HardEdgeMaskStencilProducer,
    HardEdgeMaskStencilCover,
    HardEdgeBinaryColorCover,
}
public enum class BinaryMaskFetchPlan { TextureLoadUnfiltered }

/** Immutable geometry authority for a path draw, retained by `:math`. */
public sealed interface PathDrawGeometry {
    public data class Fill(public val valueF32: PathFillGeometryF32) : PathDrawGeometry
    public data class Stroke(public val valueF32: PathStrokeGeometryF32) : PathDrawGeometry
    /**
     * The exact non-empty source of an inverse path whose finite interior is empty.
     *
     * W4d.2 receives a finite proxy solely to admit the construction seam.  W4e replaces that
     * proxy with this immutable source fact before it publishes its compiler-authenticated graph.
     * It is deliberately not drawable: a zero interior is rendered as the bounded inverse domain.
     */
    public class InverseDomainSource private constructor(
        path: PathF32,
        transform: Matrix3x3F32,
    ) : PathDrawGeometry {
        private val pathSnapshot: PathF32 = PathBuilder(path.fillRule).addPath(path).build()
        private val transformSnapshot: Matrix3x3F32 = transform.copy()

        public fun copySourcePath(): PathF32 = PathBuilder(pathSnapshot.fillRule).addPath(pathSnapshot).build()

        public fun copySourceTransform(): Matrix3x3F32 = transformSnapshot.copy()

        internal companion object {
            internal fun of(path: PathF32, transform: Matrix3x3F32): InverseDomainSource {
                require(path.segmentCount > 0) {
                    "Only a non-empty source path can be retained as a W4e inverse-domain source"
                }
                return InverseDomainSource(path, transform)
            }
        }
    }
    /** An inverse draw whose finite interior is empty; its W4e clip still owns the finite domain. */
    public data object Empty : PathDrawGeometry
}

public sealed interface PlanDraw {
    public val commandIndex: Int
    public val color: ColorF32
    /**
     * The closed material authority consumed by a renderer.  Pre-W5 draw kinds retain their
     * colour through the legacy arm; W5 draws use only the material-table reference.
     */
    public val materialAuthority: PlanDrawMaterialAuthority
        get() = PlanDrawMaterialAuthority.LegacyColorV1.of(color)
    public val materialCoordinates: MaterialCoordinatePlanV1?
        get() = (materialAuthority as? PlanDrawMaterialAuthority.MaterialV1)?.coordinates
    public val materialCoordinatesV2: MaterialCoordinatePlanV2?
        get() = (materialAuthority as? PlanDrawMaterialAuthority.MaterialV2)?.coordinates
    public val coverage: CoveragePlan
    public val sample: SamplePlan
    public val blend: BlendPlan
}

/** A visual draw whose coverage is constrained by a separately planned clip strategy. */
public class ClippedPlanDraw private constructor(
    public val source: PlanDraw,
    public val strategy: ClipPlanStrategy,
) : PlanDraw {
    override public val commandIndex: Int get() = source.commandIndex
    override public val color: ColorF32 get() = source.color
    override public val materialAuthority: PlanDrawMaterialAuthority get() = source.materialAuthority
    override public val coverage: CoveragePlan get() = source.coverage
    override public val sample: SamplePlan get() = source.sample
    override public val blend: BlendPlan get() = source.blend

    public companion object {
        public fun of(source: PlanDraw, strategy: ClipPlanStrategy): ClippedPlanDraw =
            ClippedPlanDraw(source, strategy)
    }
}

/** Common sealed contract for W4c fills and W4d stroke snapshots. */
public sealed interface PathDraw : PlanDraw {
    public val strategy: PathFillStrategy
    public fun copyPathGeometry(): PathDrawGeometry
    public fun copyScissorI32(): RectI32
}

/** Typed W4d.2 path draw snapshot for a single-sample hard producer or a four-sample AA color draw. */
public class GeneralPathDraw private constructor(
    override public val commandIndex: Int,
    override public val materialAuthority: PlanDrawMaterialAuthority,
    geometry: PathDrawGeometry,
    override public val strategy: PathFillStrategy,
    scissorI32: RectI32,
    override public val coverage: CoveragePlan,
    override public val sample: SamplePlan,
    override public val blend: BlendPlan = BlendPlan.SrcOver,
) : PathRenderDraw, PathDraw {
    private val geometrySnapshot: PathDrawGeometry = geometry
    private val scissorSnapshotI32 = scissorI32.copy()

    override fun copyPathGeometry(): PathDrawGeometry = geometrySnapshot

    override fun copyScissorI32(): RectI32 = scissorSnapshotI32.copy()

    /** Pre-publication target rebinding retains this already selected General path contract. */
    internal fun rebindGeometryV6(geometry: PathDrawGeometry, scissorI32: RectI32,
        material: PlanDrawMaterialAuthority = materialAuthority): GeneralPathDraw =
        GeneralPathDraw(commandIndex, material, geometry, strategy, scissorI32, coverage, sample, blend)

    /** Legacy-only compatibility view. W5 path draws carry no reconstructed colour. */
    override public val color: ColorF32
        get() = (materialAuthority as? PlanDrawMaterialAuthority.LegacyColorV1)?.copyColorF32()
            ?: throw IllegalStateException("W5 material draws have no legacy colour authority")

    /** Rebind only the occurrence identity; retain the selected geometry and material authority. */
    internal fun withCommandIndexI32(indexI32: Int): GeneralPathDraw {
        require(indexI32 >= 0)
        return GeneralPathDraw(indexI32, materialAuthority, geometrySnapshot, strategy, scissorSnapshotI32, coverage, sample, blend)
    }

    public companion object {
        public fun of(
            commandIndex: Int,
            color: ColorF32,
            geometry: PathDrawGeometry,
            strategy: PathFillStrategy,
            scissorI32: RectI32,
            coverage: CoveragePlan,
            sample: SamplePlan,
        ): GeneralPathDraw {
            require(commandIndex >= 0) { "Command index must not be negative" }
            require(!scissorI32.isEmpty) { "General path scissor must be non-empty" }
            require(
                (coverage == CoveragePlan.FullOrScissor && sample == SamplePlan.SingleSample) ||
                    (coverage == CoveragePlan.StencilAA4 && sample == SamplePlan.Multisample4),
            ) { "General path draws require an explicit hard or four-sample AA contract" }
            requirePathRenderGeometryForStrategy(geometry, strategy)
            return GeneralPathDraw(
                commandIndex, PlanDrawMaterialAuthority.LegacyColorV1.of(color), geometry, strategy, scissorI32, coverage, sample,
            )
        }

        public fun ofMaterial(
            commandIndexI32: Int,
            material: MaterialPlanRef,
            geometry: PathDrawGeometry,
            strategy: PathFillStrategy,
            scissorI32: RectI32,
            coverage: CoveragePlan,
            sample: SamplePlan,
            blend: BlendPlan = BlendPlan.SrcOver,
            coordinates: MaterialCoordinatePlanV1? = null,
            coordinatesV2: MaterialCoordinatePlanV2? = null,
            coordinatesV4: SourceCoordinatesV4? = null,
            composedV5: Boolean = false,
        ): GeneralPathDraw {
            require(commandIndexI32 >= 0) { "Command index must not be negative" }
            require(!scissorI32.isEmpty) { "General path scissor must be non-empty" }
            require(
                (coverage == CoveragePlan.FullOrScissor && sample == SamplePlan.SingleSample) ||
                    (coverage == CoveragePlan.StencilAA4 && sample == SamplePlan.Multisample4),
            ) { "General path draws require an explicit hard or four-sample AA contract" }
            requirePathRenderGeometryForStrategy(geometry, strategy)
            return GeneralPathDraw(
                commandIndexI32, if (composedV5) PlanDrawMaterialAuthority.MaterialV5(material) else coordinatesV4?.let { PlanDrawMaterialAuthority.MaterialV4(material, it) }
                    ?: coordinatesV2?.let { PlanDrawMaterialAuthority.MaterialV2(material, it) }
                    ?: PlanDrawMaterialAuthority.MaterialV1(material, coordinates), geometry, strategy, scissorI32, coverage, sample, blend,
            )
        }

        /** W4e uses this form only for a source path that contained no segments at all. */
        internal fun w4eActuallyEmptyInverseDomainOf(source: GeneralPathDraw): GeneralPathDraw =
            GeneralPathDraw(
                source.commandIndex,
                source.materialAuthority,
                PathDrawGeometry.Empty,
                source.strategy,
                source.copyScissorI32(),
                source.coverage,
                source.sample,
            )

        /** Restores the original source after W4d.2 used its finite construction proxy. */
        internal fun w4eInverseDomainSourceOf(
            source: GeneralPathDraw,
            geometry: PathDrawGeometry.InverseDomainSource,
        ): GeneralPathDraw = GeneralPathDraw(
            source.commandIndex,
            source.materialAuthority,
            geometry,
            source.strategy,
            source.copyScissorI32(),
            source.coverage,
            source.sample,
        )
    }
}

/** Retains the same immutable General geometry without projecting it into a narrow path lane. */
public fun GeneralPathDraw.withBlend(blend: BlendPlan): GeneralPathDraw = GeneralPathDraw.ofMaterial(
    commandIndex, materialAuthority.materialPlanRef(), copyPathGeometry(), strategy,
    copyScissorI32(), coverage, sample, blend, materialCoordinates, materialCoordinatesV2,
    (materialAuthority as? PlanDrawMaterialAuthority.MaterialV4)?.coordinates,
    materialAuthority is PlanDrawMaterialAuthority.MaterialV5,
)

/** A W4d.2 direct path draw whose final coverage is constrained by a W4e clip plan. */
public class ClippedGeneralPathDraw private constructor(
    public val source: GeneralPathDraw,
    public val clip: ClipPlanStrategy,
) : PathRenderDraw {
    override public val commandIndex: Int get() = source.commandIndex
    override public val color: ColorF32 get() = source.color
    override public val materialAuthority: PlanDrawMaterialAuthority get() = source.materialAuthority
    override public val strategy: PathFillStrategy get() = source.strategy
    override public val coverage: CoveragePlan get() = source.coverage
    override public val sample: SamplePlan get() = source.sample
    override public val blend: BlendPlan get() = source.blend

    override fun copyPathGeometry(): PathDrawGeometry = source.copyPathGeometry()
    override fun copyScissorI32(): RectI32 = source.copyScissorI32()

    public companion object {
        public fun of(source: GeneralPathDraw, clip: ClipPlanStrategy): ClippedGeneralPathDraw =
            ClippedGeneralPathDraw(source, clip)
    }
}

/** A four-sample color cover driven by one unfiltered binary texel from a single-sample mask. */
public class BinaryMaskedPathDraw private constructor(
    public val producer: GeneralPathDraw,
    public val mask: PlanResourceId,
) : PathRenderDraw {
    override public val commandIndex: Int = producer.commandIndex
    override public val color: ColorF32 get() = producer.color
    override public val materialAuthority: PlanDrawMaterialAuthority = producer.materialAuthority
    override public val strategy: PathFillStrategy = producer.strategy
    override public val coverage: CoveragePlan = CoveragePlan.BinaryMaskCover4
    override public val sample: SamplePlan = SamplePlan.Multisample4
    override public val blend: BlendPlan = BlendPlan.SrcOver
    public val maskFetch: BinaryMaskFetchPlan = BinaryMaskFetchPlan.TextureLoadUnfiltered
    public val broadcastSampleCountI32: Int = 4

    override fun copyPathGeometry(): PathDrawGeometry = producer.copyPathGeometry()

    override fun copyScissorI32(): RectI32 = producer.copyScissorI32()

    public companion object {
        public fun of(source: GeneralPathDraw, mask: PlanResourceId): BinaryMaskedPathDraw {
            require(source.coverage == CoveragePlan.FullOrScissor && source.sample == SamplePlan.SingleSample) {
                "Binary mask covers require a single-sample hard-edge producer"
            }
            return BinaryMaskedPathDraw(source, mask)
        }
    }
}

/** A typed AA4 hard-edge path consumer with both binary and ordered clip-mask coverage. */
public class ClippedBinaryMaskedPathDraw private constructor(
    public val source: BinaryMaskedPathDraw,
    public val clip: ClipPlanStrategy,
) : PathRenderDraw {
    override public val commandIndex: Int get() = source.commandIndex
    override public val color: ColorF32 get() = source.color
    override public val materialAuthority: PlanDrawMaterialAuthority get() = source.materialAuthority
    override public val strategy: PathFillStrategy get() = source.strategy
    override public val coverage: CoveragePlan get() = source.coverage
    override public val sample: SamplePlan get() = source.sample
    override public val blend: BlendPlan get() = source.blend
    /** The binary source texture is one-sample; its value is broadcast to the four color samples. */
    public val sourceMaskSampleCountI32: Int get() = 1

    override fun copyPathGeometry(): PathDrawGeometry = source.copyPathGeometry()
    override fun copyScissorI32(): RectI32 = source.copyScissorI32()

    public companion object {
        public fun of(source: BinaryMaskedPathDraw, clip: ClipPlanStrategy): ClippedBinaryMaskedPathDraw =
            ClippedBinaryMaskedPathDraw(source, clip)
    }
}

/** Common typed path draw contract owned by W4d.2 path render passes. */
public sealed interface PathRenderDraw : PlanDraw {
    public val strategy: PathFillStrategy
    public fun copyPathGeometry(): PathDrawGeometry
    public fun copyScissorI32(): RectI32
}

public class SolidRectDraw private constructor(
    override public val commandIndex: Int,
    override public val materialAuthority: PlanDrawMaterialAuthority,
    visibleBounds: RectI32,
    scissor: RectI32,
    override public val coverage: CoveragePlan,
    override public val sample: SamplePlan,
    override public val blend: BlendPlan,
) : PlanDraw {
    override public val materialCoordinates: MaterialCoordinatePlanV1?
        get() = (materialAuthority as? PlanDrawMaterialAuthority.MaterialV1)?.coordinates
    private val storedVisibleBounds = visibleBounds.copy()
    private val storedScissor = scissor.copy()

    public fun copyVisibleBounds(): RectI32 = storedVisibleBounds.copy()
    public fun copyScissor(): RectI32 = storedScissor.copy()

    /** Legacy-only compatibility view. W5 draws never carry a duplicated colour value. */
    override public val color: ColorF32
        get() = (materialAuthority as? PlanDrawMaterialAuthority.LegacyColorV1)?.copyColorF32()
            ?: throw IllegalStateException("W5 material draws have no legacy colour authority")

    /** Rebind only the occurrence identity; retain the selected geometry and material authority. */
    internal fun withCommandIndexI32(indexI32: Int): SolidRectDraw {
        require(indexI32 >= 0)
        return SolidRectDraw(indexI32, materialAuthority, storedVisibleBounds, storedScissor, coverage, sample, blend)
    }

    public companion object {
        public fun of(
            commandIndex: Int,
            color: ColorF32,
            visibleBounds: RectI32,
            scissor: RectI32,
            coverage: CoveragePlan = CoveragePlan.FullOrScissor,
            sample: SamplePlan = SamplePlan.SingleSample,
            blend: BlendPlan = BlendPlan.SrcOver,
        ): SolidRectDraw {
            require(commandIndex >= 0) { "Command index must be non-negative" }
            require(!visibleBounds.isEmpty && !scissor.isEmpty) { "Draw rectangles must be non-empty" }
            return SolidRectDraw(
                commandIndex,
                PlanDrawMaterialAuthority.LegacyColorV1.of(color),
                visibleBounds,
                scissor,
                coverage,
                sample,
                blend,
            )
        }

        public fun ofMaterial(
            commandIndexI32: Int,
            material: MaterialPlanRef,
            visibleBounds: RectI32,
            scissor: RectI32,
            coverage: CoveragePlan = CoveragePlan.FullOrScissor,
            sample: SamplePlan = SamplePlan.SingleSample,
            blend: BlendPlan = BlendPlan.SrcOver,
            coordinates: MaterialCoordinatePlanV1? = null,
            coordinatesV2: MaterialCoordinatePlanV2? = null,
            coordinatesV4: SourceCoordinatesV4? = null,
            composedV5: Boolean = false,
        ): SolidRectDraw {
            require(commandIndexI32 >= 0) { "Command index must be non-negative" }
            require(!visibleBounds.isEmpty && !scissor.isEmpty) { "Draw rectangles must be non-empty" }
            return SolidRectDraw(commandIndexI32, if (composedV5) PlanDrawMaterialAuthority.MaterialV5(material) else coordinatesV4?.let { PlanDrawMaterialAuthority.MaterialV4(material,it) }
                ?: coordinatesV2?.let { PlanDrawMaterialAuthority.MaterialV2(material, it) }
                ?: PlanDrawMaterialAuthority.MaterialV1(material, coordinates), visibleBounds, scissor, coverage, sample, blend)
        }
    }
}

/** Reissues only the sealed W5 material reference; geometry and raster facts are copied verbatim. */
public fun SolidRectDraw.withMaterialRef(material: MaterialPlanRef): SolidRectDraw = SolidRectDraw.ofMaterial(
    commandIndex, material, copyVisibleBounds(), copyScissor(), coverage, sample, blend, materialCoordinates, materialCoordinatesV2,
    (materialAuthority as? PlanDrawMaterialAuthority.MaterialV4)?.coordinates,
    materialAuthority is PlanDrawMaterialAuthority.MaterialV5,
)

public class AnalyticRectDraw private constructor(
    override public val commandIndex: Int,
    override public val materialAuthority: PlanDrawMaterialAuthority,
    deviceBounds: RectF32,
    rasterBounds: RectI32,
    scissor: RectI32,
    override public val blend: BlendPlan = BlendPlan.LegacySrcOverV1,
) : PlanDraw {
    override public val materialCoordinates: MaterialCoordinatePlanV1?
        get() = (materialAuthority as? PlanDrawMaterialAuthority.MaterialV1)?.coordinates
    override public val coverage: CoveragePlan = CoveragePlan.AnalyticScalarAA
    override public val sample: SamplePlan = SamplePlan.SingleSample
    private val storedDeviceBounds = deviceBounds.copy()
    private val storedRasterBounds = rasterBounds.copy()
    private val storedScissor = scissor.copy()

    public fun copyDeviceBounds(): RectF32 = storedDeviceBounds.copy()
    public fun copyRasterBounds(): RectI32 = storedRasterBounds.copy()
    public fun copyScissor(): RectI32 = storedScissor.copy()

    /** Legacy-only compatibility view. W5 draws never carry a duplicated colour value. */
    override public val color: ColorF32
        get() = (materialAuthority as? PlanDrawMaterialAuthority.LegacyColorV1)?.copyColorF32()
            ?: throw IllegalStateException("W5 material draws have no legacy colour authority")

    /** Rebind only the occurrence identity; retain the selected geometry and material authority. */
    internal fun withCommandIndexI32(indexI32: Int): AnalyticRectDraw {
        require(indexI32 >= 0)
        return AnalyticRectDraw(indexI32, materialAuthority, storedDeviceBounds, storedRasterBounds, storedScissor, blend)
    }

    public companion object {
        public fun of(
            commandIndex: Int,
            color: ColorF32,
            deviceBounds: RectF32,
            rasterBounds: RectI32,
            scissor: RectI32,
        ): AnalyticRectDraw {
            require(commandIndex >= 0) { "Command index must be non-negative" }
            require(!deviceBounds.isEmpty && !rasterBounds.isEmpty && !scissor.isEmpty) {
                "Draw rectangles must be non-empty"
            }
            return AnalyticRectDraw(
                commandIndex,
                PlanDrawMaterialAuthority.LegacyColorV1.of(color),
                deviceBounds,
                rasterBounds,
                scissor,
            )
        }

        public fun ofMaterial(
            commandIndexI32: Int,
            material: MaterialPlanRef,
            deviceBounds: RectF32,
            rasterBounds: RectI32,
            scissor: RectI32,
            blend: BlendPlan = BlendPlan.LegacySrcOverV1,
            coordinates: MaterialCoordinatePlanV1? = null,
            coordinatesV2: MaterialCoordinatePlanV2? = null,
            coordinatesV4: SourceCoordinatesV4? = null,
            composedV5: Boolean = false,
        ): AnalyticRectDraw {
            require(commandIndexI32 >= 0) { "Command index must not be negative" }
            require(!deviceBounds.isEmpty && !rasterBounds.isEmpty && !scissor.isEmpty) {
                "Draw rectangles must be non-empty"
            }
            return AnalyticRectDraw(
                commandIndexI32,
                if (composedV5) PlanDrawMaterialAuthority.MaterialV5(material) else coordinatesV4?.let { PlanDrawMaterialAuthority.MaterialV4(material,it) }
                    ?: coordinatesV2?.let { PlanDrawMaterialAuthority.MaterialV2(material, it) }
                    ?: PlanDrawMaterialAuthority.MaterialV1(material, coordinates),
                deviceBounds,
                rasterBounds,
                scissor,
                blend,
            )
        }
    }
}

public fun AnalyticRectDraw.withMaterialRef(material: MaterialPlanRef): AnalyticRectDraw = AnalyticRectDraw.ofMaterial(
    commandIndex, material, copyDeviceBounds(), copyRasterBounds(), copyScissor(), blend, materialCoordinates, materialCoordinatesV2,
    (materialAuthority as? PlanDrawMaterialAuthority.MaterialV4)?.coordinates,
    materialAuthority is PlanDrawMaterialAuthority.MaterialV5,
)

public class AnalyticRRectDraw private constructor(
    override public val commandIndex: Int,
    override public val materialAuthority: PlanDrawMaterialAuthority,
    public val origin: DrawOrigin,
    deviceShape: RRectF32,
    rasterBounds: RectI32,
    scissor: RectI32,
    override public val blend: BlendPlan = BlendPlan.LegacySrcOverV1,
) : PlanDraw {
    override public val coverage: CoveragePlan = CoveragePlan.AnalyticScalarAA
    override public val sample: SamplePlan = SamplePlan.SingleSample
    private val storedDeviceShape = RRectF32.of(
        deviceShape.rect.copy(),
        deviceShape.topLeft,
        deviceShape.topRight,
        deviceShape.bottomRight,
        deviceShape.bottomLeft,
    )
    private val storedRasterBounds = rasterBounds.copy()
    private val storedScissor = scissor.copy()

    public fun copyDeviceShape(): RRectF32 = RRectF32.of(
        storedDeviceShape.rect.copy(),
        storedDeviceShape.topLeft,
        storedDeviceShape.topRight,
        storedDeviceShape.bottomRight,
        storedDeviceShape.bottomLeft,
    )
    public fun copyRasterBounds(): RectI32 = storedRasterBounds.copy()
    public fun copyScissor(): RectI32 = storedScissor.copy()

    /** Legacy-only compatibility view. W5 draws never carry a duplicated colour value. */
    override public val color: ColorF32
        get() = (materialAuthority as? PlanDrawMaterialAuthority.LegacyColorV1)?.copyColorF32()
            ?: throw IllegalStateException("W5 material draws have no legacy colour authority")

    /** Rebind only the occurrence identity; retain the selected geometry and material authority. */
    internal fun withCommandIndexI32(indexI32: Int): AnalyticRRectDraw {
        require(indexI32 >= 0)
        return AnalyticRRectDraw(indexI32, materialAuthority, origin, storedDeviceShape, storedRasterBounds, storedScissor, blend)
    }

    public companion object {
        public fun of(
            commandIndex: Int,
            color: ColorF32,
            origin: DrawOrigin,
            deviceShape: RRectF32,
            rasterBounds: RectI32,
            scissor: RectI32,
        ): AnalyticRRectDraw {
            require(commandIndex >= 0) { "Command index must be non-negative" }
            require(origin == DrawOrigin.RECT || origin == DrawOrigin.RRECT) {
                "Analytic rrect draws require RECT or RRECT origin"
            }
            require(!deviceShape.rect.isEmpty && !rasterBounds.isEmpty && !scissor.isEmpty) {
                "Draw rectangles must be non-empty"
            }
            return AnalyticRRectDraw(
                commandIndex,
                PlanDrawMaterialAuthority.LegacyColorV1.of(color),
                origin,
                deviceShape,
                rasterBounds,
                scissor,
            )
        }

        public fun ofMaterial(
            commandIndexI32: Int,
            material: MaterialPlanRef,
            origin: DrawOrigin,
            deviceShape: RRectF32,
            rasterBounds: RectI32,
            scissor: RectI32,
            blend: BlendPlan = BlendPlan.LegacySrcOverV1,
            coordinates: MaterialCoordinatePlanV1? = null,
            coordinatesV2: MaterialCoordinatePlanV2? = null,
            composedV5: Boolean = false,
        ): AnalyticRRectDraw {
            validateMaterialGeometry(commandIndexI32, origin, deviceShape, rasterBounds, scissor)
            return AnalyticRRectDraw(
                commandIndexI32,
                if (composedV5) PlanDrawMaterialAuthority.MaterialV5(material) else coordinatesV2?.let { PlanDrawMaterialAuthority.MaterialV2(material, it) }
                    ?: PlanDrawMaterialAuthority.MaterialV1(material, coordinates),
                origin,
                deviceShape,
                rasterBounds,
                scissor,
                blend,
            )
        }

        internal fun ofMaterialV4(commandIndexI32: Int, material: MaterialPlanRef, origin: DrawOrigin,
            deviceShape: RRectF32, rasterBounds: RectI32, scissor: RectI32, blend: BlendPlan,
            coordinates: SourceCoordinatesV4): AnalyticRRectDraw {
            validateMaterialGeometry(commandIndexI32, origin, deviceShape, rasterBounds, scissor)
            return AnalyticRRectDraw(commandIndexI32, PlanDrawMaterialAuthority.MaterialV4(material, coordinates),
                origin, deviceShape, rasterBounds, scissor, blend)
        }

        private fun validateMaterialGeometry(commandIndexI32: Int, origin: DrawOrigin,
            deviceShape: RRectF32, rasterBounds: RectI32, scissor: RectI32) {
            require(commandIndexI32 >= 0) { "Command index must not be negative" }
            require(origin == DrawOrigin.RECT || origin == DrawOrigin.RRECT) {
                "Analytic rrect draws require RECT or RRECT origin"
            }
            require(!deviceShape.rect.isEmpty && !rasterBounds.isEmpty && !scissor.isEmpty) {
                "Draw rectangles must be non-empty"
            }
        }
    }
}

/** Reissues only the sealed W5 material reference; analytic RRect geometry remains native. */
public fun AnalyticRRectDraw.withMaterialRef(material: MaterialPlanRef): AnalyticRRectDraw =
    (materialAuthority as? PlanDrawMaterialAuthority.MaterialV4)?.let {
        AnalyticRRectDraw.ofMaterialV4(commandIndex, material, origin, copyDeviceShape(), copyRasterBounds(),
            copyScissor(), blend, it.coordinates)
    } ?: AnalyticRRectDraw.ofMaterial(commandIndex, material, origin, copyDeviceShape(), copyRasterBounds(),
        copyScissor(), blend, materialCoordinates, materialCoordinatesV2, materialAuthority is PlanDrawMaterialAuthority.MaterialV5)

/** A sealed W4c path-fill draw whose geometry authority remains owned by `:math`. */
public class PathFillDraw private constructor(
    override public val commandIndex: Int,
    override public val materialAuthority: PlanDrawMaterialAuthority,
    geometryF32: PathFillGeometryF32,
    override public val strategy: PathFillStrategy,
    scissorI32: RectI32,
    override public val blend: BlendPlan = BlendPlan.LegacySrcOverV1,
) : PathDraw {
    override public val coverage: CoveragePlan = CoveragePlan.FullOrScissor
    override public val sample: SamplePlan = SamplePlan.SingleSample
    private val geometrySnapshotF32: PathFillGeometryF32 = geometryF32
    private val scissorSnapshotI32 = scissorI32.copy()

    public fun copyGeometryF32(): PathFillGeometryF32 = geometrySnapshotF32

    /** Legacy-only compatibility view. W5 path draws carry no reconstructed colour. */
    override public val color: ColorF32
        get() = (materialAuthority as? PlanDrawMaterialAuthority.LegacyColorV1)?.copyColorF32()
            ?: throw IllegalStateException("W5 material draws have no legacy colour authority")

    override fun copyPathGeometry(): PathDrawGeometry = PathDrawGeometry.Fill(geometrySnapshotF32)

    override fun copyScissorI32(): RectI32 = scissorSnapshotI32.copy()

    /** Rebind only the occurrence identity; retain the selected geometry and material authority. */
    internal fun withCommandIndexI32(indexI32: Int): PathFillDraw {
        require(indexI32 >= 0)
        return PathFillDraw(indexI32, materialAuthority, geometrySnapshotF32, strategy, scissorSnapshotI32, blend)
    }

    public companion object {
        public fun of(
            commandIndex: Int,
            color: ColorF32,
            geometryF32: PathFillGeometryF32,
            strategy: PathFillStrategy,
            scissorI32: RectI32,
        ): PathFillDraw {
            require(commandIndex >= 0) { "Command index must not be negative" }
            require(!scissorI32.isEmpty) { "Path fill scissor must be non-empty" }
            when (strategy) {
                PathFillStrategy.DirectTriangle -> require(
                    geometryF32.copyDirectTriangleF32OrNull() != null &&
                        geometryF32.copyStencilEdgeFanF32OrNull() == null,
                ) { "Direct path fills require direct-triangle geometry" }
                PathFillStrategy.StencilCover -> require(
                    geometryF32.copyDirectTriangleF32OrNull() == null &&
                        geometryF32.copyStencilEdgeFanF32OrNull() != null,
                ) { "Stencil path fills require edge-fan geometry" }
            }
            return PathFillDraw(commandIndex, PlanDrawMaterialAuthority.LegacyColorV1.of(color), geometryF32, strategy, scissorI32)
        }

        public fun ofMaterial(
            commandIndexI32: Int,
            material: MaterialPlanRef,
            geometryF32: PathFillGeometryF32,
            strategy: PathFillStrategy,
            scissorI32: RectI32,
            blend: BlendPlan = BlendPlan.LegacySrcOverV1,
            coordinates: MaterialCoordinatePlanV1? = null,
            coordinatesV2: MaterialCoordinatePlanV2? = null,
            coordinatesV4: SourceCoordinatesV4? = null,
            composedV5: Boolean = false,
        ): PathFillDraw = ofAuthority(
            commandIndexI32, if (composedV5) PlanDrawMaterialAuthority.MaterialV5(material) else coordinatesV4?.let { PlanDrawMaterialAuthority.MaterialV4(material, it) }
                ?: coordinatesV2?.let { PlanDrawMaterialAuthority.MaterialV2(material, it) }
                ?: PlanDrawMaterialAuthority.MaterialV1(material, coordinates), geometryF32, strategy, scissorI32, blend,
        )

        private fun ofAuthority(
            commandIndex: Int,
            authority: PlanDrawMaterialAuthority,
            geometryF32: PathFillGeometryF32,
            strategy: PathFillStrategy,
            scissorI32: RectI32,
            blend: BlendPlan,
        ): PathFillDraw {
            require(commandIndex >= 0) { "Command index must not be negative" }
            require(!scissorI32.isEmpty) { "Path fill scissor must be non-empty" }
            when (strategy) {
                PathFillStrategy.DirectTriangle -> require(geometryF32.copyDirectTriangleF32OrNull() != null && geometryF32.copyStencilEdgeFanF32OrNull() == null)
                PathFillStrategy.StencilCover -> require(geometryF32.copyDirectTriangleF32OrNull() == null && geometryF32.copyStencilEdgeFanF32OrNull() != null)
            }
            return PathFillDraw(commandIndex, authority, geometryF32, strategy, scissorI32, blend)
        }
    }
}

/** Reissues only the sealed W5 material reference; no Path is reconstructed from another shape. */
public fun PathFillDraw.withMaterialRef(material: MaterialPlanRef): PathFillDraw = PathFillDraw.ofMaterial(
    commandIndex, material, copyGeometryF32(), strategy, copyScissorI32(), blend,
    materialCoordinates, materialCoordinatesV2, (materialAuthority as? PlanDrawMaterialAuthority.MaterialV4)?.coordinates,
    materialAuthority is PlanDrawMaterialAuthority.MaterialV5,
)

/** A sealed W4d stroke draw whose immutable geometry authority remains owned by `:math`. */
public class PathStrokeDraw private constructor(
    override public val commandIndex: Int,
    override public val materialAuthority: PlanDrawMaterialAuthority,
    geometryF32: PathStrokeGeometryF32,
    public val mode: PathStrokeDrawMode,
    public val styleF64: PathStrokeStyleF64,
    scissorI32: RectI32,
    override public val blend: BlendPlan = BlendPlan.SrcOver,
) : PathDraw {
    override public val coverage: CoveragePlan = CoveragePlan.FullOrScissor
    override public val sample: SamplePlan = SamplePlan.SingleSample
    private val geometrySnapshotF32: PathStrokeGeometryF32 = geometryF32
    private val scissorSnapshotI32 = scissorI32.copy()
    override val strategy: PathFillStrategy = pathFillStrategy(geometryF32.copyFillGeometryF32())

    public fun copyGeometryF32(): PathStrokeGeometryF32 = geometrySnapshotF32

    /** Legacy-only compatibility view. W5 path draws carry no reconstructed colour. */
    override public val color: ColorF32
        get() = (materialAuthority as? PlanDrawMaterialAuthority.LegacyColorV1)?.copyColorF32()
            ?: throw IllegalStateException("W5 material draws have no legacy colour authority")

    override fun copyPathGeometry(): PathDrawGeometry = PathDrawGeometry.Stroke(geometrySnapshotF32)

    override fun copyScissorI32(): RectI32 = scissorSnapshotI32.copy()

    /** Rebind only the occurrence identity; retain the selected geometry and material authority. */
    internal fun withCommandIndexI32(indexI32: Int): PathStrokeDraw {
        require(indexI32 >= 0)
        return PathStrokeDraw(indexI32, materialAuthority, geometrySnapshotF32, mode, styleF64, scissorSnapshotI32, blend)
    }

    public companion object {
        public fun of(
            commandIndex: Int,
            color: ColorF32,
            geometryF32: PathStrokeGeometryF32,
            scissorI32: RectI32,
            mode: PathStrokeDrawMode = PathStrokeDrawMode.Stroke,
            styleF64: PathStrokeStyleF64 = PathStrokeStyleF64(
                PathStrokeWidthF64.Hairline, PathStrokeCap.Butt, PathStrokeJoin.Miter, 4.0,
            ),
        ): PathStrokeDraw {
            require(commandIndex >= 0) { "Command index must not be negative" }
            require(!scissorI32.isEmpty) { "Path stroke scissor must be non-empty" }
            pathFillStrategy(geometryF32.copyFillGeometryF32())
            return PathStrokeDraw(commandIndex, PlanDrawMaterialAuthority.LegacyColorV1.of(color), geometryF32, mode, styleF64, scissorI32)
        }

        public fun ofMaterial(
            commandIndexI32: Int,
            material: MaterialPlanRef,
            geometryF32: PathStrokeGeometryF32,
            scissorI32: RectI32,
            mode: PathStrokeDrawMode = PathStrokeDrawMode.Stroke,
            styleF64: PathStrokeStyleF64 = PathStrokeStyleF64(
                PathStrokeWidthF64.Hairline, PathStrokeCap.Butt, PathStrokeJoin.Miter, 4.0,
            ),
            blend: BlendPlan = BlendPlan.SrcOver,
            coordinates: MaterialCoordinatePlanV1? = null,
            coordinatesV2: MaterialCoordinatePlanV2? = null,
            composedV5: Boolean = false,
        ): PathStrokeDraw {
            validateMaterialGeometry(commandIndexI32, geometryF32, scissorI32)
            return PathStrokeDraw(
                commandIndexI32, if (composedV5) PlanDrawMaterialAuthority.MaterialV5(material) else coordinatesV2?.let { PlanDrawMaterialAuthority.MaterialV2(material, it) }
                    ?: PlanDrawMaterialAuthority.MaterialV1(material, coordinates), geometryF32, mode, styleF64, scissorI32, blend,
            )
        }

        internal fun ofMaterialV4(commandIndexI32: Int, material: MaterialPlanRef,
            geometryF32: PathStrokeGeometryF32, scissorI32: RectI32, mode: PathStrokeDrawMode,
            styleF64: PathStrokeStyleF64, blend: BlendPlan, coordinates: SourceCoordinatesV4): PathStrokeDraw {
            validateMaterialGeometry(commandIndexI32, geometryF32, scissorI32)
            return PathStrokeDraw(commandIndexI32, PlanDrawMaterialAuthority.MaterialV4(material, coordinates),
                geometryF32, mode, styleF64, scissorI32, blend)
        }

        private fun validateMaterialGeometry(commandIndexI32: Int, geometryF32: PathStrokeGeometryF32, scissorI32: RectI32) {
            require(commandIndexI32 >= 0) { "Command index must not be negative" }
            require(!scissorI32.isEmpty) { "Path stroke scissor must be non-empty" }
            pathFillStrategy(geometryF32.copyFillGeometryF32())
        }
    }
}

public fun PathStrokeDraw.withMaterialRef(material: MaterialPlanRef): PathStrokeDraw =
    (materialAuthority as? PlanDrawMaterialAuthority.MaterialV4)?.let {
        PathStrokeDraw.ofMaterialV4(commandIndex, material, copyGeometryF32(), copyScissorI32(), mode,
            styleF64, blend, it.coordinates)
    } ?: PathStrokeDraw.ofMaterial(commandIndex, material, copyGeometryF32(), copyScissorI32(), mode,
        styleF64, blend, materialCoordinates, materialCoordinatesV2, materialAuthority is PlanDrawMaterialAuthority.MaterialV5)

private fun pathFillStrategy(geometryF32: PathFillGeometryF32): PathFillStrategy = when {
    geometryF32.copyDirectTriangleF32OrNull() != null && geometryF32.copyStencilEdgeFanF32OrNull() == null ->
        PathFillStrategy.DirectTriangle
    geometryF32.copyDirectTriangleF32OrNull() == null && geometryF32.copyStencilEdgeFanF32OrNull() != null ->
        PathFillStrategy.StencilCover
    else -> throw IllegalArgumentException("Path geometry must select exactly one fill strategy")
}

private fun requirePathRenderGeometryForStrategy(
    geometry: PathDrawGeometry,
    strategy: PathFillStrategy,
) {
    val fillGeometry = when (geometry) {
        is PathDrawGeometry.Fill -> geometry.valueF32
        is PathDrawGeometry.Stroke -> geometry.valueF32.copyFillGeometryF32()
        is PathDrawGeometry.InverseDomainSource -> throw IllegalArgumentException(
            "Inverse-domain source geometry is reserved for a sealed W4e inverse-domain draw",
        )
        PathDrawGeometry.Empty -> throw IllegalArgumentException(
            "Empty path geometry is reserved for a sealed W4e inverse-domain draw",
        )
    }
    require(pathFillStrategy(fillGeometry) == strategy) {
        "Path geometry must select the declared fill strategy"
    }
}

public data class PlanDrawDataResources(
    public val vertex: PlanResourceId,
    public val index: PlanResourceId,
    public val uniform: PlanResourceId,
)

public sealed interface PlanPass {
    public val id: PlanPassId
    public val role: PlanPassRole
    public val ordinal: Int

    public class RenderPass(
        override val ordinal: Int,
        public val target: PlanResourceId,
        draws: List<PlanDraw>,
        public val load: AttachmentLoadPlan,
        public val store: AttachmentStorePlan,
        public val drawDataResources: PlanDrawDataResources? = null,
        public val destinationVersionAfter: DestinationVersionI64? = null,
        /** W6b's captured mask coverage, applied before this W5 material/color pass. */
        public val coverageSource: PlanResourceId? = null,
        /** The typed frozen producer whose mask can expand this transparent source stage. */
        public val w6bMaskSourceBinding: W6bRasterCoverageBindingV1? = null,
        public val plannedCommandId: FramePlannedCommandIdI32? = null,
        /**
         * W5 material-coordinate bridge sealed by W6a construction.  Filter/Picture source
         * renders consume this exact device origin rather than asking the renderer to recover
         * one from a target map.
         */
        materialDeviceOriginI32: Point2I32? = null,
    ) : PlanPass {
        private val materialDeviceOriginSnapshotI32 = materialDeviceOriginI32?.let { Point2I32(it.x, it.y) }
        init { require(w6bMaskSourceBinding == null || coverageSource != null) }
        override val role: PlanPassRole = PlanPassRole.MainRender
        override val id: PlanPassId = checkedPassId(role, ordinal)
        private val storedDraws = immutableList(draws)
        public fun draws(): List<PlanDraw> = storedDraws
        public fun copyMaterialDeviceOriginI32(): Point2I32? = materialDeviceOriginSnapshotI32?.let { Point2I32(it.x, it.y) }
    }

    /** Clears one single-sample hard-edge coverage mask before its atomic producer sequence. */
    public class PathMaskClearPass(
        override val ordinal: Int,
        public val target: PlanResourceId,
        public val atomicGroup: PlanAtomicGroupId,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.PathMaskClear
        override val id: PlanPassId = checkedPassId(role, ordinal)
        public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent
        public val store: AttachmentStorePlan = AttachmentStorePlan.Store
    }

    /** Initializes the finite coverage domain to opaque coverage before ordered clip folds. */
    public class ClipMaskInitialize(
        override public val ordinal: Int,
        public val output: PlanResourceId,
        domainI32: RectI32,
        public val clearCoverageF32: Float = 1f,
        public val atomicGroup: PlanAtomicGroupId,
    ) : PlanPass {
        override public val role: PlanPassRole = PlanPassRole.ClipMaskInitialize
        override public val id: PlanPassId = checkedPassId(role, ordinal)
        private val domainSnapshotI32: RectI32 = domainI32.copy()
        public fun copyDomainI32(): RectI32 = domainSnapshotI32.copy()
    }

    /** Rasterizes one finite clip element into its own scratch attachment. */
    public class ClipMaskProducer(
        override public val ordinal: Int,
        public val target: PlanResourceId,
        public val resolveTarget: PlanResourceId?,
        public val depthStencil: PlanResourceId?,
        public val sampleCountI32: Int,
        geometryF32: ClipGeometryF32,
        public val atomicGroup: PlanAtomicGroupId,
        /** Applies finite producer coverage as the complement inside the initialized clip domain. */
        public val inverseCoverage: Boolean = false,
        /** Preserves analytic AA for rect producers even when their attachment is single-sample. */
        public val antiAlias: Boolean = sampleCountI32 == 4,
    ) : PlanPass {
        override public val role: PlanPassRole = PlanPassRole.ClipMaskProducer
        override public val id: PlanPassId = checkedPassId(role, ordinal)
        private val geometrySnapshotF32: ClipGeometryF32 = geometryF32.copyClipMaskGeometryF32()
        public fun copyGeometryF32(): ClipGeometryF32 = geometrySnapshotF32.copyClipMaskGeometryF32()
    }

    /** Combines the previous accumulator and one scratch producer in insertion order. */
    public class ClipMaskFold(
        override public val ordinal: Int,
        public val previous: PlanResourceId,
        public val source: PlanResourceId,
        public val output: PlanResourceId,
        public val operation: ClipCombineOperation,
        domainI32: RectI32,
        public val atomicGroup: PlanAtomicGroupId,
    ) : PlanPass {
        override public val role: PlanPassRole = PlanPassRole.ClipMaskFold
        override public val id: PlanPassId = checkedPassId(role, ordinal)
        private val domainSnapshotI32: RectI32 = domainI32.copy()
        public fun copyDomainI32(): RectI32 = domainSnapshotI32.copy()
    }

    /** Typed W4d.2 path pass for multisample AA and hard-edge mask production/color cover. */
    public class PathRenderPass(
        override val ordinal: Int,
        public val target: PlanResourceId,
        public val draw: PathRenderDraw,
        public val phase: PathRenderPhase,
        public val drawDataResources: PlanDrawDataResources,
        public val atomicGroup: PlanAtomicGroupId?,
        public val depthStencil: PlanResourceId?,
        public val load: AttachmentLoadPlan,
        public val store: AttachmentStorePlan,
        public val depthStencilAccess: PlanDepthStencilAccess?,
        public val depthStencilLoadStore: PlanDepthStencilLoadStore?,
        public val resolveTarget: PlanResourceId?,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.PathRender
        override val id: PlanPassId = checkedPassId(role, ordinal)
    }

    public class StencilProducer(
        override val ordinal: Int,
        public val target: PlanResourceId,
        public val depthStencil: PlanResourceId,
        public val draw: PathDraw,
        public val drawDataResources: PlanDrawDataResources,
        public val atomicGroup: PlanAtomicGroupId,
        public val load: AttachmentLoadPlan,
        public val store: AttachmentStorePlan,
        public val depthStencilAccess: PlanDepthStencilAccess,
        public val depthStencilLoadStore: PlanDepthStencilLoadStore,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.StencilProducer
        override val id: PlanPassId = checkedPassId(role, ordinal)
    }

    /** W5b geometry-only stencil writer. Source and final blend belong exclusively to its cover. */
    public class StencilGeometryProducerV3 internal constructor(
        override public val ordinal: Int,
        public val target: PlanResourceId,
        public val depthStencil: PlanResourceId,
        public val commandIndexI32: Int,
        geometry: PathDrawGeometry,
        scissorI32: RectI32,
        public val drawDataResources: PlanDrawDataResources,
        public val atomicGroup: PlanAtomicGroupId,
        public val load: AttachmentLoadPlan,
        public val store: AttachmentStorePlan,
    ) : PlanPass {
        override public val role: PlanPassRole = PlanPassRole.StencilProducer
        override public val id: PlanPassId = checkedPassId(role, ordinal)
        private val storedGeometry = geometry
        private val storedScissorI32 = scissorI32.copy()
        public fun copyGeometry(): PathDrawGeometry = storedGeometry
        public fun copyScissorI32(): RectI32 = storedScissorI32.copy()
    }

    public class StencilCover(
        override val ordinal: Int,
        public val target: PlanResourceId,
        public val depthStencil: PlanResourceId,
        public val draw: PathDraw,
        public val drawDataResources: PlanDrawDataResources,
        public val atomicGroup: PlanAtomicGroupId,
        public val load: AttachmentLoadPlan,
        public val store: AttachmentStorePlan,
        public val depthStencilAccess: PlanDepthStencilAccess,
        public val depthStencilLoadStore: PlanDepthStencilLoadStore,
        public val destinationVersionAfter: DestinationVersionI64? = null,
        /** Typed W6b coverage consumed by the color/cover phase, never the pre-mask stencil. */
        public val coverageSource: PlanResourceId? = null,
        public val plannedCommandId: FramePlannedCommandIdI32? = null,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.StencilCover
        override val id: PlanPassId = checkedPassId(role, ordinal)
    }

    public class TextureCopy(
        override val ordinal: Int,
        public val source: PlanResourceId,
        public val destination: PlanResourceId,
        public val destinationVersion: DestinationVersionI64? = null,
        sourceBoundsI32: RectI32? = null,
        destinationOriginI32: Point2I32 = Point2I32.Origin,
        public val bytesPerRowI64: Long? = null,
    ) : PlanPass {
        private val sourceBoundsSnapshotI32 = sourceBoundsI32?.copy()
        private val destinationOriginSnapshotI32 = Point2I32(destinationOriginI32.x, destinationOriginI32.y)
        public fun copySourceBoundsI32(): RectI32? = sourceBoundsSnapshotI32?.copy()
        public fun copyDestinationOriginI32(): Point2I32 =
            Point2I32(destinationOriginSnapshotI32.x, destinationOriginSnapshotI32.y)
        init {
            require(destinationVersion == null || sourceBoundsSnapshotI32?.isEmpty == false &&
                bytesPerRowI64 != null && bytesPerRowI64 >= Math.multiplyExact(sourceBoundsSnapshotI32.width().toLong(), 4L))
        }
        override val role: PlanPassRole = PlanPassRole.TextureCopy
        override val id: PlanPassId = checkedPassId(role, ordinal)
    }

    /** One already-planned layer restore into its immediate parent target. */
    public class LayerComposite(
        override val ordinal: Int,
        public val scopeId: LayerScopeIdI32,
        public val source: PlanResourceId,
        public val destination: PlanResourceId,
        sourceBoundsLayerI32: RectI32,
        destinationOriginParentI32: Point2I32,
        public val restore: LayerRestorePlanV1,
        public val load: AttachmentLoadPlan,
        public val store: AttachmentStorePlan,
        public val destinationVersionAfter: DestinationVersionI64,
    ) : PlanPass {
        private val sourceBoundsLayerSnapshotI32 = sourceBoundsLayerI32.copy()
        private val destinationOriginParentSnapshotI32 = Point2I32(
            destinationOriginParentI32.x,
            destinationOriginParentI32.y,
        )
        init { require(!sourceBoundsLayerSnapshotI32.isEmpty) { "Layer composite source bounds must be non-empty" } }
        public fun copySourceBoundsLayerI32(): RectI32 = sourceBoundsLayerSnapshotI32.copy()
        public fun copyDestinationOriginParentI32(): Point2I32 = Point2I32(
            destinationOriginParentSnapshotI32.x,
            destinationOriginParentSnapshotI32.y,
        )
        override val role: PlanPassRole = PlanPassRole.LayerComposite
        override val id: PlanPassId = checkedPassId(role, ordinal)
    }

    /** Produces a semantically distinct transparent-black input for one captured W6b node. */
    public class FilterSourceClear(
        override val ordinal: Int,
        public val output: PlanResourceId,
        /** The immutable occurrence generation whose transparent-black input this represents. */
        public val boundSourceId: PlanResourceId,
    ) : PlanPass {
        init { require(output != boundSourceId) }
        override val role: PlanPassRole = PlanPassRole.FilterSourceClear
        override val id: PlanPassId = checkedPassId(role, ordinal)
    }

    /**
     * The existing W4 raster authority used to write one W6b raw-coverage texture.
     *
     * This is deliberately a typed dependency, not a new draw operation: [draw] and its W4
     * buffers already belong to the frozen source lane.  A stencil-cover draw causes the
     * coverage pass to replay its existing producer/cover pair in its own target.
     */
    public class W6bRasterCoverageBindingV1(
        public val draw: PlanDraw,
        /** Physical W4/W5 lane that owns this occurrence's frozen draw. */
        public val sourceLaneI32: Int,
        public val drawDataResources: PlanDrawDataResources?,
        public val depthStencil: PlanResourceId? = null,
    ) {
        init {
            require(sourceLaneI32 >= 0)
            val stencil = draw is PathDraw && draw.strategy == PathFillStrategy.StencilCover
            require(stencil == (depthStencil != null)) {
                "Only a frozen stencil-cover draw may bind W6b coverage depth-stencil state."
            }
            require(draw is SolidRectDraw || drawDataResources != null) {
                "A frozen non-rect W6b coverage draw needs its existing W4 buffers."
            }
        }

        public fun withDraw(draw: PlanDraw): W6bRasterCoverageBindingV1 =
            W6bRasterCoverageBindingV1(draw, sourceLaneI32, drawDataResources, depthStencil)
    }

    /** Captures raw geometry/path-effect coverage for one immutable W6b occurrence. */
    public class FilterCoverageSourcePass(
        override val ordinal: Int,
        public val output: PlanResourceId,
        public val occurrence: FilterOccurrenceSourceV1,
        /** The outer drawPicture clip is deferred until its isolated parent composite. */
        public val deferSourceDrawClip: Boolean = false,
        /** Frozen outer transforms/clips for raw coverage; no renderer-side Picture rediscovery. */
        public val pictureCoordinates: PictureW5CoordinatesV1? =
            occurrence.pictureW5CoordinatesOrNull(includeSourceDrawClip = !deferSourceDrawClip),
        /** When present, coverage is a(S), never geometric/cull coverage from [occurrence]. */
        public val sealedAlphaSource: PictureAlphaSourceV1? = null,
        /** Precomputed source-to-output texel transform for [sealedAlphaSource]. */
        public val sealedAlphaSampling: FilterInputSamplingV1? = null,
        /** Exact W4 geometry producer for a direct W6b mask blur, when one is admitted. */
        public val rasterBinding: W6bRasterCoverageBindingV1? = null,
    ) : PlanPass {
        init {
            require(sealedAlphaSource == null || rasterBinding == null)
            require((sealedAlphaSource == null) == (sealedAlphaSampling == null))
        }
        override val role: PlanPassRole = PlanPassRole.FilterCoverageSource
        override val id: PlanPassId = checkedPassId(role, ordinal)

        /** Attaches the existing W4 producer before the graph becomes immutable. */
        public fun withRasterBinding(binding: W6bRasterCoverageBindingV1): FilterCoverageSourcePass {
            require(sealedAlphaSource == null && rasterBinding == null)
            return FilterCoverageSourcePass(ordinal, output, occurrence, deferSourceDrawClip, pictureCoordinates,
                sealedAlphaSource, sealedAlphaSampling, binding)
        }
    }

    /** Preserves original coverage when a later blur style needs both original and blurred inputs. */
    public class FilterCoverageRetainPass(
        override val ordinal: Int,
        public val source: PlanResourceId,
        public val output: PlanResourceId,
        public val sampling: FilterInputSamplingV1? = null,
    ) : PlanPass {
        init { require(source != output) }
        override val role: PlanPassRole = PlanPassRole.FilterCoverageRetain
        override val id: PlanPassId = checkedPassId(role, ordinal)
    }

    /** Opens the only mutable interval of an isolated drawPicture aggregate. */
    public class PictureAggregateBeginPass(
        override val ordinal: Int,
        public val aggregateId: PictureStreamAggregateIdI32,
        public val target: PlanResourceId,
        public val parentTarget: PlanResourceId,
    ) : PlanPass {
        init { require(target != parentTarget) }
        override val role: PlanPassRole = PlanPassRole.PictureAggregateBegin
        override val id: PlanPassId = checkedPassId(role, ordinal)
    }

    /** Seals the current aggregate target as one immutable, versioned premultiplied RGBA source. */
    public class PictureAggregateSealPass(
        override val ordinal: Int,
        public val aggregateId: PictureStreamAggregateIdI32,
        public val aggregateTarget: PlanResourceId,
        public val sealedSource: PlanResourceId,
        public val sourceGenerationI64: Long,
    ) : PlanPass {
        init {
            require(aggregateTarget == sealedSource && sourceGenerationI64 >= 0L)
        }
        override val role: PlanPassRole = PlanPassRole.PictureAggregateSeal
        override val id: PlanPassId = checkedPassId(role, ordinal)
    }

    /**
     * An immutable W5 source hand-off at one captured Picture or Layer occurrence.  Task 3
     * materializes this already ordered source; it must not substitute the frame root or
     * rediscover a Picture/layer source.
     */
    public class PictureSourcePass(
        override val ordinal: Int,
        public val output: PlanResourceId,
        public val sourceSceneCanonicalId: String,
        public val sourceCommandIndexI32: Int,
        /** Exact immutable scene/draw/nesting source; legacy contracts may omit it. */
        public val occurrence: FilterOccurrenceSourceV1? = null,
        /** Mask coverage that must be applied before this Picture's W5 material evaluation. */
        public val coverageSource: PlanResourceId? = null,
        /** The sealed layer color input when this W5 source comes from a layer restore boundary. */
        public val layerInput: PlanResourceId? = null,
        /** Target whose coordinate domain owns this source hand-off. */
        public val parentTarget: PlanResourceId? = null,
        /** Ordered outer Picture transforms, clips, and paints applied to this W5 source. */
        public val pictureCoordinates: PictureW5CoordinatesV1? = null,
        /** Source-order identity when this hand-off is one entry of a Picture aggregate. */
        public val pictureSourceLocator: PictureSourceLocatorV1? = null,
        public val plannedCommandId: FramePlannedCommandIdI32? = null,
        public val aggregateId: PictureStreamAggregateIdI32? = null,
        /** Pre-publication graph-texture source request resolved by the one W5 frame authority. */
        public val graphTextureRequest: GraphTextureSourceRequestV1? = null,
        /** Published W5 material/uniform binding for [graphTextureRequest]. */
        public val graphTextureOperand: GraphTextureSourceOperandV1? = null,
        /** Frozen source-to-output local sampling for layer and graph-texture hand-offs. */
        public val sourceSampling: FilterInputSamplingV1? = null,
    ) : PlanPass {
        init {
            require(sourceSceneCanonicalId.isNotBlank() && sourceCommandIndexI32 >= 0) {
                "Picture source must retain one captured scene occurrence."
            }
            require(occurrence == null || occurrence.sourceCommandIndexI32 == sourceCommandIndexI32)
            require(layerInput == null || occurrence?.layerDescriptor != null) {
                "A layer W5 source must retain its captured layer occurrence."
            }
            require((pictureSourceLocator == null) == (plannedCommandId == null)) {
                "Picture stream source identity must retain both locator and planned command ID."
            }
            require(graphTextureRequest == null || graphTextureOperand == null) {
                "A graph texture source is either pending or published, never both."
            }
            require(graphTextureRequest == null && graphTextureOperand == null || aggregateId != null) {
                "A graph texture source must name its owning Picture aggregate."
            }
        }
        override val role: PlanPassRole = PlanPassRole.PictureSource
        override val id: PlanPassId = checkedPassId(role, ordinal)
    }

    /** Restores an unfiltered typed Picture source before its later siblings. */
    public class PictureComposite(
        override val ordinal: Int,
        public val source: PlanResourceId,
        public val destination: PlanResourceId,
        public val occurrence: FilterOccurrenceSourceV1,
        public val destinationVersionAfter: DestinationVersionI64,
        public val operands: PictureCompositeOperandsV1? = null,
    ) : PlanPass {
        init { require(source != destination) }
        override val role: PlanPassRole = PlanPassRole.PictureComposite
        override val id: PlanPassId = checkedPassId(role, ordinal)
    }

    public class FilterPass(
        override val ordinal: Int,
        inputs: List<PlanResourceId>,
        public val output: PlanResourceId,
        public val evaluationKey: FilterEvaluationKeyV1,
        public val operation: FilterPassOperationV1,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.Filter
        override val id: PlanPassId = checkedPassId(role, ordinal)
        private val storedInputs = immutableList(inputs)
        /** W6d executable recipe and binding contract selected before this graph can freeze. */
        public val frozenSamplingProgram: W6dFrozenProgramBindingV1? =
            selectW6dSamplingProgram(id, operation, storedInputs, output)
        init {
            require(storedInputs.isNotEmpty() && output !in storedInputs) {
                "A filter pass requires distinct input and output resources."
            }
            when (operation) {
                is FilterPassOperationV1.Merge -> require(storedInputs.size == operation.inputSamplings().size) {
                    "Merge inputs and frozen sampling rows must have identical ordered cardinality."
                }
                is FilterPassOperationV1.Blend -> require(storedInputs.size == 2) {
                    "Blend requires frozen background then foreground inputs."
                }
                is FilterPassOperationV1.Morphology -> require(storedInputs.size == 1) {
                    "Morphology requires one frozen source input."
                }
                is FilterPassOperationV1.MatrixConvolution,
                is FilterPassOperationV1.Magnifier,
                is FilterPassOperationV1.Lighting,
                is FilterPassOperationV1.Picture,
                is FilterPassOperationV1.RuntimeImageOpacity,
                -> require(storedInputs.size == 1) { "W6d single-input operation requires one frozen source input." }
                is FilterPassOperationV1.DisplacementMap -> require(storedInputs.size == 2) {
                    "Displacement map requires frozen displacement then source inputs."
                }
                else -> Unit
            }
        }
        public fun inputs(): List<PlanResourceId> = storedInputs
    }

    /** Consumes one frozen W6b result into its immediate parent before later captured work. */
    public class FilterComposite(
        override val ordinal: Int,
        public val source: PlanResourceId,
        public val destination: PlanResourceId,
        public val evaluationKey: FilterEvaluationKeyV1,
        sourceBoundsTargetI32: RectI32,
        destinationOriginParentI32: Point2I32,
        sourceSampleOffsetTargetLocalI32: Point2I32,
        compositeScissorTargetLocalI32: RectI32?,
        public val operation: FilterCompositeOperationV1,
        /** The original layer target replaced by this filtered restore, when applicable. */
        public val replacedLayerSource: PlanResourceId? = null,
        public val destinationVersionAfter: DestinationVersionI64,
    ) : PlanPass {
        private val sourceBoundsTargetSnapshotI32 = sourceBoundsTargetI32.copy()
        private val destinationOriginParentSnapshotI32 = Point2I32(
            destinationOriginParentI32.x,
            destinationOriginParentI32.y,
        )
        private val sourceSampleOffsetSnapshotTargetLocalI32 = Point2I32(
            sourceSampleOffsetTargetLocalI32.x,
            sourceSampleOffsetTargetLocalI32.y,
        )
        private val compositeScissorSnapshotTargetLocalI32 = compositeScissorTargetLocalI32?.copy()
        init {
            require(!sourceBoundsTargetSnapshotI32.isEmpty) { "Filter composite source bounds must be non-empty" }
            require(compositeScissorSnapshotTargetLocalI32?.isEmpty != true) {
                "Filter composite scissor must be non-empty when present"
            }
        }
        public fun copySourceBoundsTargetI32(): RectI32 = sourceBoundsTargetSnapshotI32.copy()
        public fun copyDestinationOriginParentI32(): Point2I32 = Point2I32(
            destinationOriginParentSnapshotI32.x,
            destinationOriginParentSnapshotI32.y,
        )
        /** Final target-local texel relation; native lowering must consume it verbatim. */
        public fun copySourceSampleOffsetTargetLocalI32(): Point2I32 = Point2I32(
            sourceSampleOffsetSnapshotTargetLocalI32.x,
            sourceSampleOffsetSnapshotTargetLocalI32.y,
        )
        /** Null is the plan-sealed no-op terminal; native must not re-evaluate its clip. */
        public fun copyCompositeScissorTargetLocalI32(): RectI32? = compositeScissorSnapshotTargetLocalI32?.copy()
        override val role: PlanPassRole = PlanPassRole.FilterComposite
        override val id: PlanPassId = checkedPassId(role, ordinal)
    }

    public data class ResolvePass(
        override val ordinal: Int,
        public val source: PlanResourceId,
        public val destination: PlanResourceId,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.Resolve
        override val id: PlanPassId = checkedPassId(role, ordinal)
    }

    public data class ReadbackPass(
        override val ordinal: Int,
        public val source: PlanResourceId,
        public val staging: PlanResourceId,
        public val bytesPerRow: Long,
        /** Exact mapped range, excluding unused padding after the final row when specified. */
        public val mappedBytesI64: Long? = null,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.Readback
        override val id: PlanPassId = checkedPassId(role, ordinal)
        init { require(bytesPerRow > 0) { "Readback row bytes must be positive" } }
    }
}

public data class PlanPassDependency(public val before: PlanPassId, public val after: PlanPassId)

private fun checkedPassId(role: PlanPassRole, ordinal: Int): PlanPassId {
    require(ordinal >= 0) { "Pass ordinal must be non-negative" }
    return planPassId(role, ordinal)
}

private fun ClipGeometryF32.copyClipMaskGeometryF32(): ClipGeometryF32 = when (this) {
    is ClipGeometryF32.Rect -> ClipGeometryF32.Rect(copyRectF32())
    is ClipGeometryF32.RRect -> ClipGeometryF32.RRect(copyRRectF32())
    is ClipGeometryF32.Path -> ClipGeometryF32.Path(copyPathGeometryF32())
    ClipGeometryF32.Empty -> ClipGeometryF32.Empty
}

internal fun <T> immutableList(values: List<T>): List<T> = java.util.Collections.unmodifiableList(values.toList())
