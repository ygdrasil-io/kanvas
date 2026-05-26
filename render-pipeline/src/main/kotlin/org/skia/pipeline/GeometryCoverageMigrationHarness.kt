package org.skia.pipeline

enum class DescriptorMigrationMode {
    Shadow,
    Compare,
    Gated,
}

enum class DescriptorPrimitiveFamily(val id: String) {
    AxisAlignedFilledRect("axis-aligned-filled-rect"),
    AxisAlignedFilledRRect("axis-aligned-filled-rrect"),
    SimpleFilledPath("simple-filled-path"),
    StrokeOutlinePath("stroke-outline-path"),
}

data class DescriptorMigrationGate(
    val primitive: DescriptorPrimitiveFamily,
    val backend: BackendKind,
    val enabled: Boolean = false,
)

data class DescriptorRoute(val id: String)

data class CpuDescriptorExecutionMetrics(
    val touchedPixels: Int,
    val scalarVectorStatus: String,
    val kernelId: String?,
    val fallbackReason: String?,
    val pathVerbCount: Int? = null,
    val edgeCount: Int? = null,
    val segmentCount: Int? = null,
) {
    fun dump(): String = buildString {
        append("touchedPixels=$touchedPixels,scalarVectorStatus=$scalarVectorStatus,")
        append("kernelId=${kernelId ?: "none"},fallbackReason=${fallbackReason ?: "none"}")
        pathVerbCount?.let { append(",pathVerbCount=$it") }
        edgeCount?.let { append(",edgeCount=$it") }
        segmentCount?.let { append(",segmentCount=$it") }
    }
}

data class PathCoverageFixture(
    val bounds: FloatRect,
    val fillType: PathFillType,
    val inverse: Boolean,
    val antiAlias: Boolean,
    val verbCount: Int,
    val edgeCount: Int,
    val segmentCount: Int,
    val stroke: StrokePlan? = null,
)

data class DescriptorGateDecision(
    val mode: DescriptorMigrationMode,
    val primitive: DescriptorPrimitiveFamily,
    val backend: BackendKind,
    val descriptorEnabled: Boolean,
    val selectedRoute: DescriptorRoute,
    val descriptorRoute: DescriptorRoute,
) {
    fun dump(): String = buildString {
        appendLine("DescriptorMigrationGate(v1)")
        appendLine("mode=$mode")
        appendLine("primitive=${primitive.id}")
        appendLine("backend=$backend")
        appendLine("descriptorEnabled=$descriptorEnabled")
        appendLine("selectedRoute=${selectedRoute.id}")
        appendLine("descriptorRoute=${descriptorRoute.id}")
    }.trimEnd()
}

data class PixelDiffSummary(
    val compared: Boolean,
    val passed: Boolean?,
    val width: Int?,
    val height: Int?,
    val differingPixels: Int,
    val maxChannelDelta: Int,
    val artifactPath: String?,
) {
    fun dump(): String {
        if (!compared) return "not-compared(artifactPath=${artifactPath ?: "none"})"
        return "compared(passed=$passed,width=$width,height=$height,differingPixels=$differingPixels," +
            "maxChannelDelta=$maxChannelDelta,artifactPath=${artifactPath ?: "none"})"
    }
}

data class DescriptorMigrationShadowResult(
    val backend: BackendKind,
    val drawKind: String,
    val transform: TransformFacts,
    val clip: ClipInteraction,
    val geometryPlan: GeometryPlan,
    val coveragePlan: CoveragePlan,
    val loweringResult: CoverageLoweringResult,
    val currentRoute: DescriptorRoute,
    val descriptorRoute: DescriptorRoute,
    val currentPixels: PixelBuffer,
    val diffSummary: PixelDiffSummary,
) {
    fun dump(): String = descriptorDumpHeader(DescriptorMigrationMode.Shadow, backend, drawKind, transform, clip) +
        "\ncurrentRoute=${currentRoute.id}" +
        "\ndescriptorRoute=${descriptorRoute.id}" +
        "\n${CoverageDescriptorDump(geometryPlan, coveragePlan, loweringResult).dump()}" +
        "\nfallback=${dumpFallback(loweringResult)}" +
        "\ndiff=${diffSummary.dump()}"
}

