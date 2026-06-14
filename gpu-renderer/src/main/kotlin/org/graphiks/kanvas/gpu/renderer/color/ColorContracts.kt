package org.graphiks.kanvas.gpu.renderer.color

/** Color-space descriptor used by color planning. */
data class GPUColorSpaceDescriptor(
    val name: String,
    val primaries: String,
    val transferFunction: String,
    val whitePoint: String,
)

/** External or embedded color profile descriptor. */
data class GPUColorProfileDescriptor(
    val sourceKind: String,
    val profileId: String,
    val profileHash: String,
)

/** Color value representation. */
data class GPUColorValueSpec(
    val componentCount: Int,
    val colorSpace: GPUColorSpaceDescriptor,
    val alphaType: String,
    val numericEncoding: String,
)

/** Color transform plan. */
data class GPUColorTransformPlan(
    val transformKey: String,
    val matrixValues: List<Float>,
    val precisionPolicy: String,
)

/** Color conversion plan between source and destination spaces. */
data class GPUColorConversionPlan(
    val source: GPUColorSpaceDescriptor,
    val destination: GPUColorSpaceDescriptor,
    val transform: GPUColorTransformPlan,
    val policy: String,
    val refusalCode: String? = null,
)

/** Working color-space plan. */
data class GPUWorkingColorSpacePlan(
    val space: GPUColorSpaceDescriptor,
    val reason: String,
    val highPrecision: Boolean,
)

/** Gradient interpolation color plan. */
data class GPUGradientColorPlan(
    val interpolationSpace: GPUColorSpaceDescriptor,
    val hueMethod: String,
    val premulInterpolation: Boolean,
    val stopStorePolicy: String,
)

/** Color uniform packing plan. */
data class GPUColorUniformPlan(
    val slotName: String,
    val valueSpecs: List<GPUColorValueSpec>,
    val packingPolicy: String,
    val dynamicValueCount: Int,
)

/** HDR color plan. */
data class GPUHDRColorPlan(
    val enabled: Boolean,
    val transferFunction: String,
    val maxNits: Float? = null,
    val refusalCode: String? = null,
)

/** Gainmap handling plan. */
data class GPUGainmapPlan(
    val kind: String,
    val baseSpace: GPUColorSpaceDescriptor,
    val alternateSpace: GPUColorSpaceDescriptor? = null,
    val metadataHash: String? = null,
    val supported: Boolean,
)

/** Target store conversion plan. */
data class GPUColorStorePlan(
    val targetSpace: GPUColorSpaceDescriptor,
    val conversion: GPUColorConversionPlan? = null,
    val quantization: String,
    val dither: Boolean,
)

/** Color cache invalidation plan. */
data class GPUColorCachePlan(
    val cacheKey: String,
    val dependentProfileIds: List<String>,
    val invalidationFacts: List<String>,
)

/** Color-management plan for one material or target. */
data class GPUColorManagementPlan(
    val inputSpec: GPUColorValueSpec,
    val workingSpace: GPUWorkingColorSpacePlan,
    val conversion: GPUColorConversionPlan? = null,
    val store: GPUColorStorePlan,
    val diagnostics: List<GPUColorDiagnostic> = emptyList(),
)

/** Color planning diagnostic. */
data class GPUColorDiagnostic(
    val code: String,
    val severity: String,
    val message: String,
    val facts: Map<String, String> = emptyMap(),
    val isTerminal: Boolean,
)

/** Input facts for the bounded M7 SDR color boundary planner. */
data class GPUSDRColorBoundaryRequest(
    val sourceLabel: String,
    val sourceColorSpace: GPUColorSpaceDescriptor,
    val targetColorSpace: GPUColorSpaceDescriptor,
    val targetFormat: String,
    val componentCount: Int = 4,
    val alphaType: String = "Unpremul",
    val numericEncoding: String = "f32",
    val finiteChannels: Boolean = true,
    val profile: GPUColorProfileDescriptor? = null,
    val hdr: GPUHDRColorPlan = GPUHDRColorPlan(enabled = false, transferFunction = "SDR"),
    val gainmap: GPUGainmapPlan? = null,
    val extendedRange: Boolean = false,
) {
    init {
        require(sourceLabel.isNotBlank()) { "GPUSDRColorBoundaryRequest.sourceLabel must not be blank" }
        require(targetFormat.isNotBlank()) { "GPUSDRColorBoundaryRequest.targetFormat must not be blank" }
        require(componentCount > 0) { "GPUSDRColorBoundaryRequest.componentCount must be positive" }
    }
}

