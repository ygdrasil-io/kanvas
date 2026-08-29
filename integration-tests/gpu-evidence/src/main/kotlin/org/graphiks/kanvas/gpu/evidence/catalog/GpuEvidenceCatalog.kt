package org.graphiks.kanvas.gpu.evidence.catalog

import org.graphiks.kanvas.gpu.evidence.oracle.CpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.ReferenceRaster
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbSeparableMaskBlurCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbOracleMath
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbSrcOverCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbBitmapNearestCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbSaveLayerSrcOverOpacityCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbGradientCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbLinearGradientStrokeBandsCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbTranslatedTwoStopLinearGradientStrokeCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbTwoStopRadialGradientStrokeCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbThreeStopRadialGradientStrokeCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbTwoStopSweepGradientStrokeCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbUniformScaledTwoStopSweepGradientStrokeCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbUniformScaledThreeStopSweepGradientStrokeCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbUniformScaledTwoStopRadialGradientStrokeCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbUniformScaledThreeStopRadialGradientStrokeCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbThreeStopSweepGradientStrokeCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbRoundCapStrokeCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbRRectCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbClipRRectCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbClipPathCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbClipPathLinearGradientCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbClipPathRadialGradientCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbClipPathRadialStrokeCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbClipPathSweepStrokeCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbClipPathLinearGradientStrokeCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbClipPathDirectTriangleLinearGradientCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbClipPathRRectCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbClipPathDRRectCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbPathFillCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbFractionalRectCoverageCpuOracle
import org.graphiks.kanvas.gpu.evidence.programs.KanvasScenePrograms
import org.graphiks.kanvas.gpu.evidence.programs.RendererRefusalPrograms
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPUCustomRuntimeEffectID

/** Curated catalog deliberately contains only product routes backed by the evaluated commit. */
object GpuEvidenceCatalog {
    val renderCases: List<EvidenceCase> = listOf(
        solidCardStack(),
        boundedRgba8NearestBitmap(),
        separableBlurRect(),
        translucentCardOverlap(),
        scissorOverlay(),
        canvasStateRestoreToCount(),
        boundedSaveLayerSrcOverOpacity(),
        strokeRectOutline(),
        translatedStrokeRectOutline(),
        roundCapStroke(),
        linearGradientLanes(),
        linearGradientThreeStops(),
        radialSwatch(),
        radialGradientThreeStops(),
        sweepDisk(),
        sweepGradientThreeStops(),
        sweepGradientPartialAngle(),
        affineSolidRect(),
        basicPrimitivesValidAlpha(),
        basicPrimitivesOutOfBounds(),
        basicPrimitivesPoints(),
        fractionalAaRectOverlap(),
        affinePathClipColor(),
        scissoredRadialGradient(),
        repeatGradientRendered(),
        gradientStrokeRefusal(),
        linearGradientThreeStopStrokeRect(),
        linearGradientTwoStopTranslatedStrokeRect(),
        linearGradientThreeStopTranslatedStrokeRect(),
        linearGradientTwoStopUniformScaledStrokeRect(),
        linearGradientThreeStopUniformScaledStrokeRect(),
        radialGradientTwoStopStrokeRect(),
        radialGradientTwoStopUniformScaledStrokeRect(),
        radialGradientThreeStopStrokeRect(),
        radialGradientThreeStopUniformScaledStrokeRect(),
sweepGradientTwoStopStrokeRect(),
        sweepGradientTwoStopUniformScaledStrokeRect(),
        sweepGradientThreeStopStrokeRect(),
        sweepGradientThreeStopUniformScaledStrokeRect(),
        scaledSolidRRect(),
        solidDRRectHole(),
        asymmetricSolidRRect(),
        ellipseSolidRRect(),
        asymmetricSolidDRRectHole(),
        clipRRectSolid(),
        clipRRectEllipse(),
        clipRRectTwoBands(),
        transformedClipRRectSolid(),
        clipPathTriangleSolid(),
        clipPathTriangleDifferenceSolid(),
        clipPathConcaveSolid(),
        clipPathTriangleTwoBands(),
        clipPathTranslatedTriangleSolid(),
        clipPathUniformScaledTriangleSolid(),
        clipPathUniformScaledTriangleTwoBands(),
        clipPathTriangleLinearGradient(),
        clipPathTriangleRadialGradient(),
        clipPathTriangleRadialGradientStroke(),
        clipPathTranslatedTriangleRadialGradientStroke(),
        clipPathLocalRadialMatrixStroke(),
        clipPathRightAngleRadialSquareStroke(),
        clipPathSweepSquareStroke(),
        clipPathLocalSweepMatrixStroke(),
        clipPathSweepSquareStrokeEvenOddHole(),
        clipPathSweepSquareStrokeInverseEvenOddHole(),
        clipPathSweepSquareStrokeEvenOddDifferenceHole(),
        clipPathSweepSquareStrokeInverseWindingDifference(),
        clipPathSweepSquareStrokeScaledTranslatedInverseWinding(),
        clipPathSweepButtStrokeEvenOddHole(),
        clipPathSweepSquareStrokeScaledTranslatedInverseEvenOddDifferenceHole(),
        clipPathSweepButtStrokeInverseWinding(),
        clipPathSweepSquareStrokeRightAngleWinding(),
        clipPathSweepButtStrokeWinding(),
        clipPathLinearGradientSquareStrokeWinding(),
        clipPathLinearGradientButtStrokeWinding(),
        clipPathLinearGradientScaledTranslatedButtStrokeWinding(),
        clipPathLinearGradientButtStrokeWindingDifference(),
        clipPathTranslatedTriangleRadialGradient(),
        clipPathTranslatedTriangleLinearGradient(),
        clipPathUniformScaledTriangleLinearGradient(),
        clipPathTriangleDirectTriangleSolid(),
        clipPathTranslatedTriangleDirectTriangleSolid(),
        clipPathTriangleDirectTriangleOrder(),
        clipPathTriangleDirectTriangleLinearGradient(),
        clipPathTranslatedTriangleDirectTriangleLinearGradient(),
        clipPathUniformScaledTriangleDirectTriangleLinearGradient(),
        clipPathSolidRRect(),
        clipPathAsymmetricSolidRRect(),
        clipPathEllipseSolidRRect(),
        clipPathTranslatedSolidRRect(),
        clipPathTranslatedAsymmetricSolidRRect(),
        clipPathTranslatedEllipseSolidRRect(),
        clipPathAxisXTranslatedSolidRRect(),
        clipPathAxisYTranslatedAsymmetricSolidRRect(),
        clipPathNegativeXTranslatedEllipseSolidRRect(),
        clipPathNegativeYTranslatedSolidRRect(),
        clipPathInverseAxisXTranslatedSolidRRect(),
        clipPathInverseAxisYTranslatedAsymmetricSolidRRect(),
        clipPathInverseNegativeXTranslatedEllipseSolidRRect(),
        clipPathInverseNegativeYTranslatedSolidRRect(),
        clipPathSolidDRRect(),
        clipPathAsymmetricSolidDRRect(),
        clipPathEllipseSolidDRRect(),
        clipPathTranslatedSolidDRRect(),
        clipPathTranslatedAsymmetricSolidDRRect(),
        clipPathTranslatedEllipseSolidDRRect(),
        clipPathAxisXTranslatedSolidDRRect(),
        clipPathAxisYTranslatedAsymmetricSolidDRRect(),
        clipPathNegativeXTranslatedEllipseSolidDRRect(),
        clipPathNegativeYTranslatedSolidDRRect(),
        solidTrianglePath(),
        solidConcavePath(),
        evenOddPathHole(),
        windingPathHole(),
        inverseWindingTrianglePath(),
        inverseEvenOddPathHole(),
        evenOddBowTiePath(),
        implicitClosureTrianglePath(),
        translatedTrianglePath(),
        uniformScaledTrianglePath(),
        quadraticPathFill(),
        cubicPathFill(),
        ovalPathFill(),
        circlePathFill(),
    )
    val refusalCases: List<EvidenceCase> = listOf(
        basicPrimitivesEmptyRectRefusal(),
        perspectiveTransformRefusal(),
        mirrorLinearGradientFillRectRefusal(),
        reflectedPathTopologyRefusal(),
        unregisteredRuntimeEffectRefusal(),
        aggregateMemoryBudgetRefusal(),
        boundedSaveLayerRestoreBlendRefusal(),
        boundedBitmapLinearRefusal(),
        imageFilterBlurRefusal(),
    )
    val cases: List<EvidenceCase> = renderCases + refusalCases
    val catalog = EvidenceSceneCatalog(cases.map(EvidenceCase::descriptor))

    /**
     * The execution boundary is intentionally part of the code-level source
     * of truth.  A low-level recorder refusal can document a former bundle,
     * but it cannot become a public Kanvas Surface support claim.
     */
    init {
        require(renderCases.all { it.executionBoundary == EvidenceExecutionBoundary.PublicSurface })
        require(renderCases.all { it.program is org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram })
        require(refusalCases.filter { it.executionBoundary == EvidenceExecutionBoundary.PublicSurface }
            .all { it.program is org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram })
        require(refusalCases.filter { it.executionBoundary == EvidenceExecutionBoundary.HistoricalStandaloneRefusal }
            .all { it.descriptor.expectation is EvidenceExpectation.ShouldRefuse })
        require(cases.map { it.descriptor.id }.toSet().size == cases.size) {
            "GPU evidence catalogue scene ids must be unique across all execution boundaries"
        }
    }