data class DescriptorMigrationCompareResult(
    val backend: BackendKind,
    val drawKind: String,
    val transform: TransformFacts,
    val clip: ClipInteraction,
    val geometryPlan: GeometryPlan,
    val coveragePlan: CoveragePlan,
    val loweringResult: CoverageLoweringResult,
    val currentRoute: DescriptorRoute,
    val descriptorRoute: DescriptorRoute,
    val diffSummary: PixelDiffSummary,
    val metrics: CpuDescriptorExecutionMetrics,
) {
    fun dump(): String = descriptorDumpHeader(DescriptorMigrationMode.Compare, backend, drawKind, transform, clip) +
        "\ncurrentRoute=${currentRoute.id}" +
        "\ndescriptorRoute=${descriptorRoute.id}" +
        "\n${CoverageDescriptorDump(geometryPlan, coveragePlan, loweringResult).dump()}" +
        "\nfallback=${dumpFallback(loweringResult)}" +
        "\ndiff=${diffSummary.dump()}" +
        "\nmetrics=${metrics.dump()}"
}

data class UnsupportedDescriptorDiagnostic(
    val backend: BackendKind,
    val drawKind: String,
    val geometryPlan: GeometryPlan,
    val coveragePlan: CoveragePlan,
    val loweringResult: CoverageLoweringResult,
    val descriptorRoute: DescriptorRoute,
) {
    fun dump(): String = buildString {
        appendLine("DescriptorMigrationDiagnostic(v1)")
        appendLine("backend=$backend")
        appendLine("drawKind=$drawKind")
        appendLine("descriptorRoute=${descriptorRoute.id}")
        appendLine(CoverageDescriptorDump(geometryPlan, coveragePlan, loweringResult).dump())
        appendLine("fallback=${dumpFallback(loweringResult)}")
    }.trimEnd()
}

object GeometryCoverageMigrationHarness {
    private val legacyCpuSolidRectRoute = DescriptorRoute("kanvas-skia.current.draw-rect")
    private val legacyCpuRRectRoute = DescriptorRoute("kanvas-skia.current.draw-rrect")
    private val legacyCpuPathRoute = DescriptorRoute("kanvas-skia.current.draw-path")
    private val legacyCpuStrokePathRoute = DescriptorRoute("kanvas-skia.current.stroke-path")
    private val descriptorCpuSolidRectRoute = DescriptorRoute("cpu.descriptor.coverage-plan.solid-rect")
    private val descriptorCpuRRectRoute = DescriptorRoute("cpu.descriptor.coverage-plan.materialized-rrect")
    private val descriptorCpuPathRoute = DescriptorRoute("cpu.descriptor.coverage-plan.path-coverage")
    private val descriptorCpuStrokePathRoute = DescriptorRoute("cpu.descriptor.coverage-plan.stroke-outline")
    private val shadowOnlyRoute = DescriptorRoute("descriptor.shadow-only")

    fun shadowAxisAlignedFilledRect(
        width: Int,
        height: Int,
        rect: FloatRect,
        color: Rgba,
        backend: BackendKind = BackendKind.CPU,
        artifactPath: String? = null,
    ): DescriptorMigrationShadowResult {
        val plans = axisAlignedRectPlans(rect)
        return DescriptorMigrationShadowResult(
            backend = backend,
            drawKind = DescriptorPrimitiveFamily.AxisAlignedFilledRect.id,
            transform = identityAxisAlignedTransform(),
            clip = ClipInteraction.None,
            geometryPlan = plans.geometry,
            coveragePlan = plans.coverage,
            loweringResult = CoveragePlanAdapter.lower(plans.coverage),
            currentRoute = currentRouteFor(backend),
            descriptorRoute = shadowRouteFor(backend),
            currentPixels = CpuScalarPipelineExecutor.legacySolidRect(width, height, color),
            diffSummary = PixelDiffSummary(
                compared = false,
                passed = null,
                width = width,
                height = height,
                differingPixels = 0,
                maxChannelDelta = 0,
                artifactPath = artifactPath,
            ),
        )
    }