/** Result of bounded SDR color planning and profile/HDR refusal classification. */
data class GPUSDRColorBoundaryReport(
    val classification: String,
    val sourceLabel: String,
    val targetFormat: String,
    val plan: GPUColorManagementPlan,
    val diagnostics: List<GPUColorDiagnostic>,
    val behaviorKeyFacts: List<String>,
) {
    /** M7 SDR boundary evidence is contract/refusal evidence and never promotes color support by itself. */
    val promotable: Boolean = false

    /** Returns canonical evidence lines for reports and tests. */
    fun dumpLines(): List<String> {
        val valueLine = "color:value components=${plan.inputSpec.componentCount} " +
            "space=${plan.inputSpec.colorSpace.name} alpha=${plan.inputSpec.alphaType} " +
            "encoding=${plan.inputSpec.numericEncoding} finite=${plan.diagnostics.none { it.code == NON_FINITE_CODE }}"
        val nonClaim = "nonclaim:no-product-activation no-gpu-native-color-route no-hdr-support " +
            "no-gainmap-support no-icc-cicp-transform no-untagged-policy no-platform-color-conversion"

        if (diagnostics.isNotEmpty()) {
            val diagnostic = diagnostics.first()
            return listOf(
                "color:sdr-boundary classification=$classification source=$sourceLabel " +
                    "plan=refused targetFormat=$targetFormat reason=${diagnostic.code}",
                valueLine,
                "color:diagnostic code=${diagnostic.code} terminal=${diagnostic.isTerminal} " +
                    "sourceSpace=${plan.inputSpec.colorSpace.name} sourceKind=${diagnostic.facts["sourceKind"] ?: "none"}",
                nonClaim,
            )
        }

        return listOf(
            "color:sdr-boundary classification=$classification source=$sourceLabel " +
                "plan=finite-srgb-store targetFormat=$targetFormat",
            valueLine,
            "color:working space=${plan.workingSpace.space.name} reason=${plan.workingSpace.reason} " +
                "highPrecision=${plan.workingSpace.highPrecision}",
            "color:store targetSpace=${plan.store.targetSpace.name} quantization=${plan.store.quantization} " +
                "dither=${plan.store.dither} conversion=${if (plan.store.conversion == null) "none" else "planned"}",
            "color:keyFacts ${behaviorKeyFacts.joinToString(" ")}",
            nonClaim,
        )
    }

    private companion object {
        private const val NON_FINITE_CODE: String = "unsupported.color.source_space_unknown"
    }
}

/** Builds bounded SDR color plans and stable refusals for profile/HDR cases. */
object GPUSDRColorBoundaryPlanner {
    /** Built-in sRGB descriptor for the first bounded SDR color lane. */
    val SRGB: GPUColorSpaceDescriptor = GPUColorSpaceDescriptor(
        name = "srgb",
        primaries = "rec709",
        transferFunction = "srgb",
        whitePoint = "d65",
    )

    /** Explicit untagged descriptor used to prove policy-gated refusal. */
    val UNTAGGED: GPUColorSpaceDescriptor = GPUColorSpaceDescriptor(
        name = "untagged",
        primaries = "unknown",
        transferFunction = "unknown",
        whitePoint = "unknown",
    )