    private fun solidCardStack(): EvidenceCase {
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("solid-card-stack"), "Solid card stack", "Public Kanvas Surface Canvas recording of opaque card rectangles.",
                64, 64, 1L, setOf("solid-rect", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("reference-raster-rect-src-over", 1),
                ComparisonPolicy(0, 100.0, 1, "Exact integer RGBA8 output from opaque SrcOver rectangles."), emptySet(),
            ),
            program = KanvasScenePrograms.solidCardStack(),
            oracle = CpuOracle { width, height -> ReferenceRaster(width, height).apply {
                fillRect(0, 0, width, height, intArrayOf(13, 20, 33, 255))
                srcOver(8, 10, 56, 34, intArrayOf(31, 115, 209, 255))
                srcOver(14, 38, 50, 54, intArrayOf(242, 135, 46, 255))
            }.rgba() },
        )
    }

    private fun boundedRgba8NearestBitmap() = EvidenceCase(
        descriptor = EvidenceSceneDescriptor(
            EvidenceSceneId("bounded-rgba8-nearest-bitmap"),
            "Bounded RGBA8 nearest bitmap",
            "Public Kanvas Surface renders one immutable known-pixel RGBA8 bitmap at an integer destination through the native nearest/clamp texture route.",
            64,
            64,
            1L,
            setOf("bitmap", "rgba8", "nearest", "integer-translation", "src-over", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-bitmap-nearest", 1),
            ComparisonPolicy(0, 100.0, 1, "Independent literal RGBA8 nearest oracle; opaque texels and integer placement require exact bytes."),
            emptySet(),
        ),
        program = KanvasScenePrograms.boundedRgba8NearestBitmap(),
        oracle = SurfaceSrgbBitmapNearestCpuOracle(),
    )

    private fun boundedBitmapLinearRefusal() = EvidenceCase(
        descriptor = EvidenceSceneDescriptor(
            EvidenceSceneId("bounded-bitmap-linear-refusal"),
            "Bounded bitmap linear refusal",
            "Public Kanvas Surface refuses linear bitmap filtering before the nearest-only native sampler can be submitted.",
            64,
            64,
            1L,
            setOf("bitmap", "linear", "refusal", "kanvas-surface"),
            EvidenceExpectation.ShouldRefuse("unsupported.image.sampling_filter"),
            OraclePolicy.StableRefusal,
            null,
            emptySet(),
        ),
        program = KanvasScenePrograms.boundedBitmapLinearRefusal(),
        oracle = null,
    )

    private fun imageFilterBlurRefusal() = EvidenceCase(
        descriptor = EvidenceSceneDescriptor(
            EvidenceSceneId("image-filter-blur-refusal"),
            "Image-filter blur public refusal",
            "Public Kanvas Surface records one RGBA8 impulse with a single CLAMP blur; the prepared product refuses before submission because it cannot materialize the blur intermediates.",
            64,
            64,
            1L,
            setOf("bitmap", "image-filter", "blur", "clamp", "refusal", "kanvas-surface"),
            EvidenceExpectation.ShouldRefuse("unsupported.image.native_binding"),
            OraclePolicy.StableRefusal,
            null,
            emptySet(),
        ),
        program = KanvasScenePrograms.imageFilterBlurRefusal(),
        oracle = null,
    )

    private fun basicPrimitivesValidAlpha() = EvidenceCase(
        descriptor = EvidenceSceneDescriptor(
            EvidenceSceneId("basic-primitives-valid-alpha"), "Basic primitives alpha", "Public Surface clear, drawColor and non-AA rectangle with straight-sRGB alpha inputs.",
            64, 64, 1L, setOf("clear", "draw-color", "solid-rect", "alpha", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-basic-primitives-alpha", 1),
            ComparisonPolicy(1, 99.0, 1, "Independent straight-sRGB premultiplied SrcOver oracle; one RGBA8 rounding unit is tolerated."), emptySet(),
        ),
        program = KanvasScenePrograms.basicPrimitivesValidAlpha(),
        oracle = SurfaceSrgbSrcOverCpuOracle(
            background = intArrayOf(0, 0, 0, 0),
            rectangles = listOf(
                SurfaceSrgbSrcOverCpuOracle.StraightSrgbRectangle(
                    SurfaceSrgbOracleMath.PixelRect(0, 0, 64, 64), intArrayOf(13, 20, 33, 128),
                ),
                SurfaceSrgbSrcOverCpuOracle.StraightSrgbRectangle(
                    SurfaceSrgbOracleMath.PixelRect(8, 12, 56, 52), intArrayOf(242, 135, 46, 128),
                ),
            ),
        ),
    )

    private fun basicPrimitivesOutOfBounds() = EvidenceCase(
        descriptor = EvidenceSceneDescriptor(
            EvidenceSceneId("basic-primitives-out-of-bounds"), "Basic primitive bounds", "Public Surface off-target RRect/DRRect are no-op while a partially out-of-bounds rect is clipped to the target.",
            64, 64, 1L, setOf("solid-rect", "solid-rrect", "solid-drrect", "out-of-bounds", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("reference-raster-basic-primitive-bounds", 1),
            ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 target clipping and no-op semantics."), emptySet(),
        ),
        program = KanvasScenePrograms.basicPrimitivesOutOfBounds(),
        oracle = CpuOracle { width, height -> ReferenceRaster(width, height).apply {
            clear(intArrayOf(13, 20, 33, 255))
            fillRect(-8, -8, 4, 4, intArrayOf(31, 115, 209, 255))
        }.rgba() },
    )

    private fun boundedSaveLayerSrcOverOpacity() = EvidenceCase(
        descriptor = EvidenceSceneDescriptor(
            EvidenceSceneId("bounded-save-layer-src-over-opacity"),
            "Bounded saveLayer SrcOver opacity",
            "Public Kanvas Surface one-layer RGBA8 isolation with two opaque children and a 128/255 SrcOver group-opacity restore.",
            64,
            64,
            1L,
            setOf("save-layer", "src-over", "group-opacity", "solid-rect", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-save-layer-src-over-opacity", 2),
            ComparisonPolicy(2, 100.0, 1, "Independent linear-premultiplied CPU layer oracle; two LSBs cover bounded RGBA8 offscreen and composite quantization."),
            emptySet(),
        ),
        program = KanvasScenePrograms.boundedSaveLayerSrcOverOpacity(),
        oracle = SurfaceSrgbSaveLayerSrcOverOpacityCpuOracle(),
    )

    private fun basicPrimitivesPoints() = EvidenceCase(
        descriptor = EvidenceSceneDescriptor(
            EvidenceSceneId("basic-primitives-points"), "Basic point primitives", "Public Surface POINTS lowering with bounded opaque square footprints and an off-target point.",
            64, 64, 1L, setOf("draw-points", "solid-rect", "out-of-bounds", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("reference-raster-draw-points-squares", 1),
            ComparisonPolicy(0, 100.0, 1, "Exact opaque four-pixel-wide point footprints after target clipping."), emptySet(),
        ),
        program = KanvasScenePrograms.basicPrimitivesPoints(),
        oracle = CpuOracle { width, height -> ReferenceRaster(width, height).apply {
            clear(intArrayOf(13, 20, 33, 255))
            fillRect(8, 10, 12, 14, intArrayOf(242, 135, 46, 255))
            fillRect(28, 30, 32, 34, intArrayOf(242, 135, 46, 255))
            fillRect(60, 60, 64, 64, intArrayOf(242, 135, 46, 255))
        }.rgba() },
    )

    private fun fractionalAaRectOverlap() = EvidenceCase(
        descriptor = EvidenceSceneDescriptor(
            EvidenceSceneId("fractional-aa-rect-overlap"), "Fractional AA rectangle overlap",
            "Public Surface scalar anti-aliasing of two opaque fractional rectangles under an integer scissor clip.",
            64, 64, 1L, setOf("solid-rect", "coverage-aa", "overlap", "scissor", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-fractional-rect-area-coverage", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent exact pixel-area coverage in linear light; one byte rounding tolerance."),
            emptySet(),
        ),
        program = KanvasScenePrograms.fractionalAaRectOverlap(),
        oracle = SurfaceSrgbFractionalRectCoverageCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            rectangles = listOf(
                SurfaceSrgbFractionalRectCoverageCpuOracle.Rectangle(
                    SurfaceSrgbFractionalRectCoverageCpuOracle.DeviceRect(12.5f, 16.5f, 41.5f, 45.5f),
                    intArrayOf(242, 135, 46, 255),
                ),
                SurfaceSrgbFractionalRectCoverageCpuOracle.Rectangle(
                    SurfaceSrgbFractionalRectCoverageCpuOracle.DeviceRect(28.5f, 24.5f, 52.5f, 49.5f),
                    intArrayOf(31, 115, 209, 255),
                ),
            ),
            clip = SurfaceSrgbFractionalRectCoverageCpuOracle.DeviceRect(8f, 8f, 46f, 56f),
        ),
    )

    private fun basicPrimitivesEmptyRectRefusal() = EvidenceCase(
        descriptor = EvidenceSceneDescriptor(
            EvidenceSceneId("basic-primitives-empty-rect-refusal"), "Empty rectangle refusal", "Public Surface empty rectangle is rejected before submission with its current stable geometry diagnostic.",
            64, 64, 1L, setOf("solid-rect", "empty", "refusal", "kanvas-surface"),
            EvidenceExpectation.ShouldRefuse("unsupported.core_primitive.geometry.invalid"), OraclePolicy.StableRefusal, null, emptySet(),
        ),
        program = KanvasScenePrograms.basicPrimitivesEmptyRectRefusal(),
        oracle = null,
    )

    private fun boundedSaveLayerRestoreBlendRefusal() = EvidenceCase(
        descriptor = EvidenceSceneDescriptor(
            EvidenceSceneId("bounded-save-layer-restore-blend-refusal"),
            "Bounded saveLayer restore blend refusal",
            "Public Kanvas Surface refuses a finite single saveLayer with MULTIPLY restore before child encoding or GPU submission.",
            64,
            64,
            1L,
            setOf("save-layer", "restore-blend", "refusal", "kanvas-surface"),
            EvidenceExpectation.ShouldRefuse("unsupported.layer.restore_blend"),
            OraclePolicy.StableRefusal,
            null,
            emptySet(),
        ),
        program = KanvasScenePrograms.boundedSaveLayerRestoreBlendRefusal(),
        oracle = null,
    )

    private fun separableBlurRect(): EvidenceCase {
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("separable-blur-rect"), "Separable blur rectangle", "Public Kanvas Surface normal mask blur recording.",
                64, 64, 1L, setOf("separable-blur", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("surface-srgb-mask-blur-normal-decal", 2),
                ComparisonPolicy(2, 99.0, 1, "Bounded GPU floating-point rounding is allowed after the independently quantized vertical mask stage."), emptySet(),
            ),
            program = KanvasScenePrograms.separableBlurRect(),
            oracle = SurfaceSrgbSeparableMaskBlurCpuOracle(),
        )
    }

    private fun translucentCardOverlap(): EvidenceCase {
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("translucent-card-overlap"), "Translucent card overlap", "Two partially transparent Kanvas Canvas cards exercise SrcOver overlap.",
                64, 64, 1L, setOf("solid-rect", "translucent", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("surface-srgb-linear-premul-src-over", 2),
                ComparisonPolicy(1, 100.0, 1, "Hardware rgba8unorm nearest quantization may differ from the independent linear-premultiplied sRGB oracle by one RGB byte; alpha remains exact and delta 2 remains a failure."), emptySet(),
            ),
            program = KanvasScenePrograms.translucentCardOverlap(),
            oracle = SurfaceSrgbSrcOverCpuOracle(
                background = intArrayOf(13, 20, 33, 255),
                rectangles = listOf(
                    SurfaceSrgbSrcOverCpuOracle.StraightSrgbRectangle(
                        SurfaceSrgbOracleMath.PixelRect(8, 10, 44, 42), intArrayOf(64, 128, 191, 128),
                    ),
                    SurfaceSrgbSrcOverCpuOracle.StraightSrgbRectangle(
                        SurfaceSrgbOracleMath.PixelRect(24, 22, 56, 54), intArrayOf(128, 64, 32, 128),
                    ),
                ),
            ),
        )
    }

    private fun scissorOverlay(): EvidenceCase {
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("scissor-overlay"), "Scissor overlay", "Canvas save/clip/restore blocks constrain rectangle draws.",
                64, 64, 1L, setOf("solid-rect", "scissor", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("reference-raster-scissor-intersections", 1),
                ComparisonPolicy(0, 100.0, 1, "Exact integer RGBA8 output from literal scissor intersections."), emptySet(),
            ),
            program = KanvasScenePrograms.scissorOverlay(),
            oracle = CpuOracle { width, height -> ReferenceRaster(width, height).apply {
                fillRect(0, 0, width, height, intArrayOf(13, 20, 33, 255))
                fillRect(16, 16, 40, 40, intArrayOf(31, 115, 209, 255))
                fillRect(24, 24, 48, 48, intArrayOf(242, 135, 46, 255))
            }.rgba() },
        )
    }

    private fun canvasStateRestoreToCount(): EvidenceCase {
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("canvas-state-restore-to-count"), "Canvas state restoreToCount", "Public Surface save/clip nesting restores the parent clip before an outer post-restore sentinel.",
                64, 64, 1L, setOf("solid-rect", "scissor", "canvas-state", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("reference-raster-canvas-state-restore-to-count", 1),
                ComparisonPolicy(0, 100.0, 1, "Exact integer RGBA8 output from literal parent/child scissor state and post-restore sentinels."), emptySet(),
            ),
            program = KanvasScenePrograms.canvasStateRestoreToCount(),
            oracle = CpuOracle { width, height -> ReferenceRaster(width, height).apply {
                fillRect(0, 0, width, height, intArrayOf(13, 20, 33, 255))
                fillRect(8, 8, 40, 40, intArrayOf(31, 115, 209, 255))
                fillRect(8, 8, 20, 40, intArrayOf(242, 135, 46, 255))
                fillRect(44, 8, 56, 20, intArrayOf(255, 255, 255, 255))
            }.rgba() },
        )
    }

    private fun strokeRectOutline(): EvidenceCase {
        val clear = intArrayOf(13, 20, 33, 255)
        val stroke = intArrayOf(242, 135, 46, 255)
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("stroke-rect-outline"), "Stroke rectangle outline", "A public Kanvas Paint stroke records one anti-alias-disabled rectangle.",
                64, 64, 1L, setOf("solid-rect", "stroke-rect", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("reference-raster-stroke-rect-bands", 1),
                ComparisonPolicy(0, 100.0, 1, "Exact integer RGBA8 output from four literal analytic coverage bands."), emptySet(),
            ),
            program = KanvasScenePrograms.strokeRectOutline(),
            oracle = CpuOracle { width, height -> ReferenceRaster(width, height).apply {
                fillRect(0, 0, width, height, clear)
                fillRect(13, 13, 51, 19, stroke)
                fillRect(13, 45, 51, 51, stroke)
                fillRect(13, 19, 19, 45, stroke)
                fillRect(45, 19, 51, 45, stroke)
            }.rgba() },
        )
    }

    private fun translatedStrokeRectOutline(): EvidenceCase {
        val clear = intArrayOf(13, 20, 33, 255)
        val stroke = intArrayOf(242, 135, 46, 255)
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("translated-stroke-rect-outline"), "Translated stroke rectangle outline",
                "A public Kanvas Paint stroke records one non-AA rectangle under an integral translation.",
                64, 64, 1L, setOf("solid-rect", "stroke-rect", "integer-translation", "kanvas-surface"),
                EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("reference-raster-stroke-rect-bands", 2),
                ComparisonPolicy(0, 100.0, 1, "Exact integer RGBA8 output from four translated analytic coverage bands."), emptySet(),
            ),
            program = KanvasScenePrograms.translatedStrokeRectOutline(),
            oracle = CpuOracle { width, height -> ReferenceRaster(width, height).apply {
                fillRect(0, 0, width, height, clear)
                fillRect(18, 20, 56, 26, stroke)
                fillRect(18, 52, 56, 58, stroke)
                fillRect(18, 26, 24, 52, stroke)
                fillRect(50, 26, 56, 52, stroke)
            }.rgba() },
        )
    }

    private fun roundCapStroke(): EvidenceCase = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("round-cap-stroke"), "Pixel-exact round-cap path stroke", "Public Kanvas Surface non-AA radius-two, integral-grid horizontal path stroke with round caps.",
            32, 32, 1L, setOf("path-stroke", "round-cap", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-round-cap-stroke", 2),
            ComparisonPolicy(0, 100.0, 1, "Independent pixel-center disk oracle for W25's integral-grid radius-two horizontal contract."), emptySet(),
        ),
        KanvasScenePrograms.roundCapStroke(),
        SurfaceSrgbRoundCapStrokeCpuOracle(6.0, 26.0, 16.0, 2.0, intArrayOf(255, 0, 0, 255)),
    )

    private fun linearGradientLanes(): EvidenceCase {
        val bounds = SurfaceSrgbGradientCpuOracle.Rect(8f, 16f, 56f, 48f)
        val stops = listOf(
            SurfaceSrgbGradientCpuOracle.Stop(0f, 255, 56, 56),
            SurfaceSrgbGradientCpuOracle.Stop(1f, 56, 112, 255),
        )
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("linear-gradient-lanes"), "Linear gradient lanes", "Public Kanvas Surface clamp linear gradient across a literal rectangle.",
                64, 64, 1L, setOf("linear-gradient", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("surface-srgb-gradient-linear-clamp", 2),
                ComparisonPolicy(1, 100.0, 1, "Independent sRGB decode, linear-premultiplied interpolation, and sRGB target storage."), emptySet(),
            ),
            program = KanvasScenePrograms.linearGradientLanes(),
            oracle = SurfaceSrgbGradientCpuOracle.linear(
                bounds,
                SurfaceSrgbGradientCpuOracle.Point(8.5f, 32.5f),
                SurfaceSrgbGradientCpuOracle.Point(55.5f, 32.5f),
                stops,
            ),
        )
    }

    private fun radialSwatch(): EvidenceCase {
        val bounds = SurfaceSrgbGradientCpuOracle.Rect(8f, 8f, 56f, 56f)
        val stops = listOf(
            SurfaceSrgbGradientCpuOracle.Stop(0f, 255, 232, 72),
            SurfaceSrgbGradientCpuOracle.Stop(1f, 48, 80, 192),
        )
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("radial-swatch"), "Radial swatch", "Public Kanvas Surface clamp radial gradient across a literal swatch.",
                64, 64, 1L, setOf("radial-gradient", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("surface-srgb-gradient-radial-clamp", 2),
                ComparisonPolicy(1, 100.0, 1, "Independent sRGB decode, linear-premultiplied interpolation, and sRGB target storage."), emptySet(),
            ),
            program = KanvasScenePrograms.radialSwatch(),
            oracle = SurfaceSrgbGradientCpuOracle.radial(
                bounds,
                SurfaceSrgbGradientCpuOracle.Point(32.5f, 32.5f),
                23.5f,
                stops,
            ),
        )
    }

    private fun radialGradientThreeStops(): EvidenceCase {
        val bounds = SurfaceSrgbGradientCpuOracle.Rect(8f, 8f, 56f, 56f)
        val stops = listOf(
            SurfaceSrgbGradientCpuOracle.Stop(0f, 255, 232, 72),
            SurfaceSrgbGradientCpuOracle.Stop(.5f, 64, 208, 144),
            SurfaceSrgbGradientCpuOracle.Stop(1f, 48, 80, 192),
        )
        return EvidenceCase(
            EvidenceSceneDescriptor(
                EvidenceSceneId("radial-gradient-three-stops"),
                "Radial gradient three stops",
                "Public Kanvas Surface CorePrimitive FillRect renders an identity clamp radial gradient with three ordered opaque stops.",
                64, 64, 1L, setOf("radial-gradient", "three-stops", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("surface-srgb-gradient-radial-clamp", 2),
                ComparisonPolicy(1, 100.0, 1, "Independent sRGB decode, linear-premultiplied interpolation, and sRGB target storage."), emptySet(),
            ),
            KanvasScenePrograms.radialGradientThreeStops(),
            SurfaceSrgbGradientCpuOracle.radial(
                bounds,
                SurfaceSrgbGradientCpuOracle.Point(32.5f, 32.5f),
                23.5f,
                stops,
            ),
        )
    }

    private fun sweepDisk(): EvidenceCase {
        val bounds = SurfaceSrgbGradientCpuOracle.Rect(8f, 8f, 56f, 56f)
        val stops = listOf(
            SurfaceSrgbGradientCpuOracle.Stop(0f, 255, 64, 64),
            SurfaceSrgbGradientCpuOracle.Stop(1f, 64, 208, 255),
        )
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("sweep-disk"), "Sweep disk", "Public Kanvas Surface clamp sweep gradient across a literal disk swatch.",
                64, 64, 1L, setOf("sweep-gradient", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("surface-srgb-gradient-sweep-clamp", 2),
                ComparisonPolicy(1, 100.0, 1, "Independent sRGB decode, linear-premultiplied interpolation, and sRGB target storage."), emptySet(),
            ),
            program = KanvasScenePrograms.sweepDisk(),
            oracle = SurfaceSrgbGradientCpuOracle.sweep(
                bounds,
                SurfaceSrgbGradientCpuOracle.Point(32.5f, 32.5f),
                0f,
                360f,
                stops,
            ),
        )
    }

    private fun sweepGradientThreeStops(): EvidenceCase {
        val bounds = SurfaceSrgbGradientCpuOracle.Rect(8f, 8f, 56f, 56f)
        val stops = listOf(
            SurfaceSrgbGradientCpuOracle.Stop(0f, 255, 64, 64),
            SurfaceSrgbGradientCpuOracle.Stop(.5f, 56, 220, 120),
            SurfaceSrgbGradientCpuOracle.Stop(1f, 64, 112, 255),
        )
        return EvidenceCase(
            EvidenceSceneDescriptor(
                EvidenceSceneId("sweep-gradient-three-stops"),
                "Sweep gradient three stops",
                "Public Kanvas Surface CorePrimitive FillRect renders an identity clamp sweep gradient with three ordered opaque stops.",
                64, 64, 1L, setOf("sweep-gradient", "three-stops", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("surface-srgb-gradient-sweep-clamp", 2),
                ComparisonPolicy(1, 100.0, 1, "Independent sRGB decode, linear-premultiplied interpolation, and sRGB target storage."), emptySet(),
            ),
            KanvasScenePrograms.sweepGradientThreeStops(),
            SurfaceSrgbGradientCpuOracle.sweep(
                bounds,
                SurfaceSrgbGradientCpuOracle.Point(32.5f, 32.5f),
                0f,
                360f,
                stops,
            ),
        )
    }

    private fun linearGradientThreeStops(): EvidenceCase {
        val bounds = SurfaceSrgbGradientCpuOracle.Rect(8f, 16f, 56f, 48f)
        val stops = listOf(
            SurfaceSrgbGradientCpuOracle.Stop(0f, 255, 56, 56),
            SurfaceSrgbGradientCpuOracle.Stop(.5f, 56, 220, 120),
            SurfaceSrgbGradientCpuOracle.Stop(1f, 56, 112, 255),
        )
        return EvidenceCase(
            EvidenceSceneDescriptor(
                EvidenceSceneId("linear-gradient-three-stops"),
                "Linear gradient three stops",
                "Public Kanvas Surface CorePrimitive FillRect renders an identity clamp linear gradient with three ordered opaque stops.",
                64, 64, 1L, setOf("linear-gradient", "three-stops", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("surface-srgb-gradient-linear-clamp", 2),
                ComparisonPolicy(1, 100.0, 1, "Independent sRGB decode, linear-premultiplied interpolation, and sRGB target storage."), emptySet(),
            ),
            KanvasScenePrograms.linearGradientThreeStops(),
            SurfaceSrgbGradientCpuOracle.linear(
                bounds,
                SurfaceSrgbGradientCpuOracle.Point(8.5f, 32.5f),
                SurfaceSrgbGradientCpuOracle.Point(55.5f, 32.5f),
                stops,
            ),
        )
    }

    private fun sweepGradientPartialAngle() = gradientCase(
        "sweep-gradient-partial-angle", "Sweep gradient partial angle", "Public Kanvas Surface clamp sweep gradient across a partial angle range.",
        setOf("sweep-gradient", "kanvas-surface"), "surface-srgb-gradient-sweep-clamp", KanvasScenePrograms.sweepGradientPartialAngle(),
        SurfaceSrgbGradientCpuOracle.sweep(
            SurfaceSrgbGradientCpuOracle.Rect(8f, 8f, 56f, 56f), SurfaceSrgbGradientCpuOracle.Point(32.5f, 32.5f), 45f, 315f,
            listOf(SurfaceSrgbGradientCpuOracle.Stop(0f, 255, 64, 64), SurfaceSrgbGradientCpuOracle.Stop(1f, 64, 208, 255)),
        ),
    )

    private fun affineSolidRect() = EvidenceCase(
        EvidenceSceneDescriptor(EvidenceSceneId("affine-solid-rect"), "Affine solid rectangle", "Public Kanvas Canvas affine transform over an opaque solid rectangle.",
            64, 64, 1L, setOf("solid-rect", "affine", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("reference-raster-affine-solid-rect", 1), ComparisonPolicy(0, 100.0, 1, "Exact RGBA8 output from hand-derived inverse affine pixel-center membership."), emptySet()),
        KanvasScenePrograms.affineSolidRect(),
        CpuOracle { width, height -> ByteArray(width * height * 4).also { pixels ->
            for (y in 0 until height) for (x in 0 until width) {
                val localX = x + .5 - .25 * (y + .5) - 4.0
                if (localX >= 8.0 && localX < 40.0 && y + .5 >= 16.0 && y + .5 < 48.0) {
                    val offset = (y * width + x) * 4
                    pixels[offset] = 242.toByte(); pixels[offset + 1] = 135.toByte(); pixels[offset + 2] = 46.toByte(); pixels[offset + 3] = 255.toByte()
                }
            }
        } },
    )

    private fun affinePathClipColor() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("affine-path-clip-color"),
            "Affine hard path clip color",
            "Public Kanvas Surface captures a finite non-singular non-uniform axis scale plus translation path clip, resets the CTM, then colors its device-space coverage.",
            64, 64, 1L, setOf("affine", "clip-path", "draw-color", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-affine-path-clip-pixel-center", 1),
            ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent device-space affine rectangle clip membership."), emptySet(),
        ),
        KanvasScenePrograms.affinePathClipColor(),
        SurfaceSrgbClipPathCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            contours = listOf(
                SurfaceSrgbClipPathCpuOracle.Contour(listOf(
                    SurfaceSrgbClipPathCpuOracle.Point(8f, 5f),
                    SurfaceSrgbClipPathCpuOracle.Point(44f, 5f),
                    SurfaceSrgbClipPathCpuOracle.Point(44f, 29f),
                    SurfaceSrgbClipPathCpuOracle.Point(8f, 29f),
                )),
            ),
            draws = listOf(
                SurfaceSrgbClipPathCpuOracle.OpaqueRect(0f, 0f, 64f, 64f, intArrayOf(242, 135, 46, 255)),
            ),
        ),
    )

    private fun transformedClipRRectSolid() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("transformed-clip-rrect-solid"),
            "Transformed hard RRect clip",
            "Public Kanvas Surface freezes a finite scale-and-translation RRect clip in device space before an opaque rectangle consumer resets the CTM.",
            64, 64, 1L, setOf("clip-rrect", "scale-translate", "analytic-coverage", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-transformed-rrect-clip-pixel-center", 1),
            ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent device-space RRect membership."),
            emptySet(),
        ),
        KanvasScenePrograms.transformedClipRRectSolid(),
        SurfaceSrgbClipRRectCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            clip = SurfaceSrgbClipRRectCpuOracle.DeviceRRect(10f, 12f, 58f, 48f, 6f, 3f),
            draws = listOf(
                SurfaceSrgbClipRRectCpuOracle.OpaqueRect(0f, 0f, 64f, 64f, intArrayOf(31, 115, 209, 255)),
            ),
        ),
    )

    private fun perspectiveTransformRefusal() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("perspective-transform-refusal"),
            "Perspective transform refusal",
            "Public Kanvas Surface refuses a general perspective matrix before native frame submission.",
            64, 64, 1L, setOf("transform", "perspective", "kanvas-surface", "refusal"),
            EvidenceExpectation.ShouldRefuse("unsupported.transform.perspective"), OraclePolicy.StableRefusal, null, emptySet(),
        ),
        KanvasScenePrograms.perspectiveTransformRefusal(),
        null,
    )

    /** MIRROR is deliberately not a partial implementation of the bounded REPEAT route. */
    private fun mirrorLinearGradientFillRectRefusal() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("mirror-linear-gradient-fillrect-refusal"),
            "Mirror linear-gradient FillRect refusal",
            "Public Kanvas Surface refuses a MIRROR linear gradient before native frame submission; only bounded REPEAT is supported on this route.",
            64, 64, 1L, setOf("linear-gradient", "mirror", "fill-rect", "kanvas-surface", "refusal"),
            EvidenceExpectation.ShouldRefuse("unsupported.material.gradient_tile_mode_unsupported"), OraclePolicy.StableRefusal, null, emptySet(),
        ),
        KanvasScenePrograms.mirrorLinearGradientFillRectRefusal(),
        null,
    )

    private fun scissoredRadialGradient() = gradientCase(
        "scissored-radial-gradient", "Scissored radial gradient", "Public Kanvas Surface clamp radial gradient constrained by a literal non-AA clip.",
        setOf("radial-gradient", "scissor", "kanvas-surface"), "surface-srgb-gradient-radial-clamp", KanvasScenePrograms.scissoredRadialGradient(),
        SurfaceSrgbGradientCpuOracle.radial(
            SurfaceSrgbGradientCpuOracle.Rect(20f, 12f, 52f, 52f), SurfaceSrgbGradientCpuOracle.Point(32.5f, 32.5f), 23.5f,
            listOf(SurfaceSrgbGradientCpuOracle.Stop(0f, 255, 232, 72), SurfaceSrgbGradientCpuOracle.Stop(1f, 48, 80, 192)),
        ),
    )

    private fun gradientCase(id: String, title: String, description: String, tags: Set<String>, oracleId: String, program: org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram, oracle: CpuOracle) = EvidenceCase(
        EvidenceSceneDescriptor(EvidenceSceneId(id), title, description, 64, 64, 1L, tags, EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu(oracleId, 2), ComparisonPolicy(1, 100.0, 1, "Independent sRGB decode, linear-premultiplied interpolation, and sRGB target storage."), emptySet()), program, oracle,
    )

    private fun unregisteredRuntimeEffectRefusal() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("custom-runtime-effect-unregistered-refusal"), "Unregistered runtime effect refusal", "Unknown custom runtime effect refuses before submission.",
            16, 16, 1L, setOf("runtime-effect", "refusal"),
            EvidenceExpectation.ShouldRefuse("unsupported.runtime_effect.custom_wgsl_not_registered"), OraclePolicy.StableRefusal, null, emptySet(),
        ),
        RendererRefusalPrograms.unregisteredRuntimeEffect(GPUCustomRuntimeEffectID("gpu-evidence.unregistered")),
        null,
        EvidenceExecutionBoundary.HistoricalStandaloneRefusal,
    )

    private fun aggregateMemoryBudgetRefusal(): EvidenceCase = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("aggregate-memory-budget-refusal"), "Aggregate memory budget refusal", "A valid full-target SolidRect draw refuses during product recording under a one-byte budget.",
            16, 16, 1L, setOf("solid-rect", "frame-memory", "refusal"),
            EvidenceExpectation.ShouldRefuse("unsupported.frame_memory.aggregate_budget_exceeded"), OraclePolicy.StableRefusal, null, emptySet(),
        ),
        RendererRefusalPrograms.aggregateMemoryBudget(),
        null,
        EvidenceExecutionBoundary.HistoricalStandaloneRefusal,
    )

    private fun repeatGradientRendered() = gradientCase(
        "repeat-gradient-refusal", "Repeat linear gradient", "Public Kanvas Surface repeat linear gradient across negative coordinates and a post-first-cycle pixel.",
        setOf("linear-gradient", "repeat", "kanvas-surface"), "surface-srgb-gradient-linear-repeat", KanvasScenePrograms.repeatGradientRefusal(),
        SurfaceSrgbGradientCpuOracle.linearRepeat(
            SurfaceSrgbGradientCpuOracle.Rect(0f, 16f, 64f, 48f),
            SurfaceSrgbGradientCpuOracle.Point(16.5f, 32.5f), SurfaceSrgbGradientCpuOracle.Point(31.5f, 32.5f),
            listOf(SurfaceSrgbGradientCpuOracle.Stop(0f, 255, 56, 56), SurfaceSrgbGradientCpuOracle.Stop(1f, 56, 112, 255)),
        ),
    )
    private fun gradientStrokeRefusal() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("gradient-stroke-refusal"), "Gradient stroke rectangle", "Public Kanvas Surface clamp linear-gradient rectangle stroke rendered as four analytic bands.",
            64, 64, 1L, setOf("stroke-rect", "linear-gradient", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-gradient-linear-clamp-stroke-bands", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent four-band coverage with device-coordinate clamp linear-gradient sampling and one-LSB RGBA8 tolerance."), emptySet(),
        ),
        KanvasScenePrograms.gradientStrokeRefusal(),
        SurfaceSrgbLinearGradientStrokeBandsCpuOracle(
            listOf(
                SurfaceSrgbLinearGradientStrokeBandsCpuOracle.Rect(6, 14, 58, 18),
                SurfaceSrgbLinearGradientStrokeBandsCpuOracle.Rect(6, 46, 58, 50),
                SurfaceSrgbLinearGradientStrokeBandsCpuOracle.Rect(6, 18, 10, 46),
                SurfaceSrgbLinearGradientStrokeBandsCpuOracle.Rect(54, 18, 58, 46),
            ),
            SurfaceSrgbLinearGradientStrokeBandsCpuOracle.Point(8.5, 32.5),
            SurfaceSrgbLinearGradientStrokeBandsCpuOracle.Point(55.5, 32.5),
            SurfaceSrgbLinearGradientStrokeBandsCpuOracle.Stop(0.0, 255, 56, 56),
            SurfaceSrgbLinearGradientStrokeBandsCpuOracle.Stop(1.0, 56, 112, 255),
        ),
    )

    private fun linearGradientThreeStopStrokeRect() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("linear-gradient-three-stop-stroke-rect"), "Linear gradient three-stop stroke rectangle",
            "Public Kanvas Surface renders a non-AA identity CLAMP three-stop LinearGradient rectangle stroke as four typed analytic bands.",
            64, 64, 1L, setOf("stroke-rect", "linear-gradient", "three-stops", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-gradient-linear-clamp-three-stop-stroke-bands", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent four-band device coverage with three-stop sRGB decode, linear-premultiplied interpolation, and sRGB RGBA8 storage."), emptySet(),
        ),
        KanvasScenePrograms.linearGradientThreeStopStrokeRect(),
        SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle(
            listOf(
                SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle.Rect(6, 14, 58, 18),
                SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle.Rect(6, 46, 58, 50),
                SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle.Rect(6, 18, 10, 46),
                SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle.Rect(54, 18, 58, 46),
            ),
            SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle.Point(8.5, 32.5),
            SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle.Point(55.5, 32.5),
            listOf(
                SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle.Stop(0.0, 255, 56, 56),
                SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle.Stop(.5, 56, 220, 120),
                SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle.Stop(1.0, 56, 112, 255),
            ),
        ),
    )

    private fun linearGradientTwoStopTranslatedStrokeRect() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("linear-gradient-two-stop-translated-stroke-rect"), "Translated linear gradient two-stop stroke rectangle",
            "Public Kanvas Surface translates a bounded non-AA CLAMP two-stop LinearGradient rectangle stroke by integral device pixels.",
            64, 64, 1L, setOf("stroke-rect", "linear-gradient", "two-stops", "translation", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-gradient-linear-clamp-two-stop-translated-stroke-bands", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent four-band device coverage and separately translated two-stop sRGB linear-gradient axis."), emptySet(),
        ),
        KanvasScenePrograms.linearGradientTwoStopTranslatedStrokeRect(),
        SurfaceSrgbTranslatedTwoStopLinearGradientStrokeCpuOracle(
            listOf(
                SurfaceSrgbTranslatedTwoStopLinearGradientStrokeCpuOracle.Rect(8, 17, 60, 21),
                SurfaceSrgbTranslatedTwoStopLinearGradientStrokeCpuOracle.Rect(8, 49, 60, 53),
                SurfaceSrgbTranslatedTwoStopLinearGradientStrokeCpuOracle.Rect(8, 21, 12, 49),
                SurfaceSrgbTranslatedTwoStopLinearGradientStrokeCpuOracle.Rect(56, 21, 60, 49),
            ),
            SurfaceSrgbTranslatedTwoStopLinearGradientStrokeCpuOracle.Point(8.5, 32.5),
            SurfaceSrgbTranslatedTwoStopLinearGradientStrokeCpuOracle.Point(55.5, 32.5),
            SurfaceSrgbTranslatedTwoStopLinearGradientStrokeCpuOracle.Vector(2.0, 3.0),
            SurfaceSrgbTranslatedTwoStopLinearGradientStrokeCpuOracle.Stop(255, 56, 56),
            SurfaceSrgbTranslatedTwoStopLinearGradientStrokeCpuOracle.Stop(56, 112, 255),
        ),
    )

    private fun linearGradientThreeStopTranslatedStrokeRect() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("linear-gradient-three-stop-translated-stroke-rect"), "Translated linear gradient three-stop stroke rectangle",
            "Public Kanvas Surface renders a bounded non-AA CLAMP three-stop LinearGradient stroke under integral translation.",
            64, 64, 1L, setOf("stroke-rect", "linear-gradient", "three-stops", "translation", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-gradient-linear-clamp-three-stop-translated-stroke-bands", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent four translated device bands with pixel-center three-stop sRGB interpolation."), emptySet(),
        ),
        KanvasScenePrograms.linearGradientThreeStopTranslatedStrokeRect(),
        SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle(
            listOf(
                SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle.Rect(8, 17, 60, 21),
                SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle.Rect(8, 49, 60, 53),
                SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle.Rect(8, 21, 12, 49),
                SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle.Rect(56, 21, 60, 49),
            ),
            SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle.Point(10.5, 35.5),
            SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle.Point(57.5, 35.5),
            listOf(
                SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle.Stop(0.0, 255, 56, 56),
                SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle.Stop(.5, 56, 220, 120),
                SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle.Stop(1.0, 56, 112, 255),
            ),
        ),
    )

    private fun linearGradientTwoStopUniformScaledStrokeRect() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("linear-gradient-two-stop-uniform-scaled-stroke-rect"), "Uniform-scaled linear gradient two-stop stroke rectangle",
            "Public Kanvas Surface renders a bounded non-AA CLAMP two-stop LinearGradient rectangle stroke under positive integral uniform scale and translation.",
            64, 64, 1L, setOf("stroke-rect", "linear-gradient", "two-stops", "uniform-scale", "translation", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-gradient-linear-clamp-two-stop-uniform-scaled-stroke-bands", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent scaled four-band device coverage with uniformly scaled and translated two-stop sRGB linear-gradient axis."), emptySet(),
        ),
        KanvasScenePrograms.linearGradientTwoStopUniformScaledStrokeRect(),
        SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle(
            listOf(
                SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle.Rect(16, 18, 60, 22),
                SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle.Rect(16, 50, 60, 54),
                SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle.Rect(16, 22, 20, 50),
                SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle.Rect(56, 22, 60, 50),
            ),
            SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle.Point(8.0, 16.0),
            SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle.Point(28.0, 16.0),
            2,
            SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle.Point(2.0, 4.0),
            SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle.Stop(255, 56, 56),
            SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle.Stop(56, 112, 255),
        ),
    )

    private fun linearGradientThreeStopUniformScaledStrokeRect() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("linear-gradient-three-stop-uniform-scaled-stroke-rect"), "Uniform-scaled linear gradient three-stop stroke rectangle",
            "Public Kanvas Surface renders a bounded non-AA CLAMP three-stop LinearGradient rectangle stroke under positive integral uniform scale and translation.",
            64, 64, 1L, setOf("stroke-rect", "linear-gradient", "three-stops", "uniform-scale", "translation", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-gradient-linear-clamp-three-stop-uniform-scaled-stroke-bands", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent scaled four-band device coverage with uniformly scaled and translated three-stop sRGB linear-gradient axis."), emptySet(),
        ),
        KanvasScenePrograms.linearGradientThreeStopUniformScaledStrokeRect(),
        SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle(
            listOf(
                SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle.Rect(16, 18, 60, 22),
                SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle.Rect(16, 50, 60, 54),
                SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle.Rect(16, 22, 20, 50),
                SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle.Rect(56, 22, 60, 50),
            ),
            SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle.Point(8.0, 16.0),
            SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle.Point(28.0, 16.0),
            2,
            SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle.Point(2.0, 4.0),
            SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle.Stop(255, 56, 56),
            SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle.Stop(56, 220, 120),
            SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle.Stop(56, 112, 255),
        ),
    )

    private fun radialGradientTwoStopStrokeRect() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("radial-gradient-two-stop-stroke-rect"), "Radial gradient two-stop stroke rectangle",
            "Public Kanvas Surface renders the bounded non-AA identity CLAMP two-stop radial rectangle stroke.",
            64, 64, 1L, setOf("stroke-rect", "radial-gradient", "two-stops", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-gradient-radial-clamp-two-stop-stroke-bands", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent four-band pixel-center radial interpolation and sRGB RGBA8 storage."), emptySet(),
        ),
        KanvasScenePrograms.radialGradientTwoStopStrokeRect(),
        SurfaceSrgbTwoStopRadialGradientStrokeCpuOracle(
            listOf(
                SurfaceSrgbTwoStopRadialGradientStrokeCpuOracle.Rect(6, 14, 58, 18),
                SurfaceSrgbTwoStopRadialGradientStrokeCpuOracle.Rect(6, 46, 58, 50),
                SurfaceSrgbTwoStopRadialGradientStrokeCpuOracle.Rect(6, 18, 10, 46),
                SurfaceSrgbTwoStopRadialGradientStrokeCpuOracle.Rect(54, 18, 58, 46),
            ),
            SurfaceSrgbTwoStopRadialGradientStrokeCpuOracle.Point(32.5, 32.5), 23.5,
            intArrayOf(255, 56, 56, 255), intArrayOf(56, 112, 255, 255),
        ),
    )

    private fun radialGradientThreeStopStrokeRect() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("radial-gradient-three-stop-stroke-rect"), "Radial gradient three-stop stroke rectangle",
            "Public Kanvas Surface renders the bounded non-AA identity CLAMP three-stop radial rectangle stroke.",
            64, 64, 1L, setOf("stroke-rect", "radial-gradient", "three-stops", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-gradient-radial-clamp-three-stop-stroke-bands", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent four-band pixel-center three-stop radial interpolation and sRGB RGBA8 storage."), emptySet(),
        ),
        KanvasScenePrograms.radialGradientThreeStopStrokeRect(),
        SurfaceSrgbThreeStopRadialGradientStrokeCpuOracle(
            listOf(
                SurfaceSrgbThreeStopRadialGradientStrokeCpuOracle.Rect(6, 14, 58, 18),
                SurfaceSrgbThreeStopRadialGradientStrokeCpuOracle.Rect(6, 46, 58, 50),
                SurfaceSrgbThreeStopRadialGradientStrokeCpuOracle.Rect(6, 18, 10, 46),
                SurfaceSrgbThreeStopRadialGradientStrokeCpuOracle.Rect(54, 18, 58, 46),
            ),
            SurfaceSrgbThreeStopRadialGradientStrokeCpuOracle.Point(32.5, 32.5), 23.5,
            listOf(
                SurfaceSrgbThreeStopRadialGradientStrokeCpuOracle.Stop(0.0, 255, 56, 56),
                SurfaceSrgbThreeStopRadialGradientStrokeCpuOracle.Stop(.5, 56, 220, 120),
                SurfaceSrgbThreeStopRadialGradientStrokeCpuOracle.Stop(1.0, 56, 112, 255),
            ),
        ),
    )

    private fun sweepGradientTwoStopStrokeRect() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("sweep-gradient-two-stop-stroke-rect"), "Sweep gradient two-stop stroke rectangle",
            "Public Kanvas Surface renders the bounded non-AA identity CLAMP two-stop sweep rectangle stroke.",
            64, 64, 1L, setOf("stroke-rect", "sweep-gradient", "two-stops", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-gradient-sweep-clamp-two-stop-stroke-bands", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent four-band pixel-center sweep-angle interpolation and sRGB RGBA8 storage."), emptySet(),
        ),
        KanvasScenePrograms.sweepGradientTwoStopStrokeRect(),
        SurfaceSrgbTwoStopSweepGradientStrokeCpuOracle(
            listOf(
                SurfaceSrgbTwoStopSweepGradientStrokeCpuOracle.Rect(6, 14, 58, 18),
                SurfaceSrgbTwoStopSweepGradientStrokeCpuOracle.Rect(6, 46, 58, 50),
                SurfaceSrgbTwoStopSweepGradientStrokeCpuOracle.Rect(6, 18, 10, 46),
                SurfaceSrgbTwoStopSweepGradientStrokeCpuOracle.Rect(54, 18, 58, 46),
            ),
            SurfaceSrgbTwoStopSweepGradientStrokeCpuOracle.Point(32.5, 32.5),
            intArrayOf(255, 56, 56, 255), intArrayOf(56, 112, 255, 255),
        ),
    )

    private fun sweepGradientTwoStopUniformScaledStrokeRect() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("sweep-gradient-two-stop-uniform-scaled-stroke-rect"), "Uniform-scaled sweep gradient two-stop stroke rectangle",
            "Public Kanvas Surface renders a bounded non-AA full sweep CLAMP two-stop rectangle stroke under integral uniform scale and translation.",
            64, 64, 1L, setOf("stroke-rect", "sweep-gradient", "two-stops", "uniform-scale", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-gradient-sweep-clamp-two-stop-uniform-scaled-stroke-bands", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent device-center sweep-angle interpolation and scaled four-band coverage."), emptySet(),
        ),
        KanvasScenePrograms.sweepGradientTwoStopUniformScaledStrokeRect(),
        SurfaceSrgbUniformScaledTwoStopSweepGradientStrokeCpuOracle(
            listOf(
                SurfaceSrgbUniformScaledTwoStopSweepGradientStrokeCpuOracle.Rect(16, 18, 60, 22),
                SurfaceSrgbUniformScaledTwoStopSweepGradientStrokeCpuOracle.Rect(16, 50, 60, 54),
                SurfaceSrgbUniformScaledTwoStopSweepGradientStrokeCpuOracle.Rect(16, 22, 20, 50),
                SurfaceSrgbUniformScaledTwoStopSweepGradientStrokeCpuOracle.Rect(56, 22, 60, 50),
            ),
            SurfaceSrgbUniformScaledTwoStopSweepGradientStrokeCpuOracle.Point(38.0, 32.0),
            intArrayOf(255, 56, 56, 255), intArrayOf(56, 112, 255, 255),
        ),
    )

    private fun radialGradientTwoStopUniformScaledStrokeRect() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("radial-gradient-two-stop-uniform-scaled-stroke-rect"), "Uniform-scaled radial gradient two-stop stroke rectangle",
            "Public Kanvas Surface renders a bounded non-AA CLAMP two-stop radial rectangle stroke under positive integral uniform scale and translation.",
            64, 64, 1L, setOf("stroke-rect", "radial-gradient", "two-stops", "uniform-scale", "translation", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-gradient-radial-clamp-two-stop-uniform-scaled-stroke-bands", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent scaled four-band device coverage with uniformly scaled and translated two-stop sRGB radial center and radius."), emptySet(),
        ),
        KanvasScenePrograms.radialGradientTwoStopUniformScaledStrokeRect(),
        SurfaceSrgbUniformScaledTwoStopRadialGradientStrokeCpuOracle(
            listOf(
                SurfaceSrgbUniformScaledTwoStopRadialGradientStrokeCpuOracle.Rect(16, 18, 60, 22),
                SurfaceSrgbUniformScaledTwoStopRadialGradientStrokeCpuOracle.Rect(16, 50, 60, 54),
                SurfaceSrgbUniformScaledTwoStopRadialGradientStrokeCpuOracle.Rect(16, 22, 20, 50),
                SurfaceSrgbUniformScaledTwoStopRadialGradientStrokeCpuOracle.Rect(56, 22, 60, 50),
            ),
            SurfaceSrgbUniformScaledTwoStopRadialGradientStrokeCpuOracle.Point(38.0, 32.0),
            16.0,
            intArrayOf(255, 56, 56, 255), intArrayOf(56, 112, 255, 255),
        ),
    )

    private fun radialGradientThreeStopUniformScaledStrokeRect() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("radial-gradient-three-stop-uniform-scaled-stroke-rect"), "Uniform-scaled radial gradient three-stop stroke rectangle",
            "Public Kanvas Surface renders a bounded non-AA CLAMP three-stop radial rectangle stroke under positive integral uniform scale and translation.",
            64, 64, 1L, setOf("stroke-rect", "radial-gradient", "three-stops", "uniform-scale", "translation", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-gradient-radial-clamp-three-stop-uniform-scaled-stroke-bands", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent scaled four-band device coverage with uniformly scaled and translated three-stop sRGB radial center and radius."), emptySet(),
        ),
        KanvasScenePrograms.radialGradientThreeStopUniformScaledStrokeRect(),
        SurfaceSrgbUniformScaledThreeStopRadialGradientStrokeCpuOracle(
            listOf(
                SurfaceSrgbUniformScaledThreeStopRadialGradientStrokeCpuOracle.Rect(16, 18, 60, 22),
                SurfaceSrgbUniformScaledThreeStopRadialGradientStrokeCpuOracle.Rect(16, 50, 60, 54),
                SurfaceSrgbUniformScaledThreeStopRadialGradientStrokeCpuOracle.Rect(16, 22, 20, 50),
                SurfaceSrgbUniformScaledThreeStopRadialGradientStrokeCpuOracle.Rect(56, 22, 60, 50),
            ),
            SurfaceSrgbUniformScaledThreeStopRadialGradientStrokeCpuOracle.Point(38.0, 32.0),
            16.0,
            listOf(
                SurfaceSrgbUniformScaledThreeStopRadialGradientStrokeCpuOracle.Stop(0.0, 255, 56, 56),
                SurfaceSrgbUniformScaledThreeStopRadialGradientStrokeCpuOracle.Stop(.5, 56, 220, 120),
                SurfaceSrgbUniformScaledThreeStopRadialGradientStrokeCpuOracle.Stop(1.0, 56, 112, 255),
            ),
        ),
    )

    private fun sweepGradientThreeStopStrokeRect() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("sweep-gradient-three-stop-stroke-rect"), "Sweep gradient three-stop stroke rectangle",
            "Public Kanvas Surface renders the bounded non-AA identity CLAMP three-stop sweep rectangle stroke.",
            64, 64, 1L, setOf("stroke-rect", "sweep-gradient", "three-stops", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-gradient-sweep-clamp-three-stop-stroke-bands", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent four-band pixel-center three-stop sweep-angle interpolation and sRGB RGBA8 storage."), emptySet(),
        ),
        KanvasScenePrograms.sweepGradientThreeStopStrokeRect(),
        SurfaceSrgbThreeStopSweepGradientStrokeCpuOracle(
            listOf(
                SurfaceSrgbThreeStopSweepGradientStrokeCpuOracle.Rect(6, 14, 58, 18),
                SurfaceSrgbThreeStopSweepGradientStrokeCpuOracle.Rect(6, 46, 58, 50),
                SurfaceSrgbThreeStopSweepGradientStrokeCpuOracle.Rect(6, 18, 10, 46),
                SurfaceSrgbThreeStopSweepGradientStrokeCpuOracle.Rect(54, 18, 58, 46),
            ),
            SurfaceSrgbThreeStopSweepGradientStrokeCpuOracle.Point(32.5, 32.5),
            listOf(
                SurfaceSrgbThreeStopSweepGradientStrokeCpuOracle.Stop(0.0, 255, 56, 56),
                SurfaceSrgbThreeStopSweepGradientStrokeCpuOracle.Stop(.5, 56, 220, 120),
                SurfaceSrgbThreeStopSweepGradientStrokeCpuOracle.Stop(1.0, 56, 112, 255),
            ),
        ),
    )

    private fun sweepGradientThreeStopUniformScaledStrokeRect() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("sweep-gradient-three-stop-uniform-scaled-stroke-rect"), "Uniform-scaled sweep gradient three-stop stroke rectangle",
            "Public Kanvas Surface renders a bounded non-AA full sweep CLAMP three-stop rectangle stroke under positive integral uniform scale and translation.",
            64, 64, 1L, setOf("stroke-rect", "sweep-gradient", "three-stops", "uniform-scale", "translation", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-gradient-sweep-clamp-three-stop-uniform-scaled-stroke-bands", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent scaled four-band device coverage with uniformly scaled and translated three-stop sRGB sweep center."), emptySet(),
        ),
        KanvasScenePrograms.sweepGradientThreeStopUniformScaledStrokeRect(),
        SurfaceSrgbUniformScaledThreeStopSweepGradientStrokeCpuOracle(
            listOf(
                SurfaceSrgbUniformScaledThreeStopSweepGradientStrokeCpuOracle.Rect(16, 18, 60, 22),
                SurfaceSrgbUniformScaledThreeStopSweepGradientStrokeCpuOracle.Rect(16, 50, 60, 54),
                SurfaceSrgbUniformScaledThreeStopSweepGradientStrokeCpuOracle.Rect(16, 22, 20, 50),
                SurfaceSrgbUniformScaledThreeStopSweepGradientStrokeCpuOracle.Rect(56, 22, 60, 50),
            ),
            SurfaceSrgbUniformScaledThreeStopSweepGradientStrokeCpuOracle.Point(38.0, 32.0),
            listOf(
                SurfaceSrgbUniformScaledThreeStopSweepGradientStrokeCpuOracle.Stop(0.0, 255, 56, 56),
                SurfaceSrgbUniformScaledThreeStopSweepGradientStrokeCpuOracle.Stop(.5, 56, 220, 120),
                SurfaceSrgbUniformScaledThreeStopSweepGradientStrokeCpuOracle.Stop(1.0, 56, 112, 255),
            ),
        ),
    )

    private fun scaledSolidRRect() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("scaled-solid-rrect"), "Scaled solid rounded rectangle", "Public Kanvas Surface non-AA solid rounded rectangle under a pure axis scale.",
            64, 64, 1L, setOf("solid-rrect", "scale", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-rrect-pixel-center", 1),
            ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent analytic pixel-center RRect membership."), emptySet(),
        ),
        KanvasScenePrograms.scaledSolidRRect(),
        SurfaceSrgbRRectCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            fill = intArrayOf(242, 135, 46, 255),
            outer = SurfaceSrgbRRectCpuOracle.DeviceRRect(16f, 16f, 48f, 48f, 8f, 4f),
        ),
    )

    private fun solidDRRectHole() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("solid-drrect-hole"), "Solid double rounded rectangle hole", "Public Kanvas Surface non-AA solid double rounded rectangle with an inner hole.",
            64, 64, 1L, setOf("solid-drrect", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-rrect-pixel-center", 1),
            ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent analytic pixel-center RRect membership."), emptySet(),
        ),
        KanvasScenePrograms.solidDRRectHole(),
        SurfaceSrgbRRectCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            fill = intArrayOf(31, 115, 209, 255),
            outer = SurfaceSrgbRRectCpuOracle.DeviceRRect(8f, 8f, 56f, 56f, 8f, 8f),
            inner = SurfaceSrgbRRectCpuOracle.DeviceRRect(20f, 20f, 44f, 44f, 4f, 4f),
        ),
    )

    private fun asymmetricSolidRRect() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("asymmetric-solid-rrect"), "Asymmetric solid rounded rectangle", "Public Kanvas Surface non-AA solid rounded rectangle with independent corner radii.",
            64, 64, 1L, setOf("solid-rrect", "asymmetric-radii", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-rrect-pixel-center", 2),
            ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent per-corner analytic pixel-center RRect membership."), emptySet(),
        ),
        KanvasScenePrograms.asymmetricSolidRRect(),
        SurfaceSrgbRRectCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            fill = intArrayOf(242, 135, 46, 255),
            outer = SurfaceSrgbRRectCpuOracle.DeviceRRect(
                8f, 8f, 56f, 56f,
                topLeft = SurfaceSrgbRRectCpuOracle.CornerRadii(4f, 8f),
                topRight = SurfaceSrgbRRectCpuOracle.CornerRadii(10f, 4f),
                bottomRight = SurfaceSrgbRRectCpuOracle.CornerRadii(8f, 12f),
                bottomLeft = SurfaceSrgbRRectCpuOracle.CornerRadii(6f, 3f),
            ),
        ),
    )

    private fun ellipseSolidRRect() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("ellipse-solid-rrect"), "Ellipse solid rounded rectangle", "Public Kanvas Surface non-AA solid rounded rectangle whose radii form an ellipse.",
            64, 64, 1L, setOf("solid-rrect", "ellipse", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-rrect-pixel-center", 2),
            ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent per-corner analytic pixel-center RRect membership."), emptySet(),
        ),
        KanvasScenePrograms.ellipseSolidRRect(),
        SurfaceSrgbRRectCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            fill = intArrayOf(31, 115, 209, 255),
            outer = SurfaceSrgbRRectCpuOracle.DeviceRRect(
                12f, 20f, 52f, 44f,
                SurfaceSrgbRRectCpuOracle.CornerRadii(20f, 12f),
            ),
        ),
    )

    private fun asymmetricSolidDRRectHole() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("asymmetric-solid-drrect-hole"), "Asymmetric solid double rounded rectangle hole", "Public Kanvas Surface non-AA asymmetric double rounded rectangle with an asymmetric inner hole.",
            64, 64, 1L, setOf("solid-drrect", "asymmetric-radii", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-rrect-pixel-center", 2),
            ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent per-corner analytic pixel-center RRect membership."), emptySet(),
        ),
        KanvasScenePrograms.asymmetricSolidDRRectHole(),
        SurfaceSrgbRRectCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            fill = intArrayOf(31, 115, 209, 255),
            outer = SurfaceSrgbRRectCpuOracle.DeviceRRect(
                6f, 8f, 58f, 56f,
                topLeft = SurfaceSrgbRRectCpuOracle.CornerRadii(4f, 8f),
                topRight = SurfaceSrgbRRectCpuOracle.CornerRadii(10f, 4f),
                bottomRight = SurfaceSrgbRRectCpuOracle.CornerRadii(8f, 12f),
                bottomLeft = SurfaceSrgbRRectCpuOracle.CornerRadii(6f, 3f),
            ),
            inner = SurfaceSrgbRRectCpuOracle.DeviceRRect(
                20f, 20f, 44f, 44f,
                topLeft = SurfaceSrgbRRectCpuOracle.CornerRadii(2f, 4f),
                topRight = SurfaceSrgbRRectCpuOracle.CornerRadii(6f, 2f),
                bottomRight = SurfaceSrgbRRectCpuOracle.CornerRadii(4f, 6f),
                bottomLeft = SurfaceSrgbRRectCpuOracle.CornerRadii(3f, 2f),
            ),
        ),
    )

    private fun clipRRectSolid() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-rrect-solid"), "Solid hard RRect clip", "Public Kanvas Surface hard non-AA uniform RRect clip over an opaque rectangle.",
            64, 64, 1L, setOf("clip-rrect", "solid-rect", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-rrect-pixel-center", 1),
            ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent hard pixel-center RRect clip membership."), emptySet(),
        ),
        KanvasScenePrograms.clipRRectSolid(),
        SurfaceSrgbClipRRectCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            clip = SurfaceSrgbClipRRectCpuOracle.DeviceRRect(8f, 8f, 56f, 56f, 8f, 8f),
            draws = listOf(SurfaceSrgbClipRRectCpuOracle.OpaqueRect(0f, 0f, 64f, 64f, intArrayOf(31, 115, 209, 255))),
        ),
    )

    private fun clipRRectEllipse() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-rrect-ellipse"), "Ellipse hard RRect clip", "Public Kanvas Surface hard non-AA elliptical RRect clip over an opaque rectangle.",
            64, 64, 1L, setOf("clip-rrect", "ellipse", "solid-rect", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-rrect-pixel-center", 1),
            ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent hard pixel-center RRect clip membership."), emptySet(),
        ),
        KanvasScenePrograms.clipRRectEllipse(),
        SurfaceSrgbClipRRectCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            clip = SurfaceSrgbClipRRectCpuOracle.DeviceRRect(12f, 20f, 52f, 44f, 20f, 12f),
            draws = listOf(SurfaceSrgbClipRRectCpuOracle.OpaqueRect(0f, 0f, 64f, 64f, intArrayOf(242, 135, 46, 255))),
        ),
    )

    private fun clipRRectTwoBands() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-rrect-two-bands"), "Two-band hard RRect clip", "Public Kanvas Surface hard non-AA uniform RRect clip reused by ordered opaque blue and orange rectangles.",
            64, 64, 1L, setOf("clip-rrect", "solid-rect", "paint-order", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-rrect-pixel-center", 1),
            ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent hard pixel-center RRect clip membership and paint order."), emptySet(),
        ),
        KanvasScenePrograms.clipRRectTwoBands(),
        SurfaceSrgbClipRRectCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            clip = SurfaceSrgbClipRRectCpuOracle.DeviceRRect(8f, 8f, 56f, 56f, 8f, 8f),
            draws = listOf(
                SurfaceSrgbClipRRectCpuOracle.OpaqueRect(0f, 0f, 64f, 64f, intArrayOf(31, 115, 209, 255)),
                SurfaceSrgbClipRRectCpuOracle.OpaqueRect(32f, 0f, 64f, 64f, intArrayOf(242, 135, 46, 255)),
            ),
        ),
    )

    private fun clipPathTriangleSolid() = clipPathCase(
        id = "clip-path-triangle-solid",
        title = "Solid hard triangle path clip",
        description = "Public Kanvas Surface hard non-AA winding triangle path clip over an opaque rectangle.",
        program = KanvasScenePrograms.clipPathTriangleSolid(),
        contours = listOf(
            listOf(
                SurfaceSrgbClipPathCpuOracle.Point(8f, 8f),
                SurfaceSrgbClipPathCpuOracle.Point(56f, 8f),
                SurfaceSrgbClipPathCpuOracle.Point(8f, 55f),
            ),
        ),
        draws = listOf(
            SurfaceSrgbClipPathCpuOracle.OpaqueRect(0f, 0f, 64f, 64f, intArrayOf(242, 135, 46, 255)),
        ),
    )

    private fun clipPathTriangleDifferenceSolid() = clipPathCase(
        id = "clip-path-triangle-difference-solid",
        title = "Solid hard triangle path difference clip",
        description = "Public Kanvas Surface hard non-AA winding triangle path difference leaves the complement for an opaque rectangle.",
        program = KanvasScenePrograms.clipPathTriangleDifferenceSolid(),
        contours = listOf(
            listOf(
                SurfaceSrgbClipPathCpuOracle.Point(8f, 8f),
                SurfaceSrgbClipPathCpuOracle.Point(56f, 8f),
                SurfaceSrgbClipPathCpuOracle.Point(8f, 55f),
            ),
        ),
        draws = listOf(
            SurfaceSrgbClipPathCpuOracle.OpaqueRect(0f, 0f, 64f, 64f, intArrayOf(242, 135, 46, 255)),
        ),
        clipInverted = true,
        extraTags = setOf("difference"),
        comparisonRationale = "Exact opaque RGBA8 output from independent hard pixel-center winding path difference membership and paint order.",
    )

    private fun clipPathConcaveSolid() = clipPathCase(
        id = "clip-path-concave-solid",
        title = "Solid hard concave path clip",
        description = "Public Kanvas Surface hard non-AA winding concave path clip with a literal notch.",
        program = KanvasScenePrograms.clipPathConcaveSolid(),
        contours = listOf(
            listOf(
                SurfaceSrgbClipPathCpuOracle.Point(8f, 8f),
                SurfaceSrgbClipPathCpuOracle.Point(56f, 8f),
                SurfaceSrgbClipPathCpuOracle.Point(56f, 24f),
                SurfaceSrgbClipPathCpuOracle.Point(32f, 24f),
                SurfaceSrgbClipPathCpuOracle.Point(32f, 40f),
                SurfaceSrgbClipPathCpuOracle.Point(56f, 40f),
                SurfaceSrgbClipPathCpuOracle.Point(56f, 56f),
                SurfaceSrgbClipPathCpuOracle.Point(8f, 56f),
            ),
        ),
        draws = listOf(
            SurfaceSrgbClipPathCpuOracle.OpaqueRect(0f, 0f, 64f, 64f, intArrayOf(31, 115, 209, 255)),
        ),
    )

    private fun clipPathTriangleTwoBands() = clipPathCase(
        id = "clip-path-triangle-two-bands",
        title = "Two-band hard triangle path clip",
        description = "Public Kanvas Surface hard non-AA triangle path clip reused by ordered opaque blue and orange rectangles.",
        program = KanvasScenePrograms.clipPathTriangleTwoBands(),
        contours = listOf(
            listOf(
                SurfaceSrgbClipPathCpuOracle.Point(8f, 8f),
                SurfaceSrgbClipPathCpuOracle.Point(56f, 8f),
                SurfaceSrgbClipPathCpuOracle.Point(8f, 55f),
            ),
        ),
        draws = listOf(
            SurfaceSrgbClipPathCpuOracle.OpaqueRect(0f, 0f, 64f, 64f, intArrayOf(31, 115, 209, 255)),
            SurfaceSrgbClipPathCpuOracle.OpaqueRect(32f, 0f, 64f, 64f, intArrayOf(242, 135, 46, 255)),
        ),
    )

    private fun clipPathTranslatedTriangleSolid() = clipPathCase(
        id = "clip-path-translated-triangle-solid",
        title = "Translated solid hard triangle path clip",
        description = "Public Kanvas Surface hard non-AA winding triangle path clip captured after translation.",
        program = KanvasScenePrograms.clipPathTranslatedTriangleSolid(),
        contours = listOf(
            listOf(
                SurfaceSrgbClipPathCpuOracle.Point(10f, 8f),
                SurfaceSrgbClipPathCpuOracle.Point(58f, 8f),
                SurfaceSrgbClipPathCpuOracle.Point(10f, 55f),
            ),
        ),
        draws = listOf(
            SurfaceSrgbClipPathCpuOracle.OpaqueRect(2f, 0f, 66f, 64f, intArrayOf(242, 135, 46, 255)),
        ),
    )

    private fun clipPathUniformScaledTriangleSolid() = clipPathCase(
        id = "clip-path-uniform-scaled-triangle-solid",
        title = "Uniformly scaled solid hard triangle path clip",
        description = "Public Kanvas Surface hard non-AA winding triangle path clip captured after positive uniform scaling.",
        program = KanvasScenePrograms.clipPathUniformScaledTriangleSolid(),
        contours = listOf(
            listOf(
                SurfaceSrgbClipPathCpuOracle.Point(14f, 10f),
                SurfaceSrgbClipPathCpuOracle.Point(50f, 10f),
                SurfaceSrgbClipPathCpuOracle.Point(14f, 45.25f),
            ),
        ),
        draws = listOf(
            SurfaceSrgbClipPathCpuOracle.OpaqueRect(8f, 4f, 56f, 52f, intArrayOf(31, 115, 209, 255)),
        ),
    )

    private fun clipPathUniformScaledTriangleTwoBands() = clipPathCase(
        id = "clip-path-uniform-scaled-triangle-two-bands",
        title = "Uniformly scaled two-band hard triangle path clip",
        description = "Public Kanvas Surface hard non-AA uniformly scaled triangle path clip reused by ordered opaque blue and orange rectangles.",
        program = KanvasScenePrograms.clipPathUniformScaledTriangleTwoBands(),
        contours = listOf(
            listOf(
                SurfaceSrgbClipPathCpuOracle.Point(14f, 10f),
                SurfaceSrgbClipPathCpuOracle.Point(50f, 10f),
                SurfaceSrgbClipPathCpuOracle.Point(14f, 45.25f),
            ),
        ),
        draws = listOf(
            SurfaceSrgbClipPathCpuOracle.OpaqueRect(8f, 4f, 56f, 52f, intArrayOf(31, 115, 209, 255)),
            SurfaceSrgbClipPathCpuOracle.OpaqueRect(32f, 4f, 56f, 52f, intArrayOf(242, 135, 46, 255)),
        ),
    )

    private fun clipPathTriangleLinearGradient() = clipPathLinearGradientCase(
        "clip-path-triangle-linear-gradient", "Clamp linear gradient hard triangle path clip",
        KanvasScenePrograms.clipPathTriangleLinearGradient(),
        listOf(clipPoint(8f, 8f), clipPoint(56f, 8f), clipPoint(8f, 55f)),
        0f, 0f, 64f, 64f, 8f, 8f, 56f, 8f,
    )

    private fun clipPathTranslatedTriangleLinearGradient() = clipPathLinearGradientCase(
        "clip-path-translated-triangle-linear-gradient", "Translated clamp linear gradient hard triangle path clip",
        KanvasScenePrograms.clipPathTranslatedTriangleLinearGradient(),
        listOf(clipPoint(10f, 8f), clipPoint(58f, 8f), clipPoint(10f, 55f)),
        2f, 0f, 66f, 64f, 10f, 8f, 58f, 8f,
    )

    private fun clipPathUniformScaledTriangleLinearGradient() = clipPathLinearGradientCase(
        "clip-path-uniform-scaled-triangle-linear-gradient", "Uniformly scaled clamp linear gradient hard triangle path clip",
        KanvasScenePrograms.clipPathUniformScaledTriangleLinearGradient(),
        listOf(clipPoint(14f, 10f), clipPoint(50f, 10f), clipPoint(14f, 45.25f)),
        8f, 4f, 56f, 52f, 14f, 10f, 50f, 10f,
    )

    private fun clipPathTriangleDirectTriangleSolid() = clipPathDirectTriangleCase(
        id = "clip-path-triangle-direct-triangle-solid",
        title = "Solid direct triangle inside hard path clip",
        description = "Public Kanvas Surface hard non-AA path clip with one solid DirectTriangles drawPath consumer.",
        program = KanvasScenePrograms.clipPathTriangleDirectTriangleSolid(),
        contour = listOf(clipPoint(8f, 8f), clipPoint(56f, 8f), clipPoint(8f, 55f)),
        draws = listOf(directTriangle(4f, 4.25f, 60f, 12f, 12f, 60f, intArrayOf(242, 135, 46, 255))),
    )

    private fun clipPathTranslatedTriangleDirectTriangleSolid() = clipPathDirectTriangleCase(
        id = "clip-path-translated-triangle-direct-triangle-solid",
        title = "Translated solid direct triangle inside hard path clip",
        description = "Public Kanvas Surface translated hard non-AA path clip with one device-space solid DirectTriangles drawPath consumer.",
        program = KanvasScenePrograms.clipPathTranslatedTriangleDirectTriangleSolid(),
        contour = listOf(clipPoint(10f, 8f), clipPoint(58f, 8f), clipPoint(10f, 55f)),
        draws = listOf(directTriangle(6f, 4.25f, 62f, 12f, 14f, 60f, intArrayOf(31, 115, 209, 255))),
    )

    private fun clipPathTriangleDirectTriangleOrder() = clipPathDirectTriangleCase(
        id = "clip-path-triangle-direct-triangle-order",
        title = "Ordered direct triangles inside hard path clip",
        description = "Public Kanvas Surface hard non-AA path clip with two ordered solid DirectTriangles drawPath consumers.",
        program = KanvasScenePrograms.clipPathTriangleDirectTriangleOrder(),
        contour = listOf(clipPoint(8f, 8f), clipPoint(56f, 8f), clipPoint(8f, 55f)),
        draws = listOf(
            directTriangle(4f, 4.25f, 60f, 12f, 12f, 60f, intArrayOf(31, 115, 209, 255)),
            directTriangle(20f, 8f, 56f, 8f, 20f, 44f, intArrayOf(242, 135, 46, 255)),
        ),
    )

    private fun clipPathTriangleDirectTriangleLinearGradient() = clipPathDirectTriangleLinearGradientCase(
        id = "clip-path-triangle-direct-triangle-linear-gradient",
        title = "Clamp gradient direct triangle inside hard path clip",
        program = KanvasScenePrograms.clipPathTriangleDirectTriangleLinearGradient(),
        contour = listOf(clipPoint(8f, 8f), clipPoint(56f, 8f), clipPoint(8f, 55f)),
        triangle = directTriangleGradient(4f, 4.25f, 60f, 12f, 12f, 60f),
        start = SurfaceSrgbGradientCpuOracle.Point(20f, 19.3f),
        end = SurfaceSrgbGradientCpuOracle.Point(20f, 23.3f),
    )

    private fun clipPathTriangleRadialGradient() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-triangle-radial-gradient"),
            "Clamp radial gradient inside hard path clip",
            "Public Kanvas Surface hard non-AA path clip with one opaque two-stop sRGB clamp radial-gradient FillRect consumer.",
            64,
            64,
            1L,
            setOf("clip-path", "radial-gradient", "hard-clip", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-radial-gradient-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent double-precision oracle; one RGBA8 LSB covers bounded f32 WGSL radial-distance and target-encoding rounding."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathTriangleRadialGradient(),
        SurfaceSrgbClipPathRadialGradientCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            points = listOf(clipPoint(8f, 8f), clipPoint(56f, 8f), clipPoint(8f, 55f)),
            drawBounds = SurfaceSrgbGradientCpuOracle.Rect(0f, 0f, 64f, 64f),
            center = SurfaceSrgbGradientCpuOracle.Point(24.5f, 24.5f),
            radius = 24f,
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
        ),
    )

    private fun clipPathTriangleRadialGradientStroke() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-triangle-radial-stroke"),
            "Radial gradient stroke inside hard path clip",
            "Public Kanvas Surface hard non-AA Winding triangle clip with one opaque two-stop clamp radial butt/miter path stroke.",
            64,
            64,
            1L,
            setOf("clip-path", "radial-gradient", "stroke", "hard-clip", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-radial-stroke-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent pixel-center winding clip, butt stroke distance and linear-light radial interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathTriangleRadialGradientStroke(),
        SurfaceSrgbClipPathRadialStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            points = listOf(
                SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(7.25, 6.25),
                SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(30.25, 6.25),
                SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(7.25, 29.25),
            ),
            strokeStart = SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(5.25, 8.25),
            strokeEnd = SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(21.25, 20.25),
            strokeWidth = 4.0,
            center = SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(16.0, 16.0),
            radius = 16.0,
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
        ),
    )

    private fun clipPathTranslatedTriangleRadialGradientStroke() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-translated-triangle-radial-stroke"),
            "Translated radial gradient stroke inside hard path clip",
            "Public Kanvas Surface translated hard non-AA Winding triangle clip with one opaque two-stop clamp radial butt/miter path stroke.",
            64,
            64,
            1L,
            setOf("clip-path", "radial-gradient", "stroke", "hard-clip", "translation", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-radial-stroke-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent translated device-space pixel-center winding clip, butt stroke distance and linear-light radial interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathTranslatedTriangleRadialGradientStroke(),
        SurfaceSrgbClipPathRadialStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            points = listOf(
                SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(9.25, 6.25),
                SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(32.25, 6.25),
                SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(9.25, 29.25),
            ),
            strokeStart = SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(7.25, 8.25),
            strokeEnd = SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(23.25, 20.25),
            strokeWidth = 4.0,
            center = SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(18.0, 16.0),
            radius = 16.0,
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
        ),
    )

    private fun clipPathLocalRadialMatrixStroke() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-local-radial-matrix-stroke"),
            "Local-matrix radial gradient stroke inside hard path clip",
            "Public Kanvas Surface hard non-AA Winding triangle clip with one opaque two-stop clamp radial butt/miter path stroke and a bounded translated shader local matrix.",
            64,
            64,
            1L,
            setOf("clip-path", "radial-gradient", "stroke", "hard-clip", "shader-local-matrix", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-radial-stroke-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent device-space clip/stroke coverage with translated local-matrix radial sampling and linear-light interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathLocalRadialMatrixStroke(),
        SurfaceSrgbClipPathRadialStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            points = listOf(
                SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(7.25, 6.25),
                SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(30.25, 6.25),
                SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(7.25, 29.25),
            ),
            strokeStart = SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(5.25, 8.25),
            strokeEnd = SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(21.25, 20.25),
            strokeWidth = 4.0,
            center = SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(16.0, 16.0),
            radius = 16.0,
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
            shaderTranslation = SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(1.25, -0.75),
        ),
    )

    private fun clipPathRightAngleRadialSquareStroke() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-right-angle-radial-square-stroke"),
            "Right-angle radial square stroke inside hard path clip",
            "Public Kanvas Surface hard non-AA Winding triangle clip with a right-angle rotated opaque two-stop clamp radial square-cap miter stroke.",
            64,
            64,
            1L,
            setOf("clip-path", "radial-gradient", "stroke", "hard-clip", "right-angle-rotation", "square-cap", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-radial-stroke-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent right-angle device-space winding clip, square-cap stroke distance and linear-light radial interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathRightAngleRadialSquareStroke(),
        SurfaceSrgbClipPathRadialStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            points = listOf(
                SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(27.75, 4.25),
                SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(27.75, 27.25),
                SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(4.75, 4.25),
            ),
            strokeStart = SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(23.75, 8.25),
            strokeEnd = SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(17.75, 20.25),
            strokeWidth = 4.0,
            center = SurfaceSrgbClipPathRadialStrokeCpuOracle.Point(16.0, 16.0),
            radius = 16.0,
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
            squareCaps = true,
        ),
    )

    private fun clipPathSweepSquareStroke() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-sweep-square-stroke"),
            "Sweep gradient square stroke inside hard path clip",
            "Public Kanvas Surface hard non-AA Winding triangle clip with one opaque full-turn two-stop sweep square-cap miter stroke.",
            64,
            64,
            1L,
            setOf("clip-path", "sweep-gradient", "stroke", "hard-clip", "square-cap", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-sweep-stroke-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent pixel-center winding clip, square stroke distance and full-turn linear-light sweep interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathSweepSquareStroke(),
        SurfaceSrgbClipPathSweepStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            points = listOf(
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(7.25, 6.25),
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(30.25, 6.25),
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(7.25, 29.25),
            ),
            strokeStart = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(5.25, 8.25),
            strokeEnd = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(21.25, 20.25),
            strokeWidth = 4.0,
            center = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(16.0, 16.0),
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
        ),
    )

    private fun clipPathLocalSweepMatrixStroke() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-local-sweep-matrix-stroke"),
            "Local-matrix sweep square stroke inside hard path clip",
            "Public Kanvas Surface hard non-AA Winding triangle clip with a full-turn two-stop sweep square-cap miter stroke and a bounded translated shader local matrix.",
            64,
            64,
            1L,
            setOf("clip-path", "sweep-gradient", "stroke", "hard-clip", "shader-local-matrix", "square-cap", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-sweep-stroke-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent device-space clip/stroke coverage with translated local-matrix full-turn sweep sampling and linear-light interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathLocalSweepMatrixStroke(),
        SurfaceSrgbClipPathSweepStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            points = listOf(
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(7.25, 6.25),
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(30.25, 6.25),
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(7.25, 29.25),
            ),
            strokeStart = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(5.25, 8.25),
            strokeEnd = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(21.25, 20.25),
            strokeWidth = 4.0,
            center = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(16.0, 16.0),
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
            shaderTranslation = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(1.25, -0.75),
        ),
    )

    private fun clipPathSweepSquareStrokeEvenOddHole() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-sweep-square-stroke-even-odd-hole"),
            "Sweep square stroke through an EvenOdd hard-clip hole",
            "Public Kanvas Surface hard non-AA EvenOdd clip with an outer rectangle and inner hole around one opaque full-turn two-stop sweep square-cap miter stroke.",
            64,
            64,
            1L,
            setOf("clip-path", "sweep-gradient", "stroke", "even-odd", "hard-clip", "square-cap", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-sweep-even-odd-hole-stroke-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent pixel-center EvenOdd rectangle XOR, square stroke distance and full-turn linear-light sweep interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathSweepSquareStrokeEvenOddHole(),
        SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            outer = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Rect(3.25, 3.25, 28.75, 28.75),
            inner = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Rect(10.25, 10.25, 21.75, 21.75),
            strokeStart = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Point(5.25, 8.25),
            strokeEnd = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Point(21.25, 20.25),
            strokeWidth = 4.0,
            center = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Point(16.0, 16.0),
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
        ),
    )

    private fun clipPathSweepSquareStrokeInverseEvenOddHole() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-sweep-square-stroke-inverse-even-odd-hole"),
            "Inverse EvenOdd sweep square stroke through a hard-clip hole",
            "Public Kanvas Surface hard non-AA inverse EvenOdd clip with an outer rectangle and inner hole around one opaque full-turn two-stop sweep square-cap miter stroke.",
            64,
            64,
            1L,
            setOf("clip-path", "sweep-gradient", "stroke", "inverse-even-odd", "hard-clip", "square-cap", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-sweep-inverse-even-odd-hole-stroke-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent pixel-center inverse EvenOdd rectangle XOR, square stroke distance and full-turn linear-light sweep interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathSweepSquareStrokeInverseEvenOddHole(),
        SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            outer = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Rect(3.25, 3.25, 28.75, 28.75),
            inner = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Rect(10.25, 10.25, 21.75, 21.75),
            strokeStart = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Point(5.25, 8.25),
            strokeEnd = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Point(21.25, 20.25),
            strokeWidth = 4.0,
            center = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Point(16.0, 16.0),
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
            inverse = true,
        ),
    )

    private fun clipPathSweepSquareStrokeEvenOddDifferenceHole() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-sweep-square-stroke-even-odd-difference-hole"),
            "EvenOdd Difference sweep square stroke outside a hard-clip hole",
            "Public Kanvas Surface hard non-AA EvenOdd Difference clip subtracting an outer rectangle and inner hole from the current clip around one opaque full-turn two-stop sweep square-cap miter stroke.",
            64,
            64,
            1L,
            setOf("clip-path", "sweep-gradient", "stroke", "even-odd", "difference", "hard-clip", "square-cap", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-sweep-even-odd-difference-hole-stroke-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent pixel-center inverse EvenOdd rectangle XOR for Difference, square stroke distance and full-turn linear-light sweep interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathSweepSquareStrokeEvenOddDifferenceHole(),
        SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            outer = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Rect(3.25, 3.25, 28.75, 28.75),
            inner = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Rect(10.25, 10.25, 21.75, 21.75),
            strokeStart = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Point(5.25, 8.25),
            strokeEnd = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Point(21.25, 20.25),
            strokeWidth = 4.0,
            center = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Point(16.0, 16.0),
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
            inverse = true,
        ),
    )

    private fun clipPathSweepSquareStrokeInverseWindingDifference() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-sweep-square-stroke-inverse-winding-difference"),
            "Inverse Winding Difference sweep square stroke",
            "Public Kanvas Surface hard non-AA inverse Winding Difference triangle clip around one opaque full-turn two-stop sweep square-cap miter stroke.",
            64,
            64,
            1L,
            setOf("clip-path", "sweep-gradient", "stroke", "inverse-winding", "difference", "hard-clip", "square-cap", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-sweep-inverse-winding-difference-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent pixel-center inverse Winding triangle membership, square stroke distance and full-turn linear-light sweep interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathSweepSquareStrokeInverseWindingDifference(),
        SurfaceSrgbClipPathSweepStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            points = listOf(
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(7.25, 6.25),
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(30.25, 6.25),
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(7.25, 29.25),
            ),
            strokeStart = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(5.25, 8.25),
            strokeEnd = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(21.25, 20.25),
            strokeWidth = 4.0,
            center = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(16.0, 16.0),
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
        ),
    )

    private fun clipPathSweepSquareStrokeScaledTranslatedInverseWinding() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-sweep-square-stroke-scaled-translated-inverse-winding"),
            "Scaled translated inverse Winding sweep square stroke",
            "Public Kanvas Surface hard non-AA inverse Winding triangle clip with bounded uniform scale and translation around one opaque full-turn two-stop sweep square-cap miter stroke.",
            64,
            64,
            1L,
            setOf("clip-path", "sweep-gradient", "stroke", "inverse-winding", "hard-clip", "square-cap", "uniform-scale", "translation", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-sweep-scaled-translated-inverse-winding-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent transformed device-space inverse Winding triangle, square stroke distance and inverse-transformed full-turn linear-light sweep interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathSweepSquareStrokeScaledTranslatedInverseWinding(),
        SurfaceSrgbClipPathSweepStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            points = listOf(
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(6.875, 5.875),
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(24.875, 5.875),
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(6.875, 23.875),
            ),
            strokeStart = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(8.1875, 7.1875),
            strokeEnd = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(20.1875, 13.9375),
            strokeWidth = 3.0,
            center = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(16.0, 16.0),
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
            shaderTranslation = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(-2.0, -1.0),
            clipInverted = true,
            shaderScale = 1.5,
        ),
    )

    private fun clipPathSweepButtStrokeEvenOddHole() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-sweep-butt-stroke-even-odd-hole"),
            "EvenOdd hole with a sweep butt stroke",
            "Public Kanvas Surface hard non-AA EvenOdd clip with an outer rectangle and inner hole around one opaque full-turn two-stop sweep butt-cap miter stroke.",
            64,
            64,
            1L,
            setOf("clip-path", "sweep-gradient", "stroke", "even-odd", "hard-clip", "butt-cap", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-sweep-even-odd-hole-butt-stroke-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent pixel-center EvenOdd rectangle XOR, butt stroke distance and full-turn linear-light sweep interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathSweepButtStrokeEvenOddHole(),
        SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            outer = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Rect(3.25, 3.25, 28.75, 28.75),
            inner = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Rect(10.25, 10.25, 21.75, 21.75),
            strokeStart = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Point(5.25, 8.25),
            strokeEnd = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Point(21.25, 20.25),
            strokeWidth = 4.0,
            center = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Point(16.0, 16.0),
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
            squareCaps = false,
        ),
    )

    private fun clipPathSweepSquareStrokeScaledTranslatedInverseEvenOddDifferenceHole() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-sweep-square-stroke-scaled-translated-inverse-even-odd-difference-hole"),
            "Scaled translated inverse EvenOdd Difference sweep stroke",
            "Public Kanvas Surface hard non-AA inverse EvenOdd Difference clip with a scaled translated outer rectangle and inner hole around one opaque full-turn two-stop sweep square-cap miter stroke.",
            64,
            64,
            1L,
            setOf("clip-path", "sweep-gradient", "stroke", "inverse-even-odd", "difference", "hard-clip", "square-cap", "uniform-scale", "translation", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-sweep-scaled-translated-inverse-even-odd-difference-hole-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent transformed device-space inverse EvenOdd Difference shell, square stroke distance and inverse-transformed full-turn linear-light sweep interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathSweepSquareStrokeScaledTranslatedInverseEvenOddDifferenceHole(),
        SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            outer = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Rect(6.875, 5.875, 24.875, 23.875),
            inner = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Rect(11.375, 10.375, 20.375, 16.375),
            strokeStart = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Point(8.1875, 7.1875),
            strokeEnd = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Point(20.1875, 13.9375),
            strokeWidth = 3.0,
            center = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Point(16.0, 16.0),
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
            shaderTranslation = SurfaceSrgbClipPathSweepEvenOddHoleStrokeCpuOracle.Point(-2.0, -1.0),
            shaderScale = 1.5,
        ),
    )

    private fun clipPathSweepButtStrokeInverseWinding() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-sweep-butt-stroke-inverse-winding"),
            "Inverse Winding sweep butt stroke",
            "Public Kanvas Surface hard non-AA inverse Winding triangle clip around one opaque full-turn two-stop sweep butt-cap miter stroke.",
            64,
            64,
            1L,
            setOf("clip-path", "sweep-gradient", "stroke", "inverse-winding", "hard-clip", "butt-cap", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-sweep-inverse-winding-butt-stroke-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent pixel-center inverse Winding triangle membership, butt stroke distance and full-turn linear-light sweep interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathSweepButtStrokeInverseWinding(),
        SurfaceSrgbClipPathSweepStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            points = listOf(
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(7.25, 6.25),
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(30.25, 6.25),
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(7.25, 29.25),
            ),
            strokeStart = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(5.25, 8.25),
            strokeEnd = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(21.25, 20.25),
            strokeWidth = 4.0,
            center = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(16.0, 16.0),
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
            clipInverted = true,
            squareCaps = false,
        ),
    )

    private fun clipPathSweepSquareStrokeRightAngleWinding() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-sweep-square-stroke-right-angle-winding"),
            "Right-angle Winding sweep square stroke",
            "Public Kanvas Surface hard non-AA Winding triangle clip with a bounded 90-degree rotation around a full-turn two-stop sweep square-cap miter stroke.",
            64,
            64,
            1L,
            setOf("clip-path", "sweep-gradient", "stroke", "winding", "hard-clip", "square-cap", "right-angle-rotation", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-sweep-right-angle-winding-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent right-angle device-space Winding triangle, square stroke distance and quarter-turn-adjusted linear-light sweep interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathSweepSquareStrokeRightAngleWinding(),
        SurfaceSrgbClipPathSweepStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            points = listOf(
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(6.875, 5.875),
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(24.875, 5.875),
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(6.875, 23.875),
            ),
            strokeStart = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(27.875, 4.125),
            strokeEnd = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(23.375, 12.125),
            strokeWidth = 2.0,
            center = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(16.0, 16.0),
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
            shaderAngleOffset = 0.25,
        ),
    )

    private fun clipPathSweepButtStrokeWinding() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-sweep-butt-stroke-winding"),
            "Winding clip with a sweep butt stroke",
            "Public Kanvas Surface hard non-AA Winding triangle clip around one opaque full-turn two-stop sweep butt-cap miter stroke.",
            64,
            64,
            1L,
            setOf("clip-path", "sweep-gradient", "stroke", "winding", "hard-clip", "butt-cap", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-sweep-winding-butt-stroke-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent pixel-center Winding triangle membership, butt stroke distance and full-turn linear-light sweep interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathSweepButtStrokeWinding(),
        SurfaceSrgbClipPathSweepStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            points = listOf(
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(7.25, 6.25),
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(30.25, 6.25),
                SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(7.25, 29.25),
            ),
            strokeStart = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(5.25, 8.25),
            strokeEnd = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(21.25, 20.25),
            strokeWidth = 4.0,
            center = SurfaceSrgbClipPathSweepStrokeCpuOracle.Point(16.0, 16.0),
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
            squareCaps = false,
        ),
    )

    private fun clipPathLinearGradientSquareStrokeWinding() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-linear-gradient-square-stroke-winding"),
            "Winding clip with a linear-gradient square stroke",
            "Public Kanvas Surface hard non-AA Winding triangle clip around one opaque two-stop clamp linear-gradient square-cap miter stroke.",
            64,
            64,
            1L,
            setOf("clip-path", "linear-gradient", "stroke", "winding", "hard-clip", "square-cap", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-linear-gradient-winding-square-stroke-device-space", 1),
            ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent pixel-center Winding triangle, square stroke coverage and clamp linear-gradient interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathLinearGradientSquareStrokeWinding(),
        SurfaceSrgbClipPathLinearGradientStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            points = listOf(
                SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(7.25, 6.25),
                SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(30.25, 6.25),
                SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(7.25, 29.25),
            ),
            strokeStart = SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(5.25, 8.25),
            strokeEnd = SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(21.25, 20.25),
            strokeWidth = 4.0,
            gradientStart = SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(0.0, 0.0),
            gradientEnd = SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(32.0, 0.0),
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
        ),
    )

    private fun clipPathLinearGradientButtStrokeWinding() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-linear-gradient-butt-stroke-winding"),
            "Winding clip with a linear-gradient butt stroke",
            "Public Kanvas Surface hard non-AA Winding triangle clip around one opaque two-stop clamp linear-gradient butt-cap miter stroke.",
            64,
            64,
            1L,
            setOf("clip-path", "linear-gradient", "stroke", "winding", "hard-clip", "butt-cap", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-linear-gradient-winding-butt-stroke-device-space", 1),
            ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent pixel-center Winding triangle, butt stroke coverage and clamp linear-gradient interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathLinearGradientButtStrokeWinding(),
        SurfaceSrgbClipPathLinearGradientStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            points = listOf(
                SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(7.25, 6.25),
                SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(30.25, 6.25),
                SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(7.25, 29.25),
            ),
            strokeStart = SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(5.25, 8.25),
            strokeEnd = SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(21.25, 20.25),
            strokeWidth = 4.0,
            gradientStart = SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(0.0, 0.0),
            gradientEnd = SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(32.0, 0.0),
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
            squareCaps = false,
        ),
    )

    private fun clipPathLinearGradientScaledTranslatedButtStrokeWinding() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-linear-gradient-scaled-translated-butt-stroke-winding"),
            "Scaled translated Winding clip with a linear-gradient butt stroke",
            "Public Kanvas Surface hard non-AA Winding triangle clip with bounded uniform scale and translation around one opaque two-stop clamp linear-gradient butt-cap miter stroke.",
            64,
            64,
            1L,
            setOf("clip-path", "linear-gradient", "stroke", "winding", "hard-clip", "butt-cap", "uniform-scale", "translation", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-linear-gradient-scaled-translated-winding-butt-stroke-device-space", 1),
            ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent transformed device-space Winding triangle, butt stroke coverage and inverse-transformed clamp linear-gradient interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathLinearGradientScaledTranslatedButtStrokeWinding(),
        SurfaceSrgbClipPathLinearGradientStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            points = listOf(
                SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(6.875, 5.875),
                SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(24.875, 5.875),
                SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(6.875, 23.875),
            ),
            strokeStart = SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(8.1875, 7.1875),
            strokeEnd = SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(20.1875, 13.9375),
            strokeWidth = 3.0,
            gradientStart = SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(2.0, 0.0),
            gradientEnd = SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(50.0, 0.0),
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
            squareCaps = false,
        ),
    )

    private fun clipPathLinearGradientButtStrokeWindingDifference() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-linear-gradient-butt-stroke-winding-difference"),
            "Winding Difference clip with a linear-gradient butt stroke",
            "Public Kanvas Surface hard non-AA Winding Difference triangle clip around one opaque two-stop clamp linear-gradient butt-cap miter stroke.",
            64,
            64,
            1L,
            setOf("clip-path", "linear-gradient", "stroke", "winding", "difference", "hard-clip", "butt-cap", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-linear-gradient-winding-difference-butt-stroke-device-space", 1),
            ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent inverse pixel-center Winding triangle, butt stroke coverage and clamp linear-gradient interpolation."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathLinearGradientButtStrokeWindingDifference(),
        SurfaceSrgbClipPathLinearGradientStrokeCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            points = listOf(
                SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(7.25, 6.25),
                SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(30.25, 6.25),
                SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(7.25, 29.25),
            ),
            strokeStart = SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(5.25, 8.25),
            strokeEnd = SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(21.25, 20.25),
            strokeWidth = 4.0,
            gradientStart = SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(0.0, 0.0),
            gradientEnd = SurfaceSrgbClipPathLinearGradientStrokeCpuOracle.Point(32.0, 0.0),
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
            clipInverted = true,
            squareCaps = false,
        ),
    )

    private fun clipPathTranslatedTriangleRadialGradient() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("clip-path-translated-triangle-radial-gradient"),
            "Translated clamp radial gradient inside hard path clip",
            "Public Kanvas Surface translated hard non-AA path clip with one opaque two-stop sRGB clamp radial-gradient FillRect consumer.",
            64, 64, 1L,
            setOf("clip-path", "radial-gradient", "hard-clip", "translation", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-radial-gradient-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent double-precision oracle with translated device-space geometry."),
            emptySet(),
        ),
        KanvasScenePrograms.clipPathTranslatedTriangleRadialGradient(),
        SurfaceSrgbClipPathRadialGradientCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            points = listOf(clipPoint(10f, 8f), clipPoint(58f, 8f), clipPoint(10f, 55f)),
            drawBounds = SurfaceSrgbGradientCpuOracle.Rect(2f, 0f, 66f, 64f),
            center = SurfaceSrgbGradientCpuOracle.Point(26.5f, 24.5f),
            radius = 24f,
            startColor = intArrayOf(255, 0, 0, 255),
            endColor = intArrayOf(0, 0, 255, 255),
        ),
    )

    private fun clipPathTranslatedTriangleDirectTriangleLinearGradient() = clipPathDirectTriangleLinearGradientCase(
        id = "clip-path-translated-triangle-direct-triangle-linear-gradient",
        title = "Translated clamp gradient direct triangle inside hard path clip",
        program = KanvasScenePrograms.clipPathTranslatedTriangleDirectTriangleLinearGradient(),
        contour = listOf(clipPoint(10f, 8f), clipPoint(58f, 8f), clipPoint(10f, 55f)),
        triangle = directTriangleGradient(6f, 4.25f, 62f, 12f, 14f, 60f),
        start = SurfaceSrgbGradientCpuOracle.Point(22f, 19.3f),
        end = SurfaceSrgbGradientCpuOracle.Point(22f, 23.3f),
    )

    private fun clipPathUniformScaledTriangleDirectTriangleLinearGradient() = clipPathDirectTriangleLinearGradientCase(
        id = "clip-path-uniform-scaled-triangle-direct-triangle-linear-gradient",
        title = "Uniformly scaled clamp gradient direct triangle inside hard path clip",
        program = KanvasScenePrograms.clipPathUniformScaledTriangleDirectTriangleLinearGradient(),
        contour = listOf(clipPoint(14f, 10f), clipPoint(50f, 10f), clipPoint(14f, 45.25f)),
        triangle = directTriangleGradient(11f, 7.1875f, 53f, 13f, 17f, 49f),
        start = SurfaceSrgbGradientCpuOracle.Point(23f, 18.3f),
        end = SurfaceSrgbGradientCpuOracle.Point(23f, 22.3f),
    )

    private fun clipPathSolidRRect() = clipPathRRectCase(
        "clip-path-solid-rrect", "Solid RRect inside hard path clip", KanvasScenePrograms.clipPathSolidRRect(),
        SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(
            8f, 8f, 52f, 48f,
            SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f),
            SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f),
            SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f),
            SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f),
        ), intArrayOf(242, 135, 46, 255),
    )

    private fun clipPathAsymmetricSolidRRect() = clipPathRRectCase(
        "clip-path-asymmetric-solid-rrect", "Asymmetric RRect inside hard path clip", KanvasScenePrograms.clipPathAsymmetricSolidRRect(),
        SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(
            8f, 8f, 52f, 48f,
            SurfaceSrgbClipPathRRectCpuOracle.Radii(4f, 8f),
            SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 4f),
            SurfaceSrgbClipPathRRectCpuOracle.Radii(8f, 12f),
            SurfaceSrgbClipPathRRectCpuOracle.Radii(6f, 3f),
        ), intArrayOf(31, 115, 209, 255),
    )

    private fun clipPathEllipseSolidRRect() = clipPathRRectCase(
        "clip-path-ellipse-solid-rrect", "Ellipse RRect inside hard path clip", KanvasScenePrograms.clipPathEllipseSolidRRect(),
        SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(
            12f, 20f, 52f, 44f,
            SurfaceSrgbClipPathRRectCpuOracle.Radii(20f, 12f),
            SurfaceSrgbClipPathRRectCpuOracle.Radii(20f, 12f),
            SurfaceSrgbClipPathRRectCpuOracle.Radii(20f, 12f),
            SurfaceSrgbClipPathRRectCpuOracle.Radii(20f, 12f),
        ), intArrayOf(242, 135, 46, 255),
    )

    private fun clipPathTranslatedSolidRRect() = clipPathRRectCase(
        "clip-path-translated-solid-rrect", "Positive translated solid RRect inside hard path clip",
        KanvasScenePrograms.clipPathTranslatedSolidRRect(),
        SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(
            12f, 13f, 56f, 53f,
            SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f),
            SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f),
            SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f),
            SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f),
        ), intArrayOf(242, 135, 46, 255), translated = true,
    )

    private fun clipPathTranslatedAsymmetricSolidRRect() = clipPathRRectCase(
        "clip-path-translated-asymmetric-solid-rrect", "Positive translated asymmetric RRect inside hard path clip",
        KanvasScenePrograms.clipPathTranslatedAsymmetricSolidRRect(),
        SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(
            12f, 13f, 56f, 53f,
            SurfaceSrgbClipPathRRectCpuOracle.Radii(4f, 8f),
            SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 4f),
            SurfaceSrgbClipPathRRectCpuOracle.Radii(8f, 12f),
            SurfaceSrgbClipPathRRectCpuOracle.Radii(6f, 3f),
        ), intArrayOf(31, 115, 209, 255), translated = true,
    )

    private fun clipPathTranslatedEllipseSolidRRect() = clipPathRRectCase(
        "clip-path-translated-ellipse-solid-rrect", "Positive translated ellipse RRect inside hard path clip",
        KanvasScenePrograms.clipPathTranslatedEllipseSolidRRect(),
        SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(
            16f, 25f, 56f, 49f,
            SurfaceSrgbClipPathRRectCpuOracle.Radii(20f, 12f),
            SurfaceSrgbClipPathRRectCpuOracle.Radii(20f, 12f),
            SurfaceSrgbClipPathRRectCpuOracle.Radii(20f, 12f),
            SurfaceSrgbClipPathRRectCpuOracle.Radii(20f, 12f),
        ), intArrayOf(242, 135, 46, 255), translated = true,
    )

    private fun clipPathAxisXTranslatedSolidRRect() = clipPathRRectCase(
        "clip-path-axis-x-translated-solid-rrect", "Exact axis-X translated solid RRect inside hard path clip",
        KanvasScenePrograms.clipPathAxisXTranslatedSolidRRect(),
        SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(12f, 8f, 56f, 48f,
            SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f), SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f), SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f), SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f)),
        intArrayOf(242, 135, 46, 255), translated = true, exactTranslation = true,
    )

    private fun clipPathAxisYTranslatedAsymmetricSolidRRect() = clipPathRRectCase(
        "clip-path-axis-y-translated-asymmetric-solid-rrect", "Exact axis-Y translated asymmetric RRect inside hard path clip",
        KanvasScenePrograms.clipPathAxisYTranslatedAsymmetricSolidRRect(),
        SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(8f, 13f, 52f, 53f,
            SurfaceSrgbClipPathRRectCpuOracle.Radii(4f, 8f), SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 4f), SurfaceSrgbClipPathRRectCpuOracle.Radii(8f, 12f), SurfaceSrgbClipPathRRectCpuOracle.Radii(6f, 3f)),
        intArrayOf(31, 115, 209, 255), translated = true, exactTranslation = true,
    )

    private fun clipPathNegativeXTranslatedEllipseSolidRRect() = clipPathRRectCase(
        "clip-path-negative-x-translated-ellipse-solid-rrect", "Exact negative-X translated ellipse RRect inside hard path clip",
        KanvasScenePrograms.clipPathNegativeXTranslatedEllipseSolidRRect(),
        SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(8f, 25f, 48f, 49f,
            SurfaceSrgbClipPathRRectCpuOracle.Radii(20f, 12f), SurfaceSrgbClipPathRRectCpuOracle.Radii(20f, 12f), SurfaceSrgbClipPathRRectCpuOracle.Radii(20f, 12f), SurfaceSrgbClipPathRRectCpuOracle.Radii(20f, 12f)),
        intArrayOf(242, 135, 46, 255), translated = true, exactTranslation = true,
    )

    private fun clipPathNegativeYTranslatedSolidRRect() = clipPathRRectCase(
        "clip-path-negative-y-translated-solid-rrect", "Exact negative-Y translated solid RRect inside hard path clip",
        KanvasScenePrograms.clipPathNegativeYTranslatedSolidRRect(),
        SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(12f, 3f, 56f, 43f,
            SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f), SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f), SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f), SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f)),
        intArrayOf(242, 135, 46, 255), translated = true, exactTranslation = true,
    )

    private fun clipPathInverseAxisXTranslatedSolidRRect() = clipPathRRectCase(
        "clip-path-inverse-axis-x-translated-solid-rrect", "Inverse-winding axis-X translated solid RRect inside hard path clip",
        KanvasScenePrograms.clipPathInverseAxisXTranslatedSolidRRect(),
        SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(12f, 8f, 56f, 48f,
            SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f), SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f), SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f), SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f)),
        intArrayOf(242, 135, 46, 255), translated = true, exactTranslation = true, inverseWinding = true,
    )

    private fun clipPathInverseAxisYTranslatedAsymmetricSolidRRect() = clipPathRRectCase(
        "clip-path-inverse-axis-y-translated-asymmetric-solid-rrect", "Inverse-winding axis-Y translated asymmetric RRect inside hard path clip",
        KanvasScenePrograms.clipPathInverseAxisYTranslatedAsymmetricSolidRRect(),
        SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(8f, 13f, 52f, 53f,
            SurfaceSrgbClipPathRRectCpuOracle.Radii(4f, 8f), SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 4f), SurfaceSrgbClipPathRRectCpuOracle.Radii(8f, 12f), SurfaceSrgbClipPathRRectCpuOracle.Radii(6f, 3f)),
        intArrayOf(31, 115, 209, 255), translated = true, exactTranslation = true, inverseWinding = true,
    )

    private fun clipPathInverseNegativeXTranslatedEllipseSolidRRect() = clipPathRRectCase(
        "clip-path-inverse-negative-x-translated-ellipse-solid-rrect", "Inverse-winding negative-X translated ellipse RRect inside hard path clip",
        KanvasScenePrograms.clipPathInverseNegativeXTranslatedEllipseSolidRRect(),
        SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(8f, 25f, 48f, 49f,
            SurfaceSrgbClipPathRRectCpuOracle.Radii(20f, 12f), SurfaceSrgbClipPathRRectCpuOracle.Radii(20f, 12f), SurfaceSrgbClipPathRRectCpuOracle.Radii(20f, 12f), SurfaceSrgbClipPathRRectCpuOracle.Radii(20f, 12f)),
        intArrayOf(242, 135, 46, 255), translated = true, exactTranslation = true, inverseWinding = true,
    )

    private fun clipPathInverseNegativeYTranslatedSolidRRect() = clipPathRRectCase(
        "clip-path-inverse-negative-y-translated-solid-rrect", "Inverse-winding negative-Y translated solid RRect inside hard path clip",
        KanvasScenePrograms.clipPathInverseNegativeYTranslatedSolidRRect(),
        SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(12f, 3f, 56f, 43f,
            SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f), SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f), SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f), SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 10f)),
        intArrayOf(242, 135, 46, 255), translated = true, exactTranslation = true, inverseWinding = true,
    )

    private fun clipPathSolidDRRect() = clipPathDRRectCase(
        "clip-path-solid-drrect", "Solid DRRect inside hard path clip", KanvasScenePrograms.clipPathSolidDRRect(),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(8f, 8f, 52f, 48f, 10f, 10f),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(22f, 20f, 40f, 38f, 4f, 4f), intArrayOf(242, 135, 46, 255),
    )

    private fun clipPathAsymmetricSolidDRRect() = clipPathDRRectCase(
        "clip-path-asymmetric-solid-drrect", "Asymmetric DRRect inside hard path clip", KanvasScenePrograms.clipPathAsymmetricSolidDRRect(),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(8f, 8f, 52f, 48f,
            SurfaceSrgbClipPathDRRectCpuOracle.Radii(4f, 8f), SurfaceSrgbClipPathDRRectCpuOracle.Radii(10f, 4f), SurfaceSrgbClipPathDRRectCpuOracle.Radii(8f, 12f), SurfaceSrgbClipPathDRRectCpuOracle.Radii(6f, 3f)),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(20f, 18f, 42f, 39f,
            SurfaceSrgbClipPathDRRectCpuOracle.Radii(3f, 5f), SurfaceSrgbClipPathDRRectCpuOracle.Radii(6f, 2f), SurfaceSrgbClipPathDRRectCpuOracle.Radii(4f, 7f), SurfaceSrgbClipPathDRRectCpuOracle.Radii(2f, 3f)), intArrayOf(31, 115, 209, 255),
    )

    private fun clipPathEllipseSolidDRRect() = clipPathDRRectCase(
        "clip-path-ellipse-solid-drrect", "Ellipse DRRect inside hard path clip", KanvasScenePrograms.clipPathEllipseSolidDRRect(),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(12f, 20f, 52f, 44f, 20f, 12f),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(24f, 26f, 40f, 38f, 8f, 6f), intArrayOf(242, 135, 46, 255),
    )

    private fun clipPathTranslatedSolidDRRect() = clipPathDRRectCase(
        "clip-path-translated-solid-drrect", "Positive translated solid DRRect inside hard path clip",
        KanvasScenePrograms.clipPathTranslatedSolidDRRect(),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(12f, 13f, 56f, 53f, 10f, 10f),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(26f, 25f, 44f, 43f, 4f, 4f), intArrayOf(242, 135, 46, 255), translated = true,
    )

    private fun clipPathTranslatedAsymmetricSolidDRRect() = clipPathDRRectCase(
        "clip-path-translated-asymmetric-solid-drrect", "Positive translated asymmetric DRRect inside hard path clip",
        KanvasScenePrograms.clipPathTranslatedAsymmetricSolidDRRect(),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(12f, 13f, 56f, 53f,
            SurfaceSrgbClipPathDRRectCpuOracle.Radii(4f, 8f), SurfaceSrgbClipPathDRRectCpuOracle.Radii(10f, 4f), SurfaceSrgbClipPathDRRectCpuOracle.Radii(8f, 12f), SurfaceSrgbClipPathDRRectCpuOracle.Radii(6f, 3f)),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(24f, 23f, 46f, 44f,
            SurfaceSrgbClipPathDRRectCpuOracle.Radii(3f, 5f), SurfaceSrgbClipPathDRRectCpuOracle.Radii(6f, 2f), SurfaceSrgbClipPathDRRectCpuOracle.Radii(4f, 7f), SurfaceSrgbClipPathDRRectCpuOracle.Radii(2f, 3f)), intArrayOf(31, 115, 209, 255), translated = true,
    )

    private fun clipPathTranslatedEllipseSolidDRRect() = clipPathDRRectCase(
        "clip-path-translated-ellipse-solid-drrect", "Positive translated ellipse DRRect inside hard path clip",
        KanvasScenePrograms.clipPathTranslatedEllipseSolidDRRect(),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(16f, 25f, 56f, 49f, 20f, 12f),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(28f, 31f, 44f, 43f, 8f, 6f), intArrayOf(242, 135, 46, 255), translated = true,
    )

    private fun clipPathAxisXTranslatedSolidDRRect() = clipPathDRRectCase(
        "clip-path-axis-x-translated-solid-drrect", "Exact axis-X translated solid DRRect inside hard path clip",
        KanvasScenePrograms.clipPathAxisXTranslatedSolidDRRect(),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(12f, 8f, 56f, 48f, 10f, 10f),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(26f, 20f, 44f, 38f, 4f, 4f), intArrayOf(242, 135, 46, 255), translated = true, exactTranslation = true,
    )

    private fun clipPathAxisYTranslatedAsymmetricSolidDRRect() = clipPathDRRectCase(
        "clip-path-axis-y-translated-asymmetric-solid-drrect", "Exact axis-Y translated asymmetric DRRect inside hard path clip",
        KanvasScenePrograms.clipPathAxisYTranslatedAsymmetricSolidDRRect(),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(8f, 13f, 52f, 53f, SurfaceSrgbClipPathDRRectCpuOracle.Radii(4f, 8f), SurfaceSrgbClipPathDRRectCpuOracle.Radii(10f, 4f), SurfaceSrgbClipPathDRRectCpuOracle.Radii(8f, 12f), SurfaceSrgbClipPathDRRectCpuOracle.Radii(6f, 3f)),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(20f, 23f, 42f, 44f, SurfaceSrgbClipPathDRRectCpuOracle.Radii(3f, 5f), SurfaceSrgbClipPathDRRectCpuOracle.Radii(6f, 2f), SurfaceSrgbClipPathDRRectCpuOracle.Radii(4f, 7f), SurfaceSrgbClipPathDRRectCpuOracle.Radii(2f, 3f)), intArrayOf(31, 115, 209, 255), translated = true, exactTranslation = true,
    )

    private fun clipPathNegativeXTranslatedEllipseSolidDRRect() = clipPathDRRectCase(
        "clip-path-negative-x-translated-ellipse-solid-drrect", "Exact negative-X translated ellipse DRRect inside hard path clip",
        KanvasScenePrograms.clipPathNegativeXTranslatedEllipseSolidDRRect(),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(8f, 25f, 48f, 49f, 20f, 12f),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(20f, 31f, 36f, 43f, 8f, 6f), intArrayOf(242, 135, 46, 255), translated = true, exactTranslation = true,
    )

    private fun clipPathNegativeYTranslatedSolidDRRect() = clipPathDRRectCase(
        "clip-path-negative-y-translated-solid-drrect", "Exact negative-Y translated solid DRRect inside hard path clip",
        KanvasScenePrograms.clipPathNegativeYTranslatedSolidDRRect(),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(12f, 3f, 56f, 43f, 10f, 10f),
        SurfaceSrgbClipPathDRRectCpuOracle.RRect(26f, 15f, 44f, 33f, 4f, 4f), intArrayOf(242, 135, 46, 255), translated = true, exactTranslation = true,
    )

    private fun clipPathRRectCase(
        id: String,
        title: String,
        program: org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram,
        rrect: SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect,
        fill: IntArray,
        translated: Boolean = false,
        exactTranslation: Boolean = false,
        inverseWinding: Boolean = false,
    ) = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId(id), title,
            if (inverseWinding) {
                "Public Kanvas Surface exact finite translated analytic RRect consumer inside an identity-captured hard inverse-Winding path clip."
            } else if (exactTranslation) {
                "Public Kanvas Surface exact finite translated analytic RRect consumer inside an identity-captured hard Winding path clip."
            } else if (translated) {
                "Public Kanvas Surface positive translated analytic RRect consumer inside an identity-captured hard Winding path clip."
            } else {
                "Public Kanvas Surface hard non-AA winding triangle clip with one opaque identity analytic RRect consumer."
            },
            64, 64, 1L,
            setOf("clip-path", "solid-rrect", "hard-clip", "kanvas-surface") +
                if (inverseWinding) setOf("inverse-winding") else emptySet(),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-rrect-pixel-center", 1),
            ComparisonPolicy(
                0,
                100.0,
                1,
                if (inverseWinding) {
                    "Exact RGBA8 output from independent pixel-center inverse triangle membership and analytic RRect membership."
                } else {
                    "Exact RGBA8 output from independent pixel-center winding clip and analytic RRect membership."
                },
            ),
            emptySet(),
        ),
        program,
        SurfaceSrgbClipPathRRectCpuOracle(
            intArrayOf(0, 0, 0, 0),
            listOf(
                SurfaceSrgbClipPathRRectCpuOracle.Point(8f, 8f),
                SurfaceSrgbClipPathRRectCpuOracle.Point(56f, 8f),
                SurfaceSrgbClipPathRRectCpuOracle.Point(8f, 55f),
            ), rrect, fill,
            if (inverseWinding) SurfaceSrgbClipPathRRectCpuOracle.TriangleClip.InverseWinding else SurfaceSrgbClipPathRRectCpuOracle.TriangleClip.Winding,
        ),
    )

    private fun clipPathDRRectCase(
        id: String, title: String, program: org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram,
        outer: SurfaceSrgbClipPathDRRectCpuOracle.RRect, inner: SurfaceSrgbClipPathDRRectCpuOracle.RRect, fill: IntArray,
        translated: Boolean = false,
        exactTranslation: Boolean = false,
    ) = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId(id), title,
            if (exactTranslation) {
                "Public Kanvas Surface exact finite translated analytic DRRect consumer inside an identity-captured hard Winding path clip."
            } else if (translated) {
                "Public Kanvas Surface positive translated analytic DRRect consumer inside an identity-captured hard Winding path clip."
            } else {
                "Public Kanvas Surface hard non-AA winding triangle clip with one opaque identity analytic DRRect consumer."
            },
            64, 64, 1L, setOf("clip-path", "solid-drrect", "hard-clip", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-drrect-pixel-center", 1),
            ComparisonPolicy(0, 100.0, 1, "Exact RGBA8 output from independent pixel-center winding clip and analytic DRRect membership."), emptySet(),
        ), program,
        SurfaceSrgbClipPathDRRectCpuOracle(
            listOf(SurfaceSrgbClipPathDRRectCpuOracle.Point(8f, 8f), SurfaceSrgbClipPathDRRectCpuOracle.Point(56f, 8f), SurfaceSrgbClipPathDRRectCpuOracle.Point(8f, 55f)),
            outer, inner, fill,
        ),
    )

    private fun clipPathLinearGradientCase(
        id: String, title: String, program: org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram,
        contour: List<SurfaceSrgbClipPathCpuOracle.Point>, left: Float, top: Float, right: Float, bottom: Float,
        startX: Float, startY: Float, endX: Float, endY: Float,
    ) = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId(id), title, "Public Kanvas Surface hard non-AA path clip with one direct clamp linear-gradient FillRect.",
            64, 64, 1L, setOf("clip-path", "linear-gradient", "hard-clip", "kanvas-surface"), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-linear-gradient-device-space", 1),
            ComparisonPolicy(1, 100.0, 1, "Independent device-space pixel-center winding clip and linear-light clamp gradient oracle."), emptySet(),
        ), program,
        SurfaceSrgbClipPathLinearGradientCpuOracle(
            intArrayOf(13, 20, 33, 255), contour, SurfaceSrgbGradientCpuOracle.Rect(left, top, right, bottom),
            SurfaceSrgbGradientCpuOracle.Point(startX, startY), SurfaceSrgbGradientCpuOracle.Point(endX, endY),
            intArrayOf(255, 0, 0, 255), intArrayOf(0, 0, 255, 255),
        ),
    )

    private fun clipPathDirectTriangleCase(
        id: String,
        title: String,
        description: String,
        program: org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram,
        contour: List<SurfaceSrgbClipPathCpuOracle.Point>,
        draws: List<SurfaceSrgbClipPathCpuOracle.OpaqueTriangle>,
    ) = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId(id), title, description, 64, 64, 1L,
            setOf("clip-path", "solid-path", "direct-triangles", "hard-clip", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-direct-triangle-pixel-center", 1),
            ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent device-space pixel-center clip and direct-triangle membership."),
            emptySet(),
        ),
        program,
        SurfaceSrgbClipPathCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            contours = listOf(SurfaceSrgbClipPathCpuOracle.Contour(contour)),
            draws = draws,
        ),
    )

    private fun clipPathDirectTriangleLinearGradientCase(
        id: String,
        title: String,
        program: org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram,
        contour: List<SurfaceSrgbClipPathCpuOracle.Point>,
        triangle: SurfaceSrgbClipPathDirectTriangleLinearGradientCpuOracle.Triangle,
        start: SurfaceSrgbGradientCpuOracle.Point,
        end: SurfaceSrgbGradientCpuOracle.Point,
    ) = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId(id), title,
            "Public Kanvas Surface hard non-AA path clip with one exact DirectTriangles clamp linear-gradient drawPath consumer.",
            64, 64, 1L,
            setOf("clip-path", "linear-gradient", "direct-triangles", "hard-clip", "kanvas-surface"),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-direct-triangle-linear-gradient-device-space", 1),
            ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent device-space clip, direct-triangle, and clamp-gradient membership."),
            emptySet(),
        ),
        program,
        SurfaceSrgbClipPathDirectTriangleLinearGradientCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            clipPoints = contour,
            triangle = triangle,
            start = start,
            end = end,
            startColor = intArrayOf(0, 0, 0, 255),
            endColor = intArrayOf(4, 4, 4, 255),
        ),
    )

    private fun directTriangle(
        firstX: Float,
        firstY: Float,
        secondX: Float,
        secondY: Float,
        thirdX: Float,
        thirdY: Float,
        color: IntArray,
    ) = SurfaceSrgbClipPathCpuOracle.OpaqueTriangle(
        clipPoint(firstX, firstY),
        clipPoint(secondX, secondY),
        clipPoint(thirdX, thirdY),
        color,
    )

    private fun directTriangleGradient(
        firstX: Float,
        firstY: Float,
        secondX: Float,
        secondY: Float,
        thirdX: Float,
        thirdY: Float,
    ) = SurfaceSrgbClipPathDirectTriangleLinearGradientCpuOracle.Triangle(
        clipPoint(firstX, firstY),
        clipPoint(secondX, secondY),
        clipPoint(thirdX, thirdY),
    )

    private fun clipPoint(x: Float, y: Float) = SurfaceSrgbClipPathCpuOracle.Point(x, y)

    private fun clipPathCase(
        id: String,
        title: String,
        description: String,
        program: org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram,
        contours: List<List<SurfaceSrgbClipPathCpuOracle.Point>>,
        draws: List<SurfaceSrgbClipPathCpuOracle.OpaqueRect>,
        clipInverted: Boolean = false,
        extraTags: Set<String> = emptySet(),
        comparisonRationale: String = "Exact opaque RGBA8 output from independent hard pixel-center winding path clip membership and paint order.",
    ) = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId(id), title, description, 64, 64, 1L,
            setOf("clip-path", "solid-rect", "hard-clip", "kanvas-surface") + extraTags, EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-clip-path-pixel-center", 1),
            ComparisonPolicy(0, 100.0, 1, comparisonRationale),
            emptySet(),
        ),
        program,
        SurfaceSrgbClipPathCpuOracle(
            background = intArrayOf(13, 20, 33, 255),
            contours = contours.map(SurfaceSrgbClipPathCpuOracle::Contour),
            draws = draws,
            clipInverted = clipInverted,
        ),
    )

    private fun solidTrianglePath() = pathFillCase(
        id = "solid-triangle-path",
        title = "Solid triangle path",
        description = "Public Kanvas Surface non-AA winding triangle path.",
        tags = setOf("path-fill", "winding", "kanvas-surface"),
        program = KanvasScenePrograms.solidTrianglePath(),
        fill = intArrayOf(242, 135, 46, 255),
        contours = listOf(
            SurfaceSrgbPathFillCpuOracle.Contour(listOf(
                point(8f, 8f), point(56f, 8f), point(8f, 55f),
            )),
        ),
        fillRule = SurfaceSrgbPathFillCpuOracle.FillRule.Winding,
    )

    private fun quadraticPathFill() = pathFillCase(
        id = "quadratic-path-fill", title = "Quadratic path fill",
        description = "Public Kanvas Surface non-AA winding quadratic drawPath fill.",
        tags = setOf("path-fill", "quadratic", "kanvas-surface"),
        program = KanvasScenePrograms.quadraticPathFill(), fill = intArrayOf(242, 135, 46, 255),
        contours = listOf(SurfaceSrgbPathFillCpuOracle.Contour(quadraticContour())),
        fillRule = SurfaceSrgbPathFillCpuOracle.FillRule.Winding,
        comparisonRationale = "Independent pixel-center winding oracle evaluates the public quadratic Bézier analytically before polygon membership.",
    )

    private fun cubicPathFill() = pathFillCase(
        id = "cubic-path-fill", title = "Cubic path fill",
        description = "Public Kanvas Surface non-AA winding cubic drawPath fill.",
        tags = setOf("path-fill", "cubic", "kanvas-surface"),
        program = KanvasScenePrograms.cubicPathFill(), fill = intArrayOf(31, 115, 209, 255),
        contours = listOf(SurfaceSrgbPathFillCpuOracle.Contour(cubicContour())),
        fillRule = SurfaceSrgbPathFillCpuOracle.FillRule.Winding,
        comparisonRationale = "Independent pixel-center winding oracle evaluates the public cubic Bézier before polygon membership.",
    )

    private fun ovalPathFill() = pathFillCase(
        id = "oval-path-fill", title = "Oval path fill",
        description = "Public Kanvas Surface non-AA oval drawPath fill lowered through cubic verbs.",
        tags = setOf("path-fill", "oval", "cubic", "kanvas-surface"),
        program = KanvasScenePrograms.ovalPathFill(), fill = intArrayOf(56, 220, 120, 255),
        contours = listOf(SurfaceSrgbPathFillCpuOracle.Contour(ovalContour(10f, 12f, 54f, 52f))),
        fillRule = SurfaceSrgbPathFillCpuOracle.FillRule.Winding,
        comparisonRationale = "Independent pixel-center winding oracle evaluates the four cubic oval segments before polygon membership.",
    )

    private fun circlePathFill() = pathFillCase(
        id = "circle-path-fill", title = "Circle path fill",
        description = "Public Kanvas Surface non-AA circle drawPath fill lowered through cubic verbs.",
        tags = setOf("path-fill", "circle", "cubic", "kanvas-surface"),
        program = KanvasScenePrograms.circlePathFill(), fill = intArrayOf(242, 135, 46, 255),
        contours = listOf(SurfaceSrgbPathFillCpuOracle.Contour(ovalContour(12f, 12f, 52f, 52f))),
        fillRule = SurfaceSrgbPathFillCpuOracle.FillRule.Winding,
        comparisonRationale = "Independent pixel-center winding oracle evaluates the four cubic circle segments before polygon membership.",
    )

    private fun solidConcavePath() = pathFillCase(
        id = "solid-concave-path",
        title = "Solid concave path",
        description = "Public Kanvas Surface non-AA winding concave path.",
        tags = setOf("path-fill", "winding", "concave", "kanvas-surface"),
        program = KanvasScenePrograms.solidConcavePath(),
        fill = intArrayOf(31, 115, 209, 255),
        contours = listOf(
            SurfaceSrgbPathFillCpuOracle.Contour(listOf(
                point(8f, 8f), point(56f, 8f), point(56f, 24f), point(32f, 24f),
                point(32f, 40f), point(56f, 40f), point(56f, 56f), point(8f, 56f),
            )),
        ),
        fillRule = SurfaceSrgbPathFillCpuOracle.FillRule.Winding,
    )

    private fun evenOddPathHole() = pathFillCase(
        id = "even-odd-path-hole",
        title = "Even-odd path hole",
        description = "Public Kanvas Surface non-AA even-odd path with a rectangular hole.",
        tags = setOf("path-fill", "even-odd", "kanvas-surface"),
        program = KanvasScenePrograms.evenOddPathHole(),
        fill = intArrayOf(56, 220, 120, 255),
        contours = listOf(
            SurfaceSrgbPathFillCpuOracle.Contour(listOf(
                point(8f, 8f), point(56f, 8f), point(56f, 56f), point(8f, 56f),
            )),
            SurfaceSrgbPathFillCpuOracle.Contour(listOf(
                point(22f, 20f), point(44f, 20f), point(44f, 44f), point(22f, 44f),
            )),
        ),
        fillRule = SurfaceSrgbPathFillCpuOracle.FillRule.EvenOdd,
    )

    private fun windingPathHole() = pathFillCase(
        id = "winding-path-hole",
        title = "Winding path hole",
        description = "Public Kanvas Surface non-AA winding path with an oppositely oriented rectangular hole.",
        tags = setOf("path-fill", "winding", "hole", "kanvas-surface"),
        program = KanvasScenePrograms.windingPathHole(),
        fill = intArrayOf(31, 115, 209, 255),
        contours = listOf(
            SurfaceSrgbPathFillCpuOracle.Contour(listOf(
                point(8f, 8f), point(56f, 8f), point(56f, 56f), point(8f, 56f),
            )),
            SurfaceSrgbPathFillCpuOracle.Contour(listOf(
                point(22f, 44f), point(44f, 44f), point(44f, 20f), point(22f, 20f),
            )),
        ),
        fillRule = SurfaceSrgbPathFillCpuOracle.FillRule.Winding,
    )

    private fun inverseWindingTrianglePath() = pathFillCase(
        id = "inverse-winding-triangle-path",
        title = "Inverse winding triangle path",
        description = "Public Kanvas Surface non-AA inverse winding triangle path.",
        tags = setOf("path-fill", "inverse-winding", "kanvas-surface"),
        program = KanvasScenePrograms.inverseWindingTrianglePath(),
        fill = intArrayOf(242, 135, 46, 255),
        contours = listOf(
            SurfaceSrgbPathFillCpuOracle.Contour(listOf(
                point(8f, 8f), point(56f, 8f), point(8f, 55f),
            )),
        ),
        fillRule = SurfaceSrgbPathFillCpuOracle.FillRule.InverseWinding,
        oracleVersion = 2,
        comparisonRationale = "Exact opaque RGBA8 output from independent pixel-center inverse winding/even-odd polygon membership.",
    )

    private fun inverseEvenOddPathHole() = pathFillCase(
        id = "inverse-even-odd-path-hole",
        title = "Inverse even-odd path hole",
        description = "Public Kanvas Surface non-AA inverse even-odd path with a rectangular hole.",
        tags = setOf("path-fill", "inverse-even-odd", "kanvas-surface"),
        program = KanvasScenePrograms.inverseEvenOddPathHole(),
        fill = intArrayOf(56, 220, 120, 255),
        contours = listOf(
            SurfaceSrgbPathFillCpuOracle.Contour(listOf(
                point(8f, 8f), point(56f, 8f), point(56f, 56f), point(8f, 56f),
            )),
            SurfaceSrgbPathFillCpuOracle.Contour(listOf(
                point(22f, 20f), point(44f, 20f), point(44f, 44f), point(22f, 44f),
            )),
        ),
        fillRule = SurfaceSrgbPathFillCpuOracle.FillRule.InverseEvenOdd,
        oracleVersion = 2,
        comparisonRationale = "Exact opaque RGBA8 output from independent pixel-center inverse winding/even-odd polygon membership.",
    )

    private fun evenOddBowTiePath() = pathFillCase(
        id = "even-odd-bow-tie-path",
        title = "Even-odd bow-tie path",
        description = "Public Kanvas Surface non-AA even-odd path with one bounded self-intersection.",
        tags = setOf("path-fill", "even-odd", "self-intersection", "kanvas-surface"),
        program = KanvasScenePrograms.evenOddBowTiePath(),
        fill = intArrayOf(56, 220, 120, 255),
        contours = listOf(
            SurfaceSrgbPathFillCpuOracle.Contour(listOf(
                point(8f, 8f), point(56f, 56f), point(8f, 56f), point(56f, 8f),
            )),
        ),
        fillRule = SurfaceSrgbPathFillCpuOracle.FillRule.EvenOdd,
    )

    private fun reflectedPathTopologyRefusal() = surfaceRefusal(
        id = "reflected-path-topology-refusal",
        title = "Reflected path topology refusal",
        description = "Public Kanvas Surface refuses a reflected multi-contour winding path before native submission because reflected path topology is not yet admitted.",
        tags = setOf("path-fill", "winding", "reflection", "refusal", "kanvas-surface"),
        code = "unsupported.transform.class_downgrade",
        program = KanvasScenePrograms.reflectedWindingPathHole(),
    )

    private fun implicitClosureTrianglePath() = pathFillCase(
        id = "implicit-closure-triangle-path",
        title = "Implicit closure triangle path",
        description = "Public Kanvas Surface non-AA winding triangle path filled without an explicit close command.",
        tags = setOf("path-fill", "winding", "implicit-closure", "kanvas-surface"),
        program = KanvasScenePrograms.implicitClosureTrianglePath(),
        fill = intArrayOf(242, 135, 46, 255),
        contours = listOf(
            SurfaceSrgbPathFillCpuOracle.Contour(listOf(
                point(8f, 8f), point(56f, 8f), point(8f, 55f),
            )),
        ),
        fillRule = SurfaceSrgbPathFillCpuOracle.FillRule.Winding,
    )

    private fun translatedTrianglePath() = pathFillCase(
        id = "translated-triangle-path",
        title = "Translated triangle path",
        description = "Public Kanvas Surface non-AA winding triangle path under literal translation (4,5).",
        tags = setOf("path-fill", "winding", "translate", "kanvas-surface"),
        program = KanvasScenePrograms.translatedTrianglePath(),
        fill = intArrayOf(31, 115, 209, 255),
        contours = listOf(
            SurfaceSrgbPathFillCpuOracle.Contour(listOf(
                point(12f, 13f), point(60f, 13f), point(12f, 60f),
            )),
        ),
        fillRule = SurfaceSrgbPathFillCpuOracle.FillRule.Winding,
    )

    private fun uniformScaledTrianglePath() = pathFillCase(
        id = "uniform-scaled-triangle-path",
        title = "Uniform scaled triangle path",
        description = "Public Kanvas Surface non-AA winding triangle path under literal uniform scale (1.5,1.5).",
        tags = setOf("path-fill", "winding", "scale", "kanvas-surface"),
        program = KanvasScenePrograms.uniformScaledTrianglePath(),
        fill = intArrayOf(56, 220, 120, 255),
        contours = listOf(
            SurfaceSrgbPathFillCpuOracle.Contour(listOf(
                point(12f, 12f), point(60f, 12f), point(12f, 60f),
            )),
        ),
        fillRule = SurfaceSrgbPathFillCpuOracle.FillRule.Winding,
    )

    private fun pathFillCase(
        id: String,
        title: String,
        description: String,
        tags: Set<String>,
        program: org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram,
        fill: IntArray,
        contours: List<SurfaceSrgbPathFillCpuOracle.Contour>,
        fillRule: SurfaceSrgbPathFillCpuOracle.FillRule,
        oracleVersion: Int = 2,
        comparisonRationale: String = "Exact opaque RGBA8 output from independent pixel-center winding/even-odd polygon membership.",
    ) = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId(id), title, description, 64, 64, 1L, tags, EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("surface-srgb-path-pixel-center", oracleVersion),
            ComparisonPolicy(0, 100.0, 1, comparisonRationale), emptySet(),
        ),
        program,
        SurfaceSrgbPathFillCpuOracle(intArrayOf(13, 20, 33, 255), fill, contours, fillRule),
    )

    private fun point(x: Float, y: Float) = SurfaceSrgbPathFillCpuOracle.Point(x, y)

    /** Independent Bézier sampling for the CPU oracle; it does not call Kanvas lowering. */
    private fun quadraticContour(): List<SurfaceSrgbPathFillCpuOracle.Point> =
        (0..96).map { step ->
            val t = step / 96f
            val u = 1f - t
            point(u * u * 8f + 2f * u * t * 32f + t * t * 56f, u * u * 56f + 2f * u * t * 4f + t * t * 56f)
        }

    private fun cubicContour(): List<SurfaceSrgbPathFillCpuOracle.Point> =
        cubicSegment(8f, 56f, 8f, 0f, 56f, 0f, 56f, 56f)

    private fun ovalContour(left: Float, top: Float, right: Float, bottom: Float): List<SurfaceSrgbPathFillCpuOracle.Point> {
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val rx = (right - left) / 2f
        val ry = (bottom - top) / 2f
        val k = 0.55228475f
        return (cubicSegment(cx + rx, cy, cx + rx, cy - k * ry, cx + k * rx, cy - ry, cx, cy - ry) +
            cubicSegment(cx, cy - ry, cx - k * rx, cy - ry, cx - rx, cy - k * ry, cx - rx, cy).drop(1) +
            cubicSegment(cx - rx, cy, cx - rx, cy + k * ry, cx - k * rx, cy + ry, cx, cy + ry).drop(1) +
            cubicSegment(cx, cy + ry, cx + k * rx, cy + ry, cx + rx, cy + k * ry, cx + rx, cy).drop(1)).dropLast(1)
    }

    private fun cubicSegment(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        (0..24).map { step ->
            val t = step / 24f
            val u = 1f - t
            point(
                u * u * u * x0 + 3f * u * u * t * x1 + 3f * u * t * t * x2 + t * t * t * x3,
                u * u * u * y0 + 3f * u * u * t * y1 + 3f * u * t * t * y2 + t * t * t * y3,
            )
        }

    private fun surfaceRefusal(id: String, title: String, description: String, tags: Set<String>, code: String, program: org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram) = EvidenceCase(
        EvidenceSceneDescriptor(EvidenceSceneId(id), title, description, 16, 16, 1L, tags, EvidenceExpectation.ShouldRefuse(code), OraclePolicy.StableRefusal, null, emptySet()), program, null,
    )
}