    fun compareAxisAlignedFilledRect(
        width: Int,
        height: Int,
        rect: FloatRect,
        color: Rgba,
        artifactPath: String,
        antiAlias: Boolean = false,
    ): DescriptorMigrationCompareResult {
        val currentPixels = CpuScalarPipelineExecutor.legacySolidRect(width, height, color)
        return compareAxisAlignedFilledRectAgainstOracle(
            width = width,
            height = height,
            rect = rect,
            color = color,
            oraclePixels = currentPixels,
            artifactPath = artifactPath,
            antiAlias = antiAlias,
        )
    }

    fun compareAxisAlignedFilledRectAgainstOracle(
        width: Int,
        height: Int,
        rect: FloatRect,
        color: Rgba,
        oraclePixels: PixelBuffer,
        artifactPath: String,
        antiAlias: Boolean,
    ): DescriptorMigrationCompareResult {
        val plans = axisAlignedRectPlans(rect, antiAlias)
        val lowering = CoveragePlanAdapter.lower(plans.coverage)
        val descriptor = descriptorPixels(width, height, color, lowering)
        val diff = diffPixels(oraclePixels, descriptor.pixels, artifactPath)
        return DescriptorMigrationCompareResult(
            backend = BackendKind.CPU,
            drawKind = DescriptorPrimitiveFamily.AxisAlignedFilledRect.id,
            transform = identityAxisAlignedTransform(),
            clip = ClipInteraction.None,
            geometryPlan = plans.geometry,
            coveragePlan = plans.coverage,
            loweringResult = lowering,
            currentRoute = legacyCpuSolidRectRoute,
            descriptorRoute = descriptorCpuSolidRectRoute,
            diffSummary = diff,
            metrics = descriptor.metrics,
        )
    }

    fun compareMaterializedRRectCoverageAgainstOracle(
        width: Int,
        height: Int,
        rrect: RRectSpec,
        color: Rgba,
        oraclePixels: PixelBuffer,
        coverageAlpha: ByteArray,
        artifactPath: String,
    ): DescriptorMigrationCompareResult {
        require(coverageAlpha.size == width * height) {
            "coverageAlpha must match target dimensions"
        }
        val geometry = GeometryPlan.Supported(
            primitive = GeometryPrimitive.RRect(rrect),
            bounds = GeometryBounds(conservative = rrect.bounds, tight = rrect.bounds),
            transform = identityAxisAlignedTransform(),
            clip = ClipInteraction.None,
        )
        val coverage = CoveragePlan.AlphaMask(
            ref = AlphaMaskRef("fixture.rrect.a8"),
            bounds = IntRect(0, 0, width, height),
            format = MaskFormat.A8,
        )
        val lowering = CoveragePlanAdapter.lower(coverage)
        val descriptor = materializedCoveragePixels(width, height, color, coverageAlpha)
        val diff = diffPixels(oraclePixels, descriptor.pixels, artifactPath)
        return DescriptorMigrationCompareResult(
            backend = BackendKind.CPU,
            drawKind = DescriptorPrimitiveFamily.AxisAlignedFilledRRect.id,
            transform = identityAxisAlignedTransform(),
            clip = ClipInteraction.None,
            geometryPlan = geometry,
            coveragePlan = coverage,
            loweringResult = lowering,
            currentRoute = legacyCpuRRectRoute,
            descriptorRoute = descriptorCpuRRectRoute,
            diffSummary = diff,
            metrics = descriptor.metrics,
        )
    }