    /** Plans the bounded SDR lane or emits one terminal color refusal. */
    fun plan(request: GPUSDRColorBoundaryRequest): GPUSDRColorBoundaryReport {
        val refusalCode = refusalCodeFor(request)
        val diagnostic = refusalCode?.let { code -> diagnosticFor(code, request) }
        val diagnostics = listOfNotNull(diagnostic)
        val inputSpec = GPUColorValueSpec(
            componentCount = request.componentCount,
            colorSpace = request.sourceColorSpace,
            alphaType = request.alphaType,
            numericEncoding = request.numericEncoding,
        )
        val store = GPUColorStorePlan(
            targetSpace = request.targetColorSpace,
            quantization = "${request.targetFormat}-u8-unorm",
            dither = false,
        )
        val plan = GPUColorManagementPlan(
            inputSpec = inputSpec,
            workingSpace = GPUWorkingColorSpacePlan(
                space = SRGB,
                reason = "sdr-first-slice",
                highPrecision = false,
            ),
            store = store,
            diagnostics = diagnostics,
        )

        return GPUSDRColorBoundaryReport(
            classification = if (diagnostic == null) "DependencyGated" else "RefuseDiagnostic",
            sourceLabel = request.sourceLabel,
            targetFormat = request.targetFormat,
            plan = plan,
            diagnostics = diagnostics,
            behaviorKeyFacts = if (diagnostic == null) behaviorKeyFacts(request, store) else emptyList(),
        )
    }

    private fun behaviorKeyFacts(
        request: GPUSDRColorBoundaryRequest,
        store: GPUColorStorePlan,
    ): List<String> =
        listOf(
            "alpha=${request.alphaType}",
            "componentCount=${request.componentCount}",
            "numeric=${request.numericEncoding}",
            "sourceSpace=${request.sourceColorSpace.name}",
            "store=${store.quantization}",
            "targetSpace=${request.targetColorSpace.name}",
        )

    private fun refusalCodeFor(request: GPUSDRColorBoundaryRequest): String? =
        when {
            !request.finiteChannels -> "unsupported.color.source_space_unknown"
            request.extendedRange -> "unsupported.color.extended_range"
            request.sourceColorSpace.name == UNTAGGED.name -> "unsupported.color.untagged_policy"
            request.hdr.enabled -> "unsupported.color.hdr_transfer"
            request.gainmap != null -> "unsupported.color.gainmap"
            request.profile != null -> profileRefusalCode(request.profile)
            !request.sourceColorSpace.isSRGBDescriptor() -> "unsupported.color.gamut_transform"
            !request.targetColorSpace.isSRGBDescriptor() -> "unsupported.color.target_capability"
            request.targetFormat != "rgba8unorm" -> "unsupported.color.target_capability"
            else -> null
        }

    private fun profileRefusalCode(profile: GPUColorProfileDescriptor): String =
        when (profile.sourceKind.lowercase()) {
            "icc-v4" -> "unsupported.color.icc_v4"
            "cicp" -> "unsupported.color.cicp"
            "lut", "icc-lut" -> "unsupported.color.lut_profile"
            else -> "unsupported.color.image_profile_conversion"
        }

    private fun GPUColorSpaceDescriptor.isSRGBDescriptor(): Boolean =
        name == SRGB.name &&
            primaries == SRGB.primaries &&
            transferFunction == SRGB.transferFunction &&
            whitePoint == SRGB.whitePoint

    private fun diagnosticFor(code: String, request: GPUSDRColorBoundaryRequest): GPUColorDiagnostic =
        GPUColorDiagnostic(
            code = code,
            severity = "error",
            message = "Color boundary ${request.sourceLabel} refuses $code without profile, HDR, or gainmap promotion.",
            facts = buildMap {
                put("sourceSpace", request.sourceColorSpace.name)
                put("targetSpace", request.targetColorSpace.name)
                put("targetFormat", request.targetFormat)
                request.profile?.let { profile ->
                    put("sourceKind", profile.sourceKind)
                }
                if (request.hdr.enabled) {
                    put("hdrTransfer", request.hdr.transferFunction)
                }
                request.gainmap?.let { gainmap ->
                    put("gainmapKind", gainmap.kind)
                }
            },
            isTerminal = true,
        )
}
