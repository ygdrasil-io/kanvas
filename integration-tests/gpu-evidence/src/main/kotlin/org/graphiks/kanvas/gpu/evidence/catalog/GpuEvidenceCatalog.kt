package org.graphiks.kanvas.gpu.evidence.catalog

import org.graphiks.kanvas.gpu.evidence.oracle.CpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.ReferenceRaster
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbSeparableMaskBlurCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbOracleMath
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbSrcOverCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbGradientCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbLinearGradientStrokeBandsCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbRRectCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbClipRRectCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbClipPathCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbClipPathLinearGradientCpuOracle
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
        separableBlurRect(),
        translucentCardOverlap(),
        scissorOverlay(),
        canvasStateRestoreToCount(),
        strokeRectOutline(),
        linearGradientLanes(),
        radialSwatch(),
        sweepDisk(),
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
        scaledSolidRRect(),
        solidDRRectHole(),
        asymmetricSolidRRect(),
        ellipseSolidRRect(),
        asymmetricSolidDRRectHole(),
        clipRRectSolid(),
        clipRRectEllipse(),
        clipRRectTwoBands(),
        clipPathTriangleSolid(),
        clipPathTriangleDifferenceSolid(),
        clipPathConcaveSolid(),
        clipPathTriangleTwoBands(),
        clipPathTranslatedTriangleSolid(),
        clipPathUniformScaledTriangleSolid(),
        clipPathUniformScaledTriangleTwoBands(),
        clipPathTriangleLinearGradient(),
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
        linearGradientThreeStops(),
        basicPrimitivesEmptyRectRefusal(),
        perspectiveTransformRefusal(),
        reflectedPathTopologyRefusal(),
        unregisteredRuntimeEffectRefusal(),
        aggregateMemoryBudgetRefusal(),
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
            clip = SurfaceSrgbFractionalRectCoverageCpuOracle.DeviceRect(8f, 8f, 56f, 56f),
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

    private fun linearGradientThreeStops() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("linear-gradient-three-stops"),
            "Linear gradient three stops refusal",
            "Public Kanvas Surface rejects a clamp linear gradient with three stops before submission.",
            64,
            64,
            1L,
            setOf("linear-gradient", "kanvas-surface", "refusal"),
            EvidenceExpectation.ShouldRefuse("unsupported.material.mapping.linear_gradient_stop_count"),
            OraclePolicy.StableRefusal,
            null,
            emptySet(),
        ),
        KanvasScenePrograms.linearGradientThreeStops(),
        null,
    )

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