    fun comparePathCoverageAgainstOracle(
        width: Int,
        height: Int,
        fixture: PathCoverageFixture,
        color: Rgba,
        oraclePixels: PixelBuffer,
        coverageAlpha: ByteArray,
        artifactPath: String,
    ): DescriptorMigrationCompareResult {
        require(coverageAlpha.size == width * height) {
            "coverageAlpha must match target dimensions"
        }
        val geometry = pathGeometry(fixture)
        val coverage = CoveragePlan.PathCoverage(
            fillType = fixture.fillType,
            aa = fixture.antiAlias,
            inverse = fixture.inverse,
        )
        val lowering = CoveragePlanAdapter.lower(coverage)
        val descriptor = pathCoveragePixels(width, height, color, coverageAlpha, fixture)
        val diff = diffPixels(oraclePixels, descriptor.pixels, artifactPath)
        val primitive = if (fixture.stroke == null) {
            DescriptorPrimitiveFamily.SimpleFilledPath
        } else {
            DescriptorPrimitiveFamily.StrokeOutlinePath
        }
        return DescriptorMigrationCompareResult(
            backend = BackendKind.CPU,
            drawKind = primitive.id,
            transform = identityAxisAlignedTransform(),
            clip = ClipInteraction.None,
            geometryPlan = geometry,
            coveragePlan = coverage,
            loweringResult = lowering,
            currentRoute = currentRouteFor(primitive, BackendKind.CPU),
            descriptorRoute = descriptorRouteFor(primitive, BackendKind.CPU),
            diffSummary = diff,
            metrics = descriptor.metrics,
        )
    }

    fun gateDecision(
        primitive: DescriptorPrimitiveFamily,
        backend: BackendKind,
        gates: Collection<DescriptorMigrationGate> = emptyList(),
    ): DescriptorGateDecision {
        val enabled = gates.any { it.primitive == primitive && it.backend == backend && it.enabled }
        val descriptorRoute = descriptorRouteFor(primitive, backend)
        return DescriptorGateDecision(
            mode = DescriptorMigrationMode.Gated,
            primitive = primitive,
            backend = backend,
            descriptorEnabled = enabled,
            selectedRoute = if (enabled) descriptorRoute else currentRouteFor(primitive, backend),
            descriptorRoute = descriptorRoute,
        )
    }

    fun unsupportedDescriptorDiagnostic(
        backend: BackendKind,
        reason: CoverageReason,
    ): UnsupportedDescriptorDiagnostic {
        val rect = FloatRect(0f, 0f, 16f, 16f)
        val geometry = axisAlignedRectPlans(rect).geometry
        val coverage = CoveragePlan.Unsupported(reason)
        return UnsupportedDescriptorDiagnostic(
            backend = backend,
            drawKind = DescriptorPrimitiveFamily.AxisAlignedFilledRect.id,
            geometryPlan = geometry,
            coveragePlan = coverage,
            loweringResult = CoveragePlanAdapter.lower(coverage),
            descriptorRoute = shadowRouteFor(backend),
        )
    }

    fun unsupportedGeometryDiagnostic(
        backend: BackendKind,
        primitive: DescriptorPrimitiveFamily,
        reason: GeometryReason,
    ): UnsupportedDescriptorDiagnostic {
        val geometry = GeometryPlan.Unsupported(reason)
        val coverage = CoveragePlan.Unsupported(StandardCoverageReason.SpanRunsUnsupported)
        return UnsupportedDescriptorDiagnostic(
            backend = backend,
            drawKind = primitive.id,
            geometryPlan = geometry,
            coveragePlan = coverage,
            loweringResult = CoveragePlanAdapter.lower(coverage),
            descriptorRoute = descriptorRouteFor(primitive, backend),
        )
    }

