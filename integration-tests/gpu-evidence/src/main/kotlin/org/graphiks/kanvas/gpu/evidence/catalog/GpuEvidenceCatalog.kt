package org.graphiks.kanvas.gpu.evidence.catalog

import org.graphiks.kanvas.gpu.evidence.oracle.CpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.ReferenceRaster
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbSeparableMaskBlurCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbOracleMath
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbSrcOverCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbGradientCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbLinearGradientStrokeBandsCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbRRectCpuOracle
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
        strokeRectOutline(),
        linearGradientLanes(),
        radialSwatch(),
        sweepDisk(),
        linearGradientThreeStops(),
        sweepGradientPartialAngle(),
        affineSolidRect(),
        scissoredRadialGradient(),
        repeatGradientRendered(),
        gradientStrokeRefusal(),
        scaledSolidRRect(),
        solidDRRectHole(),
    )
    val refusalCases: List<EvidenceCase> = listOf(
        unregisteredRuntimeEffectRefusal(),
        aggregateMemoryBudgetRefusal(),
    )
    val cases: List<EvidenceCase> = renderCases + refusalCases
    val catalog = EvidenceSceneCatalog(cases.map(EvidenceCase::descriptor))

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

    private fun linearGradientThreeStops() = gradientCase(
        "linear-gradient-three-stops", "Linear gradient three stops", "Public Kanvas Surface clamp linear gradient with three opaque sRGB stops.",
        setOf("linear-gradient", "kanvas-surface"), "surface-srgb-gradient-linear-clamp", KanvasScenePrograms.linearGradientThreeStops(),
        SurfaceSrgbGradientCpuOracle.linear(
            SurfaceSrgbGradientCpuOracle.Rect(8f, 16f, 56f, 48f), SurfaceSrgbGradientCpuOracle.Point(8.5f, 32.5f), SurfaceSrgbGradientCpuOracle.Point(55.5f, 32.5f),
            listOf(SurfaceSrgbGradientCpuOracle.Stop(0f, 255, 56, 56), SurfaceSrgbGradientCpuOracle.Stop(.5f, 56, 220, 120), SurfaceSrgbGradientCpuOracle.Stop(1f, 56, 112, 255)),
        ),
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
    )

    private fun aggregateMemoryBudgetRefusal(): EvidenceCase = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("aggregate-memory-budget-refusal"), "Aggregate memory budget refusal", "A valid full-target SolidRect draw refuses during product recording under a one-byte budget.",
            16, 16, 1L, setOf("solid-rect", "frame-memory", "refusal"),
            EvidenceExpectation.ShouldRefuse("unsupported.frame_memory.aggregate_budget_exceeded"), OraclePolicy.StableRefusal, null, emptySet(),
        ),
        RendererRefusalPrograms.aggregateMemoryBudget(),
        null,
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
    private fun surfaceRefusal(id: String, title: String, description: String, tags: Set<String>, code: String, program: org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram) = EvidenceCase(
        EvidenceSceneDescriptor(EvidenceSceneId(id), title, description, 16, 16, 1L, tags, EvidenceExpectation.ShouldRefuse(code), OraclePolicy.StableRefusal, null, emptySet()), program, null,
    )
}
