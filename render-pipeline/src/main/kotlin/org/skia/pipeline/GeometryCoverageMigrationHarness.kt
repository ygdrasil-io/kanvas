package org.skia.pipeline

enum class DescriptorMigrationMode {
    Shadow,
    Compare,
    Gated,
}

enum class DescriptorPrimitiveFamily(val id: String) {
    AxisAlignedFilledRect("axis-aligned-filled-rect"),
}

data class DescriptorMigrationGate(
    val primitive: DescriptorPrimitiveFamily,
    val backend: BackendKind,
    val enabled: Boolean = false,
)

data class DescriptorRoute(val id: String)

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
) {
    fun dump(): String = descriptorDumpHeader(DescriptorMigrationMode.Compare, backend, drawKind, transform, clip) +
        "\ncurrentRoute=${currentRoute.id}" +
        "\ndescriptorRoute=${descriptorRoute.id}" +
        "\n${CoverageDescriptorDump(geometryPlan, coveragePlan, loweringResult).dump()}" +
        "\nfallback=${dumpFallback(loweringResult)}" +
        "\ndiff=${diffSummary.dump()}"
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
    private val legacyCpuSolidRectRoute = DescriptorRoute("cpu.legacy.solid-rect")
    private val descriptorCpuSolidRectRoute = DescriptorRoute("cpu.descriptor.coverage-plan.solid-rect")
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
    ): DescriptorMigrationCompareResult {
        val plans = axisAlignedRectPlans(rect)
        val lowering = CoveragePlanAdapter.lower(plans.coverage)
        val currentPixels = CpuScalarPipelineExecutor.legacySolidRect(width, height, color)
        val descriptorPixels = descriptorPixels(width, height, color, lowering)
        val diff = diffPixels(currentPixels, descriptorPixels, artifactPath)
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
        )
    }

    fun gateDecision(
        primitive: DescriptorPrimitiveFamily,
        backend: BackendKind,
        gates: Collection<DescriptorMigrationGate> = emptyList(),
    ): DescriptorGateDecision {
        val enabled = gates.any { it.primitive == primitive && it.backend == backend && it.enabled }
        val descriptorRoute = when (backend) {
            BackendKind.CPU -> descriptorCpuSolidRectRoute
            BackendKind.GPU -> DescriptorRoute("gpu.descriptor.coverage-plan.solid-rect")
        }
        return DescriptorGateDecision(
            mode = DescriptorMigrationMode.Gated,
            primitive = primitive,
            backend = backend,
            descriptorEnabled = enabled,
            selectedRoute = if (enabled) descriptorRoute else currentRouteFor(backend),
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

    private fun descriptorPixels(
        width: Int,
        height: Int,
        color: Rgba,
        lowering: CoverageLoweringResult,
    ): PixelBuffer {
        val coverage = (lowering as? CoverageLoweringResult.CoverageModelResult)?.coverage
            ?: return PixelBuffer(width, height, IntArray(width * height))
        val result = CpuScalarPipelineExecutor.execute(
            KanvasPipelineIR.demoSolidRectIr(color = color, coverage = coverage),
            width = width,
            height = height,
            options = CpuPipelineExecutionOptions(vectorMode = CpuVectorMode.Disabled),
        )
        return when (result) {
            is CpuExecutionResult.Success -> result.pixels
            is CpuExecutionResult.LegacyFallback -> PixelBuffer(width, height, IntArray(width * height))
        }
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

    private fun axisAlignedRectPlans(rect: FloatRect): RectPlans {
        val transform = identityAxisAlignedTransform()
        val geometry = GeometryPlan.Supported(
            primitive = GeometryPrimitive.Rect(source = rect, device = rect),
            bounds = GeometryBounds(conservative = rect, tight = rect),
            transform = transform,
            clip = ClipInteraction.None,
        )
        return RectPlans(geometry = geometry, coverage = CoveragePlan.AnalyticRect(bounds = rect, aa = false))
    }

    private fun identityAxisAlignedTransform(): TransformFacts = TransformFacts(
        matrix = MatrixSpec.Identity,
        isAxisAligned = true,
        hasPerspective = false,
        maxScale = 1f,
        isInvertible = true,
    )

    private fun currentRouteFor(backend: BackendKind): DescriptorRoute = when (backend) {
        BackendKind.CPU -> legacyCpuSolidRectRoute
        BackendKind.GPU -> DescriptorRoute("gpu.current.handwritten-solid-rect")
    }

    private fun shadowRouteFor(backend: BackendKind): DescriptorRoute = when (backend) {
        BackendKind.CPU -> shadowOnlyRoute
        BackendKind.GPU -> DescriptorRoute("gpu.shadow.generated-rect-candidate")
    }

    private data class RectPlans(val geometry: GeometryPlan, val coverage: CoveragePlan)
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