    private fun descriptorPixels(
        width: Int,
        height: Int,
        color: Rgba,
        lowering: CoverageLoweringResult,
    ): DescriptorPixelExecution {
        val coverage = (lowering as? CoverageLoweringResult.CoverageModelResult)?.coverage
            ?: return DescriptorPixelExecution(
                pixels = PixelBuffer(width, height, IntArray(width * height)),
                metrics = CpuDescriptorExecutionMetrics(
                    touchedPixels = 0,
                    scalarVectorStatus = "fallback",
                    kernelId = null,
                    fallbackReason = "unsupported-lowering",
                ),
            )
        if (coverage is CoverageModel.AnalyticRect) {
            return analyticRectPixels(width, height, color, coverage)
        }
        val result = CpuScalarPipelineExecutor.execute(
            KanvasPipelineIR.demoSolidRectIr(color = color, coverage = coverage),
            width = width,
            height = height,
            options = CpuPipelineExecutionOptions(vectorMode = CpuVectorMode.Disabled),
        )
        return when (result) {
            is CpuExecutionResult.Success -> DescriptorPixelExecution(
                pixels = result.pixels,
                metrics = CpuDescriptorExecutionMetrics(
                    touchedPixels = result.pixels.argb8888.count { it != 0 },
                    scalarVectorStatus = "vector-disabled",
                    kernelId = result.kernelId,
                    fallbackReason = null,
                ),
            )
            is CpuExecutionResult.LegacyFallback -> DescriptorPixelExecution(
                pixels = PixelBuffer(width, height, IntArray(width * height)),
                metrics = CpuDescriptorExecutionMetrics(
                    touchedPixels = 0,
                    scalarVectorStatus = "fallback",
                    kernelId = null,
                    fallbackReason = result.reason,
                ),
            )
        }
    }

    private fun analyticRectPixels(
        width: Int,
        height: Int,
        color: Rgba,
        coverage: CoverageModel.AnalyticRect,
    ): DescriptorPixelExecution {
        val pixels = IntArray(width * height)
        val packed = pack(color)
        var touchedPixels = 0
        for (y in 0 until height) {
            val cy = y + 0.5f
            if (cy < coverage.bounds.top || cy >= coverage.bounds.bottom) continue
            for (x in 0 until width) {
                val cx = x + 0.5f
                if (cx < coverage.bounds.left || cx >= coverage.bounds.right) continue
                pixels[y * width + x] = packed
                touchedPixels++
            }
        }
        return DescriptorPixelExecution(
            pixels = PixelBuffer(width, height, pixels),
            metrics = CpuDescriptorExecutionMetrics(
                touchedPixels = touchedPixels,
                scalarVectorStatus = if (coverage.aa) "scalar-analytic-rect-aa" else "scalar-analytic-rect",
                kernelId = "cpu.scalar.analytic_rect_src_over_clear",
                fallbackReason = null,
            ),
        )
    }

    private fun materializedCoveragePixels(
        width: Int,
        height: Int,
        color: Rgba,
        coverageAlpha: ByteArray,
    ): DescriptorPixelExecution {
        val pixels = IntArray(width * height)
        var touchedPixels = 0
        for (i in pixels.indices) {
            val coverage = coverageAlpha[i].toInt() and 0xFF
            if (coverage == 0) continue
            touchedPixels++
            pixels[i] = pack(color.copy(a = color.a * (coverage / 255f)))
        }
        return DescriptorPixelExecution(
            pixels = PixelBuffer(width, height, pixels),
            metrics = CpuDescriptorExecutionMetrics(
                touchedPixels = touchedPixels,
                scalarVectorStatus = "scalar-materialized-mask",
                kernelId = "cpu.scalar.materialized_a8_src_over_clear",
                fallbackReason = null,
            ),
        )
    }

    private fun pathCoveragePixels(
        width: Int,
        height: Int,
        color: Rgba,
        coverageAlpha: ByteArray,
        fixture: PathCoverageFixture,
    ): DescriptorPixelExecution {
        val base = materializedCoveragePixels(width, height, color, coverageAlpha)
        return base.copy(
            metrics = base.metrics.copy(
                scalarVectorStatus = "scalar-path-coverage",
                kernelId = if (fixture.stroke == null) {
                    "cpu.scalar.path_coverage_src_over_clear"
                } else {
                    "cpu.scalar.stroke_outline_path_coverage_src_over_clear"
                },
                pathVerbCount = fixture.verbCount,
                edgeCount = fixture.edgeCount,
                segmentCount = fixture.segmentCount,
            ),
        )
    }

    private fun diffPixels(
        current: PixelBuffer,
        descriptor: PixelBuffer,
        artifactPath: String,
    ): PixelDiffSummary {
        require(current.width == descriptor.width && current.height == descriptor.height) {
            "Pixel buffers must have equal dimensions"
        }
        var differingPixels = 0
        var maxChannelDelta = 0
        for (i in current.argb8888.indices) {
            val a = current.argb8888[i]
            val b = descriptor.argb8888[i]
            if (a != b) {
                differingPixels++
                maxChannelDelta = maxOf(maxChannelDelta, channelDelta(a, b, 24))
                maxChannelDelta = maxOf(maxChannelDelta, channelDelta(a, b, 16))
                maxChannelDelta = maxOf(maxChannelDelta, channelDelta(a, b, 8))
                maxChannelDelta = maxOf(maxChannelDelta, channelDelta(a, b, 0))
            }
        }
        return PixelDiffSummary(
            compared = true,
            passed = differingPixels == 0,
            width = current.width,
            height = current.height,
            differingPixels = differingPixels,
            maxChannelDelta = maxChannelDelta,
            artifactPath = artifactPath,
        )
    }

    private fun channelDelta(a: Int, b: Int, shift: Int): Int =
        kotlin.math.abs(((a ushr shift) and 0xFF) - ((b ushr shift) and 0xFF))

    private fun axisAlignedRectPlans(rect: FloatRect, antiAlias: Boolean = false): RectPlans {
        val transform = identityAxisAlignedTransform()
        val geometry = GeometryPlan.Supported(
            primitive = GeometryPrimitive.Rect(source = rect, device = rect),
            bounds = GeometryBounds(conservative = rect, tight = rect),
            transform = transform,
            clip = ClipInteraction.None,
        )
        return RectPlans(geometry = geometry, coverage = CoveragePlan.AnalyticRect(bounds = rect, aa = antiAlias))
    }

    private fun pathGeometry(fixture: PathCoverageFixture): GeometryPlan.Supported =
        GeometryPlan.Supported(
            primitive = GeometryPrimitive.Path(
                fillType = fixture.fillType,
                stroke = fixture.stroke,
                verbs = PathVerbSlice(fixture.verbCount),
            ),
            bounds = GeometryBounds(conservative = fixture.bounds, tight = fixture.bounds),
            transform = identityAxisAlignedTransform(),
            clip = ClipInteraction.None,
        )

    private fun identityAxisAlignedTransform(): TransformFacts = TransformFacts(
        matrix = MatrixSpec.Identity,
        isAxisAligned = true,
        hasPerspective = false,
        maxScale = 1f,
        isInvertible = true,
    )

    private fun currentRouteFor(backend: BackendKind): DescriptorRoute =
        currentRouteFor(DescriptorPrimitiveFamily.AxisAlignedFilledRect, backend)

    private fun currentRouteFor(
        primitive: DescriptorPrimitiveFamily,
        backend: BackendKind,
    ): DescriptorRoute = when (backend) {
        BackendKind.CPU -> when (primitive) {
            DescriptorPrimitiveFamily.AxisAlignedFilledRect -> legacyCpuSolidRectRoute
            DescriptorPrimitiveFamily.AxisAlignedFilledRRect -> legacyCpuRRectRoute
            DescriptorPrimitiveFamily.SimpleFilledPath -> legacyCpuPathRoute
            DescriptorPrimitiveFamily.StrokeOutlinePath -> legacyCpuStrokePathRoute
        }
        BackendKind.GPU -> DescriptorRoute("gpu.current.handwritten-solid-rect")
    }

    private fun descriptorRouteFor(
        primitive: DescriptorPrimitiveFamily,
        backend: BackendKind,
    ): DescriptorRoute = when (backend) {
        BackendKind.CPU -> when (primitive) {
            DescriptorPrimitiveFamily.AxisAlignedFilledRect -> descriptorCpuSolidRectRoute
            DescriptorPrimitiveFamily.AxisAlignedFilledRRect -> descriptorCpuRRectRoute
            DescriptorPrimitiveFamily.SimpleFilledPath -> descriptorCpuPathRoute
            DescriptorPrimitiveFamily.StrokeOutlinePath -> descriptorCpuStrokePathRoute
        }
        BackendKind.GPU -> DescriptorRoute("gpu.descriptor.coverage-plan.${primitive.id}")
    }

    private fun shadowRouteFor(backend: BackendKind): DescriptorRoute = when (backend) {
        BackendKind.CPU -> shadowOnlyRoute
        BackendKind.GPU -> DescriptorRoute("gpu.shadow.generated-rect-candidate")
    }

    private data class RectPlans(val geometry: GeometryPlan, val coverage: CoveragePlan)

    private data class DescriptorPixelExecution(
        val pixels: PixelBuffer,
        val metrics: CpuDescriptorExecutionMetrics,
    )
}

private fun descriptorDumpHeader(
    mode: DescriptorMigrationMode,
    backend: BackendKind,
    drawKind: String,
    transform: TransformFacts,
    clip: ClipInteraction,
): String = buildString {
    appendLine("DescriptorMigrationHarness(v1)")
    appendLine("mode=$mode")
    appendLine("backend=$backend")
    appendLine("drawKind=$drawKind")
    appendLine("ctm=${dumpMatrix(transform.matrix)}")
    appendLine(
        "transformFacts=axisAligned=${transform.isAxisAligned},hasPerspective=${transform.hasPerspective}," +
            "maxScale=${transform.maxScale},invertible=${transform.isInvertible}",
    )
    appendLine("clip=${dumpClipForMigration(clip)}")
}.trimEnd()

private fun dumpFallback(lowering: CoverageLoweringResult): String = when (lowering) {
    is CoverageLoweringResult.CoverageModelResult -> "none"
    is CoverageLoweringResult.StrategyResult -> when (val strategy = lowering.strategy) {
        is CoverageBackendStrategy.UnsupportedFallback -> "RefuseDiagnostic(reason=${strategy.reason.code},action=${strategy.fallback.reason})"
        is CoverageBackendStrategy.CpuSpanPath -> "strategy.CpuSpanPath"
        is CoverageBackendStrategy.CoverageAtlasSample -> "strategy.CoverageAtlasSample"
    }
}

private fun dumpMatrix(matrix: MatrixSpec): String =
    "${matrix.m00},${matrix.m01},${matrix.m02};${matrix.m10},${matrix.m11},${matrix.m12};" +
        "${matrix.m20},${matrix.m21},${matrix.m22}"

private fun dumpClipForMigration(clip: ClipInteraction): String = when (clip) {
    ClipInteraction.None -> "None"
    is ClipInteraction.DeviceRect -> "DeviceRect(${clip.bounds.left},${clip.bounds.top},${clip.bounds.right},${clip.bounds.bottom})"
    is ClipInteraction.AnalyticShape -> "AnalyticShape(${clip.shape.kind})"
    is ClipInteraction.AaClip -> "AaClip(${clip.ref.id})"
    is ClipInteraction.ShaderClip -> "ShaderClip(reason=${clip.reason.code})"
    is ClipInteraction.Unsupported -> "Unsupported(reason=${clip.reason.code})"
}

private fun pack(c: Rgba): Int {
    fun q(v: Float): Int = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    val a = q(c.a)
    val r = q(c.r)
    val g = q(c.g)
    val b = q(c.b)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
